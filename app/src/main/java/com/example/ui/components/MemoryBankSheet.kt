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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.MemoryProfile
import com.example.domain.model.UserMemoryInfo
import com.example.util.SemanticEmbeddingEngine
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite

@Composable
fun MemoryBankDialog(
    memories: List<UserMemoryInfo>,
    autoExtractEnabled: Boolean,
    activeProfile: MemoryProfile = MemoryProfile.ALL,
    semanticAutoAttachEnabled: Boolean = false,
    onSelectProfile: (MemoryProfile) -> Unit = {},
    onToggleSemanticAutoAttach: (Boolean) -> Unit = {},
    onToggleAutoExtract: (Boolean) -> Unit,
    onAddMemory: (fact: String, category: String, profile: String, temporary7Days: Boolean) -> Unit,
    onUpdateMemory: (UserMemoryInfo) -> Unit,
    onToggleMemory: (Long, Boolean) -> Unit,
    onTogglePinMemory: (Long, Boolean) -> Unit = { _, _ -> },
    onToggleMemoryTtl7Days: (Long, Boolean) -> Unit = { _, _ -> },
    onConsolidateMemories: () -> Unit = {},
    isConsolidating: Boolean = false,
    onDeleteMemory: (Long) -> Unit,
    onClearAllMemories: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var factInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Preference") }
    var selectedFactProfile by remember(activeProfile) { mutableStateOf(activeProfile.name) }
    var newFactTemporary7d by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<UserMemoryInfo?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var semanticMode by remember { mutableStateOf(true) }
    var semanticRanked by remember { mutableStateOf<List<Pair<UserMemoryInfo, Int>>>(emptyList()) }
    val categories = listOf("Preference", "Tech", "Profile", "Workflow")
    val profileScopedMemories = remember(memories, activeProfile) {
        if (activeProfile == MemoryProfile.ALL) memories
        else memories.filter {
            it.profile.equals("ALL", ignoreCase = true) ||
                it.profile.equals(activeProfile.name, ignoreCase = true)
        }
    }

    LaunchedEffect(searchQuery, semanticMode, profileScopedMemories) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            semanticRanked = profileScopedMemories.map { it to 100 }
        } else if (semanticMode) {
            semanticRanked = SemanticEmbeddingEngine.rankMemories(q, profileScopedMemories)
        } else {
            semanticRanked = profileScopedMemories
                .filter {
                    it.fact.contains(q, ignoreCase = true) ||
                        it.category.contains(q, ignoreCase = true) ||
                        it.profile.contains(q, ignoreCase = true)
                }
                .map { it to 100 }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = SwissCanvas,
            shape = RectangleShape,
            border = BorderStroke(1.dp, SwissInk),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .heightIn(max = 680.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MEMORY BANK // PERSISTENT USER CONTEXT", style = MaterialTheme.typography.labelLarge, color = SwissWhite)
                        Text(
                            "${memories.count { it.isEnabled }} active of ${memories.size} facts injected into system prompt",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissCanvas
                        )
                    }
                    Box(
                        modifier = Modifier
                            .border(1.dp, SwissWhite, RectangleShape)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("close_memory_dialog")
                    ) {
                        Text("[ CLOSE ]", style = MaterialTheme.typography.labelSmall, color = SwissWhite)
                    }
                }

                // Auto-Extract Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper)
                        .border(1.dp, SwissInk, RectangleShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AUTO-EXTRACT MEMORIES", style = MaterialTheme.typography.labelLarge, color = SwissInk)
                        Text(
                            "Silently extract preferences & tech stack after chat replies (or use /remember)",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissMuted
                        )
                    }
                    Switch(
                        checked = autoExtractEnabled,
                        onCheckedChange = onToggleAutoExtract,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SwissWhite,
                            checkedTrackColor = SwissSignalRed
                        ),
                        modifier = Modifier.testTag("toggle_auto_extract_memory")
                    )
                }

                // Profile Filter Bar (STUDY / WORK / PERSONAL / ALL) + Semantic Search Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PROFILE:", style = MaterialTheme.typography.labelSmall, color = SwissMuted)
                        MemoryProfile.entries.forEach { prof ->
                            val isSelected = activeProfile == prof
                            Text(
                                text = prof.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) SwissWhite else SwissInk,
                                modifier = Modifier
                                    .background(if (isSelected) SwissSignalRed else SwissSubtleFill, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable { onSelectProfile(prof) }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                                    .testTag("memory_profile_tab_${prof.name.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    if (semanticMode) "Semantic concept search…" else "Keyword search…",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RectangleShape,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("memory_semantic_search_input")
                        )
                        Text(
                            text = if (semanticMode) "SEMANTIC" else "KEYWORD",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (semanticMode) SwissWhite else SwissInk,
                            modifier = Modifier
                                .background(if (semanticMode) SwissInk else SwissPaper, RectangleShape)
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable { semanticMode = !semanticMode }
                                .padding(horizontal = 8.dp, vertical = 14.dp)
                                .testTag("memory_semantic_mode_toggle")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSemanticAutoAttach(!semanticAutoAttachEnabled) }
                            .testTag("memory_semantic_auto_attach_toggle"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SEMANTIC AUTO-ATTACH TOP MATCH ON SEND",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (semanticAutoAttachEnabled) SwissSignalRed else SwissMuted
                        )
                        Text(
                            text = if (semanticAutoAttachEnabled) "[ ON ]" else "[ OFF ]",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (semanticAutoAttachEnabled) SwissSignalRed else SwissInk
                        )
                    }
                }

                // Add / Edit Composer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissSubtleFill)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categories.forEach { cat ->
                                val active = selectedCategory == cat
                                Box(
                                    modifier = Modifier
                                        .background(if (active) SwissInk else SwissPaper)
                                        .border(1.dp, SwissInk, RectangleShape)
                                        .clickable { selectedCategory = cat }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(cat.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (active) SwissWhite else SwissInk)
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(if (newFactTemporary7d) SwissSignalRed else SwissPaper)
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable { newFactTemporary7d = !newFactTemporary7d }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("memory_new_ttl_toggle")
                        ) {
                            Text(
                                text = if (newFactTemporary7d) "TTL: 7-DAY" else "TTL: PERM",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (newFactTemporary7d) SwissWhite else SwissInk
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = factInput,
                            onValueChange = { factInput = it },
                            placeholder = { Text("Add fact to [$selectedFactProfile] profile (contradictions auto-overwrite)") },
                            shape = RectangleShape,
                            singleLine = false,
                            maxLines = 2,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("memory_fact_input")
                        )
                        Box(
                            modifier = Modifier
                                .background(SwissSignalRed)
                                .clickable {
                                    val clean = factInput.trim()
                                    if (clean.isNotEmpty()) {
                                        val editTarget = editingMemory
                                        if (editTarget != null) {
                                            val updatedExpiry = if (newFactTemporary7d) {
                                                editTarget.expiresAtMs ?: (System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L)
                                            } else null
                                            onUpdateMemory(
                                                editTarget.copy(
                                                    fact = clean,
                                                    category = selectedCategory,
                                                    profile = selectedFactProfile,
                                                    expiresAtMs = updatedExpiry
                                                )
                                            )
                                            editingMemory = null
                                        } else {
                                            onAddMemory(clean, selectedCategory, selectedFactProfile, newFactTemporary7d)
                                        }
                                        factInput = ""
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                                .testTag("save_memory_button")
                        ) {
                            Text(
                                text = if (editingMemory != null) "SAVE" else "ADD",
                                style = MaterialTheme.typography.labelLarge,
                                color = SwissWhite
                            )
                        }
                    }
                }

                // Stored Memories List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (semanticRanked.isEmpty()) {
                        item {
                            Text(
                                text = if (memories.isEmpty()) {
                                    "NO MEMORIES SAVED YET // Type /remember <fact> in Chat or add a preference above."
                                } else {
                                    "NO MATCHES IN [${activeProfile.label}] PROFILE // Try switching profile to ALL or adjusting query."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissMuted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        items(semanticRanked, key = { it.first.id }) { (mem, matchPct) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (mem.isEnabled) SwissPaper else SwissSubtleFill)
                                    .border(
                                        width = if (mem.isPinned) 1.5.dp else 1.dp,
                                        color = if (mem.isPinned) SwissSignalRed else SwissInk,
                                        shape = RectangleShape
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (mem.isPinned) {
                                            Text(
                                                text = "PINNED",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SwissWhite,
                                                modifier = Modifier
                                                    .background(SwissSignalRed, RectangleShape)
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = "[${mem.profile}]",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = SwissInk
                                        )
                                        Text(
                                            text = "[${mem.category.uppercase()}]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SwissSignalRed
                                        )
                                        Text(
                                            text = if (mem.isTemporary) {
                                                val daysLeft = mem.remainingDays() ?: 1
                                                "7D · ${daysLeft}d"
                                            } else {
                                                "PERM"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (mem.isTemporary) SwissWhite else SwissInk,
                                            modifier = Modifier
                                                .background(if (mem.isTemporary) SwissInk else SwissSubtleFill, RectangleShape)
                                                .border(1.dp, SwissInk, RectangleShape)
                                                .clickable { onToggleMemoryTtl7Days(mem.id, !mem.isTemporary) }
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                .testTag("memory_ttl_toggle_${mem.id}")
                                        )
                                        if (searchQuery.isNotBlank() && semanticMode) {
                                            Text(
                                                text = "${matchPct}% MATCH",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = SwissSignalRed
                                            )
                                        } else {
                                            Text(
                                                text = mem.source.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SwissMuted
                                            )
                                        }
                                    }
                                    Text(
                                        text = mem.fact,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (mem.isEnabled) SwissInk else SwissMuted
                                    )
                                }
                                IconButton(
                                    onClick = { onTogglePinMemory(mem.id, !mem.isPinned) },
                                    modifier = Modifier.testTag("memory_dialog_pin_${mem.id}")
                                ) {
                                    Icon(
                                        imageVector = if (mem.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = if (mem.isPinned) "Unpin memory" else "Pin memory",
                                        tint = if (mem.isPinned) SwissSignalRed else SwissInk
                                    )
                                }
                                Switch(
                                    checked = mem.isEnabled,
                                    onCheckedChange = { onToggleMemory(mem.id, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SwissWhite,
                                        checkedTrackColor = SwissInk
                                    )
                                )
                                IconButton(onClick = {
                                    editingMemory = mem
                                    factInput = mem.fact
                                    selectedCategory = mem.category
                                    newFactTemporary7d = mem.isTemporary
                                }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit memory", tint = SwissInk)
                                }
                                IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete memory", tint = SwissSignalRed)
                                }
                            }
                        }
                    }
                }

                if (memories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper)
                            .border(1.dp, SwissInk, RectangleShape)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${memories.count { it.isPinned }} pinned · ${memories.count { it.isTemporary }} temp",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissMuted
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (memories.size >= 2) {
                                Text(
                                    text = if (isConsolidating) "[ MERGING… ]" else "[ MERGE & CLEAN ]",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SwissWhite,
                                    modifier = Modifier
                                        .background(if (isConsolidating) SwissMuted else SwissInk, RectangleShape)
                                        .clickable(enabled = !isConsolidating, onClick = onConsolidateMemories)
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                        .testTag("memory_merge_clean_button")
                                )
                            }
                            Text(
                                text = "[ CLEAR ALL ]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                modifier = Modifier
                                    .clickable { showClearConfirm = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("memory_dialog_clear_all")
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = SwissCanvas,
            shape = RectangleShape,
            title = {
                Text("CLEAR ALL MEMORY?", style = MaterialTheme.typography.titleMedium, color = SwissInk)
            },
            text = {
                Text(
                    "Remove all ${memories.size} saved user facts and pinned preferences?",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )
            },
            confirmButton = {
                SwissButton(
                    text = "Clear All",
                    variant = SwissButtonVariant.PRIMARY,
                    minHeight = 40.dp,
                    onClick = {
                        showClearConfirm = false
                        onClearAllMemories()
                    }
                )
            },
            dismissButton = {
                SwissButton(
                    text = "Cancel",
                    variant = SwissButtonVariant.OUTLINE,
                    minHeight = 40.dp,
                    onClick = { showClearConfirm = false }
                )
            }
        )
    }
}
