package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.GrandConcertFanPolicy.FanRaceDecision
import com.steve1316.uma_android_automation.bot.GrandConcertFanPressure.CapacityCheck
import com.steve1316.uma_android_automation.bot.GrandConcertFanPressure.Requirement
import com.steve1316.uma_android_automation.bot.GrandConcertFanPressure.RequirementCapacity
import com.steve1316.uma_android_automation.bot.GrandConcertFanPressure.RequirementType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The Grand Concert fan-vs-training deferral policy over synthetic capacity checks; the point is the fail-closed
 * contract, not the capacity arithmetic (GrandConcertFanCapacityTest).
 */
@DisplayName("Grand Concert fan-vs-training policy")
class GrandConcertFanPolicyTest {
    private fun req(deficit: Int, capacity: Double, turn: Int = 30) =
        RequirementCapacity(Requirement(RequirementType.FAN_GOAL, 5000, turn, turn), deficit, capacity)

    private fun check(
        vararg requirements: RequirementCapacity,
        today: Double = 400.0,
        later: Boolean = true,
        failSafe: String? = null,
    ) = CapacityCheck(14, failSafe, requirements.toList(), today, later, 1.0)

    private fun decide(check: CapacityCheck, active: Boolean = true, low: Boolean = false) = GrandConcertFanPolicy.decide(active, check, low)

    @Test
    @DisplayName("every requirement with slack trains")
    fun deferWhenEveryRequirementHasSlack() {
        assertEquals(FanRaceDecision.DEFER_TO_TRAINING, decide(check(req(3000, 3000.0), req(20000, 21000.0, 48))))
    }

    @Test
    @DisplayName("one requirement without slack races, even when the nearer one has plenty")
    fun forceWhenAnyRequirementIsShort() {
        assertEquals(FanRaceDecision.FORCE_RACE, decide(check(req(3000, 9000.0), req(20000, 19999.0, 48))))
        assertEquals(FanRaceDecision.FORCE_RACE, decide(check(req(3000, 2999.0))))
    }

    @Test
    @DisplayName("short with nothing to gain today defers while a later turn can earn fans, races when none can")
    fun nothingToGainToday() {
        assertEquals(FanRaceDecision.DEFER_TO_TRAINING, decide(check(req(3000, 0.0), today = 0.0, later = true)))
        assertEquals(FanRaceDecision.FORCE_RACE, decide(check(req(3000, 0.0), today = 0.0, later = false)))
    }

    @Test
    @DisplayName("a failed capacity check races; low payouts race over slack; nothing unmet at this turn's fans trains")
    fun failClosed() {
        assertEquals(FanRaceDecision.FAIL_SAFE_FORCE_RACE, decide(check(failSafe = "aptitudes-unread")))
        assertEquals(FanRaceDecision.FAIL_SAFE_FORCE_RACE, decide(check(req(3000, 9000.0)), low = true))
        assertEquals(FanRaceDecision.DEFER_TO_TRAINING, decide(check()))
        assertEquals(FanRaceDecision.DEFER_TO_TRAINING, decide(check(), low = true))
    }

    @Test
    @DisplayName("no active requirement decides nothing")
    fun noRequirement() {
        assertEquals(FanRaceDecision.NO_REQUIREMENT, decide(check(req(3000, 0.0)), active = false))
    }

    @Test
    @DisplayName("an on-pace concert no longer forces a race over slack: the decision takes no concert pace")
    fun concertPaceIsNotAnInput() {
        assertEquals(FanRaceDecision.DEFER_TO_TRAINING, decide(check(req(3000, 3000.0))))
        val body = source("bot/campaigns/GrandConcert.kt")
        val call = body.substring(body.indexOf("GrandConcertFanPolicy.decide("), body.indexOf(")", body.indexOf("GrandConcertFanPolicy.decide(")))
        assertFalse(call.contains("concertBehindPace"), call)
    }

    @Test
    @DisplayName("prior 618-short failure class: a requirement losing capacity turn by turn stops deferring before it is short")
    fun neverDefersThroughTheDeadline() {
        // Capacity shrinks as the window closes with the deficit unchanged: the first turn without slack races.
        val decisions = listOf(5000.0, 3500.0, 3000.0, 2999.0, 1000.0, 0.0).map { decide(check(req(3000, it))) }
        assertEquals(List(3) { FanRaceDecision.DEFER_TO_TRAINING } + List(3) { FanRaceDecision.FORCE_RACE }, decisions)
    }

    @Test
    @DisplayName("forcesRace helper covers every race outcome and only those")
    fun forcesRaceHelper() {
        assertTrue(GrandConcertFanPolicy.forcesRace(FanRaceDecision.FORCE_RACE))
        assertTrue(GrandConcertFanPolicy.forcesRace(FanRaceDecision.FAIL_SAFE_FORCE_RACE))
        assertFalse(GrandConcertFanPolicy.forcesRace(FanRaceDecision.DEFER_TO_TRAINING))
        assertFalse(GrandConcertFanPolicy.forcesRace(FanRaceDecision.NO_REQUIREMENT))
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation/$relative")
            if (a.isFile) return a.readText()
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/$relative")
            if (b.isFile) return b.readText()
            dir = dir?.parentFile
        }
        error("could not locate $relative")
    }
}
