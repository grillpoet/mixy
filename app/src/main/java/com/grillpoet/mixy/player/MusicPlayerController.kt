package com.grillpoet.mixy.player

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
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
import kotlin.time.Duration.Companion.milliseconds

/**
 * 播放层错误。UI 层据此选择本地化文案，状态层不携带用户可见字符串。
 */
enum class MusicError {
    /** MediaController 连接 PlaybackService 失败。 */
    CONNECTION_FAILED,
}

/**
 * 音乐播放页 UI 状态。
 *
 * position / duration 不在这里：它们每秒变 5 次，如果放进这个 data class，
 * 每次变化都会触发订阅 MusicPlayerState 的组件（比如 MiniBar）重组。
 * 二者在 [MusicPlayerController] 里作为独立 StateFlow 暴露。
 */
data class MusicPlayerState(
    val cursor: PlaylistCursor,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val lyrics: List<LyricLine> = emptyList(),
    val error: MusicError? = null,
    /** 原始异常信息，开发者向，UI 可选择性展示为小字。 */
    val errorDetail: String? = null
) {
    val current: MediaItem? get() = cursor.current
}

class MusicPlayerController(private val container: AppContainer) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow<MusicPlayerState?>(null)
    val state: StateFlow<MusicPlayerState?> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private var listenerAttached = false
    private var tickJob: Job? = null
    private var lyricsJob: Job? = null

    fun clear() {
        val controller = container.controllerManager.controller
        runCatching {
            controller?.stop()
            controller?.clearMediaItems()
        }
        tickJob?.cancel()
        tickJob = null
        lyricsJob?.cancel()
        lyricsJob = null
        _state.value = null
        _position.value = 0L
        _duration.value = 0L
    }

    suspend fun setQueue(cursor: PlaylistCursor) {
        val old = _state.value
        val sameQueue = old != null && old.cursor.items == cursor.items

        if (sameQueue) {
            _state.update {
                it?.copy(cursor = cursor, isBuffering = true, error = null, errorDetail = null)
            }
        } else {
            _state.value = MusicPlayerState(cursor = cursor, isBuffering = true)
            _position.value = 0L
            _duration.value = 0L
        }

        val controller = runCatching { container.controllerManager.connect() }.getOrNull()
        if (controller == null) {
            _state.update {
                it?.copy(
                    isBuffering = false,
                    error = MusicError.CONNECTION_FAILED,
                    errorDetail = null
                )
            }
            return
        }

        if (!listenerAttached) {
            attachListener(controller)
            listenerAttached = true
        }

        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                val c = container.controllerManager.controller
                if (c != null && _state.value != null) {
                    _position.value = c.currentPosition.coerceAtLeast(0)
                    _duration.value = c.duration.coerceAtLeast(0)
                }
                delay(200.milliseconds)
            }
        }

        if (sameQueue) {
            // 只有用户点的是另一首时才 seek。从 MiniBar 重新进入播放页时
            // currentIndex 与当前播放索引一致，不应把进度归零。
            if (controller.currentMediaItemIndex != cursor.currentIndex) {
                controller.seekTo(cursor.currentIndex, 0L)
            }
            if (!controller.isPlaying) controller.play()
        } else {
            val initialMode = container.settingsStore.flow.value.musicDefaultPlayMode
            _state.update { it?.copy(playMode = initialMode) }
            applyPlayMode(initialMode)

            controller.stop()
            controller.clearMediaItems()
            val mediaItems = cursor.items.map { it.toMedia3() }
            controller.setMediaItems(mediaItems, cursor.currentIndex, 0L)
            controller.prepare()
            controller.play()
        }

        container.playbackSession.rememberMusicCursor(cursor)
        loadLyricsFor(cursor.currentIndex)
    }

    private fun attachListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it?.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it?.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
                }
                if (playbackState == Player.STATE_READY) {
                    _duration.value = controller.duration.coerceAtLeast(0)
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

                val newCursor = _state.value?.cursor
                if (newCursor != null) {
                    container.playbackSession.rememberMusicCursor(newCursor)
                }

                loadLyricsFor(idx)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.w(
                    "MusicPlayer",
                    "play error: ${error.errorCodeName}, cause=${error.cause?.message}"
                )

                val hasNext = controller.hasNextMediaItem()
                if (hasNext) {
                    controller.seekToNextMediaItem()
                } else {
                    controller.pause()
                    _state.update { it?.copy(isPlaying = false) }
                }
            }
        })
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
        _position.value = ms
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
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
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