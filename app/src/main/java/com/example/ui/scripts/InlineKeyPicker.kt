package com.example.ui.scripts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.bridge.KeyEventMapping
import com.example.data.PhysicalKeyOption
import com.example.data.keyDisplayName
import com.example.ui.components.MinTouchTarget
import io.github.nicholasarda.keysnap.R

/** Non-modal dock: the sentence above remains the live recording/result surface. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun InlineKeyPicker(
    savedKeys: List<PhysicalKeyOption>,
    isRecording: Boolean,
    onDismiss: () -> Unit,
    onSelectKey: (Int) -> Unit,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSwitchToSurface: (() -> Unit)?,
    advancedModeActive: Boolean = false,
    onSetUpAdvancedMode: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val cancel by rememberUpdatedState(onCancelRecording)
    DisposableEffect(Unit) { onDispose { cancel() } }
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colors.surface, shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth().testTag("inline_key_picker"),
    ) {
        Column(
            Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .44f).dp)
                .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.inline_key_choose), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("inline_key_done")) {
                    Text(stringResource(R.string.common_done))
                }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (isRecording) colors.errorContainer else colors.primary.copy(alpha = .08f))
                    .clickable(role = Role.Button, onClick = if (isRecording) onCancelRecording else onStartRecording)
                    .testTag("record_key_button").padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (isRecording) colors.error else colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(if (isRecording) 14.dp else 16.dp)
                        .clip(if (isRecording) RoundedCornerShape(4.dp) else CircleShape)
                        .background(if (isRecording) colors.onError else colors.onPrimary))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (isRecording) R.string.trigger_picker_listening else R.string.inline_key_record),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isRecording) colors.onErrorContainer else colors.primary)
                    Text(stringResource(if (isRecording) R.string.inline_key_release else R.string.trigger_picker_tap_to_record),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (isRecording && !advancedModeActive) {
                Column {
                    Text(stringResource(R.string.inline_key_advanced_hint),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    TextButton(
                        onClick = { onCancelRecording(); onSetUpAdvancedMode() },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(stringResource(R.string.inline_key_set_up))
                    }
                }
            }
            if (savedKeys.isNotEmpty()) {
                Text(stringResource(R.string.trigger_picker_saved_key), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    savedKeys.forEach { key ->
                        val name = keyDisplayName(LocalContext.current.resources, key.keyCode, savedKeys)
                        AssistChip(
                            onClick = { onSelectKey(key.keyCode) }, enabled = !isRecording,
                            label = { Text(name) },
                            modifier = Modifier.heightIn(min = MinTouchTarget).testTag("trigger_option_${key.keyCode}"),
                        )
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(
                    onClick = { onSelectKey(KeyEventMapping.ASSISTANT_GESTURE) }, enabled = !isRecording,
                    label = { Text(stringResource(R.string.trigger_assistant_gesture)) },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = MinTouchTarget).testTag("assistant_trigger_option"),
                )
                if (onSwitchToSurface != null) AssistChip(
                    onClick = onSwitchToSurface, enabled = !isRecording,
                    label = { Text(stringResource(R.string.trigger_kind_surface)) },
                    leadingIcon = { Icon(Icons.Outlined.Widgets, null, Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = MinTouchTarget).testTag("trigger_picker_use_surface"),
                )
            }
            }
        }
    }
}
