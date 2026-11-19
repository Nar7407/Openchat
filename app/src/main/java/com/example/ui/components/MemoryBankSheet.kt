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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.UserMemoryInfo
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
    onToggleAutoExtract: (Boolean) -> Unit,
    onAddMemory: (fact: String, category: String) -> Unit,
    onUpdateMemory: (UserMemoryInfo) -> Unit,
    onToggleMemory: (Long, Boolean) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var factInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Preference") }
    var editingMemory by remember { mutableStateOf<UserMemoryInfo?>(null) }
    val categories = listOf("Preference", "Tech", "Profile", "Workflow")

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

                // Add / Edit Composer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissSubtleFill)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = factInput,
                            onValueChange = { factInput = it },
                            placeholder = { Text("e.g. I code in Kotlin/Compose and prefer concise bullet answers") },
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
                                            onUpdateMemory(editTarget.copy(fact = clean, category = selectedCategory))
                                            editingMemory = null
                                        } else {
                                            onAddMemory(clean, selectedCategory)
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
                    if (memories.isEmpty()) {
                        item {
                            Text(
                                text = "NO MEMORIES SAVED YET // Type /remember <fact> in Chat or add a preference above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissMuted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        items(memories, key = { it.id }) { mem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (mem.isEnabled) SwissPaper else SwissSubtleFill)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "[${mem.category.uppercase()}]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SwissSignalRed
                                        )
                                        Text(
                                            text = mem.source.uppercase(),
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
            }
        }
    }
}
