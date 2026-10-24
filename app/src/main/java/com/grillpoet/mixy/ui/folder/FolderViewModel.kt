package com.grillpoet.mixy.ui.folder

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grillpoet.mixy.data.MediaRepository
import com.grillpoet.mixy.data.MediaSorter
import com.grillpoet.mixy.data.Settings
import com.grillpoet.mixy.data.SettingsStore
import com.grillpoet.mixy.data.model.MediaFolder
import com.grillpoet.mixy.data.model.MediaItem
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.data.model.SortSpec
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

data class FolderUiState(
    val currentType: MediaType = MediaType.MUSIC,
    val visibleChips: List<MediaType> = listOf(MediaType.MUSIC),
    val folders: List<MediaFolder> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val skeletonVisible: Boolean = true,
    val scanProgress: Int? = null
) {
    val isSearching: Boolean get() = searchQuery.isNotBlank()
}

@OptIn(FlowPreview::class)
class FolderViewModel(
    private val repo: MediaRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(FolderUiState())
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    private var currentSettings: Settings = Settings()
    private var loadGeneration = 0
    private var prevPdfTreeUri: String? = null

    init {
        viewModelScope.launch {
            // 冷启动必须先等 DataStore 恢复，否则首次 load 用默认设置
            // （排除路径、PDF 目录都不对）。
            settings.awaitLoaded()
            currentSettings = settings.flow.value

            launch {
                // 搜索输入去抖后作为第三路流参与 combine，
                // 让 searchResults 只有一个写入源。
                val queryFlow = _state
                    .map { it.searchQuery }
                    .distinctUntilChanged()
                    .debounce(200.milliseconds)

                combine(repo.folders, settings.flow, queryFlow) { map, s, q ->
                    Triple(map, s, q)
                }.collect { (map, s, q) -> applyState(map, s, q) }
            }

            selectType(MediaType.MUSIC)
        }
    }

    private fun applyState(
        map: Map<MediaType, List<MediaFolder>>,
        s: Settings,
        query: String
    ) {
        currentSettings = s

        val newPdfUri = s.pdfTreeUri
        if (prevPdfTreeUri != null && prevPdfTreeUri != newPdfUri) {
            repo.invalidate(MediaType.PDF)
            if (_state.value.currentType == MediaType.PDF) {
                load(MediaType.PDF, forceRefresh = true)
            }
        }
        prevPdfTreeUri = newPdfUri

        val visible = s.visibleChips.ifEmpty { listOf(MediaType.MUSIC) }
        val current = _state.value.currentType
        val fixed = if (current !in visible) visible.first() else current

        val rawList = map[fixed].orEmpty()
        val sortedFolders = MediaSorter.sortFolders(
            rawList, SortSpec(s.sortMode, s.sortOrder)
        )

        val newSearchResults: List<MediaItem> = if (query.isBlank()) {
            emptyList()
        } else {
            val all = rawList.flatMap { it.items }
            MediaSorter.sortItems(
                all.filter {
                    it.displayName.contains(query, ignoreCase = true) ||
                            it.folderName.contains(query, ignoreCase = true)
                },
                SortSpec(s.sortMode, s.sortOrder)
            ).take(500)
        }

        val hasData = map.containsKey(fixed)

        _state.update {
            it.copy(
                currentType = fixed,
                visibleChips = visible,
                folders = sortedFolders,
                searchResults = newSearchResults,
                isLoading = false,
                skeletonVisible = !hasData,
                scanProgress = null
            )
        }

        if (current !in visible) load(fixed)
    }

    fun selectType(type: MediaType) {
        if (_state.value.currentType == type && _state.value.folders.isNotEmpty()) return
        _state.update {
            it.copy(
                currentType = type,
                folders = emptyList(),
                searchQuery = "",
                searchResults = emptyList(),
                isLoading = true,
                skeletonVisible = true,
                scanProgress = null
            )
        }
        load(type)
    }

    fun refresh() {
        _state.update {
            it.copy(
                isLoading = true,
                skeletonVisible = it.folders.isEmpty(),
                scanProgress = null
            )
        }
        load(_state.value.currentType, forceRefresh = true)
    }

    /** 只更新输入框显示值。搜索结果由 combine 在 200ms 去抖后派生。 */
    fun updateSearch(q: String) {
        _state.update { it.copy(searchQuery = q) }
    }

    fun setPdfTreeUri(uri: Uri, context: Context) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        settings.update { it.copy(pdfTreeUri = uri.toString()) }
    }

    private fun load(type: MediaType, forceRefresh: Boolean = false) {
        val generation = ++loadGeneration
        viewModelScope.launch {
            val excluded = currentSettings.excludedPaths
            val pdfUri = currentSettings.pdfTreeUri

            val lastProgressAt = AtomicLong(0L)
            val onProgress: ((Int) -> Unit)? =
                if (type == MediaType.PDF && pdfUri != null) {
                    { count ->
                        if (loadGeneration == generation) {
                            val now = System.currentTimeMillis()
                            if (count <= 1 || now - lastProgressAt.get() >= 100) {
                                lastProgressAt.set(now)
                                _state.update { it.copy(scanProgress = count) }
                            }
                        }
                    }
                } else null

            if (type == MediaType.PDF && pdfUri != null) {
                _state.update { it.copy(scanProgress = 0) }
            }

            repo.load(
                type = type,
                forceRefresh = forceRefresh,
                excludedPaths = excluded,
                pdfTreeUri = pdfUri,
                onScanProgress = onProgress
            )

            if (loadGeneration != generation) return@launch

            // 兜底写入排序后的结果，避免 combine 因 StateFlow equals 去重
            // 未触发时留下未排序数据。
            val list = repo.folders.value[type].orEmpty()
            val sorted = MediaSorter.sortFolders(
                list, SortSpec(currentSettings.sortMode, currentSettings.sortOrder)
            )
            _state.update {
                it.copy(
                    folders = sorted,
                    isLoading = false,
                    skeletonVisible = false,
                    scanProgress = null
                )
            }
        }
    }
}