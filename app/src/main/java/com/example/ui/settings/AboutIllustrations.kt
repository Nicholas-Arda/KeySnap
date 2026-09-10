package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.components.InnerShape
import com.example.ui.home.loopPosition
import com.example.ui.home.ramp
import com.example.ui.theme.MonoLabel
import io.github.nicholasarda.keysnap.R
import kotlin.math.roundToInt

/**
 * The four miniatures the About page draws above its claims. Same construction as
 * [com.example.ui.home.SetupIllustrations]: one infinite loop per illustration, every animated
 * value read inside a `drawBehind`, `offset` or `graphicsLayer` lambda so a running loop redraws
 * without recomposing, and every shape a Compose primitive tinted from the theme.
 *
 * They exist because the claims on that page are the ones users disbelieve. A picture of packets
 * bouncing off the device says "nothing leaves" faster than the sentence under it does.
 */
private val TileShape = RoundedCornerShape(8.dp)

/** The frame every About illustration sits in — the same well the setup tutorial's use. */
@Composable
private fun IllustrationBox(
    height: Dp,
    caption: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(InnerShape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outline.copy(alpha = .55f), InnerShape)
            .clearAndSetSemantics {},
    ) {
        content()
        if (caption != null) {
            Text(
                caption,
                Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp),
                style = MonoLabel,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** A packet's own position in the loop, so three of them can share one transition. */
private fun phase(t: Float, delay: Float) = (t - delay + 1f) % 1f

/**
 * "There is no server": key presses and shortcuts rise off the device, hit a dome and drop back.
 * Deliberately not an arrow crossed out — nothing is being blocked in flight, there is simply no
 * destination.
 */
@Composable
internal fun NoServerIllustration(reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    val t = loopPosition(reduced)
    IllustrationBox(112.dp, stringResource(R.string.about_illo_no_server_caption)) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .size(width = 156.dp, height = 60.dp)
                .drawBehind {
                    val hit = ramp(t.value, .42f, .48f) - ramp(t.value, .48f, .66f)
                    drawArc(
                        color = colors.primary.copy(alpha = .34f + .56f * hit),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height * 2),
                        style = Stroke(
                            width = 1.4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f)),
                        ),
                    )
                },
        )
        Packet(t, delay = .00f, dx = (-22).dp)
        Packet(t, delay = .18f, dx = 0.dp)
        Packet(t, delay = .36f, dx = 22.dp)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .size(width = 54.dp, height = 34.dp)
                .background(colors.primary.copy(alpha = .10f), RoundedCornerShape(7.dp))
                .border(1.5.dp, colors.primary, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .padding(bottom = 5.dp)
                    .size(width = 16.dp, height = 2.dp)
                    .background(colors.primary.copy(alpha = .5f), CircleShape),
            )
        }
    }
}

/** One rising dot, on its own offset into the shared loop. */
@Composable
private fun BoxScope.Packet(t: State<Float>, delay: Float, dx: Dp) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 58.dp)
            .offset {
                val p = phase(t.value, delay)
                val travel = -34.dp.toPx() * ramp(p, 0f, .42f) + 34.dp.toPx() * ramp(p, .48f, .80f)
                IntOffset(dx.toPx().roundToInt(), travel.roundToInt())
            }
            .graphicsLayer {
                val p = phase(t.value, delay)
                alpha = ramp(p, 0f, .12f) * (1f - ramp(p, .78f, .92f))
                val pop = 1f + .45f * ramp(p, .38f, .44f) - .45f * ramp(p, .44f, .54f)
                scaleX = pop
                scaleY = pop
            }
            .size(7.dp)
            .background(colors.primary, CircleShape),
    )
}

/**
 * "Open source": the repository listing types itself out. The paths stay untranslated for the same
 * reason the kernel line in the wizard does — they are what the user would actually see in a
 * terminal.
 */
