package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.SparkRowKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Replay tests over the live 2026-07-08 capture set (src/test/resources/fixtures/sparks, see
 * PROVENANCE.md): every screen of one real Spark Reroll flow, at full resolution. These pin
 * the pixel probes, geometries, and anchors in SparkScreenProbes against the exact frames the
 * chooser must handle, with no emulator, no OpenCV, and no OCR (the PNGs are decoded by
 * [FixturePng] because android.jar offers no imageio).
 *
 * The star counts and kinds asserted here are the sets the career actually rolled: original =
 * blue Stamina 2, pink Pace Chaser 2, unique 1, whites 1/3/2/2/1/2/2 (the 3-star white is the
 * race spark "Tenno Sho (Spring)"); rerolled = blue Wit 2, pink Pace Chaser 1, unique 2,
 * whites 1/2/1/1.
 */
@DisplayName("Spark screen pixel probes on the live capture fixtures")
class SparkScreenProbeFixtureTest {
    private val fixtures = mutableMapOf<String, FixturePng>()

    private fun image(name: String): FixturePng =
        fixtures.getOrPut(name) {
            val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/sparks/$name.png")) { "missing fixture $name.png" }
            stream.use { FixturePng.read(it) }
        }

    private fun sampler(name: String): SparkPixelSampler {
        val img = image(name)
        return SparkPixelSampler { x, y -> img.getRGB(x, y) }
    }

    private val allFixtures =
        listOf(
            "sparks_screen",
            "confirm_reroll_dialog",
            "sparks_rerolled_result",
            "spark_selection_intro",
            "pager_rerolled",
            "pager_original",
            "confirmation_original",
            "umamusume_details",
            "rating_record",
            "keep_confirmation_plain",
            "keep_confirmation_medium3",
            "keep_confirmation_guts2",
        )

    @Nested
    @DisplayName("the ordinary keep confirmation (plain Sparks pill, live 2026-07-19)")
    inner class KeepConfirmation {
        /** The 11-row set the live career actually kept, in order (see PROVENANCE.md). */
        private val liveKeptSet =
            listOf(
                SparkRowKind.STAT to 1,
                SparkRowKind.APTITUDE to 2,
                SparkRowKind.UNIQUE to 1,
                SparkRowKind.WHITE to 1,
                SparkRowKind.WHITE to 1,
                SparkRowKind.WHITE to 1,
                SparkRowKind.WHITE to 1,
                SparkRowKind.WHITE to 2,
                SparkRowKind.WHITE to 1,
                SparkRowKind.WHITE to 2,
                SparkRowKind.WHITE to 2,
            )

        @Test
        fun `it shares the confirmation dialog chrome, which is why the pill text must decide`() {
            // The structural probe cannot separate this dialog from the post-reroll selection
            // confirmation - both are the same green-header card with Cancel/Confirm. That is
            // exactly why detection reads the pill and consults the transaction.
            assertTrue(sparkConfirmationStructurePresent(sampler("keep_confirmation_plain")))
        }

        @Test
        fun `it is not a pager, an intro, or a rerolled result`() {
            val s = sampler("keep_confirmation_plain")
            assertFalse(sparkPagerStructurePresent(s))
            assertFalse(sparkIntroStructurePresent(s))
            assertFalse(sparkRerolledStructurePresent(s))
            assertNull(sparkPagerActiveDotIndex(s))
            assertFalse(sparkPagerChevronsPresent(s))
        }

        @Test
        fun `the complete 11-row kept set reads exactly, with no truncation and no phantom tail`() {
            val cells = parseSparkRowCells(sampler("keep_confirmation_plain"), SPARKS_CONFIRM_GEOMETRY, image("keep_confirmation_plain").height)
            assertEquals(11, cells.size, "the live set is 11 rows; a 6- or 9-row window would truncate it")
            assertEquals(liveKeptSet, cells.map { it.kind to it.stars })
            assertTrue(sparkCellsLeadCorrectly(cells))
        }

        @Test
        fun `the 11-row set proves its own end marker inside one frame`() {
            // maxRows sits one slot past the largest observed set, so the grid break is visible
            // and the complete-list reader terminates without spending a swipe.
            val cells = parseSparkRowCells(sampler("keep_confirmation_plain"), SPARKS_CONFIRM_GEOMETRY, image("keep_confirmation_plain").height)
            assertTrue(cells.size < SPARKS_CONFIRM_GEOMETRY.maxRows, "an end marker must be reachable inside the window")
        }

        @Test
        fun `its set is longer than the SPARKS screen window could show`() {
            // The same career's SPARKS screen shows only its first rows; the dialog is the
            // authoritative full list. This is why the kept record is read from the dialog.
            val onSparksGeometry = parseSparkRowCells(sampler("keep_confirmation_plain"), SPARKS_SCREEN_GEOMETRY, 1920)
            assertTrue(onSparksGeometry.size <= SPARKS_SCREEN_GEOMETRY.maxRows)
            assertTrue(liveKeptSet.size > SPARKS_SCREEN_GEOMETRY.maxRows - 1)
        }
    }

