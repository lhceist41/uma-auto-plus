package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the Smart Borrow priority selection. Row texts mirror the two-line OCR reads
 * from the 2026-07-13 picker captures.
 */
@DisplayName("Smart Borrow priority selection")
class SmartBorrowTest {
    private val page1 =
        listOf(
            "[Tracen Reception]\nTazuna Hayakawa",
            "[Touching Sleeves Is Good Luck! ♪]\nMatikanefukukitaru",
            "[Dreams Do Come True!]\nWinning Ticket",
            "[Fire at My Heels]\nKitasan Black",
            "[Beyond This Shining Moment]\nSilence Suzuka",
        )

    @Test
    @DisplayName("The highest-priority card wins even when a lower-priority one sits higher in the list")
    fun testPriorityBeatsRowOrder() {
        // Tazuna (priority 3) is row 0, but Kitasan (priority 0) on row 3 must win.
        val best = smartBorrowBestMatch(page1)
        assertEquals(0 to 3, best, "Kitasan Black is the highest-priority card in the pool")
    }

    @Test
    @DisplayName("Without the top cards, the best remaining list entry is picked")
    fun testBestRemaining() {
        val rows = listOf("[Dreams Do Come True!]\nWinning Ticket", "[Tracen Reception]\nTazuna Hayakawa")
        val best = smartBorrowBestMatch(rows)
        assertEquals(SmartBorrowList.priority.indexOfFirst { it.contains("Tazuna") } to 1, best, "Tazuna is the best list card present; Winning Ticket is not on the list")
    }

    @Test
    @DisplayName("A pool with no list cards yields no pick")
    fun testNoListCard() {
        assertNull(smartBorrowBestMatch(listOf("[Dreams Do Come True!]\nWinning Ticket", "[Run(my)way]\nGold City")))
        assertNull(smartBorrowBestMatch(emptyList()))
    }

    @Test
    @DisplayName("A user preference passed as priority zero outranks the whole curated list")
    fun testPreferencePrepended() {
        val priorities = listOf("[Sentimental Flare ♪] Maruzensky") + SmartBorrowList.priority
        val rows = page1 + "[Sentimental Flare ♪]\nMaruzensky"
        assertEquals(0 to 5, smartBorrowBestMatch(rows, priorities), "The pinned card outranks Kitasan")
    }

    @Test
    @DisplayName("Excluding a character removes every outfit of that character from the priorities")
    fun testFilterExcludesCharacterWide() {
        val priorities = listOf(
            "[Fire at My Heels] Kitasan Black",
            "[Overflowing Feelings] Kitasan Black",
            "[Piece of Mind] Super Creek",
        )
        assertEquals(listOf("[Piece of Mind] Super Creek"), filterBorrowPriorities(priorities, setOf("Kitasan Black")))
        assertEquals(priorities, filterBorrowPriorities(priorities, emptySet()), "No exclusions leaves the list untouched")
    }

    @Test
    @DisplayName("After excluding the deck's clash, the next-best available card wins")
    fun testReplacementPickMovesDownTheList() {
        val filtered = filterBorrowPriorities(SmartBorrowList.priority, setOf("Kitasan Black"))
        val best = smartBorrowBestMatch(page1, filtered)
        assertEquals(filtered.indexOfFirst { it.contains("Tazuna") } to 0, best, "Tazuna is the best remaining list card; the Kitasan row no longer matches anything")
    }

    @Test
    @DisplayName("The character part is read from a clean entry or an OCR two-line read")
    fun testBorrowEntryCharacter() {
        assertEquals("Kitasan Black", borrowEntryCharacter("[Fire at My Heels] Kitasan Black"))
        assertEquals("Kitasan Black", borrowEntryCharacter("[Fire at My Heels]\nKitasan Black"))
        assertEquals("Kitasan Black", borrowEntryCharacter("Fire at My Heels\nKitasan Black"))
    }
}

/**
 * Unit tests for the active-trainee borrow conflict: the game refuses a deck whose borrowed
 * support is the trainee's own character ("! Trainee" pill, disabled Start Career, "Includes a
 * character identical to the Trainee."). A live queued run was lost to exactly this - the
 * default pick borrowed a Super Creek support onto a Super Creek launch - so these lock in the
 * identity rejection, the message detection, and the exclusion mechanics that recover it.
 */
@DisplayName("Active-trainee borrow conflict")
class TraineeConflictBorrowTest {
    @Test
    @DisplayName("A Super Creek trainee rejects a Super Creek support candidate")
    fun testTraineeRejectsOwnCharacter() {
        assertEquals(true, borrowCandidateConflictsWithTrainee("[Piece of Mind]\nSuper Creek", "Super Creek"))
    }

    @Test
    @DisplayName("A Super Creek trainee accepts a Maruzensky support candidate")
    fun testTraineeAcceptsOtherCharacter() {
        assertEquals(false, borrowCandidateConflictsWithTrainee("[Sentimental Flare ♪]\nMaruzensky", "Super Creek"))
    }

