package dev.auxdesign.voz.voice

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RebuildPolicyTest {

    private var now = 0L
    private val policy = RebuildPolicy(clock = { now }, backoffMs = listOf(2_000L, 10_000L))

    @Test
    fun `a dead engine is rebuilt at once, then with growing gaps, and never given up`() {
        assertTrue(policy.tryNow())
        now = 1_999
        assertFalse(policy.tryNow())
        now = 2_000
        assertTrue(policy.tryNow())
        now = 11_999
        assertFalse(policy.tryNow())
        now = 12_000
        assertTrue(policy.tryNow())
        now = 22_000
        assertTrue(policy.tryNow(), "the last gap repeats: still retried every 10 s")
    }

    @Test
    fun `a successful utterance resets the backoff`() {
        assertTrue(policy.tryNow())
        policy.onSuccess()
        now = 1
        assertTrue(policy.tryNow())
    }
}
