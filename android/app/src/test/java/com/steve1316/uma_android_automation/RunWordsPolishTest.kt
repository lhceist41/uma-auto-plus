package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.StatusBoard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Run words")
class RunWordsPolishTest {
    @Test
    fun `a count of runs says 1 run for one`() {
        assertEquals("1 run", runsPhrase(1))
        assertEquals("0 runs", runsPhrase(0))
        assertEquals("4 runs", runsPhrase(4))
    }

    @Test
    fun `the dashboard names a run's trainee only when the career-end line had a name`() {
        StatusBoard.reset(0L)
        try {
            StatusBoard.runRecorded(RunRecord(1, 0L, 1L, "TASK_RESULT_MANUALLY_STOPPED", "unknown", "URA Finale", null, 5))
            StatusBoard.runRecorded(RunRecord(2, 2L, 3L, "TASK_RESULT_COMPLETE", "Mihono_Bourbon", "URA Finale", null, 75))
            val tally = StatusBoard.Tally(0, 0, 0, 0, 0, 0, 0)
            val runs = StatusBoard.statusJson(StatusBoard.snapshot(), 4L, sessionActive = false, armed = false, lastProgressAt = null, tally = tally).getJSONArray("runs")
            assertTrue(runs.getJSONObject(0).isNull("trainee"))
            assertEquals("Mihono Bourbon", runs.getJSONObject(1).getString("trainee"))
        } finally {
            StatusBoard.reset()
        }
    }
}
