package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite

private enum class MarkupBrushMode {
    CIRCLE_HIGHLIGHT,
    BLUR_CENSOR
}

private data class DrawnStroke(
    val mode: MarkupBrushMode,
    val points: List<Offset>
)

@Composable
fun ImageAnnotatorDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onApplyAnnotatedBitmap: (Bitmap) -> Unit,
    onExtractPalette: ((Bitmap) -> Unit)? = null
) {
    var brushMode by remember { mutableStateOf(MarkupBrushMode.CIRCLE_HIGHLIGHT) }
    val strokes = remember { mutableStateListOf<DrawnStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val aspect = (sourceBitmap.width.toFloat() / sourceBitmap.height.coerceAtLeast(1).toFloat()).coerceIn(0.6f, 1.8f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("image_annotator_dialog"),
            color = SwissCanvas,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VISUAL MARKUP // CIRCLE-TO-ASK & CENSOR",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SwissWhite
                        )
                        Text(
                            text = "Draw red circles to focus AI or pixelate private areas before upload",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissCanvas
                        )
                    }
                    SwissIconCell(
                        icon = Icons.Default.Close,
                        contentDescription = "Close annotator",
                        onClick = onDismiss,
                        inverted = true,
                        bordered = false,
                        cellSize = 48.dp
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SwissToggleCell(
                            label = "RED CIRCLE",
                            selected = brushMode == MarkupBrushMode.CIRCLE_HIGHLIGHT,
                            onClick = { brushMode = MarkupBrushMode.CIRCLE_HIGHLIGHT },
                            useSignalRedWhenSelected = true,
                            modifier = Modifier.weight(1f).testTag("annotator_mode_circle")
                        )
                        SwissToggleCell(
                            label = "BLUR / CENSOR",
                            selected = brushMode == MarkupBrushMode.BLUR_CENSOR,
                            onClick = { brushMode = MarkupBrushMode.BLUR_CENSOR },
                            modifier = Modifier.weight(1f).testTag("annotator_mode_blur")
                        )
                        SwissIconCell(
                            icon = Icons.Default.Undo,
                            contentDescription = "Undo stroke",
                            onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                            enabled = strokes.isNotEmpty(),
                            cellSize = 48.dp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .background(SwissPaper)
                            .border(1.5.dp, SwissInk, RectangleShape)
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(brushMode) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        currentPoints = listOf(start)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints = currentPoints + change.position
                                    },
                                    onDragEnd = {
                                        if (currentPoints.size >= 2) {
                                            strokes.add(DrawnStroke(brushMode, currentPoints))
                                        }
                                        currentPoints = emptyList()
                                    },
                                    onDragCancel = {
                                        currentPoints = emptyList()
                                    }
                                )
                            }
                    ) {
                        Image(
                            bitmap = sourceBitmap.asImageBitmap(),
                            contentDescription = "Image to annotate",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val all = strokes.toList() + if (currentPoints.size >= 2) {
                                listOf(DrawnStroke(brushMode, currentPoints))
                            } else emptyList()

                            all.forEach { stroke ->
                                val path = Path().apply {
                                    val first = stroke.points.first()
                                    moveTo(first.x, first.y)
                                    for (i in 1 until stroke.points.size) {
                                        lineTo(stroke.points[i].x, stroke.points[i].y)
                                    }
                                }
                                if (stroke.mode == MarkupBrushMode.CIRCLE_HIGHLIGHT) {
                                    drawPath(
                                        path = path,
                                        color = SwissSignalRed,
                                        style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                } else {
                                    drawPath(
                                        path = path,
                                        color = Color(0xFF111111).copy(alpha = 0.85f),
                                        style = Stroke(width = 34f, cap = StrokeCap.Square, join = StrokeJoin.Miter)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onExtractPalette != null) {
                            SwissButton(
                                text = "PALETTE",
                                onClick = { onExtractPalette(sourceBitmap) },
                                leadingIcon = Icons.Default.Palette,
                                variant = SwissButtonVariant.OUTLINE,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SwissButton(
                            text = "APPLY MARKUP",
                            onClick = {
                                val rendered = renderAnnotatedBitmap(
                                    source = sourceBitmap,
                                    strokes = strokes.toList(),
                                    viewWidth = canvasSize.width.coerceAtLeast(1),
                                    viewHeight = canvasSize.height.coerceAtLeast(1)
                                )
                                onApplyAnnotatedBitmap(rendered)
                            },
                            leadingIcon = Icons.Default.Check,
                            variant = SwissButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1.4f).testTag("annotator_apply_btn")
                        )
                    }
                    Text(
                        text = "Tip: Red strokes tell the vision model what to focus on; Censor strokes permanently pixelate sensitive data on-device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissMuted
                    )
                }
            }
        }
    }
}

private fun renderAnnotatedBitmap(
    source: Bitmap,
    strokes: List<DrawnStroke>,
    viewWidth: Int,
    viewHeight: Int
): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(out)
    val sx = out.width.toFloat() / viewWidth.toFloat()
    val sy = out.height.toFloat() / viewHeight.toFloat()
    val scale = (sx + sy) * 0.5f

    val redPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E63946")
        style = AndroidPaint.Style.STROKE
        strokeWidth = (7f * scale).coerceAtLeast(4f)
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }

    strokes.forEach { stroke ->
        if (stroke.mode == MarkupBrushMode.CIRCLE_HIGHLIGHT) {
            val path = AndroidPath()
            val first = stroke.points.first()
            path.moveTo(first.x * sx, first.y * sy)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x * sx, stroke.points[i].y * sy)
            }
            canvas.drawPath(path, redPaint)
        } else {
            // Permanently pixelate blocks along censor brush strokes
            val block = (16f * scale).toInt().coerceIn(8, 36)
            val radius = (18f * scale).toInt().coerceAtLeast(12)
            val fillPaint = AndroidPaint()
            stroke.points.forEach { pt ->
                val cx = (pt.x * sx).toInt()
                val cy = (pt.y * sy).toInt()
                val minX = (cx - radius).coerceAtLeast(0)
                val maxX = (cx + radius).coerceAtMost(out.width - 1)
                val minY = (cy - radius).coerceAtLeast(0)
                val maxY = (cy + radius).coerceAtMost(out.height - 1)
                var y = minY
                while (y < maxY) {
                    var x = minX
                    while (x < maxX) {
                        val sampleColor = out.getPixel(x, y)
                        fillPaint.color = sampleColor
                        canvas.drawRect(
                            x.toFloat(),
                            y.toFloat(),
                            (x + block).coerceAtMost(out.width).toFloat(),
                            (y + block).coerceAtMost(out.height).toFloat(),
                            fillPaint
                        )
                        x += block
                    }
                    y += block
                }
            }
        }
    }
    return out
}
