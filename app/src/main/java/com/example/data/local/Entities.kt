package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.domain.model.ModelInfo
import com.example.domain.model.PersonaInfo
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType

@Entity(
    tableName = "providers",
    indices = [Index("name")]
)
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val baseUrl: String,
    val proxyUrl: String? = null,
    val encryptedApiKey: String? = null,
    val defaultModelId: String? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
) {
    fun toDomain(hasApiKey: Boolean, models: List<ModelInfo>): ProviderConfig {
        val parsedType = runCatching { ProviderType.valueOf(type) }.getOrDefault(ProviderType.OPENAI_COMPATIBLE)
        return ProviderConfig(
            id = id,
            name = name,
            type = parsedType,
            baseUrl = baseUrl,
            proxyUrl = proxyUrl,
            hasApiKey = hasApiKey || !encryptedApiKey.isNullOrBlank(),
            models = models,
            createdAt = createdAt,
            defaultModelId = defaultModelId,
            isEnabled = isEnabled,
            updatedAt = updatedAt
        )
    }
}

@Entity(
    tableName = "models",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("providerId"), Index(value = ["providerId", "modelId"], unique = true)]
)
data class ModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val modelId: String,
    val displayName: String,
    val supportsTextChat: Boolean = true,
    val supportsImageInput: Boolean = false,
    val supportsImageGen: Boolean = false,
    val supportsVideoGen: Boolean = false,
    val supportsSummarization: Boolean = false,
    val inputPricePerMillion: Double = 0.0,
    val outputPricePerMillion: Double = 0.0
) {
    fun toDomain(): ModelInfo = ModelInfo(
        id = id,
        providerId = providerId,
        modelId = modelId,
        displayName = displayName,
        supportsTextChat = supportsTextChat,
        supportsImageInput = supportsImageInput,
        supportsImageGen = supportsImageGen,
        supportsVideoGen = supportsVideoGen,
        supportsSummarization = supportsSummarization,
        inputPricePerMillion = inputPricePerMillion,
        outputPricePerMillion = outputPricePerMillion
    )
}

/**
 * One-to-many Room relation pairing a custom AI [ProviderEntity] with its persisted [ModelEntity] list.
 */
data class ProviderWithModels(
    @Embedded val provider: ProviderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "providerId"
    )
    val models: List<ModelEntity>
) {
    fun toDomain(hasVaultKey: Boolean = false): ProviderConfig = provider.toDomain(
        hasApiKey = hasVaultKey || !provider.encryptedApiKey.isNullOrBlank(),
        models = models.sortedBy { it.displayName.lowercase() }.map { it.toDomain() }
    )
}

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val handle: String,
    val name: String,
    val systemPrompt: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val defaultModelDbId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): PersonaInfo = PersonaInfo(
        id = id,
        handle = handle,
        name = name,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        defaultModelDbId = defaultModelDbId
    )
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val selectedModelDbId: Long? = null,
    val activePersonaId: Long? = null,
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val estimatedCostUsd: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index("parentMessageId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val parentMessageId: Long? = null,
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val attachmentUri: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val estimatedCostUsd: Double = 0.0,
    val modelLabel: String? = null,
    val isError: Boolean = false,
    val suggestProxySetting: Boolean = false,
    val providerIdForError: Long? = null,
    val citationsJson: String? = null,
    val thinkingDurationSec: Int? = null,
    val rawRequestJson: String? = null,
    val rawResponseJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "knowledge_docs")
data class KnowledgeDocEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
    val title: String,
    val fileName: String,
    val content: String,
    val chunkCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain() = com.example.domain.model.KnowledgeDocInfo(
        id = id,
        slug = slug,
        title = title,
        fileName = fileName,
        content = content,
        chunkCount = chunkCount,
        createdAt = createdAt
    )
}

@Entity(tableName = "generated_media")
data class GeneratedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: String, // "IMAGE" or "VIDEO"
    val prompt: String,
    val modelDbId: Long,
    val modelLabel: String,
    val providerId: Long,
    val localFilePath: String? = null,
    val remoteUri: String? = null,
    val status: String, // "PENDING", "POLLING", "COMPLETED", "FAILED"
    val operationName: String? = null,
    val aspectRatio: String = "1:1",
    val errorMessage: String? = null,
    val suggestProxySetting: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fact: String,
    val category: String = "Preference",
    val isEnabled: Boolean = true,
    val source: String = "Manual", // "Manual" or "Auto"
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain() = com.example.domain.model.UserMemoryInfo(
        id = id,
        fact = fact,
        category = category,
        isEnabled = isEnabled,
        source = source,
        createdAt = createdAt
    )
}

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerPhrase: String,
    val stepsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawTranscript: String,
    val markdownContent: String,
    val tagsCsv: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain() = com.example.domain.model.VoiceNoteInfo(
        id = id,
        title = title,
        rawTranscript = rawTranscript,
        markdownContent = markdownContent,
        tags = tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val dateIso: String,
    val category: String,
    val amount: Double,
    val currency: String = "INR",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain() = com.example.domain.model.ExpenseRecord(
        id = id,
        merchant = merchant,
        dateIso = dateIso,
        category = category,
        amount = amount,
        currency = currency,
        notes = notes,
        createdAt = createdAt
    )
}

@Entity(tableName = "watchlist_items")
data class WatchlistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val queryOrUrl: String,
    val lastSummary: String = "",
    val lastDiffMarkdown: String = "",
    val lastCheckedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain() = com.example.domain.model.WatchlistItemInfo(
        id = id,
        title = title,
        queryOrUrl = queryOrUrl,
        lastSummary = lastSummary,
        lastDiffMarkdown = lastDiffMarkdown,
        lastCheckedAt = lastCheckedAt,
        createdAt = createdAt
    )
}

@Entity(tableName = "webhook_actions")
data class WebhookActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerPhrase: String,
    val method: String = "POST",
    val url: String,
    val headersJson: String = "",
    val bodyTemplate: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain() = com.example.domain.model.WebhookActionInfo(
        id = id,
        name = name,
        triggerPhrase = triggerPhrase,
        method = method,
        url = url,
        headersJson = headersJson,
        bodyTemplate = bodyTemplate,
        createdAt = createdAt
    )
}

@Entity(tableName = "canvas_versions")
data class CanvasVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docTitle: String,
    val content: String,
    val versionNumber: Int,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain() = com.example.domain.model.CanvasVersionInfo(
        id = id,
        docTitle = docTitle,
        content = content,
        versionNumber = versionNumber,
        createdAt = createdAt
    )
}

@Entity(tableName = "clipboard_items")
data class ClipboardItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val preview: String,
    val isUrl: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain() = com.example.domain.model.ClipboardSnippetInfo(
        id = id,
        content = content,
        preview = preview,
        isUrl = isUrl,
        createdAt = createdAt
    )
}



