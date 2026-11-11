package com.grillpoet.mixy.ui.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import com.grillpoet.mixy.R
import com.grillpoet.mixy.pdf.PdfPageRenderer
import androidx.compose.ui.graphics.Color as ComposeColor

@Composable
internal fun HorizontalPdf(
    renderer: PdfPageRenderer,
    onPageChange: (Int) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { renderer.pageCount }
    )

    LaunchedEffect(pagerState.currentPage) {
        onPageChange(pagerState.currentPage)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { index ->
        PdfPageZoomable(
            renderer = renderer,
            pageIndex = index,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 逐页独立缩放的单页。仅横向翻页模式使用。
 *
 * 缩放围绕容器中心对称，`clipToBounds` 裁掉溢出到相邻页的部分，
 * 不会遮挡顶部栏或相邻页面。
 */
@Composable
private fun PdfPageZoomable(
    renderer: PdfPageRenderer,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(Size.Zero) }

    val bitmap by produceState<Bitmap?>(initialValue = null, pageIndex, renderer) {
        value = renderer.renderPage(pageIndex)
    }

    val pageAspect = if (bitmap != null)
        bitmap!!.width.toFloat() / bitmap!!.height else 0.7f

    Box(
        modifier = modifier
            .aspectRatio(pageAspect.coerceIn(0.3f, 3f))
            .background(ComposeColor(0xFF888888))
            .clipToBounds()
            .onSizeChanged {
                containerSize = Size(it.width.toFloat(), it.height.toFloat())
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )

                    var releasedToParent = false
                    var wasZooming = false
                    var panAccum = Offset.Zero

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (releasedToParent) continue

                        if (pressed.size >= 2) {
                            wasZooming = true
                            panAccum = Offset.Zero

                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)

                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(1f, PDF_MAX_SCALE)
                            val zoomDelta = if (oldScale > 0f) newScale / oldScale else 1f

                            val newOffset =
                                if (centroid != Offset.Unspecified &&
                                    containerSize.width > 0f
                                ) {
                                    val center = Offset(
                                        containerSize.width / 2,
                                        containerSize.height / 2
                                    )
                                    val delta = centroid - center
                                    (offset - delta) * zoomDelta + delta + pan
                                } else {
                                    offset + pan
                                }

                            scale = newScale
                            offset = clampOffset(newOffset, scale, containerSize)

                            event.changes.forEach { if (it.pressed) it.consume() }
                        } else if (wasZooming || scale > 1.01f) {
                            val pan = event.calculatePan()
                            panAccum += pan
                            val isDragging =
                                panAccum.getDistance() > viewConfiguration.touchSlop

                            if (isDragging) {
                                val desired = offset + pan
                                val clamped = clampOffset(desired, scale, containerSize)
                                val hitEdge = clamped != desired
                                offset = clamped

                                if (!hitEdge) {
                                    event.changes.forEach { if (it.pressed) it.consume() }
                                } else {
                                    releasedToParent = true
                                }
                            }
                        } else {
                            releasedToParent = true
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val newScale = PDF_DOUBLE_TAP_SCALE
                            if (containerSize.width > 0f) {
                                val center = Offset(
                                    containerSize.width / 2,
                                    containerSize.height / 2
                                )
                                val delta = tapOffset - center
                                offset = clampOffset(
                                    -delta * (newScale - 1f),
                                    newScale,
                                    containerSize
                                )
                            }
                            scale = newScale
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = stringResource(R.string.pdf_page, pageIndex + 1),
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
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.pdf_rendering),
                    color = ComposeColor.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun clampOffset(offset: Offset, scale: Float, size: Size): Offset {
    if (scale <= 1.01f || size.width == 0f) return Offset.Zero
    val maxX = (size.width * (scale - 1)) / 2
    val maxY = (size.height * (scale - 1)) / 2
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY)
    )
}