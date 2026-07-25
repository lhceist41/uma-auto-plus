package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The chooser's pure plumbing: cross-frame scroll merging, scan-termination semantics, OCR
 * text normalization, and the two-signal pager page resolution.
 */
@DisplayName("Spark chooser model")
class SparkChooserModelTest {
    private fun white(name: String, stars: Int) = SparkRowFact(name, stars, SparkRowKind.WHITE, SparkWhiteClass.SKILL)

    private fun row(kind: SparkRowKind, stars: Int, name: String = "row") = SparkRowFact(name, stars, kind)

    @Nested
    @DisplayName("scroll merge")
    inner class Merge {
        private val lead = listOf(row(SparkRowKind.STAT, 2, "Stamina"), row(SparkRowKind.APTITUDE, 2, "Pace Chaser"), row(SparkRowKind.UNIQUE, 1, "Unique"))

        @Test
        fun `overlapping frames merge without duplicating the shared rows`() {
            val frame1 = lead + listOf(white("A", 1), white("B", 3))
            val frame2 = listOf(white("A", 1), white("B", 3), white("C", 2), white("D", 1))
            val merged = SparkScrollMerge.merge(frame1, frame2)
            assertEquals(listOf("Stamina", "Pace Chaser", "Unique", "A", "B", "C", "D"), merged?.map { it.name })
        }

        @Test
        fun `consecutive identical rows that fit lists of different lengths are refused, not guessed`() {
            // Two adjacent identical rows make BOTH overlap 1 and overlap 2 align. The two frames
            // are genuinely consistent with a 4-row list (overlap 2) AND a 5-row list (overlap 1),
            // so any single guess can silently drop or duplicate a row. The merge must refuse
            // (-> ALIGNMENT_FAILED -> incomplete read -> keep original) rather than guess.
            val frame1 = listOf(white("X", 2), white("Pace Chaser Corners", 1), white("Pace Chaser Corners", 1))
            val frame2 = listOf(white("Pace Chaser Corners", 1), white("Pace Chaser Corners", 1), white("Y", 2))
            assertNull(SparkScrollMerge.merge(frame1, frame2), "an ambiguous overlap must never be silently resolved")
        }

        @Test
        fun `the ambiguous merge never drops a row the way a largest-overlap guess would`() {
            // Regression for the collision the largest-overlap heuristic produced: truth is
            // [X, W, W, W, Y] read as [X,W,W] then [W,W,Y] (a 2-row swipe). The old merge returned
            // a 4-row [X,W,W,Y], silently dropping a W. The hardened merge refuses instead.
            val w = { white("W", 1) }
            val frame1 = listOf(white("X", 2), w(), w())
            val frame2 = listOf(w(), w(), white("Y", 2))
            assertNull(SparkScrollMerge.merge(frame1, frame2))
        }

        @Test
        fun `a unique overlap through repeated rows still merges (the real anchor scroll)`() {
            // The anchor original's pager scroll overlaps on a run that contains a distinctive row
            // (the 3-star Tenno Sho), so exactly one overlap aligns and the full set is recovered.
            val lead2 = listOf(row(SparkRowKind.STAT, 2, "Stamina"), row(SparkRowKind.APTITUDE, 2, "Pace"), row(SparkRowKind.UNIQUE, 1, "U"))
            val frame1 = lead2 + listOf(white("PopPolish", 1), white("TennoSho", 3), white("PrefPos", 2), white("PCStraight", 2), white("PCCorners", 1))
            val frame2 = listOf(white("TennoSho", 3), white("PrefPos", 2), white("PCStraight", 2), white("PCCorners", 1), white("Sympathy", 2), white("OsakaHai", 1))
            val merged = SparkScrollMerge.merge(frame1, frame2)
            assertEquals(10, merged?.size)
            assertEquals(listOf("Stamina", "Pace", "U", "PopPolish", "TennoSho", "PrefPos", "PCStraight", "PCCorners", "Sympathy", "OsakaHai"), merged?.map { it.name })
        }

        @Test
        fun `an unreadable name still aligns against its readable twin`() {
            val frame1 = listOf(white("A", 1), SparkRowFact(SPARK_UNREADABLE_NAME, 3, SparkRowKind.WHITE))
            val frame2 = listOf(white("B", 3), white("C", 2))
            // Stars+kind match (3-star white), names tolerant: the unreadable row aligns with "B".
            val merged = SparkScrollMerge.merge(frame1, frame2)
            assertEquals(3, merged?.size)
            assertEquals(listOf(1, 3, 2), merged?.map { it.stars })
        }

        @Test
        fun `a frame with no consistent overlap fails the merge instead of guessing`() {
            val frame1 = listOf(white("A", 1), white("B", 2))
            val frame2 = listOf(white("C", 3), white("D", 1))
            assertNull(SparkScrollMerge.merge(frame1, frame2), "a skipped-past scroll must surface as ALIGNMENT_FAILED, not silently concatenate")
        }

        @Test
        fun `kind or star mismatches never align`() {
            assertFalse(SparkScrollMerge.rowsAlign(listOf(white("A", 1)), listOf(white("A", 2))))
            assertFalse(SparkScrollMerge.rowsAlign(listOf(row(SparkRowKind.STAT, 2, "A")), listOf(row(SparkRowKind.APTITUDE, 2, "A"))))
            assertTrue(SparkScrollMerge.rowsAlign(listOf(white("a", 1)), listOf(white("A", 1))), "names compare case-insensitively")
        }

        @Test
        fun `an empty starting merge takes the first frame verbatim`() {
            val frame = lead + listOf(white("A", 1))
            assertEquals(frame, SparkScrollMerge.merge(emptyList(), frame))
        }
    }

