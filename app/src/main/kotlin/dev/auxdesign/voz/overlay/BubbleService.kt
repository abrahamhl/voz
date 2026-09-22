package dev.auxdesign.voz.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.auxdesign.voz.MainActivity
import dev.auxdesign.voz.R
import dev.auxdesign.voz.data.VozSettings
import dev.auxdesign.voz.graph
import dev.auxdesign.voz.util.Permissions
import dev.auxdesign.voz.voice.VoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Permissions.canOverlay(this) || !startInForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) addBubble()
        runningState.value = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
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
        val settings = graph.settings.value ?: VozSettings()
        val metrics = resources.displayMetrics
        val size = (settings.bubbleSize.dp * metrics.density).roundToInt()
        val view = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            val pad = size / 4
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@BubbleService, R.color.voz_brand))
                setStroke((2 * metrics.density).roundToInt(), Color.WHITE)
            }
            contentDescription = getString(R.string.bubble_cd)
            isFocusable = true
            setOnClickListener { graph.session.toggle() }
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - size - (16 * metrics.density).roundToInt()
            y = metrics.heightPixels / 3
        }
        view.setOnTouchListener(DragToMove(params))
        windowManager.addView(view, params)
        bubble = view
        scope.launch {
            graph.session.phase.collect { phase ->
                val busy = phase != VoiceSession.Phase.IDLE
                val color = ContextCompat.getColor(this@BubbleService, if (busy) R.color.voz_listening else R.color.voz_brand)
                (view.background as? GradientDrawable)?.setColor(color)
                view.contentDescription = getString(if (busy) R.string.bubble_cd_busy else R.string.bubble_cd)
            }
        }
    }

    /** Drag moves the bubble; a tap without movement is a click (TalkBack double-tap also clicks). */
    private inner class DragToMove(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@BubbleService).scaledTouchSlop
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

        private val runningState = MutableStateFlow(false)
        val running: StateFlow<Boolean> = runningState.asStateFlow()

        /** Call from the UI while the app is in the foreground. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BubbleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }

        fun canStart(context: Context): Boolean = Permissions.hasMic(context) && Settings.canDrawOverlays(context)
    }
}
