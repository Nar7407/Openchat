package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.domain.model.CliCommandSpec
import com.example.domain.model.FilledFormResult
import com.example.domain.model.NegativeConstraint
import com.example.domain.model.ThreadCheckpoint
import com.example.domain.model.VoiceFormTemplate
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite
import com.example.util.VoiceInputController
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Reusable toggleable Negative-Constraint Pill Bar (No Yapping, Code Only, Step-by-Step, ELi5, Table Format)
 * shown directly above the keyboard in Main Chat and Assistant Overlay.
 */
@Composable
fun NegativeConstraintPillBar(
    activeConstraints: Set<NegativeConstraint>,
    onToggleConstraint: (NegativeConstraint) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissPaper)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RULES",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = SwissMuted
        )
        NegativeConstraint.entries.forEach { rule ->
            val selected = rule in activeConstraints
            Box(
                modifier = Modifier
                    .background(if (selected) SwissSignalRed else SwissCanvas, RectangleShape)
                    .border(1.dp, if (selected) SwissSignalRed else SwissInk, RectangleShape)
                    .clickable { onToggleConstraint(rule) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("constraint_pill_${rule.name.lowercase()}")
            ) {
                Text(
                    text = rule.chipLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = if (selected) SwissWhite else SwissInk
                )
            }
        }
    }
}

/**
 * Collapsible "TL;DR Thread Checkpoint" divider banner rendered inline after a summarized message.
 */
@Composable
fun ThreadCheckpointDividerCard(
    checkpoint: ThreadCheckpoint,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(checkpoint.id) { mutableStateOf(false) }

    Surface(
        color = SwissVerifiedTint,
        shape = RectangleShape,
        border = BorderStroke(1.5.dp, SwissInk),
        modifier = modifier
            .fillMaxWidth()
            .testTag("thread_checkpoint_${checkpoint.afterMessageId}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissInk)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkAdded,
                        contentDescription = "Thread checkpoint",
                        tint = SwissSignalRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "TL;DR CHECKPOINT // ${checkpoint.messageCountCovered} MSGS SUMMARIZED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = SwissWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse checkpoint" else "Expand checkpoint",
                        tint = SwissWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove checkpoint",
                        tint = SwissConcrete,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onDelete)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "CONTEXT ANCHOR // USED TO KEEP LONG THREADS FAST & CHEAP",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissVerifiedInk
                    )
                    MarkdownText(
                        markdown = checkpoint.summaryMarkdown,
                        showThinking = false
                    )
                }
            }
        }
    }
}

/**
 * 4. Voice-to-Structured-Form & Instant Table Filler ("Voice Form Filler")
 */
