package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.ConversationEntity
import com.example.domain.model.SelectableModel
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite

private data class PaletteEntry(
    val id: String,
    val section: String,
    val title: String,
    val subtitle: String,
    val badge: String?,
    val icon: ImageVector,
    val isAccent: Boolean = false,
    val searchKey: String,
    val onExecute: () -> Unit
)

/**
 * Fast fuzzy scorer supporting subsequence matches, prefix boosts, and word-boundary matches
 * so jumping to any model, provider, session, or mode takes <= 2 keystrokes.
 */
private fun fuzzyPaletteScore(query: String, target: String): Int {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return 100
    val t = target.lowercase()
    if (t == q) return 1000
    if (t.startsWith(q)) return 850
    val wordIdx = t.indexOf(" $q")
    if (wordIdx >= 0) return 750 - wordIdx
    val subIdx = t.indexOf(q)
    if (subIdx >= 0) return 600 - subIdx

    // Subsequence fuzzy match (e.g., "ll" -> "Live Lens", "gq" -> "Groq")
    var qi = 0
    var score = 0
    var prevMatched = false
    for (ti in t.indices) {
        if (qi < q.length && t[ti] == q[qi]) {
            score += if (ti == 0 || t[ti - 1] == ' ' || t[ti - 1] == '/' || t[ti - 1] == '-') 35 else 15
            if (prevMatched) score += 20
            prevMatched = true
            qi++
        } else {
            prevMatched = false
        }
    }
    return if (qi == q.length) score else -1
}

