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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssistantRoutine
import com.example.domain.model.RoutineStep
import com.example.domain.model.RoutineStepType
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissGridLine
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite
import java.util.UUID

@Composable
fun RoutinesSettingsPanel(
    routines: List<AssistantRoutine>,
    onSaveRoutine: (AssistantRoutine) -> Unit,
    onDeleteRoutine: (Long) -> Unit,
    onAddStarterPreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingId by remember { mutableStateOf(0L) }
    var routineName by remember { mutableStateOf("") }
    var triggerPhrase by remember { mutableStateOf("") }
    val draftSteps = remember {
        mutableStateListOf(
            RoutineStep(
                id = UUID.randomUUID().toString().take(8),
                type = RoutineStepType.AI_WEB_SEARCH,
                payload = ""
            )
        )
    }

    SwissPanel(modifier = modifier.fillMaxWidth()) {
        Column {
            SwissPanelHeader(
                leftLabel = "AUTOMATION // 1-TAP ROUTINES & MACROS",
                rightLabel = "${routines.size} SAVED",
                rightAccent = routines.isNotEmpty()
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Chain AI prompts, Web Search, Read-Aloud TTS, Android Device Actions, and Voice Notes into 1-tap macros triggered from Chat, Assistant Overlay, or the Home Screen Widget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )

                Text(
                    text = "QUICK STARTER TEMPLATES",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = SwissMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwissStarterPresetChip(
                        label = "+ Morning Briefing",
                        testTag = "starter_routine_morning",
                        onClick = { onAddStarterPreset("morning") }
                    )
                    SwissStarterPresetChip(
                        label = "+ Focus 25m",
                        testTag = "starter_routine_focus",
                        onClick = { onAddStarterPreset("focus") }
                    )
                    SwissStarterPresetChip(
                        label = "+ Commute Check",
                        testTag = "starter_routine_commute",
                        onClick = { onAddStarterPreset("commute") }
                    )
                }

                HorizontalDivider(color = SwissGridLine)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = routineName,
                        onValueChange = { routineName = it },
                        label = { Text("Routine Name") },
                        placeholder = { Text("Daily Brief") },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("routine_name_input")
                    )
                    OutlinedTextField(
                        value = triggerPhrase,
                        onValueChange = { triggerPhrase = it },
                        label = { Text("Trigger Phrase") },
                        placeholder = { Text("good morning") },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("routine_phrase_input")
                    )
                }

                Text(
                    text = "ACTION CHAIN (${draftSteps.size} STEP${if (draftSteps.size == 1) "" else "S"})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = SwissInk
                )

                draftSteps.forEachIndexed { idx, step ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissSubtleFill, RectangleShape)
                            .border(1.dp, SwissGridLine, RectangleShape)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = stepTypeIcon(step.type),
                                    contentDescription = step.type.label,
                                    tint = SwissSignalRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "STEP ${idx + 1} · ${step.type.label.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = SwissInk
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (idx > 0) {
                                    IconButton(
                                        onClick = {
                                            val moved = draftSteps.removeAt(idx)
                                            draftSteps.add(idx - 1, moved)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move step up", modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (idx < draftSteps.lastIndex) {
                                    IconButton(
                                        onClick = {
                                            val moved = draftSteps.removeAt(idx)
                                            draftSteps.add(idx + 1, moved)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move step down", modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (draftSteps.size > 1) {
                                    IconButton(
                                        onClick = { draftSteps.removeAt(idx) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Remove step", tint = SwissSignalRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RoutineStepType.entries.forEach { t ->
                                val selected = step.type == t
                                Box(
                                    modifier = Modifier
                                        .background(if (selected) SwissInk else SwissPaper, RectangleShape)
                                        .border(1.dp, if (selected) SwissInk else SwissGridLine, RectangleShape)
                                        .clickable { draftSteps[idx] = step.copy(type = t) }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = t.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = if (selected) SwissWhite else SwissInk
                                    )
                                }
                            }
                        }

                        val placeholderText = when (step.type) {
                            RoutineStepType.AI_PROMPT -> "Prompt (e.g. Give me 3 focus priorities)"
                            RoutineStepType.AI_WEB_SEARCH -> "Web search prompt (e.g. Latest AI headlines today)"
                            RoutineStepType.READ_ALOUD_TTS -> "Optional text (leave blank to speak previous AI result)"
                            RoutineStepType.DEVICE_ACTION -> "Device command (e.g. set timer for 25 min, navigate to work, open Spotify)"
                            RoutineStepType.SAVE_NOTE -> "Optional note heading (saves previous AI result as Markdown)"
                        }
                        OutlinedTextField(
                            value = step.payload,
                            onValueChange = { updated -> draftSteps[idx] = step.copy(payload = updated) },
                            placeholder = { Text(placeholderText, style = MaterialTheme.typography.bodySmall) },
                            shape = RectangleShape,
                            colors = swissTextFieldColors(containerColor = SwissPaper),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("routine_step_payload_$idx")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwissButton(
                        text = "Add Step",
                        leadingIcon = Icons.Outlined.Add,
                        variant = SwissButtonVariant.OUTLINE,
                        minHeight = 40.dp,
                        onClick = {
                            draftSteps.add(
                                RoutineStep(
                                    id = UUID.randomUUID().toString().take(8),
                                    type = RoutineStepType.DEVICE_ACTION,
                                    payload = ""
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("routine_add_step_button")
                    )
                    SwissButton(
                        text = if (editingId > 0L) "Update Routine" else "Save Routine",
                        variant = SwissButtonVariant.PRIMARY,
                        enabled = routineName.isNotBlank() && draftSteps.isNotEmpty(),
                        minHeight = 40.dp,
                        onClick = {
                            onSaveRoutine(
                                AssistantRoutine(
                                    id = editingId,
                                    name = routineName.trim(),
                                    triggerPhrase = triggerPhrase.trim().ifBlank { routineName.trim().lowercase() },
                                    steps = draftSteps.toList()
                                )
                            )
                            editingId = 0L
                            routineName = ""
                            triggerPhrase = ""
                            draftSteps.clear()
                            draftSteps.add(
                                RoutineStep(
                                    id = UUID.randomUUID().toString().take(8),
                                    type = RoutineStepType.AI_PROMPT,
                                    payload = ""
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("routine_save_button")
                    )
                }

                if (routines.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = SwissGridLine)
                    Text(
                        text = "SAVED ROUTINES (${routines.size}) — TAP TO EDIT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = SwissInk
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        routines.forEach { routine ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissCanvas, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable {
                                        editingId = routine.id
                                        routineName = routine.name
                                        triggerPhrase = routine.triggerPhrase
                                        draftSteps.clear()
                                        draftSteps.addAll(routine.steps)
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Bolt,
                                            contentDescription = null,
                                            tint = SwissSignalRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = routine.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = SwissInk
                                        )
                                    }
                                    Text(
                                        text = "Phrase: \"${routine.triggerPhrase}\" · ${routine.steps.size} steps (${routine.steps.joinToString(" → ") { it.type.label }})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = SwissMuted
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteRoutine(routine.id) },
                                    modifier = Modifier.testTag("delete_routine_${routine.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete routine ${routine.name}",
                                        tint = SwissSignalRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwissStarterPresetChip(
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(SwissPaper, RectangleShape)
            .border(1.dp, SwissInk, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = SwissInk
        )
    }
}

private fun stepTypeIcon(type: RoutineStepType): ImageVector = when (type) {
    RoutineStepType.AI_PROMPT -> Icons.Outlined.AutoAwesome
    RoutineStepType.AI_WEB_SEARCH -> Icons.Outlined.Public
    RoutineStepType.READ_ALOUD_TTS -> Icons.Outlined.VolumeUp
    RoutineStepType.DEVICE_ACTION -> Icons.Outlined.TouchApp
    RoutineStepType.SAVE_NOTE -> Icons.Outlined.NoteAdd
}
