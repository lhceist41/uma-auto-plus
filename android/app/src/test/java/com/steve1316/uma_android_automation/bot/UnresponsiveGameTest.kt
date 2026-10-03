package com.steve1316.uma_android_automation.bot

import android.view.WindowManager
import com.steve1316.uma_android_automation.BetweenRunRecovery
import com.steve1316.uma_android_automation.UnknownScreenLimitStep
import com.steve1316.uma_android_automation.utils.OWN_INPUT_PROBE_SELF_TOUCH_BUDGET_MS
import com.steve1316.uma_android_automation.utils.OWN_INPUT_PROBE_SELF_TOUCH_OFFSET
import com.steve1316.uma_android_automation.utils.OWN_INPUT_PROBE_SIZE
import com.steve1316.uma_android_automation.utils.OWN_INPUT_PROBE_YS
import com.steve1316.uma_android_automation.utils.OwnInputProbeResult
import com.steve1316.uma_android_automation.utils.isProbeTapTouch
import com.steve1316.uma_android_automation.utils.ownInputProbeParams
import com.steve1316.uma_android_automation.utils.ownInputProbeResult
import com.steve1316.uma_android_automation.utils.ownInputProbeY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("Unresponsive game on a known screen")
class UnresponsiveGameTest {
    @Nested
    @DisplayName("classification")
    inner class Classification {
        @Test
        fun `a tap that reached the bot's own window names the game, whatever the rebinds did`() {
            assertEquals(GAME_NOT_RESPONDING, stuckInputKey(A11Y_INPUT_DEAD, OwnInputProbeResult.ARRIVED))
            assertEquals(GAME_NOT_RESPONDING, stuckInputKey(A11Y_GRANT_MISSING, OwnInputProbeResult.ARRIVED))
            assertEquals(GAME_NOT_RESPONDING, stuckInputKey(null, OwnInputProbeResult.ARRIVED))
        }

        @Test
        fun `a tap lost on a window that provably takes touches keeps the rebinds' reason, dead input included`() {
            for (key in listOf(A11Y_INPUT_DEAD, A11Y_GRANT_MISSING, null)) {
                assertEquals(key, stuckInputKey(key, OwnInputProbeResult.LOST))
            }
        }

        @Test
        fun `an inconclusive probe never claims dead input or a frozen game`() {
            assertEquals(TAPS_HAD_NO_EFFECT, stuckInputKey(A11Y_INPUT_DEAD, OwnInputProbeResult.INCONCLUSIVE))
            assertEquals(A11Y_GRANT_MISSING, stuckInputKey(A11Y_GRANT_MISSING, OwnInputProbeResult.INCONCLUSIVE), "a refused repair is a fact either way")
            assertEquals(null, stuckInputKey(null, OwnInputProbeResult.INCONCLUSIVE))
            for (key in listOf(A11Y_INPUT_DEAD, A11Y_GRANT_MISSING, null)) {
                val probed = stuckInputKey(key, OwnInputProbeResult.INCONCLUSIVE)
                assertFalse(probed == A11Y_INPUT_DEAD || probed == GAME_NOT_RESPONDING, "$key -> $probed")
                assertFalse(reopensUnresponsiveGame(probed, careerObserved = true, reopensThisRun = 0), "no restart on an unproven probe")
            }
        }
    }

