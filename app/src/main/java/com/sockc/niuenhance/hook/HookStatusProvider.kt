package com.sockc.niuenhance.hook

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

/**
 * Narrow, write-only heartbeat from the hooked NIU process.
 * No sensitive vehicle data, no remote connections, no general-purpose IPC.
 */
class HookStatusProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return Bundle.EMPTY
        val uid = Binder.getCallingUid()
        val callerPackages = ctx.packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
        if (ctx.packageName !in callerPackages && "com.niu.cloud" !in callerPackages) {
            throw SecurityException("Caller not allowed")
        }
        if (method != "ping") return Bundle.EMPTY
        if ("com.niu.cloud" !in callerPackages) return Bundle.EMPTY
        val version = extras?.getString("app_version") ?: return Bundle.EMPTY
        if (version != "5.19.0") return Bundle.EMPTY
        ctx.getSharedPreferences("hook_status", Context.MODE_PRIVATE).edit()
            .putLong("last_seen", System.currentTimeMillis()).apply()
        return Bundle().apply { putBoolean("ok", true) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0
}
