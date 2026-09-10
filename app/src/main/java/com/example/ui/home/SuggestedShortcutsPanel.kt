package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import androidx.compose.ui.unit.dp
import com.example.data.ScriptActionCatalog
import com.example.data.ShortcutScript
import com.example.data.SuggestedShortcut
import com.example.data.SuggestedShortcuts
import com.example.ui.components.ConsolePanel
import com.example.ui.components.IconTile
import com.example.ui.components.MinTouchTarget
import com.example.ui.scripts.actionIconFor
import com.example.ui.theme.SectionLabel

/**
 * Ready-made shortcuts as a vertical list rather than a wrapping tile grid: Home has little else to
 * show, so this is the panel that fills the screen, and a list scrolls down with the rest of the page
 * instead of competing for width with its own sideways scroll.
 */
@Composable
fun SuggestedShortcutsPanel(isPro: Boolean, scripts: List<ShortcutScript>, onPick: (SuggestedShortcut) -> Unit) {
    val colors = MaterialTheme.colorScheme
    // "Already built" without a persisted suggestion id: a saved script with the same shape. A
    // hand-built shortcut with that shape counts as built too, which is the honest reading —
    // offering it again would still be offering a duplicate.
    val remaining = SuggestedShortcuts.ordered.filterNot { suggestion -> scripts.any { suggestion.matches(it) } }
    if (remaining.isEmpty()) return
    // Grouped by shape rather than by a hand-kept list, so the sections match the pills on the rows.
    val groups = listOf(
        R.string.home_suggestions_group_single to remaining.filter { !it.automation && it.actions.size == 1 },
        R.string.home_suggestions_group_multi to remaining.filter { !it.automation && it.actions.size > 1 },
        R.string.home_suggestions_group_automation to remaining.filter { it.automation },
    ).filter { it.second.isNotEmpty() }
    ConsolePanel(label = stringResource(R.string.home_suggested_shortcuts), testTag = "suggested_shortcuts") {
        Column {
            // Counted over the whole panel, not per group: the first group alone is a dozen rows, so a
            // group-boundary ad would sit far below the fold.
            var row = 0
            groups.forEachIndexed { groupIndex, (labelRes, items) ->
                Text(
                    stringResource(labelRes),
                    Modifier.padding(top = if (groupIndex == 0) 0.dp else 16.dp, bottom = 2.dp),
                    style = SectionLabel,
                    color = colors.onSurfaceVariant,
                )
                items.forEachIndexed { index, suggestion ->
                    if (index > 0) HorizontalDivider(color = colors.outline.copy(alpha = .45f))
                    SuggestionRow(suggestion, onPick)
                    row++
                    // Ad as the panel's 2nd and 9th row, never as its last item.
                    if (!isPro && (row == 1 || row == 8) && row < remaining.size) {
                        HorizontalDivider(color = colors.outline.copy(alpha = .45f))
                        NativeAdCard(Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

/** Titles whose outcome no action icon names: the first action's glyph would read as the wrong thing. */
private val suggestionIcons: Map<String, ImageVector> = mapOf(
    "suggestion_headphones_play" to Icons.Outlined.Headphones,
    "suggestion_assistant" to Icons.Outlined.Mic,
    "suggestion_bedtime" to Icons.Outlined.Bedtime,
    "suggestion_notifications" to Icons.Outlined.Notifications,
    "suggestion_car_bluetooth" to Icons.Outlined.DirectionsCar,
    "suggestion_sos" to Icons.Outlined.Sos,
    "suggestion_low_battery" to Icons.Outlined.BatteryAlert,
    "suggestion_night_charge_dnd" to Icons.Outlined.BatteryChargingFull,
    "suggestion_battery_saver" to Icons.Outlined.BatterySaver,
)

/**
 * Is [script] the shortcut this suggestion builds? Shared with [iconForScript] so the panel and a
 * built shortcut's icon can never disagree. Actions must match exactly, but constraints compare by
 * type: the editor asks the user to fill a constraint's parameter (the Bluetooth device, say), so a
 * full comparison would miss the very script the suggestion produced. Comparing constraints at all
 * is what keeps two suggestions that share an action list — headphones vs. car Bluetooth — apart.
 */
internal fun SuggestedShortcut.matches(script: ShortcutScript): Boolean =
    automation == script.automation &&
        actions == script.actions &&
        constraints.map { it.type } == script.constraints.map { it.type }

/** A shortcut built from a suggestion keeps that suggestion's icon; anything else uses its first action's. */
fun iconForScript(script: ShortcutScript): ImageVector {
    val suggestion = SuggestedShortcuts.ordered.firstOrNull { it.matches(script) }
        ?: return actionIconFor(script.actions.firstOrNull()?.type.orEmpty())
    return suggestionIcons[suggestion.id] ?: actionIconFor(suggestion.iconType)
}

@Composable
private fun SuggestionRow(suggestion: SuggestedShortcut, onPick: (SuggestedShortcut) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val available = suggestion.available
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = MinTouchTarget)
        .testTag("suggestion_${suggestion.id}")
        .alpha(if (available) 1f else 0.5f)
    val rowModifier = if (available) {
        base.clickable(
            role = Role.Button,
            onClickLabel = stringResource(R.string.home_build_from_suggestion, stringResource(suggestion.titleRes)),
            onClick = { onPick(suggestion) },
        )
    } else {
        base
    }

    Row(
        rowModifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            suggestionIcons[suggestion.id] ?: actionIconFor(suggestion.iconType),
            // Same rule as a built shortcut's row, so the colour does not change once it is built.
            ScriptActionCatalog.descriptor(suggestion.actions.first().type)?.category,
            size = 38.dp,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                stringResource(suggestion.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = if (available) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(suggestion.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TileBadge(suggestion)
    }
}

@Composable
private fun TileBadge(suggestion: SuggestedShortcut) {
    val colors = MaterialTheme.colorScheme
    val actionCount = suggestion.actions.count { it.type != "delay" }
    val label = when {
        !suggestion.available -> stringResource(R.string.home_suggestion_soon)
        suggestion.automation -> stringResource(R.string.home_suggestion_automation)
        actionCount > 1 -> pluralStringResource(R.plurals.home_suggestion_steps, actionCount, actionCount)
        else -> return
    }
    Text(
        label,
        Modifier
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        // onSurfaceVariant on this fill measures 4.05:1 on light, under AA. onSurface keeps the
        // pill exactly as it looks and reads 13.4:1 light, 9.6:1 sepia, 12.8:1 dark.
        color = colors.onSurface,
    )
}
