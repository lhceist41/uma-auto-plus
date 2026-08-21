package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.SparkRowKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Replay tests over the two live `Umamusume Details` -> Inspiration frames
 * (src/test/resources/fixtures/inspiration, see PROVENANCE.md). They pin the bar palette, the
 * two-column card geometry, the star columns, the scrollbar content model, the band-walk reader and
 * the cross-frame merge against the exact pixels a live capture must parse - no emulator, no OCR
 * (the PNGs are decoded by [FixturePng]).
 *
 * The fixture pair deliberately contains a real content gap: it was captured with one large manual
 * swipe that skipped three of ancestor 0's rows. That makes it the regression case for the one merge
 * failure a naive concatenation would silently hide, so the merge tests assert the gap is DETECTED
 * rather than pretending the pair is contiguous.
 */
@DisplayName("Veteran Inspiration panel pixel probes on the live capture fixtures")
class VeteranInspirationProbesFixtureTest {
    private fun sampler(name: String): SparkPixelSampler {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/inspiration/$name.png")) { "missing fixture $name.png" }
        val img = stream.use { FixturePng.read(it) }
        return SparkPixelSampler { x, y -> img.getRGB(x, y) }
    }

    private val frameHeight = 1920
    private val top by lazy { sampler("inspiration_top") }
    private val bottom by lazy { sampler("inspiration_bottom") }
    private val topRows by lazy { readInspirationRows(top, frameHeight) }
    private val bottomRows by lazy { readInspirationRows(bottom, frameHeight) }

    @Nested
    @DisplayName("bar-sample classification (measured Inspiration palette)")
    inner class BarClassification {
        @Test
        fun `each card kind, the section header and the panel gap classify correctly`() {
            assertEquals(InspirationBarClass.STAT, classifyInspirationBarSample(52, 183, 243), "blue stat card")
            assertEquals(InspirationBarClass.APTITUDE, classifyInspirationBarSample(255, 117, 175), "pink aptitude card")
            assertEquals(InspirationBarClass.UNIQUE, classifyInspirationBarSample(146, 207, 44), "green unique card")
            assertEquals(InspirationBarClass.WHITE, classifyInspirationBarSample(224, 224, 224), "grey race/skill card")
            assertEquals(InspirationBarClass.GAP, classifyInspirationBarSample(241, 241, 241), "panel background between cards")
            assertEquals(InspirationBarClass.SECTION_HEADER, classifyInspirationBarSample(117, 201, 12), "the green Sparks header pill")
        }

        @Test
        fun `the card drop shadow is not a white card`() {
            // The shadow under every card is a grey only ~45 darker than the card itself. If it read
            // WHITE, two vertically stacked white cards would merge into one tall run and the reader
            // would lose a whole factor row - so it must fall out as UNKNOWN and break the run.
            assertEquals(InspirationBarClass.UNKNOWN, classifyInspirationBarSample(181, 179, 193))
            assertEquals(InspirationBarClass.UNKNOWN, classifyInspirationBarSample(196, 194, 205))
        }

        @Test
        fun `the section header pill is not mistaken for a unique card`() {
            // Both are green. The pill is darker and has almost no blue; the unique card is lighter.
            assertEquals(InspirationBarClass.SECTION_HEADER, classifyInspirationBarSample(117, 201, 12))
            assertEquals(InspirationBarClass.UNIQUE, classifyInspirationBarSample(146, 207, 44))
        }

        @Test
        fun `the Legacy Origin divider text is not forced into a card kind`() {
            // The brown divider text intrudes into the left sample column. It sits in the inter-block
            // gap, so it must read UNKNOWN and separate runs rather than inventing a row.
            assertEquals(InspirationBarClass.UNKNOWN, classifyInspirationBarSample(189, 164, 146))
        }
    }

    @Nested
    @DisplayName("the tab strip")
    inner class TabStrip {
        @Test
        fun `the Inspiration tab reads active on both frames and the other two do not`() {
            for (frame in listOf(top, bottom)) {
                assertTrue(inspirationTabActive(frame, DETAIL_TAB_INSPIRATION_CX), "Inspiration is the active tab")
                assertFalse(inspirationTabActive(frame, DETAIL_TAB_SKILLS_CX), "Skills is not active")
                assertFalse(inspirationTabActive(frame, DETAIL_TAB_CAREER_INFO_CX), "Career Info is not active")
            }
        }
    }

