package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class MaskStroke(
    val points: List<Offset>, // Normalized 0f..1f coordinates
    val brushFraction: Float // Brush width as fraction of canvas width
)

data class RenderedInpaintPayloads(
    val sourcePngBytes: ByteArray,
    val openAiTransparentMaskPng: ByteArray?,
    val geminiRedOverlayMaskPng: ByteArray?
)

object InpaintingMaskRenderer {
    suspend fun renderPayloads(
        context: Context,
        sourceUri: Uri,
        strokes: List<MaskStroke>
    ): RenderedInpaintPayloads = withContext(Dispatchers.IO) {
        val rawBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw IllegalStateException("Could not decode selected image.")

        val targetSize = 1024
        val squareSource = Bitmap.createScaledBitmap(rawBitmap, targetSize, targetSize, true)
        if (squareSource !== rawBitmap) rawBitmap.recycle()

        val sourceOut = ByteArrayOutputStream()
        squareSource.compress(Bitmap.CompressFormat.PNG, 100, sourceOut)
        val sourceBytes = sourceOut.toByteArray()

        if (strokes.isEmpty()) {
            squareSource.recycle()
            return@withContext RenderedInpaintPayloads(sourceBytes, null, null)
        }

        // 1. OpenAI-spec RGBA mask: opaque pixels preserved, alpha=0 transparent hole where user painted
        val openAiMask = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val openAiCanvas = android.graphics.Canvas(openAiMask)
        openAiCanvas.drawColor(android.graphics.Color.WHITE)
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        drawNormalizedStrokes(openAiCanvas, strokes, targetSize.toFloat(), clearPaint)
        val openAiOut = ByteArrayOutputStream()
        openAiMask.compress(Bitmap.CompressFormat.PNG, 100, openAiOut)
        openAiMask.recycle()

        // 2. Gemini visual mask overlay: original image with vivid Signal Red strokes painted on top
        val geminiOverlay = squareSource.copy(Bitmap.Config.ARGB_8888, true)
        val geminiCanvas = android.graphics.Canvas(geminiOverlay)
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(180, 255, 59, 48)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        drawNormalizedStrokes(geminiCanvas, strokes, targetSize.toFloat(), redPaint)
        val geminiOut = ByteArrayOutputStream()
        geminiOverlay.compress(Bitmap.CompressFormat.PNG, 100, geminiOut)
        geminiOverlay.recycle()
        squareSource.recycle()

        RenderedInpaintPayloads(
            sourcePngBytes = sourceBytes,
            openAiTransparentMaskPng = openAiOut.toByteArray(),
            geminiRedOverlayMaskPng = geminiOut.toByteArray()
        )
    }

    private fun drawNormalizedStrokes(
        canvas: android.graphics.Canvas,
        strokes: List<MaskStroke>,
        sizePx: Float,
        paint: Paint
    ) {
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            paint.strokeWidth = (stroke.brushFraction * sizePx).coerceAtLeast(16f)
            if (stroke.points.size == 1) {
                val p = stroke.points.first()
                canvas.drawPoint(p.x * sizePx, p.y * sizePx, paint)
            } else {
                val path = android.graphics.Path()
                val first = stroke.points.first()
                path.moveTo(first.x * sizePx, first.y * sizePx)
                for (i in 1 until stroke.points.size) {
                    val pt = stroke.points[i]
                    path.lineTo(pt.x * sizePx, pt.y * sizePx)
                }
                canvas.drawPath(path, paint)
            }
        }
    }
}

@Composable
fun InpaintingMaskCanvas(
    imageUri: Uri,
    strokes: List<MaskStroke>,
    onStrokesChanged: (List<MaskStroke>) -> Unit,
    modifier: Modifier = Modifier
) {
    var brushSizeFraction by remember { mutableFloatStateOf(0.075f) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var activePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val maskColor = SwissSignalRed.copy(alpha = 0.55f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(1.dp, SwissInk, RectangleShape)
                .onSizeChanged { canvasSize = it }
                .pointerInput(brushSizeFraction, strokes, canvasSize) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                            activePoints = listOf(Offset((start.x / w).coerceIn(0f, 1f), (start.y / h).coerceIn(0f, 1f)))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                            val pt = Offset(
                                (change.position.x / w).coerceIn(0f, 1f),
                                (change.position.y / h).coerceIn(0f, 1f)
                            )
                            activePoints = activePoints + pt
                        },
                        onDragEnd = {
                            if (activePoints.isNotEmpty()) {
                                onStrokesChanged(strokes + MaskStroke(activePoints, brushSizeFraction))
                                activePoints = emptyList()
                            }
                        },
                        onDragCancel = {
                            activePoints = emptyList()
                        }
                    )
                }
                .testTag("inpainting_mask_canvas")
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Source image to edit",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val allToDraw = if (activePoints.isNotEmpty()) {
                    strokes + MaskStroke(activePoints, brushSizeFraction)
                } else {
                    strokes
                }
                allToDraw.forEach { stroke ->
                    if (stroke.points.isEmpty()) return@forEach
                    val widthPx = (stroke.brushFraction * size.width).coerceAtLeast(10f)
                    if (stroke.points.size == 1) {
                        val pt = stroke.points.first()
                        drawCircle(
                            color = maskColor,
                            radius = widthPx / 2f,
                            center = Offset(pt.x * size.width, pt.y * size.height)
                        )
                    } else {
                        val path = Path().apply {
                            val first = stroke.points.first()
                            moveTo(first.x * size.width, first.y * size.height)
                            for (i in 1 until stroke.points.size) {
                                val pt = stroke.points[i]
                                lineTo(pt.x * size.width, pt.y * size.height)
                            }
                        }
                        drawPath(
                            path = path,
                            color = maskColor,
                            style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
            // Corner HUD indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(SwissInk)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (strokes.isEmpty()) "PAINT MASK TO INPAINT (OR LEAVE EMPTY FOR FULL EDIT)"
                    else "MASK // ${strokes.size} STROKE${if (strokes.size == 1) "" else "S"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissWhite
                )
            }
        }

        // Brush Size + Undo + Clear Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SwissPaper)
                .border(1.dp, SwissInk, RectangleShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("BRUSH", style = MaterialTheme.typography.labelSmall, color = SwissMuted)
            Slider(
                value = brushSizeFraction,
                onValueChange = { brushSizeFraction = it },
                valueRange = 0.025f..0.16f,
                colors = SliderDefaults.colors(
                    thumbColor = SwissSignalRed,
                    activeTrackColor = SwissSignalRed
                ),
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .border(1.dp, SwissInk, RectangleShape)
                    .clickable(enabled = strokes.isNotEmpty()) {
                        onStrokesChanged(strokes.dropLast(1))
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("inpaint_undo_stroke")
            ) {
                Text("UNDO", style = MaterialTheme.typography.labelSmall, color = if (strokes.isNotEmpty()) SwissInk else SwissMuted)
            }
            Box(
                modifier = Modifier
                    .background(if (strokes.isNotEmpty()) SwissSignalRed else SwissPaper)
                    .border(1.dp, SwissInk, RectangleShape)
                    .clickable(enabled = strokes.isNotEmpty()) { onStrokesChanged(emptyList()) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("inpaint_clear_mask")
            ) {
                Text("CLEAR", style = MaterialTheme.typography.labelSmall, color = if (strokes.isNotEmpty()) SwissWhite else SwissMuted)
            }
        }
    }
}
