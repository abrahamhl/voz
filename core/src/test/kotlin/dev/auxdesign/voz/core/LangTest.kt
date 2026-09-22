package dev.auxdesign.voz.core

import dev.auxdesign.voz.core.model.Lang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LangTest {

    @ParameterizedTest
    @CsvSource("es-ES,ES", "es,ES", "es_MX,ES", "en-GB,EN", "EN,EN", "nl-BE,NL", "nl,NL")
    fun `resolves tags`(tag: String, expected: Lang) {
        assertEquals(expected, Lang.fromTag(tag))
    }

    @Test
    fun `unknown or blank tags resolve to null`() {
        assertNull(Lang.fromTag("fr-FR"))
        assertNull(Lang.fromTag(""))
        assertNull(Lang.fromTag(null))
    }

    @Test
    fun `default falls back to english`() {
        assertEquals(Lang.EN, Lang.fromTagOrDefault("de"))
    }
}
