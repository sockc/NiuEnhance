package com.sockc.niuenhance.diagnostics

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri

/**
 * SAF-local audio file playback only. LoudnessEnhancer attaches to OUR
 * MediaPlayer session; it cannot increase output from YouTube Music or the
 * vehicle's built-in notification tones.
 *
 * The gain options are bounded at 0, +3, +6 dB; effect may compress peaks.
 * Failure to instantiate is reported and never silently presented as boosted.
 */
object LocalGainPlayer {
    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var manager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var gainDb: Int = 0
    private var effectAvailable = false
    private var callback: ((String) -> Unit)? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun playing(): Boolean = try { player?.isPlaying == true } catch (_: Throwable) { false }

    fun currentGain(): Int = gainDb

    fun start(context: Context, uri: Uri, onStatus: (String) -> Unit) {
        stop()
        gainDb = 0 // Always begin unamplified, even if last track used +6 dB.
        callback = onStatus
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        manager = am
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                if (change < 0) {
                    try { player?.pause() } catch (_: Exception) { }
                }
            }.build()
        focusRequest = request
        if (am.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onStatus("未获得媒体播放焦点，未启动")
            stop()
            return
        }
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(attrs)
            mp.setDataSource(context, uri)
            mp.setOnPreparedListener { ready ->
                if (ready !== player) return@setOnPreparedListener
                effectAvailable = false
                try {
                    enhancer = LoudnessEnhancer(ready.audioSessionId).also {
                        it.setTargetGain(0)
                        it.enabled = true
                    }
                    effectAvailable = true
                } catch (t: Throwable) {
                    enhancer = null
                    onStatus("设备不支持此媒体会话的响度增强：${t.javaClass.simpleName}。仅可播放原声。")
                }
                try {
                    ready.start()
                    onStatus(if (effectAvailable)
                        "正在播放原声（0 dB）。确认正常后，手动切换 +3 或 +6 dB；如有失真立即停止。"
                    else
                        "正在播放原声，但此设备未能创建响度增强效果，无法使用 +3/+6 dB。")
                } catch (e: Exception) {
                    onStatus("播放失败：${e.javaClass.simpleName}")
                    stop()
                }
            }
            mp.setOnCompletionListener {
                onStatus("本地音频播放结束")
                stop()
            }
            mp.setOnErrorListener { _, what, _ ->
                onStatus("音频解码失败（错误码 $what）")
                stop()
                true
            }
            mp.prepareAsync()
            onStatus("正在加载所选本地音频，初始增益 0 dB…")
        } catch (e: Exception) {
            onStatus("无法打开所选音频：${e.javaClass.simpleName}")
            stop()
        }
    }

    fun setGain(db: Int): String {
        val wanted = db.coerceIn(0, 6)
        if (wanted !in listOf(0, 3, 6)) return "只允许 0、+3、+6 dB"
        if (!playing()) return "请先选取本地音频并开始播放"
        if (!effectAvailable) return "系统不支持本次会话的响度增强"
        return try {
            enhancer?.setTargetGain(wanted * 100)
                ?: return "响度增强尚未初始化"
            gainDb = wanted
            "正在使用 ${if (wanted == 0) "原声" else "+$wanted dB 增益"}；如出现破音、刺耳或异常增大，请立即停止。"
        } catch (e: Exception) {
            "设置增益失败：${e.javaClass.simpleName}"
        }
    }

    fun stop() {
        try { enhancer?.enabled = false } catch (_: Throwable) { }
        try { enhancer?.release() } catch (_: Throwable) { }
        enhancer = null
        effectAvailable = false
        val p = player
        player = null
        try { p?.stop() } catch (_: Throwable) { }
        try { p?.release() } catch (_: Throwable) { }
        val am = manager
        val request = focusRequest
        manager = null
        focusRequest = null
        if (am != null && request != null) {
            try { am.abandonAudioFocusRequest(request) } catch (_: Throwable) { }
        }
        callback = null
        gainDb = 0
    }
}
