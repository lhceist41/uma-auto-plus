package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.GrandConcertFanPolicy.FanRaceDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The Grand Concert fan-vs-training deferral policy. Every case is a pure decision over synthetic
 * inputs; the point is the fail-closed contract, not a live deadline reader.
 */
@DisplayName("Grand Concert fan-vs-training policy")
class GrandConcertFanPolicyTest {
    private fun decide(
        active: Boolean = true,
        deadline: Int? = 10,
        racesNeeded: Int? = 1,
        behindPace: Boolean = true,
    ) = GrandConcertFanPolicy.decide(active, deadline, racesNeeded, behindPace)

    @Test
    @DisplayName("below fan target with plenty of slack: prefer training, do not force a race")
    fun deferWhenSlackProven() {
        // Deadline 10 turns out, one race needed, cycle behind pace: skipping this turn still fits.
        assertEquals(FanRaceDecision.DEFER_TO_TRAINING, decide(deadline = 10, racesNeeded = 1, behindPace = true))
    }

    @Test
    @DisplayName("below fan target with an urgent deadline: force the race")
    fun forceWhenDeadlineUrgent() {
        // One race needed but only this turn remains before the deadline: cannot spare it.
        assertEquals(FanRaceDecision.FORCE_RACE, decide(deadline = 1, racesNeeded = 1, behindPace = true))
        // Exactly at the deferral margin boundary (need 1, deadline 2) is safe; one tighter is not.
        assertEquals(FanRaceDecision.DEFER_TO_TRAINING, decide(deadline = 2, racesNeeded = 1, behindPace = true))
    }

    @Test
    @DisplayName("unknown or insufficient deadline data: fail safe to a race")
    fun failSafeWhenDeadlineUnknown() {
        assertEquals(FanRaceDecision.FAIL_SAFE_FORCE_RACE, decide(deadline = null, racesNeeded = 1))
        assertEquals(FanRaceDecision.FAIL_SAFE_FORCE_RACE, decide(deadline = 10, racesNeeded = null))
    }

    @Test
    @DisplayName("fan target already satisfied: nothing to force")
    fun noRequirementWhenSatisfied() {
        assertEquals(FanRaceDecision.NO_REQUIREMENT, decide(active = false, deadline = 10, racesNeeded = 1))
    }

    @Test
    @DisplayName("severe deficit that cannot be proven safe to defer: force the race")
    fun forceWhenDeficitSevere() {
        // Five races still needed but only four turns before the deadline: deferring one loses the
        // gate, so race now.
        assertEquals(FanRaceDecision.FORCE_RACE, decide(deadline = 4, racesNeeded = 5, behindPace = true))
        // Even with generous slack, a null estimate cannot prove safety and fails closed.
        assertEquals(FanRaceDecision.FAIL_SAFE_FORCE_RACE, decide(deadline = 20, racesNeeded = null, behindPace = true))
    }

    @Test
    @DisplayName("no time-sensitive training value: race rather than defer for nothing")
    fun forceWhenNothingToGain() {
        // Slack is provable, but the cycle is on pace, so a training turn buys no scenario currency;
        // clear the requirement instead of sitting on it.
        assertEquals(FanRaceDecision.FORCE_RACE, decide(deadline = 10, racesNeeded = 1, behindPace = false))
    }

    @Test
    @DisplayName("prior 618-short failure class: never defers indefinitely through the deadline")
    fun neverDefersThroughTheDeadline() {
        // Walk the deadline down turn by turn with the deficit unchanged (one race still needed).
        // The policy must switch from deferring to forcing BEFORE the deadline can be crossed while
        // still short, which is the failure mode that ended a career 618 fans short.
        val decisions = (6 downTo 0).map { turnsLeft -> decide(deadline = turnsLeft, racesNeeded = 1, behindPace = true) }
        // The last turn that still defers must leave room for the forced race that follows.
        val lastDeferIndex = decisions.indexOfLast { it == FanRaceDecision.DEFER_TO_TRAINING }
        val firstForceIndex = decisions.indexOfFirst { GrandConcertFanPolicy.forcesRace(it) }
        assertTrue(firstForceIndex > lastDeferIndex, "must stop deferring before it can cross the deadline")
        // At the deadline itself (0 turns) it must force, never defer.
        assertEquals(FanRaceDecision.FORCE_RACE, decide(deadline = 0, racesNeeded = 1, behindPace = true))
    }

    @Test
    @DisplayName("forcesRace helper covers every race outcome and only those")
    fun forcesRaceHelper() {
        assertTrue(GrandConcertFanPolicy.forcesRace(FanRaceDecision.FORCE_RACE))
        assertTrue(GrandConcertFanPolicy.forcesRace(FanRaceDecision.FAIL_SAFE_FORCE_RACE))
        assertFalse(GrandConcertFanPolicy.forcesRace(FanRaceDecision.DEFER_TO_TRAINING))
        assertFalse(GrandConcertFanPolicy.forcesRace(FanRaceDecision.NO_REQUIREMENT))
    }
}
