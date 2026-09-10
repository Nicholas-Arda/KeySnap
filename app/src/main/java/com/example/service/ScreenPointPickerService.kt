package com.example.service

import android.content.Context
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.example.data.AppLocale
import com.example.ui.components.ConsoleShape
import io.github.nicholasarda.keysnap.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A draggable crosshair (or, in [PointPickMode.TWO_POINTS], two independently draggable
 * crosshairs plus a connecting line) the user positions over whatever app is on screen, plus a
 * control bar that reads out the live coordinates. Each piece is its own small, content-sized
 * [WindowManager] window (not one full-screen window) with FLAG_NOT_TOUCH_MODAL: touches outside
 * those windows fall through to the app below, so the user can navigate to their target before
 * aiming. That property is the point of the whole design and must survive any future rework.
 */
class ScreenPointPickerService : Service() {
    // Services get their own resources from the system, so the in-app language picker has to be
    // applied here too or their notifications and overlays stay in the system language.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))


    private lateinit var windowManager: WindowManager
    private var mode: PointPickMode = PointPickMode.SINGLE

    private var crosshairAWindow: ComposeView? = null
    private var crosshairBWindow: ComposeView? = null
    private var connectorWindow: ComposeView? = null
    private var barWindow: ComposeView? = null
    private var crosshairAOwner: OverlayLifecycleOwner? = null
    private var crosshairBOwner: OverlayLifecycleOwner? = null
    private var connectorOwner: OverlayLifecycleOwner? = null
    private var barOwner: OverlayLifecycleOwner? = null

    private var crosshairAParams: WindowManager.LayoutParams? = null
    private var crosshairBParams: WindowManager.LayoutParams? = null
    private var overlaySizePx = 0

