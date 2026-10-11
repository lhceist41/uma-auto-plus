package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.components.ButtonResume
import com.steve1316.uma_android_automation.components.DialogContinueCareer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The game's daily reset can drop a race to the Home screen. The race loop must hand back instead of tapping Home until its budget
 * runs out, the career loop must resume the same career through Continue Career, and the interrupted race must not be recorded twice.
 */
@DisplayName("A race interrupted by the daily reset")
class RaceAfterDailyResetTest {
    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        throw AssertionError("Kotlin source root not found from the test working directory")
    }

    private fun source(relative: String) = File(kotlinRoot(), relative).readText().replace("\r\n", "\n")

    private val racing by lazy { source("bot/Racing.kt") }
    private val campaign by lazy { source("bot/Campaign.kt") }

    private fun block(text: String, start: String, end: String): String {
        val from = text.indexOf(start)
        assertTrue(from >= 0, "missing: $start")
        val to = text.indexOf(end, from + start.length)
        assertTrue(to > from, "missing end: $end")
        return text.substring(from, to)
    }

    @Nested
    @DisplayName("the race loop")
    inner class RaceLoop {
        private val loop by lazy { block(racing, "    fun runRaceWithRetries(): Boolean {", "    fun finalizeRaceResults(") }
        private val exit by lazy { block(racing, "    private fun raceLeftForCareerScreen(bitmap: Bitmap): Boolean {", "    fun runRaceWithRetries(): Boolean {") }

        @Test
        fun `a frame with no race control is checked for Home or the career screen before any blind tap, and hands back`() {
            val check = loop.indexOf("if (raceLeftForCareerScreen(bitmap)) {")
            assertTrue(check >= 0)
            val tap = loop.indexOf("game.tap(350.0, 450.0, taps = 1)")
            assertTrue(check < tap, "no blind tap on Home before the exit check")
            val branch = loop.substring(check, loop.indexOf("}", check))
            assertTrue(branch.contains("return false"), "the race is reported as not completed, so its caller records nothing")
        }

        @Test
        fun `the exit is checked on every blind-tap iteration, so Home ends the loop on its first frame`() {
            val elseBranch = loop.substring(loop.lastIndexOf("else -> {"))
            assertTrue(elseBranch.trimStart().removePrefix("else -> {").trimStart().startsWith("if (raceLeftForCareerScreen(bitmap)) {"))
        }

        @Test
        fun `Home counts only with the career-in-progress wordmark on two captures, and the check never taps`() {
            assertTrue(exit.contains("ButtonCareerHomeTextActive.check(game.imageUtils, sourceBitmap = frame) || ButtonCareerHomeTextEvent.check(game.imageUtils, sourceBitmap = frame)"))
            val first = exit.indexOf("if (homeShowsCareerInProgress(bitmap)) {")
            val settle = exit.indexOf("game.wait(1.0, skipWaitingForLoading = true)", first)
            val second = exit.indexOf("if (homeShowsCareerInProgress(game.imageUtils.getSourceBitmap())) return true", settle)
            assertTrue(first in 0 until settle && settle < second)
            assertTrue(exit.contains("return campaign.checkMainScreen()"))
            assertFalse(Regex("""\.tap\(|\.click\(""").containsMatchIn(exit))
        }
    }

    @Nested
    @DisplayName("the resume")
    inner class Resume {
        private val branch by lazy { block(campaign, "            \"continue_career\" -> {", "            else -> {") }

        @Test
        fun `the campaign answers Continue Career with Resume instead of failing the run`() {
            assertEquals(ButtonResume, DialogContinueCareer.okButton, "ok() taps Resume, never Cancel")
            assertTrue(branch.contains("result.dialog.ok(game.imageUtils)"))
            assertFalse(branch.contains("close("))
        }

        @Test
        fun `Resume is gated like the lobby re-entry and spends its budget before the tap`() {
            val gate = branch.indexOf("if (!careerScreenObservedThisTask || lobbyReentryAttempts >= maxLobbyReentryAttempts) {")
            val unhandled = branch.indexOf("return DialogHandlerResult.Unhandled(result.dialog)", gate)
            val spend = branch.indexOf("lobbyReentryAttempts++")
            val tap = branch.indexOf("result.dialog.ok(game.imageUtils)")
            assertTrue(gate in 0 until unhandled && unhandled < spend && spend < tap)
        }

        @Test
        fun `the Resume tap comes before its log line`() {
            assertTrue(branch.indexOf("result.dialog.ok(game.imageUtils)") < branch.indexOf("MessageLog.w(TAG, \"[RECOVERY] Continue Career"))
        }

        @Test
        fun `a Resume that was not found is not logged or counted as pressed`() {
            val pressed = branch.indexOf("if (result.dialog.ok(game.imageUtils)) {")
            assertTrue(pressed >= 0, "the click result decides the line")
            val notFound = branch.indexOf("} else {", pressed)
            assertTrue(notFound > pressed)
            val onPress = branch.substring(pressed, notFound)
            assertTrue(onPress.contains("pressed Resume") && onPress.contains("SessionTally.lobbyReentries.incrementAndGet()"))
            val onMiss = branch.substring(notFound)
            assertTrue(onMiss.contains("Resume was not found"))
            assertFalse(onMiss.contains("pressed Resume") || onMiss.contains("lobbyReentries"))
            assertTrue(branch.indexOf("lobbyReentryAttempts++") < pressed, "a missed click still spends the budget")
        }

        @Test
        fun `a Home bounce outside a dialog still re-enters through the navigator's Continue Career`() {
            assertTrue(campaign.contains("val result = navigator.navigate(reuseLastLaunchSetup = true, resumeInProgressCareer = true)"))
        }
    }

    @Nested
    @DisplayName("the interrupted race")
    inner class NoDoubleCount {
        @Test
        fun `every race handler records the race only after the race and its results both completed`() {
            val records = Regex("""campaign\.recordEnteredRace\(""").findAll(racing).map { it.range.first }.toList()
            assertTrue(records.size >= 5)
            for (at in records) {
                val before = racing.lastIndexOf("val succeeded = raceCompleted && resultsFinalized", at)
                assertTrue(before >= 0, "a record at $at without the completion check")
                val guard = racing.indexOf("if (!succeeded) {", before)
                assertTrue(guard in before until at)
                assertTrue(racing.substring(guard, at).contains("return false"))
            }
        }
    }
}
