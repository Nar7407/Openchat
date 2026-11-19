package com.example.domain.model

enum class ProviderType(val label: String, val defaultBaseUrl: String) {
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    OPENAI_COMPATIBLE("OpenAI-Compatible", "https://api.openai.com/v1"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com")
}

enum class ProviderCategory(val label: String) {
    ALL("All"),
    POPULAR("Popular"),
    CLOUD_ENTERPRISE("Cloud & Enterprise"),
    CODING_PLANS("Coding Plans"),
    GATEWAYS("Gateways"),
    LOCAL("Local")
}

data class ProviderPreset(
    val id: String,
    val title: String,
    val type: ProviderType,
    val baseUrl: String,
    val category: ProviderCategory = ProviderCategory.CLOUD_ENTERPRISE,
    val isPopular: Boolean = false,
    val requiresApiKey: Boolean = true,
    val helperHint: String = ""
) {
    val hasUrlPlaceholders: Boolean
        get() = baseUrl.contains('<') && baseUrl.contains('>')
}

enum class ModelCapability(val label: String) {
    TEXT_CHAT("Text Chat"),
    IMAGE_INPUT("Image Input"),
    IMAGE_GENERATION("Image Gen"),
    VIDEO_GENERATION("Video Gen"),
    SUMMARIZATION("Summarize")
}

enum class SummaryLength(val label: String, val instruction: String) {
    SHORT("Short", "Provide a concise 3-4 sentence executive summary capturing only the essential takeaway."),
    MEDIUM("Medium", "Provide a well-structured summary in 2-3 short paragraphs with key bullet points."),
    DETAILED("Detailed", "Provide a thorough, comprehensive summary organized by sections with all important facts, arguments, and nuances.")
}

data class ModelInfo(
    val id: Long,
    val providerId: Long,
    val modelId: String,
    val displayName: String,
    val supportsTextChat: Boolean,
    val supportsImageInput: Boolean,
    val supportsImageGen: Boolean,
    val supportsVideoGen: Boolean,
    val supportsSummarization: Boolean
) {
    fun supports(capability: ModelCapability): Boolean = when (capability) {
        ModelCapability.TEXT_CHAT -> supportsTextChat
        ModelCapability.IMAGE_INPUT -> supportsImageInput
        ModelCapability.IMAGE_GENERATION -> supportsImageGen
        ModelCapability.VIDEO_GENERATION -> supportsVideoGen
        ModelCapability.SUMMARIZATION -> supportsSummarization
    }
}

data class ProviderConfig(
    val id: Long,
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val proxyUrl: String?,
    val hasApiKey: Boolean,
    val models: List<ModelInfo>,
    val createdAt: Long
) {
    val effectiveBaseUrl: String
        get() = proxyUrl?.trim()?.takeIf { it.isNotEmpty() } ?: baseUrl.trim()
}

data class SelectableModel(
    val model: ModelInfo,
    val provider: ProviderConfig
) {
    val label: String get() = "${model.displayName} · ${provider.name}"
}
