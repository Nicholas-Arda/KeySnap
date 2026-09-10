package com.example.viewmodel

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ads.RewardedAdController
import com.example.billing.PurchaseManager
import com.example.data.ActionEditorForms
import com.example.data.EntitlementManager
import com.example.data.InstalledApp
import com.example.data.InstalledAppsRepository
import com.example.data.KeyMappingConfig
import com.example.data.KeyMappingRepository
import com.example.data.LiveKeyEvent
import com.example.data.ScriptAction
import com.example.data.ScriptActionCatalog
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.data.ScriptRepository
import com.example.data.ScriptTrigger
import com.example.data.ShortcutScript
import com.example.data.SuggestedShortcut
import com.example.data.TriggerPressType
import com.example.data.getReadableKeyName
import com.example.service.FlashlightController
import com.example.service.HardwareKeyTriggerCoordinator
import com.example.service.KeyMapperAccessibilityService
import com.example.service.PickedPoints
import com.example.service.PointPickMode
import com.example.service.ScreenPointPicker
import com.example.service.TwoPointGesture
import com.example.service.ScriptActionExecutor
import com.example.service.adb.PairingStatus
import com.example.service.adb.WirelessAdbManager
import com.example.service.bridge.BridgeController
import com.example.service.bridge.BridgeState
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class HijackBannerStage {
    HIDDEN,
    BOTTOM_EXPANDED,
    ANIMATING_TO_TOP,
    SETTLED_AT_TOP
}

/** In-progress edits for one script. Not written through until [MainViewModel.commitDraft]. */
data class ScriptDraft(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger: ScriptTrigger?,
    /** The press type chosen in the trigger sheet before any key exists; [trigger] owns it afterwards. */
    val pressType: TriggerPressType = TriggerPressType.SINGLE_PRESS,
    /** Key-less: fires on the false -> true edge of the constraints; see [ShortcutScript.automation]. */
    val automation: Boolean = false,
    val actions: List<ScriptAction>,
    val constraints: List<ScriptConstraint> = emptyList(),
    val isNew: Boolean,
    /** A draft started from a Home suggestion skips the editor: assigning a key saves it outright. */
    val fromSuggestion: Boolean = false,
    val consumeOriginalEvent: Boolean? = null,
    val vibrateOnTrigger: Boolean? = null,
    val hapticDurationMs: Long? = null,
)

class MainViewModel(
    private val repository: KeyMappingRepository,
    private val appContext: Context
) : ViewModel() {

    /** User-facing strings live in resources; the app context carries the chosen language. */
    private val res get() = appContext.resources

    private val scriptRepository = ScriptRepository.getInstance(appContext)
    val config: StateFlow<KeyMappingConfig> = repository.config
    val isVibratorAvailable: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator?.hasVibrator() == true
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Vibrator::class.java)?.hasVibrator() == true
    }
    val scripts: StateFlow<List<ShortcutScript>> = scriptRepository.scripts
