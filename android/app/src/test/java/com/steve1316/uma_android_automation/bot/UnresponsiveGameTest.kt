package com.steve1316.uma_android_automation.bot

import android.view.WindowManager
import com.steve1316.uma_android_automation.BetweenRunRecovery
import com.steve1316.uma_android_automation.UnknownScreenLimitStep
import com.steve1316.uma_android_automation.utils.OWN_INPUT_PROBE_SIZE
import com.steve1316.uma_android_automation.utils.ownInputProbeParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A game that ignores taps that still reach the screen is told apart from dead accessibility input
 * by the own-input probe, restarted on a screen the bot knows, and named truthfully when it cannot be.
 */
@DisplayName("Unresponsive game on a known screen")
class UnresponsiveGameTest {
    @Nested
    @DisplayName("classification")
    inner class Classification {
        @Test
        fun `a tap that reached the bot's own window names the game, whatever the rebinds did`() {
            assertEquals(GAME_NOT_RESPONDING, stuckInputKey(A11Y_INPUT_DEAD, ownInputArrived = true))
            assertEquals(GAME_NOT_RESPONDING, stuckInputKey(A11Y_GRANT_MISSING, ownInputArrived = true))
            assertEquals(GAME_NOT_RESPONDING, stuckInputKey(null, ownInputArrived = true))
        }

        @Test
        fun `a tap that did not arrive, or a probe that could not run, keeps the rebinds' reason`() {
            for (key in listOf(A11Y_INPUT_DEAD, A11Y_GRANT_MISSING, null)) {
                assertEquals(key, stuckInputKey(key, ownInputArrived = false))
                assertEquals(key, stuckInputKey(key, ownInputArrived = null))
            }
        }
    }

    @Nested
    @DisplayName("restart on a known screen")
    inner class Restart {
        @Test
        fun `only an unresponsive game with a career seen and restarts left is restarted`() {
            assertTrue(reopensUnresponsiveGame(GAME_NOT_RESPONDING, careerObserved = true, reopensThisRun = 0))
            assertTrue(reopensUnresponsiveGame(GAME_NOT_RESPONDING, careerObserved = true, reopensThisRun = MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN - 1))
            assertFalse(reopensUnresponsiveGame(GAME_NOT_RESPONDING, careerObserved = true, reopensThisRun = MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN))
            assertFalse(reopensUnresponsiveGame(GAME_NOT_RESPONDING, careerObserved = false, reopensThisRun = 0))
            for (key in listOf(A11Y_INPUT_DEAD, A11Y_GRANT_MISSING, null)) {
                assertFalse(reopensUnresponsiveGame(key, careerObserved = true, reopensThisRun = 0), "$key")
            }
            assertEquals(2, MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN)
        }

        @Test
        fun `on Android 14 and later a re-front is the run's last try, since the game can never be closed`() {
            for (sdk in 34..36) {
                assertEquals(MAX_UNRESPONSIVE_GAME_REOPENS_PER_RUN, unresponsiveReopensAfter(GameReopen.REFRONTED, used = 1, sdk = sdk), "sdk $sdk")
                for (reopen in listOf(GameReopen.RESTARTED, GameReopen.NOT_RESTARTED, GameReopen.NOT_DISPATCHED)) {
                    assertEquals(1, unresponsiveReopensAfter(reopen, used = 1, sdk = sdk), "${reopen.name} sdk $sdk")
                }
                // After the re-front the next stuck stop halts instead of trying again.
                assertFalse(reopensUnresponsiveGame(GAME_NOT_RESPONDING, careerObserved = true, reopensThisRun = unresponsiveReopensAfter(GameReopen.REFRONTED, used = 1, sdk = sdk)))
            }
        }

        @Test
        fun `on Android 12 and 13 a Home miss is one failed try, and the second try may still close the game`() {
            for (sdk in 26..33) {
                for (reopen in GameReopen.entries) {
                    assertEquals(1, unresponsiveReopensAfter(reopen, used = 1, sdk = sdk), "${reopen.name} sdk $sdk")
                }
                assertTrue(reopensUnresponsiveGame(GAME_NOT_RESPONDING, careerObserved = true, reopensThisRun = unresponsiveReopensAfter(GameReopen.REFRONTED, used = 1, sdk = sdk)))
            }
        }

        @Test
        fun `between runs the restart spends the navigation's one relaunch, and never with a career in flight`() {
            val recovery = BetweenRunRecovery(coldStartOnHome = true, previousCareerComplete = false, finalizeToHome = false, campaignOwnsCareer = false)
            assertFalse(recovery.mayRestartUnresponsiveGame(careerLaunchInitiated = true))
            assertTrue(recovery.mayRestartUnresponsiveGame(careerLaunchInitiated = false))
            recovery.restartingUnresponsiveGame()
            assertTrue(recovery.gameComingBack)
            assertFalse(recovery.mayRestartUnresponsiveGame(careerLaunchInitiated = false))
            assertEquals(UnknownScreenLimitStep.GAME_UNRECOVERABLE, recovery.onUnknownScreenLimit(careerLaunchInitiated = false))

            val inCareer = BetweenRunRecovery(coldStartOnHome = true, previousCareerComplete = false, finalizeToHome = false, campaignOwnsCareer = true)
            assertFalse(inCareer.mayRestartUnresponsiveGame(careerLaunchInitiated = false))
        }
    }

