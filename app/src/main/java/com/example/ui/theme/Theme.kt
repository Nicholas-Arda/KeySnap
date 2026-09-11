package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** Cycle order mirrors the on-screen sequence: Light, Dark, Sepia. */
enum class ThemeMode {
    LIGHT,
    DARK,
    SEPIA;

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

/** Shared by every theme-driven crossfade, so the scheme and the page ground move together. */
const val ThemeTransitionMs = 525

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

/**
 * A soft radial glow on the page ground, fading to clear at 70% of its radius. [x] is a fraction of
 * the width; [y] is dp from the top, or a fraction of the ground's height when [yFraction] is set.
 */
@Immutable
data class GroundGlow(
    val color: Color,
    val x: Float,
    val y: Float,
    val rx: Dp,
    val ry: Dp = rx,
    val yFraction: Boolean = false,
)

/** A colour stop down the ground: dp from the top, or a fraction of its height when [fraction] is set. */
@Immutable
data class GroundStop(val at: Float, val color: Color, val fraction: Boolean = false)

/** The coloured ground Home and Shortcuts scroll over. Glows are listed top layer first. */
@Immutable
data class PageGround(val stops: List<GroundStop>, val glows: List<GroundGlow>)

// Blue family only: pink and magenta glows were tried and rejected.
private val LightGround = PageGround(
    stops = listOf(
        GroundStop(0f, Color(0xFFD5DDE7)),
        GroundStop(42f, Color(0xFFD8DEE7)),
        GroundStop(60f, Color(0xFFDAE0E7)),
        GroundStop(79f, Color(0xFFDCE1E7)),
        GroundStop(96f, Color(0xFFDDE3E8)),
        GroundStop(.30f, Color(0xFFD6DFF2), fraction = true),
        GroundStop(.60f, Color(0xFFDDDEF2), fraction = true),
        GroundStop(1f, Color(0xFFE7E4EF), fraction = true),
    ),
    glows = listOf(
        GroundGlow(Color(0x5760A0FF), x = 1f, y = 60f, rx = 230.dp),
        GroundGlow(Color(0x42768CFF), x = 0f, y = .36f, rx = 420.dp, ry = 360.dp, yFraction = true),
        GroundGlow(Color(0x385ABEE1), x = 1f, y = .62f, rx = 420.dp, ry = 380.dp, yFraction = true),
        GroundGlow(Color(0x2EFFCDA0), x = .15f, y = .92f, rx = 480.dp, ry = 380.dp, yFraction = true),
    ),
)

// Colour holds to mid-screen, then the ground reaches pure black by the bottom of the first screen.
private val DarkGround = PageGround(
    stops = listOf(
        GroundStop(0f, Color(0xFF12253F)),
        GroundStop(42f, Color(0xFF10223A)),
        GroundStop(60f, Color(0xFF102137)),
        GroundStop(96f, Color(0xFF0F1D31)),
        GroundStop(380f, Color(0xFF0D1A2E)),
        GroundStop(600f, Color(0xFF050A12)),
        GroundStop(760f, Color.Black),
    ),
    glows = listOf(
        GroundGlow(Color(0x2E409CFF), x = 1f, y = 60f, rx = 230.dp),
        GroundGlow(Color(0x1F606EFF), x = 0f, y = 300f, rx = 380.dp, ry = 300.dp),
    ),
)

private val SepiaGround = PageGround(
    stops = listOf(
        GroundStop(0f, Color(0xFFD7C2A6)),
        GroundStop(42f, Color(0xFFD9C6AC)),
        GroundStop(60f, Color(0xFFDCC9AF)),
        GroundStop(79f, Color(0xFFDFCCB3)),
        GroundStop(96f, Color(0xFFE0CEB5)),
        GroundStop(.30f, Color(0xFFEAD6BC), fraction = true),
        GroundStop(.60f, Color(0xFFEFDCC8), fraction = true),
        GroundStop(1f, Color(0xFFF2E0CF), fraction = true),
    ),
    glows = listOf(
        GroundGlow(Color(0x47EBA550), x = 1f, y = 60f, rx = 230.dp),
        GroundGlow(Color(0x33E1A073), x = 0f, y = .36f, rx = 420.dp, ry = 360.dp, yFraction = true),
        GroundGlow(Color(0x3DF0BE78), x = 1f, y = .62f, rx = 420.dp, ry = 380.dp, yFraction = true),
        GroundGlow(Color(0x2ECDAA7D), x = .15f, y = .92f, rx = 480.dp, ry = 380.dp, yFraction = true),
    ),
)

fun groundFor(themeMode: ThemeMode): PageGround = when (themeMode) {
    ThemeMode.DARK -> DarkGround
    ThemeMode.LIGHT -> LightGround
    ThemeMode.SEPIA -> SepiaGround
}

/**
 * The translucent layers that sit on the page ground: cards, the Settings panel, the bottom bar and
 * its lens. Read through [LocalGlass], which crossfades them with the colour scheme.
 *
 * [cardShadow] is painted as given. [barShadow] and [sheetShadow] are elevation shadow colours, which
 * the platform scales by its own shadow alpha, so they are opaque.
 */
@Immutable
data class GlassColors(
    val cardTop: Color,
    val cardBottom: Color,
    val cardRim: Color,
    val cardHighlight: Color,
    val cardShadow: Color,
    val sheetTop: Color,
    val sheetMid: Color,
    val sheetBottom: Color,
    val sheetShadow: Color,
    val scrim: Color,
    val barFill: Color,
    val barRim: Color,
    val barHighlight: Color,
    val barShadow: Color,
    val lensFill: Color,
    val lensRim: Color,
    val lensHighlight: Color,
)

private val LightGlass = GlassColors(
    cardTop = Color(0x94FFFFFF),
    cardBottom = Color(0x47FFFFFF),
    cardRim = Color(0xE6FFFFFF),
    cardHighlight = Color(0xB3FFFFFF),
    cardShadow = Color(0x4728325A),
    sheetTop = Color(0xFFD9E2F5),
    sheetMid = Color(0xFFE0DFF4),
    sheetBottom = Color(0xFFE9E5EF),
    sheetShadow = Color(0xFF141E32),
    scrim = Color(0x47182030),
    barFill = LightSurface.copy(alpha = .42f),
    barRim = Color(0xFFECEAE4),
    barHighlight = Color(0x99FFFFFF),
    barShadow = Color(0xFF1E283C),
    lensFill = Color(0x8CD7E3F7),
    lensRim = Color(0xF2FFFFFF),
    lensHighlight = Color(0xE6FFFFFF),
)

private val DarkGlass = GlassColors(
    cardTop = Color(0x2496B4FF),
    cardBottom = Color(0x0F788CDC),
    cardRim = Color(0x1AFFFFFF),
    cardHighlight = Color(0x0FFFFFFF),
    cardShadow = Color(0xB3000000),
    sheetTop = Color(0xFF13223B),
    sheetMid = Color(0xFF0D1829),
    sheetBottom = Color(0xFF030507),
    sheetShadow = Color.Black,
    scrim = Color(0x8C000000),
    barFill = NavySurface.copy(alpha = .48f),
    barRim = Color(0x29FFFFFF),
    barHighlight = Color(0x1FFFFFFF),
    barShadow = Color.Black,
    lensFill = Color(0x3368B0FF),
    lensRim = Color(0x38FFFFFF),
    lensHighlight = Color(0x47FFFFFF),
)

private val SepiaGlass = GlassColors(
    cardTop = Color(0x94FFFAF0),
    cardBottom = Color(0x47FFF8EA),
    cardRim = Color(0xE0FFFBF3),
    cardHighlight = Color(0xB3FFFFFF),
    cardShadow = Color(0x4D3D2A18),
    sheetTop = Color(0xFFEEDBBF),
    sheetMid = Color(0xFFF0DCCA),
    sheetBottom = Color(0xFFF2E0CF),
    sheetShadow = Color(0xFF3D2A18),
    scrim = Color(0x423D2A18),
    barFill = SepiaSurface.copy(alpha = .42f),
    barRim = Color(0xFFE6DAC6),
    barHighlight = Color(0x99FFFFFF),
    barShadow = Color(0xFF3D2A18),
    lensFill = Color(0x8CECE4DC),
    lensRim = Color(0xF2FFFFFF),
    lensHighlight = Color(0xE6FFFFFF),
)

val LocalGlass = compositionLocalOf { LightGlass }

@Composable
private fun animatedColor(target: Color, label: String): Color =
    animateColorAsState(target, tween(ThemeTransitionMs), label = label).value

@Composable
private fun animatedGlass(themeMode: ThemeMode): GlassColors {
    val target = when (themeMode) {
        ThemeMode.DARK -> DarkGlass
        ThemeMode.LIGHT -> LightGlass
        ThemeMode.SEPIA -> SepiaGlass
    }
    return GlassColors(
        cardTop = animatedColor(target.cardTop, "cardTop"),
        cardBottom = animatedColor(target.cardBottom, "cardBottom"),
        cardRim = animatedColor(target.cardRim, "cardRim"),
        cardHighlight = animatedColor(target.cardHighlight, "cardHighlight"),
        cardShadow = animatedColor(target.cardShadow, "cardShadow"),
        sheetTop = animatedColor(target.sheetTop, "sheetTop"),
        sheetMid = animatedColor(target.sheetMid, "sheetMid"),
        sheetBottom = animatedColor(target.sheetBottom, "sheetBottom"),
        sheetShadow = animatedColor(target.sheetShadow, "sheetShadow"),
        scrim = animatedColor(target.scrim, "scrim"),
        barFill = animatedColor(target.barFill, "barFill"),
        barRim = animatedColor(target.barRim, "barRim"),
        barHighlight = animatedColor(target.barHighlight, "barHighlight"),
        barShadow = animatedColor(target.barShadow, "barShadow"),
        lensFill = animatedColor(target.lensFill, "lensFill"),
        lensRim = animatedColor(target.lensRim, "lensRim"),
        lensHighlight = animatedColor(target.lensHighlight, "lensHighlight"),
    )
}

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
        // The bars stay the transparent ones enableEdgeToEdge set, so the page ground shows behind them.
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLightTheme
        }
    }
    CompositionLocalProvider(LocalGlass provides animatedGlass(themeMode)) {
        MaterialTheme(colorScheme = animatedScheme, typography = Typography, content = content)
    }
}
