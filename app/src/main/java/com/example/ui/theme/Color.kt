package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Warm laboratory white rather than a clinical pure-white interface.
val LightBackground = Color(0xFFF2F0EA)
val LightSurface = Color(0xFFFCFAF5)
val LightSurfaceVariant = Color(0xFFE8E4DA)
val LightSurfaceElevated = Color(0xFFD5D0C4)

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