    @Nested
    @DisplayName("the probe window")
    inner class ProbeWindow {
        private val params = ownInputProbeParams(sdkInt = 32)

        @Test
        fun `takes touches but never focus, as an overlay of this app`() {
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
            assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, "the tap must land on it")
            assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0, "the game keeps its input focus")
            assertEquals(OWN_INPUT_PROBE_SIZE, params.width)
            assertEquals(OWN_INPUT_PROBE_SIZE, params.height)
            assertTrue(params.y > 100, "clear of the status bar")
        }
    }

    @Nested
    @DisplayName("source guards")
    inner class SourceGuard {
        private val campaign by lazy { source("bot/Campaign.kt") }
        private val navigator by lazy { source("CareerLaunchNavigator.kt") }
        private val game by lazy { source("bot/Game.kt") }
        private val probe by lazy { source("utils/OwnInputProbe.kt") }

        @Test
        fun `the stuck-input stop probes first, restarts before it logs, and halts with the probed key`() {
            val stop = campaign.substringAfter("    private fun stopForStuckInput(").substringBefore("\n    }\n")
            val probed = stop.indexOf("val ownInputArrived = game.ownInputReachesScreen()")
            val key = stop.indexOf("val key = stuckInputKey(episode.stopKey(), ownInputArrived)")
            val reopen = stop.indexOf("val reopen = game.reopenGame(attempt = 2)")
            assertTrue(probed in 0 until key && key < reopen, "probe, then key, then the closing restart")
            assertTrue(reopen < stop.indexOf("MessageLog"), "the restart acts before anything is logged")
            assertTrue(stop.contains("if (reopensUnresponsiveGame(key, careerScreenObservedThisTask, unresponsiveGameReopens)) {"))
            assertTrue(stop.contains("key?.let { requestAccessibilityHalt(it) }"))
            assertTrue(stop.indexOf("unresponsiveGameReopens = unresponsiveReopensAfter(reopen, attempt, Build.VERSION.SDK_INT)") > reopen, "the device decides whether a re-front ends the tries")
            val label = stop.substringAfter("val tried =").substringBefore("MessageLog")
            assertTrue(label.contains("if (unresponsiveGameReopens > attempt) {\n                        \"a re-front, the run's last try"), "the last-try label follows the budget")
            assertTrue(label.contains("\"closing try \$attempt/"), "a try that may close is never called a restart")
            assertFalse(label.contains("\"restart "))
            assertFalse(stop.contains("episode.stopKey()?.let"), "the halt uses the probed key, not the rebinds' alone")
        }

        @Test
        fun `the stuck-input stop has one KDoc, not a stale one stacked above it`() {
            val before = campaign.substringBefore("    private fun stopForStuckInput(")
            val docs = before.substring(before.lastIndexOf("\n    }\n"))
            assertEquals(1, Regex(Regex.escape("/**")).findAll(docs).count())
        }

        @Test
        fun `the cutscene ladder leaves its tick after a restart instead of tapping on`() {
            assertTrue(campaign.contains("A screenshot was saved to the temp folder as event_cutscene_stuck.\",\n                    )\n                    return\n"))
        }

        @Test
        fun `the navigator probes its three stuck failures and restarts before it gives up`() {
            for (flag in listOf("stuckScreenRebindIssued", "tapScreenRebindIssued", "titleScreenRebindIssued")) {
                val line = "val stuckKey = probedStuckKey(navigatorStuckKey(navRepairRefused, $flag))\n                        restartUnresponsiveGame(stuckKey)?.let { return it }"
                assertEquals(1, Regex(Regex.escape(line)).findAll(navigator).count(), flag)
            }
            assertEquals(3, Regex(Regex.escape("reasonKey = stuckKey,")).findAll(navigator).count())
            val restart = navigator.substringAfter("fun restartUnresponsiveGame(key: String): NavigationResult? {").substringBefore("\n        }\n")
            assertTrue(restart.indexOf("betweenRunRecovery.mayRestartUnresponsiveGame(careerLaunchInitiated)") in 0 until restart.indexOf("reopenGame(attempt = 2)"))
            assertTrue(restart.indexOf("reopenGame(attempt = 2)") < restart.indexOf("MessageLog"))
        }

        @Test
        fun `Home is confirmed by the window in front, and the probe and window read take gestureUtils fresh`() {
            assertTrue(game.contains("frontPackage = {\n                    val front = frontWindowPackage()\n"))
            assertTrue(game.contains("gestureUtils.rootInActiveWindow?.packageName?.toString()"))
            assertTrue(game.contains("ownInputReachesScreen(myContext, runCatching { gestureUtils }.getOrNull(), MyAccessibilityService.isGestureAllowed)"))
            assertTrue(game.contains("val gestureUtils: MyAccessibilityService get() = MyAccessibilityService.getInstance()"), "still a per-access getter")
        }

        @Test
        fun `the restart test checks the bot's own taps before it restarts the game`() {
            val body = campaign.substringAfter("open fun startGameRestartTest() {").substringBefore("\n    }\n")
            assertTrue(body.indexOf("val ownInput = game.ownInputReachesScreen()") in 0 until body.indexOf("game.reopenGame(attempt = 2)"))
        }

        @Test
        fun `the restart test needs the window read to name the game before it touches anything`() {
            val body = campaign.substringAfter("open fun startGameRestartTest() {").substringBefore("\n    }\n")
            val read = body.indexOf("val front = game.frontWindowPackage()")
            val gate = body.indexOf("if (front != Game.GAME_PACKAGE) {")
            assertTrue(read in 0 until gate && gate < body.indexOf("game.ownInputReachesScreen()"))
            val stop = body.substring(gate).substringBefore("\n        }\n")
            assertTrue(stop.contains("return") && !stop.contains("reopenGame") && !stop.contains("ownInputReachesScreen"))
        }

        @Test
        fun `the probe taps before it logs and never uses MessageLog`() {
            assertFalse(probe.contains("MessageLog"))
            assertTrue(probe.indexOf("service.dispatchGesture(tap, null, null)") in 0 until probe.indexOf("Log.w(TAG, \"[INPUT_PROBE] Tap at"))
            assertTrue(probe.contains("if (service == null || !gesturesAllowed) return null"), "no tap while gestures are paused")
        }

        private fun source(relative: String): String {
            var dir: File? = File(System.getProperty("user.dir") ?: ".")
            repeat(5) {
                for (root in listOf("src/main/java/com/steve1316/uma_android_automation", "android/app/src/main/java/com/steve1316/uma_android_automation")) {
                    val f = File(File(dir, root), relative)
                    if (f.isFile) return f.readText().replace("\r\n", "\n")
                }
                dir = dir?.parentFile
            }
            error("missing $relative")
        }
    }
}
