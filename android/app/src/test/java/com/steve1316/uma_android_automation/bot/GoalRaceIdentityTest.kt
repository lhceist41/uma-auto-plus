package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.RunRecord
import com.steve1316.uma_android_automation.types.GameDate
import com.steve1316.uma_android_automation.types.RaceGrade
import com.steve1316.uma_android_automation.utils.StatusBoard
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Goal race identity, retry text and the failed-goal outcome.
 *
 * Fixture: a Mihono Bourbon URA career trained on turn 55 (Senior April, first half) and the game then opened
 * turn 56's goal race. The race list OCR read "Kyoto Turf 3200m (Long) Right / Outer", but the bot looked it up
 * with the stored turn 55 and logged "Lord Derby Challenge Trophy" (G3). It lost twice: the log read
 * "Retrying the race. Retries remaining: 2", then "No retries remaining but Try Again dialog detected" with
 * one retry still in the budget, and the career was ledgered outcome=COMPLETED.
 */
@DisplayName("Goal race identity and outcome")
class GoalRaceIdentityTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun source(relative: String): String = repoFile(relative).readText().replace("\r\n", "\n")

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

    @Nested
    @DisplayName("the goal race is looked up on its own turn")
    inner class Identity {
        private fun racesOnTurn(turn: Int): List<JSONObject> {
            val races = JSONObject(repoFile("src/data/compiled/races.json").readText()).getJSONArray("races")
            return (0 until races.length()).map { races.getJSONObject(it) }.filter { it.getInt("turnNumber") == turn }
        }

        private fun isReadCourse(race: JSONObject): Boolean =
            race.getString("raceTrack") == "Kyoto" &&
                race.getString("terrain") == "Turf" &&
                race.getInt("distanceMeters") == 3200 &&
                race.getString("direction") == "Right" &&
                race.getString("course") == "Outer"

        @Test
        fun `the course the race list showed is turn 56's Tenno Sho (Spring), a G1, and no turn 55 race`() {
            val turn56 = racesOnTurn(56).filter(::isReadCourse)
            assertEquals(listOf("Tenno Sho (Spring)"), turn56.map { it.getString("name") })
            assertEquals("G1", turn56.single().getString("grade"))
            assertTrue(racesOnTurn(55).none(::isReadCourse))
            assertTrue(racesOnTurn(55).any { it.getString("name") == "Lord Derby Challenge Trophy" && it.getString("grade") == "G3" })
        }

        @Test
        fun `Biwa's turn 44 course is Kikuka Sho, a G1, not turn 43's Kyoto Daishoten G2`() {
            // A Biwa Hayahide Grand Concert career read "Kyoto Turf 3000m (Long) Right / Outer" at the stale turn 43 and
            // logged "Kyoto Daishoten" (G2); her goal on turn 44 is Kikuka Sho (G1).
            val course = { race: JSONObject ->
                race.getString("raceTrack") == "Kyoto" && race.getString("terrain") == "Turf" && race.getInt("distanceMeters") == 3000 &&
                    race.getString("direction") == "Right" && race.getString("course") == "Outer"
            }
            val turn44 = racesOnTurn(44).filter(course)
            assertEquals(listOf("Kikuka Sho"), turn44.map { it.getString("name") })
            assertEquals("G1", turn44.single().getString("grade"))
            assertTrue(racesOnTurn(43).none(course))
            assertTrue(racesOnTurn(43).any { it.getString("name") == "Kyoto Daishoten" && it.getString("grade") == "G2" })
            // With G1Only, the goal read on its own turn buys the retry; the stale G2 read did not.
            assertTrue(Racing.alarmClockPurchaseAllowed("G1Only", RaceGrade.G1, lostGoalRace = false))
            assertFalse(Racing.alarmClockPurchaseAllowed("G1Only", RaceGrade.G2, lostGoalRace = false))
        }

        @Test
        fun `the mandatory race reads the date from the race list before the lookup and the finale check`() {
            val mandatory = body(source("$botDir/Racing.kt"), "private fun handleMandatoryRace(): Boolean {", "private fun selectMaidenRace(")
            val listOpened = mandatory.indexOf("tapRaceDayButton()")
            val dateRead = mandatory.indexOf("campaign.date.update(game.imageUtils, scenario = game.scenario, isOnMainScreen = false)")
            val finaleCheck = mandatory.indexOf("if (campaign.date.bIsFinaleSeason")
            val lookup = mandatory.indexOf("lookupRaceInDatabase(campaign.date.day, raceName)")
            assertTrue(listOpened in 0 until dateRead, "the date is read after the race list opens")
            assertTrue(dateRead < finaleCheck && dateRead < lookup, "the date is read before the finale check and the lookup")
        }

        @Test
        fun `finale days keep the Main screen date, and a failed race list read says which turn is used`() {
            // A finale day reaches the Main screen first (it logs "Finale Semi-Final (Turn 74)"); the race list does not
            // name the round, and a read there defaults to turn 73.
            assertTrue(GameDate(74).apply { updateDay(74) }.bIsFinaleSeason)
            assertFalse(GameDate(56).apply { updateDay(56) }.bIsFinaleSeason)
            val mandatory = body(source("$botDir/Racing.kt"), "private fun handleMandatoryRace(): Boolean {", "private fun selectMaidenRace(")
            val skip = mandatory.indexOf("if (!campaign.date.bIsFinaleSeason) {\n            val lastMainScreenTurn = campaign.date.day\n            if (campaign.date.update(")
            assertTrue(skip >= 0, "the race list read runs only outside the finale season")
            assertEquals(1, Regex("campaign\\.date\\.update\\(|campaign\\.updateDate\\(").findAll(mandatory).count(), "no other date read in the mandatory race")
            assertTrue(mandatory.contains("MessageLog.w(TAG, \"[RACE] The race list date was not read. Using turn \$lastMainScreenTurn from the last Main screen.\")"))
            assertTrue(mandatory.contains("MessageLog.v(TAG, \"[DATE] New date: \${campaign.date}\")"), "a changed date logs the line the dashboard reads")
        }

        @Test
        fun `the Alarm Clock rule decides on the goal race's grade`() {
            // The G1 goal read on its own turn lets G1Only buy; the stale turn's G3 did not.
            assertTrue(Racing.alarmClockPurchaseAllowed("G1Only", RaceGrade.G1, lostGoalRace = false))
            assertFalse(Racing.alarmClockPurchaseAllowed("G1Only", RaceGrade.G3, lostGoalRace = false))
            assertFalse(Racing.alarmClockPurchaseAllowed("Never", RaceGrade.G1, lostGoalRace = false))
            assertTrue(Racing.alarmClockPurchaseAllowed("G1AndFinale", RaceGrade.FINALE, lostGoalRace = false))
            assertFalse(Racing.alarmClockPurchaseAllowed("G1AndFinale", RaceGrade.G2, lostGoalRace = false))
            assertTrue(Racing.alarmClockPurchaseAllowed("Always", null, lostGoalRace = false))
            assertFalse(Racing.alarmClockPurchaseAllowed("G1Only", null, lostGoalRace = false))
            assertFalse(Racing.alarmClockPurchaseAllowed("unknown", RaceGrade.G1, lostGoalRace = false))
            assertTrue(source("$botDir/DialogHandler.kt").contains("val shouldSpend = Racing.alarmClockPurchaseAllowed(policy, grade, lostGoalRace)"))
        }
    }

    @Nested
    @DisplayName("retry text")
    inner class RetryText {
        @Test
        fun `a retry names what it spends and what is left`() {
            // A retry of a race held to the per-race cap of 1, with the career budget of 3 partly used.
            assertEquals(
                "[RACE] Retrying the failed race with an Alarm Clock (if none is held, alarmClockPolicy=Never buys none): retry 1 of 1 for this race, 1 left in this career's retry budget.",
                Racing.raceRetryText(freeRetryShown = false, retryNumber = 1, raceLimit = 1, budgetLeft = 1, policy = "Never", grade = RaceGrade.G1, lostGoalRace = false),
            )
            assertEquals(
                "[RACE] Retrying the failed race with an Alarm Clock (if none is held, alarmClockPolicy=G1Only buys one): retry 1 of 1 for this race, 1 left in this career's retry budget.",
                Racing.raceRetryText(freeRetryShown = false, retryNumber = 1, raceLimit = 1, budgetLeft = 1, policy = "G1Only", grade = RaceGrade.G1, lostGoalRace = false),
            )
            assertEquals(
                "[RACE] Retrying the failed race with the daily free retry: retry 1 of 1 for this race, 2 left in this career's retry budget.",
                Racing.raceRetryText(freeRetryShown = true, retryNumber = 1, raceLimit = 1, budgetLeft = 2, policy = "Never", grade = RaceGrade.G1, lostGoalRace = false),
            )
        }

        @Test
        fun `a closed Try Again dialog names the rule that stopped the retry`() {
            // A race at its per-race limit with budget still left names the limit, not an empty budget.
            val perRace = Racing.raceRetryDeclinedText(alarmClockDeclined = false, retriesThisRace = 1, raceLimit = 1, budgetLeft = 1, policy = "Never")
            assertEquals("[RACE] Not retrying the failed race: this race already used its 1 retry. Closing the Try Again dialog.", perRace)
            assertEquals(
                "[RACE] Not retrying the failed race: no Alarm Clock is held and alarmClockPolicy=Never buys none for this race. Closing the Try Again dialog.",
                Racing.raceRetryDeclinedText(alarmClockDeclined = true, retriesThisRace = 0, raceLimit = 1, budgetLeft = 3, policy = "Never"),
            )
            assertTrue(Racing.raceRetryDeclinedText(false, 0, 1, 0, "Never").contains("this career's retry budget is used up"))
            assertTrue(Racing.raceRetryDeclinedText(false, 0, 2, 4, "Never").contains("the scenario's retry rules do not cover this race"))
            assertTrue(Racing.raceRetryDeclinedText(false, 2, 2, 4, "Never").contains("already used its 2 retries"))
        }

        @Test
        fun `the dialog path logs through the helpers`() {
            val campaign = source("$botDir/Campaign.kt")
            assertTrue(campaign.contains("MessageLog.i(TAG, Racing.raceRetryText(freeRetryShown, racing.retriesThisRace + 1, raceLimit, racing.raceRetries - 1, policy, racing.lastRaceGrade, lostGoalRace))"))
            assertTrue(campaign.contains("MessageLog.i(TAG, Racing.raceRetryDeclinedText(racing.bAlarmClockPolicySkippedThisRace, racing.retriesThisRace, raceLimit, racing.raceRetries, policy))"))
            assertFalse(campaign.contains("Retries remaining: \${racing.raceRetries}"))
            assertFalse(campaign.contains("No retries remaining but Try Again dialog detected"))
        }
    }

    @Nested
    @DisplayName("a failed goal is recorded as such")
    inner class FailedGoal {
        @Test
        fun `a closed Try Again dialog on a goal race fails the goal, outside the finale`() {
            assertTrue(Racing.declinedRetryFailsGoal(runningGoalRace = true, finaleSeason = false))
            assertFalse(Racing.declinedRetryFailsGoal(runningGoalRace = false, finaleSeason = false))
            assertFalse(Racing.declinedRetryFailsGoal(runningGoalRace = true, finaleSeason = true))
        }

        @Test
        fun `a goal met below 1st does not force-end, because the bot no longer opens a Try Again dialog itself`() {
            // A goal met at 2nd-5th used to be followed by the bot's own results-screen retry tap, whose confirmation dialog
            // the per-race cap then closed (the Trackblazer G1 sequence: try_again_alt tapped, then the Try Again dialog).
            // With no retry tap left, every Try Again dialog is the game's, shown after a failed goal.
            val racing = source("$botDir/Racing.kt")
            assertFalse(racing.contains("ButtonTryAgainAlt"), "no results-screen retry tap")
            assertFalse(racing.contains("bRetryDialogOpenedByBot"))
            assertFalse(source("android/app/src/main/java/com/steve1316/uma_android_automation/components/Button.kt").contains("ButtonTryAgainAlt"))
            // Tenno Sho (Spring) lost twice: the game opened the dialog after each loss.
            assertTrue(Racing.declinedRetryFailsGoal(runningGoalRace = true, finaleSeason = false))
        }

        @Test
        fun `the fixture career ledgers FORCE_END, not COMPLETED, and keeps its result`() {
            val forceEnded = Racing.declinedRetryFailsGoal(runningGoalRace = true, finaleSeason = false)
            val outcome = classifyCareerOutcome(TaskResultCode.TASK_RESULT_COMPLETE, forceEnded)
            assertEquals("FORCE_END", outcome)
            assertEquals("FORCE_END", classifyCareerQuality(outcome, finaleRaces = 0, finaleWins = 0))
            val result = com.steve1316.uma_android_automation.careerResultAtEnd(outcome, "B", 6921, 97599, 0, 0, listOf(906, 446, 564, 360, 338))
            assertEquals(listOf(906, 446, 564, 360, 338), result?.finalStats)
        }

        @Test
        fun `the goal race window marks the force-end and closes after the results`() {
            val mandatory = body(source("$botDir/Racing.kt"), "private fun handleMandatoryRace(): Boolean {", "private fun selectMaidenRace(")
            val set = mandatory.indexOf("bRunningGoalRace = true")
            val race = mandatory.indexOf("runRaceWithRetries() to finalizeRaceResults()")
            val cleared = mandatory.indexOf("} finally {\n                bRunningGoalRace = false")
            assertTrue(set in 0 until race && race < cleared, "the flag spans the race and its results")
            val tryAgain = body(source("$botDir/Campaign.kt"), "private fun handleTryAgainDialog(", "open fun onAfterTurnStartUpdates()")
            assertTrue(tryAgain.contains("if (Racing.declinedRetryFailsGoal(racing.bRunningGoalRace, date.bIsFinaleSeason)) markCareerForceEnded(\"MANDATORY_RACE_LOST\")"))
        }

        @Test
        fun `the dashboard run carries the outcome and names a missed goal`() {
            StatusBoard.reset(0L)
            try {
                StatusBoard.runRecorded(RunRecord(1, 0L, 1L, "TASK_RESULT_COMPLETE", "Mihono_Bourbon", "URA Finale", "FORCE_END", 56))
                StatusBoard.runRecorded(RunRecord(2, 2L, 3L, "TASK_RESULT_COMPLETE", "Vodka", "URA Finale", null, 75))
                val tally = StatusBoard.Tally(0, 0, 0, 0, 0, 0, 0)
                val runs = StatusBoard.statusJson(StatusBoard.snapshot(), 4L, sessionActive = false, armed = false, lastProgressAt = null, tally = tally).getJSONArray("runs")
                assertEquals("FORCE_END", runs.getJSONObject(0).getString("outcome"))
                assertTrue(runs.getJSONObject(1).isNull("outcome"))
            } finally {
                StatusBoard.reset()
            }
            val app = source("android/app/src/main/assets/dashboard/app.js")
            assertTrue(app.contains("run.outcome === 'FORCE_END' ? 'Ended early: a goal was missed' : 'Career finished'"))
        }
    }
}
