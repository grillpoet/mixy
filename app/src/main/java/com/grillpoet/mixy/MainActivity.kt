package com.grillpoet.mixy

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.grillpoet.mixy.ui.AppRoot
import com.grillpoet.mixy.ui.theme.MixyTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as App).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // 状态栏/导航栏透明，颜色由主题适配
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

        // 冷启动：外部 App 通过 ACTION_VIEW / ACTION_SEND 调用
        container.playbackSession.postExternalUri(extractViewUri(intent))

        setContent {
            val settings by container.settingsStore.flow.collectAsState()
            val darkTheme = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            MixyTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(container = container)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 已启动：再次被外部调用
        container.playbackSession.postExternalUri(extractViewUri(intent))
    }

    /** 从外部 Intent 中取出要查看的媒体 URI。 */
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
