package com.example.service

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.example.data.EntitlementManager
import com.example.data.KeyMappingConfig
import com.example.data.KeyMappingRepository
import com.example.data.LiveKeyEvent
import com.example.data.RunEnvironment
import com.example.data.RunLogRepository
import com.example.data.RunRecord
import com.example.data.ScriptAction
import com.example.data.ScriptActionCatalog
import com.example.data.ScriptConstraintCatalog
import com.example.data.ScriptRepository
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import com.example.data.buildRunRecord
import com.example.data.keyDisplayName
import com.example.data.keyId
import com.example.data.resolveHapticDurationForScript
import com.example.data.resolveTimingForKey
import com.example.data.resolveVibrateForScript
import com.example.service.bridge.BridgeController
import com.example.service.bridge.BridgeRunResult
import io.github.nicholasarda.keysnap.R
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Unified event pipeline for Activity, Accessibility, and Advanced Mode input sources.
 * Press timing is supplied by the persisted global configuration.
 */
object HardwareKeyTriggerCoordinator {
    private const val TAG = "KeyTriggerCoordinator"

    private val coordinatorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val stateMutex = Mutex()

    private var appContext: Context? = null
    private var repository: KeyMappingRepository? = null
    private var scriptRepository: ScriptRepository? = null
    private var actionExecutor: ScriptActionExecutor? = null
    private var constraintEvaluator: ConstraintEvaluator? = null

    // Live Telemetry Streams
    private val _latestKeyEvent = MutableSharedFlow<LiveKeyEvent>(extraBufferCapacity = 64)
    val latestKeyEvent: SharedFlow<LiveKeyEvent> = _latestKeyEvent.asSharedFlow()

    private val _triggerCount = MutableStateFlow(0)
    val triggerCount: StateFlow<Int> = _triggerCount.asStateFlow()

    private val _lastCapturedSource = MutableStateFlow("")
    val lastCapturedSource: StateFlow<String> = _lastCapturedSource.asStateFlow()

    private val _isRecordingKey = MutableStateFlow(false)
    val isRecordingKey: StateFlow<Boolean> = _isRecordingKey.asStateFlow()

    /**
     * True while the detached privileged bridge is running. The bridge then owns execution
     * outright: it does its own press timing, runs every action it supports, and reports the
     * match back through [onBridgeTrigger], which runs the rest (see
     * [ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE]) in this process. The key path stops executing
     * so nothing runs twice — the bridge's report is the only driver, which is what makes a
     * script work on a key Accessibility never sees (power, assistant, restricted OEM buttons).
     * Telemetry, deduplication and key recording stay active either way.
     */
    private val _isAdvancedBridgeActive = MutableStateFlow(false)
    val isAdvancedBridgeActive: StateFlow<Boolean> = _isAdvancedBridgeActive.asStateFlow()

    private val _recordedKey = MutableSharedFlow<List<Int>>(extraBufferCapacity = 1)
    val recordedKey: SharedFlow<List<Int>> = _recordedKey.asSharedFlow()

    /** Keys held during recording; the set is emitted when the first of them is released. */
    private val recordingHeld = linkedSetOf<Int>()

    // Deduplication & Multi-source Synchronization
    private var lastEventKeyCode: Int = 0
    private var lastEventIsDown: Boolean = false
    private var lastEventTimestamp: Long = 0
    private const val DEDUPLICATION_WINDOW_MS = 80L

    // Last known "all conditions true" value per key-less script; a script fires on the
    // false -> true edge only, so a duplicate broadcast for the same state change is harmless.
    private val automationEdges = AutomationEdges()
    private var automationWatch: Job? = null

    /** False until [initialize] has run in this process; nothing can fire before that. */
    val isReady: Boolean get() = constraintEvaluator != null

    // ponytail: in memory, so a restart of this process forgets it and one early re-run is
    // possible; persist per script id if users report it.
    /** Script id -> elapsedRealtime of its last run, for the cooldown condition. */
    private val lastRunAt = ConcurrentHashMap<String, Long>()

    /** Mirrors BridgeMain.cooldownAllows(Script, long) in :bridge. */
    private fun passesCooldown(script: ShortcutScript, now: Long): Boolean =
        script.constraints.filter { it.type == ScriptConstraintCatalog.COOLDOWN }
            .all { cooldownAllows(lastRunAt[script.id], it.params["minutes"], now) }

