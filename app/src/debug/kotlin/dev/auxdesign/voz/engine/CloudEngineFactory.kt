package dev.auxdesign.voz.engine

import dev.auxdesign.voz.core.route.DecisionEngine
import dev.auxdesign.voz.data.SecretStore

/** Builds the BYOK cloud engine; it is only offered when the user saved a key. */
class CloudEngineFactory(
    private val secrets: SecretStore,
    private val transport: () -> HttpTransport = { OkHttpTransport() },
) {
    private val gemini by lazy {
        GeminiEngine(apiKey = { secrets.get(SecretStore.GEMINI_API_KEY) }, transport = transport())
    }

    fun engine(): DecisionEngine? = gemini.takeIf { it.isAvailable }
}