    // Compose-observed state: reads inside @Composable functions recompose automatically when these
    // change, so there is no manual "refresh the readout" step like the old View-based bar needed.
    // Both points exist for every mode (B is simply unused in SINGLE) so the connector and control
    // bar can read them unconditionally.
    private var pointAX by mutableIntStateOf(0)
    private var pointAY by mutableIntStateOf(0)
    private var pointBX by mutableIntStateOf(0)
    private var pointBY by mutableIntStateOf(0)
    private var isDraggingA by mutableStateOf(false)
    private var isDraggingB by mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mode = runCatching { PointPickMode.valueOf(intent?.getStringExtra(EXTRA_MODE).orEmpty()) }
            .getOrDefault(PointPickMode.SINGLE)
        windowManager = getSystemService(WindowManager::class.java)
        // A fresh pick request always starts a new session, even when the overlay is still up from
        // a previous confirm: the non-modal design lets the user return to the app and tap "Pick on
        // screen" again while the service keeps running. Tear down any windows from that previous
        // session before laying out this one, or it would leak a stuck stale overlay alongside the
        // new one.
        if (crosshairAWindow != null || crosshairBWindow != null) removeAllWindows()
        showOverlay()
        return START_NOT_STICKY
    }

    private fun themeMode(): ThemeMode {
        val prefs: SharedPreferences = getSharedPreferences("arda_mapper_appearance", MODE_PRIVATE)
        return prefs.getString("theme_mode", null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: if (prefs.getBoolean("dark_theme", true)) ThemeMode.DARK else ThemeMode.LIGHT
    }

    private fun showOverlay() {
        val metrics = resources.displayMetrics
        overlaySizePx = (OVERLAY_SIZE_DP * metrics.density).roundToInt()
        val centerX = metrics.widthPixels / 2
        val centerY = metrics.heightPixels / 2

        if (mode == PointPickMode.TWO_POINTS) {
            // Start the two points apart, not stacked, so both are immediately visible and
            // draggable without the user having to separate them first.
            val spread = (POINT_SPREAD_DP * metrics.density).roundToInt()
            pointAX = (centerX - spread).coerceIn(overlaySizePx / 2, metrics.widthPixels - overlaySizePx / 2)
            pointAY = (centerY - spread).coerceIn(overlaySizePx / 2, metrics.heightPixels - overlaySizePx / 2)
            pointBX = (centerX + spread).coerceIn(overlaySizePx / 2, metrics.widthPixels - overlaySizePx / 2)
            pointBY = (centerY + spread).coerceIn(overlaySizePx / 2, metrics.heightPixels - overlaySizePx / 2)
        } else {
            pointAX = centerX
            pointAY = centerY
        }

        val theme = themeMode()

        if (mode == PointPickMode.TWO_POINTS) {
            addConnectorWindow(theme, metrics.widthPixels, metrics.heightPixels)
        }
        addCrosshairAWindow(theme, label = if (mode == PointPickMode.TWO_POINTS) "A" else null)
        if (mode == PointPickMode.TWO_POINTS) {
            addCrosshairBWindow(theme)
        }
        addBarWindow(theme)

        // SYSTEM_ALERT_WINDOW may be missing or revoked between the caller's check and this call
        // (TOCTOU); addView then throws WindowManager.BadTokenException. Degrade to the manual X/Y
        // fields instead of letting that crash the app.
        val allAdded = crosshairAWindow != null && barWindow != null &&
            (mode != PointPickMode.TWO_POINTS || (crosshairBWindow != null && connectorWindow != null))
        if (!allAdded) {
            ScreenPointPicker.cancel()
            stopSelf()
        }
    }

    private fun addCrosshairAWindow(theme: ThemeMode, label: String?) {
        val owner = OverlayLifecycleOwner().also { it.start() }
        val params = WindowManager.LayoutParams(
            overlaySizePx,
            overlaySizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = pointAX - overlaySizePx / 2
            y = pointAY - overlaySizePx / 2
        }
        crosshairAParams = params
        val view = ComposeView(this).apply {
            attachOwners(owner)
            setContent {
                MyApplicationTheme(theme) {
                    CrosshairOverlay(
                        color = MaterialTheme.colorScheme.primary,
                        label = label,
                        isDragging = isDraggingA,
                        onDragStart = { isDraggingA = true },
                        onDrag = { delta ->
                            params.x += delta.x.roundToInt()
                            params.y += delta.y.roundToInt()
                            runCatching { windowManager.updateViewLayout(crosshairAWindow, params) }
                            pointAX = params.x + overlaySizePx / 2
                            pointAY = params.y + overlaySizePx / 2
                        },
                        onDragEnd = { isDraggingA = false },
                    )
                }
            }
        }
        if (runCatching { windowManager.addView(view, params) }.isSuccess) {
            crosshairAWindow = view
            crosshairAOwner = owner
        }
    }

    private fun addCrosshairBWindow(theme: ThemeMode) {
        val owner = OverlayLifecycleOwner().also { it.start() }
        val params = WindowManager.LayoutParams(
            overlaySizePx,
            overlaySizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = pointBX - overlaySizePx / 2
            y = pointBY - overlaySizePx / 2
        }
        crosshairBParams = params
        val view = ComposeView(this).apply {
            attachOwners(owner)
            setContent {
                MyApplicationTheme(theme) {
                    CrosshairOverlay(
                        color = MaterialTheme.colorScheme.primary,
                        label = "B",
                        isDragging = isDraggingB,
                        onDragStart = { isDraggingB = true },
                        onDrag = { delta ->
                            params.x += delta.x.roundToInt()
                            params.y += delta.y.roundToInt()
                            runCatching { windowManager.updateViewLayout(crosshairBWindow, params) }
                            pointBX = params.x + overlaySizePx / 2
                            pointBY = params.y + overlaySizePx / 2
                        },
                        onDragEnd = { isDraggingB = false },
                    )
                }
            }
        }
        if (runCatching { windowManager.addView(view, params) }.isSuccess) {
            crosshairBWindow = view
            crosshairBOwner = owner
        }
    }

    /** Full-screen, touch-transparent window drawn beneath both crosshairs so it never blocks
     * dragging them or reaching the app underneath; only its Canvas content is visible. */
    private fun addConnectorWindow(theme: ThemeMode, widthPx: Int, heightPx: Int) {
        val owner = OverlayLifecycleOwner().also { it.start() }
        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        val view = ComposeView(this).apply {
            attachOwners(owner)
            setContent {
                MyApplicationTheme(theme) {
                    val gesture by ScreenPointPicker.gesture.collectAsState()
                    ConnectorOverlay(
                        color = MaterialTheme.colorScheme.primary,
                        ax = pointAX,
                        ay = pointAY,
                        bx = pointBX,
                        by = pointBY,
                        gesture = gesture,
                    )
                }
            }
        }
        if (runCatching { windowManager.addView(view, params) }.isSuccess) {
            connectorWindow = view
            connectorOwner = owner
        }
    }

    private fun addBarWindow(theme: ThemeMode) {
        val owner = OverlayLifecycleOwner().also { it.start() }
        val view = ComposeView(this).apply {
            attachOwners(owner)
            setContent {
                MyApplicationTheme(theme) {
                    ControlBar(
                        mode = mode,
                        ax = pointAX,
                        ay = pointAY,
                        bx = pointBX,
                        by = pointBY,
                        onCancel = {
                            ScreenPointPicker.cancel()
                            stopSelf()
                        },
                        onConfirm = ::onConfirm,
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }
        if (runCatching { windowManager.addView(view, params) }.isSuccess) {
            barWindow = view
            barOwner = owner
        }
    }

    private fun onConfirm() {
        val pointA = ScreenPoint(pointAX, pointAY)
        val pointB = if (mode == PointPickMode.TWO_POINTS) ScreenPoint(pointBX, pointBY) else null
        ScreenPointPicker.submit(PickedPoints(pointA, pointB))
        returnToApp()
        stopSelf()
    }

    /** Brings the still-open configuration sheet back so it can read the picked points. */
    private fun returnToApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { startActivity(intent) }
    }

    private fun removeAllWindows() {
        crosshairAWindow?.let { runCatching { windowManager.removeView(it) } }
        crosshairBWindow?.let { runCatching { windowManager.removeView(it) } }
        connectorWindow?.let { runCatching { windowManager.removeView(it) } }
        barWindow?.let { runCatching { windowManager.removeView(it) } }
        crosshairAOwner?.destroy()
        crosshairBOwner?.destroy()
        connectorOwner?.destroy()
        barOwner?.destroy()
        crosshairAWindow = null
        crosshairBWindow = null
        connectorWindow = null
        barWindow = null
        crosshairAOwner = null
        crosshairBOwner = null
        connectorOwner = null
        barOwner = null
    }

    override fun onDestroy() {
        removeAllWindows()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        private const val OVERLAY_SIZE_DP = 120
        private const val POINT_SPREAD_DP = 90
    }
}

/**
 * A [ComposeView] hosted in a raw [WindowManager] window has no Activity to supply the tree owners
 * Compose needs, so this service is its own minimal [LifecycleOwner]/[SavedStateRegistryOwner]/
 * [ViewModelStoreOwner], moved straight to RESUMED since the overlay has no paused state of its own.
 */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun start() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}

private fun ComposeView.attachOwners(owner: OverlayLifecycleOwner) {
    setViewTreeLifecycleOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
    setViewTreeViewModelStoreOwner(owner)
}

/**
 * Soft-glowing ring with tick marks and a filled centre dot; scales up slightly while dragged.
 * When [label] is set (two-point mode) a small "A"/"B" badge sits on the ring's edge so the two
 * simultaneously visible, simultaneously draggable crosshairs stay distinguishable.
 */
@Composable
private fun CrosshairOverlay(
    color: Color,
    label: String?,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.16f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "crosshairScale",
    )
    Box(
        modifier = Modifier
            .size(CROSSHAIR_WINDOW_SIZE)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(CROSSHAIR_RING_SIZE), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(scale)
                    .shadow(elevation = 14.dp, shape = CircleShape, ambientColor = color, spotColor = color)
                    .background(color.copy(alpha = 0.14f), CircleShape)
                    .border(2.dp, color, CircleShape),
            )
            Canvas(modifier = Modifier.matchParentSize().scale(scale)) {
                val tick = 7.dp.toPx()
                val strokeWidth = 3.dp.toPx()
                drawLine(color, Offset(size.width / 2f, 0f), Offset(size.width / 2f, tick), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width / 2f, size.height - tick), Offset(size.width / 2f, size.height), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(0f, size.height / 2f), Offset(tick, size.height / 2f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width - tick, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth, StrokeCap.Round)
            }
            Box(Modifier.size(10.dp).scale(scale).background(color, CircleShape))
            if (label != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(20.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .background(color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/**
 * The line joining Point A and Point B, drawn on a full-screen touch-transparent window beneath
 * both crosshairs. [gesture] decides which way the arrowheads point: a swipe reads as a single
 * push from A to B; pinch-out (spread apart) reads as the two fingers' axis diverging, with
 * arrowheads at the very ends pointing away from each other; pinch-in (pinch together) reads as
 * the opposite, with arrowheads placed inboard of each crosshair — so they stay visible instead of
 * hiding under the ring — pointing toward the midpoint.
 */
@Composable
private fun ConnectorOverlay(color: Color, ax: Int, ay: Int, bx: Int, by: Int, gesture: TwoPointGesture) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val start = Offset(ax.toFloat(), ay.toFloat())
        val end = Offset(bx.toFloat(), by.toFloat())
        val strokeWidth = 4.dp.toPx()
        drawLine(color, start, end, strokeWidth, StrokeCap.Round)
        val arrowLength = 18.dp.toPx()
        val forward = end - start
        val backward = start - end
        when (gesture) {
            TwoPointGesture.SWIPE ->
                drawArrowhead(color, tip = end, direction = forward, length = arrowLength, strokeWidth = strokeWidth)
            TwoPointGesture.PINCH_OUT -> {
                drawArrowhead(color, tip = end, direction = forward, length = arrowLength, strokeWidth = strokeWidth)
                drawArrowhead(color, tip = start, direction = backward, length = arrowLength, strokeWidth = strokeWidth)
            }
            TwoPointGesture.PINCH_IN -> {
                val innerA = lerp(start, end, 0.35f)
                val innerB = lerp(start, end, 0.65f)
                drawArrowhead(color, tip = innerA, direction = forward, length = arrowLength, strokeWidth = strokeWidth)
                drawArrowhead(color, tip = innerB, direction = backward, length = arrowLength, strokeWidth = strokeWidth)
            }
            TwoPointGesture.NONE -> Unit
        }
    }
}

/** Draws a "V" arrowhead whose point sits at [tip], aimed along [direction]. */
private fun DrawScope.drawArrowhead(color: Color, tip: Offset, direction: Offset, length: Float, strokeWidth: Float) {
    val angle = atan2(direction.y, direction.x)
    val spread = Math.toRadians(28.0)
    val leftAngle = angle + spread
    val rightAngle = angle - spread
    val left = Offset(tip.x - length * cos(leftAngle).toFloat(), tip.y - length * sin(leftAngle).toFloat())
    val right = Offset(tip.x - length * cos(rightAngle).toFloat(), tip.y - length * sin(rightAngle).toFloat())
    drawLine(color, tip, left, strokeWidth, StrokeCap.Round)
    drawLine(color, tip, right, strokeWidth, StrokeCap.Round)
}

@Composable
private fun ControlBar(
    mode: PointPickMode,
    ax: Int,
    ay: Int,
    bx: Int,
    by: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    ) {
        // A deeper "surfaceContainerHigh" tone (not plain `surface`) plus a strong shadow and an
        // amber top edge, so this reads as a distinct floating control rather than one more row of
        // whatever app sheet happens to be open underneath it.
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            shape = ConsoleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        // The Surface above already clips this strip to the panel's top corners.
                        .background(MaterialTheme.colorScheme.primary),
                )
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    if (mode == PointPickMode.TWO_POINTS) {
                        Text(
                            text = stringResource(R.string.picker_point_a, ax, ay),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.picker_point_b, bx, by),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.picker_point, ax, ay),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.common_confirm))
                        }
                    }
                }
            }
        }
    }
}

private val CROSSHAIR_WINDOW_SIZE = 120.dp
private val CROSSHAIR_RING_SIZE = 64.dp
