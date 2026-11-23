package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.CanvasVersionInfo
import com.example.domain.model.WorkspaceFileEntry
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSignalRedTint
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissWhite
import com.example.util.LocalWorkspaceManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 1. Kindle-Style Full-Screen "Reading Room" Mode
@Composable
fun ReadingRoomDialog(
    title: String,
    markdownContent: String,
    onDismiss: () -> Unit,
    onAskAboutPassage: (String) -> Unit
) {
    var useSerif by remember { mutableStateOf(true) }
    var fontSizeSp by remember { mutableFloatStateOf(17f) }
    var lineHeightMultiplier by remember { mutableFloatStateOf(1.55f) }
    var sepiaTheme by remember { mutableStateOf(false) }
    var selectedParagraph by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val progress = if (scrollState.maxValue > 0) {
        (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
    } else 1f
    val wordCount = remember(markdownContent) {
        markdownContent.split(Regex("""\s+""")).count { it.isNotBlank() }
    }
    val estMinutes = ((wordCount / 200.0).coerceAtLeast(1.0)).roundToInt()
    val paragraphs = remember(markdownContent) {
        markdownContent.split(Regex("""\n{2,}""")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    val bg = if (sepiaTheme) Color(0xFFF6EFE2) else SwissPaper
    val fg = if (sepiaTheme) Color(0xFF2B2118) else SwissInk

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .testTag("reading_room_dialog"),
            color = bg,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissInk).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "READING ROOM // $wordCount WORDS · ~${estMinutes} MIN",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                            color = SwissWhite
                        )
                        Text(
                            text = title.ifBlank { "Long-Form Focus Reader" },
                            style = MaterialTheme.typography.titleSmall,
                            color = SwissCanvas,
                            maxLines = 1
                        )
                    }
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close reading room", onClick = onDismiss, inverted = true, bordered = false)
                }

                LinearProgressIndicator(
                    progress = { progress },
                    color = SwissSignalRed,
                    trackColor = SwissCanvas,
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissCanvas).border(1.dp, SwissInk).padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwissToggleCell(label = if (useSerif) "SERIF" else "SANS", selected = useSerif, onClick = { useSerif = !useSerif }, modifier = Modifier.weight(1f))
                    SwissToggleCell(label = "SEPIA", selected = sepiaTheme, onClick = { sepiaTheme = !sepiaTheme }, modifier = Modifier.weight(1f))
                    SwissToggleCell(label = "A-", selected = false, onClick = { fontSizeSp = (fontSizeSp - 2f).coerceAtLeast(13f) })
                    SwissToggleCell(label = "A+", selected = false, onClick = { fontSizeSp = (fontSizeSp + 2f).coerceAtMost(26f) })
                    SwissToggleCell(
                        label = String.format("%.1fx", lineHeightMultiplier),
                        selected = false,
                        onClick = { lineHeightMultiplier = if (lineHeightMultiplier >= 1.8f) 1.35f else lineHeightMultiplier + 0.2f }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Tap any paragraph below to Select-to-Ask AI about that passage.",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = SwissMuted
                    )
                    paragraphs.forEach { para ->
                        val isSelected = selectedParagraph == para
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) SwissSignalRedTint else Color.Transparent)
                                .then(if (isSelected) Modifier.border(1.dp, SwissSignalRed) else Modifier)
                                .clickable { selectedParagraph = if (isSelected) null else para }
                                .padding(if (isSelected) 10.dp else 2.dp)
                        ) {
                            Text(
                                text = para,
                                fontFamily = if (useSerif) FontFamily.Serif else FontFamily.SansSerif,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
                                color = fg
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                if (selectedParagraph != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(SwissInk).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PASSAGE SELECTED",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                            color = SwissWhite,
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "ASK AI ABOUT THIS",
                            onClick = {
                                val quote = selectedParagraph!!.take(450)
                                onAskAboutPassage("Explain and unpack this passage clearly:\n\n> $quote")
                                onDismiss()
                            },
                            leadingIcon = Icons.Default.Send,
                            variant = SwissButtonVariant.PRIMARY
                        )
                    }
                }
            }
        }
    }
}

