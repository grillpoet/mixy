package com.grillpoet.mixy.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.grillpoet.mixy.ui.common.formatDuration

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
fun VideoControls(
    fileName: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    repeatMode: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onRotate: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        // 顶部栏
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }

            Text(
                text = fileName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )

            var speedMenuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { speedMenuOpen = true }) {
                    Icon(Icons.Filled.Speed, "倍速", tint = Color.White)
                }
                DropdownMenu(
                    expanded = speedMenuOpen,
                    onDismissRequest = { speedMenuOpen = false }
                ) {
                    SPEEDS.forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${s}x") },
                            onClick = {
                                onSpeedChange(s)
                                speedMenuOpen = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onCycleRepeatMode) {
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE)
                        Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "循环",
                    tint = if (repeatMode == Player.REPEAT_MODE_OFF)
                        Color.White.copy(alpha = 0.5f) else Color.White
                )
            }

            IconButton(onClick = onRotate) {
                Icon(Icons.Filled.ScreenRotation, "旋转", tint = Color.White)
            }
        }

        // 中央播放/暂停
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause
                else Icons.Filled.PlayArrow,
                contentDescription = "播放/暂停",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        // 底部进度条
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
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
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDuration(
                        if (isDragging) (dragProgress * duration).toLong() else position
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "/",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDuration(duration),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = "上一集",
                    color = if (hasPrev) Color.White else Color.Gray,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = hasPrev) { onPrev() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "下一集",
                    color = if (hasNext) Color.White else Color.Gray,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = hasNext) { onNext() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}