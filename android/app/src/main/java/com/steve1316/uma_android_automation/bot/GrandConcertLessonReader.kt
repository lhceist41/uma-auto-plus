package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.utils.GrandConcertLessonGeometry
import com.steve1316.uma_android_automation.utils.ScreenBand
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.grandConcertDialogHeaderPresent
import com.steve1316.uma_android_automation.utils.grandConcertLessonCardKind
import com.steve1316.uma_android_automation.utils.grandConcertLessonListPresent
import com.steve1316.uma_android_automation.utils.grandConcertScheduleShortfallPresent
import com.steve1316.uma_android_automation.utils.onScreen

/** Reads the live Grand Concert Lesson list into the pure [LessonList] model. Never taps, so a read can never spend a performance point. */
class GrandConcertLessonReader(private val game: Game) {
    fun readLessonList(sourceBitmap: Bitmap): LessonList? {
        val sampler = SparkPixelSampler { x, y -> sourceBitmap.getPixel(x, y) }
        if (!grandConcertLessonListPresent(sampler, sourceBitmap.width, sourceBitmap.height)) return null

        val balances = readBalances(sourceBitmap)
        val cards = (0..2).map { readCard(sourceBitmap, sampler, it, balances) }
        return LessonList(balances = balances, cards = cards, hasFullStats = true, hasConcertInfo = true)
    }

    /** Reads the learn/schedule confirmation dialog, or null when none is on screen. `isSchedule` comes from the red shortfall band only the unaffordable dialog shows. `pointsLeftOver` stays null: the Learn transaction re-reads the list balances instead. */
    fun readConfirmation(sourceBitmap: Bitmap): LessonConfirmation? {
        val sampler = SparkPixelSampler { x, y -> sourceBitmap.getPixel(x, y) }
        if (!grandConcertDialogHeaderPresent(sampler, sourceBitmap.width, sourceBitmap.height)) return null
        val title = ocr(sourceBitmap, GrandConcertLessonGeometry.CONFIRM_TITLE_OCR_REGION, ScreenBand.DIALOG, "gc_confirm_title").ifBlank { null }
        val kindText = ocr(sourceBitmap, GrandConcertLessonGeometry.CONFIRM_KIND_PILL_OCR_REGION, ScreenBand.DIALOG, "gc_confirm_kind").lowercase()
        val kind =
            when {
                kindText.contains("song") -> LessonCardKind.SONG
                kindText.contains("techni") -> LessonCardKind.TECHNIQUE
                else -> LessonCardKind.UNKNOWN
            }
        return LessonConfirmation(
            isSchedule = grandConcertScheduleShortfallPresent(sampler, sourceBitmap.width, sourceBitmap.height),
            title = title,
            kind = kind,
            masteryText = null,
            concertText = null,
            pointsLeftOver = PerformancePointVector.of(null, null, null, null, null),
            hasCancel = true,
            hasAffirmative = true,
        )
    }

    /** The five balance badges (Da, Pa, Vo, Vi, Co). Upscaled 2x: low-contrast digits otherwise misread ("20" as "52"). */
    private fun readBalances(bmp: Bitmap): PerformancePointVector {
        val values = LinkedHashMap<PerformancePointType, Int?>()
        val types = PerformancePointType.entries
        for (i in 0..4) {
            val region = GrandConcertLessonGeometry.balanceOcrRegion(i)
            values[types[i]] = ocrPoint(bmp, region, "gc_balance_${types[i].displayName}")
        }
        return PerformancePointVector(values)
    }

    /** Five per-type cost cells. Upscaled 2x for the small, greyed digits; a cell reading "0" is a real zero, only an unreadable one is null. */
    private fun readCost(bmp: Bitmap, card: Int): PerformancePointVector {
        val values = LinkedHashMap<PerformancePointType, Int?>()
        val types = PerformancePointType.entries
        for (t in 0..4) {
            val region = GrandConcertLessonGeometry.cardCostCellOcrRegion(card, t)
            values[types[t]] = ocrPoint(bmp, region, "gc_cost_${card}_${types[t].displayName}")
        }
        return PerformancePointVector(values)
    }

    /** Affordability is computed from cost vs [balances] rather than the noisy strip probe; null when a cost or balance is unread. */
    private fun readCard(bmp: Bitmap, sampler: SparkPixelSampler, index: Int, balances: PerformancePointVector): LessonListCard {
        val kind = grandConcertLessonCardKind(sampler, index, bmp.width, bmp.height)
        val title = ocr(bmp, GrandConcertLessonGeometry.cardTitleOcrRegion(index), ScreenBand.MIDDLE, "gc_card_title_$index").ifBlank { null }
        val mastery = ocr(bmp, GrandConcertLessonGeometry.cardMasteryOcrRegion(index), ScreenBand.MIDDLE, "gc_card_mastery_$index").ifBlank { null }
        val concert = ocr(bmp, GrandConcertLessonGeometry.cardConcertOcrRegion(index), ScreenBand.MIDDLE, "gc_card_concert_$index").ifBlank { null }
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

    private fun ocr(bmp: Bitmap, region1920: IntArray, band: ScreenBand, debugName: String, scale: Double = 1.0, thresholdIncrement: Double = 0.0): String {
        val region = region1920.onScreen(band, bmp.width, bmp.height)
        return game.imageUtils
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
    }

    /**
     * An unaffordable card greys its cost strip to luminance ~150, below the shared 230 threshold, so the crop
     * binarises to solid black. Retry at a lower threshold (130 or below works) only when the default read nothing.
     */
    private fun ocrPoint(bmp: Bitmap, region: IntArray, debugName: String): Int? {
        parsePoint(ocr(bmp, region, ScreenBand.MIDDLE, debugName, scale = 2.0))?.let { return it }
        return parsePoint(ocr(bmp, region, ScreenBand.MIDDLE, "${debugName}_lowthresh", scale = 2.0, thresholdIncrement = GREY_FIELD_THRESHOLD_DELTA))
    }

    private fun parsePoint(text: String): Int? {
        val negative = text.trimStart().startsWith("-")
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val n = digits.toIntOrNull() ?: return null
        return if (negative) -n else n
    }

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

        /** 230 - 100 = 130: under the grey cost strip (luminance ~150), above the digits (~40). */
        private const val GREY_FIELD_THRESHOLD_DELTA = -100.0
    }
}
