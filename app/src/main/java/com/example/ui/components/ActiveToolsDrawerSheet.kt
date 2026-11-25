package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ReasoningEffort
import com.example.domain.model.SelectableModel
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite

/**
 * Right-edge swipe-in Active Tools & Context Drawer for mobile thumb-gesture navigation.
 * Opened by swiping left from the right screen edge; dismissed by swiping right or tapping outside.
 */
@Composable
fun ActiveToolsDrawerSheet(
    visible: Boolean,
    selectedModel: SelectableModel?,
    pinnedModels: List<SelectableModel>,
    modelLatencyMs: Map<Long, Long>,
    memoryInjectionEnabled: Boolean,
    memoryCount: Int,
    pinnedMemoryCount: Int,
    webSearchEnabled: Boolean,
    toolsEnabled: Boolean,
    tokenDietEnabled: Boolean,
    ghostModeEnabled: Boolean,
    reasoningEffort: ReasoningEffort,
    onSelectModel: (SelectableModel) -> Unit,
    onToggleMemoryInjection: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleTools: () -> Unit,
    onToggleTokenDiet: () -> Unit,
    onToggleGhostMode: () -> Unit,
    onSelectReasoningEffort: (ReasoningEffort) -> Unit,
    onOpenCommandPalette: () -> Unit,
    onOpenMemoryBank: () -> Unit,
    onOpenLiveLens: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onDismiss)
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                modifier = Modifier
                    .width(304.dp)
                    .fillMaxHeight()
                    .background(SwissCanvas)
                    .border(1.5.dp, SwissInk, RectangleShape)
                    .navigationBarsPadding()
                    .pointerInput(Unit) {
                        var dragSum = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragSum = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragSum += dragAmount
                                if (dragSum > 72f) {
                                    dragSum = 0f
                                    onDismiss()
                                }
                            }
                        )
                    }
                    .testTag("active_tools_right_drawer")
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CTX // ACTIVE TOOLS & CONTEXT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SwissSignalRed
                        )
                        Text(
                            text = "Swipe right to close",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissWhite
                        )
                    }
                    SwissIconCell(
                        icon = Icons.Filled.Close,
                        contentDescription = "Close tools drawer",
                        onClick = onDismiss,
                        inverted = true,
                        bordered = false,
                        cellSize = 38.dp,
                        modifier = Modifier.testTag("close_tools_drawer_btn")
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick Command Palette Trigger
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper)
                            .border(1.dp, SwissInk, RectangleShape)
                            .clickable {
                                onDismiss()
                                onOpenCommandPalette()
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .testTag("drawer_open_command_palette"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = SwissSignalRed, modifier = Modifier.size(16.dp))
                        Text(
                            text = "CMD // QUICK-SWITCH (CTRL+K)",
                            style = MaterialTheme.typography.labelMedium,
                            color = SwissInk
                        )
                    }

                    // Pinned Favorite Models Quick Switcher
                    if (pinnedModels.isNotEmpty()) {
                        Text(
                            text = "PINNED MODELS (${pinnedModels.size}/4)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SwissMuted
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            pinnedModels.forEach { item ->
                                val isActive = item.model.id == selectedModel?.model?.id
                                val ms = modelLatencyMs[item.model.id]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isActive) SwissInk else SwissPaper, RectangleShape)
                                        .border(1.dp, if (isActive) SwissSignalRed else SwissInk, RectangleShape)
                                        .clickable {
                                            onSelectModel(item)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.PushPin,
                                            contentDescription = null,
                                            tint = SwissSignalRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.model.displayName,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isActive) SwissWhite else SwissInk,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.provider.name.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isActive) SwissConcrete else SwissMuted
                                            )
                                        }
                                    }
                                    if (ms != null) {
                                        Text(
                                            text = "${ms}ms",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isActive) SwissSignalRed else SwissInk,
                                            modifier = Modifier
                                                .background(if (isActive) SwissWhite else SwissSubtleFill, RectangleShape)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Context & Tool Toggles
                    Text(
                        text = "SESSION CONTEXT & TOOLS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SwissMuted
                    )

                    DrawerToggleRow(
                        label = "Personal Memory ($memoryCount · $pinnedMemoryCount pinned)",
                        checked = memoryInjectionEnabled,
                        onToggle = onToggleMemoryInjection,
                        testTag = "drawer_toggle_memory"
                    )
                    DrawerToggleRow(
                        label = "Live Web Search Grounding",
                        checked = webSearchEnabled,
                        onToggle = onToggleWebSearch,
                        testTag = "drawer_toggle_web"
                    )
                    DrawerToggleRow(
                        label = "Device & Client Tools",
                        checked = toolsEnabled,
                        onToggle = onToggleTools,
                        testTag = "drawer_toggle_tools"
                    )
                    DrawerToggleRow(
                        label = "Token Diet (Concise Output)",
                        checked = tokenDietEnabled,
                        onToggle = onToggleTokenDiet,
                        testTag = "drawer_toggle_diet"
                    )
                    DrawerToggleRow(
                        label = "Ghost Mode (No History Saved)",
                        checked = ghostModeEnabled,
                        onToggle = onToggleGhostMode,
                        testTag = "drawer_toggle_ghost"
                    )

                    // Reasoning Effort Selector
                    Text(
                        text = "REASONING EFFORT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SwissMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReasoningEffort.entries.forEach { level ->
                            val selected = reasoningEffort == level
                            Text(
                                text = level.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) SwissWhite else SwissInk,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (selected) SwissSignalRed else SwissPaper, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable { onSelectReasoningEffort(level) }
                                    .padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "QUICK LAUNCH",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SwissMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SwissButton(
                            text = "Live Lens",
                            leadingIcon = Icons.Outlined.CameraAlt,
                            variant = SwissButtonVariant.PRIMARY,
                            minHeight = 44.dp,
                            onClick = {
                                onDismiss()
                                onOpenLiveLens()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "Memory",
                            leadingIcon = Icons.Outlined.Memory,
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 44.dp,
                            onClick = {
                                onDismiss()
                                onOpenMemoryBank()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SwissButton(
                            text = "Knowledge",
                            leadingIcon = Icons.Outlined.LibraryBooks,
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 44.dp,
                            onClick = {
                                onDismiss()
                                onOpenKnowledge()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "Settings",
                            leadingIcon = Icons.Outlined.Settings,
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 44.dp,
                            onClick = {
                                onDismiss()
                                onOpenSettings()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SwissPaper)
            .border(1.dp, SwissInk, RectangleShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SwissInk,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = SwissWhite,
                checkedTrackColor = SwissSignalRed
            )
        )
    }
}
