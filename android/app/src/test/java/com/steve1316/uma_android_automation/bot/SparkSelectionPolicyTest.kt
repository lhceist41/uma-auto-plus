package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The Spark Selection pager's terminal decision, and the scan-retry bounds around it.
 *
 * Background, twice live: a scrolled spark list is stitched frame by frame with an exact-match
 * overlap rule, and the post-swipe capture lands while the list is still rubber-banding off its
 * bottom bumper. One damaged-but-readable row is enough for the stitch to find no overlap, and
 * the read comes back ALIGNMENT_FAILED. On 2026-07-22 that ended a queue at 4 of 8; on
 * 2026-08-04 BOTH pager pages read short and the queue ended at 3 of 11, even though the pager
 * was standing on a heading-and-dots-verified ORIGINAL page showing exactly the set the career
 * had already read completely.
 *
 * The invariant these tests pin: an incomplete EVALUATION degrades to keeping ORIGINAL, because
 * that is the set the career earned and the 30 TP is spent either way. Only page IDENTITY or a
 * missing Confirm control is fatal, because those are what could commit the wrong set.
 */
@DisplayName("Spark selection fallback policy")
class SparkSelectionPolicyTest {
    private fun stat(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.STAT)

    private fun pink(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.APTITUDE)

    private fun unique(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.UNIQUE)

    private fun white(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.WHITE, SparkWhiteClass.SKILL)

    /**
     * The exact Original set from the 2026-08-04 El Condor Pasa career, as the corpus recorded it
     * from the COMPLETE pre-pager scan and as the failure screenshot shows it on the pager.
     */
    private val elCondorOriginal =
        listOf(
            stat("Power", 1),
            pink("Pace Chaser", 2),
            unique("Victoria por plancha t", 2),
            white("Arima Kinen", 1),
            white("Homestretch Haste", 2),
            white("Prepared to Pass", 2),
            white("Mile Straightaways O", 1),
            white("Glittering Star", 1),
            white("TS Climax Scenario", 1),
        )

    private fun reading(rows: List<SparkRowFact>, termination: SparkScanTermination, scrolls: Int = 0) = SparkSetReading(rows, termination, scrolls)

    private fun breakdownOf(rows: List<SparkRowFact>) =
        SparkKeepPolicy
            .choose(
                reading(rows, SparkScanTermination.COMPLETE_END_MARKER),
                reading(rows, SparkScanTermination.COMPLETE_END_MARKER),
                profile,
            ).original

    private val profile =
        SparkChooserProfile(
            traineeIdentity = "El Condor Pasa",
            objective = "sparks",
            blueTargetsOrdered = listOf("Power", "Speed"),
            preferredDistance = "Mile",
            preferredStyle = "Pace Chaser",
            preferredSurface = "Turf",
            plannedSkillNames = emptyList(),
        )

    /** A degraded (uncertain) choice, exactly as [SparkKeepPolicy.choose] returns one. */
    private fun uncertainChoice(): SparkChoice {
        val bd = breakdownOf(elCondorOriginal)
        return SparkChoice(SparkSetSide.ORIGINAL, "incomplete_read", "partial", certain = false, original = bd, rerolled = bd)
    }

    private fun certainChoice(side: SparkSetSide): SparkChoice {
        val bd = breakdownOf(elCondorOriginal)
        return SparkChoice(side, "total", "compared", certain = true, original = bd, rerolled = bd)
    }

    private fun inputs(
        original: SparkScanTermination,
        rerolled: SparkScanTermination,
        currentPage: SparkSetSide? = SparkSetSide.ORIGINAL,
        navVerified: Boolean = true,
        controlAvailable: Boolean = true,
        rescanAvailable: Boolean = false,
        prefix: SparkPrefixEvidence? = null,
    ) = SparkSelectionInputs(original, rerolled, currentPage, navVerified, controlAvailable, rescanAvailable, prefix)

