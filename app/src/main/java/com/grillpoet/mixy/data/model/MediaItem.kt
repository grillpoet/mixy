package com.grillpoet.mixy.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val folderPath: String,
    val folderName: String,
    val mimeType: String,
    val size: Long,
    val dateModified: Long,
    val duration: Long = 0L,
    val type: MediaType
)