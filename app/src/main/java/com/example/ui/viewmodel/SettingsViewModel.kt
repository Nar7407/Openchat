package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ConnectionTestResult
import com.example.data.repository.AllChatRepository
import com.example.domain.model.ModelCapability
import com.example.domain.model.ModelInfo
import com.example.domain.model.ProviderCatalog
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderPreset
import com.example.domain.model.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProviderEditorState(
    val isOpen: Boolean = false,
    val editingProviderId: Long? = null,
    val name: String = "",
    val type: ProviderType = ProviderType.GEMINI,
    val baseUrl: String = ProviderType.GEMINI.defaultBaseUrl,
    val proxyUrl: String = "",
    val apiKey: String = "",
    val hasExistingKey: Boolean = false,
    val requiresApiKey: Boolean = true,
    val helperHint: String = ProviderCatalog.defaultPreset.helperHint
) {
    val hasUrlPlaceholders: Boolean
        get() = baseUrl.contains('<') && baseUrl.contains('>')
}

data class SettingsUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val editor: ProviderEditorState = ProviderEditorState(),
    val showCatalogSheet: Boolean = false,
    val catalogSearchQuery: String = "",
    val catalogCategory: ProviderCategory = ProviderCategory.ALL,
    val testingProviderIds: Set<Long> = emptySet(),
    val fetchingProviderIds: Set<Long> = emptySet(),
    val connectionResults: Map<Long, ConnectionTestResult> = emptyMap(),
    val fetchMessages: Map<Long, String> = emptyMap(),
    val showLimitationsSheet: Boolean = false
)

