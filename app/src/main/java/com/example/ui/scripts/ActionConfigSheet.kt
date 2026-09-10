package com.example.ui.scripts

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.data.ActionEditorForms
import com.example.data.EditorField
import com.example.data.InstalledApp
import com.example.data.NOTIFICATION_POLICY_BADGE
import com.example.data.WRITE_SETTINGS_BADGE
import com.example.data.ScriptActionCatalog
import com.example.data.getReadableKeyName
import com.example.service.PickedPoints
import com.example.service.PointPickMode
import com.example.service.ScreenPointPicker
import com.example.service.TwoPointGesture
import com.example.ui.components.InnerShape
import com.example.ui.theme.SectionLabel
import io.github.nicholasarda.keysnap.R

/**
 * Collects one action's parameters before it joins the Do chain, and reopens for editing from the
 * chain row's gear. Every decision — which fields are visible, whether the form is complete, what
 * the defaults are — comes from [ActionEditorForms], which is unit-tested; this file only renders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionConfigSheet(
    actionType: String,
    initialParams: Map<String, String>,
    isEdit: Boolean,
    isRecordingKey: Boolean,
    installedApps: List<InstalledApp>,
    canDrawOverlays: Boolean,
    pendingPoints: PickedPoints?,
    onConsumePoints: () -> Unit,
    onPickPoints: (PointPickMode, TwoPointGesture) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onRequestNotificationPolicyAccess: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Same as the trigger sheet: key recording is global, so dismissing this sheet by any route
    // has to disarm it.
    DisposableEffect(Unit) { onDispose { onCancelRecording() } }
    val form = ActionEditorForms.forType(actionType) ?: return
    val title = ScriptActionCatalog.descriptor(actionType)?.let { stringResource(it.titleRes) } ?: actionType

    // Keyed on the params too: editing two different tap_screen rows in a row must not reuse
    // the first row's values map.
    val values = remember(actionType, initialParams) {
        mutableStateMapOf<String, String>().apply {
            putAll(ActionEditorForms.defaults(form))
            putAll(initialParams.filterValues { it.isNotBlank() })
        }
    }
    // Which field asked for a point, so the returning overlay result lands in the right params.
    var awaitingPointKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAppPickerFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingPoints) {
        val picked = pendingPoints ?: return@LaunchedEffect
        val keys = awaitingPointKeys
        if (keys.size >= 2) {
            values[keys[0]] = picked.a.x.toString()
            values[keys[1]] = picked.a.y.toString()
        }
        val second = picked.b
        if (keys.size == 4 && second != null) {
            values[keys[2]] = second.x.toString()
            values[keys[3]] = second.y.toString()
        }
        awaitingPointKeys = emptyList()
        onConsumePoints()
    }

    val snapshot = values.toMap()
    val visible = ActionEditorForms.visibleFields(form, snapshot)
    val complete = ActionEditorForms.isComplete(form, snapshot)

    // The Direction chip sits outside the overlay's own windows, so it stays tappable while the
    // overlay is up — the connector's arrows must track that live, not just whatever direction was
    // selected when "Pick on screen" was tapped.
    val twoPointGesture = when {
        actionType == "pinch_screen" && snapshot["pinchType"] == "out" -> TwoPointGesture.PINCH_OUT
        actionType == "pinch_screen" -> TwoPointGesture.PINCH_IN
        actionType == "swipe_screen" -> TwoPointGesture.SWIPE
        else -> TwoPointGesture.NONE
    }
    LaunchedEffect(twoPointGesture) { ScreenPointPicker.setGesture(twoPointGesture) }
    // Sized from the device: a fixed 560dp cap ran past the bottom of a short or landscape screen.
    val sheetMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .heightIn(max = sheetMaxHeight)
                .verticalScroll(rememberScrollState())
                .testTag("action_config_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            form.helperRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            form.requirementRes?.let {
                val onClick = when (it) {
                    NOTIFICATION_POLICY_BADGE -> onRequestNotificationPolicyAccess
                    WRITE_SETTINGS_BADGE -> onRequestWriteSettings
                    else -> null
                }
                RequirementBadge(stringResource(it), onClick)
            }

            visible.forEach { field ->
                when (field) {
                    is EditorField.Text -> TextFieldRow(field, values)
                    is EditorField.Number -> NumberFieldRow(field, values)
                    is EditorField.Choice -> ChoiceFieldRow(field, values)
                    is EditorField.KeyCodePick -> KeyCodeFieldRow(field, values, isRecordingKey, onStartRecording, onCancelRecording)
                    is EditorField.AppPick -> AppFieldRow(field, values, installedApps) { showAppPickerFor = field.key }
                    is EditorField.Point -> PointFieldRow(
                        label = stringResource(field.labelRes),
                        pointKeys = listOf(field.xKey, field.yKey),
                        values = values,
                        canDrawOverlays = canDrawOverlays,
                        onPick = {
                            awaitingPointKeys = listOf(field.xKey, field.yKey)
                            onPickPoints(PointPickMode.SINGLE, TwoPointGesture.NONE)
                        },
                        onRequestPermission = onRequestOverlayPermission,
                    )
                    is EditorField.TwoPoints -> {
                        val keys = listOf(field.x1Key, field.y1Key, field.x2Key, field.y2Key)
                        PointFieldRow(
                            label = "${stringResource(field.labelARes)} → ${stringResource(field.labelBRes)}",
                            pointKeys = keys,
                            values = values,
                            canDrawOverlays = canDrawOverlays,
                            onPick = {
                                awaitingPointKeys = keys
                                onPickPoints(PointPickMode.TWO_POINTS, twoPointGesture)
                            },
                            onRequestPermission = onRequestOverlayPermission,
                        )
                    }
                    // Constraint-only fields; listed so the sealed `when` stays exhaustive.
                    is EditorField.BluetoothDevicePick -> BluetoothDeviceFieldRow(field, values)
                    is EditorField.TimeRange -> TimeRangeFieldRow(field, values)
                    is EditorField.DaySet -> DaySetFieldRow(field, values)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("action_config_cancel")) {
                    Text(stringResource(R.string.common_cancel), color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(values.toMap().filterValues { it.isNotBlank() }) },
                    enabled = complete,
                    shape = InnerShape,
                    modifier = Modifier.testTag("action_config_confirm"),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                ) {
                    Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (isEdit) R.string.common_save else R.string.common_add), style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    showAppPickerFor?.let { key ->
        AppPickerSheet(
            apps = installedApps,
            selected = values[key],
            onSelect = {
                values[key] = it
                showAppPickerFor = null
            },
            onDismiss = { showAppPickerFor = null },
        )
    }
}

@Composable
private fun RequirementBadge(text: String, onClick: (() -> Unit)? = null, tag: String = "requirement_badge") {
    val colors = MaterialTheme.colorScheme
    Text(
        if (onClick != null) stringResource(R.string.badge_tap_to_grant, text) else text,
        Modifier
            .background(colors.surfaceVariant, InnerShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(tag),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
    )
}

@Composable
internal fun TextFieldRow(field: EditorField.Text, values: MutableMap<String, String>) {
    val colors = MaterialTheme.colorScheme
    Column {
        OutlinedTextField(
            value = values[field.key].orEmpty(),
            onValueChange = { values[field.key] = it },
            modifier = Modifier.fillMaxWidth().testTag("config_field_${field.key}"),
            label = { Text(stringResource(field.labelRes)) },
            placeholder = { field.hintRes?.let { Text(stringResource(it)) } },
            singleLine = !field.multiline,
            minLines = if (field.multiline) 3 else 1,
        )
        field.helperRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
}

@Composable
internal fun NumberFieldRow(field: EditorField.Number, values: MutableMap<String, String>) {
    OutlinedTextField(
        value = values[field.key].orEmpty(),
        // Digits only; the executor and bridge both clamp, so out-of-range text cannot misfire.
        onValueChange = { input -> values[field.key] = input.filter { it.isDigit() }.take(6) },
        modifier = Modifier.fillMaxWidth().testTag("config_field_${field.key}"),
        label = {
            val label = stringResource(field.labelRes)
            Text(if (field.suffix.isEmpty()) label else "$label (${field.suffix})")
        },
        placeholder = { Text(field.default.toString()) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text("${field.min}–${field.max}") },
    )
}

@Composable
internal fun ChoiceFieldRow(field: EditorField.Choice, values: MutableMap<String, String>) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(stringResource(field.labelRes), style = SectionLabel, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(field.options.size) { index ->
                val (value, labelRes) = field.options[index]
                FilterChip(
                    selected = values[field.key] == value,
                    onClick = { values[field.key] = value },
                    label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium) },
                    border = null,
                    modifier = Modifier.testTag("config_choice_${field.key}_$value"),
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
}

@Composable
private fun KeyCodeFieldRow(
    field: EditorField.KeyCodePick,
    values: MutableMap<String, String>,
    isRecordingKey: Boolean,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val current = values[field.key]?.toIntOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(field.labelRes), style = SectionLabel, color = colors.onSurfaceVariant)
        if (current != null) {
            Text(getReadableKeyName(LocalContext.current.resources, current), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        }
        OutlinedTextField(
            value = values[field.key].orEmpty(),
            onValueChange = { input -> values[field.key] = input.filter { it.isDigit() }.take(5) },
            modifier = Modifier.fillMaxWidth().testTag("config_field_${field.key}"),
            label = { Text(stringResource(R.string.action_config_enter_key_code)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        TextButton(
            onClick = if (isRecordingKey) onCancelRecording else onStartRecording,
            modifier = Modifier.testTag("record_key_button"),
        ) {
            Text(stringResource(if (isRecordingKey) R.string.action_config_cancel_recording else R.string.action_config_record_key), color = colors.primary)
        }
    }
}

@Composable
internal fun AppFieldRow(
    field: EditorField.AppPick,
    values: MutableMap<String, String>,
    installedApps: List<InstalledApp>,
    onOpenPicker: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val selected = values[field.key].orEmpty()
    val label = installedApps.firstOrNull { it.packageName == selected }?.label
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(field.labelRes), style = SectionLabel, color = colors.onSurfaceVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.outline.copy(alpha = .6f), InnerShape)
                .clickable(onClick = onOpenPicker)
                .testTag("config_app_pick_${field.key}")
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label ?: selected.ifEmpty { stringResource(R.string.action_config_choose_app) },
                Modifier.weight(1f),
                color = if (selected.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
            )
        }
        OutlinedTextField(
            value = selected,
            onValueChange = { values[field.key] = it.trim() },
            modifier = Modifier.fillMaxWidth().testTag("config_field_${field.key}"),
            // Packages with no launcher activity never appear in the picker, so they are typed.
            label = { Text(stringResource(R.string.action_config_enter_package)) },
            singleLine = true,
        )
    }
}

/**
 * Almost nobody types these coordinates in by hand — "Pick on screen" is the real control, so it
 * stays full-size and prominent. The picked values are shown only as a small, muted, read-only
 * readout underneath: no border, no input focus, nothing to tap.
 */
@Composable
private fun PointFieldRow(
    label: String,
    pointKeys: List<String>,
    values: MutableMap<String, String>,
    canDrawOverlays: Boolean,
    onPick: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = SectionLabel, color = colors.onSurfaceVariant)
        TextButton(
            onClick = if (canDrawOverlays) onPick else onRequestPermission,
            modifier = Modifier.testTag("config_pick_point_${pointKeys.first()}"),
        ) {
            Icon(Icons.Outlined.MyLocation, null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (canDrawOverlays) R.string.action_config_pick_on_screen else R.string.action_config_allow_overlay), color = colors.primary)
        }
        val notSet = stringResource(R.string.action_config_point_not_set)
        Text(
            text = pointKeys.chunked(2).joinToString("   ") { (x, y) ->
                val xv = values[x].orEmpty()
                val yv = values[y].orEmpty()
                if (xv.isEmpty() || yv.isEmpty()) notSet else "$xv, $yv"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp).testTag("config_point_readout_${pointKeys.first()}"),
        )
    }
}
