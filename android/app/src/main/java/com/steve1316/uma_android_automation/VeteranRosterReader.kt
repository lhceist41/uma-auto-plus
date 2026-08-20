package com.steve1316.uma_android_automation

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.utils.APTITUDE_DISTANCE_CELL_H
import com.steve1316.uma_android_automation.utils.APTITUDE_DISTANCE_CELL_W
import com.steve1316.uma_android_automation.utils.APTITUDE_DISTANCE_COL_X
import com.steve1316.uma_android_automation.utils.APTITUDE_DISTANCE_LABELS
import com.steve1316.uma_android_automation.utils.APTITUDE_DISTANCE_Y
import com.steve1316.uma_android_automation.utils.APTITUDE_STYLE_CELL_H
import com.steve1316.uma_android_automation.utils.APTITUDE_STYLE_CELL_W
import com.steve1316.uma_android_automation.utils.APTITUDE_STYLE_COL_X
import com.steve1316.uma_android_automation.utils.APTITUDE_STYLE_LABELS
import com.steve1316.uma_android_automation.utils.APTITUDE_STYLE_Y
import com.steve1316.uma_android_automation.utils.APTITUDE_TRACK_CELL_H
import com.steve1316.uma_android_automation.utils.APTITUDE_TRACK_CELL_W
import com.steve1316.uma_android_automation.utils.APTITUDE_TRACK_COL_X
import com.steve1316.uma_android_automation.utils.APTITUDE_TRACK_LABELS
import com.steve1316.uma_android_automation.utils.APTITUDE_TRACK_Y
import com.steve1316.uma_android_automation.utils.CAREER_DATE_ACQUIRED_H
import com.steve1316.uma_android_automation.utils.CAREER_DATE_ACQUIRED_W
import com.steve1316.uma_android_automation.utils.CAREER_DATE_ACQUIRED_X
import com.steve1316.uma_android_automation.utils.CAREER_DATE_ACQUIRED_Y
import com.steve1316.uma_android_automation.utils.CAREER_FANS_H
import com.steve1316.uma_android_automation.utils.CAREER_FANS_W
import com.steve1316.uma_android_automation.utils.CAREER_FANS_X
import com.steve1316.uma_android_automation.utils.CAREER_FANS_Y
import com.steve1316.uma_android_automation.utils.CAREER_RATING_H
import com.steve1316.uma_android_automation.utils.CAREER_RATING_W
import com.steve1316.uma_android_automation.utils.CAREER_RATING_X
import com.steve1316.uma_android_automation.utils.CAREER_RATING_Y
import com.steve1316.uma_android_automation.utils.CAREER_RECORD_H
import com.steve1316.uma_android_automation.utils.CAREER_RECORD_W
import com.steve1316.uma_android_automation.utils.CAREER_RECORD_X
import com.steve1316.uma_android_automation.utils.CAREER_RECORD_Y
import com.steve1316.uma_android_automation.utils.CAREER_SCENARIO_H
import com.steve1316.uma_android_automation.utils.CAREER_SCENARIO_W
import com.steve1316.uma_android_automation.utils.CAREER_SCENARIO_X
import com.steve1316.uma_android_automation.utils.CAREER_SCENARIO_Y
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.uma_android_automation.utils.DETAIL_FAVORITE_CX
import com.steve1316.uma_android_automation.utils.DETAIL_FAVORITE_CY
import com.steve1316.uma_android_automation.utils.DETAIL_NAME_OUTFIT_H
import com.steve1316.uma_android_automation.utils.DETAIL_NAME_OUTFIT_W
import com.steve1316.uma_android_automation.utils.DETAIL_NAME_OUTFIT_X
import com.steve1316.uma_android_automation.utils.DETAIL_NAME_OUTFIT_Y
import com.steve1316.uma_android_automation.utils.DETAIL_RANK_H
import com.steve1316.uma_android_automation.utils.DETAIL_RANK_W
import com.steve1316.uma_android_automation.utils.DETAIL_RANK_X
import com.steve1316.uma_android_automation.utils.DETAIL_RANK_Y
import com.steve1316.uma_android_automation.utils.DETAIL_RATING_H
import com.steve1316.uma_android_automation.utils.DETAIL_RATING_W
import com.steve1316.uma_android_automation.utils.DETAIL_RATING_X
import com.steve1316.uma_android_automation.utils.DETAIL_RATING_Y
import com.steve1316.uma_android_automation.utils.DETAIL_TITLE_H
import com.steve1316.uma_android_automation.utils.DETAIL_TITLE_W
import com.steve1316.uma_android_automation.utils.DETAIL_TITLE_X
import com.steve1316.uma_android_automation.utils.DETAIL_TITLE_Y
import com.steve1316.uma_android_automation.utils.ROSTER_ASCDESC_H
import com.steve1316.uma_android_automation.utils.ROSTER_ASCDESC_W
import com.steve1316.uma_android_automation.utils.ROSTER_ASCDESC_X
import com.steve1316.uma_android_automation.utils.ROSTER_ASCDESC_Y
import com.steve1316.uma_android_automation.utils.ROSTER_FILTERS_H
import com.steve1316.uma_android_automation.utils.ROSTER_FILTERS_W
import com.steve1316.uma_android_automation.utils.ROSTER_FILTERS_X
import com.steve1316.uma_android_automation.utils.ROSTER_FILTERS_Y
import com.steve1316.uma_android_automation.utils.ROSTER_REGISTERED_H
import com.steve1316.uma_android_automation.utils.ROSTER_REGISTERED_W
import com.steve1316.uma_android_automation.utils.ROSTER_REGISTERED_X
import com.steve1316.uma_android_automation.utils.ROSTER_REGISTERED_Y
import com.steve1316.uma_android_automation.utils.ROSTER_SORT_H
import com.steve1316.uma_android_automation.utils.ROSTER_SORT_W
import com.steve1316.uma_android_automation.utils.ROSTER_SORT_X
import com.steve1316.uma_android_automation.utils.ROSTER_SORT_Y
import com.steve1316.uma_android_automation.utils.STAT_CELL_H
import com.steve1316.uma_android_automation.utils.STAT_CELL_W
import com.steve1316.uma_android_automation.utils.STAT_COL_X
import com.steve1316.uma_android_automation.utils.STAT_LABELS
import com.steve1316.uma_android_automation.utils.STAT_ROW_Y
import com.steve1316.uma_android_automation.utils.RosterIdentityEvidence
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.classifyFavoriteMarker
import com.steve1316.uma_android_automation.utils.parseAptitudeGrade
import com.steve1316.uma_android_automation.utils.parseCareerRatingValue
import com.steve1316.uma_android_automation.utils.parseCareerRecord
import com.steve1316.uma_android_automation.utils.parseCareerScenario
import com.steve1316.uma_android_automation.utils.parseDateAcquired
import com.steve1316.uma_android_automation.utils.parseFansEarned
import com.steve1316.uma_android_automation.utils.parseFiltersOff
import com.steve1316.uma_android_automation.utils.parseNameOutfit
import com.steve1316.uma_android_automation.utils.parseRank
import com.steve1316.uma_android_automation.utils.parseRating
import com.steve1316.uma_android_automation.utils.parseRegistered
import com.steve1316.uma_android_automation.utils.parseSortDirection
import com.steve1316.uma_android_automation.utils.parseSortKey
import com.steve1316.uma_android_automation.utils.parseStatCell
import com.steve1316.uma_android_automation.utils.rosterFingerprint

