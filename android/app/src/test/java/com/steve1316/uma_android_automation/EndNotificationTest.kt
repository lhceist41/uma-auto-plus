package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The end notifier after the library's thread has finished. The library's cleanup posts "Completed
 * successfully with no errors." last, after the capture service's cancel-all on the app's Stop, so the
 * notifier must remove it then; it keeps showing how the session ended while the service is up, and
 * never touches a new session's notification.
 */
@DisplayName("The notification after a session ends")
class EndNotificationTest {
    /** Plays [StartModule.finishEndNotification] against scripted service states, one per read. */
    private fun finish(capture: List<Boolean>, session: Boolean = false): List<String> {
        val reads = capture.iterator()
        val done = mutableListOf<String>()
        StartModule.finishEndNotification(
            captureRunning = { reads.next() },
            sessionRunning = { session },
            update = { done += "update" },
            remove = { done += "remove" },
        )
        return done
    }

    @Test
    fun `the app's Stop removes the library's late success line`() {
        // The capture service is gone when the notifier looks: nothing true to show, so nothing may stay.
        assertEquals(listOf("remove"), finish(capture = listOf(false)))
    }

    @Test
    fun `the overlay's dismiss removes it the same way`() {
        assertEquals(listOf("remove"), finish(capture = listOf(false)))
    }

    @Test
    fun `a tap on the overlay's Stop keeps the service up and shows how the session ended`() {
        assertEquals(listOf("update"), finish(capture = listOf(true, true)))
    }

    @Test
    fun `a service that goes down right after the update has the update removed too`() {
        assertEquals(listOf("update", "remove"), finish(capture = listOf(true, false)))
    }

    @Test
    fun `a new session's notification is never touched`() {
        for (capture in listOf(listOf(true, true), listOf(false))) {
            assertEquals(emptyList<String>(), finish(capture = capture, session = true), "$capture")
        }
    }

    private val startModule by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val relative = "android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt"
        repeat(8) {
            if (File(dir, relative).isFile) return@lazy File(dir, relative).readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found")
    }

    @Test
    fun `the library still posts its notification under id 1, the id the notifier removes`() {
        // NotificationUtils.NOTIFICATION_ID is private in the library; a version that moved it would leave the stale post again.
        val owner = com.steve1316.automation_library.utils.NotificationUtils::class.java
        val field = (listOf(owner) + owner.declaredClasses).firstNotNullOf { c -> c.declaredFields.firstOrNull { it.name == "NOTIFICATION_ID" } }
        field.isAccessible = true
        assertEquals(1, field.getInt(null))
        assertTrue(startModule.contains("private const val LIBRARY_NOTIFICATION_ID = 1\n"))
    }

    @Test
    fun `the notifier removes only the library's notification, after the join`() {
        val start = startModule.indexOf("private fun notifySessionEnd(libraryThread: Thread, report: QueueReport?) {")
        val notifier = startModule.substring(start, startModule.indexOf("\n    }\n", start))
        assertTrue(notifier.indexOf("libraryThread.join()") in 0 until notifier.indexOf("finishEndNotification("))
        assertTrue(notifier.contains("remove = { NotificationManagerCompat.from(context).cancel(LIBRARY_NOTIFICATION_ID) },"))
        assertTrue(startModule.contains("private const val LIBRARY_NOTIFICATION_ID = 1\n"))
        assertFalse(startModule.contains("cancelAll"), "never the app's other notifications")
        assertFalse(notifier.contains("MessageLog"))
    }
}
