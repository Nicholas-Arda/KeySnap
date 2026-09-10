package com.example.service.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.RemoteInput
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.util.Log
import com.example.bridge.BridgeProtocol
import com.example.service.bridge.BridgeController
import com.example.service.bridge.BridgeState
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PairingStatus {
    IDLE,
    SEARCHING_MDNS_AND_SCREEN,
    CREDENTIALS_CAPTURED,
    PAIRING_IN_PROGRESS,
    PAIRED_SUCCESS,
    CONNECTING_STREAM,
    ADVANCED_MODE_RUNNING,
    ERROR
}

object WirelessAdbManager {
    private const val TAG = "WirelessAdbManager"
    const val PAIRING_CHANNEL_ID = "adb_pairing_urgent_v2"
    private const val PREFS_NAME = "arda_mapper_adb_prefs"
    private const val PREF_AUTO_ASSISTANT = "auto_setup_assistant_enabled"
    private const val PREF_SAVED_CONNECT_PORT = "saved_connect_port"
    private const val PREF_SAVED_IP = "saved_ip"
    private const val PREF_ADVANCED_ENABLED = "advanced_mode_enabled"

    /** Renamed from "Expert Mode" (a competitor's name for the same feature). Read as a fallback
     *  so a user who had it on before the rename doesn't see it come back off. */
    private const val PREF_ADVANCED_ENABLED_LEGACY = "expert_mode_enabled"

    /** Extra spawn attempts after a fresh mDNS re-browse when the resolved connect port refuses. */
    private const val MAX_CONNECT_RETRIES = 2

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Advanced Mode is owned by [BridgeController]: it is true exactly while a detached, shell-UID
     * bridge answers its control port. This manager no longer hosts key reading — it only pairs,
     * connects, and spawns that process.
     */
    val isAdvancedModeRunning: StateFlow<Boolean> = BridgeController.state
        .map { it == BridgeState.RUNNING }
        .stateIn(managerScope, SharingStarted.Eagerly, false)

    /**
     * True once the launch-time probe in [initialize] has resolved. Observers that only want to
     * animate a genuine off-to-on activation (not a bridge adopted from an earlier app lifetime)
     * should wait for this before reacting to [isAdvancedModeRunning].
     */
    private val _startupBridgeCheckComplete = MutableStateFlow(false)
    val startupBridgeCheckComplete: StateFlow<Boolean> = _startupBridgeCheckComplete.asStateFlow()

