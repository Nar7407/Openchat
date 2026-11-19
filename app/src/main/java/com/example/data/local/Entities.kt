package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ModelInfo
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
    val supportsSummarization: Boolean = false
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
        supportsSummarization = supportsSummarization
    )
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val selectedModelDbId: Long? = null,
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
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
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val attachmentUri: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val modelLabel: String? = null,
    val isError: Boolean = false,
    val suggestProxySetting: Boolean = false,
    val providerIdForError: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

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
