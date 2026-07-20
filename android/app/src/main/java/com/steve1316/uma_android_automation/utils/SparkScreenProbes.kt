package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.SparkRowKind

/**
 * Pixel probes and geometry for the career-end spark screens: the SPARKS list, the "Sparks
 * Rerolled" result, the "Spark Selection" intro and pager, and the keep-set Confirmation
 * dialog.
 *
 * Every coordinate and threshold here was measured on the live 2026-07-08 capture set
 * (1080x1920, the nine-shot MuMu-20260708-0154xx/0155xx sequence, mirrored under
 * src/test/resources/fixtures/sparks with a provenance file), and every probe is pinned by
 * fixture tests against those exact pixels. All code is Android-free on purpose: the runtime
 * wraps a Bitmap in a [SparkPixelSampler], the JUnit fixtures wrap a BufferedImage.
 */

/** Minimal pixel access: returns ARGB at (x, y). */
fun interface SparkPixelSampler {
    fun argb(x: Int, y: Int): Int
}

private fun red(argb: Int): Int = (argb shr 16) and 0xFF

private fun green(argb: Int): Int = (argb shr 8) and 0xFF

private fun blue(argb: Int): Int = argb and 0xFF

/** Pixel geometry of one spark list layout on 1080-wide captures. */
data class SparkListGeometry(
    /** Center Y of the first row's bar. */
    val firstRowY: Int,
    /** Row center pitch. The SPARKS screen and the Confirmation dialog use 119; the Spark
     * Selection pager uses 120 (measured band starts 307/427/547...). */
    val rowPitch: Int,
    /** Rows fully inside the visible window; anything past this needs a scroll. */
    val maxRows: Int,
    /** Star-slot sample centers. */
    val starXs: List<Int>,
    val debugPrefix: String,
)

/** The career-end SPARKS screen list: 9 full rows fit the window, a 10th renders clipped at
 * the list mask, so a 10+ row set needs one scroll. The old maxRows = 6 silently truncated
 * real sets (the 2026-07-08 capture shows 9 full rows + 1 clipped). */
val SPARKS_SCREEN_GEOMETRY = SparkListGeometry(firstRowY = 307, rowPitch = 119, maxRows = 9, starXs = listOf(846, 894, 941), debugPrefix = "sparkRow")

/** The "Keep this set of Sparks?" / Spark Selection Confirmation dialog list: shrink-wraps to
 * the set and lists it on one screen (measured live at 10 and at 11 rows from y=315). The
 * window allows one slot past the largest observed set so an 11-row set proves its own end
 * marker inside the first frame instead of spending a swipe to prove the list cannot move. */
val SPARKS_CONFIRM_GEOMETRY = SparkListGeometry(firstRowY = 315, rowPitch = 119, maxRows = 12, starXs = listOf(855, 901, 947), debugPrefix = "sparkKeepRow")

/** The Spark Selection pager list: 8 full rows per page, 120 px pitch, stars on the SPARKS
 * screen's columns. A 10-row set shows 8 full + 1 clipped, so pages must scroll to read. */
val SPARK_PAGER_GEOMETRY = SparkListGeometry(firstRowY = 354, rowPitch = 120, maxRows = 8, starXs = listOf(846, 894, 941), debugPrefix = "sparkPageRow")

/** All bar-kind sampling happens on this column (right of the name, left of the stars). */
const val SPARK_BAR_SAMPLE_X = 770

// Fixed anchors measured on the 2026-07-08 captures. The tap points are also what the fixture
// tests probe, so a layout drift breaks a test instead of a live career.
const val SPARK_PAGER_CHEVRON_LEFT_X = 88
const val SPARK_PAGER_CHEVRON_RIGHT_X = 990
const val SPARK_PAGER_CHEVRON_Y = 228
const val SPARK_PAGER_DOT_PAGE1_X = 527
const val SPARK_PAGER_DOT_PAGE2_X = 551
const val SPARK_PAGER_DOT_Y = 272
const val SPARK_PAGER_CONFIRM_X = 540
const val SPARK_PAGER_CONFIRM_Y = 1769