    @Nested
    @DisplayName("the 2026-07-21 star-undercount false block (keep_confirmation_medium3)")
    inner class KeepConfirmationMedium3 {
        /** The 6-row set the live career rolled and kept, in order (see PROVENANCE.md). */
        private val liveSet =
            listOf(
                SparkRowKind.STAT to 2,
                SparkRowKind.APTITUDE to 3,
                SparkRowKind.UNIQUE to 2,
                SparkRowKind.WHITE to 1,
                SparkRowKind.WHITE to 1,
                SparkRowKind.WHITE to 1,
            )

        @Test
        fun `the Medium row reads three stars - the exact count the live scan dropped`() {
            val cells = parseSparkRowCells(sampler("keep_confirmation_medium3"), SPARKS_CONFIRM_GEOMETRY, image("keep_confirmation_medium3").height)
            assertEquals(SparkRowKind.APTITUDE to 3, cells[1].kind to cells[1].stars, "the 2026-07-21 block undercounted this row as 2*")
        }

        @Test
        fun `all six rows match the screenshot, with the phantom tail past the set starless`() {
            val cells = parseSparkRowCells(sampler("keep_confirmation_medium3"), SPARKS_CONFIRM_GEOMETRY, image("keep_confirmation_medium3").height)
            assertEquals(liveSet, cells.take(6).map { it.kind to it.stars })
            assertTrue(cells.size > 6 && cells[6].stars == 0, "the shrink-wrapped dialog body past the real set must stay starless")
        }

        @Test
        fun `every slot of the live set reads with high confidence - no ambiguity on a settled frame`() {
            val rows = parseSparkRowCellsWithEvidence(sampler("keep_confirmation_medium3"), SPARKS_CONFIRM_GEOMETRY, image("keep_confirmation_medium3").height)
            assertEquals(listOf(2, 3, 2, 1, 1, 1), rows.take(6).map { it.filledCount })
            assertEquals(0, rows.take(6).sumOf { it.ambiguousCount }, "centered sampling must leave no slot ambiguous on a settled frame")
            assertEquals(listOf(SparkSlotRead.FILLED, SparkSlotRead.FILLED, SparkSlotRead.FILLED), rows[1].slots.map { it.read })
        }

        @Test
        fun `the fixture is straight RGB, not a BGR-swapped bot save`() {
            // The STAT bar is sky blue: blue-dominant in RGB. A BGR-swapped file (what the
            // bot's own nav_failure camera writes) would read it red-dominant and fail here.
            val p = image("keep_confirmation_medium3").getRGB(770, 315)
            val r = (p shr 16) and 0xFF
            val b = p and 0xFF
            assertTrue(b > 240 && r < 150, "expected a blue-dominant STAT bar in RGB order, got r=$r b=$b")
        }

        @Test
        fun `it wears the confirmation chrome and no other probe fires`() {
            val s = sampler("keep_confirmation_medium3")
            assertTrue(sparkConfirmationStructurePresent(s))
            assertFalse(sparkPagerStructurePresent(s))
            assertFalse(sparkIntroStructurePresent(s))
            assertFalse(sparkRerolledStructurePresent(s))
        }
    }

