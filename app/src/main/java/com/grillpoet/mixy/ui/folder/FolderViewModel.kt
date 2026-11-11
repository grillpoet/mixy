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
import kotlinx.coroutines.CancellationException
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

enum class FolderError {
    LOAD_FAILED,
}

data class FolderUiState(
    val currentType: MediaType = MediaType.MUSIC,
    val visibleChips: List<MediaType> = listOf(MediaType.MUSIC),
    val folders: List<MediaFolder> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val skeletonVisible: Boolean = true,
    val scanProgress: Int? = null,
    val error: FolderError? = null,
    val errorDetail: String? = null
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

    private var loadGeneration = 0

    // 用 ready 标志判断"已初始化"，而不是用 `!= null`。
    // pdfTreeUri 默认值就是 null，用 `!= null` 会把"用户第一次选目录"
    // 误判成"未初始化"，导致 load 永远不触发。
    private var prevPdfTreeUri: String? = null
    private var pdfTreeUriReady = false

    private var prevExcludedPaths: List<String>? = null
    private var excludedPathsReady = false

    init {
        viewModelScope.launch {
            settings.awaitLoaded()
            val s0 = settings.flow.value
            prevPdfTreeUri = s0.pdfTreeUri
            pdfTreeUriReady = true
            prevExcludedPaths = s0.excludedPaths
            excludedPathsReady = true

            launch {
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
        // PDF 目录切换：作废缓存并强制重扫。
        // 用 pdfTreeUriReady 而不是 prevPdfTreeUri != null：
        // 后者在"用户第一次选目录"（prev = null, new = "content://..."）时
        // 会因 null 判断短路，永远不触发 load。
        val newPdfUri = s.pdfTreeUri
        if (pdfTreeUriReady && prevPdfTreeUri != newPdfUri) {
            repo.invalidate(MediaType.PDF)
            if (_state.value.currentType == MediaType.PDF) {
                load(MediaType.PDF, forceRefresh = true)
            }
        }
        prevPdfTreeUri = newPdfUri

        // 排除项变更：同上，用 ready 标志。
        val newExcluded = s.excludedPaths
        if (excludedPathsReady && prevExcludedPaths != newExcluded) {
            val cur = _state.value.currentType
            repo.invalidate(cur)
            load(cur, forceRefresh = true)
        }
        prevExcludedPaths = newExcluded

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
                isRefreshing = false,
                skeletonVisible = !hasData,
                scanProgress = null,
                error = if (hasData) null else it.error,
                errorDetail = if (hasData) null else it.errorDetail
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
                isRefreshing = false,
                skeletonVisible = true,
                scanProgress = null,
                error = null,
                errorDetail = null
            )
        }
        load(type)
    }

    fun refresh() {
        val hasData = _state.value.folders.isNotEmpty()
        _state.update {
            it.copy(
                isLoading = !hasData,
                isRefreshing = hasData,
                skeletonVisible = !hasData,
                scanProgress = null,
                error = null,
                errorDetail = null
            )
        }
        load(_state.value.currentType, forceRefresh = true)
    }

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
            settings.awaitLoaded()
            val s = settings.flow.value
            val excluded = s.excludedPaths
            val pdfUri = s.pdfTreeUri

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

            try {
                repo.load(
                    type = type,
                    forceRefresh = forceRefresh,
                    excludedPaths = excluded,
                    pdfTreeUri = pdfUri,
                    onScanProgress = onProgress
                )
                if (loadGeneration != generation) return@launch

                val list = repo.folders.value[type].orEmpty()
                val sorted = MediaSorter.sortFolders(
                    list,
                    SortSpec(s.sortMode, s.sortOrder)
                )
                _state.update {
                    it.copy(
                        folders = sorted,
                        isLoading = false,
                        isRefreshing = false,
                        skeletonVisible = false,
                        scanProgress = null,
                        error = null,
                        errorDetail = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (loadGeneration != generation) return@launch
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        skeletonVisible = false,
                        scanProgress = null,
                        error = FolderError.LOAD_FAILED,
                        errorDetail = e.message
                    )
                }
            }
        }
    }
}