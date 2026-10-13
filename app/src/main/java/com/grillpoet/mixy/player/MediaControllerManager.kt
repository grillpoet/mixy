package com.grillpoet.mixy.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MediaControllerManager(private val context: Context) {

    private var _future: ListenableFuture<MediaController>? = null
    private var _controller: MediaController? = null

    private val _controllerFlow = MutableStateFlow<MediaController?>(null)
    val controllerFlow: StateFlow<MediaController?> = _controllerFlow.asStateFlow()

    val controller: MediaController? get() = _controller

    suspend fun connect(): MediaController {
        _controller?.let { return it }

        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        _future = future
        return future.await().also {
            _controller = it
            _controllerFlow.value = it
        }
    }

    fun release() {
        _future?.let { MediaController.releaseFuture(it) }
        _future = null
        _controller = null
        _controllerFlow.value = null
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, MoreExecutors.directExecutor())
        cont.invokeOnCancellation { cancel(false) }
    }