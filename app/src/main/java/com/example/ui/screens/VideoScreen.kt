package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.local.GeneratedMediaEntity
import com.example.domain.model.ModelCapability
import com.example.ui.components.ErrorBanner
import com.example.ui.components.ModelPickerButton
import com.example.ui.components.SwissButton
import com.example.ui.components.SwissButtonVariant
import com.example.ui.components.SwissIconCell
import com.example.ui.components.SwissPanel
import com.example.ui.components.SwissPanelHeader
import com.example.ui.components.SwissToggleCell
import com.example.ui.components.SwissUtilityHeader
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
import com.example.ui.viewmodel.VideoViewModel
import com.example.util.MediaStoreSaver
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VideoScreen(
    viewModel: VideoViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        containerColor = SwissCanvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SwissUtilityHeader(
                breadcrumb = "04 // STUDIO / MOTION REELS",
                title = "video studio",
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                leadingContentDescription = "Back to chat",
                onLeadingClick = onNavigateBack,
                leadingTestTag = "video_back_button",
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .background(SwissConcrete)
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${state.videos.size} REELS",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissInk
                        )
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 780.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModelPickerButton(
                        selectedModel = state.selectedModel,
                        availableModels = state.availableModels,
                        requiredCapability = ModelCapability.VIDEO_GENERATION,
                        onSelectModel = viewModel::selectModel,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwissPanelHeader(
                                leftLabel = "01 // MOTION SPECIFICATION",
                                rightLabel = "FRAME ${state.aspectRatio}"
                            )
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = state.prompt,
                                    onValueChange = viewModel::updatePrompt,
                                    placeholder = { Text("Describe the motion and scene to synthesize…") },
                                    minLines = 2,
                                    maxLines = 4,
                                    shape = RectangleShape,
                                    colors = swissTextFieldColors(containerColor = SwissCanvas),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("video_prompt_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("16:9", "9:16").forEach { ratio ->
                                            SwissToggleCell(
                                                label = ratio,
                                                selected = state.aspectRatio == ratio,
                                                onClick = { viewModel.setAspectRatio(ratio) }
                                            )
                                        }
                                    }

                                    if (state.isPolling) {
                                        SwissButton(
                                            text = "Cancel",
                                            leadingIcon = Icons.Filled.Stop,
                                            variant = SwissButtonVariant.PRIMARY,
                                            minHeight = 42.dp,
                                            onClick = viewModel::cancelVideoPolling,
                                            modifier = Modifier.testTag("cancel_video_button")
                                        )
                                    } else {
                                        SwissButton(
                                            text = "Generate Video",
                                            leadingIcon = Icons.Outlined.Movie,
                                            variant = SwissButtonVariant.PRIMARY,
                                            enabled = state.prompt.isNotBlank() && state.selectedModel != null,
                                            minHeight = 42.dp,
                                            onClick = viewModel::generateVideo,
                                            modifier = Modifier.testTag("generate_video_button")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = state.isPolling) {
                        SwissPanel(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                SwissPanelHeader(
                                    leftLabel = "JOB // ASYNC RENDER POLLING",
                                    rightLabel = "ACTIVE",
                                    rightAccent = true
                                )
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = state.pollingStatus.ifBlank { "Polling async video job…" },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SwissInk
                                    )
                                    LinearProgressIndicator(
                                        color = SwissSignalRed,
                                        trackColor = SwissConcrete,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = state.errorMessage != null) {
                        state.errorMessage?.let { err ->
                            ErrorBanner(
                                message = err,
                                suggestProxySetting = state.suggestProxySetting,
                                onOpenProxySettings = onOpenSettings,
                                onDismiss = viewModel::clearError
                            )
                        }
                    }

                    if (state.videos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(SwissPaper)
                                .border(1.dp, SwissInk, RectangleShape)
                                .swissGraphPaper()
                                .swissRegistrationMarks(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Text(
                                    text = "ARCHIVE // 00 REELS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed
                                )
                                Text(
                                    text = "Generated video reels appear here with inline playback and export.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SwissMuted
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(state.videos, key = { it.id }) { item ->
                                VideoArchivalPlate(
                                    item = item,
                                    onSave = {
                                        item.localFilePath?.let { path ->
                                            scope.launch {
                                                val res = MediaStoreSaver.saveVideoToMovies(
                                                    context,
                                                    path,
                                                    "AllChat_${item.id}"
                                                )
                                                viewModel.setStatusMessage(
                                                    res.getOrElse { it.message ?: "Save failed" }
                                                )
                                            }
                                        }
                                    },
                                    onShare = {
                                        item.localFilePath?.let { path ->
                                            MediaStoreSaver.shareFile(
                                                context,
                                                path,
                                                "video/mp4",
                                                "Share Video"
                                            )
                                        }
                                    },
                                    onDelete = { viewModel.deleteVideo(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoArchivalPlate(
    item: GeneratedMediaEntity,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    SwissPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissSubtleFill)
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REEL #${item.id} // ${item.modelLabel.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissInk,
                    modifier = Modifier.weight(1f)
                )
                SwissIconCell(
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete video",
                    onClick = onDelete,
                    bordered = false,
                    cellSize = 32.dp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SwissInk)
            )

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = item.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwissInk
                )

                when (item.status) {
                    "COMPLETED" -> {
                        val path = item.localFilePath
                        if (!path.isNullOrBlank() && File(path).exists()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SwissInk, RectangleShape)
                            ) {
                                VideoPlayerBox(
                                    filePath = path,
                                    aspectRatio = if (item.aspectRatio == "9:16") 9f / 16f else 16f / 9f
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SwissButton(
                                    text = "Save Video",
                                    leadingIcon = Icons.Outlined.Download,
                                    variant = SwissButtonVariant.PRIMARY,
                                    minHeight = 42.dp,
                                    onClick = onSave,
                                    modifier = Modifier.weight(1f)
                                )
                                SwissButton(
                                    text = "Share",
                                    leadingIcon = Icons.Outlined.Share,
                                    variant = SwissButtonVariant.OUTLINE,
                                    minHeight = 42.dp,
                                    onClick = onShare,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    "FAILED" -> {
                        Text(
                            text = "ERR // ${item.errorMessage ?: "Video generation failed."}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed
                        )
                    }
                    else -> {
                        Text(
                            text = "STATUS // ${item.status}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerBox(
    filePath: String,
    aspectRatio: Float
) {
    val context = LocalContext.current
    val player = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
    )
}
