package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ProductionHostScrollRecoveryTest {
    private val targetId = "a".repeat(32)
    private val pairingCode = "v1.$targetId.${HostAdbProtocol.encodeBase64Url(ByteArray(32) { (it + 1).toByte() })}"
    private val request = BoundedSwipe(HostInputScope.BORROW_LIST_SCROLL, 5000, 7200, 5000, 3500, 900)

    @Test
    fun `missing old and unknown modes preserve accessibility only behavior`() {
        for (mode in listOf<String?>(null, "", "accessibility_only", "host_adb", "unknown")) {
            val run = recover(modeRaw = mode)
            assertEquals("HOST_INPUT_DISABLED", run.result.detailCode, "mode=$mode")
            assertEquals(0, run.openCount, "mode=$mode")
            assertEquals(0, run.transport.swipeCount, "mode=$mode")
        }
    }

    @Test
    fun `enabled mode without valid pairing never opens transport`() {
        for (code in listOf<String?>(null, "", "v1.bad.bad")) {
            val run = recover(pairingCodeRaw = code)
            assertEquals("HOST_INPUT_UNPAIRED", run.result.detailCode)
            assertEquals(0, run.openCount)
            assertEquals(0, run.transport.swipeCount)
        }
    }

    @Test
    fun `health target foreground and capability gates fail before input`() {
        val cases =
            listOf(
                InputHealth(InputExecutionStatus.UNAVAILABLE, false, emptySet(), targetId) to "HEALTH_UNAVAILABLE",
                healthy().copy(targetId = "b".repeat(32)) to "TARGET_MISMATCH",
                healthy().copy(foreground = false) to "FOREGROUND_REJECTED",
                healthy().copy(capabilities = setOf(InputCapability.HEALTH)) to "SWIPE_UNAVAILABLE",
            )
        for ((health, detail) in cases) {
            val run = recover(health = health)
            assertEquals(detail, run.result.detailCode)
            assertEquals(0, run.transport.swipeCount, detail)
            assertEquals(0, run.prepareCount, detail)
        }
    }

    @Test
    fun `executed moved result authorizes recovery after one swipe`() {
        val run = recover(samples = fingerprints("B", "B"))

        assertTrue(run.result.moved)
        assertEquals(SwipeMovement.MOVED, run.result.movement)
        assertEquals("MOVED", run.result.detailCode)
        assertEquals(1, run.result.swipeAttempts)
        assertEquals(1, run.transport.swipeCount)
        assertEquals(1, run.prepareCount)
    }

    @Test
    fun `executed no effect fails closed without retry`() {
        val run = recover(samples = fingerprints("A", "A"))

        assertFalse(run.result.moved)
        assertEquals(SwipeMovement.NO_EFFECT, run.result.movement)
        assertEquals("NO_EFFECT", run.result.detailCode)
        assertEquals(1, run.transport.swipeCount)
    }

    @Test
    fun `unstable Android evidence stays uncertain without retry`() {
        val run = recover(samples = fingerprints("B", "C", "D", "E", "F", "G", "H", "I"))

        assertFalse(run.result.moved)
        assertEquals(SwipeMovement.UNCERTAIN, run.result.movement)
        assertEquals("UNCERTAIN", run.result.detailCode)
        assertEquals(1, run.transport.swipeCount)
        assertEquals(HOST_SWIPE_SETTLE_MAX_SAMPLES, run.captureCount)
    }

    @Test
    fun `rejected unavailable and ambiguous transport results never retry`() {
        for (status in listOf(InputExecutionStatus.REJECTED, InputExecutionStatus.UNAVAILABLE, InputExecutionStatus.AMBIGUOUS)) {
            val run = recover(execution = InputExecutionResult(status, foreground = false, detailCode = "TRANSPORT_${status.wire}"))
            assertFalse(run.result.moved)
            assertEquals(status, run.result.execution.status)
            assertEquals("TRANSPORT_${status.wire}", run.result.detailCode)
            assertEquals(1, run.transport.swipeCount, status.wire)
            assertEquals(0, run.captureCount, status.wire)
        }
    }

    @Test
    fun `stop before recovery performs no health check or input`() {
        val run = recover(stoppedInitially = true)

        assertTrue(run.result.stopped)
        assertEquals("STOP_REQUESTED", run.result.detailCode)
        assertEquals(0, run.openCount)
        assertEquals(0, run.transport.healthCount)
        assertEquals(0, run.transport.swipeCount)
    }

    @Test
    fun `stop after health or fresh preparation still performs no input`() {
        val afterHealth = recover(stopAfterHealth = true)
        assertTrue(afterHealth.result.stopped)
        assertEquals(1, afterHealth.transport.healthCount)
        assertEquals(0, afterHealth.prepareCount)
        assertEquals(0, afterHealth.transport.swipeCount)

        val afterPrepare = recover(stopAfterPrepare = true)
        assertTrue(afterPrepare.result.stopped)
        assertEquals(1, afterPrepare.transport.healthCount)
        assertEquals(1, afterPrepare.prepareCount)
        assertEquals(0, afterPrepare.transport.swipeCount)
    }

    @Test
    fun `stop during settle performs no second input or capture`() {
        val run = recover(samples = fingerprints("B", "B"), stopAfterWaitCount = 1)

        assertTrue(run.result.stopped)
        assertEquals("STOP_REQUESTED", run.result.detailCode)
        assertEquals(1, run.transport.swipeCount)
        assertEquals(0, run.captureCount)
    }

    @Test
    fun `fresh screen recognition and geometry are mandatory after health`() {
        val unrecognized = recover(prepared = PreparedHostSwipe(ScreenFingerprint(false, "A"), request))
        assertEquals("SCREEN_NOT_RECOGNIZED", unrecognized.result.detailCode)
        assertEquals(0, unrecognized.transport.swipeCount)

        val blank = recover(prepared = PreparedHostSwipe(ScreenFingerprint(true, ""), request))
        assertEquals("SCREEN_NOT_RECOGNIZED", blank.result.detailCode)
        assertEquals(0, blank.transport.swipeCount)

        val wrongScope = recover(prepared = PreparedHostSwipe(ScreenFingerprint(true, "A"), request.copy(scope = HostInputScope.LEGACY_LIST_SCROLL)))
        assertEquals("INVALID_GEOMETRY", wrongScope.result.detailCode)
        assertEquals(0, wrongScope.transport.swipeCount)
    }

    @Test
    fun `transport is closed on success and every post-open rejection`() {
        val success = recover(samples = fingerprints("B", "B"))
        assertTrue(success.transport.closed)

        val rejected = recover(health = healthy().copy(foreground = false))
        assertTrue(rejected.transport.closed)
    }

    @Test
    fun `production and diagnostic gates remain distinct and cannot select or start`() {
        val source = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt").readText()
        val production = source.substringAfter("private fun recoverBorrowScrollWithHost").substringBefore("/** Sanitized row text")
        assertTrue(production.contains("executeProductionHostScrollRecovery"))
        assertFalse(production.contains("runHostSwipeDiagnostic"))
        assertFalse(production.contains("CoordinateTap"))
        assertFalse(production.contains("ButtonStartCareer"))

        val diagnostic = source.substringAfter("internal fun rehearseHostBorrowSwipe").substringBefore("internal fun rehearseHostLegacySwipe")
        assertTrue(diagnostic.contains("runHostSwipeDiagnostic"))
        assertFalse(diagnostic.contains("recoverBorrowScrollWithHost"))
    }

    private fun healthy(): InputHealth =
        InputHealth(
            status = InputExecutionStatus.EXECUTED,
            foreground = true,
            capabilities = setOf(InputCapability.HEALTH, InputCapability.SWIPE),
            targetId = targetId,
        )

    private fun fingerprints(vararg values: String): List<ScreenFingerprint> =
        values.map { ScreenFingerprint(recognized = true, value = it) }

    private fun recover(
        modeRaw: String? = HostInputMode.ACCESSIBILITY_WITH_HOST.wire,
        pairingCodeRaw: String? = pairingCode,
        health: InputHealth = healthy(),
        execution: InputExecutionResult = InputExecutionResult(InputExecutionStatus.EXECUTED, true, "ADB_EXIT_0"),
        prepared: PreparedHostSwipe = PreparedHostSwipe(ScreenFingerprint(true, "A"), request),
        samples: List<ScreenFingerprint> = fingerprints("B", "B"),
        stoppedInitially: Boolean = false,
        stopAfterHealth: Boolean = false,
        stopAfterPrepare: Boolean = false,
        stopAfterWaitCount: Int? = null,
    ): RecoveryHarness {
        var stopped = stoppedInitially
        val transport = FakeTransport(health, execution) { if (stopAfterHealth) stopped = true }
        var openCount = 0
        var prepareCount = 0
        var captureCount = 0
        var waitCount = 0
        val result =
            executeProductionHostScrollRecovery(
                scope = HostInputScope.BORROW_LIST_SCROLL,
                modeRaw = modeRaw,
                pairingCodeRaw = pairingCodeRaw,
                openTransport = {
                    openCount++
                    transport
                },
                prepareSwipe = {
                    prepareCount++
                    if (stopAfterPrepare) stopped = true
                    prepared
                },
                shouldStop = { stopped },
                waitBeforeSample = {
                    waitCount++
                    if (stopAfterWaitCount != null && waitCount >= stopAfterWaitCount) stopped = true
                },
                captureAfter = { samples[captureCount++] },
            )
        return RecoveryHarness(result, transport, openCount, prepareCount, captureCount)
    }

    private class FakeTransport(
        private val healthResult: InputHealth,
        private val executionResult: InputExecutionResult,
        private val onHealth: () -> Unit = {},
    ) : InputTransport {
        override val authority: InputTransportAuthority = InputTransportAuthority.HOST_ADB
        var healthCount = 0
        var swipeCount = 0
        var closed = false

        override fun health(): InputHealth {
            healthCount++
            onHealth()
            return healthResult
        }

        override fun swipe(request: BoundedSwipe): InputExecutionResult {
            swipeCount++
            return executionResult
        }

        override fun close() {
            closed = true
        }
    }

    private data class RecoveryHarness(
        val result: HostScrollRecoveryReport,
        val transport: FakeTransport,
        val openCount: Int,
        val prepareCount: Int,
        val captureCount: Int,
    )

    private fun repoFile(relative: String): File {
        var current: File? = File(".").absoluteFile
        repeat(8) {
            val candidate = File(current, relative)
            if (candidate.isFile) return candidate
            current = current?.parentFile
        }
        error("Repository file not found: $relative")
    }
}
