package com.example.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.components.InnerShape
import io.github.nicholasarda.keysnap.R

/**
 * The rail between one step's marker and the next, and its accent fill. The next marker is centred
 * in its own 48 dp row, so the line has to overshoot this step's bounds by that row's leading half
 * to meet it. Shared with the Shortcuts tutorial, which draws the same rail.
 */
internal fun Modifier.stepRail(
    outline: Color,
    primary: Color,
    isLast: Boolean,
    fill: () -> Float,
) = drawBehind {
    if (isLast) return@drawBehind
    val x = 13.dp.toPx()
    val top = 39.dp.toPx()
    val bottom = size.height + 11.dp.toPx()
    val stroke = 2.dp.toPx()
    drawLine(outline, Offset(x, top), Offset(x, bottom), stroke, StrokeCap.Round)
    val filled = fill()
    if (filled > 0f) {
        drawLine(primary, Offset(x, top), Offset(x, top + (bottom - top) * filled), stroke, StrokeCap.Round)
    }
}

/** The numbered bead on the rail: number while it is pending, a check once the signal says so. */
@Composable
internal fun StepMarker(
    number: Int,
    state: SetupStepState,
    transition: Transition<SetupStepState>,
    reduced: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val fill = transition.animateColor(
        transitionSpec = { tween(dur(reduced, 300)) },
        label = "markerFill",
    ) { if (it == SetupStepState.Done) colors.primary else Color.Transparent }
    val stroke = transition.animateColor(
        transitionSpec = { tween(dur(reduced, 300)) },
        label = "markerBorder",
    ) { if (it == SetupStepState.Upcoming) colors.outline else colors.primary }
    val focus = transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.45f) },
        label = "markerFocus",
    ) { if (it == SetupStepState.Current) 1.08f else 1f }
    // Only a step that *becomes* done pops; one that was already done when the card first drew is
    // history, not news.
    val pop = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        val justCompleted = state == SetupStepState.Done && previous != SetupStepState.Done
        previous = state
        if (justCompleted && !reduced) {
            pop.animateTo(1.22f, tween(120, easing = FastOutSlowInEasing))
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
    }
    Box(
        Modifier
            .size(26.dp)
            .graphicsLayer {
                scaleX = focus.value * pop.value
                scaleY = focus.value * pop.value
            }
            .drawBehind {
                drawCircle(fill.value)
                val width = 2.dp.toPx()
                drawCircle(stroke.value, radius = (size.minDimension - width) / 2f, style = Stroke(width))
            }
            // The bead repeats what the row already says; the row carries the state description.
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state == SetupStepState.Done,
            transitionSpec = {
                (fadeIn(tween(dur(reduced, 300))) + scaleIn(tween(dur(reduced, 300)), initialScale = 0.6f)) togetherWith
                    (fadeOut(tween(dur(reduced, 200))) + scaleOut(tween(dur(reduced, 200)), targetScale = 0.6f))
            },
            label = "markerGlyph",
        ) { isDone ->
            if (isDone) {
                Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = colors.onPrimary)
            } else {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state == SetupStepState.Upcoming) colors.onSurfaceVariant else colors.primary,
                )
            }
        }
    }
}

/**
 * The one thing left to do once the tutorial retires itself: the suggestions right below are one tap
 * each, and this points at them until the user makes a shortcut or dismisses it.
 */
