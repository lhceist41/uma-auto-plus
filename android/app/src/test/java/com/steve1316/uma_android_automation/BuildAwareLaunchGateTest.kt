package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Build-aware launch-transaction gate (A2), pure.
 *
 * Proves the structural invariant: READY_TO_START_CAREER is reachable ONLY with every precondition
 * satisfied, and the Start Career tap predicate is true ONLY in that state. Every blocking case the
 * launch gate must catch is exercised against the pure evaluator.
 */
@DisplayName("Build-aware launch gate")
class BuildAwareLaunchGateTest {
    /** Every precondition satisfied: the only input that may reach READY. */
    private fun allHold() = LaunchPreconditions(
        intentPresent = true,
        intentBuildAware = true,
        freshLocateUnique = true,
        borrowSelected = true,
        borrowIdentityVerified = true,
        ownedDeckIntact = true,
        upstreamLaunchGatesHeld = true,
        onSupportFormation = true,
        startCareerPresent = true,
        noDebugConflict = true,
    )

    @Nested
    @DisplayName("evaluate")
    inner class Evaluate {
        @Test
        fun `all preconditions hold reaches READY`() {
            assertEquals(LaunchTransactionState.READY_TO_START_CAREER, BuildAwareLaunchGate.evaluate(allHold()))
        }

        @Test
        fun `a missing intent is BORROW_NOT_AVAILABLE, never a legacy fallback`() {
            assertEquals(LaunchTransactionState.BORROW_NOT_AVAILABLE, BuildAwareLaunchGate.evaluate(allHold().copy(intentPresent = false)))
        }

        @Test
        fun `a non-build-aware intent is BORROW_NOT_AVAILABLE (fail closed, no silent fallback)`() {
            assertEquals(LaunchTransactionState.BORROW_NOT_AVAILABLE, BuildAwareLaunchGate.evaluate(allHold().copy(intentBuildAware = false)))
        }

        @Test
        fun `a stale pool (card not uniquely present in the fresh scan) is BORROW_POOL_STALE`() {
            assertEquals(LaunchTransactionState.BORROW_POOL_STALE, BuildAwareLaunchGate.evaluate(allHold().copy(freshLocateUnique = false)))
        }

        @Test
        fun `cannot reach READY without a committed selection`() {
            assertEquals(LaunchTransactionState.LAUNCH_BLOCKED, BuildAwareLaunchGate.evaluate(allHold().copy(borrowSelected = false)))
        }

        @Test
        fun `cannot reach READY without a verified borrow identity`() {
            assertEquals(LaunchTransactionState.LAUNCH_BLOCKED, BuildAwareLaunchGate.evaluate(allHold().copy(borrowIdentityVerified = false)))
        }

        @Test
        fun `a changed owned deck blocks`() {
            assertEquals(LaunchTransactionState.LAUNCH_BLOCKED, BuildAwareLaunchGate.evaluate(allHold().copy(ownedDeckIntact = false)))
        }

        @Test
        fun `an unheld upstream launch gate blocks`() {
            assertEquals(LaunchTransactionState.LAUNCH_BLOCKED, BuildAwareLaunchGate.evaluate(allHold().copy(upstreamLaunchGatesHeld = false)))
        }

        @Test
        fun `the wrong screen blocks`() {
            assertEquals(LaunchTransactionState.LAUNCH_BLOCKED, BuildAwareLaunchGate.evaluate(allHold().copy(onSupportFormation = false)))
            assertEquals(LaunchTransactionState.LAUNCH_BLOCKED, BuildAwareLaunchGate.evaluate(allHold().copy(startCareerPresent = false)))
        }

        @Test
        fun `an armed debug diagnostic blocks a real launch`() {
            assertEquals(LaunchTransactionState.LAUNCH_BLOCKED, BuildAwareLaunchGate.evaluate(allHold().copy(noDebugConflict = false)))
        }

        @Test
        fun `the default preconditions block (every field defaults to the safe value)`() {
            assertEquals(LaunchTransactionState.BORROW_NOT_AVAILABLE, BuildAwareLaunchGate.evaluate(LaunchPreconditions()))
        }
    }

