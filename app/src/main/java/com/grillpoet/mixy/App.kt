package com.grillpoet.mixy

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.grillpoet.mixy.data.cover.AudioCoverFetcher
import com.grillpoet.mixy.data.cover.AudioCoverKeyer
import com.grillpoet.mixy.data.cover.PdfCoverFetcher
import com.grillpoet.mixy.data.cover.PdfCoverKeyer
import com.grillpoet.mixy.data.cover.VideoCoverFetcher
import com.grillpoet.mixy.data.cover.VideoCoverKeyer

/**
 * Application 入口。
 *
 * 通过 [SingletonImageLoader.Factory] 定制 Coil 3 的 ImageLoader：
 *  - 注册三个自定义 Fetcher（音频内嵌封面 / 视频抽帧 / PDF 首页）
 *  - 注册对应 Keyer，让内存缓存按 URI 生效
 *  - API 28+ 用 [AnimatedImageDecoder] 支持 GIF / 动画 WebP / HEIF
 *  - 旧版本退回 [GifDecoder]（仅支持 GIF）
 *
 * Coil 3 变化说明：
 *  - [ImageLoaderFactory] 已被移除，改用 [SingletonImageLoader.Factory]
 *  - [ImageDecoderDecoder] 已重命名为 [AnimatedImageDecoder]
 */
class App : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // API 28+：系统 ImageDecoder，支持 GIF / 动画 WebP / HEIF
                    add(AnimatedImageDecoder.Factory())
                } else {
                    // API 26-27：退回旧 GifDecoder，仅支持 GIF
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
