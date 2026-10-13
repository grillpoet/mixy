package com.grillpoet.mixy.ui.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.data.model.VideoOrientation
import com.grillpoet.mixy.ui.music.MusicPlayerViewModel
import kotlinx.coroutines.delay

private const val CONTROLS_HIDE_DELAY = 3500L

/**
 * 视频播放页。
 *
 * 职责：只做编排。持有播放状态、组装三个子模块、管理控件显隐和屏幕方向。
 *
 * 子模块：
 *  - [VideoSurface]：视频画面
 *  - [VideoGestureLayer]：手势
 *  - [VideoControls]：控件面板
 *
 * 状态归属：
 *  - 播放状态 (isPlaying / position / duration)：由 [VideoPlayerViewModel] 持有
 *  - videoAspect：本页持有，由 Surface 回调更新
 *  - controlsVisible：本页持有，由手势和计时器驱动
 *  - 方向管理：本页持有，跟随视频尺寸或手动切换
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    cursor: PlaylistCursor,
    container: AppContainer
) {
    if (cursor.items.isEmpty()) return

    val context = LocalContext.current
    val activity = context as? Activity

    val videoVm: VideoPlayerViewModel = viewModel(factory = container.viewModelFactory)
    val musicVm: MusicPlayerViewModel = viewModel(factory = container.viewModelFactory)

    // 进入视频页，先停掉音乐
    LaunchedEffect(Unit) { musicVm.clear() }

    // ============ 状态 ============
    var currentIndex by remember { mutableIntStateOf(cursor.currentIndex) }
    val currentItem = cursor.items.getOrNull(currentIndex) ?: return

    val isPlaying by videoVm.isPlaying.collectAsState()
    val position by videoVm.position.collectAsState()
    val duration by videoVm.duration.collectAsState()

    var speed by remember { mutableFloatStateOf(1f) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var videoAspect by remember { mutableFloatStateOf(0f) }
    var orientationManuallySet by remember { mutableStateOf(false) }
    var autoOrientationApplied by remember(currentIndex) { mutableStateOf(false) }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(System.currentTimeMillis()) }

    // 拖动手势的预览位置。非 null 时显示浮层。
    var dragSeekPreview by remember { mutableStateOf<Long?>(null) }

    // ============ 初始方向 ============
    LaunchedEffect(Unit) {
        val act = activity ?: return@LaunchedEffect
        when (container.settingsStore.flow.value.videoDefaultOrientation) {
            VideoOrientation.LANDSCAPE -> {
                act.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                orientationManuallySet = true
            }
            VideoOrientation.PORTRAIT -> {
                act.requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                orientationManuallySet = true
            }
            VideoOrientation.AUTO -> Unit
        }
    }

    // ============ 自动画中画 ============
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestAspect = rememberUpdatedState(videoAspect)
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_STOP) return@LifecycleEventObserver
            if (container.settingsStore.flow.value.videoBackground != "PIP") {
                return@LifecycleEventObserver
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return@LifecycleEventObserver
            }
            val act = activity ?: return@LifecycleEventObserver
            if (act.isInPictureInPictureMode) return@LifecycleEventObserver

            val aspect = latestAspect.value
            val ratio = if (aspect >= 1f) {
                Rational((aspect * 100).toInt().coerceAtLeast(100), 100)
            } else {
                Rational(100, ((1f / aspect) * 100).toInt().coerceAtLeast(100))
            }
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(ratio)
                .build()
            runCatching { act.enterPictureInPictureMode(params) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ============ 播放控制 ============
    LaunchedEffect(currentIndex) {
        videoVm.play(currentItem.uri)
        videoVm.setSpeed(speed)
        videoVm.setRepeatMode(repeatMode)
        lastInteraction = System.currentTimeMillis()
        controlsVisible = true
    }

    // 控件自动隐藏
    val interacting = dragSeekPreview != null
    LaunchedEffect(controlsVisible, lastInteraction, isPlaying, interacting) {
        if (!controlsVisible || !isPlaying || interacting) return@LaunchedEffect
        delay(CONTROLS_HIDE_DELAY)
        if (System.currentTimeMillis() - lastInteraction >= CONTROLS_HIDE_DELAY) {
            controlsVisible = false
        }
    }

    // ============ 全屏沉浸 ============
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            videoVm.stop()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ============ 自动方向 ============
    val currentAutoApplied = rememberUpdatedState(autoOrientationApplied)
    LaunchedEffect(videoAspect, orientationManuallySet) {
        if (orientationManuallySet || currentAutoApplied.value) return@LaunchedEffect
        if (videoAspect <= 0f) return@LaunchedEffect   // 真实尺寸未到，先不判
        val act = activity ?: return@LaunchedEffect
        if (videoAspect > 1.05f) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            autoOrientationApplied = true
        } else if (videoAspect < 0.95f) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            autoOrientationApplied = true
        }
    }

    // ============ 布局 ============
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 视频画面
        VideoSurface(
            player = videoVm.player,
            videoAspect = videoAspect,
            onVideoSizeChanged = { size ->
                if (size.width > 0 && size.height > 0) {
                    videoAspect = (size.width * size.pixelWidthHeightRatio) / size.height
                }
            }
        )

        // 手势层
        VideoGestureLayer(
            positionProvider = { position },
            durationProvider = { duration },
            seekSecondsPerScreen =
                container.settingsStore.flow.value.videoSeekSeconds,
            onTap = {
                controlsVisible = !controlsVisible
                lastInteraction = System.currentTimeMillis()
            },
            onDragSeekPreview = { preview ->
                dragSeekPreview = preview
                lastInteraction = System.currentTimeMillis()
            },
            onDragSeekCommit = { target ->
                videoVm.seekTo(target)
                dragSeekPreview = null
                lastInteraction = System.currentTimeMillis()
            }
        )

        // 拖动预览浮层
        dragSeekPreview?.let { preview ->
            DragSeekOverlay(preview = preview, duration = duration)
        }

        // 控件层
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            VideoControls(
                fileName = currentItem.displayName,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                speed = speed,
                repeatMode = repeatMode,
                hasPrev = currentIndex > 0,
                hasNext = currentIndex < cursor.items.size - 1,
                onTogglePlayPause = {
                    videoVm.togglePlayPause()
                    lastInteraction = System.currentTimeMillis()
                },
                onSeek = { ms ->
                    videoVm.seekTo(ms)
                    lastInteraction = System.currentTimeMillis()
                },
                onSpeedChange = { s ->
                    speed = s
                    videoVm.setSpeed(s)
                },
                onCycleRepeatMode = {
                    lastInteraction = System.currentTimeMillis()
                    repeatMode = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }
                    videoVm.setRepeatMode(repeatMode)
                },
                onRotate = {
                    lastInteraction = System.currentTimeMillis()
                    orientationManuallySet = true
                    val cur = activity?.requestedOrientation
                        ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    val next = when (cur) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ->
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ->
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                    activity?.requestedOrientation = next
                },
                onPrev = {
                    currentIndex--
                    lastInteraction = System.currentTimeMillis()
                },
                onNext = {
                    currentIndex++
                    lastInteraction = System.currentTimeMillis()
                }
            )
        }
    }
}


/**
 * 拖动 seek 时的预览浮层。
 *
 * 显示当前预览位置 / 总时长。松手后由调用方清除。
 */
@Composable
private fun DragSeekOverlay(
    preview: Long,
    duration: Long
) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                text = "${fmtMs(preview)} / ${fmtMs(duration)}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** 毫秒 -> mm:ss 或 h:mm:ss。 */
private fun fmtMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
    else "%02d:%02d".format(m, sec)
}
