package com.example.data

/** One decision per physical press, independent of Android and shared by both input paths. */
class KeyConsumptionTracker {
    private data class Press(val downTime: Long, val consumed: Boolean)
    private val presses = mutableMapOf<Pair<Int, Int>, Press>()

    fun onEvent(
        keyCode: Int,
        deviceId: Int,
        downTime: Long,
        isDown: Boolean,
        repeatCount: Int,
        decide: () -> Boolean,
    ): Boolean {
        val key = deviceId to keyCode
        val previous = presses[key]?.takeIf { it.downTime == downTime }
        if (!isDown) {
            // A delayed release for an older press must not discard the current decision.
            if (previous != null) presses.remove(key)
            return previous?.consumed ?: false
        }
        if (previous != null) return previous.consumed
        // A repeat without its original down must pass through, as must an orphan up.
        val consumed = repeatCount == 0 && decide()
        presses[key] = Press(downTime, consumed)
        return consumed
    }
}
