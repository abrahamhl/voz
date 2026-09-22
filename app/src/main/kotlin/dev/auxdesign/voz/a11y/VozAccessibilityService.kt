package dev.auxdesign.voz.a11y

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.auxdesign.voz.graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The hands of VOZ: reads the active window and performs taps, scrolls, typing and global actions.
 * It only acts when the voice session asks it to; it never logs or uploads what it sees.
 */
class VozAccessibilityService : AccessibilityService() {

    @Volatile
    var foregroundPackage: String? = null
        private set

    private var buttonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        A11yBridge.attach(this)
        val callback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                graph.session.toggle()
            }
        }
        accessibilityButtonController.registerAccessibilityButtonCallback(callback)
        buttonCallback = callback
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != packageName) foregroundPackage = pkg
    }

    override fun onInterrupt() {
        graph.tts.stop()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        buttonCallback?.let { accessibilityButtonController.unregisterAccessibilityButtonCallback(it) }
        buttonCallback = null
        A11yBridge.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        A11yBridge.detach(this)
        super.onDestroy()
    }

    /** Root of the window the user is looking at, or null. */
    fun root(): UiNode? = rootInActiveWindow?.let { A11yUiNode(it, null) }

    suspend fun tapAt(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatch(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS)).build())
    }

    /** Finger swipe; [fingerUp] = true scrolls content down (like reading further). */
    suspend fun swipeVertical(fingerUp: Boolean): Boolean {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val high = metrics.heightPixels * 0.3f
        val low = metrics.heightPixels * 0.7f
        val path = Path().apply {
            moveTo(x, if (fingerUp) low else high)
            lineTo(x, if (fingerUp) high else low)
        }
        return dispatch(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_MS)).build())
    }

    private suspend fun dispatch(gesture: GestureDescription): Boolean = suspendCancellableCoroutine { cont ->
        val accepted = dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            },
            null,
        )
        if (!accepted && cont.isActive) cont.resume(false)
    }

    private companion object {
        const val TAP_MS = 60L
        const val SWIPE_MS = 300L
    }
}

/** Lets the rest of the app reach the running service (if the user enabled it). */
object A11yBridge {
    private val current = MutableStateFlow<VozAccessibilityService?>(null)
    val service: StateFlow<VozAccessibilityService?> = current.asStateFlow()

    fun attach(service: VozAccessibilityService) {
        current.value = service
    }

    fun detach(service: VozAccessibilityService) {
        if (current.value === service) current.value = null
    }

    /** True if the user switched the service on in system settings (even if not bound yet). */
    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, VozAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

/** [UiNode] backed by a live AccessibilityNodeInfo. Children are fetched lazily (each is an IPC call). */
class A11yUiNode(private val info: AccessibilityNodeInfo, private val knownParent: UiNode?) : UiNode {
    override val text: String? get() = info.text?.toString()
    override val description: String? get() = info.contentDescription?.toString()
    override val viewId: String? get() = info.viewIdResourceName
    override val className: String? get() = info.className?.toString()
    override val packageName: String? get() = info.packageName?.toString()
    override val isClickable: Boolean get() = info.isClickable
    override val isEnabled: Boolean get() = info.isEnabled
    override val isEditable: Boolean get() = info.isEditable
    override val isScrollable: Boolean get() = info.isScrollable
    override val isVisible: Boolean get() = info.isVisibleToUser
    override val isFocused: Boolean get() = info.isFocused
    override val isShowingHint: Boolean get() = info.isShowingHintText
    override val bounds: Box
        get() {
            val r = Rect()
            info.getBoundsInScreen(r)
            return Box(r.left, r.top, r.right, r.bottom)
        }

    override val parent: UiNode? by lazy { knownParent ?: info.parent?.let { A11yUiNode(it, null) } }

    override val children: List<UiNode> by lazy {
        (0 until info.childCount).mapNotNull { i -> info.getChild(i)?.let { A11yUiNode(it, this) } }
    }

    override fun click(): Boolean = info.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    override fun setText(value: String): Boolean {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        return info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun scroll(forward: Boolean): Boolean =
        info.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
}
