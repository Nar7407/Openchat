package com.example.data.remote

import android.util.Base64
import com.example.domain.model.AiErrorCategory
import com.example.domain.model.AiErrorMapper
import com.example.domain.model.AiException
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
        messages: List<ChatInputMessage>
    ): Flow<StreamEvent> {
        val cleanId = modelId.removePrefix("models/")
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/models/$cleanId:streamGenerateContent?alt=sse"
        val contents = JSONArray()
        for (msg in messages) {
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
                    .put("role", if (msg.role == "USER") "user" else "model")
                    .put("parts", parts)
            )
        }
        val payload = JSONObject().put("contents", contents).toString()
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        return SseClient.stream(client, request, provider.name, provider.id).transform { sse ->
            val raw = when (sse) {
                is SseLine.Data -> sse.payload
                is SseLine.RawJson -> sse.jsonBody
            }
            val parsed = runCatching { JSONObject(raw) }.getOrNull()
            if (parsed != null) {
                emitGeminiChunk(parsed) { emit(it) }
            } else {
                // Handle JSON array wrapper if non-SSE JSON array returned
                val arr = runCatching { JSONArray(raw) }.getOrNull()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        emitGeminiChunk(arr.getJSONObject(i)) { emit(it) }
                    }
                }
            }
        }
    }

    private suspend inline fun emitGeminiChunk(
        obj: JSONObject,
        emitEvent: suspend (StreamEvent) -> Unit
    ) {
        val candidates = obj.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val content = candidates.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val text = parts.getJSONObject(i).optString("text", "")
                    if (text.isNotEmpty()) {
                        emitEvent(StreamEvent.TextDelta(text))
                    }
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
