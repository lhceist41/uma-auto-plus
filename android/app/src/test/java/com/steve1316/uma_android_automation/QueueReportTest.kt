package com.steve1316.uma_android_automation

import android.app.ApplicationExitInfo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The queue report must say truthfully how every session ended, including endings nothing reported
 * before: refusals, a queue-off single run, an escaped exception and a process death. These pin the
 * pure pieces the session and the next app start build the report from.
 */
@DisplayName("Queue report")
class QueueReportTest {
    private val queue = SessionEndFacts(queueEnabled = true)

    private fun end(facts: SessionEndFacts) = classifySessionEnd(facts).end

    /** Key-order-independent form of a JSON value, for record equality. */
    private fun canonical(value: Any?): Any? =
        when (value) {
            is JSONObject -> value.keys().asSequence().associateWith { canonical(value.get(it)) }.toSortedMap()
            is JSONArray -> (0 until value.length()).map { canonical(value.get(it)) }
            else -> value
        }

    @Nested
    @DisplayName("every ending has its own classification")
    inner class EveryEnding {
        private val cases: Map<SessionEnd, SessionEndFacts> =
            mapOf(
                SessionEnd.REFUSED_NO_APP_START to SessionEndFacts(launchRefused = true, queueEnabled = true),
                SessionEnd.REFUSED_LAUNCH_IDENTITY to SessionEndFacts(launchIdentityRefused = true, queueEnabled = true),
                SessionEnd.DIAGNOSTIC_ENDED to SessionEndFacts(diagnosticRan = true, queueEnabled = true),
                SessionEnd.ROTATION_NOT_PREPARED to queue.copy(rotationNotPrepared = true),
                SessionEnd.NOTHING_TO_RESUME to queue.copy(nothingToResume = true),
                // The two pre-loop halts also raise the stop flag; the halt must still win.
                SessionEnd.FIRST_SNAPSHOT_MISSING to queue.copy(haltEnd = SessionEnd.FIRST_SNAPSHOT_MISSING, stopRequested = true),
                SessionEnd.LAUNCH_FAILED_BEFORE_RUN to queue.copy(haltEnd = SessionEnd.LAUNCH_FAILED_BEFORE_RUN, stopRequested = true),
                SessionEnd.STOPPED_BY_USER to queue.copy(stopRequested = true),
                SessionEnd.STOPPED_BY_BOT to queue.copy(stopRequested = true, stopByBot = true),
                SessionEnd.SERVICE_ENDED to queue.copy(serviceRunning = false),
                SessionEnd.BREAKPOINT to queue.copy(haltEnd = SessionEnd.BREAKPOINT, haltCareerInFlight = true),
                SessionEnd.GAME_UNRECOVERABLE to queue.copy(haltEnd = SessionEnd.GAME_UNRECOVERABLE, haltCareerInFlight = true),
                SessionEnd.STOP_ON_ERROR to queue.copy(haltEnd = SessionEnd.STOP_ON_ERROR, haltCareerInFlight = true),
                SessionEnd.NEXT_SNAPSHOT_MISSING to queue.copy(haltEnd = SessionEnd.NEXT_SNAPSHOT_MISSING),
                SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS to queue.copy(haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS),
                SessionEnd.WAIT_INTERRUPTED to queue.copy(haltEnd = SessionEnd.WAIT_INTERRUPTED, haltCareerInFlight = true),
                SessionEnd.COMPLETED to queue,
                SessionEnd.SINGLE_RUN_ENDED to SessionEndFacts(queueEnabled = false),
                SessionEnd.ENDED_WITH_ERROR to queue.copy(unexpectedError = true, haltEnd = SessionEnd.BREAKPOINT),
            )

        @Test
        fun `each ending the session can classify comes out of its own facts`() {
            for ((expected, facts) in cases) assertEquals(expected, end(facts), "facts for $expected")
        }

        @Test
        fun `every ending is covered, the process death by its own report builder`() {
            val covered = cases.keys + SessionEnd.PROCESS_ENDED + SessionEnd.REFUSED_DATABASE_UNHEALTHY
            assertEquals(SessionEnd.entries.toSet(), covered, "a new ending needs its own case here")
            val open = JSONObject().put("sessionId", "s").put("startedAt", 1L).put("phase", StartModule.PHASE_CAREER)
            assertEquals(SessionEnd.PROCESS_ENDED, processEndedReport(open, null, null, resumable = false).kind)
        }

        @Test
        fun `a Start refused for the database is its own ending, with nothing run and nothing resumable`() {
            val report = databaseRefusalReport("sid", "9.9.9", now = 5_000L)
            val json = JSONObject(report.ledgerLine())
            assertEquals("REFUSED_DATABASE_UNHEALTHY", json.getString("kind"))
            assertEquals(5_000L, json.getLong("startedAt"))
            assertEquals(5_000L, json.getLong("endedAt"))
            assertEquals(0, json.getJSONArray("runs").length())
            assertFalse(json.getBoolean("resumable"))
            assertFalse(json.getBoolean("careerInFlight"))
            assertEquals("", json.getString("reasonKey"))
        }

        @Test
        fun `a refused launch outranks everything the session had not reached`() {
            assertEquals(SessionEnd.REFUSED_NO_APP_START, end(queue.copy(launchRefused = true, unexpectedError = true, stopRequested = true)))
        }

        @Test
        fun `an escaped exception outranks a halt, a stop and a completion`() {
            assertEquals(SessionEnd.ENDED_WITH_ERROR, end(queue.copy(unexpectedError = true, haltEnd = SessionEnd.STOP_ON_ERROR, stopRequested = true)))
        }

        @Test
        fun `a queue-off session is one run whatever it hit`() {
            assertEquals(SessionEnd.SINGLE_RUN_ENDED, end(SessionEndFacts(queueEnabled = false, haltEnd = SessionEnd.BREAKPOINT, stopRequested = true)))
        }

        @Test
        fun `a bot stop is told apart from a player stop, and both from a dead service`() {
            assertEquals(SessionEnd.STOPPED_BY_BOT, end(queue.copy(stopRequested = true, stopByBot = true, serviceRunning = false)))
            assertEquals(SessionEnd.STOPPED_BY_USER, end(queue.copy(stopRequested = true, serviceRunning = false)))
            assertEquals(SessionEnd.SERVICE_ENDED, end(queue.copy(stopByBot = true, serviceRunning = false)))
        }
    }

