package com.example.service.bridge

import android.content.Context
import android.util.Log
import com.example.bridge.BridgeProtocol
import com.example.data.KeyMappingRepository
import com.example.data.ScriptAction
import com.example.data.ScriptRepository
import com.example.service.HardwareKeyTriggerCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Lifecycle of the detached privileged bridge, as the app understands it. */
enum class BridgeState {
    /** No bridge is answering the control port. */
    STOPPED,

    /** Payload written and spawn issued; waiting for the bridge to bind its port. */
    STARTING,

    /** Handshake succeeded: a privileged process is alive and owns key detection. */
    RUNNING,

    /** The last start attempt failed. */
    ERROR,
}

/**
 * Single owner of the System Bridge.
 *
 * Advanced Mode is defined as "a detached, shell-UID bridge answers the control port". The app
 * only installs the payload, asks an authorized ADB shell to spawn it, and then observes it.
 * The bridge does its own key detection and action execution, so it keeps working after the app
 * process is killed — which is the entire point of moving it out of the app.
 */
object BridgeController {

    private const val TAG = "BridgeController"

    /** How the caller runs a one-shot command on an authorized ADB shell. */
    fun interface ShellCommandRunner {
        suspend fun run(command: String, onLine: (String) -> Unit): Boolean
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(BridgeState.STOPPED)
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    private val _statusMessage = MutableStateFlow("Advanced Mode is off.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _bridgeInfo = MutableStateFlow<BridgeStatus?>(null)
    val bridgeInfo: StateFlow<BridgeStatus?> = _bridgeInfo.asStateFlow()

    private val _inputDevices = MutableStateFlow<Map<String, String>>(emptyMap())
    val inputDevices: StateFlow<Map<String, String>> = _inputDevices.asStateFlow()

    /** Most recent kernel key transition seen by the bridge, for the setup wizard diagnostics. */
    private val _lastKernelActivity = MutableStateFlow<String?>(null)
    val lastKernelActivity: StateFlow<String?> = _lastKernelActivity.asStateFlow()

    private var appContext: Context? = null
    private var payload: BridgePayload? = null
    private var streamJob: Job? = null
    private var watchdogJob: Job? = null

    private val telemetryListener = object : BridgeClient.Listener {
        override fun onKeyEvent(event: BridgeKeyEvent) {
            _lastKernelActivity.value =
                "${event.source} ${if (event.isDown) "DOWN" else "UP"} code=${event.keyCode}"
            // Routed through the coordinator so cross-source deduplication, key recording and
            // the event history all keep working. Local execution stays suppressed while the
            // bridge is running, so this cannot double-fire an action.
            HardwareKeyTriggerCoordinator.dispatchKeyEvent(
                keyCode = event.keyCode,
                isDown = event.isDown,
                source = event.source,
            )
        }

        override fun onTrigger(trigger: BridgeTrigger) {
            HardwareKeyTriggerCoordinator.onBridgeTrigger(
                keyCode = trigger.keyCode,
                pressType = trigger.pressType,
                scriptId = trigger.scriptId,
                success = trigger.success,
            )
        }

        override fun onDevice(path: String, name: String) {
            _inputDevices.value = _inputDevices.value + (path to name)
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        payload = BridgePayload(context)
        // A bridge from a previous app lifetime may still be running. Adopt it instead of
        // relaunching: a second instance would compete for the same gestures.
        scope.launch { detectExistingBridge() }
    }

    private fun client(): BridgeClient? {
        val current = payload ?: return null
        return BridgeClient(port = current.controlPort, token = current.controlToken)
    }

    /**
     * Probes the control port, but replaces a bridge whose protocol version is stale instead of
     * adopting it. An app update can change bridge behavior (e.g. which actions it executes)
     * without touching the socket handshake, but the previous process — spawned by an older
     * app version — keeps running and answering the port until something kills it. Adopting it
     * silently would resurrect old, already-fixed bugs.
     */
    private suspend fun freshBridgeStatus(): BridgeStatus? {
        val status = client()?.probe() ?: return null
        if (status.protocolVersion == BridgeProtocol.VERSION) return status
        Log.i(
            TAG,
            "Replacing stale bridge (protocol v${status.protocolVersion}, " +
                "app expects v${BridgeProtocol.VERSION})",
        )
        client()?.requestShutdown()
        repeat(6) {
            delay(500)
            if (client()?.probe() == null) return null
        }
        Log.w(TAG, "Stale bridge did not shut down; leaving it running")
        return status
    }

    /** Adopts an already-running bridge without restarting it, unless its payload is stale. */
    suspend fun detectExistingBridge(): Boolean {
        val status = freshBridgeStatus()
        return if (status != null) {
            Log.i(TAG, "Adopted running bridge pid=${status.pid} started=${status.startedAtMillis}")
            onBridgeUp(status, "Advanced Mode is already running (pid ${status.pid}).")
            true
        } else {
            if (_state.value != BridgeState.STARTING) {
                setStopped("Advanced Mode is off.")
            }
            false
        }
    }

    /**
     * Installs the payload and spawns the bridge over an authorized shell.
     *
     * Publishes the config before spawning so the bridge has a valid mapping the moment it
     * starts, and never spawns when one is already running.
     */
    suspend fun start(runner: ShellCommandRunner): Boolean {
        val current = payload ?: return false
        if (detectExistingBridge()) {
            return true
        }
        _state.value = BridgeState.STARTING
        _statusMessage.value = "Loading bridge..."

        if (!current.install()) {
            setError("Could not write bridge files (external storage unreachable).")
            return false
        }
        publishConfig()

        val command = current.spawnCommand()
        if (command == null) {
            setError("Could not build the bridge startup script.")
            return false
        }

        _statusMessage.value = "Starting bridge as a detached process..."
        var sawMarker = false
        val ran = runner.run(command) { line ->
            if (line.contains(com.example.bridge.BridgeProtocol.SPAWN_MARKER)) {
                sawMarker = true
            }
            Log.d(TAG, "spawn: $line")
        }
        if (!ran) {
            setError("ADB shell could not start the bridge.")
            return false
        }
        Log.d(TAG, "Spawn command completed (marker seen=$sawMarker)")

        // The bridge binds its port a moment after app_process starts; poll rather than guess.
        repeat(20) {
            val status = client()?.probe()
            if (status != null) {
                onBridgeUp(status, "Advanced Mode active (bridge pid ${status.pid}).")
                return true
            }
            delay(500)
        }
        setError("Bridge started but the control port didn't respond. Log: ${com.example.bridge.BridgeProtocol.BASE_DIR}/bridge.log")
        return false
    }

    /**
     * Terminates the bridge and removes its payload. Advanced Mode is off only once the port stops
     * answering; the bridge clears its own shell-owned directory as it exits, and the app clears
     * the files it owns, so "off" means nothing runnable is left on the device.
     */
    suspend fun stop(): Boolean {
        val requested = client()?.requestShutdown() ?: false
        streamJob?.cancel()
        streamJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        HardwareKeyTriggerCoordinator.setAdvancedBridgeActive(false)
        payload?.uninstall()
        setStopped(if (requested) "Advanced Mode stopped." else "No running bridge found.")
        return requested
    }

    /** Rewrites the bridge's config from the current repositories and asks it to reload. */
    fun publishConfig() {
        val context = appContext ?: return
        val current = payload ?: return
        val scriptRepository = ScriptRepository.getInstance(context)
        val keyRepository = KeyMappingRepository.getInstance(context)
        val written = current.writeConfig(
            scripts = scriptRepository.scripts.value,
            globallyEnabled = scriptRepository.globallyEnabled.value,
            config = keyRepository.config.value,
        )
        if (!written) {
            Log.w(TAG, "Bridge config could not be written")
            return
        }
        if (_state.value == BridgeState.RUNNING) {
            scope.launch { client()?.requestReload() }
        }
    }

    /**
     * Runs one chain in the bridge, for the editor's Run button. Null when the bridge is not the
     * one executing right now, which leaves the caller on its own app-process path.
     */
    suspend fun runActions(actions: List<ScriptAction>): Pair<Int, Int>? {
        if (_state.value != BridgeState.RUNNING) return null
        val payload = JSONObject().put(
            "actions",
            JSONArray().apply {
                actions.forEach { action ->
                    val params = JSONObject()
                    action.params.forEach { (key, value) -> params.put(key, value) }
                    put(JSONObject().put("type", action.type).put("params", params))
                }
            },
        )
        return client()?.requestRun(payload.toString())
    }

    private fun onBridgeUp(status: BridgeStatus, message: String) {
        _bridgeInfo.value = status
        _state.value = BridgeState.RUNNING
        _statusMessage.value = message
        // The privileged bridge is authoritative while it runs; the in-app pipeline must not
        // execute the same script a second time.
        HardwareKeyTriggerCoordinator.setAdvancedBridgeActive(true)
        publishConfig()
        startStreaming()
        startWatchdog()
    }

    private fun startStreaming() {
        streamJob?.cancel()
        streamJob = scope.launch {
            while (isActive) {
                val result = client()?.stream(telemetryListener)
                if (!isActive) return@launch
                if (result == null) {
                    // Could not reconnect; the watchdog decides whether the bridge is really gone.
                    delay(2_000)
                } else {
                    delay(500)
                }
            }
        }
    }

    /**
     * Detects a bridge that died on its own (crash, reboot, manual kill) so the UI cannot keep
     * claiming Advanced Mode is active. Probing the port is the only reliable signal, since the app
     * has no parent/child relationship with the process.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(5_000)
                if (_state.value != BridgeState.RUNNING) continue
                if (client()?.probe() == null) {
                    // One retry: a probe can lose a race against a busy accept loop.
                    delay(1_500)
                    if (client()?.probe() == null) {
                        Log.w(TAG, "Bridge stopped answering; marking Advanced Mode inactive")
                        HardwareKeyTriggerCoordinator.setAdvancedBridgeActive(false)
                        setStopped("Bridge terminated. Advanced Mode is off.")
                    }
                }
            }
        }
    }

    private fun setStopped(message: String) {
        _state.value = BridgeState.STOPPED
        _statusMessage.value = message
        _bridgeInfo.value = null
    }

    private fun setError(message: String) {
        Log.w(TAG, message)
        _state.value = BridgeState.ERROR
        _statusMessage.value = message
        _bridgeInfo.value = null
        HardwareKeyTriggerCoordinator.setAdvancedBridgeActive(false)
    }
}
