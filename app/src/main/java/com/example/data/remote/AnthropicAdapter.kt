package com.example.data.remote

import com.example.domain.model.AiErrorMapper
import com.example.domain.model.ChatOptions
import com.example.domain.model.ClientToolEngine
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
        messages: List<ChatInputMessage>,
        options: ChatOptions = ChatOptions()
    ): Flow<StreamEvent> {
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/v1/messages"
        val msgArray = JSONArray()
        for (msg in messages) {
            when (msg.role) {
                "TOOL" -> {
                    val contentArr = JSONArray().put(
                        JSONObject()
                            .put("type", "tool_result")
                            .put("tool_use_id", msg.toolCallId ?: "toolu_0")
                            .put("content", msg.text)
                    )
                    msgArray.put(JSONObject().put("role", "user").put("content", contentArr))
                }
                "ASSISTANT" -> {
                    if (msg.toolCalls.isNotEmpty()) {
                        val contentArr = JSONArray()
                        if (msg.text.isNotBlank()) {
                            contentArr.put(JSONObject().put("type", "text").put("text", msg.text))
                        }
                        msg.toolCalls.forEach { tc ->
                            val inputObj = runCatching { JSONObject(tc.argumentsJson.ifBlank { "{}" }) }
                                .getOrDefault(JSONObject())
                            contentArr.put(
                                JSONObject()
                                    .put("type", "tool_use")
                                    .put("id", tc.id)
                                    .put("name", tc.name)
                                    .put("input", inputObj)
                            )
                        }
                        msgArray.put(JSONObject().put("role", "assistant").put("content", contentArr))
                    } else {
                        msgArray.put(JSONObject().put("role", "assistant").put("content", msg.text))
                    }
                }
                else -> {
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
                        msgArray.put(JSONObject().put("role", "user").put("content", contentArr))
                    } else {
                        msgArray.put(JSONObject().put("role", "user").put("content", msg.text))
                    }
                }
            }
        }

        val bodyObj = JSONObject()
            .put("model", modelId)
            .put("max_tokens", options.maxTokens?.takeIf { it > 0 } ?: 4096)
            .put("stream", true)
            .put("messages", msgArray)
        val combinedSys = buildList {
            options.systemPrompt?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            if (options.jsonMode) {
                add("Respond with strictly valid JSON only. Do not wrap in markdown or add prose outside the JSON object.")
            }
        }.joinToString("\n\n")
        if (combinedSys.isNotEmpty()) {
            bodyObj.put("system", combinedSys)
        }
        options.temperature?.let { bodyObj.put("temperature", it.coerceIn(0f, 1f).toDouble()) }
        options.topP?.let { bodyObj.put("top_p", it.coerceIn(0.01f, 1f).toDouble()) }
        if (options.enableTools) {
            bodyObj.put("tools", ClientToolEngine.toAnthropicToolsJson())
        }
        val body = bodyObj.toString()
        val prettyRequest = runCatching { bodyObj.toString(2) }.getOrDefault(body)

        val req = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", anthropicVersion)
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return kotlinx.coroutines.flow.flow {
            emit(StreamEvent.DebugRequest(url, prettyRequest))
            var latestInputTokens = 0
            var insideThinking = false
            val activeToolIds = LinkedHashMap<Int, String>()
            val activeToolNames = LinkedHashMap<Int, String>()
            val activeToolArgs = LinkedHashMap<Int, StringBuilder>()

            SseClient.stream(client, req, provider.name, provider.id).collect { sse ->
                val raw = when (sse) {
                    is SseLine.Data -> sse.payload
                    is SseLine.RawJson -> sse.jsonBody
                }
                emit(StreamEvent.RawResponseChunk(raw))
                val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@collect
                when (obj.optString("type")) {
                    "message_start" -> {
                        val input = obj.optJSONObject("message")
                            ?.optJSONObject("usage")
                            ?.optInt("input_tokens", 0) ?: 0
                        if (input > 0) latestInputTokens = input
                    }
                    "content_block_start" -> {
                        val block = obj.optJSONObject("content_block")
                        if (block?.optString("type") == "tool_use") {
                            val idx = obj.optInt("index", activeToolNames.size)
                            activeToolIds[idx] = block.optString("id", "toolu_$idx")
                            activeToolNames[idx] = block.optString("name", "")
                            activeToolArgs[idx] = StringBuilder()
                        }
                    }
                    "content_block_delta" -> {
                        val delta = obj.optJSONObject("delta")
                        when (delta?.optString("type")) {
                            "thinking_delta" -> {
                                val thought = delta.optString("thinking", "")
                                if (thought.isNotEmpty()) {
                                    if (!insideThinking) {
                                        insideThinking = true
                                        emit(StreamEvent.TextDelta("<think>"))
                                    }
                                    emit(StreamEvent.TextDelta(thought))
                                }
                            }
                            "text_delta" -> {
                                val text = delta.optString("text", "")
                                if (text.isNotEmpty()) {
                                    if (insideThinking) {
                                        insideThinking = false
                                        emit(StreamEvent.TextDelta("</think>\n\n"))
                                    }
                                    emit(StreamEvent.TextDelta(text))
                                }
                            }
                            "input_json_delta" -> {
                                val idx = obj.optInt("index", 0)
                                val partial = delta.optString("partial_json", "")
                                if (partial.isNotEmpty()) {
                                    activeToolArgs.getOrPut(idx) { StringBuilder() }.append(partial)
                                }
                            }
                        }
                    }
                    "message_delta" -> {
                        val output = obj.optJSONObject("usage")?.optInt("output_tokens", 0) ?: 0
                        if (latestInputTokens > 0 || output > 0) {
                            emit(StreamEvent.Usage(latestInputTokens, output))
                        }
                    }
                    else -> {
                        val content = obj.optJSONArray("content")
                        if (content != null && content.length() > 0) {
                            val text = content.getJSONObject(0).optString("text", "")
                            if (text.isNotEmpty()) emit(StreamEvent.TextDelta(text))
                        }
                    }
                }
            }
            if (insideThinking) {
                emit(StreamEvent.TextDelta("</think>"))
            }
            if (activeToolNames.isNotEmpty()) {
                val calls = activeToolNames.entries.filter { it.value.isNotBlank() }.map { (idx, name) ->
                    ToolCallInvocation(
                        id = activeToolIds[idx] ?: "toolu_$idx",
                        name = name,
                        argumentsJson = activeToolArgs[idx]?.toString()?.ifBlank { "{}" } ?: "{}"
                    )
                }
                if (calls.isNotEmpty()) {
                    emit(StreamEvent.ToolCallRequest(calls))
                }
            }
        }
    }
}