@Composable
fun FirstShortcutNudge(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val bob = remember { Animatable(0f) }
    val played = remember { booleanArrayOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reduced) {
        if (reduced) {
            bob.snapTo(0f)
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (played[0]) {
                bob.snapTo(0f)
                return@repeatOnLifecycle
            }
            // Three nods, the same 800 ms travel the loop used to repeat, then the arrow rests.
            bob.snapTo(-2f)
            repeat(3) {
                bob.animateTo(3f, tween(800, easing = FastOutSlowInEasing))
                bob.animateTo(-2f, tween(800, easing = FastOutSlowInEasing))
            }
            bob.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
            played[0] = true
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(colors.primary.copy(alpha = .10f))
            .border(1.dp, colors.primary.copy(alpha = .30f), InnerShape)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        // Between the arrow and the 48 dp close button the text had a third of the row; narrow,
        // the arrow goes and the text keeps the room.
        val narrow = isNarrow(maxWidth)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!narrow) {
                Icon(
                    Icons.Filled.ArrowDownward,
                    null,
                    Modifier.graphicsLayer { translationY = if (reduced) 0f else bob.value.dp.toPx() },
                    tint = colors.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_nudge_title),
                    style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.home_nudge_subtitle),
                    style = MaterialTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                    color = colors.onSurfaceVariant,
                )
            }
            IconButton(onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    stringResource(R.string.home_nudge_dismiss),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** True when the user has switched system animations off; every duration here then snaps to 0. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

internal fun dur(reduced: Boolean, millis: Int) = if (reduced) 0 else millis

/** Which of the revocable permissions are missing right now. */
data class PermissionGaps(
    val notifications: Boolean,
    val battery: Boolean,
    val usageAccess: Boolean = true,
) {
    /** Initial setup only; Usage Access is required conditionally by enabled key shortcuts. */
    val any: Boolean get() = !battery
}

/**
 * These values are cheap system reads that only ever change in Settings — i.e. on the resume
 * this watches for — so they are read here instead of being threaded through the view model.
 * [com.example.ui.MainScreen] does the same for `Settings.canDrawOverlays`.
 */
@Composable
fun rememberPermissionGaps(): PermissionGaps {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var gaps by remember { mutableStateOf(readPermissionGaps(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) gaps = readPermissionGaps(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return gaps
}

private fun readPermissionGaps(context: Context) = PermissionGaps(
    notifications = canPostNotifications(context),
    battery = isIgnoringBatteryOptimizations(context),
    usageAccess = com.example.service.hasUsageAccess(context),
)

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) ?: true

private fun canPostNotifications(context: Context): Boolean =
    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * The runtime dialog first: that is the only place Android shows Allow/Deny, and it is what the user
 * expects from a "Fix" button. Once the permission is permanently denied the dialog no longer
 * appears at all, so that case — and pre-13, where there is no dialog — falls back to the settings
 * screen for the app's notifications.
 */
@Composable
internal fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context as? Activity
        val dialogIsGone = activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        if (!granted && dialogIsGone) openNotificationSettings(context)
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context)
        }
    }
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * The system dialog is the only writer of the doze allowlist that `isIgnoringBatteryOptimizations`
 * reads: an OEM App info page can show "Unrestricted" and still write nothing but the OEM's own
 * list, which is why step 2 was uncompletable on ColorOS. Where the dialog is missing entirely,
 * App info is still the best remaining door.
 */
// Play accepts this permission when the app's core function needs the background: KeySnap exists
// to react to hardware keys while backgrounded, and asks once, here, during setup.
@SuppressLint("BatteryLife")
internal fun requestIgnoreBatteryOptimizations(context: Context) {
    // No FLAG_ACTIVITY_NEW_TASK: the dialog is hosted by Settings, and in Settings' own task it
    // dismisses onto whatever that task had underneath. In ours it dismisses back to this card,
    // which is where the step has to tick over.
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openAppInfo(context, HIGHLIGHT_BATTERY)
    }
}

/** The AOSP preference key for the battery row on the App info page. */
private const val HIGHLIGHT_BATTERY = "battery"

// The two undocumented-but-stable extras Settings search uses to scroll to a row and flash it. An
// OEM Settings that ignores them still opens App info, just without the highlight.
private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

/** App info for this app, highlighting the row the user has to press. */
private fun openAppInfo(context: Context, highlightKey: String) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, highlightKey)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, bundleOf(EXTRA_FRAGMENT_ARG_KEY to highlightKey))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
