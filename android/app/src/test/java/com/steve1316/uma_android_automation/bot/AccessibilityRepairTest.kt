package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.SessionEnd
import com.steve1316.uma_android_automation.SessionEndFacts
import com.steve1316.uma_android_automation.SessionTally
import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.classifySessionEnd
import com.steve1316.uma_android_automation.queueReportText
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Accessibility repairs: the stop reason each stuck-input ladder gives, the ledger counters, the one
 * stronger toggle per run, and the watchdog's monotonic heartbeat. The decisions are pure; the call
 * sites in Campaign, the navigator and Game are pinned by source guards, since dead gesture dispatch
 * cannot be staged on the JVM.
 */
@DisplayName("Accessibility repair")
class AccessibilityRepairTest {
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
        val end = Regex("\n    (/\\*\\*|// |(private |internal |override )?fun )").find(src, start + signature.length)?.range?.first ?: src.length
        return src.substring(start, end)
    }

    private val game by lazy { source("$main/bot/Game.kt") }
    private val campaign by lazy { source("$main/bot/Campaign.kt") }
    private val navigator by lazy { source("$main/CareerLaunchNavigator.kt") }

    @Nested
    @DisplayName("stop reasons")
    inner class Reasons {
        @Test
        fun `a refused repair means the grant is missing, issued ones that changed nothing mean dead input`() {
            assertEquals(A11Y_GRANT_MISSING, accessibilityStopKey(rebindsIssued = 0, rebindsRefused = 1))
            assertEquals(A11Y_GRANT_MISSING, accessibilityStopKey(rebindsIssued = 2, rebindsRefused = 1))
            assertEquals(A11Y_INPUT_DEAD, accessibilityStopKey(rebindsIssued = 2, rebindsRefused = 0))
            assertNull(accessibilityStopKey(rebindsIssued = 0, rebindsRefused = 0))
        }

        @Test
        fun `an accessibility halt records its key without stopping the queue, and the first key wins`() {
            val before = Triple(StartModule.accessibilityHaltKey, StartModule.queueStopKey, StartModule.queueStopRequested)
            try {
                StartModule.accessibilityHaltKey = null
                StartModule.queueStopRequested = false
                requestAccessibilityHalt(A11Y_INPUT_DEAD)
                requestAccessibilityHalt(A11Y_GRANT_MISSING)
                assertEquals(A11Y_INPUT_DEAD, StartModule.accessibilityHaltKey)
                assertFalse(StartModule.queueStopRequested, "a queue stop would clear the saved queue")
                assertEquals(before.second, StartModule.queueStopKey)
            } finally {
                StartModule.accessibilityHaltKey = before.first
                StartModule.queueStopKey = before.second
                StartModule.queueStopRequested = before.third
            }
        }

        @Test
        fun `a run that halts for accessibility is not replayed, and leaves a resumable halt that says to continue`() {
            val before = StartModule.accessibilityHaltKey
            try {
                StartModule.accessibilityHaltKey = null
                val result = accessibilityHaltResult(A11Y_GRANT_MISSING, "no grant")
                // The run loop's halt branch is reached only by an error; a manual stop ends the queue and clears it.
                assertEquals(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, result.code)
                assertTrue(result is TaskResult.Error)
                assertEquals(A11Y_GRANT_MISSING, StartModule.accessibilityHaltKey)
                val retried =
                    StartModule.decideRunRetry(
                        resultCode = result.code,
                        enableRunQueue = true,
                        miscMode = false,
                        diagnostic = false,
                        queueStopRequested = false,
                        skipRequested = false,
                        botRunning = true,
                        gameRecoveryFailed = false,
                        accessibilityHalt = StartModule.accessibilityHaltKey != null,
                        retriesLeft = StartModule.RUN_RETRY_BUDGET,
                    )
                assertFalse(retried)
                val verdict = classifySessionEnd(SessionEndFacts(queueEnabled = true, haltEnd = SessionEnd.RUN_HALTED, haltCareerInFlight = true, queueStateActive = true))
                assertEquals(SessionEnd.RUN_HALTED, verdict.end)
                assertTrue(verdict.resumable)
                val report =
                    JSONObject().put("kind", "RUN_HALTED").put("queueEnabled", true).put("totalRuns", 5).put("runReached", 3)
                        .put("resumable", true).put("reasonKey", A11Y_GRANT_MISSING)
                val text = queueReportText(report)
                assertEquals("The queue stopped during run 3 of 5: its accessibility service needed a repair, and UMA Auto+ does not have the permission to repair it.", text.reason)
                assertTrue(text.nextAction!!.contains("within 24 hours"), text.nextAction)
            } finally {
                StartModule.accessibilityHaltKey = before
            }
        }

        @Test
        fun `the navigator claims dead input only for a known screen that a rebind did not move`() {
            assertEquals(A11Y_GRANT_MISSING, navigatorStuckKey(repairRefused = true, rebindIssuedOnThisScreen = true))
            assertEquals(A11Y_GRANT_MISSING, navigatorStuckKey(repairRefused = true, rebindIssuedOnThisScreen = false))
            assertEquals(A11Y_INPUT_DEAD, navigatorStuckKey(repairRefused = false, rebindIssuedOnThisScreen = true))
            assertEquals("STUCK_ON_SCREEN", navigatorStuckKey(repairRefused = false, rebindIssuedOnThisScreen = false))
        }
    }

    @Nested
    @DisplayName("a stuck episode and the stronger toggle")
    inner class Episodes {
        @Test
        fun `a rebind the episode outlives is counted once as changing nothing`() {
            var noChange = 0
            val episode = RebindEpisode { noChange++ }
            episode.start()
            episode.record(true)
            assertEquals(0, episode.withoutChange)
            episode.record(true)
            assertEquals(1, episode.withoutChange)
            episode.closeLast()
            episode.closeLast()
            assertEquals(2, episode.withoutChange)
            assertEquals(2, noChange)
            assertEquals(2, episode.issued)
            assertEquals(A11Y_INPUT_DEAD, episode.stopKey())
        }

        @Test
        fun `a refused rebind changes nothing it could be blamed for and makes the grant the reason`() {
            var noChange = 0
            val episode = RebindEpisode { noChange++ }
            episode.record(false)
            episode.record(false)
            episode.closeLast()
            assertEquals(0, episode.withoutChange)
            assertEquals(0, noChange)
            assertEquals(2, episode.refused)
            assertEquals(A11Y_GRANT_MISSING, episode.stopKey())
        }

        @Test
        fun `a new episode forgets the last one`() {
            val episode = RebindEpisode()
            episode.record(false)
            episode.record(true)
            episode.record(true)
            assertEquals(1, episode.withoutChange)
            episode.start()
            episode.closeLast()
            assertEquals(listOf(0, 0, 0), listOf(episode.issued, episode.refused, episode.withoutChange))
            assertNull(episode.stopKey())
        }

        @Test
        fun `the stronger toggle waits for two rebinds that changed nothing, and comes once per run`() {
            val episode = RebindEpisode()
            episode.record(true)
            assertFalse(shouldTryStrongToggle(episode, usedThisRun = false))
            episode.record(true)
            assertFalse(shouldTryStrongToggle(episode, usedThisRun = false))
            episode.closeLast()
            assertTrue(shouldTryStrongToggle(episode, usedThisRun = false))
            assertFalse(shouldTryStrongToggle(episode, usedThisRun = true))
        }

        @Test
        fun `a dialog ladder that never recovers takes its rebinds, one stronger toggle, then stops as dead input`() {
            // The Campaign dialog ladder: rebinds at 13 and 19, the stop (or the toggle) at 25, the stop after the grace.
            val episode = RebindEpisode()
            var stopAt = 25
            var toggleUsed = false
            val events = mutableListOf<String>()
            for (tick in 1..60) {
                if (tick == 13 || tick == 19) {
                    if (tick == 13) episode.start()
                    episode.record(true)
                    events += "rebind@$tick"
                } else if (tick >= stopAt) {
                    episode.closeLast()
                    if (shouldTryStrongToggle(episode, toggleUsed)) {
                        toggleUsed = true
                        episode.record(true)
                        stopAt = tick + STRONG_TOGGLE_GRACE_TICKS
                        events += "strong@$tick"
                        continue
                    }
                    events += "stop@$tick:${episode.stopKey()}"
                    break
                }
            }
            assertEquals(listOf("rebind@13", "rebind@19", "strong@25", "stop@31:A11Y_INPUT_DEAD"), events)
            assertEquals(3, episode.withoutChange)
        }
    }

    @Nested
    @DisplayName("ledger counters")
    inner class Counters {
        @Test
        fun `the session ledger carries refused repairs, rebinds without change and stronger toggles, and a new session clears them`() {
            SessionTally.reset()
            SessionTally.accessibilityRepairsRefused.incrementAndGet()
            SessionTally.accessibilityRebindsWithoutChange.addAndGet(2)
            SessionTally.accessibilityStrongToggles.incrementAndGet()
            val json = SessionTally.recoveriesJson()
            assertEquals(1, json.getInt("accessibilityRepairsRefused"))
            assertEquals(2, json.getInt("accessibilityRebindsWithoutChange"))
            assertEquals(1, json.getInt("accessibilityStrongToggles"))
            SessionTally.reset()
            val cleared = SessionTally.recoveriesJson()
            assertEquals(listOf(0, 0, 0), listOf("accessibilityRepairsRefused", "accessibilityRebindsWithoutChange", "accessibilityStrongToggles").map { cleared.getInt(it) })
        }

        @Test
        fun `every refused repair and every stronger toggle is counted where it happens`() {
            for (name in listOf("fun ensureAccessibilityService(", "fun forceRebindAccessibilityService(", "fun strongToggleAccessibilityService(")) {
                val fn = body(game, name)
                val catch = fn.indexOf("catch (e: SecurityException) {")
                assertTrue(fn.indexOf("SessionTally.accessibilityRepairsRefused.incrementAndGet()") > catch && catch >= 0, name)
            }
            assertTrue(body(game, "fun strongToggleAccessibilityService(").contains("SessionTally.accessibilityStrongToggles.incrementAndGet()"))
            for (ladder in listOf("dialogRebinds", "cutsceneRebinds", "unknownScreenRebinds")) {
                assertTrue(campaign.contains("private val $ladder = RebindEpisode { SessionTally.accessibilityRebindsWithoutChange.incrementAndGet() }"), ladder)
            }
        }
    }

    @Nested
    @DisplayName("call sites")
    inner class CallSites {
        @Test
        fun `no rebind, restore or stronger toggle has its result ignored`() {
            val bare = Regex("^\\s*(?:[\\w?.]+\\.)?(forceRebindAccessibilityService|ensureAccessibilityService|strongToggleAccessibilityService)\\(.*\\)\\s*$", RegexOption.MULTILINE)
            val sources = repoFile("$main/bot/Game.kt").parentFile.parentFile.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
            assertTrue(sources.size > 20)
            for (file in sources) {
                val hits = bare.findAll(file.readText().replace("\r\n", "\n")).map { it.value.trim() }.toList()
                assertEquals(emptyList<String>(), hits, file.name)
            }
        }

        @Test
        fun `a missing grant halts the queue with its own reason at the start of a run and mid-career`() {
            val start = body(game, "    fun start(): TaskResult {")
            val lost = start.substring(start.indexOf("if (!ensureAccessibilityService()) {"), start.indexOf("runDiagnostic()"))
            val lines = lost.lines().map { it.trim() }.filter { it.isNotEmpty() }
            assertEquals("return accessibilityHaltResult(", lines[1], "the run returns exactly the halt result")
            assertEquals(listOf("A11Y_GRANT_MISSING,", ")", "}"), listOf(lines[2], lines[4], lines[5]))
            assertEquals(6, lines.size, lost)
            val process = body(campaign, "    override fun process(): TaskResult? {")
            assertTrue(process.indexOf("requestAccessibilityHalt(A11Y_GRANT_MISSING)\n                throw InterruptedException(reason)") in 0 until process.indexOf("tryHandleAllDialogs()"))
            val repair = source("$main/bot/AccessibilityRepair.kt")
            assertFalse(repair.contains("queueStopRequested") || repair.contains("queueStopKey"), "an accessibility stop never ends the queue")
        }

        @Test
        fun `the run loop halts resumably after gameRecoveryFailed, before Stop Queue on Error`() {
            val start = source("$main/StartModule.kt")
            val unrecoverable = start.indexOf("ledger.haltEnd = SessionEnd.GAME_UNRECOVERABLE")
            val halt = start.indexOf("ledger.haltEnd = SessionEnd.RUN_HALTED")
            val onError = start.indexOf("ledger.haltEnd = SessionEnd.STOP_ON_ERROR")
            assertTrue(unrecoverable in 0 until halt && halt < onError, "$unrecoverable $halt $onError")
            val block = start.substring(start.lastIndexOf("if (accessibilityKey != null) {", halt), start.indexOf("break", halt))
            assertTrue(block.contains("ledger.reasonKey = accessibilityKey"))
            assertTrue(block.contains("queueHaltCareerInFlight = i > startFromRun || coldStartConfirmedCareer"))
            val unrecoverableBlock = start.substring(start.lastIndexOf("if (gameRecoveryFailed) {", unrecoverable), start.indexOf("break", unrecoverable))
            assertTrue(unrecoverableBlock.contains("queueHaltCareerInFlight = i > startFromRun || coldStartConfirmedCareer"), "the GAME_UNRECOVERABLE site uses the same confirmed-career expression, not an unconditional true")
            assertTrue(start.contains("gameRecoveryFailed = false\n                accessibilityHaltKey = null"), "reset with the other session flags")
        }

        @Test
        fun `the dialog and cutscene ladders stop with their episode's reason, after one stronger toggle at most`() {
            val process = body(campaign, "    override fun process(): TaskResult? {")
            assertTrue(process.contains("dialogRebinds.record(game.forceRebindAccessibilityService())"))
            assertTrue(process.contains("shouldTryStrongToggle(dialogRebinds, game.strongToggleUsed)"))
            assertTrue(process.contains("dialogStopAt = consecutiveDialogTicks + STRONG_TOGGLE_GRACE_TICKS"))
            assertTrue(process.contains("stopForStuckInput(dialogRebinds,"))
            assertTrue(process.contains("consecutiveDialogTicks = 0\n            dialogStopAt = dialogTicksBeforeStop"))
            val recover = body(campaign, "    private fun recoverFromUnknownScreen(")
            assertTrue(recover.contains("cutsceneRebinds.record(game.forceRebindAccessibilityService())"))
            assertTrue(recover.contains("shouldTryStrongToggle(cutsceneRebinds, game.strongToggleUsed)"))
            assertTrue(recover.contains("stopForStuckInput(\n                        cutsceneRebinds,"))
            assertTrue(recover.contains("unknownScreenRebinds.record(game.forceRebindAccessibilityService())"))
            val stop = body(campaign, "    private fun stopForStuckInput(")
            assertTrue(stop.contains("episode.stopKey()?.let { requestAccessibilityHalt(it) }"))
        }

        @Test
        fun `an unrecognized screen blames the grant only when a rebind was refused and the game itself was not lost`() {
            val recover = body(campaign, "    private fun recoverFromUnknownScreen(")
            assertTrue(recover.contains("if (unknownScreenRebinds.refused > 0 && !StartModule.gameRecoveryFailed) requestAccessibilityHalt(A11Y_GRANT_MISSING)"))
            assertFalse(recover.contains("A11Y_INPUT_DEAD"))
        }

        @Test
        fun `the stronger toggle marks the run first and always turns accessibility back on`() {
            val toggle = body(game, "fun strongToggleAccessibilityService(")
            assertTrue(toggle.indexOf("strongToggleUsed = true") in 0 until toggle.indexOf("\"0\")"))
            val finally = toggle.indexOf("} finally {")
            assertTrue(finally > toggle.indexOf("wait(3.0, skipWaitingForLoading = true)"))
            assertTrue(toggle.indexOf("Settings.Secure.ACCESSIBILITY_ENABLED, \"1\")") > finally)
        }

        @Test
        fun `the navigator gives its stuck failures the repair reason and keeps gestureUtils a getter`() {
            assertEquals(2, Regex("navigatorStuckKey\\(navRepairRefused, (stuck|tap)ScreenRebindIssued\\)").findAll(navigator).count())
            assertEquals(3, Regex("navigatorStuckKey\\(navRepairRefused, rebindIssuedOnThisScreen = false\\)").findAll(navigator).count())
            assertTrue(navigator.contains("stuckScreenRebindIssued = rebindAccessibility()"))
            assertTrue(navigator.contains("tapScreenRebindIssued = rebindAccessibility()"))
            val reset = body(navigator, "    fun navigate(")
            for (field in listOf("navRepairRefused = false", "stuckScreenRebindIssued = false", "tapScreenRebindIssued = false")) {
                assertTrue(reset.indexOf(field) in 0 until reset.indexOf("for (attempt in 0 until MAX_DETECTION_ATTEMPTS)"), field)
            }
            assertTrue(game.contains("val gestureUtils: MyAccessibilityService get() = MyAccessibilityService.getInstance()"))
        }
    }

    @Nested
    @DisplayName("the watchdog's clock")
    inner class Clock {
        @Test
        fun `a wall-clock jump does not move the watchdog's age, only the monotonic clock does`() {
            val real = Game.watchdogClock
            var monotonic = 1_000_000L
            try {
                Game.watchdogClock = { monotonic }
                Game.heartbeat()
                assertEquals(0L, Game.heartbeatAgeMs(), "the wall clock is far from the fake monotonic one, and must not be read")
                monotonic += 5_000L
                assertEquals(5_000L, Game.heartbeatAgeMs())
            } finally {
                Game.watchdogClock = real
                Game.heartbeat()
            }
        }

        @Test
        fun `the heartbeat and the watchdog read only the monotonic clock, and the ledger's file keeps the wall clock`() {
            for (name in listOf("fun heartbeat()", "internal fun heartbeatAgeMs()", "private fun startWatchdog(")) {
                assertFalse(body(game, name).contains("currentTimeMillis"), name)
            }
            assertTrue(game.contains("internal var watchdogClock: () -> Long = { SystemClock.elapsedRealtime() }"))
            assertTrue(game.contains("private var lastHeartbeatMs: Long = watchdogClock()"))
            assertTrue(body(game, "private fun startWatchdog(").contains("val age = heartbeatAgeMs()"))
            assertTrue(source("$main/QueueReport.kt").contains("writeHeartbeat(context.filesDir, sessionId, System.currentTimeMillis())"))
        }
    }
}
