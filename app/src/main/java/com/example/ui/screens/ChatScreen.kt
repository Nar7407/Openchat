package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Videocam
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.remote.WebSearchClient
import com.example.domain.model.BranchMessageNode
import com.example.domain.model.ContextWindowEstimator
import com.example.domain.model.CostEstimator
import com.example.domain.model.DisplayCurrency
import com.example.domain.model.MemoryProfile
import com.example.domain.model.ModelCapability
import com.example.domain.model.OutputFormatMode
import com.example.domain.model.ReasoningEffort
import com.example.domain.model.ReplyMorphStyle
import com.example.util.DeviceAction
import com.example.util.LiveLensHistoryEntry
import com.example.util.LiveLensHistoryStore
import com.example.util.PromptPasteMinifier
import com.example.util.SemanticEmbeddingEngine
import com.example.util.SemanticSearchHit
import com.example.ui.components.ActiveToolsDrawerSheet
import com.example.ui.components.AgentsMdInitDialog
import com.example.ui.components.CallingLaunchOverlay
import com.example.ui.components.ChatMinimapScrubber
import com.example.ui.components.CitationPillsRow
import com.example.ui.components.CommandHelpSheet
import com.example.ui.components.CommandPaletteDialog
import com.example.ui.components.CustomSlashCommandsSheet
import com.example.ui.components.DeviceActionConfirmCard
import com.example.ui.components.DeviceActionQueueStack
import com.example.ui.components.EditMessageForkDialog
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ExportStudioDialog
import com.example.ui.components.KnowledgeTagBar
import com.example.ui.components.LiveCamController
import com.example.ui.components.LiveCameraViewfinderPanel
import com.example.ui.components.LiveVoiceOverlay
import com.example.ui.components.MarkdownText
import com.example.ui.components.McpServersSheet
import com.example.ui.components.MemoryBankDialog
import com.example.ui.components.MindMapDialog
import com.example.ui.components.ModelPickerButton
import com.example.ui.components.PersonaCommandBar
import com.example.ui.components.PersonaManagerSheet
import com.example.ui.components.PromptEditorDialog
import com.example.ui.components.RawJsonInspectorDialog
import com.example.ui.components.RegionSnipDialog
import com.example.ui.components.RequestParamsPopover
import com.example.ui.components.ShareSessionDialog
import com.example.ui.components.SlashCommandSuggestionBar
import com.example.ui.components.StudyQuizDialog
import com.example.ui.components.SwissBadge
import com.example.ui.components.SwissButton
import com.example.ui.components.SwissButtonVariant
import com.example.ui.components.SwissIconCell
import com.example.ui.components.SwissPanel
import com.example.ui.components.SwissPanelHeader
import com.example.ui.components.SwissRuleDivider
import com.example.ui.components.SwissToggleCell
import com.example.ui.components.ThemeSwitcherSheet
import com.example.ui.components.VoiceNotesInboxSheet
import com.example.ui.components.swissGraphPaper
import com.example.ui.components.swissRegistrationMarks
import com.example.ui.components.swissTextFieldColors
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissGridLine
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite
import com.example.domain.model.AmbientNoiseLevel
import com.example.domain.model.FactCheckClaim
import com.example.ui.components.BleInspectorDialog
import com.example.ui.components.CliCommandBuilderDialog
import com.example.ui.components.ClipboardVaultDialog
import com.example.ui.components.ColorPaletteDialog
import com.example.ui.components.CommuteHudDialog
import com.example.ui.components.CsvChartExplorerDialog
import com.example.ui.components.DecisionMatrixDialog
import com.example.ui.components.ExpenseTrackerDialog
import com.example.ui.components.FaceToFaceInterpreterDialog
import com.example.ui.components.ImageAnnotatorDialog
import com.example.ui.components.InteractiveCanvasDialog
import com.example.ui.components.InteractiveGraphPlotterDialog
import com.example.ui.components.LocalWorkspaceDialog
import com.example.ui.components.NegativeConstraintPillBar
import com.example.ui.components.PowerToolAction
import com.example.ui.components.PowerToolkitHubDialog
import com.example.ui.components.PrivacyRedactorDialog
import com.example.ui.components.QrStudioDialog
import com.example.ui.components.ReadAloudKaraokeBar
import com.example.ui.components.ReadingRoomDialog
import com.example.ui.components.SpotlightSearchDialog
import com.example.ui.components.ThreadCheckpointDividerCard
import com.example.ui.components.VideoFrameExtractorDialog
import com.example.ui.components.VoiceFormFillerDialog
import com.example.ui.components.WatchlistDialog
import com.example.ui.viewmodel.ChatUiEvent
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.CommandPaletteBus
import com.example.util.DeviceActionEngine
import com.example.util.MathExpressionEvaluator
import com.example.util.ReadAloudController
import com.example.util.SmartContextEngine
import com.example.util.TtsPlaybackState
import com.example.util.VoiceInputController
import com.example.util.WakePhraseListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenArena: () -> Unit,
    onOpenPipeline: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenSummarize: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenRewrite: () -> Unit,
    onOpenDebugger: () -> Unit,
    onOpenYouTube: () -> Unit,
    onOpenLiveLens: (Boolean) -> Unit,
    onOpenAssistantOverlay: () -> Unit,
    onSummarizeClipboardText: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onConnectProvider: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var preMinifyInputBackup by remember { mutableStateOf<String?>(null) }
    var sidebarSemanticMode by remember { mutableStateOf(false) }
    var sidebarLensHistory by remember { mutableStateOf<List<LiveLensHistoryEntry>>(emptyList()) }
    var sidebarSemanticHits by remember { mutableStateOf<List<SemanticSearchHit>>(emptyList()) }
    var showPlusMenu by remember { mutableStateOf(false) }
    var showPersonaManager by remember { mutableStateOf(false) }
    var showPersonaQuickBar by remember { mutableStateOf(false) }
    var showParamsPopover by remember { mutableStateOf(false) }
    var showCustomCommandsSheet by remember { mutableStateOf(false) }
    var inspectingMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var editingForkMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var renamingConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isDictating by remember { mutableStateOf(false) }
    var speakingMessageId by remember { mutableStateOf<Long?>(null) }

    // Slash command modal & trigger states
    var modelPickerOpenTrigger by remember { mutableIntStateOf(0) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showHelpSheet by remember { mutableStateOf(false) }
    var showMcpSheet by remember { mutableStateOf(false) }
    var showInitDialog by remember { mutableStateOf(false) }
    var showMemoryBank by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showActiveToolsDrawer by remember { mutableStateOf(false) }
    var showVoiceNotesSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CommandPaletteBus.openEvents.collect {
            showCommandPalette = true
        }
    }

    LaunchedEffect(state.memoryUpdatedToast) {
        if (state.memoryUpdatedToast != null) {
            delay(3600L)
            viewModel.dismissMemoryUpdatedToast()
        }
    }

    LaunchedEffect(drawerState.isOpen, sidebarSemanticMode) {
        if (drawerState.isOpen && sidebarSemanticMode) {
            sidebarLensHistory = LiveLensHistoryStore.loadAll(context)
        }
    }

    LaunchedEffect(
        sidebarSemanticMode,
        state.searchQuery,
        state.memories,
        state.knowledgeDocs,
        sidebarLensHistory
    ) {
        if (sidebarSemanticMode && state.searchQuery.trim().length >= 2) {
            sidebarSemanticHits = SemanticEmbeddingEngine.searchCorpus(
                query = state.searchQuery,
                memories = state.memories,
                knowledgeDocs = state.knowledgeDocs,
                lensHistory = sidebarLensHistory,
                topK = 10,
                minScore = 0.12f
            )
        } else {
            sidebarSemanticHits = emptyList()
        }
    }

    BackHandler(enabled = showActiveToolsDrawer) {
        showActiveToolsDrawer = false
    }
    var editorInitialText by remember { mutableStateOf<String?>(null) }
    var shareDialogPayload by remember { mutableStateOf<Pair<String, String>?>(null) }
    var exportStudioPayload by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingExportMarkdown by remember { mutableStateOf<String?>(null) }
    var snipSourceUri by remember { mutableStateOf<Uri?>(null) }
    val liveCamController = remember { LiveCamController() }

    // Power-User Mega Suite Modal & Adaptive Split States
    var ttsPlaybackState by remember { mutableStateOf(TtsPlaybackState()) }
    var showToolkitHub by remember { mutableStateOf(false) }
    var qrDialogPayload by remember { mutableStateOf<String?>(null) }
    var showVideoExtractor by remember { mutableStateOf(false) }
    var showColorPalette by remember { mutableStateOf(false) }
    var paletteInitialBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var redactorInitialText by remember { mutableStateOf<String?>(null) }
    var plotterInitialExpr by remember { mutableStateOf<String?>(null) }
    var showCsvExplorer by remember { mutableStateOf(false) }
    var decisionInitialPrompt by remember { mutableStateOf<String?>(null) }
    var readingRoomPayload by remember { mutableStateOf<Pair<String, String>?>(null) }
    var canvasInitialContent by remember { mutableStateOf<String?>(null) }
    var showExpensesDialog by remember { mutableStateOf(false) }
    var showWatchlistDialog by remember { mutableStateOf(false) }
    var showSpotlightDialog by remember { mutableStateOf(false) }
    var showClipboardVault by remember { mutableStateOf(false) }
    var showBleInspector by remember { mutableStateOf(false) }
    var showLocalWorkspace by remember { mutableStateOf(false) }
    var showCommuteHud by remember { mutableStateOf(false) }
    var showInterpreter by remember { mutableStateOf(false) }
    var showVoiceFormFiller by remember { mutableStateOf(false) }
    var cliBuilderQuery by remember { mutableStateOf<String?>(null) }
    var annotatingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var splitCanvasPinned by remember { mutableStateOf(false) }
    var splitCanvasDraft by remember { mutableStateOf("") }
    var splitPaneTab by remember { mutableIntStateOf(0) } // 0 = Canvas, 1 = Plotter
    var splitPlotExpr by remember { mutableStateOf("sin(x) + 0.2*x") }
    var cooldownNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var launchingCallAction by remember { mutableStateOf<DeviceAction.DialPhone?>(null) }
    var launchingCallViaBridge by remember { mutableStateOf(false) }

    fun launchDialAction(dial: DeviceAction.DialPhone) {
        val enriched = if (dial.contactLabel.isNullOrBlank() && dial.phoneNumber.isNotBlank()) {
            val matched = DeviceActionEngine.resolveContactByPhone(dial.phoneNumber, state.quickContacts)
            if (matched != null) dial.copy(contactLabel = matched.name) else dial
        } else {
            dial
        }
        viewModel.showNotice(enriched.displayBanner)
        launchingCallViaBridge = state.autoDialWebhookUrl.trim().startsWith("http")
        launchingCallAction = enriched
        scope.launch {
            DeviceActionEngine.executeDialWithBridge(context, enriched, state.autoDialWebhookUrl)
                .onSuccess { res ->
                    launchingCallViaBridge = res.usedAutoDialBridge
                    viewModel.showNotice("ACTION // ${res.message}")
                }
                .onFailure { err ->
                    launchingCallAction = null
                    viewModel.showToast(err.message ?: "Unable to launch dialer.")
                }
        }
    }

    LaunchedEffect(state.cooldownMap) {
        if (state.cooldownMap.isEmpty()) return@LaunchedEffect
        while (true) {
            cooldownNowMs = System.currentTimeMillis()
            if (state.cooldownMap.values.none { it > cooldownNowMs }) break
            delay(1000L)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshWorkspaceFiles(context)
    }

    LaunchedEffect(state.ambientWhisperEnabled, state.smartZones, state.timeProfilesEnabled, state.autoEcoMode) {
        val ambientDb = if (state.ambientWhisperEnabled) {
            runCatching { SmartContextEngine.sampleAmbientDecibels(context) }.getOrNull()
        } else null
        viewModel.refreshSmartContext(context, ambientDb)
    }

    // Smart "Paste & Ask" Clipboard Detector + Smart Context Refresh when returning to AllChat
    DisposableEffect(lifecycleOwner, context, state.ambientWhisperEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                viewModel.inspectClipboardOnResume(clipText)
                if (!clipText.isNullOrBlank()) {
                    viewModel.recordClipboard(clipText)
                }
                scope.launch {
                    val ambientDb = if (state.ambientWhisperEnabled) {
                        runCatching { SmartContextEngine.sampleAmbientDecibels(context) }.getOrNull()
                    } else null
                    viewModel.refreshSmartContext(context, ambientDb)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val voiceController = remember(context) {
        VoiceInputController(
            context = context,
            onPartialText = { partial -> inputText = partial },
            onFinalText = { finalText ->
                inputText = if (inputText.isBlank()) finalText else "$inputText $finalText".trim()
            },
            onListeningChanged = { listening -> isDictating = listening },
            onFailure = { msg -> viewModel.showToast(msg) }
        )
    }

    val ttsController = remember(context) {
        ReadAloudController(
            context = context,
            onSpeakingIdChanged = { id -> speakingMessageId = id },
            onPlaybackStateChanged = { st -> ttsPlaybackState = st }
        )
    }

    DisposableEffect(voiceController, ttsController) {
        onDispose {
            voiceController.release()
            ttsController.release()
        }
    }

    // Apply Ambient Whisper / Noisy-Room Auto-Volume gain to Read-Aloud TTS
    LaunchedEffect(state.smartContext.noiseLevel) {
        when (state.smartContext.noiseLevel) {
            AmbientNoiseLevel.QUIET_WHISPER -> ttsController.setVolumeGain(0.35f)
            AmbientNoiseLevel.NOISY_BOOST -> ttsController.setVolumeGain(1.0f)
            AmbientNoiseLevel.NORMAL -> ttsController.setVolumeGain(0.85f)
        }
    }

    // Foreground "Hey AllChat" Wake-Phrase Listener when toggled ON in Settings
    DisposableEffect(
        state.wakePhraseEnabled,
        isDictating,
        state.isStreaming,
        state.liveVoiceActive,
        showCommuteHud
    ) {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val shouldArm = state.wakePhraseEnabled &&
            micGranted &&
            !isDictating &&
            !state.isStreaming &&
            !state.liveVoiceActive &&
            !showCommuteHud
        val wakeListener = if (shouldArm) {
            WakePhraseListener(
                context = context,
                onWakeDetected = { trailing ->
                    if (trailing.isNotBlank()) {
                        viewModel.sendMessageWithWorkspaceContext(context, trailing)
                    } else {
                        voiceController.start()
                    }
                }
            ).also { it.start() }
        } else null
        onDispose {
            wakeListener?.stop()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                voiceController.start()
            } else {
                viewModel.showToast("Microphone permission is required for voice dictation.")
            }
        }
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) viewModel.attachImage(uri)
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            val captured = pendingCameraUri
            if (success && captured != null) {
                viewModel.attachImage(captured)
            }
        }
    )

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val photoFile = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                launchCamera()
            }
        }
    )

    val exportMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
        onResult = { uri ->
            val payload = pendingExportMarkdown
            pendingExportMarkdown = null
            if (uri != null && payload != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    viewModel.showNotice("EXPORT // Markdown file saved successfully.")
                }.onFailure { err ->
                    viewModel.showToast(err.message ?: "Failed to save Markdown file.")
                }
            }
        }
    )

    val scanFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.scanFileForAgentsMd(uri)
            }
        }
    )

    val batchDocsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.attachBatchDocuments(uris)
            }
        }
    )

    val snipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                snipSourceUri = uri
            }
        }
    )

    val liveCamPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                viewModel.setLiveCamEnabled(true)
            } else {
                viewModel.showToast("Camera permission is required for Live Camera Viewfinder.")
            }
        }
    )

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ChatUiEvent.OpenSessionsDrawer -> drawerState.open()
                ChatUiEvent.OpenModelPicker -> modelPickerOpenTrigger++
                ChatUiEvent.OpenConnectProvider -> onConnectProvider()
                ChatUiEvent.OpenThemeSheet -> showThemeSheet = true
                ChatUiEvent.OpenHelpSheet -> showHelpSheet = true
                ChatUiEvent.OpenMcpSheet -> showMcpSheet = true
                ChatUiEvent.OpenInitAgentsDialog -> showInitDialog = true
                ChatUiEvent.OpenCustomCommandsSheet -> showCustomCommandsSheet = true
                ChatUiEvent.OpenMemoryBank -> showMemoryBank = true
                ChatUiEvent.OpenVoiceNotes -> showVoiceNotesSheet = true
                is ChatUiEvent.SpeakRoutineText -> {
                    ttsController.speak(System.currentTimeMillis(), event.text)
                }
                ChatUiEvent.StartVoiceDictation -> {
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
                ChatUiEvent.LaunchCameraVision -> {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        launchCamera()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
                is ChatUiEvent.OpenPromptEditor -> {
                    editorInitialText = event.initialText.ifBlank { inputText }
                }
                is ChatUiEvent.OpenShareDialog -> {
                    shareDialogPayload = event.shareUrl to event.markdown
                }
                is ChatUiEvent.OpenExportStudio -> {
                    exportStudioPayload = event.title to event.markdown
                }
                is ChatUiEvent.TriggerExportMarkdown -> {
                    pendingExportMarkdown = event.markdown
                    exportMdLauncher.launch(event.suggestedFileName)
                }
                is ChatUiEvent.OpenQrDialog -> qrDialogPayload = event.initialPayload
                is ChatUiEvent.OpenPlotter -> plotterInitialExpr = event.initialExpression
                is ChatUiEvent.OpenDecisionMatrix -> {
                    decisionInitialPrompt = event.initialPrompt
                    if (event.initialPrompt.isNotBlank()) {
                        viewModel.buildDecisionMatrix(event.initialPrompt)
                    }
                }
                is ChatUiEvent.OpenCanvas -> canvasInitialContent = event.initialContent
                ChatUiEvent.OpenExpenses -> showExpensesDialog = true
                ChatUiEvent.OpenWatchlist -> showWatchlistDialog = true
                ChatUiEvent.OpenBleInspector -> showBleInspector = true
                ChatUiEvent.OpenCommuteHud -> showCommuteHud = true
                ChatUiEvent.OpenInterpreter -> showInterpreter = true
                ChatUiEvent.OpenSpotlight -> showSpotlightDialog = true
                is ChatUiEvent.OpenRedactor -> redactorInitialText = event.initialText
                ChatUiEvent.OpenCsvExplorer -> showCsvExplorer = true
                ChatUiEvent.OpenLocalWorkspace -> showLocalWorkspace = true
                ChatUiEvent.OpenVideoFrameExtractor -> showVideoExtractor = true
                ChatUiEvent.OpenColorPalette -> {
                    paletteInitialBitmap = null
                    showColorPalette = true
                }
                ChatUiEvent.OpenClipboardVault -> showClipboardVault = true
                ChatUiEvent.OpenToolkitHub -> showToolkitHub = true
                ChatUiEvent.OpenVoiceFormFiller -> showVoiceFormFiller = true
                is ChatUiEvent.OpenCliBuilder -> cliBuilderQuery = event.initialQuery
                ChatUiEvent.ExitApp -> {
                    (context as? Activity)?.finishAffinity()
                }
            }
        }
    }

    val displayedNodes = remember(state.ghostModeEnabled, state.ghostMessages, state.branchNodes) {
        if (state.ghostModeEnabled) {
            state.ghostMessages.map { msg ->
                BranchMessageNode(
                    message = msg,
                    parentKey = -1L,
                    siblingIds = listOf(msg.id),
                    siblingIndex = 0
                )
            }
        } else {
            state.branchNodes
        }
    }

    var autoScrollEnabled by remember(state.activeConversation?.id, state.ghostModeEnabled) { mutableStateOf(true) }
    val showJumpToLatest by remember {
        derivedStateOf {
            displayedNodes.isNotEmpty() && listState.canScrollForward
        }
    }

    // Pause auto-scrolling immediately if the user manually scrolls up; resume upon reaching bottom
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

    // Re-engage auto-scroll whenever the user submits a fresh message
    val lastNode = displayedNodes.lastOrNull()
    LaunchedEffect(lastNode?.message?.id) {
        if (lastNode?.message?.role == "USER") {
            autoScrollEnabled = true
        }
    }

    // Smart auto-scroll when a new turn arrives or while streaming (only if not paused by user)
    LaunchedEffect(displayedNodes.size, lastNode?.message?.id, lastNode?.message?.content?.length) {
        if (displayedNodes.isNotEmpty() && autoScrollEnabled) {
            if (state.isStreaming) {
                listState.scrollToItem(displayedNodes.lastIndex)
            } else {
                listState.animateScrollToItem(displayedNodes.lastIndex)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SwissCanvas,
                drawerShape = RectangleShape,
                modifier = Modifier
                    .width(310.dp)
                    .border(1.dp, SwissInk, RectangleShape)
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    // Drawer segmented header bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(SwissPaper),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "00 // LOG INDEX",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissMuted
                            )
                            Text(
                                text = "conversations",
                                style = MaterialTheme.typography.titleLarge,
                                color = SwissInk
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(SwissInk)
                        )
                        SwissIconCell(
                            icon = Icons.Filled.Add,
                            contentDescription = "New Chat",
                            onClick = {
                                viewModel.createNewChat()
                                scope.launch { drawerState.close() }
                            },
                            accent = true,
                            bordered = false,
                            cellSize = 52.dp,
                            modifier = Modifier
                                .fillMaxHeight()
                                .testTag("drawer_new_chat_button")
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SwissInk)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = {
                                Text(
                                    if (sidebarSemanticMode) "SEMANTIC SEARCH (PDF · LENS · MEM)…" else "FILTER THREADS…",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null, tint = SwissInk)
                            },
                            singleLine = true,
                            shape = RectangleShape,
                            colors = swissTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_search_input")
                        )

                        // Semantic Search Mode & Auto-Attach Context Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (sidebarSemanticMode) "SEMANTIC // ON" else "SEMANTIC // OFF",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sidebarSemanticMode) SwissWhite else SwissInk,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (sidebarSemanticMode) SwissInk else SwissPaper)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable { sidebarSemanticMode = !sidebarSemanticMode }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("sidebar_semantic_mode_toggle")
                            )
                            Text(
                                text = if (state.semanticAutoAttach) "AUTO-ATTACH: ON" else "AUTO-ATTACH: OFF",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.semanticAutoAttach) SwissWhite else SwissInk,
                                modifier = Modifier
                                    .background(if (state.semanticAutoAttach) SwissSignalRed else SwissPaper)
                                    .border(1.dp, if (state.semanticAutoAttach) SwissSignalRed else SwissInk, RectangleShape)
                                    .clickable { viewModel.setSemanticAutoAttach(!state.semanticAutoAttach) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("sidebar_semantic_auto_attach_toggle")
                            )
                        }

                        if (sidebarSemanticMode) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .background(SwissPaper)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "LOCAL 384D INDEX // ${state.knowledgeDocs.size} DOCS · ${sidebarLensHistory.size} LENS · ${state.memories.size} MEM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissMuted
                                )
                                if (state.searchQuery.trim().length < 2) {
                                    Text(
                                        text = "Type a concept above to rank saved PDFs, Live Lens scans, and Memory Bank facts by semantic similarity.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissMuted
                                    )
                                } else if (sidebarSemanticHits.isEmpty()) {
                                    Text(
                                        text = "No conceptual matches above threshold yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissMuted
                                    )
                                } else {
                                    sidebarSemanticHits.take(5).forEach { hit ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(SwissCanvas)
                                                .border(1.dp, SwissGridLine, RectangleShape)
                                                .clickable {
                                                    val attached = SemanticEmbeddingEngine.formatAutoAttachSnippet(hit)
                                                    inputText = if (inputText.isBlank()) {
                                                        "$attached\n\n"
                                                    } else {
                                                        "$inputText\n\n$attached"
                                                    }
                                                    viewModel.showNotice("SEMANTIC // Attached ${hit.item.sourceType.badge} (${hit.matchPercent}% match).")
                                                    scope.launch { drawerState.close() }
                                                }
                                                .padding(8.dp)
                                                .testTag("sidebar_semantic_hit_${hit.item.id}"),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "[${hit.item.sourceType.badge}] ${hit.item.title}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SwissInk,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${hit.matchPercent}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SwissSignalRed
                                                )
                                            }
                                            Text(
                                                text = hit.item.snippet,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SwissMuted,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.conversations.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(SwissPaper)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .swissGraphPaper()
                                    .swissRegistrationMarks(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "LOG // EMPTY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                    Text(
                                        text = "No conversations archived.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissMuted
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, SwissInk, RectangleShape)
                            ) {
                                items(
                                    count = state.conversations.size,
                                    key = { state.conversations[it].id }
                                ) { idx ->
                                    val conv = state.conversations[idx]
                                    val isActive = conv.id == state.activeConversation?.id
                                    val rowBg = if (isActive) SwissInk else SwissPaper
                                    val rowFg = if (isActive) SwissWhite else SwissInk
                                    val rowSub = if (isActive) SwissConcrete else SwissMuted

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(rowBg)
                                            .pointerInput(conv.id) {
                                                var dragLeft = 0f
                                                detectHorizontalDragGestures(
                                                    onDragStart = { dragLeft = 0f },
                                                    onHorizontalDrag = { _, dragAmount ->
                                                        if (dragAmount < 0f) dragLeft += -dragAmount
                                                    },
                                                    onDragEnd = {
                                                        if (dragLeft > 120f) {
                                                            viewModel.deleteConversation(conv.id)
                                                            viewModel.showNotice("THREAD // Deleted \"${conv.title.take(24)}\".")
                                                        }
                                                        dragLeft = 0f
                                                    },
                                                    onDragCancel = { dragLeft = 0f }
                                                )
                                            }
                                            .clickable {
                                                viewModel.selectConversation(conv.id)
                                                scope.launch { drawerState.close() }
                                            }
                                            .testTag("conversation_item_${conv.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(SwissSignalRed)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = conv.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = rowFg,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val totalTokens = conv.totalPromptTokens + conv.totalCompletionTokens
                                                if (totalTokens > 0) {
                                                    val costPart = if (conv.estimatedCostUsd > 0.0) {
                                                        " · ${CostEstimator.formatCost(conv.estimatedCostUsd, state.displayCurrency)}"
                                                    } else {
                                                        ""
                                                    }
                                                    Text(
                                                        text = "TOK // $totalTokens$costPart",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isActive) SwissSignalRed else rowSub
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    renamingConversation = conv
                                                    renameDraft = conv.title
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Edit,
                                                    contentDescription = "Rename conversation",
                                                    tint = rowSub,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteConversation(conv.id) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.DeleteOutline,
                                                    contentDescription = "Delete conversation",
                                                    tint = if (isActive) SwissSignalRed else SwissInk,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(SwissInk)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SwissRuleDivider(symbol = "/", modifier = Modifier.padding(horizontal = 12.dp))

                    // Pinned bottom Settings cell inside drawer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        SwissPanel(
                            containerColor = SwissPaper,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { drawerState.close() }
                                    onOpenSettings()
                                }
                                .testTag("drawer_settings_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(SwissInk),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = null,
                                        tint = SwissWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SYS // SETTINGS",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SwissInk
                                    )
                                    Text(
                                        text = "116 Providers, API keys & models",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissMuted
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = SwissInk
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(SwissCanvas)
        ) {
            val isCompactViewport = maxHeight < 500.dp || maxWidth < 380.dp
            val isWideScreen = maxWidth >= 720.dp
            val effectiveMiniMode = state.miniModeEnabled || isCompactViewport

            val estimatedCtxTokens = remember(state.branchNodes, state.activeConversation) {
                val chars = state.branchNodes.sumOf { it.message.content.length }
                ContextWindowEstimator.estimateTokens(
                    textChars = chars,
                    reportedPromptTokens = state.activeConversation?.totalPromptTokens ?: 0,
                    reportedCompletionTokens = state.activeConversation?.totalCompletionTokens ?: 0
                )
            }
            val maxCtxTokens = remember(state.selectedModel?.model?.modelId) {
                ContextWindowEstimator.maxTokensFor(state.selectedModel?.model?.modelId)
            }
            val ctxRatio = (estimatedCtxTokens.toFloat() / maxCtxTokens.toFloat()).coerceIn(0f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SwissCanvas)
                    .imePadding()
            ) {
                // Slim Segmented Top Utility Header Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SwissIconCell(
                            icon = Icons.Filled.Menu,
                            contentDescription = "Open conversations",
                            onClick = { scope.launch { drawerState.open() } },
                            bordered = false,
                            cellSize = if (effectiveMiniMode) 44.dp else 52.dp,
                            modifier = Modifier
                                .fillMaxHeight()
                                .testTag("chat_drawer_button")
                        )

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(SwissInk)
                        )

                        ModelPickerButton(
                            selectedModel = state.selectedModel,
                            availableModels = state.availableModels,
                            requiredCapability = ModelCapability.TEXT_CHAT,
                            onSelectModel = viewModel::selectModel,
                            onOpenSettings = onOpenSettings,
                            bordered = false,
                            openTrigger = modelPickerOpenTrigger,
                            supportsAutoRoute = true,
                            isAutoRouteEnabled = state.autoRouteEnabled,
                            onSelectAutoRoute = { viewModel.setAutoRouteEnabled(true) },
                            pinnedModelIds = state.pinnedModelIds,
                            modelLatencyMs = state.modelLatencyMs,
                            onTogglePinModel = viewModel::togglePinnedModel,
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(SwissInk)
                        )

                        // 1-Tap "Next Pinned Model" Cycle Button right next to active model
                        SwissIconCell(
                            icon = Icons.Outlined.Autorenew,
                            contentDescription = "Cycle next pinned model",
                            onClick = viewModel::cyclePinnedModel,
                            bordered = false,
                            accent = state.pinnedModelIds.size >= 2,
                            cellSize = if (effectiveMiniMode) 40.dp else 44.dp,
                            modifier = Modifier
                                .fillMaxHeight()
                                .testTag("cycle_pinned_model_button")
                        )

                        // Compact Live 429/503 Provider Cooldown Timer Pill directly in Header Model Bar
                        val headerCooldownSec = state.cooldownMap.values
                            .map { ((it - cooldownNowMs) / 1000L).toInt() }
                            .filter { it > 0 }
                            .maxOrNull() ?: 0
                        if (headerCooldownSec > 0) {
                            Box(
                                modifier = Modifier
                                    .background(SwissSignalRed, RectangleShape)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("header_cooldown_timer_badge")
                            ) {
                                Text(
                                    text = "429 ${headerCooldownSec}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite,
                                    maxLines = 1
                                )
                            }
                        }

                        val convTokens = (state.activeConversation?.totalPromptTokens ?: 0) +
                            (state.activeConversation?.totalCompletionTokens ?: 0)
                        val convCostUsd = state.activeConversation?.estimatedCostUsd ?: 0.0
                        if (convTokens > 0 && !effectiveMiniMode) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(SwissInk)
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(SwissConcrete)
                                    .padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "${convTokens}T",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissInk
                                )
                                if (convCostUsd > 0.0) {
                                    Text(
                                        text = CostEstimator.formatCost(convCostUsd, state.displayCurrency),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(SwissInk)
                        )

                        // Mini / Split-Screen Compact Mode Toggle
                        SwissIconCell(
                            icon = if (effectiveMiniMode) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess,
                            contentDescription = if (effectiveMiniMode) "Expand full workspace" else "Compact mini mode",
                            onClick = viewModel::toggleMiniMode,
                            bordered = false,
                            accent = state.miniModeEnabled,
                            cellSize = if (effectiveMiniMode) 40.dp else 44.dp,
                            modifier = Modifier
                                .fillMaxHeight()
                                .testTag("toggle_mini_mode_button")
                        )

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(SwissInk)
                        )

                        SwissIconCell(
                            icon = Icons.Filled.Add,
                            contentDescription = "New Chat",
                            onClick = viewModel::createNewChat,
                            bordered = false,
                            cellSize = if (effectiveMiniMode) 44.dp else 52.dp,
                            modifier = Modifier
                                .fillMaxHeight()
                                .testTag("top_new_chat_button")
                        )
                    }

                    // Active Persona / Quick Command Strip (auto-hides in Split-Screen / Mini Mode)
                    if (!effectiveMiniMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissSubtleFill)
                                .border(0.dp, SwissSubtleFill)
                                .clickable { showCommandPalette = true }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                .testTag("cmd_bar_palette_trigger"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val persona = state.activePersona
                            Text(
                                text = if (persona != null) {
                                    String.format(
                                        Locale.US,
                                        "PERSONA // @%s · T:%.1f",
                                        persona.handle.uppercase(),
                                        persona.temperature
                                    )
                                } else {
                                    "CMD // CTRL+K"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (persona != null) SwissSignalRed else SwissMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1-Tap Context Profile Switcher (STUDY / WORK / PERSONAL / ALL)
                                Text(
                                    text = "PROFILE // ${state.activeMemoryProfile.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.activeMemoryProfile == MemoryProfile.ALL) SwissInk else SwissWhite,
                                    modifier = Modifier
                                        .background(if (state.activeMemoryProfile == MemoryProfile.ALL) SwissPaper else SwissInk)
                                        .border(1.dp, SwissInk, RectangleShape)
                                        .clickable { viewModel.cycleMemoryProfile() }
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                        .testTag("cmd_bar_profile_toggle")
                                )
                                // 1-Tap Output Format Lock Cycler ([ FMT: AUTO | STEPS | TABLE | CODE ])
                                Text(
                                    text = "[ FMT: ${state.outputFormatMode.chipLabel} ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.outputFormatMode == OutputFormatMode.AUTO) SwissInk else SwissSignalRed,
                                    modifier = Modifier
                                        .clickable { viewModel.cycleOutputFormatMode() }
                                        .testTag("cmd_bar_format_toggle")
                                )
                                Text(
                                    text = if (state.memoryInjectionEnabled) "[ MEM: ON ]" else "[ MEM: OFF ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.memoryInjectionEnabled) SwissVerifiedInk else SwissSignalRed,
                                    modifier = Modifier
                                        .clickable { viewModel.toggleSessionMemory() }
                                        .testTag("cmd_bar_memory_toggle")
                                )
                                if (state.agentsMd.isNotBlank()) {
                                    Text(
                                        text = "[ AGENTS.MD ]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissVerifiedInk,
                                        modifier = Modifier.clickable { showInitDialog = true }
                                    )
                                }
                                if (state.activeShareCode != null) {
                                    Text(
                                        text = "[ SHARED ]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed,
                                        modifier = Modifier.clickable {
                                            viewModel.sendMessage("/share")
                                        }
                                    )
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    Text(
                                        text = "[ PiP ]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissInk,
                                        modifier = Modifier
                                            .clickable {
                                                val act = context as? Activity
                                                if (act != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    runCatching {
                                                        val params = PictureInPictureParams.Builder()
                                                            .setAspectRatio(Rational(3, 4))
                                                            .build()
                                                        act.enterPictureInPictureMode(params)
                                                    }.onFailure {
                                                        viewModel.showToast("PiP not supported on this device.")
                                                    }
                                                }
                                            }
                                            .testTag("enter_pip_button")
                                    )
                                }
                                Text(
                                    text = if (state.ghostModeEnabled) "[ GHOST:ON ]" else "[ GHOST ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.ghostModeEnabled) SwissSignalRed else SwissInk,
                                    modifier = Modifier
                                        .clickable { viewModel.toggleGhostMode() }
                                        .testTag("toggle_ghost_mode_button")
                                )
                                Text(
                                    text = "[ /HELP ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissInk,
                                    modifier = Modifier
                                        .clickable { showHelpSheet = true }
                                        .testTag("open_help_header_button")
                                )
                                if (isWideScreen) {
                                    Text(
                                        text = if (splitCanvasPinned) "[ SPLIT:ON ]" else "[ SPLIT ]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (splitCanvasPinned) SwissSignalRed else SwissInk,
                                        modifier = Modifier
                                            .clickable { splitCanvasPinned = !splitCanvasPinned }
                                            .testTag("toggle_split_workspace_button")
                                    )
                                }
                                Text(
                                    text = "[ PRESETS ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissInk,
                                    modifier = Modifier
                                        .clickable { showPersonaManager = true }
                                        .testTag("open_personas_header_button")
                                )
                            }
                        }

                        // Smart Context + Provider Health / 60s Rate-Limit Cooldown Strip
                        val activeCooldowns = state.cooldownMap.mapNotNull { (modelId, untilMs) ->
                            val remSec = ((untilMs - cooldownNowMs) / 1000L).toInt()
                            if (remSec > 0) {
                                val label = state.availableModels.firstOrNull { it.model.id == modelId }?.model?.displayName ?: "Model #$modelId"
                                "$label (${remSec}s)"
                            } else null
                        }
                        val smartCtx = state.smartContext
                        val hasSmartStrip = state.timeProfilesEnabled ||
                            smartCtx.isEcoModeActive ||
                            smartCtx.matchedZone != null ||
                            smartCtx.ambientDb != null ||
                            state.wakePhraseEnabled ||
                            activeCooldowns.isNotEmpty()
                        if (hasSmartStrip) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissCanvas)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.wakePhraseEnabled) {
                                    Text(
                                        text = "HEY ALLCHAT: ON",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                }
                                if (state.timeProfilesEnabled) {
                                    Text(
                                        text = "PROFILE // ${smartCtx.timeProfile.label.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissInk
                                    )
                                }
                                if (smartCtx.isEcoModeActive) {
                                    Text(
                                        text = "ECO ${smartCtx.batteryPercent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                }
                                smartCtx.matchedZone?.let { zone ->
                                    Text(
                                        text = "@${zone.name.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissVerifiedInk
                                    )
                                    zone.quickChips.forEach { chip ->
                                        Text(
                                            text = "[ ${chip.take(20).uppercase()} ]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SwissInk,
                                            modifier = Modifier.clickable { viewModel.sendMessage(chip) }
                                        )
                                    }
                                }
                                if (activeCooldowns.isNotEmpty()) {
                                    Text(
                                        text = "429 COOLDOWN // ${activeCooldowns.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                }
                            }
                        }

                        // Live Context Window Gauge & 1-Click Smart Compressor
                        if (state.branchNodes.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(SwissConcrete)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissPaper)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "CTX // ${ContextWindowEstimator.formatCompact(estimatedCtxTokens)} / ${ContextWindowEstimator.formatCompact(maxCtxTokens)} (${(ctxRatio * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ctxRatio >= 0.7f) SwissSignalRed else SwissMuted
                                )
                                LinearProgressIndicator(
                                    progress = { ctxRatio.coerceAtLeast(0.015f) },
                                    color = if (ctxRatio >= 0.7f) SwissSignalRed else SwissInk,
                                    trackColor = SwissConcrete,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(5.dp)
                                )
                                if (state.branchNodes.size >= 4 && !state.isStreaming) {
                                    Text(
                                        text = "[ COMPACT ]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed,
                                        modifier = Modifier
                                            .clickable { viewModel.triggerCompactThread() }
                                            .testTag("header_compact_thread_button")
                                    )
                                }
                            }
                        }
                    }

                    // Incognito Ghost Mode Banner
                    if (state.ghostModeEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissInk)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("ghost_mode_banner"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VisibilityOff,
                                    contentDescription = "Ghost mode active",
                                    tint = SwissSignalRed,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "GHOST MODE // INCOGNITO · ZERO DB OR SEARCH WRITES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "[ EXIT ]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                modifier = Modifier.clickable { viewModel.toggleGhostMode() }
                            )
                        }
                    }

                    // 80% Daily Budget Warning Amber Badge + Auto-Downgrade Flagship Override
                    if (state.isBudgetWarningActive) {
                        val budgetPct = (state.budgetUsageRatio * 100.0).toInt()
                        val amberBg = Color(0xFFFEF3C7)
                        val amberFg = Color(0xFF92400E)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(amberBg)
                                .border(1.dp, Color(0xFFD97706), RectangleShape)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                .testTag("budget_warning_banner"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "BUDGET %d%% // $%.2f / $%.2f · %s",
                                    budgetPct,
                                    state.todaySpendUsd,
                                    state.dailyBudgetConfig.dailyCapUsd,
                                    if (state.forceFlagshipOverride) "FLAGSHIP MANUAL" else "AUTO-FLASH ACTIVE"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = amberFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (state.forceFlagshipOverride) "[ AUTO-FLASH ]" else "[ OVERRIDE: FLAGSHIP ]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                modifier = Modifier
                                    .clickable { viewModel.toggleForceFlagshipOverride() }
                                    .testTag("budget_flagship_override_button")
                            )
                        }
                    }

                    if (state.isStreaming || state.isGeneratingStudy || state.isGeneratingMindMap || state.isEnhancingPrompt || state.summarizingCheckpointMsgId != null) {
                        LinearProgressIndicator(
                            color = SwissSignalRed,
                            trackColor = SwissConcrete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SwissInk)
                    )
                }

            // Status Notice Banner (ACTION // Call banners & slash command confirmations)
            AnimatedVisibility(visible = state.statusNotice != null) {
                state.statusNotice?.let { notice ->
                    val isActionBanner = notice.startsWith("ACTION //")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .background(if (isActionBanner) SwissSignalRed else SwissVerifiedTint, RectangleShape)
                            .border(1.dp, if (isActionBanner) SwissInk else SwissVerifiedInk, RectangleShape)
                            .clickable { viewModel.clearNotices() }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("chat_top_status_banner"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActionBanner) SwissWhite else SwissVerifiedInk,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss notice",
                            tint = if (isActionBanner) SwissWhite else SwissVerifiedInk,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Error Banner
            AnimatedVisibility(visible = state.errorToast != null) {
                state.errorToast?.let { toast ->
                    ErrorBanner(
                        message = toast,
                        onRetry = if (state.lastFailedPrompt != null) viewModel::retryLastFailedMessage else null,
                        onDismiss = viewModel::clearErrorToast,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Live Camera Viewfinder Q&A Dock (Split-Screen Top Preview)
            AnimatedVisibility(visible = state.liveCamEnabled) {
                LiveCameraViewfinderPanel(
                    controller = liveCamController,
                    onSnapNow = {
                        val frame = liveCamController.grabCurrentFrameBitmap()
                        if (frame != null) {
                            viewModel.attachBitmapAndSelectVision(frame, label = "Live Cam Frame")
                        } else {
                            viewModel.showToast("Camera frame not ready yet. Try again in a moment.")
                        }
                    },
                    onClose = { viewModel.setLiveCamEnabled(false) }
                )
            }

            // Messages Viewport (Adaptive Side-by-Side Split-Tab Workspace on Tablets / Foldables)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
            Box(
                modifier = Modifier
                    .weight(if (isWideScreen && splitCanvasPinned) 1.15f else 1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (displayedNodes.isEmpty()) {
                    if (effectiveMiniMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ALLCHAT // COMPACT MODE",
                                style = MaterialTheme.typography.labelMedium,
                                color = SwissInk
                            )
                            Text(
                                text = if (state.availableModels.isNotEmpty()) {
                                    "Type a message, call a contact, or tap Mic below."
                                } else {
                                    "Connect a provider in Settings to start chatting."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissMuted
                            )
                        }
                    } else {
                        ChatEmptyState(
                            hasConfiguredModels = state.availableModels.isNotEmpty(),
                            onOpenSettings = onOpenSettings,
                            onSelectStarter = { starter ->
                                inputText = starter
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = if (displayedNodes.count { it.message.role == "USER" } >= 3) 26.dp else 12.dp,
                            top = 14.dp,
                            bottom = 14.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 780.dp)
                    ) {
                        itemsIndexed(
                            items = displayedNodes,
                            key = { _, node -> node.message.id }
                        ) { index, node ->
                            val msg = node.message
                            val isLastAssistant = index == displayedNodes.lastIndex && msg.role == "ASSISTANT"
                            val checkpointAfter = if (!state.ghostModeEnabled) {
                                state.threadCheckpoints.firstOrNull { it.afterMessageId == msg.id }
                            } else null
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChatMessageCell(
                                    index = index + 1,
                                    node = node,
                                    displayCurrency = state.displayCurrency,
                                    showDetails = state.showDetails,
                                    showThinking = state.showThinking,
                                    isStreamingThis = isLastAssistant && state.isStreaming,
                                    isSpeakingThis = speakingMessageId == msg.id,
                                    canModify = !state.isStreaming && !state.ghostModeEnabled,
                                    isGeneratingStudy = state.isGeneratingStudy,
                                    isGeneratingMindMap = state.isGeneratingMindMap,
                                    canCheckpoint = !state.ghostModeEnabled && index >= 2 && !state.isStreaming && msg.role == "ASSISTANT",
                                    isSummarizingCheckpoint = state.summarizingCheckpointMsgId == msg.id,
                                    factClaims = state.factCheckByMessageId[msg.id].orEmpty(),
                                    isFactChecking = msg.id in state.checkingFactMessageIds,
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(msg.content))
                                        viewModel.showNotice("COPIED // Message copied to clipboard.")
                                    },
                                    onBeamQr = { qrDialogPayload = msg.content },
                                    onQuoteSwipe = {
                                        viewModel.setQuotedMessage(msg)
                                        viewModel.showNotice("QUOTE // Replying to message #${index + 1}.")
                                    },
                                    onToggleSpeak = { ttsController.toggleSpeak(msg.id, msg.content) },
                                    onEditUserMessage = { editingForkMessage = msg },
                                    onEditAssistantMessage = { editingForkMessage = msg },
                                    onSummarizeCheckpoint = { viewModel.createCheckpointAt(msg) },
                                    onRegenerateBranch = { viewModel.regenerateAsBranch(msg) },
                                    onGenerateStudyPack = { viewModel.generateStudyPackForMessage(msg) },
                                    onGenerateMindMap = { viewModel.generateMindMapForMessage(msg) },
                                    onOpenReadingRoom = {
                                        readingRoomPayload = (state.activeConversation?.title ?: "Reading Room") to msg.content
                                    },
                                    onOpenCanvas = {
                                        if (isWideScreen && splitCanvasPinned) {
                                            splitCanvasDraft = msg.content
                                        } else {
                                            canvasInitialContent = msg.content
                                        }
                                    },
                                    onVerifyClaims = { viewModel.verifyMessageClaims(msg) },
                                    onExportMessage = {
                                        exportStudioPayload = (state.activeConversation?.title ?: "AllChat Response") to msg.content
                                    },
                                    onSwitchBranch = { targetId -> viewModel.switchBranch(node.parentKey, targetId) },
                                    onInspectJson = { inspectingMessage = msg },
                                    onOpenCitationUrl = { url ->
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }
                                    },
                                    onOpenProxySettings = onOpenSettings
                                )
                                // Live Response Tuning: 1-Row Inline Tone & Length Morph Bar right below the latest AI reply
                                if (isLastAssistant && !msg.isError && msg.content.isNotBlank() && (!state.isStreaming || state.morphingMessageId == msg.id)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .testTag("inline_reply_morph_bar"),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (state.morphingMessageId == msg.id) "TUNING…" else "TUNE //",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (state.morphingMessageId == msg.id) SwissSignalRed else SwissMuted,
                                            modifier = Modifier.padding(end = 2.dp)
                                        )
                                        ReplyMorphStyle.entries.forEach { style ->
                                            val busy = state.isStreaming
                                            Text(
                                                text = style.chipLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (busy) SwissMuted else SwissInk,
                                                modifier = Modifier
                                                    .background(SwissPaper, RectangleShape)
                                                    .border(1.dp, if (busy) SwissConcrete else SwissInk, RectangleShape)
                                                    .clickable(enabled = !busy) {
                                                        viewModel.morphAssistantMessage(msg, style)
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                                    .testTag("morph_chip_${style.name.lowercase()}")
                                            )
                                        }
                                    }
                                }
                                if (checkpointAfter != null) {
                                    ThreadCheckpointDividerCard(
                                        checkpoint = checkpointAfter,
                                        onDelete = { viewModel.deleteCheckpoint(checkpointAfter.id) }
                                    )
                                }
                            }
                        }

                        // Smart 1-Tap Follow-Up Suggestion Pills below last AI response
                        if (!state.isStreaming && state.followUpSuggestions.isNotEmpty()) {
                            item(key = "smart_followup_chips") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp, bottom = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                        state.followUpSuggestions.forEachIndexed { idx, suggestion ->
                                            Row(
                                                modifier = Modifier
                                                    .background(SwissPaper, RectangleShape)
                                                    .border(1.dp, SwissInk, RectangleShape)
                                                    .clickable { viewModel.sendMessage(suggestion) }
                                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                                    .testTag("followup_chip_$idx"),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "0${idx + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SwissSignalRed
                                                )
                                                Text(
                                                    text = suggestion,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = SwissInk
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // In-Chat Message Minimap / Jump-to-Question Scrubber Rail
                    ChatMinimapScrubber(
                        branchNodes = displayedNodes,
                        currentVisibleIndex = listState.firstVisibleItemIndex,
                        onJumpToIndex = { targetIdx ->
                            autoScrollEnabled = targetIdx >= displayedNodes.lastIndex
                            scope.launch { listState.animateScrollToItem(targetIdx) }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )

                    // Floating "↓ Jump to Latest" pill when scrolled up reading earlier messages
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showJumpToLatest,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(SwissInk, RectangleShape)
                                .border(1.dp, SwissSignalRed, RectangleShape)
                                .clickable {
                                    autoScrollEnabled = true
                                    scope.launch {
                                        if (displayedNodes.isNotEmpty()) {
                                            listState.animateScrollToItem(displayedNodes.lastIndex)
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("chat_jump_to_latest_button"),
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
            }

                // True Side-by-Side Adaptive Split Workspace Pane (Tablet / Foldable >= 720dp)
                if (isWideScreen && splitCanvasPinned) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(SwissInk)
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                            .background(SwissPaper)
                            .testTag("wide_split_workspace_pane")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissInk)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "[ CANVAS ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (splitPaneTab == 0) SwissSignalRed else SwissWhite,
                                    modifier = Modifier
                                        .clickable { splitPaneTab = 0 }
                                        .testTag("split_tab_canvas")
                                )
                                Text(
                                    text = "[ PLOTTER ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (splitPaneTab == 1) SwissSignalRed else SwissWhite,
                                    modifier = Modifier
                                        .clickable { splitPaneTab = 1 }
                                        .testTag("split_tab_plotter")
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "[ FULL ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite,
                                    modifier = Modifier.clickable {
                                        if (splitPaneTab == 0) {
                                            canvasInitialContent = splitCanvasDraft
                                        } else {
                                            plotterInitialExpr = splitPlotExpr
                                        }
                                    }
                                )
                                Text(
                                    text = "[ X ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed,
                                    modifier = Modifier.clickable { splitCanvasPinned = false }
                                )
                            }
                        }

                        if (splitPaneTab == 0) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = splitCanvasDraft,
                                    onValueChange = { splitCanvasDraft = it },
                                    label = { Text("LIVE DOCUMENT CANVAS // SIDE-BY-SIDE") },
                                    shape = RectangleShape,
                                    colors = swissTextFieldColors(containerColor = SwissCanvas),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .testTag("split_canvas_editor")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SwissToggleCell(
                                        label = "SAVE V+",
                                        selected = false,
                                        onClick = {
                                            if (splitCanvasDraft.isNotBlank()) {
                                                viewModel.saveCanvasVersion("Split Canvas", splitCanvasDraft)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    SwissToggleCell(
                                        label = "SEND TO CHAT",
                                        selected = true,
                                        useSignalRedWhenSelected = true,
                                        onClick = {
                                            if (splitCanvasDraft.isNotBlank()) {
                                                viewModel.sendMessage("Review and improve this canvas draft:\n\n$splitCanvasDraft")
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = splitPlotExpr,
                                    onValueChange = { splitPlotExpr = it },
                                    label = { Text("f(x) =  (sin, cos, x^2, exp, sqrt)") },
                                    singleLine = true,
                                    shape = RectangleShape,
                                    colors = swissTextFieldColors(containerColor = SwissCanvas),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Canvas(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(SwissCanvas)
                                        .border(1.dp, SwissInk, RectangleShape)
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val midX = w / 2f
                                    val midY = h / 2f
                                    val xSpan = 10f
                                    for (g in -5..5) {
                                        val gx = midX + (g / xSpan) * (w / 2f)
                                        drawLine(SwissGridLine, Offset(gx, 0f), Offset(gx, h), 1f)
                                        val gy = midY - (g / xSpan) * (h / 2f)
                                        drawLine(SwissGridLine, Offset(0f, gy), Offset(w, gy), 1f)
                                    }
                                    drawLine(SwissInk, Offset(0f, midY), Offset(w, midY), 1.5f)
                                    drawLine(SwissInk, Offset(midX, 0f), Offset(midX, h), 1.5f)
                                    val path = Path()
                                    var started = false
                                    val steps = 220
                                    for (i in 0..steps) {
                                        val xVal = -xSpan + (2f * xSpan * i / steps)
                                        val yVal = MathExpressionEvaluator.evaluate(splitPlotExpr, xVal.toDouble())
                                        if (yVal != null && yVal.isFinite()) {
                                            val px = (i.toFloat() / steps) * w
                                            val py = midY - (yVal.toFloat() / xSpan) * (h / 2f)
                                            if (py in -h..2f * h) {
                                                if (!started) {
                                                    path.moveTo(px, py)
                                                    started = true
                                                } else {
                                                    path.lineTo(px, py)
                                                }
                                            } else {
                                                started = false
                                            }
                                        } else {
                                            started = false
                                        }
                                    }
                                    if (started) {
                                        drawPath(path, color = SwissSignalRed, style = Stroke(width = 3f))
                                    }
                                }
                                SwissToggleCell(
                                    label = "ASK AI ABOUT f(x)",
                                    selected = true,
                                    useSignalRedWhenSelected = true,
                                    onClick = {
                                        viewModel.sendMessage("Analyze the function f(x) = $splitPlotExpr: domain, roots, extrema, and asymptotes.")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Flush Architectural Composer Dock
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

                // Sequential Multi-Action Confirmation Stack (Calls, WhatsApp Voice/Video/Message, YouTube, Alarms, Timers, Apps)
                AnimatedVisibility(visible = state.queuedDeviceActions.isNotEmpty()) {
                    DeviceActionQueueStack(
                        actions = state.queuedDeviceActions,
                        quickContacts = state.quickContacts,
                        autoDialWebhookUrl = state.autoDialWebhookUrl,
                        onDialLaunched = { dialAction, usedBridge ->
                            viewModel.showNotice(dialAction.displayBanner)
                            launchingCallViaBridge = usedBridge
                            launchingCallAction = dialAction
                        },
                        onUpdateActive = viewModel::setPendingDeviceAction,
                        onRemoveQueuedAt = viewModel::removeQueuedDeviceAction,
                        onClearAll = viewModel::clearAllDeviceActions,
                        onSaveQuickContact = { name, phone -> viewModel.saveQuickContact(name, phone) },
                        onExecuteActive = { devAction ->
                            if (devAction is DeviceAction.DialPhone) {
                                launchDialAction(devAction)
                                if (state.queuedDeviceActions.size > 1) {
                                    viewModel.advanceDeviceActionQueue()
                                }
                            } else {
                                DeviceActionEngine.execute(context, devAction)
                                    .onSuccess { msg ->
                                        val hasMore = state.queuedDeviceActions.size > 1
                                        viewModel.advanceDeviceActionQueue()
                                        if (!hasMore) {
                                            viewModel.showNotice("ACTION // $msg")
                                        }
                                    }
                                    .onFailure { err ->
                                        viewModel.showToast(err.message ?: "Unable to execute device action.")
                                    }
                            }
                        },
                        onDismissActive = viewModel::dismissDeviceAction,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                // "Paste & Ask" Smart Clipboard Detector Floating Pill
                AnimatedVisibility(visible = state.clipboardHint != null) {
                    state.clipboardHint?.let { hint ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissInk)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "CLIPBOARD // ${hint.kindLabel} DETECTED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                    Text(
                                        text = hint.preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "[ ASK ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite,
                                    modifier = Modifier
                                        .background(SwissSignalRed, RectangleShape)
                                        .clickable {
                                            val raw = hint.rawText
                                            viewModel.dismissClipboardHint()
                                            inputText = if (hint.kindLabel == "LINK") {
                                                "@$raw Explain and summarize the key points."
                                            } else if (hint.kindLabel == "ERROR TRACE") {
                                                "Debug this error trace and provide the root cause + fix:\n\n$raw"
                                            } else {
                                                raw
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                        .testTag("clipboard_paste_ask_button")
                                )
                                Text(
                                    text = "[ SUM ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissInk,
                                    modifier = Modifier
                                        .background(SwissPaper, RectangleShape)
                                        .clickable {
                                            val raw = hint.rawText
                                            viewModel.dismissClipboardHint()
                                            onSummarizeClipboardText(raw)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                        .testTag("clipboard_summarize_button")
                                )
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss clipboard prompt",
                                    tint = SwissWhite,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { viewModel.dismissClipboardHint() }
                                )
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        }
                    }
                }

                // Batch Multi-Document Q&A Strip (5–10 Files Ready)
                AnimatedVisibility(visible = state.batchDocumentTitles.isNotEmpty()) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissVerifiedTint)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = SwissVerifiedInk,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "BATCH // ${state.batchDocumentTitles.size} DOCS ATTACHED FOR CROSS-DOC Q&A",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissVerifiedInk
                                )
                                Text(
                                    text = state.batchDocumentTitles.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            SwissIconCell(
                                icon = Icons.Filled.Close,
                                contentDescription = "Clear batch documents",
                                onClick = viewModel::clearBatchDocuments,
                                cellSize = 30.dp
                            )
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                    }
                }

                // Quoted Message Reply Strip (triggered via Swipe-Right on any message)
                AnimatedVisibility(visible = state.quotedMessage != null) {
                    state.quotedMessage?.let { quoted ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissSubtleFill)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FormatQuote,
                                    contentDescription = null,
                                    tint = SwissSignalRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "QUOTE // REPLYING TO ${quoted.role}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                    Text(
                                        text = quoted.content.replace("\n", " "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                SwissIconCell(
                                    icon = Icons.Filled.Close,
                                    contentDescription = "Clear quote",
                                    onClick = viewModel::clearQuotedMessage,
                                    cellSize = 30.dp
                                )
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        }
                    }
                }

                // Shared Document Context Strip (from Android Share Sheet -> Chat with Document)
                AnimatedVisibility(visible = state.pendingDocumentTitle != null) {
                    state.pendingDocumentTitle?.let { docTitle ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissVerifiedTint)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "DOC // SHARED DOCUMENT ATTACHED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissVerifiedInk
                                    )
                                    Text(
                                        text = docTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                SwissIconCell(
                                    icon = Icons.Filled.Close,
                                    contentDescription = "Detach document",
                                    onClick = viewModel::clearPendingDocument,
                                    cellSize = 30.dp
                                )
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        }
                    }
                }

                AnimatedVisibility(visible = state.pendingAttachmentUri != null) {
                    state.pendingAttachmentUri?.let { uriStr ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissSubtleFill)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .border(1.dp, SwissInk, RectangleShape)
                                ) {
                                    AsyncImage(
                                        model = uriStr,
                                        contentDescription = "Attached image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ATT // IMAGE PLATE READY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                    Text(
                                        text = "Will transmit with next prompt",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SwissMuted
                                    )
                                }
                                SwissIconCell(
                                    icon = Icons.Outlined.Brush,
                                    contentDescription = "Circle-to-Ask or Censor image",
                                    onClick = {
                                        val bmp = runCatching {
                                            val parsed = Uri.parse(uriStr)
                                            if (parsed.scheme == "file" || uriStr.startsWith("/")) {
                                                BitmapFactory.decodeFile(parsed.path ?: uriStr)
                                            } else {
                                                context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it) }
                                            }
                                        }.getOrNull()
                                        if (bmp != null) {
                                            annotatingBitmap = bmp
                                        } else {
                                            viewModel.showToast("Unable to decode image for markup.")
                                        }
                                    },
                                    accent = true,
                                    cellSize = 36.dp,
                                    modifier = Modifier.testTag("annotate_attachment_button")
                                )
                                SwissIconCell(
                                    icon = Icons.Filled.Close,
                                    contentDescription = "Remove attachment",
                                    onClick = viewModel::clearAttachment,
                                    cellSize = 36.dp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(SwissInk)
                            )
                        }
                    }
                }

                val typingSlashCommand = inputText.startsWith("/")
                val typingMention = inputText.startsWith("@") && !inputText.startsWith("@http", ignoreCase = true)
                val lastWord = inputText.substringAfterLast(' ').substringAfterLast('\n')
                val typingHashDoc = lastWord.startsWith("#")

                AnimatedVisibility(visible = showParamsPopover) {
                    RequestParamsPopover(
                        params = state.requestParams,
                        onUpdate = viewModel::updateRequestParams,
                        onReset = viewModel::resetRequestParams,
                        onClose = { showParamsPopover = false }
                    )
                }

                AnimatedVisibility(visible = typingSlashCommand) {
                    val token = inputText.takeWhile { !it.isWhitespace() }
                    SlashCommandSuggestionBar(
                        query = token,
                        customCommands = state.customSlashCommands,
                        onSelectCustomCommand = { custom ->
                            val arg = inputText.dropWhile { !it.isWhitespace() }.trim()
                            if (arg.isNotBlank()) {
                                inputText = ""
                                viewModel.sendMessage("${custom.trigger} $arg")
                            } else {
                                inputText = "${custom.trigger} "
                            }
                        },
                        onSelectCommand = { cmd ->
                            val arg = inputText.dropWhile { !it.isWhitespace() }.trim()
                            inputText = ""
                            viewModel.runSlashCommand(cmd, arg)
                        }
                    )
                }

                AnimatedVisibility(visible = !typingSlashCommand && typingHashDoc) {
                    KnowledgeTagBar(
                        query = lastWord,
                        docs = state.knowledgeDocs,
                        onSelectTag = { tag ->
                            val prefixLen = inputText.length - lastWord.length
                            inputText = inputText.substring(0, prefixLen) + "$tag "
                        },
                        onOpenKnowledgeBase = onOpenKnowledge
                    )
                }

                val lastMentionWord = lastWord.takeIf { it.startsWith("@") && !it.startsWith("@http", ignoreCase = true) }
                val matchingWorkspaceFiles = remember(lastMentionWord, state.workspaceFiles) {
                    if (lastMentionWord == null || state.workspaceFiles.isEmpty()) {
                        emptyList()
                    } else {
                        val needle = lastMentionWord.removePrefix("@").trim()
                        state.workspaceFiles.filter {
                            needle.isEmpty() ||
                                it.name.contains(needle, ignoreCase = true) ||
                                it.relativePath.contains(needle, ignoreCase = true)
                        }.take(8)
                    }
                }

                AnimatedVisibility(visible = !typingSlashCommand && !typingHashDoc && matchingWorkspaceFiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper)
                            .border(1.dp, SwissInk, RectangleShape)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "@FILES",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed
                        )
                        matchingWorkspaceFiles.forEach { fileEntry ->
                            Text(
                                text = "@${fileEntry.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissInk,
                                modifier = Modifier
                                    .background(SwissCanvas, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable {
                                        val prefixLen = inputText.length - (lastMentionWord?.length ?: 0)
                                        inputText = inputText.substring(0, prefixLen) + "@${fileEntry.name} "
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = !typingSlashCommand && !typingHashDoc && (showPersonaQuickBar || typingMention)) {
                    val filterToken = if (typingMention) {
                        inputText.takeWhile { !it.isWhitespace() }
                    } else {
                        ""
                    }
                    PersonaCommandBar(
                        personas = state.personas,
                        activePersona = state.activePersona,
                        filterQuery = filterToken,
                        onSelectPersona = { chosen ->
                            viewModel.selectPersona(chosen)
                            if (typingMention) {
                                val afterToken = inputText.dropWhile { !it.isWhitespace() }.trimStart()
                                inputText = afterToken
                            }
                            showPersonaQuickBar = false
                        },
                        onOpenManager = {
                            showPersonaQuickBar = false
                            showPersonaManager = true
                        }
                    )
                }

                // Compact Power-User Grounding, Native Tools, Live Lens/Voice/Cam/Snip, Batch Docs & Memory Strip
                // Auto-collapses cleanly in Small Window / Split-Screen Mini Mode to maximize chat viewport
                if (!effectiveMiniMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissSubtleFill)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zero-Prompt Live Lens Auto-Explainer & Translator
                    Row(
                        modifier = Modifier
                            .background(SwissSignalRed, RectangleShape)
                            .border(1.dp, SwissSignalRed, RectangleShape)
                            .clickable { onOpenLiveLens(false) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("live_lens_chip_button"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = "Open Live Lens Auto-Explainer & Translator",
                            tint = SwissWhite,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "LENS",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissWhite
                        )
                    }

                    // Hands-Free Live Voice Conversation Loop
                    Row(
                        modifier = Modifier
                            .background(if (state.liveVoiceActive) SwissSignalRed else SwissInk, RectangleShape)
                            .border(1.dp, if (state.liveVoiceActive) SwissSignalRed else SwissInk, RectangleShape)
                            .clickable { viewModel.openLiveVoice() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("live_voice_chip_button"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = "Open Hands-Free Live Voice Loop",
                            tint = SwissWhite,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissWhite
                        )
                    }

                    // Live Camera Viewfinder Q&A Toggle
                    Row(
                        modifier = Modifier
                            .background(if (state.liveCamEnabled) SwissSignalRed else SwissPaper, RectangleShape)
                            .border(1.dp, if (state.liveCamEnabled) SwissSignalRed else SwissInk, RectangleShape)
                            .clickable {
                                if (state.liveCamEnabled) {
                                    viewModel.setLiveCamEnabled(false)
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.setLiveCamEnabled(true)
                                    } else {
                                        liveCamPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("live_cam_toggle_button"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Videocam,
                            contentDescription = "Toggle Live Camera Viewfinder",
                            tint = if (state.liveCamEnabled) SwissWhite else SwissInk,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (state.liveCamEnabled) "CAM ON" else "CAM",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.liveCamEnabled) SwissWhite else SwissInk
                        )
                    }

                    // Region Snip (Crop Screenshot / Chart / UI Bug to Vision)
                    Row(
                        modifier = Modifier
                            .background(SwissPaper, RectangleShape)
                            .border(1.dp, SwissInk, RectangleShape)
                            .clickable {
                                snipPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("region_snip_button"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Crop,
                            contentDescription = "Region Snip Screenshot to Vision",
                            tint = SwissInk,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "SNIP",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissInk
                        )
                    }

                    // Batch Multi-Doc Drop (5-10 PDFs/TXT/MD at once)
                    Row(
                        modifier = Modifier
                            .background(if (state.batchDocumentTitles.isNotEmpty()) SwissSignalRed else SwissPaper, RectangleShape)
                            .border(1.dp, if (state.batchDocumentTitles.isNotEmpty()) SwissSignalRed else SwissInk, RectangleShape)
                            .clickable {
                                batchDocsLauncher.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "text/*"))
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("batch_docs_button"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = "Batch Attach Multiple Documents",
                            tint = if (state.batchDocumentTitles.isNotEmpty()) SwissWhite else SwissInk,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (state.batchDocumentTitles.isNotEmpty()) "+DOCS (${state.batchDocumentTitles.size})" else "+DOCS",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.batchDocumentTitles.isNotEmpty()) SwissWhite else SwissInk
                        )
                    }

                    Row(
                        modifier = Modifier
                            .background(if (state.webSearchEnabled) SwissSignalRed else SwissPaper, RectangleShape)
                            .border(1.dp, if (state.webSearchEnabled) SwissSignalRed else SwissInk, RectangleShape)
                            .clickable { viewModel.toggleWebSearch() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("web_search_toggle_button"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = "Toggle Web Search",
                            tint = if (state.webSearchEnabled) SwissWhite else SwissInk,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (state.webSearchEnabled) "WEB ON" else "WEB",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.webSearchEnabled) SwissWhite else SwissInk
                        )
                    }

                    Row(
                        modifier = Modifier
                            .background(if (state.toolsEnabled) SwissSignalRed else SwissPaper, RectangleShape)
                            .border(1.dp, if (state.toolsEnabled) SwissSignalRed else SwissInk, RectangleShape)
                            .clickable { viewModel.toggleTools() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("tools_toggle_button"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Build,
                            contentDescription = "Toggle Built-in Function Calling Tools",
                            tint = if (state.toolsEnabled) SwissWhite else SwissInk,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (state.toolsEnabled) "TOOLS ON" else "TOOLS",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.toolsEnabled) SwissWhite else SwissInk
                        )
                    }

                    Row(
                        modifier = Modifier
                            .background(
                                if (showParamsPopover || state.requestParams.hasCustomOverride) SwissInk else SwissPaper,
                                RectangleShape
                            )
                            .border(1.dp, SwissInk, RectangleShape)
                            .clickable { showParamsPopover = !showParamsPopover }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("request_params_toggle_button"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Request Parameters",
                            tint = if (showParamsPopover || state.requestParams.hasCustomOverride) SwissWhite else SwissInk,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (state.requestParams.jsonMode) "PARAMS · JSON" else if (state.requestParams.hasCustomOverride) "PARAMS *" else "PARAMS",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showParamsPopover || state.requestParams.hasCustomOverride) SwissWhite else SwissInk
                        )
                    }

                    SwissToggleCell(
                        label = "#DOCS (${state.knowledgeDocs.size})",
                        selected = false,
                        onClick = {
                            if (state.knowledgeDocs.isEmpty()) {
                                onOpenKnowledge()
                            } else {
                                inputText = if (inputText.isBlank() || inputText.endsWith(" ")) {
                                    "${inputText}#"
                                } else {
                                    "$inputText #"
                                }
                            }
                        },
                        modifier = Modifier.testTag("composer_docs_chip")
                    )

                    val activeMemCount = state.memories.count { it.isEnabled }
                    SwissToggleCell(
                        label = "MEM ($activeMemCount)",
                        selected = activeMemCount > 0,
                        onClick = { showMemoryBank = true },
                        modifier = Modifier.testTag("composer_memory_chip")
                    )

                    SwissToggleCell(
                        label = if (state.isSavingNote) "SAVING…" else "NOTES (${state.voiceNotes.size})",
                        selected = state.isSavingNote,
                        onClick = { showVoiceNotesSheet = true },
                        modifier = Modifier.testTag("composer_voice_notes_chip")
                    )

                    SwissToggleCell(
                        label = "TOOLKIT (18)",
                        selected = true,
                        useSignalRedWhenSelected = true,
                        onClick = { showToolkitHub = true },
                        modifier = Modifier.testTag("composer_toolkit_hub_chip")
                    )

                    SwissToggleCell(
                        label = if (state.tokenDietEnabled) "DIET: ON" else "DIET",
                        selected = state.tokenDietEnabled,
                        useSignalRedWhenSelected = true,
                        onClick = viewModel::toggleTokenDiet,
                        modifier = Modifier.testTag("composer_token_diet_chip")
                    )

                    SwissToggleCell(
                        label = if (state.guidedModeEnabled) "GUIDED: ON" else "GUIDED",
                        selected = state.guidedModeEnabled,
                        useSignalRedWhenSelected = true,
                        onClick = viewModel::toggleGuidedMode,
                        modifier = Modifier.testTag("composer_guided_mode_chip")
                    )

                    SwissToggleCell(
                        label = if (state.verifyFixEnabled) "VERIFY: ON" else "VERIFY",
                        selected = state.verifyFixEnabled,
                        useSignalRedWhenSelected = true,
                        onClick = viewModel::toggleVerifyFix,
                        modifier = Modifier.testTag("composer_verify_fix_chip")
                    )

                    SwissToggleCell(
                        label = "@FILES (${state.workspaceFiles.size})",
                        selected = state.workspaceFiles.isNotEmpty(),
                        onClick = { showLocalWorkspace = true },
                        modifier = Modifier.testTag("composer_workspace_chip")
                    )

                    SwissToggleCell(
                        label = "VAULT (${state.clipboardHistory.size})",
                        selected = state.clipboardHistory.isNotEmpty(),
                        onClick = { showClipboardVault = true },
                        modifier = Modifier.testTag("composer_clipboard_vault_chip")
                    )

                    state.routines.forEach { routine ->
                        SwissToggleCell(
                            label = "⚡ ${routine.name.uppercase()}",
                            selected = false,
                            onClick = { viewModel.runRoutine(routine) },
                            modifier = Modifier.testTag("composer_routine_chip_${routine.id}")
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SwissConcrete)
                )
                }

                // Read-Aloud Karaoke Mini Audio Player Bar
                val ambientLabel = state.smartContext.ambientDb?.let { db ->
                    when (state.smartContext.noiseLevel) {
                        AmbientNoiseLevel.QUIET_WHISPER -> "${db}dB WHISPER"
                        AmbientNoiseLevel.NOISY_BOOST -> "${db}dB BOOST"
                        AmbientNoiseLevel.NORMAL -> "${db}dB"
                    }
                }
                ReadAloudKaraokeBar(
                    state = ttsPlaybackState,
                    ambientDbLabel = ambientLabel,
                    onTogglePauseResume = { ttsController.togglePauseResume() },
                    onRewind = { ttsController.rewindSentence() },
                    onSkip = { ttsController.skipSentence() },
                    onCycleSpeed = {
                        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        val curIdx = speeds.indexOfFirst { kotlin.math.abs(it - ttsPlaybackState.speed) < 0.05f }
                        val nextSpeed = speeds[(curIdx + 1) % speeds.size]
                        ttsController.setPlaybackSpeed(nextSpeed)
                    },
                    onStop = { ttsController.stop() }
                )

                // Negative-Constraint Quick Pill Bar directly above the keyboard
                if (!effectiveMiniMode) {
                    NegativeConstraintPillBar(
                        activeConstraints = state.activeConstraints,
                        onToggleConstraint = viewModel::toggleConstraint
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SwissConcrete)
                    )
                }

                // Local Token-Saving Paste Minifier Pill (auto-triggers on long pastes >= 1,500 chars)
                val minifyPreview = remember(inputText) {
                    if (PromptPasteMinifier.shouldOfferMinify(inputText)) {
                        PromptPasteMinifier.minify(inputText)
                    } else null
                }
                if ((minifyPreview != null && minifyPreview.savedPercent >= 3) || preMinifyInputBackup != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissSubtleFill)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("composer_paste_minifier_bar"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (minifyPreview != null && minifyPreview.savedPercent >= 3) {
                            Text(
                                text = "PASTE // ${inputText.length} CHARS",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissMuted
                            )
                            Text(
                                text = "[ MINIFY PASTE · -${minifyPreview.savedPercent}% (~${minifyPreview.estimatedTokensSaved} TOK) ]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite,
                                modifier = Modifier
                                    .background(SwissInk)
                                    .clickable {
                                        preMinifyInputBackup = inputText
                                        inputText = minifyPreview.minifiedText
                                        viewModel.showNotice(
                                            "MINIFY // Trimmed ${minifyPreview.savedPercent}% (~${minifyPreview.estimatedTokensSaved} tokens saved)."
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("composer_minify_paste_button")
                            )
                        } else if (preMinifyInputBackup != null) {
                            Text(
                                text = "MINIFIED // COMPACTED TO ${inputText.length} CHARS",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissVerifiedInk
                            )
                            Text(
                                text = "[ UNDO ]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                modifier = Modifier
                                    .border(1.dp, SwissSignalRed, RectangleShape)
                                    .clickable {
                                        preMinifyInputBackup?.let { inputText = it }
                                        preMinifyInputBackup = null
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("composer_undo_minify_button")
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SwissConcrete)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Left '+' button opening Arena / Summarize / Image / Video / Personas / Vision Attachments
                    SwissIconCell(
                        icon = Icons.Filled.Add,
                        contentDescription = "Open tools and attachments",
                        onClick = { showPlusMenu = true },
                        inverted = true,
                        cellSize = 48.dp,
                        modifier = Modifier.testTag("composer_plus_button")
                    )

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            if (it.isBlank()) preMinifyInputBackup = null
                        },
                        placeholder = {
                            Text(
                                if (state.isEnhancingPrompt) {
                                    "PROMPT DOCTOR // OPTIMIZING…"
                                } else if (isDictating) {
                                    "LISTENING // SPEAK NOW…"
                                } else if (state.ghostModeEnabled) {
                                    "GHOST PROMPT // NOT SAVED…"
                                } else if (state.selectedModel != null) {
                                    "PROMPT // ${state.selectedModel?.model?.displayName}…"
                                } else {
                                    "CONFIGURE PROVIDER IN SETTINGS…"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        maxLines = 5,
                        shape = RectangleShape,
                        colors = swissTextFieldColors(containerColor = SwissCanvas),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_prompt_input")
                    )

                    // 1-Tap Prompt Doctor ("✨ Enhance Prompt") Magic Wand
                    SwissIconCell(
                        icon = Icons.Outlined.AutoFixHigh,
                        contentDescription = "Enhance prompt with Prompt Doctor",
                        onClick = {
                            viewModel.enhancePrompt(inputText) { rewritten ->
                                inputText = rewritten
                            }
                        },
                        enabled = inputText.isNotBlank() && !state.isEnhancingPrompt && !state.isStreaming,
                        accent = state.isEnhancingPrompt,
                        cellSize = 48.dp,
                        modifier = Modifier.testTag("enhance_prompt_wand_button")
                    )

                    // Voice-to-Prompt Microphone Button
                    SwissIconCell(
                        icon = Icons.Filled.Mic,
                        contentDescription = if (isDictating) "Stop voice dictation" else "Voice dictate prompt",
                        onClick = {
                            if (isDictating) {
                                voiceController.stop()
                            } else {
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
                        },
                        accent = isDictating,
                        cellSize = 48.dp,
                        modifier = Modifier.testTag("voice_dictation_button")
                    )

                    // Per-Message Creativity / Thinking Effort Override Dial (LOW / BAL / DEEP)
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .background(
                                when (state.reasoningEffort) {
                                    ReasoningEffort.DEEP -> SwissInk
                                    ReasoningEffort.LOW -> SwissSubtleFill
                                    ReasoningEffort.BALANCED -> SwissPaper
                                },
                                RectangleShape
                            )
                            .border(
                                1.dp,
                                if (state.reasoningEffort == ReasoningEffort.DEEP) SwissSignalRed else SwissInk,
                                RectangleShape
                            )
                            .clickable(onClick = viewModel::cycleReasoningEffort)
                            .padding(horizontal = 8.dp)
                            .testTag("reasoning_effort_dial"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "THINK",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.reasoningEffort == ReasoningEffort.DEEP) SwissSignalRed else SwissMuted
                            )
                            Text(
                                text = state.reasoningEffort.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.reasoningEffort == ReasoningEffort.DEEP) SwissWhite else SwissInk
                            )
                        }
                    }

                    if (state.isStreaming) {
                        SwissIconCell(
                            icon = Icons.Filled.Stop,
                            contentDescription = "Stop generation",
                            onClick = viewModel::stopGeneration,
                            accent = true,
                            cellSize = 48.dp,
                            modifier = Modifier.testTag("stop_chat_button")
                        )
                    } else {
                        SwissIconCell(
                            icon = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            onClick = {
                                if (isDictating) voiceController.stop()
                                val prompt = inputText
                                inputText = ""
                                val frame = if (state.liveCamEnabled) {
                                    liveCamController.grabCurrentFrameBitmap()
                                } else null
                                viewModel.sendMessageWithWorkspaceContext(context, prompt, frame)
                            },
                            enabled = inputText.isNotBlank() ||
                                state.pendingAttachmentUri != null ||
                                state.batchDocumentTitles.isNotEmpty() ||
                                state.liveCamEnabled,
                            accent = true,
                            cellSize = 48.dp,
                            modifier = Modifier.testTag("send_chat_button")
                        )
                    }
                }
            }
        }

            // Subtle Non-Intrusive "MEMORY // UPDATED" Toast Pill
            AnimatedVisibility(
                visible = state.memoryUpdatedToast != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
            ) {
                state.memoryUpdatedToast?.let { pillText ->
                    Row(
                        modifier = Modifier
                            .background(SwissInk, RectangleShape)
                            .border(1.dp, SwissSignalRed, RectangleShape)
                            .clickable {
                                viewModel.dismissMemoryUpdatedToast()
                                showMemoryBank = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("memory_updated_toast_pill"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(SwissSignalRed)
                        )
                        Text(
                            text = pillText,
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissWhite
                        )
                    }
                }
            }

            // Mobile Thumb-Gesture Navigation: Swipe Left from Right Edge -> Active Tools & Context Drawer
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(22.dp)
                    .pointerInput(Unit) {
                        var dragSum = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragSum = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragSum += dragAmount
                                if (dragSum < -42f) {
                                    dragSum = 0f
                                    showActiveToolsDrawer = true
                                }
                            }
                        )
                    }
                    .testTag("right_edge_tools_swipe_zone")
            )

            val pinnedModelsList = remember(state.availableModels, state.pinnedModelIds) {
                state.pinnedModelIds.mapNotNull { id ->
                    state.availableModels.firstOrNull { it.model.id == id }
                }
            }
            ActiveToolsDrawerSheet(
                visible = showActiveToolsDrawer,
                selectedModel = state.selectedModel,
                pinnedModels = pinnedModelsList,
                modelLatencyMs = state.modelLatencyMs,
                memoryInjectionEnabled = state.memoryInjectionEnabled,
                memoryCount = state.memories.count { it.isEnabled },
                pinnedMemoryCount = state.memories.count { it.isEnabled && it.isPinned },
                webSearchEnabled = state.webSearchEnabled,
                toolsEnabled = state.toolsEnabled,
                tokenDietEnabled = state.tokenDietEnabled,
                ghostModeEnabled = state.ghostModeEnabled,
                reasoningEffort = state.reasoningEffort,
                onSelectModel = viewModel::selectModel,
                onToggleMemoryInjection = viewModel::toggleSessionMemory,
                onToggleWebSearch = viewModel::toggleWebSearch,
                onToggleTools = viewModel::toggleTools,
                onToggleTokenDiet = viewModel::toggleTokenDiet,
                onToggleGhostMode = viewModel::toggleGhostMode,
                onSelectReasoningEffort = viewModel::setReasoningEffort,
                onOpenCommandPalette = { showCommandPalette = true },
                onOpenMemoryBank = { showMemoryBank = true },
                onOpenLiveLens = { onOpenLiveLens(false) },
                onOpenKnowledge = onOpenKnowledge,
                onOpenSettings = onOpenSettings,
                onDismiss = { showActiveToolsDrawer = false }
            )
        }
    }

    // '+' Tools & Studio Bottom Sheet (Summarize, Image Studio, Video Studio, Vision Attachments)
    if (showPlusMenu) {
        val supportsVision = state.selectedModel?.model?.supportsImageInput == true
        ModalBottomSheet(
            onDismissRequest = { showPlusMenu = false },
            containerColor = SwissCanvas,
            shape = RectangleShape,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SwissInk, RectangleShape)
            ) {
                SwissPanelHeader(
                    leftLabel = "MODULES // TOOLS & STUDIO",
                    rightLabel = "BYOK LOCAL",
                    rightAccent = true
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SwissInk, RectangleShape)
                    ) {
                        StudioToolRow(
                            index = "★",
                            title = "Power Toolkit Hub (18 Studios)",
                            subtitle = "QR Drop Zone, Voice Form Filler, CLI Builder, Plotter, Canvas, CSV, BLE, Commute & more",
                            icon = Icons.Outlined.Bolt,
                            onClick = {
                                showPlusMenu = false
                                showToolkitHub = true
                            },
                            testTag = "plus_menu_power_toolkit"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "00",
                            title = "Arena Compare",
                            subtitle = "Compare 2–3 models side-by-side & pick the winner",
                            icon = Icons.Outlined.CompareArrows,
                            onClick = {
                                showPlusMenu = false
                                onOpenArena()
                            },
                            testTag = "plus_menu_arena"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "01",
                            title = "Pipeline Chain (Multi-Model)",
                            subtitle = "Chain 2–3 models sequentially (Outline → Code → Audit)",
                            icon = Icons.Outlined.AccountTree,
                            onClick = {
                                showPlusMenu = false
                                onOpenPipeline()
                            },
                            testTag = "plus_menu_pipeline"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "02",
                            title = "Knowledge Base (Local RAG)",
                            subtitle = "${state.knowledgeDocs.size} indexed docs · Reference via #doc-name in Chat",
                            icon = Icons.Outlined.LibraryBooks,
                            onClick = {
                                showPlusMenu = false
                                onOpenKnowledge()
                            },
                            testTag = "plus_menu_knowledge"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "03",
                            title = "Summarize",
                            subtitle = "Text, PDF/Markdown files, or web URLs",
                            icon = Icons.Outlined.AutoAwesome,
                            onClick = {
                                showPlusMenu = false
                                onOpenSummarize()
                            },
                            testTag = "plus_menu_summarize"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "04",
                            title = "Image Studio",
                            subtitle = "Generate archival plates & save to gallery",
                            icon = Icons.Outlined.Image,
                            onClick = {
                                showPlusMenu = false
                                onOpenImage()
                            },
                            testTag = "plus_menu_image"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "05",
                            title = "Video Studio",
                            subtitle = "Async AI motion jobs & inline playback",
                            icon = Icons.Outlined.Movie,
                            onClick = {
                                showPlusMenu = false
                                onOpenVideo()
                            },
                            testTag = "plus_menu_video"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "06",
                            title = "Custom Slash Commands (/commands)",
                            subtitle = "${state.customSlashCommands.size} reusable prompt templates (/fix, /explain, /json)",
                            icon = Icons.Outlined.Terminal,
                            onClick = {
                                showPlusMenu = false
                                showCustomCommandsSheet = true
                            },
                            testTag = "plus_menu_custom_commands"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "07",
                            title = "Personas & Presets",
                            subtitle = "System prompts, temperature & max_tokens (@Coder)",
                            icon = Icons.Outlined.Psychology,
                            onClick = {
                                showPlusMenu = false
                                showPersonaManager = true
                            },
                            testTag = "plus_menu_personas"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "08",
                            title = "UI Color Themes (/themes)",
                            subtitle = "Active: ${state.activeTheme.displayName} (7 palettes)",
                            icon = Icons.Outlined.Palette,
                            onClick = {
                                showPlusMenu = false
                                showThemeSheet = true
                            },
                            testTag = "plus_menu_themes"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "09",
                            title = "Memory Bank (/memory)",
                            subtitle = "${state.memories.count { it.isEnabled }} active user facts injected into every chat",
                            icon = Icons.Outlined.Memory,
                            onClick = {
                                showPlusMenu = false
                                showMemoryBank = true
                            },
                            testTag = "plus_menu_memory"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "10",
                            title = "Tone & Rewrite Studio",
                            subtitle = "WhatsApp / Email / LinkedIn polish, Hindi/Marathi/Spanish + Copy & Open WhatsApp",
                            icon = Icons.Outlined.Draw,
                            onClick = {
                                showPlusMenu = false
                                onOpenRewrite()
                            },
                            testTag = "plus_menu_rewrite"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "11",
                            title = "Code & Error Debugger",
                            subtitle = "Paste stack trace or broken file → Root Cause + Fixed Code Patch + Copy Patch",
                            icon = Icons.Outlined.BugReport,
                            onClick = {
                                showPlusMenu = false
                                onOpenDebugger()
                            },
                            testTag = "plus_menu_debugger"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "12",
                            title = "YouTube Lecture Extractor",
                            subtitle = "Paste YouTube URL → Timestamped Chapters, Key Takeaways & Study Notes",
                            icon = Icons.Outlined.PlayCircleOutline,
                            onClick = {
                                showPlusMenu = false
                                onOpenYouTube()
                            },
                            testTag = "plus_menu_youtube"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "13",
                            title = "Live Lens (Auto-Explain & Translator)",
                            subtitle = "Zero-prompt camera explainer, 1.5s steady auto-scan & live text translation",
                            icon = Icons.Outlined.Translate,
                            onClick = {
                                showPlusMenu = false
                                onOpenLiveLens(false)
                            },
                            testTag = "plus_menu_live_lens"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "14",
                            title = "Voice & Lens Assistant Overlay",
                            subtitle = "Google-Assistant-style sheet · Auto-mic, timers, calls, apps (Vol Up+Down)",
                            icon = Icons.Outlined.GraphicEq,
                            onClick = {
                                showPlusMenu = false
                                onOpenAssistantOverlay()
                            },
                            testTag = "plus_menu_assistant_overlay"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "15",
                            title = "Live Voice Loop (Hands-Free)",
                            subtitle = "Full-screen Waveform Orb · Auto-listen → Stream → Speak → Re-listen",
                            icon = Icons.Outlined.GraphicEq,
                            onClick = {
                                showPlusMenu = false
                                viewModel.openLiveVoice()
                            },
                            testTag = "plus_menu_live_voice"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "14",
                            title = "Live Camera Viewfinder Q&A",
                            subtitle = "Keep camera live at top of Chat · Auto-snaps frame on every Send",
                            icon = Icons.Outlined.Videocam,
                            onClick = {
                                showPlusMenu = false
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    viewModel.setLiveCamEnabled(true)
                                } else {
                                    liveCamPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            testTag = "plus_menu_live_cam"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "15",
                            title = "Region Snip (Screenshot Crop)",
                            subtitle = "Draw crop box over an error, chart, or UI bug → Send straight to Vision",
                            icon = Icons.Outlined.Crop,
                            onClick = {
                                showPlusMenu = false
                                snipPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            testTag = "plus_menu_region_snip"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "16",
                            title = "Batch Multi-Doc Q&A (5–10 Files)",
                            subtitle = "Drop multiple PDFs, TXT, or MD files together for cross-document Q&A",
                            icon = Icons.Outlined.FolderOpen,
                            onClick = {
                                showPlusMenu = false
                                batchDocsLauncher.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "text/*"))
                            },
                            testTag = "plus_menu_batch_docs"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "17",
                            title = "Export Studio (PDF / DOCX / Notion)",
                            subtitle = "Export current chat as styled PDF, Word .docx, Notion MD, or Share Sheet",
                            icon = Icons.Outlined.IosShare,
                            onClick = {
                                showPlusMenu = false
                                viewModel.openExportStudioForCurrentSession()
                            },
                            testTag = "plus_menu_export_studio"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "19",
                            title = "Voice Form Filler (/form)",
                            subtitle = "Dictate into structured Standup, Bug Report, Workout, or Lab Log templates",
                            icon = Icons.Outlined.EditNote,
                            onClick = {
                                showPlusMenu = false
                                showVoiceFormFiller = true
                            },
                            testTag = "plus_menu_voice_form"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "20",
                            title = "Interactive CLI Command Builder (/cmd)",
                            subtitle = "FFmpeg, ADB, Git, Bash & PowerShell cards with flag tooltips & editable inputs",
                            icon = Icons.Outlined.Terminal,
                            onClick = {
                                showPlusMenu = false
                                cliBuilderQuery = ""
                            },
                            testTag = "plus_menu_cli_builder"
                        )
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                        StudioToolRow(
                            index = "21",
                            title = "Slash Commands Reference (/help)",
                            subtitle = "All built-in commands: /cmd, /form, /ghost, /remember, /mcp, /compact, /export",
                            icon = Icons.Outlined.Terminal,
                            onClick = {
                                showPlusMenu = false
                                showHelpSheet = true
                            },
                            testTag = "plus_menu_help"
                        )
                    }

                    if (supportsVision) {
                        Spacer(Modifier.height(12.dp))
                        SwissRuleDivider(symbol = "*")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "VISION INPUT // ATTACH IMAGE PLATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SwissButton(
                                text = "Gallery",
                                leadingIcon = Icons.Outlined.PhotoLibrary,
                                variant = SwissButtonVariant.OUTLINE,
                                onClick = {
                                    showPlusMenu = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_gallery_option")
                            )
                            SwissButton(
                                text = "Camera",
                                leadingIcon = Icons.Outlined.CameraAlt,
                                variant = SwissButtonVariant.INK,
                                onClick = {
                                    showPlusMenu = false
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        launchCamera()
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("take_photo_option")
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }

    renamingConversation?.let { conv ->
        AlertDialog(
            onDismissRequest = { renamingConversation = null },
            containerColor = SwissCanvas,
            shape = RectangleShape,
            title = {
                Column {
                    Text("THREAD // RENAME", style = MaterialTheme.typography.labelSmall, color = SwissMuted)
                    Text("Rename Conversation", style = MaterialTheme.typography.titleLarge, color = SwissInk)
                }
            },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    shape = RectangleShape,
                    colors = swissTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                SwissButton(
                    text = "Save",
                    variant = SwissButtonVariant.PRIMARY,
                    minHeight = 42.dp,
                    onClick = {
                        viewModel.renameConversation(conv.id, renameDraft)
                        renamingConversation = null
                    }
                )
            },
            dismissButton = {
                SwissButton(
                    text = "Cancel",
                    variant = SwissButtonVariant.OUTLINE,
                    minHeight = 42.dp,
                    onClick = { renamingConversation = null }
                )
            }
        )
    }

    if (showPersonaManager) {
        PersonaManagerSheet(
            personas = state.personas,
            activePersona = state.activePersona,
            availableModels = state.availableModels,
            onSelectPersona = viewModel::selectPersona,
            onSavePersona = viewModel::savePersona,
            onDeletePersona = viewModel::deletePersona,
            onDismiss = { showPersonaManager = false }
        )
    }

    editingForkMessage?.let { targetMsg ->
        EditMessageForkDialog(
            initialContent = targetMsg.content,
            onDismiss = { editingForkMessage = null },
            onConfirmFork = { newText ->
                editingForkMessage = null
                if (targetMsg.role == "ASSISTANT") {
                    viewModel.editAndForkAssistantMessage(targetMsg, newText)
                } else {
                    viewModel.editMessageAndFork(targetMsg, newText)
                }
            }
        )
    }

    if (showHelpSheet) {
        CommandHelpSheet(
            onRunCommand = { cmd -> viewModel.runSlashCommand(cmd) },
            onDismiss = { showHelpSheet = false }
        )
    }

    if (showThemeSheet) {
        ThemeSwitcherSheet(
            activeTheme = state.activeTheme,
            onSelectTheme = { preset -> viewModel.selectTheme(preset) },
            onDismiss = { showThemeSheet = false }
        )
    }

    if (showMcpSheet) {
        McpServersSheet(
            servers = state.mcpServers,
            checkResults = state.mcpCheckResults,
            checkingIds = state.checkingMcpIds,
            onAddServer = viewModel::addMcpServer,
            onDeleteServer = viewModel::deleteMcpServer,
            onCheckServer = viewModel::checkMcpServer,
            onCheckAll = viewModel::checkAllMcpServers,
            onDismiss = { showMcpSheet = false }
        )
    }

    if (showInitDialog) {
        AgentsMdInitDialog(
            currentAgentsMd = state.agentsMd,
            isGenerating = state.isGeneratingAgentsMd,
            hasActiveModel = state.selectedModel != null,
            onPickScanFile = {
                scanFileLauncher.launch(arrayOf("text/*", "application/pdf", "application/json", "*/*"))
            },
            onGenerateWithAi = viewModel::generateAgentsMdWithAi,
            onSaveAgentsMd = viewModel::saveAgentsMd,
            onExportAgentsMd = { content ->
                pendingExportMarkdown = content
                exportMdLauncher.launch("AGENTS.md")
            },
            onDismiss = { showInitDialog = false }
        )
    }

    editorInitialText?.let { initialDraft ->
        PromptEditorDialog(
            initialText = initialDraft,
            onLoadIntoComposer = { drafted -> inputText = drafted },
            onSendDirectly = { drafted ->
                inputText = ""
                viewModel.sendMessage(drafted)
            },
            onDismiss = { editorInitialText = null }
        )
    }

    shareDialogPayload?.let { (shareUrl, markdown) ->
        ShareSessionDialog(
            shareUrl = shareUrl,
            transcriptMarkdown = markdown,
            onSystemShare = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "AllChat Session: $shareUrl")
                    putExtra(Intent.EXTRA_TEXT, "$shareUrl\n\n$markdown")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share AllChat Session"))
            },
            onUnshare = { viewModel.unshareCurrentSession() },
            onDismiss = { shareDialogPayload = null }
        )
    }

    if (showCustomCommandsSheet) {
        CustomSlashCommandsSheet(
            commands = state.customSlashCommands,
            onSaveCommand = viewModel::saveCustomSlashCommand,
            onDeleteCommand = viewModel::deleteCustomSlashCommand,
            onUseCommand = { cmd -> inputText = "${cmd.trigger} " },
            onDismiss = { showCustomCommandsSheet = false }
        )
    }

    inspectingMessage?.let { msg ->
        RawJsonInspectorDialog(
            modelLabel = msg.modelLabel ?: "ASSISTANT",
            requestJson = msg.rawRequestJson,
            responseJson = msg.rawResponseJson,
            onDismiss = { inspectingMessage = null }
        )
    }

    if (showMemoryBank) {
        MemoryBankDialog(
            memories = state.memories,
            autoExtractEnabled = state.autoExtractMemories,
            activeProfile = state.activeMemoryProfile,
            semanticAutoAttachEnabled = state.semanticAutoAttach,
            onSelectProfile = viewModel::setMemoryProfile,
            onToggleSemanticAutoAttach = viewModel::setSemanticAutoAttach,
            onToggleAutoExtract = viewModel::setAutoExtractMemories,
            onAddMemory = { fact, cat, prof -> viewModel.addMemory(fact, cat, prof) },
            onUpdateMemory = viewModel::updateMemory,
            onToggleMemory = viewModel::toggleMemory,
            onTogglePinMemory = viewModel::setMemoryPinned,
            onDeleteMemory = viewModel::deleteMemory,
            onClearAllMemories = viewModel::clearAllMemories,
            onDismiss = { showMemoryBank = false }
        )
    }

    if (showCommandPalette) {
        CommandPaletteDialog(
            availableModels = state.availableModels,
            pinnedModelIds = state.pinnedModelIds,
            modelLatencyMs = state.modelLatencyMs,
            activeModelId = state.selectedModel?.model?.id,
            conversations = state.conversations,
            memoryCount = state.memories.count { it.isEnabled },
            onSelectModel = viewModel::selectModel,
            onSelectConversation = viewModel::selectConversation,
            onNewChat = viewModel::createNewChat,
            onOpenLiveLens = onOpenLiveLens,
            onOpenMemory = { showMemoryBank = true },
            onOpenSettings = onOpenSettings,
            onOpenArena = onOpenArena,
            onOpenKnowledge = onOpenKnowledge,
            onOpenSummarize = onOpenSummarize,
            onDismiss = { showCommandPalette = false }
        )
    }

    if (showVoiceNotesSheet) {
        VoiceNotesInboxSheet(
            notes = state.voiceNotes,
            isSavingNote = state.isSavingNote,
            onSaveNewMemo = viewModel::saveVoiceQuickNote,
            onUpdateNote = viewModel::updateVoiceNote,
            onDeleteNote = viewModel::deleteVoiceNote,
            onDismiss = { showVoiceNotesSheet = false }
        )
    }

    state.activeStudyPack?.let { pack ->
        StudyQuizDialog(
            studyPack = pack,
            onDismiss = viewModel::dismissStudyPack
        )
    }

    state.activeMindMap?.let { rootNode ->
        MindMapDialog(
            rootNode = rootNode,
            isExpandingNode = state.isExpandingMindMapNode,
            onExpandSubTopic = viewModel::expandMindMapNode,
            onDismiss = viewModel::dismissMindMap
        )
    }

    if (state.liveVoiceActive) {
        LiveVoiceOverlay(
            selectedModel = state.selectedModel,
            onStreamReply = { history, userText, onPartial, onSentence ->
                viewModel.streamLiveVoiceTurn(history, userText, onPartial, onSentence)
            },
            onDismiss = viewModel::closeLiveVoice
        )
    }

    snipSourceUri?.let { srcUri ->
        RegionSnipDialog(
            sourceUri = srcUri,
            onConfirmCrop = { croppedBitmap ->
                snipSourceUri = null
                viewModel.attachBitmapAndSelectVision(croppedBitmap, label = "Region Snip")
            },
            onDismiss = { snipSourceUri = null }
        )
    }

    exportStudioPayload?.let { (title, markdown) ->
        ExportStudioDialog(
            title = title,
            markdownContent = markdown,
            onDismiss = { exportStudioPayload = null }
        )
    }

    // Power Toolkit 16-Tool Launcher Hub
    if (showToolkitHub) {
        PowerToolkitHubDialog(
            onDismiss = { showToolkitHub = false },
            onSelectTool = { action ->
                showToolkitHub = false
                when (action) {
                    PowerToolAction.INTERACTIVE_CANVAS -> canvasInitialContent = state.branchNodes.lastOrNull { it.message.role == "ASSISTANT" }?.message?.content.orEmpty()
                    PowerToolAction.SPOTLIGHT_SEARCH -> showSpotlightDialog = true
                    PowerToolAction.EXPENSE_TRACKER -> showExpensesDialog = true
                    PowerToolAction.WATCHLIST -> showWatchlistDialog = true
                    PowerToolAction.GRAPH_PLOTTER -> plotterInitialExpr = "a * sin(b * x) + c"
                    PowerToolAction.DECISION_MATRIX -> decisionInitialPrompt = ""
                    PowerToolAction.CSV_EXPLORER -> showCsvExplorer = true
                    PowerToolAction.LOCAL_WORKSPACE -> showLocalWorkspace = true
                    PowerToolAction.FACE_INTERPRETER -> showInterpreter = true
                    PowerToolAction.COMMUTE_HUD -> showCommuteHud = true
                    PowerToolAction.BLE_INSPECTOR -> showBleInspector = true
                    PowerToolAction.QR_GENERATOR -> qrDialogPayload = ""
                    PowerToolAction.VIDEO_FRAME -> showVideoExtractor = true
                    PowerToolAction.COLOR_PALETTE -> {
                        paletteInitialBitmap = null
                        showColorPalette = true
                    }
                    PowerToolAction.PRIVACY_REDACTOR -> redactorInitialText = inputText
                    PowerToolAction.CLIPBOARD_VAULT -> showClipboardVault = true
                    PowerToolAction.VOICE_FORM_FILLER -> showVoiceFormFiller = true
                    PowerToolAction.CLI_COMMAND_BUILDER -> cliBuilderQuery = ""
                }
            }
        )
    }

    annotatingBitmap?.let { bmp ->
        ImageAnnotatorDialog(
            sourceBitmap = bmp,
            onDismiss = { annotatingBitmap = null },
            onApplyAnnotatedBitmap = { annotated ->
                annotatingBitmap = null
                viewModel.attachBitmapAndSelectVision(annotated, label = "Marked-Up Image")
            },
            onExtractPalette = { extracted ->
                annotatingBitmap = null
                paletteInitialBitmap = extracted
                showColorPalette = true
            }
        )
    }

    qrDialogPayload?.let { payload ->
        QrStudioDialog(
            initialPayload = payload,
            onDismiss = { qrDialogPayload = null },
            onNotify = { viewModel.showNotice(it) }
        )
    }

    if (showVideoExtractor) {
        VideoFrameExtractorDialog(
            onDismiss = { showVideoExtractor = false },
            onSendFrameToAi = { frameBmp, label ->
                showVideoExtractor = false
                viewModel.attachBitmapAndSelectVision(frameBmp, label = label)
            }
        )
    }

    if (showColorPalette) {
        ColorPaletteDialog(
            initialBitmap = paletteInitialBitmap,
            onDismiss = {
                showColorPalette = false
                paletteInitialBitmap = null
            },
            onNotify = { viewModel.showNotice(it) }
        )
    }

    redactorInitialText?.let { initial ->
        PrivacyRedactorDialog(
            initialText = initial,
            onDismiss = { redactorInitialText = null },
            onAskAiWithRedacted = { sanitized ->
                redactorInitialText = null
                inputText = sanitized
            },
            onNotify = { viewModel.showNotice(it) }
        )
    }

    plotterInitialExpr?.let { expr ->
        InteractiveGraphPlotterDialog(
            initialExpression = expr,
            onDismiss = { plotterInitialExpr = null },
            onAskAiExplain = { prompt ->
                plotterInitialExpr = null
                viewModel.sendMessage(prompt)
            }
        )
    }

    if (showCsvExplorer) {
        CsvChartExplorerDialog(
            onDismiss = { showCsvExplorer = false },
            onAskAiInsights = { prompt ->
                showCsvExplorer = false
                viewModel.sendMessage(prompt)
            }
        )
    }

    if (decisionInitialPrompt != null || state.activeDecisionMatrix != null) {
        DecisionMatrixDialog(
            initialPrompt = decisionInitialPrompt.orEmpty(),
            matrix = state.activeDecisionMatrix,
            isBuilding = state.isBuildingMatrix,
            onBuildMatrix = viewModel::buildDecisionMatrix,
            onDismiss = {
                decisionInitialPrompt = null
                viewModel.dismissDecisionMatrix()
            }
        )
    }

    readingRoomPayload?.let { (title, content) ->
        ReadingRoomDialog(
            title = title,
            markdownContent = content,
            onDismiss = { readingRoomPayload = null },
            onAskAboutPassage = { passage ->
                readingRoomPayload = null
                inputText = "Explain this passage in more detail:\n\n> $passage\n\n"
            }
        )
    }

    canvasInitialContent?.let { content ->
        InteractiveCanvasDialog(
            initialContent = content,
            versions = state.canvasVersions,
            workspaceFiles = state.workspaceFiles,
            onSaveVersion = viewModel::saveCanvasVersion,
            onAiEditBlock = { fullDoc, selected, instr ->
                viewModel.editCanvasSelection(fullDoc, selected, instr)
            },
            onNotify = { viewModel.showNotice(it) },
            onDismiss = { canvasInitialContent = null }
        )
    }

    if (showExpensesDialog) {
        ExpenseTrackerDialog(
            expenses = state.expenses,
            onSaveExpense = viewModel::saveExpense,
            onDeleteExpense = viewModel::deleteExpense,
            onScanReceiptBitmap = { bmp -> viewModel.scanReceiptBitmap(bmp) },
            onNotify = { viewModel.showNotice(it) },
            onDismiss = { showExpensesDialog = false }
        )
    }

    if (showWatchlistDialog) {
        WatchlistDialog(
            items = state.watchlist,
            onAddItem = viewModel::saveWatchlistItem,
            onDeleteItem = viewModel::deleteWatchlistItem,
            onCheckItem = { item -> viewModel.checkWatchlistItem(item) },
            onNotify = { viewModel.showNotice(it) },
            onDismiss = { showWatchlistDialog = false }
        )
    }

    if (showSpotlightDialog) {
        SpotlightSearchDialog(
            onSearch = { q, f -> viewModel.spotlightSearch(q, f) },
            onLoadTopicClusters = { viewModel.loadTopicClusters() },
            onOpenConversation = { cid ->
                showSpotlightDialog = false
                viewModel.selectConversation(cid)
            },
            onDismiss = { showSpotlightDialog = false }
        )
    }

    if (showClipboardVault) {
        ClipboardVaultDialog(
            snippets = state.clipboardHistory,
            onMergeAndSend = { merged ->
                showClipboardVault = false
                viewModel.sendMessage(merged)
            },
            onDeleteSnippet = viewModel::deleteClipboardSnippet,
            onClearAll = viewModel::clearClipboardVault,
            onDismiss = { showClipboardVault = false }
        )
    }

    if (showBleInspector) {
        BleInspectorDialog(
            onSendTelemetryToAi = { log ->
                showBleInspector = false
                viewModel.sendMessage(log)
            },
            onDismiss = { showBleInspector = false }
        )
    }

    if (showLocalWorkspace) {
        LocalWorkspaceDialog(
            workspaceUri = state.localWorkspaceUri,
            files = state.workspaceFiles,
            onMountFolder = { treeUri -> viewModel.setLocalWorkspaceTree(context, treeUri) },
            onRefreshFiles = { viewModel.refreshWorkspaceFiles(context) },
            onInjectFileIntoPrompt = { fileName, fileContent ->
                showLocalWorkspace = false
                inputText = buildString {
                    if (inputText.isNotBlank()) appendLine(inputText)
                    appendLine("File: @$fileName")
                    appendLine("```")
                    appendLine(fileContent.take(6000))
                    appendLine("```")
                }
                viewModel.showNotice("WORKSPACE // Loaded @$fileName into prompt.")
            },
            onDismiss = { showLocalWorkspace = false }
        )
    }

    if (showCommuteHud) {
        CommuteHudDialog(
            wakePhraseEnabled = state.wakePhraseEnabled,
            lastAssistantReply = state.branchNodes.lastOrNull { it.message.role == "ASSISTANT" }?.message?.content.orEmpty(),
            onSubmitVoicePrompt = { spoken -> viewModel.sendMessage(spoken) },
            onReadLastReply = {
                val last = state.branchNodes.lastOrNull { it.message.role == "ASSISTANT" }?.message
                if (last != null) ttsController.speak(last.id, last.content)
            },
            onQuickNavigateHome = {
                viewModel.sendMessage("Navigate home on Google Maps")
            },
            onDismiss = { showCommuteHud = false }
        )
    }

    if (showInterpreter) {
        FaceToFaceInterpreterDialog(
            onTranslate = { text, fromLang, toLang ->
                viewModel.translateInterpreterUtterance(text, fromLang, toLang)
            },
            onDismiss = { showInterpreter = false }
        )
    }

    if (showVoiceFormFiller) {
        VoiceFormFillerDialog(
            templates = state.voiceFormTemplates,
            onSaveTemplates = viewModel::saveVoiceFormTemplates,
            onFillWithAi = { tpl, transcript -> viewModel.fillVoiceForm(tpl, transcript) },
            onSendToChat = { markdownTable ->
                viewModel.sendMessage(markdownTable)
            },
            onDismiss = { showVoiceFormFiller = false },
            onNotify = { viewModel.showNotice(it) }
        )
    }

    cliBuilderQuery?.let { initialQ ->
        CliCommandBuilderDialog(
            initialQuery = initialQ,
            onBuildCommand = { q -> viewModel.buildCliCommand(q) },
            onSendToChat = { cmdText ->
                viewModel.sendMessage("Explain and verify this command:\n```bash\n$cmdText\n```")
            },
            onDismiss = { cliBuilderQuery = null },
            onNotify = { viewModel.showNotice(it) }
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
private fun StudioToolRow(
    index: String,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SwissPaper)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = index,
            style = MaterialTheme.typography.labelLarge,
            color = SwissSignalRed
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(SwissConcrete)
                .border(1.dp, SwissInk, RectangleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SwissInk, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = SwissInk)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = SwissMuted)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SwissInk)
    }
}

