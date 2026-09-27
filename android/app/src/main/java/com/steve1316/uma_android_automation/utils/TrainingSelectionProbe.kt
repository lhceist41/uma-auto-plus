package com.steve1316.uma_android_automation.utils

import kotlin.math.abs

/**
 * OCR-free probe for the in-career Training selection screen (the five training buttons, Back, the
 * Skip pill, Quick and Log). It reads two fixed parts: the "Training" lettering on the header tab and
 * the white Back pill with its brown "Back" text. Both are needed: the training-result cutscene keeps
 * the "Training" header without a Back pill, and other in-career lists (Race List, Grand Concert
 * Lessons) have the Back pill under another header. Measured on adb screencaps (true RGB) mirrored
 * under src/test/resources/fixtures/trainingselection and on the Grand Concert training fixtures.
 */
object TrainingSelectionProbe {
    // x, y, r, g, b on the 1080x1920 surface: the white "ning" of "Training" and the tab between its
    // letters, then the Back pill's white body and its brown text.
    private val HEADER =
        listOf(
            intArrayOf(126, 30, 255, 255, 255),
            intArrayOf(86, 34, 255, 255, 255),
            intArrayOf(100, 34, 255, 255, 255),
            intArrayOf(116, 44, 255, 255, 255),
            intArrayOf(122, 40, 247, 247, 248),
            intArrayOf(126, 44, 243, 243, 245),
            intArrayOf(90, 38, 97, 92, 125),
            intArrayOf(74, 38, 97, 92, 125),
            intArrayOf(104, 36, 97, 92, 125),
            intArrayOf(48, 38, 97, 92, 125),
            intArrayOf(82, 36, 97, 92, 125),
            intArrayOf(98, 40, 97, 92, 125),
        )
    private val BACK_PILL =
        listOf(
            intArrayOf(203, 1890, 255, 255, 255),
            intArrayOf(161, 1878, 255, 255, 255),
            intArrayOf(134, 1881, 252, 252, 252),
            intArrayOf(89, 1875, 255, 255, 255),
            intArrayOf(110, 1842, 121, 64, 22),
            intArrayOf(110, 1851, 121, 64, 22),
            intArrayOf(122, 1851, 121, 64, 22),
            intArrayOf(116, 1854, 142, 96, 62),
        )
    private const val TOLERANCE = 40
    private const val MIN_HEADER_HITS = 11
    private const val MIN_BACK_PILL_HITS = 7

    /** True on the Training selection screen. The points are absolute, so any other surface size reads false. */
    fun isTrainingSelection(sampler: SparkPixelSampler, width: Int, height: Int): Boolean {
        if (width != 1080 || height != 1920) return false
        return hits(sampler, HEADER) >= MIN_HEADER_HITS && hits(sampler, BACK_PILL) >= MIN_BACK_PILL_HITS
    }

    private fun hits(sampler: SparkPixelSampler, points: List<IntArray>): Int = points.count { (x, y, r, g, b) -> near(sampler.argb(x, y), r, g, b) }

    private fun near(argb: Int, r: Int, g: Int, b: Int): Boolean =
        abs(((argb shr 16) and 0xFF) - r) <= TOLERANCE &&
            abs(((argb shr 8) and 0xFF) - g) <= TOLERANCE &&
            abs((argb and 0xFF) - b) <= TOLERANCE
}
