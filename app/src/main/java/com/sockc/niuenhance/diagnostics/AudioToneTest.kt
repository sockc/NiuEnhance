package com.sockc.niuenhance.diagnostics

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Manually triggered 3-second test tone, 440Hz, -13dBFS peak. Always starts at
 * fixed conservative signal amplitude and never increases the system volume.
 * Not intended for use while riding. No recording or telemetry.
 */
object AudioToneTest {
    private val active = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)
    @Volatile private var track: AudioTrack? = null

    fun isActive(): Boolean = active.get()

    fun start(onDone: (String) -> Unit): Boolean {
        if (!active.compareAndSet(false, true)) return false
        cancelRequested.set(false)
        thread(name = "niu-audio-tone", isDaemon = true) {
            var result = "3 秒测试音播放结束"
            try {
                val rate = 48000
                val frameSize = 960
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val minBuffer = AudioTrack.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuffer <= 0) error("AudioTrack buffer not supported")
                val output = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(minBuffer, frameSize * 4))
                    .build()
                track = output
                output.play()
                val total = rate * 3
                val fadeFrames = rate / 6
                var pos = 0
                while (!cancelRequested.get() && pos < total) {
                    val frames = min(frameSize, total - pos)
                    val data = ShortArray(frames) { n ->
                        val i = pos + n
                        val fade = minOf(
                            1.0, i.toDouble() / fadeFrames,
                            (total - i).toDouble() / fadeFrames
                        ).coerceAtLeast(0.0)
                        (Short.MAX_VALUE * 0.22 * fade *
                            sin(2.0 * PI * 440.0 * i / rate)).toInt().toShort()
                    }
                    var written = 0
                    while (written < frames && !cancelRequested.get()) {
                        val n = output.write(
                            data, written, frames - written, AudioTrack.WRITE_BLOCKING
                        )
                        if (n <= 0) error("AudioTrack write failed: $n")
                        written += n
                    }
                    pos += frames
                }
                if (cancelRequested.get()) result = "测试音已停止"
            } catch (e: Exception) {
                result = "播放失败：${e.javaClass.simpleName}"
            } finally {
                val output = track
                try { output?.pause() } catch (_: Exception) { }
                try { output?.flush() } catch (_: Exception) { }
                try { output?.release() } catch (_: Exception) { }
                track = null
                active.set(false)
                onDone(result)
            }
        }
        return true
    }

    fun stop() {
        cancelRequested.set(true)
    }
}
