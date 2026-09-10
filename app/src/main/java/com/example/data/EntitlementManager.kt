package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single place that knows the free-tier script limit, the rewarded-ad bonus window, and Pro
 * status. Every other class asks this one whether a script can be active, instead of re-deriving
 * the rule.
 */
class EntitlementManager internal constructor(
    private val prefs: SharedPreferences,
    private val scriptRepository: ScriptRepository,
) {
    companion object {
        private const val KEY_IS_PRO = "is_pro"
        private const val KEY_REWARD_EXPIRY_AT = "reward_expiry_at"
        const val FREE_SCRIPT_LIMIT = 2
        const val REWARD_BONUS_SCRIPTS = 1
        const val REWARD_WINDOW_MS = 4 * 60 * 60 * 1000L

        @Volatile private var instance: EntitlementManager? = null

        fun getInstance(context: Context): EntitlementManager = instance ?: synchronized(this) {
            instance ?: EntitlementManager(
                context.applicationContext.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE),
                ScriptRepository.getInstance(context),
            ).also { instance = it }
        }
    }

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_IS_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _rewardExpiryAt = MutableStateFlow(prefs.getLong(KEY_REWARD_EXPIRY_AT, 0L))
    val rewardExpiryAt: StateFlow<Long> = _rewardExpiryAt.asStateFlow()

    val rewardActive: Boolean get() = _rewardExpiryAt.value > System.currentTimeMillis()

    val activeScriptLimit: Int
        get() = if (_isPro.value) Int.MAX_VALUE else FREE_SCRIPT_LIMIT + if (rewardActive) REWARD_BONUS_SCRIPTS else 0

    fun setPro(pro: Boolean) {
        _isPro.value = pro
        prefs.edit().putBoolean(KEY_IS_PRO, pro).apply()
    }

    fun grantRewardWindow() {
        val expiry = System.currentTimeMillis() + REWARD_WINDOW_MS
        _rewardExpiryAt.value = expiry
        prefs.edit().putLong(KEY_REWARD_EXPIRY_AT, expiry).apply()
    }

    /**
     * Pauses the newest scripts over the current limit, oldest-first kept. There is no background
     * timer for the reward window (it is a wall-clock deadline, per this app's persistence
     * conventions) so this only corrects things when called — on app start and after every script
     * list change is enough to keep it from drifting for long.
     */
    fun enforceLimit() {
        if (_isPro.value) return
        val enabled = scriptRepository.scripts.value.filter { it.enabled }
        val limit = activeScriptLimit
        if (enabled.size <= limit) return
        enabled.drop(limit).forEach { scriptRepository.setScriptEnabled(it.id, false) }
    }
}
