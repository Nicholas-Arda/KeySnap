package com.example.ui.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import androidx.compose.ui.unit.dp
import io.github.nicholasarda.keysnap.BuildConfig
import com.example.data.AppLocale
import com.example.data.KeyMappingConfig
import com.example.data.LiveKeyEvent
import com.example.data.TriggerTimingSpecs
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.ConsolePanel
import com.example.ui.components.InnerShape
import com.example.ui.components.MillisecondSliderSetting
import com.example.ui.components.MinTouchTarget
import com.example.ui.theme.MonoBody
import com.example.ui.theme.MonoLabel
import com.example.ui.theme.SectionLabel
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeToggleIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How many of the most recent events the monitor renders; the rest stay in the buffer. */
private const val VISIBLE_EVENTS = 6

/** Wall-clock stamp per log row: "was that this press or the one before it?" is the whole question. */
private val LogTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun SettingsScreen(
    config: KeyMappingConfig,
    onToggleConsume: (Boolean) -> Unit,
    onToggleVibrate: (Boolean) -> Unit,
    isVibratorAvailable: Boolean = true,
    onDoublePressWindowChange: (Long) -> Unit = {},
    onLongPressThresholdChange: (Long) -> Unit = {},
    onHapticDurationChange: (Long) -> Unit = {},
    keyHistory: List<LiveKeyEvent> = emptyList(),
    isEventLogExpanded: Boolean = false,
    onToggleLog: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    themeMode: ThemeMode? = null,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    onEraseAllData: () -> Unit = {},
    isAdvancedModeRunning: Boolean = false,
    onToggleAdvancedMode: (Boolean) -> Unit = {},
    onRunSetupAgain: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        item {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SettingsGroupLabel(R.string.settings_group_keys, first = true) }
        item {
            ConsolePanel(stringResource(R.string.settings_defaults_panel), testTag = "defaults_for_new_scripts") {
                Text(
                    stringResource(R.string.settings_defaults_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MillisecondSliderSetting(
                    title = stringResource(R.string.script_settings_double_press_window),
                    description = stringResource(R.string.settings_double_press_description),
                    valueMs = config.doublePressWindowMs,
                    spec = TriggerTimingSpecs.doublePressWindow,
                    testTag = "double_press_window_slider",
                    onValueChange = onDoublePressWindowChange,
                )
                MillisecondSliderSetting(
                    title = stringResource(R.string.script_settings_long_press_threshold),
                    description = stringResource(R.string.settings_long_press_description),
                    valueMs = config.longPressThresholdMs,
                    spec = TriggerTimingSpecs.longPressThreshold,
                    testTag = "long_press_threshold_slider",
                    onValueChange = onLongPressThresholdChange,
                )
            }
        }
        item {
            ConsolePanel(stringResource(R.string.settings_input_panel), testTag = "input_behavior") {
                SettingsSwitchRow(
                    title = stringResource(R.string.script_settings_consume_input),
                    description = stringResource(R.string.script_settings_consume_input_description),
                    checked = config.consumeOriginalEvent,
                    enabled = true,
                    testTag = "consume_input_switch",
                    onCheckedChange = onToggleConsume,
                )
            }
        }
        item {
            ConsolePanel(stringResource(R.string.settings_haptics_panel), testTag = "haptics") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_haptic_feedback),
                    description = stringResource(
                        if (isVibratorAvailable) R.string.settings_haptic_feedback_on else R.string.settings_haptic_unavailable,
                    ),
                    checked = config.vibrateOnTrigger,
                    enabled = isVibratorAvailable,
                    testTag = "haptic_feedback_switch",
                    onCheckedChange = onToggleVibrate,
                )
                MillisecondSliderSetting(
                    title = stringResource(R.string.settings_haptic_duration),
                    description = stringResource(
                        if (isVibratorAvailable && config.vibrateOnTrigger) {
                            R.string.settings_haptic_duration_description
                        } else {
                            R.string.settings_haptic_duration_disabled
                        },
                    ),
                    valueMs = config.hapticDurationMs,
                    spec = TriggerTimingSpecs.hapticDuration,
                    enabled = isVibratorAvailable && config.vibrateOnTrigger,
                    testTag = "haptic_duration_slider",
                    onValueChange = onHapticDurationChange,
                )
            }
        }
        item { SettingsGroupLabel(R.string.settings_group_advanced) }
        item {
            AdvancedModePanel(
                isRunning = isAdvancedModeRunning,
                onToggle = onToggleAdvancedMode,
                onRunSetupAgain = onRunSetupAgain,
            )
        }
        item { EventLogPanel(keyHistory, isEventLogExpanded, onToggleLog, onClearHistory) }
        item { SettingsGroupLabel(R.string.settings_group_app) }
        if (themeMode != null) {
            item {
                ConsolePanel(stringResource(R.string.settings_appearance_panel), testTag = "appearance") {
                    AppearanceRow(themeMode, onSetThemeMode)
                }
            }
        }
        item { LanguagePanel() }
        item { AboutPanel(onEraseAllData) }
    }
}

