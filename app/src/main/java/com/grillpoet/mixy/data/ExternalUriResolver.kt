package com.grillpoet.mixy.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.grillpoet.mixy.data.model.MediaItem
import com.grillpoet.mixy.data.model.MediaType
import com.grillpoet.mixy.data.model.PlaylistCursor
import com.grillpoet.mixy.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把外部 App 传入的 content:// URI 解析成单元素的 [PlaylistCursor]。
 *
 * 用于响应 ACTION_VIEW / ACTION_SEND。
 * 所有查询和时长读取都在 IO 线程执行（MediaMetadataRetriever 是阻塞 API）。
 */
object ExternalUriResolver {


    /**
     * 解析失败返回 null（MIME 不支持或 URI 无法访问）。
     * 成功时返回的 cursor 只有一个元素，currentIndex = 0。
     */
    suspend fun toCursor(context: Context, uri: Uri): PlaylistCursor? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: return@withContext null
            val type = mimeToType(mime) ?: return@withContext null

            val meta = queryMetadata(context, uri)
            val duration = if (type == MediaType.VIDEO || type == MediaType.MUSIC) {
                readDuration(context, uri)
            } else 0L

            val item = MediaItem(
                // 高位置 1 标记「外部来源」，与 MediaStore 的正数 id 区分。
                // 队列内 id 仍唯一（同一队列来自同一次外部调用，只有一项）。
                id = (uri.toString().hashCode().toLong() and 0x7FFFFFFFL) or (1L shl 62),
                uri = uri,
                displayName = meta.name,
                folderPath = "",
                folderName = context.getString(R.string.folder_fallback_name),
                mimeType = mime,
                size = meta.size,
                dateModified = meta.dateModified,
                duration = duration,
                type = type
            )

            PlaylistCursor(items = listOf(item), currentIndex = 0, sourceType = type)
        }

    private fun mimeToType(mime: String): MediaType? = when {
        mime.startsWith("image/") -> MediaType.IMAGE
        mime.startsWith("video/") -> MediaType.VIDEO
        mime.startsWith("audio/") -> MediaType.MUSIC
        mime.equals("application/pdf", ignoreCase = true) -> MediaType.PDF
        else -> null
    }

    private data class Metadata(
        val name: String,
        val size: Long,
        val dateModified: Long
    )

    /** 查询 OpenableColumns + date_modified。取不到的字段用默认值。 */
    private fun queryMetadata(context: Context, uri: Uri): Metadata {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "external"
        var size = 0L
        var dateModified = 0L

        context.contentResolver
            .query(uri, null, null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use

                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 }
                    ?.let { cursor.getString(it) }
                    ?.let { name = it }

                cursor.getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { size = cursor.getLong(it) }

                cursor.getColumnIndex("date_modified")
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { dateModified = cursor.getLong(it) }
            }

        return Metadata(name, size, dateModified)
    }

    /** 通过 MediaMetadataRetriever 读取时长（ms）。失败返回 0。 */
    private fun readDuration(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}
