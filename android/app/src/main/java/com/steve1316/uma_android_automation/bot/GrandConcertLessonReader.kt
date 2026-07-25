package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.utils.GrandConcertLessonGeometry
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.grandConcertDialogHeaderPresent
import com.steve1316.uma_android_automation.utils.grandConcertLessonCardKind
import com.steve1316.uma_android_automation.utils.grandConcertLessonListPresent
import com.steve1316.uma_android_automation.utils.grandConcertScheduleShortfallPresent

/**
 * Reads the live Grand Concert Lesson list into the pure [LessonList] model.
 *
 * This is the runtime half of the lesson layer. GrandConcertProbes supplies the Android-free pixel
 * geometry, [GrandConcertLessons] supplies the total data types, and this class is the only place
 * that binds them to a live [Bitmap] and the Tesseract OCR path. It never taps: the caller owns
 * navigation, so a read can never spend a performance point or dismiss a card.
 *
 * Scope (first, read-only pass): reads the five balances plus each card's title, kind, affordability,
 * Mastery text, and Concert text. The exact per-type cost cells are deliberately not read yet - the
 * read-only prowl that consumes this only needs the offered set and per-card affordability, and exact
 * costs land with the spend decider that needs them. Cards therefore carry an unread cost vector.
 */
class GrandConcertLessonReader(private val game: Game) {
    /**
     * Reads the current Lesson list, or null when the list is not actually on screen (the caller must
     * have navigated to it first). Never throws on an unreadable field: every OCR miss becomes a null
     * in the model, which the pure layer already treats as "do not act".
     */
    fun readLessonList(sourceBitmap: Bitmap): LessonList? {
        val sampler = SparkPixelSampler { x, y -> sourceBitmap.getPixel(x, y) }
        if (!grandConcertLessonListPresent(sampler)) return null

        val balances = readBalances(sourceBitmap)
        val cards = (0..2).map { readCard(sourceBitmap, sampler, it, balances) }
        // Both bottom buttons are structurally always present on the list (see fixtures song_list.png
        // / technique_list.png); a dedicated probe for them is not needed for a read-only pass.
        return LessonList(balances = balances, cards = cards, hasFullStats = true, hasConcertInfo = true)
    }

    /**
     * Reads the live learn/schedule confirmation dialog into a [LessonConfirmation], or null when no
     * such dialog is on screen. [LessonConfirmation.isSchedule] comes from the red shortfall band that
     * only the unaffordable dialog shows, so a Schedule dialog can never be mistaken for a Learn one;
     * the title and kind are OCR'd for the caller's `verifyAgainst` gate. Reads only - the caller
     * decides whether to tap Learn. `pointsLeftOver` is left all-null: the transactional Learn verifies
     * the spend by re-reading the list balances after the tap rather than trusting this preview.
     */
    fun readConfirmation(sourceBitmap: Bitmap): LessonConfirmation? {
        val sampler = SparkPixelSampler { x, y -> sourceBitmap.getPixel(x, y) }
        if (!grandConcertDialogHeaderPresent(sampler)) return null
        val title = ocr(sourceBitmap, GrandConcertLessonGeometry.CONFIRM_TITLE_OCR_REGION, "gc_confirm_title").ifBlank { null }
        val kindText = ocr(sourceBitmap, GrandConcertLessonGeometry.CONFIRM_KIND_PILL_OCR_REGION, "gc_confirm_kind").lowercase()
        val kind =
            when {
                kindText.contains("song") -> LessonCardKind.SONG
                kindText.contains("techni") -> LessonCardKind.TECHNIQUE
                else -> LessonCardKind.UNKNOWN
            }
        return LessonConfirmation(
            isSchedule = grandConcertScheduleShortfallPresent(sampler),
            title = title,
            kind = kind,
            masteryText = null,
            concertText = null,
            pointsLeftOver = PerformancePointVector.of(null, null, null, null, null),
            hasCancel = true,
            hasAffirmative = true,
        )
    }

    /** The five performance-point balance badges across the top of the list, in badge order
     * (Da, Pa, Vo, Vi, Co), which is exactly [PerformancePointType] declaration order. Upscaled 2x:
     * these digits are low-contrast purple-on-white, and the 2x scale is what stops "20" misreading
     * as "52" the way it did on the first live prowl. */
    private fun readBalances(bmp: Bitmap): PerformancePointVector {
        val values = LinkedHashMap<PerformancePointType, Int?>()
        val types = PerformancePointType.entries
        for (i in 0..4) {
            val region = GrandConcertLessonGeometry.balanceOcrRegion(i)
            values[types[i]] = ocrPoint(bmp, region, "gc_balance_${types[i].displayName}")
        }
        return PerformancePointVector(values)
    }

    /** Card [card]'s five per-type cost cells (Da, Pa, Vo, Vi, Co) into a cost vector. Upscaled 2x to
     * survive both the small digits and the low-contrast greyed digits on an unaffordable card. A cell
     * that reads "0" is a real zero cost; only an unreadable cell becomes null. */
    private fun readCost(bmp: Bitmap, card: Int): PerformancePointVector {
        val values = LinkedHashMap<PerformancePointType, Int?>()
        val types = PerformancePointType.entries
        for (t in 0..4) {
            val region = GrandConcertLessonGeometry.cardCostCellOcrRegion(card, t)
            values[types[t]] = ocrPoint(bmp, region, "gc_cost_${card}_${types[t].displayName}")
        }
        return PerformancePointVector(values)
    }

