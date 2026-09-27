package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.CareerLaunchNavigator.LaunchScreenState
import com.steve1316.uma_android_automation.bot.ConnectionOutageBudget
import com.steve1316.uma_android_automation.components.ButtonCloseWide
import com.steve1316.uma_android_automation.components.ButtonTitleScreen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * How a between-run navigation gets the game back by itself: Title Screen on a Session Error and
 * one relaunch, both only with no career in flight. The decisions are pure ([BetweenRunRecovery] and
 * the dialog planner) and are played here against scripted screens; the navigator's use of them is
 * pinned by source guards, since the navigator needs a live screen.
 */
@DisplayName("Getting the game back between runs")
class BetweenRunRecoveryTest {
    private fun recovery(
        coldStartOnHome: Boolean = false,
        previousCareerComplete: Boolean = false,
        finalizeToHome: Boolean = false,
        campaignOwnsCareer: Boolean = false,
    ) = BetweenRunRecovery(coldStartOnHome, previousCareerComplete, finalizeToHome, campaignOwnsCareer)

    @Nested
    @DisplayName("no career in flight")
    inner class NoCareer {
        @Test
        fun `a cold Start on Home has none until Start Career or Continue Career`() {
            val r = recovery(coldStartOnHome = true)
            assertTrue(r.noCareerInFlight(careerLaunchInitiated = false))
            assertFalse(r.noCareerInFlight(careerLaunchInitiated = true))
            r.onScreen(LaunchScreenState.CONTINUE_CAREER_DIALOG)
            assertFalse(r.noCareerInFlight(careerLaunchInitiated = false))
            r.onScreen(LaunchScreenState.HOME_SCREEN)
            assertFalse(r.noCareerInFlight(careerLaunchInitiated = false), "Continue Career is never forgotten")
        }

        @Test
        fun `a finished career has none only once its end screens have reached Home`() {
            val r = recovery(previousCareerComplete = true)
            assertFalse(r.noCareerInFlight(false))
            for (state in BetweenRunRecovery.CAREER_END_STATES) {
                r.onScreen(state)
                assertFalse(r.noCareerInFlight(false), "$state")
            }
            r.onScreen(LaunchScreenState.HOME_SCREEN)
            assertTrue(r.noCareerInFlight(false))
            r.onScreen(LaunchScreenState.SPARKS_SCREEN)
            assertFalse(r.noCareerInFlight(false), "a career-end screen after Home counts again")
        }

        @Test
        fun `an unfinished career, the final pass to Home and the campaign's own re-entry never have none`() {
            val unfinished = recovery()
            unfinished.onScreen(LaunchScreenState.HOME_SCREEN)
            assertFalse(unfinished.noCareerInFlight(false))
            for (r in listOf(
                recovery(coldStartOnHome = true, finalizeToHome = true),
                recovery(previousCareerComplete = true, finalizeToHome = true),
                recovery(coldStartOnHome = true, campaignOwnsCareer = true),
                recovery(previousCareerComplete = true, campaignOwnsCareer = true),
            )) {
                r.onScreen(LaunchScreenState.HOME_SCREEN)
                assertFalse(r.noCareerInFlight(false))
            }
        }

        @Test
        fun `Title Screen is allowed once per navigation`() {
            val r = recovery(coldStartOnHome = true)
            assertTrue(r.mayTapTitleScreen(false))
            r.tappedTitleScreen()
            assertFalse(r.mayTapTitleScreen(false))
        }
    }

