package dev.auxdesign.voz.a11y

import android.accessibilityservice.AccessibilityService
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.annotation.StringRes
import dev.auxdesign.voz.R
import dev.auxdesign.voz.core.model.Action
import dev.auxdesign.voz.core.model.CommentMode
import dev.auxdesign.voz.core.model.Direction
import dev.auxdesign.voz.core.model.GlobalKind
import dev.auxdesign.voz.core.model.Lang
import dev.auxdesign.voz.core.model.ScreenSnapshot
import dev.auxdesign.voz.core.model.SearchTarget
import dev.auxdesign.voz.core.rank.CommentQuery
import dev.auxdesign.voz.core.route.DecisionEngine
import dev.auxdesign.voz.core.safety.SensitiveTargetDetector
import dev.auxdesign.voz.data.InstalledApps
import dev.auxdesign.voz.data.VozSettings
import dev.auxdesign.voz.flows.YouTubeFlows
import dev.auxdesign.voz.util.Strings
import java.text.NumberFormat
import java.util.Locale

sealed interface ExecResult {
    /** [say] is an optional short spoken confirmation (null = silent success). */
    data class Done(val say: String? = null) : ExecResult
    data class Failed(val say: String) : ExecResult
    data class Cancelled(val say: String) : ExecResult
}

/** Per-step context: language, settings, and a way to ask the user "¿Confirmo?". */
class ExecEnv(
    val lang: Lang,
    val settings: VozSettings,
    val preConfirmed: Boolean,
    val ranker: DecisionEngine,
    val confirm: suspend (String) -> Boolean,
)

