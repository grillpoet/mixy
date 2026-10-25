package com.grillpoet.mixy.cover

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
import coil3.size.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 视频封面包装。UI 层通过 `AsyncImage(model = VideoCover(uri))` 触发。
 *
 * 加载策略：
 *  1. Android Q+ 优先 [android.content.ContentResolver.loadThumbnail]（走 MediaStore 缓存，快）
 *  2. 回退 MediaMetadataRetriever。API 27+ 用 [getScaledFrameAtTime] 直接解码到目标尺寸，
 *     避免「解全尺寸再缩放」的浪费
 *  3. 请求尺寸取自 Coil 的 [Options.size]（列表卡片通常 100-300px），而非固定 512
 *  4. [ImageFetchResult.isSampled] = true：bitmap 已按目标尺寸解码，Coil 不必再采样
 */
@JvmInline
value class VideoCover(val uri: Uri)

class VideoCoverFetcher(
    private val context: Context,
    private val uri: Uri,
    private val targetWidthPx: Int,
    private val targetHeightPx: Int
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val bitmap = loadThumbnail() ?: return@withContext null
        ImageFetchResult(
            image = bitmap.asImage(),
            // 已经按目标尺寸解码，告知 Coil 跳过自身采样。
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    private fun loadThumbnail(): Bitmap? {
        // 下限 64（避免极端小请求），上限 512（防某些场景请求超大图）。
        val reqW = targetWidthPx.coerceIn(64, 512)
        val reqH = targetHeightPx.coerceIn(64, 512)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(reqW, reqH), null)
            }.getOrNull()?.let { return it }
        }

        // 回退抽帧。API 27+ 直接解码到目标尺寸；
        // OPTION_CLOSEST_SYNC 只找最近的 I 帧，解码最快。
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    reqW,
                    reqH
                )
            } else {
                retriever.getFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            }
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
        ): Fetcher {
            // options.size 来自 Coil 对目标容器的测量。
            // 列表卡片 / 网格卡片尺寸不同，按各自目标解码。
            val width = options.size.width.pxOrFallback(512)
            val height = options.size.height.pxOrFallback(512)
            return VideoCoverFetcher(context, data.uri, width, height)
        }
    }
}

class VideoCoverKeyer : Keyer<VideoCover> {
    override fun key(data: VideoCover, options: Options): String =
        "video_cover:${data.uri}"
}

private fun Dimension.pxOrFallback(default: Int): Int = when (this) {
    is Dimension.Pixels -> px
    else -> default
}