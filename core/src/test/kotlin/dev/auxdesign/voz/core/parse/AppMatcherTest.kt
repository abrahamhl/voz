package dev.auxdesign.voz.core.parse

import dev.auxdesign.voz.core.model.AppEntry
import dev.auxdesign.voz.core.model.ScreenNode
import dev.auxdesign.voz.core.model.ScreenSnapshot
import dev.auxdesign.voz.core.text.Normalize
import dev.auxdesign.voz.core.text.Payload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AppMatcherTest {

    private val matcher = AppMatcher(
        listOf(
            AppEntry("YouTube", "com.google.android.youtube"),
            AppEntry("WhatsApp", "com.whatsapp"),
            AppEntry("Google Maps", "com.google.android.apps.maps"),
            AppEntry("Gmail", "com.google.android.gm"),
            AppEntry("Ajustes", "com.android.settings"),
            AppEntry("Cámara", "com.android.camera"),
            AppEntry("Spotify", "com.spotify.music"),
            AppEntry("Buienradar", "nl.elastique.buienradar"),
        ),
    )

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "youtube,com.google.android.youtube",
        "YouTube,com.google.android.youtube",
        "yutub,com.google.android.youtube",
        "guasap,com.whatsapp",
        "whats app,com.whatsapp",
        "maps,com.google.android.apps.maps",
        "mapas,com.google.android.apps.maps",
        "correo,com.google.android.gm",
        "settings,com.android.settings",
        "instellingen,com.android.settings",
        "camara,com.android.camera",
        "spotifi,com.spotify.music",
        "buienradar,nl.elastique.buienradar",
    )
    fun `fuzzy matches spoken app names`(said: String, pkg: String) {
        assertEquals(pkg, matcher.best(said)?.app?.packageName)
    }

    @Test
    fun `unknown apps do not match`() {
        assertNull(matcher.best("calculadora científica"))
        assertNull(matcher.best(""))
    }

    @Test
    fun `levenshtein basics`() {
        assertEquals(0, AppMatcher.levenshtein("abc", "abc"))
        assertEquals(3, AppMatcher.levenshtein("", "abc"))
        assertEquals(2, AppMatcher.levenshtein("yutub", "youtube"))
    }

    @Test
    fun `fold keeps length so payload ranges map back to the original`() {
        val original = "¡Ábreme «WhatsApp», por favor!"
        val folded = Normalize.fold(original)
        assertEquals(original.length, folded.length)
        assertEquals(" abreme  whatsapp   por favor ", folded)
        assertEquals("whatsapp", Normalize.forMatch("  WhatsApp!! "))
    }

    @Test
    fun `payload helpers`() {
        assertEquals("WhatsApp", Payload.stripAppWords("la app WhatsApp".removePrefix("la ")))
        assertEquals("Maps", Payload.stripAppWords("Maps app"))
        assertEquals("Hola, ¿qué tal?", Payload.unquote("\"Hola, ¿qué tal?\""))
        assertEquals("Suscribirse", Payload.clean(" «Suscribirse». "))
    }

    @Test
    fun `screen snapshot reads distinct labels in order`() {
        val snapshot = ScreenSnapshot(
            "pkg",
            listOf(
                ScreenNode(text = "Inicio"),
                ScreenNode(description = "Buscar"),
                ScreenNode(text = "inicio"),
                ScreenNode(text = "x"),
                ScreenNode(text = "Vídeo 1"),
                ScreenNode(text = "Vídeo 2"),
            ),
        )
        assertEquals(listOf("Inicio", "Buscar", "Vídeo 1"), snapshot.readableLines(max = 3))
    }
}
