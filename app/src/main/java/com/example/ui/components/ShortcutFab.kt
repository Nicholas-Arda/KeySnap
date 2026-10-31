package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.launch

/** The fixed layout box owns input; only the disc and glyph squash/lift. */
@Composable
internal fun ShortcutFab(
    onClick: () -> Unit,
    onCenterChanged: (Offset) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val attention = remember { Animatable(0f) }
    val launch = remember { Animatable(0f) }
    var launching by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    LaunchedEffect(Unit) {
        attention.animateTo(1f, tween(420, delayMillis = 220, easing = LinearEasing))
    }
    Box(
        modifier.onGloballyPositioned { onCenterChanged(it.boundsInRoot().center) }
            .clickable(
                enabled = !launching, role = Role.Button, interactionSource = interactions,
                indication = null,
            ) {
                if (!launching) {
                    launching = true
                    scope.launch {
                        launch.animateTo(1f, tween(580, easing = LinearEasing))
                        // Home can survive an interrupted open/close. Restore its button too.
                        launch.snapTo(0f)
                        launching = false
                    }
                    onClick()
                }
            }.testTag("new_script_button"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize().graphicsLayer {
                val p = if (launching) launch.value else attention.value
                val squash = if (launching) (p / .10f).coerceIn(0f, 1f)
                    else (p / .16f).coerceIn(0f, 1f)
                val lift = if (launching) ((p - .10f) / .28f).coerceIn(0f, 1f)
                    else ((p - .16f) / .32f).coerceIn(0f, 1f)
                val settle = if (launching) 0f else ((p - .48f) / .52f).coerceIn(0f, 1f)
                val press = if (pressed && !launching) .95f else 1f
                scaleX = (1f + .12f * squash - .14f * lift + .02f * settle) * press
                scaleY = (1f - .15f * squash + .19f * lift - .04f * settle) * press
                translationY = if (launching) -size.height * 1.6f * lift
                    else -size.height * .18f * lift * (1f - settle)
                alpha = if (launching) (1f - ((p - .20f) / .18f).coerceIn(0f, 1f)) else 1f
            }.shadow(10.dp, CircleShape, ambientColor = colors.primary, spotColor = colors.primary)
                .clip(CircleShape).background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add, stringResource(R.string.scripts_new_shortcut),
                Modifier.size(30.dp).graphicsLayer {
                    val p = if (launching) launch.value else attention.value
                    rotationZ = if (launching) -20f * (p / .10f).coerceIn(0f, 1f) +
                        200f * ((p - .10f) / .28f).coerceIn(0f, 1f)
                    else -20f * (p / .16f).coerceIn(0f, 1f) +
                        200f * ((p - .16f) / .84f).coerceIn(0f, 1f)
                },
                tint = colors.onPrimary,
            )
        }
    }
}
