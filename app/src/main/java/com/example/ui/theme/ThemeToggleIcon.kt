package com.example.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val ContrastTint = AccentOnDark
private val SepiaTint = Color(0xFF8B5A2B)

/**
 * Icon that morphs (scale + fade) whenever [themeMode] changes, so the top-right
 * toggle visually announces which of the three appearances is now active.
 */
@Composable
fun ThemeToggleIcon(themeMode: ThemeMode, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = themeMode,
        modifier = modifier,
        transitionSpec = {
            (scaleIn(tween(260), initialScale = 0.4f) + fadeIn(tween(220)))
                .togetherWith(scaleOut(tween(220), targetScale = 0.4f) + fadeOut(tween(160)))
        },
        label = "themeToggleIcon",
    ) { mode ->
        when (mode) {
            ThemeMode.LIGHT -> SunGlyph()
            ThemeMode.DARK -> ContrastGlyph()
            ThemeMode.SEPIA -> SepiaGlyph()
        }
    }
}

private const val RayCount = 12

private fun rayPath(rayStartFraction: Float, rayEndFraction: Float, size: Float): Path {
    val center = Offset(size / 2f, size / 2f)
    val path = Path()
    for (i in 0 until RayCount) {
        val angle = (2 * Math.PI * i / RayCount).toFloat()
        val start = Offset(center.x + rayStartFraction * size * cos(angle), center.y + rayStartFraction * size * sin(angle))
        val end = Offset(center.x + rayEndFraction * size * cos(angle), center.y + rayEndFraction * size * sin(angle))
        path.moveTo(start.x, start.y)
        path.lineTo(end.x, end.y)
    }
    return path
}

@Composable
private fun SunGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val size = this.size.minDimension
        drawPath(
            path = rayPath(0.40f, 0.49f, size),
            color = AccentOnDark,
            style = Stroke(width = size * 0.05f, cap = StrokeCap.Round),
        )
        drawCircle(
            color = AccentOnDark,
            radius = size * 0.27f,
            style = Stroke(width = size * 0.07f),
        )
    }
}

@Composable
private fun ContrastGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val size = this.size.minDimension
        drawPath(
            path = rayPath(0.40f, 0.49f, size),
            color = ContrastTint,
            style = Stroke(width = size * 0.05f, cap = StrokeCap.Round),
        )
        val center = Offset(size / 2f, size / 2f)
        val radius = size * 0.27f
        val half = Path().apply {
            moveTo(center.x, center.y - radius)
            arcTo(androidx.compose.ui.geometry.Rect(center - Offset(radius, radius), androidx.compose.ui.geometry.Size(radius * 2, radius * 2)), -90f, 180f, false)
            close()
        }
        drawPath(half, color = ContrastTint)
        drawCircle(color = ContrastTint, radius = radius, style = Stroke(width = size * 0.07f))
    }
}

@Composable
private fun SepiaGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val size = this.size.minDimension
        drawPath(
            path = rayPath(0.40f, 0.49f, size),
            color = SepiaTint,
            style = Stroke(width = size * 0.05f, cap = StrokeCap.Round),
        )
        drawCircle(color = SepiaTint, radius = size * 0.32f, style = Stroke(width = size * 0.045f))

        val potWidth = size * 0.26f
        val potTop = size * 0.58f
        val potHeight = size * 0.18f
        val potLeft = size / 2f - potWidth / 2f
        drawRect(
            color = SepiaTint,
            topLeft = Offset(potLeft, potTop),
            size = androidx.compose.ui.geometry.Size(potWidth, potHeight),
            style = Stroke(width = size * 0.035f),
        )

        val stemBase = Offset(size / 2f, potTop)
        val stemTop = Offset(size / 2f, size * 0.32f)
        drawLine(SepiaTint, stemBase, stemTop, strokeWidth = size * 0.035f, cap = StrokeCap.Round)

        val leaf = Path()
        leaf.moveTo(stemTop.x, stemTop.y + size * 0.16f)
        leaf.quadraticBezierTo(stemTop.x - size * 0.18f, stemTop.y + size * 0.10f, stemTop.x, stemTop.y - size * 0.06f)
        leaf.quadraticBezierTo(stemTop.x + size * 0.18f, stemTop.y + size * 0.10f, stemTop.x, stemTop.y + size * 0.16f)
        leaf.close()
        drawPath(leaf, color = SepiaTint, style = Stroke(width = size * 0.03f))
    }
}