    @Nested
    @DisplayName("the scrollbar content model")
    inner class Scrollbar {
        @Test
        fun `the top frame reads scrolled to the top with a zero offset`() {
            val scroll = readInspirationScroll(top)
            assertTrue(scroll.scrollable)
            assertEquals(912, scroll.thumbTop)
            assertEquals(1227, scroll.thumbBottom)
            assertEquals(0, scroll.offset)
            assertTrue(scroll.atTop)
            assertFalse(scroll.atBottom)
        }

        @Test
        fun `the bottom frame reads scrolled to the bottom with the full content offset`() {
            val scroll = readInspirationScroll(bottom)
            assertEquals(1330, scroll.thumbTop)
            assertEquals(1645, scroll.thumbBottom)
            assertEquals(1029, scroll.offset)
            assertFalse(scroll.atTop)
            assertTrue(scroll.atBottom)
        }

        @Test
        fun `both frames derive the same content height`() {
            // Same list, same total: the thumb length is the measurement, and it does not depend on
            // where the list happens to be scrolled. This is what lets the traversal prove it reached
            // the end instead of assuming the last swipe was enough.
            assertEquals(1807, readInspirationScroll(top).contentHeight)
            assertEquals(1807, readInspirationScroll(bottom).contentHeight)
        }

        @Test
        fun `both frames measure the same thumb, which is what makes a moving frame detectable`() {
            // The thumb length is a property of the list, not of the scroll position. A frame captured
            // during the panel's over-scroll bounce reports a much shorter thumb, which inflates the
            // derived content height by the same factor - the 20-Veteran validation run produced
            // heights of 2516, 5099 and 10197 for lists really about 1700 tall.
            val reference = readInspirationScroll(top).thumbLength
            assertEquals(316, reference)
            assertEquals(316, readInspirationScroll(bottom).thumbLength)
            assertTrue(inspirationScrollTrustworthy(readInspirationScroll(bottom), reference))
        }

        @Test
        fun `a thumb far shorter than the reference is refused rather than believed`() {
            val reference = readInspirationScroll(top).thumbLength
            val bouncing = readInspirationScroll(bottom).copy(thumbLength = 56, contentHeight = 10197)
            assertFalse(inspirationScrollTrustworthy(bouncing, reference))
            assertTrue(inspirationScrollTrustworthy(bouncing.copy(thumbLength = reference - INSPIRATION_THUMB_LENGTH_SLACK), reference), "antialiasing drift is still trusted")
        }

        @Test
        fun `the derived content height is a whole number of rows plus the section chrome`() {
            // 18 cards at the measured pitch, plus the Sparks header, the Legacy Origin divider, the
            // two block gaps and the bottom padding. Independent arithmetic on the same frame.
            val cards = 18
            val implied =
                98 + (cards - 1) * INSPIRATION_ROW_PITCH + 2 * (INSPIRATION_BLOCK_PITCH - INSPIRATION_ROW_PITCH) +
                    INSPIRATION_CARD_HEIGHT + INSPIRATION_BOTTOM_PADDING
            assertTrue(kotlin.math.abs(implied - 1807) < 20, "row arithmetic implies ${implied.toInt()}, the scrollbar says 1807")
        }
    }

