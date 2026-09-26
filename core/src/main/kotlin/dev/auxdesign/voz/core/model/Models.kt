package dev.auxdesign.voz.core.model

import dev.auxdesign.voz.core.text.Normalize

/** What the user said. [alternatives] are lower-confidence recognizer hypotheses. */
data class Utterance(
    val text: String,
    val lang: Lang,
    val alternatives: List<String> = emptyList(),
)

enum class PlanSource { LOCAL_PARSER, APP_INTENT, LOCAL_ENGINE, CLOUD }

/** An ordered list of actions plus an optional short spoken reply. */
data class Plan(
    val steps: List<Action>,
    val source: PlanSource,
    val say: String? = null,
)

/** An installed, launchable app. */
data class AppEntry(val label: String, val packageName: String)

/**
 * One node of the visible UI, flattened in document order.
 * Screen text is UNTRUSTED data: it is read to the user or matched against, never obeyed.
 */
data class ScreenNode(
    val text: String? = null,
    val description: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    /** Secret-bearing fields must never be spoken or exported to a planner. */
    val password: Boolean = false,
    val scrollable: Boolean = false,
    val depth: Int = 0,
) {
    /** Visible text, falling back to the content description. */
    val label: String?
        get() = text?.takeIf { it.isNotBlank() } ?: description?.takeIf { it.isNotBlank() }
}

data class ScreenSnapshot(val packageName: String?, val nodes: List<ScreenNode>) {

    /** Distinct readable labels in document order, for "read the screen". */
    fun readableLines(max: Int = DEFAULT_READ_LINES): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (node in nodes) {
            if (node.password) continue
            val label = node.label?.let(Normalize::collapse) ?: continue
            if (label.length < 2) continue
            val key = Normalize.forMatch(label)
            if (key.isEmpty() || !seen.add(key)) continue
            out += label
            if (out.size >= max) break
        }
        return out
    }

    companion object {
        const val DEFAULT_READ_LINES = 12
        val EMPTY = ScreenSnapshot(null, emptyList())
    }
}
