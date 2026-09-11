package com.example.ui.scripts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActionCategory
import com.example.ui.home.dur
import com.example.ui.theme.categoryTileColors
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The miniature the Shortcuts tutorial draws under its open step: a facsimile of the four screens a
 * first shortcut passes through, every shape a Compose primitive tinted from
 * [MaterialTheme.colorScheme] — the same approach `SetupIllustrations.kt` takes on Home.
 *
 * The clock in `ShortcutTutorialCard.kt` owns the beat; this file only draws the state it is handed
 * and animates its way there. Sizes are the prototype's pixels read as dp, since that prototype drew
 * its phone at 1 px = 1 dp.
 */
internal enum class MiniScreen { List, Editor, Sheet, Catalog }

/** No key recorded yet, one recorded in the sheet, or one saved onto the shortcut. */
internal enum class TriggerStage { None, Recorded, Saved }

internal data class MiniState(
    val screen: MiniScreen = MiniScreen.List,
    val keyChosen: Boolean = false,
    val trigger: TriggerStage = TriggerStage.None,
    val action: Boolean = false,
    val card: Boolean = false,
    val scrolledToDo: Boolean = false,
    val doneReady: Boolean = false,
    val lit: Boolean = false,
    val pressed: Boolean = false,
)

/** The element the finger aims at in the current beat. */
internal enum class TutorialTarget { Fab, KeyTile, SideButton, SaveKey, AddAction, TorchRow, Done }

/**
 * Where each target sits inside the miniature — read from the real layout rather than from
 * hard-coded points, so the finger still lands correctly at any width or font scale.
 */
internal class MiniTargets {
    var root: LayoutCoordinates? = null
    val positions = mutableStateMapOf<TutorialTarget, Offset>()

    fun mark(tag: TutorialTarget): Modifier = Modifier.onGloballyPositioned { coordinates ->
        val r = root ?: return@onGloballyPositioned
        if (!r.isAttached || !coordinates.isAttached) return@onGloballyPositioned
        positions[tag] = r.localPositionOf(
            coordinates,
            Offset(coordinates.size.width / 2f, coordinates.size.height / 2f),
        )
    }
}

private val MiniShape = RoundedCornerShape(14.dp)
private val TileShape = RoundedCornerShape(10.dp)
private val BoxShape = RoundedCornerShape(12.dp)
private val CardShape = RoundedCornerShape(14.dp)
private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** The prototype's type scale, finer than the app's own — this is a drawing of a screen. */
@Composable
private fun mini(size: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
    MaterialTheme.typography.labelSmall.copy(
        fontSize = size.sp,
        lineHeight = (size * 1.3f).sp,
        fontWeight = weight,
        letterSpacing = 0.sp,
    )

@Composable
internal fun TutorialMiniature(
    state: MiniState,
    target: TutorialTarget?,
    tapSeq: Int,
    hit: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val targets = remember { MiniTargets() }
    val slide = with(LocalDensity.current) { 6.dp.roundToPx() }
    Box(
        modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(MiniShape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outline.copy(alpha = .55f), MiniShape)
            .onGloballyPositioned { targets.root = it },
    ) {
        // Keyed on the screen, so the outgoing one keeps the state it was drawn with while it fades.
        AnimatedContent(
            targetState = state,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it.screen },
            transitionSpec = {
                (
                    fadeIn(tween(dur(reduced, 260))) +
                        slideInVertically(tween(dur(reduced, 260), easing = FastOutSlowInEasing)) { slide }
                    ) togetherWith fadeOut(tween(dur(reduced, 260)))
            },
            label = "miniScreen",
        ) { shown ->
            when (shown.screen) {
                MiniScreen.List -> ListScreen(shown, targets, reduced)
                MiniScreen.Editor -> EditorScreen(shown, targets, reduced)
                MiniScreen.Sheet -> SheetScreen(shown, targets, reduced)
                MiniScreen.Catalog -> CatalogScreen(targets, hit, reduced)
            }
        }
        if (!reduced) Finger(target, targets, tapSeq)
    }
}

