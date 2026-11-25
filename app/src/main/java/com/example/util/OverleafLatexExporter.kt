package com.example.util

/**
 * Detects math/derivation responses and wraps formatted equations and prose into a clean,
 * ready-to-compile Overleaf LaTeX (.tex) document template.
 */
object OverleafLatexExporter {

    private val mathSignalRegex = Regex(
        """(\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\)|\\frac\{|\\int|\\sum|\\sqrt\{|\\begin\{(?:align|equation|matrix|bmatrix|pmatrix)|\$[^$\n]{2,100}\$|STEP\s+0?1\s*:|FINAL RESULT\s*:)""",
        RegexOption.IGNORE_CASE
    )

    fun containsMathOrDerivation(markdown: String): Boolean {
        if (markdown.length < 12) return false
        return mathSignalRegex.containsMatchIn(markdown)
    }

    fun buildOverleafDocument(
        markdown: String,
        title: String = "AllChat Mathematical Derivation"
    ): String {
        // Strip hidden reasoning traces before exporting
        val cleaned = markdown
            .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<reasoning>[\\s\\S]*?</reasoning>", RegexOption.IGNORE_CASE), "")
            .trim()

        val body = convertMarkdownWithMathToLatex(cleaned)
        val safeTitle = escapeLatexText(title.take(80))

        return buildString {
            appendLine("""\documentclass[11pt,a4paper]{article}""")
            appendLine("""\usepackage[utf8]{inputenc}""")
            appendLine("""\usepackage[T1]{fontenc}""")
            appendLine("""\usepackage{amsmath, amssymb, amsthm, mathtools}""")
            appendLine("""\usepackage{physics}""")
            appendLine("""\usepackage[margin=1in]{geometry}""")
            appendLine("""\usepackage{hyperref}""")
            appendLine("""\usepackage{enumitem}""")
            appendLine("""\usepackage{listings}""")
            appendLine()
            appendLine("""\title{$safeTitle}""")
            appendLine("""\author{Exported from AllChat}""")
            appendLine("""\date{\today}""")
            appendLine()
            appendLine("""\begin{document}""")
            appendLine("""\maketitle""")
            appendLine()
            appendLine(body)
            appendLine()
            appendLine("""\end{document}""")
        }
    }

    private fun convertMarkdownWithMathToLatex(input: String): String {
        val out = StringBuilder()
        val lines = input.lines()
        var inCodeBlock = false
        var inDisplayMath = false
        var inItemize = false

        fun closeListIfOpen() {
            if (inItemize) {
                out.appendLine("""\end{itemize}""")
                inItemize = false
            }
        }

        for (rawLine in lines) {
            val trimmed = rawLine.trim()

            // Code blocks -> lstlisting
            if (trimmed.startsWith("```")) {
                closeListIfOpen()
                if (!inCodeBlock) {
                    inCodeBlock = true
                    out.appendLine("""\begin{lstlisting}""")
                } else {
                    inCodeBlock = false
                    out.appendLine("""\end{lstlisting}""")
                }
                continue
            }
            if (inCodeBlock) {
                out.appendLine(rawLine)
                continue
            }

            // Multi-line $$ ... $$ display math blocks
            if (trimmed == "$$") {
                closeListIfOpen()
                if (!inDisplayMath) {
                    inDisplayMath = true
                    out.appendLine("""\[""")
                } else {
                    inDisplayMath = false
                    out.appendLine("""\]""")
                }
                continue
            }
            if (inDisplayMath) {
                out.appendLine(rawLine)
                continue
            }

            // Single-line $$ ... $$ display equation
            if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4) {
                closeListIfOpen()
                val eq = trimmed.removePrefix("$$").removeSuffix("$$").trim()
                out.appendLine("""\[""")
                out.appendLine(eq)
                out.appendLine("""\]""")
                continue
            }

            if (trimmed.isEmpty()) {
                closeListIfOpen()
                out.appendLine()
                continue
            }

            // Headings
            when {
                trimmed.startsWith("### ") -> {
                    closeListIfOpen()
                    out.appendLine("""\subsubsection*{${formatProsePreservingMath(trimmed.removePrefix("### "))}}""")
                    continue
                }
                trimmed.startsWith("## ") -> {
                    closeListIfOpen()
                    out.appendLine("""\subsection*{${formatProsePreservingMath(trimmed.removePrefix("## "))}}""")
                    continue
                }
                trimmed.startsWith("# ") -> {
                    closeListIfOpen()
                    out.appendLine("""\section*{${formatProsePreservingMath(trimmed.removePrefix("# "))}}""")
                    continue
                }
            }

            // Solver card headers: STEP 01: ... / FINAL RESULT: ...
            if (Regex("""^(?:STEP\s+\d+|FINAL\s+RESULT)\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
                closeListIfOpen()
                val idx = trimmed.indexOf(':')
                val label = trimmed.substring(0, idx).trim()
                val rest = trimmed.substring(idx + 1).trim()
                out.appendLine("""\paragraph{${escapeLatexText(label)}} ${formatProsePreservingMath(rest)}""")
                continue
            }

            // Bullet items
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inItemize) {
                    out.appendLine("""\begin{itemize}[leftmargin=*]""")
                    inItemize = true
                }
                out.appendLine("""  \item ${formatProsePreservingMath(trimmed.drop(2).trim())}""")
                continue
            }

            closeListIfOpen()
            out.appendLine(formatProsePreservingMath(rawLine))
        }

        closeListIfOpen()
        if (inCodeBlock) out.appendLine("""\end{lstlisting}""")
        if (inDisplayMath) out.appendLine("""\]""")
        return out.toString().trim()
    }

    private fun formatProsePreservingMath(line: String): String {
        // Split around inline math ($...$ or \(...\)) and display math (\[...\]) so LaTeX math stays intact
        val tokenRegex = Regex("""(\$\$[^$]+\$\$|\\\[[^\]]+\\\]|\\\([^)]+\\\)|\$[^$\n]+\$)""")
        val sb = StringBuilder()
        var cursor = 0
        for (match in tokenRegex.findAll(line)) {
            if (match.range.first > cursor) {
                sb.append(formatInlineMarkdownToLatex(line.substring(cursor, match.range.first)))
            }
            val rawMath = match.value
            val normalizedMath = if (rawMath.startsWith("$$") && rawMath.endsWith("$$")) {
                "\\[${rawMath.removePrefix("$$").removeSuffix("$$")}\\]"
            } else {
                rawMath
            }
            sb.append(normalizedMath)
            cursor = match.range.last + 1
        }
        if (cursor < line.length) {
            sb.append(formatInlineMarkdownToLatex(line.substring(cursor)))
        }
        return sb.toString()
    }

    private fun formatInlineMarkdownToLatex(prose: String): String {
        var result = escapeLatexText(prose)
        // Bold **...** -> \textbf{...}
        result = result.replace(Regex("""\*\*(.+?)\*\*""")) { m ->
            """\textbf{${m.groupValues[1]}}"""
        }
        // Inline code `...` -> \texttt{...}
        result = result.replace(Regex("""`([^`]+)`""")) { m ->
            """\texttt{${m.groupValues[1]}}"""
        }
        return result
    }

    private fun escapeLatexText(text: String): String {
        return text
            .replace("&", """\&""")
            .replace("%", """\%""")
            .replace("#", """\#""")
            .replace("_", """\_""")
    }
}
