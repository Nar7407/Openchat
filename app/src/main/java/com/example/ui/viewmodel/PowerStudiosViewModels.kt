package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AllChatRepository
import com.example.domain.model.CodeDebugResult
import com.example.domain.model.MindMapNode
import com.example.domain.model.ModelCapability
import com.example.domain.model.RewritePreset
import com.example.domain.model.SelectableModel
import com.example.domain.model.StudyPack
import com.example.domain.model.YouTubeStudyResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// --- 1. Tone & Rewrite Studio ViewModel ---

data class RewriteUiState(
    val availableModels: List<SelectableModel> = emptyList(),
    val selectedModel: SelectableModel? = null,
    val inputText: String = "",
    val customNote: String = "",
    val activePreset: RewritePreset = RewritePreset.POLITE_REPLY,
    val outputText: String = "",
    val isProcessing: Boolean = false,
    val error: String? = null
)

class RewriteStudioViewModel(
    private val repository: AllChatRepository
) : ViewModel() {
    private val selectedModelId = MutableStateFlow<Long?>(null)
    private val inputText = MutableStateFlow("")
    private val customNote = MutableStateFlow("")
    private val activePreset = MutableStateFlow(RewritePreset.POLITE_REPLY)
    private val outputText = MutableStateFlow("")
    private val isProcessing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val modelsFlow = repository.observeModelsForCapability(ModelCapability.TEXT_CHAT)

    val uiState: StateFlow<RewriteUiState> = combine(
        modelsFlow,
        selectedModelId,
        inputText,
        customNote,
        activePreset
    ) { models, selId, input, note, preset ->
        val chosen = models.firstOrNull { it.model.id == selId } ?: models.firstOrNull()
        RewriteUiState(
            availableModels = models,
            selectedModel = chosen,
            inputText = input,
            customNote = note,
            activePreset = preset
        )
    }.combine(combine(outputText, isProcessing, error) { o, p, e -> Triple(o, p, e) }) { state, (out, proc, err) ->
        state.copy(outputText = out, isProcessing = proc, error = err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RewriteUiState())

    fun selectModel(m: SelectableModel) { selectedModelId.value = m.model.id }
    fun updateInput(text: String) { inputText.value = text }
    fun updateCustomNote(text: String) { customNote.value = text }
    fun clearError() { error.value = null }

    fun runPreset(preset: RewritePreset) {
        activePreset.value = preset
        val src = inputText.value.trim()
        if (src.isEmpty()) {
            error.value = "Paste or type rough text first."
            return
        }
        val model = uiState.value.selectedModel ?: run {
            error.value = "Configure a Text Chat model in Settings first."
            return
        }
        if (isProcessing.value) return
        viewModelScope.launch {
            isProcessing.value = true
            error.value = null
            runCatching {
                repository.rewriteText(src, preset, customNote.value, model)
            }.onSuccess { rewritten ->
                outputText.value = rewritten
            }.onFailure { err ->
                error.value = err.message ?: "Rewrite failed."
            }
            isProcessing.value = false
        }
    }
}

// --- 2. Code & Error Debugger ViewModel ---

data class CodeDebuggerUiState(
    val availableModels: List<SelectableModel> = emptyList(),
    val selectedModel: SelectableModel? = null,
    val language: String = "Auto",
    val inputTraceOrCode: String = "",
    val contextNote: String = "",
    val result: CodeDebugResult? = null,
    val isDebugging: Boolean = false,
    val error: String? = null
)

class CodeDebuggerViewModel(
    private val repository: AllChatRepository
) : ViewModel() {
    private val selectedModelId = MutableStateFlow<Long?>(null)
    private val language = MutableStateFlow("Auto")
    private val inputTraceOrCode = MutableStateFlow("")
    private val contextNote = MutableStateFlow("")
    private val result = MutableStateFlow<CodeDebugResult?>(null)
    private val isDebugging = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val modelsFlow = repository.observeModelsForCapability(ModelCapability.TEXT_CHAT)

    val uiState: StateFlow<CodeDebuggerUiState> = combine(
        modelsFlow,
        selectedModelId,
        language,
        inputTraceOrCode,
        contextNote
    ) { models, selId, lang, input, note ->
        val chosen = models.firstOrNull { it.model.id == selId } ?: models.firstOrNull()
        CodeDebuggerUiState(
            availableModels = models,
            selectedModel = chosen,
            language = lang,
            inputTraceOrCode = input,
            contextNote = note
        )
    }.combine(combine(result, isDebugging, error) { r, d, e -> Triple(r, d, e) }) { state, (res, dbg, err) ->
        state.copy(result = res, isDebugging = dbg, error = err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CodeDebuggerUiState())

    fun selectModel(m: SelectableModel) { selectedModelId.value = m.model.id }
    fun selectLanguage(lang: String) { language.value = lang }
    fun updateInput(text: String) { inputTraceOrCode.value = text }
    fun updateContextNote(text: String) { contextNote.value = text }
    fun clearError() { error.value = null }

    fun diagnoseAndFix() {
        val raw = inputTraceOrCode.value.trim()
        if (raw.isEmpty()) {
            error.value = "Paste a stack trace or broken code snippet first."
            return
        }
        val model = uiState.value.selectedModel ?: run {
            error.value = "Configure a Text Chat model in Settings first."
            return
        }
        if (isDebugging.value) return
        viewModelScope.launch {
            isDebugging.value = true
            error.value = null
            runCatching {
                repository.debugCodeAndError(raw, language.value, contextNote.value, model)
            }.onSuccess { parsed ->
                result.value = parsed
            }.onFailure { err ->
                error.value = err.message ?: "Code debugging failed."
            }
            isDebugging.value = false
        }
    }
}

// --- 3. YouTube Video / Lecture Extractor ViewModel ---

data class YouTubeExtractorUiState(
    val availableModels: List<SelectableModel> = emptyList(),
    val selectedModel: SelectableModel? = null,
    val urlInput: String = "",
    val statusText: String = "",
    val result: YouTubeStudyResult? = null,
    val isExtracting: Boolean = false,
    val isGeneratingStudy: Boolean = false,
    val activeStudyPack: StudyPack? = null,
    val isGeneratingMindMap: Boolean = false,
    val activeMindMap: MindMapNode? = null,
    val error: String? = null
)

class YouTubeExtractorViewModel(
    private val repository: AllChatRepository
) : ViewModel() {
    private val selectedModelId = MutableStateFlow<Long?>(null)
    private val urlInput = MutableStateFlow("")
    private val statusText = MutableStateFlow("")
    private val result = MutableStateFlow<YouTubeStudyResult?>(null)
    private val isExtracting = MutableStateFlow(false)
    private val isGeneratingStudy = MutableStateFlow(false)
    private val activeStudyPack = MutableStateFlow<StudyPack?>(null)
    private val isGeneratingMindMap = MutableStateFlow(false)
    private val activeMindMap = MutableStateFlow<MindMapNode?>(null)
    private val error = MutableStateFlow<String?>(null)

    private val modelsFlow = repository.observeModelsForCapability(ModelCapability.TEXT_CHAT)

    private val studyBundle = combine(
        isGeneratingStudy,
        activeStudyPack,
        isGeneratingMindMap,
        activeMindMap,
        error
    ) { gs, sp, gm, mm, err ->
        StudyOverlayBundle(gs, sp, gm, mm, err)
    }

    val uiState: StateFlow<YouTubeExtractorUiState> = combine(
        modelsFlow,
        selectedModelId,
        urlInput,
        statusText,
        result
    ) { models, selId, url, status, res ->
        val chosen = models.firstOrNull { it.model.id == selId } ?: models.firstOrNull()
        YouTubeExtractorUiState(
            availableModels = models,
            selectedModel = chosen,
            urlInput = url,
            statusText = status,
            result = res
        )
    }.combine(isExtracting) { state, ext ->
        state.copy(isExtracting = ext)
    }.combine(studyBundle) { state, sb ->
        state.copy(
            isGeneratingStudy = sb.genStudy,
            activeStudyPack = sb.studyPack,
            isGeneratingMindMap = sb.genMap,
            activeMindMap = sb.mindMap,
            error = sb.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YouTubeExtractorUiState())

    fun selectModel(m: SelectableModel) { selectedModelId.value = m.model.id }
    fun updateUrl(url: String) { urlInput.value = url }
    fun clearError() { error.value = null }
    fun dismissStudyPack() { activeStudyPack.value = null }
    fun dismissMindMap() { activeMindMap.value = null }

    fun extractVideo() {
        val url = urlInput.value.trim()
        if (url.isEmpty()) {
            error.value = "Paste a YouTube video or lecture URL first."
            return
        }
        val model = uiState.value.selectedModel ?: run {
            error.value = "Configure a Text Chat model in Settings first."
            return
        }
        if (isExtracting.value) return
        viewModelScope.launch {
            isExtracting.value = true
            error.value = null
            statusText.value = "YOUTUBE // Initializing extraction…"
            runCatching {
                repository.extractYouTubeStudyNotes(url, model) { st -> statusText.value = st }
            }.onSuccess { extracted ->
                result.value = extracted
                statusText.value = ""
            }.onFailure { err ->
                error.value = err.message ?: "YouTube extraction failed."
                statusText.value = ""
            }
            isExtracting.value = false
        }
    }

    fun generateStudyPack() {
        val notes = uiState.value.result?.markdownNotes ?: return
        val model = uiState.value.selectedModel ?: return
        if (isGeneratingStudy.value) return
        viewModelScope.launch {
            isGeneratingStudy.value = true
            runCatching { repository.generateStudyPack(notes, model) }
                .onSuccess { activeStudyPack.value = it }
                .onFailure { error.value = it.message ?: "Study pack failed." }
            isGeneratingStudy.value = false
        }
    }

    fun generateMindMap() {
        val notes = uiState.value.result?.markdownNotes ?: return
        val model = uiState.value.selectedModel ?: return
        if (isGeneratingMindMap.value) return
        viewModelScope.launch {
            isGeneratingMindMap.value = true
            runCatching { repository.generateMindMap(notes, model) }
                .onSuccess { activeMindMap.value = it }
                .onFailure { error.value = it.message ?: "Mind map failed." }
            isGeneratingMindMap.value = false
        }
    }

    private data class StudyOverlayBundle(
        val genStudy: Boolean,
        val studyPack: StudyPack?,
        val genMap: Boolean,
        val mindMap: MindMapNode?,
        val error: String?
    )
}
