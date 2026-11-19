package com.example.data.remote

import com.example.domain.model.ModelInfo

data class ChatInputMessage(
    val role: String, // "USER" or "ASSISTANT"
    val text: String,
    val imageBase64: String? = null,
    val imageMimeType: String? = null
)

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class Usage(val promptTokens: Int, val completionTokens: Int) : StreamEvent
    data object Completed : StreamEvent
}

data class FetchedModel(
    val modelId: String,
    val displayName: String,
    val supportsTextChat: Boolean = true,
    val supportsImageInput: Boolean = false,
    val supportsImageGen: Boolean = false,
    val supportsVideoGen: Boolean = false,
    val supportsSummarization: Boolean = false
) {
    fun toModelInfo(providerId: Long): ModelInfo = ModelInfo(
        id = 0,
        providerId = providerId,
        modelId = modelId,
        displayName = displayName,
        supportsTextChat = supportsTextChat,
        supportsImageInput = supportsImageInput,
        supportsImageGen = supportsImageGen,
        supportsVideoGen = supportsVideoGen,
        supportsSummarization = supportsSummarization
    )
}

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val message: String,
    val suggestProxySetting: Boolean = false
)

data class GeneratedImagePayload(
    val bytes: ByteArray,
    val mimeType: String = "image/png"
)

data class VideoJobSubmission(
    val operationName: String,
    val immediateVideoUrl: String? = null
)

data class VideoJobStatus(
    val done: Boolean,
    val videoUrl: String? = null,
    val errorMessage: String? = null
)