@Composable
internal fun OpenSourceIllustration(reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    val t = loopPosition(reduced)
    val lines = stringArrayResource(R.array.about_source_listing)
    IllustrationBox(112.dp) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            lines.forEachIndexed { index, line ->
                val from = .04f + index * .15f
                Text(
                    line,
                    Modifier.drawWithContent {
                        val typed = ramp(t.value, from, from + .11f) * (1f - ramp(t.value, .93f, .97f))
                        clipRect(right = size.width * typed) { this@drawWithContent.drawContent() }
                    },
                    style = MonoLabel,
                    color = if (index == 0) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * "The accessibility service only sees Settings": a sweep passes over the installed apps and only
 * the Settings tile answers. The locks are the point — the boundary is Android's, not a promise.
 *
 * Only the one tile that opens is named. Naming the locked ones too would need five labels to fit
 * across the card, and the first translation with a long word for "Settings" clips; the sentence
 * under the illustration already says which apps they stand for.
 */
@Composable
internal fun AccessibilityScopeIllustration(reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    val t = loopPosition(reduced)
    IllustrationBox(112.dp) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(5) { index ->
                val isSettings = index == 2
                Column(
                    Modifier
                        .width(if (isSettings) 92.dp else 38.dp)
                        .height(54.dp)
                        .clip(TileShape)
                        .background(if (isSettings) colors.primary.copy(alpha = .14f) else colors.surface)
                        .then(
                            if (isSettings) {
                                Modifier.drawBehind {
                                    val wake = ramp(t.value, .36f, .46f) - ramp(t.value, .70f, .90f)
                                    drawRoundRect(
                                        color = colors.primary.copy(alpha = .45f + .55f * wake),
                                        cornerRadius = CornerRadius(8.dp.toPx()),
                                        style = Stroke(1.5.dp.toPx()),
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        if (isSettings) Icons.Filled.Settings else Icons.Filled.Lock,
                        contentDescription = null,
                        Modifier.size(14.dp),
                        tint = if (isSettings) colors.primary else colors.onSurfaceVariant.copy(alpha = .5f),
                    )
                    if (isSettings) {
                        Text(
                            stringResource(R.string.settings_title),
                            style = MonoLabel,
                            color = colors.primary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                val x = -60.dp.toPx() + (size.width + 120.dp.toPx()) * ramp(t.value, 0f, .55f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            colors.primary.copy(alpha = 0f),
                            colors.primary.copy(alpha = .20f),
                            colors.primary.copy(alpha = 0f),
                        ),
                        startX = x,
                        endX = x + 56.dp.toPx(),
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(56.dp.toPx(), size.height),
                )
            },
        )
    }
}

/**
 * "Off means gone": the switch drops and the bridge's three files go with it. The blocks return at
 * the end of the loop rather than blinking, so the frame is never caught mid-reset.
 */
@Composable
internal fun BridgeOffIllustration(reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    val t = loopPosition(reduced)
    IllustrationBox(112.dp, stringResource(R.string.about_illo_bridge_caption)) {
        Column(
            Modifier.fillMaxSize().padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(width = 46.dp, height = 26.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        drawRect(
                            lerp(
                                colors.primary,
                                colors.onSurfaceVariant.copy(alpha = .30f),
                                ramp(t.value, .28f, .40f) - ramp(t.value, .90f, 1f),
                            ),
                        )
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .offset {
                            val off = 1f - ramp(t.value, .28f, .40f) + ramp(t.value, .90f, 1f)
                            IntOffset((20.dp.toPx() * off).roundToInt(), 0)
                        }
                        .size(18.dp)
                        .drawBehind {
                            val off = 1f - ramp(t.value, .28f, .40f) + ramp(t.value, .90f, 1f)
                            drawCircle(lerp(colors.onSurfaceVariant, colors.surface, off))
                        },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .graphicsLayer {
                                val gone = ramp(t.value, .34f + index * .04f, .56f + index * .04f) -
                                    ramp(t.value, .90f, 1f)
                                alpha = 1f - gone
                                val s = 1f - .3f * gone
                                scaleX = s
                                scaleY = s
                            }
                            .size(width = 30.dp, height = 16.dp)
                            .background(colors.primary.copy(alpha = .30f), RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}
