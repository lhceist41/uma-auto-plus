package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The Borrow host-recovery movement signal must be the same ordered row-identity
 * signature [BorrowListWalker] already trusts ([borrowScreenKeys] / [borrowScreenSignature]),
 * not a positional pixel digest. The Borrow list is known to shift row pixel positions by a few
 * pixels between captures, so a pixel-based fingerprint reports movement that never happened and
 * the generic settle verifier can never observe two stable identical samples -- the exact live
 * failure (three natural tail stalls, one deterministic mid-list stall, all EXECUTED/UNCERTAIN).
 *
 * A real [android.graphics.Bitmap] cannot be constructed in this JVM test module, so these tests
 * isolate the semantic identity function -- the same one the production
 * `borrowHostSwipeFingerprint` calls -- and prove it through the real generic verifier functions
 * ([executeHostSwipeWithSettleVerification], [verifySwipeMovement]).
 */
@DisplayName("Borrow host swipe uses semantic row-identity movement evidence")
class BorrowHostSemanticFingerprintTest {
    /** Mirrors the production `borrowHostSwipeFingerprint`: recognized requires at least two
     * readable row identities, and the value is the ordered semantic screen signature. */
    private fun semanticFingerprint(scan: BorrowScan): ScreenFingerprint =
        ScreenFingerprint(
            recognized = borrowScreenKeys(scan).size >= 2,
            value = borrowScreenSignature(scan),
        )

    /** A screen of borrowable rows at the given pixel Y positions, standing in for successive
     * captures of the same or a different list state. */
    private fun scanAt(vararg rows: Pair<Double, String>): BorrowScan = BorrowScan(rows.toList())

    @Test
    @DisplayName("1. Two or more readable rows are recognized")
    fun testTwoRowsRecognized() {
        val scan = scanAt(440.0 to "[Outfit One]\nAlpha Ace", 702.0 to "[Outfit Two]\nBravo Bell")
        assertTrue(semanticFingerprint(scan).recognized, "two readable row identities is enough to trust the screen")
    }

    @Test
    @DisplayName("2. Fewer than two readable rows is not recognized")
    fun testFewerThanTwoRowsRejected() {
        assertFalse(semanticFingerprint(scanAt()).recognized, "an empty screen carries no identity")
        assertFalse(semanticFingerprint(scanAt(440.0 to "[Outfit One]\nAlpha Ace")).recognized, "one row alone is not enough")
        // Blocked rows count toward the identity total exactly as they do for the walker's own freshness rule.
        val oneRowOneBlocked = BorrowScan(listOf(440.0 to "[Outfit One]\nAlpha Ace"), duplicateTexts = listOf("[Outfit Two]\nBravo Bell"))
        assertTrue(semanticFingerprint(oneRowOneBlocked).recognized, "a borrowable row plus a tagged row together clear the threshold")
    }

    @Test
    @DisplayName("3. Pixel-jittered captures of the same rows produce the same signature")
    fun testPixelJitterDoesNotChangeSignature() {
        // Three captures of an identical, unmoved screen -- the pixel representation the real bitmap
        // would carry differs capture to capture (the defect this repair targets), but the row
        // identities and their order do not.
        val captureA = scanAt(440.0 to "[Outfit One]\nAlpha Ace", 702.0 to "[Outfit Two]\nBravo Bell")
        val captureB = scanAt(441.3 to "[Outfit One]\nAlpha Ace", 703.7 to "[Outfit Two]\nBravo Bell")
        val captureC = scanAt(438.6 to "[Outfit One]\nAlpha Ace", 700.1 to "[Outfit Two]\nBravo Bell")

        val fpA = semanticFingerprint(captureA)
        val fpB = semanticFingerprint(captureB)
        val fpC = semanticFingerprint(captureC)

        assertEquals(fpA.value, fpB.value, "row-coordinate jitter between captures must not change the signature")
        assertEquals(fpB.value, fpC.value, "nor may a third, differently-jittered capture")
        assertTrue(fpA.recognized && fpB.recognized && fpC.recognized)
    }

