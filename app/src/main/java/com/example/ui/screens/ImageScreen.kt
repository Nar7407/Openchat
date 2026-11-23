package com.example.ui.screens

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.GeneratedMediaEntity
import com.example.domain.model.ModelCapability
import com.example.ui.components.ErrorBanner
import com.example.ui.components.InpaintingMaskCanvas
import com.example.ui.components.InpaintingMaskRenderer
import com.example.ui.components.MaskStroke
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
import com.example.ui.viewmodel.ImageStudioMode
import com.example.ui.viewmodel.ImageViewModel
import com.example.util.MediaStoreSaver
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ImageScreen(
    viewModel: ImageViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onGenerateSvg: (suspend (String) -> Result<String>)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var maskStrokes by remember { mutableStateOf<List<MaskStroke>>(emptyList()) }
    var generatedSvgCode by remember { mutableStateOf<String?>(null) }
    var isVectorizingSvg by remember { mutableStateOf(false) }

    val editPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            maskStrokes = emptyList()
            viewModel.setEditSourceUri(uri.toString())
        }
    }

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
                breadcrumb = "03 // STUDIO / ARCHIVAL PLATES",
                title = "image studio",
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                leadingContentDescription = "Back to chat",
                onLeadingClick = onNavigateBack,
                leadingTestTag = "image_back_button",
                trailingContent = {
                    Box(
                        modifier = Modifier
                            .background(SwissConcrete)
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${state.images.size} PLATES",
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
                        requiredCapability = ModelCapability.IMAGE_GENERATION,
                        onSelectModel = viewModel::selectModel,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Mode Switcher: Text-to-Image vs Inpaint & Edit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ImageStudioMode.entries.forEach { mode ->
                            SwissToggleCell(
                                label = mode.label,
                                selected = state.studioMode == mode,
                                onClick = { viewModel.setStudioMode(mode) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("image_mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    // Prompt + Ratio / Inpaint Canvas Bento Module
                    SwissPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = if (state.studioMode == ImageStudioMode.INPAINT) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                        ) {
                            SwissPanelHeader(
                                leftLabel = if (state.studioMode == ImageStudioMode.INPAINT) "01 // INPAINTING & MASK BRUSH" else "01 // GENERATION PROMPT",
                                rightLabel = if (state.studioMode == ImageStudioMode.INPAINT) "${maskStrokes.size} STROKES" else "RATIO ${state.aspectRatio}"
                            )
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (state.studioMode == ImageStudioMode.INPAINT) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Paint over an area to edit via OpenAI /images/edits or Gemini Image Edit.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SwissMuted,
                                            modifier = Modifier.weight(1f)
                                        )
                                        SwissButton(
                                            text = if (state.editSourceUri != null) "Change Photo" else "Pick Photo",
                                            leadingIcon = Icons.Outlined.UploadFile,
                                            variant = SwissButtonVariant.INK,
                                            minHeight = 38.dp,
                                            onClick = {
                                                editPhotoPicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            modifier = Modifier.testTag("inpaint_pick_photo")
                                        )
                                    }

                                    state.editSourceUri?.let { uriStr ->
                                        InpaintingMaskCanvas(
                                            imageUri = Uri.parse(uriStr),
                                            strokes = maskStrokes,
                                            onStrokesChanged = { maskStrokes = it }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = state.prompt,
                                    onValueChange = viewModel::updatePrompt,
                                    placeholder = {
                                        Text(
                                            if (state.studioMode == ImageStudioMode.INPAINT) {
                                                "Describe what to replace or add in the red masked region…"
                                            } else {
                                                "Describe the image plate to synthesize…"
                                            }
                                        )
                                    },
                                    minLines = 2,
                                    maxLines = 4,
                                    shape = RectangleShape,
                                    colors = swissTextFieldColors(containerColor = SwissCanvas),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("image_prompt_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (state.studioMode == ImageStudioMode.GENERATE) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf("1:1", "16:9", "9:16").forEach { ratio ->
                                                SwissToggleCell(
                                                    label = ratio,
                                                    selected = state.aspectRatio == ratio,
                                                    onClick = { viewModel.setAspectRatio(ratio) }
                                                )
                                            }
                                        }
                                        SwissButton(
                                            text = if (state.isGenerating) "Rendering…" else "Generate",
                                            leadingIcon = if (state.isGenerating) null else Icons.Outlined.Image,
                                            variant = SwissButtonVariant.PRIMARY,
                                            enabled = !state.isGenerating && state.prompt.isNotBlank() && state.selectedModel != null,
                                            minHeight = 42.dp,
                                            onClick = viewModel::generateImage,
                                            modifier = Modifier.testTag("generate_image_button")
                                        )
                                    } else {
                                        Text(
                                            text = if (state.editSourceUri == null) "Select a source photo first" else "Ready to inpaint",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SwissMuted
                                        )
                                        SwissButton(
                                            text = if (state.isGenerating) "Inpainting…" else "Apply Edit",
                                            leadingIcon = if (state.isGenerating) null else Icons.Outlined.Brush,
                                            variant = SwissButtonVariant.PRIMARY,
                                            enabled = !state.isGenerating && state.prompt.isNotBlank() && state.editSourceUri != null && state.selectedModel != null,
                                            minHeight = 42.dp,
                                            onClick = {
                                                val uriStr = state.editSourceUri ?: return@SwissButton
                                                scope.launch {
                                                    runCatching {
                                                        InpaintingMaskRenderer.renderPayloads(
                                                            context = context,
                                                            sourceUri = Uri.parse(uriStr),
                                                            strokes = maskStrokes
                                                        )
                                                    }.onSuccess { payloads ->
                                                        viewModel.editImageWithMask(
                                                            sourcePngBytes = payloads.sourcePngBytes,
                                                            openAiTransparentMaskPng = payloads.openAiTransparentMaskPng,
                                                            geminiRedOverlayMaskPng = payloads.geminiRedOverlayMaskPng
                                                        )
                                                    }.onFailure { err ->
                                                        viewModel.setStatusMessage(err.message ?: "Mask encoding failed")
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("apply_inpaint_button")
                                        )
                                    }
                                }

                                if (onGenerateSvg != null && state.studioMode == ImageStudioMode.GENERATE) {
                                    SwissButton(
                                        text = if (isVectorizingSvg) "Vectorizing SVG…" else "Convert Prompt to Clean <SVG> Logo",
                                        variant = SwissButtonVariant.OUTLINE,
                                        enabled = !isVectorizingSvg && state.prompt.isNotBlank(),
                                        minHeight = 42.dp,
                                        onClick = {
                                            scope.launch {
                                                isVectorizingSvg = true
                                                onGenerateSvg(state.prompt)
                                                    .onSuccess { svg -> generatedSvgCode = svg }
                                                    .onFailure { err -> viewModel.setStatusMessage(err.message ?: "SVG generation failed.") }
                                                isVectorizingSvg = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("generate_svg_button")
                                    )
                                }
                            }

                            if (state.isGenerating) {
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

                    if (state.images.isEmpty()) {
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
                                    text = "ARCHIVE // 00 PLATES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissSignalRed
                                )
                                Text(
                                    text = "Generated images appear in your local archival grid.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SwissMuted
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 156.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(state.images, key = { it.id }) { item ->
                                ImageArchivalPlate(
                                    item = item,
                                    onClick = { viewModel.openPreview(item) },
                                    onDelete = { viewModel.deleteImage(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.previewItem?.let { item ->
        Dialog(
            onDismissRequest = { viewModel.openPreview(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = SwissCanvas,
                shape = RectangleShape,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Segmented lightbox header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "PLATE #${item.id} // ARCHIVAL INSPECTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissMuted
                            )
                            Text(
                                text = item.modelLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = SwissInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        SwissIconCell(
                            icon = Icons.Filled.Close,
                            contentDescription = "Close preview",
                            onClick = { viewModel.openPreview(null) },
                            cellSize = 52.dp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SwissInk)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .swissGraphPaper()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!item.localFilePath.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(SwissPaper)
                                    .border(1.dp, SwissInk, RectangleShape)
                            ) {
                                AsyncImage(
                                    model = File(item.localFilePath),
                                    contentDescription = item.prompt,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Bottom monospace caption & action bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper)
                            .border(1.dp, SwissInk, RectangleShape)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "CAPTION // ${item.prompt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissInk,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!item.localFilePath.isNullOrBlank()) {
                                SwissButton(
                                    text = "Save",
                                    leadingIcon = Icons.Outlined.Download,
                                    variant = SwissButtonVariant.PRIMARY,
                                    onClick = {
                                        scope.launch {
                                            val res = MediaStoreSaver.saveImageToGallery(
                                                context,
                                                item.localFilePath,
                                                "AllChat_${item.id}"
                                            )
                                            viewModel.setStatusMessage(
                                                res.getOrElse { it.message ?: "Save failed" }
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SwissButton(
                                    text = "Share",
                                    leadingIcon = Icons.Outlined.Share,
                                    variant = SwissButtonVariant.OUTLINE,
                                    onClick = {
                                        MediaStoreSaver.shareFile(
                                            context,
                                            item.localFilePath,
                                            "image/png",
                                            "Share Image"
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            SwissIconCell(
                                icon = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete",
                                onClick = { viewModel.deleteImage(item) },
                                cellSize = 48.dp
                            )
                        }
                    }
                }
            }
        }
    }

    generatedSvgCode?.let { svg ->
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { generatedSvgCode = null },
            containerColor = SwissCanvas,
            shape = RectangleShape,
            title = { Text("VECTORIZER // CLEAN <SVG> OUTPUT", style = MaterialTheme.typography.titleMedium, color = SwissInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                loadDataWithBaseURL(null, "<html><body style='margin:0;display:flex;align-items:center;justify-content:center;background:#fff;'>$svg</body></html>", "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(180.dp).border(1.dp, SwissInk)
                    )
                    Text(svg.take(600), style = MaterialTheme.typography.bodySmall, color = SwissMuted, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {
                SwissButton(
                    text = "Copy SVG",
                    variant = SwissButtonVariant.PRIMARY,
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(svg))
                        viewModel.setStatusMessage("SVG // Copied vector markup to clipboard.")
                        generatedSvgCode = null
                    }
                )
            },
            dismissButton = {
                SwissButton(text = "Close", onClick = { generatedSvgCode = null })
            }
        )
    }
}

@Composable
private fun ImageArchivalPlate(
    item: GeneratedMediaEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = SwissPaper,
        shape = RectangleShape,
        border = BorderStroke(1.dp, SwissInk),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.status == "COMPLETED", onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(SwissSubtleFill)
                    .swissGraphPaper(),
                contentAlignment = Alignment.Center
            ) {
                when (item.status) {
                    "COMPLETED" -> {
                        AsyncImage(
                            model = item.localFilePath?.let { File(it) },
                            contentDescription = item.prompt,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    "FAILED" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = SwissSignalRed)
                            Text(
                                text = item.errorMessage ?: "FAILED",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissSignalRed,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    else -> {
                        CircularProgressIndicator(color = SwissSignalRed, strokeWidth = 2.dp)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SwissInk)
            )
            // Monospace archival caption bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissPaper)
                    .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PLT // #${item.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted
                    )
                    Text(
                        text = item.prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SwissIconCell(
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete image",
                    onClick = onDelete,
                    bordered = false,
                    cellSize = 32.dp
                )
            }
        }
    }
}
