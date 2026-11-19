package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProviderEntity::class,
        ModelEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        GeneratedMediaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AllChatDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun modelDao(): ModelDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun generatedMediaDao(): GeneratedMediaDao

    companion object {
        @Volatile
        private var INSTANCE: AllChatDatabase? = null

        fun getInstance(context: Context): AllChatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AllChatDatabase::class.java,
                    "allchat_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