// ---------------------------------------------------------------- screens

/** The page the user is on: empty at first, with the finished shortcut on it at the end. */
@Composable
private fun ListScreen(state: MiniState, targets: MiniTargets, reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(12.dp)) {
        Column {
            Text(
                stringResource(R.string.scripts_tutorial_mini_page),
                style = mini(10.5f, FontWeight.Medium).copy(letterSpacing = 0.8.sp),
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(9.dp))
            val cardVisible = remember { MutableTransitionState(false) }
            cardVisible.targetState = state.card
            AnimatedVisibility(
                visibleState = cardVisible,
                enter = fadeIn(tween(dur(reduced, 340))) +
                    slideInVertically(tween(dur(reduced, 340), easing = FastOutSlowInEasing)) { -it / 3 } +
                    scaleIn(tween(dur(reduced, 340)), initialScale = .97f),
            ) {
                ShortcutCardRow(state.lit, reduced)
            }
            AnimatedVisibility(visible = !state.card, exit = fadeOut(tween(dur(reduced, 200)))) {
                Text(
                    stringResource(R.string.scripts_empty_title),
                    style = mini(12.5f),
                    color = colors.onSurfaceVariant,
                )
            }
        }
        MiniBottomBar(targets, Modifier.align(Alignment.BottomCenter))
    }
}

/** The app's bottom bar in miniature, on the Shortcuts tab; its + is where a new shortcut starts. */
@Composable
private fun MiniBottomBar(targets: MiniTargets, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.outline.copy(alpha = .55f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.Home, null, Modifier.size(15.dp), tint = colors.onSurfaceVariant)
        Box(
            Modifier
                .then(targets.mark(TutorialTarget.Fab))
                .clip(CircleShape)
                .background(colors.primary)
                .padding(5.dp),
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(14.dp), tint = colors.onPrimary)
        }
        Icon(Icons.Filled.Bolt, null, Modifier.size(15.dp), tint = colors.primary)
    }
}

/** The finished shortcut, and the torch tile lighting up as it runs. */
@Composable
private fun ShortcutCardRow(lit: Boolean, reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surface)
            .border(1.dp, colors.outline.copy(alpha = .55f), CardShape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiniTile(ActionCategory.FLASHLIGHT, "toggle_flashlight", 30.dp, lit, reduced)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.default_shortcut_name, 1),
                style = mini(13.5f, FontWeight.Medium),
                color = colors.onSurface,
            )
            Text(
                stringResource(R.string.key_volume_up) + " · " + stringResource(R.string.press_single_title),
                style = mini(11.5f),
                color = colors.onSurfaceVariant,
            )
        }
        Box(
            Modifier.size(width = 32.dp, height = 19.dp).clip(CircleShape).background(colors.primary),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(Modifier.padding(end = 2.5.dp).size(14.dp).background(colors.onPrimary, CircleShape))
        }
    }
}

