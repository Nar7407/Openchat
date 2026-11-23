package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import com.example.util.DeviceAction
import com.example.util.DeviceActionEngine

@Composable
fun FloatingSidebarDock(
    isMiniWindowMode: Boolean,
    onToggleMiniWindowMode: () -> Unit,
    onOpenLiveTranslator: () -> Unit,
    onOpenScanScreen: () -> Unit,
    onOpenVoiceNotes: () -> Unit,
    onOpenAssistantOverlay: () -> Unit,
    onOpenMiniChat: () -> Unit,
    onTriggerDeviceAction: (DeviceAction) -> Unit,
    onOpenToolkitHub: () -> Unit = {},
    onOpenCommuteHud: () -> Unit = {},
    onOpenInterpreter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showAllToolsDrawer by remember { mutableStateOf(false) }

    fun launchImmediately(action: DeviceAction) {
        expanded = false
        DeviceActionEngine.execute(context, action).onFailure {
            onTriggerDeviceAction(action)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Slide-out ColorOS / Android Smart Sidebar Dark Vertical Dock
        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            Column(
                modifier = Modifier
                    .width(66.dp)
                    .background(SwissInk, RectangleShape)
                    .border(1.dp, SwissSignalRed, RectangleShape)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "DOCK",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )

                SidebarDockItem(
                    icon = Icons.Outlined.ScreenshotMonitor,
                    label = "SCAN",
                    accent = true,
                    onClick = {
                        expanded = false
                        onOpenScanScreen()
                    },
                    testTag = "sidebar_scan_screen"
                )
                SidebarDockItem(
                    icon = Icons.Outlined.Translate,
                    label = "LENS",
                    onClick = {
                        expanded = false
                        onOpenLiveTranslator()
                    },
                    testTag = "sidebar_live_translator"
                )
                SidebarDockItem(
                    icon = Icons.Outlined.GraphicEq,
                    label = "ASSIST",
                    onClick = {
                        expanded = false
                        onOpenAssistantOverlay()
                    },
                    testTag = "sidebar_assistant"
                )
                SidebarDockItem(
                    icon = Icons.Outlined.NoteAdd,
                    label = "NOTES",
                    onClick = {
                        expanded = false
                        onOpenVoiceNotes()
                    },
                    testTag = "sidebar_voice_notes"
                )
                SidebarDockItem(
                    icon = Icons.Outlined.Build,
                    label = "TOOLS",
                    accent = true,
                    onClick = {
                        expanded = false
                        onOpenToolkitHub()
                    },
                    testTag = "sidebar_power_toolkit"
                )
                SidebarDockItem(
                    icon = Icons.Outlined.DirectionsCar,
                    label = "HUD",
                    onClick = {
                        expanded = false
                        onOpenCommuteHud()
                    },
                    testTag = "sidebar_commute_hud"
                )
                SidebarDockItem(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = "WA",
                    onClick = {
                        launchImmediately(
                            DeviceAction.OpenApp("WhatsApp", "com.whatsapp", "whatsapp://send")
                        )
                    },
                    testTag = "sidebar_whatsapp"
                )
                SidebarDockItem(
                    icon = Icons.Outlined.PlayCircleOutline,
                    label = "YT",
                    onClick = {
                        launchImmediately(
                            DeviceAction.OpenApp("YouTube", "com.google.android.youtube", "vnd.youtube://")
                        )
                    },
                    testTag = "sidebar_youtube"
                )
                SidebarDockItem(
                    icon = Icons.Outlined.Call,
                    label = "DIAL",
                    onClick = {
                        launchImmediately(DeviceAction.DialPhone(""))
                    },
                    testTag = "sidebar_dialer"
                )
                SidebarDockItem(
                    icon = Icons.Outlined.Timer,
                    label = "5M",
                    onClick = {
                        launchImmediately(DeviceAction.SetTimer(300, "AllChat Quick Timer"))
                    },
                    testTag = "sidebar_timer"
                )
                SidebarDockItem(
                    icon = if (isMiniWindowMode) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess,
                    label = if (isMiniWindowMode) "FULL" else "MINI",
                    accent = isMiniWindowMode,
                    onClick = {
                        expanded = false
                        onToggleMiniWindowMode()
                        onOpenMiniChat()
                    },
                    testTag = "sidebar_mini_mode"
                )
                SidebarDockItem(
                    icon = Icons.Outlined.Apps,
                    label = "ALL",
                    onClick = {
                        expanded = false
                        showAllToolsDrawer = true
                    },
                    testTag = "sidebar_all_tools"
                )
            }
        }

        // Sleek Right-Edge Smart Sidebar Handle Pill
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(74.dp)
                .background(if (expanded) SwissSignalRed else SwissInk.copy(alpha = 0.85f), RectangleShape)
                .border(1.dp, SwissWhite.copy(alpha = 0.55f), RectangleShape)
                .clickable { expanded = !expanded }
                .testTag("floating_sidebar_handle"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .background(SwissWhite)
            )
        }
    }

    if (showAllToolsDrawer) {
        QuickToolsAndAppLauncherDialog(
            onOpenScanScreen = {
                showAllToolsDrawer = false
                onOpenScanScreen()
            },
            onOpenVoiceNotes = {
                showAllToolsDrawer = false
                onOpenVoiceNotes()
            },
            onOpenLiveTranslator = {
                showAllToolsDrawer = false
                onOpenLiveTranslator()
            },
            onOpenAssistantOverlay = {
                showAllToolsDrawer = false
                onOpenAssistantOverlay()
            },
            onOpenToolkitHub = {
                showAllToolsDrawer = false
                onOpenToolkitHub()
            },
            onOpenCommuteHud = {
                showAllToolsDrawer = false
                onOpenCommuteHud()
            },
            onOpenInterpreter = {
                showAllToolsDrawer = false
                onOpenInterpreter()
            },
            onToggleMiniWindowMode = {
                showAllToolsDrawer = false
                onToggleMiniWindowMode()
            },
            onExecuteAction = { action ->
                showAllToolsDrawer = false
                DeviceActionEngine.execute(context, action).onFailure {
                    onTriggerDeviceAction(action)
                }
            },
            onDismiss = { showAllToolsDrawer = false }
        )
    }
}

