package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Every way a session ends has to reach the one report writer in the session's finally. StartModule
 * is a React module that is impractical to unit-test directly, so these are source guards on the
 * wiring: each exit sets the flag or halt ending the classifier reads, the finally writes the report
 * before the session is released, and the bot code feeds the per-run facts and tallies.
 */
@DisplayName("Queue ledger wiring")
class QueueLedgerWiringTest {
    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            for (candidate in listOf(File(dir, relative), File(dir, "app/$relative"), File(dir, "android/app/$relative"))) {
                if (candidate.isFile) return candidate.readText().replace("\r\n", "\n")
            }
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val main = "src/main/java/com/steve1316/uma_android_automation"
    private val startModule by lazy { source("$main/StartModule.kt") }
    private val navigator by lazy { source("$main/CareerLaunchNavigator.kt") }
    private val campaign by lazy { source("$main/bot/Campaign.kt") }
    private val game by lazy { source("$main/bot/Game.kt") }
    private val dialogHandler by lazy { source("$main/bot/DialogHandler.kt") }
    private val ledgerSource by lazy { source("$main/QueueReport.kt") }

    private val session by lazy {
        val start = startModule.indexOf("fun onStartEvent(event: StartEvent)")
        val end = startModule.indexOf("\n    /**\n     * Tests the Discord connection", start)
        assertTrue(start in 0 until end, "onStartEvent must exist")
        startModule.substring(start, end)
    }

    private fun after(anchor: String, text: String = session): String {
        val at = text.indexOf(anchor)
        assertTrue(at >= 0, "missing: $anchor")
        return text.substring(at)
    }

    @Nested
    @DisplayName("every halt names its ending")
    inner class Halts {
        @Test
        fun `each queueHaltReason assignment records its ending right beside it`() {
            val sites = Regex("queueHaltReason = \"").findAll(session).map { it.range.first }.toList()
            // Thirteen: the ten halts, a launch whose trainee cannot start with no rotation (LAUNCH_FAILED_BEFORE_RUN),
            // and a skip that cannot go on (the game not back on Home, or the next trainee's setup missing).
            assertEquals(13, sites.size, "the halt sites")
            val ends =
                sites.map { site ->
                    val block = session.substring(site, session.indexOf('\n', session.indexOf('\n', site) + 1))
                    val m = Regex("ledger\\.haltEnd = SessionEnd\\.(\\w+)").find(block)
                    assertNotNull(m, "the halt at ${block.lineSequence().first().trim()} must set ledger.haltEnd on the next line")
                    m!!.groupValues[1]
                }
            assertEquals(
                setOf(
                    "FIRST_SNAPSHOT_MISSING",
                    "LAUNCH_FAILED_BEFORE_RUN",
                    "BREAKPOINT",
                    "GAME_UNRECOVERABLE",
                    "RUN_HALTED",
                    "STOP_ON_ERROR",
                    "NEXT_SNAPSHOT_MISSING",
                    "NAVIGATION_FAILED_BETWEEN_RUNS",
                    "WAIT_INTERRUPTED",
                ),
                ends.toSet(),
            )
            assertEquals(9, ends.toSet().size, "each halt site is a different ending")
        }

        @Test
        fun `both navigation halts carry the navigation's reason key`() {
            // Every site of both endings names its reason on the next line: a navigation's own key, the key of
            // the run's own launch that stopped, or the screen a skipped run could not be left from.
            val reasons = setOf("ledger.reasonKey = navResult.reasonKey", "ledger.reasonKey = launchStop.reasonKey", "ledger.reasonKey = \"STUCK_ON_SCREEN\"")
            for (end in listOf("LAUNCH_FAILED_BEFORE_RUN", "NAVIGATION_FAILED_BETWEEN_RUNS")) {
                val sites = Regex(Regex.escape("ledger.haltEnd = SessionEnd.$end")).findAll(session).toList()
                assertTrue(sites.isNotEmpty(), end)
                for (site in sites) {
                    val next = session.substring(site.range.last).lineSequence().drop(1).first().trim()
                    assertTrue(next in reasons, "$end: $next")
                }
                assertTrue(sites.any { session.substring(it.range.last).lineSequence().drop(1).first().trim() == "ledger.reasonKey = navResult.reasonKey" }, "$end: a navigation site")
            }
        }

        @Test
        fun `the halt bookkeeping is handed over after the loop, before the queue's own halt branch`() {
            val loop = session.indexOf("for (i in startFromRun..totalRuns) {")
            val copy = session.indexOf("ledger.haltCareerInFlight = queueHaltCareerInFlight")
            val halt = session.indexOf("val halt = queueHaltReason")
            assertTrue(copy in loop until halt, "loop $loop, copy $copy, halt branch $halt")
            for (field in listOf("ledger.completedRuns = completedRuns", "ledger.haltRun = queueHaltRun", "ledger.breakpointDetail = queueHaltDetail")) {
                assertTrue(session.substring(copy - 200, halt).contains(field), field)
            }
        }
    }

