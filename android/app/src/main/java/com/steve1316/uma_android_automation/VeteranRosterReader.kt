package com.steve1316.uma_android_automation

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.RosterCareerInfoObservation
import com.steve1316.uma_android_automation.bot.RosterEntryDiagnostics
import com.steve1316.uma_android_automation.bot.RosterEntryObservation
import com.steve1316.uma_android_automation.bot.RosterListState
import com.steve1316.uma_android_automation.bot.entryFingerprint
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
import com.steve1316.uma_android_automation.utils.GlyphBox
import com.steve1316.uma_android_automation.utils.NumericReadCandidate
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
import com.steve1316.uma_android_automation.utils.RosterScreenKind
import com.steve1316.uma_android_automation.utils.STAT_GRADE_GLYPH_BOXES
import com.steve1316.uma_android_automation.utils.STAT_LABELS
import com.steve1316.uma_android_automation.utils.STAT_VALUE_BOXES
import com.steve1316.uma_android_automation.utils.STAT_VALUE_RECOVERY_BOXES
import com.steve1316.uma_android_automation.utils.STAT_VALUE_SUSPICIOUS_MIN
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.VeteranIdentityCatalog
import com.steve1316.uma_android_automation.utils.classifyAptitudeGrade
import com.steve1316.uma_android_automation.utils.classifyFavoriteMarker
import com.steve1316.uma_android_automation.utils.classifyRankMedalDetailed
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
import com.steve1316.uma_android_automation.utils.resolveStatValue

private const val TAG = "[VeteranRosterReader]"

/**
 * How many times [VeteranRosterReader.classifyScreenWithRetries] captures and classifies before
 * trusting an UNKNOWN verdict, and how long it waits between tries. Sized to outlast the bot's own
 * "Automation is now running" heads-up notification, which peeks over the title band for the first
 * few seconds of a session (both roster diagnostics run at session start, before any navigation
 * delay).
 */
private const val CLASSIFY_ATTEMPTS = 6
private const val CLASSIFY_RETRY_DELAY_MS = 1500L

/** One frame's screen verdict plus the raw evidence it was decided from, kept together so a caller
 * can report exactly what it saw when the verdict is UNKNOWN. */
data class RosterScreenRead(
    val kind: RosterScreenKind,
    val registered: Pair<Int, Int>?,
    val registeredRaw: String,
    val titleRaw: String,
)

/**
 * Read-only field reader for the Veteran Roster list and the `Umamusume Details` dialog.
 *
 * This class never taps, swipes, or changes tabs: it turns a captured frame into parsed facts and
 * nothing else. Two callers use it. [debugRead] is the PL-R1a zero-gesture calibration diagnostic:
 * the operator parks the game by hand, and it logs every field with its raw OCR string alongside the
 * parsed value under `[ROSTER-TEST]`. [VeteranRosterScanner] drives the chevron walk and calls the
 * same readers quietly, so the walk's per-entry log stays one line instead of twenty.
 *
 * The styled identity fields are read by field-appropriate readers rather than generic OCR: the rank
 * medal and every grade badge by pixel classifiers ([classifyRankMedalDetailed], [classifyStatGrade],
 * [classifyAptitudeGrade]), the character by OCR snapped onto the known-name domain and the outfit
 * by OCR snapped onto THAT character's own costumes ([resolveNameOutfit] over [catalog]), and each
 * stat value by digit-only OCR of its own box (keeping the coloured badge out of the number read). The Career Info fields only resolve once that tab is open and
 * scrolled into view; a field whose label OCR does not match is reported unavailable rather than
 * trusting whatever text sits there.
 */