/** The editor: the When tiles, the saved trigger, Only if, and Do. */
@Composable
private fun EditorScreen(state: MiniState, targets: MiniTargets, reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    // The prototype's editor never leaves the DOM, so its scroll, its trigger row and its Done
    // button animate even on the beat that re-enters the screen. Here the screen is rebuilt, so
    // state that is "already true" on entry is handed over one frame late to get the same motion.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    val scroll by animateDpAsState(
        if (state.scrolledToDo && settled) (-116).dp else 0.dp,
        tween(dur(reduced, 420), easing = FastOutSlowInEasing),
        label = "editorScroll",
    )
    val doneColor by animateColorAsState(
        if (state.doneReady && settled) colors.primary else colors.onSurfaceVariant,
        tween(dur(reduced, 300)),
        label = "doneColor",
    )
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(colors.outline.copy(alpha = .35f)))
            Text(
                stringResource(R.string.editor_shortcut_name),
                Modifier.weight(1f),
                style = mini(13.5f, FontWeight.Medium),
                color = colors.onSurface,
            )
            Row(
                Modifier.then(targets.mark(TutorialTarget.Done)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = doneColor)
                Text(stringResource(R.string.common_done), style = mini(13f, FontWeight.Medium), color = doneColor)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outline.copy(alpha = .55f)))
        Box(Modifier.fillMaxSize().clipToBounds()) {
            // The chain is taller than the viewport — that is the point of the scroll — so it has to
            // be measured unbounded, or Compose drops everything past the bottom edge.
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .offset { IntOffset(0, scroll.roundToPx()) }
                    .padding(top = 9.dp),
            ) {
                SectionLabel(stringResource(R.string.editor_chain_when))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhenTile(
                        stringResource(R.string.trigger_kind_key),
                        selected = state.keyChosen,
                        reduced = reduced,
                        modifier = Modifier.weight(1f).then(targets.mark(TutorialTarget.KeyTile)),
                    ) { Icon(Icons.Outlined.Keyboard, null, Modifier.size(15.dp), tint = it) }
                    WhenTile(
                        stringResource(R.string.trigger_kind_automation),
                        selected = false,
                        reduced = reduced,
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(15.dp), tint = it) }
                }
                val triggerVisible = remember { MutableTransitionState(false) }
                triggerVisible.targetState = state.trigger == TriggerStage.Saved && settled
                AnimatedVisibility(
                    visibleState = triggerVisible,
                    enter = expandVertically(tween(dur(reduced, 300), easing = FastOutSlowInEasing)) +
                        fadeIn(tween(dur(reduced, 300))),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(BoxShape)
                            .background(colors.surface)
                            .border(1.dp, colors.outline.copy(alpha = .55f), BoxShape)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.key_volume_up),
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.primary.copy(alpha = .14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            style = mini(11f, FontWeight.Medium),
                            color = colors.primary,
                        )
                        Text(
                            stringResource(R.string.press_single_title),
                            style = mini(12.5f),
                            color = colors.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel(stringResource(R.string.editor_chain_only_if))
                EditorBox {
                    Text(
                        stringResource(R.string.editor_always_runs),
                        style = mini(12.5f),
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    AddRow(stringResource(R.string.editor_add_constraint))
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel(stringResource(R.string.editor_chain_do))
                EditorBox(Modifier.animateContentSize(tween(dur(reduced, 300), easing = FastOutSlowInEasing))) {
                    if (state.action) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            MiniTile(ActionCategory.FLASHLIGHT, "toggle_flashlight", 24.dp, false, reduced)
                            Text(
                                stringResource(R.string.action_toggle_flashlight),
                                style = mini(12.5f),
                                color = colors.onSurface,
                            )
                        }
                    } else {
                        AddRow(
                            stringResource(R.string.editor_add_action),
                            Modifier.fillMaxWidth().then(targets.mark(TutorialTarget.AddAction)),
                            center = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(bottom = 6.dp),
        style = mini(11f, FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EditorBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(BoxShape)
            .background(colors.surface)
            .border(1.dp, colors.outline.copy(alpha = .55f), BoxShape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        content = content,
    )
}

@Composable
private fun AddRow(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(12.dp), tint = colors.primary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = mini(12.5f), color = colors.primary)
    }
}

@Composable
private fun WhenTile(
    label: String,
    selected: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        if (selected) colors.primary.copy(alpha = .14f) else Color.Transparent,
        tween(dur(reduced, 260)),
        label = "tileFill",
    )
    val edge by animateColorAsState(
        if (selected) colors.primary else colors.outline.copy(alpha = .55f),
        tween(dur(reduced, 260)),
        label = "tileEdge",
    )
    val ink by animateColorAsState(
        if (selected) colors.primary else colors.onSurfaceVariant,
        tween(dur(reduced, 260)),
        label = "tileInk",
    )
    Row(
        modifier
            .height(38.dp)
            .clip(BoxShape)
            .background(fill)
            .border(1.dp, edge, BoxShape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon(ink)
        Spacer(Modifier.width(6.dp))
        Text(label, style = mini(12.5f), color = ink)
    }
}

/** The key picker, and the one moment the drawing leaves the app to show a real side button. */
@Composable
private fun SheetScreen(state: MiniState, targets: MiniTargets, reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val rise by animateFloatAsState(
        if (shown) 0f else 1f,
        tween(dur(reduced, 340), easing = CubicBezierEasing(.2f, .9f, .3f, 1f)),
        label = "sheetRise",
    )
    val dim by animateFloatAsState(if (shown) 1f else 0f, tween(dur(reduced, 300)), label = "sheetDim")
    val recorded = state.trigger != TriggerStage.None
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(colors.scrim.copy(alpha = .38f * dim)))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = rise * size.height }
                .clip(SheetShape)
                .background(colors.surface)
                .border(1.dp, colors.outline.copy(alpha = .55f), SheetShape)
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .background(colors.onSurfaceVariant.copy(alpha = .35f), CircleShape),
            )
            Spacer(Modifier.height(9.dp))
            Text(
                stringResource(R.string.trigger_picker_title),
                style = mini(14f, FontWeight.Bold),
                color = colors.onSurface,
            )
            Text(
                stringResource(R.string.trigger_picker_subtitle),
                Modifier.padding(top = 2.dp, bottom = 10.dp),
                style = mini(11.5f),
                color = colors.onSurfaceVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(colors.surfaceVariant)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PressTypeChip(stringResource(R.string.press_single_title), true, Modifier.weight(1f))
                PressTypeChip(stringResource(R.string.press_double_title), false, Modifier.weight(1f))
                PressTypeChip(stringResource(R.string.press_long_title), false, Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(if (recorded) R.string.key_volume_up else R.string.trigger_picker_no_key),
                    Modifier
                        .clip(CircleShape)
                        .background(if (recorded) colors.primary.copy(alpha = .14f) else colors.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    style = mini(12.5f, FontWeight.Medium),
                    color = if (recorded) colors.primary else colors.onSurfaceVariant,
                )
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RecordDot(recorded, reduced)
                    Text(
                        stringResource(
                            if (recorded) R.string.scripts_tutorial_key_recorded else R.string.scripts_tutorial_listening,
                        ),
                        style = mini(12f),
                        color = if (recorded) colors.onSurfaceVariant else colors.primary,
                    )
                }
                SideButtonPhone(state.pressed, recorded, reduced, targets.mark(TutorialTarget.SideButton))
            }
            SaveKeyButton(recorded, reduced, targets.mark(TutorialTarget.SaveKey))
        }
    }
}

