package com.sockc.niuenhance.hook

import android.app.Activity
import android.app.BroadcastOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import com.sockc.niuenhance.UiMenuCatalog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native View-only, opt-in UI cleaner. This never touches vehicle control or
 * other pages. It fails closed if it cannot identify an individual tab container.
 * The module configuration comes from an authenticated, one-time broadcast reply.
 */
object UiScanHook {
    private val installed = AtomicBoolean(false)
    private const val MAX_NODES = 1200
    private val labels = UiMenuCatalog.observedLabels
    private val states = WeakHashMap<Activity, State>()
    private val lastAt = WeakHashMap<Activity, Long>()

    private data class Flags(val selected: Set<String> = emptySet(), val received: Boolean = false)
    private class State(
        val nonce: String,
        val receiver: BroadcastReceiver,
        val listener: ViewTreeObserver.OnGlobalLayoutListener,
        var flags: Flags = Flags(),
        val hidden: MutableMap<String, Pair<View, Int>> = mutableMapOf()
    )

    fun install(log: (String) -> Unit) {
        if (!installed.compareAndSet(false, true)) return
        XposedHelpers.findAndHookMethod(Activity::class.java, "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    try {
                        if (activity.packageName != "com.niu.cloud" ||
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
                        if (!states.containsKey(activity)) attach(activity, log)
                        activity.window?.decorView?.postDelayed({ scan(activity, true) }, 500L)
                    } catch (t: Throwable) {
                        log("ui-attach-error type=" + t.javaClass.simpleName)
                    }
                }
            })
        XposedHelpers.findAndHookMethod(Activity::class.java, "onPause",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    try { detach(activity) } catch (_: Throwable) { }
                }
            })
        XposedHelpers.findAndHookMethod(Activity::class.java, "onDestroy",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    try { detach(activity) } catch (_: Throwable) { }
                }
            })
    }

    private fun attach(activity: Activity, log: (String) -> Unit) {
        val nonce = UUID.randomUUID().toString()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != HookHeartbeatReceiver.CONFIG_REPLY_ACTION) return
                if (intent.getStringExtra("nonce") != nonce) return
                if (getSentFromPackage() != "com.sockc.niuenhance") return
                val state = states[activity] ?: return
                if (intent.getStringExtra(HookHeartbeatReceiver.EXTRA_VERSION) != "5.19.0") return
                state.flags = Flags(
                    UiMenuCatalog.selected(intent.getStringExtra("requested_labels")),
                    received = true
                )
                log("ui-config-received count=" + state.flags.selected.size)
                activity.window?.decorView?.post { scan(activity, true) }
            }
        }
        val listener = ViewTreeObserver.OnGlobalLayoutListener { scan(activity, false) }
        val state = State(nonce, receiver, listener)
        try {
            activity.registerReceiver(receiver,
                IntentFilter(HookHeartbeatReceiver.CONFIG_REPLY_ACTION),
                Context.RECEIVER_EXPORTED)
            activity.window?.decorView?.viewTreeObserver?.addOnGlobalLayoutListener(listener)
            states[activity] = state
            val request = Intent(HookHeartbeatReceiver.CONFIG_REQUEST_ACTION).apply {
                setClassName("com.sockc.niuenhance",
                    "com.sockc.niuenhance.hook.HookHeartbeatReceiver")
                putExtra(HookHeartbeatReceiver.EXTRA_VERSION, "5.19.0")
                putExtra("nonce", nonce)
            }
            val options = BroadcastOptions.makeBasic()
                .setShareIdentityEnabled(true).toBundle()
            activity.sendBroadcast(request, null, options)
        } catch (t: Throwable) {
            detach(activity)
            log("ui-config-request-error type=" + t.javaClass.simpleName)
        }
    }

    private fun detach(activity: Activity) {
        val state = states.remove(activity) ?: return
        restoreAll(state)
        try { activity.unregisterReceiver(state.receiver) } catch (_: Throwable) { }
        try {
            activity.window?.decorView?.viewTreeObserver
                ?.removeOnGlobalLayoutListener(state.listener)
        } catch (_: Throwable) { }
        lastAt.remove(activity)
    }

    private fun restoreAll(state: State) {
        state.hidden.values.forEach { (view, visibility) ->
            try { view.visibility = visibility } catch (_: Throwable) { }
        }
        state.hidden.clear()
    }

    private data class Result(
        val nodes: Int,
        val textNodes: Int,
        val webViews: Int,
        val flutterViews: Int,
        val composeViews: Int,
        val reactViews: Int,
        val found: Set<String>
    ) {
        fun signature() = "$nodes/$textNodes/$webViews/$flutterViews/$composeViews/$reactViews/${found.joinToString()}"
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
                if (value in labels) found.add(value)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) stack.add(view.getChildAt(i))
            }
        }
        return Result(total, text, web, flutter, compose, react, found)
    }

    // A native My-page fingerprint. Crucially, the vehicle control page does not
    // contain these three menu labels simultaneously.
    private fun isMyPage(result: Result): Boolean =
        result.found.containsAll(listOf("主题工坊", "消息通知", "我的设备")) &&
            result.webViews == 0 && result.flutterViews == 0

    private fun menuLeaves(root: View, target: String): List<TextView> {
        val result = mutableListOf<TextView>()
        val stack = ArrayDeque<View>()
        stack.add(root)
        var count = 0
        while (stack.isNotEmpty() && count++ < MAX_NODES) {
            val view = stack.removeLast()
            if (view is TextView && view.text?.toString()?.trim() == target) {
                result.add(view)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) stack.add(view.getChildAt(i))
            }
        }
        return result
    }

    private fun hasOtherTabText(root: View, target: String): Boolean {
        val stack = ArrayDeque<View>()
        stack.add(root)
        var count = 0
        while (stack.isNotEmpty() && count++ < 100) {
            val v = stack.removeLast()
            if (v is TextView) {
                val value = v.text?.toString()?.trim()
                if (value in labels && value != target) return true
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) stack.add(v.getChildAt(i))
        }
        return false
    }

    private fun safeTab(root: View, target: String): View? {
        val density = root.resources.displayMetrics.density
        val candidates = mutableListOf<View>()
        for (leaf in menuLeaves(root, target)) {
            var ancestor = leaf.parent as? ViewGroup
            var depth = 0
            while (ancestor != null && depth++ < 6) {
                val width = ancestor.width
                val height = ancestor.height
                val item = UiMenuCatalog.item(target) ?: break
                // Preserve the proven 3-column grid matching rule. Full-width
                // list rows use a separate bounded geometry, never a card parent.
                val geometry = if (item.grid) {
                    width > 45 * density && width < root.width * 0.55f &&
                        height > 24 * density && height < 110 * density
                } else {
                    width > root.width * 0.55f && width <= root.width &&
                        height > 32 * density && height < 105 * density
                }
                if (geometry &&
                    (ancestor.isClickable ||
                     ancestor.javaClass.name.contains("TabView")) &&
                    !hasOtherTabText(ancestor, target) &&
                    ancestor.parent is ViewGroup &&
                    (ancestor.parent as ViewGroup).childCount >= 2
                ) {
                    candidates.add(ancestor)
                    break
                }
                ancestor = ancestor.parent as? ViewGroup
            }
        }
        // Duplicate labels are ambiguous; never blindly modify the first match.
        return candidates.singleOrNull()
    }

    private data class Applied(val matched: Set<String>, val attempted: Set<String>)

    private fun applyState(root: View, result: Result, state: State): Applied {
        if (!isMyPage(result)) {
            restoreAll(state)
            return Applied(emptySet(), emptySet())
        }
        val matched = linkedSetOf<String>()
        val attempted = linkedSetOf<String>()
        for (item in UiMenuCatalog.items) {
            val label = item.label
            val hidden = state.hidden[label]
            if (label !in state.flags.selected) {
                if (hidden != null) {
                    try { hidden.first.visibility = hidden.second } catch (_: Throwable) { }
                    state.hidden.remove(label)
                }
                continue
            }
            if (hidden != null && hidden.first.isAttachedToWindow) {
                matched.add(label)
                continue
            }
            if (hidden != null) state.hidden.remove(label)
            val candidate = safeTab(root, label) ?: continue
            matched.add(label)
            if (candidate.visibility != View.VISIBLE) continue
            attempted.add(label)
            state.hidden[label] = candidate to candidate.visibility
            candidate.visibility = View.GONE
        }
        return Applied(matched, attempted)
    }

    private fun scan(activity: Activity, force: Boolean) {
        if (activity.isFinishing || activity.isDestroyed) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - (lastAt[activity] ?: 0L) < 1800L) return
        try {
            val root = activity.window?.decorView ?: return
            if (!root.isAttachedToWindow) return
            val state = states[activity] ?: return
            val result = collect(root)
            val applied = applyState(root, result, state)
            lastAt[activity] = now
            // Inspect the actual View visibility on the next UI-loop turn after
            // the attempted change; do not report an attempted hide as verified.
            root.post {
                if (states[activity] !== state || activity.isFinishing ||
                    activity.isDestroyed || !root.isAttachedToWindow) return@post
                try {
                    val verified = state.hidden.filter { (_, pair) ->
                        pair.first.isAttachedToWindow && pair.first.visibility == View.GONE
                    }.keys
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
                        putExtra("whitelisted_labels", labels.filter { it in result.found }.joinToString("、"))
                        putExtra("on_my_page", isMyPage(result))
                        putExtra("config_received", state.flags.received)
                        putExtra("menu_requested", UiMenuCatalog.encoded(state.flags.selected))
                        putExtra("menu_matched", UiMenuCatalog.encoded(applied.matched))
                        putExtra("menu_attempted", UiMenuCatalog.encoded(applied.attempted))
                        putExtra("menu_verified", UiMenuCatalog.encoded(verified))
                    }
                    val opts = BroadcastOptions.makeBasic()
                        .setShareIdentityEnabled(true).toBundle()
                    activity.sendBroadcast(intent, null, opts)
                } catch (_: Throwable) { }
            }
        } catch (_: Throwable) {
            // Fail closed. Vehicle and navigation operations always remain official.
        }
    }
}
