package dev.auxdesign.voz.core.plan

import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.CommentMode
import dev.auxdesign.voz.core.model.Plan
import dev.auxdesign.voz.core.model.PlanSource
import dev.auxdesign.voz.core.model.Utterance
import dev.auxdesign.voz.core.safety.SensitiveTargetDetector
import dev.auxdesign.voz.core.text.Normalize

/**
 * Last gate before anything touches the phone: vocabulary, size limits, cloud-specific rules and
 * sensitive targets that require a spoken confirmation.
 */
class PlanValidator(private val detector: SensitiveTargetDetector = SensitiveTargetDetector()) {

    data class Confirmation(val stepIndex: Int, val target: String, val term: String)

    sealed interface Result {
        data class Valid(val plan: Plan, val confirmations: List<Confirmation>) : Result
        data class Invalid(val errors: List<String>) : Result
    }

    fun validate(plan: Plan, utterance: Utterance? = null): Result {
        val errors = mutableListOf<String>()
        if (plan.steps.isEmpty()) errors += "plan has no steps"
        if (plan.steps.size > MAX_STEPS) errors += "plan has ${plan.steps.size} steps (max $MAX_STEPS)"
        plan.say?.let { if (it.length > MAX_SAY) errors += "say is too long" }
        plan.steps.forEachIndexed { i, action -> checkAction(i, action, plan.source, utterance, errors) }
        if (errors.isNotEmpty()) return Result.Invalid(errors)

        val said = Normalize.forMatch(utterance?.text.orEmpty())
        val cloud = plan.source == PlanSource.CLOUD
        val confirmations = plan.steps.mapIndexedNotNull { i, action ->
            when (action) {
                is Action.Tap -> {
                    detector.find(action.label)?.let { return@mapIndexedNotNull Confirmation(i, action.label, it) }
                    // Screen text can steer the cloud planner to any button: it may only press unasked what the user named.
                    if (cloud && !named(action.label, said)) Confirmation(i, action.label, NOT_SAID) else null
                }
                // A cloud search can carry screen text (a code, a name) to a web search: only what the user said goes unasked.
                is Action.Search -> if (cloud && !named(action.query, said)) Confirmation(i, action.query, NOT_SAID) else null
                else -> null
            }
        }
        return Result.Valid(plan, confirmations)
    }

    private fun named(label: String, said: String): Boolean {
        val l = Normalize.forMatch(label)
        return l.isNotEmpty() && " $l " in " $said "
    }

    private fun checkAction(i: Int, a: Action, source: PlanSource, utterance: Utterance?, errors: MutableList<String>) {
        if (source == PlanSource.CLOUD && !a.verb.cloudAllowed) errors += "step $i: '${a.verb.wire}' is not allowed from the cloud"
        when (a) {
            is Action.OpenApp -> text(i, "app", a.app, MAX_LABEL, errors)
            is Action.Search -> text(i, "query", a.query, MAX_QUERY, errors)
            is Action.Tap -> text(i, "label", a.label, MAX_LABEL, errors)
            is Action.Type -> {
                text(i, "text", a.text, MAX_TEXT, errors)
                // A cloud plan may only type what the user actually dictated (blocks exfiltration via injected screen text).
                if (source == PlanSource.CLOUD) {
                    val said = Normalize.forMatch(utterance?.text.orEmpty())
                    val typed = Normalize.forMatch(a.text)
                    if (typed.isEmpty() || typed !in said) errors += "step $i: typed text was not dictated by the user"
                }
            }
            is Action.ReadComments -> {
                if (a.count !in 1..MAX_COMMENTS) errors += "step $i: comment count ${a.count} out of range"
                if (a.mode == CommentMode.TOPIC) text(i, "topic", a.topic, MAX_QUERY, errors)
            }
            else -> Unit
        }
    }

    private fun text(i: Int, field: String, value: String?, max: Int, errors: MutableList<String>) {
        if (value == null || value.isBlank()) {
            errors += "step $i: $field is empty"
            return
        }
        if (value.length > max) errors += "step $i: $field is longer than $max"
        if (value.any { it.isISOControl() }) errors += "step $i: $field contains control characters"
    }

    companion object {
        /** [Confirmation.term] for a cloud tap whose label the user never said. */
        const val NOT_SAID = "not said by the user"
        const val MAX_STEPS = 5
        const val MAX_LABEL = 80
        const val MAX_QUERY = 200
        const val MAX_TEXT = 500
        const val MAX_SAY = 300
        const val MAX_COMMENTS = 10
    }
}
