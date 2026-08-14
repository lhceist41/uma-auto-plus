package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The Grand Concert raceable-turn calendar: the factual "which turns can host a fan race" model that
 * a future fan-goal deadline reader will measure slack against. Pure and deterministic; no policy
 * decision lives here.
 */
@DisplayName("Grand Concert race calendar")
class GrandConcertRaceCalendarTest {
    @Test
    @DisplayName("pre-debut, Summer camp, and finale turns are not raceable; ordinary and concert turns are")
    fun raceabilityByTurn() {
        // Pre-debut: no regular races below turn 12.
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(1))
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(11))
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(12))
        // Summer camp: turns 37-40 and 61-64 host no races; their boundaries flip cleanly.
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(36))
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(37))
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(40))
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(41))
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(60))
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(61))
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(64))
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(65))
        // Concert turns remain ordinary raceable turns (a concert does not consume the turn).
        for (concert in listOf(24, 36, 48, 60, 72)) {
            assertTrue(GrandConcertRaceCalendar.isRaceableTurn(concert), "concert turn $concert should be raceable")
        }
        // Finale season: no fan-goal races above turn 72.
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(73))
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(75))
    }

    @Test
    @DisplayName("a clear window counts every intervening turn as raceable slack")
    fun plentyOfSlack() {
        // Turns 13..30 are all raceable (no Summer in that span): 18 opportunities.
        assertEquals(18, GrandConcertRaceCalendar.raceableTurnsBetween(12, 30))
    }

    @Test
    @DisplayName("Summer camp is excluded from raceable slack")
    fun summerReducesSlack() {
        // Window 36..45 spans Summer 37-40. Raceable: 41,42,43,44,45 (turn 36 is the exclusive lower
        // bound). Four Summer turns removed from what a naive count would give.
        assertEquals(5, GrandConcertRaceCalendar.raceableTurnsBetween(36, 45))
        // Naive (all-turns) count for the same window would be 9; the model removes the Summer turns.
        val naive = 45 - 36
        assertTrue(GrandConcertRaceCalendar.raceableTurnsBetween(36, 45) < naive)
    }

    @Test
    @DisplayName("off-by-one: the window is exclusive of afterTurn and inclusive of throughTurn")
    fun offByOne() {
        assertEquals(0, GrandConcertRaceCalendar.raceableTurnsBetween(12, 12))
        assertEquals(1, GrandConcertRaceCalendar.raceableTurnsBetween(12, 13))
        // Inverted or empty windows are zero, never negative.
        assertEquals(0, GrandConcertRaceCalendar.raceableTurnsBetween(30, 20))
    }

    @Test
    @DisplayName("pre-debut and finale turns inside a window do not count as slack")
    fun preDebutAndFinaleExcludedInWindow() {
        // Window 5..14 includes pre-debut turns 6-11; only 12,13,14 are raceable.
        assertEquals(3, GrandConcertRaceCalendar.raceableTurnsBetween(5, 14))
        // Window 70..75 includes finale turns 73-75; only 71,72 are raceable.
        assertEquals(2, GrandConcertRaceCalendar.raceableTurnsBetween(70, 75))
    }
}