    @Nested
    @DisplayName("the top frame: the Veteran's own Sparks and the first Legacy Origin rows")
    inner class TopFrame {
        @Test
        fun `seven fully-visible rows are read, none clipped`() {
            assertEquals(7, topRows.size)
            assertTrue(topRows.none { it.clipped }, "no row touches a viewport edge on this frame")
        }

        @Test
        fun `the card tops sit on the measured pitch`() {
            assertEquals(listOf(986, 1076, 1162, 1250, 1338, 1490, 1580), topRows.map { it.runTopY })
        }

        @Test
        fun `every row's kinds and star counts match the visible panel`() {
            assertEquals(
                listOf(
                    "STAT:1|APTITUDE:2",
                    "UNIQUE:1|WHITE:1",
                    "WHITE:1|WHITE:1",
                    "WHITE:2|WHITE:1",
                    "WHITE:1|-",
                    "STAT:1|APTITUDE:2",
                    "UNIQUE:3|WHITE:2",
                ),
                topRows.map { it.pixelKey() },
            )
        }

        @Test
        fun `the odd last row of the self block has no right-hand card`() {
            assertNull(topRows[4].right, "URA Finale is the fifth of nine self factors and stands alone")
            assertNotNull(topRows[3].right)
        }

        @Test
        fun `no star slot is ambiguous on any card colour`() {
            // Blue, pink, green and grey cards all sit behind the star glyphs. The classifier is
            // shared with the career-end spark reader, so this is the proof it transfers unchanged.
            for (row in topRows) {
                assertEquals(0, row.left.ambiguousStars, "left card at ${row.runTopY}")
                assertEquals(0, row.right?.ambiguousStars ?: 0, "right card at ${row.runTopY}")
            }
        }

        @Test
        fun `the Sparks section header is visible only at the top of the content`() {
            assertTrue(inspirationSparksHeaderVisible(top))
            assertFalse(inspirationSparksHeaderVisible(bottom))
        }

        @Test
        fun `a portrait sits on the rail beside each block's first row and not beside a mid-block row`() {
            val selfStat = topRows[0]
            val midBlock = topRows[2]
            assertTrue(inspirationRailPortraitDensity(top, (selfStat.runTopY + selfStat.runBottomY) / 2, frameHeight) >= INSPIRATION_PORTRAIT_MIN_DENSITY)
            assertEquals(0, inspirationRailPortraitDensity(top, (midBlock.runTopY + midBlock.runBottomY) / 2, frameHeight))
        }
    }

    @Nested
    @DisplayName("the bottom frame: the second Legacy Origin ancestor")
    inner class BottomFrame {
        @Test
        fun `eight rows are read and the one the viewport truncated is flagged clipped`() {
            assertEquals(8, bottomRows.size)
            assertTrue(bottomRows[0].clipped, "the first row is cut by the viewport top edge")
            assertTrue(bottomRows.drop(1).none { it.clipped }, "every other row is whole")
        }

        @Test
        fun `every row's kinds and star counts match the visible panel`() {
            assertEquals(
                listOf(
                    "WHITE:1|WHITE:1",
                    "WHITE:2|-",
                    "STAT:2|APTITUDE:2",
                    "UNIQUE:3|WHITE:1",
                    "WHITE:1|WHITE:1",
                    "WHITE:2|WHITE:1",
                    "WHITE:1|WHITE:2",
                    "WHITE:1|WHITE:2",
                ),
                bottomRows.map { it.pixelKey() },
            )
        }

        @Test
        fun `the second ancestor opens with the blue stat card`() {
            assertEquals(SparkRowKind.STAT, bottomRows[2].left.kind)
            assertEquals(SparkRowKind.APTITUDE, bottomRows[2].right?.kind)
            assertEquals(SparkRowKind.UNIQUE, bottomRows[3].left.kind)
        }
    }