private const val TAG = "[VeteranRosterReader]"

/**
 * Zero-gesture, read-only calibration diagnostic for the Veteran Roster list and the
 * `Umamusume Details` dialog (PL-R1a). The operator parks the game by hand on either screen; this
 * captures ONE frame, decides which screen it is on, logs every field it can read (raw OCR string
 * alongside the parsed value) tagged `[ROSTER-TEST]`, and stops. It never taps, swipes, changes tabs,
 * presses a chevron, or touches sort/filter/favorite/memo state - see PL-R1 design doc Part 1's deny
 * list. The 257-entry chevron walk is PL-R1b, not this task.
 *
 * Header identity fields (name/outfit/rank/rating/stats/aptitudes) are read whenever the dialog is
 * open, since that band sits above the tab strip and is visible on every tab. The Career Info fields
 * only resolve once the operator has manually opened that tab and scrolled the Career block into
 * view; if the label OCR at those fixed positions does not match the expected text (because a
 * different tab or scroll position is showing), the field is logged as unavailable rather than
 * trusting whatever text happens to sit there.
 */
class VeteranRosterReader(private val iu: CustomImageUtils) {
    private fun ocr(bitmap: Bitmap, x: Int, y: Int, w: Int, h: Int, debugName: String): String =
        try {
            iu.performOCROnRegion(bitmap, x, y, w, h, useThreshold = true, useGrayscale = true, scale = 2.0, debugName = "roster_$debugName")
                .replace("\r", "")
                .trim()
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            ""
        }

