package com.grillpoet.mixy

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.grillpoet.mixy.data.MediaCache
import com.grillpoet.mixy.data.MediaRepository
import com.grillpoet.mixy.data.MediaStoreDataSource
import com.grillpoet.mixy.data.SettingsStore
import com.grillpoet.mixy.player.PlaybackSession
import com.grillpoet.mixy.player.MediaControllerManager
import com.grillpoet.mixy.player.MusicPlayerController
import com.grillpoet.mixy.player.VideoEngine
import com.grillpoet.mixy.ui.AppViewModelProvider

/**
 * 应用级依赖容器。
 *
 * 职责边界：只提供依赖，不做业务逻辑。
 * 业务逻辑放在各自的类里（如 [ExternalUriResolver]、[PlaybackSession]）。
 *
 * 生命周期与 Application 一致，通过 [App.container] 全局访问。
 * 未来引入 DI 框架时，这个类会被 Koin Module 或 Hilt Module 取代。
 */
class AppContainer(context: Context) {

    /** Application Context。全局共享，不要持有 Activity Context。 */
    val appContext: Context = context.applicationContext

    /** 跨界面的播放相关状态。 */
    val playbackSession = PlaybackSession()

    /** 设置存储。UI 只通过它读写 Settings。 */
    val settingsStore = SettingsStore(appContext)

    /** MediaStore 查询结果的内存缓存。 */
    val mediaCache = MediaCache()

    /** MediaStore 数据源。 */
    val mediaDataSource = MediaStoreDataSource(appContext)

    /** 媒体仓库。组装数据源 + 缓存。 */
    val mediaRepository = MediaRepository(mediaDataSource, mediaCache)

    /** Media3 控制器管理器。负责与 PlaybackService 建立连接。 */
    val controllerManager = MediaControllerManager(appContext)

    /** 单例音乐控制器。首次访问时创建。 */
    val musicPlayerController: MusicPlayerController by lazy {
        MusicPlayerController(this)
    }

    /**
     * 视频开始时停掉音乐。
     *
     * 视频模块不应直接依赖 MusicPlayerViewModel。互斥策略收口到容器，
     * 未来做「画中画时音乐继续」之类的规则也只改这里。
     */
    fun stopMusicForVideo() {
        musicPlayerController.clear()
    }

    /** 视频引擎。首次进入视频播放页时创建。 */
    val videoEngine: VideoEngine by lazy {
        VideoEngine(appContext)
    }

    /** 全应用 ViewModel 工厂。缓存，避免每个屏幕新建。 */
    val viewModelFactory: ViewModelProvider.Factory by lazy {
        AppViewModelProvider.factory(this)
    }
}
