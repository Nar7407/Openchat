package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.repository.AllChatRepository
import com.example.domain.model.AssistantRoutine
import com.example.domain.model.AutoRouterConfig
import com.example.domain.model.BranchMessageNode
import com.example.domain.model.CanvasVersionInfo
import com.example.domain.model.ChatOptions
import com.example.domain.model.ClipboardPromptHint
import com.example.domain.model.ClipboardSnippetInfo
import com.example.domain.model.ConversationTree
import com.example.domain.model.CustomAppEntry
import com.example.domain.model.CustomSlashCommand
import com.example.domain.model.DecisionMatrixData
import com.example.domain.model.DisplayCurrency
import com.example.domain.model.ExpenseRecord
import com.example.domain.model.FactCheckClaim
import com.example.domain.model.KnowledgeDocInfo
import com.example.domain.model.McpCheckResult
import com.example.domain.model.McpServerConfig
import com.example.domain.model.MemoryProfile
import com.example.domain.model.MindMapNode
import com.example.domain.model.ModelCapability
import com.example.domain.model.OutputFormatMode
import com.example.domain.model.PersonaInfo
import com.example.domain.model.QuickContact
import com.example.domain.model.RequestParamsOverride
import com.example.domain.model.SelectableModel
import com.example.domain.model.SlashCommandDef
import com.example.domain.model.SlashCommandId
import com.example.domain.model.SlashCommandRegistry
import com.example.domain.model.CliCommandSpec
import com.example.domain.model.CliFlagToken
import com.example.domain.model.DailyBudgetConfig
import com.example.domain.model.FilledFormResult
import com.example.domain.model.NegativeConstraint
import com.example.domain.model.ReasoningEffort
import com.example.domain.model.ReplyMorphStyle
import com.example.domain.model.SmartContextSnapshot
import com.example.domain.model.SmartZone
import com.example.domain.model.SpotlightResult
import com.example.domain.model.StudyPack
import com.example.domain.model.ThreadCheckpoint
import com.example.domain.model.TopicCluster
import com.example.domain.model.UserMemoryInfo
import com.example.domain.model.VoiceFormTemplate
import com.example.domain.model.VoiceNoteInfo
import com.example.domain.model.WatchlistItemInfo
import com.example.domain.model.WebSearchProvider
import com.example.domain.model.WorkspaceFileEntry
import com.example.ui.components.PowerToolAction
import com.example.ui.theme.SwissThemePreset
import com.example.util.DeviceAction
import com.example.util.DeviceActionEngine
import com.example.util.LocalWorkspaceManager
import com.example.util.PromptCompressor
import com.example.util.SmartContextEngine
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
    data object OpenVoiceNotes : ChatUiEvent
    data class SpeakRoutineText(val text: String) : ChatUiEvent
    data object StartVoiceDictation : ChatUiEvent
    data object LaunchCameraVision : ChatUiEvent
    data class OpenPromptEditor(val initialText: String) : ChatUiEvent
    data class OpenShareDialog(val shareUrl: String, val markdown: String) : ChatUiEvent
    data class OpenExportStudio(val title: String, val markdown: String) : ChatUiEvent
    data class TriggerExportMarkdown(val suggestedFileName: String, val markdown: String) : ChatUiEvent
    data class OpenQrDialog(val initialPayload: String) : ChatUiEvent
    data class OpenPlotter(val initialExpression: String) : ChatUiEvent
    data class OpenDecisionMatrix(val initialPrompt: String) : ChatUiEvent
    data class OpenCanvas(val initialContent: String = "") : ChatUiEvent
    data object OpenExpenses : ChatUiEvent
    data object OpenWatchlist : ChatUiEvent
    data object OpenBleInspector : ChatUiEvent
    data object OpenCommuteHud : ChatUiEvent
    data object OpenInterpreter : ChatUiEvent
    data object OpenSpotlight : ChatUiEvent
    data class OpenRedactor(val initialText: String = "") : ChatUiEvent
    data object OpenCsvExplorer : ChatUiEvent
    data object OpenLocalWorkspace : ChatUiEvent
    data object OpenVideoFrameExtractor : ChatUiEvent
    data object OpenColorPalette : ChatUiEvent
    data object OpenClipboardVault : ChatUiEvent
    data object OpenVoiceFormFiller : ChatUiEvent
    data class OpenCliBuilder(val initialQuery: String = "") : ChatUiEvent
    data object OpenToolkitHub : ChatUiEvent
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
    val autoExtractMemories: Boolean = true,
    val memoryInjectionEnabled: Boolean = true,
    val memoryUpdatedToast: String? = null,
    val activeMemoryProfile: MemoryProfile = MemoryProfile.ALL,
    val outputFormatMode: OutputFormatMode = OutputFormatMode.AUTO,
    val semanticAutoAttach: Boolean = false,
    val fastUtilityModelId: Long? = null,
    val pinnedModelIds: List<Long> = emptyList(),
    val modelLatencyMs: Map<Long, Long> = emptyMap(),
    val quotedMessage: MessageEntity? = null,
    val miniModeEnabled: Boolean = false,
    val pendingDocumentTitle: String? = null,
    val isGeneratingStudy: Boolean = false,
    val activeStudyPack: StudyPack? = null,
    val isGeneratingMindMap: Boolean = false,
    val activeMindMap: MindMapNode? = null,
    val followUpSuggestions: List<String> = emptyList(),
    val clipboardHint: ClipboardPromptHint? = null,
    val batchDocumentTitles: List<String> = emptyList(),
    val liveCamEnabled: Boolean = false,
    val liveVoiceActive: Boolean = false,
    val pendingDeviceAction: DeviceAction? = null,
    val queuedDeviceActions: List<DeviceAction> = emptyList(),
    val quickContacts: List<QuickContact> = emptyList(),
    val customApps: List<CustomAppEntry> = emptyList(),
    val autoDialWebhookUrl: String = "",
    val routines: List<AssistantRoutine> = emptyList(),
    val voiceNotes: List<VoiceNoteInfo> = emptyList(),
    val isSavingNote: Boolean = false,
    val pendingAttachmentUri: String? = null,
    val isStreaming: Boolean = false,
    val statusNotice: String? = null,
    val errorToast: String? = null,
    val lastFailedPrompt: String? = null,
    val tokenDietEnabled: Boolean = false,
    val guidedModeEnabled: Boolean = false,
    val verifyFixEnabled: Boolean = false,
    val smartContext: SmartContextSnapshot = SmartContextSnapshot(),
    val smartZones: List<SmartZone> = emptyList(),
    val timeProfilesEnabled: Boolean = true,
    val autoEcoMode: Boolean = true,
    val wakePhraseEnabled: Boolean = false,
    val ambientWhisperEnabled: Boolean = false,
    val cooldownMap: Map<Long, Long> = emptyMap(),
    val clipboardHistory: List<ClipboardSnippetInfo> = emptyList(),
    val localWorkspaceUri: String? = null,
    val workspaceFiles: List<WorkspaceFileEntry> = emptyList(),
    val factCheckByMessageId: Map<Long, List<FactCheckClaim>> = emptyMap(),
    val checkingFactMessageIds: Set<Long> = emptySet(),
    val activeDecisionMatrix: DecisionMatrixData? = null,
    val isBuildingMatrix: Boolean = false,
    val expenses: List<ExpenseRecord> = emptyList(),
    val watchlist: List<WatchlistItemInfo> = emptyList(),
    val canvasVersions: List<CanvasVersionInfo> = emptyList(),
    val activeConstraints: Set<NegativeConstraint> = emptySet(),
    val isEnhancingPrompt: Boolean = false,
    val isExpandingMindMapNode: Boolean = false,
    val ghostModeEnabled: Boolean = false,
    val ghostMessages: List<MessageEntity> = emptyList(),
    val dailyBudgetConfig: DailyBudgetConfig = DailyBudgetConfig(),
    val todaySpendUsd: Double = 0.0,
    val forceFlagshipOverride: Boolean = false,
    val voiceFormTemplates: List<VoiceFormTemplate> = emptyList(),
    val threadCheckpoints: List<ThreadCheckpoint> = emptyList(),
    val summarizingCheckpointMsgId: Long? = null,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.BALANCED,
    val morphingMessageId: Long? = null
) {
    val messages: List<MessageEntity>
        get() = if (ghostModeEnabled) ghostMessages else branchNodes.map { it.message }
    val budgetUsageRatio: Double
        get() = if (dailyBudgetConfig.dailyCapUsd > 0.0) todaySpendUsd / dailyBudgetConfig.dailyCapUsd else 0.0
    val isBudgetWarningActive: Boolean
        get() = dailyBudgetConfig.dailyCapUsd > 0.0 && budgetUsageRatio >= 0.80
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
    private val toolsEnabled = MutableStateFlow(true)
    private val requestParams = MutableStateFlow(RequestParamsOverride())
    private val quotedMessage = MutableStateFlow<MessageEntity?>(null)
    private val miniModeEnabled = MutableStateFlow(false)
    private val pendingDocumentContext = MutableStateFlow<Pair<String, String>?>(null)
    private val isGeneratingStudy = MutableStateFlow(false)
    private val activeStudyPack = MutableStateFlow<StudyPack?>(null)
    private val isGeneratingMindMap = MutableStateFlow(false)
    private val activeMindMap = MutableStateFlow<MindMapNode?>(null)
    private val followUpSuggestions = MutableStateFlow<List<String>>(emptyList())
    private val clipboardHint = MutableStateFlow<ClipboardPromptHint?>(null)
    private var dismissedClipboardHash: Int? = null
    private val pendingBatchDocuments = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val liveCamEnabled = MutableStateFlow(false)
    private val liveVoiceActive = MutableStateFlow(false)
    private val queuedDeviceActions = MutableStateFlow<List<DeviceAction>>(emptyList())
    private val memoryInjectionEnabled = MutableStateFlow(true)
    private val memoryUpdatedToast = MutableStateFlow<String?>(null)
    private val outputFormatMode = MutableStateFlow(OutputFormatMode.AUTO)
    private val lastFailedPrompt = MutableStateFlow<String?>(null)
    private val isSavingNote = MutableStateFlow(false)
    private val tokenDietEnabled = MutableStateFlow(false)
    private val guidedModeEnabled = MutableStateFlow(false)
    private val verifyFixEnabled = MutableStateFlow(false)
    private val smartContext = MutableStateFlow(SmartContextSnapshot())
    private val workspaceFiles = MutableStateFlow<List<WorkspaceFileEntry>>(emptyList())
    private val factCheckByMessageId = MutableStateFlow<Map<Long, List<FactCheckClaim>>>(emptyMap())
    private val checkingFactMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    private val activeDecisionMatrix = MutableStateFlow<DecisionMatrixData?>(null)
    private val isBuildingMatrix = MutableStateFlow(false)
    private val activeConstraints = MutableStateFlow<Set<NegativeConstraint>>(emptySet())
    private val isEnhancingPrompt = MutableStateFlow(false)
    private val isExpandingMindMapNode = MutableStateFlow(false)
    private val ghostModeEnabled = MutableStateFlow(false)
    private val ghostMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val forceFlagshipOverride = MutableStateFlow(false)
    private val summarizingCheckpointMsgId = MutableStateFlow<Long?>(null)
    private val reasoningEffort = MutableStateFlow(ReasoningEffort.BALANCED)
    private val morphingMessageId = MutableStateFlow<Long?>(null)

    private val wave4UxBundleFlow = combine(
        activeConstraints,
        isEnhancingPrompt,
        isExpandingMindMapNode,
        ghostModeEnabled,
        ghostMessages
    ) { constraints, enhancing, expandingMap, ghostOn, ghostList ->
        Wave4UxBundle(constraints, enhancing, expandingMap, ghostOn, ghostList)
    }

    private val wave4BudgetAndMetaFlow = combine(
        repository.dailyBudgetConfigFlow,
        repository.observeTodaySpendUsd(),
        forceFlagshipOverride,
        repository.voiceFormTemplatesFlow,
        combine(repository.threadCheckpointsFlow, summarizingCheckpointMsgId) { map, busyId -> map to busyId }
    ) { budgetCfg, todaySpend, flagshipOverride, formTemplates, (cpMap, cpBusyId) ->
        Wave4BudgetMetaBundle(budgetCfg, todaySpend, flagshipOverride, formTemplates, cpMap, cpBusyId)
    }

    private val conversationModesBundleFlow = combine(
        tokenDietEnabled,
        guidedModeEnabled,
        verifyFixEnabled,
        smartContext,
        repository.cooldownSnapshotFlow
    ) { diet, guided, verify, ctx, cooldowns ->
        ConversationModesBundle(diet, guided, verify, ctx, cooldowns)
    }

    private val smartPrefsBundleFlow = combine(
        repository.smartZonesFlow,
        repository.timeProfilesEnabledFlow,
        repository.autoEcoModeFlow,
        repository.wakePhraseEnabledFlow,
        repository.ambientWhisperEnabledFlow
    ) { zones, timeProf, eco, wake, whisper ->
        SmartPrefsBundle(zones, timeProf, eco, wake, whisper)
    }

    private val hubsBundleFlow = combine(
        repository.clipboardFlow,
        repository.localWorkspaceUriFlow,
        workspaceFiles,
        repository.expensesFlow,
        combine(repository.watchlistFlow, repository.canvasVersionsFlow) { w, c -> w to c }
    ) { clips, wsUri, wsFiles, exps, (watch, canvas) ->
        HubsBundle(clips, wsUri, wsFiles, exps, watch, canvas)
    }

    private val verificationBundleFlow = combine(
        factCheckByMessageId,
        checkingFactMessageIds,
        activeDecisionMatrix,
        isBuildingMatrix
    ) { facts, checking, matrix, building ->
        VerificationBundle(facts, checking, matrix, building)
    }

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

    private val advancedStudioBundleFlow = combine(
        followUpSuggestions,
        clipboardHint,
        pendingBatchDocuments,
        liveCamEnabled,
        liveVoiceActive
    ) { followUps, clipHint, batchDocs, liveCam, liveVoice ->
        AdvancedStudioBundle(
            followUpSuggestions = followUps,
            clipboardHint = clipHint,
            batchDocumentTitles = batchDocs.map { it.first },
            liveCamEnabled = liveCam,
            liveVoiceActive = liveVoice
        )
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
    }.combine(advancedStudioBundleFlow) { state, adv ->
        state.copy(
            followUpSuggestions = adv.followUpSuggestions,
            clipboardHint = adv.clipboardHint,
            batchDocumentTitles = adv.batchDocumentTitles,
            liveCamEnabled = adv.liveCamEnabled,
            liveVoiceActive = adv.liveVoiceActive
        )
    }.combine(pendingAttachmentUri) { state, att ->
        state.copy(pendingAttachmentUri = att)
    }.combine(
        combine(
            queuedDeviceActions,
            repository.quickContactsFlow,
            repository.customAppsFlow,
            repository.autoDialWebhookUrlFlow
        ) { devQueue, contacts, apps, dialWebhook ->
            DeviceBridgeBundle(devQueue, contacts, apps, dialWebhook)
        }
    ) { state, bundle ->
        state.copy(
            pendingDeviceAction = bundle.queuedDeviceActions.firstOrNull(),
            queuedDeviceActions = bundle.queuedDeviceActions,
            quickContacts = bundle.quickContacts,
            customApps = bundle.customApps,
            autoDialWebhookUrl = bundle.autoDialWebhookUrl
        )
    }.combine(
        combine(
            repository.routinesFlow,
            repository.voiceNotesFlow,
            isSavingNote
        ) { routines, notes, savingNote -> Triple(routines, notes, savingNote) }
    ) { state, (routines, notes, savingNote) ->
        state.copy(
            routines = routines,
            voiceNotes = notes,
            isSavingNote = savingNote
        )
    }.combine(isStreaming) { state, streaming ->
        state.copy(isStreaming = streaming)
    }.combine(
        combine(statusNotice, errorToast, lastFailedPrompt) { notice, err, failed -> Triple(notice, err, failed) }
    ) { state, (notice, err, failed) ->
        state.copy(statusNotice = notice, errorToast = err, lastFailedPrompt = failed)
    }.combine(conversationModesBundleFlow) { state, cm ->
        state.copy(
            tokenDietEnabled = cm.tokenDietEnabled,
            guidedModeEnabled = cm.guidedModeEnabled,
            verifyFixEnabled = cm.verifyFixEnabled,
            smartContext = cm.smartContext,
            cooldownMap = cm.cooldownMap
        )
    }.combine(smartPrefsBundleFlow) { state, sp ->
        state.copy(
            smartZones = sp.smartZones,
            timeProfilesEnabled = sp.timeProfilesEnabled,
            autoEcoMode = sp.autoEcoMode,
            wakePhraseEnabled = sp.wakePhraseEnabled,
            ambientWhisperEnabled = sp.ambientWhisperEnabled
        )
    }.combine(hubsBundleFlow) { state, hb ->
        state.copy(
            clipboardHistory = hb.clipboardHistory,
            localWorkspaceUri = hb.localWorkspaceUri,
            workspaceFiles = hb.workspaceFiles,
            expenses = hb.expenses,
            watchlist = hb.watchlist,
            canvasVersions = hb.canvasVersions
        )
    }.combine(verificationBundleFlow) { state, vb ->
        state.copy(
            factCheckByMessageId = vb.factCheckByMessageId,
            checkingFactMessageIds = vb.checkingFactMessageIds,
            activeDecisionMatrix = vb.activeDecisionMatrix,
            isBuildingMatrix = vb.isBuildingMatrix
        )
    }.combine(wave4UxBundleFlow) { state, w4 ->
        state.copy(
            activeConstraints = w4.activeConstraints,
            isEnhancingPrompt = w4.isEnhancingPrompt,
            isExpandingMindMapNode = w4.isExpandingMindMapNode,
            ghostModeEnabled = w4.ghostModeEnabled,
            ghostMessages = w4.ghostMessages
        )
    }.combine(wave4BudgetAndMetaFlow) { state, bm ->
        val convId = state.activeConversation?.id
        val convCheckpoints = if (convId != null) bm.checkpointsByConv[convId].orEmpty() else emptyList()
        state.copy(
            dailyBudgetConfig = bm.budgetConfig,
            todaySpendUsd = bm.todaySpendUsd,
            forceFlagshipOverride = bm.forceFlagshipOverride,
            voiceFormTemplates = bm.voiceFormTemplates,
            threadCheckpoints = convCheckpoints,
            summarizingCheckpointMsgId = bm.summarizingCheckpointMsgId
        )
    }.combine(
        combine(reasoningEffort, morphingMessageId) { effort, morphId -> effort to morphId }
    ) { state, (effort, morphId) ->
        state.copy(
            reasoningEffort = effort,
            morphingMessageId = morphId
        )
    }.combine(
        combine(
            memoryInjectionEnabled,
            memoryUpdatedToast,
            repository.pinnedModelIdsFlow,
            repository.modelLatencyMsFlow
        ) { memOn, memToast, pinnedIds, latencies ->
            NavigationAndMemoryBundle(memOn, memToast, pinnedIds, latencies)
        }
    ) { state, navMem ->
        state.copy(
            memoryInjectionEnabled = navMem.memoryInjectionEnabled,
            memoryUpdatedToast = navMem.memoryUpdatedToast,
            pinnedModelIds = navMem.pinnedModelIds,
            modelLatencyMs = navMem.modelLatencyMs
        )
    }.combine(
        combine(
            repository.activeMemoryProfileFlow,
            outputFormatMode,
            repository.semanticAutoAttachFlow,
            repository.fastUtilityModelIdFlow
        ) { profile, fmtMode, semAuto, fastId ->
            ProfileAndFormatBundle(profile, fmtMode, semAuto, fastId)
        }
    ) { state, pf ->
        state.copy(
            activeMemoryProfile = pf.activeMemoryProfile,
            outputFormatMode = pf.outputFormatMode,
            semanticAutoAttach = pf.semanticAutoAttach,
            fastUtilityModelId = pf.fastUtilityModelId
        )
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
            repository.activeModelDbIdFlow.collect { persistedModelId ->
                if (persistedModelId != null && fallbackSelectedModelId.value != persistedModelId) {
                    fallbackSelectedModelId.value = persistedModelId
                }
            }
        }
        viewModelScope.launch {
            combine(
                repository.conversationsFlow,
                repository.activeConversationIdFlow
            ) { list, persistedId -> list to persistedId }.collect { (list, persistedId) ->
                if (persistedId != null && list.any { it.id == persistedId }) {
                    if (activeConversationId.value != persistedId) {
                        selectedBranchByParent.value = emptyMap()
                        activeConversationId.value = persistedId
                    }
                } else if (activeConversationId.value == null && list.isNotEmpty()) {
                    val firstId = list.first().id
                    activeConversationId.value = firstId
                    repository.setActiveConversationId(firstId)
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
        followUpSuggestions.value = emptyList()
        activeConversationId.value = conversationId
        viewModelScope.launch {
            repository.setActiveConversationId(conversationId)
        }
    }

    fun createNewChat() {
        stopGeneration()
        selectedBranchByParent.value = emptyMap()
        followUpSuggestions.value = emptyList()
        viewModelScope.launch {
            val modelId = uiState.value.selectedModel?.model?.id
            val personaId = uiState.value.activePersona?.id
            val newId = repository.createConversation("New Chat", modelId, personaId)
            activeConversationId.value = newId
            repository.setActiveConversationId(newId)
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
            viewModelScope.launch {
                repository.setActiveConversationId(null)
            }
        }
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
        }
    }

    fun selectModel(selectableModel: SelectableModel) {
        fallbackSelectedModelId.value = selectableModel.model.id
        viewModelScope.launch {
            repository.setAutoRouteEnabled(false)
            repository.setActiveModelDbId(selectableModel.model.id)
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

    fun setLiveCamEnabled(enabled: Boolean) {
        liveCamEnabled.value = enabled
        if (enabled) {
            statusNotice.value = "LIVE CAM // Viewfinder active. Every prompt auto-captures the current frame."
        }
    }

    fun openLiveVoice() {
        if (uiState.value.selectedModel == null) {
            errorToast.value = "Configure a Text Chat model in Settings first to use Live Voice."
            return
        }
        liveVoiceActive.value = true
    }

    fun closeLiveVoice() {
        liveVoiceActive.value = false
    }

    suspend fun streamLiveVoiceTurn(
        history: List<Pair<String, String>>,
        userText: String,
        onPartial: (String) -> Unit,
        onSentence: (String) -> Unit
    ): String {
        val model = uiState.value.selectedModel
            ?: throw IllegalStateException("Select a Text Chat model first.")
        return repository.streamLiveVoiceReply(
            history = history,
            userText = userText,
            selectableModel = model,
            onPartialFullText = onPartial,
            onSentenceReady = onSentence
        )
    }

    fun attachBitmapAndSelectVision(bitmap: Bitmap, label: String = "Region Snip") {
        viewModelScope.launch {
            runCatching {
                repository.saveBitmapAsAttachment(bitmap, prefix = "snip")
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
                        statusNotice.value = "SNIP // $label attached & switched to Vision model: ${bestVision.model.displayName}."
                    } else {
                        statusNotice.value = "SNIP // $label attached. Select a Vision-capable model to analyze."
                    }
                } else {
                    statusNotice.value = "SNIP // $label attached to prompt."
                }
            }.onFailure { err ->
                errorToast.value = err.message ?: "Failed to attach cropped image."
            }
        }
    }

    fun sendMessageWithOptionalLiveFrame(rawPromptText: String, liveFrameBitmap: Bitmap?) {
        if (liveFrameBitmap == null || pendingAttachmentUri.value != null) {
            sendMessage(rawPromptText)
            return
        }
        viewModelScope.launch {
            val savedUri = runCatching {
                repository.saveBitmapAsAttachment(liveFrameBitmap, prefix = "livecam")
            }.getOrNull()
            if (savedUri != null) {
                pendingAttachmentUri.value = savedUri
                val state = uiState.value
                if (state.selectedModel?.model?.supportsImageInput != true) {
                    val bestVision = state.autoRouterConfig.visionModelId?.let { id ->
                        state.availableModels.firstOrNull { it.model.id == id }
                    } ?: state.availableModels.firstOrNull { it.model.supportsImageInput }
                    if (bestVision != null) {
                        selectModel(bestVision)
                    }
                }
            }
            sendMessage(rawPromptText.ifBlank { "Describe and answer based on what you see in this live camera frame." })
        }
    }

    fun attachBatchDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            statusNotice.value = "BATCH // Extracting ${uris.size.coerceAtMost(10)} document(s)…"
            runCatching {
                repository.extractMultipleDocuments(uris)
            }.onSuccess { docs ->
                if (docs.isEmpty()) {
                    errorToast.value = "Could not extract text from the selected files."
                } else {
                    pendingBatchDocuments.value = docs
                    statusNotice.value = "BATCH // Loaded ${docs.size} document${if (docs.size > 1) "s" else ""} for cross-doc Q&A."
                }
            }.onFailure { err ->
                errorToast.value = err.message ?: "Batch document extraction failed."
            }
        }
    }

    fun clearBatchDocuments() {
        pendingBatchDocuments.value = emptyList()
    }

    fun inspectClipboardOnResume(clipboardText: String?) {
        val trimmed = clipboardText?.trim().orEmpty()
        if (trimmed.length < 12) return
        val hash = trimmed.hashCode()
        if (hash == dismissedClipboardHash) return
        if (clipboardHint.value?.hash == hash) return

        val kindLabel = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> "LINK"
            trimmed.contains("Exception") ||
                trimmed.contains("Error:") ||
                trimmed.contains("Traceback") ||
                trimmed.contains("at com.") ||
                trimmed.contains("at java.") ||
                trimmed.contains("NullPointer") -> "ERROR TRACE"
            else -> "TEXT"
        }
        val preview = trimmed.replace(Regex("\\s+"), " ").take(96)
        clipboardHint.value = ClipboardPromptHint(
            hash = hash,
            rawText = trimmed,
            preview = preview,
            kindLabel = kindLabel
        )
        viewModelScope.launch {
            repository.recordClipboardSnippet(trimmed)
        }
    }

    fun dismissClipboardHint() {
        clipboardHint.value?.let { dismissedClipboardHash = it.hash }
        clipboardHint.value = null
    }

    // --- Wave 3B: Smart Triggers, Token Diet, Guided Mode, Verify & Fix, Productivity Hubs ---

    fun toggleTokenDiet() {
        val next = !tokenDietEnabled.value
        tokenDietEnabled.value = next
        statusNotice.value = if (next) {
            "TOKEN DIET // Prompt compression ON (strips filler words & collapses code whitespace)."
        } else {
            "TOKEN DIET // Prompt compression OFF."
        }
    }

    fun toggleGuidedMode() {
        val next = !guidedModeEnabled.value
        guidedModeEnabled.value = next
        statusNotice.value = if (next) {
            "GUIDED MODE // AI will ask 2-3 quick clarifying questions before big plans."
        } else {
            "GUIDED MODE // Direct answer mode restored."
        }
    }

    fun toggleVerifyFix() {
        val next = !verifyFixEnabled.value
        verifyFixEnabled.value = next
        statusNotice.value = if (next) {
            "VERIFY & FIX // Automatic coding/math self-correction pass armed."
        } else {
            "VERIFY & FIX // Self-correction pass OFF."
        }
    }

    fun refreshSmartContext(context: Context, ambientDb: Int? = null) {
        val state = uiState.value
        val snapshot = SmartContextEngine.captureSnapshot(
            context = context,
            savedZones = state.smartZones,
            timeProfilesEnabled = state.timeProfilesEnabled,
            autoEcoEnabled = state.autoEcoMode,
            ambientDb = ambientDb
        )
        smartContext.value = snapshot
    }

    fun verifyMessageClaims(message: MessageEntity) {
        if (checkingFactMessageIds.value.contains(message.id)) return
        viewModelScope.launch {
            checkingFactMessageIds.update { it + message.id }
            statusNotice.value = "FACT-CHECK // Verifying dates, numbers & claims…"
            runCatching {
                repository.verifyAnswerClaims(message.content, uiState.value.selectedModel)
            }.onSuccess { claims ->
                factCheckByMessageId.update { it + (message.id to claims) }
                statusNotice.value = "FACT-CHECK // Checked ${claims.size} claim${if (claims.size == 1) "" else "s"}."
            }.onFailure { err ->
                errorToast.value = err.message ?: "Fact-check verification failed."
            }
            checkingFactMessageIds.update { it - message.id }
        }
    }

    fun buildDecisionMatrix(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || isBuildingMatrix.value) return
        viewModelScope.launch {
            isBuildingMatrix.value = true
            statusNotice.value = "COMPARE // Building weighted Decision Matrix…"
            runCatching {
                repository.generateDecisionMatrix(clean, uiState.value.selectedModel)
            }.onSuccess { data ->
                activeDecisionMatrix.value = data
                statusNotice.value = null
            }.onFailure { err ->
                errorToast.value = err.message ?: "Could not build comparison matrix."
            }
            isBuildingMatrix.value = false
        }
    }

    fun dismissDecisionMatrix() {
        activeDecisionMatrix.value = null
    }

    fun setLocalWorkspaceTree(context: Context, treeUri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val uriStr = treeUri.toString()
            repository.setLocalWorkspaceUri(uriStr)
            refreshWorkspaceFiles(context, uriStr)
            statusNotice.value = "WORKSPACE // Mounted local folder for @file context & write-back."
        }
    }

    fun refreshWorkspaceFiles(context: Context, overrideUri: String? = null) {
        val uriStr = overrideUri ?: uiState.value.localWorkspaceUri ?: return
        viewModelScope.launch {
            val files = LocalWorkspaceManager.scanWorkspaceFiles(context, uriStr)
            workspaceFiles.value = files
        }
    }

    fun recordClipboard(rawText: String) {
        viewModelScope.launch {
            repository.recordClipboardSnippet(rawText)
        }
    }

    fun deleteClipboardSnippet(id: Long) {
        viewModelScope.launch {
            repository.deleteClipboardSnippet(id)
        }
    }

    fun clearClipboardVault() {
        viewModelScope.launch {
            repository.clearClipboardVault()
            statusNotice.value = "CLIPBOARD // Cleared local snippet vault."
        }
    }

    fun saveExpense(record: ExpenseRecord) {
        viewModelScope.launch {
            repository.saveExpense(record)
            statusNotice.value = "EXPENSES // Saved ${record.merchant} (${record.currency} ${record.amount})."
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            repository.deleteExpense(id)
        }
    }

    suspend fun scanReceiptBitmap(bitmap: Bitmap): Result<ExpenseRecord> = runCatching {
        repository.extractExpenseFromImage(bitmap, uiState.value.selectedModel)
    }

    fun saveWatchlistItem(title: String, queryOrUrl: String) {
        if (queryOrUrl.isBlank()) return
        viewModelScope.launch {
            repository.saveWatchlistItem(title, queryOrUrl)
            statusNotice.value = "WATCHLIST // Added \"${title.ifBlank { queryOrUrl.take(30) }}\"."
        }
    }

    fun deleteWatchlistItem(id: Long) {
        viewModelScope.launch {
            repository.deleteWatchlistItem(id)
        }
    }

    suspend fun checkWatchlistItem(item: WatchlistItemInfo): Result<WatchlistItemInfo> = runCatching {
        repository.checkWatchlistUpdate(item, uiState.value.selectedModel)
    }

    fun saveCanvasVersion(docTitle: String, content: String) {
        viewModelScope.launch {
            val ver = repository.saveCanvasVersion(docTitle, content)
            statusNotice.value = "CANVAS // Saved v${ver.versionNumber} (${ver.docTitle})."
        }
    }

    suspend fun editCanvasSelection(
        fullDoc: String,
        selectedBlock: String,
        instruction: String
    ): Result<String> = runCatching {
        repository.aiEditCanvasSelection(fullDoc, selectedBlock, instruction, uiState.value.selectedModel)
    }

    suspend fun spotlightSearch(query: String, filter: String): List<SpotlightResult> =
        repository.spotlightSearch(query, filter)

    suspend fun loadTopicClusters(): List<TopicCluster> =
        repository.clusterConversationsByTopic()

    suspend fun generateCleanSvg(description: String): Result<String> = runCatching {
        repository.generateCleanSvg(description, uiState.value.selectedModel)
    }

    suspend fun translateInterpreterUtterance(
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<String> = runCatching {
        repository.translateLiveUtterance(text, sourceLang, targetLang, uiState.value.selectedModel)
    }

    fun openToolkitHub() {
        _events.tryEmit(ChatUiEvent.OpenToolkitHub)
    }

    fun launchPowerTool(tool: PowerToolAction) {
        val lastAssistant = uiState.value.messages.lastOrNull { it.role == "ASSISTANT" }?.content.orEmpty()
        when (tool) {
            PowerToolAction.QR_GENERATOR -> _events.tryEmit(ChatUiEvent.OpenQrDialog(lastAssistant.take(300)))
            PowerToolAction.VIDEO_FRAME -> _events.tryEmit(ChatUiEvent.OpenVideoFrameExtractor)
            PowerToolAction.COLOR_PALETTE -> _events.tryEmit(ChatUiEvent.OpenColorPalette)
            PowerToolAction.PRIVACY_REDACTOR -> _events.tryEmit(ChatUiEvent.OpenRedactor(lastAssistant))
            PowerToolAction.CSV_EXPLORER -> _events.tryEmit(ChatUiEvent.OpenCsvExplorer)
            PowerToolAction.GRAPH_PLOTTER -> _events.tryEmit(ChatUiEvent.OpenPlotter("a*sin(b*x)+c"))
            PowerToolAction.DECISION_MATRIX -> _events.tryEmit(ChatUiEvent.OpenDecisionMatrix(""))
            PowerToolAction.INTERACTIVE_CANVAS -> _events.tryEmit(ChatUiEvent.OpenCanvas(lastAssistant))
            PowerToolAction.EXPENSE_TRACKER -> _events.tryEmit(ChatUiEvent.OpenExpenses)
            PowerToolAction.WATCHLIST -> _events.tryEmit(ChatUiEvent.OpenWatchlist)
            PowerToolAction.BLE_INSPECTOR -> _events.tryEmit(ChatUiEvent.OpenBleInspector)
            PowerToolAction.FACE_INTERPRETER -> _events.tryEmit(ChatUiEvent.OpenInterpreter)
            PowerToolAction.COMMUTE_HUD -> _events.tryEmit(ChatUiEvent.OpenCommuteHud)
            PowerToolAction.SPOTLIGHT_SEARCH -> _events.tryEmit(ChatUiEvent.OpenSpotlight)
            PowerToolAction.CLIPBOARD_VAULT -> _events.tryEmit(ChatUiEvent.OpenClipboardVault)
            PowerToolAction.LOCAL_WORKSPACE -> _events.tryEmit(ChatUiEvent.OpenLocalWorkspace)
            PowerToolAction.VOICE_FORM_FILLER -> _events.tryEmit(ChatUiEvent.OpenVoiceFormFiller)
            PowerToolAction.CLI_COMMAND_BUILDER -> _events.tryEmit(ChatUiEvent.OpenCliBuilder(""))
        }
    }

    fun toggleConstraint(rule: NegativeConstraint) {
        activeConstraints.update { cur -> if (rule in cur) cur - rule else cur + rule }
    }

    fun toggleGhostMode() {
        val next = !ghostModeEnabled.value
        if (isStreaming.value) stopGeneration()
        ghostMessages.value = emptyList()
        ghostModeEnabled.value = next
        statusNotice.value = if (next) {
            "GHOST MODE // Incognito active — zero messages written to local history or search."
        } else {
            "GHOST MODE // Exited incognito & wiped volatile memory."
        }
    }

    fun toggleForceFlagshipOverride() {
        val next = !forceFlagshipOverride.value
        forceFlagshipOverride.value = next
        statusNotice.value = if (next) {
            "BUDGET // Flagship manual override ON for upcoming prompts."
        } else {
            "BUDGET // Auto-downgrade guard active."
        }
    }

    fun enhancePrompt(roughPrompt: String, onEnhanced: (String) -> Unit) {
        val clean = roughPrompt.trim()
        if (clean.isEmpty() || isEnhancingPrompt.value) return
        viewModelScope.launch {
            isEnhancingPrompt.value = true
            statusNotice.value = "PROMPT DOCTOR // Optimizing your prompt…"
            runCatching {
                repository.enhanceUserPrompt(clean, uiState.value.selectedModel)
            }.onSuccess { polished ->
                onEnhanced(polished)
                statusNotice.value = "PROMPT DOCTOR // Prompt upgraded — review or tap Send."
            }.onFailure { err ->
                errorToast.value = err.message ?: "Prompt Doctor failed."
            }
            isEnhancingPrompt.value = false
        }
    }

    fun expandMindMapNode(targetNode: MindMapNode) {
        val currentRoot = activeMindMap.value ?: return
        if (isExpandingMindMapNode.value) return
        viewModelScope.launch {
            isExpandingMindMapNode.value = true
            statusNotice.value = "MIND MAP // Expanding \"${targetNode.label}\"…"
            runCatching {
                repository.expandMindMapSubTopic(currentRoot, targetNode, uiState.value.selectedModel)
            }.onSuccess { updatedRoot ->
                activeMindMap.value = updatedRoot
                statusNotice.value = "MIND MAP // Grew new child branches under \"${targetNode.label}\"."
            }.onFailure { err ->
                errorToast.value = err.message ?: "Could not expand Mind Map branch."
            }
            isExpandingMindMapNode.value = false
        }
    }

    fun saveVoiceFormTemplates(templates: List<VoiceFormTemplate>) {
        viewModelScope.launch {
            repository.saveVoiceFormTemplates(templates)
            statusNotice.value = "FORM // Saved ${templates.size} voice form template(s)."
        }
    }

    suspend fun fillVoiceForm(template: VoiceFormTemplate, transcript: String): Result<FilledFormResult> = runCatching {
        repository.fillStructuredFormFromVoice(template, transcript, uiState.value.selectedModel)
    }

    suspend fun buildCliCommand(query: String): CliCommandSpec =
        repository.buildModularCliCommand(query, uiState.value.selectedModel)

    fun createCheckpointAt(targetMessage: MessageEntity) {
        val convId = uiState.value.activeConversation?.id ?: return
        if (summarizingCheckpointMsgId.value != null) return
        val branchMsgs = uiState.value.branchNodes.map { it.message }
        val idx = branchMsgs.indexOfFirst { it.id == targetMessage.id }
        if (idx < 0) return
        val upToHere = branchMsgs.subList(0, idx + 1)
        viewModelScope.launch {
            summarizingCheckpointMsgId.value = targetMessage.id
            statusNotice.value = "CHECKPOINT // Summarizing ${upToHere.size} messages into TL;DR divider…"
            runCatching {
                repository.createThreadCheckpoint(convId, targetMessage, upToHere, uiState.value.selectedModel)
            }.onSuccess { cp ->
                statusNotice.value = "CHECKPOINT // Inserted TL;DR divider covering ${cp.messageCountCovered} messages."
            }.onFailure { err ->
                errorToast.value = err.message ?: "Failed to create checkpoint."
            }
            summarizingCheckpointMsgId.value = null
        }
    }

    fun deleteCheckpoint(checkpointId: String) {
        val convId = uiState.value.activeConversation?.id ?: return
        viewModelScope.launch {
            repository.deleteThreadCheckpoint(convId, checkpointId)
        }
    }

    fun editAndForkAssistantMessage(targetMessage: MessageEntity, newContent: String) {
        val convId = uiState.value.activeConversation?.id ?: return
        val clean = newContent.trim()
        if (clean.isEmpty() || isStreaming.value) return
        viewModelScope.launch {
            val forkedId = repository.editAndForkAssistantMessage(convId, targetMessage, clean)
            val parentKey = targetMessage.parentMessageId ?: -1L
            selectedBranchByParent.update { it + (parentKey to forkedId) }
            statusNotice.value = "BRANCH // Created parallel AI response branch (use < 1 / N > to switch)."
        }
    }

    /**
     * Resolves inline `@filename` or `@relative/path` references against mounted SAF
     * local workspace files before dispatching the prompt to the LLM.
     */
    fun sendMessageWithWorkspaceContext(
        context: Context,
        rawPromptText: String,
        optionalLiveFrame: Bitmap? = null
    ) {
        val files = uiState.value.workspaceFiles
        if (files.isEmpty() || !rawPromptText.contains("@")) {
            if (optionalLiveFrame != null) {
                sendMessageWithOptionalLiveFrame(rawPromptText, optionalLiveFrame)
            } else {
                sendMessage(rawPromptText)
            }
            return
        }
        val mentionedTokens = Regex("""@([A-Za-z0-9_.\-/]+)""")
            .findAll(rawPromptText)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
        val matchedFiles = mentionedTokens.mapNotNull { token ->
            files.firstOrNull {
                it.name.equals(token, ignoreCase = true) ||
                    it.relativePath.equals(token, ignoreCase = true)
            }
        }.distinctBy { it.uriString }.take(3)

        if (matchedFiles.isEmpty()) {
            if (optionalLiveFrame != null) {
                sendMessageWithOptionalLiveFrame(rawPromptText, optionalLiveFrame)
            } else {
                sendMessage(rawPromptText)
            }
            return
        }

        viewModelScope.launch {
            statusNotice.value = "WORKSPACE // Reading ${matchedFiles.size} @file(s)…"
            val injectedBlocks = matchedFiles.mapNotNull { entry ->
                runCatching {
                    val content = LocalWorkspaceManager.readFileContent(context, entry.uriString, maxChars = 14_000)
                    if (content.isNotBlank()) {
                        "### WORKSPACE FILE: `${entry.relativePath}`\n```\n$content\n```"
                    } else null
                }.getOrNull()
            }
            val finalPrompt = if (injectedBlocks.isNotEmpty()) {
                injectedBlocks.joinToString("\n\n") + "\n\n" + rawPromptText
            } else {
                rawPromptText
            }
            if (optionalLiveFrame != null) {
                sendMessageWithOptionalLiveFrame(finalPrompt, optionalLiveFrame)
            } else {
                sendMessage(finalPrompt)
            }
        }
    }

    private fun enrichDeviceAction(action: DeviceAction): DeviceAction {
        val contacts = uiState.value.quickContacts
        return when (action) {
            is DeviceAction.DialPhone -> {
                if (action.contactLabel.isNullOrBlank() && action.phoneNumber.isNotBlank()) {
                    val matched = DeviceActionEngine.resolveContactByPhone(action.phoneNumber, contacts)
                    if (matched != null) action.copy(contactLabel = matched.name) else action
                } else action
            }
            is DeviceAction.WhatsAppCall -> {
                if (action.contactLabel.isNullOrBlank() && action.phoneNumber.isNotBlank()) {
                    val matched = DeviceActionEngine.resolveContactByPhone(action.phoneNumber, contacts)
                    if (matched != null) action.copy(contactLabel = matched.name) else action
                } else action
            }
            is DeviceAction.SendWhatsApp -> {
                if (action.contactLabel.isNullOrBlank() && action.phoneNumber.isNotBlank()) {
                    val matched = DeviceActionEngine.resolveContactByPhone(action.phoneNumber, contacts)
                    if (matched != null) action.copy(contactLabel = matched.name) else action
                } else action
            }
            else -> action
        }
    }

    private fun noticeForActiveAction(action: DeviceAction, totalSteps: Int = 1): String {
        val disambig = DeviceActionEngine.buildDisambiguationSpeech(action)
        val chainPrefix = if (totalSteps > 1) "CHAIN ($totalSteps STEPS) // " else ""
        if (disambig != null) return "${chainPrefix}$disambig"
        return when (action) {
            is DeviceAction.DialPhone -> if (action.phoneNumber.isNotBlank()) {
                action.displayBanner
            } else {
                "${chainPrefix}PHONE // Select a matching contact or enter number below."
            }
            is DeviceAction.WhatsAppCall -> if (action.phoneNumber.isNotBlank()) {
                action.displayBanner
            } else {
                "${chainPrefix}WHATSAPP CALL // Select a matching contact or enter number below."
            }
            is DeviceAction.SendWhatsApp -> if (action.phoneNumber.isNotBlank()) {
                "${chainPrefix}WHATSAPP // Ready for ${action.contactLabel ?: action.phoneNumber} — auto-opening in 3s."
            } else {
                "${chainPrefix}WHATSAPP // Select a matching contact or enter number below."
            }
            else -> "${chainPrefix}${action.badge} // Ready — tap confirmation button below."
        }
    }

    fun setPendingDeviceAction(action: DeviceAction?) {
        if (action == null) {
            dismissDeviceAction()
            return
        }
        val enriched = enrichDeviceAction(action)
        val current = queuedDeviceActions.value
        val updatedQueue = if (current.isEmpty()) listOf(enriched) else listOf(enriched) + current.drop(1)
        queuedDeviceActions.value = updatedQueue
        statusNotice.value = noticeForActiveAction(enriched, updatedQueue.size)
    }

    fun enqueueDeviceActions(actions: List<DeviceAction>) {
        if (actions.isEmpty()) return
        val enrichedList = actions.map { enrichDeviceAction(it) }
        queuedDeviceActions.value = enrichedList
        val first = enrichedList.first()
        statusNotice.value = noticeForActiveAction(first, enrichedList.size)
        DeviceActionEngine.buildDisambiguationSpeech(first)?.let { disambigSpeech ->
            _events.tryEmit(ChatUiEvent.SpeakRoutineText(disambigSpeech))
        }
    }

    fun advanceDeviceActionQueue() {
        val remaining = queuedDeviceActions.value.drop(1)
        queuedDeviceActions.value = remaining
        val next = remaining.firstOrNull()
        if (next != null) {
            statusNotice.value = "NEXT STEP (${remaining.size} LEFT) // ${noticeForActiveAction(next, remaining.size)}"
            DeviceActionEngine.buildDisambiguationSpeech(next)?.let { disambigSpeech ->
                _events.tryEmit(ChatUiEvent.SpeakRoutineText(disambigSpeech))
            }
        }
    }

    fun removeQueuedDeviceAction(index: Int) {
        val current = queuedDeviceActions.value
        if (index !in current.indices) return
        val updated = current.filterIndexed { i, _ -> i != index }
        queuedDeviceActions.value = updated
        if (index == 0) {
            updated.firstOrNull()?.let { next ->
                statusNotice.value = noticeForActiveAction(next, updated.size)
            }
        }
    }

    fun clearAllDeviceActions() {
        queuedDeviceActions.value = emptyList()
    }

    fun dismissDeviceAction() {
        advanceDeviceActionQueue()
    }

    fun saveQuickContact(
        name: String,
        phoneNumber: String,
        email: String = "",
        upiId: String = ""
    ) {
        val cleanName = name.trim()
        val cleanPhone = phoneNumber.trim()
        val cleanEmail = email.trim()
        val cleanUpi = upiId.trim()
        if (cleanName.isBlank() || (cleanPhone.isBlank() && cleanEmail.isBlank() && cleanUpi.isBlank())) return
        viewModelScope.launch {
            repository.saveQuickContact(
                QuickContact(
                    name = cleanName,
                    phoneNumber = cleanPhone,
                    email = cleanEmail,
                    upiId = cleanUpi
                )
            )
            statusNotice.value = "CONTACTS // Saved $cleanName to Quick Contacts."
        }
    }

    fun saveVoiceQuickNote(rawText: String) {
        val clean = rawText.trim()
        if (clean.isEmpty()) {
            _events.tryEmit(ChatUiEvent.OpenVoiceNotes)
            return
        }
        viewModelScope.launch {
            isSavingNote.value = true
            statusNotice.value = "NOTE // Cleaning up voice memo into structured Markdown…"
            runCatching {
                repository.saveVoiceQuickNote(clean, uiState.value.selectedModel)
            }.onSuccess { saved ->
                statusNotice.value = "NOTE // Saved \"${saved.title}\" to Voice Quick-Notes Inbox."
            }.onFailure { err ->
                errorToast.value = err.message ?: "Could not save voice note."
            }
            isSavingNote.value = false
        }
    }

    fun updateVoiceNote(note: VoiceNoteInfo) {
        viewModelScope.launch {
            repository.updateVoiceNote(note)
            statusNotice.value = "NOTE // Updated \"${note.title}\"."
        }
    }

    fun deleteVoiceNote(id: Long) {
        viewModelScope.launch {
            repository.deleteVoiceNote(id)
        }
    }

    fun runRoutine(routine: AssistantRoutine) {
        if (isStreaming.value) return
        activeStreamJob = viewModelScope.launch {
            isStreaming.value = true
            statusNotice.value = "ROUTINE // Running \"${routine.name}\"…"
            try {
                val result = repository.executeRoutine(
                    routine = routine,
                    preferredModel = uiState.value.selectedModel,
                    onProgress = { msg -> statusNotice.value = "ROUTINE // $msg" }
                )
                val convId = activeConversationId.value
                    ?: repository.getOrCreateActiveConversationId(
                        defaultTitle = "Routine: ${routine.name}",
                        modelDbId = uiState.value.selectedModel?.model?.id,
                        personaId = uiState.value.activePersona?.id
                    ).also { activeConversationId.value = it }
                repository.appendInstantExchange(
                    conversationId = convId,
                    userText = "Run routine: ${routine.name}",
                    assistantText = result.summaryText,
                    modelLabel = result.modelLabel
                )
                if (result.pendingDeviceAction != null) {
                    enqueueDeviceActions(listOf(result.pendingDeviceAction))
                }
                if (!result.spokenText.isNullOrBlank()) {
                    _events.tryEmit(ChatUiEvent.SpeakRoutineText(result.spokenText))
                }
                statusNotice.value = "ROUTINE // \"${routine.name}\" finished."
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                errorToast.value = t.message ?: "Routine failed."
            } finally {
                isStreaming.value = false
            }
        }
    }

    fun retryLastFailedMessage() {
        val retryPrompt = lastFailedPrompt.value ?: return
        lastFailedPrompt.value = null
        errorToast.value = null
        sendMessage(retryPrompt)
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

    fun addMemory(fact: String, category: String = "Preference", profile: String? = null) {
        viewModelScope.launch {
            val id = repository.addMemory(fact, category, source = "Manual", profile = profile)
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

    fun setMemoryPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch {
            repository.setMemoryPinned(id, pinned)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
            statusNotice.value = "MEMORY // All saved memories cleared."
        }
    }

    fun toggleSessionMemory() {
        val next = !memoryInjectionEnabled.value
        memoryInjectionEnabled.value = next
        statusNotice.value = if (next) {
            "MEMORY // Personal memory injection ON for this session."
        } else {
            "MEMORY // Muted for clean, unbiased queries in this session."
        }
    }

    fun cycleMemoryProfile() {
        val next = uiState.value.activeMemoryProfile.next()
        setMemoryProfile(next)
    }

    fun setMemoryProfile(profile: MemoryProfile) {
        viewModelScope.launch {
            repository.setActiveMemoryProfile(profile)
            statusNotice.value = "PROFILE // ${profile.label} context active for memory injection."
        }
    }

    fun cycleOutputFormatMode() {
        val next = outputFormatMode.value.next()
        outputFormatMode.value = next
        statusNotice.value = if (next == OutputFormatMode.AUTO) {
            "FORMAT LOCK // Auto (default free-form response)."
        } else {
            "FORMAT LOCK // ${next.chipLabel} structure enforced on outgoing prompts."
        }
    }

    fun setOutputFormatMode(mode: OutputFormatMode) {
        outputFormatMode.value = mode
    }

    fun setSemanticAutoAttach(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSemanticAutoAttach(enabled)
            statusNotice.value = if (enabled) {
                "SEMANTIC RAG // Auto-attaching top conceptual match on outgoing prompts."
            } else {
                "SEMANTIC RAG // Auto-attach disabled."
            }
        }
    }

    fun dismissMemoryUpdatedToast() {
        memoryUpdatedToast.value = null
    }

    fun togglePinnedModel(modelDbId: Long) {
        viewModelScope.launch {
            val (isPinned, hitLimit) = repository.togglePinnedModelId(modelDbId, maxPins = 4)
            if (hitLimit) {
                statusNotice.value = "MODELS // Max 4 pinned favorites reached. Unpin one first."
            } else {
                val name = uiState.value.availableModels.firstOrNull { it.model.id == modelDbId }?.model?.displayName ?: "Model"
                statusNotice.value = if (isPinned) "PINNED // $name locked to top favorites." else "UNPINNED // Removed $name from favorites."
            }
        }
    }

    fun cyclePinnedModel() {
        val state = uiState.value
        val pinnedList = state.pinnedModelIds.mapNotNull { pid ->
            state.availableModels.firstOrNull { it.model.id == pid }
        }
        val cyclePool = if (pinnedList.size >= 2) pinnedList else state.availableModels
        if (cyclePool.size < 2) {
            statusNotice.value = "MODELS // Pin at least 2 models in the picker to 1-tap cycle."
            return
        }
        val currentId = state.selectedModel?.model?.id
        val currentIdx = cyclePool.indexOfFirst { it.model.id == currentId }
        val nextModel = cyclePool[(currentIdx + 1) % cyclePool.size]
        selectModel(nextModel)
        val ms = state.modelLatencyMs[nextModel.model.id]
        val msSuffix = if (ms != null) " (${ms}ms)" else ""
        statusNotice.value = "CYCLE // Switched to ${nextModel.model.displayName}$msSuffix"
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
                repository.setActiveConversationId(newId)
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
        lastFailedPrompt.value = null
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
            SlashCommandId.NOTE -> {
                val memo = argument.trim()
                if (memo.isNotBlank()) {
                    saveVoiceQuickNote(memo)
                } else {
                    val lastAssistant = uiState.value.messages.lastOrNull { !it.isError && it.content.isNotBlank() }?.content
                    if (!lastAssistant.isNullOrBlank()) {
                        saveVoiceQuickNote(lastAssistant)
                    } else {
                        _events.tryEmit(ChatUiEvent.OpenVoiceNotes)
                    }
                }
            }
            SlashCommandId.NOTES -> {
                _events.tryEmit(ChatUiEvent.OpenVoiceNotes)
            }
            SlashCommandId.ROUTINES -> {
                val query = argument.trim()
                val matched = if (query.isNotBlank()) {
                    uiState.value.routines.firstOrNull {
                        it.name.equals(query, ignoreCase = true) ||
                            it.triggerPhrase.equals(query, ignoreCase = true) ||
                            it.name.contains(query, ignoreCase = true)
                    }
                } else null
                if (matched != null) {
                    runRoutine(matched)
                } else if (uiState.value.routines.isEmpty()) {
                    statusNotice.value = "ROUTINES // No routines yet. Open Settings → Routines Builder to create 1-tap macros."
                } else {
                    val names = uiState.value.routines.joinToString(", ") { "\"${it.name}\"" }
                    statusNotice.value = "ROUTINES // Available: $names. Type /routine <name> or say its trigger phrase."
                }
            }
            SlashCommandId.QR -> {
                _events.tryEmit(ChatUiEvent.OpenQrDialog(argument))
            }
            SlashCommandId.PLOT -> {
                _events.tryEmit(ChatUiEvent.OpenPlotter(argument.ifBlank { "sin(a * x) + b * x" }))
            }
            SlashCommandId.COMPARE -> {
                if (argument.isNotBlank()) {
                    buildDecisionMatrix(argument)
                }
                _events.tryEmit(ChatUiEvent.OpenDecisionMatrix(argument))
            }
            SlashCommandId.CANVAS -> {
                val seed = argument.ifBlank {
                    uiState.value.messages.lastOrNull { it.role == "ASSISTANT" && !it.isError }?.content.orEmpty()
                }
                _events.tryEmit(ChatUiEvent.OpenCanvas(seed))
            }
            SlashCommandId.EXPENSES -> {
                _events.tryEmit(ChatUiEvent.OpenExpenses)
            }
            SlashCommandId.WATCH -> {
                if (argument.isNotBlank()) {
                    saveWatchlistItem(argument.take(40), argument)
                }
                _events.tryEmit(ChatUiEvent.OpenWatchlist)
            }
            SlashCommandId.BLE -> {
                _events.tryEmit(ChatUiEvent.OpenBleInspector)
            }
            SlashCommandId.COMMUTE -> {
                _events.tryEmit(ChatUiEvent.OpenCommuteHud)
            }
            SlashCommandId.INTERPRET -> {
                _events.tryEmit(ChatUiEvent.OpenInterpreter)
            }
            SlashCommandId.SPOTLIGHT -> {
                _events.tryEmit(ChatUiEvent.OpenSpotlight)
            }
            SlashCommandId.REDACT -> {
                _events.tryEmit(ChatUiEvent.OpenRedactor(argument))
            }
            SlashCommandId.CMD -> {
                _events.tryEmit(ChatUiEvent.OpenCliBuilder(argument))
            }
            SlashCommandId.FORM -> {
                _events.tryEmit(ChatUiEvent.OpenVoiceFormFiller)
            }
            SlashCommandId.GHOST -> {
                toggleGhostMode()
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
        _events.tryEmit(ChatUiEvent.OpenExportStudio(conv.title, md))
    }

    fun openExportStudioForCurrentSession() {
        exportCurrentSession()
    }

    // --- Sending & Branching ---

    fun sendMessage(rawPromptText: String) {
        val state = uiState.value
        val trimmed = rawPromptText.trim()
        val attachment = pendingAttachmentUri.value
        val batchDocs = pendingBatchDocuments.value
        if (trimmed.isEmpty() && attachment == null && batchDocs.isEmpty()) return

        // 1. Intercept built-in /slash commands first!
        val matchedSlash = SlashCommandRegistry.matchExact(trimmed)
        if (matchedSlash != null && attachment == null) {
            val arg = trimmed.dropWhile { !it.isWhitespace() }.trim()
            runSlashCommand(matchedSlash, arg)
            return
        }

        // 1a. Intercept saved 1-tap Assistant Routine trigger phrases
        if (attachment == null && batchDocs.isEmpty() && state.routines.isNotEmpty()) {
            val normalizedTrigger = trimmed.lowercase()
                .removePrefix("run ")
                .removePrefix("start ")
                .trim()
            val matchedRoutine = state.routines.firstOrNull { r ->
                val phrase = r.triggerPhrase.trim().lowercase()
                (phrase.isNotEmpty() && (trimmed.equals(phrase, ignoreCase = true) || normalizedTrigger == phrase)) ||
                    trimmed.equals("run ${r.name}", ignoreCase = true)
            }
            if (matchedRoutine != null) {
                runRoutine(matchedRoutine)
                return
            }
        }

        // 1b. Intercept Voice/Text Disambiguation Replies ("first", "second", "Rahul Verma") & Sequential Multi-Action Chains
        if (attachment == null && batchDocs.isEmpty()) {
            val activePending = queuedDeviceActions.value.firstOrNull()
            if (activePending != null && DeviceActionEngine.ambiguousMatchesOf(activePending).size >= 2) {
                val resolvedDisambig = DeviceActionEngine.resolveDisambiguationVoiceReply(trimmed, activePending)
                if (resolvedDisambig != null) {
                    setPendingDeviceAction(resolvedDisambig)
                    return
                }
            }

            val parsedChain = DeviceActionEngine.parseActionChainFromPrompt(
                rawInput = trimmed,
                contacts = state.quickContacts,
                customApps = state.customApps
            )
            if (parsedChain.isNotEmpty()) {
                enqueueDeviceActions(parsedChain)
                return
            }
        }

        if (isStreaming.value) return

        // 1c. Intercept saved Smart Home / Webhook / Tasker trigger phrases
        if (attachment == null && batchDocs.isEmpty() && !trimmed.startsWith("/")) {
            viewModelScope.launch {
                val matchedHook = repository.matchWebhookByPhrase(trimmed)
                if (matchedHook != null) {
                    statusNotice.value = "WEBHOOK // Firing \"${matchedHook.name}\"…"
                    val res = repository.executeWebhook(matchedHook)
                    val summary = res.fold(
                        onSuccess = { "**Webhook Executed:** `${matchedHook.name}`\n\n- **Endpoint:** `${matchedHook.method} ${matchedHook.url}`\n- **Response:** `$it`" },
                        onFailure = { "**Webhook Failed:** `${matchedHook.name}` — ${it.message ?: "Network error"}" }
                    )
                    val convId = activeConversationId.value
                        ?: repository.getOrCreateActiveConversationId(
                            defaultTitle = "Webhook: ${matchedHook.name}",
                            modelDbId = uiState.value.selectedModel?.model?.id,
                            personaId = uiState.value.activePersona?.id
                        ).also { activeConversationId.value = it }
                    repository.appendInstantExchange(
                        conversationId = convId,
                        userText = trimmed,
                        assistantText = summary,
                        modelLabel = "WEBHOOK"
                    )
                    statusNotice.value = res.fold(
                        onSuccess = { "WEBHOOK // ${matchedHook.name} → $it" },
                        onFailure = { "WEBHOOK // Failed: ${it.message}" }
                    )
                    return@launch
                }
                continueSendMessage(trimmed, attachment, batchDocs, rawPromptText)
            }
            return
        }

        continueSendMessage(trimmed, attachment, batchDocs, rawPromptText)
    }

    private fun continueSendMessage(
        trimmed: String,
        attachment: String?,
        batchDocs: List<Pair<String, String>>,
        rawPromptText: String
    ) {
        val state = uiState.value

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
            if (effectivePrompt.isNotBlank()) {
                val processed = if (state.tokenDietEnabled) {
                    val comp = PromptCompressor.compress(effectivePrompt)
                    if (comp.savedPercent >= 5) {
                        statusNotice.value = "TOKEN DIET // Saved ~${comp.savedPercent}% tokens before sending."
                    }
                    comp.compressedText
                } else {
                    effectivePrompt
                }
                append(processed)
            } else if (batchDocs.isNotEmpty()) {
                append("Analyze, compare, and synthesize the ${batchDocs.size} attached documents below.")
            }
            if (docCtx != null) {
                appendLine()
                appendLine()
                appendLine("--- SHARED DOCUMENT: ${docCtx.first} ---")
                appendLine(docCtx.second)
            }
            if (batchDocs.isNotEmpty()) {
                appendLine()
                appendLine()
                appendLine("=== BATCH ATTACHED DOCUMENTS (${batchDocs.size}) ===")
                batchDocs.forEachIndexed { idx, (docTitle, docContent) ->
                    appendLine()
                    appendLine("--- DOCUMENT ${idx + 1}: $docTitle ---")
                    appendLine(docContent)
                }
            }
        }.trim()

        val latestCheckpoint = state.threadCheckpoints.maxByOrNull { it.createdAt }
        val options = buildChatOptions(
            persona = effectivePersona,
            agentsMd = state.agentsMd,
            params = state.requestParams,
            enableTools = state.toolsEnabled,
            guidedMode = state.guidedModeEnabled,
            smartContext = state.smartContext,
            activeConstraints = state.activeConstraints,
            checkpointSummary = latestCheckpoint?.summaryMarkdown,
            reasoningEffort = state.reasoningEffort,
            injectMemory = state.memoryInjectionEnabled,
            activeMemoryProfile = state.activeMemoryProfile,
            outputFormatMode = state.outputFormatMode,
            semanticAutoAttach = state.semanticAutoAttach
        )
        val searchOn = state.webSearchEnabled
        val shouldAutoExtractMemory = state.autoExtractMemories
        val shouldVerifyFix = state.verifyFixEnabled
        pendingAttachmentUri.value = null
        quotedMessage.value = null
        pendingDocumentContext.value = null
        pendingBatchDocuments.value = emptyList()
        followUpSuggestions.value = emptyList()
        clearNotices()
        if (autoRouteLabel != null) {
            statusNotice.value = "AUTO-ROUTER // Selected ${model.model.displayName} ($autoRouteLabel)."
        }

        // Incognito Ghost Chat Mode: pure volatile RAM, zero Room DB / search / memory writes
        if (state.ghostModeEnabled) {
            activeStreamJob = viewModelScope.launch {
                isStreaming.value = true
                lastFailedPrompt.value = null
                val (resolvedModel, budgetNotice) = repository.resolveBudgetDowngradeModel(
                    requestedModel = model,
                    availableModels = state.availableModels,
                    forceFlagshipOverride = state.forceFlagshipOverride
                )
                if (budgetNotice != null) statusNotice.value = budgetNotice
                val priorHistory = ghostMessages.value.map {
                    (if (it.role == "USER") "user" else "assistant") to it.content
                }
                val userMsgId = -(System.currentTimeMillis())
                val aiMsgId = userMsgId - 1L
                val userGhost = MessageEntity(
                    id = userMsgId,
                    conversationId = -1L,
                    role = "USER",
                    content = finalPrompt
                )
                val aiGhost = MessageEntity(
                    id = aiMsgId,
                    conversationId = -1L,
                    role = "ASSISTANT",
                    content = "",
                    modelLabel = "GHOST // ${resolvedModel.model.displayName}"
                )
                ghostMessages.update { it + userGhost + aiGhost }
                try {
                    val finalReply = repository.streamGhostChatTurn(
                        history = priorHistory,
                        userText = finalPrompt,
                        model = resolvedModel,
                        options = options,
                        onDelta = { partial ->
                            ghostMessages.update { list ->
                                list.map { if (it.id == aiMsgId) it.copy(content = partial) else it }
                            }
                        }
                    )
                    if (finalReply.isNotBlank()) {
                        ghostMessages.update { list ->
                            list.map { if (it.id == aiMsgId) it.copy(content = finalReply) else it }
                        }
                        viewModelScope.launch {
                            val chips = repository.generateFollowUpChips(finalPrompt, finalReply, resolvedModel)
                            if (chips.isNotEmpty()) followUpSuggestions.value = chips
                        }
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (err: Throwable) {
                    val errText = err.message ?: "Ghost request failed."
                    ghostMessages.update { list ->
                        list.map { if (it.id == aiMsgId) it.copy(content = errText, isError = true) else it }
                    }
                    errorToast.value = errText
                    lastFailedPrompt.value = rawPromptText
                } finally {
                    isStreaming.value = false
                }
            }
            return
        }

        activeStreamJob = viewModelScope.launch {
            isStreaming.value = true
            lastFailedPrompt.value = null
            try {
                val (resolvedModel, budgetNotice) = repository.resolveBudgetDowngradeModel(
                    requestedModel = model,
                    availableModels = state.availableModels,
                    forceFlagshipOverride = state.forceFlagshipOverride
                )
                if (budgetNotice != null) statusNotice.value = budgetNotice
                val model = resolvedModel
                val convId = activeConversationId.value
                    ?: repository.createConversation(
                        title = effectivePrompt.take(40).ifBlank { "Image Chat" },
                        modelDbId = model.model.id,
                        personaId = effectivePersona?.id
                    ).also {
                        activeConversationId.value = it
                        repository.setActiveConversationId(it)
                    }

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
                    },
                    onDeviceActionTriggered = { triggered ->
                        val enriched = enrichDeviceAction(triggered)
                        queuedDeviceActions.update { existing ->
                            if (existing.contains(enriched)) existing else existing + enriched
                        }
                        val updatedQueue = queuedDeviceActions.value
                        val activeHead = updatedQueue.firstOrNull() ?: enriched
                        statusNotice.value = noticeForActiveAction(activeHead, updatedQueue.size)
                        if (updatedQueue.size == 1) {
                            DeviceActionEngine.buildDisambiguationSpeech(activeHead)?.let { disambigSpeech ->
                                _events.tryEmit(ChatUiEvent.SpeakRoutineText(disambigSpeech))
                            }
                        }
                    },
                    onRequestError = { errMsg ->
                        errorToast.value = errMsg
                        lastFailedPrompt.value = rawPromptText
                    }
                )

                val lastMsg = uiState.value.messages.lastOrNull { it.role == "ASSISTANT" && !it.isError }
                var lastReply = lastMsg?.content.orEmpty()
                if (lastMsg != null && lastReply.isNotBlank()) {
                    if (shouldVerifyFix && lastReply.length >= 60) {
                        statusNotice.value = "VERIFY & FIX // Running automated edge-case self-check…"
                        val verified = repository.verifyAndFixResponse(
                            originalPrompt = effectivePrompt.ifBlank { finalPrompt.take(800) },
                            draftAnswer = lastReply,
                            preferredModel = model
                        )
                        if (verified.isNotBlank() && verified != lastReply) {
                            repository.updateMessageContent(lastMsg.id, verified)
                            lastReply = verified
                            statusNotice.value = "VERIFY & FIX // Self-check complete."
                        }
                    }

                    // Generate 3 clickable 1-tap Smart Follow-Up Chips silently in background
                    viewModelScope.launch {
                        val chips = repository.generateFollowUpChips(
                            userPrompt = effectivePrompt.ifBlank { finalPrompt.take(300) },
                            assistantReply = lastReply,
                            selectableModel = model
                        )
                        if (chips.isNotEmpty()) {
                            followUpSuggestions.value = chips
                        }
                    }

                    if (shouldAutoExtractMemory && effectivePrompt.length >= 18) {
                        viewModelScope.launch {
                            val savedCount = repository.extractMemoriesFromTurn(effectivePrompt, lastReply, model)
                            if (savedCount > 0) {
                                memoryUpdatedToast.value = "MEMORY // UPDATED (+$savedCount)"
                            }
                        }
                    }

                    // Route lightweight session auto-titling to the Fast Utility Model (<= 800 char trimmed)
                    if (state.messages.size <= 1 && effectivePrompt.length >= 12) {
                        viewModelScope.launch {
                            repository.autoTitleConversationFast(
                                conversationId = convId,
                                userMessage = effectivePrompt.ifBlank { finalPrompt.take(400) },
                                assistantMessage = lastReply,
                                fallbackModel = model
                            )
                        }
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (err: Throwable) {
                errorToast.value = err.message ?: "Request failed. Tap Retry to try again."
                lastFailedPrompt.value = rawPromptText
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
        val options = buildChatOptions(
            persona = state.activePersona,
            agentsMd = state.agentsMd,
            params = state.requestParams,
            enableTools = state.toolsEnabled,
            injectMemory = state.memoryInjectionEnabled,
            activeMemoryProfile = state.activeMemoryProfile,
            outputFormatMode = state.outputFormatMode,
            semanticAutoAttach = state.semanticAutoAttach
        )

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

    fun cycleReasoningEffort() {
        val next = reasoningEffort.value.next()
        reasoningEffort.value = next
        statusNotice.value = "THINKING DIAL // ${next.displayLabel} mode active."
    }

    fun setReasoningEffort(effort: ReasoningEffort) {
        reasoningEffort.value = effort
        statusNotice.value = "THINKING DIAL // ${effort.displayLabel} mode active."
    }

    fun morphAssistantMessage(targetMessage: MessageEntity, style: ReplyMorphStyle) {
        val state = uiState.value
        val model = state.selectedModel ?: run {
            errorToast.value = "Configure a Text Chat model in Settings first."
            return
        }
        if (isStreaming.value || morphingMessageId.value != null) return
        val baseOptions = buildChatOptions(
            persona = state.activePersona,
            agentsMd = state.agentsMd,
            params = state.requestParams,
            enableTools = false,
            reasoningEffort = state.reasoningEffort
        )

        if (state.ghostModeEnabled) {
            activeStreamJob = viewModelScope.launch {
                isStreaming.value = true
                morphingMessageId.value = targetMessage.id
                statusNotice.value = "MORPH // Rewriting (${style.chipLabel})…"
                val original = targetMessage.content
                try {
                    val rewritten = repository.streamGhostChatTurn(
                        history = emptyList(),
                        userText = "Transform this response (${style.chipLabel}): ${style.instruction}\n\nReturn ONLY the rewritten text.\n\n$original",
                        model = model,
                        options = baseOptions.copy(enableTools = false),
                        onDelta = { partial ->
                            ghostMessages.update { list ->
                                list.map { if (it.id == targetMessage.id) it.copy(content = partial) else it }
                            }
                        }
                    )
                    if (rewritten.isNotBlank()) {
                        ghostMessages.update { list ->
                            list.map { if (it.id == targetMessage.id) it.copy(content = rewritten) else it }
                        }
                        statusNotice.value = "MORPH // Applied ${style.chipLabel} in-place."
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (err: Throwable) {
                    ghostMessages.update { list ->
                        list.map { if (it.id == targetMessage.id) it.copy(content = original) else it }
                    }
                    errorToast.value = err.message ?: "Morph failed."
                } finally {
                    morphingMessageId.value = null
                    isStreaming.value = false
                }
            }
            return
        }

        val convId = activeConversationId.value ?: return
        activeStreamJob = viewModelScope.launch {
            isStreaming.value = true
            morphingMessageId.value = targetMessage.id
            try {
                repository.morphAssistantMessageInPlace(
                    conversationId = convId,
                    targetMessage = targetMessage,
                    style = style,
                    selectableModel = model,
                    baseOptions = baseOptions,
                    onStatus = { status -> statusNotice.value = status }
                )
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (err: Throwable) {
                errorToast.value = err.message ?: "Failed to morph response."
            } finally {
                morphingMessageId.value = null
                isStreaming.value = false
            }
        }
    }

    fun regenerateAsBranch(assistantMessage: MessageEntity) {
        val state = uiState.value
        val model = state.selectedModel ?: return
        val convId = activeConversationId.value ?: return
        if (isStreaming.value) return
        val options = buildChatOptions(
            persona = state.activePersona,
            agentsMd = state.agentsMd,
            params = state.requestParams,
            enableTools = state.toolsEnabled,
            reasoningEffort = state.reasoningEffort,
            injectMemory = state.memoryInjectionEnabled,
            activeMemoryProfile = state.activeMemoryProfile,
            outputFormatMode = state.outputFormatMode,
            semanticAutoAttach = state.semanticAutoAttach
        )

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
        morphingMessageId.value = null
        isStreaming.value = false
    }

    private fun buildChatOptions(
        persona: PersonaInfo?,
        agentsMd: String,
        params: RequestParamsOverride,
        enableTools: Boolean,
        guidedMode: Boolean = false,
        smartContext: SmartContextSnapshot = SmartContextSnapshot(),
        activeConstraints: Set<NegativeConstraint> = emptySet(),
        checkpointSummary: String? = null,
        reasoningEffort: ReasoningEffort = ReasoningEffort.BALANCED,
        injectMemory: Boolean = true,
        activeMemoryProfile: MemoryProfile = MemoryProfile.ALL,
        outputFormatMode: OutputFormatMode = OutputFormatMode.AUTO,
        semanticAutoAttach: Boolean = false
    ): ChatOptions {
        val combinedSystem = buildList {
            if (agentsMd.isNotBlank()) add("# Project Instructions (AGENTS.md)\n$agentsMd")
            if (!persona?.systemPrompt.isNullOrBlank()) add(persona!!.systemPrompt)
            if (smartContext.timeProfile.systemInstruction.isNotBlank()) {
                add("# Smart Time Profile (${smartContext.timeProfile.label})\n${smartContext.timeProfile.systemInstruction}")
            }
            if (smartContext.isEcoModeActive) {
                val ecoDetail = if (smartContext.isSlowNetwork) "Slow/Metered Network" else "Battery ${smartContext.batteryPercent}%"
                add("# Eco Mode Active\nKeep responses crisp and token-efficient ($ecoDetail).")
            }
            if (guidedMode) {
                add(
                    "# Guided Wizard Mode\nIf the user's request is broad, ambiguous, or a multi-step planning/architecture task, ask 2-3 brief, high-impact clarifying questions numbered 1..3 BEFORE producing a long final answer. If the user is answering your clarifying questions, deliver the complete tailored solution."
                )
            }
            if (reasoningEffort.systemHint.isNotBlank()) {
                add("# Reasoning & Creativity Effort (${reasoningEffort.displayLabel})\n${reasoningEffort.systemHint}")
            }
            if (activeConstraints.isNotEmpty()) {
                add(
                    "# Strict Output Constraints\n" +
                        activeConstraints.joinToString("\n") { "- ${it.chipLabel}: ${it.instruction}" }
                )
            }
            if (!checkpointSummary.isNullOrBlank()) {
                add("# Earlier Thread TL;DR Checkpoint Anchor\n$checkpointSummary")
            }
        }.joinToString("\n\n").takeIf { it.isNotBlank() }

        return ChatOptions(
            systemPrompt = combinedSystem,
            temperature = params.temperature ?: reasoningEffort.temperatureOverride ?: persona?.temperature,
            topP = params.topP,
            maxTokens = params.maxTokens ?: persona?.maxTokens,
            jsonMode = params.jsonMode,
            enableTools = enableTools,
            reasoningEffort = reasoningEffort,
            injectMemory = injectMemory,
            activeMemoryProfile = activeMemoryProfile,
            outputFormatMode = outputFormatMode,
            semanticAutoAttach = semanticAutoAttach
        )
    }

    private data class NavigationAndMemoryBundle(
        val memoryInjectionEnabled: Boolean,
        val memoryUpdatedToast: String?,
        val pinnedModelIds: List<Long>,
        val modelLatencyMs: Map<Long, Long>
    )

    private data class ProfileAndFormatBundle(
        val activeMemoryProfile: MemoryProfile,
        val outputFormatMode: OutputFormatMode,
        val semanticAutoAttach: Boolean,
        val fastUtilityModelId: Long?
    )

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

    private data class AdvancedStudioBundle(
        val followUpSuggestions: List<String>,
        val clipboardHint: ClipboardPromptHint?,
        val batchDocumentTitles: List<String>,
        val liveCamEnabled: Boolean,
        val liveVoiceActive: Boolean
    )

    private data class ConversationModesBundle(
        val tokenDietEnabled: Boolean,
        val guidedModeEnabled: Boolean,
        val verifyFixEnabled: Boolean,
        val smartContext: SmartContextSnapshot,
        val cooldownMap: Map<Long, Long>
    )

    private data class SmartPrefsBundle(
        val smartZones: List<SmartZone>,
        val timeProfilesEnabled: Boolean,
        val autoEcoMode: Boolean,
        val wakePhraseEnabled: Boolean,
        val ambientWhisperEnabled: Boolean
    )

    private data class HubsBundle(
        val clipboardHistory: List<ClipboardSnippetInfo>,
        val localWorkspaceUri: String?,
        val workspaceFiles: List<WorkspaceFileEntry>,
        val expenses: List<ExpenseRecord>,
        val watchlist: List<WatchlistItemInfo>,
        val canvasVersions: List<CanvasVersionInfo>
    )

    private data class VerificationBundle(
        val factCheckByMessageId: Map<Long, List<FactCheckClaim>>,
        val checkingFactMessageIds: Set<Long>,
        val activeDecisionMatrix: DecisionMatrixData?,
        val isBuildingMatrix: Boolean
    )

    private data class Wave4UxBundle(
        val activeConstraints: Set<NegativeConstraint>,
        val isEnhancingPrompt: Boolean,
        val isExpandingMindMapNode: Boolean,
        val ghostModeEnabled: Boolean,
        val ghostMessages: List<MessageEntity>
    )

    private data class Wave4BudgetMetaBundle(
        val budgetConfig: DailyBudgetConfig,
        val todaySpendUsd: Double,
        val forceFlagshipOverride: Boolean,
        val voiceFormTemplates: List<VoiceFormTemplate>,
        val checkpointsByConv: Map<Long, List<ThreadCheckpoint>>,
        val summarizingCheckpointMsgId: Long?
    )
    private data class DeviceBridgeBundle(
        val queuedDeviceActions: List<DeviceAction>,
        val quickContacts: List<QuickContact>,
        val customApps: List<CustomAppEntry>,
        val autoDialWebhookUrl: String
    )
}
