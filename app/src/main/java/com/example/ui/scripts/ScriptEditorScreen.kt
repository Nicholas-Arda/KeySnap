package com.example.ui.scripts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.ActionAvailability
import com.example.data.ActionCategory
import com.example.data.ActionDescriptor
import com.example.data.ActionEditorForms
import com.example.data.ScriptAction
import com.example.data.ScriptActionCatalog
import com.example.data.ConstraintEditorForms
import com.example.data.InstalledApp
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.data.getReadableKeyName
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.ConsoleShape
import com.example.ui.components.IconTile
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.components.trailingEdgeFade
import com.example.ui.theme.SectionLabel
import com.example.ui.theme.categoryTileColors
import io.github.nicholasarda.keysnap.R
import com.example.viewmodel.ScriptDraft

/**
 * These two actions take no params, so [ActionEditorForms] has no form for them; "Configure" still
 * applies because there is somewhere useful to send the user — the system's assistant-app picker —
 * which [onConfigure] resolves to a settings redirect instead of opening [ActionEditorForms]'s sheet.
 */
val ASSISTANT_SETTINGS_ACTION_TYPES = setOf("launch_voice_assistant", "launch_device_assistant")

/**
 * Full-screen, Shortcuts-style builder for one shortcut: a WHEN trigger, a DO action chain, and a
 * full-screen catalog to add actions to that chain. Only [ScriptDraft] is mutated here — nothing is
 * written to [com.example.data.ScriptRepository] until `onDone`.
 */