    @Nested
    @DisplayName("the unknown-screen limit")
    inner class UnknownLimit {
        @Test
        fun `one relaunch with no career in flight, then the game is unrecoverable`() {
            val r = recovery(coldStartOnHome = true)
            assertEquals(UnknownScreenLimitStep.RELAUNCH, r.onUnknownScreenLimit(false))
            assertTrue(r.relaunched)
            repeat(3) { assertEquals(UnknownScreenLimitStep.GAME_UNRECOVERABLE, r.onUnknownScreenLimit(false)) }
        }

        @Test
        fun `with a career in flight it stops as before and relaunches nothing`() {
            val inFlight =
                listOf(
                    recovery(),
                    recovery(previousCareerComplete = true).apply { onScreen(LaunchScreenState.SPARK_SELECTION_PAGER) },
                    recovery(previousCareerComplete = true, finalizeToHome = true).apply { onScreen(LaunchScreenState.HOME_SCREEN) },
                    recovery(coldStartOnHome = true, campaignOwnsCareer = true),
                    recovery(coldStartOnHome = true).apply { onScreen(LaunchScreenState.CONTINUE_CAREER_DIALOG) },
                )
            for (r in inFlight) {
                assertEquals(UnknownScreenLimitStep.STOP, r.onUnknownScreenLimit(false))
                assertFalse(r.relaunched)
            }
            assertEquals(UnknownScreenLimitStep.STOP, recovery(coldStartOnHome = true).onUnknownScreenLimit(careerLaunchInitiated = true))
        }

        @Test
        fun `the limit is raised only while the game loads its way back, until Home`() {
            val r = recovery(coldStartOnHome = true)
            assertEquals(5, r.unknownScreenLimit(5))
            r.tappedTitleScreen()
            assertEquals(BetweenRunRecovery.COMING_BACK_UNKNOWN_LIMIT, r.unknownScreenLimit(5))
            r.onScreen(LaunchScreenState.HOME_SCREEN)
            assertEquals(5, r.unknownScreenLimit(5))
            r.tappedToStart(0L)
            assertEquals(BetweenRunRecovery.COMING_BACK_UNKNOWN_LIMIT, r.unknownScreenLimit(5))
        }

        @Test
        fun `the title is tapped again only after the cooldown`() {
            val r = recovery()
            assertTrue(r.mayTapToStart(1_000L))
            r.tappedToStart(1_000L)
            assertFalse(r.mayTapToStart(1_000L + BetweenRunRecovery.TAP_TO_START_COOLDOWN_MS - 1))
            assertTrue(r.mayTapToStart(1_000L + BetweenRunRecovery.TAP_TO_START_COOLDOWN_MS))
        }
    }

    /** One scripted screen: a recognised state, a titled dialog, the title screen, or an unknown frame. */
    private sealed class Screen {
        data class Known(val state: LaunchScreenState) : Screen()

        data class Dialog(val dialog: BetweenRunDialog) : Screen()

        data object Title : Screen()

        data object Unknown : Screen()
    }

    private fun unknowns(n: Int) = List(n) { Screen.Unknown }

    private val home = Screen.Known(LaunchScreenState.HOME_SCREEN)
    private val sessionError = Screen.Dialog(BetweenRunDialog.SESSION_ERROR)
    private val launched = Screen.Known(LaunchScreenState.ACTIVE_TRAINING_MENU)

    private data class Outcome(val end: String, val taps: List<String>, val relaunches: Int)

