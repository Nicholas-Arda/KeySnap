package com.example.ui.scripts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.round
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.ShortcutScript
import com.example.data.moveShortcut
import com.example.ui.components.ConsoleShape
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun Modifier.shortcutEditOutline(color: Color, progress: () -> Float): Modifier = drawWithContent {
    drawContent()
    val alpha = progress()
    if (alpha > 0f) drawOutline(
        ConsoleShape.createOutline(size, layoutDirection, this),
        color.copy(alpha = .65f * alpha),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/**
 * Slides an item from where it was to where the layout now puts it. [enabled] = false snaps instead
 * (the dragged card follows the finger, so it must not lag behind its own slot).
 */
private fun Modifier.animatePlacement(enabled: Boolean): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(IntOffset.Zero) }
    var anim by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    onPlaced { coords ->
        val next = coords.positionInParent().round()
        val a = anim
        if (a == null) anim = Animatable(next, IntOffset.VectorConverter)
        else if (next != a.targetValue) scope.launch {
            if (enabled) a.animateTo(next, tween(240, easing = FastOutSlowInEasing)) else a.snapTo(next)
        }
        target = next
    }.offset { anim?.let { it.value - target } ?: IntOffset.Zero }
}

/** The existing scrolling page stays in charge of scrolling; only a claimed drag consumes it. */
@Composable
internal fun ReorderableShortcutCards(
    scripts: List<ShortcutScript>,
    editing: Boolean,
    scrollState: ScrollState,
    globallyEnabled: Boolean,
    failedScriptIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onEnterEditing: () -> Unit = {},
) {
    val ids = scripts.map { it.id }
    // Changing the filter cancels any unfinished drag (leaving edit mode does too, below).
    key(ids.toSet()) {
        var order by remember { mutableStateOf(ids) }
        val latestIds by rememberUpdatedState(ids)
        var dragged by remember { mutableStateOf<String?>(null) }
        var center by remember { mutableFloatStateOf(0f) }
        val bounds = remember { mutableStateMapOf<String, Rect>() }
        val latestCommit by rememberUpdatedState(onReorder)
        val density = LocalDensity.current
        val view = LocalView.current
        val haptic = LocalHapticFeedback.current
        val edge = with(density) { 96.dp.toPx() }
        val step = with(density) { 12.dp.toPx() }
        LaunchedEffect(ids) { if (dragged == null) order = ids }
        fun start(id: String) {
            val rect = bounds[id] ?: return
            dragged = id
            center = rect.center.y
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        fun move(delta: Float) {
            val id = dragged ?: return
            center += delta
            val index = order.indexOf(id)
            val target = order.take(index).firstOrNull { other ->
                bounds[other]?.center?.y?.let { center < it } == true
            } ?: order.drop(index + 1).lastOrNull { other ->
                bounds[other]?.center?.y?.let { center > it } == true
            }
            if (target != null) {
                order = moveShortcut(order, id, target)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        fun finish(commit: Boolean) {
            if (dragged == null) return
            dragged = null
            if (commit) latestCommit(order) else order = latestIds
        }
        LaunchedEffect(editing) { if (!editing) finish(false) }
        LaunchedEffect(dragged) {
            while (dragged != null) {
                val scroll = when {
                    center < edge -> -step
                    center > view.height - edge -> step
                    else -> 0f
                }
                if (scroll != 0f) {
                    scrollState.scrollBy(scroll)
                    move(0f)
                }
                delay(16)
            }
        }
        val moveUp = stringResource(R.string.editor_move_up)
        val moveDown = stringResource(R.string.editor_move_down)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            order.forEachIndexed { index, id ->
                val script = scripts.firstOrNull { it.id == id } ?: return@forEachIndexed
                key(id) {
                    val active = dragged == id
                    val dragY = if (active) center - (bounds[id]?.center?.y ?: center) else 0f
                    val lastDragY = remember { floatArrayOf(0f) }
                    if (active) lastDragY[0] = dragY
                    // On drop the card eases from where the finger left it into its slot.
                    val settle = remember { Animatable(0f) }
                    LaunchedEffect(active) {
                        if (!active && lastDragY[0] != 0f) {
                            settle.snapTo(lastDragY[0])
                            lastDragY[0] = 0f
                            settle.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                        }
                    }
                    val lift by animateFloatAsState(if (active) 1f else 0f, tween(160), label = "lift")
                    val gesture = if (!editing) Modifier else Modifier
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { start(id) },
                                onDragEnd = { finish(true) },
                                onDragCancel = { finish(false) },
                                onDrag = { change, amount -> change.consume(); move(amount.y) },
                            )
                        }
                        .semantics {
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUp) {
                                    latestCommit(moveShortcut(order, id, order[index - 1])); true
                                })
                                if (index < order.lastIndex) add(CustomAccessibilityAction(moveDown) {
                                    latestCommit(moveShortcut(order, id, order[index + 1])); true
                                })
                            }
                        }
                    val handle = Modifier.draggable(
                        state = rememberDraggableState { move(it) },
                        orientation = Orientation.Vertical,
                        enabled = editing,
                        startDragImmediately = true,
                        onDragStarted = { start(id) },
                        onDragStopped = { finish(true) },
                    )
                    // Measure a stationary wrapper, not the translated card, to avoid feedback drift.
                    Box(Modifier.zIndex(if (active || settle.isRunning) 1f else 0f).onGloballyPositioned {
                        bounds[id] = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                    }.animatePlacement(enabled = !active)) {
                        ScriptCard(
                            script, globallyEnabled, id in failedScriptIds, onToggle, onOpen,
                            modifier = Modifier.graphicsLayer {
                                translationY = if (active) dragY else settle.value
                                // No shadow: on the translucent glass card it shows through as grey bands.
                                scaleX = 1f + .03f * lift
                                scaleY = scaleX
                                shape = ConsoleShape
                            }.then(gesture),
                            editing = editing,
                            dragHandle = handle,
                            onLongPress = onEnterEditing,
                        )
                    }
                }
            }
        }
    }
}
