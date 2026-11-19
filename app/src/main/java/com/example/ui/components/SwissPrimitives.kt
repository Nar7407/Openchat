package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SwissCanvas
import com.example.ui.theme.SwissConcrete
import com.example.ui.theme.SwissGridLine
import com.example.ui.theme.SwissInk
import com.example.ui.theme.SwissMuted
import com.example.ui.theme.SwissPaper
import com.example.ui.theme.SwissSignalRed
import com.example.ui.theme.SwissSubBorder
import com.example.ui.theme.SwissWhite

/**
 * Draws a crisp 12dp x 12dp architectural graph-paper pattern behind empty or secondary utility panels.
 */
fun Modifier.swissGraphPaper(
    gridStep: Dp = 12.dp,
    lineColor: Color = SwissGridLine,
    strokeWidth: Dp = 0.75.dp
): Modifier = this.drawBehind {
    val stepPx = gridStep.toPx()
    val strokePx = strokeWidth.toPx()
    if (stepPx <= 0f) return@drawBehind
    var x = stepPx
    while (x < size.width) {
        drawLine(
            color = lineColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokePx
        )
        x += stepPx
    }
    var y = stepPx
    while (y < size.height) {
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokePx
        )
        y += stepPx
    }
}

/**
 * Draws geometric crosshair (+) registration marks in the four corners of a panel.
 */
fun Modifier.swissRegistrationMarks(
    armLength: Dp = 4.dp,
    inset: Dp = 8.dp,
    color: Color = SwissInk,
    strokeWidth: Dp = 1.dp
): Modifier = this.drawWithContent {
    drawContent()
    val arm = armLength.toPx()
    val pad = inset.toPx()
    val stroke = strokeWidth.toPx()
    val corners = listOf(
        Offset(pad, pad),
        Offset(size.width - pad, pad),
        Offset(pad, size.height - pad),
        Offset(size.width - pad, size.height - pad)
    )
    corners.forEach { c ->
        drawLine(color, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), stroke)
        drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), stroke)
    }
}

enum class SwissButtonVariant {
    PRIMARY,   // Signal Red fill, crisp white text; snaps to obsidian ink on press
    INK,       // Obsidian black fill, canvas text; snaps to signal red on press
    OUTLINE    // White/canvas fill, 1px black border; snaps abruptly to black fill + canvas text on press
}

@Composable
fun SwissButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SwissButtonVariant = SwissButtonVariant.OUTLINE,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    minHeight: Dp = 48.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val active = (isPressed || isHovered) && enabled

    val (bg, fg, borderColor) = when {
        !enabled -> Triple(SwissConcrete, SwissMuted, SwissSubBorder)
        variant == SwissButtonVariant.PRIMARY -> if (active) {
            Triple(SwissInk, SwissWhite, SwissInk)
        } else {
            Triple(SwissSignalRed, SwissWhite, SwissInk)
        }
        variant == SwissButtonVariant.INK -> if (active) {
            Triple(SwissSignalRed, SwissWhite, SwissInk)
        } else {
            Triple(SwissInk, SwissCanvas, SwissInk)
        }
        else -> if (active) {
            Triple(SwissInk, SwissCanvas, SwissInk)
        } else {
            Triple(SwissPaper, SwissInk, SwissInk)
        }
    }

    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .background(bg, RectangleShape)
            .border(1.dp, borderColor, RectangleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SwissIconCell(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    inverted: Boolean = false,
    bordered: Boolean = true,
    cellSize: Dp = 48.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val active = (isPressed || isHovered) && enabled

    val (bg, fg) = when {
        !enabled -> SwissConcrete to SwissMuted
        accent -> if (active) SwissInk to SwissWhite else SwissSignalRed to SwissWhite
        inverted -> if (active) SwissSignalRed to SwissWhite else SwissInk to SwissCanvas
        else -> if (active) SwissInk to SwissCanvas else SwissPaper to SwissInk
    }

    Box(
        modifier = modifier
            .size(cellSize)
            .background(bg, RectangleShape)
            .then(if (bordered) Modifier.border(1.dp, SwissInk, RectangleShape) else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = fg,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SwissToggleCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useSignalRedWhenSelected: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val (bg, fg) = when {
        selected && useSignalRedWhenSelected -> SwissSignalRed to SwissWhite
        selected -> SwissInk to SwissWhite
        isPressed || isHovered -> SwissInk to SwissCanvas
        else -> SwissPaper to SwissInk
    }

    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .widthIn(min = 48.dp)
            .background(bg, RectangleShape)
            .border(1.dp, SwissInk, RectangleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 11.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SwissBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = SwissConcrete,
    contentColor: Color = SwissInk,
    borderColor: Color = SwissInk
) {
    Box(
        modifier = modifier
            .background(containerColor, RectangleShape)
            .border(1.dp, borderColor, RectangleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SwissPanelHeader(
    leftLabel: String,
    rightLabel: String? = null,
    rightAccent: Boolean = false,
    backgroundColor: Color = SwissConcrete,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = leftLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SwissInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!rightLabel.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = rightLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (rightAccent) SwissSignalRed else SwissMuted,
                    maxLines = 1
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SwissInk)
        )
    }
}

/**
 * Horizontal structural rule punctuated with an editorial asterisk (*) or slash (/) glyph.
 */
@Composable
fun SwissRuleDivider(
    symbol: String = "*",
    lineColor: Color = SwissInk,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(lineColor)
        )
        Text(
            text = " $symbol ",
            style = MaterialTheme.typography.labelSmall,
            color = lineColor
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(lineColor)
        )
    }
}

/**
 * Slim top utility header bar divided into flush 1px-bordered segmented cells.
 */
@Composable
fun SwissUtilityHeader(
    breadcrumb: String,
    title: String,
    leadingIcon: ImageVector,
    leadingContentDescription: String,
    onLeadingClick: () -> Unit,
    leadingTestTag: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(SwissPaper),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwissIconCell(
                icon = leadingIcon,
                contentDescription = leadingContentDescription,
                onClick = onLeadingClick,
                bordered = false,
                cellSize = 52.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(52.dp)
                    .testTag(leadingTestTag)
            )
            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(SwissInk)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = breadcrumb.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = SwissMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = SwissInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailingContent != null) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(SwissInk)
                )
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    content = trailingContent
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SwissInk)
        )
    }
}

@Composable
fun swissTextFieldColors(
    containerColor: Color = SwissPaper
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SwissInk,
    unfocusedTextColor = SwissInk,
    cursorColor = SwissSignalRed,
    focusedBorderColor = SwissInk,
    unfocusedBorderColor = SwissSubBorder,
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    focusedLabelColor = SwissInk,
    unfocusedLabelColor = SwissMuted,
    focusedPlaceholderColor = SwissMuted,
    unfocusedPlaceholderColor = SwissMuted
)

@Composable
fun SwissPanel(
    modifier: Modifier = Modifier,
    containerColor: Color = SwissPaper,
    borderColor: Color = SwissInk,
    content: @Composable () -> Unit
) {
    Surface(
        color = containerColor,
        shape = RectangleShape,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier,
        content = content
    )
}
