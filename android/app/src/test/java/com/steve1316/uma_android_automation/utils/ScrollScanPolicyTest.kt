package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for the scroll-scan timing and completeness policy - the decisions
 * [ScrollList.process] makes about when a pass is out of budget and what its ending means.
 *
 * Motivating case: a full career-end skill-list read costs about 61-62 seconds on the
 * reference device, just past the ordinary 60-second budget, so the pass was cut off one frame
 * before it could prove it had reached the end. A completely-read list then reported as
 * incomplete, and the career-finalization guard - which may only accept a proven-complete read
 * - refused to finish an otherwise clean career. The fix is a caller-specific budget plus a
 * single end-of-list verification when the deadline lands on the frame that reached the
 * bottom; it must NOT weaken what "complete" means.
 *
 * Every decision here is pure, so the tests use synthetic elapsed times instead of sleeping.
 */
@DisplayName("Scroll scan timing and completeness policy")
class ScrollScanPolicyTest {
    /** The observed cost of a full career-end skill-list read on the reference device. */
    private val observedCareerEndScanMs = 62_000L

    @Nested
    @DisplayName("caller-specific budgets")
    inner class Budgets {
        @Test
        fun `ordinary scans keep the existing default budget`() {
            assertEquals(60_000, MAX_PROCESS_TIME_DEFAULT_MS, "changing the default silently re-times every other list")
        }

        @Test
        fun `the career-end scan budget clears the observed cost with real headroom`() {
            assertTrue(
                CAREER_END_SCAN_BUDGET_MS >= observedCareerEndScanMs * 2,
                "the dedicated budget must tolerate a host running at half the reference speed",
            )
        }

        @Test
        fun `a 61 to 62 second pass fits the career-end budget but exceeds the ordinary one`() {
            for (elapsed in listOf(61_000L, 62_000L)) {
                assertFalse(scanDeadlineExceeded(elapsed, CAREER_END_SCAN_BUDGET_MS), "career-end budget must not cut a ${elapsed}ms pass")
                assertTrue(scanDeadlineExceeded(elapsed, MAX_PROCESS_TIME_DEFAULT_MS), "the ordinary budget still ends a ${elapsed}ms pass")
            }
        }

        @Test
        fun `the deadline test is inclusive at the budget and open below it`() {
            assertFalse(scanDeadlineExceeded(59_999L, MAX_PROCESS_TIME_DEFAULT_MS))
            assertTrue(scanDeadlineExceeded(60_000L, MAX_PROCESS_TIME_DEFAULT_MS))
            assertFalse(scanDeadlineExceeded(0L, MAX_PROCESS_TIME_DEFAULT_MS))
        }
    }

    @Nested
    @DisplayName("end-of-list proof")
    inner class EndOfListProof {
        @Test
        fun `bottom plus no new entries on a frame that saw rows is a complete read`() {
            assertTrue(endOfListProven(atTrackBottom = true, foundNewEntries = false, entriesDetected = true))
        }

        @Test
        fun `bottom without the no-new-entries proof is not complete`() {
            assertFalse(
                endOfListProven(atTrackBottom = true, foundNewEntries = true, entriesDetected = true),
                "the frame still revealed unread rows, so the list end was not observed",
            )
        }

        @Test
        fun `no-new-entries away from the bottom is not complete`() {
            assertFalse(endOfListProven(atTrackBottom = false, foundNewEntries = false, entriesDetected = true))
        }

        @Test
        fun `a frame that detected nothing can never prove the end`() {
            assertFalse(
                endOfListProven(atTrackBottom = true, foundNewEntries = false, entriesDetected = false),
                "an unreadable frame reveals no rows by definition - that is not evidence of the end",
            )
        }
    }

    @Nested
    @DisplayName("deadline exits")
    inner class DeadlineExits {
        @Test
        fun `a deadline at the track bottom is never reported as complete`() {
            assertEquals(ScanTermination.TIMED_OUT_AT_BOTTOM_UNCONFIRMED, classifyScanDeadlineExit(atTrackBottom = true))
            assertFalse(classifyScanDeadlineExit(atTrackBottom = true).isComplete())
        }

        @Test
        fun `a deadline mid-list is a partial read`() {
            assertEquals(ScanTermination.TIMED_OUT_PARTIAL, classifyScanDeadlineExit(atTrackBottom = false))
            assertFalse(classifyScanDeadlineExit(atTrackBottom = false).isComplete())
        }

        @Test
        fun `only COMPLETE counts as complete`() {
            assertTrue(ScanTermination.COMPLETE.isComplete())
            for (other in ScanTermination.entries.filter { it != ScanTermination.COMPLETE }) {
                assertFalse(other.isComplete(), "$other must never be treated as a full read")
            }
        }
    }

    @Nested
    @DisplayName("deadline grace")
    inner class DeadlineGrace {
        @Test
        fun `the deadline does not preempt a proof that is one frame away`() {
            assertTrue(
                allowDeadlineGrace(atTrackBottom = true, graceAlreadyUsed = false),
                "reaching the bottom in the same iteration the budget expired is exactly the case that must not fail",
            )
        }

        @Test
        fun `grace is granted at most once`() {
            assertFalse(allowDeadlineGrace(atTrackBottom = true, graceAlreadyUsed = true), "a deadline can never be extended twice")
        }

        @Test
        fun `grace is never granted mid-list`() {
            assertFalse(allowDeadlineGrace(atTrackBottom = false, graceAlreadyUsed = false))
            assertFalse(allowDeadlineGrace(atTrackBottom = false, graceAlreadyUsed = true))
        }

        @Test
        fun `a granted grace iteration can still only finish through the positive proof`() {
            // The grace lets one more iteration run; that iteration completes the pass only if
            // it produces the same bottom + no-new-entries evidence any other frame would need.
            assertTrue(allowDeadlineGrace(atTrackBottom = true, graceAlreadyUsed = false))
            assertTrue(endOfListProven(atTrackBottom = true, foundNewEntries = false, entriesDetected = true))
            assertFalse(endOfListProven(atTrackBottom = true, foundNewEntries = true, entriesDetected = true))
            // And when the grace iteration fails to prove it, the exit is still not complete.
            assertEquals(ScanTermination.TIMED_OUT_AT_BOTTOM_UNCONFIRMED, classifyScanDeadlineExit(atTrackBottom = true))
        }
    }

    @Nested
    @DisplayName("failure paths stay failures")
    inner class FailurePaths {
        @Test
        fun `parse abort, frozen list, and never-scanned states are not complete`() {
            // Unreadable frames and a list frozen mid-track both terminate FAILED in the loop;
            // a ScrollList that never ran a pass also reports FAILED rather than a clean state.
            assertFalse(ScanTermination.FAILED.isComplete())
        }

        @Test
        fun `a caller-stopped early exit is a partial read, not a completeness proof`() {
            // The onEntry callback can stop the pass as soon as it finds what it wanted; the
            // rows below were never read.
            assertFalse(ScanTermination.TIMED_OUT_PARTIAL.isComplete())
        }

        @Test
        fun `the termination reason is distinguishable for every ending`() {
            assertEquals(4, ScanTermination.entries.size, "each ending must stay separately reportable in the durable logs")
            assertEquals(
                setOf(
                    ScanTermination.COMPLETE,
                    ScanTermination.TIMED_OUT_AT_BOTTOM_UNCONFIRMED,
                    ScanTermination.TIMED_OUT_PARTIAL,
                    ScanTermination.FAILED,
                ),
                ScanTermination.entries.toSet(),
            )
        }
    }
}