@Composable
fun ScriptEditorScreen(
    draft: ScriptDraft,
    message: String?,
    onConsumeMessage: () -> Unit,
    onSetName: (String) -> Unit,
    onAddAction: (String, Map<String, String>) -> Unit,
    onConfigureNewAction: (String) -> Unit,
    onConfigureAction: (Int) -> Unit,
    onRemoveAction: (Int) -> Unit,
    onMoveAction: (Int, Int) -> Unit,
    onRemoveConstraint: (Int) -> Unit,
    onOpenTriggerPicker: () -> Unit,
    onToggleAutomation: () -> Unit,
    onPickConstraint: (String) -> Unit,
    onOpenScriptSettings: () -> Unit,
    onConfigureConstraint: (Int) -> Unit = {},
    installedApps: List<InstalledApp> = emptyList(),
    advancedModeActive: Boolean = false,
    onRunOnce: () -> Unit,
    onDeleteScript: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        onConsumeMessage()
    }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ActionCategory?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pane by remember { mutableStateOf(EditorPane.CHAIN) }
    // The draft as the editor received it. Every edit goes through updateDraft's copy(), and the
    // draft and everything it holds are data classes, so structural inequality means "touched".
    val opened = remember(draft.id) { draft }
    fun requestClose() {
        if (draft != opened) confirmDiscard = true else onClose()
    }
    // Back pops one level: a catalog pane returns to the chain; the chain closes like the top-bar tile.
    BackHandler { if (pane != EditorPane.CHAIN) pane = EditorPane.CHAIN else requestClose() }

    if (confirmDiscard) {
        DiscardChangesDialog(
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
        )
    }

    if (confirmDelete) {
        DeleteShortcutDialog(
            name = draft.name.ifBlank { "Shortcut" },
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDeleteScript()
            },
        )
    }

    // The catalogs replace the chain rather than layering over it, so the swap is animated: the
    // outgoing pane fades first and the incoming one follows a beat later, which keeps the two from
    // reading through each other while neither paints an opaque background of its own.
    AnimatedContent(
        targetState = pane,
        transitionSpec = {
            val enteringCatalog = targetState != EditorPane.CHAIN
            val offset = if (enteringCatalog) 1 else -1
            (
                fadeIn(tween(200, delayMillis = 90)) +
                    slideInVertically(tween(260, delayMillis = 90)) { height -> offset * height / 12 }
                ) togetherWith fadeOut(tween(110))
        },
        label = "editorPane",
    ) { target ->
        when (target) {
            EditorPane.ADD_ACTION -> AddActionScreen(
                query = query,
                onQueryChange = { query = it },
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it },
                onPickAction = { type ->
                    pane = EditorPane.CHAIN
                    if (ActionEditorForms.isConfigurable(type)) onConfigureNewAction(type) else onAddAction(type, emptyMap())
                },
                onClose = { pane = EditorPane.CHAIN },
            )
            EditorPane.ADD_CONSTRAINT -> ConstraintPickerScreen(
                onClose = { pane = EditorPane.CHAIN },
                onPickConstraint = { type ->
                    pane = EditorPane.CHAIN
                    onPickConstraint(type)
                },
            )
            // statusBarsPadding keeps the top bar from rendering beneath the real system status bar,
            // which otherwise intercepts touches meant for the app.
            EditorPane.CHAIN -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                EditorTopBar(
                    name = draft.name,
                    onSetName = onSetName,
                    onOpenScriptSettings = onOpenScriptSettings,
                    // Same rule as MainViewModel.commitDraft: no actions, or a key shortcut with no
                    // key, is not a shortcut worth saving.
                    canSave = draft.actions.isNotEmpty() && (draft.automation || draft.trigger != null),
                    onDone = onDone,
                    onClose = { requestClose() },
                )
                Box(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxSize()) {
                        ChainArea(
                            draft = draft,
                            onOpenTriggerPicker = onOpenTriggerPicker,
                            onToggleAutomation = onToggleAutomation,
                            onConfigureAction = onConfigureAction,
                            onRemoveAction = onRemoveAction,
                            onMoveAction = onMoveAction,
                            onOpenConstraintPicker = { pane = EditorPane.ADD_CONSTRAINT },
                            onRemoveConstraint = onRemoveConstraint,
                            onConfigureConstraint = onConfigureConstraint,
                            installedApps = installedApps,
                            advancedModeActive = advancedModeActive,
                            onOpenAddAction = { pane = EditorPane.ADD_ACTION },
                            modifier = Modifier.weight(1f),
                        )
                        BottomToolbar(
                            canRun = draft.actions.isNotEmpty(),
                            canDelete = !draft.isNew,
                            onRunOnce = onRunOnce,
                            onDeleteScript = { confirmDelete = true },
                        )
                    }
                    SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

/** Guards Back and the close tile so unsaved edits are not thrown away silently. */
@Composable
private fun DiscardChangesDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_unsaved_title)) },
        text = { Text(stringResource(R.string.editor_unsaved_body)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.error,
                    contentColor = colors.onError,
                ),
            ) { Text(stringResource(R.string.editor_unsaved_discard)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Which full-screen pane the editor is showing; the two catalogs replace the chain outright. */
private enum class EditorPane { CHAIN, ADD_ACTION, ADD_CONSTRAINT }

@Composable
private fun EditorTopBar(
    name: String,
    onSetName: (String) -> Unit,
    onOpenScriptSettings: () -> Unit,
    canSave: Boolean,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // The field has no container and no unfocused indicator, so without this glyph the name reads
    // as static text and nobody discovers the shortcut can be renamed. It goes once editing starts.
    val interactionSource = remember { MutableInteractionSource() }
    val nameFocused by interactionSource.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth().background(colors.background).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarIconTile(Icons.Filled.Close, stringResource(R.string.editor_discard_changes), onClose)
        TextField(
            value = name,
            onValueChange = onSetName,
            modifier = Modifier.weight(1f).testTag("script_name_field"),
            placeholder = { Text(stringResource(R.string.editor_shortcut_name), style = MaterialTheme.typography.titleSmall) },
            trailingIcon = {
                if (!nameFocused) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
            },
            singleLine = true,
            interactionSource = interactionSource,
            textStyle = MaterialTheme.typography.titleSmall,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = colors.primary,
            ),
        )
        TopBarIconTile(
            Icons.Outlined.Settings,
            stringResource(R.string.editor_shortcut_settings),
            onOpenScriptSettings,
            Modifier.testTag("script_settings_button"),
        )
        TextButton(onClick = onDone, enabled = canSave, modifier = Modifier.testTag("script_done_button")) {
            val tint = if (canSave) colors.primary else colors.onSurfaceVariant.copy(alpha = .4f)
            Icon(Icons.Filled.Check, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.common_done), color = tint, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ChainArea(
    draft: ScriptDraft,
    onOpenTriggerPicker: () -> Unit,
    onToggleAutomation: () -> Unit,
    onConfigureAction: (Int) -> Unit,
    onRemoveAction: (Int) -> Unit,
    onMoveAction: (Int, Int) -> Unit,
    onOpenConstraintPicker: () -> Unit,
    onRemoveConstraint: (Int) -> Unit,
    onOpenAddAction: () -> Unit,
    modifier: Modifier = Modifier,
    onConfigureConstraint: (Int) -> Unit = {},
    installedApps: List<InstalledApp> = emptyList(),
    advancedModeActive: Boolean = false,
) {
    // Held here rather than inside the row: with per-row state, removing or reordering an action
    // leaves an open menu attached to whichever action slid into that position.
    var openMenuFor by remember { mutableStateOf<Int?>(null) }
    LazyColumn(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        item { ChainLabel(stringResource(R.string.editor_chain_when)) }
        item { TriggerCard(draft, onOpenTriggerPicker, onToggleAutomation) }
        // Done is disabled until both of these are filled in; say which one is missing.
        if (!draft.automation && draft.trigger == null) {
            item { ChainHint(stringResource(R.string.editor_needs_trigger)) }
        }
        item { ChainLabel(stringResource(R.string.editor_chain_only_if), topPadding = 6.dp) }
        item { ConstraintsCard(draft.constraints, onOpenConstraintPicker, onRemoveConstraint, onConfigureConstraint, installedApps) }
        item { ChainLabel(stringResource(R.string.editor_chain_do), topPadding = 6.dp) }
        // Keyed on instance identity, which survives a reorder: draftMoveAction moves the same
        // ScriptAction objects, and only an edited action becomes a new instance.
        itemsIndexed(draft.actions, key = { _, action -> System.identityHashCode(action) }) { index, action ->
            ActionChainCard(
                action = action,
                index = index,
                total = draft.actions.size,
                menuOpen = openMenuFor == index,
                advancedModeActive = advancedModeActive,
                onOpenMenu = { openMenuFor = index },
                onDismissMenu = { openMenuFor = null },
                onConfigure = { onConfigureAction(index) },
                onRemove = { onRemoveAction(index) },
                onMoveUp = { onMoveAction(index, index - 1) },
                onMoveDown = { onMoveAction(index, index + 1) },
            )
        }
        item { AddActionRow(onOpenAddAction) }
        if (draft.actions.isEmpty()) {
            item { ChainHint(stringResource(R.string.editor_needs_action)) }
        }
    }
}

/** Why the Done button is greyed out, shown under whichever section is still empty. */
@Composable
private fun ChainHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Dashed, Shortcuts-style call to action that opens the full-screen action catalog. */
@Composable
private fun AddActionRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .dashedBorder(colors.outline, InnerShape)
            .heightIn(min = MinTouchTarget)
            .clickable(onClickLabel = stringResource(R.string.editor_add_action), onClick = onClick)
            .testTag("add_action_button")
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.editor_add_action), color = colors.primary, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun Modifier.dashedBorder(color: Color, shape: Shape, strokeWidth: Dp = 1.6.dp): Modifier =
    drawWithContent {
        drawContent()
        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
            ),
        )
    }

