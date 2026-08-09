package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.Racing.Companion.enteredRaceFromResolution
import com.steve1316.uma_android_automation.bot.Racing.Companion.selectTrackblazerRaceFact
import com.steve1316.uma_android_automation.bot.Racing.Companion.trackblazerWinner
import com.steve1316.uma_android_automation.types.PredictionTier
import com.steve1316.uma_android_automation.types.RaceGrade
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.TrackSurface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.opencv.core.Point

/**
 * Unit tests for the Trackblazer entered-race resolution provenance seam.
 *
 * The landed Phase 1 Trackblazer override stamped `exact/1` unconditionally; these pin that a completed
 * Trackblazer optional race now carries the SELECTED row's own lookup provenance (exact / ambiguousSet /
 * fuzzy) instead. The functions under test are pure ([trackblazerWinner], [selectTrackblazerRaceFact])
 * so no device is needed, and the selected candidate carries its own tier - never a global one that a
 * later row's lookup could overwrite.
 */
@DisplayName("Trackblazer Race Provenance Tests")
class TrackblazerRaceProvenanceTest {
    private fun candidate(
        name: String,
        grade: RaceGrade = RaceGrade.G1,
        fans: Int = 1000,
        starTier: PredictionTier = PredictionTier.DOUBLE,
        lookupTier: Racing.LookupTier = Racing.LookupTier.EXACT,
        matchCount: Int = 1,
        isRival: Boolean = false,
        raceTurnNumber: Int = 99,
    ): Racing.TrackblazerCandidate =
        Racing.TrackblazerCandidate(
            point = Point(0.0, 0.0),
            race = Racing.RaceData(name, grade, fans, name, TrackSurface.TURF, TrackDistance.MILE, raceTurnNumber, isRival),
            detectedName = name,
            isRival = isRival,
            starTier = starTier,
            lookupTier = lookupTier,
            matchCount = matchCount,
        )

    @Test
    @DisplayName("exact unique selection serializes exact with name and matchCount 1")
    fun exactUnique() {
        val fact = selectTrackblazerRaceFact(listOf(candidate("Osaka Hai", lookupTier = Racing.LookupTier.EXACT, matchCount = 1)), turn = 16)!!
        assertEquals(EnteredRaceResolution.EXACT, fact.resolution)
        assertEquals(EnteredRacePath.SMART, fact.path)
        assertEquals("Osaka Hai", fact.name)
        assertEquals(1, fact.matchCount) // Trackblazer keeps the live-validated exact/1 shape.
        assertEquals(16, fact.turnNumber)
    }

    @Test
    @DisplayName("fuzzy unique selection is fuzzy, not exact")
    fun fuzzyUnique() {
        val fact = selectTrackblazerRaceFact(listOf(candidate("Hanshin Cup", lookupTier = Racing.LookupTier.FUZZY, matchCount = 1)), turn = 72)!!
        assertEquals(EnteredRaceResolution.FUZZY, fact.resolution)
        assertEquals("Hanshin Cup", fact.name)
        assertEquals(1, fact.matchCount)
    }

    @Test
    @DisplayName("exact multi-match is ambiguousSet with matchCount and no false unique name")
    fun exactAmbiguous() {
        val fact = selectTrackblazerRaceFact(listOf(candidate("Twin Turbo Cup", lookupTier = Racing.LookupTier.EXACT, matchCount = 2)), turn = 40)!!
        assertEquals(EnteredRaceResolution.AMBIGUOUS_SET, fact.resolution)
        assertEquals(2, fact.matchCount)
        assertNull(fact.name) // no index-0 flatten for an ambiguous set
    }

    @Test
    @DisplayName("fuzzy multi-match preserves fuzzy ambiguity truthfully")
    fun fuzzyMulti() {
        val fact = selectTrackblazerRaceFact(listOf(candidate("Blurred Cup", lookupTier = Racing.LookupTier.FUZZY, matchCount = 3)), turn = 40)!!
        assertEquals(EnteredRaceResolution.FUZZY, fact.resolution) // not relabeled exact
        assertEquals(3, fact.matchCount)
        assertNull(fact.name)
    }

    @Test
    @DisplayName("the selected candidate carries its own tier even when other rows resolved differently")
    fun winnerCarriesOwnTier() {
        // The rival race wins the sort; it resolved via the fuzzy tier. A non-winning exact row was also
        // scanned. The fact must reflect the WINNER's fuzzy tier, not the loser's exact one nor a global.
        val fuzzyRivalWinner = candidate("Rival Race", fans = 500, lookupTier = Racing.LookupTier.FUZZY, matchCount = 1, isRival = true)
        val exactLoser = candidate("Plain Race", fans = 9000, lookupTier = Racing.LookupTier.EXACT, matchCount = 1, isRival = false)
        val winner = trackblazerWinner(listOf(exactLoser, fuzzyRivalWinner))!!
        assertEquals("Rival Race", winner.race.name) // rival beats higher fans
        val fact = selectTrackblazerRaceFact(listOf(exactLoser, fuzzyRivalWinner), turn = 20)!!
        assertEquals(EnteredRaceResolution.FUZZY, fact.resolution)
        assertEquals("Rival Race", fact.name)
    }

