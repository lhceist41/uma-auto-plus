package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Structural guards on the passive lineage capture wired into the launch navigator. The capture
 * itself can only be exercised live, so these pin the properties that keep it safe by construction:
 * it is off by default, it runs at most once, a failure never blocks the launch, its return path is
 * bounded, and it never touches the selected parents. If a later edit weakens any of these, a unit
 * test breaks rather than a live career.
 */
@DisplayName("Passive lineage capture navigator safety guards")
class LineageCaptureNavigatorGuardTest {
    private val nav by lazy { sourceFile("CareerLaunchNavigator.kt").readText().replace("\r\n", "\n") }

    /** The region of the post-Auto-Select branch that decides whether to capture. */
    private fun captureCallSite(): String {
        val start = nav.indexOf("if (legacyAutoSelectAlreadyDone) {")
        assertTrue(start >= 0, "the post-Auto-Select branch must exist")
        val next = nav.indexOf("MessageLog.i(TAG, \"[NAV] Legacy Auto-Select already done", start)
        assertTrue(next in (start + 1)..nav.length, "the Next-click log must follow the capture hook")
        return nav.substring(start, next)
    }

    /** The full body of the capture and its helpers, from the method to the next unrelated one. */
    private fun captureBody(): String {
        val start = nav.indexOf("private fun captureLineageTelemetry()")
        val end = nav.indexOf("private fun handlePreRunConfirmation()")
        assertTrue(start in 0 until end, "the capture methods must sit before handlePreRunConfirmation")
        return nav.substring(start, end)
    }

    @Test
    fun `capture is off by default - gated on the enableLineageCapture setting`() {
        val site = captureCallSite()
        assertTrue(
            site.contains("getBooleanSetting(\"runQueue\", \"enableLineageCapture\", false)"),
            "the capture must be gated on the default-off setting",
        )
    }

    @Test
    fun `capture runs at most once per launch`() {
        val site = captureCallSite()
        assertTrue(site.contains("!lineageCaptureAttempted"), "the once-per-launch latch gates the call")
        assertTrue(site.contains("lineageCaptureAttempted = true"), "the latch is set before the attempt")
        assertTrue(nav.contains("lineageCaptureAttempted = false"), "the latch resets each navigate() pass")
    }

    @Test
    fun `a capture failure never blocks the launch`() {
        val site = captureCallSite()
        val tryIdx = site.indexOf("try {")
        val callIdx = site.indexOf("captureLineageTelemetry()")
        val catchIdx = site.indexOf("} catch (e: Exception) {")
        assertTrue(tryIdx in 0 until callIdx, "the capture call is inside a try")
        assertTrue(catchIdx > callIdx, "a catch follows the capture call")
        // The branch's Next click is emitted regardless of the capture outcome: it lives after the
        // guarded hook, not inside the try.
        assertTrue(nav.indexOf("ButtonNext.click(iu)", nav.indexOf(site)) > 0, "Next is clicked after the hook")
    }

    @Test
    fun `the Sparks-view return path is bounded`() {
        assertTrue(captureBody().contains("for (attempt in 0 until 3)"), "the return-to-Legacy-Select retry is bounded")
    }

    @Test
    fun `capture never mutates the parent selection`() {
        val body = captureBody()
        for (forbidden in listOf(
            "ButtonChange",
            "ButtonReset",
            "ButtonAutoSelect.click",
            "ButtonStartCareer",
            "ButtonNext.click",
            "ButtonOk.click",
        )) {
            assertFalse(forbidden in body, "the observational capture must not invoke $forbidden")
        }
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
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
