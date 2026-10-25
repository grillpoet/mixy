package com.grillpoet.mixy.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.R
import com.grillpoet.mixy.data.model.SortMode
import com.grillpoet.mixy.data.model.SortOrder
import com.grillpoet.mixy.data.model.ViewMode

/**
 * 视图设置底部弹窗。
 *
 * 内容委托给 [DisplaySettingsContent]，与设置页的「显示」分区共用同一份 UI。
 * 外部签名保持不变，[com.grillpoet.mixy.ui.folder.FolderContentScreen] 无需改动。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
                text = stringResource(R.string.action_view_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            DisplaySettingsContent(
                view = view,
                gridColumns = gridColumns,
                sortMode = sortMode,
                sortOrder = sortOrder,
                onView = onView,
                onGridColumns = onGridColumns,
                onSortMode = onSortMode,
                onSortOrder = onSortOrder
            )
        }
    }
}