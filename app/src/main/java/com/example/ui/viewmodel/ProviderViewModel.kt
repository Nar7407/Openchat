package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProviderConfigRepository
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sealed UI state for screens observing the Room-backed AI provider list and mutation feedback.
 */
sealed interface ProviderListUiState {
    data object Loading : ProviderListUiState

    data class Success(
        val providers: List<ProviderConfig> = emptyList(),
        val statusMessage: String? = null,
        val errorMessage: String? = null
    ) : ProviderListUiState

    data class Error(val message: String) : ProviderListUiState
}

/**
 * ViewModel exposing a reactive [StateFlow] of custom AI provider configurations stored in Room
 * along with functions to add, update, and delete provider records and their model lists.
 */
class ProviderViewModel(
    private val repository: ProviderConfigRepository
) : ViewModel() {

    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    /**
     * Reactive list of all stored AI provider configurations (with their relational model lists)
     * backed by Room.
     */
    val providers: StateFlow<List<ProviderConfig>> = repository.providersFlow
        .catch { err ->
            errorMessage.value = err.message ?: "Failed to load providers from Room."
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Reactive list of currently enabled AI provider configurations backed by Room.
     */
    val enabledProviders: StateFlow<List<ProviderConfig>> = repository.enabledProvidersFlow
        .catch { err ->
            errorMessage.value = err.message ?: "Failed to load enabled providers from Room."
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Combined UI state exposing loading/success/error states plus user-facing mutation feedback.
     */
    val uiState: StateFlow<ProviderListUiState> = combine(
        repository.providersFlow,
        statusMessage,
        errorMessage
    ) { list, status, error ->
        ProviderListUiState.Success(
            providers = list,
            statusMessage = status,
            errorMessage = error
        ) as ProviderListUiState
    }.catch { err ->
        emit(ProviderListUiState.Error(err.message ?: "Failed to load provider configurations."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProviderListUiState.Loading
    )

    /**
     * Adds a new AI provider configuration to the Room database.
     * The [apiKey], if provided, is encrypted via AES-256-GCM before persisting.
     */
    fun addProvider(
        name: String,
        type: ProviderType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl: String = type.defaultBaseUrl,
        apiKey: String? = null,
        proxyUrl: String? = null,
        defaultModelId: String? = null,
        isEnabled: Boolean = true,
        modelIds: List<String> = emptyList(),
        onComplete: ((Result<Long>) -> Unit)? = null
    ) {
        val validationError = validateProviderInputs(name, baseUrl, proxyUrl)
        if (validationError != null) {
            errorMessage.value = validationError
            onComplete?.invoke(Result.failure(IllegalArgumentException(validationError)))
            return
        }

        val cleanedModels = cleanModelIds(modelIds)
        val resolvedDefault = defaultModelId?.trim()?.takeIf { it.isNotEmpty() } ?: cleanedModels.firstOrNull()

        viewModelScope.launch {
            val result = runCatching {
                repository.upsertProvider(
                    existingId = null,
                    name = name.trim(),
                    type = type,
                    baseUrl = baseUrl.trim(),
                    proxyUrl = proxyUrl?.trim()?.takeIf { it.isNotEmpty() },
                    apiKey = apiKey,
                    clearApiKey = false,
                    defaultModelId = resolvedDefault,
                    isEnabled = isEnabled,
                    modelIds = cleanedModels
                )
            }
            result.onSuccess {
                statusMessage.value = "Added provider \"${name.trim()}\"."
                errorMessage.value = null
            }.onFailure { err ->
                errorMessage.value = err.message ?: "Failed to add provider configuration."
            }
            onComplete?.invoke(result)
        }
    }

    /**
     * Convenience overload to add a new provider from a [ProviderConfig] domain object.
     */
    fun addProvider(
        provider: ProviderConfig,
        apiKey: String? = null,
        onComplete: ((Result<Long>) -> Unit)? = null
    ) {
        addProvider(
            name = provider.name,
            type = provider.type,
            baseUrl = provider.baseUrl,
            apiKey = apiKey,
            proxyUrl = provider.proxyUrl,
            defaultModelId = provider.defaultModelId,
            isEnabled = provider.isEnabled,
            modelIds = provider.models.map { it.modelId },
            onComplete = onComplete
        )
    }

    /**
     * Updates an existing AI provider configuration in the Room database.
     * Pass [apiKey] to replace the encrypted key, or [clearApiKey] = true to remove it;
     * leaving both blank/false preserves the existing encrypted key.
     * Pass [modelIds] to replace the provider's model list, or null to keep existing models unchanged.
     */
    fun updateProvider(
        providerId: Long,
        name: String,
        type: ProviderType,
        baseUrl: String,
        apiKey: String? = null,
        clearApiKey: Boolean = false,
        proxyUrl: String? = null,
        defaultModelId: String? = null,
        isEnabled: Boolean = true,
        modelIds: List<String>? = null,
        onComplete: ((Result<Long>) -> Unit)? = null
    ) {
        if (providerId <= 0L) {
            val err = IllegalArgumentException("Invalid provider ID for update: $providerId")
            errorMessage.value = err.message
            onComplete?.invoke(Result.failure(err))
            return
        }

        val validationError = validateProviderInputs(name, baseUrl, proxyUrl)
        if (validationError != null) {
            errorMessage.value = validationError
            onComplete?.invoke(Result.failure(IllegalArgumentException(validationError)))
            return
        }

        val cleanedModels = modelIds?.let(::cleanModelIds)
        val resolvedDefault = defaultModelId?.trim()?.takeIf { it.isNotEmpty() }
            ?: cleanedModels?.firstOrNull()

        viewModelScope.launch {
            val result = runCatching {
                repository.upsertProvider(
                    existingId = providerId,
                    name = name.trim(),
                    type = type,
                    baseUrl = baseUrl.trim(),
                    proxyUrl = proxyUrl?.trim()?.takeIf { it.isNotEmpty() },
                    apiKey = apiKey,
                    clearApiKey = clearApiKey,
                    defaultModelId = resolvedDefault,
                    isEnabled = isEnabled,
                    modelIds = cleanedModels
                )
            }
            result.onSuccess {
                statusMessage.value = "Updated provider \"${name.trim()}\"."
                errorMessage.value = null
            }.onFailure { err ->
                errorMessage.value = err.message ?: "Failed to update provider configuration."
            }
            onComplete?.invoke(result)
        }
    }

    /**
     * Convenience overload to update an existing provider using a [ProviderConfig] domain object.
     */
    fun updateProvider(
        provider: ProviderConfig,
        apiKey: String? = null,
        clearApiKey: Boolean = false,
        modelIds: List<String>? = provider.models.map { it.modelId },
        onComplete: ((Result<Long>) -> Unit)? = null
    ) {
        updateProvider(
            providerId = provider.id,
            name = provider.name,
            type = provider.type,
            baseUrl = provider.baseUrl,
            apiKey = apiKey,
            clearApiKey = clearApiKey,
            proxyUrl = provider.proxyUrl,
            defaultModelId = provider.defaultModelId,
            isEnabled = provider.isEnabled,
            modelIds = modelIds,
            onComplete = onComplete
        )
    }

    /**
     * Deletes a provider configuration (and cascades deletion to its stored models and API key)
     * from the Room database by its ID.
     */
    fun deleteProvider(
        providerId: Long,
        onComplete: ((Result<Unit>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = runCatching { repository.deleteProvider(providerId) }
            result.onSuccess {
                statusMessage.value = "Deleted provider configuration."
                errorMessage.value = null
            }.onFailure { err ->
                errorMessage.value = err.message ?: "Failed to delete provider configuration."
            }
            onComplete?.invoke(result)
        }
    }

    /**
     * Convenience overload to delete a provider configuration by [ProviderConfig].
     */
    fun deleteProvider(
        provider: ProviderConfig,
        onComplete: ((Result<Unit>) -> Unit)? = null
    ) {
        deleteProvider(provider.id, onComplete)
    }

    fun setProviderEnabled(providerId: Long, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setProviderEnabled(providerId, enabled) }
                .onSuccess {
                    statusMessage.value = if (enabled) "Provider enabled." else "Provider paused."
                    errorMessage.value = null
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Failed to update provider enabled state."
                }
        }
    }

    fun setDefaultModel(providerId: Long, modelId: String?) {
        viewModelScope.launch {
            runCatching { repository.setDefaultModel(providerId, modelId) }
                .onSuccess {
                    statusMessage.value = if (modelId.isNullOrBlank()) {
                        "Cleared default model."
                    } else {
                        "Default model set to ${modelId.trim()}."
                    }
                    errorMessage.value = null
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Failed to set default model."
                }
        }
    }

    fun updateProviderModels(providerId: Long, modelIds: List<String>) {
        val cleaned = cleanModelIds(modelIds)
        viewModelScope.launch {
            runCatching { repository.replaceModels(providerId, cleaned) }
                .onSuccess {
                    statusMessage.value = "Updated model list (${cleaned.size})."
                    errorMessage.value = null
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Failed to update model list."
                }
        }
    }

    fun addModelToProvider(providerId: Long, modelId: String, displayName: String? = null) {
        val cleanId = modelId.trim()
        if (cleanId.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.addModel(providerId, cleanId, displayName) }
                .onSuccess {
                    statusMessage.value = "Added model \"$cleanId\"."
                    errorMessage.value = null
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Failed to add model."
                }
        }
    }

    fun removeModelFromProvider(modelDbId: Long) {
        viewModelScope.launch {
            runCatching { repository.removeModel(modelDbId) }
                .onSuccess { errorMessage.value = null }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Failed to remove model."
                }
        }
    }

    fun duplicateProvider(providerId: Long, onComplete: ((Result<Long?>) -> Unit)? = null) {
        viewModelScope.launch {
            val result = runCatching { repository.duplicateProvider(providerId) }
            result.onSuccess { copyId ->
                if (copyId != null) statusMessage.value = "Duplicated provider configuration."
                errorMessage.value = null
            }.onFailure { err ->
                errorMessage.value = err.message ?: "Failed to duplicate provider."
            }
            onComplete?.invoke(result)
        }
    }

    fun loadDecryptedApiKey(providerId: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val key = runCatching { repository.getDecryptedApiKey(providerId) }.getOrDefault("")
            onResult(key)
        }
    }

    fun clearNotices() {
        statusMessage.value = null
        errorMessage.value = null
    }

    private fun validateProviderInputs(
        name: String,
        baseUrl: String,
        proxyUrl: String?
    ): String? {
        if (name.trim().isEmpty()) {
            return "Provider name is required."
        }
        if (!ProviderConfigRepository.isValidHttpUrl(baseUrl.trim())) {
            return "Enter a valid http:// or https:// base URL."
        }
        val cleanProxy = proxyUrl?.trim().orEmpty()
        if (cleanProxy.isNotEmpty() && !ProviderConfigRepository.isValidHttpUrl(cleanProxy)) {
            return "Proxy URL must start with http:// or https://."
        }
        return null
    }

    private fun cleanModelIds(modelIds: List<String>): List<String> =
        modelIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
}
