package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SearchCitation
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSignalRedTint
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite
import com.example.util.MarkdownBlock
import com.example.util.MarkdownParser
import com.example.util.OverleafLatexExporter
import kotlinx.coroutines.delay

private sealed interface MarkdownSegment {
    data class Content(val markdown: String) : MarkdownSegment
    data class Thinking(val text: String, val isUnclosed: Boolean) : MarkdownSegment
}

private fun splitThinkingSegments(raw: String): List<MarkdownSegment> {
    if (!raw.contains("<think>", ignoreCase = true) && !raw.contains("<reasoning>", ignoreCase = true)) {
        return listOf(MarkdownSegment.Content(raw))
    }
    val segments = mutableListOf<MarkdownSegment>()
    var cursor = 0
    val lower = raw.lowercase()
    while (cursor < raw.length) {
        val thinkStart = lower.indexOf("<think>", cursor)
        val reasonStart = lower.indexOf("<reasoning>", cursor)
        val nextStart = listOf(thinkStart, reasonStart).filter { it >= 0 }.minOrNull()
        if (nextStart == null) {
            val tail = raw.substring(cursor).trim()
            if (tail.isNotEmpty()) segments += MarkdownSegment.Content(tail)
            break
        }
        if (nextStart > cursor) {
            val before = raw.substring(cursor, nextStart).trim()
            if (before.isNotEmpty()) segments += MarkdownSegment.Content(before)
        }
        val isReasonTag = (nextStart == reasonStart)
        val openLen = if (isReasonTag) "<reasoning>".length else "<think>".length
        val closeTag = if (isReasonTag) "</reasoning>" else "</think>"
        val closeIdx = lower.indexOf(closeTag, nextStart + openLen)
        if (closeIdx >= 0) {
            val inner = raw.substring(nextStart + openLen, closeIdx).trim()
            if (inner.isNotEmpty()) segments += MarkdownSegment.Thinking(inner, isUnclosed = false)
            cursor = closeIdx + closeTag.length
        } else {
            val inner = raw.substring(nextStart + openLen).trim()
            if (inner.isNotEmpty()) segments += MarkdownSegment.Thinking(inner, isUnclosed = true)
            break
        }
    }
    return segments
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    showThinking: Boolean = true,
    thinkingDurationSec: Int? = null
) {
    val segments = remember(markdown) { splitThinkingSegments(markdown) }
    val hasMathOrDerivation = remember(markdown) { OverleafLatexExporter.containsMathOrDerivation(markdown) }
    val clipboard = LocalClipboardManager.current
    var copiedTex by remember { mutableStateOf(false) }

    LaunchedEffect(copiedTex) {
        if (copiedTex) {
            delay(2000L)
            copiedTex = false
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { seg ->
            when (seg) {
                is MarkdownSegment.Thinking -> {
                    if (showThinking) {
                        ThinkingTraceBlock(
                            thoughtText = seg.text,
                            isStreaming = seg.isUnclosed,
                            durationSec = thinkingDurationSec
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SwissSubtleFill)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "THOUGHT TRACE HIDDEN // RUN /thinking TO SHOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissMuted
                            )
                        }
                    }
                }
                is MarkdownSegment.Content -> {
                    val blocks = remember(seg.markdown) { MarkdownParser.parse(seg.markdown) }
                    blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = buildInlineAnnotatedString(block.text),
                        style = style,
                        color = SwissInk
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildInlineAnnotatedString(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SwissInk
                    )
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.labelLarge,
                            color = SwissSignalRed,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = buildInlineAnnotatedString(block.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = SwissInk,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = block.number,
                            style = MaterialTheme.typography.labelLarge,
                            color = SwissSignalRed,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = buildInlineAnnotatedString(block.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = SwissInk,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(SwissSubtleFill)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(SwissSignalRed)
                        )
                        Text(
                            text = buildInlineAnnotatedString(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SwissMuted,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(
                        language = block.language,
                        code = block.code
                    )
                }
            }
                    }
                }
            }
        }

        if (hasMathOrDerivation) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissSubtleFill)
                    .border(1.dp, SwissInk, RectangleShape)
                    .clickable {
                        val texDoc = OverleafLatexExporter.buildOverleafDocument(markdown)
                        clipboard.setText(AnnotatedString(texDoc))
                        copiedTex = true
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("copy_overleaf_tex_button"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LATEX // OVERLEAF EXPORT TEMPLATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissMuted
                )
                Text(
                    text = if (copiedTex) "[ COPIED .TEX ]" else "[ COPY OVERLEAF .TEX ]",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (copiedTex) SwissSignalRed else SwissInk
                )
            }
        }
    }
}

