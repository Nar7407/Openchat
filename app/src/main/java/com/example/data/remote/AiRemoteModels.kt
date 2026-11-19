package com.example.data.remote

import com.example.domain.model.ModelInfo

data class ToolCallInvocation(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class ChatInputMessage(
    val role: String, // "USER", "ASSISTANT", or "TOOL"
    val text: String,
    val imageBase64: String? = null,
    val imageMimeType: String? = null,
    val toolCalls: List<ToolCallInvocation> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null
)

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class Usage(val promptTokens: Int, val completionTokens: Int) : StreamEvent
    data class DebugRequest(val endpointUrl: String, val requestJson: String) : StreamEvent
    data class RawResponseChunk(val rawJson: String) : StreamEvent
    data class GroundingCitations(val citations: List<com.example.domain.model.SearchCitation>) : StreamEvent
    data class ToolCallRequest(val calls: List<ToolCallInvocation>) : StreamEvent
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
