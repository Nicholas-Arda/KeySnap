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

        /** Home-screen widgets on the free plan. Quick Settings tiles are free: there are only three. */
        const val FREE_WIDGET_LIMIT = 2

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

    fun setPro(pro: Boolean) {
        _isPro.value = acceptancePro || pro
        // Persist only actual ownership, so installing a regular build removes test access.
        prefs.edit().putBoolean(KEY_IS_PRO, pro).apply()
    }

    /**
     * Whether home-screen widget [appWidgetId] may be bound or run, given the widgets already on the
     * home screen with a shortcut ([boundWidgetIds]). Free keeps the first [FREE_WIDGET_LIMIT] by
     * id: the widget host hands ids out in increasing order, so that is placement order and needs
     * nothing stored. After a refund the later widgets stay put but refuse to run until Pro returns.
     */
    fun widgetAllowed(appWidgetId: Int, boundWidgetIds: Collection<Int>): Boolean =
        _isPro.value || appWidgetId in (boundWidgetIds + appWidgetId).toSortedSet().take(FREE_WIDGET_LIMIT)

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