/** Wide bottom button color probe row: above the white button LABEL band (the rerolled
 * screen's "Next" text crosses exactly y=1770 at x=540, so sampling there reads text pixels
 * instead of button green). The tap anchors stay at the button center. */
const val SPARK_WIDE_BUTTON_PROBE_Y = 1735
const val SPARK_CONFIRMATION_CANCEL_X = 302
const val SPARK_CONFIRMATION_CANCEL_Y = 1804
const val SPARK_CONFIRMATION_CONFIRM_X = 777
const val SPARK_CONFIRMATION_CONFIRM_Y = 1775
const val SPARK_INTRO_BUTTON_X = 540
const val SPARK_INTRO_BUTTON_Y = 1777

/** 5x5 mean of one channel around (cx, cy), the same smoothing the production row reader has
 * always used. */
private fun mean5(sampler: SparkPixelSampler, cx: Int, cy: Int, channel: (Int) -> Int): Int {
    var sum = 0
    for (dy in -2..2) {
        for (dx in -2..2) {
            sum += channel(sampler.argb(cx + dx, cy + dy))
        }
    }
    return sum / 25
}

private fun meanRgb(sampler: SparkPixelSampler, cx: Int, cy: Int): Triple<Int, Int, Int> =
    Triple(mean5(sampler, cx, cy, ::red), mean5(sampler, cx, cy, ::green), mean5(sampler, cx, cy, ::blue))

/** Bar-color row kind, or null on the pure-white end-of-grid sample. Thresholds are the
 * live-validated production values (55 kept records) and hold on all three layouts: blue
 * (101..108, 201..204, 251), pink (255, 147..150, 195), green (154, 217, 56), white row body
 * (224, 224, 224) vs the 255 end-of-grid white. */
fun classifySparkBar(r: Int, g: Int, b: Int): SparkRowKind? =
    when {
        r >= 245 && g >= 245 && b >= 245 -> null
        b > 240 && r < 150 -> SparkRowKind.STAT
        r > 240 && g < 180 && b > 160 -> SparkRowKind.APTITUDE
        g > 200 && b < 100 -> SparkRowKind.UNIQUE
        else -> SparkRowKind.WHITE
    }

/** Gold-star test at a star-slot center: gold (255, 204..223, 64..78) against grey empty
 * slots and every bar color (pink fails on blue >= 150, blue/green fail on red). */
private fun isGoldStar(r: Int, b: Int): Boolean = r > 200 && b < 150

/** One row slot as the pixels describe it; the caller adds the OCR name. */
data class SparkRowCell(val index: Int, val rowY: Int, val kind: SparkRowKind, val stars: Int)

/**
 * Parse the visible row window of a spark list: kind from the bar color, stars from the slot
 * samples, terminated by the pure-white end-of-grid sample. The name-based termination (a
 * starless, textless slot past a shrink-wrapped dialog's last row) stays with the caller
 * because it needs OCR.
 */
fun parseSparkRowCells(sampler: SparkPixelSampler, geometry: SparkListGeometry, frameHeight: Int): List<SparkRowCell> {
    val cells = mutableListOf<SparkRowCell>()
    for (i in 0 until geometry.maxRows) {
        val y = geometry.firstRowY + i * geometry.rowPitch
        if (y + 3 >= frameHeight) break
        val (r, g, b) = meanRgb(sampler, SPARK_BAR_SAMPLE_X, y)
        val kind = classifySparkBar(r, g, b) ?: break
        val stars =
            geometry.starXs.count { x ->
                isGoldStar(mean5(sampler, x, y, ::red), mean5(sampler, x, y, ::blue))
            }
        cells.add(SparkRowCell(i, y, kind, stars))
    }
    return cells
}

/**
 * Vertical offset of the actual row grid from [SparkListGeometry.firstRowY], or null when no
 * grid can be located. A swipe does not settle on pixel-exact row multiples, so a scrolled
 * frame's rows can sit shifted against the fixed grid. The anchor is the FIRST STAR COLUMN:
 * every real spark row shows at least one gold star at starXs[0], a crisp color against every
 * row and background (the bar-color bands cannot delimit rows on the confirmation dialog,
 * whose card body reads as a white row). Each gold run's center votes an offset against its
 * nearest grid line; the median vote wins, so one noisy run cannot skew the grid. On the
 * unscrolled captures the measured offsets are within a few pixels of zero, and the fixture
 * tests pin that the aligned parse equals the fixed-grid parse there.
 */
