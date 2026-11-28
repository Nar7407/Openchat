package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.MemoryProfile
import com.example.domain.model.UserMemoryInfo
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSignalRedTint
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite

/**
 * Dedicated "Saved Memory" manager section inside SettingsScreen.
 * Lists all stored cross-session memory entries with 1-tap Edit, Pin (Always Include),
 * Delete, Quick-Add, Auto-Extract toggle, and Clear All Memory reset.
 */
@Composable
fun SavedMemorySettingsPanel(
    memories: List<UserMemoryInfo>,
    autoExtractEnabled: Boolean,
    onToggleAutoExtract: (Boolean) -> Unit,
    onAddMemory: (fact: String, category: String, temporary7Days: Boolean) -> Unit,
    onUpdateMemory: (UserMemoryInfo) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
    onToggleEnabled: (Long, Boolean) -> Unit,
    onToggleTtl7Days: (Long, Boolean) -> Unit = { _, _ -> },
    onConsolidateMemories: () -> Unit = {},
    isConsolidating: Boolean = false,
    onDeleteMemory: (Long) -> Unit,
    onClearAllMemories: () -> Unit,
    modifier: Modifier = Modifier
) {
    var factDraft by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Preference") }
    var selectedProfile by remember { mutableStateOf(MemoryProfile.ALL) }
    var newFactTemporary7d by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<UserMemoryInfo?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val categories = listOf("Preference", "Tech", "Profile", "Workflow")
    val filteredMemories = remember(memories, selectedProfile) {
        if (selectedProfile == MemoryProfile.ALL) memories
        else memories.filter {
            it.profile.equals("ALL", ignoreCase = true) ||
                it.profile.equals(selectedProfile.name, ignoreCase = true)
        }
    }
    val pinnedCount = memories.count { it.isPinned }

    SwissPanel(modifier = modifier.fillMaxWidth().testTag("settings_saved_memory_panel")) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SwissPanelHeader(
                leftLabel = "MEM // SAVED MEMORY BANK",
                rightLabel = "${memories.size} SAVED · $pinnedCount PINNED",
                rightAccent = pinnedCount > 0,
                backgroundColor = SwissSubtleFill
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Auto-Extract Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper)
                        .border(1.dp, SwissInk, RectangleShape)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SILENT AUTO-EXTRACTOR",
                            style = MaterialTheme.typography.labelLarge,
                            color = SwissInk
                        )
                        Text(
                            text = "Silently saves durable facts, stack, and formatting preferences after chat turns.",
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
                        modifier = Modifier.testTag("settings_memory_auto_extract_switch")
                    )
                }

                // Profile Filter Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PROFILE:", style = MaterialTheme.typography.labelSmall, color = SwissMuted)
                    MemoryProfile.entries.forEach { prof ->
                        val active = selectedProfile == prof
                        Text(
                            text = prof.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (active) SwissWhite else SwissInk,
                            modifier = Modifier
                                .background(if (active) SwissSignalRed else SwissPaper, RectangleShape)
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable { selectedProfile = prof }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Add / Edit Fact Row + TTL Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { cat ->
                            val active = selectedCategory == cat
                            Text(
                                text = cat.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) SwissWhite else SwissInk,
                                modifier = Modifier
                                    .background(if (active) SwissInk else SwissPaper, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Text(
                        text = if (newFactTemporary7d) "7-DAY" else "PERM",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (newFactTemporary7d) SwissWhite else SwissInk,
                        modifier = Modifier
                            .background(if (newFactTemporary7d) SwissSignalRed else SwissPaper, RectangleShape)
                            .border(1.dp, SwissInk, RectangleShape)
                            .clickable { newFactTemporary7d = !newFactTemporary7d }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("settings_memory_new_ttl_toggle")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = factDraft,
                        onValueChange = { factDraft = it },
                        placeholder = { Text("Add a memory (e.g. Prefer concise Kotlin answers)") },
                        shape = RectangleShape,
                        maxLines = 2,
                        colors = swissTextFieldColors(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_memory_input")
                    )
                    SwissButton(
                        text = if (editingEntry != null) "Save" else "Add",
                        variant = SwissButtonVariant.PRIMARY,
                        minHeight = 48.dp,
                        onClick = {
                            val clean = factDraft.trim()
                            if (clean.isNotEmpty()) {
                                val target = editingEntry
                                if (target != null) {
                                    val updatedExpiry = if (newFactTemporary7d) {
                                        target.expiresAtMs ?: (System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L)
                                    } else null
                                    onUpdateMemory(
                                        target.copy(
                                            fact = clean,
                                            category = selectedCategory,
                                            expiresAtMs = updatedExpiry
                                        )
                                    )
                                    editingEntry = null
                                } else {
                                    onAddMemory(clean, selectedCategory, newFactTemporary7d)
                                }
                                factDraft = ""
                            }
                        },
                        modifier = Modifier.testTag("settings_memory_save_btn")
                    )
                }

                // Stored Memory Entries
                if (filteredMemories.isEmpty()) {
                    Text(
                        text = "NO SAVED MEMORIES IN [${selectedProfile.label}] // Chat naturally or add a preference above. Pinned facts are always prepended into every new session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper)
                            .border(1.dp, SwissInk, RectangleShape)
                            .padding(12.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredMemories.forEachIndexed { idx, mem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        when {
                                            mem.isPinned -> SwissSignalRedTint
                                            mem.isEnabled -> SwissPaper
                                            else -> SwissSubtleFill
                                        },
                                        RectangleShape
                                    )
                                    .border(
                                        width = if (mem.isPinned) 1.5.dp else 1.dp,
                                        color = if (mem.isPinned) SwissSignalRed else SwissInk,
                                        shape = RectangleShape
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("settings_memory_item_$idx"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onToggleEnabled(mem.id, !mem.isEnabled) }
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (mem.isPinned) {
                                            Text(
                                                text = "PINNED",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
                                                .clickable { onToggleTtl7Days(mem.id, !mem.isTemporary) }
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                                .testTag("settings_memory_ttl_$idx")
                                        )
                                        Text(
                                            text = if (mem.isEnabled) mem.source.uppercase() else "MUTED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SwissMuted
                                        )
                                    }
                                    Text(
                                        text = mem.fact,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (mem.isEnabled) SwissInk else SwissMuted
                                    )
                                }

                                // 1-Tap Pin (Always Include)
                                IconButton(
                                    onClick = { onTogglePin(mem.id, !mem.isPinned) },
                                    modifier = Modifier.testTag("settings_memory_pin_$idx")
                                ) {
                                    Icon(
                                        imageVector = if (mem.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = if (mem.isPinned) "Unpin memory" else "Pin memory to always include",
                                        tint = if (mem.isPinned) SwissSignalRed else SwissInk,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // 1-Tap Edit
                                IconButton(
                                    onClick = {
                                        editingEntry = mem
                                        factDraft = mem.fact
                                        selectedCategory = mem.category
                                        newFactTemporary7d = mem.isTemporary
                                    },
                                    modifier = Modifier.testTag("settings_memory_edit_$idx")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit memory",
                                        tint = SwissInk,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // 1-Tap Delete
                                IconButton(
                                    onClick = { onDeleteMemory(mem.id) },
                                    modifier = Modifier.testTag("settings_memory_delete_$idx")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete memory",
                                        tint = SwissSignalRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 1-Tap Memory Consolidator ([MERGE & CLEAN]) + Clear All Memory Reset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (memories.size >= 2) {
                            SwissButton(
                                text = if (isConsolidating) "Merging…" else "[ Merge & Clean ]",
                                variant = SwissButtonVariant.INK,
                                enabled = !isConsolidating,
                                minHeight = 44.dp,
                                onClick = onConsolidateMemories,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_memory_merge_clean_btn")
                            )
                        }
                        SwissButton(
                            text = "Clear All (${memories.size})",
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 44.dp,
                            onClick = { showClearConfirm = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_clear_all_memory_btn")
                        )
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
                Text(
                    text = "CLEAR ALL SAVED MEMORY?",
                    style = MaterialTheme.typography.titleMedium,
                    color = SwissInk
                )
            },
            text = {
                Text(
                    text = "This permanently deletes all ${memories.size} saved user facts and pinned preferences from AllChat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )
            },
            confirmButton = {
                SwissButton(
                    text = "Clear All",
                    variant = SwissButtonVariant.PRIMARY,
                    minHeight = 42.dp,
                    onClick = {
                        showClearConfirm = false
                        onClearAllMemories()
                    },
                    modifier = Modifier.testTag("confirm_clear_all_memory_btn")
                )
            },
            dismissButton = {
                SwissButton(
                    text = "Cancel",
                    variant = SwissButtonVariant.OUTLINE,
                    minHeight = 42.dp,
                    onClick = { showClearConfirm = false }
                )
            }
        )
    }
}
