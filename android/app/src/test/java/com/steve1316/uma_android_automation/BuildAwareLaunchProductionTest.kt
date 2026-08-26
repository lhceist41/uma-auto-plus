package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A3 production build-aware launch integration (source guards).
 *
 * A3 routes the normal Support Formation launch through the build-aware transaction when
 * runQueue.enableBuildAwareLaunch is on, and gates BOTH production Start Career taps (the Support
 * Formation tap and the final confirmation tap) on [BuildAwareLaunchGate.canStartCareer]. These guards
 * pin, against the source, that: the mode is opt-in with the legacy path preserved when off; the
 * build-aware path taps only through the structural gate; a blocked launch rolls back and fails closed
 * with no legacy fallback; and the final confirmation cannot fire without an authorized build-aware
 * launch. The live behaviour drives OCR/taps, so it cannot be unit-tested directly.
 */
@DisplayName("A3 build-aware production launch")
class BuildAwareLaunchProductionTest {
    private val nav by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt") }
    private val botState by lazy { source("src/context/BotStateContext.tsx") }
    private val runQueueUi by lazy { source("src/pages/RunQueueSettings/index.tsx") }

    private fun slice(signature: String, next: String): String {
        val start = nav.indexOf(signature)
        assertTrue(start >= 0, "$signature exists")
        val end = nav.indexOf(next, start + signature.length)
        assertTrue(end > start, "$next follows $signature")
        return nav.substring(start, end)
    }

    private fun deckScreenBody() = slice("private fun handleSupportDeckScreen(", "private fun handleBuildAwareLaunch(")

    private fun buildAwareBody() = slice("private fun handleBuildAwareLaunch(", "One pass of the Smart Borrow sub-flow")

    private fun preRunBody() = slice("private fun handlePreRunConfirmation(", "private fun tickEventBoostIfOff(")

    @Nested
    @DisplayName("opt-in with legacy preserved")
    inner class OptIn {
        @Test
        fun `the deck handler routes to the build-aware launch only when the mode is on`() {
            val body = deckScreenBody()
            assertTrue(
                body.contains("getBooleanSetting(\"runQueue\", \"enableBuildAwareLaunch\", false)") && body.contains("return handleBuildAwareLaunch(requiredDeck)"),
                "handleSupportDeckScreen branches into handleBuildAwareLaunch under the setting",
            )
        }

        @Test
        fun `the legacy priority-list borrow path is preserved for the mode-off case`() {
            val body = deckScreenBody()
            assertTrue(body.contains("runBorrowStep(bitmap)"), "the legacy borrow step still runs when build-aware launch is off")
        }

        @Test
        fun `the setting is registered and defaults to off`() {
            assertTrue(botState.contains("enableBuildAwareLaunch: boolean"), "the Settings interface declares the flag")
            assertTrue(botState.contains("enableBuildAwareLaunch: false,"), "the default is off")
            assertTrue(runQueueUi.contains("run-queue-build-aware-launch"), "the Run Queue page has a discoverable control")
        }
    }

    @Nested
    @DisplayName("structural Start Career gate")
    inner class Gate {
        @Test
        fun `the build-aware tap is guarded by canStartCareer`() {
            val body = buildAwareBody()
            val gateIdx = body.indexOf("check(BuildAwareLaunchGate.canStartCareer(")
            val tapIdx = body.indexOf("ButtonStartCareer.click(")
            assertTrue(gateIdx in 0 until tapIdx, "the canStartCareer check must precede the Start Career tap")
        }

        @Test
        fun `the final confirmation tap is gated on the build-aware state when the mode is on`() {
            val body = preRunBody()
            assertTrue(body.contains("getBooleanSetting(\"runQueue\", \"enableBuildAwareLaunch\", false)"), "the confirmation checks the mode")
            assertTrue(body.contains("BuildAwareLaunchGate.canStartCareer("), "the confirmation gates on canStartCareer")
            val gateIdx = body.indexOf("BuildAwareLaunchGate.canStartCareer(")
            val tapIdx = body.indexOf("ButtonStartCareer.click(")
            assertTrue(gateIdx in 0 until tapIdx, "the build-aware gate must precede the confirmation tap")
        }
    }

    @Nested
    @DisplayName("fail closed, no legacy fallback")
    inner class FailClosed {
        @Test
        fun `a blocked launch rolls back the committed borrow and fails closed`() {
            val body = buildAwareBody()
            assertTrue(body.contains("rollbackCommittedBorrow()"), "a blocked launch rolls the borrow back")
            assertTrue(body.contains("buildAwareLaunchBlocked = true"), "a blocked launch latches so it does not retry")
            assertTrue(body.contains("TransitionResult.Failed"), "a blocked launch returns a deterministic failure")
        }

        @Test
        fun `the build-aware launch never falls back to the legacy borrow`() {
            val body = buildAwareBody()
            assertFalse(body.contains("runBorrowStep"), "no legacy runBorrowStep in the build-aware path")
            assertFalse(body.contains("fillEmptyFriendSlot"), "no legacy fill in the build-aware path")
            assertFalse(body.contains("SmartBorrowList"), "no priority-list fallback in the build-aware path")
        }

        @Test
        fun `the build-aware launch reuses the proven transaction, not a copy`() {
            val body = buildAwareBody()
            assertTrue(body.contains("prepareBuildAwareLaunchToReady("), "it drives the shared A2 transaction")
        }
    }

