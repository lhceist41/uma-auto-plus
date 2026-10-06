package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.queueReportText
import com.steve1316.uma_android_automation.utils.OwnInputProbeResult
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
 * Recovery without WRITE_SECURE_SETTINGS (phones): no rebind is ever tried, one own-input probe per stuck
 * episode decides, and only lost taps stop the run. With the grant every site keeps its rebind. The call
 * sites are pinned by source guards, since neither a phone nor dead dispatch can be staged on the JVM.
 */
@DisplayName("Recovery without the self-repair permission")
class NoGrantRecoveryTest {
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

    private val campaign by lazy { source("$main/bot/Campaign.kt") }
    private val navigator by lazy { source("$main/CareerLaunchNavigator.kt") }
    private val startModule by lazy { source("$main/StartModule.kt") }

    private val probes = OwnInputProbeResult.entries

    @Nested
    @DisplayName("the decision")
    inner class Decision {
        @Test
        fun `with the grant every site rebinds and the probe is never run`() {
            for (result in probes) {
                assertEquals(RebindStep.REBIND, rebindStep(grant = true) { error("probed with the grant ($result)") })
            }
        }

        @Test
        fun `without the grant only lost taps halt, and the probe runs exactly once`() {
            val expected = mapOf(OwnInputProbeResult.LOST to RebindStep.HALT, OwnInputProbeResult.ARRIVED to RebindStep.SKIP, OwnInputProbeResult.INCONCLUSIVE to RebindStep.SKIP)
            for (result in probes) {
                var runs = 0
                assertEquals(expected[result], rebindStep(grant = false) { runs++; result }, "$result")
                assertEquals(1, runs, "$result")
            }
        }

        @Test
        fun `a stuck stop without the grant takes its key from the probe alone`() {
            assertEquals(GAME_NOT_RESPONDING, noGrantStuckKey(OwnInputProbeResult.ARRIVED))
            assertEquals(A11Y_TAPS_STOPPED, noGrantStuckKey(OwnInputProbeResult.LOST))
            assertNull(noGrantStuckKey(OwnInputProbeResult.INCONCLUSIVE), "an inconclusive probe proves nothing about the taps")
        }

        @Test
        fun `the navigator's final stop probes a plain stuck screen only without the grant`() {
            assertEquals(GAME_NOT_RESPONDING, stuckKeyAfterProbe(STUCK_ON_SCREEN, grant = false) { OwnInputProbeResult.ARRIVED })
            assertEquals(A11Y_TAPS_STOPPED, stuckKeyAfterProbe(STUCK_ON_SCREEN, grant = false) { OwnInputProbeResult.LOST })
            assertEquals(STUCK_ON_SCREEN, stuckKeyAfterProbe(STUCK_ON_SCREEN, grant = false) { OwnInputProbeResult.INCONCLUSIVE })
            assertEquals(STUCK_ON_SCREEN, stuckKeyAfterProbe(STUCK_ON_SCREEN, grant = true) { error("no probe without a rebind on MuMu") })
            assertEquals(DIALOG_NOT_CLOSED, stuckKeyAfterProbe(DIALOG_NOT_CLOSED, grant = false) { error("no probe for a dialog nothing was tapped on") })
            // A real restore refusal (the service was switched off) keeps its own key and probe on both sides.
            for (grant in listOf(true, false)) {
                assertEquals(GAME_NOT_RESPONDING, stuckKeyAfterProbe(A11Y_GRANT_MISSING, grant) { OwnInputProbeResult.ARRIVED })
                assertEquals(A11Y_GRANT_MISSING, stuckKeyAfterProbe(A11Y_GRANT_MISSING, grant) { OwnInputProbeResult.LOST })
            }
        }

        @Test
        fun `a ladder with no rebind recorded never reaches the stronger toggle or blames the grant`() {
            val episode = RebindEpisode()
            episode.start()
            episode.closeLast()
            assertFalse(shouldTryStrongToggle(episode, usedThisRun = false))
            assertNull(episode.stopKey())
            assertEquals(STUCK_ON_SCREEN, navigatorStuckKey(repairRefused = false, rebindIssuedOnThisScreen = false))
        }

        @Test
        fun `the campaign's final stop adds the no-grant key only when the episode and the probe left none`() {
            val stop = body(campaign, "    private fun stopForStuckInput(")
            assertTrue(stop.contains("val key = stuckInputKey(episode.stopKey(), ownInput) ?: noGrantStuckKey(ownInput).takeUnless { StartModule.secureSettingsGrant }\n"))
            // Without the grant nothing is recorded, so the episode key is null: ARRIVED already reads as the game, LOST becomes the taps.
            assertEquals(GAME_NOT_RESPONDING, stuckInputKey(null, OwnInputProbeResult.ARRIVED) ?: noGrantStuckKey(OwnInputProbeResult.ARRIVED))
            assertEquals(A11Y_TAPS_STOPPED, stuckInputKey(null, OwnInputProbeResult.LOST) ?: noGrantStuckKey(OwnInputProbeResult.LOST))
            assertNull(stuckInputKey(null, OwnInputProbeResult.INCONCLUSIVE) ?: noGrantStuckKey(OwnInputProbeResult.INCONCLUSIVE))
        }
    }