    private val _pairingStatus = MutableStateFlow(PairingStatus.IDLE)
    val pairingStatus: StateFlow<PairingStatus> = _pairingStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow<String>("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _autoAssistantEnabled = MutableStateFlow(true)
    val autoAssistantEnabled: StateFlow<Boolean> = _autoAssistantEnabled.asStateFlow()

    private val _discoveredIp = MutableStateFlow("127.0.0.1")
    val discoveredIp: StateFlow<String> = _discoveredIp.asStateFlow()

    private val _discoveredPairingPort = MutableStateFlow<Int?>(null)
    val discoveredPairingPort: StateFlow<Int?> = _discoveredPairingPort.asStateFlow()

    private val _discoveredPairingCode = MutableStateFlow<String?>(null)
    val discoveredPairingCode: StateFlow<String?> = _discoveredPairingCode.asStateFlow()

    private val _discoveredConnectPort = MutableStateFlow<Int?>(null)
    val discoveredConnectPort: StateFlow<Int?> = _discoveredConnectPort.asStateFlow()

    /** Live kernel activity, now reported by the detached bridge over the control socket. */
    val lastRawKernelLine: StateFlow<String?> = BridgeController.lastKernelActivity

    /** Input nodes reported by the detached bridge, keyed by /dev/input path. */
    val inputDevices: StateFlow<Map<String, String>> = BridgeController.inputDevices

    private val _pairingEvents = MutableSharedFlow<PairingStatus>(extraBufferCapacity = 2)
    val pairingEvents: SharedFlow<PairingStatus> = _pairingEvents.asSharedFlow()

    private var streamJob: Job? = null
    private var pairingClient: AdbPairingClient? = null
    private var streamClient: AdbStreamClient? = null
    private var appContext: Context? = null
    private var nsdManager: NsdManager? = null
    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var isNsdScanning = false
    private val flowLock = Any()
    private var pairingFlowJob: Job? = null
    private var pairingFlowGeneration = 0L
    private var activePairingCode: String? = null
    private var awaitingFreshPairingPort = false
    private var pairingAttemptInProgress = false
    private var activeStreamEndpoint: Pair<String, Int>? = null

    /**
     * Host this pairing/connect flow is locked to, so a second wireless-debugging-enabled device
     * (or a stale mDNS answer from a previous session) on the same LAN can never donate its
     * connect port to a different device's IP. Null until a host is known, at which point either
     * listener below locks it in from the first resolution it accepts.
     */
    @Volatile
    private var expectedPairingHost: String? = null

    @Volatile
    private var lastConnectServiceResolutionAtMillis = 0L

    /**
     * Every `_adb-tls-connect._tcp` port resolved for this device, in resolution order. The
     * daemon can have more than one live record at a time (a rebind after pairing leaves the
     * previous name cached, so the new one is advertised with a "… (2)" suffix), and only one of
     * them accepts a TLS connection. Which one that is cannot be told from the service name, so
     * the spawn loop tries them in turn instead of guessing. [triedConnectPorts] keeps a refused
     * port from being handed back by the next re-browse.
     */
    private val connectPortCandidates = LinkedHashSet<Int>()
    private val triedConnectPorts = mutableSetOf<Int>()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _autoAssistantEnabled.value = prefs.getBoolean(PREF_AUTO_ASSISTANT, true)
        val savedPort = prefs.getInt(PREF_SAVED_CONNECT_PORT, -1)
        if (savedPort > 0) _discoveredConnectPort.value = savedPort
        val savedIp = prefs.getString(PREF_SAVED_IP, "127.0.0.1") ?: "127.0.0.1"
        _discoveredIp.value = savedIp

        pairingClient = AdbPairingClient(context.applicationContext)
        streamClient = AdbStreamClient(context.applicationContext)
        ensurePairingNotificationChannel(context.applicationContext)

        // Deliberately does not re-spawn. A bridge started in an earlier app lifetime is still
        // running, and launching a second one would leave two readers competing for the same
        // gestures. BridgeController probes the control port and adopts whatever it finds; the
        // user re-runs the ADB flow only when nothing answers.
        BridgeController.initialize(context)
        managerScope.launch {
            if (BridgeController.detectExistingBridge()) {
                _pairingStatus.value = PairingStatus.ADVANCED_MODE_RUNNING
                _statusMessage.value = BridgeController.statusMessage.value
            } else if (prefs.getBoolean(PREF_ADVANCED_ENABLED, prefs.getBoolean(PREF_ADVANCED_ENABLED_LEGACY, false))) {
                _statusMessage.value =
                    "Advanced Mode is on but the bridge isn't running (normal after a reboot). " +
                        "Restart it from the setup wizard."
            }
            _startupBridgeCheckComplete.value = true
        }
    }

    fun setAutoAssistantEnabled(enabled: Boolean) {
        _autoAssistantEnabled.value = enabled
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(PREF_AUTO_ASSISTANT, enabled)?.apply()
    }

