package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.CustomSlashCommand
import com.example.domain.model.McpCheckResult
import com.example.domain.model.McpServerConfig
import com.example.domain.model.SlashCommandCategory
import com.example.domain.model.SlashCommandDef
import com.example.domain.model.SlashCommandRegistry
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSignalRedTint
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissThemePreset
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite

/**
 * Live `/` command autocomplete drawer directly above the chat input bar.
 */
@Composable
fun SlashCommandSuggestionBar(
    query: String,
    onSelectCommand: (SlashCommandDef) -> Unit,
    customCommands: List<CustomSlashCommand> = emptyList(),
    onSelectCustomCommand: (CustomSlashCommand) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val matches = remember(query) { SlashCommandRegistry.filterSuggestions(query) }
    val customMatches = remember(query, customCommands) {
        val token = query.trim().takeWhile { !it.isWhitespace() }.lowercase()
        if (!token.startsWith("/")) emptyList()
        else if (token == "/") customCommands
        else customCommands.filter {
            it.trigger.startsWith(token, ignoreCase = true) ||
                it.description.contains(token.removePrefix("/"), ignoreCase = true)
        }
    }
    val totalCount = matches.size + customMatches.size
    if (totalCount == 0) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissPaper)
            .border(1.dp, SwissInk, RectangleShape)
    ) {
        SwissPanelHeader(
            leftLabel = "CMD // SLASH COMMANDS ($totalCount)",
            rightLabel = "TAP TO RUN",
            rightAccent = true
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
        ) {
            customMatches.forEach { custom ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissSubtleFill)
                        .clickable { onSelectCustomCommand(custom) }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .testTag("custom_slash_cmd_${custom.trigger.removePrefix("/")}"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = custom.trigger,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = SwissSignalRed
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = custom.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "CUSTOM TEMPLATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissMuted
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
            matches.forEachIndexed { idx, cmd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper)
                        .clickable { onSelectCommand(cmd) }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .testTag("slash_cmd_${cmd.primary.removePrefix("/")}"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cmd.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = SwissSignalRed
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cmd.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (cmd.aliases.isNotEmpty()) {
                            Text(
                                text = "ALIASES: ${cmd.aliases.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissMuted
                            )
                        }
                    }
                }
                if (idx < matches.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SwissConcrete)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandHelpSheet(
    onRunCommand: (SlashCommandDef) -> Unit,
    onDismiss: () -> Unit
) {
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
                .heightIn(max = 640.dp)
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
                    Text("HELP // COMMAND REFERENCE", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                    Text("Slash Commands & Aliases", style = MaterialTheme.typography.titleLarge, color = SwissInk)
                }
                SwissIconCell(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close help",
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
                SlashCommandCategory.entries.forEach { category ->
                    val group = SlashCommandRegistry.all.filter { it.category == category }
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwissPanelHeader(
                                leftLabel = category.label,
                                rightLabel = "${group.size} CMDS"
                            )
                            group.forEachIndexed { idx, cmd ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            onRunCommand(cmd)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.width(96.dp)) {
                                        Text(
                                            text = cmd.primary,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = SwissSignalRed
                                        )
                                        if (cmd.aliases.isNotEmpty()) {
                                            Text(
                                                text = cmd.aliases.joinToString(" "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SwissMuted
                                            )
                                        }
                                    }
                                    Text(
                                        text = cmd.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissInk,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (idx < group.lastIndex) {
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissConcrete))
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "PERSONAS // Type @Coder, @Concise, @Translator, or @Reviewer to activate reusable system prompt presets.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissMuted
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSwitcherSheet(
    activeTheme: SwissThemePreset,
    onSelectTheme: (SwissThemePreset) -> Unit,
    onDismiss: () -> Unit
) {
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
                .heightIn(max = 620.dp)
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
                    Text("THEME // 3-COLOR SWISS PALETTES", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                    Text("UI Color Themes", style = MaterialTheme.typography.titleLarge, color = SwissInk)
                }
                SwissIconCell(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close themes",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SwissThemePreset.entries.forEachIndexed { idx, preset ->
                    val isSelected = preset == activeTheme
                    Surface(
                        color = if (isSelected) SwissInk else SwissPaper,
                        shape = RectangleShape,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) SwissSignalRed else SwissInk),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTheme(preset) }
                            .testTag("theme_option_${preset.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 3-color crisp swatch strip
                            Row(modifier = Modifier.border(1.dp, if (isSelected) SwissWhite else SwissInk, RectangleShape)) {
                                Box(modifier = Modifier.size(20.dp, 36.dp).background(preset.swatch.first))
                                Box(modifier = Modifier.size(20.dp, 36.dp).background(preset.swatch.second))
                                Box(modifier = Modifier.size(20.dp, 36.dp).background(preset.swatch.third))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "0${idx + 1} // ${preset.displayName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected) SwissWhite else SwissInk
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) SwissConcrete else SwissMuted
                                )
                            }
                            if (isSelected) {
                                SwissBadge(
                                    text = "ACTIVE",
                                    containerColor = SwissSignalRed,
                                    contentColor = SwissWhite,
                                    borderColor = SwissSignalRed
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServersSheet(
    servers: List<McpServerConfig>,
    checkResults: Map<String, McpCheckResult>,
    checkingIds: Set<String>,
    onAddServer: (name: String, url: String, token: String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onCheckServer: (McpServerConfig) -> Unit,
    onCheckAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
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
                .heightIn(max = 640.dp)
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
                    Text("MCP // MODEL CONTEXT PROTOCOL", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                    Text("MCP Servers (${servers.size})", style = MaterialTheme.typography.titleLarge, color = SwissInk)
                }
                SwissIconCell(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close MCP",
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
                        SwissPanelHeader(leftLabel = "ADD MCP ENDPOINT", rightLabel = "HTTP / SSE")
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Server Name (e.g. GitHub MCP)") },
                                singleLine = true,
                                shape = RectangleShape,
                                colors = swissTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("MCP Endpoint URL") },
                                placeholder = { Text("https://mcp.example.com/mcp") },
                                singleLine = true,
                                shape = RectangleShape,
                                colors = swissTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = { tokenInput = it },
                                label = { Text("Bearer Token (Optional)") },
                                singleLine = true,
                                shape = RectangleShape,
                                colors = swissTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SwissButton(
                                    text = "Add Server",
                                    leadingIcon = Icons.Filled.Add,
                                    variant = SwissButtonVariant.PRIMARY,
                                    enabled = nameInput.isNotBlank() && urlInput.isNotBlank(),
                                    minHeight = 42.dp,
                                    onClick = {
                                        onAddServer(nameInput, urlInput, tokenInput)
                                        nameInput = ""
                                        urlInput = ""
                                        tokenInput = ""
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (servers.isNotEmpty()) {
                                    SwissButton(
                                        text = "Check All",
                                        leadingIcon = Icons.Outlined.NetworkCheck,
                                        variant = SwissButtonVariant.INK,
                                        minHeight = 42.dp,
                                        onClick = onCheckAll
                                    )
                                }
                            }
                        }
                    }
                }

                if (servers.isEmpty()) {
                    SwissPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .swissGraphPaper()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("MCP // 00 SERVERS CONFIGURED", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                            Text(
                                "Add remote Streamable HTTP or SSE MCP servers above to inspect connectivity and JSON-RPC initialize status.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissMuted
                            )
                        }
                    }
                } else {
                    servers.forEach { srv ->
                        val result = checkResults[srv.id]
                        val checking = srv.id in checkingIds
                        SwissPanel(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(srv.name, style = MaterialTheme.typography.titleSmall, color = SwissInk)
                                        Text(
                                            srv.url,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SwissMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SwissIconCell(
                                            icon = Icons.Outlined.NetworkCheck,
                                            contentDescription = "Test MCP server",
                                            onClick = { onCheckServer(srv) },
                                            enabled = !checking,
                                            cellSize = 36.dp
                                        )
                                        SwissIconCell(
                                            icon = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete MCP server",
                                            onClick = { onDeleteServer(srv.id) },
                                            cellSize = 36.dp
                                        )
                                    }
                                }
                                if (checking) {
                                    Text("STATUS // PROBING MCP INITIALIZE…", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                                } else if (result != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (result.isOnline) SwissVerifiedTint else SwissSignalRedTint)
                                            .border(1.dp, if (result.isOnline) SwissVerifiedInk else SwissSignalRed, RectangleShape)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "${if (result.isOnline) "ONLINE" else "OFFLINE"} · ${result.latencyMs}ms // ${result.statusDetail}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (result.isOnline) SwissVerifiedInk else SwissInk
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun AgentsMdInitDialog(
    currentAgentsMd: String,
    isGenerating: Boolean,
    hasActiveModel: Boolean,
    onPickScanFile: () -> Unit,
    onGenerateWithAi: (projectBrief: String) -> Unit,
    onSaveAgentsMd: (String) -> Unit,
    onExportAgentsMd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var briefInput by remember { mutableStateOf("") }
    var contentDraft by remember(currentAgentsMd) { mutableStateOf(currentAgentsMd) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
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
                    Text("/INIT // PROJECT INSTRUCTIONS", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                    Text("AGENTS.md Workspace Spec", style = MaterialTheme.typography.titleLarge, color = SwissInk)
                }
                SwissIconCell(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close init",
                    onClick = onDismiss,
                    bordered = false,
                    cellSize = 52.dp,
                    modifier = Modifier.border(1.dp, SwissInk, RectangleShape)
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Active AGENTS.md rules are automatically injected into every chat request as project-level system instructions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )

                OutlinedTextField(
                    value = briefInput,
                    onValueChange = { briefInput = it },
                    label = { Text("Describe codebase / stack to generate AGENTS.md") },
                    placeholder = { Text("e.g. Kotlin + Jetpack Compose Android app with Room & OkHttp...") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwissButton(
                        text = if (isGenerating) "Scanning…" else "Generate AGENTS.md",
                        leadingIcon = Icons.Outlined.AutoAwesome,
                        variant = SwissButtonVariant.PRIMARY,
                        enabled = !isGenerating && hasActiveModel && briefInput.isNotBlank(),
                        minHeight = 42.dp,
                        onClick = { onGenerateWithAi(briefInput) },
                        modifier = Modifier.weight(1f)
                    )
                    SwissButton(
                        text = "Scan File",
                        leadingIcon = Icons.Outlined.Description,
                        variant = SwissButtonVariant.OUTLINE,
                        minHeight = 42.dp,
                        onClick = onPickScanFile
                    )
                }

                SwissRuleDivider(symbol = "*")

                OutlinedTextField(
                    value = contentDraft,
                    onValueChange = { contentDraft = it },
                    label = { Text("AGENTS.md Content (Active Workspace Instructions)") },
                    minLines = 10,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(SwissPaper),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissPaper)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SwissButton(
                    text = "Export .md",
                    leadingIcon = Icons.Outlined.Download,
                    variant = SwissButtonVariant.OUTLINE,
                    enabled = contentDraft.isNotBlank(),
                    minHeight = 42.dp,
                    onClick = { onExportAgentsMd(contentDraft) }
                )
                SwissButton(
                    text = "Save Active Rules",
                    variant = SwissButtonVariant.INK,
                    minHeight = 42.dp,
                    onClick = {
                        onSaveAgentsMd(contentDraft)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun PromptEditorDialog(
    initialText: String,
    onLoadIntoComposer: (String) -> Unit,
    onSendDirectly: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(initialText) { mutableStateOf(initialText) }
    val wordCount = remember(draft) {
        draft.trim().split(Regex("\\s+")).count { it.isNotBlank() }
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
            SwissPanelHeader(
                leftLabel = "/EDITOR // FULL-SCREEN PROMPT BUFFER",
                rightLabel = "${wordCount}W / ${draft.length}C",
                rightAccent = true
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissSubtleFill)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SwissToggleCell(
                    label = "+ CODE",
                    selected = false,
                    onClick = { draft = "$draft\n```\n\n```" }
                )
                SwissToggleCell(
                    label = "+ LIST",
                    selected = false,
                    onClick = { draft = "$draft\n- " }
                )
                SwissToggleCell(
                    label = "CLEAR",
                    selected = false,
                    onClick = { draft = "" }
                )
                Spacer(Modifier.weight(1f))
                SwissIconCell(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close editor",
                    onClick = onDismiss,
                    cellSize = 36.dp
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Draft a multi-paragraph prompt, specification, or code snippet…") },
                shape = RectangleShape,
                colors = swissTextFieldColors(SwissPaper),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(10.dp)
                    .testTag("full_prompt_editor_input")
            )

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissPaper)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SwissButton(
                    text = "Load to Bar",
                    variant = SwissButtonVariant.OUTLINE,
                    minHeight = 44.dp,
                    onClick = {
                        onLoadIntoComposer(draft)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                SwissButton(
                    text = "Send Prompt",
                    leadingIcon = Icons.AutoMirrored.Filled.Send,
                    variant = SwissButtonVariant.PRIMARY,
                    enabled = draft.isNotBlank(),
                    minHeight = 44.dp,
                    onClick = {
                        onSendDirectly(draft)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ShareSessionDialog(
    shareUrl: String,
    transcriptMarkdown: String,
    onSystemShare: () -> Unit,
    onUnshare: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SwissCanvas,
        shape = RectangleShape,
        title = {
            Column {
                Text("/SHARE // SESSION SNAPSHOT", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                Text("Share Session Link", style = MaterialTheme.typography.titleLarge, color = SwissInk)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(12.dp)
                ) {
                    Text(
                        text = shareUrl,
                        style = MaterialTheme.typography.labelLarge,
                        color = SwissWhite
                    )
                }
                Text(
                    text = "Session ID registered locally. Because AllChat is 100% BYOK & device-only with no third-party cloud server, use Share Transcript below to send the full Markdown snapshot via any Android app, or run /unshare to revoke.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwissButton(
                        text = if (copied) "Copied!" else "Copy Link",
                        leadingIcon = Icons.Outlined.ContentCopy,
                        variant = SwissButtonVariant.OUTLINE,
                        minHeight = 40.dp,
                        onClick = {
                            clipboard.setText(AnnotatedString("$shareUrl\n\n$transcriptMarkdown"))
                            copied = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SwissButton(
                        text = "Share Sheet",
                        leadingIcon = Icons.Outlined.Share,
                        variant = SwissButtonVariant.PRIMARY,
                        minHeight = 40.dp,
                        onClick = onSystemShare,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            SwissButton(
                text = "Done",
                variant = SwissButtonVariant.INK,
                minHeight = 40.dp,
                onClick = onDismiss
            )
        },
        dismissButton = {
            SwissButton(
                text = "Unshare",
                leadingIcon = Icons.Outlined.LinkOff,
                variant = SwissButtonVariant.OUTLINE,
                minHeight = 40.dp,
                onClick = {
                    onUnshare()
                    onDismiss()
                }
            )
        }
    )
}
