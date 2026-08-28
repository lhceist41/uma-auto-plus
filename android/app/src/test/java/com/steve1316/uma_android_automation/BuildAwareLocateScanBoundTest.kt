package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Locate-scan-ceiling regression: the build-aware locate stage must be able to walk a long, healthy Borrow pool past
 * the shared 8-page budget, and the known-limit-break selection-evidence shortcut must never let a real
 * ambiguity slip through.
 *
 * The live regression this pins: a BUILD_AWARE Matikanefukukitaru intent with an UNKNOWN expected limit
 * break hit ~31 observed rows on a healthy (non-stalled) scan, with MAX_BORROW_SCAN_PAGES (8) exhausted
 * before the natural end -- so the locate incorrectly reported an incomplete traversal as a "stall" and
 * blocked a launch that a longer walk could have resolved cleanly. These tests drive the real
 * [BorrowListWalker], [SmartBorrowLocator], [computeSelectionEvidenceComplete], and [canSelectLocatedBorrow]
 * together over synthetic long pools, entirely offline.
 */
@DisplayName("Build-aware long-pool locate")
class BuildAwareLocateScanBoundTest {
    /** Mirrors the production MAX_BORROW_SCAN_PAGES (private in CareerLaunchNavigator; the source-guard
     * tests in BuildAwareLaunchProductionTest pin the actual constant and its wiring). */
    private val OLD_BUDGET = 8

    /** Mirrors the production MAX_BORROW_LOCATE_SCAN_PAGES = MAX_BORROW_SCAN_PAGES * 5. */
    private val NEW_BUDGET = OLD_BUDGET * 5

    private fun intent(expectedLimitBreak: Int?) =
        SmartBorrowIntent(
            schema = SMART_BORROW_INTENT_SCHEMA,
            schemaVersion = 2,
            targetProfile = "Medium",
            sourceBorrowScanId = "bp-live",
            supportCardId = 40001,
            canonicalCharacter = "Matikanefukukitaru",
            canonicalTitle = "Test Title",
            displayName = "Matikanefukukitaru [Test Title]",
            rarity = "SSR",
            expectedLevel = 50,
            expectedLimitBreak = expectedLimitBreak,
            sourceAlias = null,
            resolutionPath = "EXACT_TITLE",
            recommendationEvidenceDigest = "djb2-a3r12",
            recommendationSource = IntentRecommendationSource.BUILD_AWARE,
        )

    /** Every screen carries a filler companion row alongside its primary row, so the "natural tail" trick
     * below (a screen containing only the primary row) is always a genuine, shorter, already-seen subset
     * of the last content screen rather than an accidental exact duplicate. */
    private fun companionRow(pageIndex: Int): LocatableBorrowRow =
        LocatableBorrowRow(pageIndex, "Filler Companion $pageIndex", "Filler Title", null, 30, "owner-companion-$pageIndex", false, "high")

    private fun fillerScreen(pageIndex: Int): List<LocatableBorrowRow> =
        listOf(LocatableBorrowRow(pageIndex, "Filler Character $pageIndex", "Filler Title", null, 30, "owner-filler-$pageIndex", false, "high"), companionRow(pageIndex))

    private fun targetScreen(pageIndex: Int, limitBreakIndex: Int?): List<LocatableBorrowRow> =
        listOf(LocatableBorrowRow(pageIndex, "Matikanefukukitaru", "Test Title", limitBreakIndex, 50, "owner-target-$pageIndex", false, "high"), companionRow(pageIndex))

    private fun keyFor(row: LocatableBorrowRow): String = "${row.character}|${row.outfit}|${row.limitBreakIndex}|${row.ownerAlias}"

    /**
     * Walks [pages] (one entry per screen) with the real [BorrowListWalker] and resolves the accumulated
     * rows with the real [SmartBorrowLocator], exactly as [locateSmartBorrowIntentReadOnly] does over a
     * live picker -- collect every observed row across the bounded walk, then locate ONCE at the end.
     * [pages] never repeats its own last entry as a filler; a caller wanting a clean natural end must
     * append a final "tail" screen ([withNaturalTail]) that is a strict, already-seen subset of the prior
     * screen, which is what actually drives [BorrowWalkEnd.END_OF_LIST] without stalling. Omitting a tail
     * models the list's true bottom repeating identically, which the walker (correctly) treats as a stall.
     */
    private fun locate(intentUsed: SmartBorrowIntent, pages: List<List<LocatableBorrowRow>>, maxPageGestures: Int): Pair<SmartBorrowLocateMatch, BorrowWalkResult> {
        val observed = LinkedHashMap<String, LocatableBorrowRow>()
        var index = 0
        val walker =
            BorrowListWalker(
                maxPageGestures = maxPageGestures,
                maxSwallowedRetries = 2,
                readScreen = { BorrowScan(pages[index].map { 0.0 to keyFor(it) }) },
                advancePage = { if (index < pages.lastIndex) index++ },
            )
        val walk =
            walker.walk { _, _ ->
                for (row in pages[index]) observed.putIfAbsent(keyFor(row), row)
                false
            }
        val match = SmartBorrowLocator.locate(intentUsed, observed.values.toList())
        return match to walk
    }

