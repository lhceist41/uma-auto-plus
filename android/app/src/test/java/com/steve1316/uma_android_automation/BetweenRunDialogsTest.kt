package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.ConnectionOutageBudget
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonCloseWide
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.components.ButtonRetry
import com.steve1316.uma_android_automation.components.ButtonTitleScreen
import com.steve1316.uma_android_automation.components.DialogAgeConfirmation
import com.steve1316.uma_android_automation.components.DialogConnectionError
import com.steve1316.uma_android_automation.components.DialogDataDownload
import com.steve1316.uma_android_automation.components.DialogDateChanged
import com.steve1316.uma_android_automation.components.DialogDownloadError
import com.steve1316.uma_android_automation.components.DialogFollowTrainer
import com.steve1316.uma_android_automation.components.DialogNotices
import com.steve1316.uma_android_automation.components.DialogPurchaseCarats
import com.steve1316.uma_android_automation.components.DialogSessionError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The game dialogs the career-launch navigator handles between runs. Detection and the decision are
 * pure (a fake title source and a fake clock drive them); the navigator's use of them is pinned by
 * source guards, since the navigator needs a live screen.
 */
@DisplayName("Dialogs between runs")
class BetweenRunDialogsTest {
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

    private fun body(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue(start >= 0, "$signature not found")
        val end = Regex("\n    (/\\*\\*|// |(private |internal )?fun )").find(src, start + signature.length)?.range?.first ?: src.length
        return src.substring(start, end)
    }

    private val nothing: () -> ConnectionOutageBudget.Decision = { throw AssertionError("the connection budget must not be consulted") }

    private fun plan(dialog: BetweenRunDialog) = planBetweenRunDialog(dialog, nothing, StartModule.NAV_DEADLINE_MS)

    private val closing = listOf(BetweenRunDialog.NOTICES, BetweenRunDialog.DATE_CHANGED, BetweenRunDialog.FOLLOW_TRAINER)
    private val failing = listOf(BetweenRunDialog.SESSION_ERROR, BetweenRunDialog.PURCHASE_CARATS, BetweenRunDialog.AGE_CONFIRMATION)

    @Nested
    @DisplayName("reading the dialog")
    inner class Reading {
        @Test
        fun `every listed title is handled, by the title of the game's own dialog object`() {
            val objects =
                mapOf(
                    BetweenRunDialog.NOTICES to DialogNotices.title,
                    BetweenRunDialog.DATE_CHANGED to DialogDateChanged.title,
                    BetweenRunDialog.FOLLOW_TRAINER to DialogFollowTrainer.title,
                    BetweenRunDialog.CONNECTION_ERROR to DialogConnectionError.title,
                    BetweenRunDialog.DOWNLOAD_ERROR to DialogDownloadError.title,
                    BetweenRunDialog.DATA_DOWNLOAD to DialogDataDownload.title,
                    BetweenRunDialog.SESSION_ERROR to DialogSessionError.title,
                    BetweenRunDialog.PURCHASE_CARATS to DialogPurchaseCarats.title,
                    BetweenRunDialog.AGE_CONFIRMATION to DialogAgeConfirmation.title,
                )
            assertEquals(BetweenRunDialog.entries.toSet(), objects.keys)
            for ((dialog, title) in objects) {
                assertEquals(title, dialog.title)
                assertEquals(dialog, readBetweenRunDialog(false, { true }, { title }))
            }
        }

        @Test
        fun `any other title, no title or a failed read is left to the rest of detection`() {
            for (title in listOf("Restore TP", "Complete Career", "Umamusume Details", "notices", "", null)) {
                assertNull(readBetweenRunDialog(false, { true }, { title }), "$title")
            }
            assertNull(readBetweenRunDialog(false, { true }, { throw IllegalStateException("capture died") }))
        }

        @Test
        fun `an interrupt from the title read still reaches the navigation deadline`() {
            assertThrows(InterruptedException::class.java) { readBetweenRunDialog(false, { true }, { throw InterruptedException() }) }
        }

        @Test
        fun `the title is read only under a banner, and nothing is read once the career has started`() {
            var titleReads = 0
            var bannerChecks = 0

            fun title(text: String): String {
                titleReads++
                return text
            }
            assertNull(readBetweenRunDialog(false, { bannerChecks++ < 0 }, { title("Notices") }))
            assertEquals(1, bannerChecks)
            assertEquals(0, titleReads)
            assertNull(readBetweenRunDialog(true, { bannerChecks++ >= 0 }, { title("Connection Error") }))
            assertEquals(1, bannerChecks)
            assertEquals(0, titleReads)
            assertEquals(BetweenRunDialog.NOTICES, readBetweenRunDialog(false, { bannerChecks++ >= 0 }, { title("Notices") }))
            assertEquals(2, bannerChecks)
            assertEquals(1, titleReads)
        }
    }

