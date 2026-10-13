package com.grillpoet.mixy.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.grillpoet.mixy.data.model.MediaFolder
import com.grillpoet.mixy.data.model.MediaItem
import com.grillpoet.mixy.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class MediaStoreDataSource(private val context: Context) {

    suspend fun queryFolders(
        type: MediaType,
        excludedPaths: List<String> = emptyList(),
        pdfTreeUri: String? = null,
        onScanProgress: ((Int) -> Unit)? = null
    ): List<MediaFolder> = withContext(Dispatchers.IO) {
        val items = when (type) {
            MediaType.MUSIC -> queryAudio()
            MediaType.IMAGE -> queryImages()
            MediaType.VIDEO -> queryVideos()
            MediaType.PDF   -> queryPdf(pdfTreeUri, onScanProgress)
        }
        items
            .filterNot { item -> excludedPaths.any { item.folderPath.contains(it) } }
            .groupBy { it.folderPath }
            .map { (path, list) ->
                MediaFolder(
                    bucketId = path,
                    folderName = list.first().folderName,
                    folderPath = path,
                    items = list.sortedByDescending { it.dateModified },
                    type = type,
                    coverUri = list.firstOrNull()?.uri
                )
            }
            .sortedBy { it.folderName.lowercase() }
    }

    private fun queryAudio(): List<MediaItem> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Audio.Media.DATA
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        return query(uri, proj, sel, null) { c ->
            val data = c.getString(7) ?: ""
            val folderPath = data.substringBeforeLast('/', "")
            MediaItem(
                id = c.getLong(0),
                uri = ContentUris.withAppendedId(uri, c.getLong(0)),
                displayName = c.getString(1) ?: "",
                folderPath = folderPath,
                folderName = c.getString(6) ?: folderPath.substringAfterLast('/'),
                mimeType = c.getString(2) ?: "audio/*",
                size = c.getLong(3),
                dateModified = c.getLong(4) * 1000,
                duration = c.getLong(5),
                type = MediaType.MUSIC
            )
        }
    }

    private fun queryImages(): List<MediaItem> {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        return query(uri, proj, null, null) { c ->
            val data = c.getString(6) ?: ""
            val folderPath = data.substringBeforeLast('/', "")
            MediaItem(
                id = c.getLong(0),
                uri = ContentUris.withAppendedId(uri, c.getLong(0)),
                displayName = c.getString(1) ?: "",
                folderPath = folderPath,
                folderName = c.getString(5) ?: folderPath.substringAfterLast('/'),
                mimeType = c.getString(2) ?: "image/*",
                size = c.getLong(3),
                dateModified = c.getLong(4) * 1000,
                type = MediaType.IMAGE
            )
        }
    }

    private fun queryVideos(): List<MediaItem> {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        )
        return query(uri, proj, null, null) { c ->
            val data = c.getString(7) ?: ""
            val folderPath = data.substringBeforeLast('/', "")
            MediaItem(
                id = c.getLong(0),
                uri = ContentUris.withAppendedId(uri, c.getLong(0)),
                displayName = c.getString(1) ?: "",
                folderPath = folderPath,
                folderName = c.getString(6) ?: folderPath.substringAfterLast('/'),
                mimeType = c.getString(2) ?: "video/*",
                size = c.getLong(3),
                dateModified = c.getLong(4) * 1000,
                duration = c.getLong(5),
                type = MediaType.VIDEO
            )
        }
    }

    private suspend fun queryPdf(
        treeUriString: String?,
        onProgress: ((Int) -> Unit)? = null
    ): List<MediaItem> {
        if (treeUriString.isNullOrBlank()) return emptyList()
        val treeUri = treeUriString.toUri()
        return PdfFileScanner.scan(context, treeUri, onProgress)
    }

    private inline fun query(
        uri: Uri, proj: Array<String>, sel: String?, args: Array<String>?,
        mapper: (Cursor) -> MediaItem
    ): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        context.contentResolver.query(
            uri, proj, sel, args,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { c ->
            while (c.moveToNext()) result += mapper(c)
        }
        return result
    }
}