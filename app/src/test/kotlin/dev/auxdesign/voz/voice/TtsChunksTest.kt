package dev.auxdesign.voz.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TtsChunksTest {

    @Test
    fun `short text is one chunk`() {
        assertEquals(listOf("Hola."), TtsController.chunks("  Hola. ", 100))
    }

    @Test
    fun `long text splits on sentence ends and never exceeds the limit`() {
        val sentence = "Comentario con bastante texto para llenar la línea. "
        val text = sentence.repeat(40)
        val chunks = TtsController.chunks(text, 300)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 300 })
        assertTrue(chunks.dropLast(1).all { it.endsWith(".") })
        assertEquals(text.replace(" ", ""), chunks.joinToString("").replace(" ", ""))
    }

    @Test
    fun `text without spaces is cut hard at the limit`() {
        val chunks = TtsController.chunks("x".repeat(250), 100)
        assertEquals(listOf(100, 100, 50), chunks.map { it.length })
    }
}
