package com.grillpoet.mixy.ui.image

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.grillpoet.mixy.data.model.PlaylistCursor

@Composable
fun ImageViewerScreen(cursor: PlaylistCursor) {
    if (cursor.items.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = cursor.currentIndex.coerceIn(0, cursor.items.size - 1),
        pageCount = { cursor.items.size }
    )

    // 当前页是否放大
    var currentPageZoomed by remember { mutableStateOf(false) }

    // 切页时重置缩放状态
    LaunchedEffect(pagerState.currentPage) {
        currentPageZoomed = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            // 放大时禁翻页；未放大时允许翻页
            userScrollEnabled = !currentPageZoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImage(
                uri = cursor.items[page].uri,
                isCurrent = page == pagerState.currentPage,
                onZoomChanged = { zoomed ->
                    if (page == pagerState.currentPage) {
                        currentPageZoomed = zoomed
                    }
                }
            )
        }

        // 底部页码
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "${pagerState.currentPage + 1} / ${cursor.items.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * 缩放 / 平移。
 *
 * 关键：单指 + 未放大时**不消费**手势，让上层 HorizontalPager 处理翻页。
 * 双指任何时候都消费（缩放）；单指 + 已放大时消费（平移）。
 */
@Composable
private fun ZoomableImage(
    uri: Uri,
    isCurrent: Boolean,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(Size.Zero) }

    // 上报缩放状态给 pager
    LaunchedEffect(scale, isCurrent) {
        if (isCurrent) onZoomChanged(scale > 1.01f)
    }

    // 切页时重置
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                containerSize = Size(it.width.toFloat(), it.height.toFloat())
            }
            // 手势 1：缩放 + 平移
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break

                        val pointerCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pointerCount >= 2) {
                            // 双指：缩放 + 平移，始终消费
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale > 1.01f) {
                                clampOffset(offset + panChange, newScale, containerSize)
                            } else {
                                Offset.Zero
                            }
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1.01f) {
                            // 单指 + 已放大：平移，消费
                            offset = clampOffset(offset + panChange, scale, containerSize)
                            event.changes.forEach { it.consume() }
                        }
                        // 单指 + 未放大：不消费 → 让 pager 处理翻页
                    }
                }
            }
            // 手势 2：双击缩放
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

/** 限制平移范围，避免拖出屏幕 */
private fun clampOffset(
    offset: Offset,
    scale: Float,
    size: Size
): Offset {
    if (scale <= 1.01f || size.width == 0f) return Offset.Zero
    val maxX = (size.width * (scale - 1)) / 2
    val maxY = (size.height * (scale - 1)) / 2
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY)
    )
}