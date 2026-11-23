package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.model.SmartZone
import com.example.domain.model.WebhookActionInfo
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite
import com.example.util.SmartContextEngine
import com.example.util.TtsPlaybackState
import java.util.Locale
import java.util.UUID

// 1. Read-Aloud Karaoke Mini Audio Player Bar (0.75x - 2.0x, Rewind/Skip, Live Highlighted Sentence)
@Composable
fun ReadAloudKaraokeBar(
    state: TtsPlaybackState,
    ambientDbLabel: String?,
    onTogglePauseResume: () -> Unit,
    onRewind: () -> Unit,
    onSkip: () -> Unit,
    onCycleSpeed: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isActive) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissInk, RectangleShape)
            .border(1.5.dp, SwissSignalRed, RectangleShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("read_aloud_karaoke_bar"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "READ ALOUD // ${state.sentenceIndex + 1}/${state.totalSentences.coerceAtLeast(1)}${if (!ambientDbLabel.isNullOrBlank()) " · $ambientDbLabel" else ""}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                color = SwissSignalRed
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                SwissToggleCell(
                    label = String.format(Locale.US, "%.2gx", state.speed),
                    selected = state.speed != 1.0f,
                    onClick = onCycleSpeed,
                    useSignalRedWhenSelected = true
                )
                SwissIconCell(icon = Icons.Default.FastRewind, contentDescription = "Previous sentence", onClick = onRewind, inverted = true, bordered = false, cellSize = 48.dp)
                SwissIconCell(
                    icon = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (state.isPaused) "Resume" else "Pause",
                    onClick = onTogglePauseResume,
                    accent = true,
                    cellSize = 48.dp
                )
                SwissIconCell(icon = Icons.Default.FastForward, contentDescription = "Next sentence", onClick = onSkip, inverted = true, bordered = false, cellSize = 48.dp)
                SwissIconCell(icon = Icons.Default.Stop, contentDescription = "Stop reading", onClick = onStop, inverted = true, bordered = false, cellSize = 48.dp)
            }
        }
        if (state.currentSentence.isNotBlank()) {
            Text(
                text = "▸ ${state.currentSentence}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = SwissWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 2. Settings Panel — Smart Triggers (Time Profiles, GPS Geofence Zones, Eco Mode, Whisper, Auto-Delete 7d)
@Composable
fun SmartTriggersSettingsPanel(
    timeProfilesEnabled: Boolean,
    autoEcoMode: Boolean,
    autoDeleteJunkChats: Boolean,
    wakePhraseEnabled: Boolean,
    ambientWhisperEnabled: Boolean,
    smartZones: List<SmartZone>,
    onToggleTimeProfiles: (Boolean) -> Unit,
    onToggleAutoEco: (Boolean) -> Unit,
    onToggleAutoDeleteJunk: (Boolean) -> Unit,
    onPruneJunkNow: () -> Unit,
    onToggleWakePhrase: (Boolean) -> Unit,
    onToggleAmbientWhisper: (Boolean) -> Unit,
    onSaveSmartZone: (SmartZone) -> Unit,
    onDeleteSmartZone: (String) -> Unit
) {
    val context = LocalContext.current
    var zoneName by remember { mutableStateOf("") }
    var zoneChips by remember { mutableStateOf("Summarize lecture, Solve math step-by-step") }
    var locationNotice by remember { mutableStateOf<String?>(null) }

    val locPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            val loc = SmartContextEngine.getLastKnownLocation(context)
            if (loc != null && zoneName.isNotBlank()) {
                onSaveSmartZone(
                    SmartZone(
                        id = UUID.randomUUID().toString(),
                        name = zoneName.trim(),
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        radiusMeters = 250f,
                        quickChips = zoneChips.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(3)
                    )
                )
                zoneName = ""
                locationNotice = "Pinned current GPS coordinates."
            } else {
                locationNotice = "GPS fix unavailable yet — open Maps briefly and retry."
            }
        } else {
            locationNotice = "Location permission denied."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SwissPaper, RectangleShape)
            .border(1.5.dp, SwissInk, RectangleShape)
            .padding(14.dp)
            .testTag("smart_triggers_settings_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "SMART TRIGGERS // CONTEXT & AUTOMATION",
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
            color = SwissInk
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SwissToggleCell(label = "TIME PROFILES", selected = timeProfilesEnabled, onClick = { onToggleTimeProfiles(!timeProfilesEnabled) }, useSignalRedWhenSelected = true, modifier = Modifier.weight(1f))
            SwissToggleCell(label = "AUTO ECO", selected = autoEcoMode, onClick = { onToggleAutoEco(!autoEcoMode) }, useSignalRedWhenSelected = true, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SwissToggleCell(label = "HEY ALLCHAT", selected = wakePhraseEnabled, onClick = { onToggleWakePhrase(!wakePhraseEnabled) }, useSignalRedWhenSelected = true, modifier = Modifier.weight(1f))
            SwissToggleCell(label = "WHISPER dB", selected = ambientWhisperEnabled, onClick = { onToggleAmbientWhisper(!ambientWhisperEnabled) }, useSignalRedWhenSelected = true, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SwissToggleCell(label = "AUTO-PRUNE 7D JUNK", selected = autoDeleteJunkChats, onClick = { onToggleAutoDeleteJunk(!autoDeleteJunkChats) }, useSignalRedWhenSelected = true, modifier = Modifier.weight(1.2f))
            SwissButton(text = "PRUNE NOW", onClick = onPruneJunkNow, modifier = Modifier.weight(0.8f))
        }

        Spacer(Modifier.height(4.dp))
        Text("GPS GEOFENCE ZONES (250m RADIUS)", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissMuted)
        OutlinedTextField(
            value = zoneName,
            onValueChange = { zoneName = it },
            label = { Text("Zone name (e.g. College, Home, Office)") },
            singleLine = true,
            colors = swissTextFieldColors(),
            shape = RectangleShape,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = zoneChips,
            onValueChange = { zoneChips = it },
            label = { Text("Quick-Chips (comma separated)") },
            singleLine = true,
            colors = swissTextFieldColors(),
            shape = RectangleShape,
            modifier = Modifier.fillMaxWidth()
        )
        SwissButton(
            text = "PIN CURRENT LOCATION AS ZONE",
            onClick = {
                if (zoneName.isBlank()) {
                    locationNotice = "Enter a zone name first."
                    return@SwissButton
                }
                val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasLoc) {
                    val loc = SmartContextEngine.getLastKnownLocation(context)
                    if (loc != null) {
                        onSaveSmartZone(
                            SmartZone(
                                id = UUID.randomUUID().toString(),
                                name = zoneName.trim(),
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                radiusMeters = 250f,
                                quickChips = zoneChips.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(3)
                            )
                        )
                        zoneName = ""
                        locationNotice = "Pinned GPS zone."
                    } else {
                        locationNotice = "No cached GPS fix yet."
                    }
                } else {
                    locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }
            },
            leadingIcon = Icons.Default.MyLocation,
            variant = SwissButtonVariant.OUTLINE,
            modifier = Modifier.fillMaxWidth()
        )
        locationNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SwissSignalRed) }

        smartZones.forEach { z ->
            Row(
                modifier = Modifier.fillMaxWidth().background(SwissCanvas).border(1.dp, SwissInk).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(z.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = SwissInk)
                    Text("${String.format(Locale.US, "%.4f, %.4f", z.latitude, z.longitude)} · ${z.quickChips.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = SwissMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SwissIconCell(icon = Icons.Default.Delete, contentDescription = "Delete zone", onClick = { onDeleteSmartZone(z.id) }, bordered = false, cellSize = 48.dp)
            }
        }
    }
}

