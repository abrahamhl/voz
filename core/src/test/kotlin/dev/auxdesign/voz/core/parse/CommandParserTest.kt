package dev.auxdesign.voz.core.parse

import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.CommentMode
import dev.auxdesign.voz.core.model.Direction
import dev.auxdesign.voz.core.model.GlobalKind
import dev.auxdesign.voz.core.model.Lang
import dev.auxdesign.voz.core.model.SearchTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class CommandParserTest {

    private val parser = CommandParser()

    @ParameterizedTest(name = "[ES] {0}")
    @MethodSource("spanish")
    fun `spanish grammar`(said: String, expected: Action) {
        assertEquals(expected, parser.parse(said, Lang.ES))
    }

    @ParameterizedTest(name = "[EN] {0}")
    @MethodSource("english")
    fun `english grammar`(said: String, expected: Action) {
        assertEquals(expected, parser.parse(said, Lang.EN))
    }

    @ParameterizedTest(name = "[NL] {0}")
    @MethodSource("dutch")
    fun `dutch grammar`(said: String, expected: Action) {
        assertEquals(expected, parser.parse(said, Lang.NL))
    }

    @ParameterizedTest(name = "unparsed: {0}")
    @ValueSource(strings = ["", "   ", "¿qué tiempo hará mañana en Arnhem?", "tell me a joke about cats", "hoe laat is het"])
    fun `returns null for things outside the grammar`(said: String) {
        assertNull(parser.parse(said, Lang.ES))
    }

    @Test
    fun `falls back to other languages when the user mixes them`() {
        assertEquals(Action.OpenApp("WhatsApp"), parser.parse("open WhatsApp", Lang.ES))
        assertEquals(Action.Global(GlobalKind.BACK), parser.parse("atrás", Lang.NL))
    }

    @Test
    fun `payloads keep original casing and accents`() {
        assertEquals(
            Action.Type("Llego en 10 minutos, ¿vale?"),
            parser.parse("Escribe: Llego en 10 minutos, ¿vale?", Lang.ES),
        )
        assertEquals(Action.Search("canciones de Rosalía", SearchTarget.YOUTUBE), parser.parse("busca canciones de Rosalía en YouTube", Lang.ES))
    }

    companion object {
        private fun a(said: String, expected: Action) = Arguments.of(said, expected)

        @JvmStatic
        fun spanish() = listOf(
            a("abre YouTube", Action.OpenApp("YouTube")),
            a("Abre la app de WhatsApp", Action.OpenApp("WhatsApp")),
            a("oye voz, abre ajustes por favor", Action.OpenApp("ajustes")),
            a("busca gatitos en YouTube", Action.Search("gatitos", SearchTarget.YOUTUBE)),
            a("busca en maps farmacias cerca", Action.Search("farmacias cerca", SearchTarget.MAPS)),
            a("busca recetas de paella", Action.Search("recetas de paella", SearchTarget.GOOGLE)),
            a("pon despacito en youtube", Action.Search("despacito", SearchTarget.YOUTUBE)),
            a("busca whatsapp en la play store", Action.Search("whatsapp", SearchTarget.PLAY)),
            a("llévame a la estación de Arnhem", Action.Search("la estación de Arnhem", SearchTarget.MAPS)),
            a("atrás", Action.Global(GlobalKind.BACK)),
            a("vuelve atrás", Action.Global(GlobalKind.BACK)),
            a("ir a inicio", Action.Global(GlobalKind.HOME)),
            a("apps recientes", Action.Global(GlobalKind.RECENTS)),
            a("abre las notificaciones", Action.Global(GlobalKind.NOTIFICATIONS)),
            a("ajustes rápidos", Action.Global(GlobalKind.QUICK_SETTINGS)),
            a("baja", Action.Scroll(Direction.DOWN)),
            a("desplaza hacia arriba", Action.Scroll(Direction.UP)),
            a("toca \"Suscribirse\"", Action.Tap("Suscribirse")),
            a("pulsa el botón Enviar", Action.Tap("Enviar")),
            a("escribe hola mamá", Action.Type("hola mamá")),
            a("lee la pantalla", Action.ReadScreen),
            a("¿qué hay en pantalla?", Action.ReadScreen),
            a("sube el volumen", Action.Volume(Direction.UP)),
            a("baja el volumen", Action.Volume(Direction.DOWN)),
            a("gira la pantalla", Action.Rotate),
            a("para", Action.Stop),
            a("cancela", Action.Stop),
            a("pantalla completa", Action.Fullscreen(enter = true)),
            a("sal de pantalla completa", Action.Fullscreen(enter = false)),
            a("lee los comentarios más populares", Action.ReadComments(CommentMode.POPULAR)),
            a("léeme los comentarios más graciosos", Action.ReadComments(CommentMode.FUNNY)),
            a("lee los comentarios sobre el final", Action.ReadComments(CommentMode.TOPIC, topic = "el final")),
            a("¿qué dicen de la música?", Action.ReadComments(CommentMode.TOPIC, topic = "la música")),
        )

        @JvmStatic
        fun english() = listOf(
            a("open YouTube", Action.OpenApp("YouTube")),
            a("please launch the Spotify app", Action.OpenApp("Spotify")),
            a("go to settings", Action.OpenApp("settings")),
            a("search for cat videos on YouTube", Action.Search("cat videos", SearchTarget.YOUTUBE)),
            a("search Google for the weather in Madrid", Action.Search("the weather in Madrid", SearchTarget.GOOGLE)),
            a("look up pizza near me", Action.Search("pizza near me", SearchTarget.GOOGLE)),
            a("navigate to Central Station", Action.Search("Central Station", SearchTarget.MAPS)),
            a("go back", Action.Global(GlobalKind.BACK)),
            a("go home", Action.Global(GlobalKind.HOME)),
            a("show recent apps", Action.Global(GlobalKind.RECENTS)),
            a("open notifications", Action.Global(GlobalKind.NOTIFICATIONS)),
            a("quick settings", Action.Global(GlobalKind.QUICK_SETTINGS)),
            a("scroll down", Action.Scroll(Direction.DOWN)),
            a("scroll up", Action.Scroll(Direction.UP)),
            a("tap on Subscribe", Action.Tap("Subscribe")),
            a("click the button called Next", Action.Tap("Next")),
            a("type I'm on my way", Action.Type("I'm on my way")),
            a("what's on the screen?", Action.ReadScreen),
            a("read the screen", Action.ReadScreen),
            a("volume up", Action.Volume(Direction.UP)),
            a("turn the volume down", Action.Volume(Direction.DOWN)),
            a("rotate the screen", Action.Rotate),
            a("stop", Action.Stop),
            a("go full screen", Action.Fullscreen(enter = true)),
            a("exit fullscreen", Action.Fullscreen(enter = false)),
            a("read the top comments", Action.ReadComments(CommentMode.POPULAR)),
            a("read the funniest comments", Action.ReadComments(CommentMode.FUNNY)),
            a("read comments about the ending", Action.ReadComments(CommentMode.TOPIC, topic = "the ending")),
            a("what are people saying about the drummer", Action.ReadComments(CommentMode.TOPIC, topic = "the drummer")),
        )

        @JvmStatic
        fun dutch() = listOf(
            a("open YouTube", Action.OpenApp("YouTube")),
            a("start de app Buienradar", Action.OpenApp("Buienradar")),
            a("zoek katten op YouTube", Action.Search("katten", SearchTarget.YOUTUBE)),
            a("zoek op google naar het weer", Action.Search("het weer", SearchTarget.GOOGLE)),
            a("navigeer naar Arnhem Centraal", Action.Search("Arnhem Centraal", SearchTarget.MAPS)),
            a("ga terug", Action.Global(GlobalKind.BACK)),
            a("ga naar het startscherm", Action.Global(GlobalKind.HOME)),
            a("recente apps", Action.Global(GlobalKind.RECENTS)),
            a("toon meldingen", Action.Global(GlobalKind.NOTIFICATIONS)),
            a("snelle instellingen", Action.Global(GlobalKind.QUICK_SETTINGS)),
            a("scroll naar beneden", Action.Scroll(Direction.DOWN)),
            a("naar boven", Action.Scroll(Direction.UP)),
            a("tik op Abonneren", Action.Tap("Abonneren")),
            a("typ ik kom eraan", Action.Type("ik kom eraan")),
            a("lees het scherm voor", Action.ReadScreen),
            a("volume omhoog", Action.Volume(Direction.UP)),
            a("zachter", Action.Volume(Direction.DOWN)),
            a("draai het scherm", Action.Rotate),
            a("stop maar", Action.Stop),
            a("volledig scherm", Action.Fullscreen(enter = true)),
            a("volledig scherm verlaten", Action.Fullscreen(enter = false)),
            a("lees de populairste reacties", Action.ReadComments(CommentMode.POPULAR)),
            a("lees de grappigste reacties", Action.ReadComments(CommentMode.FUNNY)),
            a("lees de reacties over de gitarist", Action.ReadComments(CommentMode.TOPIC, topic = "de gitarist")),
        )
    }
}
