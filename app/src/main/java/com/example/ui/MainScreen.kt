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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.data.ActionEditorForms
import com.example.data.ConstraintEditorForms
import com.example.data.EntitlementManager
import com.example.data.ScriptActionCatalog
import com.example.data.ScriptConstraintCatalog
import com.example.data.ShortcutShare
import com.example.data.WhatsNewDestination
import com.example.data.latestFailedScriptIds
import com.example.ui.components.AppBottomBar
import com.example.ui.components.AdvancedActivationBanner
import com.example.ui.components.AccessibilityDisclosureDialog
import com.example.ui.components.BottomBarClearance
import com.example.ui.components.NameKeyDialog
import com.example.ui.components.WhatsNewDialog
import com.example.ui.home.HomeScreen
import com.example.ui.scripts.ASSISTANT_SETTINGS_ACTION_TYPES
import com.example.ui.scripts.ActionConfigSheet
import com.example.ui.scripts.ConstraintConfigSheet
import com.example.ui.scripts.ScriptEditorScreen
import com.example.ui.scripts.ScriptSettingsSheet
import com.example.ui.scripts.ScriptsScreen
import com.example.ui.scripts.ShortcutReviewDialog
import com.example.ui.scripts.TriggerPickerSheet
import com.example.ui.scripts.shareShortcutText
import com.example.ui.settings.SettingsGear
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsSection
import com.example.ui.settings.SettingsScrim
import com.example.ui.settings.SheetTopGap
import com.example.ui.settings.SettingsSheet
import com.example.ui.settings.rememberSettingsMotion
import com.example.ui.theme.PageFloorStop
import com.example.ui.theme.ThemeTransitionMs
import com.example.ui.theme.backgroundGlowAlpha
import com.example.ui.theme.groundFor
import com.example.ui.theme.pageFloorFor
import com.example.ui.theme.resolveThemeMode
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
    val advancedAutostart by viewModel.advancedAutostart.collectAsState()
    val autostartPermissionHeld by viewModel.autostartPermissionHeld.collectAsState()
    val autostartChanging by viewModel.autostartChanging.collectAsState()
    val storedThemeMode by viewModel.themeMode.collectAsState()
    val themeFollowSystem by viewModel.themeFollowSystem.collectAsState()
    val oledMode by viewModel.oledMode.collectAsState()
    val themeMode = resolveThemeMode(storedThemeMode, themeFollowSystem, isSystemInDarkTheme())
    val hijackBannerStage by viewModel.hijackBannerStage.collectAsState()
    val showSetupWizard by viewModel.showSetupWizard.collectAsState()
    val whatsNew by viewModel.whatsNew.collectAsState()
    val savedKeys by viewModel.savedKeys.collectAsState()
    val showNameKeyDialogForCode by viewModel.showNameKeyDialogForCode.collectAsState()
    val editingScript by viewModel.editingScript.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    val proPrice by viewModel.proPrice.collectAsState()
    val scriptsMessage by viewModel.scriptsMessage.collectAsState()
    val nudgeDismissed by viewModel.nudgeDismissed.collectAsState()
    val runLog by viewModel.runLog.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()
    val activeScriptLimit = EntitlementManager.FREE_SCRIPT_LIMIT
    val activeScriptCount = scripts.count { it.enabled }
    var isEventLogExpanded by remember { mutableStateOf(false) }
    var showTriggerPicker by remember { mutableStateOf(false) }
    var showScriptSettings by remember { mutableStateOf(false) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val pagerScope = rememberCoroutineScope()
    val homeScrollState = rememberScrollState()
    var scrollHomeToProCard by remember { mutableStateOf(false) }
    // The snackbar lives in the shell, above the bottom bar, so a message raised anywhere shows on
    // whatever page the user is on. The "Home" action is only worth offering elsewhere.
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
            pagerState.animateScrollToPage(0, animationSpec = PageSlide)
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
        val (targetNearGlow, targetFarGlow) = backgroundGlowAlpha(themeMode, oledMode)
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
            var settingsOpen by rememberSaveable { mutableStateOf(false) }
            var settingsSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
            val settingsMotion = rememberSettingsMotion(settingsOpen)
            val goToPage: (Int) -> Unit = { page -> pagerScope.launch { pagerState.animateScrollToPage(page, animationSpec = PageSlide) } }
            // Back from Shortcuts goes Home; on Home it falls through and exits.
            BackHandler(enabled = pagerState.currentPage != 0) { goToPage(0) }
            // Registered after the pager's, so Back shuts the panel before it does anything else.
            BackHandler(enabled = settingsOpen) { settingsOpen = false }
            // Pages run edge to edge: each draws its ground behind the system bars and pads its own content.
            // They are recorded as they draw, so the bottom bar can show them frosted through its glass.
            val pagesLayer = rememberGraphicsLayer()
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        pagesLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(pagesLayer)
                    }
                    .then(settingsMotion.backdropScale())
                    // Behind the open panel the page is scenery, not something TalkBack should walk.
                    .then(if (settingsOpen) Modifier.clearAndSetSemantics {} else Modifier),
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
                        activeScriptCount = activeScriptCount,
                        activeScriptLimit = activeScriptLimit,
                        themeMode = themeMode,
                        scripts = scripts,
                        onOpenScript = viewModel::editScript,
                        onToggleMapping = { viewModel.toggleEnabled(!isMappingEnabled) },
                        onOpenWizard = { viewModel.openSetupWizard(0) },
                        onOpenAccessibility = { showAccessibilityDisclosure = true },
                        onPickSuggestion = viewModel::createSuggestedScript,
                        onOpenProScreen = { viewModel.purchasePro(context as Activity) },
                        onCycleTheme = { viewModel.setThemeMode(themeMode.next()) },
                        nudgeDismissed = nudgeDismissed,
                        onDismissNudge = viewModel::dismissFirstShortcutNudge,
                        scrollState = homeScrollState,
                        scrollToProCard = scrollHomeToProCard,
                        onScrolledToProCard = { scrollHomeToProCard = false },
                    )
                    else -> ScriptsScreen(
                        scripts = scripts,
                        globallyEnabled = isMappingEnabled,
                        themeMode = themeMode,
                        onToggleScriptEnabled = viewModel::toggleScriptEnabled,
                        onOpenScript = viewModel::editScript,
                        onDeleteScript = viewModel::deleteScript,
                        onGoHome = { goToPage(0) },
                        onImportText = viewModel::importShortcutText,
                        onImportUri = viewModel::importShortcutUri,
                        failedScriptIds = remember(runLog) { latestFailedScriptIds(runLog) },
                    )
                }
            }
            AppBottomBar(
                // The target, not the current page, so the lens sets off on the tap rather than halfway through the slide.
                selectedPage = pagerState.targetPage,
                onPageSelected = goToPage,
                onNewShortcut = viewModel::createScript,
                pages = pagesLayer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (settingsOpen) Modifier.clearAndSetSemantics {} else Modifier),
            )
            SettingsScrim(
                settingsMotion,
                settingsOpen,
                onClose = { settingsOpen = false },
                ground = groundFor(themeMode, oledMode).stops.first().color,
            )
            SettingsSheet(
                settingsMotion,
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = SheetTopGap, bottom = 144.dp),
            ) {
                SettingsScreen(
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
                    themeFollowSystem = themeFollowSystem,
                    onSetThemeSystem = viewModel::setThemeSystem,
                    oledMode = oledMode,
                    onSetOledMode = viewModel::setOledMode,
                    onEraseAllData = viewModel::eraseAllData,
                    isAdvancedModeRunning = isAdvancedModeRunning,
                    advancedAutostart = advancedAutostart,
                    autostartPermissionHeld = autostartPermissionHeld,
                    autostartChanging = autostartChanging,
                    onToggleAutostart = viewModel::setAdvancedAutostart,
                    onToggleAdvancedMode = viewModel::toggleAdvancedMode,
                    onRunSetupAgain = { viewModel.openSetupWizard(0) },
                    reveal = settingsMotion::reveal,
                    targetSection = settingsSection,
                    onSectionShown = { settingsSection = null },
                )
            }
            if (!settingsOpen && pagerState.currentPage == 0 && suggestionDraft == null && !showSetupWizard &&
                pendingImport == null && showNameKeyDialogForCode == null && !showAccessibilityDisclosure
            ) {
                whatsNew?.let { release ->
                    WhatsNewDialog(
                        release = release,
                        themeMode = themeMode,
                        onDismiss = viewModel::dismissWhatsNew,
                        onOpenSettings = { destination ->
                            viewModel.dismissWhatsNew()
                            settingsSection = when (destination) {
                                WhatsNewDestination.ADVANCED_MODE -> SettingsSection.ADVANCED_MODE
                                WhatsNewDestination.APPEARANCE -> SettingsSection.APPEARANCE
                                WhatsNewDestination.ABOUT -> SettingsSection.ABOUT
                            }
                            settingsOpen = true
                        },
                    )
                }
            }
            SettingsGear(
                settingsMotion,
                settingsOpen,
                onClick = { settingsOpen = !settingsOpen },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 16.dp, end = 14.dp),
            )
            // Over the gear: the banner's OK lands where the gear is, and the banner only stays a moment.
            AnimatedVisibility(
                visible = hijackBannerStage == HijackBannerStage.BOTTOM_EXPANDED,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AdvancedActivationBanner(viewModel::dismissHijackBanner)
            }
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
                    // A phone with no spare key can still use a recipe, from a widget or tile.
                    onSwitchToSurface = { viewModel.draftSetSurfaceOnly(true) },
                )
            }
        } else {
            // Non-null while the sheet is open: the action type, and the chain index when editing.
            var configuringType by remember { mutableStateOf<String?>(null) }
            var configuringIndex by remember { mutableStateOf<Int?>(null) }
            // Same shape for constraints: the type while the sheet is open, the index when editing.
            var pendingConstraintType by remember { mutableStateOf<String?>(null) }
            var pendingConstraintIndex by remember { mutableStateOf<Int?>(null) }
            var confirmShare by remember { mutableStateOf(false) }
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
                runs = runLog.filter { it.scriptId == draft.id },
                onOpenTriggerPicker = { showTriggerPicker = true },
                onToggleAutomation = viewModel::draftToggleAutomation,
                onClearSurfaceOnly = { viewModel.draftSetSurfaceOnly(false) },
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
                // Anything privileged, or typed text, may carry a token or password; say so before it leaves.
                onShare = {
                    if (draft.actions.any { it.type in ScriptActionCatalog.REVIEWED_LITERALLY }) {
                        confirmShare = true
                    } else {
                        viewModel.draftShareText()?.let { shareShortcutText(context, it) }
                    }
                },
            )
            configuringType?.let { type ->
                val index = configuringIndex
                ActionConfigSheet(
                    actionType = type,
                    initialParams = index?.let { draft.actions.getOrNull(it)?.params }.orEmpty(),
                    isEdit = index != null,
                    startsFromNotification = ScriptConstraintCatalog.startsFromNotification(draft.automation, draft.constraints),
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
                    onSwitchToSurface = {
                        showTriggerPicker = false
                        viewModel.draftSetSurfaceOnly(true)
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
                    onSetExactTiming = viewModel::draftSetExactTiming,
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
            if (confirmShare) {
                ShortcutReviewDialog(
                    title = stringResource(R.string.share_review_title),
                    name = draft.name,
                    automation = draft.automation,
                    actions = draft.actions.map(ShortcutShare::portable),
                    note = stringResource(R.string.share_review_note),
                    confirmLabel = stringResource(R.string.share_action),
                    onConfirm = {
                        confirmShare = false
                        viewModel.draftShareText()?.let { shareShortcutText(context, it) }
                    },
                    onDismiss = { confirmShare = false },
                    repliesToEveryChat = ActionEditorForms.repliesToEveryChat(draft.automation, draft.constraints, draft.actions),
                )
            }
        }
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = BottomBarClearance + 12.dp),
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
    pendingImport?.let { imported ->
        ShortcutReviewDialog(
            title = stringResource(R.string.import_review_title),
            name = imported.name,
            automation = imported.automation,
            actions = imported.actions,
            note = stringResource(
                if (imported.startsEnabled) R.string.import_review_note else R.string.import_review_note_privileged,
            ),
            confirmLabel = stringResource(R.string.import_confirm),
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismissImport,
            repliesToEveryChat = ActionEditorForms.repliesToEveryChat(imported.automation, imported.constraints, imported.actions),
        )
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

/** The page slide, at the redesign's 1.25x pace. */
private val PageSlide = tween<Float>(300, easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f))
