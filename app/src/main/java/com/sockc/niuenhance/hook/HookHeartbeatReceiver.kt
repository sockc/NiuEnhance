package com.sockc.niuenhance.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process

/**
 * Receive a minimal heartbeat from the official NIU main process.
 *
 * Security: an explicit broadcast by itself is not authenticated. On Android 14+
 * we validate the kernel-supplied sender UID and resolve it to com.niu.cloud
 * before recording an event. On older Android versions, fail closed.
 */
class HookHeartbeatReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.sockc.niuenhance.action.HOOK_HEARTBEAT"
        const val EXTRA_VERSION = "version"
        private const val OFFICIAL_PACKAGE = "com.niu.cloud"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return

        val senderUid = getSentFromUid()
        if (senderUid == Process.INVALID_UID) return
        // Reject own-app echoes, system broadcasts and callers we cannot verify.
        if (senderUid == context.applicationInfo.uid) return

        val senderPackages = context.packageManager.getPackagesForUid(senderUid)
            ?.toSet().orEmpty()
        if (OFFICIAL_PACKAGE !in senderPackages) return
        if (intent.getStringExtra(EXTRA_VERSION) != "5.19.0") return

        context.getSharedPreferences("hook_status", Context.MODE_PRIVATE)
            .edit().putLong("last_seen", System.currentTimeMillis()).apply()
    }
}