    @Test
    @DisplayName("4. Changed row identities produce a changed signature")
    fun testChangedRowsChangeSignature() {
        val before = scanAt(440.0 to "[Outfit One]\nAlpha Ace", 702.0 to "[Outfit Two]\nBravo Bell")
        val after = scanAt(440.0 to "[Outfit Three]\nCharlie Chase", 702.0 to "[Outfit Four]\nDelta Dawn")
        assertNotEquals(semanticFingerprint(before).value, semanticFingerprint(after).value, "a genuinely different screen must change the signature")
    }

    @Test
    @DisplayName("5. A stable pixel-jittered same-screen pair settles as NO_EFFECT through the real verifier")
    fun testStableJitteredSameScreenSettlesNoEffect() {
        val before = semanticFingerprint(scanAt(440.0 to "[Outfit One]\nAlpha Ace", 702.0 to "[Outfit Two]\nBravo Bell"))
        // Two "after" samples of the SAME rows at different jittered pixel positions -- under the old
        // pixel fingerprint these would never compare equal; under the semantic signature they do.
        val afterSample1 = semanticFingerprint(scanAt(441.1 to "[Outfit One]\nAlpha Ace", 703.4 to "[Outfit Two]\nBravo Bell"))
        val afterSample2 = semanticFingerprint(scanAt(439.8 to "[Outfit One]\nAlpha Ace", 700.9 to "[Outfit Two]\nBravo Bell"))
        assertEquals(afterSample1.value, afterSample2.value, "precondition: the two jittered after-samples must carry the same semantic value")

        val samples = listOf(afterSample1, afterSample2).iterator()
        val settled =
            executeHostSwipeWithSettleVerification(
                before = before,
                executeSwipe = { InputExecutionResult(InputExecutionStatus.EXECUTED, foreground = true, detailCode = "ADB_EXIT_0") },
                shouldStop = { false },
                waitBeforeSample = {},
                captureAfter = { samples.next() },
            )
        assertEquals(SwipeMovement.NO_EFFECT, settled.movement, "an unmoved list must settle to NO_EFFECT, not exhaust to UNCERTAIN on pixel jitter alone")
        assertEquals(2, settled.samplesTaken)
    }

    @Test
    @DisplayName("6. A stable pixel-jittered different-screen pair settles as MOVED through the real verifier")
    fun testStableJitteredDifferentScreenSettlesMoved() {
        val before = semanticFingerprint(scanAt(440.0 to "[Outfit One]\nAlpha Ace", 702.0 to "[Outfit Two]\nBravo Bell"))
        val afterSample1 = semanticFingerprint(scanAt(441.1 to "[Outfit Three]\nCharlie Chase", 703.4 to "[Outfit Four]\nDelta Dawn"))
        val afterSample2 = semanticFingerprint(scanAt(439.8 to "[Outfit Three]\nCharlie Chase", 700.9 to "[Outfit Four]\nDelta Dawn"))
        assertEquals(afterSample1.value, afterSample2.value, "precondition: the two jittered after-samples of the new screen must agree")

        val samples = listOf(afterSample1, afterSample2).iterator()
        val settled =
            executeHostSwipeWithSettleVerification(
                before = before,
                executeSwipe = { InputExecutionResult(InputExecutionStatus.EXECUTED, foreground = true, detailCode = "ADB_EXIT_0") },
                shouldStop = { false },
                waitBeforeSample = {},
                captureAfter = { samples.next() },
            )
        assertEquals(SwipeMovement.MOVED, settled.movement, "a genuinely advanced list must settle to MOVED")
    }

    @Test
    @DisplayName("7. ADB_EXIT_0 / EXECUTED transport status alone never yields MOVED")
    fun testExecutedAloneNeverYieldsMoved() {
        val before = semanticFingerprint(scanAt(440.0 to "[Outfit One]\nAlpha Ace", 702.0 to "[Outfit Two]\nBravo Bell"))
        val sameAfter = semanticFingerprint(scanAt(441.1 to "[Outfit One]\nAlpha Ace", 703.4 to "[Outfit Two]\nBravo Bell"))
        val movement = verifySwipeMovement(before, InputExecutionResult(InputExecutionStatus.EXECUTED, foreground = true, detailCode = "ADB_EXIT_0"), sameAfter)
        assertEquals(SwipeMovement.NO_EFFECT, movement, "EXECUTED plus an unchanged semantic value is NO_EFFECT, never MOVED")
    }

