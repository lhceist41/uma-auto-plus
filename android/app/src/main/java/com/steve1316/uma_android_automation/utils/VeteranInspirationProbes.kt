package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.SparkRowKind

/**
 * Region geometry and pure pixel probes for the `Umamusume Details` dialog's **Inspiration** tab: the
 * Veteran's own `Sparks` block followed by the `Legacy Origin` blocks of the two parents it inherited
 * from.
 *
 * Deliberately separate from [LegacySparkProbes] (the career-launch Legacy Select "Sparks" sub-view).
 * The two screens show related data and share nothing measurable: this one lays factors out in TWO
 * columns at an ~89 px row pitch inside a nested scroll viewport with its own scrollbar, while the
 * Legacy Select view is a single column at a ~124 px pitch with a genealogy rail. Reusing the Legacy
 * constants here would be silently wrong, so only the genuinely screen-independent pieces are shared:
 * [SparkRowKind], [SparkPixelSampler], and the star-slot classifier [classifyStarSlot] with its
 * [SparkSlotRead] / [SparkSlotEvidence] evidence types.
 *
 * Every coordinate and threshold was measured on the live 2026-08-21 capture pair (1080x1920,
 * `validation/parentlab-plr1b-inspiration/`, committed as the `fixtures/inspiration` test fixtures)
 * and is pinned by [com.steve1316.uma_android_automation.utils.VeteranInspirationProbesFixtureTest]
 * against those exact pixels. Android-free on purpose: the runtime wraps a Bitmap in a
 * [SparkPixelSampler], the JUnit fixtures wrap a decoded PNG.
 */

private fun ired(argb: Int): Int = (argb shr 16) and 0xFF

private fun igreen(argb: Int): Int = (argb shr 8) and 0xFF

private fun iblue(argb: Int): Int = argb and 0xFF

/** 5x5 channel mean around (cx, cy), matching the smoothing every spark row reader uses. */
private fun imean5(sampler: SparkPixelSampler, cx: Int, cy: Int, channel: (Int) -> Int): Int {
    var sum = 0
    for (dy in -2..2) {
        for (dx in -2..2) {
            sum += channel(sampler.argb(cx + dx, cy + dy))
        }
    }
    return sum / 25
}

internal fun inspirationMeanRgb(sampler: SparkPixelSampler, cx: Int, cy: Int): Triple<Int, Int, Int> =
    Triple(imean5(sampler, cx, cy, ::ired), imean5(sampler, cx, cy, ::igreen), imean5(sampler, cx, cy, ::iblue))

// -- Tab strip (visible on every Details tab, above the panel) ------------------------------------

/** Vertical centre of the Skills / Inspiration / Career Info pill row. */
const val DETAIL_TAB_CY = 828

/** Horizontal centres of the three tabs. The active tab is the one filled with the green pill. */
const val DETAIL_TAB_SKILLS_CX = 199
const val DETAIL_TAB_INSPIRATION_CX = 540
const val DETAIL_TAB_CAREER_INFO_CX = 880

/** True when the tab whose centre is [cx] is the active (green-filled) one. The inactive pills are
 * near-white with brown text, so a green sample at the pill centre is unambiguous. */
fun inspirationTabActive(sampler: SparkPixelSampler, cx: Int = DETAIL_TAB_INSPIRATION_CX): Boolean {
    val (r, g, b) = inspirationMeanRgb(sampler, cx, DETAIL_TAB_CY)
    return g >= 150 && g - r >= 40 && b < 130
}

// -- Scroll viewport and its scrollbar ------------------------------------------------------------

/** The nested scroll viewport that clips the factor list. Content above/below these rows is not
 * rendered at all, so a row run touching either edge is a truncated read, never a short row. */
const val INSPIRATION_VIEWPORT_TOP = 888
const val INSPIRATION_VIEWPORT_BOTTOM = 1665

const val INSPIRATION_VIEWPORT_HEIGHT = INSPIRATION_VIEWPORT_BOTTOM - INSPIRATION_VIEWPORT_TOP + 1

/** The scrollbar column, its track, and the luma below which a sample is the thumb rather than the
 * (invisible, panel-coloured) track. The thumb reads a flat (125,120,142). */
const val INSPIRATION_SCROLLBAR_X = 1045
const val INSPIRATION_TRACK_TOP = 912
const val INSPIRATION_TRACK_BOTTOM = 1645
const val INSPIRATION_THUMB_MAX_LUMA = 200

const val INSPIRATION_TRACK_LENGTH = INSPIRATION_TRACK_BOTTOM - INSPIRATION_TRACK_TOP + 1

/** How close to a track end the thumb must sit to count as scrolled fully to that end. */
private const val TRACK_END_SLACK = 3

/**
 * The scroll state read straight off the scrollbar thumb.
 *
 * The thumb is not just a progress indicator here, it is the only exact measurement of content the
 * viewport cannot show: thumb length over track length is viewport height over content height, so
 * one frame yields both the total content height and this frame's absolute scroll offset. That turns
 * a blind "swipe and hope" traversal into one with a known total, a known position, and a positive
 * end-of-content signal, which is what [InspirationRowAccumulator] needs to prove it saw everything.
 *
 * [scrollable] is false when the whole list fits (no thumb, or a thumb filling the track); then the
 * single frame IS the content and offset is 0.
 */
data class InspirationScrollRead(
    val scrollable: Boolean,
    val thumbTop: Int?,
    val thumbBottom: Int?,
    /** Measured thumb run in pixels. Invariant for a given list, so a frame whose thumb measures
     * shorter than the one read at rest is a frame captured mid-motion, not a shorter list. */
    val thumbLength: Int,
    val contentHeight: Int,
    val offset: Int,
    val atTop: Boolean,
    val atBottom: Boolean,
)

