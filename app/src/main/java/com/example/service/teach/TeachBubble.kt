package com.example.service.teach

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.bridge.LearnedSetting
import com.example.data.ScriptAction
import com.example.data.ScriptActionCatalog
import com.example.data.TaughtKey
import com.example.data.TeachOutcome
import com.example.data.TeachRules
import com.example.service.AutomationUndo
import com.example.service.KeyMapperAccessibilityService
import com.example.service.bridge.BridgeController
import com.example.service.bridge.BridgeState
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * "Teach a setting": the circle over Settings. Tap, change one switch, tap, change it back, tap; a
 * green tick means the bridge saw which keys follow the switch. One more tap flips it for the user
 * ("Did it change?"); on Yes it is put back and the circle becomes a one-tap return to KeySnap, which
 * shows the save sheet. Drawn as an Accessibility overlay, so no "Display over other apps" permission.
 * Nothing the bridge reports is logged.
 */
object TeachBubble {
    enum class Step { READY, WATCHING, HALF, GOT_IT, ASK, DONE, ERROR }

    data class Ui(val step: Step = Step.READY, @StringRes val caption: Int = R.string.teach_cap_ready, val busy: Boolean = false)

    /** What the app shows once the user is back. */
    sealed interface Handoff {
        data class Learned(val keys: List<TaughtKey>) : Handoff
        data object GaveUp : Handoff
    }

    private val ui = MutableStateFlow(Ui())
    private val _handoff = MutableStateFlow<Handoff?>(null)
    val handoff: StateFlow<Handoff?> = _handoff.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var window: OverlayWindow? = null
    private var keys: List<TaughtKey> = emptyList()
    private var failures = 0
    private var errorNext = Step.READY

    fun consumeHandoff() {
        _handoff.value = null
    }

    /** Shows the circle and opens Settings; the reason it can't, or null when it started. */
    @StringRes
    fun begin(context: Context): Int? {
        if (BridgeController.state.value != BridgeState.RUNNING) return R.string.teach_needs_advanced
        val service = KeyMapperAccessibilityService.connectedOverlayContext() ?: return R.string.teach_needs_accessibility
        close()
        failures = 0
        keys = emptyList()
        ui.value = Ui()
        window = runCatching { OverlayWindow(service) }.getOrNull() ?: return R.string.teach_needs_accessibility
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return null
    }

    /**
     * A reconnecting Accessibility service loses its overlay windows (ColorOS rebinds services, and so
     * does any UiAutomation client), so a running session draws its circle again on the new connection.
     */
    fun onServiceConnected(service: Context) {
        if (window == null) return
        close()
        window = runCatching { OverlayWindow(service) }.getOrNull()
    }

    private fun close() {
        window?.remove()
        window = null
    }

    private fun cancel() {
        close()
        scope.launch { BridgeController.teach("clear") }
    }

    private fun tap(context: Context) {
        val now = ui.value
        if (now.busy || now.step == Step.ASK) return
        scope.launch {
            ui.value = now.copy(busy = true)
            ui.value = when (now.step) {
                Step.READY -> if (BridgeController.teach("start") == "ok") Ui(Step.WATCHING, R.string.teach_cap_watching) else lost()
                Step.WATCHING -> when (BridgeController.teach("changed")?.toIntOrNull()) {
                    null -> lost()
                    0 -> error(R.string.teach_err_no_change, Step.WATCHING, counts = false)
                    else -> Ui(Step.HALF, R.string.teach_cap_half)
                }
                Step.HALF -> when (val outcome = TeachRules.parseBack(BridgeController.teach("back"))) {
                    is TeachOutcome.Learned -> {
                        keys = outcome.keys
                        Ui(Step.GOT_IT, R.string.teach_cap_got_it)
                    }
                    TeachOutcome.NoChange -> error(R.string.teach_err_no_change, Step.HALF, counts = false)
                    TeachOutcome.TooMany -> error(R.string.teach_err_too_many, Step.READY)
                    TeachOutcome.Blocked -> error(R.string.teach_err_not_allowed, Step.READY)
                    TeachOutcome.Lost -> lost()
                }
                Step.GOT_IT -> if (flip(context, toChanged = true)) Ui(Step.ASK, R.string.teach_cap_ask) else error(R.string.teach_err_not_allowed, Step.READY)
                Step.DONE -> return@launch finish(context, Handoff.Learned(keys))
                Step.ERROR -> if (failures >= 2) return@launch finish(context, Handoff.GaveUp) else Ui(errorNext, captionFor(errorNext))
                Step.ASK -> now
            }
        }
    }