fun sparkRowGridOffset(sampler: SparkPixelSampler, geometry: SparkListGeometry, frameHeight: Int): Int? {
    val x = geometry.starXs[0]

    fun gold(y: Int): Boolean {
        if (y < 1 || y + 1 >= frameHeight) return false
        var r = 0
        var b = 0
        for (dy in -1..1) {
            val p = sampler.argb(x, y + dy)
            r += red(p)
            b += blue(p)
        }
        return r / 3 > 200 && b / 3 < 150
    }
    // Gold runs over the scan window (one extra half-pitch above the first grid line for a
    // shifted-up frame). The star glyph is a pentagram, so a vertical line through its center
    // crosses a notch and splits into short runs; runs separated by small gaps are clustered
    // back into one glyph before voting.
    val yStart = (geometry.firstRowY - geometry.rowPitch / 2).coerceAtLeast(1)
    val yEnd = (geometry.firstRowY + geometry.rowPitch * geometry.maxRows).coerceAtMost(frameHeight - 2)
    val runs = mutableListOf<IntArray>() // [start, end]
    var runStart = -1
    var y = yStart
    while (y <= yEnd) {
        if (gold(y)) {
            if (runStart < 0) runStart = y
        } else if (runStart >= 0) {
            runs.add(intArrayOf(runStart, y - 2))
            runStart = -1
        }
        y += 2
    }
    if (runStart >= 0) runs.add(intArrayOf(runStart, yEnd))
    val clusters = mutableListOf<IntArray>()
    for (run in runs) {
        val last = clusters.lastOrNull()
        if (last != null && run[0] - last[1] <= 10) {
            last[1] = run[1]
        } else {
            clusters.add(intArrayOf(run[0], run[1]))
        }
    }
    val votes = mutableListOf<Int>()
    for (cluster in clusters) {
        // A star glyph is ~30-40 px tall on this column; shorter glints are noise.
        if (cluster[1] - cluster[0] < 16) continue
        val center = (cluster[0] + cluster[1]) / 2
        val gridIndex = Math.round((center - geometry.firstRowY).toDouble() / geometry.rowPitch).toInt()
        val offset = center - (geometry.firstRowY + gridIndex * geometry.rowPitch)
        if (offset in -56..56) votes.add(offset)
    }
    if (votes.isEmpty()) return null
    votes.sort()
    return votes[votes.size / 2]
}

/** [parseSparkRowCells] with the grid re-anchored on the detected band offset - the scrolled
 * frames' parser. Returns null when no grid is found at all. */
fun parseSparkRowCellsAligned(sampler: SparkPixelSampler, geometry: SparkListGeometry, frameHeight: Int): List<SparkRowCell>? {
    val offset = sparkRowGridOffset(sampler, geometry, frameHeight) ?: return null
    val aligned = geometry.copy(firstRowY = geometry.firstRowY + offset)
    return parseSparkRowCells(sampler, aligned, frameHeight)
}

/** The standard lead check: a real spark list opens stat / aptitude / unique. */
fun sparkCellsLeadCorrectly(cells: List<SparkRowCell>): Boolean =
    cells.size >= 3 &&
        cells[0].kind == SparkRowKind.STAT &&
        cells[1].kind == SparkRowKind.APTITUDE &&
        cells[2].kind == SparkRowKind.UNIQUE

// Green predicates, one per UI element family (measured values in the comments).

/** Dialog header / banner green: Confirmation title (109..117, 196..201, 12), intro banner
 * (138, 211, 8), set-name pill (117, 201, 12). */
private fun isHeaderGreen(r: Int, g: Int, b: Int): Boolean = g >= 150 && g - r >= 60 && g - b >= 100

/** Advance-button green: pager Confirm (165, 219, 75), rerolled Next (98..158, 190..221, 12),
 * confirmation Confirm (167, 220, 79). */
