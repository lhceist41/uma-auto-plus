package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.Racing.Companion.FAN_EMERGENCY_TURN_WINDOW
import com.steve1316.uma_android_automation.bot.Racing.Companion.indexOfBestByTierThenFans
import com.steve1316.uma_android_automation.bot.Racing.Companion.isFanEmergency
import com.steve1316.uma_android_automation.bot.Racing.Companion.mergePredictionAnchors
import com.steve1316.uma_android_automation.types.PredictionTier
import com.steve1316.uma_android_automation.utils.CustomImageUtils.RaceDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.opencv.core.Point

/**
 * Unit tests for the race-list prediction anchor merging, the tier-aware extra-race
 * selection, and the fan-emergency policy predicate.
 */
@DisplayName("Racing Race Selection Tests")
class RacingRaceSelectionTest {
    // ////////////////////////////////////////////////////////////////////////////////////////////
    // isFanEmergency

    @Test
    @DisplayName("Fan emergency requires an unmet fan goal")
    fun fanEmergencyRequiresFanRequirement() {
        assertFalse(isFanEmergency(hasFanRequirement = false, turnsRemaining = 2))
    }

    @Test
    @DisplayName("Fan emergency activates inside the turn window")
    fun fanEmergencyInsideWindow() {
        assertTrue(isFanEmergency(hasFanRequirement = true, turnsRemaining = 0))
        assertTrue(isFanEmergency(hasFanRequirement = true, turnsRemaining = FAN_EMERGENCY_TURN_WINDOW))
    }

    @Test
    @DisplayName("Fan emergency stays off outside the turn window")
    fun fanEmergencyOutsideWindow() {
        assertFalse(isFanEmergency(hasFanRequirement = true, turnsRemaining = FAN_EMERGENCY_TURN_WINDOW + 1))
    }

