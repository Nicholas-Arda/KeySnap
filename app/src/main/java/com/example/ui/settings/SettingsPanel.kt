package com.example.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.example.ui.components.MinTouchTarget
import com.example.ui.theme.LocalGlass
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.launch

// Every duration is the mockup's at the chosen 1.25x pace.
private const val OpenMs = 600
private const val CloseMs = 350
private const val ContentFadeOutMs = 120
private const val StaggerStartMs = 162
private const val StaggerStepMs = 48
private const val StaggerFadeMs = 350
private const val StaggerSlideMs = 525

/** Settings has twelve rows; the last one has finished sliding in by here. */
private const val StaggerEndMs = StaggerStartMs + StaggerStepMs * 11 + StaggerSlideMs

private val OpenEasing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)
private val CloseEasing = CubicBezierEasing(0.5f, 0f, 0.75f, 0f)

private val GearSize = 44.dp

/** The panel starts as this rounded box in its gear-side top corner. */
private val SeedSize = 58.dp
private val PanelCorner = 28.dp
private val PanelShape = RoundedCornerShape(PanelCorner)

/**
 * The Settings panel's clocks. The panel and the backdrop (scrim, the page shrinking behind it, the
 * gear) open together but shut on different curves: the panel accelerates back into the gear while
 * the backdrop eases out.
 */
@Stable
class SettingsMotion internal constructor(open: Boolean) {
    internal val panel = Animatable(if (open) 1f else 0f)
    internal val backdrop = Animatable(if (open) 1f else 0f)
    internal val elapsedMs = Animatable(if (open) StaggerEndMs.toFloat() else 0f)
    internal val contentAlpha = Animatable(1f)

