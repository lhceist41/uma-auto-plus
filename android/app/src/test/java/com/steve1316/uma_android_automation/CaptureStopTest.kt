package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The notification's STOP stops only the capture service; the bot loop used to keep running on the
 * library's cached frame and tap blind. The decision rules are tested directly; the wiring into
 * StartModule (a React module) and CustomImageUtils is guarded on source, like the other
 * StartModule guards.
 */
@DisplayName("Screen capture loss stops the run")
class CaptureStopTest {
    @Nested
    @DisplayName("the capture-stopped event")
    inner class Event {
        @Test
        fun `the library's projection stop is recognised`() {
            assertTrue(isCaptureStoppedEvent("MediaProjectionService", "Not Running"))
        }

        @Test
        fun `a projection start is not a stop`() {
            assertFalse(isCaptureStoppedEvent("MediaProjectionService", "Running"))
        }

        @Test
        fun `the bot's own end event is not a capture stop`() {
            assertFalse(isCaptureStoppedEvent("BotService", "Not Running"))
        }
    }

    @Nested
    @DisplayName("when a stop is requested")
    inner class Decision {
        @Test
        fun `a running session with no stop pending is stopped`() {
            assertTrue(shouldStopForLostCapture(sessionActive = true, stopAlreadyRequested = false))
        }

        @Test
        fun `no session means nothing to stop`() {
            assertFalse(shouldStopForLostCapture(sessionActive = false, stopAlreadyRequested = false))
        }

        @Test
        fun `a stop already pending is left alone`() {
            assertFalse(shouldStopForLostCapture(sessionActive = true, stopAlreadyRequested = true))
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val startModule by lazy { source("StartModule.kt") }
        private val imageUtils by lazy { source("utils/CustomImageUtils.kt") }

        private fun body(text: String, signature: String, length: Int = 900): String {
            val i = text.indexOf(signature)
            assertTrue(i >= 0, "$signature must exist")
            return text.substring(i, minOf(text.length, i + length))
        }

        @Test
        fun `every EventBus JSEvent is checked for a capture stop before the internal filter`() {
            val onJs = body(startModule, "fun onJSEvent(event: JSEvent)", 400)
            val check = onJs.indexOf("if (isCaptureStoppedEvent(event.eventName, event.message)) stopForLostCapture()")
            val internal = onJs.indexOf("if (event.isInternal) return")
            assertTrue(check >= 0, "onJSEvent must call stopForLostCapture on a capture stop")
            assertTrue(internal > check, "the capture check must run before internal events return early")
        }

        @Test
        fun `the stop sets the queue stop flag before it logs`() {
            val stop = body(startModule, "internal fun stopForLostCapture()", 500)
            val gate = stop.indexOf("if (!shouldStopForLostCapture(sessionActive.get(), queueStopRequested)) return")
            val flag = stop.indexOf("queueStopRequested = true")
            val log = stop.indexOf("Log.w(TAG,")
            assertTrue(gate >= 0, "the stop must be gated on a running session")
            assertTrue(flag > gate, "the flag must be set after the gate")
            assertTrue(log > flag, "logging comes after the stop action")
            assertFalse(stop.substring(0, log).contains("MessageLog"), "the stop path must not log through MessageLog before acting")
        }

        @Test
        fun `a frame is never handed out once capture has stopped`() {
            val get = body(imageUtils, "override fun getSourceBitmap(saveImage: Boolean): Bitmap", 700)
            val guard = get.indexOf("if (!MediaProjectionService.isRunning)")
            val stop = get.indexOf("StartModule.stopForLostCapture()")
            val thrown = get.indexOf("throw InterruptedException(")
            val capture = get.indexOf("val bitmap = super.getSourceBitmap(saveImage)")
            assertTrue(guard >= 0, "getSourceBitmap must check that capture is running")
            assertTrue(stop in guard until thrown, "the guard must request the stop, then throw")
            assertTrue(capture > thrown, "the library capture must come after the guard")
        }

        @Test
        fun `a pending stop ends the loop at its next wait`() {
            val wait = body(source("bot/Game.kt"), "fun wait(seconds: Double", 1400)
            assertTrue(wait.contains("if (StartModule.queueStopRequested) {\n                throw InterruptedException()"), "Game.wait must throw on a pending queue stop")
        }

        private fun source(path: String): String {
            val rel = "android/app/src/main/java/com/steve1316/uma_android_automation/$path"
            var dir: File? = File("").absoluteFile
            while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
            return File(dir, rel).readText().replace("\r\n", "\n")
        }
    }
}
