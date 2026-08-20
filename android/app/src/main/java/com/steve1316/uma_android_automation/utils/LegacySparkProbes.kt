package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.SparkRowKind

/**
 * Pixel probes and geometry for the Legacy Select "Sparks" sub-view: the reversible full-lineage
 * factor list opened from the populated Legacy Select summary. Each parent and grandparent appears
 * as an ancestor BLOCK - a blue stat row, a pink aptitude row, a green unique row, then the white
 * race/skill rows - with the ancestor's portrait pinned to a genealogy rail on the left.
 *
 * Deliberately separate from [SparkScreenProbes] (the career-end spark screens): the bar palette,
 * the row geometry, the star columns, and the scrollable per-ancestor block structure all differ.
 * What IS genuinely identical is reused: [SparkRowKind], and the star-slot classifier
 * [classifyStarSlot] with its [SparkSlotRead] / [SparkSlotEvidence] evidence types (a filled Legacy
 * star reads (254,214,77) and an empty one (231,227,223), the same pixels the career-end stars use).
 *
 * Every coordinate and threshold here was measured on the live 2026-08-20 PL-4a capture set
 * (1080x1920, validation/parentlab-pl4a-legacy) and is pinned by fixture tests against those exact
 * pixels. Android-free on purpose: the runtime wraps a Bitmap in a [SparkPixelSampler], the JUnit
 * fixtures wrap a decoded PNG.
 */

private fun lred(argb: Int): Int = (argb shr 16) and 0xFF

private fun lgreen(argb: Int): Int = (argb shr 8) and 0xFF

private fun lblue(argb: Int): Int = argb and 0xFF

/** 5x5 channel mean around (cx, cy), matching the smoothing every spark row reader uses. */
private fun lmean5(sampler: SparkPixelSampler, cx: Int, cy: Int, channel: (Int) -> Int): Int {
    var sum = 0
    for (dy in -2..2) {
        for (dx in -2..2) {
            sum += channel(sampler.argb(cx + dx, cy + dy))
        }
    }
    return sum / 25
}

private fun lmeanRgb(sampler: SparkPixelSampler, cx: Int, cy: Int): Triple<Int, Int, Int> =
    Triple(lmean5(sampler, cx, cy, ::lred), lmean5(sampler, cx, cy, ::lgreen), lmean5(sampler, cx, cy, ::lblue))

// -- Geometry, measured on 1080x1920 Legacy "Sparks" captures ------------------------------------

/** Bar-kind sampling column: inside the colored bar / white card, left of the name text end and
 * well left of the first star column. */
const val LEGACY_BAR_SAMPLE_X = 770

/** Star-slot sample centers. Distinct from the career-end screen's 846/894/941. */
val LEGACY_STAR_XS: List<Int> = listOf(816, 862, 907)

/** Top of the scrollable list band (below the green "Sparks" header). */
const val LEGACY_LIST_TOP_Y = 150

/** Bottom of the scrollable list band (above the "Close" button). */
const val LEGACY_LIST_BOTTOM_Y = 1690

/** A real factor row's bar is ~92-96 px tall; the "1st Legacy" / "2nd Legacy" divider pill is
 * ~36 px. This floor rejects the pill and any half-scrolled edge sliver, so only fully-visible
 * rows are emitted and a partially-scrolled row is simply read whole on the next frame (the
 * cross-frame de-dupe the traversal relies on). */
const val LEGACY_MIN_ROW_HEIGHT = 70

/** A fully-visible factor row's bar runs ~92-96 px. A run that clears [LEGACY_MIN_ROW_HEIGHT] but
 * falls short of this is a row the list mask truncated (the Sparks list clips its last row above
 * the Close button, before the scan bottom), so it is emitted but flagged clipped. */
const val LEGACY_FULL_ROW_MIN_HEIGHT = 86

/** Left genealogy rail band where an ancestor's portrait sits; a portrait aligns with its block's
 * stat (first) row, so saturated content here at a stat row confirms a block start. */
const val LEGACY_RAIL_X_START = 40
const val LEGACY_RAIL_X_END = 195

/** How a single bar-sample classifies. STAT/APTITUDE/UNIQUE/WHITE are factor rows; SECTION_PILL is
 * the "Nth Legacy" divider; GAP is the white space between cards; UNKNOWN is anything else (chrome,
 * a portrait bleeding into the sample column) and never becomes a row. */
