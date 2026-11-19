package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ModelCapability
import com.example.ui.components.ErrorBanner
import com.example.ui.components.MarkdownText
import com.example.ui.components.ModelPickerButton
import com.example.ui.components.SwissButton
import com.example.ui.components.SwissButtonVariant
import com.example.ui.components.SwissIconCell
import com.example.ui.components.SwissPanel
import com.example.ui.components.SwissPanelHeader
import com.example.ui.components.SwissToggleCell
import com.example.ui.components.SwissUtilityHeader
import com.example.ui.components.swissTextFieldColors
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.viewmodel.PipelineStepState
import com.example.ui.viewmodel.PipelineViewModel
import java.util.Locale

@Composable
fun PipelineScreen(
    viewModel: PipelineViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinueInChat: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingStep by remember { mutableStateOf<PipelineStepState?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editInstruction by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SwissCanvas)
            .imePadding()
    ) {
        SwissUtilityHeader(
            breadcrumb = "07 // MULTI-AGENT / PIPELINE",
            title = "prompt chaining",
            leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
            leadingContentDescription = "Back",
            onLeadingClick = onNavigateBack,
            leadingTestTag = "pipeline_back_button"
        )

        if (state.isPipelineRunning) {
            LinearProgressIndicator(
                color = SwissSignalRed,
                trackColor = SwissConcrete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AnimatedVisibility(visible = state.errorToast != null) {
                        state.errorToast?.let { err ->
                            ErrorBanner(
                                message = err,
                                onDismiss = viewModel::clearErrorToast
                            )
                        }
                    }
                }

                // Master Task & Chain Controls
                item {
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwissPanelHeader(
                                leftLabel = "CHAIN // SEQUENTIAL WORKFLOW",
                                rightLabel = "${state.stepCount} STAGES",
                                rightAccent = true
                            )
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CHAIN LENGTH",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SwissInk
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SwissToggleCell(
                                            label = "2 STEPS",
                                            selected = state.stepCount == 2,
                                            onClick = { viewModel.setStepCount(2) }
                                        )
                                        SwissToggleCell(
                                            label = "3 STEPS",
                                            selected = state.stepCount == 3,
                                            onClick = { viewModel.setStepCount(3) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = state.taskPrompt,
                                    onValueChange = viewModel::updateTaskPrompt,
                                    label = { Text("Master Task Prompt") },
                                    placeholder = { Text("e.g. Build a thread-safe LRU cache in Kotlin with TTL eviction…") },
                                    minLines = 3,
                                    maxLines = 6,
                                    shape = RectangleShape,
                                    colors = swissTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pipeline_task_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (state.isPipelineRunning) {
                                        SwissButton(
                                            text = "Stop Pipeline",
                                            leadingIcon = Icons.Filled.Stop,
                                            variant = SwissButtonVariant.PRIMARY,
                                            minHeight = 44.dp,
                                            onClick = viewModel::stopPipeline,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        SwissButton(
                                            text = "Run ${state.stepCount}-Step Chain",
                                            leadingIcon = Icons.Filled.PlayArrow,
                                            variant = SwissButtonVariant.PRIMARY,
                                            enabled = state.taskPrompt.isNotBlank(),
                                            minHeight = 44.dp,
                                            onClick = viewModel::runPipeline,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("pipeline_run_button")
                                        )
                                    }
                                    val hasAnyOutput = state.activeSteps.any { it.outputText.isNotBlank() }
                                    if (hasAnyOutput && !state.isPipelineRunning) {
                                        SwissButton(
                                            text = "Continue in Chat",
                                            leadingIcon = Icons.AutoMirrored.Filled.Send,
                                            variant = SwissButtonVariant.INK,
                                            minHeight = 44.dp,
                                            onClick = { viewModel.saveAndContinueInChat(onContinueInChat) },
                                            modifier = Modifier.testTag("pipeline_continue_chat_button")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Individual Chained Stages
                items(state.activeSteps, key = { it.stepIndex }) { step ->
                    val model = state.modelForStep(step)
                    val stepNum = "0${step.stepIndex + 1}"
                    val statusRight = when {
                        step.isRunning -> "STREAM // LIVE"
                        step.errorMessage != null -> "FAULT"
                        step.isCompleted -> String.format(Locale.US, "DONE · %.1fs", step.latencyMs / 1000.0)
                        else -> "QUEUED"
                    }

                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwissPanelHeader(
                                leftLabel = "STEP $stepNum // ${step.title.uppercase()}",
                                rightLabel = statusRight,
                                rightAccent = step.isRunning || step.errorMessage != null,
                                backgroundColor = if (step.isCompleted) SwissSubtleFill else SwissPaper
                            )
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ModelPickerButton(
                                        selectedModel = model,
                                        availableModels = state.availableModels,
                                        requiredCapability = ModelCapability.TEXT_CHAT,
                                        onSelectModel = { picked ->
                                            viewModel.selectModelForStep(step.stepIndex, picked)
                                        },
                                        onOpenSettings = onOpenSettings,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SwissIconCell(
                                        icon = Icons.Outlined.Edit,
                                        contentDescription = "Edit step instruction",
                                        onClick = {
                                            editingStep = step
                                            editTitle = step.title
                                            editInstruction = step.instruction
                                        },
                                        cellSize = 48.dp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SwissSubtleFill)
                                        .border(1.dp, SwissConcrete, RectangleShape)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "ROLE // ${step.instruction}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissMuted
                                    )
                                }

                                step.errorMessage?.let { err ->
                                    ErrorBanner(message = err, onDismiss = {})
                                }

                                if (step.outputText.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SwissPaper)
                                            .border(1.dp, SwissInk, RectangleShape)
                                            .padding(12.dp)
                                    ) {
                                        MarkdownText(markdown = step.outputText)
                                    }
                                } else if (step.isRunning) {
                                    Text(
                                        text = "EXECUTING STAGE $stepNum…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SwissSignalRed
                                    )
                                } else if (step.isCompleted) {
                                    Text(
                                        text = "PASSED OUTPUT TO NEXT STAGE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissVerifiedInk
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    editingStep?.let { target ->
        AlertDialog(
            onDismissRequest = { editingStep = null },
            containerColor = SwissCanvas,
            shape = RectangleShape,
            title = {
                Column {
                    Text("STAGE 0${target.stepIndex + 1} // CONFIG", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                    Text("Edit Pipeline Stage", style = MaterialTheme.typography.titleLarge, color = SwissInk)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Stage Title") },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editInstruction,
                        onValueChange = { editInstruction = it },
                        label = { Text("Stage Instruction") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                SwissButton(
                    text = "Save Stage",
                    variant = SwissButtonVariant.PRIMARY,
                    minHeight = 42.dp,
                    onClick = {
                        viewModel.updateStepInstruction(target.stepIndex, editTitle, editInstruction)
                        editingStep = null
                    }
                )
            },
            dismissButton = {
                SwissButton(
                    text = "Cancel",
                    variant = SwissButtonVariant.OUTLINE,
                    minHeight = 42.dp,
                    onClick = { editingStep = null }
                )
            }
        )
    }
}
