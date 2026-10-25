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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.R
import com.grillpoet.mixy.data.model.PdfLayout
import com.grillpoet.mixy.data.model.PlayMode
import com.grillpoet.mixy.data.model.VideoOrientation
import com.grillpoet.mixy.ui.common.EditableChips
import com.grillpoet.mixy.ui.common.MixyTopBar
import com.grillpoet.mixy.ui.common.labelRes
import com.grillpoet.mixy.ui.nav.Screen

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
        MixyTopBar(
            title = stringResource(R.string.settings_title),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                SectionHeader(stringResource(R.string.settings_section_appearance))
                ChoiceGroup(
                    title = stringResource(R.string.settings_theme),
                    options = listOf(
                        "system" to stringResource(R.string.settings_theme_system),
                        "light" to stringResource(R.string.settings_theme_light),
                        "dark" to stringResource(R.string.settings_theme_dark)
                    ),
                    current = settings.theme,
                    onSelect = { v ->
                        container.settingsStore.update { it.copy(theme = v) }
                    }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(stringResource(R.string.settings_section_display))
                DisplaySettingsContent(
                    view = settings.viewMode,
                    gridColumns = settings.gridColumns,
                    sortMode = settings.sortMode,
                    sortOrder = settings.sortOrder,
                    onView = { v ->
                        container.settingsStore.update { it.copy(viewMode = v) }
                    },
                    onGridColumns = { n ->
                        container.settingsStore.update { it.copy(gridColumns = n) }
                    },
                    onSortMode = { m ->
                        container.settingsStore.update { it.copy(sortMode = m) }
                    },
                    onSortOrder = { o ->
                        container.settingsStore.update { it.copy(sortOrder = o) }
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(stringResource(R.string.settings_section_media_types))
                Text(
                    text = stringResource(R.string.settings_media_types_hint),
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
                SectionHeader(stringResource(R.string.settings_section_playback))
                ChoiceGroup(
                    title = stringResource(R.string.settings_music_default_mode),
                    options = PlayMode.entries.map {
                        it to stringResource(it.labelRes)
                    },
                    current = settings.musicDefaultPlayMode,
                    onSelect = { v ->
                        container.settingsStore.update { it.copy(musicDefaultPlayMode = v) }
                    }
                )
                Spacer(Modifier.height(8.dp))
                ChoiceGroup(
                    title = stringResource(R.string.settings_video_default_orientation),
                    options = VideoOrientation.entries.map {
                        it to stringResource(it.labelRes)
                    },
                    current = settings.videoDefaultOrientation,
                    onSelect = { v ->
                        container.settingsStore.update { it.copy(videoDefaultOrientation = v) }
                    }
                )
                Spacer(Modifier.height(8.dp))
                ChoiceGroup(
                    title = stringResource(R.string.settings_video_seek_sensitivity),
                    options = listOf(30, 60, 90, 120).map {
                        it to stringResource(R.string.settings_video_seek_seconds, it)
                    },
                    current = settings.videoSeekSeconds,
                    onSelect = { v ->
                        container.settingsStore.update { it.copy(videoSeekSeconds = v) }
                    }
                )
                Spacer(Modifier.height(8.dp))
                ChoiceGroup(
                    title = stringResource(R.string.settings_video_background),
                    options = listOf(
                        "PIP" to stringResource(R.string.settings_video_background_pip),
                        "PAUSE" to stringResource(R.string.settings_video_background_pause)
                    ),
                    current = settings.videoBackground,
                    onSelect = { v ->
                        container.settingsStore.update { it.copy(videoBackground = v) }
                    }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(stringResource(R.string.settings_section_folders))
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
                        text = stringResource(R.string.settings_excluded_folders),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_excluded_count,
                            settings.excludedPaths.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(stringResource(R.string.settings_section_pdf))
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
                        text = stringResource(R.string.settings_pdf_folder),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(
                            if (settings.pdfTreeUri == null)
                                R.string.settings_pdf_folder_none
                            else R.string.settings_pdf_folder_selected
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                ChoiceGroup(
                    title = stringResource(R.string.settings_pdf_default_layout),
                    options = PdfLayout.entries.map {
                        it to stringResource(it.labelRes)
                    },
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