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
import kotlinx.coroutines.launch

/**
 * 应用的 Settings 模型。
 *
 * 通过 [SettingsStore.update] 变换，不要在外部直接构造新实例。
 *
 * 关于 chips：可见 chips = [chipOrder] - [chipHidden]。
 * 拖动排序时改 [chipOrder]，隐藏/恢复时改 [chipHidden]，两者正交。
 */
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
    // ---- 显示（全局统一，两个浏览界面共用）----
    val viewMode: ViewMode = ViewMode.LIST,
    val gridColumns: Int = 3,
    val sortMode: SortMode = SortMode.NAME,
    val sortOrder: SortOrder = SortOrder.ASC
) {
    /** 当前可见的 chips，顺序沿用 [chipOrder]。 */
    val visibleChips: List<MediaType>
        get() = chipOrder.filter { it !in chipHidden }

    companion object {
        val DEFAULT_EXCLUDED = listOf(
            "/Android/", "/tencent/MicroMsg/", "/tencent/QQfile_recv/",
            "/.nomedia/", "/.trash/", "/.cache/"
        )
    }
}

/**
 * 设置存储。
 *
 * 架构：内存 [MutableStateFlow] 作为唯一真源（供 UI 同步读取 .value），
 * DataStore 只负责持久化与冷启动恢复。
 *
 * 为什么这样设计：
 *  1. UI 需要同步读取（比如 AppContainer 里读 pdfTreeUri），DataStore 只有异步 Flow
 *  2. 用内存 Flow 做真源，update() 是同步的，写盘是异步的，UI 不会卡顿
 *  3. 启动时异步从 DataStore 恢复一次，之后内存为准
 *
 * 生命周期与 Application 一致，不手动 cancel scope。
 */
class SettingsStore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _flow = MutableStateFlow(Settings())

    /** 当前设置。UI 用 collectAsState() 订阅。 */
    val flow: StateFlow<Settings> = _flow.asStateFlow()

    init {
        // 冷启动恢复。失败时退回默认值，不阻塞 UI。
        scope.launch {
            runCatching {
                _flow.value = context.dataStore.data.first().toSettings()
            }
        }
    }

    /**
     * 原子地变换设置。
     *
     * 内存立即更新；DataStore 异步写盘，写入失败不回滚内存（避免 UI 抖动）。
     * 多线程调用时以「读取 - 变换 - 写入」的最终写入者为准（last-write-wins）。
     */
    fun update(transform: (Settings) -> Settings) {
        val new = _flow.value.let(transform)
        _flow.value = new
        scope.launch {
            runCatching {
                context.dataStore.edit { prefs -> new.writeTo(prefs) }
            }
        }
    }
}

// ==========================================================================
// DataStore 定义与 Preferences <-> Settings 映射
// ==========================================================================

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

/** 从 DataStore 快照还原 [Settings]。任何字段缺失都走默认值。 */
private fun Preferences.toSettings(): Settings {
    val defaults = Settings()
    return Settings(
        theme = this[Keys.theme] ?: defaults.theme,
        excludedPaths = this[Keys.excludedPaths]
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: defaults.excludedPaths,
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
        // chipOrder 用逗号分隔字符串保序；chipHidden 用 set，无序即可
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

/** 把 [Settings] 全量写入 DataStore。空值用 remove 显式删除。 */
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
