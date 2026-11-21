package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object MultiFormatExporter {

    fun formatNotionMarkdown(title: String, markdownContent: String, subtitle: String = "AllChat Export"): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine("> **Source:** $subtitle  ")
            appendLine("> **Exported:** $dateStr")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine(markdownContent.trim())
        }
    }

    fun writePdf(outputStream: OutputStream, title: String, markdownContent: String) {
        val pdf = PdfDocument()
        val pageWidth = 595 // A4 pt
        val pageHeight = 842
        val margin = 42f
        val contentWidth = pageWidth - margin * 2

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF3B30")
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            textSize = 13.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            textSize = 10.5f
            typeface = Typeface.SANS_SERIF
        }
        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EFEFEF")
            textSize = 9.2f
            typeface = Typeface.MONOSPACE
        }
        val codeBgPaint = Paint().apply {
            color = Color.parseColor("#181818")
            style = Paint.Style.FILL
        }
        val rulePaint = Paint().apply {
            color = Color.parseColor("#111111")
            strokeWidth = 1f
        }

        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin

        fun drawPageHeader(isFirst: Boolean) {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            canvas.drawText("ALLCHAT // ARCHIVAL EXPORT · P.$pageNum · $stamp", margin, y, metaPaint)
            y += 12f
            canvas.drawLine(margin, y, pageWidth - margin, y, rulePaint)
            y += 18f
            if (isFirst) {
                wrapText(title, titlePaint, contentWidth).forEach { line ->
                    canvas.drawText(line, margin, y, titlePaint)
                    y += 22f
                }
                y += 8f
            }
        }

        fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > pageHeight - margin) {
                pdf.finishPage(page)
                pageNum++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin
                drawPageHeader(isFirst = false)
            }
        }

        drawPageHeader(isFirst = true)

        val cleaned = markdownContent
            .replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
            .trim()
        val lines = cleaned.lines()
        var inCodeBlock = false
        var codeLang = ""

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true
                    codeLang = trimmed.removePrefix("```").trim().uppercase().ifBlank { "CODE" }
                    ensureSpace(22f)
                    canvas.drawRect(margin, y - 10f, pageWidth - margin, y + 6f, codeBgPaint)
                    canvas.drawText("[$codeLang]", margin + 8f, y + 1f, metaPaint)
                    y += 16f
                } else {
                    inCodeBlock = false
                    y += 8f
                }
                continue
            }

            if (inCodeBlock) {
                val wrapped = wrapText(rawLine.ifEmpty { " " }, codePaint, contentWidth - 16f)
                for (wl in wrapped) {
                    ensureSpace(14f)
                    canvas.drawRect(margin, y - 10f, pageWidth - margin, y + 4f, codeBgPaint)
                    canvas.drawText(wl, margin + 8f, y, codePaint)
                    y += 13.5f
                }
                continue
            }

            if (trimmed.isEmpty()) {
                y += 7f
                continue
            }

            when {
                trimmed.startsWith("#") -> {
                    val headingText = trimmed.trimStart('#').trim()
                    val wrapped = wrapText(headingText, headingPaint, contentWidth)
                    y += 4f
                    for (wl in wrapped) {
                        ensureSpace(18f)
                        canvas.drawText(wl, margin, y, headingPaint)
                        y += 17f
                    }
                    y += 2f
                }
                else -> {
                    val plain = trimmed
                        .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
                        .replace(Regex("""`([^`]+)`"""), "$1")
                    val wrapped = wrapText(plain, bodyPaint, contentWidth)
                    for (wl in wrapped) {
                        ensureSpace(15f)
                        canvas.drawText(wl, margin, y, bodyPaint)
                        y += 14.5f
                    }
                }
            }
        }

        pdf.finishPage(page)
        pdf.writeTo(outputStream)
        pdf.close()
    }

    fun writeDocx(outputStream: OutputStream, title: String, markdownContent: String) {
        val cleaned = markdownContent
            .replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "")
            .trim()
        val bodyXml = buildString {
            append(docxParagraph(title, bold = true, sizeHalfPt = 32))
            append(docxParagraph("Exported from AllChat", bold = false, sizeHalfPt = 18, colorHex = "666666"))
            var inCode = false
            for (line in cleaned.lines()) {
                val t = line.trim()
                if (t.startsWith("```")) {
                    inCode = !inCode
                    continue
                }
                if (inCode) {
                    append(docxParagraph(line, bold = false, sizeHalfPt = 18, mono = true, colorHex = "111111"))
                } else if (t.startsWith("#")) {
                    append(docxParagraph(t.trimStart('#').trim(), bold = true, sizeHalfPt = 26))
                } else {
                    val plain = t
                        .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
                        .replace(Regex("""`([^`]+)`"""), "$1")
                    append(docxParagraph(plain, bold = false, sizeHalfPt = 22))
                }
            }
        }

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    $bodyXml
    <w:sectPr/>
  </w:body>
</w:document>"""

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        ZipOutputStream(outputStream).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(contentTypesXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(relsXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun exportToCacheAndShare(
        context: Context,
        title: String,
        markdownContent: String,
        format: String // "pdf" | "docx" | "md"
    ) {
        val safeName = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "allchat_export" }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "${safeName}_${System.currentTimeMillis()}.$format")
        val mime = when (format) {
            "pdf" -> {
                FileOutputStream(file).use { writePdf(it, title, markdownContent) }
                "application/pdf"
            }
            "docx" -> {
                FileOutputStream(file).use { writeDocx(it, title, markdownContent) }
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            }
            else -> {
                file.writeText(formatNotionMarkdown(title, markdownContent), Charsets.UTF_8)
                "text/markdown"
            }
        }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share $title ($format)"))
    }

    fun sharePlainText(context: Context, title: String, text: String, targetPackage: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
        runCatching {
            if (targetPackage != null) {
                context.startActivity(intent)
            } else {
                context.startActivity(Intent.createChooser(intent, "Share via…"))
            }
        }.onFailure {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(fallback, "Share via…"))
        }
    }

    private fun docxParagraph(
        text: String,
        bold: Boolean = false,
        sizeHalfPt: Int = 22,
        mono: Boolean = false,
        colorHex: String = "111111"
    ): String {
        val escaped = escapeXml(text)
        val fontXml = if (mono) """<w:rFonts w:ascii="Courier New" w:hAnsi="Courier New"/>""" else ""
        val boldXml = if (bold) "<w:b/>" else ""
        return """<w:p><w:r><w:rPr>$fontXml$boldXml<w:color w:val="$colorHex"/><w:sz w:val="$sizeHalfPt"/></w:rPr><w:t xml:space="preserve">$escaped</w:t></w:r></w:p>"""
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null).coerceAtLeast(1)
            var end = start + count
            if (end < text.length) {
                val lastSpace = text.lastIndexOf(' ', end - 1)
                if (lastSpace > start) end = lastSpace + 1
            }
            result += text.substring(start, end).trimEnd()
            start = end
        }
        return result
    }
}