/** Turns validated actions into Android effects. */
class ActionExecutor(
    private val context: Context,
    private val strings: Strings,
    private val apps: InstalledApps,
    private val youtube: YouTubeFlows,
    private val detector: SensitiveTargetDetector = SensitiveTargetDetector(),
) {
    private val service: VozAccessibilityService? get() = A11yBridge.service.value

    /** Snapshot of the active window for the planners (empty if the service is off). */
    fun snapshot(): ScreenSnapshot {
        val svc = service ?: return ScreenSnapshot.EMPTY
        val root = svc.root() ?: return ScreenSnapshot(svc.foregroundPackage, emptyList())
        return ScreenSnapshot(root.packageName ?: svc.foregroundPackage, NodeFinder.flatten(root))
    }

    suspend fun execute(action: Action, env: ExecEnv): ExecResult = when (action) {
        is Action.OpenApp -> openApp(action.app, env)
        is Action.Search -> search(action, env)
        is Action.Global -> global(action.kind, env)
        is Action.Scroll -> scroll(action.direction, env)
        is Action.Tap -> tap(action.label, env)
        is Action.Type -> type(action.text, env)
        Action.ReadScreen -> readScreen(env)
        is Action.Volume -> volume(action.direction, env)
        Action.Rotate -> rotate(env)
        Action.Stop -> ExecResult.Done()
        is Action.Fullscreen -> fullscreen(action.enter, env)
        is Action.ReadComments -> readComments(action, env)
    }

    private suspend fun openApp(name: String, env: ExecEnv): ExecResult {
        val match = apps.matcher().best(name) ?: return fail(env, R.string.say_app_not_found, name)
        val intent = context.packageManager.getLaunchIntentForPackage(match.app.packageName)
            ?: return fail(env, R.string.say_app_not_found, name)
        return if (launch(intent)) done(env, R.string.say_opening, match.app.label) else fail(env, R.string.say_action_failed)
    }

    private fun search(action: Action.Search, env: ExecEnv): ExecResult {
        val q = action.query
        val encoded = Uri.encode(q)
        val candidates = when (action.target) {
            SearchTarget.YOUTUBE -> listOf(
                Intent(Intent.ACTION_SEARCH).setPackage(YouTubeFlows.PACKAGE).putExtra(SearchManager.QUERY, q),
                view("https://www.youtube.com/results?search_query=$encoded"),
            )
            SearchTarget.GOOGLE -> listOf(
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q),
                view("https://www.google.com/search?q=$encoded"),
            )
            SearchTarget.MAPS -> listOf(
                view("geo:0,0?q=$encoded"),
                view("https://www.google.com/maps/search/?api=1&query=$encoded"),
            )
            SearchTarget.PLAY -> listOf(
                view("market://search?q=$encoded&c=apps"),
                view("https://play.google.com/store/search?q=$encoded&c=apps"),
            )
        }
        val ok = candidates.any { launch(it) }
        return if (ok) done(env, R.string.say_searching, q, targetName(action.target)) else fail(env, R.string.say_action_failed)
    }

    private fun global(kind: GlobalKind, env: ExecEnv): ExecResult {
        val svc = service ?: return needAccessibility(env)
        val code = when (kind) {
            GlobalKind.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            GlobalKind.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            GlobalKind.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GlobalKind.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            GlobalKind.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        }
        return if (svc.performGlobalAction(code)) ExecResult.Done() else fail(env, R.string.say_action_failed)
    }

    private suspend fun scroll(direction: Direction, env: ExecEnv): ExecResult {
        val svc = service ?: return needAccessibility(env)
        val forward = direction == Direction.DOWN
        val list = svc.root()?.let { NodeFinder.largestScrollable(it) }
        if (list != null && list.scroll(forward)) return ExecResult.Done()
        return if (svc.swipeVertical(fingerUp = forward)) ExecResult.Done() else fail(env, R.string.say_scroll_failed)
    }

    private suspend fun tap(label: String, env: ExecEnv): ExecResult {
        val svc = service ?: return needAccessibility(env)
        val root = svc.root() ?: return fail(env, R.string.say_not_found, label)
        val hit = NodeFinder.findByLabel(root, label) ?: return fail(env, R.string.say_not_found, label)
        // The label the user said may differ from what is really there ("the blue button" -> "Pay").
        if (!env.preConfirmed && detector.isSensitive(hit.label)) {
            if (!env.confirm(strings.get(env.lang, R.string.confirm_tap, hit.label))) return cancelled(env)
        }
        val clickable = NodeFinder.clickableAncestor(hit.node)
        val pressed = clickable?.click() == true || svc.tapAt(hit.node.bounds.centerX, hit.node.bounds.centerY)
        return if (pressed) done(env, R.string.say_tapped, hit.label) else fail(env, R.string.say_action_failed)
    }

    private fun type(text: String, env: ExecEnv): ExecResult {
        val svc = service ?: return needAccessibility(env)
        val field = svc.root()?.let { NodeFinder.focusedEditable(it) } ?: return fail(env, R.string.say_no_field)
        val existing = if (field.isShowingHint) null else field.text
        val value = if (existing.isNullOrBlank()) text else "$existing $text"
        return if (field.setText(value)) done(env, R.string.say_typed) else fail(env, R.string.say_action_failed)
    }

    private fun readScreen(env: ExecEnv): ExecResult {
        if (service == null) return needAccessibility(env)
        val lines = snapshot().readableLines()
        if (lines.isEmpty()) return done(env, R.string.say_screen_empty)
        return ExecResult.Done(strings.get(env.lang, R.string.say_screen_intro, lines.joinToString(". ")))
    }

    private fun volume(direction: Direction, env: ExecEnv): ExecResult {
        val am = context.getSystemService(AudioManager::class.java) ?: return fail(env, R.string.say_action_failed)
        val adjust = if (direction == Direction.UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, AudioManager.FLAG_SHOW_UI)
        return ExecResult.Done()
    }

    /** Toggles portrait/landscape. Needs "modify system settings", which the user grants in a guided screen. */
    private fun rotate(env: ExecEnv): ExecResult {
        if (!Settings.System.canWrite(context)) {
            launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            return ExecResult.Failed(strings.get(env.lang, R.string.say_rotate_permission))
        }
        val resolver = context.contentResolver
        return try {
            val current = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
            val toLandscape = current == Surface.ROTATION_0 || current == Surface.ROTATION_180
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            Settings.System.putInt(resolver, Settings.System.USER_ROTATION, if (toLandscape) Surface.ROTATION_90 else Surface.ROTATION_0)
            done(env, if (toLandscape) R.string.say_rotated_landscape else R.string.say_rotated_portrait)
        } catch (e: SecurityException) {
            fail(env, R.string.say_action_failed)
        } catch (e: IllegalArgumentException) {
            fail(env, R.string.say_action_failed)
        }
    }

    private suspend fun fullscreen(enter: Boolean, env: ExecEnv): ExecResult {
        val svc = service ?: return needAccessibility(env)
        return when (youtube.fullscreen(svc, enter)) {
            YouTubeFlows.Fullscreen.DONE -> ExecResult.Done()
            YouTubeFlows.Fullscreen.NOT_YOUTUBE -> fail(env, R.string.say_open_video_first)
            YouTubeFlows.Fullscreen.NOT_FOUND ->
                if (enter) {
                    rotate(env)
                } else if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                    ExecResult.Done()
                } else {
                    fail(env, R.string.say_action_failed)
                }
        }
    }

    private suspend fun readComments(action: Action.ReadComments, env: ExecEnv): ExecResult {
        val svc = service ?: return needAccessibility(env)
        val comments = when (val result = youtube.collectComments(svc, env.settings.commentDepth)) {
            YouTubeFlows.Comments.NotYouTube -> return fail(env, R.string.say_open_video_first)
            YouTubeFlows.Comments.PanelNotFound -> return fail(env, R.string.say_comments_not_found)
            is YouTubeFlows.Comments.Collected -> result.comments
        }
        if (comments.isEmpty()) return fail(env, R.string.say_no_comments)
        val ranking = env.ranker.rankComments(comments, CommentQuery(action.mode, action.topic), action.count)
        if (ranking.items.isEmpty()) {
            return if (action.mode == CommentMode.TOPIC) {
                fail(env, R.string.say_no_topic_comments, action.topic.orEmpty())
            } else {
                fail(env, R.string.say_no_comments)
            }
        }
        val numbers = NumberFormat.getIntegerInstance(Locale.forLanguageTag(env.lang.tag))
        val parts = ArrayList<String>()
        if (ranking.fallback) parts += strings.get(env.lang, R.string.say_no_funny)
        ranking.items.forEachIndexed { i, c ->
            parts += if (c.likes != null) {
                strings.get(env.lang, R.string.say_comment_likes, i + 1, numbers.format(c.likes), c.text)
            } else {
                strings.get(env.lang, R.string.say_comment, i + 1, c.text)
            }
        }
        return ExecResult.Done(parts.joinToString(" "))
    }

    private fun launch(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Starting from the accessibility service is allowed while the app is in the background.
        val starter: Context = service ?: context
        return try {
            starter.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    private fun targetName(target: SearchTarget): String = context.getString(
        when (target) {
            SearchTarget.YOUTUBE -> R.string.target_youtube
            SearchTarget.GOOGLE -> R.string.target_google
            SearchTarget.MAPS -> R.string.target_maps
            SearchTarget.PLAY -> R.string.target_play
        },
    )

    private fun done(env: ExecEnv, @StringRes id: Int, vararg args: Any) = ExecResult.Done(strings.get(env.lang, id, *args))

    private fun fail(env: ExecEnv, @StringRes id: Int, vararg args: Any) = ExecResult.Failed(strings.get(env.lang, id, *args))

    private fun cancelled(env: ExecEnv) = ExecResult.Cancelled(strings.get(env.lang, R.string.say_cancelled))

    private fun needAccessibility(env: ExecEnv) = fail(env, R.string.say_need_a11y)
}