    @Nested
    @DisplayName("OCR name regions")
    inner class NameRegions {
        private val wrapped by lazy { sampler("inspiration_two_line_name") }

        @Test
        fun `a one-line name yields a single full-width crop clear of the circle glyph`() {
            // 1162 is the "Kikuka Sho" card on the wrapped-name fixture.
            val regions = inspirationLeftNameRegions(wrapped, SparkRowKind.WHITE, 1162)
            assertEquals(1, regions.size)
            assertTrue(regions[0][0] > 252, "starts right of the circle glyph, whose ink ends at x=252")
            assertTrue(regions[0][0] + regions[0][2] <= 622, "ends before the right column's card at x=623")
        }

        @Test
        fun `the right column's one-line crop clears its own glyph and the scrollbar`() {
            val regions = inspirationRightNameRegions(sampler("inspiration_top"), SparkRowKind.WHITE, 1162)
            assertEquals(1, regions.size)
            assertTrue(regions[0][0] > 667, "starts right of the circle glyph, whose ink ends at x=667")
            assertTrue(regions[0][0] + regions[0][2] <= 1040, "ends before the scrollbar column at x=1041")
        }

        @Test
        fun `a wrapped name yields one crop per rendered line, in reading order`() {
            // "Behold Thine Emperor's Divine Might". The OCR engine reads one line per call, so a
            // single crop of both lines returns only "Might" - a wrong name, not a missing one.
            val regions = inspirationLeftNameRegions(wrapped, SparkRowKind.UNIQUE, 1076)
            assertEquals(2, regions.size)
            assertTrue(regions[0][1] < regions[1][1], "the crops are in reading order")
            assertTrue(regions[0][1] >= 1076, "the first line starts inside the card")
            assertTrue(regions[1][1] + regions[1][3] <= 1076 + INSPIRATION_CARD_HEIGHT + 10, "the last line ends inside the card")
        }

        @Test
        fun `only the wrapped last line is cropped short of the star column`() {
            // The stars sit at a fixed x on the last rendered line's row. A full-width crop of that
            // line would feed their glyphs to the OCR engine along with the text.
            val regions = inspirationLeftNameRegions(wrapped, SparkRowKind.UNIQUE, 1076)
            assertEquals(355, regions[0][2], "the first line gets the whole card width")
            assertTrue(regions[1][0] + regions[1][2] < 362, "the wrapped line stops before the first star at x=362")
        }

        @Test
        fun `a white card's star outlines are never mistaken for a line of text`() {
            // A white card prints dark text, and its star glyphs are outlined in a dark grey that
            // satisfies the same ink test. They are excluded by the scan window, not by luck.
            for (top in listOf(1162, 1250, 1338)) {
                assertEquals(1, inspirationLeftNameRegions(wrapped, SparkRowKind.WHITE, top).size, "card at $top")
            }
        }

        @Test
        fun `an empty right-hand cell yields no crop at all`() {
            // The last row of a block can have no right card. Reading its empty background would
            // produce whatever the OCR engine hallucinates from a flat grey rectangle.
            assertTrue(inspirationRightNameRegions(wrapped, SparkRowKind.WHITE, 1338).isEmpty())
        }

        @Test
        fun `the white card's name is read at a lowered binarization threshold`() {
            // The card families print in opposite polarity: white text on a saturated bar versus dark
            // brown text on light grey. The operator's threshold of 230 suits the first and erases the
            // second entirely, which is what the first live run of this reader demonstrated.
            assertEquals(0.0, inspirationNameThresholdIncrement(SparkRowKind.STAT))
            assertEquals(0.0, inspirationNameThresholdIncrement(SparkRowKind.APTITUDE))
            assertEquals(0.0, inspirationNameThresholdIncrement(SparkRowKind.UNIQUE))
            assertEquals(-80.0, inspirationNameThresholdIncrement(SparkRowKind.WHITE))
            val effective = 230 + INSPIRATION_WHITE_CARD_THRESHOLD_INCREMENT
            assertTrue(effective > 76 + 30, "stays clear of the white card's text at luma 76")
            assertTrue(effective < 224 - 30, "stays clear of the white card's background at luma 224")
        }
    }

    @Nested
    @DisplayName("a wrapped two-line factor name")
    inner class WrappedName {
        private val wrapped by lazy { sampler("inspiration_two_line_name") }
        private val wrappedRows by lazy { readInspirationRows(wrapped, frameHeight) }

        @Test
        fun `a two-line name does not make its card taller or move the pitch`() {
            // The unique card here carries "Behold Thine Emperor's Divine Might" over two lines. If
            // the card grew, every row position below it would shift and the merge would misalign.
            assertEquals(listOf(986, 1076, 1162, 1250, 1338, 1490, 1580), wrappedRows.map { it.runTopY })
            assertEquals(SparkRowKind.UNIQUE, wrappedRows[1].left.kind)
        }

        @Test
        fun `the star counts of the wrapped card and its neighbours are still exact`() {
            assertEquals(
                listOf(
                    "STAT:1|APTITUDE:2",
                    "UNIQUE:1|WHITE:1",
                    "WHITE:1|WHITE:1",
                    "WHITE:2|WHITE:1",
                    "WHITE:2|-",
                    "STAT:2|APTITUDE:2",
                    "UNIQUE:1|WHITE:2",
                ),
                wrappedRows.map { it.pixelKey() },
            )
        }
    }

