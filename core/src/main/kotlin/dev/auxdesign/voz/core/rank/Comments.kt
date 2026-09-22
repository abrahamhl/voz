package dev.auxdesign.voz.core.rank

import dev.auxdesign.voz.core.model.CommentMode
import dev.auxdesign.voz.core.model.ScreenNode
import dev.auxdesign.voz.core.text.Normalize
import kotlin.math.ln
import kotlin.math.roundToLong

data class Comment(val text: String, val likes: Int? = null)

data class CommentQuery(val mode: CommentMode, val topic: String? = null)

/** [fallback] is true when the requested ranking found nothing and popular comments were used instead. */
data class Ranking(val items: List<Comment>, val fallback: Boolean = false)

/** Parses YouTube-style like counts in ES/EN/NL: "1,2 mil", "3.4K", "12 k", "1,5 M", "2 mln", "1.234". */
object LikeCountParser {
    private val NUMBER = Regex(
        "(\\d{1,3}(?:[.,\\u00A0 ]\\d{3})+|\\d+(?:[.,]\\d+)?)\\s*(k|mil|d|dzd|duizend|thousand|m|mln|mill|millones|million|mio|mn|bn)?\\b",
        RegexOption.IGNORE_CASE,
    )
    private val ONLY_COUNT = Regex(
        "^\\s*\\d[\\d.,\\u00A0 ]*\\s*(k|mil|d|dzd|m|mln|mill|mio|mn)?\\.?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    /** True when the whole string is just a count ("15", "1,2 k"). */
    fun isBareCount(text: String?): Boolean = text != null && ONLY_COUNT.matches(text)

    /** First count found in [text], or null. */
    fun parse(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val m = NUMBER.find(text) ?: return null
        val digits = m.groupValues[1]
        val suffix = m.groupValues[2].lowercase()
        val multiplier = when (suffix) {
            "k", "mil", "d", "dzd", "duizend", "thousand" -> 1_000.0
            "m", "mln", "mill", "millones", "million", "mio", "mn" -> 1_000_000.0
            "bn" -> 1_000_000_000.0
            else -> 1.0
        }
        val value = toNumber(digits, hasSuffix = multiplier > 1.0) ?: return null
        val result = (value * multiplier).roundToLong()
        return if (result >= Int.MAX_VALUE) Int.MAX_VALUE else result.toInt()
    }

    private fun toNumber(raw: String, hasSuffix: Boolean): Double? {
        val s = raw.replace('\u00A0', ' ')
        // "1.234" / "1,234" / "1 234" with 3-digit groups and no suffix = thousands separators.
        val grouped = Regex("^\\d{1,3}(?:[., ]\\d{3})+$").matches(s)
        return if (grouped && !hasSuffix) {
            s.filter { it.isDigit() }.toDoubleOrNull()
        } else {
            s.replace(" ", "").replace(',', '.').toDoubleOrNull()
        }
    }
}

/** Laughter and "this is funny" signals in ES/EN/NL, plus emoji. */
object FunnyLexicon {
    private val LAUGH = listOf(
        Regex("\\b(?:j[aeiou]){2,}j?\\b"),
        Regex("\\b(?:h[aeiou]){2,}h?\\b"),
        Regex("\\bx+d+\\b"),
        Regex("\\b(?:lol|lool|lmao|lmfao|rofl|lolol)\\b"),
    )
    private val EMOJI = listOf("😂", "🤣", "😆", "😹", "💀", "😅", "😄", "😁")
    private val WORDS = listOf(
        "gracioso", "graciosa", "graciosisimo", "me meo", "me muero", "muerto de risa", "me parto", "que risa", "buenisimo",
        "funny", "hilarious", "im dead", "i m dead", "cracking up", "laughing", "dying",
        "grappig", "hilarisch", "lachen", "gelachen", "dubbel gelegen", "lig dubbel", "gierend",
    )

    fun score(text: String): Int {
        var score = EMOJI.sumOf { e -> text.windowed(e.length).count { it == e } }
        val lower = text.lowercase()
        score += LAUGH.sumOf { it.findAll(lower).count() }
        val folded = " ${Normalize.forMatch(text)} "
        score += WORDS.count { " $it " in folded }
        return score
    }
}

/** Local, offline comment ranking: popular = likes, funny = laughter lexicon, topic = keyword match. */
class CommentRanker {

    fun rank(comments: List<Comment>, query: CommentQuery, limit: Int = 3): Ranking {
        val unique = dedupe(comments)
        return when (query.mode) {
            CommentMode.POPULAR -> Ranking(popular(unique).take(limit))
            CommentMode.FUNNY -> {
                val funny = unique
                    .map { it to FunnyLexicon.score(it.text) }
                    .filter { it.second > 0 }
                    .sortedWith(compareByDescending<Pair<Comment, Int>> { it.second }.thenByDescending { it.first.likes ?: -1 })
                    .map { it.first }
                if (funny.isEmpty()) Ranking(popular(unique).take(limit), fallback = true) else Ranking(funny.take(limit))
            }
            CommentMode.TOPIC -> {
                val keys = keywords(query.topic)
                if (keys.isEmpty()) return Ranking(emptyList())
                val hits = unique
                    .map { it to topicScore(it.text, keys) }
                    .filter { it.second > 0.0 }
                    .sortedWith(
                        compareByDescending<Pair<Comment, Double>> { it.second + likeBonus(it.first) }
                    )
                    .map { it.first }
                Ranking(hits.take(limit))
            }
        }
    }

