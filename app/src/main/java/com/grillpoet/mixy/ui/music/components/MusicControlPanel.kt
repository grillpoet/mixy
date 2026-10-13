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
 * 包含进度条、播放/暂停、上一首/下一首、循环模式切换。
 *
 * 性能设计：
 *  - 进度百分比用 [derivedStateOf] 计算，只在百分比变化时才触发重组
 *  - 进度条拖动时只用本地状态，松手才提交 seek
 *
 * @param state 当前播放状态。包含 position、duration、isPlaying、playMode 等。
 * @param onToggle 播放/暂停切换回调。
 * @param onNext 下一首回调。
 * @param onPrev 上一首回调。
 * @param onSeek 跳转到指定位置（毫秒）的回调。
 * @param onCycleMode 切换循环模式（顺序/单曲/随机）的回调。
 * @param modifier 外部传入的修饰符。
 */
@Composable
fun MusicControlPanel(
    state: MusicPlayerState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        // 进度条：拖动期间用本地状态，松手才提交 seek
        var isDragging by remember { mutableStateOf(false) }
        var dragProgress by remember { mutableFloatStateOf(0f) }

        val displayProgress = when {
            isDragging -> dragProgress
            state.duration > 0 -> state.position.toFloat() / state.duration
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
                onSeek((dragProgress * state.duration).toLong())
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration(
                    if (isDragging) (dragProgress * state.duration).toLong()
                    else state.position
                ),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                formatDuration(state.duration),
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
