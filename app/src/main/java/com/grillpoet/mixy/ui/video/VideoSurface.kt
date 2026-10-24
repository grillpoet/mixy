package com.grillpoet.mixy.ui.video

import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout

@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: ExoPlayer,
    videoAspect: Float,
    onVideoSizeChanged: (VideoSize) -> Unit,
    modifier: Modifier = Modifier
) {

    // 视频尺寸监听
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                onVideoSizeChanged(videoSize)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 持有 TextureView 的引用，退出时 clearVideoTextureView 解除 ExoPlayer 的持有
    val textureViewRef = remember { arrayOfNulls<TextureView>(1) }

    DisposableEffect(player) {
        onDispose {
            textureViewRef[0]?.let { runCatching { player.clearVideoTextureView(it) } }
            textureViewRef[0] = null
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                AspectRatioFrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setAspectRatio(if (videoAspect > 0f) videoAspect else 16f / 9f)
                    tag = videoAspect

                    val textureView = TextureView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    textureViewRef[0] = textureView
                    addView(textureView)
                    player.setVideoTextureView(textureView)
                }
            },
            update = { view ->
                val cached = view.tag as? Float
                val target = if (videoAspect > 0f) videoAspect else 16f / 9f
                if (cached == null || cached != target) {
                    view.setAspectRatio(target)
                    view.tag = target
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}