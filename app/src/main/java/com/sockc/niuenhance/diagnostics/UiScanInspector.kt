package com.sockc.niuenhance.diagnostics

import android.content.Context
import java.text.DateFormat
import java.util.Date

/**
 * Displays only the whitelist-based, read-only UI signature.
 * A screenshot alone cannot determine whether NIU uses WebView, Flutter or native views.
 */
object UiScanInspector {
    private fun status(requested: Boolean, applied: Boolean, myPage: Boolean): String =
        if (applied) "已隐藏" else if (!requested) "关闭" else if (!myPage) "待进入「我的」页面" else "未找到安全容器，已跳过"

    fun describe(context: Context): String {
        val p = context.getSharedPreferences("ui_scan", Context.MODE_PRIVATE)
        val whenScanned = p.getLong("last_scan", 0L)
        if (whenScanned == 0L) {
            return "尚未识别页面。请保持 NiuEnhance 的 LSPosed 作用域，重新启动小牛官方 APP，依次打开「车况」和「我的」，然后回来刷新。"
        }
        val date = DateFormat.getDateTimeInstance().format(Date(whenScanned))
        val labels = p.getString("labels", "").orEmpty()
        return "上次扫描：$date\n" +
            "页面节点：${p.getInt("nodes", 0)}；原生文字节点：${p.getInt("text_nodes", 0)}\n" +
            "WebView：${p.getInt("web_views", 0)}；Flutter：${p.getInt("flutter_views", 0)}；Compose：${p.getInt("compose_views", 0)}；React：${p.getInt("react_views", 0)}\n" +
            "识别到的公开菜单：${if (labels.isBlank()) "未匹配" else labels}\n" +
            "菜单隐藏：NIU ZOO " + status(p.getBoolean("zoo_requested", false), p.getBoolean("zoo_applied", false), p.getBoolean("on_my_page", false)) +
            "；我的小队 " + status(p.getBoolean("team_requested", false), p.getBoolean("team_applied", false), p.getBoolean("on_my_page", false)) +
            "\n仅采集固定菜单标签与节点数量；不读取账号或车辆信息。"
    }
}
