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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.KeyMappingConfig
import com.example.data.MillisecondSettingSpec
import com.example.data.TriggerPressType
import com.example.data.TriggerTimingSpecs
import com.example.ui.components.ConsoleSwitch
import com.example.ui.components.MillisecondSliderSetting
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import com.example.ui.theme.SectionLabel
import com.example.viewmodel.ScriptDraft

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
    onSetHapticDurationMs: (Long?) -> Unit = {},
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
                singleLine = true,
            )
            ConsoleSwitch(stringResource(R.string.script_settings_enabled), draft.enabled, onSetEnabled)
            HorizontalDivider(color = colors.outline.copy(alpha = .45f))
            OverridableConsumeSwitch(
                override = draft.consumeOriginalEvent,
                globalDefault = defaults.consumeOriginalEvent,
                onChange = onSetConsumeOriginalEvent,
            )
            HorizontalDivider(color = colors.outline.copy(alpha = .45f))
            OverridableVibrateSwitch(
                override = draft.vibrateOnTrigger,
                globalDefault = defaults.vibrateOnTrigger,
                onChange = onSetVibrateOnTrigger,
            )
            OverridableMillisecondSlider(
                title = stringResource(R.string.settings_haptic_duration),
                override = draft.hapticDurationMs,
                globalDefault = defaults.hapticDurationMs,
                spec = TriggerTimingSpecs.hapticDuration,
                testTag = "script_haptic_duration_slider",
                enabled = draft.vibrateOnTrigger ?: defaults.vibrateOnTrigger,
                onChange = onSetHapticDurationMs,
            )
            HorizontalDivider(color = colors.outline.copy(alpha = .45f))
            if (draft.trigger == null) {
                Text(
                    stringResource(R.string.script_settings_assign_key_first),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(stringResource(R.string.script_settings_trigger_type), style = SectionLabel, color = colors.onSurfaceVariant)
                PressTypeChips(draft.trigger.pressType, onSetPressType)
                OverridableMillisecondSlider(
                    title = stringResource(R.string.script_settings_double_press_window),
                    override = draft.trigger.doublePressWindowMs,
                    globalDefault = defaults.doublePressWindowMs,
                    spec = TriggerTimingSpecs.doublePressWindow,
                    testTag = "script_double_press_window_slider",
                    onChange = onSetDoublePressWindow,
                )
                OverridableMillisecondSlider(
                    title = stringResource(R.string.script_settings_long_press_threshold),
                    override = draft.trigger.longPressThresholdMs,
                    globalDefault = defaults.longPressThresholdMs,
                    spec = TriggerTimingSpecs.longPressThreshold,
                    testTag = "script_long_press_threshold_slider",
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
