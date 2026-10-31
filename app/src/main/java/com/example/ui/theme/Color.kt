package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Cool neutrals that sit with the blue accent and the blue glass ground; the earlier warm cream
// read as yellow on chips and switch tracks against that ground. Sepia keeps the warm palette.
val LightBackground = Color(0xFFF1F3F7)
val LightSurface = Color(0xFFFBFCFE)
val LightSurfaceVariant = Color(0xFFE5E9F0)
val LightSurfaceElevated = Color(0xFFCBD2DD)

/**
 * The accent hue is the same blue in every appearance; only its value changes, so the brand reads
 * as one colour while still clearing 4.5:1 as text wherever it sits. [AccentOnDark] measures 6.6:1
 * on the dark background, [AccentOnLight] 5.6:1 on the light one, and each also clears 4.5:1 against
 * its own theme surface, not just its background. Sepia keeps its own warm accent and is not part
 * of this set.
 */
val AccentOnDark = Color(0xFF409CFF)
val AccentOnLight = Color(0xFF0B57D0)
val AmberOnSepia = Color(0xFF8B5A2B)
val CyanAccent = Color(0xFF78A7FF)
val CrimsonInactive = Color(0xFFFF6259)

/**
 * The error/alarm red for the two light appearances. [CrimsonInactive] is tuned for the navy dark
 * theme and only reaches 2.8:1 as text on [LightSurface], so it fails AA everywhere else.
 * [CrimsonOnLight] measures 6.3:1 on [LightSurface], 5.7:1 on [LightBackground], 6.2:1 on the sepia
 * surface and 5.4:1 on the sepia background, and white on it is 6.5:1 — so one value serves both
 * appearances as text and as a filled-pill ground.
 */
val CrimsonOnLight = Color(0xFFB3261E)

val TextPrimary = Color(0xFFF5F1E8)
val TextSecondary = Color(0xFFA9A49A)

/** Success text. The dark value is for a navy surface; the light value clears the light surfaces. */
val SuccessOnLight = Color(0xFF146C2E)
val SuccessOnDark = Color(0xFF8FDBA8)

/** Caution text for plain HTTP, limited shell, and a setting that needs Advanced Mode. */
val CautionOnLight = Color(0xFF7A4F00)
val CautionOnDark = Color(0xFFFFD27A)

fun successOn(background: Color): Color = if (background.luminance() < 0.5f) SuccessOnDark else SuccessOnLight

fun cautionOn(background: Color): Color = if (background.luminance() < 0.5f) CautionOnDark else CautionOnLight
