package com.grillpoet.mixy.cover

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

@JvmInline
value class AudioCover(val uri: Uri)

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

/**
 * 音频封面 Keyer。
 *
 * Coil 的内存缓存需要 Keyer 为自定义类型生成缓存键。没有 Keyer 时，
 * 即使 Fetcher 返回了数据，Coil 也无法把解码结果存入内存缓存，
 * 列表滚动会反复走 MediaMetadataRetriever（慢且耗电）。
 *
 * 键直接用 URI：同一音频文件的封面应命中同一条缓存，
 * 不同文件生成不同键。
 */
class AudioCoverKeyer : Keyer<AudioCover> {
    override fun key(data: AudioCover, options: Options): String =
        "audio_cover:${data.uri}"
}