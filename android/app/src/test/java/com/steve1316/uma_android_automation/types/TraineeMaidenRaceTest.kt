package com.steve1316.uma_android_automation.types

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Trainee maiden race completion")
class TraineeMaidenRaceTest {
    @Test
    fun `maiden not complete on a fresh trainee still at the MAIDEN fan class`() {
        val trainee = Trainee()
        trainee.fanCountClass = FanCountClass.MAIDEN

        assertFalse(trainee.bHasCompletedMaidenRace)
    }

    @Test
    fun `a completed non-maiden race marks maiden done even while fan-tier OCR reads stale MAIDEN`() {
        val trainee = Trainee()
        trainee.fanCountClass = FanCountClass.MAIDEN
        trainee.noteCompletedRaceGrade(RaceGrade.OP)

        assertTrue(trainee.bHasCompletedMaidenRace)
    }

    @Test
    fun `debut and maiden grade races never mark maiden done`() {
        val trainee = Trainee()
        trainee.fanCountClass = FanCountClass.MAIDEN
        trainee.noteCompletedRaceGrade(RaceGrade.DEBUT)
        trainee.noteCompletedRaceGrade(RaceGrade.MAIDEN)
        trainee.noteCompletedRaceGrade(null)

        assertFalse(trainee.bHasCompletedMaidenRace)
    }
}