private fun isButtonGreen(r: Int, g: Int, b: Int): Boolean = g >= 180 && r <= 210 && g - r >= 30 && g - b >= 80

/** Active page dot green (156, 222, 24..31); the inactive dot is brown (121, 64, 22). */
private fun isActiveDotGreen(r: Int, g: Int, b: Int): Boolean = g >= 190 && g - r >= 40 && g - b >= 120

private fun isNearWhite(r: Int, g: Int, b: Int): Boolean = r >= 245 && g >= 245 && b >= 245

private fun isWhitishButton(r: Int, g: Int, b: Int): Boolean = r >= 210 && g >= 210 && b >= 210

/** Chevron arrow stroke: dark green (53, 121, 0) with antialiased mids. Sampled raw (no
 * smoothing) on a coarse grid over the chevron box. */
private fun isChevronStroke(r: Int, g: Int, b: Int): Boolean = g >= 90 && g - r >= 40 && b <= 80

private fun chevronStrokeHits(sampler: SparkPixelSampler, centerX: Int): Int {
    var hits = 0
    var y = SPARK_PAGER_CHEVRON_Y - 30
    while (y <= SPARK_PAGER_CHEVRON_Y + 30) {
        var x = centerX - 30
        while (x <= centerX + 30) {
            val p = sampler.argb(x, y)
            if (isChevronStroke(red(p), green(p), blue(p))) hits++
            x += 6
        }
        y += 6
    }
    return hits
}

/** One chevron: an outlined white arrow, not a solid banner corner. The green diamond
 * decoration on the SPARKS / Sparks Rerolled / Confirmation headers floods the same box with
 * stroke-colored pixels (46..60 grid hits, solid header-green center) where the real chevron
 * reads 13..14 hits over a pale center, so the band and the center test reject banners. The
 * dimmed backdrop behind an overlay dialog washes the stroke to zero hits. */
private fun chevronPresent(sampler: SparkPixelSampler, centerX: Int): Boolean {
    val hits = chevronStrokeHits(sampler, centerX)
    if (hits < 3 || hits > 30) return false
    val (r, g, b) = meanRgb(sampler, centerX, SPARK_PAGER_CHEVRON_Y)
    return !isHeaderGreen(r, g, b)
}

/** Both pager chevrons visible at full strength. */
fun sparkPagerChevronsPresent(sampler: SparkPixelSampler): Boolean =
    chevronPresent(sampler, SPARK_PAGER_CHEVRON_LEFT_X) &&
        chevronPresent(sampler, SPARK_PAGER_CHEVRON_RIGHT_X)

/** The active page dot: 1 (Rerolled page), 2 (Original page), or null when not exactly one
 * dot is lit. */
fun sparkPagerActiveDotIndex(sampler: SparkPixelSampler): Int? {
    val (r1, g1, b1) = meanRgb(sampler, SPARK_PAGER_DOT_PAGE1_X, SPARK_PAGER_DOT_Y)
    val (r2, g2, b2) = meanRgb(sampler, SPARK_PAGER_DOT_PAGE2_X, SPARK_PAGER_DOT_Y)
    val first = isActiveDotGreen(r1, g1, b1)
    val second = isActiveDotGreen(r2, g2, b2)
    return when {
        first && !second -> 1
        second && !first -> 2
        else -> null
    }
}

private fun probeIs(sampler: SparkPixelSampler, x: Int, y: Int, predicate: (Int, Int, Int) -> Boolean): Boolean {
    val (r, g, b) = meanRgb(sampler, x, y)
    return predicate(r, g, b)
}

/**
 * Structural signature of the Spark Selection pager: both chevrons, exactly one lit page dot,
 * a stat-blue first row on the pager geometry, the single wide green Confirm, and no green
 * Confirmation header band (which would mean the keep dialog is overlaid on top).
 */
fun sparkPagerStructurePresent(sampler: SparkPixelSampler): Boolean =
    sparkPagerChevronsPresent(sampler) &&
        sparkPagerActiveDotIndex(sampler) != null &&
        probeIs(sampler, SPARK_BAR_SAMPLE_X, SPARK_PAGER_GEOMETRY.firstRowY) { r, _, b -> b > 240 && r < 150 } &&
        probeIs(sampler, SPARK_PAGER_CONFIRM_X, SPARK_WIDE_BUTTON_PROBE_Y, ::isButtonGreen) &&
        !probeIs(sampler, 540, 120, ::isHeaderGreen)

