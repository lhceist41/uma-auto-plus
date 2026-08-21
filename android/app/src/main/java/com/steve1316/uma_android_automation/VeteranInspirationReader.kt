package com.steve1316.uma_android_automation

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.InspirationBlockObservation
import com.steve1316.uma_android_automation.bot.InspirationColumn
import com.steve1316.uma_android_automation.bot.InspirationDiagnostics
import com.steve1316.uma_android_automation.bot.InspirationFactor
import com.steve1316.uma_android_automation.bot.InspirationReadTermination
import com.steve1316.uma_android_automation.bot.SparkRowKind
import com.steve1316.uma_android_automation.bot.VeteranInspirationObservation
import com.steve1316.uma_android_automation.bot.assembleVeteranInspiration
import com.steve1316.uma_android_automation.bot.entryFingerprint
import com.steve1316.uma_android_automation.utils.DETAIL_TAB_CY
import com.steve1316.uma_android_automation.utils.DETAIL_TAB_INSPIRATION_CX
import com.steve1316.uma_android_automation.utils.INSPIRATION_PORTRAIT_MIN_DENSITY
import com.steve1316.uma_android_automation.utils.INSPIRATION_ROW_PITCH
import com.steve1316.uma_android_automation.utils.INSPIRATION_SWIPE_LOW_Y
import com.steve1316.uma_android_automation.utils.INSPIRATION_SWIPE_X
import com.steve1316.uma_android_automation.utils.INSPIRATION_TRACK_TOP
import com.steve1316.uma_android_automation.utils.InspirationAbsoluteRow
import com.steve1316.uma_android_automation.utils.InspirationRowAccumulator
import com.steve1316.uma_android_automation.utils.InspirationScrollRead
import com.steve1316.uma_android_automation.utils.RosterScreenKind
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.VeteranFactorDomain
import com.steve1316.uma_android_automation.utils.VeteranIdentityCatalog
import com.steve1316.uma_android_automation.utils.deniedZoneAt
import com.steve1316.uma_android_automation.utils.inspirationFactorListEndsInFrame
import com.steve1316.uma_android_automation.utils.inspirationLeftNameRegions
import com.steve1316.uma_android_automation.utils.inspirationNameThresholdIncrement
import com.steve1316.uma_android_automation.utils.inspirationOffsetAgainst
import com.steve1316.uma_android_automation.utils.inspirationOffsetPreciseEnough
import com.steve1316.uma_android_automation.utils.inspirationRailPortraitDensity
import com.steve1316.uma_android_automation.utils.inspirationRightNameRegions
import com.steve1316.uma_android_automation.utils.inspirationScrollTrustworthy
import com.steve1316.uma_android_automation.utils.inspirationSparksHeaderVisible
import com.steve1316.uma_android_automation.utils.inspirationTabActive
import com.steve1316.uma_android_automation.utils.readInspirationRows
import com.steve1316.uma_android_automation.utils.readInspirationScroll
import com.steve1316.uma_android_automation.utils.segmentInspirationBlocks

private const val TAG = "[VeteranInspirationReader]"

/** How many card pitches one traversal swipe advances. Four leaves roughly four rows of overlap in
 * the ~8.7-row viewport, which is what [InspirationRowAccumulator] needs to prove two frames show the
 * same list. Bigger swipes are faster and, on a long list, silently skip rows - the exact failure the
 * two 2026-08-21 manual captures already demonstrated. */
private const val SWIPE_ROWS = 4

/** The same, for a panel whose scrollbar is too coarse to place rows (see
 * [inspirationOffsetPreciseEnough]). There the merge has to align on the pixels alone, so it is worth
 * an extra frame to hand it a wider overlap. */
private const val SWIPE_ROWS_COARSE = 3

/** Swipe duration. Long enough that the scroller treats it as a drag rather than a fling: a fling
 * overshoots by an unbounded amount and would break the overlap the merge depends on. */
private const val SWIPE_DURATION_MS = 900L