val isMappingEnabled: StateFlow<Boolean> = scriptRepository.globallyEnabled

    private val entitlementManager = EntitlementManager.getInstance(appContext)
    private val rewardedAdController = RewardedAdController(appContext)
    private val purchaseManager = PurchaseManager(appContext, entitlementManager)
    val isPro: StateFlow<Boolean> = entitlementManager.isPro
    val rewardExpiryAt: StateFlow<Long> = entitlementManager.rewardExpiryAt

    /** Null until Play answers the product query, so the Pro button falls back to a price-less label. */
    val proPrice: StateFlow<String?> = purchaseManager.proPrice

    /** One-shot shell-snackbar text. [showProAction] is carried alongside because only the free-limit
     *  messages have somewhere for the snackbar's Home action to go; a plain confirmation does not. */
    data class ScriptsMessage(val text: String, val showProAction: Boolean = false)

    /** One-shot text for why a script toggle was refused — the Scripts screen has no other place to
     *  answer a tap that silently would have been reverted by [EntitlementManager.enforceLimit]. */
    private val _scriptsMessage = MutableStateFlow<ScriptsMessage?>(null)
    val scriptsMessage: StateFlow<ScriptsMessage?> = _scriptsMessage.asStateFlow()

    fun consumeScriptsMessage() {
        _scriptsMessage.value = null
    }

    val isFlashlightOn: StateFlow<Boolean> = FlashlightController.isFlashlightOn
    val isFlashlightAvailable: StateFlow<Boolean> = FlashlightController.isAvailable
    val flashlightMessage: StateFlow<String?> = FlashlightController.lastActionMessage

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _keyHistory = MutableStateFlow<List<LiveKeyEvent>>(emptyList())
    val keyHistory: StateFlow<List<LiveKeyEvent>> = _keyHistory.asStateFlow()

    val isRecordingKey: StateFlow<Boolean> = HardwareKeyTriggerCoordinator.isRecordingKey
    val triggerCount: StateFlow<Int> = HardwareKeyTriggerCoordinator.triggerCount
    val lastCapturedSource: StateFlow<String> = HardwareKeyTriggerCoordinator.lastCapturedSource

    val savedKeys = repository.savedKeys

    private val _showNameKeyDialogForCode = MutableStateFlow<Int?>(null)
    val showNameKeyDialogForCode: StateFlow<Int?> = _showNameKeyDialogForCode.asStateFlow()

    fun dismissNameKeyDialog() {
        _showNameKeyDialogForCode.value = null
    }

    fun saveUnknownKey(keyCode: Int, name: String) {
        repository.addSavedKey(keyCode, name)
        applyRecordedKey(keyCode)
        _showNameKeyDialogForCode.value = null
    }

    // Script editor draft state
    private val actionExecutor = ScriptActionExecutor.android(appContext)
    private val _editingScript = MutableStateFlow<ScriptDraft?>(null)
    val editingScript: StateFlow<ScriptDraft?> = _editingScript.asStateFlow()

    /**
     * One-shot text for the editor: why a save was refused, or how a test run went. The editor is a
     * full screen with no other place to answer a tap, so every action that can fail says so here.
     */
    private val _editorMessage = MutableStateFlow<String?>(null)
    val editorMessage: StateFlow<String?> = _editorMessage.asStateFlow()

    fun consumeEditorMessage() {
        _editorMessage.value = null
    }

    fun createScript() {
        _editingScript.value = ScriptDraft(
            id = scriptRepository.newScriptId(),
            name = "",
            enabled = true,
            trigger = null,
            actions = emptyList(),
            isNew = true,
        )
    }

    fun createSuggestedScript(suggestion: SuggestedShortcut) {
        if (!suggestion.available) return
        _editingScript.value = ScriptDraft(
            id = scriptRepository.newScriptId(),
            name = res.getString(suggestion.titleRes),
            enabled = true,
            trigger = null,
            automation = suggestion.automation,
            actions = suggestion.actions,
            constraints = suggestion.constraints,
            isNew = true,
            // An automation opens the editor instead: the user should see the condition and can
            // finish any choice it needs (a Bluetooth device, say) before saving.
            fromSuggestion = !suggestion.automation,
        )
    }

    fun editScript(id: String) {
        val script = scripts.value.firstOrNull { it.id == id } ?: return
        _editingScript.value = ScriptDraft(
            id = script.id,
            name = script.name,
            enabled = script.enabled,
            trigger = script.triggers.firstOrNull(),
            automation = script.automation,
            actions = script.actions,
            constraints = script.constraints,
            isNew = false,
            consumeOriginalEvent = script.consumeOriginalEvent,
            vibrateOnTrigger = script.vibrateOnTrigger,
            hapticDurationMs = script.hapticDurationMs,
        )
    }

    private fun updateDraft(transform: (ScriptDraft) -> ScriptDraft) {
        _editingScript.value = _editingScript.value?.let(transform)
    }

    fun draftSetName(name: String) = updateDraft { it.copy(name = name) }

    fun draftAddAction(type: String, params: Map<String, String> = emptyMap()) {
        if (!ScriptActionCatalog.isAvailable(type)) return
        updateDraft { it.copy(actions = it.actions + ScriptAction(type, params)) }
    }

    fun draftRemoveAction(index: Int) = updateDraft { draft ->
        draft.copy(actions = draft.actions.filterIndexed { i, _ -> i != index })
    }

    fun draftMoveAction(from: Int, to: Int) = updateDraft { draft ->
        if (from !in draft.actions.indices || to !in draft.actions.indices) return@updateDraft draft
        val mutable = draft.actions.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        draft.copy(actions = mutable)
    }

    /** Replaces the whole map rather than merging, so a cleared optional field actually clears. */
    fun draftSetActionParams(index: Int, params: Map<String, String>) = updateDraft { draft ->
        if (index !in draft.actions.indices) return@updateDraft draft
        draft.copy(
            actions = draft.actions.mapIndexed { i, action ->
                if (i == index) action.copy(params = params) else action
            },
        )
    }

    private val installedAppsRepository = InstalledAppsRepository(appContext)
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch { _installedApps.value = installedAppsRepository.launchableApps() }
    }

    val pickedPoints: StateFlow<PickedPoints?> = ScreenPointPicker.result

    /** The caller must have confirmed `Settings.canDrawOverlays` before this. */
    fun startPointPick(context: Context, mode: PointPickMode, gesture: TwoPointGesture = TwoPointGesture.NONE) =
        ScreenPointPicker.start(context, mode, gesture)

    fun consumePickedPoints() {
        ScreenPointPicker.consumeResult()
    }

    fun draftSetTriggerKey(keyCode: Int) {
        updateDraft { draft ->
            val pressType = draft.trigger?.pressType ?: draft.pressType
            draft.copy(
                trigger = ScriptTrigger(listOf(keyCode), pressType, draft.trigger?.doublePressWindowMs, draft.trigger?.longPressThresholdMs),
                automation = false,
            )
        }
        // A suggestion draft is complete the moment it has a key; the editor is never shown for it.
        if (_editingScript.value?.fromSuggestion == true) commitDraft()
    }

    /** Tapping the automation card again returns the When card to its key/automation choice. */
    fun draftToggleAutomation() = updateDraft { it.copy(automation = !it.automation, trigger = null) }

    /** From the key sheet's "use an automation instead" link: replaces whatever key was set. */
    fun draftSetAutomation() = updateDraft { it.copy(automation = true, trigger = null) }

    fun draftSetPressType(type: TriggerPressType) = updateDraft { draft ->
        draft.trigger?.let { draft.copy(trigger = it.copy(pressType = type)) } ?: draft.copy(pressType = type)
    }

    fun draftSetDoublePressWindow(ms: Long?) = updateDraft { draft ->
        draft.trigger?.let { draft.copy(trigger = it.copy(doublePressWindowMs = ms)) } ?: draft
    }

    fun draftSetLongPressThreshold(ms: Long?) = updateDraft { draft ->
        draft.trigger?.let { draft.copy(trigger = it.copy(longPressThresholdMs = ms)) } ?: draft
    }

    fun draftSetEnabled(enabled: Boolean) = updateDraft { it.copy(enabled = enabled) }

    fun draftSetConsumeOriginalEvent(consume: Boolean?) = updateDraft { it.copy(consumeOriginalEvent = consume) }

    fun draftSetVibrateOnTrigger(vibrate: Boolean?) = updateDraft { it.copy(vibrateOnTrigger = vibrate) }

    fun draftSetHapticDurationMs(value: Long?) = updateDraft { it.copy(hapticDurationMs = value) }

    fun draftAddConstraint(type: String, params: Map<String, String> = emptyMap()) {
        if (!ScriptConstraintCatalog.isAvailable(type)) return
        updateDraft { draft ->
            if (draft.constraints.any { it.type == type }) draft
            else draft.copy(constraints = draft.constraints + ScriptConstraint(type, params))
        }
    }

    /** Replaces the whole map rather than merging, so a cleared optional field actually clears. */
    fun draftSetConstraintParams(index: Int, params: Map<String, String>) = updateDraft { draft ->
        if (index !in draft.constraints.indices) return@updateDraft draft
        draft.copy(
            constraints = draft.constraints.mapIndexed { i, constraint ->
                if (i == index) constraint.copy(params = params) else constraint
            },
        )
    }

    fun draftRemoveConstraint(index: Int) = updateDraft { draft ->
        draft.copy(constraints = draft.constraints.filterIndexed { i, _ -> i != index })
    }

    /** Runs the chain once from the editor and reports what happened - a silent Run reads as broken. */
    fun draftRunOnce() {
        val actions = _editingScript.value?.actions.orEmpty()
        if (actions.isEmpty()) return
        viewModelScope.launch {
            // While the bridge owns execution, Run has to go through it or it demonstrates the
            // wrong thing - the app's own ringer path lights up Do Not Disturb where a real press
            // no longer does. The app keeps the same share it runs on a real trigger, see
            // HardwareKeyTriggerCoordinator.onBridgeTrigger.
            val onBridge = BridgeController.runActions(actions)
            val results = if (onBridge == null) {
                actionExecutor.execute(actions)
            } else {
                actionExecutor.execute(actions) { it.type in ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE }
            }
            val ran = results.count { it } + (onBridge?.first ?: 0)
            _editorMessage.value = when {
                results.isEmpty() -> null
                ran == 0 -> res.getString(R.string.run_nothing_ran)
                ran < results.size -> res.getString(R.string.run_partial, ran, results.size)
                else -> res.getQuantityString(R.plurals.run_all, ran, ran)
            }
        }
    }

    /**
     * Saves the draft, or explains why it cannot. Returning silently left the editor open with the
     * Done button apparently doing nothing, so every path here either closes the editor or says why.
     *
     * A draft that would push the free plan over its limit is saved switched off rather than saved
     * on and immediately reverted by [EntitlementManager.enforceLimit] — that used to happen with no
     * explanation, same as an over-limit toggle in [toggleScriptEnabled].
     */
    private fun nextUnnamedShortcutName(): String {
        val used = scriptRepository.scripts.value.mapNotNull {
            Regex("""^Shortcut #(\d+)$""").find(it.name)?.groupValues?.get(1)?.toIntOrNull()
        }
        return res.getString(R.string.default_shortcut_name, (used.maxOrNull() ?: 0) + 1)
    }

    fun commitDraft() {
        val draft = _editingScript.value ?: return
        if (draft.actions.isEmpty()) {
            _editorMessage.value = res.getString(R.string.editor_needs_action)
            return
        }
        // Automations are key-less by design; anything else saved without a key is a card with an
        // ON toggle that can never fire.
        if (!draft.automation && draft.trigger == null) {
            _editorMessage.value = res.getString(R.string.editor_needs_trigger)
            return
        }
        val unconfigured = draft.actions.count { ActionEditorForms.missingRequired(it).isNotEmpty() }
        val overLimit = draft.enabled && !entitlementManager.isPro.value &&
            scriptRepository.scripts.value.count { it.enabled && it.id != draft.id } >= entitlementManager.activeScriptLimit
        val name = draft.name.ifBlank { nextUnnamedShortcutName() }
        scriptRepository.upsert(
            ShortcutScript(
                id = draft.id,
                name = name,
                enabled = draft.enabled && !overLimit,
                triggers = listOfNotNull(draft.trigger),
                actions = draft.actions,
                constraints = draft.constraints,
                automation = draft.automation,
                consumeOriginalEvent = draft.consumeOriginalEvent,
                vibrateOnTrigger = draft.vibrateOnTrigger,
                hapticDurationMs = draft.hapticDurationMs,
            ),
        )
        // The editor is closed by the time any of these is set, so they all go to the shell snackbar:
        // an _editorMessage here would be raised into a screen nobody is looking at. Only the two
        // validation failures above, which keep the editor open, still answer in the editor.
        _editingScript.value = null
        _scriptsMessage.value = if (overLimit) {
            ScriptsMessage(
                res.getString(R.string.free_limit_saved_off, entitlementManager.activeScriptLimit),
                showProAction = true,
            )
        } else if (draft.fromSuggestion && draft.trigger != null && unconfigured == 0) {
            ScriptsMessage(
                res.getString(
                    R.string.suggestion_saved,
                    name,
                    getReadableKeyName(res, draft.trigger.keyCodes.first()),
                ),
            )
        } else {
            ScriptsMessage(
                when {
                    unconfigured > 0 -> res.getQuantityString(R.plurals.saved_needs_config, unconfigured, unconfigured)
                    draft.automation && draft.constraints.none { ScriptConstraintCatalog.isAutomation(it.type) } ->
                        res.getString(R.string.saved_needs_condition)
                    else -> res.getString(R.string.saved_named, name)
                },
            )
        }
    }

    fun discardDraft() {
        _editingScript.value = null
    }

    fun deleteScript(id: String) {
        scriptRepository.delete(id)
        if (_editingScript.value?.id == id) _editingScript.value = null
    }

    private fun applyRecordedKey(keyCode: Int) {
        if (_editingScript.value != null) draftSetTriggerKey(keyCode) else repository.setTargetKey(keyCode)
    }

    // Advanced Mode & Wireless ADB State
    val isAdvancedModeRunning: StateFlow<Boolean> = WirelessAdbManager.isAdvancedModeRunning
    val pairingStatus: StateFlow<PairingStatus> = WirelessAdbManager.pairingStatus
    val statusMessage: StateFlow<String> = WirelessAdbManager.statusMessage
    val autoAssistantEnabled: StateFlow<Boolean> = WirelessAdbManager.autoAssistantEnabled
    val discoveredIp: StateFlow<String> = WirelessAdbManager.discoveredIp
    val discoveredPairingPort: StateFlow<Int?> = WirelessAdbManager.discoveredPairingPort
    val discoveredPairingCode: StateFlow<String?> = WirelessAdbManager.discoveredPairingCode
    val discoveredConnectPort: StateFlow<Int?> = WirelessAdbManager.discoveredConnectPort
    val lastRawKernelLine: StateFlow<String?> = WirelessAdbManager.lastRawKernelLine

    /** Detached bridge lifecycle, exposed for Advanced Mode diagnostics in the wizard/settings. */
    val bridgeState: StateFlow<BridgeState> = BridgeController.state
    val bridgeStatusMessage: StateFlow<String> = BridgeController.statusMessage
    val bridgeInfo = BridgeController.bridgeInfo

    private val appearancePrefs = appContext.getSharedPreferences("arda_mapper_appearance", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(
        appearancePrefs.getString("theme_mode", null)
            ?.let { runCatching { com.example.ui.theme.ThemeMode.valueOf(it) }.getOrNull() }
            ?: if (appearancePrefs.getBoolean("dark_theme", true)) com.example.ui.theme.ThemeMode.DARK else com.example.ui.theme.ThemeMode.LIGHT,
    )
    val themeMode: StateFlow<com.example.ui.theme.ThemeMode> = _themeMode.asStateFlow()

    // Setup Wizard UI State & Persistence
    private val wizardPrefs = appContext.getSharedPreferences("arda_mapper_wizard_prefs", Context.MODE_PRIVATE)
    private val _showSetupWizard = MutableStateFlow(wizardPrefs.getBoolean("wizard_is_open", false))
    val showSetupWizard: StateFlow<Boolean> = _showSetupWizard.asStateFlow()

    private val _wizardStep = MutableStateFlow(wizardPrefs.getInt("wizard_current_step", 0))
    val wizardStep: StateFlow<Int> = _wizardStep.asStateFlow()

    /** Whether Home's "make your first shortcut" nudge has been waved away for good. */
    private val _nudgeDismissed = MutableStateFlow(wizardPrefs.getBoolean("first_shortcut_nudge_dismissed", false))
    val nudgeDismissed: StateFlow<Boolean> = _nudgeDismissed.asStateFlow()

    fun dismissFirstShortcutNudge() {
        _nudgeDismissed.value = true
        wizardPrefs.edit().putBoolean("first_shortcut_nudge_dismissed", true).apply()
    }

    private val _wizardManualIp = MutableStateFlow(wizardPrefs.getString("wizard_manual_ip", "127.0.0.1") ?: "127.0.0.1")
    val wizardManualIp: StateFlow<String> = _wizardManualIp.asStateFlow()

    private val _wizardManualPairingPort = MutableStateFlow(wizardPrefs.getString("wizard_manual_pairing_port", "") ?: "")
    val wizardManualPairingPort: StateFlow<String> = _wizardManualPairingPort.asStateFlow()

    private val _wizardManualPairingCode = MutableStateFlow(wizardPrefs.getString("wizard_manual_pairing_code", "") ?: "")
    val wizardManualPairingCode: StateFlow<String> = _wizardManualPairingCode.asStateFlow()

    private val _wizardManualConnectPort = MutableStateFlow(wizardPrefs.getString("wizard_manual_connect_port", "") ?: "")
    val wizardManualConnectPort: StateFlow<String> = _wizardManualConnectPort.asStateFlow()

    private val _hijackBannerStage = MutableStateFlow(
        if (WirelessAdbManager.isAdvancedModeRunning.value) HijackBannerStage.SETTLED_AT_TOP else HijackBannerStage.HIDDEN
    )
    val hijackBannerStage: StateFlow<HijackBannerStage> = _hijackBannerStage.asStateFlow()

    private var bannerJob: Job? = null

    init {
        HardwareKeyTriggerCoordinator.initialize(appContext, repository)
        WirelessAdbManager.initialize(appContext)
        refreshServiceStatus()

        // Google documents this as a slow call that should stay off the main thread.
        viewModelScope.launch(Dispatchers.IO) { MobileAds.initialize(appContext) }
        rewardedAdController.preload()
        purchaseManager.start()
        viewModelScope.launch {
            scriptRepository.scripts.collect { entitlementManager.enforceLimit() }
        }

        // Sync auto-detected credentials with manual fields if empty or newly discovered
        viewModelScope.launch {
            WirelessAdbManager.discoveredPairingCode.collect { code ->
                if (!code.isNullOrBlank()) {
                    _wizardManualPairingCode.value = code
                    wizardPrefs.edit().putString("wizard_manual_pairing_code", code).apply()
                }
            }
        }

        viewModelScope.launch {
            WirelessAdbManager.discoveredPairingPort.collect { port ->
                if (port != null && port > 0) {
                    _wizardManualPairingPort.value = port.toString()
                    wizardPrefs.edit().putString("wizard_manual_pairing_port", port.toString()).apply()
                }
            }
        }

        viewModelScope.launch {
            WirelessAdbManager.discoveredIp.collect { ip ->
                if (ip.isNotBlank() && ip != "0.0.0.0") {
                    _wizardManualIp.value = ip
                    wizardPrefs.edit().putString("wizard_manual_ip", ip).apply()
                }
            }
        }

        viewModelScope.launch {
            WirelessAdbManager.discoveredConnectPort.collect { port ->
                if (port != null && port > 0) {
                    _wizardManualConnectPort.value = port.toString()
                    wizardPrefs.edit().putString("wizard_manual_connect_port", port.toString()).apply()
                }
            }
        }

        // Observe events from HardwareKeyTriggerCoordinator
        viewModelScope.launch {
            HardwareKeyTriggerCoordinator.latestKeyEvent.collect { event ->
                addKeyEvent(event)
            }
        }

        viewModelScope.launch {
            KeyMapperAccessibilityService.isServiceActive.collect { active ->
                if (active) {
                    _isServiceActive.value = true
                } else {
                    refreshServiceStatus()
                }
            }
        }

        // Observe Advanced Mode activation to trigger banner animation. Wait for the launch-time
        // bridge probe to resolve before reacting: that probe adopts a bridge left running from an
        // earlier app lifetime, and its (often delayed) false-to-true flip must settle silently
        // rather than replay the activation banner. Only a real transition afterwards should animate.
        viewModelScope.launch {
            WirelessAdbManager.startupBridgeCheckComplete.first { it }
            var isFirstEmission = true
            WirelessAdbManager.isAdvancedModeRunning.collect { running ->
                if (running) {
                    if (isFirstEmission) {
                        _hijackBannerStage.value = HijackBannerStage.SETTLED_AT_TOP
                    } else {
                        startAdvancedActivationAnimation()
                    }
                } else {
                    _hijackBannerStage.value = HijackBannerStage.HIDDEN
                }
                isFirstEmission = false
            }
        }

        // The detached bridge matches against a config file rather than shared memory, so any
        // change the user makes here has to be republished for it to take effect.
        viewModelScope.launch {
            combine(
                scriptRepository.scripts,
                scriptRepository.globallyEnabled,
                repository.config,
            ) { _, _, _ -> Unit }
                .drop(1)
                .collect { BridgeController.publishConfig() }
        }

        viewModelScope.launch {
            HardwareKeyTriggerCoordinator.recordedKey.collect { keyCode ->
                val existing = savedKeys.value.find { it.keyCode == keyCode }
                if (existing != null) {
                    applyRecordedKey(keyCode)
                } else {
                    val knownName = com.example.data.getReadableKeyName(res, keyCode)
                    if (com.example.data.isUnlabelledKey(keyCode)) {
                        _showNameKeyDialogForCode.value = keyCode
                    } else {
                        repository.addSavedKey(keyCode, knownName)
                        applyRecordedKey(keyCode)
                    }
                }
            }
        }
    }

    fun refreshServiceStatus() {
        val runningFromService = KeyMapperAccessibilityService.isServiceActive.value
        val enabledInSettings = isAccessibilityServiceEnabled(appContext, KeyMapperAccessibilityService::class.java)
        _isServiceActive.value = runningFromService || enabledInSettings
    }

    private fun addKeyEvent(event: LiveKeyEvent) {
        val current = _keyHistory.value.toMutableList()
        current.add(0, event)
        if (current.size > 20) {
            current.removeAt(current.lastIndex)
        }
        _keyHistory.value = current
    }

    fun onDirectActivityKeyEvent(keyCode: Int, action: Int): Boolean {
        val isDown = action == KeyEvent.ACTION_DOWN
        return HardwareKeyTriggerCoordinator.dispatchKeyEvent(keyCode, isDown, res.getString(R.string.source_activity))
    }

    fun toggleFlashlight() {
        FlashlightController.toggle(appContext)
    }

    fun toggleEnabled(enabled: Boolean) {
        repository.setEnabled(enabled)
        scriptRepository.setGloballyEnabled(enabled)
    }

    /** Refuses a toggle past the free limit outright instead of flipping it on and letting
     *  [EntitlementManager.enforceLimit] silently flip it back — that left the switch bouncing with
     *  no explanation. */
    fun toggleScriptEnabled(id: String, enabled: Boolean) {
        if (enabled && !entitlementManager.isPro.value) {
            val activeCount = scriptRepository.scripts.value.count { it.enabled }
            if (activeCount >= entitlementManager.activeScriptLimit) {
                _scriptsMessage.value = ScriptsMessage(
                    if (entitlementManager.rewardActive) {
                        res.getString(R.string.free_limit_reached)
                    } else {
                        res.getString(R.string.free_limit_toggle_blocked, EntitlementManager.FREE_SCRIPT_LIMIT)
                    },
                    showProAction = true,
                )
                return
            }
        }
        scriptRepository.setScriptEnabled(id, enabled)
    }

    /** Tapping the reward button: shows the ad when one is loaded and ready, on cooldown goes
     *  straight to Pro purchase instead of loading another ad. */
    fun onRewardButtonClick(activity: Activity) {
        if (entitlementManager.rewardActive) {
            purchasePro(activity)
            return
        }
        rewardedAdController.show(activity) { entitlementManager.grantRewardWindow() }
    }

    /** Called by the reward-countdown clock tick so an expired reward window turns its bonus
     *  script back off on its own, instead of waiting for the next unrelated script-list edit. */
    fun onClockTick() {
        entitlementManager.enforceLimit()
    }

    fun purchasePro(activity: Activity) {
        purchaseManager.purchasePro(activity)
    }

    fun toggleConsumeOriginal(consume: Boolean) {
        repository.setConsumeOriginalEvent(consume)
    }

    fun toggleVibrate(vibrate: Boolean) {
        repository.setVibrate(vibrate)
    }

    fun setDoublePressWindowMs(value: Long) {
        repository.setDoublePressWindowMs(value)
    }

    fun setLongPressThresholdMs(value: Long) {
        repository.setLongPressThresholdMs(value)
    }

    fun setHapticDurationMs(value: Long) {
        repository.setHapticDurationMs(value)
    }

    fun startRecordingKey() {
        HardwareKeyTriggerCoordinator.startRecordingKey()
    }

    fun cancelRecordingKey() {
        HardwareKeyTriggerCoordinator.cancelRecordingKey()
    }

    fun clearHistory() {
        _keyHistory.value = emptyList()
    }

    // Wizard Controls
    fun openSetupWizard(step: Int = 0) {
        _wizardStep.value = step
        _showSetupWizard.value = true
        wizardPrefs.edit()
            .putBoolean("wizard_is_open", true)
            .putInt("wizard_current_step", step)
            .apply()
        if (step >= 1) {
            WirelessAdbManager.startSearchingMdns()
        }
    }

    fun closeSetupWizard() {
        _showSetupWizard.value = false
        wizardPrefs.edit().putBoolean("wizard_is_open", false).apply()
    }

    fun setWizardStep(step: Int) {
        _wizardStep.value = step
        wizardPrefs.edit().putInt("wizard_current_step", step).apply()
        if (step == 1) {
            WirelessAdbManager.startSearchingMdns()
        }
    }

    fun updateWizardManualIp(ip: String) {
        _wizardManualIp.value = ip
        wizardPrefs.edit().putString("wizard_manual_ip", ip).apply()
    }

    fun updateWizardManualPairingPort(port: String) {
        _wizardManualPairingPort.value = port
        wizardPrefs.edit().putString("wizard_manual_pairing_port", port).apply()
    }

    fun updateWizardManualPairingCode(code: String) {
        _wizardManualPairingCode.value = code
        wizardPrefs.edit().putString("wizard_manual_pairing_code", code).apply()
    }

    fun updateWizardManualConnectPort(port: String) {
        _wizardManualConnectPort.value = port
        wizardPrefs.edit().putString("wizard_manual_connect_port", port).apply()
    }

    fun cycleThemeMode() {
        setThemeMode(_themeMode.value.next())
    }

    fun setThemeMode(mode: com.example.ui.theme.ThemeMode) {
        _themeMode.value = mode
        appearancePrefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setAutoAssistantEnabled(enabled: Boolean) {
        WirelessAdbManager.setAutoAssistantEnabled(enabled)
    }

    fun startPairing(ip: String, pairingPort: Int, connectPort: Int, pairingCode: String) {
        WirelessAdbManager.startPairAndConnect(ip, pairingPort, connectPort, pairingCode)
    }

    fun toggleAdvancedMode(enable: Boolean) {
        if (!enable) {
            WirelessAdbManager.stopAdvancedMode()
            _hijackBannerStage.value = HijackBannerStage.HIDDEN
            return
        }
        viewModelScope.launch {
            // A bridge from an earlier app lifetime may already be running; adopt it rather than
            // spawning a second one or sending the user through the wizard again.
            if (BridgeController.detectExistingBridge()) return@launch

            val savedPort = discoveredConnectPort.value
            if (savedPort != null && savedPort > 0) {
                WirelessAdbManager.connectShellStream(discoveredIp.value, savedPort)
            } else {
                openSetupWizard(0)
            }
        }
    }

    fun dismissHijackBanner() {
        bannerJob?.cancel()
        _hijackBannerStage.value = HijackBannerStage.SETTLED_AT_TOP
    }

    /**
     * Removes everything this app stores: scripts, saved keys, preferences, the ADB key pair and
     * the bridge payload.
     *
     * The bridge is stopped first, because only the shell-UID process can clear its own
     * /data/local/tmp directory — once the app's data is gone it can no longer ask. The wipe
     * itself is delegated to the platform rather than enumerating preference files, so nothing
     * can be missed and no in-memory singleton survives holding stale state: the framework kills
     * the process as part of the call.
     */
    fun eraseAllData() {
        viewModelScope.launch {
            BridgeController.stop()
            WirelessAdbManager.stopAdvancedMode()
            appContext.getSystemService(android.app.ActivityManager::class.java)
                ?.clearApplicationUserData()
        }
    }

    private fun startAdvancedActivationAnimation() {
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            _hijackBannerStage.value = HijackBannerStage.BOTTOM_EXPANDED
            delay(4000L)
            _hijackBannerStage.value = HijackBannerStage.ANIMATING_TO_TOP
            delay(500L)
            _hijackBannerStage.value = HijackBannerStage.SETTLED_AT_TOP
        }
    }

    // System Intents
    fun openDeveloperSettings(context: Context) {
        WirelessAdbManager.startSearchingMdns()
        WirelessAdbManager.showPairingNotification(context)
        try {
            val intent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    /** The "Digital assistant app" picker, so a Launch voice/device assistant action can be pointed at one. */
    fun openAssistantSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /** cycle_ringer_mode / change_ringer_mode's "Tap to grant" badge; DND access has no runtime
     *  permission dialog, only this Settings screen. */
    fun openNotificationPolicySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(":settings:fragment_args_key", ComponentName(context, KeyMapperAccessibilityService::class.java).flattenToString())
                putExtra(":settings:show_fragment_args", android.os.Bundle().apply {
                    putString(":settings:fragment_args_key", ComponentName(context, KeyMapperAccessibilityService::class.java).flattenToString())
                })
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    private fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        val expectedComponentName = ComponentName(context, serviceClass)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