    // Press Timing & Debounce Configuration
    private var keyGestureFeed: KeyGestureFeed? = null

    fun initialize(context: Context, keyRepository: KeyMappingRepository) {
        keyGestureFeed?.reset()
        appContext = context.applicationContext
        repository = keyRepository
        scriptRepository = ScriptRepository.getInstance(context)
        actionExecutor = ScriptActionExecutor.android(context)
        constraintEvaluator = AndroidConstraintEvaluator(context.applicationContext)
        keyGestureFeed = KeyGestureFeed(coordinatorScope) { keyId, pressType ->
            handleGesture(keyId, pressType)
        }
        FlashlightController.initialize(context.applicationContext)
        automationWatch?.cancel()
        automationWatch = watchAutomationSources()
        Log.d(TAG, "Coordinator initialized: scripts=${scriptRepository?.scripts?.value?.size}")
    }

    /**
     * Editing a script must not fire it: whenever the script list changes, forget every edge and
     * re-seed silently, so a new automation whose state is already true waits for the next real
     * change. The torch has no broadcast, so its state flow is the wake-up for flashlight triggers.
     * Each change also re-arms the automation-only conditions' wake-ups.
     */
    private fun watchAutomationSources(): Job = coordinatorScope.launch {
        launch {
            scriptRepository?.scripts?.collect { scripts ->
                appContext?.let {
                    RssPolling.sync(it, scripts)
                    CalendarCondition.sync(it, scripts)
                    TimeSchedule.sync(it, scripts)
                }
                automationEdges.reset()
                reevaluateAutomations()
            }
        }
        launch { FlashlightController.isFlashlightOn.collect { reevaluateAutomations() } }
        // Pausing everything drops the time alarm (and its alarm-clock icon); resuming re-arms it.
        launch {
            scriptRepository?.globallyEnabled?.collect {
                appContext?.let { TimeSchedule.sync(it, scriptRepository?.scripts?.value.orEmpty()) }
            }
        }
    }

    fun setAdvancedBridgeActive(active: Boolean) {
        if (_isAdvancedBridgeActive.value == active) return
        _isAdvancedBridgeActive.value = active
        // Drop any half-resolved gesture so a press that started under one owner cannot be
        // completed by the other.
        keyGestureFeed?.reset()
        Log.d(TAG, "Advanced bridge ${if (active) "active: bridge owns execution" else "inactive: app owns execution"}")
    }

    /**
     * Reports a script the detached bridge matched, and runs the part of it the bridge cannot
     * (see [ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE]). This report — not the Accessibility key
     * event — is what drives in-app execution while the bridge is up, so those actions also fire
     * on keys the Accessibility service never receives.
     *
     * [success] describes the bridge's own execution, so it gates only the counter and the
     * haptic; the app's share runs either way.
     *
     * [actionResults] is the bridge's own per-action report, merged into the run log by [buildRunRecord].
     */
    fun onBridgeTrigger(
        keyCode: Int,
        pressType: String,
        scriptId: String,
        success: Boolean,
        actionResults: List<Pair<String, Boolean>>? = null,
    ) {
        lastRunAt[scriptId] = SystemClock.elapsedRealtime()
        if (success) {
            _triggerCount.value = _triggerCount.value + 1
        }
        val context = appContext
        val config = repository?.config?.value
        val script = scriptRepository?.scripts?.value?.firstOrNull { it.id == scriptId }
        if (script != null) {
            // Runs the app's share (often nothing: executeEach calls no handler for the bridge's
            // actions) and records the whole run, the bridge's part included.
            coordinatorScope.launch {
                val appResults = actionExecutor?.executeAppShare(script.actions) ?: return@launch
                recordRun(script, keyCode, pressType, appResults, actionResults)
            }
        }
        if (success && context != null && config != null) {
            val shouldVibrate = script?.let { resolveVibrateForScript(it, config.vibrateOnTrigger) } ?: config.vibrateOnTrigger
            if (shouldVibrate) {
                val duration = script?.let { resolveHapticDurationForScript(it, config.hapticDurationMs) } ?: config.hapticDurationMs
                vibratePhone(context, duration)
            }
        }
        Log.d(TAG, "Bridge tetikledi: script=$scriptId key=$keyCode press=$pressType success=$success")
    }

