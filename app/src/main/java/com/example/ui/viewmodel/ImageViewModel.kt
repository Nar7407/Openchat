package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.GeneratedMediaEntity
import com.example.data.repository.AllChatRepository
import com.example.domain.model.AiException
import com.example.domain.model.ModelCapability
import com.example.domain.model.SelectableModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ImageStudioMode(val label: String) {
    GENERATE("Text to Image"),
    INPAINT("Inpaint & Edit")
}

data class ImageUiState(
    val availableModels: List<SelectableModel> = emptyList(),
    val selectedModel: SelectableModel? = null,
    val studioMode: ImageStudioMode = ImageStudioMode.GENERATE,
    val editSourceUri: String? = null,
    val prompt: String = "",
    val aspectRatio: String = "1:1",
    val isGenerating: Boolean = false,
    val images: List<GeneratedMediaEntity> = emptyList(),
    val previewItem: GeneratedMediaEntity? = null,
    val errorMessage: String? = null,
    val suggestProxySetting: Boolean = false,
    val errorProviderId: Long? = null,
    val statusMessage: String? = null
)

class ImageViewModel(
    private val repository: AllChatRepository
) : ViewModel() {

    private val selectedModelId = MutableStateFlow<Long?>(null)
    private val studioMode = MutableStateFlow(ImageStudioMode.GENERATE)
    private val editSourceUri = MutableStateFlow<String?>(null)
    private val prompt = MutableStateFlow("")
    private val aspectRatio = MutableStateFlow("1:1")
    private val isGenerating = MutableStateFlow(false)
    private val previewItem = MutableStateFlow<GeneratedMediaEntity?>(null)
    private val errorState = MutableStateFlow<Triple<String?, Boolean, Long?>>(Triple(null, false, null))
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ImageUiState> = combine(
        combine(
            repository.observeModelsForCapability(ModelCapability.IMAGE_GENERATION),
            selectedModelId
        ) { models, chosenId -> models to chosenId },
        repository.observeGeneratedImages(),
        prompt,
        aspectRatio,
        isGenerating
    ) { (models, chosenId), images, p, ratio, generating ->
        val selected = models.firstOrNull { it.model.id == chosenId } ?: models.firstOrNull()
        ImageUiState(
            availableModels = models,
            selectedModel = selected,
            prompt = p,
            aspectRatio = ratio,
            isGenerating = generating,
            images = images
        )
    }.combine(studioMode) { state, mode ->
        state.copy(studioMode = mode)
    }.combine(editSourceUri) { state, uri ->
        state.copy(editSourceUri = uri)
    }.combine(previewItem) { state, preview ->
        state.copy(previewItem = preview)
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
        initialValue = ImageUiState()
    )

    fun setStudioMode(mode: ImageStudioMode) {
        studioMode.value = mode
        errorState.value = Triple(null, false, null)
    }

    fun setEditSourceUri(uriString: String?) {
        editSourceUri.value = uriString
    }

    fun updatePrompt(value: String) {
        prompt.value = value
    }

    fun setAspectRatio(ratio: String) {
        aspectRatio.value = ratio
    }

    fun selectModel(model: SelectableModel) {
        selectedModelId.value = model.model.id
    }

    fun openPreview(item: GeneratedMediaEntity?) {
        previewItem.value = item
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun clearError() {
        errorState.value = Triple(null, false, null)
    }

    fun setStatusMessage(msg: String) {
        statusMessage.value = msg
    }

    fun generateImage() {
        val model = uiState.value.selectedModel
        if (model == null) {
            errorState.value = Triple("Configure an Image Generation model in Settings first.", false, null)
            return
        }
        val cleanPrompt = prompt.value.trim()
        if (cleanPrompt.isEmpty()) return

        viewModelScope.launch {
            isGenerating.value = true
            errorState.value = Triple(null, false, null)
            try {
                repository.generateImage(
                    prompt = cleanPrompt,
                    aspectRatio = aspectRatio.value,
                    selectableModel = model
                )
                prompt.value = ""
            } catch (t: Throwable) {
                val aiEx = t as? AiException
                errorState.value = Triple(
                    t.message ?: "Image generation failed.",
                    aiEx?.suggestProxySetting == true,
                    model.provider.id
                )
            } finally {
                isGenerating.value = false
            }
        }
    }

    fun editImageWithMask(
        sourcePngBytes: ByteArray,
        openAiTransparentMaskPng: ByteArray?,
        geminiRedOverlayMaskPng: ByteArray?
    ) {
        val model = uiState.value.selectedModel
        if (model == null) {
            errorState.value = Triple("Configure an Image Generation / Editing model in Settings first.", false, null)
            return
        }
        val cleanPrompt = prompt.value.trim()
        if (cleanPrompt.isEmpty()) {
            errorState.value = Triple("Describe what you want to change inside the masked area.", false, null)
            return
        }
        viewModelScope.launch {
            isGenerating.value = true
            errorState.value = Triple(null, false, null)
            try {
                repository.editImageWithMask(
                    prompt = cleanPrompt,
                    sourcePngBytes = sourcePngBytes,
                    openAiMaskPngBytes = openAiTransparentMaskPng,
                    geminiOverlayMaskPngBytes = geminiRedOverlayMaskPng,
                    selectableModel = model
                )
                prompt.value = ""
                statusMessage.value = "INPAINT // Edited image plate saved."
            } catch (t: Throwable) {
                val aiEx = t as? AiException
                errorState.value = Triple(
                    t.message ?: "Image edit failed.",
                    aiEx?.suggestProxySetting == true,
                    model.provider.id
                )
            } finally {
                isGenerating.value = false
            }
        }
    }

    fun deleteImage(item: GeneratedMediaEntity) {
        if (previewItem.value?.id == item.id) {
            previewItem.value = null
        }
        viewModelScope.launch {
            repository.deleteGeneratedMedia(item)
        }
    }
}
