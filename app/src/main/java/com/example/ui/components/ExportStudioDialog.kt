package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissWhite
import com.example.util.MultiFormatExporter

@Composable
fun ExportStudioDialog(
    title: String,
    markdownContent: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var statusText by remember { mutableStateOf<String?>(null) }

    val safeName = remember(title) {
        title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "allchat_export" }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        MultiFormatExporter.writePdf(out, title, markdownContent)
                    }
                }.onSuccess {
                    statusText = "EXPORT // PDF document saved cleanly."
                }.onFailure { err ->
                    statusText = "ERR // ${err.message ?: "PDF save failed."}"
                }
            }
        }
    )

    val saveDocxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        MultiFormatExporter.writeDocx(out, title, markdownContent)
                    }
                }.onSuccess {
                    statusText = "EXPORT // Word (.docx) document saved cleanly."
                }.onFailure { err ->
                    statusText = "ERR // ${err.message ?: "DOCX save failed."}"
                }
            }
        }
    )

    val saveMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    val notionMd = MultiFormatExporter.formatNotionMarkdown(title, markdownContent)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(notionMd.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    statusText = "EXPORT // Notion Markdown (.md) saved cleanly."
                }.onFailure { err ->
                    statusText = "ERR // ${err.message ?: "Markdown save failed."}"
                }
            }
        }
    )

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
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("EXPORT STUDIO // MULTI-FORMAT", style = MaterialTheme.typography.labelLarge, color = SwissWhite)
                        Text(title, style = MaterialTheme.typography.labelSmall, color = SwissCanvas, maxLines = 1)
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissPaper)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (statusText != null) {
                        Text(
                            text = statusText!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissVerifiedInk
                        )
                    }

                    Text("SAVE TO DEVICE", style = MaterialTheme.typography.labelSmall, color = SwissMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SwissButton(
                            text = "Save PDF",
                            leadingIcon = Icons.Outlined.PictureAsPdf,
                            variant = SwissButtonVariant.PRIMARY,
                            minHeight = 46.dp,
                            onClick = { savePdfLauncher.launch("$safeName.pdf") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_save_pdf")
                        )
                        SwissButton(
                            text = "Save DOCX",
                            leadingIcon = Icons.Outlined.Description,
                            variant = SwissButtonVariant.INK,
                            minHeight = 46.dp,
                            onClick = { saveDocxLauncher.launch("$safeName.docx") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_save_docx")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SwissButton(
                            text = "Notion .MD",
                            leadingIcon = Icons.Outlined.Description,
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 44.dp,
                            onClick = { saveMdLauncher.launch("${safeName}_notion.md") },
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "Copy Notion",
                            leadingIcon = Icons.Outlined.ContentCopy,
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 44.dp,
                            onClick = {
                                val md = MultiFormatExporter.formatNotionMarkdown(title, markdownContent)
                                clipboard.setText(AnnotatedString(md))
                                statusText = "COPIED // Notion-ready Markdown copied to clipboard."
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    SwissRuleDivider(symbol = "/")

                    Text("ANDROID SHARE SHEET (WHATSAPP · GMAIL · DRIVE)", style = MaterialTheme.typography.labelSmall, color = SwissMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SwissButton(
                            text = "Share PDF",
                            leadingIcon = Icons.Outlined.Share,
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 44.dp,
                            onClick = {
                                MultiFormatExporter.exportToCacheAndShare(context, title, markdownContent, "pdf")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "Share DOCX",
                            leadingIcon = Icons.Outlined.Share,
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 44.dp,
                            onClick = {
                                MultiFormatExporter.exportToCacheAndShare(context, title, markdownContent, "docx")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SwissButton(
                            text = "Share Text",
                            leadingIcon = Icons.Outlined.Share,
                            variant = SwissButtonVariant.OUTLINE,
                            minHeight = 44.dp,
                            onClick = {
                                MultiFormatExporter.sharePlainText(context, title, markdownContent)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
