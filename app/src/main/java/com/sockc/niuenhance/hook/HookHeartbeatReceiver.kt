package com.sockc.niuenhance.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process

/**
 * Records diagnostic state for an explicit broadcast from the official NIU main process.
 * An untrusted sender can cause a "rejected" diagnostic, but NEVER a success state:
 * only the OS-supplied sending UID can validate the source.
 */
class HookHeartbeatReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.sockc.niuenhance.action.HOOK_HEARTBEAT"
        const val EXTRA_VERSION = "version"
        private const val OFFICIAL_PACKAGE = "com.niu.cloud"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val prefs = context.getSharedPreferences("hook_status", Context.MODE_PRIVATE)
        fun record(result: String, verified: Boolean = false) {
            val now = System.currentTimeMillis()
            val edit = prefs.edit()
                .putLong("last_attempt", now)
                .putString("last_result", result)
            if (verified) edit.putLong("last_seen", now)
            edit.apply()
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            record("当前 Android 版本不支持发送方身份校验")
            return
        }
        try {
            val senderUid = getSentFromUid()
            if (senderUid == Process.INVALID_UID) {
                record("已收到广播，但系统未提供发送方 UID")
                return
            }
            if (senderUid == context.applicationInfo.uid) {
                record("拒绝：来源是 NiuEnhance 自身")
                return
            }
            val senderPackages = context.packageManager.getPackagesForUid(senderUid)
                ?.toSet().orEmpty()
            if (OFFICIAL_PACKAGE !in senderPackages) {
                record("拒绝：UID 未关联小牛官方 APP")
                return
            }
            if (intent.getStringExtra(EXTRA_VERSION) != "5.19.0") {
                record("拒绝：小牛 APP 版本标记不匹配")
                return
            }
            record("来自小牛官方 APP 的心跳已通过验证", verified = true)
        } catch (t: Exception) {
            // Only the exception type is stored; no sender UID, VIN or account data.
            record("接收校验异常：" + t.javaClass.simpleName)
        }
    }
}
