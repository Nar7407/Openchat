package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSignalRedTint
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissVerifiedInk
import com.example.ui.theme.SwissVerifiedTint

enum class DiffLineKind { SAME, ADDED, REMOVED }

data class DiffLine(
    val kind: DiffLineKind,
    val text: String
)

/**
 * Lightweight LCS line-level diff engine for comparing regenerated AI response versions (< 1/2 >).
 */
object ResponseDiffEngine {

    fun diffLines(previousText: String, currentText: String): List<DiffLine> {
        val a = previousText.lines().take(240)
        val b = currentText.lines().take(240)
        val m = a.size
        val n = b.size

        // Build DP table for LCS
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val out = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < m && j < n) {
            when {
                a[i] == b[j] -> {
                    out += DiffLine(DiffLineKind.SAME, a[i])
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    out += DiffLine(DiffLineKind.REMOVED, a[i])
                    i++
                }
                else -> {
                    out += DiffLine(DiffLineKind.ADDED, b[j])
                    j++
                }
            }
        }
        while (i < m) {
            out += DiffLine(DiffLineKind.REMOVED, a[i++])
        }
        while (j < n) {
            out += DiffLine(DiffLineKind.ADDED, b[j++])
        }
        return out
    }
}

@Composable
fun ResponseDiffView(
    previousVersionNumber: Int,
    currentVersionNumber: Int,
    previousText: String,
    currentText: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(previousText, currentText) {
        ResponseDiffEngine.diffLines(previousText, currentText)
    }
    val addedCount = remember(lines) { lines.count { it.kind == DiffLineKind.ADDED } }
    val removedCount = remember(lines) { lines.count { it.kind == DiffLineKind.REMOVED } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SwissInk, RectangleShape)
            .background(SwissPaper)
            .testTag("response_diff_view")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SwissSubtleFill)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DIFF // v$previousVersionNumber → v$currentVersionNumber",
                style = MaterialTheme.typography.labelSmall,
                color = SwissInk
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "+$addedCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissVerifiedInk
                )
                Text(
                    text = "-$removedCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            lines.forEach { line ->
                val bg = when (line.kind) {
                    DiffLineKind.ADDED -> SwissVerifiedTint
                    DiffLineKind.REMOVED -> SwissSignalRedTint
                    DiffLineKind.SAME -> Color.Transparent
                }
                val fg = when (line.kind) {
                    DiffLineKind.ADDED -> SwissVerifiedInk
                    DiffLineKind.REMOVED -> SwissSignalRed
                    DiffLineKind.SAME -> SwissMuted
                }
                val prefix = when (line.kind) {
                    DiffLineKind.ADDED -> "+ "
                    DiffLineKind.REMOVED -> "- "
                    DiffLineKind.SAME -> "  "
                }
                Text(
                    text = "$prefix${line.text}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        textDecoration = if (line.kind == DiffLineKind.REMOVED) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = fg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
