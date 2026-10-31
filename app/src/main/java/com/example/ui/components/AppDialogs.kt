package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ui.theme.LocalGlass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.example.bridge.KeyEventMapping
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

/**
 * "Advanced Mode · Connected" in the bottom bar's glass. No OK button: it leaves on its own, on a
 * tap, or when pushed back up. Sheet glass rather than the bar's frosted fill, since nothing blurs
 * the page behind it.
 */
@Composable
fun AdvancedActivationBanner(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = glass.barShadow, spotColor = glass.barShadow)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(glass.sheetTop, glass.sheetMid)))
            .border(1.2.dp, glass.barRim, shape)
            .clickable(onClickLabel = stringResource(R.string.common_close), onClick = onDismiss)
            .pointerInput(onDismiss) { detectVerticalDragGestures { _, dy -> if (dy < -4f) onDismiss() } }
            .testTag("advanced_connected_banner")
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = colors.onPrimary)
        }
        Column {
            Text(stringResource(R.string.home_status_advanced), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(stringResource(R.string.pairing_connected), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun NameKeyDialog(code: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var customName by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.dialog_new_input_title)) }, text = {
        Column {
            Text(stringResource(R.string.dialog_new_input_body))
            // Capture is read-only, so the maker's own action on this button still fires alongside ours.
            if (KeyEventMapping.isSynthetic(code)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.oem_key_tip),
                    Modifier.testTag("oem_key_tip"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(customName, { customName = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(stringResource(R.string.dialog_new_input_hint)) })
        }
    }, confirmButton = { Button(onClick = { if (customName.isNotBlank()) onSave(customName.trim()) }) { Text(stringResource(R.string.common_save_caps)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel_caps)) } }, containerColor = colors.surface, titleContentColor = colors.onSurface, textContentColor = colors.onSurfaceVariant)
}

/** One prominent disclosure for every entry point. Settings opens only after explicit consent. */
@Composable
fun AccessibilityDisclosureSheet(onAgree: () -> Unit, onDismiss: () -> Unit) {
    com.example.ui.home.SetupStepSheet(com.example.ui.home.SetupStep.Accessibility, onAgree, onDismiss)
}