    @Nested
    @DisplayName("canStartCareer structural invariant")
    inner class CanStart {
        @Test
        fun `only READY may tap Start Career`() {
            assertTrue(BuildAwareLaunchGate.canStartCareer(LaunchTransactionState.READY_TO_START_CAREER))
            for (s in LaunchTransactionState.values().filter { it != LaunchTransactionState.READY_TO_START_CAREER }) {
                assertFalse(BuildAwareLaunchGate.canStartCareer(s), "$s must not authorise a Start Career tap")
            }
        }
    }

    @Nested
    @DisplayName("furthestStageReached")
    inner class Furthest {
        @Test
        fun `a verify failure still shows the selection stage was reached`() {
            assertEquals(LaunchTransactionState.BORROW_SELECTED, BuildAwareLaunchGate.furthestStageReached(allHold().copy(borrowIdentityVerified = false)))
        }

        @Test
        fun `a deck failure shows identity verification was reached`() {
            assertEquals(LaunchTransactionState.BORROW_IDENTITY_VERIFIED, BuildAwareLaunchGate.furthestStageReached(allHold().copy(ownedDeckIntact = false)))
        }

        @Test
        fun `a screen failure shows preconditions were reached`() {
            assertEquals(LaunchTransactionState.LAUNCH_PRECONDITIONS_VERIFIED, BuildAwareLaunchGate.furthestStageReached(allHold().copy(onSupportFormation = false)))
        }

        @Test
        fun `all holding reaches READY`() {
            assertEquals(LaunchTransactionState.READY_TO_START_CAREER, BuildAwareLaunchGate.furthestStageReached(allHold()))
        }
    }

    @Nested
    @DisplayName("intent recommendation source (schema v2)")
    inner class Source {
        @Test
        fun `BUILD_AWARE parses, case and space tolerant`() {
            assertEquals(IntentRecommendationSource.BUILD_AWARE, parseIntentRecommendationSource("BUILD_AWARE"))
            assertEquals(IntentRecommendationSource.BUILD_AWARE, parseIntentRecommendationSource(" build_aware "))
        }

        @Test
        fun `DECKLAB_COMPOSITE parses`() {
            assertEquals(IntentRecommendationSource.DECKLAB_COMPOSITE, parseIntentRecommendationSource("DECKLAB_COMPOSITE"))
        }

        @Test
        fun `an absent or unknown source is UNKNOWN and fails the build-aware gate closed`() {
            assertEquals(IntentRecommendationSource.UNKNOWN, parseIntentRecommendationSource(null))
            assertEquals(IntentRecommendationSource.UNKNOWN, parseIntentRecommendationSource("something_else"))
        }

        @Test
        fun `a v2 intent JSON with recommendation_source is read on-device`() {
            val json = """
                {"schema":"deck_lab_smart_borrow_intent","schema_version":2,"support_card_id":30016,
                 "canonical_character":"Super Creek","canonical_title":"Piece of Mind","expected_limit_break":3,
                 "recommendation_source":"BUILD_AWARE"}
            """.trimIndent()
            val intent = parseSmartBorrowIntent(json)
            assertEquals(IntentRecommendationSource.BUILD_AWARE, intent?.recommendationSource)
            assertEquals(2, intent?.schemaVersion)
        }

        @Test
        fun `a v1 intent without the field parses as UNKNOWN`() {
            val json = """{"schema":"deck_lab_smart_borrow_intent","support_card_id":30002,"canonical_character":"Silence Suzuka"}"""
            assertEquals(IntentRecommendationSource.UNKNOWN, parseSmartBorrowIntent(json)?.recommendationSource)
        }
    }
}
