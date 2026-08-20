package com.steve1316.uma_android_automation

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.utils.APTITUDE_GRADE_BOXES
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
import com.steve1316.uma_android_automation.utils.DETAIL_NAME_OUTFIT_H
import com.steve1316.uma_android_automation.utils.DETAIL_NAME_OUTFIT_W
import com.steve1316.uma_android_automation.utils.DETAIL_NAME_OUTFIT_X
import com.steve1316.uma_android_automation.utils.DETAIL_NAME_OUTFIT_Y
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
import com.steve1316.uma_android_automation.utils.STAT_GRADE_GLYPH_BOXES
import com.steve1316.uma_android_automation.utils.STAT_LABELS
import com.steve1316.uma_android_automation.utils.STAT_VALUE_BOXES
import com.steve1316.uma_android_automation.utils.RosterIdentityEvidence
import com.steve1316.uma_android_automation.utils.RosterScreenKind
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.classifyAptitudeGrade
import com.steve1316.uma_android_automation.utils.classifyFavoriteMarker
import com.steve1316.uma_android_automation.utils.classifyRankMedal
import com.steve1316.uma_android_automation.utils.classifyRosterScreen
import com.steve1316.uma_android_automation.utils.classifyStatGrade
import com.steve1316.uma_android_automation.utils.parseCareerRatingValue
import com.steve1316.uma_android_automation.utils.parseCareerRecord
import com.steve1316.uma_android_automation.utils.parseCareerScenario
import com.steve1316.uma_android_automation.utils.parseDateAcquired
import com.steve1316.uma_android_automation.utils.parseFansEarned
import com.steve1316.uma_android_automation.utils.parseFiltersOff
import com.steve1316.uma_android_automation.utils.parseRating
import com.steve1316.uma_android_automation.utils.parseRegistered
import com.steve1316.uma_android_automation.utils.parseSortDirection
import com.steve1316.uma_android_automation.utils.parseSortKey
import com.steve1316.uma_android_automation.utils.parseStatValue
import com.steve1316.uma_android_automation.utils.resolveNameOutfit
import com.steve1316.uma_android_automation.utils.rosterFingerprint

private const val TAG = "[VeteranRosterReader]"

/**
 * How many times [VeteranRosterReader.debugRead] captures and classifies before trusting an UNKNOWN
 * verdict, and how long it waits between tries. Sized to outlast the bot's own "Automation is now
 * running" heads-up notification, which peeks over the title band for the first few seconds of a
 * session (this diagnostic runs at session start, before any navigation delay).
 */
private const val CLASSIFY_ATTEMPTS = 6
private const val CLASSIFY_RETRY_DELAY_MS = 1500L

/**
 * Zero-gesture, read-only calibration diagnostic for the Veteran Roster list and the
 * `Umamusume Details` dialog (PL-R1a). The operator parks the game by hand on either screen; this
 * captures ONE frame, decides which screen it is on, logs every field it can read tagged
 * `[ROSTER-TEST]`, and stops. It never taps, swipes, changes tabs, presses a chevron, or touches
 * sort/filter/favorite/memo state - see PL-R1 design doc Part 1's deny list. The 257-entry chevron
 * walk is PL-R1b, not this task.
 *
 * The styled identity fields are read by field-appropriate readers rather than generic OCR: the
 * rank medal and every grade badge by pixel classifiers ([classifyRankMedal], [classifyStatGrade],
 * [classifyAptitudeGrade]), the character/outfit by OCR snapped onto the known-name domain
 * ([resolveNameOutfit]), and each stat value by digit-only OCR of its own box (keeping the coloured
 * badge out of the number read). The Career Info fields only resolve once the operator has manually
 * opened that tab and scrolled the Career block into view; a field whose label OCR does not match is
 * logged unavailable rather than trusting whatever text sits there.
 */
class VeteranRosterReader(private val iu: CustomImageUtils) {
    private fun ocr(bitmap: Bitmap, x: Int, y: Int, w: Int, h: Int, debugName: String, digitsOnly: Boolean = false): String =
        try {
            iu.performOCROnRegion(
                bitmap,
                x,
                y,
                w,
                h,
                useThreshold = true,
                useGrayscale = true,
                scale = 2.0,
                ocrEngine = if (digitsOnly) "tesseract_digits" else "tesseract",
                debugName = "roster_$debugName",
            )
                .replace("\r", "")
                .trim()
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            ""
        }

