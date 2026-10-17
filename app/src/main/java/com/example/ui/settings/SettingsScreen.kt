package com.example.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import com.example.data.AppLocale
import com.example.data.KeyMappingConfig
import com.example.data.LiveKeyEvent
import com.example.data.TriggerTimingSpecs
import com.example.service.adb.secureSettingsCommand
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
import io.github.nicholasarda.keysnap.BuildConfig
import io.github.nicholasarda.keysnap.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How many of the most recent events the monitor renders; the rest stay in the buffer. */
private const val VISIBLE_EVENTS = 6

/** Wall-clock stamp per log row: "was that this press or the one before it?" is the whole question. */
private val LogTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

enum class SettingsSection {
    ADVANCED_MODE,
    APPEARANCE,
    ABOUT,
}

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
    themeFollowSystem: Boolean = false,
    onSetThemeSystem: () -> Unit = {},
    oledMode: Boolean = false,
    onSetOledMode: (Boolean) -> Unit = {},
    onEraseAllData: () -> Unit = {},
    isAdvancedModeRunning: Boolean = false,
    onToggleAdvancedMode: (Boolean) -> Unit = {},
    onRunSetupAgain: () -> Unit = {},
    advancedAutostart: Boolean = false,
    autostartPermissionHeld: Boolean = false,
    autostartChanging: Boolean = false,
    onToggleAutostart: (Boolean) -> Unit = {},
    reveal: (index: Int) -> Modifier = { Modifier },
    targetSection: SettingsSection? = null,
    onSectionShown: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    LaunchedEffect(targetSection) {
        val index = when (targetSection) {
            SettingsSection.ADVANCED_MODE -> 6
            SettingsSection.APPEARANCE -> 9
            SettingsSection.ABOUT -> if (themeMode != null) 11 else 10
            null -> return@LaunchedEffect
        }
        listState.scrollToItem(index)
        if (targetSection != SettingsSection.ABOUT) onSectionShown()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("settings_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 40.dp),
    ) {
        item {
            Column(
                reveal(0).padding(horizontal = 8.dp).padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    modifier = Modifier.testTag("settings_title"),
                    style = MaterialTheme.typography.titleLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SettingsGroupLabel(R.string.settings_group_keys, reveal(1), first = true) }
        item {
            ConsolePanel(stringResource(R.string.settings_defaults_panel), testTag = "defaults_for_new_scripts", modifier = reveal(2)) {
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
            ConsolePanel(stringResource(R.string.settings_input_panel), testTag = "input_behavior", modifier = reveal(3)) {
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
            ConsolePanel(stringResource(R.string.settings_haptics_panel), testTag = "haptics", modifier = reveal(4)) {
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
        item { SettingsGroupLabel(R.string.settings_group_advanced, reveal(5)) }
        item {
            Box(reveal(6)) {
                AdvancedModePanel(
                    isRunning = isAdvancedModeRunning,
                    onToggle = onToggleAdvancedMode,
                    onRunSetupAgain = onRunSetupAgain,
                    autostart = advancedAutostart,
                    grantHeld = autostartPermissionHeld,
                    changing = autostartChanging,
                    onToggleAutostart = onToggleAutostart,
                )
            }
        }
        item { Box(reveal(7)) { EventLogPanel(keyHistory, isEventLogExpanded, onToggleLog, onClearHistory) } }
        item { SettingsGroupLabel(R.string.settings_group_app, reveal(8)) }
        if (themeMode != null) {
            item {
                ConsolePanel(stringResource(R.string.settings_appearance_panel), testTag = "appearance", modifier = reveal(9)) {
                    AppearanceRow(themeMode, themeFollowSystem, onSetThemeMode, onSetThemeSystem)
                    if (themeMode == ThemeMode.DARK) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.theme_oled),
                            description = stringResource(R.string.theme_oled_subtitle),
                            checked = oledMode,
                            enabled = true,
                            testTag = "oled_mode_switch",
                            onCheckedChange = onSetOledMode,
                        )
                    }
                }
            }
        }
        item { Box(reveal(10)) { LanguagePanel() } }
        item {
            Box(reveal(11)) {
                AboutPanel(
                    onEraseAllData = onEraseAllData,
                    openRequested = targetSection == SettingsSection.ABOUT,
                    onOpenHandled = onSectionShown,
                )
            }
        }
    }
}

/** Eight sibling panels do not scan; these split them into Keys, Advanced and App. */
@Composable
private fun SettingsGroupLabel(labelRes: Int, modifier: Modifier = Modifier, first: Boolean = false) {
    Text(
        stringResource(labelRes),
        modifier.padding(start = 8.dp, top = if (first) 0.dp else 10.dp),
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
    autostart: Boolean,
    grantHeld: Boolean,
    changing: Boolean,
    onToggleAutostart: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val runSetupLabel = stringResource(R.string.settings_run_setup)
    ConsolePanel(stringResource(R.string.settings_expert_panel), testTag = "advanced_mode") {
        // The shared switch row, so at large text the switch drops under the text instead of squeezing it.
        SettingsSwitchRow(
            title = stringResource(R.string.settings_detached_bridge),
            description = stringResource(
                if (isRunning) R.string.settings_bridge_running else R.string.settings_bridge_stopped,
            ),
            checked = isRunning,
            enabled = true,
            testTag = "advanced_mode_switch",
            onCheckedChange = onToggle,
        )
        HorizontalDivider(color = colors.outline.copy(alpha = .45f))
        SettingsSwitchRow(
            title = stringResource(R.string.settings_autostart_title),
            description = stringResource(R.string.settings_autostart_description),
            checked = autostart,
            enabled = !changing,
            testTag = "advanced_autostart_switch",
            onCheckedChange = onToggleAutostart,
        )
        // The fixed permission command must be visible before the user enables the option.
        Text(
            stringResource(R.string.settings_autostart_permission_description,
                BidiFormatter.getInstance().unicodeWrap(secureSettingsCommand(context.packageName, true), TextDirectionHeuristicsCompat.LTR)),
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
        )
        if (autostart) {
            Text(
                stringResource(R.string.settings_autostart_hint_pairing),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            if (!grantHeld) {
                Text(
                    stringResource(R.string.settings_autostart_hint_permission),
                    Modifier.clickable {
                        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                    },
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                Text(stringResource(R.string.settings_autostart_hint_prompt),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        } else if (grantHeld) {
            Text(stringResource(R.string.settings_autostart_cleanup_failed),
                style = MaterialTheme.typography.bodySmall, color = colors.error)
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
private fun AboutPanel(
    onEraseAllData: () -> Unit,
    openRequested: Boolean,
    onOpenHandled: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var confirming by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showProblemRecording by remember { mutableStateOf(false) }
    LaunchedEffect(openRequested) {
        if (openRequested) {
            showProblemRecording = true
            showAbout = true
            onOpenHandled()
        }
    }

    ConsolePanel(
        stringResource(R.string.settings_about_panel),
        testTag = "about",
        contentPadding = if (LocalDensity.current.fontScale > 1.15f) 8.dp else 16.dp,
    ) {
        AboutRow(
            label = stringResource(R.string.settings_about_app),
            sublabel = stringResource(R.string.settings_about_app_summary),
            leadingIcon = Icons.Outlined.VerifiedUser,
            icon = Icons.Outlined.ChevronRight,
            color = colors.onSurface,
            testTag = "about_app_button",
            onClick = {
                showProblemRecording = false
                showAbout = true
            },
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
            stringResource(R.string.settings_version, BidiFormatter.getInstance().unicodeWrap(BuildConfig.VERSION_NAME, TextDirectionHeuristicsCompat.LTR), BuildConfig.VERSION_CODE),
            style = MonoLabel,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    if (showAbout) {
        AboutDialog(
            onEraseAllData = onEraseAllData,
            onDismiss = { showAbout = false },
            showProblemRecording = showProblemRecording,
        )
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth / LocalDensity.current.fontScale < 240.dp
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .clickable(onClickLabel = label, onClick = onClick)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!compact && leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(20.dp), tint = colors.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(label, Modifier.testTag("${testTag}_label"), style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = color)
                if (sublabel != null) {
                    Text(sublabel, style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = colors.onSurfaceVariant)
                }
            }
            if (!compact) Icon(icon, null, tint = color)
        }
    }
}

/** Appearance is a single choice, so it is a radio group and announces itself as one. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AppearanceRow(
    selected: ThemeMode,
    followSystem: Boolean,
    onSelect: (ThemeMode) -> Unit,
    onSelectSystem: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val labels = mapOf(
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
        ThemeMode.SEPIA to stringResource(R.string.theme_sepia),
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth / LocalDensity.current.fontScale < 240.dp
        FlowRow(
            Modifier.fillMaxWidth().selectableGroup().testTag("appearance_toggle_button"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = if (stacked) 2 else 4,
        ) {
            (listOf<ThemeMode?>(null) + ThemeMode.entries).forEach { mode ->
                val isSelected = if (mode == null) followSystem else !followSystem && mode == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(InnerShape)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { if (mode == null) onSelectSystem() else onSelect(mode) },
                        )
                        .padding(vertical = 4.dp)
                        .testTag("appearance_option_${mode?.name?.lowercase() ?: "system"}"),
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
                        if (mode == null) Icon(Icons.Default.Settings, null, tint = colors.primary) else ThemeToggleIcon(themeMode = mode)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (mode == null) stringResource(R.string.theme_system) else labels.getValue(mode),
                        style = MaterialTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                        color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                    )
                }
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
@OptIn(ExperimentalLayoutApi::class)
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
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
    ) {
        val stacked = maxWidth / LocalDensity.current.fontScale < 240.dp
        FlowRow(
            Modifier.fillMaxWidth(),
            maxItemsInEachRow = if (stacked) 1 else 2,
        ) {
            Column(
                (if (stacked) Modifier.fillMaxWidth() else Modifier.weight(1f).padding(end = 12.dp))
                    .align(Alignment.CenterVertically),
            ) {
                Text(
                    title,
                    color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                )
                Text(
                    description,
                    color = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                )
            }
            Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.align(Alignment.CenterVertically))
        }
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
        BoxWithConstraints {
            val compact = maxWidth / LocalDensity.current.fontScale < 240.dp
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget)
                    .clickable { showPicker = true }
                    .testTag("language_picker_button"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!compact) {
                    LocaleFlag(selected)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    stringResource(AppLocale.labelFor(selected)),
                    style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f).testTag("settings_language_label"),
                )
                if (!compact) Icon(Icons.Outlined.ChevronRight, null, tint = colors.onSurfaceVariant)
            }
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
