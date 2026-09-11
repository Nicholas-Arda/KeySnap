package com.example.ui.scripts

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ConsoleShape
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.components.glassCard
import com.example.ui.home.SetupStepState
import com.example.ui.home.StepMarker
import com.example.ui.home.dur
import com.example.ui.home.rememberReducedMotion
import com.example.ui.home.stepRail
import com.example.ui.theme.LocalGlass
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The empty Shortcuts page's own tutorial: the four taps that make a first shortcut, played on the
 * numbered rail Home's setup card uses, restarting for as long as the page stays empty. Nothing here
 * waits on a permission, so the rail advances itself — one step open at a time, ticked when its
 * beats have run, and the whole thing back to step 1 two seconds after the last one.
 *
 * The beat table below is the prototype's, millisecond for millisecond. Every beat states the screen
 * it starts on, where the finger goes, and what changes 170 ms after the tap lands; the timings the
 * miniature itself owns (the sheet's rise, the tile fills, the halo) live in
 * `ShortcutTutorialIllustrations.kt`.
 *
 * Motion: the rail, markers and titles hang off one `updateTransition` per step (300 ms, rail
 * 420 ms), the open body is `AnimatedVisibility` (`expandVertically` + `fadeIn`, 300 ms), the card
 * resizes with `Modifier.animateContentSize`, and the clock is a single `LaunchedEffect` of
 * `delay`s, cancelled whenever the user taps a step or pauses. At animator scale 0 the clock never
 * starts: step 4 stands open on its last frame with every marker ticked.
 */
@Composable
fun ShortcutTutorialCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val step = remember { mutableIntStateOf(if (reduced) Steps.lastIndex else 0) }
    val completed = remember { mutableIntStateOf(if (reduced) Steps.size else 0) }
    val loop = remember { mutableIntStateOf(0) }
    val playing = remember { mutableStateOf(true) }
    val state = remember { mutableStateOf(if (reduced) FinalFrame else MiniState()) }
    val caption = remember { mutableIntStateOf(Steps[0].beats[0].caption) }
    val tapSeq = remember { mutableIntStateOf(0) }
    val target = remember { mutableStateOf<TutorialTarget?>(null) }
    val hit = remember { mutableStateOf(false) }
    // Which beat of the open step to (re)start from, and whether a pause should still play one beat
    // — a step tapped while paused shows its first beat once; the pause button freezes in place.
    val beatIndex = remember { intArrayOf(0) }
    val holdFresh = remember { booleanArrayOf(false) }

    LaunchedEffect(step.intValue, loop.intValue, playing.value, reduced) {
        if (reduced) return@LaunchedEffect
        if (!playing.value && !holdFresh[0]) return@LaunchedEffect
        holdFresh[0] = false
        val beats = Steps[step.intValue].beats
        var index = beatIndex[0].coerceIn(0, beats.lastIndex)
        while (true) {
            beatIndex[0] = index
            val beat = beats[index]
            coroutineScope {
                state.value = beat.pre
                caption.intValue = beat.caption
                target.value = beat.target
                hit.value = false
                beat.post2?.let { after ->
                    launch {
                        delay(beat.post2At.toLong())
                        state.value = after
                    }
                }
                if (beat.target != null) {
                    launch {
                        delay(beat.tapAt.toLong())
                        tapSeq.intValue++
                        if (beat.hit) hit.value = true
                        beat.post?.let { after ->
                            delay(TapSettleMillis)
                            state.value = after
                        }
                    }
                }
                if (playing.value) delay(beat.ms.toLong())
            }
            if (!playing.value) return@LaunchedEffect
            index++
            if (index > beats.lastIndex) break
        }
        beatIndex[0] = 0
        completed.intValue = step.intValue + 1
        delay(RailFillMillis)
        if (step.intValue < Steps.lastIndex) {
            step.intValue++
        } else {
            delay(RestartHoldMillis)
            completed.intValue = 0
            step.intValue = 0
            loop.intValue++
        }
    }

    val toggleLabel = stringResource(if (playing.value) R.string.scripts_tutorial_pause else R.string.scripts_tutorial_play)
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(LocalGlass.current, rim = colors.primary.copy(alpha = .45f))
            .clip(ConsoleShape)
            // animateContentSize clips to its bounds; it goes before the padding so the current
            // step's scaled-up marker can overshoot the content edge without being cut.
            .animateContentSize(spring(dampingRatio = 0.9f))
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.scripts_tutorial_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.scripts_tutorial_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.scripts_tutorial_progress, step.intValue + 1, Steps.size),
                Modifier
                    .clip(InnerShape)
                    .background(colors.primary.copy(alpha = .14f))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
            )
            if (!reduced) {
                IconButton(
                    onClick = { playing.value = !playing.value },
                    modifier = Modifier.size(MinTouchTarget),
                ) {
                    Icon(
                        if (playing.value) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        toggleLabel,
                        Modifier.size(18.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Steps.forEachIndexed { index, tutorialStep ->
            TutorialStepRow(
                number = index + 1,
                title = stringResource(tutorialStep.title),
                state = when {
                    index < completed.intValue -> SetupStepState.Done
                    index == step.intValue -> SetupStepState.Current
                    else -> SetupStepState.Upcoming
                },
                open = index == step.intValue,
                isLast = index == Steps.lastIndex,
                reduced = reduced,
                onClick = {
                    if (index == step.intValue && !playing.value) {
                        playing.value = true
                    } else {
                        playing.value = false
                        holdFresh[0] = true
                        beatIndex[0] = 0
                        completed.intValue = index
                        if (step.intValue == index) loop.intValue++ else step.intValue = index
                    }
                },
            ) {
                TutorialMiniature(
                    state = state.value,
                    target = target.value,
                    tapSeq = tapSeq.intValue,
                    hit = hit.value,
                    reduced = reduced,
                    modifier = if (reduced) {
                        Modifier
                    } else {
                        Modifier.clickable(role = Role.Button, onClickLabel = toggleLabel) {
                            playing.value = !playing.value
                        }
                    },
                )
                if (!reduced) {
                    AnimatedContent(
                        targetState = caption.intValue,
                        modifier = Modifier.fillMaxWidth().padding(top = 9.dp).heightIn(min = 18.dp),
                        transitionSpec = {
                            (
                                fadeIn(tween(260)) +
                                    slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 3 }
                                ) togetherWith fadeOut(tween(1))
                        },
                        label = "caption",
                    ) { text ->
                        Text(
                            stringResource(text),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** One rail row: the marker, the title, and — while this is the open step — the demo under it. */
@Composable
private fun TutorialStepRow(
    number: Int,
    title: String,
    state: SetupStepState,
    open: Boolean,
    isLast: Boolean,
    reduced: Boolean,
    onClick: () -> Unit,
    body: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val transition = updateTransition(state, label = "tutorialStep$number")
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
    Column(Modifier.fillMaxWidth().stepRail(colors.outline, colors.primary, isLast) { rail.value }) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .clickable(enabled = !reduced, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { stateDescription = stateLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepMarker(number, state, transition, reduced)
            Text(
                title,
                Modifier.weight(1f),
                style = if (state == SetupStepState.Done && !open) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = titleColor,
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(dur(reduced, 300), easing = FastOutSlowInEasing)) +
                fadeIn(tween(dur(reduced, 300))),
            exit = shrinkVertically(tween(dur(reduced, 220), easing = FastOutSlowInEasing)) +
                fadeOut(tween(dur(reduced, 160))),
        ) {
            Column(Modifier.padding(start = 38.dp, top = 10.dp, bottom = 14.dp)) { body() }
        }
    }
}

// ---------------------------------------------------------------- the beat table

/** How long after the tap the screen reacts, and how long the tick and the restart hold. */
private const val TapSettleMillis = 170L
private const val RailFillMillis = 420L
private const val RestartHoldMillis = 2000L

private class Beat(
    @StringRes val caption: Int,
    val ms: Int,
    val pre: MiniState,
    val target: TutorialTarget? = null,
    tapAt: Int? = null,
    val post: MiniState? = null,
    val post2: MiniState? = null,
    val post2At: Int = 0,
    val hit: Boolean = false,
) {
    /** Late enough to have travelled, early enough to leave the result on screen. */
    val tapAt: Int = tapAt ?: maxOf(600, ms - 700)
}

private class TutorialStep(@StringRes val title: Int, val beats: List<Beat>)

private val Steps = listOf(
    TutorialStep(
        R.string.scripts_tutorial_step_new,
        listOf(
            Beat(
                caption = R.string.scripts_tutorial_cap_fab,
                ms = 2200,
                pre = MiniState(),
                target = TutorialTarget.Fab,
            ),
        ),
    ),
    TutorialStep(
        R.string.scripts_tutorial_step_key,
        listOf(
            Beat(
                caption = R.string.scripts_tutorial_cap_keypress,
                ms = 1500,
                pre = MiniState(screen = MiniScreen.Editor),
                target = TutorialTarget.KeyTile,
                post = MiniState(screen = MiniScreen.Editor, keyChosen = true),
            ),
            Beat(
                caption = R.string.scripts_tutorial_cap_press,
                ms = 2200,
                pre = MiniState(screen = MiniScreen.Sheet, keyChosen = true),
                target = TutorialTarget.SideButton,
                tapAt = 1000,
                post = MiniState(
                    screen = MiniScreen.Sheet,
                    keyChosen = true,
                    trigger = TriggerStage.Recorded,
                    pressed = true,
                ),
            ),
            Beat(
                caption = R.string.scripts_tutorial_cap_save,
                ms = 1300,
                pre = MiniState(screen = MiniScreen.Sheet, keyChosen = true, trigger = TriggerStage.Recorded),
                target = TutorialTarget.SaveKey,
            ),
        ),
    ),
    TutorialStep(
        R.string.scripts_tutorial_step_action,
        listOf(
            Beat(
                caption = R.string.scripts_tutorial_cap_add,
                ms = 1500,
                pre = MiniState(
                    screen = MiniScreen.Editor,
                    keyChosen = true,
                    trigger = TriggerStage.Saved,
                    scrolledToDo = true,
                ),
                target = TutorialTarget.AddAction,
            ),
            Beat(
                caption = R.string.scripts_tutorial_cap_pick,
                ms = 1700,
                pre = MiniState(screen = MiniScreen.Catalog, keyChosen = true, trigger = TriggerStage.Saved),
                target = TutorialTarget.TorchRow,
                tapAt = 1000,
                hit = true,
            ),
            Beat(
                caption = R.string.scripts_tutorial_cap_lands,
                ms = 1200,
                pre = MiniState(
                    screen = MiniScreen.Editor,
                    keyChosen = true,
                    trigger = TriggerStage.Saved,
                    action = true,
                    scrolledToDo = true,
                ),
            ),
        ),
    ),
    TutorialStep(
        R.string.scripts_tutorial_step_done,
        listOf(
            Beat(
                caption = R.string.scripts_tutorial_cap_check,
                ms = 1500,
                pre = MiniState(
                    screen = MiniScreen.Editor,
                    keyChosen = true,
                    trigger = TriggerStage.Saved,
                    action = true,
                    scrolledToDo = true,
                    doneReady = true,
                ),
                target = TutorialTarget.Done,
            ),
            Beat(
                caption = R.string.scripts_tutorial_cap_runs,
                ms = 2400,
                pre = MiniState(card = true),
                post2 = MiniState(card = true, lit = true),
                post2At = 700,
            ),
        ),
    ),
)

/** What the last beat ends on — and the only frame drawn when animations are switched off. */
private val FinalFrame = MiniState(card = true, lit = true)
