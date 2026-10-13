package com.grillpoet.mixy.data

import com.grillpoet.mixy.data.model.MediaFolder
import com.grillpoet.mixy.data.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MediaRepository(
    private val dataSource: MediaStoreDataSource,
    private val cache: MediaCache
) {
    private val _folders = MutableStateFlow<Map<MediaType, List<MediaFolder>>>(emptyMap())
    val folders: StateFlow<Map<MediaType, List<MediaFolder>>> = _folders.asStateFlow()

    suspend fun load(
        type: MediaType,
        forceRefresh: Boolean = false,
        excludedPaths: List<String> = emptyList(),
        pdfTreeUri: String? = null,
        onScanProgress: ((Int) -> Unit)? = null
    ) {
        if (!forceRefresh) {
            cache.get(type)?.let {
                _folders.update { m -> m + (type to it) }
                return
            }
        }
        val folders = dataSource.queryFolders(
            type, excludedPaths, pdfTreeUri, onScanProgress
        )
        cache.put(type, folders)
        _folders.update { m -> m + (type to folders) }
    }

    fun invalidate(type: MediaType) { cache.invalidate(type) }
}