/** How far a frame's thumb may differ from the at-rest reference before the frame is treated as
 * still moving. The thumb is drawn with rounded ends, so a couple of pixels of antialiasing drift is
 * normal; the over-scroll bounce shrinks it by tens or hundreds. */
const val INSPIRATION_THUMB_LENGTH_SLACK = 6

/** Reads the scrollbar thumb run and derives the content height and absolute scroll offset. */
fun readInspirationScroll(sampler: SparkPixelSampler): InspirationScrollRead {
    var top = -1
    var bottom = -1
    for (y in INSPIRATION_TRACK_TOP..INSPIRATION_TRACK_BOTTOM) {
        val argb = sampler.argb(INSPIRATION_SCROLLBAR_X, y)
        val luma = (ired(argb) * 299 + igreen(argb) * 587 + iblue(argb) * 114) / 1000
        if (luma <= INSPIRATION_THUMB_MAX_LUMA) {
            if (top < 0) top = y
            bottom = y
        }
    }
    val thumbLength = if (top < 0) 0 else bottom - top + 1
    // No thumb, or one that fills the track: the list does not overflow, so this frame is all of it.
    if (thumbLength <= 0 || thumbLength >= INSPIRATION_TRACK_LENGTH - 4) {
        return InspirationScrollRead(
            scrollable = false,
            thumbTop = if (top < 0) null else top,
            thumbBottom = if (top < 0) null else bottom,
            thumbLength = thumbLength,
            contentHeight = INSPIRATION_VIEWPORT_HEIGHT,
            offset = 0,
            atTop = true,
            atBottom = true,
        )
    }
    val contentHeight = Math.round(INSPIRATION_VIEWPORT_HEIGHT.toDouble() * INSPIRATION_TRACK_LENGTH / thumbLength).toInt()
    val travel = (INSPIRATION_TRACK_LENGTH - thumbLength).toDouble()
    val offset = Math.round((top - INSPIRATION_TRACK_TOP) * (contentHeight - INSPIRATION_VIEWPORT_HEIGHT) / travel).toInt()
    return InspirationScrollRead(
        scrollable = true,
        thumbTop = top,
        thumbBottom = bottom,
        thumbLength = thumbLength,
        contentHeight = contentHeight,
        offset = offset,
        atTop = top <= INSPIRATION_TRACK_TOP + TRACK_END_SLACK,
        atBottom = bottom >= INSPIRATION_TRACK_BOTTOM - TRACK_END_SLACK,
    )
}

/**
 * True when [read] measured the same thumb as the at-rest reference, and can therefore be trusted for
 * this frame's scroll offset.
 *
 * The panel over-scrolls with a rubber-band bounce, and the thumb visibly shrinks while it settles.
 * A frame captured during that bounce reports a thumb a fraction of its real length, which inflates
 * the derived content height by the same factor - the 20-Veteran validation run produced content
 * heights of 2516, 5099 and 10197 pixels for lists that are really about 1700 tall, and every one of
 * those captures then mis-placed its rows. Comparing against the length measured at rest is what
 * separates "the list is this tall" from "the panel is still moving".
 */
fun inspirationScrollTrustworthy(read: InspirationScrollRead, referenceThumbLength: Int): Boolean =
    kotlin.math.abs(read.thumbLength - referenceThumbLength) <= INSPIRATION_THUMB_LENGTH_SLACK

/**
 * How many pixels of content one pixel of thumb travel is worth, which is the finest the scrollbar can
 * place a row.
 *
 * It is not a constant: the thumb shortens as the panel grows, so a Veteran with a long
 * inspiration-usage history behind its factors gets a coarser ruler. A live Gold Ship's 10,018 px
 * panel leaves a 57 px thumb, worth almost fourteen pixels of content each.
 */
fun inspirationOffsetResolution(reference: InspirationScrollRead): Double {
    if (!reference.scrollable) return 0.0
    val travel = (INSPIRATION_TRACK_LENGTH - reference.thumbLength).toDouble()
    if (travel <= 0) return 0.0
    return (reference.contentHeight - INSPIRATION_VIEWPORT_HEIGHT) / travel
}

/** Above this, the scrollbar cannot place a row finely enough to be the merge's primary signal, and
 * the pixel-key alignment takes over - it only needs the offset to be right to within a couple of
 * rows. Two frames each off by six pixels put adjacent rows twelve apart, which the row-spacing check
 * absorbs; at fourteen each they do not. */
const val INSPIRATION_MAX_TRUSTED_OFFSET_RESOLUTION = 6.0

/** Whether this list's scrollbar is fine enough to place rows by position alone. */
fun inspirationOffsetPreciseEnough(reference: InspirationScrollRead): Boolean =
    inspirationOffsetResolution(reference) <= INSPIRATION_MAX_TRUSTED_OFFSET_RESOLUTION

/**
 * This frame's scroll offset measured against the calibration frame's geometry rather than its own.
 *
 * Only the thumb's POSITION varies between frames; its length and the content height do not. Deriving
 * the offset from each frame's own thumb length lets a pixel of measurement noise move the whole
 * scale - on a panel with a long usage history the thumb is barely 57 px, where one pixel of length
 * is worth 176 px of content - and two frames of the same list then disagree about where the same row
 * sits. Holding the scale fixed makes every frame's offset comparable, which is what the merge needs.
 */
