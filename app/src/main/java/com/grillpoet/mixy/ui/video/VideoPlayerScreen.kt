package com.grillpoet.mixy.ui.video

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.grillpoet.mixy.data.Settings
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.data.model.VideoOrientation
import com.grillpoet.mixy.ui.common.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val CONTROLS_HIDE_DELAY = 3500L.milliseconds
private const val MIN_SEEK_FEEDBACK_MS = 200L

private fun buildPipParams(aspect: Float, autoEnter: Boolean): PictureInPictureParams {
    val safe = if (aspect > 0f && aspect.isFinite()) {
        aspect.coerceIn(0.4f, 2.5f)
    } else {
        16f / 9f
    }
    val ratio = if (safe >= 1f) {
        Rational((safe * 10).roundToInt().coerceAtLeast(10), 10)
    } else {
        Rational(10, ((1f / safe) * 10).roundToInt().coerceAtLeast(10))
    }
    return PictureInPictureParams.Builder()
        .setAspectRatio(ratio)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(autoEnter)
            }
        }
        .build()
}

/**
 * 视频播放页。
 *
 * 参数显式化：不再接收 AppContainer，只声明真正需要的三样：
 *  - [videoVm]：播放器 ViewModel
 *  - [settingsFlow]：读取视频方向 / 后台行为 / 快进灵敏度
 *  - [onStopMusic]：视频开始时停掉音乐（互斥策略由调用方决定）
 *
 * 这样他人移植视频模块时，不用读 AppContainer 就知道依赖边界。
 */