    // ------------------------------------------------------------------
    // A complete comparison is untouched by any of this.
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("complete comparison is unchanged")
    inner class CompleteComparison {
        @Test
        fun `original complete and rerolled complete with original winning commits original`() {
            val d =
                SparkSelectionPolicy.decide(
                    certainChoice(SparkSetSide.ORIGINAL),
                    inputs(SparkScanTermination.COMPLETE_END_MARKER, SparkScanTermination.COMPLETE_END_MARKER),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action)
            assertEquals(SparkSetSide.ORIGINAL, d.side)
            assertTrue(d.certain)
            assertEquals("total", d.decidedBy, "a real comparison keeps the policy's own decidedBy")
        }

        @Test
        fun `original complete and rerolled complete with rerolled winning commits rerolled`() {
            val d =
                SparkSelectionPolicy.decide(
                    certainChoice(SparkSetSide.REROLLED),
                    inputs(SparkScanTermination.COMPLETE_NO_PROGRESS, SparkScanTermination.COMPLETE_END_MARKER),
                )
            assertEquals(SparkSelectionAction.CHOOSE_REROLLED, d.action)
            assertEquals(SparkSetSide.REROLLED, d.side)
            assertTrue(d.certain)
        }

        @Test
        fun `a certain comparison is never diverted by identity or control inputs`() {
            // The certain path predates this fallback and must stay byte-for-byte in behavior.
            val d =
                SparkSelectionPolicy.decide(
                    certainChoice(SparkSetSide.REROLLED),
                    inputs(
                        SparkScanTermination.COMPLETE_END_MARKER,
                        SparkScanTermination.COMPLETE_END_MARKER,
                        currentPage = null,
                        navVerified = false,
                        controlAvailable = false,
                        rescanAvailable = true,
                    ),
                )
            assertEquals(SparkSelectionAction.CHOOSE_REROLLED, d.action)
            assertTrue(d.certain)
        }
    }

    // ------------------------------------------------------------------
    // Degraded evaluation with trusted identity: keep Original, continue.
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("degraded evaluation keeps Original")
    inner class Degraded {
        @Test
        fun `original complete and rerolled alignment failed keeps original`() {
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(SparkScanTermination.COMPLETE_END_MARKER, SparkScanTermination.ALIGNMENT_FAILED),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action)
            assertFalse(d.certain)
            assertEquals("incomplete_read", d.decidedBy)
        }

