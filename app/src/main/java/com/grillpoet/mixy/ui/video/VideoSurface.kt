package com.grillpoet.mixy.ui.video

import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout

/**
 * 视频画面。
 *
 * 职责单一：把 [ExoPlayer] 的输出渲染到 [TextureView] 上，
 * 并根据视频宽高比和容器宽高比选择合适的填充方式。
 *
 * 不处理手势、不处理控件。手势在 VideoGestureLayer，控件在 VideoControls。
 *
 * 为什么用 TextureView 而不是 SurfaceView：
 * TextureView 支持动画、变换、在 Compose 里更灵活。
 * ExoPlayer 官方推荐在需要变换的场景下用 TextureView。
 *
 * @param player 底层播放器。Screen 持有，这里只挂载。
 * @param videoAspect 视频宽高比 (width/height)。0 时默认 16:9。
 * @param onVideoSizeChanged 视频尺寸变化回调，由 Screen 更新 videoAspect。
 */
@Composable
fun VideoSurface(
    player: ExoPlayer,
    videoAspect: Float,
    onVideoSizeChanged: (VideoSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 视频尺寸监听：挂载时注册，卸载时移除
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                onVideoSizeChanged(videoSize)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
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
                    addView(textureView)
                    player.setVideoTextureView(textureView)
                }
            },
            update = { view ->
                val cached = view.tag as? Float
                val target = if (videoAspect > 0f) videoAspect else 16f / 9f
                if (cached == null || cached != target) {
                    (view as AspectRatioFrameLayout).setAspectRatio(target)
                    view.tag = target
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