    @Nested
    @DisplayName("scan termination semantics")
    inner class Termination {
        @Test
        fun `only the two completion proofs count as complete`() {
            for (termination in SparkScanTermination.entries) {
                val expected = termination == SparkScanTermination.COMPLETE_END_MARKER || termination == SparkScanTermination.COMPLETE_NO_PROGRESS
                assertEquals(expected, termination.complete, termination.name)
            }
        }

        @Test
        fun `a complete termination over zero rows is still not a complete reading`() {
            assertFalse(SparkSetReading(emptyList(), SparkScanTermination.COMPLETE_END_MARKER).complete)
        }

        @Test
        fun `unreadable rows are counted for the completeness report`() {
            val reading =
                SparkSetReading(
                    listOf(white("A", 1), SparkRowFact(SPARK_UNREADABLE_NAME, 2, SparkRowKind.WHITE), SparkRowFact("", 1, SparkRowKind.WHITE)),
                    SparkScanTermination.COMPLETE_END_MARKER,
                )
            assertEquals(2, reading.unreadableRowCount)
            assertTrue(reading.complete)
        }
    }

    @Nested
    @DisplayName("text normalization")
    inner class TextNorm {
        @Test
        fun `pager headings resolve through common OCR damage`() {
            assertEquals(SparkSetSide.REROLLED, SparkTextNorm.headingSide("Rerolled Sparks"))
            assertEquals(SparkSetSide.REROLLED, SparkTextNorm.headingSide("Reroiied Sparks"))
            assertEquals(SparkSetSide.ORIGINAL, SparkTextNorm.headingSide("Original Sparks"))
            assertEquals(SparkSetSide.ORIGINAL, SparkTextNorm.headingSide("0riginal Sparks"))
            assertEquals(SparkSetSide.ORIGINAL, SparkTextNorm.headingSide("originai sparks"))
            assertNull(SparkTextNorm.headingSide("Sparks"))
            assertNull(SparkTextNorm.headingSide(null))
            assertNull(SparkTextNorm.headingSide(""))
        }

        @Test
        fun `screen titles normalize independently of the pager headings`() {
            assertTrue(SparkTextNorm.isSparksRerolledTitle("Sparks Rerolled"))
            assertTrue(SparkTextNorm.isSparksRerolledTitle("Sparks Reroiled"))
            assertFalse(SparkTextNorm.isSparksRerolledTitle("Sparks"))
            assertTrue(SparkTextNorm.isSparkSelectionTitle("Spark Selection"))
            assertTrue(SparkTextNorm.isSparkSelectionTitle("Spark Seiection"))
            assertFalse(SparkTextNorm.isSparkSelectionTitle("Confirmation"))
        }

        @Test
        fun `name equality folds case, digits-for-letters, and punctuation`() {
            assertTrue(SparkTextNorm.namesEqual("Pop & Polish", "pop & poiish"))
            assertTrue(SparkTextNorm.namesEqual("Tenno Sho (Spring)", "Tenno Sho Spring"))
            assertFalse(SparkTextNorm.namesEqual("Stamina", "Speed"))
            assertFalse(SparkTextNorm.namesEqual(null, "Speed"))
            assertFalse(SparkTextNorm.namesEqual("", ""))
        }

        @Test
        fun `style settings canonicalize to the pink spark names`() {
            assertEquals("Pace Chaser", SparkTextNorm.canonicalStyleName("Pace"))
            assertEquals("Pace Chaser", SparkTextNorm.canonicalStyleName("Pace Chaser"))
            assertEquals("Front Runner", SparkTextNorm.canonicalStyleName("Front"))
            assertEquals("Late Surger", SparkTextNorm.canonicalStyleName("Late"))
            assertEquals("End Closer", SparkTextNorm.canonicalStyleName("End"))
            assertNull(SparkTextNorm.canonicalStyleName(""))
            assertNull(SparkTextNorm.canonicalStyleName(null))
        }
    }

