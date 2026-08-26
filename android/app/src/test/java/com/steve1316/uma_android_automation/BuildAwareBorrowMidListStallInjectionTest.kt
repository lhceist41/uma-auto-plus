package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A3-R11 deterministic mid-list host-recovery proof seam.
 *
 * Three natural build-aware Borrow LOCATE host-recovery attempts already proved the production ladder
 * end to end, but the natural no-movement gap only reproduces near the reproducible ~31-row list tail,
 * where the Android movement verifier truthfully returns UNCERTAIN. [BuildAwareBorrowMidListStallInjector]
 * lets a validation run open that same gap deterministically, mid-list, after real semantic forward
 * progress -- so the production host swipe, its Android movement verification, and the fresh
 * post-host read can all be exercised on demand instead of waiting for the natural swallow.
 *
 * These tests drive the real [BorrowListWalker] with the real injector wired exactly as
 * [locateSmartBorrowIntentReadOnly] wires it, entirely offline. Source guards pin that only the
 * build-aware locate walker is affected, and that the existing R2 injector and DebugTestGate arming
 * are unchanged.
 */
@DisplayName("A3-R11 build-aware mid-list Accessibility stall injection")
class BuildAwareBorrowMidListStallInjectionTest {
    private val nav by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt") }
    private val debugUi by lazy { source("src/pages/DebugSettings/index.tsx") }
    private val botState by lazy { source("src/context/BotStateContext.tsx") }
    private val injectorSource by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/BuildAwareBorrowMidListStallInjector.kt") }
    private val r2InjectorSource by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/BorrowAccessibilityScrollFaultInjector.kt") }

    private fun slice(signature: String, next: String): String {
        val start = nav.indexOf(signature)
        assertTrue(start >= 0, "$signature exists")
        val end = nav.indexOf(next, start + signature.length)
        assertTrue(end > start, "$next follows $signature")
        return nav.substring(start, end)
    }

    private fun locateBody() = slice("internal fun locateSmartBorrowIntentReadOnly(", "private fun persistSmartBorrowLocate(")

    private fun selectBody() = slice("private fun selectBorrowByIdentityRevalidated(", "private fun revalidateAndTapBorrow(")

    private fun selectedSlotRowsBody() = slice("private fun readSelectedSlotRows(", "private fun readSelectedSlotVerification(")

    private fun defaultWalkerBody() = slice("private fun borrowWalker(): BorrowListWalker =", "private fun recoverBorrowScrollWithHost(): HostScrollRecoveryReport =")

    private fun censusBody() = slice("internal fun scanBorrowPoolReadOnly(", "private fun nameKeyOf(obs: BorrowRowObservation):")

    private fun legacyBody() = slice("private fun handleLegacySelectScreen(", "private fun captureLineageTelemetry")

    @Nested
    @DisplayName("setting resolution and defaults")
    inner class SettingResolution {
        @Test
        fun `1 missing old and malformed settings remain off`() {
            for (raw in listOf<String?>(null, "", "false", "0", "yes", "true-ish")) {
                assertFalse(buildAwareBorrowMidListStallEnabled(raw), "raw=$raw")
            }
            assertTrue(buildAwareBorrowMidListStallEnabled("true"))
            assertTrue(buildAwareBorrowMidListStallEnabled(" TRUE "))
        }

        @Test
        fun `2 the setting has a false default in BotStateContext`() {
            assertTrue(botState.contains("debugMode_injectBuildAwareBorrowMidListAccessibilityStall: boolean"))
            assertTrue(botState.contains("debugMode_injectBuildAwareBorrowMidListAccessibilityStall: false,"))
        }

        @Test
        fun `3 the injector is constructed fresh inside locateSmartBorrowIntentReadOnly, reading the setting each call`() {
            val body = locateBody()
            assertTrue(body.contains("BuildAwareBorrowMidListStallInjector("), "must be constructed locally per locate call")
            assertTrue(body.contains("buildAwareBorrowMidListStallEnabled("))
            assertTrue(body.contains("getStringSetting(\"debug\", BUILD_AWARE_BORROW_MID_LIST_STALL_SETTING)"))
        }
    }

