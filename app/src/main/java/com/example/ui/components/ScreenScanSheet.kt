package com.example.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.AllChatRepository
import com.example.domain.model.LiveLensMode
import com.example.domain.model.SelectableModel
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissGridLine
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite
import com.example.util.ReadAloudController
import com.example.util.ScreenScanCaptureBus
import com.example.util.ScreenScanCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SCREEN_SCAN_LANGUAGES = listOf("English", "Hindi", "Marathi", "Spanish", "French", "German", "Japanese")

@Composable
fun ScreenScanSheet(
    repository: AllChatRepository,
    preferredModel: SelectableModel?,
    initialBitmap: Bitmap? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var capturedBitmap by remember { mutableStateOf(initialBitmap) }
    var scanMode by remember { mutableStateOf(LiveLensMode.AUTO_EXPLAIN) }
    var targetLanguage by remember { mutableStateOf("English") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<String?>(null) }
    var modelLabel by remember { mutableStateOf<String?>(null) }
    var statusNotice by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var ttsMuted by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val readAloud = remember(context) {
        ReadAloudController(context = context, onSpeakingIdChanged = {})
    }
    DisposableEffect(readAloud) {
        onDispose {
            activeJob?.cancel()
            readAloud.release()
        }
    }

    fun analyzeBitmap(bitmap: Bitmap) {
        activeJob?.cancel()
        activeJob = scope.launch {
            isAnalyzing = true
            errorMessage = null
            statusNotice = "Analyzing screen with vision failover queue…"
            try {
                val res = repository.analyzeLiveLensFrame(
                    bitmap = bitmap,
                    mode = scanMode,
                    targetLanguage = targetLanguage,
                    preferredModel = preferredModel,
                    onModelFailover = { notice -> statusNotice = notice }
                )
                analysisResult = res.answer
                modelLabel = res.modelLabel
                statusNotice = res.failoverNotice
                if (!ttsMuted) {
                    readAloud.speak(System.currentTimeMillis(), res.answer)
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Screen analysis failed. Check vision model in Settings."
            } finally {
                isAnalyzing = false
            }
        }
    }

    LaunchedEffect(initialBitmap) {
        if (initialBitmap != null) {
            analyzeBitmap(initialBitmap)
        }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            statusNotice = "Capturing system screen frame…"
            ScreenScanCaptureService.captureSingleFrame(
                context = context,
                resultCode = result.resultCode,
                data = data
            ) { bmp ->
                if (bmp != null) {
                    capturedBitmap = bmp
                    analyzeBitmap(bmp)
                } else {
                    errorMessage = "Could not capture screen frame. Try 'Current Window' or pick a screenshot."
                }
            }
        } else {
            statusNotice = "System screen capture canceled."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                }
                if (bmp != null) {
                    capturedBitmap = bmp
                    analyzeBitmap(bmp)
                } else {
                    errorMessage = "Could not decode selected screenshot."
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .border(2.dp, SwissInk, RectangleShape),
            shape = RectangleShape,
            color = SwissWhite
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "SCAN SCREEN // VISION FAILOVER",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = SwissSignalRed
                        )
                        Text(
                            text = modelLabel ?: "Capture & Analyze Any Screen",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = SwissWhite
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                ttsMuted = !ttsMuted
                                if (ttsMuted) readAloud.stop()
                            },
                            modifier = Modifier.testTag("screen_scan_tts_toggle")
                        ) {
                            Icon(
                                imageVector = if (ttsMuted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                contentDescription = if (ttsMuted) "Unmute read aloud" else "Mute read aloud",
                                tint = if (ttsMuted) SwissMuted else SwissWhite
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_screen_scan")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close Scan Screen", tint = SwissWhite)
                        }
                    }
                }

                // Mode switcher (Auto-Explain | Translate | Solve Step-by-Step)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissSubtleFill)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LiveLensMode.entries.forEach { mode ->
                        val active = scanMode == mode
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) SwissInk else SwissWhite, RectangleShape)
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable {
                                    scanMode = mode
                                    capturedBitmap?.let { analyzeBitmap(it) }
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .testTag("screen_scan_mode_${mode.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    LiveLensMode.AUTO_EXPLAIN -> Icons.Outlined.AutoAwesome
                                    LiveLensMode.LIVE_TRANSLATOR -> Icons.Outlined.Translate
                                    LiveLensMode.SOLVE_STEP_BY_STEP -> Icons.Outlined.Calculate
                                    LiveLensMode.WHITEBOARD_TO_CODE -> Icons.Outlined.Code
                                },
                                contentDescription = null,
                                tint = if (active) SwissWhite else SwissInk,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (active) SwissWhite else SwissInk,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Target language pills when in Translate mode
                if (scanMode == LiveLensMode.LIVE_TRANSLATOR) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SCREEN_SCAN_LANGUAGES.forEach { lang ->
                            val selected = targetLanguage == lang
                            Box(
                                modifier = Modifier
                                    .background(if (selected) SwissSignalRed else SwissWhite, RectangleShape)
                                    .border(1.dp, if (selected) SwissSignalRed else SwissGridLine, RectangleShape)
                                    .clickable {
                                        targetLanguage = lang
                                        capturedBitmap?.let { analyzeBitmap(it) }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = lang,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = if (selected) SwissWhite else SwissInk
                                )
                            }
                        }
                    }
                }

                // Capture source bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScanSourceButton(
                        icon = Icons.Outlined.ScreenshotMonitor,
                        label = "SYSTEM SCREEN",
                        accent = true,
                        testTag = "scan_source_system_screen",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                            if (mpm != null) {
                                mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                            } else {
                                errorMessage = "MediaProjection service unavailable on this device."
                            }
                        }
                    )
                    ScanSourceButton(
                        icon = Icons.Outlined.CropFree,
                        label = "THIS WINDOW",
                        accent = false,
                        testTag = "scan_source_active_window",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val act = context.findHostActivity()
                            if (act != null) {
                                ScreenScanCaptureBus.captureActiveWindow(act) { bmp ->
                                    if (bmp != null) {
                                        capturedBitmap = bmp
                                        analyzeBitmap(bmp)
                                    } else {
                                        errorMessage = "Could not capture active window."
                                    }
                                }
                            } else {
                                errorMessage = "Host window unavailable."
                            }
                        }
                    )
                    ScanSourceButton(
                        icon = Icons.Outlined.Image,
                        label = "SCREENSHOT",
                        accent = false,
                        testTag = "scan_source_gallery_screenshot",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                HorizontalDivider(color = SwissGridLine)

                // Body (preview + analysis result)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (capturedBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(SwissInk, RectangleShape)
                                .border(1.dp, SwissInk, RectangleShape)
                        ) {
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = "Captured screen frame",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (!statusNotice.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissSubtleFill, RectangleShape)
                                .border(1.dp, SwissGridLine, RectangleShape)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = statusNotice.orEmpty(),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = SwissInk
                            )
                        }
                    }

                    if (isAnalyzing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = SwissSignalRed
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                text = "ANALYZING SCREEN FRAME…",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = SwissInk
                            )
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissSignalRed.copy(alpha = 0.08f), RectangleShape)
                                .border(1.dp, SwissSignalRed, RectangleShape)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = SwissSignalRed
                            )
                            capturedBitmap?.let { bmp ->
                                SwissButton(
                                    text = "Retry Analysis",
                                    variant = SwissButtonVariant.PRIMARY,
                                    minHeight = 40.dp,
                                    onClick = { analyzeBitmap(bmp) }
                                )
                            }
                        }
                    }

                    if (!analysisResult.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissWhite, RectangleShape)
                                .border(1.dp, SwissInk, RectangleShape)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LatexMathMarkdownView(content = analysisResult.orEmpty())

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SwissButton(
                                    text = "Copy",
                                    leadingIcon = Icons.Outlined.ContentCopy,
                                    variant = SwissButtonVariant.OUTLINE,
                                    minHeight = 40.dp,
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Screen Scan", analysisResult))
                                        statusNotice = "Copied analysis to clipboard."
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SwissButton(
                                    text = "Save Note",
                                    leadingIcon = Icons.Outlined.NoteAdd,
                                    variant = SwissButtonVariant.OUTLINE,
                                    minHeight = 40.dp,
                                    onClick = {
                                        val content = analysisResult.orEmpty()
                                        scope.launch {
                                            runCatching {
                                                repository.saveVoiceQuickNote("Screen Scan (${scanMode.label}):\n$content", preferredModel)
                                            }.onSuccess { note ->
                                                statusNotice = "Saved \"${note.title}\" to Voice Notes Inbox."
                                            }.onFailure { err ->
                                                errorMessage = err.message
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("screen_scan_save_note")
                                )
                                capturedBitmap?.let { bmp ->
                                    IconButton(
                                        onClick = { analyzeBitmap(bmp) },
                                        modifier = Modifier.border(1.dp, SwissInk, RectangleShape)
                                    ) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = "Re-analyze frame")
                                    }
                                }
                            }
                        }
                    } else if (!isAnalyzing && capturedBitmap == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tap SYSTEM SCREEN to capture any app on your phone, THIS WINDOW for instant capture, or SCREENSHOT to pick an image.\nUses your 429 Vision Auto-Failover queue.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SwissMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanSourceButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(if (accent) SwissSignalRed else SwissSubtleFill, RectangleShape)
            .border(1.dp, if (accent) SwissSignalRed else SwissInk, RectangleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (accent) SwissWhite else SwissInk,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold
            ),
            color = if (accent) SwissWhite else SwissInk,
            maxLines = 1
        )
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