    @Test
    @DisplayName("Fan emergency stays off when the turns-remaining OCR failed")
    fun fanEmergencyOcrFailure() {
        assertFalse(isFanEmergency(hasFanRequirement = true, turnsRemaining = -1))
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////
    // mergePredictionAnchors

    @Test
    @DisplayName("Doubles alone merge to double-tier anchors sorted by y")
    fun mergeDoublesOnly() {
        val merged = mergePredictionAnchors(listOf(Point(900.0, 800.0), Point(900.0, 400.0)), emptyList())
        assertEquals(2, merged.size)
        assertEquals(400.0, merged[0].location.y)
        assertEquals(800.0, merged[1].location.y)
        assertTrue(merged.all { it.tier == PredictionTier.DOUBLE })
    }

    @Test
    @DisplayName("Singles alone merge to single-tier anchors")
    fun mergeSinglesOnly() {
        val merged = mergePredictionAnchors(emptyList(), listOf(Point(900.0, 400.0), Point(900.0, 600.0)))
        assertEquals(2, merged.size)
        assertTrue(merged.all { it.tier == PredictionTier.SINGLE })
    }

    @Test
    @DisplayName("A single match on the same row as a double is dropped")
    fun mergeDropsSingleOnSameRowAsDouble() {
        // A single-star template can weakly match inside a taller star stack ~30px off-center.
        val merged =
            mergePredictionAnchors(
                listOf(Point(900.0, 400.0)),
                listOf(Point(905.0, 430.0), Point(900.0, 650.0)),
            )
        assertEquals(2, merged.size)
        assertEquals(PredictionTier.DOUBLE, merged[0].tier)
        assertEquals(400.0, merged[0].location.y)
        assertEquals(PredictionTier.SINGLE, merged[1].tier)
        assertEquals(650.0, merged[1].location.y)
    }

    @Test
    @DisplayName("Duplicate single matches within one badge are deduplicated")
    fun mergeDropsDuplicateSingles() {
        val merged = mergePredictionAnchors(emptyList(), listOf(Point(900.0, 400.0), Point(902.0, 425.0)))
        assertEquals(1, merged.size)
        assertEquals(400.0, merged[0].location.y)
    }

    @Test
    @DisplayName("Starless points become none-tier anchors sorted with the rest")
    fun mergeStarlessOnly() {
        val merged = mergePredictionAnchors(emptyList(), emptyList(), listOf(Point(881.0, 600.0), Point(881.0, 400.0)))
        assertEquals(2, merged.size)
        assertEquals(400.0, merged[0].location.y)
        assertTrue(merged.all { it.tier == PredictionTier.NONE })
    }

    @Test
    @DisplayName("A starless point on the same row as a star anchor is dropped")
    fun mergeDropsStarlessOnStarRow() {
        // The fans icon exists on every row, so rows with a real prediction icon produce both a
        // star match and a projected starless point. The real tier must win.
        val merged =
            mergePredictionAnchors(
                listOf(Point(900.0, 400.0)),
                listOf(Point(900.0, 650.0)),
                listOf(Point(902.0, 402.0), Point(898.0, 648.0), Point(900.0, 880.0)),
            )
        assertEquals(3, merged.size)
        assertEquals(PredictionTier.DOUBLE, merged[0].tier)
        assertEquals(PredictionTier.SINGLE, merged[1].tier)
        assertEquals(PredictionTier.NONE, merged[2].tier)
        assertEquals(880.0, merged[2].location.y)
    }

    @Test
    @DisplayName("Rows a full entry apart are kept separate")
    fun mergeKeepsSeparateRows() {
        // Race list rows are ~200px apart; nothing at that distance may be merged.
        val merged =
            mergePredictionAnchors(
                listOf(Point(900.0, 400.0)),
                listOf(Point(900.0, 600.0)),
            )
        assertEquals(2, merged.size)
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////
    // indexOfBestByTierThenFans

    @Test
    @DisplayName("Among equal tiers the highest fan count wins")
    fun selectionPicksMaxFansWithinTier() {
        val races =
            listOf(
                RaceDetails(2000, true),
                RaceDetails(7000, true),
                RaceDetails(3300, true),
            )
        assertEquals(1, indexOfBestByTierThenFans(races))
    }

    @Test
    @DisplayName("A double-star race beats a bigger single-star race")
    fun selectionPrefersTierOverFans() {
        val races =
            listOf(
                RaceDetails(15000, false, predictionTier = PredictionTier.SINGLE),
                RaceDetails(1200, true),
            )
        assertEquals(1, indexOfBestByTierThenFans(races))
    }

    @Test
    @DisplayName("With singles only, the highest fan count wins")
    fun selectionFallsBackToBestSingle() {
        val races =
            listOf(
                RaceDetails(1500, false, predictionTier = PredictionTier.SINGLE),
                RaceDetails(3300, false, predictionTier = PredictionTier.SINGLE),
                RaceDetails(-1, false),
            )
        assertEquals(1, indexOfBestByTierThenFans(races))
    }

    @Test
    @DisplayName("A single-star race beats a row with no prediction icon")
    fun selectionPrefersSingleOverNone() {
        val races =
            listOf(
                RaceDetails(5000, false),
                RaceDetails(800, false, predictionTier = PredictionTier.SINGLE),
            )
        assertEquals(1, indexOfBestByTierThenFans(races))
    }

    @Test
    @DisplayName("An empty list returns -1")
    fun selectionEmptyList() {
        assertEquals(-1, indexOfBestByTierThenFans(emptyList()))
    }

    // ////////////////////////////////////////////////////////////////////////////////////////////
    // RaceDetails tier default

    @Test
    @DisplayName("RaceDetails derives its tier from hasDoublePredictions when not specified")
    fun raceDetailsTierDefault() {
        assertEquals(PredictionTier.DOUBLE, RaceDetails(1000, true).predictionTier)
        assertEquals(PredictionTier.NONE, RaceDetails(1000, false).predictionTier)
    }
}
