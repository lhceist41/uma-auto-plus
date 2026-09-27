package com.steve1316.uma_android_automation.bot

import android.app.ApplicationExitInfo
import com.steve1316.uma_android_automation.ExitRecord
import com.steve1316.uma_android_automation.exitInfoJson
import com.steve1316.uma_android_automation.processEndedReport
import com.steve1316.uma_android_automation.queueReportText
import com.steve1316.uma_android_automation.watchdogBreadcrumbFor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The stall watchdog's recovery ladder: an accessibility toggle, then an interrupt of the Game
 * thread, then the unchanged kill. The decisions are pure; the watchdog loop in Game.kt and its
 * section 6 rules (act first, android.util.Log only, no MessageLog, Game.wait or SettingsHelper in a
 * rung) are pinned by source guards, since a stall cannot be staged on the JVM.
 */
@DisplayName("Stall watchdog ladder")
class StallWatchdogTest {
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

    private fun body(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue(start >= 0, "$signature not found")
        val end = Regex("\n    (/\\*\\*|// |(private |internal )?fun )").find(src, start + signature.length)?.range?.first ?: src.length
        return src.substring(start, end)
    }

    /** One watchdog tick every 5 s from a stall's start, the way Game.startWatchdog applies each decision. */
    private fun stall(
        grant: Boolean = true,
        gameThreadAlive: (Long, Int) -> Boolean = { _, _ -> true },
        heartbeatAt: Long? = null,
    ): List<Pair<Long, WatchdogRung>> {
        val taken = mutableListOf<Pair<Long, WatchdogRung>>()
        var rungsDone = 0
        var lastBeat = 0L
        var now = 0L
        while (now < 400_000L) {
            now += 5_000L
            if (heartbeatAt != null && now == heartbeatAt) lastBeat = now
            val rung = decideWatchdogRung(now - lastBeat, rungsDone, gameThreadAlive(now, rungsDone), grant)
            rungsDone = rungsDoneAfter(rung, rungsDone)
            if (rung != WatchdogRung.NONE) taken += (now - lastBeat) to rung
            if (rung == WatchdogRung.KILL || rung == WatchdogRung.RECOVERED) return taken
            if (rung == WatchdogRung.RESET) return taken
        }
        return taken
    }

    @Nested
    @DisplayName("a forced stall")
    inner class ForcedStall {
        @Test
        fun `takes the toggle, then the interrupt, then kills a thread that is still alive and stale`() {
            assertEquals(
                listOf(120_000L to WatchdogRung.TOGGLE_ACCESSIBILITY, 150_000L to WatchdogRung.INTERRUPT_GAME_THREAD, 180_000L to WatchdogRung.KILL),
                stall(),
            )
        }

        @Test
        fun `without the grant the toggle is skipped and the ladder goes on`() {
            assertEquals(
                listOf(120_000L to WatchdogRung.SKIP_TOGGLE, 150_000L to WatchdogRung.INTERRUPT_GAME_THREAD, 180_000L to WatchdogRung.KILL),
                stall(grant = false),
            )
        }

        @Test
        fun `a Game thread that exits after the interrupt is a recovery, never a kill`() {
            val taken = stall(gameThreadAlive = { now, rungsDone -> !(rungsDone >= 2 && now >= 160_000L) })
            assertEquals(listOf(WatchdogRung.TOGGLE_ACCESSIBILITY, WatchdogRung.INTERRUPT_GAME_THREAD, WatchdogRung.RECOVERED), taken.map { it.second })
        }

        @Test
        fun `a heartbeat after a rung ends the stall without a kill`() {
            val taken = stall(heartbeatAt = 130_000L)
            assertEquals(listOf(WatchdogRung.TOGGLE_ACCESSIBILITY, WatchdogRung.RESET), taken.map { it.second })
        }

        @Test
        fun `a stall with no Game thread (between runs) is not interrupted and still ends in the kill`() {
            assertEquals(listOf(120_000L to WatchdogRung.TOGGLE_ACCESSIBILITY, 180_000L to WatchdogRung.KILL), stall(gameThreadAlive = { _, _ -> false }))
        }

        @Test
        fun `a watchdog that missed the early ticks still takes the rungs in order before the kill`() {
            var rungsDone = 0
            val taken =
                listOf(200_000L, 205_000L, 210_000L).map { age ->
                    decideWatchdogRung(age, rungsDone, true, true).also { rungsDone = rungsDoneAfter(it, rungsDone) }
                }
            assertEquals(listOf(WatchdogRung.TOGGLE_ACCESSIBILITY, WatchdogRung.INTERRUPT_GAME_THREAD, WatchdogRung.KILL), taken)
        }
    }

