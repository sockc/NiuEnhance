package com.sockc.niuenhance.hook

import android.app.Activity
import android.app.BroadcastOptions
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Read-only UI structure probe. Never stores free-form view text and never changes views.
 * Samples only known public navigation labels from com.niu.cloud 5.19.0.
 */
object UiScanHook {
    // Application.attach may fire more than once; install Activity hooks exactly once.
    private val installed = AtomicBoolean(false)
    private val watch = WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()
    private val lastAt = WeakHashMap<Activity, Long>()
    private val lastSignature = WeakHashMap<Activity, String>()
    private val labels = listOf("NIU ZOO", "主题工坊", "我的小队", "消息通知", "我的订单",
        "官方社群", "意见反馈", "在线客服", "我的设备", "车币", "车况")
    private const val MAX_NODES = 1200

    fun install(log: (String) -> Unit) {
        if (!installed.compareAndSet(false, true)) return
        XposedHelpers.findAndHookMethod(Activity::class.java, "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    try {
                        if (activity.packageName != "com.niu.cloud") return
                        val root = activity.window?.decorView ?: return
                        if (!watch.containsKey(activity)) {
                            val observer = ViewTreeObserver.OnGlobalLayoutListener {
                                scan(activity, false)
                            }
                            root.viewTreeObserver.addOnGlobalLayoutListener(observer)
                            watch[activity] = observer
                        }
                        root.postDelayed({ scan(activity, true) }, 600L)
                    } catch (e: Throwable) {
                        log("ui-probe-install-error type=" + e.javaClass.simpleName)
                    }
                }
            }
        )
        XposedHelpers.findAndHookMethod(Activity::class.java, "onPause",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    val observer = watch.remove(activity) ?: return
                    try {
                        activity.window?.decorView?.viewTreeObserver
                            ?.removeOnGlobalLayoutListener(observer)
                    } catch (_: Throwable) { }
                    lastAt.remove(activity)
                    lastSignature.remove(activity)
                }
            }
        )
    }

    private data class Result(
        val nodes: Int,
        val textNodes: Int,
        val webViews: Int,
        val flutterViews: Int,
        val composeViews: Int,
        val reactViews: Int,
        val found: String
    ) {
        fun signature() = "$nodes/$textNodes/$webViews/$flutterViews/$composeViews/$reactViews/$found"
    }

    private fun collect(root: View): Result {
        val stack = ArrayDeque<View>()
        stack.add(root)
        var total = 0
        var text = 0
        var web = 0
        var flutter = 0
        var compose = 0
        var react = 0
        val found = linkedSetOf<String>()
        while (stack.isNotEmpty() && total < MAX_NODES) {
            val view = stack.removeLast()
            total++
            val clazz = view.javaClass.name
            if (clazz.contains("WebView")) web++
            if (clazz.contains("FlutterView")) flutter++
            if (clazz.contains("ComposeView")) compose++
            if (clazz.contains("ReactRootView")) react++
            if (view is TextView) {
                text++
                val value = view.text?.toString()?.trim() ?: ""
                if (value.length <= 30) labels.forEach { label ->
                    if (value == label) found.add(label)
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    stack.add(view.getChildAt(i))
                }
            }
        }
        return Result(total, text, web, flutter, compose, react,
            labels.filter { it in found }.joinToString("、"))
    }

    private fun scan(activity: Activity, force: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            activity.isFinishing || activity.isDestroyed) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - (lastAt[activity] ?: 0L) < 2500L) return
        try {
            val root = activity.window?.decorView ?: return
            if (!root.isAttachedToWindow) return
            val result = collect(root)
            val signature = result.signature()
            lastAt[activity] = now
            if (!force && lastSignature[activity] == signature) return
            lastSignature[activity] = signature
            val intent = Intent(HookHeartbeatReceiver.UI_SCAN_ACTION).apply {
                setClassName("com.sockc.niuenhance",
                    "com.sockc.niuenhance.hook.HookHeartbeatReceiver")
                putExtra(HookHeartbeatReceiver.EXTRA_VERSION, "5.19.0")
                putExtra("nodes", result.nodes)
                putExtra("text_nodes", result.textNodes)
                putExtra("web_views", result.webViews)
                putExtra("flutter_views", result.flutterViews)
                putExtra("compose_views", result.composeViews)
                putExtra("react_views", result.reactViews)
                putExtra("whitelisted_labels", result.found)
            }
            val opts = BroadcastOptions.makeBasic()
                .setShareIdentityEnabled(true).toBundle()
            activity.sendBroadcast(intent, null, opts)
        } catch (_: Throwable) {
            // Never alter or crash the official vehicle app.
        }
    }
}
