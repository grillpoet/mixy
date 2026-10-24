package com.grillpoet.mixy.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.data.model.PdfLayout
import com.grillpoet.mixy.data.model.PlayMode
import com.grillpoet.mixy.data.model.VideoOrientation
import com.grillpoet.mixy.nav.Screen
import com.grillpoet.mixy.ui.widget.EditableChips
import com.grillpoet.mixy.ui.widget.MixyTopBar

@Composable
fun SettingsScreen(
    container: AppContainer,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit
) {
    val settings by container.settingsStore.flow.collectAsState()
    val context = LocalContext.current

    val pdfTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            container.settingsStore.update { it.copy(pdfTreeUri = uri.toString()) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        MixyTopBar(title = "设置", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                SectionHeader("外观")
                ChoiceGroup(
                    title = "主题",
                    options = listOf(
                        "system" to "跟随系统",
                        "light" to "亮色",
                        "dark" to "暗色"
                    ),
                    current = settings.theme,
                    onSelect = { v ->
                        container.settingsStore.update { it.copy(theme = v) }
                    }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("媒体类型")
                Text(
                    text = "长按拖动排序，点 × 隐藏，点下方 chip 恢复",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
                EditableChips(
                    visible = settings.visibleChips,
                    hidden = settings.chipHidden,
                    onReorder = { newVisibleOrder ->
                        container.settingsStore.update {
                            val hiddenItems = it.chipOrder.filter { m -> m in it.chipHidden }
                            it.copy(chipOrder = newVisibleOrder + hiddenItems)
                        }
                    },
                    onHide = { type ->
                        if (settings.visibleChips.size > 1) {
                            container.settingsStore.update {
                                it.copy(chipHidden = it.chipHidden + type)
                            }
                        }
                    },
                    onRestore = { type ->
                        container.settingsStore.update {
                            it.copy(chipHidden = it.chipHidden - type)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("播放")
                ChoiceGroup(
                    title = "音乐默认播放模式",
                    options = listOf(
                        PlayMode.SEQUENCE to "顺序",
                        PlayMode.REPEAT_ONE to "单曲",
                        PlayMode.SHUFFLE to "随机"
                    ),
                    current = settings.musicDefaultPlayMode,
                    onSelect = { v ->
                        container.settingsStore.update { it.copy(musicDefaultPlayMode = v) }
                    }
                )
                Spacer(Modifier.height(8.dp))
                ChoiceGroup(
                    title = "视频默认方向",
                    options = listOf(
                        VideoOrientation.AUTO to "自动",
                        VideoOrientation.LANDSCAPE to "横屏",
                        VideoOrientation.PORTRAIT to "竖屏"
                    ),
                    current = settings.videoDefaultOrientation,
                    onSelect = { v ->
                        container.settingsStore.update { it.copy(videoDefaultOrientation = v) }
                    }
                )
                Spacer(Modifier.height(8.dp))
                ChoiceGroup(
                    title = "视频左右滑动快进",
                    options = listOf(
                        30 to "30 秒",
                        60 to "60 秒",
                        90 to "90 秒",
                        120 to "120 秒"
                    ),
                    current = settings.videoSeekSeconds,
                    onSelect = { v ->
                        container.settingsStore.update { it.copy(videoSeekSeconds = v) }
                    }
                )
                Spacer(Modifier.height(8.dp))
                ChoiceGroup(
                    title = "视频后台播放",
                    options = listOf(
                        "PIP" to "画中画",
                        "PAUSE" to "暂停"
                    ),
                    current = settings.videoBackground,
                    onSelect = { v ->
                        container.settingsStore.update { it.copy(videoBackground = v) }
                    }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("文件夹")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onNavigate(Screen.ExcludedPaths) }
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "屏蔽文件夹",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${settings.excludedPaths.size} 项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("PDF")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { pdfTreeLauncher.launch(null) }
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PDF 目录",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (settings.pdfTreeUri == null) "未选择" else "已选择",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                ChoiceGroup(
                    title = "PDF 默认布局",
                    options = listOf(
                        PdfLayout.VERTICAL to "纵向",
                        PdfLayout.HORIZONTAL to "横向"
                    ),
                    current = settings.pdfDefaultLayout,
                    onSelect = { v ->
                        container.settingsStore.update { it.copy(pdfDefaultLayout = v) }
                    }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceGroup(
    title: String,
    options: List<Pair<T, String>>,
    current: T,
    onSelect: (T) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = current == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}