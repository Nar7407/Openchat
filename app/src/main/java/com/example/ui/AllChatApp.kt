package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.repository.AllChatRepository
import com.example.domain.model.IncomingAppAction
import com.example.domain.model.IncomingSharePayload
import com.example.domain.model.LiveLensMode
import com.example.ui.components.AssistantOverlaySheet
import com.example.ui.components.CommuteHudDialog
import com.example.ui.components.FaceToFaceInterpreterDialog
import com.example.ui.components.FloatingSidebarDock
import com.example.ui.components.LiveLensScreen
import com.example.ui.components.PinLockOverlay
import com.example.ui.components.PowerToolAction
import com.example.ui.components.PowerToolkitHubDialog
import com.example.ui.components.ScreenScanSheet
import com.example.ui.components.SwissButton
import com.example.ui.components.SwissButtonVariant
import com.example.ui.components.VoiceNotesInboxSheet
import com.example.util.DeviceAction
import com.example.util.DeviceActionEngine
import com.example.util.EarbudCommand
import com.example.util.EarbudKeyBus
import com.example.util.MediaButtonBridge
import com.example.util.ReadAloudController
import com.example.ui.screens.ArenaScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CodeDebuggerScreen
import com.example.ui.screens.ImageScreen
import com.example.ui.screens.KnowledgeScreen
import com.example.ui.screens.PipelineScreen
import com.example.ui.screens.RewriteStudioScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SummarizeScreen
import com.example.ui.screens.VideoScreen
import com.example.ui.screens.YouTubeExtractorScreen
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissThemePreset
import com.example.ui.theme.SwissThemeState
import com.example.ui.viewmodel.AllChatViewModelFactory
import com.example.ui.viewmodel.ArenaViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.CodeDebuggerViewModel
import com.example.ui.viewmodel.ImageViewModel
import com.example.ui.viewmodel.KnowledgeViewModel
import com.example.ui.viewmodel.PipelineViewModel
import com.example.ui.viewmodel.RewriteStudioViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.SummarizeInputMode
import com.example.ui.viewmodel.SummarizeViewModel
import com.example.ui.viewmodel.VideoViewModel
import com.example.ui.viewmodel.YouTubeExtractorViewModel
import kotlinx.serialization.Serializable

@Serializable data object ChatRoute
@Serializable data object ArenaRoute
@Serializable data object PipelineRoute
@Serializable data object KnowledgeRoute
@Serializable data object SummarizeRoute
@Serializable data object ImageRoute
@Serializable data object VideoRoute
@Serializable data object RewriteRoute
@Serializable data object DebuggerRoute
@Serializable data object YouTubeRoute
@Serializable data class LiveLensRoute(val startInTranslator: Boolean = false, val initialModeName: String = "")
@Serializable data object SettingsRoute

