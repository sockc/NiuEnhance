package com.sockc.niuenhance.diagnostics

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object BluetoothInspector {
    @Suppress("MissingPermission", "DEPRECATION")
    fun describe(context: Context): String {
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return "需要授权「附近设备」后才能读取已配对设备。"
        }
        return try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: return "设备没有可用蓝牙适配器"
            if (!adapter.isEnabled) return "蓝牙已关闭"
            val niu = adapter.bondedDevices.orEmpty().filter {
                it.name?.contains("NIU", ignoreCase = true) == true
            }
            val deviceRows = if (niu.isEmpty()) "未发现已配对的 NIU 设备" else
                niu.joinToString("\n") {
                    "${it.name ?: "未知设备"} · ${it.address.takeLast(5)}（已配对，连接状态待核验）"
                }
            val a2dp = adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
            val headset = adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
            "$deviceRows\n系统 A2DP：${if (a2dp) "有连接" else "未连接"}；通话耳机：${if (headset) "有连接" else "未连接"}\n注：系统音频通道状态并不代表某一辆车的连接状态。"
        } catch (e: SecurityException) {
            "系统拒绝蓝牙权限：${e.javaClass.simpleName}"
        } catch (e: Exception) {
            "读取失败：${e.javaClass.simpleName}"
        }
    }
}
