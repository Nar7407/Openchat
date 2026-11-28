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
    val supportsSummarization: Boolean = false,
    val promptPriceUsd: Double? = null,
    val completionPriceUsd: Double? = null,
    val isExplicitFreeFlag: Boolean = false,
    val hasPricingMetadata: Boolean = false,
    val isFreeTierCatalog: Boolean = false
) {
    val isExplicitlyFree: Boolean
        get() = modelId.endsWith(":free", ignoreCase = true) ||
            modelId.equals("openrouter/free", ignoreCase = true) ||
            isExplicitFreeFlag ||
            (hasPricingMetadata && promptPriceUsd != null && completionPriceUsd != null &&
                promptPriceUsd == 0.0 && completionPriceUsd == 0.0)

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

data class FreeModelFilterResult(
    val visibleModels: List<FetchedModel>,
    val totalFetchedCount: Int,
    val freeOnlyApplied: Boolean,
    val zeroMatchWarning: String? = null
) {
    fun summaryBadge(): String {
        return if (freeOnlyApplied) {
            "Fetched ${visibleModels.size} free models (filtered from $totalFetchedCount total)"
        } else {
            "Fetched $totalFetchedCount models"
        }
    }
}

/**
 * Smart Free-Model Filter Logic during /models fetch:
 * 1. Explicit pricing / free suffix metadata (`:free`, `openrouter/free`, pricing.prompt==0 && pricing.completion==0, is_free/free).
 * 2. Free-tier-only providers (Groq, Google AI Studio / Gemini, Cerebras, Mistral free tier) when no per-model pricing exists — keeps chat/vision models (excluding audio-only whisper/tts) tagged [FREE TIER].
 * 3. Zero-Match Fallback Guard warning when paid-only endpoints (e.g. OpenAI, Anthropic) return 0 free models.
 */
object FreeModelFilterUtility {

    const val ZERO_MATCH_WARNING =
        "No \$0/free models detected for this provider — uncheck 'Only import free models' to load all models"

    fun isKnownFreeTierCatalogProvider(
        providerName: String,
        baseUrl: String,
        providerType: com.example.domain.model.ProviderType
    ): Boolean {
        if (providerType == com.example.domain.model.ProviderType.GEMINI) return true
        val combined = "${providerName.lowercase()} ${baseUrl.lowercase()}"
        return combined.contains("groq") ||
            combined.contains("generativelanguage.googleapis.com") ||
            combined.contains("gemini") ||
            combined.contains("google ai") ||
            combined.contains("cerebras") ||
            combined.contains("mistral")
    }

    fun applyFilter(
        allFetched: List<FetchedModel>,
        onlyFreeModels: Boolean,
        providerName: String,
        baseUrl: String,
        providerType: com.example.domain.model.ProviderType
    ): FreeModelFilterResult {
        if (!onlyFreeModels) {
            return FreeModelFilterResult(
                visibleModels = allFetched,
                totalFetchedCount = allFetched.size,
                freeOnlyApplied = false,
                zeroMatchWarning = null
            )
        }

        // Rule 1: Explicit pricing / free suffix metadata
        val explicitFree = allFetched.filter { it.isExplicitlyFree }
        if (explicitFree.isNotEmpty()) {
            return FreeModelFilterResult(
                visibleModels = explicitFree,
                totalFetchedCount = allFetched.size,
                freeOnlyApplied = true,
                zeroMatchWarning = null
            )
        }

        // Rule 2: Free-tier-only providers when /models payload contains no per-model pricing object
        val anyHasPricing = allFetched.any { it.hasPricingMetadata }
        if (!anyHasPricing && isKnownFreeTierCatalogProvider(providerName, baseUrl, providerType)) {
            val freeTierCatalog = allFetched
                .filter { model ->
                    val lower = model.modelId.lowercase()
                    val isAudioOnly = lower.contains("whisper") ||
                        lower.contains("tts") ||
                        lower.contains("playai-tts") ||
                        lower.contains("audio-preview")
                    !isAudioOnly && (model.supportsTextChat || model.supportsImageInput)
                }
                .map { it.copy(isFreeTierCatalog = true) }

            if (freeTierCatalog.isNotEmpty()) {
                return FreeModelFilterResult(
                    visibleModels = freeTierCatalog,
                    totalFetchedCount = allFetched.size,
                    freeOnlyApplied = true,
                    zeroMatchWarning = null
                )
            }
        }

        // Rule 3: Zero-Match Fallback Guard
        return FreeModelFilterResult(
            visibleModels = emptyList(),
            totalFetchedCount = allFetched.size,
            freeOnlyApplied = true,
            zeroMatchWarning = ZERO_MATCH_WARNING
        )
    }
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
