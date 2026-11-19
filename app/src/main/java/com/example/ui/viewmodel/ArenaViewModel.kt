package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.StreamEvent
import com.example.data.repository.AllChatRepository
import com.example.domain.model.ChatOptions
import com.example.domain.model.CostEstimator
import com.example.domain.model.DisplayCurrency
import com.example.domain.model.ModelCapability
import com.example.domain.model.SelectableModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArenaSlotState(
    val slotIndex: Int,
    val selectedModelId: Long? = null,
    val outputText: String = "",
    val isStreaming: Boolean = false,
    val latencyMs: Long = 0L,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val estimatedCostUsd: Double = 0.0,
    val errorMessage: String? = null
)

data class ArenaUiState(
    val availableModels: List<SelectableModel> = emptyList(),
    val slotCount: Int = 2,
    val isSplitView: Boolean = false,
    val slots: List<ArenaSlotState> = listOf(ArenaSlotState(0), ArenaSlotState(1)),
    val lastPrompt: String = "",
    val displayCurrency: DisplayCurrency = DisplayCurrency.USD,
    val errorToast: String? = null
) {
    val isAnyStreaming: Boolean get() = slots.take(slotCount).any { it.isStreaming }

    fun modelForSlot(slot: ArenaSlotState): SelectableModel? {
        if (availableModels.isEmpty()) return null
        return availableModels.firstOrNull { it.model.id == slot.selectedModelId }
            ?: availableModels.getOrNull(slot.slotIndex % availableModels.size)
    }
}

class ArenaViewModel(
    private val repository: AllChatRepository
) : ViewModel() {

    private val slotCount = MutableStateFlow(2)
    private val isSplitView = MutableStateFlow(false)
    private val slotsState = MutableStateFlow(
        listOf(ArenaSlotState(0), ArenaSlotState(1), ArenaSlotState(2))
    )
    private val lastPrompt = MutableStateFlow("")
    private val errorToast = MutableStateFlow<String?>(null)

    private val activeJobs = mutableMapOf<Int, Job>()

    val uiState: StateFlow<ArenaUiState> = combine(
        repository.observeModelsForCapability(ModelCapability.TEXT_CHAT),
        slotCount,
        isSplitView,
        slotsState,
        repository.displayCurrencyFlow
    ) { models, count, split, slots, currency ->
        ArenaUiState(
            availableModels = models,
            slotCount = count,
            isSplitView = split,
            slots = slots.take(count),
            displayCurrency = currency
        )
    }.combine(lastPrompt) { state, prompt ->
        state.copy(lastPrompt = prompt)
    }.combine(errorToast) { state, toast ->
        state.copy(errorToast = toast)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ArenaUiState()
    )

    fun setSlotCount(count: Int) {
        slotCount.value = count.coerceIn(2, 3)
    }

    fun setSplitView(split: Boolean) {
        isSplitView.value = split
    }

    fun selectModelForSlot(slotIndex: Int, model: SelectableModel) {
        slotsState.update { list ->
            list.map { if (it.slotIndex == slotIndex) it.copy(selectedModelId = model.model.id) else it }
        }
    }

    fun clearErrorToast() {
        errorToast.value = null
    }

    fun runComparison(promptText: String) {
        val clean = promptText.trim()
        if (clean.isEmpty()) return

        val state = uiState.value
        val activeSlots = state.slots.take(state.slotCount)
        val targets = activeSlots.mapNotNull { slot ->
            val model = state.modelForSlot(slot)
            if (model != null) slot.slotIndex to model else null
        }
        if (targets.isEmpty()) {
            errorToast.value = "Configure at least one Text Chat model in Settings first."
            return
        }

        stopAll()
        lastPrompt.value = clean

        targets.forEach { (slotIdx, selectableModel) ->
            updateSlot(slotIdx) {
                it.copy(
                    selectedModelId = selectableModel.model.id,
                    outputText = "",
                    isStreaming = true,
                    latencyMs = 0L,
                    promptTokens = 0,
                    completionTokens = 0,
                    estimatedCostUsd = 0.0,
                    errorMessage = null
                )
            }

            activeJobs[slotIdx] = viewModelScope.launch {
                val startedAt = System.currentTimeMillis()
                val buffer = StringBuilder()
                var promptT = 0
                var completionT = 0
                try {
                    repository.streamArenaModel(
                        selectableModel = selectableModel,
                        promptText = clean,
                        options = ChatOptions()
                    ).collect { event ->
                        val elapsed = System.currentTimeMillis() - startedAt
                        when (event) {
                            is StreamEvent.TextDelta -> {
                                buffer.append(event.text)
                                updateSlot(slotIdx) {
                                    it.copy(
                                        outputText = buffer.toString(),
                                        latencyMs = elapsed
                                    )
                                }
                            }
                            is StreamEvent.Usage -> {
                                promptT = event.promptTokens
                                completionT = event.completionTokens
                                val cost = CostEstimator.calculateUsd(
                                    promptT,
                                    completionT,
                                    selectableModel.model.inputPricePerMillion,
                                    selectableModel.model.outputPricePerMillion
                                )
                                updateSlot(slotIdx) {
                                    it.copy(
                                        latencyMs = elapsed,
                                        promptTokens = promptT,
                                        completionTokens = completionT,
                                        estimatedCostUsd = cost
                                    )
                                }
                            }
                            StreamEvent.Completed -> Unit
                            else -> Unit
                        }
                    }
                    val totalMs = System.currentTimeMillis() - startedAt
                    updateSlot(slotIdx) {
                        it.copy(isStreaming = false, latencyMs = totalMs)
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    updateSlot(slotIdx) { it.copy(isStreaming = false) }
                    throw ce
                } catch (t: Throwable) {
                    val totalMs = System.currentTimeMillis() - startedAt
                    updateSlot(slotIdx) {
                        it.copy(
                            isStreaming = false,
                            latencyMs = totalMs,
                            errorMessage = t.message ?: "Model request failed."
                        )
                    }
                }
            }
        }
    }

    fun stopAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        slotsState.update { list -> list.map { it.copy(isStreaming = false) } }
    }

    fun continueWithSlot(slotIndex: Int, onConversationCreated: (Long) -> Unit) {
        val state = uiState.value
        val slot = state.slots.firstOrNull { it.slotIndex == slotIndex } ?: return
        val model = state.modelForSlot(slot) ?: return
        if (state.lastPrompt.isBlank() || slot.outputText.isBlank()) return

        stopAll()
        viewModelScope.launch {
            val convId = repository.createConversationFromArena(
                promptText = state.lastPrompt,
                winningModel = model,
                replyText = slot.outputText,
                promptTokens = slot.promptTokens,
                completionTokens = slot.completionTokens
            )
            onConversationCreated(convId)
        }
    }

    private fun updateSlot(slotIndex: Int, transform: (ArenaSlotState) -> ArenaSlotState) {
        slotsState.update { list ->
            list.map { if (it.slotIndex == slotIndex) transform(it) else it }
        }
    }
}