@Composable
private fun PressTypeChip(label: String, selected: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        modifier
            .clip(CircleShape)
            .background(if (selected) colors.primary else Color.Transparent)
            .padding(vertical = 6.dp),
        style = mini(11.5f, if (selected) FontWeight.Medium else FontWeight.Normal),
        color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** The recording indicator: the only infinite loop in the drawing, and it stops on the key landing. */
@Composable
private fun RecordDot(recorded: Boolean, reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    val ping = if (recorded || reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "rec").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
            label = "ping",
        )
    }
    val dot = if (recorded) colors.onSurfaceVariant.copy(alpha = .35f) else colors.error
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        if (ping != null) {
            Box(
                Modifier.size(16.dp).drawBehind {
                    val t = (ping.value / .7f).coerceIn(0f, 1f)
                    drawCircle(
                        dot.copy(alpha = .7f * (1f - t)),
                        radius = size.minDimension / 2f * (1f + 1.1f * t),
                        style = Stroke(2.dp.toPx()),
                    )
                },
            )
        }
        Box(Modifier.size(16.dp).background(dot, CircleShape))
    }
}

/** The phone in the user's hand, and its side button sinking under the press. */
@Composable
private fun SideButtonPhone(pressed: Boolean, recorded: Boolean, reduced: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val down = pressed || recorded
    val shift by animateDpAsState(if (down) 2.dp else 0.dp, tween(dur(reduced, 120)), label = "sideShift")
    val tint by animateColorAsState(
        if (down) colors.primary else colors.onSurfaceVariant,
        tween(dur(reduced, 200)),
        label = "sideTint",
    )
    val ping = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed && !reduced) {
            ping.snapTo(0f)
            ping.animateTo(1f, tween(500, easing = CubicBezierEasing(0f, 0f, .2f, 1f)))
        }
    }
    Box(modifier.size(width = 44.dp, height = 56.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(width = 32.dp, height = 56.dp)
                .border(1.5.dp, colors.onSurfaceVariant.copy(alpha = .85f), RoundedCornerShape(9.dp)),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset((32.dp - shift).roundToPx(), 11.dp.roundToPx()) }
                .size(width = 3.dp, height = 15.dp)
                .background(tint, RoundedCornerShape(2.dp)),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(36.dp.roundToPx(), 15.dp.roundToPx()) }
                .size(8.dp)
                .drawBehind {
                    val t = ping.value
                    if (t >= 1f) return@drawBehind
                    drawCircle(
                        colors.primary.copy(alpha = .7f * (1f - t)),
                        radius = size.minDimension / 2f * (.6f + 1.6f * t),
                        style = Stroke(1.5.dp.toPx()),
                    )
                },
        )
    }
}