    @Nested
    @DisplayName("every early exit is recorded before it returns")
    inner class EarlyExits {
        private fun flagBeforeReturn(flag: String) {
            val at = session.indexOf("$flag = true")
            assertTrue(at >= 0, "missing $flag")
            val next = session.indexOf("return", at)
            val between = session.substring(at, next)
            assertFalse(between.contains("}"), "$flag must be set immediately before its return")
        }

        @Test
        fun `a refused launch identity`() {
            val dispatch = session.indexOf("val launchSelection = dispatchDiagnostic(::readLaunchSnapshot)")
            val returned = session.indexOf("ledger.dispatchReturned = true")
            assertTrue(returned > dispatch && session.substring(dispatch, returned).count { it == '\n' } == 1, "dispatchReturned directly follows dispatch")
            flagBeforeReturn("ledger.launchIdentityRefused")
        }

        @Test
        fun `a diagnostic, an unprepared rotation and nothing to resume`() {
            flagBeforeReturn("ledger.diagnosticRan")
            flagBeforeReturn("ledger.rotationNotPrepared")
            val resume = session.indexOf("ledger.nothingToResume = true")
            assertTrue(resume >= 0 && session.indexOf("return@run totalRuns + 1", resume) - resume < 80)
        }

        @Test
        fun `a refused start and an escaped exception are recorded and rethrown`() {
            val catchAt = session.indexOf("} catch (e: Throwable) {")
            val finallyAt = session.indexOf("} finally {", catchAt)
            assertTrue(catchAt in 0 until finallyAt, "the session catch must sit right before its finally")
            val body = session.substring(catchAt, finallyAt)
            assertTrue(
                body.contains(
                    "if (!ledger.dispatchReturned && isLaunchGateRefusal(e, launchSnapshotReadStarted, launchSnapshotReadFinished)) " +
                        "ledger.launchRefused = true else ledger.unexpectedError = true",
                ),
                "only the gate's own refusals before dispatch returned are a refused launch",
            )
            assertTrue(body.trimEnd().endsWith("throw e"), "the exception must propagate unchanged")
        }

        @Test
        fun `the snapshot read is marked when it starts and when it returns, and both marks reset with the session flags`() {
            val read = after("private fun readLaunchSnapshot(): Map<String, String> {", startModule).substringBefore("\n    }\n")
            val lines = read.lines().map { it.trim() }.filter { it.isNotEmpty() }
            assertEquals("launchSnapshotReadStarted = true", lines[1], "the start mark must be the read's first statement")
            assertEquals(listOf("launchSnapshotReadFinished = true", "return rows"), lines.takeLast(2), "the finished mark must be set right before the read returns")
            val dispatch = session.indexOf("val launchSelection = dispatchDiagnostic(")
            for (reset in listOf("launchSnapshotReadStarted = false", "launchSnapshotReadFinished = false")) {
                assertTrue(session.indexOf(reset) in 0 until dispatch, "'$reset' must run before dispatch")
            }
        }
    }

