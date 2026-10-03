package com.steve1316.uma_android_automation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A resumed queue's last report covers the whole queue: the earlier sessions' runs, recoveries, TP
 * restores and stops travel in `earlier`, apart from the session's own fields. A finished career whose
 * finalize stopped before Home says so.
 */
@DisplayName("Resumed queue report")
class ResumedQueueReportTest {
    @BeforeEach
    @AfterEach
    fun resetTally() = SessionTally.reset()

    private fun run(n: Int, code: String = "TASK_RESULT_COMPLETE") = JSONObject().put("run", n).put("resultCode", code).put("traineeName", "Trainee $n")

    private fun carats(n: Int) = JSONArray().also { arr -> repeat(n) { arr.put(JSONObject().put("rung", "Carats").put("context", "launch")) } }

    /** A live queue: runs 1-9 played, 11 recoveries and 4 Carat restores, then a halt after run 9 of 10. */
    private fun haltAfterRun9(): JSONObject =
        JSONObject()
            .put("kind", "NAVIGATION_FAILED_BETWEEN_RUNS")
            .put("queueEnabled", true)
            .put("totalRuns", 10)
            .put("startFromRun", 1)
            .put("completedRuns", 9)
            .put("runReached", 9)
            .put("resumable", true)
            .put("reasonKey", "DIALOG_NOT_CLOSED")
            .put("runs", JSONArray().also { arr -> (1..9).forEach { arr.put(run(it)) } })
            .put("recoveries", JSONObject().put("accessibilityRebinds", 11).put("gameRelaunches", 0))
            .put("tpRestores", carats(4))

    private fun ledger(earlier: JSONObject?, startFromRun: Int, total: Int, ownRuns: IntRange) =
        SessionLedger("resumed", 2_000L, "9.9.9", 1).apply {
            queueEnabled = true
            totalRuns = total
            this.startFromRun = startFromRun
            completedRuns = total
            this.earlier = earlier
            ownRuns.forEach { addRun(RunRecord(it, 0L, 0L, "TASK_RESULT_COMPLETE", "Trainee", null, "COMPLETED", 75, traineeName = "Trainee $it")) }
        }

    private fun completed(l: SessionLedger) = l.report(SessionEndVerdict(SessionEnd.COMPLETED, resumable = false, careerInFlight = false), 3_000L).toJson()

    @Test
    fun `the final card after a resume covers the runs, restores and halt from before it`() {
        val earlier = earlierQueueFor(haltAfterRun9(), totalRuns = 10, startFromRun = 10)
        SessionTally.accessibilityRebinds.incrementAndGet()
        val report = completed(ledger(earlier, startFromRun = 10, total = 10, ownRuns = 10..10))

        assertEquals(listOf(10), report.getJSONArray("runs").let { r -> (0 until r.length()).map { r.getJSONObject(it).getInt("run") } }, "own runs stay this session's")
        assertEquals(1, report.getJSONObject("recoveries").getInt("accessibilityRebinds"))
        val e = report.getJSONObject("earlier")
        assertEquals((1..9).toList(), (0 until e.getJSONArray("runs").length()).map { e.getJSONArray("runs").getJSONObject(it).getInt("run") })
        assertEquals(4, e.getJSONArray("tpRestores").length())
        assertEquals(11, e.getJSONObject("recoveries").getInt("accessibilityRebinds"))
        assertEquals(9, e.getJSONArray("stops").getJSONObject(0).getInt("run"))

        val text = queueReportText(report)
        assertEquals("Queue finished", text.title)
        assertEquals("All 10 runs are done. It was resumed after it stopped at run 9.", text.reason)
        // Survives the stored round trip a later app start reads.
        assertEquals(e.toString(), JSONObject(report.toString()).getJSONObject("earlier").toString())
    }

    @Test
    fun `a re-entered run and a second resume never count a run, restore or recovery twice`() {
        val runs = JSONArray().also { arr -> (1..4).forEach { arr.put(run(it)) } }.put(run(5, "TASK_RESULT_UNHANDLED_EXCEPTION"))
        val inFlight = haltAfterRun9().put("kind", "RUN_HALTED").put("runReached", 5).put("runs", runs)
        val first = earlierQueueFor(inFlight, totalRuns = 10, startFromRun = 5)!!
        assertEquals(4, first.getJSONArray("runs").length(), "run 5 is played again by the resumed session")

        SessionTally.accessibilityRebinds.incrementAndGet()
        SessionTally.recordTpRestore("Carats", "launch")
        val secondHalt =
            ledger(first, startFromRun = 5, total = 10, ownRuns = 5..7)
                .report(SessionEndVerdict(SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS, resumable = true, careerInFlight = false), 3_000L)
                .toJson()
                .put("runReached", 7)
        val second = earlierQueueFor(secondHalt, totalRuns = 10, startFromRun = 8)!!
        assertEquals((1..7).toList(), (0 until second.getJSONArray("runs").length()).map { second.getJSONArray("runs").getJSONObject(it).getInt("run") })
        assertEquals(5, second.getJSONArray("tpRestores").length())
        assertEquals(12, second.getJSONObject("recoveries").getInt("accessibilityRebinds"))
        assertEquals(listOf(5, 7), (0 until second.getJSONArray("stops").length()).map { second.getJSONArray("stops").getJSONObject(it).getInt("run") })
        SessionTally.reset()
        assertEquals("All 10 runs are done. It was resumed after it stopped at runs 5 and 7.", queueReportText(completed(ledger(second, 8, 10, 8..10))).reason)
    }

