package com.example.ui.scripts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ScriptActionCatalog
import com.example.data.ScriptConstraintCatalog
import com.example.data.ShortcutScript
import com.example.data.getReadableKeyName
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.ConsoleShape
import com.example.ui.components.IconTile
import com.example.ui.components.InnerShape
import com.example.ui.home.iconForScript
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import com.example.ui.components.MinTouchTarget


@Composable
fun ScriptsScreen(
    scripts: List<ShortcutScript>,
    globallyEnabled: Boolean,
    onToggleScriptEnabled: (String, Boolean) -> Unit,
    onOpenScript: (String) -> Unit,
    onCreateScript: () -> Unit,
    onDeleteScript: (String) -> Unit,
    onGoHome: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        // Bottom clears the floating bottom navigation only — nothing floats over the list.
        contentPadding = PaddingValues(top = 8.dp, bottom = 132.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.scripts_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!globallyEnabled && scripts.isNotEmpty()) {
                        Text(
                            stringResource(R.string.scripts_paused_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                // The icon-only button takes its name from the icon's description, so keep the label
                // there — without it TalkBack announces only "Button".
                FilledIconButton(
                    onClick = onCreateScript,
                    modifier = Modifier.testTag("new_script_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.scripts_new_shortcut))
                }
            }
        }
        if (scripts.isEmpty()) {
            item { EmptyState(onGoHome) }
        }
        items(scripts.size, key = { scripts[it].id }) { index ->
            ScriptCard(scripts[index], globallyEnabled, onToggleScriptEnabled, onOpenScript, onDeleteScript)
        }
    }
}

/**
 * Says what a shortcut is, shows how one is made, and offers the one-tap route on Home — rather than
 * only reporting that the list is empty. The tutorial below only exists while there is nothing here.
 */
@Composable
private fun EmptyState(onGoHome: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.scripts_empty_title), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        Text(
            stringResource(R.string.scripts_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Row(
            Modifier
                .heightIn(min = MinTouchTarget)
                .clickable(role = Role.Button, onClick = onGoHome),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.scripts_empty_go_home),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary,
            )
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = colors.primary)
        }
        // Last, because the tutorial card's height changes as its steps expand — above the link it
        // would push the link around and off short screens.
        Spacer(Modifier.height(16.dp))
        ShortcutTutorialCard()
    }
}

@Composable
private fun ScriptCard(
    script: ShortcutScript,
    globallyEnabled: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val firstAction = script.actions.firstOrNull()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        DeleteShortcutDialog(
            name = script.name.ifBlank { stringResource(R.string.scripts_unnamed) },
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete(script.id)
            },
        )
    }
    Card(
        onClick = { onOpen(script.id) },
        modifier = Modifier.fillMaxWidth().testTag("script_card_${script.id}"),
        shape = ConsoleShape,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, colors.outline.copy(alpha = .4f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (firstAction != null) {
                    IconTile(iconForScript(script), category = ScriptActionCatalog.descriptor(firstAction.type)?.category)
                } else {
                    Box(Modifier.size(40.dp).clip(InnerShape).background(colors.surfaceVariant))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        script.name.ifBlank { stringResource(R.string.scripts_unnamed) },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val (triggerText, triggerOk) = triggerSummary(script)
                    Text(
                        triggerText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (triggerOk) colors.onSurfaceVariant else colors.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                EnabledPill(
                    name = script.name.ifBlank { stringResource(R.string.scripts_unnamed) },
                    scriptEnabled = script.enabled,
                    globallyEnabled = globallyEnabled,
                    onToggle = { onToggle(script.id, !script.enabled) },
                    modifier = Modifier.testTag("script_status_${script.id}"),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        script.actions.isEmpty() -> stringResource(R.string.scripts_no_actions)
                        else -> pluralStringResource(R.plurals.scripts_action_count, script.actions.size, script.actions.size)
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (script.actions.isEmpty()) colors.error else colors.onSurfaceVariant,
                    maxLines = 1,
                )
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(MinTouchTarget).testTag("delete_script_${script.id}"),
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.scripts_delete_label, script.name.ifBlank { stringResource(R.string.scripts_unnamed) }),
                        Modifier.size(20.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The "key name · press type" line, plus whether the shortcut can actually fire. Shared with the
 * Home shortcut rows so the two pages never describe the same trigger differently.
 */
@Composable
fun triggerSummary(script: ShortcutScript): Pair<String, Boolean> {
    val res = LocalContext.current.resources
    val trigger = script.triggers.firstOrNull()
    val automationHasCondition = script.automation &&
        script.constraints.any { ScriptConstraintCatalog.isAutomation(it.type) }
    val text = when {
        trigger != null -> "${trigger.keyCodes.joinToString("+") { getReadableKeyName(res, it) }} · ${stringResource(trigger.pressType.shortBadgeRes)}"
        automationHasCondition -> stringResource(R.string.trigger_kind_automation)
        script.automation -> "${stringResource(R.string.trigger_kind_automation)} · ${stringResource(R.string.scripts_no_condition)}"
        else -> stringResource(R.string.scripts_no_trigger)
    }
    return text to (trigger != null || automationHasCondition)
}

/**
 * The per-shortcut switch. It reports the shortcut's own state even while the global pause is on,
 * so a tap always has a visible result; the global pause is announced once at the top of the screen
 * instead of being repeated as "Paused" on every card.
 */
@Composable
private fun EnabledPill(
    name: String,
    scriptEnabled: Boolean,
    globallyEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Off reads as neutral rather than as an alarm; the knob's side and the state description
    // are what separate off from paused, so no third track colour is needed.
    val live = scriptEnabled && globallyEnabled
    val trackColor = if (live) colors.primary else colors.surfaceVariant
    val knobColor = if (live) colors.onPrimary else colors.onSurfaceVariant
    // Off and paused share a track, so without this TalkBack calls the paused one "on".
    val state = stringResource(
        when {
            !scriptEnabled -> R.string.scripts_toggle_state_off
            globallyEnabled -> R.string.scripts_toggle_state_on
            else -> R.string.scripts_toggle_state_paused
        },
    )
    Box(
        modifier
            .size(width = MinTouchTarget, height = MinTouchTarget)
            .toggleable(value = scriptEnabled, role = Role.Switch, onValueChange = { onToggle() })
            .semantics {
                contentDescription = name
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 42.dp, height = 25.dp)
                .clip(CircleShape)
                .background(trackColor),
        ) {
            Box(
                Modifier
                    .align(if (scriptEnabled) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(3.dp)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(knobColor),
            )
        }
    }
}

/** Shared with the editor so a shortcut is deleted the same way wherever the button is. */
@Composable
fun DeleteShortcutDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scripts_delete_title)) },
        text = { Text(stringResource(R.string.scripts_delete_body, name)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.error,
                    contentColor = colors.onError,
                ),
            ) { Text(stringResource(R.string.common_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