    @Nested
    @DisplayName("each decision")
    inner class Decisions {
        @Test
        fun `nothing happens before the first rung, and a fresh heartbeat resets a started ladder`() {
            assertEquals(WatchdogRung.NONE, decideWatchdogRung(WATCHDOG_TOGGLE_AT_MS - 1, 0, true, true))
            assertEquals(WatchdogRung.RESET, decideWatchdogRung(WATCHDOG_TOGGLE_AT_MS - 1, 1, true, true))
            assertEquals(WatchdogRung.RESET, decideWatchdogRung(0L, 2, true, true))
        }

        @Test
        fun `the first rung toggles with the grant and is skipped without it`() {
            assertEquals(WatchdogRung.TOGGLE_ACCESSIBILITY, decideWatchdogRung(WATCHDOG_TOGGLE_AT_MS, 0, true, true))
            assertEquals(WatchdogRung.SKIP_TOGGLE, decideWatchdogRung(WATCHDOG_TOGGLE_AT_MS, 0, true, false))
            assertEquals(WatchdogRung.TOGGLE_ACCESSIBILITY, decideWatchdogRung(WATCHDOG_KILL_AT_MS, 0, true, true))
        }

        @Test
        fun `the interrupt waits for its threshold and a live Game thread`() {
            assertEquals(WatchdogRung.NONE, decideWatchdogRung(WATCHDOG_INTERRUPT_AT_MS - 1, 1, true, true))
            assertEquals(WatchdogRung.INTERRUPT_GAME_THREAD, decideWatchdogRung(WATCHDOG_INTERRUPT_AT_MS, 1, true, true))
            assertEquals(WatchdogRung.NONE, decideWatchdogRung(WATCHDOG_INTERRUPT_AT_MS, 1, false, true))
            assertEquals(WatchdogRung.INTERRUPT_GAME_THREAD, decideWatchdogRung(WATCHDOG_KILL_AT_MS, 1, true, true))
        }

        @Test
        fun `the kill comes at its threshold, for a live interrupted thread or when there was none to interrupt`() {
            assertEquals(WatchdogRung.NONE, decideWatchdogRung(WATCHDOG_KILL_AT_MS - 1, 2, true, true))
            assertEquals(WatchdogRung.KILL, decideWatchdogRung(WATCHDOG_KILL_AT_MS, 2, true, true))
            assertEquals(WatchdogRung.NONE, decideWatchdogRung(WATCHDOG_KILL_AT_MS - 1, 1, false, true))
            assertEquals(WatchdogRung.KILL, decideWatchdogRung(WATCHDOG_KILL_AT_MS, 1, false, false))
        }

        @Test
        fun `an interrupted Game thread that exited is a recovery at any age`() {
            for (age in listOf(0L, WATCHDOG_INTERRUPT_AT_MS, WATCHDOG_KILL_AT_MS, 10 * WATCHDOG_KILL_AT_MS)) {
                assertEquals(WatchdogRung.RECOVERED, decideWatchdogRung(age, 2, false, true), "$age")
            }
        }

        @Test
        fun `the rung count follows each step`() {
            assertEquals(0, rungsDoneAfter(WatchdogRung.RESET, 2))
            assertEquals(0, rungsDoneAfter(WatchdogRung.RECOVERED, 2))
            assertEquals(1, rungsDoneAfter(WatchdogRung.TOGGLE_ACCESSIBILITY, 0))
            assertEquals(1, rungsDoneAfter(WatchdogRung.SKIP_TOGGLE, 0))
            assertEquals(2, rungsDoneAfter(WatchdogRung.INTERRUPT_GAME_THREAD, 1))
            assertEquals(1, rungsDoneAfter(WatchdogRung.NONE, 1))
            assertEquals(2, rungsDoneAfter(WatchdogRung.KILL, 2))
        }

        @Test
        fun `the thresholds climb 120, 150, 180 seconds`() {
            assertEquals(listOf(120_000L, 150_000L, 180_000L), listOf(WATCHDOG_TOGGLE_AT_MS, WATCHDOG_INTERRUPT_AT_MS, WATCHDOG_KILL_AT_MS))
        }
    }

