package com.example.ui.scripts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.data.ScriptAction
import com.example.data.ScriptActionCatalog
import com.example.ui.theme.MonoBody
import io.github.nicholasarda.keysnap.R

/** Hands [text] to Android's share sheet. KeySnap itself sends it nowhere. */
fun shareShortcutText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * Lists what a shortcut runs before it is shared or imported. Every parameter of a
 * [ScriptActionCatalog.REVIEWED_LITERALLY] action is printed as written — never a summary — because this is
 * where the user decides whether to trust it.
 */
@Composable
fun ShortcutReviewDialog(
    title: String,
    name: String,
    automation: Boolean,
    actions: List<ScriptAction>,
    note: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    repliesToEveryChat: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("shortcut_review")) {
                if (name.isNotBlank()) Text(name, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                if (automation) Text(stringResource(R.string.home_suggestions_group_automation), style = MaterialTheme.typography.bodySmall)
                actions.forEach { action ->
                    val descriptor = ScriptActionCatalog.descriptor(action.type) ?: return@forEach
                    Text(
                        stringResource(descriptor.titleRes),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                    if (action.type in ScriptActionCatalog.REVIEWED_LITERALLY) {
                        descriptor.parameters.forEach { spec ->
                            val value = action.params[spec.key].orEmpty()
                            if (value.isNotBlank()) {
                                // A switch reads On/Off; anything else exactly as written.
                                val shown = if (spec.choices == listOf("true", "false")) {
                                    stringResource(if (value == "true") R.string.scripts_toggle_state_on else R.string.scripts_toggle_state_off)
                                } else {
                                    value
                                }
                                Text(
                                    "${stringResource(spec.labelRes)}: $shown",
                                    Modifier.padding(start = 8.dp, top = 2.dp),
                                    style = MonoBody,
                                    color = colors.onSurface,
                                )
                            }
                        }
                    }
                }
                if (repliesToEveryChat) EveryChatWarning(Modifier.padding(top = 12.dp))
                Text(note, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.testTag("review_confirm")) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}

/** Paste shared text or pick a file. The dialog stays open while the picker runs, or its result would be lost. */
@Composable
fun ImportShortcutDialog(onImportText: (String) -> Unit, onImportUri: (Uri) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onImportUri(uri)
            onDismiss()
        }
    }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_body))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    text,
                    { text = it },
                    Modifier.fillMaxWidth().testTag("import_text_field"),
                    maxLines = 6,
                    placeholder = { Text(stringResource(R.string.import_paste_hint)) },
                )
                // Any type: a shared shortcut saved by a chat app may be .txt, .json or untyped; decode sorts it out.
                TextButton(onClick = { pickFile.launch(arrayOf("*/*")) }, modifier = Modifier.testTag("import_choose_file")) {
                    Text(stringResource(R.string.import_choose_file))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onImportText(text)
                    onDismiss()
                },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.import_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}
