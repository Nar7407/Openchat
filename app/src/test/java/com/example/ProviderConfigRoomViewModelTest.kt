package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AllChatDatabase
import com.example.data.local.ApiKeyVault
import com.example.data.repository.ProviderConfigRepository
import com.example.domain.model.ProviderType
import com.example.ui.viewmodel.ProviderConfigUiState
import com.example.ui.viewmodel.ProviderConfigViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProviderConfigRoomViewModelTest {

    private lateinit var context: Context
    private lateinit var database: AllChatDatabase
    private lateinit var vault: ApiKeyVault
    private lateinit var repository: ProviderConfigRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AllChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vault = ApiKeyVault(context)
        repository = ProviderConfigRepository(
            providerDao = database.providerDao(),
            modelDao = database.modelDao(),
            apiKeyVault = vault,
            aiRouter = null
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `room schema stores provider base url, encrypted api key, and relational model list`() = runBlocking {
        val rawSecret = "sk-test-super-secret-key-98765"
        val providerId = repository.upsertProvider(
            name = "My Custom Gateway",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.custom-llm.dev/v1/v1/",
            proxyUrl = "https://proxy.custom-llm.dev/v1/",
            apiKey = rawSecret,
            defaultModelId = "deepseek-r1",
            isEnabled = true,
            modelIds = listOf("deepseek-r1", "gpt-4o-mini", "llama-3.3-70b")
        )

        // Verify raw SQLite entity never stores the API key in plaintext
        val rawEntity = database.providerDao().getById(providerId)
        assertNotNull(rawEntity)
        assertEquals("https://api.custom-llm.dev/v1", rawEntity!!.baseUrl)
        assertEquals("https://proxy.custom-llm.dev/v1", rawEntity.proxyUrl)
        assertNotNull(rawEntity.encryptedApiKey)
        assertNotEquals(rawSecret, rawEntity.encryptedApiKey)

        // Verify decrypted round-trip returns the exact secret
        assertEquals(rawSecret, repository.getDecryptedApiKey(providerId))

        // Verify @Transaction Room relation returns provider + sorted models
        val relation = database.providerDao().getProviderWithModels(providerId)
        assertNotNull(relation)
        assertEquals(3, relation!!.models.size)
        assertEquals("deepseek-r1", relation.provider.defaultModelId)

        // Verify cascade delete removes all child models
        repository.deleteProvider(providerId)
        assertNull(database.providerDao().getById(providerId))
        assertTrue(database.modelDao().getByProviderId(providerId).isEmpty())
    }

    @Test
    fun `repository supports toggle enabled, model replacement, and duplicate`() = runBlocking {
        val id = repository.upsertProvider(
            name = "Local Ollama",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "http://10.0.2.2:11434/v1",
            apiKey = "",
            modelIds = listOf("qwen2.5:14b", "llama3.2:latest")
        )

        repository.setProviderEnabled(id, false)
        val enabledList = repository.enabledProvidersFlow.first()
        assertTrue(enabledList.none { it.id == id })

        repository.replaceModels(id, listOf("qwen2.5:14b", "mistral-nemo"))
        val updatedModels = database.modelDao().getByProviderId(id).map { it.modelId }
        assertEquals(setOf("qwen2.5:14b", "mistral-nemo"), updatedModels.toSet())

        val copyId = repository.duplicateProvider(id)
        assertNotNull(copyId)
        val copied = database.providerDao().getProviderWithModels(copyId!!)
        assertNotNull(copied)
        assertEquals("Local Ollama (Copy)", copied!!.provider.name)
        assertEquals(2, copied.models.size)
    }

    @Test
    fun `viewModel validates form inputs and parses batch model lists`() {
        val viewModel = ProviderConfigViewModel(repository)
        viewModel.startCreateProvider(ProviderType.OPENAI_COMPATIBLE)
        viewModel.updateFormName("")
        viewModel.updateFormBaseUrl("not-a-valid-url")
        viewModel.updateFormProxyUrl("ftp://invalid-proxy")
        viewModel.saveForm()

        val invalidForm = viewModel.formState.value
        assertNotNull(invalidForm.nameError)
        assertNotNull(invalidForm.baseUrlError)
        assertNotNull(invalidForm.proxyUrlError)
        assertTrue(invalidForm.isOpen)

        viewModel.addDraftModelId("gpt-4o, claude-sonnet-4; deepseek-r1\ngpt-4o")
        assertEquals(listOf("gpt-4o", "claude-sonnet-4", "deepseek-r1"), viewModel.formState.value.modelIds)
        assertEquals("gpt-4o", viewModel.formState.value.defaultModelId)

        viewModel.removeDraftModelId("gpt-4o")
        assertEquals(listOf("claude-sonnet-4", "deepseek-r1"), viewModel.formState.value.modelIds)
        assertEquals("claude-sonnet-4", viewModel.formState.value.defaultModelId)
        assertFalse(viewModel.uiState.value is ProviderConfigUiState.Error)
    }
}
