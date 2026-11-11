package com.grillpoet.mixy

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import com.grillpoet.mixy.ui.AppRoot
import com.grillpoet.mixy.ui.theme.MixyTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as App).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)

        container.playbackSession.postExternalUri(extractViewUri(intent))

        setContent {
            val settings by container.settingsStore.flow.collectAsState()
            val loaded by container.settingsStore.loaded.collectAsState()

            // 修复主题闪烁：
            // DataStore 异步加载期间，先不渲染 AppRoot，只铺一层与"即将生效的主题"
            // 一致的纯色 Surface。loaded 变为 true 后再渲染完整界面。
            // 占位期间不读 settings.theme（它此时仍是默认值），而是用 isSystemInDarkTheme()
            // 作为"加载中"的临时主题——这样占位色与用户最终看到的主题尽可能接近，
            // 至少不会是"白屏突然变黑"或"黑屏突然变白"的硬闪。
            val darkTheme = when {
                !loaded -> isSystemInDarkTheme()
                settings.theme == "dark" -> true
                settings.theme == "light" -> false
                else -> isSystemInDarkTheme()
            }

            MixyTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (loaded) {
                        AppRoot(container = container)
                    }
                    // loaded == false 时只渲染 Surface 背景，不渲染任何内容。
                    // 这就是"占位"，通常只持续几十毫秒。
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        container.playbackSession.postExternalUri(extractViewUri(intent))
    }

    private fun extractViewUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java
            )
            else -> null
        }
    }
}