    @Nested
    @DisplayName("the 2026-07-25 Grand Concert keep block (keep_confirmation_guts2)")
    inner class KeepConfirmationGuts2 {
        /**
         * The 3-row set a failed Grand Concert career rolled, captured by adb while the live bot sat
         * blocked on this dialog. The Sparks screen had already read it as Guts 2* / Pace Chaser 1* /
         * Shooting for Victory! 1*, but the keep dialog's own read returned the first row as 0 stars,
         * so the chooser refused to confirm and the run stopped with the career unfinalized. A short
         * 3-row set shrink-wraps the dialog body, which is the geometry that undercount favours.
         */
        private val liveSet =
            listOf(
                SparkRowKind.STAT to 2,
                SparkRowKind.APTITUDE to 1,
                SparkRowKind.UNIQUE to 1,
            )

        @Test
        fun `the Guts row reads two stars - the exact count the live block dropped to zero`() {
            val cells = parseSparkRowCells(sampler("keep_confirmation_guts2"), SPARKS_CONFIRM_GEOMETRY, image("keep_confirmation_guts2").height)
            assertEquals(SparkRowKind.STAT to 2, cells[0].kind to cells[0].stars, "the live block read this row as stat/0*")
        }

        @Test
        fun `all three rows match the screenshot, with the shrink-wrapped tail starless`() {
            val cells = parseSparkRowCells(sampler("keep_confirmation_guts2"), SPARKS_CONFIRM_GEOMETRY, image("keep_confirmation_guts2").height)
            assertEquals(liveSet, cells.take(3).map { it.kind to it.stars })
            assertTrue(cells.size > 3 && cells[3].stars == 0, "the empty dialog body past a 3-row set must stay starless")
        }

        @Test
        fun `every slot of the live set reads with high confidence - no ambiguity on a settled frame`() {
            val rows = parseSparkRowCellsWithEvidence(sampler("keep_confirmation_guts2"), SPARKS_CONFIRM_GEOMETRY, image("keep_confirmation_guts2").height)
            assertEquals(listOf(2, 1, 1), rows.take(3).map { it.filledCount })
            assertEquals(0, rows.take(3).sumOf { it.ambiguousCount }, "centered sampling must leave no slot ambiguous on a settled frame")
        }

        @Test
        fun `the fixture is straight RGB, not a BGR-swapped bot save`() {
            val p = image("keep_confirmation_guts2").getRGB(770, 315)
            val r = (p shr 16) and 0xFF
            val b = p and 0xFF
            assertTrue(b > 200 && r < 150, "expected a blue-dominant STAT bar in RGB order, got r=$r b=$b")
        }

        @Test
        fun `it wears the confirmation chrome and no other probe fires`() {
            val s = sampler("keep_confirmation_guts2")
            assertTrue(sparkConfirmationStructurePresent(s))
            assertFalse(sparkPagerStructurePresent(s))
            assertFalse(sparkIntroStructurePresent(s))
            assertFalse(sparkRerolledStructurePresent(s))
        }
    }

