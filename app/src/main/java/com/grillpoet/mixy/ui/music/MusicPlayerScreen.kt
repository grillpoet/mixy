package com.grillpoet.mixy.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.R
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.player.MusicPlayerState
import com.grillpoet.mixy.ui.common.MixyTopBar
import com.grillpoet.mixy.ui.music.components.LyricsView
import com.grillpoet.mixy.ui.music.components.MusicControlPanel
import com.grillpoet.mixy.ui.music.components.MusicCover

/**
 * 音乐播放页。
 *
 * 参数显式化：不再接收 AppContainer，只声明 [musicVm]。
 * 这样他人移植音乐模块时，依赖边界一目了然。
 */
@Composable
fun MusicPlayerScreen(
    cursor: PlaylistCursor,
    musicVm: MusicPlayerViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(cursor.items) {
        musicVm.startPlayback(cursor)
    }

    val state by musicVm.state.collectAsState()
    val position by musicVm.position.collectAsState()
    val duration by musicVm.duration.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.screenWidthDp > configuration.screenHeightDp * 1.2f

    val s = state
    val hasError = s?.error != null

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            s == null -> LoadingState(onBack = onBack)

            hasError -> ErrorState(
                title = stringResource(R.string.error_playback_failed),
                detail = s.errorDetail,
                onRetry = { musicVm.retry() },
                onBack = onBack
            )

            isLandscape -> LandscapeLayout(
                state = s,
                position = position,
                duration = duration,
                onBack = onBack,
                onToggle = musicVm::togglePlayPause,
                onNext = musicVm::next,
                onPrev = musicVm::prev,
                onSeek = musicVm::seekTo,
                onCycleMode = musicVm::cyclePlayMode
            )

            else -> PortraitLayout(
                state = s,
                position = position,
                duration = duration,
                onBack = onBack,
                onToggle = musicVm::togglePlayPause,
                onNext = musicVm::next,
                onPrev = musicVm::prev,
                onSeek = musicVm::seekTo,
                onCycleMode = musicVm::cyclePlayMode
            )
        }
    }
}

@Composable
private fun LoadingState(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        MixyTopBar(title = "", onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ErrorState(
    title: String,
    detail: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        MixyTopBar(title = "", onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                if (detail != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

@Composable
private fun PortraitLayout(
    state: MusicPlayerState,
    position: Long,
    duration: Long,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        MixyTopBar(
            title = state.current?.displayName.orEmpty(),
            subtitle = state.current?.folderName.orEmpty(),
            onBack = onBack
        )

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 12.dp)
                .fillMaxHeight(0.42f),
            contentAlignment = Alignment.Center
        ) {
            MusicCover(
                audioUri = state.current?.uri,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            LyricsView(
                lyrics = state.lyrics,
                position = position,
                onSeek = onSeek,
                modifier = Modifier.fillMaxSize()
            )
        }

        MusicControlPanel(
            state = state,
            position = position,
            duration = duration,
            onToggle = onToggle,
            onNext = onNext,
            onPrev = onPrev,
            onSeek = onSeek,
            onCycleMode = onCycleMode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )
    }
}

@Composable
private fun LandscapeLayout(
    state: MusicPlayerState,
    position: Long,
    duration: Long,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        MixyTopBar(
            title = state.current?.displayName.orEmpty(),
            subtitle = state.current?.folderName.orEmpty(),
            onBack = onBack
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    MusicCover(
                        audioUri = state.current?.uri,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                MusicControlPanel(
                    state = state,
                    position = position,
                    duration = duration,
                    onToggle = onToggle,
                    onNext = onNext,
                    onPrev = onPrev,
                    onSeek = onSeek,
                    onCycleMode = onCycleMode,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
            ) {
                LyricsView(
                    lyrics = state.lyrics,
                    position = position,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}