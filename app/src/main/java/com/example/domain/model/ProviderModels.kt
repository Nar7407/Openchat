package com.example.domain.model

import java.util.Locale

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

enum class DisplayCurrency(val code: String, val symbol: String, val ratePerUsd: Double) {
    USD("USD", "$", 1.0),
    INR("INR", "₹", 84.5)
}

object CostEstimator {
    fun calculateUsd(
        promptTokens: Int,
        completionTokens: Int,
        inputPricePerMillion: Double,
        outputPricePerMillion: Double
    ): Double {
        if (inputPricePerMillion <= 0.0 && outputPricePerMillion <= 0.0) return 0.0
        val inCost = (promptTokens.coerceAtLeast(0) / 1_000_000.0) * inputPricePerMillion.coerceAtLeast(0.0)
        val outCost = (completionTokens.coerceAtLeast(0) / 1_000_000.0) * outputPricePerMillion.coerceAtLeast(0.0)
        return inCost + outCost
    }

    fun formatCost(costUsd: Double, currency: DisplayCurrency): String {
        val converted = costUsd * currency.ratePerUsd
        return when {
            converted <= 0.0 -> "${currency.symbol}0.00"
            converted < 0.01 -> String.format(Locale.US, "%s%.4f", currency.symbol, converted)
            converted < 1.0 -> String.format(Locale.US, "%s%.3f", currency.symbol, converted)
            else -> String.format(Locale.US, "%s%.2f", currency.symbol, converted)
        }
    }
}

data class ChatOptions(
    val systemPrompt: String? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val jsonMode: Boolean = false,
    val enableGeminiGoogleSearch: Boolean = false,
    val enableTools: Boolean = false
)

enum class WebSearchProvider(val label: String, val websiteUrl: String) {
    GEMINI_NATIVE("Gemini Grounding (Native)", "https://aistudio.google.com/apikey"),
    TAVILY("Tavily Search", "https://app.tavily.com/home"),
    BRAVE("Brave Search", "https://api.search.brave.com/app/keys"),
    EXA("Exa Search", "https://dashboard.exa.ai/api-keys")
}

data class SearchCitation(
    val index: Int,
    val title: String,
    val url: String,
    val snippet: String = ""
)

data class CustomSlashCommand(
    val id: String,
    val trigger: String, // e.g. "/fix"
    val description: String,
    val template: String // e.g. "Fix grammar and make concise:"
) {
    fun expand(userInput: String): String {
        val trimmedInput = userInput.trim()
        return if (template.contains("{{input}}", ignoreCase = true)) {
            template.replace(Regex("\\{\\{input\\}\\}", RegexOption.IGNORE_CASE), trimmedInput)
        } else if (trimmedInput.isNotEmpty()) {
            "${template.trim()}\n\n$trimmedInput"
        } else {
            template.trim()
        }
    }
}

data class AutoRouterConfig(
    val fastChatModelId: Long? = null,
    val heavyCodingModelId: Long? = null,
    val visionModelId: Long? = null,
    val longContextModelId: Long? = null
)

data class KnowledgeDocInfo(
    val id: Long,
    val slug: String,
    val title: String,
    val fileName: String,
    val content: String,
    val chunkCount: Int,
    val createdAt: Long
) {
    val charCount: Int get() = content.length
}

data class RequestParamsOverride(
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val jsonMode: Boolean = false
) {
    val hasOverrides: Boolean
        get() = temperature != null || topP != null || maxTokens != null || jsonMode
    val hasCustomOverride: Boolean
        get() = hasOverrides
}

data class PersonaInfo(
    val id: Long = 0,
    val handle: String,
    val name: String,
    val systemPrompt: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val defaultModelDbId: Long? = null
)

data class ModelInfo(
    val id: Long,
    val providerId: Long,
    val modelId: String,
    val displayName: String,
    val supportsTextChat: Boolean,
    val supportsImageInput: Boolean,
    val supportsImageGen: Boolean,
    val supportsVideoGen: Boolean,
    val supportsSummarization: Boolean,
    val inputPricePerMillion: Double = 0.0,
    val outputPricePerMillion: Double = 0.0
) {
    val hasPricing: Boolean
        get() = inputPricePerMillion > 0.0 || outputPricePerMillion > 0.0

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
    val createdAt: Long,
    val defaultModelId: String? = null,
    val isEnabled: Boolean = true,
    val updatedAt: Long = createdAt
) {
    val effectiveBaseUrl: String
        get() {
            val validProxy = proxyUrl?.trim()?.takeIf {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true)
            }
            val raw = validProxy
                ?: baseUrl.trim().takeIf {
                    it.startsWith("http://", ignoreCase = true) ||
                        it.startsWith("https://", ignoreCase = true)
                }
                ?: type.defaultBaseUrl

            var normalized = raw.trimEnd('/')
            // Collapse accidental duplicate version segments like /v1/v1 or /v1beta/v1beta
            normalized = normalized
                .replace(Regex("""(/v1)+$""", RegexOption.IGNORE_CASE), "/v1")
                .replace(Regex("""(/v1beta)+$""", RegexOption.IGNORE_CASE), "/v1beta")
            if (type == ProviderType.ANTHROPIC && normalized.endsWith("/v1", ignoreCase = true)) {
                normalized = normalized.dropLast(3).trimEnd('/')
            }
            return normalized.ifBlank { type.defaultBaseUrl }
        }
}

