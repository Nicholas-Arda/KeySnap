package com.example.service

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wakes [HardwareKeyTriggerCoordinator.reevaluateAutomations] on every device-state change Android
 * announces, and keeps the Bluetooth ACL bookkeeping the Bluetooth-device constraints read. The set
 * of actions here is what makes a constraint eligible for [ScriptConstraintCatalog.AUTOMATION_TYPES].
 * Registered and unregistered by [KeyMapperAccessibilityService] for its lifetime — the app has
 * no other component that is guaranteed to be alive to receive these.
 */
class DeviceEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> onAcl(intent, isConnected = true)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> onAcl(intent, isConnected = false)
        }
        HardwareKeyTriggerCoordinator.reevaluateAutomations()
    }

    private fun onAcl(intent: Intent, isConnected: Boolean) {
        val device = bluetoothDeviceExtra(intent) ?: return
        val address = device.address ?: return
        if (isConnected) connected[address] = nameOf(device) else connected.remove(address)
    }

    private fun bluetoothDeviceExtra(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        private const val TAG = "DeviceEventReceiver"

        /**
         * Addresses of Bluetooth devices with an open ACL link, kept for the
         * `bluetooth_device_connected/disconnected` constraints. Fed by the ACL broadcasts above
         * and seeded once by [seedConnectedDevices] from the headset/A2DP profile proxies, so a
         * device that was already connected before the service started is still known. A
         * connected device on some other profile only (a wearable, say) stays unknown until its
         * next ACL event.
         */
        private val connected: MutableMap<String, String> = Collections.synchronizedMap(mutableMapOf())
        private val seeded = AtomicBoolean(false)

        /**
         * Matches on [address] when given, else on [name] case-insensitively. The bridge sees only
         * anonymized addresses in `dumpsys`, so it matches the same way; keep the two in step.
         */
        fun isBluetoothDeviceConnected(address: String?, name: String?): Boolean = when {
            !address.isNullOrBlank() -> address.trim() in connected
            !name.isNullOrBlank() -> synchronized(connected) { connected.values.any { it.equals(name.trim(), ignoreCase = true) } }
            else -> false
        }

        /** Needs BLUETOOTH_CONNECT; without it the name is unknown and only the address matches. */
        private fun nameOf(device: BluetoothDevice): String = try {
            device.name.orEmpty()
        } catch (_: SecurityException) {
            ""
        }

        /** Idempotent; the proxy callbacks are asynchronous, so the first query right after may still miss. */
        fun seedConnectedDevices(context: Context) {
            if (!seeded.compareAndSet(false, true)) return
            try {
                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        try {
                            proxy.connectedDevices.forEach { device -> device.address?.let { connected[it] = nameOf(device) } }
                        } catch (e: Exception) {
                            Log.w(TAG, "Bluetooth profile state read failed", e)
                        } finally {
                            adapter.closeProfileProxy(profile, proxy)
                        }
                    }

                    override fun onServiceDisconnected(profile: Int) = Unit
                }
                adapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET)
                adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
            } catch (e: Exception) {
                Log.w(TAG, "Bluetooth profile seed failed", e)
            }
        }

        /** Test-only. */
        internal fun setConnectedForTest(address: String, name: String, isConnected: Boolean) {
            seeded.set(true)
            if (isConnected) connected[address] = name else connected.remove(address)
        }

        fun intentFilter(): IntentFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }

        /**
         * `ACTION_CONFIGURATION_CHANGED` is delivered before this process's own `Resources` are
         * updated, so re-evaluating from `onReceive` still reads the *old* uiMode and orientation
         * and the dark-mode edge is missed entirely — the value only turns correct at the next
         * unrelated event. This callback runs when the new [Configuration] is actually applied to
         * the app, which is the value `AndroidConstraintEvaluator` reads. The broadcast still
         * fires too; a duplicate re-evaluation is harmless because the edge detector dedupes it.
         */
        private val configurationCallbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) =
                HardwareKeyTriggerCoordinator.reevaluateAutomations()

            override fun onLowMemory() = Unit
        }

        /** Wi-Fi association has no non-deprecated broadcast; the default-network callback covers it. */
        private val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = HardwareKeyTriggerCoordinator.reevaluateAutomations()
            override fun onLost(network: Network) = HardwareKeyTriggerCoordinator.reevaluateAutomations()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                HardwareKeyTriggerCoordinator.reevaluateAutomations()
        }

        fun register(context: Context, receiver: DeviceEventReceiver) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, intentFilter(), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, intentFilter())
            }
            context.applicationContext.registerComponentCallbacks(configurationCallbacks)
            runCatching {
                (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                    ?.registerDefaultNetworkCallback(networkCallback)
            }.onFailure { Log.w(TAG, "Network callback registration failed", it) }
        }

        fun unregister(context: Context, receiver: DeviceEventReceiver) {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { context.applicationContext.unregisterComponentCallbacks(configurationCallbacks) }
            runCatching {
                (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                    ?.unregisterNetworkCallback(networkCallback)
            }
        }
    }
}
