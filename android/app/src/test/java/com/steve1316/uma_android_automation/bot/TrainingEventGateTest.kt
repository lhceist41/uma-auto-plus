package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the Training Event confidence gate.
 *
 * Verifies that [TrainingEvent.shouldActOnEventMatch] routes a sub-threshold fuzzy match to the
 * safe first-option default instead of acting on the matched event's rewards, while leaving
 * above-threshold matches and special events free to act.
 */
@DisplayName("Training Event Gate Tests")
class TrainingEventGateTest {
    private val rewards = listOf("Speed +10", "Stamina +10")

    @Test
    @DisplayName("Sub-threshold non-special match routes to the safe default")
    fun subThresholdNonSpecialMatchFallsBack() {
        // The headline assertion: a wrong-but-plausible match below the floor must NOT act on its rewards.
        assertFalse(
            TrainingEvent.shouldActOnEventMatch(
                eventRewards = rewards,
                specialEventHandled = false,
                confidence = 0.60,
                minimumConfidence = 0.90,
            ),
        )
    }

    @Test
    @DisplayName("At-or-above-threshold non-special match is acted on")
    fun atOrAboveThresholdNonSpecialMatchActs() {
        assertTrue(
            TrainingEvent.shouldActOnEventMatch(rewards, specialEventHandled = false, confidence = 0.90, minimumConfidence = 0.90),
        )
        assertTrue(
            TrainingEvent.shouldActOnEventMatch(rewards, specialEventHandled = false, confidence = 0.95, minimumConfidence = 0.90),
        )
    }

    @Test
    @DisplayName("Special event bypasses the confidence floor")
    fun specialEventBypassesFloor() {
        assertTrue(
            TrainingEvent.shouldActOnEventMatch(rewards, specialEventHandled = true, confidence = 0.40, minimumConfidence = 0.90),
        )
    }

    @Test
    @DisplayName("Empty rewards route to the safe default")
    fun emptyRewardsFallBack() {
        assertFalse(
            TrainingEvent.shouldActOnEventMatch(emptyList(), specialEventHandled = false, confidence = 0.99, minimumConfidence = 0.90),
        )
    }

    @Test
    @DisplayName("Blank first reward routes to the safe default")
    fun blankFirstRewardFallsBack() {
        assertFalse(
            TrainingEvent.shouldActOnEventMatch(listOf(""), specialEventHandled = false, confidence = 0.99, minimumConfidence = 0.90),
        )
    }
}
