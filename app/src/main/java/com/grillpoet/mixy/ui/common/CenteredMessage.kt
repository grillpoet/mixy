package com.grillpoet.mixy.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 居中占位态。统一空列表、错误、权限引导、PDF 目录引导的视觉。
 *
 * 六个占位场景之前各自手写 Column，间距/字号/按钮样式不一致。
 * 收口到这里后，任何页面要加占位态只需一行调用。
 *
 * @param title 主标题，必填。
 * @param description 说明文字。null 时不显示。
 * @param actionText 按钮文字。null 时不显示按钮。
 * @param onAction 按钮回调。actionText 非 null 时必须有值。
 */
@Composable
fun CenteredMessage(
    title: String,
    description: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            if (description != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = actionText,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}