    @Nested
    @DisplayName("cross-frame merge")
    inner class Merge {
        @Test
        fun `offering the same frame twice adds nothing and reports a full overlap`() {
            val accumulator = InspirationRowAccumulator()
            val offset = readInspirationScroll(top).offset
            assertEquals(7, accumulator.offerFrame(offset, topRows).added)
            val second = accumulator.offerFrame(offset, topRows)
            assertEquals(0, second.added, "a re-read frame contributes no duplicate rows")
            assertEquals(0, second.alignedAt)
            assertEquals(7, second.overlap)
            assertEquals(7, accumulator.rows.size)
            assertEquals(0, accumulator.gapCount)
        }

        @Test
        fun `the clipped row is never accepted`() {
            val accumulator = InspirationRowAccumulator()
            accumulator.offerFrame(readInspirationScroll(bottom).offset, bottomRows)
            assertEquals(7, accumulator.rows.size, "eight rows read, the clipped one rejected")
        }

        @Test
        fun `merging the two manual captures reports the content gap between them`() {
            // This is the regression case. The pair was captured with one swipe that skipped three of
            // ancestor 0's rows; the merge must refuse to align them and say so, because concatenating
            // them silently would produce a plausible-looking ancestor missing six factors.
            val accumulator = InspirationRowAccumulator()
            accumulator.offerFrame(readInspirationScroll(top).offset, topRows)
            val second = accumulator.offerFrame(readInspirationScroll(bottom).offset, bottomRows)
            assertTrue(second.gapDetected, "the frames do not overlap and must not be joined silently")
            assertEquals(1, accumulator.gapCount)
            assertEquals(1, accumulator.spacingBreaks().size, "one irregular row spacing, where the content was skipped")
        }

        @Test
        fun `rows are placed on the content axis, not the screen axis`() {
            val accumulator = InspirationRowAccumulator()
            accumulator.offerFrame(readInspirationScroll(top).offset, topRows)
            accumulator.offerFrame(readInspirationScroll(bottom).offset, bottomRows)
            assertEquals(
                listOf(98, 188, 274, 362, 450, 602, 692, 1119, 1271, 1361, 1445, 1535, 1623, 1711),
                accumulator.rows.map { it.contentTopY },
            )
            assertTrue(accumulator.rows.zipWithNext().all { (a, b) -> a.contentTopY < b.contentTopY }, "content order is preserved")
        }

        @Test
        fun `frames that abut with no shared row are joined, not reported as a gap`() {
            // A swipe can land with the new frame's first row immediately after the last accepted one,
            // sharing nothing to match on - which happens whenever the row between them was clipped in
            // both frames and therefore accepted in neither. Row spacing settles it.
            val accumulator = InspirationRowAccumulator()
            accumulator.offerFrame(readInspirationScroll(top).offset, topRows)
            val lastY = accumulator.rows.last().contentTopY
            // Place the bottom frame so its first accepted row lands exactly one card pitch on.
            val contiguousOffset = lastY + INSPIRATION_ROW_PITCH.toInt() - (bottomRows.first { !it.clipped }.runTopY - INSPIRATION_VIEWPORT_TOP)
            val merge = accumulator.offerFrame(contiguousOffset, bottomRows)
            assertFalse(merge.gapDetected, "abutting frames are contiguous, not a gap")
            assertEquals(7, merge.added)
            assertEquals(0, accumulator.gapCount)
            assertTrue(accumulator.spacingBreaks().isEmpty(), "and the joined stream has regular spacing")
        }

        /** A synthetic frame with the measured card pitch, so an overlap of a chosen size can be
         * constructed exactly. The live frames cannot: the pair in these fixtures has a real gap. */
        private fun frame(vararg keys: Pair<Pair<SparkRowKind, Int>, Pair<SparkRowKind, Int>?>): List<InspirationRowCell> =
            keys.mapIndexed { i, (left, right) ->
                val top = 986 + i * 88
                InspirationRowCell(top, top + INSPIRATION_CARD_HEIGHT, false, cell(left.first, left.second), right?.let { cell(it.first, it.second) })
            }

        private fun cell(kind: SparkRowKind, stars: Int) =
            InspirationCell(kind, (0 until 3).map { SparkSlotEvidence(it, 0, 0, 0, if (it < stars) SparkSlotRead.FILLED else SparkSlotRead.EMPTY) })

        private val frameA =
            frame(
                (SparkRowKind.STAT to 1) to (SparkRowKind.APTITUDE to 2),
                (SparkRowKind.UNIQUE to 1) to (SparkRowKind.WHITE to 1),
                (SparkRowKind.WHITE to 1) to (SparkRowKind.WHITE to 2),
                (SparkRowKind.WHITE to 2) to (SparkRowKind.WHITE to 1),
                (SparkRowKind.WHITE to 3) to (SparkRowKind.WHITE to 1),
                (SparkRowKind.WHITE to 1) to (SparkRowKind.WHITE to 3),
                (SparkRowKind.WHITE to 2) to (SparkRowKind.WHITE to 2),
            )

        /** The same list after a five-row advance: its first two rows are frame A's last two. */
        private val frameB =
            frame(
                (SparkRowKind.WHITE to 1) to (SparkRowKind.WHITE to 3),
                (SparkRowKind.WHITE to 2) to (SparkRowKind.WHITE to 2),
                (SparkRowKind.STAT to 2) to (SparkRowKind.APTITUDE to 3),
                (SparkRowKind.UNIQUE to 2) to (SparkRowKind.WHITE to 1),
                (SparkRowKind.WHITE to 1) to (SparkRowKind.WHITE to 1),
                (SparkRowKind.WHITE to 2) to (SparkRowKind.WHITE to 3),
                (SparkRowKind.WHITE to 3) to null,
            )

        private val fiveRowAdvance = 5 * 88

        @Test
        fun `a thin two-row overlap still merges, because position leads and the keys only confirm`() {
            // The scroller adds inertia: a swipe asked to advance four card pitches advances five and a
            // half, leaving as little as two rows in common between consecutive frames. Pixel matching
            // on two rows of "white one star / white one star" is not reliable, and treating a failure
            // there as a content gap wrongly condemned otherwise perfect live reads.
            val accumulator = InspirationRowAccumulator()
            accumulator.offerFrame(0, frameA)
            val merge = accumulator.offerFrame(fiveRowAdvance, frameB, offsetTrusted = true)
            assertFalse(merge.gapDetected, "a thin overlap is not a gap")
            assertEquals(2, merge.overlap, "exactly two rows are recognised as already held")
            assertEquals(5, merge.added)
            assertEquals(12, accumulator.rows.size)
            assertEquals(0, accumulator.gapCount)
            assertTrue(accumulator.spacingBreaks().isEmpty())
        }

        @Test
        fun `an untrusted offset can still place a frame, by matching pixel keys instead`() {
            // Dead reckoning after an unreadable scrollbar. The same thin overlap has to carry the
            // whole placement here, which is why it is the fallback and not the primary path.
            val accumulator = InspirationRowAccumulator()
            accumulator.offerFrame(0, frameA)
            val merge = accumulator.offerFrame(fiveRowAdvance, frameB, offsetTrusted = false)
            assertFalse(merge.gapDetected)
            assertEquals(5, merge.added)
        }

        @Test
        fun `a trusted offset that lands on content it does not match is refused, not written over`() {
            // Position leads, but it does not overrule the pixels: a frame placed where the rows say
            // something else must fall through to matching rather than overwrite what is held.
            val accumulator = InspirationRowAccumulator()
            accumulator.offerFrame(0, frameA)
            val merge = accumulator.offerFrame(88, frameB, offsetTrusted = true)
            assertTrue(merge.gapDetected, "one row's advance would put frame B's rows on top of frame A's")
        }

        @Test
        fun `an untrusted offset over content that does not match is refused`() {
            val accumulator = InspirationRowAccumulator()
            accumulator.offerFrame(readInspirationScroll(top).offset, topRows)
            val merge = accumulator.offerFrame(readInspirationScroll(bottom).offset, bottomRows, offsetTrusted = false)
            assertTrue(merge.gapDetected)
        }

        @Test
        fun `the observed content height agrees with the scrollbar's once the bottom is reached`() {
            val accumulator = InspirationRowAccumulator()
            accumulator.offerFrame(readInspirationScroll(top).offset, topRows)
            accumulator.offerFrame(readInspirationScroll(bottom).offset, bottomRows)
            val observed = requireNotNull(accumulator.observedContentHeight())
            assertTrue(
                kotlin.math.abs(observed - 1807) <= 45,
                "the last accepted row implies a content height of $observed against the scrollbar's 1807",
            )
        }
    }

