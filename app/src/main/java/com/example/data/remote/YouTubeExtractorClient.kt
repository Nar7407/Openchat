package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class YouTubeExtractedVideo(
    val videoId: String,
    val canonicalUrl: String,
    val title: String,
    val author: String,
    val transcriptText: String,
    val hasTimestampedCaptions: Boolean
)

class YouTubeExtractorClient(
    private val client: OkHttpClient,
    private val webSearchClient: WebSearchClient
) {
    fun extractVideoId(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        val patterns = listOf(
            Regex("""(?:youtube\.com/watch\?.*?v=|youtu\.be/|youtube\.com/shorts/|youtube\.com/live/|youtube\.com/embed/)([A-Za-z0-9_-]{11})"""),
            Regex("""^([A-Za-z0-9_-]{11})$""")
        )
        for (p in patterns) {
            val m = p.find(trimmed)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    suspend fun fetchVideoAndTranscript(rawUrl: String): YouTubeExtractedVideo = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(rawUrl)
            ?: throw IllegalArgumentException("Could not parse a valid YouTube video URL.")
        val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"

        var title = "YouTube Video ($videoId)"
        var author = "YouTube Creator"

        // 1. Fetch official oEmbed metadata (title + channel author)
        runCatching {
            val encoded = URLEncoder.encode(canonicalUrl, "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val obj = JSONObject(resp.body?.string().orEmpty())
                    title = obj.optString("title").ifBlank { title }
                    author = obj.optString("author_name").ifBlank { author }
                }
            }
        }

        // 2. Fetch YouTube watch page & extract timedtext captionTracks
        var transcript = ""
        var hasCaptions = false
        runCatching {
            val watchReq = Request.Builder()
                .url(canonicalUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .get()
                .build()
            val html = client.newCall(watchReq).execute().use { it.body?.string().orEmpty() }
            val captionUrl = findCaptionTrackBaseUrl(html)
            if (!captionUrl.isNullOrBlank()) {
                val json3Url = if (captionUrl.contains("fmt=")) captionUrl else "$captionUrl&fmt=json3"
                val capReq = Request.Builder()
                    .url(json3Url)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()
                val capBody = client.newCall(capReq).execute().use { it.body?.string().orEmpty() }
                val parsed = parseJson3Captions(capBody).ifBlank { parseXmlCaptions(capBody) }
                if (parsed.length > 80) {
                    transcript = parsed
                    hasCaptions = true
                }
            }
        }

        // 3. Fallback via Jina Reader scrape if YouTube captions are disabled/restricted
        if (!hasCaptions) {
            runCatching {
                val scraped = webSearchClient.fetchUrlAsMarkdown(canonicalUrl, maxChars = 12_000)
                if (scraped.markdown.isNotBlank()) {
                    transcript = scraped.markdown.take(12_000)
                }
            }
        }

        YouTubeExtractedVideo(
            videoId = videoId,
            canonicalUrl = canonicalUrl,
            title = title,
            author = author,
            transcriptText = transcript,
            hasTimestampedCaptions = hasCaptions
        )
    }

    private fun findCaptionTrackBaseUrl(html: String): String? {
        val marker = "\"captionTracks\":"
        val idx = html.indexOf(marker)
        if (idx < 0) return null
        val start = html.indexOf('[', idx)
        if (start < 0) return null
        var depth = 0
        var end = -1
        for (i in start until html.length) {
            when (html[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        if (end <= start) return null
        val arrStr = html.substring(start, end + 1)
        val arr = runCatching { JSONArray(arrStr) }.getOrNull() ?: return null
        var fallbackUrl: String? = null
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val baseUrl = obj.optString("baseUrl").replace("\\u0026", "&")
            if (baseUrl.isBlank()) continue
            val lang = obj.optString("languageCode")
            if (lang.startsWith("en", ignoreCase = true)) return baseUrl
            if (fallbackUrl == null) fallbackUrl = baseUrl
        }
        return fallbackUrl
    }

    private fun parseJson3Captions(body: String): String {
        if (!body.trimStart().startsWith("{")) return ""
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return ""
        val events = root.optJSONArray("events") ?: return ""
        val sb = StringBuilder()
        var lastBucketSec = -15
        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            val segs = ev.optJSONArray("segs") ?: continue
            val startSec = (ev.optLong("tStartMs", 0L) / 1000L).toInt()
            val text = buildString {
                for (j in 0 until segs.length()) {
                    append(segs.optJSONObject(j)?.optString("utf8").orEmpty())
                }
            }.replace("\n", " ").trim()
            if (text.isBlank()) continue
            if (startSec - lastBucketSec >= 15) {
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append("[").append(formatTimestamp(startSec)).append("] ")
                lastBucketSec = startSec
            } else {
                sb.append(" ")
            }
            sb.append(text)
            if (sb.length > 26_000) break
        }
        return sb.toString().trim()
    }

    private fun parseXmlCaptions(body: String): String {
        val regex = Regex("""<text start="([0-9.]+)"[^>]*>([\s\S]*?)</text>""")
        val sb = StringBuilder()
        var lastSec = -15
        for (m in regex.findAll(body)) {
            val sec = m.groupValues[1].toDoubleOrNull()?.toInt() ?: 0
            val text = m.groupValues[2]
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("\n", " ")
                .trim()
            if (text.isBlank()) continue
            if (sec - lastSec >= 15) {
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append("[").append(formatTimestamp(sec)).append("] ")
                lastSec = sec
            } else {
                sb.append(" ")
            }
            sb.append(text)
            if (sb.length > 26_000) break
        }
        return sb.toString().trim()
    }

    private fun formatTimestamp(totalSec: Int): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }
}