@Composable
private fun SaveKeyButton(recorded: Boolean, reduced: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        if (recorded) colors.primary else colors.surfaceVariant,
        tween(dur(reduced, 300)),
        label = "saveFill",
    )
    val ink by animateColorAsState(
        if (recorded) colors.onPrimary else colors.onSurfaceVariant,
        tween(dur(reduced, 300)),
        label = "saveInk",
    )
    Text(
        stringResource(R.string.trigger_picker_save_combo),
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(BoxShape)
            .background(fill)
            .padding(vertical = 10.dp),
        style = mini(13f, FontWeight.Medium),
        color = ink,
        textAlign = TextAlign.Center,
    )
}

/** The action catalog, with the row the finger is about to add. */
@Composable
private fun CatalogScreen(targets: MiniTargets, hit: Boolean, reduced: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(colors.outline.copy(alpha = .35f)))
            Text(
                stringResource(R.string.editor_add_action),
                style = mini(13.5f, FontWeight.Bold),
                color = colors.onSurface,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(colors.outline.copy(alpha = .28f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Search, null, Modifier.size(13.dp), tint = colors.onSurfaceVariant)
            Text(stringResource(R.string.action_picker_search), style = mini(12f), color = colors.onSurfaceVariant)
        }
        CatalogGroup(stringResource(R.string.action_category_input))
        CatalogRow(ActionCategory.INPUT, "input_key_code", stringResource(R.string.action_input_key_code), false, reduced)
        CatalogGroup(stringResource(R.string.action_category_flashlight))
        CatalogRow(
            ActionCategory.FLASHLIGHT,
            "toggle_flashlight",
            stringResource(R.string.action_toggle_flashlight),
            hit,
            reduced,
            Modifier.then(targets.mark(TutorialTarget.TorchRow)),
        )
        CatalogGroup(stringResource(R.string.action_category_connectivity))
        CatalogRow(
            ActionCategory.CONNECTIVITY,
            "toggle_bluetooth",
            stringResource(R.string.action_toggle_bluetooth),
            false,
            reduced,
        )
    }
}

