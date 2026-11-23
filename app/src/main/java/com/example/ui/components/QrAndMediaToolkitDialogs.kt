package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite
import com.example.util.ColorPaletteExtractor
import com.example.util.PaletteSwatch
import com.example.util.PrivacyRedactor
import com.example.util.QrCodeGenerator
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// 1. Multi-Device "Drop Zone" & Animated QR Link/File Beamer (Single QR + Looping Multi-Frame QR Stream)
@Composable
fun QrStudioDialog(
    initialPayload: String,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(initialPayload.ifBlank { "https://allchat.app" }) }
    var forceSingleFrame by remember { mutableStateOf(false) }
    var isPlayingStream by remember { mutableStateOf(true) }
    var fps by remember { mutableFloatStateOf(2.5f) }
    var currentFrameIdx by remember { mutableIntStateOf(0) }

    val chunks = remember(text, forceSingleFrame) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            emptyList()
        } else if (forceSingleFrame || clean.length <= 260) {
            listOf(clean.take(1200))
        } else {
            val rawParts = clean.chunked(220)
            val total = rawParts.size
            rawParts.mapIndexed { idx, part ->
                "[ALLCHAT-QR ${idx + 1}/$total]\n$part"
            }
        }
    }

    val isMultiFrame = chunks.size > 1

    LaunchedEffect(chunks.size) {
        currentFrameIdx = 0
    }

    LaunchedEffect(isMultiFrame, isPlayingStream, fps, chunks.size) {
        if (!isMultiFrame || !isPlayingStream) return@LaunchedEffect
        val delayMs = (1000f / fps.coerceIn(0.8f, 6f)).toLong().coerceAtLeast(160L)
        while (true) {
            delay(delayMs)
            currentFrameIdx = (currentFrameIdx + 1) % chunks.size
        }
    }

    val activePayload = chunks.getOrElse(currentFrameIdx) { text.trim() }
    val qrBitmap = remember(activePayload) {
        if (activePayload.isBlank()) null else runCatching { QrCodeGenerator.generateBitmap(activePayload, 640) }.getOrNull()
    }
    val parsed = remember(text) {
        if (text.isBlank()) null else QrCodeGenerator.parsePayload(text.trim())
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("qr_studio_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissInk).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isMultiFrame) "DROP ZONE // ANIMATED QR BEAMER" else "QR DROP ZONE // AIR-GAP BEAMER",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                            color = SwissWhite
                        )
                        Text(
                            text = if (isMultiFrame) {
                                "Long text auto-split into ${chunks.size} looping QR frames · Zero internet needed"
                            } else {
                                "Instant air-gap transfer to any phone or lab PC"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissConcrete
                        )
                    }
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close QR studio", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 590.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Code snippet, API response, URL, UPI, Wi-Fi, or Text (${text.length} chars)") },
                        singleLine = false,
                        maxLines = 4,
                        colors = swissTextFieldColors(),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth().testTag("qr_payload_input")
                    )

                    if (text.trim().length > 260) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SwissToggleCell(
                                label = "ANIMATED STREAM (${chunks.size} FRAMES)",
                                selected = !forceSingleFrame,
                                useSignalRedWhenSelected = true,
                                onClick = { forceSingleFrame = false },
                                modifier = Modifier.weight(1f)
                            )
                            SwissToggleCell(
                                label = "SINGLE QR",
                                selected = forceSingleFrame,
                                onClick = { forceSingleFrame = true },
                                modifier = Modifier.weight(0.65f)
                            )
                        }
                    }

                    if (parsed != null && !isMultiFrame) {
                        Column(
                            modifier = Modifier.fillMaxWidth().background(SwissPaper).border(1.dp, SwissInk).padding(10.dp)
                        ) {
                            Text(parsed.badge, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissSignalRed)
                            Text(parsed.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = SwissInk)
                            Text(parsed.subtitle, style = MaterialTheme.typography.bodySmall, color = SwissMuted)
                        }
                    }

                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(252.dp)
                                .background(Color.White)
                                .border(2.dp, if (isMultiFrame) SwissSignalRed else SwissInk)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Generated scannable QR code",
                                modifier = Modifier.size(232.dp)
                            )
                        }
                    }

                    if (isMultiFrame) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissPaper)
                                .border(1.dp, SwissInk, RectangleShape)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BEAM STREAM // FRAME ${currentFrameIdx + 1} / ${chunks.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = SwissSignalRed
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f FPS", fps),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissMuted
                                )
                            }
                            LinearProgressIndicator(
                                progress = { ((currentFrameIdx + 1).toFloat() / chunks.size.toFloat()).coerceIn(0f, 1f) },
                                color = SwissSignalRed,
                                trackColor = SwissConcrete,
                                modifier = Modifier.fillMaxWidth().height(5.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SwissIconCell(
                                    icon = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous QR frame",
                                    onClick = {
                                        isPlayingStream = false
                                        currentFrameIdx = if (currentFrameIdx == 0) chunks.lastIndex else currentFrameIdx - 1
                                    },
                                    cellSize = 44.dp
                                )
                                SwissButton(
                                    text = if (isPlayingStream) "PAUSE" else "PLAY STREAM",
                                    leadingIcon = if (isPlayingStream) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    variant = if (isPlayingStream) SwissButtonVariant.INK else SwissButtonVariant.PRIMARY,
                                    onClick = { isPlayingStream = !isPlayingStream },
                                    modifier = Modifier.weight(1f).testTag("qr_stream_play_pause_btn")
                                )
                                SwissIconCell(
                                    icon = Icons.Default.SkipNext,
                                    contentDescription = "Next QR frame",
                                    onClick = {
                                        isPlayingStream = false
                                        currentFrameIdx = (currentFrameIdx + 1) % chunks.size
                                    },
                                    cellSize = 44.dp
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1.5f to "1.5 FPS", 2.5f to "2.5 FPS", 4.5f to "4.5 FPS").forEach { (speedVal, lbl) ->
                                    SwissToggleCell(
                                        label = lbl,
                                        selected = kotlin.math.abs(fps - speedVal) < 0.2f,
                                        onClick = { fps = speedVal },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissButton(
                            text = "COPY TEXT",
                            onClick = {
                                clipboard.setText(AnnotatedString(text))
                                onNotify("QR // Payload copied.")
                            },
                            leadingIcon = Icons.Default.ContentCopy,
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "SHARE PNG",
                            onClick = {
                                if (qrBitmap != null) {
                                    runCatching {
                                        val file = QrCodeGenerator.saveQrPngToCache(context, qrBitmap)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(share, "Share QR Code"))
                                    }.onFailure { onNotify("Could not share QR PNG: ${it.message}") }
                                }
                            },
                            leadingIcon = Icons.Default.Share,
                            variant = SwissButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f).testTag("qr_share_btn")
                        )
                    }
                }
            }
        }
    }
}

