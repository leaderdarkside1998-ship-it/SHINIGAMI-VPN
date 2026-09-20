package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// SHINIGAMI VPN: an accent theme is just the "primary" brand hue used across the
// app bar, active drawer item, main FAB ring, etc. Everything else (surfaces,
// backgrounds, error colors...) stays shared between accents so the app keeps a
// consistent, readable look no matter which accent the user picks.
interface AccentColorSet {
    val primaryLight: Color
    val onPrimaryLight: Color
    val primaryContainerLight: Color
    val onPrimaryContainerLight: Color
    val primaryDark: Color
    val onPrimaryDark: Color
    val primaryContainerDark: Color
    val onPrimaryContainerDark: Color
}

/**
 * Lets the user pick any of the ~16 million RGB colors as the app's accent, instead of being
 * limited to the fixed [AppAccentTheme] presets. Light/dark and container variants are derived
 * from the picked color by keeping its hue+saturation and only shifting lightness.
 */
class CustomAccentColors(base: Color) : AccentColorSet {
    private val hsl = FloatArray(3).also {
        androidx.core.graphics.ColorUtils.colorToHSL(base.toArgb(), it)
    }

    private fun withLightness(lightness: Float): Color {
        val arr = floatArrayOf(hsl[0], hsl[1].coerceAtLeast(0.35f), lightness)
        return Color(androidx.core.graphics.ColorUtils.HSLToColor(arr))
    }

    override val primaryLight = withLightness(0.40f)
    override val onPrimaryLight = Color.White
    override val primaryContainerLight = withLightness(0.88f)
    override val onPrimaryContainerLight = withLightness(0.16f)
    override val primaryDark = withLightness(0.75f)
    override val onPrimaryDark = withLightness(0.18f)
    override val primaryContainerDark = withLightness(0.28f)
    override val onPrimaryContainerDark = withLightness(0.90f)
}

enum class AppAccentTheme(
    override val primaryLight: Color,
    override val onPrimaryLight: Color,
    override val primaryContainerLight: Color,
    override val onPrimaryContainerLight: Color,
    override val primaryDark: Color,
    override val onPrimaryDark: Color,
    override val primaryContainerDark: Color,
    override val onPrimaryContainerDark: Color
) : AccentColorSet {
    // Default SHINIGAMI look: white background + turquoise accent.
    Turquoise(
        primaryLight = Color(0xFF00A99D),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFB2F1EA),
        onPrimaryContainerLight = Color(0xFF00201C),
        primaryDark = Color(0xFF5FE8D9),
        onPrimaryDark = Color(0xFF00382F),
        primaryContainerDark = Color(0xFF00504A),
        onPrimaryContainerDark = Color(0xFFB2F1EA)
    ),
    Blue(
        primaryLight = Color(0xFF0061A4),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFD1E4FF),
        onPrimaryContainerLight = Color(0xFF001D36),
        primaryDark = Color(0xFF9ECAFF),
        onPrimaryDark = Color(0xFF003258),
        primaryContainerDark = Color(0xFF00497D),
        onPrimaryContainerDark = Color(0xFFD1E4FF)
    ),
    Purple(
        primaryLight = Color(0xFF7B4EA8),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFEEDBFF),
        onPrimaryContainerLight = Color(0xFF2E0052),
        primaryDark = Color(0xFFDDB6FF),
        onPrimaryDark = Color(0xFF45146B),
        primaryContainerDark = Color(0xFF5D2C83),
        onPrimaryContainerDark = Color(0xFFEEDBFF)
    ),
    Red(
        primaryLight = Color(0xFFBA1B2C),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFFFDAD9),
        onPrimaryContainerLight = Color(0xFF410008),
        primaryDark = Color(0xFFFFB3AF),
        onPrimaryDark = Color(0xFF680011),
        primaryContainerDark = Color(0xFF930019),
        onPrimaryContainerDark = Color(0xFFFFDAD9)
    ),
    // Original PattNG/v2rayNG accent, kept for anyone who prefers the classic look.
    Orange(
        primaryLight = Color(0xFF000000),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFE0E0E0),
        onPrimaryContainerLight = Color(0xFF000000),
        primaryDark = Color(0xFFC0C0C0),
        onPrimaryDark = Color(0xFF303030),
        primaryContainerDark = Color(0xFF474747),
        onPrimaryContainerDark = Color(0xFFE0E0E0)
    );

    companion object {
        fun fromKey(key: String?): AppAccentTheme =
            entries.firstOrNull { it.name == key } ?: Turquoise
    }
}

