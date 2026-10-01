package com.steve1316.uma_android_automation.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Instrumentation
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.steve1316.automation_library.data.SharedData
import com.steve1316.uma_android_automation.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal const val OWN_INPUT_PROBE_SIZE = 64

internal const val OWN_INPUT_PROBE_X = 0
internal val OWN_INPUT_PROBE_YS = listOf(640, 960, 1280, 320)

/** The app's own touch reaches every window of this app, so a probe under the floating Start/Stop button would press it and stop the bot. */
internal const val OWN_INPUT_PROBE_BUTTON_CLEARANCE = 200

/** Null when no row is clear of the floating button (no probe is run). */
internal fun ownInputProbeY(buttonX: Int, buttonY: Int, buttonSize: Int): Int? {
    val clear = OWN_INPUT_PROBE_BUTTON_CLEARANCE
    val clearOfColumn = buttonX >= OWN_INPUT_PROBE_X + OWN_INPUT_PROBE_SIZE + clear || buttonX + buttonSize + clear <= OWN_INPUT_PROBE_X
    return OWN_INPUT_PROBE_YS.firstOrNull { y -> clearOfColumn || buttonY >= y + OWN_INPUT_PROBE_SIZE + clear || buttonY + buttonSize + clear <= y }
}

/** Read the way the overlay stores it: last dragged top-left in OverlayPrefs, else screen centre. Its window is the button plus a 2 dp margin per side. */
private fun floatingButtonRect(context: Context): Triple<Int, Int, Int> {
    val metrics = context.resources.displayMetrics
    val sizeDp = runCatching { SharedData.overlayButtonSizeDP }.getOrDefault(50f) + 4f
    val size = (sizeDp * metrics.density).toInt()
    val width = if (SharedData.displayWidth > 0) SharedData.displayWidth else metrics.widthPixels
    val height = if (SharedData.displayHeight > 0) SharedData.displayHeight else metrics.heightPixels
    val prefs = context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
    return Triple(prefs.getInt("lastX", (width - size) / 2), prefs.getInt("lastY", (height - size) / 2), size)
}

internal const val OWN_INPUT_PROBE_WAIT_MS = 3000L

/** The new window takes a moment to become touchable, so the app's own touch is retried. */
internal const val OWN_INPUT_PROBE_SELF_TOUCH_TRIES = 5
internal const val OWN_INPUT_PROBE_SELF_TOUCH_WAIT_MS = 400L

/** One injection can block up to 30 s on a window that does not finish the event, so the tries run on their own thread under this cap. */
internal const val OWN_INPUT_PROBE_SELF_TOUCH_BUDGET_MS = 3000L

/** Keeps the own touch off the centre where the tap lands, so a late own touch is never counted as the tap. */
internal const val OWN_INPUT_PROBE_SELF_TOUCH_OFFSET = OWN_INPUT_PROBE_SIZE / 4

internal fun isProbeTapTouch(localX: Float): Boolean = localX > OWN_INPUT_PROBE_SIZE / 2f - OWN_INPUT_PROBE_SELF_TOUCH_OFFSET / 2f

