package com.grillpoet.mixy.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.data.model.PdfLayout
import com.grillpoet.mixy.data.model.PlayMode
import com.grillpoet.mixy.data.model.SortMode
import com.grillpoet.mixy.data.model.SortOrder
import com.grillpoet.mixy.data.model.VideoOrientation
import com.grillpoet.mixy.data.model.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class Settings(
    val theme: String = "system",
    val excludedPaths: List<String> = DEFAULT_EXCLUDED,
    val musicDefaultPlayMode: PlayMode = PlayMode.SEQUENCE,
    val videoDefaultOrientation: VideoOrientation = VideoOrientation.AUTO,
    val videoBackground: String = "PIP",
    val videoSeekSeconds: Int = 90,
    val pdfDefaultLayout: PdfLayout = PdfLayout.VERTICAL,
    val pdfTreeUri: String? = null,
    val chipOrder: List<MediaType> = listOf(
        MediaType.MUSIC, MediaType.IMAGE, MediaType.VIDEO, MediaType.PDF
    ),
    val chipHidden: Set<MediaType> = emptySet(),
    val viewMode: ViewMode = ViewMode.LIST,
    val gridColumns: Int = 3,
    val sortMode: SortMode = SortMode.NAME,
    val sortOrder: SortOrder = SortOrder.ASC
) {
    val visibleChips: List<MediaType>
        get() = chipOrder.filter { it !in chipHidden }

    companion object {
        val DEFAULT_EXCLUDED = listOf(
            "/Android/", "/tencent/MicroMsg/", "/tencent/QQfile_recv/",
            "/.nomedia/", "/.trash/", "/.cache/"
        )
    }
}

class SettingsStore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _flow = MutableStateFlow(Settings())
    val flow: StateFlow<Settings> = _flow.asStateFlow()

    /**
     * 冷启动恢复是否完成。
     *
     * 之前依赖方直接读 [flow].value 会读到默认值——DataStore 恢复是异步的。
     * 依赖方应先 `awaitLoaded()` 或 `loaded.first { it }` 再读。
     */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            runCatching {
                _flow.value = context.dataStore.data.first().toSettings()
            }
            // 恢复失败也置 true，避免依赖方永远挂起。
            _loaded.value = true
        }
    }

    /** 挂起到冷启动恢复完成。 */
    suspend fun awaitLoaded() {
        loaded.first { it }
    }

    fun update(transform: (Settings) -> Settings) {
        _flow.update(transform)
        scope.launch {
            runCatching {
                context.dataStore.edit { prefs -> _flow.value.writeTo(prefs) }
            }
        }
    }
}

private val Context.dataStore by preferencesDataStore(name = "mixy_settings")

private object Keys {
    val theme = stringPreferencesKey("theme")
    val excludedPaths = stringSetPreferencesKey("excluded_paths")
    val musicPlayMode = stringPreferencesKey("music_play_mode")
    val videoOrientation = stringPreferencesKey("video_orientation")
    val videoBackground = stringPreferencesKey("video_background")
    val videoSeekSeconds = intPreferencesKey("video_seek_seconds")
    val pdfLayout = stringPreferencesKey("pdf_layout")
    val pdfTreeUri = stringPreferencesKey("pdf_tree_uri")
    val chipOrder = stringPreferencesKey("chip_order")
    val chipHidden = stringSetPreferencesKey("chip_hidden")
    val viewMode = stringPreferencesKey("view_mode")
    val gridColumns = intPreferencesKey("grid_columns")
    val sortMode = stringPreferencesKey("sort_mode")
    val sortOrder = stringPreferencesKey("sort_order")
}

private fun Preferences.toSettings(): Settings {
    val defaults = Settings()
    return Settings(
        theme = this[Keys.theme] ?: defaults.theme,
        // 区分「key 不存在」和「key 存在但为空」：
        // 用户删光屏蔽项后应保持空列表，不能回退默认。
        excludedPaths = this[Keys.excludedPaths]?.toList() ?: defaults.excludedPaths,
        musicDefaultPlayMode = this[Keys.musicPlayMode]
            ?.let { runCatching { PlayMode.valueOf(it) }.getOrNull() }
            ?: defaults.musicDefaultPlayMode,
        videoDefaultOrientation = this[Keys.videoOrientation]
            ?.let { runCatching { VideoOrientation.valueOf(it) }.getOrNull() }
            ?: defaults.videoDefaultOrientation,
        videoBackground = this[Keys.videoBackground] ?: defaults.videoBackground,
        videoSeekSeconds = this[Keys.videoSeekSeconds] ?: defaults.videoSeekSeconds,
        pdfDefaultLayout = this[Keys.pdfLayout]
            ?.let { runCatching { PdfLayout.valueOf(it) }.getOrNull() }
            ?: defaults.pdfDefaultLayout,
        pdfTreeUri = this[Keys.pdfTreeUri],
        // chipOrder 不能为空（否则 UI 全空），解析失败或为空时兜底默认。
        chipOrder = this[Keys.chipOrder]
            ?.split(",")
            ?.mapNotNull { runCatching { MediaType.valueOf(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: defaults.chipOrder,
        chipHidden = this[Keys.chipHidden]
            ?.mapNotNull { runCatching { MediaType.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: defaults.chipHidden,
        viewMode = this[Keys.viewMode]
            ?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() }
            ?: defaults.viewMode,
        gridColumns = (this[Keys.gridColumns] ?: defaults.gridColumns).coerceIn(2, 5),
        sortMode = this[Keys.sortMode]
            ?.let { runCatching { SortMode.valueOf(it) }.getOrNull() }
            ?: defaults.sortMode,
        sortOrder = this[Keys.sortOrder]
            ?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
            ?: defaults.sortOrder
    )
}

private fun Settings.writeTo(prefs: MutablePreferences) {
    prefs[Keys.theme] = theme
    prefs[Keys.excludedPaths] = excludedPaths.toSet()
    prefs[Keys.musicPlayMode] = musicDefaultPlayMode.name
    prefs[Keys.videoOrientation] = videoDefaultOrientation.name
    prefs[Keys.videoBackground] = videoBackground
    prefs[Keys.videoSeekSeconds] = videoSeekSeconds
    prefs[Keys.pdfLayout] = pdfDefaultLayout.name

    if (pdfTreeUri != null) prefs[Keys.pdfTreeUri] = pdfTreeUri
    else prefs.remove(Keys.pdfTreeUri)

    prefs[Keys.chipOrder] = chipOrder.joinToString(",") { it.name }
    prefs[Keys.chipHidden] = chipHidden.mapTo(mutableSetOf()) { it.name }
    prefs[Keys.viewMode] = viewMode.name
    prefs[Keys.gridColumns] = gridColumns
    prefs[Keys.sortMode] = sortMode.name
    prefs[Keys.sortOrder] = sortOrder.name
}