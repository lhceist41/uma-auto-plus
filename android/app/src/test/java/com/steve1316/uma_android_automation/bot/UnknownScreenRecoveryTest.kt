package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.DebugTestGate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The unknown-screen recovery ladder's game-relaunch decisions, plus source guards that pin the
 * 2026-07-21 incident fix in place: the in-app relaunch must not tear down a live game task
 * (CLEAR_TASK killed a live foreground game), and a stop after a failed relaunch must pause the
 * queue rather than march the next run onto a dead or foreign screen.
 */
@DisplayName("Unknown-screen recovery")
class UnknownScreenRecoveryTest {
    private val threshold = 22
    private val maxAttempts = 3

    @Nested
    @DisplayName("shouldRelaunchGame")
    inner class ShouldRelaunch {
        @Test
        fun `fires exactly at the threshold with budget left and a career observed`() {
            assertTrue(shouldRelaunchGame(threshold, threshold, attemptsUsed = 0, maxAttempts, careerObserved = true))
        }

        @Test
        fun `does not fire off the threshold count`() {
            assertFalse(shouldRelaunchGame(threshold - 1, threshold, 0, maxAttempts, true))
            assertFalse(shouldRelaunchGame(threshold + 1, threshold, 0, maxAttempts, true))
        }

        @Test
        fun `never fires before a career was observed (a parked pre-career lobby is not relaunched)`() {
            assertFalse(shouldRelaunchGame(threshold, threshold, 0, maxAttempts, careerObserved = false))
        }

        @Test
        fun `retries across the episode up to the budget, then stops relaunching`() {
            assertTrue(shouldRelaunchGame(threshold, threshold, attemptsUsed = 0, maxAttempts, true))
            assertTrue(shouldRelaunchGame(threshold, threshold, attemptsUsed = 1, maxAttempts, true))
            assertTrue(shouldRelaunchGame(threshold, threshold, attemptsUsed = 2, maxAttempts, true))
            // Budget spent: the 4th climb to the threshold no longer relaunches (falls through to stop).
            assertFalse(shouldRelaunchGame(threshold, threshold, attemptsUsed = 3, maxAttempts, true))
        }
    }

    @Nested
    @DisplayName("stopIsGameUnrecoverable")
    inner class StopUnrecoverable {
        @Test
        fun `a stop after at least one relaunch attempt pauses the queue`() {
            assertTrue(stopIsGameUnrecoverable(1))
            assertTrue(stopIsGameUnrecoverable(maxAttempts))
        }

        @Test
        fun `a stop with no relaunch attempted stays a generic error`() {
            assertFalse(stopIsGameUnrecoverable(0))
        }
    }

