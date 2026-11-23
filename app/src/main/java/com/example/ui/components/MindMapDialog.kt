package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.MindMapNode
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissGridLine
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import com.example.util.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

private data class PositionedMindNode(
    val node: MindMapNode,
    val depth: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val parent: PositionedMindNode?
)

private object MindMapLayoutEngine {
    const val COL_GAP = 340f
    const val ROW_GAP = 104f
    const val NODE_W = 240f
    const val NODE_H = 68f

    fun layout(root: MindMapNode): Pair<List<PositionedMindNode>, Size> {
        val result = mutableListOf<PositionedMindNode>()
        var nextLeafY = 80f

        fun place(node: MindMapNode, depth: Int, parent: PositionedMindNode?): PositionedMindNode {
            val x = 60f + depth * COL_GAP
            val placeholder = PositionedMindNode(node, depth, x, 0f, NODE_W, NODE_H, parent)
            if (node.children.isEmpty()) {
                val placed = placeholder.copy(y = nextLeafY)
                nextLeafY += ROW_GAP
                result += placed
                return placed
            }
            val childPositions = node.children.map { child -> place(child, depth + 1, null) }
            val centerY = (childPositions.first().y + childPositions.last().y) / 2f
            val placed = placeholder.copy(y = centerY)
            result += placed
            // Re-link children parent reference in result list
            val childIds = childPositions.map { it.node.id }.toSet()
            for (i in result.indices) {
                if (result[i].node.id in childIds && result[i].depth == depth + 1) {
                    result[i] = result[i].copy(parent = placed)
                }
            }
            return placed
        }

        place(root, 0, null)
        val maxX = (result.maxOfOrNull { it.x + it.width } ?: 600f) + 120f
        val maxY = (result.maxOfOrNull { it.y + it.height } ?: 400f) + 120f
        return result to Size(maxX, maxY)
    }