    @Nested
    @DisplayName("dispatch semantics (pure)")
    inner class DispatchSemantics {
        @Test
        fun `4 explicit off dispatches the normal swipe regardless of progress`() {
            var swipes = 0
            val injector = BuildAwareBorrowMidListStallInjector(armed = false, threshold = 3)

            val dispatched = injector.dispatch(uniqueRows = 100, attempt = 0) { swipes++ }

            assertTrue(dispatched)
            assertEquals(1, swipes)
            assertFalse(injector.injected)
        }

        @Test
        fun `5 armed but below threshold dispatches the normal swipe`() {
            var swipes = 0
            val injector = BuildAwareBorrowMidListStallInjector(armed = true, threshold = 10)

            val dispatched = injector.dispatch(uniqueRows = 9, attempt = 0) { swipes++ }

            assertTrue(dispatched)
            assertEquals(1, swipes)
            assertFalse(injector.injected)
            assertEquals(0, injector.suppressedAttempts)
        }

        @Test
        fun `6 threshold reached begins exactly one injected gap and swallows the dispatch`() {
            var swipes = 0
            val logs = mutableListOf<String>()
            val injector = BuildAwareBorrowMidListStallInjector(armed = true, threshold = 10, log = logs::add)

            val dispatched = injector.dispatch(uniqueRows = 10, attempt = 0) { swipes++ }

            assertFalse(dispatched)
            assertEquals(0, swipes)
            assertTrue(injector.injected)
            assertEquals(1, injector.suppressedAttempts)
            assertTrue(logs.any { it.contains("FAULT_INJECTED") && it.contains("uniqueRows=10") })
        }

        @Test
        fun `7 once injected, further calls keep swallowing until release`() {
            var swipes = 0
            val logs = mutableListOf<String>()
            val injector = BuildAwareBorrowMidListStallInjector(armed = true, threshold = 5, log = logs::add)
            injector.dispatch(uniqueRows = 5, attempt = 0) { swipes++ }

            val dispatched = injector.dispatch(uniqueRows = 5, attempt = 1) { swipes++ }

            assertFalse(dispatched)
            assertEquals(0, swipes)
            assertEquals(2, injector.suppressedAttempts)
            assertTrue(logs.any { it.contains("continuing injected ladder") && it.contains("attempt=1") })
        }

        @Test
        fun `8 release unlatches dispatch and is one-shot itself`() {
            val logs = mutableListOf<String>()
            val injector = BuildAwareBorrowMidListStallInjector(armed = true, threshold = 5, log = logs::add)
            injector.dispatch(uniqueRows = 5, attempt = 0) {}

            injector.release()
            injector.release()

            var swipes = 0
            val dispatched = injector.dispatch(uniqueRows = 999, attempt = 0) { swipes++ }
            assertTrue(dispatched)
            assertEquals(1, swipes)
            assertEquals(1, logs.count { it.contains("RELEASED") }, "release must log exactly once even if called twice")
        }

        @Test
        fun `9 released injector never opens a second gap even when threshold stays crossed`() {
            val injector = BuildAwareBorrowMidListStallInjector(armed = true, threshold = 5)
            injector.dispatch(uniqueRows = 5, attempt = 0) {}
            injector.release()

            var swipes = 0
            repeat(5) { injector.dispatch(uniqueRows = 999, attempt = 0) { swipes++ } }

            assertEquals(5, swipes, "every post-release call must be a real dispatch")
            assertEquals(1, injector.suppressedAttempts, "no second gap opened")
        }
    }

