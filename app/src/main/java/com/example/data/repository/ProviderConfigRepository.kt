package com.example.data.repository

import com.example.data.local.ApiKeyVault
import com.example.data.local.ModelDao
import com.example.data.local.ModelEntity
import com.example.data.local.ProviderDao
import com.example.data.local.ProviderEntity
import com.example.data.remote.AiRouter
import com.example.data.remote.ConnectionTestResult
import com.example.domain.model.ModelCapability
import com.example.domain.model.ModelInfo
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * Dedicated Room-backed repository for managing custom AI provider configurations,
 * including base URLs, optional proxy URLs, encrypted API keys, and per-provider model lists.
 */
class ProviderConfigRepository(
    private val providerDao: ProviderDao,
    private val modelDao: ModelDao,
    private val apiKeyVault: ApiKeyVault,
    private val aiRouter: AiRouter? = null
) {

    val providersFlow: Flow<List<ProviderConfig>> = combine(
        providerDao.observeProvidersWithModels(),
        apiKeyVault.observeConfiguredProviderIds()
    ) { rows, vaultIds ->
        rows.map { row -> row.toDomain(hasVaultKey = row.provider.id in vaultIds) }
    }

    val enabledProvidersFlow: Flow<List<ProviderConfig>> = combine(
        providerDao.observeEnabledProvidersWithModels(),
        apiKeyVault.observeConfiguredProviderIds()
    ) { rows, vaultIds ->
        rows.map { row -> row.toDomain(hasVaultKey = row.provider.id in vaultIds) }
    }

    fun observeProvider(providerId: Long): Flow<ProviderConfig?> = combine(
        providerDao.observeProviderWithModels(providerId),
        apiKeyVault.observeConfiguredProviderIds()
    ) { row, vaultIds ->
        row?.toDomain(hasVaultKey = row.provider.id in vaultIds)
    }

    fun searchProviders(query: String): Flow<List<ProviderConfig>> {
        val clean = query.trim()
        if (clean.isEmpty()) return providersFlow
        return combine(
            providerDao.searchProvidersWithModels(clean),
            apiKeyVault.observeConfiguredProviderIds()
        ) { rows, vaultIds ->
            rows.map { row -> row.toDomain(hasVaultKey = row.provider.id in vaultIds) }
        }
    }

    /**
     * Inserts or updates a custom AI provider in Room, encrypting the API key with Android Keystore
     * AES-256-GCM before persisting, and optionally syncing its custom model list atomically.
     */
    suspend fun upsertProvider(
        existingId: Long? = null,
        name: String,
        type: ProviderType,
        baseUrl: String,
        proxyUrl: String? = null,
        apiKey: String? = null,
        clearApiKey: Boolean = false,
        defaultModelId: String? = null,
        isEnabled: Boolean = true,
        modelIds: List<String>? = null
    ): Long = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifEmpty {
            throw IllegalArgumentException("Provider name cannot be blank.")
        }
        val cleanBase = normalizeEndpointUrl(baseUrl.trim().ifEmpty { type.defaultBaseUrl })
        val cleanProxy = proxyUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizeEndpointUrl(it) }
        val cleanDefaultModel = defaultModelId?.trim()?.takeIf { it.isNotEmpty() }
        val now = System.currentTimeMillis()

        val existing = existingId?.takeIf { it > 0L }?.let { providerDao.getById(it) }

        val encryptedKey: String? = when {
            clearApiKey -> null
            !apiKey.isNullOrBlank() -> apiKeyVault.encryptSecret(apiKey.trim())
            existing != null -> existing.encryptedApiKey
            else -> null
        }

        val modelEntities: List<ModelEntity>? = modelIds?.let { ids ->
            val cleaned = ids
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
            val existingById = existing?.let {
                modelDao.getByProviderId(it.id).associateBy { m -> m.modelId.lowercase() }
            }.orEmpty()
            cleaned.map { rawId ->
                existingById[rawId.lowercase()] ?: ModelEntity(
                    providerId = existing?.id ?: 0L,
                    modelId = rawId,
                    displayName = rawId,
                    supportsTextChat = true,
                    supportsImageInput = rawId.contains("vision", ignoreCase = true) ||
                        rawId.contains("gpt-4o", ignoreCase = true) ||
                        rawId.contains("gemini", ignoreCase = true) ||
                        rawId.contains("claude-3", ignoreCase = true) ||
                        rawId.contains("claude-sonnet", ignoreCase = true),
                    supportsSummarization = true
                )
            }
        }

        val entityToWrite = if (existing == null) {
            ProviderEntity(
                name = cleanName,
                type = type.name,
                baseUrl = cleanBase,
                proxyUrl = cleanProxy,
                encryptedApiKey = encryptedKey,
                defaultModelId = cleanDefaultModel,
                isEnabled = isEnabled,
                createdAt = now,
                updatedAt = now
            )
        } else {
            existing.copy(
                name = cleanName,
                type = type.name,
                baseUrl = cleanBase,
                proxyUrl = cleanProxy,
                encryptedApiKey = encryptedKey,
                defaultModelId = cleanDefaultModel,
                isEnabled = isEnabled,
                updatedAt = now
            )
        }

        val providerId = providerDao.upsertProviderWithModels(
            provider = entityToWrite,
            isUpdate = existing != null,
            models = modelEntities
        )

        // Keep DataStore ApiKeyVault synchronized for legacy readers & vault backup compatibility
        when {
            clearApiKey -> apiKeyVault.deleteApiKey(providerId)
            !apiKey.isNullOrBlank() -> apiKeyVault.saveApiKey(providerId, apiKey.trim())
        }

        providerId
    }

    suspend fun getDecryptedApiKey(providerId: Long): String = withContext(Dispatchers.IO) {
        val fromRoom = providerDao.getById(providerId)?.encryptedApiKey
        if (!fromRoom.isNullOrBlank()) {
            val decrypted = apiKeyVault.decryptSecret(fromRoom)
            if (decrypted.isNotBlank()) return@withContext decrypted
        }
        apiKeyVault.getApiKey(providerId)
    }

    suspend fun setProviderEnabled(providerId: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        providerDao.setEnabled(providerId, enabled)
    }

    suspend fun setDefaultModel(providerId: Long, modelId: String?) = withContext(Dispatchers.IO) {
        providerDao.setDefaultModelId(providerId, modelId?.trim()?.takeIf { it.isNotEmpty() })
    }

    suspend fun duplicateProvider(providerId: Long): Long? = withContext(Dispatchers.IO) {
        val original = providerDao.getProviderWithModels(providerId) ?: return@withContext null
        val plainKey = getDecryptedApiKey(providerId)
        val now = System.currentTimeMillis()
        val copyId = providerDao.insert(
            original.provider.copy(
                id = 0,
                name = "${original.provider.name} (Copy)",
                encryptedApiKey = if (plainKey.isNotBlank()) apiKeyVault.encryptSecret(plainKey) else null,
                createdAt = now,
                updatedAt = now
            )
        )
        if (plainKey.isNotBlank()) {
            apiKeyVault.saveApiKey(copyId, plainKey)
        }
        if (original.models.isNotEmpty()) {
            modelDao.insertAll(original.models.map { it.copy(id = 0, providerId = copyId) })
        }
        copyId
    }

    suspend fun deleteProvider(providerId: Long) = withContext(Dispatchers.IO) {
        apiKeyVault.deleteApiKey(providerId)
        providerDao.deleteById(providerId)
    }

    // --- Model List Management ---

    suspend fun addModel(
        providerId: Long,
        modelId: String,
        displayName: String? = null,
        supportsImageInput: Boolean = false,
        supportsImageGen: Boolean = false,
        supportsVideoGen: Boolean = false,
        supportsSummarization: Boolean = true
    ): Long = withContext(Dispatchers.IO) {
        val cleanId = modelId.trim()
        require(cleanId.isNotEmpty()) { "Model ID cannot be blank." }
        val existing = modelDao.getByProviderId(providerId)
            .firstOrNull { it.modelId.equals(cleanId, ignoreCase = true) }
        if (existing != null) return@withContext existing.id

        val label = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: cleanId
        val isLikelyVision = supportsImageInput ||
            cleanId.contains("vision", ignoreCase = true) ||
            cleanId.contains("gpt-4o", ignoreCase = true) ||
            cleanId.contains("gemini", ignoreCase = true) ||
            cleanId.contains("claude-3", ignoreCase = true) ||
            cleanId.contains("claude-sonnet", ignoreCase = true) ||
            cleanId.contains("claude-opus", ignoreCase = true)

        modelDao.insert(
            ModelEntity(
                providerId = providerId,
                modelId = cleanId,
                displayName = label,
                supportsTextChat = true,
                supportsImageInput = isLikelyVision,
                supportsImageGen = supportsImageGen,
                supportsVideoGen = supportsVideoGen,
                supportsSummarization = supportsSummarization
            )
        )
    }

    /**
     * Atomically replaces a provider's stored model list while preserving existing capability flags
     * and pricing metadata for any model IDs that remain in the updated list.
     */
    suspend fun replaceModels(providerId: Long, modelIds: List<String>) = withContext(Dispatchers.IO) {
        val cleaned = modelIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

        val existingById = modelDao.getByProviderId(providerId)
            .associateBy { it.modelId.lowercase() }

        val entities = cleaned.map { rawId ->
            existingById[rawId.lowercase()] ?: ModelEntity(
                providerId = providerId,
                modelId = rawId,
                displayName = rawId,
                supportsTextChat = true,
                supportsImageInput = rawId.contains("vision", ignoreCase = true) ||
                    rawId.contains("gpt-4o", ignoreCase = true) ||
                    rawId.contains("gemini", ignoreCase = true) ||
                    rawId.contains("claude-3", ignoreCase = true) ||
                    rawId.contains("claude-sonnet", ignoreCase = true),
                supportsSummarization = true
            )
        }
        modelDao.replaceModelsForProvider(providerId, entities)
    }

    suspend fun toggleModelCapability(model: ModelInfo, capability: ModelCapability) = withContext(Dispatchers.IO) {
        val updated = when (capability) {
            ModelCapability.TEXT_CHAT -> model.copy(supportsTextChat = !model.supportsTextChat)
            ModelCapability.IMAGE_INPUT -> model.copy(supportsImageInput = !model.supportsImageInput)
            ModelCapability.IMAGE_GENERATION -> model.copy(supportsImageGen = !model.supportsImageGen)
            ModelCapability.VIDEO_GENERATION -> model.copy(supportsVideoGen = !model.supportsVideoGen)
            ModelCapability.SUMMARIZATION -> model.copy(supportsSummarization = !model.supportsSummarization)
        }
        modelDao.update(
            ModelEntity(
                id = updated.id,
                providerId = updated.providerId,
                modelId = updated.modelId,
                displayName = updated.displayName,
                supportsTextChat = updated.supportsTextChat,
                supportsImageInput = updated.supportsImageInput,
                supportsImageGen = updated.supportsImageGen,
                supportsVideoGen = updated.supportsVideoGen,
                supportsSummarization = updated.supportsSummarization,
                inputPricePerMillion = updated.inputPricePerMillion,
                outputPricePerMillion = updated.outputPricePerMillion
            )
        )
    }

    suspend fun removeModel(modelDbId: Long) = withContext(Dispatchers.IO) {
        modelDao.deleteById(modelDbId)
    }

    suspend fun fetchAndSyncRemoteModels(provider: ProviderConfig): List<ModelInfo> = withContext(Dispatchers.IO) {
        val router = aiRouter ?: throw IllegalStateException("Network router unavailable for remote model discovery.")
        val apiKey = getDecryptedApiKey(provider.id)
        val fetched = router.fetchModels(provider, apiKey)
        val existing = modelDao.getByProviderId(provider.id).associateBy { it.modelId }
        val merged = fetched.map { item ->
            existing[item.modelId]?.copy(displayName = item.displayName) ?: ModelEntity(
                providerId = provider.id,
                modelId = item.modelId,
                displayName = item.displayName,
                supportsTextChat = item.supportsTextChat,
                supportsImageInput = item.supportsImageInput,
                supportsImageGen = item.supportsImageGen,
                supportsVideoGen = item.supportsVideoGen,
                supportsSummarization = item.supportsSummarization
            )
        }
        if (merged.isNotEmpty()) {
            val fetchedIds = fetched.map { it.modelId }.toSet()
            val manualToKeep = existing.values.filter { it.modelId !in fetchedIds }
            modelDao.replaceModelsForProvider(provider.id, merged + manualToKeep)
        }
        modelDao.getByProviderId(provider.id).map { it.toDomain() }
    }

    suspend fun testConnection(provider: ProviderConfig): ConnectionTestResult = withContext(Dispatchers.IO) {
        val router = aiRouter ?: return@withContext ConnectionTestResult(
            success = false,
            message = "Network router unavailable.",
            latencyMs = 0L,
            suggestProxySetting = false
        )
        val apiKey = getDecryptedApiKey(provider.id)
        router.testConnection(provider, apiKey)
    }

    companion object {
        fun normalizeEndpointUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return trimmed
                .replace(Regex("""(/v1)+$""", RegexOption.IGNORE_CASE), "/v1")
                .replace(Regex("""(/v1beta)+$""", RegexOption.IGNORE_CASE), "/v1beta")
        }

        fun isValidHttpUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return false
            return (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)) &&
                !trimmed.contains(' ') &&
                trimmed.length >= 10
        }
    }
}
