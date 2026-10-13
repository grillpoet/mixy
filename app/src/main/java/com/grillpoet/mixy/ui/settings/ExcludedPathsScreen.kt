package com.grillpoet.mixy.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.AppContainer

@Composable
fun ExcludedPathsScreen(container: AppContainer) {
    val settings by container.settingsStore.flow.collectAsState()
    val context = LocalContext.current

    var showManualDialog by remember { mutableStateOf(false) }

    // SAF 目录选择器
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val fragment = safUriToFragment(uri)
            when {
                fragment.isNullOrBlank() -> {
                    Toast.makeText(
                        context,
                        "请选择一个具体目录，不能是存储根目录",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                fragment in settings.excludedPaths -> {
                    Toast.makeText(
                        context,
                        "已在屏蔽列表中",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    container.settingsStore.update {
                        it.copy(excludedPaths = it.excludedPaths + fragment)
                    }
                    Toast.makeText(
                        context,
                        "已添加：$fragment",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "屏蔽文件夹",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )

            // 从文件选择器选
            IconButton(onClick = { treeLauncher.launch(null) }) {
                Icon(Icons.Filled.CreateNewFolder, "选择目录")
            }

            // 手动输入（备选）
            IconButton(onClick = { showManualDialog = true }) {
                Icon(Icons.Filled.Edit, "手动输入")
            }
        }

        Text(
            text = "选中的目录及其子目录内的媒体将不会显示",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (settings.excludedPaths.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "暂无屏蔽项",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "点右上角图标选择要屏蔽的目录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(settings.excludedPaths, key = { it }) { path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CreateNewFolder, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            container.settingsStore.update {
                                it.copy(excludedPaths = it.excludedPaths - path)
                            }
                        }) {
                            Icon(
                                Icons.Filled.Delete, "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        AddPathDialog(
            onDismiss = { showManualDialog = false },
            onConfirm = { path ->
                if (path.isNotBlank()) {
                    val clean = path.trim().trim('/')
                    if (clean.isNotBlank() && clean !in settings.excludedPaths) {
                        container.settingsStore.update {
                            it.copy(excludedPaths = it.excludedPaths + clean)
                        }
                    }
                }
                showManualDialog = false
            }
        )
    }
}

// ============================================================
// 手动输入对话框
// ============================================================

@Composable
private fun AddPathDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动输入路径") },
        text = {
            Column {
                Text(
                    "输入文件夹路径的一部分，如 tencent/MicroMsg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ============================================================
// SAF URI → 路径片段
// ============================================================

/**
 * 从 SAF 树 URI 解析出相对路径片段。
 *
 * content://com.android.externalstorage.documents/tree/primary%3Atencent%2FMicroMsg
 *   → getTreeDocumentId = "primary:tencent/MicroMsg"
 *   → 去掉卷标 = "tencent/MicroMsg"
 *
 * 若用户选存储根，返回 null（不允许屏蔽根，否则屏蔽全部）。
 */
private fun safUriToFragment(uri: Uri): String? {
    val docId = runCatching {
        DocumentsContract.getTreeDocumentId(uri)
    }.getOrNull() ?: return null

    // docId 形如 "primary:tencent/MicroMsg" 或 "primary:"
    val noVolume = docId.substringAfter(':', docId)
    return noVolume.trim('/').ifBlank { null }
}