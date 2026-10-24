package com.grillpoet.mixy.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MediaController 连接管理器。
 *
 * Service 被系统回收后 controller 会失效。通过 [MediaController.Listener]
 * 的 onDisconnected 清空缓存，下次 connect() 自动重建连接。
 */
class MediaControllerManager(private val context: Context) {

    private val lock = Mutex()

    @Volatile
    private var _controller: MediaController? = null

    val controller: MediaController? get() = _controller

    suspend fun connect(): MediaController = lock.withLock {
        _controller?.let { return it }

        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    // 只清理自己缓存的实例，避免误清新连接。
                    if (_controller === controller) {
                        _controller = null
                    }
                }
            })
            .buildAsync()

        future.await().also { _controller = it }
    }
}