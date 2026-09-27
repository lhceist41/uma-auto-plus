package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.RunRecord
import com.steve1316.uma_android_automation.runRecordJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Detect-only progress measurement: gaps without progress, alive-but-no-progress episodes, frozen
 * frames while the bot acts, and the watchdog's view, all feeding the run's ledger record. The
 * measurement is pure; that nothing acts on it is pinned by source guards.
 */
@DisplayName("Progress tracker")
class ProgressTrackerTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"

    private fun source(relative: String) = repoFile(relative).readText().replace("\r\n", "\n")

    private val min = 60_000L

    @Nested
    @DisplayName("gaps and events")
    inner class Gaps {
        @Test
        fun `each event type is counted and closes the gap before it`() {
            val w = ProgressWindow(0L)
            w.progress(ProgressEvent.NAV_NEW_STATE, 10_000L)
            w.progress(ProgressEvent.SCREEN_CHANGE, 25_000L)
            w.progress(ProgressEvent.DATE_CHANGE, 95_000L)
            w.progress(ProgressEvent.DATE_CHANGE, 120_000L)
            w.progress(ProgressEvent.CAREER_END, 130_000L)
            val s = w.snapshot(131_000L)
            assertEquals(70_000L, s.getLong("longestGapMs"))
            assertEquals(listOf(1, 1, 2, 1), listOf("navNewStates", "screenChanges", "dateChanges", "careerEnds").map { s.getInt(it) })
            assertEquals(0, s.getInt("stallsOver5Min"))
        }

        @Test
        fun `a gap of five minutes or more is one alive-but-no-progress episode, and the open gap counts at the end`() {
            val w = ProgressWindow(0L)
            w.progress(ProgressEvent.DATE_CHANGE, 5 * min - 1)
            w.progress(ProgressEvent.DATE_CHANGE, 10 * min - 1)
            w.progress(ProgressEvent.DATE_CHANGE, 11 * min)
            assertEquals(1, w.snapshot(11 * min).getInt("stallsOver5Min"))
            val s = w.snapshot(17 * min)
            assertEquals(2, s.getInt("stallsOver5Min"))
            assertEquals(6 * min, s.getLong("longestGapMs"))
        }

        @Test
        fun `a window with no progress at all reports its whole length`() {
            val s = ProgressWindow(1_000L).snapshot(301_000L)
            assertEquals(300_000L, s.getLong("longestGapMs"))
            assertEquals(1, s.getInt("stallsOver5Min"))
        }
    }

    @Nested
    @DisplayName("frozen frames")
    inner class Frames {
        @Test
        fun `three identical frames after actions are one episode, spanning from the first identical frame`() {
            val w = ProgressWindow(0L)
            w.frame(7L, actedSinceLastSample = true, nowMs = 1_000L)
            w.frame(7L, actedSinceLastSample = true, nowMs = 2_000L)
            w.frame(7L, actedSinceLastSample = true, nowMs = 3_000L)
            assertEquals(0, w.snapshot(3_000L).getInt("frozenEpisodes"))
            w.frame(7L, actedSinceLastSample = true, nowMs = 4_000L)
            w.frame(7L, actedSinceLastSample = true, nowMs = 9_000L)
            val s = w.snapshot(9_000L)
            assertEquals(1, s.getInt("frozenEpisodes"))
            assertEquals(8_000L, s.getLong("longestFrozenMs"))
            assertEquals(5, s.getInt("frameSamples"))
        }

        @Test
        fun `a changed frame ends the run, and the next frozen run is a new episode`() {
            val w = ProgressWindow(0L)
            listOf(1_000L, 2_000L, 3_000L, 4_000L).forEach { w.frame(5L, true, it) }
            w.frame(6L, true, 5_000L)
            listOf(6_000L, 7_000L, 8_000L).forEach { w.frame(6L, true, it) }
            val s = w.snapshot(8_000L)
            assertEquals(2, s.getInt("frozenEpisodes"))
            assertEquals(3_000L, s.getLong("longestFrozenMs"))
        }

        @Test
        fun `identical frames while the bot is idle are not frozen, and do not break a run either`() {
            val idle = ProgressWindow(0L)
            listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L).forEach { idle.frame(9L, false, it) }
            assertEquals(0, idle.snapshot(5_000L).getInt("frozenEpisodes"))
            val held = ProgressWindow(0L)
            held.frame(9L, true, 1_000L)
            held.frame(9L, true, 2_000L)
            held.frame(9L, false, 3_000L)
            held.frame(9L, true, 4_000L)
            held.frame(9L, true, 5_000L)
            assertEquals(1, held.snapshot(5_000L).getInt("frozenEpisodes"))
        }

        @Test
        fun `the frame hash reads a 32 by 18 luminance grid, and one sampled pixel changes it`() {
            val reads = mutableListOf<Pair<Int, Int>>()
            val base =
                frameHash(1080, 1920) { x, y ->
                    reads += x to y
                    0xFF102030.toInt()
                }
            assertEquals(FRAME_GRID_W * FRAME_GRID_H, reads.size)
            assertEquals(FRAME_GRID_W * FRAME_GRID_H, reads.toSet().size)
            assertTrue(reads.all { (x, y) -> x in 0 until 1080 && y in 0 until 1920 })
            assertEquals(base, frameHash(1080, 1920) { _, _ -> 0xFF102030.toInt() })
            val (cx, cy) = reads[100]
            assertNotEquals(base, frameHash(1080, 1920) { x, y -> if (x == cx && y == cy) 0xFFFFFFFF.toInt() else 0xFF102030.toInt() })
            assertEquals(base, frameHash(1080, 1920) { _, _ -> 0x00102030 }, "alpha does not count")
        }
    }

    @Nested
    @DisplayName("the process-wide tracker")
    inner class Tracker {
        private fun withClock(block: (advance: (Long) -> Unit) -> Unit) {
            val real = ProgressTracker.clock
            var now = 1_000_000L
            try {
                ProgressTracker.clock = { now }
                ProgressTracker.beginWindow()
                block { now += it }
            } finally {
                ProgressTracker.clock = real
                ProgressTracker.beginWindow()
            }
        }

        @Test
        fun `a window collects progress, frozen frames after taps and watchdog rungs, then starts over`() {
            withClock { advance ->
                advance(30_000L)
                ProgressTracker.noteProgress(ProgressEvent.DATE_CHANGE)
                repeat(4) {
                    ProgressTracker.noteAction()
                    advance(1_000L)
                    ProgressTracker.noteFrame(42L)
                }
                advance(150_000L)
                ProgressTracker.noteWatchdogRung()
                advance(30_000L)
                ProgressTracker.noteWatchdogRung()
                val s = ProgressTracker.endWindow()
                assertEquals(1, s.getInt("dateChanges"))
                assertEquals(1, s.getInt("frozenEpisodes"))
                assertEquals(2, s.getInt("watchdogRungs"))
                assertEquals(184_000L, s.getLong("watchdogRungMaxAgeMs"))
                assertEquals(184_000L, s.getLong("longestGapMs"))
                val next = ProgressTracker.endWindow()
                assertEquals(listOf(0, 0, 0), listOf(next.getInt("dateChanges"), next.getInt("watchdogRungs"), next.getInt("frameSamples")))
                assertEquals(0L, next.getLong("longestGapMs"))
            }
        }

        @Test
        fun `frames with no tap in between never count as frozen`() {
            withClock { advance ->
                repeat(6) {
                    advance(1_000L)
                    ProgressTracker.noteFrame(42L)
                }
                assertEquals(0, ProgressTracker.endWindow().getInt("frozenEpisodes"))
            }
        }
    }

    @Nested
    @DisplayName("the ledger")
    inner class Ledger {
        @Test
        fun `a run record carries its window's fields, keys and numbers only, and omits them when there are none`() {
            val fields = ProgressWindow(0L).snapshot(1_000L).put("watchdogRungs", 0).put("watchdogRungMaxAgeMs", 0L)
            val json = runRecordJson(RunRecord(1, 0L, 1L, "TASK_RESULT_COMPLETE", null, null, null, null, false, fields))
            val progress = json.getJSONObject("progress")
            val keys = progress.keys().asSequence().toSet()
            assertEquals(
                setOf(
                    "longestGapMs",
                    "stallsOver5Min",
                    "frameSamples",
                    "frozenEpisodes",
                    "longestFrozenMs",
                    "dateChanges",
                    "navNewStates",
                    "screenChanges",
                    "careerEnds",
                    "watchdogRungs",
                    "watchdogRungMaxAgeMs",
                ),
                keys,
            )
            assertTrue(keys.all { progress.get(it) is Number }, "numbers only")
            assertFalse(runRecordJson(RunRecord(1, 0L, 1L, "TASK_RESULT_COMPLETE", null, null, null, null)).has("progress"))
        }

        @Test
        fun `each recorded run closes its window, and a session starts a fresh one`() {
            val start = source("$main/StartModule.kt")
            assertTrue(start.contains("val progress = ProgressTracker.endWindow()\n        ledger.addRun(\n            RunRecord("))
            assertTrue(start.contains("careerEnd?.turn, retried, progress, careerEnd?.traineeName),"))
            assertTrue(start.contains("SessionTally.reset()\n                ProgressTracker.beginWindow()"))
        }
    }

    @Nested
    @DisplayName("detect-only and thread rules")
    inner class Guards {
        private val allowed =
            listOf(
                Regex("^(if \\([^)]*\\) )?ProgressTracker\\.noteProgress\\(ProgressEvent\\.\\w+\\)$"),
                Regex("^ProgressTracker\\.noteAction\\(\\)$"),
                Regex("^ProgressTracker\\.beginWindow\\(\\)$"),
                Regex(
                    "^if \\(rung == WatchdogRung\\.TOGGLE_ACCESSIBILITY \\|\\| rung == WatchdogRung\\.SKIP_TOGGLE \\|\\| " +
                        "rung == WatchdogRung\\.INTERRUPT_GAME_THREAD\\) ProgressTracker\\.noteWatchdogRung\\(\\)$",
                ),
                Regex("^override fun getSourceBitmap\\(saveImage: Boolean\\): Bitmap = super\\.getSourceBitmap\\(saveImage\\)\\.also \\{ ProgressTracker\\.noteCapture\\(it\\) \\}$"),
                Regex("^val progress = ProgressTracker\\.endWindow\\(\\)$"),
            )

        @Test
        fun `every use of the tracker is a bare recording statement, so no decision depends on it`() {
            val root = repoFile("$main/StartModule.kt").parentFile
            val files = root.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") && it.name != "ProgressTracker.kt" }.toList()
            val lines = files.flatMap { file -> file.readText().replace("\r\n", "\n").lines().map { file.name to it.trim() } }
            // A member, wildcard or aliased import would let a decision read the tracker without naming it.
            val sideDoor = Regex("\\b(ProgressTracker|ProgressEvent|ProgressWindow)(\\.(\\w+|\\*)$| as )|\\bframeHash\\b")
            assertEquals(emptyList<Pair<String, String>>(), lines.filter { (_, l) -> l.startsWith("import ") && sideDoor.containsMatchIn(l) })
            val uses =
                lines.filter { (_, l) -> !l.startsWith("//") && !l.startsWith("*") && !l.startsWith("/**") && !l.startsWith("import ") }
                    .filter { (_, l) -> Regex("\\b(ProgressTracker|ProgressEvent|ProgressWindow)\\b|\\bframeHash\\(").containsMatchIn(l) }
            assertEquals(11, uses.size, uses.joinToString("\n"))
            for ((file, line) in uses) assertTrue(allowed.any { it.matches(line) }, "$file: $line")
        }

        @Test
        fun `the hooks sit where the events happen`() {
            val date = source("$main/types/GameDate.kt")
            assertTrue(date.contains("if (day != dayBefore) ProgressTracker.noteProgress(ProgressEvent.DATE_CHANGE)\n        return true"))
            val campaign = source("$main/bot/Campaign.kt")
            assertTrue(campaign.contains("StartModule.lastCareerEndSeq++\n        if (outcome != \"INCOMPLETE\") ProgressTracker.noteProgress(ProgressEvent.CAREER_END)"))
            val nav = source("$main/CareerLaunchNavigator.kt")
            val newState = "if (seenStates.add(detectedState)) {\n                    iterationsWithoutProgress = 0\n                    ProgressTracker.noteProgress(ProgressEvent.NAV_NEW_STATE)"
            assertTrue(nav.contains(newState))
            assertTrue(nav.contains("if (detectedState != currentState) ProgressTracker.noteProgress(ProgressEvent.SCREEN_CHANGE)\n                currentState = detectedState"))
            assertTrue(source("$main/bot/Game.kt").contains("gestureUtils.tap(x, y, imageName, taps = taps)\n        ProgressTracker.noteAction()"))
            assertTrue(source("$main/components/Components.kt").contains("MyAccessibilityService.getInstance().tap(x, y, imageName, taps = taps)\n        ProgressTracker.noteAction()"))
            assertTrue(source("$main/bot/CoordinateTap.kt").contains("service.tap(jx.toDouble(), jy.toDouble(), null, taps = taps)\n        ProgressTracker.noteAction()"))
        }

        @Test
        fun `the tracker and the capture path take no MessageLog, no ocrLock, and the watchdog's call takes no lock at all`() {
            val tracker =
                source("$main/utils/ProgressTracker.kt").lines()
                    .filterNot { it.trim().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/**") } }
                    .joinToString("\n")
            for (banned in listOf("MessageLog", "ocrLock", "SettingsHelper", "Log.", "Thread.sleep", "wait(")) assertFalse(tracker.contains(banned), banned)
            val rung = tracker.substring(tracker.indexOf("fun noteWatchdogRung()"), tracker.indexOf("fun endWindow()"))
            assertFalse(rung.contains("synchronized"))
            val capture = tracker.substring(tracker.indexOf("fun noteCapture("), tracker.indexOf("internal fun noteFrame("))
            assertTrue(capture.contains("if (captures.incrementAndGet() % SAMPLE_EVERY != 0) return"))
            assertTrue(capture.contains("} catch (_: Throwable) {"))
            val watchdog = source("$main/bot/Game.kt")
            val loop = watchdog.substring(watchdog.indexOf("private fun startWatchdog("), watchdog.indexOf("// --- WakeLock ---"))
            assertTrue(loop.indexOf("ProgressTracker.noteWatchdogRung()") > loop.indexOf("return@launch"), "after every rung branch, never inside one")
        }
    }
}
