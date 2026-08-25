package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BorrowAccessibilityFaultInjectionTest {
    private val navigator by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
            .readText()
            .replace("\r\n", "\n")
    }

    @Test
    fun `1 missing old and malformed settings remain off`() {
        for (raw in listOf<String?>(null, "", "false", "0", "yes", "true-ish")) {
            assertFalse(borrowAccessibilityScrollFaultEnabled(raw), "raw=$raw")
        }
        assertTrue(borrowAccessibilityScrollFaultEnabled("true"))
        assertTrue(borrowAccessibilityScrollFaultEnabled(" TRUE "))

        val settings = repoFile("src/context/BotStateContext.tsx").readText()
        assertTrue(settings.contains("debugMode_swallowBorrowAccessibilityScroll: false"))
        val navigateReset = section("fun navigate(", "LaunchTransactionGate.beginLaunch")
        val reset = navigateReset.substring(navigateReset.indexOf("borrowAccessibilityScrollFaultInjector ="))
        assertTrue(reset.contains("getStringSetting(\"debug\", BORROW_ACCESSIBILITY_SCROLL_FAULT_SETTING)"))
        assertTrue(reset.contains("borrowAccessibilityScrollFaultEnabled"))
        assertFalse(reset.contains("hostInputMode"))
        assertFalse(reset.contains("hostInputPairingCode"))
        assertFalse(reset.contains("BuildAware"))
        assertFalse(reset.contains("BORROW_PRETAP_VALIDATION_SETTING"))
    }

    @Test
    fun `2 explicit off dispatches the normal Borrow Accessibility swipe`() {
        var accessibilitySwipes = 0
        val injector = BorrowAccessibilityScrollFaultInjector(armed = false)

        val dispatched = injector.dispatch(attempt = 0) { accessibilitySwipes++ }

        assertTrue(dispatched)
        assertEquals(1, accessibilitySwipes)
        assertEquals(1, injector.requestedAttempts)
        assertEquals(0, injector.suppressedAttempts)
    }

    @Test
    fun `3 armed injector suppresses Accessibility and records truthful fault evidence`() {
        var accessibilitySwipes = 0
        val logs = mutableListOf<String>()
        val injector = BorrowAccessibilityScrollFaultInjector(armed = true, log = logs::add)

        val dispatched = injector.dispatch(attempt = 2) { accessibilitySwipes++ }

        assertFalse(dispatched)
        assertEquals(0, accessibilitySwipes)
        assertEquals(1, injector.requestedAttempts)
        assertEquals(1, injector.suppressedAttempts)
        assertTrue(logs.any { it.contains("attempt requested") && it.contains("attempt=2") })
        assertTrue(logs.any { it.contains("FAULT_INJECTED") && it.contains("attempt=2") })
    }

    @Test
    fun `4 armed scroll fault does not suppress an accepted Borrow row tap`() {
        var accessibilitySwipes = 0
        val injector = BorrowAccessibilityScrollFaultInjector(armed = true)
        injector.dispatch(attempt = 0) { accessibilitySwipes++ }

        var taps = 0
        val tap = BorrowPreTapValidationGate(armed = false).attempt(AcceptedBorrowRow(500.0, "accepted")) { taps++ }

        assertEquals(0, accessibilitySwipes)
        assertTrue(tap.tapped)
        assertEquals(1, taps)
    }

    @Test
    fun `5 non-Borrow Accessibility remains outside the fault boundary`() {
        val production = section("private fun advanceProductionBorrowList", "/** Non-production Borrow diagnostics")
        val ordinary = section("private fun swipeBorrowList", "/**\n     * One bounded accessibility gesture-dispatch recovery")

        assertTrue(production.contains("borrowAccessibilityScrollFaultInjector.dispatch"))
        assertFalse(ordinary.contains("borrowAccessibilityScrollFaultInjector"))
        assertTrue(ordinary.contains("dispatchBorrowListSwipe"))
    }

    @Test
    fun `6 Legacy Accessibility remains outside the Borrow fault boundary`() {
        val legacy = section("private fun handleLegacySelectScreen", "private fun captureLineageTelemetry")

        assertFalse(legacy.contains("BorrowAccessibilityScrollFaultInjector"))
        assertFalse(legacy.contains("BORROW_ACCESSIBILITY_SCROLL_FAULT_SETTING"))
        assertFalse(legacy.contains("advanceProductionBorrowList"))
    }

    @Test
    fun `7 armed fault leaves the host swipe callback real and separate`() {
        val run = runFault(host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.MOVED, 1), acceptFresh = true)

        assertEquals(0, run.accessibilitySwipes)
        assertEquals(1, run.hostCalls)
        assertEquals(1, run.hostSwipes)
        assertEquals(SwipeMovement.MOVED, run.selection.walk.hostRecovery?.movement)
    }

    @Test
    fun `8 armed fault alone cannot enable host recovery`() {
        val run = runFault(host = null)

        assertEquals(0, run.hostCalls)
        assertEquals(0, run.hostSwipes)
        assertNull(run.selection.walk.hostRecovery)
        assertTrue(run.selection.walk.stalled)
    }

    @Test
    fun `9 accessibility only exhausts truthfully without host input`() {
        val run =
            runFault(
                host = hostReport(InputExecutionStatus.UNAVAILABLE, SwipeMovement.UNCERTAIN, 0, "HOST_INPUT_DISABLED"),
            )

        assertEquals(0, run.accessibilitySwipes)
        assertEquals(1, run.rebindCalls)
        assertEquals(1, run.hostCalls, "production policy is checked once after Accessibility exhausts")
        assertEquals(0, run.hostSwipes, "disabled host mode sends no host input")
        assertTrue(run.selection.walk.stalled)
        assertEquals("HOST_INPUT_DISABLED", run.selection.walk.hostRecovery?.detailCode)
    }

    @Test
    fun `10 healthy host runs only after the full Accessibility ladder and once at most`() {
        val run = runFault(host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.MOVED, 1), acceptFresh = true)

        assertEquals(listOf(0, 1, 2, 0, 0), run.advanceAttempts)
        assertEquals(5, run.injector.suppressedAttempts)
        assertEquals(1, run.rebindCalls)
        assertEquals(1, run.hostCalls)
        assertEquals(1, run.hostSwipes)
    }

    @Test
    fun `11 host movement resumes through a fresh walker read`() {
        val run = runFault(host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.MOVED, 1), acceptFresh = true)

        assertNotNull(run.selection.row)
        assertEquals(999.0, run.selection.row?.first)
        assertTrue(run.readsAfterHost > 0)
        assertFalse(run.selection.walk.stalled)
    }

    @Test
    fun `12 host no effect fails closed without retry`() {
        val run = runFault(host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.NO_EFFECT, 1))

        assertEquals(1, run.hostCalls)
        assertEquals(1, run.hostSwipes)
        assertNull(run.selection.row)
        assertTrue(run.selection.walk.stalled)
        assertFalse(run.selection.walk.fullyTraversed)
    }

    @Test
    fun `13 uncertain and rejected host outcomes fail closed without retry`() {
        val outcomes =
            listOf(
                hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.UNCERTAIN, 1),
                hostReport(InputExecutionStatus.REJECTED, SwipeMovement.UNCERTAIN, 0, "FOREGROUND_REJECTED"),
            )

        for (host in outcomes) {
            val run = runFault(host = host)
            assertEquals(1, run.hostCalls, host.detailCode)
            assertEquals(host.swipeAttempts, run.hostSwipes, host.detailCode)
            assertTrue(run.selection.walk.stalled, host.detailCode)
            assertNull(run.selection.row, host.detailCode)
        }
    }

    @Test
    fun `14 service rebind occurs before the host rung`() {
        val run = runFault(host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.NO_EFFECT, 1))

        val rebindAt = run.events.indexOf("rebind")
        val hostAt = run.events.indexOf("host")
        assertTrue(rebindAt >= 0)
        assertTrue(hostAt > rebindAt, "host recovery cannot precede the service rebind")
    }

    @Test
    fun `15 post-rebind gesture budget is spent before the host rung`() {
        val run = runFault(host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.NO_EFFECT, 1))

        val rebindAt = run.events.indexOf("rebind")
        val hostAt = run.events.indexOf("host")
        val attemptsBetween = run.events.subList(rebindAt + 1, hostAt).count { it.startsWith("advance:") }
        assertEquals(2, attemptsBetween)
        assertEquals(2, run.selection.walk.postRebindGestures)
    }

    @Test
    fun `16 injector cannot short-circuit directly to host recovery`() {
        val source = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/BorrowAccessibilityScrollFaultInjector.kt").readText()
        val run = runFault(host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.NO_EFFECT, 1))

        assertFalse(source.contains("recoverHost"))
        assertFalse(source.contains("HostAdbInputTransport"))
        assertEquals(5, run.injector.requestedAttempts, "all normal, stronger, and post-rebind attempts run first")
        assertTrue(run.events.indexOf("host") > run.events.lastIndexOf("advance:0"))
    }

    @Test
    fun `17 pre-tap validation remains separately controllable`() {
        var taps = 0
        val faultOnTapOff = BorrowPreTapValidationGate(armed = false).attempt(AcceptedBorrowRow(500.0, "fresh")) { taps++ }
        val faultOffTapOn = BorrowPreTapValidationGate(armed = true).attempt(AcceptedBorrowRow(600.0, "fresh")) { taps++ }

        assertTrue(faultOnTapOff.tapped)
        assertTrue(faultOffTapOn.suppressed)
        assertEquals(1, taps)
        assertFalse(BORROW_ACCESSIBILITY_SCROLL_FAULT_SETTING == BORROW_PRETAP_VALIDATION_SETTING)
    }

    @Test
    fun `18 armed fault plus host movement reaches fresh acceptance and pre-tap suppression`() {
        val run = runFault(host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.MOVED, 1), acceptFresh = true)
        var taps = 0
        val result =
            BorrowPreTapValidationGate(armed = true).attempt(
                run.selection.row?.let { AcceptedBorrowRow(it.first, it.second) },
            ) { taps++ }

        assertEquals(999.0, result.row?.centerY)
        assertEquals(BorrowTapStatus.LOCATED_VALIDATED_TAP_SUPPRESSED, result.status)
        assertEquals(0, taps)
        assertEquals(1, run.hostSwipes)
    }

    @Test
    fun `19 host swipe rehearsal remains a separate debug test`() {
        val ui = repoFile("src/pages/DebugSettings/index.tsx").readText()
        val debugKeys = Regex("const debugTestKeys = \\[(.*?)] as const", RegexOption.DOT_MATCHES_ALL).find(ui)?.groupValues?.get(1) ?: error("debugTestKeys missing")

        assertTrue(DebugTestGate.ALL_KEYS.contains("debugMode_startHostBorrowSwipeTest"))
        assertFalse(DebugTestGate.ALL_KEYS.contains(BORROW_ACCESSIBILITY_SCROLL_FAULT_SETTING))
        assertFalse(debugKeys.contains(BORROW_ACCESSIBILITY_SCROLL_FAULT_SETTING))
        assertTrue(ui.contains("debug-swallow-borrow-accessibility-scroll"))
    }

    @Test
    fun `20 Legacy and host recovery implementations remain untouched by the injector`() {
        val source = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/BorrowAccessibilityScrollFaultInjector.kt").readText()
        val hostRecovery = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/ProductionHostScrollRecovery.kt").readText()

        assertFalse(source.contains("Legacy", ignoreCase = true))
        assertFalse(source.contains("ProductionHostScrollRecovery"))
        assertFalse(source.contains("CoordinateTap"))
        assertFalse(hostRecovery.contains(BORROW_ACCESSIBILITY_SCROLL_FAULT_SETTING))
        assertFalse(hostRecovery.contains("BorrowAccessibilityScrollFaultInjector"))
    }

    private data class FaultRun(
        val selection: BorrowSelection,
        val injector: BorrowAccessibilityScrollFaultInjector,
        val accessibilitySwipes: Int,
        val rebindCalls: Int,
        val hostCalls: Int,
        val hostSwipes: Int,
        val readsAfterHost: Int,
        val advanceAttempts: List<Int>,
        val events: List<String>,
    )

    private fun runFault(
        host: HostScrollRecoveryReport?,
        acceptFresh: Boolean = false,
    ): FaultRun {
        val events = mutableListOf<String>()
        val attempts = mutableListOf<Int>()
        val injector = BorrowAccessibilityScrollFaultInjector(armed = true)
        var accessibilitySwipes = 0
        var rebindCalls = 0
        var hostCalls = 0
        var hostSwipes = 0
        var hostMoved = false
        var readsAfterHost = 0

        val selection =
            selectFromBorrowList(
                BorrowListWalker(
                    maxPageGestures = 2,
                    maxSwallowedRetries = 2,
                    maxPostRebindGestures = 2,
                    readScreen = {
                        if (hostMoved) {
                            readsAfterHost++
                            BorrowScan(listOf(999.0 to "fresh"))
                        } else {
                            BorrowScan(listOf(111.0 to "stale"))
                        }
                    },
                    advancePage = { attempt ->
                        attempts += attempt
                        events += "advance:$attempt"
                        injector.dispatch(attempt) { accessibilitySwipes++ }
                    },
                    recoverService = {
                        events += "rebind"
                        rebindCalls++
                        true
                    },
                    recoverHost =
                        host?.let { report ->
                            {
                                events += "host"
                                hostCalls++
                                hostSwipes += report.swipeAttempts
                                hostMoved = report.moved
                                report
                            }
                        },
                ),
            ) { text -> acceptFresh && hostMoved && text == "fresh" }

        return FaultRun(
            selection = selection,
            injector = injector,
            accessibilitySwipes = accessibilitySwipes,
            rebindCalls = rebindCalls,
            hostCalls = hostCalls,
            hostSwipes = hostSwipes,
            readsAfterHost = readsAfterHost,
            advanceAttempts = attempts,
            events = events,
        )
    }

    private fun hostReport(
        status: InputExecutionStatus,
        movement: SwipeMovement,
        swipeAttempts: Int,
        detailCode: String = movement.name,
    ): HostScrollRecoveryReport =
        HostScrollRecoveryReport(
            scope = HostInputScope.BORROW_LIST_SCROLL,
            execution =
                InputExecutionResult(
                    status = status,
                    foreground = status == InputExecutionStatus.EXECUTED,
                    detailCode = detailCode,
                ),
            movement = movement,
            detailCode = detailCode,
            swipeAttempts = swipeAttempts,
            stopped = false,
        )

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