/** Never takes focus, so the game keeps its input focus. */
internal fun ownInputProbeParams(probeY: Int, sdkInt: Int = Build.VERSION.SDK_INT): WindowManager.LayoutParams =
    WindowManager.LayoutParams().apply {
        width = OWN_INPUT_PROBE_SIZE
        height = OWN_INPUT_PROBE_SIZE
        type =
            if (sdkInt >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.START
        x = OWN_INPUT_PROBE_X
        y = probeY
    }

internal enum class OwnInputProbeResult {
    ARRIVED,
    LOST,
    INCONCLUSIVE,
}

/** Without the own touch arriving the tap is never dispatched, since it could land on the game below. */
internal fun ownInputProbeResult(selfTouchArrived: Boolean, dispatched: Boolean, gestureArrived: Boolean): OwnInputProbeResult =
    when {
        !selfTouchArrived -> OwnInputProbeResult.INCONCLUSIVE
        dispatched && gestureArrived -> OwnInputProbeResult.ARRIVED
        else -> OwnInputProbeResult.LOST
    }

/**
 * Proves the probe window takes touches with a touch this app injects into its own windows
 * ([Instrumentation.sendPointerSync] is delivered only to this app's windows), then dispatches one tap onto it
 * through [service]. The game plays no part: an arriving tap means a game that ignores taps has stopped
 * responding; a window that took the own touch but not the tap means the taps are dead. Dispatching before the
 * window was touchable raced it on MuMu, and the tap could have landed on the game.
 *
 * Acts first and logs after with [Log], as recovery code must. Blocks up to about 2 s + the two waits; never call it on the
 * main thread, which delivers the touches.
 */
internal fun ownInputReachesScreen(context: Context, service: AccessibilityService?, gesturesAllowed: Boolean): OwnInputProbeResult {
    if (service == null || !gesturesAllowed) return OwnInputProbeResult.INCONCLUSIVE
    val windowManager = context.getSystemService(WindowManager::class.java) ?: return OwnInputProbeResult.INCONCLUSIVE
    val (buttonX, buttonY, buttonSize) = floatingButtonRect(context)
    val probeY = ownInputProbeY(buttonX, buttonY, buttonSize)
    if (probeY == null) {
        Log.w(TAG, "[INPUT_PROBE] Not run: every probe row could overlap the floating button at ($buttonX, $buttonY).")
        return OwnInputProbeResult.INCONCLUSIVE
    }
    val main = Handler(Looper.getMainLooper())
    val shown = CountDownLatch(1)
    val selfTouched = CountDownLatch(1)
    val gestureTouched = CountDownLatch(1)
    val window = AtomicReference<View?>(null)
    val centre = IntArray(2)
    main.post {
        try {
            val view = View(context)
            view.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    if (isProbeTapTouch(event.x)) gestureTouched.countDown() else selfTouched.countDown()
                }
                true
            }
            view.addOnLayoutChangeListener(
                object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                        v.removeOnLayoutChangeListener(this)
                        v.getLocationOnScreen(centre)
                        centre[0] += (right - left) / 2
                        centre[1] += (bottom - top) / 2
                        shown.countDown()
                    }
                },
            )
            windowManager.addView(view, ownInputProbeParams(probeY))
            window.set(view)
        } catch (e: Exception) {
            Log.w(TAG, "[INPUT_PROBE] The probe window could not be shown: ${e.javaClass.simpleName}")
            shown.countDown()
        }
    }
    try {
        if (!shown.await(2, TimeUnit.SECONDS) || window.get() == null) return OwnInputProbeResult.INCONCLUSIVE
        val x = centre[0].toFloat()
        val y = centre[1].toFloat()
        val injector =
            Thread {
                try {
                    var tries = 0
                    while (selfTouched.count > 0 && tries++ < OWN_INPUT_PROBE_SELF_TOUCH_TRIES) {
                        injectOwnTouch(x - OWN_INPUT_PROBE_SELF_TOUCH_OFFSET, y - OWN_INPUT_PROBE_SELF_TOUCH_OFFSET)
                        selfTouched.await(OWN_INPUT_PROBE_SELF_TOUCH_WAIT_MS, TimeUnit.MILLISECONDS)
                    }
                } catch (_: InterruptedException) {
                }
            }
        injector.isDaemon = true
        injector.start()
        val selfTouchArrived = selfTouched.await(OWN_INPUT_PROBE_SELF_TOUCH_BUDGET_MS, TimeUnit.MILLISECONDS)
        injector.interrupt()
        var dispatched = false
        var gestureArrived = false
        if (selfTouchArrived) {
            val path = Path().apply { moveTo(x, y) }
            val tap = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build()
            dispatched = service.dispatchGesture(tap, null, null)
            gestureArrived = dispatched && gestureTouched.await(OWN_INPUT_PROBE_WAIT_MS, TimeUnit.MILLISECONDS)
        }
        val result = ownInputProbeResult(selfTouchArrived, dispatched, gestureArrived)
        Log.w(TAG, "[INPUT_PROBE] At (${centre[0]}, ${centre[1]}): own touch arrived=$selfTouchArrived, tap dispatched=$dispatched arrived=$gestureArrived -> $result.")
        return result
    } finally {
        main.post {
            window.getAndSet(null)?.let { view -> runCatching { windowManager.removeView(view) } }
        }
    }
}

/** Refused (thrown or dropped) wherever another app's window is on top. */
private fun injectOwnTouch(x: Float, y: Float) {
    val down = SystemClock.uptimeMillis()
    val instrumentation = Instrumentation()
    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            instrumentation.sendPointerSync(event)
        } catch (e: Exception) {
            Log.w(TAG, "[INPUT_PROBE] The app's own touch was refused: ${e.javaClass.simpleName}")
            return
        } finally {
            event.recycle()
        }
    }
}

private val TAG = "[${MainActivity.loggerTag}]OwnInputProbe"
