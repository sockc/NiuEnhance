package com.sockc.niuenhance.diagnostics

import android.content.Context
import java.text.DateFormat
import java.util.Date

/**
 * The hooked process reports a snapshot of the last scanned UI. A setting in our
 * own process may have changed since that snapshot, so do not mislabel stale
 * results as current or claim a disabled option was skipped.
 */
object UiScanInspector {
    private fun status(
        configured: Boolean,
        reported: Boolean,
        applied: Boolean,
        onMyPage: Boolean,
        fresh: Boolean
    ): String {
        if (!fresh || configured != reported) {
            return if (configured) "已开启，待重新进入小牛页面验证"
                   else "已关闭，待官方页面刷新"
        }
        if (!configured) return "已关闭"
        if (applied) return "已隐藏"
        return if (onMyPage) "未找到安全容器，已跳过" else "待扫描「我的」页面"
    }

    fun describe(context: Context): String {
        val p = context.getSharedPreferences("ui_scan", Context.MODE_PRIVATE)
        val config = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
        val whenScanned = p.getLong("last_scan", 0L)
        if (whenScanned == 0L) {
            return "尚未识别页面。请重新进入小牛 APP 的「我的」页面，再回来刷新。"
        }
        val date = DateFormat.getDateTimeInstance().format(Date(whenScanned))
        val labels = p.getString("labels", "").orEmpty()
        val onMyPage = p.getBoolean("on_my_page", false)
        val zooStatus = status(
            configured = config.getBoolean("hide_zoo", false),
            reported = p.getBoolean("zoo_requested", false),
            applied = p.getBoolean("zoo_applied", false),
            onMyPage = onMyPage,
            fresh = whenScanned >= config.getLong("hide_zoo_changed_at", 0L)
        )
        val teamStatus = status(
            configured = config.getBoolean("hide_team", false),
            reported = p.getBoolean("team_requested", false),
            applied = p.getBoolean("team_applied", false),
            onMyPage = onMyPage,
            fresh = whenScanned >= config.getLong("hide_team_changed_at", 0L)
        )
        return "上次扫描：$date\n" +
            "页面节点：${p.getInt("nodes", 0)}；原生文字节点：${p.getInt("text_nodes", 0)}\n" +
            "WebView：${p.getInt("web_views", 0)}；Flutter：${p.getInt("flutter_views", 0)}；Compose：${p.getInt("compose_views", 0)}；React：${p.getInt("react_views", 0)}\n" +
            "页面结构包含的菜单（含已隐藏控件）：${if (labels.isBlank()) "未匹配" else labels}\n" +
            "菜单隐藏：NIU ZOO $zooStatus；我的小队 $teamStatus\n" +
            "开关以当前配置为准，隐藏结果以最近一次官方页面扫描为准。"
    }
}
