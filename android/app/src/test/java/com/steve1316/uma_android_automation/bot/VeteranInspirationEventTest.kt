package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The Veteran Inspiration record: factor fingerprints, the self-versus-ancestor split, the
 * completeness gate, and the serialized shape.
 *
 * The fingerprints are the load-bearing part. A later retention stage compares what a Veteran can
 * pass on against what it already inherited and against what a career launch selected, so the token a
 * factor hashes to has to be stable across re-reads, independent of the order the rows came back in,
 * and byte-identical to the token the PL-4 lineage reader produces for the same factor.
 */
@DisplayName("Veteran Inspiration observation record")
class VeteranInspirationEventTest {
    private fun factor(kind: SparkRowKind, name: String, stars: Int, row: Int = 0, column: InspirationColumn = InspirationColumn.LEFT, ambiguous: Boolean = false) =
        InspirationFactor(rowIndex = row, column = column, kind = kind, displayName = name, stars = stars, ambiguous = ambiguous)

    private fun cleanDiagnostics(blocks: Int, rows: Int) =
        InspirationDiagnostics(
            frames = 3,
            swipes = 2,
            startedAtTop = true,
            reachedBottom = true,
            factorListEndObserved = true,
            gapFrames = 0,
            spacingBreaks = 0,
            alignmentFailures = 0,
            unsettledFrames = 0,
            deadReckonedFrames = 0,
            scrollbarContentHeight = 1807,
            observedContentHeight = 1795,
            rowsAccepted = rows,
            clippedRowsRejected = 1,
            leadingPartialBlockRows = 0,
            blocksObserved = blocks,
        )

    private val selfBlock =
        InspirationBlockObservation(
            0,
            portraitObserved = true,
            factors =
                listOf(
                    factor(SparkRowKind.STAT, "Power", 1, 0, InspirationColumn.LEFT),
                    factor(SparkRowKind.APTITUDE, "Mile", 2, 0, InspirationColumn.RIGHT),
                    factor(SparkRowKind.UNIQUE, "Shooting for Victory!", 1, 1, InspirationColumn.LEFT),
                    factor(SparkRowKind.WHITE, "Yasuda Kinen", 1, 1, InspirationColumn.RIGHT),
                ),
        )
    private val ancestorOne =
        InspirationBlockObservation(
            1,
            portraitObserved = true,
            factors =
                listOf(
                    factor(SparkRowKind.STAT, "Power", 1, 0, InspirationColumn.LEFT),
                    factor(SparkRowKind.APTITUDE, "Pace Chaser", 2, 0, InspirationColumn.RIGHT),
                ),
        )
    private val ancestorTwo =
        InspirationBlockObservation(
            2,
            portraitObserved = true,
            factors =
                listOf(
                    factor(SparkRowKind.STAT, "Speed", 2, 0, InspirationColumn.LEFT),
                    factor(SparkRowKind.APTITUDE, "Pace Chaser", 2, 0, InspirationColumn.RIGHT),
                ),
        )

    private fun assemble(
        blocks: List<InspirationBlockObservation> = listOf(selfBlock, ancestorOne, ancestorTwo),
        diagnostics: InspirationDiagnostics = cleanDiagnostics(3, 4),
        termination: InspirationReadTermination = InspirationReadTermination.REACHED_BOTTOM,
    ) = assembleVeteranInspiration(
        scanId = "insp-1",
        scanIndex = 0,
        observedAt = 1_700_000_000_000L,
        rosterFingerprint = "abc123",
        character = "Taiki Shuttle",
        outfit = "Wild Frontier",
        rank = "A",
        blocks = blocks,
        termination = termination,
        diagnostics = diagnostics,
    )

