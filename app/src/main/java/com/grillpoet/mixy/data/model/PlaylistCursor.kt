package com.grillpoet.mixy.data.model
import androidx.compose.runtime.Immutable

@Immutable
data class PlaylistCursor(
    val items: List<MediaItem>,
    val currentIndex: Int = 0,
    val sourceType: MediaType
) {
    val current: MediaItem? get() = items.getOrNull(currentIndex)
    val hasNext: Boolean get() = currentIndex < items.size - 1
    val hasPrev: Boolean get() = currentIndex > 0

    fun next(): PlaylistCursor = if (hasNext) copy(currentIndex = currentIndex + 1) else this
    fun prev(): PlaylistCursor = if (hasPrev) copy(currentIndex = currentIndex - 1) else this
    fun seek(index: Int): PlaylistCursor =
        if (index in items.indices) copy(currentIndex = index) else this
}