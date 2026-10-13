package com.grillpoet.mixy.ui.folder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.data.model.MediaItem
import com.grillpoet.mixy.ui.widget.MediaThumbnail

/**
 * 媒体项网格列表。
 *
 * @param items 已排序的媒体项。
 * @param gridColumns 列数。由全局视图设置决定。
 * @param onItemClick 点击某张卡片时的回调，参数为索引。
 * @param modifier 外部传入的修饰符。
 */
@Composable
fun FolderGridList(
    items: List<MediaItem>,
    gridColumns: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> item.uri.toString() }
        ) { index, item ->
            FolderGridCell(
                item = item,
                onClick = { onItemClick(index) }
            )
        }
    }
}

/**
 * 单个网格卡片。
 *
 * 文件名固定两行高度（minLines = maxLines = 2），
 * 避免长文件名和短文件名混合时同排卡片高度参差。
 *
 * @param item 媒体项。
 * @param onClick 点击回调。
 */
@Composable
private fun FolderGridCell(
    item: MediaItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            MediaThumbnail(item = item, modifier = Modifier.fillMaxSize())
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = item.displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )

        Spacer(Modifier.height(2.dp))
    }
}
