package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import com.steve1316.uma_android_automation.utils.GrandConcertGainDigits
import com.steve1316.uma_android_automation.utils.GrandConcertTrainingGeometry
import com.steve1316.uma_android_automation.utils.ScreenBand
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.grandConcertPerformancePanelPresent
import com.steve1316.uma_android_automation.utils.onScreen
import com.steve1316.uma_android_automation.utils.selectedTrainingPerformanceRows

/** Reads the Grand Concert training screen's Performance Points panel (five balances and the selected facility's "+N" gains) off an analysis frame; never taps. */
class GrandConcertTrainingReader(private val game: Game) {
    /** Null components where OCR failed; a gain with a null amount had a readable glyph but not a readable number. */
    data class FacilityPanelRead(
        val balances: Map<PerformancePointType, Int?>,
        val gains: Map<PerformancePointType, Int?>,
    )

    /** Null when the panel is not structurally present (event overlay, dialog, other screen): callers treat it as no data, not zeros. */
    fun readFacilityPanel(sourceBitmap: Bitmap): FacilityPanelRead? {
        val sampler = SparkPixelSampler { x, y -> sourceBitmap.getPixel(x, y) }
        if (!grandConcertPerformancePanelPresent(sampler, sourceBitmap.width, sourceBitmap.height)) return null

        val balances = LinkedHashMap<PerformancePointType, Int?>()
        for (i in 0..4) {
            val raw = ocrNumber(sourceBitmap, GrandConcertTrainingGeometry.perfBalanceOcrRegion(i), "gc_train_balance_$i")
            balances[GrandConcertTrainingGeometry.PERF_ROW_TYPES[i]] = raw?.takeIf { it in 0..999 }
        }

        val gains = LinkedHashMap<PerformancePointType, Int?>()
        for (row in selectedTrainingPerformanceRows(sampler, sourceBitmap.width, sourceBitmap.height)) {
            // The pixel digit reader handles the stylised warm "+N" glyph far better than OCR, which is the fallback.
            // Gains run 7..30, so the OCR sanity band stays just above that (a live OCR read "+99" from glyph noise).
            val amount =
                GrandConcertGainDigits.readGainAmount(sampler, row, sourceBitmap.width, sourceBitmap.height)
                    ?: ocrNumber(sourceBitmap, GrandConcertTrainingGeometry.perfGainAmountOcrRegion(row), "gc_train_gain_$row")?.takeIf { it in 1..40 }
            gains[GrandConcertTrainingGeometry.PERF_ROW_TYPES[row]] = amount
        }
        return FacilityPanelRead(balances = balances, gains = gains)
    }

    /** Retries at the lower threshold for digits the default 230 cutoff blacks out. */
    private fun ocrNumber(bmp: Bitmap, region: IntArray, debugName: String): Int? {
        parseNumber(ocr(bmp, region, debugName))?.let { return it }
        return parseNumber(ocr(bmp, region, "${debugName}_lowthresh", thresholdIncrement = GREY_FIELD_THRESHOLD_DELTA))
    }

    private fun ocr(bmp: Bitmap, region1920: IntArray, debugName: String, thresholdIncrement: Double = 0.0): String {
        val region = region1920.onScreen(ScreenBand.TOP, bmp.width, bmp.height)
        return game.imageUtils
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
    }

    /** Digits only; the balance region excludes the cap line because "13 /300" would read garbage. */
    private fun parseNumber(text: String): Int? {
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return digits.toIntOrNull()
    }

    companion object {
        /** 230 - 100 = 130: under grey or mid-tone fills, above dark digit strokes (same as GrandConcertLessonReader). */
        private const val GREY_FIELD_THRESHOLD_DELTA = -100.0
    }
}