// 3. Settings Panel — Smart Home, Webhook & Tasker / MacroDroid Bridge
@Composable
fun WebhookRegistryPanel(
    webhooks: List<WebhookActionInfo>,
    testStatus: Map<Long, String>,
    onSaveWebhook: (WebhookActionInfo) -> Unit,
    onDeleteWebhook: (Long) -> Unit,
    onTestWebhook: (WebhookActionInfo) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("POST") }
    var url by remember { mutableStateOf("") }
    var bodyTemplate by remember { mutableStateOf("{\"trigger\":\"allchat\"}") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SwissPaper, RectangleShape)
            .border(1.5.dp, SwissInk, RectangleShape)
            .padding(14.dp)
            .testTag("webhook_registry_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "SMART HOME // WEBHOOK & TASKER BRIDGE",
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
            color = SwissInk
        )
        Text(
            text = "Register local Home Assistant, IFTTT, n8n, or MacroDroid webhooks. Speak or type the trigger phrase in Chat/Assistant to fire it live.",
            style = MaterialTheme.typography.bodySmall,
            color = SwissMuted
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, colors = swissTextFieldColors(), shape = RectangleShape, modifier = Modifier.weight(1f))
            OutlinedTextField(value = phrase, onValueChange = { phrase = it }, label = { Text("Voice Phrase") }, singleLine = true, colors = swissTextFieldColors(), shape = RectangleShape, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SwissToggleCell(label = "POST", selected = method == "POST", onClick = { method = "POST" }, useSignalRedWhenSelected = true)
            SwissToggleCell(label = "GET", selected = method == "GET", onClick = { method = "GET" }, useSignalRedWhenSelected = true)
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("https://... webhook URL") }, singleLine = true, colors = swissTextFieldColors(), shape = RectangleShape, modifier = Modifier.weight(1f))
        }
        if (method != "GET") {
            OutlinedTextField(value = bodyTemplate, onValueChange = { bodyTemplate = it }, label = { Text("JSON Body") }, singleLine = true, colors = swissTextFieldColors(), shape = RectangleShape, modifier = Modifier.fillMaxWidth())
        }
        SwissButton(
            text = "SAVE WEBHOOK ACTION",
            onClick = {
                onSaveWebhook(
                    WebhookActionInfo(
                        name = name.trim(),
                        triggerPhrase = phrase.trim().ifBlank { name.trim() },
                        method = method,
                        url = url.trim(),
                        bodyTemplate = bodyTemplate.trim()
                    )
                )
                name = ""
                phrase = ""
                url = ""
            },
            enabled = name.isNotBlank() && url.startsWith("http"),
            leadingIcon = Icons.Default.Webhook,
            variant = SwissButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        )

        webhooks.forEach { hook ->
            Column(modifier = Modifier.fillMaxWidth().background(SwissCanvas).border(1.dp, SwissInk).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${hook.name} (${hook.method})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = SwissInk)
                        Text("Say: \"${hook.triggerPhrase}\" → ${hook.url}", style = MaterialTheme.typography.bodySmall, color = SwissMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row {
                        SwissToggleCell(label = "TEST", selected = false, onClick = { onTestWebhook(hook) })
                        SwissIconCell(icon = Icons.Default.Delete, contentDescription = "Delete webhook", onClick = { onDeleteWebhook(hook.id) }, bordered = false, cellSize = 48.dp)
                    }
                }
                testStatus[hook.id]?.let { st ->
                    Text(st, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily), color = if (st.startsWith("OK")) SwissVerifiedInk else SwissSignalRed)
                }
            }
        }
    }
}

