package com.example.data.remote

import android.util.Base64
import com.example.domain.model.AiErrorCategory
import com.example.domain.model.AiErrorMapper
import com.example.domain.model.AiException
import com.example.domain.model.ChatOptions
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ClientToolEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAiCompatibleAdapter(private val client: OkHttpClient) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun cleanBaseUrl(baseUrl: String): String = baseUrl.trimEnd('/')

    private fun Request.Builder.applyAuth(apiKey: String): Request.Builder {
        if (apiKey.isNotBlank()) {
            addHeader("Authorization", "Bearer $apiKey")
        }
        return this
    }

    suspend fun testConnection(provider: ProviderConfig, apiKey: String): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/models"
            val req = Request.Builder()
                .url(url)
                .applyAuth(apiKey)
                .get()
                .build()
            try {
                client.newCall(req).execute().use { res ->
                    val elapsed = System.currentTimeMillis() - start
                    if (res.isSuccessful) {
                        ConnectionTestResult(true, elapsed, "Connected to ${provider.name} (${elapsed}ms)")
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
            val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/models"
            val req = Request.Builder()
                .url(url)
                .applyAuth(apiKey)
                .get()
                .build()
            try {
                client.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        throw AiErrorMapper.fromHttpStatus(res.code, raw, provider.name, provider.id)
                    }
                    val root = JSONObject(raw)
                    val arr = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
                    val list = mutableListOf<FetchedModel>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optString("id").ifBlank { obj.optString("name") }
                        if (id.isBlank()) continue
                        val lower = id.lowercase()
                        val isImageGen = lower.contains("dall-e") ||
                            lower.contains("gpt-image") ||
                            lower.contains("flux") ||
                            lower.contains("sdxl") ||
                            lower.contains("stable-diffusion")
                        val isVideoGen = lower.contains("sora") || lower.contains("video")
                        val isNonChatUtility = lower.contains("embed") ||
                            lower.contains("whisper") ||
                            lower.contains("tts-") ||
                            lower.contains("rerank") ||
                            lower.contains("reward") ||
                            lower.contains("moderation") ||
                            lower.contains("nvclip") ||
                            lower.contains("retriever")
                        if (isNonChatUtility) continue
                        val supportsVision = lower.contains("gpt-4o") ||
                            lower.contains("gpt-4.1") ||
                            lower.contains("gpt-5") ||
                            lower.contains("vision") ||
                            lower.contains("claude") ||
                            lower.contains("gemini") ||
                            lower.contains("llava") ||
                            lower.contains("qwen-vl")
                        list += FetchedModel(
                            modelId = id,
                            displayName = id,
                            supportsTextChat = !isImageGen && !isVideoGen,
                            supportsImageInput = supportsVision && !isImageGen && !isVideoGen,
                            supportsImageGen = isImageGen,
                            supportsVideoGen = isVideoGen,
                            supportsSummarization = !isImageGen && !isVideoGen
                        )
                    }
                    list.sortedBy { it.displayName }
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
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/chat/completions"
        val msgArray = JSONArray()
        val sysPrompt = options.systemPrompt?.trim()
        if (!sysPrompt.isNullOrEmpty()) {
            msgArray.put(JSONObject().put("role", "system").put("content", sysPrompt))
        }
        for (msg in messages) {
            when (msg.role) {
                "TOOL" -> {
                    msgArray.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", msg.toolCallId ?: "call_0")
                            .put("content", msg.text)
                    )
                }
                "ASSISTANT" -> {
                    if (msg.toolCalls.isNotEmpty()) {
                        val tcArr = JSONArray()
                        msg.toolCalls.forEach { tc ->
                            tcArr.put(
                                JSONObject()
                                    .put("id", tc.id)
                                    .put("type", "function")
                                    .put(
                                        "function",
                                        JSONObject()
                                            .put("name", tc.name)
                                            .put("arguments", tc.argumentsJson.ifBlank { "{}" })
                                    )
                            )
                        }
                        val assistantObj = JSONObject()
                            .put("role", "assistant")
                            .put("tool_calls", tcArr)
                        if (msg.text.isNotBlank()) assistantObj.put("content", msg.text) else assistantObj.put("content", JSONObject.NULL)
                        msgArray.put(assistantObj)
                    } else {
                        msgArray.put(JSONObject().put("role", "assistant").put("content", msg.text))
                    }
                }
                else -> {
                    if (!msg.imageBase64.isNullOrBlank()) {
                        val contentParts = JSONArray()
                        if (msg.text.isNotBlank()) {
                            contentParts.put(
                                JSONObject().put("type", "text").put("text", msg.text)
                            )
                        }
                        val mime = msg.imageMimeType ?: "image/jpeg"
                        contentParts.put(
                            JSONObject()
                                .put("type", "image_url")
                                .put(
                                    "image_url",
                                    JSONObject().put("url", "data:$mime;base64,${msg.imageBase64}")
                                )
                        )
                        msgArray.put(JSONObject().put("role", "user").put("content", contentParts))
                    } else {
                        msgArray.put(JSONObject().put("role", "user").put("content", msg.text))
                    }
                }
            }
        }
        val bodyObj = JSONObject()
            .put("model", modelId)
            .put("messages", msgArray)
            .put("stream", true)
            .put("stream_options", JSONObject().put("include_usage", true))
        options.temperature?.let { bodyObj.put("temperature", it.toDouble()) }
        options.topP?.let { bodyObj.put("top_p", it.coerceIn(0.01f, 1f).toDouble()) }
        options.maxTokens?.takeIf { it > 0 }?.let { bodyObj.put("max_tokens", it) }
        val lowerModelId = modelId.lowercase()
        val isOpenAiReasoning = lowerModelId.startsWith("o1") || lowerModelId.startsWith("o3") || lowerModelId.startsWith("o4") ||
            lowerModelId.contains("/o1") || lowerModelId.contains("/o3") || lowerModelId.contains("/o4")
        if (isOpenAiReasoning && options.reasoningEffort != com.example.domain.model.ReasoningEffort.BALANCED) {
            bodyObj.put(
                "reasoning_effort",
                if (options.reasoningEffort == com.example.domain.model.ReasoningEffort.DEEP) "high" else "low"
            )
        }
        if (options.jsonMode) {
            bodyObj.put("response_format", JSONObject().put("type", "json_object"))
        }
        if (options.enableTools) {
            bodyObj.put("tools", ClientToolEngine.toOpenAiToolsJson())
            bodyObj.put("tool_choice", "auto")
        }
        val body = bodyObj.toString()
        val prettyRequest = runCatching { bodyObj.toString(2) }.getOrDefault(body)

        val req = Request.Builder()
            .url(url)
            .applyAuth(apiKey)
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return kotlinx.coroutines.flow.flow {
            emit(StreamEvent.DebugRequest(url, prettyRequest))
            var insideThinking = false
            val toolCallIds = LinkedHashMap<Int, String>()
            val toolCallNames = LinkedHashMap<Int, String>()
            val toolCallArgs = LinkedHashMap<Int, StringBuilder>()

            SseClient.stream(client, req, provider.name, provider.id).collect { sse ->
                val raw = when (sse) {
                    is SseLine.Data -> sse.payload
                    is SseLine.RawJson -> sse.jsonBody
                }
                emit(StreamEvent.RawResponseChunk(raw))
                val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@collect
                val choices = obj.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val first = choices.getJSONObject(0)
                    val deltaObj = first.optJSONObject("delta") ?: first.optJSONObject("message")
                    val reasoningText = deltaObj?.optString("reasoning_content", "")
                        ?.takeIf { it.isNotEmpty() && it != "null" }
                        ?: deltaObj?.optString("reasoning", "")?.takeIf { it.isNotEmpty() && it != "null" }
                        ?: ""
                    if (reasoningText.isNotEmpty()) {
                        if (!insideThinking) {
                            insideThinking = true
                            emit(StreamEvent.TextDelta("<think>"))
                        }
                        emit(StreamEvent.TextDelta(reasoningText))
                    }
                    val deltaText = deltaObj?.optString("content", "") ?: ""
                    if (deltaText.isNotEmpty() && deltaText != "null") {
                        if (insideThinking) {
                            insideThinking = false
                            emit(StreamEvent.TextDelta("</think>\n\n"))
                        }
                        emit(StreamEvent.TextDelta(deltaText))
                    }
                    val tcArr = deltaObj?.optJSONArray("tool_calls")
                    if (tcArr != null) {
                        for (i in 0 until tcArr.length()) {
                            val tc = tcArr.optJSONObject(i) ?: continue
                            val idx = tc.optInt("index", i)
                            val id = tc.optString("id", "")
                            if (id.isNotBlank()) toolCallIds[idx] = id
                            val fn = tc.optJSONObject("function")
                            if (fn != null) {
                                val name = fn.optString("name", "")
                                if (name.isNotBlank()) toolCallNames[idx] = name
                                val argChunk = fn.optString("arguments", "")
                                if (argChunk.isNotEmpty()) {
                                    toolCallArgs.getOrPut(idx) { StringBuilder() }.append(argChunk)
                                }
                            }
                        }
                    }
                }
                val usage = obj.optJSONObject("usage")
                if (usage != null) {
                    val prompt = usage.optInt("prompt_tokens", 0)
                    val completion = usage.optInt("completion_tokens", 0)
                    if (prompt > 0 || completion > 0) {
                        emit(StreamEvent.Usage(prompt, completion))
                    }
                }
            }
            if (insideThinking) {
                emit(StreamEvent.TextDelta("</think>"))
            }
            if (toolCallNames.isNotEmpty()) {
                val invocations = toolCallNames.entries.map { (idx, name) ->
                    ToolCallInvocation(
                        id = toolCallIds[idx] ?: "call_$idx",
                        name = name,
                        argumentsJson = toolCallArgs[idx]?.toString()?.ifBlank { "{}" } ?: "{}"
                    )
                }
                emit(StreamEvent.ToolCallRequest(invocations))
            }
        }
    }

    suspend fun generateImage(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        prompt: String,
        aspectRatio: String
    ): GeneratedImagePayload = withContext(Dispatchers.IO) {
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/images/generations"
        val size = when (aspectRatio) {
            "16:9" -> "1792x1024"
            "9:16" -> "1024x1792"
            else -> "1024x1024"
        }
        val body = JSONObject()
            .put("model", modelId)
            .put("prompt", prompt)
            .put("n", 1)
            .put("size", size)
            .put("response_format", "b64_json")
            .toString()

        val req = Request.Builder()
            .url(url)
            .applyAuth(apiKey)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw AiErrorMapper.fromHttpStatus(res.code, raw, provider.name, provider.id)
                }
                val json = JSONObject(raw)
                val dataArr = json.optJSONArray("data")
                if (dataArr == null || dataArr.length() == 0) {
                    throw AiException(
                        AiErrorCategory.UNKNOWN,
                        "No image returned by ${provider.name}.",
                        providerId = provider.id
                    )
                }
                val first = dataArr.getJSONObject(0)
                val b64 = first.optString("b64_json")
                if (b64.isNotBlank()) {
                    return@withContext GeneratedImagePayload(
                        Base64.decode(b64, Base64.DEFAULT),
                        "image/png"
                    )
                }
                val remoteUrl = first.optString("url")
                if (remoteUrl.isNotBlank()) {
                    val downloadReq = Request.Builder().url(remoteUrl).get().build()
                    client.newCall(downloadReq).execute().use { imgRes ->
                        val imgBytes = imgRes.body?.bytes()
                        if (!imgRes.isSuccessful || imgBytes == null) {
                            throw AiErrorMapper.fromHttpStatus(imgRes.code, "", provider.name, provider.id)
                        }
                        return@withContext GeneratedImagePayload(
                            imgBytes,
                            imgRes.header("Content-Type") ?: "image/png"
                        )
                    }
                }
                throw AiException(
                    AiErrorCategory.UNKNOWN,
                    "Response from ${provider.name} contained neither image bytes nor URL.",
                    providerId = provider.id
                )
            }
        } catch (t: Throwable) {
            throw AiErrorMapper.fromThrowable(t, provider.name, provider.id)
        }
    }

    suspend fun editImage(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        prompt: String,
        sourcePngBytes: ByteArray,
        maskPngBytes: ByteArray?
    ): GeneratedImagePayload = withContext(Dispatchers.IO) {
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/images/edits"
        val pngMedia = "image/png".toMediaType()
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", modelId)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("n", "1")
            .addFormDataPart("size", "1024x1024")
            .addFormDataPart("response_format", "b64_json")
            .addFormDataPart("image", "source.png", sourcePngBytes.toRequestBody(pngMedia))
        if (maskPngBytes != null && maskPngBytes.isNotEmpty()) {
            multipartBuilder.addFormDataPart("mask", "mask.png", maskPngBytes.toRequestBody(pngMedia))
        }
        val req = Request.Builder()
            .url(url)
            .applyAuth(apiKey)
            .post(multipartBuilder.build())
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw AiErrorMapper.fromHttpStatus(res.code, raw, provider.name, provider.id)
                }
                val json = JSONObject(raw)
                val dataArr = json.optJSONArray("data")
                if (dataArr == null || dataArr.length() == 0) {
                    throw AiException(AiErrorCategory.UNKNOWN, "No edited image returned by ${provider.name}.", providerId = provider.id)
                }
                val first = dataArr.getJSONObject(0)
                val b64 = first.optString("b64_json")
                if (b64.isNotBlank()) {
                    return@withContext GeneratedImagePayload(Base64.decode(b64, Base64.DEFAULT), "image/png")
                }
                val remoteUrl = first.optString("url")
                if (remoteUrl.isNotBlank()) {
                    val downloadReq = Request.Builder().url(remoteUrl).get().build()
                    client.newCall(downloadReq).execute().use { imgRes ->
                        val imgBytes = imgRes.body?.bytes()
                        if (!imgRes.isSuccessful || imgBytes == null) {
                            throw AiErrorMapper.fromHttpStatus(imgRes.code, "", provider.name, provider.id)
                        }
                        return@withContext GeneratedImagePayload(imgBytes, imgRes.header("Content-Type") ?: "image/png")
                    }
                }
                throw AiException(AiErrorCategory.UNKNOWN, "Image edit response contained no image bytes.", providerId = provider.id)
            }
        } catch (t: Throwable) {
            throw AiErrorMapper.fromThrowable(t, provider.name, provider.id)
        }
    }

    suspend fun submitVideo(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        prompt: String,
        aspectRatio: String
    ): VideoJobSubmission = withContext(Dispatchers.IO) {
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/videos"
        val body = JSONObject()
            .put("model", modelId)
            .put("prompt", prompt)
            .put("aspect_ratio", aspectRatio)
            .toString()
        val req = Request.Builder()
            .url(url)
            .applyAuth(apiKey)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw AiErrorMapper.fromHttpStatus(res.code, raw, provider.name, provider.id)
                }
                val json = JSONObject(raw)
                val jobId = json.optString("id").ifBlank { json.optString("task_id") }
                val immediateUrl = json.optString("url").takeIf { it.isNotBlank() }
                if (jobId.isBlank() && immediateUrl == null) {
                    throw AiException(
                        AiErrorCategory.UNSUPPORTED_MODEL,
                        "${provider.name} did not return a video job ID or video URL.",
                        providerId = provider.id
                    )
                }
                VideoJobSubmission(operationName = jobId, immediateVideoUrl = immediateUrl)
            }
        } catch (t: Throwable) {
            throw AiErrorMapper.fromThrowable(t, provider.name, provider.id)
        }
    }

    suspend fun pollVideo(
        provider: ProviderConfig,
        apiKey: String,
        operationName: String
    ): VideoJobStatus = withContext(Dispatchers.IO) {
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/videos/$operationName"
        val req = Request.Builder()
            .url(url)
            .applyAuth(apiKey)
            .get()
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw AiErrorMapper.fromHttpStatus(res.code, raw, provider.name, provider.id)
                }
                val json = JSONObject(raw)
                val status = json.optString("status").lowercase()
                when (status) {
                    "completed", "succeeded", "done" -> {
                        val videoUrl = json.optString("url")
                            .ifBlank { json.optJSONArray("data")?.optJSONObject(0)?.optString("url").orEmpty() }
                            .ifBlank { "$url/content" }
                        VideoJobStatus(done = true, videoUrl = videoUrl)
                    }
                    "failed", "error", "canceled" -> {
                        val msg = json.optJSONObject("error")?.optString("message")
                            ?: json.optString("message", "Video generation failed.")
                        VideoJobStatus(done = true, errorMessage = msg)
                    }
                    else -> VideoJobStatus(done = false)
                }
            }
        } catch (t: Throwable) {
            throw AiErrorMapper.fromThrowable(t, provider.name, provider.id)
        }
    }
}