fun inspirationOffsetAgainst(reference: InspirationScrollRead, thumbTop: Int): Int {
    if (!reference.scrollable) return 0
    val travel = (INSPIRATION_TRACK_LENGTH - reference.thumbLength).toDouble()
    if (travel <= 0) return 0
    return Math.round((thumbTop - INSPIRATION_TRACK_TOP) * (reference.contentHeight - INSPIRATION_VIEWPORT_HEIGHT) / travel).toInt()
}

// -- Two-column card grid -------------------------------------------------------------------------

/** Bar-kind sampling columns: inside each card, right of its rounded left edge and left of the
 * leading circle glyph (which starts at x=221 / x=636), so the sample is never text or icon. */
const val INSPIRATION_LEFT_BAR_X = 215
const val INSPIRATION_RIGHT_BAR_X = 629

/** Star-slot sample centres per column. Fixed positions, not a centred group: an empty slot renders
 * a grey star at the same x as a filled one would. */
val INSPIRATION_LEFT_STAR_XS: List<Int> = listOf(374, 409, 444)
val INSPIRATION_RIGHT_STAR_XS: List<Int> = listOf(789, 824, 859)

/** The card columns' outer edges. Only their inner edges matter to the code: the gap between them is
 * the one vertical strip of the panel that carries no card, and therefore the safest place to start a
 * scroll drag on a screen whose other controls are irreversible. */
const val INSPIRATION_LEFT_CARD_RIGHT_X = 611
const val INSPIRATION_RIGHT_CARD_LEFT_X = 623

/** The traversal's swipe column and its lower endpoint. Both endpoints of every swipe sit inside the
 * scroll viewport and in the inter-column gutter; [VeteranInspirationScannerSafetyTest] pins that
 * numerically rather than trusting the comment. */
const val INSPIRATION_SWIPE_X = 617
const val INSPIRATION_SWIPE_LOW_Y = 1205

/** Offset from a card's top edge to its star row centre. The name sits at +12..+48, the stars at
 * +51..+69, so the two never share a crop. */
const val INSPIRATION_STAR_DY = 55

/** A fully-visible card body. The list mask can clip a card at either viewport edge, so a run that
 * clears [INSPIRATION_MIN_ROW_HEIGHT] but falls short of this is emitted flagged clipped rather than
 * trusted; the accumulator re-reads it whole from the next frame. */
const val INSPIRATION_CARD_HEIGHT = 64
const val INSPIRATION_MIN_ROW_HEIGHT = 45
const val INSPIRATION_FULL_ROW_MIN_HEIGHT = 52

/** Card-to-card pitch inside one block, measured over eight consecutive rows (88.57 px). Used only
 * to sanity-check merged row spacing; no row position is ever computed from it. */
const val INSPIRATION_ROW_PITCH = 88.6

/** Card pitch across a block boundary or the "Legacy Origin" divider: the same card pitch plus the
 * ~61 px the divider/portrait gap inserts. */
const val INSPIRATION_BLOCK_PITCH = 150.0

/** How far a merged row's spacing may stray from [INSPIRATION_ROW_PITCH] or [INSPIRATION_BLOCK_PITCH]
 * before it is reported as a content gap. Generous enough for the scrollbar's own quantization,
 * far below the ~89 px a missed row would cost. */
const val INSPIRATION_PITCH_SLACK = 26.0

/** Content padding below the last card. Same purpose as [INSPIRATION_FIRST_ROW_CONTENT_Y]. */
const val INSPIRATION_BOTTOM_PADDING = 20

/** The left rail band where a block's owner portrait sits. Read as corroborating evidence for a
 * block start, never as the segmentation signal (the portrait is ~115 px tall and bleeds into the
 * row below its own). */
const val INSPIRATION_RAIL_X_START = 60
const val INSPIRATION_RAIL_X_END = 190

/** How a single bar sample classifies. STAT/APTITUDE/UNIQUE/WHITE are factor cards; SECTION_HEADER is
 * the green "Sparks" pill at the very top of the content; GAP is the panel background between cards
 * and in an empty right-hand cell; UNKNOWN is anything else (the card's drop shadow, the "Legacy
 * Origin" divider text) and never becomes a row. */
enum class InspirationBarClass { STAT, APTITUDE, UNIQUE, WHITE, SECTION_HEADER, GAP, UNKNOWN }

/**
 * Classify one smoothed bar sample.
 *
 * Unlike the career-end and Legacy Select spark bars, these cards are flat-filled at the sample
 * columns (measured: no gloss gradient at all), so each kind is an exact colour with wide margins:
 * stat (52,183,243), aptitude (255,117,175), unique (146,207,44), white card (224,224,224), panel
 * gap (241,241,241), section header (117,201,12).
 *
 * Two separations are deliberate and load-bearing. The white card and the panel gap are both pure
 * greys only 17 apart, so they split on a mean threshold rather than a hue test; and the card's drop
 * shadow (~181,179,193) is excluded from WHITE by the neutrality test, which is what keeps two
 * vertically adjacent white cards from merging into one tall run.
 */
fun classifyInspirationBarSample(r: Int, g: Int, b: Int): InspirationBarClass {
    val spread = maxOf(r, g, b) - minOf(r, g, b)
    val mean = (r + g + b) / 3
    return when {
        spread <= 10 && mean >= 235 -> InspirationBarClass.GAP
        g >= 180 && b <= 30 && r <= 132 -> InspirationBarClass.SECTION_HEADER
        b >= 210 && r <= 120 && g >= 150 -> InspirationBarClass.STAT
        r >= 235 && g <= 165 && b in 140..215 -> InspirationBarClass.APTITUDE
        g >= 190 && b in 25..90 && r in 125..190 -> InspirationBarClass.UNIQUE
        spread <= 10 && mean in 205..234 -> InspirationBarClass.WHITE
        else -> InspirationBarClass.UNKNOWN
    }
}

