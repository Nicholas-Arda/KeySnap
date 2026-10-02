package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.data.ActionEditorForms
import com.example.data.RecipeCategory
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
 * instead of competing for width with its own sideways scroll. The list is grouped into
 * [RecipeCategory] shelves, with a chip row on top that filters to one shelf.
 */
@Composable
fun SuggestedShortcutsPanel(isPro: Boolean, scripts: List<ShortcutScript>, onPick: (SuggestedShortcut) -> Unit) {
    val colors = MaterialTheme.colorScheme
    // "Already built" without a persisted suggestion id: a saved script with the same shape. A
    // hand-built shortcut with that shape counts as built too, which is the honest reading —
    // offering it again would still be offering a duplicate.
    val remaining = SuggestedShortcuts.ordered.filterNot { suggestion -> scripts.any { suggestion.matches(it) } }
    if (remaining.isEmpty()) return
    // Enum order, so the gallery reads basics first and the two special shelves last.
    val groups = RecipeCategory.entries
        .map { category -> category to remaining.filter { it.category == category } }
        .filter { it.second.isNotEmpty() }
    // Null shows every shelf. Saved, so the choice survives paging to Shortcuts and back.
    var selected by rememberSaveable { mutableStateOf<RecipeCategory?>(null) }
    // A shelf empties once its last recipe is built; fall back to all rather than show nothing.
    val active = selected?.takeIf { chosen -> groups.any { it.first == chosen } }
    val shown = groups.filter { active == null || it.first == active }
    val shownCount = shown.sumOf { it.second.size }
    ConsolePanel(label = stringResource(R.string.home_suggested_shortcuts), testTag = "suggested_shortcuts") {
        Column {
            RecipeCategoryChips(groups.map { it.first }, active) { selected = it }
            // Counted over the whole panel, not per group: the first group alone is a dozen rows, so a
            // group-boundary ad would sit far below the fold.
            var row = 0
            shown.forEachIndexed { groupIndex, (category, items) ->
                Text(
                    stringResource(category.titleRes),
                    Modifier.padding(top = if (groupIndex == 0) 8.dp else 16.dp, bottom = 2.dp),
                    style = SectionLabel,
                    color = colors.onSurfaceVariant,
                )
                items.forEachIndexed { index, suggestion ->
                    if (index > 0) HorizontalDivider(color = colors.outline.copy(alpha = .45f))
                    SuggestionRow(suggestion, onPick)
                    row++
                    // Ad as the panel's 2nd and 9th row, never as its last item.
                    if (!isPro && (row == 1 || row == 8) && row < shownCount) {
                        HorizontalDivider(color = colors.outline.copy(alpha = .45f))
                        NativeAdCard(Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

/** Same chip look as the action editor's choice rows ([com.example.ui.scripts.ChoiceFieldRow]). */
@Composable
private fun RecipeCategoryChips(
    categories: List<RecipeCategory>,
    selected: RecipeCategory?,
    onSelect: (RecipeCategory?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = colors.primary,
        selectedLabelColor = colors.onPrimary,
        containerColor = colors.surfaceVariant,
        labelColor = colors.onSurfaceVariant,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.home_recipes_all), style = MaterialTheme.typography.labelMedium) },
                border = null,
                colors = chipColors,
                modifier = Modifier.testTag("recipe_chip_all"),
            )
        }
        items(categories) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(stringResource(category.titleRes), style = MaterialTheme.typography.labelMedium) },
                border = null,
                colors = chipColors,
                modifier = Modifier.testTag("recipe_chip_${category.name.lowercase()}"),
            )
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
    // Ready-made commands: a terminal glyph on every row would say nothing about any of them.
    "recipe_screen_record" to Icons.Outlined.FiberManualRecord,
    "recipe_video_camera" to Icons.Outlined.Videocam,
    "recipe_internet_panel" to Icons.Outlined.Wifi,
    "recipe_animations_fast" to Icons.Outlined.Speed,
    "recipe_animations_normal" to Icons.Outlined.SlowMotionVideo,
    "recipe_timer_5_min" to Icons.Outlined.Timer,
    "recipe_dark_mode_toggle" to Icons.Outlined.DarkMode,
    "recipe_power_saving_toggle" to Icons.Outlined.EnergySavingsLeaf,
    "recipe_grayscale_toggle" to Icons.Outlined.FilterBAndW,
    "recipe_nearby_pharmacy" to Icons.Outlined.LocalPharmacy,
    "recipe_write_email" to Icons.Outlined.Email,
    "recipe_evening_dark" to Icons.Outlined.DarkMode,
    "recipe_morning_light" to Icons.Outlined.LightMode,
    "recipe_low_battery_power_saving" to Icons.Outlined.EnergySavingsLeaf,
    "recipe_bedtime_grayscale" to Icons.Outlined.FilterBAndW,
    "recipe_car_gas" to Icons.Outlined.LocalGasStation,
    "recipe_car_chargers" to Icons.Outlined.EvStation,
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
                // Two lines: beside a wide badge ("Automatisation") one line left the title as
                // "En quittant le tr…".
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(suggestion.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Under the subtitle rather than beside the title, so a long translation wraps in the
            // text column instead of squeezing the title.
            if (suggestion.actions.any { ActionEditorForms.needsAdvancedMode(it.type) }) {
                RecipePill(
                    stringResource(R.string.badge_expert_mode),
                    Modifier.padding(top = 4.dp).testTag("recipe_advanced_${suggestion.id}"),
                )
            }
            // In full, never ellipsized: it carries the limits the user must know before picking it.
            suggestion.noteRes?.let { note ->
                Text(
                    stringResource(note),
                    Modifier.padding(top = 4.dp).testTag("recipe_note_${suggestion.id}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        TileBadge(suggestion)
    }
}

@Composable
private fun TileBadge(suggestion: SuggestedShortcut) {
    val actionCount = suggestion.actions.count { it.type != "delay" }
    val label = when {
        !suggestion.available -> stringResource(R.string.home_suggestion_soon)
        suggestion.automation -> stringResource(R.string.home_suggestion_automation)
        actionCount > 1 -> pluralStringResource(R.plurals.home_suggestion_steps, actionCount, actionCount)
        else -> return
    }
    RecipePill(label)
}

@Composable
private fun RecipePill(label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        modifier
            // Rounded, not CircleShape: a long translation can wrap to two lines at large text sizes,
            // and a circle's half-height corners would then clip its first and last letters.
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        // onSurfaceVariant on this fill measures 4.05:1 on light, under AA. onSurface keeps the
        // pill exactly as it looks and reads 13.4:1 light, 9.6:1 sepia, 12.8:1 dark.
        color = colors.onSurface,
    )
}
