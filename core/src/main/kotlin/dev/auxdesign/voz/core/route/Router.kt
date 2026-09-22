package dev.auxdesign.voz.core.route

import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.Plan
import dev.auxdesign.voz.core.model.PlanSource
import dev.auxdesign.voz.core.model.ScreenSnapshot
import dev.auxdesign.voz.core.model.SearchTarget
import dev.auxdesign.voz.core.model.Utterance
import dev.auxdesign.voz.core.parse.AppMatcher
import dev.auxdesign.voz.core.parse.CommandParser
import dev.auxdesign.voz.core.plan.PlanValidator
import dev.auxdesign.voz.core.rank.Comment
import dev.auxdesign.voz.core.rank.CommentQuery
import dev.auxdesign.voz.core.rank.Ranking
import dev.auxdesign.voz.core.safety.KillPhrase
import dev.auxdesign.voz.core.text.Normalize
import dev.auxdesign.voz.core.text.Payload

data class PlanRequest(val utterance: Utterance, val screen: ScreenSnapshot)

sealed interface EngineResult {
    data class Planned(val plan: Plan) : EngineResult
    data class NoPlan(val reason: String) : EngineResult
    data class Failed(val error: String) : EngineResult
}

/**
 * A "brain" that decides what to do when the offline grammar does not understand, and ranks
 * comments. Implementations: LocalEngine (offline, default), GeminiEngine (BYOK cloud),
 * JevEngine (disabled stub).
 */
interface DecisionEngine {
    val id: String
    val isAvailable: Boolean
    suspend fun plan(request: PlanRequest): EngineResult
    suspend fun rankComments(comments: List<Comment>, query: CommentQuery, limit: Int): Ranking
}

/** Local rules → app intent → decision engines (in order), then the validator. */
class Router(
    private val parser: CommandParser = CommandParser(),
    private val validator: PlanValidator = PlanValidator(),
) {
    sealed interface Outcome {
        data object Kill : Outcome
        data class Ready(val plan: Plan, val confirmations: List<PlanValidator.Confirmation>) : Outcome
        data class Rejected(val source: PlanSource, val errors: List<String>) : Outcome
        data class NotUnderstood(val reason: String?) : Outcome
        data class EngineError(val engineId: String, val error: String) : Outcome
    }

    suspend fun route(
        utterance: Utterance,
        screen: ScreenSnapshot = ScreenSnapshot.EMPTY,
        apps: AppMatcher? = null,
        engines: List<DecisionEngine> = emptyList(),
    ): Outcome {
        if (KillPhrase.matches(utterance.text)) return Outcome.Kill

        for (text in listOf(utterance.text) + utterance.alternatives.take(MAX_ALTERNATIVES)) {
            parser.parse(text, utterance.lang)?.let { return finalize(Plan(listOf(it), PlanSource.LOCAL_PARSER), utterance) }
        }

        if (apps != null) appIntent(utterance.text, apps)?.let { return finalize(Plan(listOf(it), PlanSource.APP_INTENT), utterance) }

        var lastError: Outcome? = null
        var lastReason: String? = null
        for (engine in engines) {
            if (!engine.isAvailable) continue
            when (val result = engine.plan(PlanRequest(utterance, screen))) {
                is EngineResult.Planned -> return finalize(result.plan, utterance)
                is EngineResult.NoPlan -> lastReason = result.reason
                is EngineResult.Failed -> lastError = Outcome.EngineError(engine.id, result.error)
            }
        }
        return lastError ?: Outcome.NotUnderstood(lastReason)
    }

    /** "YouTube" → open it; "YouTube cats" → search cats on YouTube. */
    fun appIntent(text: String, apps: AppMatcher): Action? {
        val folded = Normalize.fold(text)
        val spans = WORD.findAll(folded).map { it.range }.toList()
        if (spans.isEmpty()) return null
        for (k in minOf(MAX_APP_WORDS, spans.size) downTo 1) {
            val prefix = spans.take(k).joinToString(" ") { folded.substring(it) }
            val match = apps.best(prefix, minScore = APP_INTENT_MIN_SCORE) ?: continue
            if (k == spans.size) return Action.OpenApp(match.app.label)
            val target = SearchTarget.forPackage(match.app.packageName) ?: return null
            val rest = Payload.clean(text.substring(spans[k].first))
            return if (rest.isEmpty()) Action.OpenApp(match.app.label) else Action.Search(rest, target)
        }
        return null
    }

    private fun finalize(plan: Plan, utterance: Utterance): Outcome =
        when (val v = validator.validate(plan, utterance)) {
            is PlanValidator.Result.Valid -> Outcome.Ready(v.plan, v.confirmations)
            is PlanValidator.Result.Invalid -> Outcome.Rejected(plan.source, v.errors)
        }

    private companion object {
        val WORD = Regex("\\S+")
        const val MAX_ALTERNATIVES = 2
        const val MAX_APP_WORDS = 3
        const val APP_INTENT_MIN_SCORE = 0.9
    }
}
