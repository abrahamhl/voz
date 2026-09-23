package dev.auxdesign.voz.engine

import dev.auxdesign.voz.core.rank.Comment
import dev.auxdesign.voz.core.rank.CommentQuery
import dev.auxdesign.voz.core.rank.Ranking
import dev.auxdesign.voz.core.route.DecisionEngine
import dev.auxdesign.voz.core.route.EngineResult
import dev.auxdesign.voz.core.route.PlanRequest
import dev.auxdesign.voz.data.VozSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EngineProviderTest {

    private object Cloud : DecisionEngine {
        override val id = "cloud"
        override val isAvailable = true
        override suspend fun plan(request: PlanRequest): EngineResult = EngineResult.NoPlan("test")
        override suspend fun rankComments(comments: List<Comment>, query: CommentQuery, limit: Int) = Ranking(emptyList())
    }

    private val provider = EngineProvider(LocalEngine(), JevEngine()) { Cloud }

    @Test
    fun `on-phone brains are asked before the cloud`() {
        val allowed = VozSettings(cloudEnabled = true, cloudConsent = VozSettings.CLOUD_CONSENT_VERSION)
        assertEquals(listOf("local", "cloud"), provider.planners(allowed).map { it.id })
    }

    @Test
    fun `no cloud without the accepted privacy notice`() {
        assertEquals(listOf("local"), provider.planners(VozSettings(cloudEnabled = true)).map { it.id })
    }

    @Test
    fun `release builds never ask the cloud, whatever the settings say`() {
        val release = EngineProvider(LocalEngine(), JevEngine(), cloudBuilt = false) { Cloud }
        val allowed = VozSettings(cloudEnabled = true, cloudConsent = VozSettings.CLOUD_CONSENT_VERSION)
        assertEquals(listOf("local"), release.planners(allowed).map { it.id })
    }
}
