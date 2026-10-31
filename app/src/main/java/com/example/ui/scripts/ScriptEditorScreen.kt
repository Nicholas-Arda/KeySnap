package com.example.ui.scripts

import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.example.data.ActionAvailability
import com.example.data.advancedModeWarningRes
import com.example.data.ActionCategory
import com.example.data.ActionDescriptor
import com.example.data.ActionEditorForms
import com.example.data.ScriptAction
import com.example.data.ScriptActionCatalog
import com.example.data.ConstraintEditorForms
import com.example.data.InstalledApp
import com.example.data.PhysicalKeyOption
import com.example.data.TriggerPressType
import com.example.bridge.KeyEventMapping
import com.example.bridge.ActionTestResult
import com.example.bridge.ConstraintNegation
import com.example.data.constraintLabel
import com.example.data.shellRunsIn
import com.example.data.ShellContext
import com.example.data.RunRecord
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.data.keyDisplayName
import com.example.mode.ModeHooks
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.IconTile
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.example.ui.components.SelectionLeadSpring
import com.example.ui.components.SelectionTrailSpring
import com.example.ui.components.trailingEdgeFade
import com.example.ui.theme.SectionLabel
import com.example.ui.theme.categoryTileColors
import io.github.nicholasarda.keysnap.R
import com.example.viewmodel.ScriptDraft
import com.example.viewmodel.hasTrigger
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
    onClearSurfaceOnly: () -> Unit = {},
    onPickConstraint: (String) -> Unit,
    onOpenScriptSettings: () -> Unit,
    onConfigureConstraint: (Int) -> Unit = {},
    installedApps: List<InstalledApp> = emptyList(),
    advancedModeActive: Boolean = false,
    runs: List<RunRecord> = emptyList(),
    onRunOnce: () -> Unit,
    onDeleteScript: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit = {},
    scripts: List<com.example.data.ShortcutScript> = emptyList(),
    isPro: Boolean = false,
    onSetRunsIn: (Int?) -> Unit = {},
    keyPickerOpen: Boolean = false,
    savedKeys: List<PhysicalKeyOption> = emptyList(),
    isRecordingKey: Boolean = false,
    recordingKeys: List<Int> = emptyList(),
    onDismissKeyPicker: () -> Unit = {},
    onSelectKey: (Int) -> Unit = {},
    onStartRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    onSetPressType: (TriggerPressType) -> Unit = {},
    onSwitchToSurface: () -> Unit = {},
    onSetUpAdvancedMode: () -> Unit = {},
    onSetConstraintParams: (Int, Map<String, String>) -> Unit = { _, _ -> },
    onTestAction: suspend (ScriptAction) -> ActionTestResult = { ActionTestResult(false, null, null, 0, false, emptyList()) },
) {
    val context = LocalContext.current
    var feedback by remember { mutableStateOf<EditorFeedback?>(null) }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2_600)
            feedback = null
        }
    }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
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
        if (keyPickerOpen) onDismissKeyPicker()
        if (draft != opened) confirmDiscard = true else onClose()
    }
    // Back pops one level: a catalog pane returns to the chain; the chain closes like the top-bar tile.
    BackHandler {
        if (keyPickerOpen) onDismissKeyPicker()
        else if (pane != EditorPane.CHAIN) pane = EditorPane.CHAIN
        else requestClose()
    }

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
                advancedModeActive = advancedModeActive,
            )
            EditorPane.ADD_CONSTRAINT -> ConstraintPickerScreen(
                automation = draft.automation,
                scriptId = draft.id,
                scripts = scripts,
                isPro = isPro,
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
                    isNew = draft.isNew,
                    canShare = draft.actions.isNotEmpty(),
                    onShare = onShare,
                    onDelete = { confirmDelete = true },
                    onClose = { requestClose() },
                )
                Box(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxSize()) {
                        ChainArea(
                            draft = draft,
                            scripts = scripts,
                            isPro = isPro,
                            onSetRunsIn = onSetRunsIn,
                            onOpenTriggerPicker = onOpenTriggerPicker,
                            onToggleAutomation = { onDismissKeyPicker(); onToggleAutomation() },
                            onClearSurfaceOnly = onClearSurfaceOnly,
                            onConfigureAction = { onDismissKeyPicker(); onConfigureAction(it) },
                            onRemoveAction = onRemoveAction,
                            onMoveAction = onMoveAction,
                            onOpenConstraintPicker = { onDismissKeyPicker(); pane = EditorPane.ADD_CONSTRAINT },
                            onRemoveConstraint = onRemoveConstraint,
                            onConfigureConstraint = { onDismissKeyPicker(); onConfigureConstraint(it) },
                            onSetConstraintParams = onSetConstraintParams,
                            onTestAction = onTestAction,
                            onTestResult = { feedback = it },
                            onSetUpAdvancedMode = onSetUpAdvancedMode,
                            installedApps = installedApps,
                            savedKeys = savedKeys,
                            advancedModeActive = advancedModeActive,
                            runs = runs,
                            onSetName = onSetName,
                            onOpenScriptSettings = onOpenScriptSettings,
                            onOpenAddAction = { onDismissKeyPicker(); pane = EditorPane.ADD_ACTION },
                            keyPickerOpen = keyPickerOpen,
                            isRecordingKey = isRecordingKey,
                            recordingKeys = recordingKeys,
                            onSetPressType = onSetPressType,
                            modifier = Modifier.weight(1f),
                        )
                        EditorFeedbackPill(feedback, Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp))
                        AnimatedContent(
                            targetState = keyPickerOpen,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
                            label = "inlineKeyDock",
                        ) { choosing ->
                        if (choosing) InlineKeyPicker(
                            savedKeys = savedKeys, isRecording = isRecordingKey,
                            onDismiss = onDismissKeyPicker, onSelectKey = onSelectKey,
                            onStartRecording = onStartRecording, onCancelRecording = onCancelRecording,
                            onSwitchToSurface = onSwitchToSurface,
                            advancedModeActive = advancedModeActive,
                            onSetUpAdvancedMode = onSetUpAdvancedMode,
                        ) else BottomToolbar(
                            canRun = draft.actions.isNotEmpty(),
                            onRunOnce = onRunOnce,
                            onDone = {
                                val fromNotification = ScriptConstraintCatalog.startsFromNotification(draft.automation, draft.constraints)
                                val error = when {
                                    draft.actions.isEmpty() -> R.string.editor_needs_action
                                    !draft.hasTrigger() -> R.string.editor_needs_trigger
                                    draft.automation && draft.constraints.isEmpty() -> R.string.chain_needs_condition
                                    draft.actions.any { ActionEditorForms.missingRequired(it, fromNotification).isNotEmpty() } -> R.string.chain_action_empty
                                    draft.constraints.any { !ConstraintEditorForms.isComplete(it.type, it.params) } -> R.string.chain_condition_empty
                                    else -> null
                                }
                                if (error == null) onDone() else feedback = EditorFeedback(context.getString(error), true)
                            },
                        )
                        }
                    }
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
    isNew: Boolean,
    canShare: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var pendingName by rememberSaveable { mutableStateOf(name) }
    var invalidName by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    fun cancelRename() {
        pendingName = name
        invalidName = false
        renaming = false
        focusManager.clearFocus()
    }
    fun applyName() {
        invalidName = pendingName.isBlank()
        if (!invalidName) {
            onSetName(pendingName.trim())
            renaming = false
            focusManager.clearFocus()
        }
    }
    BackHandler(renaming) { cancelRename() }
    LaunchedEffect(renaming) { if (renaming) focusRequester.requestFocus() }
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { if (renaming) cancelRename() else onClose() }) {
            Icon(Icons.Filled.Close, stringResource(if (renaming) R.string.shortcuts_rename_cancel else R.string.editor_discard_changes), tint = colors.onSurface)
        }
        if (renaming) {
            OutlinedTextField(
                value = pendingName,
                onValueChange = { pendingName = it; invalidName = false },
                modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("editor_inline_name"),
                label = { Text(stringResource(R.string.script_settings_name)) },
                singleLine = true,
                isError = invalidName,
                supportingText = if (invalidName) ({ Text(stringResource(R.string.shortcuts_name_required)) }) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { applyName() }),
            )
            IconButton(onClick = { applyName() }, modifier = Modifier.testTag("editor_apply_name")) {
                Icon(Icons.Filled.Check, stringResource(R.string.shortcuts_name_apply), tint = colors.primary)
            }
        } else {
            val renameDescription = stringResource(R.string.shortcuts_rename)
            Row(
                Modifier.weight(1f).heightIn(min = MinTouchTarget)
                    .clickable { pendingName = name; invalidName = false; renaming = true }
                    .semantics { contentDescription = renameDescription }
                    .testTag("editor_rename_title").padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    name.ifBlank { stringResource(R.string.scripts_new_shortcut) },
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
            }
        }
        if (!renaming) Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.editor_more_options, name.ifBlank { stringResource(R.string.scripts_new_shortcut) }), tint = colors.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_action)) },
                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                    enabled = canShare,
                    modifier = Modifier.testTag("share_script_button"),
                    onClick = { menuOpen = false; onShare() },
                )
                if (!isNew) DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete), color = colors.error) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = colors.error) },
                    modifier = Modifier.testTag("delete_script_button"),
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun ScriptNameField(name: String, placeholder: String, onSetName: (String) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    // The field has no container and no unfocused indicator, so without this glyph the name reads
    // as static text and nobody discovers the shortcut can be renamed. It goes once editing starts.
    val interactionSource = remember { MutableInteractionSource() }
    val nameFocused by interactionSource.collectIsFocusedAsState()
    TextField(
        value = name,
        onValueChange = onSetName,
        modifier = modifier.testTag("script_name_field"),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.titleSmall) },
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
}

