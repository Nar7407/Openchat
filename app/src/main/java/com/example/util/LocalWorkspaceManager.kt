package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.domain.model.WorkspaceFileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Play-Policy compliant Local Folder Workspace backed by Android Storage Access Framework
 * (OpenDocumentTree). Supports scanning project/notes folders, reading file contents for @file
 * injection, generating a unified diff preview, and writing AI edits back to disk.
 */
object LocalWorkspaceManager {

    private val ALLOWED_EXTENSIONS = setOf(
        "kt", "java", "ts", "tsx", "js", "jsx", "py", "cpp", "c", "h", "hpp",
        "rs", "go", "md", "txt", "json", "yaml", "yml", "xml", "csv", "html", "css", "sql", "sh"
    )

    fun persistTreePermission(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
    }

    suspend fun scanWorkspaceFiles(
        context: Context,
        treeUriString: String,
        maxFiles: Int = 180
    ): List<WorkspaceFileEntry> = withContext(Dispatchers.IO) {
        val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return@withContext emptyList()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@withContext emptyList()
        val out = mutableListOf<WorkspaceFileEntry>()
        traverseFolder(context, treeUri, rootDocId, "", 0, maxFiles, out)
        out.sortedBy { it.relativePath.lowercase() }
    }

    private fun traverseFolder(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        prefixPath: String,
        depth: Int,
        maxFiles: Int,
        out: MutableList<WorkspaceFileEntry>
    ) {
        if (depth > 3 || out.size >= maxFiles) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext() && out.size < maxFiles) {
                    val docId = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: continue
                    if (name.startsWith(".") || name == "node_modules" || name == "build" || name == ".git") continue
                    val mime = cursor.getString(mimeCol).orEmpty()
                    val size = cursor.getLong(sizeCol)
                    val relPath = if (prefixPath.isEmpty()) name else "$prefixPath/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        traverseFolder(context, treeUri, docId, relPath, depth + 1, maxFiles, out)
                    } else {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in ALLOWED_EXTENSIONS || mime.startsWith("text/")) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            out += WorkspaceFileEntry(
                                uriString = docUri.toString(),
                                name = name,
                                relativePath = relPath,
                                mimeType = mime,
                                sizeBytes = size
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun readFileContent(context: Context, uriString: String, maxChars: Int = 24_000): String =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val raw = reader.readText()
                if (raw.length > maxChars) raw.take(maxChars) + "\n\n... [truncated]" else raw
            } ?: ""
        }

    suspend fun writeFileContent(context: Context, uriString: String, newContent: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(newContent.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: error("Unable to open file for writing.")
            }
        }

    fun buildSimpleDiffPreview(original: String, updated: String, maxLines: Int = 60): String {
        val oldLines = original.lines()
        val newLines = updated.lines()
        val diff = mutableListOf<String>()
        val maxLen = maxOf(oldLines.size, newLines.size)
        for (i in 0 until maxLen) {
            val o = oldLines.getOrNull(i)
            val n = newLines.getOrNull(i)
            if (o != n) {
                if (o != null) diff += "- ${i + 1}: $o"
                if (n != null) diff += "+ ${i + 1}: $n"
            }
            if (diff.size >= maxLines) {
                diff += "… (more changes truncated)"
                break
            }
        }
        return if (diff.isEmpty()) "No textual differences detected." else diff.joinToString("\n")
    }
}
