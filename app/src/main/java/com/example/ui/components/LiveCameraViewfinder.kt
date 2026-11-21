package com.example.ui.components

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite

class LiveCamController {
    internal var previewViewRef: PreviewView? = null

    fun grabCurrentFrameBitmap(): Bitmap? {
        return runCatching { previewViewRef?.bitmap }.getOrNull()
    }
}

@Composable
fun LiveCameraViewfinderPanel(
    controller: LiveCamController,
    onSnapNow: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var useFrontCamera by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf<String?>(null) }

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

    DisposableEffect(controller, previewView) {
        controller.previewViewRef = previewView
        onDispose {
            if (controller.previewViewRef === previewView) {
                controller.previewViewRef = null
            }
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    LaunchedEffect(useFrontCamera, lifecycleOwner) {
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
                bindError = null
            }.onFailure { err ->
                bindError = err.message ?: "Unable to open camera preview."
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissInk)
            .border(1.dp, SwissInk, RectangleShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SwissInk)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(SwissSignalRed)
                )
                Text(
                    text = "LIVE CAM // AUTO-GRABS FRAME ON SEND",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissWhite
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .border(1.dp, SwissSignalRed, RectangleShape)
                        .background(SwissSignalRed, RectangleShape)
                        .clickable(onClick = onSnapNow)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("live_cam_snap_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = "Snap frame", tint = SwissWhite, modifier = Modifier.size(13.dp))
                        Text("SNAP", style = MaterialTheme.typography.labelSmall, color = SwissWhite)
                    }
                }
                Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = "Flip camera",
                    tint = SwissWhite,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { useFrontCamera = !useFrontCamera }
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close Live Cam",
                    tint = SwissWhite,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onClose)
                        .testTag("live_cam_close_button")
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(SwissInk),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            if (bindError != null) {
                Text(
                    text = "CAM // $bindError",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed,
                    modifier = Modifier
                        .background(SwissInk.copy(alpha = 0.85f))
                        .padding(8.dp)
                )
            }
        }
    }
}
