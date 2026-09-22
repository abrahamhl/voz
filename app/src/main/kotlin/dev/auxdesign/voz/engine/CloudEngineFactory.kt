package dev.auxdesign.voz.engine

import dev.auxdesign.voz.core.route.DecisionEngine
import dev.auxdesign.voz.data.SecretStore

/** Builds the BYOK cloud engine when the user has saved a key. The Gemini engine lands in the next change. */
@Suppress("UnusedPrivateProperty")
class CloudEngineFactory(private val secrets: SecretStore) {
    fun engine(): DecisionEngine? = null
}
