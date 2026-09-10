package com.example.ui.scripts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.PhysicalKeyOption
import com.example.data.TriggerPressType
import com.example.data.getReadableKeyName
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import com.example.ui.theme.SectionLabel

/**
 * Assigns the shortcut's trigger: a press type, then a key, in one sheet — replacing what used to
 * be a plain "assign a key" flow with a separate "build a combo" sheet behind it. The underlying
 * matcher only supports one key today, so recording or picking a key here always replaces the
 * trigger's whole key list with that one key.
 *
 * Every change here writes straight into the draft as it happens (there is no separate "cancel");
 * [onDismiss] just closes the sheet, wired to both the close button and "Save combo".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerPickerSheet(
    pressType: TriggerPressType,
    keyCodes: List<Int>,
    savedKeys: List<PhysicalKeyOption>,
    isRecordingKey: Boolean,
    onDismiss: () -> Unit,
    onSelectKey: (Int) -> Unit,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSetPressType: (TriggerPressType) -> Unit,
    // Null for the suggestion-tile flow, which has no automation counterpart to switch to.
    onSwitchToAutomation: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val res = LocalContext.current.resources
    val useAutomationLabel = stringResource(R.string.trigger_picker_use_automation)
    // Recording is global coordinator state, so every way out of this sheet — Back, tapping
    // outside, the X, Save, or the whole editor closing — has to disarm it, not just the stop
    // button. Leaving composition is the one path all of them pass through.
    DisposableEffect(Unit) { onDispose { onCancelRecording() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.trigger_picker_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(
                        stringResource(R.string.trigger_picker_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(InnerShape)
                        .background(colors.surfaceVariant)
                        .clickable(onClickLabel = stringResource(R.string.common_close), onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }

            PressTypeSegmented(pressType, onSetPressType)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (keyCodes.isEmpty()) {
                    KeyChip(stringResource(R.string.trigger_picker_no_key), filled = false)
                } else {
                    keyCodes.forEach { code -> KeyChip(getReadableKeyName(res, code), filled = true) }
                }
            }

            if (savedKeys.isNotEmpty()) {
                Column {
                    Text(stringResource(R.string.trigger_picker_saved_key), style = SectionLabel, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    // Every saved key, scrolling sideways. Taking the first three silently hid the
                    // rest with nothing on screen to say there were more.
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        savedKeys.forEach { key ->
                            SavedKeyChip(
                                key = key,
                                enabled = !isRecordingKey,
                                onClick = { onSelectKey(key.keyCode) },
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RecordButton(
                    isRecordingKey = isRecordingKey,
                    onClick = if (isRecordingKey) onCancelRecording else onStartRecording,
                )
                Text(
                    stringResource(if (isRecordingKey) R.string.trigger_picker_listening else R.string.trigger_picker_tap_to_record),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isRecordingKey) colors.primary else colors.onSurfaceVariant,
                )
            }

            Button(
                onClick = onDismiss,
                enabled = keyCodes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("save_trigger_button"),
                shape = InnerShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            ) {
                Text(stringResource(R.string.trigger_picker_save_combo), style = MaterialTheme.typography.labelLarge)
            }

            if (onSwitchToAutomation != null) {
                Text(
                    stringResource(R.string.trigger_picker_use_automation),
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouchTarget)
                        .clickable(onClickLabel = useAutomationLabel, onClick = onSwitchToAutomation)
                        .padding(vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Single-bar Single/Double/Long control, matching the redesign's segmented control exactly
 *  rather than [PressTypeChips]'s separated filter chips. */
@Composable
private fun PressTypeSegmented(selected: TriggerPressType, onSelect: (TriggerPressType) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(colors.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TriggerPressType.entries.forEach { type ->
            val isSelected = type == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(InnerShape)
                    .background(if (isSelected) colors.primary else Color.Transparent)
                    .clickable(onClickLabel = stringResource(type.titleRes), onClick = { onSelect(type) })
                    .padding(vertical = 9.dp)
                    .testTag("press_type_${type.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(type.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KeyChip(label: String, filled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        Modifier
            .clip(InnerShape)
            .background(if (filled) colors.surface else colors.surfaceVariant.copy(alpha = .4f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.titleSmall,
        color = if (filled) colors.onSurface else colors.onSurfaceVariant,
    )
}

@Composable
private fun SavedKeyChip(key: PhysicalKeyOption, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.heightIn(min = MinTouchTarget), contentAlignment = Alignment.Center) {
        Text(
            key.shortLabel,
            Modifier
                .clip(InnerShape)
                .clickable(enabled = enabled, onClickLabel = stringResource(R.string.trigger_picker_use_key, key.shortLabel), onClick = onClick)
                .testTag("trigger_option_${key.keyCode}")
                .background(colors.surfaceVariant.copy(alpha = .5f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = .4f),
        )
    }
}

/** The record affordance: a filled dot while idle, a rounded stop-square while listening. */
@Composable
private fun RecordButton(isRecordingKey: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (isRecordingKey) colors.error else colors.primary)
            .clickable(onClick = onClick)
            .testTag("record_key_button"),
        contentAlignment = Alignment.Center,
    ) {
        if (isRecordingKey) {
            // Glyph, not a container: at 16dp the tile radius would round this into the very circle
            // it has to read as different from, so the stop square keeps a radius of its own.
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(colors.onError))
        } else {
            Box(Modifier.size(18.dp).clip(CircleShape).background(colors.onPrimary))
        }
    }
}
