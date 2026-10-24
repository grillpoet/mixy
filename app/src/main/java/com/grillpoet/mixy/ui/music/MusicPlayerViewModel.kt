package com.grillpoet.mixy.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.player.MusicPlayerController
import com.grillpoet.mixy.player.MusicPlayerState
import com.grillpoet.mixy.player.PlaybackSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 音乐播放页的 ViewModel。
 *
 * 只做转发：把 UI 请求转给全局单例 [MusicPlayerController]，
 * 把状态透传给 UI。Controller 保持单例是为了音乐能跨界面播放。
 *
 * 状态流：
 *  - [state]     低频：切歌、播放/暂停、模式、歌词
 *  - [position]  高频：200ms 一次。只给进度条/歌词用
 *  - [duration]  高频：同上
 */
class MusicPlayerViewModel(
    private val controller: MusicPlayerController,
    private val session: PlaybackSession
) : ViewModel() {

    val state: StateFlow<MusicPlayerState?> = controller.state
    val position: StateFlow<Long> = controller.position
    val duration: StateFlow<Long> = controller.duration

    private var lastCursor: PlaylistCursor? = null

    fun startPlayback(cursor: PlaylistCursor) {
        lastCursor = cursor
        viewModelScope.launch {
            controller.setQueue(cursor)
            session.rememberMusicCursor(cursor)
        }
    }

    fun retry() {
        val c = lastCursor ?: return
        startPlayback(c)
    }

    fun clear() = controller.clear()
    fun togglePlayPause() = controller.togglePlayPause()
    fun next() = controller.next()
    fun prev() = controller.prev()
    fun seekTo(ms: Long) = controller.seekTo(ms)
    fun cyclePlayMode() = controller.cyclePlayMode()
}