    @Nested
    @DisplayName("factor fingerprints")
    inner class Fingerprints {
        @Test
        fun `the same evidence always produces the same token`() {
            assertEquals(
                factor(SparkRowKind.WHITE, "Calm in a Crowd", 2).factorFingerprint,
                factor(SparkRowKind.WHITE, "Calm in a Crowd", 2, row = 7, column = InspirationColumn.RIGHT).factorFingerprint,
            )
        }

        @Test
        fun `whitespace and case in the OCR text do not change the token`() {
            assertEquals(
                factor(SparkRowKind.WHITE, "Calm in a Crowd", 2).factorFingerprint,
                factor(SparkRowKind.WHITE, "  calm   IN a Crowd ", 2).factorFingerprint,
            )
        }

        @Test
        fun `a trailing grade marker OCR cannot read consistently does not change the token`() {
            // The same card read twice comes back as "Medium Straightaways O" and then "Medium
            // Straightaways". A fingerprint that flips between two values for unchanged evidence is
            // useless to a later stage, so the marker is dropped from the token and kept in the text.
            val bare = factor(SparkRowKind.WHITE, "Medium Straightaways", 1).factorFingerprint
            for (marker in listOf("O", "0", "*", "@", "()", "○", "◎", "☆")) {
                assertEquals(bare, factor(SparkRowKind.WHITE, "Medium Straightaways $marker", 1).factorFingerprint, "marker $marker")
            }
            assertEquals("Medium Straightaways O", factor(SparkRowKind.WHITE, "Medium Straightaways O", 1).displayName, "the raw text keeps it")
        }

        @Test
        fun `a name that merely ends in an abbreviation or a plus is left alone`() {
            // "Japan C.", "February S.", "Mile Ch." and "Ignited Spirit: Speed +" are whole names.
            for (name in listOf("Japan C.", "February S.", "Mile Ch.", "Ignited Spirit: Speed +", "Triple 7s")) {
                assertTrue(factor(SparkRowKind.WHITE, name, 1).normalizedName.isNotEmpty())
                assertEquals(name.uppercase(), factor(SparkRowKind.WHITE, name, 1).normalizedName, name)
            }
        }

        @Test
        fun `a different star count or name changes the token`() {
            val base = factor(SparkRowKind.WHITE, "Calm in a Crowd", 2).factorFingerprint
            assertNotEquals(base, factor(SparkRowKind.WHITE, "Calm in a Crowd", 3).factorFingerprint)
            assertNotEquals(base, factor(SparkRowKind.WHITE, "Calm in a Cloud", 2).factorFingerprint)
            assertNotEquals(base, factor(SparkRowKind.UNIQUE, "Calm in a Crowd", 2).factorFingerprint)
        }

        @Test
        fun `the token is byte-identical to the PL-4 lineage reader's for the same factor`() {
            // The two screens are different evidence sources for the same inheritance system. If their
            // tokens diverged, nothing could ever be cross-checked between them.
            val f = factor(SparkRowKind.UNIQUE, "Dancing in the Leaves", 3)
            assertEquals(
                ancestorFactorFingerprint(listOf(LineageFactorObservation(SparkRowKind.UNIQUE, "Dancing in the Leaves", 3, ambiguous = false, clipped = false))),
                f.factorFingerprint,
            )
        }

        @Test
        fun `an ancestor's set digest does not depend on the order the rows were read`() {
            val forward = InspirationAncestor(0, true, null, selfBlock.factors)
            val reversed = InspirationAncestor(0, true, null, selfBlock.factors.reversed())
            assertEquals(forward.factorFingerprint, reversed.factorFingerprint)
        }
    }

