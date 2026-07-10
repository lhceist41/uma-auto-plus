package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DatingSchedule], the pure helpers driving the support-card recreation schedule.
 *
 * Covers pinned-turn membership, whether the final outing may start, and the hold-the-final-outing gating for both Team Sirius (7 outings) and Heirs to the Throne (5 outings).
 */
@DisplayName("DatingSchedule Tests")
class DatingScheduleTest {
    @Nested
    @DisplayName("isPinnedRecreationTurn()")
    inner class IsPinnedRecreationTurnTests {
        @Test
        fun `regular recreation turn is pinned`() {
            assertTrue(DatingSchedule.isPinnedRecreationTurn(56, setOf(50, 56), 60))
        }

        @Test
        fun `pure passion turn is pinned`() {
            assertTrue(DatingSchedule.isPinnedRecreationTurn(60, setOf(50, 56), 60))
        }

        @Test
        fun `unpinned turn is not pinned`() {
            assertFalse(DatingSchedule.isPinnedRecreationTurn(40, setOf(50, 56), 60))
        }

        @Test
        fun `unset pure passion turn does not match turn zero`() {
            assertFalse(DatingSchedule.isPinnedRecreationTurn(0, setOf(50, 56), -1))
        }
    }

    @Nested
    @DisplayName("allowFinalOuting()")
    inner class AllowFinalOutingTests {
        @Test
        fun `final outing is allowed when the schedule is disabled`() {
            assertTrue(DatingSchedule.allowFinalOuting(enableDatingSchedule = false, purePassionTurn = 60, currentTurn = 35))
        }

        @Test
        fun `Sirius allows every outing when no pure passion turn is set`() {
            assertTrue(DatingSchedule.allowFinalOuting(enableDatingSchedule = true, purePassionTurn = -1, currentTurn = 58))
        }

        @Test
        fun `Throne holds the final before the pure passion turn`() {
            assertFalse(DatingSchedule.allowFinalOuting(enableDatingSchedule = true, purePassionTurn = 60, currentTurn = 58))
        }

        @Test
        fun `Throne allows the final on the pure passion turn`() {
            assertTrue(DatingSchedule.allowFinalOuting(enableDatingSchedule = true, purePassionTurn = 60, currentTurn = 60))
        }
    }

    @Nested
    @DisplayName("shouldHoldFinalOuting()")
    inner class ShouldHoldFinalOutingTests {
        @Test
        fun `holds the final on a regular turn when six of seven outings are done`() {
            assertTrue(DatingSchedule.shouldHoldFinalOuting(6, 7, false))
        }

        @Test
        fun `starts an outing on a regular turn when five of seven outings are done`() {
            assertFalse(DatingSchedule.shouldHoldFinalOuting(5, 7, false))
        }

        @Test
        fun `starts the final once the final outing is allowed`() {
            assertFalse(DatingSchedule.shouldHoldFinalOuting(6, 7, true))
        }

        @Test
        fun `Throne holds the final on a regular turn when four of five outings are done`() {
            assertTrue(DatingSchedule.shouldHoldFinalOuting(4, 5, false))
        }

        @Test
        fun `Throne starts the final once the final outing is allowed`() {
            assertFalse(DatingSchedule.shouldHoldFinalOuting(4, 5, true))
        }
    }

    @Nested
    @DisplayName("isBehindSchedule()")
    inner class IsBehindScheduleTests {
        @Test
        fun `on track when every due turn has an outing`() {
            assertFalse(DatingSchedule.isBehindSchedule(currentTurn = 44, recreationTurns = setOf(35, 43, 52, 58), outingsStarted = 2))
        }

        @Test
        fun `behind when a due turn was missed`() {
            assertTrue(DatingSchedule.isBehindSchedule(currentTurn = 44, recreationTurns = setOf(35, 43, 52, 58), outingsStarted = 1))
        }

        @Test
        fun `not behind before the first pinned turn`() {
            assertFalse(DatingSchedule.isBehindSchedule(currentTurn = 30, recreationTurns = setOf(35, 43, 52, 58), outingsStarted = 0))
        }

        @Test
        fun `not behind when caught up exactly on a pinned turn`() {
            assertFalse(DatingSchedule.isBehindSchedule(currentTurn = 52, recreationTurns = setOf(35, 43, 52, 58), outingsStarted = 3))
        }

        @Test
        fun `behind when several later turns are missed`() {
            assertTrue(DatingSchedule.isBehindSchedule(currentTurn = 58, recreationTurns = setOf(35, 43, 52, 58), outingsStarted = 2))
        }
    }

    @Nested
    @DisplayName("isScheduleAbandoned()")
    inner class IsScheduleAbandonedTests {
        @Test
        fun `abandoned once past the pure passion turn with the chain incomplete`() {
            assertTrue(DatingSchedule.isScheduleAbandoned(purePassionTurn = 60, currentTurn = 61, chainComplete = false))
        }

        @Test
        fun `not abandoned on the pure passion turn itself`() {
            assertFalse(DatingSchedule.isScheduleAbandoned(purePassionTurn = 60, currentTurn = 60, chainComplete = false))
        }

        @Test
        fun `not abandoned before the pure passion turn`() {
            assertFalse(DatingSchedule.isScheduleAbandoned(purePassionTurn = 60, currentTurn = 58, chainComplete = false))
        }

        @Test
        fun `not abandoned when the chain is already complete`() {
            assertFalse(DatingSchedule.isScheduleAbandoned(purePassionTurn = 60, currentTurn = 61, chainComplete = true))
        }

        @Test
        fun `Sirius is never abandoned without a pure passion turn`() {
            assertFalse(DatingSchedule.isScheduleAbandoned(purePassionTurn = -1, currentTurn = 70, chainComplete = false))
        }
    }
}
