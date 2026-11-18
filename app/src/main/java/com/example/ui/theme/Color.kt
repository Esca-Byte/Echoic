package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.model.StudioThemePreset

@Immutable
data class StudioPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val glassSurface: Color,
    val subtleBorder: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val onPrimary: Color,
    val accentCoral: Color,
    val accentAmber: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDim: Color,
    val isLight: Boolean,
    val dynamicArtworkTint: Boolean,
    val photoScrim: Color = Color(0xFF0A0A0D),
    val photoForeground: Color = Color(0xFFF8FAFC),
    val photoMuted: Color = Color(0xFFCBD5E1)
)

fun resolveStudioPalette(
    preset: StudioThemePreset,
    amoledPureBlack: Boolean = false,
    dynamicArtworkTint: Boolean = true
): StudioPalette {
    val base = when (preset) {
        StudioThemePreset.OBSIDIAN_STUDIO -> StudioPalette(
            background = Color(0xFF0A0A0A),
            surface = Color(0xFF141418),
            surfaceElevated = Color(0xFF1C1C24),
            glassSurface = Color(0xD11E1E28),
            subtleBorder = Color(0xFF2A2A36),
            primary = Color(0xFF1ED760),
            secondary = Color(0xFFA855F7),
            tertiary = Color(0xFF00F5D4),
            onPrimary = Color(0xFF0A0A0A),
            accentCoral = Color(0xFFFB7185),
            accentAmber = Color(0xFFFBBF24),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8),
            textDim = Color(0xFF64748B),
            isLight = false,
            dynamicArtworkTint = dynamicArtworkTint
        )

        StudioThemePreset.CHAMPAGNE_VINYL -> StudioPalette(
            background = Color(0xFF0D0B09),
            surface = Color(0xFF171411),
            surfaceElevated = Color(0xFF221D18),
            glassSurface = Color(0xD81F1A15),
            subtleBorder = Color(0xFF342C24),
            primary = Color(0xFFE6B86E),
            secondary = Color(0xFFD97757),
            tertiary = Color(0xFFF5D08A),
            onPrimary = Color(0xFF14100B),
            accentCoral = Color(0xFFE57373),
            accentAmber = Color(0xFFF59E0B),
            textPrimary = Color(0xFFFAF6F0),
            textSecondary = Color(0xFFB3A697),
            textDim = Color(0xFF7D7164),
            isLight = false,
            dynamicArtworkTint = dynamicArtworkTint
        )

        StudioThemePreset.MIDNIGHT_VELVET -> StudioPalette(
            background = Color(0xFF07060D),
            surface = Color(0xFF12101F),
            surfaceElevated = Color(0xFF1B1830),
            glassSurface = Color(0xD8161326),
            subtleBorder = Color(0xFF2D264A),
            primary = Color(0xFFC084FC),
            secondary = Color(0xFF818CF8),
            tertiary = Color(0xFFE879F9),
            onPrimary = Color(0xFF0B0714),
            accentCoral = Color(0xFFF472B6),
            accentAmber = Color(0xFFFBBF24),
            textPrimary = Color(0xFFF5F3FF),
            textSecondary = Color(0xFFA39BBF),
            textDim = Color(0xFF6D658C),
            isLight = false,
            dynamicArtworkTint = dynamicArtworkTint
        )

        StudioThemePreset.NORDIC_FROST -> StudioPalette(
            background = Color(0xFF080D12),
            surface = Color(0xFF101923),
            surfaceElevated = Color(0xFF182432),
            glassSurface = Color(0xD8131E2B),
            subtleBorder = Color(0xFF24364A),
            primary = Color(0xFF38BDF8),
            secondary = Color(0xFF818CF8),
            tertiary = Color(0xFF2DD4BF),
            onPrimary = Color(0xFF06131C),
            accentCoral = Color(0xFFFB7185),
            accentAmber = Color(0xFFFBBF24),
            textPrimary = Color(0xFFF0F9FF),
            textSecondary = Color(0xFF91AAC2),
            textDim = Color(0xFF5E7790),
            isLight = false,
            dynamicArtworkTint = dynamicArtworkTint
        )

        StudioThemePreset.CRIMSON_VELVET -> StudioPalette(
            background = Color(0xFF0B090B),
            surface = Color(0xFF171216),
            surfaceElevated = Color(0xFF231B21),
            glassSurface = Color(0xD81F161D),
            subtleBorder = Color(0xFF382831),
            primary = Color(0xFFF43F5E),
            secondary = Color(0xFFFB7185),
            tertiary = Color(0xFFFDA4AF),
            onPrimary = Color(0xFF14060A),
            accentCoral = Color(0xFFFB7185),
            accentAmber = Color(0xFFF59E0B),
            textPrimary = Color(0xFFFFF1F2),
            textSecondary = Color(0xFFB2969F),
            textDim = Color(0xFF785D66),
            isLight = false,
            dynamicArtworkTint = dynamicArtworkTint
        )

        StudioThemePreset.PAPER_DAYLIGHT -> StudioPalette(
            background = Color(0xFFF4F1EA),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFEAE4D7),
            glassSurface = Color(0xEBF7F4ED),
            subtleBorder = Color(0xFFD7D0C2),
            primary = Color(0xFF0B8A4B),
            secondary = Color(0xFF6D28D9),
            tertiary = Color(0xFF0284C7),
            onPrimary = Color(0xFFFFFFFF),
            accentCoral = Color(0xFFDC2626),
            accentAmber = Color(0xFFD97706),
            textPrimary = Color(0xFF141519),
            textSecondary = Color(0xFF555B66),
            textDim = Color(0xFF7D838E),
            isLight = true,
            dynamicArtworkTint = dynamicArtworkTint
        )
    }

    return if (amoledPureBlack && !base.isLight) {
        base.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF09090C),
            surfaceElevated = Color(0xFF121218)
        )
    } else {
        base
    }
}

private val DefaultObsidianPalette = resolveStudioPalette(StudioThemePreset.OBSIDIAN_STUDIO)

val LocalStudioPalette = staticCompositionLocalOf { DefaultObsidianPalette }

// Dynamic Semantic Palette Tokens (Backed by LocalStudioPalette for live studio theme switching)
val PitchBlack: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.background

val CarbonSurface: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.surface

val CarbonElevated: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.surfaceElevated

val GlassSurface: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.glassSurface

val SubtleBorder: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.subtleBorder

val ElectricMint: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.primary

val CyberCyan: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.tertiary

val NeonViolet: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.secondary

val ThemeOnPrimary: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.onPrimary

val DeepIndigo = Color(0xFF4F46E5)

val SunsetCoral: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.accentCoral

val AmberPulse: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.accentAmber

val AlabasterWhite: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.textPrimary

val MutedSlate: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.textSecondary

val DimSlate: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.textDim

val PhotoScrimColor: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.photoScrim

val PhotoForegroundColor: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.photoForeground

val PhotoMutedColor: Color
    @Composable @ReadOnlyComposable
    get() = LocalStudioPalette.current.photoMuted

// Mood Card Gradients (Preserved vibrant editorial gradients)
val WorkoutGradientStart = Color(0xFFFF416C)
val WorkoutGradientEnd = Color(0xFFFF4B2B)

val FocusGradientStart = Color(0xFF00F5D4)
val FocusGradientEnd = Color(0xFF0077B6)

val MidnightGradientStart = Color(0xFF7F00FF)
val MidnightGradientEnd = Color(0xFFE100FF)

val ChillGradientStart = Color(0xFF11998E)
val ChillGradientEnd = Color(0xFF38EF7D)
