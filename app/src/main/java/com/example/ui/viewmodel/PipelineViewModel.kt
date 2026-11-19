package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.StreamEvent
import com.example.data.repository.AllChatRepository
import com.example.domain.model.ChatOptions
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

data class PipelineStepState(
    val stepIndex: Int,
    val title: String,
    val instruction: String,
    val selectedModelId: Long? = null,
    val outputText: String = "",
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val latencyMs: Long = 0L,
    val errorMessage: String? = null
)

data class PipelineUiState(
    val availableModels: List<SelectableModel> = emptyList(),
    val stepCount: Int = 3,
    val steps: List<PipelineStepState> = defaultPipelineSteps(),
    val taskPrompt: String = "",
    val isPipelineRunning: Boolean = false,
    val errorToast: String? = null
) {
    val activeSteps: List<PipelineStepState> get() = steps.take(stepCount)

    fun modelForStep(step: PipelineStepState): SelectableModel? {
        if (availableModels.isEmpty()) return null
        return availableModels.firstOrNull { it.model.id == step.selectedModelId }
            ?: availableModels.getOrNull(step.stepIndex % availableModels.size)
    }
}

fun defaultPipelineSteps(): List<PipelineStepState> = listOf(
    PipelineStepState(
        stepIndex = 0,
        title = "Outline Architecture",
        instruction = "Break down the user's request into a crisp technical architecture, data flow, key edge cases, and step-by-step plan."
    ),
    PipelineStepState(
        stepIndex = 1,
        title = "Full Implementation",
        instruction = "Using the architecture blueprint from Step 1, write the complete, clean, production-grade implementation."
    ),
    PipelineStepState(
        stepIndex = 2,
        title = "Audit Bugs & Edge Cases",
        instruction = "Carefully audit the implementation from Step 2 for bugs, security risks, race conditions, and missing edge cases, then provide concrete fixes."
    )
)

class PipelineViewModel(
    private val repository: AllChatRepository
) : ViewModel() {

    private val stepCount = MutableStateFlow(3)
    private val stepsState = MutableStateFlow(defaultPipelineSteps())
    private val taskPrompt = MutableStateFlow("")
    private val isPipelineRunning = MutableStateFlow(false)
    private val errorToast = MutableStateFlow<String?>(null)

    private var activeJob: Job? = null

    val uiState: StateFlow<PipelineUiState> = combine(
        repository.observeModelsForCapability(ModelCapability.TEXT_CHAT),
        stepCount,
        stepsState,
        taskPrompt,
        isPipelineRunning
    ) { models, count, steps, prompt, running ->
        PipelineUiState(
            availableModels = models,
            stepCount = count,
            steps = steps,
            taskPrompt = prompt,
            isPipelineRunning = running
        )
    }.combine(errorToast) { state, err ->
        state.copy(errorToast = err)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PipelineUiState()
    )

    fun setStepCount(count: Int) {
        stepCount.value = count.coerceIn(2, 3)
    }

    fun updateTaskPrompt(text: String) {
        taskPrompt.value = text
    }

    fun updateStepInstruction(stepIndex: Int, title: String, instruction: String) {
        stepsState.update { list ->
            list.map {
                if (it.stepIndex == stepIndex) it.copy(title = title, instruction = instruction) else it
            }
        }
    }

    fun selectModelForStep(stepIndex: Int, model: SelectableModel) {
        stepsState.update { list ->
            list.map {
                if (it.stepIndex == stepIndex) it.copy(selectedModelId = model.model.id) else it
            }
        }
    }

    fun clearErrorToast() {
        errorToast.value = null
    }

    fun runPipeline() {
        val state = uiState.value
        val prompt = state.taskPrompt.trim()
        if (prompt.isEmpty()) {
            errorToast.value = "Enter a task or prompt to run through the pipeline."
            return
        }
        val active = state.activeSteps
        val resolvedModels = active.map { step -> state.modelForStep(step) }
        if (resolvedModels.any { it == null }) {
            errorToast.value = "Configure at least one Text Chat model in Settings first."
            return
        }

        stopPipeline()
        errorToast.value = null
        stepsState.update { list ->
            list.map {
                it.copy(
                    outputText = "",
                    isRunning = false,
                    isCompleted = false,
                    latencyMs = 0L,
                    errorMessage = null
                )
            }
        }

        activeJob = viewModelScope.launch {
            isPipelineRunning.value = true
            try {
                val previousOutputs = mutableListOf<Pair<String, String>>()

                for (i in active.indices) {
                    val step = stepsState.value[i]
                    val model = state.modelForStep(step) ?: break
                    val startedAt = System.currentTimeMillis()
                    updateStep(i) { it.copy(isRunning = true, errorMessage = null) }

                    val chainedPrompt = buildString {
                        appendLine("### Original User Task")
                        appendLine(prompt)
                        appendLine()
                        if (previousOutputs.isNotEmpty()) {
                            previousOutputs.forEachIndexed { prevIdx, (prevTitle, prevOut) ->
                                appendLine("### Output from Step ${prevIdx + 1}: $prevTitle")
                                appendLine(prevOut)
                                appendLine()
                            }
                        }
                        appendLine("### Your Assignment (Step ${i + 1}: ${step.title})")
                        appendLine(step.instruction)
                    }

                    val buffer = StringBuilder()
                    try {
                        repository.streamArenaModel(
                            selectableModel = model,
                            promptText = chainedPrompt,
                            options = ChatOptions()
                        ).collect { event ->
                            val elapsed = System.currentTimeMillis() - startedAt
                            if (event is StreamEvent.TextDelta) {
                                buffer.append(event.text)
                                updateStep(i) {
                                    it.copy(
                                        outputText = buffer.toString(),
                                        latencyMs = elapsed
                                    )
                                }
                            }
                        }
                        val totalMs = System.currentTimeMillis() - startedAt
                        val finalOut = buffer.toString().trim()
                        previousOutputs += step.title to finalOut
                        updateStep(i) {
                            it.copy(
                                isRunning = false,
                                isCompleted = true,
                                latencyMs = totalMs,
                                outputText = finalOut
                            )
                        }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        updateStep(i) { it.copy(isRunning = false) }
                        throw ce
                    } catch (t: Throwable) {
                        val totalMs = System.currentTimeMillis() - startedAt
                        updateStep(i) {
                            it.copy(
                                isRunning = false,
                                latencyMs = totalMs,
                                errorMessage = t.message ?: "Step ${i + 1} failed."
                            )
                        }
                        break
                    }
                }
            } finally {
                isPipelineRunning.value = false
            }
        }
    }

    fun stopPipeline() {
        activeJob?.cancel()
        activeJob = null
        isPipelineRunning.value = false
        stepsState.update { list -> list.map { it.copy(isRunning = false) } }
    }

    fun saveAndContinueInChat(onCreated: (Long) -> Unit) {
        val state = uiState.value
        val completed = state.activeSteps.mapNotNull { step ->
            val model = state.modelForStep(step)
            if (model != null && step.outputText.isNotBlank()) {
                Triple(step.title, model, step.outputText)
            } else {
                null
            }
        }
        if (completed.isEmpty()) return
        stopPipeline()
        viewModelScope.launch {
            val convId = repository.createConversationFromPipeline(
                userTask = state.taskPrompt.ifBlank { "Multi-Model Pipeline" },
                stepOutputs = completed
            )
            onCreated(convId)
        }
    }

    private fun updateStep(stepIndex: Int, transform: (PipelineStepState) -> PipelineStepState) {
        stepsState.update { list ->
            list.map { if (it.stepIndex == stepIndex) transform(it) else it }
        }
    }
}
