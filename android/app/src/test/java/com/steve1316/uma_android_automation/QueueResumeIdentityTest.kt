package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.StartModule.Companion.PHASE_CAREER
import com.steve1316.uma_android_automation.StartModule.Companion.PHASE_LAUNCHING
import com.steve1316.uma_android_automation.StartModule.Companion.ResumePlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/** A saved queue resume record resumes only the queue that saved it; any other queue clears it and starts at run 1. */
@DisplayName("Queue resume identity")
class QueueResumeIdentityTest {
    private val now = 10_000_000_000L

    private fun saved(identity: String?, phase: String = PHASE_LAUNCHING, currentRun: Int = 1, totalRuns: Int = 2, completedRuns: Int = 1): StartModule.Companion.QueueState {
        val raw =
            mutableMapOf(
                "active" to "true",
                "currentRun" to currentRun.toString(),
                "totalRuns" to totalRuns.toString(),
                "phase" to phase,
                "completedRuns" to completedRuns.toString(),
                "timestamp" to (now - 1_233L * 60_000L).toString(),
            )
        if (identity != null) raw["queueIdentity"] = identity
        return requireNotNull(StartModule.queueStateFrom(raw, now))
    }

    @Nested
    @DisplayName("the decision")
    inner class Decision {
        @Test
        fun `a stopped two-run queue's record is refused by a different queue of the same length`() {
            // A career queue stopped during the run 2 launch, then a different two-run queue.
            val record = saved("settings:228a1af8")
            assertEquals("settings:228a1af8", record.identity)
            // What the old totalRuns-only check let through: run 2, with the earlier queue's career counted as run 1.
            assertEquals(ResumePlan(2, 1), StartModule.resumePlanFor(record.phase, record.currentRun, record.completedRuns))
            val refusal = StartModule.resumeRefusal(record, totalRuns = 2, identity = "settings:47c041d0")
            assertNotNull(refusal)
            assertTrue(refusal!!.contains("the settings or the app version changed"), refusal)
        }

        @Test
        fun `the same queue resumes - after a launch boundary at the next run, mid-career by re-entering the run`() {
            val launching = saved("settings:228a1af8")
            assertNull(StartModule.resumeRefusal(launching, 2, "settings:228a1af8"))
            assertEquals(ResumePlan(2, 1), StartModule.resumePlanFor(launching.phase, launching.currentRun, launching.completedRuns))
            val career = saved("runQueue:a7f6f679", phase = PHASE_CAREER, completedRuns = 0)
            assertNull(StartModule.resumeRefusal(career, 2, "runQueue:a7f6f679"))
            assertEquals(ResumePlan(1, 0), StartModule.resumePlanFor(career.phase, career.currentRun, career.completedRuns))
        }

        @Test
        fun `a record without an identity, from an older build or an empty row, is refused`() {
            for (record in listOf(saved(null), saved(""))) {
                assertNull(record.identity)
                assertNotNull(StartModule.resumeRefusal(record, 2, "settings:228a1af8"))
            }
        }

        @Test
        fun `a rotation identity never equals a non-rotation one, and a different run count is still refused first`() {
            assertNotNull(StartModule.resumeRefusal(saved("runQueue:a7f6f679"), 2, "settings:a7f6f679"))
            val refusal = StartModule.resumeRefusal(saved("settings:228a1af8"), totalRuns = 3, identity = "settings:228a1af8")
            assertTrue(refusal!!.contains("totalRuns"), refusal)
        }
    }

    @Nested
    @DisplayName("the wiring")
    inner class Wiring {
        private fun source(relative: String): String {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(8) {
                if (File(dir, relative).isFile) return File(dir, relative).readText().replace("\r\n", "\n")
                dir = dir?.parentFile
            }
            throw AssertionError("$relative not found from the test working directory")
        }

        private val start by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt") }
        private val session by lazy { start.substring(start.indexOf("fun onStartEvent(")) }

        private fun body(signature: String): String {
            val begin = start.indexOf(signature)
            assertTrue(begin >= 0, signature)
            val open = start.indexOf('{', start.indexOf(")", begin))
            var depth = 0
            for (i in open until start.length) {
                when (start[i]) {
                    '{' -> depth++
                    '}' -> if (--depth == 0) return start.substring(begin, i + 1)
                }
            }
            throw AssertionError("unbalanced: $signature")
        }

        @Test
        fun `every active save stamps the session's identity in one write, and the record reads it back`() {
            val save = body("fun saveQueueState(")
            val write = save.indexOf("arrayOf(\"queueState\", \"queueIdentity\", queueIdentity)")
            assertTrue(write in save.indexOf("if (active) {") until save.indexOf("arrayOf(\"queueState\", \"timestamp\""), "written inside the active branch")
            assertEquals(1, Regex("\"queueIdentity\", queueIdentity").findAll(start).count(), "one writer")
            assertTrue(start.contains("val identity = raw[\"queueIdentity\"]?.takeIf { it.isNotEmpty() }"))
            assertTrue(start.contains("map.putString(\"identity\", saved.identity)"), "Home's banner gets the identity")
        }

        @Test
        fun `the identity comes from the launch snapshot and is set before the record is read or written`() {
            assertTrue(body("private fun readLaunchSnapshot(").contains("launchSnapshotRows = rows"))
            val reset = session.indexOf("launchSnapshotRows = emptyMap()")
            val dispatch = session.indexOf("dispatchDiagnostic(::readLaunchSnapshot)")
            val stamp = session.indexOf("queueIdentity = com.steve1316.uma_android_automation.bot.LaunchIdentityGate.snapshotQueueIdentity(launchSnapshotRows, rotation.enabled)")
            val read = session.indexOf("val saved = loadQueueState(context)")
            val firstSave = session.indexOf("saveQueueState(context, active = true")
            assertTrue(reset in 0 until dispatch && dispatch < stamp && stamp < read && read < firstSave, "reset $reset, dispatch $dispatch, stamp $stamp, read $read, save $firstSave")
        }

        @Test
        fun `a refused record is cleared and the queue starts at run 1 with nothing carried over`() {
            val read = session.indexOf("val saved = loadQueueState(context)")
            val decision = session.substring(read, session.indexOf("val plan = resumePlanFor(", read))
            assertTrue(decision.contains("val refusal = resumeRefusal(saved, totalRuns, queueIdentity)"))
            val refused = decision.substring(decision.indexOf("if (refusal != null) {"))
            assertTrue(refused.contains("clearQueueState(context)\n                            return@run 1"))
            for (carried in listOf("priorCompletedRuns", "resumedQueue", "resumedRotationOffset", "resumeReEntersCareer", "ledger.earlier")) assertFalse(refused.contains(carried), carried)
            assertFalse(session.contains("if (saved.totalRuns != totalRuns)"), "the decision has one gate")
            assertTrue(session.contains("if (resumedQueue) ledger.earlier = "), "earlier runs are merged only into a resumed queue")
        }
    }
}
