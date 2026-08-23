package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Smart Borrow SELECTED-slot verifier (DeckLab Smart Borrow 2.0, Stage B/C).
 *
 * Proves the pure decision that turns "these reopened-picker rows carry the Selected marker" into a
 * verdict on whether the committed friend slot is the intent's card. It must fail CLOSED on everything
 * that could let a wrong or unproven selection pass: no marker, several markers, a different card, or a
 * contradicted limit break.
 */
@DisplayName("Smart Borrow selected-slot verifier")
class SmartBorrowSelectionVerifierTest {
    private fun intent(
        character: String = "Kitasan Black",
        title: String? = "Fire at My Heels",
        expectedLimitBreak: Int? = 4,
        supportCardId: Int = 30001,
    ) = SmartBorrowIntent(
        schema = SMART_BORROW_INTENT_SCHEMA,
        schemaVersion = 1,
        targetProfile = "Medium",
        sourceBorrowScanId = "bp-live",
        supportCardId = supportCardId,
        canonicalCharacter = character,
        canonicalTitle = title,
        displayName = "$character [$title]",
        rarity = "SSR",
        expectedLevel = 50,
        expectedLimitBreak = expectedLimitBreak,
        sourceAlias = null,
        resolutionPath = "EXACT_TITLE",
        recommendationEvidenceDigest = "djb2-00000000",
    )

    private fun row(
        character: String? = "Kitasan Black",
        outfit: String? = "Fire at My Heels",
        limitBreakIndex: Int? = 4,
        ownerAlias: String? = null,
    ) = LocatableBorrowRow(0, character, outfit, limitBreakIndex, 50, ownerAlias, false, "high")

    @Nested
    @DisplayName("verify")
    inner class Verify {
        @Test
        fun `the marked-selected row matching the intent verifies`() {
            val v = SmartBorrowSelectionVerifier.verify(intent(), listOf(row()))
            assertEquals(SelectedSlotVerdict.VERIFIED, v.verdict)
            assertSame("Kitasan Black", v.selectedRow?.character)
        }

        @Test
        fun `an OCR-noisy selected title still verifies through the canonical matcher`() {
            // The reopened picker split the name band; the outfit gained a corrupted closing bracket.
            val v = SmartBorrowSelectionVerifier.verify(intent(), listOf(row(outfit = "Fire at My Heelsl")))
            assertEquals(SelectedSlotVerdict.VERIFIED, v.verdict)
        }

        @Test
        fun `no selected marker fails closed`() {
            val v = SmartBorrowSelectionVerifier.verify(intent(), emptyList())
            assertEquals(SelectedSlotVerdict.NO_SELECTION, v.verdict)
            assertNull(v.selectedRow)
        }

        @Test
        fun `more than one selected marker fails closed`() {
            val v = SmartBorrowSelectionVerifier.verify(intent(), listOf(row(ownerAlias = "a"), row(ownerAlias = "b")))
            assertEquals(SelectedSlotVerdict.MULTIPLE_SELECTION, v.verdict)
            assertNull(v.selectedRow)
        }

        @Test
        fun `a different card in the slot is an identity mismatch`() {
            val v = SmartBorrowSelectionVerifier.verify(intent(), listOf(row(character = "Winning Ticket", outfit = "Dreams Do Come True!")))
            assertEquals(SelectedSlotVerdict.IDENTITY_MISMATCH, v.verdict)
            assertSame("Winning Ticket", v.selectedRow?.character)
        }

        @Test
        fun `a same-character wrong-outfit selection is an identity mismatch`() {
            val v = SmartBorrowSelectionVerifier.verify(intent(), listOf(row(outfit = "Blossoming Bond")))
            assertEquals(SelectedSlotVerdict.IDENTITY_MISMATCH, v.verdict)
        }

        @Test
        fun `a selected copy whose known limit break contradicts the intent is a mismatch`() {
            val v = SmartBorrowSelectionVerifier.verify(intent(expectedLimitBreak = 4), listOf(row(limitBreakIndex = 1)))
            assertEquals(SelectedSlotVerdict.IDENTITY_MISMATCH, v.verdict)
        }

        @Test
        fun `an unknown observed limit break is not decisive`() {
            // The pip band did not read; identity still matches, so the selection verifies.
            val v = SmartBorrowSelectionVerifier.verify(intent(expectedLimitBreak = 4), listOf(row(limitBreakIndex = null)))
            assertEquals(SelectedSlotVerdict.VERIFIED, v.verdict)
        }

        @Test
        fun `an untitled intent verifies on the character alone`() {
            val v = SmartBorrowSelectionVerifier.verify(intent(title = null), listOf(row(outfit = "anything")))
            assertEquals(SelectedSlotVerdict.VERIFIED, v.verdict)
        }
    }
}
