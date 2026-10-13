package com.grillpoet.mixy.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.nav.Screen
import com.grillpoet.mixy.ui.widget.EditableChips

@Composable
fun SettingsScreen(
    container: AppContainer,
    onNavigate: (Screen) -> Unit
) {
    val settings by container.settingsStore.flow.collectAsState()
    val context = LocalContext.current

    // ==================== 操作 lambda ====================

    val moveUp: (MediaType) -> Unit = { type ->
        container.settingsStore.update { s ->
            val idx = s.chipOrder.indexOf(type)
            if (idx <= 0) s else {
                val list = s.chipOrder.toMutableList()
                val tmp = list[idx]
                list[idx] = list[idx - 1]
                list[idx - 1] = tmp
                s.copy(chipOrder = list)
            }
        }
    }

    val moveDown: (MediaType) -> Unit = { type ->
        container.settingsStore.update { s ->
            val idx = s.chipOrder.indexOf(type)
            if (idx < 0 || idx >= s.chipOrder.lastIndex) s else {
                val list = s.chipOrder.toMutableList()
                val tmp = list[idx]
                list[idx] = list[idx + 1]
                list[idx + 1] = tmp
                s.copy(chipOrder = list)
            }
        }
    }

    val setVisible: (MediaType, Boolean) -> Unit = { type, visible ->
        container.settingsStore.update { s ->
            val cur = s.visibleChips
            if (!visible && cur.size <= 1 && type in cur) {
                s
            } else {
                val hidden =
                    if (visible) s.chipHidden - type
                    else s.chipHidden + type
                s.copy(chipHidden = hidden)
            }
        }
    }

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

    // ==================== UI ====================

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ==================== 媒体类型 ====================
            item {
                SectionHeader("媒体类型")
                SectionHint("长按拖动排序，点 × 隐藏，点下方 chip 恢复")
                EditableChips(
                    visible = settings.visibleChips,
                    hidden = settings.chipHidden,
                    // 设置页只配置顺序，不涉及"当前选中"，不高亮任何 chip
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
                Spacer(Modifier.height(8.dp))
            }

            // ==================== 文件夹 ====================
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("文件夹")
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onNavigate(Screen.ExcludedPaths) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
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

            // ==================== PDF ====================
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader("PDF")
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { pdfTreeLauncher.launch(null) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
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
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ==================== 小组件 ====================

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

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}