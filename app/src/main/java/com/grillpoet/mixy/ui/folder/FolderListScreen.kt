package com.grillpoet.mixy.ui.folder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.R
import com.grillpoet.mixy.data.PermissionHelper
import com.grillpoet.mixy.data.model.MediaFolder
import com.grillpoet.mixy.data.model.MediaItem
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.data.model.ViewMode
import com.grillpoet.mixy.ui.common.CenteredMessage
import com.grillpoet.mixy.ui.common.MediaThumbnail
import com.grillpoet.mixy.ui.common.MediaTypeChips
import com.grillpoet.mixy.ui.nav.Screen
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(
    container: AppContainer,
    onNavigate: (Screen) -> Unit
) {
    val vm: FolderViewModel = viewModel(factory = container.viewModelFactory)
    val state by vm.state.collectAsState()
    val settings by container.settingsStore.flow.collectAsState()
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val effectiveColumns = if (isLandscape) {
        (settings.gridColumns + 1).coerceAtMost(5)
    } else {
        settings.gridColumns
    }

    var permissionTick by remember { mutableIntStateOf(0) }
    val hasPermission = remember(state.currentType, permissionTick) {
        PermissionHelper.hasForType(context, state.currentType)
    }

    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(permissionLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionTick++
            }
        }
        permissionLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { permissionLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRequested = true
        permissionTick++
        if (PermissionHelper.hasForType(context, state.currentType)) {
            vm.refresh()
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            vm.setPdfTreeUri(uri, context)
        }
    }

    var lastGranted by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(hasPermission) {
        if (lastGranted == false && hasPermission) {
            vm.refresh()
        }
        lastGranted = hasPermission
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                value = state.searchQuery,
                onValueChange = vm::updateSearch,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { onNavigate(Screen.Settings) }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        MediaTypeChips(
            visibleTypes = state.visibleChips,
            current = state.currentType,
            onSelect = vm::selectType,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Spacer(Modifier.height(4.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                !hasPermission -> CenteredMessage(
                    title = stringResource(R.string.permission_title),
                    description = stringResource(
                        if (permissionRequested) R.string.permission_denied
                        else R.string.permission_desc
                    ),
                    actionText = stringResource(
                        if (permissionRequested) R.string.permission_open_settings
                        else R.string.permission_grant
                    ),
                    onAction = {
                        if (permissionRequested) {
                            val intent = Intent(
                                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        } else {
                            val perms = PermissionHelper.forType(state.currentType) +
                                    PermissionHelper.notificationPermissions()
                            if (perms.isNotEmpty()) permissionLauncher.launch(perms)
                        }
                    }
                )

                state.error != null -> CenteredMessage(
                    title = stringResource(R.string.error_load_failed),
                    description = state.errorDetail,
                    actionText = stringResource(R.string.action_retry),
                    onAction = { vm.refresh() }
                )
                state.currentType == MediaType.PDF && settings.pdfTreeUri == null ->
                    CenteredMessage(
                        title = stringResource(R.string.pdf_folder_title),
                        description = stringResource(R.string.pdf_folder_desc),
                        actionText = stringResource(R.string.pdf_folder_choose),
                        onAction = { pdfLauncher.launch(null) }
                    )

                state.currentType == MediaType.PDF && state.scanProgress != null ->
                    ScanProgress(state.scanProgress!!)

                state.skeletonVisible -> SkeletonList()

                state.isSearching -> SearchResultList(
                    items = state.searchResults,
                    gridMode = settings.viewMode == ViewMode.GRID,
                    gridColumns = effectiveColumns,
                    onItemClick = { index ->
                        if (state.searchResults.isNotEmpty()) {
                            val cursor = PlaylistCursor(
                                items = state.searchResults,
                                currentIndex = index,
                                sourceType = state.currentType
                            )
                            onNavigate(
                                when (state.currentType) {
                                    MediaType.MUSIC -> Screen.MusicPlayer(cursor)
                                    MediaType.IMAGE -> Screen.ImageViewer(cursor)
                                    MediaType.VIDEO -> Screen.VideoPlayer(cursor)
                                    MediaType.PDF -> Screen.PdfViewer(cursor)
                                }
                            )
                        }
                    }
                )

                state.folders.isEmpty() -> CenteredMessage(
                    title = stringResource(R.string.folder_empty_title),
                    description = stringResource(R.string.folder_empty_desc)
                )

                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    FolderList(
                        folders = state.folders,
                        gridMode = settings.viewMode == ViewMode.GRID,
                        gridColumns = effectiveColumns,
                        onNavigate = onNavigate
                    )
                }
            }
        }
    }
}

// ==================== 搜索栏 ====================

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = stringResource(R.string.action_search),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    stringResource(R.string.search_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (value.isNotEmpty()) 24.dp else 0.dp)
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_clear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}

// ==================== 文件夹列表 ====================

@Composable
private fun FolderList(
    folders: List<MediaFolder>,
    gridMode: Boolean,
    gridColumns: Int,
    onNavigate: (Screen) -> Unit
) {
    if (gridMode) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            gridItemsIndexed(
                items = folders,
                key = { _, folder -> folder.folderPath }
            ) { _, folder ->
                FolderCardGrid(
                    folder = folder,
                    onClick = {
                        onNavigate(Screen.FolderContent(folder.type, folder.folderPath))
                    }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(folders, key = { it.folderPath }) { folder ->
                FolderCard(
                    folder = folder,
                    onClick = {
                        onNavigate(Screen.FolderContent(folder.type, folder.folderPath))
                    }
                )
            }
        }
    }
}

// ==================== 列表卡片 ====================

@Composable
private fun FolderCard(
    folder: MediaFolder,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            val cover = folder.items.firstOrNull()
            if (cover != null) {
                MediaThumbnail(item = cover, modifier = Modifier.fillMaxSize())
            } else {
                EmptyCoverFallback(fontSize = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = folder.folderName.ifBlank { folder.folderPath },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.folder_item_count,
                    folder.items.size
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FolderCardGrid(
    folder: MediaFolder,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            val cover = folder.items.firstOrNull()
            if (cover != null) {
                MediaThumbnail(item = cover, modifier = Modifier.fillMaxSize())
            } else {
                EmptyCoverFallback(fontSize = MaterialTheme.typography.headlineMedium)
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = folder.folderName.ifBlank { folder.folderPath },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(
                R.string.folder_item_count,
                folder.items.size
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== 搜索结果 ====================

@Composable
private fun SearchResultList(
    items: List<MediaItem>,
    gridMode: Boolean,
    gridColumns: Int,
    onItemClick: (Int) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.search_no_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (gridMode) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            gridItemsIndexed(
                items = items,
                key = { _, item -> item.uri.toString() }
            ) { index, item ->
                SearchResultGridCell(item = item, onClick = { onItemClick(index) })
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.uri.toString() }
            ) { index, item ->
                SearchResultRow(item = item, onClick = { onItemClick(index) })
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: MediaItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            MediaThumbnail(item = item, modifier = Modifier.fillMaxSize())
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.folderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchResultGridCell(
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
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )
        Spacer(Modifier.height(2.dp))
    }
}

// ==================== 骨架屏 ====================

@Composable
private fun SkeletonList() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}

// ==================== 扫描进度 ====================

@Composable
private fun ScanProgress(count: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.pdf_scanning, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 封面占位 ====================

@Composable
private fun EmptyCoverFallback(
    fontSize: androidx.compose.ui.text.TextStyle
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "—",
            style = fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}