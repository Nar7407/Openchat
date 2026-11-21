package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.BranchMessageNode
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite

data class QuestionTick(
    val listIndex: Int,
    val questionNumber: Int,
    val preview: String
)

@Composable
fun ChatMinimapScrubber(
    branchNodes: List<BranchMessageNode>,
    currentVisibleIndex: Int,
    onJumpToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ticks = remember(branchNodes) {
        var qCount = 0
        branchNodes.mapIndexedNotNull { idx, node ->
            if (node.message.role == "USER") {
                qCount++
                QuestionTick(
                    listIndex = idx,
                    questionNumber = qCount,
                    preview = node.message.content.replace("\n", " ").trim().take(60)
                )
            } else {
                null
            }
        }
    }

    if (ticks.size < 3) return

    var activePreviewTick by remember { mutableStateOf<QuestionTick?>(null) }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        activePreviewTick?.let { tick ->
            Column(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .background(SwissInk, RectangleShape)
                    .border(1.dp, SwissSignalRed, RectangleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Q${tick.questionNumber.toString().padStart(2, '0')} // JUMP",
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissSignalRed
                )
                Text(
                    text = tick.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight()
                .background(SwissPaper.copy(alpha = 0.85f), RectangleShape)
                .border(1.dp, SwissInk, RectangleShape)
                .padding(vertical = 6.dp, horizontal = 3.dp)
                .pointerInput(ticks) {
                    detectTapGestures { offset ->
                        val frac = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 0.999f)
                        val tickIdx = (frac * ticks.size).toInt().coerceIn(0, ticks.lastIndex)
                        val chosen = ticks[tickIdx]
                        activePreviewTick = chosen
                        onJumpToIndex(chosen.listIndex)
                    }
                }
                .pointerInput(ticks) {
                    detectVerticalDragGestures(
                        onDragEnd = { activePreviewTick = null },
                        onDragCancel = { activePreviewTick = null },
                        onVerticalDrag = { change, _ ->
                            val frac = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 0.999f)
                            val tickIdx = (frac * ticks.size).toInt().coerceIn(0, ticks.lastIndex)
                            val chosen = ticks[tickIdx]
                            activePreviewTick = chosen
                            onJumpToIndex(chosen.listIndex)
                        }
                    )
                }
                .testTag("chat_minimap_scrubber"),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ticks.forEachIndexed { idx, tick ->
                val nextTickStart = ticks.getOrNull(idx + 1)?.listIndex ?: Int.MAX_VALUE
                val isCurrent = currentVisibleIndex in tick.listIndex until nextTickStart
                Box(
                    modifier = Modifier
                        .width(if (isCurrent) 12.dp else 8.dp)
                        .background(if (isCurrent) SwissSignalRed else SwissInk)
                        .padding(vertical = if (isCurrent) 2.5.dp else 1.5.dp)
                )
            }
        }
    }
}
