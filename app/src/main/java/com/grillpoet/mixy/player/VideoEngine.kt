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
import androidx.media3.exoplayer.SeekParameters
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
 * 生命周期：App 级单例，跟随进程存活。
 *
 * 刻意不提供 release()：
 *  - 视频页退出只调 [stop]（停播放、清队列），不销毁 ExoPlayer。
 *    下次进视频页复用同一个 ExoPlayer，省掉 200-500ms 的初始化和首帧延迟。
 *  - 进程结束时系统回收，ExoPlayer 无需手动 release。
 *
 * 代价：解码器随进程存活，占用少量内存。
 */
@OptIn(UnstableApi::class)
class VideoEngine(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000,
                    30_000,
                    800,
                    1_500
                )
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

    init {
        // 允许 seek 落在目标前后 500ms 范围内，尽量对齐到 I 帧。
        //
        // 用 setSeekParameters 方法而非 seekParameters 属性：
        // getSeekParameters() 定义在 Player 接口，setSeekParameters() 只在 ExoPlayer，
        // Kotlin 无法跨接口合成可写属性，seekParameters 被识别为 val。
        player.setSeekParameters(SeekParameters(500, 500))
    }

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
                    Player.STATE_ENDED -> {
                        _isBuffering.value = false
                        // tick 在 onIsPlayingChanged(false) 时已停，最后一次 position
                        // 可能还差几十毫秒没跑到底。显式钉到 duration，
                        // 让进度条显示 100%。
                        _position.value = player.duration.coerceAtLeast(0)
                    }
                    Player.STATE_IDLE -> _isBuffering.value = false
                }
            }
        })
    }

    fun play(uri: Uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        when {
            // 播放结束：play() 不会重播，必须 seek 回 0 再 play。
            // 这是 ExoPlayer 的固有行为，不加这段用户会以为卡死。
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                _position.value = 0
                player.play()
            }
            player.isPlaying -> player.pause()
            else -> player.play()
        }
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        stopTick()
        _position.value = 0
        _duration.value = 0
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        _position.value = ms.coerceAtLeast(0)
        if (!player.isPlaying) startTick()
    }

    fun setSpeed(speed: Float) {
        // Media3 的 setPlaybackSpeed / getPlaybackSpeed 无法被 Kotlin 合成为属性，
        // 必须用方法调用。IDE 的 "Use property access syntax" 建议是误报。
        player.setPlaybackSpeed(speed)
    }

    fun setRepeatMode(mode: Int) {
        player.repeatMode = mode
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