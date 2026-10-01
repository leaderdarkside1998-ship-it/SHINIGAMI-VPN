package com.v2ray.ang.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Shared helpers for the futuristic "HUD" look used by the main-screen banner, group chips and
 * subscription usage bar. Everything is derived from the active theme's accent colors so the
 * look follows the selected theme instead of hardcoding a single bright palette.
 */

/** Picks a dark or light foreground that stays readable on top of [background]. */
internal fun readableOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF041018) else Color.White

/** Thin illuminated outline: brighter at both ends, softer in the middle. */
internal fun hudOutlineBrush(accent: Color, strength: Float = 1f): Brush = Brush.horizontalGradient(
    listOf(
        accent.copy(alpha = 0.95f * strength),
        accent.copy(alpha = 0.35f * strength),
        accent.copy(alpha = 0.95f * strength)
    )
)

/**
 * Fill shared by the round/pill HUD controls (bottom bar and top-bar action buttons): the card
 * surface with a little of the theme accent mixed in, [strength] being how much
 * (0 = plain surface, 1 = solid accent).
 */
@Composable
internal fun hudControlFill(strength: Float): Brush {
    val colors = MaterialTheme.colorScheme
    val base = colors.surfaceContainerHigh
    return Brush.verticalGradient(
        listOf(
            lerp(base, colors.primary, strength * 0.5f),
            lerp(base, colors.primary, strength)
        )
    )
}

/**
 * Server-card palette. The card surfaces and text colors are FIXED (dark navy + explicit
 * high-contrast text) so no accent theme, light/dark mode or Material-You dynamic palette can
 * ever make the name, address or protocol unreadable. Only small accents follow the theme.
 */
internal object ServerCardColors {
    val NavySelectedTop = Color(0xFF0F2342)
    val NavySelectedBottom = Color(0xFF08162B)
    val NavyTop = Color(0xFF0D1A2D)
    val NavyBottom = Color(0xFF091324)
    val BorderIdle = Color(0xFF263A55)
    val IndicatorIdle = Color(0xFF3B5272)
    val TextName = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9DB2CC)
    val TextTag = Color(0xFFC3D3E8)
    val IconMuted = Color(0xFF8CA4C3)
    val PingGood = Color(0xFF5CE1E6)
    val FallbackAccent = Color(0xFF3FD7E6)
}

/**
 * Turns the active theme's primary color into an accent that is always luminous enough to show
 * on the fixed dark-navy card (light themes use dark primaries, e.g. black or deep blue).
 * Colorless themes (no hue) fall back to the default cyan.
 */
internal fun serverCardAccent(primary: Color): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(primary.toArgb(), hsl)
    if (hsl[1] < 0.15f) return ServerCardColors.FallbackAccent
    hsl[1] = hsl[1].coerceIn(0.55f, 0.80f)
    hsl[2] = hsl[2].coerceIn(0.62f, 0.78f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}
