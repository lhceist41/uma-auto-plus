package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A2 build-aware launch-gate safety + wiring (source guards).
 *
 * The production launch transaction and its A2 dry-run drive real OCR, real taps, and the borrow
 * selection, so they cannot be unit-tested directly. These guards pin the load-bearing invariants
 * against the source: the A2 path NEVER taps Start Career (the tap stays strictly downstream of the
 * READY gate, which A2 never crosses), it fails closed with no legacy fallback, it reuses the proven
 * selection/verification primitives, always rolls a committed borrow back, and the diagnostic is fully
 * registered so the arming fail-closed covers it.
 */
@DisplayName("Build-aware launch gate safety + wiring")
class BuildAwareLaunchGateSafetyTest {
    private val key = "debugMode_startBuildAwareLaunchGateTest"

    private val nav by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt") }
    private val campaign by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt") }
    private val debugUi by lazy { source("src/pages/DebugSettings/index.tsx") }
    private val botState by lazy { source("src/context/BotStateContext.tsx") }
    private val gate by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/BuildAwareLaunchGate.kt") }

    private fun slice(signature: String, next: String): String {
        val start = nav.indexOf(signature)
        assertTrue(start >= 0, "$signature exists")
        val end = nav.indexOf(next, start + signature.length)
        assertTrue(end > start, "$next follows $signature")
        return nav.substring(start, end)
    }

    private fun moduleBody() = slice("internal fun prepareBuildAwareLaunchToReady(", "internal fun dryRunBuildAwareLaunchGate(")

    private fun dryRunBody() = slice("internal fun dryRunBuildAwareLaunchGate(", "private fun rollbackCommittedBorrow(")

    @Nested
    @DisplayName("Start Career stays downstream of the READY gate")
    inner class NoTap {
        @Test
        fun `the production module never taps Start Career`() {
            val body = moduleBody()
            assertFalse(body.contains("ButtonStartCareer.click"), "the module must never click Start Career")
            assertFalse(body.contains("ButtonStartCareerOffset.click"), "the module must never click the offset Start Career")
            assertFalse(body.contains("ButtonStartCareerRight.click"), "the module must never click the right Start Career")
        }

        @Test
        fun `the A2 dry-run never taps Start Career`() {
            val body = dryRunBody()
            assertFalse(body.contains("ButtonStartCareer.click"), "the dry-run must never click Start Career")
            assertFalse(body.contains("ButtonStartCareerOffset.click"), "the dry-run must never click the offset Start Career")
            assertFalse(body.contains("ButtonStartCareerRight.click"), "the dry-run must never click the right Start Career")
        }

        @Test
        fun `the only structural authority for a Start Career tap is canStartCareer(READY)`() {
            assertTrue(gate.contains("fun canStartCareer(state: LaunchTransactionState): Boolean = state == LaunchTransactionState.READY_TO_START_CAREER"), "the gate exposes the single READY-only tap predicate")
        }
    }

    @Nested
    @DisplayName("fail closed + reuse")
    inner class FailClosed {
        @Test
        fun `the module fails closed to BORROW_NOT_AVAILABLE when the intent is not build-aware (no legacy fallback)`() {
            val body = moduleBody()
            assertTrue(body.contains("IntentRecommendationSource.BUILD_AWARE"), "the module requires a BUILD_AWARE intent")
            assertTrue(body.contains("BORROW_NOT_AVAILABLE") || body.contains("BuildAwareLaunchGate.evaluate"), "the module routes a non-build-aware intent to a blocked state")
            assertFalse(body.contains("SmartBorrowList") || body.contains("fillEmptyFriendSlot"), "the module must not fall back to the priority-list borrow")
        }

        @Test
        fun `the module reuses the proven locate, selection, and verification primitives`() {
            val body = moduleBody()
            assertTrue(body.contains("locateSmartBorrowIntentReadOnly("), "reuses the fresh read-only locate")
            assertTrue(body.contains("selectFromBorrowList("), "reuses the exact-row selection primitive")
            assertTrue(body.contains("readSelectedSlotVerification("), "reuses the committed-slot verification")
            assertTrue(body.contains("BuildAwareLaunchGate.evaluate("), "the READY verdict comes from the pure gate")
        }

        @Test
        fun `the dry-run always rolls a committed borrow back`() {
            val body = dryRunBody()
            assertTrue(body.contains("rollbackCommittedBorrow()"), "the dry-run rolls back any committed borrow")
            assertTrue(body.contains("startCareerTapped = false"), "the dry-run records that Start Career was never tapped")
        }
    }

    @Nested
    @DisplayName("registry + routing (arming fail-closed covers it)")
    inner class Registry {
        @Test
        fun `the key is in the canonical DebugTestGate registry`() {
            assertTrue(DebugTestGate.ALL_KEYS.contains(key), "ALL_KEYS must include the launch-gate key")
        }

        @Test
        fun `the key has a user-facing Debug Settings entry defaulting to false`() {
            assertTrue(debugUi.contains("\"$key\""), "the key is in the debugTestKeys list")
            assertTrue(debugUi.contains("Start Build-Aware Launch Gate Dry-Run"), "the launch-gate dry-run has a user-facing entry")
            assertTrue(botState.contains("$key: false,"), "the default in BotStateContext must be false")
        }

        @Test
        fun `Campaign routes the key to a handler that invokes the navigator dry-run`() {
            assertTrue(campaign.contains("\"$key\" to ::startBuildAwareLaunchGateTest"), "the fnMap routes the launch-gate key")
            val handler = campaign.substring(campaign.indexOf("open fun startBuildAwareLaunchGateTest("))
            assertTrue(handler.contains("dryRunBuildAwareLaunchGate("), "the handler invokes the navigator dry-run")
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
