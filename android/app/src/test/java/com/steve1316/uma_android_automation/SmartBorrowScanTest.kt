package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for the bounded Borrow Card list scanner.
 *
 * Every fixture here is synthetic. The scanner has no opinion about which cards exist, so the
 * tests drive it with invented names and an invented priority list; a test that needed a real
 * card name would be testing the curated list instead of the scanner.
 */
@DisplayName("Smart Borrow bounded list scan")
class SmartBorrowScanTest {
    /** Invented priority order, standing in for the curated list. */
    private val priorities = listOf("Alpha Ace", "Bravo Bell", "Charlie Chase")

    private val target = priorities[0]

    private fun rankOf(text: String): Int? = smartBorrowBestMatch(listOf(text), priorities)?.first

    private fun approved(text: String, intended: String? = null): Boolean = borrowTapApproved(text, intended, emptySet(), "")

    /** A screen of borrowable rows, laid out on the measured row pitch. */
    private fun screen(vararg rows: String): BorrowScan = BorrowScan(rows.mapIndexed { i, text -> (440.0 + i * 262.0) to text })

    /** A screen whose every row carries the game's "! Duplicate Support" tag: nothing borrowable. */
    private fun blockedScreen(vararg rows: String): BorrowScan = BorrowScan(emptyList(), duplicateTexts = rows.toList())

    /**
     * A scripted picker. [screens] is what successive page gestures reveal; once the last screen
     * is reached the list cannot scroll further and keeps returning it, which is exactly how the
     * real list behaves at its end.
     */
    private class FakePicker(
        private val screens: List<BorrowScan>,
        private val swallowGestures: Int = 0,
        private val frozen: Boolean = false,
    ) {
        var index = 0
            private set
        var gestures = 0
            private set
        private var swallowed = 0

        fun read(): BorrowScan = screens[index]

        fun advance() {
            gestures++
            if (swallowed < swallowGestures) {
                swallowed++
                return
            }
            if (!frozen && index < screens.lastIndex) index++
        }

        fun walker(maxPageGestures: Int = 8, maxSwallowedRetries: Int = 2): BorrowListWalker =
            BorrowListWalker(
                maxPageGestures = maxPageGestures,
                maxSwallowedRetries = maxSwallowedRetries,
                readScreen = ::read,
                advancePage = ::advance,
            )
    }

    @Test
    @DisplayName("A screen of nothing but blocked rows still counts as a screen")
    fun testBlockedRowsCountAsMovement() {
        val blocked = blockedScreen("[Outfit One]\nDelta Dawn", "[Outfit Two]\nEcho Edge")
        // The old freshness rule counted borrowable rows only, so this screen looked empty and
        // read as the end of the list. Both halves of that are locked here.
        assertTrue(blocked.rows.isEmpty(), "a fully blocked screen offers no borrowable row")
        assertEquals(2, borrowScreenKeys(blocked).size, "but it still carries two readable row identities")
        assertTrue(borrowScreenSignature(blocked).isNotEmpty(), "and therefore has a signature to compare against")
    }

    @Test
    @DisplayName("1. The card is on the first screen: it is taken without paging")
    fun testCandidateOnSamePage() {
        val picker = FakePicker(listOf(screen("[Outfit One]\n$target", "[Outfit Two]\nDelta Dawn")))
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNotNull(selection.row, "the card is right there")
        assertEquals(1, selection.walk.screensInspected)
        assertEquals(0, picker.gestures, "no page gesture is needed for a card on the first screen")
    }

    @Test
    @DisplayName("2. The card moved to a deeper screen after the reopen: the scan pages down to it")
    fun testCandidateMovedDeeper() {
        val picker =
            FakePicker(
                listOf(
                    screen("[Outfit Two]\nDelta Dawn"),
                    screen("[Outfit Three]\nEcho Edge"),
                    screen("[Outfit Four]\nFoxtrot Fall"),
                    screen("[Outfit One]\n$target"),
                ),
            )
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNotNull(selection.row, "a card three screens down is still reachable")
        assertEquals(4, selection.walk.screensInspected)
        assertEquals(3, picker.gestures)
    }

    @Test
    @DisplayName("3. The card moved up to the first screen: it is taken immediately")
    fun testCandidateMovedEarlier() {
        val picker =
            FakePicker(
                listOf(
                    screen("[Outfit One]\n$target"),
                    screen("[Outfit Two]\nDelta Dawn"),
                    screen("[Outfit Three]\nEcho Edge"),
                ),
            )
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNotNull(selection.row)
        assertEquals(1, selection.walk.screensInspected)
        assertEquals(0, picker.gestures)
    }

    @Test
    @DisplayName("4. The whole first screen is duplicates and the card sits below it: it is still found")
    fun testAllDuplicatesOnTopThenValidCandidate() {
        // This is the live failure: the reopened list opened on a screen of nothing but blocked
        // rows, and the scan stopped there with the rest of the list unread.
        val picker =
            FakePicker(
                listOf(
                    blockedScreen("[Outfit Two]\nDelta Dawn", "[Outfit Three]\nEcho Edge", "[Outfit Four]\nFoxtrot Fall"),
                    blockedScreen("[Outfit Five]\nGolf Gate", "[Outfit Six]\nHotel Hill"),
                    screen("[Outfit One]\n$target"),
                ),
            )
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNotNull(selection.row, "two screens of blocked rows must not be mistaken for the end of the list")
        assertEquals(target, rankOf(selection.row!!.second)?.let { priorities[it] })
        assertEquals(3, selection.walk.screensInspected)
        assertEquals(2, picker.gestures)
    }