    /**
     * Plays [screens] through the same decisions the navigator makes each look: the dialog planner
     * gated by [BetweenRunRecovery.mayTapTitleScreen], the title tap, and the unknown-screen limit,
     * starting the launch over (fresh latch, fresh unknown count) after Title Screen or a relaunch.
     */
    private fun play(r: BetweenRunRecovery, screens: List<Screen>): Outcome {
        val taps = mutableListOf<String>()
        var relaunches = 0
        var careerLaunchInitiated = false
        var unknownsInARow = 0
        var nowMs = 0L
        val budget = ConnectionOutageBudget { nowMs }
        for (screen in screens) {
            nowMs += 4_000L
            when (screen) {
                is Screen.Unknown -> {
                    unknownsInARow++
                    if (unknownsInARow < r.unknownScreenLimit(5)) continue
                    when (r.onUnknownScreenLimit(careerLaunchInitiated)) {
                        UnknownScreenLimitStep.RELAUNCH -> {
                            relaunches++
                            careerLaunchInitiated = false
                            unknownsInARow = 0
                        }
                        UnknownScreenLimitStep.GAME_UNRECOVERABLE -> return Outcome("GAME_UNRECOVERABLE", taps, relaunches)
                        UnknownScreenLimitStep.STOP -> return Outcome("STUCK_ON_SCREEN", taps, relaunches)
                    }
                }
                is Screen.Known -> {
                    unknownsInARow = 0
                    r.onScreen(screen.state)
                    if (screen.state == LaunchScreenState.PRE_RUN_CONFIRMATION) careerLaunchInitiated = true
                    if (screen.state == LaunchScreenState.ACTIVE_TRAINING_MENU) return Outcome("SUCCESS", taps, relaunches)
                }
                is Screen.Title -> {
                    unknownsInARow = 0
                    if (r.mayTapToStart(nowMs)) {
                        taps += "TAP TO START"
                        r.tappedToStart(nowMs)
                    }
                }
                is Screen.Dialog -> {
                    unknownsInARow = 0
                    val step = planBetweenRunDialog(screen.dialog, budget::onError, StartModule.NAV_DEADLINE_MS, r.mayTapTitleScreen(careerLaunchInitiated))
                    if (step is BetweenRunDialogStep.Fail) return Outcome(step.reasonKey, taps, relaunches)
                    taps += step.taps.first()::class.simpleName!!
                    if (step is BetweenRunDialogStep.ReturnToTitle) {
                        r.tappedTitleScreen()
                        careerLaunchInitiated = false
                    }
                }
            }
        }
        return Outcome("SCRIPT_ENDED", taps, relaunches)
    }

    private val titleScreenTap = ButtonTitleScreen::class.simpleName!!
    private val closeNotices = ButtonCloseWide::class.simpleName!!

    /** A Session Error after CAREER, then the way a player follows it back to Home and on. */
    private val sessionErrorThenBack =
        listOf(home, sessionError, Screen.Title, Screen.Title) + unknowns(8) +
            listOf(Screen.Dialog(BetweenRunDialog.NOTICES), home, Screen.Known(LaunchScreenState.SCENARIO_SELECT), Screen.Known(LaunchScreenState.PRE_RUN_CONFIRMATION), launched)

    @Nested
    @DisplayName("a Session Error")
    inner class SessionError {
        @Test
        fun `with no career in flight goes back through the title screen to Home and launches`() {
            for (r in listOf(recovery(coldStartOnHome = true), recovery(previousCareerComplete = true))) {
                val out = play(r, listOf(Screen.Known(LaunchScreenState.CAREER_COMPLETE_DIALOG)) + sessionErrorThenBack)
                assertEquals(Outcome("SUCCESS", listOf(titleScreenTap, "TAP TO START", closeNotices), 0), out)
            }
        }

        @Test
        fun `with a career in flight stops with SESSION_EXPIRED and taps nothing`() {
            val inFlight =
                listOf(
                    recovery() to listOf(home, sessionError),
                    recovery(previousCareerComplete = true) to listOf(Screen.Known(LaunchScreenState.SPARKS_SCREEN), sessionError),
                    recovery(coldStartOnHome = true) to listOf(home, Screen.Known(LaunchScreenState.CONTINUE_CAREER_DIALOG), sessionError),
                    recovery(coldStartOnHome = true) to listOf(home, Screen.Known(LaunchScreenState.PRE_RUN_CONFIRMATION), sessionError),
                    recovery(coldStartOnHome = true, finalizeToHome = true) to listOf(sessionError),
                    recovery(coldStartOnHome = true, campaignOwnsCareer = true) to listOf(home, sessionError),
                )
            for ((r, screens) in inFlight) assertEquals(Outcome("SESSION_EXPIRED", emptyList(), 0), play(r, screens))
        }

        @Test
        fun `a second one in the same navigation stops`() {
            val out = play(recovery(coldStartOnHome = true), listOf(home, sessionError, Screen.Title, home, sessionError))
            assertEquals(Outcome("SESSION_EXPIRED", listOf(titleScreenTap, "TAP TO START"), 0), out)
        }
    }

