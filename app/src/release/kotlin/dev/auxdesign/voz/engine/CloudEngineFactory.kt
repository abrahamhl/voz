package dev.auxdesign.voz.engine

import dev.auxdesign.voz.core.route.DecisionEngine
import dev.auxdesign.voz.data.SecretStore

/**
 * Release builds do not ship the cloud planner at all: no Gemini client, no HTTP dependency, no
 * network permission. This stub matches the constructor of the debug implementation so that
 * [dev.auxdesign.voz.AppGraph] is variant-agnostic, and it can never return an engine.
 */
class CloudEngineFactory(@Suppress("UNUSED_PARAMETER") secrets: SecretStore) {
    fun engine(): DecisionEngine? = null
}
