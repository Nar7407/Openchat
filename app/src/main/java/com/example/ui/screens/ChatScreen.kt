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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.RectangleShape
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
import com.example.domain.model.ModelCapability
import com.example.ui.components.AgentsMdInitDialog
import com.example.ui.components.ChatMinimapScrubber
import com.example.ui.components.CitationPillsRow
import com.example.ui.components.CommandHelpSheet
import com.example.ui.components.CustomSlashCommandsSheet
import com.example.ui.components.DeviceActionConfirmCard
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
import com.example.ui.components.swissGraphPaper
import com.example.ui.components.swissRegistrationMarks
import com.example.ui.components.swissTextFieldColors
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite
import com.example.ui.viewmodel.ChatUiEvent
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.DeviceActionEngine
import com.example.util.ReadAloudController
import com.example.util.VoiceInputController
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
    var editorInitialText by remember { mutableStateOf<String?>(null) }
    var shareDialogPayload by remember { mutableStateOf<Pair<String, String>?>(null) }
    var exportStudioPayload by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingExportMarkdown by remember { mutableStateOf<String?>(null) }
    var snipSourceUri by remember { mutableStateOf<Uri?>(null) }
    val liveCamController = remember { LiveCamController() }

    // Smart "Paste & Ask" Clipboard Detector when returning to AllChat
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                viewModel.inspectClipboardOnResume(clipText)
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
            onSpeakingIdChanged = { id -> speakingMessageId = id }
        )
    }

    DisposableEffect(voiceController, ttsController) {
        onDispose {
            voiceController.release()
            ttsController.release()
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
                ChatUiEvent.ExitApp -> {
                    (context as? Activity)?.finishAffinity()
                }
            }
        }
    }

    var autoScrollEnabled by remember(state.activeConversation?.id) { mutableStateOf(true) }
    val showJumpToLatest by remember {
        derivedStateOf {
            state.branchNodes.isNotEmpty() && listState.canScrollForward
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
    val lastNode = state.branchNodes.lastOrNull()
    LaunchedEffect(lastNode?.message?.id) {
        if (lastNode?.message?.role == "USER") {
            autoScrollEnabled = true
        }
    }

    // Smart auto-scroll when a new turn arrives or while streaming (only if not paused by user)
    LaunchedEffect(state.branchNodes.size, lastNode?.message?.id, lastNode?.message?.content?.length) {
        if (state.branchNodes.isNotEmpty() && autoScrollEnabled) {
            if (state.isStreaming) {
                listState.scrollToItem(state.branchNodes.lastIndex)
            } else {
                listState.animateScrollToItem(state.branchNodes.lastIndex)
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
                            placeholder = { Text("FILTER THREADS…", style = MaterialTheme.typography.labelMedium) },
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
            val isCompactViewport = maxHeight < 520.dp || maxWidth < 360.dp
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
                            modifier = Modifier.weight(1f)
                        )

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
                                .clickable { showPersonaQuickBar = !showPersonaQuickBar }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val persona = state.activePersona
                            Text(
                                text = if (persona != null) {
                                    String.format(
                                        Locale.US,
                                        "PERSONA // @%s · T:%.1f · MAX:%d",
                                        persona.handle.uppercase(),
                                        persona.temperature,
                                        persona.maxTokens
                                    )
                                } else {
                                    "CMD // / COMMANDS · @ PERSONAS · SWIPE MSG TO QUOTE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (persona != null) SwissSignalRed else SwissMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                    text = "[ /HELP ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissInk,
                                    modifier = Modifier
                                        .clickable { showHelpSheet = true }
                                        .testTag("open_help_header_button")
                                )
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

                    if (state.isStreaming || state.isGeneratingStudy || state.isGeneratingMindMap) {
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

            // Status Notice Banner (Slash command confirmations)
            AnimatedVisibility(visible = state.statusNotice != null) {
                state.statusNotice?.let { notice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .background(SwissVerifiedTint, RectangleShape)
                            .border(1.dp, SwissVerifiedInk, RectangleShape)
                            .clickable { viewModel.clearNotices() }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissVerifiedInk,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss notice",
                            tint = SwissVerifiedInk,
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

            // Messages Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (state.branchNodes.isEmpty()) {
                    ChatEmptyState(
                        hasConfiguredModels = state.availableModels.isNotEmpty(),
                        onOpenSettings = onOpenSettings,
                        onSelectStarter = { starter ->
                            inputText = starter
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = if (state.branchNodes.count { it.message.role == "USER" } >= 3) 26.dp else 12.dp,
                            top = 14.dp,
                            bottom = 14.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 780.dp)
                    ) {
                        itemsIndexed(
                            items = state.branchNodes,
                            key = { _, node -> node.message.id }
                        ) { index, node ->
                            val msg = node.message
                            val isLastAssistant = index == state.branchNodes.lastIndex && msg.role == "ASSISTANT"
                            ChatMessageCell(
                                index = index + 1,
                                node = node,
                                displayCurrency = state.displayCurrency,
                                showDetails = state.showDetails,
                                showThinking = state.showThinking,
                                isStreamingThis = isLastAssistant && state.isStreaming,
                                isSpeakingThis = speakingMessageId == msg.id,
                                canModify = !state.isStreaming,
                                isGeneratingStudy = state.isGeneratingStudy,
                                isGeneratingMindMap = state.isGeneratingMindMap,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(msg.content))
                                    viewModel.showNotice("COPIED // Message copied to clipboard.")
                                },
                                onQuoteSwipe = {
                                    viewModel.setQuotedMessage(msg)
                                    viewModel.showNotice("QUOTE // Replying to message #${index + 1}.")
                                },
                                onToggleSpeak = { ttsController.toggleSpeak(msg.id, msg.content) },
                                onEditUserMessage = { editingForkMessage = msg },
                                onRegenerateBranch = { viewModel.regenerateAsBranch(msg) },
                                onGenerateStudyPack = { viewModel.generateStudyPackForMessage(msg) },
                                onGenerateMindMap = { viewModel.generateMindMapForMessage(msg) },
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
                        branchNodes = state.branchNodes,
                        currentVisibleIndex = listState.firstVisibleItemIndex,
                        onJumpToIndex = { targetIdx ->
                            autoScrollEnabled = targetIdx >= state.branchNodes.lastIndex
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
                                        if (state.branchNodes.isNotEmpty()) {
                                            listState.animateScrollToItem(state.branchNodes.lastIndex)
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

                // 1-Tap Device Action Confirmation Card (Calls, WhatsApp, YouTube, Alarms, Timers, App Launch)
                AnimatedVisibility(visible = state.pendingDeviceAction != null) {
                    state.pendingDeviceAction?.let { devAction ->
                        DeviceActionConfirmCard(
                            action = devAction,
                            quickContacts = state.quickContacts,
                            onUpdateAction = viewModel::setPendingDeviceAction,
                            onSaveQuickContact = viewModel::saveQuickContact,
                            onExecute = {
                                DeviceActionEngine.execute(context, devAction)
                                    .onSuccess { msg ->
                                        viewModel.dismissDeviceAction()
                                        viewModel.showNotice("ACTION // $msg")
                                    }
                                    .onFailure { err ->
                                        viewModel.showToast(err.message ?: "Unable to execute device action.")
                                    }
                            },
                            onDismiss = viewModel::dismissDeviceAction,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
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
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                if (isDictating) {
                                    "LISTENING // SPEAK NOW…"
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
                                if (state.liveCamEnabled) {
                                    val frame = liveCamController.grabCurrentFrameBitmap()
                                    viewModel.sendMessageWithOptionalLiveFrame(prompt, frame)
                                } else {
                                    viewModel.sendMessage(prompt)
                                }
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
                            index = "18",
                            title = "Slash Commands Reference (/help)",
                            subtitle = "All built-in commands: /remember, /mcp, /init, /compact, /undo, /export",
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
                viewModel.editMessageAndFork(targetMsg, newText)
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
            onToggleAutoExtract = viewModel::setAutoExtractMemories,
            onAddMemory = viewModel::addMemory,
            onUpdateMemory = viewModel::updateMemory,
            onToggleMemory = viewModel::toggleMemory,
            onDeleteMemory = viewModel::deleteMemory,
            onDismiss = { showMemoryBank = false }
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
    onCopy: () -> Unit,
    onQuoteSwipe: () -> Unit,
    onToggleSpeak: () -> Unit,
    onEditUserMessage: () -> Unit,
    onRegenerateBranch: () -> Unit,
    onGenerateStudyPack: () -> Unit,
    onGenerateMindMap: () -> Unit,
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

                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    if (canModify) {
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
                                            icon = Icons.Outlined.Refresh,
                                            contentDescription = "Regenerate as new branch",
                                            onClick = onRegenerateBranch,
                                            cellSize = 36.dp,
                                            modifier = Modifier.testTag("regenerate_button")
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
