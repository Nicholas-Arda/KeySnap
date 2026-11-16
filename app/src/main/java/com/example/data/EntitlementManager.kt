package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.nicholasarda.keysnap.BuildConfig

/** Even a misconfigured constant cannot grant test access to the production package. */
internal fun capsuleAcceptanceProEnabled(requested: Boolean, applicationId: String): Boolean =
    requested && applicationId == "io.github.nicholasarda.keysnap.dev"

/**
 * The single place that knows the free-tier script limit and Pro
 * status. Every other class asks this one whether a script can be active, instead of re-deriving
 * the rule.
 */
class EntitlementManager internal constructor(
    private val prefs: SharedPreferences,
    private val scriptRepository: ScriptRepository,
    private val acceptancePro: Boolean = false,
) {
    companion object {
        private const val KEY_IS_PRO = "is_pro"
        const val FREE_SCRIPT_LIMIT = 3

        @Volatile private var instance: EntitlementManager? = null

        fun getInstance(context: Context): EntitlementManager = instance ?: synchronized(this) {
            instance ?: EntitlementManager(
                context.applicationContext.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE),
                ScriptRepository.getInstance(context),
                capsuleAcceptanceProEnabled(BuildConfig.CAPSULE_ACCEPTANCE_PRO, BuildConfig.APPLICATION_ID),
            ).also { instance = it }
        }
    }

    private val _isPro = MutableStateFlow(acceptancePro || prefs.getBoolean(KEY_IS_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    val activeScriptLimit: Int
        get() = if (_isPro.value) Int.MAX_VALUE else FREE_SCRIPT_LIMIT

    /** Whether one more script may be switched on without passing [activeScriptLimit]. */
    fun mayEnableAnother(): Boolean =
        _isPro.value || scriptRepository.scripts.value.count { it.enabled } < activeScriptLimit

    fun setPro(pro: Boolean) {
        _isPro.value = acceptancePro || pro
        // Persist only actual ownership, so installing a regular build removes test access.
        prefs.edit().putBoolean(KEY_IS_PRO, pro).apply()
    }

    /**
     * Pauses the newest scripts over the current limit, oldest-first kept. Called on app start,
     * after every script list change and when Pro status changes.
     */
    fun enforceLimit() {
        if (_isPro.value) return
        val enabled = scriptRepository.scripts.value.filter { it.enabled }
        val limit = activeScriptLimit
        if (enabled.size <= limit) return
        enabled.drop(limit).forEach { scriptRepository.setScriptEnabled(it.id, false) }
    }
}