@Composable
private fun ChainLabel(text: String, topPadding: Dp = 0.dp) {
    Text(
        text,
        Modifier.padding(top = topPadding),
        style = SectionLabel,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TriggerCard(draft: ScriptDraft, onOpenKeyPicker: () -> Unit, onToggleAutomation: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val res = LocalContext.current.resources
    val trigger = draft.trigger
    when {
        trigger != null -> Row(
            Modifier
                .fillMaxWidth()
                .clip(InnerShape)
                .background(colors.primary.copy(alpha = .12f))
                .heightIn(min = MinTouchTarget)
                .clickable(onClick = onOpenKeyPicker)
                .testTag("assign_key_card")
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Filled.Bolt, size = 36.dp, selected = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    trigger.keyCodes.joinToString("+") { getReadableKeyName(res, it) },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(trigger.pressType.titleRes), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = colors.onSurfaceVariant)
        }
        // Tapping the automation card clears it, back to the key/automation choice.
        draft.automation -> Row(
            Modifier
                .fillMaxWidth()
                .clip(InnerShape)
                .background(colors.primary.copy(alpha = .12f))
                .heightIn(min = MinTouchTarget)
                .clickable(onClick = onToggleAutomation)
                .testTag("assign_key_card")
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Outlined.AutoAwesome, size = 36.dp, selected = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.trigger_kind_automation),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.automation_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = colors.onSurfaceVariant)
        }
        else -> Row(
            Modifier
                .fillMaxWidth()
                .clip(InnerShape)
                .border(1.dp, colors.outline.copy(alpha = .6f), InnerShape)
                .testTag("assign_key_card"),
        ) {
            TriggerKindOption(
                Icons.Outlined.Keyboard,
                stringResource(R.string.trigger_kind_key),
                Modifier.weight(1f).testTag("trigger_kind_key"),
                onOpenKeyPicker,
            )
            Box(Modifier.width(1.dp).heightIn(min = MinTouchTarget).background(colors.outline.copy(alpha = .3f)))
            TriggerKindOption(
                Icons.Outlined.AutoAwesome,
                stringResource(R.string.trigger_kind_automation),
                Modifier.weight(1f).testTag("trigger_kind_automation"),
                onToggleAutomation,
            )
        }
    }
}

