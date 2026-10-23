package com.sockc.niuenhance

/**
 * Fixed, public navigation labels only. Never persist arbitrary text collected
 * from the official app. Keys for zoo/team are retained for existing users.
 */
object UiMenuCatalog {
    data class Item(val label: String, val key: String, val grid: Boolean = false, val important: Boolean = false)
    val items = listOf(
        Item("NIU ZOO", "hide_zoo", grid = true),
        Item("主题工坊", "hide_theme", grid = true),
        Item("我的小队", "hide_team", grid = true),
        Item("消息通知", "hide_notifications", important = true),
        Item("我的订单", "hide_orders", important = true),
        Item("官方社群", "hide_community"),
        Item("意见反馈", "hide_feedback"),
        Item("在线客服", "hide_support"),
        Item("我的设备", "hide_devices", important = true)
    )
    val observedLabels: List<String> = items.map { it.label } + listOf("车币", "车况")
    fun item(label: String): Item? = items.firstOrNull { it.label == label }
    fun selected(raw: String?): Set<String> {
        val parts = raw.orEmpty().split("、").toSet()
        return items.map { it.label }.filterTo(linkedSetOf()) { it in parts }
    }
    fun encoded(labels: Collection<String>): String =
        items.map { it.label }.filter { it in labels }.joinToString("、")
}