    @Nested
    @DisplayName("the relaunch")
    inner class Relaunch {
        @Test
        fun `is tried once, and a game that still shows nothing known is unrecoverable`() {
            val out = play(recovery(coldStartOnHome = true), listOf(home) + unknowns(5) + unknowns(BetweenRunRecovery.COMING_BACK_UNKNOWN_LIMIT))
            assertEquals(Outcome("GAME_UNRECOVERABLE", emptyList(), 1), out)
        }

        @Test
        fun `brings a game back through its title to a launch`() {
            val screens = listOf(Screen.Known(LaunchScreenState.CAREER_COMPLETE_DIALOG), home) + unknowns(5) + unknowns(20) + listOf(Screen.Title, home, launched)
            assertEquals(Outcome("SUCCESS", listOf("TAP TO START"), 1), play(recovery(previousCareerComplete = true), screens))
        }

        @Test
        fun `never happens on the career-end screens, on the final pass or after Start Career`() {
            val cases =
                listOf(
                    recovery(previousCareerComplete = true) to listOf(Screen.Known(LaunchScreenState.SPARK_SELECTION_PAGER)),
                    recovery(previousCareerComplete = true) to listOf(home, Screen.Known(LaunchScreenState.CAREER_SUMMARY)),
                    recovery(previousCareerComplete = true, finalizeToHome = true) to listOf(Screen.Known(LaunchScreenState.CAREER_COMPLETE_DIALOG)),
                    recovery(coldStartOnHome = true) to listOf(home, Screen.Known(LaunchScreenState.PRE_RUN_CONFIRMATION)),
                )
            for ((r, screens) in cases) assertEquals(Outcome("STUCK_ON_SCREEN", emptyList(), 0), play(r, screens + unknowns(5)))
        }
    }

