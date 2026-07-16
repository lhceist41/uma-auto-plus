package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.StartModule.Companion.PostCareerAction
import com.steve1316.uma_android_automation.bot.TaskResultCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [StartModule.decidePostCareerAction] - the pure routing decision the run loop
 * takes once a career playthrough returns.
 *
 * The bug this locks down: the career-end flow (Complete Career -> sparks/reroll -> veteran
 * registration -> Home) used to run only when a run queue was enabled, so a queue-disabled single
 * run completed its career, wrote the [CAREER_END] record, then stopped on the results screen -
 * no sparks read, no reroll, no Home return. The rule now: mandatory cleanup ([FINALIZE_TO_HOME])
 * is NOT queue-gated; only launching the NEXT career ([LAUNCH_NEXT]) is. A single run therefore
 * finalizes to Home and can never start a second career.
 */
@DisplayName("Post-career routing decision")
class PostCareerDecisionTest {
    // Convenience wrapper: default to the healthy "career completed, bot alive, no stop" state and
    // let each test vary only the axis it exercises.
    private fun decide(
        resultCode: TaskResultCode = TaskResultCode.TASK_RESULT_COMPLETE,
        runIndex: Int = 1,
        totalRuns: Int = 1,
        enableRunQueue: Boolean = false,
        queueStopRequested: Boolean = false,
        botRunning: Boolean = true,
    ) = StartModule.decidePostCareerAction(resultCode, runIndex, totalRuns, enableRunQueue, queueStopRequested, botRunning)

    @Nested
    @DisplayName("mandatory finalize-to-home (the fix)")
    inner class FinalizeTests {
        @Test
        fun `queue disabled, one completed career finalizes to home`() {
            // The reported failure: single run, queue off. Must now run the career-end flow to Home.
            assertEquals(PostCareerAction.FINALIZE_TO_HOME, decide(enableRunQueue = false, runIndex = 1, totalRuns = 1))
        }

        @Test
        fun `applied-preset single run (queue off) finalizes to home`() {
            // An applied-preset single launch is still enableRunQueue=false, totalRuns=1 - same path.
            // (Trainee identity is verified separately in TraineeNameMatcherTest.)
            assertEquals(PostCareerAction.FINALIZE_TO_HOME, decide(enableRunQueue = false, runIndex = 1, totalRuns = 1))
        }

        @Test
        fun `queue enabled with a single total run finalizes to home`() {
            assertEquals(PostCareerAction.FINALIZE_TO_HOME, decide(enableRunQueue = true, runIndex = 1, totalRuns = 1))
        }

        @Test
        fun `queue enabled, final run of many finalizes to home`() {
            assertEquals(PostCareerAction.FINALIZE_TO_HOME, decide(enableRunQueue = true, runIndex = 3, totalRuns = 3))
        }

        @Test
        fun `the last run of every queue size finalizes on a clean completion`() {
            for (total in 1..5) {
                assertEquals(
                    PostCareerAction.FINALIZE_TO_HOME,
                    decide(enableRunQueue = true, runIndex = total, totalRuns = total),
                    "last run of a $total-run queue should finalize",
                )
            }
        }
    }

    @Nested
    @DisplayName("launch next career (queue-gated, unchanged)")
    inner class LaunchNextTests {
        @Test
        fun `queue enabled, more runs remain launches the next career`() {
            assertEquals(PostCareerAction.LAUNCH_NEXT, decide(enableRunQueue = true, runIndex = 1, totalRuns = 3))
            assertEquals(PostCareerAction.LAUNCH_NEXT, decide(enableRunQueue = true, runIndex = 2, totalRuns = 3))
        }

        @Test
        fun `first run of a two-run queue launches the next`() {
            assertEquals(PostCareerAction.LAUNCH_NEXT, decide(enableRunQueue = true, runIndex = 1, totalRuns = 2))
        }

        @Test
        fun `a skipped mid-queue run still launches the next (preserved behavior)`() {
            // A skip advances the queue exactly like a completion does.
            assertEquals(
                PostCareerAction.LAUNCH_NEXT,
                decide(resultCode = TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE, enableRunQueue = true, runIndex = 1, totalRuns = 3),
            )
        }

        @Test
        fun `a non-fatal error mid-queue still launches the next (preserved behavior)`() {
            // With stopOnError=false the loop's when-block does not break, so an errored run reaches
            // this decision and continues the queue - the pre-existing continue-on-error contract.
            assertEquals(
                PostCareerAction.LAUNCH_NEXT,
                decide(resultCode = TaskResultCode.TASK_RESULT_CONNECTION_ERROR, enableRunQueue = true, runIndex = 1, totalRuns = 3),
            )
        }
    }