    /** One card: kind from the bar colour, title/mastery/concert from OCR, cost from the five cells, and
     * affordability computed arithmetically (cost vs [balances]) rather than from the noisy strip probe.
     * [LessonListCard.learnable] stays three-valued: true, false, or null when a cost or balance is unread. */
    private fun readCard(bmp: Bitmap, sampler: SparkPixelSampler, index: Int, balances: PerformancePointVector): LessonListCard {
        val kind = grandConcertLessonCardKind(sampler, index)
        val title = ocr(bmp, GrandConcertLessonGeometry.cardTitleOcrRegion(index), "gc_card_title_$index").ifBlank { null }
        val mastery = ocr(bmp, GrandConcertLessonGeometry.cardMasteryOcrRegion(index), "gc_card_mastery_$index").ifBlank { null }
        val concert = ocr(bmp, GrandConcertLessonGeometry.cardConcertOcrRegion(index), "gc_card_concert_$index").ifBlank { null }
        val cost = readCost(bmp, index)
        return LessonListCard(
            slot = index,
            title = title,
            kind = kind,
            masteryText = mastery,
            concertText = concert,
            cost = cost,
            learnable = cost.affordableWith(balances),
            scheduled = null,
        )
    }

    /** OCRs a region off [bmp] at [scale]; empty string on any miss (the bounds guard and Tesseract both
     * fail to empty rather than throwing). */
    private fun ocr(bmp: Bitmap, region: IntArray, debugName: String, scale: Double = 1.0, thresholdIncrement: Double = 0.0): String =
        game.imageUtils
            .performOCROnRegion(
                bmp,
                region[0],
                region[1],
                region[2],
                region[3],
                scale = scale,
                debugName = debugName,
                thresholdIncrement = thresholdIncrement,
            ).trim()

    /**
     * Reads one performance-point number, retrying under a lower binarisation threshold when the
     * default one yields nothing.
     *
     * The shared OCR threshold is 230, which suits digits on a white field. An unaffordable card
     * greys its whole cost strip down to luminance ~150, so every pixel of the cell falls below the
     * cutoff and the crop binarises to solid black: the digits do not survive preprocessing at all.
     * Measured 2026-07-25 from the reader's own debug dumps, where the saved threshold image was
     * uniformly black while the matching crop held a crisp "14"; a sweep put the usable cutoff at
     * 130 or below. The retry costs one extra OCR only on cells the default already lost, and it is
     * what lets the caller tell "too expensive" apart from "could not read".
     */
    private fun ocrPoint(bmp: Bitmap, region: IntArray, debugName: String): Int? {
        parsePoint(ocr(bmp, region, debugName, scale = 2.0))?.let { return it }
        return parsePoint(ocr(bmp, region, "${debugName}_lowthresh", scale = 2.0, thresholdIncrement = GREY_FIELD_THRESHOLD_DELTA))
    }

    /** Parses a performance-point number from OCR text: keep digits and a leading minus, drop the
     * rest ("+22", "Da 14", "-5"). Null when nothing numeric remains, which the model reads as
     * "unread" rather than zero. */
    private fun parsePoint(text: String): Int? {
        val negative = text.trimStart().startsWith("-")
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val n = digits.toIntOrNull() ?: return null
        return if (negative) -n else n
    }

    /** Emits grep-friendly telemetry: one balances line, then one line per offered card. */
    fun logLessonList(list: LessonList) {
        val b = list.balances
        MessageLog.i(
            TAG,
            "[GRAND_CONCERT] [LESSON_READ] balances " +
                "Da=${b[PerformancePointType.DANCE]} Pa=${b[PerformancePointType.PASSION]} " +
                "Vo=${b[PerformancePointType.VOCAL]} Vi=${b[PerformancePointType.VISUAL]} " +
                "Co=${b[PerformancePointType.COMPOSURE]}",
        )
        for (card in list.cards) {
            val c = card.cost
            MessageLog.i(
                TAG,
                "[GRAND_CONCERT] [LESSON_READ] card${card.slot} kind=${card.kind} learnable=${card.learnable} " +
                    "cost=Da${c[PerformancePointType.DANCE]}/Pa${c[PerformancePointType.PASSION]}/" +
                    "Vo${c[PerformancePointType.VOCAL]}/Vi${c[PerformancePointType.VISUAL]}/Co${c[PerformancePointType.COMPOSURE]} " +
                    "title=\"${card.title}\" mastery=\"${card.masteryText}\" concert=\"${card.concertText}\"",
            )
        }
    }

    companion object {
        private const val TAG = "GrandConcertLessonReader"

        /** Threshold offset for the retry in [ocrPoint]: 230 - 100 = 130, measured to sit under the
         * grey cost strip (luminance ~150) and above the digits (~40). */
        private const val GREY_FIELD_THRESHOLD_DELTA = -100.0
    }
}
