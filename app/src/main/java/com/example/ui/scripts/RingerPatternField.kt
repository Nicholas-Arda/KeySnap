package com.example.ui.scripts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.bridge.BuzzPattern
import com.example.bridge.RingerBuzz
import com.example.data.EditorField
import com.example.data.RINGER_MODE_LABELS
import com.example.service.HardwareKeyTriggerCoordinator
import com.example.ui.components.ConsoleSwitch
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.theme.SectionLabel
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Piece {
    val ms: Long
    data class Shortcut(override val ms: Long) : Piece
    data class ShortcutGap(override val ms: Long) : Piece
    data class Lead(override val ms: Long) : Piece
    data class Pulse(val index: Int, override val ms: Long) : Piece
    data class Gap(val index: Int, override val ms: Long) : Piece
}

private val Piece.editable: Boolean get() = this !is Piece.Shortcut && this !is Piece.ShortcutGap

/** Pieces narrower than this many ms still draw at this width, so a 20 ms buzz stays tappable. */
private const val MIN_DRAWN_MS = 40L

/** The track always spans at least this long, so a lone short buzz looks short. */
private const val MIN_TRACK_MS = 800L

private fun trackMs(pieces: List<Piece>): Long = maxOf(MIN_TRACK_MS, pieces.sumOf { maxOf(it.ms, MIN_DRAWN_MS) })

/** A flat "pulse, gap, pulse, …" list as track pieces: Pulse i sits at 2i, Gap i at 2i + 1. */
private fun patternPieces(parts: List<Long>): List<Piece> = parts.mapIndexed { i, ms -> if (i % 2 == 0) Piece.Pulse(i / 2, ms) else Piece.Gap(i / 2, ms) }

private fun shortcutPieces(parts: List<Long>): List<Piece> = parts.mapIndexed { i, ms -> if (i % 2 == 0) Piece.Shortcut(ms) else Piece.ShortcutGap(ms) }

/** [parts] with [piece] set to [ms], clamped; unchanged when the piece is not in it. */
private fun withMs(parts: List<Long>, piece: Piece, ms: Long): List<Long> {
    val at = when (piece) {
        is Piece.Pulse -> piece.index * 2
        is Piece.Gap -> piece.index * 2 + 1
        else -> return parts
    }
    if (at >= parts.size) return parts
    val (min, max) = piece.range()
    return parts.toMutableList().also { it[at] = ms.coerceIn(min, max) }
}

/** One more buzz like the last one, after a pause like the last one. */
private fun withBuzzAdded(parts: List<Long>): List<Long> = when {
    parts.isEmpty() -> listOf(BuzzPattern.PULSE_DEFAULT_MS)
    parts.size >= BuzzPattern.MAX_BUZZES * 2 - 1 -> parts
    else -> parts + (parts.getOrNull(parts.size - 2) ?: BuzzPattern.GAP_DEFAULT_MS) + parts.last()
}

private fun withBuzzRemoved(parts: List<Long>): List<Long> = parts.dropLast(if (parts.size > 1) 2 else 1)

private fun buzzCount(parts: List<Long>): Int = (parts.size + 1) / 2

/**
 * cycle_ringer_mode's editor: which modes are in the cycle, and each mode's own buzz — every buzz
 * and every pause with its own length, 0 to 5 buzzes, dragged wider or narrower on the track or
 * stepped. [shortcutPattern] is the shortcut's own vibration, shown grey in front and switchable here.
 */
