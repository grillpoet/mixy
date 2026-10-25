package com.grillpoet.mixy.ui.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.R
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.data.model.PdfLayout
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.pdf.PdfPageRenderer
import com.grillpoet.mixy.ui.common.MixyTopBar
import androidx.compose.ui.graphics.Color as ComposeColor

@Composable
fun PdfViewerScreen(
    cursor: PlaylistCursor,
    container: AppContainer,
    onBack: () -> Unit
) {
    if (cursor.items.isEmpty()) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) {
        configuration.screenWidthDp.dp.toPx().toInt()
    }
    val renderWidth = remember(screenWidthPx) {
        (screenWidthPx * 2).coerceIn(720, 1200)
    }

    val uri = cursor.items[cursor.currentIndex].uri
    val fileName = cursor.items[cursor.currentIndex].displayName

    var rendererState by remember(uri) {
        mutableStateOf<Result<PdfPageRenderer>?>(null)
    }

    LaunchedEffect(uri) {
        rendererState = PdfPageRenderer.create(context, uri, renderWidth)
    }

    DisposableEffect(uri) {
        onDispose { rendererState?.getOrNull()?.close() }
    }

    val result = rendererState

    if (result == null) {
        Column(Modifier.fillMaxSize()) {
            MixyTopBar(title = fileName, onBack = onBack)
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.pdf_opening), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    val renderer = result.getOrNull()
    if (renderer == null) {
        Column(Modifier.fillMaxSize()) {
            MixyTopBar(title = fileName, onBack = onBack)
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(stringResource(R.string.pdf_cannot_open), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    var vertical by remember {
        mutableStateOf(
            container.settingsStore.flow.value.pdfDefaultLayout == PdfLayout.VERTICAL
        )
    }
    var currentPage by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        MixyTopBar(
            title = fileName,
            subtitle = "${currentPage + 1} / ${renderer.pageCount}",
            onBack = onBack,
            actions = {
                IconButton(onClick = {
                    vertical = !vertical
                    currentPage = 0
                }) {
                    Icon(
                        imageVector = if (vertical) Icons.Filled.SwapHoriz
                        else Icons.Filled.SwapVert,
                        contentDescription = stringResource(
                            if (vertical) R.string.pdf_switch_horizontal
                            else R.string.pdf_switch_vertical
                        )
                    )
                }
            }
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            key(vertical) {
                if (vertical) {
                    VerticalPdf(
                        renderer = renderer,
                        onPageChange = { currentPage = it }
                    )
                } else {
                    HorizontalPdf(
                        renderer = renderer,
                        onPageChange = { currentPage = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalPdf(
    renderer: PdfPageRenderer,
    onPageChange: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val currentIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    LaunchedEffect(currentIndex) { onPageChange(currentIndex) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(renderer.pageCount) { index ->
            PdfPageZoomable(
                renderer = renderer,
                pageIndex = index,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HorizontalPdf(
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

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
private fun PdfPageZoomable(
    renderer: PdfPageRenderer,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(Size.Zero) }

    val bitmap by produceState<Bitmap?>(initialValue = null, pageIndex) {
        value = renderer.renderPage(pageIndex)
    }

    val pageAspect = if (bitmap != null)
        bitmap!!.width.toFloat() / bitmap!!.height else 0.7f

    Box(
        modifier = modifier
            .aspectRatio(pageAspect.coerceIn(0.3f, 3f))
            .background(ComposeColor(0xFF888888))
            .onSizeChanged {
                containerSize = Size(it.width.toFloat(), it.height.toFloat())
            }
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
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale > 1.01f) {
                                clampOffset(offset + panChange, newScale, containerSize)
                            } else Offset.Zero
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1.01f) {
                            offset = clampOffset(offset + panChange, scale, containerSize)
                            event.changes.forEach { it.consume() }
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
                            scale = 2.5f
                            if (containerSize.width > 0f) {
                                val cx = containerSize.width / 2
                                val cy = containerSize.height / 2
                                val target = Offset(
                                    (cx - tapOffset.x) * 1.5f,
                                    (cy - tapOffset.y) * 1.5f
                                )
                                offset = clampOffset(target, 2.5f, containerSize)
                            }
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