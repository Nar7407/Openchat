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
        messages: List<ChatInputMessage>
    ): Flow<StreamEvent> {
        val url = "${cleanBaseUrl(provider.effectiveBaseUrl)}/chat/completions"
        val msgArray = JSONArray()
        for (msg in messages) {
            val role = if (msg.role == "USER") "user" else "assistant"
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
                msgArray.put(JSONObject().put("role", role).put("content", contentParts))
            } else {
                msgArray.put(JSONObject().put("role", role).put("content", msg.text))
            }
        }
        val body = JSONObject()
            .put("model", modelId)
            .put("messages", msgArray)
            .put("stream", true)
            .put("stream_options", JSONObject().put("include_usage", true))
            .toString()

        val req = Request.Builder()
            .url(url)
            .applyAuth(apiKey)
            .post(body.toRequestBody(jsonMediaType))
            .build()

        return SseClient.stream(client, req, provider.name, provider.id).transform { sse ->
            val raw = when (sse) {
                is SseLine.Data -> sse.payload
                is SseLine.RawJson -> sse.jsonBody
            }
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@transform
            val choices = obj.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val first = choices.getJSONObject(0)
                val deltaText = first.optJSONObject("delta")?.optString("content", "")
                    ?: first.optJSONObject("message")?.optString("content", "")
                    ?: ""
                if (deltaText.isNotEmpty() && deltaText != "null") {
                    emit(StreamEvent.TextDelta(deltaText))
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
