package dev.auxdesign.voz.a11y

import android.accessibilityservice.AccessibilityService
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
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
import dev.auxdesign.voz.core.text.Normalize
import dev.auxdesign.voz.data.InstalledApps
import dev.auxdesign.voz.data.VozSettings
import dev.auxdesign.voz.flows.YouTubeFlows
import dev.auxdesign.voz.util.Strings
import java.text.NumberFormat
import kotlinx.coroutines.delay
import java.util.Locale

sealed interface ExecResult {
    /** [say] is an optional short spoken confirmation (null = silent success). */
    data class Done(val say: String? = null) : ExecResult
    data class Failed(val say: String) : ExecResult
    data class Cancelled(val say: String) : ExecResult
}

/**
 * Per-step context: language, settings, and a way to ask the user "¿Confirmo?".
 * [confirmedTarget] is the label the user already confirmed for this step (plan-level check), if any.
 */
class ExecEnv(
    val lang: Lang,
    val settings: VozSettings,
    val confirmedTarget: String?,
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
    private val state: SharedPreferences by lazy { context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE) }

    /** Snapshot of the active window for the planners (empty if the service is off). */
    fun snapshot(): ScreenSnapshot {
        val svc = service ?: return ScreenSnapshot.EMPTY
        val root = svc.root() ?: return ScreenSnapshot(svc.foregroundPackage, emptyList())
        return ScreenSnapshot(root.packageName ?: svc.foregroundPackage, NodeFinder.flatten(root))
    }

    /** Package of the window in front right now (null if unknown). */
    fun foregroundPackage(): String? = service?.let { it.root()?.packageName ?: it.foregroundPackage }

    /** Counter of window events seen by the accessibility service; captured before a step, compared after. */
    fun windowStamp(): Long = service?.windowEvents ?: 0L

    /**
     * After a step that changes the window, wait until the new window is in front (≤ 3 s) or the screen has
     * reacted and gone quiet, so the next step never acts on a stale tree or on coordinates that are gone.
     */
    suspend fun awaitSettled(action: Action, packageBefore: String?, stampBefore: Long) {
        val opensSomethingElse = action is Action.OpenApp || action is Action.Search ||
            (action is Action.Global && action.kind == GlobalKind.HOME)
        val mayChangeWindow = opensSomethingElse || action is Action.Global || action is Action.Tap || action is Action.Fullscreen
        if (!mayChangeWindow) return
        if (opensSomethingElse) {
            repeat(SETTLE_POLLS) {
                delay(SETTLE_POLL_MS)
                val now = foregroundPackage()
                if (now != null && now != packageBefore) {
                    delay(SETTLE_POLL_MS)
                    return
                }
            }
        } else {
            Settle.awaitChangeThenQuiet({ windowStamp() }, stampBefore)
        }
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
        var target = when (val r = NodeFinder.resolveTap(root, label)) {
            NodeFinder.TapResolution.NotFound -> return fail(env, R.string.say_not_found, label)
            is NodeFinder.TapResolution.Found -> r.target
            is NodeFinder.TapResolution.Ambiguous -> {
                // One "Eliminar" per row: never guess the row for anything that needs a "sí".
                if (env.confirmedTarget != null || r.target.riskLabels.any(detector::isSensitive)) {
                    return fail(env, R.string.say_ambiguous, r.count, r.target.hit.label)
                }
                r.target
            }
        }
        // Check what will really be pressed (matched text, the clickable container and the texts inside it), not
        // what the user said: "borrar" may match "Borrar cuenta", and "Visa" may sit inside a "Pagar 49,99 €" row.
        val risky = target.riskLabels.filter { detector.isSensitive(it) }
        if (env.confirmedTarget != null) {
            // Plan-level confirmation (for example a cloud-inferred harmless tap) still
            // needs a fresh identity check, but must not ask the user twice.
            target = NodeFinder.recheck(target, service?.root(), label) ?: return fail(env, R.string.say_screen_changed)
        } else if (risky.isNotEmpty()) {
            // Name the most informative label (the one with the amount), capped so the question stays short.
            val what = risky.maxBy { it.length }.take(MAX_CONFIRM_LABEL)
            if (!env.confirm(strings.get(env.lang, R.string.confirm_tap, what))) return cancelled(env)
            // The answer took seconds: press only if the fresh screen still shows what was confirmed.
            target = NodeFinder.recheck(target, service?.root(), label) ?: return fail(env, R.string.say_screen_changed)
        }
        val clickable = target.pressed
        val pressed = if (clickable != null) {
            clickable.click()
        } else {
            // Nothing clickable in the tree (e.g. web content): tap the on-screen part of the label, never off screen.
            val metrics = context.resources.displayMetrics
            val point = NodeFinder.visibleCenter(target.hit.node.bounds, metrics.widthPixels, metrics.heightPixels)
                ?: return fail(env, R.string.say_not_found, label)
            svc.tapAt(point.first, point.second)
        }
        return if (pressed) done(env, R.string.say_tapped, target.hit.label) else fail(env, R.string.say_action_failed)
    }

    private fun type(text: String, env: ExecEnv): ExecResult {
        val svc = service ?: return needAccessibility(env)
        val field = when (val t = svc.root()?.let { NodeFinder.editTarget(it) } ?: NodeFinder.EditTarget.None) {
            is NodeFinder.EditTarget.Found -> t.node
            is NodeFinder.EditTarget.Ambiguous -> return fail(env, R.string.say_which_field, t.count)
            NodeFinder.EditTarget.None -> return fail(env, R.string.say_no_field)
        }
        // Password fields expose masked dots as text: always replace, never append.
        val existing = if (field.isShowingHint || field.isPassword) null else field.text
        val value = if (existing.isNullOrBlank()) text else "$existing $text"
        return if (field.setText(value)) done(env, R.string.say_typed) else fail(env, R.string.say_action_failed)
    }

    private fun readScreen(env: ExecEnv): ExecResult {
        if (service == null) return needAccessibility(env)
        val lines = snapshot().readableLines().map { if (it.length > MAX_READ_LINE) it.take(MAX_READ_LINE) + "…" else it }
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
        val current = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, Surface.ROTATION_0) }
            .getOrDefault(Surface.ROTATION_0)
        return rotateTo(env, landscape = current == Surface.ROTATION_0 || current == Surface.ROTATION_180)
    }

    /**
     * Portrait or landscape, idempotent. Forcing an orientation turns auto-rotate off; the user's own
     * auto-rotate choice is saved and given back when VOZ returns to portrait.
     */
    private fun rotateTo(env: ExecEnv, landscape: Boolean): ExecResult {
        if (!Settings.System.canWrite(context)) {
            launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri()))
            return ExecResult.Failed(strings.get(env.lang, R.string.say_rotate_permission))
        }
        val resolver = context.contentResolver
        return try {
            val saved = if (state.contains(KEY_SAVED_AUTO_ROTATE)) state.getInt(KEY_SAVED_AUTO_ROTATE, 0) else null
            val auto = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            val change = RotationPlan.to(landscape, auto, saved)
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, change.autoRotate)
            Settings.System.putInt(resolver, Settings.System.USER_ROTATION, if (landscape) Surface.ROTATION_90 else Surface.ROTATION_0)
            state.edit {
                val keep = change.savedAutoRotate
                if (keep == null) remove(KEY_SAVED_AUTO_ROTATE) else putInt(KEY_SAVED_AUTO_ROTATE, keep)
            }
            done(env, if (landscape) R.string.say_rotated_landscape else R.string.say_rotated_portrait)
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
                    // Saying "full screen" twice must not flip back to portrait.
                    rotateTo(env, landscape = true)
                } else if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                    if (state.contains(KEY_SAVED_AUTO_ROTATE)) rotateTo(env, landscape = false) else ExecResult.Done()
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

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, uri.toUri())

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

    /** From another app there is no other route to the switch, so the accessibility settings open too. */
    private fun needAccessibility(env: ExecEnv): ExecResult {
        launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        return fail(env, R.string.say_need_a11y)
    }

    private companion object {
        const val SETTLE_POLLS = 15
        const val SETTLE_POLL_MS = 200L
        const val MAX_READ_LINE = 200
        const val MAX_CONFIRM_LABEL = 80
        const val STATE_PREFS = "voz_state"
        const val KEY_SAVED_AUTO_ROTATE = "saved_auto_rotate"
    }
}
