package com.grillpoet.mixy.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.data.MediaSorter
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.data.model.SortSpec
import com.grillpoet.mixy.data.model.ViewMode
import com.grillpoet.mixy.nav.Screen
import com.grillpoet.mixy.ui.folder.components.FolderContentHeader
import com.grillpoet.mixy.ui.folder.components.FolderGridList
import com.grillpoet.mixy.ui.folder.components.FolderRowList
import com.grillpoet.mixy.ui.settings.ViewSettingsSheet
import com.grillpoet.mixy.vm.FolderViewModel

/**
 * 文件夹内容页：显示一个文件夹内的所有媒体项。
 *
 * 编排层：负责状态收集、排序、导航分发。
 * 具体的列表渲染委托给 [FolderGridList] / [FolderRowList]，
 * 顶部栏委托给 [FolderContentHeader]。
 *
 * @param folder 当前文件夹。包含文件夹名、路径、媒体项列表。
 * @param container 应用依赖容器。用于读取设置和创建 ViewModel。
 * @param onNavigate 导航回调。点击媒体项时触发。
 */
@Composable
fun FolderContentScreen(
    folderPath: String,
    container: AppContainer,
    onNavigate: (Screen) -> Unit
) {
    val settings by container.settingsStore.flow.collectAsState()
    val vm: FolderViewModel = viewModel(factory = container.viewModelFactory)
    val vmState by vm.state.collectAsState()

    val folder = vmState.folders.find { it.folderPath == folderPath }
    if (folder == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var showViewSettings by remember { mutableStateOf(false) }

    val sortedItems = remember(folder.items, settings.sortMode, settings.sortOrder) {
        MediaSorter.sortItems(
            folder.items,
            SortSpec(settings.sortMode, settings.sortOrder)
        )
    }

    val onItemClick: (Int) -> Unit = { index ->
        val cursor = PlaylistCursor(
            items = sortedItems,
            currentIndex = index,
            sourceType = folder.type
        )
        when (folder.type) {
            MediaType.MUSIC -> onNavigate(Screen.MusicPlayer(cursor))
            MediaType.IMAGE -> onNavigate(Screen.ImageViewer(cursor))
            MediaType.VIDEO -> onNavigate(Screen.VideoPlayer(cursor))
            MediaType.PDF -> onNavigate(Screen.PdfViewer(cursor))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        FolderContentHeader(
            folderName = folder.folderName,
            folderPath = folder.folderPath,
            itemCount = sortedItems.size,
            onOpenViewSettings = { showViewSettings = true }
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (settings.viewMode == ViewMode.GRID) {
                FolderGridList(
                    items = sortedItems,
                    gridColumns = settings.gridColumns,
                    onItemClick = onItemClick
                )
            } else {
                FolderRowList(
                    items = sortedItems,
                    onItemClick = onItemClick
                )
            }
        }
    }

    if (showViewSettings) {
        ViewSettingsSheet(
            view = settings.viewMode,
            gridColumns = settings.gridColumns,
            sortMode = settings.sortMode,
            sortOrder = settings.sortOrder,
            onView = { v -> container.settingsStore.update { it.copy(viewMode = v) } },
            onGridColumns = { n -> container.settingsStore.update { it.copy(gridColumns = n) } },
            onSortMode = { m -> container.settingsStore.update { it.copy(sortMode = m) } },
            onSortOrder = { o -> container.settingsStore.update { it.copy(sortOrder = o) } },
            onDismiss = { showViewSettings = false }
        )
    }
}
