package com.grillpoet.mixy.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.R
import com.grillpoet.mixy.data.model.SortMode
import com.grillpoet.mixy.data.model.SortOrder
import com.grillpoet.mixy.data.model.ViewMode
import com.grillpoet.mixy.ui.common.labelRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplaySettingsContent(
    view: ViewMode,
    gridColumns: Int,
    sortMode: SortMode,
    sortOrder: SortOrder,
    onView: (ViewMode) -> Unit,
    onGridColumns: (Int) -> Unit,
    onSortMode: (SortMode) -> Unit,
    onSortOrder: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.settings_view))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = view == ViewMode.LIST,
                onClick = { onView(ViewMode.LIST) },
                label = { Text(stringResource(R.string.settings_view_list)) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = null
                    )
                }
            )
            FilterChip(
                selected = view == ViewMode.GRID,
                onClick = { onView(ViewMode.GRID) },
                label = { Text(stringResource(R.string.settings_view_grid)) },
                leadingIcon = {
                    Icon(Icons.Filled.GridView, contentDescription = null)
                }
            )
        }

        if (view == ViewMode.GRID) {
            Spacer(Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.settings_grid_columns))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..4).forEach { n ->
                    FilterChip(
                        selected = gridColumns == n,
                        onClick = { onGridColumns(n) },
                        label = { Text("$n") }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel(stringResource(R.string.settings_sort_by))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SortMode.entries.forEach { mode ->
                FilterChip(
                    selected = sortMode == mode,
                    onClick = { onSortMode(mode) },
                    label = { Text(stringResource(mode.labelRes)) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel(stringResource(R.string.settings_sort_order))
        Row {
            FilterChip(
                selected = sortOrder == SortOrder.ASC,
                onClick = { onSortOrder(SortOrder.ASC) },
                label = { Text(stringResource(SortOrder.ASC.labelRes)) },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = sortOrder == SortOrder.DESC,
                onClick = { onSortOrder(SortOrder.DESC) },
                label = { Text(stringResource(SortOrder.DESC.labelRes)) }
            )
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