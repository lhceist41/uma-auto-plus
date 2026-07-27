package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import com.steve1316.uma_android_automation.utils.GrandConcertTrainingGeometry
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.grandConcertPerformancePanelPresent
import com.steve1316.uma_android_automation.utils.selectedTrainingPerformanceRows

/**
 * Reads the Grand Concert training screen's Performance Points panel off a facility's analysis
 * frame: the five balances, and the "+N" gain annotation(s) of the currently selected facility.
 *
 * This is the runtime half of the panel layer, same split as [GrandConcertLessonReader]: the
 * probes in GrandConcertProbes supply the Android-free structure (panel presence, which rows
 * carry a gain glyph), and this class is the only place that binds them to a live [Bitmap] and
 * the OCR path. It never taps. Because the training analysis loop selects every facility in
 * turn, calling [readFacilityPanel] on each facility's own analysis frame yields the full
 * per-facility (type, amount) income preview for the turn with no extra navigation.
 *
 * Amount reads are best-effort: a row whose glyph is detected but whose "+N" resists OCR keeps
 * its TYPE with a null amount (the scorer substitutes a conservative default), because the type
 * is the dominant signal and the glyph's warm-gradient fill over arbitrary background art makes
 * the number the fragile part.
 */
class GrandConcertTrainingReader(private val game: Game) {
    /** One facility's panel read: the five balances (null components where OCR failed) and the
     * selected facility's per-type gains (null amount where only the glyph, not the number, was
     * readable). */
    data class FacilityPanelRead(
        val balances: Map<PerformancePointType, Int?>,
        val gains: Map<PerformancePointType, Int?>,
    )

    /** Reads the panel off [sourceBitmap], or null when the panel is not structurally present
     * (event overlay, dialog, non-training screen), which callers must treat as "no data" rather
     * than zeros. */
    fun readFacilityPanel(sourceBitmap: Bitmap): FacilityPanelRead? {
        val sampler = SparkPixelSampler { x, y -> sourceBitmap.getPixel(x, y) }
        if (!grandConcertPerformancePanelPresent(sampler)) return null

        val balances = LinkedHashMap<PerformancePointType, Int?>()
        for (i in 0..4) {
            val raw = ocrNumber(sourceBitmap, GrandConcertTrainingGeometry.perfBalanceOcrRegion(i), "gc_train_balance_$i")
            balances[GrandConcertTrainingGeometry.PERF_ROW_TYPES[i]] = raw?.takeIf { it in 0..999 }
        }

        val gains = LinkedHashMap<PerformancePointType, Int?>()
        for (row in selectedTrainingPerformanceRows(sampler)) {
            val amount = ocrNumber(sourceBitmap, GrandConcertTrainingGeometry.perfGainAmountOcrRegion(row), "gc_train_gain_$row")
            // Observed per-training gains run 7..30; the first live run OCR'd a "+99" out of glyph
            // noise, so the sanity band stays just above the plausible ceiling, not at two digits.
            gains[GrandConcertTrainingGeometry.PERF_ROW_TYPES[row]] = amount?.takeIf { it in 1..40 }
        }
        return FacilityPanelRead(balances = balances, gains = gains)
    }

    /** OCRs one number, retrying under the lower binarisation threshold the lesson reader
     * established for digits that the default 230 cutoff blacks out entirely. */
    private fun ocrNumber(bmp: Bitmap, region: IntArray, debugName: String): Int? {
        parseNumber(ocr(bmp, region, debugName))?.let { return it }
        return parseNumber(ocr(bmp, region, "${debugName}_lowthresh", thresholdIncrement = GREY_FIELD_THRESHOLD_DELTA))
    }

    private fun ocr(bmp: Bitmap, region: IntArray, debugName: String, thresholdIncrement: Double = 0.0): String =
        game.imageUtils
            .performOCROnRegion(
                bmp,
                region[0],
                region[1],
                region[2],
                region[3],
                scale = 2.0,
                debugName = debugName,
                thresholdIncrement = thresholdIncrement,
            ).trim()

    /** Digits only ("+23" reads 23, "13 /300" would read garbage so the balance region
     * deliberately excludes the cap line). Null when nothing numeric remains. */
    private fun parseNumber(text: String): Int? {
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return digits.toIntOrNull()
    }

    companion object {
        /** Same measured offset as GrandConcertLessonReader: 230 - 100 = 130 sits under grey or
         * mid-tone fills and above dark digit strokes. */
        private const val GREY_FIELD_THRESHOLD_DELTA = -100.0
    }
}
