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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.ConsoleShape
import com.example.ui.components.InnerShape
import com.example.service.adb.WirelessAdbManager
import com.example.ui.components.MinTouchTarget
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.delay

/**
 * The first-launch tutorial: the three things Android has to be told before a single shortcut can
 * fire, in dependency order, with only the first unfinished one open. The open step says why it
 * exists, draws a miniature of the settings screen it is about to send the user to, and carries the
 * one button that goes there; a finished step collapses to a check and the word Android itself uses
 * for the state. Steps complete from the real system signals when the user comes back — never from
 * the tap — so the checks are a report rather than a promise.
 *
 * Motion, and the API behind each piece:
 * - step opens / closes: `AnimatedVisibility`, `expandVertically` + `fadeIn` 280 ms, exit 220 ms.
 * - marker number to check: `AnimatedContent`, 300 ms in / 200 ms out.
 * - marker fill, border, title tint and the rail fill: one `updateTransition` per step over
 *   Upcoming / Current / Done — 300 ms, rail 420 ms, focus scale on a `spring`.
 * - the pop when a step completes: an `Animatable` to 1.22 then `spring(dampingRatio = 0.45f)` back,
 *   fired by the state change, so it also plays when Settings is what flipped the step.
 * - card resize: `Modifier.animateContentSize(spring(dampingRatio = 0.9f))`, so Home slides up.
 * - illustration loops: `rememberInfiniteTransition` in `SetupIllustrations.kt`, composed — and so
 *   running — only while their step is open.
 * - all three done: the closing line held 900 ms, then `AnimatedVisibility` exit `shrinkVertically`
 *   320 ms + `fadeOut` 200 ms, after which [onRetired] lets the caller drop the item.
 *
 * With the system animator scale at 0 every duration above collapses to a snap and the loops park
 * on their end frame.
 */
