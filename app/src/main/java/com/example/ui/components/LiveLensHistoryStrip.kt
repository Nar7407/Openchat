package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import com.example.util.LiveLensHistoryEntry
import com.example.util.LiveLensHistoryStore
import java.util.Locale
import kotlin.math.abs

/**
 * Compact `1x / 2x / 3x` zoom selector pill bar bound to CameraX zoom control,
 * also reflecting continuous pinch-to-zoom values cleanly.
 */
@Composable
fun LiveLensZoomPillBar(
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onSelectZoom: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(1f, 2f, 3f).filter { it <= maxZoom + 0.05f }.ifEmpty { listOf(1f) }
    val isCustomPinch = presets.none { abs(currentZoom - it) < 0.15f }

    Row(
        modifier = modifier
            .background(SwissInk.copy(alpha = 0.86f), RectangleShape)
            .border(1.dp, SwissWhite.copy(alpha = 0.55f), RectangleShape)
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .testTag("live_lens_zoom_bar"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { preset ->
            val clamped = preset.coerceIn(minZoom, maxZoom)
            val isActive = abs(currentZoom - clamped) < 0.15f
            Text(
                text = "${preset.toInt()}x",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isActive) SwissWhite else SwissConcrete,
                modifier = Modifier
                    .background(if (isActive) SwissSignalRed else Color.Transparent, RectangleShape)
                    .clickable { onSelectZoom(clamped) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("lens_zoom_pill_${preset.toInt()}x")
            )
        }
        if (isCustomPinch) {
            Text(
                text = String.format(Locale.US, "%.1fx", currentZoom),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = SwissInk,
                modifier = Modifier
                    .background(SwissWhite, RectangleShape)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("lens_zoom_live_readout")
            )
        }
    }
}

/**
 * Persistent horizontal thumbnail strip of the last 10 Live Lens captures & OCR/AI answers.
 * Tapping any frame opens its saved answer immediately for re-inspection or 1-tap re-run.
 */
@Composable
fun LiveLensHistoryStrip(
    entries: List<LiveLensHistoryEntry>,
    selectedEntryId: Long?,
    onSelectEntry: (LiveLensHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SwissInk.copy(alpha = 0.90f), RectangleShape)
            .border(1.dp, SwissWhite.copy(alpha = 0.25f), RectangleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("live_lens_history_strip"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LENS HISTORY // ${entries.size}/10 SAVED SCANS — TAP TO INSPECT OR RE-RUN",
                style = MaterialTheme.typography.labelSmall,
                color = SwissConcrete
            )
            Text(
                text = "[CLEAR]",
                style = MaterialTheme.typography.labelSmall,
                color = SwissSignalRed,
                modifier = Modifier
                    .clickable { onClearHistory() }
                    .padding(horizontal = 4.dp)
                    .testTag("live_lens_clear_history")
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            entries.forEachIndexed { idx, entry ->
                val isSelected = entry.id == selectedEntryId
                val thumbBitmap = produceState<Bitmap?>(initialValue = null, key1 = entry.imageFilePath) {
                    value = LiveLensHistoryStore.loadBitmap(entry, maxEdge = 180)
                }.value

                val shortMode = when (entry.modeName) {
                    "LIVE_TRANSLATOR" -> "TRN"
                    "SOLVE_STEP_BY_STEP" -> "SLV"
                    "WHITEBOARD_TO_CODE" -> "COD"
                    else -> "EXP"
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(SwissInk, RectangleShape)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) SwissSignalRed else SwissWhite.copy(alpha = 0.55f),
                            shape = RectangleShape
                        )
                        .clickable { onSelectEntry(entry) }
                        .testTag("live_lens_history_item_$idx")
                ) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap.asImageBitmap(),
                            contentDescription = "Saved scan ${idx + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = shortMode,
                        style = MaterialTheme.typography.labelSmall,
                        color = SwissWhite,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(SwissInk.copy(alpha = 0.82f), RectangleShape)
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                    if (entry.scanEnhanced) {
                        Text(
                            text = "E",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SwissWhite,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(SwissSignalRed, RectangleShape)
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
