package com.steve1316.uma_android_automation.utils

import kotlin.math.abs

/**
 * OCR-free structural probes for the benign reward overlays the game injects after a career
 * completes, while a limited-time event is running, on the way back to Home.
 *
 * Measured on the live 2026-08-19 capture (the failure-camera frame the navigator gave up on
 * after a COMPLETE Grand Concert career: five straight UNKNOWNs stopped the queue one step past
 * [CAREER_END]). The frame is mirrored under src/test/resources/fixtures/postcareer with a
 * provenance file, converted from the failure camera's OpenCV BGR byte order to true RGB so its
 * pixels match what the runtime [SparkPixelSampler] reads from a Bitmap. Pinned by a fixture test.
 *
 * Android-free on purpose: the runtime wraps a Bitmap in a [SparkPixelSampler] (the generic ARGB
 * pixel accessor the navigator already builds for its other structural probes), the JUnit fixture
 * wraps a decoded PNG.
 */
object PostCareerScreenProbes {
    // The event-points "REWARDS" summary is a full-screen, button-less, tap-to-advance overlay: no
    // Next/OK/Close/Confirm template and no dialog gradient, so it matches nothing in the
    // navigator's cascade and falls through to UNKNOWN. Its fingerprint is two full-width lime
    // section-header bars - one under "Event Points obtained", one under "Progress" - painted in a
    // uniform (117,201,12) that nothing else in the between-run flow spans the whole width with at
    // these rows. BOTH bars are required, so a lone green accent elsewhere cannot trip it.
    private const val HEADER_GREEN_R = 117
    private const val HEADER_GREEN_G = 201
    private const val HEADER_GREEN_B = 12

    // Bar mid-row centers as a fraction of a 1920-tall capture (header 1 ~0.224, header 2 ~0.533).
    private val HEADER_ROW_FRACTIONS = listOf(0.224, 0.533)

    // Sample columns spanning the bar interior only: the far left carries the white heading text
    // and the far right carries the "//" glyph and the rounded end, both over background.
    private val HEADER_SAMPLE_X_FRACTIONS = listOf(0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.88)

    // Allow a couple of stray interior samples per bar (an anti-aliased text pixel, a compression
    // ring) without dropping the match; the solid interior reads a clean 8/8 on the live capture.
    private const val MIN_GREEN_HITS_PER_ROW = 7

    /**
     * True when the current frame is the event-period "REWARDS" points summary. Gated on the
     * supported 1080x1920 game surface (the row/column fractions are calibrated to it), then a
     * majority green read on each of the two header bars.
     */
    fun isEventPointsRewardsSummary(sampler: SparkPixelSampler, width: Int, height: Int): Boolean {
        if (width < 1080 || height < 1840) return false
        for (rowFraction in HEADER_ROW_FRACTIONS) {
            val y = (height * rowFraction).toInt()
            var hits = 0
            for (xFraction in HEADER_SAMPLE_X_FRACTIONS) {
                val x = (width * xFraction).toInt()
                if (isHeaderGreen(sampler.argb(x, y))) hits++
            }
            if (hits < MIN_GREEN_HITS_PER_ROW) return false
        }
        return true
    }

    private fun isHeaderGreen(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return abs(r - HEADER_GREEN_R) <= 40 &&
            abs(g - HEADER_GREEN_G) <= 30 &&
            abs(b - HEADER_GREEN_B) <= 40
    }
}
