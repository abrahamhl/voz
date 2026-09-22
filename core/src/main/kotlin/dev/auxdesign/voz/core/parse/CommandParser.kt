package dev.auxdesign.voz.core.parse

import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.Lang
import dev.auxdesign.voz.core.model.Utterance
import dev.auxdesign.voz.core.safety.KillPhrase
import dev.auxdesign.voz.core.text.Normalize

/**
 * Offline command parser (no AI). Tries the utterance language first, then the other two:
 * people mix languages ("abre Settings", "open de YouTube app").
 */
class CommandParser {

    fun parse(utterance: Utterance): Action? = parse(utterance.text, utterance.lang)

    fun parse(text: String, lang: Lang): Action? {
        if (text.isBlank()) return null
        if (KillPhrase.matches(text)) return Action.Stop
        val folded = Normalize.fold(text)
        for (l in listOf(lang) + Lang.entries.filter { it != lang }) {
            for (rule in rulesFor(l)) {
                val match = rule.regex.matchEntire(folded) ?: continue
                rule.build(Captures(match, text, folded))?.let { return it }
            }
        }
        return null
    }

    private fun rulesFor(lang: Lang): List<Rule> = when (lang) {
        Lang.ES -> EsGrammar.rules
        Lang.EN -> EnGrammar.rules
        Lang.NL -> NlGrammar.rules
    }
}
