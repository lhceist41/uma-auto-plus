package com.steve1316.uma_android_automation.utils

import kotlin.math.roundToInt

/**
 * Team Trials pixel probes, measured on the 1080x2316 phone captures (fixtures/teamtrials, cutout inset 94).
 * Band-mapped y values are the phone positions moved back through [gameY]; no MuMu capture confirms them yet.
 */
object TeamTrialsGeometry {
    /** Opponent cards are scanned on this column, left of the team-rank emblem. */
    const val CARD_SCAN_X = 60

    /** A card's white body is one 308 px run on the phone, cards 405 px apart; the game does not scale at width 1080. */
    const val CARD_MIN_RUN = 280
    const val CARD_MAX_RUN = 340
    const val CARD_MIN_PITCH = 380
    const val CARD_MAX_PITCH = 430

    /** RP pips, TOP band: phone centres y 194, so 100 at 1920. The gaps between them are white bar. */
    val RP_PIP_XS = intArrayOf(639, 685, 732, 778, 824)
    val RP_GAP_XS = intArrayOf(662, 709, 755, 801)
    const val RP_PIP_Y = 100

    /** "Quick Mode: ON" pill on the standby screen, BOTTOM band: phone x 403-673, y 1962-2021. */
    const val QUICK_MODE_X0 = 410
    const val QUICK_MODE_X1 = 668
    const val QUICK_MODE_Y = 1596

    /** Items Selected "Race!", DIALOG band: phone (771, 1560). */
    const val ITEMS_RACE_X = 771
    const val ITEMS_RACE_Y = 1362

    /** "See All Race Results", BOTTOM band: the old 1920 fallback (540, 1775); phone button x 335-745, y 2090-2250. */
    const val SEE_ALL_X = 540
    const val SEE_ALL_Y = 1775

    /** "TAP" prompt on the WIN/LOSE splash, BOTTOM band: phone (540, 1980). */
    const val SPLASH_TAP_X = 540
    const val SPLASH_TAP_Y = 1584

    /**
     * "NEW HIGH SCORE!" banner, measured on MuMu 1080x1920 frames (DIALOG band; no phone capture confirms it):
     * white rows clear of the rank and points, the green stripe rows above and below, and the title's lime fill.
     */
    val HIGH_SCORE_WHITE_YS = intArrayOf(710, 740, 890, 920)
    val HIGH_SCORE_STRIPE_YS = intArrayOf(460, 480, 1170, 1190)
    const val HIGH_SCORE_TITLE_Y0 = 660
    const val HIGH_SCORE_TITLE_Y1 = 680
    const val HIGH_SCORE_TITLE_X0 = 150
    const val HIGH_SCORE_TITLE_X1 = 930

    /** Its blinking "TAP" prompt, BOTTOM band: MuMu y 1596-1683. */
    const val HIGH_SCORE_TAP_X = 540
    const val HIGH_SCORE_TAP_Y = 1640
}

private fun red(argb: Int): Int = (argb shr 16) and 0xFF

private fun green(argb: Int): Int = (argb shr 8) and 0xFF

private fun blue(argb: Int): Int = argb and 0xFF

private fun isCardWhite(argb: Int): Boolean {
    val r = red(argb)
    val g = green(argb)
    val b = blue(argb)
    return minOf(r, g, b) >= 220 && maxOf(r, g, b) - minOf(r, g, b) <= 16
}

/**
 * Centre y of each of the three Select Opponent cards, top first, in capture pixels; null on any other screen.
 * Found by scanning, so it holds in any band.
 */
fun findOpponentCardCentres(
    sampler: SparkPixelSampler,
    width: Int,
    height: Int,
): List<Int>? {
    val scale = width / 1080.0
    val x = (TeamTrialsGeometry.CARD_SCAN_X * scale).roundToInt()
    val dx = (4 * scale).roundToInt().coerceAtLeast(1)
    val minRun = TeamTrialsGeometry.CARD_MIN_RUN * scale
    val maxRun = TeamTrialsGeometry.CARD_MAX_RUN * scale
    val runs = mutableListOf<IntRange>()
    var start = -1
    for (y in (height * 0.15).toInt() until (height * 0.85).toInt()) {
        val white = isCardWhite(sampler.argb(x - dx, y)) && isCardWhite(sampler.argb(x, y)) && isCardWhite(sampler.argb(x + dx, y))
        if (white && start < 0) start = y
        if (!white && start >= 0) {
            val length = y - start
            if (length >= minRun && length <= maxRun) runs.add(start until y)
            start = -1
        }
    }
    if (runs.size != 3) return null
    val centres = runs.map { (it.first + it.last) / 2 }
    val pitchOk = centres.zipWithNext().all { (a, b) -> b - a >= TeamTrialsGeometry.CARD_MIN_PITCH * scale && b - a <= TeamTrialsGeometry.CARD_MAX_PITCH * scale }
    return if (pitchOk) centres else null
}

/** Mean colour of a 5x5 patch. */
private fun meanArgb(
    sampler: SparkPixelSampler,
    cx: Int,
    cy: Int,
): Triple<Int, Int, Int> {
    var r = 0
    var g = 0
    var b = 0
    for (dy in -2..2) {
        for (dx in -2..2) {
            val p = sampler.argb(cx + dx, cy + dy)
            r += red(p)
            g += green(p)
            b += blue(p)
        }
    }
    return Triple(r / 25, g / 25, b / 25)
}

