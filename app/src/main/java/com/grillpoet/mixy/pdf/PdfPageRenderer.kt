package com.grillpoet.mixy.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class PdfPageRenderer private constructor(
    private val tempFile: File?,
    private var pfd: ParcelFileDescriptor?,
    private var renderer: PdfRenderer?,
    private val renderWidth: Int
) : AutoCloseable {

    private val renderMutex = Mutex()
    @Volatile private var closed = false

    val pageCount: Int
        get() = if (closed) 0 else (renderer?.pageCount ?: 0)

    /**
     * 渲染一页。
     *
     * 修复：PdfRenderer 的 openPage/render 是阻塞 IO，
     * withContext(Dispatchers.IO) 在协程取消时不会中断已阻塞的线程。
     * 改用 runInterruptible(Dispatchers.IO)：协程取消时会向执行线程
     * 发送 interrupt，让阻塞的 render 有机会提前退出，避免快速滑动时
     * 旧渲染任务堆积占满 IO 线程池。
     */
    suspend fun renderPage(index: Int): Bitmap? = renderMutex.withLock {
        runInterruptible(Dispatchers.IO) {
            if (closed) return@runInterruptible null
            val r = renderer ?: return@runInterruptible null
            try {
                r.openPage(index).use { page ->
                    if (closed) return@runInterruptible null
                    val scale = renderWidth.toFloat() / page.width
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = createBitmap(renderWidth, height)
                    bmp.eraseColor(Color.WHITE)
                    page.render(
                        bmp, null, null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    bmp
                }
            } catch (e: Exception) {
                // interrupt 导致的异常不应让 UI 崩溃，视为本页渲染失败。
                if (e is CancellationException) throw e
                null
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { renderer?.close() }
        renderer = null
        runCatching { pfd?.close() }
        pfd = null
        runCatching { tempFile?.delete() }
    }

    companion object {
        suspend fun create(
            context: Context,
            uri: Uri,
            renderWidth: Int
        ): Result<PdfPageRenderer> = withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?: error("openFileDescriptor returned null")
                try {
                    val renderer = PdfRenderer(pfd)
                    renderer.pageCount
                    return@withContext Result.success(
                        PdfPageRenderer(null, pfd, renderer, renderWidth)
                    )
                } catch (e: Exception) {
                    runCatching { pfd.close() }
                    if (e is CancellationException) throw e
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }

            val temp = File.createTempFile("pdf_", ".pdf", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { input.copyTo(it) }
                } ?: error("Unable to open input stream")

                val pfd = ParcelFileDescriptor.open(
                    temp,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                try {
                    val renderer = PdfRenderer(pfd)
                    renderer.pageCount
                    Result.success(PdfPageRenderer(temp, pfd, renderer, renderWidth))
                } catch (e: Exception) {
                    runCatching { pfd.close() }
                    runCatching { temp.delete() }
                    if (e is CancellationException) throw e
                    Result.failure(e)
                }
            } catch (e: CancellationException) {
                runCatching { temp.delete() }
                throw e
            } catch (e: Exception) {
                runCatching { temp.delete() }
                Result.failure(e)
            }
        }
    }
}