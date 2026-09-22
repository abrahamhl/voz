package dev.auxdesign.voz.core.rank

import dev.auxdesign.voz.core.model.CommentMode
import dev.auxdesign.voz.core.model.ScreenNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CommentsTest {

    private val ranker = CommentRanker()

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "15|15", "1.234|1234", "1,234|1234", "1,2 mil|1200", "12 mil|12000", "3.4K|3400", "1,2K|1200", "12 k|12000",
            "1,5 M|1500000", "2 mln|2000000", "Like this comment along with 1,234 other people|1234",
            "Indicar que te gusta este comentario junto con otras 56 personas|56",
            "Vind deze reactie leuk, samen met 7 anderen|7",
        ],
    )
    fun `parses like counts in ES EN NL formats`(raw: String, expected: Int) {
        assertEquals(expected, LikeCountParser.parse(raw))
    }

    @Test
    fun `bare count detection`() {
        assertTrue(LikeCountParser.isBareCount("1,2 k"))
        assertTrue(LikeCountParser.isBareCount("87"))
        assertFalse(LikeCountParser.isBareCount("87 respuestas"))
        assertFalse(LikeCountParser.isBareCount("Top 10 goals"))
    }

    @Test
    fun `popular ranks by likes and dedupes repeated comments keeping max likes`() {
        val comments = listOf(
            Comment("Great song", 10),
            Comment("Best part is 2:13", 900),
            Comment("great   SONG", 5000),
            Comment("first", null),
        )
        val top = ranker.rank(comments, CommentQuery(CommentMode.POPULAR), limit = 2).items
        assertEquals(listOf(Comment("Great song", 5000), Comment("Best part is 2:13", 900)), top)
    }

    @Test
    fun `funny uses laughter lexicon and emoji across languages`() {
        val comments = listOf(
            Comment("Beautiful melody", 5000),
            Comment("jajajaja me muero con el gato", 20),
            Comment("I'm dead 😂😂", 3),
            Comment("Ik lig dubbel haha", 1),
            Comment("Nice video", 100),
        )
        val ranking = ranker.rank(comments, CommentQuery(CommentMode.FUNNY))
        assertFalse(ranking.fallback)
        assertEquals(3, ranking.items.size)
        assertTrue(ranking.items.none { it.text == "Beautiful melody" || it.text == "Nice video" })
    }

    @Test
    fun `funny falls back to popular when nothing is funny`() {
        val ranking = ranker.rank(listOf(Comment("Nice", 1), Comment("Wow", 9)), CommentQuery(CommentMode.FUNNY))
        assertTrue(ranking.fallback)
        assertEquals("Wow", ranking.items.first().text)
    }

    @Test
    fun `topic matches keywords with simple stemming and ignores stopwords`() {
        val comments = listOf(
            Comment("The drummer is insane", 10),
            Comment("Those drums at 3:20!", 500),
            Comment("Love the lyrics", 999),
        )
        val items = ranker.rank(comments, CommentQuery(CommentMode.TOPIC, "the drums"), limit = 3).items
        assertEquals(listOf("Those drums at 3:20!", "The drummer is insane"), items.map { it.text })
        assertTrue(ranker.rank(comments, CommentQuery(CommentMode.TOPIC, "guitarra"), 3).items.isEmpty())
    }

    @Test
    fun `extracts comments and attaches like counts from a fake comments panel`() {
        val nodes = listOf(
            ScreenNode(text = "Comentarios"),
            ScreenNode(text = "Ordenar"),
            ScreenNode(text = "@maria · hace 2 días"),
            ScreenNode(text = "Este vídeo me salvó el día jajaja"),
            ScreenNode(description = "Indicar que te gusta este comentario junto con otras 1.234 personas", clickable = true),
            ScreenNode(text = "1,2 mil"),
            ScreenNode(text = "Responder"),
            ScreenNode(text = "15 respuestas"),
            ScreenNode(text = "@pieter · 1 week ago"),
            ScreenNode(text = "The bridge at 2:10 gives me chills"),
            ScreenNode(text = "87"),
            ScreenNode(text = "Reply"),
        )
        val comments = CommentExtractor.extract(nodes)
        assertEquals(
            listOf(Comment("Este vídeo me salvó el día jajaja", 1234), Comment("The bridge at 2:10 gives me chills", 87)),
            comments,
        )
    }
}