    @Test
    @DisplayName("An outfit-prefixed trainee identity still rejects the same base character")
    fun testOutfitPrefixedTraineeRejectsBaseCharacter() {
        assertEquals(true, borrowCandidateConflictsWithTrainee("[Wonder Bouquet]\nGrass Wonder", "[Saintly Jade Cleric] Grass Wonder"))
    }

    @Test
    @DisplayName("Different outfits of the same character still count as the same trainee identity")
    fun testDifferentOutfitsSameCharacter() {
        assertEquals(true, borrowCandidateConflictsWithTrainee("[Piece of Mind]\nSuper Creek", "[Murmuring Stream] Super Creek"))
    }

    @Test
    @DisplayName("OCR noise in the row (brackets, line breaks, spacing) does not hide the conflict")
    fun testOcrNoiseStillConflicts() {
        assertEquals(true, borrowCandidateConflictsWithTrainee("(Piece of Mind]  Super  Creek", "Super Creek"))
    }

    @Test
    @DisplayName("A duplicate-flagged OTHER character is not a trainee conflict (the reasons stay distinct)")
    fun testDuplicateReasonStaysDistinctFromTraineeReason() {
        assertEquals(false, borrowCandidateConflictsWithTrainee("[Special Dreamers!]\nSatono Diamond", "Super Creek"))
    }

    @Test
    @DisplayName("Similar names never cross-match: Gold Ship vs Gold City, Mejiro McQueen vs Mejiro Ryan")
    fun testNoFalseMatchOnSimilarNames() {
        assertEquals(false, borrowCandidateConflictsWithTrainee("[Autumn Cosmos]\nGold City", "Gold Ship"))
        assertEquals(false, borrowCandidateConflictsWithTrainee("[Run! Golshi-chan!]\nGold Ship", "Gold City"))
        assertEquals(false, borrowCandidateConflictsWithTrainee("[Down the Line]\nMejiro Ryan", "Mejiro McQueen"))
    }

    @Test
    @DisplayName("A blank trainee identity never conflicts (non-rotation launches stay unchanged)")
    fun testBlankTraineeNeverConflicts() {
        assertEquals(false, borrowCandidateConflictsWithTrainee("[Piece of Mind]\nSuper Creek", ""))
    }

    @Test
    @DisplayName("Seeding the exclusion set with the trainee removes her cards from the curated priorities")
    fun testTraineeSeedFiltersCuratedList() {
        val filtered = filterBorrowPriorities(SmartBorrowList.priority, setOf("Super Creek"))
        assertEquals(false, filtered.any { it.contains("Super Creek") }, "the trainee's own curated entry must be dropped")
        assertEquals(true, filtered.any { it.contains("Kitasan Black") }, "other entries stay")
    }

    @Test
    @DisplayName("With the trainee excluded, the search continues to the next valid candidate")
    fun testSearchContinuesToNextValidCandidate() {
        val rows = listOf("[Piece of Mind]\nSuper Creek", "[Tracen Reception]\nTazuna Hayakawa")
        val filtered = filterBorrowPriorities(SmartBorrowList.priority, setOf("Super Creek"))
        val best = smartBorrowBestMatch(rows, filtered)
        assertEquals(filtered.indexOfFirst { it.contains("Tazuna") } to 1, best, "the Creek row must not match; Tazuna is the next valid candidate")
    }

    @Test
    @DisplayName("No valid candidate yields a null pick (the flow then stops with a specific error)")
    fun testNoValidCandidateIsSafeTerminal() {
        val rows = listOf("[Piece of Mind]\nSuper Creek")
        val filtered = filterBorrowPriorities(listOf("[Piece of Mind] Super Creek"), setOf("Super Creek"))
        assertEquals(0, filtered.size)
        assertNull(smartBorrowBestMatch(rows, filtered))
    }

    @Test
    @DisplayName("The blocking formation message is detected across OCR variants")
    fun testTraineeConflictMessageVariants() {
        assertEquals(true, isTraineeConflictMessage("Includes a character identical to the Trainee."))
        assertEquals(true, isTraineeConflictMessage("includes a character identical to the trainee"))
        assertEquals(true, isTraineeConflictMessage("lncludes a character identicaI to the Trainee."), "capital-I/lowercase-l OCR swaps in edge words still leave the core intact")
        assertEquals(true, isTraineeConflictMessage("Includes a character identical to the\nTrainee."))
    }

    @Test
    @DisplayName("Unrelated refusal text does not read as a trainee conflict")
    fun testOtherMessagesAreNotTraineeConflicts() {
        assertEquals(false, isTraineeConflictMessage("Cannot proceed with duplicate Umamusume in the Support Card deck"))
        assertEquals(false, isTraineeConflictMessage(""))
        assertEquals(false, isTraineeConflictMessage("Start Career!"))
    }
}
