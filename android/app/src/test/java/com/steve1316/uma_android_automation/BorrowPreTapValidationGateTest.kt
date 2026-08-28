package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BorrowPreTapValidationGateTest {
    private val navigator by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
            .readText()
            .replace("\r\n", "\n")
    }

    @Test
    fun `1 missing setting keeps the default gate inert`() {
        var taps = 0
        val result = BorrowPreTapValidationGate().attempt(row(100.0)) { taps++ }

        assertEquals(BorrowTapStatus.TAPPED, result.status)
        assertEquals(1, taps)
        val settings = repoFile("src/context/BotStateContext.tsx").readText()
        assertTrue(settings.contains("debugMode_stopBeforeBorrowTap: false"))
        assertTrue(navigator.contains("getBooleanSetting(\"debug\", BORROW_PRETAP_VALIDATION_SETTING, false)"))
    }

    @Test
    fun `2 explicit off preserves the accepted row tap`() {
        var taps = 0
        val result = BorrowPreTapValidationGate(armed = false).attempt(row(200.0)) { taps++ }

        assertTrue(result.tapped)
        assertEquals(1, taps)
        assertEquals(200.0, result.row?.centerY)
    }

    @Test
    fun `3 armed fresh acceptance suppresses the tap truthfully`() {
        var taps = 0
        val result = BorrowPreTapValidationGate(armed = true).attempt(row(300.0)) { taps++ }

        assertEquals(BorrowTapStatus.LOCATED_VALIDATED_TAP_SUPPRESSED, result.status)
        assertTrue(result.suppressed)
        assertFalse(result.tapped)
        assertEquals(0, taps)
    }

    @Test
    fun `4 armed gate waits until a row is accepted`() {
        var taps = 0
        val result = BorrowPreTapValidationGate(armed = true).attempt(null) { taps++ }

        assertEquals(BorrowTapStatus.WAITING_FOR_ACCEPTED_ROW, result.status)
        assertFalse(result.suppressed)
        assertEquals(0, taps)
    }

    @Test
    fun `5 host movement uses the fresh coordinate and suppresses its tap`() {
        val run = runHostRecovery(SwipeMovement.MOVED)

        assertEquals(1, run.hostCalls)
        assertEquals(999.0, run.selection.row?.first)
        assertEquals(BorrowTapStatus.LOCATED_VALIDATED_TAP_SUPPRESSED, run.gateResult.status)
        assertEquals(999.0, run.gateResult.row?.centerY)
        assertEquals(0, run.taps)
    }

    @Test
    fun `6 stale pre-swipe row cannot arm validation evidence`() {
        val run = runHostRecovery(SwipeMovement.MOVED)

        assertNotNull(run.selection.row)
        assertFalse(run.acceptedCoordinates.contains(111.0))
        assertEquals(listOf(999.0), run.acceptedCoordinates)
    }

    @Test
    fun `7 terminal suppression blocks a fallback row`() {
        var taps = 0
        val gate = BorrowPreTapValidationGate(armed = true)
        val first = gate.attempt(row(400.0, "primary")) { taps++ }
        val fallback = gate.attempt(row(800.0, "fallback")) { taps++ }

        assertEquals(first, fallback)
        assertEquals("primary", fallback.row?.identity)
        assertEquals(0, taps)
    }

    @Test
    fun `8 suppressed build-aware state is never ready`() {
        assertFalse(BuildAwareLaunchGate.canStartCareer(LaunchTransactionState.BORROW_TAP_SUPPRESSED))
        assertFalse(BuildAwareLaunchResult(LaunchTransactionState.BORROW_TAP_SUPPRESSED, LaunchPreconditions(), LaunchTransactionState.BORROW_TAP_SUPPRESSED).ready)
    }

    @Test
    fun `9 suppression result cannot reach Start Career`() {
        val stop = section("private fun borrowTapValidationStopped", "private fun fillEmptyFriendSlot")
        assertTrue(stop.contains("LOCATED_VALIDATED_TAP_SUPPRESSED"))
        assertFalse(stop.contains("ButtonStartCareer"))
        assertFalse(BuildAwareLaunchGate.canStartCareer(LaunchTransactionState.BORROW_TAP_SUPPRESSED))
    }

    @Test
    fun `10 arming the gate does not enable host input`() {
        assertEquals(HostInputMode.ACCESSIBILITY_ONLY, HostInputMode.parse(null))
        assertEquals(HostInputMode.ACCESSIBILITY_ONLY, HostInputMode.parse("accessibility_only"))
        val reset = section("fun navigate(", "LaunchTransactionGate.beginLaunch")
        assertTrue(reset.contains("BORROW_PRETAP_VALIDATION_SETTING"))
        assertFalse(reset.contains("hostInputMode"))
        assertFalse(reset.contains("hostInputPairingCode"))
    }

    @Test
    fun `11 host disabled leaves armed validation on the accessibility-only path`() {
        val run = runHostRecovery(SwipeMovement.UNCERTAIN, detailCode = "HOST_INPUT_DISABLED", swipeAttempts = 0)

        assertNull(run.selection.row)
        assertEquals("HOST_INPUT_DISABLED", run.selection.walk.hostRecovery?.detailCode)
        assertEquals(BorrowTapStatus.WAITING_FOR_ACCEPTED_ROW, run.gateResult.status)
        assertEquals(0, run.taps)
    }

    @Test
    fun `12 failed host evidence cannot fabricate validation success`() {
        val run = runHostRecovery(SwipeMovement.NO_EFFECT)

        assertTrue(run.selection.walk.stalled)
        assertNull(run.selection.row)
        assertFalse(run.gateResult.suppressed)
        assertEquals(0, run.taps)
    }

    @Test
    fun `13 stop before fresh acceptance produces no result or tap`() {
        var visits = 0
        var taps = 0
        val walk =
            BorrowListWalker(
                maxPageGestures = 1,
                maxSwallowedRetries = 0,
                readScreen = { BorrowScan(listOf(700.0 to "accepted")) },
                advancePage = {},
                abort = { true },
            ).walk { _, _ ->
                visits++
                false
            }
        val result = BorrowPreTapValidationGate(armed = true).attempt(null) { taps++ }

        assertEquals(BorrowWalkEnd.ABORTED, walk.end)
        assertEquals(0, visits)
        assertEquals(BorrowTapStatus.WAITING_FOR_ACCEPTED_ROW, result.status)
        assertEquals(0, taps)
    }

    @Test
    fun `14 standard Smart Borrow paths use the shared boundary`() {
        val fill = section("private fun fillEmptyFriendSlot", "private fun performBorrowReplacement")
        val smart = section("private fun trySmartBorrowPick", "/** Outcome of one reopen-and-select pass")

        assertTrue(fill.contains("tapAcceptedBorrowRow"))
        assertTrue(fill.contains("SmartBorrowPickOutcome.TapSuppressed"))
        assertTrue(smart.contains("tapAcceptedBorrowRow"))
        assertTrue(smart.contains("SmartBorrowPickOutcome.TapSuppressed"))
        assertFalse(fill.contains("CoordinateTap.tap"))
        assertFalse(smart.contains("CoordinateTap.tap"))
    }

    @Test
    fun `15 build-aware exact revalidation uses the shared boundary`() {
        val revalidate = section("private fun revalidateAndTapBorrow", "/**\n     * Production build-aware launch transaction")
        val transaction = section("internal fun prepareBuildAwareLaunchToReady", "/**\n     * The launch-gate dry-run diagnostic")

        assertTrue(revalidate.contains("tapAcceptedBorrowRow"))
        assertTrue(revalidate.contains("LOCATED_VALIDATED_TAP_SUPPRESSED"))
        assertFalse(revalidate.contains("CoordinateTap.tap"))
        assertTrue(transaction.contains("LaunchTransactionState.BORROW_TAP_SUPPRESSED"))
        assertTrue(transaction.indexOf("BORROW_TAP_SUPPRESSED") < transaction.indexOf("if (!selection.tapped)"))
    }

    @Test
    fun `16 reopen default and fallback branches cannot bypass suppression`() {
        val reopen = section("private fun reopenAndSelect", "/** The bounded list walker")
        assertTrue(reopen.contains("tapAcceptedBorrowRow"))
        assertTrue(reopen.contains("ROW_ACCEPTED"), "candidate discovery must not be logged as a completed selection")
        assertFalse(reopen.contains("CoordinateTap.tap"))

        for (action in listOf("borrow_preferred_row", "borrow_card_first_valid_row", "borrow_smart_row", "a3r2_launch_borrow_select")) {
            val lines = navigator.lineSequence().filter { it.contains("\"$action\"") }.toList()
            assertTrue(lines.isNotEmpty(), "$action must remain wired")
            assertTrue(lines.all { it.contains("tapAcceptedBorrowRow") }, "$action must cross the shared boundary")
        }
    }

    @Test
    fun `17 host swipe diagnostic remains separately armed`() {
        val ui = repoFile("src/pages/DebugSettings/index.tsx").readText()
        val debugKeys = Regex("const debugTestKeys = \\[(.*?)] as const", RegexOption.DOT_MATCHES_ALL).find(ui)?.groupValues?.get(1) ?: error("debugTestKeys missing")

        assertTrue(debugKeys.contains("debugMode_startHostBorrowSwipeTest"))
        assertFalse(debugKeys.contains(BORROW_PRETAP_VALIDATION_SETTING))
        assertTrue(DebugTestGate.ALL_KEYS.contains("debugMode_startHostBorrowSwipeTest"))
        assertFalse(DebugTestGate.ALL_KEYS.contains(BORROW_PRETAP_VALIDATION_SETTING))
    }

    @Test
    fun `18 Legacy selection remains outside the Borrow validation seam`() {
        val legacy = section("private fun handleLegacySelectScreen", "private fun captureLineageTelemetry")

        assertTrue(legacy.contains("ButtonAutoSelect"))
        assertFalse(legacy.contains("BorrowPreTapValidationGate"))
        assertFalse(legacy.contains("BORROW_PRETAP_VALIDATION_SETTING"))
        assertFalse(legacy.contains("tapAcceptedBorrowRow"))
    }

    private fun row(centerY: Double, identity: String = "accepted"): AcceptedBorrowRow = AcceptedBorrowRow(centerY, identity)

    private data class HostGateRun(
        val selection: BorrowSelection,
        val gateResult: BorrowTapResult,
        val hostCalls: Int,
        val taps: Int,
        val acceptedCoordinates: List<Double>,
    )

    private fun runHostRecovery(
        movement: SwipeMovement,
        detailCode: String = movement.name,
        swipeAttempts: Int = 1,
    ): HostGateRun {
        var hostCalls = 0
        var hostMoved = false
        val acceptedCoordinates = mutableListOf<Double>()
        val selection =
            selectFromBorrowList(
                BorrowListWalker(
                    maxPageGestures = 1,
                    maxSwallowedRetries = 0,
                    readScreen = {
                        if (hostMoved) BorrowScan(listOf(999.0 to "fresh")) else BorrowScan(listOf(111.0 to "stale"))
                    },
                    advancePage = {},
                    recoverService = { false },
                    recoverHost = {
                        hostCalls++
                        hostMoved = movement == SwipeMovement.MOVED
                        HostScrollRecoveryReport(
                            scope = HostInputScope.BORROW_LIST_SCROLL,
                            execution =
                                InputExecutionResult(
                                    status = if (swipeAttempts == 0) InputExecutionStatus.UNAVAILABLE else InputExecutionStatus.EXECUTED,
                                    foreground = swipeAttempts != 0,
                                    detailCode = detailCode,
                                ),
                            movement = movement,
                            detailCode = detailCode,
                            swipeAttempts = swipeAttempts,
                            stopped = false,
                        )
                    },
                ),
            ) { text ->
                val accepted = hostMoved && text == "fresh"
                if (accepted) acceptedCoordinates += 999.0
                accepted
            }
        var taps = 0
        val gateResult =
            BorrowPreTapValidationGate(armed = true).attempt(selection.row?.let { row(it.first, it.second) }) {
                taps++
            }
        return HostGateRun(selection, gateResult, hostCalls, taps, acceptedCoordinates)
    }

    private fun section(start: String, end: String): String {
        val startAt = navigator.indexOf(start)
        val endAt = navigator.indexOf(end, startAt + start.length)
        check(startAt >= 0 && endAt > startAt) { "could not isolate navigator section $start -> $end" }
        return navigator.substring(startAt, endAt)
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(8) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }
}