    @Nested
    @DisplayName("navigator and queue wiring")
    inner class Wiring {
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

        private val navigator by lazy { source("$main/CareerLaunchNavigator.kt") }
        private val startModule by lazy { source("$main/StartModule.kt") }

        private fun body(src: String, signature: String): String {
            val start = src.indexOf(signature)
            assertTrue(start >= 0, "$signature not found")
            val end = Regex("\n    (/\\*\\*|// |(private |internal )?fun )").find(src, start + signature.length)?.range?.first ?: src.length
            return src.substring(start, end)
        }

        private fun count(src: String, literal: String) = Regex(Regex.escape(literal)).findAll(src).count()

        @Test
        fun `the game is relaunched only at the unknown-screen limit, once, and a failed relaunch is unrecoverable`() {
            assertEquals(1, count(navigator, "restartGame("))
            val limit = navigator.substring(navigator.indexOf("when (betweenRunRecovery.onUnknownScreenLimit(careerLaunchInitiated)) {"))
            val relaunch = limit.substring(limit.indexOf("UnknownScreenLimitStep.RELAUNCH -> {"), limit.indexOf("UnknownScreenLimitStep.GAME_UNRECOVERABLE ->"))
            assertTrue(relaunch.contains("if (tempGame?.restartGame() == true) {\n                                return startLaunchOver("))
            assertTrue(relaunch.contains("return gameUnrecoverable(currentState, "))
            val limitAt = navigator.indexOf("val unknownLimit = betweenRunRecovery.unknownScreenLimit(MAX_CONSECUTIVE_UNKNOWNS)\n                if (consecutiveUnknowns >= unknownLimit) {")
            assertTrue(limitAt > navigator.indexOf("consecutiveUnknowns++"))
            // No forced rebinds while the game loads its way back: its splash screens are unknown by design.
            assertTrue(navigator.contains("if (consecutiveUnknowns >= 2 && !betweenRunRecovery.gameComingBack) {\n                    rebindAccessibility()"))
            val decided = navigator.indexOf("when (betweenRunRecovery.onUnknownScreenLimit(")
            assertTrue(decided in navigator.indexOf("consecutiveUnknowns++") until navigator.indexOf("captureFailureScreenshot(\"unknown_state\")"))
            val unrecoverable = body(navigator, "private fun gameUnrecoverable(")
            assertTrue(unrecoverable.contains("isRecoverable = false"))
            assertTrue(unrecoverable.contains("reasonKey = \"GAME_UNRECOVERABLE\""))
        }

        @Test
        fun `the launch starts over only after Title Screen or a relaunch, keeping the deadline, budget and recoveries`() {
            assertEquals(3, count(navigator, "startLaunchOver("), "the definition and its two callers")
            assertEquals(1, count(navigator, "TransitionResult.StartLaunchOver ->"))
            assertEquals(1, count(navigator, "restartingLaunch = true"))
            assertTrue(body(navigator, "private fun startLaunchOver(").contains("restartingLaunch = true\n        return navigate("))
            val navigate = body(navigator, "fun navigate(")
            val kept =
                "if (restartingLaunch) {\n            restartingLaunch = false\n        } else {\n            navigationStartedAtMs = System.currentTimeMillis()\n" +
                    "            betweenRunConnectionBudget = ConnectionOutageBudget()\n" +
                    "            betweenRunRecovery = BetweenRunRecovery(coldStartOnHome, previousCareerComplete, finalizeToHome, " +
                    "campaignOwnsCareer = resumeInProgressCareer || liveGameAttached)\n        }"
            val at = navigate.indexOf(kept)
            assertTrue(at >= 0)
            val latches = listOf("careerLaunchInitiated = false", "legacyAutoSelectAlreadyDone = false", "supportDeckPreBorrowVerified = false", "launchFlowEntered = false")
            for (reset in latches + "LaunchTransactionGate.beginLaunch(") {
                assertTrue(navigate.indexOf(reset) > at, "$reset still resets on the new pass")
            }
            assertEquals(1, count(navigator, "\n            betweenRunRecovery = BetweenRunRecovery("))
            // Before any navigate() the recovery allows nothing.
            val closed = "BetweenRunRecovery(coldStartOnHome = false, previousCareerComplete = false, finalizeToHome = false, campaignOwnsCareer = true)"
            assertTrue(navigator.contains("private var betweenRunRecovery = $closed"))
        }

        @Test
        fun `every recognised screen reaches the recovery and the title is read only between runs, under no dialog`() {
            val navigate = body(navigator, "fun navigate(")
            assertTrue(navigate.indexOf("betweenRunRecovery.onScreen(detectedState)") in navigate.indexOf("launchFlowEntered = true") until navigate.indexOf("consecutiveUnknowns++"))
            val detect = body(navigator, "private fun detectScreenState(")
            val gate = "!careerLaunchInitiated && !resumeInProgressCareerMode && !liveGameAttached && isTitleScreen(bitmap) && !DialogUtils.check(iu, sourceBitmap = bitmap)"
            val title = detect.indexOf("if ($gate) {\n            return LaunchScreenState.TITLE_SCREEN")
            assertTrue(title > detect.indexOf("readBetweenRunDialog("))
            assertTrue(title < detect.indexOf("if (ButtonNext.check(iu, sourceBitmap = bitmap) ||"))
            assertTrue(navigator.contains("LaunchScreenState.TITLE_SCREEN -> handleTitleScreen()"))
            assertTrue(navigator.contains("liveGameAttached = true\n    }"))
        }

        @Test
        fun `the title while the game logs in is a bounded wait, not a stuck screen`() {
            val navigate = body(navigator, "fun navigate(")
            assertTrue(navigate.contains("val titleLoggingIn = detectedState == LaunchScreenState.TITLE_SCREEN && betweenRunRecovery.gameComingBack\n"))
            // Exempt from the stuck-screen rebind and stop and from the no-progress stop, like a cutscene or a connection ride-out.
            assertEquals(2, count(navigate, "detectedState != LaunchScreenState.TAP_TO_CONTINUE &&\n                    !titleLoggingIn &&\n"))
            // Bounded by the come-back limit instead, and by the navigation deadline around it.
            val bound = navigate.substring(navigate.indexOf("if (titleLoggingIn) {"))
            assertTrue(bound.startsWith("if (titleLoggingIn) {\n                    titleLoginLooks++\n"))
            // One force-rebind partway, for a title whose taps stopped landing, and the stop carries whether it was issued.
            val rebind = bound.indexOf("if (titleLoginLooks == TITLE_LOGIN_REBIND_AT) {")
            assertTrue(rebind in 0 until bound.indexOf("if (titleLoginLooks >= BetweenRunRecovery.COMING_BACK_UNKNOWN_LIMIT) {"))
            assertTrue(bound.substring(rebind).contains("titleScreenRebindIssued = rebindAccessibility()\n"))
            assertTrue(navigator.contains("private const val TITLE_LOGIN_REBIND_AT = 20\n"))
            assertTrue(20 < BetweenRunRecovery.COMING_BACK_UNKNOWN_LIMIT)
            assertTrue(bound.substringBefore("} else {\n                    titleLoginLooks = 0").contains("reasonKey = navigatorStuckKey(navRepairRefused, titleScreenRebindIssued),"))
            assertTrue(navigate.indexOf("titleScreenRebindIssued = false") in 0 until navigate.indexOf("for (attempt in 0 until MAX_DETECTION_ATTEMPTS)"))
        }

        @Test
        fun `the title handler taps only the TAP TO START text, and only after the cooldown`() {
            val handler = body(navigator, "private fun handleTitleScreen(")
            assertFalse(Regex("\\.click\\(|findAndTapImage|\\.close\\(|\\.ok\\(|Button[A-Z]").containsMatchIn(handler))
            assertEquals(1, count(handler, "CoordinateTap.tap("))
            assertTrue(handler.contains("CoordinateTap.tap(gestureUtils, TitleScreenProbe.TAP_TO_START_X, TitleScreenProbe.TAP_TO_START_Y, \"title_tap_to_start\")"))
            assertTrue(handler.indexOf("if (!betweenRunRecovery.mayTapToStart(now)) {") in 0 until handler.indexOf("CoordinateTap.tap("))
            assertTrue(handler.indexOf("betweenRunRecovery.tappedToStart(now)") > handler.indexOf("CoordinateTap.tap("))
        }

        @Test
        fun `the recovery names no game control`() {
            for (file in listOf("$main/BetweenRunRecovery.kt", "$main/utils/TitleScreenProbe.kt")) {
                assertFalse(Regex("\\b(Button|Dialog)[A-Z]\\w*|\\.click\\(|\\btap\\(").containsMatchIn(source(file)), file)
            }
        }

        @Test
        fun `only the queue's cold Start that re-enters no career counts as career-free`() {
            assertEquals(1, count(startModule, "coldStartOnHome = !resumeReEntersCareer"))
            assertTrue(startModule.contains("val navResult = navigateWithDeadline(coldStartReuse, coldStartNavigator, coldStartOnHome = !resumeReEntersCareer, careerInFlight = resumeReEntersCareer)"))
            assertTrue(startModule.contains("val reEnter = saved.phase == PHASE_CAREER\n                        resumeReEntersCareer = reEnter"))
            assertEquals(1, count(startModule, "resumeReEntersCareer = reEnter"))
            assertTrue(startModule.indexOf("var resumeReEntersCareer = false") in 0 until startModule.indexOf("resumeReEntersCareer = reEnter"))
            assertTrue(startModule.contains("val navResult = navigateWithDeadline(nextReuse, previousCareerComplete = careerFinished, careerInFlight = !careerFinished)"))
            val passOn = "previousCareerComplete = previousCareerComplete, coldStartOnHome = coldStartOnHome, careerInFlight = careerInFlight)"
            assertTrue(startModule.contains("navigator.navigate(reuseLastLaunchSetup, finalizeToHome, $passOn"))
        }
    }
}