    @Nested
    @DisplayName("resumable and career in flight")
    inner class Resumable {
        @Test
        fun `a halt keeps the resume record, so the queue stays resumable`() {
            for (halt in listOf(SessionEnd.BREAKPOINT, SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS, SessionEnd.WAIT_INTERRUPTED)) {
                assertTrue(classifySessionEnd(queue.copy(haltEnd = halt, queueStateActive = true)).resumable, "$halt")
            }
        }

        @Test
        fun `a completion clears it, whatever the record said before`() {
            assertFalse(classifySessionEnd(queue.copy(queueStateActive = true)).resumable)
        }

        @Test
        fun `every ending whose path clears the record is never resumable`() {
            val clearing = SessionEnd.entries.filter { it.clearsQueueState }.toSet()
            assertEquals(
                setOf(SessionEnd.NOTHING_TO_RESUME, SessionEnd.STOPPED_BY_USER, SessionEnd.STOPPED_BY_BOT, SessionEnd.SERVICE_ENDED, SessionEnd.COMPLETED),
                clearing,
            )
            assertFalse(classifySessionEnd(queue.copy(stopRequested = true, queueStateActive = true)).resumable)
            assertFalse(classifySessionEnd(queue.copy(serviceRunning = false, queueStateActive = true)).resumable)
        }

        @Test
        fun `a halt with no resume record is not resumable`() {
            assertFalse(classifySessionEnd(queue.copy(haltEnd = SessionEnd.BREAKPOINT, queueStateActive = false)).resumable)
        }

        @Test
        fun `a halt reports its own career-in-flight answer, other endings go by the last run`() {
            assertFalse(classifySessionEnd(queue.copy(haltEnd = SessionEnd.NEXT_SNAPSHOT_MISSING, haltCareerInFlight = false, lastRunIncomplete = true)).careerInFlight)
            assertTrue(classifySessionEnd(queue.copy(stopRequested = true, lastRunIncomplete = true)).careerInFlight)
            assertFalse(classifySessionEnd(queue.copy(lastRunIncomplete = false)).careerInFlight)
        }
    }