    @Test
    @DisplayName("5. The chosen card is gone: the best other ranked card is taken instead")
    fun testChosenCardGoneRankedAlternativeTaken() {
        // Pass one hunts the chosen card and records what else the list offers.
        val firstOpen =
            FakePicker(
                listOf(
                    screen("[Outfit Nine]\nIndia Isle"),
                    screen("[Outfit Two]\n${priorities[1]}"),
                    screen("[Outfit Three]\n${priorities[2]}"),
                ),
            )
        var bestSeenRank = Int.MAX_VALUE
        val pass1 =
            selectFromBorrowList(
                firstOpen.walker(),
                observe = { text -> rankOf(text)?.let { if (it < bestSeenRank) bestSeenRank = it } },
            ) { approved(it, target) }
        assertNull(pass1.row, "the chosen card is not in the pool any more")
        assertEquals(1, bestSeenRank, "but the next-best ranked card was seen on the way")

        // Pass two takes the best card the list actually still has.
        val secondOpen = FakePicker(listOf(screen("[Outfit Nine]\nIndia Isle", "[Outfit Two]\n${priorities[1]}")))
        val pass2 =
            selectFromBorrowList(secondOpen.walker()) { text ->
                val rank = rankOf(text)
                rank != null && rank <= bestSeenRank && approved(text)
            }
        assertNotNull(pass2.row)
        assertEquals(1, rankOf(pass2.row!!.second), "the ranked alternative, not the first row on screen")
    }

    @Test
    @DisplayName("6. The chosen card is gone and nothing is ranked: an unranked valid row is still usable")
    fun testChosenCardGoneUnrankedFallback() {
        val picker = FakePicker(listOf(screen("[Outfit Nine]\nIndia Isle", "[Outfit Ten]\nJuliet Jade")))
        var bestSeenRank = Int.MAX_VALUE
        val ranked =
            selectFromBorrowList(
                picker.walker(),
                observe = { text -> rankOf(text)?.let { if (it < bestSeenRank) bestSeenRank = it } },
            ) { approved(it, target) }
        assertNull(ranked.row)
        assertEquals(Int.MAX_VALUE, bestSeenRank, "nothing on the curated list is on offer")

        val anyValid = FakePicker(listOf(screen("[Outfit Nine]\nIndia Isle"))).let { p -> selectFromBorrowList(p.walker()) { approved(it) } }
        assertNotNull(anyValid.row, "an untagged row of a character the deck has not refused is still a legal borrow")
    }

    @Test
    @DisplayName("7. Every reachable row is blocked: nothing is selected and the scan ends cleanly")
    fun testEveryRowDuplicate() {
        val picker =
            FakePicker(
                listOf(
                    blockedScreen("[Outfit Two]\nDelta Dawn"),
                    blockedScreen("[Outfit Three]\nEcho Edge"),
                    blockedScreen("[Outfit Four]\nFoxtrot Fall"),
                ),
            )
        val selection = selectFromBorrowList(picker.walker()) { approved(it) }
        assertNull(selection.row, "no borrowable row exists anywhere in the list")
        assertEquals(BorrowWalkEnd.END_OF_LIST, selection.walk.end)
        assertEquals(3, selection.walk.screensInspected, "and the scan proved it by reading the whole list")
    }

    @Test
    @DisplayName("8. The list is in a different order on every open: the card is found each time")
    fun testReorderBetweenEveryOpen() {
        val orders =
            listOf(
                listOf(screen("[Outfit One]\n$target"), screen("[Outfit Two]\nDelta Dawn")),
                listOf(screen("[Outfit Two]\nDelta Dawn"), screen("[Outfit One]\n$target")),
                listOf(blockedScreen("[Outfit Three]\nEcho Edge"), screen("[Outfit Two]\nDelta Dawn"), screen("[Outfit One]\n$target")),
            )
        orders.forEachIndexed { openIndex, screens ->
            val selection = selectFromBorrowList(FakePicker(screens).walker()) { approved(it, target) }
            assertNotNull(selection.row, "open ${openIndex + 1} must find the card wherever it landed")
        }
    }

    @Test
    @DisplayName("9. A swallowed page gesture is retried and the scan continues")
    fun testSwallowedGestureRetried() {
        val picker =
            FakePicker(
                listOf(screen("[Outfit Two]\nDelta Dawn"), screen("[Outfit One]\n$target")),
                swallowGestures = 1,
            )
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNotNull(selection.row, "one dropped drag must not end the scan")
        assertEquals(1, selection.walk.swallowedRetries)
        assertEquals(2, picker.gestures, "the swallowed drag plus the one that landed")
    }

