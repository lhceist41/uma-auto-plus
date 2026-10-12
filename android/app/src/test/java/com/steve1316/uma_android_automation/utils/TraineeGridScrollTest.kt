package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Synthetic-frame tests for the Trainee Select cross-frame scroll measurement. Real consecutive
 * swipe frames are not captured anywhere (the measurement exists precisely because the swipe
 * outcome was unobserved), so the math is pinned on generated texture: a deterministic
 * pseudo-noise pattern whose vertically shifted copy IS the scrolled frame, plus the degenerate
 * cases (no texture, unrelated content) that must refuse to answer rather than guess.
 */
@DisplayName("Trainee grid scroll measurement")
class TraineeGridScrollTest {
    private val w = 1080
    private val h = 1920

    /** Deterministic textured pattern with structure at several scales, so the cost surface has a
     * sharp minimum at the true shift like real tile art does. */
    private fun texel(x: Int, y: Int): Int {
        val n = (x * 73856093) xor (y * 19349663)
        val coarse = ((y / 64) * 31 + (x / 96) * 17) and 0x7F
        val v = ((n ushr 9) and 0x7F) + coarse
        val g = v.coerceIn(0, 255)
        return (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }

    private fun frame(shift: Int, jitter: Int = 0): SparkPixelSampler =
        SparkPixelSampler { x, y ->
            val base = texel(x, y + shift)
            if (jitter == 0) {
                base
            } else {
                val j = (((x * 40503) xor ((y + shift) * 30011)) % (2 * jitter + 1)) - jitter
                val g = (((base shr 8) and 0xFF) + j).coerceIn(0, 255)
                (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }

    @Test
    fun `an unmoved frame measures zero`() {
        assertEquals(0, TraineeGridScroll.measureDeltaPx(frame(0), frame(0), w, h))
    }

    @Test
    fun `a one-row advance is measured exactly`() {
        val rowPx = 190
        assertEquals(rowPx, TraineeGridScroll.measureDeltaPx(frame(0), frame(rowPx), w, h))
    }

    @Test
    fun `a half-row advance and a swipe near the overlap limit are both measured`() {
        assertEquals(96, TraineeGridScroll.measureDeltaPx(frame(0), frame(96), w, h))
        assertEquals(330, TraineeGridScroll.measureDeltaPx(frame(0), frame(330), w, h))
    }

    @Test
    fun `a shift that leaves too little band overlap refuses to answer`() {
        assertNull(TraineeGridScroll.measureDeltaPx(frame(0), frame(380), w, h))
    }

    @Test
    fun `a small bounce-back (negative shift) is measured`() {
        assertEquals(-20, TraineeGridScroll.measureDeltaPx(frame(0), frame(-20), w, h))
    }

    @Test
    fun `render noise does not break the measurement`() {
        val d = TraineeGridScroll.measureDeltaPx(frame(0), frame(190, jitter = 3), w, h)
        assertNotNull(d)
        assertTrue(Math.abs(d!! - 190) <= TraineeGridScroll.SAMPLE_STRIDE, "measured $d for a true 190px shift")
    }

    @Test
    fun `a textureless frame refuses to answer`() {
        val flat = SparkPixelSampler { _, _ -> (0xFF shl 24) or (200 shl 16) or (200 shl 8) or 200 }
        assertNull(TraineeGridScroll.measureDeltaPx(flat, flat, w, h))
    }

    @Test
    fun `unrelated content refuses to answer`() {
        val other = SparkPixelSampler { x, y -> texel(x * 7 + 13, y * 3 + 5) }
        assertNull(TraineeGridScroll.measureDeltaPx(frame(0), other, w, h))
    }

    @Test
    fun `a shift beyond the search range refuses to answer rather than aliasing`() {
        // 0.23 x 1920 = 441 is the search ceiling; a 500px shift must not come back as some
        // in-range impostor.
        assertNull(TraineeGridScroll.measureDeltaPx(frame(0), frame(500), w, h))
    }

    /** Real MuMu frames (`fixtures/rostergrid/PROVENANCE.md`): synthetic texture cannot catch the fixed Filters/Name/Asc overlay inside the band. */
    @Nested
    @DisplayName("real roster swipes")
    inner class RealSwipes {
        private val firstY = 1000
        private val lastY = 1600
        private val probeXs = intArrayOf(340, 540, 739)

        private val strip = requireNotNull(javaClass.getResourceAsStream("/fixtures/rostergrid/swipe_probe_columns.png")).use { FixturePng.read(it) }

        private fun frameAt(index: Int): SparkPixelSampler =
            SparkPixelSampler { x, y ->
                val col = probeXs.indexOf(x)
                require(col >= 0 && y in firstY..lastY) { "fixture holds only the probe columns of y $firstY..$lastY, asked ($x, $y)" }
                strip.getRGB(index * probeXs.size + col, y - firstY)
            }

        private fun before(swipe: Int) = frameAt(2 * (swipe - 1))

        private fun after(swipe: Int) = frameAt(2 * (swipe - 1) + 1)

        @Test
        fun `every page swipe is measured at the content movement`() {
            val expected = intArrayOf(292, 286, 288, 274, 274)
            for (swipe in 1..5) {
                assertEquals(expected[swipe - 1], TraineeGridScroll.measureDeltaPx(before(swipe), after(swipe), w, h), "swipe $swipe")
            }
        }

        @Test
        fun `a page that did not move reads as no movement`() {
            for (swipe in 1..4) {
                assertEquals(0, TraineeGridScroll.measureDeltaPx(after(swipe), before(swipe + 1), w, h), "pair $swipe")
            }
        }
    }

    /** Column-0 strips of Trainee Select (`fixtures/rostergrid/PROVENANCE.md`): at the end of the list the game parks the last row
     * on the bottom band, which on the 1080x2316 phone (inset 94) sits below both middle-band scan rows. */
    @Nested
    @DisplayName("parked last roster row")
    inner class ParkedLastRow {
        private val phoneHeight = 2316
        private val phoneInset = 94

        /** The navigator's two scan rows (traineeRow0Fraction 0.585, traineeRowStepFraction 0.099) on the middle band. */
        private fun scanRowYs(height: Int, inset: Int): List<Double> =
            (0..1).map { row -> gameY((1920 * (0.585f + row * 0.099f)).toDouble(), ScreenBand.MIDDLE, w, height, inset) }

        private fun scanYs(height: Int, inset: Int): List<Double> {
            val rows = scanRowYs(height, inset)
            return rows + listOfNotNull(TraineeGridScroll.parkedLastRowTapY(w, height, rows.last(), inset))
        }

        /** Card rows wholly inside the strip and above the Filters bar, in screen y. A strip row is card when under 60% of it is
         * plain light background; light gaps up to 16px stay inside one card (the gap between cards is ~33px). */
        private fun cardRows(resource: String, stripTop: Int, filtersBarTop: Int): List<IntRange> {
            val strip = requireNotNull(javaClass.getResourceAsStream("/fixtures/rostergrid/$resource")).use { FixturePng.read(it) }
            val runs = ArrayList<IntArray>()
            for (y in 0 until strip.height) {
                var light = 0
                for (x in 0 until strip.width) {
                    val p = strip.getRGB(x, y)
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    if (minOf(r, g, b) > 215 && maxOf(r, g, b) - minOf(r, g, b) < 25) light++
                }
                if (light >= strip.width * 0.6) continue
                val last = runs.lastOrNull()
                if (last != null && y - last[1] <= 16) last[1] = y else runs.add(intArrayOf(y, y))
            }
            return runs
                .filter { it[1] - it[0] >= 150 && it[0] > 0 && it[1] < strip.height - 1 }
                .map { (it[0] + stripTop)..(it[1] + stripTop) }
                .filter { it.last < filtersBarTop }
        }

        /** Inside the card with the tap jitter (+-6px) to spare. */
        private fun tapped(row: IntRange, ys: List<Double>) = ys.any { it >= row.first + 6 && it <= row.last - 6 }

        @Test
        fun `on the tall phone the parked last row lies below both scan rows`() {
            val rows = cardRows("tall_bottom_col0.png", stripTop = 1150, filtersBarTop = 1854)
            assertEquals(2, rows.size, "card rows found: $rows")
            assertFalse(tapped(rows.last(), scanRowYs(phoneHeight, phoneInset)), "parked row ${rows.last()} was already reachable")
        }

        @Test
        fun `every fully visible row of the parked tall roster is tapped`() {
            val ys = scanYs(phoneHeight, phoneInset)
            for (row in cardRows("tall_bottom_col0.png", stripTop = 1150, filtersBarTop = 1854)) {
                assertTrue(tapped(row, ys), "row $row untapped by $ys")
            }
        }

        @Test
        fun `the top of the tall roster is still read by the two scan rows`() {
            val rows = cardRows("tall_top_col0.png", stripTop = 1150, filtersBarTop = 1851)
            assertEquals(2, rows.size, "card rows found: $rows")
            for (row in rows) assertTrue(tapped(row, scanRowYs(phoneHeight, phoneInset)), "row $row untapped")
        }

        @Test
        fun `on 1080x1920 the scan rows already read the parked row and no extra row is tapped`() {
            assertNull(TraineeGridScroll.parkedLastRowTapY(w, h, scanRowYs(h, 0).last(), 0))
            val rows = cardRows("mumu_bottom_col0.png", stripTop = 950, filtersBarTop = 1458)
            assertEquals(2, rows.size, "card rows found: $rows")
            for (row in rows) assertTrue(tapped(row, scanRowYs(h, 0)), "row $row untapped")
        }

        @Test
        fun `the parked row tap stays clear of the Filters bar on common tall screens`() {
            for (height in intArrayOf(2160, 2316, 2340, 2400)) {
                for (inset in intArrayOf(0, 94)) {
                    val y = TraineeGridScroll.parkedLastRowTapY(w, height, scanRowYs(height, inset).last(), inset) ?: continue
                    val filtersBarTop = gameY(1458.0, ScreenBand.BOTTOM, w, height, inset)
                    assertTrue(filtersBarTop - y >= 100, "${w}x$height inset $inset: tap $y vs Filters bar $filtersBarTop")
                }
            }
            for (height in intArrayOf(2316, 2340, 2400)) {
                assertNotNull(TraineeGridScroll.parkedLastRowTapY(w, height, scanRowYs(height, phoneInset).last(), phoneInset), "${w}x$height")
            }
        }

        @Test
        fun `an unmapped surface keeps the old scan`() {
            assertNull(TraineeGridScroll.parkedLastRowTapY(1440, 3088, 2112.0, 125))
        }
    }

    @Nested
    @DisplayName("navigator wiring")
    inner class SourceGuard {
        private fun navigatorSource(): String {
            var dir: File? = File(System.getProperty("user.dir") ?: ".")
            repeat(5) {
                val a = File(dir, "src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
                if (a.isFile) return a.readText()
                val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
                if (b.isFile) return b.readText()
                dir = dir?.parentFile
            }
            error("could not locate CareerLaunchNavigator.kt")
        }

        /** The three behaviors the measurement exists for: the scan advances by measured rows, the
         * remembered-position jump verifies its page swipes, and a scan remembers every trainee it
         * read. Each is one call a refactor could drop with no compile error. */
        @Test
        fun `the scan and the jump both use measured swipes and scans remember every trainee`() {
            val nav = navigatorSource()
            assertTrue(nav.contains("swipeTraineeGridMeasured(pageDown = true)"), "measured swipes left the navigator")
            assertEquals(
                2,
                Regex("swipeTraineeGridMeasured\\(pageDown = true\\)").findAll(nav).count(),
                "both the scan loop and the remembered-position jump must swipe measured",
            )
            assertTrue(nav.contains("TraineePositionStore.putAll(context, discoveredCells)"), "the roster-wide position save left the scan")
        }

        /** A swipe moves ~1.2 tile rows (274-292px live) against 190px between the scan's tap rows, so a skipped row can leave a tile row untapped. */
        @Test
        fun `every roster page is scanned from row 0 whatever the swipe measured`() {
            val nav = navigatorSource()
            assertFalse(nav.contains("RosterScanPolicy.nextStartRow("), "the scan consumes a measured advance to skip rows again")
            assertTrue(Regex("""\n\s+val startRow = 0\n\s+for \(page in 0\.\.traineeMaxSwipes\)""").containsMatchIn(nav), "the scan's start row is no longer fixed at 0")
            assertTrue(nav.contains("for (row in startRow until traineeGridRows)"), "the scan loop no longer starts at the start row")
        }

        /** On tall screens the parked last row is below both scan rows; dropping this read makes it unpickable (live 2026-10-11). */
        @Test
        fun `a scan that ends without a match reads the parked last row through the same cell path`() {
            val nav = navigatorSource()
            assertTrue(
                nav.contains("TraineeGridScroll.parkedLastRowTapY(scanBitmap.width, scanBitmap.height, lowestScanRowY)"),
                "the scan no longer asks for the parked last row",
            )
            assertTrue(
                nav.contains("readAndScoreCell(scanBitmap, lastPage, col, traineeGridRows, parkedY)?.let { return it }"),
                "the parked last row is no longer read through the scan's cell path",
            )
            assertTrue(nav.contains("readAndScoreCell(bitmap, page, col, row)?.let { return it }"), "the page rows left the shared cell path")
        }

        /** The re-anchored second pass re-reads names the first pass already holds; counted against those, it stopped on page 1
         * and its parked-row read ran mid-list. */
        @Test
        fun `the second pass judges the roster end by names new in that pass`() {
            val nav = navigatorSource()
            assertTrue(nav.contains("if (passNames.add(norm) && !excluded) newCells++"), "the no-new-page check no longer counts per pass")
            assertTrue(Regex("""failedReads = 0\n\s+passNames\.clear\(\)""").containsMatchIn(nav), "the second pass no longer starts a fresh name set")
        }

        /** The trainee EVENT banner ("Trainee Event / ...") reads "Trainee" in the same header
         * band as the roster title; without the reject, a launch starting on an event dialog runs
         * a roster scan against it and taps grid cells into the event choices (live 2026-07-27). */
        @Test
        fun `the roster detector rejects the trainee event banner`() {
            val nav = navigatorSource()
            assertTrue(
                nav.contains("header.contains(\"EVENT\")"),
                "the trainee-event header reject left isTraineeSelectScreen",
            )
        }
    }
}