    @Nested
    @DisplayName("per-run career facts")
    inner class CareerFacts {
        @Test
        fun `a stash left by the previous run is not this run's`() {
            val previous = CareerEndStash(seq = 4, trainee = "Special_Week", scenario = "URA_Finale", outcome = "WIN", turn = 78)
            assertNull(careerEndForRun(seqBeforeRun = 4, stash = previous))
        }

        @Test
        fun `a stash written during the run is this run's`() {
            val own = CareerEndStash(seq = 5, trainee = "Silence_Suzuka", scenario = "Unity_Cup", outcome = "WIN", turn = 78)
            assertEquals(own, careerEndForRun(seqBeforeRun = 4, stash = own))
        }
    }

    @Nested
    @DisplayName("session ledger")
    inner class Ledger {
        @BeforeEach
        fun resetTally() = SessionTally.reset()

        private fun ledger(queueEnabled: Boolean) =
            SessionLedger("sid", startedAt = 1_000L, appVersion = "9.9.9", pid = 42).apply {
                this.queueEnabled = queueEnabled
                totalRuns = if (queueEnabled) 3 else 1
            }

        @Test
        fun `a queue-off single run produces a report with its run`() {
            val l = ledger(queueEnabled = false)
            l.addRun(RunRecord(1, 1_100L, 2_000L, "TASK_RESULT_COMPLETE", "Special_Week", "URA_Finale", "WIN", 78))
            val verdict = classifySessionEnd(l.facts(stopRequested = false, stopByBot = false, serviceRunning = true, queueStateActive = false))
            val report = l.report(verdict, endedAt = 2_100L)
            assertEquals(SessionEnd.SINGLE_RUN_ENDED, report.kind)
            assertEquals(1, report.runs.length())
            assertEquals("Special_Week", report.runs.getJSONObject(0).getString("trainee"))
            assertFalse(report.careerInFlight)
        }

        @Test
        fun `the history line and the app's current report carry the identical record`() {
            val l = ledger(queueEnabled = true).apply { haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS }
            l.addRun(RunRecord(1, 1_100L, 2_000L, "TASK_RESULT_COMPLETE", null, null, null, null))
            SessionTally.recordTpRestore("Carats", "launch")
            SessionTally.gameRelaunches.incrementAndGet()
            val report = l.report(classifySessionEnd(l.facts(false, false, true, true)), endedAt = 3_000L)
            assertEquals(SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS, report.kind)
            val line = JSONObject(report.ledgerLine())
            val current = JSONObject(report.lastReportValue())
            assertFalse(current.getBoolean("dismissed"))
            current.remove("dismissed")
            assertEquals(canonical(line), canonical(current), "history $line vs current $current")
            assertEquals("Carats", line.getJSONArray("tpRestores").getJSONObject(0).getString("rung"))
            assertEquals(1, line.getJSONObject("recoveries").getInt("gameRelaunches"))
        }

        @Test
        fun `the report carries codes, keys and numbers, and text only for a breakpoint`() {
            val l =
                ledger(queueEnabled = true).apply {
                    haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS
                    reasonKey = "REQUIRED_DECK"
                    breakpointDetail = "must not appear"
                }
            val nav = l.report(classifySessionEnd(l.facts(false, false, true, true)), 5L).toJson()
            assertEquals("REQUIRED_DECK", nav.getString("reasonKey"))
            assertTrue(nav.isNull("breakpointDetail"))

            l.haltEnd = SessionEnd.BREAKPOINT
            l.breakpointDetail = "Mandatory race detected. Stopping bot..."
            val bp = l.report(classifySessionEnd(l.facts(false, false, true, true)), 5L).toJson()
            assertEquals("Mandatory race detected. Stopping bot...", bp.getString("breakpointDetail"))
            assertEquals("", bp.getString("reasonKey"), "a navigation key never rides on another ending")

            val stop = ledger(queueEnabled = true).apply { reasonKey = "TRAINEE_MISMATCH" }
            val stopped = stop.report(classifySessionEnd(stop.facts(stopRequested = true, stopByBot = true, serviceRunning = true, queueStateActive = false)), 5L)
            assertEquals(SessionEnd.STOPPED_BY_BOT, stopped.kind)
            assertEquals("TRAINEE_MISMATCH", stopped.reasonKey)
        }

        @Test
        fun `a dead session's report is built from the same snapshot the session kept open`() {
            val l =
                ledger(queueEnabled = true).apply {
                    currentRun = 2
                    completedRuns = 1
                }
            l.addRun(RunRecord(1, 1_100L, 2_000L, "TASK_RESULT_COMPLETE", "Special_Week", "URA_Finale", "WIN", 78))
            SessionTally.accessibilityRebinds.incrementAndGet()
            val report = processEndedReport(l.openJson(now = 5_000L), exit = null, lastSeenAt = 9_000L, resumable = true)
            assertEquals("sid", report.sessionId)
            assertEquals(2, report.runReached)
            assertEquals(1, report.completedRuns)
            assertEquals(1, report.runs.length())
            assertEquals(1, report.recoveries.getInt("accessibilityRebinds"))
            assertTrue(report.careerInFlight)
            assertTrue(report.resumable)
        }
    }