    @Nested
    @DisplayName("the end of the factor list")
    inner class FactorListEnd {
        private val boundary by lazy { sampler("inspiration_history_boundary") }
        private val boundaryRows by lazy { readInspirationRows(boundary, frameHeight) }

        @Test
        fun `the factor list is not the whole panel`() {
            // Below the Legacy Origin blocks sits an "Inspiration History" section - one dated row per
            // time this Veteran was borrowed as a parent. This live Gold Ship has 142 of them, and the
            // scrollbar measures a panel an order of magnitude taller than its eighteen factor rows.
            // Treating that height as the factor list's height, or scrolling to it, is simply wrong.
            val scroll = readInspirationScroll(boundary)
            assertEquals(57, scroll.thumbLength)
            assertTrue(scroll.contentHeight > 9000, "the panel is ${scroll.contentHeight} px tall")
            assertFalse(scroll.atBottom, "and this frame is nowhere near its bottom")
        }

        @Test
        fun `history rows are never mistaken for factor cards`() {
            // They are white on white, so they fall out as panel background rather than as grey cards.
            assertEquals(2, boundaryRows.size)
            assertEquals(listOf("WHITE:3|WHITE:3", "WHITE:2|-"), boundaryRows.map { it.pixelKey() })
        }

        @Test
        fun `a panel this tall makes the scrollbar too coarse to place a row`() {
            // Fourteen pixels of content per pixel of thumb travel. Two frames each off by that much
            // put adjacent rows twenty-eight pixels apart, and the row-spacing check has to stay tight
            // enough to tell one block boundary (150 px) from two missed cards (177 px). So on a panel
            // like this the pixel keys lead the merge instead of the position.
            val reference = readInspirationScroll(boundary)
            assertTrue(inspirationOffsetResolution(reference) > 13.0)
            assertFalse(inspirationOffsetPreciseEnough(reference))
            // The ordinary case is four times finer and stays position-led.
            assertTrue(inspirationOffsetPreciseEnough(readInspirationScroll(top)))
            assertTrue(inspirationOffsetResolution(readInspirationScroll(top)) < 3.0)
        }

        @Test
        fun `empty space below the last card is what marks the end of the factors`() {
            assertTrue(inspirationFactorListEndsInFrame(boundaryRows, frameHeight))
        }

        @Test
        fun `a viewport-truncated middle of the list is not mistaken for its end`() {
            // Both Taiki frames are full of cards right down to the viewport edge. The largest gap that
            // can appear mid-list is the block boundary, which leaves about 130 px - well inside the
            // clearance this test relies on.
            assertFalse(inspirationFactorListEndsInFrame(topRows, frameHeight))
            assertFalse(inspirationFactorListEndsInFrame(bottomRows, frameHeight))
            assertTrue(INSPIRATION_LIST_END_CLEARANCE > 130, "clears the widest mid-list gap")
        }

        @Test
        fun `an empty frame is not an ended list`() {
            assertFalse(inspirationFactorListEndsInFrame(emptyList(), frameHeight))
        }
    }

