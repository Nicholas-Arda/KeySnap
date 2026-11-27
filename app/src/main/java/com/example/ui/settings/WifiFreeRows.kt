package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.service.adb.WirelessAdbManager
import com.example.ui.components.MinTouchTarget
import com.example.ui.components.PointBox
import com.example.ui.components.PointLine
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.delay

/** What a computer runs for "Start from a computer", and what the switch does itself: the same request. */
private val TCPIP_COMMAND = "adb tcpip ${WirelessAdbManager.WIFI_FREE_PORT}"

/**
 * "Keep running without Wi-Fi" and "Start from a computer", under Start after restart. Both rely on
 * adbd listening on [WirelessAdbManager.WIFI_FREE_PORT]; [portOpen] is checked while this is on
 * screen, and with the computer steps open a bridge is started as soon as that port answers.
 */
@Composable
internal fun WifiFreeRows(
    isRunning: Boolean,
    keepWithoutWifi: Boolean,
    onToggleKeepWithoutWifi: (Boolean) -> Unit,
    portOpen: suspend () -> Boolean,
    onStartOverLoopback: suspend () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val port = WirelessAdbManager.WIFI_FREE_PORT.toString()
    var open by remember { mutableStateOf(false) }
    var computerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isRunning, keepWithoutWifi, computerOpen) {
        while (true) {
            open = portOpen()
            if (computerOpen && open && !isRunning) onStartOverLoopback()
            delay(3_000)
        }
    }

    HorizontalDivider(color = colors.outline.copy(alpha = .45f))
    SettingsSwitchRow(
        title = stringResource(R.string.settings_wifi_free_title),
        description = stringResource(R.string.settings_wifi_free_description),
        checked = keepWithoutWifi,
        enabled = true,
        testTag = "keep_without_wifi_switch",
        onCheckedChange = onToggleKeepWithoutWifi,
        extra = if (keepWithoutWifi && isRunning && open) {
            {
                Text(
                    stringResource(R.string.settings_wifi_free_active),
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp))
                        .background(colors.primary.copy(alpha = .12f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                )
            }
        } else {
            null
        },
    )
    AnimatedVisibility(keepWithoutWifi) {
        PointBox(colors.primary) {
            PointLine(Icons.Outlined.Shield, colors.primary, stringResource(R.string.settings_wifi_free_point_sensitive))
            PointLine(Icons.Outlined.CheckCircle, colors.primary, stringResource(R.string.settings_wifi_free_point_works))
            PointLine(Icons.Outlined.Refresh, colors.tertiary, stringResource(R.string.settings_wifi_free_point_restart))
            PointLine(
                Icons.Outlined.Lock,
                colors.tertiary,
                highlightCommand(stringResource(R.string.settings_wifi_free_point_port, port, TCPIP_COMMAND), TCPIP_COMMAND, colors.primary),
            )
        }
    }
    if (!keepWithoutWifi && open) {
        Text(
            stringResource(R.string.settings_wifi_free_still_open, port),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }

    HorizontalDivider(color = colors.outline.copy(alpha = .45f))
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).clickable { computerOpen = !computerOpen },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_computer_title), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(stringResource(R.string.settings_computer_description), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Icon(if (computerOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = colors.onSurfaceVariant)
    }
    AnimatedVisibility(computerOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Step(1, Modifier.clickable {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            }) { Text(stringResource(R.string.settings_computer_step_usb), style = MaterialTheme.typography.bodyMedium) }
            Step(2) { Text(stringResource(R.string.settings_computer_step_connect), style = MaterialTheme.typography.bodyMedium) }
            Step(3) {
                Column {
                    Text(stringResource(R.string.settings_computer_step_run), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(colors.inverseSurface).padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            TCPIP_COMMAND,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = colors.inverseOnSurface,
                        )
                        TextButton(onClick = { copy(context, TCPIP_COMMAND) }) {
                            Text(stringResource(R.string.settings_computer_copy), color = colors.inversePrimary)
                        }
                    }
                }
            }
            Step(4) { Text(stringResource(R.string.settings_computer_step_back), style = MaterialTheme.typography.bodyMedium) }
            PointBox(colors.primary) {
                PointLine(Icons.Outlined.CheckCircle, colors.primary, stringResource(R.string.settings_computer_unplug))
            }
        }
    }
}

@Composable
private fun Step(number: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(colors.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.primary)
        }
        Box(Modifier.weight(1f).padding(top = 2.dp)) { content() }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(text, text))
}
