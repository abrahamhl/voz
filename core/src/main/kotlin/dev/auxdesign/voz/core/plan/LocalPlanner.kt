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
        var bestLabel: String? = null
        var bestScore = 0.0
        for (node in request.screen.nodes) {
            val label = node.label ?: continue
            val score = AppMatcher.score(said, Normalize.forMatch(label))
            if (score > bestScore) {
                bestScore = score
                bestLabel = label
            }
        }
        val label = bestLabel
        if (label != null && bestScore >= MIN_LABEL_SCORE) {
            return EngineResult.Planned(Plan(listOf(Action.Tap(Normalize.collapse(label))), PlanSource.LOCAL_ENGINE))
        }
        return EngineResult.NoPlan("no local rule matched")
    }

    private companion object {
        const val MIN_LABEL_SCORE = 0.9
    }
}
