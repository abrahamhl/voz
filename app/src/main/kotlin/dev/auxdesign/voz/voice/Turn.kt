package dev.auxdesign.voz.voice

import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.PlanSource

/**
 * What the current (or last) voice turn really is. The UI renders only this: no confidence scores,
 * no fake progress. [Finished] stays until the next turn starts, so the last result stays visible.
 */
sealed interface Turn {
    data object Idle : Turn

    /** The recognizer is starting; the mic is open only when [VoiceSession.micOpen] says so. */
    data object Listening : Turn

    data class Understanding(val heard: String) : Turn

    data class Acting(val heard: String, val steps: List<Action>, val source: PlanSource, val index: Int) : Turn

    data class Confirming(val heard: String, val what: String) : Turn

    data class Finished(
        val heard: String?,
        val result: Result,
        val steps: List<Action> = emptyList(),
        val source: PlanSource? = null,
        val failedAt: Int? = null,
        val reply: String? = null,
    ) : Turn

    enum class Result {
        DONE, FAILED, CANCELLED, STOPPED, BLOCKED, NOT_HEARD, NOT_UNDERSTOOD, CLOUD_ERROR, MIC_DENIED, SPEECH_UNAVAILABLE, SPEECH_ERROR,
    }
}

/** The one visual state of the mic orb and the floating bubble. */
enum class OrbState { IDLE, STARTING, LISTENING, UNDERSTANDING, ACTING, SPEAKING, CONFIRMING }

/** Yellow (mic open) only when the recognizer is really listening; a question being asked shows as speaking. */
fun orbState(turn: Turn, micOpen: Boolean, speaking: Boolean): OrbState = when {
    micOpen -> if (turn is Turn.Confirming) OrbState.CONFIRMING else OrbState.LISTENING
    speaking -> OrbState.SPEAKING
    turn is Turn.Listening -> OrbState.STARTING
    turn is Turn.Understanding -> OrbState.UNDERSTANDING
    turn is Turn.Acting -> OrbState.ACTING
    turn is Turn.Confirming -> OrbState.SPEAKING
    else -> OrbState.IDLE
}

/** True while a turn is running (tapping the orb stops it). */
val OrbState.busy: Boolean get() = this != OrbState.IDLE

/** Maps the recognizer's RMS dB (roughly −2…10 on common engines, TODO(verify) per device) to 0…1. */
fun normalizedLevel(rmsDb: Float): Float = ((rmsDb - MIN_RMS_DB) / (MAX_RMS_DB - MIN_RMS_DB)).coerceIn(0f, 1f)

private const val MIN_RMS_DB = -2f
private const val MAX_RMS_DB = 10f
