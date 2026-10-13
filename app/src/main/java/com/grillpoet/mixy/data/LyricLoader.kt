package com.grillpoet.mixy.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LyricLine(val timestamp: Long, val text: String)

object LyricLoader {

    /**
     * 只读同目录同名 .lrc 文件。
     * 内嵌标签（ID3 USLT / FLAC LYRICS / M4A ©lyr）需要第三方库，不引。
     */
    suspend fun load(context: Context, uri: Uri): List<LyricLine> =
        withContext(Dispatchers.IO) {
            val text = readSidecarLrc(context, uri)
            if (text.isBlank()) emptyList() else parseLrc(text)
        }

    private fun readSidecarLrc(context: Context, uri: Uri): String {
        val path = resolveDataPath(context, uri) ?: return ""
        val audioFile = File(path)
        val lrcFile = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".lrc")
        if (!lrcFile.exists() || !lrcFile.canRead()) return ""

        val gbk = runCatching { java.nio.charset.Charset.forName("GBK") }.getOrNull()

        // 先试 UTF-8，失败再试 GBK
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