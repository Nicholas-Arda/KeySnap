package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScriptRepository internal constructor(
    private val prefs: SharedPreferences,
    moshi: Moshi,
) {
    companion object {
        private const val STORE_KEY = "shortcut_scripts_v1"
        private const val LEGACY_ID = "legacy-flashlight-mapping"
        @Volatile private var instance: ScriptRepository? = null

        fun getInstance(context: Context): ScriptRepository = instance ?: synchronized(this) {
            instance ?: ScriptRepository(
                context.applicationContext.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE),
                Moshi.Builder().build(),
            ).also { instance = it }
        }
    }

    private val adapter = moshi.adapter(PersistedScriptStore::class.java)
    private val initialStore = loadOrMigrate()
    private val _scripts = MutableStateFlow(initialStore.scripts.mapNotNull(PersistedShortcutScript::toDomain))
    val scripts: StateFlow<List<ShortcutScript>> = _scripts.asStateFlow()
    private val _globallyEnabled = MutableStateFlow(initialStore.globallyEnabled)
    val globallyEnabled: StateFlow<Boolean> = _globallyEnabled.asStateFlow()

    private fun loadOrMigrate(): PersistedScriptStore {
        prefs.getString(STORE_KEY, null)?.let { json ->
            runCatching { adapter.fromJson(json) }.getOrNull()?.let { return it }
        }
        // The first write is the migration marker. It snapshots every legacy behavior setting.
        val keyCode = prefs.getInt("target_key_code", -1).takeIf { it != -1 }
        val pressType = prefs.getString("press_type", TriggerPressType.DOUBLE_PRESS.name)
            ?.let { runCatching { TriggerPressType.valueOf(it) }.getOrNull() }
            ?: TriggerPressType.DOUBLE_PRESS
        val enabled = prefs.getBoolean("is_enabled", true)
        val migrated = PersistedScriptStore(
            globallyEnabled = enabled,
            scripts = keyCode?.let {
                listOf(
                    ShortcutScript(
                        id = LEGACY_ID,
                        name = "Flashlight mapping",
                        enabled = enabled,
                        triggers = listOf(ScriptTrigger(listOf(it), pressType)),
                        actions = listOf(ScriptAction.ToggleFlashlight),
                    ).toPersisted(),
                )
            }.orEmpty(),
        )
        persist(migrated)
        return migrated
    }

    fun setGloballyEnabled(enabled: Boolean) {
        _globallyEnabled.value = enabled
        // Retain compatibility for old releases and existing settings surfaces.
        prefs.edit().putBoolean("is_enabled", enabled).apply()
        persistCurrent()
    }

    /** A script with actions but no trigger yet is a legitimate saved draft; it simply never matches. */
    fun upsert(script: ShortcutScript) {
        require(script.id.isNotBlank() && script.actions.isNotEmpty())
        _scripts.value = _scripts.value.filterNot { it.id == script.id } + script
        syncLegacyIfPrimary(script)
        persistCurrent()
    }

    fun delete(id: String) {
        _scripts.value = _scripts.value.filterNot { it.id == id }
        persistCurrent()
    }

    fun setScriptEnabled(id: String, enabled: Boolean) {
        _scripts.value = _scripts.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        persistCurrent()
    }

    fun newScriptId(): String = "script-${UUID.randomUUID()}"

    fun shouldConsume(keyCode: Int): Boolean = globallyEnabled.value &&
        prefs.getBoolean("consume_original", true) && scripts.value.any { script ->
            script.enabled && script.triggers.any { it.keyCodes.size == 1 && it.keyCodes.single() == keyCode }
        }

    private fun syncLegacyIfPrimary(script: ShortcutScript) {
        if (_scripts.value.firstOrNull()?.id != script.id) return
        val trigger = script.triggers.firstOrNull { it.keyCodes.size == 1 } ?: return
        prefs.edit()
            .putInt("target_key_code", trigger.keyCodes.single())
            .putString("press_type", trigger.pressType.name)
            .apply()
    }

    private fun persistCurrent() = persist(PersistedScriptStore(
        globallyEnabled = _globallyEnabled.value,
        scripts = _scripts.value.map(ShortcutScript::toPersisted),
    ))

    private fun persist(store: PersistedScriptStore) {
        prefs.edit().putString(STORE_KEY, adapter.toJson(store)).apply()
    }
}
