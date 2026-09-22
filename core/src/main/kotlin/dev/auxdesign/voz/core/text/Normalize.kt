package dev.auxdesign.voz.core.text

import java.text.Normalizer

/** Text folding helpers shared by the parser, matchers and safety checks. */
object Normalize {
    private val SPACES = Regex("\\s+")

    /**
     * Length-preserving fold: lowercase, accents stripped, every non letter/digit becomes a space.
     * Because it maps one char to one char, regex group ranges on the folded text are valid
     * ranges on the original text (used to extract payloads with their original casing).
     */
    fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            val base = if (c.code < 128) c else baseChar(c)
            val lower = base.lowercaseChar()
            sb.append(if (lower.isLetterOrDigit()) lower else ' ')
        }
        return sb.toString()
    }

    /** Folded and whitespace-collapsed form, for comparisons. */
    fun forMatch(s: String): String = collapse(fold(s))

    fun tokens(s: String?): List<String> =
        if (s.isNullOrBlank()) emptyList() else forMatch(s).split(' ').filter { it.isNotEmpty() }

    fun collapse(s: String): String = SPACES.replace(s, " ").trim()

    private fun baseChar(c: Char): Char {
        if (c.isSurrogate()) return ' '
        val decomposed = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
        return decomposed.firstOrNull() ?: c
    }
}

/** Cleans a spoken payload (app name, query, label, dictated text). */
object Payload {
    private const val EDGE = "\"'«»“”‘’.,;:!?¿¡()[]{}"
    private val APP_WORDS = setOf("app", "apps", "aplicacion", "application", "applicatie")

    fun clean(s: String): String = Normalize.collapse(s).trim { it in EDGE || it.isWhitespace() }

    /** Trims whitespace, a leading ":" and matching surrounding quotes; keeps other punctuation. */
    fun unquote(s: String): String {
        var t = Normalize.collapse(s).trimStart(':', ' ')
        val quotes = listOf('"' to '"', '«' to '»', '“' to '”', '\'' to '\'', '‘' to '’')
        for ((open, close) in quotes) {
            if (t.length >= 2 && t.first() == open && t.last() == close) t = t.substring(1, t.length - 1).trim()
        }
        return t.trimEnd(',', ';', ':', ' ')
    }

    /** "la app de WhatsApp" → "WhatsApp", "Maps app" → "Maps". */
    fun stripAppWords(s: String): String {
        val words = clean(s).split(' ').toMutableList()
        while (words.size > 1 && Normalize.forMatch(words.first()) in APP_WORDS) words.removeAt(0)
        while (words.size > 1 && Normalize.forMatch(words.last()) in APP_WORDS) words.removeAt(words.lastIndex)
        return words.joinToString(" ")
    }
}
