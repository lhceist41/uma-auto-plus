package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the [EnteredRace] model, its stable serialized enum tokens, and the
 * [PendingEnteredRace] holder lifecycle.
 *
 * These pin the two things a later consumer would silently misread if they broke: the wire strings a
 * reader keys on, and the per-turn lifecycle that guarantees a completed-race fact never leaks across
 * turns. The turn-source safety (never a bare-name-map turn) is enforced by construction - the model
 * simply holds whatever turn the writer passed - and is pinned at the serialization layer in
 * `DecisionTraceTest`.
 */
@DisplayName("EnteredRace Tests")
class EnteredRaceTest {
    @Test
    @DisplayName("resolution wire tokens are the stable contract strings")
    fun `resolution wire tokens`() {
        assertEquals("exact", EnteredRaceResolution.EXACT.wire)
        assertEquals("ambiguousSet", EnteredRaceResolution.AMBIGUOUS_SET.wire)
        assertEquals("fuzzy", EnteredRaceResolution.FUZZY.wire)
        assertEquals("unresolved", EnteredRaceResolution.UNRESOLVED.wire)
        assertEquals("nonCatalog", EnteredRaceResolution.NON_CATALOG.wire)
        // Exactly these five states exist; a new one is a schema decision, not an accident.
        assertEquals(5, EnteredRaceResolution.entries.size)
    }

    @Test
    @DisplayName("path wire tokens are the stable contract strings")
    fun `path wire tokens`() {
        assertEquals("mandatoryGoal", EnteredRacePath.MANDATORY_GOAL.wire)
        assertEquals("scheduled", EnteredRacePath.SCHEDULED.wire)
        assertEquals("plannedMandatory", EnteredRacePath.PLANNED_MANDATORY.wire)
        assertEquals("smart", EnteredRacePath.SMART.wire)
        assertEquals("standard", EnteredRacePath.STANDARD.wire)
        assertEquals("maiden", EnteredRacePath.MAIDEN.wire)
        assertEquals("standalone", EnteredRacePath.STANDALONE.wire)
        assertEquals("unityCupShowdown", EnteredRacePath.UNITY_CUP_SHOWDOWN.wire)
        assertEquals(8, EnteredRacePath.entries.size)
    }

    @Test
    @DisplayName("the holder starts empty, records, and clears")
    fun `holder lifecycle`() {
        val holder = PendingEnteredRace()
        // A fresh turn holds no fact.
        assertNull(holder.current())

        val fact = EnteredRace(34, EnteredRaceResolution.EXACT, EnteredRacePath.MANDATORY_GOAL, name = "Tokyo Yushun (Japanese Derby)")
        holder.record(fact)
        assertEquals(fact, holder.current())

        // The next decision turn clears it, so no completed-race fact leaks forward.
        holder.clear()
        assertNull(holder.current())
    }

    @Test
    @DisplayName("a later completion overwrites an earlier one (last-write-wins on the same turn)")
    fun `holder last write wins`() {
        val holder = PendingEnteredRace()
        // The base already-selected tail records an unresolved standalone fact first.
        holder.record(EnteredRace(30, EnteredRaceResolution.UNRESOLVED, EnteredRacePath.STANDALONE))
        // A scenario override then replaces it with the stronger identity it actually entered.
        val stronger = EnteredRace(30, EnteredRaceResolution.EXACT, EnteredRacePath.SMART, name = "Osaka Hai", matchCount = 1)
        holder.record(stronger)
        assertEquals(stronger, holder.current())
        assertEquals("Osaka Hai", holder.current()?.name)
        assertEquals(EnteredRacePath.SMART, holder.current()?.path)
    }
}