internal fun InspirationBarClass.toRowKind(): SparkRowKind? =
    when (this) {
        InspirationBarClass.STAT -> SparkRowKind.STAT
        InspirationBarClass.APTITUDE -> SparkRowKind.APTITUDE
        InspirationBarClass.UNIQUE -> SparkRowKind.UNIQUE
        InspirationBarClass.WHITE -> SparkRowKind.WHITE
        else -> null
    }

/** One card of a row: its pixel-classified kind and per-slot star evidence. The name is OCR'd
 * separately by the reader and is not part of this pixel-only read. */
data class InspirationCell(val kind: SparkRowKind, val slots: List<SparkSlotEvidence>) {
    val filledStars: Int get() = slots.count { it.read == SparkSlotRead.FILLED }
    val ambiguousStars: Int get() = slots.count { it.read == SparkSlotRead.AMBIGUOUS }
}

/**
 * One grid row: always a left card, and a right card except on a block's last row when the factor
 * count is odd. [clipped] marks a run the viewport mask truncated.
 */
data class InspirationRowCell(
    val runTopY: Int,
    val runBottomY: Int,
    val clipped: Boolean,
    val left: InspirationCell,
    val right: InspirationCell?,
) {
    val runHeight: Int get() = runBottomY - runTopY

    /** OCR-free identity used to align two overlapping frames. Deliberately pixel-only: OCR of the
     * same card can differ by a glyph between frames, and an alignment that depended on that would
     * fail exactly when the reader is least able to notice. */
    fun pixelKey(): String =
        "${left.kind.name}:${left.filledStars}|${right?.let { "${it.kind.name}:${it.filledStars}" } ?: "-"}"
}

private const val INSPIRATION_RUN_MERGE_GAP = 8

/**
 * Band-walk one frame: scan the LEFT column top to bottom for maximal runs of a factor-card kind,
 * and for each accepted run probe the right column at the same height.
 *
 * The left column is the walk's spine because the grid fills in reading order: a row exists if and
 * only if it has a left card, and only the right cell can be empty. Antialiased breaks inside one
 * card are bridged ([INSPIRATION_RUN_MERGE_GAP]); the ~18 px panel gap between cards is wider than
 * that, so two stacked white cards stay two rows.
 */
fun readInspirationRows(sampler: SparkPixelSampler, frameHeight: Int): List<InspirationRowCell> {
    val bottom = minOf(INSPIRATION_VIEWPORT_BOTTOM, frameHeight - 3)
    val top = INSPIRATION_VIEWPORT_TOP + 2
    val runs = mutableListOf<IntArray>() // [kindOrdinal, top, bottom]
    var curKind: SparkRowKind? = null
    var curTop = 0

    fun flush(endY: Int) {
        val k = curKind ?: return
        runs.add(intArrayOf(k.ordinal, curTop, endY))
        curKind = null
    }
    var y = top
    while (y <= bottom) {
        val (r, g, b) = inspirationMeanRgb(sampler, INSPIRATION_LEFT_BAR_X, y)
        val kind = classifyInspirationBarSample(r, g, b).toRowKind()
        if (kind != curKind) {
            flush(y - 2)
            if (kind != null) {
                curKind = kind
                curTop = y
            }
        }
        y += 2
    }
    flush(bottom)

    val merged = mutableListOf<IntArray>()
    for (run in runs) {
        val last = merged.lastOrNull()
        if (last != null && last[0] == run[0] && run[1] - last[2] <= INSPIRATION_RUN_MERGE_GAP) {
            last[2] = run[2]
        } else {
            merged.add(run)
        }
    }

    val cells = mutableListOf<InspirationRowCell>()
    for (run in merged) {
        val runTop = run[1]
        val runBottom = run[2]
        if (runBottom - runTop < INSPIRATION_MIN_ROW_HEIGHT) continue
        val kind = SparkRowKind.entries[run[0]]
        val clipped =
            runTop <= top + 1 ||
                runBottom >= bottom - 1 ||
                (runBottom - runTop) < INSPIRATION_FULL_ROW_MIN_HEIGHT
        // The star row is measured from the card's TOP edge, which a clipped-at-the-top run does not
        // have; such a row is discarded by the accumulator anyway, and reading it from the wrong
        // origin would put a fabricated star count into the evidence in the meantime.
        val starY = runTop + INSPIRATION_STAR_DY
        val left = InspirationCell(kind, readStarSlots(sampler, INSPIRATION_LEFT_STAR_XS, starY))
        val rightKind =
            inspirationMeanRgb(sampler, INSPIRATION_RIGHT_BAR_X, (runTop + runBottom) / 2)
                .let { (r, g, b) -> classifyInspirationBarSample(r, g, b).toRowKind() }
        val right = rightKind?.let { InspirationCell(it, readStarSlots(sampler, INSPIRATION_RIGHT_STAR_XS, starY)) }
        cells.add(InspirationRowCell(runTop, runBottom, clipped, left, right))
    }
    return cells
}

private fun readStarSlots(sampler: SparkPixelSampler, xs: List<Int>, starY: Int): List<SparkSlotEvidence> =
    xs.mapIndexed { slot, x ->
        val (r, g, b) = inspirationMeanRgb(sampler, x, starY)
        SparkSlotEvidence(slot, r, g, b, classifyStarSlot(r, b))
    }

