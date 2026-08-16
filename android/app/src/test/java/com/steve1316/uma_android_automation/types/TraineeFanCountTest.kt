package com.steve1316.uma_android_automation.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The write-always fan-observation funnel: every valid observation is stored so the OCR reading can
 * self-heal. A real fan total never decreases, but the fan OCR can misread in either direction and
 * has no independent source to correct a false-high later, so rejecting lower observations would let
 * one false-high read stick for the whole career. Accepting the newest readable value keeps both a
 * false-low and a false-high recoverable on the next correct read.
 */
@DisplayName("Trainee fan-observation funnel writes every valid observation")
class TraineeFanCountTest {
    @Test
    @DisplayName("a higher observation is written")
    fun increaseWrites() {
        val trainee = Trainee()
        trainee.observeFanCount(1151)
        assertEquals(1151, trainee.fans)
        trainee.observeFanCount(3100)
        assertEquals(3100, trainee.fans)
    }

    @Test
    @DisplayName("a lower observation is accepted, not rejected (the corrected sticky-high behavior)")
    fun decreaseIsAccepted() {
        val trainee = Trainee()
        trainee.observeFanCount(3100)
        assertEquals(3100, trainee.fans)
        trainee.observeFanCount(1151) // below the previous observation, but still accepted
        assertEquals(1151, trainee.fans)
    }

    @Test
    @DisplayName("an equal observation writes harmlessly")
    fun equalWrites() {
        val trainee = Trainee()
        trainee.observeFanCount(3000)
        trainee.observeFanCount(3000)
        assertEquals(3000, trainee.fans)
    }

    @Test
    @DisplayName("a false-high observation self-heals on the next correct read")
    fun falseHighSelfHeals() {
        val trainee = Trainee()
        trainee.observeFanCount(1151)
        assertEquals(1151, trainee.fans)
        trainee.observeFanCount(11151) // an OCR false-high (a stray leading digit)
        assertEquals(11151, trainee.fans)
        trainee.observeFanCount(1300) // the next correct read overwrites it; no sticky-high remains
        assertEquals(1300, trainee.fans)
    }

    @Test
    @DisplayName("a fresh Trainee starts at the default and is not contaminated by a prior career")
    fun freshInstanceNotContaminated() {
        val prior = Trainee()
        prior.observeFanCount(50000)
        assertEquals(50000, prior.fans)

        val fresh = Trainee()
        assertEquals(1, fresh.fans) // the constructor default, not the prior career's 50000
        fresh.observeFanCount(200)
        assertEquals(200, fresh.fans)
    }
}
