package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.CustomSlashCommand
import com.example.domain.model.KnowledgeDocInfo
import com.example.domain.model.RequestParamsOverride
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Compact per-request parameter popover positioned directly above the chat composer.
 */
@Composable
fun RequestParamsPopover(
    params: RequestParamsOverride,
    onUpdate: (RequestParamsOverride) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = SwissSignalRed,
        activeTrackColor = SwissInk,
        inactiveTrackColor = SwissConcrete
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissPaper)
            .border(1.dp, SwissInk, RectangleShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SwissSubtleFill)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PARAMS // PER-REQUEST OVERRIDE",
                style = MaterialTheme.typography.labelSmall,
                color = SwissSignalRed
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (params.hasCustomOverride) {
                    Text(
                        text = "[ RESET ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissInk,
                        modifier = Modifier.clickable(onClick = onReset)
                    )
                }
                Text(
                    text = "[ DONE ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissInk,
                    modifier = Modifier.clickable(onClick = onClose)
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))

        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Temperature slider
            val tempValue = params.temperature ?: 0.7f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TEMPERATURE", style = MaterialTheme.typography.labelSmall, color = SwissInk)
                Text(
                    text = if (params.temperature != null) {
                        String.format(Locale.US, "%.2f", params.temperature)
                    } else {
                        "DEFAULT (0.70)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (params.temperature != null) SwissSignalRed else SwissMuted
                )
            }
            Slider(
                value = tempValue,
                onValueChange = { v ->
                    val snapped = (v * 20).roundToInt() / 20f
                    onUpdate(params.copy(temperature = snapped))
                },
                valueRange = 0f..2f,
                colors = sliderColors,
                modifier = Modifier.height(28.dp)
            )

            // Top-P slider
            val topPValue = params.topP ?: 1.0f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TOP-P (NUCLEUS)", style = MaterialTheme.typography.labelSmall, color = SwissInk)
                Text(
                    text = if (params.topP != null) {
                        String.format(Locale.US, "%.2f", params.topP)
                    } else {
                        "DEFAULT (1.00)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (params.topP != null) SwissSignalRed else SwissMuted
                )
            }
            Slider(
                value = topPValue,
                onValueChange = { v ->
                    val snapped = (v * 20).roundToInt() / 20f
                    onUpdate(params.copy(topP = snapped))
                },
                valueRange = 0.05f..1.0f,
                colors = sliderColors,
                modifier = Modifier.height(28.dp)
            )

            // Max Output Tokens slider
            val maxTokValue = (params.maxTokens ?: 4096).toFloat()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("MAX OUTPUT TOKENS", style = MaterialTheme.typography.labelSmall, color = SwissInk)
                Text(
                    text = if (params.maxTokens != null) "${params.maxTokens}" else "DEFAULT (4096)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (params.maxTokens != null) SwissSignalRed else SwissMuted
                )
            }
            Slider(
                value = maxTokValue,
                onValueChange = { v ->
                    val snapped = ((v / 256f).roundToInt() * 256).coerceIn(256, 16384)
                    onUpdate(params.copy(maxTokens = snapped))
                },
                valueRange = 256f..16384f,
                colors = sliderColors,
                modifier = Modifier.height(28.dp)
            )

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("JSON MODE", style = MaterialTheme.typography.labelSmall, color = SwissInk)
                    Text(
                        "Enforce response_format: json_object",
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissMuted
                    )
                }
                SwissToggleCell(
                    label = if (params.jsonMode) "JSON ON" else "JSON OFF",
                    selected = params.jsonMode,
                    useSignalRedWhenSelected = true,
                    onClick = { onUpdate(params.copy(jsonMode = !params.jsonMode)) },
                    modifier = Modifier.testTag("params_json_mode_toggle")
                )
            }
        }
    }
}

/**
 * Full-screen Swiss JSON inspector dialog showing exact raw Request payload & Response chunks.
 */
@Composable
fun RawJsonInspectorDialog(
    modelLabel: String,
    requestJson: String?,
    responseJson: String?,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var activeTab by remember { mutableStateOf(0) } // 0 = Request, 1 = Response
    var copied by remember { mutableStateOf(false) }

    val displayedText = if (activeTab == 0) {
        requestJson?.ifBlank { null } ?: "// No raw request JSON recorded for this message."
    } else {
        responseJson?.ifBlank { null } ?: "// No raw response chunks recorded for this message."
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .background(SwissCanvas, RectangleShape)
                .border(1.dp, SwissInk, RectangleShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissPaper),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = "{ } // RAW JSON PAYLOAD INSPECTOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissSignalRed
                    )
                    Text(
                        text = modelLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = SwissInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SwissIconCell(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close JSON inspector",
                    onClick = onDismiss,
                    bordered = false,
                    cellSize = 52.dp,
                    modifier = Modifier.border(1.dp, SwissInk, RectangleShape)
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissSubtleFill)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SwissToggleCell(
                        label = "REQUEST JSON",
                        selected = activeTab == 0,
                        useSignalRedWhenSelected = true,
                        onClick = {
                            activeTab = 0
                            copied = false
                        }
                    )
                    SwissToggleCell(
                        label = "RESPONSE JSON",
                        selected = activeTab == 1,
                        useSignalRedWhenSelected = true,
                        onClick = {
                            activeTab = 1
                            copied = false
                        }
                    )
                }
                SwissButton(
                    text = if (copied) "Copied" else "Copy",
                    leadingIcon = Icons.Outlined.ContentCopy,
                    variant = SwissButtonVariant.OUTLINE,
                    minHeight = 34.dp,
                    onClick = {
                        clipboard.setText(AnnotatedString(displayedText))
                        copied = true
                    }
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(SwissPaper)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = displayedText,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = SwissInk
                )
            }
        }
    }
}

