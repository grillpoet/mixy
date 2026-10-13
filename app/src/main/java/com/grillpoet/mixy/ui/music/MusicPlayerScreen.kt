package com.grillpoet.mixy.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.ui.music.components.LyricsView
import com.grillpoet.mixy.ui.music.components.MusicControlPanel
import com.grillpoet.mixy.ui.music.components.MusicCover

/**
 * 音乐播放页。
 *
 * 编排层：持有 ViewModel、组装封面 / 歌词 / 控制面板三个子模块。
 * 切换歌曲时自动加载歌词。
 *
 * @param cursor 播放队列。包含所有媒体项和当前索引。
 * @param container 应用依赖容器。
 */
@Composable
fun MusicPlayerScreen(
    cursor: PlaylistCursor,
    container: AppContainer
) {
    val vm: MusicPlayerViewModel = viewModel(factory = container.viewModelFactory)

    // 切文件夹时替换播放队列
    LaunchedEffect(cursor) {
        vm.startPlayback(cursor)
    }

    val state by vm.state.collectAsState()
    val s = state ?: return

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部信息
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = s.current?.displayName.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = s.current?.folderName.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 封面
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 60.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            MusicCover(
                audioUri = s.current?.uri,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
            )
        }

        // 歌词
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            LyricsView(
                lyrics = s.lyrics,
                position = s.position,
                onSeek = vm::seekTo,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 控制面板
        MusicControlPanel(
            state = s,
            onToggle = vm::togglePlayPause,
            onNext = vm::next,
            onPrev = vm::prev,
            onSeek = vm::seekTo,
            onCycleMode = vm::cyclePlayMode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}