@SuppressLint("SourceLockedOrientationActivity")
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    cursor: PlaylistCursor,
    videoVm: VideoPlayerViewModel,
    settingsFlow: StateFlow<Settings>,
    onStopMusic: () -> Unit,
    onBack: () -> Unit
) {
    if (cursor.items.isEmpty()) return

    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(Unit) { onStopMusic() }

    val settings by settingsFlow.collectAsState()

    var currentIndex by remember { mutableIntStateOf(cursor.currentIndex) }
    val currentItem = cursor.items.getOrNull(currentIndex) ?: return

    val isPlaying by videoVm.isPlaying.collectAsState()
    val isBuffering by videoVm.isBuffering.collectAsState()
    val position by videoVm.position.collectAsState()
    val duration by videoVm.duration.collectAsState()

    var speed by remember { mutableFloatStateOf(1f) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var videoAspect by remember { mutableFloatStateOf(0f) }
    var orientationManuallySet by remember { mutableStateOf(false) }
    var autoOrientationApplied by remember(currentIndex) { mutableStateOf(false) }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var dragSeekPreview by remember { mutableStateOf<Long?>(null) }
    var seekStartedAt by remember { mutableStateOf<Long?>(null) }

    var wasPlayingBeforeBackground by remember { mutableStateOf(false) }

    // ---------- PiP 参数 ----------
    val pipEnabled = settings.videoBackground == "PIP"
    LaunchedEffect(pipEnabled, isPlaying, videoAspect) {
        val act = activity ?: return@LaunchedEffect
        if (!pipEnabled) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect
        act.setPictureInPictureParams(
            buildPipParams(videoAspect, autoEnter = isPlaying)
        )
    }

    DisposableEffect(pipEnabled, videoAspect) {
        val act = activity ?: return@DisposableEffect onDispose {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return@DisposableEffect onDispose {}
        }
        act.setPictureInPictureParams(buildPipParams(videoAspect, autoEnter = false))
        onDispose {}
    }

    LaunchedEffect(Unit) {
        val act = activity ?: return@LaunchedEffect
        when (settings.videoDefaultOrientation) {
            VideoOrientation.LANDSCAPE -> {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                orientationManuallySet = true
            }
            VideoOrientation.PORTRAIT -> {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                orientationManuallySet = true
            }
            VideoOrientation.AUTO -> Unit
        }
    }

    // ---------- 后台行为 ----------
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (activity?.isInPictureInPictureMode == true) {
                        return@LifecycleEventObserver
                    }
                    val controller = videoVm.player
                    wasPlayingBeforeBackground = controller.isPlaying

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        val pipWanted = settingsFlow.value.videoBackground == "PIP"
                        if (pipWanted && wasPlayingBeforeBackground && activity != null) {
                            val entered = runCatching {
                                activity.enterPictureInPictureMode(
                                    buildPipParams(videoAspect, autoEnter = false)
                                )
                            }.getOrDefault(false)
                            if (entered) return@LifecycleEventObserver
                        }
                    }
                    if (controller.isPlaying) controller.pause()
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (activity?.isInPictureInPictureMode == true) {
                        return@LifecycleEventObserver
                    }
                    if (wasPlayingBeforeBackground) {
                        videoVm.player.play()
                    }
                    wasPlayingBeforeBackground = false
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentIndex) {
        videoVm.play(currentItem.uri)
        videoVm.setSpeed(speed)
        videoVm.setRepeatMode(repeatMode)
        lastInteraction = System.currentTimeMillis()
        controlsVisible = true
    }

    val interacting = dragSeekPreview != null
    val showSeekSpinner = !interacting &&
            (seekStartedAt != null || isBuffering)

    LaunchedEffect(controlsVisible, lastInteraction, isPlaying, interacting, showSeekSpinner) {
        if (!controlsVisible || !isPlaying || interacting || showSeekSpinner) {
            return@LaunchedEffect
        }
        delay(CONTROLS_HIDE_DELAY)
        if (System.currentTimeMillis() - lastInteraction >= CONTROLS_HIDE_DELAY.inWholeMilliseconds) {
            controlsVisible = false
        }
    }

    LaunchedEffect(seekStartedAt, isBuffering) {
        val started = seekStartedAt ?: return@LaunchedEffect
        if (isBuffering) return@LaunchedEffect
        val elapsed = System.currentTimeMillis() - started
        val remaining = MIN_SEEK_FEEDBACK_MS - elapsed
        if (remaining > 0) delay(remaining.milliseconds)
        seekStartedAt = null
    }

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

    val currentAutoApplied = rememberUpdatedState(autoOrientationApplied)
    LaunchedEffect(videoAspect, orientationManuallySet) {
        if (orientationManuallySet || currentAutoApplied.value) return@LaunchedEffect
        if (videoAspect <= 0f) return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        if (videoAspect > 1.05f) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            autoOrientationApplied = true
        } else if (videoAspect < 0.95f) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            autoOrientationApplied = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VideoSurface(
            player = videoVm.player,
            videoAspect = videoAspect,
            onVideoSizeChanged = { size ->
                if (size.width > 0 && size.height > 0) {
                    videoAspect = (size.width * size.pixelWidthHeightRatio) / size.height
                }
            }
        )

        VideoGestureLayer(
            positionProvider = { position },
            durationProvider = { duration },
            seekSecondsPerScreen = settings.videoSeekSeconds,
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
                seekStartedAt = System.currentTimeMillis()
                dragSeekPreview = null
                lastInteraction = System.currentTimeMillis()
            }
        )

        dragSeekPreview?.let { preview ->
            DragSeekOverlay(preview = preview, duration = duration)
        }

        if (showSeekSpinner) {
            BufferingOverlay()
        }

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
                repeatMode = repeatMode,
                hasPrev = currentIndex > 0,
                hasNext = currentIndex < cursor.items.size - 1,
                onBack = onBack,
                onTogglePlayPause = {
                    videoVm.togglePlayPause()
                    lastInteraction = System.currentTimeMillis()
                },
                onSeek = { ms ->
                    videoVm.seekTo(ms)
                    seekStartedAt = System.currentTimeMillis()
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

@Composable
private fun DragSeekOverlay(preview: Long, duration: Long) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                text = "${formatDuration(preview)} / ${formatDuration(duration)}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun BufferingOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(20.dp)
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}