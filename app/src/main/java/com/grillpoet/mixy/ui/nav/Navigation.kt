package com.grillpoet.mixy.ui.nav

import androidx.compose.runtime.mutableStateListOf

import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.data.model.PlaylistCursor

sealed interface Screen {
    data object FolderList : Screen
    data object Settings : Screen
    data object ExcludedPaths : Screen

    data class FolderContent(
        val mediaType: MediaType,
        val folderPath: String
    ) : Screen
    data class MusicPlayer(val cursor: PlaylistCursor) : Screen
    data class ImageViewer(val cursor: PlaylistCursor) : Screen
    data class VideoPlayer(val cursor: PlaylistCursor) : Screen
    data class PdfViewer(val cursor: PlaylistCursor) : Screen
}

class Navigator {
    private companion object { const val MAX_STACK = 20 }

    private val _stack = mutableStateListOf<Screen>(Screen.FolderList)
    val current: Screen get() = _stack.last()

    fun push(screen: Screen) {
        if (_stack.size >= MAX_STACK) return
        if (_stack.lastOrNull() == screen) return
        _stack.add(screen)
    }

    fun pop(): Boolean {
        if (_stack.size <= 1) return false
        _stack.removeAt(_stack.lastIndex)
        return true
    }
}