    @Nested
    @DisplayName("launch refusal")
    inner class LaunchRefusal {
        @Test
        fun `the gate's refusal before the snapshot is read is a refused launch`() {
            assertTrue(isLaunchGateRefusal(IllegalStateException("Start again from UMA Auto+ with an explicit launch choice"), snapshotReadStarted = false, snapshotReadFinished = false))
        }

        @Test
        fun `the gate's refusals after the snapshot is read are refused launches`() {
            for (message in listOf("Scenario changed after launch verification", "Diagnostic arms do not match the requested handler", "Missing or malformed diagnostic arm")) {
                assertTrue(isLaunchGateRefusal(IllegalArgumentException(message), snapshotReadStarted = true, snapshotReadFinished = true), message)
            }
        }

        @Test
        fun `a throw from inside the snapshot read is an error, whatever its type`() {
            assertFalse(isLaunchGateRefusal(IllegalStateException("Null launch setting"), snapshotReadStarted = true, snapshotReadFinished = false))
            assertFalse(isLaunchGateRefusal(IllegalArgumentException("bad row"), snapshotReadStarted = true, snapshotReadFinished = false))
        }

        @Test
        fun `any other exception type is an error, before or after the read`() {
            for (finished in listOf(false, true)) {
                assertFalse(isLaunchGateRefusal(RuntimeException("database is locked"), snapshotReadStarted = finished, snapshotReadFinished = finished))
                assertFalse(isLaunchGateRefusal(OutOfMemoryError(), snapshotReadStarted = finished, snapshotReadFinished = finished))
            }
        }
    }

    @Nested
    @DisplayName("heartbeat file")
    inner class HeartbeatFile {
        @TempDir
        lateinit var dir: File

        @Test
        fun `the last write is read back for its own session`() {
            assertTrue(writeHeartbeat(dir, "sid", 1_000L))
            assertTrue(writeHeartbeat(dir, "sid", 61_000L))
            assertEquals(61_000L, readHeartbeat(dir, "sid"))
            assertEquals("sid:61000", File(dir, HEARTBEAT_FILE).readText())
        }

        @Test
        fun `the write leaves no temp file behind`() {
            writeHeartbeat(dir, "sid", 1_000L)
            assertEquals(listOf(HEARTBEAT_FILE), dir.list()!!.toList())
        }

        @Test
        fun `another session's, a missing or a garbled heartbeat dates nothing`() {
            assertNull(readHeartbeat(dir, "sid"))
            writeHeartbeat(dir, "other", 1_000L)
            assertNull(readHeartbeat(dir, "sid"))
            File(dir, HEARTBEAT_FILE).writeText("sid:")
            assertNull(readHeartbeat(dir, "sid"))
            File(dir, HEARTBEAT_FILE).writeText("garbage")
            assertNull(readHeartbeat(dir, "sid"))
        }
    }