    private fun answer(context: Context, yes: Boolean) {
        if (ui.value.busy) return
        scope.launch {
            ui.value = ui.value.copy(busy = true)
            // Put back as it was either way, so the user never has to go and fix it.
            flip(context, toChanged = false)
            ui.value = if (yes) Ui(Step.DONE, R.string.teach_cap_done) else error(R.string.teach_err_retry, Step.READY)
        }
    }

    /**
     * Writes the side the user changed to (or back to the start) through the bridge, then reads the
     * state key ~1.5 s later. False when the phone put it back on its own or nothing answered.
     */
    private suspend fun flip(context: Context, toChanged: Boolean): Boolean {
        val writes = TeachRules.writes(keys)
        val first = LearnedSetting.parse(writes)!![0]
        val changed = keys.first { it.namespace == first.namespace && it.key == first.key }.changed
        val wantOn = (first.on == changed) == toChanged
        val action = ScriptAction(
            ScriptActionCatalog.LEARNED_SETTING,
            mapOf(LearnedSetting.PARAM_WRITES to writes, LearnedSetting.PARAM_STATE to if (wantOn) "on" else "off"),
        )
        if (BridgeController.runSingleActionForTest(action)?.ok != true) return false
        delay(1_500)
        return AutomationUndo.preset(context, LearnedSetting.variants(writes)!!) == wantOn
    }

    private suspend fun error(@StringRes caption: Int, next: Step, counts: Boolean = true): Ui {
        if (counts) failures++
        errorNext = next
        if (next == Step.READY) BridgeController.teach("clear")
        return Ui(Step.ERROR, caption)
    }

    private suspend fun lost() = error(R.string.teach_err_lost, Step.READY, counts = false)

    private fun captionFor(step: Step) = when (step) {
        Step.WATCHING -> R.string.teach_cap_watching
        Step.HALF -> R.string.teach_cap_half
        else -> R.string.teach_cap_ready
    }

    private fun finish(context: Context, handoff: Handoff) {
        _handoff.value = handoff
        close()
        // A system-bound Accessibility service may start an activity from the background.
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { runCatching { context.startActivity(it) } }
    }