    /** [contentScreens] unique screens (index 0 until size), then one extra tail screen carrying only the
     * FIRST (primary) row of the last content screen -- already seen, shorter than the 2-row content
     * screen it copies from, so it contributes nothing new and drives a clean natural END_OF_LIST rather
     * than a stall. */
    private fun withNaturalTail(contentScreens: List<List<LocatableBorrowRow>>): List<List<LocatableBorrowRow>> =
        contentScreens + listOf(listOf(contentScreens.last().first()))

    @Nested
    @DisplayName("known expected limit break (locate-scan-ceiling selection-evidence shortcut)")
    inner class KnownLimitBreak {
        @Test
        fun `1 - LOCATED with a known LB on a healthy MAX_PAGES cutoff authorises selection`() {
            val pages = (0 until 15).map { i -> if (i == 2) targetScreen(i, limitBreakIndex = 4) else fillerScreen(i) }
            val (match, walk) = locate(intent(expectedLimitBreak = 4), pages, maxPageGestures = OLD_BUDGET)
            assertEquals(SmartBorrowLocateVerdict.LOCATED, match.verdict)
            assertEquals(BorrowWalkEnd.MAX_PAGES, walk.end)
            assertFalse(walk.stalled)
            assertFalse(walk.fullyTraversed, "the pool genuinely was not fully walked")
            val evidenceComplete = computeSelectionEvidenceComplete(intent(expectedLimitBreak = 4), match, walk.stalled)
            assertTrue(evidenceComplete)
            assertTrue(canSelectLocatedBorrow(SmartBorrowLocateResult.Status.LOCATED, walk.fullyTraversed, evidenceComplete))
        }

        @Test
        fun `2 - LOCATED with a known LB on a TRUE stall does NOT authorise selection`() {
            // Three screens, no natural tail: the walker exhausts its retry-rebind-host ladder against the
            // frozen bottom and declares a genuine stall.
            val pages = listOf(fillerScreen(0), targetScreen(1, limitBreakIndex = 4), fillerScreen(2))
            val (match, walk) = locate(intent(expectedLimitBreak = 4), pages, maxPageGestures = NEW_BUDGET)
            assertEquals(SmartBorrowLocateVerdict.LOCATED, match.verdict)
            assertTrue(walk.stalled, "this fixture must genuinely stall, not merely run out of budget")
            val evidenceComplete = computeSelectionEvidenceComplete(intent(expectedLimitBreak = 4), match, walk.stalled)
            assertFalse(evidenceComplete, "a true stall must block a known-LB target too")
            assertFalse(canSelectLocatedBorrow(SmartBorrowLocateResult.Status.LOCATED, walk.fullyTraversed, evidenceComplete))
        }
    }

