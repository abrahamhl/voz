package dev.auxdesign.voz.voice

import androidx.annotation.StringRes
import dev.auxdesign.voz.R
import dev.auxdesign.voz.a11y.ActionExecutor
import dev.auxdesign.voz.a11y.ExecEnv
import dev.auxdesign.voz.a11y.ExecResult
import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.Lang
import dev.auxdesign.voz.core.model.Plan
import dev.auxdesign.voz.core.model.PlanSource
import dev.auxdesign.voz.core.model.Utterance
import dev.auxdesign.voz.core.route.Router
import dev.auxdesign.voz.core.safety.ConfirmationReply
import dev.auxdesign.voz.data.ActionLog
import dev.auxdesign.voz.data.InstalledApps
import dev.auxdesign.voz.data.LogEntry
import dev.auxdesign.voz.data.VozSettings
import dev.auxdesign.voz.engine.EngineProvider
import dev.auxdesign.voz.util.Strings
import dev.auxdesign.voz.util.effectiveLang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One voice turn: listen → route (grammar, app intent, engines) → confirm sensitive steps → execute → speak.
 * Tapping the mic (or saying a kill phrase) while busy stops everything.
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
) {
    enum class Phase { IDLE, LISTENING, WORKING, SPEAKING, CONFIRMING }

    private val phaseState = MutableStateFlow(Phase.IDLE)
    private val heardState = MutableStateFlow("")
    private val replyState = MutableStateFlow("")
    val phase: StateFlow<Phase> = phaseState.asStateFlow()
    val heard: StateFlow<String> = heardState.asStateFlow()
    val reply: StateFlow<String> = replyState.asStateFlow()

    private var job: Job? = null

    val isBusy: Boolean get() = job?.isActive == true

    fun toggle() {
        if (isBusy) stop() else start()
    }

    fun start() {
        if (isBusy) return
        val turn = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val lang = lang()
                val texts = listen(lang) ?: return@launch
                handle(texts, lang)
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
        speech.cancel()
        tts.stop()
        phaseState.value = Phase.IDLE
    }

    private fun current(): VozSettings = settings.value ?: VozSettings()

    private fun lang(): Lang = effectiveLang(current().language)

    private suspend fun listen(lang: Lang): List<String>? {
        phaseState.value = Phase.LISTENING
        earcons.start()
        val result = speech.listen(lang)
        earcons.stop()
        return when (result) {
            is SpeechController.Result.Heard -> result.texts
            SpeechController.Result.NoMatch -> {
                say(lang, R.string.say_not_heard)
                null
            }
            is SpeechController.Result.Error -> {
                earcons.error()
                log.add(LogEntry.Kind.FAILED, "speech error ${result.code}")
                say(
                    lang,
                    when {
                        result.isPermission -> R.string.say_mic_error
                        result.isUnavailable -> R.string.say_speech_unavailable
                        else -> R.string.say_speech_error
                    },
                )
                null
            }
        }
    }

    private suspend fun handle(texts: List<String>, lang: Lang) {
        val utterance = Utterance(texts.first(), lang, texts.drop(1))
        heardState.value = utterance.text
        phaseState.value = Phase.WORKING
        val s = current()
        // Tree walks, Keystore reads and network calls stay off the main thread.
        val outcome = withContext(Dispatchers.Default) {
            router.route(utterance, executor.snapshot(), apps.matcher(), engines.planners(s))
        }
        val dictation = outcome is Router.Outcome.Ready && outcome.plan.steps.any { it is Action.Type }
        log.add(LogEntry.Kind.HEARD, if (dictation) "[dictation, ${utterance.text.length} chars]" else utterance.text)
        when (outcome) {
            Router.Outcome.Kill -> say(lang, R.string.say_stopped)
            is Router.Outcome.Ready -> run(outcome, lang, s)
            is Router.Outcome.Rejected -> {
                log.add(LogEntry.Kind.BLOCKED, "${outcome.source}: ${outcome.errors.joinToString("; ")}")
                say(lang, R.string.say_rejected)
            }
            is Router.Outcome.NotUnderstood -> say(lang, R.string.say_not_understood)
            is Router.Outcome.EngineError -> {
                log.add(LogEntry.Kind.FAILED, "${outcome.engineId}: ${outcome.error}")
                say(lang, R.string.say_cloud_error)
            }
        }
    }

    private suspend fun run(ready: Router.Outcome.Ready, lang: Lang, s: VozSettings) {
        val plan = ready.plan
        log.add(LogEntry.Kind.PLAN, describe(plan))
        for ((index, step) in plan.steps.withIndex()) {
            if (step is Action.Stop) {
                say(lang, R.string.say_stopped)
                return
            }
            val needed = ready.confirmations.firstOrNull { it.stepIndex == index }
            if (needed != null && !confirm(lang, strings.get(lang, R.string.confirm_tap, needed.target))) {
                log.add(LogEntry.Kind.BLOCKED, "user declined: ${describe(step)}")
                say(lang, R.string.say_cancelled)
                return
            }
            phaseState.value = Phase.WORKING
            val env = ExecEnv(lang, s, confirmedTarget = needed?.target, ranker = engines.ranker()) { what -> confirm(lang, what) }
            val before = withContext(Dispatchers.Default) { executor.foregroundPackage() }
            val result = withContext(Dispatchers.Default) { executor.execute(step, env) }
            if (result is ExecResult.Done && index < plan.steps.lastIndex) {
                withContext(Dispatchers.Default) { executor.awaitSettled(step, before) }
            }
            when (result) {
                is ExecResult.Done -> {
                    log.add(LogEntry.Kind.DONE, describe(step))
                    result.say?.let { say(lang, it) }
                }
                is ExecResult.Failed -> {
                    log.add(LogEntry.Kind.FAILED, "${describe(step)}: ${result.say}")
                    say(lang, result.say)
                    return
                }
                is ExecResult.Cancelled -> {
                    log.add(LogEntry.Kind.BLOCKED, "user declined: ${describe(step)}")
                    say(lang, result.say)
                    return
                }
            }
        }
        if (plan.source == PlanSource.CLOUD) plan.say?.let { say(lang, it) }
    }

    /** Asks "¿Confirmo?" and listens for yes/no (twice at most). Anything unclear means no. */
    private suspend fun confirm(lang: Lang, what: String): Boolean {
        repeat(CONFIRM_ATTEMPTS) { attempt ->
            phaseState.value = Phase.CONFIRMING
            if (attempt == 0) say(lang, strings.get(lang, R.string.say_confirm, what)) else say(lang, R.string.say_confirm_again)
            phaseState.value = Phase.CONFIRMING
            earcons.start()
            val result = speech.listen(lang)
            earcons.stop()
            val answer = (result as? SpeechController.Result.Heard)?.texts?.firstOrNull()
            when (ConfirmationReply.parse(answer)) {
                ConfirmationReply.Reply.YES -> {
                    log.add(LogEntry.Kind.CONFIRM, what)
                    return true
                }
                ConfirmationReply.Reply.NO -> return false
                ConfirmationReply.Reply.UNKNOWN -> Unit
            }
        }
        return false
    }

    private suspend fun say(lang: Lang, @StringRes id: Int) = say(lang, strings.get(lang, id))

    private suspend fun say(lang: Lang, text: String) {
        phaseState.value = Phase.SPEAKING
        replyState.value = text
        tts.speak(text, lang, current().speechRate)
    }

    private fun describe(plan: Plan): String = plan.steps.joinToString(" → ") { describe(it) } + " [${plan.source.name.lowercase()}]"

    /** Log-friendly description. Dictated text is redacted to its length. */
    private fun describe(action: Action): String = when (action) {
        is Action.OpenApp -> "open_app(${action.app})"
        is Action.Search -> "search(${action.query}, ${action.target.wire})"
        is Action.Tap -> "tap(${action.label})"
        is Action.Type -> "type(${action.text.length} chars)"
        is Action.ReadComments -> "read_comments(${action.mode.wire}${action.topic?.let { ", $it" }.orEmpty()})"
        else -> action.verb.wire
    }

    private companion object {
        const val CONFIRM_ATTEMPTS = 2
    }
}