    @Nested
    @DisplayName("source guard")
    inner class SourceGuard {
        @Test
        fun `restartGame re-fronts the game and never tears down a live task with CLEAR_TASK`() {
            val game = sourceFile("bot/Game.kt").readText().replace("\r\n", "\n")
            assertTrue(
                game.contains("fun restartGame(waitAfterLaunch: Double = 20.0): Boolean = reopenGame(attempt = 1, waitAfterLaunch = waitAfterLaunch)"),
                "the navigator's single relaunch stays the first-attempt re-front",
            )
            // CLEAR_TASK against a live game killed it on 2026-07-21: it exists only behind launchGame's
            // clearTask flag, and only the restart sequence passes true, after its kill window.
            assertEquals(1, Regex("FLAG_ACTIVITY_CLEAR_TASK").findAll(game).count())
            assertTrue(game.contains("if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED"))
            assertFalse(game.contains("launchGame(clearTask = true)") || game.contains("launchGame(true)"))
            assertTrue(game.contains("launch = { clearTask -> launchGame(clearTask) }"))
            val restart = sourceFile("bot/GameRestart.kt").readText().replace("\r\n", "\n").substringAfter("internal fun restartFrozenGame(")
            val lastKill = restart.indexOf("        killGame()\n    }\n    sleep(GAME_LAUNCH_SECONDS_AFTER_HOME - elapsed)")
            assertTrue(lastKill > restart.indexOf("pressHome()"), "the kill calls come after Home")
            assertTrue(restart.indexOf("launch(true)") > lastKill, "the fresh-task launch comes after the whole kill window")
            assertEquals(1, Regex("""launch\(true\)""").findAll(restart).count())
        }

        @Test
        fun `the kill route is only taken where Android lets an app close another app, and only after Home`() {
            val game = sourceFile("bot/Game.kt").readText().replace("\r\n", "\n")
            val reopen = game.substringAfter("internal fun reopenGame(").substringBefore("\n    }\n")
            assertTrue(reopen.contains("if (!reopenClosesGame(attempt, Build.VERSION.SDK_INT)) {"))
            // A Home press that was not dispatched must stop the kill route: the lambda returns the real result.
            assertTrue(reopen.contains("(dispatched=\$pressed).\")\n                    pressed\n                },"))
            assertEquals(1, Regex("""\.killBackgroundProcesses\(""").findAll(game).count(), "one kill site")
            assertTrue(game.substringAfter("private fun killGameProcess()").substringBefore("\n    }\n").contains("killBackgroundProcesses(GAME_PACKAGE)"))
            val manifest = File(kotlinRoot(), "../../../../AndroidManifest.xml").readText()
            assertTrue(manifest.contains("<uses-permission android:name=\"android.permission.KILL_BACKGROUND_PROCESSES\" />"))
        }

        @Test
        fun `the reopen acts before it logs through MessageLog, and counts only what it dispatched`() {
            val game = sourceFile("bot/Game.kt").readText().replace("\r\n", "\n")
            val reopen = game.substringAfter("internal fun reopenGame(").substringBefore("\n    }\n")
            val steps = reopen.substringAfter("restartFrozenGame(").substringBefore("return when (result)")
            assertFalse(steps.contains("MessageLog"), "the steps log with android.util.Log only")
            for (helper in listOf("private fun launchGame(", "private fun killGameProcess(")) {
                assertFalse(game.substringAfter(helper).substringBefore("\n    }\n").contains("MessageLog"), helper)
            }
            // Re-front, restarted, not restarted, and the re-front after an unchanged screen; never when nothing was dispatched.
            assertEquals(4, Regex("""SessionTally\.gameRelaunches\.incrementAndGet\(\)""").findAll(reopen).count())
            assertFalse(reopen.substringAfter("FrozenGameRestart.NOT_DISPATCHED ->").contains("SessionTally"))

            val campaign = sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n")
            val rung = campaign.substringAfter("if (shouldRelaunchGame(").substringBefore("if (DialogUtils.check(game.imageUtils))")
            val act = rung.indexOf("val reopen = game.reopenGame(gameRestartAttemptsThisEpisode)")
            assertTrue(act >= 0 && act < rung.indexOf("MessageLog"), "the ladder reopens before it logs")
            assertTrue(rung.contains("if (reopen != GameReopen.NOT_DISPATCHED) {"))
            assertFalse(campaign.contains("relaunching the game"), "the ladder no longer claims a relaunch before one happened")
        }

        @Test
        fun `the recovery log says the game is reopened, since a running game keeps its process`() {
            // The intent re-fronts a live game: the pid stays the same, so "relaunching" misled the
            // reader of a stuck run into thinking the game had been restarted.
            val game = sourceFile("bot/Game.kt").readText()
            val body = game.substring(game.indexOf("fun restartGame("), game.indexOf("fun start()"))
            assertFalse("Relaunching the game" in body)
            assertTrue("[RECOVERY] Reopening the game" in body && "not restarted" in body)
            val campaign = sourceFile("bot/Campaign.kt").readText()
            assertFalse("did not help - relaunching the game" in campaign)
            assertFalse("\$gameRestartAttemptsThisEpisode relaunch " in campaign)
            assertTrue("did not help - reopening the game" in campaign)
        }

        @Test
        fun `the reopen's words claim only what the app can see`() {
            val game = sourceFile("bot/Game.kt").readText().replace("\r\n", "\n")
            val reopen = game.substringAfter("internal fun reopenGame(").substringBefore("\n    }\n")
            val restarted = reopen.substringAfter("FrozenGameRestart.RESTARTED, FrozenGameRestart.RESTARTED_ON_PLAIN_LAUNCH ->").substringBefore("GameReopen.RESTARTED")
            assertTrue(restarted.contains("asked to close") && !restarted.contains(", closed and"), "a kill is asked for, never proven")
            val notRestarted = reopen.substringAfter("FrozenGameRestart.NOT_RESTARTED ->").substringBefore("GameReopen.NOT_RESTARTED")
            assertFalse(notRestarted.contains("twice") || notRestarted.contains("either time"), "one of the two launches may not have been dispatched")
            val unchanged = reopen.substringAfter("FrozenGameRestart.SCREEN_UNCHANGED_AFTER_HOME ->").substringBefore("GameReopen.REFRONTED")
            assertTrue(unchanged.contains("Home did not clearly leave the game") && !unchanged.contains("is stuck"), "the cause is not known")
        }

        @Test
        fun `the restart test is an explicit diagnostic that makes the ladder's second-attempt call and taps nothing`() {
            val campaign = sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n")
            assertTrue("debugMode_startGameRestartTest" in DebugTestGate.ALL_KEYS)
            assertTrue(campaign.contains("\"debugMode_startGameRestartTest\" to ::startGameRestartTest,"))
            val body = campaign.substringAfter("open fun startGameRestartTest() {").substringBefore("\n    }\n")
            assertTrue(body.contains("game.reopenGame(attempt = 2)"))
            for (forbidden in listOf("tap(", "tapCoordinate(", "swipe(", ".click(", "restartGame(")) assertFalse(body.contains(forbidden), forbidden)
            val rung = campaign.substringAfter("if (shouldRelaunchGame(").substringBefore("if (DialogUtils.check(game.imageUtils))")
            assertTrue(rung.contains("game.reopenGame(gameRestartAttemptsThisEpisode)"), "the diagnostic makes the rung's own call")
        }

        @Test
        fun `the relaunch rung is bounded by the retry helper, not a one-shot boolean`() {
            val campaign = sourceFile("bot/Campaign.kt").readText()
            assertTrue("shouldRelaunchGame(" in campaign, "the ladder gates the relaunch through the bounded helper")
            assertTrue("gameRestartAttemptsThisEpisode++" in campaign, "each attempt is counted against the budget")
            assertFalse("gameRestartAttemptedThisEpisode" in campaign, "the old one-shot boolean is gone")
        }

        @Test
        fun `an unrecoverable stop flags the queue-pause before throwing`() {
            val campaign = sourceFile("bot/Campaign.kt").readText()
            val cap = campaign.indexOf("count >= maxUnknownScreenBeforeStop")
            val flag = campaign.indexOf("StartModule.gameRecoveryFailed = true", cap)
            val guard = campaign.indexOf("stopIsGameUnrecoverable(", cap)
            val throwAt = campaign.indexOf("throw InterruptedException(", cap)
            assertTrue(guard in cap until throwAt, "the pause flag is gated on stopIsGameUnrecoverable")
            assertTrue(flag in cap until throwAt, "the queue-pause flag is set before the stop throw")
        }

        @Test
        fun `the queue pauses on game-recovery failure regardless of stopOnError`() {
            val start = sourceFile("StartModule.kt").readText()
            // Inside the queue result evaluation's else-branch, the gameRecoveryFailed check must come
            // before the stopOnError branch so it wins regardless of the user's stopOnError setting.
            val elseBranch = start.indexOf("// Error, timeout, connection error, etc.")
            val recoveryCheck = start.indexOf("if (gameRecoveryFailed)", elseBranch)
            val stopOnErrorCheck = start.indexOf("if (stopOnError)", elseBranch)
            assertTrue(recoveryCheck in elseBranch until stopOnErrorCheck, "the recovery-failure pause is checked before stopOnError")
            assertTrue(start.indexOf("break", recoveryCheck) < stopOnErrorCheck, "a recovery failure breaks the queue loop")
        }

        @Test
        fun `the queue-pause flag is reset at the start of every session`() {
            val start = sourceFile("StartModule.kt").readText()
            val reset = start.indexOf("Reset queue control flags at the start of every new session.")
            assertTrue(reset > 0)
            assertTrue(start.indexOf("gameRecoveryFailed = false", reset) in reset until (reset + 400), "the flag is reset alongside the other queue flags")
        }
    }

