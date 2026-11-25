package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Three events were emitted on the caller's thread while the forwarder thread emitted the rest, so `messageId++` and the lazy emitter
 * ran on two threads unsynchronized. Every send now goes through [StartModule]'s forwarder queue. Source guards: the module needs a
 * live React context.
 */
@DisplayName("StartModule sends every frontend event from the forwarder thread")
class StartModuleEventSendTest {
    private val src by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var text: String? = null
        repeat(8) {
            val f = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt")
            if (text == null && f.isFile) text = f.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        text ?: throw AssertionError("StartModule.kt not found from the test working directory")
    }

    @Test
    fun `only the forwarder thread calls sendEvent, and nothing outside the module can`() {
        val calls = Regex("""(?<![\w.])sendEvent\(""").findAll(src).map { it.range.first }.toList()
        val definition = src.indexOf("private fun sendEvent(eventName: String, message: String) {")
        assertTrue(definition >= 0, "sendEvent must stay private")
        val worker = src.substring(src.indexOf("private fun ensureJsEventWorker() {"), src.indexOf("worker.start()"))
        assertEquals(2, calls.size, "the definition and the forwarder's call, nothing else")
        assertTrue(calls.contains(definition + "private fun ".length))
        assertTrue(worker.contains("sendEvent(event.eventName, event.message)"), worker)
    }

    @Test
    fun `the projection and queue progress events go through the forwarder queue`() {
        for (event in listOf(
            "enqueueJsEvent(JSEvent(\"MediaProjectionService\", \"Running\", false))",
            "enqueueJsEvent(JSEvent(\"MediaProjectionService\", \"Not Running\", false))",
            "enqueueJsEvent(JSEvent(\"RunQueueProgress\", payload.toString(), false))",
        )) {
            assertEquals(1, src.split(event).size - 1, event)
        }
    }

    @Test
    fun `messageId is only touched inside sendEvent`() {
        val body = src.substring(src.indexOf("private fun sendEvent("), src.indexOf("private val jsEventWorkerStarted"))
        val uses = Regex("""\bmessageId\b""").findAll(src).count()
        assertEquals(2, uses, "the declaration and the one increment")
        assertTrue(body.contains("params.putInt(\"id\", messageId++)"), body)
    }
}