private fun buildLightColorScheme(accent: AccentColorSet) = lightColorScheme(
    primary = accent.primaryLight,
    onPrimary = accent.onPrimaryLight,
    primaryContainer = accent.primaryContainerLight,
    onPrimaryContainer = accent.onPrimaryContainerLight,
    secondary = Color(0xFFf97910), // Orange
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFFFE8D6), // Pale Orange
    onSecondaryContainer = Color(0xFF2B1700), // Dark Brown
    tertiary = Color(0xFF009966), // Green
    onTertiary = Color(0xFFFFFFFF), // White
    tertiaryContainer = Color(0xFFA0F2D0), // Light Green
    onTertiaryContainer = Color(0xFF00201A), // Dark Teal
    error = Color(0xFFBA1A1A), // Red
    errorContainer = Color(0xFFFFDAD6), // Light Red
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF410002), // Dark Red
    background = Color(0xFFFFFFFF), // White
    onBackground = Color(0xFF1C1B1F), // Near Black
    surface = Color(0xFFFFFFFF), // White
    onSurface = Color(0xFF1C1B1F), // Near Black
    surfaceVariant = Color(0xFFE7E0EC), // Light Purple Gray
    onSurfaceVariant = Color(0xFF49454F), // Dark Gray
    outline = Color(0xFF79747E), // Medium Gray
    outlineVariant = Color(0xFFCAC4D0), // Light Gray
    inverseSurface = Color(0xFF313033), // Dark Gray
    inverseOnSurface = Color(0xFFF4EFF4), // Very Light Gray
    inversePrimary = accent.primaryDark,
    scrim = Color(0xFF000000), // Black
    surfaceTint = accent.primaryLight,
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFF7F7F7), // Very Light Gray
    surfaceContainer = Color(0xFFF1F1F1), // Light Gray
    surfaceContainerHigh = Color(0xFFEBEBEB), // Light Gray
    surfaceContainerHighest = Color(0xFFE5E5E5), // Light Gray
)

private fun buildDarkColorScheme(accent: AccentColorSet) = darkColorScheme(
    primary = accent.primaryDark,
    onPrimary = accent.onPrimaryDark,
    primaryContainer = accent.primaryContainerDark,
    onPrimaryContainer = accent.onPrimaryContainerDark,
    secondary = Color(0xFFf97910), // Orange
    onSecondary = Color(0xFF4E2600), // Dark Brown
    secondaryContainer = Color(0xFF6F3800), // Brown
    onSecondaryContainer = Color(0xFFFFE8D6), // Pale Orange
    tertiary = Color(0xFF83D6B5), // Mint Green
    onTertiary = Color(0xFF00382E), // Dark Teal
    tertiaryContainer = Color(0xFF005143), // Teal
    onTertiaryContainer = Color(0xFFA0F2D0), // Light Green
    error = Color(0xFFFFB4AB), // Light Red
    errorContainer = Color(0xFF93000A), // Dark Red
    onError = Color(0xFF690005), // Deep Red
    onErrorContainer = Color(0xFFFFDAD6), // Light Red
    background = Color(0xFF1C1B1F), // Near Black
    onBackground = Color(0xFFE6E1E5), // Light Gray
    surface = Color(0xFF1C1B1F), // Near Black
    onSurface = Color(0xFFE6E1E5), // Light Gray
    surfaceVariant = Color(0xFF49454F), // Dark Gray
    onSurfaceVariant = Color(0xFFCAC4D0), // Light Gray
    outline = Color(0xFF938F99), // Grayish Purple
    outlineVariant = Color(0xFF49454F), // Dark Gray
    inverseSurface = Color(0xFFE6E1E5), // Light Gray
    inverseOnSurface = Color(0xFF1C1B1F), // Near Black
    inversePrimary = accent.primaryLight,
    scrim = Color(0xFF000000), // Black
    surfaceTint = accent.primaryDark,
    surfaceContainerLowest = Color(0xFF0F0F12), // Near Black
    surfaceContainerLow = Color(0xFF1A191D), // Dark Gray
    surfaceContainer = Color(0xFF1E1D21), // Dark Gray
    surfaceContainerHigh = Color(0xFF282729), // Dark Gray
    surfaceContainerHighest = Color(0xFF333234), // Dark Gray
)

