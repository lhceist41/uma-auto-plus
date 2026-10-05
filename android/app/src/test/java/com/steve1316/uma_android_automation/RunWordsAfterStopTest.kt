package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.StatusBoard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Run words after a stop")
class RunWordsAfterStopTest {
    private fun ledger() =
        SessionLedger("lost-finish", 2_000L, "9.9.9", 1).apply {
            queueEnabled = true
            totalRuns = 2
            completedRuns = 1
            addRun(RunRecord(1, 0L, 1L, "TASK_RESULT_COMPLETE", "Special_Week", null, "COMPLETED", 75, traineeName = "Special Week"))
            addRun(RunRecord(2, 2L, 3L, "TASK_RESULT_COMPLETE", "Super_Creek", null, "COMPLETED", 75, traineeName = "Super Creek"))
        }

    @Test
    fun `a career whose Finish was lost is marked, the finished one is not`() {
        val ledger = ledger()
        assertNull(ledger.markFinishLost(9))
        assertEquals(true, ledger.markFinishLost(2)?.finishLost)
        val runs = ledger.report(SessionEndVerdict(SessionEnd.RUN_HALTED, resumable = true, careerInFlight = true), 4_000L).toJson().getJSONArray("runs")
        assertFalse(runs.getJSONObject(0).has("finishLost"))
        assertTrue(runs.getJSONObject(1).getBoolean("finishLost"))
        assertEquals("TASK_RESULT_COMPLETE", runs.getJSONObject(1).getString("resultCode"))
    }

    @Test
    fun `the dashboard shows a lost Finish as stopped with the reason, not as done`() {
        StatusBoard.reset(0L)
        try {
            StatusBoard.runRecorded(RunRecord(1, 0L, 1L, "TASK_RESULT_COMPLETE", "Special_Week", null, "COMPLETED", 75))
            StatusBoard.runRecorded(RunRecord(2, 2L, 3L, "TASK_RESULT_COMPLETE", "Super_Creek", null, "COMPLETED", 75, finishLost = true))
            val tally = StatusBoard.Tally(0, 0, 0, 0, 0, 0, 0)
            val runs = StatusBoard.statusJson(StatusBoard.snapshot(), 4L, sessionActive = false, armed = false, lastProgressAt = null, tally = tally).getJSONArray("runs")
            assertEquals("done", runs.getJSONObject(0).getString("state"))
            assertEquals("stopped", runs.getJSONObject(1).getString("state"))
            assertEquals(
                "The game's daily reset or another interruption stopped the career's Finish. The career is kept, and Start finishes it.",
                runs.getJSONObject(1).getJSONObject("words").getString("reason"),
            )
        } finally {
            StatusBoard.reset()
        }
    }

    @Test
    fun `a resume from a launch point does not say what happened to the earlier run`() {
        val (log, note) = StartModule.resumeWords(reEnter = false, savedRun = 1, next = 2, totalRuns = 2)
        assertEquals("Resuming at run 2 of 2, after run 1.", log)
        assertEquals("Auto-resuming: starting at run 2 of 2", note)
    }

    @Test
    fun `a resume that re-enters a run does not claim a career was in flight`() {
        val (log, note) = StartModule.resumeWords(reEnter = true, savedRun = 2, next = 2, totalRuns = 2)
        assertFalse(log.contains("was in flight"))
        assertEquals("Re-entering run 2 of 2; it was interrupted, and a career already in the game finishes under the same trainee's preset.", log)
        assertEquals("Auto-resuming: starting at run 2 of 2 (run 2 was interrupted)", note)
    }
}
