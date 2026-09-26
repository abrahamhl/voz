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
    val isPassword: Boolean

    /** True for pagers/carousels that only scroll sideways (a vertical "scroll down" must skip them). */
    val scrollsOnlyHorizontally: Boolean
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

    /**
     * What a tap would really press: the clickable ancestor ([pressed]; null = tap [hit]'s own bounds) and
     * [labels] = the matched text plus that container's own text and description.
     */
    data class TapTarget(val hit: Hit, val pressed: UiNode?, val labels: List<String>, val inner: List<String> = emptyList()) {
        val bounds: Box get() = (pressed ?: hit.node).bounds

        /**
         * Everything the press may trigger: [labels] plus the texts inside the pressed container ([inner]).
         * View-based buttons keep their text in child views, so "tap Visa" can press a "Pagar 49,99 €" row.
         */
        val riskLabels: List<String> get() = (labels + inner).distinct()

        /** Same labels at the same place. */
        fun sameAs(other: TapTarget): Boolean =
            bounds == other.bounds && labels.map(Normalize::forMatch) == other.labels.map(Normalize::forMatch)
    }

    sealed interface TapResolution {
        data class Found(val target: TapTarget) : TapResolution

        /** [count] different controls match equally well (e.g. one "Eliminar" per row); [target] is the first. */
        data class Ambiguous(val target: TapTarget, val count: Int) : TapResolution
        data object NotFound : TapResolution
    }

    sealed interface EditTarget {
        data class Found(val node: UiNode) : EditTarget

        /** Nothing focused and [count] visible fields: guessing could put a password into the e-mail field. */
        data class Ambiguous(val count: Int) : EditTarget
        data object None : EditTarget
    }

    /** Best visible node whose text or content description matches [label]. */
    fun findByLabel(root: UiNode, label: String, minScore: Double = DEFAULT_MIN_SCORE): Hit? {
        val query = Normalize.forMatch(label)
        if (query.isEmpty()) return null
        var best: Hit? = null
        walk(root) { node ->
            if (!isSafeForMatching(node)) return@walk
            for (raw in listOfNotNull(node.text, node.description)) {
                val score = scoreNode(query, raw, node) ?: continue
                val current = best
                if (current == null || score > current.score) best = Hit(node, Normalize.collapse(raw), score)
            }
        }
        return best?.takeIf { it.score >= minScore }
    }

    /** The control a tap on [label] would press, and whether other controls match it just as well. */
    fun resolveTap(root: UiNode, label: String, minScore: Double = DEFAULT_MIN_SCORE): TapResolution {
        val best = findByLabel(root, label, minScore) ?: return TapResolution.NotFound
        val target = tapTarget(best)
        val query = Normalize.forMatch(label)
        // Distinct places that would be pressed; the same control reached twice has the same bounds.
        val places = hashSetOf(target.bounds)
        walk(root) { node ->
            if (!isSafeForMatching(node)) return@walk
            val tie = listOfNotNull(node.text, node.description).any { raw ->
                (scoreNode(query, raw, node) ?: return@any false) >= best.score - TIE_EPSILON
            }
            if (tie) places += (clickableAncestor(node) ?: node).bounds
        }
        return if (places.size > 1) TapResolution.Ambiguous(target, places.size) else TapResolution.Found(target)
    }

    fun tapTarget(hit: Hit): TapTarget {
        val clickable = clickableAncestor(hit.node)
        val labels = listOfNotNull(hit.label, clickable?.text, clickable?.description)
            .map { Normalize.collapse(it) }
            .filter { it.isNotBlank() }
            .distinct()
        return TapTarget(hit, clickable, labels, clickable?.let { innerLabels(it) }.orEmpty())
    }

    /** Texts of the visible views inside [container], bounded (a whole-screen container must not flood the check). */
    fun innerLabels(container: UiNode, max: Int = MAX_INNER_LABELS, maxDepth: Int = MAX_INNER_DEPTH): List<String> {
        val out = ArrayList<String>()
        val stack = ArrayDeque<Triple<UiNode, Int, Boolean>>()
        container.children.asReversed().forEach { stack.addLast(Triple(it, 1, false)) }
        while (stack.isNotEmpty() && out.size < max) {
            val (node, depth, secretAncestor) = stack.removeLast()
            if (!node.isVisible || secretAncestor || node.isPassword) continue
            listOfNotNull(node.text, node.description).map { Normalize.collapse(it) }.filter { it.isNotBlank() }.forEach {
                if (out.size < max) out += it
            }
            if (depth < maxDepth) node.children.asReversed().forEach {
                stack.addLast(Triple(it, depth + 1, secretAncestor || node.isPassword))
            }
        }
        return out.distinct()
    }

    /**
     * Where to tap a node that has no clickable view (web content): the centre of its on-screen part.
     * Null when nothing of it is on screen; gestures with negative coordinates are rejected by Android.
     */
    fun visibleCenter(bounds: Box, screenWidth: Int, screenHeight: Int): Pair<Int, Int>? {
        val left = bounds.left.coerceAtLeast(0)
        val top = bounds.top.coerceAtLeast(0)
        val right = bounds.right.coerceAtMost(screenWidth)
        val bottom = bounds.bottom.coerceAtMost(screenHeight)
        if (right <= left || bottom <= top) return null
        return (left + right) / 2 to (top + bottom) / 2
    }

    /**
     * Answering "¿Confirmo?" takes seconds; meanwhile a list can rebind or a page reflow. Re-resolve [label] on
     * the fresh [rootNow] and return the target only if it is still the one the user confirmed.
     */
    fun recheck(confirmed: TapTarget, rootNow: UiNode?, label: String): TapTarget? {
        val now = rootNow?.let { resolveTap(it, label) } as? TapResolution.Found ?: return null
        return now.target.takeIf { it.sameAs(confirmed) }
    }

    private fun scoreNode(query: String, raw: String, node: UiNode): Double? {
        val candidate = Normalize.forMatch(raw)
        if (candidate.isEmpty()) return null
        val score = score(query, candidate)
        return if (score > 0.0 && clickableAncestor(node) != null) score + ACTIONABLE_BONUS else score
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

    /** Secret fields and their descendants are never eligible as spoken/tap targets. */
    private fun isSafeForMatching(node: UiNode): Boolean =
        node.isVisible && generateSequence(node) { it.parent }.take(MAX_DEPTH).none { it.isPassword }

    /** The focused editable field, else the only visible one; several and none focused is ambiguous. */
    fun editTarget(root: UiNode): EditTarget {
        val visible = ArrayList<UiNode>()
        var focused: UiNode? = null
        walk(root) { node ->
            if (focused == null && node.isEditable && node.isVisible) {
                if (node.isFocused) focused = node
                visible += node
            }
        }
        focused?.let { return EditTarget.Found(it) }
        return when (visible.size) {
            0 -> EditTarget.None
            1 -> EditTarget.Found(visible.single())
            else -> EditTarget.Ambiguous(visible.size)
        }
    }

    /**
     * The list to scroll: prefers vertical scrollers over horizontal pagers, then the largest area;
     * on equal area the deeper node wins (a pager and the list inside it often have the same bounds).
     */
    fun largestScrollable(root: UiNode): UiNode? {
        var best: UiNode? = null
        walk(root) { node ->
            if (!node.isScrollable || !node.isVisible) return@walk
            val current = best
            best = when {
                current == null -> node
                current.scrollsOnlyHorizontally && !node.scrollsOnlyHorizontally -> node
                !current.scrollsOnlyHorizontally && node.scrollsOnlyHorizontally -> current
                node.bounds.area >= current.bounds.area -> node
                else -> current
            }
        }
        return best
    }

    /** Flattened, visible, labelled (or actionable) nodes in document order for the core layer. */
    fun flatten(root: UiNode, max: Int = 400): List<ScreenNode> {
        val out = ArrayList<ScreenNode>()
        val stack = ArrayDeque<Pair<UiNode, Boolean>>()
        stack.addLast(root to false)
        while (stack.isNotEmpty() && out.size < max) {
            val (node, secretAncestor) = stack.removeLast()
            if (!node.isVisible || secretAncestor || node.isPassword) continue
            val hasLabel = !node.text.isNullOrBlank() || !node.description.isNullOrBlank()
            if (hasLabel || node.isEditable || node.isScrollable) {
                out += ScreenNode(
                    text = node.text,
                    description = node.description,
                    viewId = node.viewId,
                    className = node.className,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    password = node.isPassword,
                    scrollable = node.isScrollable,
                )
            }
            node.children.asReversed().forEach { stack.addLast(it to (secretAncestor || node.isPassword)) }
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
    private const val TIE_EPSILON = 1e-9
    private const val MAX_INNER_LABELS = 12
    private const val MAX_INNER_DEPTH = 4
}
