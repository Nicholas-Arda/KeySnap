package com.example.ui.scripts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LooksTwo
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.capsule.CapsuleHooks
import com.example.data.KeyMappingConfig
import com.example.data.MillisecondSettingSpec
import com.example.data.TriggerPressType
import com.example.bridge.BuzzPattern
import com.example.data.TriggerTimingSpecs
import com.example.service.TimeSchedule
import com.example.ui.components.MillisecondSliderSetting
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import com.example.ui.theme.SectionLabel
import com.example.viewmodel.ScriptDraft
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.example.data.RunNotice
import com.example.service.AutomationRunNotifier
import com.example.service.AutomationUndo

/** Per-script behavior: name, enabled, press type and timing overrides. Nothing here is written
 * through until the editor's Done commits the whole [ScriptDraft]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptSettingsSheet(
    draft: ScriptDraft,
    defaults: KeyMappingConfig,
    onDismiss: () -> Unit,
    onSetName: (String) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetPressType: (TriggerPressType) -> Unit,
    onSetDoublePressWindow: (Long?) -> Unit,
    onSetLongPressThreshold: (Long?) -> Unit,
    onSetConsumeOriginalEvent: (Boolean?) -> Unit = {},
    onSetVibrateOnTrigger: (Boolean?) -> Unit = {},
    onSetShowCapsule: (Boolean) -> Unit = {},
    onSetHapticPattern: (String?) -> Unit = {},
    onSetExactTiming: (Boolean) -> Unit = {},
    onSetNotice: (RunNotice) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    // Sliders plus a focused name field make this taller than a short screen, so the content
    // scrolls inside a bounded box rather than running off the bottom of the sheet.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = maxHeight)
                .nestedScroll(rememberSheetContentScroll())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.editor_shortcut_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                // Said once for the whole sheet; both switches below used to repeat it.
                Text(
                    stringResource(R.string.script_settings_not_global),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = onSetName,
                modifier = Modifier.fillMaxWidth().testTag("script_settings_name_field"),
                label = { Text(stringResource(R.string.script_settings_name)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.primary) },
                singleLine = true,
            )
            SheetSwitchRow(Icons.Outlined.PowerSettingsNew, stringResource(R.string.script_settings_enabled), null, draft.enabled, onSetEnabled)
            if (draft.automation) NoticeRow(draft, onSetNotice)
            HorizontalDivider(color = colors.outline.copy(alpha = .45f))
            OverridableConsumeSwitch(
                override = draft.consumeOriginalEvent,
                globalDefault = defaults.consumeOriginalEvent,
                onChange = onSetConsumeOriginalEvent,
            )
            HorizontalDivider(color = colors.outline.copy(alpha = .45f))
            if (CapsuleHooks.AVAILABLE && draft.trigger != null && !draft.automation && !draft.surfaceOnly) {
                SheetSwitchRow(Icons.Outlined.SmartButton, stringResource(R.string.script_settings_show_capsule),
                    stringResource(R.string.script_settings_show_capsule_description), draft.showCapsule, onSetShowCapsule,
                    Modifier.testTag("script_settings_capsule_switch"))
                HorizontalDivider(color = colors.outline.copy(alpha = .45f))
            }
            OverridableVibrateSwitch(
                override = draft.vibrateOnTrigger,
                globalDefault = defaults.vibrateOnTrigger,
                onChange = onSetVibrateOnTrigger,
            )
            if (draft.vibrateOnTrigger ?: defaults.vibrateOnTrigger) {
                // An older single-length override shows as one buzz of that length.
                ShortcutBuzzEditor(
                    pattern = BuzzPattern.parse(draft.hapticPattern).ifEmpty { listOf(draft.hapticDurationMs ?: defaults.hapticDurationMs) },
                    isCustom = draft.hapticPattern != null || draft.hapticDurationMs != null,
                    onChange = { onSetHapticPattern(it?.let(BuzzPattern::join)) },
                )
            }
            HorizontalDivider(color = colors.outline.copy(alpha = .45f))
            if (TimeSchedule.hasTimeCondition(draft.automation, draft.constraints)) {
                ExactTimingSwitch(draft.exactTiming, onSetExactTiming)
                HorizontalDivider(color = colors.outline.copy(alpha = .45f))
            }
            if (draft.trigger == null) {
                Text(
                    stringResource(R.string.script_settings_assign_key_first),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SheetIcon(Icons.Outlined.TouchApp)
                    Text(stringResource(R.string.script_settings_trigger_type), style = SectionLabel, color = colors.onSurfaceVariant)
                }
                PressTypeChips(draft.trigger.pressType, onSetPressType)
                OverridableMillisecondSlider(
                    title = stringResource(R.string.script_settings_double_press_window),
                    override = draft.trigger.doublePressWindowMs,
                    globalDefault = defaults.doublePressWindowMs,
                    spec = TriggerTimingSpecs.doublePressWindow,
                    testTag = "script_double_press_window_slider",
                    icon = Icons.Outlined.LooksTwo,
                    onChange = onSetDoublePressWindow,
                )
                OverridableMillisecondSlider(
                    title = stringResource(R.string.script_settings_long_press_threshold),
                    override = draft.trigger.longPressThresholdMs,
                    globalDefault = defaults.longPressThresholdMs,
                    spec = TriggerTimingSpecs.longPressThreshold,
                    testTag = "script_long_press_threshold_slider",
                    icon = Icons.Outlined.Timelapse,
                    onChange = onSetLongPressThreshold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PressTypeChips(selected: TriggerPressType, onSelect: (TriggerPressType) -> Unit) {
    val colors = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TriggerPressType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(stringResource(type.titleRes), style = MaterialTheme.typography.labelMedium) },
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primary,
                    selectedLabelColor = colors.onPrimary,
                    containerColor = colors.surfaceVariant,
                    labelColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}

/** The Settings slider, with "reset" redefined as clearing the override rather than jumping to
 * the catalog default. */