    @Nested
    @DisplayName("confirmation pill classification (three live variants)")
    inner class Pill {
        @Test
        fun `the ordinary keep dialog's plain Sparks pill is recognised positively`() {
            // The variant that blocked a completed live career on 2026-07-19 because the code
            // knew only the two side-named forms and treated everything else as unreadable.
            assertEquals(SparkConfirmationPill.PLAIN, SparkTextNorm.confirmationPill("Sparks"))
            assertEquals(SparkConfirmationPill.PLAIN, SparkTextNorm.confirmationPill("sparks"))
            assertEquals(SparkConfirmationPill.PLAIN, SparkTextNorm.confirmationPill("Spark5"))
        }

        @Test
        fun `the side-named variants still win over the plain form`() {
            // Both side names also contain the word "Sparks", so ordering matters.
            assertEquals(SparkConfirmationPill.ORIGINAL, SparkTextNorm.confirmationPill("Original Sparks"))
            assertEquals(SparkConfirmationPill.REROLLED, SparkTextNorm.confirmationPill("Rerolled Sparks"))
            assertEquals(SparkConfirmationPill.ORIGINAL, SparkTextNorm.confirmationPill("0riginai Sparks"))
            assertEquals(SparkConfirmationPill.REROLLED, SparkTextNorm.confirmationPill("Reroiied Sparks"))
        }

        @Test
        fun `text with neither a side name nor the word spark is unreadable, never plain`() {
            assertEquals(SparkConfirmationPill.UNREADABLE, SparkTextNorm.confirmationPill(null))
            assertEquals(SparkConfirmationPill.UNREADABLE, SparkTextNorm.confirmationPill(""))
            assertEquals(SparkConfirmationPill.UNREADABLE, SparkTextNorm.confirmationPill("   "))
            assertEquals(SparkConfirmationPill.UNREADABLE, SparkTextNorm.confirmationPill("|||"))
        }

        @Test
        fun `a plain pill never resolves to a side`() {
            assertNull(SparkTextNorm.headingSide("Sparks"))
        }
    }

