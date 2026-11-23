package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.model.ExpenseRecord
import com.example.domain.model.ProviderType
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// 1. Visual Color Palette & WCAG Contrast Extractor
data class PaletteSwatch(
    val hex: String,
    val r: Int,
    val g: Int,
    val b: Int,
    val ratioOnWhite: Double,
    val ratioOnBlack: Double
) {
    val wcagLabel: String
        get() = when {
            ratioOnWhite >= 7.0 || ratioOnBlack >= 7.0 -> "AAA"
            ratioOnWhite >= 4.5 || ratioOnBlack >= 4.5 -> "AA"
            else -> "LARGE"
        }
}

object ColorPaletteExtractor {
    fun extractDominantColors(bitmap: Bitmap, count: Int = 6): List<PaletteSwatch> {
        val scaled = Bitmap.createScaledBitmap(bitmap, 72, 72, true)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        if (scaled !== bitmap) scaled.recycle()

        // Quantize RGB into 5-bit buckets to find top distinct colors fast on-device
        val counts = HashMap<Int, Int>()
        val sumR = HashMap<Int, Long>()
        val sumG = HashMap<Int, Long>()
        val sumB = HashMap<Int, Long>()
        for (px in pixels) {
            if (Color.alpha(px) < 128) continue
            val r = Color.red(px)
            val g = Color.green(px)
            val b = Color.blue(px)
            val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            counts[key] = (counts[key] ?: 0) + 1
            sumR[key] = (sumR[key] ?: 0L) + r
            sumG[key] = (sumG[key] ?: 0L) + g
            sumB[key] = (sumB[key] ?: 0L) + b
        }

        val sortedBuckets = counts.entries.sortedByDescending { it.value }
        val chosen = mutableListOf<Triple<Int, Int, Int>>()
        for ((key, cnt) in sortedBuckets) {
            val r = (sumR[key]!! / cnt).toInt().coerceIn(0, 255)
            val g = (sumG[key]!! / cnt).toInt().coerceIn(0, 255)
            val b = (sumB[key]!! / cnt).toInt().coerceIn(0, 255)
            val tooClose = chosen.any { (cr, cg, cb) ->
                val dist = sqrt(((r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb)).toDouble())
                dist < 42.0
            }
            if (!tooClose) {
                chosen += Triple(r, g, b)
                if (chosen.size >= count) break
            }
        }
        return chosen.map { (r, g, b) ->
            val lum = relativeLuminance(r, g, b)
            val onWhite = (1.0 + 0.05) / (lum + 0.05)
            val onBlack = (lum + 0.05) / 0.05
            PaletteSwatch(
                hex = String.format(Locale.US, "#%02X%02X%02X", r, g, b),
                r = r,
                g = g,
                b = b,
                ratioOnWhite = onWhite,
                ratioOnBlack = onBlack
            )
        }
    }

