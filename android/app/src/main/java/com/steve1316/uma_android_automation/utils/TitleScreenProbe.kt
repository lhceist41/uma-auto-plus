package com.steve1316.uma_android_automation.utils

import kotlin.math.abs

/**
 * OCR-free probe for the game's title screen ("TAP TO START"). Its background is animated, so the
 * probe reads only the two opaque controls drawn over it: the CRIWARE badge (bottom left) and the
 * round menu button (bottom right). Measured on adb screencaps (true RGB) mirrored under
 * src/test/resources/fixtures/titlescreen: every title capture read all 12 points and no other
 * capture read more than 7. The title keeps both controls while it logs in ("Entering the starting
 * gate..."), so a match does not mean the title still waits for a tap.
 */
object TitleScreenProbe {
    /** The "TAP TO START" text, clear of both corner controls. */
    const val TAP_TO_START_X = 540.0
    const val TAP_TO_START_Y = 1687.0

    // x, y, r, g, b on the 1080x1920 surface: the menu button's white rim and brown bars, then the
    // badge's white frame and blue cube.
    private val POINTS =
        listOf(
            intArrayOf(998, 1795, 255, 255, 255),
            intArrayOf(998, 1810, 255, 255, 255),
            intArrayOf(998, 1827, 187, 114, 60),
            intArrayOf(998, 1838, 255, 255, 255),
            intArrayOf(998, 1846, 156, 90, 41),
            intArrayOf(998, 1864, 124, 67, 24),
            intArrayOf(90, 1809, 255, 255, 255),
            intArrayOf(90, 1831, 3, 88, 173),
            intArrayOf(84, 1846, 0, 90, 170),
            intArrayOf(56, 1846, 255, 255, 255),
            intArrayOf(120, 1846, 255, 255, 255),
            intArrayOf(90, 1880, 255, 255, 255),
        )
    private const val TOLERANCE = 30
    private const val MIN_HITS = 11

    /** True on the title screen. The points are measured on 1080x1920; both controls sit in [ScreenBand.BOTTOM] on taller screens. */
    fun isTitleScreen(sampler: SparkPixelSampler, width: Int, height: Int): Boolean {
        if (!isMappedSurface(width, height)) return false
        val screen = sampler.onScreen(ScreenBand.BOTTOM, width, height)
        return POINTS.count { (x, y, r, g, b) -> near(screen.argb(x, y), r, g, b) } >= MIN_HITS
    }

    private fun near(argb: Int, r: Int, g: Int, b: Int): Boolean =
        abs(((argb shr 16) and 0xFF) - r) <= TOLERANCE &&
            abs(((argb shr 8) and 0xFF) - g) <= TOLERANCE &&
            abs((argb and 0xFF) - b) <= TOLERANCE
}
