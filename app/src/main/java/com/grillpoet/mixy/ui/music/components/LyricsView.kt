package com.grillpoet.mixy.ui.music.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grillpoet.mixy.R
import com.grillpoet.mixy.data.LyricLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 歌词视图。
 *
 * 交互：
 *  - 播放中自动滚动到当前行
 *  - 手动拖动时暂停自动滚动，松手后 3 秒内不恢复（避免与用户操作打架）
 *  - 松手时把中心行作为 seek 目标
 *  - 切歌时重置拖动抑制窗口，让新歌的歌词立刻能自动滚动
 *
 * @param lyrics 歌词行列表。空列表时显示stringResource(R.string.music_no_lyrics)。
 * @param position 当前播放位置 (ms)。高频更新，来自播放器。
 * @param onSeek 用户拖动歌词后跳转到指定位置 (ms) 的回调。
 * @param modifier 外部传入的修饰符。
 */
@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    position: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.music_no_lyrics),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // 当前行索引。二分查找可以用但歌词数量少，线性也够。
    val currentIndex = remember(position, lyrics) {
        lyrics.indexOfLast { it.timestamp <= position }.coerceAtLeast(0)
    }

    var isDragging by remember { mutableStateOf(false) }
    var lastUserSeekAt by remember { mutableLongStateOf(0L) }

    // 切歌时重置拖动抑制窗口。不重置的话，切换前 3 秒内拖动过歌词，
    // 新歌的歌词会在这 3 秒内不自动滚动，看起来像卡住了。
    LaunchedEffect(lyrics) {
        lastUserSeekAt = 0L
        isDragging = false
    }

    // 监听拖动交互，松手后计算中心行并 seek
    LaunchedEffect(listState) {
        var userInitiated = false

        launch {
            listState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) {
                    userInitiated = true
                    isDragging = true
                }
            }
        }

        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && isDragging && userInitiated) {
                    delay(200.milliseconds)
                    val info = listState.layoutInfo
                    val centerY = info.viewportSize.height / 2
                    val centerItem = info.visibleItemsInfo.minByOrNull {
                        kotlin.math.abs(it.offset + it.size / 2 - centerY)
                    }
                    val lyricIndex = ((centerItem?.index ?: 1) - 1)
                        .coerceIn(0, lyrics.lastIndex)
                    onSeek(lyrics[lyricIndex].timestamp)
                    lastUserSeekAt = System.currentTimeMillis()
                    isDragging = false
                    userInitiated = false
                }
            }
    }

    // maxHeight 被使用（halfHeight 和 viewportPx），lint 误报 scope 未使用，抑制。
    @Suppress("UnusedBoxWithConstraintsScope")
    BoxWithConstraints(modifier = modifier) {
        val halfHeight = maxHeight / 2
        val viewportPx = with(density) { maxHeight.toPx() }
        val currentItemHeightPx = remember { mutableIntStateOf(0) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "top_spacer") {
                Spacer(Modifier.height(halfHeight))
            }
            itemsIndexed(
                lyrics,
                key = { i, _ -> "lyric_$i" }
            ) { index, line ->
                LyricLineItem(
                    text = line.text,
                    isCurrent = index == currentIndex && !isDragging,
                    itemHeightPx = currentItemHeightPx
                )
            }
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(halfHeight))
            }
        }

        LaunchedEffect(currentIndex, currentItemHeightPx.intValue, isDragging) {
            if (isDragging) return@LaunchedEffect
            if (currentIndex !in lyrics.indices) return@LaunchedEffect
            if (System.currentTimeMillis() - lastUserSeekAt < 3000) return@LaunchedEffect

            val itemH = currentItemHeightPx.intValue
            val offset = -((viewportPx - itemH) / 2).toInt()
            listState.animateScrollToItem(currentIndex + 1, offset)
        }
    }
}

/**
 * 单行歌词。
 *
 * 当前行字号更大、颜色更深、加粗；其他行淡化。
 * 用 [animateFloatAsState] 做 250ms 过渡。
 *
 * @param text 歌词文本。
 * @param isCurrent 是否当前行。
 * @param itemHeightPx 输出：当前行的高度 (px)。父组件用它计算自动滚动的偏移。
 */
@Composable
private fun LyricLineItem(
    text: String,
    isCurrent: Boolean,
    itemHeightPx: androidx.compose.runtime.MutableIntState
) {
    val fontSize by animateFloatAsState(
        targetValue = if (isCurrent) 22f else 15f,
        animationSpec = tween(durationMillis = 250),
        label = "lyricFontSize"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0.5f,
        animationSpec = tween(durationMillis = 250),
        label = "lyricAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (isCurrent) itemHeightPx.intValue = size.height
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSize.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha)
        )
    }
}