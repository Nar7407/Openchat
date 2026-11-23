package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.DecisionMatrixData
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissGridLine
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint
import com.example.ui.theme.SwissWhite
import com.example.util.MathExpressionEvaluator
import java.util.Locale

// 1. Desmos-Style Interactive Graphing Calculator & Live Plotter
@Composable
fun InteractiveGraphPlotterDialog(
    initialExpression: String = "sin(a * x) + b * x",
    onDismiss: () -> Unit,
    onAskAiExplain: (String) -> Unit
) {
    var expr by remember { mutableStateOf(initialExpression.ifBlank { "a * sin(b * x) + c" }) }
    var paramA by remember { mutableFloatStateOf(1.5f) }
    var paramB by remember { mutableFloatStateOf(1.0f) }
    var paramC by remember { mutableFloatStateOf(0.0f) }
    var xRange by remember { mutableFloatStateOf(10f) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("graph_plotter_dialog"),
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
                        text = "GRAPH PLOTTER // LIVE 2D f(x)",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close plotter", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = expr,
                        onValueChange = { expr = it },
                        label = { Text("f(x) =  (supports sin, cos, tan, exp, ln, sqrt, ^, a, b, c)") },
                        singleLine = true,
                        colors = swissTextFieldColors(),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth().testTag("plotter_expr_input")
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("a*sin(b*x)+c", "a*x^2+b*x+c", "exp(-0.2*x^2)*cos(a*x)").forEach { preset ->
                            SwissToggleCell(
                                label = preset.take(12),
                                selected = expr == preset,
                                onClick = { expr = preset },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(SwissPaper)
                            .border(1.5.dp, SwissInk)
                    ) {
                        val w = size.width
                        val h = size.height
                        val midX = w / 2f
                        val midY = h / 2f
                        val yRange = xRange * (h / w.coerceAtLeast(1f))

                        // Gridlines
                        val gridSteps = 8
                        for (i in 1 until gridSteps) {
                            val gx = w * i / gridSteps
                            val gy = h * i / gridSteps
                            drawLine(SwissGridLine, Offset(gx, 0f), Offset(gx, h), 1f)
                            drawLine(SwissGridLine, Offset(0f, gy), Offset(w, gy), 1f)
                        }
                        // Axes
                        drawLine(SwissInk, Offset(0f, midY), Offset(w, midY), 2f)
                        drawLine(SwissInk, Offset(midX, 0f), Offset(midX, h), 2f)

                        val samples = 240
                        val path = Path()
                        var started = false
                        for (i in 0..samples) {
                            val t = i.toDouble() / samples
                            val x = -xRange + (2.0 * xRange * t)
                            val y = MathExpressionEvaluator.evaluate(expr, x, paramA.toDouble(), paramB.toDouble(), paramC.toDouble())
                            if (y.isFinite() && kotlin.math.abs(y) < yRange * 4.0) {
                                val px = ((x + xRange) / (2.0 * xRange) * w).toFloat()
                                val py = (midY - (y / yRange) * (h / 2.0)).toFloat()
                                if (!started) {
                                    path.moveTo(px, py)
                                    started = true
                                } else {
                                    path.lineTo(px, py)
                                }
                            } else {
                                started = false
                            }
                        }
                        drawPath(path, color = SwissSignalRed, style = Stroke(width = 4f))
                    }

                    PlotParamSlider("a", paramA, -5f..5f) { paramA = it }
                    PlotParamSlider("b", paramB, -5f..5f) { paramB = it }
                    PlotParamSlider("c", paramC, -5f..5f) { paramC = it }
                    PlotParamSlider("Zoom ±X", xRange, 2f..25f) { xRange = it }

                    SwissButton(
                        text = "ASK AI TO ANALYZE FUNCTION",
                        onClick = {
                            onAskAiExplain(
                                String.format(
                                    Locale.US,
                                    "Analyze f(x) = %s with a=%.2f, b=%.2f, c=%.2f: find roots, derivative, extrema, and asymptotic behavior.",
                                    expr, paramA, paramB, paramC
                                )
                            )
                            onDismiss()
                        },
                        leadingIcon = Icons.Default.Send,
                        variant = SwissButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PlotParamSlider(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = String.format(Locale.US, "%s = %.2f", name, value),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
            color = SwissInk,
            modifier = Modifier.padding(end = 8.dp)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = SwissSignalRed, activeTrackColor = SwissInk),
            modifier = Modifier.weight(1f)
        )
    }
}

// 2. Local CSV Instant Chart Explorer (Bar / Line / Scatter / Pie)
@Composable
fun CsvChartExplorerDialog(
    onDismiss: () -> Unit,
    onAskAiInsights: (String) -> Unit
) {
    val context = LocalContext.current
    var rawCsv by remember {
        mutableStateOf("Month,Revenue,Expenses\nJan,4200,2600\nFeb,5100,2900\nMar,4850,2750\nApr,6300,3100\nMay,7100,3400")
    }
    var chartType by remember { mutableStateOf("BAR") }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { text ->
                if (text.isNotBlank()) rawCsv = text.take(15_000)
            }
        }
    }

    val parsed = remember(rawCsv) { parseCsvNumericSeries(rawCsv) }
    val palette = listOf(SwissSignalRed, SwissInk, Color(0xFF2A9D8F), Color(0xFFE9C46A), Color(0xFF457B9D), Color(0xFF8D5B4C))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("csv_explorer_dialog"),
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
                        text = "CSV STUDIO // INSTANT CHART EXPLORER",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close CSV studio", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwissButton(
                            text = "LOAD .CSV FILE",
                            onClick = { csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) },
                            leadingIcon = Icons.Default.FileOpen,
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "ASK AI INSIGHTS",
                            onClick = {
                                onAskAiInsights("Analyze this CSV dataset and summarize trends, outliers, and key KPIs:\n```csv\n${rawCsv.take(3500)}\n```")
                                onDismiss()
                            },
                            leadingIcon = Icons.Default.Send,
                            variant = SwissButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("BAR", "LINE", "SCATTER", "PIE").forEach { mode ->
                            SwissToggleCell(
                                label = mode,
                                selected = chartType == mode,
                                onClick = { chartType = mode },
                                useSignalRedWhenSelected = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (parsed.points.isNotEmpty()) {
                        val maxVal = parsed.points.maxOf { it.second }.coerceAtLeast(1.0)
                        val sumVal = parsed.points.sumOf { it.second.coerceAtLeast(0.0) }.coerceAtLeast(1.0)
                        Text(
                            text = "SERIES: ${parsed.valueColumn} (${parsed.points.size} rows · Max ${String.format(Locale.US, "%.1f", maxVal)})",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                            color = SwissInk
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(SwissPaper)
                                .border(1.5.dp, SwissInk)
                                .padding(12.dp)
                        ) {
                            val w = size.width
                            val h = size.height
                            val pts = parsed.points.take(18)
                            when (chartType) {
                                "BAR" -> {
                                    val barW = (w / pts.size.coerceAtLeast(1)) * 0.65f
                                    val step = w / pts.size.coerceAtLeast(1)
                                    pts.forEachIndexed { idx, (_, v) ->
                                        val bh = ((v / maxVal).toFloat().coerceIn(0f, 1f)) * (h - 12f)
                                        drawRect(
                                            color = palette[idx % palette.size],
                                            topLeft = Offset(idx * step + (step - barW) / 2f, h - bh),
                                            size = Size(barW, bh)
                                        )
                                    }
                                }
                                "LINE" -> {
                                    val path = Path()
                                    val step = w / (pts.size - 1).coerceAtLeast(1)
                                    pts.forEachIndexed { idx, (_, v) ->
                                        val px = idx * step
                                        val py = h - ((v / maxVal).toFloat().coerceIn(0f, 1f)) * (h - 16f) - 8f
                                        if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                        drawCircle(SwissInk, radius = 5f, center = Offset(px, py))
                                    }
                                    drawPath(path, SwissSignalRed, style = Stroke(width = 4f))
                                }
                                "SCATTER" -> {
                                    val step = w / pts.size.coerceAtLeast(1)
                                    pts.forEachIndexed { idx, (_, v) ->
                                        val px = idx * step + step / 2f
                                        val py = h - ((v / maxVal).toFloat().coerceIn(0f, 1f)) * (h - 16f) - 8f
                                        drawCircle(palette[idx % palette.size], radius = 8f, center = Offset(px, py))
                                    }
                                }
                                else -> { // PIE
                                    val diameter = minOf(w, h) - 16f
                                    val topLeft = Offset((w - diameter) / 2f, (h - diameter) / 2f)
                                    var startAngle = -90f
                                    pts.forEachIndexed { idx, (_, v) ->
                                        val sweep = ((v.coerceAtLeast(0.0) / sumVal) * 360.0).toFloat()
                                        drawArc(
                                            color = palette[idx % palette.size],
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = true,
                                            topLeft = topLeft,
                                            size = Size(diameter, diameter)
                                        )
                                        startAngle += sweep
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            parsed.points.take(5).forEachIndexed { i, (lbl, v) ->
                                Text(
                                    text = "$lbl: ${String.format(Locale.US, "%.0f", v)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = palette[i % palette.size]
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = rawCsv,
                        onValueChange = { rawCsv = it },
                        label = { Text("Paste or edit CSV rows") },
                        minLines = 4,
                        maxLines = 6,
                        colors = swissTextFieldColors(),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private data class CsvSeries(
    val valueColumn: String,
    val points: List<Pair<String, Double>>
)

private fun parseCsvNumericSeries(csv: String): CsvSeries {
    val lines = csv.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size < 2) return CsvSeries("Value", emptyList())
    val headers = lines.first().split(",").map { it.trim() }
    val rows = lines.drop(1).map { line -> line.split(",").map { it.trim() } }
    val numCol = (1 until headers.size).firstOrNull { col ->
        rows.any { row -> row.getOrNull(col)?.toDoubleOrNull() != null }
    } ?: return CsvSeries("Value", emptyList())
    val pts = rows.mapNotNull { row ->
        val label = row.firstOrNull()?.ifBlank { "?" } ?: return@mapNotNull null
        val num = row.getOrNull(numCol)?.toDoubleOrNull() ?: return@mapNotNull null
        label to num
    }
    return CsvSeries(headers.getOrElse(numCol) { "Value" }, pts)
}

// 3. Weighted Pros/Cons Side-by-Side Decision Matrix Card
@Composable
fun DecisionMatrixDialog(
    initialPrompt: String,
    matrix: DecisionMatrixData?,
    isBuilding: Boolean,
    onBuildMatrix: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf(initialPrompt.ifBlank { "Compare MacBook Air M3 vs ThinkPad X1 Carbon for coding" }) }
    var weights by remember(matrix) {
        mutableStateOf(matrix?.criteria?.map { it.weight } ?: emptyList())
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, SwissInk, RectangleShape)
                .background(SwissCanvas, RectangleShape)
                .testTag("decision_matrix_dialog"),
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
                        text = "DECISION MATRIX // WEIGHTED COMPARE",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold),
                        color = SwissWhite
                    )
                    SwissIconCell(icon = Icons.Default.Close, contentDescription = "Close decision matrix", onClick = onDismiss, inverted = true, bordered = false)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("What are you comparing? (e.g. Option A vs Option B)") },
                        singleLine = true,
                        colors = swissTextFieldColors(),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth().testTag("decision_query_input")
                    )

                    SwissButton(
                        text = if (isBuilding) "ANALYZING…" else "GENERATE WEIGHTED MATRIX",
                        onClick = { onBuildMatrix(query) },
                        enabled = query.isNotBlank() && !isBuilding,
                        leadingIcon = Icons.Default.Scale,
                        variant = SwissButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth().testTag("decision_generate_btn")
                    )

                    if (isBuilding) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = SwissSignalRed, modifier = Modifier.padding(end = 10.dp))
                            Text("Scoring options across criteria…", style = MaterialTheme.typography.bodySmall, color = SwissMuted)
                        }
                    }

                    if (matrix != null) {
                        val totalW = weights.sum().coerceAtLeast(1f)
                        val weightedA = matrix.criteria.mapIndexed { i, c -> c.scoreA * (weights.getOrElse(i) { c.weight }) }.sum() / totalW
                        val weightedB = matrix.criteria.mapIndexed { i, c -> c.scoreB * (weights.getOrElse(i) { c.weight }) }.sum() / totalW
                        val winner = when {
                            weightedA > weightedB + 0.05f -> matrix.optionA
                            weightedB > weightedA + 0.05f -> matrix.optionB
                            else -> "TIE"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().background(SwissInk).padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(matrix.optionA.uppercase(), style = MaterialTheme.typography.labelSmall, color = SwissCanvas)
                                Text(String.format(Locale.US, "%.2f / 10", weightedA), style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissWhite)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LIVE WINNER", style = MaterialTheme.typography.labelSmall, color = SwissSignalRed)
                                Text(winner, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = SwissWhite)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(matrix.optionB.uppercase(), style = MaterialTheme.typography.labelSmall, color = SwissCanvas)
                                Text(String.format(Locale.US, "%.2f / 10", weightedB), style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold), color = SwissWhite)
                            }
                        }

                        matrix.criteria.forEachIndexed { idx, crit ->
                            val w = weights.getOrElse(idx) { crit.weight }
                            Column(
                                modifier = Modifier.fillMaxWidth().background(SwissPaper).border(1.dp, SwissInk).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(crit.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = SwissInk)
                                    Text("WEIGHT: ${w.toInt()}/10", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily), color = SwissSignalRed)
                                }
                                Slider(
                                    value = w,
                                    onValueChange = { next ->
                                        weights = weights.toMutableList().also { list ->
                                            if (idx < list.size) list[idx] = next
                                        }
                                    },
                                    valueRange = 1f..10f,
                                    steps = 8,
                                    colors = SliderDefaults.colors(thumbColor = SwissSignalRed, activeTrackColor = SwissInk)
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(modifier = Modifier.weight(1f).background(SwissVerifiedTint).padding(6.dp)) {
                                        Text("${matrix.optionA}: ${crit.scoreA}/10", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SwissVerifiedInk)
                                        if (crit.noteA.isNotBlank()) Text(crit.noteA, style = MaterialTheme.typography.bodySmall, color = SwissInk)
                                    }
                                    Column(modifier = Modifier.weight(1f).background(SwissCanvas).padding(6.dp)) {
                                        Text("${matrix.optionB}: ${crit.scoreB}/10", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SwissInk)
                                        if (crit.noteB.isNotBlank()) Text(crit.noteB, style = MaterialTheme.typography.bodySmall, color = SwissInk)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
