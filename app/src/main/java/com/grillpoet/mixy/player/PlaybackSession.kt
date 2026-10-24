package com.grillpoet.mixy.player

import android.net.Uri
import com.grillpoet.mixy.data.model.PlaylistCursor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 跨界面的播放相关全局状态。
 *
 * 生命周期与 Application 一致（AppContainer 字段）。
 *
 * 收拢三件事：
 *  1. [externalUri]  外部 App 传进来的 URI。一次性事件，用 Channel 实现。
 *  2. [lastMusicCursor] 最近一次音乐队列，MiniBar 点击时重建播放页。
 */
class PlaybackSession {

    // 外部 URI 是「事件」不是「状态」：消费一次就结束。
    // Channel 有缓冲，MainActivity.onCreate 在 collector 订阅前 post 也不会丢。
    private val _externalUri = Channel<Uri>(Channel.BUFFERED)

    /** 外部传入待处理的 URI 流。每个 URI 只被投递一次。 */
    val externalUri: Flow<Uri> = _externalUri.receiveAsFlow()

    private val _lastMusicCursor = MutableStateFlow<PlaylistCursor?>(null)
    val lastMusicCursor: StateFlow<PlaylistCursor?> = _lastMusicCursor.asStateFlow()

    /** 主线程调用。写入外部 URI。 */
    fun postExternalUri(uri: Uri?) {
        if (uri != null) _externalUri.trySend(uri)
    }

    /** 主线程调用。切换音乐队列时记录，MiniBar 依赖它跳转。 */
    fun rememberMusicCursor(cursor: PlaylistCursor) {
        _lastMusicCursor.value = cursor
    }
}