@Composable
fun VoiceFormFillerDialog(
    templates: List<VoiceFormTemplate>,
    onSaveTemplates: (List<VoiceFormTemplate>) -> Unit,
    onFillWithAi: suspend (VoiceFormTemplate, String) -> Result<FilledFormResult>,
    onSendToChat: (String) -> Unit,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedTemplateId by remember(templates) {
        mutableStateOf(templates.firstOrNull()?.id.orEmpty())
    }
    val activeTemplate = templates.firstOrNull { it.id == selectedTemplateId } ?: templates.firstOrNull()

    var dictationText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isMapping by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showNewTemplateBuilder by remember { mutableStateOf(false) }
    var newTplName by remember { mutableStateOf("") }
    var newTplFields by remember { mutableStateOf("Title, Priority, Owner, Notes") }

    val editedFields = remember(activeTemplate?.id) {
        mutableStateMapOf<String, String>().apply {
            activeTemplate?.fields?.forEach { put(it, "") }
        }
    }

    val voiceController = remember(context) {
        VoiceInputController(
            context = context,
            onPartialText = { partial -> dictationText = partial },
            onFinalText = { finalText ->
                dictationText = if (dictationText.isBlank()) finalText else "$dictationText $finalText".trim()
            },
            onListeningChanged = { active -> isListening = active },
            onFailure = { err -> errorMsg = err }
        )
    }
    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) voiceController.start() else errorMsg = "Microphone permission is required for Voice Form Filler."
    }

    fun buildMarkdownTable(): String {
        val tpl = activeTemplate ?: return ""
        return buildString {
            appendLine("### ${tpl.name}")
            appendLine("| Field | Value |")
            appendLine("|---|---|")
            tpl.fields.forEach { f ->
                val v = editedFields[f].orEmpty().ifBlank { "—" }
                appendLine("| **${f.replace("|", "/")}** | ${v.replace("|", "/").replace("\n", " ")} |")
            }
        }.trim()
    }

    fun buildJsonPayload(): String {
        val tpl = activeTemplate ?: return "{}"
        val obj = JSONObject()
        tpl.fields.forEach { f -> obj.put(f, editedFields[f].orEmpty()) }
        return obj.toString(2)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("voice_form_filler_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VOICE FORM FILLER // INSTANT TABLE",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SwissWhite
                        )
                        Text(
                            text = "Speak naturally in one sentence → mapped to structured fields",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissConcrete
                        )
                    }
                    SwissIconCell(
                        icon = Icons.Default.Close,
                        contentDescription = "Close Voice Form Filler",
                        onClick = onDismiss,
                        inverted = true,
                        bordered = false
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 580.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Template Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        templates.forEach { tpl ->
                            SwissToggleCell(
                                label = tpl.name.uppercase(),
                                selected = tpl.id == activeTemplate?.id,
                                useSignalRedWhenSelected = true,
                                onClick = { selectedTemplateId = tpl.id }
                            )
                        }
                        SwissToggleCell(
                            label = "+ TEMPLATE",
                            selected = showNewTemplateBuilder,
                            onClick = { showNewTemplateBuilder = !showNewTemplateBuilder }
                        )
                    }

                    AnimatedVisibility(visible = showNewTemplateBuilder) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissPaper)
                                .border(1.dp, SwissInk, RectangleShape)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("CREATE CUSTOM TEMPLATE", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                            OutlinedTextField(
                                value = newTplName,
                                onValueChange = { newTplName = it },
                                label = { Text("Template Name (e.g. Client Call Note)") },
                                singleLine = true,
                                shape = RectangleShape,
                                colors = swissTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newTplFields,
                                onValueChange = { newTplFields = it },
                                label = { Text("Comma-separated Fields") },
                                shape = RectangleShape,
                                colors = swissTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            SwissButton(
                                text = "Save Custom Template",
                                leadingIcon = Icons.Default.Add,
                                variant = SwissButtonVariant.PRIMARY,
                                onClick = {
                                    val cleanName = newTplName.trim()
                                    val fields = newTplFields.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    if (cleanName.isNotEmpty() && fields.isNotEmpty()) {
                                        val created = VoiceFormTemplate(
                                            id = "tpl_${System.currentTimeMillis()}",
                                            name = cleanName,
                                            category = "CUSTOM",
                                            fields = fields
                                        )
                                        onSaveTemplates(templates + created)
                                        selectedTemplateId = created.id
                                        newTplName = ""
                                        showNewTemplateBuilder = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Natural Voice / Text Dictation Box
                    OutlinedTextField(
                        value = dictationText,
                        onValueChange = { dictationText = it },
                        label = { Text("Dictate naturally (e.g. \"Log bug: camera freezes on low battery, severity high, Android\")") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_form_dictation_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SwissButton(
                            text = if (isListening) "STOP MIC" else "FILL BY VOICE",
                            leadingIcon = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            variant = if (isListening) SwissButtonVariant.PRIMARY else SwissButtonVariant.INK,
                            onClick = {
                                if (isListening) {
                                    voiceController.stop()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) voiceController.start()
                                    else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_form_mic_button")
                        )
                        SwissButton(
                            text = if (isMapping) "MAPPING…" else "MAP FIELDS",
                            leadingIcon = Icons.Outlined.AutoAwesome,
                            variant = SwissButtonVariant.PRIMARY,
                            enabled = !isMapping && dictationText.isNotBlank() && activeTemplate != null,
                            onClick = {
                                val tpl = activeTemplate ?: return@SwissButton
                                errorMsg = null
                                scope.launch {
                                    isMapping = true
                                    onFillWithAi(tpl, dictationText).onSuccess { filled ->
                                        filled.values.forEach { (k, v) -> editedFields[k] = v }
                                        onNotify("FORM // Mapped ${filled.values.size} fields into ${tpl.name}.")
                                    }.onFailure { err ->
                                        errorMsg = err.message ?: "Failed to map voice note."
                                    }
                                    isMapping = false
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_form_map_button")
                        )
                    }

                    if (!errorMsg.isNullOrBlank()) {
                        ErrorBanner(message = errorMsg!!, onDismiss = { errorMsg = null })
                    }

                    // Structured Fields Grid
                    if (activeTemplate != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissPaper)
                                .border(1.dp, SwissInk, RectangleShape)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "STRUCTURED FIELDS // ${activeTemplate.name.uppercase()}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SwissSignalRed
                            )
                            activeTemplate.fields.forEach { field ->
                                OutlinedTextField(
                                    value = editedFields[field].orEmpty(),
                                    onValueChange = { editedFields[field] = it },
                                    label = { Text(field) },
                                    singleLine = false,
                                    maxLines = 2,
                                    shape = RectangleShape,
                                    colors = swissTextFieldColors(containerColor = SwissCanvas),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SwissButton(
                                text = "COPY TABLE",
                                leadingIcon = Icons.Default.ContentCopy,
                                variant = SwissButtonVariant.OUTLINE,
                                onClick = {
                                    clipboard.setText(AnnotatedString(buildMarkdownTable()))
                                    onNotify("FORM // Copied Markdown table.")
                                },
                                modifier = Modifier.weight(1f)
                            )
                            SwissButton(
                                text = "COPY JSON",
                                variant = SwissButtonVariant.OUTLINE,
                                onClick = {
                                    clipboard.setText(AnnotatedString(buildJsonPayload()))
                                    onNotify("FORM // Copied structured JSON.")
                                },
                                modifier = Modifier.weight(0.8f)
                            )
                            SwissButton(
                                text = "TO CHAT",
                                leadingIcon = Icons.Default.Send,
                                variant = SwissButtonVariant.PRIMARY,
                                onClick = {
                                    onSendToChat(buildMarkdownTable())
                                    onDismiss()
                                },
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 9. Interactive CLI Command Builder (/cmd) with Tappable Flag Tooltips & Editable Inline Fields
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CliCommandBuilderDialog(
    initialQuery: String = "",
    onBuildCommand: suspend (String) -> CliCommandSpec,
    onSendToChat: (String) -> Unit,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf(initialQuery.ifBlank { "Compress video to 720p with FFmpeg" }) }
    var isLoading by remember { mutableStateOf(false) }
    var spec by remember { mutableStateOf<CliCommandSpec?>(null) }
    var selectedFlagIdx by remember { mutableIntStateOf(-1) }
    val argOverrides = remember { mutableStateMapOf<Int, String>() }

    fun triggerBuild(targetQuery: String) {
        if (targetQuery.isBlank()) return
        scope.launch {
            isLoading = true
            selectedFlagIdx = -1
            argOverrides.clear()
            val built = runCatching { onBuildCommand(targetQuery) }.getOrNull()
            if (built != null) {
                spec = built
                built.tokens.forEachIndexed { i, t ->
                    if (t.isEditableArg) argOverrides[i] = t.defaultValue
                }
                if (built.tokens.isNotEmpty()) selectedFlagIdx = 0
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        triggerBuild(query)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("cli_command_builder_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = SwissSignalRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "INTERACTIVE CLI COMMAND BUILDER // /CMD",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SwissWhite
                            )
                            Text(
                                text = "Tap any flag for its tooltip · Edit filenames/paths inline before copying",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissConcrete
                            )
                        }
                    }
                    SwissIconCell(
                        icon = Icons.Default.Close,
                        contentDescription = "Close CLI Command Builder",
                        onClick = onDismiss,
                        inverted = true,
                        bordered = false
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 590.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick Shell Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "FFmpeg 720p" to "Compress this video to 720p with FFmpeg",
                            "ADB Uninstall" to "Uninstall bloatware via ADB without root",
                            "Git Squash" to "Squash last 5 git commits interactively",
                            "PowerShell Size" to "Find largest files in folder with Windows PowerShell"
                        ).forEach { (label, presetPrompt) ->
                            SwissToggleCell(
                                label = label.uppercase(),
                                selected = query == presetPrompt,
                                onClick = {
                                    query = presetPrompt
                                    triggerBuild(presetPrompt)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Describe Bash, PowerShell, Git, FFmpeg, or ADB task") },
                            singleLine = true,
                            shape = RectangleShape,
                            colors = swissTextFieldColors(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cli_builder_query_input")
                        )
                        SwissButton(
                            text = if (isLoading) "…" else "BUILD",
                            variant = SwissButtonVariant.PRIMARY,
                            enabled = !isLoading && query.isNotBlank(),
                            onClick = { triggerBuild(query) },
                            modifier = Modifier.testTag("cli_builder_build_btn")
                        )
                    }

                    if (isLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = SwissSignalRed,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                text = "ASSEMBLING MODULAR COMMAND CARD…",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissMuted
                            )
                        }
                    } else spec?.let { currentSpec ->
                        val assembled = currentSpec.assemble(argOverrides)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissPaper)
                                .border(1.5.dp, SwissInk, RectangleShape)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${currentSpec.shell.uppercase()} // ${currentSpec.title}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = SwissInk,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(SwissInk)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = currentSpec.shell.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                }
                            }
                            Text(
                                text = currentSpec.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissMuted
                            )

                            // Live Assembled Terminal Output Preview
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissInk)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "LIVE ASSEMBLED COMMAND",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed
                                )
                                Text(
                                    text = "$ $assembled",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    color = SwissWhite
                                )
                            }

                            // Tappable Flag Pills (tap any flag to inspect tooltip)
                            Text(
                                text = "TAP ANY FLAG FOR TOOLTIP EXPLANATION:",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissMuted
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(SwissInk, RectangleShape)
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = currentSpec.baseCommand,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SwissWhite
                                    )
                                }
                                currentSpec.tokens.forEachIndexed { idx, tok ->
                                    val isSelected = idx == selectedFlagIdx
                                    val currentVal = argOverrides[idx] ?: tok.defaultValue
                                    val pillText = listOf(tok.flag, currentVal)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" ")
                                    Box(
                                        modifier = Modifier
                                            .background(if (isSelected) SwissSignalRed else SwissCanvas, RectangleShape)
                                            .border(1.dp, if (isSelected) SwissSignalRed else SwissInk, RectangleShape)
                                            .clickable { selectedFlagIdx = idx }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                            .testTag("cli_flag_chip_$idx")
                                    ) {
                                        Text(
                                            text = pillText.ifBlank { "(arg)" },
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = if (isSelected) SwissWhite else SwissInk
                                        )
                                    }
                                }
                            }

                            // Active Flag Tooltip Callout Box
                            currentSpec.tokens.getOrNull(selectedFlagIdx)?.let { activeTok ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SwissVerifiedTint)
                                        .border(1.dp, SwissInk, RectangleShape)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = SwissVerifiedInk,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "FLAG TOOLTIP // ${activeTok.flag.ifBlank { activeTok.argLabel.ifBlank { "ARGUMENT" } }}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = SwissVerifiedInk
                                        )
                                        Text(
                                            text = activeTok.explanation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SwissInk
                                        )
                                    }
                                }
                            }

                            // Inline Editable Input Boxes for Filenames / Paths / Values
                            val editableIndices = currentSpec.tokens.indices.filter { currentSpec.tokens[it].isEditableArg }
                            if (editableIndices.isNotEmpty()) {
                                Text(
                                    text = "EDITABLE PARAMETERS (LIVE-UPDATES COMMAND):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissMuted
                                )
                                editableIndices.forEach { idx ->
                                    val tok = currentSpec.tokens[idx]
                                    val labelStr = tok.argLabel.ifBlank { tok.flag.ifBlank { "Argument ${idx + 1}" } }
                                    OutlinedTextField(
                                        value = argOverrides[idx] ?: tok.defaultValue,
                                        onValueChange = { argOverrides[idx] = it },
                                        label = { Text("$labelStr ${if (tok.flag.isNotBlank()) "(${tok.flag})" else ""}") },
                                        singleLine = true,
                                        shape = RectangleShape,
                                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SwissButton(
                                    text = "COPY COMMAND",
                                    leadingIcon = Icons.Default.ContentCopy,
                                    variant = SwissButtonVariant.PRIMARY,
                                    onClick = {
                                        clipboard.setText(AnnotatedString(assembled))
                                        onNotify("CLI // Copied: ${assembled.take(50)}")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cli_copy_command_btn")
                                )
                                SwissButton(
                                    text = "SEND TO CHAT",
                                    leadingIcon = Icons.Default.Send,
                                    variant = SwissButtonVariant.OUTLINE,
                                    onClick = {
                                        onSendToChat("Explain any caveats or safety tips for running this ${currentSpec.shell} command:\n```bash\n$assembled\n```")
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