// 4. Compact Paste-a-cURL Auto-Importer Strip (for Provider Editor Dialog in SettingsScreen)
@Composable
fun PasteCurlImporterBox(
    onImportCurl: (String) -> Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    var rawCurl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SwissVerifiedTint, RectangleShape)
            .border(1.dp, SwissInk, RectangleShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PASTE-A-CURL AUTO-IMPORTER", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissVerifiedInk)
            SwissToggleCell(label = if (expanded) "HIDE" else "PASTE CURL", selected = expanded, onClick = { expanded = !expanded })
        }
        if (expanded) {
            OutlinedTextField(
                value = rawCurl,
                onValueChange = { rawCurl = it },
                placeholder = { Text("curl https://api.groq.com/openai/v1/chat/completions -H \"Authorization: Bearer gsk_...\" -d '{\"model\":\"llama-3.3-70b-versatile\"}'") },
                minLines = 3,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                colors = swissTextFieldColors(),
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth().testTag("curl_import_input")
            )
            SwissButton(
                text = "AUTO-FILL FROM CURL",
                onClick = {
                    if (onImportCurl(rawCurl)) {
                        rawCurl = ""
                        expanded = false
                    }
                },
                enabled = rawCurl.isNotBlank(),
                leadingIcon = Icons.Default.Terminal,
                variant = SwissButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth().testTag("curl_import_apply_btn")
            )
        }
    }
}