data class SelectableModel(
    val model: ModelInfo,
    val provider: ProviderConfig
) {
    val label: String get() = "${model.displayName} · ${provider.name}"
}

sealed interface IncomingSharePayload {
    data class Text(val text: String) : IncomingSharePayload
    data class ImageUri(val uriString: String) : IncomingSharePayload
    data class DocumentUri(val uriString: String) : IncomingSharePayload
}

enum class IncomingAppAction {
    NEW_CHAT,
    VOICE_PROMPT,
    SUMMARIZE_CLIPBOARD,
    GENERATE_IMAGE,
    CAMERA_VISION,
    ASSISTANT_OVERLAY,
    LIVE_LENS_EXPLAIN,
    LIVE_LENS_TRANSLATE
}

enum class LiveLensMode(val label: String) {
    AUTO_EXPLAIN("AUTO-EXPLAIN"),
    LIVE_TRANSLATOR("LIVE TRANSLATOR")
}

data class UserMemoryInfo(
    val id: Long,
    val fact: String,
    val category: String = "Preference",
    val isEnabled: Boolean = true,
    val source: String = "Manual",
    val createdAt: Long = System.currentTimeMillis()
)

data class StudyCard(
    val front: String,
    val back: String
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class StudyPack(
    val title: String,
    val flashcards: List<StudyCard>,
    val quiz: List<QuizQuestion>
)

data class MindMapNode(
    val id: String,
    val label: String,
    val detail: String = "",
    val children: List<MindMapNode> = emptyList()
)

object ContextWindowEstimator {
    fun maxTokensFor(modelId: String?): Int {
        val m = modelId?.lowercase().orEmpty()
        return when {
            m.contains("gemini-2.5") || m.contains("gemini-2.0") || m.contains("gemini-1.5") -> 1_000_000
            m.contains("claude-3.5") || m.contains("claude-3.7") || m.contains("claude-4") || m.contains("claude-sonnet") || m.contains("claude-opus") -> 200_000
            m.contains("gpt-4.1") || m.contains("gpt-5") -> 1_000_000
            m.contains("gpt-4o") || m.contains("o1") || m.contains("o3") || m.contains("o4") -> 128_000
            m.contains("deepseek") || m.contains("llama-3.1") || m.contains("llama-3.2") || m.contains("llama-3.3") || m.contains("qwen") || m.contains("mistral-large") || m.contains("grok") -> 128_000
            m.contains("gemma") || m.contains("llama-3") -> 32_000
            else -> 64_000
        }
    }

    fun estimateTokens(textChars: Int, reportedPromptTokens: Int = 0, reportedCompletionTokens: Int = 0): Int {
        val heuristic = (textChars / 4).coerceAtLeast(0)
        val reported = (reportedPromptTokens + reportedCompletionTokens).coerceAtLeast(0)
        return maxOf(heuristic, reported)
    }

    fun formatCompact(tokens: Int): String = when {
        tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format(Locale.US, "%.1fk", tokens / 1_000.0)
        else -> tokens.toString()
    }
}

enum class RewritePreset(val label: String, val instruction: String) {
    POLITE_REPLY("Polite Reply", "Rewrite as a warm, polite, natural reply suitable for WhatsApp or chat."),
    FIX_GRAMMAR("Fix Grammar", "Fix grammar, spelling, and punctuation while preserving the original tone and meaning."),
    MAKE_SHORTER("Make Shorter", "Condense to the clearest, punchiest short message without losing key facts."),
    PROFESSIONAL_EMAIL("Professional Email", "Rewrite as a crisp, polished professional email with a clear subject line and sign-off."),
    LINKEDIN_POLISH("LinkedIn Polish", "Rewrite as an engaging, structured LinkedIn post or message with strong clarity and no fluff."),
    TRANSLATE_HINDI("→ Hindi", "Translate naturally into fluent, everyday Hindi (Devanagari script)."),
    TRANSLATE_MARATHI("→ Marathi", "Translate naturally into fluent, everyday Marathi (Devanagari script)."),
    TRANSLATE_SPANISH("→ Spanish", "Translate naturally into clear, idiomatic Spanish."),
    CASUAL_FRIENDLY("Casual & Friendly", "Rewrite in a relaxed, upbeat, friendly tone for messaging friends or teammates.")
}

data class CodeDebugResult(
    val rootCause: String,
    val severity: String,
    val explanation: String,
    val fixedCode: String,
    val patchSummary: String,
    val verificationSteps: List<String>
)

data class YouTubeStudyResult(
    val videoId: String,
    val canonicalUrl: String,
    val title: String,
    val author: String,
    val hasTimestampedCaptions: Boolean,
    val markdownNotes: String
)

data class ClipboardPromptHint(
    val hash: Int,
    val rawText: String,
    val preview: String,
    val kindLabel: String // "LINK", "ERROR TRACE", "TEXT"
)

data class QuickContact(
    val id: String = "",
    val name: String,
    val phoneNumber: String
)

data class CustomAppEntry(
    val id: String = "",
    val name: String,
    val packageName: String
)

data class LiveLensAnalysisResult(
    val modelLabel: String,
    val answer: String,
    val failoverNotice: String? = null
)