/**
 * Clear vertical space that must follow the last factor card in a frame before the factor list can be
 * called finished.
 *
 * Mid-list, the largest space that can ever appear below the last DETECTED card is the block boundary
 * gap: the next card starts 86 px below the previous card's bottom, and it is detected as soon as
 * 45 px of it is visible, so the worst case leaves about 130 px. Anything beyond that means nothing
 * more is coming.
 */
const val INSPIRATION_LIST_END_CLEARANCE = 180

/**
 * True when this frame shows the END of the factor list rather than a viewport-truncated middle of it.
 *
 * This exists because the Inspiration panel does not end with the factors. Below the Legacy Origin
 * blocks sits an inspiration-usage history - one dated row per time this Veteran was borrowed as a
 * parent - which for a popular Veteran runs to thousands of pixels (a live Gold Ship measured a
 * content height of 10,018 px behind eighteen factor rows). Scrolling to the scrollbar's bottom would
 * therefore mean paging through hundreds of history rows that carry no factor data, and treating the
 * scrollbar's content height as the factor list's height is simply wrong.
 *
 * The history rows are white on white and never classify as factor cards, so the end of the factors
 * is visible as a stretch of empty space below the last card.
 */
fun inspirationFactorListEndsInFrame(rows: List<InspirationRowCell>, frameHeight: Int): Boolean {
    val last = rows.lastOrNull() ?: return false
    val bottom = minOf(INSPIRATION_VIEWPORT_BOTTOM, frameHeight - 3)
    return bottom - last.runBottomY >= INSPIRATION_LIST_END_CLEARANCE
}

/** Density of saturated pixels on the left rail beside row-centre [rowY]: a block's owner portrait.
 * Corroborating evidence only - the portrait is taller than one row, so a non-zero density on the
 * row below a block start is normal and is not a second block. */
fun inspirationRailPortraitDensity(sampler: SparkPixelSampler, rowY: Int, frameHeight: Int): Int {
    var saturated = 0
    var yy = rowY - 40
    while (yy <= rowY + 40) {
        if (yy in 1 until frameHeight - 1) {
            var x = INSPIRATION_RAIL_X_START
            while (x <= INSPIRATION_RAIL_X_END) {
                val p = sampler.argb(x, yy)
                val r = ired(p)
                val g = igreen(p)
                val b = iblue(p)
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                if (mx - mn > 50 && mx > 90) saturated++
                x += 6
            }
        }
        yy += 5
    }
    return saturated
}

/** Saturated-pixel count above which a portrait is present on the rail. The bare rail is a thin grey
 * connector line on panel grey and reads 0; the smallest observed real portrait reads 75. */
const val INSPIRATION_PORTRAIT_MIN_DENSITY = 40

/** True when the green "Sparks" section header is visible, which is only so at the very top of the
 * content. The traversal asserts it on its first frame, so a capture that silently began part-way
 * down the list cannot be mistaken for a complete one. */
fun inspirationSparksHeaderVisible(sampler: SparkPixelSampler): Boolean {
    var y = INSPIRATION_VIEWPORT_TOP + 4
    while (y <= INSPIRATION_VIEWPORT_TOP + 120) {
        val (r, g, b) = inspirationMeanRgb(sampler, INSPIRATION_LEFT_BAR_X, y)
        if (classifyInspirationBarSample(r, g, b) == InspirationBarClass.SECTION_HEADER) return true
        y += 4
    }
    return false
}

// -- OCR regions ----------------------------------------------------------------------------------

/**
 * The name band, and why it is measured per card rather than fixed.
 *
 * A short name renders as one line at +19..+38; a long one wraps to two, at +8..+24 and +35..+51,
 * inside a card of exactly the same height. The OCR engine reads ONE line per call - a two-line crop
 * comes back as just the last line, which is how the first live run of this reader turned "Behold
 * Thine Emperor's Divine Might" into "Might". A wrong name is worse than a missing one, so the
 * lines are found in the pixels and read one at a time.
 *
 * The wrapped second line shares its row with the star glyphs, which sit at a fixed x to its right.
 * That line is therefore cropped short of the star column; the first line, and any single line, gets
 * the full card width.
 */
private const val INSPIRATION_NAME_SCAN_TOP = 4
private const val INSPIRATION_NAME_SCAN_BOTTOM = 58
private const val INSPIRATION_NAME_BAND_MIN_INK = 3
private const val INSPIRATION_NAME_BAND_GAP = 5
private const val INSPIRATION_NAME_PAD = 5

/** Name-band x extents per column: the text start (right of the leading circle glyph, whose ink ends
 * at x=252 / x=667), the card's inner right edge, and the left edge of the first star glyph. */
private const val INSPIRATION_LEFT_NAME_X0 = 256
private const val INSPIRATION_LEFT_NAME_X1 = 611
private const val INSPIRATION_LEFT_STAR_X0 = 362
private const val INSPIRATION_RIGHT_NAME_X0 = 671
private const val INSPIRATION_RIGHT_NAME_X1 = 1026
private const val INSPIRATION_RIGHT_STAR_X0 = 777

/** Gap between the last text column a wrapped line may use and the first star glyph. */
private const val INSPIRATION_STAR_CLEARANCE = 6

/** A pixel belongs to the name when it is bright on a saturated card (white text) or dark on a grey
 * one (brown text). The star glyphs satisfy neither test on a coloured card, and are excluded from a
 * white card's scan by the narrow scan window instead. */
