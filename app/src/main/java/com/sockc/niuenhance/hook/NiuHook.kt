package com.sockc.niuenhance.hook

import android.app.Application
import android.app.BroadcastOptions
import android.os.Build
import android.content.Context
import android.content.Intent
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Safe read-only module probe. Only observes the official application's main process.
 * Explicit, UID-authenticated Android 14+ broadcast for the read-only module
 * heartbeat. Android's identity sharing must be requested by the sender.
 */
class NiuHook : IXposedHookLoadPackage {
    private fun trace(event: String) {
        try {
            XposedBridge.log("[NiuEnhance] $event")
        } catch (_: Throwable) {
            // The hooked application must not depend on our logging.
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.niu.cloud") return
        // Do not inject additional behavior into pushcore or other secondary processes.
        if (lpparam.processName != "com.niu.cloud") return
        trace("entry process=com.niu.cloud")
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java, "attach", Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val ctx = param.args.firstOrNull() as? Context ?: run {
                            trace("attach-no-context")
                            return
                        }
                        try {
                            @Suppress("DEPRECATION")
                            val version = ctx.packageManager
                                .getPackageInfo("com.niu.cloud", 0).versionName
                            if (version != "5.19.0") {
                                trace("version-skip")
                                return
                            }
                            trace("attach version=5.19.0")
                            UiScanHook.install(::trace)
                            // The official app doesn't declare package-visibility queries for
                            // our provider. Use only an explicit, narrowly-scoped broadcast.
                            // Receipt is verified on Android 14+ by actual sending UID.
                            val intent = Intent(HookHeartbeatReceiver.ACTION).apply {
                                setClassName(
                                    "com.sockc.niuenhance",
                                    "com.sockc.niuenhance.hook.HookHeartbeatReceiver"
                                )
                                putExtra(HookHeartbeatReceiver.EXTRA_VERSION, "5.19.0")
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                // Android 14+ defaults sender identity sharing to false.
                                // Without this flag, getSentFromUid() rejects our heartbeat.
                                val opts = BroadcastOptions.makeBasic()
                                    .setShareIdentityEnabled(true)
                                    .toBundle()
                                ctx.sendBroadcast(intent, null, opts)
                                trace("heartbeat-dispatched identity-shared")
                            } else {
                                // Receiver intentionally fails closed on older Android.
                                trace("heartbeat-unsupported-android")
                            }
                        } catch (t: Throwable) {
                            trace("heartbeat-error type=" + t.javaClass.simpleName)
                        }
                    }
                }
            )
            trace("hook-installed")
        } catch (t: Throwable) {
            trace("hook-error type=" + t.javaClass.simpleName)
        }
    }
}