    @Nested
    @DisplayName("the report is written on every exit")
    inner class Writer {
        private val finallyBlock by lazy { after("} finally {\n                // Always release the wake lock") }

        @Test
        fun `the finally writes the report before the session is released and the app is told`() {
            val write = finallyBlock.indexOf("writeSessionReport(ledger)")
            val release = finallyBlock.indexOf("sessionActive.set(false)")
            val jsEvent = finallyBlock.indexOf("enqueueJsEvent(JSEvent(\"BotService\", \"Not Running\", false))")
            assertTrue(write in 0 until release, "write $write, release $release")
            assertTrue(release < jsEvent)
            assertTrue(finallyBlock.indexOf("ledgerHeartbeat?.interrupt()") in 0 until write)
        }

        @Test
        fun `the writer classifies and hands the report to the ledger store`() {
            val writer = after("private fun writeSessionReport(ledger: SessionLedger): QueueReport? {", startModule).substringBefore("\n    }\n")
            val classify = writer.indexOf("classifySessionEnd(facts)")
            val store = writer.indexOf("ledger.report(verdict, System.currentTimeMillis()).also { QueueLedger.finishSession(context, it) }")
            assertTrue(classify in 0 until store)
            assertTrue(writer.contains("queueStateActive = loadQueueState(context) != null"))
            assertTrue(writer.contains("stopByBot = queueStopReason != null"))
            assertTrue(writer.contains("if (verdict.end == SessionEnd.STOPPED_BY_BOT) ledger.reasonKey = queueStopKey.orEmpty()"))
        }

        @Test
        fun `the writer cannot latch the session, not even on an Error`() {
            val writer = after("private fun writeSessionReport(ledger: SessionLedger): QueueReport? {", startModule).substringBefore("\n    }\n")
            assertTrue(writer.contains("} catch (e: Throwable) {"), "an Error escaping here would skip the latch release")
        }

        @Test
        fun `the ledger object exists before the try, so catch and finally can read it`() {
            val ledger = session.indexOf("val ledger = SessionLedger(")
            val tryAt = session.indexOf("try {", ledger)
            assertTrue(ledger >= 0 && session.substring(ledger, tryAt).count { it == '\n' } <= 2)
        }

        @Test
        fun `the store writes the same record to the app report and the history file`() {
            val store = after("private fun store(context: Context, report: QueueReport) {", ledgerSource).substringBefore("\n    }\n")
            assertTrue(store.contains("KEY_LAST_REPORT to report.lastReportValue()"))
            assertTrue(store.contains("OutcomeCorpus.append(context, report.toJson(), OutcomeCorpus.QUEUE_LEDGER_PATH)"))
        }

        @Test
        fun `every settings write is one transaction`() {
            val write = after("private fun write(context: Context, values: Map<String, String>, delete: List<String> = emptyList()) {", ledgerSource).substringBefore("\n    }\n")
            val begin = write.indexOf("db.beginTransaction()")
            val success = write.indexOf("db.setTransactionSuccessful()")
            val end = write.indexOf("db.endTransaction()")
            assertTrue(begin in 0 until success && success < end, "begin $begin, success $success, end $end")
            assertTrue(write.substring(begin, success).contains("INSERT OR REPLACE") && write.substring(begin, success).contains("DELETE FROM"))
            assertTrue(write.substring(success, end).contains("} finally {"), "the transaction must end even when a statement throws")
        }

        @Test
        fun `the heartbeat never opens settings db`() {
            val markAlive = after("fun markAlive(context: Context, sessionId: String) {", ledgerSource).substringBefore("\n    }\n")
            val locked = after("private fun markAliveLocked(context: Context, sessionId: String) {", ledgerSource).substringBefore("\n    }\n")
            assertTrue(markAlive.contains("markAliveLocked(context, sessionId)"))
            assertTrue(locked.contains("writeHeartbeat(context.filesDir, sessionId,"))
            for (body in listOf(markAlive, locked)) assertFalse(body.contains("write(context") || body.contains("SQLiteDatabase"), body)
            assertFalse(ledgerSource.contains("openSessionSeenAt"), "no heartbeat key left in settings.db")
            assertTrue(startModule.contains("QueueLedger.markAlive(context, sessionId)"), "the heartbeat thread goes through markAlive")
        }

        @Test
        fun `the heartbeat is replaced atomically, never written in place`() {
            val write = after("internal fun writeHeartbeat(dir: File, sessionId: String, now: Long): Boolean {", ledgerSource).substringBefore("\n}\n")
            val tempWrite = write.indexOf("temp.writeText(")
            val rename = write.indexOf("temp.renameTo(target)")
            assertTrue(tempWrite in 0 until rename, "write the temp file, then rename it over the target")
            assertFalse(write.contains("target.writeText("), "the target must never be written in place")
        }

        @Test
        fun `a finished or dead session's heartbeat is removed, and death detection reads it from the file`() {
            val store = after("private fun store(context: Context, report: QueueReport) {", ledgerSource).substringBefore("\n    }\n")
            assertTrue(store.contains("delete = listOf(KEY_OPEN_SESSION)"))
            assertTrue(store.contains("File(context.filesDir, HEARTBEAT_FILE).delete()"))
            val detect = after("private fun reportDeadSessionLocked(context: Context) {", ledgerSource).substringBefore("\n    }\n")
            assertTrue(detect.contains("val seen = readHeartbeat(context.filesDir, sessionId)"))
        }

        @Test
        fun `finishing reports any older dead session first`() {
            val finish = after("internal fun finishSession(context: Context, report: QueueReport) {", ledgerSource).substringBefore("\n    }\n")
            assertTrue(finish.indexOf("reportDeadSessionLocked(context)") in 0 until finish.indexOf("store(context, report)"))
        }

        @Test
        fun `the bridge reports a dead session before returning the report`() {
            val read = after("fun lastReport(context: Context): String? {", ledgerSource).substringBefore("\n    }\n")
            assertTrue(read.indexOf("reportDeadSessionLocked(context)") in 0 until read.indexOf("read(context, listOf(KEY_LAST_REPORT))"))
            assertTrue(startModule.contains("promise.resolve(lastReportPayload(QueueLedger.lastReport(context)))"))
            assertTrue(startModule.contains("promise.resolve(QueueLedger.dismissLastReport(context, sessionId))"))
        }
    }

