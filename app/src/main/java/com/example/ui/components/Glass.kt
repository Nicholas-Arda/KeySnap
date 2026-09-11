package com.example.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassColors
import com.example.ui.theme.PageGround
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeTransitionMs
import com.example.ui.theme.groundFor

/**
 * A page on the glass ground. The ground scrolls with the content and spans its full height, at
 * least one screen, so cards change tone as they move over it. Content starts below the status bar
 * and ends clear of the bottom bar; the ground runs behind both.
 */
@Composable
fun GroundedPage(
    themeMode: ThemeMode,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val screen = maxHeight
        Box(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            PageGroundLayer(themeMode, Modifier.matchParentSize())
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = screen)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = BottomBarClearance + 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

/** [groundFor] drawn over the whole of its parent, crossfading with the colour scheme. */
@Composable
private fun PageGroundLayer(themeMode: ThemeMode, modifier: Modifier = Modifier) {
    Crossfade(themeMode, modifier, animationSpec = tween(ThemeTransitionMs), label = "pageGround") { mode ->
        val ground = groundFor(mode)
        Spacer(Modifier.fillMaxSize().drawBehind { drawGround(ground) })
    }
}

private fun DrawScope.drawGround(ground: PageGround) {
    val height = size.height
    fun y(at: Float, fraction: Boolean) = if (fraction) at * height else at.dp.toPx()
    val stops = ground.stops.map { y(it.at, it.fraction) to it.color }.sortedBy { it.first }
    // Dark's stops are fixed lengths that can run past a short page; the last colour then holds.
    val end = maxOf(height, stops.last().first)
    drawRect(
        Brush.verticalGradient(*stops.map { (it.first / end) to it.second }.toTypedArray(), endY = end),
        size = size.copy(height = height),
    )
    ground.glows.asReversed().forEach { glow ->
        val center = Offset(glow.x * size.width, y(glow.y, glow.yFraction))
        val rx = glow.rx.toPx()
        // Fading to the glow's own colour at zero alpha, not to transparent black, keeps the edge clean.
        val brush = Brush.radialGradient(
            0f to glow.color,
            .7f to glow.color.copy(alpha = 0f),
            center = center,
            radius = rx,
        )
        scale(scaleX = 1f, scaleY = glow.ry.toPx() / rx, pivot = center) {
            drawCircle(brush, radius = rx, center = center)
        }
    }
}

/**
 * Translucent glass over the page ground: a near-vertical fill, a bright rim, a highlight along the
 * top edge and a soft shadow. The shadow is drawn only outside the card, like a CSS box-shadow; a
 * platform elevation shadow would show through the fill and grey it.
 */
fun Modifier.glassCard(glass: GlassColors, rim: Color = glass.cardRim): Modifier = this
    .drawWithCache {
        val corner = ConsoleCorner.toPx()
        val card = Path().apply { addRoundRect(RoundRect(size.toRect(), CornerRadius(corner))) }
        val spread = 18.dp.toPx()
        val drop = 12.dp.toPx()
        val shadow = Paint().asFrameworkPaint().apply {
            color = glass.cardShadow.toArgb()
            maskFilter = BlurMaskFilter(23.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        // CSS 160deg: twenty degrees off vertical.
        val fill = Brush.linearGradient(
            listOf(glass.cardTop, glass.cardBottom),
            start = Offset.Zero,
            end = Offset(size.height * .36f, size.height),
        )
        val highlightY = 2.dp.toPx()
        onDrawBehind {
            clipPath(card, ClipOp.Difference) {
                drawIntoCanvas {
                    it.nativeCanvas.drawRoundRect(
                        spread,
                        spread + drop,
                        size.width - spread,
                        size.height - spread + drop,
                        corner,
                        corner,
                        shadow,
                    )
                }
            }
            drawPath(card, fill)
            drawLine(glass.cardHighlight, Offset(corner, highlightY), Offset(size.width - corner, highlightY), 1.dp.toPx())
        }
    }
    .border(1.5.dp, rim, ConsoleShape)
