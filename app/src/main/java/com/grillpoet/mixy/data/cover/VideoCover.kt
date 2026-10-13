package com.grillpoet.mixy.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 视频封面包装。UI 层通过 `AsyncImage(model = VideoCover(uri))` 触发。
 *
 * 加载策略：
 *  1. Android Q+ 优先用系统 [android.content.ContentResolver.loadThumbnail]（快）
 *  2. 否则 MediaMetadataRetriever 抽第一帧（兼容旧设备）
 */
@JvmInline
value class VideoCover(val uri: Uri)

class VideoCoverFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val bitmap = loadThumbnail() ?: return@withContext null
        ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    private fun loadThumbnail(): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
            }.getOrNull()?.let { return it }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0L)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<VideoCover> {
        override fun create(
            data: VideoCover,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = VideoCoverFetcher(context, data.uri)
    }
}

class VideoCoverKeyer : Keyer<VideoCover> {
    override fun key(data: VideoCover, options: Options): String =
        "video_cover:${data.uri}"
}
