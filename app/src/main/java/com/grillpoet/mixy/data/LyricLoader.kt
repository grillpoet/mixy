package com.grillpoet.mixy.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LyricLine(val timestamp: Long, val text: String)

object LyricLoader {

    /**
     * 内存缓存。同一首歌来回切换时命中，避免重复 IO + 解析。
     * 20 条足够覆盖普通播放列表的滚动窗口。
     * 空结果也缓存——没有 .lrc 的歌反复切也不必反复查文件系统。
     *
     * 不做主动清理：LruCache 自然淘汰；用户删除 .lrc 后进程存活期间仍显示旧歌词，
     * 重启 App 即恢复。引入「媒体变化监听 → 清缓存」的复杂度不值得。
     */
    private val cache = LruCache<String, List<LyricLine>>(20)

    suspend fun load(context: Context, uri: Uri): List<LyricLine> {
        val key = uri.toString()
        cache.get(key)?.let { return it }

        val result = withContext(Dispatchers.IO) {
            val text = readSidecarLrc(context, uri)
            if (text.isBlank()) emptyList() else parseLrc(text)
        }
        cache.put(key, result)
        return result
    }

    private fun readSidecarLrc(context: Context, uri: Uri): String {
        val path = resolveDataPath(context, uri) ?: return ""
        val audioFile = File(path)
        val lrcFile = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".lrc")
        if (!lrcFile.exists() || !lrcFile.canRead()) return ""

        val gbk = runCatching { java.nio.charset.Charset.forName("GBK") }.getOrNull()

        val utf8Text = runCatching { lrcFile.readText(Charsets.UTF_8) }.getOrDefault("")
        if (utf8Text.isNotBlank() && !utf8Text.contains('\uFFFD')) return utf8Text

        if (gbk != null) {
            val gbkText = runCatching { lrcFile.readText(gbk) }.getOrDefault("")
            if (gbkText.isNotBlank()) return gbkText
        }

        return utf8Text
    }

    private fun resolveDataPath(context: Context, uri: Uri): String? {
        val proj = arrayOf(MediaStore.MediaColumns.DATA)
        return runCatching {
            context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private val LRC_TIME = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parseLrc(raw: String): List<LyricLine> {
        if (raw.isBlank()) return emptyList()
        val out = mutableListOf<LyricLine>()
        for (line in raw.lineSequence()) {
            val m = LRC_TIME.find(line) ?: continue
            val min = m.groupValues[1].toIntOrNull() ?: continue
            val sec = m.groupValues[2].toIntOrNull() ?: continue
            val ms = parseMs(m.groupValues.getOrNull(3).orEmpty())
            val text = line.replace(LRC_TIME, "").trim()
            if (text.isEmpty()) continue
            out += LyricLine((min * 60L + sec) * 1000L + ms, text)
        }
        return out.sortedBy { it.timestamp }
    }

    private fun parseMs(s: String): Long = when (s.length) {
        0 -> 0L
        1 -> s.toLong() * 100
        2 -> s.toLong() * 10
        else -> s.take(3).toLong()
    }
}