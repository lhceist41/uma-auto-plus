package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the Borrow Card preference matcher. Row texts mirror what the picker's name
 * band actually OCRs: the outfit line and the character line, with brackets and a line break
 * (taken from the 2026-07-13 picker captures).
 */
@DisplayName("Borrow Card preference matching")
class BorrowPreferenceTest {
    @Test
    @DisplayName("A character name matches its row regardless of the outfit line and line breaks")
    fun testCharacterNameMatch() {
        assertTrue(borrowRowMatchesPreference("[Fire at My Heels]\nKitasan Black", "Kitasan Black"))
        assertTrue(borrowRowMatchesPreference("[Beyond This Shining Moment]\nSilence Suzuka", "silence suzuka"))
        assertTrue(borrowRowMatchesPreference("[Esteemed and Adored]\nHeirs to the Throne", "Heirs to the Throne"))
    }

    @Test
    @DisplayName("An outfit name matches its row too")
    fun testOutfitNameMatch() {
        assertTrue(borrowRowMatchesPreference("[Fire at My Heels]\nKitasan Black", "Fire at My Heels"))
        assertTrue(borrowRowMatchesPreference("[Touching Sleeves Is Good Luck! ♪]\nMatikanefukukitaru", "touching sleeves"))
    }

    @Test
    @DisplayName("Punctuation and case differences in the OCR read do not break the match")
    fun testOcrNoiseTolerance() {
        assertTrue(borrowRowMatchesPreference("[FIRE AT MY HEELS!] KITASAN-BLACK", "kitasan black"))
        assertTrue(borrowRowMatchesPreference("[Tracen Reception] Tazuna, Hayakawa", "Tazuna Hayakawa"))
    }

    @Test
    @DisplayName("A different card's row does not match")
    fun testNoFalseMatch() {
        assertFalse(borrowRowMatchesPreference("[Dreams Do Come True!]\nWinning Ticket", "Kitasan Black"))
        assertFalse(borrowRowMatchesPreference("[Wave of Gratitude]\nFine Motion", "Fire at My Heels"))
    }

    @Test
    @DisplayName("An empty or whitespace preference never matches")
    fun testEmptyPreference() {
        assertFalse(borrowRowMatchesPreference("[Fire at My Heels]\nKitasan Black", ""))
        assertFalse(borrowRowMatchesPreference("[Fire at My Heels]\nKitasan Black", "   "))
    }
}
