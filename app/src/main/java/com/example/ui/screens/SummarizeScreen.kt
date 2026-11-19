package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ModelCapability
import com.example.domain.model.SummaryLength
import com.example.ui.components.ErrorBanner
import com.example.ui.components.MarkdownText
import com.example.ui.components.MindMapDialog
import com.example.ui.components.ModelPickerButton
import com.example.ui.components.StudyQuizDialog
import com.example.ui.components.SwissButton
import com.example.ui.components.SwissButtonVariant
import com.example.ui.components.SwissIconCell
import com.example.ui.components.SwissPanel
import com.example.ui.components.SwissPanelHeader
import com.example.ui.components.SwissToggleCell
import com.example.ui.components.SwissUtilityHeader
import com.example.ui.components.swissGraphPaper
import com.example.ui.components.swissRegistrationMarks
import com.example.ui.components.swissTextFieldColors
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.viewmodel.SummarizeInputMode
import com.example.ui.viewmodel.SummarizeViewModel
import com.example.util.MediaStoreSaver

@Composable
fun SummarizeScreen(
    viewModel: SummarizeViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) viewModel.loadFile(uri)
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SwissCanvas)
    ) {
        SwissUtilityHeader(
            breadcrumb = "02 // MODULE / SUMMARIZE",
            title = "summarize // digest",
            leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
            leadingContentDescription = "Back to chat",
            onLeadingClick = onNavigateBack,
            leadingTestTag = "summarize_back_button",
            trailingContent = {
                Box(
                    modifier = Modifier
                        .background(SwissConcrete)
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.length.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissInk
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 780.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModelPickerButton(
                    selectedModel = state.selectedModel,
                    availableModels = state.availableModels,
                    requiredCapability = ModelCapability.SUMMARIZATION,
                    onSelectModel = viewModel::selectModel,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                )

                // Segmented 3-cell input mode bar
                SwissPanel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SwissPanelHeader(
                            leftLabel = "01 // INPUT SOURCE",
                            rightLabel = state.inputMode.label.uppercase()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SummarizeInputMode.entries.forEach { mode ->
                                SwissToggleCell(
                                    label = mode.label,
                                    selected = state.inputMode == mode,
                                    onClick = { viewModel.setInputMode(mode) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("summarize_mode_${mode.name.lowercase()}")
                                )
                            }
                        }

                        when (state.inputMode) {
                            SummarizeInputMode.FILE -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp)
                                        .padding(bottom = 10.dp)
                                        .background(SwissSubtleFill)
                                        .border(1.dp, SwissInk, RectangleShape)
                                        .swissGraphPaper()
                                        .swissRegistrationMarks()
                                        .padding(14.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "CLIENT EXTRACTOR // .TXT / .MD / .PDF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SwissMuted
                                        )
                                        SwissButton(
                                            text = "Choose Document",
                                            leadingIcon = Icons.Outlined.UploadFile,
                                            variant = SwissButtonVariant.INK,
                                            onClick = {
                                                filePickerLauncher.launch(
                                                    arrayOf("text/plain", "text/markdown", "application/pdf", "text/*")
                                                )
                                            },
                                            modifier = Modifier.testTag("pick_document_button")
                                        )
                                        if (!state.loadedSourceLabel.isNullOrBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Description,
                                                    contentDescription = null,
                                                    tint = SwissSignalRed,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "${state.loadedSourceLabel} (${state.rawTextInput.length} CHARS)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SwissInk
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            SummarizeInputMode.URL -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp)
                                        .padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = state.urlInput,
                                        onValueChange = viewModel::updateUrlInput,
                                        placeholder = { Text("https://example.com/article") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Link, contentDescription = null, tint = SwissInk)
                                        },
                                        singleLine = true,
                                        shape = RectangleShape,
                                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("summarize_url_input")
                                    )
                                    SwissButton(
                                        text = "Fetch",
                                        variant = SwissButtonVariant.INK,
                                        enabled = !state.isExtracting && state.urlInput.isNotBlank(),
                                        onClick = { viewModel.fetchUrlContent() },
                                        modifier = Modifier.testTag("fetch_url_button")
                                    )
                                }
                            }

                            SummarizeInputMode.TEXT -> Unit
                        }

                        if (state.isExtracting) {
                            LinearProgressIndicator(
                                color = SwissSignalRed,
                                trackColor = SwissConcrete,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                            )
                        }
                    }
                }

                // Source Content Panel
                SwissPanel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SwissPanelHeader(
                            leftLabel = "02 // SOURCE BUFFER",
                            rightLabel = "${state.rawTextInput.length} CHARS"
                        )
                        OutlinedTextField(
                            value = state.rawTextInput,
                            onValueChange = viewModel::updateRawText,
                            placeholder = { Text("Paste source text or load a document / URL above…") },
                            minLines = 5,
                            maxLines = 10,
                            shape = RectangleShape,
                            colors = swissTextFieldColors(containerColor = SwissPaper),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                                .testTag("summarize_text_input")
                        )
                    }
                }

                // Summary Length Selector & Execute CTA
                SwissPanel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SwissPanelHeader(
                            leftLabel = "03 // COMPRESSION RATIO",
                            rightLabel = state.length.label.uppercase()
                        )
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SummaryLength.entries.forEach { len ->
                                    SwissToggleCell(
                                        label = len.label,
                                        selected = state.length == len,
                                        onClick = { viewModel.setLength(len) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("summary_length_${len.name.lowercase()}")
                                    )
                                }
                            }

                            if (state.isSummarizing) {
                                SwissButton(
                                    text = "Stop Summarizing",
                                    leadingIcon = Icons.Filled.Stop,
                                    variant = SwissButtonVariant.PRIMARY,
                                    onClick = viewModel::stopSummarize,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("stop_summarize_button")
                                )
                            } else {
                                SwissButton(
                                    text = "Execute Summary",
                                    leadingIcon = Icons.Outlined.AutoAwesome,
                                    variant = SwissButtonVariant.PRIMARY,
                                    enabled = state.rawTextInput.isNotBlank() && state.selectedModel != null,
                                    onClick = viewModel::runSummarize,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("run_summarize_button")
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = state.errorMessage != null) {
                    state.errorMessage?.let { err ->
                        ErrorBanner(
                            message = err,
                            suggestProxySetting = state.suggestProxySetting,
                            onOpenProxySettings = onOpenSettings
                        )
                    }
                }

                // Archival Summary Output Panel
                AnimatedVisibility(visible = state.isSummarizing || state.progress.partialOutput.isNotBlank()) {
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwissPanelHeader(
                                leftLabel = "04 // DIGEST OUTPUT",
                                rightLabel = state.progress.statusText.ifBlank { "READY" },
                                rightAccent = state.isSummarizing
                            )

                            if (state.isSummarizing && state.progress.totalChunks > 0) {
                                LinearProgressIndicator(
                                    progress = {
                                        state.progress.currentChunk.toFloat() / state.progress.totalChunks.coerceAtLeast(1)
                                    },
                                    color = SwissSignalRed,
                                    trackColor = SwissConcrete,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (state.progress.partialOutput.isNotBlank()) {
                                    MarkdownText(markdown = state.progress.partialOutput)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SwissButton(
                                            text = "Copy",
                                            leadingIcon = Icons.Outlined.ContentCopy,
                                            variant = SwissButtonVariant.OUTLINE,
                                            minHeight = 40.dp,
                                            onClick = {
                                                clipboard.setText(AnnotatedString(state.progress.partialOutput))
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        SwissButton(
                                            text = "Share",
                                            leadingIcon = Icons.Outlined.Share,
                                            variant = SwissButtonVariant.INK,
                                            minHeight = 40.dp,
                                            onClick = {
                                                MediaStoreSaver.shareText(
                                                    context,
                                                    state.progress.partialOutput,
                                                    "Share Summary"
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SwissButton(
                                            text = if (state.isGeneratingStudy) "Building Quiz…" else "Study / Quiz",
                                            variant = SwissButtonVariant.PRIMARY,
                                            enabled = !state.isSummarizing && !state.isGeneratingStudy && state.selectedModel != null,
                                            minHeight = 40.dp,
                                            onClick = viewModel::generateStudyPack,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("summarize_study_quiz_btn")
                                        )
                                        SwissButton(
                                            text = if (state.isGeneratingMindMap) "Mapping…" else "Mind Map",
                                            variant = SwissButtonVariant.OUTLINE,
                                            enabled = !state.isSummarizing && !state.isGeneratingMindMap && state.selectedModel != null,
                                            minHeight = 40.dp,
                                            onClick = viewModel::generateMindMap,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("summarize_mindmap_btn")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    state.activeStudyPack?.let { pack ->
        StudyQuizDialog(
            studyPack = pack,
            onDismiss = viewModel::dismissStudyPack
        )
    }

    state.activeMindMap?.let { root ->
        MindMapDialog(
            rootNode = root,
            onDismiss = viewModel::dismissMindMap
        )
    }
}
