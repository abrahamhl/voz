package dev.auxdesign.voz.engine

import dev.auxdesign.voz.core.plan.LocalPlanner
import dev.auxdesign.voz.core.rank.Comment
import dev.auxdesign.voz.core.rank.CommentQuery
import dev.auxdesign.voz.core.rank.CommentRanker
import dev.auxdesign.voz.core.rank.Ranking
import dev.auxdesign.voz.core.route.DecisionEngine
import dev.auxdesign.voz.core.route.EngineResult
import dev.auxdesign.voz.core.route.PlanRequest
import dev.auxdesign.voz.data.VozSettings

/** Default brain: offline heuristics and local comment ranking. Always available, never uses the network. */
class LocalEngine(
    private val planner: LocalPlanner = LocalPlanner(),
    private val ranker: CommentRanker = CommentRanker(),
) : DecisionEngine {
    override val id: String = "local"
    override val isAvailable: Boolean = true

    override suspend fun plan(request: PlanRequest): EngineResult = planner.plan(request)

    override suspend fun rankComments(comments: List<Comment>, query: CommentQuery, limit: Int): Ranking =
        ranker.rank(comments, query, limit)
}

/**
 * Placeholder for Jev (TypeSafe AI). Jev is in early access and its API is not available to this
 * project, so this engine is permanently disabled and never makes network calls.
 */
class JevEngine : DecisionEngine {
    override val id: String = "jev"
    override val isAvailable: Boolean = false

    override suspend fun plan(request: PlanRequest): EngineResult = EngineResult.NoPlan(UNAVAILABLE)

    override suspend fun rankComments(comments: List<Comment>, query: CommentQuery, limit: Int): Ranking = Ranking(emptyList())

    private companion object {
        const val UNAVAILABLE = "Jev engine is not available (early access)"
    }
}

/** Chooses which brains to ask, in order. The cloud is used only when the user turned it on and saved a key. */
class EngineProvider(
    private val local: LocalEngine,
    private val jev: JevEngine,
    private val cloud: () -> DecisionEngine?,
) {
    fun planners(settings: VozSettings): List<DecisionEngine> = buildList {
        if (settings.cloudEnabled) cloud()?.takeIf { it.isAvailable }?.let { add(it) }
        if (jev.isAvailable) add(jev)
        add(local)
    }

    /** Comment ranking always stays on the phone (comments are never uploaded). */
    fun ranker(): DecisionEngine = local
}
