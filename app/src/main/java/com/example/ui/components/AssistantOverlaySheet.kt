package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.repository.AllChatRepository
import com.example.domain.model.AssistantRoutine
import com.example.domain.model.ChatOptions
import com.example.domain.model.ConversationTree
import com.example.domain.model.DailyBudgetConfig
import com.example.domain.model.ModelCapability
import com.example.domain.model.NegativeConstraint
import com.example.domain.model.QuickContact
import com.example.domain.model.ReasoningEffort
import com.example.domain.model.SelectableModel
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissWhite
import com.example.domain.model.AmbientNoiseLevel
import com.example.domain.model.SmartContextSnapshot
import com.example.util.DeviceAction
import com.example.util.DeviceActionEngine
import com.example.util.EarbudChimePlayer
import com.example.util.EarbudCommand
import com.example.util.EarbudKeyBus
import com.example.util.MediaButtonBridge
import com.example.util.ReadAloudController
import kotlinx.coroutines.delay
import com.example.util.SmartContextEngine
import com.example.util.VoiceInputController
import com.example.util.WakePhraseListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantOverlaySheet(
    repository: AllChatRepository,
    autoStartMic: Boolean = true,
    onOpenTranslateCamera: () -> Unit,
    onOpenLiveLensExplain: (() -> Unit)? = null,
    onOpenFullApp: (() -> Unit)? = null,
    onOpenToolkitHub: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Sync directly to the active persisted Room conversation shared with Main Chat & Mini-Window
    val allConversations by repository.conversationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val availableModels by remember(repository) {
        repository.observeModelsForCapability(ModelCapability.TEXT_CHAT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val persistedModelDbId by repository.activeModelDbIdFlow.collectAsStateWithLifecycle(initialValue = null)
    val quickContacts by repository.quickContactsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val customApps by repository.customAppsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val routines by repository.routinesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val voiceNotes by repository.voiceNotesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val smartZones by repository.smartZonesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val timeProfilesEnabled by repository.timeProfilesEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val autoEcoMode by repository.autoEcoModeFlow.collectAsStateWithLifecycle(initialValue = true)
    val ambientWhisperEnabled by repository.ambientWhisperEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val wakePhraseEnabled by repository.wakePhraseEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val dailyBudgetConfig by repository.dailyBudgetConfigFlow.collectAsStateWithLifecycle(initialValue = DailyBudgetConfig())
    val todaySpendUsd by remember(repository) {
        repository.observeTodaySpendUsd()
    }.collectAsStateWithLifecycle(initialValue = 0.0)
    var smartSnapshot by remember { mutableStateOf(SmartContextSnapshot()) }

    val activeConvIdFlow = remember(repository) {
        combine(
            repository.activeConversationIdFlow,
            repository.conversationsFlow
        ) { storedId, allConvs ->
            when {
                storedId != null && allConvs.any { it.id == storedId } -> storedId
                allConvs.isNotEmpty() -> allConvs.first().id
                else -> null
            }
        }
    }
    val activeConvId by activeConvIdFlow.collectAsStateWithLifecycle(initialValue = null)

    val persistedMessagesFlow = remember(repository) {
        activeConvIdFlow.flatMapLatest { convId ->
            if (convId == null) flowOf(emptyList()) else repository.observeMessages(convId)
        }
    }
    val allPersistedMessages by persistedMessagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val visibleMessages by remember(allPersistedMessages) {
        derivedStateOf {
            ConversationTree.resolveActiveBranch(allPersistedMessages, emptyMap()).map { it.message }
        }
    }

    var ghostMode by remember { mutableStateOf(false) }
    var ghostMessages by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    var activeConstraints by remember { mutableStateOf<Set<NegativeConstraint>>(emptySet()) }
    var reasoningEffort by remember { mutableStateOf(ReasoningEffort.BALANCED) }
    var isEnhancingPrompt by remember { mutableStateOf(false) }
    var followUpChips by remember { mutableStateOf<List<String>>(emptyList()) }
    var qrBeamPayload by remember { mutableStateOf<String?>(null) }
    var forceFlagshipOverride by remember { mutableStateOf(false) }

    val displayedMessages = if (ghostMode) ghostMessages else visibleMessages
    val isBudgetWarning = dailyBudgetConfig.dailyCapUsd > 0.0 && (todaySpendUsd / dailyBudgetConfig.dailyCapUsd) >= 0.80

    var selectedModel by remember { mutableStateOf<SelectableModel?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showHistoryDrawer by remember { mutableStateOf(false) }
    var showVoiceNotesSheet by remember { mutableStateOf(false) }
    var isSavingNote by remember { mutableStateOf(false) }
    var historySearch by remember { mutableStateOf("") }

    var isListening by remember { mutableStateOf(false) }
    var isStreaming by remember { mutableStateOf(false) }
    var muteTts by remember { mutableStateOf(false) }
    var webSearchEnabled by remember { mutableStateOf(false) }
    var toolsEnabled by remember { mutableStateOf(true) }
    var liveRms by remember { mutableFloatStateOf(0f) }
    var transcriptInput by remember { mutableStateOf("") }
    val autoDialWebhookUrl by repository.autoDialWebhookUrlFlow.collectAsStateWithLifecycle(initialValue = "")
    var actionQueue by remember { mutableStateOf<List<DeviceAction>>(emptyList()) }
    var launchingCallAction by remember { mutableStateOf<DeviceAction.DialPhone?>(null) }
    var launchingCallViaBridge by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var errorBanner by remember { mutableStateOf<String?>(null) }
    var lastFailedPrompt by remember { mutableStateOf<String?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val listState = rememberLazyListState()
    var autoScrollEnabled by remember { mutableStateOf(true) }

    val isScrolledUp by remember {
        derivedStateOf {
            displayedMessages.isNotEmpty() && listState.canScrollForward
        }
    }

    // Keep selectedModel synced with activeModelDbIdFlow and freshly hydrated provider settings
    LaunchedEffect(availableModels, persistedModelDbId) {
        selectedModel = availableModels.firstOrNull { it.model.id == persistedModelDbId }
            ?: runCatching { repository.resolveFreshModel(persistedModelDbId) }.getOrNull()
            ?: availableModels.firstOrNull()
    }

    // Wake / Re-hydrate provider config cleanly on ON_RESUME after idle gaps
    DisposableEffect(lifecycleOwner, repository) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    runCatching { repository.resolveFreshModel() }
                        .getOrNull()
                        ?.let { selectedModel = it }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Pause auto-scroll if the user manually scrolls up; resume when reaching the bottom
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollDown) ->
                if (scrolling && canScrollDown) {
                    autoScrollEnabled = false
                } else if (!canScrollDown) {
                    autoScrollEnabled = true
                }
            }
    }

    // Smart auto-scroll on new messages and live streaming deltas
    val lastMessage = displayedMessages.lastOrNull()
    LaunchedEffect(displayedMessages.size, lastMessage?.id, lastMessage?.content?.length) {
        if (displayedMessages.isEmpty()) return@LaunchedEffect
        val lastIdx = displayedMessages.lastIndex
        if (autoScrollEnabled) {
            if (isStreaming) {
                listState.scrollToItem(lastIdx)
            } else {
                listState.animateScrollToItem(lastIdx)
            }
        }
    }

    var isTtsSpeaking by remember { mutableStateOf(false) }
    val cooldownMap by repository.cooldownSnapshotFlow.collectAsStateWithLifecycle()
    var cooldownNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(cooldownMap) {
        val maxUntil = cooldownMap.values.maxOrNull() ?: 0L
        while (maxUntil > System.currentTimeMillis()) {
            cooldownNowMs = System.currentTimeMillis()
            delay(1000L)
        }
        cooldownNowMs = System.currentTimeMillis()
    }
    val activeCooldownSec = remember(cooldownMap, cooldownNowMs) {
        cooldownMap.values
            .map { ((it - cooldownNowMs) / 1000L).toInt() }
            .filter { it > 0 }
            .maxOrNull() ?: 0
    }

    val ttsController = remember(context) {
        ReadAloudController(
            context = context,
            onSpeakingIdChanged = { id -> isTtsSpeaking = (id != null) }
        )
    }

    // Refresh Smart Context (Time Profile, Geofence Zone, Eco Mode, optional Ambient dB Whisper)
    LaunchedEffect(smartZones, timeProfilesEnabled, autoEcoMode, ambientWhisperEnabled) {
        val ambientDb = if (ambientWhisperEnabled) {
            runCatching { SmartContextEngine.sampleAmbientDecibels(context) }.getOrNull()
        } else null
        val snap = SmartContextEngine.captureSnapshot(
            context = context,
            savedZones = smartZones,
            timeProfilesEnabled = timeProfilesEnabled,
            autoEcoEnabled = autoEcoMode,
            ambientDb = ambientDb
        )
        smartSnapshot = snap
        when (snap.noiseLevel) {
            AmbientNoiseLevel.QUIET_WHISPER -> ttsController.setVolumeGain(0.35f)
            AmbientNoiseLevel.NOISY_BOOST -> ttsController.setVolumeGain(1.0f)
            AmbientNoiseLevel.NORMAL -> ttsController.setVolumeGain(0.85f)
        }
    }

    fun cancelInFlightRequest() {
        activeJob?.cancel()
        activeJob = null
        isStreaming = false
        ttsController.stop()
    }

    fun startFreshConversation() {
        cancelInFlightRequest()
        actionQueue = emptyList()
        errorBanner = null
        lastFailedPrompt = null
        showHistoryDrawer = false
        scope.launch {
            val freshModel = runCatching { repository.resolveFreshModel(selectedModel?.model?.id) }.getOrNull()
            val newId = repository.createConversation("New Chat", freshModel?.model?.id, null)
            repository.setActiveConversationId(newId)
            autoScrollEnabled = true
            statusBanner = "OK // Started fresh conversation. Previous thread saved."
        }
    }

    fun saveQuickNote(rawMemo: String) {
        val memo = rawMemo.trim()
        if (memo.isEmpty()) {
            showVoiceNotesSheet = true
            return
        }
        scope.launch {
            isSavingNote = true
            statusBanner = "NOTE // Cleaning & structuring memo…"
            runCatching {
                val freshModel = runCatching { repository.resolveFreshModel(selectedModel?.model?.id) }.getOrNull()
                repository.saveVoiceQuickNote(memo, freshModel)
            }.onSuccess { saved ->
                statusBanner = "OK // Saved note \"${saved.title}\" to Voice Notes Inbox."
                if (!muteTts) ttsController.speak(System.currentTimeMillis(), "Saved note: ${saved.title}")
                val convId = activeConvId ?: repository.getOrCreateActiveConversationId(
                    defaultTitle = "Note: ${saved.title}".take(40)
                )
                repository.appendInstantExchange(
                    conversationId = convId,
                    userText = "/note $memo",
                    assistantText = "**Saved Voice Note: ${saved.title}**\n\n${saved.markdownContent}",
                    modelLabel = "VOICE NOTE"
                )
            }.onFailure { err ->
                errorBanner = err.message ?: "Failed to save note."
            }
            isSavingNote = false
        }
    }

    fun runRoutine(routine: AssistantRoutine) {
        cancelInFlightRequest()
        actionQueue = emptyList()
        errorBanner = null
        lastFailedPrompt = null
        autoScrollEnabled = true
        activeJob = scope.launch {
            isStreaming = true
            try {
                val freshModel = runCatching { repository.resolveFreshModel(selectedModel?.model?.id) }.getOrNull()
                if (freshModel != null) selectedModel = freshModel
                val result = repository.executeRoutine(
                    routine = routine,
                    preferredModel = freshModel,
                    onProgress = { status -> statusBanner = status }
                )
                val convId = activeConvId ?: repository.getOrCreateActiveConversationId(
                    defaultTitle = routine.name.take(40),
                    modelDbId = freshModel?.model?.id
                )
                repository.appendInstantExchange(
                    conversationId = convId,
                    userText = "⚡ Routine: ${routine.name}",
                    assistantText = result.summaryText,
                    modelLabel = result.modelLabel
                )
                if (result.pendingDeviceAction != null) {
                    actionQueue = listOf(result.pendingDeviceAction)
                }
                val speakable = result.spokenText
                if (!muteTts && !speakable.isNullOrBlank()) {
                    ttsController.speak(System.currentTimeMillis(), speakable)
                }
                statusBanner = "OK // Routine \"${routine.name}\" complete."
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                errorBanner = err.message ?: "Routine failed."
            } finally {
                isStreaming = false
            }
        }
    }

    fun handlePromptSubmission(rawPrompt: String) {
        val rawTrimmed = rawPrompt.trim()
        if (rawTrimmed.isEmpty()) return
        val clean = DeviceActionEngine.preprocessVoiceCommand(rawTrimmed, quickContacts)
        transcriptInput = ""
        statusBanner = null
        errorBanner = null
        lastFailedPrompt = null
        autoScrollEnabled = true

        followUpChips = emptyList()
        val lowerClean = clean.lowercase()
        if (lowerClean == "/ghost" || lowerClean == "/incognito") {
            ghostMode = !ghostMode
            if (!ghostMode) ghostMessages = emptyList()
            statusBanner = if (ghostMode) "GHOST MODE // ON — Nothing saved to DB or history." else "GHOST MODE // OFF — Returned to saved chat."
            return
        }

        // 0a. Voice Quick-Notes commands (/note <memo>, /notes, /inbox)
        if (lowerClean == "/notes" || lowerClean == "/inbox" || lowerClean == "/note") {
            showVoiceNotesSheet = true
            return
        }
        if (lowerClean.startsWith("/note ")) {
            saveQuickNote(clean.removePrefix("/note").removePrefix("/NOTE").trim())
            return
        }

        // 0b. Custom Assistant Routine trigger phrase match
        val matchedRoutine = routines.firstOrNull { r ->
            val trig = r.triggerPhrase.trim().lowercase()
            trig.isNotEmpty() && (lowerClean == trig || lowerClean == "/routine $trig" || lowerClean == "run $trig")
        }
        if (matchedRoutine != null) {
            runRoutine(matchedRoutine)
            return
        }

        // 0c. Voice Disambiguation Reply ("first", "second", "Rahul Verma") when duplicate contacts are pending
        val currentHead = actionQueue.firstOrNull()
        if (currentHead != null && DeviceActionEngine.ambiguousMatchesOf(currentHead).size >= 2) {
            val disambiguated = DeviceActionEngine.resolveDisambiguationVoiceReply(rawTrimmed, currentHead)
            if (disambiguated != null) {
                val updatedQueue = listOf(disambiguated) + actionQueue.drop(1)
                actionQueue = updatedQueue
                statusBanner = when (disambiguated) {
                    is DeviceAction.DialPhone -> disambiguated.displayBanner
                    is DeviceAction.WhatsAppCall -> disambiguated.displayBanner
                    else -> "OK // Selected ${DeviceActionEngine.contactTargetNameOf(disambiguated) ?: disambiguated.title}"
                }
                val confirmSpeech = DeviceActionEngine.buildSpokenConfirmationPrompt(
                    action = disambiguated,
                    stepIndex = 0,
                    totalSteps = updatedQueue.size
                )
                if (!muteTts) ttsController.speak(System.currentTimeMillis(), confirmSpeech)
                return
            }
        }

        // 1. Check Sequential Multi-Action Chain Parser (Calls, WhatsApp Voice/Video/Message, SMS, Email, UPI, Maps, Calendar, YouTube, Alarms, Timers, App Launch)
        val parsedChain = DeviceActionEngine.parseActionChainFromPrompt(clean, quickContacts, customApps)
        if (parsedChain.isNotEmpty()) {
            actionQueue = parsedChain
            val firstAction = parsedChain.first()
            statusBanner = when {
                parsedChain.size > 1 -> "ACTION CHAIN // ${parsedChain.size} steps queued — Step 1: ${firstAction.title}"
                firstAction is DeviceAction.DialPhone && firstAction.phoneNumber.isNotBlank() -> firstAction.displayBanner
                firstAction is DeviceAction.WhatsAppCall && firstAction.phoneNumber.isNotBlank() -> firstAction.displayBanner
                else -> null
            }
            val spoken = DeviceActionEngine.buildSpokenConfirmationPrompt(
                action = firstAction,
                stepIndex = 0,
                totalSteps = parsedChain.size
            )
            if (!muteTts) ttsController.speak(System.currentTimeMillis(), spoken)
            val assistantSummary = if (parsedChain.size > 1) {
                buildString {
                    appendLine("**Action Chain Ready (${parsedChain.size} steps):**")
                    parsedChain.forEachIndexed { idx, step ->
                        appendLine("${idx + 1}. **${step.badge}** — ${step.title}")
                    }
                    append("\n$spoken")
                }
            } else {
                spoken
            }
            if (ghostMode) {
                val now = System.currentTimeMillis()
                ghostMessages = ghostMessages + listOf(
                    MessageEntity(id = -now, conversationId = -1L, role = "USER", content = clean, createdAt = now),
                    MessageEntity(id = -(now + 1), conversationId = -1L, role = "ASSISTANT", content = assistantSummary, modelLabel = firstAction.badge, createdAt = now + 1)
                )
            } else {
                scope.launch {
                    val convId = activeConvId ?: repository.getOrCreateActiveConversationId(
                        defaultTitle = clean.take(40)
                    )
                    repository.appendInstantExchange(
                        conversationId = convId,
                        userText = clean,
                        assistantText = assistantSummary,
                        modelLabel = firstAction.badge
                    )
                }
            }
            return
        }

        // 2. Check Smart Home / Webhook trigger phrases, then fall through to streaming LLM
        //    reading fresh provider baseUrl / proxyUrl / API key / modelId at exact send time.
        cancelInFlightRequest()
        actionQueue = emptyList()
        activeJob = scope.launch {
            isStreaming = true
            try {
                val matchedWebhook = runCatching { repository.matchWebhookByPhrase(clean) }.getOrNull()
                if (matchedWebhook != null) {
                    statusBanner = "WEBHOOK // Firing ${matchedWebhook.name}…"
                    val res = repository.executeWebhook(matchedWebhook)
                    val replyText = res.fold(
                        onSuccess = { "OK // Triggered **${matchedWebhook.name}** (${matchedWebhook.method}): $it" },
                        onFailure = { "ERR // Webhook **${matchedWebhook.name}** failed: ${it.message}" }
                    )
                    if (!ghostMode) {
                        val convId = activeConvId ?: repository.getOrCreateActiveConversationId(
                            defaultTitle = "Webhook: ${matchedWebhook.name}".take(40)
                        )
                        repository.appendInstantExchange(
                            conversationId = convId,
                            userText = clean,
                            assistantText = replyText,
                            modelLabel = "WEBHOOK"
                        )
                    } else {
                        val now = System.currentTimeMillis()
                        ghostMessages = ghostMessages + listOf(
                            MessageEntity(id = -now, conversationId = -1L, role = "USER", content = clean, createdAt = now),
                            MessageEntity(id = -(now + 1), conversationId = -1L, role = "ASSISTANT", content = replyText, modelLabel = "WEBHOOK", createdAt = now + 1)
                        )
                    }
                    statusBanner = if (res.isSuccess) "OK // Webhook fired." else "ERR // Webhook failed."
                    if (!muteTts && res.isSuccess) ttsController.speak(System.currentTimeMillis(), "Triggered ${matchedWebhook.name}.")
                    return@launch
                }
                val freshModel = repository.resolveFreshModel(selectedModel?.model?.id)
                    ?: throw IllegalStateException("No AI model configured. Open Settings to add a provider & API key.")
                selectedModel = freshModel

                val (effectiveModel, budgetNotice) = repository.resolveBudgetDowngradeModel(
                    requestedModel = freshModel,
                    availableModels = availableModels,
                    forceFlagshipOverride = forceFlagshipOverride
                )
                if (budgetNotice != null) statusBanner = budgetNotice

                val constraintBlock = activeConstraints.joinToString("\n") { "- ${it.instruction}" }
                val contactsDirectory = if (ghostMode) {
                    DeviceActionEngine.formatContactsForSystemPrompt(quickContacts)
                } else ""
                val effectiveSystemPrompt = buildString {
                    append("You are AllChat Assistant in Quick Overlay mode. Answer clearly, concisely, and naturally while remembering all prior turns in this conversation. When the user asks to call someone, send a WhatsApp message, send an SMS or email, pay via UPI, navigate or search Maps, create a calendar event, play/open YouTube, set an alarm/timer, or open an app, call the execute_device_action tool.")
                    if (reasoningEffort.systemHint.isNotBlank()) {
                        append("\n\nReasoning Effort (${reasoningEffort.displayLabel}): ")
                        append(reasoningEffort.systemHint)
                    }
                    if (constraintBlock.isNotBlank()) {
                        append("\n\nStrict User Output Rules:\n")
                        append(constraintBlock)
                    }
                    if (contactsDirectory.isNotBlank()) {
                        append("\n\n")
                        append(contactsDirectory)
                    }
                }

                if (ghostMode) {
                    val now = System.currentTimeMillis()
                    val userId = -now
                    val aiId = -(now + 1)
                    val priorHistory = ghostMessages.map { (if (it.role == "USER") "user" else "assistant") to it.content }
                    ghostMessages = ghostMessages + listOf(
                        MessageEntity(id = userId, conversationId = -1L, role = "USER", content = clean, createdAt = now),
                        MessageEntity(id = aiId, conversationId = -1L, role = "ASSISTANT", content = "", modelLabel = "${effectiveModel.model.displayName} (GHOST)", createdAt = now + 1)
                    )
                    val finalReply = repository.streamGhostChatTurn(
                        history = priorHistory,
                        userText = clean,
                        model = effectiveModel,
                        options = ChatOptions(
                            systemPrompt = effectiveSystemPrompt,
                            temperature = reasoningEffort.temperatureOverride,
                            enableTools = false,
                            reasoningEffort = reasoningEffort,
                            supportsVisionInput = effectiveModel.model.supportsImageInput
                        ),
                        onDelta = { partial ->
                            ghostMessages = ghostMessages.map { if (it.id == aiId) it.copy(content = partial) else it }
                        }
                    )
                    if (finalReply.isNotBlank()) {
                        ghostMessages = ghostMessages.map { if (it.id == aiId) it.copy(content = finalReply) else it }
                        val interceptedGhost = DeviceActionEngine.parseFromAssistantReply(
                            userPrompt = clean,
                            assistantReply = finalReply,
                            contacts = quickContacts,
                            customApps = customApps
                        )
                        if (interceptedGhost != null) {
                            actionQueue = listOf(interceptedGhost)
                            if (interceptedGhost is DeviceAction.DialPhone && interceptedGhost.phoneNumber.isNotBlank()) {
                                statusBanner = interceptedGhost.displayBanner
                            } else if (interceptedGhost is DeviceAction.WhatsAppCall && interceptedGhost.phoneNumber.isNotBlank()) {
                                statusBanner = interceptedGhost.displayBanner
                            }
                            val promptMsg = DeviceActionEngine.buildSpokenConfirmationPrompt(interceptedGhost)
                            if (!muteTts) ttsController.speak(aiId, promptMsg)
                        } else if (!muteTts) {
                            ttsController.speak(aiId, finalReply)
                        }
                        scope.launch {
                            val chips = repository.generateFollowUpChips(clean, finalReply, effectiveModel)
                            if (chips.isNotEmpty()) followUpChips = chips
                        }
                    }
                    errorBanner = null
                    lastFailedPrompt = null
                } else {
                    val convId = activeConvId ?: repository.getOrCreateActiveConversationId(
                        defaultTitle = clean.take(40),
                        modelDbId = effectiveModel.model.id
                    )
                    val leafId = visibleMessages.lastOrNull()?.id
                    var hadRequestError = false
                    var streamedAssistantBuf = StringBuilder()
                    repository.sendChatMessage(
                        conversationId = convId,
                        promptText = clean,
                        attachmentLocalUri = null,
                        selectableModel = effectiveModel,
                        parentMessageId = leafId,
                        options = ChatOptions(
                            systemPrompt = effectiveSystemPrompt,
                            temperature = reasoningEffort.temperatureOverride,
                            enableTools = toolsEnabled,
                            reasoningEffort = reasoningEffort,
                            supportsVisionInput = effectiveModel.model.supportsImageInput
                        ),
                        enableWebSearch = webSearchEnabled,
                        onGroundingStatus = { status -> statusBanner = status },
                        onSentenceReady = { sentence ->
                            streamedAssistantBuf.append(sentence).append(" ")
                            if (!muteTts) ttsController.enqueueSentence(sentence)
                        },
                        onDeviceActionTriggered = { triggered ->
                            val enriched = when (triggered) {
                                is DeviceAction.DialPhone -> if (triggered.contactLabel.isNullOrBlank() && triggered.phoneNumber.isNotBlank()) {
                                    val matched = DeviceActionEngine.resolveContactByPhone(triggered.phoneNumber, quickContacts)
                                    if (matched != null) triggered.copy(contactLabel = matched.name) else triggered
                                } else triggered
                                is DeviceAction.WhatsAppCall -> if (triggered.contactLabel.isNullOrBlank() && triggered.phoneNumber.isNotBlank()) {
                                    val matched = DeviceActionEngine.resolveContactByPhone(triggered.phoneNumber, quickContacts)
                                    if (matched != null) triggered.copy(contactLabel = matched.name) else triggered
                                } else triggered
                                else -> triggered
                            }
                            val updatedQueue = if (actionQueue.contains(enriched)) actionQueue else actionQueue + enriched
                            actionQueue = updatedQueue
                            val activeHead = updatedQueue.firstOrNull() ?: enriched
                            if (activeHead is DeviceAction.DialPhone && activeHead.phoneNumber.isNotBlank()) {
                                statusBanner = activeHead.displayBanner
                            } else if (activeHead is DeviceAction.WhatsAppCall && activeHead.phoneNumber.isNotBlank()) {
                                statusBanner = activeHead.displayBanner
                            } else if (updatedQueue.size > 1) {
                                statusBanner = "ACTION CHAIN // ${updatedQueue.size} steps queued — Step 1: ${activeHead.title}"
                            }
                            if (updatedQueue.size == 1 && !muteTts) {
                                ttsController.speak(
                                    System.currentTimeMillis(),
                                    DeviceActionEngine.buildSpokenConfirmationPrompt(activeHead)
                                )
                            }
                        },
                        onRequestError = { msg ->
                            hadRequestError = true
                            errorBanner = msg
                            lastFailedPrompt = clean
                        }
                    )
                    if (!hadRequestError) {
                        errorBanner = null
                        lastFailedPrompt = null
                        val replyForChips = streamedAssistantBuf.toString().trim().ifBlank {
                            visibleMessages.lastOrNull { it.role == "ASSISTANT" }?.content.orEmpty()
                        }
                        if (replyForChips.length >= 40) {
                            scope.launch {
                                val chips = repository.generateFollowUpChips(clean, replyForChips, effectiveModel)
                                if (chips.isNotEmpty()) followUpChips = chips
                            }
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                errorBanner = err.message ?: "Assistant request failed. Tap Retry to try again."
                lastFailedPrompt = clean
            } finally {
                isStreaming = false
            }
        }
    }

    val voiceController = remember(context) {
        VoiceInputController(
            context = context,
            onPartialText = { partial -> transcriptInput = partial },
            onFinalText = { finalText ->
                val trimmed = finalText.trim()
                if (trimmed.isNotEmpty()) {
                    handlePromptSubmission(trimmed)
                }
            },
            onListeningChanged = { active -> isListening = active },
            onFailure = { err -> statusBanner = err },
            onRmsChanged = { rms -> liveRms = ((rms + 2f) / 12f).coerceIn(0f, 1f) }
        )
    }

    DisposableEffect(voiceController, ttsController) {
        onDispose {
            activeJob?.cancel()
            voiceController.release()
            ttsController.release()
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                voiceController.start()
            } else {
                statusBanner = "Microphone permission denied. You can still type below."
            }
        }
    )

    fun startMic(playChime: Boolean = true) {
        ttsController.stop()
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (playChime) EarbudChimePlayer.playMicActivationChime()
            voiceController.start()
        } else {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun launchDialAction(dial: DeviceAction.DialPhone) {
        val enriched = if (dial.contactLabel.isNullOrBlank() && dial.phoneNumber.isNotBlank()) {
            val matched = DeviceActionEngine.resolveContactByPhone(dial.phoneNumber, quickContacts)
            if (matched != null) dial.copy(contactLabel = matched.name) else dial
        } else {
            dial
        }
        statusBanner = enriched.displayBanner
        launchingCallViaBridge = autoDialWebhookUrl.trim().startsWith("http")
        launchingCallAction = enriched
        scope.launch {
            DeviceActionEngine.executeDialWithBridge(context, enriched, autoDialWebhookUrl)
                .onSuccess { res ->
                    launchingCallViaBridge = res.usedAutoDialBridge
                    statusBanner = "ACTION // ${res.message}"
                }
                .onFailure { err ->
                    launchingCallAction = null
                    errorBanner = err.message ?: "Action execution failed"
                }
        }
    }

    fun advanceAssistantActionQueue(completedMsg: String? = null) {
        val remaining = actionQueue.drop(1)
        actionQueue = remaining
        val next = remaining.firstOrNull()
        if (next != null) {
            statusBanner = when (next) {
                is DeviceAction.DialPhone -> if (next.phoneNumber.isNotBlank()) next.displayBanner else "NEXT STEP (${remaining.size} LEFT) // ${next.title}"
                is DeviceAction.WhatsAppCall -> if (next.phoneNumber.isNotBlank()) next.displayBanner else "NEXT STEP (${remaining.size} LEFT) // ${next.title}"
                else -> "NEXT STEP (${remaining.size} LEFT) // ${next.title}"
            }
            if (!muteTts) {
                val nextPrompt = DeviceActionEngine.buildSpokenConfirmationPrompt(
                    action = next,
                    stepIndex = 1,
                    totalSteps = remaining.size + 1
                )
                scope.launch {
                    delay(450L)
                    ttsController.speak(System.currentTimeMillis(), nextPrompt)
                }
            }
        } else if (completedMsg != null) {
            statusBanner = "OK // $completedMsg"
        }
    }

    // Automatically re-open microphone for hands-free voice disambiguation ("first" / "second")
    // right after TTS finishes reading duplicate contact matches!
    val activeHeadAction = actionQueue.firstOrNull()
    val needsVoiceDisambiguation = activeHeadAction != null &&
        DeviceActionEngine.ambiguousMatchesOf(activeHeadAction).size >= 2
    LaunchedEffect(activeHeadAction, needsVoiceDisambiguation) {
        if (needsVoiceDisambiguation) {
            // Wait briefly for TTS to begin, then wait until speaking ends (capped so it never stalls)
            delay(400L)
            var waited = 0L
            while (isTtsSpeaking && waited < 6500L) {
                delay(200L)
                waited += 200L
            }
            if (!isListening && actionQueue.firstOrNull() == activeHeadAction) {
                startMic(playChime = true)
            }
        }
    }

    fun isActionReadyForEarbudExecute(action: DeviceAction?): Boolean {
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

    fun handleEarbudSingleTap() {
        launchingCallAction?.let { activeCall ->
            EarbudChimePlayer.playConfirmChime()
            launchDialAction(activeCall)
            return
        }
        val currentAction = actionQueue.firstOrNull()
        when {
            // 1. Active queued Device Action ready -> Immediately CONFIRM & EXECUTE, then auto-advance to Step 2!
            currentAction != null && isActionReadyForEarbudExecute(currentAction) -> {
                EarbudChimePlayer.playConfirmChime()
                ttsController.stop()
                if (!muteTts) ttsController.speak(System.currentTimeMillis(), "Executing now")
                if (currentAction is DeviceAction.DialPhone) {
                    launchDialAction(currentAction)
                    if (actionQueue.size > 1) {
                        advanceAssistantActionQueue()
                    }
                } else {
                    DeviceActionEngine.execute(context, currentAction)
                        .onSuccess { msg ->
                            advanceAssistantActionQueue(msg)
                        }
                        .onFailure { err ->
                            errorBanner = err.message ?: "Action execution failed"
                        }
                }
            }
            // 2. Active TTS playback -> Voice Barge-In (stop speaking & immediately open mic)
            isTtsSpeaking -> {
                ttsController.stop()
                statusBanner = "BARGE-IN // Interrupted reply — listening…"
                startMic(playChime = true)
            }
            // 3. Already listening -> stop listening
            isListening -> {
                voiceController.stop()
            }
            // 4. Mic idle -> toggle microphone listening ON with activation chime
            else -> {
                startMic(playChime = true)
            }
        }
    }

    fun handleEarbudCancelTap() {
        if (launchingCallAction != null) {
            EarbudChimePlayer.playCancelChime()
            launchingCallAction = null
            statusBanner = "EARBUD // Dismissed calling overlay."
        } else if (actionQueue.isNotEmpty()) {
            EarbudChimePlayer.playCancelChime()
            val remaining = actionQueue.drop(1)
            actionQueue = remaining
            ttsController.stop()
            if (remaining.isNotEmpty()) {
                val next = remaining.first()
                statusBanner = "EARBUD // Skipped step — Next: ${next.title}"
                if (!muteTts) {
                    ttsController.speak(
                        System.currentTimeMillis(),
                        "Skipped. " + DeviceActionEngine.buildSpokenConfirmationPrompt(next, 1, remaining.size + 1)
                    )
                }
            } else {
                statusBanner = "EARBUD // Cancelled — returning to listening…"
                if (!muteTts) ttsController.speak(System.currentTimeMillis(), "Cancelled")
                scope.launch {
                    delay(850L)
                    startMic(playChime = true)
                }
            }
        } else if (isTtsSpeaking) {
            ttsController.stop()
            statusBanner = "BARGE-IN // Speech stopped — listening…"
            startMic(playChime = true)
        }
    }

    // Active Bluetooth Earbud MediaSession + Silent AudioTrack Keep-Alive while Live Assistant is open
    DisposableEffect(context) {
        val bridge = MediaButtonBridge(
            context = context,
            onEarbudTriggerAsk = { handleEarbudSingleTap() },
            onEarbudNextAction = { handleEarbudCancelTap() },
            enableSilentKeepAlive = true
        ).also { it.start(context) }
        onDispose { bridge.stop() }
    }

    // Also consume hardware media keys forwarded via Activity.dispatchKeyEvent
    LaunchedEffect(Unit) {
        EarbudKeyBus.events.collect { cmd ->
            when (cmd) {
                EarbudCommand.SINGLE_TAP -> handleEarbudSingleTap()
                EarbudCommand.DOUBLE_TAP_OR_NEXT -> handleEarbudCancelTap()
            }
        }
    }

    LaunchedEffect(autoStartMic) {
        if (autoStartMic) {
            startMic(playChime = true)
        }
    }

    // Foreground "Hey AllChat" Wake-Phrase Listener when enabled in Settings and idle
    DisposableEffect(wakePhraseEnabled, isListening, isStreaming, showHistoryDrawer, showModelPicker) {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val shouldArm = wakePhraseEnabled && micGranted && !isListening && !isStreaming && !showHistoryDrawer && !showModelPicker
        val listener = if (shouldArm) {
            WakePhraseListener(
                context = context,
                onWakeDetected = { trailing ->
                    if (trailing.isNotBlank()) {
                        handlePromptSubmission(trailing)
                    } else {
                        startMic()
                    }
                }
            ).also { it.start() }
        } else null
        onDispose {
            listener?.stop()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "assistant_wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val filteredHistory = remember(allConversations, historySearch) {
        val q = historySearch.trim()
        if (q.isEmpty()) allConversations
        else allConversations.filter { it.title.contains(q, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Fixed-height flex-column overlay sheet:
        // Pinned Header + Scrollable Message List (weight 1f) + Pinned Bottom Input Bar
        Surface(
            color = SwissCanvas,
            shape = RectangleShape,
            border = BorderStroke(1.dp, SwissInk),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .clickable(enabled = false) {}
                .testTag("assistant_overlay_sheet")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. PINNED TOP HEADER
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissInk)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clickable Model Selector Pill
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showModelPicker = !showModelPicker }
                                .padding(end = 8.dp)
                                .testTag("assistant_model_selector_pill")
                        ) {
                            Text(
                                text = "ASSISTANT // TAP TO SWITCH MODEL",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = selectedModel?.let { "${it.model.displayName} · ${it.provider.name}" }
                                        ?: "NO MODEL — CONFIGURE IN SETTINGS",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SwissWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Select model",
                                    tint = SwissSignalRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Compact 429/503 Cooldown Timer Badge in Live Assistant Header
                            if (activeCooldownSec > 0) {
                                Text(
                                    text = "429:${activeCooldownSec}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite,
                                    modifier = Modifier
                                        .background(SwissSignalRed, RectangleShape)
                                        .padding(horizontal = 5.dp, vertical = 3.dp)
                                        .testTag("assistant_cooldown_badge")
                                )
                            }
                            // Incognito Ghost Mode Toggle in Assistant Header
                            Box(
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        if (ghostMode) SwissSignalRed else SwissWhite.copy(alpha = 0.65f),
                                        RectangleShape
                                    )
                                    .background(if (ghostMode) SwissSignalRed else Color.Transparent)
                                    .clickable {
                                        ghostMode = !ghostMode
                                        if (!ghostMode) ghostMessages = emptyList()
                                        followUpChips = emptyList()
                                        statusBanner = if (ghostMode) "GHOST // Zero DB writes." else "GHOST // Off."
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("assistant_overlay_ghost_toggle")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VisibilityOff,
                                    contentDescription = if (ghostMode) "Disable Ghost Mode" else "Enable Incognito Ghost Mode",
                                    tint = SwissWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // History Clock Icon Button (opens scrollable thread drawer inside Assistant)
                            Box(
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        if (showHistoryDrawer) SwissSignalRed else SwissWhite.copy(alpha = 0.65f),
                                        RectangleShape
                                    )
                                    .background(if (showHistoryDrawer) SwissSignalRed else Color.Transparent)
                                    .clickable {
                                        showModelPicker = false
                                        showHistoryDrawer = !showHistoryDrawer
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("assistant_overlay_history")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = "Assistant Chat History",
                                    tint = SwissWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Explicit New Chat / Clear Button (aborts in-flight jobs & resets cleanly)
                            Row(
                                modifier = Modifier
                                    .border(1.dp, SwissWhite.copy(alpha = 0.7f), RectangleShape)
                                    .clickable { startFreshConversation() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("assistant_overlay_new_chat"),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "New Chat",
                                    tint = SwissWhite,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "NEW",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite
                                )
                            }

                            if (onOpenFullApp != null) {
                                Text(
                                    text = "[FULL]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite,
                                    modifier = Modifier
                                        .clickable(onClick = onOpenFullApp)
                                        .padding(vertical = 4.dp)
                                )
                            }

                            Icon(
                                imageVector = if (muteTts) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                contentDescription = "Toggle voice reply",
                                tint = if (muteTts) SwissConcrete else SwissSignalRed,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        muteTts = !muteTts
                                        if (muteTts) ttsController.stop()
                                    }
                            )

                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close assistant",
                                tint = SwissWhite,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable(onClick = onDismiss)
                                    .testTag("assistant_overlay_close")
                            )
                        }
                    }

                    // Top ACTION // Banner (keeps confirmed Contact Name like "Manisha ❤️✨" visible at top)
                    AnimatedVisibility(visible = statusBanner?.startsWith("ACTION //") == true) {
                        statusBanner?.let { actionTop ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissSignalRed)
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                                    .testTag("assistant_top_action_banner"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = actionTop,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss action banner",
                                    tint = SwissWhite,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { statusBanner = null }
                                )
                            }
                        }
                    }

                    // Inline Model Picker Dropdown when header pill is tapped
                    AnimatedVisibility(visible = showModelPicker) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissPaper)
                                .border(1.dp, SwissInk, RectangleShape)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissSubtleFill)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SELECT ACTIVE MODEL // SYNCED ACROSS ALLCHAT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissMuted
                                )
                                Text(
                                    text = "[CLOSE]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed,
                                    modifier = Modifier.clickable { showModelPicker = false }
                                )
                            }
                            if (availableModels.isEmpty()) {
                                Text(
                                    text = "No models configured yet. Add a provider & fetch models in Settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissMuted,
                                    modifier = Modifier.padding(12.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 190.dp)
                                ) {
                                    items(availableModels, key = { it.model.id }) { candidate ->
                                        val isActive = candidate.model.id == selectedModel?.model?.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isActive) SwissInk else SwissPaper)
                                                .clickable {
                                                    selectedModel = candidate
                                                    showModelPicker = false
                                                    scope.launch {
                                                        repository.setActiveModelDbId(candidate.model.id)
                                                        activeConvId?.let { cid ->
                                                            repository.updateConversationModel(cid, candidate.model.id)
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = candidate.model.displayName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isActive) SwissWhite else SwissInk,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${candidate.provider.name} · ${candidate.model.modelId}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isActive) SwissSignalRed else SwissMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isActive) {
                                                Text(
                                                    text = "ACTIVE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SwissSignalRed
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Interactive Voice Barge-In Audio Waveform Strip (Single tap cancels TTS & starts listening)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(SwissInk)
                            .clickable {
                                if (isTtsSpeaking) {
                                    ttsController.stop()
                                    statusBanner = "BARGE-IN // Interrupted speech — listening…"
                                    startMic(playChime = true)
                                } else if (isListening) {
                                    voiceController.stop()
                                } else {
                                    startMic(playChime = true)
                                }
                            }
                            .testTag("assistant_waveform_barge_in"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            val barCount = 28
                            val spacing = size.width / barCount
                            val midY = size.height / 2f
                            for (i in 0 until barCount) {
                                val norm = i / barCount.toFloat()
                                val envelope = sin(norm * Math.PI).toFloat()
                                val amplitude = when {
                                    isListening -> (0.25f + liveRms * 0.75f) * envelope * (0.65f + 0.35f * sin(wavePhase + i * 0.5f))
                                    isTtsSpeaking || isStreaming -> 0.55f * envelope * (0.5f + 0.5f * sin(wavePhase * 1.4f + i * 0.6f))
                                    else -> 0.08f * envelope
                                }
                                val barHalfH = (size.height * 0.40f * amplitude).coerceAtLeast(2f)
                                val x = spacing * i + spacing / 2f
                                drawLine(
                                    color = if (isListening || isTtsSpeaking || isStreaming) SwissSignalRed else SwissConcrete,
                                    start = Offset(x, midY - barHalfH),
                                    end = Offset(x, midY + barHalfH),
                                    strokeWidth = (spacing * 0.45f).coerceIn(3f, 8f),
                                    cap = StrokeCap.Butt
                                )
                            }
                        }
                        val waveHint = when {
                            isTtsSpeaking -> "SPEAKING // TAP WAVEFORM OR EARBUD TO INTERRUPT"
                            isListening -> "LISTENING // TAP WAVEFORM TO STOP"
                            else -> "🎧 EARBUD READY // TAP WAVEFORM TO TALK"
                        }
                        Text(
                            text = waveHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissWhite.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .background(SwissInk.copy(alpha = 0.65f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    // Quick-Action & Parity Controls Strip (Pinned under waveform)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistantQuickChip(
                            icon = Icons.Outlined.Language,
                            label = if (webSearchEnabled) "SEARCH: ON" else "SEARCH",
                            accent = webSearchEnabled,
                            onClick = { webSearchEnabled = !webSearchEnabled },
                            testTag = "assist_chip_web_search"
                        )
                        AssistantQuickChip(
                            icon = Icons.Outlined.Build,
                            label = if (toolsEnabled) "TOOLS: ON" else "TOOLS",
                            accent = toolsEnabled,
                            onClick = { toolsEnabled = !toolsEnabled },
                            testTag = "assist_chip_tools"
                        )
                        AssistantQuickChip(
                            icon = Icons.Outlined.CameraAlt,
                            label = "LIVE LENS",
                            onClick = {
                                voiceController.stop()
                                ttsController.stop()
                                (onOpenLiveLensExplain ?: onOpenTranslateCamera).invoke()
                            },
                            testTag = "assist_chip_live_lens"
                        )
                        AssistantQuickChip(
                            icon = Icons.Outlined.Translate,
                            label = "TRANSLATE",
                            onClick = {
                                voiceController.stop()
                                ttsController.stop()
                                onOpenTranslateCamera()
                            },
                            testTag = "assist_chip_translate_cam"
                        )
                        AssistantQuickChip(
                            icon = Icons.Outlined.Call,
                            label = "CALL",
                            onClick = {
                                actionQueue = listOf(DeviceAction.DialPhone(""))
                                statusBanner = "Pick a Quick Contact or type a number below."
                            },
                            testTag = "assist_chip_call"
                        )
                        AssistantQuickChip(
                            icon = Icons.Outlined.Chat,
                            label = "WHATSAPP",
                            onClick = {
                                actionQueue = listOf(DeviceAction.SendWhatsApp(phoneNumber = "", message = ""))
                                statusBanner = "Pick a Quick Contact or enter a WhatsApp number below."
                            },
                            testTag = "assist_chip_whatsapp"
                        )
                        AssistantQuickChip(
                            icon = Icons.Outlined.PlayCircleOutline,
                            label = "YOUTUBE",
                            onClick = { handlePromptSubmission("Open YouTube") },
                            testTag = "assist_chip_youtube"
                        )
                        AssistantQuickChip(
                            icon = Icons.Outlined.NoteAdd,
                            label = if (isSavingNote) "SAVING…" else "SAVE NOTE",
                            accent = isSavingNote,
                            onClick = {
                                val candidate = transcriptInput.trim().ifBlank {
                                    visibleMessages.lastOrNull()?.content?.trim().orEmpty()
                                }
                                if (candidate.isNotBlank()) {
                                    transcriptInput = ""
                                    saveQuickNote(candidate)
                                } else {
                                    showVoiceNotesSheet = true
                                }
                            },
                            testTag = "assist_chip_save_note"
                        )
                        AssistantQuickChip(
                            icon = Icons.Outlined.StickyNote2,
                            label = "NOTES (${voiceNotes.size})",
                            onClick = { showVoiceNotesSheet = true },
                            testTag = "assist_chip_notes_inbox"
                        )
                        AssistantQuickChip(
                            icon = Icons.Outlined.Alarm,
                            label = "TIMER 5M",
                            onClick = { handlePromptSubmission("Set a timer for 5 minutes") },
                            testTag = "assist_chip_timer"
                        )
                        if (onOpenToolkitHub != null) {
                            AssistantQuickChip(
                                icon = Icons.Outlined.Bolt,
                                label = "TOOLKIT",
                                accent = true,
                                onClick = {
                                    voiceController.stop()
                                    ttsController.stop()
                                    onOpenToolkitHub()
                                },
                                testTag = "assist_chip_toolkit_hub"
                            )
                        }
                    }

                    // Smart Context & Geofence Quick-Chips Strip
                    val zoneChips = smartSnapshot.matchedZone?.quickChips.orEmpty()
                    if (timeProfilesEnabled || smartSnapshot.isEcoModeActive || smartSnapshot.ambientDb != null || wakePhraseEnabled || zoneChips.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissCanvas)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (wakePhraseEnabled) {
                                Text(
                                    text = "HEY ALLCHAT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed
                                )
                            }
                            if (timeProfilesEnabled) {
                                Text(
                                    text = "CTX // ${smartSnapshot.timeProfile.label.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissInk
                                )
                            }
                            if (smartSnapshot.isEcoModeActive) {
                                Text(
                                    text = "ECO ${smartSnapshot.batteryPercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed
                                )
                            }
                            smartSnapshot.ambientDb?.let { db ->
                                val modeTag = when (smartSnapshot.noiseLevel) {
                                    AmbientNoiseLevel.QUIET_WHISPER -> "WHISPER"
                                    AmbientNoiseLevel.NOISY_BOOST -> "BOOST"
                                    AmbientNoiseLevel.NORMAL -> "NORM"
                                }
                                Text(
                                    text = "${db}dB $modeTag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissMuted
                                )
                            }
                            smartSnapshot.matchedZone?.let { zone ->
                                Text(
                                    text = "@${zone.name.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissVerifiedInk
                                )
                            }
                            zoneChips.forEach { chip ->
                                AssistantQuickChip(
                                    icon = Icons.Outlined.Bolt,
                                    label = chip.take(24).uppercase(),
                                    onClick = { handlePromptSubmission(chip) },
                                    testTag = "assist_zone_chip"
                                )
                            }
                        }
                    }

                    // 1-Tap Assistant Routines Strip (when user has configured Routines in Settings)
                    if (routines.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissSubtleFill)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ROUTINES //",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed
                            )
                            routines.forEach { routine ->
                                AssistantQuickChip(
                                    icon = Icons.Outlined.Bolt,
                                    label = routine.name.uppercase(),
                                    accent = false,
                                    onClick = { runRoutine(routine) },
                                    testTag = "assist_routine_chip_${routine.id}"
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SwissInk)
                    )

                    if (ghostMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissInk)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("assistant_ghost_banner"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GHOST MODE // INCOGNITO · NOT WRITTEN TO DB OR SEARCH",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "[ CLEAR ]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                modifier = Modifier.clickable { ghostMessages = emptyList() }
                            )
                        }
                    }

                    if (isBudgetWarning) {
                        val pct = ((todaySpendUsd / dailyBudgetConfig.dailyCapUsd) * 100.0).toInt()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF3C7))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BUDGET $pct% // ${if (forceFlagshipOverride) "FLAGSHIP OVERRIDE" else "AUTO-FLASH ACTIVE"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = if (forceFlagshipOverride) "[ AUTO-FLASH ]" else "[ OVERRIDE ]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                modifier = Modifier.clickable { forceFlagshipOverride = !forceFlagshipOverride }
                            )
                        }
                    }

                    // 2. SCROLLABLE MULTI-TURN CONVERSATION AREA (flex: 1)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(SwissCanvas)
                    ) {
                        if (displayedMessages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SwissPaper, RectangleShape)
                                        .border(1.dp, SwissInk, RectangleShape)
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (ghostMode) "GHOST MODE // EPHEMERAL RAM SESSION" else "READY // PERSISTENT MULTI-TURN ASSISTANT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                    Text(
                                        text = if (ghostMode) {
                                            "Everything spoken or typed here stays strictly in memory and disappears when you exit Ghost Mode or close the sheet."
                                        } else {
                                            "Speak or type below. Try \"Call Mom\", \"WhatsApp Rahul saying I'll be late\", \"Play lofi beats on YouTube\", or ask any follow-up question."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissMuted
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("assistant_overlay_message_list")
                            ) {
                                itemsIndexed(
                                    items = displayedMessages,
                                    key = { _, msg -> msg.id }
                                ) { idx, msg ->
                                    val isLastStreaming = isStreaming && idx == displayedMessages.lastIndex && msg.role == "ASSISTANT"
                                    OverlayMessageCard(
                                        turnNumber = idx + 1,
                                        message = msg,
                                        isStreamingThis = isLastStreaming,
                                        onBeamQr = { qrBeamPayload = msg.content }
                                    )
                                }

                                // 3 Ultra-Short Contextual Follow-Up Chips in Assistant Overlay
                                if (!isStreaming && followUpChips.isNotEmpty()) {
                                    item(key = "assistant_followup_chips") {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 2.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = "NEXT // 1-TAP FOLLOW-UPS",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SwissMuted
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                followUpChips.forEachIndexed { idx, chip ->
                                                    Row(
                                                        modifier = Modifier
                                                            .background(SwissPaper, RectangleShape)
                                                            .border(1.dp, SwissInk, RectangleShape)
                                                            .clickable { handlePromptSubmission(chip) }
                                                            .padding(horizontal = 9.dp, vertical = 6.dp)
                                                            .testTag("assistant_followup_chip_$idx"),
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("0${idx + 1}", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                                                        Text(chip, style = MaterialTheme.typography.labelSmall, color = SwissInk)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Floating "↓ Jump to Latest" pill when user scrolls up
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isScrolledUp,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(SwissInk, RectangleShape)
                                    .border(1.dp, SwissSignalRed, RectangleShape)
                                    .clickable {
                                        autoScrollEnabled = true
                                        scope.launch {
                                            if (displayedMessages.isNotEmpty()) {
                                                listState.animateScrollToItem(displayedMessages.lastIndex)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                                    .testTag("assistant_jump_to_latest"),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDownward,
                                    contentDescription = "Jump to latest message",
                                    tint = SwissSignalRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "JUMP TO LATEST",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite
                                )
                            }
                        }
                    }

                    // 3. PINNED BOTTOM INPUT BAR
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(SwissInk)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Sequential Multi-Action Confirmation Stack (Calls, WhatsApp Voice/Video/Message, Alarms, Timers, Apps)
                            AnimatedVisibility(visible = actionQueue.isNotEmpty()) {
                                DeviceActionQueueStack(
                                    actions = actionQueue,
                                    quickContacts = quickContacts,
                                    autoLaunchDialer = false,
                                    showEarbudHints = true,
                                    autoDialWebhookUrl = autoDialWebhookUrl,
                                    onDialLaunched = { dialAction, usedBridge ->
                                        statusBanner = dialAction.displayBanner
                                        launchingCallViaBridge = usedBridge
                                        launchingCallAction = dialAction
                                    },
                                    onUpdateActive = { updated ->
                                        actionQueue = listOf(updated) + actionQueue.drop(1)
                                        if (updated is DeviceAction.DialPhone && updated.phoneNumber.isNotBlank()) {
                                            statusBanner = updated.displayBanner
                                        } else if (updated is DeviceAction.WhatsAppCall && updated.phoneNumber.isNotBlank()) {
                                            statusBanner = updated.displayBanner
                                        }
                                    },
                                    onRemoveQueuedAt = { idx ->
                                        if (idx in actionQueue.indices) {
                                            actionQueue = actionQueue.filterIndexed { i, _ -> i != idx }
                                        }
                                    },
                                    onClearAll = { actionQueue = emptyList() },
                                    onSaveQuickContact = { name, phone ->
                                        scope.launch {
                                            repository.saveQuickContact(
                                                QuickContact(name = name, phoneNumber = phone)
                                            )
                                            statusBanner = "OK // Saved $name ($phone) to Quick Contacts."
                                        }
                                    },
                                    onExecuteActive = { action ->
                                        if (action is DeviceAction.DialPhone) {
                                            launchDialAction(action)
                                            if (actionQueue.size > 1) {
                                                advanceAssistantActionQueue()
                                            }
                                        } else {
                                            DeviceActionEngine.execute(context, action)
                                                .onSuccess { msg ->
                                                    advanceAssistantActionQueue(msg)
                                                }
                                                .onFailure { err ->
                                                    errorBanner = err.message ?: "Action failed"
                                                }
                                        }
                                    },
                                    onDismissActive = { advanceAssistantActionQueue() }
                                )
                            }

                            // Actionable Error Banner with 1-Tap RETRY Button
                            AnimatedVisibility(visible = errorBanner != null) {
                                errorBanner?.let { errMsg ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SwissInk, RectangleShape)
                                            .border(1.dp, SwissSignalRed, RectangleShape)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = errMsg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SwissWhite,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        lastFailedPrompt?.let { failed ->
                                            Row(
                                                modifier = Modifier
                                                    .background(SwissSignalRed, RectangleShape)
                                                    .clickable { handlePromptSubmission(failed) }
                                                    .padding(horizontal = 9.dp, vertical = 6.dp)
                                                    .testTag("assistant_retry_button"),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Refresh,
                                                    contentDescription = "Retry request",
                                                    tint = SwissWhite,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "RETRY",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SwissWhite
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Dismiss error",
                                            tint = SwissConcrete,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { errorBanner = null }
                                        )
                                    }
                                }
                            }

                            // Non-Error Status Notice
                            AnimatedVisibility(visible = statusBanner != null) {
                                statusBanner?.let { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (note.startsWith("OK")) SwissVerifiedInk else SwissMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Negative-Constraint Quick Pill Bar
                            NegativeConstraintPillBar(
                                activeConstraints = activeConstraints,
                                onToggleConstraint = { rule ->
                                    activeConstraints = if (rule in activeConstraints) activeConstraints - rule else activeConstraints + rule
                                }
                            )

                            // Mic + Text Field + Enhance Prompt Wand + Send/Stop Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SwissIconCell(
                                    icon = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                    contentDescription = if (isListening) "Stop listening" else "Start voice input",
                                    onClick = {
                                        if (isListening) voiceController.stop() else startMic()
                                    },
                                    accent = isListening,
                                    cellSize = 48.dp,
                                    modifier = Modifier.testTag("assistant_mic_button")
                                )

                                OutlinedTextField(
                                    value = transcriptInput,
                                    onValueChange = { transcriptInput = it },
                                    placeholder = {
                                        Text(
                                            if (isEnhancingPrompt) {
                                                "PROMPT DOCTOR // REWRITING…"
                                            } else if (isListening) {
                                                "LISTENING // SPEAK COMMAND OR QUESTION…"
                                            } else if (ghostMode) {
                                                "GHOST PROMPT // NOT SAVED…"
                                            } else {
                                                "Ask follow-up, call, WhatsApp, YouTube…"
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    singleLine = true,
                                    shape = RectangleShape,
                                    colors = swissTextFieldColors(containerColor = SwissCanvas),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (transcriptInput.isNotBlank() && !isStreaming) {
                                                handlePromptSubmission(transcriptInput)
                                            }
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("assistant_text_input")
                                )

                                // 1-Tap Prompt Doctor Magic Wand
                                SwissIconCell(
                                    icon = Icons.Outlined.AutoFixHigh,
                                    contentDescription = "Enhance prompt with Prompt Doctor",
                                    onClick = {
                                        val rough = transcriptInput.trim()
                                        if (rough.isNotEmpty() && !isEnhancingPrompt) {
                                            scope.launch {
                                                isEnhancingPrompt = true
                                                statusBanner = "PROMPT DOCTOR // Optimizing…"
                                                runCatching {
                                                    repository.enhanceUserPrompt(rough, selectedModel)
                                                }.onSuccess { enhanced ->
                                                    if (enhanced.isNotBlank()) {
                                                        transcriptInput = enhanced
                                                        statusBanner = "OK // Prompt enhanced. Review & send."
                                                    }
                                                }.onFailure { err ->
                                                    errorBanner = err.message ?: "Prompt Doctor failed."
                                                }
                                                isEnhancingPrompt = false
                                            }
                                        }
                                    },
                                    enabled = transcriptInput.isNotBlank() && !isEnhancingPrompt && !isStreaming,
                                    accent = isEnhancingPrompt,
                                    cellSize = 48.dp,
                                    modifier = Modifier.testTag("assistant_enhance_wand_button")
                                )

                                // Per-Message Thinking / Creativity Effort Dial (LOW / BAL / DEEP)
                                Box(
                                    modifier = Modifier
                                        .height(48.dp)
                                        .background(
                                            when (reasoningEffort) {
                                                ReasoningEffort.DEEP -> SwissInk
                                                ReasoningEffort.LOW -> SwissSubtleFill
                                                ReasoningEffort.BALANCED -> SwissPaper
                                            },
                                            RectangleShape
                                        )
                                        .border(
                                            1.dp,
                                            if (reasoningEffort == ReasoningEffort.DEEP) SwissSignalRed else SwissInk,
                                            RectangleShape
                                        )
                                        .clickable {
                                            reasoningEffort = reasoningEffort.next()
                                            statusBanner = "THINKING DIAL // ${reasoningEffort.displayLabel}"
                                        }
                                        .padding(horizontal = 7.dp)
                                        .testTag("assistant_reasoning_dial"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = reasoningEffort.shortLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (reasoningEffort == ReasoningEffort.DEEP) SwissWhite else SwissInk
                                    )
                                }

                                if (isStreaming) {
                                    SwissIconCell(
                                        icon = Icons.Filled.Stop,
                                        contentDescription = "Stop generating",
                                        onClick = { cancelInFlightRequest() },
                                        accent = true,
                                        cellSize = 48.dp,
                                        modifier = Modifier.testTag("assistant_stop_button")
                                    )
                                } else {
                                    SwissIconCell(
                                        icon = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send command",
                                        onClick = { handlePromptSubmission(transcriptInput) },
                                        enabled = transcriptInput.isNotBlank(),
                                        accent = true,
                                        cellSize = 48.dp,
                                        modifier = Modifier.testTag("assistant_send_button")
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. COMPACT SCROLLABLE SLIDE-OVER CHAT HISTORY DRAWER (INSIDE ASSISTANT)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showHistoryDrawer,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { showHistoryDrawer = false }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showHistoryDrawer,
                    enter = slideInHorizontally(initialOffsetX = { -it }),
                    exit = slideOutHorizontally(targetOffsetX = { -it }),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(0.86f)
                        .fillMaxHeight()
                ) {
                    AssistantHistoryDrawerPanel(
                        conversations = filteredHistory,
                        activeConversationId = activeConvId,
                        searchQuery = historySearch,
                        onSearchChange = { historySearch = it },
                        onSelectConversation = { conv ->
                            cancelInFlightRequest()
                            errorBanner = null
                            lastFailedPrompt = null
                            showHistoryDrawer = false
                            autoScrollEnabled = true
                            scope.launch {
                                repository.setActiveConversationId(conv.id)
                            }
                        },
                        onDeleteConversation = { conv ->
                            scope.launch {
                                if (conv.id == activeConvId) {
                                    cancelInFlightRequest()
                                    repository.setActiveConversationId(null)
                                }
                                repository.deleteConversation(conv.id)
                            }
                        },
                        onNewChat = { startFreshConversation() },
                        onOpenFullApp = onOpenFullApp,
                        onClose = { showHistoryDrawer = false }
                    )
                }
            }
        }
    }

    if (showVoiceNotesSheet) {
        VoiceNotesInboxSheet(
            notes = voiceNotes,
            isSavingNote = isSavingNote,
            onSaveNewMemo = { memo -> saveQuickNote(memo) },
            onUpdateNote = { updated ->
                scope.launch { repository.updateVoiceNote(updated) }
            },
            onDeleteNote = { id ->
                scope.launch { repository.deleteVoiceNote(id) }
            },
            onDismiss = { showVoiceNotesSheet = false }
        )
    }

    qrBeamPayload?.let { payload ->
        QrStudioDialog(
            initialPayload = payload,
            onDismiss = { qrBeamPayload = null },
            onNotify = { statusBanner = it }
        )
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

@Composable
private fun AssistantHistoryDrawerPanel(
    conversations: List<ConversationEntity>,
    activeConversationId: Long?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectConversation: (ConversationEntity) -> Unit,
    onDeleteConversation: (ConversationEntity) -> Unit,
    onNewChat: () -> Unit,
    onOpenFullApp: (() -> Unit)?,
    onClose: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("MMM d · HH:mm", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SwissPaper)
            .border(1.dp, SwissInk, RectangleShape)
            .clickable(enabled = false) {}
            .testTag("assistant_history_drawer")
    ) {
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
                    text = "HISTORY // SAVED SESSIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )
                Text(
                    text = "${conversations.size} Thread${if (conversations.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = SwissWhite
                )
            }
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close history drawer",
                tint = SwissWhite,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onClose)
                    .testTag("assistant_history_close")
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SwissButton(
                    text = "New Thread",
                    leadingIcon = Icons.Filled.Add,
                    variant = SwissButtonVariant.PRIMARY,
                    minHeight = 38.dp,
                    onClick = onNewChat,
                    modifier = Modifier.weight(1f)
                )
                if (onOpenFullApp != null) {
                    SwissButton(
                        text = "Full App",
                        variant = SwissButtonVariant.OUTLINE,
                        minHeight = 38.dp,
                        onClick = onOpenFullApp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Filter history…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                shape = RectangleShape,
                colors = swissTextFieldColors(containerColor = SwissCanvas),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assistant_history_search")
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SwissInk)
        )

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved conversations yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("assistant_history_list"),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val isCurrent = conv.id == activeConversationId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isCurrent) SwissInk else SwissCanvas, RectangleShape)
                            .border(1.dp, if (isCurrent) SwissSignalRed else SwissInk, RectangleShape)
                            .clickable { onSelectConversation(conv) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = conv.title.ifBlank { "Untitled Chat" },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrent) SwissWhite else SwissInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    if (isCurrent) append("ACTIVE · ")
                                    append(timeFormatter.format(Date(conv.updatedAt)))
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) SwissSignalRed else SwissMuted
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete thread",
                            tint = if (isCurrent) SwissWhite.copy(alpha = 0.8f) else SwissMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onDeleteConversation(conv) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayMessageCard(
    turnNumber: Int,
    message: MessageEntity,
    isStreamingThis: Boolean,
    onBeamQr: () -> Unit = {}
) {
    val isUser = message.role == "USER"
    val borderColor = if (isUser) SwissInk else SwissInk.copy(alpha = 0.7f)
    val bgColor = if (isUser) SwissInk else SwissPaper
    val headerFg = if (isUser) SwissSignalRed else SwissMuted
    val bodyFg = if (isUser) SwissWhite else SwissInk
    val indexStr = turnNumber.toString().padStart(2, '0')
    val headerLabel = if (isUser) {
        "$indexStr // YOU"
    } else {
        "$indexStr // ${(message.modelLabel ?: "ASSISTANT").uppercase()}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RectangleShape)
            .border(1.dp, borderColor, RectangleShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = headerFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isStreamingThis) {
                Text(
                    text = "STREAMING…",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )
            } else if (message.content.isNotBlank()) {
                Icon(
                    imageVector = Icons.Outlined.QrCode2,
                    contentDescription = "Beam via QR",
                    tint = if (isUser) SwissConcrete else SwissMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onBeamQr)
                        .testTag("assistant_beam_qr_${message.id}")
                )
            }
        }
        Text(
            text = message.content.ifBlank { if (isStreamingThis) "Thinking…" else "" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (message.isError) SwissSignalRed else bodyFg
        )
    }
}

@Composable
private fun AssistantQuickChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .background(if (accent) SwissSignalRed else SwissCanvas, RectangleShape)
            .border(1.dp, if (accent) SwissSignalRed else SwissInk, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) SwissWhite else SwissInk,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) SwissWhite else SwissInk
        )
    }
}
