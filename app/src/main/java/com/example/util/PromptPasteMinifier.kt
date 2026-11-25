package com.example.util

import kotlin.math.roundToInt

data class MinifyPasteResult(
    val minifiedText: String,
    val originalChars: Int,
    val minifiedChars: Int,
    val savedPercent: Int,
    val estimatedTokensSaved: Int
)

/**
 * Local client-side token-saving minifier for long pasted logs, stack traces, HTML, or source code.
 * Collapses repeated stack-trace frames, duplicate log lines, boilerplate comments, and redundant
 * blank lines/indentation before sending to cloud APIs.
 */
object PromptPasteMinifier {

    const val MIN_TRIGGER_CHARS = 1500

    fun shouldOfferMinify(text: String): Boolean = text.length >= MIN_TRIGGER_CHARS

    fun minify(raw: String): MinifyPasteResult {
        val originalLen = raw.length
        if (originalLen < 200) {
            return MinifyPasteResult(raw, originalLen, originalLen, 0, 0)
        }

        // 1. Strip HTML comments & multi-line C/JS/Kotlin block comments (preserving license-free code)
        var text = raw
            .replace(Regex("<!--[\\s\\S]*?-->"), "")
            .replace(Regex("/\\*(?!\\*\\s*@)[\\s\\S]*?\\*/"), "")

        val rawLines = text.lines()
        val compactedLines = mutableListOf<String>()

        var lastNormalizedLine: String? = null
        var repeatCount = 0
        var stackFrameStreak = 0
        var omittedStackFrames = 0

        fun flushRepeat() {
            if (repeatCount > 1 && compactedLines.isNotEmpty()) {
                val lastIdx = compactedLines.lastIndex
                compactedLines[lastIdx] = "${compactedLines[lastIdx]} [x$repeatCount]"
            }
            repeatCount = 0
        }

        fun flushStackOmission() {
            if (omittedStackFrames > 0) {
                compactedLines += "  ... ($omittedStackFrames verbose framework stack frames omitted)"
                omittedStackFrames = 0
            }
            stackFrameStreak = 0
        }

        for (line in rawLines) {
            val trimmed = line.trim()

            // Skip blank lines and pure single-line comments (// ... or # ...)
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("//") && !trimmed.contains("http", ignoreCase = true)) continue
            if (trimmed.startsWith("# ") && !trimmed.startsWith("#include") && !trimmed.startsWith("#define")) continue
            if (trimmed.startsWith("<!--")) continue

            // Collapse noisy JVM / Android / Node / Python framework stack-trace frames after first 4 frames
            val isFrameworkStackFrame =
                (trimmed.startsWith("at java.") ||
                    trimmed.startsWith("at android.") ||
                    trimmed.startsWith("at androidx.") ||
                    trimmed.startsWith("at kotlin.coroutines.") ||
                    trimmed.startsWith("at kotlinx.coroutines.") ||
                    trimmed.startsWith("at dalvik.") ||
                    trimmed.startsWith("at sun.") ||
                    trimmed.startsWith("at node:") ||
                    trimmed.contains("node_modules/")) &&
                    (trimmed.startsWith("at ") || trimmed.startsWith("File "))

            if (isFrameworkStackFrame) {
                stackFrameStreak++
                if (stackFrameStreak > 3) {
                    omittedStackFrames++
                    continue
                }
            } else {
                flushStackOmission()
            }

            // Strip trailing inline comments (safe when outside quotes/URLs)
            val withoutInlineComment = stripSafeInlineComment(line)
            // Collapse leading indentation (4/8 spaces -> compact 1-2 spaces) and internal multi-spaces
            val indentLen = withoutInlineComment.takeWhile { it == ' ' || it == '\t' }.length
            val compactIndent = if (indentLen >= 4) "  " else if (indentLen > 0) " " else ""
            val body = withoutInlineComment.trim().replace(Regex("[ \\t]{2,}"), " ")
            if (body.isEmpty()) continue

            val candidate = compactIndent + body
            // Normalize timestamps / hex addresses when detecting consecutive duplicate log lines
            val dedupKey = body
                .replace(Regex("\\b\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?\\b"), "<T>")
                .replace(Regex("0x[0-9a-fA-F]+"), "<HEX>")

            if (dedupKey == lastNormalizedLine) {
                repeatCount++
            } else {
                flushRepeat()
                lastNormalizedLine = dedupKey
                repeatCount = 1
                compactedLines += candidate
            }
        }
        flushStackOmission()
        flushRepeat()

        val minified = compactedLines.joinToString("\n").trim()
        val finalText = if (minified.length < originalLen) minified else raw.trim()
        val savedChars = (originalLen - finalText.length).coerceAtLeast(0)
        val savedPct = if (originalLen > 0) ((savedChars * 100.0) / originalLen).roundToInt().coerceIn(0, 95) else 0
        val tokensSaved = (savedChars / 4).coerceAtLeast(0)

        return MinifyPasteResult(
            minifiedText = finalText,
            originalChars = originalLen,
            minifiedChars = finalText.length,
            savedPercent = savedPct,
            estimatedTokensSaved = tokensSaved
        )
    }

    private fun stripSafeInlineComment(line: String): String {
        val idx = line.indexOf(" //")
        if (idx <= 0) return line
        val before = line.substring(0, idx)
        if (before.contains("http:") || before.contains("https:")) return line
        val quotes = before.count { it == '"' || it == '\'' }
        return if (quotes % 2 == 0) before else line
    }
}
