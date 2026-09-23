package dev.auxdesign.voz.a11y

import dev.auxdesign.voz.core.safety.SensitiveTargetDetector
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HardeningTest {

    private val detector = SensitiveTargetDetector()

    @Test
    fun `a harmless label inside a pay row is checked against the row's own texts`() {
        val visa = FakeNode(text = "Visa ••42", bounds = Box(0, 0, 400, 100))
        val amount = FakeNode(text = "Pagar 49,99 €", bounds = Box(400, 0, 1000, 100))
        val row = FakeNode(isClickable = true, bounds = Box(0, 0, 1000, 100), children = listOf(visa, amount))
        val target = (NodeFinder.resolveTap(FakeNode(children = listOf(row)), "visa") as NodeFinder.TapResolution.Found).target
        assertFalse(target.labels.any(detector::isSensitive), "the matched label alone looks harmless")
        assertTrue(target.riskLabels.any(detector::isSensitive), "the row it presses says Pagar 49,99 €")
        assertEquals("Pagar 49,99 €", target.riskLabels.filter(detector::isSensitive).maxBy { it.length })
    }

    @Test
    fun `inner labels are bounded so a whole-screen container cannot flood the check`() {
        val many = FakeNode(isClickable = true, children = List(50) { FakeNode(text = "item $it") })
        assertEquals(5, NodeFinder.innerLabels(many, max = 5).size)
        val hidden = FakeNode(isClickable = true, children = listOf(FakeNode(text = "Comprar", isVisible = false)))
        assertTrue(NodeFinder.innerLabels(hidden).isEmpty())
    }

    @Test
    fun `web content is tapped on its visible part, never off screen`() {
        assertEquals(100 to 100, NodeFinder.visibleCenter(Box(-100, 50, 200, 150), 1080, 2400))
        assertEquals(1040 to 2350, NodeFinder.visibleCenter(Box(1000, 2300, 1200, 2600), 1080, 2400))
        assertNull(NodeFinder.visibleCenter(Box(-300, 0, -10, 100), 1080, 2400))
        assertNull(NodeFinder.visibleCenter(Box(0, 2500, 100, 2600), 1080, 2400))
    }

    @Test
    fun `forcing landscape keeps the user's auto-rotate and gives it back in portrait`() {
        val landscape = RotationPlan.to(landscape = true, autoRotate = 1, savedAutoRotate = null)
        assertEquals(RotationPlan.Change(autoRotate = 0, savedAutoRotate = 1), landscape)
        // "Full screen" twice: still landscape, the original choice is not overwritten by VOZ's own 0.
        assertEquals(landscape, RotationPlan.to(landscape = true, autoRotate = 0, savedAutoRotate = 1))
        assertEquals(RotationPlan.Change(autoRotate = 1, savedAutoRotate = null), RotationPlan.to(false, 0, 1))
        // Nothing saved: portrait leaves the user's setting as it is.
        assertEquals(RotationPlan.Change(autoRotate = 0, savedAutoRotate = null), RotationPlan.to(false, 0, null))
    }

    @Test
    fun `the next step waits until the screen reacted and went quiet`() = runTest {
        // Events arrive at 400 ms and 500 ms, then nothing.
        val events = { when { testScheduler.currentTime >= 500 -> 2L; testScheduler.currentTime >= 400 -> 1L; else -> 0L } }
        assertTrue(Settle.awaitChangeThenQuiet(events, before = 0L))
        assertTrue(testScheduler.currentTime in 800L..900L, "reacted at 400, quiet 300 ms after the last event (500)")
    }

    @Test
    fun `a tap that changes nothing gives up after the change timeout`() = runTest {
        assertFalse(Settle.awaitChangeThenQuiet({ 7L }, before = 7L))
        assertEquals(Settle.CHANGE_TIMEOUT_MS, testScheduler.currentTime)
    }
}
