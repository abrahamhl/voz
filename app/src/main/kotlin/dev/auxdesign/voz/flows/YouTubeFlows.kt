package dev.auxdesign.voz.flows

import dev.auxdesign.voz.a11y.NodeFinder
import dev.auxdesign.voz.a11y.UiNode
import dev.auxdesign.voz.a11y.VozAccessibilityService
import dev.auxdesign.voz.core.rank.Comment
import dev.auxdesign.voz.core.rank.CommentExtractor
import kotlinx.coroutines.delay

/**
 * YouTube demo flows driven through the accessibility tree.
 * Labels come from the YouTube app UI in ES/EN/NL; they are not an API and can change.
 * Everything here is untested on a real device until the owner runs the manual checklist.
 */
class YouTubeFlows {

    enum class Fullscreen { DONE, NOT_YOUTUBE, NOT_FOUND }

    sealed interface Comments {
        data object NotYouTube : Comments
        data object PanelNotFound : Comments
        data class Collected(val comments: List<Comment>) : Comments
    }

    suspend fun fullscreen(service: VozAccessibilityService, enter: Boolean): Fullscreen {
        if (!isYouTube(service)) return Fullscreen.NOT_YOUTUBE
        repeat(2) { attempt ->
            val root = service.root() ?: return Fullscreen.NOT_FOUND
            val exit = findAny(root, EXIT_FULLSCREEN)
            if (enter && exit != null) return Fullscreen.DONE
            val control = if (enter) findAny(root, ENTER_FULLSCREEN) else exit
            if (control != null) {
                if (press(service, control)) return Fullscreen.DONE
            }
            if (attempt == 0) {
                revealPlayerControls(service, root)
                delay(REVEAL_MS)
            }
        }
        return Fullscreen.NOT_FOUND
    }

    suspend fun collectComments(service: VozAccessibilityService, depth: Int): Comments {
        if (!isYouTube(service)) return Comments.NotYouTube
        if (!panelOpen(service)) {
            if (!openPanel(service)) {
                // The comments teaser can sit below the fold under the video description.
                service.root()?.let { NodeFinder.largestScrollable(it)?.scroll(forward = true) }
                delay(SETTLE_MS)
                if (!openPanel(service)) return Comments.PanelNotFound
            }
            delay(PANEL_MS)
        }
        val all = ArrayList<Comment>()
        val passes = depth.coerceIn(1, MAX_PASSES)
        for (pass in 0 until passes) {
            val root = service.root() ?: break
            all += CommentExtractor.extract(NodeFinder.flatten(root))
            if (pass == passes - 1) break
            val list = NodeFinder.largestScrollable(root)
            if (list?.scroll(forward = true) != true) service.swipeVertical(fingerUp = true)
            delay(SCROLL_MS)
        }
        return Comments.Collected(all)
    }

    /** The active window decides; the last window event is only a fallback when the tree is unavailable. */
    private fun isYouTube(service: VozAccessibilityService): Boolean =
        (service.root()?.packageName ?: service.foregroundPackage) == PACKAGE

    private fun panelOpen(service: VozAccessibilityService): Boolean {
        val root = service.root() ?: return false
        return findAny(root, COMMENT_BOX) != null
    }

    private suspend fun openPanel(service: VozAccessibilityService): Boolean {
        val root = service.root() ?: return false
        val teaser = findAny(root, COMMENTS) ?: return false
        return press(service, teaser)
    }

    /** Taps the player to show its controls. Never taps blindly: without a player node, nothing happens. */
    private suspend fun revealPlayerControls(service: VozAccessibilityService, root: UiNode) {
        var player: UiNode? = null
        NodeFinder.walk(root) { node ->
            val id = node.viewId.orEmpty()
            if (player == null && node.isVisible && PLAYER_IDS.any { it in id }) player = node
        }
        val target = player ?: return
        service.tapAt(target.bounds.centerX, target.bounds.centerY)
    }

    private suspend fun press(service: VozAccessibilityService, hit: NodeFinder.Hit): Boolean {
        val clickable = NodeFinder.clickableAncestor(hit.node)
        if (clickable != null) return clickable.click()
        val b = hit.node.bounds
        return service.tapAt(b.centerX, b.centerY)
    }

    private fun findAny(root: UiNode, labels: List<String>): NodeFinder.Hit? =
        labels.firstNotNullOfOrNull { NodeFinder.findByLabel(root, it, NodeFinder.STRICT_MIN_SCORE) }

    companion object {
        const val PACKAGE = "com.google.android.youtube"
        private const val REVEAL_MS = 600L
        private const val SETTLE_MS = 700L
        private const val PANEL_MS = 1_200L
        private const val SCROLL_MS = 800L
        private const val MAX_PASSES = 15

        val ENTER_FULLSCREEN = listOf(
            "enter full screen", "enter fullscreen", "full screen", "fullscreen",
            "pantalla completa", "activar pantalla completa", "ver en pantalla completa",
            "volledig scherm", "volledig scherm openen", "schermvullend", "schermvullende weergave", "beeldvullend",
        )
        val EXIT_FULLSCREEN = listOf(
            "exit full screen", "exit fullscreen", "salir de pantalla completa", "salir del modo de pantalla completa",
            "volledig scherm sluiten", "volledig scherm verlaten", "schermvullende weergave sluiten", "schermvullende weergave afsluiten",
        )
        val COMMENTS = listOf("comments", "comentarios", "reacties", "comment", "comentario", "reactie")
        val COMMENT_BOX = listOf("add a comment", "añade un comentario", "añadir un comentario", "voeg een reactie toe", "reageer")
        private val PLAYER_IDS = listOf("watch_player", "player_view", "player_fragment", "inline_player", "player_container")
    }
}
