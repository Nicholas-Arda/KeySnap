package com.example.data

import androidx.annotation.StringRes
import io.github.nicholasarda.keysnap.R

enum class AccessibilityStatus(@StringRes val label: Int) {
    DISABLED(R.string.home_status_offline),
    CONNECTING(R.string.accessibility_status_connecting),
    ACTIVE(R.string.home_status_active),
    NEEDS_RECONNECT(R.string.accessibility_status_reconnect),
}

/** Permission is not a live connection. Allow Android a short binding grace period after launch. */
class AccessibilityStatusTracker(private val bindingGraceMs: Long = 3_000L) {
    private var disconnectedSince: Long? = null

    fun update(enabled: Boolean, connected: Boolean, elapsedRealtimeMs: Long): AccessibilityStatus {
        if (!enabled || connected) {
            disconnectedSince = null
            return if (enabled && connected) AccessibilityStatus.ACTIVE else AccessibilityStatus.DISABLED
        }
        val since = disconnectedSince ?: elapsedRealtimeMs.also { disconnectedSince = it }
        return if (elapsedRealtimeMs - since >= bindingGraceMs) {
            AccessibilityStatus.NEEDS_RECONNECT
        } else {
            AccessibilityStatus.CONNECTING
        }
    }
}
