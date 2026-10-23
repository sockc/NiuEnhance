package com.sockc.niuenhance.diagnostics

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

object MediaInspector {
    private fun active(context: Context): MediaController? {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val listener = ComponentName(context, MediaAccessListener::class.java)
        return manager.getActiveSessions(listener).firstOrNull()
    }

    fun describe(context: Context): String {
        return try {
            val controller = active(context) ?: return "当前没有可读取的媒体会话。请先开启播放器，并授权「通知使用权」。"
            val title = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "未知歌曲"
            val artist = controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "未知歌手"
            val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            "$title · $artist\n${if (playing) "播放中" else "未播放"} · ${controller.packageName}"
        } catch (_: SecurityException) {
            "需要开启 NiuEnhance 的「通知使用权」，才能访问媒体会话。"
        } catch (e: Exception) {
            "媒体读取失败：${e.javaClass.simpleName}"
        }
    }

    fun control(context: Context, action: String) {
        try {
            val controller = active(context) ?: return
            when (action) {
                "previous" -> controller.transportControls.skipToPrevious()
                "next" -> controller.transportControls.skipToNext()
                "toggle" -> if (controller.playbackState?.state == PlaybackState.STATE_PLAYING)
                    controller.transportControls.pause()
                else controller.transportControls.play()
            }
        } catch (_: Exception) {
            // No session or an unsupported transport action: never crash the UI.
        }
    }
}
