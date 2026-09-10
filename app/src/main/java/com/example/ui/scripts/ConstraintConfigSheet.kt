package com.example.ui.scripts

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ConstraintEditorForms
import com.example.data.EditorField
import com.example.data.InstalledApp
import com.example.data.ScriptConstraintCatalog
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.theme.SectionLabel
import io.github.nicholasarda.keysnap.R
import java.time.format.TextStyle

/**
 * Collects one constraint's parameters, and reopens for editing from its row in the editor. Every
 * decision comes from [ConstraintEditorForms]; the per-field composables are shared with
 * [ActionConfigSheet] so the two sheets cannot drift apart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstraintConfigSheet(
    constraintType: String,
    initialParams: Map<String, String>,
    isEdit: Boolean,
    installedApps: List<InstalledApp>,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val form = ConstraintEditorForms.form(constraintType) ?: return
    val context = LocalContext.current
    val typeTitle = ScriptConstraintCatalog.descriptor(constraintType)?.let { stringResource(it.titleRes) } ?: constraintType

    val values = remember(constraintType, initialParams) {
        mutableStateMapOf<String, String>().apply {
            putAll(ConstraintEditorForms.defaults(form))
            putAll(initialParams.filterValues { it.isNotBlank() })
        }
    }
    var showAppPickerFor by remember { mutableStateOf<String?>(null) }
    val complete = ConstraintEditorForms.isComplete(constraintType, values.toMap())
    val sheetMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .heightIn(max = sheetMaxHeight)
                .verticalScroll(rememberScrollState())
                .testTag("constraint_config_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.constraint_picker_configure_title), style = SectionLabel, color = colors.onSurfaceVariant)
            Text(typeTitle, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            form.helperRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }

            PermissionHints(constraintType, values["ssid"].orEmpty(), context)

            form.fields.forEach { field ->
                when (field) {
                    is EditorField.Text -> TextFieldRow(field, values)
                    is EditorField.Number -> NumberFieldRow(field, values)
                    is EditorField.Choice -> ChoiceFieldRow(field, values)
                    is EditorField.AppPick -> {
                        AppFieldRow(field, values, installedApps) { showAppPickerFor = field.key }
                        // Add stays disabled until an app is picked; without this the button just
                        // looks broken.
                        if (values[field.key].isNullOrBlank()) {
                            Text(
                                stringResource(R.string.constraint_config_choose_app),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    is EditorField.BluetoothDevicePick -> BluetoothDeviceFieldRow(field, values)
                    is EditorField.TimeRange -> TimeRangeFieldRow(field, values)
                    is EditorField.DaySet -> DaySetFieldRow(field, values)
                    // Never declared by a constraint form; [ConstraintEditorFormTest] guards this.
                    is EditorField.KeyCodePick, is EditorField.Point, is EditorField.TwoPoints -> Unit
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("constraint_config_cancel")) {
                    Text(stringResource(R.string.common_cancel), color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(values.toMap().filterValues { it.isNotBlank() }) },
                    enabled = complete,
                    shape = InnerShape,
                    modifier = Modifier.testTag("constraint_config_confirm"),
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

/** Informational only: the evaluator degrades without these grants, but Save never waits on them. */
@Composable
private fun PermissionHints(constraintType: String, ssid: String, context: Context) {
    when {
        constraintType.startsWith("app_") -> {
            var granted by remember { mutableStateOf(hasUsageAccess(context)) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                granted = hasUsageAccess(context)
            }
            if (!granted) {
                PermissionHint(stringResource(R.string.constraint_needs_usage_access)) {
                    launcher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        }
        constraintType.endsWith("_wifi_network") && ssid.isNotBlank() -> {
            var granted by remember { mutableStateOf(hasFineLocation(context)) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
            if (!granted) {
                PermissionHint(stringResource(R.string.constraint_needs_location)) {
                    launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }
    }
}

@Composable
private fun PermissionHint(text: String, onGrant: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outline.copy(alpha = .6f), InnerShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        OutlinedButton(
            onClick = onGrant,
            shape = InnerShape,
            modifier = Modifier.testTag("constraint_grant_permission"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
        ) {
            Text(stringResource(R.string.constraint_grant_permission))
        }
    }
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
    val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun hasFineLocation(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun hasBluetoothConnect(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** Address to display name. Only called once [hasBluetoothConnect] is true. */
@SuppressLint("MissingPermission")
private fun bondedDevices(context: Context): List<Pair<String, String>> {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return runCatching { adapter.bondedDevices.map { it.address to (it.name ?: it.address) } }.getOrDefault(emptyList())
}

@Composable
internal fun BluetoothDeviceFieldRow(field: EditorField.BluetoothDevicePick, values: MutableMap<String, String>) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasBluetoothConnect(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(field.labelRes), style = SectionLabel, color = colors.onSurfaceVariant)
        if (!hasPermission) {
            Button(
                onClick = { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                shape = InnerShape,
                modifier = Modifier.testTag("config_bluetooth_permission"),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            ) {
                Text(stringResource(R.string.constraint_grant_permission))
            }
            return@Column
        }
        val devices = remember(hasPermission) { bondedDevices(context) }
        if (devices.isEmpty()) {
            Text(stringResource(R.string.bluetooth_no_paired_devices), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            return@Column
        }
        val selected = values[field.addressKey]
        Column(Modifier.fillMaxWidth().border(1.dp, colors.outline.copy(alpha = .6f), InnerShape)) {
            devices.forEach { (address, name) ->
                val isSelected = address == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouchTarget)
                        .clickable {
                            values[field.addressKey] = address
                            values[field.nameKey] = name
                        }
                        .testTag("config_bluetooth_device_$address")
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name,
                        Modifier.weight(1f),
                        color = if (isSelected) colors.primary else colors.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isSelected) Icon(Icons.Filled.Check, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeRangeFieldRow(field: EditorField.TimeRange, values: MutableMap<String, String>) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    // Which key the open picker writes to; null while closed.
    var editingKey by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(field.startKey to field.startLabelRes, field.endKey to field.endLabelRes).forEach { (key, labelRes) ->
            val value = values[key].orEmpty()
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.outline.copy(alpha = .6f), InnerShape)
                    .clickable { editingKey = key }
                    .testTag("config_time_$key")
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(labelRes), Modifier.weight(1f), style = SectionLabel, color = colors.onSurfaceVariant)
                Text(ConstraintEditorForms.formatTime(value, locale), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    editingKey?.let { key ->
        val (hour, minute) = ConstraintEditorForms.parseTime(values[key].orEmpty()) ?: (0 to 0)
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { editingKey = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        values[key] = ConstraintEditorForms.formatTime(state.hour, state.minute)
                        editingKey = null
                    },
                    modifier = Modifier.testTag("config_time_confirm"),
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { editingKey = null }) { Text(stringResource(R.string.common_cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
internal fun DaySetFieldRow(field: EditorField.DaySet, values: MutableMap<String, String>) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val chosen = ConstraintEditorForms.parseDays(values[field.key].orEmpty())
    val days = remember(locale) { ConstraintEditorForms.weekOrder(locale) }
    Column {
        Text(stringResource(field.labelRes), style = SectionLabel, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days.size) { index ->
                val day = days[index]
                val selected = day in chosen
                FilterChip(
                    selected = selected,
                    onClick = { values[field.key] = ConstraintEditorForms.formatDays(if (selected) chosen - day else chosen + day) },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelMedium) },
                    border = null,
                    modifier = Modifier.testTag("config_day_${day.name.lowercase()}"),
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
