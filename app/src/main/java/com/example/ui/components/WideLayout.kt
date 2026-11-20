package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The gap between the two columns. Pages pad both sides alike, so on a Fold it sits on the crease. */
val WideGutter = 32.dp

/** A page's side padding in the wide layout; 16 dp on a phone. */
val WidePagePadding = 24.dp

/** Settings keeps a phone's width on a wide screen. */
val PhonePanelWidth = 428.dp

/**
 * The Fold's inner screen, tablets and wide windows: two phone-size columns either side of
 * [WideGutter], one page at a time. Under 600 dp, or once large text would squeeze the columns,
 * it is the phone layout.
 */
@Composable
fun isWideLayout(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp / config.fontScale >= 600f
}

/** [content] in the left column; the right one stays empty. */
@Composable
fun LeftColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WideGutter)) {
        Box(Modifier.weight(1f)) { content() }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * [items] in rows of two; an odd last one keeps the left column's width. [startInset] is how far
 * this block already sits right of the page's own column; the left column gives that up, so the
 * gutter still lands on the page's centre line.
 */
@Composable
fun <T> TwoColumns(items: List<T>, modifier: Modifier = Modifier, rowGap: Dp = 14.dp, startInset: Dp = 0.dp, item: @Composable (T) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val right = (maxWidth + startInset - WideGutter) / 2
        val left = maxWidth - WideGutter - right
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowGap)) {
            items.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WideGutter)) {
                    Box(Modifier.width(left)) { item(pair[0]) }
                    if (pair.size == 2) Box(Modifier.width(right)) { item(pair[1]) }
                }
            }
        }
    }
}

/**
 * A picker over the page instead of in its place: a scrim, and a panel no wider than 760 dp.
 * [target] null closes it; the last one stays on screen while it fades out.
 */
@Composable
fun <T : Any> WidePanel(target: T?, onDismiss: () -> Unit, content: @Composable (T) -> Unit) {
    AnimatedVisibility(target != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f))
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        )
    }
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            (fadeIn(tween(200, delayMillis = 40)) + scaleIn(tween(240), initialScale = .96f)) togetherWith
                (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = .96f))
        },
        label = "widePanel",
    ) { shown ->
        if (shown != null) Box(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = WidePagePadding, vertical = 20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
            ) { content(shown) }
        }
    }
}
