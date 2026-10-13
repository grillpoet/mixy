package com.grillpoet.mixy.ui.video

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

private const val DRAG_THRESHOLD_PX = 30f

/**
 * 视频手势层。
 *
 * 目前支持两种手势：
 *  - 单击：切换控件显隐（通过 [onTap] 通知上层）
 *  - 水平拖动：快进/快退。拖动中通过 [onDragSeekPreview] 实时上报预览位置，
 *    松手通过 [onDragSeekCommit] 提交 seek。
 *
 * 为什么手势单独抽出来：
 *  1. 手势逻辑独立于视觉，可单独测试
 *  2. 未来加"上下滑调音量/亮度"只需改这个文件
 *  3. 不影响 VideoSurface 和 VideoControls
 *
 * @param positionProvider 当前播放位置提供者。拖动开始时读取一次作为基准。
 * @param durationProvider 视频总时长提供者，用于 clamp。
 * @param onTap 单击回调，Screen 用来切换控件显隐。
 * @param onDragSeekPreview 拖动中回调，传入预览位置 (ms)。
 * @param onDragSeekCommit 拖动结束回调，传入最终 seek 位置 (ms)。
 */
@Composable
fun VideoGestureLayer(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    seekSecondsPerScreen: Int,
    onTap: () -> Unit,
    onDragSeekPreview: (Long) -> Unit,
    onDragSeekCommit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(seekSecondsPerScreen) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragging = false
                    var totalDx = 0f
                    var lastX = down.position.x
                    var dragStartPosition = 0L
                    var previewPosition = 0L

                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break

                        val change = event.changes.firstOrNull() ?: continue
                        val dx = change.position.x - lastX
                        lastX = change.position.x
                        totalDx += dx

                        // 未达阈值不消费，让事件冒泡
                        if (!dragging && abs(totalDx) > DRAG_THRESHOLD_PX) {
                            dragging = true
                            dragStartPosition = positionProvider()
                            previewPosition = dragStartPosition
                        }

                        if (dragging) {
                            change.consume()
                            val deltaMs =
                                (dx / size.width * seekSecondsPerScreen * 1000f).toLong()
                            previewPosition = (previewPosition + deltaMs)
                                .coerceIn(0L, durationProvider().coerceAtLeast(0))
                            onDragSeekPreview(previewPosition)
                        }
                    }

                    if (dragging) {
                        onDragSeekCommit(previewPosition)
                    } else {
                        onTap()
                    }
                }
            }
    )
}