    @Nested
    @DisplayName("spend-decline diagnostics name one honest blocker")
    inner class Diagnostics {
        private fun diag(
            tx: Boolean = true,
            stats: Int? = 5,
            scan: SparkScanTermination? = SparkScanTermination.COMPLETE_END_MARKER,
            complete: Boolean = true,
            rows: Int = 9,
            leads: Boolean = true,
        ) = SparkSpendDiagnostics(tx, stats, scan, complete, rows, leads)

        @Test
        fun `all prerequisites met allows the spend decision`() {
            val d = diag()
            assertEquals(SparkSpendBlocker.NONE, d.blocker)
            assertTrue(d.spendAllowed)
        }

        @Test
        fun `a missing transaction is reported as missing, not as an unexpected layout`() {
            // The live regression: "spark rows: unexpected layout, scan: missing" when in fact
            // the transaction was gone and neither had been looked at.
            val d = diag(tx = false, scan = null, complete = false, rows = 0, leads = false)
            assertEquals(SparkSpendBlocker.TRANSACTION_MISSING, d.blocker)
            assertFalse(d.spendAllowed)
            val text = d.format()
            assertTrue(text.contains("blocker=transaction_missing"), text)
            assertTrue(text.contains("transaction=missing"), text)
            assertFalse(text.contains("layout=ok"), text)
        }

        @Test
        fun `a scan that never ran reads as skipped, not failed`() {
            val d = diag(scan = null, complete = false, rows = 0, leads = false)
            assertEquals(SparkSpendBlocker.ORIGINAL_READ_SKIPPED, d.blocker)
            assertTrue(d.format().contains("original_read=skipped"))
            assertTrue(d.format().contains("scan=not attempted"))
        }

        @Test
        fun `a scan that ran but fell short is incomplete, and names its termination`() {
            val d = diag(scan = SparkScanTermination.TIMED_OUT_PARTIAL, complete = false)
            assertEquals(SparkSpendBlocker.ORIGINAL_READ_INCOMPLETE, d.blocker)
            assertTrue(d.format().contains("original_read=incomplete"))
            assertTrue(d.format().contains("scan=TIMED_OUT_PARTIAL"))
        }

        @Test
        fun `a genuinely unexpected layout is only reported once everything upstream is fine`() {
            val d = diag(leads = false)
            assertEquals(SparkSpendBlocker.LAYOUT_UNEXPECTED, d.blocker)
            assertTrue(d.format().contains("layout=unexpected"))
        }

        @Test
        fun `blocker precedence runs transaction then stats then scan then completeness then layout`() {
            assertEquals(SparkSpendBlocker.TRANSACTION_MISSING, diag(tx = false, stats = null, scan = null, leads = false).blocker)
            assertEquals(SparkSpendBlocker.STATS_SNAPSHOT_MISSING, diag(stats = null, scan = null, leads = false).blocker)
            assertEquals(SparkSpendBlocker.STATS_SNAPSHOT_MISSING, diag(stats = 4).blocker)
            assertEquals(SparkSpendBlocker.ORIGINAL_READ_SKIPPED, diag(scan = null, complete = false, leads = false).blocker)
            assertEquals(SparkSpendBlocker.ORIGINAL_READ_INCOMPLETE, diag(complete = false, leads = false).blocker)
            assertEquals(SparkSpendBlocker.LAYOUT_UNEXPECTED, diag(leads = false).blocker)
        }
    }

    @Nested
    @DisplayName("pager page resolution requires both signals in agreement")
    inner class PageResolution {
        @Test
        fun `heading and dot agreeing resolves the page`() {
            assertEquals(
                SparkPagerResolution.Resolved(SparkSetSide.REROLLED),
                resolvePagerSide(SparkSetSide.REROLLED, 1),
            )
            assertEquals(
                SparkPagerResolution.Resolved(SparkSetSide.ORIGINAL),
                resolvePagerSide(SparkSetSide.ORIGINAL, 2),
            )
        }

        @Test
        fun `swapped or wrong headings are contradictions, never resolved`() {
            assertEquals(SparkPagerResolution.Contradictory, resolvePagerSide(SparkSetSide.ORIGINAL, 1))
            assertEquals(SparkPagerResolution.Contradictory, resolvePagerSide(SparkSetSide.REROLLED, 2))
        }

        @Test
        fun `a missing signal is unreadable, not assumed`() {
            assertEquals(SparkPagerResolution.Unreadable, resolvePagerSide(null, 1))
            assertEquals(SparkPagerResolution.Unreadable, resolvePagerSide(SparkSetSide.ORIGINAL, null))
            assertEquals(SparkPagerResolution.Unreadable, resolvePagerSide(null, null))
            assertEquals(SparkPagerResolution.Unreadable, resolvePagerSide(SparkSetSide.ORIGINAL, 3))
        }
    }