/** Bounded swipe budget for one Veteran. The longest list observed needs three; twelve leaves room
 * for a much larger ancestry without ever becoming an unbounded scroll loop. */
private const val MAX_SWIPES = 12

/** Bounded budget for returning the panel to the top before a read. */
private const val MAX_RESET_SWIPES = 8

/** Consecutive swipes that add no new row before the traversal gives up. Keyed on CONTENT rather
 * than on the scrollbar: a bouncing panel reports a different offset on every frame, so an
 * offset-based stall check never fires and the swipe budget is burned instead. */
private const val STALL_LIMIT = 2

/** How many times a frame is re-captured while its scrollbar thumb still disagrees with the length
 * measured at rest, and how long to wait between tries. The panel over-scrolls with a rubber-band
 * bounce that shrinks the thumb; capturing during it produced content heights up to six times the
 * real one in the 20-Veteran validation run. */
private const val SCROLL_SETTLE_ATTEMPTS = 5
private const val SCROLL_SETTLE_DELAY_SECONDS = 0.35

/** Settle time after a swipe or a tab tap. */
private const val SETTLE_SECONDS = 0.8

/**
 * Reads one parked Veteran's `Umamusume Details` -> Inspiration panel: the Veteran's own Sparks block
 * and the Legacy Origin ancestor blocks below it.
 *
 * The panel is a nested scroll viewport roughly 8.7 rows tall over a list that routinely runs to
 * twenty, so this cannot be a single-frame read. It performs exactly three kinds of gesture, all
 * read-only: it selects the Inspiration tab if another tab is showing, it swipes inside the panel,
 * and it swipes back to the top afterwards. It never taps a factor card, the favorite marker, the
 * epithet pencil, Change, share, Transfer, or any other control - the swipe coordinate deliberately
 * sits in the empty gutter between the two card columns, and every tap coordinate is checked against
 * [deniedZoneAt] before it is dispatched.
 *
 * Completeness is proved, not assumed. The scrollbar gives an absolute scroll offset and a total
 * content height; the merge requires overlapping frames to agree pixel-for-pixel before it joins
 * them; and the assembled record is only marked complete when the traversal started at the top of the
 * content, ended at the bottom, merged with no gap, and independently agreed with the scrollbar on
 * how tall the list was.
 */
class VeteranInspirationReader(private val game: Game, private val factorDomain: VeteranFactorDomain? = null) {
    private val iu = game.imageUtils

    /** One row's per-frame evidence - the two OCR'd names and whether a portrait sat on the rail
     * beside it - kept in lockstep with the accumulator's rows and read from the frame that actually
     * contributed the row, which is the only frame where its pixels are in hand. */
    private data class RowRead(val left: String, val right: String?, val portrait: Boolean)

    private fun safeTap(x: Int, y: Int, label: String): Boolean {
        val denied = deniedZoneAt(RosterScreenKind.UMAMUSUME_DETAILS, x, y)
        if (denied != null) {
            MessageLog.e(TAG, "[INSPIRATION] Refusing to tap $label at ($x, $y): inside deny zone ${denied.label}.")
            return false
        }
        game.tapCoordinate(x.toDouble(), y.toDouble(), label)
        return true
    }

    /** Reads one card's name, one rendered line per OCR call, joined in reading order. The engine
     * treats a crop as a single line of text, so a two-line crop returns only its last line. */
    private fun ocrName(bitmap: Bitmap, regions: List<IntArray>, kind: SparkRowKind, debugName: String): String =
        regions.mapIndexed { i, region -> ocr(bitmap, region, kind, "${debugName}_$i") }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    private fun ocr(bitmap: Bitmap, region: IntArray, kind: SparkRowKind, debugName: String): String =
        try {
            iu.performOCROnRegion(
                bitmap,
                region[0],
                region[1],
                region[2],
                region[3],
                scale = 2.0,
                debugName = debugName,
                thresholdIncrement = inspirationNameThresholdIncrement(kind),
            )
                .replace("\r", "")
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            ""
        }

