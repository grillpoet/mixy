package com.grillpoet.mixy.data.cover

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem

/**
 * 音频内嵌封面包装。UI 层通过 `AsyncImage(model = AudioCover(uri))` 触发加载。
 *
 * 为什么要包装而不是直接用 Uri：Coil 默认的 Uri Fetcher 会把内容当图片解码，
 * 而音频文件里的封面需要先经过 MediaMetadataRetriever 抽取内嵌标签。
 * 用一个独立类型做标记，可以精确拦截，避免污染其他类型。
 */
@JvmInline
value class AudioCover(val uri: Uri)

/** 从音频文件的内嵌标签抽取封面（MP3 ID3 / FLAC PICTURE / M4A covr 等）。 */
class AudioCoverFetcher(
    private val context: Context,
    private val uri: Uri,
    private val fileSystem: FileSystem
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val bytes = readEmbeddedPicture() ?: return@withContext null
        SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(bytes) },
                fileSystem = fileSystem
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK
        )
    }

    private fun readEmbeddedPicture(): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<AudioCover> {
        override fun create(
            data: AudioCover,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = AudioCoverFetcher(context, data.uri, options.fileSystem)
    }
}

/** 让内存缓存按 URI 生效。不写 Keyer 会导致列表滚动时封面闪烁。 */
class AudioCoverKeyer : Keyer<AudioCover> {
    override fun key(data: AudioCover, options: Options): String =
        "audio_cover:${data.uri}"
}
