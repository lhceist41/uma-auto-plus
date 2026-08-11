package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Mandatory-race navigation robustness (selective upstream v5.8.6 port).
 *
 * Two runtime races were still open in this fork:
 *  - the race-day button tap can be swallowed by the turn's intro animation, so the mandatory flow ran
 *    against the main screen (no prediction OCR, no race list) instead of the race list; and
 *  - the skip lock was judged on a bitmap captured before the strategy dialog, so a mid-transition fade
 *    darkened every button and a genuinely skippable race was run manually (a full live playback).
 *
 * These decisions live inside device-coupled tap/screen code that cannot be unit-exercised without an
 * emulator, so the ported structure is pinned with source guards: a bounded re-tap gated on the factual
 * race-list-open signal, and a fresh-frame re-check of the skip lock. Nothing here alters race selection,
 * strategy, mandatory intent, or the entered-race telemetry boundary.
 */
@DisplayName("Mandatory-race navigation robustness")
class RaceEntryRobustnessTest {
    private val racing by lazy { sourceFile("bot/Racing.kt").readText().replace("\r\n", "\n") }

    /** Body of a method from its signature to the start of the next declaration, so nested blocks are included. */
    private fun methodBody(signature: String, nextSignature: String): String {
        val start = racing.indexOf(signature)
        require(start >= 0) { "missing $signature" }
        val end = racing.indexOf(nextSignature, start + signature.length)
        require(end > start) { "missing $nextSignature after $signature" }
        return racing.substring(start, end)
    }

    @Nested
    @DisplayName("race-day entry retry")
    inner class RaceDayEntryRetry {
        @Test
        fun `the race-day tap is factored into a shared helper that taps below the un-clickable ribbon`() {
            val helper = methodBody("private fun tapRaceDayButton(ribbonLocation: Point? = null) {", "private fun handleMandatoryRace(")
            assertTrue(
                helper.contains("game.tap(loc.x, loc.y + 100, IconRaceDayRibbon.template.path, ignoreWaiting = true)"),
                "the helper taps 100px below the race-day ribbon",
            )
        }

        @Test
        fun `the helper reuses an already-found ribbon location before searching again`() {
            val helper = methodBody("private fun tapRaceDayButton(ribbonLocation: Point? = null) {", "private fun handleMandatoryRace(")
            assertTrue(
                helper.contains("ribbonLocation ?: IconRaceDayRibbon.find(game.imageUtils).first ?: return"),
                "a supplied ribbon location is used as-is; the search (and an early return when absent) is the fallback",
            )
        }

        @Test
        fun `handleRaceEvents enters mandatory racing through the shared helper (no duplicated tap site)`() {
            val handler = methodBody("fun handleRaceEvents(", "fun handleStandaloneRace(")
            assertTrue(handler.contains("tapRaceDayButton(loc)"), "the caller taps via the helper, passing its pre-found ribbon location")
        }

        @Test
        fun `the mandatory handler re-taps only until the race list factually opens`() {
            val handler = mandatoryBody()
            assertTrue(handler.contains("for (attempt in 1..3) {"), "the retry is bounded to three attempts")
            assertTrue(
                handler.contains("if (ButtonRaceListFullStats.check(game.imageUtils, tries = 10)) break"),
                "the loop breaks on the strongest race-list-open signal already used elsewhere in this file",
            )
            assertTrue(handler.contains("tapRaceDayButton()"), "it re-taps through the shared helper, not raw coordinates")
        }

        @Test
        fun `the open check precedes the re-tap so a successful first tap never taps twice`() {
            val handler = mandatoryBody()
            val loopStart = handler.indexOf("for (attempt in 1..3) {")
            val open = handler.indexOf("ButtonRaceListFullStats.check(game.imageUtils, tries = 10)", loopStart)
            val retap = handler.indexOf("tapRaceDayButton()", loopStart)
            assertTrue(open in loopStart until retap, "each iteration verifies the list is open before tapping again (no blind tap spam)")
        }

        @Test
        fun `the entry retry runs before any race-list read (prediction OCR, confirmation)`() {
            val handler = mandatoryBody()
            val loop = handler.indexOf("for (attempt in 1..3) {")
            val ocr = handler.indexOf("findPredictionAnchors")
            val confirm = handler.indexOf("ButtonRace.click(game.imageUtils, tries = 3)")
            assertTrue(loop in 0 until ocr, "the list is confirmed open before the mandatory-race name OCR")
            assertTrue(loop in 0 until confirm, "the list is confirmed open before the Race-button confirmation")
        }

        @Test
        fun `no unbounded entry loop was introduced`() {
            val handler = mandatoryBody()
            assertFalse(handler.contains("while (true)"), "the re-tap is a bounded for-loop, never a spin loop")
        }
    }

