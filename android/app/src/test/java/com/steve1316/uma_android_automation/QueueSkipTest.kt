package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.TraineeNameMatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A queue run whose trainee cannot start (an own-character deck card, an outfit-only preset, a trainee
 * not on the roster) says why on its record and in the report. With a rotation the queue skips that
 * run and goes on with the next trainee from Home; without one it halts, because every run is the same
 * trainee. The decisions are pure; the queue loop's wiring is pinned by source guards.
 */
@DisplayName("Queue runs whose trainee cannot start")
class QueueSkipTest {
    @Nested
    @DisplayName("the decision")
    inner class Decision {
        @Test
        fun `a trainee's own launch stop skips with a rotation and halts without one`() {
            for (key in listOf("TRAINEE_IN_DECK", "TRAINEE_ONLY_OTHER_OUTFIT", "TRAINEE_NOT_FOUND")) {
                assertEquals(UnplayableRunStep.SKIP, unplayableRunStep(key, rotationEnabled = true), key)
                assertEquals(UnplayableRunStep.HALT, unplayableRunStep(key, rotationEnabled = false), key)
            }
        }

        @Test
        fun `a stop that would stop every trainee is not a skip`() {
            for (key in listOf("TP_EMPTY", "REQUIRED_DECK", "DECK_INCOMPLETE", "VETERAN_ROSTER_FULL", "REUSE_OFF", "CONNECTION_LOST", "STUCK_ON_SCREEN", "")) {
                assertNull(unplayableRunStep(key, rotationEnabled = true), key)
                assertNull(unplayableRunStep(key, rotationEnabled = false), key)
            }
        }
    }

    @Nested
    @DisplayName("backing out of the stopped launch")
    inner class BackOut {
        /** Plays [backOutOfLaunch] over [backsToHome] Back presses; returns (at home, presses). */
        private fun play(backsToHome: Int, backFound: Boolean = true, maxBacks: Int = LAUNCH_BACK_OUT_MAX_PRESSES): Pair<Boolean, Int> {
            var presses = 0
            val home = backOutOfLaunch(isHome = { presses >= backsToHome }, pressBack = { if (backFound) presses++; backFound }, settle = {}, maxBacks = maxBacks)
            return home to presses
        }

        @Test
        fun `it presses Back until Home shows, and nothing on Home`() {
            assertEquals(true to 0, play(backsToHome = 0))
            assertEquals(true to 4, play(backsToHome = 4))
            assertEquals(true to LAUNCH_BACK_OUT_MAX_PRESSES, play(backsToHome = LAUNCH_BACK_OUT_MAX_PRESSES))
        }

        @Test
        fun `it stops at the press limit and at a missing Back`() {
            assertEquals(false to 3, play(backsToHome = 5, maxBacks = 3))
            assertEquals(false to 0, play(backsToHome = 2, backFound = false))
        }
    }

    @Nested
    @DisplayName("the run record and its words")
    inner class Record {
        private val skipped =
            RunRecord(
                2, 1L, 2L, "TASK_RESULT_SKIPPED_BY_QUEUE", null, null, null, null,
                traineeName = "Mihono Bourbon", reasonKey = "TRAINEE_ONLY_OTHER_OUTFIT", reasonTrainee = "Mihono Bourbon", reasonOutfit = "CODE: ICING",
            )

        @Test
        fun `a stopped launch keeps its reason on the record, and a launched run carries none`() {
            val json = runRecordJson(skipped)
            assertEquals("TRAINEE_ONLY_OTHER_OUTFIT", json.getString("reasonKey"))
            assertEquals("Mihono Bourbon", json.getString("reasonTrainee"))
            assertEquals("CODE: ICING", json.getString("reasonOutfit"))
            val played = runRecordJson(RunRecord(1, 1L, 2L, "TASK_RESULT_COMPLETE", "Vodka", "URA_Finale", "COMPLETED", 78))
            for (key in listOf("reasonKey", "reasonTrainee", "reasonOutfit")) assertFalse(played.has(key), key)
        }

        @Test
        fun `the dashboard's run words name the reason`() {
            assertEquals(
                "The run was skipped: Mihono Bourbon is on your roster only as CODE: ICING, which has its own preset.",
                runWords(skipped),
            )
            val halted = RunRecord(1, 1L, 2L, "TASK_RESULT_QUEUE_NAVIGATION_FAILED", null, null, null, null, reasonKey = "TRAINEE_IN_DECK")
            assertEquals(
                "The run could not start: the support deck has a card of the trainee's own character, so the game would not start the career. Nothing was spent.",
                runWords(halted),
            )
            assertEquals(runWords("TASK_RESULT_COMPLETE"), runWords(RunRecord(1, 1L, 2L, "TASK_RESULT_COMPLETE", null, null, null, null)))
        }
    }

    @Nested
    @DisplayName("the roster banner in the log")
    inner class Banner {
        @Test
        fun `a bracket the crop cut open is dropped, a whole one is kept`() {
            assertEquals("[CODE: ICING] Mihono Bourbon", TraineeNameMatcher.bannerForLog("[CODE: ICING] Mihono Bourbon ("))
            assertEquals("Mihono Bourbon", TraineeNameMatcher.bannerForLog(" Mihono Bourbon [ "))
            assertEquals("Mihono Bourbon (Summer)", TraineeNameMatcher.bannerForLog("Mihono Bourbon (Summer)"))
            assertEquals("[CODE: ICING] Mihono Bourbon", TraineeNameMatcher.bannerForLog("[CODE: ICING] Mihono Bourbon"))
        }
    }