/** Eight sibling panels do not scan; these split them into Keys, Advanced and App. */
@Composable
private fun SettingsGroupLabel(labelRes: Int, first: Boolean = false) {
    Text(
        stringResource(labelRes),
        Modifier.padding(top = if (first) 0.dp else 10.dp),
        style = SectionLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Advanced Mode's real on/off (`toggleAdvancedMode`) and its re-pair entry point used to live only
 * in the setup wizard and the Home status panel; this is the one place a user can find and flip
 * them without going through Home.
 */
@Composable
private fun AdvancedModePanel(
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    onRunSetupAgain: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val runSetupLabel = stringResource(R.string.settings_run_setup)
    ConsolePanel(stringResource(R.string.settings_expert_panel), testTag = "advanced_mode") {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .toggleable(value = isRunning, role = Role.Switch, onValueChange = onToggle)
                .testTag("advanced_mode_switch"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.settings_detached_bridge), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                Text(
                    stringResource(
                        if (isRunning) R.string.settings_bridge_running else R.string.settings_bridge_stopped,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(checked = isRunning, onCheckedChange = null)
        }
        HorizontalDivider(color = colors.outline.copy(alpha = .45f))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .clickable(onClickLabel = runSetupLabel, onClick = onRunSetupAgain)
                .testTag("run_setup_again_button"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(runSetupLabel, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                Text(stringResource(R.string.settings_run_setup_description), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = colors.onSurfaceVariant)
        }
    }
}

/**
 * The one place that answers "what does this app know about me, and how do I take it back".
 *
 * An app that asks for shell-level access has to answer that without the user reading source
 * code. The summary line this panel used to carry could only assert; [AboutDialog] can show the
 * checks behind each claim, so the panel now leads with the way into it and keeps the two actions
 * a user comes here for. The detail lives in [PrivacyPolicy] rather than being duplicated, so the
 * in-app wording and the wording published for Play cannot drift apart.
 */
@Composable
private fun AboutPanel(onEraseAllData: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var confirming by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    ConsolePanel(stringResource(R.string.settings_about_panel), testTag = "about") {
        AboutRow(
            label = stringResource(R.string.settings_about_app),
            sublabel = stringResource(R.string.settings_about_app_summary),
            leadingIcon = Icons.Outlined.VerifiedUser,
            icon = Icons.Outlined.ChevronRight,
            color = colors.onSurface,
            testTag = "about_app_button",
            onClick = { showAbout = true },
        )

        HorizontalDivider(color = colors.outline.copy(alpha = .45f))
        AboutRow(
            label = stringResource(R.string.settings_privacy_policy),
            icon = Icons.Outlined.ChevronRight,
            color = colors.onSurface,
            testTag = "privacy_policy_button",
            onClick = { showPolicy = true },
        )
        HorizontalDivider(color = colors.outline.copy(alpha = .45f))
        AboutRow(
            label = stringResource(R.string.settings_erase),
            icon = Icons.Outlined.Delete,
            color = colors.error,
            testTag = "erase_all_data_button",
            onClick = { confirming = true },
        )

        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MonoLabel,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    if (showAbout) {
        AboutDialog(onEraseAllData = onEraseAllData, onDismiss = { showAbout = false })
    }
    if (showPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPolicy = false })
    }
    // Same confirm dialog a single shortcut deletion gets; the old two-tap swap had no timeout and
    // no way back once the second tap was already under the finger.
    if (confirming) {
        EraseAllDataDialog(
            onConfirm = {
                confirming = false
                onEraseAllData()
            },
            onDismiss = { confirming = false },
        )
    }
}

/** Same tappable row as the Advanced Mode panel's, so About's three actions match the rest of Settings. */
@Composable
private fun AboutRow(
    label: String,
    icon: ImageVector,
    color: Color,
    testTag: String,
    onClick: () -> Unit,
    sublabel: String? = null,
    leadingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(onClickLabel = label, onClick = onClick)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, Modifier.size(20.dp), tint = colors.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
            if (sublabel != null) {
                Text(sublabel, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Icon(icon, null, tint = color)
    }
}

/** Appearance is a single choice, so it is a radio group and announces itself as one. */
@Composable
private fun AppearanceRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val labels = mapOf(
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
        ThemeMode.SEPIA to stringResource(R.string.theme_sepia),
    )
    Row(
        Modifier.fillMaxWidth().selectableGroup().testTag("appearance_toggle_button"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(InnerShape)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(mode) },
                    )
                    .padding(vertical = 4.dp)
                    .testTag("appearance_option_${mode.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(InnerShape)
                        .background(colors.surfaceVariant.copy(alpha = if (isSelected) .35f else .15f))
                        .then(
                            if (isSelected) {
                                Modifier.border(2.5.dp, colors.primary, InnerShape)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ThemeToggleIcon(themeMode = mode)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    labels.getValue(mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The raw event feed. The header states how many of the buffered events are actually on screen,
 * because printing the buffer size next to six rows reads as a count of what is shown.
 */
@Composable
private fun EventLogPanel(
    history: List<LiveKeyEvent>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val visible = history.take(VISIBLE_EVENTS)
    ConsolePanel(
        label = stringResource(R.string.settings_input_monitor),
        testTag = "input_monitor",
        onHeaderClick = onToggle,
        isExpanded = expanded,
        headerActionDescription = stringResource(if (expanded) R.string.settings_hide_log_action else R.string.settings_show_log_action),
        headerActionIcon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
    ) {
        // The header chevron is the only toggle, so this line carries what a shut panel is worth
        // opening for: the newest event and how many are buffered, merged into one reading.
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Terminal, null, tint = colors.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                history.firstOrNull()?.keyName ?: stringResource(R.string.settings_log_waiting),
                Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MonoLabel,
                color = colors.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (expanded && history.size > visible.size) {
                    stringResource(R.string.settings_log_count, visible.size, history.size)
                } else {
                    pluralStringResource(R.plurals.settings_log_event_count, history.size, history.size)
                },
                style = MonoLabel,
                color = colors.onSurfaceVariant,
            )
        }
        if (expanded && history.isNotEmpty()) {
            visible.forEach { event -> EventRow(event) }
            TextButton(
                onClick = onClear,
                modifier = Modifier.heightIn(min = MinTouchTarget).testTag("clear_history_button"),
            ) {
                Text(stringResource(R.string.settings_log_clear), style = MonoLabel)
            }
        }
    }
}

@Composable
private fun EventRow(event: LiveKeyEvent) {
    val colors = MaterialTheme.colorScheme
    val matchedLabel = stringResource(R.string.settings_log_matched)
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (event.isTriggerMatch) stateDescription = matchedLabel
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            LogTimeFormat.format(Instant.ofEpochMilli(event.timestamp)),
            style = MonoLabel,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        // A match was primary-coloured and nothing else; the marker is what makes it survive a
        // colour-blind reading or a screenshot. The blank keeps the key names in one column.
        Text(
            if (event.isTriggerMatch) "▶" else " ",
            style = MonoBody,
            color = colors.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            event.keyName,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MonoBody,
            color = colors.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            event.actionName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MonoLabel,
            color = if (event.isTriggerMatch) colors.primary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .testTag(testTag)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                description,
                color = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * The in-app language picker. Writing the choice through [AppLocale] and recreating the activity is
 * the whole mechanism: on Android 13+ the platform is told as well, so services and toasts follow;
 * below that the recreated activity re-runs `attachBaseContext` and picks the locale up there.
 */
@Composable
private fun LanguagePanel() {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val activity = context as? Activity
    var selected by remember { mutableStateOf(AppLocale.stored(context)) }
    var showPicker by remember { mutableStateOf(false) }
    ConsolePanel(stringResource(R.string.settings_language_panel), testTag = "language") {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .clickable { showPicker = true }
                .testTag("language_picker_button"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LocaleFlag(selected)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(AppLocale.labelFor(selected)),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ChevronRight, null, tint = colors.onSurfaceVariant)
        }
    }
    if (showPicker) {
        LanguagePickerDialog(
            selected = selected,
            onSelect = { tag ->
                showPicker = false
                if (tag != selected) {
                    selected = tag
                    AppLocale.set(context, tag)
                    activity?.recreate()
                }
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** A language's own flag, or the globe glyph where "follow the system" has no country to show. */
@Composable
private fun LocaleFlag(tag: String) {
    val flag = AppLocale.flagFor(tag)
    if (flag.isEmpty()) {
        Icon(
            Icons.Outlined.Language,
            null,
            Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(flag, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Same dismissible-dialog idiom as [PrivacyPolicyDialog], so the language choice is a tap away. */
@Composable
private fun LanguagePickerDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Language, null) },
        title = { Text(stringResource(R.string.settings_language_panel)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup().verticalScroll(rememberScrollState()),
            ) {
                AppLocale.options.forEach { (tag, labelRes) ->
                    val isSelected = tag == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MinTouchTarget)
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(tag) })
                            .testTag("language_option_${tag.ifEmpty { "system" }}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        LocaleFlag(tag)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