    @Nested
    @DisplayName("skip-lock freshness")
    inner class SkipLockFreshness {
        @Test
        fun `isSkipLocked returns other reads immediately and only re-checks a locked read on a fresh frame`() {
            val helper = methodBody("private fun isSkipLocked(sourceBitmap: Bitmap): Boolean? {", "Executes the race with retry logic")
            assertTrue(helper.contains("ButtonViewResults.checkDisabled(game.imageUtils, sourceBitmap)"), "the first read is made against the supplied frame")
            assertTrue(helper.contains("if (bIsLocked != true) {"), "only a locked (true) read is re-checked; false and null return straight through")
            assertTrue(helper.contains("return bIsLocked"), "a non-locked read is returned as-is")
            val settle = helper.indexOf("game.wait(game.dialogWaitDelay, skipWaitingForLoading = true)")
            val fresh = helper.indexOf("return ButtonViewResults.checkDisabled(game.imageUtils)")
            assertTrue(settle in 0 until fresh, "the re-check settles, then reads a fresh capture (no sourceBitmap)")
        }

        @Test
        fun `the race-prep bitmap is refreshed after the strategy dialog, gated on the dialog actually opening`() {
            val loop = methodBody("fun runRaceWithRetries(", "private fun tapRaceDayButton(")
            assertTrue(loop.contains("var bitmap: Bitmap = game.imageUtils.getSourceBitmap()"), "the frame is recapturable")
            val strategy = loop.indexOf("bDidSelectRaceStrategy = selectRaceStrategy()")
            val gate = loop.indexOf("if (bHasSetTemporaryRunningStyle) {", strategy)
            val recapture = loop.indexOf("bitmap = game.imageUtils.getSourceBitmap()", strategy)
            assertTrue(strategy >= 0)
            assertTrue(gate in strategy until recapture, "the recapture is guarded by the flag that is set only when the dialog changed a running style")
            assertTrue(recapture in gate until (gate + 200), "the guarded branch settles and re-captures the frame")
        }

        @Test
        fun `the skip decision consults isSkipLocked instead of the stale one-shot check`() {
            val loop = methodBody("fun runRaceWithRetries(", "private fun tapRaceDayButton(")
            assertTrue(loop.contains("when (isSkipLocked(bitmap)) {"), "the lock is judged through the fresh-frame helper")
            assertFalse(
                loop.contains("when (ButtonViewResults.checkDisabled(game.imageUtils, bitmap)) {"),
                "the pre-fix stale one-shot check is gone",
            )
        }

        @Test
        fun `the locked branch runs the race manually on a fresh frame`() {
            val loop = methodBody("fun runRaceWithRetries(", "private fun tapRaceDayButton(")
            val locked = loop.indexOf("when (isSkipLocked(bitmap)) {")
            val trueBranch = loop.indexOf("true -> {", locked)
            val manual = loop.indexOf("if (ButtonRaceManual.click(game.imageUtils)) {", trueBranch)
            assertTrue(manual in trueBranch until (trueBranch + 120), "the manual-race click no longer reuses the pre-check bitmap")
        }
    }

    @Nested
    @DisplayName("intent + telemetry preservation")
    inner class Preservation {
        @Test
        fun `the mandatory confirmation still uses the Race button (already-present upstream behavior, unchanged)`() {
            val handler = mandatoryBody()
            assertEquals(
                2,
                Regex("ButtonRace\\.click\\(game\\.imageUtils, tries = 3\\)").findAll(handler).count(),
                "the two-tap Race-button confirmation on the race-list path is preserved",
            )
        }

        @Test
        fun `entered-race telemetry still records only at the successful-completion boundary`() {
            val handler = mandatoryBody()
            val succeeded = handler.indexOf("val succeeded = raceCompleted && resultsFinalized")
            val record = handler.indexOf("campaign.recordEnteredRace(enteredRaceFact")
            assertTrue(succeeded >= 0)
            assertTrue(record > succeeded, "the completed-race record still fires after the success determination")
            val loop = handler.indexOf("for (attempt in 1..3) {")
            val recordFromLoop = handler.indexOf("recordEnteredRace", loop)
            assertTrue(recordFromLoop >= record, "the entry retry records no telemetry of its own")
        }

        @Test
        fun `no scenario-aware raceDayButton dependency was pulled in from upstream`() {
            assertFalse(
                racing.contains("campaign.raceDayButton"),
                "the scenario-aware race-day component (Campaign accessor) is deliberately not ported; this fork taps the ribbon offset",
            )
        }

        @Test
        fun `the skip policy semantics are unchanged (manual when locked, skip when unlocked)`() {
            val loop = methodBody("fun runRaceWithRetries(", "private fun tapRaceDayButton(")
            assertTrue(loop.contains("[RACE] Skip is locked. Running race manually."), "locked still runs manually")
            assertTrue(loop.contains("[RACE] Clicked ViewResults button to skip race."), "unlocked still skips")
        }
    }

    private fun mandatoryBody(): String = methodBody("private fun handleMandatoryRace(): Boolean {", "private fun selectMaidenRace(")

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
