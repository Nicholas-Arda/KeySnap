package com.example.data

import android.content.Context
import com.example.bridge.LearnedSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A switch taught on this phone ("Teach a setting"): its name and [LearnedSetting] writes. */
data class LearnedSwitch(val id: String, val name: String, val writes: String)

/** One key a teaching run kept: its value after the user's change and after changing it back. */
data class TaughtKey(val namespace: String, val key: String, val changed: String, val back: String)

sealed interface TeachOutcome {
    data class Learned(val keys: List<TaughtKey>) : TeachOutcome
    data object NoChange : TeachOutcome
    data object TooMany : TeachOutcome
    data object Blocked : TeachOutcome
    /** Only a record: the phone's own app applies the change, so writing the key changes nothing ([TeachRules.RECORD_ONLY]). */
    data object RecordOnly : TeachOutcome
    /** The bridge forgot the run (10 minutes passed, or it restarted), or did not answer. */
    data object Lost : TeachOutcome
}

/** Pure rules for turning a teaching run into a [LearnedSetting]; unit-tested. */
object TeachRules {
    private val TRUE = setOf("1", "true", "on", "enabled", "enable", "yes")
    private val FALSE = setOf("0", "false", "off", "disabled", "disable", "no")

    /**
     * Keys the phone only records: its own app applies the change through a service no app or shell
     * can reach, so a learned switch would flip in Settings and change nothing. Grows with reports.
     * `system_dolby*`: OPPO/OnePlus sound effects (checked on ColorOS 17, 25 Nov 2026).
     */
    val RECORD_ONLY = listOf("system_dolby")

    /** The bridge's answer to TEACH back. */
    fun parseBack(reply: String?): TeachOutcome = runCatching {
        val json = JSONObject(reply ?: return TeachOutcome.Lost)
        when (json.optString("status")) {
            "ok" -> {
                val changes = json.getJSONArray("changes")
                TeachOutcome.Learned(
                    (0 until changes.length()).map { i ->
                        val c = changes.getJSONObject(i)
                        TaughtKey(c.getString("ns"), c.getString("key"), c.getString("changed"), c.getString("back"))
                    },
                ).takeIf { it.keys.isNotEmpty() }
                    ?.let { learned -> if (learned.keys.any { k -> RECORD_ONLY.any(k.key::startsWith) }) TeachOutcome.RecordOnly else learned }
                    ?: TeachOutcome.NoChange
            }
            "none" -> TeachOutcome.NoChange
            "too-many" -> TeachOutcome.TooMany
            "blocked" -> TeachOutcome.Blocked
            else -> TeachOutcome.Lost
        }
    }.getOrDefault(TeachOutcome.Lost)

    private fun isSwitch(k: TaughtKey) = k.changed.lowercase().let { c ->
        k.back.lowercase().let { b -> (c in TRUE && b in FALSE) || (c in FALSE && b in TRUE) }
    }

    /** 1/0, true/false and friends say which side is On; anything else needs the user's word. */
    fun knowsOn(keys: List<TaughtKey>): Boolean = keys.any(::isSwitch)

    /**
     * The writes, state key first (a 1/0-looking one where there is one). On is the side the state
     * key calls true; with no such key it is the side the user changed to, unless [swapped].
     */
    fun writes(keys: List<TaughtKey>, swapped: Boolean = false): String {
        val ordered = keys.sortedByDescending(::isSwitch)
        val state = ordered.first()
        val changedIsOn = (if (isSwitch(state)) state.changed.lowercase() in TRUE else true) != swapped
        return LearnedSetting.encode(
            ordered.map {
                LearnedSetting.Write(it.namespace, it.key, if (changedIsOn) it.changed else it.back, if (changedIsOn) it.back else it.changed)
            },
        )
    }

    /** The commands printed before a learned switch is saved or run (CLAUDE.md legibility rule). */
    fun commands(writes: String?, on: Boolean): List<String>? =
        LearnedSetting.parse(writes)?.map { "settings put ${it.namespace} ${it.key} ${if (on) it.on else it.off}" }
}

/** Switches taught on this phone, for the action picker. Phone-bound: never shared. */
object LearnedSettings {
    private const val PREFS = "learned_settings"
    private const val KEY = "items"
    private val items = MutableStateFlow<List<LearnedSwitch>>(emptyList())

    @Volatile private var loaded = false

    fun flow(context: Context): StateFlow<List<LearnedSwitch>> {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    items.value = read(context)
                    loaded = true
                }
            }
        }
        return items.asStateFlow()
    }

    fun add(context: Context, name: String, writes: String) =
        update(context) { it + LearnedSwitch(UUID.randomUUID().toString(), name.trim(), writes) }

    fun rename(context: Context, id: String, name: String) =
        update(context) { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }

    fun delete(context: Context, id: String) = update(context) { list -> list.filterNot { it.id == id } }

    private fun update(context: Context, change: (List<LearnedSwitch>) -> List<LearnedSwitch>) {
        flow(context)
        items.value = change(items.value)
        val array = JSONArray()
        items.value.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("writes", it.writes)) }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun read(context: Context): List<LearnedSwitch> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY, null) ?: return emptyList())
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            LearnedSwitch(o.getString("id"), o.getString("name"), o.getString("writes")).takeIf { LearnedSetting.parse(it.writes) != null }
        }
    }.getOrDefault(emptyList())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
