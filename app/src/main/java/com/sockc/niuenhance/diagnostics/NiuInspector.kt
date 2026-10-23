package com.sockc.niuenhance.diagnostics

import android.content.Context

object NiuInspector {
    private const val PKG = "com.niu.cloud"
    private const val VERIFIED = "5.19.0"

    @Suppress("DEPRECATION")
    fun describe(context: Context): String {
        val version = try {
            context.packageManager.getPackageInfo(PKG, 0).versionName ?: "未知"
        } catch (_: Exception) {
            return "未检测到小牛电动 APP；请确认已安装官方应用。"
        }
        val prefs = context.getSharedPreferences("hook_status", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_seen", 0L)
        val lastAttempt = prefs.getLong("last_attempt", 0L)
        val lastResult = prefs.getString("last_result", null)
        val hookInfo = if (last <= 0)
            "LSPosed：尚未收到经过验证的心跳（不等于注入失败）"
        else
            "LSPosed：已验证，最后成功 " +
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last))
        val deliveryInfo = if (lastAttempt <= 0)
            "\n广播：接收端尚无记录"
        else
            "\n广播：" + (lastResult ?: "未知状态") + "（" +
                java.text.DateFormat.getDateTimeInstance()
                    .format(java.util.Date(lastAttempt)) + "）"
        val compat = if (version == VERIFIED) "已列入适配清单" else "未验证版本，Hook 会自动跳过"
        return "官方 APP：" + version + "（" + compat + "）\n" +
            "FX 固件基准：MTC85V14 / MTQ02V06\n" + hookInfo + deliveryInfo
    }
}
