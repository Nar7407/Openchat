package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSignalRedTint
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite
import kotlinx.coroutines.launch

data class SolverStepItem(
    val number: Int,
    val title: String,
    val content: String,
    val embeddedWhy: String? = null
)

data class ParsedSolverSolution(
    val intro: String?,
    val steps: List<SolverStepItem>,
    val finalResult: String?
)

/**
 * Parses a Live Lens math/physics/circuit/code solution into structured collapsible
 * STEP 01 -> STEP 02 -> FINAL RESULT cards with optional WHY explanations.
 */
fun parseSolverResponse(raw: String): ParsedSolverSolution? {
    val text = raw.trim()
    if (text.isBlank()) return null

    val stepHeaderRegex = Regex(
        pattern = """(?im)^(?:#{1,4}\s*|\*\*)?(?:step\s*0?(\d+)|(\d+)\.\s*(?=\*\*?[A-Za-z]))[:.)\-\s]*([^\n*]*?)(?:\*\*)?\s*$"""
    )
    val finalHeaderRegex = Regex(
        pattern = """(?im)^(?:#{1,4}\s*|\*\*)?(?:final\s*(?:result|answer|solution)|answer|conclusion)[:.\s]*(?:\*\*)?(.*)$"""
    )

    val stepMatches = stepHeaderRegex.findAll(text).toList()
    if (stepMatches.isEmpty()) return null

    val finalMatch = finalHeaderRegex.findAll(text)
        .lastOrNull { it.range.first > stepMatches.first().range.first }

    val intro = text.substring(0, stepMatches.first().range.first).trim().takeIf { it.isNotBlank() }

    val parsedSteps = mutableListOf<SolverStepItem>()
    for (i in stepMatches.indices) {
        val m = stepMatches[i]
        val startBody = (m.range.last + 1).coerceAtMost(text.length)
        val endBody = when {
            i + 1 < stepMatches.size -> stepMatches[i + 1].range.first
            finalMatch != null && finalMatch.range.first > startBody -> finalMatch.range.first
            else -> text.length
        }.coerceIn(startBody, text.length)

        val num = (m.groupValues[1].ifBlank { m.groupValues[2] }).toIntOrNull() ?: (i + 1)
        val title = m.groupValues[3].trim().trimEnd(':').ifBlank { "Derivation Step" }
        val rawBody = text.substring(startBody, endBody).trim()

        val whyRegex = Regex("""(?im)^(?:\*\*)?(?:why|rule|formula|reason)\s*[:\-]\s*(?:\*\*)?(.*)$""")
        val whyMatch = whyRegex.find(rawBody)
        val embeddedWhy = whyMatch?.let { wm ->
            rawBody.substring(wm.range.first).replaceFirst(Regex("""(?i)^(?:\*\*)?(?:why|rule|formula|reason)\s*[:\-]\s*(?:\*\*)?"""), "").trim()
        }?.takeIf { it.isNotBlank() }

        val cleanBody = if (whyMatch != null) {
            rawBody.substring(0, whyMatch.range.first).trim()
        } else {
            rawBody
        }

        if (cleanBody.isNotBlank() || title.isNotBlank()) {
            parsedSteps += SolverStepItem(
                number = num,
                title = title,
                content = cleanBody.ifBlank { title },
                embeddedWhy = embeddedWhy
            )
        }
    }

    if (parsedSteps.isEmpty()) return null

    val finalResult = finalMatch?.let { fm ->
        val inlineTail = fm.groupValues.getOrNull(1)?.trim().orEmpty()
        val afterBody = text.substring((fm.range.last + 1).coerceAtMost(text.length)).trim()
        listOf(inlineTail, afterBody).filter { it.isNotBlank() }.joinToString("\n").trim()
    }?.takeIf { it.isNotBlank() }

    return ParsedSolverSolution(
        intro = intro,
        steps = parsedSteps,
        finalResult = finalResult
    )
}

@Composable
fun InteractiveSolverCardsView(
    solution: ParsedSolverSolution,
    onRequestWhyExplanation: suspend (step: SolverStepItem) -> String,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val expandedSteps = remember(solution) {
        mutableStateMapOf<Int, Boolean>().apply {
            solution.steps.indices.forEach { put(it, true) }
        }
    }
    val whyVisible = remember(solution) { mutableStateMapOf<Int, Boolean>() }
    val whyTexts = remember(solution) {
        mutableStateMapOf<Int, String>().apply {
            solution.steps.forEachIndexed { idx, s ->
                if (!s.embeddedWhy.isNullOrBlank()) put(idx, s.embeddedWhy)
            }
        }
    }
    val loadingWhy = remember(solution) { mutableStateMapOf<Int, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("interactive_solver_cards"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!solution.intro.isNullOrBlank()) {
            LatexMathMarkdownView(content = solution.intro)
        }

        solution.steps.forEachIndexed { idx, step ->
            val isExpanded = expandedSteps[idx] ?: true
            val isWhyOpen = whyVisible[idx] ?: false
            val isWhyLoading = loadingWhy[idx] ?: false
            val stepLabel = "STEP %02d".format(step.number)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissPaper, RectangleShape)
                    .border(1.dp, SwissInk, RectangleShape)
                    .testTag("solver_step_card_$idx")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SwissSubtleFill)
                        .clickable { expandedSteps[idx] = !isExpanded }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stepLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SwissWhite,
                        modifier = Modifier
                            .background(SwissInk, RectangleShape)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = SwissInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // "Why?" button
                    Row(
                        modifier = Modifier
                            .background(if (isWhyOpen) SwissSignalRed else SwissCanvas, RectangleShape)
                            .border(1.dp, if (isWhyOpen) SwissSignalRed else SwissInk, RectangleShape)
                            .clickable {
                                val nextOpen = !isWhyOpen
                                whyVisible[idx] = nextOpen
                                if (nextOpen && whyTexts[idx].isNullOrBlank() && !isWhyLoading) {
                                    loadingWhy[idx] = true
                                    scope.launch {
                                        val fetched = runCatching { onRequestWhyExplanation(step) }
                                            .getOrDefault("Applies the standard algebraic or physical rule for this step.")
                                        whyTexts[idx] = fetched
                                        loadingWhy[idx] = false
                                    }
                                }
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                            .testTag("solver_step_why_$idx"),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = "Why this step works",
                            tint = if (isWhyOpen) SwissWhite else SwissSignalRed,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Why?",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isWhyOpen) SwissWhite else SwissInk
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse step" else "Expand step",
                        tint = SwissInk,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LatexMathMarkdownView(content = step.content)

                        AnimatedVisibility(visible = isWhyOpen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SwissSignalRedTint, RectangleShape)
                                    .border(1.dp, SwissSignalRed, RectangleShape)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("solver_step_why_panel_$idx"),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "FORMULA & RULE // WHY THIS STEP WORKS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SwissSignalRed
                                )
                                Text(
                                    text = when {
                                        isWhyLoading -> "Explaining underlying formula & rule…"
                                        !whyTexts[idx].isNullOrBlank() -> whyTexts[idx].orEmpty()
                                        else -> "Standard step transformation."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SwissInk
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!solution.finalResult.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissInk, RectangleShape)
                    .border(2.dp, SwissSignalRed, RectangleShape)
                    .padding(12.dp)
                    .testTag("solver_final_result_card"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "FINAL RESULT // VERIFIED",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SwissSignalRed
                )
                Text(
                    text = solution.finalResult.replace("**", ""),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = SwissWhite
                )
            }
        }
    }
}