    @Nested
    @DisplayName("open session and per-run facts")
    inner class PerRun {
        @Test
        fun `a lost session is reported after the diagnostic branch, before the resume record is read`() {
            val diagnosticReturn = session.indexOf("ledger.diagnosticRan = true")
            val report = session.indexOf("QueueLedger.reportDeadSession(context)")
            val resumeRead = session.indexOf("val saved = loadQueueState(context)")
            assertTrue(report in diagnosticReturn until resumeRead, "diagnostic $diagnosticReturn, report $report, resume read $resumeRead")
        }

        @Test
        fun `the session opens after the resume decision, before the first launch`() {
            val resume = session.indexOf("var completedRuns = priorCompletedRuns")
            val open = session.indexOf("QueueLedger.beginSession(context, ledger.sessionId, ledger.openJson())")
            val coldStart = session.indexOf("val navResult = navigateWithDeadline(coldStartReuse, coldStartNavigator, coldStartOnHome = !resumeReEntersCareer, careerInFlight = resumeReEntersCareer)")
            assertTrue(open in resume until coldStart)
            assertTrue(session.indexOf("ledgerHeartbeat = startLedgerHeartbeat(ledger.sessionId)", open) in open until coldStart)
        }

        @Test
        fun `each run refreshes the open record and remembers the stash sequence before it plays`() {
            val refresh = session.indexOf("ledger.currentRun = i")
            val seq = session.indexOf("val careerEndSeqBeforeRun = lastCareerEndSeq")
            val run = session.indexOf("var result = runSingleGame()")
            assertTrue(refresh in 0 until run && seq in refresh until run)
            assertTrue(session.substring(refresh, run).contains("QueueLedger.refreshOpenSession("))
        }

        @Test
        fun `each run is recorded before any branch can leave the loop`() {
            val record = session.indexOf("recordRun(ledger, i, runStartedAt, careerEndSeqBeforeRun, effectiveResult.code, retried)")
            val evaluate = session.indexOf("when (effectiveResult.code) {")
            assertTrue(record in session.indexOf("val effectiveResult =") until evaluate)
            assertFalse(session.substring(session.indexOf("var result = runSingleGame()"), record).contains("break"))
        }

        @Test
        fun `a run takes career facts only through the sequence check`() {
            val recordRun = after("private fun recordRun(", startModule).substringBefore("\n    }\n")
            assertTrue(recordRun.contains("val stash =\n            CareerEndStash(lastCareerEndSeq,"))
            assertTrue(recordRun.contains("val careerEnd = careerEndForRun(careerEndSeqBeforeRun, stash)"))
            val careerFacts = listOf("trainee", "scenario", "outcome", "turn").joinToString("") { "                careerEnd?.$it,\n" }
            assertTrue(recordRun.contains(careerFacts), "the record takes career facts only from the checked stash")
            assertTrue(recordRun.contains("                careerEnd?.result,\n"), "and its result")
        }

        @Test
        fun `the career end bumps the sequence after every stash it writes`() {
            val ledgerLine = after("override fun careerEndLedgerLine(result: TaskResult): String {", campaign).substringBefore("val record =")
            val bump = ledgerLine.indexOf("StartModule.lastCareerEndSeq++")
            assertTrue(bump > 0)
            for (stash in listOf("lastCareerEndTrainee =", "lastCareerEndScenario =", "lastCareerEndFp =", "lastCareerEndOutcome =", "lastCareerEndTurn =", "lastCareerEndResult =")) {
                val at = ledgerLine.indexOf("StartModule.$stash")
                assertTrue(at in 0 until bump, "$stash must be written before the sequence moves")
            }
            assertTrue(ledgerLine.contains("StartModule.lastCareerEndTurn = if (date.dayObserved) date.day else null"))
        }

        @Test
        fun `the launching phase refreshes the open record too`() {
            val launching = after("saveQueueState(context, active = true, currentRun = i, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns)")
            val next = launching.lineSequence().drop(1).take(4).joinToString("\n")
            assertTrue(next.contains("ledger.phase = StartModule.PHASE_LAUNCHING") && next.contains("QueueLedger.refreshOpenSession("), next)
        }
    }

