package com.grillpoet.mixy.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.data.model.PdfLayout
import com.grillpoet.mixy.data.model.PlaylistCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.graphics.Color as ComposeColor

// ============================================================
// 渲染器
// ============================================================

class PdfPageRenderer private constructor(
    private val tempFile: File?,
    private var pfd: ParcelFileDescriptor?,
    private var renderer: PdfRenderer?,
    private val renderWidth: Int
) : AutoCloseable {

    private val renderMutex = Mutex()
    @Volatile private var closed = false

    val pageCount: Int
        get() = if (closed) 0 else (renderer?.pageCount ?: 0)

    suspend fun renderPage(index: Int): Bitmap? = renderMutex.withLock {
        withContext(Dispatchers.IO) {
            if (closed) return@withContext null
            val r = renderer ?: return@withContext null
            runCatching {
                r.openPage(index).use { page ->
                    val scale = renderWidth.toFloat() / page.width
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(
                        renderWidth, height, Bitmap.Config.ARGB_8888
                    )
                    bmp.eraseColor(Color.WHITE)
                    page.render(
                        bmp, null, null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    bmp
                }
            }.getOrNull()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { renderer?.close() }
        renderer = null
        runCatching { pfd?.close() }
        pfd = null
        runCatching { tempFile?.delete() }
    }

    companion object {
        suspend fun create(
            context: Context,
            uri: Uri,
            renderWidth: Int
        ): Result<PdfPageRenderer> = withContext(Dispatchers.IO) {
            val direct = runCatching {
                val pfd = context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?: error("openFileDescriptor 返回 null")
                val renderer = PdfRenderer(pfd)
                renderer.pageCount
                PdfPageRenderer(null, pfd, renderer, renderWidth)
            }
            direct.getOrNull()?.let { return@withContext Result.success(it) }

            runCatching {
                val temp = File(
                    context.cacheDir,
                    "pdf_${System.currentTimeMillis()}.pdf"
                )
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { input.copyTo(it) }
                } ?: error("无法打开输入流")

                val pfd = ParcelFileDescriptor.open(
                    temp,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                val renderer = PdfRenderer(pfd)
                renderer.pageCount
                PdfPageRenderer(temp, pfd, renderer, renderWidth)
            }
        }
    }
}

// ============================================================
// 主界面
// ============================================================

@Composable
fun PdfViewerScreen(
    cursor: PlaylistCursor,
    container: AppContainer
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
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在打开…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val renderer = result.getOrNull()
    if (renderer == null) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text("无法打开该 PDF", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var vertical by remember {
        mutableStateOf(
            container.settingsStore.flow.value.pdfDefaultLayout ==
                PdfLayout.VERTICAL
        )
    }
    var currentPage by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${currentPage + 1} / ${renderer.pageCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = {
                vertical = !vertical
                currentPage = 0
            }) {
                Icon(
                    imageVector = if (vertical) Icons.Filled.SwapHoriz
                    else Icons.Filled.SwapVert,
                    contentDescription = if (vertical) "切换横向" else "切换纵向"
                )
            }
        }

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

// ============================================================
// 纵向：LazyColumn 连续滚动
// ============================================================

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

// ============================================================
// 横向：HorizontalPager 翻页
// ============================================================

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

// ============================================================
// 单页（独立缩放）
//
// 手势规则（与 ImageViewer 一致）：
//   单指 + 未缩放  → 不消费，让 LazyColumn / Pager 处理滚动/翻页
//   单指 + 已缩放  → 平移
//   双指           → 缩放 + 平移
//   双击 + 未缩放  → 放大到 2.5x
//   双击 + 已缩放  → 还原
// ============================================================

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
            // 缩放 + 平移
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
                            // 双指：缩放 + 平移，消费
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale > 1.01f) {
                                clampOffset(offset + panChange, newScale, containerSize)
                            } else Offset.Zero
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1.01f) {
                            // 单指 + 已缩放：平移，消费
                            offset = clampOffset(offset + panChange, scale, containerSize)
                            event.changes.forEach { it.consume() }
                        }
                        // 单指 + 未缩放：不消费，父级处理滚动/翻页
                    }
                }
            }
            // 双击
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
                contentDescription = "第 ${pageIndex + 1} 页",
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
                    "渲染中…",
                    color = ComposeColor.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ============================================================
// 工具
// ============================================================

private fun clampOffset(offset: Offset, scale: Float, size: Size): Offset {
    if (scale <= 1.01f || size.width == 0f) return Offset.Zero
    val maxX = (size.width * (scale - 1)) / 2
    val maxY = (size.height * (scale - 1)) / 2
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY)
    )
}