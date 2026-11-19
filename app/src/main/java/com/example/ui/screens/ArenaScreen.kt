package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.CostEstimator
import com.example.domain.model.DisplayCurrency
import com.example.domain.model.ModelCapability
import com.example.domain.model.SelectableModel
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
import com.example.ui.components.swissGraphPaper
import com.example.ui.components.swissTextFieldColors
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite
import com.example.ui.viewmodel.ArenaSlotState
import com.example.ui.viewmodel.ArenaViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ArenaScreen(
    viewModel: ArenaViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinueInChat: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var promptText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SwissCanvas)
            .imePadding()
    ) {
        SwissUtilityHeader(
            breadcrumb = "02 // ARENA / MULTI-MODEL COMPARE",
            title = "arena compare",
            leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
            leadingContentDescription = "Back to chat",
            onLeadingClick = {
                viewModel.stopAll()
                onNavigateBack()
            },
            leadingTestTag = "arena_back_button"
        )

        // Controls Strip: 2 vs 3 models + Carousel vs Split view
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SwissPaper)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwissToggleCell(
                label = "2 MODELS",
                selected = state.slotCount == 2,
                onClick = { viewModel.setSlotCount(2) },
                modifier = Modifier.testTag("arena_2_models")
            )
            SwissToggleCell(
                label = "3 MODELS",
                selected = state.slotCount == 3,
                onClick = { viewModel.setSlotCount(3) },
                modifier = Modifier.testTag("arena_3_models")
            )
            Spacer(Modifier.weight(1f))
            SwissToggleCell(
                label = "CAROUSEL",
                selected = !state.isSplitView,
                useSignalRedWhenSelected = true,
                onClick = { viewModel.setSplitView(false) },
                modifier = Modifier.testTag("arena_carousel_toggle")
            )
            SwissToggleCell(
                label = "SPLIT",
                selected = state.isSplitView,
                useSignalRedWhenSelected = true,
                onClick = { viewModel.setSplitView(true) },
                modifier = Modifier.testTag("arena_split_toggle")
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SwissInk)
        )

        AnimatedVisibility(visible = state.errorToast != null) {
            state.errorToast?.let { toast ->
                ErrorBanner(
                    message = toast,
                    onDismiss = viewModel::clearErrorToast,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (state.lastPrompt.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissInk)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PROMPT //",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )
                Text(
                    text = state.lastPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Comparison Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .swissGraphPaper()
        ) {
            if (state.availableModels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "ARENA // NO MODELS CONFIGURED",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed
                            )
                            Text(
                                text = "Add at least one provider & Text Chat model in Settings to compare responses side-by-side.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SwissMuted
                            )
                            SwissButton(
                                text = "Open Provider Settings",
                                variant = SwissButtonVariant.PRIMARY,
                                onClick = onOpenSettings,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else if (state.isSplitView) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.slots.forEach { slot ->
                        ArenaSlotPane(
                            slot = slot,
                            selectedModel = state.modelForSlot(slot),
                            availableModels = state.availableModels,
                            displayCurrency = state.displayCurrency,
                            compact = true,
                            onSelectModel = { viewModel.selectModelForSlot(slot.slotIndex, it) },
                            onOpenSettings = onOpenSettings,
                            onContinue = {
                                viewModel.continueWithSlot(slot.slotIndex, onContinueInChat)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { state.slots.size })
                Column(modifier = Modifier.fillMaxSize()) {
                    // Swipe tab strip for quick jumps on mobile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissSubtleFill)
                            .border(1.dp, SwissInk, RectangleShape)
                    ) {
                        state.slots.forEachIndexed { idx, slot ->
                            val isCurrent = pagerState.currentPage == idx
                            val modelName = state.modelForSlot(slot)?.model?.displayName ?: "SLOT ${idx + 1}"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isCurrent) SwissInk else SwissPaper)
                                    .clickable {
                                        scope.launch { pagerState.animateScrollToPage(idx) }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "0${idx + 1} / $modelName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrent) SwissWhite else SwissInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (idx < state.slots.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(32.dp)
                                        .background(SwissInk)
                                )
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(10.dp),
                        pageSpacing = 10.dp
                    ) { pageIndex ->
                        val slot = state.slots[pageIndex]
                        ArenaSlotPane(
                            slot = slot,
                            selectedModel = state.modelForSlot(slot),
                            availableModels = state.availableModels,
                            displayCurrency = state.displayCurrency,
                            compact = false,
                            onSelectModel = { viewModel.selectModelForSlot(slot.slotIndex, it) },
                            onOpenSettings = onOpenSettings,
                            onContinue = {
                                viewModel.continueWithSlot(slot.slotIndex, onContinueInChat)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Bottom Arena Prompt Dock
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SwissPaper)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SwissInk)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = {
                        Text(
                            "DISPATCH PROMPT TO ${state.slotCount} MODELS…",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    maxLines = 4,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(containerColor = SwissCanvas),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("arena_prompt_input")
                )
                if (state.isAnyStreaming) {
                    SwissIconCell(
                        icon = Icons.Filled.Stop,
                        contentDescription = "Stop all models",
                        onClick = viewModel::stopAll,
                        accent = true,
                        cellSize = 48.dp,
                        modifier = Modifier.testTag("arena_stop_button")
                    )
                } else {
                    SwissIconCell(
                        icon = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Compare models",
                        onClick = {
                            val toSend = promptText
                            promptText = ""
                            viewModel.runComparison(toSend)
                        },
                        enabled = promptText.isNotBlank() && state.availableModels.isNotEmpty(),
                        accent = true,
                        cellSize = 48.dp,
                        modifier = Modifier.testTag("arena_send_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun ArenaSlotPane(
    slot: ArenaSlotState,
    selectedModel: SelectableModel?,
    availableModels: List<SelectableModel>,
    displayCurrency: DisplayCurrency,
    compact: Boolean,
    onSelectModel: (SelectableModel) -> Unit,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val latencyLabel = if (slot.latencyMs > 0L) {
        String.format(Locale.US, "%.1fs", slot.latencyMs / 1000.0)
    } else if (slot.isStreaming) {
        "LIVE"
    } else {
        "READY"
    }

    SwissPanel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            SwissPanelHeader(
                leftLabel = "SLOT 0${slot.slotIndex + 1} // ${selectedModel?.provider?.name ?: "UNSET"}",
                rightLabel = latencyLabel,
                rightAccent = slot.isStreaming
            )

            ModelPickerButton(
                selectedModel = selectedModel,
                availableModels = availableModels,
                requiredCapability = ModelCapability.TEXT_CHAT,
                onSelectModel = onSelectModel,
                onOpenSettings = onOpenSettings,
                bordered = false,
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SwissInk)
            )

            if (slot.isStreaming) {
                LinearProgressIndicator(
                    color = SwissSignalRed,
                    trackColor = SwissConcrete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }

            // Metrics strip
            val totalTok = slot.promptTokens + slot.completionTokens
            if (totalTok > 0 || slot.estimatedCostUsd > 0.0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissSubtleFill)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TOK // IN:${slot.promptTokens} OUT:${slot.completionTokens}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted
                    )
                    if (slot.estimatedCostUsd > 0.0) {
                        Text(
                            text = CostEstimator.formatCost(slot.estimatedCostUsd, displayCurrency),
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SwissConcrete)
                )
            }

            // Output body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(if (compact) 8.dp else 12.dp)
            ) {
                when {
                    !slot.errorMessage.isNullOrBlank() -> {
                        ErrorBanner(
                            message = slot.errorMessage,
                            onDismiss = {}
                        )
                    }
                    slot.outputText.isNotBlank() -> {
                        MarkdownText(markdown = slot.outputText)
                    }
                    slot.isStreaming -> {
                        Text(
                            text = "STREAMING OUTPUT…",
                            style = MaterialTheme.typography.labelMedium,
                            color = SwissSignalRed
                        )
                    }
                    else -> {
                        Text(
                            text = "Select a model above and send a prompt below to benchmark responses side-by-side.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissMuted
                        )
                    }
                }
            }

            // Footer CTA: Continue with this model
            if (slot.outputText.isNotBlank() && !slot.isStreaming) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SwissInk)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissSubtleFill)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwissIconCell(
                        icon = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy response",
                        onClick = { clipboard.setText(AnnotatedString(slot.outputText)) },
                        cellSize = 40.dp
                    )
                    SwissButton(
                        text = if (compact) "Continue" else "Continue with this model",
                        leadingIcon = if (compact) null else Icons.Outlined.ChatBubbleOutline,
                        variant = SwissButtonVariant.PRIMARY,
                        minHeight = 40.dp,
                        onClick = onContinue,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("arena_continue_slot_${slot.slotIndex}")
                    )
                }
            }
        }
    }
}