    fun exportToSvg(nodes: List<PositionedMindNode>, bounds: Size): String = buildString {
        appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="${bounds.width.toInt()}" height="${bounds.height.toInt()}" viewBox="0 0 ${bounds.width.toInt()} ${bounds.height.toInt()}">""")
        appendLine("""  <rect width="100%" height="100%" fill="#EFEFEF" />""")
        // Connectors
        nodes.forEach { n ->
            val p = n.parent ?: return@forEach
            val sx = p.x + p.width
            val sy = p.y + p.height / 2f
            val tx = n.x
            val ty = n.y + n.height / 2f
            val midX = (sx + tx) / 2f
            appendLine("""  <path d="M $sx $sy C $midX $sy, $midX $ty, $tx $ty" fill="none" stroke="#111111" stroke-width="2" />""")
        }
        // Boxes + Labels
        nodes.forEach { n ->
            val fill = when (n.depth) {
                0 -> "#FF3B30"
                1 -> "#111111"
                else -> "#FFFFFF"
            }
            val textColor = if (n.depth <= 1) "#FFFFFF" else "#111111"
            val escaped = n.node.label
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            appendLine("""  <rect x="${n.x}" y="${n.y}" width="${n.width}" height="${n.height}" fill="$fill" stroke="#111111" stroke-width="2" />""")
            appendLine("""  <text x="${n.x + 12}" y="${n.y + 38}" fill="$textColor" font-family="monospace" font-size="14" font-weight="bold">$escaped</text>""")
        }
        appendLine("</svg>")
    }

    fun exportToPngBytes(nodes: List<PositionedMindNode>, bounds: Size): ByteArray {
        val w = bounds.width.toInt().coerceIn(800, 3200)
        val h = bounds.height.toInt().coerceIn(600, 3200)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.parseColor("#EFEFEF"))

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#111111")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        nodes.forEach { n ->
            val p = n.parent ?: return@forEach
            val path = android.graphics.Path().apply {
                val sx = p.x + p.width
                val sy = p.y + p.height / 2f
                val tx = n.x
                val ty = n.y + n.height / 2f
                val midX = (sx + tx) / 2f
                moveTo(sx, sy)
                cubicTo(midX, sy, midX, ty, tx, ty)
            }
            canvas.drawPath(path, linePaint)
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.parseColor("#111111")
            strokeWidth = 3f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            typeface = Typeface.MONOSPACE
        }

        nodes.forEach { n ->
            fillPaint.color = when (n.depth) {
                0 -> android.graphics.Color.parseColor("#FF3B30")
                1 -> android.graphics.Color.parseColor("#111111")
                else -> android.graphics.Color.WHITE
            }
            textPaint.color = if (n.depth <= 1) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#111111")
            canvas.drawRect(n.x, n.y, n.x + n.width, n.y + n.height, fillPaint)
            canvas.drawRect(n.x, n.y, n.x + n.width, n.y + n.height, strokePaint)
            val label = if (n.node.label.length > 22) n.node.label.take(20) + "…" else n.node.label
            canvas.drawText(label, n.x + 14f, n.y + n.height / 2f + 8f, textPaint)
        }

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}

@Composable
fun MindMapDialog(
    rootNode: MindMapNode,
    isExpandingNode: Boolean = false,
    onExpandSubTopic: ((MindMapNode) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (positioned, bounds) = remember(rootNode) { MindMapLayoutEngine.layout(rootNode) }

    var scale by remember { mutableFloatStateOf(0.85f) }
    var pan by remember { mutableStateOf(Offset(20f, 20f)) }
    var selectedNodeId by remember { mutableStateOf(rootNode.id) }
    val selectedNode = remember(rootNode, selectedNodeId) {
        positioned.firstOrNull { it.node.id == selectedNodeId }?.node ?: rootNode
    }
    var exportNotice by remember { mutableStateOf<String?>(null) }

    val inkColor = SwissInk
    val signalRed = SwissSignalRed
    val paperColor = SwissPaper
    val whiteColor = SwissWhite
    val gridColor = SwissGridLine

    val svgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val svg = MindMapLayoutEngine.exportToSvg(positioned, bounds)
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(svg.toByteArray(Charsets.UTF_8)) }
                }.onSuccess {
                    exportNotice = "EXPORT // Saved Mind Map SVG."
                }.onFailure {
                    exportNotice = "ERR // Failed to save SVG."
                }
            }
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
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissInk)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("VISUALIZER // INTERACTIVE MIND MAP", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                        Text(rootNode.label.uppercase(), style = MaterialTheme.typography.titleSmall, color = SwissWhite)
                    }
                    Box(
                        modifier = Modifier
                            .border(1.dp, SwissWhite, RectangleShape)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("close_mindmap_dialog")
                    ) {
                        Text("[ CLOSE ]", style = MaterialTheme.typography.labelSmall, color = SwissWhite)
                    }
                }

                // Controls Bar (Zoom + Export SVG + Export PNG)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper)
                        .border(1.dp, SwissInk, RectangleShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable { scale = (scale - 0.15f).coerceIn(0.35f, 2.5f) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("[-]", style = MaterialTheme.typography.labelSmall, color = SwissInk) }
                        Box(
                            modifier = Modifier
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable { scale = 0.85f; pan = Offset(20f, 20f) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = SwissInk) }
                        Box(
                            modifier = Modifier
                                .border(1.dp, SwissInk, RectangleShape)
                                .clickable { scale = (scale + 0.15f).coerceIn(0.35f, 2.5f) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("[+]", style = MaterialTheme.typography.labelSmall, color = SwissInk) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .background(SwissInk)
                                .clickable { svgLauncher.launch("allchat_mindmap.svg") }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("export_mindmap_svg")
                        ) {
                            Text("SVG", style = MaterialTheme.typography.labelSmall, color = SwissWhite)
                        }
                        Box(
                            modifier = Modifier
                                .background(SwissSignalRed)
                                .clickable {
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            val pngBytes = MindMapLayoutEngine.exportToPngBytes(positioned, bounds)
                                            val tmp = File(context.cacheDir, "mindmap_${System.currentTimeMillis()}.png")
                                            FileOutputStream(tmp).use { it.write(pngBytes) }
                                            MediaStoreSaver.saveImageToGallery(context, tmp.absolutePath, "allchat_mindmap_${System.currentTimeMillis()}.png")
                                        }
                                        exportNotice = res.fold(
                                            onSuccess = { "EXPORT // Saved Mind Map PNG to Gallery." },
                                            onFailure = { "ERR // ${it.message ?: "PNG export failed"}" }
                                        )
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("export_mindmap_png")
                        ) {
                            Text("PNG", style = MaterialTheme.typography.labelSmall, color = SwissWhite)
                        }
                    }
                }

                if (!exportNotice.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissSignalRed)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(exportNotice!!, style = MaterialTheme.typography.labelSmall, color = SwissWhite)
                    }
                }

                // Interactive Pan & Pinch-to-Zoom Mind Map Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(SwissCanvas)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                scale = (scale * zoomChange).coerceIn(0.35f, 2.5f)
                                pan += panChange
                            }
                        }
                        .pointerInput(positioned, scale, pan) {
                            detectTapGestures { tapOffset ->
                                val graphX = (tapOffset.x - pan.x) / scale
                                val graphY = (tapOffset.y - pan.y) / scale
                                val hit = positioned.firstOrNull { n ->
                                    graphX in n.x..(n.x + n.width) && graphY in n.y..(n.y + n.height)
                                }
                                if (hit != null) selectedNodeId = hit.node.id
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Subtle drafting grid
                        val step = 36f
                        var gx = 0f
                        while (gx < size.width) {
                            drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                            gx += step
                        }
                        var gy = 0f
                        while (gy < size.height) {
                            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                            gy += step
                        }

                        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = 24f
                            typeface = Typeface.MONOSPACE
                            isFakeBoldText = true
                        }

                        withTransform({
                            translate(left = pan.x, top = pan.y)
                            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                        }) {
                            // Draw Bezier Connectors
                            positioned.forEach { n ->
                                val p = n.parent ?: return@forEach
                                val sx = p.x + p.width
                                val sy = p.y + p.height / 2f
                                val tx = n.x
                                val ty = n.y + n.height / 2f
                                val midX = (sx + tx) / 2f
                                val path = Path().apply {
                                    moveTo(sx, sy)
                                    cubicTo(midX, sy, midX, ty, tx, ty)
                                }
                                drawPath(path = path, color = inkColor, style = Stroke(width = 2.5f))
                            }

                            // Draw Nodes
                            positioned.forEach { n ->
                                val isSel = selectedNode?.id == n.node.id
                                val fill = when (n.depth) {
                                    0 -> signalRed
                                    1 -> inkColor
                                    else -> paperColor
                                }
                                drawRect(color = fill, topLeft = Offset(n.x, n.y), size = Size(n.width, n.height))
                                drawRect(
                                    color = if (isSel) signalRed else inkColor,
                                    topLeft = Offset(n.x, n.y),
                                    size = Size(n.width, n.height),
                                    style = Stroke(width = if (isSel) 4.5f else 2f)
                                )

                                textPaint.color = if (n.depth <= 1) whiteColor.toArgb() else inkColor.toArgb()
                                val label = if (n.node.label.length > 20) n.node.label.take(18) + "…" else n.node.label
                                drawContext.canvas.nativeCanvas.drawText(
                                    label,
                                    n.x + 12f,
                                    n.y + n.height / 2f + 8f,
                                    textPaint
                                )
                            }
                        }
                    }
                }

                // Selected Node Inspector Footer + Dynamic Sub-Topic Expansion
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SwissInk))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NODE // ${selectedNode.label}",
                            style = MaterialTheme.typography.labelLarge,
                            color = SwissInk,
                            modifier = Modifier.weight(1f)
                        )
                        if (onExpandSubTopic != null) {
                            Box(
                                modifier = Modifier
                                    .background(if (isExpandingNode) SwissInk else SwissSignalRed, RectangleShape)
                                    .border(1.dp, SwissInk, RectangleShape)
                                    .clickable(enabled = !isExpandingNode) {
                                        onExpandSubTopic(selectedNode)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("mindmap_expand_subtopic_btn")
                            ) {
                                Text(
                                    text = if (isExpandingNode) "EXPANDING…" else "+ EXPAND THIS SUB-TOPIC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SwissWhite
                                )
                            }
                        }
                    }
                    Text(
                        text = selectedNode.detail.takeIf { it.isNotBlank() }
                            ?: "Drag to pan, pinch to zoom, tap any node to inspect or dynamically grow child branches.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SwissMuted
                    )
                }
            }
        }
    }
}
