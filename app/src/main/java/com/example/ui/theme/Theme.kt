package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SwissGridColorScheme = lightColorScheme(
    primary = SwissSignalRed,
    onPrimary = SwissWhite,
    primaryContainer = SwissInk,
    onPrimaryContainer = SwissWhite,
    secondary = SwissInk,
    onSecondary = SwissCanvas,
    secondaryContainer = SwissConcrete,
    onSecondaryContainer = SwissInk,
    tertiary = SwissSignalRed,
    onTertiary = SwissWhite,
    background = SwissCanvas,
    onBackground = SwissInk,
    surface = SwissPaper,
    onSurface = SwissInk,
    surfaceVariant = SwissConcrete,
    onSurfaceVariant = SwissMuted,
    surfaceContainerLowest = SwissPaper,
    surfaceContainerLow = SwissSubtleFill,
    surfaceContainer = SwissCanvas,
    surfaceContainerHigh = SwissConcrete,
    surfaceContainerHighest = SwissGridLine,
    outline = SwissInk,
    outlineVariant = SwissSubBorder,
    error = SwissSignalRed,
    onError = SwissWhite,
    errorContainer = SwissSignalRedTint,
    onErrorContainer = SwissInk
)

// Enforce strict zero border-radius across every Material 3 component
val AllChatShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

@Composable
fun AllChatTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SwissGridColorScheme,
        typography = AllChatTypography,
        shapes = AllChatShapes,
        content = content
    )
}
