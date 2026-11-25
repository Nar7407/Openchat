package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.domain.model.LiveLensOverlayRegion
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissWhite
import kotlin.math.roundToInt

/**
 * Frozen-frame visual overlay translation view ("Translate View").
 * Draws high-contrast translated text callouts directly over the frozen camera frame
 * so foreign-language signs, manuals, or labels can be read at a glance.
 */
@Composable
fun LiveLensTranslateOverlay(
    frozenFrame: Bitmap,
    targetLanguage: String,
    regions: List<LiveLensOverlayRegion>,
    modelLabel: String?,
    onCopyAll: () -> Unit,
    onRescan: () -> Unit,
    onReturnLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOriginalIndex by remember(regions) { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SwissInk)
            .testTag("live_lens_translate_overlay")
    ) {
        Image(
            bitmap = frozenFrame.asImageBitmap(),
            contentDescription = "Frozen frame with translated text overlays",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Subtle contrast veil so overlaid translated cards remain crisp on bright backgrounds
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )

        // Spatial translated blocks positioned directly onto the frozen frame
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerW = constraints.maxWidth.toFloat()
            val containerH = constraints.maxHeight.toFloat()
            val maxCardWidth = (maxWidth * 0.86f).coerceAtLeast(140.dp)

            regions.forEachIndexed { idx, region ->
                val pxX = (region.x.coerceIn(0.03f, 0.78f) * containerW).roundToInt()
                val pxY = (region.y.coerceIn(0.14f, 0.78f) * containerH).roundToInt()
                val showingOrig = showOriginalIndex == idx && region.original.isNotBlank()

                Column(
                    modifier = Modifier
                        .offset { IntOffset(pxX, pxY) }
                        .widthIn(min = 96.dp, max = maxCardWidth)
                        .background(SwissPaper.copy(alpha = 0.94f), RectangleShape)
                        .border(1.5.dp, SwissSignalRed, RectangleShape)
                        .clickable {
                            showOriginalIndex = if (showOriginalIndex == idx) null else idx
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("translate_overlay_region_$idx")
                ) {
                    Text(
                        text = if (showingOrig) region.original else region.translated,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = SwissInk
                    )
                    if (region.original.isNotBlank()) {
                        Text(
                            text = if (showingOrig) "ORIGINAL // TAP FOR ${targetLanguage.uppercase()}" else "TAP TO SEE ORIGINAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = SwissSignalRed
                        )
                    }
                }
            }
        }

        // Top Header Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(SwissInk.copy(alpha = 0.93f))
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TRANSLATE VIEW // → ${targetLanguage.uppercase()} (${regions.size} BLOCKS)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SwissSignalRed
                )
                Text(
                    text = modelLabel ?: "Tap any card to toggle original text",
                    style = MaterialTheme.typography.bodySmall,
                    color = SwissWhite
                )
            }
            SwissIconCell(
                icon = Icons.Filled.Close,
                contentDescription = "Exit translate overlay",
                onClick = onReturnLive,
                inverted = true,
                cellSize = 40.dp,
                modifier = Modifier.testTag("translate_overlay_close")
            )
        }

        // Bottom Action Dock
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(SwissInk.copy(alpha = 0.94f))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwissIconCell(
                icon = Icons.Outlined.ContentCopy,
                contentDescription = "Copy all translations",
                onClick = onCopyAll,
                inverted = true,
                cellSize = 48.dp,
                modifier = Modifier.testTag("translate_overlay_copy")
            )
            SwissButton(
                text = "Re-Scan",
                leadingIcon = Icons.Outlined.Refresh,
                variant = SwissButtonVariant.OUTLINE,
                onClick = onRescan,
                modifier = Modifier
                    .weight(1f)
                    .testTag("translate_overlay_rescan")
            )
            SwissButton(
                text = "Live Camera",
                variant = SwissButtonVariant.PRIMARY,
                onClick = onReturnLive,
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("translate_overlay_live")
            )
        }
    }
}