    @Nested
    @DisplayName("zombies and the interrupted run's reason")
    inner class Zombies {
        @Test
        fun `a run that is no longer current is stale, and a Game that never ran never is`() {
            val first = GameGeneration.claim()
            assertFalse(GameGeneration.isStale(first))
            val second = GameGeneration.claim()
            assertTrue(GameGeneration.isStale(first))
            assertFalse(GameGeneration.isStale(second))
            assertFalse(GameGeneration.isStale(0))
        }

        @Test
        fun `the watchdog's reason is taken once and cleared by a new run`() {
            WatchdogReason.set(watchdogInterruptReason(152_300L))
            assertEquals("No progress for 152 seconds, so the stall watchdog interrupted the run.", WatchdogReason.take())
            assertNull(WatchdogReason.take())
            WatchdogReason.set("x")
            WatchdogReason.clear()
            assertNull(WatchdogReason.take())
        }
    }

    @Nested
    @DisplayName("breadcrumbs and the report")
    inner class Breadcrumbs {
        @Test
        fun `a rung's breadcrumb round-trips and fits the process-state summary`() {
            for (rung in listOf(WatchdogRung.TOGGLE_ACCESSIBILITY, WatchdogRung.SKIP_TOGGLE, WatchdogRung.INTERRUPT_GAME_THREAD)) {
                val text = encodeWatchdogBreadcrumb(rung, Long.MAX_VALUE)
                assertTrue(text.toByteArray(Charsets.US_ASCII).size <= 128, text)
                assertEquals(WatchdogBreadcrumb(rung.name), decodeWatchdogBreadcrumb(encodeWatchdogBreadcrumb(rung, 151_000L)))
            }
        }

        @Test
        fun `a cleared, foreign or damaged summary is no breadcrumb`() {
            val notBreadcrumbs =
                listOf(
                    WATCHDOG_BREADCRUMB_CLEARED,
                    null,
                    "",
                    "uma-watchdog:KILL:180",
                    "uma-watchdog:INTERRUPT_GAME_THREAD",
                    "other:INTERRUPT_GAME_THREAD:150",
                    "uma-watchdog:INTERRUPT_GAME_THREAD:x",
                )
            for (text in notBreadcrumbs) {
                assertNull(decodeWatchdogBreadcrumb(text), "$text")
            }
        }

        @Test
        fun `the breadcrumb file is read only for the dead process and its session`(
            @TempDir dir: File,
        ) {
            val text = encodeWatchdogBreadcrumb(WatchdogRung.INTERRUPT_GAME_THREAD, 150_000L)
            assertTrue(writeWatchdogBreadcrumbFile(dir, 4242, 5_000L, text))
            assertEquals(WatchdogBreadcrumb("INTERRUPT_GAME_THREAD"), readWatchdogBreadcrumbFile(dir, 4242, 5_000L))
            assertNull(readWatchdogBreadcrumbFile(dir, 4243, 5_000L))
            assertNull(readWatchdogBreadcrumbFile(dir, 4242, 5_001L))
            writeWatchdogBreadcrumbFile(dir, 4242, 6_000L, WATCHDOG_BREADCRUMB_CLEARED)
            assertNull(readWatchdogBreadcrumbFile(dir, 4242, 5_000L))
            assertNull(readWatchdogBreadcrumbFile(File(dir, "missing"), 4242, 0L))
        }

        @Test
        fun `the breadcrumb explains only a death by signal, or any death below API 30`() {
            val summary = encodeWatchdogBreadcrumb(WatchdogRung.INTERRUPT_GAME_THREAD, 150_000L)
            val signaled = ExitRecord(1, 2L, ApplicationExitInfo.REASON_SIGNALED, 9, summary)
            assertEquals(WatchdogBreadcrumb("INTERRUPT_GAME_THREAD"), watchdogBreadcrumbFor(signaled, null))
            assertNull(watchdogBreadcrumbFor(signaled.copy(reason = ApplicationExitInfo.REASON_LOW_MEMORY), null))
            assertNull(watchdogBreadcrumbFor(signaled.copy(summary = WATCHDOG_BREADCRUMB_CLEARED), null))
            assertNull(watchdogBreadcrumbFor(signaled.copy(summary = null), WatchdogBreadcrumb("SKIP_TOGGLE")))
            assertEquals(WatchdogBreadcrumb("SKIP_TOGGLE"), watchdogBreadcrumbFor(null, WatchdogBreadcrumb("SKIP_TOGGLE")))
            assertNull(watchdogBreadcrumbFor(null, null))
        }

        @Test
        fun `the exit facts carry the stall next to the exit record`() {
            val exit = ExitRecord(1, 2L, ApplicationExitInfo.REASON_SIGNALED, 9)
            val both = exitInfoJson(exit, WatchdogBreadcrumb("INTERRUPT_GAME_THREAD"))!!
            assertEquals("SIGNALED", both.getString("reason"))
            assertEquals(180L, both.getJSONObject("watchdog").getLong("stalledSeconds"))
            assertEquals("INTERRUPT_GAME_THREAD", both.getJSONObject("watchdog").getString("rung"))
            val fileOnly = exitInfoJson(null, WatchdogBreadcrumb("SKIP_TOGGLE"))!!
            assertFalse(fileOnly.has("reason"))
            assertTrue(fileOnly.has("watchdog"))
            assertFalse(exitInfoJson(exit, null)!!.has("watchdog"))
            assertNull(exitInfoJson(null, null))
        }

        @Test
        fun `the dead session's report says the bot stopped itself`() {
            val open =
                JSONObject().put("sessionId", "s").put("appVersion", "1").put("startedAt", 1L).put("queueEnabled", true)
                    .put("totalRuns", 5).put("startFromRun", 1).put("completedRuns", 2).put("currentRun", 3).put("runs", JSONArray())
            val exit = ExitRecord(1, 2L, ApplicationExitInfo.REASON_SIGNALED, 9, encodeWatchdogBreadcrumb(WatchdogRung.INTERRUPT_GAME_THREAD, 150_000L))
            val report = processEndedReport(open, exit, null, resumable = true, watchdog = watchdogBreadcrumbFor(exit, null)).toJson()
            assertEquals(
                "UMA Auto+ stopped unexpectedly during run 3 of 5. The bot stopped itself after 180 seconds without progress.",
                queueReportText(report).reason,
            )
            val plain = processEndedReport(open, exit.copy(summary = null), null, resumable = true).toJson()
            assertEquals("UMA Auto+ stopped unexpectedly during run 3 of 5.", queueReportText(plain).reason)
        }
    }

