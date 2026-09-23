package dev.auxdesign.voz.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import dev.auxdesign.voz.MainActivity
import dev.auxdesign.voz.R
import dev.auxdesign.voz.data.BubbleSize
import dev.auxdesign.voz.graph
import dev.auxdesign.voz.util.Permissions
import dev.auxdesign.voz.voice.OrbState
import dev.auxdesign.voz.voice.VoiceSession
import dev.auxdesign.voz.voice.busy
import dev.auxdesign.voz.voice.orbState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Floating mic drawn over other apps. Runs as a microphone foreground service so VOZ can listen
 * while another app is in front. Started only from the app UI (foreground), never restarted in background.
 */
class BubbleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var bubble: ImageView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // startForegroundService() requires startForeground() before any early exit, or Android kills the app.
        if (!startInForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Permissions.canOverlay(this)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) addBubble()
        runningState.value = true
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // After a rotation the old position may be off-screen.
        val view = bubble ?: return
        val p = params ?: return
        clamp(p)
        runCatching { windowManager.updateViewLayout(view, p) }
    }

    override fun onDestroy() {
        touchPassThrough = null
        scope.cancel()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        params = null
        runningState.value = false
        super.onDestroy()
    }

    private fun startInForeground(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notif_bubble_title))
            .setContentText(getString(R.string.notif_bubble_text))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: RuntimeException) {
            // Not allowed right now (e.g. mic permission missing or started from background).
            false
        }
    }

    private fun addBubble() {
        val metrics = resources.displayMetrics
        val size = sizePx(graph.settings.value?.bubbleSize ?: BubbleSize.MEDIUM)
        val view = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            contentDescription = getString(R.string.orb_cd)
            isFocusable = true
            setOnClickListener { graph.session.toggle() }
        }
        val p = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - size - (16 * metrics.density).roundToInt()
            y = metrics.heightPixels / 3
        }
        clamp(p)
        applySize(view, p, size)
        view.setOnTouchListener(DragToMove(p))
        addMoveActions(view, p)
        windowManager.addView(view, p)
        bubble = view
        params = p
        render(view, OrbState.IDLE)
        touchPassThrough = { through ->
            p.flags = if (through) p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { windowManager.updateViewLayout(view, p) }
        }
        scope.launch {
            val session = graph.session
            combine(session.turn, session.micOpen, session.phase) { turn, micOpen, phase ->
                orbState(turn, micOpen, phase == VoiceSession.Phase.SPEAKING)
            }.distinctUntilChanged().collect { render(view, it) }
        }
        scope.launch {
            graph.settings.map { it?.bubbleSize ?: BubbleSize.MEDIUM }.distinctUntilChanged().collect { bubbleSize ->
                val px = sizePx(bubbleSize)
                if (px != p.width) {
                    applySize(view, p, px)
                    clamp(p)
                    runCatching { windowManager.updateViewLayout(view, p) }
                }
            }
        }
    }

    private fun sizePx(size: BubbleSize): Int = (size.dp * resources.displayMetrics.density).roundToInt()

    private fun applySize(view: ImageView, p: WindowManager.LayoutParams, size: Int) {
        p.width = size
        p.height = size
        val pad = size / 4
        view.setPadding(pad, pad, pad, pad)
    }

    /** Navy at rest or working, yellow only while the mic is open; icon + state description, never colour alone. */
    private fun render(view: ImageView, state: OrbState) {
        val micOpen = state == OrbState.LISTENING || state == OrbState.CONFIRMING
        val fill = ContextCompat.getColor(this, if (micOpen) R.color.voz_voice else R.color.voz_brand)
        val fg = ContextCompat.getColor(this, if (micOpen) R.color.voz_on_voice else R.color.voz_on_brand)
        (view.background as? GradientDrawable)?.apply {
            setColor(fill)
            setStroke((2 * resources.displayMetrics.density).roundToInt(), fg)
        }
        view.setImageResource(if (state.busy && !micOpen) R.drawable.ic_stop else R.drawable.ic_mic)
        view.imageTintList = ColorStateList.valueOf(fg)
        ViewCompat.setStateDescription(view, getString(stateText(state)))
        val clickLabel = getString(if (state.busy) R.string.orb_action_stop else R.string.orb_action_start)
        ViewCompat.replaceAccessibilityAction(view, AccessibilityActionCompat.ACTION_CLICK, clickLabel) { v, _ -> v.performClick() }
    }

    @StringRes
    private fun stateText(state: OrbState): Int = when (state) {
        OrbState.IDLE -> R.string.orb_sd_idle
        OrbState.STARTING -> R.string.orb_starting
        OrbState.LISTENING -> R.string.orb_listening
        OrbState.UNDERSTANDING, OrbState.ACTING -> R.string.orb_sd_understanding
        OrbState.SPEAKING -> R.string.orb_speaking
        OrbState.CONFIRMING -> R.string.orb_sd_confirming
    }

    /** Screen-reader and switch users can't drag: they get "Move left/right/up/down" actions instead. */
    private fun addMoveActions(view: View, p: WindowManager.LayoutParams) {
        listOf(
            R.string.bubble_move_left to (-1 to 0),
            R.string.bubble_move_right to (1 to 0),
            R.string.bubble_move_up to (0 to -1),
            R.string.bubble_move_down to (0 to 1),
        ).forEach { (label, dir) ->
            ViewCompat.addAccessibilityAction(view, getString(label)) { v, _ ->
                p.x += dir.first * p.width
                p.y += dir.second * p.height
                clamp(p)
                runCatching { windowManager.updateViewLayout(v, p) }.isSuccess
            }
        }
    }

    private fun clamp(p: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val clamped = BubbleBounds.clamp(p.x, p.y, p.width, metrics.widthPixels, metrics.heightPixels)
        p.x = clamped.first
        p.y = clamped.second
    }

    /** Drag moves the bubble; a tap is a click (TalkBack double-tap also clicks). A tremor must not turn a tap into a drag. */
    private inner class DragToMove(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@BubbleService).scaledTouchSlop * TREMOR_SLOP_FACTOR
        private var startX = 0
        private var startY = 0
        private var downX = 0f
        private var downY = 0f
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        clamp(params)
                        windowManager.updateViewLayout(v, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) v.performClick()
            }
            return true
        }
    }

    companion object {
        private const val CHANNEL_ID = "voz_bubble"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "dev.auxdesign.voz.action.STOP_BUBBLE"
        private const val TREMOR_SLOP_FACTOR = 3
        private const val PASS_THROUGH_SETTLE_MS = 32L

        /** Set while a bubble is shown: lets touches through it (true) or not (false). */
        @Volatile
        private var touchPassThrough: ((Boolean) -> Unit)? = null

        /**
         * Runs an injected gesture while the bubble lets touches through, so a tap under the bubble reaches the
         * app and never toggles VOZ itself.
         */
        suspend fun <T> withTouchesPassingThrough(block: suspend () -> T): T {
            val hook = touchPassThrough ?: return block()
            withContext(Dispatchers.Main.immediate) { hook(true) }
            delay(PASS_THROUGH_SETTLE_MS)
            try {
                return block()
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) { hook(false) }
            }
        }

        private val runningState = MutableStateFlow(false)
        val running: StateFlow<Boolean> = runningState.asStateFlow()

        /** Call from the UI while the app is in the foreground. Returns false if it could not start. */
        fun start(context: Context): Boolean {
            if (!canStart(context)) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, BubbleService::class.java))
                true
            } catch (e: RuntimeException) {
                // e.g. ForegroundServiceStartNotAllowedException when not in the foreground.
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }

        fun canStart(context: Context): Boolean = Permissions.hasMic(context) && Settings.canDrawOverlays(context)
    }
}

/** Keeps the bubble fully on screen (pure, unit-tested). */
object BubbleBounds {
    fun clamp(x: Int, y: Int, size: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> =
        x.coerceIn(0, (screenWidth - size).coerceAtLeast(0)) to y.coerceIn(0, (screenHeight - size).coerceAtLeast(0))
}