    @Nested
    @DisplayName("A3-R1 borrow reliability (equivalence + truthful traversal)")
    inner class BorrowReliability {
        private fun moduleBody() = slice("internal fun prepareBuildAwareLaunchToReady(", "internal fun dryRunBuildAwareLaunchGate(")

        @Test
        fun `production accepts a LOCATED equivalence class, not only a single identity candidate`() {
            val body = moduleBody()
            assertTrue(body.contains("canSelectLocatedBorrow(locate.status, locate.traversalComplete, locate.selectionEvidenceComplete)"), "production accepts any LOCATED (exact or equivalent source) with either a completed traversal or A3-R12 selection evidence")
            assertFalse(body.contains("identityCandidates?.size == 1"), "the strict single-candidate rule is gone from production")
        }

        @Test
        fun `a stalled traversal is blocked distinctly from a stale pool`() {
            val body = moduleBody()
            assertTrue(body.contains("BORROW_LOCATOR_STALLED"), "an incomplete traversal has its own blocking state")
            assertTrue(body.contains("locate.traversalComplete"), "the stale-vs-stalled split keys on a full traversal")
            assertTrue(body.contains("BORROW_POOL_STALE"), "a genuinely absent card in a full traversal is still stale")
        }

        @Test
        fun `a non-READY build-aware state never authorises a tap`() {
            // The new BORROW_LOCATOR_STALLED state, like every non-READY state, must fail canStartCareer.
            assertFalse(BuildAwareLaunchGate.canStartCareer(LaunchTransactionState.BORROW_LOCATOR_STALLED))
        }
    }

    @Nested
    @DisplayName("A3-R2 stall-before-tap + pre-tap revalidation")
    inner class StallBeforeTap {
        private fun moduleBody() = slice("internal fun prepareBuildAwareLaunchToReady(", "internal fun dryRunBuildAwareLaunchGate(")

        private fun revalidateBody() = slice("private fun revalidateAndTapBorrow(", "Production build-aware launch transaction")

        @Test
        fun `the production gate authorises a tap only through canSelectLocatedBorrow, not bare LOCATED`() {
            val body = moduleBody()
            assertTrue(body.contains("canSelectLocatedBorrow(locate.status, locate.traversalComplete, locate.selectionEvidenceComplete)"), "the tap authority requires a completed traversal or A3-R12 selection evidence")
            assertTrue(body.contains("BORROW_LOCATOR_STALLED"), "a stalled LOCATED blocks as a locator stall before any tap")
        }

        @Test
        fun `production selects by identity and limit break with revalidation, never a text-only match`() {
            val body = moduleBody()
            assertTrue(body.contains("selectBorrowByIdentityRevalidated("), "production uses the identity+LB revalidating selection")
            assertFalse(body.contains("intentTextMatch("), "production no longer taps by a character+title-only text match")
        }

        @Test
        fun `the tap is downstream of a fresh re-read and an identity+LB check`() {
            val body = revalidateBody()
            assertTrue(body.contains("readBorrowPoolRichRows("), "revalidation re-reads the current picker before tapping")
            assertTrue(body.contains("rowMatchesIntentIdentity("), "revalidation re-checks character, title, and limit break")
            assertTrue(body.contains("ROW_REVALIDATION_FAILED"), "a vanished or LB-changed row fails closed with no tap")
            val readIdx = body.indexOf("readBorrowPoolRichRows(")
            val boundaryIdx = body.indexOf("tapAcceptedBorrowRow(")
            assertTrue(readIdx in 0 until boundaryIdx, "the fresh re-read must precede the shared pre-tap boundary")
            assertFalse(body.contains("CoordinateTap.tap("), "build-aware revalidation cannot bypass the shared pre-tap boundary")
        }
    }

