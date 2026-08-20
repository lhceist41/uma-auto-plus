package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.SparkRowKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Replay tests over the two live Legacy "Sparks" sub-view frames (src/test/resources/fixtures/legacy,
 * see PROVENANCE.md). They pin the band-walk reader, the Legacy bar palette, the star columns, and
 * the ancestor-block segmentation against the exact pixels a live lineage capture must parse - no
 * emulator, no OCR (the PNGs are decoded by [FixturePng]).
 */
@DisplayName("Legacy Sparks-view pixel probes on the live capture fixtures")
class LegacySparkProbesFixtureTest {
    private fun sampler(name: String): SparkPixelSampler {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/legacy/$name.png")) { "missing fixture $name.png" }
        val img = stream.use { FixturePng.read(it) }
        return SparkPixelSampler { x, y -> img.getRGB(x, y) }
    }

    private val frameHeight = 1920

    @Nested
    @DisplayName("bar-sample classification (measured Legacy palette)")
    inner class BarClassification {
        @Test
        fun `each factor kind, the divider pill, and the gaps classify correctly`() {
            assertEquals(LegacyBarClass.STAT, classifyLegacyBarSample(105, 203, 251), "blue stat bar")
            assertEquals(LegacyBarClass.STAT, classifyLegacyBarSample(120, 211, 252), "blue stat bar, antialiased top")
            assertEquals(LegacyBarClass.APTITUDE, classifyLegacyBarSample(253, 146, 196), "pink aptitude bar")
            assertEquals(LegacyBarClass.UNIQUE, classifyLegacyBarSample(154, 217, 52), "green unique bar")
            assertEquals(LegacyBarClass.WHITE, classifyLegacyBarSample(220, 220, 220), "grey race/skill card")
            assertEquals(LegacyBarClass.GAP, classifyLegacyBarSample(255, 255, 255), "inter-card white gap")
        }

        @Test
        fun `the section divider pill is not mistaken for a unique row`() {
            // The "1st/2nd Legacy" pill (117,201,12) would fall into the plain unique test; its very
            // low blue channel separates it. The unique bar (154,217,52) must stay UNIQUE.
            assertEquals(LegacyBarClass.SECTION_PILL, classifyLegacyBarSample(117, 201, 12))
            assertEquals(LegacyBarClass.UNIQUE, classifyLegacyBarSample(154, 217, 52))
        }

        @Test
        fun `an unrelated bright color is not forced into a row kind`() {
            // A portrait pixel bleeding into the sample column must read UNKNOWN, never a false bar.
            assertEquals(LegacyBarClass.UNKNOWN, classifyLegacyBarSample(255, 215, 170))
        }
    }

    @Nested
    @DisplayName("the parent block (1st Legacy frame)")
    inner class ParentBlock {
        private val rows by lazy { readLegacySparkRows(sampler("legacy_sparks_first"), frameHeight) }

        @Test
        fun `reads exactly the parent's eleven rows in kind-and-star order`() {
            val expected =
                listOf(
                    SparkRowKind.STAT to 1,
                    SparkRowKind.APTITUDE to 2,
                    SparkRowKind.UNIQUE to 3,
                    SparkRowKind.WHITE to 2,
                    SparkRowKind.WHITE to 1,
                    SparkRowKind.WHITE to 1,
                    SparkRowKind.WHITE to 2,
                    SparkRowKind.WHITE to 1,
                    SparkRowKind.WHITE to 1,
                    SparkRowKind.WHITE to 2,
                    SparkRowKind.WHITE to 1,
                )
            assertEquals(expected, rows.map { it.kind to it.filledStars })
        }

        @Test
        fun `segments into one ancestor block that leads stat, aptitude, unique`() {
            val blocks = segmentLegacyBlocks(rows)
            assertEquals(1, blocks.size)
            assertTrue(blocks[0].hasLeadTriple)
            assertEquals(0, blocks[0].index)
            assertEquals(8, blocks[0].whiteRows.size)
        }

        @Test
        fun `the portrait sits on the rail at the stat row and nowhere else in the block`() {
            assertTrue(rows.first { it.kind == SparkRowKind.STAT }.portraitOnRail, "the block-start stat row has its portrait")
            assertFalse(rows.first { it.kind == SparkRowKind.APTITUDE }.portraitOnRail, "the aptitude row is below the portrait")
            assertFalse(rows.first { it.kind == SparkRowKind.UNIQUE }.portraitOnRail)
        }

        @Test
        fun `the divider pill above the block never becomes a row`() {
            // The "1st Legacy" pill band is ~y188-224; no emitted row may fall there.
            assertTrue(rows.none { it.rowY in 180..230 }, "the section pill must not be read as a factor row")
        }
    }

