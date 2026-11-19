package com.example

import android.app.Application
import com.example.data.local.AllChatDatabase
import com.example.data.local.ApiKeyVault
import com.example.data.remote.AiRouter
import com.example.data.repository.AllChatRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AllChatApplication : Application() {

    lateinit var repository: AllChatRepository
        private set

    lateinit var okHttpClient: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val database = AllChatDatabase.getInstance(applicationContext)
        val vault = ApiKeyVault(applicationContext)
        val router = AiRouter(okHttpClient)

        repository = AllChatRepository(
            context = applicationContext,
            providerDao = database.providerDao(),
            modelDao = database.modelDao(),
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            generatedMediaDao = database.generatedMediaDao(),
            apiKeyVault = vault,
            aiRouter = router,
            okHttpClient = okHttpClient
        )
    }
}