@Composable
private fun SidebarDockItem(
    icon: ImageVector,
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (accent) SwissSignalRed else SwissWhite.copy(alpha = 0.08f), RectangleShape)
            .border(1.dp, if (accent) SwissSignalRed else SwissWhite.copy(alpha = 0.22f), RectangleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = SwissWhite,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SwissWhite,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickToolsAndAppLauncherDialog(
    onOpenScanScreen: () -> Unit,
    onOpenVoiceNotes: () -> Unit,
    onOpenLiveTranslator: () -> Unit,
    onOpenAssistantOverlay: () -> Unit,
    onOpenToolkitHub: () -> Unit = {},
    onOpenCommuteHud: () -> Unit = {},
    onOpenInterpreter: () -> Unit = {},
    onToggleMiniWindowMode: () -> Unit,
    onExecuteAction: (DeviceAction) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var dialDraft by remember { mutableStateOf("") }

    val filteredApps = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            DeviceActionEngine.curatedApps
        } else {
            DeviceActionEngine.curatedApps.filter { app ->
                app.name.lowercase().contains(q) ||
                    app.category.lowercase().contains(q) ||
                    app.packageName?.lowercase()?.contains(q) == true ||
                    app.aliases.any { it.contains(q) }
            }
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
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SIDEBAR // ALL QUICK TOOLS & APPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed
                        )
                        Text(
                            text = "Quick App Launcher & Device Intents",
                            style = MaterialTheme.typography.titleMedium,
                            color = SwissWhite
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close quick launcher",
                        tint = SwissWhite,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(onClick = onDismiss)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search apps or enter package ID (com.example.app)…") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = SwissInk) },
                        singleLine = true,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_launcher_search")
                    )

                    // Smart AI & System Quick Tools Row
                    Text(
                        text = "01 // ALLCHAT FLOATING TOOLS",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SwissToggleCell(
                            label = "SCAN SCREEN",
                            selected = true,
                            onClick = onOpenScanScreen
                        )
                        SwissToggleCell(
                            label = "VOICE NOTES INBOX",
                            selected = false,
                            onClick = onOpenVoiceNotes
                        )
                        SwissToggleCell(
                            label = "LIVE LENS / SOLVE",
                            selected = false,
                            onClick = onOpenLiveTranslator
                        )
                        SwissToggleCell(
                            label = "VOICE ASSISTANT",
                            selected = false,
                            onClick = onOpenAssistantOverlay
                        )
                        SwissToggleCell(
                            label = "SMALL WINDOW MODE",
                            selected = false,
                            onClick = onToggleMiniWindowMode
                        )
                        SwissToggleCell(
                            label = "TIMER 5 MIN",
                            selected = false,
                            onClick = { onExecuteAction(DeviceAction.SetTimer(300, "5 Min Timer")) }
                        )
                        SwissToggleCell(
                            label = "POWER TOOLKIT (16)",
                            selected = true,
                            onClick = onOpenToolkitHub
                        )
                        SwissToggleCell(
                            label = "COMMUTE HUD",
                            selected = false,
                            onClick = onOpenCommuteHud
                        )
                        SwissToggleCell(
                            label = "FACE INTERPRETER",
                            selected = false,
                            onClick = onOpenInterpreter
                        )
                        SwissToggleCell(
                            label = "ALARM 07:00",
                            selected = false,
                            onClick = { onExecuteAction(DeviceAction.SetAlarm(7, 0, "Morning Alarm")) }
                        )
                    }

                    // Quick Dial Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = dialDraft,
                            onValueChange = { dialDraft = it },
                            placeholder = { Text("Quick dial phone number…") },
                            singleLine = true,
                            shape = RectangleShape,
                            colors = swissTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "Dial",
                            leadingIcon = Icons.Outlined.Call,
                            variant = SwissButtonVariant.INK,
                            minHeight = 52.dp,
                            onClick = {
                                onExecuteAction(DeviceAction.DialPhone(dialDraft.trim()))
                            }
                        )
                    }

                    // Direct Package Launch when user types a custom package name
                    if (searchQuery.trim().contains(".") && !searchQuery.trim().contains(" ")) {
                        SwissButton(
                            text = "Launch Package: ${searchQuery.trim()}",
                            leadingIcon = Icons.Outlined.Launch,
                            variant = SwissButtonVariant.PRIMARY,
                            onClick = {
                                val pkg = searchQuery.trim()
                                onExecuteAction(DeviceAction.OpenApp(pkg, pkg, null))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Curated Apps Grid
                    Text(
                        text = "02 // QUICK APP LAUNCHER (${filteredApps.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredApps.forEach { app ->
                            Column(
                                modifier = Modifier
                                    .width(104.dp)
                                    .background(SwissPaper, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable {
                                        onExecuteAction(
                                            DeviceAction.OpenApp(
                                                appName = app.name,
                                                packageName = app.packageName,
                                                fallbackUri = app.fallbackUri
                                            )
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                                    .testTag("launch_app_${app.id}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = app.category.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed,
                                    maxLines = 1
                                )
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SwissInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