    @Nested
    @DisplayName("self versus ancestor split")
    inner class Split {
        @Test
        fun `block zero is the Veteran itself and later blocks are its Legacy Origin ancestors`() {
            val o = assemble()
            assertEquals(4, o.selfFactors.size)
            assertEquals(2, o.legacyAncestors.size)
            assertEquals(listOf(0, 1), o.legacyAncestors.map { it.ancestorIndex })
            assertEquals("Speed", o.legacyAncestors[1].factors.first().displayName)
        }

        @Test
        fun `the two sources are never flattened into one bag`() {
            val o = assemble()
            assertNotEquals(o.selfFactorFingerprint, o.legacyAncestors[0].factorFingerprint)
            assertFalse(o.selfFactors.any { it.displayName == "Speed" }, "an ancestor's factor never lands in selfFactors")
        }

        @Test
        fun `ancestor rank stays unresolved rather than guessed from the portrait badge`() {
            assertTrue(assemble().legacyAncestors.all { it.rank == null })
        }

        @Test
        fun `a leading partial block is dropped, counted, and never becomes the self block`() {
            val partial = InspirationBlockObservation(-1, portraitObserved = false, factors = listOf(factor(SparkRowKind.WHITE, "Levelheaded", 1)))
            val o = assemble(blocks = listOf(partial, selfBlock, ancestorOne))
            assertEquals(4, o.selfFactors.size)
            assertFalse(o.selfFactors.any { it.displayName == "Levelheaded" })
            assertEquals(1, o.legacyAncestors.size)
        }
    }

    @Nested
    @DisplayName("the completeness gate")
    inner class Completeness {
        @Test
        fun `a clean traversal of a complete panel is marked complete`() {
            val o = assemble()
            assertTrue(o.sparkCaptureComplete)
            assertEquals(1.0, o.screenReadCompleteness)
            assertTrue(o.unresolvedFields.isEmpty())
        }

        @Test
        fun `a detected content gap disqualifies the capture`() {
            val o = assemble(diagnostics = cleanDiagnostics(3, 4).copy(gapFrames = 1, spacingBreaks = 1))
            assertFalse(o.sparkCaptureComplete)
            assertTrue(o.unresolvedFields.contains("contentGap"))
            assertTrue(o.unresolvedFields.contains("rowSpacing"))
        }

        @Test
        fun `not starting at the top disqualifies the capture`() {
            assertFalse(assemble(diagnostics = cleanDiagnostics(3, 4).copy(startedAtTop = false)).sparkCaptureComplete)
            assertTrue(assemble(diagnostics = cleanDiagnostics(3, 4).copy(startedAtTop = false)).unresolvedFields.contains("startedAtTop"))
        }

        @Test
        fun `a scrollbar that could not confirm the panel bottom does not disqualify the read`() {
            // The panel bounces after a swipe and its thumb shrinks while it settles, so the final
            // frame cannot always confirm the bottom - and for a Veteran with a usage history that
            // bottom is hundreds of rows past the last factor anyway.
            val o = assemble(diagnostics = cleanDiagnostics(3, 4).copy(reachedBottom = false))
            assertTrue(o.sparkCaptureComplete)
            assertFalse(o.unresolvedFields.contains("reachedBottom"))
        }

        @Test
        fun `not seeing the end of the factor list disqualifies the capture`() {
            val o = assemble(diagnostics = cleanDiagnostics(3, 4).copy(factorListEndObserved = false))
            assertFalse(o.sparkCaptureComplete)
            assertTrue(o.unresolvedFields.contains("factorListEnd"))
        }

        @Test
        fun `a panel far taller than its factors is not treated as an unfinished read`() {
            // Below the factors sits an inspiration-usage history that can be an order of magnitude
            // taller than them (a live Gold Ship measured 10,018 px behind eighteen factor rows). The
            // panel's height therefore says nothing about whether every factor was read.
            val o = assemble(diagnostics = cleanDiagnostics(3, 4).copy(scrollbarContentHeight = 10018, reachedBottom = false))
            assertTrue(o.sparkCaptureComplete)
            assertTrue(o.unresolvedFields.isEmpty())
        }

        @Test
        fun `an unread factor name is named in the unresolved list rather than inferred`() {
            val blank = selfBlock.copy(factors = selfBlock.factors.dropLast(1) + factor(SparkRowKind.WHITE, "", 1, 1, InspirationColumn.RIGHT))
            val o = assemble(blocks = listOf(blank, ancestorOne, ancestorTwo))
            assertFalse(o.sparkCaptureComplete)
            assertTrue(o.unresolvedFields.any { it.startsWith("factorName@white:1:right") })
            assertEquals("", o.selfFactors.last().displayName, "the blank is kept as evidence, never filled in")
        }

        @Test
        fun `an ambiguous star read is named rather than counted as a value`() {
            val fuzzy = selfBlock.copy(factors = listOf(factor(SparkRowKind.STAT, "Power", 1, 0, InspirationColumn.LEFT, ambiguous = true)))
            val o = assemble(blocks = listOf(fuzzy, ancestorOne, ancestorTwo))
            assertFalse(o.sparkCaptureComplete)
            assertTrue(o.unresolvedFields.any { it.startsWith("factorStars@stat:0:left") })
        }

        @Test
        fun `a panel that never opened yields no factors and no completeness`() {
            val o = assemble(blocks = emptyList(), termination = InspirationReadTermination.PANEL_NOT_READY)
            assertFalse(o.sparkCaptureComplete)
            assertTrue(o.selfFactors.isEmpty())
            assertTrue(o.unresolvedFields.contains("selfSparks"))
        }
    }

