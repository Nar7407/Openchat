package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AllChatRepository
import com.example.data.repository.SummaryProgress
import com.example.domain.model.AiException
import com.example.domain.model.ModelCapability
import com.example.domain.model.SelectableModel
import com.example.domain.model.SummaryLength
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SummarizeInputMode(val label: String) {
    TEXT("Paste Text"),
    FILE("File (.txt/.md/.pdf)"),
    URL("Web URL")
}

data class SummarizeUiState(
    val inputMode: SummarizeInputMode = SummarizeInputMode.TEXT,
    val rawTextInput: String = "",
    val urlInput: String = "",
    val loadedSourceLabel: String? = null,
    val length: SummaryLength = SummaryLength.MEDIUM,
    val availableModels: List<SelectableModel> = emptyList(),
    val selectedModel: SelectableModel? = null,
    val isExtracting: Boolean = false,
    val isSummarizing: Boolean = false,
    val progress: SummaryProgress = SummaryProgress(),
    val errorMessage: String? = null,
    val suggestProxySetting: Boolean = false,
    val errorProviderId: Long? = null
)

class SummarizeViewModel(
    private val repository: AllChatRepository
) : ViewModel() {

    private val inputMode = MutableStateFlow(SummarizeInputMode.TEXT)
    private val rawTextInput = MutableStateFlow("")
    private val urlInput = MutableStateFlow("")
    private val loadedSourceLabel = MutableStateFlow<String?>(null)
    private val summaryLength = MutableStateFlow(SummaryLength.MEDIUM)
    private val selectedModelId = MutableStateFlow<Long?>(null)
    private val isExtracting = MutableStateFlow(false)
    private val isSummarizing = MutableStateFlow(false)
    private val progress = MutableStateFlow(SummaryProgress())
    private val errorState = MutableStateFlow<Triple<String?, Boolean, Long?>>(Triple(null, false, null))

    private var summarizeJob: Job? = null

    val uiState: StateFlow<SummarizeUiState> = combine(
        combine(
            repository.observeModelsForCapability(ModelCapability.SUMMARIZATION),
            selectedModelId
        ) { models, chosenId -> models to chosenId },
        inputMode,
        rawTextInput,
        urlInput,
        loadedSourceLabel
    ) { (models, chosenId), mode, text, url, sourceLabel ->
        val selected = models.firstOrNull { it.model.id == chosenId } ?: models.firstOrNull()
        SummarizeUiState(
            inputMode = mode,
            rawTextInput = text,
            urlInput = url,
            loadedSourceLabel = sourceLabel,
            availableModels = models,
            selectedModel = selected
        )
    }.combine(summaryLength) { state, len ->
        state.copy(length = len)
    }.combine(isExtracting) { state, extracting ->
        state.copy(isExtracting = extracting)
    }.combine(isSummarizing) { state, summarizing ->
        state.copy(isSummarizing = summarizing)
    }.combine(progress) { state, prog ->
        state.copy(progress = prog)
    }.combine(errorState) { state, err ->
        state.copy(
            errorMessage = err.first,
            suggestProxySetting = err.second,
            errorProviderId = err.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SummarizeUiState()
    )

    fun setInputMode(mode: SummarizeInputMode) {
        inputMode.value = mode
        errorState.value = Triple(null, false, null)
    }

    fun updateRawText(text: String) {
        rawTextInput.value = text
    }

    fun updateUrlInput(url: String) {
        urlInput.value = url
    }

    fun setLength(length: SummaryLength) {
        summaryLength.value = length
    }

    fun selectModel(model: SelectableModel) {
        selectedModelId.value = model.model.id
    }

    fun consumeSharedPayload(sharedText: String) {
        val trimmed = sharedText.trim()
        if (trimmed.isEmpty()) return
        val looksLikeUrl = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        if (looksLikeUrl && !trimmed.contains(" ")) {
            inputMode.value = SummarizeInputMode.URL
            urlInput.value = trimmed
            fetchUrlContent(trimmed)
        } else {
            inputMode.value = SummarizeInputMode.TEXT
            rawTextInput.value = trimmed
            loadedSourceLabel.value = "Shared text"
        }
    }

    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            isExtracting.value = true
            errorState.value = Triple(null, false, null)
            runCatching {
                repository.extractTextFromFile(uri)
            }.onSuccess { (title, text) ->
                loadedSourceLabel.value = title
                rawTextInput.value = text
            }.onFailure { err ->
                errorState.value = Triple(err.message ?: "Failed to read document.", false, null)
            }
            isExtracting.value = false
        }
    }

    fun fetchUrlContent(customUrl: String? = null) {
        val target = (customUrl ?: urlInput.value).trim()
        if (target.isEmpty()) return
        viewModelScope.launch {
            isExtracting.value = true
            errorState.value = Triple(null, false, null)
            runCatching {
                repository.extractTextFromUrl(target)
            }.onSuccess { (title, text) ->
                loadedSourceLabel.value = title
                rawTextInput.value = text
            }.onFailure { err ->
                val aiEx = err as? AiException
                errorState.value = Triple(
                    err.message ?: "Failed to fetch URL.",
                    aiEx?.suggestProxySetting == true,
                    null
                )
            }
            isExtracting.value = false
        }
    }

    fun runSummarize() {
        val model = uiState.value.selectedModel
        if (model == null) {
            errorState.value = Triple("Configure a Summarization model in Settings first.", false, null)
            return
        }
        val textToSummarize = rawTextInput.value.trim()
        if (textToSummarize.isEmpty()) {
            errorState.value = Triple("Enter text, load a file, or fetch a URL first.", false, null)
            return
        }

        summarizeJob?.cancel()
        summarizeJob = viewModelScope.launch {
            isSummarizing.value = true
            errorState.value = Triple(null, false, null)
            progress.value = SummaryProgress()
            try {
                repository.summarizeText(
                    sourceText = textToSummarize,
                    length = summaryLength.value,
                    selectableModel = model
                ).collect { prog ->
                    progress.value = prog
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val aiEx = t as? AiException
                errorState.value = Triple(
                    t.message ?: "Summarization failed.",
                    aiEx?.suggestProxySetting == true,
                    model.provider.id
                )
            } finally {
                isSummarizing.value = false
            }
        }
    }

    fun stopSummarize() {
        summarizeJob?.cancel()
        summarizeJob = null
        isSummarizing.value = false
    }
}