    @Nested
    @DisplayName("unknown expected limit break (full traversal still required)")
    inner class UnknownLimitBreak {
        @Test
        fun `3 - LOCATED with an unknown LB on a healthy MAX_PAGES cutoff does NOT authorise selection`() {
            val pages = (0 until 15).map { i -> if (i == 2) targetScreen(i, limitBreakIndex = 4) else fillerScreen(i) }
            val (match, walk) = locate(intent(expectedLimitBreak = null), pages, maxPageGestures = OLD_BUDGET)
            assertEquals(SmartBorrowLocateVerdict.LOCATED, match.verdict)
            assertFalse(walk.stalled)
            assertFalse(walk.fullyTraversed)
            val evidenceComplete = computeSelectionEvidenceComplete(intent(expectedLimitBreak = null), match, walk.stalled)
            assertFalse(evidenceComplete, "an unknown LB gets no shortcut: a later conflicting LB could still appear")
            assertFalse(canSelectLocatedBorrow(SmartBorrowLocateResult.Status.LOCATED, walk.fullyTraversed, evidenceComplete))
        }

        @Test
        fun `4 - a conflicting known LB beyond the old budget flips LOCATED to AMBIGUOUS under the extended bound`() {
            val content =
                (0 until 25).map { idx ->
                    when (idx) {
                        3 -> targetScreen(idx, limitBreakIndex = 4)
                        20 -> targetScreen(idx, limitBreakIndex = 2) // same identity, conflicting KNOWN limit break
                        else -> fillerScreen(idx)
                    }
                }
            val pages = withNaturalTail(content)
            val i = intent(expectedLimitBreak = null)

            val (oldMatch, oldWalk) = locate(i, pages, maxPageGestures = OLD_BUDGET)
            assertEquals(SmartBorrowLocateVerdict.LOCATED, oldMatch.verdict, "the old budget never reaches the conflict, so it reports a premature LOCATED")
            assertFalse(oldWalk.fullyTraversed)

            val (newMatch, newWalk) = locate(i, pages, maxPageGestures = NEW_BUDGET)
            assertEquals(SmartBorrowLocateVerdict.AMBIGUOUS, newMatch.verdict, "the extended bound reaches the conflicting row and correctly blocks as ambiguous")
            assertFalse(newWalk.stalled)
            val evidenceComplete = computeSelectionEvidenceComplete(i, newMatch, newWalk.stalled)
            assertFalse(evidenceComplete)
            assertFalse(canSelectLocatedBorrow(SmartBorrowLocateResult.Status.AMBIGUOUS, newWalk.fullyTraversed, evidenceComplete))
        }

        @Test
        fun `5 - no conflicting LB anywhere - the extended bound reaches a natural end and authorises selection`() {
            val content = (0 until 25).map { i -> if (i == 15) targetScreen(i, limitBreakIndex = null) else fillerScreen(i) }
            val pages = withNaturalTail(content)
            val i = intent(expectedLimitBreak = null)

            val (oldMatch, _) = locate(i, pages, maxPageGestures = OLD_BUDGET)
            assertEquals(SmartBorrowLocateVerdict.NOT_FOUND, oldMatch.verdict, "the old budget cuts off before the target's page")

            val (newMatch, newWalk) = locate(i, pages, maxPageGestures = NEW_BUDGET)
            assertEquals(SmartBorrowLocateVerdict.LOCATED, newMatch.verdict)
            assertEquals(BorrowWalkEnd.END_OF_LIST, newWalk.end)
            assertFalse(newWalk.stalled)
            assertTrue(newWalk.fullyTraversed, "the natural end was truly reached, not merely cut off")
            assertTrue(canSelectLocatedBorrow(SmartBorrowLocateResult.Status.LOCATED, newWalk.fullyTraversed, computeSelectionEvidenceComplete(i, newMatch, newWalk.stalled)))
        }
    }

    @Nested
    @DisplayName("reachability beyond the old 8-page ceiling")
    inner class Reachability {
        @Test
        fun `6 and 7 - a target past the old ceiling is found by the extended bound, never falsely reported absent`() {
            val pages = (0 until 25).map { i -> if (i == 20) targetScreen(i, limitBreakIndex = 4) else fillerScreen(i) }
            val i = intent(expectedLimitBreak = 4)

            val (oldMatch, _) = locate(i, pages, maxPageGestures = OLD_BUDGET)
            assertEquals(SmartBorrowLocateVerdict.NOT_FOUND, oldMatch.verdict, "the old 8-page budget cannot reach page 20")

            val (newMatch, _) = locate(i, pages, maxPageGestures = NEW_BUDGET)
            assertEquals(SmartBorrowLocateVerdict.LOCATED, newMatch.verdict, "the extended bound reaches it and reports it found, never a false absence")
        }
    }

    @Nested
    @DisplayName("short pools are unaffected")
    inner class ShortPools {
        @Test
        fun `8 - a short pool reaches the identical natural-end result under either bound`() {
            val content = (0 until 5).map { i -> if (i == 2) targetScreen(i, limitBreakIndex = 4) else fillerScreen(i) }
            val pages = withNaturalTail(content)
            val i = intent(expectedLimitBreak = 4)

            val (oldMatch, oldWalk) = locate(i, pages, maxPageGestures = OLD_BUDGET)
            val (newMatch, newWalk) = locate(i, pages, maxPageGestures = NEW_BUDGET)

            assertEquals(SmartBorrowLocateVerdict.LOCATED, oldMatch.verdict)
            assertEquals(oldMatch.verdict, newMatch.verdict)
            assertTrue(oldWalk.fullyTraversed)
            assertEquals(oldWalk.fullyTraversed, newWalk.fullyTraversed, "a short pool pays no extra time under the wider ceiling")
        }
    }
}
