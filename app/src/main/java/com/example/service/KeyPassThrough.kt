package com.example.service

import android.content.Context
import android.media.AudioManager
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.example.data.KeyMappingConfig
import com.example.data.ScriptAction
import com.example.data.TriggerPressType
import kotlinx.coroutines.delay

/**
 * Gives a swallowed key its own job back when the press it resolves to runs no shortcut: a single
 * press does it once, a double press twice, a long press repeats it while the key is held, the way
 * holding the key does.
 *
 * The app decides, not the bridge: only the consumer (Accessibility or the activity, through
 * [HardwareKeyTriggerCoordinator.onKeySwallowed]) knows the press was swallowed. A key whose script
 * keeps its own job, whose shortcuts' "Only if" did not hold at the down, or that a Sensitive app
 * reached with Accessibility off already did its job, and a second one would double it. With
 * Advanced Mode the bridge performs it (`input keyevent`, so the system does exactly its own job);
 * without, [appJob] approximates it.
 */
internal object KeyPassThrough {
    /** Keys whose own job comes back, and how the app does it without Advanced Mode. Add a key here. */
    private val APP_JOBS: Map<Int, (Context) -> Unit> = mapOf(
        KeyEvent.KEYCODE_VOLUME_UP to { adjustVolume(it, AudioManager.ADJUST_RAISE) },
        KeyEvent.KEYCODE_VOLUME_DOWN to { adjustVolume(it, AudioManager.ADJUST_LOWER) },
    )

    /** AOSP's key repeat delay (ViewConfiguration DEFAULT_KEY_REPEAT_DELAY_MS, 50 ms); the long-press threshold stands in for its 400 ms repeat timeout. */
    const val REPEAT_DELAY_MS = 50L

    /** A press whose up never arrived (Accessibility rebound mid-press) must not repeat forever. */
    const val MAX_HOLD_MS = 10_000L

    /** Scheduling headroom on top of the longest a press can take to resolve. */
    private const val SLACK_MS = 500L

    fun handles(keyCode: Int): Boolean = keyCode in APP_JOBS

    /**
     * Our own `input keyevent` coming back: every injected event carries the virtual keyboard's device
     * id (InputDispatcher::injectInputEvent sets VIRTUAL_KEYBOARD_ID unless the event came through
     * the input filter; Android 11's `input` sets it itself). It must pass untouched, or the
     * activity would swallow and match the job it gave back. Hardware keys never carry this id.
     */
    fun isInjected(keyCode: Int, deviceId: Int): Boolean =
        deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD && handles(keyCode)

    /**
     * The one rule: the resolved press ran nothing, and KeySnap swallowed this key's last down
     * ([swallowedAt], elapsedRealtime) recently enough to belong to this press. A press resolves at
     * most a long-press threshold plus a double-press window after its down.
     */
    fun shouldGiveBack(keyCode: Int, nothingRan: Boolean, swallowedAt: Long?, now: Long, timing: KeyMappingConfig): Boolean =
        nothingRan && handles(keyCode) && swallowedAt != null &&
            now - swallowedAt in 0..timing.longPressThresholdMs + timing.doublePressWindowMs + SLACK_MS

    /** What the bridge runs: `input keyevent <keyCode>` (BridgeMain's input_key_code). */
    fun bridgeAction(keyCode: Int) = ScriptAction("input_key_code", mapOf("keyCode" to keyCode.toString()))

    fun appJob(context: Context, keyCode: Int) {
        APP_JOBS[keyCode]?.invoke(context)
    }

    /** Runs [step] as the press type asks; a long press repeats while [isHeld], from the moment it resolved. */
    suspend fun perform(pressType: TriggerPressType, isHeld: () -> Boolean, step: suspend () -> Unit) {
        when (pressType) {
            TriggerPressType.SINGLE_PRESS -> step()
            TriggerPressType.DOUBLE_PRESS -> repeat(2) { step() }
            TriggerPressType.LONG_PRESS -> {
                var held = 0L
                step()
                while (held < MAX_HOLD_MS) {
                    delay(REPEAT_DELAY_MS)
                    held += REPEAT_DELAY_MS
                    if (!isHeld()) break
                    step()
                }
            }
        }
    }

    private fun adjustVolume(context: Context, direction: Int) {
        // Throws when the step would change Do Not Disturb without policy access; that press then does nothing.
        runCatching {
            context.getSystemService(AudioManager::class.java)
                ?.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
        }
    }
}
