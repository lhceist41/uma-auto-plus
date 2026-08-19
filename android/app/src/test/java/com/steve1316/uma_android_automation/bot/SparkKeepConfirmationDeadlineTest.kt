package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards the career-end spark-keep stall repair (2026-08-19, Taiki Shuttle GC): a capture or OCR
 * that wedged on the "Keep this set of Sparks?" screen hung the queue silently past both the
 * navigation deadline and the stall watchdog, because a native/monitor wait ignores interruption.
 * The fix wraps the handler and the spark OCR reads in a wall-clock ceiling, and on overrun fails
 * bounded WITHOUT tapping and WITHOUT spending TP.
 */
@DisplayName("Spark keep-confirmation deadline")
class SparkKeepConfirmationDeadlineTest {
    private val nav = sourceFile("CareerLaunchNavigator.kt").readText()

    private fun keepWrapper(): String {
        val start = nav.indexOf("private fun handleSparksKeepConfirmation(): TransitionResult {")
        val end = nav.indexOf("private fun handleSparksKeepConfirmationInner(")
        require(start in 0 until end) { "keep wrapper not found where expected" }
        return nav.substring(start, end)
    }

    private fun keepInner(): String {
        val start = nav.indexOf("private fun handleSparksKeepConfirmationInner(")
        val end = nav.indexOf("private fun clickSparkConfirmationCancel")
        require(start in 0 until end) { "keep inner not found where expected" }
        return nav.substring(start, end)
    }

    @Test
    fun `the handler body runs under a bounded deadline`() {
        val wrapper = keepWrapper()
        assertTrue(wrapper.contains("BoundedExecution.runWithDeadline("), "the handler must delegate to the bounded runner")
        assertTrue(wrapper.contains("SPARK_KEEP_CONFIRM_DEADLINE_MS"), "the handler deadline constant must gate the run")
        assertTrue(wrapper.contains("handleSparksKeepConfirmationInner(abandoned)"), "the real body runs as the inner under the ceiling")
    }

    @Test
    fun `the timeout path fails bounded, taps nothing, and spends no TP`() {
        val wrapper = keepWrapper()
        assertTrue(wrapper.contains("onTimeout"), "there must be a timeout branch")
        assertTrue(wrapper.contains("TransitionResult.Failed"), "an overrun must fail bounded, not hang or continue")
        assertFalse(wrapper.contains("ButtonConfirm.click"), "the timeout branch must never tap Confirm")
        assertFalse(wrapper.contains("CoordinateTap.tap"), "the timeout branch must never tap")
        assertFalse(wrapper.contains("confirmSpend"), "the timeout branch must never spend TP")
    }

    @Test
    fun `an abandoned worker cannot tap Confirm behind the bounded failure`() {
        val inner = keepInner()
        val gate = inner.indexOf("abandoned.get()")
        val click = inner.indexOf("ButtonConfirm.click")
        assertTrue(gate in 0 until click, "the abandoned-gate must precede the Confirm tap")
    }

    @Test
    fun `spark OCR reads are individually bounded`() {
        val start = nav.indexOf("private fun readSparkOcrRegion(")
        val end = nav.indexOf("private fun sparkSelectionBlocked(")
        require(start in 0 until end) { "readSparkOcrRegion not found where expected" }
        val body = nav.substring(start, end)
        assertTrue(body.contains("BoundedExecution.runWithDeadline("), "the spark OCR read must be bounded")
        assertTrue(body.contains("SPARK_OCR_READ_DEADLINE_MS"), "the spark OCR read deadline constant must gate it")
    }

    @Test
    fun `both spark deadlines sit under the stall watchdog timeout`() {
        val keep = constantMs(nav, "SPARK_KEEP_CONFIRM_DEADLINE_MS")
        val ocr = constantMs(nav, "SPARK_OCR_READ_DEADLINE_MS")
        val watchdog = constantMs(sourceFile("bot/Game.kt").readText(), "HEARTBEAT_TIMEOUT_MS")
        assertTrue(keep < watchdog, "keep deadline ($keep) must beat the stall watchdog ($watchdog) so it recovers first")
        assertTrue(ocr < watchdog, "OCR deadline ($ocr) must beat the stall watchdog ($watchdog)")
        assertTrue(ocr <= keep, "a single OCR read cannot outlast the whole handler it runs inside")
    }

    private fun constantMs(source: String, name: String): Long {
        val match = Regex("$name\\s*(?::\\s*\\w+)?\\s*=\\s*([0-9_]+)L").find(source) ?: error("constant $name not found")
        return match.groupValues[1].replace("_", "").toLong()
    }

    private fun sourceFile(relative: String): File = File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        error("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
