package com.example.service

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import com.example.bridge.BrightnessSettings
import com.example.bridge.LearnedSetting
import com.example.bridge.PresetSettings
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.service.bridge.BridgeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

/**
 * "Undo when it ends": an automation remembers what its flagged actions changed and puts it back
 * once its If stops being true. Only the value from just before the run is restored, and only when
 * the setting still holds what the run left there — a setting the user changed in between is theirs.
 * Kept in prefs, because the If can end hours later in another process.
 */
object AutomationUndo {

    /** One setting an action changes. Several actions touching the same one are restored once. */
    enum class Kind {
        FLASHLIGHT, DND, RINGER, BRIGHTNESS, VOLUME_RING, VOLUME_MEDIA, VOLUME_ALARM, VOLUME_NOTIFICATION,
        WIFI, BLUETOOTH, BATTERY_SAVER, ALWAYS_ON_DISPLAY, GRAYSCALE, CHARGE_LIMIT,
    }

    private val STREAMS = mapOf(
        "ring" to Kind.VOLUME_RING, "media" to Kind.VOLUME_MEDIA,
        "alarm" to Kind.VOLUME_ALARM, "notification" to Kind.VOLUME_NOTIFICATION,
    )

    fun kindOf(action: ScriptAction): Kind? = when (action.type) {
        "toggle_flashlight", "enable_flashlight", "disable_flashlight" -> Kind.FLASHLIGHT
        "toggle_dnd", "enable_dnd", "disable_dnd" -> Kind.DND
        "change_ringer_mode", "cycle_ringer_mode" -> Kind.RINGER
        "change_brightness", "increase_brightness", "decrease_brightness" -> Kind.BRIGHTNESS
        "change_volume_stream" -> STREAMS[action.params["stream"]]
        "toggle_wifi", "enable_wifi", "disable_wifi" -> Kind.WIFI
        "toggle_bluetooth", "enable_bluetooth", "disable_bluetooth" -> Kind.BLUETOOTH
        "toggle_battery_saver", "enable_battery_saver", "disable_battery_saver" -> Kind.BATTERY_SAVER
        "always_on_display" -> Kind.ALWAYS_ON_DISPLAY
        "grayscale" -> Kind.GRAYSCALE
        "charge_limit" -> Kind.CHARGE_LIMIT
        else -> null
    }

    private const val LEARNED = "learned:"

    /**
     * What [action] changes, as a stored key: a [Kind]'s name, or "learned:" plus a taught switch's
     * writes, so the switch can be read and put back without the learned list.
     */
    fun keyOf(action: ScriptAction): String? = kindOf(action)?.name
        ?: action.params[LearnedSetting.PARAM_WRITES]
            ?.takeIf { action.type == LearnedSetting.TYPE && LearnedSetting.parse(it) != null }
            ?.let { LEARNED + it }

    fun canUndo(action: ScriptAction): Boolean = keyOf(action) != null

    /** A pulse (At a time, Repeat every, a notification, a new feed item) is over the moment it fires. */
    private val PULSES = setOf(
        ScriptConstraintCatalog.AT_TIME, ScriptConstraintCatalog.INTERVAL,
        ScriptConstraintCatalog.NOTIFICATION_RECEIVED, ScriptConstraintCatalog.RSS_NEW_ITEM,
    )

    /** Undo needs an If that holds for a while and then ends. */
    fun offered(automation: Boolean, constraints: List<ScriptConstraint>): Boolean =
        automation && constraints.isNotEmpty() && constraints.none { it.type in PULSES }

    /** The settings [actions] will put back, given the If allows undo at all. */
    fun kinds(actions: List<ScriptAction>): List<String> = actions.filter { it.undoOnEnd }.mapNotNull(::keyOf).distinct()

    /** Current value of each kind; a kind that can't be read here is left out (and never undone). */
    suspend fun read(context: Context, kinds: List<String>): Map<String, String> =
        kinds.mapNotNull { kind -> readOne(context, kind)?.let { kind to it } }.toMap()