// 2. Local Video Frame Extractor (MediaMetadataRetriever)
@Composable
fun VideoFrameExtractorDialog(
    onDismiss: () -> Unit,
    onSendFrameToAi: (Bitmap, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionFraction by remember { mutableFloatStateOf(0f) }
    var extractedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusText by remember { mutableStateOf("Pick a local video to scrub and capture an exact frame (0 API cost until sent).") }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            videoUri = uri
            positionFraction = 0f
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(context, uri)
                    val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val frame = mmr.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    mmr.release()
                    dur to frame
                }.onSuccess { (dur, bmp) ->
                    withContext(Dispatchers.Main) {
                        durationMs = dur
                        extractedBitmap = bmp
                        statusText = "Loaded video (${dur / 1000}s). Drag slider to pick frame."
                    }
                }.onFailure { err ->
                    withContext(Dispatchers.Main) {
                        statusText = "Could not open video: ${err.message}"
                    }
                }
            }
        }
    }

    LaunchedEffect(videoUri, positionFraction) {
        val uri = videoUri ?: return@LaunchedEffect
        if (durationMs <= 0L) return@LaunchedEffect
        val targetUs = (durationMs * positionFraction * 1000f).toLong()
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, uri)
                val f = mmr.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                mmr.release()
                f
            }.getOrNull()
        }
        if (bmp != null) extractedBitmap = bmp
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("video_frame_extractor_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissInk).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "VIDEO SCRUBBER // KEYFRAME CAPTURE",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close video scrubber", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = SwissMuted)

                    SwissButton(
                        text = if (videoUri == null) "PICK VIDEO" else "CHANGE VIDEO",
                        onClick = {
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        leadingIcon = Icons.Default.VideoFile,
                        variant = SwissButtonVariant.OUTLINE,
                        modifier = Modifier.fillMaxWidth().testTag("video_pick_btn")
                    )

                    extractedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Extracted video frame",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .background(SwissInk)
                                .border(1.dp, SwissInk)
                        )
                        val curSec = ((durationMs * positionFraction) / 1000f)
                        Text(
                            text = String.format(Locale.US, "FRAME TIMESTAMP: %.1fs / %.1fs", curSec, durationMs / 1000f),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                            color = SwissInk
                        )
                        Slider(
                            value = positionFraction,
                            onValueChange = { positionFraction = it },
                            colors = SliderDefaults.colors(thumbColor = SwissSignalRed, activeTrackColor = SwissInk)
                        )
                        SwissButton(
                            text = "ATTACH FRAME TO AI CHAT",
                            onClick = {
                                onSendFrameToAi(bmp, String.format(Locale.US, "Video @ %.1fs", curSec))
                                onDismiss()
                            },
                            leadingIcon = Icons.Default.Send,
                            variant = SwissButtonVariant.PRIMARY,
                            modifier = Modifier.fillMaxWidth().testTag("video_attach_frame_btn")
                        )
                    }
                }
            }
        }
    }
}