        @Test
        fun `original alignment failed and rerolled complete keeps original`() {
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(SparkScanTermination.ALIGNMENT_FAILED, SparkScanTermination.COMPLETE_END_MARKER),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action)
            assertFalse(d.certain)
        }

        @Test
        fun `both alignment failed with a verified ORIGINAL page keeps original and continues`() {
            // The 2026-08-04 halt, exactly.
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(SparkScanTermination.ALIGNMENT_FAILED, SparkScanTermination.ALIGNMENT_FAILED),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action)
            assertEquals(SparkSetSide.ORIGINAL, d.side)
            assertFalse(d.certain)
            assertTrue(d.reason.contains("Original chosen without reroll comparison"))
            assertFalse(d.reason.contains("no choice is safe"), "the old wording was wrong whenever Original was verified")
        }

        @Test
        fun `both sides completely unreadable with a verified ORIGINAL page still keeps original`() {
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(SparkScanTermination.FAILED, SparkScanTermination.FAILED),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action)
            assertFalse(d.certain)
        }

        @Test
        fun `identity from the current page alone is enough when navigation never verified it`() {
            // The pager can open directly on ORIGINAL: heading AND dots resolved it this pass.
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(
                        SparkScanTermination.ALIGNMENT_FAILED,
                        SparkScanTermination.ALIGNMENT_FAILED,
                        currentPage = SparkSetSide.ORIGINAL,
                        navVerified = false,
                    ),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action)
        }

        @Test
        fun `standing on REROLLED is fine once navigation verified ORIGINAL earlier`() {
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(
                        SparkScanTermination.ALIGNMENT_FAILED,
                        SparkScanTermination.ALIGNMENT_FAILED,
                        currentPage = SparkSetSide.REROLLED,
                        navVerified = true,
                    ),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action)
        }
    }

    // ------------------------------------------------------------------
    // Content disagreement is evidence, never a veto.
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("content disagreement never vetoes")
    inner class ContentIsEvidenceOnly {
        private fun partialWith(mutate: (MutableList<SparkRowFact>) -> Unit): SparkPrefixEvidence {
            val partial = elCondorOriginal.take(8).toMutableList()
            mutate(partial)
            return SparkScrollMerge.describePrefix(elCondorOriginal, partial)
        }

        @Test
        fun `a one-star flicker in the partial original still keeps original`() {
            val evidence = partialWith { it[4] = white("Homestretch Haste", 1) }
            assertFalse(evidence.prefixAgreed, "the old gate would have refused here")
            assertEquals(4, evidence.firstDifferingRow)
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(SparkScanTermination.ALIGNMENT_FAILED, SparkScanTermination.ALIGNMENT_FAILED, prefix = evidence),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action)
            assertTrue(d.reason.contains("prefixAgreed=false"), "the disagreement is reported, not obeyed")
        }

        @Test
        fun `a unique-name OCR variant in the partial original still keeps original`() {
            // "Victoria por plancha t" lost its trailing star glyph and a letter.
            val evidence = partialWith { it[2] = unique("Victoria por plancha", 2) }
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(SparkScanTermination.ALIGNMENT_FAILED, SparkScanTermination.ALIGNMENT_FAILED, prefix = evidence),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action)
            assertFalse(d.certain)
        }

        @Test
        fun `a leading-glyph drop is reported as a real difference and still keeps original`() {
            // The proven post-swipe damage family: "Mile Straightaways O" read as
            // "ile Straightaways O". Folding fixes glyph CONFUSIONS (0/o, 1/i, l/i), not a
            // missing character, so this is a genuine string disagreement and is named as such.
            // Making it fold away would be tolerant alignment, which is deliberately out of scope
            // here; the point is that naming it changes nothing about the outcome.
            val evidence = partialWith { it[6] = white("ile Straightaways O", 1) }
            assertEquals(6, evidence.firstDifferingRow)
            assertTrue(evidence.firstDifference!!.contains("Mile Straightaways O"), "the log names the known row")
            assertTrue(evidence.firstDifference!!.contains("ile Straightaways O"), "and what was actually read")
            assertFalse(evidence.prefixAgreed, "the strict old rule refuses it, which is exactly why it stopped deciding")

            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(SparkScanTermination.ALIGNMENT_FAILED, SparkScanTermination.ALIGNMENT_FAILED, prefix = evidence),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action, "a named disagreement is still only evidence")
        }

        @Test
        fun `a glyph confusion does fold away`() {
            // The other half of the same damage family: an l-for-i style misread of one row.
            val evidence = partialWith { it[7] = white("Giittering Star", 1) }
            assertNull(evidence.firstDifferingRow, "l/i confusion is not a genuine row disagreement")
        }

        @Test
        fun `an exactly agreeing partial reports agreement`() {
            val evidence = SparkScrollMerge.describePrefix(elCondorOriginal, elCondorOriginal.take(8))
            assertTrue(evidence.prefixAgreed)
            assertNull(evidence.firstDifferingRow)
            assertEquals(1, evidence.missingRowCount)
            assertEquals(0, evidence.extraRowCount)
            assertEquals(8, evidence.comparedRowCount)
        }

        @Test
        fun `unknown race and scenario spark names alone never make a read fatal`() {
            // Arima Kinen, Hopeful S. and TS Climax Scenario are not in the skill database, so the
            // classifier warns on every frame. That noise must not reach the alignment verdict.
            val known = elCondorOriginal
            val evidence = SparkScrollMerge.describePrefix(known, known.take(8))
            assertTrue(evidence.prefixAgreed)
            val stitched = SparkScrollMerge.merge(known.take(8), known.drop(1))
            assertEquals(known, stitched, "unknown names stitch exactly like any other row")
        }
    }

    // ------------------------------------------------------------------
    // Identity and control failures stay fatal.
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("identity and control failures halt")
    inner class Fatal {
        @Test
        fun `an unresolved page with no verified navigation halts`() {
            // Heading says one page and the dots say another: resolveCurrentPagerSide yields null.
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(
                        SparkScanTermination.ALIGNMENT_FAILED,
                        SparkScanTermination.ALIGNMENT_FAILED,
                        currentPage = null,
                        navVerified = false,
                    ),
                )
            assertEquals(SparkSelectionAction.HALT, d.action)
            assertNull(d.side)
            assertEquals("identity_unverified", d.decidedBy)
        }

        @Test
        fun `heading Original with dots Rerolled cannot resolve a side at all`() {
            // The upstream guard: contradictory signals never produce a side to trust.
            assertEquals(SparkPagerResolution.Contradictory, resolvePagerSide(SparkSetSide.ORIGINAL, 1))
        }

        @Test
        fun `standing on REROLLED with no verified ORIGINAL navigation halts`() {
            // Navigation to Original failed or was never verified: nothing proves where Original is.
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(
                        SparkScanTermination.ALIGNMENT_FAILED,
                        SparkScanTermination.ALIGNMENT_FAILED,
                        currentPage = SparkSetSide.REROLLED,
                        navVerified = false,
                    ),
                )
            assertEquals(SparkSelectionAction.HALT, d.action)
            assertEquals("identity_unverified", d.decidedBy)
        }

        @Test
        fun `a missing Original selection control halts instead of guessing a coordinate`() {
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(
                        SparkScanTermination.ALIGNMENT_FAILED,
                        SparkScanTermination.ALIGNMENT_FAILED,
                        controlAvailable = false,
                    ),
                )
            assertEquals(SparkSelectionAction.HALT, d.action)
            assertEquals("control_unavailable", d.decidedBy)
            assertTrue(d.reason.contains("guessed coordinate"))
        }

        @Test
        fun `identity is checked before the control, so an unknown page never probes for a button`() {
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(
                        SparkScanTermination.ALIGNMENT_FAILED,
                        SparkScanTermination.ALIGNMENT_FAILED,
                        currentPage = null,
                        navVerified = false,
                        controlAvailable = false,
                    ),
                )
            assertEquals("identity_unverified", d.decidedBy)
        }
    }

    // ------------------------------------------------------------------
    // Bounds.
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("retry bounds")
    inner class Bounds {
        @Test
        fun `an incomplete current page with rescan budget asks for exactly one rescan`() {
            val first =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(SparkScanTermination.ALIGNMENT_FAILED, SparkScanTermination.COMPLETE_END_MARKER, rescanAvailable = true),
                )
            assertEquals(SparkSelectionAction.RESCAN_CURRENT_PAGE, first.action)
            assertEquals("rescan", first.decidedBy)

            // Budget spent: the same state now decides instead of looping.
            val second =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(SparkScanTermination.ALIGNMENT_FAILED, SparkScanTermination.COMPLETE_END_MARKER, rescanAvailable = false),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, second.action)
        }

        @Test
        fun `a rescan is never asked for when the page on screen read completely`() {
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(
                        SparkScanTermination.COMPLETE_END_MARKER,
                        SparkScanTermination.ALIGNMENT_FAILED,
                        currentPage = SparkSetSide.ORIGINAL,
                        rescanAvailable = true,
                    ),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action, "the short page is the other one; re-reading this one proves nothing")
        }

        @Test
        fun `the transaction hands out one rescan per page and no more`() {
            val tx = SparkRerollTransaction(careerNonce = "n", queueRun = 1, startedAtMs = 1_000L)
            assertTrue(tx.usePagerRescan(SparkSetSide.ORIGINAL))
            assertFalse(tx.usePagerRescan(SparkSetSide.ORIGINAL), "the second request for the same page is refused")
            assertTrue(tx.pagerRescanUsed(SparkSetSide.ORIGINAL))
            // The other page keeps its own independent budget.
            assertFalse(tx.pagerRescanUsed(SparkSetSide.REROLLED))
            assertTrue(tx.usePagerRescan(SparkSetSide.REROLLED))
            assertFalse(tx.usePagerRescan(SparkSetSide.REROLLED))
        }

        @Test
        fun `same-position retry counters ride along on the reading`() {
            val r = SparkSetReading(elCondorOriginal, SparkScanTermination.COMPLETE_NO_PROGRESS, scrollsUsed = 1, sameFrameRetries = 1, sameFrameRecoveries = 1)
            assertEquals(1, r.sameFrameRetries)
            assertEquals(1, r.sameFrameRecoveries)
            assertTrue(r.complete, "a stitch rescued by the fresh capture is a normal complete read")
            // Default construction stays backward compatible.
            val plain = SparkSetReading(elCondorOriginal, SparkScanTermination.COMPLETE_END_MARKER)
            assertEquals(0, plain.sameFrameRetries)
            assertEquals(0, plain.sameFrameRecoveries)
        }
    }

    // ------------------------------------------------------------------
    // The reconstructed live sequence.
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("the 2026-08-04 El Condor sequence")
    inner class LiveSequence {
        @Test
        fun `one scroll of eight readable rows reconstructs the nine-row set deterministically`() {
            // The pager window fits 8 of the 9 rows; the clamped swipe advances exactly one row.
            val frame1 = elCondorOriginal.take(8)
            val frame2 = elCondorOriginal.drop(1)
            val merged = SparkScrollMerge.merge(frame1, frame2)
            assertEquals(elCondorOriginal, merged)
            assertEquals(9, merged!!.size)
        }

        @Test
        fun `the damaged post-swipe frame is what refuses the stitch`() {
            // Frame 2 as it actually read: a dropped leading glyph on one row.
            val frame1 = elCondorOriginal.take(8)
            val damaged = elCondorOriginal.drop(1).toMutableList()
            damaged[5] = white("ile Straightaways O", 1)
            assertNull(SparkScrollMerge.merge(frame1, damaged), "exact-match stitching finds no overlap")
            // The same-position re-read supplies an undamaged frame, and the stitch succeeds.
            assertEquals(elCondorOriginal, SparkScrollMerge.merge(frame1, elCondorOriginal.drop(1)))
        }

        @Test
        fun `the full halt scenario now returns a commit instead of a block`() {
            // Both pager reads ALIGNMENT_FAILED, rescans already spent, pager verified on ORIGINAL,
            // Confirm locatable. This is the exact state that ended the queue at 3 of 11.
            val evidence = SparkScrollMerge.describePrefix(elCondorOriginal, elCondorOriginal.take(8))
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(
                        SparkScanTermination.ALIGNMENT_FAILED,
                        SparkScanTermination.ALIGNMENT_FAILED,
                        currentPage = SparkSetSide.ORIGINAL,
                        navVerified = true,
                        controlAvailable = true,
                        rescanAvailable = false,
                        prefix = evidence,
                    ),
                )
            assertEquals(SparkSelectionAction.CHOOSE_ORIGINAL, d.action)
            assertEquals(SparkSetSide.ORIGINAL, d.side)
            assertFalse(d.certain)
            assertEquals("incomplete_read", d.decidedBy)
            assertNotNull(d.reason)
        }

        @Test
        fun `the same scenario with an unprovable page still blocks`() {
            val d =
                SparkSelectionPolicy.decide(
                    uncertainChoice(),
                    inputs(
                        SparkScanTermination.ALIGNMENT_FAILED,
                        SparkScanTermination.ALIGNMENT_FAILED,
                        currentPage = null,
                        navVerified = false,
                        rescanAvailable = false,
                    ),
                )
            assertEquals(SparkSelectionAction.HALT, d.action)
        }
    }

    // ------------------------------------------------------------------
    // Reroll disabled, and the transaction's own gate on the fallback.
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("scope of the fallback")
    inner class Scope {
        @Test
        fun `a pre-spend transaction never reaches the pager decision at all`() {
            // Reroll disabled (or declined) means no spend, so the Spark Selection pager never
            // appears and none of this runs: the career confirms its own set on the SPARKS screen.
            val tx = SparkRerollTransaction(careerNonce = "n", queueRun = 1, startedAtMs = 1_000L)
            tx.captureOriginal(SparkSetReading(elCondorOriginal, SparkScanTermination.COMPLETE_END_MARKER), "El Condor Pasa", "Trackblazer")
            tx.declineSpend("reroll disabled")
            assertFalse(tx.postSpend, "a declined transaction is not post-spend")
            assertNull(tx.pagerRead(SparkSetSide.ORIGINAL))
            assertFalse(tx.recordPagerRead(SparkSetSide.ORIGINAL, SparkSetReading(elCondorOriginal, SparkScanTermination.COMPLETE_END_MARKER)).ok)
        }

        @Test
        fun `the uncertain fallback is only ever allowed to select Original`() {
            val tx = SparkRerollTransaction(careerNonce = "n", queueRun = 1, startedAtMs = 1_000L)
            tx.captureOriginal(SparkSetReading(elCondorOriginal, SparkScanTermination.COMPLETE_END_MARKER), "El Condor Pasa", "Trackblazer")
            tx.approveSpend("ev positive")
            tx.confirmSpend(1L)
            tx.captureRerolled(SparkSetReading(elCondorOriginal, SparkScanTermination.ALIGNMENT_FAILED))
            tx.introPassed()
            tx.recordPagerRead(SparkSetSide.ORIGINAL, SparkSetReading(elCondorOriginal.take(8), SparkScanTermination.ALIGNMENT_FAILED))
            val bd = breakdownOf(elCondorOriginal)

            val rerolledFallback = SparkChoice(SparkSetSide.REROLLED, "incomplete_read", "x", certain = false, original = bd, rerolled = bd)
            assertFalse(tx.selectWinner(rerolledFallback).ok, "an uncertain read can never commit the rerolled set")

            val originalFallback = SparkChoice(SparkSetSide.ORIGINAL, "incomplete_read", "x", certain = false, original = bd, rerolled = bd)
            assertTrue(tx.selectWinner(originalFallback).ok)
            assertEquals(SparkSetSide.ORIGINAL, tx.winner)
        }

        @Test
        fun `the fallback advances the transaction exactly once`() {
            val tx = SparkRerollTransaction(careerNonce = "n", queueRun = 1, startedAtMs = 1_000L)
            tx.captureOriginal(SparkSetReading(elCondorOriginal, SparkScanTermination.COMPLETE_END_MARKER), "El Condor Pasa", "Trackblazer")
            tx.approveSpend("ev positive")
            tx.confirmSpend(1L)
            tx.captureRerolled(SparkSetReading(elCondorOriginal, SparkScanTermination.ALIGNMENT_FAILED))
            tx.introPassed()
            tx.recordPagerRead(SparkSetSide.ORIGINAL, SparkSetReading(elCondorOriginal.take(8), SparkScanTermination.ALIGNMENT_FAILED))
            val bd = breakdownOf(elCondorOriginal)
            val fallback = SparkChoice(SparkSetSide.ORIGINAL, "incomplete_read", "x", certain = false, original = bd, rerolled = bd)

            assertTrue(tx.selectWinner(fallback).ok)
            val winnerAfterFirst = tx.winner
            // Re-entering the pager (the FSM does) must not re-decide or re-commit anything.
            assertTrue(tx.selectWinner(fallback).ok, "a repeat is absorbed, not refused")
            assertEquals(winnerAfterFirst, tx.winner)
            assertEquals(SparkTxState.WINNER_SELECTED, tx.state)
        }
    }
}
