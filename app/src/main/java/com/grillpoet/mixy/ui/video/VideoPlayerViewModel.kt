package com.grillpoet.mixy.ui.video

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.exoplayer.ExoPlayer
import com.grillpoet.mixy.player.VideoEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * 视频播放页的 ViewModel。
 *
 * 转发给全局 [VideoEngine]。视频通常不跨界面播放，
 * Engine 做成 App 级单例主要是为了复用 [ExoPlayer]（避免每次进页面都重建，加快首帧）。
 *
 * [player] 直接暴露给 UI：VideoScreen 需要把 TextureView 挂上去、
 * 挂 Listener 收 VideoSize 变化。前后台切换的暂停/恢复也直接用 player，
 * 不经过 ViewModel。
 *
 * 这是有意打破封装的地方，属于 AndroidView 互操作场景下的常见妥协。
 */
class VideoPlayerViewModel(
    private val engine: VideoEngine
) : ViewModel() {

    /** 底层 ExoPlayer。UI 挂 TextureView / Listener / 前后台控制用。 */
    val player: ExoPlayer get() = engine.player

    val isPlaying: StateFlow<Boolean> = engine.isPlaying
    val position: StateFlow<Long> = engine.position
    val duration: StateFlow<Long> = engine.duration

    fun play(uri: Uri) = engine.play(uri)
    fun togglePlayPause() = engine.togglePlayPause()
    fun stop() = engine.stop()
    fun seekTo(ms: Long) = engine.seekTo(ms)
    fun setSpeed(speed: Float) = engine.setSpeed(speed)
    fun setRepeatMode(mode: Int) = engine.setRepeatMode(mode)
}