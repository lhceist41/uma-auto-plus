package com.steve1316.uma_android_automation.utils

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Keeps the device screen on while a bot session runs, so the screen timeout cannot end an
 * unattended queue. Android keeps the screen on while a shown window carries FLAG_KEEP_SCREEN_ON,
 * and the library's floating button window cannot carry it (its params are private), so this adds
 * its own 1x1 transparent overlay in the top corner. The window never takes focus or touches, so
 * the player's taps and the bot's gestures reach the game, and a transparent pixel never shows in a
 * capture. Turning the screen off with the power button still stops the bot, through the library's
 * screen-off receiver.
 *
 * [start] and [stop] can be called from any thread; the window is only touched on the main looper,
 * in call order. Neither ever throws.
 */
internal object KeepScreenOn {
    private const val TAG = "KeepScreenOn"

    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Main looper only. */
    private var hold: ScreenHold? = null

    /**
     * Adds the window unless the overlay permission is missing, in which case it returns false and
     * adds nothing. The window is actually added later, on the main looper, so a failed add cannot
     * be reflected in this method's own return value; [onHoldFailed] runs then, on the main looper,
     * if it happens, so the caller (session code) can log it there instead of this utility reaching
     * into MessageLog itself. [onHoldFailed] runs inside this method's own try, so a throwing
     * callback cannot crash the main looper; it should still do its own logging off that thread
     * (for example from a short daemon thread), since the main looper must never wait on
     * MessageLog's lock.
     */
    fun start(context: Context, onHoldFailed: (() -> Unit)? = null): Boolean {
        val app = context.applicationContext
        return try {
            if (!Settings.canDrawOverlays(app)) return false
            main.post {
                val current = hold ?: screenHoldFor(app).also { hold = it }
                holdAndReportFailure(current::hold, onHoldFailed)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not keep the screen on: ${e.javaClass.simpleName}")
            false
        }
    }

    fun stop() {
        try {
            main.post { hold?.release() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not release the screen: ${e.javaClass.simpleName}")
        }
    }

    private fun screenHoldFor(app: Context): ScreenHold {
        val windowManager = app.getSystemService(WindowManager::class.java)
        val view = View(app)
        return ScreenHold(attach = { windowManager.addView(view, keepScreenOnParams()) }, detach = { windowManager.removeView(view) })
    }
}

/**
 * Runs [hold]; if it returns false, runs [onHoldFailed], catching anything it throws so a failing
 * caller-supplied callback can never escape onto whatever thread this runs on (the main looper,
 * called from [KeepScreenOn.start]). Pure apart from its two functional parameters, so this is unit
 * tested directly instead of through the main looper.
 */
internal fun holdAndReportFailure(hold: () -> Boolean, onHoldFailed: (() -> Unit)?) {
    if (!hold()) {
        try {
            onHoldFailed?.invoke()
        } catch (e: Exception) {
            Log.w("KeepScreenOn", "onHoldFailed threw: ${e.javaClass.simpleName}")
        }
    }
}

/**
 * The on/off bookkeeping for the window, apart from Android so it can be tested: a second hold or
 * release does nothing, and a failed attach or detach is logged, never thrown, so it cannot break
 * the session that asked for it.
 */
internal class ScreenHold(private val attach: () -> Unit, private val detach: () -> Unit) {
    var held = false
        private set

    fun hold(): Boolean {
        if (held) return true
        return try {
            attach()
            held = true
            true
        } catch (e: Exception) {
            Log.w("KeepScreenOn", "The keep-screen-on window could not be added: ${e.javaClass.simpleName}")
            false
        }
    }

    fun release() {
        if (!held) return
        held = false
        try {
            detach()
        } catch (e: Exception) {
            Log.w("KeepScreenOn", "The keep-screen-on window could not be removed: ${e.javaClass.simpleName}")
        }
    }
}

/**
 * Android 12+ passes touches through a window of another app only when its opacity is at most
 * InputManager's maximum obscuring opacity, 0.8 by default. The pixel draws nothing either way,
 * so a small positive value gives the same invisible pixel with margin: 0.8 sits exactly on the
 * default threshold, which an OEM or a developer can lower via Settings.Global, leaving no room.
 */
internal const val KEEP_SCREEN_ON_ALPHA = 0.1f

internal fun keepScreenOnParams(sdkInt: Int = Build.VERSION.SDK_INT): WindowManager.LayoutParams =
    WindowManager.LayoutParams().apply {
        width = 1
        height = 1
        type =
            if (sdkInt >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
        flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 0
        alpha = KEEP_SCREEN_ON_ALPHA
    }
