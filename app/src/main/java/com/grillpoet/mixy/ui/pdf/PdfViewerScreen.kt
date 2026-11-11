package com.grillpoet.mixy.ui.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.R
import com.grillpoet.mixy.data.model.PdfLayout
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.pdf.PdfPageRenderer
import com.grillpoet.mixy.ui.common.MixyTopBar

/** 最大缩放。3x 足够看清 PDF 小字，5x 会铺满屏幕、视觉上挤压顶部栏。 */
internal const val PDF_MAX_SCALE = 3f

/** 双击放大到的倍数。1x ↔ 2x 切换。 */
internal const val PDF_DOUBLE_TAP_SCALE = 2f

/**
 * PDF 阅读页。
 *
 * 子视图拆分：
 *  - [VerticalPdf]   纵向整体缩放（连续滚动）
 *  - [HorizontalPdf] 横向逐页缩放（翻书直觉）
 */
@Composable
fun PdfViewerScreen(
    cursor: PlaylistCursor,
    defaultLayout: PdfLayout,
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

    // 用当前持有的实例做 dispose，避免闭包捕获到新值导致关错对象
    val currentRenderer = rendererState?.getOrNull()
    DisposableEffect(currentRenderer) {
        onDispose { currentRenderer?.close() }
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
                    Text(
                        stringResource(R.string.pdf_opening),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    Text(
                        stringResource(R.string.pdf_cannot_open),
                        style = MaterialTheme.typography.titleMedium
                    )
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
        mutableStateOf(defaultLayout == PdfLayout.VERTICAL)
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
                .clipToBounds()
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