    fun debugRead() {
        // This diagnostic runs at session start (startTests, before any navigation delay), so its first
        // capture lands while the bot's own "Automation is now running" foreground-service notification
        // is still peeking as a heads-up banner over the top of the screen. That banner covers the
        // "Umamusume Details" title (OCR reads "Status ... Automation is now running"), misclassifying a
        // valid Details dialog as UNKNOWN. Re-capture a few times so the heads-up dismisses before we
        // trust an UNKNOWN verdict; a genuinely wrong screen still ends UNKNOWN after the last attempt.
        var bitmap = iu.getSourceBitmap()
        var registeredRaw = ""
        var registered: Pair<Int, Int>? = null
        var titleRaw = ""
        var kind = RosterScreenKind.UNKNOWN
        for (attempt in 1..CLASSIFY_ATTEMPTS) {
            registeredRaw = ocr(bitmap, ROSTER_REGISTERED_X, ROSTER_REGISTERED_Y, ROSTER_REGISTERED_W, ROSTER_REGISTERED_H, "registered")
            registered = parseRegistered(registeredRaw)
            titleRaw = ocr(bitmap, DETAIL_TITLE_X, DETAIL_TITLE_Y, DETAIL_TITLE_W, DETAIL_TITLE_H, "title")
            kind = classifyRosterScreen(registered, titleRaw)
            if (kind != RosterScreenKind.UNKNOWN || attempt == CLASSIFY_ATTEMPTS) break
            Thread.sleep(CLASSIFY_RETRY_DELAY_MS)
            bitmap = iu.getSourceBitmap()
        }

        MessageLog.i(TAG, "[ROSTER-TEST] ===== Veteran Roster read-only diagnostic (${bitmap.width}x${bitmap.height}) =====")
        when (kind) {
            // classifyRosterScreen returns ROSTER_LIST only when registered parsed non-null.
            RosterScreenKind.ROSTER_LIST -> readRosterList(bitmap, registeredRaw, registered!!)
            RosterScreenKind.UMAMUSUME_DETAILS -> readUmamusumeDetails(bitmap)
            RosterScreenKind.UNKNOWN ->
                MessageLog.w(
                    TAG,
                    "[ROSTER-TEST] screenKind=UNKNOWN - registered OCR='$registeredRaw' title OCR='$titleRaw'. " +
                        "Park the game on the Veteran Roster list or an open Umamusume Details dialog and re-run.",
                )
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
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }

        val nameOutfitRaw = ocr(bitmap, DETAIL_NAME_OUTFIT_X, DETAIL_NAME_OUTFIT_Y, DETAIL_NAME_OUTFIT_W, DETAIL_NAME_OUTFIT_H, "name_outfit")
        val identity = resolveNameOutfit(nameOutfitRaw)
        val outfit = identity.outfit
        val name = identity.name
        MessageLog.i(TAG, "[ROSTER-TEST] Name/Outfit OCR='${nameOutfitRaw.replace("\n", " | ")}' -> outfit=${outfit ?: "UNRESOLVED"} name=${name ?: "UNRESOLVED"}")

        val rank = classifyRankMedal(sampler)
        MessageLog.i(TAG, "[ROSTER-TEST] Rank medal classifier -> rank=${rank ?: "UNRESOLVED"}")

        val ratingRaw = ocr(bitmap, DETAIL_RATING_X, DETAIL_RATING_Y, DETAIL_RATING_W, DETAIL_RATING_H, "rating")
        val rating = parseRating(ratingRaw)
        MessageLog.i(TAG, "[ROSTER-TEST] Rating OCR='$ratingRaw' -> rating=${rating ?: "UNRESOLVED"}")

        val stats = mutableListOf<Int?>()
        for (i in STAT_LABELS.indices) {
            val grade = classifyStatGrade(sampler, STAT_GRADE_GLYPH_BOXES[i])
            val valueBox = STAT_VALUE_BOXES[i]
            val valueRaw = ocr(bitmap, valueBox.x0, valueBox.y0, valueBox.x1 - valueBox.x0, valueBox.y1 - valueBox.y0, "stat_${STAT_LABELS[i]}", digitsOnly = true)
            val value = parseStatValue(valueRaw)
            stats.add(value)
            MessageLog.i(TAG, "[ROSTER-TEST] ${STAT_LABELS[i]} grade=${grade ?: "UNRESOLVED"} valueOCR='$valueRaw' -> value=${value ?: "UNRESOLVED"}")
        }

        val aptitudes = mutableListOf<Pair<String, String?>>()
        for ((role, box) in APTITUDE_GRADE_BOXES) {
            val grade = classifyAptitudeGrade(sampler, box)
            aptitudes.add(role to grade)
            MessageLog.i(TAG, "[ROSTER-TEST] Aptitude $role -> grade=${grade ?: "UNRESOLVED"}")
        }

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
