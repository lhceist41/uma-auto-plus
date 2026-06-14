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
