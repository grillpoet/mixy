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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.R
import com.grillpoet.mixy.pdf.PdfPageRenderer
import kotlin.math.abs
import androidx.compose.ui.graphics.Color as ComposeColor

/** A4 竖版比例，用于统一页面高度、保证布局稳定。 */
private const val DEFAULT_PAGE_ASPECT = 0.707f

/**
 * 纵向连续滚动，整卷共享缩放级别。
 *
 * - 缩放/平移：整个 [Column] 作为一个整体，通过 [graphicsLayer] 变换，中心原点。
 * - 手势：条件消费。双指时消费用于缩放；单指在 1x 时不消费交给滚动，
 *   放大后水平主导消费为平移、垂直主导让给 [verticalScroll]。
 * - 性能：只组合可见范围内的页面，其余用同尺寸 [Spacer] 占位。
 * - 视觉：[graphicsLayer] 开启 `clip = true`，不会盖到外部 UI。
 */
@Composable
internal fun VerticalPdf(
    renderer: PdfPageRenderer,
    onPageChange: (Int) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(Size.Zero) }

    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val pageHeightPx by remember {
        derivedStateOf {
            if (containerSize.width > 0f) containerSize.width / DEFAULT_PAGE_ASPECT else 0f
        }
    }
    val pageHeightDp: Dp = with(density) { pageHeightPx.toDp() }

    val currentPage by remember {
        derivedStateOf {
            if (pageHeightPx <= 0f) 0
            else (scrollState.value / pageHeightPx).toInt().coerceIn(0, renderer.pageCount - 1)
        }
    }
    LaunchedEffect(currentPage) { onPageChange(currentPage) }

    val visibleRange by remember {
        derivedStateOf {
            if (pageHeightPx <= 0f) IntRange.EMPTY
            else {
                val buffer = 1
                val first = ((scrollState.value / pageHeightPx).toInt() - buffer).coerceAtLeast(0)
                val last = (((scrollState.value + containerSize.height) / pageHeightPx).toInt() + buffer)
                    .coerceAtMost(renderer.pageCount - 1)
                if (first <= last) first..last else IntRange.EMPTY
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black)
            .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

                    var isZooming = false
                    var hasPannedHorizontally = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        // 双指：缩放 + 平移
                        if (pressed.size >= 2) {
                            isZooming = true
                            hasPannedHorizontally = false

                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)

                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(1f, PDF_MAX_SCALE)
                            val zoomDelta = if (oldScale > 0f) newScale / oldScale else 1f

                            val center = Offset(containerSize.width / 2, containerSize.height / 2)
                            val delta = if (centroid != Offset.Unspecified) centroid - center else Offset.Zero
                            val newOffset = (offset - delta) * zoomDelta + delta + pan

                            scale = newScale
                            offset = clampOffset(newOffset, scale, containerSize)

                            event.changes.forEach { if (it.pressed) it.consume() }
                            continue
                        }

                        // 1x：单指全部交给 verticalScroll
                        if (!isZooming && scale <= 1.01f) continue

                        // 放大后单指：只有横向主导才消费
                        val pan = event.calculatePan()
                        if (scale > 1.01f) {
                            if (abs(pan.x) > abs(pan.y)) hasPannedHorizontally = true
                            if (hasPannedHorizontally) {
                                offset = clampOffset(offset + pan, scale, containerSize)
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
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
                                val center = Offset(containerSize.width / 2, containerSize.height / 2)
                                val delta = tapOffset - center
                                offset = clampOffset(-delta * (newScale - 1f), newScale, containerSize)
                            }
                            scale = newScale
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                    clip = true
                )
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pageHeightDp > 0.dp) {
                for (index in 0 until renderer.pageCount) {
                    if (index in visibleRange) {
                        PdfPageShared(
                            renderer = renderer,
                            pageIndex = index,
                            pageHeight = pageHeightDp
                        )
                    } else {
                        Spacer(Modifier.fillMaxWidth().height(pageHeightDp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageShared(
    renderer: PdfPageRenderer,
    pageIndex: Int,
    pageHeight: Dp
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, pageIndex, renderer) {
        value = renderer.renderPage(pageIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(pageHeight)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = stringResource(R.string.pdf_page, pageIndex + 1),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
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