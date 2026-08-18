package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Replay tests for the pure-pixel Grand Concert "+N" gain digit reader over real training-panel
 * captures (see src/test/resources/fixtures/grandconcert/PROVENANCE.md). These pin the exact
 * amounts the reader recovers from the stylised warm glyph and - just as importantly - pin that it
 * declines to invent a number when no glyph is present, so a false read cannot slip through as the
 * OCR path's ambiguity did.
 */
@DisplayName("Grand Concert +N gain digit reader on real captures")
class GrandConcertGainDigitsTest {
    private val cache = mutableMapOf<String, FixturePng>()

    private fun image(name: String): FixturePng =
        cache.getOrPut(name) {
            val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/grandconcert/$name.png")) { "missing fixture $name.png" }
            stream.use { FixturePng.read(it) }
        }

    private fun sampler(name: String): SparkPixelSampler {
        val img = image(name)
        return SparkPixelSampler { x, y -> img.getRGB(x, y) }
    }

    /** Row index the reader is expected to read on a fixture, via the same selection the runtime uses. */
    private fun selectedRows(name: String): List<Int> = selectedTrainingPerformanceRows(sampler(name))

    @Test
    @DisplayName("Case 1: clean one-digit amount reads exactly")
    fun singleDigit() {
        assertEquals(listOf(4), selectedRows("training_panel_gain_single_digit"))
        assertEquals(7, GrandConcertGainDigits.readGainAmount(sampler("training_panel_gain_single_digit"), 4))
    }

    @Test
    @DisplayName("Case 2: clean two-digit amounts read exactly")
    fun twoDigit() {
        assertEquals(23, GrandConcertGainDigits.readGainAmount(sampler("training_panel_vi_gain"), 3))
        assertEquals(13, GrandConcertGainDigits.readGainAmount(sampler("training_guts_before"), 0))
    }

    @Test
    @DisplayName("Case 3: a two-colour facility reads each colour's amount")
    fun multiColour() {
        val rows = selectedRows("training_panel_rainbow")
        assertEquals(listOf(0, 3), rows)
        assertEquals(28, GrandConcertGainDigits.readGainAmount(sampler("training_panel_rainbow"), 0))
        assertEquals(28, GrandConcertGainDigits.readGainAmount(sampler("training_panel_rainbow"), 3))
    }

    @Test
    @DisplayName("Case 4: a glyph over warm background art still reads exactly")
    fun warmBackgroundRegression() {
        // Row 3 (Visual) here floats over warm stadium art; a plain warm-run scan fused the last
        // digit with that art. The white-outline enclosure test separates them.
        assertEquals(listOf(3), selectedRows("training_panel_gain_row3_bg"))
        assertEquals(11, GrandConcertGainDigits.readGainAmount(sampler("training_panel_gain_row3_bg"), 3))
    }

    @Test
    @DisplayName("Case 5: rows with no gain glyph return null, never a guess")
    fun noGlyphIsNull() {
        // On a single-gain frame, the four rows without a "+N" must not fabricate a number.
        for (row in intArrayOf(0, 1, 2, 4)) {
            assertNull(GrandConcertGainDigits.readGainAmount(sampler("training_panel_vi_gain"), row), "row $row")
        }
    }

    @Test
    @DisplayName("Case 6: a non-training frame yields no fabricated amounts")
    fun hiddenPanelIsNull() {
        for (row in 0..4) {
            assertNull(GrandConcertGainDigits.readGainAmount(sampler("training_panel_hidden"), row), "row $row")
        }
    }
}