    @Nested
    @DisplayName("star-slot centering and confidence")
    inner class StarSlots {
        /** Gold-run centers along one row's star band, using the production gold thresholds
         * on raw pixels. */
        private fun goldRunCenters(name: String, rowY: Int): List<Int> {
            val img = image(name)
            val centers = mutableListOf<Int>()
            var start = -1
            for (x in 810..1004) {
                val p = img.getRGB(x, rowY)
                val gold = ((p shr 16) and 0xFF) > 200 && (p and 0xFF) < 150
                if (gold && start < 0) start = x
                if (!gold && start >= 0) {
                    if (x - start >= 8) centers.add((start + x - 1) / 2)
                    start = -1
                }
            }
            return centers
        }

        @Test
        fun `the confirmation star columns sit on the measured glyph centers, on every confirmation-family fixture`() {
            // The original calibration sampled the glyphs' last gold column (855/901/947), so
            // a filled star's smoothed mean was half background and one live frame undercounted
            // Medium 3* as 2* (2026-07-21). This pins the columns to the measured run centers.
            val threeStarRows =
                listOf(
                    "keep_confirmation_medium3" to (SPARKS_CONFIRM_GEOMETRY.firstRowY + 1 * SPARKS_CONFIRM_GEOMETRY.rowPitch),
                    "confirmation_original" to (SPARKS_CONFIRM_GEOMETRY.firstRowY + 4 * SPARKS_CONFIRM_GEOMETRY.rowPitch),
                )
            for ((fixture, rowY) in threeStarRows) {
                val centers = goldRunCenters(fixture, rowY)
                assertEquals(3, centers.size, "$fixture: expected a 3-star row at y=$rowY")
                for ((center, configured) in centers.zip(SPARKS_CONFIRM_GEOMETRY.starXs)) {
                    assertTrue(kotlin.math.abs(center - configured) <= 3, "$fixture: configured column $configured is off the measured glyph center $center")
                }
            }
            val twoStar = goldRunCenters("keep_confirmation_plain", SPARKS_CONFIRM_GEOMETRY.firstRowY + 7 * SPARKS_CONFIRM_GEOMETRY.rowPitch)
            assertEquals(2, twoStar.size, "keep_confirmation_plain: expected a 2-star row")
            for ((center, configured) in twoStar.zip(SPARKS_CONFIRM_GEOMETRY.starXs)) {
                assertTrue(kotlin.math.abs(center - configured) <= 3, "keep_confirmation_plain: configured column $configured vs measured center $center")
            }
        }

        @Test
        fun `slot classification separates the measured populations with an ambiguous band between`() {
            // Measured means: filled glyph center (255,216,78); empty slot (231,227,223); the
            // 2026-07-21 edge-of-glyph read (220,186,88); a dimmed filled center.
            assertEquals(SparkSlotRead.FILLED, classifyStarSlot(255, 78))
            assertEquals(SparkSlotRead.EMPTY, classifyStarSlot(231, 223))
            assertEquals(SparkSlotRead.EMPTY, classifyStarSlot(202, 197))
            assertEquals(SparkSlotRead.AMBIGUOUS, classifyStarSlot(220, 88), "the live edge read must grade as evidence for a retry, never a silent lower count")
            assertEquals(SparkSlotRead.AMBIGUOUS, classifyStarSlot(229, 70), "a dimmed filled center must not read as a confident empty")
        }
    }

    @Nested
    @DisplayName("state classification: each screen matches exactly its own structural probe")
    inner class Classification {
        private fun matrix(name: String): List<Boolean> {
            val s = sampler(name)
            return listOf(
                sparkPagerStructurePresent(s),
                sparkConfirmationStructurePresent(s),
                sparkIntroStructurePresent(s),
                sparkRerolledStructurePresent(s),
            )
        }

        @Test
        fun `every fixture matches exactly the expected probes and no other`() {
            val expected =
                mapOf(
                    "sparks_screen" to listOf(false, false, false, false),
                    "confirm_reroll_dialog" to listOf(false, false, false, false),
                    "sparks_rerolled_result" to listOf(false, false, false, true),
                    "spark_selection_intro" to listOf(false, false, true, false),
                    "pager_rerolled" to listOf(true, false, false, false),
                    "pager_original" to listOf(true, false, false, false),
                    "confirmation_original" to listOf(false, true, false, false),
                    "umamusume_details" to listOf(false, false, false, false),
                    "rating_record" to listOf(false, false, false, false),
                    // Shares the confirmation chrome with the post-reroll selection dialog on
                    // purpose: only the pill text and the transaction separate the two.
                    "keep_confirmation_plain" to listOf(false, true, false, false),
                    "keep_confirmation_medium3" to listOf(false, true, false, false),
                    "keep_confirmation_guts2" to listOf(false, true, false, false),
                )
            for (name in allFixtures) {
                assertEquals(expected[name], matrix(name), "probe matrix (pager/confirmation/intro/rerolled) for $name")
            }
        }

        @Test
        fun `the post-selection boundary screens match no spark probe at all`() {
            // These are the frames the generic POST_RUN_RESULTS chain must keep owning: a spark
            // probe firing here would steal ordinary post-career navigation.
            for (name in listOf("umamusume_details", "rating_record")) {
                assertEquals(listOf(false, false, false, false), matrix(name), name)
            }
        }

        @Test
        fun `the spend dialog is never mistaken for the intro dialog`() {
            // Same green-banner dialog family; the intro probe must reject the spend dialog
            // (its card ends higher and its green spend button sits at (778, 1252)). Tapping
            // "Next" coordinates on the spend dialog could dismiss it and desync the spend.
            assertFalse(sparkIntroStructurePresent(sampler("confirm_reroll_dialog")))
        }

        @Test
        fun `the SPARKS screen itself matches neither the rerolled-result nor the pager probe`() {
            // SPARKS is detected earlier via its Reroll Sparks button; these probes must still
            // reject it outright (its bottom row differs: buttons at x=777, whitish at x=540).
            assertFalse(sparkRerolledStructurePresent(sampler("sparks_screen")))
            assertFalse(sparkPagerStructurePresent(sampler("sparks_screen")))
        }
    }