    /**
     * Called by KeyMapperAccessibilityService or UI when screen scanner or notification
     * detects pairing credentials from Developer Options Wireless Debugging dialog.
     */
    fun onCredentialsDetectedFromScreen(ip: String?, port: Int?, code: String?) {
        val normalizedCode = code?.takeIf { it.matches(Regex("\\d{6}")) } ?: return
        synchronized(flowLock) {
            if (pairingAttemptInProgress && normalizedCode == activePairingCode) {
                Log.d(TAG, "Ignoring duplicate accessibility credentials for active pairing flow")
                return
            }

            val normalizedIp = ip?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
            expectedPairingHost = normalizedIp
            if (normalizedIp != null) _discoveredIp.value = normalizedIp
            activePairingCode = normalizedCode
            _discoveredPairingCode.value = normalizedCode

            // A pairing dialog refresh invalidates its previous ephemeral port. The accessibility
            // hierarchy can briefly expose stale text, so only a subsequent mDNS resolution may
            // supply the port used by this new flow.
            _discoveredPairingPort.value = null
            awaitingFreshPairingPort = true
            pairingAttemptInProgress = true
            pairingFlowGeneration++
        }

        Log.d(TAG, "🎯 New pairing code captured; invalidated cached pairing port and waiting for mDNS")
        _pairingStatus.value = PairingStatus.CREDENTIALS_CAPTURED
        _statusMessage.value = "Pairing code detected; waiting for a fresh mDNS port."
        if (_autoAssistantEnabled.value) startMdnsDiscovery()
    }

    
    fun isSearchingMdnsAndScreen(): Boolean {
        return _pairingStatus.value == PairingStatus.SEARCHING_MDNS_AND_SCREEN
    }

    fun startSearchingMdns() {
        _pairingStatus.value = PairingStatus.SEARCHING_MDNS_AND_SCREEN
        _statusMessage.value = "Scanning for pairing code and port... (open Developer Options)"
        startMdnsDiscovery()
    }

