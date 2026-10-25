package com.grillpoet.mixy.ui.music.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.grillpoet.mixy.cover.AudioCover

/**
 * 音乐封面。
 *
 * 从音频文件的内嵌标签读取封面，加载失败时显示 ♪ 占位符。
 *
 * @param audioUri 音频文件 URI。null 时直接显示占位符，不尝试加载。
 * @param modifier 外部传入的修饰符。应指定尺寸。
 */
@Composable
fun MusicCover(
    audioUri: Uri?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = audioUri?.let { AudioCover(it) },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { CoverPlaceholder() },
            error = { CoverPlaceholder() }
        )
    }
}

@Composable
private fun CoverPlaceholder() {
    Text(
        text = "♪",
        fontSize = 96.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
