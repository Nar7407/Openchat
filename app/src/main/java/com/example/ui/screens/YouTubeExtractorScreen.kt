package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.School
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
import com.example.ui.components.ExportStudioDialog
import com.example.ui.components.MarkdownText
import com.example.ui.components.MindMapDialog
import com.example.ui.components.ModelPickerButton
import com.example.ui.components.StudyQuizDialog
import com.example.ui.components.SwissButton
import com.example.ui.components.SwissButtonVariant
import com.example.ui.components.SwissIconCell
import com.example.ui.components.SwissPanel
import com.example.ui.components.SwissPanelHeader
import com.example.ui.components.swissTextFieldColors
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.viewmodel.YouTubeExtractorViewModel

@Composable
fun YouTubeExtractorScreen(
    viewModel: YouTubeExtractorViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SwissCanvas)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(SwissPaper),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwissIconCell(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Chat",
                onClick = onNavigateBack,
                bordered = false,
                cellSize = 52.dp
            )
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(SwissInk))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("STUDIO // YOUTUBE EXTRACTOR", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                Text("Chapters · Takeaways · Study Notes", style = MaterialTheme.typography.titleMedium, color = SwissInk)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))

        if (state.isExtracting || state.isGeneratingStudy || state.isGeneratingMindMap) {
            LinearProgressIndicator(
                color = SwissSignalRed,
                trackColor = SwissConcrete,
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModelPickerButton(
                selectedModel = state.selectedModel,
                availableModels = state.availableModels,
                requiredCapability = ModelCapability.TEXT_CHAT,
                onSelectModel = viewModel::selectModel,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            )

            state.error?.let { err ->
                ErrorBanner(message = err, onDismiss = viewModel::clearError)
            }

            SwissPanel(modifier = Modifier.fillMaxWidth()) {
                SwissPanelHeader(leftLabel = "01 // YOUTUBE LECTURE OR VIDEO URL", rightLabel = "CAPTIONS + AI")
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.urlInput,
                        onValueChange = viewModel::updateUrl,
                        placeholder = { Text("https://www.youtube.com/watch?v=… or youtu.be/…") },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("youtube_url_input")
                    )

                    if (state.statusText.isNotBlank()) {
                        Text(
                            text = state.statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed
                        )
                    }

                    SwissButton(
                        text = if (state.isExtracting) "Extracting Transcript & Notes…" else "Extract Chapters & Study Notes",
                        leadingIcon = Icons.Outlined.PlayCircleOutline,
                        variant = SwissButtonVariant.PRIMARY,
                        enabled = !state.isExtracting,
                        onClick = viewModel::extractVideo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("youtube_extract_button")
                    )
                }
            }

            state.result?.let { res ->
                SwissPanel(modifier = Modifier.fillMaxWidth()) {
                    SwissPanelHeader(
                        leftLabel = "02 // ${res.author.uppercase()}",
                        rightLabel = if (res.hasTimestampedCaptions) "TIMED CAPTIONS" else "AI GROUNDED",
                        rightAccent = res.hasTimestampedCaptions
                    )
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(res.title, style = MaterialTheme.typography.titleLarge, color = SwissInk)
                        Text(res.canonicalUrl, style = MaterialTheme.typography.labelSmall, color = SwissMuted)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SwissButton(
                                text = "Study / Quiz",
                                leadingIcon = Icons.Outlined.School,
                                variant = SwissButtonVariant.INK,
                                minHeight = 42.dp,
                                enabled = !state.isGeneratingStudy,
                                onClick = viewModel::generateStudyPack,
                                modifier = Modifier.weight(1f)
                            )
                            SwissButton(
                                text = "Mind Map",
                                leadingIcon = Icons.Outlined.Hub,
                                variant = SwissButtonVariant.OUTLINE,
                                minHeight = 42.dp,
                                enabled = !state.isGeneratingMindMap,
                                onClick = viewModel::generateMindMap,
                                modifier = Modifier.weight(1f)
                            )
                            SwissButton(
                                text = "Export",
                                leadingIcon = Icons.Outlined.IosShare,
                                variant = SwissButtonVariant.OUTLINE,
                                minHeight = 42.dp,
                                onClick = { showExportDialog = true },
                                modifier = Modifier.weight(0.85f)
                            )
                        }

                        MarkdownText(markdown = res.markdownNotes)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    state.activeStudyPack?.let { pack ->
        StudyQuizDialog(studyPack = pack, onDismiss = viewModel::dismissStudyPack)
    }

    state.activeMindMap?.let { root ->
        MindMapDialog(rootNode = root, onDismiss = viewModel::dismissMindMap)
    }

    if (showExportDialog && state.result != null) {
        ExportStudioDialog(
            title = state.result!!.title,
            markdownContent = state.result!!.markdownNotes,
            onDismiss = { showExportDialog = false }
        )
    }
}