    @Nested
    @DisplayName("full walker wiring (behavioral, mirrors production)")
    inner class WalkerWiring {
        @Test
        fun `10 the full ladder is swallowed then the host rung runs exactly once and only after it`() {
            val run = runFault(threshold = 6, host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.MOVED, 1))

            assertEquals(listOf(0, 0, 0, 1, 2, 0, 0, 0, 0), run.advanceAttempts)
            assertEquals(5, run.injector.suppressedAttempts)
            assertEquals(4, run.accessibilitySwipes, "2 real advances before the gap, 2 real advances after release")
            assertEquals(1, run.rebindCalls)
            assertEquals(1, run.hostCalls)
            assertEquals(1, run.hostSwipes)
            val rebindAt = run.events.indexOf("rebind")
            val hostAt = run.events.indexOf("host")
            assertTrue(rebindAt in 0 until hostAt, "host recovery must follow the rebind")
        }

        @Test
        fun `11 host MOVED clears the stale signature and traversal continues to a natural end`() {
            val run = runFault(threshold = 6, host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.MOVED, 1))

            assertTrue(run.readsAfterHost > 0, "at least one fresh read must follow the host recovery")
            assertEquals(BorrowWalkEnd.END_OF_LIST, run.walk.end)
            assertFalse(run.walk.stalled)
            assertTrue(run.walk.fullyTraversed)
            assertTrue(run.observedRows.containsAll(listOf("fresh-6", "fresh-7", "fresh-8", "fresh-9")), "post-host pages must be genuinely read")
        }

        @Test
        fun `12 threshold remains crossed after MOVED but no second gap opens`() {
            val run = runFault(threshold = 6, host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.MOVED, 1))

            assertEquals(1, run.hostCalls, "only the one host rung of the injected gap; no second one")
            assertEquals(5, run.injector.suppressedAttempts, "the suppressed count does not grow after release")
            assertEquals(1, run.logs.count { it.contains("FAULT_INJECTED") })
        }

        @Test
        fun `13 host UNCERTAIN fails closed without a second attempt`() {
            val run = runFault(threshold = 6, host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.UNCERTAIN, 1))

            assertEquals(1, run.hostCalls)
            assertTrue(run.walk.stalled)
            assertFalse(run.walk.fullyTraversed)
            assertTrue(run.injector.released, "the injector still releases even though the host outcome was not MOVED")
        }

        @Test
        fun `14 host NO_EFFECT and REJECTED fail closed without a second attempt`() {
            for (report in listOf(
                hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.NO_EFFECT, 1),
                hostReport(InputExecutionStatus.REJECTED, SwipeMovement.UNCERTAIN, 0, "FOREGROUND_REJECTED"),
            )) {
                val run = runFault(threshold = 6, host = report)
                assertEquals(1, run.hostCalls, report.detailCode)
                assertTrue(run.walk.stalled, report.detailCode)
            }
        }

        @Test
        fun `15 no host callback wired means the gap simply stalls, never bypassing recovery`() {
            val run = runFault(threshold = 6, host = null)

            assertEquals(0, run.hostCalls)
            assertTrue(run.walk.stalled)
            assertTrue(run.injector.injected)
            assertFalse(run.injector.released, "release() is only reachable from inside recoverHost")
        }

        @Test
        fun `16 armed but threshold never reached leaves the whole walk real`() {
            val run = runFault(threshold = 100, host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.MOVED, 1))

            assertFalse(run.injector.injected)
            assertEquals(0, run.injector.suppressedAttempts)
            assertEquals(0, run.hostCalls)
            assertEquals(0, run.rebindCalls)
        }

        @Test
        fun `17 explicit off leaves the whole walk real even past the natural threshold value`() {
            val run = runFault(threshold = 6, host = hostReport(InputExecutionStatus.EXECUTED, SwipeMovement.MOVED, 1), armed = false)

            assertFalse(run.injector.injected)
            assertEquals(0, run.injector.suppressedAttempts)
            assertEquals(0, run.hostCalls)
        }
    }

    @Nested
    @DisplayName("release ordering (source guard - a lambda body's own statement order cannot be proven by a mock)")
    inner class ReleaseOrdering {
        @Test
        fun `18 the locate walker releases the injector before calling the real host recovery`() {
            val body = locateBody()
            val recoverHostAt = body.indexOf("recoverHost = {")
            assertTrue(recoverHostAt >= 0)
            val releaseAt = body.indexOf("midListStallInjector.release()", recoverHostAt)
            val hostCallAt = body.indexOf("recoverBorrowScrollWithHost()", recoverHostAt)
            assertTrue(releaseAt in (recoverHostAt + 1) until hostCallAt, "release() must run before the real host recovery call")
        }

        @Test
        fun `19 the locate walker does not call the host recovery from within the injector itself`() {
            // "recoverHost" itself is legitimately named in the KDoc (documenting when release() must be
            // called relative to it); what must be absent is the injector actually reaching host authority.
            assertFalse(injectorSource.contains("recoverBorrowScrollWithHost"))
            assertFalse(injectorSource.contains("HostAdbInputTransport"))
            assertFalse(injectorSource.contains("verifySwipeMovement"))
            assertFalse(injectorSource.contains("hostRecoveryAttempted"))
            assertFalse(injectorSource.contains("recoverHost("), "the injector must not invoke a recoverHost callback itself")
            assertFalse(injectorSource.contains("recoverHost.invoke"), "the injector must not invoke a recoverHost callback itself")
        }
    }

    @Nested
    @DisplayName("build-aware locate isolation (source guards)")
    inner class Isolation {
        @Test
        fun `20 build-aware select-revalidate is unaffected`() {
            val body = selectBody()
            assertFalse(body.contains("BuildAwareBorrowMidListStallInjector"))
            assertFalse(body.contains(BUILD_AWARE_BORROW_MID_LIST_STALL_SETTING))
        }

        @Test
        fun `21 Selected-marker verification is unaffected`() {
            val body = selectedSlotRowsBody()
            assertFalse(body.contains("BuildAwareBorrowMidListStallInjector"))
            assertFalse(body.contains(BUILD_AWARE_BORROW_MID_LIST_STALL_SETTING))
        }

        @Test
        fun `22 default Smart Borrow (borrowWalker) is unaffected`() {
            val body = defaultWalkerBody()
            assertFalse(body.contains("BuildAwareBorrowMidListStallInjector"))
            assertTrue(body.contains("advanceProductionBorrowList"), "the default walker keeps its own R2-gated advance")
        }

        @Test
        fun `23 the read-only census walker is unaffected`() {
            val body = censusBody()
            assertFalse(body.contains("BuildAwareBorrowMidListStallInjector"))
        }

        @Test
        fun `24 Legacy is unaffected`() {
            val body = legacyBody()
            assertFalse(body.contains("BuildAwareBorrowMidListStallInjector"))
            assertFalse(body.contains(BUILD_AWARE_BORROW_MID_LIST_STALL_SETTING))
        }

        @Test
        fun `25 the existing R2 injector is untouched by the new seam`() {
            assertFalse(r2InjectorSource.contains("BuildAwareBorrowMidListStallInjector"))
            assertFalse(r2InjectorSource.contains(BUILD_AWARE_BORROW_MID_LIST_STALL_SETTING))
        }

        @Test
        fun `26 the locate walker never introduces a host TAP or references Start Career`() {
            val body = locateBody()
            assertFalse(body.contains("CoordinateTap"))
            assertFalse(body.contains("ButtonStartCareer"))
        }
    }

    @Nested
    @DisplayName("DebugTestGate and READY interaction")
    inner class DebugGateInteraction {
        @Test
        fun `27 the new key is absent from the canonical DebugTestGate registry`() {
            assertFalse(DebugTestGate.ALL_KEYS.contains(BUILD_AWARE_BORROW_MID_LIST_STALL_SETTING))
        }

        @Test
        fun `28 the new key is absent from the standalone debug-test conflict list`() {
            val block = Regex("const debugTestKeys = \\[(.*?)] as const", RegexOption.DOT_MATCHES_ALL).find(debugUi)?.groupValues?.get(1) ?: error("debugTestKeys missing")
            assertFalse(block.contains(BUILD_AWARE_BORROW_MID_LIST_STALL_SETTING))
        }

        @Test
        fun `29 the key still has a user-facing diagnostic control`() {
            assertTrue(debugUi.contains("debugMode_injectBuildAwareBorrowMidListAccessibilityStall"))
            assertTrue(debugUi.contains("Inject Build-Aware Borrow Mid-List Stall"))
        }
    }

    private data class FaultRun(
        val walk: BorrowWalkResult,
        val injector: BuildAwareBorrowMidListStallInjector,
        val accessibilitySwipes: Int,
        val rebindCalls: Int,
        val hostCalls: Int,
        val hostSwipes: Int,
        val readsAfterHost: Int,
        val advanceAttempts: List<Int>,
        val events: List<String>,
        val observedRows: Set<String>,
        val logs: List<String>,
    )

    /** Six synthetic screens: three "stale" screens (6 unique rows -> crosses a threshold of 6 on the
     * third), two "fresh" screens reachable only after a host MOVED, and a natural tail that repeats
     * the last fresh row so the walk ends at a genuine END_OF_LIST instead of a stall. */
    private fun runFault(
        threshold: Int,
        host: HostScrollRecoveryReport?,
        armed: Boolean = true,
    ): FaultRun {
        val pages =
            listOf(
                listOf(0.0 to "stale-0", 1.0 to "stale-1"),
                listOf(2.0 to "stale-2", 3.0 to "stale-3"),
                listOf(4.0 to "stale-4", 5.0 to "stale-5"),
                listOf(6.0 to "fresh-6", 7.0 to "fresh-7"),
                listOf(8.0 to "fresh-8", 9.0 to "fresh-9"),
                listOf(8.0 to "fresh-8"),
            )
        val events = mutableListOf<String>()
        val attempts = mutableListOf<Int>()
        val logs = mutableListOf<String>()
        val observed = LinkedHashSet<String>()
        val injector = BuildAwareBorrowMidListStallInjector(armed = armed, threshold = threshold, log = logs::add)
        var pageIndex = 0
        var accessibilitySwipes = 0
        var rebindCalls = 0
        var hostCalls = 0
        var hostSwipes = 0
        var hostMoved = false
        var readsAfterHost = 0

        val walker =
            BorrowListWalker(
                maxPageGestures = 20,
                maxSwallowedRetries = 2,
                maxPostRebindGestures = 2,
                readScreen = {
                    if (hostMoved) readsAfterHost++
                    BorrowScan(pages[pageIndex])
                },
                advancePage = { attempt ->
                    attempts += attempt
                    events += "advance:$attempt"
                    injector.dispatch(observed.size, attempt) {
                        accessibilitySwipes++
                        if (pageIndex < pages.lastIndex) pageIndex++
                    }
                },
                recoverService = {
                    events += "rebind"
                    rebindCalls++
                    true
                },
                recoverHost =
                    host?.let { report ->
                        {
                            injector.release()
                            events += "host"
                            hostCalls++
                            hostSwipes += report.swipeAttempts
                            if (report.moved) {
                                hostMoved = true
                                if (pageIndex < pages.lastIndex) pageIndex++
                            }
                            report
                        }
                    },
            )
        val walk =
            walker.walk { screen, _ ->
                for ((_, text) in screen.rows) observed.add(text)
                false
            }

        return FaultRun(
            walk = walk,
            injector = injector,
            accessibilitySwipes = accessibilitySwipes,
            rebindCalls = rebindCalls,
            hostCalls = hostCalls,
            hostSwipes = hostSwipes,
            readsAfterHost = readsAfterHost,
            advanceAttempts = attempts,
            events = events,
            observedRows = observed,
            logs = logs,
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

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(8) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }
}
