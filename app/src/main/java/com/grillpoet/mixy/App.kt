package com.grillpoet.mixy

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import com.grillpoet.mixy.cover.AudioCoverFetcher
import com.grillpoet.mixy.cover.AudioCoverKeyer
import com.grillpoet.mixy.cover.PdfCoverFetcher
import com.grillpoet.mixy.cover.PdfCoverKeyer
import com.grillpoet.mixy.cover.VideoCoverFetcher
import com.grillpoet.mixy.cover.VideoCoverKeyer
import okio.Path.Companion.toOkioPath

class App : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            // 内存缓存：25% 可用堆。列表滚动时缩略图命中率显著提升。
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this, 0.25)
                    .build()
            }
            // 磁盘缓存：250MB，放在 cacheDir 下，卸载自动清。
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(AudioCoverFetcher.Factory(this@App))
                add(AudioCoverKeyer())
                add(VideoCoverFetcher.Factory(this@App))
                add(VideoCoverKeyer())
                add(PdfCoverFetcher.Factory(this@App))
                add(PdfCoverKeyer())
            }
            .build()
    }
}