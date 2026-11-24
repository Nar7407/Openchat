package com.example.data.remote

import com.example.domain.model.AiErrorCategory
import com.example.domain.model.AiErrorMapper
import com.example.domain.model.AiException
import com.example.domain.model.ChatOptions
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class AiRouter(private val okHttpClient: OkHttpClient) {

    private val gemini = GeminiAdapter(okHttpClient)
    private val openAi = OpenAiCompatibleAdapter(okHttpClient)
    private val anthropic = AnthropicAdapter(okHttpClient)

    // Remembers the last working key index per provider ID so rotated keys stay active
    private val activeKeyIndexByProvider = ConcurrentHashMap<Long, Int>()

    /**
     * Parses comma- or newline-separated API keys for automatic 429 rate-limit fallback rotation.
     */
    fun parseKeyPool(rawKey: String): List<String> {
        val keys = rawKey.split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return if (keys.isEmpty()) listOf("") else keys
    }

    private fun orderedCandidates(providerId: Long, rawKey: String): List<Pair<Int, String>> {
        val pool = parseKeyPool(rawKey)
        val start = (activeKeyIndexByProvider[providerId] ?: 0).coerceIn(0, pool.lastIndex)
        return List(pool.size) { offset ->
            val idx = (start + offset) % pool.size
            idx to pool[idx]
        }
    }

    private suspend fun <T> executeWithKeyRotation(
        provider: ProviderConfig,
        rawApiKey: String,
        block: suspend (singleKey: String) -> T
    ): T {
        val candidates = orderedCandidates(provider.id, rawApiKey)
        var lastError: Throwable? = null
        for ((keyIndex, singleKey) in candidates) {
            try {
                val result = block(singleKey)
                activeKeyIndexByProvider[provider.id] = keyIndex
                return result
            } catch (ex: AiException) {
                lastError = ex
                if (ex.category == AiErrorCategory.RATE_LIMIT && candidates.size > 1) {
                    continue
                }
                throw ex
            }
        }
        throw lastError ?: AiException(AiErrorCategory.RATE_LIMIT, "All API keys hit rate limits on ${provider.name}.")
    }

    suspend fun testConnection(provider: ProviderConfig, apiKey: String): ConnectionTestResult {
        val candidates = orderedCandidates(provider.id, apiKey)
        var lastResult: ConnectionTestResult? = null
        for ((keyIndex, singleKey) in candidates) {
            val res = when (provider.type) {
                ProviderType.GEMINI -> gemini.testConnection(provider, singleKey)
                ProviderType.OPENAI_COMPATIBLE -> openAi.testConnection(provider, singleKey)
                ProviderType.ANTHROPIC -> anthropic.testConnection(provider, singleKey)
            }
            if (res.success) {
                activeKeyIndexByProvider[provider.id] = keyIndex
                val suffix = if (candidates.size > 1) " [Key #${keyIndex + 1}/${candidates.size}]" else ""
                return res.copy(message = res.message + suffix)
            }
            lastResult = res
            val looksRateLimited = res.message.contains("429") ||
                res.message.contains("rate limit", ignoreCase = true) ||
                res.message.contains("quota", ignoreCase = true)
            if (!looksRateLimited) return res
        }
        return lastResult ?: ConnectionTestResult(false, 0, "Connection failed.")
    }

    suspend fun fetchModels(provider: ProviderConfig, apiKey: String): List<FetchedModel> =
        executeWithKeyRotation(provider, apiKey) { singleKey ->
            when (provider.type) {
                ProviderType.GEMINI -> gemini.fetchModels(provider, singleKey)
                ProviderType.OPENAI_COMPATIBLE -> openAi.fetchModels(provider, singleKey)
                ProviderType.ANTHROPIC -> anthropic.fetchModels(provider, singleKey)
            }
        }

    /**
     * Cross-Provider Payload Sanitizer:
     * 1) Strips <think>...</think> and <reasoning>...</reasoning> blocks from prior assistant messages
     *    so switching from a Thinking model (Gemini/Claude/DeepSeek-R1) to a strict endpoint never causes 400 errors.
     * 2) Converts unsupported image attachments into clean text placeholders when the target model is text-only.
     * 3) Normalizes consecutive same-role text messages for strict schema providers (Anthropic / DeepSeek / Groq).
     */
    private fun sanitizeMessagesForProvider(
        provider: ProviderConfig,
        modelId: String,
        messages: List<ChatInputMessage>,
        options: ChatOptions
    ): List<ChatInputMessage> {
        val thinkRegex = Regex("""<(?:think|reasoning)>[\s\S]*?</(?:think|reasoning)>""", RegexOption.IGNORE_CASE)
        val lowerModel = modelId.lowercase()
        val inferredTextOnly = !options.supportsVisionInput || (
            provider.type == ProviderType.OPENAI_COMPATIBLE &&
                (lowerModel.contains("deepseek") || lowerModel.contains("r1") || lowerModel.contains("qwen-2.5-coder"))
            )

        val cleaned = messages.map { msg ->
            var text = msg.text
            if (msg.role.equals("ASSISTANT", ignoreCase = true) && msg.toolCalls.isEmpty()) {
                val stripped = text.replace(thinkRegex, "").trim()
                if (stripped.isNotBlank()) text = stripped
            }
            if (inferredTextOnly && !msg.imageBase64.isNullOrBlank()) {
                val note = "[Attached image omitted — current model ($modelId) is text-only]"
                val mergedText = if (text.isBlank()) note else "$text\n\n$note"
                msg.copy(
                    text = mergedText,
                    imageBase64 = null,
                    imageMimeType = null
                )
            } else {
                msg.copy(text = text)
            }
        }

        // Collapse consecutive same-role non-tool text turns to prevent 400 Bad Request on strict providers
        val normalized = mutableListOf<ChatInputMessage>()
        for (msg in cleaned) {
            val prev = normalized.lastOrNull()
            val canMerge = prev != null &&
                prev.role.equals(msg.role, ignoreCase = true) &&
                !msg.role.equals("TOOL", ignoreCase = true) &&
                prev.toolCalls.isEmpty() && msg.toolCalls.isEmpty() &&
                prev.imageBase64 == null && msg.imageBase64 == null
            if (canMerge && prev != null) {
                normalized[normalized.lastIndex] = prev.copy(
                    text = listOf(prev.text, msg.text).filter { it.isNotBlank() }.joinToString("\n\n")
                )
            } else {
                normalized += msg
            }
        }
        return normalized
    }

    fun streamChat(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        messages: List<ChatInputMessage>,
        options: ChatOptions = ChatOptions()
    ): Flow<StreamEvent> = flow {
        val sanitizedMessages = sanitizeMessagesForProvider(provider, modelId, messages, options)
        val candidates = orderedCandidates(provider.id, apiKey)
        var lastError: Throwable? = null

        for ((keyIndex, singleKey) in candidates) {
            var emittedText = false
            try {
                val upstream = when (provider.type) {
                    ProviderType.GEMINI -> gemini.streamChat(provider, singleKey, modelId, sanitizedMessages, options)
                    ProviderType.OPENAI_COMPATIBLE -> openAi.streamChat(provider, singleKey, modelId, sanitizedMessages, options)
                    ProviderType.ANTHROPIC -> anthropic.streamChat(provider, singleKey, modelId, sanitizedMessages, options)
                }
                upstream.collect { event ->
                    if (event is StreamEvent.TextDelta) emittedText = true
                    emit(event)
                }
                activeKeyIndexByProvider[provider.id] = keyIndex
                return@flow
            } catch (ex: AiException) {
                lastError = ex
                if (!emittedText && ex.category == AiErrorCategory.RATE_LIMIT && candidates.size > 1) {
                    continue
                }
                throw ex
            }
        }
        throw lastError ?: AiException(
            AiErrorCategory.RATE_LIMIT,
            "All ${candidates.size} configured API keys hit rate limits on ${provider.name}."
        )
    }

    suspend fun generateImage(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        prompt: String,
        aspectRatio: String
    ): GeneratedImagePayload = executeWithKeyRotation(provider, apiKey) { singleKey ->
        when (provider.type) {
            ProviderType.GEMINI -> gemini.generateImage(provider, singleKey, modelId, prompt, aspectRatio)
            ProviderType.OPENAI_COMPATIBLE -> openAi.generateImage(provider, singleKey, modelId, prompt, aspectRatio)
            ProviderType.ANTHROPIC -> throw AiException(
                AiErrorCategory.UNSUPPORTED_MODEL,
                "Anthropic does not natively support image generation.",
                providerId = provider.id
            )
        }
    }

    suspend fun editImage(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        prompt: String,
        sourcePngBytes: ByteArray,
        openAiTransparentMaskPng: ByteArray?,
        geminiOverlayMaskPng: ByteArray?
    ): GeneratedImagePayload = executeWithKeyRotation(provider, apiKey) { singleKey ->
        when (provider.type) {
            ProviderType.GEMINI -> gemini.editImage(provider, singleKey, modelId, prompt, sourcePngBytes, geminiOverlayMaskPng)
            ProviderType.OPENAI_COMPATIBLE -> openAi.editImage(provider, singleKey, modelId, prompt, sourcePngBytes, openAiTransparentMaskPng)
            ProviderType.ANTHROPIC -> throw AiException(
                AiErrorCategory.UNSUPPORTED_MODEL,
                "Anthropic does not natively support image editing/inpainting.",
                providerId = provider.id
            )
        }
    }

    suspend fun submitVideo(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        prompt: String,
        aspectRatio: String
    ): VideoJobSubmission = executeWithKeyRotation(provider, apiKey) { singleKey ->
        when (provider.type) {
            ProviderType.GEMINI -> gemini.submitVideo(provider, singleKey, modelId, prompt, aspectRatio)
            ProviderType.OPENAI_COMPATIBLE -> openAi.submitVideo(provider, singleKey, modelId, prompt, aspectRatio)
            ProviderType.ANTHROPIC -> throw AiException(
                AiErrorCategory.UNSUPPORTED_MODEL,
                "Anthropic does not support video generation.",
                providerId = provider.id
            )
        }
    }

    suspend fun pollVideo(
        provider: ProviderConfig,
        apiKey: String,
        operationName: String
    ): VideoJobStatus = executeWithKeyRotation(provider, apiKey) { singleKey ->
        when (provider.type) {
            ProviderType.GEMINI -> gemini.pollVideo(provider, singleKey, operationName)
            ProviderType.OPENAI_COMPATIBLE -> openAi.pollVideo(provider, singleKey, operationName)
            ProviderType.ANTHROPIC -> VideoJobStatus(done = true, errorMessage = "Unsupported provider for video.")
        }
    }

    suspend fun downloadFile(
        url: String,
        provider: ProviderConfig,
        apiKey: String,
        destinationFile: File
    ): File = withContext(Dispatchers.IO) {
        val primaryKey = orderedCandidates(provider.id, apiKey).first().second
        val builder = Request.Builder().url(url).get()
        if (primaryKey.isNotBlank()) {
            when (provider.type) {
                ProviderType.GEMINI -> builder.addHeader("x-goog-api-key", primaryKey)
                ProviderType.OPENAI_COMPATIBLE -> builder.addHeader("Authorization", "Bearer $primaryKey")
                ProviderType.ANTHROPIC -> builder.addHeader("x-api-key", primaryKey)
            }
        }
        try {
            okHttpClient.newCall(builder.build()).execute().use { res ->
                if (!res.isSuccessful) {
                    throw AiErrorMapper.fromHttpStatus(res.code, "", provider.name, provider.id)
                }
                val body = res.body ?: throw AiException(
                    AiErrorCategory.NETWORK_PROXY,
                    "Empty media download response."
                )
                destinationFile.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
                destinationFile
            }
        } catch (t: Throwable) {
            throw AiErrorMapper.fromThrowable(t, provider.name, provider.id)
        }
    }
}
