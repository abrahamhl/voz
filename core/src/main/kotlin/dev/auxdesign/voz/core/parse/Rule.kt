package dev.auxdesign.voz.core.parse

import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.SearchTarget
import dev.auxdesign.voz.core.text.Normalize
import dev.auxdesign.voz.core.text.Payload

/**
 * One grammar rule. [pattern] is a regex over folded text (see [Normalize.fold]) where a literal
 * space means "one or more spaces". Named group `p` is the payload, `t` a search target.
 * Rules are anchored and wrapped with optional politeness fillers in ES/EN/NL.
 */
internal class Rule(pattern: String, val build: (Captures) -> Action?) {
    val regex = Regex("^\\s*$PREFIX(?:${pattern.replace(" ", "\\s+")})$SUFFIX\\s*$")

    private companion object {
        const val PREFIX =
            "(?:(?:oye|hey|ok|okay|vale|eh|hee)\\s+)?(?:voz\\s+)?" +
                "(?:(?:por\\s+favor|please|alsjeblieft|puedes|podrias|quiero\\s+que|can\\s+you|could\\s+you|" +
                "would\\s+you|kun\\s+je|kan\\s+je|wil\\s+je|zou\\s+je)\\s+)*"
        const val SUFFIX =
            "(?:\\s+(?:por\\s+favor|please|alsjeblieft|graag|gracias|thanks|thank\\s+you|dank\\s+je|dankjewel|" +
                "para\\s+mi|for\\s+me|voor\\s+mij|ahora|now|nu))*"
    }
}

/** Access to regex groups, returning payloads with their ORIGINAL casing and accents. */
internal class Captures(private val match: MatchResult, private val original: String, private val folded: String) {
    fun raw(name: String): String? {
        val group = match.groups[name] ?: return null
        return Payload.clean(original.substring(group.range.first, group.range.last + 1)).ifEmpty { null }
    }

    /** Dictated text: keeps inner and trailing punctuation ("¿vale?"), only drops a leading ":" or quotes. */
    fun dictated(name: String): String? {
        val group = match.groups[name] ?: return null
        var end = group.range.last + 1
        while (end < original.length && folded[end] == ' ' && !original[end].isWhitespace()) end++
        return Payload.unquote(original.substring(group.range.first, end)).ifEmpty { null }
    }

    fun norm(name: String): String? = match.groups[name]?.value?.let(Normalize::collapse)?.ifEmpty { null }
}

internal object Targets {
    fun of(norm: String?): SearchTarget {
        val t = norm ?: return SearchTarget.GOOGLE
        return when {
            "youtube" in t || "you tube" in t || "yutub" in t -> SearchTarget.YOUTUBE
            "map" in t || "kaart" in t -> SearchTarget.MAPS
            "play" in t || "tienda" in t || "store" in t || "winkel" in t -> SearchTarget.PLAY
            else -> SearchTarget.GOOGLE
        }
    }

    fun search(c: Captures): Action? = c.raw("p")?.let { Action.Search(it, of(c.norm("t"))) }

    fun searchIn(c: Captures, target: SearchTarget): Action? = c.raw("p")?.let { Action.Search(it, target) }

    fun open(c: Captures): Action? = c.raw("p")?.let { Action.OpenApp(Payload.stripAppWords(it)) }
}
