package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ConnectionTestResult
import com.example.data.repository.AllChatRepository
import com.example.domain.model.AssistantRoutine
import com.example.domain.model.AutoRouterConfig
import com.example.domain.model.CustomAppEntry
import com.example.domain.model.DailyBudgetConfig
import com.example.domain.model.DisplayCurrency
import com.example.domain.model.ModelCapability
import com.example.domain.model.ModelInfo
import com.example.domain.model.ProviderCatalog
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderPreset
import com.example.domain.model.ProviderType
import com.example.domain.model.QuickContact
import com.example.domain.model.RoutineStep
import com.example.domain.model.RoutineStepType
import com.example.domain.model.SelectableModel
import com.example.domain.model.SmartZone
import com.example.domain.model.UserMemoryInfo
import com.example.domain.model.WebSearchProvider
import com.example.domain.model.WebhookActionInfo
import com.example.util.CurlImporter
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
    val visionFallbackModelIds: List<Long> = emptyList(),
    val routines: List<AssistantRoutine> = emptyList(),
    val smartZones: List<SmartZone> = emptyList(),
    val timeProfilesEnabled: Boolean = true,
    val autoEcoMode: Boolean = true,
    val autoDeleteJunkChats: Boolean = false,
    val wakePhraseEnabled: Boolean = false,
    val ambientWhisperEnabled: Boolean = false,
    val webhooks: List<WebhookActionInfo> = emptyList(),
    val webhookTestStatus: Map<Long, String> = emptyMap(),
    val dailyBudgetConfig: DailyBudgetConfig = DailyBudgetConfig(),
    val todaySpendUsd: Double = 0.0,
    val autoLockMinutes: Int = 5,
    val lockOnAppSwitch: Boolean = false,
    val autoDialWebhookUrl: String = "",
    val memories: List<UserMemoryInfo> = emptyList(),
    val autoExtractMemories: Boolean = true,
    val fastUtilityModelId: Long? = null
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
    private val webhookTestStatus = MutableStateFlow<Map<Long, String>>(emptyMap())

    private val smartTriggersPrefsFlow = combine(
        repository.smartZonesFlow,
        repository.timeProfilesEnabledFlow,
        repository.autoEcoModeFlow,
        repository.autoDeleteJunkChatsFlow,
        combine(repository.wakePhraseEnabledFlow, repository.ambientWhisperEnabledFlow) { w, a -> w to a }
    ) { zones, timeProf, eco, autoDel, (wake, whisper) ->
        SmartTriggersBundle(zones, timeProf, eco, autoDel, wake, whisper)
    }

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
            repository.visionFallbackModelIdsFlow,
            repository.routinesFlow
        ) { contacts, apps, fallbackIds, routines ->
            DeviceAutomationBundle(contacts, apps, fallbackIds, routines)
        }
    ) { state, bundle ->
        state.copy(
            quickContacts = bundle.contacts,
            customApps = bundle.apps,
            visionFallbackModelIds = bundle.fallbackIds,
            routines = bundle.routines
        )
    }.combine(smartTriggersPrefsFlow) { state, st ->
        state.copy(
            smartZones = st.smartZones,
            timeProfilesEnabled = st.timeProfilesEnabled,
            autoEcoMode = st.autoEcoMode,
            autoDeleteJunkChats = st.autoDeleteJunkChats,
            wakePhraseEnabled = st.wakePhraseEnabled,
            ambientWhisperEnabled = st.ambientWhisperEnabled
        )
    }.combine(
        combine(repository.webhooksFlow, webhookTestStatus) { hooks, statuses -> hooks to statuses }
    ) { state, (hooks, statuses) ->
        state.copy(
            webhooks = hooks,
            webhookTestStatus = statuses
        )
    }.combine(
        combine(
            repository.dailyBudgetConfigFlow,
            repository.observeTodaySpendUsd(),
            repository.autoLockMinutesFlow,
            repository.lockOnAppSwitchFlow
        ) { budget, spend, lockMins, lockSwitch ->
            BudgetAndLockBundle(budget, spend, lockMins, lockSwitch)
        }
    ) { state, bl ->
        state.copy(
            dailyBudgetConfig = bl.budget,
            todaySpendUsd = bl.todaySpendUsd,
            autoLockMinutes = bl.autoLockMinutes,
            lockOnAppSwitch = bl.lockOnAppSwitch
        )
    }.combine(
        combine(
            repository.autoDialWebhookUrlFlow,
            repository.memoriesFlow,
            repository.autoExtractMemoriesFlow,
            repository.fastUtilityModelIdFlow
        ) { dialUrl, memories, autoMem, fastId ->
            MemoryAndRoutingBundle(dialUrl, memories, autoMem, fastId)
        }
    ) { state, mr ->
        state.copy(
            autoDialWebhookUrl = mr.autoDialWebhookUrl,
            memories = mr.memories,
            autoExtractMemories = mr.autoExtractMemories,
            fastUtilityModelId = mr.fastUtilityModelId
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

    fun showStatusMessage(message: String) {
        vaultStatusMessage.value = message
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

    fun saveQuickContact(
        name: String,
        phoneNumber: String,
        email: String = "",
        upiId: String = "",
        nicknames: String = ""
    ) {
        val cleanName = name.trim()
        val cleanPhone = phoneNumber.trim()
        val cleanEmail = email.trim()
        val cleanUpi = upiId.trim()
        val cleanNicks = nicknames.trim()
        if (cleanName.isBlank() || (cleanPhone.isBlank() && cleanEmail.isBlank() && cleanUpi.isBlank())) return
        viewModelScope.launch {
            repository.saveQuickContact(
                QuickContact(
                    name = cleanName,
                    phoneNumber = cleanPhone,
                    email = cleanEmail,
                    upiId = cleanUpi,
                    nicknames = cleanNicks
                )
            )
            val details = listOfNotNull(
                cleanPhone.takeIf { it.isNotBlank() },
                cleanEmail.takeIf { it.isNotBlank() },
                cleanUpi.takeIf { it.isNotBlank() },
                cleanNicks.takeIf { it.isNotBlank() }?.let { "AKA $it" }
            ).joinToString(" · ")
            vaultStatusMessage.value = "CONTACTS // Saved $cleanName ($details)."
        }
    }

    fun importQuickContactsBulk(contacts: List<QuickContact>) {
        if (contacts.isEmpty()) return
        viewModelScope.launch {
            val count = repository.saveQuickContactsBulk(contacts)
            vaultStatusMessage.value = "CONTACTS // Imported $count contact${if (count == 1) "" else "s"} from .vcf."
        }
    }

    fun saveRoutine(routine: AssistantRoutine) {
        if (routine.name.isBlank() || routine.steps.isEmpty()) return
        viewModelScope.launch {
            repository.saveRoutine(routine)
            vaultStatusMessage.value = "ROUTINES // Saved \"${routine.name.trim()}\" (${routine.steps.size} steps)."
        }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch {
            repository.deleteRoutine(id)
            vaultStatusMessage.value = "ROUTINES // Deleted routine."
        }
    }

    fun addStarterRoutine(presetKey: String) {
        val preset = when (presetKey) {
            "morning" -> AssistantRoutine(
                name = "Morning Briefing",
                triggerPhrase = "good morning",
                steps = listOf(
                    RoutineStep(
                        id = "s1",
                        type = RoutineStepType.AI_WEB_SEARCH,
                        payload = "Give a crisp 4-bullet briefing on top world & tech headlines today plus one motivational focus tip."
                    ),
                    RoutineStep(id = "s2", type = RoutineStepType.READ_ALOUD_TTS, payload = ""),
                    RoutineStep(id = "s3", type = RoutineStepType.SAVE_NOTE, payload = "Morning Briefing")
                )
            )
            "focus" -> AssistantRoutine(
                name = "Deep Focus 25m",
                triggerPhrase = "focus mode",
                steps = listOf(
                    RoutineStep(
                        id = "s1",
                        type = RoutineStepType.AI_PROMPT,
                        payload = "Give me a 2-sentence sharp Pomodoro deep-work kickoff checklist."
                    ),
                    RoutineStep(id = "s2", type = RoutineStepType.READ_ALOUD_TTS, payload = ""),
                    RoutineStep(id = "s3", type = RoutineStepType.DEVICE_ACTION, payload = "set timer for 25 minutes")
                )
            )
            else -> AssistantRoutine(
                name = "Commute & Weather",
                triggerPhrase = "start commute",
                steps = listOf(
                    RoutineStep(
                        id = "s1",
                        type = RoutineStepType.AI_WEB_SEARCH,
                        payload = "Give a concise 2-sentence travel readiness tip and reminder checklist for commuting."
                    ),
                    RoutineStep(id = "s2", type = RoutineStepType.READ_ALOUD_TTS, payload = ""),
                    RoutineStep(id = "s3", type = RoutineStepType.DEVICE_ACTION, payload = "navigate to work")
                )
            )
        }
        saveRoutine(preset)
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

    // --- Smart Triggers, Geofence Zones, Junk Chat Pruning & Webhooks ---

    fun setTimeProfilesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setTimeProfilesEnabled(enabled)
            vaultStatusMessage.value = if (enabled) "SMART TRIGGERS // Time-of-Day auto profiles ON." else "SMART TRIGGERS // Time-of-Day auto profiles OFF."
        }
    }

    fun setAutoEcoMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoEcoMode(enabled)
            vaultStatusMessage.value = if (enabled) "ECO MODE // Battery & metered network awareness ON." else "ECO MODE // Disabled."
        }
    }

    fun setAutoDeleteJunkChats(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoDeleteJunkChats(enabled)
            vaultStatusMessage.value = if (enabled) "CLEANUP // Auto-delete 1-message chats older than 7 days ON." else "CLEANUP // Auto-delete disabled."
        }
    }

    fun pruneJunkChatsNow() {
        viewModelScope.launch {
            val count = repository.pruneOldJunkChatsNow()
            vaultStatusMessage.value = "CLEANUP // Pruned $count stale 1-message chat${if (count == 1) "" else "s"}."
        }
    }

    fun setWakePhraseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setWakePhraseEnabled(enabled)
            vaultStatusMessage.value = if (enabled) "VOICE // \"Hey AllChat\" wake-phrase armed in Assistant & Commute HUD." else "VOICE // Wake-phrase turned OFF."
        }
    }

    fun setAmbientWhisperEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAmbientWhisperEnabled(enabled)
            vaultStatusMessage.value = if (enabled) "AMBIENT // Loud-room Whisper Mode auto-detection ON." else "AMBIENT // Whisper Mode auto-detection OFF."
        }
    }

    fun saveSmartZone(zone: SmartZone) {
        viewModelScope.launch {
            val current = uiState.value.smartZones.filterNot { it.id == zone.id }
            repository.saveSmartZones((current + zone).take(5))
            vaultStatusMessage.value = "GEOFENCE // Saved zone \"${zone.name}\" (${zone.radiusMeters.toInt()}m)."
        }
    }

    fun deleteSmartZone(id: String) {
        viewModelScope.launch {
            val updated = uiState.value.smartZones.filterNot { it.id == id }
            repository.saveSmartZones(updated)
            vaultStatusMessage.value = "GEOFENCE // Removed zone."
        }
    }

    fun saveWebhook(webhook: WebhookActionInfo) {
        if (webhook.name.isBlank() || webhook.url.isBlank()) return
        viewModelScope.launch {
            repository.saveWebhook(webhook)
            vaultStatusMessage.value = "WEBHOOK // Saved \"${webhook.name.trim()}\"."
        }
    }

    fun deleteWebhook(id: Long) {
        viewModelScope.launch {
            repository.deleteWebhook(id)
            webhookTestStatus.update { it - id }
        }
    }

    fun testWebhook(webhook: WebhookActionInfo) {
        viewModelScope.launch {
            webhookTestStatus.update { it + (webhook.id to "Firing…") }
            val result = repository.executeWebhook(webhook)
            val label = result.fold(
                onSuccess = { "OK // $it" },
                onFailure = { "ERR // ${it.message ?: "Request failed"}" }
            )
            webhookTestStatus.update { it + (webhook.id to label) }
        }
    }

    fun importProviderFromCurl(rawCurl: String): Boolean {
        val parsed = CurlImporter.parse(rawCurl) ?: run {
            vaultErrorMessage.value = "Could not detect a valid http(s) endpoint inside that cURL command."
            return false
        }
        editorState.update { current ->
            current.copy(
                name = parsed.name,
                type = parsed.type,
                baseUrl = parsed.baseUrl,
                apiKey = parsed.apiKey.ifBlank { current.apiKey },
                customModelsText = parsed.modelId.orEmpty().ifBlank { current.customModelsText },
                helperHint = "Auto-parsed from cURL (${parsed.type.label}). Review and tap Save."
            )
        }
        vaultStatusMessage.value = "CURL IMPORT // Parsed ${parsed.name} (${parsed.baseUrl})."
        return true
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

    private data class DeviceAutomationBundle(
        val contacts: List<QuickContact>,
        val apps: List<CustomAppEntry>,
        val fallbackIds: List<Long>,
        val routines: List<AssistantRoutine>
    )

    fun saveDailyBudgetConfig(config: DailyBudgetConfig) {
        viewModelScope.launch {
            repository.saveDailyBudgetConfig(config)
            vaultStatusMessage.value = if (config.dailyCapUsd > 0.0) {
                "Daily cap saved ($${String.format(java.util.Locale.US, "%.2f", config.dailyCapUsd)}). Auto-downgrade triggers at 80%."
            } else {
                "Daily budget cap disabled."
            }
        }
    }

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch {
            repository.setAutoLockMinutes(minutes)
            vaultStatusMessage.value = if (minutes > 0) "Vault auto-locks after $minutes min idle." else "Idle auto-lock disabled."
        }
    }

    fun setLockOnAppSwitch(enabled: Boolean) {
        viewModelScope.launch {
            repository.setLockOnAppSwitch(enabled)
        }
    }

    fun saveAutoDialWebhookUrl(url: String) {
        val clean = url.trim()
        viewModelScope.launch {
            if (clean.isNotEmpty() && !clean.startsWith("http://") && !clean.startsWith("https://")) {
                vaultErrorMessage.value = "Auto-Dial Webhook URL must start with http:// or https://"
                return@launch
            }
            vaultErrorMessage.value = null
            repository.setAutoDialWebhookUrl(clean)
            vaultStatusMessage.value = if (clean.isNotEmpty()) {
                "Saved Auto-Dial Webhook URL for hands-free 1-tap earbud calls."
            } else {
                "Cleared Auto-Dial Webhook URL (using direct Android Dialer)."
            }
        }
    }

    fun setAutoExtractMemories(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoExtractMemories(enabled)
            vaultStatusMessage.value = if (enabled) {
                "Silent cross-session memory extraction enabled."
            } else {
                "Silent memory extraction paused."
            }
        }
    }

    fun addMemory(fact: String, category: String = "Preference") {
        viewModelScope.launch {
            val id = repository.addMemory(fact, category, source = "Manual")
            if (id > 0L) {
                vaultStatusMessage.value = "MEMORY // Saved to Memory Bank."
            }
        }
    }

    fun updateMemory(memory: UserMemoryInfo) {
        viewModelScope.launch {
            repository.updateMemory(memory)
            vaultStatusMessage.value = "MEMORY // Entry updated."
        }
    }

    fun toggleMemoryEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleMemory(id, enabled)
        }
    }

    fun setMemoryPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch {
            repository.setMemoryPinned(id, pinned)
            vaultStatusMessage.value = if (pinned) {
                "MEMORY // Pinned to always include in new sessions."
            } else {
                "MEMORY // Unpinned."
            }
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
            vaultStatusMessage.value = "MEMORY // All saved memories cleared."
        }
    }

    fun setFastUtilityModelId(modelDbId: Long?) {
        viewModelScope.launch {
            repository.setFastUtilityModelId(modelDbId)
            val label = uiState.value.textChatModels.firstOrNull { it.model.id == modelDbId }?.model?.displayName
            vaultStatusMessage.value = if (label != null) {
                "FAST LANE // Background utility tasks routed to $label."
            } else {
                "FAST LANE // Reset to use active primary model."
            }
        }
    }

    private data class MemoryAndRoutingBundle(
        val autoDialWebhookUrl: String,
        val memories: List<UserMemoryInfo>,
        val autoExtractMemories: Boolean,
        val fastUtilityModelId: Long?
    )

    private data class BudgetAndLockBundle(
        val budget: DailyBudgetConfig,
        val todaySpendUsd: Double,
        val autoLockMinutes: Int,
        val lockOnAppSwitch: Boolean
    )

    private data class SmartTriggersBundle(
        val smartZones: List<SmartZone>,
        val timeProfilesEnabled: Boolean,
        val autoEcoMode: Boolean,
        val autoDeleteJunkChats: Boolean,
        val wakePhraseEnabled: Boolean,
        val ambientWhisperEnabled: Boolean
    )
}
