package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.RaceGrade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Retries on a lost goal race, and the "Goal races" Alarm Clock policy.
 *
 * Fixtures: a Mihono Bourbon URA career lost Tenno Sho (Spring) twice and ended with one retry of its career budget of 3 still
 * unused (it had used one on Kikuka Sho); a Biwa Hayahide Grand Concert career lost its Kikuka Sho goal twice with two retries
 * unused. In both, the per-race cap of 1 closed the second Try Again dialog.
 */
@DisplayName("Goal race retries")
class GoalRaceRetryTest {
    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun body(
        text: String,
        start: String,
        end: String,
    ): String {
        val from = text.indexOf(start)
        assertTrue(from >= 0, "missing: $start")
        val to = text.indexOf(end, from + start.length)
        assertTrue(to > from, "missing: $end")
        return text.substring(from, to)
    }

    private val botDir = "android/app/src/main/java/com/steve1316/uma_android_automation/bot"

    /** Retries taken on a race the bot keeps losing: the Try Again dialog is accepted while [Racing.retryAllowed] says so. */
    private fun retriesTaken(
        budgetAtRace: Int,
        lostGoalRace: Boolean,
        maxRetriesPerRace: Int = 1,
    ): Pair<Int, Int> {
        var retries = 0
        var budget = budgetAtRace
        while (Racing.retryAllowed(lostGoalRace, retries, budget, maxRetriesPerRace)) {
            retries++
            budget--
        }
        return retries to budget
    }

    @Nested
    @DisplayName("a lost goal race uses the career's retry budget")
    inner class Budget {
        @Test
        fun `Bourbon's Tenno Sho retries twice, using the budget Kikuka Sho left, then force-ends`() {
            val (retries, left) = retriesTaken(budgetAtRace = 2, lostGoalRace = true)
            assertEquals(2, retries, "the per-race cap of 1 used to stop at 1 with a retry still left")
            assertEquals(0, left)
            val limit = Racing.raceRetryLimit(lostGoalRace = true, retriesThisRace = retries, budgetLeft = left, maxRetriesPerRace = 1)
            assertEquals(
                "[RACE] Not retrying the failed race: this career's retry budget is used up. Closing the Try Again dialog.",
                Racing.raceRetryDeclinedText(alarmClockDeclined = false, retriesThisRace = retries, raceLimit = limit, budgetLeft = left, policy = "Never"),
            )
            assertTrue(Racing.declinedRetryFailsGoal(runningGoalRace = true, finaleSeason = false))
        }

        @Test
        fun `Biwa's Kikuka Sho may use all three retries`() {
            assertEquals(3 to 0, retriesTaken(budgetAtRace = 3, lostGoalRace = true))
        }

        @Test
        fun `any other race keeps the per-race cap`() {
            assertEquals(1 to 2, retriesTaken(budgetAtRace = 3, lostGoalRace = false))
            assertEquals(2 to 1, retriesTaken(budgetAtRace = 3, lostGoalRace = false, maxRetriesPerRace = 2))
            assertEquals(0 to 0, retriesTaken(budgetAtRace = 0, lostGoalRace = true))
        }

        @Test
        fun `the retry line says it is a lost goal race and what is left`() {
            assertEquals(
                "[RACE] Retrying the lost goal race with an Alarm Clock (if none is held, alarmClockPolicy=GoalRaces buys one): retry 1 of 2 for this race, 1 left in this career's retry budget.",
                Racing.raceRetryText(freeRetryShown = false, retryNumber = 1, raceLimit = 2, budgetLeft = 1, policy = "GoalRaces", grade = RaceGrade.G1, lostGoalRace = true),
            )
        }

        @Test
        fun `the dialog path passes the lost goal race and stops after a declined purchase`() {
            val campaign = source("$botDir/Campaign.kt")
            val tryAgain = body(campaign, "private fun handleTryAgainDialog(", "open fun onAfterTurnStartUpdates()")
            assertTrue(tryAgain.contains("val lostGoalRace = racing.bRunningGoalRace\n        if (shouldRetryRace(dialog, args, lostGoalRace)) {"))
            val retry = body(campaign, "open fun shouldRetryRace(dialog: DialogInterface, args: Map<String, Any>, lostGoalRace: Boolean): Boolean {", "private fun handleConsecutiveRaceWarning(")
            val skipped = retry.indexOf("if (racing.bAlarmClockPolicySkippedThisRace) return false")
            val allowed = retry.indexOf("if (Racing.retryAllowed(lostGoalRace, racing.retriesThisRace, racing.raceRetries, racing.maxRetriesPerRace)) {")
            assertTrue(skipped in 0 until allowed, "a declined purchase ends the retries before the budget is consulted")
            assertTrue(retry.contains("if (lostGoalRace) racing.bRetryingLostGoalRace = true"))
            // Trackblazer takes the same path: its own override, which retried only G1-G3 races, is gone.
            assertFalse(source("$botDir/campaigns/Trackblazer.kt").contains("override fun shouldRetryRace("))
        }
    }

