package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY createdAt ASC")
    fun observeProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getById(id: Long): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: ProviderEntity): Long

    @Update
    suspend fun update(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY displayName ASC")
    fun observeAllModels(): Flow<List<ModelEntity>>

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

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM models WHERE providerId = :providerId")
    suspend fun deleteByProviderId(providerId: Long)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

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

    @Query(
        "UPDATE conversations SET totalPromptTokens = totalPromptTokens + :promptTokens, " +
            "totalCompletionTokens = totalCompletionTokens + :completionTokens, " +
            "updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun addTokenUsage(
        id: Long,
        promptTokens: Int,
        completionTokens: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Query(
        "UPDATE messages SET content = :content, promptTokens = :promptTokens, " +
            "completionTokens = :completionTokens, isError = :isError, " +
            "suggestProxySetting = :suggestProxySetting, providerIdForError = :providerIdForError " +
            "WHERE id = :id"
    )
    suspend fun updateStreamingMessage(
        id: Long,
        content: String,
        promptTokens: Int?,
        completionTokens: Int?,
        isError: Boolean = false,
        suggestProxySetting: Boolean = false,
        providerIdForError: Long? = null
    )

    @Query("DELETE FROM messages WHERE id = :id")
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
