package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.ui.theme.MonoLabel
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import androidx.compose.ui.unit.dp

/**
 * Every alert in the app, wearing the same surface and text colours. Buttons stay with the caller,
 * so a destructive dialog still passes its own error-tinted confirm.
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}

@Composable
fun AdvancedActivationBanner(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth().border(1.dp, colors.primary.copy(alpha = .7f), ConsoleShape), shape = ConsoleShape, colors = CardDefaults.cardColors(containerColor = colors.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(colors.primary)); Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.dialog_expert_connected), Modifier.weight(1f), style = MonoLabel, color = colors.onSurface)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        }
    }
}

@Composable
fun NameKeyDialog(code: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var customName by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.dialog_new_input_title)) }, text = {
        Column { Text(stringResource(R.string.dialog_new_input_body, code)); Spacer(Modifier.height(8.dp)); OutlinedTextField(customName, { customName = it }, singleLine = true, placeholder = { Text(stringResource(R.string.dialog_new_input_hint)) }) }
    }, confirmButton = { Button(onClick = { if (customName.isNotBlank()) onSave(customName.trim()) }) { Text(stringResource(R.string.common_save_caps)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel_caps)) } }, containerColor = colors.surface, titleContentColor = colors.onSurface, textContentColor = colors.onSurfaceVariant)
}

/** Play's prominent-disclosure requirement for AccessibilityService: the app must say in its own
 *  UI what the service is used for, and get an affirmative tap, before sending the user to the
 *  system toggle. Reuses the service's own description so the two never disagree. */
@Composable
fun AccessibilityDisclosureDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_accessibility_service)) },
        text = { Text(stringResource(R.string.accessibility_service_description)) },
        confirmButton = { Button(onClick = onAgree) { Text(stringResource(R.string.accessibility_disclosure_agree)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}