    fun startRecordingKey() {
        synchronized(recordingHeld) { recordingHeld.clear() }
        _isRecordingKey.value = true
    }

    fun cancelRecordingKey() {
        synchronized(recordingHeld) { recordingHeld.clear() }
        _isRecordingKey.value = false
    }

    /**
     * Dispatches a key event from any subsystem (Accessibility Service, Linux getevent, or Activity).
     */
    fun dispatchKeyEvent(keyCode: Int, isDown: Boolean, source: String): Boolean {
        val now = System.currentTimeMillis()
        val repo = repository ?: return false
        val scriptsRepo = scriptRepository ?: return false
        val ctx = appContext ?: return false
        val config = repo.config.value

        // 1. Deduplicate multi-source arrivals (e.g. Accessibility + getevent arriving concurrently)
        if (keyCode == lastEventKeyCode && isDown == lastEventIsDown && (now - lastEventTimestamp) < DEDUPLICATION_WINDOW_MS) {
            return false
        }
        lastEventKeyCode = keyCode
        lastEventIsDown = isDown
        lastEventTimestamp = now
        _lastCapturedSource.value = source

        val actionName = ctx.getString(if (isDown) R.string.event_key_down else R.string.event_key_up)
        val keyName = keyDisplayName(ctx.resources, keyCode)

        // 2. Key Recording Mode: every key held together is recorded, saved on the first release.
        if (_isRecordingKey.value) {
            // Main (Accessibility/Activity) and the BridgeClient IO thread (bridge echo) both land here.
            val released = synchronized(recordingHeld) {
                if (isDown) {
                    recordingHeld += keyCode
                    false
                } else if (keyCode in recordingHeld) {
                    _recordedKey.tryEmit(recordingHeld.sorted())
                    recordingHeld.clear()
                    true
                } else {
                    return false
                }
            }
            if (released) {
                _isRecordingKey.value = false
                vibratePhone(ctx, 40)
                Log.d(TAG, "New target key recorded: $keyName ($keyCode) Kaynak: $source")
            }
            _latestKeyEvent.tryEmit(
                LiveKeyEvent(keyCode = keyCode, keyName = keyName, actionName = actionName, isTriggerMatch = true, timestamp = now),
            )
            return true
        }

        var isMatched = false

        // 3. Timing & Debounce Algorithm for Target Key
        val matchingScripts = if (scriptsRepo.globallyEnabled.value) {
            scriptsRepo.scripts.value.filter { script ->
                script.enabled && script.triggers.any { keyCode in it.keyCodes }
            }
        } else emptyList()
        if (matchingScripts.isNotEmpty()) {
            isMatched = true
            // Scheduling always runs, even while the privileged bridge is active: it resolves the
            // same deterministic chord tracker and single/double/long-press state machine the
            // bridge runs on its own getevent stream, using the same thresholds, so both land on
            // the same press type for the same physical press. Execution itself (handleGesture) is
            // what's split so neither side repeats the other's actions.
            val scripts = scriptsRepo.scripts.value
            coordinatorScope.launch {
                stateMutex.withLock {
                    keyGestureFeed?.onEvent(keyCode, isDown, now, scripts, config)
                }
            }
        } else {
            Log.d(TAG, "No key match: event=$keyCode scripts=${scriptsRepo.scripts.value.size} global=${scriptsRepo.globallyEnabled.value} source=$source")
        }

        // 4. Emit live telemetry
        val liveEvent = LiveKeyEvent(
            keyCode = keyCode,
            keyName = "$keyName [$source]",
            actionName = actionName,
            isTriggerMatch = isMatched,
            timestamp = now,
        )
        _latestKeyEvent.tryEmit(liveEvent)

        return isMatched
    }

