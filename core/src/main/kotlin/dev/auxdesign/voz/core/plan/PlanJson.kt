package dev.auxdesign.voz.core.plan

import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.CommentMode
import dev.auxdesign.voz.core.model.Direction
import dev.auxdesign.voz.core.model.GlobalKind
import dev.auxdesign.voz.core.model.Plan
import dev.auxdesign.voz.core.model.PlanSource
import dev.auxdesign.voz.core.model.SearchTarget
import dev.auxdesign.voz.core.model.Verb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Wire format of a cloud plan:
 * `{"steps":[{"action":"tap","text":"Subscribe"}], "say":"Done"}`.
 * Parsing is strict: unknown actions or malformed steps reject the WHOLE plan.
 */
object PlanJson {

    sealed interface Parsed {
        data class Ok(val plan: Plan) : Parsed
        data class Error(val reason: String) : Parsed
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** JSON Schema sent to the model as the response schema. */
    val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("steps") {
                put("type", "array")
                put("maxItems", PlanValidator.MAX_STEPS)
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("action") {
                            put("type", "string")
                            putJsonArray("enum") { Verb.cloudVocabulary.forEach { add(it) } }
                        }
                        putJsonObject("text") {
                            put("type", "string")
                            put("description", "app name, search query, visible label to tap, text to type, or comment topic")
                        }
                        putJsonObject("target") {
                            put("type", "string")
                            putJsonArray("enum") { SearchTarget.entries.forEach { add(it.wire) } }
                        }
                        putJsonObject("mode") {
                            put("type", "string")
                            putJsonArray("enum") { CommentMode.entries.forEach { add(it.wire) } }
                        }
                    }
                    putJsonArray("required") { add("action") }
                }
            }
            putJsonObject("say") {
                put("type", "string")
                put("description", "short reply to speak to the user, in the user's language")
            }
        }
        putJsonArray("required") { add("steps") }
    }

    fun parse(raw: String): Parsed {
        val text = stripFences(raw)
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            return Parsed.Error("response is not valid JSON")
        }
        val obj = root as? JsonObject ?: return Parsed.Error("root is not an object")
        val steps = obj["steps"] as? JsonArray ?: return Parsed.Error("missing 'steps' array")
        if (steps.size > PlanValidator.MAX_STEPS) return Parsed.Error("too many steps (${steps.size})")
        val actions = ArrayList<Action>(steps.size)
        steps.forEachIndexed { i, element ->
            val action = toAction(element as? JsonObject) ?: return Parsed.Error("step $i is invalid or outside the vocabulary")
            actions += action
        }
        return Parsed.Ok(Plan(actions, PlanSource.CLOUD, say = obj.string("say")?.takeIf { it.isNotBlank() }))
    }

    /** Serializes a plan back to the wire format (used by the action log). */
    fun encode(plan: Plan): String = buildJsonObject {
        putJsonArray("steps") {
            plan.steps.forEach { a ->
                addJsonObject {
                    put("action", a.verb.wire)
                    when (a) {
                        is Action.OpenApp -> put("text", a.app)
                        is Action.Search -> {
                            put("text", a.query)
                            put("target", a.target.wire)
                        }
                        is Action.Tap -> put("text", a.label)
                        is Action.Type -> put("text", a.text)
                        is Action.ReadComments -> {
                            put("mode", a.mode.wire)
                            a.topic?.let { put("text", it) }
                        }
                        else -> Unit
                    }
                }
            }
        }
        plan.say?.let { put("say", it) }
    }.toString()

    private fun toAction(o: JsonObject?): Action? {
        if (o == null) return null
        val verb = Verb.fromWire(o.string("action")) ?: return null
        val text = o.string("text")?.trim()?.takeIf { it.isNotEmpty() }
        return when (verb) {
            Verb.OPEN_APP -> text?.let { Action.OpenApp(it) }
            Verb.SEARCH -> text?.let { Action.Search(it, SearchTarget.fromWire(o.string("target")) ?: SearchTarget.GOOGLE) }
            Verb.BACK -> Action.Global(GlobalKind.BACK)
            Verb.HOME -> Action.Global(GlobalKind.HOME)
            Verb.RECENTS -> Action.Global(GlobalKind.RECENTS)
            Verb.NOTIFICATIONS -> Action.Global(GlobalKind.NOTIFICATIONS)
            Verb.QUICK_SETTINGS -> Action.Global(GlobalKind.QUICK_SETTINGS)
            Verb.SCROLL_UP -> Action.Scroll(Direction.UP)
            Verb.SCROLL_DOWN -> Action.Scroll(Direction.DOWN)
            Verb.TAP -> text?.let { Action.Tap(it) }
            Verb.TYPE -> text?.let { Action.Type(it) }
            Verb.READ_SCREEN -> Action.ReadScreen
            Verb.VOLUME_UP -> Action.Volume(Direction.UP)
            Verb.VOLUME_DOWN -> Action.Volume(Direction.DOWN)
            Verb.ROTATE -> Action.Rotate
            Verb.STOP -> Action.Stop
            Verb.FULLSCREEN -> Action.Fullscreen(enter = true)
            Verb.EXIT_FULLSCREEN -> Action.Fullscreen(enter = false)
            Verb.READ_COMMENTS -> {
                val mode = CommentMode.fromWire(o.string("mode")) ?: CommentMode.POPULAR
                if (mode == CommentMode.TOPIC && text == null) null else Action.ReadComments(mode, topic = text.takeIf { mode == CommentMode.TOPIC })
            }
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun stripFences(raw: String): String {
        val t = raw.trim()
        if (!t.startsWith("```")) return t
        return t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()
    }
}
