package com.grillpoet.mixy.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grillpoet.mixy.AppContainer
import com.grillpoet.mixy.ui.music.MusicPlayerViewModel
import com.grillpoet.mixy.ui.video.VideoPlayerViewModel
import com.grillpoet.mixy.vm.FolderViewModel

/**
 * 全应用 ViewModel 工厂。
 *
 * UI 层统一用：
 * ```
 * val vm: XxxViewModel = viewModel(
 *     factory = remember(container) { AppViewModelProvider.factory(container) }
 * )
 * ```
 *
 * 为什么要集中：
 *  1. ViewModel 构造签名变化时只改这一处
 *  2. UI 层不关心依赖从哪来，只声明「我要一个 XxxViewModel」
 *  3. 未来引入 DI 框架时，只需替换这一个类
 */
object AppViewModelProvider {

    fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            FolderViewModel(container.mediaRepository, container.settingsStore)
        }
        initializer {
            MusicPlayerViewModel(
                controller = container.musicPlayerController,
                session = container.playbackSession
            )
        }
        initializer {
            VideoPlayerViewModel(engine = container.videoEngine)
        }
    }
}
