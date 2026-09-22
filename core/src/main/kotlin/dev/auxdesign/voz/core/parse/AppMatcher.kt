package dev.auxdesign.voz.core.parse

import dev.auxdesign.voz.core.model.AppEntry
import dev.auxdesign.voz.core.text.Normalize

/** Fuzzy matcher from a spoken app name to an installed launchable app. */
class AppMatcher(apps: List<AppEntry>) {

    data class Match(val app: AppEntry, val score: Double)

    private val indexed = apps.map { it to Normalize.forMatch(it.label) }.filter { it.second.isNotEmpty() }

    fun best(query: String, minScore: Double = DEFAULT_MIN_SCORE): Match? {
        val candidates = AppAliases.expand(query)
        if (candidates.isEmpty()) return null
        var best: Match? = null
        for ((app, label) in indexed) {
            for (q in candidates) {
                val s = score(q, label)
                if (best == null || s > best.score) best = Match(app, s)
            }
        }
        return best?.takeIf { it.score >= minScore }
    }

    companion object {
        const val DEFAULT_MIN_SCORE = 0.62

        /** Similarity in [0, 1] between a normalized query and a normalized app label. */
        fun score(q: String, label: String): Double {
            if (q.isEmpty() || label.isEmpty()) return 0.0
            if (q == label) return 1.0
            val qc = q.replace(" ", "")
            val lc = label.replace(" ", "")
            if (qc == lc) return 0.98
            if (qc.length >= 3 && lc.startsWith(qc)) return 0.9
            val labelTokens = label.split(' ')
            val queryTokens = q.split(' ')
            if (queryTokens.all { it in labelTokens }) return 0.88
            if (lc.length >= 3 && qc.startsWith(lc)) return 0.85
            if (qc.length >= 3 && labelTokens.any { it.startsWith(qc) }) return 0.8
            val whole = similarity(qc, lc)
            val perToken = labelTokens.maxOf { similarity(qc, it) } * 0.95
            return maxOf(whole, perToken)
        }

        fun similarity(a: String, b: String): Double {
            val max = maxOf(a.length, b.length)
            if (max == 0) return 1.0
            return 1.0 - levenshtein(a, b).toDouble() / max
        }

        fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                }
                val tmp = prev
                prev = cur
                cur = tmp
            }
            return prev[b.length]
        }
    }
}

/** Common spoken names (ES/EN/NL + phonetic spellings) for system and popular apps. */
object AppAliases {
    private val GROUPS: List<List<String>> = listOf(
        listOf("settings", "ajustes", "configuracion", "opciones", "instellingen"),
        listOf("camera", "camara"),
        listOf("phone", "telefono", "telefoon", "llamadas", "bellen"),
        listOf("messages", "mensajes", "berichten", "sms"),
        listOf("gmail", "correo", "email", "mail", "e mail"),
        listOf("maps", "google maps", "mapas", "mapa", "kaarten"),
        listOf("play store", "google play", "tienda", "playstore", "store", "winkel"),
        listOf("photos", "google photos", "fotos", "galeria", "gallery", "foto s"),
        listOf("clock", "reloj", "alarma", "klok", "wekker"),
        listOf("calculator", "calculadora", "rekenmachine"),
        listOf("calendar", "calendario", "agenda"),
        listOf("chrome", "navegador", "browser", "internet"),
        listOf("contacts", "contactos", "contacten"),
        listOf("whatsapp", "guasap", "wasap", "whatsap", "wassap", "watsap"),
        listOf("youtube", "yutub", "yutu", "you tube"),
    )

    private val INDEX: Map<String, List<String>> = buildMap {
        for (group in GROUPS) for (name in group) put(name, group)
    }

    /** The normalized query first, then its aliases (if any). */
    fun expand(query: String): List<String> {
        val q = Normalize.forMatch(query)
        if (q.isEmpty()) return emptyList()
        val aliases = INDEX[q].orEmpty().filter { it != q }
        return listOf(q) + aliases
    }
}