    @Nested
    @DisplayName("non-complete endings on the last or only run leave the screen as-is")
    inner class StopOnNonCompleteTests {
        @Test
        fun `queue disabled, errored single run stops without navigating`() {
            for (code in listOf(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, TaskResultCode.TASK_RESULT_CONNECTION_ERROR, TaskResultCode.TASK_RESULT_TIMED_OUT)) {
                assertEquals(PostCareerAction.STOP, decide(resultCode = code, enableRunQueue = false, runIndex = 1, totalRuns = 1), "single run ending in $code")
            }
        }

        @Test
        fun `queue enabled, last run ended non-clean stops (no finalize, no launch)`() {
            assertEquals(
                PostCareerAction.STOP,
                decide(resultCode = TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE, enableRunQueue = true, runIndex = 3, totalRuns = 3),
            )
            assertEquals(
                PostCareerAction.STOP,
                decide(resultCode = TaskResultCode.TASK_RESULT_CONNECTION_ERROR, enableRunQueue = true, runIndex = 3, totalRuns = 3),
            )
        }

        @Test
        fun `only a clean completion finalizes - no other code does`() {
            for (code in TaskResultCode.values()) {
                val action = decide(resultCode = code, enableRunQueue = false, runIndex = 1, totalRuns = 1)
                if (code == TaskResultCode.TASK_RESULT_COMPLETE) {
                    assertEquals(PostCareerAction.FINALIZE_TO_HOME, action, "COMPLETE must finalize")
                } else {
                    assertEquals(PostCareerAction.STOP, action, "$code on a single run must not finalize")
                }
            }
        }
    }

    @Nested
    @DisplayName("a stop request or dead service overrides everything")
    inner class StopRequestedTests {
        @Test
        fun `a stop request on a completed final run stops instead of finalizing`() {
            assertEquals(
                PostCareerAction.STOP,
                decide(runIndex = 1, totalRuns = 1, enableRunQueue = false, queueStopRequested = true),
            )
            assertEquals(
                PostCareerAction.STOP,
                decide(runIndex = 3, totalRuns = 3, enableRunQueue = true, queueStopRequested = true),
            )
        }

        @Test
        fun `a stop request mid-queue does not launch the next career`() {
            assertEquals(
                PostCareerAction.STOP,
                decide(runIndex = 1, totalRuns = 3, enableRunQueue = true, queueStopRequested = true),
            )
        }

        @Test
        fun `a torn-down service stops instead of finalizing or launching`() {
            assertEquals(PostCareerAction.STOP, decide(runIndex = 1, totalRuns = 1, enableRunQueue = false, botRunning = false))
            assertEquals(PostCareerAction.STOP, decide(runIndex = 1, totalRuns = 3, enableRunQueue = true, botRunning = false))
        }
    }

    @Nested
    @DisplayName("the last or only run can never start a second career")
    inner class NeverSecondCareerTests {
        @Test
        fun `no result code makes the last run of a queue launch another career`() {
            for (code in TaskResultCode.values()) {
                assertNotEquals(
                    PostCareerAction.LAUNCH_NEXT,
                    decide(resultCode = code, enableRunQueue = true, runIndex = 4, totalRuns = 4),
                    "last run ending in $code must not launch a second career",
                )
            }
        }

        @Test
        fun `no result code makes a queue-disabled single run launch another career`() {
            for (code in TaskResultCode.values()) {
                assertNotEquals(
                    PostCareerAction.LAUNCH_NEXT,
                    decide(resultCode = code, enableRunQueue = false, runIndex = 1, totalRuns = 1),
                    "single run ending in $code must not launch a second career",
                )
            }
        }
    }

    @Nested
    @DisplayName("a multi-run queue routes each run correctly end to end")
    inner class MultiRunSequenceTests {
        @Test
        fun `a three-run queue launches runs 1 and 2 and finalizes run 3`() {
            assertEquals(PostCareerAction.LAUNCH_NEXT, decide(enableRunQueue = true, runIndex = 1, totalRuns = 3))
            assertEquals(PostCareerAction.LAUNCH_NEXT, decide(enableRunQueue = true, runIndex = 2, totalRuns = 3))
            assertEquals(PostCareerAction.FINALIZE_TO_HOME, decide(enableRunQueue = true, runIndex = 3, totalRuns = 3))
        }
    }
}
