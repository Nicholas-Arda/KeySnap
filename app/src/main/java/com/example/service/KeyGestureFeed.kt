package com.example.service

import com.example.bridge.ChordTracker
import com.example.data.KeyMappingConfig
import com.example.data.ShortcutScript
import com.example.data.TriggerPressType
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
    fun onEvent(keyCode: Int, isDown: Boolean, eventTime: Long, scripts: List<ShortcutScript>, defaults: KeyMappingConfig) {
        val chords = ChordTracker.chordsOf(scripts.filter { it.enabled }.flatMap { s -> s.triggers.map { it.keyCodes } })
        for (command in tracker.onEvent(keyCode, isDown, eventTime, chords)) {
            if (command.kind == ChordTracker.CANCEL) {
                scheduler.cancel(command.keyCode)
            } else {
                scheduler.onEvent(command.keyCode, command.down, eventTime, resolveTimingForKey(scripts, command.keyCode, defaults))
            }
        }
    }

    @Synchronized
    fun reset() {
        tracker.reset()
        scheduler.reset()
    }
}