    fun dedupe(comments: List<Comment>): List<Comment> {
        val byKey = LinkedHashMap<String, Comment>()
        for (c in comments) {
            val key = Normalize.forMatch(c.text)
            if (key.isEmpty()) continue
            val prev = byKey[key]
            byKey[key] = if (prev == null) c else prev.copy(likes = maxOf(prev.likes ?: -1, c.likes ?: -1).takeIf { it >= 0 })
        }
        return byKey.values.toList()
    }

    private fun popular(list: List<Comment>) = list.sortedByDescending { it.likes ?: -1 }

    private fun likeBonus(c: Comment): Double = ln((c.likes ?: 0).toDouble() + 1.0) * 0.01

    private fun topicScore(text: String, keys: List<String>): Double {
        val tokens = Normalize.tokens(text)
        return keys.count { k -> tokens.any { t -> sameWord(t, k) } }.toDouble()
    }

    /** Cheap stemming: "drums" ~ "drummer", "gato" ~ "gatos", "musica" ~ "music". */
    private fun sameWord(a: String, b: String): Boolean {
        if (a == b) return true
        val common = a.commonPrefixWith(b).length
        return common >= 4 && common >= 0.6 * minOf(a.length, b.length)
    }

    private fun keywords(topic: String?): List<String> =
        Normalize.tokens(topic).filter { it.length >= 3 && it !in STOPWORDS }.distinct()

    private companion object {
        val STOPWORDS = setOf(
            "the", "and", "about", "with", "this", "that", "for", "los", "las", "del", "que", "con", "sobre", "una", "uno",
            "por", "para", "het", "een", "van", "over", "met", "die", "dat", "voor",
        )
    }
}

/**
 * Pulls comments and like counts out of a flattened comments panel.
 * Heuristic (untested on real YouTube layouts): long text nodes are comments; a like count
 * (bare number or a "like this comment along with N" description) attaches to the latest comment.
 */
object CommentExtractor {
    private val CHROME = setOf(
        "reply", "replies", "responder", "respuestas", "antwoorden", "antwoord", "comments", "comentarios", "reacties",
        "sort by", "sort comments", "ordenar", "sorteren", "top comments", "newest first", "mas recientes",
        "principales", "nieuwste eerst", "add a comment", "anade un comentario", "voeg een reactie toe", "like",
        "dislike", "me gusta", "no me gusta", "share", "compartir", "delen", "subscribe", "suscribirse", "abonneren",
        "read more", "leer mas", "meer lezen", "show less", "mostrar menos", "minder weergeven", "more", "mas", "meer",
    )
    private val TIME_AGO = Regex(
        "\\b(?:ago|hace|geleden)\\b|\\b\\d+\\s*(?:s|min|h|d|w|mo|y|sec|seconds?|minutes?|hours?|days?|weeks?|months?|years?|" +
            "segundos?|minutos?|horas?|dias?|semanas?|meses|mes|anos?|seconden|minuten|uur|dagen|weken|maanden|jaar)\\b",
    )
    private val REPLIES = Regex("\\b\\d+\\s+(?:replies|reply|respuestas|respuesta|antwoorden|antwoord)\\b")
    private val LIKE_WORDS = listOf("like", "gusta", "leuk")

    fun extract(nodes: List<ScreenNode>): List<Comment> {
        val out = ArrayList<Comment>()
        var pendingIndex = -1
        for (node in nodes) {
            val likes = likeCount(node)
            if (likes != null) {
                if (pendingIndex >= 0 && out[pendingIndex].likes == null) out[pendingIndex] = out[pendingIndex].copy(likes = likes)
                continue
            }
            val text = node.text?.takeIf { it.isNotBlank() } ?: continue
            if (isComment(text)) {
                out += Comment(Normalize.collapse(text))
                pendingIndex = out.lastIndex
            }
        }
        return out
    }

    private fun likeCount(node: ScreenNode): Int? {
        val desc = node.description
        if (desc != null) {
            val d = Normalize.forMatch(desc)
            if (LIKE_WORDS.any { it in d } && !d.startsWith("dislike") && !d.contains("no me gusta")) {
                LikeCountParser.parse(desc)?.let { return it }
            }
        }
        val text = node.text ?: return null
        return if (LikeCountParser.isBareCount(text)) LikeCountParser.parse(text) else null
    }

    private fun isComment(text: String): Boolean {
        val n = Normalize.forMatch(text)
        if (n.length < 3 && FunnyLexicon.score(text) == 0) return false
        if (n in CHROME) return false
        if (text.trim().startsWith("@") && !text.trim().contains(' ')) return false
        if (REPLIES.containsMatchIn(n)) return false
        if (n.split(' ').size <= 4 && TIME_AGO.containsMatchIn(n)) return false
        if (n.all { it.isDigit() || it == ' ' }) return false
        return true
    }
}