    private suspend fun readOne(context: Context, key: String): String? = runCatching {
        if (key.startsWith(LEARNED)) return@runCatching LearnedSetting.variants(key.removePrefix(LEARNED))?.let { preset(context, it) }?.toString()
        when (Kind.valueOf(key)) {
            Kind.FLASHLIGHT -> FlashlightController.isFlashlightOn.value.toString()
            Kind.DND -> context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter?.toString()
            Kind.RINGER -> context.getSystemService(AudioManager::class.java)?.ringerMode?.toString()
            Kind.BRIGHTNESS -> {
                val resolver = context.contentResolver
                "${Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)}," +
                    Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            }
            Kind.VOLUME_RING -> volume(context, AudioManager.STREAM_RING)
            Kind.VOLUME_MEDIA -> volume(context, AudioManager.STREAM_MUSIC)
            Kind.VOLUME_ALARM -> volume(context, AudioManager.STREAM_ALARM)
            Kind.VOLUME_NOTIFICATION -> volume(context, AudioManager.STREAM_NOTIFICATION)
            Kind.WIFI -> context.getSystemService(WifiManager::class.java)?.isWifiEnabled?.toString()
            Kind.BLUETOOTH -> context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled?.toString()
            Kind.BATTERY_SAVER -> context.getSystemService(PowerManager::class.java)?.isPowerSaveMode?.toString()
            Kind.ALWAYS_ON_DISPLAY -> preset(context, PresetSettings.ALWAYS_ON_DISPLAY)?.toString()
            Kind.GRAYSCALE -> preset(context, PresetSettings.GRAYSCALE)?.toString()
            Kind.CHARGE_LIMIT -> when (preset(context, PresetSettings.CHARGE_LIMIT)) {
                null -> null
                false -> "off"
                true -> "on:" + bridgeSetting(context, "system", PresetSettings.CHARGE_LEVEL_KEY)
            }
        }
    }.getOrNull()

    private fun volume(context: Context, stream: Int): String? =
        context.getSystemService(AudioManager::class.java)?.getStreamVolume(stream)?.toString()

    /** The preset keys are hidden settings an app UID may not read; the bridge (which ran the action) can. */
    internal suspend fun preset(context: Context, variants: List<PresetSettings.Variant>): Boolean? = withContext(Dispatchers.IO) {
        val store = object : PresetSettings.Store {
            override fun get(namespace: String, key: String): String =
                runBlocking { bridgeSetting(context, namespace, key) } ?: "null"
            override fun put(namespace: String, key: String, value: String) = false
        }
        PresetSettings.isOn(variants, store)
    }

    private suspend fun bridgeSetting(context: Context, namespace: String, key: String): String? =
        SettingAccess.read(context, namespace, key, advanced = true) { action ->
            BridgeController.runSingleActionForTest(action) ?: com.example.bridge.ActionTestResult.outcome(false, System.nanoTime())
        }

    /** The actions that put [kind] back to [before]. */
    internal fun restore(context: Context, key: String, before: String): List<ScriptAction> {
        if (key.startsWith(LEARNED)) {
            return listOf(
                ScriptAction(
                    LearnedSetting.TYPE,
                    mapOf(LearnedSetting.PARAM_WRITES to key.removePrefix(LEARNED), LearnedSetting.PARAM_STATE to if (before == "true") "on" else "off"),
                ),
            )
        }
        return restore(context, Kind.valueOf(key), before)
    }

