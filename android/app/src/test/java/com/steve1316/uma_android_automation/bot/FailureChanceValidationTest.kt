package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for `Training.crossValidateFailureChances`. The clamp rejects an OCR read that sits far
 * above the energy-based estimate (e.g. 99% at 100% energy), which could strand the bot in energy
 * recovery on a full-energy turn; the monotonicity walk corrects a facility reading suspiciously
 * below its successor while keeping genuine support-card dips.
 */
@DisplayName("Failure-chance cross-validation")
class FailureChanceValidationTest {
    @Test
    @DisplayName("Impossible high reads at full energy are clamped to the energy estimate (0%)")
    fun testClampsImplausibleReadsAtFullEnergy() {
        // At 100% energy the estimate is 0% for every facility, so a read of 99% (physicals) / 62% (Wit) is an OCR misread and must clamp to 0.
        val input =
            listOf(
                StatName.SPEED to 99,
                StatName.STAMINA to 99,
                StatName.POWER to 99,
                StatName.GUTS to 99,
                StatName.WIT to 62,
            )
        val corrected = Training.crossValidateFailureChances(input, currentEnergy = 100)
        for (stat in StatName.entries) {
            assertEquals(0, corrected[stat], "$stat must clamp to 0% at 100% energy (impossible 99%/62% read)")
        }
    }

    @Test
    @DisplayName("Genuinely high reads at low energy are left unchanged")
    fun testLeavesPlausibleHighReadsAtLowEnergy() {
        // At 20% energy the physical estimate is (50-20)*2 = 60% and Wit ~24%, so these monotonic reads are within tolerance and must NOT be clamped down.
        val input =
            listOf(
                StatName.SPEED to 60,
                StatName.STAMINA to 62,
                StatName.POWER to 64,
                StatName.GUTS to 66,
                StatName.WIT to 30,
            )
        val corrected = Training.crossValidateFailureChances(input, currentEnergy = 20)
        assertEquals(60, corrected[StatName.SPEED])
        assertEquals(62, corrected[StatName.STAMINA])
        assertEquals(64, corrected[StatName.POWER])
        assertEquals(66, corrected[StatName.GUTS])
        assertEquals(30, corrected[StatName.WIT])
    }

    @Test
    @DisplayName("A facility reading far below its successor and the energy curve is corrected upward")
    fun testCorrectsSuspiciouslyLowRead() {
        // At 20% energy the expected physical chance is 60%. Speed reading 5% against Stamina's 62%
        // is a 57-point jump, and |5 - 60| = 55 exceeds the tolerance, so Speed corrects to 60.
        val input =
            listOf(
                StatName.SPEED to 5,
                StatName.STAMINA to 62,
                StatName.POWER to 64,
                StatName.GUTS to 66,
            )
        val corrected = Training.crossValidateFailureChances(input, currentEnergy = 20)
        assertEquals(60, corrected[StatName.SPEED], "A 5% Speed read at 20% energy is an OCR misread and corrects to the 60% estimate")
        assertEquals(62, corrected[StatName.STAMINA])
    }

    @Test
    @DisplayName("A modest dip within tolerance is a genuine support-card effect and is kept")
    fun testKeepsGenuineSupportCardDip() {
        // Speed at 45% vs Stamina at 66% is a suspicious 21-point jump, but |45 - 60| = 15 is inside
        // the tolerance - a real support-card failure reduction - so the read must survive.
        val input =
            listOf(
                StatName.SPEED to 45,
                StatName.STAMINA to 66,
                StatName.POWER to 68,
                StatName.GUTS to 70,
            )
        val corrected = Training.crossValidateFailureChances(input, currentEnergy = 20)
        assertEquals(45, corrected[StatName.SPEED], "A dip within the outlier tolerance is a support-card effect, not a misread")
    }
}
