package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The live anchor cases come from the 2026-07-12 farm queue's recorded sets - the policy must
 * keep the sets that were right to keep and reroll the one that was right to redraw.
 */
class SparkRerollPolicyTest {
    @Test
    fun `three-star blue is never rerolled`() {
        // Daiwa career 1: Speed 3-star at 890 - the account's first 3-star blue.
        val verdict = SparkRerollPolicy.decide(3, 2, 1, 1, listOf(890, 522, 564, 623, 646))
        assertFalse(verdict.reroll)
    }

    @Test
    fun `two-star blue is kept even with weak holdings on a monster pool`() {
        // Even all-1100 stats price a redraw at 1.905 expected blue stars - below a held 2-star.
        val verdict = SparkRerollPolicy.decide(2, 1, 1, 0, listOf(1150, 1120, 1100, 1200, 1105))
        assertFalse(verdict.reroll)
    }

    @Test
    fun `one-star blue with mediocre holdings rerolls on a live pool`() {
        // Daiwa career 2: Power 1-star, pink 2-star, unique 2-star, no 3-star whites visible.
        val verdict = SparkRerollPolicy.decide(1, 2, 2, 0, listOf(1006, 550, 590, 922, 441))
        assertTrue(verdict.reroll)
    }

    @Test
    fun `one-star blue behind a three-star pink is kept`() {
        // El Condor career 5: Speed 1-star but Long 3-star - the pink holding outweighs the redraw.
        val verdict = SparkRerollPolicy.decide(1, 3, 2, 0, listOf(876, 731, 658, 855, 531))
        assertFalse(verdict.reroll)
    }

    @Test
    fun `one-star blue on a dead pool is kept`() {
        // Every stat under 600: a redraw cannot roll a 3-star blue, so 30 TP buys nothing.
        val verdict = SparkRerollPolicy.decide(1, 1, 1, 0, listOf(599, 598, 500, 400, 300))
        assertFalse(verdict.reroll)
    }

    @Test
    fun `one-star blue on a monster pool rerolls`() {
        val verdict = SparkRerollPolicy.decide(1, 1, 1, 0, listOf(1150, 1120, 1100, 1200, 1105))
        assertTrue(verdict.reroll)
    }

    @Test
    fun `visible three-star whites shield a one-star blue`() {
        // Same monster pool, but two 3-star whites on screen tip the price back to keeping.
        val verdict = SparkRerollPolicy.decide(1, 1, 1, 2, listOf(1150, 1120, 1100, 1200, 1105))
        assertFalse(verdict.reroll)
    }

    @Test
    fun `unreadable pink and unique rows price neutral`() {
        val verdict = SparkRerollPolicy.decide(1, null, null, 0, listOf(700, 700, 700, 700, 700))
        assertTrue(verdict.reroll)
    }

    @Test
    fun `fresh blue expectation averages the band odds over the five stats`() {
        assertEquals(1.555, SparkRerollPolicy.expectedFreshBlueStars(listOf(700, 700, 700, 700, 700)), 1e-9)
        assertEquals(1.282, SparkRerollPolicy.expectedFreshBlueStars(listOf(1006, 550, 590, 922, 441)), 1e-9)
        assertEquals(0.0, SparkRerollPolicy.expectedFreshBlueStars(emptyList()), 1e-9)
    }
}
