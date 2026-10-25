package com.grillpoet.mixy.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
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
import java.io.File

/**
 * PDF 首页封面包装。UI 层通过 `AsyncImage(model = PdfCover(uri))` 触发。
 *
 * 渲染策略：
 *  1. 直接 openFileDescriptor 打开（大部分 SAF uri 支持）
 *  2. 失败则把 PDF 拷贝到 cacheDir 临时文件再打开（部分 provider 不支持随机访问）
 */
@JvmInline
value class PdfCover(val uri: Uri)

class PdfCoverFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val bitmap = renderFirstPage() ?: return@withContext null
        ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    private fun renderFirstPage(): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var tempFile: File? = null
        try {
            pfd = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")
            }.getOrNull()

            renderer = pfd?.let { runCatching { PdfRenderer(it) }.getOrNull() }

            // 直接打开失败时走临时文件兜底
            if (renderer == null) {
                pfd?.let { runCatching { it.close() } }
                pfd = null

                tempFile = File(context.cacheDir, "pdf_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { input.copyTo(it) }
                } ?: return null

                pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
            }

            if (renderer.pageCount <= 0) return null

            renderer.openPage(0).use { page ->
                val scale = COVER_WIDTH.toFloat() / page.width
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = createBitmap(COVER_WIDTH, height)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
            runCatching { tempFile?.delete() }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<PdfCover> {
        override fun create(
            data: PdfCover,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = PdfCoverFetcher(context, data.uri)
    }

    companion object {
        private const val COVER_WIDTH = 400
    }
}

class PdfCoverKeyer : Keyer<PdfCover> {
    override fun key(data: PdfCover, options: Options): String =
        "pdf_cover:${data.uri}"
}
