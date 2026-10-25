package com.grillpoet.mixy.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.R
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.ui.common.label
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 设置页的媒体类型编辑组件。
 *
 * 三个操作，全部常驻可用：
 *  - 长按拖动手柄排序
 *  - 点 chip 上的 × 隐藏
 *  - 点下方"已隐藏"区域的 chip 恢复
 *
 * 与主界面的 [MediaTypeChips] 是两个完全独立的组件：
 * 主界面只切换类型，设置页只编辑顺序和可见性。
 */
@Composable
fun EditableChips(
    visible: List<MediaType>,
    hidden: Set<MediaType>,
    onReorder: (List<MediaType>) -> Unit,
    onHide: (MediaType) -> Unit,
    onRestore: (MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        VisibleRow(
            visible = visible,
            onReorder = onReorder,
            onHide = onHide
        )
        if (hidden.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HiddenRow(hidden = hidden, onRestore = onRestore)
        }
    }
}

@Composable
private fun VisibleRow(
    visible: List<MediaType>,
    onReorder: (List<MediaType>) -> Unit,
    onHide: (MediaType) -> Unit
) {
    val lazyListState = rememberLazyListState()

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newList = visible.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onReorder(newList)
    }

    LazyRow(
        state = lazyListState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(
            items = visible,
            key = { it.name }
        ) { type ->
            ReorderableItem(
                state = reorderableState,
                key = type.name
            ) { isDragging ->
                ChipItem(
                    type = type,
                    isDragging = isDragging,
                    dragHandleModifier = Modifier.draggableHandle(),
                    onHideClick = { onHide(type) }
                )
            }
        }
    }
}

/**
 * 单个可编辑 chip。
 *
 * 拖动手柄单独标记，只有按住手柄才能拖动，
 * 点 × 或点 chip 其他区域不会触发拖动。
 *
 * @param modifier 整体容器修饰符。Compose lint 要求第一个 Modifier 参数叫 modifier。
 * @param dragHandleModifier 拖动手柄的修饰符。由调用方传入 draggableHandle()。
 */
@Composable
private fun ChipItem(
    type: MediaType,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier,
    onHideClick: () -> Unit
) {
    val bg = if (isDragging) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = stringResource(R.string.action_drag_to_reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .size(20.dp)
                .then(dragHandleModifier)
        )

        Spacer(Modifier.width(4.dp))

        Text(
            text = type.label(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onHideClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_hide),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun HiddenRow(
    hidden: Set<MediaType>,
    onRestore: (MediaType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_media_types_hidden),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hidden.forEach { type ->
                HiddenChip(type = type, onRestore = { onRestore(type) })
            }
        }
    }
}

@Composable
private fun HiddenChip(type: MediaType, onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onRestore)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = type.label(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alpha(0.7f)
        )
    }
}