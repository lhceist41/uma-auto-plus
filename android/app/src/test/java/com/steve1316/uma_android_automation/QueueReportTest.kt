package com.steve1316.uma_android_automation

import android.app.ApplicationExitInfo
import com.steve1316.uma_android_automation.bot.SparkRowFact
import com.steve1316.uma_android_automation.bot.SparkRowKind
import com.steve1316.uma_android_automation.bot.SparkSetSide
import com.steve1316.uma_android_automation.bot.SparkWhiteClass
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
                SessionEnd.RUN_HALTED to queue.copy(haltEnd = SessionEnd.RUN_HALTED, haltCareerInFlight = true),
                SessionEnd.STOP_ON_ERROR to queue.copy(haltEnd = SessionEnd.STOP_ON_ERROR, haltCareerInFlight = true),
                SessionEnd.NEXT_SNAPSHOT_MISSING to queue.copy(haltEnd = SessionEnd.NEXT_SNAPSHOT_MISSING),
                SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS to queue.copy(haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS),
                SessionEnd.WAIT_INTERRUPTED to queue.copy(haltEnd = SessionEnd.WAIT_INTERRUPTED, haltCareerInFlight = true),
                SessionEnd.COMPLETED to queue,
                SessionEnd.STOPPED_AFTER_CAREER to queue.copy(stoppedAfterCareer = true),
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
                setOf(SessionEnd.NOTHING_TO_RESUME, SessionEnd.STOPPED_BY_BOT, SessionEnd.SERVICE_ENDED, SessionEnd.COMPLETED),
                clearing,
            )
            assertFalse(classifySessionEnd(queue.copy(serviceRunning = false, queueStateActive = true)).resumable)
        }

        @Test
        fun `a player's stop is resumable exactly when it kept the record`() {
            assertTrue(classifySessionEnd(queue.copy(stopRequested = true, queueStateActive = true)).resumable, "stopped in the middle of a career")
            assertFalse(classifySessionEnd(queue.copy(stopRequested = true, queueStateActive = false)).resumable, "stopped between careers")
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

        @Test
        fun `the run keeps its stored identifier and adds the name as the game shows it`() {
            val named = runRecordJson(RunRecord(1, 1_100L, 2_000L, "TASK_RESULT_MANUALLY_STOPPED", "El_Condor_Pasa", "URA_Finale", "INCOMPLETE", 4, traineeName = "El Condor Pasa"))
            assertEquals("El_Condor_Pasa", named.getString("trainee"))
            assertEquals("El Condor Pasa", named.getString("traineeName"))
            // A name that was never read adds nothing, so older readers see the record as before.
            assertFalse(runRecordJson(RunRecord(1, 1_100L, 2_000L, "TASK_RESULT_COMPLETE", null, null, null, null)).has("traineeName"))
        }

        @Test
        fun `the career end stashes the shown name beside the identifier, and each run takes both`() {
            fun source(relative: String): String {
                var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
                val path = "android/app/src/main/java/com/steve1316/uma_android_automation/$relative"
                repeat(8) {
                    if (java.io.File(dir, path).isFile) return java.io.File(dir, path).readText().replace("\r\n", "\n")
                    dir = dir?.parentFile
                }
                throw AssertionError("$path not found")
            }
            val campaign = source("bot/Campaign.kt")
            assertTrue(campaign.contains("val resolvedName = shownName.ifEmpty { \"unknown\" }.replace(\" \", \"_\")"), "the identifier is derived exactly as before")
            assertTrue(campaign.contains("StartModule.lastCareerEndTrainee = resolvedName\n        StartModule.lastCareerEndTraineeName = shownName.ifEmpty { null }\n"))
            val startModule = source("StartModule.kt")
            assertTrue(startModule.contains("lastCareerEndTurn, lastCareerEndTraineeName, lastCareerEndResult)"))
            val recordTail = listOf("careerEnd?.turn,", "retried,", "progress,", "careerEnd?.traineeName,", "careerEnd?.result,").joinToString("") { "                $it\n" }
            assertTrue(startModule.contains(recordTail))
        }
    }

    @Nested
    @DisplayName("career result and kept sparks on the run")
    inner class CareerResults {
        private val finished = CareerResult("A", 10757, 221054, 3, 3, listOf(1248, 508, 816, 395, 438))
        private val kept = listOf(KeptSpark("Power", "stat", 1), KeptSpark("Turf", "aptitude", 2), KeptSpark("Tokyo Yushun", "other", 3))

        /** A stored run exactly as the previous version wrote it: none of the result keys. */
        private val oldStoredRun =
            """{"run":1,"startedAt":1100,"endedAt":2000,"resultCode":"TASK_RESULT_COMPLETE","trainee":"El_Condor_Pasa","scenario":"URA_Finale","outcome":"COMPLETED","turn":75,"retried":false,"traineeName":"El Condor Pasa"}"""

        private fun open(runs: JSONArray) =
            JSONObject()
                .put("sessionId", "s")
                .put("appVersion", "1.6.0")
                .put("startedAt", 1_000L)
                .put("updatedAt", 2_000L)
                .put("queueEnabled", true)
                .put("totalRuns", 2)
                .put("startFromRun", 1)
                .put("completedRuns", 1)
                .put("currentRun", 2)
                .put("phase", StartModule.PHASE_LAUNCHING)
                .put("runs", runs)

        @Test
        fun `a stored run without the result keys reads back unchanged, with no result, and the same words`() {
            val report = processEndedReport(JSONObject(open(JSONArray().put(JSONObject(oldStoredRun))).toString()), null, null, resumable = false)
            val run = report.toJson().getJSONArray("runs").getJSONObject(0)
            assertEquals(canonical(JSONObject(oldStoredRun)), canonical(run))
            for (key in listOf("rank", "estScore", "fans", "finale", "finalStats", "sparks", "sparksNote")) assertFalse(run.has(key), key)
            // A result-less record is still written byte-for-byte the way the previous version wrote it.
            val same = RunRecord(1, 1_100L, 2_000L, "TASK_RESULT_COMPLETE", "El_Condor_Pasa", "URA_Finale", "COMPLETED", 75, traineeName = "El Condor Pasa")
            assertEquals(canonical(JSONObject(oldStoredRun)), canonical(JSONObject(runRecordJson(same).toString())))
            // The words never depend on the new keys.
            val withResult = runRecordJson(same.copy(result = finished, sparks = kept, sparksNote = "n"))
            val newer = processEndedReport(open(JSONArray().put(withResult)), null, null, resumable = false)
            assertEquals(queueReportText(report.toJson()), queueReportText(newer.toJson()))
        }

        @Test
        fun `a run with a result and kept sparks round-trips through the stored record`() {
            val record =
                RunRecord(1, 1_100L, 2_000L, "TASK_RESULT_COMPLETE", "El_Condor_Pasa", "URA_Finale", "COMPLETED", 75)
                    .copy(result = finished, sparks = kept, sparksNote = "rerolled once, kept the original sparks")
            val stored = JSONObject(open(JSONArray().put(runRecordJson(record))).toString())
            val run = processEndedReport(stored, null, null, resumable = false).toJson().getJSONArray("runs").getJSONObject(0)
            assertEquals("A", run.getString("rank"))
            assertEquals(10757, run.getInt("estScore"))
            assertEquals(221054, run.getInt("fans"))
            assertEquals(canonical(JSONObject("""{"won":3,"of":3}""")), canonical(run.getJSONObject("finale")))
            assertEquals(canonical(JSONObject("""{"speed":1248,"stamina":508,"power":816,"guts":395,"wit":438}""")), canonical(run.getJSONObject("finalStats")))
            assertEquals(
                canonical(JSONArray("""[{"name":"Power","type":"stat","stars":1},{"name":"Turf","type":"aptitude","stars":2},{"name":"Tokyo Yushun","type":"other","stars":3}]""")),
                canonical(run.getJSONArray("sparks")),
            )
            assertEquals("rerolled once, kept the original sparks", run.getString("sparksNote"))
            assertEquals(canonical(JSONObject(runRecordJson(record).toString())), canonical(run))
        }

        @Test
        fun `only a career that ended has a result, and unread values stay out`() {
            assertNull(careerResultAtEnd("INCOMPLETE", "B", 9000, 50_000, 0, 0, listOf(600, 300, 400, 300, 300)), "a stop mid-career has no final result")
            assertEquals(finished, careerResultAtEnd("COMPLETED", "A", 10757, 221054, 3, 3, listOf(1248, 508, 816, 395, 438)))
            val partial = careerResultAtEnd("FORCE_END", null, null, 1, 0, 0, listOf(-1, 300, -1, -1, -1))!!
            assertNull(partial.rank)
            assertNull(partial.estScore)
            assertNull(partial.fans, "1 fan is the default, not a read")
            assertNull(partial.finaleWon)
            assertNull(partial.finaleOf, "no finale race seen")
            assertEquals(listOf(null, 300, null, null, null), partial.finalStats)
            assertNull(careerResultAtEnd("COMPLETED", null, null, 2, 0, 0, List(5) { -1 })!!.finalStats, "no stat read at all")
            assertEquals(2, careerResultAtEnd("COMPLETED", null, null, 2, 3, 2, List(5) { -1 })!!.fans)
            val lost = careerResultAtEnd("COMPLETED", null, null, 2, 3, 2, List(5) { -1 })!!
            assertEquals(2 to 3, lost.finaleWon to lost.finaleOf)
        }

        @Test
        fun `a stat the career end could only report at its last-known value is marked, and nothing else is`() {
            val lastKnown = careerResultAtEnd("COMPLETED", "A", 10757, 221054, 3, 3, listOf(1248, 508, 1213, 395, 438), listOf("pwr"))!!
            assertEquals(listOf("power"), lastKnown.lastKnownStats)
            assertEquals(1213, lastKnown.finalStats!![2], "the value stays, marked")
            assertEquals(emptyList<String>(), careerResultAtEnd("COMPLETED", "A", 10757, 221054, 3, 3, listOf(1248, 508, 816, 395, 438))!!.lastKnownStats)
            val unread = careerResultAtEnd("COMPLETED", "A", 10757, 221054, 3, 3, listOf(1248, -1, 816, 395, 438), listOf("sta", "bogus", "spd"))!!
            assertEquals(listOf("speed"), unread.lastKnownStats, "an unread stat shows no value, and an unknown key is ignored")
        }

        @Test
        fun `the run record lists last-known stats only when there are some, and reads back unchanged`() {
            val base = RunRecord(1, 1_100L, 2_000L, "TASK_RESULT_COMPLETE", "El_Condor_Pasa", "URA_Finale", "COMPLETED", 75)
            assertFalse(runRecordJson(base.copy(result = finished)).has("lastKnownStats"), "additive: absent for a fully read career")
            val marked = base.copy(result = finished.copy(lastKnownStats = listOf("speed", "power")))
            val json = runRecordJson(marked)
            assertEquals(listOf("speed", "power"), (0 until json.getJSONArray("lastKnownStats").length()).map { json.getJSONArray("lastKnownStats").getString(it) })
            assertEquals(1248, json.getJSONObject("finalStats").getInt("speed"), "finalStats keeps its keys and values")
            val stored = JSONObject(open(JSONArray().put(json)).toString())
            val run = processEndedReport(stored, null, null, resumable = false).toJson().getJSONArray("runs").getJSONObject(0)
            assertEquals(canonical(JSONObject(json.toString())), canonical(run))
        }

        @Test
        fun `spark types follow the bot's rows, and a white is a skill only when the catalog knew it`() {
            fun type(
                kind: SparkRowKind,
                white: SparkWhiteClass? = null,
            ) = keptSpark(SparkRowFact("x", 2, kind, white)).type
            assertEquals("stat", type(SparkRowKind.STAT))
            assertEquals("aptitude", type(SparkRowKind.APTITUDE))
            assertEquals("unique", type(SparkRowKind.UNIQUE))
            assertEquals("skill", type(SparkRowKind.WHITE, SparkWhiteClass.SKILL))
            assertEquals("other", type(SparkRowKind.WHITE, SparkWhiteClass.RACE))
            assertEquals("other", type(SparkRowKind.WHITE, SparkWhiteClass.UNKNOWN))
            assertEquals("other", type(SparkRowKind.WHITE, null))
            assertEquals(KeptSpark("x", "stat", 2), keptSpark(SparkRowFact("x", 2, SparkRowKind.STAT)))
        }

        @Test
        fun `the note names the kept set only after a reroll`() {
            assertNull(sparksNoteFor(false, null))
            assertNull(sparksNoteFor(false, SparkSetSide.ORIGINAL), "no reroll, nothing to say")
            assertNull(sparksNoteFor(true, null), "a reroll with no chosen side says nothing it cannot back")
            assertEquals("rerolled once, kept the original sparks", sparksNoteFor(true, SparkSetSide.ORIGINAL))
            assertEquals("rerolled once, kept the new sparks", sparksNoteFor(true, SparkSetSide.REROLLED))
        }

        @Test
        fun `a run that died before its career end inherits neither the previous result nor its sparks`() {
            val previous = CareerEndStash(seq = 4, trainee = "Special_Week", scenario = "URA_Finale", outcome = "COMPLETED", turn = 75, result = finished)
            val careerEnd = careerEndForRun(seqBeforeRun = 4, stash = previous)
            assertNull(careerEnd)
            val record =
                RunRecord(2, 1_100L, 2_000L, "TASK_RESULT_UNHANDLED_EXCEPTION", careerEnd?.trainee, careerEnd?.scenario, careerEnd?.outcome, careerEnd?.turn)
                    .copy(result = careerEnd?.result)
            assertNull(record.result)
            val previousSparks = CareerEndSparks(seq = 4, sparks = kept, note = null)
            assertNull(sparksForRun(careerEnd?.seq, previousSparks), "no career end, no sparks")
            assertNull(sparksForRun(5, previousSparks), "sparks from another career's end")
            assertEquals(previousSparks, sparksForRun(4, previousSparks))
            assertEquals(finished, careerEndForRun(seqBeforeRun = 3, stash = previous)?.result, "the run that wrote the stash takes its result")
        }

        @Test
        fun `kept sparks reach the run's record and its open snapshot, and only that run`() {
            val l = SessionLedger("s", 1_000L, "1.6.0", 1)
            l.addRun(RunRecord(1, 1_100L, 2_000L, "TASK_RESULT_COMPLETE", "Special_Week", "URA_Finale", "COMPLETED", 75, result = finished))
            l.addRun(RunRecord(2, 2_100L, 3_000L, "TASK_RESULT_COMPLETE", "El_Condor_Pasa", "URA_Finale", "COMPLETED", 75))
            assertNull(l.attachSparks(3, CareerEndSparks(9, kept, null)), "no record for run 3")
            val updated = l.attachSparks(1, CareerEndSparks(9, kept, "rerolled once, kept the new sparks"))!!
            assertEquals(kept, updated.sparks)
            assertEquals(finished, updated.result, "the result stays")
            val runs = l.openJson(now = 4_000L).getJSONArray("runs")
            assertEquals(2, runs.length())
            assertEquals(3, runs.getJSONObject(0).getJSONArray("sparks").length())
            assertEquals("rerolled once, kept the new sparks", runs.getJSONObject(0).getString("sparksNote"))
            assertFalse(runs.getJSONObject(1).has("sparks"))
        }

        @Test
        fun `the career end stashes the result before the sequence moves, and the sparks follow the kept set only`() {
            fun source(relative: String): String {
                var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
                val path = "android/app/src/main/java/com/steve1316/uma_android_automation/$relative"
                repeat(8) {
                    if (java.io.File(dir, path).isFile) return java.io.File(dir, path).readText().replace("\r\n", "\n")
                    dir = dir?.parentFile
                }
                throw AssertionError("$path not found")
            }
            val campaign = source("bot/Campaign.kt")
            val stashed = campaign.indexOf("StartModule.lastCareerEndResult =")
            assertTrue(stashed > 0 && stashed < campaign.indexOf("StartModule.lastCareerEndSeq++"), "the result is stashed before the sequence bump")
            val startModule = source("StartModule.kt")
            val attachAfterNavigation = Regex("""navigateWithDeadline\([^\n]*\)\n\s*attachCareerEndSparks\(ledger, i, runCareerEndSeq\)""")
            assertEquals(3, attachAfterNavigation.findAll(startModule).count(), "sparks attach after each finalize and after the between-run navigation")
            assertTrue(startModule.contains("val runCareerEndSeq = recordRun("))
            val navigator = source("CareerLaunchNavigator.kt")
            val keptOnly = "if (phase == \"kept\") {\n            val tx = SparkRerollGate.transaction\n            StartModule.lastCareerEndSparks = CareerEndSparks(StartModule.lastCareerEndSeq,"
            assertTrue(navigator.contains(keptOnly))
            assertEquals(1, Regex("StartModule.lastCareerEndSparks =").findAll(navigator).count())
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
        fun `the trainee and outfit a key names ride only with that key, on an ending a key explains`() {
            val l =
                ledger(queueEnabled = true).apply {
                    haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS
                    reasonKey = "TRAINEE_ONLY_OTHER_OUTFIT"
                    reasonTrainee = "Biwa Hayahide"
                    reasonOutfit = "Rouge Caroler"
                }
            val nav = l.report(classifySessionEnd(l.facts(false, false, true, true)), 5L).toJson()
            assertEquals("Biwa Hayahide", nav.getString("reasonTrainee"))
            assertEquals("Rouge Caroler", nav.getString("reasonOutfit"))

            l.haltEnd = SessionEnd.BREAKPOINT
            val bp = l.report(classifySessionEnd(l.facts(false, false, true, true)), 5L).toJson()
            assertFalse(bp.has("reasonTrainee") || bp.has("reasonOutfit"), "the names never ride on another ending")

            assertFalse(nav.has("reasonRotation"), "an applied preset's target (rotation off) writes no rotation flag")
            l.haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS
            l.reasonRotation = true
            val rotated = l.report(classifySessionEnd(l.facts(false, false, true, true)), 5L).toJson()
            assertTrue(rotated.getBoolean("reasonRotation"))
            assertTrue(queueReportText(rotated).nextAction!!.startsWith("Pick the Biwa Hayahide (Rouge Caroler) preset for this trainee under Rotate Trainees"))
            assertTrue(queueReportText(nav).nextAction!!.startsWith("Apply the Biwa Hayahide (Rouge Caroler) preset on Home"))

            val plain = ledger(queueEnabled = true).apply { haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS; reasonKey = "REQUIRED_DECK"; reasonRotation = true }
            val plainJson = plain.report(classifySessionEnd(plain.facts(false, false, true, true)), 5L).toJson()
            assertFalse(plainJson.has("reasonTrainee") || plainJson.has("reasonOutfit") || plainJson.has("reasonRotation"), "a key that names nobody adds no fields")
        }

        @Test
        fun `a single run carries its own launch navigation's key, so its card names the fix`() {
            val l =
                ledger(queueEnabled = false).apply {
                    reasonKey = "TRAINEE_ONLY_OTHER_OUTFIT"
                    reasonTrainee = "Biwa Hayahide"
                    reasonOutfit = "Rouge Caroler"
                }
            l.addRun(RunRecord(1, 1_100L, 2_000L, "TASK_RESULT_QUEUE_NAVIGATION_FAILED", null, null, null, null))
            val report = l.report(classifySessionEnd(l.facts(stopRequested = false, stopByBot = false, serviceRunning = true, queueStateActive = false)), 2_100L)
            assertEquals(SessionEnd.SINGLE_RUN_ENDED, report.kind)
            assertEquals("TRAINEE_ONLY_OTHER_OUTFIT", report.reasonKey)
            val text = queueReportText(report.toJson())
            assertEquals("The run stopped at Trainee Select: Biwa Hayahide is on your roster only as Rouge Caroler, which has its own preset.", text.reason)
            assertEquals("Apply the Biwa Hayahide (Rouge Caroler) preset on Home, then press Start in UMA Auto+.", text.nextAction)
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
        fun `a refusal logs one readable line without the raw reason`() {
            val overlay = launchRefusalLine(IllegalStateException("Start again from UMA Auto+ with an explicit launch choice"))
            val settings = launchRefusalLine(IllegalArgumentException("Diagnostic arms do not match the requested handler"))
            assertTrue(overlay.contains("fresh Start in UMA Auto+"), overlay)
            assertTrue(settings.contains("did not pass the launch check"), settings)
            for (line in listOf(overlay, settings)) {
                assertTrue(line.startsWith("[START] Nothing was started: ") && '\n' !in line, line)
                assertFalse(line.contains("Exception") || line.contains("explicit launch choice") || line.contains("Diagnostic arms"), line)
            }
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