@Composable
private fun BranchSwitcherBar(
    node: BranchMessageNode,
    onSwitchBranch: (Long) -> Unit,
    inverted: Boolean = false
) {
    if (!node.hasBranches) return
    val prevId = node.siblingIds.getOrNull(node.siblingIndex - 1)
    val nextId = node.siblingIds.getOrNull(node.siblingIndex + 1)
    val bg = if (inverted) SwissInk else SwissConcrete
    val fg = if (inverted) SwissWhite else SwissInk
    val borderCol = if (inverted) SwissWhite else SwissInk

    Row(
        modifier = Modifier
            .background(bg, RectangleShape)
            .border(1.dp, borderCol, RectangleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clickable(enabled = prevId != null) { prevId?.let(onSwitchBranch) }
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .testTag("branch_prev_${node.message.id}")
        ) {
            Icon(
                imageVector = Icons.Outlined.ChevronLeft,
                contentDescription = "Previous branch",
                tint = if (prevId != null) fg else SwissMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = "${node.siblingIndex + 1} / ${node.siblingCount}",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Box(
            modifier = Modifier
                .clickable(enabled = nextId != null) { nextId?.let(onSwitchBranch) }
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .testTag("branch_next_${node.message.id}")
        ) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "Next branch",
                tint = if (nextId != null) fg else SwissMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ChatMessageCell(
    index: Int,
    node: BranchMessageNode,
    displayCurrency: DisplayCurrency,
    showDetails: Boolean,
    showThinking: Boolean,
    isStreamingThis: Boolean,
    isSpeakingThis: Boolean,
    canModify: Boolean,
    isGeneratingStudy: Boolean,
    isGeneratingMindMap: Boolean,
    canCheckpoint: Boolean = false,
    isSummarizingCheckpoint: Boolean = false,
    factClaims: List<FactCheckClaim> = emptyList(),
    isFactChecking: Boolean = false,
    onCopy: () -> Unit,
    onBeamQr: () -> Unit = {},
    onQuoteSwipe: () -> Unit,
    onToggleSpeak: () -> Unit,
    onEditUserMessage: () -> Unit,
    onEditAssistantMessage: () -> Unit = {},
    onSummarizeCheckpoint: () -> Unit = {},
    onRegenerateBranch: () -> Unit,
    onGenerateStudyPack: () -> Unit,
    onGenerateMindMap: () -> Unit,
    onOpenReadingRoom: () -> Unit = {},
    onOpenCanvas: () -> Unit = {},
    onVerifyClaims: () -> Unit = {},
    onExportMessage: () -> Unit,
    onSwitchBranch: (Long) -> Unit,
    onInspectJson: () -> Unit,
    onOpenCitationUrl: (String) -> Unit,
    onOpenProxySettings: () -> Unit
) {
    val message = node.message
    val isUser = message.role == "USER"
    val paddedIndex = index.toString().padStart(2, '0')

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                var dragRight = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragRight = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) dragRight += dragAmount
                    },
                    onDragEnd = {
                        if (dragRight > 95f && message.content.isNotBlank()) {
                            onQuoteSwipe()
                        }
                        dragRight = 0f
                    },
                    onDragCancel = { dragRight = 0f }
                )
            }
            .pointerInput(message.id, message.content) {
                detectTapGestures(
                    onDoubleTap = {
                        if (message.content.isNotBlank()) onCopy()
                    }
                )
            },
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (message.isError) {
            ErrorBanner(
                message = message.content,
                suggestProxySetting = message.suggestProxySetting,
                onOpenProxySettings = onOpenProxySettings,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canModify) {
                    SwissButton(
                        text = "Retry Request",
                        leadingIcon = Icons.Outlined.Refresh,
                        variant = SwissButtonVariant.OUTLINE,
                        minHeight = 40.dp,
                        onClick = onRegenerateBranch,
                        modifier = Modifier.testTag("regenerate_button")
                    )
                }
                BranchSwitcherBar(node = node, onSwitchBranch = onSwitchBranch)
            }
            return
        }

        if (isUser) {
            // Stark inverted obsidian block for user input
            Surface(
                color = SwissInk,
                shape = RectangleShape,
                border = BorderStroke(1.dp, SwissInk),
                shadowElevation = 0.dp,
                modifier = Modifier.widthIn(max = 620.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$paddedIndex // USR / PROMPT",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissConcrete
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BranchSwitcherBar(
                                node = node,
                                onSwitchBranch = onSwitchBranch,
                                inverted = true
                            )
                            if (message.content.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clickable(onClick = onBeamQr)
                                        .padding(2.dp)
                                        .testTag("beam_qr_user_${message.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.QrCode2,
                                        contentDescription = "Beam via QR",
                                        tint = SwissConcrete,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            if (canModify) {
                                Box(
                                    modifier = Modifier
                                        .clickable(onClick = onEditUserMessage)
                                        .padding(2.dp)
                                        .testTag("edit_fork_message_${message.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit and fork branch",
                                        tint = SwissConcrete,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(SwissSignalRed)
                            )
                        }
                    }

                    if (!message.attachmentUri.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SwissWhite, RectangleShape)
                        ) {
                            AsyncImage(
                                model = message.attachmentUri,
                                contentDescription = "Message image attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            )
                            Text(
                                text = "PLATE // ATTACHED INPUT",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissInk,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissPaper)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = SwissWhite
                        )
                    }
                }
            }
        } else {
            // Structured flush white architectural panel for assistant output
            Surface(
                color = SwissPaper,
                shape = RectangleShape,
                border = BorderStroke(1.dp, SwissInk),
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SwissPanelHeader(
                        leftLabel = "$paddedIndex // ${(message.modelLabel ?: "ASSISTANT")}",
                        rightLabel = if (isStreamingThis) "STREAM // LIVE" else "STATUS: OK",
                        rightAccent = isStreamingThis,
                        backgroundColor = SwissSubtleFill
                    )

                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (message.content.isNotBlank()) {
                            MarkdownText(
                                markdown = message.content,
                                showThinking = showThinking,
                                thinkingDurationSec = message.thinkingDurationSec
                            )
                        } else if (isStreamingThis) {
                            Text(
                                text = "RECEIVING STREAM…",
                                style = MaterialTheme.typography.labelMedium,
                                color = SwissSignalRed
                            )
                        }

                        val citations = remember(message.citationsJson) {
                            WebSearchClient.citationsFromJson(message.citationsJson)
                        }
                        if (citations.isNotEmpty()) {
                            CitationPillsRow(
                                citations = citations,
                                onOpenUrl = onOpenCitationUrl
                            )
                        }

                        if (!isStreamingThis && message.content.isNotBlank()) {
                            SwissRuleDivider(symbol = "*", lineColor = SwissConcrete)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (showDetails) {
                                        val promptT = message.promptTokens ?: 0
                                        val compT = message.completionTokens ?: 0
                                        val costStr = if (message.estimatedCostUsd > 0.0) {
                                            " · ${CostEstimator.formatCost(message.estimatedCostUsd, displayCurrency)}"
                                        } else {
                                            ""
                                        }
                                        Text(
                                            text = if (promptT > 0 || compT > 0) {
                                                "IN:$promptT / OUT:$compT$costStr"
                                            } else {
                                                "OUTPUT // ARCHIVED"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (message.estimatedCostUsd > 0.0) SwissInk else SwissMuted
                                        )
                                    }
                                    BranchSwitcherBar(node = node, onSwitchBranch = onSwitchBranch)
                                }

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (canModify) {
                                        SwissIconCell(
                                            icon = Icons.Outlined.FactCheck,
                                            contentDescription = "Fact-check claims",
                                            onClick = onVerifyClaims,
                                            enabled = !isFactChecking,
                                            accent = factClaims.isNotEmpty(),
                                            cellSize = 36.dp,
                                            modifier = Modifier.testTag("fact_check_${message.id}")
                                        )
                                        SwissIconCell(
                                            icon = Icons.Outlined.Book,
                                            contentDescription = "Open in Reading Room",
                                            onClick = onOpenReadingRoom,
                                            cellSize = 36.dp,
                                            modifier = Modifier.testTag("reading_room_${message.id}")
                                        )
                                        SwissIconCell(
                                            icon = Icons.Outlined.EditNote,
                                            contentDescription = "Open in Interactive Canvas",
                                            onClick = onOpenCanvas,
                                            cellSize = 36.dp,
                                            modifier = Modifier.testTag("open_canvas_${message.id}")
                                        )
                                        SwissIconCell(
                                            icon = Icons.Outlined.School,
                                            contentDescription = "Generate Study Flashcards & Quiz",
                                            onClick = onGenerateStudyPack,
                                            enabled = !isGeneratingStudy,
                                            cellSize = 36.dp,
                                            modifier = Modifier.testTag("study_quiz_${message.id}")
                                        )
                                        SwissIconCell(
                                            icon = Icons.Outlined.Hub,
                                            contentDescription = "Visualize as Mind Map",
                                            onClick = onGenerateMindMap,
                                            enabled = !isGeneratingMindMap,
                                            cellSize = 36.dp,
                                            modifier = Modifier.testTag("mind_map_${message.id}")
                                        )
                                    }
                                    if (!message.rawRequestJson.isNullOrBlank() || !message.rawResponseJson.isNullOrBlank()) {
                                        SwissIconCell(
                                            icon = Icons.Outlined.DataObject,
                                            contentDescription = "Inspect Request & Response JSON",
                                            onClick = onInspectJson,
                                            cellSize = 36.dp,
                                            modifier = Modifier.testTag("inspect_json_${message.id}")
                                        )
                                    }
                                    SwissIconCell(
                                        icon = if (isSpeakingThis) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                        contentDescription = if (isSpeakingThis) "Stop reading aloud" else "Read aloud",
                                        onClick = onToggleSpeak,
                                        accent = isSpeakingThis,
                                        cellSize = 36.dp,
                                        modifier = Modifier.testTag("tts_speak_${message.id}")
                                    )
                                    SwissIconCell(
                                        icon = Icons.Outlined.QrCode2,
                                        contentDescription = "Beam via QR (Single or Animated Multi-QR)",
                                        onClick = onBeamQr,
                                        cellSize = 36.dp,
                                        modifier = Modifier.testTag("beam_qr_${message.id}")
                                    )
                                    if (canCheckpoint) {
                                        SwissIconCell(
                                            icon = Icons.Outlined.BookmarkAdd,
                                            contentDescription = "Summarize up to here (TL;DR Checkpoint)",
                                            onClick = onSummarizeCheckpoint,
                                            enabled = !isSummarizingCheckpoint,
                                            accent = isSummarizingCheckpoint,
                                            cellSize = 36.dp,
                                            modifier = Modifier.testTag("checkpoint_summarize_${message.id}")
                                        )
                                    }
                                    SwissIconCell(
                                        icon = Icons.Outlined.IosShare,
                                        contentDescription = "Export Studio (PDF, DOCX, Notion MD, Share)",
                                        onClick = onExportMessage,
                                        cellSize = 36.dp,
                                        modifier = Modifier.testTag("export_studio_${message.id}")
                                    )
                                    SwissIconCell(
                                        icon = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy response",
                                        onClick = onCopy,
                                        cellSize = 36.dp
                                    )
                                    if (canModify) {
                                        SwissIconCell(
                                            icon = Icons.Outlined.Edit,
                                            contentDescription = "Edit and branch AI response",
                                            onClick = onEditAssistantMessage,
                                            cellSize = 36.dp,
                                            modifier = Modifier.testTag("edit_fork_ai_${message.id}")
                                        )
                                        SwissIconCell(
                                            icon = Icons.Outlined.Refresh,
                                            contentDescription = "Regenerate as new branch",
                                            onClick = onRegenerateBranch,
                                            cellSize = 36.dp,
                                            modifier = Modifier.testTag("regenerate_button")
                                        )
                                    }
                                }
                            }

                            // Inline Fact-Check Verification Results
                            if (factClaims.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SwissCanvas, RectangleShape)
                                        .border(1.dp, SwissInk, RectangleShape)
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "FACT-CHECK // ${factClaims.size} VERIFIED CLAIMS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissInk
                                    )
                                    factClaims.forEach { claim ->
                                        val isOk = claim.status.equals("VERIFIED", ignoreCase = true)
                                        val isConflict = claim.status.equals("CONFLICT", ignoreCase = true)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "[${claim.status.uppercase()}]",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when {
                                                    isOk -> SwissVerifiedInk
                                                    isConflict -> SwissSignalRed
                                                    else -> SwissMuted
                                                }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = claim.statement,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SwissInk
                                                )
                                                if (claim.note.isNotBlank()) {
                                                    Text(
                                                        text = claim.note,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SwissMuted
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatEmptyState(
    hasConfiguredModels: Boolean,
    onOpenSettings: () -> Unit,
    onSelectStarter: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .swissGraphPaper()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = SwissPaper,
            shape = RectangleShape,
            border = BorderStroke(1.dp, SwissInk),
            shadowElevation = 0.dp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .swissRegistrationMarks()
        ) {
            Column {
                SwissPanelHeader(
                    leftLabel = "01 // SYSTEM / ALLCHAT WORKSPACE",
                    rightLabel = if (hasConfiguredModels) "STATUS: ACTIVE" else "STATUS: STANDBY",
                    rightAccent = !hasConfiguredModels
                )

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SwissBadge(
                            text = "BYOK // LOCAL",
                            containerColor = SwissSignalRed,
                            contentColor = SwissWhite,
                            borderColor = SwissSignalRed
                        )
                        SwissBadge(
                            text = "116 PROVIDERS",
                            containerColor = SwissConcrete,
                            contentColor = SwissInk
                        )
                    }

                    Text(
                        text = "allchat // universal terminal",
                        style = MaterialTheme.typography.displaySmall,
                        color = SwissInk
                    )

                    Text(
                        text = if (hasConfiguredModels) {
                            "Select any active model in the segmented header bar to begin streaming. API keys remain encrypted strictly on this device."
                        } else {
                            "Connect Google Gemini, OpenAI, Anthropic, OpenRouter, Groq, Ollama, or any of 116 OpenCode providers in Settings."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SwissMuted
                    )

                    SwissRuleDivider(symbol = "/")

                    if (!hasConfiguredModels) {
                        SwissButton(
                            text = "Add Provider & API Key",
                            leadingIcon = Icons.Outlined.Settings,
                            variant = SwissButtonVariant.PRIMARY,
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_configure_providers_button")
                        )
                    } else {
                        Text(
                            text = "QUICK PROMPTS // SELECT TO LOAD",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissMuted
                        )
                        val starters = listOf(
                            "01" to "Compare Kotlin Coroutines vs RxJava cleanly",
                            "02" to "Draft a concise architecture review checklist"
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SwissInk, RectangleShape)
                        ) {
                            starters.forEachIndexed { idx, (num, prompt) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SwissSubtleFill)
                                        .clickable { onSelectStarter(prompt) }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$num /",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SwissSignalRed
                                    )
                                    Text(
                                        text = prompt,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SwissInk,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (idx < starters.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(SwissInk)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