/**
 * Structural signature of the Spark Selection Confirmation dialog: the green "Confirmation"
 * title band, the green set-name pill, a stat-blue first row on the confirmation geometry,
 * and the Cancel/Confirm pair.
 */
fun sparkConfirmationStructurePresent(sampler: SparkPixelSampler): Boolean =
    probeIs(sampler, 540, 120, ::isHeaderGreen) &&
        probeIs(sampler, 540, 225, ::isHeaderGreen) &&
        probeIs(sampler, SPARK_BAR_SAMPLE_X, SPARKS_CONFIRM_GEOMETRY.firstRowY) { r, _, b -> b > 240 && r < 150 } &&
        probeIs(sampler, SPARK_CONFIRMATION_CONFIRM_X, SPARK_CONFIRMATION_CONFIRM_Y, ::isButtonGreen) &&
        probeIs(sampler, SPARK_CONFIRMATION_CANCEL_X, SPARK_CONFIRMATION_CANCEL_Y, ::isWhitishButton)

/**
 * Structural signature of the "Spark Selection" intro dialog: the green banner across the
 * card (528..665), white card body below it reaching y=1600 (the "Confirm Reroll" spend
 * dialog shares the banner layout but its card ends higher and its green spend button sits at
 * (778, 1252) - both differences are probed so the two dialogs can never be confused), and
 * the dimmed backdrop above the card where the pager would be pure white.
 */
fun sparkIntroStructurePresent(sampler: SparkPixelSampler): Boolean =
    probeIs(sampler, 540, 596, ::isHeaderGreen) &&
        probeIs(sampler, SPARK_BAR_SAMPLE_X, 596, ::isHeaderGreen) &&
        probeIs(sampler, 540, 1050) { r, g, b -> r >= 238 && g >= 238 && b >= 238 } &&
        probeIs(sampler, 540, 1600) { r, g, b -> r >= 238 && g >= 238 && b >= 238 } &&
        !probeIs(sampler, 778, 1252, ::isButtonGreen) &&
        !probeIs(sampler, SPARK_BAR_SAMPLE_X, 280) { r, g, b -> isNearWhite(r, g, b) }

/**
 * Structural signature of the "Sparks Rerolled" result screen: the SPARKS-geometry list
 * leading stat / aptitude / unique, the single wide green Next, and no pager chevrons. The
 * SPARKS screen itself never reaches this probe (its Reroll Sparks button is detected first),
 * and its bottom differs anyway (buttons at x=777, whitish at x=540).
 */
fun sparkRerolledStructurePresent(sampler: SparkPixelSampler): Boolean =
    probeIs(sampler, SPARK_BAR_SAMPLE_X, 307) { r, _, b -> b > 240 && r < 150 } &&
        probeIs(sampler, SPARK_BAR_SAMPLE_X, 426) { r, g, b -> r > 240 && g < 180 && b > 160 } &&
        probeIs(sampler, SPARK_BAR_SAMPLE_X, 545) { r, g, b -> g > 200 && b < 100 } &&
        probeIs(sampler, 540, SPARK_WIDE_BUTTON_PROBE_Y, ::isButtonGreen) &&
        !sparkPagerChevronsPresent(sampler)

// OCR regions (x, y, width, height) on 1080x1920 frames. The navigator feeds them to
// performOCROnRegion; the fixture tests pin that each stays inside its measured text band.

val SPARK_PAGER_HEADING_OCR_REGION = intArrayOf(150, 190, 780, 80)
val SPARK_REROLLED_TITLE_OCR_REGION = intArrayOf(200, 130, 680, 100)
val SPARK_INTRO_TITLE_OCR_REGION = intArrayOf(250, 540, 580, 130)
val SPARK_CONFIRMATION_TITLE_OCR_REGION = intArrayOf(250, 88, 580, 72)
val SPARK_CONFIRMATION_SET_NAME_OCR_REGION = intArrayOf(40, 196, 700, 62)
