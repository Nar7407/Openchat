package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.repository.AllChatRepository
import com.example.domain.model.ModelCapability
import com.example.domain.model.SelectableModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val searchQuery: String = "",
    val activeConversation: ConversationEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val availableModels: List<SelectableModel> = emptyList(),
    val selectedModel: SelectableModel? = null,
    val pendingAttachmentUri: String? = null,
    val isStreaming: Boolean = false,
    val errorToast: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: AllChatRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val activeConversationId = MutableStateFlow<Long?>(null)
    private val fallbackSelectedModelId = MutableStateFlow<Long?>(null)
    private val pendingAttachmentUri = MutableStateFlow<String?>(null)
    private val isStreaming = MutableStateFlow(false)
    private val errorToast = MutableStateFlow<String?>(null)

    private var activeStreamJob: Job? = null

    private val chatModelsFlow = repository.observeModelsForCapability(ModelCapability.TEXT_CHAT)

    private val activeConversationFlow = activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeConversation(id)
    }

    private val messagesFlow = activeConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        repository.conversationsFlow,
        searchQuery,
        activeConversationFlow,
        messagesFlow,
        combine(chatModelsFlow, fallbackSelectedModelId) { models, fallbackId -> models to fallbackId }
    ) { conversations, query, activeConv, messages, (models, fallbackId) ->
        val filtered = if (query.isBlank()) {
            conversations
        } else {
            conversations.filter { it.title.contains(query, ignoreCase = true) }
        }
        val selectedModel = models.firstOrNull { it.model.id == activeConv?.selectedModelDbId }
            ?: models.firstOrNull { it.model.id == fallbackId }
            ?: pickPreferredChatModel(models)

        ChatUiState(
            conversations = filtered,
            searchQuery = query,
            activeConversation = activeConv,
            messages = messages,
            availableModels = models,
            selectedModel = selectedModel
        )
    }.combine(pendingAttachmentUri) { state, att ->
        state.copy(pendingAttachmentUri = att)
    }.combine(isStreaming) { state, streaming ->
        state.copy(isStreaming = streaming)
    }.combine(errorToast) { state, toast ->
        state.copy(errorToast = toast)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    init {
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
        activeConversationId.value = conversationId
    }

    fun createNewChat() {
        stopGeneration()
        viewModelScope.launch {
            val modelId = uiState.value.selectedModel?.model?.id
            val newId = repository.createConversation("New Chat", modelId)
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
            activeConversationId.value = null
        }
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
        }
    }

    fun selectModel(selectableModel: SelectableModel) {
        fallbackSelectedModelId.value = selectableModel.model.id
        val convId = activeConversationId.value ?: return
        viewModelScope.launch {
            repository.updateConversationModel(convId, selectableModel.model.id)
        }
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

    fun clearAttachment() {
        pendingAttachmentUri.value = null
    }

    fun clearErrorToast() {
        errorToast.value = null
    }

    fun sendMessage(promptText: String) {
        val model = uiState.value.selectedModel
        if (model == null) {
            errorToast.value = "Configure a Text Chat model in Settings first."
            return
        }
        val attachment = pendingAttachmentUri.value
        if (promptText.isBlank() && attachment == null) return
        if (isStreaming.value) return

        pendingAttachmentUri.value = null
        activeStreamJob = viewModelScope.launch {
            isStreaming.value = true
            try {
                val convId = activeConversationId.value
                    ?: repository.createConversation(
                        title = promptText.trim().take(40).ifBlank { "Image Chat" },
                        modelDbId = model.model.id
                    ).also { activeConversationId.value = it }

                repository.sendChatMessage(
                    conversationId = convId,
                    promptText = promptText,
                    attachmentLocalUri = attachment,
                    selectableModel = model
                )
            } finally {
                isStreaming.value = false
            }
        }
    }

    fun regenerateLastResponse() {
        val model = uiState.value.selectedModel ?: return
        val convId = activeConversationId.value ?: return
        if (isStreaming.value) return

        activeStreamJob = viewModelScope.launch {
            isStreaming.value = true
            try {
                repository.regenerateLastResponse(convId, model)
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
}
