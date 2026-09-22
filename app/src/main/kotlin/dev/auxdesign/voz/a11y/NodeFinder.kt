package dev.auxdesign.voz.a11y

import dev.auxdesign.voz.core.model.ScreenNode
import dev.auxdesign.voz.core.parse.AppMatcher
import dev.auxdesign.voz.core.text.Normalize

data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val area: Long get() = maxOf(0, right - left).toLong() * maxOf(0, bottom - top)

    companion object {
        val EMPTY = Box(0, 0, 0, 0)
    }
}

/** A view on screen. Backed by AccessibilityNodeInfo on device and by fakes in unit tests. */
interface UiNode {
    val text: String?
    val description: String?
    val viewId: String?
    val className: String?
    val packageName: String?
    val isClickable: Boolean
    val isEnabled: Boolean
    val isEditable: Boolean
    val isScrollable: Boolean
    val isVisible: Boolean
    val isFocused: Boolean
    val isShowingHint: Boolean
    val bounds: Box
    val parent: UiNode?
    val children: List<UiNode>

    fun click(): Boolean
    fun setText(value: String): Boolean
    fun scroll(forward: Boolean): Boolean
}

/** Pure matching logic over a [UiNode] tree. */
object NodeFinder {
    const val DEFAULT_MIN_SCORE = 0.75
    const val STRICT_MIN_SCORE = 0.9
    private const val MAX_NODES = 1500
    private const val MAX_DEPTH = 60

    data class Hit(val node: UiNode, val label: String, val score: Double)

    /** Best visible node whose text or content description matches [label]. */
    fun findByLabel(root: UiNode, label: String, minScore: Double = DEFAULT_MIN_SCORE): Hit? {
        val query = Normalize.forMatch(label)
        if (query.isEmpty()) return null
        var best: Hit? = null
        walk(root) { node ->
            if (!node.isVisible) return@walk
            for (raw in listOfNotNull(node.text, node.description)) {
                val candidate = Normalize.forMatch(raw)
                if (candidate.isEmpty()) continue
                var score = score(query, candidate)
                if (score > 0.0 && clickableAncestor(node) != null) score += ACTIONABLE_BONUS
                val current = best
                if (current == null || score > current.score) best = Hit(node, Normalize.collapse(raw), score)
            }
        }
        return best?.takeIf { it.score >= minScore }
    }

    /** Exact beats prefix beats suffix beats whole-word containment beats fuzzy. */
    fun score(query: String, candidate: String): Double = when {
        query == candidate -> 1.0
        candidate.startsWith("$query ") -> 0.92
        candidate.endsWith(" $query") -> 0.88
        " $query " in " $candidate " -> 0.85
        else -> AppMatcher.similarity(query, candidate) * 0.9
    }

    fun clickableAncestor(node: UiNode): UiNode? =
        generateSequence(node) { it.parent }.take(MAX_DEPTH).firstOrNull { it.isClickable && it.isEnabled }

    /** The focused editable field, else the first visible editable field. */
    fun focusedEditable(root: UiNode): UiNode? {
        var firstEditable: UiNode? = null
        var focused: UiNode? = null
        walk(root) { node ->
            if (focused == null && node.isEditable && node.isVisible) {
                if (node.isFocused) focused = node
                if (firstEditable == null) firstEditable = node
            }
        }
        return focused ?: firstEditable
    }

    fun largestScrollable(root: UiNode): UiNode? {
        var best: UiNode? = null
        walk(root) { node ->
            if (node.isScrollable && node.isVisible && node.bounds.area > (best?.bounds?.area ?: -1L)) best = node
        }
        return best
    }

    /** Flattened, visible, labelled (or actionable) nodes in document order for the core layer. */
    fun flatten(root: UiNode, max: Int = 400): List<ScreenNode> {
        val out = ArrayList<ScreenNode>()
        walk(root) { node ->
            if (out.size >= max || !node.isVisible) return@walk
            val hasLabel = !node.text.isNullOrBlank() || !node.description.isNullOrBlank()
            if (hasLabel || node.isEditable || node.isScrollable) {
                out += ScreenNode(
                    text = node.text,
                    description = node.description,
                    viewId = node.viewId,
                    className = node.className,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    scrollable = node.isScrollable,
                )
            }
        }
        return out
    }

    /** Pre-order depth-first walk, bounded in size and depth. */
    fun walk(root: UiNode, visit: (UiNode) -> Unit) {
        val stack = ArrayDeque<Pair<UiNode, Int>>()
        stack.addLast(root to 0)
        var count = 0
        while (stack.isNotEmpty() && count < MAX_NODES) {
            val (node, depth) = stack.removeLast()
            count++
            visit(node)
            if (depth < MAX_DEPTH) {
                val kids = node.children
                for (i in kids.indices.reversed()) stack.addLast(kids[i] to depth + 1)
            }
        }
    }

    private const val ACTIONABLE_BONUS = 0.01
}