    /** The panel's row at [index] fades and drops into place, each row a beat after the one above. */
    fun reveal(index: Int): Modifier = Modifier.graphicsLayer {
        val t = elapsedMs.value - (StaggerStartMs + StaggerStepMs * index)
        alpha = (t / StaggerFadeMs).coerceIn(0f, 1f) * contentAlpha.value
        translationY = -8.dp.toPx() * (1f - OpenEasing.transform((t / StaggerSlideMs).coerceIn(0f, 1f)))
        // Alpha per draw, not through a layer clipped to the row, so a card's shadow is not cut off.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

    /** What sits behind the panel shrinks slightly while it is open. */
    fun backdropScale(): Modifier = Modifier.graphicsLayer {
        val scale = 1f - .045f * backdrop.value
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(.5f, .35f)
    }
}

@Composable
fun rememberSettingsMotion(open: Boolean): SettingsMotion {
    val motion = remember { SettingsMotion(open) }
    LaunchedEffect(open) {
        if (open) {
            if (motion.panel.value == 1f) return@LaunchedEffect
            motion.contentAlpha.snapTo(1f)
            if (motion.panel.value == 0f) motion.elapsedMs.snapTo(0f)
            val remaining = StaggerEndMs - motion.elapsedMs.value.toInt()
            launch { motion.elapsedMs.animateTo(StaggerEndMs.toFloat(), tween(remaining, easing = LinearEasing)) }
            launch { motion.panel.animateTo(1f, tween(OpenMs, easing = OpenEasing)) }
            motion.backdrop.animateTo(1f, tween(OpenMs, easing = OpenEasing))
        } else {
            if (motion.panel.value == 0f) return@LaunchedEffect
            launch { motion.contentAlpha.animateTo(0f, tween(ContentFadeOutMs)) }
            launch { motion.panel.animateTo(0f, tween(CloseMs, easing = CloseEasing)) }
            motion.backdrop.animateTo(0f, tween(CloseMs, easing = FastOutSlowInEasing))
        }
    }
    return motion
}

/** Dims what is behind the panel; a tap on it shuts the panel. */
@Composable
fun SettingsScrim(motion: SettingsMotion, open: Boolean, onClose: () -> Unit) {
    val visible by remember { derivedStateOf { motion.backdrop.value > 0f } }
    if (!visible) return
    val glass = LocalGlass.current
    val closeLabel = stringResource(R.string.settings_close)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = motion.backdrop.value }
            .background(glass.scrim)
            .then(
                if (open) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = closeLabel,
                        onClick = onClose,
                    )
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * The panel itself. It grows out of the gear's corner and scrolls inside; the caller places it,
 * inset from the screen edges so Home stays visible around it.
 */
@Composable
fun SettingsSheet(motion: SettingsMotion, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val visible by remember { derivedStateOf { motion.panel.value > 0f } }
    if (!visible) return
    val glass = LocalGlass.current
    val rim = MaterialTheme.colorScheme.outline.copy(alpha = .7f)
    val title = stringResource(R.string.settings_title)
    Box(
        modifier
            .graphicsLayer {
                shape = RevealShape(motion.panel.value)
                clip = true
                shadowElevation = 24.dp.toPx()
                ambientShadowColor = glass.sheetShadow
                spotShadowColor = glass.sheetShadow
            }
            .drawWithContent {
                // CSS 170deg: ten degrees off vertical.
                drawRect(
                    Brush.linearGradient(
                        0f to glass.sheetTop,
                        .55f to glass.sheetMid,
                        1f to glass.sheetBottom,
                        start = Offset.Zero,
                        end = Offset(size.height * .18f, size.height),
                    ),
                )
                drawContent()
                // Rows fade out into the panel's bottom edge rather than being cut off by it.
                val fade = 36.dp.toPx()
                drawRect(
                    Brush.verticalGradient(
                        listOf(glass.sheetBottom.copy(alpha = 0f), glass.sheetBottom),
                        startY = size.height - fade,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height - fade),
                )
            }
            .border(1.dp, rim, PanelShape)
            .semantics { paneTitle = title }
            .testTag("settings_panel"),
    ) {
        content()
    }
}

/** The panel's clip: a [SeedSize] box in the gear-side top corner at 0, the whole panel at 1. */
private class RevealShape(private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val seed = with(density) { SeedSize.toPx() }
        val corner = with(density) { lerp(SeedSize / 2, PanelCorner, progress).toPx() }
        val width = seed + (size.width - seed) * progress
        val left = if (layoutDirection == LayoutDirection.Rtl) 0f else size.width - width
        return Outline.Rounded(
            RoundRect(left, 0f, left + width, seed + (size.height - seed) * progress, CornerRadius(corner)),
        )
    }
}

/** The gear that opens Settings. While the panel is open it has turned into the panel's close button. */
@Composable
fun SettingsGear(motion: SettingsMotion, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(if (open) R.string.settings_close else R.string.nav_settings)
    val inward = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    Box(
        modifier
            .graphicsLayer {
                translationX = inward * 8.dp.toPx() * motion.backdrop.value
                translationY = 4.dp.toPx() * motion.backdrop.value
            }
            .size(MinTouchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag("settings_gear"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(GearSize)
                .graphicsLayer {
                    shape = CircleShape
                    clip = true
                    shadowElevation = 4.dp.toPx() * (1f - motion.backdrop.value)
                }
                .drawBehind { drawRect(lerp(colors.surface, colors.surfaceVariant, motion.backdrop.value)) }
                .border(1.dp, colors.outline.copy(alpha = .8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Settings,
                null,
                Modifier.size(22.dp).graphicsLayer {
                    val p = motion.backdrop.value
                    alpha = 1f - p
                    rotationZ = 90f * p
                    scaleX = 1f - .4f * p
                    scaleY = scaleX
                },
                tint = colors.onSurface,
            )
            Icon(
                Icons.Filled.Close,
                null,
                Modifier.size(22.dp).graphicsLayer {
                    val p = motion.backdrop.value
                    alpha = p
                    rotationZ = -90f * (1f - p)
                    scaleX = .6f + .4f * p
                    scaleY = scaleX
                },
                tint = colors.onSurface,
            )
        }
    }
}