@Composable
private fun ChainArea(
    draft: ScriptDraft,
    scripts: List<com.example.data.ShortcutScript>,
    isPro: Boolean,
    onSetRunsIn: (Int?) -> Unit,
    onOpenTriggerPicker: () -> Unit,
    onToggleAutomation: () -> Unit,
    onClearSurfaceOnly: () -> Unit,
    onConfigureAction: (Int) -> Unit,
    onRemoveAction: (Int) -> Unit,
    onMoveAction: (Int, Int) -> Unit,
    onOpenConstraintPicker: () -> Unit,
    onRemoveConstraint: (Int) -> Unit,
    onOpenAddAction: () -> Unit,
    modifier: Modifier = Modifier,
    keyPickerOpen: Boolean = false,
    isRecordingKey: Boolean = false,
    recordingKeys: List<Int> = emptyList(),
    onSetPressType: (TriggerPressType) -> Unit = {},
    onConfigureConstraint: (Int) -> Unit = {},
    onSetConstraintParams: (Int, Map<String, String>) -> Unit,
    onTestAction: suspend (ScriptAction) -> ActionTestResult,
    onTestResult: (EditorFeedback) -> Unit,
    onSetUpAdvancedMode: () -> Unit,
    installedApps: List<InstalledApp> = emptyList(),
    savedKeys: List<PhysicalKeyOption> = emptyList(),
    advancedModeActive: Boolean = false,
    runs: List<RunRecord> = emptyList(),
    onSetName: (String) -> Unit,
    onOpenScriptSettings: () -> Unit,
) {
    var openMenuFor by remember { mutableStateOf<Int?>(null) }
    var fineTuneExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val showHighlightHint = remember { highlightHintStillShown(context) }
    val colors = MaterialTheme.colorScheme
    val visibleConstraints = draft.constraints
    val conditionColor = sentenceConditionColor(visibleConstraints)
    val actionColor = sentenceActionColor(draft.actions)
    LazyColumn(
        modifier.fillMaxWidth().testTag("editor_chain").padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
    ) {
        item { ShortcutTypeSelector(draft, onToggleAutomation, onClearSurfaceOnly) }
        draft.noteRes?.let { note -> item { ChainHint(stringResource(note)) } }
        item {
            AnimatedContent(
                targetState = draft.takeUnless { it.automation },
                contentKey = { it != null },
                transitionSpec = {
                    (fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(100)))
                        .using(SizeTransform(clip = false))
                },
                label = "shortcutKeyClause",
            ) { keyDraft ->
                if (keyDraft != null) SentenceSection(stringResource(R.string.editor_chain_when), colors.onSurfaceVariant) {
                    TriggerCard(keyDraft, onOpenTriggerPicker, onClearSurfaceOnly,
                        keyPickerOpen, isRecordingKey, recordingKeys, onSetPressType, savedKeys = savedKeys)
                    if (!keyDraft.hasTrigger()) ChainHint(stringResource(R.string.editor_needs_trigger))
                }
            }
        }
        item {
            SentenceSection(stringResource(if (draft.automation) R.string.editor_chain_if else R.string.editor_chain_only_if), conditionColor,
                modifier = Modifier.alpha(if (keyPickerOpen) .4f else 1f)) {
                ConstraintsCard(
                    constraints = draft.constraints, onOpenPicker = onOpenConstraintPicker,
                    onRemove = onRemoveConstraint, onConfigure = onConfigureConstraint,
                    onSetParams = onSetConstraintParams,
                    installedApps = installedApps, automation = draft.automation,
                )
            }
        }
        item {
            SentenceSection(stringResource(R.string.editor_chain_do), actionColor, last = true,
                modifier = Modifier.alpha(if (keyPickerOpen) .4f else 1f)) {
                val everyChatIndex = ActionEditorForms.everyChatReplyIndex(draft.automation, draft.constraints, draft.actions)
                draft.actions.forEachIndexed { index, action ->
                    key(index, action) {
                        ActionChainCard(
                            action = action, index = index, total = draft.actions.size,
                            menuOpen = openMenuFor == index, advancedModeActive = advancedModeActive,
                            startsFromNotification = ScriptConstraintCatalog.startsFromNotification(draft.automation, draft.constraints),
                            onOpenMenu = { openMenuFor = index }, onDismissMenu = { openMenuFor = null },
                            onConfigure = { onConfigureAction(index) },
                            onRemove = { openMenuFor = null; onRemoveAction(index) },
                            onMoveUp = { onMoveAction(index, index - 1) },
                            onMoveDown = { onMoveAction(index, index + 1) },
                            onTestAction = onTestAction,
                            onTestResult = onTestResult,
                            onSetUpAdvancedMode = onSetUpAdvancedMode,
                        )
                    }
                    if (index == everyChatIndex) EveryChatWarning()
                }
                AddActionRow(onOpenAddAction)
                if (draft.actions.isEmpty()) ChainHint(stringResource(R.string.editor_needs_action))
            }
        }
        if (draft.automation) item { ChainHint(stringResource(R.string.automation_card_subtitle)) }
        if (showHighlightHint) item { ChainHint(stringResource(R.string.editor_highlight_hint)) }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
                    .border(1.dp, colors.outline.copy(alpha = .4f), RoundedCornerShape(20.dp)),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { fineTuneExpanded = !fineTuneExpanded }
                        .testTag("editor_fine_tune").padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Tune, null, tint = colors.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.editor_fine_tune), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                        Text(stringResource(R.string.editor_fine_tune_summary), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ExpandMore, null, tint = colors.onSurfaceVariant)
                }
                if (fineTuneExpanded) Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    ScriptNameField(draft.name, stringResource(R.string.editor_shortcut_name), onSetName, Modifier.fillMaxWidth())
                    if (ModeHooks.showsRunsIn(draft.id, scripts, isPro)) ModeHooks.RunsInField(
                        scriptId = draft.id, constraints = draft.constraints, scripts = scripts,
                        isPro = isPro, onChange = onSetRunsIn,
                    )
                    TextButton(onClick = onOpenScriptSettings, modifier = Modifier.testTag("script_settings_button")) {
                        Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.editor_shortcut_settings))
                    }
                }
            }
        }
        if (!draft.isNew) {
            item { ChainLabel(stringResource(R.string.editor_chain_runs), topPadding = 6.dp) }
            item { RunHistoryCard(runs, draft.trigger?.keyCodes.orEmpty()) }
        }
    }
}

