package com.grillpoet.mixy.vm

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

class FolderViewModel(
    private val repo: MediaRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(FolderUiState())
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    private var currentSettings: Settings = settings.flow.value

    /**
     * 加载代次。
     *
     * 每次 [load] 递增。异步回调（进度、收尾写入）拿到的是调用时的
     * 快照值，与当前值不一致即代表期间用户已切走，结果丢弃。
     *
     * 用于解决：切到别的类型后，慢加载（尤其 PDF 扫描）完成时
     * 把 state.folders 覆盖成旧类型的列表。
     */
    private var loadGeneration = 0

    init {
        viewModelScope.launch {
            combine(repo.folders, settings.flow) { map, s -> map to s }
                .collect { (map, s) ->
                    currentSettings = s

                    val visible = s.visibleChips.ifEmpty { listOf(MediaType.MUSIC) }
                    val current = _state.value.currentType
                    val fixed = if (current !in visible) visible.first() else current

                    val rawList = map[fixed].orEmpty()
                    val sortedFolders = MediaSorter.sortFolders(
                        rawList, SortSpec(s.sortMode, s.sortOrder)
                    )

                    val query = _state.value.searchQuery
                    val newSearchResults: List<MediaItem> = if (query.isBlank()) {
                        _state.value.searchResults
                    } else {
                        val all = rawList.flatMap { it.items }
                        val filtered = all.filter {
                            it.displayName.contains(query, ignoreCase = true) ||
                                    it.folderName.contains(query, ignoreCase = true)
                        }
                        MediaSorter
                            .sortItems(filtered, SortSpec(s.sortMode, s.sortOrder))
                            .take(500)
                    }

                    _state.update {
                        it.copy(
                            currentType = fixed,
                            visibleChips = visible,
                            folders = sortedFolders,
                            searchResults = newSearchResults,
                            isLoading = false,
                            skeletonVisible = false,
                            scanProgress = null
                        )
                    }

                    if (current !in visible) load(fixed)
                }
        }

        selectType(MediaType.MUSIC)
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

    fun updateSearch(q: String) {
        _state.update { it.copy(searchQuery = q, searchResults = emptyList()) }
        if (q.isBlank()) return
        viewModelScope.launch {
            val all = _state.value.folders.flatMap { it.items }
            val filtered = all.filter {
                it.displayName.contains(q, ignoreCase = true) ||
                        it.folderName.contains(q, ignoreCase = true)
            }
            val sorted = MediaSorter.sortItems(
                filtered,
                SortSpec(currentSettings.sortMode, currentSettings.sortOrder)
            ).take(500)
            _state.update { it.copy(searchResults = sorted) }
        }
    }

    fun setPdfTreeUri(uri: Uri, context: Context) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        settings.update { it.copy(pdfTreeUri = uri.toString()) }
        // 强制清缓存 + 刷新
        repo.invalidate(MediaType.PDF)
        load(MediaType.PDF, forceRefresh = true)
    }

    private fun load(type: MediaType, forceRefresh: Boolean = false) {
        val generation = ++loadGeneration
        viewModelScope.launch {
            val excluded = currentSettings.excludedPaths
            val pdfUri = currentSettings.pdfTreeUri

            val onProgress: ((Int) -> Unit)? =
                if (type == MediaType.PDF && pdfUri != null) {
                    { count ->
                        if (loadGeneration == generation) {
                            _state.update { it.copy(scanProgress = count) }
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

            // 期间用户可能已切换到别的类型：丢弃本次结果。
            if (loadGeneration != generation) return@launch

            val list = repo.folders.value[type].orEmpty()
            _state.update {
                it.copy(
                    folders = list,
                    isLoading = false,
                    skeletonVisible = false,
                    scanProgress = null
                )
            }
        }
    }
}