    @Nested
    @DisplayName("row geometry, bar kinds, and star counts")
    inner class Rows {
        private fun cells(fixture: String, geometry: SparkListGeometry) = parseSparkRowCells(sampler(fixture), geometry, image(fixture).height)

        private fun kindsAndStars(fixture: String, geometry: SparkListGeometry) = cells(fixture, geometry).map { it.kind to it.stars }

        private val originalLead = listOf(SparkRowKind.STAT to 2, SparkRowKind.APTITUDE to 2, SparkRowKind.UNIQUE to 1)
        private val originalWhites = listOf(1, 3, 2, 2, 1, 2, 2)
        private val rerolledSet =
            listOf(
                SparkRowKind.STAT to 2,
                SparkRowKind.APTITUDE to 1,
                SparkRowKind.UNIQUE to 2,
                SparkRowKind.WHITE to 1,
                SparkRowKind.WHITE to 2,
                SparkRowKind.WHITE to 1,
                SparkRowKind.WHITE to 1,
            )

        @Test
        fun `the SPARKS screen shows 9 full rows - more than the old six-row cap ever read`() {
            val parsed = kindsAndStars("sparks_screen", SPARKS_SCREEN_GEOMETRY)
            assertEquals(9, parsed.size, "the visible window holds 9 full rows (row 10 renders clipped)")
            assertEquals(originalLead + originalWhites.take(6).map { SparkRowKind.WHITE to it }, parsed)
            assertTrue(parsed.size > 6, "the fixture proves maxRows=6 truncated real sets")
        }

        @Test
        fun `the Sparks Rerolled result parses the complete 7-row rerolled set with its end marker in view`() {
            val parsed = cells("sparks_rerolled_result", SPARKS_SCREEN_GEOMETRY)
            assertEquals(rerolledSet, parsed.map { it.kind to it.stars })
            assertTrue(parsed.size < SPARKS_SCREEN_GEOMETRY.maxRows, "the grid break inside the window is the end-of-list proof")
        }

        @Test
        fun `the pager pages parse on their own 120px-pitch geometry`() {
            assertEquals(rerolledSet, kindsAndStars("pager_rerolled", SPARK_PAGER_GEOMETRY))
            val original = kindsAndStars("pager_original", SPARK_PAGER_GEOMETRY)
            assertEquals(8, original.size, "8 of the 10 original rows fit the pager window - scrolling is required")
            assertEquals(originalLead + originalWhites.take(5).map { SparkRowKind.WHITE to it }, original)
        }

        @Test
        fun `the pager and the rerolled result agree on the rerolled set across different geometries`() {
            assertEquals(
                kindsAndStars("sparks_rerolled_result", SPARKS_SCREEN_GEOMETRY),
                kindsAndStars("pager_rerolled", SPARK_PAGER_GEOMETRY),
            )
        }

        @Test
        fun `the Confirmation dialog lists the complete 10-row original set`() {
            val parsed = cells("confirmation_original", SPARKS_CONFIRM_GEOMETRY)
            // The dialog shrink-wraps: 10 real rows plus one starless slot past the end that
            // the OCR-side name break trims (the phantom-tail rule).
            assertEquals(11, parsed.size)
            assertEquals(originalLead + originalWhites.map { SparkRowKind.WHITE to it }, parsed.take(10).map { it.kind to it.stars })
            assertEquals(0, parsed[10].stars, "the slot past the real set is starless")
        }

        @Test
        fun `the band-aligned parser agrees with the fixed grid on every scrollable capture`() {
            // Scrolled frames re-anchor the grid on the detected star-column offset; on the
            // unscrolled captures that offset must be tiny and the aligned parse identical.
            // The Confirmation dialog is deliberately absent: it shrink-wraps its whole set
            // onto one screen and is only ever read on the fixed grid.
            val listFixtures =
                listOf(
                    "sparks_screen" to SPARKS_SCREEN_GEOMETRY,
                    "sparks_rerolled_result" to SPARKS_SCREEN_GEOMETRY,
                    "pager_rerolled" to SPARK_PAGER_GEOMETRY,
                    "pager_original" to SPARK_PAGER_GEOMETRY,
                )
            for ((fixture, geometry) in listFixtures) {
                val height = image(fixture).height
                // The star glyphs sit up to ~7 px off the nominal grid line on the real
                // captures; what matters is that the re-anchored parse stays identical.
                val offset = sparkRowGridOffset(sampler(fixture), geometry, height)
                assertTrue(offset != null && offset in -12..12, "$fixture: expected a small grid offset, got $offset")
                val aligned = parseSparkRowCellsAligned(sampler(fixture), geometry, height)
                assertEquals(
                    parseSparkRowCells(sampler(fixture), geometry, height).map { it.kind to it.stars },
                    aligned?.map { it.kind to it.stars },
                    fixture,
                )
            }
        }

        @Test
        fun `a shifted grid is re-anchored instead of misread`() {
            // Simulate a swipe that settled 40 px short: shift the whole frame and require the
            // aligned parser to recover the exact same rows.
            val shift = 40
            val base = image("pager_original")
            val shifted = SparkPixelSampler { x, y -> base.getRGB(x, (y - shift).coerceIn(0, base.height - 1)) }
            val offset = sparkRowGridOffset(shifted, SPARK_PAGER_GEOMETRY, base.height)
            assertTrue(offset != null && offset in (shift - 4)..(shift + 4), "the offset must recover the simulated settle error, got $offset")
            val aligned = parseSparkRowCellsAligned(shifted, SPARK_PAGER_GEOMETRY, base.height)
            assertEquals(
                parseSparkRowCells(sampler("pager_original"), SPARK_PAGER_GEOMETRY, base.height).map { it.kind to it.stars }.take(7),
                aligned?.map { it.kind to it.stars }?.take(7),
            )
        }

        @Test
        fun `every real spark list leads stat then aptitude then unique`() {
            assertTrue(sparkCellsLeadCorrectly(cells("sparks_screen", SPARKS_SCREEN_GEOMETRY)))
            assertTrue(sparkCellsLeadCorrectly(cells("sparks_rerolled_result", SPARKS_SCREEN_GEOMETRY)))
            assertTrue(sparkCellsLeadCorrectly(cells("pager_rerolled", SPARK_PAGER_GEOMETRY)))
            assertTrue(sparkCellsLeadCorrectly(cells("pager_original", SPARK_PAGER_GEOMETRY)))
            assertTrue(sparkCellsLeadCorrectly(cells("confirmation_original", SPARKS_CONFIRM_GEOMETRY)))
            assertFalse(sparkCellsLeadCorrectly(cells("umamusume_details", SPARKS_SCREEN_GEOMETRY)))
        }

        @Test
        fun `the ten-row original set is read identically on all three screens that show it`() {
            val full = originalLead + originalWhites.map { SparkRowKind.WHITE to it }
            val sparksScreen = kindsAndStars("sparks_screen", SPARKS_SCREEN_GEOMETRY)
            val pager = kindsAndStars("pager_original", SPARK_PAGER_GEOMETRY)
            val dialog = kindsAndStars("confirmation_original", SPARKS_CONFIRM_GEOMETRY).take(10)
            assertEquals(full.take(9), sparksScreen)
            assertEquals(full.take(8), pager)
            assertEquals(full, dialog)
        }
    }

