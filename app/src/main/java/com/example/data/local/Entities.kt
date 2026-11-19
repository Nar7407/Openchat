package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ModelInfo
import com.example.domain.model.PersonaInfo
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val baseUrl: String,
    val proxyUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(hasApiKey: Boolean, models: List<ModelInfo>): ProviderConfig {
        val parsedType = runCatching { ProviderType.valueOf(type) }.getOrDefault(ProviderType.OPENAI_COMPATIBLE)
        return ProviderConfig(
            id = id,
            name = name,
            type = parsedType,
            baseUrl = baseUrl,
            proxyUrl = proxyUrl,
            hasApiKey = hasApiKey,
            models = models,
            createdAt = createdAt
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
    indices = [Index("providerId")]
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