    @Nested
    @DisplayName("the probe's verdict")
    inner class ProbeVerdict {
        @Test
        fun `a healthy game - own touch and accessibility tap both arrive - reads ARRIVED`() {
            assertEquals(OwnInputProbeResult.ARRIVED, ownInputProbeResult(selfTouchArrived = true, dispatched = true, gestureArrived = true))
        }

        @Test
        fun `dead input - the window took the app's own touch but not the tap - reads LOST`() {
            assertEquals(OwnInputProbeResult.LOST, ownInputProbeResult(selfTouchArrived = true, dispatched = true, gestureArrived = false))
            assertEquals(OwnInputProbeResult.LOST, ownInputProbeResult(selfTouchArrived = true, dispatched = false, gestureArrived = false), "a refused dispatch is dead input too")
        }

        @Test
        fun `the app's own touch and the tap land on different halves of the window, so neither counts as the other`() {
            val centre = OWN_INPUT_PROBE_SIZE / 2f
            assertTrue(isProbeTapTouch(centre), "the tap lands on the centre")
            assertFalse(isProbeTapTouch(centre - OWN_INPUT_PROBE_SELF_TOUCH_OFFSET), "the app's own touch")
            assertTrue(centre - OWN_INPUT_PROBE_SELF_TOUCH_OFFSET > 0, "the app's own touch stays inside the window")
        }

        @Test
        fun `a window never proven touchable reads INCONCLUSIVE, whatever else happened`() {
            for (dispatched in listOf(false, true)) {
                for (arrived in listOf(false, true)) {
                    assertEquals(OwnInputProbeResult.INCONCLUSIVE, ownInputProbeResult(selfTouchArrived = false, dispatched = dispatched, gestureArrived = arrived))
                }
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
        private val params = ownInputProbeParams(probeY = OWN_INPUT_PROBE_YS[0], sdkInt = 32)

        /** MuMu's 1080x1920 at 3x density: the default 50 dp button plus its 2 dp margins. */
        private val buttonSize = 162

        @Test
        fun `with the floating button where it starts, at the screen centre, the probe keeps its usual row`() {
            assertEquals(640, ownInputProbeY(buttonX = (1080 - buttonSize) / 2, buttonY = (1920 - buttonSize) / 2, buttonSize = buttonSize))
        }

        @Test
        fun `a button parked over the usual row moves the probe to a clear one`() {
            assertEquals(1280, ownInputProbeY(buttonX = 0, buttonY = 600, buttonSize = buttonSize))
        }

        @Test
        fun `the app's own touch and the tap never land on the button, even a status bar below its saved y`() {
            for (bx in 0..400 step 20) {
                for (by in 0..1900 step 10) {
                    val y = ownInputProbeY(bx, by, buttonSize)
                    assertTrue(y != null, "a clear row exists for a normal button at ($bx, $by)")
                    for (shift in 0..150 step 50) {
                        val top = by + shift
                        for ((px, py) in listOf(OWN_INPUT_PROBE_SIZE / 4 to y!! + OWN_INPUT_PROBE_SIZE / 4, OWN_INPUT_PROBE_SIZE / 2 to y + OWN_INPUT_PROBE_SIZE / 2)) {
                            assertFalse(px in bx..bx + buttonSize && py in top..top + buttonSize, "button ($bx, $top) covers ($px, $py)")
                        }
                    }
                }
            }
        }

        @Test
        fun `a button too big to avoid skips the probe`() {
            assertEquals(null, ownInputProbeY(buttonX = 0, buttonY = 0, buttonSize = 2000))
        }

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
            val probed = stop.indexOf("val ownInput = game.ownInputReachesScreen()")
            val key = stop.indexOf("val key = stuckInputKey(episode.stopKey(), ownInput)")
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
            for (flag in listOf("stuckScreenRebindIssued, dialogButtonsMissing", "tapScreenRebindIssued", "titleScreenRebindIssued")) {
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
            assertTrue(probe.indexOf("service.dispatchGesture(tap, null, null)") in 0 until probe.indexOf("Log.w(TAG, \"[INPUT_PROBE] At ("))
            assertTrue(probe.contains("if (service == null || !gesturesAllowed) return OwnInputProbeResult.INCONCLUSIVE"), "no tap while gestures are paused")
        }

        @Test
        fun `the probe row is chosen clear of the button before the window is shown, and the own touch has a short total cap`() {
            val body = probe.substringAfter("internal fun ownInputReachesScreen(").substringBefore("\n}\n")
            assertTrue(body.indexOf("val probeY = ownInputProbeY(buttonX, buttonY, buttonSize)") in 0 until body.indexOf("windowManager.addView(view, ownInputProbeParams(probeY))"))
            assertTrue(body.contains("if (probeY == null) {"))
            assertTrue(body.contains("val selfTouchArrived = selfTouched.await(OWN_INPUT_PROBE_SELF_TOUCH_BUDGET_MS, TimeUnit.MILLISECONDS)"))
            assertTrue(body.indexOf("injectOwnTouch(") in body.indexOf("Thread {") until body.indexOf("injector.start()"), "injection off the probe's thread")
            assertTrue(OWN_INPUT_PROBE_SELF_TOUCH_BUDGET_MS in 1000L..4000L)
        }

        @Test
        fun `the accessibility tap is dispatched only after the app's own touch proved the window takes touches`() {
            val body = probe.substringAfter("internal fun ownInputReachesScreen(").substringBefore("\n}\n")
            val selfTouch = body.indexOf("injectOwnTouch(x - OWN_INPUT_PROBE_SELF_TOUCH_OFFSET, y - OWN_INPUT_PROBE_SELF_TOUCH_OFFSET)")
            val gate = body.indexOf("if (selfTouchArrived) {")
            val dispatch = body.indexOf("service.dispatchGesture(tap, null, null)")
            assertTrue(selfTouch in 0 until gate && gate < dispatch, "own touch, then the gate, then the tap")
            assertEquals(1, Regex(Regex.escape("dispatchGesture(")).findAll(probe).count(), "one tap, inside the gate")
            assertTrue(body.contains("if (isProbeTapTouch(event.x)) gestureTouched.countDown() else selfTouched.countDown()"), "the tap's touch is told apart from the app's own")
            val inject = probe.substringAfter("private fun injectOwnTouch(").substringBefore("\n}\n")
            assertTrue(inject.contains("instrumentation.sendPointerSync(event)"), "Android delivers this only to the app's own windows")
            assertFalse(inject.contains("dispatchGesture"))
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
