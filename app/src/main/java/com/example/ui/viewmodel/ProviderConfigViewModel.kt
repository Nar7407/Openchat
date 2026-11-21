package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ConnectionTestResult
import com.example.data.repository.ProviderConfigRepository
import com.example.domain.model.ModelCapability
import com.example.domain.model.ModelInfo
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProviderConfigUiState {
    data object Loading : ProviderConfigUiState

    data class Success(
        val providers: List<ProviderConfig> = emptyList(),
        val totalProviderCount: Int = 0,
        val searchQuery: String = "",
        val selectedTypeFilter: ProviderType? = null,
        val testingProviderIds: Set<Long> = emptySet(),
        val fetchingProviderIds: Set<Long> = emptySet(),
        val connectionResults: Map<Long, ConnectionTestResult> = emptyMap(),
        val statusMessage: String? = null,
        val errorMessage: String? = null
    ) : ProviderConfigUiState

    data class Error(val message: String) : ProviderConfigUiState
}

data class ProviderConfigFormState(
    val isOpen: Boolean = false,
    val editingProviderId: Long? = null,
    val name: String = "",
    val type: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String = ProviderType.OPENAI_COMPATIBLE.defaultBaseUrl,
    val proxyUrl: String = "",
    val apiKey: String = "",
    val hasStoredApiKey: Boolean = false,
    val clearStoredApiKey: Boolean = false,
    val isEnabled: Boolean = true,
    val defaultModelId: String = "",
    val modelIds: List<String> = emptyList(),
    val modelInputDraft: String = "",
    val nameError: String? = null,
    val baseUrlError: String? = null,
    val proxyUrlError: String? = null,
    val isSaving: Boolean = false
) {
    val isEditing: Boolean get() = editingProviderId != null
}

/**
 * ViewModel for managing custom AI provider configurations stored in Room,
 * including base URLs, optional proxy URLs, encrypted API keys, and per-provider model lists.
 */