@Composable
private fun ShortcutTypeSelector(draft: ScriptDraft, onToggleAutomation: () -> Unit, onClearSurfaceOnly: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val start = remember { Animatable(if (draft.automation) 1f else 0f) }
    val end = remember { Animatable(if (draft.automation) 2f else 1f) }
    LaunchedEffect(draft.automation, draft.surfaceOnly) {
        val target = if (draft.automation) 1f else 0f
        val towardEnd = target > start.value
        launch { start.animateTo(target, if (towardEnd) SelectionTrailSpring else SelectionLeadSpring) }
        launch { end.animateTo(target + 1f, if (towardEnd) SelectionLeadSpring else SelectionTrailSpring) }
    }
    val selectorModifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceVariant).padding(4.dp)
            .drawBehind {
                if (!draft.surfaceOnly) {
                    val gap = 4.dp.toPx()
                    val length = if (stacked) size.height else size.width
                    val step = (length + gap) / 2f
                    val left = start.value * step
                    val right = end.value * step - gap
                    val from = minOf(left, right).coerceIn(0f, length)
                    val to = maxOf(left, right).coerceIn(from, length)
                    val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - to else from
                    drawRoundRect(colors.surface,
                        if (stacked) Offset(0f, from) else Offset(x, 0f),
                        if (stacked) Size(size.width, to - from) else Size(to - from, size.height),
                        CornerRadius(14.dp.toPx()))
                }
            }.testTag("shortcut_type_selector").selectableGroup()
    val segment: @Composable (Boolean, Modifier) -> Unit = { automation, modifier ->
            val selected = !draft.surfaceOnly && draft.automation == automation
            val tint by animateColorAsState(
                if (selected) colors.onSurface else colors.onSurfaceVariant,
                animationSpec = tween(150), label = "shortcutTypeTint",
            )
            Row(
                modifier.clip(RoundedCornerShape(14.dp))
                    .selectable(selected, role = Role.RadioButton) {
                        if (automation != draft.automation) onToggleAutomation()
                        else if (!automation && draft.surfaceOnly) onClearSurfaceOnly()
                    }
                    .testTag(if (automation) "trigger_kind_automation" else "trigger_kind_key")
                    .heightIn(min = MinTouchTarget).padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(if (automation) Icons.Outlined.AutoAwesome else Icons.Outlined.Keyboard, null, Modifier.size(18.dp), tint = tint)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (automation) R.string.trigger_kind_automation else R.string.trigger_kind_key), style = MaterialTheme.typography.labelLarge, color = tint)
            }
    }
    if (stacked) Column(selectorModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(false, true).forEach { segment(it, Modifier.fillMaxWidth()) }
    } else Row(selectorModifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(false, true).forEach { segment(it, Modifier.weight(1f)) }
    }
}

