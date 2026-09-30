package com.steve1316.uma_android_automation.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.steve1316.uma_android_automation.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Side of the probe's square window, in pixels. */
internal const val OWN_INPUT_PROBE_SIZE = 64

/** Where the probe's window sits: the left edge, a third of the way down, clear of the status bar. */
internal const val OWN_INPUT_PROBE_X = 0
internal const val OWN_INPUT_PROBE_Y = 640

/** How long the dispatched tap has to reach the probe's window. */
internal const val OWN_INPUT_PROBE_WAIT_MS = 3000L

/**
 * The probe's window: an overlay of this app that takes touches (no FLAG_NOT_TOUCHABLE) but never
 * focus, so the game keeps its input focus and the tap lands on this window instead of the game.
 */
internal fun ownInputProbeParams(sdkInt: Int = Build.VERSION.SDK_INT): WindowManager.LayoutParams =
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
        y = OWN_INPUT_PROBE_Y
    }

/**
 * Whether the bot's own taps still reach the screen. Shows a small window of this app, dispatches
 * one tap onto its centre through [service] (the same injection path every bot tap takes), and waits
 * for the window to receive it. The game plays no part: when the tap arrives, input works and a game
 * that ignores the bot's taps has stopped responding; when it does not, the taps themselves are dead.
 *
 * Acts first and logs after with [Log], as recovery code must. Blocks the caller for at most about
 * [OWN_INPUT_PROBE_WAIT_MS] plus 2 s; never call it on the main thread, which delivers the touch.
 *
 * @return True when the tap arrived, false when it was dispatched and did not arrive (or could not be
 *   dispatched), null when the probe could not run: no window permission, gestures paused, or no service.
 */
internal fun ownInputReachesScreen(context: Context, service: AccessibilityService?, gesturesAllowed: Boolean): Boolean? {
    if (service == null || !gesturesAllowed) return null
    val windowManager = context.getSystemService(WindowManager::class.java) ?: return null
    val main = Handler(Looper.getMainLooper())
    val shown = CountDownLatch(1)
    val touched = CountDownLatch(1)
    val window = AtomicReference<View?>(null)
    val centre = IntArray(2)
    main.post {
        try {
            val view = View(context)
            view.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touched.countDown()
                true
            }
            // The centre is read once the window is laid out; before that its size is still 0.
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
            windowManager.addView(view, ownInputProbeParams())
            window.set(view)
        } catch (e: Exception) {
            Log.w(TAG, "[INPUT_PROBE] The probe window could not be shown: ${e.javaClass.simpleName}")
            shown.countDown()
        }
    }
    try {
        if (!shown.await(2, TimeUnit.SECONDS) || window.get() == null) return null
        val path = Path().apply { moveTo(centre[0].toFloat(), centre[1].toFloat()) }
        val tap = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build()
        val dispatched = service.dispatchGesture(tap, null, null)
        val arrived = dispatched && touched.await(OWN_INPUT_PROBE_WAIT_MS, TimeUnit.MILLISECONDS)
        Log.w(TAG, "[INPUT_PROBE] Tap at (${centre[0]}, ${centre[1]}) dispatched=$dispatched arrived=$arrived.")
        return arrived
    } finally {
        main.post {
            window.getAndSet(null)?.let { view -> runCatching { windowManager.removeView(view) } }
        }
    }
}

private val TAG = "[${MainActivity.loggerTag}]OwnInputProbe"
