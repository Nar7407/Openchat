package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FreezeSelectTool {
    CIRCLE,
    BOX,
    MULTI_BOX
}

private data class QuadBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * Freeze-frame interactive canvas overlay for Live Lens.
 * Supports:
 * - Freehand Finger Circle (`CIRCLE`)
 * - Single Crop Box (`BOX`)
 * - Multi-Box Batch Selection (`MULTI_BOX`: `[Q1]`, `[Q2]`, `[Q3]`...) stitching all drawn boxes
 *   into one clean labeled vertical composite image for a single vision pass.
 */
@Composable
fun LiveLensFreezeSelectOverlay(
    frozenFrame: Bitmap,
    actionLabel: String,
    onAnalyzeCropped: (cropped: Bitmap, wasCropped: Boolean, multiBoxCount: Int) -> Unit,
    onAnalyzeFullFrame: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tool by remember { mutableStateOf(FreezeSelectTool.CIRCLE) }
    var circlePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var boxStart by remember { mutableStateOf<Offset?>(null) }
    var boxEnd by remember { mutableStateOf<Offset?>(null) }
    var multiBoxes by remember { mutableStateOf<List<QuadBounds>>(emptyList()) }
    var activeMultiStart by remember { mutableStateOf<Offset?>(null) }
    var activeMultiEnd by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    val hasSelection = when (tool) {
        FreezeSelectTool.CIRCLE -> circlePoints.size >= 4
        FreezeSelectTool.BOX -> {
            val s = boxStart
            val e = boxEnd
            s != null && e != null && abs(e.x - s.x) >= 18f && abs(e.y - s.y) >= 18f
        }
        FreezeSelectTool.MULTI_BOX -> multiBoxes.isNotEmpty()
    }

    fun mapCanvasQuadToBitmapRect(quad: QuadBounds): Rect? {
        val cw = canvasSize.width.coerceAtLeast(1).toFloat()
        val ch = canvasSize.height.coerceAtLeast(1).toFloat()
        if ((quad.right - quad.left) < 18f || (quad.bottom - quad.top) < 18f) return null

        val bw = frozenFrame.width.toFloat()
        val bh = frozenFrame.height.toFloat()
        val scale = max(cw / bw, ch / bh)
        val drawnW = bw * scale
        val drawnH = bh * scale
        val offsetX = (cw - drawnW) * 0.5f
        val offsetY = (ch - drawnH) * 0.5f

        val padX = ((quad.right - quad.left) * 0.06f).coerceAtLeast(8f)
        val padY = ((quad.bottom - quad.top) * 0.06f).coerceAtLeast(8f)

        val bmpLeft = (((quad.left - padX) - offsetX) / scale).roundToInt().coerceIn(0, frozenFrame.width - 2)
        val bmpTop = (((quad.top - padY) - offsetY) / scale).roundToInt().coerceIn(0, frozenFrame.height - 2)
        val bmpRight = (((quad.right + padX) - offsetX) / scale).roundToInt().coerceIn(bmpLeft + 2, frozenFrame.width)
        val bmpBottom = (((quad.bottom + padY) - offsetY) / scale).roundToInt().coerceIn(bmpTop + 2, frozenFrame.height)

        if (bmpRight - bmpLeft < 16 || bmpBottom - bmpTop < 16) return null
        return Rect(bmpLeft, bmpTop, bmpRight, bmpBottom)
    }

    fun computeSingleSelectionRect(): Rect? {
        val quad = when (tool) {
            FreezeSelectTool.CIRCLE -> {
                if (circlePoints.size < 4) return null
                var lx = Float.MAX_VALUE
                var ty = Float.MAX_VALUE
                var rx = Float.MIN_VALUE
                var by = Float.MIN_VALUE
                circlePoints.forEach { p ->
                    lx = min(lx, p.x)
                    ty = min(ty, p.y)
                    rx = max(rx, p.x)
                    by = max(by, p.y)
                }
                QuadBounds(lx, ty, rx, by)
            }
            FreezeSelectTool.BOX -> {
                val s = boxStart ?: return null
                val e = boxEnd ?: return null
                QuadBounds(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y))
            }
            FreezeSelectTool.MULTI_BOX -> return null
        }
        return mapCanvasQuadToBitmapRect(quad)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SwissInk)
            .testTag("live_lens_freeze_overlay")
    ) {
        Image(
            bitmap = frozenFrame.asImageBitmap(),
            contentDescription = "Frozen camera frame for region selection",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
        )

        // Touch-enabled selection canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tool) {
                    detectDragGestures(
                        onDragStart = { start ->
                            when (tool) {
                                FreezeSelectTool.CIRCLE -> {
                                    boxStart = null
                                    boxEnd = null
                                    circlePoints = listOf(start)
                                }
                                FreezeSelectTool.BOX -> {
                                    circlePoints = emptyList()
                                    boxStart = start
                                    boxEnd = start
                                }
                                FreezeSelectTool.MULTI_BOX -> {
                                    activeMultiStart = start
                                    activeMultiEnd = start
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pos = Offset(
                                x = change.position.x.coerceIn(0f, size.width.toFloat()),
                                y = change.position.y.coerceIn(0f, size.height.toFloat())
                            )
                            when (tool) {
                                FreezeSelectTool.CIRCLE -> circlePoints = circlePoints + pos
                                FreezeSelectTool.BOX -> boxEnd = pos
                                FreezeSelectTool.MULTI_BOX -> activeMultiEnd = pos
                            }
                        },
                        onDragEnd = {
                            if (tool == FreezeSelectTool.MULTI_BOX) {
                                val s = activeMultiStart
                                val e = activeMultiEnd
                                if (s != null && e != null && abs(e.x - s.x) >= 22f && abs(e.y - s.y) >= 22f) {
                                    val newBox = QuadBounds(
                                        left = min(s.x, e.x),
                                        top = min(s.y, e.y),
                                        right = max(s.x, e.x),
                                        bottom = max(s.y, e.y)
                                    )
                                    multiBoxes = (multiBoxes + newBox).take(6)
                                }
                                activeMultiStart = null
                                activeMultiEnd = null
                            }
                        }
                    )
                }
                .testTag("live_lens_freeze_canvas")
        ) {
            val scrim = Color.Black.copy(alpha = 0.42f)

            when (tool) {
                FreezeSelectTool.CIRCLE -> {
                    if (circlePoints.size >= 2) {
                        var lx = Float.MAX_VALUE
                        var ty = Float.MAX_VALUE
                        var rx = Float.MIN_VALUE
                        var by = Float.MIN_VALUE
                        val path = Path().apply {
                            val f = circlePoints.first()
                            moveTo(f.x, f.y)
                            circlePoints.forEach { p ->
                                lx = min(lx, p.x)
                                ty = min(ty, p.y)
                                rx = max(rx, p.x)
                                by = max(by, p.y)
                                lineTo(p.x, p.y)
                            }
                        }
                        drawRect(scrim, Offset(0f, 0f), Size(size.width, ty))
                        drawRect(scrim, Offset(0f, by), Size(size.width, (size.height - by).coerceAtLeast(0f)))
                        drawRect(scrim, Offset(0f, ty), Size(lx, (by - ty).coerceAtLeast(0f)))
                        drawRect(scrim, Offset(rx, ty), Size((size.width - rx).coerceAtLeast(0f), (by - ty).coerceAtLeast(0f)))

                        drawRect(
                            color = SwissWhite.copy(alpha = 0.75f),
                            topLeft = Offset(lx, ty),
                            size = Size((rx - lx).coerceAtLeast(2f), (by - ty).coerceAtLeast(2f)),
                            style = Stroke(width = 2.5f)
                        )
                        drawPath(
                            path = path,
                            color = SwissSignalRed,
                            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    } else {
                        drawRect(Color.Black.copy(alpha = 0.22f))
                    }
                }
                FreezeSelectTool.BOX -> {
                    val s = boxStart
                    val e = boxEnd
                    if (s != null && e != null) {
                        val l = min(s.x, e.x)
                        val t = min(s.y, e.y)
                        val r = max(s.x, e.x)
                        val b = max(s.y, e.y)
                        drawRect(scrim, Offset(0f, 0f), Size(size.width, t))
                        drawRect(scrim, Offset(0f, b), Size(size.width, (size.height - b).coerceAtLeast(0f)))
                        drawRect(scrim, Offset(0f, t), Size(l, (b - t).coerceAtLeast(0f)))
                        drawRect(scrim, Offset(r, t), Size((size.width - r).coerceAtLeast(0f), (b - t).coerceAtLeast(0f)))

                        drawRect(
                            color = SwissSignalRed,
                            topLeft = Offset(l, t),
                            size = Size((r - l).coerceAtLeast(2f), (b - t).coerceAtLeast(2f)),
                            style = Stroke(width = 4.5f)
                        )
                        val corner = 18f
                        val sw = 6f
                        drawLine(SwissWhite, Offset(l, t), Offset(l + corner, t), sw)
                        drawLine(SwissWhite, Offset(l, t), Offset(l, t + corner), sw)
                        drawLine(SwissWhite, Offset(r, b), Offset(r - corner, b), sw)
                        drawLine(SwissWhite, Offset(r, b), Offset(r, b - corner), sw)
                    } else {
                        drawRect(Color.Black.copy(alpha = 0.22f))
                    }
                }
                FreezeSelectTool.MULTI_BOX -> {
                    drawRect(Color.Black.copy(alpha = 0.24f))
                    multiBoxes.forEach { q ->
                        drawRect(
                            color = SwissSignalRed.copy(alpha = 0.12f),
                            topLeft = Offset(q.left, q.top),
                            size = Size((q.right - q.left).coerceAtLeast(2f), (q.bottom - q.top).coerceAtLeast(2f))
                        )
                        drawRect(
                            color = SwissSignalRed,
                            topLeft = Offset(q.left, q.top),
                            size = Size((q.right - q.left).coerceAtLeast(2f), (q.bottom - q.top).coerceAtLeast(2f)),
                            style = Stroke(width = 4.5f)
                        )
                    }
                    val s = activeMultiStart
                    val e = activeMultiEnd
                    if (s != null && e != null) {
                        val l = min(s.x, e.x)
                        val t = min(s.y, e.y)
                        val r = max(s.x, e.x)
                        val b = max(s.y, e.y)
                        drawRect(
                            color = SwissWhite,
                            topLeft = Offset(l, t),
                            size = Size((r - l).coerceAtLeast(2f), (b - t).coerceAtLeast(2f)),
                            style = Stroke(width = 3.5f)
                        )
                    }
                }
            }
        }

        // Numbered [Q1], [Q2], [Q3] Badges for Multi-Box Crop Mode
        if (tool == FreezeSelectTool.MULTI_BOX) {
            multiBoxes.forEachIndexed { idx, box ->
                Text(
                    text = "[Q${idx + 1}]",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SwissWhite,
                    modifier = Modifier
                        .offset { IntOffset(box.left.roundToInt(), (box.top - 26f).coerceAtLeast(8f).roundToInt()) }
                        .background(SwissSignalRed, RectangleShape)
                        .border(1.dp, SwissWhite, RectangleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("multi_box_badge_${idx + 1}")
                )
            }
        }

        // Top Freeze HUD Header
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(SwissInk.copy(alpha = 0.92f))
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (tool == FreezeSelectTool.MULTI_BOX) {
                            "MULTI-CROP // ${multiBoxes.size} BOX${if (multiBoxes.size == 1) "" else "ES"} QUEUED (UP TO 6)"
                        } else {
                            "FRAME FROZEN // CIRCLE, BOX, OR MULTI-CROP"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SwissSignalRed
                    )
                    Text(
                        text = if (tool == FreezeSelectTool.MULTI_BOX) {
                            "Drag separate boxes ([Q1], [Q2], [Q3]) — stitched into 1 labeled composite sheet"
                        } else {
                            "Draw around one equation, circuit, or error line — clutter is cropped out"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissWhite
                    )
                }
                SwissIconCell(
                    icon = Icons.Filled.Close,
                    contentDescription = "Discard frozen frame",
                    onClick = onCancel,
                    inverted = true,
                    cellSize = 40.dp,
                    modifier = Modifier.testTag("freeze_select_cancel")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FreezeToolChip(
                    label = "CIRCLE",
                    icon = Icons.Outlined.Gesture,
                    selected = tool == FreezeSelectTool.CIRCLE,
                    onClick = {
                        tool = FreezeSelectTool.CIRCLE
                        boxStart = null
                        boxEnd = null
                        multiBoxes = emptyList()
                    },
                    testTag = "freeze_tool_circle",
                    modifier = Modifier.weight(1f)
                )
                FreezeToolChip(
                    label = "BOX",
                    icon = Icons.Outlined.CropFree,
                    selected = tool == FreezeSelectTool.BOX,
                    onClick = {
                        tool = FreezeSelectTool.BOX
                        circlePoints = emptyList()
                        multiBoxes = emptyList()
                    },
                    testTag = "freeze_tool_box",
                    modifier = Modifier.weight(1f)
                )
                FreezeToolChip(
                    label = if (multiBoxes.isNotEmpty()) "MULTI (${multiBoxes.size})" else "MULTI Q1+",
                    icon = Icons.Outlined.GridView,
                    selected = tool == FreezeSelectTool.MULTI_BOX,
                    onClick = {
                        tool = FreezeSelectTool.MULTI_BOX
                        circlePoints = emptyList()
                        boxStart = null
                        boxEnd = null
                    },
                    testTag = "freeze_tool_multi_box",
                    modifier = Modifier.weight(1.15f)
                )
                if (tool == FreezeSelectTool.MULTI_BOX && multiBoxes.isNotEmpty()) {
                    SwissIconCell(
                        icon = Icons.AutoMirrored.Outlined.Undo,
                        contentDescription = "Undo last box",
                        onClick = { multiBoxes = multiBoxes.dropLast(1) },
                        inverted = true,
                        cellSize = 36.dp,
                        modifier = Modifier.testTag("freeze_undo_box")
                    )
                }
                SwissIconCell(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "Clear selection",
                    onClick = {
                        circlePoints = emptyList()
                        boxStart = null
                        boxEnd = null
                        multiBoxes = emptyList()
                    },
                    inverted = true,
                    cellSize = 36.dp,
                    modifier = Modifier.testTag("freeze_clear_selection")
                )
            }
        }

        // Bottom Confirmation Dock
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(SwissInk.copy(alpha = 0.94f))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwissButton(
                text = "Full Frame",
                variant = SwissButtonVariant.OUTLINE,
                onClick = onAnalyzeFullFrame,
                modifier = Modifier
                    .weight(1f)
                    .testTag("freeze_analyze_full")
            )
            SwissButton(
                text = when {
                    tool == FreezeSelectTool.MULTI_BOX && multiBoxes.isNotEmpty() ->
                        "Stitch ${multiBoxes.size} & $actionLabel"
                    hasSelection -> "Crop & $actionLabel"
                    else -> actionLabel
                },
                variant = SwissButtonVariant.PRIMARY,
                onClick = {
                    if (tool == FreezeSelectTool.MULTI_BOX && multiBoxes.isNotEmpty()) {
                        val mappedRects = multiBoxes.mapNotNull { mapCanvasQuadToBitmapRect(it) }
                        if (mappedRects.isNotEmpty()) {
                            val composite = stitchMultiBoxComposite(frozenFrame, mappedRects)
                            onAnalyzeCropped(composite, true, mappedRects.size)
                        } else {
                            onAnalyzeFullFrame()
                        }
                    } else {
                        val rect = computeSingleSelectionRect()
                        if (rect != null) {
                            val cropped = Bitmap.createBitmap(
                                frozenFrame,
                                rect.left,
                                rect.top,
                                rect.width(),
                                rect.height()
                            )
                            onAnalyzeCropped(cropped, true, 1)
                        } else {
                            onAnalyzeFullFrame()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1.45f)
                    .testTag("freeze_analyze_selection")
            )
        }
    }
}

@Composable
private fun FreezeToolChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(if (selected) SwissSignalRed else Color.Transparent, RectangleShape)
            .border(1.dp, if (selected) SwissSignalRed else SwissWhite.copy(alpha = 0.55f), RectangleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 6.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = SwissWhite, modifier = Modifier.size(14.dp))
        Text(
            text = " $label",
            style = MaterialTheme.typography.labelSmall,
            color = SwissWhite,
            maxLines = 1
        )
    }
}

