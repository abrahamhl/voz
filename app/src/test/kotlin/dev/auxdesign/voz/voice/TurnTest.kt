package dev.auxdesign.voz.voice

import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.PlanSource
import dev.auxdesign.voz.overlay.BubbleBounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TurnTest {

    private val acting = Turn.Acting("abre youtube", listOf(Action.OpenApp("YouTube")), PlanSource.LOCAL_PARSER, 0)

    @Test
    fun `yellow only while the recognizer is really listening`() {
        assertEquals(OrbState.STARTING, orbState(Turn.Listening, micOpen = false, speaking = false))
        assertEquals(OrbState.LISTENING, orbState(Turn.Listening, micOpen = true, speaking = false))
    }

    @Test
    fun `a confirmation shows as speaking while asked and as confirming once the mic is open`() {
        val confirming = Turn.Confirming("borra el chat", "tap “Eliminar”")
        assertEquals(OrbState.SPEAKING, orbState(confirming, micOpen = false, speaking = true))
        assertEquals(OrbState.SPEAKING, orbState(confirming, micOpen = false, speaking = false))
        assertEquals(OrbState.CONFIRMING, orbState(confirming, micOpen = true, speaking = false))
    }

    @Test
    fun `working states and a finished turn`() {
        assertEquals(OrbState.UNDERSTANDING, orbState(Turn.Understanding("hola"), micOpen = false, speaking = false))
        assertEquals(OrbState.ACTING, orbState(acting, micOpen = false, speaking = false))
        assertEquals(OrbState.SPEAKING, orbState(acting, micOpen = false, speaking = true))
        val done = Turn.Finished("abre youtube", Turn.Result.DONE)
        assertEquals(OrbState.IDLE, orbState(done, micOpen = false, speaking = false))
        assertFalse(OrbState.IDLE.busy)
        assertTrue(OrbState.STARTING.busy)
    }

    @Test
    fun `audio level is normalised and clamped`() {
        assertEquals(0f, normalizedLevel(-10f))
        assertEquals(1f, normalizedLevel(20f))
        assertEquals(0.5f, normalizedLevel(4f), 0.001f)
    }

    @Test
    fun `the floating mic always stays fully on screen`() {
        assertEquals(0 to 0, BubbleBounds.clamp(-50, -20, 100, 1080, 2400))
        assertEquals(980 to 2300, BubbleBounds.clamp(5000, 9000, 100, 1080, 2400))
        assertEquals(300 to 400, BubbleBounds.clamp(300, 400, 100, 1080, 2400))
        // After rotating to landscape, a position valid in portrait is pulled back in.
        assertEquals(900 to 980, BubbleBounds.clamp(900, 1500, 100, 1080, 1080))
    }
}
