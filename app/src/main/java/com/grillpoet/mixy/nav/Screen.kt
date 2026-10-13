package com.grillpoet.mixy.nav

import com.grillpoet.mixy.data.model.PlaylistCursor

sealed interface Screen {
    data object FolderList : Screen
    data object Settings : Screen
    data object ExcludedPaths : Screen

    data class FolderContent(val folderPath: String) : Screen
    data class MusicPlayer(val cursor: PlaylistCursor) : Screen
    data class ImageViewer(val cursor: PlaylistCursor) : Screen
    data class VideoPlayer(val cursor: PlaylistCursor) : Screen
    data class PdfViewer(val cursor: PlaylistCursor) : Screen
}