    private fun handleGesture(keyId: Int, pressType: TriggerPressType) {
        val scriptsRepo = scriptRepository ?: return
        val context = appContext ?: return
        if (!scriptsRepo.globallyEnabled.value) return

        val matched = scriptsRepo.scripts.value.filter { script ->
            script.enabled && script.triggers.any { trigger ->
                trigger.keyId() == keyId && trigger.pressType == pressType
            }
        }.filter { script ->
            val evaluator = constraintEvaluator
            script.constraints.isEmpty() || evaluator == null || script.constraints.all(evaluator::isSatisfied)
        }.filter { passesCooldown(it, SystemClock.elapsedRealtime()) }
        if (matched.isEmpty()) return

        val defaults = repository?.config?.value ?: return
        val config = resolveTimingForKey(scriptsRepo.scripts.value, keyId, defaults)
        val label = when (pressType) {
            TriggerPressType.SINGLE_PRESS -> context.getString(R.string.trigger_label_single, config.doublePressWindowMs)
            TriggerPressType.DOUBLE_PRESS -> context.getString(R.string.trigger_label_double, config.doublePressWindowMs)
            TriggerPressType.LONG_PRESS -> context.getString(R.string.trigger_label_long, config.longPressThresholdMs)
        }
        // The bridge matches this same physical press on its own getevent stream and reports it
        // back through onBridgeTrigger, which runs the app's share. Executing here as well would
        // run everything twice, so while the bridge is up this path only reports.
        if (_isAdvancedBridgeActive.value) {
            Log.d(TAG, "$label detected, bridge owns execution")
            return
        }
        Log.d(TAG, "$label detected and fired")
        executeActions(context, config, matched, label, keyId, pressType)
    }

    /**
     * Re-reads every automation script's constraints and fires the scripts whose conjunction just
     * turned true. Called on every device-state broadcast `DeviceEventReceiver` listens for; cheap
     * enough that no mapping from state to script is kept. A disabled script or a global pause
     * still tracks the edge, so un-pausing never replays it. No constraints means never.
     */
    fun reevaluateAutomations() {
        reevaluateAutomationsFired()
    }

    /** [reevaluateAutomations], true when a script fired. */
    fun reevaluateAutomationsFired(): Boolean {
        val scriptsRepo = scriptRepository ?: return false
        val context = appContext ?: return false
        val defaults = repository?.config?.value ?: return false
        val evaluator = constraintEvaluator ?: return false

        val now = SystemClock.elapsedRealtime()
        val fired = scriptsRepo.scripts.value.filter { script ->
            if (!script.automation) return@filter false
            val satisfied = script.constraints.isNotEmpty() && script.constraints.all(evaluator::isSatisfied)
            // update stays first, so the edge is recorded even when the cooldown skips this run.
            automationEdges.update(script.id, satisfied) && script.enabled && scriptsRepo.globallyEnabled.value &&
                passesCooldown(script, now)
        }
        if (fired.isEmpty()) return false
        Log.d(TAG, "Automation fired: scripts=${fired.map { it.id }}")
        executeActions(context, defaults, fired, context.getString(R.string.trigger_kind_automation), keyCode = null, pressType = null)
        return true
    }

    /**
     * Main thread. For a wake-up that can start this process cold (a calendar alarm, the feed job):
     * sets the pipeline up the way a widget tap does, if nothing has yet.
     */
    fun ensureInitialized(context: Context) {
        if (!isReady) initialize(context.applicationContext, KeyMappingRepository.getInstance(context))
    }

    /**
     * Records the current state of every automation whose edge is not known yet, and never fires.
     * A wake-up calls it while its condition still reads as it was just before the change, so in a
     * fresh process (where a first sighting only records) the change itself becomes a false -> true
     * edge. A script whose edge is already tracked is left alone, so a change some other broadcast
     * already reported is never fired twice.
     */
    fun seedUnknownAutomations() {
        val scripts = scriptRepository?.scripts?.value ?: return
        val evaluator = constraintEvaluator ?: return
        scripts.filter { it.automation && !automationEdges.knows(it.id) }.forEach { script ->
            automationEdges.update(script.id, script.constraints.isNotEmpty() && script.constraints.all(evaluator::isSatisfied))
        }
    }

    /** For a condition that expires with no broadcast (a notification's window): re-check once it has. */
    fun reevaluateAutomationsAfter(delayMs: Long) {
        coordinatorScope.launch {
            delay(delayMs)
            reevaluateAutomations()
        }
    }

