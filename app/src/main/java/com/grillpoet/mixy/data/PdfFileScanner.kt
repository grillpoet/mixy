package com.grillpoet.mixy.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.grillpoet.mixy.data.model.MediaItem
import com.grillpoet.mixy.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfFileScanner {

    private const val MAX_DEPTH = 4
    private const val MAX_FILES = 20_000

    private val PROJ = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    suspend fun scan(
        context: Context,
        treeUri: Uri,
        onProgress: ((Int) -> Unit)? = null
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val out = ArrayList<MediaItem>(256)
        val rootDocId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return@withContext emptyList()

        traverse(context, treeUri, rootDocId, "", out, 0, onProgress)
        out
    }

    /**
     * 用 DocumentsContract 查询一层，一次 IPC 拿到所有子项。
     * 比 DocumentFile.listFiles() 快 10-50 倍。
     */
    private fun queryChildren(
        context: Context,
        treeUri: Uri,
        parentDocId: String
    ): List<Row> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, parentDocId
        )
        val result = ArrayList<Row>(64)
        runCatching {
            context.contentResolver.query(childrenUri, PROJ, null, null, null)
                ?.use { c ->
                    while (c.moveToNext()) {
                        result += Row(
                            docId = c.getString(0),
                            name = c.getString(1).orEmpty(),
                            mime = c.getString(2).orEmpty(),
                            size = c.getLong(3),
                            lastModified = c.getLong(4)
                        )
                    }
                }
        }
        return result
    }

    /**
     * 递归遍历目录树。
     *
     * Row 的五个字段在循环体内全部用到，直接解构声明，避免 `child.xxx` 重复。
     * Row 字段顺序固定（docId, name, mime, size, lastModified），
     * 与 componentN() 对齐。
     */
    private fun traverse(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        parentPath: String,
        out: MutableList<MediaItem>,
        depth: Int,
        onProgress: ((Int) -> Unit)?
    ) {
        if (depth > MAX_DEPTH) return
        if (out.size >= MAX_FILES) return

        val children = queryChildren(context, treeUri, parentDocId)
        for ((docId, name, mime, size, lastModified) in children) {
            if (out.size >= MAX_FILES) return

            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                val subPath = if (parentPath.isBlank()) name
                else "$parentPath/$name"
                traverse(
                    context, treeUri, docId, subPath,
                    out, depth + 1, onProgress
                )
            } else if (name.endsWith(".pdf", ignoreCase = true)) {
                val childUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, docId
                )
                // parentPath 空时表示根目录
                val folderPath = parentPath.ifBlank { "(root)" }
                out += MediaItem(
                    id = docId.hashCode().toLong(),
                    uri = childUri,
                    displayName = name,
                    folderPath = folderPath,
                    folderName = folderPath.substringAfterLast('/'),
                    mimeType = "application/pdf",
                    size = size,
                    dateModified = lastModified,
                    type = MediaType.PDF
                )
                onProgress?.invoke(out.size)
            }
        }
    }

    private data class Row(
        val docId: String,
        val name: String,
        val mime: String,
        val size: Long,
        val lastModified: Long
    )
}