@Composable
private fun CatalogGroup(label: String) {
    Text(
        label,
        Modifier.padding(top = 6.dp, bottom = 4.dp),
        style = mini(10.5f, FontWeight.Bold).copy(letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CatalogRow(
    category: ActionCategory,
    type: String,
    label: String,
    hit: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val ring by animateFloatAsState(if (hit) 1f else 0f, tween(dur(reduced, 250)), label = "rowRing")
    val plusFill by animateColorAsState(
        if (hit) colors.primary else colors.primary.copy(alpha = .14f),
        tween(dur(reduced, 250)),
        label = "plusFill",
    )
    val plusInk by animateColorAsState(
        if (hit) colors.onPrimary else colors.primary,
        tween(dur(reduced, 250)),
        label = "plusInk",
    )
    Row(
        modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        MiniTile(category, type, 30.dp, false, reduced, ring)
        Text(label, Modifier.weight(1f), style = mini(12.5f), color = colors.onSurface)
        Box(Modifier.size(22.dp).background(plusFill, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null, Modifier.size(12.dp), tint = plusInk)
        }
    }
}

/**
 * A category tile. [lit] takes the solid swatch `IconTile` uses when selected, but animated, and
 * [ring] draws the halo the catalog and the finished card use to say "this one just fired".
 */
@Composable
private fun MiniTile(
    category: ActionCategory,
    type: String,
    size: Dp,
    lit: Boolean,
    reduced: Boolean,
    ring: Float = 0f,
) {
    val (restBackground, restGlyph) = categoryTileColors(category)
    val (litBackground, litGlyph) = categoryTileColors(category, selected = true)
    val background by animateColorAsState(
        if (lit) litBackground else restBackground,
        tween(dur(reduced, 300)),
        label = "tileBackground",
    )
    val glyph by animateColorAsState(if (lit) litGlyph else restGlyph, tween(dur(reduced, 300)), label = "tileGlyph")
    val halo by animateFloatAsState(if (lit) 1f else 0f, tween(dur(reduced, 300)), label = "tileHalo")
    Box(
        Modifier
            .size(size)
            .drawBehind {
                val spread = (5.dp.toPx() * halo) + (3.dp.toPx() * ring)
                val alpha = .22f * halo + .26f * ring
                if (alpha > 0f) {
                    drawRoundRect(
                        litBackground.copy(alpha = alpha),
                        topLeft = Offset(-spread, -spread),
                        size = Size(this.size.width + 2 * spread, this.size.height + 2 * spread),
                        cornerRadius = CornerRadius(10.dp.toPx() + spread),
                    )
                }
            }
            .clip(TileShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(actionIconFor(type), null, Modifier.size(size * 0.55f), tint = glyph)
    }
}

// ---------------------------------------------------------------- the finger

/** One translucent fingertip: 500 ms to the target, a 340 ms squash on the tap, and a ripple. */
@Composable
private fun BoxScope.Finger(target: TutorialTarget?, targets: MiniTargets, tapSeq: Int) {
    val colors = MaterialTheme.colorScheme
    val fallback = with(LocalDensity.current) { Offset(60.dp.toPx(), 60.dp.toPx()) }
    val destination = target?.let { targets.positions[it] } ?: fallback
    val travel = updateTransition(destination, label = "finger")
    val position by travel.animateOffset(
        transitionSpec = { tween(500, easing = FastOutSlowInEasing) },
        label = "travel",
    ) { it }
    val visible by animateFloatAsState(if (target == null) 0f else 1f, tween(200), label = "fingerAlpha")
    val squash = remember { Animatable(1f) }
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(tapSeq) {
        if (tapSeq == 0) return@LaunchedEffect
        launch {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(500, easing = CubicBezierEasing(0f, 0f, .2f, 1f)))
        }
        squash.animateTo(.78f, tween(136, easing = FastOutSlowInEasing))
        squash.animateTo(1f, tween(204, easing = CubicBezierEasing(.34f, 1.4f, .64f, 1f)))
    }
    Box(
        Modifier
            .size(26.dp)
            .offset { IntOffset((position.x - 13.dp.toPx()).roundToInt(), (position.y - 13.dp.toPx()).roundToInt()) }
            .graphicsLayer {
                val t = ripple.value
                alpha = .65f * (1f - t)
                scaleX = .5f + 1.9f * t
                scaleY = scaleX
            }
            .border(2.dp, colors.primary, CircleShape),
    )
    Box(
        Modifier
            .size(26.dp)
            .offset { IntOffset((position.x - 13.dp.toPx()).roundToInt(), (position.y - 13.dp.toPx()).roundToInt()) }
            .graphicsLayer {
                alpha = visible
                scaleX = squash.value
                scaleY = squash.value
            }
            .background(colors.onSurface.copy(alpha = .20f), CircleShape)
            .border(1.5.dp, colors.onSurface.copy(alpha = .32f), CircleShape),
    )
}
