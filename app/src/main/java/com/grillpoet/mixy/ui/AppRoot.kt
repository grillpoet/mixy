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
import com.grillpoet.mixy.ui.music.components.MiniBar
import com.grillpoet.mixy.ui.pdf.PdfViewerScreen
import com.grillpoet.mixy.ui.settings.ExcludedPathsScreen
import com.grillpoet.mixy.ui.settings.SettingsScreen
import com.grillpoet.mixy.ui.video.VideoPlayerScreen

/** MiniBar 高度估算（封面 40dp + 上下 padding 6+6 + 阴影余量）。 */
private val MINI_BAR_SPACE = 72.dp

/** 只有列表类页面才在底部为 MiniBar 预留空间、才显示 MiniBar。 */
private fun Screen.hostsMiniBar(): Boolean = when (this) {
    Screen.FolderList, Screen.Settings, Screen.ExcludedPaths -> true
    is Screen.FolderContent -> true
    else -> false
}

@Composable
fun AppRoot(container: AppContainer) {
    val nav = remember { Navigator() }
    val context = LocalContext.current
    val saveableStateHolder = rememberSaveableStateHolder()

    // 用 collectAsState 订阅，不用 StateFlow.value：
    // 后者在 composition 内读会被 lint 拦，且值变化不触发重组。
    val musicState by container.musicPlayerController.state.collectAsState()

    LaunchedEffect(container) {
        // Channel 保证每个 URI 只处理一次，重组不会重复触发。
        container.playbackSession.externalUri.collect { uri ->
            val cursor = ExternalUriResolver.toCursor(context, uri) ?: return@collect
            val target = when (cursor.sourceType) {
                MediaType.IMAGE -> Screen.ImageViewer(cursor)
                MediaType.VIDEO -> Screen.VideoPlayer(cursor)
                MediaType.MUSIC -> Screen.MusicPlayer(cursor)
                MediaType.PDF -> Screen.PdfViewer(cursor)
            }
            nav.push(target)
        }
    }

    BackHandler(enabled = true) {
        if (!nav.pop()) {
            (context as? Activity)?.finish()
        }
    }

    val current = nav.current
    val reserveBottom = current.hostsMiniBar() && musicState != null

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // 列表类页面底部预留 MiniBar 高度，避免最后一项被遮挡。
                .padding(bottom = if (reserveBottom) MINI_BAR_SPACE else 0.dp)
        ) {
            // current 是 val，分支内直接 smart cast，不再引入 screen 变量。
            when (current) {
                is Screen.FolderList ->
                    saveableStateHolder.SaveableStateProvider("folder_list") {
                        FolderListScreen(container = container, onNavigate = nav::push)
                    }

                is Screen.FolderContent ->
                    saveableStateHolder.SaveableStateProvider(
                        "folder_content_${current.mediaType.name}_${current.folderPath}"
                    ) {
                        FolderContentScreen(
                            mediaType = current.mediaType,
                            folderPath = current.folderPath,
                            container = container,
                            onNavigate = nav::push,
                            onBack = { nav.pop() }
                        )
                    }

                is Screen.Settings ->
                    saveableStateHolder.SaveableStateProvider("settings") {
                        SettingsScreen(
                            container = container,
                            onNavigate = nav::push,
                            onBack = { nav.pop() }
                        )
                    }

                is Screen.ExcludedPaths ->
                    saveableStateHolder.SaveableStateProvider("excluded_paths") {
                        ExcludedPathsScreen(container = container, onBack = { nav.pop() })
                    }

                is Screen.MusicPlayer ->
                    saveableStateHolder.SaveableStateProvider("music_player") {
                        MusicPlayerScreen(
                            cursor = current.cursor,
                            container = container,
                            onBack = { nav.pop() }
                        )
                    }

                is Screen.ImageViewer -> key(current) {
                    ImageViewerScreen(cursor = current.cursor, onBack = { nav.pop() })
                }

                is Screen.VideoPlayer ->
                    saveableStateHolder.SaveableStateProvider("video_player") {
                        VideoPlayerScreen(
                            cursor = current.cursor,
                            container = container,
                            onBack = { nav.pop() }
                        )
                    }

                is Screen.PdfViewer ->
                    saveableStateHolder.SaveableStateProvider("pdf_viewer") {
                        PdfViewerScreen(
                            cursor = current.cursor,
                            container = container,
                            onBack = { nav.pop() }
                        )
                    }
            }
        }

        // 只在列表类页面显示 MiniBar。全屏媒体页一律不显示。
        if (current.hostsMiniBar()) {
            MiniBar(
                container = container,
                onOpenPlayer = {
                    // 事件回调，不是 composition 期间，读 .value 合法且能拿到最新值。
                    val cursor = container.musicPlayerController.state.value?.cursor
                        ?: container.playbackSession.lastMusicCursor.value
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