    @Nested
    @DisplayName("keys, tallies and the watchdog")
    inner class KeysAndTallies {
        @Test
        fun `a navigation failure carries its transition's reason key`() {
            val conversion = after("is TransitionResult.Failed -> {", navigator).substringBefore("\n                }\n")
            assertTrue(conversion.contains("reasonKey = transitionResult.reasonKey,"))
        }

        @Test
        fun `the reason keys are the player-safe set`() {
            val literal = Regex("reasonKey = \"(\\w+)\"").findAll(navigator).map { it.groupValues[1] }.toSet()
            // Stuck failures take their key from navigatorStuckKey, whose three outcomes AccessibilityRepairTest pins.
            val stuck = if (navigator.contains("reasonKey = navigatorStuckKey(")) setOf("STUCK_ON_SCREEN", "A11Y_GRANT_MISSING", "A11Y_INPUT_DEAD") else emptySet()
            val keys = literal + stuck
            assertEquals(
                setOf(
                    "CAPTURE_OR_ACCESSIBILITY",
                    "STUCK_ON_SCREEN",
                    "A11Y_GRANT_MISSING",
                    "A11Y_INPUT_DEAD",
                    "UNSPENT_SKILL_POINTS",
                    "SPARKS_NEED_HAND",
                    "TP_EMPTY",
                    "TP_RESTORE_CAP",
                    "TP_NO_RESTORE_ITEM",
                    "TP_CARATS_NOT_ALLOWED",
                    "VETERAN_ROSTER_FULL",
                    "REUSE_OFF",
                    "REQUIRED_DECK",
                    "DECK_INCOMPLETE",
                    "BORROW_NEEDS_HAND",
                    "TRAINEE_IN_DECK",
                    "TRAINEE_NOT_FOUND",
                    "TRAINEE_ONLY_OTHER_OUTFIT",
                    "GAME_UNRECOVERABLE",
                ),
                keys,
            )
            assertTrue(startModule.contains("reasonKey = \"NAVIGATION_TIMEOUT\","))
        }

        @Test
        fun `each bot stop sets its key with its prose`() {
            val nav = after("queueStopKey = \"NAVIGATION_UNRESPONSIVE\"", startModule)
            assertTrue(nav.lineSequence().drop(1).first().contains("queueStopReason = "))
            val mismatch = after("StartModule.queueStopKey = \"TRAINEE_MISMATCH\"", campaign)
            assertTrue(mismatch.lineSequence().drop(1).first().contains("StartModule.queueStopReason ="))
            val reset = session.indexOf("queueStopKey = null")
            assertTrue(reset in 0 until session.indexOf("val launchSelection = dispatchDiagnostic("), "the key resets with the other session flags")
        }

        @Test
        fun `TP restores record their rung and purpose where the counter moves`() {
            assertTrue(navigator.contains("tpRestoresThisSession++\n        SessionTally.recordTpRestore(item.label, purpose)"))
            assertTrue(navigator.contains("SessionTally.recordTpRestore(pendingItem?.label ?: \"unknown\", \"unknown\")"))
            assertTrue(navigator.contains("driveTpRestorePicker(noLocation.x, noLocation.y, purpose = \"reroll\")"))
            assertTrue(navigator.contains("driveTpRestorePicker(noLocation.x, noLocation.y, purpose = \"launch\")"))
        }

        @Test
        fun `each recovery bumps its tally`() {
            assertTrue(after("fun ensureAccessibilityService(", game).substringBefore("\n    }\n").contains("SessionTally.accessibilityRewrites.incrementAndGet()"))
            assertTrue(after("fun forceRebindAccessibilityService(", game).substringBefore("\n    }\n").contains("SessionTally.accessibilityRebinds.incrementAndGet()"))
            assertTrue(after("fun restartGame(", game).substringBefore("\n    }\n").contains("SessionTally.gameRelaunches.incrementAndGet()"))
            assertTrue(campaign.contains("lobbyReentryAttempts++\n                SessionTally.lobbyReentries.incrementAndGet()"))
            assertTrue(dialogHandler.contains("if (decision.attempt == 1) SessionTally.connectionHolds.incrementAndGet()"))
            assertTrue(startModule.indexOf("SessionTally.reset()") in 0 until startModule.indexOf("val launchSelection = dispatchDiagnostic("))
        }

        @Test
        fun `the watchdog is untouched`() {
            // The stall watchdog's thread body ends in its own killProcess; nothing of the ledger may run there.
            val kill = game.indexOf("android.os.Process.killProcess(android.os.Process.myPid())")
            val threadStart = game.lastIndexOf("Thread {", kill)
            assertTrue(threadStart in 0 until kill, "the watchdog thread must end in killProcess")
            val watchdog = game.substring(threadStart, kill)
            assertFalse(watchdog.contains("SessionTally") || watchdog.contains("QueueLedger"), "no ledger code in the watchdog")
        }
    }
}