    private fun startMdnsDiscovery() {
        if (isNsdScanning) return
        val ctx = appContext ?: return
        try {
            nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager
            val pairingListener = createNsdDiscoveryListener("_adb-tls-pairing._tcp") { serviceInfo ->
                val port = serviceInfo.port
                val host = serviceInfo.host?.hostAddress
                Log.d(TAG, "mDNS discovered a pairing service on port $port")
                val expected = expectedPairingHost
                if (host.isNullOrBlank() || (expected != null && host != expected)) {
                    if (!host.isNullOrBlank()) {
                        Log.d(TAG, "Ignoring a pairing service from an unexpected host")
                    }
                } else {
                    if (expected == null) expectedPairingHost = host
                    _discoveredIp.value = host
                    if (port > 0) {
                        val shouldStart = synchronized(flowLock) {
                            if (!awaitingFreshPairingPort || !pairingAttemptInProgress) {
                                false
                            } else {
                                awaitingFreshPairingPort = false
                                _discoveredPairingPort.value = port
                                true
                            }
                        }
                        if (shouldStart) maybeStartPairing()
                    }
                }
            }
            val connectListener = createNsdDiscoveryListener("_adb-tls-connect._tcp") { serviceInfo ->
                val port = serviceInfo.port
                val host = serviceInfo.host?.hostAddress
                Log.d(TAG, "mDNS resolved a connect service on port $port")
                val expected = expectedPairingHost

                if (expected != null && host != expected) {
                    // A second wireless-debugging-enabled device (or an emulator) on the same LAN
                    // advertises its own connect port; without this check its port would get
                    // paired with a different device's IP and every connection would refuse.
                    Log.d(TAG, "Ignoring a connect service from an unexpected host")
                } else if (port > 0) {
                    if (!host.isNullOrBlank()) _discoveredIp.value = host
                    val isFirst = synchronized(flowLock) {
                        connectPortCandidates.add(port) && connectPortCandidates.size == 1
                    }
                    lastConnectServiceResolutionAtMillis = android.os.SystemClock.elapsedRealtime()
                    if (isFirst) saveConnectPort(port)
                }
            }
            discoveryListeners += pairingListener
            discoveryListeners += connectListener
            nsdManager?.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, pairingListener)
            nsdManager?.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, connectListener)
            isNsdScanning = true
        } catch (e: Exception) {
            Log.w(TAG, "NsdManager discovery notice: ${e.message}")
        }
    }

    /** Re-query mDNS because ADB's TLS connect endpoint changes after pairing. */
    private fun refreshConnectServiceDiscovery() {
        val manager = nsdManager
        discoveryListeners.forEach { listener ->
            runCatching { manager?.stopServiceDiscovery(listener) }
        }
        discoveryListeners.clear()
        isNsdScanning = false
        _discoveredConnectPort.value = null
        synchronized(flowLock) { connectPortCandidates.clear() }
        lastConnectServiceResolutionAtMillis = 0L
        startMdnsDiscovery()
    }

    private fun createNsdDiscoveryListener(serviceType: String, onResolved: (NsdServiceInfo) -> Unit): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "NSD discovery started: $regType")
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                try {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.w(TAG, "NSD resolve failed for $serviceType: $errorCode")
                        }
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) = onResolved(serviceInfo)
                    })
                } catch (e: Exception) {
                    Log.w(TAG, "resolveService error", e)
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(type: String) {
                Log.d(TAG, "NSD discovery stopped: $type")
            }
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                Log.w(TAG, "NSD start failed for $type: $errorCode")
                runCatching { nsdManager?.stopServiceDiscovery(this) }
            }
            override fun onStopDiscoveryFailed(type: String, errorCode: Int) {
                Log.w(TAG, "NSD stop failed for $type: $errorCode")
            }
        }
    }

    fun startPairAndConnect(
        ip: String = "127.0.0.1",
        pairingPort: Int,
        @Suppress("UNUSED_PARAMETER") connectPort: Int,
        pairingCode: String,
    ) {
        val generation = synchronized(flowLock) {
            if (activePairingCode != pairingCode) activePairingCode = pairingCode
            pairingAttemptInProgress = true
            // A port that refused during an earlier flow may well be the live one now.
            triedConnectPorts.clear()
            pairingFlowGeneration
        }
        pairingFlowJob?.cancel()
        pairingFlowJob = managerScope.launch {
            _pairingStatus.value = PairingStatus.PAIRING_IN_PROGRESS
            _statusMessage.value = "Pairing with wireless ADB..."

            val client = pairingClient ?: AdbPairingClient(appContext ?: return@launch)
            when (val result = client.pair(ip, pairingPort, pairingCode)) {
                is AdbPairingClient.PairingResult.Success -> {
                    if (!isCurrentPairingFlow(generation, pairingCode)) return@launch
                    appContext?.let { context ->
                        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .cancel(PairingNotificationReceiver.NOTIFICATION_ID)
                    }
                    _pairingStatus.value = PairingStatus.PAIRED_SUCCESS
                    _statusMessage.value = result.message
                    saveIp(ip)

                    // The two endpoints advertised before pairing may already be stale.
                    // Start a new browse cycle and accept only a connection port resolved after it.
                    refreshConnectServiceDiscovery()
                    delay(600)
                    if (!isCurrentPairingFlow(generation, pairingCode)) return@launch
                    _pairingStatus.value = PairingStatus.CONNECTING_STREAM
                    _statusMessage.value = "Starting Advanced Mode (optimized getevent stream)..."
                    val finalConnectPort = awaitVerifiedConnectPort()
                    if (finalConnectPort == null) {
                        finishPairingFlow(generation, pairingCode)
                        _pairingStatus.value = PairingStatus.ERROR
                        _statusMessage.value = "ADB connect service not found. Keep Wireless Debugging on and try again."
                        appContext?.let {
                            showPairingNotification(it, it.getString(R.string.notif_pairing_no_port))
                        }
                        return@launch
                    }
                    connectShellStream(ip, finalConnectPort, generation, pairingCode)
                }

                is AdbPairingClient.PairingResult.Error -> {
                    finishPairingFlow(generation, pairingCode)
                    _pairingStatus.value = PairingStatus.ERROR
                    _statusMessage.value = result.error
                    appContext?.let {
                        showPairingNotification(it, it.getString(R.string.notif_pairing_failed))
                    }
                }
            }
        }
    }

    /**
     * Spawns the detached System Bridge over the authorized ADB shell.
     *
     * The ADB connection is only a launcher: once the bridge is running as its own session
     * leader, this stream is closed and the bridge keeps detecting keys and executing actions
     * even after the app process is killed. Nothing about Advanced Mode depends on this connection
     * staying open.
     */
    fun connectShellStream(
        ip: String = "127.0.0.1",
        port: Int,
        pairingGeneration: Long? = null,
        pairingCode: String? = null,
    ) {
        val endpoint = ip to port
        if (streamJob?.isActive == true && activeStreamEndpoint == endpoint) {
            Log.d(TAG, "Ignoring a duplicate bridge spawn request")
            return
        }

        streamJob?.cancel()
        activeStreamEndpoint = endpoint
        streamJob = managerScope.launch {
            val context = appContext ?: return@launch

            // Short-lived: it protects the setup coroutine while the app may be backgrounded.
            // It is not a keep-alive for Advanced Mode, which now outlives the app entirely.
            WirelessAdbHelperService.start(context)
            try {
                var currentIp = ip
                var currentPort = port
                var started = false
                var attempt = 0
                synchronized(flowLock) { triedConnectPorts.clear() }
                while (true) {
                    synchronized(flowLock) { triedConnectPorts.add(currentPort) }
                    saveConnectPort(currentPort)
                    _pairingStatus.value = PairingStatus.CONNECTING_STREAM
                    _statusMessage.value = "ADB connected; starting the detached bridge..."

                    val client = streamClient ?: AdbStreamClient(context)
                    started = BridgeController.start { command, onLine ->
                        client.runCommand(
                            host = currentIp,
                            connectPort = currentPort,
                            command = command,
                            onLine = onLine,
                            completeWhen = { line -> line.contains(BridgeProtocol.SPAWN_MARKER) },
                        )
                    }
                    if (started || attempt >= MAX_CONNECT_RETRIES) break

                    // A refused port does not mean Wireless Debugging is off: the daemon can
                    // advertise several `_adb-tls-connect._tcp` records and only one of them
                    // listens, and it also rebinds the TLS listener right around pairing. Try the
                    // other port already resolved for this device first, and only re-browse once
                    // every known candidate has refused.
                    Log.w(TAG, "Bridge spawn failed (attempt $attempt); trying the next resolved ADB port")
                    var nextPort = untriedConnectPort()
                    if (nextPort == null) {
                        refreshConnectServiceDiscovery()
                        nextPort = awaitVerifiedConnectPort()
                    }
                    currentPort = nextPort ?: break
                    attempt++
                }

                if (pairingGeneration != null && pairingCode != null) {
                    finishPairingFlow(pairingGeneration, pairingCode)
                }
                if (started) {
                    activeStreamEndpoint = currentIp to currentPort
                    _pairingStatus.value = PairingStatus.ADVANCED_MODE_RUNNING
                    _statusMessage.value = BridgeController.statusMessage.value
                    persistAdvancedModeWanted(true)
                } else {
                    activeStreamEndpoint = null
                    _pairingStatus.value = PairingStatus.ERROR
                    _statusMessage.value = BridgeController.statusMessage.value
                }
            } finally {
                // The ADB stream has done its only job; drop it so nothing ties the bridge to
                // this app process.
                streamClient?.stop()
                WirelessAdbHelperService.stop(context)
            }
        }
    }

    /**
     * The TLS connect port is ephemeral. Never reuse a persisted port after pairing;
     * wait for the current `_adb-tls-connect._tcp` mDNS advertisement instead.
     */
    private suspend fun awaitVerifiedConnectPort(): Int? {
        val deadline = android.os.SystemClock.elapsedRealtime() + 8_000L
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val age = android.os.SystemClock.elapsedRealtime() - lastConnectServiceResolutionAtMillis
            if (age in 0..15_000L) untriedConnectPort()?.let { return it }
            delay(200)
        }
        return null
    }

    /** The first freshly advertised connect port that has not already refused a connection. */
    private fun untriedConnectPort(): Int? = synchronized(flowLock) {
        connectPortCandidates.firstOrNull { it !in triedConnectPorts }
    }

    private fun isCurrentPairingFlow(generation: Long, code: String): Boolean =
        synchronized(flowLock) {
            pairingAttemptInProgress && pairingFlowGeneration == generation && activePairingCode == code
        }

    private fun finishPairingFlow(generation: Long, code: String) {
        synchronized(flowLock) {
            if (pairingFlowGeneration == generation && activePairingCode == code) {
                pairingAttemptInProgress = false
                awaitingFreshPairingPort = false
                activePairingCode = null
            }
        }
    }

    fun stopAdvancedMode() {
        streamJob?.cancel()
        streamJob = null
        streamClient?.stop()
        activeStreamEndpoint = null
        expectedPairingHost = null
        synchronized(flowLock) {
            pairingFlowGeneration++
            pairingFlowJob?.cancel()
            pairingFlowJob = null
            pairingAttemptInProgress = false
            awaitingFreshPairingPort = false
            activePairingCode = null
        }
        managerScope.launch {
            BridgeController.stop()
            persistAdvancedModeWanted(false)
            _pairingStatus.value = PairingStatus.IDLE
            _statusMessage.value = BridgeController.statusMessage.value
        }
    }

    /**
     * Records whether the user wants Advanced Mode, which is separate from whether it is running:
     * a bridge dies on reboot, so on the next launch the app probes for a live bridge first and
     * only re-runs the ADB flow when the user asks for it.
     */
    private fun persistAdvancedModeWanted(wanted: Boolean) {
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(PREF_ADVANCED_ENABLED, wanted)?.apply()
    }

    private fun saveConnectPort(port: Int) {
        _discoveredConnectPort.value = port
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putInt(PREF_SAVED_CONNECT_PORT, port)?.apply()
    }

    private fun saveIp(ip: String) {
        _discoveredIp.value = ip
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(PREF_SAVED_IP, ip)?.apply()
    }

    fun onCredentialsFromNotification(code: String, port: Int?) {
        if (!code.matches(Regex("\\d{6}"))) {
            _statusMessage.value = "Pairing code must be 6 digits."
            return
        }
        synchronized(flowLock) {
            if (pairingAttemptInProgress && code == activePairingCode) return
            expectedPairingHost = null
            activePairingCode = code
            _discoveredPairingCode.value = code
            _discoveredPairingPort.value = null
            awaitingFreshPairingPort = true
            pairingAttemptInProgress = true
            pairingFlowGeneration++
        }
        _pairingStatus.value = PairingStatus.CREDENTIALS_CAPTURED
        _statusMessage.value = "Pairing code received from notification; waiting for a fresh mDNS port."
        startMdnsDiscovery()
    }

    private fun maybeStartPairing() {
        val pairingPort: Int
        val code: String
        val generation: Long
        synchronized(flowLock) {
            pairingPort = _discoveredPairingPort.value ?: return
            code = activePairingCode ?: return
            if (awaitingFreshPairingPort) return
            generation = pairingFlowGeneration
        }
        if (pairingFlowJob?.isActive == true) return
        startPairAndConnect(
            ip = _discoveredIp.value,
            pairingPort = pairingPort,
            connectPort = _discoveredConnectPort.value ?: -1,
            pairingCode = code,
        )
        Log.d(TAG, "Started pairing flow $generation using fresh mDNS port $pairingPort")
    }

    fun ensurePairingNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            PAIRING_CHANNEL_ID,
            context.getString(R.string.notif_channel_pairing),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_pairing_description)
            enableVibration(true)
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showPairingNotification(context: Context, message: String? = null) {
        val body = message ?: context.getString(R.string.notif_pairing_default)
        ensurePairingNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val remoteInput = RemoteInput.Builder(PairingNotificationReceiver.KEY_PAIRING_INPUT)
            .setLabel(context.getString(R.string.notif_pairing_input_label))
            .build()
        val intent = Intent(context, PairingNotificationReceiver::class.java).apply {
            action = PairingNotificationReceiver.ACTION_SUBMIT_PAIRING_CODE
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            context.getString(R.string.notif_pairing_enter_code),
            pendingIntent
        ).addRemoteInput(remoteInput).build()
        val notification = NotificationCompat.Builder(context, PAIRING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notif_pairing_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .addAction(action)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        notificationManager.notify(PairingNotificationReceiver.NOTIFICATION_ID, notification)
    }

}