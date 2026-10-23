package com.sockc.niuenhance.hook

import android.app.Application
import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Safe V0.1 entry: only verifies the installed official app version and reports
 * module activation. Does NOT interfere with UI, Bluetooth keys, or vehicle control.
 */
class NiuHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.niu.cloud") return
        XposedHelpers.findAndHookMethod(
            Application::class.java, "attach", Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ctx = param.args[0] as? Context ?: return
                    try {
                        @Suppress("DEPRECATION")
                        val version = ctx.packageManager.getPackageInfo("com.niu.cloud", 0).versionName
                        if (version != "5.19.0") return
                        ctx.contentResolver.call(
                            "com.sockc.niuenhance.config", "ping", null,
                            Bundle().apply { putString("app_version", version) }
                        )
                    } catch (_: Throwable) {
                        // Fail closed: never risk official app startup.
                    }
                }
            }
        )
    }
}