    @Nested
    @DisplayName("block segmentation")
    inner class Blocks {
        @Test
        fun `a blue stat card opens each block and the self block comes first`() {
            val accumulator = InspirationRowAccumulator()
            accumulator.offerFrame(readInspirationScroll(top).offset, topRows)
            accumulator.offerFrame(readInspirationScroll(bottom).offset, bottomRows)
            val blocks = segmentInspirationBlocks(accumulator.rows)
            assertEquals(listOf(0, 1, 2), blocks.map { it.first }, "the Veteran plus its two Legacy Origin ancestors")
            assertEquals(listOf(5, 3, 6), blocks.map { it.second.size })
            assertTrue(blocks.all { it.second.first().row.left.kind == SparkRowKind.STAT })
        }

        @Test
        fun `rows above the first stat card become a leading partial block, never block zero`() {
            // A traversal that began part-way down the list would otherwise attribute an ancestor's
            // factors to the Veteran itself, which no later stage could detect.
            val accumulator = InspirationRowAccumulator()
            accumulator.offerFrame(readInspirationScroll(bottom).offset, bottomRows)
            val blocks = segmentInspirationBlocks(accumulator.rows)
            assertEquals(-1, blocks.first().first)
            assertEquals(1, blocks.first().second.size, "the one row above ancestor 1's stat card")
            assertEquals(0, blocks[1].first)
        }
    }
}
