package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.repository.AllChatRepository
import com.example.domain.model.AutoRouterConfig
import com.example.domain.model.BranchMessageNode
import com.example.domain.model.ChatOptions
import com.example.domain.model.ConversationTree
import com.example.domain.model.CustomSlashCommand
import com.example.domain.model.DisplayCurrency
import com.example.domain.model.KnowledgeDocInfo
import com.example.domain.model.McpCheckResult
import com.example.domain.model.McpServerConfig
import com.example.domain.model.MindMapNode
import com.example.domain.model.ModelCapability
import com.example.domain.model.PersonaInfo
import com.example.domain.model.RequestParamsOverride
import com.example.domain.model.SelectableModel
import com.example.domain.model.SlashCommandDef
import com.example.domain.model.SlashCommandId
import com.example.domain.model.SlashCommandRegistry
import com.example.domain.model.StudyPack
import com.example.domain.model.UserMemoryInfo
import com.example.domain.model.WebSearchProvider
import com.example.ui.theme.SwissThemePreset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatUiEvent {
    data object OpenSessionsDrawer : ChatUiEvent
    data object OpenModelPicker : ChatUiEvent
    data object OpenConnectProvider : ChatUiEvent
    data object OpenThemeSheet : ChatUiEvent
    data object OpenHelpSheet : ChatUiEvent
    data object OpenMcpSheet : ChatUiEvent
    data object OpenInitAgentsDialog : ChatUiEvent
    data object OpenCustomCommandsSheet : ChatUiEvent
    data object OpenMemoryBank : ChatUiEvent
    data object StartVoiceDictation : ChatUiEvent
    data object LaunchCameraVision : ChatUiEvent
    data class OpenPromptEditor(val initialText: String) : ChatUiEvent
    data class OpenShareDialog(val shareUrl: String, val markdown: String) : ChatUiEvent
    data class TriggerExportMarkdown(val suggestedFileName: String, val markdown: String) : ChatUiEvent
    data object ExitApp : ChatUiEvent
}

