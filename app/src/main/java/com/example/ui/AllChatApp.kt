package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.AllChatRepository
import com.example.domain.model.IncomingAppAction
import com.example.domain.model.IncomingSharePayload
import com.example.ui.components.PinLockOverlay
import com.example.ui.components.SwissButton
import com.example.ui.components.SwissButtonVariant
import com.example.ui.screens.ArenaScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ImageScreen
import com.example.ui.screens.KnowledgeScreen
import com.example.ui.screens.PipelineScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SummarizeScreen
import com.example.ui.screens.VideoScreen
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissThemePreset
import com.example.ui.theme.SwissThemeState
import com.example.ui.viewmodel.AllChatViewModelFactory
import com.example.ui.viewmodel.ArenaViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ImageViewModel
import com.example.ui.viewmodel.KnowledgeViewModel
import com.example.ui.viewmodel.PipelineViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.SummarizeInputMode
import com.example.ui.viewmodel.SummarizeViewModel
import com.example.ui.viewmodel.VideoViewModel
import kotlinx.serialization.Serializable

@Serializable data object ChatRoute
@Serializable data object ArenaRoute
@Serializable data object PipelineRoute
@Serializable data object KnowledgeRoute
@Serializable data object SummarizeRoute
@Serializable data object ImageRoute
@Serializable data object VideoRoute
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
    val isLocked by repository.isLockedFlow.collectAsStateWithLifecycle()
    val themeId by repository.themePresetIdFlow.collectAsStateWithLifecycle(initialValue = SwissThemePreset.SWISS_SIGNAL.id)
    var pendingDocShareUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(themeId) {
        SwissThemeState.activePreset = SwissThemePreset.fromId(themeId)
    }

    LaunchedEffect(Unit) {
        repository.initializeLockState()
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
            is IncomingSharePayload.ImageUri -> {
                chatViewModel.attachSharedImageAndSelectVision(Uri.parse(incomingSharePayload.uriString))
                navController.popBackStack(ChatRoute, inclusive = false)
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

    Scaffold(
        containerColor = SwissCanvas,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ChatRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    onOpenSettings = { navigateTo(SettingsRoute) }
                )
            }
            composable<VideoRoute> {
                VideoScreen(
                    viewModel = videoViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSettings = { navigateTo(SettingsRoute) }
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
