package com.example

import android.app.Application
import com.example.data.local.AllChatDatabase
import com.example.data.local.ApiKeyVault
import com.example.data.remote.AiRouter
import com.example.data.remote.ResilientNetworkDns
import com.example.data.remote.WebSearchClient
import com.example.data.repository.AllChatRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AllChatApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: AllChatRepository
        private set

    lateinit var okHttpClient: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)

        okHttpClient = OkHttpClient.Builder()
            .dns(ResilientNetworkDns(applicationContext))
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val database = AllChatDatabase.getInstance(applicationContext)
        val vault = ApiKeyVault(applicationContext)
        val router = AiRouter(okHttpClient)
        val webSearchClient = WebSearchClient(okHttpClient)

        repository = AllChatRepository(
            context = applicationContext,
            providerDao = database.providerDao(),
            modelDao = database.modelDao(),
            personaDao = database.personaDao(),
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            knowledgeDocDao = database.knowledgeDocDao(),
            generatedMediaDao = database.generatedMediaDao(),
            userMemoryDao = database.userMemoryDao(),
            apiKeyVault = vault,
            aiRouter = router,
            webSearchClient = webSearchClient,
            okHttpClient = okHttpClient
        )
    }
}