@Composable
fun AllChatApp(
    repository: AllChatRepository,
    incomingSharePayload: IncomingSharePayload?,
    onSharedPayloadConsumed: () -> Unit,
    incomingAppAction: IncomingAppAction? = null,
    onAppActionConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isLocked by repository.isLockedFlow.collectAsStateWithLifecycle()
    val isPinEnabled by repository.isPinEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val autoLockMinutes by repository.autoLockMinutesFlow.collectAsStateWithLifecycle(initialValue = 5)
    val lockOnAppSwitch by repository.lockOnAppSwitchFlow.collectAsStateWithLifecycle(initialValue = false)
    var backgroundedAtMs by remember { mutableLongStateOf(0L) }
    val themeId by repository.themePresetIdFlow.collectAsStateWithLifecycle(initialValue = SwissThemePreset.SWISS_SIGNAL.id)
    var pendingDocShareUri by remember { mutableStateOf<String?>(null) }
    var showAssistantOverlay by remember { mutableStateOf(false) }
    var showScreenScanSheet by remember { mutableStateOf(false) }
    var showVoiceNotesSheet by remember { mutableStateOf(false) }
    var showToolkitHub by remember { mutableStateOf(false) }
    var showCommuteHud by remember { mutableStateOf(false) }
    var showInterpreter by remember { mutableStateOf(false) }

    LaunchedEffect(themeId) {
        SwissThemeState.activePreset = SwissThemePreset.fromId(themeId)
    }

    LaunchedEffect(Unit) {
        repository.initializeLockState()
        runCatching { repository.pruneOldJunkChatsIfEnabled() }
    }

    // Foreground Earbud Tap Listener when Live Assistant is closed:
    // Single-tap Bluetooth earbud opens Live Assistant straight into active Mic mode.
    DisposableEffect(showAssistantOverlay, showCommuteHud) {
        val bridge = if (!showAssistantOverlay && !showCommuteHud) {
            MediaButtonBridge(
                context = context,
                onEarbudTriggerAsk = { showAssistantOverlay = true },
                onEarbudNextAction = {},
                enableSilentKeepAlive = false
            ).also { it.start(context) }
        } else null
        onDispose { bridge?.stop() }
    }

    LaunchedEffect(showAssistantOverlay, showCommuteHud) {
        if (!showAssistantOverlay && !showCommuteHud) {
            EarbudKeyBus.events.collect { cmd ->
                if (cmd == EarbudCommand.SINGLE_TAP) {
                    showAssistantOverlay = true
                }
            }
        }
    }

    // Automatic Vault Lock after N minutes of idle/background or immediately on app switch
    DisposableEffect(lifecycleOwner, isPinEnabled, autoLockMinutes, lockOnAppSwitch) {
        val observer = LifecycleEventObserver { _, event ->
            if (!isPinEnabled) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    backgroundedAtMs = System.currentTimeMillis()
                    if (lockOnAppSwitch) {
                        repository.lockNow()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    val awayMs = if (backgroundedAtMs > 0L) System.currentTimeMillis() - backgroundedAtMs else 0L
                    if (autoLockMinutes > 0 && awayMs >= autoLockMinutes * 60_000L) {
                        repository.lockNow()
                    }
                    backgroundedAtMs = 0L
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isLocked) {
        PinLockOverlay(
            onUnlockAttempt = { pin -> repository.unlockWithPin(pin) }
        )
        return
    }

    val factory = AllChatViewModelFactory(repository)
    val chatViewModel: ChatViewModel = viewModel(factory = factory)
    val arenaViewModel: ArenaViewModel = viewModel(factory = factory)
    val pipelineViewModel: PipelineViewModel = viewModel(factory = factory)
    val knowledgeViewModel: KnowledgeViewModel = viewModel(factory = factory)
    val summarizeViewModel: SummarizeViewModel = viewModel(factory = factory)
    val imageViewModel: ImageViewModel = viewModel(factory = factory)
    val videoViewModel: VideoViewModel = viewModel(factory = factory)
    val rewriteViewModel: RewriteStudioViewModel = viewModel(factory = factory)
    val debuggerViewModel: CodeDebuggerViewModel = viewModel(factory = factory)
    val youtubeViewModel: YouTubeExtractorViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

    val navController = rememberNavController()

    fun navigateTo(route: Any) {
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(incomingSharePayload) {
        when (incomingSharePayload) {
            is IncomingSharePayload.Text -> {
                summarizeViewModel.consumeSharedPayload(incomingSharePayload.text)
                navigateTo(SummarizeRoute)
                onSharedPayloadConsumed()
            }
            is IncomingSharePayload.DocumentUri -> {
                pendingDocShareUri = incomingSharePayload.uriString
                onSharedPayloadConsumed()
            }
            is IncomingSharePayload.WorkspaceFileLaunch -> {
                chatViewModel.loadLaunchedFileIntoActiveWorkspace(Uri.parse(incomingSharePayload.uriString))
                navController.popBackStack(ChatRoute, inclusive = false)
                onSharedPayloadConsumed()
            }
            is IncomingSharePayload.ImageUri -> {
                chatViewModel.attachSharedImageAndSelectVision(Uri.parse(incomingSharePayload.uriString))
                navController.popBackStack(ChatRoute, inclusive = false)
                onSharedPayloadConsumed()
            }
            is IncomingSharePayload.DeepLinkAsk -> {
                navController.popBackStack(ChatRoute, inclusive = false)
                chatViewModel.sendMessage(incomingSharePayload.query)
                onSharedPayloadConsumed()
            }
            null -> Unit
        }
    }

    LaunchedEffect(incomingAppAction) {
        when (incomingAppAction) {
            IncomingAppAction.NEW_CHAT -> {
                chatViewModel.createNewChat()
                navController.popBackStack(ChatRoute, inclusive = false)
                onAppActionConsumed()
            }
            IncomingAppAction.VOICE_PROMPT -> {
                navController.popBackStack(ChatRoute, inclusive = false)
                chatViewModel.triggerVoiceDictation()
                onAppActionConsumed()
            }
            IncomingAppAction.SUMMARIZE_CLIPBOARD -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (clipText.isNotBlank()) {
                    summarizeViewModel.consumeSharedPayload(clipText)
                }
                navigateTo(SummarizeRoute)
                onAppActionConsumed()
            }
            IncomingAppAction.GENERATE_IMAGE -> {
                navigateTo(ImageRoute)
                onAppActionConsumed()
            }
            IncomingAppAction.CAMERA_VISION -> {
                navController.popBackStack(ChatRoute, inclusive = false)
                chatViewModel.triggerCameraVision()
                onAppActionConsumed()
            }
            IncomingAppAction.ASSISTANT_OVERLAY -> {
                showAssistantOverlay = true
                onAppActionConsumed()
            }
            IncomingAppAction.LIVE_LENS_EXPLAIN -> {
                navigateTo(LiveLensRoute(startInTranslator = false))
                onAppActionConsumed()
            }
            IncomingAppAction.LIVE_LENS_TRANSLATE -> {
                navigateTo(LiveLensRoute(startInTranslator = true))
                onAppActionConsumed()
            }
            IncomingAppAction.LIVE_LENS_SOLVE -> {
                navigateTo(LiveLensRoute(initialModeName = LiveLensMode.SOLVE_STEP_BY_STEP.name))
                onAppActionConsumed()
            }
            IncomingAppAction.LIVE_LENS_CODE -> {
                navigateTo(LiveLensRoute(initialModeName = LiveLensMode.WHITEBOARD_TO_CODE.name))
                onAppActionConsumed()
            }
            IncomingAppAction.COMMUTE_HUD -> {
                showCommuteHud = true
                onAppActionConsumed()
            }
            IncomingAppAction.INTERPRETER -> {
                showInterpreter = true
                onAppActionConsumed()
            }
            IncomingAppAction.TOOLKIT -> {
                showToolkitHub = true
                onAppActionConsumed()
            }
            null -> Unit
        }
    }

    // Swiss Quick-Action Sheet when receiving a shared PDF/Document via Android Share Sheet
    pendingDocShareUri?.let { docUriStr ->
        AlertDialog(
            onDismissRequest = { pendingDocShareUri = null },
            containerColor = SwissCanvas,
            shape = RectangleShape,
            title = {
                Column {
                    Text(
                        text = "SHARE TARGET // DOCUMENT RECEIVED",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted
                    )
                    Text(
                        text = "Choose Document Action",
                        style = MaterialTheme.typography.titleLarge,
                        color = SwissInk
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Route this document to instant structured summarization or attach it to a new Chat session for Q&A.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissMuted
                    )
                    SwissButton(
                        text = "Summarize Document",
                        variant = SwissButtonVariant.PRIMARY,
                        onClick = {
                            val uri = Uri.parse(docUriStr)
                            pendingDocShareUri = null
                            summarizeViewModel.setInputMode(SummarizeInputMode.FILE)
                            summarizeViewModel.loadFile(uri)
                            navigateTo(SummarizeRoute)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    SwissButton(
                        text = "Chat with Document",
                        variant = SwissButtonVariant.INK,
                        onClick = {
                            val uri = Uri.parse(docUriStr)
                            pendingDocShareUri = null
                            chatViewModel.startChatWithSharedDocument(uri)
                            navController.popBackStack(ChatRoute, inclusive = false)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                SwissButton(
                    text = "Cancel",
                    variant = SwissButtonVariant.OUTLINE,
                    minHeight = 40.dp,
                    onClick = { pendingDocShareUri = null }
                )
            }
        )
    }

    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = SwissCanvas,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = ChatRoute,
                modifier = Modifier.fillMaxSize()
            ) {
                composable<ChatRoute> {
                    ChatScreen(
                        viewModel = chatViewModel,
                        onOpenArena = { navigateTo(ArenaRoute) },
                        onOpenPipeline = { navigateTo(PipelineRoute) },
                        onOpenKnowledge = { navigateTo(KnowledgeRoute) },
                        onOpenSummarize = { navigateTo(SummarizeRoute) },
                        onOpenImage = { navigateTo(ImageRoute) },
                        onOpenVideo = { navigateTo(VideoRoute) },
                        onOpenRewrite = { navigateTo(RewriteRoute) },
                        onOpenDebugger = { navigateTo(DebuggerRoute) },
                        onOpenYouTube = { navigateTo(YouTubeRoute) },
                        onOpenLiveLens = { translator -> navigateTo(LiveLensRoute(startInTranslator = translator)) },
                        onOpenAssistantOverlay = { showAssistantOverlay = true },
                        onSummarizeClipboardText = { clipText ->
                            summarizeViewModel.consumeSharedPayload(clipText)
                            navigateTo(SummarizeRoute)
                        },
                        onOpenSettings = { navigateTo(SettingsRoute) },
                        onConnectProvider = {
                            settingsViewModel.openCatalog()
                            navigateTo(SettingsRoute)
                        }
                    )
                }
                composable<ArenaRoute> {
                    ArenaScreen(
                        viewModel = arenaViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenSettings = { navigateTo(SettingsRoute) },
                        onContinueInChat = { conversationId ->
                            chatViewModel.selectConversation(conversationId)
                            navController.popBackStack()
                        }
                    )
                }
                composable<PipelineRoute> {
                    PipelineScreen(
                        viewModel = pipelineViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenSettings = { navigateTo(SettingsRoute) },
                        onContinueInChat = { conversationId ->
                            chatViewModel.selectConversation(conversationId)
                            navController.popBackStack()
                        }
                    )
                }
                composable<KnowledgeRoute> {
                    KnowledgeScreen(
                        viewModel = knowledgeViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable<SummarizeRoute> {
                    SummarizeScreen(
                        viewModel = summarizeViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenSettings = { navigateTo(SettingsRoute) }
                    )
                }
                composable<ImageRoute> {
                    ImageScreen(
                        viewModel = imageViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenSettings = { navigateTo(SettingsRoute) },
                        onGenerateSvg = { desc -> chatViewModel.generateCleanSvg(desc) }
                    )
                }
                composable<VideoRoute> {
                    VideoScreen(
                        viewModel = videoViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenSettings = { navigateTo(SettingsRoute) }
                    )
                }
                composable<RewriteRoute> {
                    RewriteStudioScreen(
                        viewModel = rewriteViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenSettings = { navigateTo(SettingsRoute) }
                    )
                }
                composable<DebuggerRoute> {
                    CodeDebuggerScreen(
                        viewModel = debuggerViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenSettings = { navigateTo(SettingsRoute) }
                    )
                }
                composable<YouTubeRoute> {
                    YouTubeExtractorScreen(
                        viewModel = youtubeViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenSettings = { navigateTo(SettingsRoute) }
                    )
                }
                composable<LiveLensRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<LiveLensRoute>()
                    val parsedMode = route.initialModeName.takeIf { it.isNotBlank() }?.let { name ->
                        runCatching { LiveLensMode.valueOf(name) }.getOrNull()
                    }
                    LiveLensScreen(
                        repository = repository,
                        preferredModel = chatState.selectedModel,
                        initialTranslatorMode = route.startInTranslator,
                        initialMode = parsedMode,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenSettings = { navigateTo(SettingsRoute) },
                        onPinResultToChat = { title, content ->
                            chatViewModel.pinReference(
                                title = title,
                                content = content,
                                sourceLabel = "LIVE LENS"
                            )
                            navController.popBackStack()
                        }
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onTestAssistantOverlay = { showAssistantOverlay = true }
                    )
                }
            }

            // Right-Edge ColorOS / Android Floating Smart Sidebar Dock
            FloatingSidebarDock(
                isMiniWindowMode = chatState.miniModeEnabled,
                onToggleMiniWindowMode = { chatViewModel.toggleMiniMode() },
                onOpenLiveTranslator = { navigateTo(LiveLensRoute(startInTranslator = true)) },
                onOpenScanScreen = { showScreenScanSheet = true },
                onOpenVoiceNotes = { showVoiceNotesSheet = true },
                onOpenAssistantOverlay = { showAssistantOverlay = true },
                onOpenMiniChat = { navController.popBackStack(ChatRoute, inclusive = false) },
                onTriggerDeviceAction = { action ->
                    chatViewModel.setPendingDeviceAction(action)
                    navController.popBackStack(ChatRoute, inclusive = false)
                },
                onOpenToolkitHub = { showToolkitHub = true },
                onOpenCommuteHud = { showCommuteHud = true },
                onOpenInterpreter = { showInterpreter = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            )

            // In-App Google-Assistant-Style Bottom-Sheet Overlay (Volume Up+Down Chord or Quick Launch)
            if (showAssistantOverlay) {
                AssistantOverlaySheet(
                    repository = repository,
                    autoStartMic = true,
                    onOpenTranslateCamera = {
                        showAssistantOverlay = false
                        navigateTo(LiveLensRoute(startInTranslator = true))
                    },
                    onOpenLiveLensExplain = {
                        showAssistantOverlay = false
                        navigateTo(LiveLensRoute(startInTranslator = false))
                    },
                    onOpenToolkitHub = {
                        showAssistantOverlay = false
                        showToolkitHub = true
                    },
                    onDismiss = { showAssistantOverlay = false }
                )
            }

            if (showScreenScanSheet) {
                ScreenScanSheet(
                    repository = repository,
                    preferredModel = chatState.selectedModel,
                    onDismiss = { showScreenScanSheet = false }
                )
            }

            if (showVoiceNotesSheet) {
                VoiceNotesInboxSheet(
                    notes = chatState.voiceNotes,
                    isSavingNote = chatState.isSavingNote,
                    onSaveNewMemo = { memo -> chatViewModel.saveVoiceQuickNote(memo) },
                    onUpdateNote = { note -> chatViewModel.updateVoiceNote(note) },
                    onDeleteNote = { id -> chatViewModel.deleteVoiceNote(id) },
                    onDismiss = { showVoiceNotesSheet = false }
                )
            }

            if (showToolkitHub) {
                PowerToolkitHubDialog(
                    onDismiss = { showToolkitHub = false },
                    onSelectTool = { tool ->
                        showToolkitHub = false
                        when (tool) {
                            PowerToolAction.COMMUTE_HUD -> showCommuteHud = true
                            PowerToolAction.FACE_INTERPRETER -> showInterpreter = true
                            else -> {
                                navController.popBackStack(ChatRoute, inclusive = false)
                                chatViewModel.launchPowerTool(tool)
                            }
                        }
                    }
                )
            }

            if (showCommuteHud) {
                val hudTts = remember(context) { ReadAloudController(context, onSpeakingIdChanged = {}) }
                androidx.compose.runtime.DisposableEffect(hudTts) {
                    onDispose { hudTts.release() }
                }
                val lastReply = chatState.messages.lastOrNull { it.role == "ASSISTANT" && !it.isError }?.content.orEmpty()
                CommuteHudDialog(
                    wakePhraseEnabled = chatState.wakePhraseEnabled,
                    lastAssistantReply = lastReply,
                    onSubmitVoicePrompt = { spoken -> chatViewModel.sendMessage(spoken) },
                    onReadLastReply = {
                        if (lastReply.isNotBlank()) hudTts.speak(System.currentTimeMillis(), lastReply)
                    },
                    onQuickNavigateHome = {
                        DeviceActionEngine.execute(context, DeviceAction.OpenMaps("Home", isNavigation = true))
                    },
                    onDismiss = { showCommuteHud = false }
                )
            }

            if (showInterpreter) {
                FaceToFaceInterpreterDialog(
                    onTranslate = { text, src, tgt -> chatViewModel.translateInterpreterUtterance(text, src, tgt) },
                    onDismiss = { showInterpreter = false }
                )
            }
        }
    }
}
