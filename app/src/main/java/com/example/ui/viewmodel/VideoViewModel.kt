package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.GeneratedMediaEntity
import com.example.data.repository.AllChatRepository
import com.example.domain.model.AiException
import com.example.domain.model.ModelCapability
import com.example.domain.model.SelectableModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VideoUiState(
    val availableModels: List<SelectableModel> = emptyList(),
    val selectedModel: SelectableModel? = null,
    val prompt: String = "",
    val aspectRatio: String = "16:9",
    val isPolling: Boolean = false,
    val pollingStatus: String = "",
    val videos: List<GeneratedMediaEntity> = emptyList(),
    val errorMessage: String? = null,
    val suggestProxySetting: Boolean = false,
    val errorProviderId: Long? = null,
    val statusMessage: String? = null
)

class VideoViewModel(
    private val repository: AllChatRepository
) : ViewModel() {

    private val selectedModelId = MutableStateFlow<Long?>(null)
    private val prompt = MutableStateFlow("")
    private val aspectRatio = MutableStateFlow("16:9")
    private val isPolling = MutableStateFlow(false)
    private val pollingStatus = MutableStateFlow("")
    private val errorState = MutableStateFlow<Triple<String?, Boolean, Long?>>(Triple(null, false, null))
    private val statusMessage = MutableStateFlow<String?>(null)

    private var activeVideoJob: Job? = null

    val uiState: StateFlow<VideoUiState> = combine(
        combine(
            repository.observeModelsForCapability(ModelCapability.VIDEO_GENERATION),
            selectedModelId
        ) { models, chosenId -> models to chosenId },
        repository.observeGeneratedVideos(),
        prompt,
        aspectRatio,
        isPolling
    ) { (models, chosenId), videos, p, ratio, polling ->
        val selected = models.firstOrNull { it.model.id == chosenId } ?: models.firstOrNull()
        VideoUiState(
            availableModels = models,
            selectedModel = selected,
            prompt = p,
            aspectRatio = ratio,
            isPolling = polling,
            videos = videos
        )
    }.combine(pollingStatus) { state, pollText ->
        state.copy(pollingStatus = pollText)
    }.combine(errorState) { state, err ->
        state.copy(
            errorMessage = err.first,
            suggestProxySetting = err.second,
            errorProviderId = err.third
        )
    }.combine(statusMessage) { state, msg ->
        state.copy(statusMessage = msg)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VideoUiState()
    )

    fun updatePrompt(value: String) {
        prompt.value = value
    }

    fun setAspectRatio(ratio: String) {
        aspectRatio.value = ratio
    }

    fun selectModel(model: SelectableModel) {
        selectedModelId.value = model.model.id
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun setStatusMessage(msg: String) {
        statusMessage.value = msg
    }

    fun clearError() {
        errorState.value = Triple(null, false, null)
    }

    fun generateVideo() {
        val model = uiState.value.selectedModel
        if (model == null) {
            errorState.value = Triple("Configure a Video Generation model in Settings first.", false, null)
            return
        }
        val cleanPrompt = prompt.value.trim()
        if (cleanPrompt.isEmpty() || isPolling.value) return

        activeVideoJob = viewModelScope.launch {
            isPolling.value = true
            pollingStatus.value = "Submitting video job…"
            errorState.value = Triple(null, false, null)
            try {
                repository.generateVideoAsync(
                    prompt = cleanPrompt,
                    aspectRatio = aspectRatio.value,
                    selectableModel = model,
                    onStatusUpdate = { pollingStatus.value = it }
                )
                prompt.value = ""
                pollingStatus.value = ""
            } catch (ce: kotlinx.coroutines.CancellationException) {
                pollingStatus.value = ""
                throw ce
            } catch (t: Throwable) {
                val aiEx = t as? AiException
                errorState.value = Triple(
                    t.message ?: "Video generation failed.",
                    aiEx?.suggestProxySetting == true,
                    model.provider.id
                )
                pollingStatus.value = ""
            } finally {
                isPolling.value = false
            }
        }
    }

    fun cancelVideoPolling() {
        activeVideoJob?.cancel()
        activeVideoJob = null
        isPolling.value = false
        pollingStatus.value = ""
    }

    fun deleteVideo(item: GeneratedMediaEntity) {
        viewModelScope.launch {
            repository.deleteGeneratedMedia(item)
        }
    }
}
