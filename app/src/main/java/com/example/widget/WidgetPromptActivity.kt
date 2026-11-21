package com.example.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.components.swissTextFieldColors
import com.example.ui.theme.AllChatTheme
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import com.example.util.VoiceInputController

/**
 * Translucent floating micro-overlay for the Home Screen Widget.
 * Floats directly over the launcher without ever launching the main AllChat app,
 * captures typed text or voice dictation, streams the answer onto the widget itself,
 * and immediately dismisses back to the Home Screen.
 */
class WidgetPromptActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "widget_prompt_mode"
        const val EXTRA_RETRY_QUESTION = "widget_retry_question"
        const val MODE_TEXT = "text"
        const val MODE_VOICE = "voice"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val retryQuestion = intent?.getStringExtra(EXTRA_RETRY_QUESTION)?.trim().orEmpty()
        if (retryQuestion.isNotEmpty()) {
            WidgetQuickAskRunner.submitQuestion(applicationContext, retryQuestion)
            finishNoAnim()
            return
        }

        val initialMode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_TEXT
        if (initialMode == MODE_VOICE) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        } else {
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }

        setContent {
            AllChatTheme {
                WidgetFloatingPromptOverlay(
                    startInVoiceMode = initialMode == MODE_VOICE,
                    onSubmitAndDismiss = { question ->
                        WidgetQuickAskRunner.submitQuestion(applicationContext, question)
                        finishNoAnim()
                    },
                    onDismiss = { finishNoAnim() }
                )
            }
        }
    }

    private fun finishNoAnim() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

@Composable
private fun WidgetFloatingPromptOverlay(
    startInVoiceMode: Boolean,
    onSubmitAndDismiss: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var promptText by remember { mutableStateOf("") }
    var isVoiceMode by remember { mutableStateOf(startInVoiceMode) }
    var isListening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("MIC // SPEAK YOUR QUESTION…") }
    val focusRequester = remember { FocusRequester() }

    val voiceController = remember(context) {
        VoiceInputController(
            context = context,
            onPartialText = { partial ->
                promptText = partial
                voiceStatus = "LISTENING // LIVE TRANSCRIPT"
            },
            onFinalText = { finalText ->
                val clean = finalText.trim()
                if (clean.isNotEmpty()) {
                    onSubmitAndDismiss(clean)
                }
            },
            onListeningChanged = { active ->
                isListening = active
                if (active) {
                    voiceStatus = "MIC // LISTENING — SPEAK NOW…"
                }
            },
            onFailure = { err ->
                isListening = false
                voiceStatus = "ERR // $err"
            }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                voiceController.start()
            } else {
                voiceStatus = "ERR // Microphone permission denied. Tap below to type."
                isVoiceMode = false
            }
        }
    )

    fun startVoiceCapture() {
        isVoiceMode = true
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            voiceController.start()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    LaunchedEffect(isVoiceMode) {
        if (isVoiceMode) {
            if (!isListening && promptText.isBlank()) {
                startVoiceCapture()
            }
        } else {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    voiceController.stop()
                    onDismiss()
                }
            )
            .imePadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = SwissCanvas,
            shape = RectangleShape,
            border = BorderStroke(1.5.dp, SwissInk),
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Micro Swiss Header Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isVoiceMode) "WIDGET // VOICE QUICK ASK" else "WIDGET // QUICK ASK (ANSWER ON WIDGET)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissWhite
                    )
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close quick ask",
                        tint = SwissWhite,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                voiceController.stop()
                                onDismiss()
                            }
                    )
                }

                if (isVoiceMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = voiceStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isListening) SwissSignalRed else SwissMuted
                        )
                        Text(
                            text = promptText.ifBlank { "Speak clearly — your answer will stream directly onto the Home Screen widget." },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (promptText.isBlank()) SwissMuted else SwissInk
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isListening) SwissSignalRed else SwissInk, RectangleShape)
                                    .clickable {
                                        if (isListening) {
                                            voiceController.stop()
                                            if (promptText.isNotBlank()) {
                                                onSubmitAndDismiss(promptText)
                                            }
                                        } else {
                                            startVoiceCapture()
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                        contentDescription = null,
                                        tint = SwissWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isListening) "STOP & ASK" else "TAP TO SPEAK",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SwissWhite
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .background(SwissPaper, RectangleShape)
                                    .clickable {
                                        voiceController.stop()
                                        isVoiceMode = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "TYPE",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SwissInk
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            placeholder = {
                                Text(
                                    text = "Ask right here…",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = false,
                            maxLines = 3,
                            shape = RectangleShape,
                            colors = swissTextFieldColors(containerColor = SwissCanvas),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (promptText.isNotBlank()) onSubmitAndDismiss(promptText)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )

                        // Quick switch to Voice without leaving overlay
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(SwissPaper, RectangleShape)
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable { startVoiceCapture() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Switch to voice ask",
                                tint = SwissInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Submit to Widget directly
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (promptText.isNotBlank()) SwissSignalRed else SwissMuted,
                                    RectangleShape
                                )
                                .clickable(enabled = promptText.isNotBlank()) {
                                    onSubmitAndDismiss(promptText)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Ask on widget",
                                tint = SwissWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
