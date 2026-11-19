package com.example.data.remote

import com.example.domain.model.AiErrorMapper
import com.example.domain.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AnthropicAdapter(private val client: OkHttpClient) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val anthropicVersion = "2023-06-01"

    private fun cleanBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return trimmed.removeSuffix("/v1")
    }

    suspend fun testConnection(provider: ProviderConfig, apiKey: String): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/v1/models"
            val req = Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", anthropicVersion)
                .get()
                .build()
            try {
                client.newCall(req).execute().use { res ->
                    val elapsed = System.currentTimeMillis() - start
                    if (res.isSuccessful) {
                        ConnectionTestResult(true, elapsed, "Connected to Anthropic (${elapsed}ms)")
                    } else {
                        val err = AiErrorMapper.fromHttpStatus(
                            res.code,
                            res.body?.string().orEmpty(),
                            provider.name,
                            provider.id
                        )
                        ConnectionTestResult(false, elapsed, err.message, err.suggestProxySetting)
                    }
                }
            } catch (t: Throwable) {
                val elapsed = System.currentTimeMillis() - start
                val err = AiErrorMapper.fromThrowable(t, provider.name, provider.id)
                ConnectionTestResult(false, elapsed, err.message, err.suggestProxySetting)
            }
        }

    suspend fun fetchModels(provider: ProviderConfig, apiKey: String): List<FetchedModel> =
        withContext(Dispatchers.IO) {
            val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/v1/models"
            val req = Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", anthropicVersion)
                .get()
                .build()
            try {
                client.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        throw AiErrorMapper.fromHttpStatus(res.code, raw, provider.name, provider.id)
                    }
                    val root = JSONObject(raw)
                    val arr = root.optJSONArray("data") ?: JSONArray()
                    val result = mutableListOf<FetchedModel>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("id")
                        if (id.isBlank()) continue
                        val displayName = obj.optString("display_name").ifBlank { id }
                        result += FetchedModel(
                            modelId = id,
                            displayName = displayName,
                            supportsTextChat = true,
                            supportsImageInput = true,
                            supportsImageGen = false,
                            supportsVideoGen = false,
                            supportsSummarization = true
                        )
                    }
                    result
                }
            } catch (t: Throwable) {
                throw AiErrorMapper.fromThrowable(t, provider.name, provider.id)
            }
        }

    fun streamChat(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        messages: List<ChatInputMessage>
    ): Flow<StreamEvent> {
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/v1/messages"
        val msgArray = JSONArray()
        for (msg in messages) {
            val role = if (msg.role == "USER") "user" else "assistant"
            if (!msg.imageBase64.isNullOrBlank()) {
                val contentArr = JSONArray()
                contentArr.put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", msg.imageMimeType ?: "image/jpeg")
                                .put("data", msg.imageBase64)
                        )
                )
                if (msg.text.isNotBlank()) {
                    contentArr.put(JSONObject().put("type", "text").put("text", msg.text))
                }
                msgArray.put(JSONObject().put("role", role).put("content", contentArr))
            } else {
                msgArray.put(JSONObject().put("role", role).put("content", msg.text))
            }
        }

        val body = JSONObject()
            .put("model", modelId)
            .put("max_tokens", 4096)
            .put("stream", true)
            .put("messages", msgArray)
            .toString()

        val req = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", anthropicVersion)
            .post(body.toRequestBody(jsonMediaType))
            .build()

        var latestInputTokens = 0
        return SseClient.stream(client, req, provider.name, provider.id).transform { sse ->
            val raw = when (sse) {
                is SseLine.Data -> sse.payload
                is SseLine.RawJson -> sse.jsonBody
            }
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@transform
            when (obj.optString("type")) {
                "message_start" -> {
                    val input = obj.optJSONObject("message")
                        ?.optJSONObject("usage")
                        ?.optInt("input_tokens", 0) ?: 0
                    if (input > 0) latestInputTokens = input
                }
                "content_block_delta" -> {
                    val delta = obj.optJSONObject("delta")
                    if (delta?.optString("type") == "text_delta") {
                        val text = delta.optString("text", "")
                        if (text.isNotEmpty()) emit(StreamEvent.TextDelta(text))
                    }
                }
                "message_delta" -> {
                    val output = obj.optJSONObject("usage")?.optInt("output_tokens", 0) ?: 0
                    if (latestInputTokens > 0 || output > 0) {
                        emit(StreamEvent.Usage(latestInputTokens, output))
                    }
                }
                else -> {
                    // Fallback for non-streaming JSON response
                    val content = obj.optJSONArray("content")
                    if (content != null && content.length() > 0) {
                        val text = content.getJSONObject(0).optString("text", "")
                        if (text.isNotEmpty()) emit(StreamEvent.TextDelta(text))
                    }
                }
            }
        }
    }
}
