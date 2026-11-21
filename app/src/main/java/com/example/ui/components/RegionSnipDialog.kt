package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RegionSnipDialog(
    sourceUri: Uri,
    onConfirmCrop: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var loadedBitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }

    // Normalized crop box [0f..1f]
    var leftNorm by remember { mutableFloatStateOf(0.10f) }
    var topNorm by remember { mutableFloatStateOf(0.15f) }
    var rightNorm by remember { mutableFloatStateOf(0.90f) }
    var bottomNorm by remember { mutableFloatStateOf(0.85f) }

    LaunchedEffect(sourceUri) {
        loadedBitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = SwissCanvas,
            shape = RectangleShape,
            border = BorderStroke(1.dp, SwissInk),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("REGION SNIP // DRAG TO CROP TARGET AREA", style = MaterialTheme.typography.labelLarge, color = SwissWhite)
                        Text("Draw a box over an error, chart, or UI bug to attach to Vision", style = MaterialTheme.typography.labelSmall, color = SwissCanvas)
                    }
                    Text(
                        text = "[ CLOSE ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissWhite,
                        modifier = Modifier
                            .border(1.dp, SwissWhite, RectangleShape)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                val bmp = loadedBitmap
                if (bmp == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("LOADING SCREENSHOT…", style = MaterialTheme.typography.labelMedium, color = SwissMuted)
                    }
                } else {
                    val aspect = (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.45f, 1.8f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .aspectRatio(aspect)
                            .border(1.dp, SwissInk, RectangleShape)
                            .pointerInput(bmp) {
                                var startNormX = 0f
                                var startNormY = 0f
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        startNormX = (offset.x / size.width).coerceIn(0f, 1f)
                                        startNormY = (offset.y / size.height).coerceIn(0f, 1f)
                                        leftNorm = startNormX
                                        rightNorm = (startNormX + 0.05f).coerceAtMost(1f)
                                        topNorm = startNormY
                                        bottomNorm = (startNormY + 0.05f).coerceAtMost(1f)
                                    },
                                    onDrag = { change, _ ->
                                        val curX = (change.position.x / size.width).coerceIn(0f, 1f)
                                        val curY = (change.position.y / size.height).coerceIn(0f, 1f)
                                        leftNorm = minOf(startNormX, curX)
                                        rightNorm = maxOf(startNormX, curX)
                                        topNorm = minOf(startNormY, curY)
                                        bottomNorm = maxOf(startNormY, curY)
                                    }
                                )
                            }
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Screenshot to snip",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val l = leftNorm * size.width
                            val t = topNorm * size.height
                            val r = rightNorm * size.width
                            val b = bottomNorm * size.height
                            val scrim = Color.Black.copy(alpha = 0.50f)

                            // Outer dimmed frame
                            drawRect(scrim, Offset(0f, 0f), Size(size.width, t))
                            drawRect(scrim, Offset(0f, b), Size(size.width, size.height - b))
                            drawRect(scrim, Offset(0f, t), Size(l, b - t))
                            drawRect(scrim, Offset(r, t), Size(size.width - r, b - t))

                            // Crisp Swiss Signal Red crop border
                            drawRect(
                                color = SwissSignalRed,
                                topLeft = Offset(l, t),
                                size = Size((r - l).coerceAtLeast(2f), (b - t).coerceAtLeast(2f)),
                                style = Stroke(width = 3f)
                            )

                            // Corner registration brackets
                            val corner = 14f
                            val strokeW = 5f
                            // Top-left
                            drawLine(SwissWhite, Offset(l, t), Offset(l + corner, t), strokeW)
                            drawLine(SwissWhite, Offset(l, t), Offset(l, t + corner), strokeW)
                            // Bottom-right
                            drawLine(SwissWhite, Offset(r, b), Offset(r - corner, b), strokeW)
                            drawLine(SwissWhite, Offset(r, b), Offset(r, b - corner), strokeW)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissPaper)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SwissButton(
                            text = "Full Frame",
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 44.dp,
                            onClick = {
                                leftNorm = 0f; topNorm = 0f; rightNorm = 1f; bottomNorm = 1f
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "Attach Snip",
                            variant = SwissButtonVariant.PRIMARY,
                            minHeight = 44.dp,
                            onClick = {
                                val x = (leftNorm * bmp.width).toInt().coerceIn(0, bmp.width - 2)
                                val y = (topNorm * bmp.height).toInt().coerceIn(0, bmp.height - 2)
                                val w = ((rightNorm - leftNorm) * bmp.width).toInt().coerceIn(2, bmp.width - x)
                                val h = ((bottomNorm - topNorm) * bmp.height).toInt().coerceIn(2, bmp.height - y)
                                val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
                                onConfirmCrop(cropped)
                            },
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("confirm_region_snip_button")
                        )
                    }
                }
            }
        }
    }
}
