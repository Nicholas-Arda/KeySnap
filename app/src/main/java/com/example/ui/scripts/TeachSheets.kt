package com.example.ui.scripts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.data.LearnedSettings
import com.example.data.LearnedSwitch
import com.example.data.TaughtKey
import com.example.data.TeachRules
import com.example.service.teach.TeachBubble
import io.github.nicholasarda.keysnap.R

/** Starts teaching, or says in a toast why it can't. */
fun startTeaching(context: Context) {
    TeachBubble.begin(context)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
}

/** What the circle hands over once the user is back in KeySnap: the save sheet or "Couldn't learn". */
@Composable
fun TeachHandoffSheets() {
    val handoff by TeachBubble.handoff.collectAsState()
    when (val h = handoff) {
        is TeachBubble.Handoff.Learned -> SaveSwitchSheet(h.keys, onDone = TeachBubble::consumeHandoff)
        TeachBubble.Handoff.GaveUp -> CouldntLearnSheet(onDone = TeachBubble::consumeHandoff)
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSwitchSheet(keys: List<TaughtKey>, onDone: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var swapped by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val writes = TeachRules.writes(keys, swapped)
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("teach_save_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.teach_save_title), style = MaterialTheme.typography.headlineSmall)
            val on = TeachRules.commands(writes, on = true).orEmpty()
            val off = TeachRules.commands(writes, on = false).orEmpty()
            // CLAUDE.md legibility: both commands, as they will run, before anything is saved.
            CommandLine(stringResource(R.string.choice_state_on), on.firstOrNull().orEmpty())
            CommandLine(stringResource(R.string.choice_state_off), off.firstOrNull().orEmpty())
            if (keys.size > 1) {
                TextButton(onClick = { details = !details }) { Text(stringResource(R.string.teach_details, keys.size)) }
                AnimatedVisibility(details) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        on.drop(1).zip(off.drop(1)).forEach { (a, b) ->
                            CommandLine(stringResource(R.string.choice_state_on), a)
                            CommandLine(stringResource(R.string.choice_state_off), b)
                        }
                    }
                }
            }
            if (!TeachRules.knowsOn(keys)) {
                TextButton(onClick = { swapped = !swapped }) { Text(stringResource(R.string.teach_swap)) }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                modifier = Modifier.fillMaxWidth().testTag("teach_name"),
                label = { Text(stringResource(R.string.field_learned_name)) },
                placeholder = { Text(stringResource(R.string.teach_name_hint)) },
                singleLine = true,
            )
            Button(
                onClick = {
                    LearnedSettings.add(context, name, writes)
                    onDone()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("teach_save"),
            ) { Text(stringResource(R.string.common_save)) }
        }
    }
}

@Composable
private fun CommandLine(label: String, command: String) {
    val colors = MaterialTheme.colorScheme
    Row {
        Text(label, Modifier.width(36.dp).padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Text(
            command,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surfaceVariant).padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CouldntLearnSheet(onDone: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("teach_failed_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.teach_fail_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.teach_fail_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onDone(); startTeaching(context) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.teach_fail_retry))
            }
            TextButton(onClick = { onDone(); tellUs(context) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.teach_fail_tell))
            }
        }
    }
}

/** Only the phone model and Android version; the user names the switch and decides to send. */
private fun tellUs(context: Context) {
    val body = context.getString(R.string.teach_mail_body, "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE)
    val uri = Uri.parse("mailto:support@ardasenn.com?subject=" + Uri.encode(context.getString(R.string.teach_mail_subject)) + "&body=" + Uri.encode(body))
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Long-press on a learned row: rename it or delete it. Shortcuts already using it keep working. */
@Composable
fun LearnedSwitchDialog(item: LearnedSwitch, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(item.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.teach_rename_title)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, modifier = Modifier.testTag("learned_rename"))
        },
        confirmButton = {
            TextButton(onClick = { LearnedSettings.rename(context, item.id, name); onDismiss() }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { LearnedSettings.delete(context, item.id); onDismiss() }) {
                Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
