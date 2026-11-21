package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.IosShare
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ModelCapability
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ExportStudioDialog
import com.example.ui.components.ModelPickerButton
import com.example.ui.components.SwissBadge
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
import com.example.ui.viewmodel.CodeDebuggerViewModel

@Composable
fun CodeDebuggerScreen(
    viewModel: CodeDebuggerViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var notice by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    val languages = listOf("Auto", "Kotlin", "TypeScript", "Python", "Java", "React/JS", "Swift", "SQL", "Go", "Rust")

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
                Text("STUDIO // CODE & ERROR DEBUGGER", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                Text("Root Cause + Fixed Patch", style = MaterialTheme.typography.titleMedium, color = SwissInk)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))

        if (state.isDebugging) {
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

            // Input Stack Trace / Broken Code Panel
            SwissPanel(modifier = Modifier.fillMaxWidth()) {
                SwissPanelHeader(leftLabel = "01 // STACK TRACE OR BROKEN CODE", rightLabel = state.language.uppercase())
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        languages.forEach { lang ->
                            val selected = state.language == lang
                            Box(
                                modifier = Modifier
                                    .background(if (selected) SwissInk else SwissPaper, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable { viewModel.selectLanguage(lang) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(lang.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (selected) SwissWhite else SwissInk)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = state.inputTraceOrCode,
                        onValueChange = viewModel::updateInput,
                        placeholder = { Text("Paste crash log, stack trace, or broken source code…") },
                        minLines = 5,
                        maxLines = 10,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("debugger_code_input")
                    )

                    OutlinedTextField(
                        value = state.contextNote,
                        onValueChange = viewModel::updateContextNote,
                        placeholder = { Text("Optional context (e.g. Android 15, Compose 1.7, expected behavior)…") },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SwissButton(
                        text = if (state.isDebugging) "Diagnosing Root Cause…" else "Diagnose & Generate Patch",
                        leadingIcon = Icons.Outlined.BugReport,
                        variant = SwissButtonVariant.PRIMARY,
                        enabled = !state.isDebugging,
                        onClick = {
                            notice = null
                            viewModel.diagnoseAndFix()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("debugger_run_button")
                    )
                }
            }

            // Structured Root Cause + Fixed Patch Output
            state.result?.let { res ->
                SwissPanel(modifier = Modifier.fillMaxWidth()) {
                    SwissPanelHeader(
                        leftLabel = "02 // ROOT CAUSE ANALYSIS",
                        rightLabel = "SEVERITY: ${res.severity}",
                        rightAccent = res.severity == "CRITICAL" || res.severity == "HIGH"
                    )
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(res.rootCause, style = MaterialTheme.typography.titleMedium, color = SwissInk)
                        Text(res.explanation, style = MaterialTheme.typography.bodyMedium, color = SwissInk)
                        SwissBadge(
                            text = "PATCH // ${res.patchSummary}",
                            containerColor = SwissConcrete,
                            contentColor = SwissInk
                        )
                    }
                }

                SwissPanel(modifier = Modifier.fillMaxWidth()) {
                    SwissPanelHeader(leftLabel = "03 // FIXED CODE PATCH", rightLabel = state.language.uppercase())
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (notice != null) {
                            Text(notice!!, style = MaterialTheme.typography.labelSmall, color = SwissVerifiedInk)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissInk, RectangleShape)
                                .border(1.dp, SwissInk, RectangleShape)
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = res.fixedCode,
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissWhite
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SwissButton(
                                text = "Copy Patch",
                                leadingIcon = Icons.Outlined.ContentCopy,
                                variant = SwissButtonVariant.PRIMARY,
                                minHeight = 46.dp,
                                onClick = {
                                    clipboard.setText(AnnotatedString(res.fixedCode))
                                    notice = "COPIED // Fixed code patch copied to clipboard."
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("debugger_copy_patch_button")
                            )
                            SwissButton(
                                text = "Export",
                                leadingIcon = Icons.Outlined.IosShare,
                                variant = SwissButtonVariant.OUTLINE,
                                minHeight = 46.dp,
                                onClick = { showExportDialog = true },
                                modifier = Modifier.weight(0.9f)
                            )
                        }

                        if (res.verificationSteps.isNotEmpty()) {
                            Text("VERIFICATION CHECKLIST", style = MaterialTheme.typography.labelSmall, color = SwissMuted)
                            res.verificationSteps.forEachIndexed { idx, step ->
                                Text(
                                    text = "${idx + 1}. $step",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissInk
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showExportDialog && state.result != null) {
        val res = state.result!!
        val md = buildString {
            appendLine("## Root Cause (${res.severity})")
            appendLine(res.rootCause)
            appendLine()
            appendLine(res.explanation)
            appendLine()
            appendLine("## Fixed Code Patch")
            appendLine("```${state.language.lowercase()}")
            appendLine(res.fixedCode)
            appendLine("```")
            if (res.verificationSteps.isNotEmpty()) {
                appendLine()
                appendLine("## Verification")
                res.verificationSteps.forEach { appendLine("- $it") }
            }
        }
        ExportStudioDialog(
            title = "Debug Report: ${res.rootCause.take(40)}",
            markdownContent = md,
            onDismiss = { showExportDialog = false }
        )
    }
}