    @Nested
    @DisplayName("own screen in front")
    inner class OwnUiInFront {
        private val main by lazy { sourceFile("MainActivity.kt").readText().replace("\r\n", "\n") }
        private val campaign by lazy { sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n") }
        private val racing by lazy { sourceFile("bot/Racing.kt").readText().replace("\r\n", "\n") }
        private val unityCup by lazy { sourceFile("bot/campaigns/UnityCup.kt").readText().replace("\r\n", "\n") }
        private val game by lazy { sourceFile("bot/Game.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `waits instead of tapping while our screen is in front`() {
            var waits = 0
            assertTrue(holdForOwnUi(ownUiInFront = true) { waits++ })
            assertEquals(1, waits)
        }

        @Test
        fun `taps as before while the game is in front`() {
            var waits = 0
            assertFalse(holdForOwnUi(ownUiInFront = false) { waits++ })
            assertEquals(0, waits)
        }

        @Test
        fun `the flag follows the activity lifecycle and a new process starts with the game assumed in front`() {
            val onResume = main.substringAfter("override fun onResume()").substringBefore("}")
            val onPause = main.substringAfter("override fun onPause()").substringBefore("}")
            assertTrue("OwnUiForeground.resumed = true" in onResume)
            assertTrue("OwnUiForeground.resumed = false" in onPause)
            assertTrue(onPause.indexOf("OwnUiForeground.resumed = false") < onPause.indexOf("super.onPause()"), "cleared before the pause completes")
            assertTrue("OwnUiForeground.resumed = true" !in main.substringAfter("override fun onPause()"), "nothing sets it after onPause")
            val flag = sourceFile("bot/UnknownScreenRecovery.kt").readText().replace("\r\n", "\n").substringAfter("internal object OwnUiForeground {")
            assertTrue(flag.substringBefore("}").contains("var resumed: Boolean = false"), "a new process never holds input")
            val writers = Regex("""OwnUiForeground\.resumed = """)
            assertEquals(2, writers.findAll(main).count())
            assertEquals(0, writers.findAll(game + campaign + racing + unityCup).count(), "only the activity lifecycle writes it")
        }

        @Test
        fun `the game helper reads the lifecycle flag`() {
            val helper = game.substringAfter("fun holdBlindInputForOwnUi(): Boolean {").substringBefore("\n    }\n")
            assertTrue("holdForOwnUi(OwnUiForeground.resumed)" in helper)
            assertTrue("wait(" in helper, "the hold waits, keeping the stall watchdog's heartbeat")
        }

        @Test
        fun `an unknown tick with our screen in front neither counts nor recovers`() {
            val hold = campaign.indexOf("} else if (game.holdBlindInputForOwnUi()) {")
            val increment = campaign.indexOf("consecutiveUnknownScreenCount++")
            assertTrue(hold in 0 until increment, "held before the streak advances")
            val branch = campaign.substring(hold, campaign.indexOf("} else", hold + 1))
            assertTrue("detectedKnownScreen = false" in branch, "the streak is neither reset nor advanced")
            assertTrue("recoverFromUnknownScreen" !in branch && "tap(" !in branch)
            assertEquals(1, Regex("""recoverFromUnknownScreen\(consecutiveUnknownScreenCount\)""").findAll(campaign).count())
            assertTrue(campaign.indexOf("recoverFromUnknownScreen(consecutiveUnknownScreenCount)") > increment, "recovery runs only on a counted tick")
        }

        @Test
        fun `race-loop blind taps are held too`() {
            assertTrue(racing.contains("if (!game.holdBlindInputForOwnUi()) {\n                        Log.d(TAG, \"[DEBUG] runRaceWithRetries:: No components detected."))
            val timedFallback = "val heldMs = game.heldMsForOwnUi()\n                    if (heldMs != null) startTime += heldMs else game.tap(350.0, 750.0, taps = 3)"
            assertTrue(racing.contains(timedFallback))
            assertTrue(unityCup.contains(timedFallback))
            val literalTaps = Regex("""game\.tap\(\d""")
            assertEquals(2, literalTaps.findAll(racing).count())
            assertEquals(1, literalTaps.findAll(unityCup).count())
        }
    }

    @Nested
    @DisplayName("time held for our own screen stays off the race caps")
    inner class HeldTimeOffRaceCaps {
        private val racing by lazy { sourceFile("bot/Racing.kt").readText().replace("\r\n", "\n") }
        private val unityCup by lazy { sourceFile("bot/campaigns/UnityCup.kt").readText().replace("\r\n", "\n") }

        /** One pass of a capped loop's fallback: [heldForMs] of our own screen in front, the loop's start moved by what was held. */
        private fun elapsedAfterFallback(heldForMs: Long?): Long {
            var now = 1_000L
            var startTime = now
            val heldMs = heldMsForOwnUi({ now }) {
                if (heldForMs == null) return@heldMsForOwnUi false
                now += heldForMs
                true
            }
            if (heldMs != null) startTime += heldMs else now += 50
            return now - startTime
        }

        @Test
        fun `a hold longer than the 30 s finalize cap does not end the race`() {
            val maxTimeMs = 30_000L
            assertTrue(elapsedAfterFallback(heldForMs = 5 * 60_000L) < maxTimeMs)
            val loop = racing.substringAfter("fun finalizeRaceResults(").substringBefore("return false\n    }")
            assertTrue(loop.contains("var startTime: Long = System.currentTimeMillis()"))
            assertTrue(loop.contains("while (System.currentTimeMillis() - startTime < maxTimeMs)"))
            assertTrue(loop.contains("if (heldMs != null) startTime += heldMs else game.tap(350.0, 750.0, taps = 3)"))
        }

        @Test
        fun `a hold longer than the 120 s Unity Cup cap does not abort the race event`() {
            val executionTimeThresholdMs = 120_000L
            assertTrue(elapsedAfterFallback(heldForMs = 10 * 60_000L) <= executionTimeThresholdMs)
            val loop = unityCup.substringAfter("val executionTimeThresholdMs = 120000").substringBefore("\n    }\n")
            assertTrue(loop.contains("var startTime = System.currentTimeMillis()"))
            assertTrue(loop.contains("System.currentTimeMillis() - startTime > executionTimeThresholdMs"))
            assertTrue(loop.contains("if (heldMs != null) startTime += heldMs else game.tap(350.0, 750.0, taps = 3)"))
        }

        @Test
        fun `with the game in front the fallback taps and its time still counts`() {
            assertEquals(null, heldMsForOwnUi({ 0L }) { false })
            assertEquals(50L, elapsedAfterFallback(heldForMs = null))
        }

        @Test
        fun `the held time is measured across the hold on the loops' own clock`() {
            var now = 10L
            assertEquals(2_000L, heldMsForOwnUi({ now }) { now += 2_000L; true })
            val game = sourceFile("bot/Game.kt").readText().replace("\r\n", "\n")
            assertTrue(game.contains("fun heldMsForOwnUi(): Long? = heldMsForOwnUi(System::currentTimeMillis) { holdBlindInputForOwnUi() }"), "the caps run on System.currentTimeMillis")
        }
    }

    @Nested
    @DisplayName("settleAfterShadeDismiss")
    inner class ShadeDismissSettle {
        private fun settle(dispatched: Boolean): List<String> {
            val calls = mutableListOf<String>()
            settleAfterShadeDismiss(dispatched, waitForShadeClose = { calls += "shade" }, waitForLoading = { calls += "loading" })
            return calls
        }

        @Test
        fun `a dismissal the system did not perform skips the shade wait but still waits out loading`() {
            assertEquals(listOf("loading"), settle(dispatched = false))
        }

        @Test
        fun `a performed dismissal keeps the shade wait`() {
            assertEquals(listOf("shade"), settle(dispatched = true))
        }

        @Test
        fun `the shade dismissal settles through it, with the half-second wait only for a performed dismissal`() {
            val body = sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n")
                .substringAfter("protected fun dismissNotificationShade(reason: String) {").substringBefore("\n    }\n")
            assertTrue(body.contains("settleAfterShadeDismiss(dispatched, waitForShadeClose = { game.wait(0.5) }, waitForLoading = { game.waitForLoading() })"))
            assertEquals(1, Regex("""game\.wait\(0\.5\)""").findAll(body).count(), "no unconditional wait besides the performed-dismissal one")
        }
    }

    private fun sourceFile(relative: String): File = File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
