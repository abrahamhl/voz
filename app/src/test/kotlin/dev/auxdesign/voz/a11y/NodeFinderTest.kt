package dev.auxdesign.voz.a11y

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Fake view tree node for JVM tests. */
class FakeNode(
    override val text: String? = null,
    override val description: String? = null,
    override val viewId: String? = null,
    override val isClickable: Boolean = false,
    override val isEditable: Boolean = false,
    override val isScrollable: Boolean = false,
    override val isVisible: Boolean = true,
    override val isFocused: Boolean = false,
    override val isShowingHint: Boolean = false,
    override val bounds: Box = Box(0, 0, 100, 100),
    override val children: List<FakeNode> = emptyList(),
    override val isEnabled: Boolean = true,
) : UiNode {
    override val className: String? = null
    override val packageName: String? = "com.example"
    override var parent: UiNode? = null
    var clicks = 0
    var typed: String? = null

    init {
        children.forEach { it.parent = this }
    }

    override fun click(): Boolean {
        clicks++
        return isClickable
    }

    override fun setText(value: String): Boolean {
        typed = value
        return isEditable
    }

    override fun scroll(forward: Boolean): Boolean = isScrollable
}

class NodeFinderTest {

    private val subscribeLabel = FakeNode(text = "Suscribirse")
    private val subscribeButton = FakeNode(isClickable = true, children = listOf(subscribeLabel))
    private val hidden = FakeNode(text = "Enviar", isClickable = true, isVisible = false)
    private val send = FakeNode(description = "Enviar mensaje", isClickable = true)
    private val field = FakeNode(text = "Escribe un mensaje", isEditable = true, isShowingHint = true)
    private val focusedField = FakeNode(text = "hola", isEditable = true, isFocused = true)
    private val smallList = FakeNode(isScrollable = true, bounds = Box(0, 0, 100, 100))
    private val bigList = FakeNode(isScrollable = true, bounds = Box(0, 0, 1000, 2000), children = listOf(FakeNode(text = "Vídeo 1")))
    private val root = FakeNode(
        children = listOf(
            FakeNode(text = "YouTube"),
            subscribeButton,
            hidden,
            send,
            field,
            focusedField,
            smallList,
            bigList,
        ),
    )

    @Test
    fun `finds label text and resolves the clickable ancestor`() {
        val hit = NodeFinder.findByLabel(root, "suscribirse")!!
        assertSame(subscribeLabel, hit.node)
        assertSame(subscribeButton, NodeFinder.clickableAncestor(hit.node))
        assertEquals("Suscribirse", hit.label)
    }

    @Test
    fun `ignores invisible nodes and matches content descriptions by prefix`() {
        val hit = NodeFinder.findByLabel(root, "Enviar")!!
        assertSame(send, hit.node)
        assertTrue(hit.score in 0.9..0.95)
    }

    @Test
    fun `accent and case insensitive, fuzzy below threshold is rejected`() {
        assertSame(bigList.children.first(), NodeFinder.findByLabel(root, "VIDEO 1")!!.node)
        assertNull(NodeFinder.findByLabel(root, "comprar ahora"))
        assertNull(NodeFinder.findByLabel(root, "   "))
    }

    @Test
    fun `strict threshold rejects suffix matches`() {
        val exitOnly = FakeNode(children = listOf(FakeNode(description = "Exit full screen", isClickable = true)))
        assertNull(NodeFinder.findByLabel(exitOnly, "full screen", NodeFinder.STRICT_MIN_SCORE))
        assertEquals("Exit full screen", NodeFinder.findByLabel(exitOnly, "exit full screen", NodeFinder.STRICT_MIN_SCORE)?.label)
    }

    @Test
    fun `prefers the focused editable field`() {
        assertSame(focusedField, NodeFinder.focusedEditable(root))
        val noFocus = FakeNode(children = listOf(field))
        assertSame(field, NodeFinder.focusedEditable(noFocus))
    }

    @Test
    fun `largest visible scrollable wins`() {
        assertSame(bigList, NodeFinder.largestScrollable(root))
    }

    @Test
    fun `flatten keeps document order and skips invisible or empty nodes`() {
        val flat = NodeFinder.flatten(root)
        val labels = flat.mapNotNull { it.label }
        assertEquals(listOf("YouTube", "Suscribirse", "Enviar mensaje", "Escribe un mensaje", "hola", "Vídeo 1"), labels)
        assertTrue(flat.count { it.scrollable } == 2)
    }

    @Test
    fun `walk is bounded on huge trees`() {
        var deep = FakeNode(text = "leaf")
        repeat(200) { deep = FakeNode(children = listOf(deep)) }
        var visited = 0
        NodeFinder.walk(deep) { visited++ }
        assertTrue(visited in 1..61)
    }
}
