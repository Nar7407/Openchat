package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.ApiKeyVault
import com.example.data.local.ConversationDao
import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationImporter
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
import com.example.data.local.UserMemoryDao
import com.example.data.local.UserMemoryEntity
import com.example.data.local.VaultBackupManager
import com.example.data.local.VaultSnapshot
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
import com.example.domain.model.MindMapNode
import com.example.domain.model.ModelCapability
import com.example.domain.model.ModelInfo
import com.example.domain.model.PersonaInfo
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType
import com.example.domain.model.QuickContact
import com.example.domain.model.QuizQuestion
import com.example.domain.model.RewritePreset
import com.example.domain.model.SearchCitation
import com.example.domain.model.SelectableModel
import com.example.domain.model.StudyCard
import com.example.domain.model.StudyPack
import com.example.domain.model.SummaryLength
import com.example.domain.model.UserMemoryInfo
import com.example.domain.model.WebSearchProvider
import com.example.util.DeviceAction
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import com.example.domain.model.YouTubeStudyResult
import com.example.util.DocumentExtractor
import com.example.util.ImageEncoder
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
    val memoriesFlow: Flow<List<UserMemoryInfo>> = userMemoryDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun setActiveConversationId(conversationId: Long?) =
        apiKeyVault.setActiveConversationId(conversationId)

    suspend fun setActiveModelDbId(modelDbId: Long?) =
        apiKeyVault.setActiveModelDbId(modelDbId)

    suspend fun saveQuickContact(contact: QuickContact) =
        apiKeyVault.saveQuickContact(contact)

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

    suspend fun addMemory(fact: String, category: String = "Preference", source: String = "Manual"): Long {
        val clean = fact.trim()
        if (clean.isEmpty()) return -1L
        val existing = userMemoryDao.getAll()
        if (existing.any { it.fact.equals(clean, ignoreCase = true) }) return -1L
        return userMemoryDao.insert(
            UserMemoryEntity(
                fact = clean,
                category = category.trim().ifBlank { "Preference" },
                isEnabled = true,
                source = source
            )
        )
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
    }

    suspend fun toggleMemory(id: Long, enabled: Boolean) {
        userMemoryDao.setEnabled(id, enabled)
    }

    suspend fun deleteMemory(id: Long) {
        userMemoryDao.deleteById(id)
    }

    suspend fun extractMemoriesFromTurn(
        userMessage: String,
        assistantMessage: String,
        selectableModel: SelectableModel
    ): Int = withContext(Dispatchers.IO) {
        if (userMessage.length < 18) return@withContext 0
        val existing = userMemoryDao.getAll().map { it.fact }
        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        val prompt = """
            Extract 0 to 2 durable, reusable user facts or preferences from this exchange (e.g. programming languages, tech stack, role, communication style, location/timezone, ongoing projects).
            Do NOT extract ephemeral one-off questions. Do NOT duplicate existing memories: ${existing.joinToString("; ")}.
            Return strictly a JSON array of objects: [{"fact": "...", "category": "Preference|Tech|Profile|Workflow"}]. If nothing durable should be saved, return [].

            User: ${userMessage.take(1200)}
            Assistant: ${assistantMessage.take(800)}
        """.trimIndent()
        val raw = runCatching {
            collectSingleCompletion(selectableModel, apiKey, prompt) {}
        }.getOrDefault("")
        val cleaned = extractJsonArray(raw) ?: return@withContext 0
        var added = 0
        runCatching {
            val arr = JSONArray(cleaned)
            for (i in 0 until arr.length().coerceAtMost(2)) {
                val obj = arr.optJSONObject(i) ?: continue
                val fact = obj.optString("fact").trim()
                val cat = obj.optString("category", "Preference").trim()
                if (fact.length >= 6 && addMemory(fact, cat, source = "Auto") > 0L) {
                    added++
                }
            }
        }
        added
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

        // 0. Inject enabled User Memory Bank facts into system prompt
        val enabledMemories = userMemoryDao.getEnabled()
        if (enabledMemories.isNotEmpty()) {
            val memoryBlock = buildString {
                appendLine("### User Memory Bank (Persistent Preferences & Facts)")
                enabledMemories.forEach { m ->
                    appendLine("- [${m.category}] ${m.fact}")
                }
            }
            val mergedSys = listOfNotNull(
                effectiveOptions.systemPrompt?.trim()?.takeIf { it.isNotEmpty() },
                memoryBlock
            ).joinToString("\n\n")
            effectiveOptions = effectiveOptions.copy(systemPrompt = mergedSys)
        }

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

        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
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

        try {
            var toolRound = 0
            val maxToolRounds = 4
            while (toolRound <= maxToolRounds) {
                var requestedToolCalls: List<ToolCallInvocation> = emptyList()
                val roundTextBuffer = StringBuilder()

                withTimeout(timeoutMs) {
                    aiRouter.streamChat(
                        provider = selectableModel.provider,
                        apiKey = apiKey,
                        modelId = selectableModel.model.modelId,
                        messages = inputMessages.toList(),
                        options = effectiveOptions
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
                modelCooldownUntilMs[selectableModel.model.id] = System.currentTimeMillis() + 60_000L
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
        onPartial: suspend (String) -> Unit
    ): String {
        val sb = StringBuilder()
        aiRouter.streamChat(
            provider = selectableModel.provider,
            apiKey = apiKey,
            modelId = selectableModel.model.modelId,
            messages = listOf(ChatInputMessage(role = "USER", text = prompt))
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

    suspend fun quickAskForWidget(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        onPartialUpdate: (modelLabel: String, partialText: String) -> Unit
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val available = observeModelsForCapability(ModelCapability.TEXT_CHAT).first()
        if (available.isEmpty()) {
            throw IllegalStateException("No Text Chat model configured. Open AllChat → Settings to add an API key.")
        }

        val activeConvId = getOrCreateActiveConversationId(
            defaultTitle = prompt.trim().replace("\n", " ").take(40).ifBlank { "Widget Chat" }
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
        val systemPrompt = buildString {
            appendLine("You are AllChat running inside a compact Android Home Screen Widget.")
            appendLine("Give a direct, accurate, concise answer in plain readable text (short paragraphs or brief bullets) so it fits cleanly on a widget.")
            if (enabledMemories.isNotEmpty()) {
                appendLine()
                appendLine("User Memory Bank:")
                enabledMemories.forEach { m -> appendLine("- [${m.category}] ${m.fact}") }
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

        val fullInputMessages = buildList {
            addAll(priorMessages)
            add(ChatInputMessage(role = "USER", text = prompt.trim()))
        }

        val apiKey = apiKeyVault.getApiKey(selectedModel.provider.id)
        val modelLabel = selectedModel.model.displayName
        val buffer = StringBuilder()
        var lastEmitMs = 0L

        try {
            withTimeout(25_000L) {
                aiRouter.streamChat(
                    provider = selectedModel.provider,
                    apiKey = apiKey,
                    modelId = selectedModel.model.modelId,
                    messages = fullInputMessages,
                    options = ChatOptions(systemPrompt = systemPrompt, maxTokens = 512)
                ).collect { event ->
                    if (event is StreamEvent.TextDelta) {
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
                }
            }
        } catch (tce: TimeoutCancellationException) {
            throw IllegalStateException("Request timed out after 25s on $modelLabel. Tap Ask to retry.")
        }

        val finalAnswer = cleanWidgetAnswerText(buffer.toString())
            .ifBlank { "No response received from $modelLabel." }

        // Persist the widget Q&A turn into the shared active conversation in Room
        runCatching {
            appendInstantExchange(
                conversationId = activeConvId,
                userText = prompt.trim(),
                assistantText = finalAnswer,
                modelLabel = modelLabel
            )
        }
        modelLabel to finalAnswer
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

        val systemInstruction = when (mode) {
            LiveLensMode.AUTO_EXPLAIN ->
                "Identify, explain, and summarize what is shown in this image clearly and concisely without waiting for further questions."
            LiveLensMode.LIVE_TRANSLATOR ->
                "Extract all visible text in this image and translate it cleanly into $targetLanguage, preserving layout context."
        }
        val userPrompt = when (mode) {
            LiveLensMode.AUTO_EXPLAIN ->
                "Analyze this live camera view right now."
            LiveLensMode.LIVE_TRANSLATOR ->
                "Read all visible text in this camera frame and translate it cleanly into $targetLanguage."
        }

        var lastError: Throwable? = null
        var lastFailoverNotice: String? = null

        for (index in attemptOrder.indices) {
            val candidate = attemptOrder[index]
            val apiKey = apiKeyVault.getApiKey(candidate.provider.id)
            val buffer = StringBuilder()
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
                        options = ChatOptions(systemPrompt = systemInstruction, maxTokens = 700)
                    ).collect { event ->
                        if (event is StreamEvent.TextDelta) {
                            buffer.append(event.text)
                        }
                    }
                }
                val resultText = cleanWidgetAnswerText(buffer.toString())
                    .ifBlank { "Could not read details from this frame. Hold steady and try again." }
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
                    // Put rate-limited model on 60-second cooldown and auto-switch immediately!
                    modelCooldownUntilMs[candidate.model.id] = System.currentTimeMillis() + 60_000L
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
}