@Composable
internal fun SentenceSection(
    title: String,
    tint: Color,
    last: Boolean = false,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    titleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    line: Color = MaterialTheme.colorScheme.outline.copy(alpha = .4f),
    lineWidth: Dp = 1.5.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().drawBehind {
            val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - 5.dp.toPx() else 5.dp.toPx()
            drawCircle(tint, 5.dp.toPx(), Offset(x, 10.dp.toPx()))
            if (!last) drawLine(line, Offset(x, 24.dp.toPx()), Offset(x, size.height), lineWidth.toPx())
        }.padding(start = 24.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(
            targetState = title,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
            label = "shortcutClauseTitle",
        ) { label ->
            Text(label, style = titleStyle, fontWeight = FontWeight.SemiBold, color = titleColor)
        }
        content()
    }
}

@Composable
internal fun sentenceConditionColor(constraints: List<ScriptConstraint>): Color {
    val category = constraints.firstOrNull()?.let { ScriptConstraintCatalog.descriptor(it.type)?.category }
    return category?.let { categoryTileColors(constraintTileCategory(it)).second }
        ?: MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun sentenceActionColor(actions: List<ScriptAction>): Color =
    categoryTileColors(actions.firstOrNull()?.let { ScriptActionCatalog.descriptor(it.type)?.category }).second

/** "Tap a highlighted word" is for the first few editor visits only; after that it is learned. */
private fun highlightHintStillShown(context: android.content.Context): Boolean {
    val prefs = context.getSharedPreferences("editor_hints", android.content.Context.MODE_PRIVATE)
    val seen = prefs.getInt("highlight_hint_seen", 0)
    if (seen >= HIGHLIGHT_HINT_VISITS) return false
    prefs.edit().putInt("highlight_hint_seen", seen + 1).apply()
    return true
}

private const val HIGHLIGHT_HINT_VISITS = 3

/** Why the Done button is greyed out, shown under whichever section is still empty. */
@Composable
private fun ChainHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** See [ActionEditorForms.everyChatReplyIndex]; also shown in the import/share review. */
@Composable
internal fun EveryChatWarning(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.error
    Row(modifier.testTag("every_chat_warning"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.WarningAmber, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.warning_reply_every_chat), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** Dashed, Shortcuts-style call to action that opens the full-screen action catalog. */
@Composable
private fun AddActionRow(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
                        .dashedBorder(colors.outline, RoundedCornerShape(14.dp))
            .heightIn(min = MinTouchTarget)
            .clickable(onClickLabel = stringResource(R.string.editor_add_action), onClick = onClick)
            .testTag("add_action_button")
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.editor_add_action), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun TriggerCard(
    draft: ScriptDraft, onOpenKeyPicker: () -> Unit, onClearSurfaceOnly: () -> Unit,
    choosing: Boolean, recording: Boolean, heldKeys: List<Int>, onSetPressType: (TriggerPressType) -> Unit,
    savedKeys: List<PhysicalKeyOption> = emptyList(),
) {
    val colors = MaterialTheme.colorScheme
    val res = LocalContext.current.resources
    val trigger = draft.trigger
    val assistant = trigger?.keyCodes?.contains(KeyEventMapping.ASSISTANT_GESTURE) == true
    val tint by animateColorAsState(if (recording) colors.error else colors.primary, label = "inlineKeyTint")
    var pressMenu by remember { mutableStateOf(false) }
    val keyFocus = remember { BringIntoViewRequester() }
    LaunchedEffect(choosing, heldKeys) {
        if (choosing) { delay(220); keyFocus.bringIntoView() }
    }
    if (draft.surfaceOnly) {
        KeylessTriggerCard(Icons.Outlined.Widgets, stringResource(R.string.trigger_kind_surface), stringResource(R.string.surface_card_subtitle), onClearSurfaceOnly)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.editor_press), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, modifier = Modifier.padding(vertical = 8.dp))
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .12f))
                .then(if (choosing) Modifier.dashedBorder(tint, RoundedCornerShape(14.dp)) else Modifier)
                .clickable(onClick = onOpenKeyPicker).testTag("assign_key_card")
                .bringIntoViewRequester(keyFocus)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .heightIn(min = MinTouchTarget).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (assistant && !recording) Icons.Outlined.AutoAwesome else Icons.Outlined.Keyboard, null, Modifier.size(22.dp), tint = tint)
            Spacer(Modifier.width(8.dp))
            val shownKeys = if (recording) heldKeys else trigger?.keyCodes.orEmpty()
            Text(if (shownKeys.isNotEmpty()) shownKeys.joinToString(" + ") { keyDisplayName(res, it, savedKeys) }
                else stringResource(if (recording) R.string.inline_key_listening else R.string.inline_key_placeholder),
                Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = tint)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = tint)
        }
        Box {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(colors.primary.copy(alpha = .12f))
                .clickable(enabled = !assistant && !recording) { pressMenu = true }
                .testTag("trigger_press_menu").heightIn(min = MinTouchTarget).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource((trigger?.pressType ?: draft.pressType).titleRes), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.primary)
            Icon(if (assistant) Icons.Outlined.Lock else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = colors.primary)
        }
        DropdownMenu(expanded = pressMenu && !assistant && !recording, onDismissRequest = { pressMenu = false }) {
            TriggerPressType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.titleRes)) },
                    modifier = Modifier.testTag("press_type_${type.name}"),
                    onClick = { pressMenu = false; onSetPressType(type) },
                )
            }
        }
        }
    }
    if (recording) ChainHint(stringResource(R.string.inline_key_release))
    else if (assistant) {
        ChainHint(stringResource(R.string.assistant_trigger_hint))
        AssistantSettingsRow()
        Text(stringResource(R.string.assistant_voice_wakeup_warning), style = MaterialTheme.typography.bodySmall, color = colors.error)
    } else if (trigger != null) ChainHint(stringResource(R.string.inline_key_edit))
    if (trigger != null && trigger.keyCodes.size >= 2) {
        ChainHint(stringResource(R.string.chord_consume_hint))
        if (KeyEvent.KEYCODE_POWER in trigger.keyCodes) ChainHint(stringResource(R.string.chord_power_hint))
    }
}

