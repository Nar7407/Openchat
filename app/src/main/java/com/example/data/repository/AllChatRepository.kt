package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.ApiKeyVault
import com.example.data.local.CanvasVersionDao
import com.example.data.local.CanvasVersionEntity
import com.example.data.local.ClipboardDao
import com.example.data.local.ClipboardItemEntity
import com.example.data.local.ConversationDao
import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationImporter
import com.example.data.local.ExpenseDao
import com.example.data.local.ExpenseEntity
import com.example.data.local.GeneratedMediaDao
import com.example.data.local.GeneratedMediaEntity
import com.example.data.local.KnowledgeDocDao
import com.example.data.local.KnowledgeDocEntity
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.local.ModelDao
import com.example.data.local.ModelEntity
import com.example.data.local.PersonaDao
import com.example.data.local.PersonaEntity
import com.example.data.local.ProviderDao
import com.example.data.local.ProviderEntity
import com.example.data.local.RoutineDao
import com.example.data.local.RoutineEntity
import com.example.data.local.UserMemoryDao
import com.example.data.local.UserMemoryEntity
import com.example.data.local.VaultBackupManager
import com.example.data.local.VaultSnapshot
import com.example.data.local.VoiceNoteDao
import com.example.data.local.VoiceNoteEntity
import com.example.data.local.WatchlistDao
import com.example.data.local.WatchlistItemEntity
import com.example.data.local.WebhookActionEntity
import com.example.data.local.WebhookDao
import android.graphics.Bitmap
import com.example.data.remote.AiRouter
import com.example.data.remote.ChatInputMessage
import com.example.data.remote.ConnectionTestResult
import com.example.data.remote.FetchedModel
import com.example.data.remote.StreamEvent
import com.example.data.remote.ToolCallInvocation
import com.example.data.remote.WebSearchClient
import com.example.data.remote.YouTubeExtractorClient
import com.example.domain.model.AiErrorCategory
import com.example.domain.model.AiException
import com.example.domain.model.AssistantRoutine
import com.example.domain.model.AutoRouterConfig
import com.example.domain.model.ChatOptions
import com.example.domain.model.ClientToolEngine
import com.example.domain.model.CodeDebugResult
import com.example.domain.model.ConversationTree
import com.example.domain.model.CostEstimator
import com.example.domain.model.CustomAppEntry
import com.example.domain.model.CustomSlashCommand
import com.example.domain.model.DisplayCurrency
import com.example.domain.model.KnowledgeDocInfo
import com.example.domain.model.LiveLensAnalysisResult
import com.example.domain.model.LiveLensMode
import com.example.domain.model.LocalRagEngine
import com.example.domain.model.McpCheckResult
import com.example.domain.model.McpServerConfig
import com.example.domain.model.MemoryProfile
import com.example.domain.model.MindMapNode
import com.example.domain.model.ModelCapability
import com.example.domain.model.ModelInfo
import com.example.domain.model.OutputFormatMode
import com.example.domain.model.PersonaInfo
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType
import com.example.domain.model.QuickContact
import com.example.domain.model.QuizQuestion
import com.example.domain.model.ReplyMorphStyle
import com.example.domain.model.RewritePreset
import com.example.domain.model.RoutineStep
import com.example.domain.model.RoutineStepType
import com.example.domain.model.SearchCitation
import com.example.domain.model.SelectableModel
import com.example.domain.model.CanvasVersionInfo
import com.example.domain.model.ClipboardSnippetInfo
import com.example.domain.model.DecisionCriterion
import com.example.domain.model.DecisionMatrixData
import com.example.domain.model.ExpenseRecord
import com.example.domain.model.FactCheckClaim
import com.example.domain.model.CliCommandSpec
import com.example.domain.model.CliFlagToken
import com.example.domain.model.DailyBudgetConfig
import com.example.domain.model.FilledFormResult
import com.example.domain.model.SmartZone
import com.example.domain.model.SpotlightResult
import com.example.domain.model.StudyCard
import com.example.domain.model.StudyPack
import com.example.domain.model.SummaryLength
import com.example.domain.model.ThreadCheckpoint
import com.example.domain.model.TopicCluster
import com.example.domain.model.VoiceFormTemplate
import com.example.domain.model.LiveLensOverlayRegion
import com.example.domain.model.UserMemoryInfo
import com.example.domain.model.VoiceNoteInfo
import com.example.domain.model.WatchlistItemInfo
import com.example.domain.model.WebSearchProvider
import com.example.domain.model.WebhookActionInfo
import com.example.util.DeviceAction
import com.example.util.DeviceActionEngine
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import com.example.domain.model.YouTubeStudyResult
import com.example.util.DocumentExtractor
import com.example.util.ImageEncoder
import com.example.util.LiveLensHistoryStore
import com.example.util.SemanticEmbeddingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class SummaryProgress(
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val statusText: String = "",
    val partialOutput: String = "",
    val isCompleted: Boolean = false
)

data class RoutineRunResult(
    val routineName: String,
    val summaryText: String,
    val spokenText: String? = null,
    val pendingDeviceAction: DeviceAction? = null,
    val savedNote: VoiceNoteInfo? = null,
    val modelLabel: String = "ROUTINE"
)

