package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ModelCapability
import com.example.domain.model.RewritePreset
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ModelPickerButton
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
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissWhite
import com.example.ui.viewmodel.RewriteStudioViewModel
import com.example.util.MultiFormatExporter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RewriteStudioScreen(
    viewModel: RewriteStudioViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copyBanner by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SwissCanvas)
    ) {
        // Header Bar
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
                Text("STUDIO // TONE & REWRITE", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                Text("WhatsApp · Email · LinkedIn Polish", style = MaterialTheme.typography.titleMedium, color = SwissInk)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))

        if (state.isProcessing) {
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

            // Input Panel
            SwissPanel(modifier = Modifier.fillMaxWidth()) {
                SwissPanelHeader(leftLabel = "01 // ROUGH DRAFT INPUT", rightLabel = "${state.inputText.length} CH")
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = viewModel::updateInput,
                        placeholder = { Text("Paste rough message, reply, email draft, or text to translate…") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rewrite_input_field")
                    )
                    OutlinedTextField(
                        value = state.customNote,
                        onValueChange = viewModel::updateCustomNote,
                        placeholder = { Text("Optional constraint (e.g. mention Friday 4pm deadline)…") },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("1-TAP INSTANT TONE & TRANSLATION CHIPS", style = MaterialTheme.typography.labelSmall, color = SwissMuted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RewritePreset.entries.forEach { preset ->
                            val selected = state.activePreset == preset && state.outputText.isNotBlank()
                            Box(
                                modifier = Modifier
                                    .background(if (selected) SwissSignalRed else SwissPaper, RectangleShape)
                                    .border(1.dp, if (selected) SwissSignalRed else SwissInk, RectangleShape)
                                    .clickable(enabled = !state.isProcessing) {
                                        copyBanner = null
                                        viewModel.runPreset(preset)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                    .testTag("rewrite_chip_${preset.name.lowercase()}")
                            ) {
                                Text(
                                    text = preset.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) SwissWhite else SwissInk
                                )
                            }
                        }
                    }
                }
            }

            // Polished Output Panel
            SwissPanel(modifier = Modifier.fillMaxWidth()) {
                SwissPanelHeader(
                    leftLabel = "02 // POLISHED OUTPUT (${state.activePreset.label.uppercase()})",
                    rightLabel = if (state.isProcessing) "WORKING…" else "READY",
                    rightAccent = state.isProcessing
                )
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (copyBanner != null) {
                        Text(copyBanner!!, style = MaterialTheme.typography.labelSmall, color = SwissVerifiedInk)
                    }
                    if (state.outputText.isBlank()) {
                        Text(
                            text = "Tap any chip above to polish or translate your message instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissMuted
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissCanvas, RectangleShape)
                                .border(1.dp, SwissInk, RectangleShape)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = state.outputText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = SwissInk
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SwissButton(
                                text = "Copy & Open WhatsApp",
                                leadingIcon = Icons.AutoMirrored.Outlined.Send,
                                variant = SwissButtonVariant.PRIMARY,
                                minHeight = 46.dp,
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.outputText))
                                    copyBanner = "COPIED // Opening WhatsApp…"
                                    MultiFormatExporter.sharePlainText(
                                        context = context,
                                        title = "Rewritten Message",
                                        text = state.outputText,
                                        targetPackage = "com.whatsapp"
                                    )
                                },
                                modifier = Modifier
                                    .weight(1.4f)
                                    .testTag("rewrite_whatsapp_button")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SwissButton(
                                text = "Copy Text",
                                leadingIcon = Icons.Outlined.ContentCopy,
                                variant = SwissButtonVariant.INK,
                                minHeight = 44.dp,
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.outputText))
                                    copyBanner = "COPIED // Polished text copied to clipboard."
                                },
                                modifier = Modifier.weight(1f)
                            )
                            SwissButton(
                                text = "Share",
                                leadingIcon = Icons.Outlined.Share,
                                variant = SwissButtonVariant.OUTLINE,
                                minHeight = 44.dp,
                                onClick = {
                                    MultiFormatExporter.sharePlainText(context, "AllChat Rewrite", state.outputText)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
