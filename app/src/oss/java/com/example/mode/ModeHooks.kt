package com.example.mode

import android.content.Context
import androidx.compose.runtime.Composable
import com.example.data.PhysicalKeyOption
import com.example.data.ScriptAction
import com.example.data.ScriptConstraint
import com.example.data.ScriptConstraintCatalog
import com.example.data.ShortcutScript
import com.example.data.WhatsNewFeature

/**
 * Public-source build. Shortcut modes are a KeySnap Pro feature whose source is kept private;
 * the Pro build replaces this file (see `keysnap.edition` in app/build.gradle.kts). A mode
 * condition fails closed and cycling does nothing. Every signature here matches the Pro file.
 */
object ModeHooks {
    const val AVAILABLE = false
    const val CYCLE_MODE = "cycle_mode"

    fun whatsNewFeatures(versionCode: Long): List<WhatsNewFeature> = emptyList()

    fun offered(type: String): Boolean = AVAILABLE || (type != CYCLE_MODE && type != ScriptConstraintCatalog.MODE_IS)

    fun requiresPro(type: String): Boolean = type == CYCLE_MODE || type == ScriptConstraintCatalog.MODE_IS

    fun allows(type: String, isPro: Boolean): Boolean = !requiresPro(type) || (AVAILABLE && isPro)

    fun hidesFromEditor(type: String, scriptId: String, scripts: List<ShortcutScript>, isPro: Boolean): Boolean = false

    fun cycleCountLocked(actionType: String, fieldKey: String, scripts: List<ShortcutScript>): Boolean = false

    fun alignCycleCount(
        actionType: String,
        params: Map<String, String>,
        scripts: List<ShortcutScript>,
    ): Map<String, String> = params

    fun showsRunsIn(scriptId: String, scripts: List<ShortcutScript>, isPro: Boolean): Boolean = false

    fun withRunsIn(constraints: List<ScriptConstraint>, mode: Int?): List<ScriptConstraint> = constraints

    fun constraintsForNewShortcut(mode: Int?): List<ScriptConstraint> = emptyList()

    fun noteViewedMode(mode: Int?) = Unit

    fun modeForNewShortcut(shortcutsPageVisible: Boolean): Int? = null

    fun offerRecordedKeys(keyCodes: List<Int>): Boolean = false

    /** Display clamp for a count already stored on a script. Cycling itself is a no-op here. */
    fun modeCount(value: String?): Int {
        val count = value?.trim()?.toIntOrNull() ?: 3
        return count.coerceIn(2, 9)
    }

    fun ensureState(context: Context) = Unit

    fun forgetState(context: Context) = Unit

    fun matches(context: Context, expected: String?): Boolean = false

    suspend fun cycle(context: Context, params: Map<String, String>): Boolean = false

    fun triggerFeedbackPause(actions: List<ScriptAction>, durationMs: Long): Long = 0L

    fun showToast(context: Context, mode: Int) = Unit

    @Composable
    fun shortcutList(
        scripts: List<ShortcutScript>,
        isPro: Boolean,
        onUpsell: () -> Unit,
        onUpdateScripts: (List<ShortcutScript>) -> Unit,
        onNewScriptId: () -> String,
        savedKeys: List<PhysicalKeyOption>,
        isRecordingKey: Boolean,
        onStartRecording: () -> Unit,
        onCancelRecording: () -> Unit,
        hapticDurationMs: Long,
        vibrateOnTrigger: Boolean,
    ): List<ShortcutScript> = scripts

    @Composable
    fun LockedCycleCount(scripts: List<ShortcutScript>) = Unit

    @Composable
    fun RunsInField(
        scriptId: String,
        constraints: List<ScriptConstraint>,
        scripts: List<ShortcutScript>,
        isPro: Boolean,
        onChange: (Int?) -> Unit,
    ) = Unit
}
