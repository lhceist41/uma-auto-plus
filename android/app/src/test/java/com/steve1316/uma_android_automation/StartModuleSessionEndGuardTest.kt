package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Home used to have no truthful signal for "the bot actually stopped running": BotService only
 * ever posted "Running", so a session that ended naturally left projection-armed UI looking
 * like the bot was still going. The fix adds a "Not Running" end event in onStartEvent's
 * finally, but it must travel through the exact same FIFO the existing "Running" event uses
 * (jsEventQueue, via a shared enqueue helper) rather than a direct sendEvent/EventBus post -- a
 * direct emission could overtake the still-queued start event on a fast-abort session and leave
 * Home latched on "running" forever.
 *
 * StartModule is a React module that is impractical to unit-test directly, so these are source
 * guards on the wiring rather than behavioral tests of the queue draining.
 */
@DisplayName("StartModule session-end event guard")
class StartModuleSessionEndGuardTest {
    private val startModule by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt")
            .readText().replace("\r\n", "\n")
    }

    private val botEndCall = "enqueueJsEvent(JSEvent(\"BotService\", \"Not Running\", false))"

    /** The onStartEvent `finally` block, from the `} finally {` guarding the wake lock release
     * through the closing brace of onStartEvent itself. Narrow enough that a direct sendEvent or
     * EventBus.post ANYWHERE ELSE in the file (both are used legitimately elsewhere) cannot make
     * these assertions pass by accident. */
    private val onStartEventFinally by lazy {
        val releaseIdx = startModule.indexOf("Game.releaseWakeLock()")
        assertTrue(releaseIdx >= 0, "the wake lock release must exist")
        val finallyIdx = startModule.lastIndexOf("} finally {", releaseIdx)
        assertTrue(finallyIdx in 0 until releaseIdx, "a finally block must directly precede the wake lock release")
        val end = startModule.indexOf("\n    /**\n     * Tests the Discord connection", finallyIdx)
        assertTrue(end > finallyIdx, "onStartEvent must end before the Discord test method")
        startModule.substring(finallyIdx, end)
    }

    @Nested
    @DisplayName("the bot-end event is enqueued from the right place")
    inner class Placement {
        @Test
        fun `the bot-end enqueue is inside the onStartEvent finally block`() {
            assertTrue(onStartEventFinally.contains(botEndCall), "the bot-end enqueue must be in onStartEvent's finally")
        }

        @Test
        fun `it is ordered after the session latch cleanup`() {
            val latchIdx = onStartEventFinally.indexOf("sessionActive.set(false)")
            val enqueueIdx = onStartEventFinally.indexOf(botEndCall)
            assertTrue(latchIdx >= 0, "the session latch release must exist in the finally block")
            assertTrue(enqueueIdx > latchIdx, "the bot-end event must be enqueued after the session latch is released")
        }

        @Test
        fun `there is exactly one bot-end enqueue call`() {
            assertEquals(1, startModule.split(botEndCall).size - 1, "exactly one bot-end enqueue in the whole file")
        }
    }

    @Nested
    @DisplayName("the bot-end event uses the shared FIFO, not a direct emission")
    inner class FifoOnly {
        @Test
        fun `it goes through the shared enqueueJsEvent helper`() {
            assertTrue(onStartEventFinally.contains("enqueueJsEvent(JSEvent("), "the bot-end event must be constructed and enqueued via enqueueJsEvent")
        }

        @Test
        fun `it does not call sendEvent directly for the bot-end emission`() {
            assertFalse(onStartEventFinally.contains("sendEvent("), "sendEvent bypasses the FIFO and could overtake the queued start event")
        }

        @Test
        fun `it does not post directly to EventBus for the bot-end emission`() {
            assertFalse(onStartEventFinally.contains("EventBus.getDefault().post"), "EventBus.post bypasses the FIFO the same way a direct sendEvent would")
        }
    }

    @Nested
    @DisplayName("the shared helper preserves the existing onJSEvent path")
    inner class HelperSharedWithOnJSEvent {
        private val enqueueJsEventHelper by lazy {
            val start = startModule.indexOf("private fun enqueueJsEvent(event: JSEvent) {")
            assertTrue(start >= 0, "the shared enqueue helper must exist")
            val end = startModule.indexOf("\n    }\n", start)
            assertTrue(end > start, "the helper body must be closeable")
            startModule.substring(start, end)
        }

        private val onJSEvent by lazy {
            val start = startModule.indexOf("fun onJSEvent(event: JSEvent) {")
            assertTrue(start >= 0, "onJSEvent must exist")
            val end = startModule.indexOf("\n    }\n", start)
            assertTrue(end > start, "onJSEvent body must be closeable")
            startModule.substring(start, end)
        }

        @Test
        fun `the helper still offers onto jsEventQueue with the drop-oldest fallback`() {
            assertTrue(enqueueJsEventHelper.contains("jsEventQueue.offer(event)"), "the helper must offer the event onto the FIFO")
            assertTrue(enqueueJsEventHelper.contains("jsEventQueue.poll()"), "the drop-oldest fallback on a full queue must be preserved")
            assertTrue(enqueueJsEventHelper.contains("ensureJsEventWorker()"), "the worker thread must still be started lazily")
        }

        @Test
        fun `onJSEvent still filters internal events before delegating to the helper`() {
            assertTrue(onJSEvent.contains("if (event.isInternal) return"), "internal events must still be filtered before reaching the FIFO")
            assertTrue(onJSEvent.contains("enqueueJsEvent(event)"), "onJSEvent must delegate to the shared helper, not inline its own queueing")
        }

        @Test
        fun `onJSEvent no longer inlines the offer or drop-oldest logic itself`() {
            assertFalse(onJSEvent.contains("jsEventQueue.offer"), "the offer logic now lives only in the shared helper")
            assertFalse(onJSEvent.contains("jsEventQueue.poll"), "the drop-oldest fallback now lives only in the shared helper")
        }
    }

    @Nested
    @DisplayName("getInterruptedQueueState delegates to loadQueueState")
    inner class BridgeDelegation {
        private val getInterruptedQueueState by lazy {
            val start = startModule.indexOf("fun getInterruptedQueueState(promise: Promise) {")
            assertTrue(start >= 0, "getInterruptedQueueState must exist")
            val end = startModule.indexOf("\n    }\n", start)
            assertTrue(end > start, "getInterruptedQueueState body must be closeable")
            startModule.substring(start, end)
        }

        @Test
        fun `it delegates to loadQueueState instead of running its own SQLite query`() {
            assertTrue(getInterruptedQueueState.contains("loadQueueState(context)"), "the bridge must read through loadQueueState")
            assertFalse(getInterruptedQueueState.contains("SQLiteDatabase.openDatabase"), "no second, divergent SQLite read path")
            assertFalse(getInterruptedQueueState.contains("rawQuery"), "no second, divergent SQLite read path")
        }

        @Test
        fun `it returns the phase alongside the existing fields`() {
            assertTrue(getInterruptedQueueState.contains("saved.phase"), "the bridge must surface the phase loadQueueState resolved")
        }
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val f = File(dir, relative)
            if (f.isFile) return f
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }
}
