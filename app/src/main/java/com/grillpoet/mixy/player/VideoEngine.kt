package com.grillpoet.mixy.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 视频引擎。
 *
 * [DefaultLoadControl] 是 Media3 的 unstable API，整个类标记 @OptIn 抑制
 * UnsafeOptInUsageError。
 *
 * 生命周期：App 级单例，[release] 当前无调用点——这是有意取舍，
 * 复用 ExoPlayer 加快二次进入首帧，代价是解码器随进程存活。
 * 若未来引入 per-screen ViewModel 作用域，应在页面退出时 release 并重建。
 */
@OptIn(UnstableApi::class)
class VideoEngine(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(5_000, 15_000, 500, 1_000)
                .build()
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var tickJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startTick() else stopTick()
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> _isBuffering.value = true
                    Player.STATE_READY -> {
                        _isBuffering.value = false
                        _duration.value = player.duration.coerceAtLeast(0)
                    }
                    Player.STATE_ENDED, Player.STATE_IDLE -> _isBuffering.value = false
                }
            }
        })
    }

    fun play(uri: Uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    fun pause() = player.pause()
    fun resume() = player.play()
    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _position.value = 0
        _duration.value = 0
    }

    fun seekTo(ms: Long) = player.seekTo(ms)

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    fun setRepeatMode(mode: Int) {
        player.repeatMode = mode
    }

    fun getRepeatMode(): Int = player.repeatMode

    fun release() {
        stopTick()
        player.release()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun startTick() {
        stopTick()
        tickJob = scope.launch {
            while (isActive) {
                _position.value = player.currentPosition.coerceAtLeast(0)
                delay(200.milliseconds)
            }
        }
    }

    private fun stopTick() {
        tickJob?.cancel()
        tickJob = null
    }
}