@Composable
fun SetupCard(
    gaps: PermissionGaps,
    isServiceActive: Boolean,
    onOpenAccessibility: () -> Unit,
    onRetired: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val requestNotifications = rememberNotificationPermissionRequest()
    val reduced = rememberReducedMotion()
    val done = listOf(isServiceActive, gaps.battery, gaps.notifications)
    val states = setupStepStates(done)
    val allDone = done.all { it }
    val steps = listOf(
        SetupStep(
            title = stringResource(R.string.home_setup_step_accessibility),
            why = stringResource(R.string.home_setup_why_accessibility),
            whyText = stringResource(R.string.home_setup_whytext_accessibility),
            doneNote = stringResource(R.string.scripts_toggle_state_on),
            button = stringResource(R.string.home_setup_open_accessibility),
            illustration = { AccessibilityIllustration(reduced) },
            onClick = onOpenAccessibility,
        ),
        SetupStep(
            title = stringResource(R.string.home_setup_step_background),
            why = stringResource(R.string.home_setup_why_background),
            whyText = stringResource(R.string.home_setup_whytext_background),
            doneNote = stringResource(R.string.home_setup_done_background),
            button = stringResource(R.string.home_setup_allow_background),
            illustration = { BackgroundDialogIllustration(reduced) },
            onClick = { requestIgnoreBatteryOptimizations(context) },
        ),
        SetupStep(
            title = stringResource(R.string.home_setup_step_notifications),
            why = stringResource(R.string.home_setup_why_notifications),
            whyText = stringResource(R.string.home_setup_whytext_notifications),
            doneNote = stringResource(R.string.home_setup_done_notifications),
            button = stringResource(R.string.home_setup_ask_now),
            illustration = { NotificationIllustration(reduced) },
            onClick = requestNotifications,
        ),
    )

    var phase by remember { mutableStateOf(if (allDone) CardPhase.Closing else CardPhase.Steps) }
    LaunchedEffect(allDone) {
        if (!allDone) {
            phase = CardPhase.Steps
            return@LaunchedEffect
        }
        delay(380)
        phase = CardPhase.Closing
        delay(900)
        phase = CardPhase.Gone
        delay(dur(reduced, 320).toLong())
        onRetired()
    }

    AnimatedVisibility(
        visible = phase != CardPhase.Gone,
        exit = shrinkVertically(tween(dur(reduced, 320), easing = FastOutSlowInEasing)) +
            fadeOut(tween(dur(reduced, 200))),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(ConsoleShape)
                .background(colors.surface)
                .border(1.dp, colors.primary.copy(alpha = .45f), ConsoleShape)
                .testTag("permission_alerts")
                // animateContentSize clips to its bounds; it goes before the padding so the current
                // step's scaled-up marker can overshoot the content edge without being cut.
                .animateContentSize(spring(dampingRatio = 0.9f))
                .padding(16.dp),
        ) {
            AnimatedVisibility(
                visible = phase == CardPhase.Steps,
                exit = fadeOut(tween(dur(reduced, 120))) +
                    shrinkVertically(tween(dur(reduced, 240), easing = FastOutSlowInEasing)),
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            // Nothing runs at all without the service, so that case gets the blunt
                            // title; with it on, the two remaining steps only risk a later kill.
                            Text(
                                stringResource(
                                    if (isServiceActive) R.string.home_setup_title_at_risk else R.string.home_setup_title_off,
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface,
                            )
                            Text(
                                stringResource(
                                    if (isServiceActive) R.string.home_setup_subtitle_at_risk else R.string.home_setup_subtitle_off,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Text(
                            stringResource(R.string.home_setup_progress, done.count { it }),
                            Modifier
                                .clip(InnerShape)
                                .background(colors.primary.copy(alpha = .14f))
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    steps.forEachIndexed { index, step ->
                        SetupStepRow(
                            number = index + 1,
                            step = step,
                            state = states[index],
                            isLast = index == steps.lastIndex,
                            reduced = reduced,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = phase == CardPhase.Closing,
                enter = fadeIn(tween(dur(reduced, 300))) +
                    slideInVertically(tween(dur(reduced, 300), easing = FastOutSlowInEasing)) { it / 3 },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(26.dp).background(colors.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = colors.onPrimary)
                    }
                    Text(
                        stringResource(R.string.home_setup_all_done),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                    )
                }
            }
        }
    }
}

/** Steps, then the closing line, then gone — the card's own three-beat handoff. */
private enum class CardPhase { Steps, Closing, Gone }

private class SetupStep(
    val title: String,
    val why: String,
    val whyText: String,
    val doneNote: String,
    val button: String,
    val illustration: @Composable () -> Unit,
    val onClick: () -> Unit,
)

@Composable
private fun SetupStepRow(number: Int, step: SetupStep, state: SetupStepState, isLast: Boolean, reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    val transition = updateTransition(state, label = "step$number")
    val rail = transition.animateFloat(
        transitionSpec = { tween(dur(reduced, 420), easing = FastOutSlowInEasing) },
        label = "rail",
    ) { if (it == SetupStepState.Done) 1f else 0f }
    val titleColor by transition.animateColor(
        transitionSpec = { tween(dur(reduced, 300)) },
        label = "title",
    ) { if (it == SetupStepState.Current) colors.onSurface else colors.onSurfaceVariant }
    val stateLabel = stringResource(
        when (state) {
            SetupStepState.Done -> R.string.home_setup_state_done
            SetupStepState.Current -> R.string.home_setup_state_current
            SetupStepState.Upcoming -> R.string.home_setup_state_upcoming
        },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .stepRail(colors.outline, colors.primary, isLast) { rail.value },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .semantics(mergeDescendants = true) { stateDescription = stateLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepMarker(number, state, transition, reduced)
            Text(
                step.title,
                Modifier.weight(1f),
                style = if (state == SetupStepState.Done) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = titleColor,
            )
            AnimatedVisibility(
                visible = state == SetupStepState.Done,
                enter = fadeIn(tween(dur(reduced, 250), delayMillis = dur(reduced, 120))) +
                    slideInHorizontally(tween(dur(reduced, 250), delayMillis = dur(reduced, 120))) { it / 2 },
            ) {
                Text(step.doneNote, style = MaterialTheme.typography.labelMedium, color = colors.primary)
            }
        }
        AnimatedVisibility(
            visible = state == SetupStepState.Current,
            enter = expandVertically(tween(dur(reduced, 280), easing = FastOutSlowInEasing)) +
                fadeIn(tween(dur(reduced, 180), delayMillis = dur(reduced, 80))),
            exit = shrinkVertically(tween(dur(reduced, 220), easing = FastOutSlowInEasing)) +
                fadeOut(tween(dur(reduced, 160))),
        ) {
            Column(Modifier.padding(start = 38.dp, bottom = 14.dp)) {
                Text(step.why, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                step.illustration()
                // The long answer, for the user who wants one before granting: opened per step, and
                // forgotten again as soon as the step stops being the current one.
                var whyOpen by remember(state) { mutableStateOf(false) }
                AnimatedVisibility(
                    visible = whyOpen,
                    enter = expandVertically(tween(dur(reduced, 280), easing = FastOutSlowInEasing)) +
                        fadeIn(tween(dur(reduced, 180), delayMillis = dur(reduced, 80))),
                    exit = shrinkVertically(tween(dur(reduced, 220), easing = FastOutSlowInEasing)) +
                        fadeOut(tween(dur(reduced, 160))),
                ) {
                    Text(
                        step.whyText,
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                val whyState = stringResource(
                    if (whyOpen) R.string.home_setup_why_expanded else R.string.home_setup_why_collapsed,
                )
                Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = step.onClick,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
                    ) {
                        Text(step.button, style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(
                        onClick = { whyOpen = !whyOpen },
                        modifier = Modifier.semantics { stateDescription = whyState },
                    ) {
                        Text(
                            stringResource(R.string.home_setup_why),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

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
    val bob = rememberInfiniteTransition(label = "nudge").animateFloat(
        initialValue = -2f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(colors.primary.copy(alpha = .10f))
            .border(1.dp, colors.primary.copy(alpha = .30f), InnerShape)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.ArrowDownward,
            null,
            Modifier.graphicsLayer { translationY = if (reduced) 0f else bob.value.dp.toPx() },
            tint = colors.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_nudge_title),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
            Text(
                stringResource(R.string.home_nudge_subtitle),
                style = MaterialTheme.typography.labelSmall,
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
) {
    val any: Boolean get() = !notifications || !battery
}

/**
 * Both values are cheap system reads that only ever change in Settings — i.e. on the resume
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
 * screen for the pairing channel, the only notification this app posts.
 */
@Composable
private fun rememberNotificationPermissionRequest(): () -> Unit {
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
    WirelessAdbManager.ensurePairingNotificationChannel(context)
    context.startActivity(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, WirelessAdbManager.PAIRING_CHANNEL_ID)
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
private fun requestIgnoreBatteryOptimizations(context: Context) {
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
