package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.model.ProviderType

class AllChatRoomConverters {
    @TypeConverter
    fun fromProviderType(type: ProviderType): String = type.name

    @TypeConverter
    fun toProviderType(value: String): ProviderType =
        runCatching { ProviderType.valueOf(value) }.getOrDefault(ProviderType.OPENAI_COMPATIBLE)
}

@Database(
    entities = [
        ProviderEntity::class,
        ModelEntity::class,
        PersonaEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        KnowledgeDocEntity::class,
        GeneratedMediaEntity::class,
        UserMemoryEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(AllChatRoomConverters::class)
abstract class AllChatDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun modelDao(): ModelDao
    abstract fun personaDao(): PersonaDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun knowledgeDocDao(): KnowledgeDocDao
    abstract fun generatedMediaDao(): GeneratedMediaDao
    abstract fun userMemoryDao(): UserMemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AllChatDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE models ADD COLUMN inputPricePerMillion REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE models ADD COLUMN outputPricePerMillion REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN activePersonaId INTEGER")
                db.execSQL("ALTER TABLE conversations ADD COLUMN estimatedCostUsd REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE messages ADD COLUMN parentMessageId INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN estimatedCostUsd REAL NOT NULL DEFAULT 0.0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_parentMessageId ON messages(parentMessageId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS personas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        handle TEXT NOT NULL,
                        name TEXT NOT NULL,
                        systemPrompt TEXT NOT NULL,
                        temperature REAL NOT NULL DEFAULT 0.7,
                        maxTokens INTEGER NOT NULL DEFAULT 2048,
                        defaultModelDbId INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN citationsJson TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN thinkingDurationSec INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN rawRequestJson TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN rawResponseJson TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS knowledge_docs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        slug TEXT NOT NULL,
                        title TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        content TEXT NOT NULL,
                        chunkCount INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fact TEXT NOT NULL,
                        category TEXT NOT NULL DEFAULT 'Preference',
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        source TEXT NOT NULL DEFAULT 'Manual',
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE providers ADD COLUMN encryptedApiKey TEXT")
                db.execSQL("ALTER TABLE providers ADD COLUMN defaultModelId TEXT")
                db.execSQL("ALTER TABLE providers ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE providers ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE providers SET updatedAt = createdAt WHERE updatedAt = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_providers_name ON providers(name)")
                // Remove any duplicate (providerId, modelId) rows before creating the unique index
                db.execSQL(
                    """
                    DELETE FROM models
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM models GROUP BY providerId, modelId
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_models_providerId_modelId ON models(providerId, modelId)")
            }
        }

        fun getInstance(context: Context): AllChatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AllChatDatabase::class.java,
                    "allchat_local.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
