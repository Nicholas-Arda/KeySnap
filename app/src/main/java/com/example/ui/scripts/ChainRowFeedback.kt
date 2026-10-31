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

internal class EditorFeedback(val text: String, val failed: Boolean)

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
                        onResult(EditorFeedback(actionTestSummary(resources, action, outcome), !outcome.ok))
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
        result.exitCode != null -> resources.getString(R.string.chain_exit_code, result.exitCode)
        result.ok -> resources.getString(R.string.chain_test_success)
        else -> resources.getString(R.string.chain_test_failure)
    }
    val seconds = String.format(resources.configuration.locales[0], "%.1f", result.durationMs / 1000.0)
    return resources.getString(R.string.chain_test_result, if (result.ok) "✓" else "✗", title, status, seconds)
}

@Composable
internal fun EditorFeedbackPill(feedback: EditorFeedback?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AnimatedContent(feedback, modifier, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "feedbackPill") { current ->
        if (current != null) {
            val ink by animateColorAsState(if (current.failed) colors.error else setupSuccess(), label = "feedbackInk")
            Text(
                current.text,
                Modifier.clip(InnerShape).background(colors.surfaceContainerHigh).animateContentSize().padding(12.dp)
                    .testTag("chain_feedback").semantics { liveRegion = LiveRegionMode.Polite },
                color = ink,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

internal fun canTestChainAction(action: ScriptAction): Boolean =
    action.type !in setOf("reply_to_notification", "reply_notification", "send_message")

@Composable
internal fun ConstraintNegationToggle(negated: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
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
                Modifier.selectable(selected, role = Role.RadioButton, onClick = {
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
