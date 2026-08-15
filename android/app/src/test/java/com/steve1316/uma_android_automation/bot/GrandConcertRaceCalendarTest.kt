package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The Grand Concert raceable-turn calendar: the factual "which turns can host a fan race" model that
 * a future fan-goal deadline reader will measure slack against. Raceability mirrors the game's own
 * `single_mode_turn.race_entry_type` for the Grand Concert turn set: every turn 12..72 is legal
 * (Summer and concert turns included). Pure and deterministic; no policy decision lives here. This is
 * base race-entry legality, NOT guaranteed free slack after mandatory actions.
 */
@DisplayName("Grand Concert race calendar")
class GrandConcertRaceCalendarTest {
    @Test
    @DisplayName("every career turn 12..72 is race-entry legal; pre-debut and finale off-turns are not")
    fun raceabilityByTurn() {
        // Pre-debut: no regular races below turn 12.
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(1))
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(11))
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(12))
        // Summer camp turns 37-40 and 61-64 ARE race-entry legal (race_entry_type = 1); the earlier
        // Summer exclusion was factually wrong.
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(37))
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(40))
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(41))
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(61))
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(64))
        assertTrue(GrandConcertRaceCalendar.isRaceableTurn(65))
        // Concert turns are ordinary race-entry-legal turns.
        for (concert in listOf(24, 36, 48, 60, 72)) {
            assertTrue(GrandConcertRaceCalendar.isRaceableTurn(concert), "concert turn $concert should be raceable")
        }
        // Above the career window (finale off-turns) is not raceable in this model.
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(73))
        assertFalse(GrandConcertRaceCalendar.isRaceableTurn(75))
    }

    @Test
    @DisplayName("a clear window counts every intervening career turn as raceable slack")
    fun plentyOfSlack() {
        // Turns 13..30 are all raceable: 18 opportunities.
        assertEquals(18, GrandConcertRaceCalendar.raceableTurnsBetween(12, 30))
    }

    @Test
    @DisplayName("Summer camp turns are counted as raceable slack")
    fun summerIsRaceable() {
        // Window 36..45 spans Summer 37-40; every turn 37..45 is race-entry legal, so all 9 count
        // (turn 36 is the exclusive lower bound). The old model wrongly subtracted the Summer turns.
        assertEquals(9, GrandConcertRaceCalendar.raceableTurnsBetween(36, 45))
        // A window entirely inside the second Summer (61..64) is fully raceable.
        assertEquals(4, GrandConcertRaceCalendar.raceableTurnsBetween(60, 64))
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
        // Window 70..75 reaches past the career window; only 71,72 are raceable.
        assertEquals(2, GrandConcertRaceCalendar.raceableTurnsBetween(70, 75))
    }
}