    @Nested
    @DisplayName("A3-R12 build-aware locate scan ceiling")
    inner class LocateScanCeiling {
        private fun locateBody() = slice("internal fun locateSmartBorrowIntentReadOnly(", "private fun persistSmartBorrowLocate(")

        private fun censusBody() = slice("internal fun scanBorrowPoolReadOnly(", "private fun nameKeyOf(obs: BorrowRowObservation):")

        private fun selectBody() = slice("private fun selectBorrowByIdentityRevalidated(", "private fun revalidateAndTapBorrow(")

        private fun selectedSlotRowsBody() = slice("private fun readSelectedSlotRows(", "private fun readSelectedSlotVerification(")

        private fun defaultWalkerBody() = slice("private fun borrowWalker(): BorrowListWalker =", "private fun recoverBorrowScrollWithHost(): HostScrollRecoveryReport =")

        private fun moduleBody() = slice("internal fun prepareBuildAwareLaunchToReady(", "internal fun dryRunBuildAwareLaunchGate(")

        @Test
        fun `only the locate walker uses the widened locate-only scan bound`() {
            assertTrue(locateBody().contains("maxPageGestures = MAX_BORROW_LOCATE_SCAN_PAGES"), "the locate walker must use the widened locate-only bound")
        }

        @Test
        fun `census, select, verify, and the default walker keep the shared bound unchanged`() {
            assertTrue(censusBody().contains("maxPageGestures = MAX_BORROW_SCAN_PAGES"), "the census walker must not be widened")
            assertTrue(selectBody().contains("maxPageGestures = MAX_BORROW_SCAN_PAGES"), "the select/revalidate walker must not be widened")
            assertTrue(selectedSlotRowsBody().contains("maxPageGestures = MAX_BORROW_SCAN_PAGES"), "the selected-marker verification walker must not be widened")
            assertTrue(defaultWalkerBody().contains("maxPageGestures = MAX_BORROW_SCAN_PAGES"), "the default/legacy walker must not be widened")
        }

        @Test
        fun `the locate-only bound is a fixed multiple of the shared bound, not an unbounded scroll`() {
            assertTrue(nav.contains("MAX_BORROW_LOCATE_SCAN_PAGES = MAX_BORROW_SCAN_PAGES * 5"), "the locate-only ceiling must be a fixed, finite, deterministic constant")
        }

        @Test
        fun `a healthy locate scan budget exhaustion is worded distinctly from a true stall`() {
            val body = locateBody()
            assertTrue(body.contains("walk.end == BorrowWalkEnd.MAX_PAGES"), "the locate stage distinguishes a healthy MAX_PAGES cutoff from a stall")
            assertTrue(body.contains("a healthy cutoff, not a stall"), "the healthy-cutoff log line must not claim the scroll stalled")
        }

        @Test
        fun `the block reason for a non-stalled incomplete traversal does not claim the scroll stalled`() {
            val body = moduleBody()
            assertTrue(body.contains("locate.traversalStalled"), "the block-reason text keys on the true stall signal, not traversalComplete alone")
            assertTrue(body.contains("the healthy locate scan budget was exhausted"), "a healthy budget exhaustion gets its own truthful wording")
        }

        @Test
        fun `MAX_PAGES exhaustion never triggers host recovery merely because the budget ran out`() {
            // A healthy walk that keeps moving to a fresh screen every page (never repeats, never stalls)
            // must exhaust its forward budget as a plain MAX_PAGES ending without ever invoking the host
            // recovery rung -- that rung is reachable only from the no-movement/stall branch.
            var hostCalls = 0
            var page = 0
            val walker =
                BorrowListWalker(
                    maxPageGestures = 3,
                    maxSwallowedRetries = 2,
                    readScreen = { BorrowScan(listOf(0.0 to "row-${page}")) },
                    advancePage = { page++ },
                    recoverHost = {
                        hostCalls++
                        HostScrollRecoveryReport(
                            scope = HostInputScope.BORROW_LIST_SCROLL,
                            execution = InputExecutionResult(status = InputExecutionStatus.EXECUTED, foreground = true, detailCode = "test"),
                            movement = SwipeMovement.MOVED,
                            detailCode = "test",
                            swipeAttempts = 1,
                            stopped = false,
                        )
                    },
                )
            val result = walker.walk { _, _ -> false }
            assertEquals(BorrowWalkEnd.MAX_PAGES, result.end)
            assertFalse(result.stalled, "a healthy walk that always advances must not be marked stalled")
            assertEquals(0, hostCalls, "host recovery must never fire merely because the forward budget ran out")
        }
    }

    @Nested
    @DisplayName("A2 dry-run regression")
    inner class A2Regression {
        @Test
        fun `the A2 dry-run still reaches the module, suppresses the tap, and rolls back`() {
            val dry = slice("internal fun dryRunBuildAwareLaunchGate(", "private fun rollbackCommittedBorrow(")
            assertTrue(dry.contains("prepareBuildAwareLaunchToReady()"), "the dry-run drives the same production module")
            assertTrue(dry.contains("rollbackCommittedBorrow()"), "the dry-run still rolls back")
            assertFalse(dry.contains("ButtonStartCareer.click"), "the dry-run still never taps Start Career")
        }
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val f = File(dir, relative)
            if (f.isFile) return f.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }
}
