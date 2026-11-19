package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AllChatRepository
import com.example.domain.model.KnowledgeDocInfo
import com.example.domain.model.LocalRagEngine
import com.example.domain.model.RagExcerpt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class KnowledgeUiState(
    val docs: List<KnowledgeDocInfo> = emptyList(),
    val isImporting: Boolean = false,
    val testQuery: String = "",
    val testResults: List<RagExcerpt> = emptyList(),
    val statusNotice: String? = null,
    val errorMessage: String? = null
)

class KnowledgeViewModel(
    private val repository: AllChatRepository
) : ViewModel() {

    private val isImporting = MutableStateFlow(false)
    private val testQuery = MutableStateFlow("")
    private val statusNotice = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<KnowledgeUiState> = combine(
        repository.knowledgeDocsFlow,
        isImporting,
        testQuery,
        statusNotice,
        errorMessage
    ) { docs, importing, query, notice, err ->
        val results = if (query.isNotBlank() && docs.isNotEmpty()) {
            LocalRagEngine.retrieveTopExcerpts(
                query = query,
                docs = docs,
                referencedSlugs = listOf("all"),
                topK = 4
            )
        } else {
            emptyList()
        }
        KnowledgeUiState(
            docs = docs,
            isImporting = importing,
            testQuery = query,
            testResults = results,
            statusNotice = notice,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KnowledgeUiState()
    )

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            isImporting.value = true
            errorMessage.value = null
            statusNotice.value = null
            runCatching {
                repository.addKnowledgeDocFromUri(uri)
            }.onSuccess { doc ->
                statusNotice.value = "INDEXED // #${doc.slug} (${doc.chunkCount} chunks, ${doc.charCount} chars). Reference in chat with #${doc.slug}."
            }.onFailure { err ->
                errorMessage.value = err.message ?: "Could not extract text from selected document."
            }
            isImporting.value = false
        }
    }

    fun addManualNote(title: String, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            errorMessage.value = null
            runCatching {
                repository.addKnowledgeDocManual(title, content)
            }.onSuccess { doc ->
                statusNotice.value = "SAVED // #${doc.slug} (${doc.chunkCount} chunks). Type #${doc.slug} in Chat to retrieve."
            }.onFailure { err ->
                errorMessage.value = err.message ?: "Failed to save note."
            }
        }
    }

    fun renameDoc(id: Long, newTitle: String, newSlug: String) {
        viewModelScope.launch {
            repository.renameKnowledgeDoc(id, newTitle, newSlug)
            statusNotice.value = "UPDATED // Document handle updated."
        }
    }

    fun deleteDoc(id: Long) {
        viewModelScope.launch {
            repository.deleteKnowledgeDoc(id)
            statusNotice.value = "REMOVED // Document deleted from local knowledge index."
        }
    }

    fun updateTestQuery(query: String) {
        testQuery.value = query
    }

    fun clearNotices() {
        statusNotice.value = null
        errorMessage.value = null
    }
}
