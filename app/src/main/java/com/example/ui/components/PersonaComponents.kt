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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.PersonaInfo
import com.example.domain.model.SelectableModel
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite
import java.util.Locale

/**
 * Inline `@mention` / `/command` persona bar shown above the chat composer when
 * the user types `@` or `/`, or toggles quick-access personas.
 */
@Composable
fun PersonaCommandBar(
    personas: List<PersonaInfo>,
    activePersona: PersonaInfo?,
    filterQuery: String,
    onSelectPersona: (PersonaInfo?) -> Unit,
    onOpenManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanQuery = filterQuery.trim().removePrefix("@").removePrefix("/")
    val filtered = remember(personas, cleanQuery) {
        if (cleanQuery.isEmpty()) personas
        else personas.filter {
            it.handle.contains(cleanQuery, ignoreCase = true) ||
                it.name.contains(cleanQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissSubtleFill)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwissToggleCell(
                label = "DEFAULT",
                selected = activePersona == null,
                onClick = { onSelectPersona(null) }
            )
            filtered.forEach { persona ->
                SwissToggleCell(
                    label = "@${persona.handle}",
                    selected = activePersona?.id == persona.id,
                    useSignalRedWhenSelected = true,
                    onClick = { onSelectPersona(persona) },
                    modifier = Modifier.testTag("persona_chip_${persona.handle}")
                )
            }
            SwissToggleCell(
                label = "+ MANAGE",
                selected = false,
                onClick = onOpenManager,
                modifier = Modifier.testTag("open_persona_manager_chip")
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SwissInk)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaManagerSheet(
    personas: List<PersonaInfo>,
    activePersona: PersonaInfo?,
    availableModels: List<SelectableModel>,
    onSelectPersona: (PersonaInfo?) -> Unit,
    onSavePersona: (PersonaInfo) -> Unit,
    onDeletePersona: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var editingPersona by remember { mutableStateOf<PersonaInfo?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SwissCanvas,
        shape = RectangleShape,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SwissInk, RectangleShape)
        ) {
            SwissPanelHeader(
                leftLabel = "PERSONAS // SYSTEM PROMPT PRESETS",
                rightLabel = "${personas.size} SAVED",
                rightAccent = true
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwissButton(
                        text = "New Persona",
                        leadingIcon = Icons.Filled.Add,
                        variant = SwissButtonVariant.PRIMARY,
                        minHeight = 42.dp,
                        onClick = {
                            editingPersona = PersonaInfo(
                                id = 0,
                                handle = "",
                                name = "",
                                systemPrompt = "",
                                temperature = 0.7f,
                                maxTokens = 2048
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_persona_button")
                    )
                    if (activePersona != null) {
                        SwissButton(
                            text = "Clear Active",
                            leadingIcon = Icons.Filled.Close,
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 42.dp,
                            onClick = {
                                onSelectPersona(null)
                                onDismiss()
                            }
                        )
                    }
                }

                Text(
                    text = "TIP // Type @Coder or /Concise directly in the chat bar to switch presets on the fly.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissMuted
                )

                personas.forEach { persona ->
                    val isActive = activePersona?.id == persona.id
                    val defaultModelName = availableModels
                        .firstOrNull { it.model.id == persona.defaultModelDbId }
                        ?.model?.displayName

                    SwissPanel(
                        containerColor = if (isActive) SwissInk else SwissPaper,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectPersona(persona)
                                onDismiss()
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    SwissBadge(
                                        text = "@${persona.handle}",
                                        containerColor = if (isActive) SwissSignalRed else SwissInk,
                                        contentColor = SwissWhite,
                                        borderColor = if (isActive) SwissSignalRed else SwissInk
                                    )
                                    Text(
                                        text = persona.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isActive) SwissWhite else SwissInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SwissIconCell(
                                        icon = Icons.Outlined.Edit,
                                        contentDescription = "Edit persona",
                                        onClick = { editingPersona = persona },
                                        cellSize = 32.dp
                                    )
                                    SwissIconCell(
                                        icon = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete persona",
                                        onClick = { onDeletePersona(persona.id) },
                                        cellSize = 32.dp
                                    )
                                }
                            }
                            Text(
                                text = persona.systemPrompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isActive) SwissConcrete else SwissMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(String.format(Locale.US, "TEMP %.1f // MAX %d TOK", persona.temperature, persona.maxTokens))
                                    if (!defaultModelName.isNullOrBlank()) {
                                        append(" // $defaultModelName")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) SwissSignalRed else SwissMuted
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    editingPersona?.let { target ->
        PersonaEditorDialog(
            initial = target,
            availableModels = availableModels,
            onDismiss = { editingPersona = null },
            onSave = { updated ->
                onSavePersona(updated)
                editingPersona = null
            }
        )
    }
}

@Composable
private fun PersonaEditorDialog(
    initial: PersonaInfo,
    availableModels: List<SelectableModel>,
    onDismiss: () -> Unit,
    onSave: (PersonaInfo) -> Unit
) {
    var handle by remember(initial) { mutableStateOf(initial.handle) }
    var name by remember(initial) { mutableStateOf(initial.name) }
    var systemPrompt by remember(initial) { mutableStateOf(initial.systemPrompt) }
    var temperature by remember(initial) { mutableFloatStateOf(initial.temperature) }
    var maxTokensText by remember(initial) { mutableStateOf(initial.maxTokens.toString()) }
    var defaultModelId by remember(initial) { mutableStateOf(initial.defaultModelDbId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SwissCanvas,
        shape = RectangleShape,
        title = {
            Column {
                Text(
                    text = if (initial.id == 0L) "PERSONA // NEW PRESET" else "PERSONA // EDIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )
                Text(
                    text = "System Prompt Preset",
                    style = MaterialTheme.typography.titleLarge,
                    color = SwissInk
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("Command Handle (e.g. Coder)") },
                    prefix = { Text("@", style = MaterialTheme.typography.labelLarge) },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Title") },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt Instructions") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text(
                        text = String.format(Locale.US, "TEMPERATURE // %.2f", temperature),
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissInk
                    )
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..2f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = SwissSignalRed,
                            activeTrackColor = SwissInk,
                            inactiveTrackColor = SwissConcrete
                        )
                    )
                }

                OutlinedTextField(
                    value = maxTokensText,
                    onValueChange = { maxTokensText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Max Tokens (128 – 32768)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (availableModels.isNotEmpty()) {
                    Text(
                        text = "DEFAULT MODEL (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SwissToggleCell(
                            label = "KEEP CURRENT",
                            selected = defaultModelId == null,
                            onClick = { defaultModelId = null }
                        )
                        availableModels.take(12).forEach { m ->
                            SwissToggleCell(
                                label = m.model.displayName,
                                selected = defaultModelId == m.model.id,
                                useSignalRedWhenSelected = true,
                                onClick = { defaultModelId = m.model.id }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            SwissButton(
                text = "Save Preset",
                variant = SwissButtonVariant.PRIMARY,
                enabled = handle.isNotBlank() && systemPrompt.isNotBlank(),
                minHeight = 42.dp,
                onClick = {
                    val parsedMax = maxTokensText.toIntOrNull()?.coerceIn(128, 32768) ?: 2048
                    onSave(
                        initial.copy(
                            handle = handle,
                            name = name.ifBlank { handle },
                            systemPrompt = systemPrompt,
                            temperature = temperature,
                            maxTokens = parsedMax,
                            defaultModelDbId = defaultModelId
                        )
                    )
                }
            )
        },
        dismissButton = {
            SwissButton(
                text = "Cancel",
                variant = SwissButtonVariant.OUTLINE,
                minHeight = 42.dp,
                onClick = onDismiss
            )
        }
    )
}

@Composable
fun EditMessageForkDialog(
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirmFork: (String) -> Unit
) {
    var draft by remember(initialContent) { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SwissCanvas,
        shape = RectangleShape,
        title = {
            Column {
                Text(
                    text = "BRANCH // EDIT & FORK",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )
                Text(
                    text = "Fork Conversation Branch",
                    style = MaterialTheme.typography.titleLarge,
                    color = SwissInk
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Saving creates a new sibling branch (< 1 / 2 >) so your original thread and replies are preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    minLines = 3,
                    maxLines = 8,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fork_edit_input")
                )
            }
        },
        confirmButton = {
            SwissButton(
                text = "Save & Fork",
                variant = SwissButtonVariant.PRIMARY,
                enabled = draft.isNotBlank(),
                minHeight = 42.dp,
                onClick = { onConfirmFork(draft) },
                modifier = Modifier.testTag("fork_edit_confirm")
            )
        },
        dismissButton = {
            SwissButton(
                text = "Cancel",
                variant = SwissButtonVariant.OUTLINE,
                minHeight = 42.dp,
                onClick = onDismiss
            )
        }
    )
}