    @Nested
    @DisplayName("what each dialog does")
    inner class Steps {
        @Test
        fun `Notices, Date Changed and Follow Trainer are dismissed with their own button`() {
            assertEquals(BetweenRunDialogStep.CloseNotices, plan(BetweenRunDialog.NOTICES))
            assertEquals(listOf(ButtonCloseWide, ButtonClose), BetweenRunDialogStep.CloseNotices.taps)
            assertEquals(BetweenRunDialogStep.ConfirmDateChanged, plan(BetweenRunDialog.DATE_CHANGED))
            assertEquals(listOf(ButtonOk), BetweenRunDialogStep.ConfirmDateChanged.taps)
            assertEquals(BetweenRunDialogStep.CancelFollowTrainer, plan(BetweenRunDialog.FOLLOW_TRAINER))
            assertEquals(listOf(ButtonCancel), BetweenRunDialogStep.CancelFollowTrainer.taps)
        }

        @Test
        fun `Session Error and the purchase screens stop with their own key and tap nothing`() {
            assertEquals(BetweenRunDialogStep.Fail("SESSION_EXPIRED"), plan(BetweenRunDialog.SESSION_ERROR))
            assertEquals(BetweenRunDialogStep.Fail("PURCHASE_PROMPT"), plan(BetweenRunDialog.PURCHASE_CARATS))
            assertEquals(BetweenRunDialogStep.Fail("PURCHASE_PROMPT"), plan(BetweenRunDialog.AGE_CONFIRMATION))
            for (dialog in failing) assertTrue(plan(dialog).taps.isEmpty(), "$dialog")
        }

        @Test
        fun `a Session Error taps Title Screen only when the navigation allows it, and nothing else ever does`() {
            val allowed = planBetweenRunDialog(BetweenRunDialog.SESSION_ERROR, nothing, StartModule.NAV_DEADLINE_MS, mayReturnToTitle = true)
            assertEquals(BetweenRunDialogStep.ReturnToTitle, allowed)
            assertEquals(listOf(ButtonTitleScreen), allowed.taps)
            val retry = { ConnectionOutageBudget.Decision.Retry(30_000L, 1, 5_000L) }
            for (dialog in BetweenRunDialog.entries - BetweenRunDialog.SESSION_ERROR) {
                for (mayReturnToTitle in listOf(true, false)) {
                    val step = planBetweenRunDialog(dialog, retry, StartModule.NAV_DEADLINE_MS, mayReturnToTitle)
                    assertFalse(ButtonTitleScreen in step.taps, "$dialog")
                }
            }
        }

        @Test
        fun `a connection or download error retries, and Retry is its only tap`() {
            for (dialog in listOf(BetweenRunDialog.CONNECTION_ERROR, BetweenRunDialog.DOWNLOAD_ERROR)) {
                val step = planBetweenRunDialog(dialog, { ConnectionOutageBudget.Decision.Retry(30_000L, 2, 5_000L) }, StartModule.NAV_DEADLINE_MS)
                assertEquals(BetweenRunDialogStep.Retry(30_000L, 2), step)
                assertEquals(listOf(ButtonRetry), step.taps)
            }
        }

        @Test
        fun `no step can press anything but Close, OK on Date Changed or Data Download, Cancel or Retry`() {
            val taps = closing.flatMap { plan(it).taps } + failing.flatMap { plan(it).taps } + BetweenRunDialogStep.Retry(0L, 1).taps
            assertEquals(setOf(ButtonCloseWide, ButtonClose, ButtonOk, ButtonCancel, ButtonRetry), taps.toSet())
            val connection = listOf(BetweenRunDialog.CONNECTION_ERROR, BetweenRunDialog.DOWNLOAD_ERROR)
            assertEquals(listOf(BetweenRunDialog.DATE_CHANGED, BetweenRunDialog.DATA_DOWNLOAD), (BetweenRunDialog.entries - connection.toSet()).filter { ButtonOk in plan(it).taps })
        }
    }

