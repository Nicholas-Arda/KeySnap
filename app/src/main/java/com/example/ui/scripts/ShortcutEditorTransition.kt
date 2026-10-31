package com.example.ui.scripts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.viewmodel.ScriptDraft

/** Keep the outgoing draft alive through its exit; edits to the same draft update in place. */
@Composable
internal fun ShortcutEditorTransition(
    draft: ScriptDraft?,
    fabLaunchId: String? = null,
    fabOrigin: Offset? = null,
    editorBackground: Modifier = Modifier,
    content: @Composable BoxScope.(ScriptDraft?) -> Unit,
) {
    var viewport by remember { mutableStateOf(Rect.Zero) }
    fun fromFab(value: ScriptDraft?) = fabOrigin != null && value?.id == fabLaunchId && value?.isNew == true
    AnimatedContent(
        targetState = draft,
        contentKey = { it?.id },
        modifier = Modifier.fillMaxSize().onGloballyPositioned { viewport = it.boundsInRoot() },
        transitionSpec = {
            if (fromFab(targetState ?: initialState)) {
                val motion = if (targetState != null) {
                    EnterTransition.None togetherWith fadeOut(tween(180, delayMillis = 90))
                } else {
                    fadeIn(tween(230, delayMillis = 90)) togetherWith ExitTransition.None
                }
                motion.targetContentZIndex = if (targetState == null) 0f else 1f
                motion.using(SizeTransform(clip = false))
            } else if (targetState != null) {
                (fadeIn(tween(200, delayMillis = 90)) +
                    slideInVertically(tween(260, delayMillis = 90)) { it / 12 }) togetherWith
                    fadeOut(tween(110))
            } else {
                fadeIn(tween(200, delayMillis = 50)) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 16 })
            }
        },
        label = "shortcutEditorDestination",
    ) { target ->
        // A quick second + may replace the launch id while the old draft is still exiting.
        val storedOrigin = remember(target?.id) { fabOrigin?.takeIf { fromFab(target) } }
        val launched = storedOrigin != null
        val pivot = if (storedOrigin != null && viewport.width > 0f && viewport.height > 0f) {
            TransformOrigin(
                ((storedOrigin.x - viewport.left) / viewport.width).coerceIn(0f, 1f),
                ((storedOrigin.y - viewport.top) / viewport.height).coerceIn(0f, 1f),
            )
        } else TransformOrigin(.5f, .9f)
        val motion = if (launched) {
            // Registered on the visibility transition so disposal waits for the entire return.
            val phase by transition.animateFloat(
                transitionSpec = {
                    tween(if (targetState == EnterExitState.Visible) 580 else 420, easing = LinearEasing)
                }, label = "shortcutSpringLaunch",
            ) { if (it == EnterExitState.Visible) 1f else 0f }
            Modifier.graphicsLayer {
                val pose = springLaunchPose(phase)
                transformOrigin = pivot
                scaleX = pose.scaleX
                scaleY = pose.scaleY
                translationY = size.height * pose.shiftY
                rotationX = pose.tilt
                cameraDistance = size.maxDimension * 2f
                alpha = pose.alpha
                shape = RoundedCornerShape(CornerSize(size.minDimension * pose.corner / 100f))
                clip = true
            }.then(editorBackground)
        } else Modifier
        // The home destination emits overlapping page/bar/settings layers. AnimatedContent
        // propagates its size constraints to each root; a Box keeps their own touch sizes intact
        // and owns the align modifiers while the whole destination animates together.
        Box(Modifier.fillMaxSize().then(motion)) { content(target) }
    }
}

private data class LaunchPose(
    val time: Float, val scaleX: Float, val scaleY: Float, val shiftY: Float,
    val tilt: Float, val corner: Float, val alpha: Float,
)

private val LaunchFrames = listOf(
    LaunchPose(0f, .02f, .02f, .02f, 16f, 40f, 0f),
    LaunchPose(.22f, .19f, .24f, -.05f, 20f, 24f, 1f),
    LaunchPose(.73f, 1f, 1.025f, -.018f, -2f, 1f, 1f),
    LaunchPose(.91f, 1f, .995f, .0035f, 0f, 0f, 1f),
    LaunchPose(1f, 1f, 1f, 0f, 0f, 0f, 1f),
)
private val LaunchRise = CubicBezierEasing(.16f, .9f, .22f, 1f)

private fun springLaunchPose(progress: Float): LaunchPose {
    val p = progress.coerceIn(0f, 1f)
    val end = LaunchFrames.indexOfFirst { it.time >= p }.coerceAtLeast(1)
    val a = LaunchFrames[end - 1]
    val b = LaunchFrames[end]
    val linear = ((p - a.time) / (b.time - a.time)).coerceIn(0f, 1f)
    val t = if (a.time == .22f) LaunchRise.transform(linear) else linear
    fun mix(x: Float, y: Float) = x + (y - x) * t
    return LaunchPose(p, mix(a.scaleX, b.scaleX), mix(a.scaleY, b.scaleY),
        mix(a.shiftY, b.shiftY), mix(a.tilt, b.tilt), mix(a.corner, b.corner), mix(a.alpha, b.alpha))
}
