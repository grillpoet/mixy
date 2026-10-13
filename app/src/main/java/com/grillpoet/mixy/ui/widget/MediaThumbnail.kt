package com.grillpoet.mixy.ui.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.grillpoet.mixy.data.cover.AudioCover
import com.grillpoet.mixy.data.cover.PdfCover
import com.grillpoet.mixy.data.cover.VideoCover
import com.grillpoet.mixy.data.model.MediaItem
import com.grillpoet.mixy.data.model.MediaType

/**
 * 统一封面组件。按 [item] 的类型选择数据包装，交给 Coil 处理缓存和取消。
 *
 * 图片走 Coil 默认 Uri Fetcher；音频 / 视频 / PDF 走各自的自定义 Fetcher。
 *
 * ## 为什么不用 SubcomposeAsyncImage
 *
 * `SubcomposeAsyncImage` 会给每个 cell 开独立的 subcomposition，
 * 在网格列表（尤其 5 列）里同时可见 15-20 个 cell 时，subcomposition
 * 开销叠加，直接拖慢整个 LazyGrid 的滚动。
 *
 * 改用 `AsyncImage`：加载中的状态由一个静态占位层覆盖。
 * 加载失败时也显示占位图标，视觉上与 Subcompose 版一致。
 *
 * @param item 媒体项。用它决定数据包装类型和占位图标。
 * @param modifier 外部传入的修饰符。通常指定尺寸。
 * @param contentScale 图片填充方式，默认 Crop。
 */
@Composable
fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    // 按类型选数据包装。uri 和 type 变化时重新计算。
    val model: Any = remember(item.uri, item.type) {
        when (item.type) {
            MediaType.MUSIC -> AudioCover(item.uri)
            MediaType.VIDEO -> VideoCover(item.uri)
            MediaType.PDF -> PdfCover(item.uri)
            MediaType.IMAGE -> item.uri
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        // 底层占位图标。图片加载成功后被覆盖，加载中/失败时可见。
        Text(
            text = item.type.placeholderText(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 顶层图片。失败时什么都不画，露出底层的占位图标。
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** 各类型的占位符文字。 */
private fun MediaType.placeholderText(): String = when (this) {
    MediaType.MUSIC -> "♪"
    MediaType.VIDEO -> "▶"
    MediaType.PDF -> "PDF"
    MediaType.IMAGE -> "—"
}
