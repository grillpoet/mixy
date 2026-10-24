package com.grillpoet.mixy.ui.folder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.data.PermissionHelper
import com.grillpoet.mixy.data.model.MediaFolder
import com.grillpoet.mixy.data.model.MediaItem
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.data.model.ViewMode
import com.grillpoet.mixy.nav.Screen
import com.grillpoet.mixy.ui.settings.ViewSettingsSheet
import com.grillpoet.mixy.ui.widget.MediaThumbnail
import com.grillpoet.mixy.ui.widget.MediaTypeChips
import com.grillpoet.mixy.ui.widget.MixyTopBar
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

@Composable
fun FolderListScreen(
    container: AppContainer,
    onNavigate: (Screen) -> Unit
) {
    val vm: FolderViewModel = viewModel(factory = container.viewModelFactory)
    val state by vm.state.collectAsState()
    val settings by container.settingsStore.flow.collectAsState()
    val context = LocalContext.current

    var showViewSettings by remember { mutableStateOf(false) }

    // 权限按当前媒体类型判断。PDF 走 SAF，不需要媒体权限（恒为 true）。
    var permissionTick by remember { mutableIntStateOf(0) }
    val hasPermission = remember(state.currentType, permissionTick) {
        PermissionHelper.hasForType(context, state.currentType)
    }

    // 从系统设置返回时刷新权限状态
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

    // 只在「权限从无到有」时刷新
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
        // ==================== 顶部栏 ====================
        MixyTopBar(
            title = "Mixy",
            actions = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = { showViewSettings = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = "视图设置")
                }
                IconButton(onClick = { onNavigate(Screen.Settings) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            }
        )

        // 搜索栏
        SearchBar(
            value = state.searchQuery,
            onValueChange = vm::updateSearch,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // 媒体类型 chips
        MediaTypeChips(
            visibleTypes = state.visibleChips,
            current = state.currentType,
            onSelect = vm::selectType,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(4.dp))

        // ==================== 内容区 ====================
        Box(Modifier.fillMaxSize()) {
            when {
                !hasPermission -> PermissionGuide(
                    onRequest = {
                        val perms = PermissionHelper.forType(state.currentType) +
                                PermissionHelper.notificationPermissions()
                        if (perms.isNotEmpty()) permissionLauncher.launch(perms)
                    }
                )

                state.currentType == MediaType.PDF && settings.pdfTreeUri == null ->
                    PdfDirectoryGuide(onRequest = { pdfLauncher.launch(null) })

                state.currentType == MediaType.PDF && state.scanProgress != null ->
                    ScanProgress(state.scanProgress!!)

                state.skeletonVisible -> SkeletonList()

                state.isSearching -> SearchResultList(
                    items = state.searchResults,
                    gridMode = settings.viewMode == ViewMode.GRID,
                    gridColumns = settings.gridColumns,
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

                state.folders.isEmpty() -> EmptyState()

                else -> FolderList(
                    folders = state.folders,
                    gridMode = settings.viewMode == ViewMode.GRID,
                    gridColumns = settings.gridColumns,
                    onNavigate = onNavigate
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
            Icons.Filled.Search, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    "搜索",
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
                modifier = Modifier.fillMaxWidth()
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
                    onClick = { onNavigate(Screen.FolderContent(folder.type, folder.folderPath)) }
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
                    onClick = { onNavigate(Screen.FolderContent(folder.type, folder.folderPath)) }
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
                text = "${folder.items.size} 项 · ${folder.folderPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==================== 网格卡片 ====================

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
            text = "${folder.items.size} 项",
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
            Text("无结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                SearchResultGridCell(
                    item = item,
                    onClick = { onItemClick(index) }
                )
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
                text = "已扫描 $count 个 PDF…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 空状态 ====================

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("暂无文件夹", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "该类型下没有找到媒体",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 权限引导 ====================

@Composable
private fun PermissionGuide(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("需要媒体权限", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Mixy 需要读取媒体文件才能展示内容",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onRequest)
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text("授予权限", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

// ==================== PDF 目录引导 ====================

@Composable
private fun PdfDirectoryGuide(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("选择 PDF 目录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Android 系统不索引 PDF，请选择一个包含 PDF 的目录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onRequest)
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text("选择目录", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

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