@Composable
private fun TriggerKindOption(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.heightIn(min = MinTouchTarget).clickable(onClickLabel = label, onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(label, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ConstraintsCard(
    constraints: List<ScriptConstraint>,
    onOpenPicker: () -> Unit,
    onRemove: (Int) -> Unit,
    onConfigure: (Int) -> Unit,
    installedApps: List<InstalledApp>,
) {
    val colors = MaterialTheme.colorScheme
    val resources = LocalContext.current.resources
    Column(
        Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .border(1.dp, colors.outline.copy(alpha = .6f), InnerShape)
            .testTag("constraints_card"),
    ) {
        if (constraints.isEmpty()) {
            Text(
                stringResource(R.string.editor_always_runs),
                Modifier.padding(16.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            constraints.forEachIndexed { index, constraint ->
                val descriptor = ScriptConstraintCatalog.descriptor(constraint.type)
                val title = descriptor?.let { stringResource(it.titleRes) } ?: constraint.type
                val configurable = ConstraintEditorForms.form(constraint.type) != null
                val summary = ConstraintEditorForms.summarize(constraint, resources, installedApps)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = configurable, onClickLabel = stringResource(R.string.editor_configure_action, title)) { onConfigure(index) }
                        .testTag("constraint_row_$index")
                        .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        constraintIcon(constraint.type, descriptor?.category),
                        null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
                        if (summary != null) {
                            Text(
                                summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = { onRemove(index) },
                        modifier = Modifier.size(MinTouchTarget).testTag("remove_constraint_$index"),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            stringResource(R.string.editor_remove_constraint, title),
                            Modifier.size(18.dp),
                            tint = colors.error,
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .clickable(onClick = onOpenPicker)
                .testTag("add_constraint_button")
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.editor_add_constraint), color = colors.primary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * One step in the Do chain. The row itself opens the action's settings; reordering and removal live
 * behind one overflow button, so a phone-width row is a name and a summary rather than four 32dp
 * icon buttons competing with the text for space.
 */
@Composable
private fun ActionChainCard(
    action: ScriptAction,
    index: Int,
    total: Int,
    menuOpen: Boolean,
    advancedModeActive: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onConfigure: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val descriptor = ScriptActionCatalog.descriptor(action.type)
    val title = descriptor?.let { stringResource(it.titleRes) } ?: action.type
    val configurable = ActionEditorForms.isConfigurable(action.type) || action.type in ASSISTANT_SETTINGS_ACTION_TYPES
    // Scripts saved before configuration existed can be missing required params; surface that
    // rather than letting the action fail silently at run time.
    val incomplete = ActionEditorForms.missingRequired(action).isNotEmpty()
    val summary = ActionEditorForms.summarize(action, LocalContext.current.resources)
    val connectorColor = colors.outline.copy(alpha = .5f)
    Box(
        Modifier.fillMaxWidth().drawBehind {
            // One continuous line down the chain, through every numbered badge's center (x=20dp,
            // matching the badge's own math below). Each card only owns its own height, so a
            // middle card's segment overshoots by half of ChainArea's 10dp item spacing in both
            // directions to meet its neighbors' segments in the middle of the gap between cards;
            // the first/last card stops at its own badge so the line never pokes past the chain's ends.
            val lineX = 20.dp.toPx()
            val halfGap = 5.dp.toPx()
            val top = if (index == 0) size.height / 2f else -halfGap
            val bottom = if (index == total - 1) size.height / 2f else size.height + halfGap
            drawLine(connectorColor, Offset(lineX, top), Offset(lineX, bottom), strokeWidth = 2.dp.toPx())
        },
    ) {
        Card(
            Modifier.fillMaxWidth().padding(start = 10.dp).testTag("chain_action_$index"),
            shape = ConsoleShape,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(1.dp, colors.outline.copy(alpha = .4f)),
        ) {
            Row(
                Modifier
                    .heightIn(min = MinTouchTarget)
                    .clickable(enabled = configurable, onClickLabel = stringResource(R.string.editor_configure_action, title), onClick = onConfigure)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(actionIcon(action.type, descriptor?.category), category = descriptor?.category, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            incomplete -> Text(
                                stringResource(R.string.editor_tap_to_configure),
                                Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.error,
                            )
                            summary != null -> Text(
                                summary,
                                Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (ActionEditorForms.needsAdvancedMode(action.type)) AdvancedModeChip(advancedModeActive)
                    }
                }
                Box {
                    IconButton(
                        onClick = onOpenMenu,
                        modifier = Modifier.size(MinTouchTarget).testTag("action_menu_$index"),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            stringResource(R.string.editor_more_options, title),
                            tint = if (incomplete) colors.error else colors.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                        if (configurable) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_configure)) },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                modifier = Modifier.testTag("configure_action_$index"),
                                onClick = {
                                    onDismissMenu()
                                    onConfigure()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_move_up)) },
                            leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) },
                            enabled = index > 0,
                            onClick = {
                                onDismissMenu()
                                onMoveUp()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_move_down)) },
                            leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) },
                            enabled = index < total - 1,
                            onClick = {
                                onDismissMenu()
                                onMoveDown()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_remove), color = colors.error) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = colors.error) },
                            modifier = Modifier.testTag("remove_action_$index"),
                            onClick = {
                                onDismissMenu()
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-1).dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant)
                .border(2.dp, colors.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Says on the chain row that this action only ever runs through the shell-UID bridge. The error
 * tint is the whole point: with Advanced Mode off the step is dead, and the sheet's badge is one
 * tap too far away for the user to see that while reading the chain.
 */
@Composable
private fun AdvancedModeChip(active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(24.dp)
            .clip(InnerShape)
            .background(if (active) colors.secondaryContainer else colors.errorContainer)
            .padding(horizontal = 8.dp)
            .testTag("advanced_mode_chip"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.home_status_advanced),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) colors.onSecondaryContainer else colors.onErrorContainer,
            maxLines = 1,
        )
    }
}