    @Nested
    @DisplayName("a lost connection between runs")
    inner class Connection {
        private var nowMs = 0L
        private val budget = ConnectionOutageBudget { nowMs }

        /** Plays one navigation that meets [dialog] on every look, [spinnerMs] after each Retry. */
        private fun rideOut(dialog: BetweenRunDialog, deadlineMs: Long, spinnerMs: Long = 5_000L): Pair<List<BetweenRunDialogStep.Retry>, BetweenRunDialogStep> {
            val retries = mutableListOf<BetweenRunDialogStep.Retry>()
            repeat(1_000) {
                budget.beginIteration()
                val step = planBetweenRunDialog(dialog, budget::onError, deadlineMs - nowMs)
                budget.endIterationNormally()
                if (step !is BetweenRunDialogStep.Retry) return retries to step
                retries += step
                nowMs += step.waitMs + spinnerMs
            }
            throw AssertionError("never stopped")
        }

        @Test
        fun `it is ridden out on the budget's backoff, then stops with its own key before the navigation deadline`() {
            val (retries, end) = rideOut(BetweenRunDialog.CONNECTION_ERROR, StartModule.NAV_DEADLINE_MS)
            assertEquals(BetweenRunDialogStep.Fail("CONNECTION_LOST"), end)
            assertEquals(listOf(0L, 30_000L, 60_000L, 120_000L), retries.take(4).map { it.waitMs })
            assertEquals((1..retries.size).toList(), retries.map { it.attempt })
            assertTrue(nowMs < StartModule.NAV_DEADLINE_MS - CONNECTION_DEADLINE_MARGIN_MS + 5_000L, "stopped at $nowMs")
        }

        @Test
        fun `a download error does the same with its own key`() {
            assertEquals(BetweenRunDialogStep.Fail("DOWNLOAD_FAILED"), rideOut(BetweenRunDialog.DOWNLOAD_ERROR, StartModule.NAV_DEADLINE_MS).second)
        }

        @Test
        fun `with time left the whole outage budget is ridden out first`() {
            val (retries, end) = rideOut(BetweenRunDialog.CONNECTION_ERROR, Long.MAX_VALUE / 2)
            assertEquals(BetweenRunDialogStep.Fail("CONNECTION_LOST"), end)
            assertTrue(nowMs >= ConnectionOutageBudget.OUTAGE_BUDGET_MS, "gave up at $nowMs")
            assertTrue(retries.size > 4)
        }

        @Test
        fun `a wait that would run into the deadline margin stops instead`() {
            val wait = 30_000L
            val retry = { ConnectionOutageBudget.Decision.Retry(wait, 2, 1_000L) }
            assertEquals(BetweenRunDialogStep.Retry(wait, 2), planBetweenRunDialog(BetweenRunDialog.CONNECTION_ERROR, retry, wait + CONNECTION_DEADLINE_MARGIN_MS))
            assertEquals(BetweenRunDialogStep.Fail("CONNECTION_LOST"), planBetweenRunDialog(BetweenRunDialog.CONNECTION_ERROR, retry, wait + CONNECTION_DEADLINE_MARGIN_MS - 1))
        }

        @Test
        fun `a recognised screen in between ends the outage, so the next one starts over`() {
            budget.beginIteration()
            planBetweenRunDialog(BetweenRunDialog.CONNECTION_ERROR, budget::onError, StartModule.NAV_DEADLINE_MS)
            budget.endIterationNormally()
            nowMs += 40_000L
            budget.beginIteration()
            assertEquals(BetweenRunDialogStep.Retry(30_000L, 2), planBetweenRunDialog(BetweenRunDialog.CONNECTION_ERROR, budget::onError, StartModule.NAV_DEADLINE_MS))
            budget.endIterationNormally()
            budget.beginIteration()
            budget.endIterationNormally()
            budget.beginIteration()
            assertEquals(BetweenRunDialogStep.Retry(0L, 1), planBetweenRunDialog(BetweenRunDialog.CONNECTION_ERROR, budget::onError, StartModule.NAV_DEADLINE_MS))
        }
    }