    /**
     * Runs [scriptId] now for a home-screen widget or a Quick Settings tile ([source] is
     * [RunRecord.SOURCE_WIDGET] or [RunRecord.SOURCE_TILE]), and hands the outcome to [onDone] on
     * the main thread. A tap has no press timing to resolve and no second source to deduplicate
     * against, but it keeps every other rule of a key press: [surfaceGate] for pauses, "Only if"
     * and the cooldown, the script's own haptic, the trigger counter, the run log, and the
     * bridge's ownership of execution through [runChain], exactly as an automation does. A
     * refused tap is not a run and is not logged.
     */
    fun runFromSurface(context: Context, scriptId: String, source: String, onDone: (SurfaceRun) -> Unit): Job =
        coordinatorScope.launch { onDone(runSurfaceScript(context.applicationContext, scriptId, source)) }

    private suspend fun runSurfaceScript(context: Context, scriptId: String, source: String): SurfaceRun {
        // A widget or tile can start this process cold, before the Accessibility service or the
        // app has set the pipeline up.
        if (appContext == null) initialize(context, KeyMappingRepository.getInstance(context))
        val scriptsRepo = scriptRepository ?: return SurfaceRun.Missing
        val executor = actionExecutor ?: return SurfaceRun.Missing
        val defaults = repository?.config?.value ?: return SurfaceRun.Missing
        // The free limit works by switching scripts off, and a lapsed reward window is otherwise
        // only applied when the app opens.
        EntitlementManager.getInstance(context).enforceLimit()
        val script = scriptsRepo.scripts.value.firstOrNull { it.id == scriptId } ?: return SurfaceRun.Missing
        val evaluator = constraintEvaluator
        val now = SystemClock.elapsedRealtime()
        surfaceGate(script, scriptsRepo.globallyEnabled.value, passesCooldown(script, now)) { candidate ->
            evaluator == null || candidate.constraints.all(evaluator::isSatisfied)
        }?.let { return it }
        lastRunAt[script.id] = now

        if (resolveVibrateForScript(script, defaults.vibrateOnTrigger)) {
            vibratePhone(context, resolveHapticDurationForScript(script, defaults.hapticDurationMs))
        }
        // Nothing may have adopted a running bridge in a cold process yet; the probe does socket and
        // config-file I/O, as it does off the main thread at app start.
        withContext(Dispatchers.IO) { BridgeController.ensureAdopted(context) }
        val run = runChain(script.actions, executor, runOnBridge = BridgeController::runActions)
        _triggerCount.value = _triggerCount.value + 1
        val record = recordRun(script, keyCode = null, pressType = null, run.appResults, run.bridgeReport, source)
        val ran = record?.actions?.count { it.ok } ?: 0
        Log.d(TAG, "Surface run: script=${script.id} source=$source ran=$ran/${script.actions.size} bridge=${run.bridgeReport != null}")
        return SurfaceRun.Ran(script.name, ran, script.actions.size)
    }

    private fun executeActions(
        context: Context,
        config: KeyMappingConfig,
        scripts: List<ShortcutScript>,
        triggerLabel: String,
        keyCode: Int?,
        pressType: TriggerPressType?,
    ) {
        // Marked before the launch, so a second trigger queued behind this one already sees it.
        val now = SystemClock.elapsedRealtime()
        scripts.forEach { lastRunAt[it.id] = now }
        // Read now, while the match is still inside its window: the notification a reply with no
        // chat named should answer. A key run has none.
        val triggerNotifications = if (keyCode == null) {
            scripts.associate { it.id to NotificationMatches.triggeringKey(it.constraints, now) }
        } else {
            emptyMap()
        }
        coordinatorScope.launch {
            scripts.forEach { script ->
                if (resolveVibrateForScript(script, config.vibrateOnTrigger)) {
                    vibratePhone(context, resolveHapticDurationForScript(script, config.hapticDurationMs))
                }
                val executor = actionExecutor
                if (executor != null) {
                    // An automation has no press for the bridge to match, so it is handed the chain
                    // here. A key run reaches this only while the bridge is down (handleGesture),
                    // and must not ask it: a bridge coming up meanwhile has matched the press itself.
                    val run = if (keyCode == null) {
                        runChain(script.actions, executor, triggerNotifications[script.id], BridgeController::runActions)
                    } else {
                        ChainResults(executor.executeEach(script.actions), bridgeReport = null)
                    }
                    recordRun(script, keyCode, pressType?.name, run.appResults, run.bridgeReport)
                }
                _triggerCount.value = _triggerCount.value + 1
                Log.d(TAG, "Trigger fired: script=${script.id}, action=$triggerLabel, total=${_triggerCount.value}")
            }
        }
    }

