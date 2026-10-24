package com.grillpoet.mixy.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.data.MediaSorter
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.data.model.SortSpec
import com.grillpoet.mixy.data.model.ViewMode
import com.grillpoet.mixy.nav.Screen
import com.grillpoet.mixy.ui.folder.components.FolderGridList
import com.grillpoet.mixy.ui.folder.components.FolderRowList
import com.grillpoet.mixy.ui.settings.ViewSettingsSheet
import com.grillpoet.mixy.ui.widget.MixyTopBar

@Composable
fun FolderContentScreen(
    mediaType: MediaType,
    folderPath: String,
    container: AppContainer,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit
) {
    val settings by container.settingsStore.flow.collectAsState()

    // 直接订阅仓库，不依赖列表页的 FolderViewModel。
    val foldersMap by container.mediaRepository.folders.collectAsState()
    val typedList = foldersMap[mediaType]

    // 冷启动 / deep link 直达时仓库还没数据，触发一次加载。
    // 列表页已经 load 过则 typedList 非 null，不会重复扫描。
    LaunchedEffect(mediaType, typedList) {
        if (typedList == null) {
            val s = container.settingsStore.flow.value
            container.mediaRepository.load(
                type = mediaType,
                excludedPaths = s.excludedPaths,
                pdfTreeUri = s.pdfTreeUri
            )
        }
    }

    val folder = remember(typedList, folderPath) {
        typedList?.find { it.folderPath == folderPath }
    }

    if (folder == null) {
        Column(Modifier.fillMaxSize()) {
            MixyTopBar(title = "文件夹", onBack = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (typedList == null) {
                    CircularProgressIndicator()
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "文件夹不存在或已被移除",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "请返回上一页刷新列表",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
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
        MixyTopBar(
            title = folder.folderName.ifBlank { folder.folderPath },
            subtitle = "${sortedItems.size} 项 · ${folder.folderPath}",
            onBack = onBack,
            actions = {
                IconButton(onClick = { showViewSettings = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = "视图设置")
                }
            }
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