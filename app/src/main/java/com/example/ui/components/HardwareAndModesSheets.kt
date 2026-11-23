package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.domain.model.WorkspaceFileEntry
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite
import com.example.util.BleInspectorController
import com.example.util.LocalWorkspaceManager
import com.example.util.MediaButtonBridge
import com.example.util.ReadAloudController
import com.example.util.VoiceInputController
import com.example.util.WakePhraseListener
import kotlinx.coroutines.launch
import java.util.Locale

// 1. Two-Person "Face-to-Face" Live Interpreter (Top half rotated 180 degrees)
@Composable
fun FaceToFaceInterpreterDialog(
    onTranslate: suspend (text: String, sourceLang: String, targetLang: String) -> Result<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val languages = listOf(
        "English" to "en-US",
        "Hindi" to "hi-IN",
        "Marathi" to "mr-IN",
        "Spanish" to "es-ES",
        "French" to "fr-FR",
        "Japanese" to "ja-JP"
    )
    var topLangIdx by remember { mutableStateOf(1) } // Hindi default for Guest
    var bottomLangIdx by remember { mutableStateOf(0) } // English default for You
    var topTranscript by remember { mutableStateOf("Tap Speak to talk in ${languages[topLangIdx].first}") }
    var bottomTranscript by remember { mutableStateOf("Tap Speak to talk in ${languages[bottomLangIdx].first}") }
    var activeSide by remember { mutableStateOf<String?>(null) }

    val tts = remember { ReadAloudController(context, onSpeakingIdChanged = {}) }
    DisposableEffect(Unit) { onDispose { tts.release() } }

    val stt = remember {
        VoiceInputController(
            context = context,
            onPartialText = { partial ->
                if (activeSide == "TOP") topTranscript = partial else bottomTranscript = partial
            },
            onFinalText = { spoken ->
                val side = activeSide ?: "BOTTOM"
                activeSide = null
                val (srcName, _) = if (side == "TOP") languages[topLangIdx] else languages[bottomLangIdx]
                val (tgtName, tgtTag) = if (side == "TOP") languages[bottomLangIdx] else languages[topLangIdx]
                if (side == "TOP") topTranscript = spoken else bottomTranscript = spoken
                scope.launch {
                    onTranslate(spoken, srcName, tgtName).onSuccess { translated ->
                        if (side == "TOP") bottomTranscript = translated else topTranscript = translated
                        tts.setLanguage(Locale.forLanguageTag(tgtTag))
                        tts.speak(System.currentTimeMillis(), translated)
                    }.onFailure { err ->
                        val msg = "Translation error: ${err.message}"
                        if (side == "TOP") bottomTranscript = msg else topTranscript = msg
                    }
                }
            },
            onListeningChanged = { listening -> if (!listening) activeSide = null },
            onFailure = { activeSide = null }
        )
    }
    DisposableEffect(Unit) { onDispose { stt.release() } }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val side = activeSide ?: "BOTTOM"
            val tag = if (side == "TOP") languages[topLangIdx].second else languages[bottomLangIdx].second
            stt.start(tag)
        }
    }

    fun startSideListening(side: String) {
        activeSide = side
        val tag = if (side == "TOP") languages[topLangIdx].second else languages[bottomLangIdx].second
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            stt.start(tag)
        } else {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().background(SwissCanvas).testTag("face_interpreter_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Half — Rotated 180 degrees for the person sitting across the table
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .rotate(180f)
                        .background(SwissPaper)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("GUEST // ${languages[topLangIdx].first.uppercase()}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissSignalRed)
                        SwissToggleCell(label = "LANG", selected = false, onClick = { topLangIdx = (topLangIdx + 1) % languages.size })
                    }
                    Text(topTranscript, style = MaterialTheme.typography.headlineSmall, color = SwissInk)
                    SwissButton(
                        text = if (activeSide == "TOP") "LISTENING…" else "SPEAK (${languages[topLangIdx].first})",
                        onClick = { startSideListening("TOP") },
                        leadingIcon = Icons.Default.Mic,
                        variant = if (activeSide == "TOP") SwissButtonVariant.PRIMARY else SwissButtonVariant.INK,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Center Divider Bar
                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissInk).padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FACE-TO-FACE LIVE INTERPRETER", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissWhite)
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close interpreter", onClick = onDismiss, inverted = true, bordered = false)
                }

                // Bottom Half — Upright for You
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(SwissCanvas)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("YOU // ${languages[bottomLangIdx].first.uppercase()}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissInk)
                        SwissToggleCell(label = "LANG", selected = false, onClick = { bottomLangIdx = (bottomLangIdx + 1) % languages.size })
                    }
                    Text(bottomTranscript, style = MaterialTheme.typography.headlineSmall, color = SwissInk)
                    SwissButton(
                        text = if (activeSide == "BOTTOM") "LISTENING…" else "SPEAK (${languages[bottomLangIdx].first})",
                        onClick = { startSideListening("BOTTOM") },
                        leadingIcon = Icons.Default.Mic,
                        variant = SwissButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// 2. Minimalist 4-Button Commute / Driving HUD + Bluetooth Earbuds MediaSession + "Hey AllChat" Wake Phrase
@Composable
fun CommuteHudDialog(
    wakePhraseEnabled: Boolean,
    lastAssistantReply: String,
    onSubmitVoicePrompt: (String) -> Unit,
    onReadLastReply: () -> Unit,
    onQuickNavigateHome: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hudStatus by remember { mutableStateOf("HUD READY // Tap TALK or press Bluetooth earbud play/pause") }
    var isListening by remember { mutableStateOf(false) }
    var hotwordActive by remember { mutableStateOf(wakePhraseEnabled) }

    val stt = remember {
        VoiceInputController(
            context = context,
            onPartialText = { hudStatus = "HEARING: $it" },
            onFinalText = { spoken ->
                hudStatus = "SENT: $spoken"
                onSubmitVoicePrompt(spoken)
            },
            onListeningChanged = { isListening = it },
            onFailure = { hudStatus = it }
        )
    }

    val wakeListener = remember {
        WakePhraseListener(
            context = context,
            onWakeDetected = { trailing ->
                if (trailing.isNotBlank()) {
                    hudStatus = "WAKE // $trailing"
                    onSubmitVoicePrompt(trailing)
                } else {
                    hudStatus = "WAKE // Listening…"
                    stt.start()
                }
            },
            onStatusChanged = { hudStatus = it }
        )
    }

    val earbudBridge = remember {
        MediaButtonBridge(
            context = context,
            onEarbudTriggerAsk = {
                hudStatus = "EARBUD TAP // Listening…"
                stt.start()
            },
            onEarbudNextAction = {
                hudStatus = "EARBUD NEXT // Reading latest reply…"
                onReadLastReply()
            }
        )
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) stt.start()
    }

    DisposableEffect(hotwordActive) {
        earbudBridge.start(context)
        if (hotwordActive && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            wakeListener.start()
        }
        onDispose {
            wakeListener.stop()
            earbudBridge.stop()
            stt.release()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().background(SwissInk).testTag("commute_hud_dialog"),
            color = SwissInk,
            shape = RectangleShape
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("COMMUTE HUD // HANDS-FREE", style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissWhite)
                        Text(hudStatus, style = MaterialTheme.typography.bodySmall, color = SwissCanvas, maxLines = 2)
                    }
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Exit Commute HUD", onClick = onDismiss, inverted = true)
                }

                // Giant high-contrast 2x2 button grid for effortless glances
                Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HudGiantButton(
                            title = if (isListening) "STOP MIC" else "TALK TO AI",
                            subtitle = "Voice Ask",
                            icon = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            accent = true,
                            onClick = {
                                if (isListening) {
                                    stt.stop()
                                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    stt.start()
                                } else {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                        HudGiantButton(
                            title = "READ ALOUD",
                            subtitle = "Play Last Reply",
                            icon = Icons.Default.VolumeUp,
                            accent = false,
                            onClick = onReadLastReply,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HudGiantButton(
                            title = if (hotwordActive) "HEY ALLCHAT: ON" else "HEY ALLCHAT: OFF",
                            subtitle = "Wake Phrase",
                            icon = Icons.Default.Headphones,
                            accent = hotwordActive,
                            onClick = {
                                hotwordActive = !hotwordActive
                                if (!hotwordActive) wakeListener.stop()
                            },
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                        HudGiantButton(
                            title = "NAVIGATE",
                            subtitle = "Open Maps",
                            icon = Icons.Default.Navigation,
                            accent = false,
                            onClick = onQuickNavigateHome,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                }

                if (lastAssistantReply.isNotBlank()) {
                    Text(
                        text = lastAssistantReply.take(220),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwissCanvas,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().border(1.dp, SwissCanvas).padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HudGiantButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(if (accent) SwissSignalRed else SwissPaper, RectangleShape)
            .border(2.dp, SwissWhite, RectangleShape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (accent) SwissWhite else SwissInk,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = if (accent) SwissWhite else SwissInk)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (accent) SwissCanvas else SwissMuted)
    }
}

// 3. Bluetooth LE (BLE) Hardware & Sensor Inspector (ESP32 / Arduino / GATT)
@Composable
fun BleInspectorDialog(
    onSendTelemetryToAi: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val controller = remember { BleInspectorController(context) }
    DisposableEffect(Unit) { onDispose { controller.release() } }

    val isScanning by controller.isScanning.collectAsState()
    val devices by controller.devices.collectAsState()
    val connectedName by controller.connectedName.collectAsState()
    val statusMessage by controller.statusMessage.collectAsState()
    val telemetry by controller.telemetryLogs.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) controller.startScan()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("ble_inspector_dialog"),
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
                        text = "BLE INSPECTOR // ESP32 & GATT SENSORS",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close BLE inspector", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(statusMessage, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily), color = SwissSignalRed)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissButton(
                            text = if (isScanning) "STOP SCAN" else "SCAN BLE DEVICES",
                            onClick = {
                                if (isScanning) {
                                    controller.stopScan()
                                } else if (controller.hasPermissions()) {
                                    controller.startScan()
                                } else {
                                    permLauncher.launch(controller.requiredPermissions())
                                }
                            },
                            leadingIcon = Icons.Default.BluetoothSearching,
                            variant = SwissButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f).testTag("ble_scan_btn")
                        )
                        if (connectedName != null) {
                            SwissButton(
                                text = "DISCONNECT",
                                onClick = { controller.disconnect() },
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                    }

                    if (devices.isNotEmpty() && connectedName == null) {
                        Text("DISCOVERED PERIPHERALS (${devices.size})", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissInk)
                        devices.take(12).forEach { dev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissPaper)
                                    .border(1.dp, SwissInk)
                                    .clickable { controller.connect(dev) }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dev.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = SwissInk)
                                    Text(dev.address, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily), color = SwissMuted)
                                }
                                Text("${dev.rssi} dBm", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissVerifiedInk)
                            }
                        }
                    }

                    if (telemetry.isNotEmpty()) {
                        SwissButton(
                            text = "SEND LIVE TELEMETRY TO AI (${telemetry.size})",
                            onClick = {
                                onSendTelemetryToAi(controller.formatTelemetryForAi())
                                onDismiss()
                            },
                            leadingIcon = Icons.Default.Send,
                            variant = SwissButtonVariant.INK,
                            modifier = Modifier.fillMaxWidth()
                        )
                        telemetry.take(12).forEach { line ->
                            Column(modifier = Modifier.fillMaxWidth().background(SwissVerifiedTint).border(1.dp, SwissInk).padding(8.dp)) {
                                Text("[${line.timestamp}] UUID ${line.characteristicUuid}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissVerifiedInk)
                                Text("HEX: ${line.hexPayload}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily), color = SwissInk)
                                Text("TXT: ${line.utf8Preview}", style = MaterialTheme.typography.bodySmall, color = SwissMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 4. Local Folder Workspace (SAF OpenDocumentTree + @file Picker)
@Composable
fun LocalWorkspaceDialog(
    workspaceUri: String?,
    files: List<WorkspaceFileEntry>,
    onMountFolder: (Uri) -> Unit,
    onRefreshFiles: () -> Unit,
    onInjectFileIntoPrompt: (fileName: String, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onMountFolder(uri)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("local_workspace_dialog"),
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
                        text = "LOCAL WORKSPACE // SAF @FILE MOUNT",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close local workspace", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissButton(
                            text = if (workspaceUri == null) "MOUNT FOLDER" else "CHANGE FOLDER",
                            onClick = { folderPicker.launch(null) },
                            leadingIcon = Icons.Default.FolderOpen,
                            variant = SwissButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f).testTag("workspace_mount_btn")
                        )
                        if (workspaceUri != null) {
                            SwissButton(text = "RESCAN", onClick = onRefreshFiles, modifier = Modifier.weight(0.7f))
                        }
                    }

                    if (files.isEmpty()) {
                        Text(
                            text = "Mount any local code or Markdown notes folder via Android SAF. Tap any file below to inject its contents directly into your AI prompt, or edit & write-back from Canvas Studio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissMuted
                        )
                    } else {
                        Text("FILES READY FOR @FILE INJECTION (${files.size})", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissInk)
                        files.take(40).forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissPaper)
                                    .border(1.dp, SwissInk)
                                    .clickable {
                                        scope.launch {
                                            val text = LocalWorkspaceManager.readFileContent(context, entry.uriString)
                                            onInjectFileIntoPrompt(entry.relativePath, text)
                                            onDismiss()
                                        }
                                    }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("@${entry.relativePath}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${(entry.sizeBytes / 1024L).coerceAtLeast(1)} KB", style = MaterialTheme.typography.labelSmall, color = SwissMuted)
                                }
                                Text("ATTACH", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissSignalRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
