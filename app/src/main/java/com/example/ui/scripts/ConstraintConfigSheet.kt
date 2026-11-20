package com.example.ui.scripts

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.example.service.PlacePick
import com.example.service.PlaceCondition
import android.text.format.DateUtils
import androidx.compose.runtime.produceState
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import com.example.data.Place
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.InputChip
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ConstraintEditorForms
import com.example.data.EditorField
import com.example.data.InstalledApp
import com.example.data.InstalledAppsRepository
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.service.CalendarCondition
import com.example.service.NotificationEventListener
import com.example.service.TimeRules
import com.example.service.hasUsageAccess
import com.example.data.AppLabelResolver
import com.example.ui.components.ConsoleSwitch
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.home.AutoLaunchCard
import com.example.ui.home.hasAutoLaunchSwitch
import com.example.ui.home.openAutoLaunchSettings
import com.example.ui.theme.SectionLabel
import io.github.nicholasarda.keysnap.R
import java.time.format.TextStyle
import kotlin.math.roundToInt

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
    val keyboardPick = form.fields.any { it is EditorField.AppPick && it.keyboardOnly }
    val keyboardApps by produceState(emptyList<InstalledApp>(), keyboardPick) {
        if (keyboardPick) value = InstalledAppsRepository(context).keyboardApps()
    }
    val complete = ConstraintEditorForms.isComplete(constraintType, values.toMap())
    val sheetMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .heightIn(max = sheetMaxHeight)
                .testTag("constraint_config_sheet"),
        ) {
            // Only the fields scroll; Cancel/Add stay pinned, so a tall form (At a place) never shows half a button.
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .nestedScroll(rememberSheetContentScroll())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.constraint_picker_configure_title), style = SectionLabel, color = colors.onSurfaceVariant)
                Text(typeTitle, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                if (constraintType in ScriptConstraintCatalog.EXPERIMENTAL) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExperimentalBadge()
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.place_experimental_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                if (ScriptConstraintCatalog.supportsNegation(constraintType)) {
                    ConstraintNegationToggle(
                        values["negate"] == "true",
                        { negated -> if (negated) values["negate"] = "true" else values.remove("negate") },
                        Modifier.testTag("constraint_config_negation"),
                    )
                }
                form.helperRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }

                if (constraintType == ScriptConstraintCatalog.AT_PLACE) PlaceRow(values, context)

                PermissionHints(constraintType, values["ssid"].orEmpty(), context)

                form.fields.forEach { field ->
                    when (field) {
                        is EditorField.Text -> TextFieldRow(field, values)
                        is EditorField.Number -> NumberFieldRow(field, values)
                        is EditorField.Choice -> ChoiceFieldRow(field, values)
                        is EditorField.AppPick -> {
                            AppFieldRow(field, values) { showAppPickerFor = field.key }
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
                        is EditorField.Time -> TimeFieldRows(listOf(field.key to field.labelRes), values)
                        is EditorField.Interval -> IntervalFieldRow(field, values)
                        is EditorField.AppList -> AnimatedVisibility(field.visibleWhen?.let { values[it.first] in it.second } ?: true) {
                            AppListFieldRow(field, values) { showAppPickerFor = field.key }
                        }
                        is EditorField.Switch -> ConsoleSwitch(stringResource(field.labelRes), values[field.key] == "true") { values[field.key] = it.toString() }
                        // Never declared by a constraint form; [ConstraintEditorFormTest] guards this.
                        is EditorField.KeyCodePick, is EditorField.Point, is EditorField.TwoPoints, is EditorField.ImagePick,
                        is EditorField.ContactPick, is EditorField.AppShortcutPick, EditorField.RingerPattern -> Unit
                    }
                }
                if (constraintType == ScriptConstraintCatalog.AT_TIME && values["negate"] != "true") FirstRunHint(ScriptConstraint(constraintType, values.toMap()))
                // Android checks a closed app's proximity alert at most every 30 min; say so up front.
                AnimatedVisibility(constraintType == ScriptConstraintCatalog.AT_PLACE && values["mode"] != Place.MODE_FAST) {
                    Text(stringResource(R.string.place_battery_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                AnimatedVisibility(constraintType == ScriptConstraintCatalog.AT_PLACE && values["mode"] == Place.MODE_FAST) {
                    // As in the automation notices: the label sits above, so a blank field shows the words it falls back to.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.place_fast_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        listOf(
                            Triple(Place.NOTICE_TITLE, R.string.notice_field_title, R.string.place_fast_notification_title),
                            Triple(Place.NOTICE_TEXT, R.string.notice_field_text, R.string.place_fast_notification_text),
                        ).forEach { (key, label, default) ->
                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                            OutlinedTextField(
                                value = values[key].orEmpty(),
                                onValueChange = { values[key] = it.take(if (key == Place.NOTICE_TITLE) 60 else 120) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("place_$key"),
                                placeholder = { Text(stringResource(default)) },
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("constraint_config_cancel")) {
                    Text(stringResource(R.string.common_cancel), color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        // Typed values clamp on Save; focus loss rarely fires in touch mode.
                        form.fields.filterIsInstance<EditorField.Interval>().forEach { f ->
                            values[f.valueKey] = ConstraintEditorForms.clampInterval(values[f.valueKey].orEmpty(), values[f.unitKey].orEmpty())
                        }
                        onConfirm(values.toMap().filterValues { it.isNotBlank() })
                    },
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
            apps = if (keyboardPick) keyboardApps else installedApps,
            // A list adds one app per pick; marking one of its apps "selected" would read as the whole value.
            selected = if (form.fields.any { it is EditorField.AppList && it.key == key }) null else values[key],
            onSelect = { pkg ->
                if (form.fields.any { it is EditorField.AppList && it.key == key }) {
                    val list = values[key].orEmpty().split(',').filter(String::isNotBlank)
                    if (pkg !in list) values[key] = (list + pkg).joinToString(",")
                } else {
                    values[key] = pkg
                }
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
        constraintType == ScriptConstraintCatalog.NOTIFICATION_RECEIVED || constraintType == ScriptConstraintCatalog.NOTIFICATION_WAITING -> {
            var granted by remember { mutableStateOf(NotificationEventListener.isAccessGranted(context)) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                granted = NotificationEventListener.isAccessGranted(context)
            }
            // The hint text is the prominent disclosure Play expects before the grant screen.
            if (!granted) {
                PermissionHint(stringResource(R.string.constraint_needs_notification_access)) {
                    runCatching { launcher.launch(NotificationEventListener.accessSettingsIntent(context)) }
                        .onFailure { launcher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                }
            }
        }
        constraintType == "app_playing_media" || constraintType == "app_not_playing_media" -> {
            var granted by remember { mutableStateOf(NotificationEventListener.isAccessGranted(context)) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                granted = NotificationEventListener.isAccessGranted(context)
            }
            if (!granted) {
                PermissionHint(stringResource(R.string.constraint_needs_media_access)) {
                    runCatching { launcher.launch(NotificationEventListener.accessSettingsIntent(context)) }
                        .onFailure { launcher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                }
            }
        }
        constraintType == ScriptConstraintCatalog.CALENDAR_EVENT_ACTIVE -> {
            var granted by remember {
                mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
            }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
            if (!granted) {
                PermissionHint(stringResource(R.string.constraint_needs_calendar)) {
                    launcher.launch(Manifest.permission.READ_CALENDAR)
                }
            }
            ExactAlarmHint(R.string.constraint_needs_exact_alarm, context)
        }
        constraintType == ScriptConstraintCatalog.AT_PLACE -> {
            var fine by remember { mutableStateOf(hasFineLocation(context)) }
            var background by remember { mutableStateOf(hasBackgroundLocation(context)) }
            val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                fine = hasFineLocation(context)
                background = hasBackgroundLocation(context)
            }
            // Android 11+ answers this one with the app's location page, where "Allow all the time" lives.
            val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { background = it }
            // Each hint is the prominent disclosure Play expects before Android's own prompt.
            if (!fine) {
                PermissionHint(stringResource(R.string.constraint_needs_location_place)) {
                    fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            } else if (!background) {
                PermissionHint(stringResource(R.string.constraint_needs_background_location)) {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            } else {
                AutoLaunchHint(context)
            }
        }
        constraintType == ScriptConstraintCatalog.AT_TIME || constraintType == ScriptConstraintCatalog.INTERVAL ->
            ExactAlarmHint(R.string.time_needs_exact_alarm, context)
        constraintType.startsWith("app_") || constraintType == "no_app_open" -> {
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
            // Android 12+ ignores a fine-only request; fine must be asked alongside coarse.
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            }
            if (!granted) {
                PermissionHint(stringResource(R.string.constraint_needs_location)) {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
        }
    }
}

@Composable
private fun ExactAlarmHint(@StringRes message: Int, context: Context) {
    val alarms = remember { context.getSystemService(AlarmManager::class.java) }
    var exact by remember { mutableStateOf(alarms == null || CalendarCondition.canScheduleExact(alarms)) }
    val exactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        exact = alarms == null || CalendarCondition.canScheduleExact(alarms)
    }
    // Only reachable on Android 12+: canScheduleExact is always true below it.
    if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PermissionHint(stringResource(message)) {
            exactLauncher.launch(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
            )
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

/**
 * Home's Auto launch row, in the place sheet too. Without that switch ColorOS (and phones like it)
 * drops a closed KeySnap's recheck alarms and its boot restart, so place watching stops until the
 * app is opened. Closing it here closes Home's row as well: one answer for both.
 */
@Composable
private fun AutoLaunchHint(context: Context) {
    if (!hasAutoLaunchSwitch(Build.MANUFACTURER)) return
    val prefs = remember(context) { context.getSharedPreferences("keysnap_setup", Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean("auto_launch_dismissed", false)) }
    AnimatedVisibility(!dismissed, exit = fadeOut() + shrinkVertically()) {
        AutoLaunchCard(
            stoppedBefore = false,
            onTurnOn = { openAutoLaunchSettings(context, Build.MANUFACTURER) },
            onClose = {
                dismissed = true
                prefs.edit().putBoolean("auto_launch_dismissed", true).apply()
            },
        )
    }
}

private fun hasFineLocation(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun hasBackgroundLocation(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Where the place comes from: before one is picked, why the map opens in the browser (what the
 * user gains, what it costs) and the button; after, the picked circle and a way back to the map.
 * The map page's answer arrives through [PlacePick.result] while this sheet is open.
 */
@Composable
private fun PlaceRow(values: MutableMap<String, String>, context: Context) {
    val colors = MaterialTheme.colorScheme
    val picked by PlacePick.result.collectAsState()
    DisposableEffect(Unit) { onDispose { PlacePick.cancel() } }
    LaunchedEffect(picked) {
        picked?.let {
            values.putAll(it)
            PlacePick.result.value = null
        }
    }
    val place = Place.parse(values)
    val card = Modifier.fillMaxWidth().border(1.dp, colors.outline.copy(alpha = .6f), InnerShape).padding(12.dp)
    AnimatedContent(place, label = "placeRow") { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shown == null) {
                Column(card, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.place_why_title), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    PlaceFact(true, R.string.place_why_private)
                    PlaceFact(true, R.string.place_why_no_sdk)
                    PlaceFact(true, R.string.place_why_small)
                    PlaceFact(false, R.string.place_why_internet)
                    PlaceTable()
                }
                Button(
                    onClick = { PlacePick.open(context, null) },
                    shape = InnerShape,
                    modifier = Modifier.fillMaxWidth().testTag("place_open_map"),
                ) { Text(stringResource(R.string.place_open_map)) }
            } else {
                Row(card, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Place, null, tint = colors.primary)
                    Column(Modifier.weight(1f)) {
                        Text(String.format(java.util.Locale.ROOT, "%.5f, %.5f", shown.lat, shown.lng), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                        Text(stringResource(R.string.place_circle, shown.radius), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                if (shown.radius < Place.MIN_BATTERY_RADIUS_M && values["mode"] != Place.MODE_FAST) {
                    Text(stringResource(R.string.place_battery_min), style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
                PlaceStatusLine(values.toMap())
                TextButton(onClick = { PlacePick.open(context, shown) }, modifier = Modifier.testTag("place_change_on_map")) {
                    Text(stringResource(R.string.place_change_on_map))
                }
            }
        }
    }
}

/**
 * What KeySnap believes right now and when it last checked, or why it cannot know (permission or
 * location off). Shown in the sheet and on the condition card; re-read every 30 s while visible.
 */
@Composable
internal fun PlaceStatusLine(params: Map<String, String>) {
    val context = LocalContext.current
    // Re-read every 30 s, and on coming back from Settings or the permission prompt.
    val resumed by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val reading by produceState(PlaceCondition.reading(context, params), params, resumed) {
        while (true) {
            value = PlaceCondition.reading(context, params)
            delay(30_000)
        }
    }
    val colors = MaterialTheme.colorScheme
    val (text, bad) = when (reading.status) {
        PlaceCondition.Status.NO_PERMISSION -> R.string.place_status_no_permission to true
        PlaceCondition.Status.NO_BACKGROUND -> R.string.place_status_no_background to true
        PlaceCondition.Status.LOCATION_OFF -> R.string.place_status_location_off to true
        PlaceCondition.Status.INSIDE -> R.string.place_status_inside to false
        PlaceCondition.Status.OUTSIDE -> R.string.place_status_outside to false
        PlaceCondition.Status.UNKNOWN -> R.string.place_status_unknown to false
    }
    val checked = reading.checkedAtMs.takeIf { it > 0 && !bad }?.let {
        stringResource(
            R.string.place_status_checked,
            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
        )
    }
    Text(
        listOfNotNull(stringResource(text), checked).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = if (bad) colors.error else colors.onSurfaceVariant,
        modifier = Modifier.testTag("place_status"),
    )
}

@Composable
private fun PlaceFact(good: Boolean, @StringRes text: Int) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (good) Icons.Filled.Check else Icons.Filled.Remove,
            null,
            tint = if (good) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

/** The trade the user is shown before the browser opens: a map inside the app against this page. */
@Composable
private fun PlaceTable() {
    val colors = MaterialTheme.colorScheme
    val rows = listOf(
        Triple(R.string.place_table_tracks, stringResource(R.string.place_table_yes), stringResource(R.string.place_table_no)),
        Triple(R.string.place_table_size, "+12 MB", "+0 MB"),
        Triple(R.string.place_table_data, stringResource(R.string.place_table_shared), stringResource(R.string.place_table_private)),
    )
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Spacer(Modifier.weight(1.3f))
            Text(stringResource(R.string.place_table_in_app), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(stringResource(R.string.place_table_our_way), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        rows.forEach { (label, inApp, ours) ->
            Row {
                Text(stringResource(label), Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
                Text(inApp, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.error)
                Text(ours, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun hasBluetoothConnect(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** Address to display name. Only called once [hasBluetoothConnect] is true. */
@SuppressLint("MissingPermission")
private fun bondedDevices(context: Context): List<Pair<String, String>> {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return runCatching { adapter.bondedDevices.map { it.address to (it.name ?: it.address) } }.getOrDefault(emptyList())
}

/** "Launch app shortcut": the picked app's shortcuts, read by the Advanced Mode bridge. */
@Composable
internal fun AppShortcutFieldRow(field: EditorField.AppShortcutPick, values: MutableMap<String, String>) {
    val colors = MaterialTheme.colorScheme
    val pkg = values[field.appKey].orEmpty()
    if (pkg.isBlank()) return
    var loaded by remember(pkg) { mutableStateOf(false) }
    var shortcuts by remember(pkg) { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(pkg) {
        shortcuts = com.example.service.bridge.BridgeController.appShortcuts(pkg)
        loaded = true
        // Another app was picked: its old shortcut id means nothing here.
        shortcuts?.let { list -> if (list.none { it.first == values[field.idKey] }) { values.remove(field.idKey); values.remove(field.labelKey) } }
    }
    if (!loaded) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(field.labelRes), style = SectionLabel, color = colors.onSurfaceVariant)
        val list = shortcuts
        if (list == null || list.isEmpty()) {
            Text(
                stringResource(if (list == null) R.string.shortcut_needs_advanced else R.string.shortcut_none),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            return@Column
        }
        val selected = values[field.idKey]
        Column(Modifier.fillMaxWidth().border(1.dp, colors.outline.copy(alpha = .6f), InnerShape)) {
            list.forEach { (id, label) ->
                val isSelected = id == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouchTarget)
                        .clickable {
                            values[field.idKey] = id
                            values[field.labelKey] = label
                        }
                        .testTag("config_app_shortcut_$id")
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
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
        Text(stringResource(R.string.shortcut_may_change), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
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

@Composable
internal fun TimeRangeFieldRow(field: EditorField.TimeRange, values: MutableMap<String, String>) =
    TimeFieldRows(listOf(field.startKey to field.startLabelRes, field.endKey to field.endLabelRes), values)

/** One tappable row per key-to-label entry, each opening the same clock picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeFieldRows(entries: List<Pair<String, Int>>, values: MutableMap<String, String>) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    // Which key the open picker writes to; null while closed.
    var editingKey by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { (key, labelRes) ->
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

private val INTERVAL_UNITS = listOf(
    "s" to R.string.interval_unit_seconds,
    "m" to R.string.interval_unit_minutes,
    "h" to R.string.interval_unit_hours,
)

/** Unit chips, then a slider and a number field over that unit's range, kept in sync through [values]. */
@Composable
private fun IntervalFieldRow(field: EditorField.Interval, values: MutableMap<String, String>) {
    val colors = MaterialTheme.colorScheme
    val unit = values[field.unitKey].orEmpty()
    val every = values[field.valueKey].orEmpty()
    val range = ConstraintEditorForms.intervalRange(unit) ?: ConstraintEditorForms.intervalRange("m")!!
    val clamp = { values[field.valueKey] = ConstraintEditorForms.clampInterval(values[field.valueKey].orEmpty(), values[field.unitKey].orEmpty()) }
    // 59 minutes switched to hours becomes 24, never 59 hours.
    LaunchedEffect(unit) { clamp() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceFieldRow(remember(field) { EditorField.Choice(field.unitKey, field.labelRes, INTERVAL_UNITS) }, values)
        Slider(
            value = (every.toIntOrNull() ?: range.first).coerceIn(range).toFloat(),
            onValueChange = { values[field.valueKey] = it.roundToInt().toString() },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = 0,
            modifier = Modifier.fillMaxWidth().testTag("config_interval_slider"),
        )
        OutlinedTextField(
            value = every,
            onValueChange = { input -> values[field.valueKey] = input.filter { it.isDigit() }.take(2) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) clamp() }
                .testTag("config_field_every"),
            label = { Text(stringResource(INTERVAL_UNITS.firstOrNull { it.first == unit }?.second ?: field.labelRes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { Text("${range.first}–${range.last}") },
        )
        ConstraintEditorForms.clockExample(every, unit)?.let { ticks ->
            val helper = if (ConstraintEditorForms.intervalDividesClock(every, unit)) R.string.helper_interval_clock else R.string.helper_interval_midnight
            Text(stringResource(helper, ticks), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if ((TimeRules.intervalSeconds(every, unit) ?: Long.MAX_VALUE) < 600) {
            Text(stringResource(R.string.helper_interval_sleep), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

// Wraps rather than scrolls: in a row the last days sat off the edge with nothing saying they were there.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DaySetFieldRow(field: EditorField.DaySet, values: MutableMap<String, String>) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val chosen = ConstraintEditorForms.parseDays(values[field.key].orEmpty())
    val days = remember(locale) { ConstraintEditorForms.weekOrder(locale) }
    Column {
        Text(stringResource(field.labelRes), style = SectionLabel, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { day ->
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

/** "Not today. First run in 22 h 49 min" under an "At a time", refreshed each minute while open. */
@Composable
private fun FirstRunHint(constraint: ScriptConstraint) {
    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = java.time.LocalDateTime.now()
        }
    }
    val (today, minutes) = TimeRules.firstRun(constraint, now) ?: return
    val days = minutes / (24 * 60)
    val hours = minutes / 60 % 24
    val wait = when {
        days > 0 -> stringResource(R.string.time_in_days, days.toInt(), hours.toInt())
        hours > 0 -> stringResource(R.string.time_in_hours, hours.toInt(), (minutes % 60).toInt())
        else -> stringResource(R.string.time_in_minutes, minutes.toInt())
    }
    Text(
        stringResource(if (today) R.string.time_first_run_today else R.string.time_first_run_later, wait),
        Modifier.testTag("constraint_first_run"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Several apps as removable chips, plus "Add app" (the same picker, one app at a time). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppListFieldRow(field: EditorField.AppList, values: MutableMap<String, String>, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val packages = values[field.key].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(field.labelRes), style = SectionLabel, color = colors.onSurfaceVariant)
        FlowRow(
            Modifier.animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            packages.forEach { pkg ->
                val label = AppLabelResolver.get(context).displayName(pkg, context.resources)
                InputChip(
                    selected = false,
                    onClick = { values[field.key] = (packages - pkg).joinToString(",") },
                    label = { Text(label) },
                    trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.app_list_remove, label), Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("app_list_${field.key}_$pkg"),
                )
            }
            AssistChip(
                onClick = onAdd,
                label = { Text(stringResource(R.string.app_list_add)) },
                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) },
                modifier = Modifier.testTag("app_list_add_${field.key}"),
            )
        }
    }
}
