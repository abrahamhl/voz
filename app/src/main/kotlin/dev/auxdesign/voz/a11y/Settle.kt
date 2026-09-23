package dev.auxdesign.voz.a11y

import kotlinx.coroutines.delay

/** Waiting for the screen to react before the next step (pure timing logic, unit-tested with virtual time). */
object Settle {
    const val CHANGE_TIMEOUT_MS = 1_500L
    const val QUIET_MS = 300L
    const val MAX_MS = 2_500L
    const val POLL_MS = 50L

    /**
     * Waits until [stamp] (a counter of window events) moves past [before], then until it stays still for
     * [quietMs], so the next step reads the new screen and not a half-drawn one. Returns false if nothing
     * changed within [changeTimeoutMs] (a tap may legitimately change nothing, e.g. a toggle).
     */
    suspend fun awaitChangeThenQuiet(
        stamp: () -> Long,
        before: Long,
        changeTimeoutMs: Long = CHANGE_TIMEOUT_MS,
        quietMs: Long = QUIET_MS,
        maxMs: Long = MAX_MS,
        pollMs: Long = POLL_MS,
    ): Boolean {
        var elapsed = 0L
        while (stamp() == before && elapsed < changeTimeoutMs) {
            delay(pollMs)
            elapsed += pollMs
        }
        if (stamp() == before) return false
        var last = stamp()
        var quiet = 0L
        while (quiet < quietMs && elapsed < maxMs) {
            delay(pollMs)
            elapsed += pollMs
            val now = stamp()
            if (now != last) {
                last = now
                quiet = 0L
            } else {
                quiet += pollMs
            }
        }
        return true
    }
}
