package com.example.assistant

/** Monotonic, process-local guard shared by every assistant entry point and launch action. */
internal class AssistantInvocationGate {
    private var launchedAt: Long? = null
    private var acceptedAt: Long? = null

    @Synchronized
    fun markOutgoing(now: Long) {
        launchedAt = now
    }

    @Synchronized
    fun accept(now: Long): Boolean {
        if (launchedAt?.let { now - it in 0 until 2_000L } == true) return false
        if (acceptedAt?.let { now - it in 0 until 300L } == true) return false
        acceptedAt = now
        return true
    }
}
