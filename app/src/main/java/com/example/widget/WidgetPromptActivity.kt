package com.example.widget

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.AllChatApplication
import com.example.domain.model.ModelCapability
import com.example.domain.model.NegativeConstraint
import com.example.domain.model.QuickContact
import com.example.ui.components.DeviceActionConfirmCard
import com.example.ui.components.NegativeConstraintPillBar
import com.example.ui.components.swissTextFieldColors
import com.example.ui.theme.AllChatTheme
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissWhite
import com.example.util.DeviceAction
import com.example.util.DeviceActionEngine
import com.example.util.VoiceInputController
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Translucent floating micro-overlay for the Home Screen Widget.
 * Floats directly over the launcher with explicit themed colors, resilient error fallback,
 * full Quick Contacts + Device Action Confirmation Card (Calls, WhatsApp 3s auto-launch,
 * Alarms, Timers, Maps, YouTube), and direct streaming Q&A onto the Home Screen Widget.
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

        // Ensure window is strictly transparent & edge-to-edge so OEMs never paint a blank white box
        runCatching {
            enableEdgeToEdge()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

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
    val repository = remember(context) {
        runCatching { (context.applicationContext as? AllChatApplication)?.repository }.getOrNull()
    }
    val routines by remember(repository) {
        repository?.routinesFlow ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val quickContacts by remember(repository) {
        repository?.quickContactsFlow ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val customApps by remember(repository) {
        repository?.customAppsFlow ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val availableModels by remember(repository) {
        repository?.observeModelsForCapability(ModelCapability.TEXT_CHAT) ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val scope = rememberCoroutineScope()
    var promptText by remember { mutableStateOf("") }
    var activeConstraints by remember { mutableStateOf<Set<NegativeConstraint>>(emptySet()) }
    var isEnhancing by remember { mutableStateOf(false) }
    var isVoiceMode by remember { mutableStateOf(startInVoiceMode) }
    var isListening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("MIC // SPEAK YOUR QUESTION OR COMMAND…") }
    var pendingDeviceAction by remember { mutableStateOf<DeviceAction?>(null) }
    var statusNotice by remember { mutableStateOf<String?>(null) }
    var widgetError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun resetWidgetState() {
        widgetError = null
        statusNotice = null
        pendingDeviceAction = null
        promptText = ""
        isEnhancing = false
    }

    fun submitWithConstraints(raw: String) {
        val clean = raw.trim()
        if (clean.isEmpty()) return
        widgetError = null
        statusNotice = null

        // 1. Intercept Calls, WhatsApp, SMS, Maps, YouTube, Alarms, Timers, Apps right inside the Floating Widget!
        val parsedAction = runCatching {
            DeviceActionEngine.parseFromPrompt(clean, quickContacts, customApps)
        }.onFailure { err ->
            widgetError = err.message ?: "Failed to parse device action."
        }.getOrNull()

        if (parsedAction != null) {
            pendingDeviceAction = parsedAction
            isVoiceMode = false
            promptText = ""
            statusNotice = when (parsedAction) {
                is DeviceAction.DialPhone -> if (parsedAction.phoneNumber.isNotBlank()) {
                    parsedAction.displayBanner
                } else {
                    "PHONE // Tap a matching contact chip or enter a number below."
                }
                is DeviceAction.SendWhatsApp -> if (parsedAction.phoneNumber.isNotBlank()) {
                    "WHATSAPP // Ready — auto-opening in 3s (tap Cancel to stop)."
                } else {
                    "WHATSAPP // Tap a matching contact chip or enter a number below."
                }
                else -> "${parsedAction.badge} // Tap confirmation button below."
            }
            return
        }

        // 2. Otherwise dispatch question to Home Screen Widget Q&A runner
        val finalPayload = if (activeConstraints.isEmpty() || clean.startsWith("/")) {
            clean
        } else {
            val rules = activeConstraints.joinToString("; ") { it.instruction }
            "$clean\n\n[Formatting constraints: $rules]"
        }
        runCatching {
            onSubmitAndDismiss(finalPayload)
        }.onFailure { err ->
            widgetError = err.message ?: "Unable to submit question from widget."
        }
    }

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
                    submitWithConstraints(clean)
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

    LaunchedEffect(isVoiceMode, pendingDeviceAction) {
        if (pendingDeviceAction != null) return@LaunchedEffect
        if (isVoiceMode) {
            if (!isListening && promptText.isBlank()) {
                startVoiceCapture()
            }
        } else {
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Explicit themed full-viewport overlay container with scrim — prevents blank white window issues
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    voiceController.stop()
                    onDismiss()
                }
            )
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val isCompactWindow = maxHeight < 500.dp || maxWidth < 380.dp
        Surface(
            color = SwissCanvas,
            contentColor = SwissInk,
            shape = RectangleShape,
            border = BorderStroke(1.5.dp, SwissInk),
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (isCompactWindow) 120.dp else 160.dp, max = maxHeight)
                .padding(
                    horizontal = if (isCompactWindow) 6.dp else 12.dp,
                    vertical = if (isCompactWindow) 6.dp else 14.dp
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("floating_widget_overlay_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissCanvas)
                    .verticalScroll(rememberScrollState())
            ) {
                // Micro Swiss Header Strip (auto-collapses to slim single row in compact/split windows)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(
                            horizontal = if (isCompactWindow) 8.dp else 10.dp,
                            vertical = if (isCompactWindow) 5.dp else 7.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isVoiceMode) "WIDGET // VOICE QUICK ASK" else "WIDGET // QUICK ASK & ACTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isCompactWindow) {
                            val metaLine = buildString {
                                append("${quickContacts.size} CONTACTS")
                                availableModels.firstOrNull()?.let { m ->
                                    append(" · ${m.model.displayName.take(20)}")
                                }
                            }
                            Text(
                                text = metaLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close quick ask",
                        tint = SwissWhite,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                voiceController.stop()
                                onDismiss()
                            }
                            .testTag("widget_close_button")
                    )
                }

                // Resilient Error Fallback Panel (shows readable error + Reset button instead of a blank box)
                if (repository == null || widgetError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissInk)
                            .border(1.dp, SwissSignalRed, RectangleShape)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "WIDGET RECOVERY // ${widgetError ?: "AllChat repository unavailable — tap Reset to reinitialize."}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissWhite
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(SwissSignalRed, RectangleShape)
                                    .clickable { resetWidgetState() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("widget_error_reset_button"),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Reset widget",
                                    tint = SwissWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "RESET WIDGET",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite
                                )
                            }
                        }
                    }
                }

                // Interactive Device Action Confirmation Card (Calls, WhatsApp 3s Auto-Launch, Multi-Match Contact Chips)
                AnimatedVisibility(visible = pendingDeviceAction != null) {
                    pendingDeviceAction?.let { action ->
                        DeviceActionConfirmCard(
                            action = action,
                            quickContacts = quickContacts,
                            onUpdateAction = { updated ->
                                pendingDeviceAction = updated
                                if (updated is DeviceAction.DialPhone && updated.phoneNumber.isNotBlank()) {
                                    statusNotice = updated.displayBanner
                                }
                            },
                            onSaveQuickContact = { name, phone ->
                                val repo = repository ?: return@DeviceActionConfirmCard
                                scope.launch {
                                    runCatching {
                                        repo.saveQuickContact(QuickContact(name = name, phoneNumber = phone))
                                    }.onSuccess {
                                        statusNotice = "OK // Saved $name ($phone) to Quick Contacts."
                                    }.onFailure { err ->
                                        widgetError = err.message ?: "Could not save contact."
                                    }
                                }
                            },
                            onExecute = {
                                DeviceActionEngine.execute(context, action)
                                    .onSuccess { msg ->
                                        statusNotice = if (action is DeviceAction.DialPhone) "ACTION // $msg" else "OK // $msg"
                                        if (action !is DeviceAction.DialPhone) {
                                            pendingDeviceAction = null
                                            onDismiss()
                                        }
                                    }
                                    .onFailure { err ->
                                        widgetError = err.message ?: "Action execution failed."
                                    }
                            },
                            onDismiss = { pendingDeviceAction = null },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                // Inline Status Notice
                AnimatedVisibility(visible = statusNotice != null) {
                    statusNotice?.let { notice ->
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (notice.startsWith("OK")) SwissVerifiedInk else SwissMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissPaper)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // 1-Tap Quick Note + Assistant Routines Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissSubtleFill)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .background(SwissPaper, RectangleShape)
                            .border(1.dp, SwissInk, RectangleShape)
                            .clickable {
                                if (promptText.isNotBlank()) {
                                    voiceController.stop()
                                    val memoPayload = if (promptText.trim().lowercase().startsWith("/note")) {
                                        promptText.trim()
                                    } else {
                                        "/note ${promptText.trim()}"
                                    }
                                    onSubmitAndDismiss(memoPayload)
                                } else {
                                    voiceController.stop()
                                    isVoiceMode = false
                                    promptText = "/note "
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NoteAdd,
                            contentDescription = "Save Voice Note",
                            tint = SwissSignalRed,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "+ /NOTE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissInk
                        )
                    }

                    routines.forEach { routine ->
                        Row(
                            modifier = Modifier
                                .background(SwissInk, RectangleShape)
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable {
                                    voiceController.stop()
                                    onSubmitAndDismiss(routine.triggerPhrase.ifBlank { routine.name })
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = "Run ${routine.name}",
                                tint = SwissSignalRed,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = routine.name.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite
                            )
                        }
                    }
                }

                // Negative-Constraint Quick Pill Bar directly above the widget input
                NegativeConstraintPillBar(
                    activeConstraints = activeConstraints,
                    onToggleConstraint = { rule ->
                        activeConstraints = if (rule in activeConstraints) activeConstraints - rule else activeConstraints + rule
                    }
                )

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
                            text = promptText.ifBlank { "Speak clearly — ask a question, or say \"Call Mom\" / \"WhatsApp Bhai hello\"." },
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
                                                submitWithConstraints(promptText)
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
                                        text = if (isListening) "STOP & RUN" else "TAP TO SPEAK",
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            placeholder = {
                                Text(
                                    text = if (isEnhancing) "PROMPT DOCTOR // REWRITING…" else "Ask, Call Mom, WhatsApp Bhai…",
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
                                    if (promptText.isNotBlank()) submitWithConstraints(promptText)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .testTag("widget_prompt_input")
                        )

                        // 1-Tap Prompt Doctor ("✨ Enhance Prompt") Magic Wand
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(if (isEnhancing) SwissSignalRed else SwissPaper, RectangleShape)
                                .border(1.dp, if (isEnhancing) SwissSignalRed else SwissInk, RectangleShape)
                                .clickable(enabled = promptText.isNotBlank() && !isEnhancing && repository != null) {
                                    val rough = promptText.trim()
                                    val repo = repository ?: return@clickable
                                    scope.launch {
                                        isEnhancing = true
                                        runCatching { repo.enhanceUserPrompt(rough, null) }
                                            .onSuccess { improved ->
                                                if (improved.isNotBlank()) promptText = improved
                                            }
                                            .onFailure { err ->
                                                widgetError = err.message ?: "Prompt Doctor failed."
                                            }
                                        isEnhancing = false
                                    }
                                }
                                .testTag("widget_enhance_prompt_wand"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoFixHigh,
                                contentDescription = "Enhance prompt with Prompt Doctor",
                                tint = if (isEnhancing) SwissWhite else if (promptText.isNotBlank()) SwissInk else SwissMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Quick switch to Voice without leaving overlay
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(SwissPaper, RectangleShape)
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable { startVoiceCapture() }
                                .testTag("widget_voice_switch_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Switch to voice ask",
                                tint = SwissInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Submit to Widget / Execute Action
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (promptText.isNotBlank()) SwissSignalRed else SwissMuted,
                                    RectangleShape
                                )
                                .clickable(enabled = promptText.isNotBlank()) {
                                    submitWithConstraints(promptText)
                                }
                                .testTag("widget_submit_button"),
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