    @Nested
    @DisplayName("the player's words")
    inner class Words {
        private fun halt(key: String) = queueReportText(JSONObject().put("kind", "RUN_HALTED").put("queueEnabled", true).put("totalRuns", 3).put("runReached", 2).put("resumable", true).put("reasonKey", key))

        @Test
        fun `lost taps and a missing grant both send the player to Settings, never to adb`() {
            for (key in listOf(A11Y_TAPS_STOPPED, A11Y_GRANT_MISSING)) {
                val text = halt(key)
                assertTrue(text.nextAction!!.startsWith("Turn UMA Auto+ off and on again in Settings > Accessibility, then press Start"), "$key: ${text.nextAction}")
                for (word in listOf("adb", "WRITE_SECURE_SETTINGS", "MuMu", "grant")) assertFalse(text.body.contains(word, ignoreCase = true), "$key: $word")
            }
            assertEquals("The queue stopped during run 2 of 3: Android stopped delivering its taps.", halt(A11Y_TAPS_STOPPED).reason)
        }

        @Test
        fun `the run's own stop reasons name the Settings fix, not the permission`() {
            val start = body(source("$main/bot/Game.kt"), "    fun start(): TaskResult {")
            val lost = start.substring(start.indexOf("if (!ensureAccessibilityService()) {"), start.indexOf("runDiagnostic()"))
            val process = body(campaign, "    override fun process(): TaskResult? {")
            val wiped = process.substring(process.indexOf("if (!game.ensureAccessibilityService()) {"), process.indexOf("requestAccessibilityHalt(A11Y_GRANT_MISSING)"))
            val stopped = body(campaign, "    private fun stopIfTapsStopped(")
            for (text in listOf(lost, wiped, stopped)) {
                assertTrue(text.contains("in Settings > Accessibility, then press Start."), text)
                assertFalse(text.contains("WRITE_SECURE_SETTINGS") || text.contains("see log"), text)
            }
        }
    }