    @Nested
    @DisplayName("process death")
    inner class ProcessDeath {
        private val startedAt = 10_000L
        private val deadPid = 4321

        @Test
        fun `a previous process's record is a dead session, whatever pid this process got`() {
            // A new process holds no live session, so its pid, equal to the dead one's or not, cannot hide the record.
            assertTrue(isOrphanedSession("old", reportedSessionId = null, liveSessionId = null))
            assertTrue(isOrphanedSession("old", reportedSessionId = "older", liveSessionId = "new"))
        }

        @Test
        fun `this process's live session is not dead`() {
            assertFalse(isOrphanedSession("live", reportedSessionId = "older", liveSessionId = "live"))
        }

        @Test
        fun `a session whose report was already written is not reported twice`() {
            assertFalse(isOrphanedSession("old", reportedSessionId = "old", liveSessionId = null))
            assertFalse(isOrphanedSession(null, reportedSessionId = null, liveSessionId = null))
            assertFalse(isOrphanedSession("", reportedSessionId = null, liveSessionId = null))
        }

        private fun exitFor(reason: Int, status: Int = 0) =
            listOf(
                ExitRecord(pid = 7777, timestamp = startedAt + 50, reason = ApplicationExitInfo.REASON_LOW_MEMORY, status = 0),
                ExitRecord(pid = deadPid, timestamp = startedAt + 100, reason = reason, status = status),
            )

        @Test
        fun `each exit reason is picked from the dead pid's own record and keyed`() {
            val expected =
                mapOf(
                    ApplicationExitInfo.REASON_LOW_MEMORY to "LOW_MEMORY",
                    ApplicationExitInfo.REASON_CRASH to "CRASH",
                    ApplicationExitInfo.REASON_CRASH_NATIVE to "CRASH_NATIVE",
                    ApplicationExitInfo.REASON_USER_REQUESTED to "USER_REQUESTED",
                    ApplicationExitInfo.REASON_SIGNALED to "SIGNALED",
                    ApplicationExitInfo.REASON_ANR to "ANR",
                    ApplicationExitInfo.REASON_OTHER to "OTHER",
                )
            for ((reason, key) in expected) {
                val picked = pickExitRecord(exitFor(reason, status = 9), deadPid, startedAt)
                assertEquals(deadPid, picked?.pid, "reason $key")
                assertEquals(key, exitReasonKey(picked!!.reason))
                val report = processEndedReport(openSnapshot(), picked, lastSeenAt = null, resumable = false)
                assertEquals(key, report.exitInfo!!.getString("reason"))
                assertEquals(9, report.exitInfo!!.getInt("status"))
                assertEquals(startedAt + 100, report.endedAt)
                assertEquals("exit_info", report.endedAtSource)
            }
        }

        @Test
        fun `a record older than the session belongs to an earlier process with the same pid`() {
            assertNull(pickExitRecord(listOf(ExitRecord(deadPid, startedAt - 1, ApplicationExitInfo.REASON_CRASH, 0)), deadPid, startedAt))
        }

        @Test
        fun `below API 30 there are no exit records and the report falls back to the last heartbeat`() {
            val records = exitRecordsFor(29) { fail("the exit-record API must not be called below API 30") }
            assertNull(records)
            assertNull(pickExitRecord(records, deadPid, startedAt))
            val report = processEndedReport(openSnapshot(), null, lastSeenAt = startedAt + 60_000, resumable = true)
            assertEquals(startedAt + 60_000, report.endedAt)
            assertEquals("last_seen", report.endedAtSource)
            assertTrue(report.toJson().isNull("exitInfo"))
            assertTrue(report.resumable)
        }

        @Test
        fun `from API 30 the records are read`() {
            val list = listOf(ExitRecord(deadPid, startedAt + 1, ApplicationExitInfo.REASON_CRASH, 0))
            assertEquals(list, exitRecordsFor(30) { list })
        }

        @Test
        fun `a death while launching the next run does not claim a career in flight`() {
            val launching = openSnapshot().put("phase", StartModule.PHASE_LAUNCHING)
            assertFalse(processEndedReport(launching, null, null, resumable = true).careerInFlight)
            assertTrue(processEndedReport(openSnapshot(), null, null, resumable = true).careerInFlight)
        }

        private fun openSnapshot() =
            JSONObject()
                .put("sessionId", "old")
                .put("pid", deadPid)
                .put("startedAt", startedAt)
                .put("updatedAt", startedAt + 10)
                .put("queueEnabled", true)
                .put("totalRuns", 4)
                .put("currentRun", 2)
                .put("phase", StartModule.PHASE_CAREER)
                .put("runs", JSONArray())
    }
}