class SettingsViewModel(
    private val repository: AllChatRepository
) : ViewModel() {

    private val editorState = MutableStateFlow(ProviderEditorState())
    private val showCatalogSheet = MutableStateFlow(false)
    private val catalogQuery = MutableStateFlow("")
    private val catalogCategory = MutableStateFlow(ProviderCategory.ALL)
    private val testingIds = MutableStateFlow<Set<Long>>(emptySet())
    private val fetchingIds = MutableStateFlow<Set<Long>>(emptySet())
    private val testResults = MutableStateFlow<Map<Long, ConnectionTestResult>>(emptyMap())
    private val fetchMsgs = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val showGuide = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.providersFlow,
        editorState,
        testingIds,
        fetchingIds,
        testResults
    ) { providers, editor, testing, fetching, results ->
        SettingsUiState(
            providers = providers,
            editor = editor,
            testingProviderIds = testing,
            fetchingProviderIds = fetching,
            connectionResults = results
        )
    }.combine(fetchMsgs) { state, msgs ->
        state.copy(fetchMessages = msgs)
    }.combine(showGuide) { state, guide ->
        state.copy(showLimitationsSheet = guide)
    }.combine(showCatalogSheet) { state, showCatalog ->
        state.copy(showCatalogSheet = showCatalog)
    }.combine(catalogQuery) { state, query ->
        state.copy(catalogSearchQuery = query)
    }.combine(catalogCategory) { state, cat ->
        state.copy(catalogCategory = cat)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun openCatalog() {
        catalogQuery.value = ""
        catalogCategory.value = ProviderCategory.ALL
        showCatalogSheet.value = true
    }

    fun dismissCatalog() {
        showCatalogSheet.value = false
    }

    fun updateCatalogQuery(query: String) {
        catalogQuery.value = query
    }

    fun selectCatalogCategory(category: ProviderCategory) {
        catalogCategory.value = category
    }

    fun selectPresetFromCatalog(preset: ProviderPreset) {
        showCatalogSheet.value = false
        if (editorState.value.isOpen && editorState.value.editingProviderId == null) {
            applyPresetInEditor(preset)
        } else {
            openAddProvider(preset)
        }
    }

    fun openCustomFromCatalogSearch() {
        val customName = catalogQuery.value.trim().ifBlank { "Custom" }
        showCatalogSheet.value = false
        val base = ProviderCatalog.customPreset
        editorState.value = ProviderEditorState(
            isOpen = true,
            editingProviderId = null,
            name = customName,
            type = base.type,
            baseUrl = base.baseUrl,
            proxyUrl = "",
            apiKey = "",
            hasExistingKey = false,
            requiresApiKey = base.requiresApiKey,
            helperHint = base.helperHint
        )
    }

    fun openAddProvider(preset: ProviderPreset = ProviderCatalog.defaultPreset) {
        editorState.value = ProviderEditorState(
            isOpen = true,
            editingProviderId = null,
            name = preset.title,
            type = preset.type,
            baseUrl = preset.baseUrl,
            proxyUrl = "",
            apiKey = "",
            hasExistingKey = false,
            requiresApiKey = preset.requiresApiKey,
            helperHint = preset.helperHint
        )
    }

    fun openEditProvider(provider: ProviderConfig) {
        viewModelScope.launch {
            val existingKey = repository.getProviderApiKey(provider.id)
            editorState.value = ProviderEditorState(
                isOpen = true,
                editingProviderId = provider.id,
                name = provider.name,
                type = provider.type,
                baseUrl = provider.baseUrl,
                proxyUrl = provider.proxyUrl.orEmpty(),
                apiKey = existingKey,
                hasExistingKey = provider.hasApiKey,
                requiresApiKey = true,
                helperHint = ""
            )
        }
    }

    fun applyPresetInEditor(preset: ProviderPreset) {
        editorState.update {
            it.copy(
                name = if (it.editingProviderId == null) preset.title else it.name,
                type = preset.type,
                baseUrl = preset.baseUrl,
                requiresApiKey = preset.requiresApiKey,
                helperHint = preset.helperHint
            )
        }
    }

    fun updateEditorFields(
        name: String? = null,
        type: ProviderType? = null,
        baseUrl: String? = null,
        proxyUrl: String? = null,
        apiKey: String? = null
    ) {
        editorState.update { current ->
            val newType = type ?: current.type
            val newBase = baseUrl ?: if (type != null && current.baseUrl == current.type.defaultBaseUrl) {
                newType.defaultBaseUrl
            } else {
                current.baseUrl
            }
            current.copy(
                name = name ?: current.name,
                type = newType,
                baseUrl = newBase,
                proxyUrl = proxyUrl ?: current.proxyUrl,
                apiKey = apiKey ?: current.apiKey
            )
        }
    }

    fun dismissEditor() {
        editorState.value = ProviderEditorState(isOpen = false)
    }

    fun saveEditorProvider() {
        val current = editorState.value
        if (current.name.isBlank()) return
        viewModelScope.launch {
            repository.saveProvider(
                existingId = current.editingProviderId,
                name = current.name,
                type = current.type,
                baseUrl = current.baseUrl,
                proxyUrl = current.proxyUrl,
                apiKey = current.apiKey
            )
            dismissEditor()
        }
    }

    fun deleteProvider(providerId: Long) {
        viewModelScope.launch {
            repository.deleteProvider(providerId)
        }
    }

    fun testConnection(provider: ProviderConfig) {
        viewModelScope.launch {
            testingIds.update { it + provider.id }
            val result = repository.testProviderConnection(provider)
            testResults.update { it + (provider.id to result) }
            testingIds.update { it - provider.id }
        }
    }

    fun fetchModels(provider: ProviderConfig) {
        viewModelScope.launch {
            fetchingIds.update { it + provider.id }
            fetchMsgs.update { it - provider.id }
            runCatching {
                repository.fetchAndSyncModels(provider)
            }.onSuccess { models ->
                fetchMsgs.update {
                    it + (provider.id to "Loaded ${models.size} model${if (models.size == 1) "" else "s"} from ${provider.name}.")
                }
            }.onFailure { err ->
                fetchMsgs.update {
                    it + (provider.id to "Fetch failed: ${err.message ?: "Unknown error"}")
                }
            }
            fetchingIds.update { it - provider.id }
        }
    }

    fun addManualModel(providerId: Long, modelId: String) {
        if (modelId.isBlank()) return
        viewModelScope.launch {
            repository.addManualModel(providerId, modelId)
        }
    }

    fun toggleCapability(model: ModelInfo, capability: ModelCapability) {
        val updated = when (capability) {
            ModelCapability.TEXT_CHAT -> model.copy(supportsTextChat = !model.supportsTextChat)
            ModelCapability.IMAGE_INPUT -> model.copy(supportsImageInput = !model.supportsImageInput)
            ModelCapability.IMAGE_GENERATION -> model.copy(supportsImageGen = !model.supportsImageGen)
            ModelCapability.VIDEO_GENERATION -> model.copy(supportsVideoGen = !model.supportsVideoGen)
            ModelCapability.SUMMARIZATION -> model.copy(supportsSummarization = !model.supportsSummarization)
        }
        viewModelScope.launch {
            repository.updateModelCapabilities(updated)
        }
    }

    fun deleteModel(modelDbId: Long) {
        viewModelScope.launch {
            repository.deleteModel(modelDbId)
        }
    }

    fun setShowLimitationsSheet(show: Boolean) {
        showGuide.value = show
    }
}