    @Nested
    @DisplayName("the call sites")
    inner class Sites {
        private val grantIf = "if (StartModule.secureSettingsGrant) {"

        /** The line that opens the block a call sits in. */
        private fun enclosingLine(src: String, callAt: Int): String {
            val lines = src.substring(0, callAt).lines()
            val callIndent = lines.last().length - lines.last().trimStart().length
            return lines.dropLast(1).last { it.isNotBlank() && it.length - it.trimStart().length < callIndent }.trim()
        }

        private fun calls(src: String, call: String) = Regex(Regex.escape(call)).findAll(src).map { it.range.first }.toList()

        @Test
        fun `all ten rebind sites sit in a grant branch`() {
            val navCalls = calls(navigator, "rebindAccessibility()").filter { !navigator.substring(navigator.lastIndexOf('\n', it), it).contains("fun ") }
            assertEquals(6, navCalls.size, "detection catch, stuck screen, tap to continue, title login, unknown screen, handler catch")
            val unknownTick = "} else if (consecutiveUnknowns >= 2 && !betweenRunRecovery.gameComingBack) {"
            for (at in navCalls) assertTrue(enclosingLine(navigator, at) in listOf(grantIf, unknownTick), navigator.substring(at - 200, at))
            assertEquals(1, navCalls.count { enclosingLine(navigator, it) == unknownTick })
            // The unknown-screen tick: the no-grant branch comes first, so its rebind is reached only with the grant.
            val noGrantTick = navigator.indexOf("if (!StartModule.secureSettingsGrant && consecutiveUnknowns >= 2 && !betweenRunRecovery.gameComingBack) {")
            assertTrue(noGrantTick in 0 until navigator.indexOf(unknownTick))
            assertFalse(navigator.substring(noGrantTick, navigator.indexOf(unknownTick)).contains("rebindAccessibility()"))
            val campaignCalls = calls(campaign, "game.forceRebindAccessibilityService()")
            assertEquals(3, campaignCalls.size, "dialog, cutscene and unknown-screen ladders")
            for (at in campaignCalls) assertEquals(grantIf, enclosingLine(campaign, at), campaign.substring(at - 200, at))
            // The borrow walker's rebind: refused up front, with one W line, before anything else runs.
            val borrow = body(navigator, "    private fun recoverGestureDispatch(")
            val guard = borrow.indexOf("if (!StartModule.secureSettingsGrant) {")
            val guardEnd = borrow.indexOf("return false\n        }", guard)
            assertTrue(guard in 0 until borrow.indexOf("MyAccessibilityService.getInstance()"), borrow)
            assertTrue(guardEnd in guard until borrow.indexOf("game.forceRebindAccessibilityService()"))
            assertEquals(1, Regex("MessageLog\\.").findAll(borrow.substring(guard, guardEnd)).count())
            assertEquals(1, calls(navigator, "game.forceRebindAccessibilityService()").size, "the borrow walker's own call; the others go through rebindAccessibility")
        }

        @Test
        fun `without the grant the detection and handler catches still retry once`() {
            for (at in calls(navigator, "Retrying once.\")")) {
                val branch = navigator.substring(navigator.lastIndexOf("exceptionRecoveryUsed = true", at), navigator.indexOf("continue\n", at))
                assertTrue(branch.contains("} else {"), branch)
                assertTrue(branch.indexOf("consecutiveUnknowns = 0") > branch.indexOf("Retrying once."), "the counters reset on both sides")
            }
            assertEquals(2, calls(navigator, "Retrying once.\")").size)
        }

        @Test
        fun `a stuck episode probes once at its first threshold, never per tick`() {
            assertEquals(1, calls(navigator, "tempGame?.ownInputReachesScreen()").size)
            assertEquals(4, calls(navigator, "tapsStoppedWithoutGrant(detectedState)?.let { return it }").size)
            for (threshold in listOf("stuckInStateCount == STUCK_STATE_REBIND_AT", "tapToContinueCount == TAP_TO_CONTINUE_REBIND_AT", "titleLoginLooks == TITLE_LOGIN_REBIND_AT")) {
                val at = navigator.indexOf("if ($threshold) {")
                val site = navigator.substring(at, navigator.indexOf("\n                    }\n", at))
                assertTrue(site.contains("} else {\n                            tapsStoppedWithoutGrant(detectedState)?.let { return it }"), site)
            }
            assertTrue(navigator.contains("if (consecutiveUnknowns == 2) tapsStoppedWithoutGrant(detectedState)?.let { return it }\n                    checkAccessibility()\n"), "one probe per unknown episode, then the string check")

            assertEquals(1, calls(body(campaign, "    private fun stopForStuckInput("), "game.ownInputReachesScreen()").size, "the final stop")
            assertEquals(1, calls(body(campaign, "    private fun stopIfTapsStopped("), "game.ownInputReachesScreen()").size, "the no-grant threshold")
            assertEquals(3, calls(campaign, "stopIfTapsStopped(\"").size)
            for (first in listOf("} else if (consecutiveDialogTicks == 13) {", "} else if (count == cutsceneRebindThresholds.min()) {", "} else if (count == gestureRebindThresholds.min()) {")) {
                val at = campaign.indexOf(first)
                assertTrue(at >= 0, first)
                assertTrue(campaign.substring(at, campaign.indexOf('\n', at + first.length + 1)).contains("stopIfTapsStopped(\""), first)
            }
            val stop = body(campaign, "    private fun stopIfTapsStopped(")
            assertTrue(stop.indexOf("requestAccessibilityHalt(A11Y_TAPS_STOPPED)") in stop.indexOf("!= RebindStep.HALT) return") until stop.indexOf("throw InterruptedException("))
        }

        @Test
        fun `the navigator's lost-taps stop is a halt with its own key and a stuck stop passes the grant`() {
            val stopped = body(navigator, "    private fun tapsStoppedWithoutGrant(")
            assertTrue(stopped.contains("rebindStep(StartModule.secureSettingsGrant) { ownInputProbe() } != RebindStep.HALT) return null"))
            assertTrue(stopped.contains("reasonKey = A11Y_TAPS_STOPPED,"))
            assertTrue(navigator.contains("stuckKeyAfterProbe(key, StartModule.secureSettingsGrant) { ownInputProbe() }"))
        }

        @Test
        fun `the grant is read once per session, logged once, and only read elsewhere`() {
            val resetAt = startModule.indexOf("accessibilityHaltKey = null\n                launchStoppedByPlayer = false")
            val reset = startModule.substring(resetAt, startModule.indexOf("dispatchDiagnostic(", resetAt))
            val read = reset.indexOf("secureSettingsGrant = secureSettingsGranted(context)\n")
            assertTrue(read >= 0, reset)
            val logLine = reset.substring(read).lines()[1]
            assertTrue(logLine.trim().startsWith("MessageLog.i(TAG, if (secureSettingsGrant) \"[A11Y] "), logLine)
            assertEquals(2, Regex("\"\\[A11Y\\] ").findAll(logLine).count(), "one line, either way")
            val sources = repoFile("$main/bot/Game.kt").parentFile.parentFile.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
            val writes = sources.sumOf { f -> Regex("\\bsecureSettingsGrant = ").findAll(f.readText()).count() }
            assertEquals(1, writes, "the session reset is the only writer")
            for (src in listOf(navigator, campaign)) assertFalse(src.contains("hasSecureSettingsGrant") || src.contains("checkSelfPermission"))
        }
    }
}