    private class OverlayWindow(private val service: Context) {
        private val manager = service.getSystemService(WindowManager::class.java)
        private val owner = OverlayOwner()
        private val density = service.resources.displayMetrics.density
        private val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (8 * density).toInt()
            y = (service.resources.displayMetrics.heightPixels * 0.42f).toInt()
        }
        private val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val state by ui.collectAsState()
                BubbleUi(
                    state = state,
                    onTap = { tap(service) },
                    onAnswer = { answer(service, it) },
                    onClose = { cancel() },
                    onDrag = { dx, dy ->
                        params.x = (params.x - dx).toInt()
                        params.y = (params.y + dy).toInt()
                        runCatching { manager.updateViewLayout(this, params) }
                    },
                )
            }
        }

        init {
            owner.resume()
            manager.addView(view, params)
        }

        fun remove() {
            runCatching { manager.removeView(view) }
            owner.destroy()
        }
    }

    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry get() = saved.savedStateRegistry

        fun resume() {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

private val Blue = Color(0xFF006EFD)
private val Red = Color(0xFFD93025)
private val Green = Color(0xFF1E9E4A)
private val Ink = Color(0xFF1B1C20)

@Composable
private fun BubbleUi(
    state: TeachBubble.Ui,
    onTap: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val step = state.step
    val view = LocalView.current
    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val captionColor by animateColorAsState(
                when (step) {
                    TeachBubble.Step.GOT_IT -> Green
                    TeachBubble.Step.ERROR -> Red
                    else -> Ink
                },
                label = "caption",
            )
            Row(
                Modifier.widthIn(max = 190.dp).clip(RoundedCornerShape(14.dp)).background(captionColor).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.common_close), onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.common_close), tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(16.dp))
                }
                AnimatedContent(state.caption, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "captionText") {
                    Text(stringResource(it), color = Color.White, fontSize = 13.sp, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Bubble(
                state,
                Modifier
                    .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } }
                    .clickable(onClickLabel = stringResource(state.caption)) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTap()
                    },
            )
        }
        AnimatedVisibility(step == TeachBubble.Step.ASK) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnswerChip(stringResource(R.string.common_yes), Green) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onAnswer(true) }
                AnswerChip(stringResource(R.string.common_no), Red) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); onAnswer(false) }
            }
        }
    }
}

@Composable
private fun AnswerChip(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun Bubble(state: TeachBubble.Ui, modifier: Modifier) {
    val step = state.step
    val recording = step == TeachBubble.Step.WATCHING || step == TeachBubble.Step.HALF
    val fill by animateColorAsState(
        when (step) {
            TeachBubble.Step.WATCHING, TeachBubble.Step.HALF -> Red
            TeachBubble.Step.GOT_IT, TeachBubble.Step.ASK -> Green
            TeachBubble.Step.ERROR -> Color.White
            else -> Blue
        },
        label = "fill",
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val wave by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "wave")
    Box(
        modifier
            .size(64.dp)
            .drawBehind {
                if (recording) {
                    val r = size.minDimension / 2 * (0.95f + 0.4f * wave)
                    drawCircle(Red.copy(alpha = 1f - wave), radius = r, style = Stroke(3.dp.toPx()))
                }
            }
            .clip(CircleShape)
            .background(fill)
            .then(if (step == TeachBubble.Step.ERROR) Modifier.border(3.dp, Red, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            step,
            transitionSpec = { scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = .6f) + fadeIn() togetherWith fadeOut(tween(90)) },
            contentKey = { if (it == TeachBubble.Step.HALF) TeachBubble.Step.WATCHING else it },
            label = "glyph",
        ) { target ->
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                when (target) {
                    TeachBubble.Step.READY -> Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
                    TeachBubble.Step.WATCHING, TeachBubble.Step.HALF -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
                    TeachBubble.Step.GOT_IT -> Tick()
                    TeachBubble.Step.ASK -> Icon(Icons.Filled.SwapHoriz, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    TeachBubble.Step.DONE -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    TeachBubble.Step.ERROR -> Text("!", color = Red, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                }
            }
        }
        if (step == TeachBubble.Step.HALF) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                drawArc(Color.White.copy(alpha = .35f), 0f, 360f, false, style = stroke)
                drawArc(Color.White, -90f, 180f, false, style = stroke)
            }
        }
        if (state.busy) {
            CircularProgressIndicator(Modifier.fillMaxSize().padding(2.dp), color = Color.White.copy(alpha = .85f), strokeWidth = 3.dp)
        }
    }
}

/** The tick draws itself once, like the key-record button's. */
@Composable
private fun Tick() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(450)) }
    Canvas(Modifier.size(64.dp)) {
        val unit = size.minDimension / 64f
        val path = Path().apply {
            moveTo(20 * unit, 33 * unit)
            lineTo(28 * unit, 41 * unit)
            lineTo(44 * unit, 23 * unit)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * progress.value, partial, true)
        drawPath(partial, Color.White, style = Stroke(6 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
