package com.example.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.components.InnerShape
import io.github.nicholasarda.keysnap.R
import kotlin.math.roundToInt

/**
 * The three miniatures the tutorial draws under its open step: what the user is about to look at in
 * Android's own settings, and which control they have to move. Facsimiles, not screenshots — every
 * shape is a Compose primitive tinted from [MaterialTheme.colorScheme], so they follow the theme.
 *
 * Each runs the prototype's 3.6 s loop off one [rememberInfiniteTransition]; the keyframe fractions
 * below are the prototype's CSS percentages. Every animated value is read inside a `drawBehind`,
 * `offset` or `graphicsLayer` lambda, so a running loop redraws without recomposing. The loop only
 * exists while its step is open, because the illustration is only composed then.
 */
internal const val LoopMillis = 3600

/** The loop position, 0..1 — or parked on its end frame when the system animator scale is 0. */
@Composable
internal fun loopPosition(reduced: Boolean): State<Float> {
    if (reduced) return rememberUpdatedState(1f)
    return rememberInfiniteTransition(label = "illustration").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LoopMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "loop",
    )
}

/** A CSS keyframe ramp: 0 before [from], 1 after [to], linear between them. */
internal fun ramp(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** The loop's own reset — the frame blinks out at 95 % so the control is never seen springing back. */
private fun loopFade(t: Float) = 1f - ramp(t, .90f, .95f) + ramp(t, .95f, 1f)

@Composable
private fun IllustrationFrame(caption: String?, t: State<Float>, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .graphicsLayer { alpha = loopFade(t.value) }
            .clip(InnerShape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outline.copy(alpha = .55f), InnerShape)
            .padding(12.dp)
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
        content()
    }
}

/** The fingertip: in, press, out, on the same loop as the control it presses. */
@Composable
private fun Finger(modifier: Modifier, alpha: () -> Float, scale: () -> Float) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(22.dp)
            .graphicsLayer {
                this.alpha = alpha()
                scaleX = scale()
                scaleY = scale()
            }
            .background(colors.onSurface.copy(alpha = .22f), CircleShape)
            .border(1.5.dp, colors.onSurface.copy(alpha = .34f), CircleShape),
    )
}

@Composable
private fun GhostRow(barWidth: Dp) {
    val ghost = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .22f)
    Row(
        Modifier.height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(22.dp).background(ghost, InnerShape))
        Box(Modifier.width(barWidth).height(8.dp).background(ghost, InnerShape))
    }
}

/** Step 1: the Accessibility list, KeySnap's row, its switch sliding on with a 1.12 overshoot. */
@Composable
internal fun AccessibilityIllustration(reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    val t = loopPosition(reduced)
    val ghost = colors.onSurfaceVariant.copy(alpha = .22f)
    IllustrationFrame(stringResource(R.string.home_setup_illo_downloaded_apps), t) {
        Box {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GhostRow(88.dp)
                Row(
                    Modifier.fillMaxWidth().height(30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(24.dp).background(colors.primary, InnerShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Bolt, null, Modifier.size(14.dp), tint = colors.onPrimary)
                    }
                    Text(
                        stringResource(R.string.app_name),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                    Box(
                        Modifier
                            .size(width = 38.dp, height = 22.dp)
                            .clip(CircleShape)
                            .drawBehind { drawRect(lerp(ghost, colors.primary, ramp(t.value, .26f, .34f))) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier
                                .padding(start = 3.dp)
                                .offset {
                                    val x = 18.dp.toPx() * ramp(t.value, .26f, .31f) -
                                        2.dp.toPx() * ramp(t.value, .31f, .36f)
                                    IntOffset(x.roundToInt(), 0)
                                }
                                .graphicsLayer {
                                    val s = 1f + .12f * ramp(t.value, .26f, .31f) - .12f * ramp(t.value, .31f, .36f)
                                    scaleX = s
                                    scaleY = s
                                }
                                .size(16.dp)
                                .background(colors.surface, CircleShape),
                        )
                    }
                }
                GhostRow(64.dp)
            }
            Finger(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val out = ramp(t.value, .34f, .46f)
                        val inward = 1f - ramp(t.value, .08f, .22f)
                        IntOffset(
                            (-8.dp.toPx() + 26.dp.toPx() * inward + 14.dp.toPx() * out).roundToInt(),
                            (38.dp.toPx() + 18.dp.toPx() * inward + 10.dp.toPx() * out).roundToInt(),
                        )
                    },
                alpha = { ramp(t.value, .08f, .22f) * (1f - ramp(t.value, .34f, .46f)) },
                scale = { 1f - .18f * ramp(t.value, .22f, .28f) + .18f * ramp(t.value, .28f, .34f) },
            )
        }
    }
}