class AllChatRepository(
    private val context: Context,
    private val providerDao: ProviderDao,
    private val modelDao: ModelDao,
    private val personaDao: PersonaDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val knowledgeDocDao: KnowledgeDocDao,
    private val generatedMediaDao: GeneratedMediaDao,
    private val userMemoryDao: UserMemoryDao,
    private val routineDao: RoutineDao,
    private val voiceNoteDao: VoiceNoteDao,
    private val expenseDao: ExpenseDao,
    private val watchlistDao: WatchlistDao,
    private val webhookDao: WebhookDao,
    private val canvasVersionDao: CanvasVersionDao,
    private val clipboardDao: ClipboardDao,
    private val apiKeyVault: ApiKeyVault,
    private val aiRouter: AiRouter,
    private val webSearchClient: WebSearchClient,
    private val okHttpClient: OkHttpClient
) {
    private val youtubeExtractorClient = YouTubeExtractorClient(okHttpClient, webSearchClient)

    val providerConfigRepository: ProviderConfigRepository = ProviderConfigRepository(
        providerDao = providerDao,
        modelDao = modelDao,
        apiKeyVault = apiKeyVault,
        aiRouter = aiRouter
    )

    // 60-second cooldown tracker per modelDbId when HTTP 429 / RESOURCE_EXHAUSTED is encountered
    private val modelCooldownUntilMs = ConcurrentHashMap<Long, Long>()
    private val _cooldownSnapshotFlow = kotlinx.coroutines.flow.MutableStateFlow<Map<Long, Long>>(emptyMap())
    val cooldownSnapshotFlow: StateFlow<Map<Long, Long>> = _cooldownSnapshotFlow

    private fun markModelRateLimited(modelId: Long, durationMs: Long = 60_000L) {
        val until = System.currentTimeMillis() + durationMs
        modelCooldownUntilMs[modelId] = until
        _cooldownSnapshotFlow.value = modelCooldownUntilMs.toMap()
    }

    private fun markProviderRateLimited(providerId: Long, allModels: List<SelectableModel>, durationMs: Long = 60_000L) {
        val until = System.currentTimeMillis() + durationMs
        val providerModels = allModels.filter { it.provider.id == providerId }
        if (providerModels.isEmpty()) return
        providerModels.forEach { m ->
            modelCooldownUntilMs[m.model.id] = until
        }
        _cooldownSnapshotFlow.value = modelCooldownUntilMs.toMap()
    }

    private fun isRateLimitOrUnavailableError(t: Throwable): Boolean {
        if (isRateLimitError(t)) return true
        val msg = t.message.orEmpty().lowercase()
        return msg.contains("503") ||
            msg.contains("service unavailable") ||
            msg.contains("overloaded") ||
            msg.contains("temporarily unavailable") ||
            msg.contains("capacity") ||
            msg.contains("server busy")
    }

    // --- Vault Lock, Themes & Command Preferences ---

    val isLockedFlow: StateFlow<Boolean> = apiKeyVault.isLocked
    val isPinEnabledFlow: Flow<Boolean> = apiKeyVault.isPinEnabledFlow
    val displayCurrencyFlow: Flow<DisplayCurrency> = apiKeyVault.displayCurrencyFlow
    val themePresetIdFlow: Flow<String> = apiKeyVault.themePresetIdFlow
    val showDetailsFlow: Flow<Boolean> = apiKeyVault.showDetailsFlow
    val showThinkingFlow: Flow<Boolean> = apiKeyVault.showThinkingFlow
    val agentsMdFlow: Flow<String> = apiKeyVault.agentsMdFlow
    val mcpServersFlow: Flow<List<McpServerConfig>> = apiKeyVault.mcpServersFlow
    val sharedSessionsFlow: Flow<Map<Long, String>> = apiKeyVault.sharedSessionsFlow
    val searchProviderFlow: Flow<WebSearchProvider> = apiKeyVault.searchProviderFlow
    val hasSearchApiKeyFlow: Flow<Boolean> = apiKeyVault.hasSearchApiKeyFlow
    val timeProfilesEnabledFlow: Flow<Boolean> = apiKeyVault.timeProfilesEnabledFlow
    val autoEcoModeFlow: Flow<Boolean> = apiKeyVault.autoEcoModeFlow
    val autoDeleteJunkChatsFlow: Flow<Boolean> = apiKeyVault.autoDeleteJunkChatsFlow
    val localWorkspaceUriFlow: Flow<String?> = apiKeyVault.localWorkspaceUriFlow
    val wakePhraseEnabledFlow: Flow<Boolean> = apiKeyVault.wakePhraseEnabledFlow
    val ambientWhisperEnabledFlow: Flow<Boolean> = apiKeyVault.ambientWhisperEnabledFlow
    val autoDialWebhookUrlFlow: Flow<String> = apiKeyVault.autoDialWebhookUrlFlow
    val smartZonesFlow: Flow<List<SmartZone>> = apiKeyVault.smartZonesFlow

    suspend fun setTimeProfilesEnabled(enabled: Boolean) = apiKeyVault.setTimeProfilesEnabled(enabled)
    suspend fun setAutoEcoMode(enabled: Boolean) = apiKeyVault.setAutoEcoMode(enabled)
    suspend fun setAutoDeleteJunkChats(enabled: Boolean) = apiKeyVault.setAutoDeleteJunkChats(enabled)
    suspend fun setLocalWorkspaceUri(uri: String?) = apiKeyVault.setLocalWorkspaceUri(uri)
    suspend fun setWakePhraseEnabled(enabled: Boolean) = apiKeyVault.setWakePhraseEnabled(enabled)
    suspend fun setAmbientWhisperEnabled(enabled: Boolean) = apiKeyVault.setAmbientWhisperEnabled(enabled)
    suspend fun setAutoDialWebhookUrl(url: String) = apiKeyVault.setAutoDialWebhookUrl(url)
    suspend fun saveSmartZones(zones: List<SmartZone>) = apiKeyVault.saveSmartZones(zones)
    val customSlashCommandsFlow: Flow<List<CustomSlashCommand>> = apiKeyVault.customSlashCommandsFlow
    val autoRouterConfigFlow: Flow<AutoRouterConfig> = apiKeyVault.autoRouterConfigFlow
    val autoRouteEnabledFlow: Flow<Boolean> = apiKeyVault.autoRouteEnabledFlow
    val autoExtractMemoriesFlow: Flow<Boolean> = apiKeyVault.autoExtractMemoriesFlow
    val activeConversationIdFlow: Flow<Long?> = apiKeyVault.activeConversationIdFlow
    val activeModelDbIdFlow: Flow<Long?> = apiKeyVault.activeModelDbIdFlow
    val quickContactsFlow: Flow<List<QuickContact>> = apiKeyVault.quickContactsFlow
    val customAppsFlow: Flow<List<CustomAppEntry>> = apiKeyVault.customAppsFlow
    val visionFallbackModelIdsFlow: Flow<List<Long>> = apiKeyVault.visionFallbackModelIdsFlow
    val liveLensIntervalSecFlow: Flow<Int> = apiKeyVault.liveLensIntervalSecFlow
    val knowledgeDocsFlow: Flow<List<KnowledgeDocInfo>> = knowledgeDocDao.observeDocs().map { list ->
        list.map { it.toDomain() }
    }
    val pinnedModelIdsFlow: Flow<List<Long>> = apiKeyVault.pinnedModelIdsFlow
    val modelLatencyMsFlow: Flow<Map<Long, Long>> = apiKeyVault.modelLatencyMsFlow
    val fastUtilityModelIdFlow: Flow<Long?> = apiKeyVault.fastUtilityModelIdFlow
    val activeMemoryProfileFlow: Flow<MemoryProfile> = apiKeyVault.activeMemoryProfileFlow
    val semanticAutoAttachFlow: Flow<Boolean> = apiKeyVault.semanticAutoAttachFlow

    val memoriesFlow: Flow<List<UserMemoryInfo>> = combine(
        userMemoryDao.observeAll(),
        apiKeyVault.pinnedMemoryIdsFlow,
        apiKeyVault.memoryProfileMapFlow
    ) { list, pinnedIds, profileMap ->
        list.map { entity ->
            entity.toDomain().copy(
                isPinned = entity.id in pinnedIds,
                profile = profileMap[entity.id] ?: "ALL"
            )
        }.sortedWith(
            compareByDescending<UserMemoryInfo> { it.isPinned }
                .thenByDescending { it.createdAt }
        )
    }

    suspend fun togglePinnedModelId(modelDbId: Long, maxPins: Int = 4): Pair<Boolean, Boolean> =
        apiKeyVault.togglePinnedModelId(modelDbId, maxPins)

    suspend fun recordModelLatency(modelDbId: Long, latencyMs: Long) =
        apiKeyVault.recordModelLatency(modelDbId, latencyMs)

    suspend fun setFastUtilityModelId(modelDbId: Long?) =
        apiKeyVault.setFastUtilityModelId(modelDbId)

    suspend fun setActiveMemoryProfile(profile: MemoryProfile) =
        apiKeyVault.setActiveMemoryProfile(profile)

    suspend fun setMemoryProfile(memoryId: Long, profile: String) =
        apiKeyVault.setMemoryProfile(memoryId, profile)

    suspend fun setSemanticAutoAttach(enabled: Boolean) =
        apiKeyVault.setSemanticAutoAttach(enabled)

    /**
     * Resolves the user's designated Fast Utility Model (Dual-Lane Cloud Routing)
     * for lightweight background tasks (auto-titling, memory extraction, Live Lens tagging,
     * quick clipboard summaries), falling back to [fallback] if none is assigned.
     */
    suspend fun resolveFastUtilityModel(fallback: SelectableModel?): SelectableModel? {
        val fastId = apiKeyVault.fastUtilityModelIdFlow.first()
        if (fastId != null && fastId > 0L) {
            val available = observeModelsForCapability(ModelCapability.TEXT_CHAT).first()
            available.firstOrNull { it.model.id == fastId }?.let { return it }
        }
        return fallback
    }
    val routinesFlow: Flow<List<AssistantRoutine>> = routineDao.observeAll().map { list ->
        list.map { it.toDomainRoutine() }
    }
    val voiceNotesFlow: Flow<List<VoiceNoteInfo>> = voiceNoteDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun setActiveConversationId(conversationId: Long?) =
        apiKeyVault.setActiveConversationId(conversationId)

    suspend fun setActiveModelDbId(modelDbId: Long?) =
        apiKeyVault.setActiveModelDbId(modelDbId)

    suspend fun saveQuickContact(contact: QuickContact) =
        apiKeyVault.saveQuickContact(contact)

    suspend fun saveQuickContactsBulk(contacts: List<QuickContact>): Int =
        apiKeyVault.saveQuickContactsBulk(contacts)

    suspend fun deleteQuickContact(id: String) =
        apiKeyVault.deleteQuickContact(id)

    suspend fun saveCustomApp(entry: CustomAppEntry) =
        apiKeyVault.saveCustomApp(entry)

    suspend fun deleteCustomApp(id: String) =
        apiKeyVault.deleteCustomApp(id)

    suspend fun setVisionFallbackModelIds(ids: List<Long>) =
        apiKeyVault.setVisionFallbackModelIds(ids)

    suspend fun setLiveLensIntervalSec(seconds: Int) =
        apiKeyVault.setLiveLensIntervalSec(seconds)

    /**
     * Reads a fresh [SelectableModel] (with up-to-date provider baseUrl, proxyUrl, and modelId)
     * directly from Room right before sending so idle gaps never hold stale closure values.
     */
    suspend fun resolveFreshModel(modelDbId: Long? = null): SelectableModel? = withContext(Dispatchers.IO) {
        val available = observeModelsForCapability(ModelCapability.TEXT_CHAT).first()
        if (available.isEmpty()) return@withContext null
        val targetId = modelDbId ?: activeModelDbIdFlow.first()
        available.firstOrNull { it.model.id == targetId } ?: resolvePreferredChatModel()
    }

    /**
     * Resolves the currently active persisted conversation ID (shared across Main Chat,
     * Mini-Window mode, Assistant Overlay, and Widget), falling back to the latest existing
     * conversation or creating a new one if none exist yet.
     */
    suspend fun getOrCreateActiveConversationId(
        defaultTitle: String = "New Chat",
        modelDbId: Long? = null,
        personaId: Long? = null
    ): Long {
        val allConvs = conversationsFlow.first()
        val storedId = activeConversationIdFlow.first()
        if (storedId != null && allConvs.any { it.id == storedId }) {
            return storedId
        }
        val latestExisting = allConvs.firstOrNull()?.id
        if (latestExisting != null) {
            apiKeyVault.setActiveConversationId(latestExisting)
            return latestExisting
        }
        val created = createConversation(defaultTitle, modelDbId, personaId)
        apiKeyVault.setActiveConversationId(created)
        return created
    }

    /**
     * Appends a local user+assistant exchange (e.g., confirmed Device Action) directly to the
     * active leaf of [conversationId] so multi-turn history is never lost.
     */
    suspend fun appendInstantExchange(
        conversationId: Long,
        userText: String,
        assistantText: String,
        modelLabel: String = "DEVICE ACTION"
    ) {
        val existingMessages = messageDao.getMessages(conversationId)
        val shouldAutoTitle = existingMessages.isEmpty() && userText.isNotBlank()
        val leafParentId = ConversationTree
            .resolveActiveBranch(existingMessages, emptyMap())
            .lastOrNull()?.message?.id

        val userId = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                parentMessageId = leafParentId,
                role = "USER",
                content = userText.trim()
            )
        )
        messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                parentMessageId = userId,
                role = "ASSISTANT",
                content = assistantText.trim(),
                modelLabel = modelLabel
            )
        )
        if (shouldAutoTitle) {
            conversationDao.rename(conversationId, userText.trim().replace("\n", " ").take(40))
        } else {
            conversationDao.touch(conversationId)
        }
    }

    suspend fun initializeLockState() = apiKeyVault.initializeLockState()
    suspend fun setPinPassphrase(passphrase: String): Result<Unit> = apiKeyVault.setPinPassphrase(passphrase)
    suspend fun unlockWithPin(passphrase: String): Boolean = apiKeyVault.unlockWithPin(passphrase)
    fun lockNow() = apiKeyVault.lockNow()
    suspend fun disablePinPassphrase() = apiKeyVault.disablePinPassphrase()
    val autoLockMinutesFlow: Flow<Int> = apiKeyVault.autoLockMinutesFlow
    suspend fun setAutoLockMinutes(minutes: Int) = apiKeyVault.setAutoLockMinutes(minutes)
    val lockOnAppSwitchFlow: Flow<Boolean> = apiKeyVault.lockOnAppSwitchFlow
    suspend fun setLockOnAppSwitch(enabled: Boolean) = apiKeyVault.setLockOnAppSwitch(enabled)
    val dailyBudgetConfigFlow: Flow<DailyBudgetConfig> = apiKeyVault.dailyBudgetConfigFlow
    suspend fun saveDailyBudgetConfig(config: DailyBudgetConfig) = apiKeyVault.saveDailyBudgetConfig(config)
    val voiceFormTemplatesFlow: Flow<List<VoiceFormTemplate>> = apiKeyVault.voiceFormTemplatesFlow
    suspend fun saveVoiceFormTemplates(templates: List<VoiceFormTemplate>) = apiKeyVault.saveVoiceFormTemplates(templates)
    val threadCheckpointsFlow: Flow<Map<Long, List<ThreadCheckpoint>>> = apiKeyVault.threadCheckpointsFlow

    fun observeTodaySpendUsd(): Flow<Double> {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return messageDao.observeSpendSince(cal.timeInMillis)
    }

    suspend fun getTodaySpendUsd(): Double = withContext(Dispatchers.IO) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        messageDao.getSpendSince(cal.timeInMillis)
    }

    suspend fun setDisplayCurrency(currency: DisplayCurrency) = apiKeyVault.setDisplayCurrency(currency)
    suspend fun setThemePresetId(presetId: String) = apiKeyVault.setThemePresetId(presetId)
    suspend fun setShowDetails(show: Boolean) = apiKeyVault.setShowDetails(show)
    suspend fun setShowThinking(show: Boolean) = apiKeyVault.setShowThinking(show)
    suspend fun setAgentsMd(content: String) = apiKeyVault.setAgentsMd(content)
    suspend fun setSearchProvider(provider: WebSearchProvider) = apiKeyVault.setSearchProvider(provider)
    suspend fun saveSearchApiKey(rawKey: String) = apiKeyVault.saveSearchApiKey(rawKey)
    suspend fun getSearchApiKey(): String = apiKeyVault.getSearchApiKey()
    suspend fun setAutoRouteEnabled(enabled: Boolean) = apiKeyVault.setAutoRouteEnabled(enabled)
    suspend fun setAutoExtractMemories(enabled: Boolean) = apiKeyVault.setAutoExtractMemories(enabled)
    suspend fun saveAutoRouterConfig(config: AutoRouterConfig) = apiKeyVault.saveAutoRouterConfig(config)

    // --- User Memory Bank (Persistent Auto-Extracted & Manual Facts) ---

    private fun normalizeMemoryTokens(text: String): Set<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .toSet()

    private fun isDuplicateMemoryFact(candidate: String, existingFacts: List<String>): Boolean {
        val normCandidate = candidate.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (normCandidate.length < 4) return true
        val candidateTokens = normalizeMemoryTokens(candidate)
        return existingFacts.any { existing ->
            val normExisting = existing.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (normExisting == normCandidate) return@any true
            if (normExisting.length >= 10 && normCandidate.length >= 10 &&
                (normExisting.contains(normCandidate) || normCandidate.contains(normExisting))
            ) {
                return@any true
            }
            val existingTokens = normalizeMemoryTokens(existing)
            if (candidateTokens.size >= 3 && existingTokens.size >= 3) {
                val intersection = candidateTokens.intersect(existingTokens).size
                val union = candidateTokens.union(existingTokens).size
                if (union > 0 && (intersection.toFloat() / union.toFloat()) >= 0.72f) {
                    return@any true
                }
            }
            false
        }
    }

    /**
     * Detects when a newly extracted fact updates/contradicts an older memory on the same
     * preference key or subject (e.g., switching stack, exam subject, editor, or output rule).
     */
    private fun findConflictingMemory(
        candidate: String,
        category: String,
        existing: List<UserMemoryEntity>
    ): UserMemoryEntity? {
        val subjectKeys = listOf(
            "preferred language", "favorite language", "primary language", "programming language",
            "tech stack", "preferred stack", "framework", "default editor", "ide",
            "output style", "formatting preference", "response style", "tone",
            "currently studying", "studying", "exam", "course", "subject", "major",
            "working on", "current project", "job", "role", "occupation",
            "timezone", "location", "lives in", "based in", "name is", "operating system", "os "
        )
        val candLow = candidate.lowercase()
        val matchedKeys = subjectKeys.filter { candLow.contains(it) }
        val candTokens = normalizeMemoryTokens(candidate)

        for (entry in existing) {
            val exLow = entry.fact.lowercase()
            if (matchedKeys.isNotEmpty() && matchedKeys.any { exLow.contains(it) }) {
                return entry
            }
            // Also detect prefix/subject alignment ("User prefers ...", "Stack: ...", "Exam: ...")
            val candPrefix = candLow.substringBefore(':').trim()
            val exPrefix = exLow.substringBefore(':').trim()
            if (candPrefix.length in 3..26 && candLow.contains(':') && exLow.contains(':') && candPrefix == exPrefix) {
                return entry
            }
            // Same category with strong subject overlap (0.45..0.71 -> likely update/contradiction)
            if (entry.category.equals(category, ignoreCase = true)) {
                val exTokens = normalizeMemoryTokens(entry.fact)
                if (candTokens.size >= 3 && exTokens.size >= 3) {
                    val inter = candTokens.intersect(exTokens).size
                    val union = candTokens.union(exTokens).size
                    val ratio = if (union > 0) inter.toFloat() / union.toFloat() else 0f
                    if (ratio >= 0.48f) return entry
                }
            }
        }
        return null
    }

    suspend fun addMemory(
        fact: String,
        category: String = "Preference",
        source: String = "Manual",
        profile: String? = null
    ): Long {
        val clean = fact.trim()
        if (clean.isEmpty()) return -1L
        val cleanCat = category.trim().ifBlank { "Preference" }
        val targetProfile = (profile ?: apiKeyVault.activeMemoryProfileFlow.first().name).uppercase()
        val existing = userMemoryDao.getAll()
        if (isDuplicateMemoryFact(clean, existing.map { it.fact })) return -1L

        // Automatic contradiction overwrite: update stale conflicting fact in-place instead of duplicating
        val conflicting = findConflictingMemory(clean, cleanCat, existing)
        if (conflicting != null) {
            userMemoryDao.update(
                conflicting.copy(
                    fact = clean,
                    category = cleanCat,
                    isEnabled = true,
                    source = "$source (Updated)",
                    createdAt = System.currentTimeMillis()
                )
            )
            apiKeyVault.setMemoryProfile(conflicting.id, targetProfile)
            return conflicting.id
        }

        val insertedId = userMemoryDao.insert(
            UserMemoryEntity(
                fact = clean,
                category = cleanCat,
                isEnabled = true,
                source = source
            )
        )
        if (insertedId > 0L) {
            apiKeyVault.setMemoryProfile(insertedId, targetProfile)
        }
        return insertedId
    }

    suspend fun updateMemory(memory: UserMemoryInfo) {
        val clean = memory.fact.trim()
        if (clean.isEmpty()) return
        userMemoryDao.update(
            UserMemoryEntity(
                id = memory.id,
                fact = clean,
                category = memory.category.trim().ifBlank { "Preference" },
                isEnabled = memory.isEnabled,
                source = memory.source,
                createdAt = memory.createdAt
            )
        )
        apiKeyVault.setMemoryProfile(memory.id, memory.profile)
    }

    suspend fun toggleMemory(id: Long, enabled: Boolean) {
        userMemoryDao.setEnabled(id, enabled)
    }

    suspend fun setMemoryPinned(id: Long, pinned: Boolean) {
        apiKeyVault.setMemoryPinned(id, pinned)
        if (pinned) {
            userMemoryDao.setEnabled(id, true)
        }
    }

    suspend fun deleteMemory(id: Long) {
        userMemoryDao.deleteById(id)
        apiKeyVault.setMemoryPinned(id, false)
    }

    suspend fun clearAllMemories() {
        userMemoryDao.deleteAll()
        apiKeyVault.clearPinnedMemoryIds()
        apiKeyVault.clearMemoryProfiles()
    }

    /**
     * Token-saving payload trimmer: keeps only the latest 2 messages bounded strictly to 800 characters
     * total so background utility API calls stay under ~250 tokens and avoid rate limits.
     */
    private fun trimLatestTwoMessagesTo800Chars(userMessage: String, assistantMessage: String): String {
        val uClean = userMessage.trim().replace(Regex("\\s+"), " ").take(450)
        val remaining = (800 - uClean.length - 16).coerceAtLeast(180)
        val aClean = assistantMessage.trim().replace(Regex("\\s+"), " ").take(remaining)
        return "U: $uClean\nA: $aClean".take(800)
    }

    suspend fun extractMemoriesFromTurn(
        userMessage: String,
        assistantMessage: String,
        selectableModel: SelectableModel
    ): Int = withContext(Dispatchers.IO) {
        if (userMessage.length < 16) return@withContext 0
        // Route exclusively to Fast Utility Model when designated
        val utilityModel = resolveFastUtilityModel(selectableModel) ?: selectableModel
        val existingEntities = userMemoryDao.getAll()
        val activeProfile = apiKeyVault.activeMemoryProfileFlow.first().name
        val apiKey = apiKeyVault.getApiKey(utilityModel.provider.id)

        val compactExisting = existingEntities.take(12).joinToString("; ") { "[${it.id}] ${it.fact.take(64)}" }
        val trimmedExchange = trimLatestTwoMessagesTo800Chars(userMessage, assistantMessage)
        val prompt = """
            Extract 0-2 durable user facts/preferences. If a fact updates/contradicts an existing memory [id], set "replaces_id":id. No duplicates.
            Existing: $compactExisting
            Return JSON array only: [{"fact":"...","category":"Preference|Tech|Profile|Workflow","replaces_id":null}]
            $trimmedExchange
        """.trimIndent()

        val raw = runCatching {
            collectSingleCompletion(utilityModel, apiKey, prompt, maxTokens = 160) {}
        }.getOrDefault("")
        val cleaned = extractJsonArray(raw) ?: return@withContext 0
        var savedOrUpdated = 0
        runCatching {
            val arr = JSONArray(cleaned)
            for (i in 0 until arr.length().coerceAtMost(2)) {
                val obj = arr.optJSONObject(i) ?: continue
                val fact = obj.optString("fact").trim()
                val cat = obj.optString("category", "Preference").trim().ifBlank { "Preference" }
                val replacesId = obj.optLong("replaces_id", -1L).takeIf { it > 0L }
                if (fact.length < 6) continue

                val replaceTarget = replacesId?.let { rid -> existingEntities.firstOrNull { it.id == rid } }
                if (replaceTarget != null) {
                    userMemoryDao.update(
                        replaceTarget.copy(
                            fact = fact,
                            category = cat,
                            isEnabled = true,
                            source = "Auto (Updated)",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    apiKeyVault.setMemoryProfile(replaceTarget.id, activeProfile)
                    savedOrUpdated++
                } else if (addMemory(fact, cat, source = "Auto", profile = activeProfile) > 0L) {
                    savedOrUpdated++
                }
            }
        }
        savedOrUpdated
    }

    /**
     * Background Fast Utility Model session auto-titler using strictly the latest 2 messages (<= 800 chars).
     */
    suspend fun autoTitleConversationFast(
        conversationId: Long,
        userMessage: String,
        assistantMessage: String,
        fallbackModel: SelectableModel
    ) = withContext(Dispatchers.IO) {
        val conv = conversationDao.getById(conversationId) ?: return@withContext
        val utilityModel = resolveFastUtilityModel(fallbackModel) ?: fallbackModel
        val apiKey = apiKeyVault.getApiKey(utilityModel.provider.id)
        val trimmed = trimLatestTwoMessagesTo800Chars(userMessage, assistantMessage)
        val prompt = "Write a concise 3-5 word title for this chat (no quotes, no punctuation):\n$trimmed"
        val generated = runCatching {
            collectSingleCompletion(utilityModel, apiKey, prompt, maxTokens = 28) {}
        }.getOrDefault("")
            .replace(Regex("""["'`\n\r]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(44)
        if (generated.length >= 3 && generated != conv.title) {
            conversationDao.rename(conversationId, generated)
        }
    }

    /**
     * Lightweight Live Lens image tagging via Fast Utility Model (<= 800 chars input).
     */
    suspend fun tagLiveLensAnswerFast(
        answerText: String,
        fallbackModel: SelectableModel?
    ): String? = withContext(Dispatchers.IO) {
        val utilityModel = resolveFastUtilityModel(fallbackModel) ?: return@withContext null
        val apiKey = apiKeyVault.getApiKey(utilityModel.provider.id)
        val trimmed = answerText.trim().replace(Regex("\\s+"), " ").take(650)
        if (trimmed.length < 20) return@withContext null
        val prompt = "Return 1 short topic tag (2-3 words max, e.g. Calculus Integral, Circuit KVL, Kotlin Bug) for:\n$trimmed"
        runCatching {
            collectSingleCompletion(utilityModel, apiKey, prompt, maxTokens = 20) {}
        }.getOrNull()
            ?.replace(Regex("""["'`#\n\r]"""), "")
            ?.trim()
            ?.take(28)
            ?.takeIf { it.length >= 2 }
    }

    /**
     * Quick clipboard summary routed exclusively to the Fast Utility Model (<= 800 chars).
     */
    suspend fun summarizeClipboardFast(
        clipboardText: String,
        fallbackModel: SelectableModel?
    ): String? = withContext(Dispatchers.IO) {
        val utilityModel = resolveFastUtilityModel(fallbackModel) ?: return@withContext null
        val apiKey = apiKeyVault.getApiKey(utilityModel.provider.id)
        val trimmed = clipboardText.trim().take(800)
        if (trimmed.length < 20) return@withContext null
        val prompt = "Summarize this clipboard snippet in 2 crisp bullet points:\n$trimmed"
        runCatching {
            collectSingleCompletion(utilityModel, apiKey, prompt, maxTokens = 120) {}
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun saveCustomSlashCommand(command: CustomSlashCommand) {
        val normalizedTrigger = "/" + command.trigger.trim().removePrefix("/")
            .replace(Regex("[^A-Za-z0-9_-]"), "")
            .lowercase()
            .ifBlank { "custom" }
        val normalized = command.copy(
            id = command.id.ifBlank { UUID.randomUUID().toString().take(8) },
            trigger = normalizedTrigger,
            description = command.description.trim().ifBlank { "Custom prompt template" },
            template = command.template.trim()
        )
        if (normalized.template.isEmpty()) return
        val current = apiKeyVault.customSlashCommandsFlow.first().toMutableList()
        val idx = current.indexOfFirst { it.id == normalized.id || it.trigger.equals(normalizedTrigger, ignoreCase = true) }
        if (idx >= 0) current[idx] = normalized else current.add(normalized)
        apiKeyVault.saveCustomSlashCommands(current)
    }

    suspend fun deleteCustomSlashCommand(id: String) {
        val updated = apiKeyVault.customSlashCommandsFlow.first().filterNot { it.id == id }
        apiKeyVault.saveCustomSlashCommands(updated)
    }

    // --- Local Knowledge Base (Client-Side Document RAG) ---

    suspend fun addKnowledgeDocFromUri(uri: Uri): KnowledgeDocInfo = withContext(Dispatchers.IO) {
        val extracted = DocumentExtractor.extractFromUri(context, uri)
        val existingSlugs = knowledgeDocDao.getAll().map { it.slug }.toSet()
        var slug = LocalRagEngine.slugify(extracted.title)
        var counter = 2
        while (slug in existingSlugs) {
            slug = "${LocalRagEngine.slugify(extracted.title)}-$counter"
            counter++
        }
        val chunks = LocalRagEngine.splitIntoChunks(extracted.text)
        val entity = KnowledgeDocEntity(
            slug = slug,
            title = extracted.title.substringBeforeLast('.').ifBlank { extracted.title },
            fileName = extracted.title,
            content = extracted.text,
            chunkCount = chunks.size.coerceAtLeast(1)
        )
        val id = knowledgeDocDao.insert(entity)
        entity.copy(id = id).toDomain()
    }

    suspend fun addKnowledgeDocManual(title: String, content: String): KnowledgeDocInfo = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifBlank { "Untitled Note" }
        val existingSlugs = knowledgeDocDao.getAll().map { it.slug }.toSet()
        var slug = LocalRagEngine.slugify(cleanTitle)
        var counter = 2
        while (slug in existingSlugs) {
            slug = "${LocalRagEngine.slugify(cleanTitle)}-$counter"
            counter++
        }
        val chunks = LocalRagEngine.splitIntoChunks(content)
        val entity = KnowledgeDocEntity(
            slug = slug,
            title = cleanTitle,
            fileName = "$slug.md",
            content = content.trim(),
            chunkCount = chunks.size.coerceAtLeast(1)
        )
        val id = knowledgeDocDao.insert(entity)
        entity.copy(id = id).toDomain()
    }

    suspend fun renameKnowledgeDoc(id: Long, newTitle: String, newSlug: String) {
        val cleanSlug = LocalRagEngine.slugify(newSlug.ifBlank { newTitle })
        val cleanTitle = newTitle.trim().ifBlank { cleanSlug }
        knowledgeDocDao.rename(id, cleanSlug, cleanTitle)
    }

    suspend fun deleteKnowledgeDoc(id: Long) {
        knowledgeDocDao.deleteById(id)
    }

    // --- ChatGPT / Claude Conversation Importer ---

    suspend fun importExternalConversations(rawJson: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = ConversationImporter.parseExportJson(rawJson)
            var totalMessages = 0
            parsed.conversations.forEach { conv ->
                val convId = conversationDao.insert(
                    ConversationEntity(
                        title = conv.title.take(80),
                        createdAt = conv.createdAt,
                        updatedAt = conv.updatedAt
                    )
                )
                var parentId: Long? = null
                conv.messages.forEach { msg ->
                    val newMsgId = messageDao.insert(
                        MessageEntity(
                            conversationId = convId,
                            parentMessageId = parentId,
                            role = msg.role,
                            content = msg.content,
                            modelLabel = if (msg.role == "ASSISTANT") parsed.sourceLabel else null,
                            createdAt = msg.createdAt
                        )
                    )
                    parentId = newMsgId
                    totalMessages++
                }
            }
            "Imported ${parsed.conversations.size} conversations ($totalMessages messages) from ${parsed.sourceLabel}."
        }
    }

    // --- Smart Auto-Router Specialist Selection ---

    fun resolveAutoRouteModel(
        promptText: String,
        hasImageAttachment: Boolean,
        historyCharLength: Int,
        availableModels: List<SelectableModel>,
        config: AutoRouterConfig,
        fallback: SelectableModel?
    ): Pair<SelectableModel?, String> {
        if (availableModels.isEmpty()) return null to "No models"
        fun byId(id: Long?) = id?.let { target -> availableModels.firstOrNull { it.model.id == target } }

        // 1. Vision task
        if (hasImageAttachment) {
            val vision = byId(config.visionModelId)
                ?: availableModels.firstOrNull { it.model.supportsImageInput }
                ?: fallback
                ?: availableModels.first()
            return vision to "Vision"
        }

        // 2. Long-context task (> ~10k tokens ~= 36,000 chars)
        val totalChars = historyCharLength + promptText.length
        if (totalChars >= 36_000) {
            val longCtx = byId(config.longContextModelId)
                ?: availableModels.firstOrNull {
                    it.model.modelId.contains("gemini", ignoreCase = true) ||
                        it.model.modelId.contains("claude", ignoreCase = true) ||
                        it.model.modelId.contains("long", ignoreCase = true)
                }
                ?: fallback
                ?: availableModels.first()
            return longCtx to "Long Context"
        }

        // 3. Heavy coding / technical task
        val lower = promptText.lowercase()
        val isCoding = promptText.contains("```") ||
            Regex("""\b(fun |class |def |import |function |const |SELECT |refactor|debug|stack trace|exception|regex|algorithm|dockerfile|kubernetes|typescript|kotlin|python|rust|sql)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(lower)
        if (isCoding) {
            val coder = byId(config.heavyCodingModelId)
                ?: availableModels.firstOrNull {
                    val m = it.model.modelId.lowercase()
                    m.contains("claude") || m.contains("deepseek") || m.contains("coder") ||
                        m.contains("o1") || m.contains("o3") || m.contains("pro")
                }
                ?: fallback
                ?: availableModels.first()
            return coder to "Heavy Coding"
        }

        // 4. Fast general chat
        val fast = byId(config.fastChatModelId)
            ?: availableModels.firstOrNull {
                val m = it.model.modelId.lowercase()
                m.contains("flash") || m.contains("mini") || m.contains("haiku") || m.contains("8b")
            }
            ?: fallback
            ?: availableModels.first()
        return fast to "Fast Chat"
    }

    suspend fun saveMcpServer(server: McpServerConfig) {
        val current = apiKeyVault.mcpServersFlow.first().toMutableList()
        val idx = current.indexOfFirst { it.id == server.id }
        val normalized = server.copy(
            id = server.id.ifBlank { UUID.randomUUID().toString().take(8) },
            name = server.name.trim().ifBlank { "MCP Server" },
            url = server.url.trim()
        )
        if (idx >= 0) current[idx] = normalized else current.add(normalized)
        apiKeyVault.saveMcpServers(current)
    }

    suspend fun deleteMcpServer(serverId: String) {
        val updated = apiKeyVault.mcpServersFlow.first().filterNot { it.id == serverId }
        apiKeyVault.saveMcpServers(updated)
    }

    suspend fun checkMcpServer(server: McpServerConfig): McpCheckResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val url = server.url.trim()
        if (url.isBlank()) {
            return@withContext McpCheckResult(server.id, false, 0, "Empty server URL")
        }
        val initRpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "initialize")
            .put(
                "params",
                JSONObject()
                    .put("protocolVersion", "2024-11-05")
                    .put("capabilities", JSONObject())
                    .put("clientInfo", JSONObject().put("name", "AllChat").put("version", "2.0"))
            )
            .toString()

        val jsonMedia = "application/json; charset=utf-8".toMediaType()
        val postReqBuilder = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json, text/event-stream")
            .post(initRpc.toRequestBody(jsonMedia))
        if (server.authToken.isNotBlank()) {
            postReqBuilder.addHeader("Authorization", "Bearer ${server.authToken.trim()}")
        }

        try {
            okHttpClient.newCall(postReqBuilder.build()).execute().use { res ->
                val elapsed = System.currentTimeMillis() - start
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val serverName = runCatching {
                        JSONObject(body)
                            .optJSONObject("result")
                            ?.optJSONObject("serverInfo")
                            ?.optString("name")
                    }.getOrNull()
                    val detail = if (!serverName.isNullOrBlank()) {
                        "MCP Ready ($serverName · HTTP ${res.code})"
                    } else {
                        "Connected (HTTP ${res.code})"
                    }
                    McpCheckResult(server.id, true, elapsed, detail)
                } else if (res.code == 405 || res.code == 400) {
                    // Fallback GET probe for SSE-only MCP servers
                    val getReq = Request.Builder().url(url).get().apply {
                        if (server.authToken.isNotBlank()) {
                            addHeader("Authorization", "Bearer ${server.authToken.trim()}")
                        }
                    }.build()
                    okHttpClient.newCall(getReq).execute().use { getRes ->
                        val total = System.currentTimeMillis() - start
                        if (getRes.isSuccessful) {
                            McpCheckResult(server.id, true, total, "SSE Endpoint Reachable (HTTP ${getRes.code})")
                        } else {
                            McpCheckResult(server.id, false, total, "HTTP ${getRes.code}")
                        }
                    }
                } else {
                    McpCheckResult(server.id, false, elapsed, "HTTP ${res.code}")
                }
            }
        } catch (t: Throwable) {
            val elapsed = System.currentTimeMillis() - start
            McpCheckResult(server.id, false, elapsed, t.message ?: "Unreachable")
        }
    }

    suspend fun shareConversation(conversationId: Long): String {
        val existing = apiKeyVault.sharedSessionsFlow.first()[conversationId]
        val code = existing ?: UUID.randomUUID().toString().replace("-", "").take(10)
        apiKeyVault.setSharedSessionCode(conversationId, code)
        return "https://opncd.ai/s/$code"
    }

    suspend fun unshareConversation(conversationId: Long) {
        apiKeyVault.setSharedSessionCode(conversationId, null)
    }

    suspend fun deleteMessagesByIds(ids: List<Long>) {
        ids.forEach { messageDao.deleteById(it) }
    }

    suspend fun restoreMessages(messages: List<MessageEntity>) {
        messages.sortedWith(compareBy<MessageEntity> { it.createdAt }.thenBy { it.id }).forEach {
            messageDao.insert(it)
        }
    }

    suspend fun compactConversation(
        conversationId: Long,
        activeBranch: List<MessageEntity>,
        selectableModel: SelectableModel,
        keepRecentCount: Int = 4
    ): MessageEntity {
        val keepCount = if (activeBranch.size > keepRecentCount) keepRecentCount else 0
        val toSummarize = if (keepCount > 0) activeBranch.dropLast(keepCount) else activeBranch
        val recentToKeep = if (keepCount > 0) activeBranch.takeLast(keepCount) else emptyList()

        val transcript = toSummarize.joinToString("\n\n") { m ->
            "${m.role}: ${m.content}"
        }
        val prompt = """
            Compact these older conversation turns into a dense, structured handoff summary so the thread can continue seamlessly with a smaller context window.
            Include:
            1. Core Goal & Context
            2. Key Decisions, Code / Technical Facts, and Outputs
            3. Open Questions / Active Constraints

            Transcript (${toSummarize.size} older messages):
            $transcript
        """.trimIndent()
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        val summary = collectSingleCompletion(selectableModel, apiKey, prompt) {}

        // Replace messages in this session with the pinned compacted context + preserved recent messages
        val existing = messageDao.getMessages(conversationId)
        existing.forEach { messageDao.deleteById(it.id) }

        val userCheckpointId = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                parentMessageId = null,
                role = "USER",
                content = "/compact — Summarized ${toSummarize.size} older messages into pinned context" +
                    if (recentToKeep.isNotEmpty()) " (preserved last ${recentToKeep.size} messages intact)." else "."
            )
        )
        val compactedEntity = MessageEntity(
            conversationId = conversationId,
            parentMessageId = userCheckpointId,
            role = "ASSISTANT",
            content = "## [COMPACTED CONTEXT]\n\n$summary",
            modelLabel = "${selectableModel.model.displayName} (Compact)"
        )
        var lastParentId = messageDao.insert(compactedEntity)
        recentToKeep.forEach { recent ->
            lastParentId = messageDao.insert(
                recent.copy(
                    id = 0,
                    conversationId = conversationId,
                    parentMessageId = lastParentId
                )
            )
        }
        conversationDao.touch(conversationId)
        return compactedEntity.copy(id = lastParentId)
    }

    suspend fun generateAgentsMd(
        projectBrief: String,
        selectableModel: SelectableModel
    ): String {
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        val prompt = """
            Generate a concise, practical project-level `AGENTS.md` instruction file for an AI coding assistant based on the following project scan / description.
            Include sections for: Architecture Overview, Build & Test Commands, Coding Conventions, and Guardrails.
            Output clean Markdown only.

            Project details:
            $projectBrief
        """.trimIndent()
        val generated = collectSingleCompletion(selectableModel, apiKey, prompt) {}
        apiKeyVault.setAgentsMd(generated)
        return generated
    }

    fun buildMarkdownTranscript(
        title: String,
        branchMessages: List<MessageEntity>,
        shareUrl: String? = null
    ): String = buildString {
        appendLine("# $title")
        if (!shareUrl.isNullOrBlank()) {
            appendLine("> Share Link: $shareUrl")
        }
        appendLine()
        branchMessages.forEachIndexed { idx, msg ->
            val header = if (msg.role == "USER") {
                "## ${idx + 1}. User"
            } else {
                "## ${idx + 1}. Assistant (${msg.modelLabel ?: "Model"})"
            }
            appendLine(header)
            appendLine()
            appendLine(msg.content)
            appendLine()
            appendLine("---")
            appendLine()
        }
    }

    // --- Providers, Models & Personas ---

    val providersFlow: Flow<List<ProviderConfig>> = providerConfigRepository.providersFlow

    val personasFlow: Flow<List<PersonaInfo>> = personaDao.observePersonas().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun ensureDefaultPersonas() {
        if (personaDao.getAll().isNotEmpty()) return
        personaDao.insertAll(
            listOf(
                PersonaEntity(
                    handle = "Coder",
                    name = "Senior Engineer",
                    systemPrompt = "You are a pragmatic senior software engineer. Output clean, idiomatic, production-ready code with concise explanations. Prefer complete working snippets over hand-wavy pseudocode.",
                    temperature = 0.2f,
                    maxTokens = 4096
                ),
                PersonaEntity(
                    handle = "Concise",
                    name = "Ultra-Brief Mode",
                    systemPrompt = "Be radically concise. Answer directly in tight bullet points or 2-4 crisp sentences. Zero filler, zero preamble.",
                    temperature = 0.3f,
                    maxTokens = 1024
                ),
                PersonaEntity(
                    handle = "Translator",
                    name = "Precision Translator",
                    systemPrompt = "Translate user text accurately while preserving tone, technical terminology, and nuance. If no target language is specified, translate to clear natural English and briefly note key idioms.",
                    temperature = 0.2f,
                    maxTokens = 2048
                ),
                PersonaEntity(
                    handle = "Reviewer",
                    name = "Architecture Critic",
                    systemPrompt = "Review the user's code, architecture, or writing critically. Identify edge cases, bugs, security issues, and concrete improvements prioritized by impact.",
                    temperature = 0.4f,
                    maxTokens = 3072
                )
            )
        )
    }

    suspend fun savePersona(persona: PersonaInfo): Long {
        val cleanHandle = persona.handle.trim().removePrefix("@").removePrefix("/")
            .replace(Regex("[^A-Za-z0-9_-]"), "")
            .ifBlank { "Custom" }
        val entity = PersonaEntity(
            id = persona.id,
            handle = cleanHandle,
            name = persona.name.trim().ifBlank { cleanHandle },
            systemPrompt = persona.systemPrompt.trim(),
            temperature = persona.temperature.coerceIn(0f, 2f),
            maxTokens = persona.maxTokens.coerceIn(128, 32768),
            defaultModelDbId = persona.defaultModelDbId
        )
        return if (persona.id <= 0L) {
            personaDao.insert(entity.copy(id = 0))
        } else {
            personaDao.update(entity)
            persona.id
        }
    }

    suspend fun deletePersona(personaId: Long) {
        personaDao.deleteById(personaId)
    }

    fun observeModelsForCapability(capability: ModelCapability): Flow<List<SelectableModel>> =
        providersFlow.combine(kotlinx.coroutines.flow.flowOf(capability)) { providers, cap ->
            providers
                .filter { it.isEnabled }
                .flatMap { provider ->
                    provider.models
                        .filter { it.supports(cap) }
                        .map { SelectableModel(it, provider) }
                }
        }

    val conversationsFlow: Flow<List<ConversationEntity>> = conversationDao.observeConversations()

    fun observeConversation(conversationId: Long): Flow<ConversationEntity?> =
        conversationDao.observeConversation(conversationId)

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.observeMessages(conversationId)

    fun observeGeneratedImages(): Flow<List<GeneratedMediaEntity>> =
        generatedMediaDao.observeByType("IMAGE")

    fun observeGeneratedVideos(): Flow<List<GeneratedMediaEntity>> =
        generatedMediaDao.observeByType("VIDEO")

    // --- Provider & Model Management ---

    suspend fun saveProvider(
        existingId: Long? = null,
        name: String,
        type: ProviderType,
        baseUrl: String,
        proxyUrl: String?,
        apiKey: String?,
        defaultModelId: String? = null,
        isEnabled: Boolean = true,
        modelIds: List<String>? = null
    ): Long {
        return providerConfigRepository.upsertProvider(
            existingId = existingId,
            name = name,
            type = type,
            baseUrl = baseUrl,
            proxyUrl = proxyUrl,
            apiKey = apiKey,
            clearApiKey = existingId == null && apiKey != null && apiKey.isBlank(),
            defaultModelId = defaultModelId,
            isEnabled = isEnabled,
            modelIds = modelIds
        )
    }

    suspend fun getProviderApiKey(providerId: Long): String =
        providerConfigRepository.getDecryptedApiKey(providerId)

    suspend fun setProviderEnabled(providerId: Long, enabled: Boolean) =
        providerConfigRepository.setProviderEnabled(providerId, enabled)

    suspend fun setProviderDefaultModel(providerId: Long, modelId: String?) =
        providerConfigRepository.setDefaultModel(providerId, modelId)

    suspend fun deleteProvider(providerId: Long) =
        providerConfigRepository.deleteProvider(providerId)

    suspend fun testProviderConnection(provider: ProviderConfig): ConnectionTestResult {
        val apiKey = apiKeyVault.getApiKey(provider.id)
        return aiRouter.testConnection(provider, apiKey)
    }

    suspend fun fetchAndSyncModels(provider: ProviderConfig): List<ModelInfo> {
        val apiKey = apiKeyVault.getApiKey(provider.id)
        val fetched: List<FetchedModel> = aiRouter.fetchModels(provider, apiKey)
        val existing = modelDao.getByProviderId(provider.id).associateBy { it.modelId }

        val toInsert = fetched.map { item ->
            val old = existing[item.modelId]
            if (old != null) {
                old.copy(displayName = item.displayName)
            } else {
                ModelEntity(
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
        }
        if (toInsert.isNotEmpty()) {
            val newIds = fetched.map { it.modelId }.toSet()
            val manualToKeep = existing.values.filter { it.modelId !in newIds }
            modelDao.deleteByProviderId(provider.id)
            modelDao.insertAll(toInsert + manualToKeep)
        }
        return modelDao.getByProviderId(provider.id).map { it.toDomain() }
    }

    suspend fun addManualModel(providerId: Long, modelId: String, displayName: String? = null) {
        val cleanId = modelId.trim()
        if (cleanId.isEmpty()) return
        val existing = modelDao.getByProviderId(providerId).firstOrNull { it.modelId == cleanId }
        if (existing != null) return
        modelDao.insert(
            ModelEntity(
                providerId = providerId,
                modelId = cleanId,
                displayName = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: cleanId,
                supportsTextChat = true,
                supportsImageInput = false,
                supportsImageGen = false,
                supportsVideoGen = false,
                supportsSummarization = false
            )
        )
    }

    suspend fun updateModelCapabilities(model: ModelInfo) {
        modelDao.update(
            ModelEntity(
                id = model.id,
                providerId = model.providerId,
                modelId = model.modelId,
                displayName = model.displayName,
                supportsTextChat = model.supportsTextChat,
                supportsImageInput = model.supportsImageInput,
                supportsImageGen = model.supportsImageGen,
                supportsVideoGen = model.supportsVideoGen,
                supportsSummarization = model.supportsSummarization,
                inputPricePerMillion = model.inputPricePerMillion,
                outputPricePerMillion = model.outputPricePerMillion
            )
        )
    }

    suspend fun updateModelPricing(
        modelDbId: Long,
        inputPricePerMillion: Double,
        outputPricePerMillion: Double
    ) {
        val existing = modelDao.getById(modelDbId) ?: return
        modelDao.update(
            existing.copy(
                inputPricePerMillion = inputPricePerMillion.coerceAtLeast(0.0),
                outputPricePerMillion = outputPricePerMillion.coerceAtLeast(0.0)
            )
        )
    }

    suspend fun deleteModel(modelDbId: Long) {
        modelDao.deleteById(modelDbId)
    }

    // --- Encrypted Config Export / Import (.allchat JSON) ---

    suspend fun exportEncryptedVaultJson(password: String): String = withContext(Dispatchers.IO) {
        val snapshot = VaultSnapshot(
            providers = providerDao.getAll(),
            apiKeysByProviderId = apiKeyVault.getAllDecryptedKeys(),
            models = modelDao.getAll(),
            personas = personaDao.getAll(),
            conversations = conversationDao.getAll(),
            messages = messageDao.getAll()
        )
        VaultBackupManager.encryptToAllChatJson(snapshot, password)
    }

    suspend fun importEncryptedVaultJson(rawJson: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val snapshot = VaultBackupManager.decryptFromAllChatJson(rawJson, password)

                // Clear existing records cleanly before restoring
                messageDao.deleteAll()
                conversationDao.deleteAll()
                modelDao.deleteAll()
                providerDao.deleteAll()
                personaDao.deleteAll()
                apiKeyVault.clearAllApiKeys()

                val providerIdMap = mutableMapOf<Long, Long>()
                snapshot.providers.forEach { p ->
                    val newId = providerDao.insert(p.copy(id = 0))
                    providerIdMap[p.id] = newId
                    val key = snapshot.apiKeysByProviderId[p.id]
                    if (!key.isNullOrBlank()) {
                        apiKeyVault.saveApiKey(newId, key)
                    }
                }

                val modelIdMap = mutableMapOf<Long, Long>()
                snapshot.models.forEach { m ->
                    val mappedProviderId = providerIdMap[m.providerId] ?: return@forEach
                    val newModelId = modelDao.insert(m.copy(id = 0, providerId = mappedProviderId))
                    modelIdMap[m.id] = newModelId
                }

                val personaIdMap = mutableMapOf<Long, Long>()
                snapshot.personas.forEach { per ->
                    val mappedDefaultModel = per.defaultModelDbId?.let { modelIdMap[it] }
                    val newPersonaId = personaDao.insert(
                        per.copy(id = 0, defaultModelDbId = mappedDefaultModel)
                    )
                    personaIdMap[per.id] = newPersonaId
                }

                val conversationIdMap = mutableMapOf<Long, Long>()
                snapshot.conversations.forEach { c ->
                    val newConvId = conversationDao.insert(
                        c.copy(
                            id = 0,
                            selectedModelDbId = c.selectedModelDbId?.let { modelIdMap[it] },
                            activePersonaId = c.activePersonaId?.let { personaIdMap[it] }
                        )
                    )
                    conversationIdMap[c.id] = newConvId
                }

                // Restore messages in chronological order so parentMessageId remaps cleanly
                val messageIdMap = mutableMapOf<Long, Long>()
                snapshot.messages
                    .sortedWith(compareBy<MessageEntity> { it.createdAt }.thenBy { it.id })
                    .forEach { msg ->
                        val mappedConvId = conversationIdMap[msg.conversationId] ?: return@forEach
                        val mappedParentId = msg.parentMessageId?.let { messageIdMap[it] }
                        val mappedErrProviderId = msg.providerIdForError?.let { providerIdMap[it] }
                        val newMsgId = messageDao.insert(
                            msg.copy(
                                id = 0,
                                conversationId = mappedConvId,
                                parentMessageId = mappedParentId,
                                providerIdForError = mappedErrProviderId
                            )
                        )
                        messageIdMap[msg.id] = newMsgId
                    }

                ensureDefaultPersonas()

                "Restored ${snapshot.providers.size} providers, ${snapshot.models.size} models, " +
                    "${snapshot.personas.size} personas & ${snapshot.conversations.size} chats."
            }
        }

    // --- Conversations & Branching Tree Streaming Chat ---

    suspend fun createConversation(
        title: String = "New Chat",
        modelDbId: Long? = null,
        personaId: Long? = null
    ): Long {
        return conversationDao.insert(
            ConversationEntity(
                title = title,
                selectedModelDbId = modelDbId,
                activePersonaId = personaId
            )
        )
    }

    suspend fun renameConversation(conversationId: Long, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isNotEmpty()) {
            conversationDao.rename(conversationId, clean)
        }
    }

    suspend fun deleteConversation(conversationId: Long) {
        conversationDao.deleteById(conversationId)
    }

    suspend fun updateConversationModel(conversationId: Long, modelDbId: Long?) {
        conversationDao.updateSelectedModel(conversationId, modelDbId)
    }

    suspend fun updateConversationPersona(conversationId: Long, personaId: Long?) {
        conversationDao.updateActivePersona(conversationId, personaId)
    }

    suspend fun persistAttachment(uri: Uri): String {
        return ImageEncoder.persistAndEncode(context, uri).localFileUri
    }

    suspend fun sendChatMessage(
        conversationId: Long,
        promptText: String,
        attachmentLocalUri: String?,
        selectableModel: SelectableModel,
        parentMessageId: Long? = null,
        options: ChatOptions = ChatOptions(),
        enableWebSearch: Boolean = false,
        autoRouteLabel: String? = null,
        onGroundingStatus: ((String) -> Unit)? = null,
        onBranchCreated: ((parentKey: Long, newChildId: Long) -> Unit)? = null,
        onSentenceReady: ((String) -> Unit)? = null,
        onDeviceActionTriggered: ((DeviceAction) -> Unit)? = null,
        onRequestError: ((String) -> Unit)? = null
    ) {
        // Re-hydrate fresh provider/baseUrl/proxyUrl right at send time (prevents stale idle state)
        val freshModel = resolveFreshModel(selectableModel.model.id) ?: selectableModel
        val existingMessages = messageDao.getMessages(conversationId)
        val shouldAutoTitle = existingMessages.isEmpty() && promptText.isNotBlank()
        // CRITICAL: Always append to the current active leaf if parentMessageId was omitted or stale,
        // so sending a follow-up never creates a sibling root that hides previous turns.
        val validExistingIds = existingMessages.map { it.id }.toSet()
        val effectiveParentId = parentMessageId?.takeIf { it in validExistingIds }
            ?: ConversationTree.resolveActiveBranch(existingMessages, emptyMap())
                .lastOrNull()?.message?.id

        val userMsgId = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                parentMessageId = effectiveParentId,
                role = "USER",
                content = promptText.trim(),
                attachmentUri = attachmentLocalUri
            )
        )
        onBranchCreated?.invoke(effectiveParentId ?: ConversationTree.ROOT_PARENT_KEY, userMsgId)

        if (shouldAutoTitle) {
            val autoTitle = promptText.trim().replace("\n", " ").take(40)
            conversationDao.rename(conversationId, autoTitle)
        } else {
            conversationDao.touch(conversationId)
        }

        val timeoutMs = if (!attachmentLocalUri.isNullOrBlank()) 35_000L else 25_000L
        streamAssistantReply(
            conversationId = conversationId,
            parentUserMessageId = userMsgId,
            selectableModel = freshModel,
            options = options,
            enableWebSearch = enableWebSearch,
            autoRouteLabel = autoRouteLabel,
            onGroundingStatus = onGroundingStatus,
            onBranchCreated = onBranchCreated,
            onSentenceReady = onSentenceReady,
            onDeviceActionTriggered = onDeviceActionTriggered,
            onRequestError = onRequestError,
            timeoutMs = timeoutMs
        )
    }

    /**
     * Edits an earlier USER message by creating a sibling fork under the same parent
     * instead of overwriting history, then streams a fresh assistant reply on the new branch.
     */
    suspend fun editAndForkUserMessage(
        conversationId: Long,
        originalMessage: MessageEntity,
        newContent: String,
        selectableModel: SelectableModel,
        options: ChatOptions = ChatOptions(),
        enableWebSearch: Boolean = false,
        onBranchCreated: ((parentKey: Long, newChildId: Long) -> Unit)? = null
    ) {
        val allMessages = messageDao.getMessages(conversationId)
        val effectiveParentId = ConversationTree.findEffectiveParentId(allMessages, originalMessage.id)

        val forkedUserId = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                parentMessageId = effectiveParentId,
                role = "USER",
                content = newContent.trim(),
                attachmentUri = originalMessage.attachmentUri
            )
        )
        onBranchCreated?.invoke(effectiveParentId ?: ConversationTree.ROOT_PARENT_KEY, forkedUserId)
        conversationDao.touch(conversationId)

        streamAssistantReply(
            conversationId = conversationId,
            parentUserMessageId = forkedUserId,
            selectableModel = selectableModel,
            options = options,
            enableWebSearch = enableWebSearch,
            onBranchCreated = onBranchCreated
        )
    }

    /**
     * Regenerates an ASSISTANT reply as a new sibling branch under the same parent user message,
     * preserving the previous output so the user can toggle with `< 1 / 2 >`.
     */
    suspend fun regenerateAsBranch(
        conversationId: Long,
        assistantMessage: MessageEntity,
        selectableModel: SelectableModel,
        options: ChatOptions = ChatOptions(),
        enableWebSearch: Boolean = false,
        onBranchCreated: ((parentKey: Long, newChildId: Long) -> Unit)? = null
    ) {
        val allMessages = messageDao.getMessages(conversationId)
        val effectiveParentId = ConversationTree.findEffectiveParentId(allMessages, assistantMessage.id)
        if (effectiveParentId != null) {
            streamAssistantReply(
                conversationId = conversationId,
                parentUserMessageId = effectiveParentId,
                selectableModel = selectableModel,
                options = options,
                enableWebSearch = enableWebSearch,
                onBranchCreated = onBranchCreated
            )
        }
    }

    /**
     * Inline Tone & Length Morph: regenerates a specific assistant reply cleanly in-place
     * without cluttering chat history with extra user turns.
     */
    suspend fun morphAssistantMessageInPlace(
        conversationId: Long,
        targetMessage: MessageEntity,
        style: ReplyMorphStyle,
        selectableModel: SelectableModel,
        baseOptions: ChatOptions = ChatOptions(),
        onStatus: ((String) -> Unit)? = null
    ) {
        val freshModel = resolveFreshModel(selectableModel.model.id) ?: selectableModel
        val apiKey = apiKeyVault.getApiKey(freshModel.provider.id)
        val originalContent = targetMessage.content
        if (originalContent.isBlank()) return

        onStatus?.invoke("MORPH // Rewriting (${style.chipLabel})…")
        val morphSystem = buildString {
            appendLine("You are AllChat's Live Response Tuner.")
            appendLine("Apply the requested transformation cleanly to the assistant response below.")
            appendLine("Return ONLY the rewritten response — no meta-commentary like 'Here is the shorter version'.")
            appendLine()
            appendLine("Requested Transformation (${style.chipLabel}):")
            appendLine(style.instruction)
        }.trim()

        val morphInput = listOf(
            ChatInputMessage(
                role = "USER",
                text = "Transform this response according to the instructions (${style.chipLabel}):\n\n$originalContent"
            )
        )
        val morphOptions = baseOptions.copy(
            systemPrompt = morphSystem,
            enableTools = false,
            enableGeminiGoogleSearch = false,
            supportsVisionInput = false
        )

        val buffer = StringBuilder()
        var promptTokens = 0
        var completionTokens = 0

        try {
            withTimeout(25_000L) {
                aiRouter.streamChat(
                    provider = freshModel.provider,
                    apiKey = apiKey,
                    modelId = freshModel.model.modelId,
                    messages = morphInput,
                    options = morphOptions
                ).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            buffer.append(event.text)
                            val costUsd = CostEstimator.calculateUsd(
                                promptTokens,
                                completionTokens,
                                freshModel.model.inputPricePerMillion,
                                freshModel.model.outputPricePerMillion
                            )
                            messageDao.updateStreamingMessage(
                                id = targetMessage.id,
                                content = buffer.toString(),
                                promptTokens = promptTokens.takeIf { it > 0 },
                                completionTokens = completionTokens.takeIf { it > 0 },
                                estimatedCostUsd = costUsd
                            )
                        }
                        is StreamEvent.Usage -> {
                            promptTokens = event.promptTokens
                            completionTokens = event.completionTokens
                        }
                        else -> Unit
                    }
                }
            }
            if (buffer.isBlank()) {
                // Restore original content if model returned nothing
                messageDao.updateStreamingMessage(
                    id = targetMessage.id,
                    content = originalContent,
                    promptTokens = targetMessage.promptTokens,
                    completionTokens = targetMessage.completionTokens
                )
            } else {
                val finalCostUsd = CostEstimator.calculateUsd(
                    promptTokens,
                    completionTokens,
                    freshModel.model.inputPricePerMillion,
                    freshModel.model.outputPricePerMillion
                )
                if (promptTokens > 0 || completionTokens > 0) {
                    conversationDao.addTokenUsage(conversationId, promptTokens, completionTokens, finalCostUsd)
                }
                onStatus?.invoke("MORPH // Applied ${style.chipLabel} in-place.")
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            if (buffer.isBlank()) {
                messageDao.updateStreamingMessage(
                    id = targetMessage.id,
                    content = originalContent,
                    promptTokens = targetMessage.promptTokens,
                    completionTokens = targetMessage.completionTokens
                )
            }
            throw ce
        } catch (err: Throwable) {
            // Preserve original reply cleanly on error
            if (buffer.isBlank()) {
                messageDao.updateStreamingMessage(
                    id = targetMessage.id,
                    content = originalContent,
                    promptTokens = targetMessage.promptTokens,
                    completionTokens = targetMessage.completionTokens
                )
            }
            throw err
        }
    }

    private fun isContextOverflowError(err: Throwable): Boolean {
        val msg = err.message.orEmpty().lowercase()
        return msg.contains("context_length_exceeded") ||
            msg.contains("maximum context length") ||
            msg.contains("context window") ||
            msg.contains("413") ||
            msg.contains("payload too large") ||
            msg.contains("request entity too large") ||
            msg.contains("too many tokens") ||
            msg.contains("token limit") ||
            msg.contains("prompt is too long") ||
            msg.contains("exceeds the maximum") ||
            msg.contains("input is too long")
    }

    /**
     * Automatic Context-Overflow Trimmer:
     * 1) Strips heavy base64 images from older messages, preserving only the latest image.
     * 2) Compresses oldest non-recent turns into a compact summary anchor while keeping the latest turns intact.
     * 3) Caps oversized historical messages to fit comfortably under smaller context limits.
     */
    private fun trimMessagesForContextOverflow(messages: List<ChatInputMessage>): List<ChatInputMessage> {
        if (messages.isEmpty()) return messages
        val lastImageIdx = messages.indexOfLast { !it.imageBase64.isNullOrBlank() }

        val imageStripped = messages.mapIndexed { idx, msg ->
            if (!msg.imageBase64.isNullOrBlank() && idx != lastImageIdx) {
                val note = "[Earlier image attachment stripped to recover context window]"
                msg.copy(
                    text = if (msg.text.isBlank()) note else "${msg.text}\n\n$note",
                    imageBase64 = null,
                    imageMimeType = null
                )
            } else {
                msg
            }
        }

        val keepRecentCount = 6
        val compacted = if (imageStripped.size > keepRecentCount) {
            val dropped = imageStripped.dropLast(keepRecentCount)
            val recent = imageStripped.takeLast(keepRecentCount)
            val digest = dropped.takeLast(8).joinToString("\n") { m ->
                val snippet = m.text.replace(Regex("\\s+"), " ").trim().take(160)
                "- ${m.role}: $snippet"
            }
            val summaryHeader = ChatInputMessage(
                role = "USER",
                text = "[Auto-trimmed ${dropped.size} earlier turns to fit context window:\n$digest]"
            )
            listOf(summaryHeader) + recent
        } else {
            imageStripped
        }

        return compacted.mapIndexed { idx, msg ->
            val maxLen = if (idx == compacted.lastIndex) 16_000 else 3_500
            if (msg.text.length > maxLen) {
                msg.copy(text = msg.text.take(maxLen) + "\n…[trimmed by Self-Healer]")
            } else {
                msg
            }
        }
    }

    private suspend fun streamAssistantReply(
        conversationId: Long,
        parentUserMessageId: Long,
        selectableModel: SelectableModel,
        options: ChatOptions = ChatOptions(),
        enableWebSearch: Boolean = false,
        autoRouteLabel: String? = null,
        onGroundingStatus: ((String) -> Unit)? = null,
        onBranchCreated: ((parentKey: Long, newChildId: Long) -> Unit)? = null,
        onSentenceReady: ((String) -> Unit)? = null,
        onDeviceActionTriggered: ((DeviceAction) -> Unit)? = null,
        onRequestError: ((String) -> Unit)? = null,
        timeoutMs: Long = 25_000L
    ) {
        val allMessages = messageDao.getMessages(conversationId)
        val branchHistory = ConversationTree.getAncestorChain(allMessages, parentUserMessageId)
            .filter { !it.isError && !(it.role == "ASSISTANT" && it.content.isBlank()) }

        val latestUserText = branchHistory.lastOrNull { it.role == "USER" }?.content.orEmpty()
        val collectedCitations = mutableListOf<SearchCitation>()
        val extraContextBlocks = mutableListOf<String>()
        var effectiveOptions = options

        // 1. URL-to-Markdown Reader: `@https://example.com` inside prompt
        val atUrls = webSearchClient.extractAtUrls(latestUserText)
        if (atUrls.isNotEmpty()) {
            onGroundingStatus?.invoke("READER // Fetching ${atUrls.size} webpage${if (atUrls.size > 1) "s" else ""} via Jina Reader…")
            atUrls.take(3).forEach { url ->
                runCatching { webSearchClient.fetchUrlAsMarkdown(url) }
                    .onSuccess { scraped ->
                        val citeIdx = collectedCitations.size + 1
                        collectedCitations += SearchCitation(
                            index = citeIdx,
                            title = scraped.title,
                            url = scraped.url,
                            snippet = scraped.markdown.take(240)
                        )
                        extraContextBlocks += buildString {
                            appendLine("### Scraped Webpage [$citeIdx]: ${scraped.title} (${scraped.url})")
                            appendLine(scraped.markdown)
                        }
                    }
                    .onFailure { err ->
                        extraContextBlocks += "Note: Could not fetch $url (${err.message})."
                    }
            }
        }

        // 2. Local Knowledge Base Document RAG: `#doc-slug` or `#all`
        val ragSlugs = LocalRagEngine.extractReferencedSlugs(latestUserText)
        if (ragSlugs.isNotEmpty()) {
            val allDocs = knowledgeDocDao.getAll().map { it.toDomain() }
            val excerpts = LocalRagEngine.retrieveTopExcerpts(latestUserText, allDocs, ragSlugs, topK = 4)
            if (excerpts.isNotEmpty()) {
                onGroundingStatus?.invoke("RAG // Attached ${excerpts.size} local excerpts from ${ragSlugs.joinToString { "#$it" }}.")
                extraContextBlocks += buildString {
                    appendLine("### Local Knowledge Base Excerpts (TF-IDF Ranked)")
                    excerpts.forEachIndexed { idx, ex ->
                        appendLine("#### [Doc ${idx + 1}] #${ex.docSlug} — ${ex.docTitle} (Chunk #${ex.chunkIndex + 1})")
                        appendLine(ex.text)
                        appendLine()
                    }
                }
            }
        }

        // 3. Bring-Your-Own Web Search Toggle (Tavily / Brave / Exa or Native Gemini Grounding)
        if (enableWebSearch && latestUserText.isNotBlank()) {
            val searchProvider = apiKeyVault.searchProviderFlow.first()
            val searchKey = apiKeyVault.getSearchApiKey()
            val useNativeGemini = selectableModel.provider.type == ProviderType.GEMINI &&
                (searchProvider == WebSearchProvider.GEMINI_NATIVE || searchKey.isBlank())

            if (useNativeGemini) {
                effectiveOptions = effectiveOptions.copy(enableGeminiGoogleSearch = true)
                onGroundingStatus?.invoke("SEARCH // Gemini Native Google Search Grounding active.")
            } else if (searchKey.isNotBlank() && searchProvider != WebSearchProvider.GEMINI_NATIVE) {
                onGroundingStatus?.invoke("SEARCH // Querying ${searchProvider.label} for top 5 web snippets…")
                val searchQuery = latestUserText
                    .replace(Regex("""@https?://\S+"""), "")
                    .replace(Regex("""#[A-Za-z0-9_-]+"""), "")
                    .trim()
                runCatching {
                    webSearchClient.searchWeb(searchQuery, searchProvider, searchKey, maxResults = 5)
                }.onSuccess { results ->
                    if (results.isNotEmpty()) {
                        val baseOffset = collectedCitations.size
                        val shifted = results.mapIndexed { idx, c -> c.copy(index = baseOffset + idx + 1) }
                        collectedCitations += shifted
                        extraContextBlocks += buildString {
                            appendLine("### Live Web Search Results (${searchProvider.label})")
                            appendLine("Use these grounded sources and cite them inline as [1], [2], etc.:")
                            shifted.forEach { c ->
                                appendLine("[${c.index}] ${c.title} — ${c.url}")
                                if (c.snippet.isNotBlank()) appendLine("Snippet: ${c.snippet}")
                                appendLine()
                            }
                        }
                        onGroundingStatus?.invoke("SEARCH // Grounded with ${shifted.size} live web sources.")
                    }
                }.onFailure { err ->
                    onGroundingStatus?.invoke("SEARCH // ${err.message ?: "Web search failed, continuing ungrounded."}")
                }
            } else {
                onGroundingStatus?.invoke("SEARCH // Configure a Tavily/Brave/Exa key in Settings or switch to Gemini for native Search.")
            }
        }

        // 0. Smart Context Injection: Profile-Scoped Pinned + top relevant enabled User Memory Bank facts
        val enabledMemoriesRaw = if (effectiveOptions.injectMemory) userMemoryDao.getEnabled() else emptyList()
        val pinnedIds = if (enabledMemoriesRaw.isNotEmpty()) apiKeyVault.pinnedMemoryIdsFlow.first() else emptySet()
        val profileMap = if (enabledMemoriesRaw.isNotEmpty()) apiKeyVault.memoryProfileMapFlow.first() else emptyMap()
        val activeProfileName = effectiveOptions.activeMemoryProfile.name
        val enabledMemories = if (activeProfileName == MemoryProfile.ALL.name) {
            enabledMemoriesRaw
        } else {
            enabledMemoriesRaw.filter { m ->
                val tag = profileMap[m.id] ?: "ALL"
                tag.equals("ALL", ignoreCase = true) || tag.equals(activeProfileName, ignoreCase = true)
            }
        }
        val selectedMemories = if (enabledMemories.isNotEmpty()) {
            val queryTokens = normalizeMemoryTokens(latestUserText)
            val pinned = enabledMemories.filter { it.id in pinnedIds }
            val unpinnedRanked = enabledMemories
                .filter { it.id !in pinnedIds }
                .sortedWith(
                    compareByDescending<UserMemoryEntity> { m ->
                        val memTokens = normalizeMemoryTokens(m.fact + " " + m.category)
                        if (queryTokens.isEmpty()) 0 else memTokens.intersect(queryTokens).size
                    }.thenByDescending { it.createdAt }
                )
            (pinned + unpinnedRanked).take(12)
        } else {
            emptyList()
        }

        // Semantic Search Auto-Attach: attach top conceptual match across Docs, Live Lens scans, and Memory
        if (effectiveOptions.semanticAutoAttach && latestUserText.length >= 4) {
            val allMemInfo = enabledMemories.map { e ->
                e.toDomain().copy(isPinned = e.id in pinnedIds, profile = profileMap[e.id] ?: "ALL")
            }
            val docs = knowledgeDocDao.getAll().map { it.toDomain() }
            val lensHistory = LiveLensHistoryStore.loadAll(context)
            val topHit = SemanticEmbeddingEngine.searchCorpus(
                query = latestUserText,
                memories = allMemInfo,
                knowledgeDocs = docs,
                lensHistory = lensHistory,
                topK = 1,
                minScore = 0.24f
            ).firstOrNull()
            if (topHit != null) {
                extraContextBlocks += SemanticEmbeddingEngine.formatAutoAttachSnippet(topHit)
                onGroundingStatus?.invoke("SEMANTIC // Auto-attached ${topHit.matchPercent}% match (${topHit.item.sourceType.badge}: ${topHit.item.title.take(28)})")
            }
        }

        val savedQuickContacts = quickContactsFlow.first()
        val contactsBlock = DeviceActionEngine.formatContactsForSystemPrompt(savedQuickContacts)
        val systemAddons = buildList {
            effectiveOptions.systemPrompt?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            if (effectiveOptions.outputFormatMode != OutputFormatMode.AUTO) {
                add(effectiveOptions.outputFormatMode.directive)
            }
            if (selectedMemories.isNotEmpty()) {
                add(
                    buildString {
                        appendLine("### User Memory Bank [PROFILE: $activeProfileName] (Respect Automatically)")
                        selectedMemories.forEach { m ->
                            val pinTag = if (m.id in pinnedIds) "[PINNED] " else ""
                            appendLine("- $pinTag[${m.category}] ${m.fact}")
                        }
                    }.trim()
                )
            }
            if (contactsBlock.isNotEmpty()) {
                add(contactsBlock)
            }
        }
        effectiveOptions = effectiveOptions.copy(
            systemPrompt = systemAddons.joinToString("\n\n").takeIf { it.isNotBlank() },
            supportsVisionInput = selectableModel.model.supportsImageInput
        )

        val inputMessages = branchHistory.mapIndexed { idx, msg ->
            val encodedImg = if (!msg.attachmentUri.isNullOrBlank()) {
                ImageEncoder.encodeFromStoredUri(context, msg.attachmentUri)
            } else {
                null
            }
            val isLastUser = idx == branchHistory.lastIndex && msg.role == "USER"
            val augmentedText = if (isLastUser && extraContextBlocks.isNotEmpty()) {
                buildString {
                    appendLine(msg.content)
                    appendLine()
                    appendLine("--- GROUNDED CONTEXT (DO NOT IGNORE) ---")
                    extraContextBlocks.forEach { appendLine(it) }
                }
            } else {
                msg.content
            }
            ChatInputMessage(
                role = msg.role,
                text = augmentedText,
                imageBase64 = encodedImg?.first,
                imageMimeType = encodedImg?.second
            )
        }.toMutableList()

        val labelPrefix = if (!autoRouteLabel.isNullOrBlank()) "AUTO:$autoRouteLabel · " else ""
        val assistantId = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                parentMessageId = parentUserMessageId,
                role = "ASSISTANT",
                content = "",
                modelLabel = "$labelPrefix${selectableModel.model.displayName}",
                citationsJson = WebSearchClient.citationsToJson(collectedCitations)
            )
        )
        onBranchCreated?.invoke(parentUserMessageId, assistantId)

        var activeModel = selectableModel
        var activeApiKey = apiKeyVault.getApiKey(activeModel.provider.id)
        var activeOptions = effectiveOptions
        val triedProviderIds = mutableSetOf(activeModel.provider.id)
        var deviceActionTriggered = false
        val buffer = StringBuilder()
        val ttsSentenceBuffer = StringBuilder()
        val pendingArtifacts = mutableListOf<String>()
        var promptTokens = 0
        var completionTokens = 0
        var capturedRequestJson: String? = null
        val rawResponseSamples = StringBuilder()
        var rawChunkCount = 0
        var thinkingStartMs: Long? = null
        var thinkingDurationSec: Int? = null
        var latencyRecorded = false
        val turnStartMs = System.currentTimeMillis()

        try {
            var toolRound = 0
            val maxToolRounds = 4
            var contextAutoHealed = false
            while (toolRound <= maxToolRounds) {
                var requestedToolCalls: List<ToolCallInvocation> = emptyList()
                val roundTextBuffer = StringBuilder()

                try {
                    withTimeout(timeoutMs) {
                        aiRouter.streamChat(
                            provider = activeModel.provider,
                            apiKey = activeApiKey,
                            modelId = activeModel.model.modelId,
                            messages = inputMessages.toList(),
                            options = activeOptions
                        ).collect { event ->
                        when (event) {
                            is StreamEvent.DebugRequest -> {
                                capturedRequestJson = "// POST ${event.endpointUrl}\n${event.requestJson}"
                            }
                            is StreamEvent.RawResponseChunk -> {
                                rawChunkCount++
                                if (rawResponseSamples.length < 10_000) {
                                    rawResponseSamples.append("/* chunk #$rawChunkCount */\n")
                                    rawResponseSamples.append(event.rawJson).append("\n\n")
                                }
                            }
                            is StreamEvent.GroundingCitations -> {
                                val existingUrls = collectedCitations.map { it.url }.toSet()
                                event.citations.forEach { c ->
                                    if (c.url !in existingUrls) {
                                        collectedCitations += c.copy(index = collectedCitations.size + 1)
                                    }
                                }
                            }
                            is StreamEvent.ToolCallRequest -> {
                                requestedToolCalls = event.calls
                            }
                            is StreamEvent.TextDelta -> {
                                if (!latencyRecorded && event.text.isNotEmpty()) {
                                    latencyRecorded = true
                                    val ttftMs = (System.currentTimeMillis() - turnStartMs).coerceAtLeast(1L)
                                    runCatching { apiKeyVault.recordModelLatency(activeModel.model.id, ttftMs) }
                                }
                                val prevText = buffer.toString()
                                roundTextBuffer.append(event.text)
                                buffer.append(event.text)
                                val curText = buffer.toString()
                                if (thinkingStartMs == null && (curText.contains("<think>") || curText.contains("<reasoning>"))) {
                                    thinkingStartMs = System.currentTimeMillis()
                                }
                                if (thinkingStartMs != null && thinkingDurationSec == null &&
                                    ((!prevText.contains("</think>") && curText.contains("</think>")) ||
                                        (!prevText.contains("</reasoning>") && curText.contains("</reasoning>")))
                                ) {
                                    val elapsedSec = ((System.currentTimeMillis() - thinkingStartMs!!) / 1000L).toInt().coerceAtLeast(1)
                                    thinkingDurationSec = elapsedSec
                                }
                                if (onSentenceReady != null) {
                                    val insideThinkBlock = (curText.contains("<think>") && !curText.contains("</think>")) ||
                                        (curText.contains("<reasoning>") && !curText.contains("</reasoning>"))
                                    if (!insideThinkBlock) {
                                        ttsSentenceBuffer.append(event.text)
                                        val currentChunk = ttsSentenceBuffer.toString()
                                        val breakIdx = findSentenceBreak(currentChunk)
                                        if (breakIdx > 0) {
                                            val ready = cleanWidgetAnswerText(currentChunk.substring(0, breakIdx))
                                            val rem = currentChunk.substring(breakIdx)
                                            ttsSentenceBuffer.clear()
                                            ttsSentenceBuffer.append(rem)
                                            if (ready.length >= 6) {
                                                onSentenceReady(ready)
                                            }
                                        }
                                    }
                                }
                                val costUsd = CostEstimator.calculateUsd(
                                    promptTokens,
                                    completionTokens,
                                    selectableModel.model.inputPricePerMillion,
                                    selectableModel.model.outputPricePerMillion
                                )
                                messageDao.updateStreamingMessage(
                                    id = assistantId,
                                    content = curText,
                                    promptTokens = promptTokens.takeIf { it > 0 },
                                    completionTokens = completionTokens.takeIf { it > 0 },
                                    estimatedCostUsd = costUsd
                                )
                            }
                            is StreamEvent.Usage -> {
                                promptTokens = event.promptTokens
                                completionTokens = event.completionTokens
                                val costUsd = CostEstimator.calculateUsd(
                                    promptTokens,
                                    completionTokens,
                                    selectableModel.model.inputPricePerMillion,
                                    selectableModel.model.outputPricePerMillion
                                )
                                messageDao.updateStreamingMessage(
                                    id = assistantId,
                                    content = buffer.toString(),
                                    promptTokens = promptTokens.takeIf { it > 0 },
                                    completionTokens = completionTokens.takeIf { it > 0 },
                                    estimatedCostUsd = costUsd
                                )
                            }
                            StreamEvent.Completed -> Unit
                        }
                    }
                    }
                } catch (err: Throwable) {
                    if (err is kotlinx.coroutines.CancellationException) throw err
                    // 1. Seamless HTTP 429 / 503 Auto-Failover (60s provider cooldown + transparent retry on next configured provider)
                    if (roundTextBuffer.isEmpty() && isRateLimitOrUnavailableError(err)) {
                        val allChatModels = runCatching {
                            observeModelsForCapability(ModelCapability.TEXT_CHAT).first()
                        }.getOrDefault(emptyList())
                        val failedProviderName = activeModel.provider.name
                        markModelRateLimited(activeModel.model.id, 60_000L)
                        markProviderRateLimited(activeModel.provider.id, allChatModels, 60_000L)

                        val nowMs = System.currentTimeMillis()
                        val needsVision = inputMessages.any { !it.imageBase64.isNullOrBlank() }
                        val candidates = allChatModels.filter { cand ->
                            val isLocal = cand.provider.baseUrl.contains("localhost", ignoreCase = true) ||
                                cand.provider.baseUrl.contains("127.0.0.1") ||
                                cand.provider.baseUrl.contains("10.0.2.2")
                            cand.provider.id !in triedProviderIds &&
                                (modelCooldownUntilMs[cand.model.id] ?: 0L) <= nowMs &&
                                (isLocal || !apiKeyVault.getApiKey(cand.provider.id).isNullOrBlank())
                        }
                        val nextModel = (if (needsVision) candidates.firstOrNull { it.model.supportsImageInput } else null)
                            ?: candidates.firstOrNull()

                        if (nextModel != null) {
                            triedProviderIds.add(nextModel.provider.id)
                            activeModel = nextModel
                            activeApiKey = apiKeyVault.getApiKey(nextModel.provider.id)
                            activeOptions = activeOptions.copy(
                                supportsVisionInput = nextModel.model.supportsImageInput
                            )
                            onGroundingStatus?.invoke(
                                "FAILOVER // $failedProviderName 429/503 (60s cooldown) → retrying on ${nextModel.provider.name} · ${nextModel.model.displayName}…"
                            )
                            continue
                        }
                    }
                    // 2. Automatic Context-Overflow Trimmer & Self-Healer (413 / context_length_exceeded / token limit)
                    if (!contextAutoHealed && roundTextBuffer.isEmpty() && isContextOverflowError(err)) {
                        contextAutoHealed = true
                        val trimmed = trimMessagesForContextOverflow(inputMessages.toList())
                        inputMessages.clear()
                        inputMessages.addAll(trimmed)
                        onGroundingStatus?.invoke("SELF-HEALER // Context limit hit — trimmed oldest turns & stripped older images, retrying seamlessly…")
                        continue
                    }
                    throw err
                }

                if (requestedToolCalls.isEmpty() || toolRound >= maxToolRounds) {
                    break
                }

                // Append the assistant's tool call message and execute each client tool locally
                inputMessages += ChatInputMessage(
                    role = "ASSISTANT",
                    text = roundTextBuffer.toString(),
                    toolCalls = requestedToolCalls
                )

                val savedContacts = quickContactsFlow.first()
                val savedCustomApps = customAppsFlow.first()
                val traceLines = mutableListOf<String>()
                for (tc in requestedToolCalls) {
                    onGroundingStatus?.invoke("TOOL // Executing ${tc.name}…")
                    val outcome = ClientToolEngine.executeTool(
                        name = tc.name,
                        argumentsJson = tc.argumentsJson,
                        webSearchClient = webSearchClient,
                        contacts = savedContacts,
                        customApps = savedCustomApps
                    )
                    onGroundingStatus?.invoke(outcome.displaySummary)
                    outcome.triggeredDeviceAction?.let { triggered ->
                        deviceActionTriggered = true
                        onDeviceActionTriggered?.invoke(triggered)
                    }
                    traceLines += "> **${outcome.displaySummary}**"
                    outcome.inlineArtifactMarkdown?.let { pendingArtifacts += it }
                    inputMessages += ChatInputMessage(
                        role = "TOOL",
                        text = outcome.resultJson,
                        toolCallId = tc.id,
                        toolName = tc.name
                    )
                }

                if (traceLines.isNotEmpty()) {
                    if (buffer.isNotEmpty() && !buffer.endsWith("\n")) buffer.append("\n\n")
                    buffer.append(traceLines.joinToString("\n")).append("\n\n")
                    messageDao.updateStreamingMessage(
                        id = assistantId,
                        content = buffer.toString(),
                        promptTokens = promptTokens.takeIf { it > 0 },
                        completionTokens = completionTokens.takeIf { it > 0 }
                    )
                }
                toolRound++
            }

            // Streamed Plain-Text Action Interceptor:
            // If the model replied with plain confirmation text instead of calling execute_device_action,
            // extract WhatsApp/Call action so the interactive confirmation card still appears reliably.
            if (!deviceActionTriggered && onDeviceActionTriggered != null && buffer.isNotBlank()) {
                val savedContacts = runCatching { quickContactsFlow.first() }.getOrDefault(emptyList())
                val savedApps = runCatching { customAppsFlow.first() }.getOrDefault(emptyList())
                val intercepted = DeviceActionEngine.parseFromAssistantReply(
                    userPrompt = latestUserText,
                    assistantReply = buffer.toString(),
                    contacts = savedContacts,
                    customApps = savedApps
                )
                if (intercepted != null) {
                    deviceActionTriggered = true
                    onDeviceActionTriggered.invoke(intercepted)
                }
            }

            // Append any tool-generated chart/mermaid artifacts if not already in response
            if (pendingArtifacts.isNotEmpty()) {
                val cur = buffer.toString()
                pendingArtifacts.forEach { artifact ->
                    if (!cur.contains(artifact.take(30))) {
                        if (buffer.isNotEmpty() && !buffer.endsWith("\n")) buffer.append("\n\n")
                        buffer.append(artifact).append("\n")
                    }
                }
                messageDao.updateStreamingMessage(
                    id = assistantId,
                    content = buffer.toString(),
                    promptTokens = promptTokens.takeIf { it > 0 },
                    completionTokens = completionTokens.takeIf { it > 0 }
                )
            }

            if (onSentenceReady != null) {
                val tail = cleanWidgetAnswerText(ttsSentenceBuffer.toString())
                if (tail.isNotBlank()) {
                    onSentenceReady(tail)
                }
            }

            if (thinkingStartMs != null && thinkingDurationSec == null) {
                thinkingDurationSec = ((System.currentTimeMillis() - thinkingStartMs!!) / 1000L).toInt().coerceAtLeast(1)
            }

            messageDao.updateMessageExtras(
                id = assistantId,
                citationsJson = WebSearchClient.citationsToJson(collectedCitations),
                thinkingDurationSec = thinkingDurationSec,
                rawRequestJson = capturedRequestJson,
                rawResponseJson = rawResponseSamples.toString().takeIf { it.isNotBlank() }
            )

            val finalCostUsd = CostEstimator.calculateUsd(
                promptTokens,
                completionTokens,
                selectableModel.model.inputPricePerMillion,
                selectableModel.model.outputPricePerMillion
            )
            if (promptTokens > 0 || completionTokens > 0) {
                conversationDao.addTokenUsage(
                    id = conversationId,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    costUsd = finalCostUsd
                )
            } else {
                conversationDao.touch(conversationId)
            }
        } catch (tce: TimeoutCancellationException) {
            val timeoutSec = (timeoutMs / 1000L).toInt()
            val timeoutMsg = "Request timed out after ${timeoutSec}s on ${selectableModel.model.displayName}. Check your network/Base URL or tap Retry."
            val errContent = if (buffer.isNotEmpty()) {
                "${buffer}\n\n---\n**Timeout:** $timeoutMsg"
            } else {
                timeoutMsg
            }
            messageDao.updateStreamingMessage(
                id = assistantId,
                content = errContent,
                promptTokens = promptTokens.takeIf { it > 0 },
                completionTokens = completionTokens.takeIf { it > 0 },
                isError = true,
                suggestProxySetting = true,
                providerIdForError = selectableModel.provider.id
            )
            messageDao.updateMessageExtras(
                id = assistantId,
                citationsJson = WebSearchClient.citationsToJson(collectedCitations),
                thinkingDurationSec = thinkingDurationSec,
                rawRequestJson = capturedRequestJson,
                rawResponseJson = rawResponseSamples.toString().takeIf { it.isNotBlank() }
            )
            onRequestError?.invoke(timeoutMsg)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            if (buffer.isEmpty()) {
                messageDao.updateStreamingMessage(
                    id = assistantId,
                    content = "*Generation stopped.*",
                    promptTokens = null,
                    completionTokens = null
                )
            }
            messageDao.updateMessageExtras(
                id = assistantId,
                citationsJson = WebSearchClient.citationsToJson(collectedCitations),
                thinkingDurationSec = thinkingDurationSec,
                rawRequestJson = capturedRequestJson,
                rawResponseJson = rawResponseSamples.toString().takeIf { it.isNotBlank() }
            )
            throw ce
        } catch (t: Throwable) {
            val aiEx = t as? AiException ?: AiException(
                AiErrorCategory.UNKNOWN,
                t.message ?: "Request failed."
            )
            if (isRateLimitError(aiEx)) {
                markModelRateLimited(selectableModel.model.id)
            }
            val errContent = if (buffer.isNotEmpty()) {
                "${buffer}\n\n---\n**Error:** ${aiEx.message}"
            } else {
                aiEx.message
            }
            messageDao.updateStreamingMessage(
                id = assistantId,
                content = errContent,
                promptTokens = promptTokens.takeIf { it > 0 },
                completionTokens = completionTokens.takeIf { it > 0 },
                isError = true,
                suggestProxySetting = aiEx.suggestProxySetting,
                providerIdForError = selectableModel.provider.id
            )
            messageDao.updateMessageExtras(
                id = assistantId,
                citationsJson = WebSearchClient.citationsToJson(collectedCitations),
                thinkingDurationSec = thinkingDurationSec,
                rawRequestJson = capturedRequestJson,
                rawResponseJson = rawResponseSamples.toString().takeIf { it.isNotBlank() }
            )
            onRequestError?.invoke(aiEx.message)
        }
    }

    // --- Side-by-Side Arena & Sequential Pipeline Comparison ---

    suspend fun streamArenaModel(
        selectableModel: SelectableModel,
        promptText: String,
        options: ChatOptions = ChatOptions()
    ): Flow<StreamEvent> {
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        return aiRouter.streamChat(
            provider = selectableModel.provider,
            apiKey = apiKey,
            modelId = selectableModel.model.modelId,
            messages = listOf(ChatInputMessage(role = "USER", text = promptText.trim())),
            options = options
        )
    }

    suspend fun createConversationFromPipeline(
        userTask: String,
        stepOutputs: List<Triple<String, SelectableModel, String>>
    ): Long {
        val lastModel = stepOutputs.lastOrNull()?.second
        val title = "Pipeline: ${userTask.trim().replace("\n", " ").take(32)}"
        val convId = conversationDao.insert(
            ConversationEntity(
                title = title,
                selectedModelDbId = lastModel?.model?.id
            )
        )
        var parentId: Long? = messageDao.insert(
            MessageEntity(
                conversationId = convId,
                parentMessageId = null,
                role = "USER",
                content = userTask.trim()
            )
        )
        val combinedMarkdown = buildString {
            stepOutputs.forEachIndexed { idx, (stepTitle, model, output) ->
                appendLine("## Step ${idx + 1}: $stepTitle (${model.model.displayName})")
                appendLine()
                appendLine(output.trim())
                if (idx < stepOutputs.lastIndex) {
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
            }
        }
        messageDao.insert(
            MessageEntity(
                conversationId = convId,
                parentMessageId = parentId,
                role = "ASSISTANT",
                content = combinedMarkdown,
                modelLabel = "PIPELINE (${stepOutputs.size} STEPS)"
            )
        )
        return convId
    }

    suspend fun createConversationFromArena(
        promptText: String,
        winningModel: SelectableModel,
        replyText: String,
        promptTokens: Int,
        completionTokens: Int
    ): Long {
        val costUsd = CostEstimator.calculateUsd(
            promptTokens,
            completionTokens,
            winningModel.model.inputPricePerMillion,
            winningModel.model.outputPricePerMillion
        )
        val title = promptText.trim().replace("\n", " ").take(40).ifBlank { "Arena Comparison" }
        val convId = conversationDao.insert(
            ConversationEntity(
                title = title,
                selectedModelDbId = winningModel.model.id,
                totalPromptTokens = promptTokens,
                totalCompletionTokens = completionTokens,
                estimatedCostUsd = costUsd
            )
        )
        val userMsgId = messageDao.insert(
            MessageEntity(
                conversationId = convId,
                parentMessageId = null,
                role = "USER",
                content = promptText.trim()
            )
        )
        messageDao.insert(
            MessageEntity(
                conversationId = convId,
                parentMessageId = userMsgId,
                role = "ASSISTANT",
                content = replyText,
                promptTokens = promptTokens.takeIf { it > 0 },
                completionTokens = completionTokens.takeIf { it > 0 },
                estimatedCostUsd = costUsd,
                modelLabel = winningModel.model.displayName
            )
        )
        return convId
    }

    // --- Summarization (Direct + Map-Reduce Chunking) ---

    suspend fun extractTextFromFile(uri: Uri): Pair<String, String> {
        val doc = DocumentExtractor.extractFromUri(context, uri)
        return doc.title to doc.text
    }

    suspend fun extractTextFromUrl(url: String): Pair<String, String> {
        val doc = DocumentExtractor.extractFromUrl(okHttpClient, url)
        return doc.title to doc.text
    }

    fun summarizeText(
        sourceText: String,
        length: SummaryLength,
        selectableModel: SelectableModel
    ): Flow<SummaryProgress> = flow {
        val chunks = DocumentExtractor.splitIntoChunks(sourceText)
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)

        if (chunks.size <= 1) {
            emit(SummaryProgress(1, 1, "Summarizing with ${selectableModel.model.displayName}…"))
            val prompt = buildString {
                appendLine(length.instruction)
                appendLine("Use clean Markdown formatting.")
                appendLine()
                appendLine("Source content:")
                append(chunks.firstOrNull().orEmpty())
            }
            val result = collectSingleCompletion(selectableModel, apiKey, prompt) { partial ->
                emit(
                    SummaryProgress(
                        currentChunk = 1,
                        totalChunks = 1,
                        statusText = "Streaming summary…",
                        partialOutput = partial
                    )
                )
            }
            emit(SummaryProgress(1, 1, "Summary complete", result, isCompleted = true))
        } else {
            val chunkSummaries = mutableListOf<String>()
            chunks.forEachIndexed { index, chunk ->
                val step = index + 1
                emit(
                    SummaryProgress(
                        currentChunk = step,
                        totalChunks = chunks.size,
                        statusText = "Summarizing chunk $step of ${chunks.size}…"
                    )
                )
                val chunkPrompt = """
                    Summarize the following excerpt (part $step of ${chunks.size}) faithfully, preserving all key facts, figures, and arguments in concise bullet points:

                    $chunk
                """.trimIndent()
                val summary = collectSingleCompletion(selectableModel, apiKey, chunkPrompt) {}
                chunkSummaries += "Part $step:\n$summary"
            }

            emit(
                SummaryProgress(
                    currentChunk = chunks.size,
                    totalChunks = chunks.size,
                    statusText = "Combining ${chunks.size} chunk summaries into final ${length.label.lowercase()} summary…"
                )
            )

            val combinePrompt = buildString {
                appendLine("Combine these partial section summaries into one cohesive, non-repetitive summary.")
                appendLine(length.instruction)
                appendLine("Use clear Markdown headings and bullets where helpful.")
                appendLine()
                append(chunkSummaries.joinToString("\n\n"))
            }
            val finalSummary = collectSingleCompletion(selectableModel, apiKey, combinePrompt) { partial ->
                emit(
                    SummaryProgress(
                        currentChunk = chunks.size,
                        totalChunks = chunks.size,
                        statusText = "Finalizing summary…",
                        partialOutput = partial
                    )
                )
            }
            emit(
                SummaryProgress(
                    currentChunk = chunks.size,
                    totalChunks = chunks.size,
                    statusText = "Summary complete (${chunks.size} chunks combined)",
                    partialOutput = finalSummary,
                    isCompleted = true
                )
            )
        }
    }

    private suspend fun collectSingleCompletion(
        selectableModel: SelectableModel,
        apiKey: String,
        prompt: String,
        maxTokens: Int? = null,
        onPartial: suspend (String) -> Unit
    ): String {
        val sb = StringBuilder()
        aiRouter.streamChat(
            provider = selectableModel.provider,
            apiKey = apiKey,
            modelId = selectableModel.model.modelId,
            messages = listOf(ChatInputMessage(role = "USER", text = prompt)),
            options = ChatOptions(maxTokens = maxTokens, injectMemory = false)
        ).collect { ev ->
            if (ev is StreamEvent.TextDelta) {
                sb.append(ev.text)
                onPartial(sb.toString())
            }
        }
        return sb.toString()
    }

    // --- Image Generation ---

    suspend fun generateImage(
        prompt: String,
        aspectRatio: String,
        selectableModel: SelectableModel
    ): GeneratedMediaEntity {
        val recordId = generatedMediaDao.insert(
            GeneratedMediaEntity(
                mediaType = "IMAGE",
                prompt = prompt.trim(),
                modelDbId = selectableModel.model.id,
                modelLabel = selectableModel.model.displayName,
                providerId = selectableModel.provider.id,
                status = "PENDING",
                aspectRatio = aspectRatio
            )
        )
        return try {
            val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
            val payload = aiRouter.generateImage(
                provider = selectableModel.provider,
                apiKey = apiKey,
                modelId = selectableModel.model.modelId,
                prompt = prompt.trim(),
                aspectRatio = aspectRatio
            )
            val dir = File(context.filesDir, "media/images").apply { mkdirs() }
            val ext = if (payload.mimeType.contains("jpeg")) "jpg" else "png"
            val file = File(dir, "img_${recordId}_${System.currentTimeMillis()}.$ext")
            FileOutputStream(file).use { it.write(payload.bytes) }

            val completed = GeneratedMediaEntity(
                id = recordId,
                mediaType = "IMAGE",
                prompt = prompt.trim(),
                modelDbId = selectableModel.model.id,
                modelLabel = selectableModel.model.displayName,
                providerId = selectableModel.provider.id,
                localFilePath = file.absolutePath,
                status = "COMPLETED",
                aspectRatio = aspectRatio
            )
            generatedMediaDao.update(completed)
            completed
        } catch (t: Throwable) {
            val aiEx = t as? AiException
            val failed = GeneratedMediaEntity(
                id = recordId,
                mediaType = "IMAGE",
                prompt = prompt.trim(),
                modelDbId = selectableModel.model.id,
                modelLabel = selectableModel.model.displayName,
                providerId = selectableModel.provider.id,
                status = "FAILED",
                aspectRatio = aspectRatio,
                errorMessage = t.message ?: "Image generation failed.",
                suggestProxySetting = aiEx?.suggestProxySetting == true
            )
            generatedMediaDao.update(failed)
            throw (aiEx ?: Exception(failed.errorMessage, t))
        }
    }

    suspend fun editImageWithMask(
        prompt: String,
        sourcePngBytes: ByteArray,
        openAiMaskPngBytes: ByteArray?,
        geminiOverlayMaskPngBytes: ByteArray?,
        selectableModel: SelectableModel
    ): GeneratedMediaEntity {
        val recordId = generatedMediaDao.insert(
            GeneratedMediaEntity(
                mediaType = "IMAGE",
                prompt = "[EDIT] ${prompt.trim()}",
                modelDbId = selectableModel.model.id,
                modelLabel = "${selectableModel.model.displayName} (Inpaint)",
                providerId = selectableModel.provider.id,
                status = "PENDING",
                aspectRatio = "1:1"
            )
        )
        return try {
            val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
            val payload = aiRouter.editImage(
                provider = selectableModel.provider,
                apiKey = apiKey,
                modelId = selectableModel.model.modelId,
                prompt = prompt.trim(),
                sourcePngBytes = sourcePngBytes,
                openAiTransparentMaskPng = openAiMaskPngBytes,
                geminiOverlayMaskPng = geminiOverlayMaskPngBytes
            )
            val dir = File(context.filesDir, "media/images").apply { mkdirs() }
            val ext = if (payload.mimeType.contains("jpeg")) "jpg" else "png"
            val file = File(dir, "inpaint_${recordId}_${System.currentTimeMillis()}.$ext")
            FileOutputStream(file).use { it.write(payload.bytes) }

            val completed = GeneratedMediaEntity(
                id = recordId,
                mediaType = "IMAGE",
                prompt = "[EDIT] ${prompt.trim()}",
                modelDbId = selectableModel.model.id,
                modelLabel = "${selectableModel.model.displayName} (Inpaint)",
                providerId = selectableModel.provider.id,
                localFilePath = file.absolutePath,
                status = "COMPLETED",
                aspectRatio = "1:1"
            )
            generatedMediaDao.update(completed)
            completed
        } catch (t: Throwable) {
            val aiEx = t as? AiException
            val failed = GeneratedMediaEntity(
                id = recordId,
                mediaType = "IMAGE",
                prompt = "[EDIT] ${prompt.trim()}",
                modelDbId = selectableModel.model.id,
                modelLabel = "${selectableModel.model.displayName} (Inpaint)",
                providerId = selectableModel.provider.id,
                status = "FAILED",
                aspectRatio = "1:1",
                errorMessage = t.message ?: "Image edit failed.",
                suggestProxySetting = aiEx?.suggestProxySetting == true
            )
            generatedMediaDao.update(failed)
            throw (aiEx ?: Exception(failed.errorMessage, t))
        }
    }

    // --- Interactive Study / Quiz & Mind Map Generators ---

    suspend fun generateStudyPack(
        sourceText: String,
        selectableModel: SelectableModel
    ): StudyPack = withContext(Dispatchers.IO) {
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        val prompt = """
            Convert the content below into an interactive study pack with 5-8 crisp flashcards and 4-5 multiple-choice quiz questions.
            Return STRICTLY valid JSON (no markdown fences, no extra commentary) matching this schema:
            {
              "title": "Short Topic Title",
              "flashcards": [
                {"front": "Question or concept prompt", "back": "Clear concise answer"}
              ],
              "quiz": [
                {
                  "question": "Multiple choice question?",
                  "options": ["Option A", "Option B", "Option C", "Option D"],
                  "correctIndex": 0,
                  "explanation": "Brief explanation of why the correct option is right."
                }
              ]
            }

            Content:
            ${sourceText.take(9000)}
        """.trimIndent()
        val raw = collectSingleCompletion(selectableModel, apiKey, prompt) {}
        val jsonStr = extractJsonObject(raw)
            ?: throw IllegalStateException("Model did not return structured JSON for Study Pack.")
        val root = JSONObject(jsonStr)
        val title = root.optString("title").ifBlank { "Study Pack" }
        val cardsArr = root.optJSONArray("flashcards") ?: JSONArray()
        val cards = buildList {
            for (i in 0 until cardsArr.length()) {
                val c = cardsArr.optJSONObject(i) ?: continue
                val front = c.optString("front").trim()
                val back = c.optString("back").trim()
                if (front.isNotEmpty() && back.isNotEmpty()) add(StudyCard(front, back))
            }
        }
        val quizArr = root.optJSONArray("quiz") ?: JSONArray()
        val quiz = buildList {
            for (i in 0 until quizArr.length()) {
                val q = quizArr.optJSONObject(i) ?: continue
                val question = q.optString("question").trim()
                val optsArr = q.optJSONArray("options") ?: continue
                val opts = buildList {
                    for (j in 0 until optsArr.length()) {
                        val o = optsArr.optString(j).trim()
                        if (o.isNotEmpty()) add(o)
                    }
                }
                if (question.isNotEmpty() && opts.size >= 2) {
                    val correctIdx = q.optInt("correctIndex", 0).coerceIn(0, opts.lastIndex)
                    val exp = q.optString("explanation", "").trim()
                    add(QuizQuestion(question, opts, correctIdx, exp))
                }
            }
        }
        if (cards.isEmpty() && quiz.isEmpty()) {
            throw IllegalStateException("No study cards or quiz questions could be parsed.")
        }
        StudyPack(title = title, flashcards = cards, quiz = quiz)
    }

    suspend fun generateMindMap(
        sourceText: String,
        selectableModel: SelectableModel
    ): MindMapNode = withContext(Dispatchers.IO) {
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        val prompt = """
            Convert the content below into a clean hierarchical Mind Map tree (root -> 3 to 5 main branches -> 2 to 3 concise sub-nodes each).
            Keep every node label short (2-6 words) and add an optional 1-sentence detail.
            Return STRICTLY valid JSON (no markdown fences, no prose) matching this recursive schema:
            {
              "id": "root",
              "label": "Core Topic",
              "detail": "Brief 1-sentence overview",
              "children": [
                {
                  "id": "b1",
                  "label": "Branch One",
                  "detail": "Short note",
                  "children": [
                    {"id": "b1_1", "label": "Sub Concept", "detail": "Key fact", "children": []}
                  ]
                }
              ]
            }

            Content:
            ${sourceText.take(9000)}
        """.trimIndent()
        val raw = collectSingleCompletion(selectableModel, apiKey, prompt) {}
        val jsonStr = extractJsonObject(raw)
            ?: throw IllegalStateException("Model did not return structured JSON for Mind Map.")
        parseMindMapNode(JSONObject(jsonStr), "root")
    }

    private fun parseMindMapNode(obj: JSONObject, fallbackId: String): MindMapNode {
        val id = obj.optString("id").ifBlank { fallbackId }
        val label = obj.optString("label").ifBlank { obj.optString("title", "Node") }.trim()
        val detail = obj.optString("detail", "").trim()
        val childArr = obj.optJSONArray("children")
        val children = buildList {
            if (childArr != null) {
                for (i in 0 until childArr.length()) {
                    val childObj = childArr.optJSONObject(i) ?: continue
                    add(parseMindMapNode(childObj, "${id}_$i"))
                }
            }
        }
        return MindMapNode(id = id, label = label, detail = detail, children = children)
    }

    private fun extractJsonObject(raw: String): String? {
        val stripped = raw
            .replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""```json\s*""", RegexOption.IGNORE_CASE), "")
            .replace("```", "")
            .trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        return if (start >= 0 && end > start) stripped.substring(start, end + 1) else null
    }

    private fun extractJsonArray(raw: String): String? {
        val stripped = raw
            .replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""```json\s*""", RegexOption.IGNORE_CASE), "")
            .replace("```", "")
            .trim()
        val start = stripped.indexOf('[')
        val end = stripped.lastIndexOf(']')
        return if (start >= 0 && end > start) stripped.substring(start, end + 1) else null
    }

    // --- Video Generation (Async Submit + Poll) ---

    suspend fun generateVideoAsync(
        prompt: String,
        aspectRatio: String,
        selectableModel: SelectableModel,
        onStatusUpdate: (String) -> Unit
    ): GeneratedMediaEntity {
        val recordId = generatedMediaDao.insert(
            GeneratedMediaEntity(
                mediaType = "VIDEO",
                prompt = prompt.trim(),
                modelDbId = selectableModel.model.id,
                modelLabel = selectableModel.model.displayName,
                providerId = selectableModel.provider.id,
                status = "PENDING",
                aspectRatio = aspectRatio
            )
        )

        return try {
            val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
            onStatusUpdate("Submitting video job…")
            val submission = aiRouter.submitVideo(
                provider = selectableModel.provider,
                apiKey = apiKey,
                modelId = selectableModel.model.modelId,
                prompt = prompt.trim(),
                aspectRatio = aspectRatio
            )

            generatedMediaDao.update(
                GeneratedMediaEntity(
                    id = recordId,
                    mediaType = "VIDEO",
                    prompt = prompt.trim(),
                    modelDbId = selectableModel.model.id,
                    modelLabel = selectableModel.model.displayName,
                    providerId = selectableModel.provider.id,
                    status = "POLLING",
                    operationName = submission.operationName,
                    aspectRatio = aspectRatio
                )
            )

            var finalUrl = submission.immediateVideoUrl
            val timeoutMs = 8 * 60 * 1000L
            val startTime = System.currentTimeMillis()

            while (finalUrl == null) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > timeoutMs) {
                    throw AiException(
                        AiErrorCategory.TIMEOUT,
                        "Video generation timed out after 8 minutes. The job may still finish on the provider side.",
                        providerId = selectableModel.provider.id
                    )
                }
                onStatusUpdate("Rendering video (${elapsed / 1000}s)…")
                delay(5000L)
                val poll = aiRouter.pollVideo(
                    provider = selectableModel.provider,
                    apiKey = apiKey,
                    operationName = submission.operationName
                )
                if (poll.done) {
                    if (!poll.errorMessage.isNullOrBlank()) {
                        throw AiException(
                            AiErrorCategory.UNKNOWN,
                            poll.errorMessage,
                            providerId = selectableModel.provider.id
                        )
                    }
                    finalUrl = poll.videoUrl
                    break
                }
            }

            val resolvedUrl = finalUrl ?: throw AiException(
                AiErrorCategory.UNKNOWN,
                "Video job finished without a downloadable URL.",
                providerId = selectableModel.provider.id
            )
            onStatusUpdate("Downloading completed video…")
            val dir = File(context.filesDir, "media/videos").apply { mkdirs() }
            val destFile = File(dir, "vid_${recordId}_${System.currentTimeMillis()}.mp4")
            aiRouter.downloadFile(resolvedUrl, selectableModel.provider, apiKey, destFile)

            val completed = GeneratedMediaEntity(
                id = recordId,
                mediaType = "VIDEO",
                prompt = prompt.trim(),
                modelDbId = selectableModel.model.id,
                modelLabel = selectableModel.model.displayName,
                providerId = selectableModel.provider.id,
                localFilePath = destFile.absolutePath,
                remoteUri = finalUrl,
                status = "COMPLETED",
                operationName = submission.operationName,
                aspectRatio = aspectRatio
            )
            generatedMediaDao.update(completed)
            completed
        } catch (ce: kotlinx.coroutines.CancellationException) {
            generatedMediaDao.update(
                GeneratedMediaEntity(
                    id = recordId,
                    mediaType = "VIDEO",
                    prompt = prompt.trim(),
                    modelDbId = selectableModel.model.id,
                    modelLabel = selectableModel.model.displayName,
                    providerId = selectableModel.provider.id,
                    status = "FAILED",
                    aspectRatio = aspectRatio,
                    errorMessage = "Video polling cancelled by user."
                )
            )
            throw ce
        } catch (t: Throwable) {
            val aiEx = t as? AiException
            val failed = GeneratedMediaEntity(
                id = recordId,
                mediaType = "VIDEO",
                prompt = prompt.trim(),
                modelDbId = selectableModel.model.id,
                modelLabel = selectableModel.model.displayName,
                providerId = selectableModel.provider.id,
                status = "FAILED",
                aspectRatio = aspectRatio,
                errorMessage = t.message ?: "Video generation failed.",
                suggestProxySetting = aiEx?.suggestProxySetting == true
            )
            generatedMediaDao.update(failed)
            throw (aiEx ?: Exception(failed.errorMessage, t))
        }
    }

    suspend fun deleteGeneratedMedia(item: GeneratedMediaEntity) {
        item.localFilePath?.let { path ->
            runCatching { File(path).delete() }
        }
        generatedMediaDao.deleteById(item.id)
    }

    // --- Direct Home Screen Widget Quick Ask (No Full App Launch) ---

    data class WidgetQuickAskResult(
        val modelLabel: String,
        val answerText: String,
        val deviceAction: DeviceAction? = null,
        val deviceActions: List<DeviceAction> = listOfNotNull(deviceAction)
    )

    suspend fun quickAskForWidget(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        onPartialUpdate: (modelLabel: String, partialText: String) -> Unit
    ): WidgetQuickAskResult = withContext(Dispatchers.IO) {
        val rawTrimmed = prompt.trim()
        val savedContacts = runCatching { quickContactsFlow.first() }.getOrDefault(emptyList())
        val savedCustomApps = runCatching { customAppsFlow.first() }.getOrDefault(emptyList())
        val trimmedPrompt = DeviceActionEngine.preprocessVoiceCommand(rawTrimmed, savedContacts)

        // 0a. Intercept /note <memo> from Home Screen Widget -> save structured Markdown note to Room
        if (trimmedPrompt.startsWith("/note ", ignoreCase = true) || trimmedPrompt.equals("/note", ignoreCase = true)) {
            val memoText = trimmedPrompt.removePrefix("/note").removePrefix("/NOTE").trim()
            if (memoText.isBlank()) {
                return@withContext WidgetQuickAskResult(
                    modelLabel = "NOTES",
                    answerText = "Type /note followed by your memo (e.g. /note Buy milk and send invoice tomorrow)."
                )
            }
            onPartialUpdate("NOTES", "Cleaning up note into structured Markdown…")
            val saved = saveVoiceQuickNote(memoText, resolvePreferredChatModel())
            val widgetReply = "SAVED NOTE: ${saved.title}\n\n${cleanWidgetAnswerText(saved.markdownContent)}"
            return@withContext WidgetQuickAskResult("VOICE NOTE", widgetReply)
        }

        // 0b. Intercept custom Assistant Routine trigger phrases right from Home Screen Widget
        val matchedRoutine = matchRoutineByPhrase(trimmedPrompt)
        if (matchedRoutine != null) {
            onPartialUpdate("ROUTINE", "Running \"${matchedRoutine.name}\"…")
            val runResult = executeRoutine(
                routine = matchedRoutine,
                preferredModel = resolvePreferredChatModel(),
                onProgress = { msg -> onPartialUpdate("ROUTINE", msg) }
            )
            val cleanedSummary = cleanWidgetAnswerText(runResult.summaryText)
                .ifBlank { "Routine \"${matchedRoutine.name}\" completed." }
            val actionSuffix = runResult.pendingDeviceAction?.let { "\n\nAction ready in AllChat: ${it.title}" } ?: ""
            return@withContext WidgetQuickAskResult(
                modelLabel = runResult.modelLabel,
                answerText = cleanedSummary + actionSuffix,
                deviceAction = runResult.pendingDeviceAction
            )
        }

        // 0c. Direct Sequential Multi-Action Interception (Call, WhatsApp Voice/Video/Message, SMS, Alarm, Timer, Maps, YouTube, Apps)
        val directChain = DeviceActionEngine.parseActionChainFromPrompt(trimmedPrompt, savedContacts, savedCustomApps)
        if (directChain.isNotEmpty()) {
            val firstAction = directChain.first()
            val actionSummary = if (directChain.size > 1) {
                "ACTION CHAIN (${directChain.size} STEPS) // " +
                    directChain.mapIndexed { i, a -> "${i + 1}. ${a.title}" }.joinToString(" → ")
            } else {
                when (firstAction) {
                    is DeviceAction.DialPhone -> firstAction.displayBanner
                    is DeviceAction.WhatsAppCall -> firstAction.displayBanner
                    is DeviceAction.SendWhatsApp -> "WHATSAPP // Ready for ${firstAction.contactLabel ?: firstAction.phoneNumber}" +
                        if (firstAction.message.isNotBlank()) ": \"${firstAction.message}\"" else ""
                    else -> "${firstAction.badge} // ${firstAction.title}"
                }
            }
            return@withContext WidgetQuickAskResult(
                modelLabel = if (directChain.size > 1) "CHAIN (${directChain.size})" else firstAction.badge,
                answerText = actionSummary,
                deviceAction = firstAction,
                deviceActions = directChain
            )
        }

        val available = observeModelsForCapability(ModelCapability.TEXT_CHAT).first()
        if (available.isEmpty()) {
            throw IllegalStateException("No Text Chat model configured. Open AllChat → Settings to add an API key.")
        }

        val activeConvId = getOrCreateActiveConversationId(
            defaultTitle = trimmedPrompt.replace("\n", " ").take(40).ifBlank { "Widget Chat" }
        )
        val latestConvModelId = conversationsFlow.first().firstOrNull { it.id == activeConvId }?.selectedModelDbId
            ?: conversationsFlow.first().firstOrNull()?.selectedModelDbId
        val preferredKeywords = listOf(
            "gemini-2.5", "gemini-2.0", "gpt-4o", "gpt-4.1", "claude-3", "claude-sonnet",
            "llama-3.3", "llama-3.1", "deepseek", "qwen", "instruct", "chat"
        )
        val selectedModel = available.firstOrNull { it.model.id == latestConvModelId }
            ?: preferredKeywords.firstNotNullOfOrNull { kw ->
                available.firstOrNull { it.model.modelId.contains(kw, ignoreCase = true) }
            }
            ?: available.first()

        val enabledMemories = userMemoryDao.getEnabled()
        val contactsDirectory = DeviceActionEngine.formatContactsForSystemPrompt(savedContacts)
        val systemPrompt = buildString {
            appendLine("You are AllChat running inside a compact Android Home Screen Widget with full Device Action tools.")
            appendLine("When the user asks to call someone, message/WhatsApp someone, send an SMS or email, pay via UPI, navigate Maps, set an alarm/timer, play YouTube, or open an app, ALWAYS call the `execute_device_action` tool immediately using the Saved Contacts Directory below. NEVER reply with plain text like 'User wants to contact ...' without invoking `execute_device_action`.")
            appendLine("For normal questions, give a direct, accurate, concise answer in plain readable text (short paragraphs or brief bullets) so it fits cleanly on a widget.")
            if (enabledMemories.isNotEmpty()) {
                appendLine()
                appendLine("User Memory Bank:")
                enabledMemories.forEach { m -> appendLine("- [${m.category}] ${m.fact}") }
            }
            if (contactsDirectory.isNotEmpty()) {
                appendLine()
                appendLine(contactsDirectory)
            }
        }.trim()

        // Build full conversation context from accumulated widget history (or active Room thread)
        val priorMessages = if (history.isNotEmpty()) {
            history.map { (role, text) -> ChatInputMessage(role = role, text = text) }
        } else {
            val dbMsgs = messageDao.getMessages(activeConvId)
            ConversationTree.resolveActiveBranch(dbMsgs, emptyMap())
                .map { it.message }
                .filter { !it.isError && it.content.isNotBlank() }
                .takeLast(16)
                .map { ChatInputMessage(role = it.role, text = it.content) }
        }

        val inputMessages = mutableListOf<ChatInputMessage>().apply {
            addAll(priorMessages)
            add(ChatInputMessage(role = "USER", text = trimmedPrompt))
        }

        val apiKey = getProviderApiKey(selectedModel.provider.id)
        val modelLabel = selectedModel.model.displayName
        val buffer = StringBuilder()
        var lastEmitMs = 0L
        var triggeredDeviceAction: DeviceAction? = null
        val triggeredDeviceActions = mutableListOf<DeviceAction>()

        var attempt = 0
        var lastNetworkError: Throwable? = null
        while (attempt < 2) {
            attempt++
            buffer.setLength(0)
            lastEmitMs = 0L
            triggeredDeviceAction = null
            triggeredDeviceActions.clear()
            val workingMessages = inputMessages.toMutableList()
            try {
                withTimeout(25_000L) {
                    var toolRound = 0
                    val maxToolRounds = 2
                    while (toolRound <= maxToolRounds) {
                        val roundText = StringBuilder()
                        var requestedTools: List<ToolCallInvocation> = emptyList()
                        aiRouter.streamChat(
                            provider = selectedModel.provider,
                            apiKey = apiKey,
                            modelId = selectedModel.model.modelId,
                            messages = workingMessages.toList(),
                            options = ChatOptions(
                                systemPrompt = systemPrompt,
                                maxTokens = 512,
                                enableTools = true,
                                supportsVisionInput = selectedModel.model.supportsImageInput
                            )
                        ).collect { event ->
                            when (event) {
                                is StreamEvent.TextDelta -> {
                                    roundText.append(event.text)
                                    buffer.append(event.text)
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmitMs >= 350L) {
                                        lastEmitMs = now
                                        val cleaned = cleanWidgetAnswerText(buffer.toString())
                                        if (cleaned.isNotBlank()) {
                                            onPartialUpdate(modelLabel, cleaned)
                                        }
                                    }
                                }
                                is StreamEvent.ToolCallRequest -> {
                                    requestedTools = event.calls
                                }
                                else -> Unit
                            }
                        }

                        if (requestedTools.isEmpty() || toolRound >= maxToolRounds) {
                            break
                        }

                        workingMessages += ChatInputMessage(
                            role = "ASSISTANT",
                            text = roundText.toString(),
                            toolCalls = requestedTools
                        )
                        for (tc in requestedTools) {
                            onPartialUpdate(modelLabel, "TOOL // Executing ${tc.name}…")
                            val outcome = ClientToolEngine.executeTool(
                                name = tc.name,
                                argumentsJson = tc.argumentsJson,
                                webSearchClient = webSearchClient,
                                contacts = savedContacts,
                                customApps = savedCustomApps
                            )
                            outcome.triggeredDeviceAction?.let { triggered ->
                                val enriched = when (triggered) {
                                    is DeviceAction.DialPhone -> if (triggered.contactLabel.isNullOrBlank() && triggered.phoneNumber.isNotBlank()) {
                                        val byPhone = DeviceActionEngine.resolveContactByPhone(triggered.phoneNumber, savedContacts)
                                        if (byPhone != null) triggered.copy(contactLabel = byPhone.name) else triggered
                                    } else triggered
                                    is DeviceAction.WhatsAppCall -> if (triggered.contactLabel.isNullOrBlank() && triggered.phoneNumber.isNotBlank()) {
                                        val byPhone = DeviceActionEngine.resolveContactByPhone(triggered.phoneNumber, savedContacts)
                                        if (byPhone != null) triggered.copy(contactLabel = byPhone.name) else triggered
                                    } else triggered
                                    else -> triggered
                                }
                                triggeredDeviceActions += enriched
                                if (triggeredDeviceAction == null) triggeredDeviceAction = enriched
                            }
                            workingMessages += ChatInputMessage(
                                role = "TOOL",
                                text = outcome.resultJson,
                                toolCallId = tc.id,
                                toolName = tc.name
                            )
                        }
                        if (triggeredDeviceActions.isNotEmpty()) {
                            // Device action(s) resolved cleanly — no need to wait for another verbose LLM round
                            break
                        }
                        toolRound++
                    }
                }
                lastNetworkError = null
                break
            } catch (tce: TimeoutCancellationException) {
                throw IllegalStateException("Request timed out after 25s on $modelLabel. Tap RETRY to try again.")
            } catch (ex: AiException) {
                val isTransientNetwork = ex.category == AiErrorCategory.NETWORK_PROXY &&
                    buffer.isEmpty() &&
                    attempt < 2
                if (isTransientNetwork) {
                    lastNetworkError = ex
                    // Evict stale sockets after Wi-Fi/idle handoff and retry once cleanly
                    runCatching { okHttpClient.connectionPool.evictAll() }
                    delay(450L)
                    continue
                }
                throw ex
            }
        }
        if (lastNetworkError != null) {
            throw lastNetworkError
        }

        // Fallback plain-text action interceptor if model replied with text instead of calling execute_device_action
        if (triggeredDeviceActions.isEmpty() && buffer.isNotBlank()) {
            DeviceActionEngine.parseFromAssistantReply(
                userPrompt = trimmedPrompt,
                assistantReply = buffer.toString(),
                contacts = savedContacts,
                customApps = savedCustomApps
            )?.let { fallbackAction ->
                triggeredDeviceActions += fallbackAction
                triggeredDeviceAction = fallbackAction
            }
        }

        val resolvedAction = triggeredDeviceActions.firstOrNull() ?: triggeredDeviceAction
        val finalAnswer = when {
            triggeredDeviceActions.size > 1 ->
                "ACTION CHAIN (${triggeredDeviceActions.size} STEPS) // " +
                    triggeredDeviceActions.mapIndexed { i, a -> "${i + 1}. ${a.title}" }.joinToString(" → ")
            resolvedAction is DeviceAction.DialPhone -> resolvedAction.displayBanner
            resolvedAction is DeviceAction.WhatsAppCall -> resolvedAction.displayBanner
            resolvedAction is DeviceAction.SendWhatsApp ->
                "WHATSAPP // Ready for ${resolvedAction.contactLabel ?: resolvedAction.phoneNumber}" +
                    if (resolvedAction.message.isNotBlank()) ": \"${resolvedAction.message}\"" else ""
            resolvedAction != null -> "${resolvedAction.badge} // ${resolvedAction.title}"
            else -> cleanWidgetAnswerText(buffer.toString())
                .ifBlank { "No response received from $modelLabel." }
        }

        // Persist the widget Q&A turn into the shared active conversation in Room
        runCatching {
            appendInstantExchange(
                conversationId = activeConvId,
                userText = trimmedPrompt,
                assistantText = finalAnswer,
                modelLabel = resolvedAction?.badge ?: modelLabel
            )
        }
        WidgetQuickAskResult(
            modelLabel = if (triggeredDeviceActions.size > 1) "CHAIN (${triggeredDeviceActions.size})" else resolvedAction?.badge ?: modelLabel,
            answerText = finalAnswer,
            deviceAction = resolvedAction,
            deviceActions = triggeredDeviceActions.ifEmpty { listOfNotNull(resolvedAction) }
        )
    }

    private fun cleanWidgetAnswerText(raw: String): String {
        return raw
            .replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<reasoning>[\s\S]*?</reasoning>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""```[a-zA-Z0-9_-]*\n?"""), "")
            .replace("```", "")
            .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
            .replace(Regex("""`([^`]+)`"""), "$1")
            .replace(Regex("""^#+\s*""", RegexOption.MULTILINE), "")
            .trim()
    }

    // --- Smart Follow-Up Suggestion Chips ---

    suspend fun generateFollowUpChips(
        userPrompt: String,
        assistantReply: String,
        selectableModel: SelectableModel
    ): List<String> = withContext(Dispatchers.IO) {
        if (assistantReply.length < 40) return@withContext emptyList()
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        val prompt = """
            Based on this Q&A turn, suggest exactly 3 concise, high-value follow-up prompts the user might tap next on mobile (3 to 7 words each, no numbering, no quotes).
            Return STRICTLY a JSON array of 3 strings, e.g. ["Show a working code example", "Explain the trade-offs", "Convert to a checklist"].

            User: ${userPrompt.take(500)}
            Assistant: ${assistantReply.take(1500)}
        """.trimIndent()
        val raw = runCatching {
            collectSingleCompletion(selectableModel, apiKey, prompt) {}
        }.getOrDefault("")
        val arrStr = extractJsonArray(raw) ?: return@withContext emptyList()
        runCatching {
            val arr = JSONArray(arrStr)
            buildList {
                for (i in 0 until arr.length().coerceAtMost(3)) {
                    val item = arr.optString(i).trim()
                    if (item.length in 3..64) add(item)
                }
            }
        }.getOrDefault(emptyList())
    }

    // --- Hands-Free Live Voice Conversation Loop (Sentence-by-Sentence Streaming) ---

    suspend fun streamLiveVoiceReply(
        history: List<Pair<String, String>>,
        userText: String,
        selectableModel: SelectableModel,
        onPartialFullText: (String) -> Unit,
        onSentenceReady: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        val enabledMemories = userMemoryDao.getEnabled()
        val sysPrompt = buildString {
            appendLine("You are AllChat in Hands-Free Live Voice Conversation mode.")
            appendLine("Respond naturally in spoken conversational sentences (2-4 sentences unless asked for more). Avoid markdown formatting, code fences, or bullet symbols so text-to-speech reads smoothly.")
            if (enabledMemories.isNotEmpty()) {
                appendLine("User Memory:")
                enabledMemories.forEach { appendLine("- ${it.fact}") }
            }
        }.trim()

        val messages = buildList {
            history.takeLast(8).forEach { (role, content) ->
                add(ChatInputMessage(role = role, text = content))
            }
            add(ChatInputMessage(role = "USER", text = userText.trim()))
        }

        val fullBuffer = StringBuilder()
        val sentenceBuffer = StringBuilder()

        aiRouter.streamChat(
            provider = selectableModel.provider,
            apiKey = apiKey,
            modelId = selectableModel.model.modelId,
            messages = messages,
            options = ChatOptions(systemPrompt = sysPrompt, maxTokens = 512)
        ).collect { event ->
            if (event is StreamEvent.TextDelta) {
                fullBuffer.append(event.text)
                sentenceBuffer.append(event.text)
                onPartialFullText(cleanWidgetAnswerText(fullBuffer.toString()))

                // Flush completed sentences immediately to TTS for low-latency conversation
                val current = sentenceBuffer.toString()
                val breakIdx = findSentenceBreak(current)
                if (breakIdx > 0) {
                    val readySentence = current.substring(0, breakIdx).trim()
                    val remainder = current.substring(breakIdx)
                    sentenceBuffer.clear()
                    sentenceBuffer.append(remainder)
                    if (readySentence.length >= 6) {
                        onSentenceReady(cleanWidgetAnswerText(readySentence))
                    }
                }
            }
        }

        val tail = cleanWidgetAnswerText(sentenceBuffer.toString())
        if (tail.isNotBlank()) {
            onSentenceReady(tail)
        }
        cleanWidgetAnswerText(fullBuffer.toString())
    }

    private fun findSentenceBreak(text: String): Int {
        for (i in text.indices) {
            val ch = text[i]
            if ((ch == '.' || ch == '!' || ch == '?' || ch == '\n') && i >= 18) {
                val nextIsBoundary = i == text.lastIndex || text[i + 1].isWhitespace()
                if (nextIsBoundary) return i + 1
            }
        }
        return -1
    }

    // --- Save Bitmap (Region Snip / Live Camera Viewfinder Frame) as Local Attachment ---

    suspend fun saveBitmapAsAttachment(bitmap: Bitmap, prefix: String = "snip"): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        Uri.fromFile(file).toString()
    }

    // --- Batch Multi-Document Extraction (5-10 PDFs/TXT/MD at once) ---

    suspend fun extractMultipleDocuments(uris: List<Uri>): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val capped = uris.take(10)
        val perDocLimit = if (capped.size >= 5) 5500 else 10_000
        capped.mapNotNull { uri ->
            runCatching {
                val doc = DocumentExtractor.extractFromUri(context, uri)
                doc.title to doc.text.take(perDocLimit)
            }.getOrNull()
        }
    }

    // --- Tone & Rewrite Studio ---

    suspend fun rewriteText(
        inputText: String,
        preset: RewritePreset,
        customInstruction: String,
        selectableModel: SelectableModel
    ): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        val extra = customInstruction.trim().takeIf { it.isNotEmpty() }?.let { "\nAdditional instruction: $it" } ?: ""
        val prompt = """
            ${preset.instruction}$extra
            Return ONLY the rewritten final text with zero preamble, no quotes around the whole message, and no commentary.

            Original text:
            ${inputText.trim()}
        """.trimIndent()
        collectSingleCompletion(selectableModel, apiKey, prompt) {}.trim()
    }

    // --- Code & Error Debugger Mode ---

    suspend fun debugCodeAndError(
        codeOrTrace: String,
        language: String,
        contextNote: String,
        selectableModel: SelectableModel
    ): CodeDebugResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        val extraCtx = contextNote.trim().takeIf { it.isNotEmpty() }?.let { "\nExtra context: $it" } ?: ""
        val prompt = """
            You are a senior Staff Software Engineer debugging an error or broken code snippet ($language).$extraCtx
            Analyze the root cause and produce a clean, complete fixed code patch.
            Return STRICTLY valid JSON (no markdown fences, no commentary outside JSON) with this exact schema:
            {
              "rootCause": "One crisp headline naming the exact root cause",
              "severity": "CRITICAL | HIGH | MEDIUM | LOW",
              "explanation": "Concise 2-4 sentence technical breakdown of why this happens",
              "fixedCode": "Complete corrected code / patch ready to paste",
              "patchSummary": "One-line summary of the exact diff/fix applied",
              "verificationSteps": ["Step 1 to verify fix", "Step 2 to verify edge case"]
            }

            Stack Trace / Broken Code:
            ${codeOrTrace.take(14_000)}
        """.trimIndent()
        val raw = collectSingleCompletion(selectableModel, apiKey, prompt) {}
        val jsonStr = extractJsonObject(raw)
            ?: throw IllegalStateException("Model did not return structured JSON for Code Debugger.")
        val obj = JSONObject(jsonStr)
        val stepsArr = obj.optJSONArray("verificationSteps") ?: JSONArray()
        val steps = buildList {
            for (i in 0 until stepsArr.length()) {
                val s = stepsArr.optString(i).trim()
                if (s.isNotEmpty()) add(s)
            }
        }
        CodeDebugResult(
            rootCause = obj.optString("rootCause").ifBlank { "Root Cause Identified" },
            severity = obj.optString("severity", "HIGH").uppercase(),
            explanation = obj.optString("explanation").ifBlank { "See fixed patch below." },
            fixedCode = obj.optString("fixedCode").ifBlank { raw },
            patchSummary = obj.optString("patchSummary").ifBlank { "Applied fix for reported error." },
            verificationSteps = steps
        )
    }

    // --- YouTube Video / Lecture Extractor ---

    suspend fun extractYouTubeStudyNotes(
        rawUrl: String,
        selectableModel: SelectableModel,
        onStatus: (String) -> Unit
    ): YouTubeStudyResult = withContext(Dispatchers.IO) {
        onStatus("YOUTUBE // Fetching video metadata & timed captions…")
        val extracted = youtubeExtractorClient.fetchVideoAndTranscript(rawUrl)
        onStatus(
            if (extracted.hasTimestampedCaptions) {
                "YOUTUBE // Captions extracted (${extracted.transcriptText.length} chars). Generating study notes…"
            } else {
                "YOUTUBE // Metadata fetched. Generating chapters & takeaways with ${selectableModel.model.displayName}…"
            }
        )
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        val transcriptSection = if (extracted.transcriptText.isNotBlank()) {
            """
            Transcript / Page Content:
            ${extracted.transcriptText.take(22_000)}
            """.trimIndent()
        } else {
            "No public captions were exposed; analyze this YouTube video URL and title carefully using your knowledge and grounding: ${extracted.canonicalUrl}"
        }

        val prompt = """
            Analyze this YouTube video/lecture and generate comprehensive, cleanly structured Markdown study notes.
            Video Title: ${extracted.title}
            Channel: ${extracted.author}
            URL: ${extracted.canonicalUrl}

            Produce these exact sections in Markdown:
            ## 1. Executive Overview
            (2-3 concise sentences)

            ## 2. Timestamped Chapters
            (Bulleted chapters with `[MM:SS]` timestamps where available from the transcript, each with a crisp summary)

            ## 3. Key Takeaways
            (5-8 high-signal actionable takeaways)

            ## 4. Structured Study Notes & Definitions
            (Organized concepts, formulas, code/steps, or mental models taught in the video)

            $transcriptSection
        """.trimIndent()

        val options = ChatOptions(
            enableGeminiGoogleSearch = selectableModel.provider.type == ProviderType.GEMINI && !extracted.hasTimestampedCaptions
        )
        val buffer = StringBuilder()
        aiRouter.streamChat(
            provider = selectableModel.provider,
            apiKey = apiKey,
            modelId = selectableModel.model.modelId,
            messages = listOf(ChatInputMessage(role = "USER", text = prompt)),
            options = options
        ).collect { event ->
            if (event is StreamEvent.TextDelta) {
                buffer.append(event.text)
            }
        }

        YouTubeStudyResult(
            videoId = extracted.videoId,
            canonicalUrl = extracted.canonicalUrl,
            title = extracted.title,
            author = extracted.author,
            hasTimestampedCaptions = extracted.hasTimestampedCaptions,
            markdownNotes = buffer.toString().trim().ifBlank { "No notes generated." }
        )
    }

    // --- Live Lens Zero-Prompt Camera Auto-Explainer & Live Translator (with 429 Auto-Failover) ---

    private fun isRateLimitError(t: Throwable): Boolean {
        if (t is AiException && t.category == AiErrorCategory.RATE_LIMIT) return true
        val msg = t.message.orEmpty().lowercase()
        return msg.contains("429") ||
            msg.contains("resource_exhausted") ||
            msg.contains("rate limit") ||
            msg.contains("rate_limit") ||
            msg.contains("too many requests") ||
            msg.contains("quota exceeded") ||
            msg.contains("requests per minute")
    }

    suspend fun analyzeLiveLensFrame(
        bitmap: Bitmap,
        mode: LiveLensMode,
        targetLanguage: String,
        preferredModel: SelectableModel?,
        overlayTranslateMode: Boolean = false,
        multiBoxCount: Int = 0,
        onModelFailover: ((String) -> Unit)? = null
    ): LiveLensAnalysisResult = withContext(Dispatchers.IO) {
        val available = observeModelsForCapability(ModelCapability.TEXT_CHAT).first()
        val visionModels = available.filter { it.model.supportsImageInput }.ifEmpty { available }
        if (visionModels.isEmpty()) {
            throw IllegalStateException("No Vision-capable model configured. Open Settings to add Gemini, GPT-4o, or Claude.")
        }

        // Build priority fallback queue:
        // 1. User-configured Vision Fallback Queue in Settings / Live Lens
        // 2. Currently selected / preferred model (if vision-capable)
        // 3. Remaining vision-capable models across all configured providers
        val configuredPriorityIds = visionFallbackModelIdsFlow.first()
        val orderedCandidates = buildList {
            val addedIds = hashSetOf<Long>()
            configuredPriorityIds.forEach { id ->
                visionModels.firstOrNull { it.model.id == id }?.let { m ->
                    if (addedIds.add(m.model.id)) add(m)
                }
            }
            if (preferredModel != null) {
                visionModels.firstOrNull { it.model.id == preferredModel.model.id }?.let { freshPref ->
                    if (addedIds.add(freshPref.model.id)) add(freshPref)
                }
            }
            visionModels.forEach { m ->
                if (addedIds.add(m.model.id)) add(m)
            }
        }

        val nowMs = System.currentTimeMillis()
        val readyModels = orderedCandidates.filter { (modelCooldownUntilMs[it.model.id] ?: 0L) <= nowMs }
        val coolingModels = orderedCandidates
            .filter { (modelCooldownUntilMs[it.model.id] ?: 0L) > nowMs }
            .sortedBy { modelCooldownUntilMs[it.model.id] ?: 0L }
        val attemptOrder = (readyModels + coolingModels)

        val savedUri = saveBitmapAsAttachment(bitmap, prefix = "lens")
        val (imgBase64, imgMime) = ImageEncoder.encodeFromStoredUri(context, savedUri)
            ?: throw IllegalStateException("Could not encode live camera frame.")

        val multiBoxHint = if (multiBoxCount >= 2) {
            "\nThis composite image contains $multiBoxCount cropped regions labeled [Q1]..[Q$multiBoxCount]. Address EVERY labeled region in order cleanly."
        } else {
            ""
        }

        val systemInstruction = when {
            overlayTranslateMode ->
                """You are an on-screen visual translator. Detect every distinct foreign-language text block, sign, label, or manual line in this image and translate it cleanly into $targetLanguage.
Return ONLY a valid JSON array of objects using normalized coordinates (0.0 to 1.0 relative to image width/height):
[{"original":"...", "translated":"...", "x":0.10, "y":0.18, "w":0.65, "h":0.10}]
Keep translations crisp and natural. Limit to the 8 most prominent text blocks.""".trimIndent()
            mode == LiveLensMode.AUTO_EXPLAIN ->
                "Identify, explain, and summarize what is shown in this image clearly and concisely without waiting for further questions.$multiBoxHint"
            mode == LiveLensMode.LIVE_TRANSLATOR ->
                "Extract all visible text in this image and translate it cleanly into $targetLanguage, preserving layout context.$multiBoxHint"
            mode == LiveLensMode.SOLVE_STEP_BY_STEP ->
                """You are an expert STEM, mathematics, physics, circuit analysis, and code debugging tutor.$multiBoxHint
Carefully read the equation, circuit derivation, or error shown in this image and solve it cleanly in structured steps.
Format EVERY step strictly using these headings so the UI can render collapsible interactive cards:
### STEP 01: <Short Step Title>
<Clear working with standard LaTeX math formatting using inline ${'$'}...${'$'} or display ${'$'}${'$'}...${'$'}${'$'} where appropriate>
WHY: <1-2 crisp sentences explaining the underlying formula, theorem, law, or debugging rule used in this step>

### STEP 02: <Short Step Title>
<Working>
WHY: <1-2 crisp sentences explaining why this step is valid>

### FINAL RESULT:
<Verified final answer clearly highlighted>""".trimIndent()
            else ->
                "You are an expert UI Engineer and Systems Architect.$multiBoxHint Inspect this hand-drawn sketch, wireframe, flowchart, circuit diagram, or truth table. If it is a UI wireframe, generate a complete, self-contained single-file HTML+CSS responsive working UI wrapped in ```html ... ```. If it is a flowchart, block diagram, state machine, or table, convert it into clean Mermaid.js code wrapped in ```mermaid ... ``` AND a LaTeX tabular/equation block."
        }
        val userPrompt = when {
            overlayTranslateMode ->
                "Detect and translate all visible text blocks in this frame into $targetLanguage with normalized (x, y, w, h) coordinates."
            multiBoxCount >= 2 && mode == LiveLensMode.SOLVE_STEP_BY_STEP ->
                "Solve all $multiBoxCount labeled regions ([Q1]..[Q$multiBoxCount]) in this stitched composite image using structured STEP 01 / WHY / FINAL RESULT cards."
            multiBoxCount >= 2 ->
                "Analyze all $multiBoxCount labeled regions ([Q1]..[Q$multiBoxCount]) in this stitched composite image clearly in order."
            mode == LiveLensMode.AUTO_EXPLAIN ->
                "Analyze this live view right now."
            mode == LiveLensMode.LIVE_TRANSLATOR ->
                "Read all visible text in this image and translate it cleanly into $targetLanguage."
            mode == LiveLensMode.SOLVE_STEP_BY_STEP ->
                "Solve the math equation, physics/circuit derivation, or code error shown in this image using the structured STEP 01 / WHY / FINAL RESULT format."
            else ->
                "Digitize this hand-drawn wireframe, diagram, or whiteboard sketch into clean runnable HTML/CSS or Mermaid/LaTeX code."
        }
        val maxLensTokens = when {
            overlayTranslateMode -> 1000
            multiBoxCount >= 2 -> 2000
            mode == LiveLensMode.SOLVE_STEP_BY_STEP || mode == LiveLensMode.WHITEBOARD_TO_CODE -> 1600
            else -> 720
        }

        var lastError: Throwable? = null
        var lastFailoverNotice: String? = null

        for (index in attemptOrder.indices) {
            val candidate = attemptOrder[index]
            val apiKey = getProviderApiKey(candidate.provider.id)
            val buffer = StringBuilder()
            val reqStart = System.currentTimeMillis()
            try {
                withTimeout(35_000L) {
                    aiRouter.streamChat(
                        provider = candidate.provider,
                        apiKey = apiKey,
                        modelId = candidate.model.modelId,
                        messages = listOf(
                            ChatInputMessage(
                                role = "USER",
                                text = userPrompt,
                                imageBase64 = imgBase64,
                                imageMimeType = imgMime
                            )
                        ),
                        options = ChatOptions(
                            systemPrompt = systemInstruction,
                            maxTokens = maxLensTokens,
                            injectMemory = false
                        )
                    ).collect { event ->
                        if (event is StreamEvent.TextDelta) {
                            buffer.append(event.text)
                        }
                    }
                }
                val elapsed = (System.currentTimeMillis() - reqStart).coerceAtLeast(1L)
                runCatching { apiKeyVault.recordModelLatency(candidate.model.id, elapsed) }

                val rawOut = buffer.toString()
                if (overlayTranslateMode) {
                    val regions = parseLiveLensOverlayRegions(rawOut)
                    val summaryText = if (regions.isNotEmpty()) {
                        regions.joinToString("\n") { r ->
                            if (r.original.isNotBlank() && !r.original.equals(r.translated, ignoreCase = true)) {
                                "${r.translated}  (${r.original})"
                            } else {
                                r.translated
                            }
                        }
                    } else {
                        cleanWidgetAnswerText(rawOut)
                            .ifBlank { "No readable text detected in this frame." }
                    }
                    return@withContext LiveLensAnalysisResult(
                        modelLabel = "${candidate.model.displayName} · ${candidate.provider.name}",
                        answer = summaryText,
                        failoverNotice = lastFailoverNotice,
                        overlayRegions = regions
                    )
                }

                val resultText = if (mode == LiveLensMode.SOLVE_STEP_BY_STEP) {
                    stripReasoningTagsOnly(rawOut)
                        .ifBlank { "Could not read equations or diagram details. Hold steady and try again." }
                } else {
                    cleanWidgetAnswerText(rawOut)
                        .ifBlank { "Could not read details from this frame. Hold steady and try again." }
                }
                return@withContext LiveLensAnalysisResult(
                    modelLabel = "${candidate.model.displayName} · ${candidate.provider.name}",
                    answer = resultText,
                    failoverNotice = lastFailoverNotice
                )
            } catch (tce: TimeoutCancellationException) {
                lastError = IllegalStateException("Vision request timed out after 35s on ${candidate.model.displayName}.")
                if (index + 1 < attemptOrder.size) {
                    val next = attemptOrder[index + 1]
                    val notice = "Timeout on ${candidate.model.displayName} — auto-switched to ${next.model.displayName}"
                    lastFailoverNotice = notice
                    onModelFailover?.invoke(notice)
                    continue
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                lastError = t
                if (isRateLimitError(t)) {
                    markModelRateLimited(candidate.model.id)
                    if (index + 1 < attemptOrder.size) {
                        val next = attemptOrder[index + 1]
                        val notice = "Rate limit hit on ${candidate.model.displayName} — auto-switched to ${next.model.displayName}"
                        lastFailoverNotice = notice
                        onModelFailover?.invoke(notice)
                        continue
                    }
                } else {
                    throw t
                }
            }
        }

        throw lastError ?: IllegalStateException("All configured vision models are currently rate-limited. Wait 60s or add another provider in Settings.")
    }

    private fun parseLiveLensOverlayRegions(rawOut: String): List<LiveLensOverlayRegion> {
        val cleaned = stripReasoningTagsOnly(rawOut).trim()
        val jsonArrStr = extractJsonArray(cleaned)
        if (jsonArrStr != null) {
            val parsed = runCatching {
                val arr = JSONArray(jsonArrStr)
                buildList {
                    for (i in 0 until arr.length().coerceAtMost(10)) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val translated = obj.optString("translated").trim()
                        if (translated.isBlank()) continue
                        val original = obj.optString("original").trim()
                        val x = obj.optDouble("x", 0.08).toFloat().coerceIn(0.02f, 0.85f)
                        val y = obj.optDouble("y", (0.14 + i * 0.11).coerceAtMost(0.84)).toFloat().coerceIn(0.04f, 0.86f)
                        val w = obj.optDouble("w", 0.72).toFloat().coerceIn(0.15f, 0.94f)
                        val h = obj.optDouble("h", 0.09).toFloat().coerceIn(0.05f, 0.35f)
                        add(LiveLensOverlayRegion(original, translated, x, y, w, h))
                    }
                }
            }.getOrDefault(emptyList())
            if (parsed.isNotEmpty()) return parsed
        }
        // Clean fallback: stack non-empty translated lines neatly across the frame
        val lines = cleanWidgetAnswerText(rawOut)
            .lines()
            .map { it.trim().trimStart('-', '*', '•', ' ') }
            .filter { it.isNotBlank() }
            .take(6)
        return lines.mapIndexed { idx, line ->
            LiveLensOverlayRegion(
                original = "",
                translated = line,
                x = 0.07f,
                y = (0.16f + idx * 0.115f).coerceAtMost(0.82f),
                w = 0.86f,
                h = 0.09f
            )
        }
    }

    suspend fun explainSolverStepWhy(
        stepTitle: String,
        stepBody: String,
        problemSummary: String,
        preferredModel: SelectableModel?
    ): String = withContext(Dispatchers.IO) {
        val model = preferredModel ?: resolvePreferredChatModel()
            ?: return@withContext "Configure a model in Settings to expand step formulas."
        val apiKey = getProviderApiKey(model.provider.id)
        val prompt = """
            In 2 short, crystal-clear sentences, explain the underlying mathematical formula, physics/circuit law, or programming rule used in this step:
            Step: $stepTitle
            Work: ${stepBody.take(500)}
            Context: ${problemSummary.take(400)}
        """.trimIndent()
        val raw = runCatching {
            collectSingleCompletion(model, apiKey, prompt) {}
        }.getOrDefault("")
        stripReasoningTagsOnly(raw).trim()
            .ifBlank { "This step applies the standard transformation rule to simplify the expression toward the final result." }
    }

    suspend fun resolvePreferredChatModel(): SelectableModel? = withContext(Dispatchers.IO) {
        val available = observeModelsForCapability(ModelCapability.TEXT_CHAT).first()
        if (available.isEmpty()) return@withContext null
        val persistedModelId = activeModelDbIdFlow.first()
        val activeConvId = activeConversationIdFlow.first()
        val allConvs = conversationsFlow.first()
        val activeConvModelId = allConvs.firstOrNull { it.id == activeConvId }?.selectedModelDbId
            ?: allConvs.firstOrNull()?.selectedModelDbId
        val preferredKeywords = listOf(
            "gemini-2.5", "gemini-2.0", "gpt-4o", "gpt-4.1", "claude-3", "claude-sonnet",
            "llama-3.3", "llama-3.1", "deepseek", "qwen", "instruct", "chat"
        )
        available.firstOrNull { it.model.id == persistedModelId }
            ?: available.firstOrNull { it.model.id == activeConvModelId }
            ?: preferredKeywords.firstNotNullOfOrNull { kw ->
                available.firstOrNull { it.model.modelId.contains(kw, ignoreCase = true) }
            }
            ?: available.first()
    }

    // --- 1-Tap Assistant Routines & Macros ---

    private fun stripReasoningTagsOnly(raw: String): String = raw
        .replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""<reasoning>[\s\S]*?</reasoning>""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun RoutineEntity.toDomainRoutine(): AssistantRoutine {
        val parsedSteps = runCatching {
            val arr = JSONArray(stepsJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val typeName = obj.optString("type", RoutineStepType.AI_PROMPT.name)
                    val stepType = runCatching { RoutineStepType.valueOf(typeName) }
                        .getOrDefault(RoutineStepType.AI_PROMPT)
                    add(
                        RoutineStep(
                            id = obj.optString("id", "step_$i"),
                            type = stepType,
                            payload = obj.optString("payload", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        return AssistantRoutine(
            id = id,
            name = name,
            triggerPhrase = triggerPhrase,
            steps = parsedSteps,
            createdAt = createdAt
        )
    }

    private fun serializeRoutineSteps(steps: List<RoutineStep>): String {
        val arr = JSONArray()
        steps.forEachIndexed { idx, step ->
            arr.put(
                JSONObject()
                    .put("id", step.id.ifBlank { "step_${idx + 1}" })
                    .put("type", step.type.name)
                    .put("payload", step.payload.trim())
            )
        }
        return arr.toString()
    }

    suspend fun saveRoutine(routine: AssistantRoutine): Long = withContext(Dispatchers.IO) {
        val entity = RoutineEntity(
            id = routine.id,
            name = routine.name.trim().ifBlank { "Untitled Routine" },
            triggerPhrase = routine.triggerPhrase.trim(),
            stepsJson = serializeRoutineSteps(routine.steps),
            createdAt = if (routine.id > 0L) routine.createdAt else System.currentTimeMillis()
        )
        if (routine.id <= 0L) {
            routineDao.insert(entity.copy(id = 0L))
        } else {
            routineDao.update(entity)
            routine.id
        }
    }

    suspend fun deleteRoutine(id: Long) = withContext(Dispatchers.IO) {
        routineDao.deleteById(id)
    }

    suspend fun matchRoutineByPhrase(rawInput: String): AssistantRoutine? = withContext(Dispatchers.IO) {
        val normalized = rawInput.trim().lowercase()
            .removePrefix("run ")
            .removePrefix("start ")
            .removePrefix("routine ")
            .trim()
        if (normalized.length < 2) return@withContext null
        val all = routineDao.getAll().map { it.toDomainRoutine() }
        all.firstOrNull { r ->
            val phrase = r.triggerPhrase.trim().lowercase()
            val rName = r.name.trim().lowercase()
            (phrase.isNotEmpty() && (rawInput.trim().equals(phrase, ignoreCase = true) || normalized == phrase)) ||
                (rName.isNotEmpty() && (rawInput.trim().equals("run $rName", ignoreCase = true) || normalized == rName))
        }
    }

    suspend fun executeRoutine(
        routine: AssistantRoutine,
        preferredModel: SelectableModel? = null,
        onProgress: ((String) -> Unit)? = null
    ): RoutineRunResult = withContext(Dispatchers.IO) {
        val contacts = quickContactsFlow.first()
        val apps = customAppsFlow.first()
        val activeModel = preferredModel ?: resolveFreshModel()
        val sections = mutableListOf<String>()
        var lastAiOutput = ""
        var spokenText: String? = null
        var pendingAction: DeviceAction? = null
        var savedNote: VoiceNoteInfo? = null
        var modelLabel = "ROUTINE"

        routine.steps.forEachIndexed { idx, step ->
            val stepNum = idx + 1
            val total = routine.steps.size
            when (step.type) {
                RoutineStepType.AI_PROMPT, RoutineStepType.AI_WEB_SEARCH -> {
                    val promptText = step.payload.trim().ifBlank { routine.name }
                    val useSearch = step.type == RoutineStepType.AI_WEB_SEARCH
                    onProgress?.invoke("Step $stepNum/$total · ${if (useSearch) "Web Search + AI" else "AI Prompt"}…")
                    if (activeModel == null) {
                        sections += "**Step $stepNum (AI):** No model configured in Settings."
                    } else {
                        modelLabel = "${activeModel.model.displayName} · Routine"
                        val apiKey = getProviderApiKey(activeModel.provider.id)
                        val extraGrounding = if (useSearch) {
                            val searchProvider = searchProviderFlow.first()
                            val searchKey = getSearchApiKey()
                            if (searchKey.isNotBlank() && searchProvider != WebSearchProvider.GEMINI_NATIVE) {
                                runCatching {
                                    webSearchClient.searchWeb(promptText, searchProvider, searchKey, maxResults = 5)
                                }.getOrDefault(emptyList()).takeIf { it.isNotEmpty() }?.let { results ->
                                    buildString {
                                        appendLine("Live Web Search Context:")
                                        results.forEachIndexed { i, c ->
                                            appendLine("[${i + 1}] ${c.title} (${c.url}): ${c.snippet}")
                                        }
                                    }
                                }.orEmpty()
                            } else {
                                ""
                            }
                        } else {
                            ""
                        }
                        val fullPrompt = if (extraGrounding.isNotBlank()) {
                            "$extraGrounding\n\nTask: $promptText"
                        } else {
                            promptText
                        }
                        val enableGeminiSearch = useSearch &&
                            activeModel.provider.type == ProviderType.GEMINI &&
                            extraGrounding.isBlank()
                        val buffer = StringBuilder()
                        withTimeout(30_000L) {
                            aiRouter.streamChat(
                                provider = activeModel.provider,
                                apiKey = apiKey,
                                modelId = activeModel.model.modelId,
                                messages = listOf(ChatInputMessage(role = "USER", text = fullPrompt)),
                                options = ChatOptions(
                                    systemPrompt = "You are executing step $stepNum of the user's AllChat Routine \"${routine.name}\". Be crisp, structured, and directly actionable.",
                                    maxTokens = 750,
                                    enableGeminiGoogleSearch = enableGeminiSearch
                                )
                            ).collect { ev ->
                                if (ev is StreamEvent.TextDelta) buffer.append(ev.text)
                            }
                        }
                        val cleaned = stripReasoningTagsOnly(buffer.toString())
                        if (cleaned.isNotBlank()) {
                            lastAiOutput = cleaned
                            sections += cleaned
                        }
                    }
                }
                RoutineStepType.READ_ALOUD_TTS -> {
                    onProgress?.invoke("Step $stepNum/$total · Preparing Read-Aloud TTS…")
                    val textToSpeak = step.payload.trim().ifBlank { lastAiOutput }
                    if (textToSpeak.isNotBlank()) {
                        spokenText = cleanWidgetAnswerText(textToSpeak)
                    }
                }
                RoutineStepType.DEVICE_ACTION -> {
                    onProgress?.invoke("Step $stepNum/$total · Preparing Device Action…")
                    val cmd = step.payload.trim()
                    if (cmd.isNotBlank()) {
                        val parsed = DeviceActionEngine.parseFromPrompt(cmd, contacts, apps)
                        if (parsed != null) {
                            pendingAction = parsed
                            sections += "**Device Action Ready:** ${parsed.title} — ${parsed.subtitle}"
                        } else {
                            sections += "**Device Action:** Could not parse \"$cmd\"."
                        }
                    }
                }
                RoutineStepType.SAVE_NOTE -> {
                    onProgress?.invoke("Step $stepNum/$total · Saving Voice Note…")
                    val noteSource = buildString {
                        val label = step.payload.trim()
                        if (label.isNotBlank()) appendLine(label)
                        if (lastAiOutput.isNotBlank()) appendLine(lastAiOutput)
                    }.trim().ifBlank { routine.name }
                    val note = saveVoiceQuickNote(noteSource, activeModel)
                    savedNote = note
                    sections += "**Saved Note:** ${note.title}"
                }
            }
        }

        val finalSummary = sections.joinToString("\n\n")
            .ifBlank { "Routine \"${routine.name}\" completed." }

        RoutineRunResult(
            routineName = routine.name,
            summaryText = finalSummary,
            spokenText = spokenText,
            pendingDeviceAction = pendingAction,
            savedNote = savedNote,
            modelLabel = modelLabel
        )
    }

    // --- Voice Quick-Notes Inbox (AI Memo Cleanup -> Structured Markdown in Room) ---

    suspend fun saveVoiceQuickNote(
        rawTranscript: String,
        preferredModel: SelectableModel? = null
    ): VoiceNoteInfo = withContext(Dispatchers.IO) {
        val cleanRaw = rawTranscript.trim()
        require(cleanRaw.isNotEmpty()) { "Note text cannot be empty." }

        val model = preferredModel ?: resolveFreshModel()
        var title = cleanRaw.lines().firstOrNull()?.take(48)?.trim().orEmpty().ifBlank { "Quick Note" }
        var markdown = buildLocalStructuredNoteFallback(cleanRaw)
        var tags = listOf("quick-note")

        if (model != null) {
            val apiKey = getProviderApiKey(model.provider.id)
            val cleanupPrompt = """
                Clean up this dictated voice memo or quick note into a crisp, structured Markdown note.
                Fix speech-to-text typos/filler words, organize key points and any action items into clean Markdown bullets/checkboxes (`- [ ]`), and keep every factual detail intact.
                Return STRICTLY valid JSON (no markdown fences outside JSON):
                {
                  "title": "Short 3-6 word title",
                  "tags": ["tag1", "tag2"],
                  "markdown": "## Summary\n...\n\n## Key Points\n- ...\n\n## Action Items\n- [ ] ..."
                }

                Raw Memo:
                ${cleanRaw.take(6000)}
            """.trimIndent()

            val aiRaw = runCatching {
                withTimeout(20_000L) {
                    collectSingleCompletion(model, apiKey, cleanupPrompt) {}
                }
            }.getOrNull()

            val jsonStr = aiRaw?.let { extractJsonObject(it) }
            if (jsonStr != null) {
                runCatching {
                    val obj = JSONObject(jsonStr)
                    val parsedTitle = obj.optString("title").trim()
                    val parsedMd = obj.optString("markdown").trim()
                    val tagsArr = obj.optJSONArray("tags")
                    val parsedTags = buildList {
                        if (tagsArr != null) {
                            for (i in 0 until tagsArr.length().coerceAtMost(5)) {
                                val t = tagsArr.optString(i).trim().lowercase().removePrefix("#")
                                if (t.isNotEmpty()) add(t)
                            }
                        }
                    }
                    if (parsedTitle.isNotBlank()) title = parsedTitle.take(64)
                    if (parsedMd.isNotBlank()) markdown = parsedMd
                    if (parsedTags.isNotEmpty()) tags = parsedTags
                }
            }
        }

        val now = System.currentTimeMillis()
        val entity = VoiceNoteEntity(
            title = title,
            rawTranscript = cleanRaw,
            markdownContent = markdown,
            tagsCsv = tags.joinToString(","),
            createdAt = now,
            updatedAt = now
        )
        val id = voiceNoteDao.insert(entity)
        entity.copy(id = id).toDomain()
    }

    private fun buildLocalStructuredNoteFallback(raw: String): String {
        val sentences = raw.split(Regex("""(?<=[.!?])\s+|\n+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return buildString {
            appendLine("## Voice Note")
            appendLine()
            if (sentences.size <= 1) {
                appendLine("- $raw")
            } else {
                sentences.forEach { appendLine("- $it") }
            }
        }.trim()
    }

    suspend fun updateVoiceNote(note: VoiceNoteInfo) = withContext(Dispatchers.IO) {
        voiceNoteDao.update(
            VoiceNoteEntity(
                id = note.id,
                title = note.title.trim().ifBlank { "Untitled Note" },
                rawTranscript = note.rawTranscript,
                markdownContent = note.markdownContent.trim(),
                tagsCsv = note.tags.joinToString(",") { it.trim().removePrefix("#") },
                createdAt = note.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteVoiceNote(id: Long) = withContext(Dispatchers.IO) {
        voiceNoteDao.deleteById(id)
    }

    // --- 1. Private Receipt, Bill & Monthly Expense Tracker ---

    val expensesFlow: Flow<List<ExpenseRecord>> = expenseDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveExpense(record: ExpenseRecord): Long = withContext(Dispatchers.IO) {
        expenseDao.insert(
            ExpenseEntity(
                id = record.id,
                merchant = record.merchant.trim().ifBlank { "Merchant" },
                dateIso = record.dateIso.trim().ifBlank {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                },
                category = record.category.trim().ifBlank { "General" },
                amount = record.amount.coerceAtLeast(0.0),
                currency = record.currency.trim().ifBlank { "INR" },
                notes = record.notes.trim(),
                createdAt = record.createdAt
            )
        )
    }

    suspend fun deleteExpense(id: Long) = withContext(Dispatchers.IO) {
        expenseDao.deleteById(id)
    }

    suspend fun extractExpenseFromImage(
        bitmap: Bitmap,
        preferredModel: SelectableModel?
    ): ExpenseRecord = withContext(Dispatchers.IO) {
        val model = preferredModel?.takeIf { it.model.supportsImageInput }
            ?: observeModelsForCapability(ModelCapability.TEXT_CHAT).first()
                .firstOrNull { it.model.supportsImageInput }
            ?: resolveFreshModel()
            ?: throw IllegalStateException("Configure a Vision-capable model in Settings to scan receipts.")
        val savedUri = saveBitmapAsAttachment(bitmap, prefix = "receipt")
        val (b64, mime) = ImageEncoder.encodeFromStoredUri(context, savedUri)
            ?: throw IllegalStateException("Could not encode receipt image.")
        val apiKey = getProviderApiKey(model.provider.id)
        val prompt = """
            Extract the expense details from this receipt, bill, or UPI payment screenshot.
            Return STRICTLY valid JSON only (no markdown fences):
            {
              "merchant": "Store or payee name",
              "dateIso": "YYYY-MM-DD",
              "category": "Food | Travel | Shopping | Bills | Study | Health | General",
              "amount": 125.50,
              "currency": "INR",
              "notes": "Short 1-line item summary"
            }
        """.trimIndent()
        val buf = StringBuilder()
        withTimeout(35_000L) {
            aiRouter.streamChat(
                provider = model.provider,
                apiKey = apiKey,
                modelId = model.model.modelId,
                messages = listOf(
                    ChatInputMessage(
                        role = "USER",
                        text = prompt,
                        imageBase64 = b64,
                        imageMimeType = mime
                    )
                ),
                options = ChatOptions(temperature = 0.1f, maxTokens = 400, jsonMode = true)
            ).collect { ev ->
                if (ev is StreamEvent.TextDelta) buf.append(ev.text)
            }
        }
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val rawJson = extractJsonObject(buf.toString())
        val obj = runCatching { JSONObject(rawJson ?: "{}") }.getOrDefault(JSONObject())
        val record = ExpenseRecord(
            merchant = obj.optString("merchant").trim().ifBlank { "Scanned Receipt" },
            dateIso = obj.optString("dateIso").trim().ifBlank { today },
            category = obj.optString("category").trim().ifBlank { "General" },
            amount = obj.optDouble("amount", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            currency = obj.optString("currency").trim().ifBlank { "INR" },
            notes = obj.optString("notes").trim()
        )
        val id = saveExpense(record)
        record.copy(id = id)
    }

    // --- 2. Web Page / Topic Change Watcher ---

    val watchlistFlow: Flow<List<WatchlistItemInfo>> = watchlistDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveWatchlistItem(title: String, queryOrUrl: String): Long = withContext(Dispatchers.IO) {
        watchlistDao.insert(
            WatchlistItemEntity(
                title = title.trim().ifBlank { queryOrUrl.take(40) },
                queryOrUrl = queryOrUrl.trim()
            )
        )
    }

    suspend fun deleteWatchlistItem(id: Long) = withContext(Dispatchers.IO) {
        watchlistDao.deleteById(id)
    }

    suspend fun checkWatchlistUpdate(
        item: WatchlistItemInfo,
        preferredModel: SelectableModel?
    ): WatchlistItemInfo = withContext(Dispatchers.IO) {
        val model = preferredModel ?: resolveFreshModel()
            ?: throw IllegalStateException("Configure an AI model in Settings to run Watchlist checks.")
        val apiKey = getProviderApiKey(model.provider.id)
        val fetchedContext = if (item.queryOrUrl.startsWith("http://", true) || item.queryOrUrl.startsWith("https://", true)) {
            runCatching { webSearchClient.fetchUrlAsMarkdown(item.queryOrUrl).markdown.take(7000) }
                .getOrElse { "Direct URL fetch unavailable: ${it.message}" }
        } else {
            val sp = apiKeyVault.searchProviderFlow.first()
            val sk = apiKeyVault.getSearchApiKey()
            if (sk.isNotBlank() && sp != WebSearchProvider.GEMINI_NATIVE) {
                runCatching {
                    webSearchClient.searchWeb(item.queryOrUrl, sp, sk, maxResults = 5)
                        .joinToString("\n") { "- ${it.title} (${it.url}): ${it.snippet}" }
                }.getOrDefault("")
            } else ""
        }

        val prompt = buildString {
            appendLine("Topic / URL being watched: ${item.title} (${item.queryOrUrl})")
            if (fetchedContext.isNotBlank()) {
                appendLine("\nLatest fetched content:\n$fetchedContext")
            }
            if (item.lastSummary.isNotBlank()) {
                appendLine("\nPrevious snapshot summary:\n${item.lastSummary}")
                appendLine("\nCompare the latest information against the previous snapshot. Highlight ONLY WHAT IS NEW in crisp Markdown bullets under `## What's New`, followed by an updated `## Current Status` summary.")
            } else {
                appendLine("\nProduce a concise baseline summary of the current status & latest updates in Markdown bullets.")
            }
        }
        val buf = StringBuilder()
        withTimeout(30_000L) {
            aiRouter.streamChat(
                provider = model.provider,
                apiKey = apiKey,
                modelId = model.model.modelId,
                messages = listOf(ChatInputMessage(role = "USER", text = prompt)),
                options = ChatOptions(
                    temperature = 0.2f,
                    maxTokens = 700,
                    enableGeminiGoogleSearch = model.provider.type == ProviderType.GEMINI
                )
            ).collect { ev ->
                if (ev is StreamEvent.TextDelta) buf.append(ev.text)
            }
        }
        val resultText = buf.toString().trim().ifBlank { "No updates returned." }
        val now = System.currentTimeMillis()
        val updated = WatchlistItemEntity(
            id = item.id,
            title = item.title,
            queryOrUrl = item.queryOrUrl,
            lastSummary = resultText.take(2000),
            lastDiffMarkdown = resultText,
            lastCheckedAt = now,
            createdAt = item.createdAt
        )
        watchlistDao.update(updated)
        updated.toDomain()
    }

    // --- 3. Smart Home, Webhook & Tasker / MacroDroid Bridge ---

    val webhooksFlow: Flow<List<WebhookActionInfo>> = webhookDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveWebhook(webhook: WebhookActionInfo): Long = withContext(Dispatchers.IO) {
        webhookDao.insert(
            WebhookActionEntity(
                id = webhook.id,
                name = webhook.name.trim(),
                triggerPhrase = webhook.triggerPhrase.trim(),
                method = webhook.method.uppercase().ifBlank { "POST" },
                url = webhook.url.trim(),
                headersJson = webhook.headersJson.trim(),
                bodyTemplate = webhook.bodyTemplate.trim(),
                createdAt = webhook.createdAt
            )
        )
    }

    suspend fun deleteWebhook(id: Long) = withContext(Dispatchers.IO) {
        webhookDao.deleteById(id)
    }

    suspend fun matchWebhookByPhrase(rawInput: String): WebhookActionInfo? = withContext(Dispatchers.IO) {
        val clean = rawInput.trim().lowercase()
        if (clean.isEmpty()) return@withContext null
        webhookDao.getAll().map { it.toDomain() }.firstOrNull { w ->
            val trig = w.triggerPhrase.trim().lowercase()
            val name = w.name.trim().lowercase()
            (trig.isNotEmpty() && (clean == trig || clean == "run $trig" || clean == "trigger $trig")) ||
                (name.isNotEmpty() && (clean == name || clean == "run $name"))
        }
    }

    suspend fun executeWebhook(webhook: WebhookActionInfo): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = webhook.url.trim()
            require(url.startsWith("http://", true) || url.startsWith("https://", true)) {
                "Webhook URL must start with http:// or https://"
            }
            val builder = okhttp3.Request.Builder().url(url)
            if (webhook.headersJson.isNotBlank()) {
                runCatching {
                    val obj = JSONObject(webhook.headersJson)
                    obj.keys().forEach { k -> builder.addHeader(k, obj.optString(k)) }
                }
            }
            val method = webhook.method.uppercase()
            if (method == "GET") {
                builder.get()
            } else {
                val media = "application/json; charset=utf-8".toMediaType()
                val body = webhook.bodyTemplate.ifBlank { "{}" }.toRequestBody(media)
                builder.method(method, body)
            }
            okHttpClient.newCall(builder.build()).execute().use { resp ->
                val snippet = resp.body?.string().orEmpty().take(180).trim()
                val statusLabel = "${resp.code} ${resp.message.ifBlank { if (resp.isSuccessful) "OK" else "ERR" }}"
                if (snippet.isNotBlank()) "$statusLabel · $snippet" else statusLabel
            }
        }
    }

    // --- 4. Interactive Canvas Version History & Highlight-to-Edit ---

    val canvasVersionsFlow: Flow<List<CanvasVersionInfo>> = canvasVersionDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveCanvasVersion(docTitle: String, content: String): CanvasVersionInfo = withContext(Dispatchers.IO) {
        val existing = canvasVersionDao.getAll()
        val nextVer = (existing.maxOfOrNull { it.versionNumber } ?: 0) + 1
        val entity = CanvasVersionEntity(
            docTitle = docTitle.trim().ifBlank { "Untitled Canvas" },
            content = content,
            versionNumber = nextVer
        )
        val id = canvasVersionDao.insert(entity)
        entity.copy(id = id).toDomain()
    }

    suspend fun aiEditCanvasSelection(
        fullDocument: String,
        selectedBlock: String,
        instruction: String,
        preferredModel: SelectableModel?
    ): String = withContext(Dispatchers.IO) {
        val model = preferredModel ?: resolveFreshModel()
            ?: throw IllegalStateException("Configure an AI model in Settings to use Highlight-to-Edit.")
        val apiKey = getProviderApiKey(model.provider.id)
        val prompt = """
            You are editing ONLY a highlighted block inside a larger document.
            Instruction: $instruction

            Full document context (for reference only — DO NOT rewrite unrelated parts):
            ${fullDocument.take(4000)}

            Highlighted block to transform:
            ```
            $selectedBlock
            ```

            Return ONLY the replacement text for the highlighted block (no commentary or surrounding fences unless the block itself is code).
        """.trimIndent()
        val out = withTimeout(25_000L) {
            collectSingleCompletion(model, apiKey, prompt) {}
        }.trim()
        out.ifBlank { selectedBlock }
    }

    // --- 5. Smart Clipboard History Vault ---

    val clipboardFlow: Flow<List<ClipboardSnippetInfo>> = clipboardDao.observeRecent().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun recordClipboardSnippet(rawText: String) = withContext(Dispatchers.IO) {
        val clean = rawText.trim()
        if (clean.length < 3) return@withContext
        val recent = clipboardDao.getRecent()
        if (recent.firstOrNull()?.content == clean) return@withContext
        val preview = clean.replace(Regex("""\s+"""), " ").take(110)
        val isUrl = clean.startsWith("http://", true) || clean.startsWith("https://", true)
        clipboardDao.insert(
            ClipboardItemEntity(
                content = clean.take(8000),
                preview = preview,
                isUrl = isUrl
            )
        )
    }

    suspend fun deleteClipboardSnippet(id: Long) = withContext(Dispatchers.IO) {
        clipboardDao.deleteById(id)
    }

    suspend fun clearClipboardVault() = withContext(Dispatchers.IO) {
        clipboardDao.deleteAll()
    }

    // --- 6. Global Spotlight Search, Topic Map & Auto-Delete Junk Chats ---

    suspend fun spotlightSearch(query: String, filter: String = "ALL"): List<SpotlightResult> =
        withContext(Dispatchers.IO) {
            val q = query.trim().lowercase()
            val results = mutableListOf<SpotlightResult>()
            val weekCutoff = System.currentTimeMillis() - 7L * 24L * 3600_000L
            val convMap = conversationDao.getAll().associateBy { it.id }

            if (filter in setOf("ALL", "CODE", "IMAGES", "WEEK")) {
                messageDao.getAll().asReversed().forEach { msg ->
                    val hasCode = msg.content.contains("```")
                    val hasImg = !msg.attachmentUri.isNullOrBlank()
                    val matchesFilter = when (filter) {
                        "CODE" -> hasCode
                        "IMAGES" -> hasImg
                        "WEEK" -> msg.createdAt >= weekCutoff
                        else -> true
                    }
                    if (matchesFilter && (q.isEmpty() || msg.content.lowercase().contains(q))) {
                        val convTitle = convMap[msg.conversationId]?.title ?: "Chat"
                        val idx = if (q.isEmpty()) 0 else msg.content.lowercase().indexOf(q).coerceAtLeast(0)
                        val start = (idx - 40).coerceAtLeast(0)
                        val snippet = msg.content.substring(start, (start + 140).coerceAtMost(msg.content.length))
                            .replace("\n", " ")
                        results += SpotlightResult(
                            kind = "MESSAGE",
                            title = "$convTitle (${msg.role})",
                            snippet = snippet,
                            timestamp = msg.createdAt,
                            conversationId = msg.conversationId,
                            messageId = msg.id,
                            hasCode = hasCode,
                            hasImage = hasImg
                        )
                    }
                }
            }

            if (filter == "ALL" || filter == "NOTES") {
                voiceNoteDao.getAll().forEach { note ->
                    val hay = "${note.title} ${note.markdownContent} ${note.tagsCsv}".lowercase()
                    if (q.isEmpty() || hay.contains(q)) {
                        results += SpotlightResult(
                            kind = "NOTE",
                            title = note.title,
                            snippet = note.markdownContent.replace("\n", " ").take(140),
                            timestamp = note.updatedAt
                        )
                    }
                }
            }

            if (filter == "ALL" || filter == "EXPENSES") {
                expenseDao.getAll().forEach { exp ->
                    val hay = "${exp.merchant} ${exp.category} ${exp.notes} ${exp.amount}".lowercase()
                    if (q.isEmpty() || hay.contains(q)) {
                        results += SpotlightResult(
                            kind = "EXPENSE",
                            title = "${exp.merchant} · ${exp.currency} ${exp.amount}",
                            snippet = "${exp.dateIso} · ${exp.category} ${exp.notes}".trim(),
                            timestamp = exp.createdAt
                        )
                    }
                }
            }

            if (filter == "ALL") {
                knowledgeDocDao.getAll().forEach { doc ->
                    val hay = "${doc.title} ${doc.slug} ${doc.content}".lowercase()
                    if (q.isEmpty() || hay.contains(q)) {
                        results += SpotlightResult(
                            kind = "DOC",
                            title = "#${doc.slug} · ${doc.title}",
                            snippet = doc.content.replace("\n", " ").take(140),
                            timestamp = doc.createdAt
                        )
                    }
                }
            }

            results.sortedByDescending { it.timestamp }.take(60)
        }

    suspend fun clusterConversationsByTopic(): List<TopicCluster> = withContext(Dispatchers.IO) {
        val convs = conversationDao.getAll()
        val buckets = linkedMapOf<String, MutableList<ConversationEntity>>(
            "Coding & Dev" to mutableListOf(),
            "Hardware & Electronics" to mutableListOf(),
            "Study & Math" to mutableListOf(),
            "Finance & Bills" to mutableListOf(),
            "Daily Tasks & Actions" to mutableListOf(),
            "General & Ideas" to mutableListOf()
        )
        for (c in convs) {
            val t = c.title.lowercase()
            val key = when {
                Regex("""\b(code|kotlin|python|react|js|ts|bug|error|api|git|sql|css|html|cors|docker|linux)\b""").containsMatchIn(t) -> "Coding & Dev"
                Regex("""\b(arduino|esp32|circuit|ble|sensor|voltage|resistor|pcb|motor|led|hardware|gpio)\b""").containsMatchIn(t) -> "Hardware & Electronics"
                Regex("""\b(exam|math|calculus|physics|pdf|lecture|study|formula|solve|quiz|notes|essay)\b""").containsMatchIn(t) -> "Study & Math"
                Regex("""\b(upi|expense|bill|receipt|budget|price|tax|salary|cost|invest)\b""").containsMatchIn(t) -> "Finance & Bills"
                Regex("""\b(call|whatsapp|timer|alarm|map|calendar|email|sms|routine|remind|schedule)\b""").containsMatchIn(t) -> "Daily Tasks & Actions"
                else -> "General & Ideas"
            }
            buckets[key]?.add(c)
        }
        buckets.entries
            .filter { it.value.isNotEmpty() }
            .map { (topic, list) ->
                TopicCluster(
                    topic = topic,
                    conversationIds = list.map { it.id },
                    sampleTitles = list.take(4).map { it.title }
                )
            }
    }

    suspend fun pruneOldJunkChatsIfEnabled(): Int = withContext(Dispatchers.IO) {
        if (!autoDeleteJunkChatsFlow.first()) return@withContext 0
        pruneOldJunkChatsNow()
    }

    suspend fun pruneOldJunkChatsNow(): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 7L * 24L * 3600_000L
        val allConvs = conversationDao.getAll()
        var deleted = 0
        for (c in allConvs) {
            if (c.updatedAt < cutoff) {
                val msgs = messageDao.getMessages(c.id)
                val userCount = msgs.count { it.role == "USER" }
                if (userCount <= 1) {
                    conversationDao.deleteById(c.id)
                    deleted++
                }
            }
        }
        deleted
    }

    // --- 7. Inline Fact-Check Highlighter, Decision Matrix, SVG Vectorizer & Live Interpreter ---

    suspend fun verifyAnswerClaims(
        answerText: String,
        preferredModel: SelectableModel?
    ): List<FactCheckClaim> = withContext(Dispatchers.IO) {
        val model = preferredModel ?: resolveFreshModel()
            ?: throw IllegalStateException("Configure an AI model in Settings to verify claims.")
        val apiKey = getProviderApiKey(model.provider.id)
        val prompt = """
            Extract up to 5 key verifiable factual claims (dates, numbers, specs, names, or technical assertions) from this AI answer and verify each one carefully.
            Return STRICTLY valid JSON:
            {
              "claims": [
                { "statement": "short claim text", "status": "VERIFIED | UNCERTAIN | CONFLICT", "note": "concise verification note", "sourceUrl": "" }
              ]
            }

            Answer to verify:
            ${answerText.take(3500)}
        """.trimIndent()
        val buf = StringBuilder()
        withTimeout(25_000L) {
            aiRouter.streamChat(
                provider = model.provider,
                apiKey = apiKey,
                modelId = model.model.modelId,
                messages = listOf(ChatInputMessage(role = "USER", text = prompt)),
                options = ChatOptions(
                    temperature = 0.1f,
                    maxTokens = 700,
                    jsonMode = true,
                    enableGeminiGoogleSearch = model.provider.type == ProviderType.GEMINI
                )
            ).collect { ev ->
                if (ev is StreamEvent.TextDelta) buf.append(ev.text)
            }
        }
        val rawJson = extractJsonObject(buf.toString()) ?: return@withContext emptyList()
        runCatching {
            val arr = JSONObject(rawJson).optJSONArray("claims") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        FactCheckClaim(
                            statement = o.optString("statement").trim(),
                            status = o.optString("status", "UNCERTAIN").uppercase(),
                            note = o.optString("note").trim(),
                            sourceUrl = o.optString("sourceUrl").trim().takeIf { it.startsWith("http") }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun generateDecisionMatrix(
        comparisonPrompt: String,
        preferredModel: SelectableModel?
    ): DecisionMatrixData = withContext(Dispatchers.IO) {
        val model = preferredModel ?: resolveFreshModel()
            ?: throw IllegalStateException("Configure an AI model in Settings to build a Decision Matrix.")
        val apiKey = getProviderApiKey(model.provider.id)
        val prompt = """
            Create a structured side-by-side decision comparison matrix for:
            "$comparisonPrompt"
            Return STRICTLY valid JSON:
            {
              "title": "Short comparison title",
              "optionA": "Option A name",
              "optionB": "Option B name",
              "criteria": [
                { "name": "Price & Value", "weight": 8, "scoreA": 8.5, "scoreB": 7.0, "noteA": "short pro/con", "noteB": "short pro/con" }
              ]
            }
            Include 5 crisp, relevant criteria (weights 1-10, scores 1-10).
        """.trimIndent()
        val raw = withTimeout(25_000L) {
            collectSingleCompletion(model, apiKey, prompt) {}
        }
        val json = extractJsonObject(raw) ?: error("Could not parse comparison JSON.")
        val o = JSONObject(json)
        val arr = o.optJSONArray("criteria") ?: JSONArray()
        val criteria = buildList {
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                add(
                    DecisionCriterion(
                        name = c.optString("name", "Criterion ${i + 1}"),
                        weight = c.optDouble("weight", 5.0).toFloat().coerceIn(1f, 10f),
                        scoreA = c.optDouble("scoreA", 7.0).toFloat().coerceIn(0f, 10f),
                        scoreB = c.optDouble("scoreB", 7.0).toFloat().coerceIn(0f, 10f),
                        noteA = c.optString("noteA"),
                        noteB = c.optString("noteB")
                    )
                )
            }
        }
        DecisionMatrixData(
            title = o.optString("title", comparisonPrompt.take(40)),
            optionA = o.optString("optionA", "Option A"),
            optionB = o.optString("optionB", "Option B"),
            criteria = criteria.ifEmpty {
                listOf(DecisionCriterion("Overall Fit", 8f, 7.5f, 7.5f))
            }
        )
    }

    suspend fun generateCleanSvg(
        description: String,
        preferredModel: SelectableModel?
    ): String = withContext(Dispatchers.IO) {
        val model = preferredModel ?: resolveFreshModel()
            ?: throw IllegalStateException("Configure an AI model in Settings to vectorize SVG.")
        val apiKey = getProviderApiKey(model.provider.id)
        val prompt = """
            Generate a clean, crisp, minimal, scalable <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256"> icon/logo for:
            "$description"
            Use bold geometric shapes, clean paths, and balanced Swiss design.
            Return ONLY raw <svg ...>...</svg> markup with no prose or markdown fences.
        """.trimIndent()
        val raw = withTimeout(25_000L) {
            collectSingleCompletion(model, apiKey, prompt) {}
        }
        val start = raw.indexOf("<svg", ignoreCase = true)
        val end = raw.lastIndexOf("</svg>", ignoreCase = true)
        if (start >= 0 && end > start) raw.substring(start, end + 6) else raw.trim()
    }

    suspend fun translateLiveUtterance(
        text: String,
        sourceLang: String,
        targetLang: String,
        preferredModel: SelectableModel?
    ): String = withContext(Dispatchers.IO) {
        val model = preferredModel ?: resolveFreshModel()
            ?: throw IllegalStateException("Configure an AI model in Settings for Live Interpreter.")
        val apiKey = getProviderApiKey(model.provider.id)
        val prompt = """
            Translate the following spoken sentence naturally from $sourceLang into $targetLang.
            Return ONLY the translated spoken phrase with no quotes, labels, or explanation.

            Sentence: $text
        """.trimIndent()
        withTimeout(18_000L) {
            collectSingleCompletion(model, apiKey, prompt) {}
        }.trim()
    }

    suspend fun verifyAndFixResponse(
        originalPrompt: String,
        draftAnswer: String,
        preferredModel: SelectableModel?
    ): String = withContext(Dispatchers.IO) {
        val model = preferredModel ?: resolveFreshModel() ?: return@withContext draftAnswer
        val apiKey = getProviderApiKey(model.provider.id)
        val prompt = """
            Perform a strict self-correction pass on the draft answer below for coding/math accuracy.
            Trace edge cases, off-by-one bugs, syntax errors, and arithmetic mistakes.
            If there are any bugs, output the fully corrected clean final answer and add a brief `### Self-Check Verified` note at the bottom listing what was checked/fixed.
            If the draft is already 100% correct, output the clean final answer with `### Self-Check Verified: Passed all edge cases.` at the bottom.

            Original Prompt:
            ${originalPrompt.take(1500)}

            Draft Answer:
            ${draftAnswer.take(4000)}
        """.trimIndent()
        runCatching {
            withTimeout(28_000L) {
                collectSingleCompletion(model, apiKey, prompt) {}
            }.trim().ifBlank { draftAnswer }
        }.getOrDefault(draftAnswer)
    }

    suspend fun updateMessageContent(messageId: Long, newContent: String) = withContext(Dispatchers.IO) {
        messageDao.updateStreamingMessage(
            id = messageId,
            content = newContent,
            promptTokens = null,
            completionTokens = null,
            isError = false
        )
    }

    // --- Wave 4: Prompt Doctor, Dynamic Mind Map Expansion, Voice Form Filler, CLI Builder,
    //             TL;DR Thread Checkpoints, 80% Budget Auto-Downgrade, Assistant Forking & Ghost Mode ---

    /**
     * Resolves an auto-downgraded flash/free model when daily spend >= 80% of [DailyBudgetConfig.dailyCapUsd]
     * and auto-downgrade is enabled (unless the user explicitly enabled manual flagship override).
     */
    suspend fun resolveBudgetDowngradeModel(
        requestedModel: SelectableModel,
        availableModels: List<SelectableModel>,
        forceFlagshipOverride: Boolean = false
    ): Pair<SelectableModel, String?> = withContext(Dispatchers.IO) {
        if (forceFlagshipOverride) return@withContext requestedModel to null
        val budget = dailyBudgetConfigFlow.first()
        if (budget.dailyCapUsd <= 0.0 || !budget.autoDowngradeEnabled) {
            return@withContext requestedModel to null
        }
        val spent = getTodaySpendUsd()
        val ratio = spent / budget.dailyCapUsd
        if (ratio < 0.80) return@withContext requestedModel to null

        val targetDowngrade = budget.downgradeModelDbId?.let { id ->
            availableModels.firstOrNull { it.model.id == id }
        } ?: availableModels.firstOrNull { cand ->
            val idLow = cand.model.modelId.lowercase()
            val provLow = cand.provider.name.lowercase()
            cand.model.id != requestedModel.model.id && (
                idLow.contains("flash") ||
                    idLow.contains("mini") ||
                    idLow.contains("haiku") ||
                    idLow.contains("8b") ||
                    idLow.contains("free") ||
                    provLow.contains("ollama") ||
                    provLow.contains("groq")
                )
        }
        if (targetDowngrade != null && targetDowngrade.model.id != requestedModel.model.id) {
            val pct = (ratio * 100).toInt()
            targetDowngrade to "BUDGET $pct% // Auto-downgraded to ${targetDowngrade.model.displayName} (tap Override to force flagship)."
        } else {
            requestedModel to null
        }
    }

    /**
     * Picks the lightest/fastest configured model for instant Prompt Doctor rewriting,
     * falling back to [preferredModel].
     */
    private suspend fun pickLightweightHelperModel(preferredModel: SelectableModel?): SelectableModel? {
        val available = observeModelsForCapability(ModelCapability.TEXT_CHAT).first()
        val fast = available.firstOrNull { cand ->
            val low = cand.model.modelId.lowercase()
            low.contains("flash") || low.contains("mini") || low.contains("haiku") || low.contains("8b") || low.contains("nano")
        }
        return fast ?: preferredModel ?: resolveFreshModel()
    }

    suspend fun enhanceUserPrompt(
        roughPrompt: String,
        preferredModel: SelectableModel?
    ): String = withContext(Dispatchers.IO) {
        val clean = roughPrompt.trim()
        if (clean.isEmpty()) return@withContext ""
        val model = pickLightweightHelperModel(preferredModel)
            ?: throw IllegalStateException("Configure an AI provider in Settings to use Prompt Doctor.")
        val apiKey = getProviderApiKey(model.provider.id)
        val prompt = """
            You are Prompt Doctor — an expert prompt engineer optimizing rough mobile prompts before they are sent to an AI.
            Rewrite the rough prompt below into a crisp, clear, well-structured prompt that covers:
            - Exact goal & context
            - Key technical/design requirements (e.g. accessibility, validation, responsive layout, edge cases, or constraints when relevant)
            - Clear expected output format

            Rules:
            - Keep it concise (4-8 focused lines max), ready to drop directly into a chat input box.
            - Preserve the user's exact intent without inventing unrelated topics.
            - Return ONLY the improved prompt text with NO preamble, quotes, or meta-commentary.

            Rough Prompt:
            $clean
        """.trimIndent()
        withTimeout(18_000L) {
            collectSingleCompletion(model, apiKey, prompt) {}
        }.trim().ifBlank { clean }
    }

    suspend fun expandMindMapSubTopic(
        rootNode: MindMapNode,
        targetNode: MindMapNode,
        preferredModel: SelectableModel?
    ): MindMapNode = withContext(Dispatchers.IO) {
        val model = preferredModel ?: resolveFreshModel()
            ?: throw IllegalStateException("Configure an AI model in Settings to expand Mind Map branches.")
        val apiKey = getProviderApiKey(model.provider.id)
        val existingLabels = targetNode.children.map { it.label }.joinToString(", ").ifBlank { "none" }
        val prompt = """
            Expand the Mind Map sub-topic "${targetNode.label}" (part of root topic "${rootNode.label}").
            Existing direct children to avoid duplicating: $existingLabels.
            Generate 3 to 4 crisp, insightful next-level child sub-topics.
            Return STRICTLY valid JSON only:
            {
              "children": [
                {"label": "Short 2-4 word title", "detail": "One crisp explanatory sentence."}
              ]
            }
        """.trimIndent()
        val raw = withTimeout(22_000L) {
            collectSingleCompletion(model, apiKey, prompt) {}
        }
        val jsonStr = extractJsonObject(raw) ?: "{}"
        val newChildren = mutableListOf<MindMapNode>()
        runCatching {
            val arr = JSONObject(jsonStr).optJSONArray("children")
            if (arr != null) {
                for (i in 0 until arr.length().coerceAtMost(5)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val lbl = o.optString("label").trim()
                    if (lbl.isNotEmpty()) {
                        newChildren += MindMapNode(
                            id = "${targetNode.id}_exp_${System.currentTimeMillis()}_$i",
                            label = lbl.take(36),
                            detail = o.optString("detail").trim()
                        )
                    }
                }
            }
        }
        if (newChildren.isEmpty()) {
            throw IllegalStateException("Could not parse sub-topic expansion from model output.")
        }
        fun graft(node: MindMapNode): MindMapNode {
            if (node.id == targetNode.id) {
                return node.copy(children = node.children + newChildren)
            }
            return node.copy(children = node.children.map { graft(it) })
        }
        graft(rootNode)
    }

    suspend fun fillStructuredFormFromVoice(
        template: VoiceFormTemplate,
        dictatedText: String,
        preferredModel: SelectableModel?
    ): FilledFormResult = withContext(Dispatchers.IO) {
        val clean = dictatedText.trim()
        require(clean.isNotEmpty()) { "Speak or enter a note first." }
        val model = pickLightweightHelperModel(preferredModel)
            ?: throw IllegalStateException("Configure an AI model in Settings to fill forms.")
        val apiKey = getProviderApiKey(model.provider.id)
        val fieldsList = template.fields.joinToString("\n") { "- $it" }
        val prompt = """
            Map the user's natural spoken dictation into the exact fields of the "${template.name}" structured template.
            Fill every field cleanly (infer today's date if a Date field is present and unmentioned; use "—" only if genuinely not mentioned).
            Return STRICTLY valid JSON only where keys match the exact field names below:
            $fieldsList

            User Dictation:
            "$clean"
        """.trimIndent()
        val raw = withTimeout(22_000L) {
            collectSingleCompletion(model, apiKey, prompt) {}
        }
        val objStr = extractJsonObject(raw) ?: "{}"
        val values = LinkedHashMap<String, String>()
        runCatching {
            val obj = JSONObject(objStr)
            template.fields.forEach { field ->
                val v = obj.optString(field, "").trim()
                    .ifBlank {
                        // Case-insensitive key lookup fallback
                        var found = ""
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (k.equals(field, ignoreCase = true)) {
                                found = obj.optString(k, "").trim()
                                break
                            }
                        }
                        found.ifBlank { "—" }
                    }
                values[field] = v
            }
        }.onFailure {
            template.fields.forEach { values[it] = if (values.isEmpty()) clean else "—" }
        }
        val mdTable = buildString {
            appendLine("### ${template.name}")
            appendLine("| Field | Value |")
            appendLine("|---|---|")
            values.forEach { (k, v) ->
                appendLine("| **${k.replace("|", "/")}** | ${v.replace("|", "/").replace("\n", " ")} |")
            }
        }.trim()
        FilledFormResult(templateName = template.name, values = values, markdownTable = mdTable)
    }

    suspend fun buildModularCliCommand(
        request: String,
        preferredModel: SelectableModel?
    ): CliCommandSpec = withContext(Dispatchers.IO) {
        val clean = request.trim()
        val model = preferredModel ?: resolveFreshModel()
        if (model == null) {
            return@withContext fallbackCliCommandSpec(clean)
        }
        val apiKey = getProviderApiKey(model.provider.id)
        val prompt = """
            Build a modular, production-safe CLI command (Linux Bash, Windows PowerShell, Git, FFmpeg, or ADB) for:
            "$clean"

            Break the command into its base executable and an ordered list of flag/argument tokens so every flag has a crisp explanation and every filename/path/package/value is editable inline.
            Return STRICTLY valid JSON only:
            {
              "title": "Short title",
              "shell": "FFmpeg|ADB|Git|Bash|PowerShell",
              "baseCommand": "ffmpeg",
              "summary": "One sentence explaining what this command accomplishes",
              "tokens": [
                {
                  "flag": "-i",
                  "explanation": "Input source media file path",
                  "defaultValue": "input.mp4",
                  "isEditableArg": true,
                  "argLabel": "Input File"
                },
                {
                  "flag": "-crf",
                  "explanation": "Constant Rate Factor (18=high quality, 23=balanced, 28=smaller size)",
                  "defaultValue": "23",
                  "isEditableArg": true,
                  "argLabel": "Quality CRF"
                }
              ]
            }
        """.trimIndent()
        val raw = runCatching {
            withTimeout(22_000L) {
                collectSingleCompletion(model, apiKey, prompt) {}
            }
        }.getOrNull() ?: return@withContext fallbackCliCommandSpec(clean)

        val objStr = extractJsonObject(raw) ?: return@withContext fallbackCliCommandSpec(clean)
        runCatching {
            val o = JSONObject(objStr)
            val base = o.optString("baseCommand").trim()
            val arr = o.optJSONArray("tokens")
            val tokens = mutableListOf<CliFlagToken>()
            if (arr != null) {
                for (i in 0 until arr.length().coerceAtMost(14)) {
                    val t = arr.optJSONObject(i) ?: continue
                    tokens += CliFlagToken(
                        flag = t.optString("flag").trim(),
                        explanation = t.optString("explanation").trim().ifBlank { "Command parameter" },
                        defaultValue = t.optString("defaultValue").trim(),
                        isEditableArg = t.optBoolean("isEditableArg", t.optString("defaultValue").isNotBlank()),
                        argLabel = t.optString("argLabel").trim()
                    )
                }
            }
            if (base.isBlank() || tokens.isEmpty()) return@runCatching fallbackCliCommandSpec(clean)
            CliCommandSpec(
                title = o.optString("title", clean.take(40)).trim(),
                shell = o.optString("shell", "Bash").trim(),
                baseCommand = base,
                summary = o.optString("summary", "Modular CLI command ready to customize and copy.").trim(),
                tokens = tokens
            )
        }.getOrDefault(fallbackCliCommandSpec(clean))
    }

    private fun fallbackCliCommandSpec(request: String): CliCommandSpec {
        val low = request.lowercase()
        return when {
            low.contains("adb") || low.contains("bloat") -> CliCommandSpec(
                title = "Uninstall App for Current User (ADB)",
                shell = "ADB",
                baseCommand = "adb",
                summary = "Removes a preinstalled package for user 0 without requiring root access.",
                tokens = listOf(
                    CliFlagToken("shell pm uninstall", "Runs Package Manager uninstall inside the Android shell"),
                    CliFlagToken("-k", "Keep app data/cache directories (remove flag to wipe cleanly)"),
                    CliFlagToken("--user", "Target Android user profile ID (0 = primary owner)", "0", true, "User ID"),
                    CliFlagToken("", "Android application package name (e.g. com.vendor.bloatware)", "com.example.bloatware", true, "Package Name")
                )
            )
            low.contains("git") -> CliCommandSpec(
                title = "Interactive Squash Last N Commits",
                shell = "Git",
                baseCommand = "git",
                summary = "Opens interactive rebase to combine or reword recent local commits.",
                tokens = listOf(
                    CliFlagToken("rebase", "Reapplies commits on top of another base tip"),
                    CliFlagToken("-i", "Interactive mode: pick, squash, fixup, or reword each commit"),
                    CliFlagToken("", "Number of commits back from HEAD", "HEAD~5", true, "Commit Range")
                )
            )
            low.contains("powershell") || low.contains("windows") -> CliCommandSpec(
                title = "Find Largest Files in Folder",
                shell = "PowerShell",
                baseCommand = "Get-ChildItem",
                summary = "Recursively scans a directory and lists the largest files first.",
                tokens = listOf(
                    CliFlagToken("-Path", "Root directory to search", "C:\\Users\\Public", true, "Folder Path"),
                    CliFlagToken("-Recurse -File", "Include all subfolders and skip directory entries"),
                    CliFlagToken("| Sort-Object Length -Descending | Select-Object -First", "Sort largest-first and cap row count", "15", true, "Top N Files")
                )
            )
            else -> CliCommandSpec(
                title = "Compress Video to 720p H.264",
                shell = "FFmpeg",
                baseCommand = "ffmpeg",
                summary = "Re-encodes video to crisp 720p MP4 with balanced CRF quality and fast web streaming.",
                tokens = listOf(
                    CliFlagToken("-i", "Input source video file path", "input.mp4", true, "Input File"),
                    CliFlagToken("-vf", "Video filter: scales height to 720px while preserving aspect ratio", "scale=-2:720", true, "Scale Filter"),
                    CliFlagToken("-c:v", "Video codec (libx264 = universal H.264 compatibility)", "libx264", false),
                    CliFlagToken("-crf", "Constant Rate Factor (18=near lossless, 23=default, 28=small)", "23", true, "Quality CRF"),
                    CliFlagToken("-preset", "Encoding speed vs compression ratio (fast, medium, slow)", "medium", true, "Preset"),
                    CliFlagToken("-c:a aac -b:a", "Encode audio as AAC at target bitrate", "128k", true, "Audio Bitrate"),
                    CliFlagToken("", "Output compressed MP4 path", "output_720p.mp4", true, "Output File")
                )
            )
        }
    }

    suspend fun createThreadCheckpoint(
        conversationId: Long,
        afterMessage: MessageEntity,
        messagesUpToHere: List<MessageEntity>,
        preferredModel: SelectableModel?
    ): ThreadCheckpoint = withContext(Dispatchers.IO) {
        val model = pickLightweightHelperModel(preferredModel)
            ?: throw IllegalStateException("Configure an AI model in Settings to create a TL;DR checkpoint.")
        val apiKey = getProviderApiKey(model.provider.id)
        val transcript = messagesUpToHere.takeLast(40).joinToString("\n\n") { m ->
            "${m.role}: ${m.content.take(800)}"
        }
        val prompt = """
            Create a compact "TL;DR Thread Checkpoint" summarizing the conversation up to this point.
            Format as 4-6 crisp Markdown bullet points covering:
            - Core goal & decisions made so far
            - Important code symbols, configs, numbers, or constraints
            - Open questions / next steps

            Keep it under 140 words so it acts as a fast context anchor.

            Transcript (${messagesUpToHere.size} messages):
            $transcript
        """.trimIndent()
        val summary = withTimeout(22_000L) {
            collectSingleCompletion(model, apiKey, prompt) {}
        }.trim().ifBlank { "• Summarized ${messagesUpToHere.size} earlier messages up to this checkpoint." }

        val cp = ThreadCheckpoint(
            id = java.util.UUID.randomUUID().toString(),
            conversationId = conversationId,
            afterMessageId = afterMessage.id,
            messageCountCovered = messagesUpToHere.size,
            summaryMarkdown = summary
        )
        val currentMap = threadCheckpointsFlow.first().toMutableMap()
        val existingList = currentMap[conversationId].orEmpty()
            .filterNot { it.afterMessageId == afterMessage.id }
        currentMap[conversationId] = existingList + cp
        apiKeyVault.saveThreadCheckpoints(currentMap)
        cp
    }

    suspend fun deleteThreadCheckpoint(conversationId: Long, checkpointId: String) = withContext(Dispatchers.IO) {
        val currentMap = threadCheckpointsFlow.first().toMutableMap()
        val updated = currentMap[conversationId].orEmpty().filterNot { it.id == checkpointId }
        if (updated.isEmpty()) currentMap.remove(conversationId) else currentMap[conversationId] = updated
        apiKeyVault.saveThreadCheckpoints(currentMap)
    }

    /**
     * Forks an existing Assistant message into a parallel sibling branch sharing the exact same
     * [MessageEntity.parentMessageId], preserving the original response for `< 1 / N >` switching.
     */
    suspend fun editAndForkAssistantMessage(
        conversationId: Long,
        originalMessage: MessageEntity,
        newContent: String
    ): Long = withContext(Dispatchers.IO) {
        val clean = newContent.trim()
        val forkedId = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                parentMessageId = originalMessage.parentMessageId,
                role = "ASSISTANT",
                content = clean,
                modelLabel = originalMessage.modelLabel ?: "ASSISTANT (EDITED)"
            )
        )
        conversationDao.touch(conversationId)
        forkedId
    }

    /**
     * Pure volatile Incognito Ghost Chat streaming turn:
     * NEVER writes to Room DB, search index, or memory bank.
     */
    suspend fun streamGhostChatTurn(
        history: List<Pair<String, String>>,
        userText: String,
        model: SelectableModel,
        options: ChatOptions,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val freshModel = resolveFreshModel(model.model.id) ?: model
        val apiKey = getProviderApiKey(freshModel.provider.id)
        val inputMsgs = buildList {
            history.takeLast(16).forEach { (role, content) ->
                add(ChatInputMessage(role = role, text = content))
            }
            add(ChatInputMessage(role = "user", text = userText))
        }
        val sb = StringBuilder()
        withTimeout(30_000L) {
            aiRouter.streamChat(
                provider = freshModel.provider,
                apiKey = apiKey,
                modelId = freshModel.model.modelId,
                messages = inputMsgs,
                options = options
            ).collect { event ->
                if (event is StreamEvent.TextDelta && event.text.isNotEmpty()) {
                    sb.append(event.text)
                    onDelta(sb.toString())
                }
            }
        }
        sb.toString().trim()
    }
}