    /** One entry in the shortcut's run log. Action type ids and outcomes only; see [RunRecord]. */
    private fun recordRun(
        script: ShortcutScript,
        keyCode: Int?,
        pressType: String?,
        appResults: List<Boolean?>,
        bridgeReport: List<Pair<String, Boolean>>?,
        source: String = if (keyCode != null) RunRecord.SOURCE_KEY else RunRecord.SOURCE_AUTOMATION,
    ): RunRecord? {
        val context = appContext ?: return null
        val record = buildRunRecord(
            scriptId = script.id,
            at = System.currentTimeMillis(),
            source = source,
            keyCode = keyCode,
            pressType = pressType,
            actions = script.actions,
            appResults = appResults,
            bridgeReport = bridgeReport,
            env = runEnvironment(context),
        )
        RunLogRepository.getInstance(context).append(record)
        RunFailureNotifier.onRun(context, script, record)
        Log.d(TAG, "Run recorded: script=${script.id} failed=${record.actions.filterNot { it.ok }.map { it.type }}")
        return record
    }

    private fun runEnvironment(context: Context) = RunEnvironment(
        accessibility = KeyMapperAccessibilityService.isServiceActive.value,
        dndAccess = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true,
        writeSettings = Settings.System.canWrite(context),
        advancedMode = _isAdvancedBridgeActive.value,
        notifications = context.getSystemService(NotificationManager::class.java)?.let(::shortcutNotificationsAllowed) == true,
        notificationAccess = NotificationEventListener.isAccessGranted(context),
        autoSendOutcome = ScriptActionExecutor.lastAutoSendOutcome,
    )

    fun vibratePhone(context: Context, durationMs: Long = 50) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() != true) return

            vibrator.vibrate(VibrationEffect.createOneShot(durationMs.coerceAtLeast(1L), VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.e(TAG, "Vibration execution failed", e)
        }
    }
}

/** [appResults] is `executeEach`'s output (null = the bridge's action); [bridgeReport] the bridge's own. */
internal data class ChainResults(val appResults: List<Boolean?>, val bridgeReport: List<Pair<String, Boolean>>?)

/** The part of a bridge-run chain the app runs itself; see [ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE]. */
internal suspend fun ScriptActionExecutor.executeAppShare(actions: List<ScriptAction>, notificationKey: String? = null): List<Boolean?> =
    executeEach(actions, notificationKey) { it.type in ScriptActionCatalog.UNSUPPORTED_BY_BRIDGE }

/**
 * Runs a chain the way a key press would: [runOnBridge] runs what it supports, then the app runs
 * its share, as [HardwareKeyTriggerCoordinator.onBridgeTrigger] does. When [runOnBridge] returns
 * null (no bridge, or the request failed) the whole chain runs here, once. [notificationKey] is
 * the notification that started the run, if any; see [ScriptActionExecutor.executeEach].
 */
internal suspend fun runChain(
    actions: List<ScriptAction>,
    executor: ScriptActionExecutor,
    notificationKey: String? = null,
    runOnBridge: suspend (List<ScriptAction>) -> BridgeRunResult?,
): ChainResults {
    val bridge = runOnBridge(actions) ?: return ChainResults(executor.executeEach(actions, notificationKey), bridgeReport = null)
    return ChainResults(executor.executeAppShare(actions, notificationKey), bridge.actionResults)
}

/** Rising-edge detector keyed by script id. [update] is true only for a false -> true change; a first sighting only records. */
internal class AutomationEdges {
    private val last = mutableMapOf<String, Boolean>()

    fun update(id: String, satisfied: Boolean): Boolean {
        val was = last.put(id, satisfied)
        return satisfied && was == false
    }

    fun knows(id: String): Boolean = id in last

    fun reset() = last.clear()
}
