package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The bounded-execution guard that keeps a wedged capture/OCR from hanging the queue thread. It
 * must: return a fast block's value promptly, hand back [onTimeout] when a block overruns (without
 * waiting out the block), tell a legitimate null result apart from a timeout, propagate a block's
 * exception, and short-circuit on [shouldAbort].
 */
@DisplayName("BoundedExecution.runWithDeadline")
class BoundedExecutionTest {
    @Test
    fun `a fast block returns its value`() {
        val result = BoundedExecution.runWithDeadline(timeoutMs = 2_000L, onTimeout = { "timeout" }) { "ok" }
        assertEquals("ok", result)
    }

    @Test
    fun `an overrunning block returns onTimeout without waiting it out`() {
        val started = System.currentTimeMillis()
        val ran = AtomicBoolean(false)
        val result =
            BoundedExecution.runWithDeadline(timeoutMs = 200L, pollMs = 20L, onTimeout = { "bounded" }) {
                Thread.sleep(5_000L)
                ran.set(true)
                "should never surface"
            }
        val elapsed = System.currentTimeMillis() - started
        assertEquals("bounded", result)
        assertTrue(elapsed < 2_000L, "the wait must return near the deadline, not after the block; elapsed=${elapsed}ms")
    }

    @Test
    fun `a block that returns null is not mistaken for a timeout`() {
        val result: String? = BoundedExecution.runWithDeadline(timeoutMs = 2_000L, onTimeout = { "timeout" }) { null }
        assertNull(result, "a legitimate null result must be returned, not replaced by onTimeout")
    }

    @Test
    fun `a block exception propagates to the caller`() {
        val thrown =
            assertThrows(IllegalStateException::class.java) {
                BoundedExecution.runWithDeadline(timeoutMs = 2_000L, onTimeout = { "timeout" }) {
                    throw IllegalStateException("boom")
                }
            }
        assertEquals("boom", thrown.message)
    }

    @Test
    fun `shouldAbort short-circuits to onTimeout`() {
        val started = System.currentTimeMillis()
        val result =
            BoundedExecution.runWithDeadline(
                timeoutMs = 60_000L,
                pollMs = 20L,
                shouldAbort = { true },
                onTimeout = { "aborted" },
            ) {
                Thread.sleep(60_000L)
                "should never surface"
            }
        val elapsed = System.currentTimeMillis() - started
        assertEquals("aborted", result)
        assertTrue(elapsed < 2_000L, "shouldAbort must not wait out the deadline; elapsed=${elapsed}ms")
    }
}