    @Nested
    @DisplayName("section 6 wiring")
    inner class Wiring {
        private val game by lazy { source("$main/bot/Game.kt") }
        private val loop by lazy { body(game, "private fun startWatchdog(") }
        private val ladder by lazy { source("$main/bot/StallWatchdog.kt") }

        /** A `when` branch of the watchdog loop; the kill's ends at its `return@launch`. */
        private fun branch(name: String): String {
            val start = loop.indexOf("WatchdogRung.$name -> {")
            assertTrue(start >= 0, name)
            val next = Regex("\n                            WatchdogRung\\.\\w+ -> ").find(loop, start + 1)?.range?.first
            return loop.substring(start, next ?: (loop.indexOf("return@launch", start) + "return@launch".length))
        }

        private fun code(text: String) = text.lines().filterNot { it.trim().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }.joinToString("\n")

        @Test
        fun `no rung reaches MessageLog, Game wait, SettingsHelper or the Game rebind`() {
            val rungs = code(loop.substring(0, loop.indexOf("WatchdogRung.KILL -> {")))
            val banned = Regex("MessageLog|SettingsHelper|\\bwait\\(|forceRebindAccessibilityService|ensureAccessibilityService|gestureUtils")
            assertFalse(banned.containsMatchIn(rungs), banned.find(rungs)?.value)
            // A permission check is a binder call; one that blocked on the watchdog's own thread would hold off the kill.
            assertFalse(Regex("hasSecureSettingsGrant|checkSelfPermission|Settings\\.Secure").containsMatchIn(rungs))
            assertFalse(banned.containsMatchIn(code(ladder)), banned.find(code(ladder))?.value)
            assertFalse(ladder.contains("import com.steve1316.automation_library"))
        }

        @Test
        fun `every rung acts before it logs, and records its breadcrumb last`() {
            val interrupt = branch("INTERRUPT_GAME_THREAD")
            val order = listOf("WatchdogReason.set(", "thread?.interrupt()", "Log.e(", "recordWatchdogBreadcrumb(")
            assertTrue(order.all { interrupt.indexOf(it) >= 0 }, interrupt)
            assertEquals(order.map { interrupt.indexOf(it) }, order.map { interrupt.indexOf(it) }.sorted())
            val toggle = branch("TOGGLE_ACCESSIBILITY")
            assertTrue(toggle.indexOf("runWatchdogRung(") in 0 until toggle.indexOf("toggleAccessibilityForWatchdog(context)"))
            assertTrue(toggle.indexOf("toggleAccessibilityForWatchdog(context)") < toggle.indexOf("Log.e("))
            assertTrue(toggle.indexOf("Log.e(") < toggle.indexOf("recordWatchdogBreadcrumb("))
            val recovered = branch("RECOVERED")
            assertTrue(recovered.indexOf("heartbeat()") in 0 until recovered.indexOf("Log.w("))
            for (name in listOf("RESET", "RECOVERED", "SKIP_TOGGLE")) {
                val b = branch(name)
                assertTrue(b.indexOf("Log.") in 0 until b.indexOf("recordWatchdogBreadcrumb("), name)
            }
            assertTrue(loop.indexOf("rungsDone = rungsDoneAfter(rung, rungsDone)") in 0 until loop.indexOf("when (rung) {"))
        }

        @Test
        fun `the kill is the old kill, reached only through the ladder and never behind a write`() {
            val kill = branch("KILL").lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }
            val expected =
                listOf(
                    "WatchdogRung.KILL -> {",
                    "val msg =",
                    "\"[WATCHDOG] No bot progress for \${age / 1000}s while BotService.isRunning=true. \" +",
                    "\"Likely a stalled gesture injector / input-dispatch freeze. Self-restarting process to recover.\"",
                    "Log.e(TAG, msg)",
                    "try {",
                    "Thread {",
                    "try {",
                    "MessageLog.e(TAG, msg)",
                    "} catch (_: Throwable) {",
                    "}",
                    "}.apply {",
                    "isDaemon = true",
                    "start()",
                    "}",
                    "} catch (_: Throwable) {",
                    "}",
                    "delay(250)",
                    "android.os.Process.killProcess(android.os.Process.myPid())",
                    "return@launch",
                )
            assertEquals(expected, kill)
            assertEquals(1, Regex("killProcess\\(").findAll(game).count())
        }