@Composable
private fun ThinkingTraceBlock(
    thoughtText: String,
    isStreaming: Boolean,
    durationSec: Int? = null
) {
    // Open while actively streaming; automatically collapses once finished!
    var expanded by remember(isStreaming) { mutableStateOf(isStreaming) }
    val headerLabel = when {
        isStreaming -> "THINKING // LIVE REASONING…"
        durationSec != null && durationSec > 0 -> "THOUGHT FOR ${durationSec}S"
        else -> "THOUGHT FOR <1S // REASONING TRACE"
    }

    Surface(
        color = SwissSubtleFill,
        shape = RectangleShape,
        border = BorderStroke(1.dp, SwissInk),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissConcrete)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("thinking_accordion_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = headerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isStreaming) SwissSignalRed else SwissInk
                )
                Text(
                    text = if (expanded) "[ HIDE ]" else "[ EXPAND ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissMuted
                )
            }
            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SwissInk)
                )
                Text(
                    text = thoughtText,
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissMuted,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CitationPillsRow(
    citations: List<SearchCitation>,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (citations.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "SOURCES // ${citations.size} GROUNDED CITATION${if (citations.size == 1) "" else "S"}",
            style = MaterialTheme.typography.labelSmall,
            color = SwissMuted
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            citations.forEach { cite ->
                Row(
                    modifier = Modifier
                        .background(SwissSignalRedTint, RectangleShape)
                        .border(1.dp, SwissSignalRed, RectangleShape)
                        .clickable { onOpenUrl(cite.url) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("citation_pill_${cite.index}"),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[${cite.index}]",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SwissSignalRed
                    )
                    Text(
                        text = cite.title.take(30),
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var showFullscreenPreview by remember { mutableStateOf(false) }
    var showInlinePreview by remember { mutableStateOf(false) }
    var showRunner by remember { mutableStateOf(false) }
    var showChart by remember { mutableStateOf(false) }
    val canPreview = remember(language, code) { ArtifactDetector.isPreviewable(language, code) }
    val canRun = remember(language) { CodeSandboxDetector.isRunnableCode(language) }
    val isChartOrMermaid = remember(language) { CodeSandboxDetector.isChartOrDiagram(language) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800L)
            copied = false
        }
    }

    if (showFullscreenPreview) {
        HtmlArtifactPreviewDialog(
            language = language,
            code = code,
            onDismiss = { showFullscreenPreview = false }
        )
    }
    if (showRunner) {
        CodeInterpreterDialog(
            language = language,
            code = code,
            onDismiss = { showRunner = false }
        )
    }
    if (showChart) {
        ChartDiagramDialog(
            language = language,
            code = code,
            onDismiss = { showChart = false }
        )
    }

    Surface(
        color = SwissSubtleFill,
        shape = RectangleShape,
        border = BorderStroke(1.dp, SwissInk),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Stark obsidian header bar with instant-invert run / chart / inline preview & copy cells
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissInk),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SRC // ${(language.ifBlank { "PLAIN" }).uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissCanvas,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isChartOrMermaid) {
                        Box(
                            modifier = Modifier
                                .background(SwissSignalRed)
                                .clickable { showChart = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("render_chart_button")
                        ) {
                            Text(
                                text = if (language.equals("mermaid", ignoreCase = true)) "[ DIAGRAM ]" else "[ CHART ]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite
                            )
                        }
                    } else if (canPreview) {
                        Box(
                            modifier = Modifier
                                .background(if (showInlinePreview) SwissPaper else SwissSignalRed)
                                .clickable { showInlinePreview = !showInlinePreview }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("preview_code_button")
                        ) {
                            Text(
                                text = if (showInlinePreview) "[ HIDE PREVIEW ]" else "[ ▶ PREVIEW ]",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (showInlinePreview) SwissInk else SwissWhite
                            )
                        }
                    } else if (canRun) {
                        Box(
                            modifier = Modifier
                                .background(SwissSignalRed)
                                .clickable { showRunner = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("run_code_button")
                        ) {
                            Text(
                                text = "[ RUN ]",
                                style = MaterialTheme.typography.labelSmall,
                                color = SwissWhite
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(if (copied) SwissSignalRed else SwissInk)
                            .clickable {
                                clipboard.setText(AnnotatedString(code))
                                copied = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("copy_code_button")
                    ) {
                        Text(
                            text = if (copied) "[ COPIED ]" else "[ COPY ]",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissWhite
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SwissInk)
            )
            Text(
                text = code,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = SwissInk,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
            if (canPreview && showInlinePreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SwissInk)
                )
                InlineSandboxPreviewPanel(
                    language = language,
                    code = code,
                    onExpandFullscreen = { showFullscreenPreview = true },
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun buildInlineAnnotatedString(text: String): AnnotatedString {
    val inlineCodeBg = SwissConcrete
    val inlineCodeColor = SwissInk
    return remember(text) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                when {
                    text.startsWith("**", i) -> {
                        val end = text.indexOf("**", i + 2)
                        if (end != -1) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(text.substring(i + 2, end))
                            }
                            i = end + 2
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                    text.startsWith("`", i) -> {
                        val end = text.indexOf("`", i + 1)
                        if (end != -1) {
                            withStyle(
                                SpanStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.5.sp,
                                    color = inlineCodeColor,
                                    background = inlineCodeBg
                                )
                            ) {
                                append(" ${text.substring(i + 1, end)} ")
                            }
                            i = end + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                    text.startsWith("*", i) && !text.startsWith("**", i) -> {
                        val end = text.indexOf("*", i + 1)
                        if (end != -1 && end > i + 1) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(text.substring(i + 1, end))
                            }
                            i = end + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                    else -> {
                        append(text[i])
                        i++
                    }
                }
            }
        }
    }
}
