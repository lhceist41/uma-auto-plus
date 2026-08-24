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
        /** Whether the injected service recovery reports it did something (models a live game attached). */
        private val recoverAvailable: Boolean = false,
        /** Whether a successful recovery actually revives scrolling (models the rebind fixing dead dispatch). */
        private val revivesAfterRecover: Boolean = false,
        /** Once the list reaches this index, gesture dispatch is dead (advances no-op) until a recovery
         * revives it -- models MuMu's mid-traversal dispatchGesture death after healthy paging. -1 = never. */
        private val dispatchDiesAtIndex: Int = -1,
        /** How many gestures AFTER a successful recovery are still swallowed before movement resumes --
         * models the first-gesture-after-rebind swallow that A3-R4 could not clear with a single retry. */
        private val postRebindSwallow: Int = 0,
        /** A SECOND dispatch death, at this index, that only bites after the first recovery -- models a fresh
         * gesture death at a later gap that the one-per-walk rebind may not touch again. -1 = never. */
        private val reDeathAtIndex: Int = -1,
    ) {
        var index = 0
            private set
        var gestures = 0
            private set
        var recoverCalls = 0
            private set
        private var swallowed = 0
        private var recovered = false
        private var postRecoverAdvances = 0

        fun read(): BorrowScan = screens[index]

        private fun dispatchAlive(): Boolean {
            // A fresh death at a later gap once the first recovery has already been spent.
            if (reDeathAtIndex >= 0 && recovered && index >= reDeathAtIndex) return false
            val deadZone = frozen || (dispatchDiesAtIndex >= 0 && index >= dispatchDiesAtIndex)
            if (!deadZone) return true
            if (!recovered || !revivesAfterRecover) return false
            // Recovered, but the first postRebindSwallow gestures after the rebind are still swallowed.
            return postRecoverAdvances > postRebindSwallow
        }

        fun advance(attempt: Int) {
            gestures++
            // The calibrated gesture (attempt 0) is what gets swallowed; the walker's escalated recovery
            // gesture (attempt >= 1) always lands, modelling the real stronger-drag recovery ladder.
            if (attempt == 0 && swallowed < swallowGestures) {
                swallowed++
                return
            }
            if (recovered) postRecoverAdvances++
            if (dispatchAlive() && index < screens.lastIndex) index++
        }

        fun recoverService(): Boolean {
            recoverCalls++
            if (recoverAvailable) recovered = true
            return recoverAvailable
        }

        fun walker(maxPageGestures: Int = 8, maxSwallowedRetries: Int = 2, maxPostRebindGestures: Int = 2): BorrowListWalker =
            BorrowListWalker(
                maxPageGestures = maxPageGestures,
                maxSwallowedRetries = maxSwallowedRetries,
                readScreen = ::read,
                advancePage = ::advance,
                recoverService = ::recoverService,
                maxPostRebindGestures = maxPostRebindGestures,
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

    @Test
    @DisplayName("16. A walk that ends on a repeated screen is fully traversed and not stalled")
    fun testNaturalEndIsFullyTraversed() {
        // The last screen repeats the first screen's rows (the overlap a real list shows near its end), so
        // the walk ends because nothing new appeared -- a credible complete traversal. No row is the target.
        val a = screen("[Outfit One]\nDelta Dawn")
        val b = screen("[Outfit Two]\nEcho Edge")
        val picker = FakePicker(listOf(a, b, a))
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row, "the intended card is nowhere in the list")
        assertEquals(BorrowWalkEnd.END_OF_LIST, selection.walk.end)
        assertFalse(selection.walk.stalled, "a repeated-rows end did not stall")
        assertTrue(selection.walk.fullyTraversed, "so the whole list was seen: a card's absence is genuine")
    }

    @Test
    @DisplayName("17. A list that never moves is marked stalled and NOT fully traversed")
    fun testFrozenWalkIsStalledNotComplete() {
        val picker = FakePicker(listOf(screen("[Outfit Two]\nDelta Dawn")), frozen = true)
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row)
        assertTrue(selection.walk.stalled, "the scroll never moved after the recovery ladder: stalled")
        assertFalse(selection.walk.fullyTraversed, "so the list was NOT fully traversed: absence is unproven")
    }

    @Test
    @DisplayName("18. A swallowed drag recovered by the escalated retry still reaches a full traversal")
    fun testSwallowRecoveredThenFullyTraversed() {
        // Attempt 0 (the calibrated gesture) is swallowed once; the walker's escalated retry (attempt >= 1)
        // lands and the walk reaches its natural repeated-rows end.
        val a = screen("[Outfit One]\nDelta Dawn")
        val b = screen("[Outfit Two]\nEcho Edge")
        val picker = FakePicker(listOf(a, b, a), swallowGestures = 1)
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row)
        assertEquals(1, selection.walk.swallowedRetries, "one drag was swallowed and recovered")
        assertFalse(selection.walk.stalled, "the escalated retry cleared the swallow, so the walk did not stall")
        assertTrue(selection.walk.fullyTraversed, "and the whole list was still seen")
    }

    @Test
    @DisplayName("19. A stall the gesture ladder cannot clear is revived by one service recovery and completes")
    fun testServiceRecoveryRevivesStalledScroll() {
        // The gesture ladder never moves the list (dead accessibility dispatch), so it exhausts. The single
        // service recovery reports it rebound the dispatcher, and the list moves from then on -- the walk
        // reaches its natural repeated-rows end instead of stalling.
        val a = screen("[Outfit One]\nDelta Dawn")
        val b = screen("[Outfit Two]\nEcho Edge")
        val picker =
            FakePicker(listOf(a, b, a), frozen = true, recoverAvailable = true, revivesAfterRecover = true)
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row)
        assertEquals(1, picker.recoverCalls, "exactly one bounded recovery was performed")
        assertEquals(BorrowRecovery.PERFORMED, selection.walk.recovery)
        assertFalse(selection.walk.stalled, "the recovery revived scrolling, so the walk did not stall")
        assertTrue(selection.walk.fullyTraversed, "and the whole list was then seen")
    }

    @Test
    @DisplayName("20. A recovery that does not revive scrolling stalls, and is attempted at most once")
    fun testServiceRecoveryBoundedToOneAttempt() {
        // The recovery reports success (a rebind happened) but the list is still frozen afterward. The walk
        // must NOT keep rebinding forever: it declares a stall, having recovered exactly once.
        val picker =
            FakePicker(
                listOf(screen("[Outfit Two]\nDelta Dawn")),
                frozen = true,
                recoverAvailable = true,
                revivesAfterRecover = false,
            )
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row)
        assertEquals(1, picker.recoverCalls, "the recovery is bounded to a single attempt per walk")
        assertEquals(BorrowRecovery.PERFORMED, selection.walk.recovery, "a rebind was performed")
        assertTrue(selection.walk.stalled, "a recovery that did not revive scrolling still stalls")
        assertFalse(selection.walk.fullyTraversed, "so absence stays unproven")
    }

    @Test
    @DisplayName("21. A healthy scroll never triggers the service recovery")
    fun testServiceRecoveryNotInvokedWhenScrollHealthy() {
        // A list that pages normally to its end must never reach the recovery rung -- recovery is reserved
        // for the proven dead-dispatch failure, not fired on every walk.
        val a = screen("[Outfit One]\nDelta Dawn")
        val b = screen("[Outfit Two]\nEcho Edge")
        val picker = FakePicker(listOf(a, b, a), recoverAvailable = true, revivesAfterRecover = true)
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row)
        assertEquals(0, picker.recoverCalls, "a healthy scroll never invokes the recovery")
        assertEquals(BorrowRecovery.NONE, selection.walk.recovery, "recovery was never needed")
        assertTrue(selection.walk.fullyTraversed)
    }

    /** Seven filler screens then the target, so the target is only reachable one page past where the
     * dispatcher dies. Under the A3-R3 shared counter the forward paging plus the swallowed-retry ladder
     * spend the whole page budget before the rebind gate, so the rebind is skipped and the target is never
     * reached. This is the exact live failure. */
    private fun dispatchDeathBeforeTarget(): FakePicker {
        val fillers = (0..6).map { screen("[Outfit $it]\nFiller number $it") }
        val targetScreen = screen("[Outfit Seven]\n$target")
        return FakePicker(
            fillers + targetScreen,
            dispatchDiesAtIndex = 6,
            recoverAvailable = true,
            revivesAfterRecover = true,
        )
    }

    @Test
    @DisplayName("22. REGRESSION: a mid-list dispatch death that exhausts the page budget is still recovered")
    fun testRecoveryReachableAfterBudgetExhaustion() {
        // Fails against 3f6fdcbe: there the rebind gate `gestures < maxPageGestures` is already false when
        // the ladder exhausts, so recovery is skipped and the post-stall target is never found.
        val picker = dispatchDeathBeforeTarget()
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNotNull(selection.row, "the card past the dispatch death must be reached after the rebind")
        assertEquals(1, picker.recoverCalls, "the rebind is reachable even with the page budget spent, once")
        assertEquals(BorrowRecovery.PERFORMED, selection.walk.recovery)
        assertFalse(selection.walk.stalled, "the revived scroll reached the card, so the walk did not stall")
    }

    @Test
    @DisplayName("23. A mid-list dispatch death with no recovery available stalls with an explicit reason")
    fun testDispatchDeathNoRecoveryStalls() {
        val fillers = (0..6).map { screen("[Outfit $it]\nFiller number $it") }
        val targetScreen = screen("[Outfit Seven]\n$target")
        val picker = FakePicker(fillers + targetScreen, dispatchDiesAtIndex = 6, recoverAvailable = false)
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row, "the target below the dead scroll is unreachable without recovery")
        assertEquals(1, picker.recoverCalls, "recovery was attempted exactly once")
        assertEquals(BorrowRecovery.UNAVAILABLE, selection.walk.recovery, "and its unavailability is explicit")
        assertTrue(selection.walk.stalled)
        assertFalse(selection.walk.fullyTraversed, "absence past a dead scroll is never proven")
    }

    @Test
    @DisplayName("24. A rebind that does not revive dispatch stalls, having rebound exactly once")
    fun testRebindThatDoesNotReviveStalls() {
        // The rebind is reported performed, but dispatch stays dead (revivesAfterRecover = false). The walk
        // must take its one post-rebind retry, see no movement, and stall -- never rebinding a second time.
        val fillers = (0..6).map { screen("[Outfit $it]\nFiller number $it") }
        val targetScreen = screen("[Outfit Seven]\n$target")
        val picker =
            FakePicker(fillers + targetScreen, dispatchDiesAtIndex = 6, recoverAvailable = true, revivesAfterRecover = false)
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row)
        assertEquals(1, picker.recoverCalls, "the rebind is capped at once per walk even as the stall persists")
        assertEquals(BorrowRecovery.PERFORMED, selection.walk.recovery, "a rebind did happen")
        assertTrue(selection.walk.stalled, "but it did not revive dispatch, so the walk still stalls")
    }

    @Test
    @DisplayName("25. Stall handling does not spend the forward budget: an early death still completes")
    fun testForwardBudgetPreservedAcrossRecovery() {
        // Dispatch dies at index 2, is recovered, and the list then pages on to a natural end that needs
        // most of the forward budget. Under a shared counter the ladder retries would have eaten into that
        // budget; with the budgets separate the traversal still finishes.
        val head = (0..5).map { screen("[Outfit $it]\nFiller number $it") }
        val tail = screen("[Outfit 0]\nFiller number 0") // rows identical to head[0]: all-seen -> natural end
        val picker =
            FakePicker(head + tail, dispatchDiesAtIndex = 2, recoverAvailable = true, revivesAfterRecover = true)
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row, "no target in this list")
        assertEquals(1, picker.recoverCalls)
        assertEquals(BorrowRecovery.PERFORMED, selection.walk.recovery)
        assertFalse(selection.walk.stalled, "the revived scroll reached the natural end")
        assertTrue(selection.walk.fullyTraversed, "so the whole list was seen despite the mid-list death")
    }

    @Test
    @DisplayName("26. REGRESSION: the first post-rebind gesture is swallowed, the second one moves the list")
    fun testSecondPostRebindGestureMoves() {
        // Fails against e12a4c55: there only ONE post-rebind gesture is issued, so a swallowed first gesture
        // leaves the list stuck and the card past the death is never reached. This is the A3-R4 live failure.
        val fillers = (0..6).map { screen("[Outfit $it]\nFiller number $it") }
        val targetScreen = screen("[Outfit Seven]\n$target")
        val picker =
            FakePicker(
                fillers + targetScreen,
                dispatchDiesAtIndex = 6,
                recoverAvailable = true,
                revivesAfterRecover = true,
                postRebindSwallow = 1, // the first gesture after the rebind no-ops; the second lands
            )
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNotNull(selection.row, "the second post-rebind gesture reaches the card past the death")
        assertEquals(1, picker.recoverCalls, "still exactly one rebind")
        assertEquals(2, selection.walk.postRebindGestures, "one swallowed attempt plus the one that landed")
        assertFalse(selection.walk.stalled)
    }

    @Test
    @DisplayName("27. REGRESSION: every post-rebind gesture no-ops, so the walk stalls at the cap and cannot rebind again")
    fun testAllPostRebindGesturesNoOpStall() {
        val fillers = (0..6).map { screen("[Outfit $it]\nFiller number $it") }
        val targetScreen = screen("[Outfit Seven]\n$target")
        val picker =
            FakePicker(
                fillers + targetScreen,
                dispatchDiesAtIndex = 6,
                recoverAvailable = true,
                revivesAfterRecover = true,
                postRebindSwallow = 5, // more than the cap: neither post-rebind gesture ever moves
            )
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertNull(selection.row)
        assertEquals(1, picker.recoverCalls, "recovery cannot repeat within a walk")
        assertEquals(2, selection.walk.postRebindGestures, "the post-rebind gesture attempts are capped")
        assertEquals(BorrowRecovery.PERFORMED, selection.walk.recovery)
        assertTrue(selection.walk.stalled)
        assertFalse(selection.walk.fullyTraversed, "absence past an unrecovered death is never proven")
    }

    @Test
    @DisplayName("28. A raised post-rebind cap is honoured, and the count never exceeds it")
    fun testPostRebindGestureCapHonoured() {
        val fillers = (0..6).map { screen("[Outfit $it]\nFiller number $it") }
        val targetScreen = screen("[Outfit Seven]\n$target")
        // Two gestures swallowed after the rebind; the third moves. A cap of 2 stalls, a cap of 3 completes.
        val runWithCap = { cap: Int ->
            val picker =
                FakePicker(fillers + targetScreen, dispatchDiesAtIndex = 6, recoverAvailable = true, revivesAfterRecover = true, postRebindSwallow = 2)
            selectFromBorrowList(picker.walker(maxPostRebindGestures = cap)) { approved(it, target) }
        }
        val capped = runWithCap(2)
        assertNull(capped.row, "two swallowed gestures exhaust a cap of two")
        assertEquals(2, capped.walk.postRebindGestures)
        assertTrue(capped.walk.stalled)
        val raised = runWithCap(3)
        assertNotNull(raised.row, "a cap of three clears two swallowed gestures")
        assertEquals(3, raised.walk.postRebindGestures)
        assertFalse(raised.walk.stalled)
    }

    @Test
    @DisplayName("29. A later distinct stall may use the swallowed-drag ladder but never a second rebind")
    fun testLaterGapDoesNotRebindTwice() {
        // Death at index 2 recovers and the list pages on; a SECOND death at index 4 gets the ordinary ladder
        // (two swallowed-drag retries) but no fresh rebind (one per walk), so it stalls without a second call.
        val screens = (0..6).map { screen("[Outfit $it]\nFiller number $it") }
        val picker =
            FakePicker(screens, dispatchDiesAtIndex = 2, recoverAvailable = true, revivesAfterRecover = true, postRebindSwallow = 0, reDeathAtIndex = 4)
        val selection = selectFromBorrowList(picker.walker()) { approved(it, target) }
        assertEquals(1, picker.recoverCalls, "at most one rebind per walk, whatever later gaps occur")
        assertEquals(4, selection.walk.swallowedRetries, "both gaps ran the ordinary ladder (two retries each)")
        assertEquals(1, selection.walk.postRebindGestures, "the one post-rebind gesture belonged to the first gap only")
        assertTrue(selection.walk.stalled, "the second, un-rebindable death leaves the walk stalled")
    }

    @Test
    @DisplayName("30. The bounded readiness poll returns when ready and times out truthfully")
    fun testReadinessPollBounds() {
        // Ready only on the 3rd check: within a 6-poll budget it succeeds without gesturing early.
        var checks = 0
        var sleeps = 0
        val readyOnThird = {
            checks++
            checks >= 3
        }
        val ready = pollUntil(maxPolls = 6, ready = readyOnThird, sleep = { sleeps++ })
        assertTrue(ready, "readiness that lands within the budget is honoured")
        assertEquals(2, sleeps, "it slept between the two misses, no busy loop")

        // Never ready: the poll must time out (false), so the caller blocks the gesture fail-closed.
        var polls = 0
        var sleepsNever = 0
        val neverReady = {
            polls++
            false
        }
        val timedOut = pollUntil(maxPolls = 4, ready = neverReady, sleep = { sleepsNever++ })
        assertFalse(timedOut, "an unproven readiness never reports ready")
        assertEquals(4, polls, "exactly maxPolls checks, no more")
        assertEquals(3, sleepsNever, "and at most maxPolls minus one sleeps (none after the last check)")
    }

    @Test
    @DisplayName("31. REGRESSION: a stale accessibility instance is not a reconnect; a fresh one is")
    fun testFreshInstanceReconnectSignal() {
        val stale = Any()
        // The A3-R5 predicate was `getInstance() != null`, which the stale (never-nulled) 2.5.9 singleton
        // satisfies immediately -- vacuous. freshInstanceObserved must reject exactly that case.
        assertNotNull(stale, "the stale instance is non-null, so the old != null gate would have passed it")
        assertFalse(freshInstanceObserved(stale) { stale }, "the same stale instance is not a reconnect")
        val fresh = Any()
        assertTrue(freshInstanceObserved(stale) { fresh }, "a distinct instance is a genuine reconnect")
        assertFalse(freshInstanceObserved(stale) { null }, "a null current is never a reconnect")
    }

    @Test
    @DisplayName("32. REGRESSION: getInstance throwing during reconnect is treated as not-ready, never unwinds")
    fun testReconnectExceptionIsNotReady() {
        val stale = Any()
        assertFalse(
            freshInstanceObserved(stale) { throw IllegalStateException("Accessibility Service not initialized.") },
            "an IllegalStateException during the check is swallowed as not-ready",
        )
        // Transient throws then a fresh instance: recovery may continue once the fresh instance appears.
        val fresh = Any()
        var attempt = 0
        val recovered =
            pollUntil(
                maxPolls = 6,
                ready = {
                    freshInstanceObserved(stale) {
                        attempt++
                        if (attempt < 3) throw IllegalStateException("still reconnecting")
                        fresh
                    }
                },
                sleep = {},
            )
        assertTrue(recovered, "a fresh instance after transient exceptions lets recovery continue")
        // A persistent throw times out with no reconnect, so the caller fires no gesture.
        val timedOut =
            pollUntil(
                maxPolls = 4,
                ready = { freshInstanceObserved(stale) { throw IllegalStateException("service is stopping") } },
                sleep = {},
            )
        assertFalse(timedOut, "a persistent exception times out, blocking any post-rebind gesture")
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