@Composable
private fun OverridableMillisecondSlider(
    title: String,
    override: Long?,
    globalDefault: Long,
    spec: MillisecondSettingSpec,
    testTag: String,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onChange: (Long?) -> Unit,
) {
    MillisecondSliderSetting(
        title = title,
        description = if (override == null) {
            stringResource(R.string.script_settings_using_global, globalDefault)
        } else {
            stringResource(R.string.script_settings_custom_value)
        },
        valueMs = override ?: globalDefault,
        spec = spec,
        testTag = testTag,
        enabled = enabled,
        resetEnabled = override != null,
        onReset = { onChange(null) },
        icon = icon,
        onValueChange = { onChange(it) },
    )
}

@Composable
private fun OverridableVibrateSwitch(
    override: Boolean?,
    globalDefault: Boolean,
    onChange: (Boolean?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val effective = override ?: globalDefault
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = effective,
                role = Role.Switch,
                onValueChange = { onChange(!effective) },
            ).testTag("script_settings_vibrate_switch"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetIcon(Icons.Outlined.Vibration)
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    stringResource(R.string.script_settings_vibrate),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.script_settings_vibrate_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(checked = effective, onCheckedChange = null)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            if (override != null) {
                TextButton(onClick = { onChange(null) }, modifier = Modifier.testTag("vibrate_on_trigger_use_default")) {
                    Text(
                        stringResource(
                            R.string.script_settings_reset_to_default,
                            stringResource(if (globalDefault) R.string.common_yes else R.string.common_no),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExactTimingSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .testTag("script_settings_exact_timing_switch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetIcon(Icons.Outlined.Alarm)
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                stringResource(R.string.script_settings_exact_timing),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Text(
                stringResource(R.string.script_settings_exact_timing_description),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun OverridableConsumeSwitch(
    override: Boolean?,
    globalDefault: Boolean,
    onChange: (Boolean?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val effective = override ?: globalDefault
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = effective,
                role = Role.Switch,
                onValueChange = { onChange(!effective) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetIcon(Icons.Outlined.Block)
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    stringResource(R.string.script_settings_consume_input),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.script_settings_consume_input_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(checked = effective, onCheckedChange = null)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            if (override != null) {
                TextButton(onClick = { onChange(null) }, modifier = Modifier.testTag("consume_original_use_default")) {
                    Text(
                        stringResource(
                            R.string.script_settings_reset_to_default,
                            stringResource(if (globalDefault) R.string.common_yes else R.string.common_no),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/** Same leading icon as the key capsule settings rows. */
@Composable
private fun SheetIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, Modifier.padding(end = 12.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
}

/** Whether this automation has anything "Undo when it ends" could put back. */
private fun undoOffered(draft: ScriptDraft) =
    AutomationUndo.offered(draft.automation, draft.constraints) && draft.actions.any { it.undoOnEnd && AutomationUndo.canUndo(it) }

/** "Notifications": the master switch, which parts are on, and a gear to the words. */
@Composable
private fun NoticeRow(draft: ScriptDraft, onSetNotice: (RunNotice) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val notice = draft.notice
    val undo = undoOffered(draft)
    var editing by remember { mutableStateOf(false) }
    val parts = listOfNotNull(
        stringResource(R.string.notice_when_runs).takeIf { notice.onRun },
        stringResource(R.string.notice_when_undone).takeIf { notice.onUndo && undo },
    )
    fun setOn(on: Boolean) {
        onSetNotice(notice.copy(onRun = on, onUndo = on && undo))
        if (on && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = notice.any, role = Role.Switch, onValueChange = ::setOn)
            .testTag("script_settings_notice_switch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetIcon(Icons.Outlined.Notifications)
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(stringResource(R.string.notice_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Text(
                if (parts.isEmpty()) stringResource(R.string.notice_subtitle_off) else parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        AnimatedVisibility(notice.any) {
            IconButton(onClick = { editing = true }, modifier = Modifier.testTag("script_settings_notice_gear")) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.notice_edit), tint = colors.primary)
            }
        }
        Switch(checked = notice.any, onCheckedChange = null)
    }
    if (editing) NoticeSheet(draft, undo, onSetNotice) { editing = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeSheet(draft: ScriptDraft, undo: Boolean, onSetNotice: (RunNotice) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val notice = draft.notice
    val blank = RunNotice()
    // Placeholders are the words a blank field falls back to.
    val (runTitle, runText) = AutomationRunNotifier.runWords(context, draft.name, blank, draft.actions.size)
    val (undoTitle, undoText) = AutomationRunNotifier.undoWords(context, draft.name, blank)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("notice_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.notice_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(draft.name.ifBlank { stringResource(R.string.scripts_unnamed) }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            NoticePart(
                stringResource(R.string.notice_when_runs), notice.onRun, { onSetNotice(notice.copy(onRun = it)) },
                notice.runTitle, runTitle, { onSetNotice(notice.copy(runTitle = it)) },
                notice.runText, runText, { onSetNotice(notice.copy(runText = it)) },
            )
            if (undo) {
                NoticePart(
                    stringResource(R.string.notice_when_undone), notice.onUndo, { onSetNotice(notice.copy(onUndo = it)) },
                    notice.undoTitle, undoTitle, { onSetNotice(notice.copy(undoTitle = it)) },
                    notice.undoText, undoText, { onSetNotice(notice.copy(undoText = it)) },
                )
            }
            TextButton(
                onClick = { onSetNotice(notice.copy(runTitle = "", runText = "", undoTitle = "", undoText = "")) },
                enabled = listOf(notice.runTitle, notice.runText, notice.undoTitle, notice.undoText).any { it.isNotEmpty() },
            ) { Text(stringResource(R.string.notice_reset)) }
        }
    }
}

@Composable
private fun NoticePart(
    label: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    title: String,
    titleHint: String,
    onTitle: (String) -> Unit,
    text: String,
    textHint: String,
    onText: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = on, role = Role.Switch, onValueChange = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = SectionLabel, color = colors.onSurfaceVariant)
        Switch(checked = on, onCheckedChange = null)
    }
    AnimatedVisibility(on) {
        // The label sits above, so a blank field always shows the words it falls back to.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.notice_field_title), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            OutlinedTextField(
                value = title, onValueChange = { onTitle(it.take(60)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(titleHint) },
            )
            Text(stringResource(R.string.notice_field_text), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            OutlinedTextField(
                value = text, onValueChange = { onText(it.take(120)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(textHint) },
            )
        }
    }
}

@Composable
private fun SheetSwitchRow(
    icon: ImageVector,
    title: String,
    description: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetIcon(icon)
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = colors.onSurface)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
