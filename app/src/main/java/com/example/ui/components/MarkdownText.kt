package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite
import com.example.util.MarkdownBlock
import com.example.util.MarkdownParser
import kotlinx.coroutines.delay

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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

@Composable
private fun CodeBlockCard(
    language: String,
    code: String
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800L)
            copied = false
        }
    }

    Surface(
        color = SwissSubtleFill,
        shape = RectangleShape,
        border = BorderStroke(1.dp, SwissInk),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Stark obsidian header bar with instant-invert copy cell
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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
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
