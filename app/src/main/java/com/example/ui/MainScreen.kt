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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
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
import androidx.compose.ui.geometry.Rect
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
import com.example.data.RatingPromptChoice
import com.example.ui.home.openKeySnapPlayListing
import com.example.data.AccessibilityStatus
import com.example.data.latestFailedScriptIds
import com.example.ui.components.AppBottomBar
import com.example.ui.components.AdvancedActivationBanner
import com.example.ui.components.AccessibilityDisclosureSheet
import com.example.ui.components.BottomBarClearance
import com.example.ui.components.NameKeyDialog
import com.example.ui.components.WhatsNewDialog
import com.example.ui.home.HomeScreen
import com.example.ui.scripts.ASSISTANT_SETTINGS_ACTION_TYPES
import com.example.ui.scripts.ActionConfigSheet
import com.example.ui.scripts.ConstraintConfigSheet
import com.example.ui.scripts.ScriptEditorScreen
import com.example.ui.scripts.ShortcutEditorTransition
import com.example.ui.scripts.ScriptSettingsSheet
import com.example.ui.scripts.ScriptsScreen
import com.example.ui.scripts.ShortcutReviewDialog
import com.example.ui.scripts.InlineKeyPicker
import com.example.ui.scripts.shareShortcutText
import com.example.ui.settings.SettingsGear
import com.example.capsule.CapsuleHooks
import com.example.data.keyDisplayName
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsSection
import com.example.ui.settings.SettingsScrim
import com.example.ui.settings.SheetTopGap
import com.example.ui.settings.SettingsSheet
import com.example.ui.settings.rememberSettingsMotion
import com.example.ui.settings.settingsBottomClearance
import com.example.ui.theme.PageFloorStop
import com.example.ui.theme.ThemeTransitionMs
import com.example.ui.theme.backgroundGlowAlpha
import com.example.ui.theme.groundFor
import com.example.ui.theme.pageFloorFor
import com.example.ui.theme.resolveThemeMode
import com.example.ui.wizard.SetupWizardModal
import com.example.mode.ModeHooks
import com.example.service.NotificationEventListener
import com.example.viewmodel.HijackBannerStage
import com.example.viewmodel.MainViewModel
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val accessibilityStatus by viewModel.accessibilityStatus.collectAsState()
    val accessibilityReconnecting by viewModel.accessibilityReconnecting.collectAsState()
    val config by viewModel.config.collectAsState()
    val scripts by viewModel.scripts.collectAsState()
    val isMappingEnabled by viewModel.isMappingEnabled.collectAsState()
    val keyHistory by viewModel.keyHistory.collectAsState()
    val isRecordingKey by viewModel.isRecordingKey.collectAsState()
    val recordingKeys by viewModel.recordingKeys.collectAsState()
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
    val browsedWhatsNew by viewModel.browsedWhatsNew.collectAsState()
    val whatsNewAnnouncement by viewModel.whatsNewAnnouncement.collectAsState()
    val whatsNewHint by viewModel.whatsNewHint.collectAsState()
    val showRatingPrompt by viewModel.showRatingPrompt.collectAsState()
    val lifetimeSuccessfulRuns by viewModel.lifetimeSuccessfulRuns.collectAsState()
    val savedKeys by viewModel.savedKeys.collectAsState()
    var capsuleEditorOpen by rememberSaveable { mutableStateOf(false) }
    var capsuleIntroOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // Where the header logo sat when it opened What's New; it flies from and back to there.
    var whatsNewLogoOrigin by remember { mutableStateOf<Rect?>(null) }
    var whatsNewLogoLifted by remember { mutableStateOf(false) }
    val showNameKeyDialogForCode by viewModel.showNameKeyDialogForCode.collectAsState()
    val editingScript by viewModel.editingScript.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    val showModesExplainer by viewModel.showModesExplainer.collectAsState()
    LaunchedEffect(isPro) { if (isPro) viewModel.dismissModesExplainer() }
    LaunchedEffect(isPro) { if (!isPro) capsuleEditorOpen = false else capsuleIntroOpen = false }
    LaunchedEffect(Unit) {
        viewModel.openProPurchase.collect {
            (context as? Activity)?.let(viewModel::purchasePro)
        }
    }
    val proPrice by viewModel.proPrice.collectAsState()
    val scriptsMessage by viewModel.scriptsMessage.collectAsState()
    val nudgeDismissed by viewModel.nudgeDismissed.collectAsState()
    val showColorOsRecentsTip by viewModel.showColorOsRecentsTip.collectAsState()
    val runLog by viewModel.runLog.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()
    val activeScriptLimit = EntitlementManager.FREE_SCRIPT_LIMIT
    val activeScriptCount = scripts.count { it.enabled }
    var isEventLogExpanded by remember { mutableStateOf(false) }
    var showTriggerPicker by remember { mutableStateOf(false) }
    var showScriptSettings by remember { mutableStateOf(false) }
    var shortcutFabCenter by remember { mutableStateOf<Offset?>(null) }
    var fabLaunchOrigin by remember { mutableStateOf<Offset?>(null) }
    var fabLaunchedScriptId by remember { mutableStateOf<String?>(null) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    // A live bridge can redo Android's off/on switch for KeySnap's own entry; without it, the user does.
    val canReconnect = accessibilityStatus == AccessibilityStatus.NEEDS_RECONNECT && isAdvancedModeRunning
    val accessibilityButton = when {
        accessibilityReconnecting -> R.string.accessibility_reconnecting
        canReconnect -> R.string.accessibility_reconnect_button
        else -> R.string.home_setup_open_accessibility
    }
    val onOpenAccessibility: () -> Unit = {
        when {
            // While our entry is briefly off mid-reconnect, a tap must not reopen the first-time disclosure.
            accessibilityReconnecting -> Unit
            else -> showAccessibilityDisclosure = true
        }
    }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val pagerScope = rememberCoroutineScope()
    val ratingHomeReady = pagerState.currentPage == 0 && !pagerState.isScrollInProgress && !settingsOpen &&
        editingScript == null && !showSetupWizard && whatsNew == null && browsedWhatsNew == null && pendingImport == null &&
        showNameKeyDialogForCode == null && !showAccessibilityDisclosure && !capsuleEditorOpen &&
        !capsuleIntroOpen && !showTriggerPicker && !showScriptSettings
    LaunchedEffect(ratingHomeReady, lifetimeSuccessfulRuns) {
        viewModel.updateRatingPromptVisibility(ratingHomeReady)
    }
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

    BoxWithConstraints(Modifier.fillMaxSize().then(if (capsuleEditorOpen || capsuleIntroOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val settingsClearance = settingsBottomClearance(maxHeight)
        val accent = MaterialTheme.colorScheme.primary
        // The colour scheme crossfades over ThemeTransitionMs; the ground has to move on the same
        // clock or it snaps to the new theme while the cards are still fading.
        val (targetNearGlow, targetFarGlow) = backgroundGlowAlpha(themeMode, oledMode)
        val nearGlowAlpha by animateFloatAsState(targetNearGlow, tween(ThemeTransitionMs), label = "nearGlow")
        val farGlowAlpha by animateFloatAsState(targetFarGlow, tween(ThemeTransitionMs), label = "farGlow")
        val pageFloor by animateColorAsState(pageFloorFor(themeMode), tween(ThemeTransitionMs), label = "pageFloor")
        val screenGround = Modifier.background(MaterialTheme.colorScheme.background)
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
                )
        Box(Modifier.fillMaxSize().then(screenGround)) {
        val suggestionDraft = editingScript?.takeIf { it.fromSuggestion }
        ShortcutEditorTransition(
            editingScript?.takeUnless { it.fromSuggestion },
            fabLaunchId = fabLaunchedScriptId,
            fabOrigin = fabLaunchOrigin,
            editorBackground = screenGround,
        ) { draft ->
        if (draft == null) {
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
                        accessibilityStatus = accessibilityStatus,
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
                        onOpenAccessibility = onOpenAccessibility,
                        onPickSuggestion = viewModel::createSuggestedScript,
                        onOpenProScreen = { viewModel.purchasePro(context as Activity) },
                        onOpenWhatsNew = { viewModel.openWhatsNew(fromLogo = true) },
                        onWhatsNewLogoBounds = { whatsNewLogoOrigin = it },
                        whatsNewLogoAway = whatsNewLogoLifted,
                        announceWhatsNew = whatsNewAnnouncement,
                        showWhatsNewHint = whatsNewHint && browsedWhatsNew == null && whatsNew == null &&
                            !settingsOpen && pagerState.currentPage == 0 && !showSetupWizard,
                        onWhatsNewHintFinished = viewModel::consumeWhatsNewHint,
                        nudgeDismissed = nudgeDismissed,
                        onDismissNudge = viewModel::dismissFirstShortcutNudge,
                        showColorOsRecentsTip = showColorOsRecentsTip,
                        onDismissColorOsRecentsTip = viewModel::dismissColorOsRecentsTip,
                        scrollState = homeScrollState,
                        scrollToProCard = scrollHomeToProCard,
                        onScrolledToProCard = { scrollHomeToProCard = false },
                        showRatingPrompt = showRatingPrompt && ratingHomeReady,
                        onRatingChoice = { choice ->
                            viewModel.answerRatingPrompt(choice)
                            if (choice == RatingPromptChoice.RATE) openKeySnapPlayListing(context)
                        },
                    )
                    else -> ScriptsScreen(
                        scripts = scripts,
                        globallyEnabled = isMappingEnabled,
                        themeMode = themeMode,
                        onToggleScriptEnabled = viewModel::toggleScriptEnabled,
                        onReorderScripts = viewModel::reorderScripts,
                        isPageVisible = pagerState.currentPage == 1 && !settingsOpen,
                        onRenameScript = viewModel::renameScript,
                        onSetScriptMode = viewModel::setScriptMode,
                        onDeleteScript = viewModel::deleteScript,
                        onOpenScript = viewModel::editScript,
                        onGoHome = { goToPage(0) },
                        onImportText = viewModel::importShortcutText,
                        onImportUri = viewModel::importShortcutUri,
                        failedScriptIds = remember(runLog) { latestFailedScriptIds(runLog) },
                        isPro = isPro,
                        onUpsell = viewModel::explainModes,
                        onUpdateScripts = viewModel::updateScripts,
                        onNewScriptId = viewModel::newScriptId,
                        savedKeys = savedKeys,
                        isRecordingKey = isRecordingKey,
                        onStartRecording = viewModel::startRecordingKey,
                        onCancelRecording = viewModel::cancelRecordingKey,
                        hapticDurationMs = config.hapticDurationMs,
                        vibrateOnTrigger = config.vibrateOnTrigger,
                    )
                }
            }
            AppBottomBar(
                // The target, not the current page, so the lens sets off on the tap rather than halfway through the slide.
                selectedPage = pagerState.targetPage,
                onPageSelected = goToPage,
                onNewShortcut = {
                    fabLaunchOrigin = shortcutFabCenter
                    viewModel.createScript(ModeHooks.modeForNewShortcut(pagerState.currentPage == 1))
                    fabLaunchedScriptId = viewModel.editingScript.value?.id
                },
                onNewShortcutCenterChanged = { shortcutFabCenter = it },
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
                    .padding(start = 12.dp, end = 12.dp, top = SheetTopGap, bottom = settingsClearance),
            ) {
                SettingsScreen(
                    config = config,
                    isServiceActive = isServiceActive,
                    accessibilityStatus = accessibilityStatus,
                    onOpenAccessibility = onOpenAccessibility,
                    accessibilityButton = accessibilityButton,
                    showTitle = false,
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
                    onOpenKeyCapsule = {
                        if (isPro) capsuleEditorOpen = true else capsuleIntroOpen = true
                    },
                    onOpenWhatsNew = { viewModel.openWhatsNew() },
                    savedKeys = savedKeys,
                    isRecordingKey = isRecordingKey,
                    onAddKey = viewModel::startAddingSavedKey,
                    onCancelAddKey = viewModel::cancelRecordingKey,
                    onRenameKey = viewModel::renameSavedKey,
                )
            }
            val showAutoWhatsNew = !settingsOpen && pagerState.currentPage == 0 && suggestionDraft == null &&
                !showSetupWizard && pendingImport == null && showNameKeyDialogForCode == null &&
                !showAccessibilityDisclosure
            val presentedWhatsNew = browsedWhatsNew ?: whatsNew?.takeIf { showAutoWhatsNew }
            presentedWhatsNew?.let { release ->
                WhatsNewDialog(
                    release = release,
                    themeMode = themeMode,
                    onDismiss = {
                        whatsNewLogoOrigin = null
                        whatsNewLogoLifted = false
                        viewModel.dismissWhatsNew()
                    },
                    onLogoLifted = { whatsNewLogoLifted = true },
                    // Only the header logo flies; an auto-shown or Settings-opened dialog just fades in.
                    logoOrigin = whatsNewLogoOrigin.takeIf { browsedWhatsNew != null },
                    onOpenSettings = { destination ->
                        whatsNewLogoOrigin = null
                        whatsNewLogoLifted = false
                        viewModel.dismissWhatsNew()
                        when (destination) {
                            WhatsNewDestination.KEY_CAPSULE -> {
                                settingsOpen = false
                                if (isPro) capsuleEditorOpen = true else capsuleIntroOpen = true
                            }
                            WhatsNewDestination.SHORTCUTS -> {
                                settingsOpen = false
                                goToPage(1)
                            }
                            WhatsNewDestination.ADVANCED_MODE -> {
                                settingsSection = SettingsSection.ADVANCED_MODE
                                settingsOpen = true
                            }
                            WhatsNewDestination.APPEARANCE -> {
                                settingsSection = SettingsSection.APPEARANCE
                                settingsOpen = true
                            }
                            WhatsNewDestination.ABOUT -> {
                                settingsSection = SettingsSection.ABOUT
                                settingsOpen = true
                            }
                        }
                    },
                )
            }
            SettingsGear(
                settingsMotion,
                settingsOpen,
                onClick = { settingsOpen = !settingsOpen },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 16.dp, end = 14.dp),
            )
            // Over the gear for a moment. The wizard would hide it, and its three seconds count only
            // while the app is in front, so a pairing finished in Settings is still announced.
            val bannerShown = hijackBannerStage == HijackBannerStage.BOTTOM_EXPANDED && !showSetupWizard
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(bannerShown) {
                if (!bannerShown) return@LaunchedEffect
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                delay(3_000)
                viewModel.dismissHijackBanner()
            }
            AnimatedVisibility(
                visible = bannerShown,
                enter = slideInVertically(spring(dampingRatio = .72f, stiffness = Spring.StiffnessMediumLow)) { -it * 2 } + fadeIn(),
                exit = slideOutVertically(tween(260)) { -it * 2 } + fadeOut(tween(260)),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AdvancedActivationBanner(viewModel::dismissHijackBanner)
            }
            // The editor's own key dock. Assigning the key saves the shortcut outright, which
            // clears the draft and the dock; the recipe keeps its press type, editable later.
            BackHandler(enabled = suggestionDraft != null) { viewModel.discardDraft() }
            AnimatedVisibility(suggestionDraft != null, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f))
                        .clickable(interactionSource = null, indication = null, onClick = viewModel::discardDraft),
                )
            }
            AnimatedVisibility(
                suggestionDraft != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                InlineKeyPicker(
                    savedKeys = savedKeys,
                    isRecording = isRecordingKey,
                    onDismiss = viewModel::discardDraft,
                    onSelectKey = viewModel::draftSetTriggerKey,
                    onStartRecording = viewModel::startRecordingKey,
                    onCancelRecording = viewModel::cancelRecordingKey,
                    // A phone with no spare key can still use a recipe, from a widget or tile.
                    onSwitchToSurface = { viewModel.draftSetSurfaceOnly(true) },
                    advancedModeActive = isAdvancedModeRunning,
                    onSetUpAdvancedMode = { viewModel.openSetupWizard(0) },
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
            var keypressTrigger by remember(draft.id) { mutableStateOf(draft.trigger) }
            LaunchedEffect(draft.trigger) {
                if (!draft.automation && draft.trigger != null) keypressTrigger = draft.trigger
            }
            val installedApps by viewModel.installedApps.collectAsState()
            val pickedPoints by viewModel.pickedPoints.collectAsState()
            var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

            val overlayPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { canDrawOverlays = Settings.canDrawOverlays(context) }

            fun closeEditorSheets() {
                viewModel.cancelRecordingKey()
                showTriggerPicker = false
                showScriptSettings = false
                configuringType = null
                configuringIndex = null
                pendingConstraintType = null
                pendingConstraintIndex = null
            }
            LaunchedEffect(showTriggerPicker) {
                if (showTriggerPicker) viewModel.keyRecordingResults.collect { showTriggerPicker = false }
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
                    if (viewModel.acceptProFeature(type)) {
                        viewModel.loadInstalledApps()
                        configuringIndex = null
                        configuringType = type
                    }
                },
                onConfigureAction = { index ->
                    val type = draft.actions.getOrNull(index)?.type
                    if (type != null && viewModel.acceptProFeature(type)) {
                        if (type in ASSISTANT_SETTINGS_ACTION_TYPES) {
                            viewModel.openAssistantSettings(context)
                        } else {
                            viewModel.loadInstalledApps()
                            configuringIndex = index
                            configuringType = type
                        }
                    }
                },
                onRemoveAction = viewModel::draftRemoveAction,
                onMoveAction = viewModel::draftMoveAction,
                onRemoveConstraint = viewModel::draftRemoveConstraint,
                onSetConstraintParams = viewModel::draftSetConstraintParams,
                onTestAction = viewModel::draftTestAction,
                onConfigureConstraint = { index ->
                    draft.constraints.getOrNull(index)?.let {
                        if (viewModel.acceptProFeature(it.type)) {
                            viewModel.loadInstalledApps()
                            pendingConstraintIndex = index
                            pendingConstraintType = it.type
                        }
                    }
                },
                installedApps = installedApps,
                advancedModeActive = isAdvancedModeRunning,
                runs = runLog.filter { it.scriptId == draft.id },
                onOpenTriggerPicker = { showTriggerPicker = true },
                keyPickerOpen = showTriggerPicker,
                savedKeys = savedKeys,
                isRecordingKey = isRecordingKey,
                recordingKeys = recordingKeys,
                onDismissKeyPicker = {
                    viewModel.cancelRecordingKey()
                    showTriggerPicker = false
                },
                onSelectKey = {
                    viewModel.cancelRecordingKey()
                    viewModel.draftSetTriggerKey(it)
                    showTriggerPicker = false
                },
                onStartRecording = viewModel::startRecordingKey,
                onCancelRecording = viewModel::cancelRecordingKey,
                onSetUpAdvancedMode = { viewModel.openSetupWizard(0) },
                onSetPressType = viewModel::draftSetPressType,
                onSwitchToSurface = {
                    viewModel.cancelRecordingKey()
                    showTriggerPicker = false
                    viewModel.draftSetSurfaceOnly(true)
                },
                onToggleAutomation = {
                    if (draft.automation) {
                        viewModel.draftToggleAutomation()
                        keypressTrigger?.let { trigger ->
                            viewModel.draftSetTriggerKeys(trigger.keyCodes)
                            viewModel.draftSetPressType(trigger.pressType)
                            viewModel.draftSetDoublePressWindow(trigger.doublePressWindowMs)
                            viewModel.draftSetLongPressThreshold(trigger.longPressThresholdMs)
                        }
                    } else {
                        keypressTrigger = draft.trigger
                        viewModel.draftToggleAutomation()
                    }
                },
                onClearSurfaceOnly = { viewModel.draftSetSurfaceOnly(false) },
                onPickConstraint = { type ->
                    if (viewModel.acceptProFeature(type)) {
                        if (ConstraintEditorForms.form(type) == null) {
                            viewModel.draftAddConstraint(type)
                        } else {
                            viewModel.loadInstalledApps()
                            pendingConstraintIndex = null
                            pendingConstraintType = type
                        }
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
                scripts = scripts,
                isPro = isPro,
                onSetRunsIn = viewModel::draftSetRunsIn,
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
                    scripts = scripts,
                    onConfirm = { params ->
                        val aligned = ModeHooks.alignCycleCount(type, params, scripts)
                        if (index == null) viewModel.draftAddAction(type, aligned) else viewModel.draftSetActionParams(index, aligned)
                        configuringType = null
                        configuringIndex = null
                    },
                    onDismiss = {
                        configuringType = null
                        configuringIndex = null
                    },
                    // New actions only: an edited row keeps its type, so its params would land on the wrong action.
                    onSwitchAction = if (index == null) {
                        { newType -> if (viewModel.acceptProFeature(newType)) configuringType = newType }
                    } else {
                        null
                    },
                    advancedActive = isAdvancedModeRunning,
                    onSetUpAdvancedMode = { viewModel.openSetupWizard(0) },
                    everyChatWarning = ScriptConstraintCatalog.startsFromNotification(draft.automation, draft.constraints) &&
                        draft.constraints.none {
                            it.type == ScriptConstraintCatalog.NOTIFICATION_RECEIVED && !it.params["contact"].isNullOrBlank()
                        },
                    cooldownRemoved = draft.cooldownRemoved,
                    existingCooldownMinutes = draft.constraints
                        .firstOrNull { it.type == ScriptConstraintCatalog.COOLDOWN }
                        ?.params
                        ?.get("minutes"),
                    onRemoveReplyCooldown = viewModel::draftDismissReplyCooldown,
                    notificationAccessGranted = NotificationEventListener.isAccessGranted(context),
                    canWriteSettings = Settings.System.canWrite(context),
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
                    onSetShowCapsule = viewModel::draftSetShowCapsule,
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

    // Same pane change as the Pro editor below, so the free preview never cuts in.
    AnimatedVisibility(
        visible = capsuleIntroOpen && !isPro,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(200, delayMillis = 90)) +
            slideInVertically(tween(260, delayMillis = 90)) { height -> height / 12 },
        exit = fadeOut(tween(110)),
    ) {
        CapsuleHooks.Intro(
            themeMode = themeMode,
            onClose = { capsuleIntroOpen = false },
            onExplorePro = {
                capsuleIntroOpen = false
                settingsOpen = false
                pagerScope.launch {
                    pagerState.animateScrollToPage(0, animationSpec = PageSlide)
                    scrollHomeToProCard = true
                }
            },
        )
    }
    // Same pane change as ScriptEditorScreen: the editor fades and rises a twelfth; leaving fades.
    AnimatedVisibility(
        visible = capsuleEditorOpen && isPro,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(200, delayMillis = 90)) +
            slideInVertically(tween(260, delayMillis = 90)) { height -> height / 12 },
        exit = fadeOut(tween(110)),
    ) {
        CapsuleHooks.Editor(
            savedKeyLabels = savedKeys.associate { it.keyCode to keyDisplayName(context.resources, it.keyCode, savedKeys) },
            themeMode = themeMode,
            onClose = { capsuleEditorOpen = false },
        )
    }
    if (showSetupWizard) SetupWizardModal(viewModel, themeMode, viewModel::closeSetupWizard)
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
        AccessibilityDisclosureSheet(
            onAgree = {
                showAccessibilityDisclosure = false
                // A live bridge can switch KeySnap's entry on (or redo its off/on) directly; without it, Android's screen does.
                if (canReconnect) viewModel.reconnectAccessibility(context)
                else if (isAdvancedModeRunning && accessibilityStatus == AccessibilityStatus.DISABLED) {
                    viewModel.reconnectAccessibility(context, enable = true)
                } else viewModel.openAccessibilitySettings(context)
            },
            onDismiss = { showAccessibilityDisclosure = false },
        )
    }
    if (showModesExplainer && !isPro) {
        ModeHooks.ModesExplainer(
            onUnlock = viewModel::unlockModes,
            onDismiss = viewModel::dismissModesExplainer,
        )
    }
}

/** The page slide, at the redesign's 1.25x pace. */
private val PageSlide = tween<Float>(300, easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f))
