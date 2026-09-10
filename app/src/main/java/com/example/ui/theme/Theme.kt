package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Cycle order mirrors the on-screen sequence: Light, Dark, Sepia. */
enum class ThemeMode {
    LIGHT,
    DARK,
    SEPIA;

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

/** Shared by every theme-driven crossfade, so the scheme and the page ground move together. */
const val ThemeTransitionMs = 420

private val NavyBackground = Color(0xFF0B1220)
private val NavySurface = Color(0xFF131E31)
private val NavySurfaceVariant = Color(0xFF1C2A40)
private val NavyOutline = Color(0xFF344866)
private val SepiaBackground = Color(0xFFF4E9D5)
private val SepiaSurface = Color(0xFFFFF8EA)
private val SepiaSurfaceVariant = Color(0xFFE8D7B8)
private val SepiaOutline = Color(0xFFC8AE80)
private val LightTextPrimary = Color(0xFF1C1C1E)
private val LightTextSecondary = Color(0xFF6D6D72)
private val SepiaTextPrimary = Color(0xFF3D2A18)
private val SepiaTextSecondary = Color(0xFF725A3E)

private val DarkColorScheme = darkColorScheme(
    primary = AccentOnDark, onPrimary = NavyBackground, primaryContainer = NavySurfaceVariant,
    onPrimaryContainer = TextPrimary, secondary = CyanAccent, onSecondary = NavyBackground,
    secondaryContainer = NavySurfaceVariant, onSecondaryContainer = TextPrimary, tertiary = AccentOnDark,
    background = NavyBackground, onBackground = TextPrimary, surface = NavySurface, onSurface = TextPrimary,
    surfaceVariant = NavySurfaceVariant, onSurfaceVariant = TextSecondary, outline = NavyOutline, error = CrimsonInactive,
    onError = NavyBackground,
    // Bottom sheets, menus and the other elevated M3 containers read these; left unset they fall
    // back to the baseline purple-tinted greys, which read as a foreign surface against the palette.
    surfaceDim = NavyBackground, surfaceBright = NavySurfaceVariant,
    surfaceContainerLowest = NavyBackground, surfaceContainerLow = NavySurface, surfaceContainer = NavySurface,
    surfaceContainerHigh = NavySurfaceVariant, surfaceContainerHighest = NavySurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = AccentOnLight, onPrimary = LightSurface, primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = LightTextPrimary, secondary = CyanAccent, onSecondary = LightSurface,
    secondaryContainer = LightSurfaceVariant, onSecondaryContainer = LightTextPrimary, tertiary = AccentOnLight,
    background = LightBackground, onBackground = LightTextPrimary, surface = LightSurface, onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightTextSecondary, outline = LightSurfaceElevated, error = CrimsonOnLight,
    onError = Color.White,
    surfaceDim = LightSurfaceVariant, surfaceBright = LightSurface,
    surfaceContainerLowest = LightSurface, surfaceContainerLow = LightSurface, surfaceContainer = LightBackground,
    surfaceContainerHigh = LightSurfaceVariant, surfaceContainerHighest = LightSurfaceVariant,
)

private val SepiaColorScheme = lightColorScheme(
    primary = AmberOnSepia, onPrimary = SepiaSurface, primaryContainer = SepiaSurfaceVariant,
    onPrimaryContainer = SepiaTextPrimary, secondary = Color(0xFF586C55), onSecondary = SepiaSurface,
    secondaryContainer = SepiaSurfaceVariant, onSecondaryContainer = SepiaTextPrimary, tertiary = AmberOnSepia,
    background = SepiaBackground, onBackground = SepiaTextPrimary, surface = SepiaSurface, onSurface = SepiaTextPrimary,
    surfaceVariant = SepiaSurfaceVariant, onSurfaceVariant = SepiaTextSecondary, outline = SepiaOutline, error = CrimsonOnLight,
    onError = Color.White,
    surfaceDim = SepiaSurfaceVariant, surfaceBright = SepiaSurface,
    surfaceContainerLowest = SepiaSurface, surfaceContainerLow = SepiaSurface, surfaceContainer = SepiaBackground,
    surfaceContainerHigh = SepiaSurfaceVariant, surfaceContainerHighest = SepiaSurfaceVariant,
)

fun backgroundFor(themeMode: ThemeMode): Color = when (themeMode) {
    ThemeMode.DARK -> NavyBackground
    ThemeMode.LIGHT -> LightBackground
    ThemeMode.SEPIA -> SepiaBackground
}

/**
 * The card/panel ground each appearance draws on. Paired with [backgroundFor] and [accentFor] so a
 * test can check a colour is legible on the surface it actually lands on.
 */
fun surfaceFor(themeMode: ThemeMode): Color = when (themeMode) {
    ThemeMode.DARK -> NavySurface
    ThemeMode.LIGHT -> LightSurface
    ThemeMode.SEPIA -> SepiaSurface
}

/** The accent each appearance uses for `primary`, including as a text colour. */
fun accentFor(themeMode: ThemeMode): Color = when (themeMode) {
    ThemeMode.DARK -> AccentOnDark
    ThemeMode.LIGHT -> AccentOnLight
    ThemeMode.SEPIA -> AmberOnSepia
}

/**
 * The colour the page ground deepens to further down the screen. Dark sinks to true black so the
 * top-to-bottom shift reads as clearly there as the tinted grounds make it on light and sepia; the
 * light appearances already contrast against their own ground and keep it.
 */
fun pageFloorFor(themeMode: ThemeMode): Color =
    if (themeMode == ThemeMode.DARK) Color.Black else backgroundFor(themeMode)

/** Where down the screen [pageFloorFor] is reached, as a fraction of its height. */
const val PageFloorStop = 0.62f

/**
 * Alpha for the top-of-screen accent glow, as (near-corner, far-corner). A flat alpha reads
 * differently per appearance: [AmberOnSepia] barely shifts a background that is already warm, so
 * it needs more of it than the same blue reads as against a light background.
 */
fun backgroundGlowAlpha(themeMode: ThemeMode): Pair<Float, Float> = when (themeMode) {
    ThemeMode.DARK -> 0.16f to 0.09f
    ThemeMode.LIGHT -> 0.14f to 0.07f
    ThemeMode.SEPIA -> 0.30f to 0.16f
}

@Composable
private fun animatedColor(target: Color, label: String): Color =
    animateColorAsState(target, tween(ThemeTransitionMs), label = label).value

@Composable
fun MyApplicationTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val targetScheme = when (themeMode) {
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.SEPIA -> SepiaColorScheme
    }
    val animatedScheme = targetScheme.copy(
        primary = animatedColor(targetScheme.primary, "primary"),
        onPrimary = animatedColor(targetScheme.onPrimary, "onPrimary"),
        primaryContainer = animatedColor(targetScheme.primaryContainer, "primaryContainer"),
        onPrimaryContainer = animatedColor(targetScheme.onPrimaryContainer, "onPrimaryContainer"),
        secondary = animatedColor(targetScheme.secondary, "secondary"),
        onSecondary = animatedColor(targetScheme.onSecondary, "onSecondary"),
        secondaryContainer = animatedColor(targetScheme.secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = animatedColor(targetScheme.onSecondaryContainer, "onSecondaryContainer"),
        tertiary = animatedColor(targetScheme.tertiary, "tertiary"),
        background = animatedColor(targetScheme.background, "background"),
        onBackground = animatedColor(targetScheme.onBackground, "onBackground"),
        surface = animatedColor(targetScheme.surface, "surface"),
        onSurface = animatedColor(targetScheme.onSurface, "onSurface"),
        surfaceVariant = animatedColor(targetScheme.surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = animatedColor(targetScheme.onSurfaceVariant, "onSurfaceVariant"),
        surfaceDim = animatedColor(targetScheme.surfaceDim, "surfaceDim"),
        surfaceBright = animatedColor(targetScheme.surfaceBright, "surfaceBright"),
        surfaceContainerLowest = animatedColor(targetScheme.surfaceContainerLowest, "surfaceContainerLowest"),
        surfaceContainerLow = animatedColor(targetScheme.surfaceContainerLow, "surfaceContainerLow"),
        surfaceContainer = animatedColor(targetScheme.surfaceContainer, "surfaceContainer"),
        surfaceContainerHigh = animatedColor(targetScheme.surfaceContainerHigh, "surfaceContainerHigh"),
        surfaceContainerHighest = animatedColor(targetScheme.surfaceContainerHighest, "surfaceContainerHighest"),
        outline = animatedColor(targetScheme.outline, "outline"),
        error = animatedColor(targetScheme.error, "error"),
        onError = animatedColor(targetScheme.onError, "onError"),
    )
    val isLightTheme = themeMode == ThemeMode.LIGHT || themeMode == ThemeMode.SEPIA
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = animatedScheme.background.toArgb()
            window.navigationBarColor = animatedScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLightTheme
        }
    }
    MaterialTheme(colorScheme = animatedScheme, typography = Typography, content = content)
}
