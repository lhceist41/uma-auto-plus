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

/** Side of the probe's square window, in pixels. */
internal const val OWN_INPUT_PROBE_SIZE = 64

/** Where the probe's window sits: the left edge, below the status bar. The rows are tried in order,
 * a third of the way down first; [ownInputProbeY] picks one clear of the floating Start/Stop button. */
internal const val OWN_INPUT_PROBE_X = 0
internal val OWN_INPUT_PROBE_YS = listOf(640, 960, 1280, 320)

/**
 * Clearance kept around the floating Start/Stop button's saved rectangle. The app's own touch reaches
 * every window of this app, so a probe under the button would press it and stop the bot. The button's
 * window wraps it in a small margin and, laid out below the status bar, can sit lower than its saved y.
 */
internal const val OWN_INPUT_PROBE_BUTTON_CLEARANCE = 200

/**
 * The probe window's y on the left edge, clear of the floating Start/Stop button whose window has its
 * top-left at ([buttonX], [buttonY]) and side [buttonSize]; null when no row is clear (no probe is run).
 */
internal fun ownInputProbeY(buttonX: Int, buttonY: Int, buttonSize: Int): Int? {
    val clear = OWN_INPUT_PROBE_BUTTON_CLEARANCE
    val clearOfColumn = buttonX >= OWN_INPUT_PROBE_X + OWN_INPUT_PROBE_SIZE + clear || buttonX + buttonSize + clear <= OWN_INPUT_PROBE_X
    return OWN_INPUT_PROBE_YS.firstOrNull { y -> clearOfColumn || buttonY >= y + OWN_INPUT_PROBE_SIZE + clear || buttonY + buttonSize + clear <= y }
}

/**
 * Where the floating Start/Stop button is, read the way the overlay itself stores it: its last dragged
 * top-left in the OverlayPrefs preferences, else the screen centre it starts at. Its window is the
 * button plus a 2 dp margin on each side.
 */
private fun floatingButtonRect(context: Context): Triple<Int, Int, Int> {
    val metrics = context.resources.displayMetrics
    val sizeDp = runCatching { SharedData.overlayButtonSizeDP }.getOrDefault(50f) + 4f
    val size = (sizeDp * metrics.density).toInt()
    val width = if (SharedData.displayWidth > 0) SharedData.displayWidth else metrics.widthPixels
    val height = if (SharedData.displayHeight > 0) SharedData.displayHeight else metrics.heightPixels
    val prefs = context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
    return Triple(prefs.getInt("lastX", (width - size) / 2), prefs.getInt("lastY", (height - size) / 2), size)
}

/** How long the dispatched tap has to reach the probe's window. */
internal const val OWN_INPUT_PROBE_WAIT_MS = 3000L

/** The app's own touch is retried while the new window becomes touchable: up to 5 tries, 400 ms apart. */
internal const val OWN_INPUT_PROBE_SELF_TOUCH_TRIES = 5
internal const val OWN_INPUT_PROBE_SELF_TOUCH_WAIT_MS = 400L

/** Wall-clock cap on the app's own touch. One injection can block up to 30 s on a window that does not
 * finish the event, so the tries run on their own thread and the probe stops waiting after this. */
internal const val OWN_INPUT_PROBE_SELF_TOUCH_BUDGET_MS = 3000L

/** The app's own touch lands a quarter of the window up and left of the centre, where the tap lands,
 * so a late own touch can never be counted as the tap. */
internal const val OWN_INPUT_PROBE_SELF_TOUCH_OFFSET = OWN_INPUT_PROBE_SIZE / 4

/** Whether a touch at [localX] inside the probe's window is the accessibility tap, not the app's own touch. */
internal fun isProbeTapTouch(localX: Float): Boolean = localX > OWN_INPUT_PROBE_SIZE / 2f - OWN_INPUT_PROBE_SELF_TOUCH_OFFSET / 2f

/**
 * The probe's window: an overlay of this app that takes touches (no FLAG_NOT_TOUCHABLE) but never
 * focus, so the game keeps its input focus and the tap lands on this window instead of the game.
 */
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

/** What the own-input probe proved. */
internal enum class OwnInputProbeResult {
    /** The accessibility tap reached the probe's window: the bot's taps work. */
    ARRIVED,

    /** The probe's window provably took touches, and the accessibility tap did not reach it: the taps are dead. */
    LOST,

    /** Nothing was proven: the probe could not run, or its window never took this app's own touch. */
    INCONCLUSIVE,
}

/**
 * The probe's verdict. Only [selfTouchArrived] (this app's own injected touch reached the window, so
 * the window takes touches at its centre) lets a missing accessibility tap mean dead input; without
 * it the tap is never dispatched, since it could land on the game below.
 */
internal fun ownInputProbeResult(selfTouchArrived: Boolean, dispatched: Boolean, gestureArrived: Boolean): OwnInputProbeResult =
    when {
        !selfTouchArrived -> OwnInputProbeResult.INCONCLUSIVE
        dispatched && gestureArrived -> OwnInputProbeResult.ARRIVED
        else -> OwnInputProbeResult.LOST
    }

/**
 * Whether the bot's own taps still reach the screen. Shows a small window of this app and proves it
 * takes touches with a touch this app injects into its own windows ([Instrumentation.sendPointerSync]:
 * Android delivers that only to windows of this app, refusing it anywhere else, and syncs the window's
 * input state first). Then dispatches one tap onto the window's centre through [service] (the injection
 * path every bot tap takes) and waits for the window to receive it. The game plays no part: when the tap
 * arrives, input works and a game that ignores the bot's taps has stopped responding; when the window
 * took the app's own touch and not the tap, the taps themselves are dead.
 *
 * Dispatching as soon as the window was laid out (the first version) raced the window becoming
 * touchable: on a healthy MuMu game the tap never arrived (2026-09-30, twice), and it may have landed
 * on the game instead.
 *
 * Acts first and logs after with [Log], as recovery code must. Blocks the caller for at most about
 * 2 s (window) + [OWN_INPUT_PROBE_SELF_TOUCH_BUDGET_MS] + [OWN_INPUT_PROBE_WAIT_MS]; never call it on
 * the main thread, which delivers the touches.
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

/** One DOWN/UP pair injected into this app's own windows. Refused (thrown or dropped) wherever another app's window is on top. */
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