/**
 * Step 2: Android's battery-optimisation dialog — the only door that actually writes the doze
 * allowlist — on the same keyframes as step 3's permission dialog.
 */
@Composable
internal fun BackgroundDialogIllustration(reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    val t = loopPosition(reduced)
    IllustrationFrame(caption = null, t = t) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(InnerShape)
                    .background(colors.surface)
                    .border(1.dp, colors.outline.copy(alpha = .55f), InnerShape)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.home_setup_illo_battery_dialog),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.home_setup_illo_battery_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogButton(stringResource(R.string.home_setup_illo_battery_deny), fill = { 0f })
                    DialogButton(stringResource(R.string.home_setup_illo_allow), fill = { ramp(t.value, .28f, .36f) })
                }
            }
            Finger(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset {
                        val inward = 1f - ramp(t.value, .10f, .24f)
                        val out = ramp(t.value, .38f, .50f)
                        IntOffset(
                            (-14.dp.toPx() + 18.dp.toPx() * inward + 10.dp.toPx() * out).roundToInt(),
                            (-8.dp.toPx() + 16.dp.toPx() * inward + 12.dp.toPx() * out).roundToInt(),
                        )
                    },
                alpha = { ramp(t.value, .10f, .24f) * (1f - ramp(t.value, .38f, .50f)) },
                scale = { 1f - .2f * ramp(t.value, .24f, .30f) + .2f * ramp(t.value, .30f, .38f) },
            )
        }
    }
}

/** Step 3: Android's permission dialog, with Allow filling under the press they must not miss. */
@Composable
internal fun NotificationIllustration(reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    val t = loopPosition(reduced)
    IllustrationFrame(caption = null, t = t) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(InnerShape)
                    .background(colors.surface)
                    .border(1.dp, colors.outline.copy(alpha = .55f), InnerShape)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(26.dp).background(colors.primary.copy(alpha = .14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Notifications, null, Modifier.size(14.dp), tint = colors.primary)
                }
                Text(
                    stringResource(R.string.home_setup_illo_dialog),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogButton(stringResource(R.string.home_setup_illo_deny), fill = { 0f })
                    DialogButton(stringResource(R.string.home_setup_illo_allow), fill = { ramp(t.value, .28f, .36f) })
                }
            }
            Finger(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset {
                        val inward = 1f - ramp(t.value, .10f, .24f)
                        val out = ramp(t.value, .38f, .50f)
                        IntOffset(
                            (-14.dp.toPx() + 18.dp.toPx() * inward + 10.dp.toPx() * out).roundToInt(),
                            (-8.dp.toPx() + 16.dp.toPx() * inward + 12.dp.toPx() * out).roundToInt(),
                        )
                    },
                alpha = { ramp(t.value, .10f, .24f) * (1f - ramp(t.value, .38f, .50f)) },
                scale = { 1f - .2f * ramp(t.value, .24f, .30f) + .2f * ramp(t.value, .30f, .38f) },
            )
        }
    }
}

/**
 * A dialog pill. [fill] both floods the pill and cross-fades the label from the accent to its
 * on-colour, so the text never ends up drawn in the colour it now sits on.
 */
@Composable
private fun DialogButton(label: String, fill: () -> Float) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(CircleShape)
            .drawBehind {
                val f = fill()
                drawRect(colors.primary.copy(alpha = f))
                val ring = 7.dp.toPx() * f
                if (f > 0f && f < 1f) {
                    drawRoundRect(
                        colors.primary.copy(alpha = 1f - f),
                        topLeft = Offset(-ring, -ring),
                        size = Size(size.width + 2 * ring, size.height + 2 * ring),
                        cornerRadius = CornerRadius(size.height),
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
            .border(1.dp, colors.primary.copy(alpha = .45f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            Modifier.graphicsLayer { alpha = 1f - fill() },
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
        )
        Text(
            label,
            Modifier.graphicsLayer { alpha = fill() },
            style = MaterialTheme.typography.labelMedium,
            color = colors.onPrimary,
        )
    }
}