enum class LegacyBarClass { STAT, APTITUDE, UNIQUE, WHITE, SECTION_PILL, GAP, UNKNOWN }

/**
 * Classify one smoothed bar sample. The STAT/APTITUDE/UNIQUE/WHITE tests are the gradient-tolerant
 * hue logic the career-end reader proved (a colored bar is glossed, so a nearest-fixed-color match
 * drops its antialiased top). The one Legacy-specific addition is SECTION_PILL: the divider pill is
 * a dark, near-zero-blue green (117,201,12) that the plain UNIQUE test (154,217,52) would otherwise
 * swallow, so it is separated FIRST by its very low blue channel.
 */
fun classifyLegacyBarSample(r: Int, g: Int, b: Int): LegacyBarClass =
    when {
        r >= 245 && g >= 245 && b >= 245 -> LegacyBarClass.GAP
        g >= 150 && g - r >= 55 && b < 35 -> LegacyBarClass.SECTION_PILL
        b > 240 && r < 150 -> LegacyBarClass.STAT
        r > 235 && g < 185 && b > 150 -> LegacyBarClass.APTITUDE
        g > 195 && b < 100 -> LegacyBarClass.UNIQUE
        r in 190..238 && kotlin.math.abs(r - g) < 18 && kotlin.math.abs(g - b) < 18 -> LegacyBarClass.WHITE
        else -> LegacyBarClass.UNKNOWN
    }

private fun LegacyBarClass.toRowKind(): SparkRowKind? =
    when (this) {
        LegacyBarClass.STAT -> SparkRowKind.STAT
        LegacyBarClass.APTITUDE -> SparkRowKind.APTITUDE
        LegacyBarClass.UNIQUE -> SparkRowKind.UNIQUE
        LegacyBarClass.WHITE -> SparkRowKind.WHITE
        else -> null
    }

/** One parsed Legacy factor row: the bar kind, the run it occupied, whether the run touched a
 * viewport edge (clipped), whether a portrait sits on the rail beside it (a block-start signal),
 * and the per-slot star evidence. */
data class LegacyRowCell(
    val kind: SparkRowKind,
    val rowY: Int,
    val runTopY: Int,
    val runBottomY: Int,
    val clipped: Boolean,
    val portraitOnRail: Boolean,
    val slots: List<SparkSlotEvidence>,
) {
    val filledStars: Int get() = slots.count { it.read == SparkSlotRead.FILLED }
    val ambiguousStars: Int get() = slots.count { it.read == SparkSlotRead.AMBIGUOUS }
    val runHeight: Int get() = runBottomY - runTopY
}

private const val LEGACY_RUN_MERGE_GAP = 12

/**
 * Band-walk one frame of the Sparks list: scan the bar-sample column top to bottom, group maximal
 * runs of one factor-row kind (the divider pill, inter-card gaps, and chrome are separators), and
 * emit one row per run that clears [LEGACY_MIN_ROW_HEIGHT], with its stars and rail-portrait
 * evidence. Antialiased breaks inside one bar are bridged ([LEGACY_RUN_MERGE_GAP]); the ~28 px gaps
 * between white cards are wider than that, so adjacent white rows stay distinct.
 */
