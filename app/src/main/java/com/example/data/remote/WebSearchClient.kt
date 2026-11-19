package com.example.data.remote

import com.example.domain.model.SearchCitation
import com.example.domain.model.WebSearchProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ScrapedUrlResult(
    val url: String,
    val title: String,
    val markdown: String
)

/**
 * Client-side Web Search (Tavily / Brave / Exa) and URL-to-Markdown scraper (r.jina.ai + HTML fallback).
 */
class WebSearchClient(private val client: OkHttpClient) {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val atUrlRegex = Regex("""@(https?://[^\s)\]>"]+)""", RegexOption.IGNORE_CASE)

    fun extractAtUrls(prompt: String): List<String> {
        return atUrlRegex.findAll(prompt)
            .map { it.groupValues[1].trimEnd('.', ',', ';', '!', '?') }
            .distinct()
            .toList()
    }

    suspend fun searchWeb(
        query: String,
        provider: WebSearchProvider,
        apiKey: String,
        maxResults: Int = 5
    ): List<SearchCitation> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().take(400)
        if (cleanQuery.isEmpty() || apiKey.isBlank()) return@withContext emptyList()

        when (provider) {
            WebSearchProvider.TAVILY -> searchTavily(cleanQuery, apiKey.trim(), maxResults)
            WebSearchProvider.BRAVE -> searchBrave(cleanQuery, apiKey.trim(), maxResults)
            WebSearchProvider.EXA -> searchExa(cleanQuery, apiKey.trim(), maxResults)
            WebSearchProvider.GEMINI_NATIVE -> emptyList()
        }
    }

    private fun searchTavily(query: String, apiKey: String, maxResults: Int): List<SearchCitation> {
        val payload = JSONObject()
            .put("api_key", apiKey)
            .put("query", query)
            .put("max_results", maxResults)
            .put("search_depth", "basic")
            .put("include_answer", false)
            .toString()

        val req = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(payload.toRequestBody(jsonMedia))
            .build()

        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalStateException("Tavily search error (${res.code}): ${body.take(180)}")
            }
            val arr = JSONObject(body).optJSONArray("results") ?: JSONArray()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val url = o.optString("url").trim()
                    if (url.isEmpty()) continue
                    add(
                        SearchCitation(
                            index = size + 1,
                            title = o.optString("title").ifBlank { url },
                            url = url,
                            snippet = o.optString("content").trim().take(500)
                        )
                    )
                }
            }
        }
    }

    private fun searchBrave(query: String, apiKey: String, maxResults: Int): List<SearchCitation> {
        val httpUrl = "https://api.search.brave.com/res/v1/web/search".toHttpUrlOrNull()!!
            .newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("count", maxResults.toString())
            .build()

        val req = Request.Builder()
            .url(httpUrl)
            .addHeader("Accept", "application/json")
            .addHeader("X-Subscription-Token", apiKey)
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalStateException("Brave Search error (${res.code}): ${body.take(180)}")
            }
            val arr = JSONObject(body)
                .optJSONObject("web")
                ?.optJSONArray("results") ?: JSONArray()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val url = o.optString("url").trim()
                    if (url.isEmpty()) continue
                    add(
                        SearchCitation(
                            index = size + 1,
                            title = o.optString("title").ifBlank { url },
                            url = url,
                            snippet = o.optString("description").trim().take(500)
                        )
                    )
                }
            }
        }
    }

    private fun searchExa(query: String, apiKey: String, maxResults: Int): List<SearchCitation> {
        val payload = JSONObject()
            .put("query", query)
            .put("numResults", maxResults)
            .put("contents", JSONObject().put("text", JSONObject().put("maxCharacters", 600)))
            .toString()

        val req = Request.Builder()
            .url("https://api.exa.ai/search")
            .addHeader("x-api-key", apiKey)
            .post(payload.toRequestBody(jsonMedia))
            .build()

        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalStateException("Exa Search error (${res.code}): ${body.take(180)}")
            }
            val arr = JSONObject(body).optJSONArray("results") ?: JSONArray()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val url = o.optString("url").trim()
                    if (url.isEmpty()) continue
                    val snippet = o.optString("text").ifBlank { o.optString("summary") }.trim().take(500)
                    add(
                        SearchCitation(
                            index = size + 1,
                            title = o.optString("title").ifBlank { url },
                            url = url,
                            snippet = snippet
                        )
                    )
                }
            }
        }
    }

    /**
     * Fetches clean webpage Markdown using Jina Reader (`https://r.jina.ai/<url>`),
     * falling back to direct HTTP fetch + HTML text stripping if Jina Reader is unavailable.
     */
    suspend fun fetchUrlAsMarkdown(url: String, maxChars: Int = 8000): ScrapedUrlResult =
        withContext(Dispatchers.IO) {
            val cleanUrl = url.trim()
            val jinaResult = runCatching {
                val jinaReq = Request.Builder()
                    .url("https://r.jina.ai/$cleanUrl")
                    .addHeader("Accept", "text/markdown, text/plain;q=0.9")
                    .addHeader("User-Agent", "AllChat-Android/2.0")
                    .get()
                    .build()
                client.newCall(jinaReq).execute().use { res ->
                    if (!res.isSuccessful) error("Jina HTTP ${res.code}")
                    val text = res.body?.string().orEmpty().trim()
                    if (text.length < 40) error("Empty Jina response")
                    val title = text.lineSequence()
                        .firstOrNull { it.startsWith("Title:") }
                        ?.removePrefix("Title:")
                        ?.trim()
                        ?.ifBlank { cleanUrl } ?: cleanUrl
                    ScrapedUrlResult(cleanUrl, title, text.take(maxChars))
                }
            }.getOrNull()

            if (jinaResult != null) return@withContext jinaResult

            // Direct fallback
            val directReq = Request.Builder()
                .url(cleanUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Android 14; Mobile) AllChat/2.0")
                .get()
                .build()
            client.newCall(directReq).execute().use { res ->
                if (!res.isSuccessful) {
                    throw IllegalStateException("Failed to fetch $cleanUrl (HTTP ${res.code})")
                }
                val html = res.body?.string().orEmpty()
                val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim()
                    .orEmpty().ifBlank { cleanUrl }
                val stripped = html
                    .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(maxChars)
                ScrapedUrlResult(cleanUrl, title, stripped)
            }
        }

    companion object {
        fun citationsToJson(citations: List<SearchCitation>): String? {
            if (citations.isEmpty()) return null
            val arr = JSONArray()
            citations.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("index", c.index)
                        .put("title", c.title)
                        .put("url", c.url)
                        .put("snippet", c.snippet)
                )
            }
            return arr.toString()
        }

        fun citationsFromJson(raw: String?): List<SearchCitation> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val url = o.optString("url")
                        if (url.isBlank()) continue
                        add(
                            SearchCitation(
                                index = o.optInt("index", i + 1),
                                title = o.optString("title", url),
                                url = url,
                                snippet = o.optString("snippet", "")
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
