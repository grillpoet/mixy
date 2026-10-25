package com.grillpoet.mixy.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.grillpoet.mixy.data.model.MediaFolder
import com.grillpoet.mixy.data.model.MediaItem
import com.grillpoet.mixy.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            MediaType.PDF -> queryPdf(pdfTreeUri, onScanProgress)
        }
        items
            .filterNot { item -> isExcluded(item.folderPath, excludedPaths) }
            .groupBy { it.folderPath }
            .map { (path, list) ->
                MediaFolder(
                    bucketId = path,
                    folderName = list.first().folderName,
                    folderPath = path,
                    items = list.sortedByDescending { it.dateModified },
                    type = type,
                )
            }
            .sortedBy { it.folderName.lowercase() }
    }

    /**
     * 路径段匹配。把排除项和 folderPath 都规范成 "/a/b/c/" 形式，
     * 再按完整段比较，避免 "Music" 误伤 "MyMusic"。
     *
     * 对绝对路径（MediaStore）和相对路径（SAF PDF）都适用。
     */
    private fun isExcluded(folderPath: String, excludedPaths: List<String>): Boolean {
        if (excludedPaths.isEmpty()) return false
        val target = "/" + folderPath.trim('/') + "/"
        return excludedPaths.any { raw ->
            val p = raw.trim('/')
            p.isNotEmpty() && target.contains("/$p/", ignoreCase = true)
        }
    }

    /**
     * 基础过滤：排除回收站项（Android 11+）和正在写入项。
     * 已删除项出现在列表里点开会失败，正在写入的缩略图加载失败。
     */
    private fun baseSelection(): String {
        val parts = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            parts += "${MediaStore.MediaColumns.IS_TRASHED} = 0"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parts += "${MediaStore.MediaColumns.IS_PENDING} = 0"
        }
        return parts.joinToString(" AND ")
    }

    /** 拼接用户已有 selection 与基础过滤。 */
    private fun combineSelection(user: String?): String? {
        val base = baseSelection()
        return when {
            user.isNullOrBlank() -> base.ifBlank { null }
            base.isBlank() -> user
            else -> "($user) AND ($base)"
        }
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
        return query(uri, proj, combineSelection(sel)) { c ->
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
        return query(uri, proj, combineSelection(null)) { c ->
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
        return query(uri, proj, combineSelection(null)) { c ->
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

    /**
     * 通用查询。只支持无 selectionArgs 的查询——当前所有调用点都没有 args，
     * 需要时再扩参数。
     */
    private inline fun query(
        uri: Uri,
        proj: Array<String>,
        sel: String?,
        mapper: (Cursor) -> MediaItem
    ): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        context.contentResolver.query(
            uri, proj, sel, null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { c ->
            while (c.moveToNext()) result += mapper(c)
        }
        return result
    }
}