    /** Selects the Inspiration tab when another tab is showing. Returns the frame it verified on, or
     * null when the tab could not be confirmed active. */
    fun ensureInspirationTab(): Bitmap? {
        var bitmap = iu.getSourceBitmap()
        if (inspirationTabActive(sampler(bitmap))) return bitmap
        if (!safeTap(DETAIL_TAB_INSPIRATION_CX, DETAIL_TAB_CY, "veteran_detail_inspiration_tab")) return null
        game.wait(SETTLE_SECONDS)
        bitmap = iu.getSourceBitmap()
        if (inspirationTabActive(sampler(bitmap))) return bitmap
        MessageLog.w(TAG, "[INSPIRATION] The Inspiration tab did not become active after one tap; not retrying blindly.")
        return null
    }

    /** Swipes the panel back to the top of its content, bounded. Returns true when the scrollbar
     * confirms the top. Called before each read so a capture never inherits the previous Veteran's
     * scroll position. */
    fun scrollPanelToTop(): Boolean {
        for (attempt in 0 until MAX_RESET_SWIPES) {
            val scroll = readInspirationScroll(sampler(iu.getSourceBitmap()))
            if (scroll.atTop) return true
            swipe(down = true)
        }
        return readInspirationScroll(sampler(iu.getSourceBitmap())).atTop
    }

