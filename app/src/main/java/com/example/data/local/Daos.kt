package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY createdAt ASC")
    fun observeProviders(): Flow<List<ProviderEntity>>

    @Transaction
    @Query("SELECT * FROM providers ORDER BY createdAt ASC")
    fun observeProvidersWithModels(): Flow<List<ProviderWithModels>>

    @Transaction
    @Query("SELECT * FROM providers WHERE isEnabled = 1 ORDER BY createdAt ASC")
    fun observeEnabledProvidersWithModels(): Flow<List<ProviderWithModels>>

    @Transaction
    @Query("SELECT * FROM providers WHERE id = :id")
    fun observeProviderWithModels(id: Long): Flow<ProviderWithModels?>

    @Transaction
    @Query(
        "SELECT * FROM providers " +
            "WHERE name LIKE '%' || :query || '%' OR baseUrl LIKE '%' || :query || '%' OR type LIKE '%' || :query || '%' " +
            "ORDER BY updatedAt DESC, createdAt ASC"
    )
    fun searchProvidersWithModels(query: String): Flow<List<ProviderWithModels>>

    @Transaction
    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getProviderWithModels(id: Long): ProviderWithModels?

    @Query("SELECT * FROM providers ORDER BY createdAt ASC")
    suspend fun getAll(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getById(id: Long): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: ProviderEntity): Long

    @Update
    suspend fun update(provider: ProviderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModelsForProvider(models: List<ModelEntity>)

    @Query("DELETE FROM models WHERE providerId = :providerId")
    suspend fun deleteModelsForProvider(providerId: Long)

    @Transaction
    suspend fun upsertProviderWithModels(
        provider: ProviderEntity,
        isUpdate: Boolean,
        models: List<ModelEntity>?
    ): Long {
        val providerId = if (isUpdate) {
            update(provider)
            provider.id
        } else {
            insert(provider)
        }
        if (models != null) {
            deleteModelsForProvider(providerId)
            if (models.isNotEmpty()) {
                insertModelsForProvider(models.map { it.copy(providerId = providerId) })
            }
        }
        return providerId
    }

    @Query("UPDATE providers SET encryptedApiKey = :encryptedApiKey, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEncryptedApiKey(
        id: Long,
        encryptedApiKey: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE providers SET isEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(
        id: Long,
        enabled: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE providers SET defaultModelId = :modelId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDefaultModelId(
        id: Long,
        modelId: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM providers")
    suspend fun deleteAll()
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY displayName ASC")
    fun observeAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY displayName ASC")
    fun observeByProviderId(providerId: Long): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models ORDER BY displayName ASC")
    suspend fun getAll(): List<ModelEntity>

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY displayName ASC")
    suspend fun getByProviderId(providerId: Long): List<ModelEntity>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getById(id: Long): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: ModelEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<ModelEntity>)

    @Update
    suspend fun update(model: ModelEntity)

    @Transaction
    suspend fun replaceModelsForProvider(providerId: Long, models: List<ModelEntity>) {
        deleteByProviderId(providerId)
        if (models.isNotEmpty()) {
            insertAll(models.map { it.copy(providerId = providerId) })
        }
    }

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM models WHERE providerId = :providerId")
    suspend fun deleteByProviderId(providerId: Long)

    @Query("DELETE FROM models")
    suspend fun deleteAll()
}

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY handle ASC")
    fun observePersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas ORDER BY handle ASC")
    suspend fun getAll(): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getById(id: Long): PersonaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(persona: PersonaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(personas: List<PersonaEntity>)

    @Update
    suspend fun update(persona: PersonaEntity)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM personas")
    suspend fun deleteAll()
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeConversation(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET selectedModelDbId = :modelDbId WHERE id = :id")
    suspend fun updateSelectedModel(id: Long, modelDbId: Long?)

    @Query("UPDATE conversations SET activePersonaId = :personaId WHERE id = :id")
    suspend fun updateActivePersona(id: Long, personaId: Long?)

    @Query(
        "UPDATE conversations SET totalPromptTokens = totalPromptTokens + :promptTokens, " +
            "totalCompletionTokens = totalCompletionTokens + :completionTokens, " +
            "estimatedCostUsd = estimatedCostUsd + :costUsd, " +
            "updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun addTokenUsage(
        id: Long,
        promptTokens: Int,
        completionTokens: Int,
        costUsd: Double = 0.0,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query(
        "UPDATE messages SET content = :content, promptTokens = :promptTokens, " +
            "completionTokens = :completionTokens, estimatedCostUsd = :estimatedCostUsd, " +
            "isError = :isError, suggestProxySetting = :suggestProxySetting, " +
            "providerIdForError = :providerIdForError WHERE id = :id"
    )
    suspend fun updateStreamingMessage(
        id: Long,
        content: String,
        promptTokens: Int?,
        completionTokens: Int?,
        estimatedCostUsd: Double = 0.0,
        isError: Boolean = false,
        suggestProxySetting: Boolean = false,
        providerIdForError: Long? = null
    )

    @Query(
        "UPDATE messages SET citationsJson = :citationsJson, thinkingDurationSec = :thinkingDurationSec, " +
            "rawRequestJson = :rawRequestJson, rawResponseJson = :rawResponseJson WHERE id = :id"
    )
    suspend fun updateMessageExtras(
        id: Long,
        citationsJson: String?,
        thinkingDurationSec: Int?,
        rawRequestJson: String?,
        rawResponseJson: String?
    )

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface KnowledgeDocDao {
    @Query("SELECT * FROM knowledge_docs ORDER BY createdAt DESC")
    fun observeDocs(): Flow<List<KnowledgeDocEntity>>

    @Query("SELECT * FROM knowledge_docs ORDER BY createdAt DESC")
    suspend fun getAll(): List<KnowledgeDocEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: KnowledgeDocEntity): Long

    @Query("UPDATE knowledge_docs SET slug = :slug, title = :title WHERE id = :id")
    suspend fun rename(id: Long, slug: String, title: String)

    @Query("DELETE FROM knowledge_docs WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface GeneratedMediaDao {
    @Query("SELECT * FROM generated_media WHERE mediaType = :mediaType ORDER BY createdAt DESC")
    fun observeByType(mediaType: String): Flow<List<GeneratedMediaEntity>>

    @Query("SELECT * FROM generated_media WHERE id = :id")
    suspend fun getById(id: Long): GeneratedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GeneratedMediaEntity): Long

    @Update
    suspend fun update(entity: GeneratedMediaEntity)

    @Query("DELETE FROM generated_media WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface UserMemoryDao {
    @Query("SELECT * FROM user_memories ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UserMemoryEntity>>

    @Query("SELECT * FROM user_memories WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabled(): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories ORDER BY createdAt DESC")
    suspend fun getAll(): List<UserMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: UserMemoryEntity): Long

    @Update
    suspend fun update(memory: UserMemoryEntity)

    @Query("UPDATE user_memories SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM user_memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM user_memories")
    suspend fun deleteAll()
}

