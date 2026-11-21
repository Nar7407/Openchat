package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.domain.model.SelectableModel
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import com.example.util.ReadAloudController
import com.example.util.VoiceInputController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class LiveVoicePhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun LiveVoiceOverlay(
    selectedModel: SelectableModel?,
    onStreamReply: suspend (
        history: List<Pair<String, String>>,
        userText: String,
        onPartial: (String) -> Unit,
        onSentence: (String) -> Unit
    ) -> String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(LiveVoicePhase.IDLE) }
    var rmsLevel by remember { mutableFloatStateOf(0f) }
    var liveTranscript by remember { mutableStateOf("") }
    var liveAssistantText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("INITIALIZING LIVE VOICE LOOP…") }
    var streamDone by remember { mutableStateOf(true) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    val history = remember { mutableStateListOf<Pair<String, String>>() }

    var startListeningAction by remember { mutableStateOf<() -> Unit>({}) }

    val ttsController = remember(context) {
        ReadAloudController(
            context = context,
            onSpeakingIdChanged = { speakingId ->
                if (speakingId != null) {
                    phase = LiveVoicePhase.SPEAKING
                    statusMessage = "SPEAKING // TAP ORB TO INTERRUPT"
                }
            },
            onQueueDrained = {
                scope.launch {
                    if (streamDone && phase == LiveVoicePhase.SPEAKING) {
                        delay(350L)
                        startListeningAction()
                    }
                }
            }
        )
    }

    val voiceController = remember(context) {
        VoiceInputController(
            context = context,
            onPartialText = { partial ->
                liveTranscript = partial
                statusMessage = "LISTENING // AUTO-SEND ON PAUSE"
            },
            onFinalText = { finalText ->
                val clean = finalText.trim()
                if (clean.isEmpty()) {
                    scope.launch {
                        delay(400L)
                        startListeningAction()
                    }
                    return@VoiceInputController
                }
                liveTranscript = clean
                liveAssistantText = ""
                phase = LiveVoicePhase.THINKING
                statusMessage = "STREAMING // ${selectedModel?.model?.displayName ?: "MODEL"}"
                streamDone = false
                activeJob?.cancel()
                activeJob = scope.launch {
                    runCatching {
                        val reply = onStreamReply(
                            history.toList(),
                            clean,
                            { partial -> liveAssistantText = partial },
                            { sentence ->
                                ttsController.enqueueSentence(sentence)
                            }
                        )
                        history += ("USER" to clean)
                        history += ("ASSISTANT" to reply)
                        streamDone = true
                        if (!ttsController.hasActiveSpeech()) {
                            delay(450L)
                            startListeningAction()
                        }
                    }.onFailure { err ->
                        streamDone = true
                        phase = LiveVoicePhase.IDLE
                        statusMessage = "ERR // ${err.message ?: "Live voice failed."}"
                    }
                }
            },
            onListeningChanged = { listening ->
                if (listening) {
                    phase = LiveVoicePhase.LISTENING
                    statusMessage = "LISTENING // SPEAK NATURALLY"
                }
            },
            onFailure = { errMsg ->
                if (phase == LiveVoicePhase.LISTENING) {
                    statusMessage = "$errMsg — TAP MIC TO RESUME"
                    phase = LiveVoicePhase.IDLE
                }
            },
            onRmsChanged = { rms ->
                rmsLevel = ((rms + 2f) / 12f).coerceIn(0f, 1f)
            }
        )
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                voiceController.start()
            } else {
                statusMessage = "PERMISSION // Microphone access required for Live Voice."
                phase = LiveVoicePhase.IDLE
            }
        }
    )

    fun beginListening() {
        if (selectedModel == null) {
            statusMessage = "ERR // Configure a Text Chat model in Settings first."
            phase = LiveVoicePhase.IDLE
            return
        }
        ttsController.stop()
        activeJob?.cancel()
        streamDone = true
        liveTranscript = ""
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            voiceController.start()
        } else {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    startListeningAction = { beginListening() }

    LaunchedEffect(Unit) {
        beginListening()
    }

    DisposableEffect(Unit) {
        onDispose {
            activeJob?.cancel()
            voiceController.release()
            ttsController.release()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_orb")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    Dialog(
        onDismissRequest = {
            activeJob?.cancel()
            voiceController.stop()
            ttsController.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = SwissInk,
            shape = RectangleShape,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top HUD
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SwissWhite, RectangleShape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LIVE VOICE // HANDS-FREE LOOP (STT → LLM → TTS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed
                        )
                        Text(
                            text = selectedModel?.model?.displayName ?: "NO MODEL SELECTED",
                            style = MaterialTheme.typography.titleMedium,
                            color = SwissWhite
                        )
                    }
                    Box(
                        modifier = Modifier
                            .border(1.dp, SwissWhite, RectangleShape)
                            .clickable {
                                activeJob?.cancel()
                                voiceController.stop()
                                ttsController.stop()
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("close_live_voice_button")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "End live voice", tint = SwissWhite, modifier = Modifier.size(16.dp))
                            Text("END", style = MaterialTheme.typography.labelSmall, color = SwissWhite)
                        }
                    }
                }

                // Animated Waveform Orb (Tap to Interrupt or Resume)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (phase == LiveVoicePhase.LISTENING || phase == LiveVoicePhase.SPEAKING) SwissSignalRed else SwissConcrete,
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clickable {
                                // Tap-to-Interrupt immediately cancels speech/LLM and re-arms Mic
                                beginListening()
                            }
                            .testTag("live_voice_orb_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        val orbColor = when (phase) {
                            LiveVoicePhase.LISTENING -> SwissSignalRed
                            LiveVoicePhase.SPEAKING -> SwissWhite
                            LiveVoicePhase.THINKING -> SwissConcrete
                            LiveVoicePhase.IDLE -> SwissMuted
                        }
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val baseR = size.minDimension * 0.30f
                            val dynamicBoost = when (phase) {
                                LiveVoicePhase.LISTENING -> 1f + rmsLevel * 0.35f
                                LiveVoicePhase.SPEAKING -> pulse
                                LiveVoicePhase.THINKING -> 0.96f
                                LiveVoicePhase.IDLE -> 0.85f
                            }

                            // Registration grid crosshairs
                            drawLine(SwissMuted.copy(alpha = 0.35f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1f)
                            drawLine(SwissMuted.copy(alpha = 0.35f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1f)

                            // Concentric Swiss sonar rings
                            drawCircle(
                                color = orbColor.copy(alpha = 0.25f),
                                radius = baseR * dynamicBoost * 1.38f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.5f)
                            )
                            drawCircle(
                                color = orbColor.copy(alpha = 0.55f),
                                radius = baseR * dynamicBoost * 1.15f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2f)
                            )

                            // Animated harmonic waveform ring
                            val path = Path()
                            val points = 72
                            val amp = when (phase) {
                                LiveVoicePhase.LISTENING -> 8f + rmsLevel * 28f
                                LiveVoicePhase.SPEAKING -> 18f
                                LiveVoicePhase.THINKING -> 6f
                                LiveVoicePhase.IDLE -> 2f
                            }
                            for (i in 0..points) {
                                val angle = (i.toFloat() / points) * (2.0 * PI)
                                val wave = sin(angle * 6 + wavePhase) * amp + cos(angle * 3 - wavePhase * 1.5) * (amp * 0.4f)
                                val r = (baseR * dynamicBoost) + wave.toFloat()
                                val x = cx + r * cos(angle).toFloat()
                                val y = cy + r * sin(angle).toFloat()
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            path.close()
                            drawPath(path = path, color = orbColor.copy(alpha = 0.22f))
                            drawPath(path = path, color = orbColor, style = Stroke(width = 3f))
                        }

                        Icon(
                            imageVector = when (phase) {
                                LiveVoicePhase.LISTENING -> Icons.Filled.Mic
                                LiveVoicePhase.SPEAKING -> Icons.Outlined.GraphicEq
                                LiveVoicePhase.THINKING -> Icons.Outlined.GraphicEq
                                LiveVoicePhase.IDLE -> Icons.Filled.Mic
                            },
                            contentDescription = "Voice orb status",
                            tint = SwissWhite,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Multi-Turn Live Transcript & Assistant Subtitle Log
                    val voiceListState = rememberLazyListState()
                    val combinedVoiceTurns = buildList {
                        addAll(history)
                        if (liveTranscript.isNotBlank() && history.lastOrNull()?.let { it.first == "USER" && it.second == liveTranscript } != true) {
                            add("USER" to liveTranscript)
                        }
                        if (liveAssistantText.isNotBlank() && history.lastOrNull()?.let { it.first == "ASSISTANT" && it.second == liveAssistantText } != true) {
                            add("ASSISTANT" to liveAssistantText)
                        }
                    }
                    LaunchedEffect(combinedVoiceTurns.size, liveAssistantText.length) {
                        if (combinedVoiceTurns.isNotEmpty() && !voiceListState.isScrollInProgress) {
                            voiceListState.scrollToItem(combinedVoiceTurns.lastIndex)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .border(1.dp, SwissWhite.copy(alpha = 0.35f), RectangleShape)
                            .background(SwissPaper.copy(alpha = 0.06f))
                            .padding(10.dp)
                    ) {
                        if (combinedVoiceTurns.isEmpty()) {
                            Text(
                                text = "Speak naturally. AllChat detects pauses, remembers every turn, streams your reply sentence-by-sentence aloud, and resumes listening automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissConcrete
                            )
                        } else {
                            LazyColumn(
                                state = voiceListState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(combinedVoiceTurns) { idx, (role, content) ->
                                    val isUser = role == "USER"
                                    Text(
                                        text = "${if (isUser) "YOU" else "AI"} // $content",
                                        style = if (isUser) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                                        color = if (isUser) SwissConcrete else SwissWhite
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Controls: Tap-to-Interrupt & End
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(SwissSignalRed, RectangleShape)
                            .border(1.dp, SwissSignalRed, RectangleShape)
                            .clickable { beginListening() }
                            .padding(vertical = 15.dp)
                            .testTag("live_voice_interrupt_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (phase == LiveVoicePhase.SPEAKING || phase == LiveVoicePhase.THINKING) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = null,
                                tint = SwissWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when (phase) {
                                    LiveVoicePhase.SPEAKING, LiveVoicePhase.THINKING -> "TAP TO INTERRUPT"
                                    LiveVoicePhase.LISTENING -> "RESTART MIC"
                                    LiveVoicePhase.IDLE -> "TAP TO SPEAK"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = SwissWhite
                            )
                        }
                    }
                }
            }
        }
    }
}