    @Nested
    @DisplayName("serialization")
    inner class Serialization {
        @Test
        fun `the record carries the roster join key and the retention-readiness fields`() {
            val json = serializeVeteranInspiration(assemble())
            assertEquals("veteran_inspiration", json.getString("type"))
            assertEquals("abc123", json.getString("rosterFingerprint"))
            assertEquals(4, json.getInt("selfFactorCount"))
            assertTrue(json.getBoolean("sparkCaptureComplete"))
            assertEquals(2, json.getJSONArray("legacyAncestors").length())
            assertEquals(2, json.getJSONArray("legacyAncestors").getJSONObject(0).getInt("factorCount"))
        }

        @Test
        fun `every factor carries its raw text, its normalized text and its token`() {
            val first = serializeVeteranInspiration(assemble()).getJSONArray("selfFactors").getJSONObject(0)
            assertEquals("Power", first.getString("displayName"))
            assertEquals("POWER", first.getString("normalizedName"))
            assertEquals("stat:POWER:1", first.getString("factorFingerprint"))
            assertEquals("left", first.getString("column"))
            assertFalse(first.has("ambiguous"), "a clean star read writes no honesty flag at all")
        }

        @Test
        fun `the traversal diagnostics survive into the record`() {
            val d = serializeVeteranInspiration(assemble()).getJSONObject("diagnostics")
            assertEquals(1807, d.getInt("scrollbarContentHeight"))
            assertEquals(1795, d.getInt("observedContentHeight"))
            assertEquals(0, d.getInt("gapFrames"))
            assertEquals(3, d.getInt("blocksObserved"))
        }

        @Test
        fun `the scan header records both roster counts and refuses to assume they matched`() {
            fun header(start: Int?, end: Int?) =
                VeteranInspirationScanHeader(
                    schemaVersion = VETERAN_INSPIRATION_SCHEMA_VERSION,
                    scanId = "insp-1",
                    startedAt = 1L,
                    completedAt = 2L,
                    registeredUsedAtStart = start,
                    registeredUsedAtEnd = end,
                    registeredCapacity = 260,
                    filtersOff = true,
                    sortKey = "Rating",
                    sortDirection = "Desc",
                    snapshotCompatibility = end != null && end == start,
                    entryLimit = 20,
                    entriesCaptured = 20,
                    entriesComplete = 20,
                    terminationReason = InspirationScanTermination.ENTRY_LIMIT_REACHED,
                    app = "test",
                    screenWidth = 1080,
                    screenHeight = 1920,
                )
            assertTrue(serializeVeteranInspirationScan(header(257, 257)).getBoolean("snapshotCompatibility"))
            assertFalse(serializeVeteranInspirationScan(header(257, 258)).getBoolean("snapshotCompatibility"))
            assertFalse(serializeVeteranInspirationScan(header(257, null)).getBoolean("snapshotCompatibility"), "an unread count is not a matching count")
        }
    }
}
