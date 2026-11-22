package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * An app KeySnap steps aside for: while it is in front, the switches it objects to are turned off
 * and put back when it leaves. Both default to hidden, the safest start; the user can narrow it.
 * [hideWirelessDebugging] is opt-in and one-way: the bridge turns wireless debugging off and ends,
 * so Advanced Mode stays off until the user sets it up again.
 */
@JsonClass(generateAdapter = true)
data class SensitiveApp(
    val packageName: String,
    val label: String,
    val hideDeveloperOptions: Boolean = true,
    val hideAccessibility: Boolean = true,
    val hideWirelessDebugging: Boolean = false,
)

/** Notification and vibration stay off until the user asks for them. */
@JsonClass(generateAdapter = true)
data class SensitiveAppsSettings(
    val masterEnabled: Boolean = true,
    val showNotification: Boolean = false,
    val vibrate: Boolean = false,
    val vibrateMs: Long = 60,
)

@JsonClass(generateAdapter = true)
data class SensitiveAppsStore(
    val apps: List<SensitiveApp> = emptyList(),
    val settings: SensitiveAppsSettings = SensitiveAppsSettings(),
    /** The Home discovery card was closed or used; it never comes back. */
    val homeCardDismissed: Boolean = false,
    /** A wireless-debugging app ended Advanced Mode; the Fix cards show until a bridge is back or the user gives up on it. */
    val advancedModeEnded: Boolean = false,
)

/** The apps to step aside for now: none while the master switch is off, none with nothing to hide. */
val SensitiveAppsStore.activeApps: List<SensitiveApp>
    get() = if (!settings.masterEnabled) emptyList() else apps.filter { it.hideDeveloperOptions || it.hideAccessibility || it.hideWirelessDebugging }

/** Settings → Sensitive apps, as one JSON value. Unreadable data starts the list empty. */
class SensitiveAppsRepository internal constructor(
    private val prefs: SharedPreferences,
    moshi: Moshi,
) {
    companion object {
        private const val STORE_KEY = "sensitive_apps_v1"
        @Volatile private var instance: SensitiveAppsRepository? = null

        fun getInstance(context: Context): SensitiveAppsRepository = instance ?: synchronized(this) {
            instance ?: SensitiveAppsRepository(
                context.applicationContext.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE),
                Moshi.Builder().build(),
            ).also { instance = it }
        }
    }

    private val adapter = moshi.adapter(SensitiveAppsStore::class.java)
    private val _store = MutableStateFlow(
        prefs.getString(STORE_KEY, null)?.let { runCatching { adapter.fromJson(it) }.getOrNull() } ?: SensitiveAppsStore(),
    )
    val store: StateFlow<SensitiveAppsStore> = _store.asStateFlow()

    /** Adding an app already on the list keeps the existing entry and its choices. */
    fun add(packageName: String, label: String) = edit { store ->
        if (store.apps.any { it.packageName == packageName }) store
        else store.copy(apps = store.apps + SensitiveApp(packageName, label))
    }

    fun update(app: SensitiveApp) = edit { store ->
        store.copy(apps = store.apps.map { if (it.packageName == app.packageName) app else it })
    }

    fun remove(packageName: String) = edit { store -> store.copy(apps = store.apps.filterNot { it.packageName == packageName }) }

    fun updateSettings(change: (SensitiveAppsSettings) -> SensitiveAppsSettings) = edit { it.copy(settings = change(it.settings)) }

    fun dismissHomeCard() = edit { it.copy(homeCardDismissed = true) }

    fun setAdvancedModeEnded(ended: Boolean) {
        if (_store.value.advancedModeEnded != ended) edit { it.copy(advancedModeEnded = ended) }
    }

    private fun edit(change: (SensitiveAppsStore) -> SensitiveAppsStore) {
        _store.update(change)
        prefs.edit().putString(STORE_KEY, adapter.toJson(_store.value)).apply()
    }
}
