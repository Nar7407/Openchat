package com.example.widget

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
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
import com.example.ui.components.CallingLaunchOverlay
import com.example.ui.components.DeviceActionConfirmCard
import com.example.ui.components.DeviceActionQueueStack
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
import com.example.util.EarbudChimePlayer
import com.example.util.EarbudCommand
import com.example.util.EarbudKeyBus
import com.example.util.MediaButtonBridge
import com.example.util.ReadAloudController
import com.example.util.VoiceInputController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
        const val EXTRA_OPEN_PENDING_ACTION = "widget_open_pending_action"
        const val MODE_TEXT = "text"
        const val MODE_VOICE = "voice"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (EarbudKeyBus.handleHardwareKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
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

        val openPending = intent?.getBooleanExtra(EXTRA_OPEN_PENDING_ACTION, false) == true
        val initialPendingActions = if (openPending) WidgetPendingActionStore.consumeAll() else emptyList()

        val initialMode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_TEXT
        if (initialMode == MODE_VOICE || initialPendingActions.isNotEmpty()) {
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
                    startInVoiceMode = initialMode == MODE_VOICE && initialPendingActions.isEmpty(),
                    initialPendingActions = initialPendingActions,
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
    initialPendingActions: List<DeviceAction> = emptyList(),
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
    val autoDialWebhookUrl by remember(repository) {
        repository?.autoDialWebhookUrlFlow ?: flowOf("")
    }.collectAsStateWithLifecycle(initialValue = "")

    val scope = rememberCoroutineScope()
    var promptText by remember { mutableStateOf("") }
    var activeConstraints by remember { mutableStateOf<Set<NegativeConstraint>>(emptySet()) }
    var isEnhancing by remember { mutableStateOf(false) }
    var isVoiceMode by remember { mutableStateOf(startInVoiceMode) }
    var isListening by remember { mutableStateOf(false) }
    var isTtsSpeaking by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("MIC // SPEAK YOUR QUESTION OR COMMAND…") }
    var actionQueue by remember { mutableStateOf<List<DeviceAction>>(initialPendingActions) }
    var launchingCallAction by remember { mutableStateOf<DeviceAction.DialPhone?>(null) }
    var launchingCallViaBridge by remember { mutableStateOf(false) }

    val ttsController = remember(context) {
        ReadAloudController(
            context = context,
            onSpeakingIdChanged = { id -> isTtsSpeaking = (id != null) }
        )
    }

    fun noticeForWidgetAction(action: DeviceAction?, totalSteps: Int = 1): String? {
        if (action == null) return null
        val disambig = DeviceActionEngine.buildDisambiguationSpeech(action)
        val prefix = if (totalSteps > 1) "CHAIN ($totalSteps STEPS) // " else ""
        if (disambig != null) return "${prefix}$disambig"
        return when (action) {
            is DeviceAction.DialPhone -> if (action.phoneNumber.isNotBlank()) {
                action.displayBanner
            } else {
                "${prefix}PHONE // Tap a matching contact pill or enter a number below."
            }
            is DeviceAction.WhatsAppCall -> if (action.phoneNumber.isNotBlank()) {
                action.displayBanner
            } else {
                "${prefix}WHATSAPP CALL // Tap a matching contact pill or enter a number below."
            }
            is DeviceAction.SendWhatsApp -> if (action.phoneNumber.isNotBlank()) {
                "${prefix}WHATSAPP // Ready for ${action.contactLabel ?: action.phoneNumber} — auto-opening in 3s."
            } else {
                "${prefix}WHATSAPP // Tap a matching contact pill or enter a number below."
            }
            else -> "${prefix}${action.badge} // Tap confirmation button below."
        }
    }

    var statusNotice by remember {
        mutableStateOf(noticeForWidgetAction(initialPendingActions.firstOrNull(), initialPendingActions.size))
    }
    var widgetError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun launchDialAction(dial: DeviceAction.DialPhone) {
        val enriched = if (dial.contactLabel.isNullOrBlank() && dial.phoneNumber.isNotBlank()) {
            val matched = DeviceActionEngine.resolveContactByPhone(dial.phoneNumber, quickContacts)
            if (matched != null) dial.copy(contactLabel = matched.name) else dial
        } else {
            dial
        }
        statusNotice = enriched.displayBanner
        launchingCallViaBridge = autoDialWebhookUrl.trim().startsWith("http")
        launchingCallAction = enriched
        scope.launch {
            DeviceActionEngine.executeDialWithBridge(context, enriched, autoDialWebhookUrl)
                .onSuccess { res ->
                    launchingCallViaBridge = res.usedAutoDialBridge
                    statusNotice = "ACTION // ${res.message}"
                }
                .onFailure { err ->
                    launchingCallAction = null
                    widgetError = err.message ?: "Dialer launch failed."
                }
        }
    }

    fun resetWidgetState() {
        widgetError = null
        statusNotice = null
        actionQueue = emptyList()
        launchingCallAction = null
        promptText = ""
        isEnhancing = false
    }

    fun advanceWidgetActionQueue(completedMsg: String? = null, dismissWhenEmpty: Boolean = true) {
        val remaining = actionQueue.drop(1)
        actionQueue = remaining
        val next = remaining.firstOrNull()
        if (next != null) {
            statusNotice = noticeForWidgetAction(next, remaining.size)
            ttsController.speak(
                System.currentTimeMillis(),
                DeviceActionEngine.buildSpokenConfirmationPrompt(next, stepIndex = 1, totalSteps = remaining.size + 1)
            )
        } else {
            if (completedMsg != null) statusNotice = "OK // $completedMsg"
            if (dismissWhenEmpty) onDismiss()
        }
    }

    fun submitWithConstraints(raw: String) {
        val clean = raw.trim()
        if (clean.isEmpty()) return
        widgetError = null
        statusNotice = null

        scope.launch {
            // 0. Check Voice/Text Disambiguation Reply ("first", "second", "Rahul Verma") if active action has duplicate contacts
            val headPending = actionQueue.firstOrNull()
            if (headPending != null && DeviceActionEngine.ambiguousMatchesOf(headPending).size >= 2) {
                val resolvedDisambig = DeviceActionEngine.resolveDisambiguationVoiceReply(clean, headPending)
                if (resolvedDisambig != null) {
                    val updatedQueue = listOf(resolvedDisambig) + actionQueue.drop(1)
                    actionQueue = updatedQueue
                    isVoiceMode = false
                    promptText = ""
                    statusNotice = noticeForWidgetAction(resolvedDisambig, updatedQueue.size)
                    ttsController.speak(
                        System.currentTimeMillis(),
                        DeviceActionEngine.buildSpokenConfirmationPrompt(resolvedDisambig, 0, updatedQueue.size)
                    )
                    return@launch
                }
            }

            // Ensure fresh contacts & custom apps are loaded even on cold widget start
            val contacts = if (quickContacts.isNotEmpty()) {
                quickContacts
            } else {
                runCatching { repository?.quickContactsFlow?.first() }.getOrNull().orEmpty()
            }
            val apps = if (customApps.isNotEmpty()) {
                customApps
            } else {
                runCatching { repository?.customAppsFlow?.first() }.getOrNull().orEmpty()
            }

            val preprocessed = DeviceActionEngine.preprocessVoiceCommand(clean, contacts)

            // 1. Intercept Sequential Multi-Action Chains, WhatsApp Voice/Video/Message, Calls, Alarms, Timers, Maps, YouTube, Apps
            val parsedChain = runCatching {
                DeviceActionEngine.parseActionChainFromPrompt(clean, contacts, apps)
            }.onFailure { err ->
                widgetError = err.message ?: "Failed to parse device action."
            }.getOrDefault(emptyList())

            if (parsedChain.isNotEmpty()) {
                actionQueue = parsedChain
                isVoiceMode = false
                promptText = ""
                val first = parsedChain.first()
                statusNotice = noticeForWidgetAction(first, parsedChain.size)
                val disambigSpeech = DeviceActionEngine.buildDisambiguationSpeech(first)
                if (disambigSpeech != null) {
                    ttsController.speak(System.currentTimeMillis(), disambigSpeech)
                } else if (parsedChain.size > 1) {
                    ttsController.speak(
                        System.currentTimeMillis(),
                        DeviceActionEngine.buildSpokenConfirmationPrompt(first, 0, parsedChain.size)
                    )
                }
                return@launch
            }

            // 2. Otherwise dispatch preprocessed question to Home Screen Widget Q&A runner (which ALSO runs DeviceAction tools!)
            val finalPayload = if (activeConstraints.isEmpty() || preprocessed.startsWith("/")) {
                preprocessed
            } else {
                val rules = activeConstraints.joinToString("; ") { it.instruction }
                "$preprocessed\n\n[Formatting constraints: $rules]"
            }
            runCatching {
                onSubmitAndDismiss(finalPayload)
            }.onFailure { err ->
                widgetError = err.message ?: "Unable to submit question from widget."
            }
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

    DisposableEffect(voiceController, ttsController) {
        onDispose {
            voiceController.release()
            ttsController.release()
        }
    }

    // Speak & listen automatically when an ambiguous duplicate-name action arrives in the widget
    val widgetActiveAction = actionQueue.firstOrNull()
    val widgetNeedsDisambig = widgetActiveAction != null &&
        DeviceActionEngine.ambiguousMatchesOf(widgetActiveAction).size >= 2
    LaunchedEffect(widgetActiveAction, widgetNeedsDisambig) {
        if (widgetNeedsDisambig && widgetActiveAction != null) {
            DeviceActionEngine.buildDisambiguationSpeech(widgetActiveAction)?.let { speech ->
                ttsController.speak(System.currentTimeMillis(), speech)
            }
            delay(450L)
            var waited = 0L
            while (isTtsSpeaking && waited < 6000L) {
                delay(200L)
                waited += 200L
            }
            if (!isListening && actionQueue.firstOrNull() == widgetActiveAction) {
                startVoiceCapture()
            }
        }
    }

    fun isWidgetActionReadyForEarbud(action: DeviceAction?): Boolean {
        if (action == null) return false
        if (DeviceActionEngine.ambiguousMatchesOf(action).size >= 2) return false
        return when (action) {
            is DeviceAction.DialPhone -> action.phoneNumber.isNotBlank()
            is DeviceAction.WhatsAppCall -> action.phoneNumber.isNotBlank()
            is DeviceAction.SendWhatsApp -> action.phoneNumber.isNotBlank()
            is DeviceAction.SendSms -> action.phoneNumber.isNotBlank()
            is DeviceAction.SendEmail -> action.emailAddress.isNotBlank()
            is DeviceAction.UpiPay -> action.upiId.isNotBlank() && action.upiId.contains("@")
            else -> true
        }
    }

    // Earbud 1x Tap = Confirm Active Step (Call/WhatsApp/Chain Step) or Re-Dial; 2x Tap = Cancel/Skip Step
    fun handleWidgetEarbudSingleTap() {
        launchingCallAction?.let { activeCall ->
            EarbudChimePlayer.playConfirmChime()
            launchDialAction(activeCall)
            return
        }
        val current = actionQueue.firstOrNull() ?: return
        if (!isWidgetActionReadyForEarbud(current)) return
        EarbudChimePlayer.playConfirmChime()
        if (current is DeviceAction.DialPhone) {
            launchDialAction(current)
            if (actionQueue.size > 1) {
                advanceWidgetActionQueue(dismissWhenEmpty = false)
            }
        } else {
            DeviceActionEngine.execute(context, current)
                .onSuccess { msg ->
                    advanceWidgetActionQueue(completedMsg = msg, dismissWhenEmpty = true)
                }
                .onFailure { err -> widgetError = err.message ?: "Action execution failed." }
        }
    }

    fun handleWidgetEarbudCancelTap() {
        if (launchingCallAction != null) {
            EarbudChimePlayer.playCancelChime()
            launchingCallAction = null
        } else if (actionQueue.isNotEmpty()) {
            EarbudChimePlayer.playCancelChime()
            advanceWidgetActionQueue(completedMsg = "EARBUD // Cancelled.", dismissWhenEmpty = false)
        }
    }

    DisposableEffect(context, actionQueue.isNotEmpty(), launchingCallAction != null) {
        val bridge = if (actionQueue.isNotEmpty() || launchingCallAction != null) {
            MediaButtonBridge(
                context = context,
                onEarbudTriggerAsk = { handleWidgetEarbudSingleTap() },
                onEarbudNextAction = { handleWidgetEarbudCancelTap() },
                enableSilentKeepAlive = true
            ).also { it.start(context) }
        } else null
        onDispose { bridge?.stop() }
    }

    LaunchedEffect(Unit) {
        EarbudKeyBus.events.collect { cmd ->
            when (cmd) {
                EarbudCommand.SINGLE_TAP -> handleWidgetEarbudSingleTap()
                EarbudCommand.DOUBLE_TAP_OR_NEXT -> handleWidgetEarbudCancelTap()
            }
        }
    }

    LaunchedEffect(isVoiceMode, actionQueue.isNotEmpty()) {
        if (actionQueue.isNotEmpty()) return@LaunchedEffect
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

                // Top ACTION // Banner (keeps confirmed Contact Name like "Manisha ❤️✨" visible at top)
                AnimatedVisibility(visible = statusNotice?.startsWith("ACTION //") == true) {
                    statusNotice?.let { topActionBanner ->
                        Text(
                            text = topActionBanner,
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissWhite,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissSignalRed)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("widget_top_action_banner")
                        )
                    }
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

                // Interactive Sequential Multi-Action Stack (Calls, WhatsApp Voice/Video/Message, Numbered Disambiguation Pills)
                AnimatedVisibility(visible = actionQueue.isNotEmpty()) {
                    DeviceActionQueueStack(
                        actions = actionQueue,
                        quickContacts = quickContacts,
                        autoDialWebhookUrl = autoDialWebhookUrl,
                        onDialLaunched = { dialAction, usedBridge ->
                            statusNotice = dialAction.displayBanner
                            launchingCallViaBridge = usedBridge
                            launchingCallAction = dialAction
                        },
                        onUpdateActive = { updated ->
                            val nextQueue = listOf(updated) + actionQueue.drop(1)
                            actionQueue = nextQueue
                            statusNotice = noticeForWidgetAction(updated, nextQueue.size)
                        },
                        onRemoveQueuedAt = { idx ->
                            if (idx in actionQueue.indices) {
                                actionQueue = actionQueue.filterIndexed { i, _ -> i != idx }
                            }
                        },
                        onClearAll = { actionQueue = emptyList() },
                        onSaveQuickContact = { name, phone ->
                            val repo = repository ?: return@DeviceActionQueueStack
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
                        onExecuteActive = { action ->
                            if (action is DeviceAction.DialPhone) {
                                launchDialAction(action)
                                if (actionQueue.size > 1) {
                                    advanceWidgetActionQueue(dismissWhenEmpty = false)
                                }
                            } else {
                                DeviceActionEngine.execute(context, action)
                                    .onSuccess { msg ->
                                        advanceWidgetActionQueue(completedMsg = msg, dismissWhenEmpty = true)
                                    }
                                    .onFailure { err ->
                                        widgetError = err.message ?: "Action execution failed."
                                    }
                            }
                        },
                        onDismissActive = { advanceWidgetActionQueue(dismissWhenEmpty = false) },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                // Inline Status Notice (non-ACTION notices; ACTION // banners are pinned at the top)
                AnimatedVisibility(visible = statusNotice != null && statusNotice?.startsWith("ACTION //") != true) {
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

    launchingCallAction?.let { activeCall ->
        CallingLaunchOverlay(
            action = activeCall,
            usedAutoDialBridge = launchingCallViaBridge,
            onLaunchDialerNow = { launchDialAction(activeCall) },
            onDismiss = { launchingCallAction = null }
        )
    }
}