    private fun sampler(bitmap: Bitmap) = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }

    /** One captured frame and its scroll state, plus whether that state can be trusted. */
    private data class Frame(val bitmap: Bitmap, val scroll: InspirationScrollRead, val trusted: Boolean)

    /**
     * Captures a frame whose scrollbar has stopped moving.
     *
     * [referenceThumbLength] is the thumb measured with the panel at rest at the top of its content.
     * Because that length is a property of the list, a frame reporting a different one was captured
     * mid-bounce; re-capturing outlasts the bounce. A frame that never settles is returned anyway,
     * flagged untrusted, so the caller can fall back to dead reckoning rather than believing it.
     */
    private fun captureSettledFrame(referenceThumbLength: Int?): Frame {
        var bitmap = iu.getSourceBitmap()
        var scroll = readInspirationScroll(sampler(bitmap))
        if (referenceThumbLength == null) return Frame(bitmap, scroll, trusted = true)
        for (attempt in 2..SCROLL_SETTLE_ATTEMPTS) {
            if (inspirationScrollTrustworthy(scroll, referenceThumbLength)) return Frame(bitmap, scroll, trusted = true)
            game.wait(SCROLL_SETTLE_DELAY_SECONDS)
            bitmap = iu.getSourceBitmap()
            scroll = readInspirationScroll(sampler(bitmap))
        }
        return Frame(bitmap, scroll, inspirationScrollTrustworthy(scroll, referenceThumbLength))
    }

    private fun swipe(down: Boolean, rows: Int = SWIPE_ROWS) {
        val distance = (rows * INSPIRATION_ROW_PITCH).toFloat()
        // Both endpoints sit inside the scroll viewport, in the empty gutter between the two card
        // columns. A drag never becomes a click, and this column carries no control even if it did.
        val gutterX = INSPIRATION_SWIPE_X.toFloat()
        val lowY = INSPIRATION_SWIPE_LOW_Y.toFloat()
        val highY = lowY + distance
        if (down) {
            game.gestureUtils.swipe(gutterX, lowY, gutterX, highY, duration = SWIPE_DURATION_MS)
        } else {
            game.gestureUtils.swipe(gutterX, highY, gutterX, lowY, duration = SWIPE_DURATION_MS)
        }
        game.wait(SETTLE_SECONDS)
    }

    /**
     * Captures one Veteran's whole Inspiration panel. The caller supplies the identity this evidence
     * attaches to; it is echoed into the record and never re-derived here, so the roster walk's
     * fingerprint and this record's fingerprint can never disagree.
     */
    fun capture(
        scanId: String,
        scanIndex: Int,
        rosterFingerprint: String?,
        character: String?,
        outfit: String?,
        rank: String?,
        verbose: Boolean = false,
    ): VeteranInspirationObservation {
        val observedAt = System.currentTimeMillis()
        val accumulator = InspirationRowAccumulator()
        val names = mutableListOf<RowRead>()
        var frames = 0
        var swipes = 0
        var clippedRejected = 0
        var alignmentFailures = 0
        var unsettledFrames = 0
        var deadReckonedFrames = 0
        var stalls = 0

        if (ensureInspirationTab() == null) {
            return finish(
                scanId,
                scanIndex,
                observedAt,
                rosterFingerprint,
                character,
                outfit,
                rank,
                accumulator,
                names,
                InspirationReadTermination.PANEL_NOT_READY,
                frames,
                swipes,
                clippedRejected,
                alignmentFailures,
                unsettledFrames,
                deadReckonedFrames,
                startedAtTop = false,
                reference = null,
                reachedBottom = false,
                listEndObserved = false,
                verbose = verbose,
            )
        }
        if (!scrollPanelToTop()) {
            MessageLog.w(TAG, "[INSPIRATION] Could not return the panel to the top of its content; refusing to claim a complete read.")
        }

        // The first frame is the calibration frame: the panel is at rest at the top, so its thumb
        // length is the reference every later frame is checked against, and its content height is the
        // list's true height. Both are read once and never re-derived from a moving frame.
        var frame = captureSettledFrame(null)
        val reference = frame.scroll
        val startedAtTop = reference.atTop && inspirationSparksHeaderVisible(sampler(frame.bitmap))
        var rows = readInspirationRows(sampler(frame.bitmap), frame.bitmap.height)
        if (rows.isEmpty()) {
            MessageLog.w(TAG, "[INSPIRATION] The Inspiration panel showed no factor cards; skipping this entry.")
            return finish(
                scanId,
                scanIndex,
                observedAt,
                rosterFingerprint,
                character,
                outfit,
                rank,
                accumulator,
                names,
                InspirationReadTermination.PANEL_NOT_READY,
                frames,
                swipes,
                clippedRejected,
                alignmentFailures,
                unsettledFrames,
                deadReckonedFrames,
                startedAtTop,
                reference,
                reachedBottom = false,
                listEndObserved = false,
                verbose = verbose,
            )
        }

        var termination = if (reference.scrollable) InspirationReadTermination.REACHED_BOTTOM else InspirationReadTermination.NO_SCROLL_NEEDED
        var reachedBottom = !reference.scrollable
        var listEndObserved = !reference.scrollable
        var offset = reference.offset
        // Whether the scrollbar is fine enough to place a row by position at all. On a panel with a
        // long usage history the thumb is barely 57 px, and one of its pixels is worth fourteen of
        // content; there the pixel-key alignment leads instead, since it only needs the offset to be
        // right to within a couple of rows.
        val offsetPrecise = inspirationOffsetPreciseEnough(reference)
        var offsetTrusted = offsetPrecise
        while (true) {
            frames++
            clippedRejected += rows.count { it.clipped }
            val merge = accumulator.offerFrame(offset, rows, offsetTrusted)
            if (merge.gapDetected) alignmentFailures++
            ocrNewRows(frame.bitmap, accumulator.rows, names)
            if (verbose) {
                MessageLog.i(
                    TAG,
                    "[INSPIRATION-TEST] frame=$frames offset=$offset thumb=${frame.scroll.thumbTop}+${frame.scroll.thumbLength} " +
                        "trusted=${frame.trusted} precise=$offsetPrecise rows=${rows.size} added=${merge.added} " +
                        "alignedAt=${merge.alignedAt ?: "NONE"} overlap=${merge.overlap} total=${accumulator.rows.size}",
                )
            }

            if (!reference.scrollable) break
            if (inspirationFactorListEndsInFrame(rows, frame.bitmap.height)) {
                // Empty space below the last card: the factors have ended. Everything below is the
                // inspiration-usage history, which carries no factor data and can run to thousands of
                // pixels, so the traversal stops here rather than paging to the scrollbar's bottom.
                listEndObserved = true
                termination = InspirationReadTermination.REACHED_FACTOR_LIST_END
                break
            }
            if (frame.trusted && frame.scroll.atBottom) {
                reachedBottom = true
                listEndObserved = true
                break
            }
            if (merge.added == 0) {
                stalls++
                if (stalls >= STALL_LIMIT) {
                    // Two swipes that exposed nothing new. Either the list really ended and the
                    // scrollbar could not confirm it, or the panel stopped responding; the content
                    // height cross-check below decides which, rather than this loop guessing.
                    termination = InspirationReadTermination.STALLED
                    break
                }
            } else {
                stalls = 0
            }
            if (swipes >= MAX_SWIPES) {
                termination = InspirationReadTermination.SCROLL_BUDGET_EXHAUSTED
                break
            }

            val swipeRows = if (offsetPrecise) SWIPE_ROWS else SWIPE_ROWS_COARSE
            swipe(down = false, rows = swipeRows)
            swipes++
            frame = captureSettledFrame(reference.thumbLength)
            if (!frame.trusted) unsettledFrames++
            // A trusted thumb gives the exact offset. An untrusted one is discarded in favour of dead
            // reckoning from the swipe distance we chose ourselves: wrong by at most the scroller's
            // overshoot, which the merge's alignment search absorbs, instead of wrong by a factor of six.
            offsetTrusted = frame.trusted && offsetPrecise
            offset =
                if (frame.trusted) {
                    // Measured against the CALIBRATION frame's geometry, not this frame's own: only
                    // the thumb's position varies between frames, and deriving the scale afresh each
                    // time lets a pixel of noise move where every row is thought to sit.
                    inspirationOffsetAgainst(reference, frame.scroll.thumbTop ?: INSPIRATION_TRACK_TOP)
                } else {
                    deadReckonedFrames++
                    offset + (swipeRows * INSPIRATION_ROW_PITCH).toInt()
                }
            rows = readInspirationRows(sampler(frame.bitmap), frame.bitmap.height)
        }
        if (!startedAtTop && termination == InspirationReadTermination.REACHED_BOTTOM) {
            termination = InspirationReadTermination.NOT_AT_TOP
        }

        val observation =
            finish(
                scanId,
                scanIndex,
                observedAt,
                rosterFingerprint,
                character,
                outfit,
                rank,
                accumulator,
                names,
                termination,
                frames,
                swipes,
                clippedRejected,
                alignmentFailures,
                unsettledFrames,
                deadReckonedFrames,
                startedAtTop,
                reference,
                reachedBottom,
                listEndObserved,
                verbose,
            )
        // Leave the panel where the next entry expects it. Best-effort: the next capture re-asserts
        // the top itself, so a failure here costs a few extra swipes and never a wrong read.
        scrollPanelToTop()
        return observation
    }

    /** Builds one factor, snapping its OCR name onto the canonical factor domain conditioned on the
     * pixel-classified kind. When the domain did not load, or the name is empty/off-domain, the factor
     * is left canonically unresolved and its semantic fingerprint stays blocked fail-closed. */
    private fun buildFactor(rowIndex: Int, column: InspirationColumn, kind: SparkRowKind, name: String, stars: Int, ambiguous: Boolean): InspirationFactor {
        val res = factorDomain?.resolve(name, kind)
        return InspirationFactor(
            rowIndex = rowIndex,
            column = column,
            kind = kind,
            displayName = name,
            stars = stars,
            ambiguous = ambiguous,
            canonicalName = res?.canonicalName,
            canonicalPath = res?.path ?: com.steve1316.uma_android_automation.utils.FactorAcceptancePath.REJECT,
            canonicalScore = res?.bestScore ?: 0.0,
            canonicalSecondScore = res?.secondScore,
        )
    }

    /** OCRs the names of rows appended since the last call, from the frame that contributed them. */
    private fun ocrNewRows(bitmap: Bitmap, rows: List<InspirationAbsoluteRow>, names: MutableList<RowRead>) {
        for (i in names.size until rows.size) {
            val row = rows[i].row
            val frame = sampler(bitmap)
            val left = ocrName(bitmap, inspirationLeftNameRegions(frame, row.left.kind, row.runTopY), row.left.kind, "inspiration_factor_l")
            val right = row.right?.let { ocrName(bitmap, inspirationRightNameRegions(frame, it.kind, row.runTopY), it.kind, "inspiration_factor_r") }
            val density = inspirationRailPortraitDensity(sampler(bitmap), (row.runTopY + row.runBottomY) / 2, bitmap.height)
            names.add(RowRead(left, right, density >= INSPIRATION_PORTRAIT_MIN_DENSITY))
        }
    }

    private fun finish(
        scanId: String,
        scanIndex: Int,
        observedAt: Long,
        rosterFingerprint: String?,
        character: String?,
        outfit: String?,
        rank: String?,
        accumulator: InspirationRowAccumulator,
        names: List<RowRead>,
        termination: InspirationReadTermination,
        frames: Int,
        swipes: Int,
        clippedRejected: Int,
        alignmentFailures: Int,
        unsettledFrames: Int,
        deadReckonedFrames: Int,
        startedAtTop: Boolean,
        /** The calibration frame's scroll state: the panel at rest at the top of its content. */
        reference: InspirationScrollRead?,
        reachedBottom: Boolean,
        listEndObserved: Boolean,
        verbose: Boolean,
    ): VeteranInspirationObservation {
        val rows = accumulator.rows
        val segments = segmentInspirationBlocks(rows)
        val leadingPartial = segments.firstOrNull { it.first < 0 }?.second?.size ?: 0
        val blocks =
            segments.filter { it.first >= 0 }.map { (blockIndex, blockRows) ->
                val factors = mutableListOf<InspirationFactor>()
                blockRows.forEachIndexed { rowIndex, absolute ->
                    val globalIndex = rows.indexOf(absolute)
                    val rowNames = names.getOrNull(globalIndex)
                    val cell = absolute.row
                    factors.add(buildFactor(rowIndex, InspirationColumn.LEFT, cell.left.kind, rowNames?.left ?: "", cell.left.filledStars, cell.left.ambiguousStars > 0))
                    cell.right?.let { right ->
                        factors.add(buildFactor(rowIndex, InspirationColumn.RIGHT, right.kind, rowNames?.right ?: "", right.filledStars, right.ambiguousStars > 0))
                    }
                }
                val portrait = names.getOrNull(rows.indexOf(blockRows.first()))?.portrait ?: false
                InspirationBlockObservation(blockIndex, portraitObserved = portrait, factors = factors)
            }

        val diagnostics =
            InspirationDiagnostics(
                frames = frames,
                swipes = swipes,
                startedAtTop = startedAtTop,
                reachedBottom = reachedBottom,
                factorListEndObserved = listEndObserved,
                gapFrames = accumulator.gapCount,
                spacingBreaks = accumulator.spacingBreaks().size,
                alignmentFailures = alignmentFailures,
                unsettledFrames = unsettledFrames,
                deadReckonedFrames = deadReckonedFrames,
                scrollbarContentHeight = reference?.contentHeight,
                observedContentHeight = accumulator.observedContentHeight(),
                rowsAccepted = rows.size,
                clippedRowsRejected = clippedRejected,
                leadingPartialBlockRows = leadingPartial,
                blocksObserved = blocks.size,
            )

        val observation =
            assembleVeteranInspiration(
                scanId = scanId,
                scanIndex = scanIndex,
                observedAt = observedAt,
                rosterFingerprint = rosterFingerprint,
                character = character,
                outfit = outfit,
                rank = rank,
                blocks = blocks,
                termination = termination,
                diagnostics = diagnostics,
            )
        if (verbose) logObservation(observation)
        return observation
    }

    /** One factor rendered for the diagnostic log: kind, raw OCR, the canonical it snapped to (with
     * acceptance path), and its stars. Lets a repeated capture be compared for semantic determinism. */
    private fun factorLine(f: InspirationFactor): String =
        "${f.kind.name.lowercase()} '${f.displayName}'->'${f.canonicalName ?: "?"}'[${f.canonicalPath.name.lowercase()}] ${f.stars}*"

    private fun logObservation(o: VeteranInspirationObservation) {
        MessageLog.i(
            TAG,
            "[INSPIRATION-TEST] ${o.character ?: "?"} [${o.outfit ?: "?"}] rank=${o.rank ?: "?"} fp=${o.rosterFingerprint ?: "UNRESOLVED"}",
        )
        MessageLog.i(
            TAG,
            "[INSPIRATION-TEST] self ${o.selfFactors.size} factor(s) trusted=${o.selfFactorSetTrusted} fp=${o.selfFactorFingerprint ?: "UNTRUSTED"}: " +
                o.selfFactors.joinToString(", ") { factorLine(it) },
        )
        for (ancestor in o.legacyAncestors) {
            MessageLog.i(
                TAG,
                "[INSPIRATION-TEST] legacy ancestor ${ancestor.ancestorIndex} portrait=${ancestor.portraitObserved} " +
                    "trusted=${ancestor.factorSetTrusted} fp=${ancestor.factorFingerprint ?: "UNTRUSTED"} " +
                    "${ancestor.factors.size} factor(s): " +
                    ancestor.factors.joinToString(", ") { factorLine(it) },
            )
        }
        val d = o.diagnostics
        MessageLog.i(
            TAG,
            "[INSPIRATION-TEST] termination=${o.termination} complete=${o.sparkCaptureComplete} " +
                "readCompleteness=${"%.2f".format(o.screenReadCompleteness)} frames=${d.frames} swipes=${d.swipes} " +
                "rows=${d.rowsAccepted} blocks=${d.blocksObserved} gaps=${d.gapFrames} spacingBreaks=${d.spacingBreaks} " +
                "unsettled=${d.unsettledFrames} deadReckoned=${d.deadReckonedFrames} " +
                "contentH=${d.scrollbarContentHeight ?: "?"}/${d.observedContentHeight ?: "?"} " +
                "unresolved=${o.unresolvedFields.joinToString(",").ifEmpty { "none" }}",
        )
    }

    /**
     * PL-R1c's single-Veteran calibration diagnostic. The operator parks the game on an open
     * `Umamusume Details` dialog; this selects the Inspiration tab if needed, walks the panel, logs
     * every factor it read tagged `[INSPIRATION-TEST]`, and stops without persisting anything.
     */
    fun debugRead() {
        val catalog = VeteranIdentityCatalog.loadFromAssets(game.myContext)
        val rosterReader = VeteranRosterReader(iu, catalog)
        val (bitmap, screen) = rosterReader.classifyScreenWithRetries()
        MessageLog.i(TAG, "[INSPIRATION-TEST] ===== Veteran Inspiration read-only diagnostic (${bitmap.width}x${bitmap.height}) =====")
        if (screen.kind != RosterScreenKind.UMAMUSUME_DETAILS) {
            MessageLog.w(
                TAG,
                "[INSPIRATION-TEST] screenKind=${screen.kind} - park the game on an open Umamusume Details dialog " +
                    "(Home -> Enhance -> Veteran Umamusume -> a roster card) and re-run. (title OCR='${screen.titleRaw}')",
            )
            return
        }
        val identity = rosterReader.readDetailObservation(bitmap, includeCareerInfo = false, verbose = false)
        capture(
            scanId = "insp-debug",
            scanIndex = 0,
            rosterFingerprint = entryFingerprint(identity),
            character = identity.character,
            outfit = identity.outfit,
            rank = identity.rank,
            verbose = true,
        )
        MessageLog.i(TAG, "[INSPIRATION-TEST] ===== end =====")
    }
}
