package com.grillpoet.mixy.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class MediaFolder(
    val bucketId: String,
    val folderName: String,
    val folderPath: String,
    val items: List<MediaItem>,
    val type: MediaType
)