    @Nested
    @DisplayName("optional races and passed goals are never retried")
    inner class NoOtherRetries {
        @Test
        fun `the results-screen retry taps are gone, so nothing retries a won race, a passed goal or an optional race`() {
            // A Trackblazer career at the default cap of 1 tapped retry after G1 wins and then declined its own
            // confirmation, five times: the tap had already counted the retry, so these taps never retried anything.
            val racing = source("$botDir/Racing.kt")
            val loop = body(racing, "fun runRaceWithRetries(): Boolean {", "private fun tapRaceDayButton(")
            assertFalse(loop.contains("TryAgain"), "no retry tap in the race loop")
            assertFalse(racing.contains("Retrying for the win"), "no retry of a goal met below 1st")
            assertTrue(racing.contains("    internal val maxRetriesPerRace = 1\n"), "every race other than a lost goal keeps one retry")
        }

        @Test
        fun `the Trackblazer race-retry settings are removed everywhere`() {
            val keys = listOf("trackblazerMaxRetriesPerRace", "trackblazerRetryRacesBeforeFinalGrades")
            val files =
                listOf(
                    "$botDir/Racing.kt",
                    "$botDir/campaigns/Trackblazer.kt",
                    "src/context/BotStateContext.tsx",
                    "src/data/characterPresets.ts",
                    "src/pages/ScenarioOverridesSettings/index.tsx",
                    "src/components/MessageLog/index.tsx",
                )
            for (file in files) for (key in keys) assertFalse(source(file).contains(key), "$key in $file")
            val search = source("src/data/searchConfig.ts")
            assertFalse(search.contains("trackblazer-max-retries-per-race"))
            assertFalse(search.contains("trackblazer-retry-races-before-final-grades"))
            val migrations = source("src/lib/settingsUtils.ts")
            for (key in keys) assertTrue(migrations.contains("delete overrides.$key"), "a stored $key is dropped on load")
        }
    }

    @Nested
    @DisplayName("the Goal races Alarm Clock policy")
    inner class GoalRacesPolicy {
        @Test
        fun `it buys only to retry a lost goal race, whatever the grade`() {
            assertTrue(Racing.alarmClockPurchaseAllowed("GoalRaces", RaceGrade.G2, lostGoalRace = true))
            assertTrue(Racing.alarmClockPurchaseAllowed("GoalRaces", null, lostGoalRace = true))
            assertFalse(Racing.alarmClockPurchaseAllowed("GoalRaces", RaceGrade.G1, lostGoalRace = false))
            assertFalse(Racing.alarmClockPurchaseAllowed("Never", RaceGrade.G1, lostGoalRace = true))
            assertFalse(Racing.alarmClockPurchaseAllowed("G1Only", RaceGrade.G2, lostGoalRace = true))
        }

        @Test
        fun `the purchase dialog asks whether this retry is for a lost goal race, and resets per race`() {
            assertTrue(source("$botDir/DialogHandler.kt").contains("val lostGoalRace = (game.task as? Campaign)?.isRetryingLostGoalRace() ?: false"))
            val racing = source("$botDir/Racing.kt")
            assertTrue(racing.contains("retriesThisRace = 0\n        bRetryingLostGoalRace = false"), "reset when the race loop starts")
            assertTrue(racing.contains("retriesThisRace = 0\n            bRetryingLostGoalRace = false"), "reset with the other per-race flags")
        }

        @Test
        fun `the app default stays Never and the option is offered with true copy`() {
            val context = source("src/context/BotStateContext.tsx")
            assertTrue(context.contains("alarmClockPolicy: \"Never\" | \"GoalRaces\" | \"G1Only\" | \"G1AndFinale\" | \"Always\""))
            assertTrue(context.contains("        alarmClockPolicy: \"Never\",\n"))
            val page = source("src/pages/RacingSettings/index.tsx")
            assertTrue(page.contains("{ value: \"GoalRaces\", label: \"Goal races only\" }"))
            assertFalse(page.contains("5 per career"))
            assertFalse(source("src/data/searchConfig.ts").contains("5 per career"))
        }
    }
}