private fun inspirationNameInk(sampler: SparkPixelSampler, kind: SparkRowKind, x: Int, y: Int): Boolean {
    val argb = sampler.argb(x, y)
    val luma = (ired(argb) * 299 + igreen(argb) * 587 + iblue(argb) * 114) / 1000
    return if (kind == SparkRowKind.WHITE) luma < 150 else luma > 246
}

private fun inspirationNameRegions(
    sampler: SparkPixelSampler,
    kind: SparkRowKind,
    rowTopY: Int,
    nameX0: Int,
    nameX1: Int,
    starX0: Int,
): List<IntArray> {
    // Bands are found in a window that stops short of the star column, so a white card's star
    // outlines - which do satisfy its dark-ink test - can never be mistaken for a line of text.
    val scanX1 = starX0 - INSPIRATION_STAR_CLEARANCE
    val bands = mutableListOf<IntArray>()
    var open: IntArray? = null
    for (dy in INSPIRATION_NAME_SCAN_TOP..INSPIRATION_NAME_SCAN_BOTTOM) {
        var ink = 0
        var x = nameX0
        while (x <= scanX1) {
            if (inspirationNameInk(sampler, kind, x, rowTopY + dy)) ink++
            x++
        }
        if (ink >= INSPIRATION_NAME_BAND_MIN_INK) {
            if (open == null) open = intArrayOf(dy, dy) else open[1] = dy
        } else if (open != null && dy - open[1] > INSPIRATION_NAME_BAND_GAP) {
            bands.add(open)
            open = null
        }
    }
    open?.let { bands.add(it) }
    if (bands.isEmpty()) return emptyList()
    return bands.mapIndexed { index, band ->
        // Only a genuinely wrapped last line shares its row with the stars; a single line never does.
        val wrappedLastLine = bands.size > 1 && index == bands.lastIndex
        val x1 = if (wrappedLastLine) scanX1 else nameX1
        val y0 = rowTopY + band[0] - INSPIRATION_NAME_PAD
        val y1 = rowTopY + band[1] + INSPIRATION_NAME_PAD
        intArrayOf(nameX0, y0, x1 - nameX0, y1 - y0)
    }
}

/** The left card's name crops, in reading order: one per rendered line of the name. Empty when the
 * card carries no readable text at all. */
fun inspirationLeftNameRegions(sampler: SparkPixelSampler, kind: SparkRowKind, rowTopY: Int): List<IntArray> =
    inspirationNameRegions(sampler, kind, rowTopY, INSPIRATION_LEFT_NAME_X0, INSPIRATION_LEFT_NAME_X1, INSPIRATION_LEFT_STAR_X0)

/** The right card's name crops, in reading order. */
fun inspirationRightNameRegions(sampler: SparkPixelSampler, kind: SparkRowKind, rowTopY: Int): List<IntArray> =
    inspirationNameRegions(sampler, kind, rowTopY, INSPIRATION_RIGHT_NAME_X0, INSPIRATION_RIGHT_NAME_X1, INSPIRATION_RIGHT_STAR_X0)

/**
 * How far this card's name OCR must move the global binarization threshold, because the two card
 * families print their names in opposite polarity.
 *
 * A stat/aptitude/unique card is a saturated bar (luma 150-170) with WHITE text, so the operator's
 * `ocrThreshold` of 230 separates them perfectly. A race/skill card is light grey (luma 224) with
 * DARK BROWN text (luma ~76) - both fall below 230, so the same pass binarizes the entire crop to
 * black and the name reads as an empty string. That is exactly what the first live run of this
 * reader produced: every coloured card's name read, and every white card's name came back blank.
 *
 * Dropping the threshold into the gap between the card and its text fixes it without touching any
 * other reader's calibration, since this is a per-call increment rather than a settings change.
 */
fun inspirationNameThresholdIncrement(kind: SparkRowKind): Double =
    if (kind == SparkRowKind.WHITE) INSPIRATION_WHITE_CARD_THRESHOLD_INCREMENT else 0.0

/** Moves the default 230 threshold to 150, comfortably between the white card's 224 background and
 * its ~76 text. */
const val INSPIRATION_WHITE_CARD_THRESHOLD_INCREMENT: Double = -80.0

// -- Cross-frame merge ----------------------------------------------------------------------------

/** One accepted row placed on the content's own y axis, so two frames of the same list agree on
 * where a row is regardless of how far either was scrolled. */
data class InspirationAbsoluteRow(val contentTopY: Int, val row: InspirationRowCell, val frameIndex: Int)

/** Why an offered frame contributed what it did. [alignedAt] is how many already-accumulated rows
 * had scrolled off above this frame's first row; null when the frame could not be aligned. */
data class InspirationFrameMerge(val added: Int, val alignedAt: Int?, val overlap: Int, val gapDetected: Boolean)

/** How far the scroll-derived row estimate may be wrong before alignment gives up: +/- this many
 * rows are tried around it. Two absorbs the scrollbar's own quantization on any plausible list
 * length; widening it would start admitting genuinely wrong alignments. */
private const val ALIGN_SEARCH_ROWS = 2

/** The smallest overlap that proves two frames are showing the same list at the claimed offset. One
 * row is not proof - "white 1 star / white 1 star" is the single most common row on this screen. */
private const val ALIGN_MIN_OVERLAP = 2

/** How close two rows' content positions must be to be the same row. Half a card pitch, so the match
 * is unambiguous, and far wider than the few pixels of noise a verified scrollbar read carries. */
private const val POSITION_MATCH_SLACK = 40