    @Nested
    @DisplayName("the grandparent blocks (scrolled frame)")
    inner class GrandparentBlocks {
        private val rows by lazy { readLegacySparkRows(sampler("legacy_sparks_grandparents"), frameHeight) }

        @Test
        fun `two grandparent blocks are segmented, each starting on its own stat row`() {
            val blocks = segmentLegacyBlocks(rows)
            assertEquals(2, blocks.size)
            assertEquals(listOf(0, 1), blocks.map { it.index })
            // Block 0 is the fully-visible A-rank grandparent.
            assertTrue(blocks[0].hasLeadTriple)
            assertEquals(3, blocks[0].statRow?.filledStars)
            assertEquals(3, blocks[0].aptitudeRow?.filledStars)
            assertEquals(2, blocks[0].uniqueRow?.filledStars)
            assertEquals(6, blocks[0].whiteRows.size)
            // Block 1 is the SS-rank grandparent, clipped at the list bottom (stat + aptitude only).
            assertEquals(2, blocks[1].statRow?.filledStars)
            assertEquals(2, blocks[1].aptitudeRow?.filledStars)
            assertEquals(null, blocks[1].uniqueRow, "the unique row scrolled below the list bottom")
        }

        @Test
        fun `both grandparent stat rows carry a rail portrait`() {
            val blocks = segmentLegacyBlocks(rows)
            assertTrue(blocks[0].statRow!!.portraitOnRail)
            assertTrue(blocks[1].statRow!!.portraitOnRail)
        }

        @Test
        fun `the clipped last row is flagged, so a partial capture is honest`() {
            assertTrue(rows.last().clipped, "the row cut by the list bottom is marked clipped")
        }
    }

    @Nested
    @DisplayName("cross-frame ancestor accumulation")
    inner class Accumulation {
        private val frame1 by lazy { readLegacySparkRows(sampler("legacy_sparks_first"), frameHeight) }
        private val frame2 by lazy { readLegacySparkRows(sampler("legacy_sparks_grandparents"), frameHeight) }

        @Test
        fun `two scrolled frames accumulate distinct ancestors in capture order`() {
            val acc = LegacyLineageAccumulator()
            assertEquals(1, acc.offerFrame(frame1), "the parent block")
            assertEquals(1, acc.offerFrame(frame2), "the fully-visible A-rank grandparent")
            assertEquals(2, acc.ancestors.size)
            // Order preserved: parent (stat 1) then grandparent (stat 3).
            assertEquals(1, acc.ancestors[0].statRow?.filledStars)
            assertEquals(3, acc.ancestors[1].statRow?.filledStars)
        }

        @Test
        fun `a block clipped by the list mask is not accepted until it is seen whole`() {
            val acc = LegacyLineageAccumulator()
            acc.offerFrame(frame2)
            // frame2 holds two grandparents but the SS-rank one is clipped at the bottom; only the
            // fully-visible one is accepted.
            assertEquals(1, acc.ancestors.size, "the clipped grandparent block is held back, not accepted partial")
        }

        @Test
        fun `re-offering an already-captured frame adds nothing and counts as a stall`() {
            val acc = LegacyLineageAccumulator()
            acc.offerFrame(frame1)
            assertEquals(0, acc.offerFrame(frame1), "the overlapping re-read de-dupes to zero")
            assertEquals(1, acc.stalledRounds)
            assertFalse(acc.complete, "two of six ancestors is not a complete capture")
        }
    }

    @Nested
    @DisplayName("OCR name regions stay inside the frame, left of the stars")
    inner class OcrRegions {
        @Test
        fun `a row name region sits left of the first star column and inside the frame`() {
            val region = legacyNameOcrRegion(rowY = 705)
            val (x, y, w, h) = listOf(region[0], region[1], region[2], region[3])
            assertTrue(x >= 0 && y >= 0, "region origin inside frame")
            assertTrue(x + w <= LEGACY_STAR_XS.first(), "the name band ends left of the first star column")
            assertTrue(h in 40..90, "one row tall")
        }
    }
}
