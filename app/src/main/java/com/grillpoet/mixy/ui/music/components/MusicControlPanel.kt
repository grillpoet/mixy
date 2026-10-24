package com.grillpoet.mixy.ui.music.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.data.model.PlayMode
import com.grillpoet.mixy.player.MusicPlayerState
import com.grillpoet.mixy.ui.common.formatDuration

/**
 * 音乐播放控制面板。
 *
 * position / duration 单独通过参数传入（不再从 state 里读），
 * 因为它们在 MusicPlayerController 里是独立 StateFlow。
 */
@Composable
fun MusicControlPanel(
    state: MusicPlayerState,
    position: Long,
    duration: Long,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        var isDragging by remember { mutableStateOf(false) }
        var dragProgress by remember { mutableFloatStateOf(0f) }

        val displayProgress = when {
            isDragging -> dragProgress
            duration > 0 -> position.toFloat() / duration
            else -> 0f
        }

        Slider(
            value = displayProgress.coerceIn(0f, 1f),
            onValueChange = {
                isDragging = true
                dragProgress = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek((dragProgress * duration).toLong())
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration(
                    if (isDragging) (dragProgress * duration).toLong()
                    else position
                ),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                formatDuration(duration),
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCycleMode) {
                Icon(
                    imageVector = when (state.playMode) {
                        PlayMode.SEQUENCE -> Icons.Filled.Repeat
                        PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                        PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                    },
                    contentDescription = "循环模式"
                )
            }

            IconButton(onClick = onPrev, enabled = state.cursor.hasPrev) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
            }

            IconButton(onClick = onToggle, modifier = Modifier.size(64.dp)) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause
                    else Icons.Filled.PlayArrow,
                    contentDescription = "播放/暂停",
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(onClick = onNext, enabled = state.cursor.hasNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
            }

            Spacer(Modifier.size(48.dp))
        }
    }
}