/**
 * Stitches multiple numbered bounding boxes ([Q1], [Q2], [Q3]...) from a frozen frame into one
 * clean high-contrast labeled vertical composite image so the vision model solves all questions in 1 pass.
 */
private fun stitchMultiBoxComposite(source: Bitmap, rects: List<Rect>): Bitmap {
    if (rects.size == 1) {
        val r = rects.first()
        return Bitmap.createBitmap(source, r.left, r.top, r.width(), r.height())
    }
    val crops = rects.map { r ->
        Bitmap.createBitmap(source, r.left, r.top, r.width(), r.height())
    }
    val targetWidth = crops.maxOf { it.width }.coerceIn(480, 1280)
    val headerHeight = 42
    val gap = 14
    val pad = 12

    val scaledCrops = crops.map { bmp ->
        val scale = (targetWidth - pad * 2).toFloat() / bmp.width.coerceAtLeast(1).toFloat()
        val drawW = (bmp.width * scale).roundToInt().coerceAtLeast(64)
        val drawH = (bmp.height * scale).roundToInt().coerceIn(48, 720)
        Bitmap.createScaledBitmap(bmp, drawW, drawH, true)
    }

    val totalHeight = scaledCrops.sumOf { headerHeight + it.height + gap } + pad * 2
    val composite = Bitmap.createBitmap(targetWidth, totalHeight.coerceAtLeast(128), Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(composite)
    canvas.drawColor(AndroidColor.WHITE)

    val headerBgPaint = Paint().apply {
        color = AndroidColor.rgb(17, 17, 17)
        style = Paint.Style.FILL
    }
    val badgePaint = Paint().apply {
        color = AndroidColor.rgb(230, 0, 0)
        style = Paint.Style.FILL
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 24f
        isFakeBoldText = true
    }
    val borderPaint = Paint().apply {
        color = AndroidColor.rgb(17, 17, 17)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    var cursorY = pad
    scaledCrops.forEachIndexed { idx, crop ->
        // Header strip with [Q1], [Q2], ... label
        canvas.drawRect(pad.toFloat(), cursorY.toFloat(), (targetWidth - pad).toFloat(), (cursorY + headerHeight).toFloat(), headerBgPaint)
        canvas.drawRect(pad.toFloat(), cursorY.toFloat(), (pad + 96).toFloat(), (cursorY + headerHeight).toFloat(), badgePaint)
        canvas.drawText("[Q${idx + 1}]", (pad + 16).toFloat(), (cursorY + 30).toFloat(), textPaint)
        canvas.drawText("REGION ${idx + 1} OF ${scaledCrops.size}", (pad + 114).toFloat(), (cursorY + 30).toFloat(), textPaint)
        cursorY += headerHeight

        canvas.drawBitmap(crop, pad.toFloat(), cursorY.toFloat(), null)
        canvas.drawRect(
            pad.toFloat(),
            cursorY.toFloat(),
            (pad + crop.width).toFloat(),
            (cursorY + crop.height).toFloat(),
            borderPaint
        )
        cursorY += crop.height + gap
    }
    return composite
}
