package com.example.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import com.example.data.InstalledApp
import com.example.data.InstalledAppsRepository
import com.example.data.SensitiveApp
import com.example.data.SensitiveAppsRepository
import com.example.service.adb.WirelessAdbManager
import com.example.ui.components.EdgeToEdgeDialogWindow
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.home.AdvancedModeFixCards
import com.example.ui.home.rememberNotificationPermissionRequest
import com.example.ui.scripts.AppIconTile
import com.example.ui.scripts.AppPickerSheet
import com.example.ui.theme.setupSuccess
import com.example.ui.theme.setupWarning
import io.github.nicholasarda.keysnap.R

/**
 * Settings → Sensitive apps: the apps KeySnap steps aside for, what to hide for each, and the
 * optional notice. Only the list and its settings live here; enforcement reads them through
 * [com.example.service.SensitiveAppsGate].
 */
@Composable
fun SensitiveAppsDialog(onSetUpAdvancedMode: () -> Unit, onOpenAccessibility: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val repository = remember(context) { SensitiveAppsRepository.getInstance(context) }
    val store by repository.store.collectAsState()
    // Enforcement lives in the bridge alone: without it nothing is hidden.
    val bridgeRunning by WirelessAdbManager.isAdvancedModeRunning.collectAsState()
    val settings = store.settings
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val requestNotifications = rememberNotificationPermissionRequest()
    val on = stringResource(R.string.scripts_toggle_state_on)
    val off = stringResource(R.string.home_status_off)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(decorFitsSystemWindows = false)) {
        EdgeToEdgeDialogWindow()
        Column(Modifier.fillMaxSize().background(colors.background).testTag("sensitive_apps_screen")) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_close), tint = colors.onSurfaceVariant)
                }
                Text(stringResource(R.string.sensitive_title), style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
            }
            LazyColumn(
                Modifier.fillMaxWidth().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "needs_advanced") {
                    Column {
                        // After a Sensitive app ended Advanced Mode, the Fix cards say exactly what is missing instead.
                        AdvancedModeFixCards(onOpenAccessibility, onSetUpAdvancedMode)
                        AnimatedVisibility(!bridgeRunning && !store.advancedModeEnded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            SettingsCard("sensitive_needs_advanced") {
                                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                    Icon(Icons.Outlined.DeveloperMode, null, Modifier.size(22.dp), tint = setupWarning())
                                    Text(stringResource(R.string.sensitive_needs_advanced), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    FilledTonalButton(onClick = onSetUpAdvancedMode, Modifier.testTag("sensitive_set_up_advanced")) {
                                        Text(stringResource(R.string.sensitive_card_set_up))
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "intro") {
                    SettingsCard("sensitive_intro") {
                        Row(Modifier.padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            Icon(Icons.Outlined.Shield, null, Modifier.size(22.dp), tint = colors.primary)
                            Column {
                                Text(stringResource(R.string.sensitive_intro_title), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.sensitive_intro_body), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                item(key = "master") {
                    SettingsCard("sensitive_master_card", Modifier.padding(top = 4.dp)) {
                        ToggleRow(Icons.Outlined.Shield, stringResource(R.string.sensitive_master), if (settings.masterEnabled) on else off, settings.masterEnabled, "sensitive_master") { value ->
                            repository.updateSettings { it.copy(masterEnabled = value) }
                        }
                    }
                }
                item(key = "apps_label") { SettingsGroupLabel(R.string.sensitive_your_apps) }
                items(store.apps, key = { it.packageName }) { app ->
                    AppRow(app, Modifier.animateItem()) { editing = app.packageName }
                }
                item(key = "add") { AddAppButton { picking = true } }
                item(key = "feedback_label") { SettingsGroupLabel(R.string.sensitive_feedback_label) }
                item(key = "notification") {
                    SettingsCard("sensitive_notification_card") {
                        ToggleRow(
                            Icons.Outlined.Notifications,
                            stringResource(R.string.sensitive_show_notification),
                            null,
                            settings.showNotification,
                            "sensitive_notification",
                        ) { value ->
                            repository.updateSettings { it.copy(showNotification = value) }
                            if (value && !NotificationManagerCompat.from(context).areNotificationsEnabled()) requestNotifications()
                        }
                    }
                }
                item(key = "vibrate") {
                    SettingsCard("sensitive_vibrate_card") {
                        ToggleRow(
                            Icons.Outlined.Vibration,
                            stringResource(R.string.sensitive_vibrate),
                            null,
                            settings.vibrate,
                            "sensitive_vibrate",
                        ) { value -> repository.updateSettings { it.copy(vibrate = value) } }
                    }
                }
                item(key = "footer") { Footer(anyWireless = store.apps.any { it.hideWirelessDebugging }) }
            }
        }
        store.apps.firstOrNull { it.packageName == editing }?.let { app ->
            WhatToHideSheet(
                app = app,
                onSave = {
                    repository.update(it)
                    editing = null
                },
                onRemove = {
                    repository.remove(app.packageName)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
        if (picking) {
            val installed by produceState(emptyList<InstalledApp>()) { value = InstalledAppsRepository(context).launchableApps() }
            val added = store.apps.mapTo(HashSet()) { it.packageName }
            AppPickerSheet(
                apps = installed.filter { it.packageName != context.packageName && it.packageName !in added },
                selected = null,
                onSelect = { pkg ->
                    repository.add(pkg, installed.firstOrNull { it.packageName == pkg }?.label ?: pkg)
                    picking = false
                },
                onDismiss = { picking = false },
            )
        }
    }
}

/** The Home card that points people here once: closed or used, it is gone for good. */
@Composable
internal fun SensitiveAppsHomeCard(onSetUp: () -> Unit, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SettingsCard("sensitive_home_card") {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Outlined.Shield)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sensitive_card_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.sensitive_card_body), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onSetUp, Modifier.testTag("sensitive_home_set_up")) { Text(stringResource(R.string.sensitive_card_set_up)) }
            IconButton(onClick = onClose, Modifier.testTag("sensitive_home_close")) {
                Icon(Icons.Filled.Close, stringResource(R.string.common_close), tint = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(38.dp).background(colors.primary.copy(alpha = .14f), InnerShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = colors.primary)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, testTag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .testTag(testTag)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppRow(app: SensitiveApp, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SettingsCard("sensitive_app_${app.packageName}", modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconTile(app.packageName, app.label)
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FlowRow(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (app.hideDeveloperOptions) Tag(stringResource(R.string.sensitive_developer_options), setupWarning())
                    if (app.hideAccessibility) Tag(stringResource(R.string.sensitive_accessibility), colors.primary)
                    if (app.hideWirelessDebugging) Tag(stringResource(R.string.sensitive_wireless_debugging), colors.error)
                }
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tag(text: String, tint: Color) {
    Text(
        text,
        Modifier.background(tint.copy(alpha = .16f), InnerShape).padding(horizontal = 9.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = tint,
    )
}

@Composable
private fun AddAppButton(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dash = colors.outline
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp).heightIn(min = MinTouchTarget)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    dash,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clip(InnerShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("sensitive_add_app")
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = colors.primary)
        Text(stringResource(R.string.sensitive_add_app), Modifier.padding(start = 8.dp), color = colors.primary, fontWeight = FontWeight.Bold)
    }
}

/** [anyWireless]: an app here ends Advanced Mode instead, so the promise above gets its exception. */
@Composable
private fun Footer(anyWireless: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = setupSuccess())
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) { append(stringResource(R.string.sensitive_footer_keys)) }
                    append(" ")
                    append(stringResource(R.string.sensitive_footer_restore))
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        AnimatedVisibility(anyWireless, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Outlined.WarningAmber, null, Modifier.size(16.dp), tint = setupWarning())
                Text(stringResource(R.string.sensitive_footer_wireless), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Per-app choices. Nothing to hide is not a choice: that is what Remove is for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhatToHideSheet(app: SensitiveApp, onSave: (SensitiveApp) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var developer by remember(app.packageName) { mutableStateOf(app.hideDeveloperOptions) }
    var accessibility by remember(app.packageName) { mutableStateOf(app.hideAccessibility) }
    var wireless by remember(app.packageName) { mutableStateOf(app.hideWirelessDebugging) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)
                .testTag("sensitive_sheet"),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIconTile(app.packageName, app.label)
                Text(app.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                stringResource(R.string.sensitive_sheet_question),
                Modifier.padding(vertical = 3.dp, horizontal = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            OptionRow(Icons.Outlined.DeveloperMode, R.string.sensitive_developer_options, R.string.sensitive_developer_options_desc, developer, "sensitive_hide_developer") { developer = it }
            OptionRow(Icons.Outlined.Accessibility, R.string.sensitive_accessibility, R.string.sensitive_accessibility_desc, accessibility, "sensitive_hide_accessibility") { accessibility = it }
            OptionRow(Icons.Outlined.Wifi, R.string.sensitive_wireless_debugging, R.string.sensitive_wireless_debugging_desc, wireless, "sensitive_hide_wireless") { wireless = it }
            AnimatedVisibility(wireless, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                val warn = setupWarning()
                Row(
                    Modifier.fillMaxWidth().background(warn.copy(alpha = .16f), InnerShape).padding(horizontal = 12.dp, vertical = 11.dp)
                        .testTag("sensitive_wireless_warning"),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(Icons.Outlined.WarningAmber, null, Modifier.size(16.dp), tint = warn)
                    Column {
                        Text(stringResource(R.string.sensitive_wireless_warning_off), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(stringResource(R.string.sensitive_wireless_warning_setup), style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
                    }
                }
            }
            // With wireless debugging nothing comes back on leaving: the green promise gives way to the plain truth.
            val noteTint by animateColorAsState(if (wireless) colors.onSurfaceVariant else setupSuccess(), label = "sheetNoteTint")
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp).clip(InnerShape).drawBehind { drawRect(noteTint.copy(alpha = .16f)) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                AnimatedContent(wireless, label = "sheetNote") { leaving ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(if (leaving) Icons.Outlined.Info else Icons.Filled.Check, null, Modifier.size(16.dp), tint = noteTint)
                        Text(
                            stringResource(if (leaving) R.string.sensitive_sheet_note_wireless else R.string.sensitive_sheet_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface,
                        )
                    }
                }
            }
            Button(
                onClick = { onSave(app.copy(hideDeveloperOptions = developer, hideAccessibility = accessibility, hideWirelessDebugging = wireless)) },
                enabled = developer || accessibility || wireless,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = MinTouchTarget).testTag("sensitive_save"),
            ) { Text(stringResource(R.string.common_save)) }
            TextButton(onClick = onRemove, Modifier.fillMaxWidth().testTag("sensitive_remove")) {
                Text(stringResource(R.string.sensitive_remove), color = colors.error)
            }
        }
    }
}

@Composable
private fun OptionRow(icon: ImageVector, title: Int, description: Int, checked: Boolean, testTag: String, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val border by animateColorAsState(if (checked) colors.primary else colors.outline.copy(alpha = .6f), label = "optionBorder")
    val fill by animateColorAsState(if (checked) colors.primary.copy(alpha = .08f) else colors.onSurface.copy(alpha = .04f), label = "optionFill")
    Row(
        Modifier.fillMaxWidth()
            .clip(InnerShape)
            .background(fill)
            .border(1.5.dp, border, InnerShape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .testTag(testTag)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