    private fun restore(context: Context, kind: Kind, before: String): List<ScriptAction> = when (kind) {
        Kind.FLASHLIGHT -> listOf(ScriptAction(if (before == "true") "enable_flashlight" else "disable_flashlight"))
        Kind.DND -> listOf(
            when (before.toIntOrNull()) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> ScriptAction("disable_dnd")
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> ScriptAction("enable_dnd", mapOf("filter" to "alarms"))
                NotificationManager.INTERRUPTION_FILTER_NONE -> ScriptAction("enable_dnd", mapOf("filter" to "none"))
                else -> ScriptAction("enable_dnd", mapOf("filter" to "priority"))
            },
        )
        Kind.RINGER -> listOf(
            ScriptAction(
                "change_ringer_mode",
                mapOf("mode" to when (before.toIntOrNull()) {
                    AudioManager.RINGER_MODE_SILENT -> "silent"
                    AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                    else -> "normal"
                }),
            ),
        )
        Kind.BRIGHTNESS -> {
            val (raw, mode) = before.split(",").map { it.toIntOrNull() ?: 0 }.let { it[0] to it.getOrElse(1) { 0 } }
            // change_brightness takes 0..255 and scales to this phone; find the value that lands on [raw].
            val scale = BrightnessSettings.scale(context)
            val legacy = (0..255).minBy { abs(scale.fromLegacy(it) - raw) }
            listOfNotNull(
                ScriptAction("change_brightness", mapOf("value" to legacy.toString())),
                // Setting a level switches auto-brightness off; put it back on if it was.
                ScriptAction("toggle_auto_brightness").takeIf { mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC },
            )
        }
        Kind.VOLUME_RING, Kind.VOLUME_MEDIA, Kind.VOLUME_ALARM, Kind.VOLUME_NOTIFICATION -> listOf(
            ScriptAction("change_volume_stream", mapOf("stream" to STREAMS.entries.first { it.value == kind }.key, "value" to before)),
        )
        Kind.WIFI -> listOf(ScriptAction(if (before == "true") "enable_wifi" else "disable_wifi"))
        Kind.BLUETOOTH -> listOf(ScriptAction(if (before == "true") "enable_bluetooth" else "disable_bluetooth"))
        Kind.BATTERY_SAVER -> listOf(ScriptAction(if (before == "true") "enable_battery_saver" else "disable_battery_saver"))
        Kind.ALWAYS_ON_DISPLAY -> listOf(ScriptAction("always_on_display", mapOf("state" to if (before == "true") "on" else "off")))
        Kind.GRAYSCALE -> listOf(ScriptAction("grayscale", mapOf("state" to if (before == "true") "on" else "off")))
        Kind.CHARGE_LIMIT -> listOf(
            if (before.startsWith("on")) {
                ScriptAction("charge_limit", mapOf("state" to "on", "level" to before.substringAfter(":", "80")))
            } else {
                ScriptAction("charge_limit", mapOf("state" to "off"))
            },
        )
    }

    // --- What each automation changed, until its If ends ---

    private fun prefs(context: Context) = context.getSharedPreferences("automation_undo", Context.MODE_PRIVATE)

    /**
     * After a run: remembers each kind the run changed. An entry left from an earlier run that was
     * never undone keeps its older "before", so the original value is what comes back.
     */
    internal fun remember(context: Context, scriptId: String, before: Map<String, String>, after: Map<String, String>) {
        val stored = load(context, scriptId).toMutableMap()
        after.forEach { (kind, now) ->
            val was = stored[kind]?.first ?: before[kind] ?: return@forEach
            if (was == now) stored.remove(kind) else stored[kind] = was to now
        }
        save(context, scriptId, stored)
    }

    internal fun pending(context: Context, scriptId: String): Boolean = prefs(context).contains(scriptId)

    internal fun pendingIds(context: Context): Set<String> = prefs(context).all.keys

    internal fun forget(context: Context, scriptId: String) = prefs(context).edit().remove(scriptId).apply()

    /**
     * The actions that undo the last run of [scriptId], entry cleared. Only [allowed] kinds (still
     * flagged on the script) come back, and a setting the user changed since is skipped.
     */
    internal suspend fun takeRestore(context: Context, scriptId: String, allowed: List<String>): List<ScriptAction> {
        val stored = load(context, scriptId).filterKeys { it in allowed }
        forget(context, scriptId)
        val now = read(context, stored.keys.toList())
        return stored.flatMap { (kind, values) ->
            val (before, after) = values
            if (now[kind] == after) restore(context, kind, before) else emptyList()
        }
    }

    internal fun load(context: Context, scriptId: String): Map<String, Pair<String, String>> {
        val json = prefs(context).getString(scriptId, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().mapNotNull { name ->
                val known = name.startsWith(LEARNED) || runCatching { Kind.valueOf(name) }.isSuccess
                if (!known) return@mapNotNull null
                val kind = name
                val pair = obj.getJSONArray(name)
                kind to (pair.getString(0) to pair.getString(1))
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun save(context: Context, scriptId: String, entries: Map<String, Pair<String, String>>) {
        if (entries.isEmpty()) {
            forget(context, scriptId)
            return
        }
        val obj = JSONObject()
        entries.forEach { (kind, pair) -> obj.put(kind, org.json.JSONArray().put(pair.first).put(pair.second)) }
        prefs(context).edit().putString(scriptId, obj.toString()).apply()
    }
}
