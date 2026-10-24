package com.sockc.niuenhance.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.sockc.niuenhance.UiMenuCatalog

/**
 * Records diagnostic state for an explicit broadcast from the official NIU main process.
 * An untrusted sender can cause a "rejected" diagnostic, but NEVER a success state:
 * only the OS-supplied sending UID can validate the source.
 */
class HookHeartbeatReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.sockc.niuenhance.action.HOOK_HEARTBEAT"
        const val UI_SCAN_ACTION = "com.sockc.niuenhance.action.UI_SCAN"
        const val CONFIG_REQUEST_ACTION = "com.sockc.niuenhance.action.CONFIG_REQUEST"
        const val CONFIG_REPLY_ACTION = "com.sockc.niuenhance.action.CONFIG_REPLY"
        const val EXTRA_VERSION = "version"
        private const val OFFICIAL_PACKAGE = "com.niu.cloud"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION && intent.action != UI_SCAN_ACTION &&
            intent.action != CONFIG_REQUEST_ACTION) return

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
            if (intent.action == CONFIG_REQUEST_ACTION) {
                // Only a verified com.niu.cloud sender can request the small UI flags.
                val nonce = intent.getStringExtra("nonce").orEmpty()
                if (nonce.length !in 32..50 ||
                    !nonce.all { it.isDigit() || it in 'a'..'f' || it == '-' }) return
                val settings = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
                val reply = Intent(CONFIG_REPLY_ACTION).apply {
                    setPackage(OFFICIAL_PACKAGE)
                    putExtra("nonce", nonce)
                    putExtra(EXTRA_VERSION, "5.19.0")
                    putExtra("requested_labels", UiMenuCatalog.encoded(
                        UiMenuCatalog.items.filter { settings.getBoolean(it.key, false) }
                            .map { it.label }
                    ))
                }
                val opts = android.app.BroadcastOptions.makeBasic()
                    .setShareIdentityEnabled(true).toBundle()
                context.sendBroadcast(reply, null, opts)
            } else if (intent.action == UI_SCAN_ACTION) {
                // Only fixed, public navigation labels and numeric view-type counts.
                // Do not persist arbitrary view text, account info, locations or VIN.
                val nodes = intent.getIntExtra("nodes", 0).coerceIn(0, 1200)
                val labels = intent.getStringExtra("whitelisted_labels").orEmpty()
                val safeLabels = UiMenuCatalog.observedLabels.filter {
                    it in labels.split("、")
                }
                val requested = UiMenuCatalog.selected(intent.getStringExtra("menu_requested"))
                val matched = UiMenuCatalog.selected(intent.getStringExtra("menu_matched"))
                val attempted = UiMenuCatalog.selected(intent.getStringExtra("menu_attempted"))
                val verified = UiMenuCatalog.selected(intent.getStringExtra("menu_verified"))
                val invisible = UiMenuCatalog.selected(intent.getStringExtra("menu_invisible"))
                context.getSharedPreferences("ui_scan", Context.MODE_PRIVATE).edit()
                    .putLong("last_scan", System.currentTimeMillis())
                    .putInt("nodes", nodes)
                    .putInt("text_nodes", intent.getIntExtra("text_nodes", 0).coerceIn(0, 1200))
                    .putInt("web_views", intent.getIntExtra("web_views", 0).coerceIn(0, 1200))
                    .putInt("flutter_views", intent.getIntExtra("flutter_views", 0).coerceIn(0, 1200))
                    .putInt("compose_views", intent.getIntExtra("compose_views", 0).coerceIn(0, 1200))
                    .putInt("react_views", intent.getIntExtra("react_views", 0).coerceIn(0, 1200))
                    .putString("labels", safeLabels.joinToString("、"))
                    .putBoolean("on_my_page", intent.getBooleanExtra("on_my_page", false))
                    .putString("menu_requested", UiMenuCatalog.encoded(requested))
                    .putString("menu_matched", UiMenuCatalog.encoded(matched))
                    .putString("menu_attempted", UiMenuCatalog.encoded(attempted))
                    .putString("menu_invisible", UiMenuCatalog.encoded(invisible))
                    .putString("menu_verified", UiMenuCatalog.encoded(verified))
                    .putBoolean("config_received", intent.getBooleanExtra("config_received", false))
                    .apply()
                if (intent.getBooleanExtra("on_my_page", false)) {
                    context.getSharedPreferences("ui_scan", Context.MODE_PRIVATE).edit()
                        .putLong("last_my_scan", System.currentTimeMillis())
                        .putString("last_my_labels", safeLabels.joinToString("、"))
                        .apply()
                }
            } else {
                record("来自小牛官方 APP 的心跳已通过验证", verified = true)
            }
        } catch (t: Exception) {
            // Only the exception type is stored; no sender UID, VIN or account data.
            record("接收校验异常：" + t.javaClass.simpleName)
        }
    }
}