        @Test
        fun `the run's own thread is watched, and a stale run stops before it taps or beats the heartbeat`() {
            val start = body(game, "    fun start(): TaskResult {")
            assertTrue(start.lines()[1].trim() == "watchRun()")
            val watchRun = body(game, "private fun watchRun(")
            assertTrue(watchRun.contains("gameThread = Thread.currentThread()"))
            assertTrue(watchRun.contains("watchdogGrant = hasSecureSettingsGrant(myContext)"))
            assertTrue(watchRun.contains("WatchdogReason.clear()"))
            assertTrue(watchRun.contains("runGeneration = GameGeneration.claim()"))
            assertFalse(body(game, "    init {").contains("gameThread"), "the navigator's Game never runs a career, so init must not claim the thread")
            val wait = body(game, "fun wait(seconds: Double")
            assertTrue(wait.indexOf("checkCurrentRun()") in 0 until wait.indexOf("heartbeat()"))
            val tap = body(game, "fun tap(x: Double")
            assertTrue(tap.indexOf("checkCurrentRun()") in 0 until tap.indexOf("gestureUtils.tap("))
            assertTrue(game.contains("val gestureUtils: MyAccessibilityService get() = MyAccessibilityService.getInstance()"))
        }

        @Test
        fun `the interrupted run reports the watchdog's reason, and the next start joins the breadcrumb`() {
            val task = body(source("$main/bot/Task.kt"), "private fun interruptResult(")
            assertTrue(task.indexOf("val watchdogReason = WatchdogReason.take()") in 0 until task.indexOf("Thread.interrupted()"))
            assertTrue(task.contains("watchdogReason ?: e.message ?: \"Bot was interrupted by an internal watchdog or safety-net, not by the user.\""))
            val report = source("$main/QueueReport.kt")
            assertTrue(report.contains("watchdog = watchdogBreadcrumbFor(exit, file)"))
            assertTrue(report.contains("it.processStateSummary?.toString(Charsets.US_ASCII)"))
            assertTrue(report.contains("File(context.filesDir, WATCHDOG_BREADCRUMB_FILE).delete()"))
        }
    }
}
