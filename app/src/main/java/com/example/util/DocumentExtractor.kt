package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.domain.model.AiErrorCategory
import com.example.domain.model.AiErrorMapper
import com.example.domain.model.AiException
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ExtractedDocument(
    val title: String,
    val text: String
)

object DocumentExtractor {

    suspend fun extractFromUri(context: Context, uri: Uri): ExtractedDocument =
        withContext(Dispatchers.IO) {
            val fileName = resolveFileName(context, uri)
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
            val isPdf = mime.contains("pdf") || fileName.lowercase().endsWith(".pdf")

            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                if (isPdf) {
                    PDDocument.load(stream).use { doc ->
                        PDFTextStripper().getText(doc)
                    }
                } else {
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
            }.orEmpty().trim()

            if (text.isBlank()) {
                throw AiException(
                    AiErrorCategory.UNKNOWN,
                    "No readable text was found in $fileName."
                )
            }
            ExtractedDocument(title = fileName, text = text)
        }

    suspend fun extractFromUrl(client: OkHttpClient, rawUrl: String): ExtractedDocument =
        withContext(Dispatchers.IO) {
            val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                rawUrl.trim()
            } else {
                "https://${rawUrl.trim()}"
            }
            val request = Request.Builder()
                .url(normalized)
                .addHeader("User-Agent", "Mozilla/5.0 (Android; AllChat Reader)")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { res ->
                    if (!res.isSuccessful) {
                        throw AiErrorMapper.fromHttpStatus(res.code, "", "URL Fetcher")
                    }
                    val html = res.body?.string().orEmpty()
                    val title = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(html)?.groupValues?.getOrNull(1)?.let(::decodeHtmlEntities)?.trim()
                        ?: normalized
                    val cleaned = stripHtmlToText(html)
                    if (cleaned.isBlank()) {
                        throw AiException(
                            AiErrorCategory.UNKNOWN,
                            "Fetched $normalized, but could not extract readable article text."
                        )
                    }
                    ExtractedDocument(title = title, text = cleaned)
                }
            } catch (t: Throwable) {
                throw AiErrorMapper.fromThrowable(t, "URL Fetcher")
            }
        }

    fun splitIntoChunks(text: String, maxCharsPerChunk: Int = 3600, overlapChars: Int = 200): List<String> {
        val normalized = text.trim()
        if (normalized.length <= maxCharsPerChunk) return listOf(normalized)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < normalized.length) {
            var end = (start + maxCharsPerChunk).coerceAtMost(normalized.length)
            if (end < normalized.length) {
                val paragraphBreak = normalized.lastIndexOf("\n\n", end)
                val sentenceBreak = normalized.lastIndexOf(". ", end)
                val spaceBreak = normalized.lastIndexOf(' ', end)
                end = when {
                    paragraphBreak > start + maxCharsPerChunk / 2 -> paragraphBreak + 2
                    sentenceBreak > start + maxCharsPerChunk / 2 -> sentenceBreak + 2
                    spaceBreak > start + maxCharsPerChunk / 2 -> spaceBreak + 1
                    else -> end
                }
            }
            chunks += normalized.substring(start, end).trim()
            if (end >= normalized.length) break
            start = (end - overlapChars).coerceAtLeast(start + 1)
        }
        return chunks.filter { it.isNotBlank() }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIdx) ?: "document"
            }
        }
        return uri.lastPathSegment ?: "document"
    }

    private fun stripHtmlToText(html: String): String {
        val withoutBlocks = html
            .replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<noscript[^>]*>.*?</noscript>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""</(p|div|h1|h2|h3|h4|li|tr|article|section|br)>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), " ")
        return decodeHtmlEntities(withoutBlocks)
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n\s*\n+"""), "\n\n")
            .trim()
    }

    private fun decodeHtmlEntities(input: String): String = input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
}