// Semantic Colors
val colorPing = Color(0xFF009966) // Green
val colorPingRed = Color(0xFFFF0099) // Pink Red
val colorConfigType = Color(0xFFf97910) // Orange
val colorFabActive = Color(0xFFf97910) // Orange
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray
val colorFabInactiveDark = Color(0xFF646464) // Dark Gray
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF424242) // Dark Gray

// Toast Colors 70%
val toastNormalBgLight = Color(0xB3353A3E) // Dark Gray
val toastNormalBgDark = Color(0xB34A4F54) // Darker Gray
val toastSuccessBg = Color(0xB3388E3C) // Green
val toastErrorBg = Color(0xB3D50000) // Red
val toastInfoBg = Color(0xB33F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    // Default accent is Turquoise: this is the SHINIGAMI VPN standard white/turquoise look.
    private val _accentTheme = MutableStateFlow(
        AppAccentTheme.fromKey(MmkvManager.decodeSettingsString(AppConfig.PREF_APP_ACCENT_THEME, null))
    )
    val accentTheme: StateFlow<AppAccentTheme> = _accentTheme.asStateFlow()

    private val _useCustomAccent = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_ACCENT, false)
    )
    val useCustomAccent: StateFlow<Boolean> = _useCustomAccent.asStateFlow()

    private val _customAccentColor = MutableStateFlow(readCustomAccentColor())
    val customAccentColor: StateFlow<Color> = _customAccentColor.asStateFlow()

    private fun readCustomAccentColor(): Color {
        val hex = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_ACCENT_COLOR, null)
        return try {
            if (hex.isNullOrEmpty()) Color(0xFF00A99D) else Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            Color(0xFF00A99D)
        }
    }

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun setAccentTheme(theme: AppAccentTheme) {
        MmkvManager.encodeSettings(AppConfig.PREF_APP_ACCENT_THEME, theme.name)
        _accentTheme.value = theme
        MmkvManager.encodeSettings(AppConfig.PREF_USE_CUSTOM_ACCENT, false)
        _useCustomAccent.value = false
    }

    /** Sets a custom, freely-picked (16.7M possible) RGB color as the app's accent. */
    fun setCustomAccentColor(color: Color) {
        val hex = String.format("#%06X", color.toArgb() and 0xFFFFFF)
        MmkvManager.encodeSettings(AppConfig.PREF_CUSTOM_ACCENT_COLOR, hex)
        MmkvManager.encodeSettings(AppConfig.PREF_USE_CUSTOM_ACCENT, true)
        _customAccentColor.value = color
        _useCustomAccent.value = true
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
        _accentTheme.value =
            AppAccentTheme.fromKey(MmkvManager.decodeSettingsString(AppConfig.PREF_APP_ACCENT_THEME, null))
        _useCustomAccent.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_CUSTOM_ACCENT, false)
        _customAccentColor.value = readCustomAccentColor()
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val accent by ThemeManager.accentTheme.collectAsState()
    val useCustomAccent by ThemeManager.useCustomAccent.collectAsState()
    val customAccentColor by ThemeManager.customAccentColor.collectAsState()
    val context = LocalContext.current
    val effectiveAccent: AccentColorSet = if (useCustomAccent) CustomAccentColors(customAccentColor) else accent
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> buildDarkColorScheme(effectiveAccent)
        else -> buildLightColorScheme(effectiveAccent)
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