    fun debugRead() {
        val bitmap = iu.getSourceBitmap()
        MessageLog.i(TAG, "[ROSTER-TEST] ===== Veteran Roster read-only diagnostic (${bitmap.width}x${bitmap.height}) =====")

        val registeredRaw = ocr(bitmap, ROSTER_REGISTERED_X, ROSTER_REGISTERED_Y, ROSTER_REGISTERED_W, ROSTER_REGISTERED_H, "registered")
        val registered = parseRegistered(registeredRaw)
        val titleRaw = ocr(bitmap, DETAIL_TITLE_X, DETAIL_TITLE_Y, DETAIL_TITLE_W, DETAIL_TITLE_H, "title")

        when {
            registered != null -> readRosterList(bitmap, registeredRaw, registered)
            titleRaw.uppercase().contains("DETAIL") -> readUmamusumeDetails(bitmap)
            else -> {
                MessageLog.w(
                    TAG,
                    "[ROSTER-TEST] screenKind=UNKNOWN - registered OCR='$registeredRaw' title OCR='$titleRaw'. " +
                        "Park the game on the Veteran Roster list or an open Umamusume Details dialog and re-run.",
                )
            }
        }
        MessageLog.i(TAG, "[ROSTER-TEST] ===== end =====")
    }

    private fun readRosterList(bitmap: Bitmap, registeredRaw: String, registered: Pair<Int, Int>) {
        MessageLog.i(TAG, "[ROSTER-TEST] screenKind=ROSTER_LIST")
        MessageLog.i(TAG, "[ROSTER-TEST] Registered OCR='$registeredRaw' -> used=${registered.first} capacity=${registered.second}")

        val filtersRaw = ocr(bitmap, ROSTER_FILTERS_X, ROSTER_FILTERS_Y, ROSTER_FILTERS_W, ROSTER_FILTERS_H, "filters")
        val filtersOff = parseFiltersOff(filtersRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Filters OCR='$filtersRaw' -> filtersOff=${filtersOff ?: "UNRESOLVED"}")

        val sortRaw = ocr(bitmap, ROSTER_SORT_X, ROSTER_SORT_Y, ROSTER_SORT_W, ROSTER_SORT_H, "sortkey")
        val sortKey = parseSortKey(sortRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Sort key OCR='$sortRaw' -> sortKey=${sortKey ?: "UNRESOLVED"}")

        val ascDescRaw = ocr(bitmap, ROSTER_ASCDESC_X, ROSTER_ASCDESC_Y, ROSTER_ASCDESC_W, ROSTER_ASCDESC_H, "ascdesc")
        val sortDirection = parseSortDirection(ascDescRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Sort direction OCR='$ascDescRaw' -> sortDirection=${sortDirection ?: "UNRESOLVED"}")

        if (filtersOff != true) {
            MessageLog.w(TAG, "[ROSTER-TEST] filtersOff is not confirmed true - a full roster scan (PL-R1b) must treat this as a fail-closed precondition, not proceed.")
        }
    }

    private fun readUmamusumeDetails(bitmap: Bitmap) {
        MessageLog.i(TAG, "[ROSTER-TEST] screenKind=UMAMUSUME_DETAILS")

        val nameOutfitRaw = ocr(bitmap, DETAIL_NAME_OUTFIT_X, DETAIL_NAME_OUTFIT_Y, DETAIL_NAME_OUTFIT_W, DETAIL_NAME_OUTFIT_H, "name_outfit")
        val (outfit, name) = parseNameOutfit(nameOutfitRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Name/Outfit OCR='${nameOutfitRaw.replace("\n", " | ")}' -> outfit=${outfit ?: "UNRESOLVED"} name=${name ?: "UNRESOLVED"}")

        val rankRaw = ocr(bitmap, DETAIL_RANK_X, DETAIL_RANK_Y, DETAIL_RANK_W, DETAIL_RANK_H, "rank")
        val rank = parseRank(rankRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Rank OCR='${rankRaw.replace("\n", " | ")}' -> rank=${rank ?: "UNRESOLVED"}")

        val ratingRaw = ocr(bitmap, DETAIL_RATING_X, DETAIL_RATING_Y, DETAIL_RATING_W, DETAIL_RATING_H, "rating")
        val rating = parseRating(ratingRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Rating OCR='$ratingRaw' -> rating=${rating ?: "UNRESOLVED"}")

        val stats = mutableListOf<Int?>()
        for (i in STAT_COL_X.indices) {
            val raw = ocr(bitmap, STAT_COL_X[i], STAT_ROW_Y, STAT_CELL_W, STAT_CELL_H, "stat_${STAT_LABELS[i]}")
            val cell = parseStatCell(raw)
            stats.add(cell.value)
            MessageLog.i(TAG, "[ROSTER-TEST] ${STAT_LABELS[i]} OCR='${raw.replace("\n", " ")}' -> grade=${cell.grade ?: "UNRESOLVED"} value=${cell.value ?: "UNRESOLVED"}")
        }

        val aptitudes = mutableListOf<Pair<String, String?>>()
        fun readAptitudeRow(colXs: List<Int>, y: Int, w: Int, h: Int, labels: List<String>) {
            for (i in colXs.indices) {
                val raw = ocr(bitmap, colXs[i], y, w, h, "apt_${labels[i]}")
                val grade = parseAptitudeGrade(raw)
                aptitudes.add(labels[i] to grade)
                MessageLog.i(TAG, "[ROSTER-TEST] Aptitude ${labels[i]} OCR='${raw.replace("\n", " ")}' -> grade=${grade ?: "UNRESOLVED"}")
            }
        }
        readAptitudeRow(APTITUDE_TRACK_COL_X, APTITUDE_TRACK_Y, APTITUDE_TRACK_CELL_W, APTITUDE_TRACK_CELL_H, APTITUDE_TRACK_LABELS)
        readAptitudeRow(APTITUDE_DISTANCE_COL_X, APTITUDE_DISTANCE_Y, APTITUDE_DISTANCE_CELL_W, APTITUDE_DISTANCE_CELL_H, APTITUDE_DISTANCE_LABELS)
        readAptitudeRow(APTITUDE_STYLE_COL_X, APTITUDE_STYLE_Y, APTITUDE_STYLE_CELL_W, APTITUDE_STYLE_CELL_H, APTITUDE_STYLE_LABELS)

        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
        val favorite = classifyFavoriteMarker(sampler)
        MessageLog.i(TAG, "[ROSTER-TEST] Favorite marker classification (never tapped) = $favorite")

        // Career Info block: only meaningful if the operator scrolled that tab into view. Read
        // opportunistically; a field whose label OCR does not match stays UNAVAILABLE rather than
        // trusting whatever text sits at that position on a different tab.
        val recordRaw = ocr(bitmap, CAREER_RECORD_X, CAREER_RECORD_Y, CAREER_RECORD_W, CAREER_RECORD_H, "career_record")
        val record = parseCareerRecord(recordRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Career Record OCR='$recordRaw' -> races=${record?.first ?: "UNAVAILABLE"} wins=${record?.second ?: "UNAVAILABLE"}")

        val fansRaw = ocr(bitmap, CAREER_FANS_X, CAREER_FANS_Y, CAREER_FANS_W, CAREER_FANS_H, "career_fans")
        val fans = parseFansEarned(fansRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Fans Earned OCR='$fansRaw' -> fans=${fans ?: "UNAVAILABLE"}")

        val scenarioRaw = ocr(bitmap, CAREER_SCENARIO_X, CAREER_SCENARIO_Y, CAREER_SCENARIO_W, CAREER_SCENARIO_H, "career_scenario")
        val scenario = parseCareerScenario(scenarioRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Career Scenario OCR='$scenarioRaw' -> scenario=${scenario ?: "UNAVAILABLE"}")

        val careerRatingRaw = ocr(bitmap, CAREER_RATING_X, CAREER_RATING_Y, CAREER_RATING_W, CAREER_RATING_H, "career_rating")
        val careerRating = parseCareerRatingValue(careerRatingRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Career Rating OCR='$careerRatingRaw' -> rating=${careerRating ?: "UNAVAILABLE"}")
        if (rating != null && careerRating != null && rating != careerRating) {
            MessageLog.w(TAG, "[ROSTER-TEST] Header rating ($rating) and Career Info rating ($careerRating) disagree - one of the two reads is wrong.")
        }

        val dateRaw = ocr(bitmap, CAREER_DATE_ACQUIRED_X, CAREER_DATE_ACQUIRED_Y, CAREER_DATE_ACQUIRED_W, CAREER_DATE_ACQUIRED_H, "career_date")
        val date = parseDateAcquired(dateRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Date Acquired OCR='$dateRaw' -> dateAcquired=${date ?: "UNAVAILABLE"}")

        // rosterFingerprint: only computed when every immutable-evidence field resolved. Pure function
        // of the parsed evidence (proven deterministic by VeteranRosterProbesTest), so running this
        // diagnostic twice against the same parked frame is expected to log the identical hash both
        // times - that repeat run is the live-validation proof, not something to fake in-process here.
        computeAndLogFingerprint(name, outfit, rank, rating, stats, aptitudes)
    }

    private fun computeAndLogFingerprint(
        name: String?,
        outfit: String?,
        rank: String?,
        rating: Int?,
        stats: List<Int?>,
        aptitudes: List<Pair<String, String?>>,
    ) {
        val statValues = stats.filterNotNull()
        val aptitudeValues = aptitudes.mapNotNull { it.second }
        val missing =
            buildList {
                if (name == null) add("character")
                if (outfit == null) add("outfit")
                if (rank == null) add("rank")
                if (rating == null) add("rating")
                if (statValues.size != stats.size) add("stats(${stats.size - statValues.size} unresolved)")
                if (aptitudeValues.size != aptitudes.size) add("aptitudes(${aptitudes.size - aptitudeValues.size} unresolved)")
            }
        if (missing.isNotEmpty() || name == null || outfit == null || rank == null || rating == null) {
            MessageLog.i(TAG, "[ROSTER-TEST] rosterFingerprint UNAVAILABLE - missing: ${missing.joinToString(", ")}")
            return
        }
        val evidence = RosterIdentityEvidence(name, outfit, rank, rating, statValues, aptitudeValues)
        MessageLog.i(TAG, "[ROSTER-TEST] rosterFingerprint=${rosterFingerprint(evidence)}")
    }
}
