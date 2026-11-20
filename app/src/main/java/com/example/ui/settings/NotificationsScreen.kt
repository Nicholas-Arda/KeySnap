package com.example.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.NotificationPrefs
import com.example.service.NotificationPrefs.Kind
import com.example.ui.components.EdgeToEdgeDialogWindow
import com.example.ui.components.InnerShape
import io.github.nicholasarda.keysnap.R

/** Every notification KeySnap itself can send: one switch each, words where the words are the user's. */
@Composable
fun NotificationsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val on = remember { mutableStateMapOf<Kind, Boolean>().apply { Kind.entries.forEach { put(it, NotificationPrefs.enabled(context, it)) } } }
    var editing by remember { mutableStateOf<WordsSheet?>(null) }
    fun toggle(kind: Kind) = { value: Boolean -> on[kind] = value; NotificationPrefs.setEnabled(context, kind, value) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(decorFitsSystemWindows = false)) {
        EdgeToEdgeDialogWindow()
        Column(Modifier.fillMaxSize().background(colors.background).testTag("notifications_screen")) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_close), tint = colors.onSurfaceVariant)
                }
                Text(stringResource(R.string.notice_title), style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
            }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsGroupLabel(R.string.notif_group_working, first = true)
                SettingsCard("notif_working") {
                    SettingsSwitchRow(
                        stringResource(R.string.notif_channel_run_failures), stringResource(R.string.notif_failures_desc),
                        on[Kind.FAILURES] == true, true, "notif_failures", toggle(Kind.FAILURES), RowPadding,
                    )
                }
                SettingsGroupLabel(R.string.notif_group_shortcuts)
                SettingsCard("notif_shortcuts") {
                    SettingsSwitchRow(
                        stringResource(R.string.notif_channel_automation_runs), stringResource(R.string.notif_runs_desc),
                        on[Kind.AUTOMATION_RUNS] == true, true, "notif_runs", toggle(Kind.AUTOMATION_RUNS), RowPadding,
                    ) { WordsLink("notif_runs_words") { editing = WordsSheet.RUNS } }
                    SettingsDivider()
                    SettingsSwitchRow(
                        stringResource(R.string.action_show_notification), stringResource(R.string.notif_action_desc),
                        on[Kind.SHORTCUT_ACTION] == true, true, "notif_action", toggle(Kind.SHORTCUT_ACTION), RowPadding,
                    )
                }
                SettingsGroupLabel(R.string.notif_group_places)
                SettingsCard("notif_places") {
                    RequiredRow(stringResource(R.string.notif_channel_place_fast), stringResource(R.string.notif_place_desc)) {
                        WordsLink("notif_place_words") { editing = WordsSheet.PLACE }
                    }
                }
                SettingsGroupLabel(R.string.home_status_advanced)
                SettingsCard("notif_advanced") {
                    SettingsSwitchRow(
                        stringResource(R.string.notif_autostart_row), stringResource(R.string.notif_autostart_desc),
                        on[Kind.AUTOSTART] == true, true, "notif_autostart", toggle(Kind.AUTOSTART), RowPadding,
                    )
                    SettingsDivider()
                    RequiredRow(stringResource(R.string.notif_setup_row), stringResource(R.string.notif_setup_desc))
                }
                SettingsCard("notif_system") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.testTag("notif_system_settings"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.notif_system_settings), Modifier.weight(1f), color = colors.primary, fontWeight = FontWeight.Medium)
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = colors.primary)
                    }
                }
            }
        }
        editing?.let { WordsEditor(it) { editing = null } }
    }
}

/** A notification a running feature must show (Android requires it): no switch, just why. */
@Composable
private fun RequiredRow(title: String, description: String, extra: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = RowPadding), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            extra?.invoke()
        }
        Text(
            stringResource(R.string.notif_required),
            Modifier.background(colors.onSurface.copy(alpha = .07f), InnerShape).padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Sits under the row's description, flush with its title. */
@Composable
private fun WordsLink(tag: String, onClick: () -> Unit) {
    Text(
        stringResource(R.string.notice_edit),
        Modifier.clickable(role = Role.Button, onClick = onClick).padding(top = 6.dp, bottom = 2.dp).testTag(tag),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

private val RowPadding = 12.dp

private enum class WordsSheet { RUNS, PLACE }

/** One field per word the user may set; blank keeps the built-in words, shown as the placeholder. */
private class Field(val key: String, val label: Int, val placeholder: String, val max: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordsEditor(sheet: WordsSheet, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val name = NotificationPrefs.NAME
    val sections: List<Pair<Int, List<Field>>> = when (sheet) {
        WordsSheet.RUNS -> listOf(
            R.string.notice_when_runs to listOf(
                Field(NotificationPrefs.RUN_TITLE, R.string.notice_field_title, stringResource(R.string.notice_run_title_default, name), 60),
                Field(NotificationPrefs.RUN_TEXT, R.string.notice_field_text, context.resources.getQuantityString(R.plurals.notice_run_text_default, 2, 2), 120),
            ),
            R.string.notice_when_undone to listOf(
                Field(NotificationPrefs.UNDO_TITLE, R.string.notice_field_title, stringResource(R.string.notice_undo_title_default, name), 60),
                Field(NotificationPrefs.UNDO_TEXT, R.string.notice_field_text, stringResource(R.string.notice_undo_text_default), 120),
            ),
        )
        WordsSheet.PLACE -> listOf(
            R.string.notif_channel_place_fast to listOf(
                Field(NotificationPrefs.PLACE_TITLE, R.string.notice_field_title, stringResource(R.string.place_fast_notification_title), 60),
                Field(NotificationPrefs.PLACE_TEXT, R.string.notice_field_text, stringResource(R.string.place_fast_notification_text), 120),
            ),
        )
    }
    val fields = sections.flatMap { it.second }
    val values = remember { mutableStateMapOf<String, String>().apply { fields.forEach { put(it.key, NotificationPrefs.text(context, it.key)) } } }
    val sample = stringResource(R.string.notif_sample_name)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(if (sheet == WordsSheet.RUNS) R.string.notif_channel_automation_runs else R.string.notif_channel_place_fast),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(if (sheet == WordsSheet.RUNS) R.string.notif_runs_hint else R.string.notif_place_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            sections.forEach { (heading, list) ->
                if (sections.size > 1) Text(stringResource(heading), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
                list.forEach { field ->
                    Text(stringResource(field.label), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    OutlinedTextField(
                        value = values[field.key].orEmpty(),
                        onValueChange = { values[field.key] = it.take(field.max) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("notif_field_${field.key}"),
                        placeholder = { Text(field.placeholder) },
                    )
                }
                // What the notification will say, with a sample shortcut name in place of {name}.
                val (title, text) = list.map { f -> values[f.key].orEmpty().ifBlank { f.placeholder }.replace(name, sample) }
                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp)
                        .background(colors.onSurface.copy(alpha = .05f), InnerShape).padding(12.dp),
                ) {
                    Text(stringResource(R.string.notif_preview), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { fields.forEach { values[it.key] = "" } }) { Text(stringResource(R.string.notice_reset)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    fields.forEach { NotificationPrefs.setText(context, it.key, values[it.key].orEmpty()) }
                    onDismiss()
                }) { Text(stringResource(R.string.common_save)) }
            }
        }
    }
}
