package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite

private sealed interface LatexSegment {
    data class TextBlock(val markdown: String) : LatexSegment
    data class DisplayEquation(val prettyMath: String, val isBoxed: Boolean) : LatexSegment
}

/**
 * Offline-ready Swiss-styled LaTeX & Markdown math renderer for Live Lens "Solve Step-by-Step"
 * and Screen Scan technical analysis.
 */
@Composable
fun LatexMathMarkdownView(
    content: String,
    modifier: Modifier = Modifier
) {
    val segments = remember(content) { parseLatexSegments(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { seg ->
            when (seg) {
                is LatexSegment.TextBlock -> {
                    val pretty = remember(seg.markdown) { formatInlineLatex(seg.markdown) }
                    MarkdownText(markdown = pretty)
                }
                is LatexSegment.DisplayEquation -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SwissInk, RectangleShape)
                            .border(
                                width = if (seg.isBoxed) 2.dp else 1.dp,
                                color = if (seg.isBoxed) SwissSignalRed else SwissInk,
                                shape = RectangleShape
                            )
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = seg.prettyMath,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = if (seg.isBoxed) FontWeight.Bold else FontWeight.Medium,
                                lineHeight = 24.sp
                            ),
                            color = SwissWhite
                        )
                    }
                }
            }
        }
    }
}

private fun parseLatexSegments(raw: String): List<LatexSegment> {
    val blockRegex = Regex("""\$\$([\s\S]+?)\$\$|\\\[([\s\S]+?)\\\]""")
    val out = mutableListOf<LatexSegment>()
    var cursor = 0
    blockRegex.findAll(raw).forEach { match ->
        if (match.range.first > cursor) {
            val textBefore = raw.substring(cursor, match.range.first).trim()
            if (textBefore.isNotEmpty()) out += LatexSegment.TextBlock(textBefore)
        }
        val expr = (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim()
        if (expr.isNotEmpty()) {
            val isBoxed = expr.contains("\\boxed")
            out += LatexSegment.DisplayEquation(
                prettyMath = convertLatexToReadableMath(expr),
                isBoxed = isBoxed
            )
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        val tail = raw.substring(cursor).trim()
        if (tail.isNotEmpty()) out += LatexSegment.TextBlock(tail)
    }
    return if (out.isEmpty()) listOf(LatexSegment.TextBlock(raw)) else out
}

private fun formatInlineLatex(markdown: String): String {
    val inlineRegex = Regex("""(?<!\$)\$([^$\n]+?)\$(?!\$)|\\\((.+?)\\\)""")
    return inlineRegex.replace(markdown) { m ->
        val expr = m.groupValues[1].ifEmpty { m.groupValues[2] }
        "`${convertLatexToReadableMath(expr)}`"
    }
}

internal fun convertLatexToReadableMath(raw: String): String {
    var s = raw.trim()
    // Unwrap \boxed{...}, \text{...}, \mathrm{...}, \mathbf{...}
    s = s.replace(Regex("""\\boxed\{([^{}]+)\}"""), "[ $1 ]")
    s = s.replace(Regex("""\\(?:text|mathrm|mathbf|operatorname)\{([^{}]*)\}"""), "$1")
    // Fractions: \frac{a}{b} -> (a)/(b)
    repeat(3) {
        s = s.replace(Regex("""\\(?:d|t)?frac\{([^{}]+)\}\{([^{}]+)\}"""), "($1)/($2)")
    }
    // Square roots: \sqrt[n]{x} and \sqrt{x}
    s = s.replace(Regex("""\\sqrt\[([^\]]+)\]\{([^{}]+)\}"""), "$1√($2)")
    s = s.replace(Regex("""\\sqrt\{([^{}]+)\}"""), "√($1)")

    val symbols = listOf(
        "\\times" to "×",
        "\\cdot" to "·",
        "\\div" to "÷",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\leq" to "≤",
        "\\le" to "≤",
        "\\geq" to "≥",
        "\\ge" to "≥",
        "\\neq" to "≠",
        "\\ne" to "≠",
        "\\approx" to "≈",
        "\\equiv" to "≡",
        "\\infty" to "∞",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\int" to "∫",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\Rightarrow" to "⇒",
        "\\implies" to "⇒",
        "\\Leftrightarrow" to "⇔",
        "\\rightarrow" to "→",
        "\\to" to "→",
        "\\leftarrow" to "←",
        "\\therefore" to "∴",
        "\\because" to "∵",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\subset" to "⊂",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\degree" to "°",
        "\\circ" to "°",
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\Delta" to "Δ",
        "\\epsilon" to "ε",
        "\\theta" to "θ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\pi" to "π",
        "\\Pi" to "Π",
        "\\sigma" to "σ",
        "\\Sigma" to "Σ",
        "\\tau" to "τ",
        "\\phi" to "φ",
        "\\omega" to "ω",
        "\\Omega" to "Ω",
        "\\left" to "",
        "\\right" to "",
        "\\," to " ",
        "\\;" to " ",
        "\\quad" to "  ",
        "\\\\" to "\n"
    )
    symbols.forEach { (cmd, repl) -> s = s.replace(cmd, repl) }

    // Superscripts & subscripts for common chars
    s = s.replace(Regex("""\^\{([^{}]+)\}""")) { toSuperscript(it.groupValues[1]) }
    s = s.replace(Regex("""\^([0-9n+-])""")) { toSuperscript(it.groupValues[1]) }
    s = s.replace(Regex("""_\{([^{}]+)\}""")) { toSubscript(it.groupValues[1]) }
    s = s.replace(Regex("""_([0-9ijkmn])""")) { toSubscript(it.groupValues[1]) }

    return s.replace("{", "").replace("}", "").replace(Regex("""[ \t]{2,}"""), " ").trim()
}

private fun toSuperscript(input: String): String {
    val map = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ'
    )
    return if (input.all { it in map }) input.map { map.getValue(it) }.joinToString("") else "^($input)"
}

private fun toSubscript(input: String): String {
    val map = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ',
        'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ', 'x' to 'ₓ'
    )
    return if (input.all { it in map }) input.map { map.getValue(it) }.joinToString("") else "_($input)"
}
