package com.example.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.delay

/** One pass of the header shine and the dot's pop, matching the approved header mock. */
internal const val AnnouncementPeriodMs = 3_200

/** How many passes play when What's New is announced, then the dot rests. */
internal const val AnnouncementPlays = 3

/** The approved hint bubble. It stays dark on every theme, the same way the lockup blue stays blue. */
private val HintBubble = Color(0xFF1C1C1E)

/** A white band, so it reads on the blue pieces in light, dark, and sepia. Not a glow. */
internal fun Modifier.logoShine(progress: () -> Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val center = size.width * shineCenterFraction(progress())
        val half = size.width * 0.32f
        drawRect(
            brush = Brush.linearGradient(
                0f to Color.Transparent,
                0.35f to Color.Transparent,
                0.5f to Color.White.copy(alpha = 0.85f),
                0.65f to Color.Transparent,
                1f to Color.Transparent,
                start = Offset(center - half, size.height * 0.85f),
                end = Offset(center + half, size.height * 0.15f),
            ),
            blendMode = BlendMode.SrcAtop,
        )
    }

/**
 * The shine's progress, read from draw. Plays [AnnouncementPlays] times while the app is in front,
 * then stays at 0 so an idle Home stops asking for frames. A pass interrupted by leaving the app
 * resumes; it does not start the count over.
 */
@Composable
internal fun rememberAnnouncementCycle(animate: Boolean): () -> Float {
    val cycle = remember { Animatable(0f) }
    val plays = remember { intArrayOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(animate) {
        if (!animate) {
            plays[0] = 0
            cycle.snapTo(0f)
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (plays[0] < AnnouncementPlays) {
                cycle.snapTo(0f)
                cycle.animateTo(1f, tween(AnnouncementPeriodMs, easing = LinearEasing))
                plays[0]++
            }
            if (cycle.value != 0f) cycle.snapTo(0f)
        }
    }
    return { cycle.value }
}

@Composable
internal fun AnnouncementDot(progress: () -> Float, animate: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(11.dp)
            .graphicsLayer {
                val scale = if (animate) announcementDotScale(progress()) else 1f
                scaleX = scale
                scaleY = scale
            }
            .background(colors.surface, CircleShape)
            .padding(2.dp)
            .background(colors.error, CircleShape)
            .clearAndSetSemantics {},
    )
}

@Composable
internal fun WhatsNewHintBubble(
    reducedMotion: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) alpha.animateTo(1f, tween(200))
        delay(4_000)
        if (reducedMotion) alpha.snapTo(0f) else alpha.animateTo(0f, tween(280))
        onFinished()
    }
    Column(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity),
                )
                layout(0, 0) { placeable.placeRelative(0, 6.dp.roundToPx()) }
            }
            .graphicsLayer { this.alpha = alpha.value }
            .clearAndSetSemantics {},
    ) {
        Canvas(Modifier.padding(start = 18.dp).size(10.dp, 5.dp)) {
            val path = Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, HintBubble)
        }
        Text(
            text = stringResource(R.string.whats_new_hint),
            modifier = Modifier
                .widthIn(max = 190.dp)
                .background(HintBubble, RoundedCornerShape(9.dp))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
        )
    }
}

/** Holds off the right, crosses during the middle of the cycle, then rests off the left. */
private fun shineCenterFraction(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    val travel = when {
        clamped <= 0.55f -> 0f
        clamped >= 0.85f -> 1f
        else -> {
            val p = (clamped - 0.55f) / 0.30f
            p * p * (3f - 2f * p)
        }
    }
    return 1.15f + (-0.15f - 1.15f) * travel
}

/** Quiet at the start of the cycle, a small pop as the shine finishes, then back. */
private fun announcementDotScale(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    val smooth = { p: Float -> p * p * (3f - 2f * p) }
    return when {
        clamped <= 0.80f -> 1f
        clamped <= 0.88f -> 1f + 0.35f * smooth((clamped - 0.80f) / 0.08f)
        else -> 1.35f - 0.35f * smooth((clamped - 0.88f) / 0.12f)
    }
}