data class ChatUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val searchQuery: String = "",
    val activeConversation: ConversationEntity? = null,
    val branchNodes: List<BranchMessageNode> = emptyList(),
    val availableModels: List<SelectableModel> = emptyList(),
    val selectedModel: SelectableModel? = null,
    val personas: List<PersonaInfo> = emptyList(),
    val activePersona: PersonaInfo? = null,
    val displayCurrency: DisplayCurrency = DisplayCurrency.USD,
    val activeTheme: SwissThemePreset = SwissThemePreset.SWISS_SIGNAL,
    val showDetails: Boolean = true,
    val showThinking: Boolean = true,
    val agentsMd: String = "",
    val isGeneratingAgentsMd: Boolean = false,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val mcpCheckResults: Map<String, McpCheckResult> = emptyMap(),
    val checkingMcpIds: Set<String> = emptySet(),
    val activeShareCode: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val webSearchEnabled: Boolean = false,
    val toolsEnabled: Boolean = false,
    val searchProvider: WebSearchProvider = WebSearchProvider.GEMINI_NATIVE,
    val hasSearchApiKey: Boolean = false,
    val requestParams: RequestParamsOverride = RequestParamsOverride(),
    val autoRouteEnabled: Boolean = false,
    val autoRouterConfig: AutoRouterConfig = AutoRouterConfig(),
    val customSlashCommands: List<CustomSlashCommand> = emptyList(),
    val knowledgeDocs: List<KnowledgeDocInfo> = emptyList(),
    val memories: List<UserMemoryInfo> = emptyList(),
    val autoExtractMemories: Boolean = false,
    val quotedMessage: MessageEntity? = null,
    val miniModeEnabled: Boolean = false,
    val pendingDocumentTitle: String? = null,
    val isGeneratingStudy: Boolean = false,
    val activeStudyPack: StudyPack? = null,
    val isGeneratingMindMap: Boolean = false,
    val activeMindMap: MindMapNode? = null,
    val pendingAttachmentUri: String? = null,
    val isStreaming: Boolean = false,
    val statusNotice: String? = null,
    val errorToast: String? = null
) {
    val messages: List<MessageEntity> get() = branchNodes.map { it.message }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: AllChatRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val activeConversationId = MutableStateFlow<Long?>(null)
    private val fallbackSelectedModelId = MutableStateFlow<Long?>(null)
    private val fallbackPersonaId = MutableStateFlow<Long?>(null)
    private val selectedBranchByParent = MutableStateFlow<Map<Long, Long>>(emptyMap())
    private val pendingAttachmentUri = MutableStateFlow<String?>(null)
    private val isStreaming = MutableStateFlow(false)
    private val isGeneratingAgentsMd = MutableStateFlow(false)
    private val statusNotice = MutableStateFlow<String?>(null)
    private val errorToast = MutableStateFlow<String?>(null)
    private val mcpCheckResults = MutableStateFlow<Map<String, McpCheckResult>>(emptyMap())
    private val checkingMcpIds = MutableStateFlow<Set<String>>(emptySet())
    private val webSearchEnabled = MutableStateFlow(false)
    private val toolsEnabled = MutableStateFlow(false)
    private val requestParams = MutableStateFlow(RequestParamsOverride())
    private val quotedMessage = MutableStateFlow<MessageEntity?>(null)
    private val miniModeEnabled = MutableStateFlow(false)
    private val pendingDocumentContext = MutableStateFlow<Pair<String, String>?>(null)
    private val isGeneratingStudy = MutableStateFlow(false)
    private val activeStudyPack = MutableStateFlow<StudyPack?>(null)
    private val isGeneratingMindMap = MutableStateFlow(false)
    private val activeMindMap = MutableStateFlow<MindMapNode?>(null)

    // Per-conversation redo stack storing popped message turns from /undo
    private val redoStackByConv = MutableStateFlow<Map<Long, List<List<MessageEntity>>>>(emptyMap())

    private val _events = MutableSharedFlow<ChatUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ChatUiEvent> = _events.asSharedFlow()

    private var activeStreamJob: Job? = null

    private val chatModelsFlow = repository.observeModelsForCapability(ModelCapability.TEXT_CHAT)

    private val activeConversationFlow = activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeConversation(id)
    }

    private val messagesFlow = activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
    }

    private val resolvedBranchesFlow = combine(
        messagesFlow,
        selectedBranchByParent
    ) { allMessages, branchMap ->
        ConversationTree.resolveActiveBranch(allMessages, branchMap)
    }

    private val modelAndPersonaBundleFlow = combine(
        chatModelsFlow,
        fallbackSelectedModelId,
        repository.personasFlow,
        fallbackPersonaId,
        repository.displayCurrencyFlow
    ) { models, fallbackModelId, personas, fallbackPerId, currency ->
        ModelPersonaBundle(models, fallbackModelId, personas, fallbackPerId, currency)
    }

    private val commandPrefsBundleFlow = combine(
        repository.themePresetIdFlow,
        repository.showDetailsFlow,
        repository.showThinkingFlow,
        repository.agentsMdFlow,
        repository.sharedSessionsFlow
    ) { themeId, details, thinking, agentsMd, sharedMap ->
        CommandPrefsBundle(
            theme = SwissThemePreset.fromId(themeId),
            showDetails = details,
            showThinking = thinking,
            agentsMd = agentsMd,
            sharedSessions = sharedMap
        )
    }

    private val mcpBundleFlow = combine(
        repository.mcpServersFlow,
        mcpCheckResults,
        checkingMcpIds,
        isGeneratingAgentsMd,
        redoStackByConv
    ) { servers, results, checking, genAgents, redoMap ->
        McpAndUndoBundle(servers, results, checking, genAgents, redoMap)
    }

    private val powerFeaturesBundleFlow = combine(
        webSearchEnabled,
        repository.searchProviderFlow,
        repository.hasSearchApiKeyFlow,
        requestParams,
        repository.autoRouteEnabledFlow
    ) { searchOn, searchProv, hasSearchKey, params, autoOn ->
        PowerFeaturesBundle(searchOn, searchProv, hasSearchKey, params, autoOn)
    }

    private val knowledgeAndCommandsFlow = combine(
        repository.autoRouterConfigFlow,
        repository.customSlashCommandsFlow,
        repository.knowledgeDocsFlow
    ) { routerCfg, customCmds, docs ->
        Triple(routerCfg, customCmds, docs)
    }

    private val memoryAndToolsBundleFlow = combine(
        toolsEnabled,
        repository.memoriesFlow,
        repository.autoExtractMemoriesFlow,
        quotedMessage,
        miniModeEnabled
    ) { toolsOn, memories, autoMem, quoted, mini ->
        MemoryToolsBundle(toolsOn, memories, autoMem, quoted, mini)
    }

    private val studyAndMapBundleFlow = combine(
        pendingDocumentContext,
        isGeneratingStudy,
        activeStudyPack,
        isGeneratingMindMap,
        activeMindMap
    ) { pendingDoc, genStudy, studyPack, genMap, mindMap ->
        StudyMapBundle(pendingDoc?.first, genStudy, studyPack, genMap, mindMap)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        repository.conversationsFlow,
        searchQuery,
        activeConversationFlow,
        resolvedBranchesFlow,
        modelAndPersonaBundleFlow
    ) { conversations, query, activeConv, branchNodes, bundle ->
        val filtered = if (query.isBlank()) {
            conversations
        } else {
            conversations.filter { it.title.contains(query, ignoreCase = true) }
        }
        val selectedModel = bundle.models.firstOrNull { it.model.id == activeConv?.selectedModelDbId }
            ?: bundle.models.firstOrNull { it.model.id == bundle.fallbackModelId }
            ?: pickPreferredChatModel(bundle.models)

        val resolvedPersonaId = activeConv?.activePersonaId ?: bundle.fallbackPersonaId
        val activePersona = bundle.personas.firstOrNull { it.id == resolvedPersonaId }

        ChatUiState(
            conversations = filtered,
            searchQuery = query,
            activeConversation = activeConv,
            branchNodes = branchNodes,
            availableModels = bundle.models,
            selectedModel = selectedModel,
            personas = bundle.personas,
            activePersona = activePersona,
            displayCurrency = bundle.currency,
            canUndo = branchNodes.isNotEmpty()
        )
    }.combine(commandPrefsBundleFlow) { state, prefs ->
        val shareCode = state.activeConversation?.id?.let { prefs.sharedSessions[it] }
        state.copy(
            activeTheme = prefs.theme,
            showDetails = prefs.showDetails,
            showThinking = prefs.showThinking,
            agentsMd = prefs.agentsMd,
            activeShareCode = shareCode
        )
    }.combine(mcpBundleFlow) { state, mcp ->
        val convId = state.activeConversation?.id
        val canRedo = convId != null && !mcp.redoMap[convId].isNullOrEmpty()
        state.copy(
            mcpServers = mcp.servers,
            mcpCheckResults = mcp.results,
            checkingMcpIds = mcp.checking,
            isGeneratingAgentsMd = mcp.genAgents,
            canRedo = canRedo
        )
    }.combine(powerFeaturesBundleFlow) { state, p ->
        state.copy(
            webSearchEnabled = p.webSearchEnabled,
            searchProvider = p.searchProvider,
            hasSearchApiKey = p.hasSearchApiKey,
            requestParams = p.requestParams,
            autoRouteEnabled = p.autoRouteEnabled
        )
    }.combine(knowledgeAndCommandsFlow) { state, (routerCfg, customCmds, docs) ->
        state.copy(
            autoRouterConfig = routerCfg,
            customSlashCommands = customCmds,
            knowledgeDocs = docs
        )
    }.combine(memoryAndToolsBundleFlow) { state, mt ->
        state.copy(
            toolsEnabled = mt.toolsEnabled,
            memories = mt.memories,
            autoExtractMemories = mt.autoExtractMemories,
            quotedMessage = mt.quotedMessage,
            miniModeEnabled = mt.miniModeEnabled
        )
    }.combine(studyAndMapBundleFlow) { state, sm ->
        state.copy(
            pendingDocumentTitle = sm.pendingDocumentTitle,
            isGeneratingStudy = sm.isGeneratingStudy,
            activeStudyPack = sm.activeStudyPack,
            isGeneratingMindMap = sm.isGeneratingMindMap,
            activeMindMap = sm.activeMindMap
        )
    }.combine(pendingAttachmentUri) { state, att ->
        state.copy(pendingAttachmentUri = att)
    }.combine(isStreaming) { state, streaming ->
        state.copy(isStreaming = streaming)
    }.combine(combine(statusNotice, errorToast) { notice, err -> notice to err }) { state, (notice, err) ->
        state.copy(statusNotice = notice, errorToast = err)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultPersonas()
        }
        viewModelScope.launch {
            repository.conversationsFlow.collect { list ->
                if (activeConversationId.value == null && list.isNotEmpty()) {
                    activeConversationId.value = list.first().id
                }
            }
        }
    }

    private fun pickPreferredChatModel(models: List<SelectableModel>): SelectableModel? {
        if (models.isEmpty()) return null
        val preferredKeywords = listOf(
            "gemini-2.5", "gemini-2.0", "gpt-4o", "gpt-4.1", "claude-3", "claude-sonnet",
            "llama-3.3", "llama-3.1", "deepseek-r1", "deepseek-v3", "qwen", "instruct", "chat"
        )
        for (keyword in preferredKeywords) {
            val match = models.firstOrNull { it.model.modelId.contains(keyword, ignoreCase = true) }
            if (match != null) return match
        }
        return models.first()
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectConversation(conversationId: Long) {
        stopGeneration()
        selectedBranchByParent.value = emptyMap()
        activeConversationId.value = conversationId
    }

    fun createNewChat() {
        stopGeneration()
        selectedBranchByParent.value = emptyMap()
        viewModelScope.launch {
            val modelId = uiState.value.selectedModel?.model?.id
            val personaId = uiState.value.activePersona?.id
            val newId = repository.createConversation("New Chat", modelId, personaId)
            activeConversationId.value = newId
        }
    }

    fun renameConversation(conversationId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(conversationId, newTitle)
        }
    }

    fun deleteConversation(conversationId: Long) {
        if (activeConversationId.value == conversationId) {
            stopGeneration()
            selectedBranchByParent.value = emptyMap()
            activeConversationId.value = null
        }
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
        }
    }

    fun selectModel(selectableModel: SelectableModel) {
        fallbackSelectedModelId.value = selectableModel.model.id
        viewModelScope.launch {
            repository.setAutoRouteEnabled(false)
            val convId = activeConversationId.value
            if (convId != null) {
                repository.updateConversationModel(convId, selectableModel.model.id)
            }
        }
    }

    fun setAutoRouteEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoRouteEnabled(enabled)
            statusNotice.value = if (enabled) {
                "AUTO-ROUTER // Smart task routing active (Fast, Coding, Vision, Long Context)."
            } else {
                "AUTO-ROUTER // Manual model selection active."
            }
        }
    }

    fun toggleWebSearch() {
        val next = !webSearchEnabled.value
        webSearchEnabled.value = next
        val state = uiState.value
        if (next) {
            val prov = state.searchProvider
            val note = if (prov == WebSearchProvider.GEMINI_NATIVE) {
                "SEARCH // Native Gemini Google Search Grounding armed."
            } else if (state.hasSearchApiKey) {
                "SEARCH // ${prov.label} live web grounding armed (top 5 snippets)."
            } else {
                "SEARCH // Armed. Add a ${prov.label} key in Settings (or Gemini models will auto-use native Search)."
            }
            statusNotice.value = note
        } else {
            statusNotice.value = "SEARCH // Live web search turned OFF."
        }
    }

    fun toggleTools() {
        val next = !toolsEnabled.value
        toolsEnabled.value = next
        statusNotice.value = if (next) {
            "TOOLS // Native Function Calling armed (calculator, clock/tz, fetch_url, generate_chart)."
        } else {
            "TOOLS // Native Function Calling turned OFF."
        }
    }

    fun toggleMiniMode() {
        val next = !miniModeEnabled.value
        miniModeEnabled.value = next
    }

    fun setQuotedMessage(message: MessageEntity?) {
        quotedMessage.value = message
    }

    fun clearQuotedMessage() {
        quotedMessage.value = null
    }

    fun clearPendingDocument() {
        pendingDocumentContext.value = null
    }

    fun triggerCompactThread() {
        compactCurrentConversation()
    }

    fun triggerVoiceDictation() {
        _events.tryEmit(ChatUiEvent.StartVoiceDictation)
    }

    fun triggerCameraVision() {
        _events.tryEmit(ChatUiEvent.LaunchCameraVision)
    }

    fun setAutoExtractMemories(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoExtractMemories(enabled)
            statusNotice.value = if (enabled) {
                "MEMORY // Auto-extract user preferences enabled."
            } else {
                "MEMORY // Auto-extract disabled. Use /remember to save manually."
            }
        }
    }

    fun addMemory(fact: String, category: String = "Preference") {
        viewModelScope.launch {
            val id = repository.addMemory(fact, category, source = "Manual")
            if (id > 0L) {
                statusNotice.value = "MEMORY // Saved to Memory Bank."
            }
        }
    }

    fun updateMemory(memory: UserMemoryInfo) {
        viewModelScope.launch {
            repository.updateMemory(memory)
        }
    }

    fun toggleMemory(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleMemory(id, enabled)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun generateStudyPackForMessage(message: MessageEntity) {
        val model = uiState.value.selectedModel ?: run {
            errorToast.value = "Select a Text Chat model first to generate flashcards & quiz."
            return
        }
        if (isGeneratingStudy.value) return
        viewModelScope.launch {
            isGeneratingStudy.value = true
            statusNotice.value = "STUDY // Generating interactive flashcards & quiz…"
            runCatching {
                repository.generateStudyPack(message.content, model)
            }.onSuccess { pack ->
                activeStudyPack.value = pack
                statusNotice.value = null
            }.onFailure { err ->
                errorToast.value = err.message ?: "Failed to generate Study Pack."
            }
            isGeneratingStudy.value = false
        }
    }

    fun dismissStudyPack() {
        activeStudyPack.value = null
    }

    fun generateMindMapForMessage(message: MessageEntity) {
        val model = uiState.value.selectedModel ?: run {
            errorToast.value = "Select a Text Chat model first to generate a Mind Map."
            return
        }
        if (isGeneratingMindMap.value) return
        viewModelScope.launch {
            isGeneratingMindMap.value = true
            statusNotice.value = "MIND MAP // Building visual node graph…"
            runCatching {
                repository.generateMindMap(message.content, model)
            }.onSuccess { root ->
                activeMindMap.value = root
                statusNotice.value = null
            }.onFailure { err ->
                errorToast.value = err.message ?: "Failed to generate Mind Map."
            }
            isGeneratingMindMap.value = false
        }
    }

    fun dismissMindMap() {
        activeMindMap.value = null
    }

    fun updateRequestParams(updated: RequestParamsOverride) {
        requestParams.value = updated
    }

    fun resetRequestParams() {
        requestParams.value = RequestParamsOverride()
        statusNotice.value = "PARAMS // Reset Temperature, Top-P, Max Tokens, and JSON mode to defaults."
    }

    fun saveCustomSlashCommand(command: CustomSlashCommand) {
        viewModelScope.launch {
            repository.saveCustomSlashCommand(command)
            statusNotice.value = "CMD // Saved custom command ${command.trigger}."
        }
    }

    fun deleteCustomSlashCommand(id: String) {
        viewModelScope.launch {
            repository.deleteCustomSlashCommand(id)
        }
    }

    fun selectPersona(persona: PersonaInfo?) {
        fallbackPersonaId.value = persona?.id
        val convId = activeConversationId.value
        val defaultModel = persona?.defaultModelDbId?.let { targetId ->
            uiState.value.availableModels.firstOrNull { it.model.id == targetId }
        }
        if (defaultModel != null) {
            fallbackSelectedModelId.value = defaultModel.model.id
        }
        if (convId != null) {
            viewModelScope.launch {
                repository.updateConversationPersona(convId, persona?.id)
                if (defaultModel != null) {
                    repository.updateConversationModel(convId, defaultModel.model.id)
                }
            }
        }
    }

    fun savePersona(persona: PersonaInfo) {
        viewModelScope.launch {
            repository.savePersona(persona)
        }
    }

    fun deletePersona(personaId: Long) {
        if (uiState.value.activePersona?.id == personaId) {
            selectPersona(null)
        }
        viewModelScope.launch {
            repository.deletePersona(personaId)
        }
    }

    fun switchBranch(parentKey: Long, targetMessageId: Long) {
        selectedBranchByParent.update { it + (parentKey to targetMessageId) }
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.persistAttachment(uri)
            }.onSuccess { localUri ->
                pendingAttachmentUri.value = localUri
            }.onFailure { err ->
                errorToast.value = err.message ?: "Could not attach image."
            }
        }
    }

    fun attachSharedImageAndSelectVision(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.persistAttachment(uri)
            }.onSuccess { localUri ->
                pendingAttachmentUri.value = localUri
                val state = uiState.value
                val currentSupportsVision = state.selectedModel?.model?.supportsImageInput == true
                if (!currentSupportsVision) {
                    val configuredVision = state.autoRouterConfig.visionModelId?.let { id ->
                        state.availableModels.firstOrNull { it.model.id == id }
                    }
                    val bestVision = configuredVision
                        ?: state.availableModels.firstOrNull { it.model.supportsImageInput }
                    if (bestVision != null) {
                        selectModel(bestVision)
                        statusNotice.value = "SHARE // Image attached & auto-selected Vision model: ${bestVision.model.displayName}."
                    } else {
                        statusNotice.value = "SHARE // Image attached. Select a Vision-capable model to analyze."
                    }
                } else {
                    statusNotice.value = "SHARE // Image plate attached to Chat."
                }
            }.onFailure { err ->
                errorToast.value = err.message ?: "Could not attach shared image."
            }
        }
    }

    fun startChatWithSharedDocument(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.extractTextFromFile(uri)
            }.onSuccess { (title, text) ->
                val modelId = uiState.value.selectedModel?.model?.id
                val personaId = uiState.value.activePersona?.id
                val newId = repository.createConversation("Doc: ${title.take(32)}", modelId, personaId)
                selectedBranchByParent.value = emptyMap()
                activeConversationId.value = newId
                pendingDocumentContext.value = title to text.take(28_000)
                statusNotice.value = "DOC // Loaded \"$title\" (${text.length} chars). Ask anything about it."
            }.onFailure { err ->
                errorToast.value = err.message ?: "Could not read shared document."
            }
        }
    }

    fun clearAttachment() {
        pendingAttachmentUri.value = null
    }

    fun showToast(message: String) {
        errorToast.value = message
    }

    fun showNotice(message: String) {
        statusNotice.value = message
    }

    fun clearNotices() {
        errorToast.value = null
        statusNotice.value = null
    }

    fun clearErrorToast() {
        errorToast.value = null
    }

    // --- Theme, MCP & AGENTS.md Controls ---

    fun selectTheme(preset: SwissThemePreset) {
        viewModelScope.launch {
            repository.setThemePresetId(preset.id)
            statusNotice.value = "THEME // Switched to ${preset.displayName}."
        }
    }

    fun addMcpServer(name: String, url: String, token: String) {
        viewModelScope.launch {
            val server = McpServerConfig(
                id = "",
                name = name,
                url = url,
                authToken = token
            )
            repository.saveMcpServer(server)
            statusNotice.value = "MCP // Saved server \"$name\"."
        }
    }

    fun deleteMcpServer(serverId: String) {
        viewModelScope.launch {
            repository.deleteMcpServer(serverId)
            mcpCheckResults.update { it - serverId }
        }
    }

    fun checkMcpServer(server: McpServerConfig) {
        viewModelScope.launch {
            checkingMcpIds.update { it + server.id }
            val result = repository.checkMcpServer(server)
            mcpCheckResults.update { it + (server.id to result) }
            checkingMcpIds.update { it - server.id }
        }
    }

    fun checkAllMcpServers() {
        uiState.value.mcpServers.forEach { checkMcpServer(it) }
    }

    fun saveAgentsMd(content: String) {
        viewModelScope.launch {
            repository.setAgentsMd(content)
            statusNotice.value = if (content.isBlank()) {
                "INIT // Cleared active AGENTS.md rules."
            } else {
                "INIT // Saved project AGENTS.md instructions."
            }
        }
    }

    fun generateAgentsMdWithAi(projectBrief: String) {
        val model = uiState.value.selectedModel ?: run {
            errorToast.value = "Select a Text Chat model first to generate AGENTS.md."
            return
        }
        viewModelScope.launch {
            isGeneratingAgentsMd.value = true
            runCatching {
                repository.generateAgentsMd(projectBrief, model)
            }.onSuccess {
                statusNotice.value = "INIT // Generated and activated AGENTS.md."
            }.onFailure { err ->
                errorToast.value = err.message ?: "Failed to generate AGENTS.md."
            }
            isGeneratingAgentsMd.value = false
        }
    }

    fun scanFileForAgentsMd(uri: Uri) {
        val model = uiState.value.selectedModel
        viewModelScope.launch {
            isGeneratingAgentsMd.value = true
            runCatching {
                val (title, text) = repository.extractTextFromFile(uri)
                if (model != null) {
                    repository.generateAgentsMd("Scanned file: $title\n\n${text.take(8000)}", model)
                } else {
                    repository.setAgentsMd("# AGENTS.md (Imported from $title)\n\n${text.take(8000)}")
                }
            }.onSuccess {
                statusNotice.value = "INIT // Scanned file & updated AGENTS.md."
            }.onFailure { err ->
                errorToast.value = err.message ?: "Could not scan selected file."
            }
            isGeneratingAgentsMd.value = false
        }
    }

    // --- Slash Command Execution ---

    fun runSlashCommand(def: SlashCommandDef, argument: String = "") {
        clearNotices()
        when (def.id) {
            SlashCommandId.NEW -> {
                createNewChat()
                statusNotice.value = "SESSION // Started fresh conversation. Previous session saved."
            }
            SlashCommandId.SESSIONS -> {
                _events.tryEmit(ChatUiEvent.OpenSessionsDrawer)
            }
            SlashCommandId.COMPACT -> {
                compactCurrentConversation()
            }
            SlashCommandId.UNDO -> {
                undoLastTurn()
            }
            SlashCommandId.REDO -> {
                redoLastTurn()
            }
            SlashCommandId.SHARE -> {
                shareCurrentSession()
            }
            SlashCommandId.UNSHARE -> {
                unshareCurrentSession()
            }
            SlashCommandId.EXPORT -> {
                exportCurrentSession()
            }
            SlashCommandId.CONNECT -> {
                _events.tryEmit(ChatUiEvent.OpenConnectProvider)
            }
            SlashCommandId.MODELS -> {
                _events.tryEmit(ChatUiEvent.OpenModelPicker)
            }
            SlashCommandId.MCP -> {
                _events.tryEmit(ChatUiEvent.OpenMcpSheet)
                checkAllMcpServers()
            }
            SlashCommandId.INIT -> {
                _events.tryEmit(ChatUiEvent.OpenInitAgentsDialog)
            }
            SlashCommandId.HELP -> {
                _events.tryEmit(ChatUiEvent.OpenHelpSheet)
            }
            SlashCommandId.THEMES -> {
                _events.tryEmit(ChatUiEvent.OpenThemeSheet)
            }
            SlashCommandId.DETAILS -> {
                val next = !uiState.value.showDetails
                viewModelScope.launch {
                    repository.setShowDetails(next)
                    statusNotice.value = "DETAILS // Inline execution metadata ${if (next) "VISIBLE" else "HIDDEN"}."
                }
            }
            SlashCommandId.THINKING -> {
                val next = !uiState.value.showThinking
                viewModelScope.launch {
                    repository.setShowThinking(next)
                    statusNotice.value = "THINKING // Reasoning blocks ${if (next) "VISIBLE" else "HIDDEN"}."
                }
            }
            SlashCommandId.EDITOR -> {
                _events.tryEmit(ChatUiEvent.OpenPromptEditor(argument))
            }
            SlashCommandId.COMMANDS -> {
                _events.tryEmit(ChatUiEvent.OpenCustomCommandsSheet)
            }
            SlashCommandId.REMEMBER -> {
                val fact = argument.trim()
                if (fact.isNotBlank()) {
                    addMemory(fact, "Preference")
                } else {
                    _events.tryEmit(ChatUiEvent.OpenMemoryBank)
                }
            }
            SlashCommandId.MEMORY -> {
                _events.tryEmit(ChatUiEvent.OpenMemoryBank)
            }
            SlashCommandId.EXIT -> {
                stopGeneration()
                _events.tryEmit(ChatUiEvent.ExitApp)
            }
        }
    }

    private fun compactCurrentConversation() {
        val state = uiState.value
        val convId = state.activeConversation?.id ?: return
        val model = state.selectedModel ?: run {
            errorToast.value = "Select a model first to run /compact."
            return
        }
        val activeMsgs = state.messages
        if (activeMsgs.size < 2) {
            statusNotice.value = "COMPACT // Need at least 2 messages to compact."
            return
        }
        if (isStreaming.value) return

        activeStreamJob = viewModelScope.launch {
            isStreaming.value = true
            statusNotice.value = "COMPACT // Summarizing ${activeMsgs.size} messages…"
            try {
                repository.compactConversation(convId, activeMsgs, model)
                selectedBranchByParent.value = emptyMap()
                redoStackByConv.update { it - convId }
                statusNotice.value = "COMPACT // Context window compacted cleanly."
            } catch (t: Throwable) {
                errorToast.value = t.message ?: "Compaction failed."
            } finally {
                isStreaming.value = false
            }
        }
    }

    private fun undoLastTurn() {
        val state = uiState.value
        val convId = state.activeConversation?.id ?: return
        if (isStreaming.value) stopGeneration()
        val msgs = state.messages
        if (msgs.isEmpty()) {
            statusNotice.value = "UNDO // Nothing left to revert in this session."
            return
        }
        // Revert the last assistant+user pair if ending on assistant, otherwise revert the last single message
        val toPop = if (msgs.size >= 2 && msgs.last().role == "ASSISTANT" && msgs[msgs.lastIndex - 1].role == "USER") {
            listOf(msgs[msgs.lastIndex - 1], msgs.last())
        } else {
            listOf(msgs.last())
        }
        viewModelScope.launch {
            repository.deleteMessagesByIds(toPop.map { it.id })
            redoStackByConv.update { map ->
                val stack = map[convId].orEmpty()
                map + (convId to (stack + listOf(toPop)))
            }
            statusNotice.value = "UNDO // Reverted ${toPop.size} message${if (toPop.size > 1) "s" else ""}. Run /redo to restore."
        }
    }

    private fun redoLastTurn() {
        val convId = uiState.value.activeConversation?.id ?: return
        val stack = redoStackByConv.value[convId].orEmpty()
        if (stack.isEmpty()) {
            statusNotice.value = "REDO // Nothing to restore."
            return
        }
        val batch = stack.last()
        viewModelScope.launch {
            repository.restoreMessages(batch)
            redoStackByConv.update { map ->
                val remaining = stack.dropLast(1)
                if (remaining.isEmpty()) map - convId else map + (convId to remaining)
            }
            statusNotice.value = "REDO // Restored ${batch.size} message${if (batch.size > 1) "s" else ""}."
        }
    }

    private fun shareCurrentSession() {
        val state = uiState.value
        val conv = state.activeConversation ?: run {
            errorToast.value = "Start a conversation before running /share."
            return
        }
        viewModelScope.launch {
            val url = repository.shareConversation(conv.id)
            val md = repository.buildMarkdownTranscript(conv.title, state.messages, url)
            _events.tryEmit(ChatUiEvent.OpenShareDialog(url, md))
        }
    }

    fun unshareCurrentSession() {
        val convId = uiState.value.activeConversation?.id ?: return
        viewModelScope.launch {
            repository.unshareConversation(convId)
            statusNotice.value = "UNSHARE // Revoked shared access and cleared session link."
        }
    }

    private fun exportCurrentSession() {
        val state = uiState.value
        val conv = state.activeConversation
        if (conv == null || state.messages.isEmpty()) {
            errorToast.value = "No messages in current session to export."
            return
        }
        val shareUrl = state.activeShareCode?.let { "https://opncd.ai/s/$it" }
        val md = repository.buildMarkdownTranscript(conv.title, state.messages, shareUrl)
        val safeTitle = conv.title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "session" }
        _events.tryEmit(ChatUiEvent.TriggerExportMarkdown("allchat_${safeTitle}.md", md))
    }

    // --- Sending & Branching ---

    fun sendMessage(rawPromptText: String) {
        val state = uiState.value
        val trimmed = rawPromptText.trim()
        val attachment = pendingAttachmentUri.value
        if (trimmed.isEmpty() && attachment == null) return

        // 1. Intercept built-in /slash commands first!
        val matchedSlash = SlashCommandRegistry.matchExact(trimmed)
        if (matchedSlash != null && attachment == null) {
            val arg = trimmed.dropWhile { !it.isWhitespace() }.trim()
            runSlashCommand(matchedSlash, arg)
            return
        }

        if (isStreaming.value) return

        // 2. Intercept Custom Slash Commands (e.g. /fix, /explain, /json)
        var effectivePrompt = trimmed
        if (trimmed.startsWith("/")) {
            val token = trimmed.takeWhile { !it.isWhitespace() }
            val matchedCustom = state.customSlashCommands.firstOrNull {
                it.trigger.equals(token, ignoreCase = true)
            }
            if (matchedCustom != null) {
                val arg = trimmed.drop(token.length).trim()
                if (arg.isEmpty() && attachment == null) {
                    statusNotice.value = "CMD // Type ${matchedCustom.trigger} followed by your text to expand."
                    return
                }
                effectivePrompt = matchedCustom.expand(arg)
            }
        }

        // 3. Parse optional leading @Persona or /Persona handle
        var effectivePersona = state.activePersona
        var effectiveModel = state.selectedModel

        if (effectivePrompt.startsWith("@") && !effectivePrompt.startsWith("@http", ignoreCase = true)) {
            val token = effectivePrompt.substring(1).takeWhile { !it.isWhitespace() }
            val matched = state.personas.firstOrNull { it.handle.equals(token, ignoreCase = true) }
            if (matched != null) {
                selectPersona(matched)
                effectivePersona = matched
                matched.defaultModelDbId?.let { targetId ->
                    state.availableModels.firstOrNull { it.model.id == targetId }?.let {
                        effectiveModel = it
                    }
                }
                val remainder = effectivePrompt.drop(token.length + 1).trim()
                if (remainder.isEmpty() && attachment == null) {
                    statusNotice.value = "PERSONA // Switched to @${matched.handle}."
                    return
                }
                effectivePrompt = remainder
            }
        }

        // 4. Smart Auto-Router resolution when AUTO mode is active
        var autoRouteLabel: String? = null
        if (state.autoRouteEnabled) {
            val historyChars = state.messages.sumOf { it.content.length }
            val (routedModel, reason) = repository.resolveAutoRouteModel(
                promptText = effectivePrompt,
                hasImageAttachment = attachment != null,
                historyCharLength = historyChars,
                availableModels = state.availableModels,
                config = state.autoRouterConfig,
                fallback = effectiveModel
            )
            if (routedModel != null) {
                effectiveModel = routedModel
                autoRouteLabel = reason
            }
        }

        val model = effectiveModel
        if (model == null) {
            errorToast.value = "Configure a Text Chat model in Settings first (or run /connect)."
            return
        }

        val currentLeafId = state.branchNodes.lastOrNull()?.message?.id
        val quoted = quotedMessage.value
        val docCtx = pendingDocumentContext.value
        val finalPrompt = buildString {
            if (quoted != null) {
                val preview = quoted.content.replace("\n", " ").trim().take(240)
                appendLine("> Quoting: \"$preview\"")
                appendLine()
            }
            append(effectivePrompt)
            if (docCtx != null) {
                appendLine()
                appendLine()
                appendLine("--- SHARED DOCUMENT: ${docCtx.first} ---")
                appendLine(docCtx.second)
            }
        }.trim()

        val options = buildChatOptions(effectivePersona, state.agentsMd, state.requestParams, state.toolsEnabled)
        val searchOn = state.webSearchEnabled
        val shouldAutoExtractMemory = state.autoExtractMemories
        pendingAttachmentUri.value = null
        quotedMessage.value = null
        pendingDocumentContext.value = null
        clearNotices()
        if (autoRouteLabel != null) {
            statusNotice.value = "AUTO-ROUTER // Selected ${model.model.displayName} ($autoRouteLabel)."
        }

        activeStreamJob = viewModelScope.launch {
            isStreaming.value = true
            try {
                val convId = activeConversationId.value
                    ?: repository.createConversation(
                        title = effectivePrompt.take(40).ifBlank { "Image Chat" },
                        modelDbId = model.model.id,
                        personaId = effectivePersona?.id
                    ).also { activeConversationId.value = it }

                redoStackByConv.update { it - convId }

                repository.sendChatMessage(
                    conversationId = convId,
                    promptText = finalPrompt,
                    attachmentLocalUri = attachment,
                    selectableModel = model,
                    parentMessageId = currentLeafId,
                    options = options,
                    enableWebSearch = searchOn,
                    autoRouteLabel = autoRouteLabel,
                    onGroundingStatus = { status ->
                        statusNotice.value = status
                    },
                    onBranchCreated = { parentKey, childId ->
                        selectedBranchByParent.update { it + (parentKey to childId) }
                    }
                )

                if (shouldAutoExtractMemory && effectivePrompt.length >= 18) {
                    val lastReply = uiState.value.messages.lastOrNull { it.role == "ASSISTANT" }?.content.orEmpty()
                    if (lastReply.isNotBlank()) {
                        viewModelScope.launch {
                            val savedCount = repository.extractMemoriesFromTurn(effectivePrompt, lastReply, model)
                            if (savedCount > 0) {
                                statusNotice.value = "MEMORY // Auto-saved $savedCount preference${if (savedCount > 1) "s" else ""} to Memory Bank."
                            }
                        }
                    }
                }
            } finally {
                isStreaming.value = false
            }
        }
    }

    fun editMessageAndFork(originalMessage: MessageEntity, newContent: String) {
        val clean = newContent.trim()
        if (clean.isEmpty() || isStreaming.value) return
        val state = uiState.value
        val model = state.selectedModel ?: run {
            errorToast.value = "Configure a Text Chat model in Settings first."
            return
        }
        val convId = activeConversationId.value ?: return
        val options = buildChatOptions(state.activePersona, state.agentsMd, state.requestParams, state.toolsEnabled)

        activeStreamJob = viewModelScope.launch {
            isStreaming.value = true
            try {
                repository.editAndForkUserMessage(
                    conversationId = convId,
                    originalMessage = originalMessage,
                    newContent = clean,
                    selectableModel = model,
                    options = options,
                    enableWebSearch = state.webSearchEnabled,
                    onBranchCreated = { parentKey, childId ->
                        selectedBranchByParent.update { it + (parentKey to childId) }
                    }
                )
            } finally {
                isStreaming.value = false
            }
        }
    }

    fun regenerateAsBranch(assistantMessage: MessageEntity) {
        val state = uiState.value
        val model = state.selectedModel ?: return
        val convId = activeConversationId.value ?: return
        if (isStreaming.value) return
        val options = buildChatOptions(state.activePersona, state.agentsMd, state.requestParams, state.toolsEnabled)

        activeStreamJob = viewModelScope.launch {
            isStreaming.value = true
            try {
                repository.regenerateAsBranch(
                    conversationId = convId,
                    assistantMessage = assistantMessage,
                    selectableModel = model,
                    options = options,
                    enableWebSearch = state.webSearchEnabled,
                    onBranchCreated = { parentKey, childId ->
                        selectedBranchByParent.update { it + (parentKey to childId) }
                    }
                )
            } finally {
                isStreaming.value = false
            }
        }
    }

    fun stopGeneration() {
        activeStreamJob?.cancel()
        activeStreamJob = null
        isStreaming.value = false
    }

    private fun buildChatOptions(
        persona: PersonaInfo?,
        agentsMd: String,
        params: RequestParamsOverride,
        enableTools: Boolean
    ): ChatOptions {
        val combinedSystem = buildList {
            if (agentsMd.isNotBlank()) add("# Project Instructions (AGENTS.md)\n$agentsMd")
            if (!persona?.systemPrompt.isNullOrBlank()) add(persona!!.systemPrompt)
        }.joinToString("\n\n").takeIf { it.isNotBlank() }

        return ChatOptions(
            systemPrompt = combinedSystem,
            temperature = params.temperature ?: persona?.temperature,
            topP = params.topP,
            maxTokens = params.maxTokens ?: persona?.maxTokens,
            jsonMode = params.jsonMode,
            enableTools = enableTools
        )
    }

    private data class PowerFeaturesBundle(
        val webSearchEnabled: Boolean,
        val searchProvider: WebSearchProvider,
        val hasSearchApiKey: Boolean,
        val requestParams: RequestParamsOverride,
        val autoRouteEnabled: Boolean
    )

    private data class ModelPersonaBundle(
        val models: List<SelectableModel>,
        val fallbackModelId: Long?,
        val personas: List<PersonaInfo>,
        val fallbackPersonaId: Long?,
        val currency: DisplayCurrency
    )

    private data class CommandPrefsBundle(
        val theme: SwissThemePreset,
        val showDetails: Boolean,
        val showThinking: Boolean,
        val agentsMd: String,
        val sharedSessions: Map<Long, String>
    )

    private data class McpAndUndoBundle(
        val servers: List<McpServerConfig>,
        val results: Map<String, McpCheckResult>,
        val checking: Set<String>,
        val genAgents: Boolean,
        val redoMap: Map<Long, List<List<MessageEntity>>>
    )

    private data class MemoryToolsBundle(
        val toolsEnabled: Boolean,
        val memories: List<UserMemoryInfo>,
        val autoExtractMemories: Boolean,
        val quotedMessage: MessageEntity?,
        val miniModeEnabled: Boolean
    )

    private data class StudyMapBundle(
        val pendingDocumentTitle: String?,
        val isGeneratingStudy: Boolean,
        val activeStudyPack: StudyPack?,
        val isGeneratingMindMap: Boolean,
        val activeMindMap: MindMapNode?
    )
}