@Composable
fun CommandPaletteDialog(
    availableModels: List<SelectableModel>,
    pinnedModelIds: List<Long>,
    modelLatencyMs: Map<Long, Long>,
    activeModelId: Long?,
    conversations: List<ConversationEntity>,
    memoryCount: Int,
    onSelectModel: (SelectableModel) -> Unit,
    onSelectConversation: (Long) -> Unit,
    onNewChat: () -> Unit,
    onOpenLiveLens: (Boolean) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArena: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenSummarize: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val allEntries = remember(availableModels, pinnedModelIds, modelLatencyMs, activeModelId, conversations, memoryCount) {
        buildList {
            // 1. Pinned Favorite Models
            pinnedModelIds.mapNotNull { id -> availableModels.firstOrNull { it.model.id == id } }
                .forEach { item ->
                    val ms = modelLatencyMs[item.model.id]
                    val badge = buildString {
                        if (ms != null) append("${ms}ms")
                        if (item.model.id == activeModelId) {
                            if (isNotEmpty()) append(" · ")
                            append("ACTIVE")
                        }
                    }.ifBlank { "PINNED" }
                    add(
                        PaletteEntry(
                            id = "pin_model_${item.model.id}",
                            section = "PINNED MODELS",
                            title = item.model.displayName,
                            subtitle = "${item.provider.name.uppercase()} / ${item.model.modelId}",
                            badge = badge,
                            icon = Icons.Filled.PushPin,
                            isAccent = true,
                            searchKey = "${item.model.displayName} ${item.provider.name} ${item.model.modelId} pinned model",
                            onExecute = {
                                onSelectModel(item)
                                onDismiss()
                            }
                        )
                    )
                }

            // 2. Modes & Quick Destinations
            add(
                PaletteEntry(
                    id = "mode_live_lens",
                    section = "MODES & VIEWS",
                    title = "Live Lens — Camera Vision & Solver",
                    subtitle = "Freeze-frame crop, step-by-step solver & scan enhance",
                    badge = "LENS",
                    icon = Icons.Outlined.CameraAlt,
                    isAccent = true,
                    searchKey = "live lens camera vision scan solve photo",
                    onExecute = {
                        onDismiss()
                        onOpenLiveLens(false)
                    }
                )
            )
            add(
                PaletteEntry(
                    id = "mode_translate_view",
                    section = "MODES & VIEWS",
                    title = "Live Lens — Translate View",
                    subtitle = "Visual camera translation overlay",
                    badge = "TRN",
                    icon = Icons.Outlined.Translate,
                    searchKey = "translate view live translator camera language",
                    onExecute = {
                        onDismiss()
                        onOpenLiveLens(true)
                    }
                )
            )
            add(
                PaletteEntry(
                    id = "mode_memory",
                    section = "MODES & VIEWS",
                    title = "Memory Bank ($memoryCount saved)",
                    subtitle = "Manage persistent user facts, pins & auto-extraction",
                    badge = "MEM",
                    icon = Icons.Outlined.Memory,
                    searchKey = "memory bank saved facts personalization remember",
                    onExecute = {
                        onDismiss()
                        onOpenMemory()
                    }
                )
            )
            add(
                PaletteEntry(
                    id = "mode_settings",
                    section = "MODES & VIEWS",
                    title = "Settings & Providers",
                    subtitle = "API keys, Saved Memory, fallback queues & security",
                    badge = "SET",
                    icon = Icons.Outlined.Settings,
                    searchKey = "settings providers api keys config preferences",
                    onExecute = {
                        onDismiss()
                        onOpenSettings()
                    }
                )
            )
            add(
                PaletteEntry(
                    id = "mode_new_chat",
                    section = "MODES & VIEWS",
                    title = "New Chat Session",
                    subtitle = "Start a fresh conversation thread",
                    badge = "NEW",
                    icon = Icons.Outlined.Add,
                    searchKey = "new chat fresh session clear",
                    onExecute = {
                        onNewChat()
                        onDismiss()
                    }
                )
            )
            add(
                PaletteEntry(
                    id = "mode_arena",
                    section = "MODES & VIEWS",
                    title = "Model Arena",
                    subtitle = "Compare two models side-by-side",
                    badge = "ARENA",
                    icon = Icons.Outlined.AutoAwesome,
                    searchKey = "arena compare models battle benchmark",
                    onExecute = {
                        onDismiss()
                        onOpenArena()
                    }
                )
            )
            add(
                PaletteEntry(
                    id = "mode_knowledge",
                    section = "MODES & VIEWS",
                    title = "Knowledge Base (Local RAG)",
                    subtitle = "Attach & query local documents with #tags",
                    badge = "RAG",
                    icon = Icons.Outlined.LibraryBooks,
                    searchKey = "knowledge base rag docs pdf files",
                    onExecute = {
                        onDismiss()
                        onOpenKnowledge()
                    }
                )
            )
            add(
                PaletteEntry(
                    id = "mode_summarize",
                    section = "MODES & VIEWS",
                    title = "Summarize Studio",
                    subtitle = "Structured document & text summaries",
                    badge = "SUM",
                    icon = Icons.Outlined.Summarize,
                    searchKey = "summarize studio tldr notes",
                    onExecute = {
                        onDismiss()
                        onOpenSummarize()
                    }
                )
            )

            // 3. Remaining Models & Providers
            val pinnedSet = pinnedModelIds.toSet()
            availableModels
                .filter { it.model.id !in pinnedSet }
                .forEach { item ->
                    val ms = modelLatencyMs[item.model.id]
                    val badge = ms?.let { "${it}ms" }
                        ?: if (item.model.id == activeModelId) "ACTIVE" else null
                    add(
                        PaletteEntry(
                            id = "model_${item.model.id}",
                            section = "MODELS & PROVIDERS",
                            title = item.model.displayName,
                            subtitle = "${item.provider.name.uppercase()} / ${item.model.modelId}",
                            badge = badge,
                            icon = Icons.Outlined.Psychology,
                            searchKey = "${item.model.displayName} ${item.provider.name} ${item.model.modelId}",
                            onExecute = {
                                onSelectModel(item)
                                onDismiss()
                            }
                        )
                    )
                }

            // 4. Saved Sessions
            conversations.take(25).forEach { conv ->
                add(
                    PaletteEntry(
                        id = "session_${conv.id}",
                        section = "SAVED SESSIONS",
                        title = conv.title.ifBlank { "Untitled Session" },
                        subtitle = "Session #${conv.id}",
                        badge = "CHAT",
                        icon = Icons.Outlined.Forum,
                        searchKey = "${conv.title} session chat history",
                        onExecute = {
                            onSelectConversation(conv.id)
                            onDismiss()
                        }
                    )
                )
            }
        }
    }

    val filtered = remember(allEntries, query) {
        if (query.isBlank()) {
            allEntries.take(22)
        } else {
            allEntries
                .mapNotNull { entry ->
                    val s = fuzzyPaletteScore(query, entry.searchKey)
                    if (s >= 0) entry to s else null
                }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(24)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = SwissCanvas,
            shape = RectangleShape,
            border = BorderStroke(2.dp, SwissInk),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 600.dp)
                .testTag("command_palette_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CMD // QUICK-SWITCH PALETTE (CTRL+K)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SwissSignalRed
                        )
                        Text(
                            text = "Jump to pinned models, providers, sessions, Live Lens, Memory, or Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SwissIconCell(
                        icon = Icons.Filled.Close,
                        contentDescription = "Close command palette",
                        onClick = onDismiss,
                        inverted = true,
                        bordered = false,
                        cellSize = 38.dp,
                        modifier = Modifier.testTag("command_palette_close")
                    )
                }

                // Fuzzy Search Input
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            "Type 2 letters (e.g. gr, le, me, se, or session name)…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = SwissSignalRed)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Text(
                                text = "CLR",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissInk,
                                modifier = Modifier
                                    .clickable { query = "" }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = { filtered.firstOrNull()?.onExecute?.invoke() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .focusRequester(focusRequester)
                        .testTag("command_palette_search_input")
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SwissInk)
                )

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO MATCHES FOR \"${query.uppercase()}\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = SwissMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        var lastSection: String? = null
                        items(filtered, key = { it.id }) { entry ->
                            val showSectionHeader = query.isBlank() && entry.section != lastSection
                            lastSection = entry.section

                            if (showSectionHeader) {
                                Text(
                                    text = entry.section,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SwissMuted,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SwissSubtleFill)
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissPaper)
                                    .clickable { entry.onExecute() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("palette_item_${entry.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = entry.icon,
                                    contentDescription = null,
                                    tint = if (entry.isAccent) SwissSignalRed else SwissInk,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SwissInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                entry.badge?.let { badgeText ->
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (entry.isAccent) SwissWhite else SwissInk,
                                        modifier = Modifier
                                            .background(if (entry.isAccent) SwissSignalRed else SwissConcrete, RectangleShape)
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
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
                    }
                }
            }
        }
    }
}
