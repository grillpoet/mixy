package com.grillpoet.mixy.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.grillpoet.mixy.R
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.data.model.PdfLayout
import com.grillpoet.mixy.data.model.PlayMode
import com.grillpoet.mixy.data.model.SortMode
import com.grillpoet.mixy.data.model.SortOrder
import com.grillpoet.mixy.data.model.VideoOrientation
import com.grillpoet.mixy.data.model.ViewMode

/**
 * 枚举 → 文案资源映射。
 *
 * data/model 保持无 Android 依赖，所有翻译集中在 UI 层。
 * 用法：`type.label()`（@Composable 内），或 `type.labelRes` 交给 stringResource。
 */

@get:StringRes
val MediaType.labelRes: Int
    get() = when (this) {
        MediaType.MUSIC -> R.string.media_type_music
        MediaType.IMAGE -> R.string.media_type_image
        MediaType.VIDEO -> R.string.media_type_video
        MediaType.PDF -> R.string.media_type_pdf
    }

@Composable
fun MediaType.label(): String = stringResource(labelRes)

@get:StringRes
val PlayMode.labelRes: Int
    get() = when (this) {
        PlayMode.SEQUENCE -> R.string.settings_music_mode_sequence
        PlayMode.REPEAT_ONE -> R.string.settings_music_mode_repeat_one
        PlayMode.SHUFFLE -> R.string.settings_music_mode_shuffle
    }

@Composable
fun PlayMode.label(): String = stringResource(labelRes)

@get:StringRes
val VideoOrientation.labelRes: Int
    get() = when (this) {
        VideoOrientation.AUTO -> R.string.settings_video_orientation_auto
        VideoOrientation.LANDSCAPE -> R.string.settings_video_orientation_landscape
        VideoOrientation.PORTRAIT -> R.string.settings_video_orientation_portrait
    }

@Composable
fun VideoOrientation.label(): String = stringResource(labelRes)

@get:StringRes
val PdfLayout.labelRes: Int
    get() = when (this) {
        PdfLayout.VERTICAL -> R.string.settings_pdf_layout_vertical
        PdfLayout.HORIZONTAL -> R.string.settings_pdf_layout_horizontal
    }

@Composable
fun PdfLayout.label(): String = stringResource(labelRes)

@get:StringRes
val ViewMode.labelRes: Int
    get() = when (this) {
        ViewMode.LIST -> R.string.settings_view_list
        ViewMode.GRID -> R.string.settings_view_grid
    }

@Composable
fun ViewMode.label(): String = stringResource(labelRes)

@get:StringRes
val SortMode.labelRes: Int
    get() = when (this) {
        SortMode.NAME -> R.string.settings_sort_name
        SortMode.MODIFIED -> R.string.settings_sort_modified
        SortMode.SIZE -> R.string.settings_sort_size
        SortMode.DURATION -> R.string.settings_sort_duration
    }

@Composable
fun SortMode.label(): String = stringResource(labelRes)

@get:StringRes
val SortOrder.labelRes: Int
    get() = when (this) {
        SortOrder.ASC -> R.string.settings_sort_asc
        SortOrder.DESC -> R.string.settings_sort_desc
    }

@Composable
fun SortOrder.label(): String = stringResource(labelRes)