@Composable
internal fun RingerPatternField(
    values: MutableMap<String, String>,
    shortcutBuzz: Boolean,
    shortcutPattern: List<Long>,
    onShortcutBuzz: ((Boolean) -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val modes = values[EditorField.RingerPattern.MODES]
    val inCycle = RINGER_MODE_LABELS.map { RingerBuzz.inCycle(it.first, modes) }
    fun setInCycle(index: Int) {
        val next = inCycle.toMutableList().also { it[index] = !it[index] }
        if (next.count { it } < 2) return // A cycle of one mode is not a cycle.
        values[EditorField.RingerPattern.MODES] = RINGER_MODE_LABELS.filterIndexed { i, _ -> next[i] }.joinToString(",") { RingerBuzz.name(it.first) }
    }

    Column {
        Text(stringResource(R.string.param_ringer_cycle), style = SectionLabel, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RINGER_MODE_LABELS.forEachIndexed { index, (mode, label) ->
                ModeChip(
                    text = "${index + 1}  ${stringResource(label)}",
                    selected = inCycle[index],
                    check = true,
                    onClick = { setInCycle(index) },
                    modifier = Modifier.weight(1f).testTag("ringer_cycle_${RingerBuzz.name(mode)}"),
                )
            }
        }
        Text(stringResource(R.string.ringer_cycle_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }

    var shown by remember { mutableStateOf(RINGER_MODE_LABELS.indexOfFirst { RingerBuzz.inCycle(it.first, modes) }.coerceAtLeast(0)) }
    val mode = RINGER_MODE_LABELS[shown].first
    val modeName = stringResource(RINGER_MODE_LABELS[shown].second)
    val pattern = BuzzPattern.parse(values[RingerBuzz.key(mode)])
    val lead = RingerBuzz.lead(values)
    val pieces = buildList {
        if (shortcutBuzz) addAll(shortcutPieces(shortcutPattern))
        if (shortcutBuzz && pattern.isNotEmpty()) add(Piece.Lead(lead))
        addAll(patternPieces(pattern))
    }
    var pick by remember { mutableStateOf<Piece>(Piece.Pulse(0, 0)) }
    val picked = pieces.firstOrNull { it.editable && it::class == pick::class && it.index() == pick.index() }
        ?: pieces.firstOrNull { it is Piece.Pulse }

    // Handlers read the mode and its pattern when they run, not when they were composed: a drag or
    // step started under one mode must never write another mode's (shorter) list.
    val modeNow = rememberUpdatedState(mode)
    fun parts(): List<Long> = BuzzPattern.parse(values[RingerBuzz.key(modeNow.value)])
    fun store(next: List<Long>) {
        values[RingerBuzz.key(modeNow.value)] = BuzzPattern.join(next)
    }
    fun setMs(piece: Piece, ms: Long) {
        if (piece is Piece.Lead) values[RingerBuzz.LEAD_KEY] = ms.coerceIn(RingerBuzz.LEAD_MIN_MS, RingerBuzz.LEAD_MAX_MS).toString()
        else store(withMs(parts(), piece, ms))
    }

    val scope = rememberCoroutineScope()
    var feelJob by remember { mutableStateOf<Job?>(null) }
    PatternCard(
        tag = "ringer_pattern",
        onFeel = {
            feelJob?.cancel()
            val waveform = RingerBuzz.waveform(values, mode)
            val pause = if (shortcutBuzz && waveform.isNotEmpty()) BuzzPattern.total(shortcutPattern) + lead else 0L
            feelJob = scope.launch {
                if (shortcutBuzz) HardwareKeyTriggerCoordinator.vibratePattern(context, shortcutPattern)
                delay(pause)
                if (waveform.isNotEmpty()) HardwareKeyTriggerCoordinator.vibratePhone(context, waveform)
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RINGER_MODE_LABELS.forEachIndexed { index, (m, label) ->
                ModeChip(
                    text = stringResource(label),
                    selected = index == shown,
                    check = false,
                    struck = !inCycle[index],
                    onClick = { shown = index; pick = Piece.Pulse(0, 0) },
                    modifier = Modifier.weight(1f).testTag("ringer_preview_${RingerBuzz.name(m)}"),
                )
            }
        }
        if (pieces.isEmpty()) {
            Text(stringResource(R.string.ringer_no_buzz, modeName), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            Track(pieces, picked, onPick = { pick = it }, onDrag = { piece, ms -> setMs(piece, ms) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (shortcutBuzz) LegendDot(colors.outline, stringResource(R.string.ringer_legend_shortcut))
            LegendDot(colors.primary, stringResource(R.string.ringer_legend_mode))
        }
        Stepper(
            label = stringResource(R.string.ringer_buzz_count, modeName),
            value = "${buzzCount(pattern)}×",
            canLess = pattern.isNotEmpty(),
            canMore = buzzCount(pattern) < BuzzPattern.MAX_BUZZES,
            onLess = { store(withBuzzRemoved(parts())) },
            onMore = {
                val next = withBuzzAdded(parts())
                store(next)
                pick = Piece.Pulse(buzzCount(next) - 1, 0)
            },
            tag = "ringer_count",
        )
        picked?.let { PieceStepper(it) { piece, ms -> setMs(piece, ms) } }
        if (onShortcutBuzz != null) {
            ConsoleSwitch(stringResource(R.string.ringer_shortcut_buzz), shortcutBuzz, onShortcutBuzz)
        }
        ResetLink("ringer_reset") {
            RINGER_MODE_LABELS.forEach { (m, _) -> values[RingerBuzz.key(m)] = RingerBuzz.defaultPattern(m) }
            values[RingerBuzz.LEAD_KEY] = RingerBuzz.LEAD_DEFAULT_MS.toString()
            pick = Piece.Pulse(0, 0)
        }
    }
}

/**
 * A shortcut's own buzz: 1 to 5 buzzes, each buzz and pause with its own length. [isCustom] false
 * means [pattern] is the global default; Reset goes back to it ([onChange] with null).
 */
@Composable
internal fun ShortcutBuzzEditor(pattern: List<Long>, isCustom: Boolean, onChange: (List<Long>?) -> Unit) {
    val context = LocalContext.current
    val patternNow = rememberUpdatedState(pattern)
    val pieces = patternPieces(pattern)
    var pick by remember { mutableStateOf<Piece>(Piece.Pulse(0, 0)) }
    val picked = pieces.firstOrNull { it::class == pick::class && it.index() == pick.index() } ?: pieces.first()
    fun setMs(piece: Piece, ms: Long) = onChange(withMs(patternNow.value, piece, ms))
    PatternCard(tag = "shortcut_buzz_pattern", onFeel = { HardwareKeyTriggerCoordinator.vibratePattern(context, pattern) }) {
        Track(pieces, picked, onPick = { pick = it }, onDrag = { piece, ms -> setMs(piece, ms) })
        Stepper(
            label = stringResource(R.string.shortcut_buzz_count),
            value = "${buzzCount(pattern)}×",
            canLess = buzzCount(pattern) > 1,
            canMore = buzzCount(pattern) < BuzzPattern.MAX_BUZZES,
            onLess = { onChange(withBuzzRemoved(patternNow.value).ifEmpty { patternNow.value }) },
            onMore = {
                val next = withBuzzAdded(patternNow.value)
                onChange(next)
                pick = Piece.Pulse(buzzCount(next) - 1, 0)
            },
            tag = "shortcut_buzz_count",
        )
        PieceStepper(picked) { piece, ms -> setMs(piece, ms) }
        if (isCustom) ResetLink("shortcut_buzz_reset") { onChange(null); pick = Piece.Pulse(0, 0) }
    }
}

@Composable
private fun PatternCard(tag: String, onFeel: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .border(1.dp, colors.outline.copy(alpha = .6f), InnerShape)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ringer_pattern), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            TextButton(onClick = onFeel, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("${tag}_feel")) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ringer_feel), maxLines = 1, softWrap = false)
            }
        }
        content()
    }
}

@Composable
private fun PieceStepper(piece: Piece, setMs: (Piece, Long) -> Unit) {
    val (min, max) = piece.range()
    Stepper(
        label = piece.label(),
        value = stringResource(R.string.ringer_ms, piece.ms.toInt()),
        canLess = piece.ms > min,
        canMore = piece.ms < max,
        onLess = { setMs(piece, piece.ms - 10) },
        onMore = { setMs(piece, piece.ms + 10) },
        tag = "ringer_piece",
    )
}

@Composable
private fun ResetLink(tag: String, onClick: () -> Unit) {
    Text(
        stringResource(R.string.ringer_reset),
        Modifier
            .heightIn(min = MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag(tag),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun Piece.index(): Int = when (this) {
    is Piece.Pulse -> index
    is Piece.Gap -> index
    else -> -1
}

private fun Piece.range(): Pair<Long, Long> = when (this) {
    is Piece.Pulse -> BuzzPattern.PULSE_MIN_MS to BuzzPattern.PULSE_MAX_MS
    is Piece.Gap -> BuzzPattern.GAP_MIN_MS to BuzzPattern.GAP_MAX_MS
    is Piece.Lead -> RingerBuzz.LEAD_MIN_MS to RingerBuzz.LEAD_MAX_MS
    is Piece.Shortcut, is Piece.ShortcutGap -> ms to ms
}

@Composable
private fun Piece.label(): String = when (this) {
    is Piece.Pulse -> stringResource(R.string.ringer_piece_buzz, index + 1)
    is Piece.Gap -> stringResource(R.string.ringer_piece_pause, index + 1)
    is Piece.Lead -> stringResource(R.string.ringer_piece_lead)
    is Piece.Shortcut, is Piece.ShortcutGap -> stringResource(R.string.ringer_legend_shortcut)
}

@Composable
private fun Track(pieces: List<Piece>, picked: Piece?, onPick: (Piece) -> Unit, onDrag: (Piece, Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val piecesState = rememberUpdatedState(pieces)
    val onDragState = rememberUpdatedState(onDrag)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val trackPx = with(density) { maxWidth.toPx() }
        val minPx = with(density) { 14.dp.toPx() }
        val total = trackMs(pieces).toFloat()
        val widths = pieces.map { maxOf(minPx, maxOf(it.ms, MIN_DRAWN_MS) / total * trackPx) }
        val scale = minOf(1f, trackPx / widths.sum()) // Minimum widths can overflow the track; shrink all to fit.
        Column {
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                pieces.forEachIndexed { i, piece ->
                    val selected = piece == picked
                    val description = "${piece.label()}, ${stringResource(R.string.ringer_ms, piece.ms.toInt())}"
                    val drag = if (!piece.editable) {
                        Modifier
                    } else {
                        // Freezes px-per-ms at the start so the piece follows the finger, not its own growth.
                        Modifier.pointerInput(piece::class, piece.index()) {
                            var startMs = 0L
                            var pxPerMs = 1f
                            var moved = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    val now = piecesState.value.first { it::class == piece::class && it.index() == piece.index() }
                                    startMs = now.ms
                                    pxPerMs = trackPx / trackMs(piecesState.value)
                                    moved = 0f
                                    onPick(now)
                                },
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    moved += amount
                                    val ms = ((startMs + moved / pxPerMs) / 10).toLong() * 10
                                    onDragState.value(piece, ms)
                                },
                            )
                        }
                    }
                    Box(
                        Modifier
                            .width(with(density) { (widths[i] * scale).toDp() })
                            .fillMaxHeight()
                            .semantics(mergeDescendants = true) { contentDescription = description }
                            .then(if (piece.editable) Modifier.clickable { onPick(piece) } else Modifier)
                            .then(drag),
                    ) {
                        when (piece) {
                            is Piece.Shortcut, is Piece.Pulse -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(vertical = if (selected) 4.dp else 10.dp, horizontal = 1.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (piece is Piece.Shortcut) colors.outline else colors.primary)
                                    .then(if (selected) Modifier.border(2.dp, colors.onSurface, RoundedCornerShape(6.dp)) else Modifier),
                            )
                            is Piece.Lead, is Piece.Gap, is Piece.ShortcutGap -> Canvas(Modifier.fillMaxSize()) {
                                val radius = 1.6.dp.toPx()
                                var x = radius
                                while (x < size.width) {
                                    drawCircle(if (selected) colors.primary else colors.outline, radius, Offset(x, size.height / 2f))
                                    x += radius * 5
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                pieces.forEachIndexed { i, piece ->
                    Text(
                        piece.ms.toString(),
                        Modifier.width(with(density) { (widths[i] * scale).toDp() }),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, check: Boolean, onClick: () -> Unit, modifier: Modifier, struck: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
            )
        },
        leadingIcon = if (check && selected) ({ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }) else null,
        border = null,
        modifier = modifier.alpha(if (struck && !selected) .5f else 1f),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
            selectedLeadingIconColor = colors.onPrimary,
            containerColor = colors.surfaceVariant,
            labelColor = colors.onSurfaceVariant,
        ),
    )
}

@Composable
private fun Stepper(label: String, value: String, canLess: Boolean, canMore: Boolean, onLess: () -> Unit, onMore: () -> Unit, tag: String) {
    val colors = MaterialTheme.colorScheme
    val less = stringResource(R.string.ringer_less)
    val more = stringResource(R.string.ringer_more)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        IconButton(onClick = onLess, enabled = canLess, modifier = Modifier.size(MinTouchTarget).testTag("${tag}_less").semantics { contentDescription = less }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(value, Modifier.widthIn(min = 64.dp).testTag("${tag}_value"), textAlign = TextAlign.Center, maxLines = 1, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        IconButton(onClick = onMore, enabled = canMore, modifier = Modifier.size(MinTouchTarget).testTag("${tag}_more").semantics { contentDescription = more }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
