package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CareerLaunchNavigator.sessionRestoreCapFor] and
 * [CareerLaunchNavigator.tpRestoreCapReachedReason] - the pure seams of the session TP-restore
 * budget.
 *
 * The bug this locks down: the cap used to be a fixed 10 justified by "one restore covers one
 * career, so 10 outruns any queue length the UI offers". Both halves were wrong - a restore
 * Max-fills TP (funding ~3 normal careers), but an Event Boost career costs 60 TP and the spark
 * reroll draws on the same counter, so a legitimate 20-run Event Boost queue needs ~19 restores
 * and died mid-queue at the cap. Worse, the cap-reached failure reused the generic out-of-TP
 * decline text, telling the user to enable a setting that was already enabled. The budget now
 * scales with the configured run count (never below the old floor of 10), and the cap-reached
 * failure states its actual cause.
 */
@DisplayName("TP restore session budget")
class TpRestoreSessionCapTest {
    @Nested
    @DisplayName("derived cap values")
    inner class CapValues {
        @Test
        fun `single run keeps the historical floor of 10`() {
            assertEquals(10, CareerLaunchNavigator.sessionRestoreCapFor(1))
        }

        @Test
        fun `four runs sit exactly on the floor boundary`() {
            // 2*4+2 = 10: the formula and the floor agree here; five runs is the first value above it.
            assertEquals(10, CareerLaunchNavigator.sessionRestoreCapFor(4))
        }

        @Test
        fun `five runs is the first derived value above the floor`() {
            assertEquals(12, CareerLaunchNavigator.sessionRestoreCapFor(5))
        }

        @Test
        fun `the production six-run queue gets 14`() {
            assertEquals(14, CareerLaunchNavigator.sessionRestoreCapFor(6))
        }

        @Test
        fun `the UI-maximum twenty-run queue gets 42, covering Event Boost plus reroll`() {
            // Worst legitimate case: ~1 launch restore + ~1 reroll restore per career = 40 < 42.
            assertEquals(42, CareerLaunchNavigator.sessionRestoreCapFor(20))
        }
    }

    @Nested
    @DisplayName("input safety")
    inner class InputSafety {
        @Test
        fun `cap is monotonic across the UI slider range`() {
            var previous = CareerLaunchNavigator.sessionRestoreCapFor(1)
            for (runs in 2..20) {
                val cap = CareerLaunchNavigator.sessionRestoreCapFor(runs)
                assertTrue(cap >= previous, "cap($runs)=$cap dropped below cap(${runs - 1})=$previous")
                previous = cap
            }
        }

        @Test
        fun `zero and negative run counts floor to 10`() {
            assertEquals(10, CareerLaunchNavigator.sessionRestoreCapFor(0))
            assertEquals(10, CareerLaunchNavigator.sessionRestoreCapFor(-5))
        }

        @Test
        fun `a corrupt run-count setting cannot make the spend bound unbounded`() {
            // Input clamps at 100 runs, so the absolute worst cap is 202 - still a hard bound.
            assertEquals(202, CareerLaunchNavigator.sessionRestoreCapFor(1000))
            assertEquals(202, CareerLaunchNavigator.sessionRestoreCapFor(Int.MAX_VALUE))
            assertTrue(CareerLaunchNavigator.sessionRestoreCapFor(Int.MIN_VALUE) == 10)
        }
    }

    @Nested
    @DisplayName("cap-reached failure message")
    inner class CapReachedMessage {
        @Test
        fun `names the cap, the counts, the saved runs, and the re-arm path`() {
            val reason = CareerLaunchNavigator.tpRestoreCapReachedReason(14, 14)
            assertTrue(reason.contains("TP restore session cap reached"), "stable greppable phrase missing: $reason")
            assertTrue(reason.contains("(14/14"), "count/max missing: $reason")
            assertTrue(reason.contains("All completed runs are saved"), "saved-runs assurance missing: $reason")
            assertTrue(reason.contains("re-arms the restore budget"), "re-arm guidance missing: $reason")
        }

        @Test
        fun `never repeats the old misleading advice to enable an already-enabled setting`() {
            val reason = CareerLaunchNavigator.tpRestoreCapReachedReason(10, 10)
            assertFalse(reason.contains("enable \"Restore TP with items\""), "old decline advice leaked back in: $reason")
        }
    }
}
