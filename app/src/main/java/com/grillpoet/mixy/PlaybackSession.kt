package com.grillpoet.mixy

import android.net.Uri
import com.grillpoet.mixy.data.model.PlaylistCursor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨界面的播放相关全局状态。
 *
 * 单独抽出来是因为这些状态的生命周期跟 Activity 一致，不属于任何 ViewModel，
 * 又不应该散在 AppContainer 里当裸字段。
 *
 * 收拢的三件事：
 *  1. [externalUri]  外部 App 通过 ACTION_VIEW / ACTION_SEND 传进来的 URI
 *  2. [lastMusicCursor] 上次播放的音乐队列，用于点开 MiniBar 时重建播放页
 */
class PlaybackSession {

    private val _externalUri = MutableStateFlow<Uri?>(null)

    /** 外部传入待消费的 URI。消费后置为 null。 */
    val externalUri: StateFlow<Uri?> = _externalUri.asStateFlow()

    private val _lastMusicCursor = MutableStateFlow<PlaylistCursor?>(null)

    /** 最近一次播放的音乐队列。MiniBar 点击时用它重建 MusicPlayerScreen。 */
    val lastMusicCursor: StateFlow<PlaylistCursor?> = _lastMusicCursor.asStateFlow()

    /** 主线程调用。写入外部 URI，等待 [consumeExternalUri] 消费。 */
    fun postExternalUri(uri: Uri?) {
        _externalUri.value = uri
    }

    /** 主线程调用。消费后置空，避免重组时重复触发跳转。 */
    fun consumeExternalUri() {
        _externalUri.value = null
    }

    /** 主线程调用。切换音乐队列时记录，MiniBar 依赖它跳转。 */
    fun rememberMusicCursor(cursor: PlaylistCursor) {
        _lastMusicCursor.value = cursor
    }
}
