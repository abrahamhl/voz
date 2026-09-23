package dev.auxdesign.voz.voice

import androidx.annotation.StringRes
import dev.auxdesign.voz.R
import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.CommentMode
import dev.auxdesign.voz.core.model.Direction
import dev.auxdesign.voz.core.model.GlobalKind
import dev.auxdesign.voz.core.model.Lang
import dev.auxdesign.voz.core.model.PlanSource
import dev.auxdesign.voz.core.model.SearchTarget
import dev.auxdesign.voz.util.Strings

/** Human sentences for actions ("Search YouTube for “cats”"), never wire strings. Dictated text is reduced to its length. */
class ActionText(private val strings: Strings) {

    fun describe(action: Action, lang: Lang): String = when (action) {
        is Action.OpenApp -> get(lang, R.string.intent_open_app, action.app)
        is Action.Search -> get(lang, R.string.intent_search, action.query, target(lang, action.target))
        is Action.Global -> get(
            lang,
            when (action.kind) {
                GlobalKind.BACK -> R.string.intent_back
                GlobalKind.HOME -> R.string.intent_home
                GlobalKind.RECENTS -> R.string.intent_recents
                GlobalKind.NOTIFICATIONS -> R.string.intent_notifications
                GlobalKind.QUICK_SETTINGS -> R.string.intent_quick_settings
            },
        )
        is Action.Scroll -> get(lang, if (action.direction == Direction.UP) R.string.intent_scroll_up else R.string.intent_scroll_down)
        is Action.Tap -> get(lang, R.string.intent_tap, action.label)
        is Action.Type -> get(lang, R.string.intent_type, action.text.length)
        Action.ReadScreen -> get(lang, R.string.intent_read_screen)
        is Action.Volume -> get(lang, if (action.direction == Direction.UP) R.string.intent_volume_up else R.string.intent_volume_down)
        Action.Rotate -> get(lang, R.string.intent_rotate)
        Action.Stop -> get(lang, R.string.intent_stop)
        is Action.Fullscreen -> get(lang, if (action.enter) R.string.intent_fullscreen else R.string.intent_exit_fullscreen)
        is Action.ReadComments -> when (action.mode) {
            CommentMode.POPULAR -> get(lang, R.string.intent_read_comments_popular)
            CommentMode.FUNNY -> get(lang, R.string.intent_read_comments_funny)
            CommentMode.TOPIC -> get(lang, R.string.intent_read_comments_topic, action.topic.orEmpty())
        }
    }

    fun describe(steps: List<Action>, lang: Lang): String = steps.joinToString("; ") { describe(it, lang) }

    /** "On this phone" or "Cloud (Gemini)". */
    fun source(source: PlanSource, lang: Lang): String =
        get(lang, if (source == PlanSource.CLOUD) R.string.source_cloud else R.string.source_phone)

    private fun target(lang: Lang, target: SearchTarget): String = get(
        lang,
        when (target) {
            SearchTarget.YOUTUBE -> R.string.target_youtube
            SearchTarget.GOOGLE -> R.string.target_google
            SearchTarget.MAPS -> R.string.target_maps
            SearchTarget.PLAY -> R.string.target_play
        },
    )

    private fun get(lang: Lang, @StringRes id: Int, vararg args: Any): String = strings.get(lang, id, *args)
}