class VeteranRosterReader(
    private val iu: CustomImageUtils,
    /** The character/outfit identity domain. Null when the generated asset did not load, in which
     * case outfits read unresolved rather than falling back to a guess (see [VeteranIdentityCatalog]). */
    private val catalog: VeteranIdentityCatalog?,
) {
    private fun ocr(bitmap: Bitmap, x: Int, y: Int, w: Int, h: Int, debugName: String, digitsOnly: Boolean = false, thresholdIncrement: Double = 0.0): String =
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
                thresholdIncrement = thresholdIncrement,
            )
                .replace("\r", "")
                .trim()
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            ""
        }

    /** One stat number resolved from up to two independent geometries, plus the evidence for it. */
    private data class StatValueRead(val value: Int?, val evidence: String?, val resolvedBy: String)

    /** Threshold increments a single geometry is re-read at, then voted, to shake loose a
     * threshold-sensitive misread. Mirrors the retry ladder's add-only direction (blacker
     * binarization). The base pass (0) is always first so the common good read is unchanged. */
    private val statThresholdVariants = listOf(0.0, 8.0, 16.0)

    /**
     * Reads stat [i]'s numeric value with a fail-closed multi-pass reader.
     *
     * The common path is unchanged: one digit-OCR of the primary box, and a plausible non-suspicious
     * value (>= [STAT_VALUE_SUSPICIOUS_MIN]) is returned as-is - no extra OCR, byte-identical to before.
     * Only when that read is unacceptable (out of range / blank) or suspiciously low does the reader
     * gather corroborating candidates: it votes the primary box across [statThresholdVariants], votes
     * the badge-aware [STAT_VALUE_RECOVERY_BOXES] geometry the same way (recovering a clipped 4-digit
     * leading digit or dropping the Wit grey UI), and hands both to [resolveStatValue]. Two independent
     * geometries agreeing accepts; a lone plausible high read accepts; a lone suspicious-low read, or
     * a genuine conflict, stays unresolved with its raw candidates kept as evidence.
     */
    private fun readStatValue(bitmap: Bitmap, i: Int): StatValueRead {
        val label = STAT_LABELS[i]
        val primaryRaw = ocrStatBox(bitmap, STAT_VALUE_BOXES[i], "stat_$label", 0.0)
        val primary = parseStatValue(primaryRaw)
        // Fast path: a plausible, non-suspicious primary read is trusted exactly as before.
        if (primary != null && primary >= STAT_VALUE_SUSPICIOUS_MIN) {
            return StatValueRead(primary, primaryRaw.ifEmpty { null }, "primary")
        }

        val candidates = mutableListOf<NumericReadCandidate>()
        val evidence = mutableListOf<String>()
        if (primaryRaw.isNotEmpty()) evidence.add(primaryRaw)

        // Geometry 1: the primary box, voted across threshold variants (the base pass is reused).
        val primaryVote = voteGeometry(bitmap, STAT_VALUE_BOXES[i], "stat_$label", primaryRaw)
        if (primaryVote.value != null) candidates.add(NumericReadCandidate(primaryVote.value, "primary", primaryVote.raw))

        // Geometry 2: the badge-aware recovery box, when one exists for this column.
        STAT_VALUE_RECOVERY_BOXES[i]?.let { box ->
            val wideVote = voteGeometry(bitmap, box, "stat_${label}_wide", firstRaw = null)
            if (wideVote.raw.isNotEmpty()) evidence.add("wide=${wideVote.raw}")
            if (wideVote.value != null) candidates.add(NumericReadCandidate(wideVote.value, "wide", wideVote.raw))
        }

        val value = resolveStatValue(candidates)
        val resolvedBy =
            when {
                value == null -> "unresolved"
                candidates.count { it.value == value } >= 2 -> "consensus"
                else -> candidates.first { it.value == value }.variant
            }
        return StatValueRead(value, evidence.joinToString("|").ifEmpty { null }, resolvedBy)
    }

    private data class GeometryVote(val value: Int?, val raw: String)

    private fun ocrStatBox(bitmap: Bitmap, box: GlyphBox, debugName: String, thresholdIncrement: Double): String =
        ocr(bitmap, box.x0, box.y0, box.x1 - box.x0, box.y1 - box.y0, debugName, digitsOnly = true, thresholdIncrement = thresholdIncrement)

    /**
     * Reads one geometry at every [statThresholdVariants] increment and returns its confident vote:
     * the plausible value with a strict plurality across the passes (a tie is no vote). [firstRaw],
     * when supplied, reuses an already-taken base-threshold read so the primary box is not OCR'd twice.
     */
    private fun voteGeometry(bitmap: Bitmap, box: GlyphBox, debugName: String, firstRaw: String?): GeometryVote {
        val values = mutableListOf<Int>()
        var baseRaw = firstRaw ?: ""
        statThresholdVariants.forEachIndexed { idx, inc ->
            val raw = if (idx == 0 && firstRaw != null) firstRaw else ocrStatBox(bitmap, box, debugName, inc)
            if (idx == 0) baseRaw = raw
            parseStatValue(raw)?.let { values.add(it) }
        }
        if (values.isEmpty()) return GeometryVote(null, baseRaw)
        val counts = values.groupingBy { it }.eachCount()
        val max = counts.values.max()
        val winner = counts.filterValues { it == max }.keys.singleOrNull()
        return GeometryVote(winner, baseRaw)
    }

    /** Classifies one already-captured frame as the roster list, the details dialog, or unknown. */
    fun classifyScreen(bitmap: Bitmap): RosterScreenRead {
        val registeredRaw = ocr(bitmap, ROSTER_REGISTERED_X, ROSTER_REGISTERED_Y, ROSTER_REGISTERED_W, ROSTER_REGISTERED_H, "registered")
        val registered = parseRegistered(registeredRaw)
        val titleRaw = ocr(bitmap, DETAIL_TITLE_X, DETAIL_TITLE_Y, DETAIL_TITLE_W, DETAIL_TITLE_H, "title")
        return RosterScreenRead(classifyRosterScreen(registered, titleRaw), registered, registeredRaw, titleRaw)
    }

    /**
     * Captures and classifies repeatedly until a known screen appears or the attempts run out.
     *
     * The retry exists for one measured reason: at session start the bot's own "Automation is now
     * running" foreground-service notification peeks as a heads-up banner over the title band, so a
     * valid Details dialog momentarily reads its title as "Status ... Automation is now running" and
     * classifies UNKNOWN. Re-capturing outlasts the banner; a genuinely wrong screen still ends
     * UNKNOWN after the last attempt. Returns the frame the verdict was reached on.
     */
    fun classifyScreenWithRetries(attempts: Int = CLASSIFY_ATTEMPTS): Pair<Bitmap, RosterScreenRead> {
        var bitmap = iu.getSourceBitmap()
        var read = classifyScreen(bitmap)
        for (attempt in 2..attempts) {
            if (read.kind != RosterScreenKind.UNKNOWN) break
            Thread.sleep(CLASSIFY_RETRY_DELAY_MS)
            bitmap = iu.getSourceBitmap()
            read = classifyScreen(bitmap)
        }
        return bitmap to read
    }

    /** The roster list status bar. [screen] supplies the already-parsed `Registered X/Y` so the
     * caller does not pay for that OCR twice. */
    fun readListState(bitmap: Bitmap, screen: RosterScreenRead, verbose: Boolean = false): RosterListState {
        val filtersRaw = ocr(bitmap, ROSTER_FILTERS_X, ROSTER_FILTERS_Y, ROSTER_FILTERS_W, ROSTER_FILTERS_H, "filters")
        val filtersOff = parseFiltersOff(filtersRaw)
        val sortRaw = ocr(bitmap, ROSTER_SORT_X, ROSTER_SORT_Y, ROSTER_SORT_W, ROSTER_SORT_H, "sortkey")
        val sortKey = parseSortKey(sortRaw)
        val ascDescRaw = ocr(bitmap, ROSTER_ASCDESC_X, ROSTER_ASCDESC_Y, ROSTER_ASCDESC_W, ROSTER_ASCDESC_H, "ascdesc")
        val sortDirection = parseSortDirection(ascDescRaw)

        if (verbose) {
            MessageLog.i(TAG, "[ROSTER-TEST] Registered OCR='${screen.registeredRaw}' -> used=${screen.registered?.first} capacity=${screen.registered?.second}")
            MessageLog.i(TAG, "[ROSTER-TEST] Filters OCR='$filtersRaw' -> filtersOff=${filtersOff ?: "UNRESOLVED"}")
            MessageLog.i(TAG, "[ROSTER-TEST] Sort key OCR='$sortRaw' -> sortKey=${sortKey ?: "UNRESOLVED"}")
            MessageLog.i(TAG, "[ROSTER-TEST] Sort direction OCR='$ascDescRaw' -> sortDirection=${sortDirection ?: "UNRESOLVED"}")
        }
        return RosterListState(screen.registered?.first, screen.registered?.second, filtersOff, sortKey, sortDirection)
    }

    /**
     * Every identity field the detail dialog's persistent header carries, plus the Career Info block
     * when [includeCareerInfo] is set and that tab is actually open. The header band sits above the
     * tab strip and stays on screen whichever tab is selected, so the identity read never depends on
     * the operator's tab choice; the Career block does, which is why it is opt-in and why every one
     * of its parsers refuses a crop whose shape does not match.
     */
    fun readDetailObservation(bitmap: Bitmap, includeCareerInfo: Boolean = true, verbose: Boolean = false): RosterEntryObservation {
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }

        val nameOutfitRaw = ocr(bitmap, DETAIL_NAME_OUTFIT_X, DETAIL_NAME_OUTFIT_Y, DETAIL_NAME_OUTFIT_W, DETAIL_NAME_OUTFIT_H, "name_outfit")
        val identity = resolveNameOutfit(nameOutfitRaw, catalog)
        val rankRead = classifyRankMedalDetailed(sampler)
        val rank = rankRead.tier
        val ratingRaw = ocr(bitmap, DETAIL_RATING_X, DETAIL_RATING_Y, DETAIL_RATING_W, DETAIL_RATING_H, "rating")
        val rating = parseRating(ratingRaw)

        if (verbose) {
            MessageLog.i(
                TAG,
                "[ROSTER-TEST] Name/Outfit OCR='${nameOutfitRaw.replace("\n", " | ")}' -> outfit=${identity.outfit ?: "UNRESOLVED"} name=${identity.name ?: "UNRESOLVED"}",
            )
            MessageLog.i(
                TAG,
                "[ROSTER-TEST] Rank medal classifier -> rank=${rank ?: "UNRESOLVED"} " +
                    "(family=${rankRead.family ?: "none"} best=${rankRead.chosen ?: "none"}@${rankRead.bestScore?.let { "%.3f".format(it) } ?: "n/a"} " +
                    "second=${rankRead.secondScore?.let { "%.3f".format(it) } ?: "n/a"})",
            )
            MessageLog.i(TAG, "[ROSTER-TEST] Rating OCR='$ratingRaw' -> rating=${rating ?: "UNRESOLVED"}")
        }

        val stats = mutableListOf<Int?>()
        val statGrades = mutableListOf<String?>()
        val rawStatOcr = mutableListOf<String?>()
        for (i in STAT_LABELS.indices) {
            val grade = classifyStatGrade(sampler, STAT_GRADE_GLYPH_BOXES[i])
            val read = readStatValue(bitmap, i)
            stats.add(read.value)
            statGrades.add(grade)
            // The raw string(s), kept as evidence and never as a value: resolveStatValue already
            // rejected implausible reads (a dropped digit renders 949 as "1") and lone suspicious-low
            // reads, and promoting one here would mint a wrong identity rather than merely lose a field.
            rawStatOcr.add(read.evidence)
            if (verbose) {
                MessageLog.i(
                    TAG,
                    "[ROSTER-TEST] ${STAT_LABELS[i]} grade=${grade ?: "UNRESOLVED"} valueOCR='${read.evidence ?: ""}' " +
                        "-> value=${read.value ?: "UNRESOLVED"} (${read.resolvedBy})",
                )
            }
        }

        val aptitudes = mutableListOf<String?>()
        for ((role, box) in APTITUDE_GRADE_BOXES) {
            val grade = classifyAptitudeGrade(sampler, box)
            aptitudes.add(grade)
            if (verbose) MessageLog.i(TAG, "[ROSTER-TEST] Aptitude $role -> grade=${grade ?: "UNRESOLVED"}")
        }

        val favorite = classifyFavoriteMarker(sampler)
        if (verbose) MessageLog.i(TAG, "[ROSTER-TEST] Favorite marker classification (never tapped) = $favorite")

        val careerInfo = if (includeCareerInfo) readCareerInfo(bitmap, rating, verbose) else null

        return RosterEntryObservation(
            character = identity.name,
            outfit = identity.outfit,
            rank = rank,
            rating = rating,
            stats = stats,
            statGrades = statGrades,
            aptitudes = aptitudes,
            favoriteState = favorite.name.lowercase(),
            careerInfo = careerInfo,
            diagnostics =
                RosterEntryDiagnostics(
                    rawNameOutfitOcr = nameOutfitRaw.ifEmpty { null },
                    rawRatingOcr = ratingRaw.ifEmpty { null },
                    rawStatOcr = rawStatOcr,
                    outfitCandidate = identity.outfitCandidate,
                    outfitScore = identity.outfitScore,
                    outfitSecondCandidate = identity.outfitSecondCandidate,
                    outfitSecondScore = identity.outfitSecondScore,
                    outfitAcceptancePath = identity.outfitAcceptancePath.name.lowercase(),
                    rankFamily = rankRead.family?.toString(),
                    rankChosen = rankRead.chosen,
                    rankBestScore = rankRead.bestScore,
                    rankSecondScore = rankRead.secondScore,
                    rankAcceptancePath = rankRead.acceptancePath.name.lowercase(),
                ),
        )
    }

    /** The Career Info key/value block. Only meaningful once that tab is open and scrolled into
     * view; every field whose label/shape does not match stays null rather than trusting whatever
     * text sits at that position on a different tab. */
    private fun readCareerInfo(bitmap: Bitmap, headerRating: Int?, verbose: Boolean): RosterCareerInfoObservation {
        val recordRaw = ocr(bitmap, CAREER_RECORD_X, CAREER_RECORD_Y, CAREER_RECORD_W, CAREER_RECORD_H, "career_record")
        val record = parseCareerRecord(recordRaw)
        val fansRaw = ocr(bitmap, CAREER_FANS_X, CAREER_FANS_Y, CAREER_FANS_W, CAREER_FANS_H, "career_fans")
        val fans = parseFansEarned(fansRaw)
        val scenarioRaw = ocr(bitmap, CAREER_SCENARIO_X, CAREER_SCENARIO_Y, CAREER_SCENARIO_W, CAREER_SCENARIO_H, "career_scenario")
        val scenario = parseCareerScenario(scenarioRaw)
        val careerRatingRaw = ocr(bitmap, CAREER_RATING_X, CAREER_RATING_Y, CAREER_RATING_W, CAREER_RATING_H, "career_rating")
        val careerRating = parseCareerRatingValue(careerRatingRaw)
        val dateRaw = ocr(bitmap, CAREER_DATE_ACQUIRED_X, CAREER_DATE_ACQUIRED_Y, CAREER_DATE_ACQUIRED_W, CAREER_DATE_ACQUIRED_H, "career_date")
        val date = parseDateAcquired(dateRaw)

        if (verbose) {
            MessageLog.i(TAG, "[ROSTER-TEST] Career Record OCR='$recordRaw' -> races=${record?.first ?: "UNAVAILABLE"} wins=${record?.second ?: "UNAVAILABLE"}")
            MessageLog.i(TAG, "[ROSTER-TEST] Fans Earned OCR='$fansRaw' -> fans=${fans ?: "UNAVAILABLE"}")
            MessageLog.i(TAG, "[ROSTER-TEST] Career Scenario OCR='$scenarioRaw' -> scenario=${scenario ?: "UNAVAILABLE"}")
            MessageLog.i(TAG, "[ROSTER-TEST] Career Rating OCR='$careerRatingRaw' -> rating=${careerRating ?: "UNAVAILABLE"}")
            MessageLog.i(TAG, "[ROSTER-TEST] Date Acquired OCR='$dateRaw' -> dateAcquired=${date ?: "UNAVAILABLE"}")
            if (headerRating != null && careerRating != null && headerRating != careerRating) {
                MessageLog.w(TAG, "[ROSTER-TEST] Header rating ($headerRating) and Career Info rating ($careerRating) disagree - one of the two reads is wrong.")
            }
        }
        return RosterCareerInfoObservation(record?.first, record?.second, fans, scenario, careerRating, date)
    }

    /**
     * PL-R1a's zero-gesture calibration diagnostic. The operator parks the game by hand on either
     * screen; this captures ONE frame, decides which screen it is on, logs every field it can read
     * tagged `[ROSTER-TEST]`, and stops. It never taps, swipes, changes tabs, presses a chevron, or
     * touches sort/filter/favorite/memo state - see PL-R1 design doc Part 1's deny list. The chevron
     * walk lives in [VeteranRosterScanner], not here.
     */
    fun debugRead() {
        val (bitmap, screen) = classifyScreenWithRetries()
        MessageLog.i(TAG, "[ROSTER-TEST] ===== Veteran Roster read-only diagnostic (${bitmap.width}x${bitmap.height}) =====")
        when (screen.kind) {
            RosterScreenKind.ROSTER_LIST -> {
                MessageLog.i(TAG, "[ROSTER-TEST] screenKind=ROSTER_LIST")
                val list = readListState(bitmap, screen, verbose = true)
                if (list.filtersOff != true) {
                    MessageLog.w(
                        TAG,
                        "[ROSTER-TEST] filtersOff is not confirmed true - a full roster scan must treat this as a fail-closed precondition, not proceed.",
                    )
                }
            }
            RosterScreenKind.UMAMUSUME_DETAILS -> {
                MessageLog.i(TAG, "[ROSTER-TEST] screenKind=UMAMUSUME_DETAILS")
                val observation = readDetailObservation(bitmap, includeCareerInfo = true, verbose = true)
                // rosterFingerprint: only computed when every immutable-evidence field resolved. Pure
                // function of the parsed evidence (proven deterministic by VeteranRosterScanEventTest),
                // so running this diagnostic twice against the same parked frame is expected to log the
                // identical hash both times - that repeat run is the live-validation proof, not
                // something to fake in-process here.
                val fingerprint = entryFingerprint(observation)
                if (fingerprint != null) {
                    MessageLog.i(TAG, "[ROSTER-TEST] rosterFingerprint=$fingerprint")
                } else {
                    MessageLog.i(TAG, "[ROSTER-TEST] rosterFingerprint UNAVAILABLE - one or more identity fields did not resolve (see the UNRESOLVED lines above).")
                }
            }
            RosterScreenKind.UNKNOWN ->
                MessageLog.w(
                    TAG,
                    "[ROSTER-TEST] screenKind=UNKNOWN - registered OCR='${screen.registeredRaw}' title OCR='${screen.titleRaw}'. " +
                        "Park the game on the Veteran Roster list or an open Umamusume Details dialog and re-run.",
                )
        }
        MessageLog.i(TAG, "[ROSTER-TEST] ===== end =====")
    }
}
