package com.grillpoet.mixy.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.mixy.PlaybackSession
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.player.MusicPlayerController
import com.grillpoet.mixy.player.MusicPlayerState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 音乐播放页的 ViewModel。
 *
 * 只做转发：把 UI 请求转给全局单例 [MusicPlayerController]，
 * 把状态透传给 UI。Controller 保持单例是为了音乐能跨界面播放。
 *
 * 为什么不直接让 UI 访问 Controller：
 *  1. UI 只依赖 ViewModel，不依赖 Application 级单例，方便单测替换
 *  2. UI 层访问依赖的方式统一（都走 ViewModel），减少心智负担
 *  3. 想扩展业务逻辑（比如播放前检查权限）时有地方放
 */
class MusicPlayerViewModel(
    private val controller: MusicPlayerController,
    private val session: PlaybackSession
) : ViewModel() {

    /** 当前播放状态。null 表示没有音乐在播。 */
    val state: StateFlow<MusicPlayerState?> = controller.state

    /**
     * 设置新队列并开始播放，同时把 cursor 记到 [PlaybackSession]，
     * 让 MiniBar 能重建播放页。
     */
    fun startPlayback(cursor: PlaylistCursor) {
        viewModelScope.launch {
            controller.setQueue(cursor)
            session.rememberMusicCursor(cursor)
        }
    }

    /** 停止播放并清空队列。MiniBar 和通知栏会随之消失。 */
    fun clear() = controller.clear()

    fun togglePlayPause() = controller.togglePlayPause()
    fun next() = controller.next()
    fun prev() = controller.prev()
    fun seekTo(ms: Long) = controller.seekTo(ms)
    fun cyclePlayMode() = controller.cyclePlayMode()
}
