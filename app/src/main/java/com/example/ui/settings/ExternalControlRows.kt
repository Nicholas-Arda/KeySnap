package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.surface.ExternalControlReceiver
import com.example.ui.components.MinTouchTarget
import com.example.ui.components.PointBox
import com.example.ui.components.PointLine
import io.github.nicholasarda.keysnap.R

/** "Control from other apps": the off-by-default switch, then every name another app needs, each copyable. */
@Composable
internal fun ExternalControlRows() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var enabled by remember { mutableStateOf(ExternalControlReceiver.isEnabled(context)) }
    val pkg = context.packageName
    val cls = ExternalControlReceiver::class.java.name
    val pause = ExternalControlReceiver.action(context, ExternalControlReceiver.PAUSE)
    var namesOpen by rememberSaveable { mutableStateOf(false) }

    SettingsSwitchRow(
        title = stringResource(R.string.external_control_title),
        description = stringResource(R.string.external_control_description),
        checked = enabled,
        enabled = true,
        testTag = "external_control_switch",
        onCheckedChange = {
            ExternalControlReceiver.setEnabled(context, it)
            enabled = it
        },
    )
    AnimatedVisibility(enabled) {
        Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PointBox(colors.primary) {
                PointLine(Icons.Outlined.PauseCircle, colors.primary, stringResource(R.string.external_control_can))
                PointLine(Icons.Outlined.Block, colors.tertiary, stringResource(R.string.external_control_cannot))
            }
            // Needed once, while setting up the other app, so they stay folded away.
            Row(
                Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).clickable { namesOpen = !namesOpen },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.external_control_names), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = colors.primary)
                Icon(if (namesOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = colors.primary)
            }
            AnimatedVisibility(namesOpen) { Names(pkg, cls, pause) }
        }
    }
}

@Composable
private fun Names(pkg: String, cls: String, pause: String) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CopyField(stringResource(R.string.external_control_package), pkg)
        CopyField(stringResource(R.string.external_control_class), cls)
        CopyField(stringResource(R.string.external_control_pause), pause)
        CopyField(stringResource(R.string.external_control_resume), ExternalControlReceiver.action(context, ExternalControlReceiver.RESUME))
        CopyField(stringResource(R.string.external_control_toggle), ExternalControlReceiver.action(context, ExternalControlReceiver.TOGGLE))
        Text(stringResource(R.string.external_control_try_adb), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        val command = "adb shell am broadcast -n $pkg/$cls -a $pause"
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.inverseSurface).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                command,
                Modifier.weight(1f).padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.inverseOnSurface,
            )
            TextButton(onClick = { copyText(context, command) }) {
                Text(stringResource(R.string.settings_computer_copy), color = colors.inversePrimary)
            }
        }
    }
}

@Composable
private fun CopyField(label: String, value: String) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant.copy(alpha = .5f)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = colors.onSurface)
        }
        TextButton(onClick = { copyText(context, value) }) { Text(stringResource(R.string.settings_computer_copy)) }
    }
}

private fun copyText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(text, text))
}
