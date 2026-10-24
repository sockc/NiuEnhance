package com.sockc.niuenhance.diagnostics

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Read-only diagnostic for the outgoing MEDIA stream.
 * The device returned by AudioManager is an anticipated media route, not proof
 * that the original NIU speaker amplifier has any remaining headroom.
 */
object AudioRouteInspector {
    private fun manager(context: Context) =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun typeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙 A2DP（立体声媒体）"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙 SCO（通话音质）"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙 LE 耳机"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "蓝牙 LE 扬声器"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "手机扬声器"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 音频"
        else -> "其他输出（类型 $type）"
    }

    private fun devices(context: Context): List<AudioDeviceInfo> {
        val audio = manager(context)
        return if (Build.VERSION.SDK_INT >= 33) {
            val media = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audio.getAudioDevicesForAttributes(media)
        } else {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
    }

    fun hasBluetoothMediaRoute(context: Context): Boolean = try {
        devices(context).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
    } catch (_: Exception) { false }

    fun describe(context: Context): String = try {
        val audio = manager(context)
        val level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        val routes = devices(context)
            .map { typeLabel(it.type) }
            .distinct()
        val routeText = routes.ifEmpty { listOf("未能确定") }.joinToString("、")
        "媒体音量：$level / $max（${if (muted) "已静音" else "未静音"}）\n" +
            "媒体输出路径：$routeText\n" +
            "提示：这里显示系统预测的媒体输出，不代表一定是 NIU Link，也不代表原车功放的实际音量。"
    } catch (e: Exception) {
        "音频路由读取失败：${e.javaClass.simpleName}；不自动更改任何音量设置。"
    }
}
