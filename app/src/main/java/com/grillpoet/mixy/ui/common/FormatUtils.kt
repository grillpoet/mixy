package com.grillpoet.mixy.ui.common

/**
 * 把毫秒格式化为可读时长。
 *
 * 规则：
 *  - 不足 1 小时："mm:ss"（如 "03:45"）
 *  - 超过 1 小时："h:mm:ss"（如 "1:23:45"）
 *
 * @param ms 毫秒。<= 0 时返回 "00:00"。
 * @return 格式化后的时长字符串。
 */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