/** Lit pip blue, measured (16..116, 134..219, 233..255). */
private fun isPipLit(r: Int, g: Int, b: Int): Boolean = b >= 200 && b - r >= 100

/** Empty pip grey, measured (81, 76, 89) on Home and on the Team Trials home. */
private fun isPipEmpty(r: Int, g: Int, b: Int): Boolean = kotlin.math.abs(r - 81) <= 20 && kotlin.math.abs(g - 76) <= 20 && kotlin.math.abs(b - 89) <= 20

/** RP held, from the five pips in the top bar (lit ones fill from the left); null when the pips do not read cleanly. */
fun readRpPips(
    sampler: SparkPixelSampler,
    width: Int,
    height: Int,
): Int? {
    if (!isMappedSurface(width, height)) return null
    val mapped = sampler.onScreen(ScreenBand.TOP, width, height)
    // Without the white gaps, sky behind a screen with no top bar reads as lit pips.
    for (x in TeamTrialsGeometry.RP_GAP_XS) {
        val p = mapped.argb(x, TeamTrialsGeometry.RP_PIP_Y)
        if (minOf(red(p), green(p), blue(p)) < 240) return null
    }
    var lit = 0
    var emptySeen = false
    for (x in TeamTrialsGeometry.RP_PIP_XS) {
        val (r, g, b) = meanArgb(mapped, x, TeamTrialsGeometry.RP_PIP_Y)
        when {
            isPipLit(r, g, b) && !emptySeen -> lit++
            isPipEmpty(r, g, b) -> emptySeen = true
            else -> return null
        }
    }
    return lit
}

/** The "n/5" RP counter as OCR reads it ("3/5" -> 3); null unless it is a whole count out of 5. */
fun parseRpCount(raw: String): Int? {
    val m = Regex("(\\d)\\s*/\\s*5").find(raw.replace(" ", "")) ?: return null
    return m.groupValues[1].toInt().takeIf { it in 0..5 }
}

/**
 * Whether the standby screen's Quick Mode pill reads green (ON). Only meaningful once See All Race Results is
 * matched: the race field behind other screens is green too. The OFF look has not been captured.
 */
fun quickModePillOn(
    sampler: SparkPixelSampler,
    width: Int,
    height: Int,
): Boolean {
    if (!isMappedSurface(width, height)) return false
    val mapped = sampler.onScreen(ScreenBand.BOTTOM, width, height)
    var hits = 0
    var total = 0
    for (y in TeamTrialsGeometry.QUICK_MODE_Y - 10..TeamTrialsGeometry.QUICK_MODE_Y + 10 step 10) {
        for (x in TeamTrialsGeometry.QUICK_MODE_X0..TeamTrialsGeometry.QUICK_MODE_X1 step 4) {
            val p = mapped.argb(x, y)
            if (green(p) >= 170 && green(p) - blue(p) >= 100 && green(p) - red(p) >= 20) hits++
            total++
        }
    }
    return hits.toDouble() / total >= 0.5
}

private fun isBannerLime(argb: Int): Boolean = red(argb) >= 150 && green(argb) >= 225 && blue(argb) <= 120

private fun isBannerEmerald(argb: Int): Boolean = red(argb) <= 110 && green(argb) in 180..225 && blue(argb) in 90..160

private fun rowShare(
    sampler: SparkPixelSampler,
    y: Int,
    x0: Int,
    x1: Int,
    step: Int,
    hit: (Int) -> Boolean,
): Double {
    var hits = 0
    var total = 0
    for (x in x0 until x1 step step) {
        if (hit(sampler.argb(x, y))) hits++
        total++
    }
    return hits.toDouble() / total
}

/**
 * Whether the full-screen "NEW HIGH SCORE!" banner is up, shown after a match that sets a weekly high score.
 * Measured on the banner: white rows 0.96 and up, stripe rows 0.68 and up, title 0.24. Other screens reach 0.22 stripes or
 * 0.41 title on their own, so only all three together name the banner.
 */
fun teamTrialsHighScoreBanner(
    sampler: SparkPixelSampler,
    width: Int,
    height: Int,
): Boolean {
    if (!isMappedSurface(width, height)) return false
    val mapped = sampler.onScreen(ScreenBand.DIALOG, width, height)
    if (TeamTrialsGeometry.HIGH_SCORE_WHITE_YS.any { y -> rowShare(mapped, y, 0, 1080, 6) { minOf(red(it), green(it), blue(it)) >= 235 } < 0.9 }) return false
    if (TeamTrialsGeometry.HIGH_SCORE_STRIPE_YS.any { y -> rowShare(mapped, y, 0, 1080, 6) { isBannerLime(it) || isBannerEmerald(it) } < 0.4 }) return false
    val titleRows = TeamTrialsGeometry.HIGH_SCORE_TITLE_Y0..TeamTrialsGeometry.HIGH_SCORE_TITLE_Y1 step 4
    val title = titleRows.map { y -> rowShare(mapped, y, TeamTrialsGeometry.HIGH_SCORE_TITLE_X0, TeamTrialsGeometry.HIGH_SCORE_TITLE_X1, 3, ::isBannerLime) }.average()
    return title >= 0.12
}
