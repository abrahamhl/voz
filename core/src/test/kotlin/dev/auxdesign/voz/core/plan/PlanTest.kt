package dev.auxdesign.voz.core.plan

import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.CommentMode
import dev.auxdesign.voz.core.model.Direction
import dev.auxdesign.voz.core.model.Lang
import dev.auxdesign.voz.core.model.Plan
import dev.auxdesign.voz.core.model.PlanSource
import dev.auxdesign.voz.core.model.ScreenNode
import dev.auxdesign.voz.core.model.ScreenSnapshot
import dev.auxdesign.voz.core.model.SearchTarget
import dev.auxdesign.voz.core.model.Utterance
import dev.auxdesign.voz.core.model.Verb
import dev.auxdesign.voz.core.route.EngineResult
import dev.auxdesign.voz.core.route.PlanRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PlanTest {

    private val validator = PlanValidator()
    private val said = Utterance("escribe hola mamá y envíalo", Lang.ES)

    private fun cloud(vararg steps: Action) = Plan(steps.toList(), PlanSource.CLOUD)

    @Test
    fun `valid plan passes and has no confirmations`() {
        val result = validator.validate(cloud(Action.OpenApp("WhatsApp"), Action.Scroll(Direction.DOWN)), said)
        val valid = assertInstanceOf(PlanValidator.Result.Valid::class.java, result)
        assertTrue(valid.confirmations.isEmpty())
    }

    @Test
    fun `tap on a sensitive target requires confirmation`() {
        val result = validator.validate(cloud(Action.Type("hola mamá"), Action.Tap("Enviar")), said)
        val valid = assertInstanceOf(PlanValidator.Result.Valid::class.java, result)
        assertEquals(listOf(PlanValidator.Confirmation(1, "Enviar", "enviar")), valid.confirmations)
    }

    @Test
    fun `cloud tap on a label the user never said needs a spoken yes`() {
        val steered = validator.validate(cloud(Action.Tap("Mix de gatos")), Utterance("pon el primer vídeo", Lang.ES))
        val valid = assertInstanceOf(PlanValidator.Result.Valid::class.java, steered)
        assertEquals(listOf(PlanValidator.Confirmation(0, "Mix de gatos", PlanValidator.NOT_SAID)), valid.confirmations)
    }

    @Test
    fun `cloud tap on a label the user named, and local taps, need no extra yes`() {
        val named = validator.validate(cloud(Action.Tap("Siguiente")), Utterance("pulsa siguiente por favor", Lang.ES))
        assertTrue(assertInstanceOf(PlanValidator.Result.Valid::class.java, named).confirmations.isEmpty())
        val local = validator.validate(Plan(listOf(Action.Tap("Mix de gatos")), PlanSource.LOCAL_ENGINE), Utterance("pon el primer vídeo", Lang.ES))
        assertTrue(assertInstanceOf(PlanValidator.Result.Valid::class.java, local).confirmations.isEmpty())
    }

    @Test
    fun `more than five steps is rejected`() {
        val steps = List(6) { Action.Scroll(Direction.DOWN) }
        assertInstanceOf(PlanValidator.Result.Invalid::class.java, validator.validate(Plan(steps, PlanSource.CLOUD), said))
    }

    @Test
    fun `empty plan is rejected`() {
        assertInstanceOf(PlanValidator.Result.Invalid::class.java, validator.validate(cloud(), said))
    }

    @Test
    fun `cloud cannot type text the user did not dictate`() {
        val result = validator.validate(cloud(Action.Type("my password is hunter2")), said)
        val invalid = assertInstanceOf(PlanValidator.Result.Invalid::class.java, result)
        assertTrue(invalid.errors.single().contains("not dictated"))
    }

    @Test
    fun `local parser may type anything the grammar extracted`() {
        val plan = Plan(listOf(Action.Type("hola")), PlanSource.LOCAL_PARSER)
        assertInstanceOf(PlanValidator.Result.Valid::class.java, validator.validate(plan, Utterance("escribe hola", Lang.ES)))
    }

    @Test
    fun `cloud cannot emit stop and fields are bounded`() {
        assertInstanceOf(PlanValidator.Result.Invalid::class.java, validator.validate(cloud(Action.Stop), said))
        assertInstanceOf(PlanValidator.Result.Invalid::class.java, validator.validate(cloud(Action.Tap("x".repeat(81))), said))
        assertInstanceOf(PlanValidator.Result.Invalid::class.java, validator.validate(cloud(Action.Tap("ok\u0007")), said))
        assertInstanceOf(PlanValidator.Result.Invalid::class.java, validator.validate(cloud(Action.ReadComments(CommentMode.TOPIC, null)), said))
        assertInstanceOf(PlanValidator.Result.Invalid::class.java, validator.validate(cloud(Action.ReadComments(CommentMode.POPULAR, count = 50)), said))
    }

    @Test
    fun `parses a well formed cloud plan`() {
        val json = """
            {"steps":[{"action":"open_app","text":"YouTube"},{"action":"search","text":"lofi","target":"youtube"},
                      {"action":"read_comments","mode":"funny"}],"say":"Voy"}
        """.trimIndent()
        val parsed = assertInstanceOf(PlanJson.Parsed.Ok::class.java, PlanJson.parse(json))
        assertEquals(
            listOf(Action.OpenApp("YouTube"), Action.Search("lofi", SearchTarget.YOUTUBE), Action.ReadComments(CommentMode.FUNNY)),
            parsed.plan.steps,
        )
        assertEquals("Voy", parsed.plan.say)
        assertEquals(PlanSource.CLOUD, parsed.plan.source)
    }

    @Test
    fun `accepts a plan wrapped in a markdown code fence`() {
        val parsed = PlanJson.parse("```json\n{\"steps\":[{\"action\":\"back\"}]}\n```")
        assertInstanceOf(PlanJson.Parsed.Ok::class.java, parsed)
    }

    @ParameterizedTest(name = "rejects: {0}")
    @ValueSource(
        strings = [
            "not json at all",
            "[]",
            "{\"say\":\"hi\"}",
            "{\"steps\":[{\"action\":\"format_phone\"}]}",
            "{\"steps\":[{\"action\":\"tap\"}]}",
            "{\"steps\":[{\"action\":\"read_comments\",\"mode\":\"topic\"}]}",
            "{\"steps\":[\"back\"]}",
            "{\"steps\":[{\"action\":\"back\"},{\"action\":\"back\"},{\"action\":\"back\"},{\"action\":\"back\"},{\"action\":\"back\"},{\"action\":\"back\"}]}",
        ],
    )
    fun `malformed or out-of-vocabulary plans are rejected whole`(json: String) {
        assertInstanceOf(PlanJson.Parsed.Error::class.java, PlanJson.parse(json))
    }

    @Test
    fun `encode and parse round trip`() {
        val plan = Plan(
            listOf(Action.Tap("Next"), Action.ReadComments(CommentMode.TOPIC, "drums"), Action.Search("x", SearchTarget.MAPS)),
            PlanSource.CLOUD,
            say = "ok",
        )
        val back = assertInstanceOf(PlanJson.Parsed.Ok::class.java, PlanJson.parse(PlanJson.encode(plan)))
        assertEquals(plan, back.plan)
    }

    @Test
    fun `schema enumerates the cloud vocabulary without stop`() {
        val items = (PlanJson.schema["properties"] as JsonObject)["steps"] as JsonObject
        val action = ((items["items"] as JsonObject)["properties"] as JsonObject)["action"] as JsonObject
        val allowed = (action["enum"] as JsonArray).map { (it as JsonPrimitive).content }
        assertEquals(Verb.cloudVocabulary, allowed)
        assertTrue("stop" !in allowed)
    }

    @Test
    fun `local planner taps a visible label that the user says`() {
        val screen = ScreenSnapshot("com.google.android.youtube", listOf(ScreenNode(text = "Suscribirse", clickable = true), ScreenNode(text = "Compartir")))
        val result = LocalPlanner().plan(PlanRequest(Utterance("suscribirse", Lang.ES), screen))
        val planned = assertInstanceOf(EngineResult.Planned::class.java, result)
        assertEquals(listOf(Action.Tap("Suscribirse")), planned.plan.steps)
        assertInstanceOf(EngineResult.NoPlan::class.java, LocalPlanner().plan(PlanRequest(Utterance("haz magia", Lang.ES), screen)))
    }
}