    @Test
    @DisplayName("8. An unrecognized before-screen fails before any swipe is trusted")
    fun testUnrecognizedBeforeIsNeverTrusted() {
        val unrecognizedBefore = semanticFingerprint(scanAt(440.0 to "[Outfit One]\nAlpha Ace"))
        assertFalse(unrecognizedBefore.recognized, "precondition: a single-row screen is not recognized")
        val after = semanticFingerprint(scanAt(440.0 to "[Outfit One]\nAlpha Ace", 702.0 to "[Outfit Two]\nBravo Bell"))
        val movement = verifySwipeMovement(unrecognizedBefore, InputExecutionResult(InputExecutionStatus.EXECUTED, foreground = true, detailCode = "ADB_EXIT_0"), after)
        assertEquals(SwipeMovement.UNCERTAIN, movement, "an unrecognized before-fingerprint can never produce MOVED")
    }

    @Test
    @DisplayName("9. Semantic values that keep changing never reach a stable recognized pair: UNCERTAIN")
    fun testNeverStableSemanticValuesStayUncertain() {
        val before = semanticFingerprint(scanAt(440.0 to "[Outfit One]\nAlpha Ace", 702.0 to "[Outfit Two]\nBravo Bell"))
        // Every sample is a distinct, fully recognized semantic screen -- real content, never repeating.
        val samples =
            (0 until HOST_SWIPE_SETTLE_MAX_SAMPLES)
                .map { i -> semanticFingerprint(scanAt(440.0 to "[Row $i]\nCandidate $i", 702.0 to "[Row ${i + 100}]\nCandidate ${i + 100}")) }
                .iterator()
        val settled =
            executeHostSwipeWithSettleVerification(
                before = before,
                executeSwipe = { InputExecutionResult(InputExecutionStatus.EXECUTED, foreground = true, detailCode = "ADB_EXIT_0") },
                shouldStop = { false },
                waitBeforeSample = {},
                captureAfter = { samples.next() },
            )
        assertEquals(SwipeMovement.UNCERTAIN, settled.movement, "a list that never settles must stay fail-closed, not guess MOVED or NO_EFFECT")
        assertEquals(HOST_SWIPE_SETTLE_MAX_SAMPLES, settled.samplesTaken)
    }

    @Test
    @DisplayName("10. A stale/repeated jittered same frame cannot false-positive MOVED")
    fun testStaleSameFrameCannotFalsePositiveMoved() {
        val before = semanticFingerprint(scanAt(440.0 to "[Outfit One]\nAlpha Ace", 702.0 to "[Outfit Two]\nBravo Bell"))
        // Same rows, jittered pixels, repeated across several polls before the pair that settles --
        // models a slow settle where the capture cadence catches the same unmoved frame more than once.
        val same1 = semanticFingerprint(scanAt(440.9 to "[Outfit One]\nAlpha Ace", 702.6 to "[Outfit Two]\nBravo Bell"))
        val same2 = semanticFingerprint(scanAt(439.4 to "[Outfit One]\nAlpha Ace", 701.2 to "[Outfit Two]\nBravo Bell"))
        val samples = listOf(same1, same2).iterator()
        val settled =
            executeHostSwipeWithSettleVerification(
                before = before,
                executeSwipe = { InputExecutionResult(InputExecutionStatus.EXECUTED, foreground = true, detailCode = "ADB_EXIT_0") },
                shouldStop = { false },
                waitBeforeSample = {},
                captureAfter = { samples.next() },
            )
        assertEquals(SwipeMovement.NO_EFFECT, settled.movement, "a repeated unmoved frame must never be read as movement")
    }