/**
 * Live `#doc-name` RAG autocomplete strip directly above the composer when typing `#`.
 */
@Composable
fun KnowledgeTagBar(
    query: String,
    docs: List<KnowledgeDocInfo>,
    onSelectTag: (String) -> Unit,
    onOpenKnowledgeBase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanQuery = query.removePrefix("#").trim()
    val filtered = remember(docs, cleanQuery) {
        if (cleanQuery.isEmpty()) docs
        else docs.filter {
            it.slug.contains(cleanQuery, ignoreCase = true) ||
                it.title.contains(cleanQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissPaper)
            .border(1.dp, SwissInk, RectangleShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SwissSubtleFill)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RAG // LOCAL KNOWLEDGE DOCS (${filtered.size})",
                style = MaterialTheme.typography.labelSmall,
                color = SwissSignalRed
            )
            Text(
                text = "[ MANAGE DOCS ]",
                style = MaterialTheme.typography.labelSmall,
                color = SwissInk,
                modifier = Modifier.clickable(onClick = onOpenKnowledgeBase)
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
        if (docs.isEmpty()) {
            Text(
                text = "No documents indexed yet. Tap [ MANAGE DOCS ] to upload PDF/MD/TXT files.",
                style = MaterialTheme.typography.bodySmall,
                color = SwissMuted,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SwissToggleCell(
                    label = "#all (${docs.size})",
                    selected = false,
                    onClick = { onSelectTag("#all") }
                )
                filtered.forEach { doc ->
                    SwissToggleCell(
                        label = "#${doc.slug}",
                        selected = false,
                        onClick = { onSelectTag("#${doc.slug}") }
                    )
                }
            }
        }
    }
}

/**
 * Custom Slash Commands (/commands) CRUD Manager bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSlashCommandsSheet(
    commands: List<CustomSlashCommand>,
    onSaveCommand: (CustomSlashCommand) -> Unit,
    onDeleteCommand: (String) -> Unit,
    onUseCommand: (CustomSlashCommand) -> Unit,
    onDismiss: () -> Unit
) {
    var editingId by remember { mutableStateOf("") }
    var triggerInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }
    var templateInput by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SwissCanvas,
        shape = RectangleShape,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .border(1.dp, SwissInk, RectangleShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissPaper),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = "/COMMANDS // REUSABLE PROMPT TEMPLATES",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissSignalRed
                    )
                    Text(
                        text = "Custom Slash Commands (${commands.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = SwissInk
                    )
                }
                SwissIconCell(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close custom commands",
                    onClick = onDismiss,
                    bordered = false,
                    cellSize = 52.dp,
                    modifier = Modifier.border(1.dp, SwissInk, RectangleShape)
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SwissPanel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SwissPanelHeader(
                            leftLabel = if (editingId.isBlank()) "CREATE TEMPLATE" else "EDIT TEMPLATE",
                            rightLabel = "USE {{input}} OR APPEND"
                        )
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = triggerInput,
                                onValueChange = { triggerInput = it },
                                label = { Text("Trigger (e.g. /fix, /sql, /email)") },
                                singleLine = true,
                                shape = RectangleShape,
                                colors = swissTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = descInput,
                                onValueChange = { descInput = it },
                                label = { Text("Short Description") },
                                singleLine = true,
                                shape = RectangleShape,
                                colors = swissTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = templateInput,
                                onValueChange = { templateInput = it },
                                label = { Text("Prompt Template (optional {{input}} placeholder)") },
                                placeholder = { Text("Fix grammar, clarity, and make concise:\n\n{{input}}") },
                                minLines = 3,
                                maxLines = 5,
                                shape = RectangleShape,
                                colors = swissTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SwissButton(
                                    text = if (editingId.isBlank()) "Save Command" else "Update Command",
                                    leadingIcon = Icons.Filled.Add,
                                    variant = SwissButtonVariant.PRIMARY,
                                    enabled = triggerInput.isNotBlank() && templateInput.isNotBlank(),
                                    minHeight = 42.dp,
                                    onClick = {
                                        onSaveCommand(
                                            CustomSlashCommand(
                                                id = editingId,
                                                trigger = triggerInput,
                                                description = descInput,
                                                template = templateInput
                                            )
                                        )
                                        editingId = ""
                                        triggerInput = ""
                                        descInput = ""
                                        templateInput = ""
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (editingId.isNotBlank()) {
                                    SwissButton(
                                        text = "Cancel",
                                        variant = SwissButtonVariant.OUTLINE,
                                        minHeight = 42.dp,
                                        onClick = {
                                            editingId = ""
                                            triggerInput = ""
                                            descInput = ""
                                            templateInput = ""
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                commands.forEach { cmd ->
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cmd.trigger,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SwissSignalRed
                                    )
                                    Text(
                                        text = cmd.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissInk
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SwissToggleCell(
                                        label = "USE",
                                        selected = false,
                                        onClick = {
                                            onUseCommand(cmd)
                                            onDismiss()
                                        }
                                    )
                                    SwissIconCell(
                                        icon = Icons.Outlined.Edit,
                                        contentDescription = "Edit custom command",
                                        onClick = {
                                            editingId = cmd.id
                                            triggerInput = cmd.trigger
                                            descInput = cmd.description
                                            templateInput = cmd.template
                                        },
                                        cellSize = 34.dp
                                    )
                                    SwissIconCell(
                                        icon = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete custom command",
                                        onClick = { onDeleteCommand(cmd.id) },
                                        cellSize = 34.dp
                                    )
                                }
                            }
                            Text(
                                text = cmd.template,
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
