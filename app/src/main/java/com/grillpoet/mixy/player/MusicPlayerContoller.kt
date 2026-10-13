package com.grillpoet.mixy.player

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.data.LyricLine
import com.grillpoet.mixy.data.LyricLoader
import com.grillpoet.mixy.data.model.MediaItem
import com.grillpoet.mixy.data.model.PlayMode
import com.grillpoet.mixy.data.model.PlaylistCursor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MusicPlayerState(
    val cursor: PlaylistCursor,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val lyrics: List<LyricLine> = emptyList()
) {
    val current: MediaItem? get() = cursor.current
}

/**
 * 单例音乐播放控制器。
 *
 * 整个 App 只有一个实例（放在 AppContainer）。
 * 切换文件夹 → 调 [setQueue]，换队列但不换实例。
 * listener 只挂一次，tick 只跑一次，永不重复。
 */
class MusicPlayerController(private val container: AppContainer) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow<MusicPlayerState?>(null)
    val state: StateFlow<MusicPlayerState?> = _state.asStateFlow()

    private var listenerAttached = false
    private var tickJob: Job? = null


    /**
     * 关闭播放：停止、清空队列、state 置 null。
     * 迷你栏和通知栏都会随之消失。
     */
    fun clear() {
        val controller = container.controllerManager.controller
        runCatching {
            controller?.stop()
            controller?.clearMediaItems()
        }
        tickJob?.cancel()
        tickJob = null
        _state.value = null
    }

    /** 设置新队列并开始播放 */
    suspend fun setQueue(cursor: PlaylistCursor) {
        val controller = runCatching { container.controllerManager.connect() }.getOrNull()
            ?: return

        // 清空 + 按设置应用初始播放模式
        controller.stop()
        controller.clearMediaItems()

        val initialMode = container.settingsStore.flow.value.musicDefaultPlayMode
        _state.value = MusicPlayerState(cursor = cursor, playMode = initialMode)
        applyPlayMode(initialMode)

        // 挂 listener（只挂一次）
        if (!listenerAttached) {
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it?.copy(isPlaying = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _state.update {
                        it?.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
                    }
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int
                ) {
                    val mediaId = mediaItem?.mediaId ?: return
                    val s = _state.value ?: return
                    val idx = s.cursor.items
                        .indexOfFirst { it.id.toString() == mediaId }
                        .takeIf { it >= 0 }
                        ?: controller.currentMediaItemIndex
                    _state.update { it?.copy(cursor = it.cursor.seek(idx)) }
                    loadLyricsFor(idx)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.w("MusicPlayer", "play error: ${error.errorCodeName}, " +
                            "cause=${error.cause?.message}")

                    // 队列里还有下一首 → 跳下一首；否则停止
                    val hasNext = controller.hasNextMediaItem()
                    if (hasNext) {
                        controller.seekToNextMediaItem()
                    } else {
                        controller.pause()
                        _state.update { it?.copy(isPlaying = false) }
                    }
                }
            })
            listenerAttached = true

            // tick 位置（只跑一次）
            tickJob?.cancel()
            tickJob = scope.launch {
                while (isActive) {
                    val controller = container.controllerManager.controller
                    if (controller != null && _state.value != null) {
                        _state.update {
                            it?.copy(
                                position = controller.currentPosition.coerceAtLeast(0),
                                duration = controller.duration.coerceAtLeast(0)
                            )
                        }
                    }
                    delay(200)
                }
            }
        }

        // 设置队列并播放
        val mediaItems = cursor.items.map { it.toMedia3() }
        controller.setMediaItems(mediaItems, cursor.currentIndex, 0L)
        controller.prepare()
        controller.play()

        loadLyricsFor(cursor.currentIndex)


    }

    fun togglePlayPause() {
        val c = container.controllerManager.controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        container.controllerManager.controller?.seekToNextMediaItem()
    }

    fun prev() {
        container.controllerManager.controller?.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        container.controllerManager.controller?.seekTo(ms)
        _state.update { it?.copy(position = ms) }
    }

    fun cyclePlayMode() {
        val cur = _state.value?.playMode ?: PlayMode.SEQUENCE
        val next = when (cur) {
            PlayMode.SEQUENCE -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SEQUENCE
        }
        _state.update { it?.copy(playMode = next) }
        applyPlayMode(next)
    }

    private fun applyPlayMode(mode: PlayMode) {
        val c = container.controllerManager.controller ?: return
        when (mode) {
            PlayMode.SEQUENCE -> {
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = false
            }
            PlayMode.REPEAT_ONE -> {
                c.repeatMode = Player.REPEAT_MODE_ONE
                c.shuffleModeEnabled = false
            }
            PlayMode.SHUFFLE -> {
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = true
            }
        }
    }

    private fun loadLyricsFor(index: Int) {
        val item = _state.value?.cursor?.items?.getOrNull(index) ?: return
        scope.launch {
            val lyrics = LyricLoader.load(container.appContext, item.uri)
            _state.update { it?.copy(lyrics = lyrics) }
        }
    }
}

private fun MediaItem.toMedia3(): androidx.media3.common.MediaItem =
    androidx.media3.common.MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(displayName)
                .setArtist(folderName)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()

