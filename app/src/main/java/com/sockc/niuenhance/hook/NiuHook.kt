package com.sockc.niuenhance.hook

import android.app.Application
import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * V0.1.1 diagnostics-only LSPosed entry. Does not alter any NIU application state.
 * Each stage is tagged so injection failures can be distinguished from IPC failures.
 */
class NiuHook : IXposedHookLoadPackage {
    private fun trace(stage: String) {
        try {
            XposedBridge.log("[NiuEnhance] " + stage)
        } catch (_: Throwable) {
            // Logging must not affect the official application.
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.niu.cloud") return
        trace("entry process=" + lpparam.processName)
        try {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val ctx = param.args.firstOrNull() as? Context ?: run {
                            trace("attach-no-context")
                            return
                        }
                        try {
                            @Suppress("DEPRECATION")
                            val version = ctx.packageManager.getPackageInfo("com.niu.cloud", 0)
                                .versionName ?: "unknown"
                            if (version != "5.19.0") {
                                trace("version-skip version=" + version)
                                return
                            }
                            trace("attach version=" + version)
                            val result = ctx.contentResolver.call(
                                "com.sockc.niuenhance.config", "ping", null,
                                Bundle().apply { putString("app_version", version) }
                            )
                            trace(if (result?.getBoolean("ok") == true) "heartbeat-ok"
                                  else "heartbeat-no")
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