// 2. Interactive "Canvas / Split-Editor" with Highlight-to-Edit, Version History Slider & SAF Write-Back
@Composable
fun InteractiveCanvasDialog(
    initialContent: String,
    versions: List<CanvasVersionInfo>,
    workspaceFiles: List<WorkspaceFileEntry>,
    onSaveVersion: (title: String, content: String) -> Unit,
    onAiEditBlock: suspend (fullDoc: String, selectedBlock: String, instruction: String) -> Result<String>,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var docTitle by remember { mutableStateOf("Canvas Draft") }
    var editorValue by remember { mutableStateOf(TextFieldValue(initialContent.ifBlank { "# New Document\n\nHighlight any paragraph or code block and tap an AI edit action below." })) }
    var previewMode by remember { mutableStateOf(false) }
    var isEditingBlock by remember { mutableStateOf(false) }
    var customInstruction by remember { mutableStateOf("") }
    var selectedTargetFile by remember { mutableStateOf<WorkspaceFileEntry?>(null) }
    var diffPreview by remember { mutableStateOf<String?>(null) }

    val selectionRange: TextRange = editorValue.selection
    val hasSelection = !selectionRange.collapsed && selectionRange.min >= 0 && selectionRange.max <= editorValue.text.length
    val selectedText = if (hasSelection) editorValue.text.substring(selectionRange.min, selectionRange.max) else ""

    fun runBlockEdit(instruction: String) {
        val targetBlock = if (hasSelection) selectedText else editorValue.text
        if (targetBlock.isBlank() || isEditingBlock) return
        scope.launch {
            isEditingBlock = true
            onAiEditBlock(editorValue.text, targetBlock, instruction)
                .onSuccess { replacement ->
                    val newText = if (hasSelection) {
                        editorValue.text.replaceRange(selectionRange.min, selectionRange.max, replacement)
                    } else {
                        replacement
                    }
                    editorValue = TextFieldValue(newText, TextRange(newText.length))
                    onSaveVersion(docTitle, newText)
                    onNotify("CANVAS // Updated block & saved new version.")
                }
                .onFailure { err ->
                    onNotify("CANVAS // ${err.message ?: "Edit failed"}")
                }
            isEditingBlock = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("interactive_canvas_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissInk).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CANVAS STUDIO // SPLIT EDITOR & VERSIONS",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close canvas", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = docTitle,
                            onValueChange = { docTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            colors = swissTextFieldColors(),
                            shape = RectangleShape,
                            modifier = Modifier.weight(1f)
                        )
                        SwissToggleCell(
                            label = if (previewMode) "PREVIEW" else "EDIT",
                            selected = previewMode,
                            onClick = { previewMode = !previewMode },
                            useSignalRedWhenSelected = true
                        )
                        SwissIconCell(
                            icon = Icons.Default.Save,
                            contentDescription = "Save canvas version",
                            onClick = { onSaveVersion(docTitle, editorValue.text) },
                            accent = true
                        )
                    }

                    // Version History Slider
                    if (versions.isNotEmpty()) {
                        var verIdx by remember(versions.size) { mutableIntStateOf(versions.lastIndex) }
                        Row(
                            modifier = Modifier.fillMaxWidth().background(SwissPaper).border(1.dp, SwissInk).padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HISTORY v${versions.getOrNull(verIdx)?.versionNumber ?: 1}/${versions.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = SwissInk
                            )
                            Slider(
                                value = verIdx.toFloat(),
                                onValueChange = { next ->
                                    val idx = next.roundToInt().coerceIn(0, versions.lastIndex)
                                    verIdx = idx
                                    versions.getOrNull(idx)?.let { v ->
                                        docTitle = v.docTitle
                                        editorValue = TextFieldValue(v.content)
                                    }
                                },
                                valueRange = 0f..versions.lastIndex.coerceAtLeast(1).toFloat(),
                                steps = (versions.size - 2).coerceAtLeast(0),
                                colors = SliderDefaults.colors(thumbColor = SwissSignalRed, activeTrackColor = SwissInk),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                        }
                    }

                    // Highlight-to-Edit Mini Action Bar
                    Column(
                        modifier = Modifier.fillMaxWidth().background(SwissPaper).border(1.dp, SwissInk).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (hasSelection) {
                                "HIGHLIGHT-TO-EDIT (${selectedText.length} CHARS SELECTED)"
                            } else {
                                "AI TRANSFORM BAR (SELECT TEXT FOR TARGETED EDIT, OR APPLY TO WHOLE DOC)"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                            color = if (hasSelection) SwissSignalRed else SwissMuted
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SwissToggleCell(label = "SHORTER", selected = false, onClick = { runBlockEdit("Make this tighter, clearer, and 40% shorter.") }, modifier = Modifier.weight(1f))
                            SwissToggleCell(label = "REFACTOR", selected = false, onClick = { runBlockEdit("Refactor for clean architecture, readability, and zero bugs.") }, modifier = Modifier.weight(1f))
                            SwissToggleCell(label = "POLISH", selected = false, onClick = { runBlockEdit("Polish grammar, structure, and professional tone.") }, modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customInstruction,
                                onValueChange = { customInstruction = it },
                                placeholder = { Text("Custom edit instruction…") },
                                singleLine = true,
                                colors = swissTextFieldColors(),
                                shape = RectangleShape,
                                modifier = Modifier.weight(1f)
                            )
                            SwissIconCell(
                                icon = Icons.Default.AutoFixHigh,
                                contentDescription = "Run AI edit",
                                onClick = {
                                    if (customInstruction.isNotBlank()) {
                                        runBlockEdit(customInstruction)
                                        customInstruction = ""
                                    }
                                },
                                enabled = !isEditingBlock && customInstruction.isNotBlank(),
                                accent = true
                            )
                        }
                    }

                    if (previewMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 320.dp)
                                .background(SwissPaper)
                                .border(1.dp, SwissInk)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            LatexMathMarkdownView(content = editorValue.text)
                        }
                    } else {
                        OutlinedTextField(
                            value = editorValue,
                            onValueChange = { editorValue = it },
                            minLines = 9,
                            maxLines = 14,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            colors = swissTextFieldColors(),
                            shape = RectangleShape,
                            modifier = Modifier.fillMaxWidth().testTag("canvas_editor_field")
                        )
                    }

                    // Optional SAF Write-Back to Local Workspace File
                    if (workspaceFiles.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().background(SwissPaper).border(1.dp, SwissInk).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "WRITE-BACK TO LOCAL WORKSPACE (${selectedTargetFile?.relativePath ?: "PICK FILE"})",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = SwissInk
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                workspaceFiles.take(3).forEach { file ->
                                    SwissToggleCell(
                                        label = file.name.take(12),
                                        selected = selectedTargetFile?.uriString == file.uriString,
                                        onClick = {
                                            selectedTargetFile = file
                                            scope.launch {
                                                val orig = LocalWorkspaceManager.readFileContent(context, file.uriString)
                                                diffPreview = LocalWorkspaceManager.buildSimpleDiffPreview(orig, editorValue.text)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            diffPreview?.let { diff ->
                                Text(
                                    text = diff.take(600),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = SwissVerifiedInk,
                                    modifier = Modifier.fillMaxWidth().background(SwissCanvas).padding(6.dp)
                                )
                                SwissButton(
                                    text = "CONFIRM WRITE TO DISK",
                                    onClick = {
                                        val target = selectedTargetFile ?: return@SwissButton
                                        scope.launch {
                                            LocalWorkspaceManager.writeFileContent(context, target.uriString, editorValue.text)
                                                .onSuccess {
                                                    onNotify("WORKSPACE // Wrote ${target.name} to disk.")
                                                    diffPreview = null
                                                }
                                                .onFailure { onNotify("Write failed: ${it.message}") }
                                        }
                                    },
                                    variant = SwissButtonVariant.PRIMARY,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissButton(
                            text = "COPY DOC",
                            onClick = {
                                clipboard.setText(AnnotatedString(editorValue.text))
                                onNotify("CANVAS // Copied to clipboard.")
                            },
                            leadingIcon = Icons.Default.ContentCopy,
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "SAVE VERSION",
                            onClick = { onSaveVersion(docTitle, editorValue.text) },
                            leadingIcon = Icons.Default.Save,
                            variant = SwissButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
