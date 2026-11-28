package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.PinnedReferenceItem
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubtleFill
import com.example.ui.theme.SwissWhite

/**
 * Collapsible & vertically resizable top "Pinned Reference" split-pane docked above the chat
 * scroll area so users can keep equations, code blocks, diagrams, or Live Lens scans visible
 * while typing follow-up prompts.
 */
@Composable
fun PinnedReferenceSplitPane(
    pinned: PinnedReferenceItem,
    onCopy: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var collapsed by remember(pinned.id) { mutableStateOf(false) }
    var paneHeightDp by remember(pinned.id) { mutableFloatStateOf(168f) }
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissPaper)
            .border(1.dp, SwissInk, RectangleShape)
            .testTag("pinned_reference_split_pane")
    ) {
        // Header strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SwissInk)
                .clickable { collapsed = !collapsed }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = null,
                tint = SwissSignalRed,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "PINNED REF // ${pinned.sourceLabel} · ${pinned.title}",
                style = MaterialTheme.typography.labelSmall,
                color = SwissWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy pinned reference",
                tint = SwissWhite,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onCopy)
                    .testTag("pinned_reference_copy_button")
            )
            Icon(
                imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (collapsed) "Expand pinned reference" else "Collapse pinned reference",
                tint = SwissWhite,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { collapsed = !collapsed }
                    .testTag("pinned_reference_collapse_button")
            )
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Unpin reference",
                tint = SwissSignalRed,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onUnpin)
                    .testTag("pinned_reference_unpin_button")
            )
        }

        if (!collapsed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(paneHeightDp.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                MarkdownText(
                    markdown = pinned.content,
                    showThinking = false
                )
            }

            // Drag handle to resize split-pane height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SwissSubtleFill)
                    .border(0.5.dp, SwissConcrete, RectangleShape)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val deltaDp = with(density) { dragAmount.toDp().value }
                            paneHeightDp = (paneHeightDp + deltaDp).coerceIn(88f, 360f)
                        }
                    }
                    .padding(vertical = 5.dp)
                    .testTag("pinned_reference_resize_handle"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(3.dp)
                            .background(SwissInk)
                    )
                    Text(
                        text = "DRAG TO RESIZE",
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissMuted
                    )
                }
            }
        }
    }
}