fun readLegacySparkRows(sampler: SparkPixelSampler, frameHeight: Int): List<LegacyRowCell> {
    val bottom = minOf(LEGACY_LIST_BOTTOM_Y, frameHeight - 3)
    // Collect (kind, top, bottom) runs of factor-row kinds; null-kind samples break a run.
    val runs = mutableListOf<IntArray>() // [kindOrdinal, top, bottom]
    var curKind: SparkRowKind? = null
    var curTop = 0
    var y = LEGACY_LIST_TOP_Y
    fun flush(endY: Int) {
        val k = curKind ?: return
        runs.add(intArrayOf(k.ordinal, curTop, endY))
        curKind = null
    }
    while (y <= bottom) {
        val (r, g, b) = lmeanRgb(sampler, LEGACY_BAR_SAMPLE_X, y)
        val kind = classifyLegacyBarSample(r, g, b).toRowKind()
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
    // Merge same-kind runs separated only by an antialiasing sliver.
    val merged = mutableListOf<IntArray>()
    for (run in runs) {
        val last = merged.lastOrNull()
        if (last != null && last[0] == run[0] && run[1] - last[2] <= LEGACY_RUN_MERGE_GAP) {
            last[2] = run[2]
        } else {
            merged.add(run)
        }
    }
    val cells = mutableListOf<LegacyRowCell>()
    for (run in merged) {
        val top = run[1]
        val bot = run[2]
        if (bot - top < LEGACY_MIN_ROW_HEIGHT) continue
        val kind = SparkRowKind.entries[run[0]]
        val cy = (top + bot) / 2
        val clipped = top <= LEGACY_LIST_TOP_Y + 2 || bot >= bottom - 2 || (bot - top) < LEGACY_FULL_ROW_MIN_HEIGHT
        val slots =
            LEGACY_STAR_XS.mapIndexed { slot, x ->
                val (sr, sg, sb) = lmeanRgb(sampler, x, cy)
                SparkSlotEvidence(slot, sr, sg, sb, classifyStarSlot(sr, sb))
            }
        cells.add(
            LegacyRowCell(
                kind = kind,
                rowY = cy,
                runTopY = top,
                runBottomY = bot,
                clipped = clipped,
                portraitOnRail = legacyPortraitPresentAt(sampler, cy, frameHeight),
                slots = slots,
            ),
        )
    }
    return cells
}

/**
 * Whether a portrait sits on the genealogy rail beside row-center [rowY]. A portrait pins to its
 * block's first (stat) row, so this reads true at a block start and false on the aptitude/unique/
 * white rows below it - an independent cross-check on the stat-row block boundary. Measured by the
 * density of saturated (non-grey, non-white) pixels in the rail band over a portrait-height window.
 */
fun legacyPortraitPresentAt(sampler: SparkPixelSampler, rowY: Int, frameHeight: Int): Boolean {
    var saturated = 0
    var samples = 0
    var yy = rowY - 45
    while (yy <= rowY + 45) {
        if (yy in 1 until frameHeight - 1) {
            var x = LEGACY_RAIL_X_START
            while (x <= LEGACY_RAIL_X_END) {
                val p = sampler.argb(x, yy)
                val r = lred(p)
                val g = lgreen(p)
                val b = lblue(p)
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                if (mx - mn > 50 && mx > 90) saturated++
                samples++
                x += 6
            }
        }
        yy += 5
    }
    // ~60 saturated hits over the window on a real portrait; the bare rail (a grey tree line on
    // white) stays near zero.
    return samples > 0 && saturated > 60
}

/**
 * One ancestor's block: its lead stat/aptitude/unique rows and the white race/skill rows below,
 * as segmented from a single frame's rows. [index] is the block's ordinal within the frame.
 */
data class LegacyAncestorBlock(val index: Int, val rows: List<LegacyRowCell>) {
    val statRow: LegacyRowCell? get() = rows.firstOrNull { it.kind == SparkRowKind.STAT }
    val aptitudeRow: LegacyRowCell? get() = rows.firstOrNull { it.kind == SparkRowKind.APTITUDE }
    val uniqueRow: LegacyRowCell? get() = rows.firstOrNull { it.kind == SparkRowKind.UNIQUE }
    val whiteRows: List<LegacyRowCell> get() = rows.filter { it.kind == SparkRowKind.WHITE }

    /** A complete block opens stat, then aptitude, then unique. Partially-scrolled blocks (a
     * grandparent whose stat row scrolled off the top) legitimately miss the lead and are reported
     * as such rather than dropped. */
    val hasLeadTriple: Boolean
        get() =
            rows.size >= 3 &&
                rows[0].kind == SparkRowKind.STAT &&
                rows[1].kind == SparkRowKind.APTITUDE &&
                rows[2].kind == SparkRowKind.UNIQUE

    /** Only a fully-visible, portrait-anchored, complete block may be accepted as an ancestor: the
     * stat row leads it, its portrait sits on the rail, and no row was truncated by the list mask.
     * A block that fails this is re-read whole after the next scroll. */
    fun isAcceptable(): Boolean = hasLeadTriple && rows.none { it.clipped } && (statRow?.portraitOnRail == true)

    /** A pixel-only identity for cross-frame de-dupe: the lead-triple star counts plus the sequence
     * of white-row star counts. Stable across the overlap between two scrolled frames (it needs no
     * OCR), and distinctive enough that re-seeing the same ancestor collapses to one entry. */
    fun starSignature(): String {
        val lead = listOf(statRow?.filledStars ?: -1, aptitudeRow?.filledStars ?: -1, uniqueRow?.filledStars ?: -1)
        val whites = whiteRows.map { it.filledStars }
        return "${lead.joinToString(",")}|${whites.joinToString(",")}"
    }
}

/**
 * Accumulates ancestor blocks across the overlapping frames of a bounded scroll. Frames are offered
 * in scroll order; each fully-visible, not-yet-seen block is accepted once (de-duped by its
 * [LegacyAncestorBlock.starSignature]) and appended, preserving top-to-bottom ancestor order.
 * Consecutive frames deliberately overlap so a block clipped at one frame's bottom is accepted whole
 * from the next, never skipped and never double-counted.
 *
 * Pure and Android-free so the de-dupe logic - the part hardest to get right live - is unit-tested
 * against the real fixtures rather than only the emulator.
 */
class LegacyLineageAccumulator(val expectedAncestors: Int = LEGACY_EXPECTED_ANCESTORS) {
    private val acceptedSignatures = mutableListOf<String>()
    private val _ancestors = mutableListOf<LegacyAncestorBlock>()

    /** Accepted ancestors so far, in capture order. */
    val ancestors: List<LegacyAncestorBlock> get() = _ancestors.toList()

    /** How many consecutive offered frames added nothing new - the stall the traversal bounds on. */
    var stalledRounds: Int = 0
        private set

    val complete: Boolean get() = _ancestors.size >= expectedAncestors

    /** Offer one frame's rows; returns how many new ancestors it contributed. */
    fun offerFrame(rows: List<LegacyRowCell>): Int {
        var added = 0
        for (block in segmentLegacyBlocks(rows)) {
            if (_ancestors.size >= expectedAncestors) break
            if (block.index < 0 || !block.isAcceptable()) continue
            val sig = block.starSignature()
            if (sig in acceptedSignatures) continue
            acceptedSignatures.add(sig)
            _ancestors.add(block)
            added++
        }
        stalledRounds = if (added == 0) stalledRounds + 1 else 0
        return added
    }
}

/** The Legacy summary always shows exactly six ancestors: two parents and their four grandparents. */
const val LEGACY_EXPECTED_ANCESTORS = 6

/**
 * Segment a frame's rows into ancestor blocks. A STAT (blue) row begins a new block - the one
 * colored bar that appears exactly once per ancestor and always leads the block. Rows before the
 * first STAT row (the tail of a block whose stat row scrolled off the top) form a leading partial
 * block at index -1 so nothing is silently dropped.
 */
fun segmentLegacyBlocks(rows: List<LegacyRowCell>): List<LegacyAncestorBlock> {
    val blocks = mutableListOf<LegacyAncestorBlock>()
    var current = mutableListOf<LegacyRowCell>()
    var index = -1
    fun close() {
        if (current.isNotEmpty()) {
            blocks.add(LegacyAncestorBlock(index, current.toList()))
            current = mutableListOf()
        }
    }
    for (row in rows) {
        if (row.kind == SparkRowKind.STAT) {
            close()
            index += 1
        }
        current.add(row)
    }
    close()
    return blocks
}

// -- OCR regions and evidence anchors (x, y, w, h on 1080x1920 frames) ---------------------------

/** The factor-name text band for a row centered at [rowY]: left of the star column. Fed to
 * performOCROnRegion by the navigator; the fixture tests pin it inside the measured text band.
 * The x-start sits in the empty gap between each row's leading open-circle glyph (ink ends at
 * x=279 on the live 1080x1920 fixtures) and the name text (ink starts at x=288), so the circle no
 * longer OCRs as a spurious leading "O"/"0" that polluted displayText / factorFingerprint. Right
 * edge held at x=720, well left of the first star column (816). */
fun legacyNameOcrRegion(rowY: Int): IntArray = intArrayOf(284, rowY - 34, 436, 68)

/** The rail portrait+rank crop for a stat row at [rowY], retained as identity evidence. Rank text
 * is a stylized badge that is not reliably OCR-able offline, so this task keeps the crop reference
 * and leaves rank/character unresolved rather than fabricating a read. */
fun legacyPortraitCropRegion(rowY: Int): IntArray = intArrayOf(30, rowY - 60, 175, 130)