    private fun relativeLuminance(r: Int, g: Int, b: Int): Double {
        fun lin(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    fun toTailwindConfig(swatches: List<PaletteSwatch>): String = buildString {
        appendLine("theme: {")
        appendLine("  extend: {")
        appendLine("    colors: {")
        swatches.forEachIndexed { i, s ->
            appendLine("      brand${i + 1}: '${s.hex}',")
        }
        appendLine("    }")
        appendLine("  }")
        append("}")
    }

    fun toCssVariables(swatches: List<PaletteSwatch>): String = buildString {
        appendLine(":root {")
        swatches.forEachIndexed { i, s ->
            appendLine("  --palette-${i + 1}: ${s.hex}; /* rgb(${s.r}, ${s.g}, ${s.b}) */")
        }
        append("}")
    }
}

// 2. Privacy-First Document & Text Redactor (Local Regex PII Masker)
data class RedactionReport(
    val redactedText: String,
    val phonesMasked: Int,
    val emailsMasked: Int,
    val idsMasked: Int,
    val upiAndBankMasked: Int
) {
    val totalMasked: Int get() = phonesMasked + emailsMasked + idsMasked + upiAndBankMasked
}

object PrivacyRedactor {
    private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val UPI_REGEX = Regex("""\b[a-zA-Z0-9._-]{2,}@(?:okicici|okhdfcbank|okaxis|oksbi|ybl|ibl|axl|paytm|upi|apl|fbl)\b""", RegexOption.IGNORE_CASE)
    private val AADHAAR_REGEX = Regex("""\b\d{4}[\s-]\d{4}[\s-]\d{4}\b""")
    private val PAN_REGEX = Regex("""\b[A-Z]{5}\d{4}[A-Z]\b""")
    private val CARD_REGEX = Regex("""\b(?:\d{4}[\s-]?){3}\d{4}\b""")
    private val PHONE_REGEX = Regex("""(?<!\d)(?:\+?\d{1,3}[\s-]?)?(?:\(?\d{3}\)?[\s-]?\d{3}[\s-]?\d{4}|\d{10})(?!\d)""")

    fun redact(raw: String): RedactionReport {
        var text = raw
        var upiBank = 0
        var emails = 0
        var ids = 0
        var phones = 0

        text = UPI_REGEX.replace(text) { upiBank++; "[REDACTED_UPI]" }
        text = EMAIL_REGEX.replace(text) { emails++; "[REDACTED_EMAIL]" }
        text = AADHAAR_REGEX.replace(text) { ids++; "[REDACTED_AADHAAR]" }
        text = PAN_REGEX.replace(text) { ids++; "[REDACTED_PAN]" }
        text = CARD_REGEX.replace(text) { upiBank++; "[REDACTED_CARD]" }
        text = PHONE_REGEX.replace(text) { phones++; "[REDACTED_PHONE]" }

        return RedactionReport(text, phones, emails, ids, upiBank)
    }
}

// 3. Prompt Compression ("Token Diet")
data class CompressionResult(
    val compressedText: String,
    val originalChars: Int,
    val compressedChars: Int
) {
    val savedPercent: Int
        get() = if (originalChars <= 0) 0 else (((originalChars - compressedChars) * 100.0) / originalChars).toInt().coerceIn(0, 95)
}

object PromptCompressor {
    private val FILLER_PHRASES = listOf(
        Regex("""\b(?:please\s+note\s+that|it\s+is\s+important\s+to\s+note\s+that|as\s+a\s+matter\s+of\s+fact|in\s+order\s+to|for\s+the\s+purpose\s+of|at\s+this\s+point\s+in\s+time)\b""", RegexOption.IGNORE_CASE),
        Regex("""[-=_*]{5,}""")
    )

    fun compress(input: String): CompressionResult {
        if (input.length < 80) return CompressionResult(input, input.length, input.length)
        val normalizedLines = input
            .replace("\r\n", "\n")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .lines()
            .map { it.trimEnd() }
        val deduped = buildList {
            for (line in normalizedLines) {
                if (isEmpty() || last() != line) add(line)
            }
        }
        var out = deduped.joinToString("\n")
        for (rx in FILLER_PHRASES) {
            out = rx.replace(out, " ")
        }
        out = out.replace(Regex("""[ \t]{2,}"""), " ").trim()
        return CompressionResult(out, input.length, out.length)
    }
}

// 4. Paste-a-cURL Auto-Importer for Provider Settings
data class ParsedCurlProvider(
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val apiKey: String,
    val modelId: String?
)

object CurlImporter {
    fun parse(rawCurl: String): ParsedCurlProvider? {
        val cleaned = rawCurl
            .replace("\\\n", " ")
            .replace("\\\r\n", " ")
            .trim()
        val urlMatch = Regex("""https?://[^\s'"\\]+""").find(cleaned) ?: return null
        val fullUrl = urlMatch.value.trimEnd('\'', '"', ',')
        val lowerUrl = fullUrl.lowercase(Locale.US)

        val type = when {
            lowerUrl.contains("generativelanguage.googleapis.com") -> ProviderType.GEMINI
            lowerUrl.contains("anthropic.com") -> ProviderType.ANTHROPIC
            else -> ProviderType.OPENAI_COMPATIBLE
        }

        val baseUrl = fullUrl
            .replace(Regex("""/chat/completions.*$"""), "")
            .replace(Regex("""/completions.*$"""), "")
            .replace(Regex("""/v1/messages.*$"""), "")
            .replace(Regex("""/models/[^/?]+:.*$"""), "")
            .trimEnd('/')

        val bearer = Regex("""Authorization:\s*Bearer\s+([^\s'"]+)""", RegexOption.IGNORE_CASE).find(cleaned)?.groupValues?.getOrNull(1)
        val xApiKey = Regex("""(?:x-api-key|x-goog-api-key):\s*([^\s'"]+)""", RegexOption.IGNORE_CASE).find(cleaned)?.groupValues?.getOrNull(1)
        val queryKey = Regex("""[?&]key=([^&\s'"]+)""").find(fullUrl)?.groupValues?.getOrNull(1)
        val apiKey = (bearer ?: xApiKey ?: queryKey).orEmpty()

        val modelId = Regex(""""model"\s*:\s*"([^"]+)"""").find(cleaned)?.groupValues?.getOrNull(1)
            ?: Regex("""/models/([^/:?]+)""").find(fullUrl)?.groupValues?.getOrNull(1)

        val host = runCatching { java.net.URI(baseUrl).host.orEmpty() }.getOrDefault("")
        val inferredName = host
            .removePrefix("api.")
            .substringBefore('.')
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "Custom Provider" }

        return ParsedCurlProvider(
            name = inferredName,
            type = type,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId
        )
    }
}

// 5. Real Native OOXML .xlsx Spreadsheet Exporter (Zero external dependencies)
object XlsxExporter {
    fun buildExpensesXlsx(expenses: List<ExpenseRecord>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            fun putText(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            putText(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
            )
            putText(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )
            putText(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="AllChat Expenses" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""
            )
            putText(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
            )

            val rowsXml = buildString {
                fun esc(s: String) = s
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                fun strCell(ref: String, value: String) =
                    """<c r="$ref" t="inlineStr"><is><t>${esc(value)}</t></is></c>"""
                fun numCell(ref: String, value: Double) =
                    """<c r="$ref"><v>${String.format(Locale.US, "%.2f", value)}</v></c>"""

                append("""<row r="1">""")
                append(strCell("A1", "Date"))
                append(strCell("B1", "Merchant"))
                append(strCell("C1", "Category"))
                append(strCell("D1", "Amount"))
                append(strCell("E1", "Currency"))
                append(strCell("F1", "Notes"))
                appendLine("</row>")

                expenses.forEachIndexed { idx, e ->
                    val r = idx + 2
                    append("""<row r="$r">""")
                    append(strCell("A$r", e.dateIso))
                    append(strCell("B$r", e.merchant))
                    append(strCell("C$r", e.category))
                    append(numCell("D$r", e.amount))
                    append(strCell("E$r", e.currency))
                    append(strCell("F$r", e.notes))
                    appendLine("</row>")
                }
                val totalRow = expenses.size + 2
                val sum = expenses.sumOf { it.amount }
                append("""<row r="$totalRow">""")
                append(strCell("C$totalRow", "TOTAL"))
                append(numCell("D$totalRow", sum))
                appendLine("</row>")
            }

            putText(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
$rowsXml
  </sheetData>
</worksheet>"""
            )
        }
        return baos.toByteArray()
    }
}

// 6. Pure-Kotlin Math Expression Evaluator (Desmos-Style Plotter with a, b, c parameters)
object MathExpressionEvaluator {
    fun evaluate(expression: String, x: Double, a: Double = 1.0, b: Double = 1.0, c: Double = 0.0): Double {
        val cleaned = expression
            .replace(Regex("""^(?:f\(x\)|y)\s*=\s*""", RegexOption.IGNORE_CASE), "")
            .replace("\\sin", "sin")
            .replace("\\cos", "cos")
            .replace("\\tan", "tan")
            .replace("\\exp", "exp")
            .replace("\\ln", "ln")
            .replace("\\log", "log")
            .replace("\\sqrt", "sqrt")
            .replace("\\pi", "pi")
            .replace("{", "(")
            .replace("}", ")")
            .replace(" ", "")
        return runCatching { Parser(cleaned, x, a, b, c).parseExpression() }.getOrDefault(Double.NaN)
    }

    private class Parser(
        private val s: String,
        private val xVal: Double,
        private val aVal: Double,
        private val bVal: Double,
        private val cVal: Double
    ) {
        private var pos = 0

        fun parseExpression(): Double {
            var v = parseTerm()
            while (pos < s.length) {
                when (s[pos]) {
                    '+' -> { pos++; v += parseTerm() }
                    '-' -> { pos++; v -= parseTerm() }
                    else -> break
                }
            }
            return v
        }

        private fun parseTerm(): Double {
            var v = parsePower()
            while (pos < s.length) {
                when {
                    s[pos] == '*' -> { pos++; v *= parsePower() }
                    s[pos] == '/' -> { pos++; v /= parsePower() }
                    s[pos] == '(' || s[pos].isLetter() -> {
                        // Implicit multiplication like 2x or x*sin(x)
                        v *= parsePower()
                    }
                    else -> break
                }
            }
            return v
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            return if (pos < s.length && s[pos] == '^') {
                pos++
                base.pow(parsePower())
            } else {
                base
            }
        }

        private fun parseUnary(): Double {
            if (pos < s.length && s[pos] == '+') { pos++; return parseUnary() }
            if (pos < s.length && s[pos] == '-') { pos++; return -parseUnary() }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            if (pos >= s.length) return Double.NaN
            val ch = s[pos]
            if (ch == '(') {
                pos++
                val v = parseExpression()
                if (pos < s.length && s[pos] == ')') pos++
                return v
            }
            if (ch.isDigit() || ch == '.') {
                val start = pos
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                return s.substring(start, pos).toDoubleOrNull() ?: Double.NaN
            }
            if (ch.isLetter()) {
                val start = pos
                while (pos < s.length && s[pos].isLetter()) pos++
                val ident = s.substring(start, pos).lowercase(Locale.US)
                if (pos < s.length && s[pos] == '(') {
                    pos++
                    val arg = parseExpression()
                    if (pos < s.length && s[pos] == ')') pos++
                    return when (ident) {
                        "sin" -> sin(arg)
                        "cos" -> cos(arg)
                        "tan" -> tan(arg)
                        "exp" -> exp(arg)
                        "ln" -> ln(arg)
                        "log" -> log10(arg)
                        "sqrt" -> sqrt(arg)
                        "abs" -> abs(arg)
                        else -> Double.NaN
                    }
                }
                return when (ident) {
                    "x" -> xVal
                    "a" -> aVal
                    "b" -> bVal
                    "c" -> cVal
                    "pi" -> PI
                    "e" -> kotlin.math.E
                    else -> Double.NaN
                }
            }
            return Double.NaN
        }
    }
}

// 7. JSON Schema Validator & Flattener for Structured JSON Mode
data class JsonValidationReport(
    val isValidJson: Boolean,
    val missingKeys: List<String>,
    val flattenedPairs: List<Pair<String, String>>,
    val error: String? = null
)

object JsonSchemaValidator {
    fun validate(rawOutput: String, schemaTemplate: String = ""): JsonValidationReport {
        val cleaned = rawOutput.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            val parsedObj = JSONObject(cleaned)
            val missing = mutableListOf<String>()
            if (schemaTemplate.isNotBlank()) {
                runCatching {
                    val tmpl = JSONObject(schemaTemplate.trim())
                    tmpl.keys().forEach { key ->
                        if (!parsedObj.has(key)) missing += key
                    }
                }
            }
            val pairs = mutableListOf<Pair<String, String>>()
            flatten("", parsedObj, pairs)
            JsonValidationReport(true, missing, pairs, null)
        } catch (err: Throwable) {
            try {
                val arr = JSONArray(cleaned)
                val pairs = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    pairs += "[$i]" to arr.get(i).toString()
                }
                JsonValidationReport(true, emptyList(), pairs, null)
            } catch (_: Throwable) {
                JsonValidationReport(false, emptyList(), emptyList(), err.message ?: "Invalid JSON")
            }
        }
    }

    private fun flatten(prefix: String, obj: JSONObject, out: MutableList<Pair<String, String>>) {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val full = if (prefix.isEmpty()) k else "$prefix.$k"
            when (val v = obj.opt(k)) {
                is JSONObject -> flatten(full, v, out)
                is JSONArray -> out += full to v.toString()
                else -> out += full to (v?.toString() ?: "null")
            }
        }
    }
}
