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

    /**
     * Real frames: the probe columns of five Trainee Select page swipes captured on MuMu (see
     * `fixtures/rostergrid/PROVENANCE.md`). Synthetic texture cannot catch the fixed
     * Filters/Name/Asc overlay inside the band, which is what made every real swipe unmeasurable.
     */
    @Nested
    @DisplayName("real roster swipes")
    inner class RealSwipes {
        private val firstY = 1000
        private val lastY = 1600
        private val probeXs = intArrayOf(340, 540, 739)

        private val strip = requireNotNull(javaClass.getResourceAsStream("/fixtures/rostergrid/swipe_probe_columns.png")).use { FixturePng.read(it) }

        /** Frames are stored before/after per swipe: index 2 * (swipe - 1) is its before frame, the next its after frame. */
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
            // The after frame of one swipe and the before frame of the next show the same page.
            for (swipe in 1..4) {
                assertEquals(0, TraineeGridScroll.measureDeltaPx(after(swipe), before(swipe + 1), w, h), "pair $swipe")
            }
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

        /** A measured swipe must not shorten the next page. The swipe moves ~1.2 tile rows (235px pitch,
         * 274-292px on the live frames) while the scan's two tap rows are 190px apart, so a skipped tap
         * row can leave a whole tile row untapped, which reads as the target being absent. */
        @Test
        fun `every roster page is scanned from row 0 whatever the swipe measured`() {
            val nav = navigatorSource()
            assertFalse(nav.contains("RosterScanPolicy.nextStartRow("), "the scan consumes a measured advance to skip rows again")
            assertTrue(Regex("""\n\s+val startRow = 0\n\s+for \(page in 0\.\.traineeMaxSwipes\)""").containsMatchIn(nav), "the scan's start row is no longer fixed at 0")
            assertTrue(nav.contains("for (row in startRow until traineeGridRows)"), "the scan loop no longer starts at the start row")
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