    @Nested
    @DisplayName("the queue loop's wiring")
    inner class Wiring {
        private val start by lazy { source("StartModule.kt") }
        private val navigator by lazy { source("CareerLaunchNavigator.kt") }

        /** The text after [anchor], which must appear exactly once in [src]. */
        private fun after(anchor: String, src: String = start): String {
            assertEquals(1, Regex(Regex.escape(anchor)).findAll(src).count(), anchor)
            return src.substringAfter(anchor)
        }

        @Test
        fun `a run whose own launch stopped keeps its reason, and its trainee's own stop skips or halts`() {
            assertTrue(start.contains("val launchStop = (result as? TaskResult.Error)?.takeIf { enableRunQueue && it.reasonKey.isNotEmpty() }"))
            assertTrue(start.contains("val unplayable = if (queueSkipRequested || queueStopRequested) null else launchStop?.let { unplayableRunStep(it.reasonKey, rotation.enabled) }"))
            val effective = after("val effectiveResult =").substringBefore("if (finishesLastCareer(")
            assertTrue(effective.indexOf("unplayable == UnplayableRunStep.SKIP ->") > effective.indexOf("queueStopRequested ->"), "a Stop or a skip request wins")
            assertTrue(effective.contains("TaskResult.Success(TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE, "))
            val record = start.indexOf("val runCareerEndSeq = recordRun(ledger, i, runStartedAt, careerEndSeqBeforeRun, effectiveResult.code, retried)\n")
            assertTrue(record > 0)
            assertTrue(start.substring(record).lineSequence().drop(1).first().contains("launchStop?.let { attachLaunchStop(ledger, i, it, it.reasonTrainee.ifEmpty { rotationTraineeFor(rotation, i) }) }"))
        }

        @Test
        fun `without a rotation the trainee's own stop halts before Stop Queue on Error, saved to launch that run again`() {
            val halt = after("if (unplayable == UnplayableRunStep.HALT && launchStop != null) {").substringBefore("// Evaluate the result.")
            assertTrue(halt.contains("ledger.haltEnd = SessionEnd.LAUNCH_FAILED_BEFORE_RUN"))
            assertTrue(halt.contains("ledger.reasonKey = launchStop.reasonKey"))
            assertTrue(halt.contains("ledger.reasonRotation = false"))
            assertTrue(halt.contains("queueHaltRun = i - 1"))
            assertTrue(halt.contains("queueHaltCareerInFlight = false"))
            assertTrue(halt.contains("if (i > 1) saveQueueState(context, active = true, currentRun = i - 1, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns) else clearQueueState(context)"))
            assertTrue(halt.trimEnd().endsWith("break\n                    }"), "the halt leaves the loop before the error branch")
            assertTrue(start.indexOf("if (unplayable == UnplayableRunStep.HALT && launchStop != null) {") in 0 until start.indexOf("if (stopOnError) {"))
        }

        @Test
        fun `a skipped run is saved past, left from Home, and the next trainee's own launch follows`() {
            val skip = after("                    if (unplayable == UnplayableRunStep.SKIP) {\n").substringBefore("// Debug diagnostics are single-shot")
            assertTrue(skip.contains("if (!leaveSkippedRun(ledger, i, totalRuns, completedRuns)) {\n                            haltSkipping(i, snapshotMissing = false)\n                            break"))
            assertTrue(skip.contains("if (i < totalRuns && applyRotationForRun(rotation, i + 1, reuseLastLaunchSetup) == null) {\n                            haltSkipping(i + 1, snapshotMissing = true)\n                            break"))
            assertTrue(skip.trimEnd().endsWith("continue\n                    }"), "no career end, no wait, no between-run launch")
            val leave = after("private fun leaveSkippedRun(").substringBefore("\n    }\n")
            assertTrue(leave.contains("saveQueueState(context, active = true, currentRun = run, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns)"))
            assertTrue(leave.indexOf("saveQueueState(") < leave.indexOf("CareerLaunchNavigator(context).backOutToHome()"), "saved past before the game is touched")
            assertFalse(after("TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE -> {").substringBefore("}").contains("completedRuns++"), "a skipped run is not counted as done")
        }

        @Test
        fun `a launch stopped by its trainee before the run is left from Home, and the run's own launch records the skip`() {
            assertTrue(start.contains("if (!resumeReEntersCareer && skipsTrainee(navResult, rotation) && coldStartNavigator.backOutToHome()) {"))
            assertTrue(start.contains("if (skipsTrainee(navResult, rotation) && CareerLaunchNavigator(context).backOutToHome()) {"))
            for (divert in listOf("coldStartNavigator.backOutToHome()) {", "CareerLaunchNavigator(context).backOutToHome()) {")) {
                val branch = after(divert).substringBefore("} else if (!navResult.success) {")
                for (forbidden in listOf("queueHaltReason", "queueStopRequested", "break")) assertFalse(branch.contains(forbidden), "$divert: $forbidden")
            }
            val decide = after("private fun skipsTrainee(").substringBefore("\n\n")
            assertTrue(decide.contains("!navResult.success && navResult.lastDetectedState != \"STOPPED\" && unplayableRunStep(navResult.reasonKey, rotation.enabled) == UnplayableRunStep.SKIP"))
        }

        @Test
        fun `backing out presses only Back and stops at Home`() {
            val back = after("fun backOutToHome(): Boolean {", navigator).substringBefore("\n    }\n")
            assertTrue(back.contains("backOutOfLaunch(::isOnHomeScreen, { ButtonBack.click(iu) }, { waitSafe(1.5) }, LAUNCH_BACK_OUT_MAX_PRESSES)"))
            assertEquals(1, Regex("""\.click\(""").findAll(back).count())
            assertTrue(navigator.contains("if (excludedOutfitSeen != null) excludedBannerSeen = TraineeNameMatcher.bannerForLog(banner)"))
        }

        private fun source(relative: String): String = File(kotlinRoot(), relative).readText().replace("\r\n", "\n")

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
}
