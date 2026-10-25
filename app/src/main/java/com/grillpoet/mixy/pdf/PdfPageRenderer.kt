package com.grillpoet.mixy.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CancellationException

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

    suspend fun renderPage(index: Int): Bitmap? = renderMutex.withLock {
        withContext(Dispatchers.IO) {
            // 协程被取消时（快速滚动导致 produceState 重启）立即退出，
            // 不再继续渲染。避免大量渲染任务堆积。
            ensureActive()
            if (closed) return@withContext null
            val r = renderer ?: return@withContext null
            runCatching {
                r.openPage(index).use { page ->
                    // 渲染前的第二次取消检查：page 打开后如果协程已被取消，直接返回。
                    ensureActive()
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
            }.getOrNull()
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

            val temp = File(
                context.cacheDir,
                "pdf_${System.currentTimeMillis()}.pdf"
            )
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