package com.example.ui.scripts

import android.content.res.Resources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bridge.ActionTestResult
import com.example.data.ScriptAction
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.theme.setupSuccess
import com.example.ui.theme.setupWarning
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** [fix]: the failure is the phone blocking Bluetooth, so the pill offers "Fix" ([SystemOptimizationSheet]). */
internal class EditorFeedback(val text: String, val failed: Boolean, val fix: Boolean = false)

@Composable
internal fun ChainActionTestButton(
    action: ScriptAction,
    index: Int,
    enabled: Boolean,
    runAction: suspend (ScriptAction) -> ActionTestResult,
    onResult: (EditorFeedback) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val success = setupSuccess()
    val resources = LocalContext.current.resources
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var running by remember(action) { mutableStateOf(false) }
    var result by remember(action) { mutableStateOf<ActionTestResult?>(null) }
    val tint by animateColorAsState(
        when (result?.ok) { true -> success; false -> colors.error; null -> colors.onSurfaceVariant },
        label = "actionTestTint",
    )
    val description = stringResource(when {
        running && result == null -> R.string.chain_test_running
        result?.ok == true -> R.string.chain_test_success
        result?.ok == false -> R.string.chain_test_failure
        else -> R.string.chain_test_action
    })
    IconButton(
        onClick = {
            if (!running) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                running = true
                result = null
                scope.launch {
                    try {
                        val outcome = try {
                            runAction(action)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            ActionTestResult(false, null, null, 0, false, emptyList())
                        }
                        result = outcome
                        val blocked = !outcome.ok && action.type in BLUETOOTH_SWITCH_ACTIONS && readPermissionMonitor() == "true"
                        onResult(
                            if (blocked) EditorFeedback("✗ " + resources.getString(R.string.chain_bt_blocked), true, fix = true)
                            else EditorFeedback(actionTestSummary(resources, action, outcome), !outcome.ok),
                        )
                        delay(2_000)
                        result = null
                    } finally {
                        running = false
                    }
                }
            }
        },
        enabled = enabled && !running,
        modifier = Modifier.size(MinTouchTarget).testTag("test_action_$index")
            .semantics { contentDescription = description; liveRegion = LiveRegionMode.Polite },
    ) {
        AnimatedContent(running to result?.ok, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "actionTestState") { state ->
            Box(Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                if (state.first && state.second == null) {
                    CircularProgressIndicator(Modifier.size(16.dp).testTag("action_test_progress_$index"), color = tint, strokeWidth = 2.dp)
                } else {
                    Icon(
                        when (state.second) { true -> Icons.Filled.Check; false -> Icons.Filled.Close; null -> Icons.Filled.PlayArrow },
                        null, Modifier.size(17.dp), tint = tint,
                    )
                }
            }
        }
    }
}

internal fun actionTestSummary(resources: Resources, action: ScriptAction, result: ActionTestResult): String {
    val title = if (action.type == "http_request") "HTTP" else
        com.example.data.ScriptActionCatalog.descriptor(action.type)?.let { resources.getString(it.titleRes) } ?: resources.getString(R.string.chain_test_action)
    val status = when {
        result.timedOut -> resources.getString(R.string.chain_test_timeout)
        result.httpStatus != null -> if (result.httpStatus == 200) "200 OK" else resources.getString(R.string.chain_http_status, result.httpStatus)
        result.exitCode != null && (action.type == "shell_command" || !result.ok) -> resources.getString(R.string.chain_exit_code, result.exitCode)
        result.ok -> resources.getString(R.string.chain_test_success)
        else -> resources.getString(R.string.chain_test_failure)
    }
    val seconds = String.format(resources.configuration.locales[0], "%.1f", result.durationMs / 1000.0)
    val summary = resources.getString(R.string.chain_test_result, if (result.ok) "✓" else "✗", title, status, seconds)
    // A failed fixed command (svc bluetooth …) shows what the system said, so a screenshot names the
    // cause. Never for shell/HTTP: their output can echo a token from the user's own command.
    // A stack trace ends in "at android.os.Binder…", so its exception line is the one that says why.
    val said = if (result.ok || action.type == "shell_command" || action.type == "http_request") null
    else (result.output.lastOrNull { "Exception:" in it } ?: result.output.lastOrNull { it.isNotBlank() })?.trim()?.take(120)
    return if (said == null) summary else "$summary\n$said"
}

