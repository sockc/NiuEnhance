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
        val hookInfo = if (last <= 0) "LSPosed：尚未收到模块加载记录"
            else "LSPosed：上次捕获小牛启动 ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last))}"
        val compat = if (version == VERIFIED) "已列入适配清单" else "未验证版本，Hook 会自动跳过"
        return "官方 APP：$version（$compat）\nFX 固件基准：MTC85V14 / MTQ02V06\n$hookInfo"
    }
}
