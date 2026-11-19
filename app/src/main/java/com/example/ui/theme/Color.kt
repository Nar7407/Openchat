package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class SwissPalette(
    val canvas: Color,
    val paper: Color,
    val concrete: Color,
    val subtleFill: Color,
    val ink: Color,
    val muted: Color,
    val dim: Color,
    val gridLine: Color,
    val subBorder: Color,
    val accent: Color,
    val accentTint: Color,
    val onAccent: Color,
    val verifiedInk: Color = Color(0xFF0E6B3E),
    val verifiedTint: Color = Color(0xFFE7F4ED),
    val warnTint: Color = Color(0xFFFFF4E0),
    val warnInk: Color = Color(0xFF8A4B00)
)

enum class SwissThemePreset(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val swatch: Triple<Color, Color, Color>,
    val palette: SwissPalette
) {
    SWISS_SIGNAL(
        id = "swiss_signal",
        displayName = "Swiss Signal",
        subtitle = "Off-White · Obsidian Black · Signal Red",
        swatch = Triple(Color(0xFFEFEFEF), Color(0xFF111111), Color(0xFFFF3B30)),
        palette = SwissPalette(
            canvas = Color(0xFFEFEFEF),
            paper = Color(0xFFFFFFFF),
            concrete = Color(0xFFE5E5E5),
            subtleFill = Color(0xFFF7F7F5),
            ink = Color(0xFF111111),
            muted = Color(0xFF555555),
            dim = Color(0xFF777777),
            gridLine = Color(0xFFCCCCCC),
            subBorder = Color(0xFFBBBBBB),
            accent = Color(0xFFFF3B30),
            accentTint = Color(0xFFFFECEA),
            onAccent = Color(0xFFFFFFFF)
        )
    ),
    CHARCOAL_SAND_BLUE(
        id = "charcoal_sand_blue",
        displayName = "Charcoal + Sand + Electric Blue",
        subtitle = "Warm Sand · Charcoal · Electric Blue",
        swatch = Triple(Color(0xFFF2ECE1), Color(0xFF1F2429), Color(0xFF0066FF)),
        palette = SwissPalette(
            canvas = Color(0xFFF2ECE1),
            paper = Color(0xFFFDFBF7),
            concrete = Color(0xFFE6DEC8),
            subtleFill = Color(0xFFF7F2E8),
            ink = Color(0xFF1F2429),
            muted = Color(0xFF555C63),
            dim = Color(0xFF757C84),
            gridLine = Color(0xFFD5CCB8),
            subBorder = Color(0xFFC2B8A3),
            accent = Color(0xFF0066FF),
            accentTint = Color(0xFFE6F0FF),
            onAccent = Color(0xFFFFFFFF)
        )
    ),
    NAVY_WHITE_PINK(
        id = "navy_white_pink",
        displayName = "Navy + White + Pink",
        subtitle = "Crisp White · Deep Navy · Signal Pink",
        swatch = Triple(Color(0xFFF4F7FB), Color(0xFF0B1F4B), Color(0xFFFF2E88)),
        palette = SwissPalette(
            canvas = Color(0xFFF4F7FB),
            paper = Color(0xFFFFFFFF),
            concrete = Color(0xFFE3EAF4),
            subtleFill = Color(0xFFF9FBFE),
            ink = Color(0xFF0B1F4B),
            muted = Color(0xFF47587A),
            dim = Color(0xFF6B7B9C),
            gridLine = Color(0xFFCAD5E8),
            subBorder = Color(0xFFB5C4DC),
            accent = Color(0xFFFF2E88),
            accentTint = Color(0xFFFFE8F2),
            onAccent = Color(0xFFFFFFFF)
        )
    ),
    CREAM_INK_ORANGE(
        id = "cream_ink_orange",
        displayName = "Cream + Ink Black + Burnt Orange",
        subtitle = "Warm Cream · Ink Black · Burnt Orange",
        swatch = Triple(Color(0xFFFAF3E6), Color(0xFF111111), Color(0xFFE85D04)),
        palette = SwissPalette(
            canvas = Color(0xFFFAF3E6),
            paper = Color(0xFFFFFCF6),
            concrete = Color(0xFFEDE2CE),
            subtleFill = Color(0xFFFDF8EE),
            ink = Color(0xFF111111),
            muted = Color(0xFF595248),
            dim = Color(0xFF7A7165),
            gridLine = Color(0xFFDDD1BC),
            subBorder = Color(0xFFC7B9A1),
            accent = Color(0xFFE85D04),
            accentTint = Color(0xFFFFEFE5),
            onAccent = Color(0xFFFFFFFF)
        )
    ),
    CLEAN_TECH_SAAS(
        id = "clean_tech_saas",
        displayName = "Clean Tech & SaaS",
        subtitle = "Soft Slate · Deep Navy · Signal Blue",
        swatch = Triple(Color(0xFFF8FAFC), Color(0xFF00246B), Color(0xFF0057FF)),
        palette = SwissPalette(
            canvas = Color(0xFFF8FAFC),
            paper = Color(0xFFFFFFFF),
            concrete = Color(0xFFCADCFC),
            subtleFill = Color(0xFFF1F5FB),
            ink = Color(0xFF00246B),
            muted = Color(0xFF1E293B),
            dim = Color(0xFF475569),
            gridLine = Color(0xFFCBD5E1),
            subBorder = Color(0xFF94A3B8),
            accent = Color(0xFF0057FF),
            accentTint = Color(0xFFE8F0FF),
            onAccent = Color(0xFFFFFFFF)
        )
    ),
    INDIGO_BLUSH_CORAL(
        id = "indigo_blush_coral",
        displayName = "Indigo + Blush + Coral",
        subtitle = "Warm Off-White · Deep Indigo · Coral Red",
        swatch = Triple(Color(0xFFFCF6F5), Color(0xFF2F3C7E), Color(0xFFF96167)),
        palette = SwissPalette(
            canvas = Color(0xFFFCF6F5),
            paper = Color(0xFFFFFFFF),
            concrete = Color(0xFFFBEAEB),
            subtleFill = Color(0xFFFDF2F2),
            ink = Color(0xFF2F3C7E),
            muted = Color(0xFF1A1A1A),
            dim = Color(0xFF5D627A),
            gridLine = Color(0xFFE8D5D8),
            subBorder = Color(0xFFD6BFC3),
            accent = Color(0xFFF96167),
            accentTint = Color(0xFFFFEBEF),
            onAccent = Color(0xFFFFFFFF),
            warnTint = Color(0xFFFEF9C3),
            warnInk = Color(0xFF713F12)
        )
    ),
    PORCELAIN_TEAL_ROSE(
        id = "porcelain_teal_rose",
        displayName = "Porcelain + Teal + Rose",
        subtitle = "Porcelain · Deep Teal-Charcoal · Vibrant Rose",
        swatch = Triple(Color(0xFFF8F7F4), Color(0xFF021F28), Color(0xFFF44D78)),
        palette = SwissPalette(
            canvas = Color(0xFFF8F7F4),
            paper = Color(0xFFFFFFFF),
            concrete = Color(0xFFDFFCF3),
            subtleFill = Color(0xFFF0FBF7),
            ink = Color(0xFF021F28),
            muted = Color(0xFF2E525C),
            dim = Color(0xFF52727B),
            gridLine = Color(0xFFC5DDD7),
            subBorder = Color(0xFF00B5B5),
            accent = Color(0xFFF44D78),
            accentTint = Color(0xFFFFEBF0),
            onAccent = Color(0xFFFFFFFF),
            verifiedInk = Color(0xFF007A7A),
            verifiedTint = Color(0xFFDFFCF3)
        )
    );

    companion object {
        fun fromId(id: String?): SwissThemePreset =
            entries.firstOrNull { it.id == id } ?: SWISS_SIGNAL
    }
}