// 3. Visual Color Palette & WCAG Contrast Extractor
@Composable
fun ColorPaletteDialog(
    initialBitmap: Bitmap? = null,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var bitmap by remember { mutableStateOf(initialBitmap) }
    var swatches by remember {
        mutableStateOf(initialBitmap?.let { ColorPaletteExtractor.extractDominantColors(it, 6) }.orEmpty())
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { bmp ->
                bitmap = bmp
                swatches = ColorPaletteExtractor.extractDominantColors(bmp, 6)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("color_palette_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissInk).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PALETTE STUDIO // HEX & WCAG",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close palette dialog", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SwissButton(
                        text = if (bitmap == null) "PICK PHOTO / UI SCREENSHOT" else "PICK ANOTHER IMAGE",
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        leadingIcon = Icons.Default.Image,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (swatches.isNotEmpty()) {
                        swatches.forEach { sw: PaletteSwatch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissPaper)
                                    .border(1.dp, SwissInk)
                                    .clickable {
                                        clipboard.setText(AnnotatedString(sw.hex))
                                        onNotify("Copied ${sw.hex}")
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(sw.r, sw.g, sw.b))
                                            .border(1.dp, SwissInk)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = sw.hex,
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                            color = SwissInk
                                        )
                                        Text(
                                            text = "RGB(${sw.r}, ${sw.g}, ${sw.b}) · White ${String.format(Locale.US, "%.1f", sw.ratioOnWhite)}:1 · Black ${String.format(Locale.US, "%.1f", sw.ratioOnBlack)}:1",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SwissMuted
                                        )
                                    }
                                }
                                Text(
                                    text = sw.wcagLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                    color = SwissVerifiedInk,
                                    modifier = Modifier.background(SwissVerifiedTint).border(1.dp, SwissVerifiedInk).padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SwissButton(
                                text = "COPY TAILWIND",
                                onClick = {
                                    clipboard.setText(AnnotatedString(ColorPaletteExtractor.toTailwindConfig(swatches)))
                                    onNotify("Copied Tailwind theme colors.")
                                },
                                leadingIcon = Icons.Default.ContentCopy,
                                modifier = Modifier.weight(1f)
                            )
                            SwissButton(
                                text = "COPY CSS VARS",
                                onClick = {
                                    clipboard.setText(AnnotatedString(ColorPaletteExtractor.toCssVariables(swatches)))
                                    onNotify("Copied CSS :root variables.")
                                },
                                leadingIcon = Icons.Default.ContentCopy,
                                variant = SwissButtonVariant.PRIMARY,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Text(
                            text = "Select any photo or UI screenshot to extract its 6 dominant HEX colors, WCAG AA/AAA ratios, and Tailwind/CSS tokens completely offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissMuted
                        )
                    }
                }
            }
        }
    }
}

// 4. Privacy-First Document & Text PII Redactor
@Composable
fun PrivacyRedactorDialog(
    initialText: String = "",
    onDismiss: () -> Unit,
    onAskAiWithRedacted: (String) -> Unit,
    onNotify: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var rawText by remember { mutableStateOf(initialText) }
    val report = remember(rawText) { PrivacyRedactor.redact(rawText) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("privacy_redactor_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SwissInk).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PRIVACY SHIELD // LOCAL PII REDACTOR",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close redactor", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("Paste sensitive contract, email, invoice, or message") },
                        minLines = 4,
                        maxLines = 6,
                        colors = swissTextFieldColors(),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth().testTag("redactor_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().background(SwissPaper).border(1.dp, SwissInk).padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("PHONES: ${report.phonesMasked}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily), color = SwissInk)
                        Text("EMAILS: ${report.emailsMasked}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily), color = SwissInk)
                        Text("IDs: ${report.idsMasked}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily), color = SwissInk)
                        Text("UPI/CARD: ${report.upiAndBankMasked}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily), color = SwissSignalRed)
                    }

                    if (rawText.isNotBlank()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().background(SwissPaper).border(1.dp, SwissInk).padding(10.dp)
                        ) {
                            Text(
                                text = "SAFE SANITIZED PREVIEW (${report.totalMasked} MASKED ON-DEVICE)",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                                color = SwissVerifiedInk
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(report.redactedText, style = MaterialTheme.typography.bodySmall, color = SwissInk)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissButton(
                            text = "COPY SAFE",
                            onClick = {
                                clipboard.setText(AnnotatedString(report.redactedText))
                                onNotify("REDACT // Copied sanitized text.")
                            },
                            enabled = rawText.isNotBlank(),
                            leadingIcon = Icons.Default.ContentCopy,
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "ASK AI SAFELY",
                            onClick = {
                                onAskAiWithRedacted(report.redactedText)
                                onDismiss()
                            },
                            enabled = rawText.isNotBlank(),
                            leadingIcon = Icons.Default.Shield,
                            variant = SwissButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1.3f).testTag("redactor_ask_ai_btn")
                        )
                    }
                }
            }
        }
    }
}