    @Test
    fun `a fresh queue after a finished one, another queue or a single run starts clean`() {
        val finished = haltAfterRun9().put("kind", "COMPLETED").put("resumable", false)
        assertNull(earlierQueueFor(finished, totalRuns = 10, startFromRun = 1))
        assertNull(earlierQueueFor(haltAfterRun9(), totalRuns = 8, startFromRun = 4), "another queue length")
        assertNull(earlierQueueFor(haltAfterRun9().put("queueEnabled", false), totalRuns = 10, startFromRun = 10), "a single run")
        assertNull(earlierQueueFor(haltAfterRun9().put("kind", "NOTHING_TO_RESUME"), totalRuns = 10, startFromRun = 10))
        assertNull(earlierQueueFor(haltAfterRun9().put("kind", "SOMETHING_NEW"), totalRuns = 10, startFromRun = 10))
        assertNull(earlierQueueFor(null, totalRuns = 10, startFromRun = 10))

        val fresh = completed(ledger(null, startFromRun = 1, total = 2, ownRuns = 1..2))
        assertFalse(fresh.has("earlier"))
        assertEquals("All 2 runs are done.", queueReportText(fresh).reason)
    }

    @Test
    fun `a resumed report without the earlier runs still says its errors are since the resume`() {
        val report = completed(ledger(null, startFromRun = 4, total = 4, ownRuns = 4..4)).put("runs", JSONArray().put(run(4, "TASK_RESULT_TIMED_OUT")))
        assertEquals("All 4 runs are done. 1 run since the queue resumed ended with an error.", queueReportText(report).reason)
        val earlier = earlierQueueFor(haltAfterRun9().put("totalRuns", 4).put("runReached", 3).put("runs", JSONArray().put(run(1)).put(run(2, "TASK_RESULT_TIMED_OUT")).put(run(3))), 4, 4)
        assertEquals("All 4 runs are done. It was resumed after it stopped at run 3. 2 runs ended with an error.", queueReportText(report.put("earlier", earlier)).reason)
    }

    @Test
    fun `a finished career whose finalize stopped before Home says the bot stopped and why`() {
        val l = ledger(null, startFromRun = 1, total = 1, ownRuns = 1..1).apply { finalizeStopKey = "DIALOG_NOT_CLOSED" }
        val text = queueReportText(completed(l))
        assertEquals("Queue finished", text.title)
        assertEquals(
            "The run is done. The bot then stopped before the game was back on its home screen: it was stuck on a game dialog that showed none of the buttons it knows how to press.",
            text.reason,
        )
        assertEquals("Close the dialog in the game.", text.nextAction)

        val single = l.report(SessionEndVerdict(SessionEnd.SINGLE_RUN_ENDED, resumable = false, careerInFlight = false), 3_000L).toJson().put("runs", JSONArray().put(run(1)))
        assertTrue(queueReportText(single).reason.startsWith("The career finished. The bot then stopped before the game was back on its home screen:"))
        val stopped = l.report(SessionEndVerdict(SessionEnd.STOPPED_BY_USER, resumable = false, careerInFlight = false), 3_000L).toJson()
        assertFalse(stopped.has("finalizeStopKey"), "only a finished queue or run reports it")
        assertEquals("The run is done.", queueReportText(completed(ledger(null, 1, 1, 1..1))).reason)
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative).readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    @Test
    fun `the session seeds a resume from the last report and records a finalize that stopped`() {
        val start = source("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt")
        val seed = start.indexOf("if (resumedQueue) ledger.earlier = runCatching { earlierQueueFor(QueueLedger.lastReport(context)?.let { JSONObject(it) }, totalRuns, startFromRun) }.getOrNull()")
        assertTrue(seed in 0 until start.indexOf("QueueLedger.beginSession(context, ledger.sessionId, ledger.openJson())"), "before the open record is written")
        assertTrue(start.contains("resumedQueue = true\n                        next\n"), "only a plan that resumes sets it")
        val recorded = "if (finalizeResult.lastDetectedState != \"STOPPED\") ledger.finalizeStopKey = finalizeResult.reasonKey\n"
        assertTrue(start.contains("logNavigationFailure(finalizeResult)\n                            $recorded"))
        val ledgerSource = source("android/app/src/main/java/com/steve1316/uma_android_automation/QueueReport.kt")
        assertTrue(ledgerSource.contains(".apply { earlier?.let { put(\"earlier\", it) } }\n\n    @Synchronized\n    fun facts("), "the open record carries it")
        assertTrue(ledgerSource.contains("earlier = open.optJSONObject(\"earlier\"),"), "and so does a dead session's report")
    }
}