@Composable
internal fun EditorFeedbackPill(feedback: EditorFeedback?, modifier: Modifier = Modifier, onFix: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    AnimatedContent(feedback, modifier, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "feedbackPill") { current ->
        if (current != null) {
            val ink by animateColorAsState(if (current.failed) colors.error else setupSuccess(), label = "feedbackInk")
            Row(
                Modifier.clip(InnerShape).background(colors.surfaceContainerHigh).animateContentSize()
                    .padding(start = 12.dp, end = if (current.fix) 4.dp else 12.dp, top = if (current.fix) 0.dp else 12.dp, bottom = if (current.fix) 0.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    current.text,
                    Modifier.weight(1f, fill = false).testTag("chain_feedback").semantics { liveRegion = LiveRegionMode.Polite },
                    color = ink,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (current.fix) TextButton(onClick = onFix, modifier = Modifier.testTag("chain_feedback_fix")) { Text(stringResource(R.string.setup_fix)) }
            }
        }
    }
}

internal fun canTestChainAction(action: ScriptAction): Boolean =
    action.type !in setOf("reply_to_notification", "reply_notification", "send_message")

@Composable
internal fun ConstraintNegationToggle(negated: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    Row(modifier.selectableGroup().clip(InnerShape).background(colors.surfaceVariant), verticalAlignment = Alignment.CenterVertically) {
        listOf(false, true).forEach { option ->
            val selected = option == negated
            val fill by animateColorAsState(
                if (selected) { if (option) colors.error else colors.primary } else colors.surfaceVariant,
                label = "negationFill",
            )
            val ink by animateColorAsState(
                if (selected) { if (option) colors.onError else colors.onPrimary } else colors.onSurfaceVariant,
                label = "negationInk",
            )
            Box(
                Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = {
                    if (!selected) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(option)
                    }
                }).background(fill).heightIn(min = MinTouchTarget).padding(horizontal = 8.dp, vertical = 10.dp)
                    .testTag(if (option) "constraint_not" else "constraint_is"),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(if (option) R.string.chain_not else R.string.chain_is), color = ink, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun RowConfigError(incomplete: Boolean, action: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(incomplete, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Text(
            stringResource(if (action) R.string.chain_action_empty else R.string.chain_condition_empty),
            modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun ShellLimitedWarning(visible: Boolean, onSetUp: () -> Unit) {
    val warning = setupWarning()
    AnimatedVisibility(visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(
            Modifier.clip(InnerShape).background(warning.copy(alpha = .12f)).padding(start = 8.dp).testTag("shell_limited_warning"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.chain_shell_limited), Modifier.weight(1f), color = warning, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onSetUp, modifier = Modifier.testTag("shell_set_up")) {
                Text(stringResource(R.string.chain_set_up), color = warning)
            }
        }
    }
}

internal val BLUETOOTH_SWITCH_ACTIONS = setOf("toggle_bluetooth", "enable_bluetooth", "disable_bluetooth")

/**
 * Whether ColorOS will refuse these actions: its permission monitor (Developer options "Disable
 * system optimization" off, `persist.sys.permission.enable=true`) refuses the bridge's
 * `svc bluetooth`, and there is deliberately no dialog fallback, so the editor asks for that switch.
 * When the property cannot be read, an OPPO / OnePlus / realme phone is assumed to have it on.
 */
internal fun bluetoothPromptExpected(permissionMonitor: String?, manufacturer: String): Boolean = when (permissionMonitor) {
    "true" -> true
    "false" -> false
    else -> manufacturer.lowercase() in setOf("oppo", "oneplus", "realme")
}

internal fun readPermissionMonitor(): String? = runCatching {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        .invoke(null, "persist.sys.permission.enable") as String
}.getOrNull()?.takeIf { it.isNotEmpty() }

@Composable
internal fun BluetoothPromptWarning(actionType: String) {
    fun read() = bluetoothPromptExpected(readPermissionMonitor(), android.os.Build.MANUFACTURER.orEmpty())
    var expected by remember { mutableStateOf(read()) }
    // Re-read on return from Developer options, so the warning goes away once the switch is on.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        expected = read()
        onPauseOrDispose {}
    }
    val warning = setupWarning()
    var showSheet by remember { mutableStateOf(false) }
    if (showSheet) SystemOptimizationSheet(onDismiss = { showSheet = false })
    AnimatedVisibility(expected && actionType in BLUETOOTH_SWITCH_ACTIONS, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(
            Modifier.clip(InnerShape).background(warning.copy(alpha = .12f)).padding(start = 8.dp).testTag("bluetooth_prompt_warning"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(stringResource(R.string.chain_bt_prompt), color = warning, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.chain_bt_prompt_hide), color = warning, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { showSheet = true }) {
                Text(stringResource(R.string.chain_open_developer_options), color = warning)
            }
        }
    }
}