    @Nested
    @DisplayName("page indicator and chevrons")
    inner class PagerSignals {
        @Test
        fun `the lit page dot distinguishes the two pager pages`() {
            assertEquals(1, sparkPagerActiveDotIndex(sampler("pager_rerolled")), "page 1 = Rerolled Sparks")
            assertEquals(2, sparkPagerActiveDotIndex(sampler("pager_original")), "page 2 = Original Sparks")
        }

        @Test
        fun `no other screen shows a lit page dot`() {
            for (name in allFixtures.filterNot { it.startsWith("pager_") }) {
                assertNull(sparkPagerActiveDotIndex(sampler(name)), name)
            }
        }

        @Test
        fun `chevrons are detected on both pager pages and nowhere else`() {
            for (name in allFixtures) {
                assertEquals(name.startsWith("pager_"), sparkPagerChevronsPresent(sampler(name)), name)
            }
        }
    }

    @Nested
    @DisplayName("anchors and OCR regions")
    inner class Anchors {
        private fun rgbAt(name: String, x: Int, y: Int): Triple<Int, Int, Int> {
            val p = image(name).getRGB(x, y)
            return Triple((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
        }

        @Test
        fun `every OCR region stays inside the 1080x1920 frame`() {
            for (region in listOf(
                SPARK_PAGER_HEADING_OCR_REGION,
                SPARK_REROLLED_TITLE_OCR_REGION,
                SPARK_INTRO_TITLE_OCR_REGION,
                SPARK_CONFIRMATION_TITLE_OCR_REGION,
                SPARK_CONFIRMATION_SET_NAME_OCR_REGION,
            )) {
                assertTrue(region[0] >= 0 && region[1] >= 0)
                assertTrue(region[0] + region[2] <= 1080, "width overflow")
                assertTrue(region[1] + region[3] <= 1920, "height overflow")
            }
        }

        @Test
        fun `the confirmation set-name region contains the green pill on the confirmation fixture`() {
            // The pill is solid green under the white set-name text; a drifted region would
            // sample the white card instead.
            val (r, g, b) = rgbAt("confirmation_original", 540, SPARK_CONFIRMATION_SET_NAME_OCR_REGION[1] + SPARK_CONFIRMATION_SET_NAME_OCR_REGION[3] / 2)
            assertTrue(g >= 150 && g - r >= 60 && g - b >= 100, "expected pill green at the region center, got ($r,$g,$b)")
        }

        @Test
        fun `the confirmation Cancel and Confirm anchors sit on their buttons`() {
            val (cr, cg, cb) = rgbAt("confirmation_original", SPARK_CONFIRMATION_CONFIRM_X, SPARK_CONFIRMATION_CONFIRM_Y - 30)
            assertTrue(cg >= 180 && cg - cb >= 80, "Confirm anchor not on green, got ($cr,$cg,$cb)")
            val (kr, kg, kb) = rgbAt("confirmation_original", SPARK_CONFIRMATION_CANCEL_X, SPARK_CONFIRMATION_CANCEL_Y - 30)
            assertTrue(kr >= 200 && kg >= 200 && kb >= 200, "Cancel anchor not on the white button, got ($kr,$kg,$kb)")
        }

        @Test
        fun `the pager Confirm anchor column is button green at the probe row on both pages`() {
            for (name in listOf("pager_rerolled", "pager_original")) {
                val (r, g, b) = rgbAt(name, SPARK_PAGER_CONFIRM_X, SPARK_WIDE_BUTTON_PROBE_Y)
                assertTrue(g >= 180 && g - b >= 80, "$name: expected button green at the probe row, got ($r,$g,$b)")
            }
        }

        @Test
        fun `fixture frames are the supported 1080x1920 capture size`() {
            for (name in allFixtures) {
                assertEquals(1080, image(name).width, name)
                assertEquals(1920, image(name).height, name)
            }
        }
    }
}
