package com.grillpoet.mixy.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.ui.music.components.LyricsView
import com.grillpoet.mixy.ui.music.components.MusicControlPanel
import com.grillpoet.mixy.ui.music.components.MusicCover
import com.grillpoet.mixy.ui.widget.MixyTopBar

@Composable
fun MusicPlayerScreen(
    cursor: PlaylistCursor,
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: MusicPlayerViewModel = viewModel(factory = container.viewModelFactory)

    LaunchedEffect(cursor.items) {
        vm.startPlayback(cursor)
    }

    val state by vm.state.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val s = state

        MixyTopBar(
            title = s?.current?.displayName.orEmpty(),
            subtitle = s?.current?.folderName.orEmpty(),
            onBack = onBack
        )

        if (s == null) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val err = s.error
        if (err != null) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.retry() }) {
                        Text("重试")
                    }
                }
            }
            return@Column
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 60.dp, vertical = 16.dp)
                .fillMaxHeight(0.5f),
            contentAlignment = Alignment.Center
        ) {
            MusicCover(
                audioUri = s.current?.uri,
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
                lyrics = s.lyrics,
                position = position,
                onSeek = vm::seekTo,
                modifier = Modifier.fillMaxSize()
            )
        }

        MusicControlPanel(
            state = s,
            position = position,
            duration = duration,
            onToggle = vm::togglePlayPause,
            onNext = vm::next,
            onPrev = vm::prev,
            onSeek = vm::seekTo,
            onCycleMode = vm::cyclePlayMode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )
    }
}