class ProviderConfigViewModel(
    private val repository: ProviderConfigRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedTypeFilter = MutableStateFlow<ProviderType?>(null)
    private val testingProviderIds = MutableStateFlow<Set<Long>>(emptySet())
    private val fetchingProviderIds = MutableStateFlow<Set<Long>>(emptySet())
    private val connectionResults = MutableStateFlow<Map<Long, ConnectionTestResult>>(emptyMap())
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val _formState = MutableStateFlow(ProviderConfigFormState())
    val formState: StateFlow<ProviderConfigFormState> = _formState.asStateFlow()

    private val filterBundle = combine(
        searchQuery,
        selectedTypeFilter,
        statusMessage,
        errorMessage
    ) { query, typeFilter, status, error ->
        FilterBundle(query, typeFilter, status, error)
    }

    private val networkBundle = combine(
        testingProviderIds,
        fetchingProviderIds,
        connectionResults
    ) { testing, fetching, results ->
        Triple(testing, fetching, results)
    }

    val uiState: StateFlow<ProviderConfigUiState> = combine(
        repository.providersFlow,
        filterBundle,
        networkBundle
    ) { allProviders, filters, (testing, fetching, results) ->
        val q = filters.query.trim()
        val filtered = allProviders.filter { provider ->
            val matchesType = filters.typeFilter == null || provider.type == filters.typeFilter
            val matchesQuery = q.isEmpty() ||
                provider.name.contains(q, ignoreCase = true) ||
                provider.baseUrl.contains(q, ignoreCase = true) ||
                provider.models.any {
                    it.modelId.contains(q, ignoreCase = true) ||
                        it.displayName.contains(q, ignoreCase = true)
                }
            matchesType && matchesQuery
        }
        ProviderConfigUiState.Success(
            providers = filtered,
            totalProviderCount = allProviders.size,
            searchQuery = filters.query,
            selectedTypeFilter = filters.typeFilter,
            testingProviderIds = testing,
            fetchingProviderIds = fetching,
            connectionResults = results,
            statusMessage = filters.status,
            errorMessage = filters.error
        ) as ProviderConfigUiState
    }.catch { err ->
        emit(ProviderConfigUiState.Error(err.message ?: "Failed to load AI provider configurations."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProviderConfigUiState.Loading
    )

    // --- Search & Filtering ---

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectTypeFilter(type: ProviderType?) {
        selectedTypeFilter.value = type
    }

    fun clearNotices() {
        statusMessage.value = null
        errorMessage.value = null
    }

    // --- Create / Edit Provider Form ---

    fun startCreateProvider(initialType: ProviderType = ProviderType.OPENAI_COMPATIBLE) {
        _formState.value = ProviderConfigFormState(
            isOpen = true,
            editingProviderId = null,
            type = initialType,
            baseUrl = initialType.defaultBaseUrl
        )
    }

    fun startEditProvider(provider: ProviderConfig) {
        viewModelScope.launch {
            val decryptedKey = runCatching { repository.getDecryptedApiKey(provider.id) }.getOrDefault("")
            _formState.value = ProviderConfigFormState(
                isOpen = true,
                editingProviderId = provider.id,
                name = provider.name,
                type = provider.type,
                baseUrl = provider.baseUrl,
                proxyUrl = provider.proxyUrl.orEmpty(),
                apiKey = decryptedKey,
                hasStoredApiKey = provider.hasApiKey,
                clearStoredApiKey = false,
                isEnabled = provider.isEnabled,
                defaultModelId = provider.defaultModelId.orEmpty(),
                modelIds = provider.models.map { it.modelId }
            )
        }
    }

    fun dismissForm() {
        _formState.value = ProviderConfigFormState(isOpen = false)
    }

    fun updateFormName(name: String) {
        _formState.update { it.copy(name = name, nameError = null) }
    }

    fun updateFormType(type: ProviderType) {
        _formState.update { current ->
            val shouldSwapBase = current.baseUrl.isBlank() || current.baseUrl == current.type.defaultBaseUrl
            current.copy(
                type = type,
                baseUrl = if (shouldSwapBase) type.defaultBaseUrl else current.baseUrl,
                baseUrlError = null
            )
        }
    }

    fun updateFormBaseUrl(baseUrl: String) {
        _formState.update { it.copy(baseUrl = baseUrl, baseUrlError = null) }
    }

    fun updateFormProxyUrl(proxyUrl: String) {
        _formState.update { it.copy(proxyUrl = proxyUrl, proxyUrlError = null) }
    }

    fun updateFormApiKey(apiKey: String) {
        _formState.update {
            it.copy(
                apiKey = apiKey,
                clearStoredApiKey = if (apiKey.isNotBlank()) false else it.clearStoredApiKey
            )
        }
    }

    fun toggleClearStoredApiKey(clear: Boolean) {
        _formState.update {
            it.copy(
                clearStoredApiKey = clear,
                apiKey = if (clear) "" else it.apiKey
            )
        }
    }

    fun updateFormEnabled(enabled: Boolean) {
        _formState.update { it.copy(isEnabled = enabled) }
    }

    fun updateFormDefaultModel(modelId: String) {
        _formState.update { it.copy(defaultModelId = modelId) }
    }

    fun updateModelInputDraft(draft: String) {
        _formState.update { it.copy(modelInputDraft = draft) }
    }

    fun addDraftModelId(rawModelId: String = _formState.value.modelInputDraft) {
        val parsed = parseModelListInput(rawModelId)
        if (parsed.isEmpty()) return
        _formState.update { current ->
            val merged = (current.modelIds + parsed).distinctBy { it.lowercase() }
            val nextDefault = current.defaultModelId.ifBlank { merged.firstOrNull().orEmpty() }
            current.copy(
                modelIds = merged,
                defaultModelId = nextDefault,
                modelInputDraft = ""
            )
        }
    }

    fun removeDraftModelId(modelId: String) {
        _formState.update { current ->
            val remaining = current.modelIds.filterNot { it.equals(modelId, ignoreCase = true) }
            val nextDefault = if (current.defaultModelId.equals(modelId, ignoreCase = true)) {
                remaining.firstOrNull().orEmpty()
            } else {
                current.defaultModelId
            }
            current.copy(modelIds = remaining, defaultModelId = nextDefault)
        }
    }

    fun saveForm() {
        val current = _formState.value
        val nameClean = current.name.trim()
        val baseClean = current.baseUrl.trim()
        val proxyClean = current.proxyUrl.trim()

        val nameErr = if (nameClean.isEmpty()) "Provider name is required." else null
        val baseErr = if (!ProviderConfigRepository.isValidHttpUrl(baseClean)) {
            "Enter a valid http:// or https:// base URL."
        } else null
        val proxyErr = if (proxyClean.isNotEmpty() && !ProviderConfigRepository.isValidHttpUrl(proxyClean)) {
            "Proxy URL must start with http:// or https://."
        } else null

        if (nameErr != null || baseErr != null || proxyErr != null) {
            _formState.update {
                it.copy(
                    nameError = nameErr,
                    baseUrlError = baseErr,
                    proxyUrlError = proxyErr
                )
            }
            return
        }

        // Include any model IDs still sitting in the text draft field
        val extraDraftModels = parseModelListInput(current.modelInputDraft)
        val finalModels = (current.modelIds + extraDraftModels).distinctBy { it.lowercase() }
        val resolvedDefault = current.defaultModelId.trim().takeIf { it.isNotEmpty() }
            ?: finalModels.firstOrNull()

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }
            runCatching {
                repository.upsertProvider(
                    existingId = current.editingProviderId,
                    name = nameClean,
                    type = current.type,
                    baseUrl = baseClean,
                    proxyUrl = proxyClean.takeIf { it.isNotEmpty() },
                    apiKey = current.apiKey,
                    clearApiKey = current.clearStoredApiKey,
                    defaultModelId = resolvedDefault,
                    isEnabled = current.isEnabled,
                    modelIds = finalModels
                )
            }.onSuccess {
                statusMessage.value = if (current.isEditing) {
                    "Updated provider \"$nameClean\" (${finalModels.size} model${if (finalModels.size == 1) "" else "s"})."
                } else {
                    "Saved custom provider \"$nameClean\" (${finalModels.size} model${if (finalModels.size == 1) "" else "s"})."
                }
                errorMessage.value = null
                dismissForm()
            }.onFailure { err ->
                _formState.update { it.copy(isSaving = false) }
                errorMessage.value = err.message ?: "Failed to save provider configuration."
            }
        }
    }

    // --- Persisted Provider & Model Operations ---

    fun toggleProviderEnabled(providerId: Long, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setProviderEnabled(providerId, enabled) }
                .onFailure { err -> errorMessage.value = err.message ?: "Failed to update provider status." }
        }
    }

    fun setProviderDefaultModel(providerId: Long, modelId: String?) {
        viewModelScope.launch {
            runCatching { repository.setDefaultModel(providerId, modelId) }
                .onSuccess {
                    statusMessage.value = if (modelId.isNullOrBlank()) {
                        "Cleared default model."
                    } else {
                        "Default model set to $modelId."
                    }
                }
                .onFailure { err -> errorMessage.value = err.message ?: "Failed to update default model." }
        }
    }

    fun duplicateProvider(providerId: Long) {
        viewModelScope.launch {
            runCatching { repository.duplicateProvider(providerId) }
                .onSuccess { copyId ->
                    if (copyId != null) statusMessage.value = "Duplicated provider configuration."
                }
                .onFailure { err -> errorMessage.value = err.message ?: "Failed to duplicate provider." }
        }
    }

    fun deleteProvider(providerId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteProvider(providerId) }
                .onSuccess { statusMessage.value = "Deleted provider configuration." }
                .onFailure { err -> errorMessage.value = err.message ?: "Failed to delete provider." }
        }
    }

    fun addModelToProvider(providerId: Long, rawModelInput: String) {
        val modelIds = parseModelListInput(rawModelInput)
        if (modelIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                modelIds.forEach { id -> repository.addModel(providerId, id) }
            }.onSuccess {
                statusMessage.value = "Added ${modelIds.size} model${if (modelIds.size == 1) "" else "s"}."
            }.onFailure { err ->
                errorMessage.value = err.message ?: "Failed to add model."
            }
        }
    }

    fun replaceProviderModels(providerId: Long, modelIds: List<String>) {
        viewModelScope.launch {
            runCatching { repository.replaceModels(providerId, modelIds) }
                .onSuccess { statusMessage.value = "Updated model list (${modelIds.size})." }
                .onFailure { err -> errorMessage.value = err.message ?: "Failed to replace models." }
        }
    }

    fun toggleModelCapability(model: ModelInfo, capability: ModelCapability) {
        viewModelScope.launch {
            runCatching { repository.toggleModelCapability(model, capability) }
                .onFailure { err -> errorMessage.value = err.message ?: "Failed to update model capability." }
        }
    }

    fun removeModelFromProvider(modelDbId: Long) {
        viewModelScope.launch {
            runCatching { repository.removeModel(modelDbId) }
                .onFailure { err -> errorMessage.value = err.message ?: "Failed to remove model." }
        }
    }

    fun fetchRemoteModels(provider: ProviderConfig) {
        viewModelScope.launch {
            fetchingProviderIds.update { it + provider.id }
            runCatching { repository.fetchAndSyncRemoteModels(provider) }
                .onSuccess { models ->
                    statusMessage.value = "Synced ${models.size} model${if (models.size == 1) "" else "s"} from ${provider.name}."
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Failed to fetch models from ${provider.name}."
                }
            fetchingProviderIds.update { it - provider.id }
        }
    }

    fun testProviderConnection(provider: ProviderConfig) {
        viewModelScope.launch {
            testingProviderIds.update { it + provider.id }
            val result = repository.testConnection(provider)
            connectionResults.update { it + (provider.id to result) }
            testingProviderIds.update { it - provider.id }
        }
    }

    companion object {
        fun parseModelListInput(raw: String): List<String> =
            raw.split(',', '\n', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
    }

    private data class FilterBundle(
        val query: String,
        val typeFilter: ProviderType?,
        val status: String?,
        val error: String?
    )
}
