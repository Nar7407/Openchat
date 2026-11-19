package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.ApiKeyVault
import com.example.data.local.ConversationDao
import com.example.data.local.ConversationEntity
import com.example.data.local.GeneratedMediaDao
import com.example.data.local.GeneratedMediaEntity
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.local.ModelDao
import com.example.data.local.ModelEntity
import com.example.data.local.ProviderDao
import com.example.data.local.ProviderEntity
import com.example.data.remote.AiRouter
import com.example.data.remote.ChatInputMessage
import com.example.data.remote.ConnectionTestResult
import com.example.data.remote.FetchedModel
import com.example.data.remote.StreamEvent
import com.example.domain.model.AiErrorCategory
import com.example.domain.model.AiException
import com.example.domain.model.ModelCapability
import com.example.domain.model.ModelInfo
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderType
import com.example.domain.model.SelectableModel
import com.example.domain.model.SummaryLength
import com.example.util.DocumentExtractor
import com.example.util.ImageEncoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream

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
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val generatedMediaDao: GeneratedMediaDao,
    private val apiKeyVault: ApiKeyVault,
    private val aiRouter: AiRouter,
    private val okHttpClient: OkHttpClient
) {

    val providersFlow: Flow<List<ProviderConfig>> = combine(
        providerDao.observeProviders(),
        modelDao.observeAllModels(),
        apiKeyVault.observeConfiguredProviderIds()
    ) { providers, models, configuredKeyIds ->
        val modelsByProvider = models.groupBy { it.providerId }
        providers.map { p ->
            p.toDomain(
                hasApiKey = p.id in configuredKeyIds,
                models = modelsByProvider[p.id].orEmpty().map { it.toDomain() }
            )
        }
    }

    fun observeModelsForCapability(capability: ModelCapability): Flow<List<SelectableModel>> =
        providersFlow.combine(kotlinx.coroutines.flow.flowOf(capability)) { providers, cap ->
            providers.flatMap { provider ->
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
        apiKey: String?
    ): Long {
        val cleanProxy = proxyUrl?.trim()?.takeIf { it.isNotEmpty() }
        val cleanBase = baseUrl.trim().ifEmpty { type.defaultBaseUrl }
        val id = if (existingId == null || existingId <= 0L) {
            providerDao.insert(
                ProviderEntity(
                    name = name.trim(),
                    type = type.name,
                    baseUrl = cleanBase,
                    proxyUrl = cleanProxy
                )
            )
        } else {
            val existing = providerDao.getById(existingId)
            providerDao.update(
                ProviderEntity(
                    id = existingId,
                    name = name.trim(),
                    type = type.name,
                    baseUrl = cleanBase,
                    proxyUrl = cleanProxy,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
            )
            existingId
        }
        if (apiKey != null && apiKey.isNotBlank()) {
            apiKeyVault.saveApiKey(id, apiKey)
        } else if (existingId == null && apiKey != null && apiKey.isBlank()) {
            apiKeyVault.deleteApiKey(id)
        }
        return id
    }

    suspend fun getProviderApiKey(providerId: Long): String = apiKeyVault.getApiKey(providerId)

    suspend fun deleteProvider(providerId: Long) {
        apiKeyVault.deleteApiKey(providerId)
        providerDao.deleteById(providerId)
    }

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
                supportsSummarization = model.supportsSummarization
            )
        )
    }

    suspend fun deleteModel(modelDbId: Long) {
        modelDao.deleteById(modelDbId)
    }

    // --- Conversations & Streaming Chat ---

    suspend fun createConversation(title: String = "New Chat", modelDbId: Long? = null): Long {
        return conversationDao.insert(
            ConversationEntity(
                title = title,
                selectedModelDbId = modelDbId
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

    suspend fun persistAttachment(uri: Uri): String {
        return ImageEncoder.persistAndEncode(context, uri).localFileUri
    }

    suspend fun sendChatMessage(
        conversationId: Long,
        promptText: String,
        attachmentLocalUri: String?,
        selectableModel: SelectableModel
    ) {
        val existingMessages = messageDao.getMessages(conversationId)
        val shouldAutoTitle = existingMessages.isEmpty() && promptText.isNotBlank()

        messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = "USER",
                content = promptText.trim(),
                attachmentUri = attachmentLocalUri
            )
        )
        if (shouldAutoTitle) {
            val autoTitle = promptText.trim().replace("\n", " ").take(40)
            conversationDao.rename(conversationId, autoTitle)
        } else {
            conversationDao.touch(conversationId)
        }

        streamAssistantReply(conversationId, selectableModel)
    }

    suspend fun regenerateLastResponse(
        conversationId: Long,
        selectableModel: SelectableModel
    ) {
        val messages = messageDao.getMessages(conversationId)
        val last = messages.lastOrNull() ?: return
        if (last.role == "ASSISTANT") {
            messageDao.deleteById(last.id)
        }
        streamAssistantReply(conversationId, selectableModel)
    }

    private suspend fun streamAssistantReply(
        conversationId: Long,
        selectableModel: SelectableModel
    ) {
        val history = messageDao.getMessages(conversationId)
            .filter { !it.isError }
        val inputMessages = history.map { msg ->
            val encodedImg = if (!msg.attachmentUri.isNullOrBlank()) {
                ImageEncoder.encodeFromStoredUri(context, msg.attachmentUri)
            } else {
                null
            }
            ChatInputMessage(
                role = msg.role,
                text = msg.content,
                imageBase64 = encodedImg?.first,
                imageMimeType = encodedImg?.second
            )
        }

        val assistantId = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = "ASSISTANT",
                content = "",
                modelLabel = selectableModel.model.displayName
            )
        )

        val apiKey = apiKeyVault.getApiKey(selectableModel.provider.id)
        val buffer = StringBuilder()
        var promptTokens = 0
        var completionTokens = 0

        try {
            aiRouter.streamChat(
                provider = selectableModel.provider,
                apiKey = apiKey,
                modelId = selectableModel.model.modelId,
                messages = inputMessages
            ).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> {
                        buffer.append(event.text)
                        messageDao.updateStreamingMessage(
                            id = assistantId,
                            content = buffer.toString(),
                            promptTokens = promptTokens.takeIf { it > 0 },
                            completionTokens = completionTokens.takeIf { it > 0 }
                        )
                    }
                    is StreamEvent.Usage -> {
                        promptTokens = event.promptTokens
                        completionTokens = event.completionTokens
                        messageDao.updateStreamingMessage(
                            id = assistantId,
                            content = buffer.toString(),
                            promptTokens = promptTokens.takeIf { it > 0 },
                            completionTokens = completionTokens.takeIf { it > 0 }
                        )
                    }
                    StreamEvent.Completed -> Unit
                }
            }

            if (promptTokens > 0 || completionTokens > 0) {
                conversationDao.addTokenUsage(conversationId, promptTokens, completionTokens)
            } else {
                conversationDao.touch(conversationId)
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            if (buffer.isEmpty()) {
                messageDao.updateStreamingMessage(
                    id = assistantId,
                    content = "*Generation stopped.*",
                    promptTokens = null,
                    completionTokens = null
                )
            }
            throw ce
        } catch (t: Throwable) {
            val aiEx = t as? AiException ?: AiException(
                AiErrorCategory.UNKNOWN,
                t.message ?: "Request failed."
            )
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
        }
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
}
