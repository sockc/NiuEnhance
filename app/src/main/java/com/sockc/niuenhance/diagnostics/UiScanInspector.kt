package com.sockc.niuenhance.diagnostics

import android.content.Context
import com.sockc.niuenhance.UiMenuCatalog
import java.text.DateFormat
import java.util.Date

/**
 * Fixed-label snapshots only. Current switches are never presented as proof of
 * an older scan result; unverified attempts remain explicitly unverified.
 */
object UiScanInspector {
    private fun scan(context: Context) =
        context.getSharedPreferences("ui_scan", Context.MODE_PRIVATE)
    private fun settings(context: Context) =
        context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    fun discovered(context: Context): Set<String> =
        UiMenuCatalog.selected(scan(context).getString("last_my_labels", ""))

    fun configuredCount(context: Context): Int =
        UiMenuCatalog.items.count { settings(context).getBoolean(it.key, false) }

    fun menuStatus(context: Context, item: UiMenuCatalog.Item): String {
        val config = settings(context)
        val p = scan(context)
        if (!config.getBoolean(item.key, false)) return "保留显示"
        val whenScanned = p.getLong("last_scan", 0L)
        if (whenScanned == 0L ||
            whenScanned < config.getLong(item.key + "_changed_at", 0L) ||
            !p.getBoolean("config_received", false) ||
            item.label !in UiMenuCatalog.selected(p.getString("menu_requested", ""))
        ) return "待重新进入小牛验证"
        if (!p.getBoolean("on_my_page", false)) return "待扫描「我的」页面"
        if (item.label in UiMenuCatalog.selected(p.getString("menu_verified", ""))) {
            return "已隐藏 · 复查通过"
        }
        if (item.label in UiMenuCatalog.selected(p.getString("menu_attempted", ""))) {
            return "已执行隐藏 · 复查未通过"
        }
        if (item.label in UiMenuCatalog.selected(p.getString("menu_matched", ""))) {
            return "已匹配 · 未执行隐藏"
        }
        return "未找到安全的独立容器 · 已跳过"
    }

    fun summary(context: Context): String {
        val p = scan(context)
        val last = p.getLong("last_scan", 0L)
        val known = discovered(context)
        if (last == 0L) {
            return "尚无页面扫描记录。进入小牛官方 APP 的「我的」页面后即可自动发现可配置菜单。"
        }
        val verified = UiMenuCatalog.selected(p.getString("menu_verified", ""))
        val enabled = UiMenuCatalog.items.filter { settings(context).getBoolean(it.key, false) }
        val confirmed = enabled.count { menuStatus(context, it) == "已隐藏 · 复查通过" }
        val date = DateFormat.getDateTimeInstance().format(Date(last))
        return "已发现 ${known.size} 项菜单 · 已开启 ${enabled.size} 项隐藏 · 复查成功 $confirmed 项\n" +
            "最近扫描：$date" +
            if (!p.getBoolean("on_my_page", false)) "（非「我的」页面）" else ""
    }

    fun detail(context: Context): String {
        val p = scan(context)
        val last = p.getLong("last_scan", 0L)
        if (last == 0L) return "尚无扫描记录。"
        val date = DateFormat.getDateTimeInstance().format(Date(last))
        val labels = p.getString("labels", "").orEmpty()
        val matched = UiMenuCatalog.selected(p.getString("menu_matched", ""))
        val attempted = UiMenuCatalog.selected(p.getString("menu_attempted", ""))
        val verified = UiMenuCatalog.selected(p.getString("menu_verified", ""))
        val invisible = UiMenuCatalog.selected(p.getString("menu_invisible", ""))
        return "扫描：$date\n" +
            "页面节点 ${p.getInt("nodes", 0)} · 文字节点 ${p.getInt("text_nodes", 0)}\n" +
            "WebView ${p.getInt("web_views", 0)} · Flutter ${p.getInt("flutter_views", 0)} · Compose ${p.getInt("compose_views", 0)} · React ${p.getInt("react_views", 0)}\n" +
            "页面识别：${if (p.getBoolean("on_my_page", false)) "我的" else "其他页面或未匹配"}\n" +
            "固定菜单标签：${if (labels.isEmpty()) "未匹配" else labels}\n" +
            "配置已读取：${if (p.getBoolean("config_received", false)) "是" else "否"}\n" +
            "找到独立容器：${if (matched.isEmpty()) "无" else UiMenuCatalog.encoded(matched)}\n" +
            "已尝试隐藏：${if (attempted.isEmpty()) "无" else UiMenuCatalog.encoded(attempted)}\n" +
            "目标原本不可见：${if (invisible.isEmpty()) "无" else UiMenuCatalog.encoded(invisible)}\n" +
            "复查仍为隐藏：${if (verified.isEmpty()) "无" else UiMenuCatalog.encoded(verified)}\n" +
            "仅记录固定公开菜单名称与控件数量，不保存任意页面文字。"
    }
}
