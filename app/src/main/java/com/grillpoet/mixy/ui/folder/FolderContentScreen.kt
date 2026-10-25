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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.R
import com.grillpoet.mixy.data.MediaSorter
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.data.model.SortSpec
import com.grillpoet.mixy.data.model.ViewMode
import com.grillpoet.mixy.ui.common.MixyTopBar
import com.grillpoet.mixy.ui.common.displayFolderPath
import com.grillpoet.mixy.ui.folder.components.FolderGridList
import com.grillpoet.mixy.ui.folder.components.FolderRowList
import com.grillpoet.mixy.ui.nav.Screen
import com.grillpoet.mixy.ui.settings.ViewSettingsSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContentScreen(
    mediaType: MediaType,
    folderPath: String,
    container: AppContainer,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit
) {
    val settings by container.settingsStore.flow.collectAsState()

    val foldersMap by container.mediaRepository.folders.collectAsState()
    val typedList = foldersMap[mediaType]

    LaunchedEffect(mediaType, typedList) {
        if (typedList == null) {
            container.mediaRepository.load(
                type = mediaType,
                excludedPaths = settings.excludedPaths,
                pdfTreeUri = settings.pdfTreeUri
            )
        }
    }

    val folder = remember(typedList, folderPath) {
        typedList?.find { it.folderPath == folderPath }
    }

    if (folder == null) {
        Column(Modifier.fillMaxSize()) {
            MixyTopBar(
                title = stringResource(R.string.content_folder_title),
                onBack = onBack
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (typedList == null) {
                    CircularProgressIndicator()
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.folder_not_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.folder_not_found_hint),
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val effectiveColumns = if (isLandscape) {
        (settings.gridColumns + 1).coerceAtMost(5)
    } else {
        settings.gridColumns
    }

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

    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    val displayPath = remember(folder.folderPath) {
        displayFolderPath(folder.folderPath)
    }
    val subtitle = if (displayPath.isBlank()) {
        stringResource(R.string.folder_item_count, sortedItems.size)
    } else {
        stringResource(R.string.folder_subtitle, sortedItems.size, displayPath)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MixyTopBar(
            title = folder.folderName.ifBlank { folder.folderPath },
            subtitle = subtitle,
            onBack = onBack,
            actions = {
                IconButton(onClick = { showViewSettings = true }) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = stringResource(R.string.action_view_settings)
                    )
                }
            }
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (isRefreshing) return@PullToRefreshBox
                isRefreshing = true
                scope.launch {
                    try {
                        container.mediaRepository.load(
                            type = mediaType,
                            forceRefresh = true,
                            excludedPaths = settings.excludedPaths,
                            pdfTreeUri = settings.pdfTreeUri
                        )
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (settings.viewMode == ViewMode.GRID) {
                FolderGridList(
                    items = sortedItems,
                    gridColumns = effectiveColumns,
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