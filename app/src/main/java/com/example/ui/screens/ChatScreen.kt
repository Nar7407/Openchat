package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.domain.model.ModelCapability
import com.example.ui.components.ErrorBanner
import com.example.ui.components.MarkdownText
import com.example.ui.components.ModelPickerButton
import com.example.ui.components.SwissBadge
import com.example.ui.components.SwissButton
import com.example.ui.components.SwissButtonVariant
import com.example.ui.components.SwissIconCell
import com.example.ui.components.SwissPanel
import com.example.ui.components.SwissPanelHeader
import com.example.ui.components.SwissRuleDivider
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
import com.example.ui.theme.SwissWhite
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSummarize: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showPlusMenu by remember { mutableStateOf(false) }
    var renamingConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

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

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
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
                                                    Text(
                                                        text = "TOK // $totalTokens",
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
                        cellSize = 52.dp,
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
                        modifier = Modifier.weight(1f)
                    )

                    val convTokens = (state.activeConversation?.totalPromptTokens ?: 0) +
                        (state.activeConversation?.totalCompletionTokens ?: 0)
                    if (convTokens > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(SwissInk)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(SwissConcrete)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${convTokens}T",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissInk
                            )
                        }
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
                        onClick = viewModel::createNewChat,
                        bordered = false,
                        cellSize = 52.dp,
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("top_new_chat_button")
                    )
                }

                if (state.isStreaming) {
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

            // Error Banner
            AnimatedVisibility(visible = state.errorToast != null) {
                state.errorToast?.let { toast ->
                    ErrorBanner(
                        message = toast,
                        onDismiss = viewModel::clearErrorToast,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Messages Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (state.messages.isEmpty()) {
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 780.dp)
                    ) {
                        itemsIndexed(
                            items = state.messages,
                            key = { _, msg -> msg.id }
                        ) { index, msg ->
                            val isLastAssistant = index == state.messages.lastIndex && msg.role == "ASSISTANT"
                            ChatMessageCell(
                                index = index + 1,
                                message = msg,
                                isStreamingThis = isLastAssistant && state.isStreaming,
                                showRegenerate = isLastAssistant && !state.isStreaming,
                                onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                                onRegenerate = viewModel::regenerateLastResponse,
                                onOpenProxySettings = onOpenSettings
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left '+' button opening Summarize / Image / Video / Attachments sheet
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
                                if (state.selectedModel != null) {
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
                                val prompt = inputText
                                inputText = ""
                                viewModel.sendMessage(prompt)
                            },
                            enabled = inputText.isNotBlank() || state.pendingAttachmentUri != null,
                            accent = true,
                            cellSize = 48.dp,
                            modifier = Modifier.testTag("send_chat_button")
                        )
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
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SwissInk, RectangleShape)
                    ) {
                        StudioToolRow(
                            index = "01",
                            title = "Summarize",
                            subtitle = "Text, PDF/Markdown files, or web URLs",
                            icon = Icons.Outlined.AutoAwesome,
                            onClick = {
                                showPlusMenu = false
                                onOpenSummarize()
                            },
                            testTag = "plus_menu_summarize"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(SwissInk)
                        )
                        StudioToolRow(
                            index = "02",
                            title = "Image Studio",
                            subtitle = "Generate archival plates & save to gallery",
                            icon = Icons.Outlined.Image,
                            onClick = {
                                showPlusMenu = false
                                onOpenImage()
                            },
                            testTag = "plus_menu_image"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(SwissInk)
                        )
                        StudioToolRow(
                            index = "03",
                            title = "Video Studio",
                            subtitle = "Async AI motion jobs & inline playback",
                            icon = Icons.Outlined.Movie,
                            onClick = {
                                showPlusMenu = false
                                onOpenVideo()
                            },
                            testTag = "plus_menu_video"
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
private fun ChatMessageCell(
    index: Int,
    message: MessageEntity,
    isStreamingThis: Boolean,
    showRegenerate: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenProxySettings: () -> Unit
) {
    val isUser = message.role == "USER"
    val paddedIndex = index.toString().padStart(2, '0')

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (message.isError) {
            ErrorBanner(
                message = message.content,
                suggestProxySetting = message.suggestProxySetting,
                onOpenProxySettings = onOpenProxySettings,
                modifier = Modifier.fillMaxWidth()
            )
            if (showRegenerate) {
                Spacer(Modifier.height(6.dp))
                SwissButton(
                    text = "Retry Request",
                    leadingIcon = Icons.Outlined.Refresh,
                    variant = SwissButtonVariant.OUTLINE,
                    minHeight = 40.dp,
                    onClick = onRegenerate,
                    modifier = Modifier.testTag("regenerate_button")
                )
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
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(SwissSignalRed)
                        )
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
                            MarkdownText(markdown = message.content)
                        } else if (isStreamingThis) {
                            Text(
                                text = "RECEIVING STREAM…",
                                style = MaterialTheme.typography.labelMedium,
                                color = SwissSignalRed
                            )
                        }

                        if (!isStreamingThis && message.content.isNotBlank()) {
                            SwissRuleDivider(symbol = "*", lineColor = SwissConcrete)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val promptT = message.promptTokens ?: 0
                                val compT = message.completionTokens ?: 0
                                if (promptT > 0 || compT > 0) {
                                    Text(
                                        text = "IN:$promptT / OUT:$compT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissMuted
                                    )
                                } else {
                                    Text(
                                        text = "OUTPUT // ARCHIVED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissMuted
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SwissIconCell(
                                        icon = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy response",
                                        onClick = onCopy,
                                        cellSize = 36.dp
                                    )
                                    if (showRegenerate) {
                                        SwissIconCell(
                                            icon = Icons.Outlined.Refresh,
                                            contentDescription = "Regenerate response",
                                            onClick = onRegenerate,
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
