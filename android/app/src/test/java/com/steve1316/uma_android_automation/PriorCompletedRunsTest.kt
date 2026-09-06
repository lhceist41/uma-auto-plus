package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for [StartModule.priorCompletedRunsFor] - how many runs a resumed queue treats as
 * already done before this process started.
 *
 * The bug this locks down: completedRuns used to start at 0 every process launch, so a queue
 * resumed at run 4 of 6 that went on to finish runs 4-6 reported "Completed 3 of 6 runs" (this
 * session's count only) instead of the truthful 6/6. priorCompletedRunsFor seeds completedRuns
 * with what the persisted queue state proves already finished, keyed by phase.
 */
@DisplayName("Resumed-queue prior-completed-runs count")
class PriorCompletedRunsTest {
    @Test
    fun `PHASE_LAUNCHING counts currentRun as done - its career already finished`() {
        // currentRun's career finished; the interrupted work was launching currentRun+1.
        assertEquals(4, StartModule.priorCompletedRunsFor(StartModule.PHASE_LAUNCHING, 4))
        assertEquals(1, StartModule.priorCompletedRunsFor(StartModule.PHASE_LAUNCHING, 1))
    }

    @Test
    fun `PHASE_CAREER does not count currentRun - it was still in flight`() {
        // currentRun's career was interrupted mid-play; resume either re-enters or abandons it,
        // but it was never recorded as completed.
        assertEquals(3, StartModule.priorCompletedRunsFor(StartModule.PHASE_CAREER, 4))
        assertEquals(0, StartModule.priorCompletedRunsFor(StartModule.PHASE_CAREER, 1))
    }

    @Test
    fun `a queue resumed at run 4 of 6 that finishes runs 4-6 reports 6 of 6, not 3 of 6`() {
        // Runs 1-3 finished in an earlier process lifetime; PHASE_CAREER means run 4 was
        // interrupted mid-play (currentRun=4), so priorCompletedRuns is 3, not 4.
        val priorCompletedRuns = StartModule.priorCompletedRunsFor(StartModule.PHASE_CAREER, 4)
        assertEquals(3, priorCompletedRuns)

        // The run loop then plays runs 4, 5, 6 to completion, incrementing once per run.
        var completedRuns = priorCompletedRuns
        for (run in 4..6) completedRuns++

        assertEquals(6, completedRuns, "resumed queue must report the truthful total, not the this-session count of 3")
    }

    @Test
    fun `a queue found already finished on resume (PHASE_LAUNCHING at the last run) reports fully done`() {
        // next > totalRuns short-circuits the loop entirely in this case; the queue never re-runs
        // anything, so priorCompletedRunsFor alone must already read as fully complete.
        assertEquals(6, StartModule.priorCompletedRunsFor(StartModule.PHASE_LAUNCHING, 6))
    }

    @Test
    fun `a non-rotation queue that abandons its interrupted run on resume is never over-reported`() {
        // PHASE_CAREER at the last run (non-rotation resume skips rather than re-enters): the
        // abandoned run must not be counted as done, so the truthful ceiling is totalRuns - 1.
        assertEquals(5, StartModule.priorCompletedRunsFor(StartModule.PHASE_CAREER, 6))
    }

    /**
     * The arithmetic above still passes if the queue loop stops calling the helper, so these guard
     * the wiring: the resume block derives the count from the saved phase and run, and completedRuns
     * starts from it rather than from zero.
     */
    @Nested
    @DisplayName("the run loop actually consumes the helper")
    inner class ProductionWiring {
        private val startModule by lazy {
            repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt")
                .readText().replace("\r\n", "\n")
        }

        @Test
        fun `the resume block derives the prior count from the saved phase and run`() {
            assertTrue(
                startModule.contains("priorCompletedRuns = priorCompletedRunsFor(saved.phase, saved.currentRun)"),
                "the resume block must derive the prior count from the persisted state, not recompute it inline",
            )
        }

        @Test
        fun `completedRuns starts from the prior count, not from zero`() {
            assertTrue(
                startModule.contains("var completedRuns = priorCompletedRuns"),
                "completedRuns must be seeded with the prior count",
            )
            assertFalse(
                Regex("var completedRuns = 0").containsMatchIn(startModule),
                "a resumed queue that restarts the count at zero is the undercount this test exists for",
            )
        }

        @Test
        fun `the seed is established before the run loop increments it`() {
            val seed = startModule.indexOf("var completedRuns = priorCompletedRuns")
            val loop = startModule.indexOf("for (i in startFromRun..totalRuns) {")
            val increment = startModule.indexOf("completedRuns++", loop)
            assertTrue(seed in 0 until loop, "completedRuns must be seeded before the run loop")
            assertTrue(increment > loop, "the loop must still count each run it finishes on top of the seed")
        }

        @Test
        fun `the terminal events report completedRuns rather than the loop index`() {
            for (status in listOf("queueComplete", "queueStopped", "queueHalted")) {
                assertTrue(
                    startModule.contains("sendQueueProgressEvent(completedRuns, totalRuns, \"$status\""),
                    "$status must report the seeded completed count",
                )
            }
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
