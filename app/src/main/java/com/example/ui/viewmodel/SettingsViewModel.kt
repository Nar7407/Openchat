package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ConnectionTestResult
import com.example.data.repository.AllChatRepository
import com.example.domain.model.AutoRouterConfig
import com.example.domain.model.CustomAppEntry
import com.example.domain.model.DisplayCurrency
import com.example.domain.model.ModelCapability
import com.example.domain.model.ModelInfo
import com.example.domain.model.ProviderCatalog
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderPreset
import com.example.domain.model.ProviderType
import com.example.domain.model.QuickContact
import com.example.domain.model.SelectableModel
import com.example.domain.model.WebSearchProvider
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
    val helperHint: String = ProviderCatalog.defaultPreset.helperHint,
    val customModelsText: String = "",
    val isEnabled: Boolean = true
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
    val showLimitationsSheet: Boolean = false,
    val displayCurrency: DisplayCurrency = DisplayCurrency.USD,
    val isPinEnabled: Boolean = false,
    val pricingTargetModel: ModelInfo? = null,
    val vaultStatusMessage: String? = null,
    val vaultErrorMessage: String? = null,
    val searchProvider: WebSearchProvider = WebSearchProvider.GEMINI_NATIVE,
    val hasSearchApiKey: Boolean = false,
    val autoRouterConfig: AutoRouterConfig = AutoRouterConfig(),
    val textChatModels: List<SelectableModel> = emptyList(),
    val isImportingHistory: Boolean = false,
    val quickContacts: List<QuickContact> = emptyList(),
    val customApps: List<CustomAppEntry> = emptyList(),
    val visionFallbackModelIds: List<Long> = emptyList()
) {
    val visionModels: List<SelectableModel>
        get() = textChatModels.filter { it.model.supportsImageInput }.ifEmpty { textChatModels }
}

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
    private val pricingTargetModel = MutableStateFlow<ModelInfo?>(null)
    private val vaultStatusMessage = MutableStateFlow<String?>(null)
    private val vaultErrorMessage = MutableStateFlow<String?>(null)
    private val isImportingHistory = MutableStateFlow(false)

    private val vaultPrefsFlow = combine(
        repository.displayCurrencyFlow,
        repository.isPinEnabledFlow,
        pricingTargetModel,
        vaultStatusMessage,
        vaultErrorMessage
    ) { currency, pinEnabled, pricingModel, statusMsg, errMsg ->
        VaultPrefsBundle(currency, pinEnabled, pricingModel, statusMsg, errMsg)
    }

    private val powerSettingsFlow = combine(
        repository.searchProviderFlow,
        repository.hasSearchApiKeyFlow,
        repository.autoRouterConfigFlow,
        repository.observeModelsForCapability(ModelCapability.TEXT_CHAT),
        isImportingHistory
    ) { searchProv, hasSearchKey, routerCfg, chatModels, importing ->
        PowerSettingsBundle(searchProv, hasSearchKey, routerCfg, chatModels, importing)
    }

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
    }.combine(vaultPrefsFlow) { state, vault ->
        state.copy(
            displayCurrency = vault.currency,
            isPinEnabled = vault.isPinEnabled,
            pricingTargetModel = vault.pricingModel,
            vaultStatusMessage = vault.statusMsg,
            vaultErrorMessage = vault.errorMsg
        )
    }.combine(powerSettingsFlow) { state, power ->
        state.copy(
            searchProvider = power.searchProvider,
            hasSearchApiKey = power.hasSearchApiKey,
            autoRouterConfig = power.autoRouterConfig,
            textChatModels = power.textChatModels,
            isImportingHistory = power.isImportingHistory
        )
    }.combine(
        combine(
            repository.quickContactsFlow,
            repository.customAppsFlow,
            repository.visionFallbackModelIdsFlow
        ) { contacts, apps, fallbackIds -> Triple(contacts, apps, fallbackIds) }
    ) { state, (contacts, apps, fallbackIds) ->
        state.copy(
            quickContacts = contacts,
            customApps = apps,
            visionFallbackModelIds = fallbackIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    // --- Vault Security, Currency & Encrypted Backup ---

    fun setDisplayCurrency(currency: DisplayCurrency) {
        viewModelScope.launch {
            repository.setDisplayCurrency(currency)
        }
    }

    fun enableOrUpdatePin(pin: String) {
        viewModelScope.launch {
            vaultErrorMessage.value = null
            repository.setPinPassphrase(pin)
                .onSuccess {
                    vaultStatusMessage.value = "Vault PIN lock enabled. API keys re-encrypted with AES-256-GCM."
                }
                .onFailure { err ->
                    vaultErrorMessage.value = err.message ?: "Failed to set PIN."
                }
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            vaultErrorMessage.value = null
            repository.disablePinPassphrase()
            vaultStatusMessage.value = "Vault PIN lock disabled. Keys restored to Android Keystore."
        }
    }

    fun lockVaultNow() {
        repository.lockNow()
    }

    suspend fun exportEncryptedVault(password: String): Result<String> {
        vaultErrorMessage.value = null
        vaultStatusMessage.value = null
        return runCatching {
            repository.exportEncryptedVaultJson(password)
        }.onSuccess {
            vaultStatusMessage.value = "Encrypted .allchat vault backup exported."
        }.onFailure { err ->
            vaultErrorMessage.value = err.message ?: "Backup export failed."
        }
    }

    fun importEncryptedVault(rawJson: String, password: String) {
        viewModelScope.launch {
            vaultErrorMessage.value = null
            vaultStatusMessage.value = null
            repository.importEncryptedVaultJson(rawJson, password)
                .onSuccess { summary ->
                    vaultStatusMessage.value = summary
                }
                .onFailure { err ->
                    vaultErrorMessage.value = err.message ?: "Failed to decrypt backup file."
                }
        }
    }

    fun reportVaultError(message: String) {
        vaultErrorMessage.value = message
    }

    fun clearVaultNotices() {
        vaultStatusMessage.value = null
        vaultErrorMessage.value = null
    }

    // --- Web Search Grounding, Smart Auto-Router & ChatGPT/Claude Importer ---

    fun setSearchProvider(provider: WebSearchProvider) {
        viewModelScope.launch {
            repository.setSearchProvider(provider)
            vaultStatusMessage.value = "SEARCH // Active web search engine set to ${provider.label}."
        }
    }

    fun saveSearchApiKey(rawKey: String) {
        viewModelScope.launch {
            repository.saveSearchApiKey(rawKey)
            vaultStatusMessage.value = if (rawKey.isBlank()) {
                "SEARCH // Cleared Search API key."
            } else {
                "SEARCH // Encrypted Search API key saved locally."
            }
        }
    }

    fun updateAutoRouterConfig(config: AutoRouterConfig) {
        viewModelScope.launch {
            repository.saveAutoRouterConfig(config)
            vaultStatusMessage.value = "AUTO-ROUTER // Updated specialist model assignments."
        }
    }

    fun importExternalConversations(rawJson: String) {
        viewModelScope.launch {
            isImportingHistory.value = true
            vaultErrorMessage.value = null
            vaultStatusMessage.value = null
            repository.importExternalConversations(rawJson)
                .onSuccess { msg -> vaultStatusMessage.value = "IMPORT // $msg" }
                .onFailure { err -> vaultErrorMessage.value = err.message ?: "Failed to import conversations.json." }
            isImportingHistory.value = false
        }
    }

    // --- Quick Contacts, Custom App Packages & Live Lens 429 Failover Queue ---

    fun saveQuickContact(name: String, phoneNumber: String) {
        val cleanName = name.trim()
        val cleanPhone = phoneNumber.trim()
        if (cleanName.isBlank() || cleanPhone.isBlank()) return
        viewModelScope.launch {
            repository.saveQuickContact(QuickContact(name = cleanName, phoneNumber = cleanPhone))
            vaultStatusMessage.value = "CONTACTS // Saved $cleanName ($cleanPhone)."
        }
    }

    fun deleteQuickContact(id: String) {
        viewModelScope.launch {
            repository.deleteQuickContact(id)
        }
    }

    fun saveCustomApp(name: String, packageName: String) {
        val cleanName = name.trim()
        val cleanPkg = packageName.trim()
        if (cleanName.isBlank() || cleanPkg.isBlank()) return
        viewModelScope.launch {
            repository.saveCustomApp(CustomAppEntry(name = cleanName, packageName = cleanPkg))
            vaultStatusMessage.value = "APPS // Added $cleanName ($cleanPkg)."
        }
    }

    fun deleteCustomApp(id: String) {
        viewModelScope.launch {
            repository.deleteCustomApp(id)
        }
    }

    fun updateVisionFallbackQueue(ids: List<Long>) {
        viewModelScope.launch {
            repository.setVisionFallbackModelIds(ids)
            vaultStatusMessage.value = "FAILOVER // Updated Live Lens vision priority queue (${ids.size} model${if (ids.size == 1) "" else "s"})."
        }
    }

    // --- Model Token Pricing ---

    fun openModelPricing(model: ModelInfo) {
        pricingTargetModel.value = model
    }

    fun dismissModelPricing() {
        pricingTargetModel.value = null
    }

    fun saveModelPricing(modelDbId: Long, inputPrice: Double, outputPrice: Double) {
        viewModelScope.launch {
            repository.updateModelPricing(modelDbId, inputPrice, outputPrice)
            pricingTargetModel.value = null
        }
    }

    // --- Provider Catalog & Editor ---

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
                helperHint = "",
                customModelsText = provider.models.joinToString(", ") { it.modelId },
                isEnabled = provider.isEnabled
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
        apiKey: String? = null,
        customModelsText: String? = null,
        isEnabled: Boolean? = null
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
                apiKey = apiKey ?: current.apiKey,
                customModelsText = customModelsText ?: current.customModelsText,
                isEnabled = isEnabled ?: current.isEnabled
            )
        }
    }

    fun dismissEditor() {
        editorState.value = ProviderEditorState(isOpen = false)
    }

    fun saveEditorProvider() {
        val current = editorState.value
        if (current.name.isBlank()) return
        val parsedModels = ProviderConfigViewModel.parseModelListInput(current.customModelsText)
        val shouldSyncModels = current.editingProviderId != null || parsedModels.isNotEmpty()
        viewModelScope.launch {
            repository.saveProvider(
                existingId = current.editingProviderId,
                name = current.name,
                type = current.type,
                baseUrl = current.baseUrl,
                proxyUrl = current.proxyUrl,
                apiKey = current.apiKey,
                defaultModelId = parsedModels.firstOrNull(),
                isEnabled = current.isEnabled,
                modelIds = if (shouldSyncModels) parsedModels else null
            )
            dismissEditor()
        }
    }

    fun toggleProviderEnabled(providerId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setProviderEnabled(providerId, enabled)
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

    private data class VaultPrefsBundle(
        val currency: DisplayCurrency,
        val isPinEnabled: Boolean,
        val pricingModel: ModelInfo?,
        val statusMsg: String?,
        val errorMsg: String?
    )

    private data class PowerSettingsBundle(
        val searchProvider: WebSearchProvider,
        val hasSearchApiKey: Boolean,
        val autoRouterConfig: AutoRouterConfig,
        val textChatModels: List<SelectableModel>,
        val isImportingHistory: Boolean
    )
}