    @Nested
    @DisplayName("the reconnect's loading screen")
    inner class Loading {
        private var nowMs = 0L
        private val budget = ConnectionOutageBudget { nowMs }
        private val loadingState = CareerLaunchNavigator.LaunchScreenState.GAME_LOADING
        private val dialogState = CareerLaunchNavigator.LaunchScreenState.DIALOG_HANDLED

        private fun limit() = betweenRunLoadingLimitMs(StartModule.NAV_DEADLINE_MS - nowMs)

        /** Waits out [spinnerMs] of loading on the fake clock, 1 s per look, as the navigator does. */
        private fun spinner(spinnerMs: Long): BetweenRunDialogStep.Fail? {
            val end = nowMs + spinnerMs
            return waitOutBetweenRunLoading({ nowMs < end }, { nowMs }, { nowMs += 1_000L }, limit())
        }

        /**
         * One navigation that meets Connection Error, then [spinnerMs] of loading after every Retry,
         * until the connection is back after [backAfterMs]. Returns the outcome and every state seen,
         * each with the stuck and no-progress rule the navigator applies to it.
         */
        private fun reconnect(spinnerMs: Long, backAfterMs: Long): Pair<String, List<Boolean>> {
            val strikes = mutableListOf<Boolean>()
            repeat(1_000) {
                if (nowMs >= backAfterMs) return "SUCCESS" to strikes
                budget.beginIteration()
                strikes += !isConnectionRideOut(dialogState, BetweenRunDialog.CONNECTION_ERROR)
                val step = planBetweenRunDialog(BetweenRunDialog.CONNECTION_ERROR, budget::onError, StartModule.NAV_DEADLINE_MS - nowMs)
                budget.endIterationNormally()
                if (step is BetweenRunDialogStep.Fail) return step.reasonKey to strikes
                nowMs += (step as BetweenRunDialogStep.Retry).waitMs
                budget.beginIteration()
                strikes += !isConnectionRideOut(loadingState, null)
                spinner(spinnerMs)?.let { return it.reasonKey to strikes }
            }
            throw AssertionError("never stopped")
        }

        @Test
        fun `only a connection ride-out is spared the stuck and no-progress limits`() {
            for (state in CareerLaunchNavigator.LaunchScreenState.entries) {
                for (dialog in BetweenRunDialog.entries + listOf(null)) {
                    val rideOut = state == loadingState || (state == dialogState && dialog in listOf(BetweenRunDialog.CONNECTION_ERROR, BetweenRunDialog.DOWNLOAD_ERROR))
                    assertEquals(rideOut, isConnectionRideOut(state, dialog), "$state $dialog")
                }
            }
        }

        @Test
        fun `a loading wait is capped by the in-career hard limit and ends before the navigation deadline`() {
            assertEquals(StartModule.NAV_DEADLINE_MS - CONNECTION_DEADLINE_MARGIN_MS, betweenRunLoadingLimitMs(StartModule.NAV_DEADLINE_MS))
            assertEquals(Game.LOADING_HARD_LIMIT_MS, betweenRunLoadingLimitMs(Long.MAX_VALUE / 2))
            assertEquals(0L, betweenRunLoadingLimitMs(CONNECTION_DEADLINE_MARGIN_MS - 1))
        }

        @Test
        fun `a spinner that clears is waited out without a failure`() {
            assertNull(spinner(45_000L))
            assertEquals(45_000L, nowMs)
        }

        @Test
        fun `a spinner that never ends stops at the limit with the lost-connection key`() {
            val fail = waitOutBetweenRunLoading({ true }, { nowMs }, { nowMs += 1_000L }, limit())
            assertEquals(BetweenRunDialogStep.Fail("CONNECTION_LOST"), fail)
            assertEquals(StartModule.NAV_DEADLINE_MS - CONNECTION_DEADLINE_MARGIN_MS, nowMs)
        }

        @Test
        fun `a stop or the deadline's interrupt during the wait still reaches the navigator`() {
            assertThrows(InterruptedException::class.java) { waitOutBetweenRunLoading({ true }, { nowMs }, { throw InterruptedException() }, limit()) }
        }

        @Test
        fun `a long spinner after each Retry rides the outage out, and the navigation then succeeds`() {
            val (end, strikes) = reconnect(spinnerMs = 45_000L, backAfterMs = 300_000L)
            assertEquals("SUCCESS", end)
            assertEquals(8, strikes.size, "four Retries, each followed by a spinner")
            assertTrue(strikes.none { it }, "no look counts as stuck or as no progress")
        }

        @Test
        fun `a connection that never comes back ends with its own key, never as a stuck screen`() {
            val (end, strikes) = reconnect(spinnerMs = 45_000L, backAfterMs = Long.MAX_VALUE)
            assertEquals("CONNECTION_LOST", end)
            assertTrue(strikes.none { it })
            assertTrue(nowMs <= StartModule.NAV_DEADLINE_MS - CONNECTION_DEADLINE_MARGIN_MS, "stopped at $nowMs")
        }
    }

