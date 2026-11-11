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

    companion object {
        /**
         * 从 PlaylistCursor 构造目标播放页。
         *
         * 新增 MediaType 时只需在这里加 1 个分支，
         * 4 个调用点（搜索、文件夹内容、外部 URI）自动覆盖。
         *
         * 放在 Screen 的 companion 里，是为了让"类型 → 页面"的映射
         * 与 Screen 定义同文件，避免额外引入 Mapper 文件。
         */
        fun fromCursor(cursor: PlaylistCursor): Screen = when (cursor.sourceType) {
            MediaType.MUSIC -> MusicPlayer(cursor)
            MediaType.IMAGE -> ImageViewer(cursor)
            MediaType.VIDEO -> VideoPlayer(cursor)
            MediaType.PDF -> PdfViewer(cursor)
        }
    }
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