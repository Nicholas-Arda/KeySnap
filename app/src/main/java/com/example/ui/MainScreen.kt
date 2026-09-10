package com.example.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.data.ConstraintEditorForms
import com.example.data.EntitlementManager
import com.example.ui.components.AppBottomBar
import com.example.ui.components.AdvancedActivationBanner
import com.example.ui.components.AccessibilityDisclosureDialog
import com.example.ui.components.NameKeyDialog
import com.example.ui.home.HomeScreen
import com.example.ui.scripts.ASSISTANT_SETTINGS_ACTION_TYPES
import com.example.ui.scripts.ActionConfigSheet
import com.example.ui.scripts.ConstraintConfigSheet
import com.example.ui.scripts.ScriptEditorScreen
import com.example.ui.scripts.ScriptSettingsSheet
import com.example.ui.scripts.ScriptsScreen
import com.example.ui.scripts.TriggerPickerSheet
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.PageFloorStop
import com.example.ui.theme.ThemeTransitionMs
import com.example.ui.theme.backgroundGlowAlpha
import com.example.ui.theme.pageFloorFor
import com.example.ui.wizard.SetupWizardModal
import com.example.viewmodel.HijackBannerStage
import com.example.viewmodel.MainViewModel
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val config by viewModel.config.collectAsState()
    val scripts by viewModel.scripts.collectAsState()
    val isMappingEnabled by viewModel.isMappingEnabled.collectAsState()
    val keyHistory by viewModel.keyHistory.collectAsState()
    val isRecordingKey by viewModel.isRecordingKey.collectAsState()
    val triggerCount by viewModel.triggerCount.collectAsState()
    val lastSource by viewModel.lastCapturedSource.collectAsState()
    val editorMessage by viewModel.editorMessage.collectAsState()
    val isAdvancedModeRunning by viewModel.isAdvancedModeRunning.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val hijackBannerStage by viewModel.hijackBannerStage.collectAsState()
    val showSetupWizard by viewModel.showSetupWizard.collectAsState()
    val savedKeys by viewModel.savedKeys.collectAsState()
    val showNameKeyDialogForCode by viewModel.showNameKeyDialogForCode.collectAsState()
    val editingScript by viewModel.editingScript.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    val proPrice by viewModel.proPrice.collectAsState()
    val rewardExpiryAt by viewModel.rewardExpiryAt.collectAsState()
    val scriptsMessage by viewModel.scriptsMessage.collectAsState()
    val nudgeDismissed by viewModel.nudgeDismissed.collectAsState()
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            nowTick = System.currentTimeMillis()
            viewModel.onClockTick()
        }
    }
    val rewardActive = rewardExpiryAt > nowTick
    val rewardRemainingLabel = if (rewardActive) formatRemaining(rewardExpiryAt - nowTick) else ""
    val activeScriptLimit = EntitlementManager.FREE_SCRIPT_LIMIT + if (rewardActive) EntitlementManager.REWARD_BONUS_SCRIPTS else 0
    val activeScriptCount = scripts.count { it.enabled }
    var isEventLogExpanded by remember { mutableStateOf(false) }
    var showTriggerPicker by remember { mutableStateOf(false) }
    var showScriptSettings by remember { mutableStateOf(false) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val pagerScope = rememberCoroutineScope()
    val homeListState = rememberLazyListState()
    var scrollHomeToProCard by remember { mutableStateOf(false) }
    // The snackbar lives in the shell, above the bottom bar's fade, so a message raised anywhere
    // shows on whatever page the user is on. The "Home" action is only worth offering elsewhere.
    val snackbarHostState = remember { SnackbarHostState() }
    val homeAction = stringResource(R.string.scripts_snackbar_home)
    LaunchedEffect(scriptsMessage) {
        val message = scriptsMessage ?: return@LaunchedEffect
        val autoDismiss = launch {
            delay(4000)
            snackbarHostState.currentSnackbarData?.dismiss()
        }
        val result = snackbarHostState.showSnackbar(
            message.text,
            // Only the free-limit messages have somewhere to send the user; a confirmation does not.
            actionLabel = homeAction.takeIf { message.showProAction && pagerState.currentPage != 0 },
        )
        autoDismiss.cancel()
        if (result == SnackbarResult.ActionPerformed) {
            pagerState.animateScrollToPage(0)
            scrollHomeToProCard = true
        }
        viewModel.consumeScriptsMessage()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val accent = MaterialTheme.colorScheme.primary
        // The colour scheme crossfades over ThemeTransitionMs; the ground has to move on the same
        // clock or it snaps to the new theme while the cards are still fading.
        val (targetNearGlow, targetFarGlow) = backgroundGlowAlpha(themeMode)
        val nearGlowAlpha by animateFloatAsState(targetNearGlow, tween(ThemeTransitionMs), label = "nearGlow")
        val farGlowAlpha by animateFloatAsState(targetFarGlow, tween(ThemeTransitionMs), label = "farGlow")
        val pageFloor by animateColorAsState(pageFloorFor(themeMode), tween(ThemeTransitionMs), label = "pageFloor")
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = nearGlowAlpha), Color.Transparent),
                        center = Offset(widthPx * 0.12f, 0f),
                        radius = widthPx * 1.1f,
                    ),
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = farGlowAlpha), Color.Transparent),
                        center = Offset(widthPx * 0.98f, widthPx * 0.15f),
                        radius = widthPx * 0.9f,
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, pageFloor),
                        startY = 0f,
                        endY = heightPx * PageFloorStop,
                    ),
                ),
        ) {
        val draft = editingScript
        val suggestionDraft = draft?.takeIf { it.fromSuggestion }
        if (draft == null || suggestionDraft != null) {
            // Back from Scripts or Settings goes Home; on Home it falls through and exits.
            BackHandler(enabled = pagerState.currentPage != 0) { pagerScope.launch { pagerState.animateScrollToPage(0) } }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            ) { page ->
                when (page) {
                    0 -> HomeScreen(
                        isMappingEnabled = isMappingEnabled,
                        isServiceActive = isServiceActive,
                        isAdvancedModeRunning = isAdvancedModeRunning,
                        triggerCount = triggerCount,
                        lastSource = lastSource,
                        isPro = isPro,
                        proPrice = proPrice,
                        rewardActive = rewardActive,
                        rewardRemainingLabel = rewardRemainingLabel,
                        activeScriptCount = activeScriptCount,
                        activeScriptLimit = activeScriptLimit,
                        themeMode = themeMode,
                        scripts = scripts,
                        onOpenScript = viewModel::editScript,
                        onToggleMapping = { viewModel.toggleEnabled(!isMappingEnabled) },
                        onOpenWizard = { viewModel.openSetupWizard(0) },
                        onOpenAccessibility = { showAccessibilityDisclosure = true },
                        onPickSuggestion = viewModel::createSuggestedScript,
                        onRewardButtonClick = { viewModel.onRewardButtonClick(context as Activity) },
                        onOpenProScreen = { viewModel.purchasePro(context as Activity) },
                        nudgeDismissed = nudgeDismissed,
                        onDismissNudge = viewModel::dismissFirstShortcutNudge,
                        listState = homeListState,
                        scrollToProCard = scrollHomeToProCard,
                        onScrolledToProCard = { scrollHomeToProCard = false },
                    )
                    1 -> ScriptsScreen(
                        scripts = scripts,
                        globallyEnabled = isMappingEnabled,
                        onToggleScriptEnabled = viewModel::toggleScriptEnabled,
                        onOpenScript = viewModel::editScript,
                        onCreateScript = viewModel::createScript,
                        onDeleteScript = viewModel::deleteScript,
                        onGoHome = { pagerScope.launch { pagerState.animateScrollToPage(0) } },
                    )
                    else -> SettingsScreen(
                        config = config,
                        isVibratorAvailable = viewModel.isVibratorAvailable,
                        onToggleConsume = viewModel::toggleConsumeOriginal,
                        onToggleVibrate = viewModel::toggleVibrate,
                        onDoublePressWindowChange = viewModel::setDoublePressWindowMs,
                        onLongPressThresholdChange = viewModel::setLongPressThresholdMs,
                        onHapticDurationChange = viewModel::setHapticDurationMs,
                        keyHistory = keyHistory,
                        isEventLogExpanded = isEventLogExpanded,
                        onToggleLog = { isEventLogExpanded = !isEventLogExpanded },
                        onClearHistory = viewModel::clearHistory,
                        themeMode = themeMode,
                        onSetThemeMode = viewModel::setThemeMode,
                        onEraseAllData = viewModel::eraseAllData,
                        isAdvancedModeRunning = isAdvancedModeRunning,
                        onToggleAdvancedMode = viewModel::toggleAdvancedMode,
                        onRunSetupAgain = { viewModel.openSetupWizard(0) },
                    )
                }
            }
            BottomFadeWall(pageFloor, Modifier.align(Alignment.BottomCenter))
            AnimatedVisibility(
                visible = hijackBannerStage == HijackBannerStage.BOTTOM_EXPANDED,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AdvancedActivationBanner(viewModel::dismissHijackBanner)
            }
            AppBottomBar(
                selectedPage = pagerState.currentPage,
                onPageSelected = { page -> pagerScope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding(),
            )
            if (suggestionDraft != null) {
                // Assigning the key saves the shortcut outright, which clears the draft and the sheet.
                TriggerPickerSheet(
                    pressType = suggestionDraft.trigger?.pressType ?: suggestionDraft.pressType,
                    keyCodes = suggestionDraft.trigger?.keyCodes.orEmpty(),
                    savedKeys = savedKeys,
                    isRecordingKey = isRecordingKey,
                    onDismiss = viewModel::discardDraft,
                    onSelectKey = viewModel::draftSetTriggerKey,
                    onStartRecording = viewModel::startRecordingKey,
                    onCancelRecording = viewModel::cancelRecordingKey,
                    onSetPressType = viewModel::draftSetPressType,
                )
            }
        } else {
            // Non-null while the sheet is open: the action type, and the chain index when editing.
            var configuringType by remember { mutableStateOf<String?>(null) }
            var configuringIndex by remember { mutableStateOf<Int?>(null) }
            // Same shape for constraints: the type while the sheet is open, the index when editing.
            var pendingConstraintType by remember { mutableStateOf<String?>(null) }
            var pendingConstraintIndex by remember { mutableStateOf<Int?>(null) }
            val installedApps by viewModel.installedApps.collectAsState()
            val pickedPoints by viewModel.pickedPoints.collectAsState()
            var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

            val overlayPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { canDrawOverlays = Settings.canDrawOverlays(context) }

            fun closeEditorSheets() {
                showTriggerPicker = false
                showScriptSettings = false
                configuringType = null
                configuringIndex = null
                pendingConstraintType = null
                pendingConstraintIndex = null
            }
            // An app constraint's row shows the app label only once the list is loaded.
            LaunchedEffect(draft.constraints) {
                if (draft.constraints.any { ConstraintEditorForms.needsInstalledApps(it.type) }) viewModel.loadInstalledApps()
            }
            ScriptEditorScreen(
                draft = draft,
                message = editorMessage,
                onConsumeMessage = viewModel::consumeEditorMessage,
                onSetName = viewModel::draftSetName,
                onAddAction = viewModel::draftAddAction,
                onConfigureNewAction = { type ->
                    viewModel.loadInstalledApps()
                    configuringIndex = null
                    configuringType = type
                },
                onConfigureAction = { index ->
                    val type = draft.actions.getOrNull(index)?.type
                    if (type in ASSISTANT_SETTINGS_ACTION_TYPES) {
                        viewModel.openAssistantSettings(context)
                    } else {
                        viewModel.loadInstalledApps()
                        draft.actions.getOrNull(index)?.let {
                            configuringIndex = index
                            configuringType = it.type
                        }
                    }
                },
                onRemoveAction = viewModel::draftRemoveAction,
                onMoveAction = viewModel::draftMoveAction,
                onRemoveConstraint = viewModel::draftRemoveConstraint,
                onConfigureConstraint = { index ->
                    draft.constraints.getOrNull(index)?.let {
                        viewModel.loadInstalledApps()
                        pendingConstraintIndex = index
                        pendingConstraintType = it.type
                    }
                },
                installedApps = installedApps,
                advancedModeActive = isAdvancedModeRunning,
                onOpenTriggerPicker = { showTriggerPicker = true },
                onToggleAutomation = viewModel::draftToggleAutomation,
                onPickConstraint = { type ->
                    if (ConstraintEditorForms.form(type) == null) {
                        viewModel.draftAddConstraint(type)
                    } else {
                        viewModel.loadInstalledApps()
                        pendingConstraintIndex = null
                        pendingConstraintType = type
                    }
                },
                onOpenScriptSettings = { showScriptSettings = true },
                onRunOnce = viewModel::draftRunOnce,
                onDeleteScript = {
                    closeEditorSheets()
                    if (draft.isNew) viewModel.discardDraft() else viewModel.deleteScript(draft.id)
                },
                onDone = {
                    closeEditorSheets()
                    viewModel.commitDraft()
                },
                onClose = {
                    closeEditorSheets()
                    viewModel.discardDraft()
                },
            )
            configuringType?.let { type ->
                val index = configuringIndex
                ActionConfigSheet(
                    actionType = type,
                    initialParams = index?.let { draft.actions.getOrNull(it)?.params }.orEmpty(),
                    isEdit = index != null,
                    isRecordingKey = isRecordingKey,
                    installedApps = installedApps,
                    canDrawOverlays = canDrawOverlays,
                    pendingPoints = pickedPoints,
                    onConsumePoints = viewModel::consumePickedPoints,
                    onPickPoints = { mode, gesture -> viewModel.startPointPick(context, mode, gesture) },
                    onRequestOverlayPermission = {
                        overlayPermissionLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    onStartRecording = viewModel::startRecordingKey,
                    onCancelRecording = viewModel::cancelRecordingKey,
                    onRequestNotificationPolicyAccess = { viewModel.openNotificationPolicySettings(context) },
                    onRequestWriteSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
                        )
                    },
                    onConfirm = { params ->
                        if (index == null) viewModel.draftAddAction(type, params) else viewModel.draftSetActionParams(index, params)
                        configuringType = null
                        configuringIndex = null
                    },
                    onDismiss = {
                        configuringType = null
                        configuringIndex = null
                    },
                )
            }
            if (showTriggerPicker) {
                TriggerPickerSheet(
                    pressType = draft.trigger?.pressType ?: draft.pressType,
                    keyCodes = draft.trigger?.keyCodes.orEmpty(),
                    savedKeys = savedKeys,
                    isRecordingKey = isRecordingKey,
                    onDismiss = { showTriggerPicker = false },
                    onSelectKey = viewModel::draftSetTriggerKey,
                    onStartRecording = viewModel::startRecordingKey,
                    onCancelRecording = viewModel::cancelRecordingKey,
                    onSetPressType = viewModel::draftSetPressType,
                    onSwitchToAutomation = {
                        showTriggerPicker = false
                        viewModel.draftSetAutomation()
                    },
                )
            }
            if (showScriptSettings) {
                ScriptSettingsSheet(
                    draft = draft,
                    defaults = config,
                    onDismiss = { showScriptSettings = false },
                    onSetName = viewModel::draftSetName,
                    onSetEnabled = viewModel::draftSetEnabled,
                    onSetPressType = viewModel::draftSetPressType,
                    onSetDoublePressWindow = viewModel::draftSetDoublePressWindow,
                    onSetLongPressThreshold = viewModel::draftSetLongPressThreshold,
                    onSetConsumeOriginalEvent = viewModel::draftSetConsumeOriginalEvent,
                    onSetVibrateOnTrigger = viewModel::draftSetVibrateOnTrigger,
                    onSetHapticDurationMs = viewModel::draftSetHapticDurationMs,
                )
            }
            pendingConstraintType?.let { type ->
                val index = pendingConstraintIndex
                ConstraintConfigSheet(
                    constraintType = type,
                    initialParams = index?.let { draft.constraints.getOrNull(it)?.params }.orEmpty(),
                    isEdit = index != null,
                    installedApps = installedApps,
                    onConfirm = { params ->
                        if (index == null) viewModel.draftAddConstraint(type, params) else viewModel.draftSetConstraintParams(index, params)
                        pendingConstraintType = null
                        pendingConstraintIndex = null
                    },
                    onDismiss = {
                        pendingConstraintType = null
                        pendingConstraintIndex = null
                    },
                )
            }
        }
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = BottomBarHeight + BottomBarVerticalPadding)
                // The Shortcuts page floats its "New shortcut" FAB above the bottom bar; without
                // this the snackbar lands right on top of it.
                .padding(bottom = if (pagerState.currentPage == 1) ScriptsFabClearance else 0.dp),
        ) { data ->
            Snackbar(
                action = data.visuals.actionLabel?.let { label ->
                    { TextButton(onClick = { data.performAction() }) { Text(label) } }
                },
            ) {
                Text(data.visuals.message, modifier = Modifier.clickable { data.dismiss() })
            }
        }
        }
    }

    if (showSetupWizard) SetupWizardModal(viewModel, viewModel::closeSetupWizard)
    showNameKeyDialogForCode?.let { code ->
        NameKeyDialog(code, viewModel::dismissNameKeyDialog) { viewModel.saveUnknownKey(code, it) }
    }
    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAgree = {
                showAccessibilityDisclosure = false
                viewModel.openAccessibilitySettings(context)
            },
            onDismiss = { showAccessibilityDisclosure = false },
        )
    }
}

private fun formatRemaining(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private val BottomFadeHeight = 28.dp
private val BottomBarHeight = 68.dp
private val BottomBarVerticalPadding = 24.dp

/** ScriptsScreen's FAB sits 112dp up and is 56dp tall; clear its top edge (168dp) plus a gap. */
private val ScriptsFabClearance = 168.dp + 16.dp - (BottomBarHeight + BottomBarVerticalPadding)

@Composable
private fun BottomFadeWall(background: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val navigationBarInset = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val wallHeight = BottomFadeHeight + BottomBarHeight + BottomBarVerticalPadding + navigationBarInset

    Box(
        modifier
            .fillMaxWidth()
            .height(wallHeight)
            .drawWithCache {
                val fadeEnd = BottomFadeHeight.toPx()
                val brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, background),
                    startY = 0f,
                    endY = fadeEnd,
                )
                onDrawBehind { drawRect(brush) }
            },
    )
}