@Composable
private fun KeylessTriggerCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(colors.primary.copy(alpha = .12f))
            .heightIn(min = MinTouchTarget)
            .clickable(onClick = onClick)
            .testTag("assign_key_card")
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, size = 36.dp, selected = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = colors.onSurfaceVariant)
    }
}

@Composable
internal fun ConstraintsCard(
    constraints: List<ScriptConstraint>,
    onOpenPicker: () -> Unit,
    onRemove: (Int) -> Unit,
    onConfigure: (Int) -> Unit,
    installedApps: List<InstalledApp>,
    automation: Boolean,
    addTestTag: String = "add_constraint_button",
    // The mode sheet lists conditions under a sentence that is not the page's subject, so smaller there.
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    onSetParams: ((Int, Map<String, String>) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val resources = LocalContext.current.resources
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("constraints_card"),
    ) {
        val visible = constraints.mapIndexed { index, constraint -> index to constraint }
        if (visible.isEmpty()) {
            Text(
                stringResource(if (automation) R.string.editor_add_constraint else R.string.editor_always_runs),
                Modifier.padding(vertical = 4.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        } else {
            visible.forEach { (index, constraint) ->
                val descriptor = ScriptConstraintCatalog.descriptor(constraint.type)
                val title = resources.constraintLabel(constraint)
                val incomplete = !ConstraintEditorForms.isComplete(constraint.type, constraint.params)
                val outline by animateColorAsState(if (incomplete) colors.error else Color.Transparent, label = "constraintErrorOutline")
                val configurable = ConstraintEditorForms.form(constraint.type) != null
                val summary = ConstraintEditorForms.summarize(constraint, LocalContext.current)
                val (fill, glyph) = categoryTileColors(descriptor?.category?.let(::constraintTileCategory))
                val description: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier) {
                        Text(title, color = glyph, style = titleStyle)
                        if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                val remove: @Composable () -> Unit = {
                    if (ScriptConstraintCatalog.supportsNegation(constraint.type) && onSetParams != null) {
                        ConstraintNegationToggle(
                            ConstraintNegation.requested(constraint.params),
                            { negated -> onSetParams(index, if (negated) constraint.params + ("negate" to "true") else constraint.params - "negate") },
                            Modifier.testTag("constraint_negation_$index"),
                        )
                    }
                    IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(MinTouchTarget).testTag("remove_constraint_$index")) {
                        Icon(Icons.Filled.Close, stringResource(R.string.editor_remove_constraint, title), Modifier.size(18.dp), tint = colors.error)
                    }
                }
                BoxWithConstraints(
                    Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(14.dp)).background(fill)
                        .border(1.dp, outline, RoundedCornerShape(14.dp))
                        .clickable(enabled = configurable, onClickLabel = stringResource(R.string.editor_configure_action, title)) { onConfigure(index) }
                        .testTag("constraint_row_$index"),
                ) {
                    if (maxWidth < 280.dp && LocalDensity.current.fontScale > 1.3f) Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(constraintIcon(constraint.type, descriptor?.category), null, Modifier.size(22.dp), tint = glyph)
                            Spacer(Modifier.weight(1f))
                            remove()
                        }
                        description(Modifier.fillMaxWidth())
                    } else Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(constraintIcon(constraint.type, descriptor?.category), null, Modifier.size(22.dp), tint = glyph)
                        Spacer(Modifier.width(10.dp))
                        description(Modifier.weight(1f))
                        remove()
                    }
                }
                RowConfigError(incomplete, action = false, Modifier.testTag("constraint_error_$index"))
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(
            Modifier
                .dashedBorder(colors.outline, RoundedCornerShape(14.dp))
                .heightIn(min = MinTouchTarget)
                .clickable(onClick = onOpenPicker)
                .testTag(addTestTag)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.editor_add_constraint), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * One step in the Do chain. The row itself opens the action's settings; reordering and removal live
 * behind one overflow button, so a phone-width row is a name and a summary rather than four 32dp
 * icon buttons competing with the text for space.
 */
@Composable
internal fun ActionChainCard(
    action: ScriptAction,
    index: Int,
    total: Int,
    menuOpen: Boolean,
    advancedModeActive: Boolean,
    startsFromNotification: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onConfigure: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onTestAction: suspend (ScriptAction) -> ActionTestResult,
    onTestResult: (EditorFeedback) -> Unit,
    onSetUpAdvancedMode: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val descriptor = ScriptActionCatalog.descriptor(action.type)
    val title = descriptor?.let { stringResource(it.titleRes) } ?: action.type
    val configurable = ActionEditorForms.isConfigurable(action.type) || action.type in ASSISTANT_SETTINGS_ACTION_TYPES
    val redirectsAssistant = action.type in ASSISTANT_SETTINGS_ACTION_TYPES && rememberAssistantSelected().value
    // Scripts saved before configuration existed can be missing required params; surface that
    // rather than letting the action fail silently at run time.
    val incomplete = ActionEditorForms.missingRequired(action, startsFromNotification).isNotEmpty()
    val outline by animateColorAsState(if (incomplete) colors.error else Color.Transparent, label = "actionErrorOutline")
    val summary = ActionEditorForms.summarize(action, LocalContext.current)
    val (fill, glyph) = categoryTileColors(descriptor?.category)
    val description: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = glyph,
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
                    )
                }
                if (action.type != "shell_command" && ActionEditorForms.needsAdvancedMode(action.type)) AdvancedModeChip(advancedModeActive)
            }
            if (redirectsAssistant) {
                Text(
                    stringResource(R.string.assistant_redirect_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
    val menu: @Composable () -> Unit = {
        if (canTestChainAction(action)) {
            ChainActionTestButton(action, index, !incomplete, onTestAction, onTestResult)
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
    BoxWithConstraints(
        Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(14.dp))
            .border(1.dp, outline, RoundedCornerShape(14.dp))
            .background(fill).testTag("chain_action_$index"),
    ) {
        val compact = maxWidth < 280.dp && LocalDensity.current.fontScale > 1.3f
        val configure = if (configurable) onConfigure else onOpenMenu
        val label = stringResource(if (configurable) R.string.editor_configure_action else R.string.editor_more_options, title)
        if (compact) Column(Modifier.fillMaxWidth().clickable(onClickLabel = label, onClick = configure).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(actionIcon(action.type, descriptor?.category), null, Modifier.size(22.dp), tint = glyph)
                Spacer(Modifier.weight(1f))
                menu()
            }
            description(Modifier.fillMaxWidth())
        } else Row(
            Modifier.heightIn(min = MinTouchTarget).clickable(onClickLabel = label, onClick = configure)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(actionIcon(action.type, descriptor?.category), null, Modifier.size(22.dp), tint = glyph)
            Spacer(Modifier.width(12.dp))
            description(Modifier.weight(1f))
            menu()
        }
    }
    RowConfigError(incomplete, action = true, Modifier.testTag("action_error_$index"))
    ShellLimitedWarning(action.type == "shell_command" && shellRunsIn(advancedModeActive) == ShellContext.APP_LIMITED, onSetUpAdvancedMode)
    Spacer(Modifier.height(8.dp))
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
@OptIn(ExperimentalLayoutApi::class)
private fun BottomToolbar(canRun: Boolean, onRunOnce: () -> Unit, onDone: () -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val testDescription = stringResource(R.string.editor_test_run)
    FlowRow(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = if (LocalDensity.current.fontScale > 2f && LocalConfiguration.current.screenWidthDp < 600) 1 else 2,
    ) {
        OutlinedButton(onClick = onRunOnce, enabled = canRun, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp), modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("run_once_button").semantics { contentDescription = testDescription }) {
            if (!largeText) {
                Icon(Icons.Outlined.PlayCircleOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(stringResource(R.string.editor_test), textAlign = TextAlign.Center)
        }
        Button(onClick = onDone, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), modifier = Modifier.weight(1.8f).heightIn(min = 52.dp).testTag("script_done_button")) {
            Text(stringResource(if (largeText) R.string.common_save else R.string.editor_save_shortcut), textAlign = TextAlign.Center)
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
    advancedModeActive: Boolean = false,
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
        val offered = (if (searching) ScriptActionCatalog.search(query, LocalContext.current.resources) else ScriptActionCatalog.all)
            .filter { ModeHooks.offered(it.type) }
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
                    ActionRow(entries[i], onPickAction, advancedModeWarningRes(entries[i].type, advancedModeActive))
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
    // The styled box is the text field itself, so a tap anywhere in it focuses the field.
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .clip(InnerShape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outline.copy(alpha = .4f), InnerShape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    inner()
                }
            }
        },
    )
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
    // At least as wide as the tile, wider for a label that needs it: the rail scrolls, so a long
    // translation costs a little travel instead of being cut to "Taschenla…".
    Column(
        Modifier
            .widthIn(min = 62.dp)
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
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActionRow(descriptor: ActionDescriptor, onPickAction: (String) -> Unit, @StringRes warningRes: Int? = null) {
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
            } else if (warningRes != null) {
                Row(
                    Modifier.padding(top = 1.dp).testTag("action_row_warning_${descriptor.type}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Error, null, tint = colors.error, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(warningRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                        maxLines = 2,
                    )
                }
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
