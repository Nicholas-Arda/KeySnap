package com.example.ui.scripts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.ScriptActionCatalog
import com.example.data.ActionEditorForms
import com.example.data.ShortcutScript
import com.example.data.ShortcutShare
import com.example.data.TriggerPressType
import com.example.data.isSurfaceOnly
import com.example.mode.ModeHooks
import com.example.ui.components.MinTouchTarget
import io.github.nicholasarda.keysnap.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShortcutQuickSettingsSheet(
    script: ShortcutScript,
    scripts: List<ShortcutScript>,
    isPro: Boolean,
    onRename: (String) -> Unit,
    onSetMode: (Int?) -> Unit,
    onUpdateScripts: (List<ShortcutScript>) -> Unit,
    onNewScriptId: () -> String,
    onDelete: () -> Unit,
    onUpsell: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable(script.id, script.name) { mutableStateOf(script.name) }
    var invalidName by rememberSaveable(script.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(script.id) { mutableStateOf(false) }
    var reviewing by rememberSaveable(script.id) { mutableStateOf(false) }
    fun saveName() {
        invalidName = name.isBlank()
        if (!invalidName) onRename(name.trim())
    }
    fun discardName() {
        name = script.name
        invalidName = false
    }
    fun share() {
        val encoded = ShortcutShare.encode(
            name = script.name,
            automation = script.automation,
            pressType = script.triggers.firstOrNull()?.pressType ?: TriggerPressType.DOUBLE_PRESS,
            actions = script.actions,
            constraints = script.constraints,
            surfaceOnly = script.isSurfaceOnly,
            showCapsule = script.showCapsule,
        )
        shareShortcutText(context, context.getString(R.string.share_text_intro) + "\n\n" + encoded)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.editor_shortcut_settings), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.common_close)) }
            }
            Text(triggerSummary(script).first, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; invalidName = false },
                    modifier = Modifier.weight(1f).testTag("quick_shortcut_name"),
                    label = { Text(stringResource(R.string.script_settings_name)) },
                    singleLine = true,
                    isError = invalidName,
                    supportingText = if (invalidName) ({ Text(stringResource(R.string.shortcuts_name_required)) }) else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveName() }),
                )
                Button(onClick = { saveName() }, modifier = Modifier.testTag("quick_shortcut_save")) {
                    Text(stringResource(R.string.common_save))
                }
            }
            ModeHooks.MoveToMode(
                script = script,
                scripts = scripts,
                isPro = isPro,
                onChange = { discardName(); onSetMode(it) },
                onUpsell = { discardName(); onUpsell() },
                onOpen = { discardName() },
                onUpdateScripts = onUpdateScripts,
                onNewScriptId = onNewScriptId,
            )
            ShortcutSettingsAction(
                label = stringResource(R.string.share_action),
                icon = Icons.Outlined.Share,
                enabled = script.actions.isNotEmpty(),
                onClick = {
                    discardName()
                    if (script.actions.any { it.type in ScriptActionCatalog.REVIEWED_LITERALLY }) reviewing = true else share()
                },
            )
            ShortcutSettingsAction(
                label = stringResource(R.string.shortcuts_delete),
                icon = Icons.Outlined.DeleteOutline,
                destructive = true,
                onClick = { discardName(); deleting = true },
            )
        }
    }
    if (deleting) DeleteShortcutDialog(script.name, onDismiss = { deleting = false }, onConfirm = onDelete)
    if (reviewing) ShortcutReviewDialog(
        title = stringResource(R.string.share_review_title),
        name = script.name,
        automation = script.automation,
        actions = script.actions.map(ShortcutShare::portable),
        note = stringResource(R.string.share_review_note),
        confirmLabel = stringResource(R.string.share_action),
        onConfirm = { reviewing = false; share() },
        onDismiss = { reviewing = false },
        repliesToEveryChat = ActionEditorForms.repliesToEveryChat(script.automation, script.constraints, script.actions),
    )
}

/** Shared row geometry; the private mode implementation supplies its existing PRO badge label. */
@Composable
fun ShortcutSettingsAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    proBadge: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val color = if (destructive) colors.error else colors.onSurface
    Row(
        modifier.fillMaxWidth().heightIn(min = MinTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = color)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = color)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (proBadge != null) Row(
            Modifier.clip(RoundedCornerShape(6.dp)).background(colors.primary.copy(alpha = .12f)).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(12.dp), tint = colors.primary)
            Text(proBadge, style = MaterialTheme.typography.labelSmall, color = colors.primary)
        }
        if (!destructive) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
    }
}