/**
 * Accumulates the factor rows of one Veteran's Inspiration list across the frames of a bounded
 * scroll, in content order, with no row read twice and no row silently skipped.
 *
 * Two independent signals decide where a new frame belongs, and they must agree:
 *  - the scrollbar's absolute offset, which places the frame's first row on the content axis; and
 *  - a strict pixel-key match over the whole overlap with what is already accumulated.
 *
 * The offset alone is not enough (the thumb quantizes, and the error grows with list length) and the
 * pixel keys alone are not enough (a run of identical white rows aligns at several shifts). Requiring
 * both, with the offset narrowing the search to +/-[ALIGN_SEARCH_ROWS] and the keys confirming the
 * choice, is what makes the merge deterministic rather than a best guess.
 *
 * A frame that cannot be aligned is still accepted, positioned by the scrollbar alone and flagged
 * [InspirationFrameMerge.gapDetected]: content was skipped between the two frames, which the caller
 * must report as an incomplete read rather than quietly concatenate into a plausible-looking list.
 */
class InspirationRowAccumulator {
    private val _rows = mutableListOf<InspirationAbsoluteRow>()
    private var frames = 0
    private var gaps = 0

    val rows: List<InspirationAbsoluteRow> get() = _rows.toList()
    val gapCount: Int get() = gaps

    /**
     * Offers one frame's rows, already read by [readInspirationRows], at the scroll [offset] derived
     * from the scrollbar. Clipped rows are never accepted: the next frame shows them whole.
     *
     * [offsetTrusted] says whether that offset came from a scrollbar reading verified against the
     * at-rest reference. When it did, position is the primary signal and the pixel keys only have to
     * agree; when it did not, the frame has to earn its place by matching pixel keys outright.
     */
    fun offerFrame(offset: Int, frameRows: List<InspirationRowCell>, offsetTrusted: Boolean = true): InspirationFrameMerge {
        val frameIndex = frames++
        val usable = frameRows.filter { !it.clipped }
        if (usable.isEmpty()) return InspirationFrameMerge(0, null, 0, gapDetected = false)

        val absolute =
            usable.map { InspirationAbsoluteRow(it.runTopY - INSPIRATION_VIEWPORT_TOP + offset, it, frameIndex) }
        if (_rows.isEmpty()) {
            _rows.addAll(absolute)
            return InspirationFrameMerge(absolute.size, 0, 0, gapDetected = false)
        }

        if (offsetTrusted) {
            positionalMerge(absolute)?.let { return it }
        }
        val aligned = alignmentFor(absolute)
        if (aligned != null) {
            val (shift, overlap) = aligned
            // Anchor on the rows the two frames share, for the same reason the positional path does.
            val correction = Math.round((0 until overlap).sumOf { _rows[shift + it].contentTopY - absolute[it].contentTopY }.toDouble() / overlap).toInt()
            val newRows = absolute.drop(_rows.size - shift).map { it.copy(contentTopY = it.contentTopY + correction) }
            _rows.addAll(newRows)
            return InspirationFrameMerge(newRows.size, shift, overlap, gapDetected = false)
        }

        // No pixel overlap survives. That is not automatically a gap: a swipe that advanced far enough
        // can land with its first row immediately after the last accepted one, with nothing in common
        // to match on - which happens whenever the row between them was clipped in both frames and so
        // accepted in neither. Row spacing settles it, using the same pitch rule the spacing check uses.
        val lastY = _rows.last().contentTopY
        val delta = absolute.first().contentTopY - lastY
        val contiguous =
            kotlin.math.abs(delta - INSPIRATION_ROW_PITCH) <= INSPIRATION_PITCH_SLACK ||
                kotlin.math.abs(delta - INSPIRATION_BLOCK_PITCH) <= INSPIRATION_PITCH_SLACK
        if (contiguous) {
            _rows.addAll(absolute)
            return InspirationFrameMerge(absolute.size, _rows.size - absolute.size, 0, gapDetected = false)
        }
        // Genuinely discontinuous. Keep only rows the scrollbar places strictly after everything already
        // held, so a partially-overlapping unalignable frame cannot duplicate rows on top of a gap.
        gaps++
        val newRows = absolute.filter { it.contentTopY > lastY + INSPIRATION_ROW_PITCH / 2 }
        _rows.addAll(newRows)
        return InspirationFrameMerge(newRows.size, null, 0, gapDetected = true)
    }

