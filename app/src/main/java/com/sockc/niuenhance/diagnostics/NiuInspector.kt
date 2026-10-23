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
        val last = context.getSharedPreferences("hook_status", Context.MODE_PRIVATE)
            .getLong("last_seen", 0L)
        val hookInfo = if (last <= 0)
            "LSPosed：尚未收到小牛进程的验证心跳（不等于未注入）。\n" +
                "请检查模块作用域并重启小牛 APP，然后在 LSPosed 模块日志中搜索 [NiuEnhance]。"
        else
            "LSPosed：验证心跳已收到，时间 " +
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last))
        val compat = if (version == VERIFIED) "已列入适配清单" else "未验证版本，Hook 会自动跳过"
        return "官方 APP：" + version + "（" + compat + "）\n" +
            "FX 固件基准：MTC85V14 / MTQ02V06\n" + hookInfo
    }
}