    /**
     * The corroboration test behind the keep-original fallback. A short pager read may only ever
     * confirm a set that was already read completely, never define or extend one, so the rule is
     * deliberately one-directional.
     */
    @Nested
    @DisplayName("partial-read corroboration")
    inner class ConsistentPrefix {
        // The nine-row Original set from the 2026-07-25 Daiwa Scarlet career, whose pager re-read
        // returned ALIGNMENT_FAILED and ended the queue.
        private val known =
            listOf(
                row(SparkRowKind.STAT, 1, "Stamina"),
                row(SparkRowKind.APTITUDE, 2, "Medium"),
                row(SparkRowKind.UNIQUE, 2, "Resplendent Red Ace"),
                white("Shuka Sho", 1),
                white("Competitive Spirit O", 2),
                white("Unyielding Spirit", 1),
                white("Front Runner Corners O", 1),
                white("Leader's Pride", 1),
                white("Playtime's Over!", 2),
            )

        @Test
        fun `a leading slice of the known set corroborates it`() {
            assertTrue(SparkScrollMerge.rowsAreConsistentPrefix(known, known.take(8)))
            assertTrue(SparkScrollMerge.rowsAreConsistentPrefix(known, known.take(1)))
        }

        @Test
        fun `the complete set corroborates itself`() {
            assertTrue(SparkScrollMerge.rowsAreConsistentPrefix(known, known))
        }

        @Test
        fun `an empty partial read proves nothing`() {
            assertFalse(SparkScrollMerge.rowsAreConsistentPrefix(known, emptyList()))
        }

        @Test
        fun `a partial read longer than the known set is rejected`() {
            assertFalse(SparkScrollMerge.rowsAreConsistentPrefix(known, known + white("Extra", 3)))
        }

        @Test
        fun `a contradicted star count is rejected`() {
            val contradicted = known.take(5).toMutableList()
            contradicted[4] = white("Competitive Spirit O", 3)
            assertFalse(SparkScrollMerge.rowsAreConsistentPrefix(known, contradicted))
        }

        @Test
        fun `a contradicted name is rejected`() {
            val contradicted = known.take(4).toMutableList()
            contradicted[3] = white("Some Other Skill", 1)
            assertFalse(SparkScrollMerge.rowsAreConsistentPrefix(known, contradicted))
        }

        @Test
        fun `a contradicted kind is rejected`() {
            val contradicted = known.take(3).toMutableList()
            contradicted[1] = row(SparkRowKind.STAT, 2, "Medium")
            assertFalse(SparkScrollMerge.rowsAreConsistentPrefix(known, contradicted))
        }

        @Test
        fun `rows in the right set but the wrong order are rejected`() {
            // Same content, shuffled: a suffix or reordering is not a prefix, so it cannot confirm.
            assertFalse(SparkScrollMerge.rowsAreConsistentPrefix(known, known.drop(1).take(4)))
        }

        @Test
        fun `an unreadable name still corroborates when kind and stars agree`() {
            // The reader marks a row unreadable rather than guessing; rowsAlign already tolerates
            // that, and the prefix rule inherits it so one garbled row cannot veto the fallback.
            val partial = known.take(4).toMutableList()
            partial[3] = SparkRowFact(SPARK_UNREADABLE_NAME, 1, SparkRowKind.WHITE, SparkWhiteClass.SKILL)
            assertTrue(SparkScrollMerge.rowsAreConsistentPrefix(known, partial))
        }
    }
}
