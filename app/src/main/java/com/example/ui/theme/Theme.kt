package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

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
    val palette = SwissThemeState.current
    val colorScheme = lightColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.ink,
        onPrimaryContainer = palette.onAccent,
        secondary = palette.ink,
        onSecondary = palette.canvas,
        secondaryContainer = palette.concrete,
        onSecondaryContainer = palette.ink,
        tertiary = palette.accent,
        onTertiary = palette.onAccent,
        background = palette.canvas,
        onBackground = palette.ink,
        surface = palette.paper,
        onSurface = palette.ink,
        surfaceVariant = palette.concrete,
        onSurfaceVariant = palette.muted,
        surfaceContainerLowest = palette.paper,
        surfaceContainerLow = palette.subtleFill,
        surfaceContainer = palette.canvas,
        surfaceContainerHigh = palette.concrete,
        surfaceContainerHighest = palette.gridLine,
        outline = palette.ink,
        outlineVariant = palette.subBorder,
        error = palette.accent,
        onError = palette.onAccent,
        errorContainer = palette.accentTint,
        onErrorContainer = palette.ink
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AllChatTypography,
        shapes = AllChatShapes,
        content = content
    )
}
