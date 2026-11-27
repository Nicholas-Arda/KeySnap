package com.example.ui.scripts

import android.view.KeyEvent
import android.widget.Toast
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import com.example.service.AutomationUndo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PhoneAndroid
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
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
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
import com.example.data.OemActions
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
import com.example.ui.components.LeftColumn
import com.example.ui.components.TwoColumns
import com.example.ui.components.WideGutter
import com.example.ui.components.WidePagePadding
import com.example.ui.components.WidePanel
import com.example.ui.components.isWideLayout
import com.example.ui.theme.SectionLabel
import com.example.ui.theme.categoryTileColors
import io.github.nicholasarda.keysnap.R
import com.example.viewmodel.ScriptDraft
import com.example.viewmodel.hasTrigger
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.collectAsState
import com.example.bridge.LearnedSetting
import com.example.data.LearnedSettings
import com.example.data.LearnedSwitch

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
    openPickerOn: PickerTab? = null,
    onPickerOpened: () -> Unit = {},
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
    onSetActionUndo: (Int, Boolean) -> Unit = { _, _ -> },
    onSetAllUndo: (Boolean) -> Unit = {},
    onSetMatchAny: (Boolean) -> Unit = {},
    // "When it ends" counterparts of the action callbacks above; see [ShortcutScript.endActions].
    onAddEndAction: (String, Map<String, String>) -> Unit = { _, _ -> },
    onConfigureNewEndAction: (String) -> Unit = {},
    onConfigureEndAction: (Int) -> Unit = {},
    onRemoveEndAction: (Int) -> Unit = {},
    onMoveEndAction: (Int, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var feedback by remember { mutableStateOf<EditorFeedback?>(null) }
    var saveRefused by remember { mutableStateOf(false) }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2_600)
            feedback = null
            saveRefused = false
        }
    }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
        onConsumeMessage()
    }
    // Saveable: folding or unfolding the phone recreates the activity, and an open picker stays open.
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<ActionCategory?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pane by rememberSaveable { mutableStateOf(if (openPickerOn != null) EditorPane.ADD_ACTION else EditorPane.CHAIN) }
    // Which list the action picker adds to: Do, or an automation's "When it ends".
    var addingToEnd by rememberSaveable { mutableStateOf(false) }
    // Read once, when the editor opens; the caller forgets it so the next shortcut opens normally.
    val pickerTab by rememberSaveable { mutableStateOf(openPickerOn) }
    LaunchedEffect(Unit) { if (openPickerOn != null) onPickerOpened() }
    // A wide screen keeps the chain on screen and opens the catalogs as a panel over it.
    val wide = isWideLayout()
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
    val addActionPane = @Composable {
        AddActionScreen(
            query = query,
            onQueryChange = { query = it },
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            onPickAction = { type ->
                pane = EditorPane.CHAIN
                when {
                    !ActionEditorForms.isConfigurable(type) -> if (addingToEnd) onAddEndAction(type, emptyMap()) else onAddAction(type, emptyMap())
                    addingToEnd -> onConfigureNewEndAction(type)
                    else -> onConfigureNewAction(type)
                }
            },
            onPickLearned = { params ->
                pane = EditorPane.CHAIN
                if (addingToEnd) onAddEndAction(ScriptActionCatalog.LEARNED_SETTING, params) else onAddAction(ScriptActionCatalog.LEARNED_SETTING, params)
            },
            onClose = { pane = EditorPane.CHAIN },
            advancedModeActive = advancedModeActive,
            initialTab = pickerTab,
        )
    }
    val addConstraintPane = @Composable {
        ConstraintPickerScreen(
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
    }
    Box(Modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = if (wide) EditorPane.CHAIN else pane,
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
            EditorPane.ADD_ACTION -> addActionPane()
            EditorPane.ADD_CONSTRAINT -> addConstraintPane()
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
                            onSetActionUndo = onSetActionUndo,
                            onSetAllUndo = onSetAllUndo,
                            onSetMatchAny = onSetMatchAny,
                            onOpenConstraintPicker = { onDismissKeyPicker(); pane = EditorPane.ADD_CONSTRAINT },
                            onRemoveConstraint = onRemoveConstraint,
                            onConfigureConstraint = { onDismissKeyPicker(); onConfigureConstraint(it) },
                            onSetConstraintParams = onSetConstraintParams,
                            onTestAction = onTestAction,
                            onTestResult = { saveRefused = false; feedback = it },
                            onSetUpAdvancedMode = onSetUpAdvancedMode,
                            installedApps = installedApps,
                            savedKeys = savedKeys,
                            advancedModeActive = advancedModeActive,
                            runs = runs,
                            onSetName = onSetName,
                            onOpenScriptSettings = onOpenScriptSettings,
                            onOpenAddAction = { onDismissKeyPicker(); addingToEnd = false; pane = EditorPane.ADD_ACTION },
                            onOpenAddEndAction = { onDismissKeyPicker(); addingToEnd = true; pane = EditorPane.ADD_ACTION },
                            onConfigureEndAction = { onDismissKeyPicker(); onConfigureEndAction(it) },
                            onRemoveEndAction = onRemoveEndAction,
                            onMoveEndAction = onMoveEndAction,
                            keyPickerOpen = keyPickerOpen,
                            isRecordingKey = isRecordingKey,
                            recordingKeys = recordingKeys,
                            onSetPressType = onSetPressType,
                            modifier = Modifier.weight(1f),
                            flagMissing = saveRefused && feedback != null,
                        )
                        var showSystemOptimization by remember { mutableStateOf(false) }
                        if (showSystemOptimization) SystemOptimizationSheet(onDismiss = { showSystemOptimization = false })
                        EditorFeedbackPill(
                            feedback, Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp),
                            onFix = { showSystemOptimization = true },
                        )
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
                            wide = wide,
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
                                saveRefused = error != null
                                if (error == null) onDone() else feedback = EditorFeedback(context.getString(error), true)
                            },
                        )
                        }
                    }
                }
            }
        }
    }
    if (wide) WidePanel(pane.takeIf { it != EditorPane.CHAIN }, onDismiss = { pane = EditorPane.CHAIN }) { shown ->
        if (shown == EditorPane.ADD_ACTION) addActionPane() else addConstraintPane()
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
    onSetActionUndo: (Int, Boolean) -> Unit,
    onSetAllUndo: (Boolean) -> Unit,
    onSetMatchAny: (Boolean) -> Unit,
    onOpenConstraintPicker: () -> Unit,
    onRemoveConstraint: (Int) -> Unit,
    onOpenAddAction: () -> Unit,
    onOpenAddEndAction: () -> Unit = {},
    onConfigureEndAction: (Int) -> Unit = {},
    onRemoveEndAction: (Int) -> Unit = {},
    onMoveEndAction: (Int, Int) -> Unit = { _, _ -> },
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
    flagMissing: Boolean = false,
) {
    var openMenuFor by remember { mutableStateOf<Int?>(null) }
    var fineTuneExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val showHighlightHint = remember { highlightHintStillShown(context) }
    val colors = MaterialTheme.colorScheme
    val visibleConstraints = draft.constraints
    val conditionColor = sentenceConditionColor(visibleConstraints)
    // All / Any only means something with two real conditions; the cooldown is a gate, not one.
    val anyOffered = draft.constraints.count { it.type != ScriptConstraintCatalog.COOLDOWN } >= 2
    val actionColor = sentenceActionColor(draft.actions)
    val everyChatIndex = ActionEditorForms.everyChatReplyIndex(draft.automation, draft.constraints, draft.actions)
    val undoOffered = AutomationUndo.offered(draft.automation, draft.constraints)
    val undoable = draft.actions.filter(AutomationUndo::canUndo)
    val undoing = undoable.count { it.undoOnEnd }
    // Wide: conditions side by side (their order does not matter), everything else in the left column.
    // At Fold width the actions keep a phone's width with the options beside them; narrower, the
    // actions take the full width so their names do not wrap.
    val wide = isWideLayout()
    val sideOptions = wide && LocalConfiguration.current.screenWidthDp >= 800
    val half: @Composable (@Composable () -> Unit) -> Unit = { content -> if (wide) LeftColumn { content() } else content() }
    val doSection = @Composable {
        SentenceSection(stringResource(R.string.editor_chain_do), actionColor, last = !draft.automation,
            modifier = Modifier.alpha(if (keyPickerOpen) .4f else 1f)) {
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
                        undoBadge = when {
                            !undoOffered || undoing == 0 -> null
                            !AutomationUndo.canUndo(action) -> R.string.undo_badge_cant
                            action.undoOnEnd -> R.string.undo_badge_on
                            else -> R.string.undo_badge_off
                        },
                        onSetUndo = if (undoOffered && AutomationUndo.canUndo(action)) { undo -> onSetActionUndo(index, undo) } else null,
                    )
                }
                if (index == everyChatIndex) EveryChatWarning()
            }
            AddActionRow(onOpenAddAction)
            if (!sideOptions && undoOffered && undoable.isNotEmpty()) UndoWhenItEndsRow(undoing, undoable.size, onSetAllUndo)
            if (draft.actions.isEmpty()) ChainHint(stringResource(R.string.editor_needs_action), flagMissing)
        }
    }
    // An automation's "When it ends": its own cards and menu, no undo badges (nothing to put back).
    var openEndMenuFor by remember { mutableStateOf<Int?>(null) }
    val endSection = @Composable {
        SentenceSection(stringResource(R.string.editor_chain_when_it_ends), sentenceActionColor(draft.endActions), last = true,
            modifier = Modifier.alpha(if (keyPickerOpen) .4f else 1f).testTag("editor_when_it_ends")) {
            draft.endActions.forEachIndexed { index, action ->
                key("end", index, action) {
                    ActionChainCard(
                        action = action, index = index, total = draft.endActions.size,
                        menuOpen = openEndMenuFor == index, advancedModeActive = advancedModeActive,
                        startsFromNotification = false,
                        onOpenMenu = { openEndMenuFor = index }, onDismissMenu = { openEndMenuFor = null },
                        onConfigure = { onConfigureEndAction(index) },
                        onRemove = { openEndMenuFor = null; onRemoveEndAction(index) },
                        onMoveUp = { onMoveEndAction(index, index - 1) },
                        onMoveDown = { onMoveEndAction(index, index + 1) },
                        onTestAction = onTestAction,
                        onTestResult = onTestResult,
                        onSetUpAdvancedMode = onSetUpAdvancedMode,
                        undoBadge = null,
                        onSetUndo = null,
                    )
                }
            }
            AddActionRow(onOpenAddEndAction)
        }
    }
    val fineTune = @Composable {
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
    LazyColumn(
        modifier.fillMaxWidth().testTag("editor_chain").padding(horizontal = if (wide) WidePagePadding else 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
    ) {
        item { half { ShortcutTypeSelector(draft, onToggleAutomation, onClearSurfaceOnly) } }
        draft.noteRes?.let { note -> item { half { ChainHint(stringResource(note)) } } }
        item {
            half {
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
                        if (!keyDraft.hasTrigger()) ChainHint(stringResource(R.string.editor_needs_trigger), flagMissing)
                    }
                }
            }
        }
        item {
            SentenceSection(stringResource(if (draft.automation) R.string.editor_chain_if else R.string.editor_chain_only_if), conditionColor,
                modifier = Modifier.alpha(if (keyPickerOpen) .4f else 1f),
                trailing = { AnimatedVisibility(anyOffered, enter = fadeIn(), exit = fadeOut()) { MatchModeToggle(draft.matchAny, onSetMatchAny) } }) {
                ConstraintsCard(
                    constraints = draft.constraints, onOpenPicker = onOpenConstraintPicker,
                    onRemove = onRemoveConstraint, onConfigure = onConfigureConstraint,
                    onSetParams = onSetConstraintParams,
                    installedApps = installedApps, automation = draft.automation,
                    joins = anyOffered, matchAny = draft.matchAny,
                    columns = if (wide) 2 else 1,
                )
            }
        }
        item {
            if (sideOptions) Row(horizontalArrangement = Arrangement.spacedBy(WideGutter)) {
                Box(Modifier.weight(1f)) { doSection() }
                // Level with the first action, below the section's title.
                Column(Modifier.weight(1f).padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (undoOffered && undoable.isNotEmpty()) UndoWhenItEndsRow(undoing, undoable.size, onSetAllUndo)
                    fineTune()
                }
            } else doSection()
        }
        if (draft.automation) item {
            half { ChainHint(stringResource(if (anyOffered && draft.matchAny) R.string.automation_card_subtitle_any else R.string.automation_card_subtitle)) }
        }
        if (draft.automation) item { half { endSection() } }
        // An empty automation has no highlighted word yet (the key clause is what a key press highlights).
        if (showHighlightHint && (!draft.automation || draft.constraints.isNotEmpty())) {
            item { half { ChainHint(stringResource(R.string.editor_highlight_hint)) } }
        }
        if (!sideOptions) item { half { fineTune() } }
        if (!draft.isNew) {
            item { half { ChainLabel(stringResource(R.string.editor_chain_runs), topPadding = 6.dp) } }
            item { half { RunHistoryCard(runs, draft.trigger?.keyCodes.orEmpty()) } }
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

/** How far a sentence section's content sits right of its timeline dot. */
private val SentenceInset = 24.dp

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
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().drawBehind {
            val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - 5.dp.toPx() else 5.dp.toPx()
            drawCircle(tint, 5.dp.toPx(), Offset(x, 10.dp.toPx()))
            if (!last) drawLine(line, Offset(x, 24.dp.toPx()), Offset(x, size.height), lineWidth.toPx())
        }.padding(start = SentenceInset, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = title,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                label = "shortcutClauseTitle",
                modifier = Modifier.weight(1f),
            ) { label ->
                Text(label, style = titleStyle, fontWeight = FontWeight.SemiBold, color = titleColor)
            }
            trailing?.invoke()
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
private fun ChainHint(text: String, flagged: Boolean = false) {
    // A Save refused for this missing piece turns its hint red while the reason pill shows.
    val color by animateColorAsState(
        if (flagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chainHintColor",
    )
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
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
    enabled: Boolean = true,
    valueContent: @Composable (String, String, Color, @Composable (String, Color) -> Unit) -> Unit = { _, value, tint, content -> content(value, tint) },
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
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
        Text(
            stringResource(R.string.editor_press),
            style = textStyle.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
            color = colors.onSurface,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        val shownKeys = if (recording) heldKeys else trigger?.keyCodes.orEmpty()
        val keyText = if (shownKeys.isNotEmpty()) shownKeys.joinToString(" + ") { keyDisplayName(res, it, savedKeys) }
            else stringResource(if (recording) R.string.inline_key_listening else R.string.inline_key_placeholder)
        // An empty slot reads as pending: dashed outline, muted fill, placeholder ink.
        val empty = shownKeys.isEmpty() && !recording
        valueContent("keys", keyText, if (empty) colors.onSurfaceVariant else tint) { value, ink ->
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(ink.copy(alpha = if (empty) .04f else .12f))
                .then(if (choosing) Modifier.dashedBorder(tint, RoundedCornerShape(14.dp)) else if (empty) Modifier.dashedBorder(colors.outline, RoundedCornerShape(14.dp)) else Modifier)
                .clickable(enabled = enabled, onClick = onOpenKeyPicker).testTag("assign_key_card")
                .bringIntoViewRequester(keyFocus)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .heightIn(min = MinTouchTarget).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (assistant && !recording) Icons.Outlined.AutoAwesome else Icons.Outlined.Keyboard, null, Modifier.size(22.dp), tint = ink)
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                Modifier.weight(1f, fill = false),
                style = textStyle.copy(fontWeight = FontWeight.SemiBold, hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                color = ink,
            )
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = ink)
        }
        }
        Box {
        valueContent("press", stringResource((trigger?.pressType ?: draft.pressType).titleRes), colors.primary) { value, ink ->
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(ink.copy(alpha = .12f))
                .clickable(enabled = enabled && !assistant && !recording) { pressMenu = true }
                .testTag("trigger_press_menu").heightIn(min = MinTouchTarget).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, Modifier.weight(1f, fill = false), style = textStyle.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(if (assistant) Icons.Outlined.Lock else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = ink)
        }
        }
        DropdownMenu(expanded = pressMenu && enabled && !assistant && !recording, onDismissRequest = { pressMenu = false }) {
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
    /** Shows AND / OR between the rows; [matchAny] picks which. */
    joins: Boolean = false,
    matchAny: Boolean = false,
    /** 2 lays the conditions out side by side, as a wide editor does; their order does not matter. */
    columns: Int = 1,
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
            // An empty automation shows only the "+ Add condition" button below; the same words as a
            // headline above it read twice.
            if (!automation) Text(
                stringResource(R.string.editor_always_runs),
                Modifier.padding(vertical = 4.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph,
                ),
            )
        } else {
            val row: @Composable (Int, ScriptConstraint) -> Unit = { index, constraint ->
                // Side by side, every condition keeps the join's line so the cards stay level.
                if (joins && (index > 0 || columns > 1)) Box(Modifier.heightIn(min = if (columns > 1) 22.dp else 0.dp)) {
                    // The cooldown is always a gate, even under Any.
                    val or = matchAny && constraint.type != ScriptConstraintCatalog.COOLDOWN
                    if (index > 0) AnimatedContent(or, label = "constraintJoin") { isOr ->
                        Text(
                            stringResource(if (isOr) R.string.constraint_join_or else R.string.constraint_join_and),
                            Modifier.padding(start = 14.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                    }
                }
                val descriptor = ScriptConstraintCatalog.descriptor(constraint.type)
                val title = resources.constraintLabel(constraint)
                val incomplete = !ConstraintEditorForms.isComplete(constraint.type, constraint.params)
                val outline by animateColorAsState(if (incomplete) colors.error else Color.Transparent, label = "constraintErrorOutline")
                val configurable = ConstraintEditorForms.form(constraint.type) != null
                val summary = ConstraintEditorForms.summarize(constraint, LocalContext.current)
                val (fill, glyph) = categoryTileColors(descriptor?.category?.let(::constraintTileCategory))
                val description: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier) {
                        Text(title, color = glyph, style = titleStyle.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph))
                        if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = colors.onSurfaceVariant)
                        if (constraint.type == ScriptConstraintCatalog.AT_PLACE && !incomplete) PlaceStatusLine(constraint.params)
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
                        // A gap so a long wrapped title never touches the IS/NOT toggle.
                        description(Modifier.weight(1f).padding(end = 8.dp))
                        remove()
                    }
                }
                RowConfigError(incomplete, action = false, Modifier.testTag("constraint_error_$index"))
                Spacer(Modifier.height(8.dp))
            }
            // Inside a sentence section: its inset comes off the left column so the gap stays centred.
            if (columns > 1) TwoColumns(visible, rowGap = 0.dp, startInset = SentenceInset) { (index, constraint) -> Column { row(index, constraint) } }
            else visible.forEach { (index, constraint) -> row(index, constraint) }
        }
        val add = @Composable {
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
        if (columns > 1) LeftColumn { add() } else add()
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
    /** "Undone" / "Stays" / "Can't undo" while any action of this automation is undone; null hides it. */
    @StringRes undoBadge: Int? = null,
    /** Shows the menu's "Undo when it ends" switch; null when this action or this If can't be undone. */
    onSetUndo: ((Boolean) -> Unit)? = null,
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
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph,
                ),
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
                        style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                        color = colors.error,
                    )
                    summary != null -> Text(
                        summary,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                        color = colors.onSurfaceVariant,
                    )
                }
                if (action.type != "shell_command" && ActionEditorForms.needsAdvancedMode(action.type)) AdvancedModeChip(advancedModeActive)
                undoBadge?.let { UndoBadge(it, on = it == R.string.undo_badge_on) }
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
                if (onSetUndo != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.undo_when_it_ends)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Undo, null) },
                        trailingIcon = { Switch(checked = action.undoOnEnd, onCheckedChange = null, modifier = Modifier.scale(.8f)) },
                        modifier = Modifier.testTag("undo_action_$index"),
                        onClick = { onSetUndo(!action.undoOnEnd) },
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
    BluetoothPromptWarning(action.type)
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
private fun BottomToolbar(canRun: Boolean, onRunOnce: () -> Unit, onDone: () -> Unit, wide: Boolean = false) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val testDescription = stringResource(R.string.editor_test_run)
    val width = LocalConfiguration.current.screenWidthDp.dp
    FlowRow(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding()
            // Wide: Test and Save sit in the right column, under the options.
            .padding(start = if (wide) width / 2 + WideGutter / 2 else 22.dp, end = if (wide) WidePagePadding else 22.dp)
            .padding(vertical = 10.dp),
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
    onPickLearned: (Map<String, String>) -> Unit = {},
    initialTab: PickerTab? = null,
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
        val context = LocalContext.current
        // Saveable: folding or unfolding the phone recreates the activity. Opens on "All".
        var tab by rememberSaveable { mutableStateOf(initialTab) }
        val brand = remember { OemActions.brandLabel(Build.MANUFACTURER.orEmpty()) }
        val catalog = ScriptActionCatalog.all
            .filter { ModeHooks.offered(it.type) && it.type != ScriptActionCatalog.LEARNED_SETTING && OemActions.offered(context, it.type) }
        val shownTabs = buildList {
            if (catalog.any { it.type in ScriptActionCatalog.NEW }) add(PickerTab.NEW)
            add(PickerTab.POPULAR)
            if (brand != null && catalog.any { it.type in OemActions.TYPES }) add(PickerTab.PHONE)
        }
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
                    selected = selectedCategory == null && tab == null,
                    onClick = { tab = null; onSelectCategory(null) },
                )
            }
            items(shownTabs.size) { i ->
                val shown = shownTabs[i]
                PickerTabChip(shown, brand.orEmpty(), selected = tab == shown) {
                    tab = if (tab == shown) null else shown
                    onSelectCategory(null)
                }
            }
            items(ActionCategory.entries.size) { i ->
                val category = ActionCategory.entries[i]
                CategoryIconChip(
                    icon = categoryIcon(category),
                    label = stringResource(category.titleRes),
                    selected = selectedCategory == category,
                    onClick = { tab = null; onSelectCategory(if (selectedCategory == category) null else category) },
                    category = category,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val searching = query.isNotBlank()
        val offered = if (searching) ScriptActionCatalog.search(query, context.resources).filter { it in catalog } else catalog
        val filtered = when (tab) {
            PickerTab.NEW -> offered.filter { it.type in ScriptActionCatalog.NEW }
            PickerTab.POPULAR -> ScriptActionCatalog.POPULAR.mapNotNull { type -> offered.find { it.type == type } }
            PickerTab.PHONE -> offered.filter { it.type in OemActions.TYPES }
            null -> selectedCategory?.let { cat -> offered.filter { it.category == cat } } ?: offered
        }
        // "Teach a setting" and the switches taught on this phone live under Device.
        val deviceShown = tab == null && (selectedCategory == null || selectedCategory == ActionCategory.DEVICE)
        val needle = query.trim()
        val showTeach = deviceShown && (!searching || stringResource(R.string.teach_setting_title).contains(needle, ignoreCase = true))
        val learned by LearnedSettings.flow(context).collectAsState()
        val learnedShown = if (deviceShown) learned.filter { !searching || it.name.contains(needle, ignoreCase = true) } else emptyList()
        var editingLearned by remember { mutableStateOf<LearnedSwitch?>(null) }
        editingLearned?.let { LearnedSwitchDialog(it) { editingLearned = null } }
        // A tab is one flat list (Popular keeps its own order); categories group under headers.
        val grouped: List<Pair<ActionCategory?, List<ActionDescriptor>>> = when {
            tab != null -> if (filtered.isEmpty()) emptyList() else listOf(null to filtered)
            else -> ActionCategory.entries.mapNotNull { category ->
                val entries = filtered.filter { it.category == category }
                if (entries.isEmpty()) null else category to entries
            }
        }
        // In the wide panel the rows go two to a line, so a name never has to wrap.
        val columns = if (isWideLayout()) 2 else 1
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
            if (grouped.isEmpty() && !showTeach && learnedShown.isEmpty()) {
                item {
                    Text(
                        if (searching) stringResource(R.string.action_picker_no_match, query) else stringResource(R.string.action_picker_empty_category),
                        Modifier.padding(16.dp),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            // "Teach a setting" and the switches taught on this phone always come first.
            if (showTeach) {
                item(key = "teach_setting") {
                    TeachRow(advancedModeWarningRes(ScriptActionCatalog.LEARNED_SETTING, advancedModeActive)) { startTeaching(context) }
                }
            }
            if (learnedShown.isNotEmpty()) {
                item(key = "header_learned") {
                    Text(
                        stringResource(R.string.teach_learned_header),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = SectionLabel,
                        color = colors.onSurfaceVariant,
                    )
                }
                val learnedRows = learnedShown.flatMap { switch -> LEARNED_STATES.map { (state, label) -> Triple(switch, state, label) } }
                    .chunked(columns)
                items(learnedRows.size, key = { "learned_${learnedRows[it][0].first.id}_${learnedRows[it][0].second}" }) { i ->
                    PickerRow(learnedRows[i]) { (switch, state, label) ->
                        LearnedRow(
                            title = "${switch.name} · ${stringResource(label)}",
                            onPick = {
                                onPickLearned(
                                    mapOf(
                                        LearnedSetting.PARAM_NAME to switch.name,
                                        LearnedSetting.PARAM_WRITES to switch.writes,
                                        LearnedSetting.PARAM_STATE to state,
                                    ),
                                )
                            },
                            onLongPress = { editingLearned = switch },
                        )
                    }
                }
            }
            grouped.forEach { (category, entries) ->
                if (category != null) {
                    item(key = "header_${category.name}") {
                        Text(
                            stringResource(category.titleRes),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = SectionLabel,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                val rows = entries.chunked(columns)
                items(rows.size, key = { "action_${rows[it][0].type}" }) { i ->
                    PickerRow(rows[i]) { ActionRow(it, onPickAction, advancedModeWarningRes(it.type, advancedModeActive)) }
                }
            }
        }
    }
}

/** One picker line: a row, or two side by side in the wide panel. */
@Composable
internal fun <T> PickerRow(items: List<T>, item: @Composable (T) -> Unit) {
    if (items.size == 1 && !isWideLayout()) item(items[0]) else TwoColumns(items, rowGap = 0.dp, item = item)
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
            Modifier
                .size(46.dp)
                .clip(InnerShape)
                .background(fill)
                .then(if (selected) Modifier else Modifier.border(1.dp, glyph.copy(alpha = .45f), InnerShape)),
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

/** The picker tiles that are not categories: what's new, hand-picked favourites, and this phone's own. */
enum class PickerTab { NEW, POPULAR, PHONE }

/** A [PickerTab] tile; [brand] names the phone tile ("OPPO"). Tinted like the categories beside it. */
@Composable
internal fun PickerTabChip(tab: PickerTab, brand: String, selected: Boolean, onClick: () -> Unit) {
    when (tab) {
        PickerTab.NEW -> CategoryIconChip(Icons.Outlined.AutoAwesome, stringResource(R.string.picker_tab_new), selected, onClick)
        PickerTab.POPULAR ->
            CategoryIconChip(Icons.Outlined.LocalFireDepartment, stringResource(R.string.picker_tab_popular), selected, onClick, ActionCategory.DISPLAY)
        PickerTab.PHONE -> CategoryIconChip(Icons.Outlined.PhoneAndroid, brand, selected, onClick, ActionCategory.APPS)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(descriptor.titleRes),
                    Modifier.weight(1f, fill = false),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (descriptor.type in ScriptActionCatalog.NEW) NewBadge()
                if (descriptor.type in OemActions.EXPERIMENTAL) ExperimentalBadge(Modifier.padding(start = 6.dp))
            }
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

private val LEARNED_STATES = listOf(
    "toggle" to R.string.choice_state_toggle,
    "on" to R.string.choice_state_on,
    "off" to R.string.choice_state_off,
)

/** "Teach a setting": opens Settings with the teaching circle on top. */
@Composable
private fun TeachRow(@StringRes warningRes: Int?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(onClickLabel = stringResource(R.string.teach_setting_title), onClick = onClick)
            .testTag("action_row_teach_setting")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(actionIcon(ScriptActionCatalog.LEARNED_SETTING, ActionCategory.DEVICE), category = ActionCategory.DEVICE, size = 32.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.teach_setting_title), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                if (ScriptActionCatalog.LEARNED_SETTING in ScriptActionCatalog.NEW) NewBadge()
            }
            Text(
                stringResource(warningRes ?: R.string.teach_setting_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = if (warningRes != null) colors.error else colors.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.onSurfaceVariant)
    }
}

/** Blue "NEW" tag after a just-shipped action's name ([ScriptActionCatalog.NEW]). */
@Composable
private fun NewBadge() {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(R.string.badge_new),
        Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.primary)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = colors.onPrimary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/** One of a learned switch's three rows (Toggle, On, Off). Long-press renames or deletes it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LearnedRow(title: String, onPick: () -> Unit, onLongPress: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .combinedClickable(
                onClickLabel = stringResource(R.string.action_picker_add_label, title),
                onClick = onPick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(actionIcon(ScriptActionCatalog.LEARNED_SETTING, ActionCategory.DEVICE), category = ActionCategory.DEVICE, size = 32.dp)
        Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(colors.primary.copy(alpha = .14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, null, tint = colors.primary, modifier = Modifier.size(14.dp))
        }
    }
}

/** The small tag after an action's summary saying what "Undo when it ends" does with it. */
@Composable
private fun UndoBadge(@StringRes text: Int, on: Boolean) {
    val colors = MaterialTheme.colorScheme
    val tint = if (on) colors.onPrimaryContainer else colors.onSurfaceVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) colors.primaryContainer else colors.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) Icon(Icons.AutoMirrored.Outlined.Undo, null, Modifier.size(14.dp).padding(end = 2.dp), tint = tint)
        Text(stringResource(text), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

/** Under the Do chain of an automation: undo every action that can be, at once; each one's ⋮ changes just it. */
@Composable
private fun UndoWhenItEndsRow(undoing: Int, undoable: Int, onSetAll: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val on = undoing > 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .toggleable(value = on, role = Role.Switch, onValueChange = onSetAll)
            .testTag("undo_when_it_ends")
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = colors.tertiary)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.undo_when_it_ends), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(
                if (on && undoing < undoable) {
                    stringResource(R.string.undo_some, undoing, undoable)
                } else {
                    stringResource(R.string.undo_all_subtitle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

/** "All" / "Any" beside the If title: whether every condition must hold or just one. */
@Composable
private fun MatchModeToggle(any: Boolean, onSet: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).padding(2.dp).selectableGroup(),
    ) {
        listOf(false to R.string.constraint_match_all, true to R.string.constraint_match_any).forEach { (value, label) ->
            val selected = any == value
            val fill by animateColorAsState(if (selected) colors.surface else Color.Transparent, label = "matchModeFill")
            val ink by animateColorAsState(if (selected) colors.primary else colors.onSurfaceVariant, label = "matchModeInk")
            Text(
                stringResource(label),
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(fill)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSet(value) })
                    .testTag(if (value) "match_any" else "match_all")
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = ink,
            )
        }
    }
}
