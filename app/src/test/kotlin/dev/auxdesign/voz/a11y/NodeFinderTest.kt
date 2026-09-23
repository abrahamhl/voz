package dev.auxdesign.voz.a11y

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
    override val isPassword: Boolean = false,
    override val scrollsOnlyHorizontally: Boolean = false,
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
    fun `prefers the focused editable field, else the only one`() {
        assertSame(focusedField, (NodeFinder.editTarget(root) as NodeFinder.EditTarget.Found).node)
        val noFocus = FakeNode(children = listOf(field))
        assertSame(field, (NodeFinder.editTarget(noFocus) as NodeFinder.EditTarget.Found).node)
        assertEquals(NodeFinder.EditTarget.None, NodeFinder.editTarget(FakeNode(children = listOf(FakeNode(text = "Hola")))))
    }

    @Test
    fun `two fields and none focused is ambiguous, never the first one`() {
        val login = FakeNode(
            children = listOf(
                FakeNode(text = "Email", isEditable = true, isShowingHint = true, bounds = Box(0, 0, 100, 50)),
                FakeNode(text = "Password", isEditable = true, isShowingHint = true, isPassword = true, bounds = Box(0, 60, 100, 110)),
            ),
        )
        assertEquals(NodeFinder.EditTarget.Ambiguous(2), NodeFinder.editTarget(login))
    }

    private fun row(title: String, top: Int) = FakeNode(
        bounds = Box(0, top, 1000, top + 100),
        children = listOf(
            FakeNode(text = title, bounds = Box(0, top, 800, top + 100)),
            FakeNode(description = "Eliminar", isClickable = true, bounds = Box(800, top, 1000, top + 100)),
        ),
    )

    @Test
    fun `one Eliminar per row is ambiguous`() {
        val cart = FakeNode(children = listOf(row("Auriculares", 0), row("Cargador", 100)))
        val result = NodeFinder.resolveTap(cart, "eliminar")
        val ambiguous = assertInstanceOf(NodeFinder.TapResolution.Ambiguous::class.java, result)
        assertEquals(2, ambiguous.count)
    }

    @Test
    fun `the same button reached through its text and its description is not ambiguous`() {
        val label = FakeNode(text = "Enviar", bounds = Box(0, 0, 50, 50))
        val button = FakeNode(description = "Enviar", isClickable = true, bounds = Box(0, 0, 100, 50), children = listOf(label))
        val header = FakeNode(text = "Enviar", bounds = Box(0, 200, 300, 250))
        val result = NodeFinder.resolveTap(FakeNode(children = listOf(header, button)), "enviar")
        val found = assertInstanceOf(NodeFinder.TapResolution.Found::class.java, result)
        assertSame(button, found.target.pressed)
    }

    @Test
    fun `after the spoken yes the target is pressed only if the screen still shows it`() {
        val before = FakeNode(children = listOf(row("Auriculares", 0)))
        val confirmed = (NodeFinder.resolveTap(before, "eliminar") as NodeFinder.TapResolution.Found).target

        val unchanged = FakeNode(children = listOf(row("Auriculares", 0)))
        assertEquals(confirmed.bounds, NodeFinder.recheck(confirmed, unchanged, "eliminar")?.bounds)

        // While the user answered, a row was added, the list scrolled, or the button changed.
        val moved = FakeNode(children = listOf(row("Cargador", 0), row("Auriculares", 100)))
        assertNull(NodeFinder.recheck(confirmed, moved, "eliminar"))
        val shifted = FakeNode(children = listOf(row("Auriculares", 300)))
        assertNull(NodeFinder.recheck(confirmed, shifted, "eliminar"))
        val relabelled = FakeNode(children = listOf(FakeNode(description = "Eliminar cuenta", isClickable = true, bounds = confirmed.bounds)))
        assertNull(NodeFinder.recheck(confirmed, relabelled, "eliminar"))
        assertNull(NodeFinder.recheck(confirmed, null, "eliminar"))
    }

    @Test
    fun `largest visible scrollable wins`() {
        assertSame(bigList, NodeFinder.largestScrollable(root))
    }

    @Test
    fun `vertical list beats a same-size horizontal pager and deeper node wins ties`() {
        val list = FakeNode(isScrollable = true, bounds = Box(0, 0, 1000, 2000))
        val pager = FakeNode(isScrollable = true, scrollsOnlyHorizontally = true, bounds = Box(0, 0, 1000, 2000), children = listOf(list))
        assertSame(list, NodeFinder.largestScrollable(FakeNode(children = listOf(pager))))

        val inner = FakeNode(isScrollable = true, bounds = Box(0, 0, 500, 500))
        val outer = FakeNode(isScrollable = true, bounds = Box(0, 0, 500, 500), children = listOf(inner))
        assertSame(inner, NodeFinder.largestScrollable(FakeNode(children = listOf(outer))))

        val carousel = FakeNode(isScrollable = true, scrollsOnlyHorizontally = true, bounds = Box(0, 0, 1000, 3000))
        val feed = FakeNode(isScrollable = true, bounds = Box(0, 0, 1000, 1000))
        assertSame(feed, NodeFinder.largestScrollable(FakeNode(children = listOf(carousel, feed))))
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