@Composable
private fun BottomToolbar(canRun: Boolean, canDelete: Boolean, onRunOnce: () -> Unit, onDeleteScript: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // A draft that was never saved has nothing to delete; the close tile's discard guard is
        // the way out of it.
        if (canDelete) {
            TextButton(onClick = onDeleteScript, modifier = Modifier.testTag("delete_script_button")) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = colors.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.common_delete), color = colors.error, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Spacer(Modifier)
        }
        TextButton(onClick = onRunOnce, enabled = canRun, modifier = Modifier.testTag("run_once_button")) {
            val tint = if (canRun) colors.primary else colors.onSurfaceVariant.copy(alpha = .4f)
            Icon(Icons.Outlined.PlayCircleOutline, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.editor_test_run), color = tint, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Full-screen catalog of every action, opened from the dashed "Add action" row at the end of the
 * DO chain. Search and the category rail both narrow the same grouped list underneath.
 */
@Composable
private fun AddActionScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: ActionCategory?,
    onSelectCategory: (ActionCategory?) -> Unit,
    onPickAction: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("action_picker_sheet")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopBarIconTile(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), onClose, Modifier.testTag("action_picker_back"))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.editor_add_action), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        }
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.action_picker_search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("action_picker_search"),
        )
        Spacer(Modifier.height(12.dp))
        val categoryScroll = rememberLazyListState()
        LazyRow(
            Modifier.fillMaxWidth().trailingEdgeFade(categoryScroll.canScrollForward),
            state = categoryScroll,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CategoryIconChip(
                    icon = Icons.Outlined.GridView,
                    label = stringResource(R.string.picker_category_all),
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                )
            }
            items(ActionCategory.entries.size) { i ->
                val category = ActionCategory.entries[i]
                CategoryIconChip(
                    icon = categoryIcon(category),
                    label = stringResource(category.titleRes),
                    selected = selectedCategory == category,
                    onClick = { onSelectCategory(if (selectedCategory == category) null else category) },
                    category = category,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val searching = query.isNotBlank()
        val offered = if (searching) ScriptActionCatalog.search(query, LocalContext.current.resources) else ScriptActionCatalog.all
        val filtered = selectedCategory?.let { cat -> offered.filter { it.category == cat } } ?: offered
        val grouped = ActionCategory.entries.mapNotNull { category ->
            val entries = filtered.filter { it.category == category }
            if (entries.isEmpty()) null else category to entries
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            if (grouped.isEmpty()) {
                item {
                    Text(
                        if (searching) stringResource(R.string.action_picker_no_match, query) else stringResource(R.string.action_picker_empty_category),
                        Modifier.padding(16.dp),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            grouped.forEach { (category, entries) ->
                item(key = "header_${category.name}") {
                    Text(
                        stringResource(category.titleRes),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = SectionLabel,
                        color = colors.onSurfaceVariant,
                    )
                }
                items(entries.size, key = { "action_${entries[it].type}" }) { i ->
                    ActionRow(entries[i], onPickAction)
                }
            }
        }
    }
}

/** A topbar icon button drawn as a small rounded tile, matching the redesign's `.topbtn`, with a
 *  full [MinTouchTarget] tap area centered around the visibly smaller 36dp tile. */
@Composable
fun TopBarIconTile(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.size(MinTouchTarget).clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(InnerShape).background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** Flat, borderless-looking search row matching the redesign rather than Material's boxed outline. */
@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(InnerShape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outline.copy(alpha = .4f), InnerShape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
            )
        }
    }
}

/**
 * Icon-tile chip in the category rail, tinted by the same rule as the rows it filters: [category]'s
 * wash while resting, its solid swatch once selected. "All" has no category and takes the accent.
 */
@Composable
fun CategoryIconChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    category: ActionCategory? = null,
) {
    val colors = MaterialTheme.colorScheme
    val (fill, glyph) = categoryTileColors(category, selected)
    Column(
        Modifier
            .width(62.dp)
            .clickable(onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(46.dp).clip(InnerShape).background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = glyph, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActionRow(descriptor: ActionDescriptor, onPickAction: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val available = descriptor.availability == ActionAvailability.AVAILABLE
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(
                enabled = available,
                onClickLabel = stringResource(R.string.action_picker_add_label, stringResource(descriptor.titleRes)),
                onClick = { onPickAction(descriptor.type) },
            )
            .semantics { if (!available) disabled() }
            .testTag("action_row_${descriptor.type}")
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (available) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(actionIcon(descriptor.type, descriptor.category), category = descriptor.category, size = 32.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(descriptor.titleRes),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!available) {
                Text(
                    stringResource(R.string.common_coming_soon),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (available) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(colors.primary.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = colors.primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}
