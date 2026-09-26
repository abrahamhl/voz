package dev.auxdesign.voz.core.plan

import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.Plan
import dev.auxdesign.voz.core.model.PlanSource
import dev.auxdesign.voz.core.parse.AppMatcher
import dev.auxdesign.voz.core.route.EngineResult
import dev.auxdesign.voz.core.route.PlanRequest
import dev.auxdesign.voz.core.text.Normalize

/**
 * Offline heuristics used by the local engine for utterances the grammar did not parse.
 * Today: saying the exact name of something visible taps it ("Suscribirse", "Next").
 */
class LocalPlanner {

    fun plan(request: PlanRequest): EngineResult {
        val said = Normalize.forMatch(request.utterance.text)
        if (said.isEmpty()) return EngineResult.NoPlan("empty utterance")
        val command = ACTION_WORDS.firstOrNull { said == it || said.startsWith("$it ") }
        val query = command?.let { said.removePrefix(it).trim() }.orEmpty().ifEmpty { said }
        var bestLabel: String? = null
        var bestScore = 0.0
        var secondScore = 0.0
        for (node in request.screen.nodes) {
            val label = node.label ?: continue
            val score = AppMatcher.score(query, Normalize.forMatch(label))
            if (score > bestScore) {
                secondScore = bestScore
                bestScore = score
                bestLabel = label
            } else if (score > secondScore) {
                secondScore = score
            }
        }
        val label = bestLabel
        val explicitAction = ACTION_WORDS.any { said == it || said.startsWith("$it ") }
        val exact = label != null && Normalize.forMatch(label) == query
        if (label != null && bestScore >= MIN_LABEL_SCORE && (exact || explicitAction) && bestScore - secondScore >= MIN_MARGIN) {
            return EngineResult.Planned(Plan(listOf(Action.Tap(Normalize.collapse(label))), PlanSource.LOCAL_ENGINE))
        }
        return EngineResult.NoPlan("no local rule matched")
    }

    private companion object {
        const val MIN_LABEL_SCORE = 0.9
        const val MIN_MARGIN = 0.03
        val ACTION_WORDS = setOf(
            "tap", "press", "click", "open", "select", "choose", "pulsa", "pulsar", "toca", "tocar",
            "abre", "abrir", "selecciona", "seleccionar", "tik", "klik", "openen", "selecteer",
        )
    }
}