object SwissThemeState {
    var activePreset by mutableStateOf(SwissThemePreset.SWISS_SIGNAL)
    val current: SwissPalette get() = activePreset.palette
}

// Reactive Technical Swiss Grid color accessors (auto-recompose on /themes switch)
val SwissCanvas: Color get() = SwissThemeState.current.canvas
val SwissPaper: Color get() = SwissThemeState.current.paper
val SwissConcrete: Color get() = SwissThemeState.current.concrete
val SwissSubtleFill: Color get() = SwissThemeState.current.subtleFill

val SwissInk: Color get() = SwissThemeState.current.ink
val SwissMuted: Color get() = SwissThemeState.current.muted
val SwissDim: Color get() = SwissThemeState.current.dim

val SwissGridLine: Color get() = SwissThemeState.current.gridLine
val SwissSubBorder: Color get() = SwissThemeState.current.subBorder

val SwissSignalRed: Color get() = SwissThemeState.current.accent
val SwissSignalRedTint: Color get() = SwissThemeState.current.accentTint
val SwissWhite: Color get() = SwissThemeState.current.onAccent

val SwissVerifiedInk: Color get() = SwissThemeState.current.verifiedInk
val SwissVerifiedTint: Color get() = SwissThemeState.current.verifiedTint
val SwissWarnTint: Color get() = SwissThemeState.current.warnTint
val SwissWarnInk: Color get() = SwissThemeState.current.warnInk
