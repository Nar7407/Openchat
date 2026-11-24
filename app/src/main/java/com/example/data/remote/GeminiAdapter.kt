package com.example.data.remote

import android.util.Base64
import com.example.domain.model.AiErrorCategory
import com.example.domain.model.AiErrorMapper
import com.example.domain.model.AiException
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

class GeminiAdapter(private val client: OkHttpClient) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun cleanBaseUrl(baseUrl: String): String = baseUrl.trimEnd('/')

    suspend fun testConnection(provider: ProviderConfig, apiKey: String): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/models?pageSize=1"
            val req = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .get()
                .build()
            try {
                client.newCall(req).execute().use { res ->
                    val elapsed = System.currentTimeMillis() - start
                    if (res.isSuccessful) {
                        ConnectionTestResult(true, elapsed, "Connected to Gemini API (${elapsed}ms)")
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
            val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/models?pageSize=100"
            val req = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .get()
                .build()
            try {
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        throw AiErrorMapper.fromHttpStatus(res.code, body, provider.name, provider.id)
                    }
                    val root = JSONObject(body)
                    val arr = root.optJSONArray("models") ?: JSONArray()
                    val result = mutableListOf<FetchedModel>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val rawName = obj.optString("name") // e.g. models/gemini-2.5-flash
                        val modelId = rawName.removePrefix("models/")
                        if (modelId.isBlank()) continue
                        val displayName = obj.optString("displayName").ifBlank { modelId }
                        val methods = obj.optJSONArray("supportedGenerationMethods")
                        val methodSet = buildSet {
                            if (methods != null) {
                                for (j in 0 until methods.length()) add(methods.optString(j))
                            }
                        }
                        val lower = modelId.lowercase()
                        val isVideo = lower.contains("veo") || methodSet.contains("predictLongRunning")
                        val isImagen = lower.contains("imagen")
                        val isGeminiImage = lower.contains("image") && lower.contains("gemini")
                        val isChat = !isVideo && !isImagen && (methodSet.isEmpty() || methodSet.contains("generateContent"))
                        if (isChat || isImagen || isVideo || isGeminiImage) {
                            result += FetchedModel(
                                modelId = modelId,
                                displayName = displayName,
                                supportsTextChat = isChat,
                                supportsImageInput = isChat && !lower.contains("gemma"),
                                supportsImageGen = isImagen || isGeminiImage,
                                supportsVideoGen = isVideo,
                                supportsSummarization = isChat
                            )
                        }
                    }
                    result.sortedBy { it.displayName }
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
        val cleanId = modelId.removePrefix("models/")
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/models/$cleanId:streamGenerateContent?alt=sse"
        val contents = JSONArray()
        for (msg in messages) {
            when (msg.role) {
                "TOOL" -> {
                    val responseObj = runCatching { JSONObject(msg.text) }
                        .getOrElse { JSONObject().put("result", msg.text) }
                    val parts = JSONArray().put(
                        JSONObject().put(
                            "functionResponse",
                            JSONObject()
                                .put("name", msg.toolName ?: "tool")
                                .put("response", responseObj)
                        )
                    )
                    contents.put(JSONObject().put("role", "user").put("parts", parts))
                }
                "ASSISTANT" -> {
                    val parts = JSONArray()
                    if (msg.text.isNotBlank()) {
                        parts.put(JSONObject().put("text", msg.text))
                    }
                    msg.toolCalls.forEach { tc ->
                        val argsObj = runCatching { JSONObject(tc.argumentsJson.ifBlank { "{}" }) }
                            .getOrDefault(JSONObject())
                        parts.put(
                            JSONObject().put(
                                "functionCall",
                                JSONObject()
                                    .put("name", tc.name)
                                    .put("args", argsObj)
                            )
                        )
                    }
                    if (parts.length() > 0) {
                        contents.put(JSONObject().put("role", "model").put("parts", parts))
                    }
                }
                else -> {
                    val parts = JSONArray()
                    if (!msg.imageBase64.isNullOrBlank()) {
                        parts.put(
                            JSONObject().put(
                                "inline_data",
                                JSONObject()
                                    .put("mime_type", msg.imageMimeType ?: "image/jpeg")
                                    .put("data", msg.imageBase64)
                            )
                        )
                    }
                    if (msg.text.isNotBlank()) {
                        parts.put(JSONObject().put("text", msg.text))
                    }
                    contents.put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", parts)
                    )
                }
            }
        }
        val payloadObj = JSONObject().put("contents", contents)
        val sysPrompt = options.systemPrompt?.trim()
        if (!sysPrompt.isNullOrEmpty()) {
            payloadObj.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sysPrompt)))
            )
        }
        val thinkingBudget = if (modelId.contains("2.5", ignoreCase = true)) {
            options.reasoningEffort.thinkingBudgetTokens
        } else null
        if (options.temperature != null || options.topP != null || options.maxTokens != null || options.jsonMode || thinkingBudget != null) {
            val genConfig = JSONObject()
            options.temperature?.let { genConfig.put("temperature", it.toDouble()) }
            options.topP?.let { genConfig.put("topP", it.coerceIn(0.01f, 1f).toDouble()) }
            options.maxTokens?.takeIf { it > 0 }?.let { genConfig.put("maxOutputTokens", it) }
            if (options.jsonMode) {
                genConfig.put("responseMimeType", "application/json")
            }
            if (thinkingBudget != null) {
                genConfig.put("thinkingConfig", JSONObject().put("thinkingBudget", thinkingBudget))
            }
            payloadObj.put("generationConfig", genConfig)
        }
        if (options.enableTools) {
            payloadObj.put(
                "tools",
                JSONArray().put(
                    JSONObject().put("functionDeclarations", ClientToolEngine.toGeminiFunctionDeclarationsJson())
                )
            )
        } else if (options.enableGeminiGoogleSearch) {
            payloadObj.put(
                "tools",
                JSONArray().put(JSONObject().put("google_search", JSONObject()))
            )
        }
        val payload = payloadObj.toString()
        val prettyRequest = runCatching { payloadObj.toString(2) }.getOrDefault(payload)
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        return kotlinx.coroutines.flow.flow {
            emit(StreamEvent.DebugRequest(url, prettyRequest))
            var insideThinking = false
            val collectedToolCalls = mutableListOf<ToolCallInvocation>()
            SseClient.stream(client, request, provider.name, provider.id).collect { sse ->
                val raw = when (sse) {
                    is SseLine.Data -> sse.payload
                    is SseLine.RawJson -> sse.jsonBody
                }
                emit(StreamEvent.RawResponseChunk(raw))
                val parsed = runCatching { JSONObject(raw) }.getOrNull()
                if (parsed != null) {
                    insideThinking = emitGeminiChunk(parsed, insideThinking, collectedToolCalls) { emit(it) }
                } else {
                    val arr = runCatching { JSONArray(raw) }.getOrNull()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            insideThinking = emitGeminiChunk(arr.getJSONObject(i), insideThinking, collectedToolCalls) { emit(it) }
                        }
                    }
                }
            }
            if (insideThinking) {
                emit(StreamEvent.TextDelta("</think>"))
            }
            if (collectedToolCalls.isNotEmpty()) {
                emit(StreamEvent.ToolCallRequest(collectedToolCalls.toList()))
            }
        }
    }

    private suspend inline fun emitGeminiChunk(
        obj: JSONObject,
        currentlyThinking: Boolean,
        collectedToolCalls: MutableList<ToolCallInvocation>,
        emitEvent: suspend (StreamEvent) -> Unit
    ): Boolean {
        var insideThinking = currentlyThinking
        val candidates = obj.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val cand0 = candidates.getJSONObject(0)
            val content = cand0.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val fnCall = part.optJSONObject("functionCall")
                    if (fnCall != null) {
                        val fnName = fnCall.optString("name", "")
                        if (fnName.isNotBlank()) {
                            val argsStr = fnCall.optJSONObject("args")?.toString() ?: "{}"
                            collectedToolCalls += ToolCallInvocation(
                                id = "gemini_call_${collectedToolCalls.size}",
                                name = fnName,
                                argumentsJson = argsStr
                            )
                        }
                    }
                    val text = part.optString("text", "")
                    val isThought = part.optBoolean("thought", false)
                    if (text.isNotEmpty()) {
                        if (isThought) {
                            if (!insideThinking) {
                                insideThinking = true
                                emitEvent(StreamEvent.TextDelta("<think>"))
                            }
                            emitEvent(StreamEvent.TextDelta(text))
                        } else {
                            if (insideThinking) {
                                insideThinking = false
                                emitEvent(StreamEvent.TextDelta("</think>\n\n"))
                            }
                            emitEvent(StreamEvent.TextDelta(text))
                        }
                    }
                }
            }
            // Extract native Gemini Google Search Grounding citations if present
            val grounding = cand0.optJSONObject("groundingMetadata")
            val chunks = grounding?.optJSONArray("groundingChunks")
            if (chunks != null && chunks.length() > 0) {
                val citations = buildList {
                    for (i in 0 until chunks.length()) {
                        val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
                        val uri = web.optString("uri").trim()
                        if (uri.isEmpty()) continue
                        add(
                            com.example.domain.model.SearchCitation(
                                index = size + 1,
                                title = web.optString("title").ifBlank { uri },
                                url = uri
                            )
                        )
                    }
                }
                if (citations.isNotEmpty()) {
                    emitEvent(StreamEvent.GroundingCitations(citations))
                }
            }
        }
        val usage = obj.optJSONObject("usageMetadata")
        if (usage != null) {
            val prompt = usage.optInt("promptTokenCount", 0)
            val completion = usage.optInt("candidatesTokenCount", 0)
            if (prompt > 0 || completion > 0) {
                emitEvent(StreamEvent.Usage(prompt, completion))
            }
        }
        return insideThinking
    }

    suspend fun generateImage(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        prompt: String,
        aspectRatio: String
    ): GeneratedImagePayload = withContext(Dispatchers.IO) {
        val cleanId = modelId.removePrefix("models/")
        val base = cleanBaseUrl(provider.effectiveBaseUrl)
        try {
            if (cleanId.contains("imagen", ignoreCase = true)) {
                val url = "$base/models/$cleanId:predict"
                val body = JSONObject()
                    .put("instances", JSONArray().put(JSONObject().put("prompt", prompt)))
                    .put(
                        "parameters",
                        JSONObject()
                            .put("sampleCount", 1)
                            .put("aspectRatio", aspectRatio)
                    )
                    .toString()
                val req = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(body.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        throw AiErrorMapper.fromHttpStatus(res.code, raw, provider.name, provider.id)
                    }
                    val json = JSONObject(raw)
                    val predictions = json.optJSONArray("predictions")
                    if (predictions == null || predictions.length() == 0) {
                        throw AiException(
                            AiErrorCategory.UNKNOWN,
                            "No image returned by $cleanId.",
                            providerId = provider.id
                        )
                    }
                    val first = predictions.getJSONObject(0)
                    val b64 = first.optString("bytesBase64Encoded")
                    val mime = first.optString("mimeType", "image/png")
                    GeneratedImagePayload(Base64.decode(b64, Base64.DEFAULT), mime)
                }
            } else {
                val url = "$base/models/$cleanId:generateContent"
                val body = JSONObject()
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", prompt))
                            )
                        )
                    )
                    .put(
                        "generationConfig",
                        JSONObject().put(
                            "responseModalities",
                            JSONArray().put("TEXT").put("IMAGE")
                        )
                    )
                    .toString()
                val req = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(body.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        throw AiErrorMapper.fromHttpStatus(res.code, raw, provider.name, provider.id)
                    }
                    val json = JSONObject(raw)
                    val candidates = json.optJSONArray("candidates")
                    val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val inline = parts.getJSONObject(i).optJSONObject("inlineData")
                                ?: parts.getJSONObject(i).optJSONObject("inline_data")
                            if (inline != null) {
                                val data = inline.optString("data")
                                val mime = inline.optString("mimeType", inline.optString("mime_type", "image/png"))
                                if (data.isNotBlank()) {
                                    return@withContext GeneratedImagePayload(
                                        Base64.decode(data, Base64.DEFAULT),
                                        mime
                                    )
                                }
                            }
                        }
                    }
                    throw AiException(
                        AiErrorCategory.UNSUPPORTED_MODEL,
                        "$cleanId completed but returned no image data. Ensure it is an image-generation capable model.",
                        providerId = provider.id
                    )
                }
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
        maskOverlayPngBytes: ByteArray?
    ): GeneratedImagePayload = withContext(Dispatchers.IO) {
        val cleanId = modelId.removePrefix("models/")
        val base = cleanBaseUrl(provider.effectiveBaseUrl)
        val url = "$base/models/$cleanId:generateContent"
        try {
            val parts = JSONArray()
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/png")
                        .put("data", Base64.encodeToString(sourcePngBytes, Base64.NO_WRAP))
                )
            )
            if (maskOverlayPngBytes != null && maskOverlayPngBytes.isNotEmpty()) {
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/png")
                            .put("data", Base64.encodeToString(maskOverlayPngBytes, Base64.NO_WRAP))
                    )
                )
            }
            val editInstruction = if (maskOverlayPngBytes != null) {
                "Edit the first image ONLY inside the region highlighted in red in the second mask overlay image, keeping everything outside the masked region unchanged. Requested edit: $prompt"
            } else {
                "Edit this image according to the following instruction while preserving its composition and style: $prompt"
            }
            parts.put(JSONObject().put("text", editInstruction))

            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .put(
                    "generationConfig",
                    JSONObject().put(
                        "responseModalities",
                        JSONArray().put("TEXT").put("IMAGE")
                    )
                )
                .toString()

            val req = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .post(body.toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw AiErrorMapper.fromHttpStatus(res.code, raw, provider.name, provider.id)
                }
                val json = JSONObject(raw)
                val candidates = json.optJSONArray("candidates")
                val resParts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                if (resParts != null) {
                    for (i in 0 until resParts.length()) {
                        val inline = resParts.getJSONObject(i).optJSONObject("inlineData")
                            ?: resParts.getJSONObject(i).optJSONObject("inline_data")
                        if (inline != null) {
                            val data = inline.optString("data")
                            val mime = inline.optString("mimeType", inline.optString("mime_type", "image/png"))
                            if (data.isNotBlank()) {
                                return@withContext GeneratedImagePayload(Base64.decode(data, Base64.DEFAULT), mime)
                            }
                        }
                    }
                }
                throw AiException(
                    AiErrorCategory.UNSUPPORTED_MODEL,
                    "$cleanId completed without returning an edited image. Select a Gemini image-editing model (e.g. gemini-2.0-flash-exp-image-generation or gemini-2.5-flash-image-preview).",
                    providerId = provider.id
                )
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
        val cleanId = modelId.removePrefix("models/")
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/models/$cleanId:predictLongRunning"
        val body = JSONObject()
            .put("instances", JSONArray().put(JSONObject().put("prompt", prompt)))
            .put(
                "parameters",
                JSONObject()
                    .put("sampleCount", 1)
                    .put("aspectRatio", if (aspectRatio == "9:16" || aspectRatio == "16:9") aspectRatio else "16:9")
            )
            .toString()
        val req = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw AiErrorMapper.fromHttpStatus(res.code, raw, provider.name, provider.id)
                }
                val json = JSONObject(raw)
                val opName = json.optString("name")
                if (opName.isBlank()) {
                    throw AiException(
                        AiErrorCategory.UNKNOWN,
                        "Video operation started without an operation ID.",
                        providerId = provider.id
                    )
                }
                VideoJobSubmission(operationName = opName)
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
        val cleanOp = operationName.trimStart('/')
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/$cleanOp"
        val req = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()
        try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw AiErrorMapper.fromHttpStatus(res.code, raw, provider.name, provider.id)
                }
                val json = JSONObject(raw)
                val done = json.optBoolean("done", false)
                if (!done) return@withContext VideoJobStatus(done = false)

                val error = json.optJSONObject("error")
                if (error != null) {
                    return@withContext VideoJobStatus(
                        done = true,
                        errorMessage = error.optString("message", "Video generation failed.")
                    )
                }

                val responseObj = json.optJSONObject("response")
                val videoUri = extractVideoUri(responseObj)
                if (videoUri.isNullOrBlank()) {
                    VideoJobStatus(
                        done = true,
                        errorMessage = "Operation completed, but no downloadable video URI was returned."
                    )
                } else {
                    VideoJobStatus(done = true, videoUrl = videoUri)
                }
            }
        } catch (t: Throwable) {
            throw AiErrorMapper.fromThrowable(t, provider.name, provider.id)
        }
    }

    private fun extractVideoUri(responseObj: JSONObject?): String? {
        if (responseObj == null) return null
        val genSamples = responseObj
            .optJSONObject("generateVideoResponse")
            ?.optJSONArray("generatedSamples")
        if (genSamples != null && genSamples.length() > 0) {
            val uri = genSamples.getJSONObject(0).optJSONObject("video")?.optString("uri")
            if (!uri.isNullOrBlank()) return uri
        }
        val genVideos = responseObj.optJSONArray("generatedVideos")
        if (genVideos != null && genVideos.length() > 0) {
            val uri = genVideos.getJSONObject(0).optJSONObject("video")?.optString("uri")
            if (!uri.isNullOrBlank()) return uri
        }
        return null
    }
}
