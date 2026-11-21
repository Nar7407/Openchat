package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AllChatDatabase
import com.example.data.local.ApiKeyVault
import com.example.data.repository.ProviderConfigRepository
import com.example.domain.model.ProviderType
import com.example.ui.viewmodel.ProviderListUiState
import com.example.ui.viewmodel.ProviderViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProviderViewModelTest {

    private lateinit var context: Context
    private lateinit var database: AllChatDatabase
    private lateinit var repository: ProviderConfigRepository
    private lateinit var viewModel: ProviderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AllChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProviderConfigRepository(
            providerDao = database.providerDao(),
            modelDao = database.modelDao(),
            apiKeyVault = ApiKeyVault(context),
            aiRouter = null
        )
        viewModel = ProviderViewModel(repository)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `providers StateFlow emits updates across add, update, and delete`() = runBlocking {
        val providersCollector = launch(Dispatchers.Default) { viewModel.providers.collect {} }
        val uiStateCollector = launch(Dispatchers.Default) { viewModel.uiState.collect {} }

        try {
            // 1. Add provider
            val addedIdDeferred = CompletableDeferred<Long>()
            viewModel.addProvider(
                name = "OpenRouter Custom",
                type = ProviderType.OPENAI_COMPATIBLE,
                baseUrl = "https://openrouter.ai/api/v1/",
                apiKey = "sk-or-secret-123",
                modelIds = listOf("openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet")
            ) { result ->
                addedIdDeferred.complete(result.getOrThrow())
            }
            val addedId = withTimeout(5_000) { addedIdDeferred.await() }

            val afterAdd = withTimeout(5_000) {
                viewModel.providers.first { list -> list.any { it.id == addedId } }
            }
            val created = afterAdd.first { it.id == addedId }
            assertEquals("OpenRouter Custom", created.name)
            assertEquals("https://openrouter.ai/api/v1", created.baseUrl)
            assertTrue(created.hasApiKey)
            assertEquals(2, created.models.size)
            assertEquals("openai/gpt-4o-mini", created.defaultModelId)

            // 2. Update provider via domain object overload
            val updatedDeferred = CompletableDeferred<Long>()
            viewModel.updateProvider(
                provider = created.copy(
                    name = "OpenRouter Production",
                    baseUrl = "https://openrouter.ai/api/v1",
                    isEnabled = false,
                    defaultModelId = "deepseek/deepseek-r1"
                ),
                modelIds = listOf("deepseek/deepseek-r1", "openai/gpt-4o")
            ) { result ->
                updatedDeferred.complete(result.getOrThrow())
            }
            withTimeout(5_000) { updatedDeferred.await() }

            val afterUpdate = withTimeout(5_000) {
                viewModel.providers.first { list ->
                    list.any { it.id == addedId && it.name == "OpenRouter Production" && !it.isEnabled }
                }
            }
            val updated = afterUpdate.first { it.id == addedId }
            assertEquals("OpenRouter Production", updated.name)
            assertFalse(updated.isEnabled)
            assertEquals("deepseek/deepseek-r1", updated.defaultModelId)
            assertEquals(setOf("deepseek/deepseek-r1", "openai/gpt-4o"), updated.models.map { it.modelId }.toSet())
            // Existing encrypted API key preserved when apiKey is null & clearApiKey is false
            assertEquals("sk-or-secret-123", repository.getDecryptedApiKey(addedId))

            // 3. Delete provider
            val deletedDeferred = CompletableDeferred<Unit>()
            viewModel.deleteProvider(updated) { result ->
                deletedDeferred.complete(result.getOrThrow())
            }
            withTimeout(5_000) { deletedDeferred.await() }

            val afterDelete = withTimeout(5_000) {
                viewModel.providers.first { list -> list.none { it.id == addedId } }
            }
            assertTrue(afterDelete.none { it.id == addedId })
            assertTrue(database.modelDao().getByProviderId(addedId).isEmpty())
        } finally {
            providersCollector.cancel()
            uiStateCollector.cancel()
        }
    }

    @Test
    fun `addProvider rejects invalid name or baseUrl and surfaces error message`() = runBlocking {
        val uiStateCollector = launch(Dispatchers.Default) { viewModel.uiState.collect {} }
        try {
            val resultDeferred = CompletableDeferred<Result<Long>>()
            viewModel.addProvider(
                name = "   ",
                type = ProviderType.OPENAI_COMPATIBLE,
                baseUrl = "invalid-url"
            ) { resultDeferred.complete(it) }

            val result = withTimeout(5_000) { resultDeferred.await() }
            assertTrue(result.isFailure)

            val state = withTimeout(5_000) {
                viewModel.uiState.first { it is ProviderListUiState.Success && it.errorMessage != null }
            } as ProviderListUiState.Success
            assertNotNull(state.errorMessage)
            assertTrue(viewModel.providers.value.isEmpty())
        } finally {
            uiStateCollector.cancel()
        }
    }
}