    @Test
    @DisplayName("PIXEL-JITTER REGRESSION: three differently-jittered captures of the same rows collapse to one signature")
    fun testPixelJitterRegressionThreeCapturesCollapse() {
        // capture 1 / 2 / 3: same semantic rows, three distinct pixel representations (A/B/C).
        val capture1 = scanAt(440.0 to "[Outfit One]\nAlpha Ace", 702.0 to "[Outfit Two]\nBravo Bell", 964.0 to "[Outfit Three]\nCharlie Chase")
        val capture2 = scanAt(442.0 to "[Outfit One]\nAlpha Ace", 699.0 to "[Outfit Two]\nBravo Bell", 967.0 to "[Outfit Three]\nCharlie Chase")
        val capture3 = scanAt(437.0 to "[Outfit One]\nAlpha Ace", 705.0 to "[Outfit Two]\nBravo Bell", 961.0 to "[Outfit Three]\nCharlie Chase")

        val values = listOf(capture1, capture2, capture3).map { semanticFingerprint(it) }
        assertTrue(values.all { it.recognized })
        assertEquals(1, values.map { it.value }.toSet().size, "all three pixel-distinct captures of the same rows must share one semantic value")

        // Under the OLD pixel-fingerprint behavior (a per-pixel digest over the list body) these three
        // captures would each hash differently, so no two consecutive settle samples could ever compare
        // equal -- the exact EXECUTED/UNCERTAIN live failure. The semantic signature must not reproduce that.
    }

    @Test
    @DisplayName("MOVED REGRESSION: distinct before/after rows settle to MOVED through the real settle verifier")
    fun testMovedRegressionThroughRealVerifier() {
        val before = semanticFingerprint(scanAt(440.0 to "[Outfit One]\nAlpha", 702.0 to "[Outfit Two]\nBravo", 964.0 to "[Outfit Three]\nCharlie"))
        val after1 = semanticFingerprint(scanAt(441.0 to "[Outfit Four]\nDelta", 703.0 to "[Outfit Five]\nEcho", 965.0 to "[Outfit Six]\nFoxtrot"))
        val after2 = semanticFingerprint(scanAt(438.0 to "[Outfit Four]\nDelta", 700.0 to "[Outfit Five]\nEcho", 962.0 to "[Outfit Six]\nFoxtrot"))
        val samples = listOf(after1, after2).iterator()
        val settled =
            executeHostSwipeWithSettleVerification(
                before = before,
                executeSwipe = { InputExecutionResult(InputExecutionStatus.EXECUTED, foreground = true, detailCode = "ADB_EXIT_0") },
                shouldStop = { false },
                waitBeforeSample = {},
                captureAfter = { samples.next() },
            )
        assertEquals(SwipeMovement.MOVED, settled.movement)
        assertEquals(2, settled.samplesTaken)
    }

    @Test
    @DisplayName("NO-EFFECT REGRESSION: a genuinely unmoved Borrow screen settles to NO_EFFECT, not UNCERTAIN")
    fun testNoEffectRegressionDoesNotExhaustToUncertain() {
        // This is the load-bearing case: every prior live tail attempt returned UNCERTAIN because the
        // old pixel digest never repeated between captures, even when the list was genuinely at rest.
        val before = semanticFingerprint(scanAt(440.0 to "[Outfit One]\nAlpha Ace", 702.0 to "[Outfit Two]\nBravo Bell"))
        val after1 = semanticFingerprint(scanAt(440.4 to "[Outfit One]\nAlpha Ace", 702.9 to "[Outfit Two]\nBravo Bell"))
        val after2 = semanticFingerprint(scanAt(439.6 to "[Outfit One]\nAlpha Ace", 701.3 to "[Outfit Two]\nBravo Bell"))
        val samples = listOf(after1, after2).iterator()
        val settled =
            executeHostSwipeWithSettleVerification(
                before = before,
                executeSwipe = { InputExecutionResult(InputExecutionStatus.EXECUTED, foreground = true, detailCode = "ADB_EXIT_0") },
                shouldStop = { false },
                waitBeforeSample = {},
                captureAfter = { samples.next() },
            )
        assertEquals(SwipeMovement.NO_EFFECT, settled.movement, "an at-rest list must settle, not exhaust the settle window")
        assertTrue(settled.samplesTaken < HOST_SWIPE_SETTLE_MAX_SAMPLES, "settling in 2 samples, well inside the unchanged 8-sample / 3.75s budget")
    }
}
