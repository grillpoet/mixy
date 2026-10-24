package com.grillpoet.mixy.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.data.model.SortMode
import com.grillpoet.mixy.data.model.SortOrder
import com.grillpoet.mixy.data.model.ViewMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ViewSettingsSheet(
    view: ViewMode,
    gridColumns: Int,
    sortMode: SortMode,
    sortOrder: SortOrder,
    onView: (ViewMode) -> Unit,
    onGridColumns: (Int) -> Unit,
    onSortMode: (SortMode) -> Unit,
    onSortOrder: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "视图设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            SectionLabel("视图")
            FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = view == ViewMode.LIST,
                    onClick = { onView(ViewMode.LIST) },
                    label = { Text("列表") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null)
                    }
                )
                FilterChip(
                    selected = view == ViewMode.GRID,
                    onClick = { onView(ViewMode.GRID) },
                    label = { Text("网格") },
                    leadingIcon = {
                        Icon(Icons.Filled.GridView, contentDescription = null)
                    }
                )
            }

            if (view == ViewMode.GRID) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("网格列数")
                FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    (2..5).forEach { n ->
                        FilterChip(
                            selected = gridColumns == n,
                            onClick = { onGridColumns(n) },
                            label = { Text("$n") }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("排序")
            FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                SortModeChip("名称", sortMode == SortMode.NAME) { onSortMode(SortMode.NAME) }
                SortModeChip("时间", sortMode == SortMode.MODIFIED) { onSortMode(SortMode.MODIFIED) }
                SortModeChip("大小", sortMode == SortMode.SIZE) { onSortMode(SortMode.SIZE) }
                SortModeChip("时长", sortMode == SortMode.DURATION) { onSortMode(SortMode.DURATION) }
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("顺序")
            Row {
                FilterChip(
                    selected = sortOrder == SortOrder.ASC,
                    onClick = { onSortOrder(SortOrder.ASC) },
                    label = { Text("升序") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = sortOrder == SortOrder.DESC,
                    onClick = { onSortOrder(SortOrder.DESC) },
                    label = { Text("降序") }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SortModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}