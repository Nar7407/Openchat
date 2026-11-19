package com.example.data.remote

import com.example.domain.model.AiErrorCategory
import com.example.domain.model.AiErrorMapper
import com.example.domain.model.AiException
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class AiRouter(private val okHttpClient: OkHttpClient) {

    private val gemini = GeminiAdapter(okHttpClient)
    private val openAi = OpenAiCompatibleAdapter(okHttpClient)
    private val anthropic = AnthropicAdapter(okHttpClient)

    suspend fun testConnection(provider: ProviderConfig, apiKey: String): ConnectionTestResult =
        when (provider.type) {
            ProviderType.GEMINI -> gemini.testConnection(provider, apiKey)
            ProviderType.OPENAI_COMPATIBLE -> openAi.testConnection(provider, apiKey)
            ProviderType.ANTHROPIC -> anthropic.testConnection(provider, apiKey)
        }

    suspend fun fetchModels(provider: ProviderConfig, apiKey: String): List<FetchedModel> =
        when (provider.type) {
            ProviderType.GEMINI -> gemini.fetchModels(provider, apiKey)
            ProviderType.OPENAI_COMPATIBLE -> openAi.fetchModels(provider, apiKey)
            ProviderType.ANTHROPIC -> anthropic.fetchModels(provider, apiKey)
        }

    fun streamChat(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        messages: List<ChatInputMessage>
    ): Flow<StreamEvent> = when (provider.type) {
        ProviderType.GEMINI -> gemini.streamChat(provider, apiKey, modelId, messages)
        ProviderType.OPENAI_COMPATIBLE -> openAi.streamChat(provider, apiKey, modelId, messages)
        ProviderType.ANTHROPIC -> anthropic.streamChat(provider, apiKey, modelId, messages)
    }

    suspend fun generateImage(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        prompt: String,
        aspectRatio: String
    ): GeneratedImagePayload = when (provider.type) {
        ProviderType.GEMINI -> gemini.generateImage(provider, apiKey, modelId, prompt, aspectRatio)
        ProviderType.OPENAI_COMPATIBLE -> openAi.generateImage(provider, apiKey, modelId, prompt, aspectRatio)
        ProviderType.ANTHROPIC -> throw AiException(
            AiErrorCategory.UNSUPPORTED_MODEL,
            "Anthropic does not natively support image generation.",
            providerId = provider.id
        )
    }

    suspend fun submitVideo(
        provider: ProviderConfig,
        apiKey: String,
        modelId: String,
        prompt: String,
        aspectRatio: String
    ): VideoJobSubmission = when (provider.type) {
        ProviderType.GEMINI -> gemini.submitVideo(provider, apiKey, modelId, prompt, aspectRatio)
        ProviderType.OPENAI_COMPATIBLE -> openAi.submitVideo(provider, apiKey, modelId, prompt, aspectRatio)
        ProviderType.ANTHROPIC -> throw AiException(
            AiErrorCategory.UNSUPPORTED_MODEL,
            "Anthropic does not support video generation.",
            providerId = provider.id
        )
    }

    suspend fun pollVideo(
        provider: ProviderConfig,
        apiKey: String,
        operationName: String
    ): VideoJobStatus = when (provider.type) {
        ProviderType.GEMINI -> gemini.pollVideo(provider, apiKey, operationName)
        ProviderType.OPENAI_COMPATIBLE -> openAi.pollVideo(provider, apiKey, operationName)
        ProviderType.ANTHROPIC -> VideoJobStatus(done = true, errorMessage = "Unsupported provider for video.")
    }

    suspend fun downloadFile(
        url: String,
        provider: ProviderConfig,
        apiKey: String,
        destinationFile: File
    ): File = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) {
            when (provider.type) {
                ProviderType.GEMINI -> builder.addHeader("x-goog-api-key", apiKey)
                ProviderType.OPENAI_COMPATIBLE -> builder.addHeader("Authorization", "Bearer $apiKey")
                ProviderType.ANTHROPIC -> builder.addHeader("x-api-key", apiKey)
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
