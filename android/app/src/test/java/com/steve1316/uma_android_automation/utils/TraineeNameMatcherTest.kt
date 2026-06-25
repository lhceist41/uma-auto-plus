package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [TraineeNameMatcher].
 *
 * The safety-critical property is outfit disambiguation: the same character can own several
 * outfits, and rotation targets one exactly, so a Trainee Select preview of the wrong outfit must
 * NOT clear the match threshold. These tests lock that in (and the normalization that enables it).
 */
@DisplayName("TraineeNameMatcher Tests")
class TraineeNameMatcherTest {
    // Mirrors the navigator's accept bar (CareerLaunchNavigator.traineeMatchThreshold).
    private val threshold = 0.86

    @Nested
    @DisplayName("normalize()")
    inner class NormalizeTests {
        @Test
        fun `strips brackets and keeps the outfit words`() {
            assertEquals("kukulkan warrior el condor pasa", TraineeNameMatcher.normalize("[Kukulkan Warrior] El Condor Pasa"))
        }

        @Test
        fun `de-accents and drops stars`() {
            assertEquals("e numero 1 el condor pasa", TraineeNameMatcher.normalize("[E☆Número 1] El Condor Pasa"))
        }

        @Test
        fun `collapses whitespace and lowercases`() {
            assertEquals("blossom in learning sakura bakushin o", TraineeNameMatcher.normalize("  [Blossom in Learning]   Sakura Bakushin O  "))
        }
    }

    @Nested
    @DisplayName("score()")
    inner class ScoreTests {
        @Test
        fun `identical names score 1`() {
            assertEquals(1.0, TraineeNameMatcher.score("[Kukulkan Warrior] El Condor Pasa", "[Kukulkan Warrior] El Condor Pasa"), 1e-9)
        }

        @Test
        fun `bracket-stripped OCR variant still clears the threshold`() {
            // The OCR may drop the brackets; the same outfit must still match.
            val s = TraineeNameMatcher.score("[Kukulkan Warrior] El Condor Pasa", "Kukulkan Warrior El Condor Pasa")
            assertTrue(s >= threshold, "expected >= $threshold, got $s")
        }

        @Test
        fun `wrong outfit of the same character stays below the threshold`() {
            // Both are El Condor Pasa; targeting Kukulkan must NOT accept the Numero 1 outfit.
            val s = TraineeNameMatcher.score("[Kukulkan Warrior] El Condor Pasa", "[E☆Número 1] El Condor Pasa")
            assertTrue(s < threshold, "wrong outfit should be rejected, got $s")
        }

        @Test
        fun `a different trainee scores below the threshold`() {
            val s = TraineeNameMatcher.score("[Kukulkan Warrior] El Condor Pasa", "[Wild Frontier] Taiki Shuttle")
            assertTrue(s < threshold, "different trainee should be rejected, got $s")
        }

        @Test
        fun `a plain character-name target matches an outfit-prefixed banner`() {
            // Most presets are named without an outfit ("Sweep Tosho"), but the banner always shows
            // one ("[Platanus Witch] Sweep Tosho"). The trailing name part must clear the threshold.
            val s = TraineeNameMatcher.score("Sweep Tosho", "[Platanus Witch] Sweep Tosho")
            assertTrue(s >= threshold, "expected >= $threshold, got $s")
        }

        @Test
        fun `a plain-name target rejects a different trainee`() {
            val s = TraineeNameMatcher.score("Sweep Tosho", "[Autumn Cosmos] Gold City")
            assertTrue(s < threshold, "different trainee should be rejected, got $s")
        }

        @Test
        fun `shared-prefix different trainee is rejected (Gold Ship vs Gold City)`() {
            // The live bug: rotation target "Gold Ship" must NOT match a pre-selected
            // "[Autumn Cosmos] Gold City" banner just because both start with "Gold".
            val s = TraineeNameMatcher.score("Gold Ship", "[Autumn Cosmos] Gold City")
            assertTrue(s < threshold, "Gold City should be rejected for target Gold Ship, got $s")
        }

        @Test
        fun `plain-name target matches its own outfit banner`() {
            val s = TraineeNameMatcher.score("Gold Ship", "[Run! Golshi-chan!] Gold Ship")
            assertTrue(s >= threshold, "expected >= $threshold, got $s")
        }

        @Test
        fun `single-character OCR slip in the name still matches`() {
            // "Ship" misread as "Shlp" (i -> l) is noise, not a different word.
            val s = TraineeNameMatcher.score("Gold Ship", "[Autumn Cosmos] Gold Shlp")
            assertTrue(s >= threshold, "OCR-noisy name should still match, got $s")
        }

        @Test
        fun `same outfit but different name is rejected`() {
            // Even with a matching outfit prefix, the differing name word must gate the match.
            val s = TraineeNameMatcher.score("[Autumn Cosmos] Gold City", "[Autumn Cosmos] Gold Ship")
            assertTrue(s < threshold, "different name under same outfit should be rejected, got $s")
        }

        @Test
        fun `shared first name different surname is rejected (Mejiro)`() {
            val s = TraineeNameMatcher.score("Mejiro McQueen", "Mejiro Ryan")
            assertTrue(s < threshold, "Mejiro Ryan should be rejected for target Mejiro McQueen, got $s")
        }
    }

    @Nested
    @DisplayName("bestMatch()")
    inner class BestMatchTests {
        private val roster =
            listOf(
                "[E☆Número 1] El Condor Pasa",
                "[Kukulkan Warrior] El Condor Pasa",
                "[Blossom in Learning] Sakura Bakushin O",
                "[Wild Frontier] Taiki Shuttle",
            )

        @Test
        fun `picks the exact outfit among same-character entries`() {
            val (name, score) = TraineeNameMatcher.bestMatch("[Kukulkan Warrior] El Condor Pasa", roster)!!
            assertEquals("[Kukulkan Warrior] El Condor Pasa", name)
            assertEquals(1.0, score, 1e-9)
        }

        @Test
        fun `returns null for an empty roster`() {
            assertNull(TraineeNameMatcher.bestMatch("[Kukulkan Warrior] El Condor Pasa", emptyList()))
        }
    }
}
