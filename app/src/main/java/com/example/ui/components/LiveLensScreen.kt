package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.AllChatRepository
import com.example.domain.model.LiveLensMode
import com.example.domain.model.ModelCapability
import com.example.domain.model.SelectableModel
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import com.example.util.ReadAloudController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@Composable
fun LiveLensScreen(
    repository: AllChatRepository,
    preferredModel: SelectableModel?,
    initialTranslatorMode: Boolean = false,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val allChatModels by remember(repository) {
        repository.observeModelsForCapability(ModelCapability.TEXT_CHAT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val visionModels = remember(allChatModels) {
        allChatModels.filter { it.model.supportsImageInput }.ifEmpty { allChatModels }
    }
    val fallbackQueueIds by repository.visionFallbackModelIdsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val intervalSec by repository.liveLensIntervalSecFlow.collectAsStateWithLifecycle(initialValue = 0)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var lensMode by remember {
        mutableStateOf(if (initialTranslatorMode) LiveLensMode.LIVE_TRANSLATOR else LiveLensMode.AUTO_EXPLAIN)
    }
    var targetLanguage by remember { mutableStateOf("English") }
    val languages = remember {
        listOf("English", "Hindi", "Marathi", "Spanish", "French", "German", "Japanese", "Arabic")
    }
    val intervalOptions = remember {
        listOf(0 to "STEADY 1.5s", 3 to "3s", 5 to "5s", 10 to "10s", 15 to "15s")
    }

    var useFrontCamera by remember { mutableStateOf(false) }
    var autoCaptureOnSteady by remember { mutableStateOf(false) }
    var muteTts by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var resultModelLabel by remember { mutableStateOf<String?>(null) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var failoverNotice by remember { mutableStateOf<String?>(null) }
    var showFallbackDialog by remember { mutableStateOf(false) }
    var steadyProgress by remember { mutableFloatStateOf(0f) }
    var lastAutoCaptureMs by remember { mutableLongStateOf(0L) }
    var speakingId by remember { mutableStateOf<Long?>(null) }
    var analyzeJob by remember { mutableStateOf<Job?>(null) }

    // Auto-dismiss non-blocking 429 Failover Toast Pill after 4.5s
    LaunchedEffect(failoverNotice) {
        if (failoverNotice != null) {
            delay(4500L)
            failoverNotice = null
        }
    }

    val ttsController = remember(context) {
        ReadAloudController(
            context = context,
            onSpeakingIdChanged = { id -> speakingId = id }
        )
    }
    DisposableEffect(ttsController) {
        onDispose {
            analyzeJob?.cancel()
            ttsController.release()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    fun captureAndAnalyze() {
        if (isAnalyzing) return
        val frame = runCatching { previewView.bitmap }.getOrNull()
        if (frame == null) {
            errorText = "Camera frame not ready yet. Point at a subject and tap Scan."
            return
        }
        isAnalyzing = true
        errorText = null
        ttsController.stop()
        analyzeJob?.cancel()
        analyzeJob = scope.launch {
            try {
                val res = repository.analyzeLiveLensFrame(
                    bitmap = frame,
                    mode = lensMode,
                    targetLanguage = targetLanguage,
                    preferredModel = preferredModel,
                    onModelFailover = { notice -> failoverNotice = notice }
                )
                resultModelLabel = res.modelLabel
                resultText = res.answer
                if (!res.failoverNotice.isNullOrBlank()) {
                    failoverNotice = res.failoverNotice
                }
                if (!muteTts) {
                    ttsController.speak(System.currentTimeMillis(), res.answer)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                errorText = err.message ?: "Live Lens analysis failed."
            } finally {
                isAnalyzing = false
            }
        }
    }

    // Bind CameraX Preview
    LaunchedEffect(hasCameraPermission, useFrontCamera, lifecycleOwner) {
        if (!hasCameraPermission) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val cameraProvider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
            }.onFailure {
                errorText = "Camera preview unavailable: ${it.message ?: "check permissions"}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(context) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    // Mode A: Adjustable Interval Timer Auto-Scan (3s / 5s / 10s / 15s) to avoid hitting free-tier RPM limits
    LaunchedEffect(autoCaptureOnSteady, intervalSec, hasCameraPermission, lensMode, targetLanguage) {
        if (!autoCaptureOnSteady || !hasCameraPermission || intervalSec <= 0) return@LaunchedEffect
        while (true) {
            delay(intervalSec * 1000L)
            if (!isAnalyzing) {
                captureAndAnalyze()
            }
        }
    }

    // Mode B: Real Accelerometer Steady-Frame Detector (1.5s stillness -> Auto-Capture when intervalSec == 0)
    DisposableEffect(autoCaptureOnSteady, intervalSec, hasCameraPermission, isAnalyzing, lensMode, targetLanguage) {
        if (!autoCaptureOnSteady || !hasCameraPermission || intervalSec > 0) {
            steadyProgress = 0f
            return@DisposableEffect onDispose {}
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || accel == null) {
            return@DisposableEffect onDispose {}
        }

        var lastX = 0f
        var lastY = 0f
        var lastZ = 0f
        var hasBaseline = false
        var steadySinceMs = System.currentTimeMillis()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (isAnalyzing) {
                    steadyProgress = 0f
                    steadySinceMs = System.currentTimeMillis()
                    return
                }
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                if (!hasBaseline) {
                    lastX = x
                    lastY = y
                    lastZ = z
                    hasBaseline = true
                    steadySinceMs = System.currentTimeMillis()
                    return
                }
                val dx = x - lastX
                val dy = y - lastY
                val dz = z - lastZ
                lastX = x
                lastY = y
                lastZ = z
                val delta = sqrt(dx * dx + dy * dy + dz * dz)
                val now = System.currentTimeMillis()

                if (delta > 0.35f) {
                    steadySinceMs = now
                    steadyProgress = 0f
                } else {
                    val cooldownPassed = (now - lastAutoCaptureMs) > 5500L
                    if (cooldownPassed) {
                        val elapsed = (now - steadySinceMs).coerceAtLeast(0L)
                        steadyProgress = (elapsed / 1500f).coerceIn(0f, 1f)
                        if (elapsed >= 1500L) {
                            lastAutoCaptureMs = now
                            steadySinceMs = now
                            steadyProgress = 0f
                            captureAndAnalyze()
                        }
                    } else {
                        steadyProgress = 0f
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SwissInk)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "LIVE LENS // CAMERA PERMISSION REQUIRED",
                    style = MaterialTheme.typography.labelLarge,
                    color = SwissSignalRed,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Grant camera access to use Zero-Prompt Auto-Explainer and Live Translator.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SwissWhite,
                    textAlign = TextAlign.Center
                )
                SwissButton(
                    text = "Grant Camera Access",
                    variant = SwissButtonVariant.PRIMARY,
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
        }

        // Swiss Viewfinder Corner Reticle
        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(220.dp)
                    .border(
                        width = if (steadyProgress > 0.1f || isAnalyzing) 2.dp else 1.dp,
                        color = if (isAnalyzing || steadyProgress > 0.1f) SwissSignalRed else SwissWhite.copy(alpha = 0.65f),
                        shape = RectangleShape
                    )
            ) {
                if (autoCaptureOnSteady && intervalSec == 0 && steadyProgress > 0.05f && !isAnalyzing) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(SwissInk.copy(alpha = 0.85f))
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "HOLD STEADY // AUTO-SCAN ${(steadyProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { steadyProgress },
                            color = SwissSignalRed,
                            trackColor = SwissConcrete,
                            modifier = Modifier.fillMaxWidth().height(3.dp)
                        )
                    }
                } else if (autoCaptureOnSteady && intervalSec > 0 && !isAnalyzing) {
                    Text(
                        text = "AUTO TIMER // EVERY ${intervalSec}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissSignalRed,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(SwissInk.copy(alpha = 0.85f))
                            .padding(vertical = 5.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Top HUD Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(SwissInk.copy(alpha = 0.92f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SwissIconCell(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Chat",
                    onClick = {
                        analyzeJob?.cancel()
                        ttsController.stop()
                        onNavigateBack()
                    },
                    inverted = true,
                    cellSize = 40.dp,
                    modifier = Modifier.testTag("live_lens_back")
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LIVE LENS // ZERO-PROMPT VISION",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissSignalRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (lensMode == LiveLensMode.AUTO_EXPLAIN) "Auto-Explainer" else "Live Translator → $targetLanguage",
                        style = MaterialTheme.typography.titleSmall,
                        color = SwissWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Fallback Models Queue Quick Button
                Row(
                    modifier = Modifier
                        .border(1.dp, SwissWhite.copy(alpha = 0.65f), RectangleShape)
                        .clickable { showFallbackDialog = true }
                        .padding(horizontal = 7.dp, vertical = 7.dp)
                        .testTag("live_lens_fallback_button"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = "Configure fallback models",
                        tint = SwissSignalRed,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (fallbackQueueIds.isEmpty()) "429 AUTO" else "QUEUE:${fallbackQueueIds.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissWhite
                    )
                }
                SwissIconCell(
                    icon = if (muteTts) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                    contentDescription = if (muteTts) "Unmute auto-read TTS" else "Mute auto-read TTS",
                    onClick = {
                        muteTts = !muteTts
                        if (muteTts) ttsController.stop()
                    },
                    accent = !muteTts,
                    cellSize = 40.dp,
                    modifier = Modifier.testTag("live_lens_mute_toggle")
                )
                SwissIconCell(
                    icon = Icons.Filled.Cameraswitch,
                    contentDescription = "Switch camera lens",
                    onClick = { useFrontCamera = !useFrontCamera },
                    inverted = true,
                    cellSize = 40.dp
                )
            }

            // Mode Switcher Bar: Auto-Explain vs Live Translator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LiveLensMode.entries.forEach { mode ->
                    val selected = lensMode == mode
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selected) SwissSignalRed else Color.Transparent, RectangleShape)
                            .border(1.dp, if (selected) SwissSignalRed else SwissWhite.copy(alpha = 0.5f), RectangleShape)
                            .clickable { lensMode = mode }
                            .padding(vertical = 7.dp, horizontal = 8.dp)
                            .testTag("lens_mode_${mode.name.lowercase()}"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (mode == LiveLensMode.AUTO_EXPLAIN) Icons.Outlined.CenterFocusStrong else Icons.Outlined.Translate,
                            contentDescription = null,
                            tint = SwissWhite,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissWhite
                        )
                    }
                }
            }

            // Auto-Explain Pacing Interval Selector (Steady 1.5s vs 3s / 5s / 10s / 15s)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PACE:",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissConcrete
                )
                intervalOptions.forEach { (sec, label) ->
                    val active = intervalSec == sec
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) SwissInk else SwissWhite,
                        modifier = Modifier
                            .background(if (active) SwissWhite else Color.Transparent, RectangleShape)
                            .border(1.dp, SwissWhite.copy(alpha = 0.5f), RectangleShape)
                            .clickable {
                                scope.launch { repository.setLiveLensIntervalSec(sec) }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("lens_interval_$sec")
                    )
                }
            }

            // Target Language Pills in Live Translator Mode
            AnimatedVisibility(visible = lensMode == LiveLensMode.LIVE_TRANSLATOR) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languages.forEach { lang ->
                        val active = targetLanguage == lang
                        Text(
                            text = lang.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) SwissInk else SwissWhite,
                            modifier = Modifier
                                .background(if (active) SwissWhite else Color.Transparent, RectangleShape)
                                .border(1.dp, SwissWhite.copy(alpha = 0.6f), RectangleShape)
                                .clickable { targetLanguage = lang }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("lens_lang_${lang.lowercase()}")
                        )
                    }
                }
            }

            // Non-blocking 429 Auto-Failover Toast Pill
            AnimatedVisibility(visible = failoverNotice != null) {
                failoverNotice?.let { notice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissSignalRed)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FAILOVER // $notice",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissWhite,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss failover notice",
                            tint = SwissWhite,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { failoverNotice = null }
                        )
                    }
                }
            }

            if (isAnalyzing) {
                LinearProgressIndicator(
                    color = SwissSignalRed,
                    trackColor = SwissConcrete,
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }
        }

        // Bottom Controls + Clean Result Bottom Sheet
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            AnimatedVisibility(visible = errorText != null) {
                errorText?.let { err ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .background(SwissInk, RectangleShape)
                            .border(1.dp, SwissSignalRed, RectangleShape)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissWhite,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier
                                .background(SwissSignalRed, RectangleShape)
                                .clickable { captureAndAnalyze() }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                                .testTag("live_lens_retry_button"),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Retry scan",
                                tint = SwissWhite,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "RETRY",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite
                            )
                        }
                        if (err.contains("Settings", ignoreCase = true) || err.contains("rate", ignoreCase = true)) {
                            Text(
                                text = "[SET]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite,
                                modifier = Modifier
                                    .clickable { onOpenSettings() }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            // Live Result Bottom Sheet Over Camera Preview
            AnimatedVisibility(visible = resultText != null) {
                resultText?.let { answer ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .background(SwissPaper, RectangleShape)
                            .border(1.dp, SwissInk, RectangleShape)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissInk)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${lensMode.label} // ${resultModelLabel ?: "VISION"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (speakingId != null) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                    contentDescription = "Read aloud result",
                                    tint = SwissSignalRed,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            if (speakingId != null) ttsController.stop()
                                            else ttsController.speak(System.currentTimeMillis(), answer)
                                        }
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy result",
                                    tint = SwissWhite,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { clipboard.setText(AnnotatedString(answer)) }
                                )
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss result",
                                    tint = SwissWhite,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            ttsController.stop()
                                            resultText = null
                                        }
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = answer,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SwissInk
                            )
                        }
                    }
                }
            }

            // Bottom Shutter & Auto-Capture Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissInk.copy(alpha = 0.94f))
                    .border(1.dp, SwissInk, RectangleShape)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val autoLabel = when {
                    autoCaptureOnSteady && intervalSec == 0 -> "AUTO 1.5s: ON"
                    autoCaptureOnSteady && intervalSec > 0 -> "AUTO ${intervalSec}s: ON"
                    intervalSec > 0 -> "AUTO ${intervalSec}s"
                    else -> "AUTO STEADY"
                }
                Column(
                    modifier = Modifier
                        .background(if (autoCaptureOnSteady) SwissSignalRed else SwissCanvas.copy(alpha = 0.14f), RectangleShape)
                        .border(1.dp, if (autoCaptureOnSteady) SwissSignalRed else SwissWhite.copy(alpha = 0.5f), RectangleShape)
                        .clickable { autoCaptureOnSteady = !autoCaptureOnSteady }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                        .testTag("live_lens_steady_toggle"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = autoLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissWhite
                    )
                }

                // Primary Scan / Translate Button
                SwissButton(
                    text = when {
                        isAnalyzing -> "Scanning View…"
                        lensMode == LiveLensMode.LIVE_TRANSLATOR -> "Translate View ($targetLanguage)"
                        else -> "Explain View Now"
                    },
                    variant = SwissButtonVariant.PRIMARY,
                    enabled = hasCameraPermission && !isAnalyzing,
                    onClick = { captureAndAnalyze() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("live_lens_capture_button")
                )
            }
        }
    }

    // Quick Fallback Vision Models Priority Queue Dialog
    if (showFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showFallbackDialog = false },
            containerColor = SwissCanvas,
            shape = RectangleShape,
            title = {
                Column {
                    Text(
                        text = "429 AUTO-FAILOVER // PRIORITY QUEUE",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissSignalRed
                    )
                    Text(
                        text = "Vision Fallback Models",
                        style = MaterialTheme.typography.titleLarge,
                        color = SwissInk
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "When a model hits HTTP 429 / quota limit, Live Lens places it on a 60s cooldown and switches automatically to the next model below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissMuted
                    )
                    if (visionModels.isEmpty()) {
                        Text(
                            text = "No vision models found. Configure providers in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SwissSignalRed
                        )
                    } else {
                        val queuedModels = fallbackQueueIds.mapNotNull { id ->
                            visionModels.firstOrNull { it.model.id == id }
                        }
                        val unqueuedModels = visionModels.filter { m -> m.model.id !in fallbackQueueIds }

                        if (queuedModels.isNotEmpty()) {
                            Text(
                                text = "PRIORITY ORDER (1 → ${queuedModels.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissInk
                            )
                            queuedModels.forEachIndexed { idx, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SwissInk, RectangleShape)
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.model.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SwissWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.provider.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SwissConcrete
                                        )
                                    }
                                    if (idx > 0) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowUpward,
                                            contentDescription = "Move up",
                                            tint = SwissWhite,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable {
                                                    val mutable = fallbackQueueIds.toMutableList()
                                                    val cur = mutable.removeAt(idx)
                                                    mutable.add(idx - 1, cur)
                                                    scope.launch { repository.setVisionFallbackModelIds(mutable) }
                                                }
                                        )
                                    }
                                    if (idx < queuedModels.lastIndex) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDownward,
                                            contentDescription = "Move down",
                                            tint = SwissWhite,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable {
                                                    val mutable = fallbackQueueIds.toMutableList()
                                                    val cur = mutable.removeAt(idx)
                                                    mutable.add(idx + 1, cur)
                                                    scope.launch { repository.setVisionFallbackModelIds(mutable) }
                                                }
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove from queue",
                                        tint = SwissSignalRed,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                scope.launch {
                                                    repository.setVisionFallbackModelIds(
                                                        fallbackQueueIds.filterNot { it == item.model.id }
                                                    )
                                                }
                                            }
                                    )
                                }
                            }
                        }

                        if (unqueuedModels.isNotEmpty()) {
                            Text(
                                text = "TAP TO ADD TO PRIORITY QUEUE",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissMuted
                            )
                            unqueuedModels.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SwissPaper, RectangleShape)
                                        .border(1.dp, SwissInk, RectangleShape)
                                        .clickable {
                                            scope.launch {
                                                repository.setVisionFallbackModelIds(fallbackQueueIds + item.model.id)
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.model.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SwissInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.provider.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SwissMuted
                                        )
                                    }
                                    Text(
                                        text = "+ ADD",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SwissSignalRed
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                SwissButton(
                    text = "Done",
                    variant = SwissButtonVariant.PRIMARY,
                    minHeight = 40.dp,
                    onClick = { showFallbackDialog = false }
                )
            }
        )
    }
}