    @Test
    @DisplayName("same-name rows do not overwrite provenance: the winner's own candidate is used")
    fun sameNameNoOverwrite() {
        // Two rows share a bare name but resolved with different tiers (a degraded second read). Because
        // provenance travels with each candidate object (not a name-keyed map), the winner keeps its tier.
        val exactHigh = candidate("Twin Cup", fans = 8000, lookupTier = Racing.LookupTier.EXACT, matchCount = 1)
        val fuzzyLow = candidate("Twin Cup", fans = 1000, lookupTier = Racing.LookupTier.FUZZY, matchCount = 1)
        // Higher fans wins (both non-rival, same tier/grade), so the exact candidate is selected.
        val fact = selectTrackblazerRaceFact(listOf(fuzzyLow, exactHigh), turn = 30)!!
        assertEquals(EnteredRaceResolution.EXACT, fact.resolution)
        assertEquals("Twin Cup", fact.name)
    }

    @Test
    @DisplayName("telemetry turn is the passed current turn, never RaceData.turnNumber")
    fun turnIsCurrentNotRaceData() {
        // The candidate's RaceData carries turnNumber 99, but the fact must serialize the current turn.
        val fact = selectTrackblazerRaceFact(listOf(candidate("Kikuka Sho", raceTurnNumber = 99)), turn = 44)!!
        assertEquals(44, fact.turnNumber)
    }

    @Test
    @DisplayName("no suitable candidate yields no fact (abort emits nothing)")
    fun emptyYieldsNull() {
        assertNull(selectTrackblazerRaceFact(emptyList(), turn = 10))
        assertNull(trackblazerWinner(emptyList()))
    }

    @Test
    @DisplayName("the provenance refactor does not change which race is selected")
    fun selectionUnchangedByProvenance() {
        // Selection is Rival > tier > grade > fans and is independent of the lookup provenance fields.
        // Same candidate set, differing only in lookupTier/matchCount, selects the identical winner.
        val a = candidate("A", grade = RaceGrade.G3, fans = 5000, lookupTier = Racing.LookupTier.EXACT, matchCount = 1)
        val b = candidate("B", grade = RaceGrade.G1, fans = 100, lookupTier = Racing.LookupTier.FUZZY, matchCount = 4)
        val c = candidate("C", grade = RaceGrade.G1, fans = 3000, lookupTier = Racing.LookupTier.EXACT, matchCount = 2)
        // Among equal top tier (DOUBLE) and no rival, G1 beats G3, then higher fans: C wins over B, over A.
        assertEquals("C", trackblazerWinner(listOf(a, b, c))!!.race.name)
        // Flipping only provenance fields keeps the same winner.
        val cFlipped = c.copy(lookupTier = Racing.LookupTier.FUZZY, matchCount = 1)
        assertEquals("C", trackblazerWinner(listOf(a, b, cFlipped))!!.race.name)
    }

    @Test
    @DisplayName("wire tokens and the resolution mapping are unchanged for each tier")
    fun resolutionMappingWireTokens() {
        // Direct mapping checks, independent of Trackblazer selection.
        assertEquals(
            "exact",
            enteredRaceFromResolution(EnteredRacePath.SMART, 5, Racing.LookupTier.EXACT, 1, "X", unitMatchCount = 1).resolution.wire,
        )
        assertEquals(
            "fuzzy",
            enteredRaceFromResolution(EnteredRacePath.SMART, 5, Racing.LookupTier.FUZZY, 1, "X").resolution.wire,
        )
        assertEquals(
            "ambiguousSet",
            enteredRaceFromResolution(EnteredRacePath.SMART, 5, Racing.LookupTier.EXACT, 2, null).resolution.wire,
        )
        // A base (mandatory/scheduled) exact match omits matchCount; only the Trackblazer path stamps 1.
        assertFalse(enteredRaceFromResolution(EnteredRacePath.MANDATORY_GOAL, 5, Racing.LookupTier.EXACT, 1, "X").matchCount != null)
        assertTrue(enteredRaceFromResolution(EnteredRacePath.SMART, 5, Racing.LookupTier.EXACT, 1, "X", unitMatchCount = 1).matchCount == 1)
    }
}