    /**
     * Merges by content position, which is what a verified scrollbar offset actually gives.
     *
     * Every incoming row within half a pitch of one already held IS that row, and its pixel key must
     * agree; every other row is new and must fall after everything held. Returns null when either
     * condition breaks, so the stricter pixel-key alignment can have its turn instead of this quietly
     * writing a row into the wrong place.
     *
     * This path exists because the panel's scroller adds inertia: a swipe asked to advance four card
     * pitches actually advances five and a half, which leaves as little as two rows of overlap between
     * consecutive frames. Two rows is too thin a base for pixel matching to be reliable on a screen
     * whose most common row is "white one star / white one star", and a failed match there was
     * reported as a content gap on otherwise perfect reads.
     *
     * New rows are anchored on the shared ones rather than trusted at their own computed position. A
     * scrollbar offset is only as fine as one thumb pixel is worth, which on a panel with a long usage
     * history is fourteen pixels of content; two frames each off by that much put adjacent rows up to
     * twenty-eight pixels out, and the row spacing check - which has to stay tight enough to tell one
     * block boundary (150 px) from two missed cards (177 px) - then reports a break that is really just
     * measurement noise. Correcting by the shared rows removes the frame's error entirely.
     */
    private fun positionalMerge(absolute: List<InspirationAbsoluteRow>): InspirationFrameMerge? {
        val lastY = _rows.last().contentTopY
        var firstMatchIndex: Int? = null
        var matches = 0
        var driftSum = 0
        val fresh = mutableListOf<InspirationAbsoluteRow>()
        // Pass one: which incoming rows are rows already held, and by how much is this frame adrift.
        for (row in absolute) {
            val nearest = _rows.indices.minByOrNull { kotlin.math.abs(_rows[it].contentTopY - row.contentTopY) }
            if (nearest != null && kotlin.math.abs(_rows[nearest].contentTopY - row.contentTopY) <= POSITION_MATCH_SLACK) {
                if (_rows[nearest].row.pixelKey() != row.row.pixelKey()) return null
                if (firstMatchIndex == null) firstMatchIndex = nearest
                driftSum += _rows[nearest].contentTopY - row.contentTopY
                matches++
            } else {
                fresh.add(row)
            }
        }
        val correction = if (matches > 0) Math.round(driftSum.toDouble() / matches).toInt() else 0
        val corrected = fresh.map { it.copy(contentTopY = it.contentTopY + correction) }
        // Pass two, against the CORRECTED positions: every remaining row has to fall after everything
        // held. Judging that before the correction is applied rejects perfectly good frames on a panel
        // whose thumb is too coarse to place a row to better than a dozen pixels.
        if (corrected.any { it.contentTopY <= lastY + POSITION_MATCH_SLACK }) return null
        val delta = corrected.firstOrNull()?.let { it.contentTopY - lastY }
        val contiguous =
            delta == null ||
                kotlin.math.abs(delta - INSPIRATION_ROW_PITCH) <= INSPIRATION_PITCH_SLACK ||
                kotlin.math.abs(delta - INSPIRATION_BLOCK_PITCH) <= INSPIRATION_PITCH_SLACK
        if (!contiguous) gaps++
        _rows.addAll(corrected)
        return InspirationFrameMerge(corrected.size, firstMatchIndex ?: _rows.size - corrected.size, matches, gapDetected = !contiguous)
    }

    /** The (shift, overlap) whose pixel keys agree over the entire overlap, searched around the
     * scroll-derived estimate. Null when no candidate agrees or the overlap is too short to prove. */
    private fun alignmentFor(absolute: List<InspirationAbsoluteRow>): Pair<Int, Int>? {
        val estimate = _rows.indices.minByOrNull { kotlin.math.abs(_rows[it].contentTopY - absolute.first().contentTopY) } ?: return null
        var best: Pair<Int, Int>? = null
        var bestDistance = Int.MAX_VALUE
        for (shift in (estimate - ALIGN_SEARCH_ROWS)..(estimate + ALIGN_SEARCH_ROWS)) {
            if (shift < 0 || shift > _rows.size) continue
            val overlap = minOf(absolute.size, _rows.size - shift)
            if (overlap < ALIGN_MIN_OVERLAP) continue
            var agrees = true
            for (i in 0 until overlap) {
                if (_rows[shift + i].row.pixelKey() != absolute[i].row.pixelKey()) {
                    agrees = false
                    break
                }
            }
            if (!agrees) continue
            val distance = kotlin.math.abs(shift - estimate)
            if (distance < bestDistance) {
                best = shift to overlap
                bestDistance = distance
            }
        }
        return best
    }

    /** Row spacings that are neither the in-block card pitch nor a block-boundary pitch: each one is
     * content the traversal never saw. Returns the content y of the row AFTER each break. */
    fun spacingBreaks(): List<Int> =
        _rows.zipWithNext()
            .filter { (a, b) ->
                val delta = (b.contentTopY - a.contentTopY).toDouble()
                kotlin.math.abs(delta - INSPIRATION_ROW_PITCH) > INSPIRATION_PITCH_SLACK &&
                    kotlin.math.abs(delta - INSPIRATION_BLOCK_PITCH) > INSPIRATION_PITCH_SLACK
            }
            .map { it.second.contentTopY }

    /** The content height the accumulated rows themselves imply, to be compared against the height
     * the scrollbar reported. Agreement is the traversal's own proof that nothing fell off the end. */
    fun observedContentHeight(): Int? =
        _rows.lastOrNull()?.let { it.contentTopY + INSPIRATION_CARD_HEIGHT + INSPIRATION_BOTTOM_PADDING }
}

/**
 * Segments the merged rows into blocks. Exactly one blue STAT card leads each block - the Veteran's
 * own Sparks block first, then one block per Legacy Origin ancestor - so a STAT row in the LEFT
 * column opens a block and everything below it belongs to that block until the next one.
 *
 * Rows before the first STAT row would mean the traversal began part-way down the list; they are
 * returned as block index -1 rather than folded into block 0, so the caller can refuse the capture
 * instead of attributing an ancestor's factors to the Veteran itself.
 */
fun segmentInspirationBlocks(rows: List<InspirationAbsoluteRow>): List<Pair<Int, List<InspirationAbsoluteRow>>> {
    val blocks = mutableListOf<Pair<Int, List<InspirationAbsoluteRow>>>()
    var current = mutableListOf<InspirationAbsoluteRow>()
    var index = -1

    fun close() {
        if (current.isNotEmpty()) {
            blocks.add(index to current.toList())
            current = mutableListOf()
        }
    }
    for (row in rows) {
        if (row.row.left.kind == SparkRowKind.STAT) {
            close()
            index += 1
        }
        current.add(row)
    }
    close()
    return blocks
}
