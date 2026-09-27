package com.steve1316.uma_android_automation.utils

import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File

/**
 * The keep-screen-on window: its parameters, its on/off bookkeeping, and the session that holds it.
 * The window itself needs a device; these pin what decides whether it keeps the screen on and stays
 * out of the way.
 */
@DisplayName("Keep screen on")
class KeepScreenOnTest {
    @Nested
    @DisplayName("the window")
    inner class Window {
        private val params = keepScreenOnParams(sdkInt = 34)

        @Test
        fun `it keeps the screen on and never takes focus or touches`() {
            assertEquals(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                params.flags,
            )
        }

        @Test
        fun `it is one transparent pixel in the top corner`() {
            assertEquals(1, params.width)
            assertEquals(1, params.height)
            assertEquals(PixelFormat.TRANSLUCENT, params.format)
            assertEquals(Gravity.TOP or Gravity.START, params.gravity)
            assertEquals(0, params.x)
            assertEquals(0, params.y)
        }

        @Test
        fun `touches still reach the game below on Android 12 and newer, with margin below the default threshold`() {
            assertTrue(params.alpha > 0f && params.alpha <= 0.1f, "small and positive, well under the default maximum obscuring opacity of 0.8: ${params.alpha}")
        }

        @Test
        fun `it is an app overlay, with the pre-Oreo type on older Android`() {
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, keepScreenOnParams(sdkInt = 26).type)
            @Suppress("DEPRECATION")
            assertEquals(WindowManager.LayoutParams.TYPE_PHONE, keepScreenOnParams(sdkInt = 25).type)
        }
    }

    @Nested
    @DisplayName("holding and releasing")
    inner class Hold {
        private var attached = 0
        private var detached = 0

        private fun hold(attach: () -> Unit = { attached++ }, detach: () -> Unit = { detached++ }) = ScreenHold(attach, detach)

        @Test
        fun `a second hold or release does nothing`() {
            val h = hold()
            assertTrue(h.hold())
            assertTrue(h.hold())
            assertEquals(1, attached)
            h.release()
            h.release()
            assertEquals(1, detached)
            assertTrue(h.hold())
            assertEquals(2, attached, "a new session holds again")
        }

        @Test
        fun `a release without a hold detaches nothing`() {
            hold().release()
            assertEquals(0, detached)
        }

        @Test
        fun `a failed attach is reported, not thrown, and holds nothing`() {
            val h = hold(attach = { throw WindowManager.BadTokenException("no permission") })
            assertFalse(assertDoesNotThrow<Boolean> { h.hold() })
            assertFalse(h.held)
            h.release()
            assertEquals(0, detached, "nothing to remove")
        }

        @Test
        fun `a failed detach is not thrown and leaves nothing held`() {
            val h = hold(detach = { throw IllegalArgumentException("not attached") })
            h.hold()
            assertDoesNotThrow { h.release() }
            assertFalse(h.held)
        }

        @Test
        fun `a missing overlay permission is a plain false, never an exception`() {
            // Without a device, the permission reads as not granted.
            assertFalse(assertDoesNotThrow<Boolean> { KeepScreenOn.start(ContextWrapper(null)) })
            assertDoesNotThrow { KeepScreenOn.stop() }
        }
    }

    @Nested
    @DisplayName("reporting a failed hold")
    inner class HoldFailureReport {
        @Test
        fun `a failed hold invokes the callback`() {
            var invoked = 0
            holdAndReportFailure(hold = { false }, onHoldFailed = { invoked++ })
            assertEquals(1, invoked)
        }

        @Test
        fun `a successful hold never invokes the callback`() {
            var invoked = 0
            holdAndReportFailure(hold = { true }, onHoldFailed = { invoked++ })
            assertEquals(0, invoked)
        }

        @Test
        fun `no callback is fine on a failed hold`() {
            assertDoesNotThrow { holdAndReportFailure(hold = { false }, onHoldFailed = null) }
        }

        @Test
        fun `a throwing callback does not escape`() {
            assertDoesNotThrow { holdAndReportFailure(hold = { false }, onHoldFailed = { throw RuntimeException("boom") }) }
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"
        private val keeper by lazy { source("$main/utils/KeepScreenOn.kt") }
        private val startModule by lazy { source("$main/StartModule.kt") }

        @Test
        fun `the permission is checked before anything is posted, and the window is only touched on the main looper`() {
            val start = keeper.substringAfter("fun start(context: Context, onHoldFailed: (() -> Unit)? = null): Boolean {").substringBefore("\n    }\n")
            val check = start.indexOf("if (!Settings.canDrawOverlays(app)) return false")
            assertTrue(check in 0 until start.indexOf("main.post {"))
            assertTrue(keeper.substringAfter("fun stop() {").substringBefore("\n    }\n").contains("main.post { hold?.release() }"))
            assertEquals(2, Regex("main\\.post \\{").findAll(keeper).count())
            assertEquals(1, Regex("addView\\(").findAll(keeper).count())
            assertEquals(1, Regex("removeView\\(").findAll(keeper).count())
            assertTrue(keeper.contains("val app = context.applicationContext"))
        }

        @Test
        fun `the view stays visible, so the window holds the flag`() {
            assertFalse(Regex("View\\.(GONE|INVISIBLE)|visibility").containsMatchIn(keeper))
        }

        @Test
        fun `the session holds the screen from its first statement and releases it in the finally`() {
            val session = startModule.substring(startModule.indexOf("fun onStartEvent(event: StartEvent)"))
            val tryAt = session.indexOf("\n            try {\n")
            val add = session.indexOf("if (!KeepScreenOn.start(context, onHoldFailed = {")
            assertTrue(add > tryAt && session.substring(tryAt, add).count { it == '\n' } <= 3, "the first statement inside the session try")
            assertTrue(session.substring(add).substringBefore("\n                }\n").contains("MessageLog.w(TAG, \"[START] UMA Auto+ cannot draw over other apps"))
            val finallyAt = session.indexOf("\n            } finally {\n")
            val release = session.indexOf("KeepScreenOn.stop()")
            assertTrue(release > finallyAt && release < session.indexOf("sessionActive.set(false)"), "released in the finally, before the latch opens")
            assertEquals(1, Regex("KeepScreenOn\\.start\\(").findAll(startModule).count())
            assertEquals(1, Regex("KeepScreenOn\\.stop\\(").findAll(startModule).count())
        }

        @Test
        fun `the screen timeout is no longer probed`() {
            assertFalse(startModule.contains("fun getScreenTimeout("))
            assertFalse(source("src/lib/preflightWarnings.ts").contains("getScreenTimeout"))
        }
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative).readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }
}
