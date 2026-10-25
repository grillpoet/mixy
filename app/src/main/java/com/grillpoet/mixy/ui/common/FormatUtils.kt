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
private val STORAGE_EMULATED = Regex("^/storage/emulated/0/")
private val STORAGE_VOLUME = Regex("^/storage/[A-Za-z0-9_-]+/")

/**
 * 去掉存储根前缀后的显示路径。
 *
 * `/storage/emulated/0/Music/周杰伦` → `Music/周杰伦`
 * `/storage/ABCD-1234/Music/周杰伦`  → `Music/周杰伦`
 * `(root)`（PDF 根目录）→ 空串
 * 其他情况原样返回。
 */
fun displayFolderPath(path: String): String {
    if (path.isBlank() || path == "(root)") return ""
    val stripped = path
        .replaceFirst(STORAGE_EMULATED, "")
        .replaceFirst(STORAGE_VOLUME, "")
    return stripped.ifBlank { path }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}
