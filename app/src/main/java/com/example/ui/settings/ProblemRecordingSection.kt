package com.example.ui.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.KeyMappingRepository
import com.example.data.ScriptRepository
import com.example.service.DiagnosticRecorder
import com.example.service.HardwareKeyTriggerCoordinator
import com.example.service.KeyMapperAccessibilityService
import com.example.ui.theme.MonoLabel
import io.github.nicholasarda.keysnap.BuildConfig
import io.github.nicholasarda.keysnap.R

@Composable
fun ProblemRecordingSection() {
    val context = LocalContext.current
    val recording by DiagnosticRecorder.isRecording.collectAsState()
    val hasRecording by DiagnosticRecorder.hasRecording.collectAsState()
    var preview by remember { mutableStateOf<String?>(null) }
    var mailUnavailable by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.diagnostic_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.diagnostic_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (recording) {
            RecordingIndicator()
            Button(onClick = DiagnosticRecorder::stop) { Text(stringResource(R.string.diagnostic_stop)) }
        } else if (!hasRecording) {
            Button(onClick = {
                val scripts = ScriptRepository.getInstance(context)
                val service = ComponentName(context, KeyMapperAccessibilityService::class.java)
                val accessibilityEnabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == service }
                DiagnosticRecorder.start(
                    accessibility = accessibilityEnabled,
                    advancedMode = HardwareKeyTriggerCoordinator.isAdvancedBridgeActive.value,
                    paused = !scripts.globallyEnabled.value,
                    scriptCount = scripts.scripts.value.size,
                    consumeOriginal = KeyMappingRepository.getInstance(context).config.value.consumeOriginalEvent,
                )
            }) { Text(stringResource(R.string.diagnostic_start)) }
        }
        if (hasRecording) {
            if (!recording) {
                Text(stringResource(R.string.diagnostic_stopped), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { mailUnavailable = false; preview = DiagnosticRecorder.render() }) {
                    Text(stringResource(R.string.diagnostic_review))
                }
            }
            TextButton(onClick = DiagnosticRecorder::clear) { Text(stringResource(R.string.diagnostic_discard)) }
        }
    }
    preview?.let { text ->
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.diagnostic_review), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.diagnostic_send_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // The weight sits on a plain Box: SelectionContainer does not pass it on, and the
                // log then pushed the send buttons off screen.
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(text, style = MonoLabel, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                if (mailUnavailable) {
                    Text(stringResource(R.string.diagnostic_no_email), color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = {
                    val subject = "KeySnap diagnostic ${BuildConfig.VERSION_NAME}"
                    // Some mail apps ignore the mailto body, others the extras; set both so the log survives either.
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@ardasenn.com?subject=${Uri.encode(subject)}&body=${Uri.encode(text)}"))
                        .putExtra(Intent.EXTRA_EMAIL, arrayOf("support@ardasenn.com"))
                        .putExtra(Intent.EXTRA_SUBJECT, subject)
                        .putExtra(Intent.EXTRA_TEXT, text)
                    try {
                        context.startActivity(intent)
                        preview = null
                        DiagnosticRecorder.clear()
                    } catch (_: ActivityNotFoundException) {
                        mailUnavailable = true
                    }
                }) { Text(stringResource(R.string.diagnostic_send)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { preview = null }) { Text(stringResource(R.string.common_close)) }
                    TextButton(onClick = { preview = null; DiagnosticRecorder.clear() }) { Text(stringResource(R.string.diagnostic_discard)) }
                }
            }
        }
    }
}

@Composable
fun RecordingIndicator(modifier: Modifier = Modifier) {
    val recording by DiagnosticRecorder.isRecording.collectAsState()
    if (recording) {
        AssistChip(
            onClick = DiagnosticRecorder::stop,
            label = { Text(stringResource(R.string.diagnostic_recording)) },
            modifier = modifier,
        )
    }
}