    @Nested
    @DisplayName("navigator wiring")
    inner class Wiring {
        private val detect by lazy { body(navigator, "private fun detectScreenState(") }

        @Test
        fun `the dialog is read before the generic advance buttons and the menu-bar Home fallback`() {
            val read = detect.indexOf("readBetweenRunDialog(careerLaunchInitiated, { DialogUtils.check(iu, sourceBitmap = bitmap) }, { DialogUtils.getTitle(iu, bitmap, logOnMiss = false) })")
            assertTrue(read >= 0)
            assertTrue(read < detect.indexOf("if (ButtonNext.check(iu, sourceBitmap = bitmap) ||"))
            assertTrue(read < detect.indexOf("ButtonMenuBarHomeSelected.check(iu, sourceBitmap = bitmap)"))
            assertTrue(read < detect.indexOf("return LaunchScreenState.TP_RESTORE_DIALOG"))
        }

        @Test
        fun `the screens that spend or launch are still recognised before it`() {
            val read = detect.indexOf("readBetweenRunDialog(")
            val spendOrLaunch =
                listOf(
                    "SPARKS_SCREEN",
                    "VETERAN_UMAMUSUME_MAX",
                    "RECOVER_TP_QUANTITY",
                    "CONFIRM_REROLL_DIALOG",
                    "SPARK_SELECTION_PAGER",
                    "SPARK_SELECTION_CONFIRMATION",
                    "TRAINEE_SELECT_SCREEN",
                    "SCENARIO_SELECT",
                    "LEGACY_SELECT_SCREEN",
                )
            for (state in spendOrLaunch) {
                val at = detect.indexOf("return LaunchScreenState.$state")
                assertTrue(at in 0 until read, state)
            }
        }

        @Test
        fun `a handled dialog is its own state, never an unknown screen`() {
            assertTrue(detect.contains("pendingBetweenRunDialog = it\n            return LaunchScreenState.DIALOG_HANDLED"))
            assertFalse(detect.contains("DialogFollowTrainer"))
            assertTrue(navigator.contains("LaunchScreenState.DIALOG_HANDLED -> handleBetweenRunDialog()"))
        }

        @Test
        fun `the handler presses only the step's taps and stops on a failure`() {
            val handler = body(navigator, "private fun handleBetweenRunDialog(")
            assertEquals(listOf("it.click(iu)"), Regex("[\\w.]+\\.click\\([^)]*\\)").findAll(handler).map { it.value }.toList())
            assertTrue(handler.contains("step.taps.none { it.click(iu) }"))
            assertTrue(handler.contains("reasonKey = step.reasonKey"))
            val taps = handler.indexOf("step.taps")
            assertTrue(handler.indexOf("if (step is BetweenRunDialogStep.Fail)") in 0 until taps)
            assertTrue(handler.contains("StartModule.NAV_DEADLINE_MS - (System.currentTimeMillis() - navigationStartedAtMs)"))
            assertTrue(handler.indexOf("waitSafe(step.waitMs / 1000.0)") in 0 until taps, "the budget's pause comes before Retry")
            // Title Screen is planned only through the no-career check, and the launch starts over only once it landed.
            val planned = listOf("dialog,", "betweenRunConnectionBudget::onError,", "msBeforeDeadline,", "betweenRunRecovery.mayTapTitleScreen(careerLaunchInitiated),")
            assertTrue(handler.contains("planBetweenRunDialog(\n" + planned.joinToString("") { "                $it\n" }))
            val landed =
                "} else if (step is BetweenRunDialogStep.ReturnToTitle) {\n            betweenRunRecovery.tappedTitleScreen()\n" +
                    "            waitSafe(3.0)\n            return TransitionResult.StartLaunchOver\n        }"
            assertTrue(handler.indexOf(landed) > handler.indexOf("if (step.taps.none { it.click(iu) }) {"))
            assertEquals(1, Regex("TransitionResult\\.StartLaunchOver").findAll(handler).count())
            assertEquals(1, Regex("tappedTitleScreen\\(").findAll(navigator).count())
        }

        @Test
        fun `the navigator never uses the in-career dialog handler, the connection dialog's ok or Title Screen outside the planned step`() {
            for (banned in listOf("DialogConnectionError.ok", "handleDialogs(", "ButtonTitleScreen", "DialogDownloadError.ok")) {
                assertFalse(navigator.contains(banned), banned)
            }
        }

        @Test
        fun `the allowlist names no spend or launch control`() {
            val allowlist = source("$main/BetweenRunDialogs.kt")
            val controls = Regex("\\b(Button|Label|Dialog)[A-Z]\\w*").findAll(allowlist).map { it.value }.toSet() - setOf("ButtonInterface", "DialogHandler", "DialogUtils")
            assertEquals(setOf("ButtonCancel", "ButtonClose", "ButtonCloseWide", "ButtonOk", "ButtonRetry", "ButtonTitleScreen", "DialogNotices", "DialogFollowTrainer"), controls)
            val titleScreen = allowlist.lines().filter { it.contains("ButtonTitleScreen") }
            assertEquals(
                listOf("import com.steve1316.uma_android_automation.components.ButtonTitleScreen", "    data object ReturnToTitle : BetweenRunDialogStep(listOf(ButtonTitleScreen))"),
                titleScreen,
            )
            assertTrue(allowlist.contains("BetweenRunDialog.SESSION_ERROR -> if (mayReturnToTitle) BetweenRunDialogStep.ReturnToTitle else BetweenRunDialogStep.Fail(reasonKey = \"SESSION_EXPIRED\")"))
            assertEquals(1, Regex("BetweenRunDialogStep\\.ReturnToTitle").findAll(allowlist).count())
        }

        @Test
        fun `each navigation starts a fresh outage and deadline, and every recognised screen closes the iteration`() {
            val navigate = body(navigator, "fun navigate(")
            assertTrue(navigate.indexOf("navigationStartedAtMs = System.currentTimeMillis()") in 0 until navigate.indexOf("for (attempt in 0 until MAX_DETECTION_ATTEMPTS)"))
            assertTrue(navigate.indexOf("betweenRunConnectionBudget = ConnectionOutageBudget()") in 0 until navigate.indexOf("for (attempt in 0 until MAX_DETECTION_ATTEMPTS)"))
            val loop = navigate.substring(navigate.indexOf("for (attempt in 0 until MAX_DETECTION_ATTEMPTS)"))
            assertTrue(loop.indexOf("betweenRunConnectionBudget.beginIteration()") in 0 until loop.indexOf("detectScreenState(deepHomeProbe"))
            val end = loop.indexOf("betweenRunConnectionBudget.endIterationNormally()")
            assertTrue(end > loop.indexOf("handleState(currentState, reuseLastLaunchSetup, autoFillSupports)"))
            assertTrue(end < loop.indexOf("when (transitionResult)"))
            assertTrue(loop.indexOf("consecutiveUnknowns++") in 0 until loop.indexOf("handleState(currentState"), "unknown frames continue before the iteration closes")
            assertTrue(loop.contains("if (currentState != LaunchScreenState.GAME_LOADING) betweenRunConnectionBudget.endIterationNormally()"))
        }

        @Test
        fun `the loading screen is recognised where it would have been unknown, after every dialog`() {
            val loading = detect.indexOf("if (gameLoading(bitmap)) {\n            return LaunchScreenState.GAME_LOADING\n        }")
            assertTrue(loading > detect.indexOf("readBetweenRunDialog("))
            assertTrue(loading > detect.indexOf("if (careerLaunchInitiated && DialogUtils.check(iu, sourceBitmap = bitmap))"))
            assertTrue(loading > detect.indexOf("ButtonMenuBarHomeSelected.check(iu, sourceBitmap = bitmap)"))
            assertTrue(loading in 0 until detect.indexOf("if (deepHomeProbe) {"))
            assertTrue(loading < detect.lastIndexOf("return LaunchScreenState.UNKNOWN"))
            assertTrue(navigator.contains("LaunchScreenState.GAME_LOADING -> handleGameLoading()"))
        }

        @Test
        fun `it reads the same templates as the in-career loading wait`() {
            val checkLoading = body(source("$main/bot/Game.kt"), "fun checkLoading(")
            val gameLoading = body(navigator, "private fun gameLoading(")
            for (label in listOf("LabelConnecting.check(", "LabelNowLoading.check(")) {
                assertTrue(checkLoading.contains(label), label)
                assertTrue(gameLoading.contains(label), label)
            }
        }

        @Test
        fun `the loading wait never taps, stops for a dialog and keeps the deadline`() {
            val handler = body(navigator, "private fun handleGameLoading(")
            assertFalse(Regex("\\.click\\(|\\btap\\(|findAndTapImage|\\.close\\(|\\.ok\\(").containsMatchIn(handler))
            assertTrue(handler.contains("!DialogUtils.check(iu, sourceBitmap = it) && gameLoading(it)"))
            assertTrue(handler.contains("BotService.isRunning &&\n                        !StartModule.queueStopRequested &&"))
            assertTrue(handler.contains("limitMs = betweenRunLoadingLimitMs(msBeforeDeadline)"))
            assertTrue(handler.contains("StartModule.NAV_DEADLINE_MS - (System.currentTimeMillis() - navigationStartedAtMs)"))
            assertTrue(handler.contains("reasonKey = fail.reasonKey"))
        }

        @Test
        fun `a ride-out counts toward neither the stuck-screen rebind nor the no-progress limit`() {
            val loop = body(navigator, "fun navigate(")
            val indent = "\n                    "
            val rideOut = "!isConnectionRideOut(detectedState, pendingBetweenRunDialog)\n                ) {"
            val exemption = "${indent}detectedState != LaunchScreenState.TAP_TO_CONTINUE &&$indent!titleLoggingIn &&$indent$rideOut"
            val stuck = "if (detectedState == currentState &&${indent}detectedState != LaunchScreenState.ACTIVE_TRAINING_MENU &&$exemption"
            val progress = "} else if (detectedState != LaunchScreenState.ACTIVE_TRAINING_MENU &&$exemption"
            assertTrue(loop.contains(stuck))
            assertTrue(loop.contains(progress))
            assertTrue(loop.indexOf(stuck) < loop.indexOf("stuckInStateCount++"))
            assertTrue(loop.indexOf(progress) < loop.indexOf("iterationsWithoutProgress++"))
        }
    }
}
