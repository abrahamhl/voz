package dev.auxdesign.voz.voice

import androidx.annotation.StringRes
import dev.auxdesign.voz.R
import dev.auxdesign.voz.a11y.ActionExecutor
import dev.auxdesign.voz.a11y.ExecEnv
import dev.auxdesign.voz.a11y.ExecResult
import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.Lang
import dev.auxdesign.voz.core.model.PlanSource
import dev.auxdesign.voz.core.model.Utterance
import dev.auxdesign.voz.core.route.Router
import dev.auxdesign.voz.core.safety.CloudReply
import dev.auxdesign.voz.core.safety.ConfirmationReply
import dev.auxdesign.voz.data.ActionLog
import dev.auxdesign.voz.data.InstalledApps
import dev.auxdesign.voz.data.LogEntry
import dev.auxdesign.voz.data.VozSettings
import dev.auxdesign.voz.engine.EngineProvider
import dev.auxdesign.voz.util.Strings
import dev.auxdesign.voz.util.effectiveLang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One voice turn: listen → route (grammar, app intent, engines) → confirm sensitive steps → execute → speak.
 * Tapping the mic (or saying a kill phrase) while busy stops everything. [turn] is the only state the UI renders.
 */
class VoiceSession(
    private val scope: CoroutineScope,
    private val speech: SpeechController,
    private val tts: TtsController,
    private val earcons: Earcons,
    private val settings: StateFlow<VozSettings?>,
    private val strings: Strings,
    private val router: Router,
    private val engines: EngineProvider,
    private val executor: ActionExecutor,
    private val apps: InstalledApps,
    private val log: ActionLog,
    /** True while TalkBack (touch exploration) is on: its own speech must not become the command. */
    private val screenReaderOn: () -> Boolean = { false },
    val text: ActionText = ActionText(strings),
) {
    enum class Phase { IDLE, LISTENING, WORKING, SPEAKING, CONFIRMING }

    private val phaseState = MutableStateFlow(Phase.IDLE)
    private val turnState = MutableStateFlow<Turn>(Turn.Idle)
    private val speechOutputState = MutableStateFlow(true)
    val phase: StateFlow<Phase> = phaseState.asStateFlow()
    val turn: StateFlow<Turn> = turnState.asStateFlow()
    val micOpen: StateFlow<Boolean> = speech.micOpen
    val partial: StateFlow<String> = speech.partial
    val level: StateFlow<Float?> = speech.level

    /** False after speech output failed (until it works again): the UI shows why VOZ is silent. */
    val speechOutput: StateFlow<Boolean> = speechOutputState.asStateFlow()

    private var job: Job? = null
    private var pendingAnswer: CompletableDeferred<Boolean>? = null

    /** What the current turn heard, as it may be shown (dictation reduced to its length). */
    private var shownHeard: String? = null

    val isBusy: Boolean get() = job?.isActive == true

    fun toggle() {
        if (isBusy) stop() else start()
    }

    fun start() {
        if (isBusy) return
        shownHeard = null
        turnState.value = Turn.Listening
        val turn = scope.launch(start = CoroutineStart.LAZY) {
            val lang = lang()
            try {
                // TalkBack announces the tapped button; let it finish before the mic opens. TODO(verify) the delay on device.
                if (screenReaderOn()) delay(SCREEN_READER_SETTLE_MS)
                val texts = listen(lang) ?: return@launch
                handle(texts, lang)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An unexpected error must never end in silence.
                log.add(LogEntry.Kind.FAILED, strings.get(lang, R.string.log_unexpected))
                earcons.error()
                end(lang, Turn.Finished(shownHeard, Turn.Result.FAILED), R.string.say_action_failed)
            } finally {
                // A cancelled turn can finish late (e.g. a network call): never reset a newer turn's state.
                if (job === coroutineContext[Job]) phaseState.value = Phase.IDLE
            }
        }
        job = turn
        turn.start()
    }

    fun stop() {
        job?.cancel()
        job = null
        pendingAnswer?.cancel()
        pendingAnswer = null
        speech.cancel()
        tts.stop()
        phaseState.value = Phase.IDLE
        val current = turnState.value
        if (current !is Turn.Finished && current !is Turn.Idle) turnState.value = Turn.Finished(shownHeard, Turn.Result.STOPPED)
    }

    /** The on-screen Yes/No buttons answer the pending "¿Confirmo?" instead of the voice. */
    fun answerConfirmation(yes: Boolean) {
        val pending = pendingAnswer ?: return
        pending.complete(yes)
        tts.stop()
        speech.cancel()
    }

    private fun current(): VozSettings = settings.value ?: VozSettings()

    private fun lang(): Lang = effectiveLang(current().language)

    private suspend fun listen(lang: Lang): List<String>? {
        phaseState.value = Phase.LISTENING
        return when (val result = speech.listen(lang, onReady = earcons::start)) {
            is SpeechController.Result.Heard -> {
                earcons.stop()
                result.texts
            }
            SpeechController.Result.NoMatch -> {
                end(lang, Turn.Finished(null, Turn.Result.NOT_HEARD), R.string.say_not_heard)
                null
            }
            is SpeechController.Result.Error -> {
                earcons.error()
                log.add(LogEntry.Kind.FAILED, strings.get(lang, R.string.log_speech_error))
                when {
                    result.isPermission -> end(lang, Turn.Finished(null, Turn.Result.MIC_DENIED), R.string.say_mic_error)
                    result.isUnavailable -> end(lang, Turn.Finished(null, Turn.Result.SPEECH_UNAVAILABLE), R.string.say_speech_unavailable)
                    else -> end(lang, Turn.Finished(null, Turn.Result.SPEECH_ERROR), R.string.say_speech_error)
                }
                null
            }
        }
    }

    private suspend fun handle(texts: List<String>, lang: Lang) {
        val utterance = Utterance(texts.first(), lang, texts.drop(1))
        shownHeard = utterance.text
        turnState.value = Turn.Understanding(utterance.text)
        phaseState.value = Phase.WORKING
        val s = current()
        // Tree walks, Keystore reads and network calls stay off the main thread.
        val outcome = withContext(Dispatchers.Default) {
            router.route(utterance, executor.snapshot(), apps.matcher(), engines.planners(s))
        }
        // Only a recognised command is stored word for word; dictation and anything not understood may hold private text.
        val verbatim = outcome is Router.Outcome.Kill ||
            (outcome is Router.Outcome.Ready && outcome.plan.steps.none { it is Action.Type })
        val redacted = strings.get(lang, R.string.log_heard_redacted, utterance.text.length)
        log.add(LogEntry.Kind.HEARD, if (verbatim) utterance.text else redacted)
        if (outcome is Router.Outcome.Ready && !verbatim) shownHeard = redacted
        when (outcome) {
            Router.Outcome.Kill -> end(lang, Turn.Finished(shownHeard, Turn.Result.STOPPED), R.string.say_stopped)
            is Router.Outcome.Ready -> run(outcome, lang, s)
            is Router.Outcome.Rejected -> {
                log.add(LogEntry.Kind.BLOCKED, strings.get(lang, R.string.log_rejected, text.source(outcome.source, lang)))
                end(lang, Turn.Finished(shownHeard, Turn.Result.BLOCKED, source = outcome.source), R.string.say_rejected)
            }
            is Router.Outcome.NotUnderstood -> end(
                lang,
                Turn.Finished(shownHeard, Turn.Result.NOT_UNDERSTOOD),
                strings.get(lang, R.string.say_not_understood_heard, utterance.text),
            )
            is Router.Outcome.EngineError -> {
                log.add(LogEntry.Kind.FAILED, strings.get(lang, R.string.log_cloud_error))
                earcons.error()
                end(lang, Turn.Finished(shownHeard, Turn.Result.CLOUD_ERROR, source = PlanSource.CLOUD), R.string.say_cloud_error)
            }
        }
    }

    private suspend fun run(ready: Router.Outcome.Ready, lang: Lang, s: VozSettings) {
        val plan = ready.plan
        val heard = shownHeard.orEmpty()
        log.add(LogEntry.Kind.PLAN, strings.get(lang, R.string.log_plan, text.describe(plan.steps, lang), text.source(plan.source, lang)))
        var lastReply: String? = null
        for ((index, step) in plan.steps.withIndex()) {
            fun finished(result: Turn.Result, reply: String?) =
                Turn.Finished(heard, result, plan.steps, plan.source, failedAt = index, reply = reply)
            if (step is Action.Stop) {
                end(lang, Turn.Finished(heard, Turn.Result.STOPPED, plan.steps, plan.source), R.string.say_stopped)
                return
            }
            val acting = Turn.Acting(heard, plan.steps, plan.source, index)
            turnState.value = acting
            val needed = ready.confirmations.firstOrNull { it.stepIndex == index }
            if (needed != null && !confirm(lang, confirmPhrase(step, needed.target, lang))) {
                log.add(LogEntry.Kind.BLOCKED, strings.get(lang, R.string.log_declined, text.describe(step, lang)))
                end(lang, finished(Turn.Result.CANCELLED, null), R.string.say_cancelled)
                return
            }
            turnState.value = acting
            phaseState.value = Phase.WORKING
            val env = ExecEnv(lang, s, confirmedTarget = needed?.target, ranker = engines.ranker()) { what -> confirm(lang, what) }
            val (before, stamp) = withContext(Dispatchers.Default) { executor.foregroundPackage() to executor.windowStamp() }
            val result = withContext(Dispatchers.Default) { executor.execute(step, env) }
            if (result is ExecResult.Done && index < plan.steps.lastIndex) {
                withContext(Dispatchers.Default) { executor.awaitSettled(step, before, stamp) }
            }
            when (result) {
                is ExecResult.Done -> {
                    log.add(LogEntry.Kind.DONE, text.describe(step, lang))
                    val said = result.say
                    if (said != null) {
                        lastReply = said
                        say(lang, said)
                    } else {
                        // Scroll, back, volume… have no sentence: a distinct tone says it worked.
                        earcons.done()
                    }
                }
                is ExecResult.Failed -> {
                    log.add(LogEntry.Kind.FAILED, strings.get(lang, R.string.log_step_failed, text.describe(step, lang), result.say))
                    earcons.error()
                    end(lang, finished(Turn.Result.FAILED, result.say), result.say)
                    return
                }
                is ExecResult.Cancelled -> {
                    log.add(LogEntry.Kind.BLOCKED, strings.get(lang, R.string.log_declined, text.describe(step, lang)))
                    end(lang, finished(Turn.Result.CANCELLED, result.say), result.say)
                    return
                }
            }
        }
        turnState.value = Turn.Finished(heard, Turn.Result.DONE, plan.steps, plan.source, reply = lastReply)
        if (plan.source == PlanSource.CLOUD && plan.say != null) {
            // Screen text can steer the cloud reply: speak it only if plain, and never in VOZ's own voice.
            val reply = CloudReply.speakable(plan.say)
            if (reply != null) {
                say(lang, strings.get(lang, R.string.say_cloud_reply, reply))
            } else {
                log.add(LogEntry.Kind.BLOCKED, strings.get(lang, R.string.log_cloud_withheld))
            }
        }
    }

    /**
     * Asks "¿Confirmo?" and waits for yes/no by voice (twice at most) or by the on-screen buttons, whichever
     * comes first. Anything unclear, a timeout or a question that could not be spoken means no.
     */
    private suspend fun confirm(lang: Lang, what: String): Boolean {
        val before = turnState.value
        turnState.value = Turn.Confirming(shownHeard.orEmpty(), what)
        val answer = CompletableDeferred<Boolean>()
        pendingAnswer = answer
        try {
            val yes = coroutineScope {
                val voice = launch { answer.complete(askByVoice(lang, what)) }
                val result = answer.await()
                voice.cancel()
                result
            }
            if (yes) log.add(LogEntry.Kind.CONFIRM, strings.get(lang, R.string.log_confirmed, what))
            return yes
        } finally {
            if (pendingAnswer === answer) pendingAnswer = null
            if (turnState.value is Turn.Confirming) turnState.value = before
        }
    }

    private suspend fun askByVoice(lang: Lang, what: String): Boolean {
        repeat(CONFIRM_ATTEMPTS) { attempt ->
            phaseState.value = Phase.CONFIRMING
            val asked = if (attempt == 0) say(lang, strings.get(lang, R.string.say_confirm, what)) else say(lang, R.string.say_confirm_again)
            // A "sí" to a question the user never heard is not consent.
            if (!asked) return false
            phaseState.value = Phase.CONFIRMING
            val result = speech.listen(lang, onReady = earcons::start)
            earcons.stop()
            val heard = (result as? SpeechController.Result.Heard)?.texts?.firstOrNull()
            when (ConfirmationReply.parse(heard)) {
                ConfirmationReply.Reply.YES -> return true
                ConfirmationReply.Reply.NO -> return false
                ConfirmationReply.Reply.UNKNOWN -> Unit
            }
        }
        return false
    }

    /** The action named in the question: "tap “Send”" or "search Google for “…”". */
    private fun confirmPhrase(step: Action, target: String, lang: Lang): String = when (step) {
        is Action.Search -> strings.get(lang, R.string.confirm_search, step.query, text.targetName(lang, step.target))
        else -> strings.get(lang, R.string.confirm_tap, target)
    }

    /** Shows the final state first, then says [reply], so screen and voice agree. */
    private suspend fun end(lang: Lang, finished: Turn.Finished, @StringRes reply: Int) = end(lang, finished, strings.get(lang, reply))

    private suspend fun end(lang: Lang, finished: Turn.Finished, reply: String) {
        turnState.value = finished.copy(reply = reply)
        say(lang, reply)
    }

    private suspend fun say(lang: Lang, @StringRes id: Int): Boolean = say(lang, strings.get(lang, id))

    /** Speaks [text]. False if speech output failed (an error tone plays instead; the text stays on screen). */
    private suspend fun say(lang: Lang, text: String): Boolean {
        phaseState.value = Phase.SPEAKING
        if (tts.speak(text, lang, current().speechRate)) {
            speechOutputState.value = true
            return true
        }
        earcons.error()
        if (speechOutputState.value) log.add(LogEntry.Kind.FAILED, strings.get(lang, R.string.log_speech_output))
        speechOutputState.value = false
        return false
    }

    private companion object {
        const val CONFIRM_ATTEMPTS = 2
        const val SCREEN_READER_SETTLE_MS = 700L
    }
}
