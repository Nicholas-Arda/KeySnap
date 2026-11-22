package com.example.service

import com.example.bridge.ChordTracker
import com.example.bridge.PressTimingMachine
import com.example.data.KeyMappingConfig
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
import com.example.data.isLive
import com.example.data.keyId
import com.example.data.resolveTimingForKey
import kotlinx.coroutines.CoroutineScope

/**
 * The app's key-to-gesture path: the shared [ChordTracker] in front of [KeyPressScheduler].
 * BridgeMain.onKeyEvent does the same with PressTimingMachine; keep the two loops identical.
 * Gesture ids are key codes, or chord ids for keys held together.
 */
internal class KeyGestureFeed(
    scope: CoroutineScope,
    onGesture: (Int, TriggerPressType) -> Unit,
) {
    private val tracker = ChordTracker()
    private val scheduler = KeyPressScheduler(scope, onGesture)

    // Lock order: this feed, then the scheduler's lock. handleGesture never calls back into the feed.
    @Synchronized
    fun onEvent(
        keyCode: Int,
        isDown: Boolean,
        eventTime: Long,
        scripts: List<ShortcutScript>,
        defaults: KeyMappingConfig,
        globallyEnabled: Boolean = true,
    ) {
        // Chords from live scripts, timing from every enabled one: BridgeConfig.triggerKeyLists / triggersForKeyLocked.
        val keyLists = scripts.filter { it.isLive(globallyEnabled) }.flatMap { s -> s.triggers.map { it.keyCodes } }
        val chords = ChordTracker.chordsOf(keyLists)
        for (command in tracker.onEvent(keyCode, isDown, eventTime, chords, ChordTracker.soloKeysOf(keyLists))) {
            if (command.kind == ChordTracker.CANCEL) {
                scheduler.cancel(command.keyCode)
            } else {
                // Press types from every enabled trigger: BridgeConfig.pressTypesFor.
                val pressTypes = scripts.filter { it.enabled }.flatMap { it.triggers }
                    .filter { it.keyId() == command.keyCode }.map { it.pressType.name }
                scheduler.onEvent(
                    command.keyCode, command.down, eventTime, resolveTimingForKey(scripts, command.keyCode, defaults),
                    PressTimingMachine.singlePressAt(command.keyCode, pressTypes, chords),
                )
            }
        }
    }

    /** Whether [keyCode]'s own press is still down, not taken over by a chord; see [KeyPressScheduler.isHeld]. */
    fun isHeld(keyCode: Int): Boolean = scheduler.isHeld(keyCode)

    @Synchronized
    fun reset() {
        tracker.reset()
        scheduler.reset()
    }
}