    @Test
    @DisplayName("10. The list stops moving at its true end and the scan stops with it")
    fun testEndOfListDetected() {
        val picker = FakePicker(listOf(screen("[Outfit Two]\nDelta Dawn"), screen("[Outfit Three]\nEcho Edge")))
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row)
        assertEquals(BorrowWalkEnd.END_OF_LIST, selection.walk.end)
        assertEquals(2, selection.walk.screensInspected)
        assertTrue(picker.gestures <= 4, "the tail costs the retry budget and no more, was ${picker.gestures}")
    }

    @Test
    @DisplayName("11. A list that never moves cannot loop the scan")
    fun testFrozenListTerminates() {
        val picker = FakePicker(listOf(screen("[Outfit Two]\nDelta Dawn")), frozen = true)
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row)
        assertEquals(BorrowWalkEnd.END_OF_LIST, selection.walk.end)
        assertEquals(1, selection.walk.screensInspected, "a screen that never changes is read once")
        assertEquals(2, selection.walk.swallowedRetries, "then retried up to the strict bound and given up on")
    }

    @Test
    @DisplayName("12. A pathological list is stopped by the page bound")
    fun testMaximumPageBound() {
        // Every screen is unique and the card is nowhere, so only the hard bound can stop this.
        val endless = (0..40).map { screen("[Outfit $it]\nRow number $it") }
        val picker = FakePicker(endless)
        val selection = selectFromBorrowList(picker.walker(maxPageGestures = 8)) { approved(it, target) }
        assertNull(selection.row)
        assertEquals(BorrowWalkEnd.MAX_PAGES, selection.walk.end)
        assertEquals(9, selection.walk.screensInspected, "the first screen plus eight advances")
        assertEquals(8, picker.gestures)
    }

    @Test
    @DisplayName("13. An unreadable row is never tapped")
    fun testUnreadableRowNotTapped() {
        assertFalse(borrowRowIsReadable(""), "a blank OCR read is not a row")
        assertFalse(borrowRowIsReadable("  \n "), "whitespace is not a row")
        assertFalse(borrowRowIsReadable("["), "a stray glyph is not a row")
        assertFalse(approved(""), "and none of those may be tapped")

        val picker = FakePicker(listOf(BorrowScan(listOf(440.0 to "", 702.0 to "[", 964.0 to "[Outfit Nine]\nIndia Isle"))))
        val selection = selectFromBorrowList(picker.walker()) { approved(it) }
        assertEquals(964.0, selection.row?.first, "the scan skips past the unreadable rows to the readable one")
    }

    @Test
    @DisplayName("14. A row that is not the intended card fails the pre-tap check and nothing is taken")
    fun testIdentityVerificationStopsSafely() {
        assertFalse(approved("[Outfit Two]\nDelta Dawn", target), "a different card must not pass as the intended one")
        assertTrue(approved("[Outfit One]\n$target", target), "the intended card passes")
        assertFalse(borrowTapApproved("[Outfit One]\n$target", target, setOf("Alpha Ace"), ""), "a refused character never passes")
        assertFalse(borrowTapApproved("[Outfit One]\nKilo King", null, emptySet(), "Kilo King"), "the active trainee's own character never passes")

        val picker = FakePicker(listOf(screen("[Outfit Two]\nDelta Dawn", "[Outfit Three]\nEcho Edge")))
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row, "the intended card is not on offer, so nothing is tapped")
    }

    @Test
    @DisplayName("15. The scanner carries no card, trainer, deck, or account specifics")
    fun testScannerIsAgnostic() {
        val source = findRepoFile("src/main/java/com/steve1316/uma_android_automation/SmartBorrowScan.kt")
        assertNotNull(source, "the scanner source must be locatable for this check")
        val text = source!!.readText()
        // The curated card list, the deck, the account and its trainers all live elsewhere. The
        // scanner only knows how to walk a list and read a row.
        for (forbidden in listOf("Kitasan", "Maruzensky", "Tazuna", "SSR", "Lvl", "Following", "Mutual", "SmartBorrowList")) {
            assertFalse(text.contains(forbidden, ignoreCase = true), "the scanner must not mention \"$forbidden\"")
        }
        // Proof by behavior as well as by text: the same scan works on wholly invented names.
        val invented = listOf("Zulu Zenith", "Yankee Yard")
        val picker = FakePicker(listOf(screen("[Made Up]\n${invented[1]}"), screen("[Made Up]\n${invented[0]}")))
        val selection = selectFromBorrowList(picker.walker()) { borrowTapApproved(it, invented[0], emptySet(), "") }
        assertNotNull(selection.row, "nothing in the scan depends on which cards exist")
    }

    /** Walks up from the test working directory to find a repository file, so the check does not
     * depend on which directory Gradle happened to run the tests from. */
    private fun findRepoFile(relative: String): File? {
        var dir: File? = File(".").absoluteFile
        repeat(8) {
            val direct = File(dir, relative)
            if (direct.isFile) return direct
            val underApp = File(dir, "app/$relative")
            if (underApp.isFile) return underApp
            dir = dir?.parentFile
        }
        return null
    }
}
