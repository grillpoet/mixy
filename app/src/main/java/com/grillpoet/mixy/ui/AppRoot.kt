package com.grillpoet.mixy.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.data.ExternalUriResolver
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.nav.Navigator
import com.grillpoet.mixy.nav.Screen
import com.grillpoet.mixy.ui.folder.FolderContentScreen
import com.grillpoet.mixy.ui.folder.FolderListScreen
import com.grillpoet.mixy.ui.image.ImageViewerScreen
import com.grillpoet.mixy.ui.music.MusicPlayerScreen
import com.grillpoet.mixy.ui.pdf.PdfViewerScreen
import com.grillpoet.mixy.ui.settings.ExcludedPathsScreen
import com.grillpoet.mixy.ui.settings.SettingsScreen
import com.grillpoet.mixy.ui.video.VideoPlayerScreen
import com.grillpoet.mixy.ui.widget.MiniBar

@Composable
fun AppRoot(container: AppContainer) {
    val nav = remember { Navigator() }
    val context = LocalContext.current
    val saveableStateHolder = rememberSaveableStateHolder()

    // 外部 App 传入的 URI（ACTION_VIEW / ACTION_SEND）
    val externalUri by container.playbackSession.externalUri.collectAsState()
    LaunchedEffect(externalUri) {
        val uri = externalUri ?: return@LaunchedEffect
        val cursor = ExternalUriResolver.toCursor(context, uri)
        if (cursor != null) {
            val target = when (cursor.sourceType) {
                MediaType.IMAGE -> Screen.ImageViewer(cursor)
                MediaType.VIDEO -> Screen.VideoPlayer(cursor)
                MediaType.MUSIC -> Screen.MusicPlayer(cursor)
                MediaType.PDF -> Screen.PdfViewer(cursor)
            }
            nav.push(target)
        }
        // 消费掉，避免重组时重复跳转
        container.playbackSession.consumeExternalUri()
    }

    // 全局接管返回键
    BackHandler(enabled = true) {
        if (!nav.pop()) {
            (context as? Activity)?.finish()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            when (val screen = nav.current) {
                is Screen.FolderList ->
                    saveableStateHolder.SaveableStateProvider("folder_list") {
                        FolderListScreen(
                            container = container,
                            onNavigate = nav::push
                        )
                    }

                is Screen.FolderContent ->
                    saveableStateHolder.SaveableStateProvider(
                        "folder_content_${screen.folderPath}"
                    ) {
                        FolderContentScreen(
                            folderPath = screen.folderPath,
                            container = container,
                            onNavigate = nav::push
                        )
                    }

                is Screen.Settings ->
                    saveableStateHolder.SaveableStateProvider("settings") {
                        SettingsScreen(
                            container = container,
                            onNavigate = nav::push
                        )
                    }

                is Screen.ExcludedPaths ->
                    saveableStateHolder.SaveableStateProvider("excluded_paths") {
                        ExcludedPathsScreen(container = container)
                    }

                is Screen.MusicPlayer ->
                    saveableStateHolder.SaveableStateProvider("music_player") {
                        MusicPlayerScreen(
                            cursor = screen.cursor,
                            container = container
                        )
                    }

                is Screen.ImageViewer -> key(screen) {
                    ImageViewerScreen(cursor = screen.cursor)
                }

                is Screen.VideoPlayer ->
                    saveableStateHolder.SaveableStateProvider("video_player") {
                        VideoPlayerScreen(
                            cursor = screen.cursor,
                            container = container
                        )
                    }

                is Screen.PdfViewer ->
                    saveableStateHolder.SaveableStateProvider("pdf_viewer") {
                        PdfViewerScreen(
                            cursor = screen.cursor,
                            container = container
                        )
                    }
            }
        }

        // 迷你栏：不在音乐播放页时显示
        val showMiniBar = nav.current !is Screen.MusicPlayer
        if (showMiniBar) {
            MiniBar(
                container = container,
                onOpenPlayer = {
                    // 用 PlaybackSession 记录的上一个 cursor，信息完整
                    val cursor = container.playbackSession.lastMusicCursor.value
                        ?: return@MiniBar
                    if (nav.current !is Screen.MusicPlayer) {
                        nav.push(Screen.MusicPlayer(cursor))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}