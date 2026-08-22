package com.steve1316.uma_android_automation

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.ProtectionPopulation
import com.steve1316.uma_android_automation.bot.ProtectionScanOutcome
import com.steve1316.uma_android_automation.bot.VETERAN_PROTECTION_SCHEMA_VERSION
import com.steve1316.uma_android_automation.bot.VeteranProtectionScan
import com.steve1316.uma_android_automation.bot.entryFingerprint
import com.steve1316.uma_android_automation.bot.populationFromApply
import com.steve1316.uma_android_automation.bot.serializeVeteranProtectionScan
import com.steve1316.uma_android_automation.utils.ALL_FAVORITE_CHECKBOXES
import com.steve1316.uma_android_automation.utils.ApplyButtonState
import com.steve1316.uma_android_automation.utils.CHEVRON_NEXT_BOX
import com.steve1316.uma_android_automation.utils.ChevronState
import com.steve1316.uma_android_automation.utils.DIALOG_CANCEL_X
import com.steve1316.uma_android_automation.utils.DIALOG_CANCEL_Y
import com.steve1316.uma_android_automation.utils.DIALOG_OK_X
import com.steve1316.uma_android_automation.utils.DIALOG_OK_Y
import com.steve1316.uma_android_automation.utils.DIALOG_RESET_FILTERS_X
import com.steve1316.uma_android_automation.utils.DIALOG_RESET_FILTERS_Y
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_FILTER_TAB_X
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_TAB_Y
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_TITLE_H
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_TITLE_W
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_TITLE_X
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_TITLE_Y
import com.steve1316.uma_android_automation.utils.DETAIL_CLOSE_X
import com.steve1316.uma_android_automation.utils.DETAIL_CLOSE_Y
import com.steve1316.uma_android_automation.utils.DETAIL_NEXT_CHEVRON_X
import com.steve1316.uma_android_automation.utils.DETAIL_NEXT_CHEVRON_Y
import com.steve1316.uma_android_automation.utils.FAVORITE_ICON_CHECKBOXES
import com.steve1316.uma_android_automation.utils.FILTER_SCROLL_GUTTER_X
import com.steve1316.uma_android_automation.utils.FILTER_SCROLL_SWIPE_DURATION_MS
import com.steve1316.uma_android_automation.utils.FILTER_SCROLL_SWIPE_FROM_Y
import com.steve1316.uma_android_automation.utils.FILTER_SCROLL_SWIPE_TO_Y
import com.steve1316.uma_android_automation.utils.FILTER_SCROLL_TO_BOTTOM_SWIPES
import com.steve1316.uma_android_automation.utils.FilterCheckbox
import com.steve1316.uma_android_automation.utils.FilterCheckboxState
import com.steve1316.uma_android_automation.utils.MEMO_HAS_CHECKBOX
import com.steve1316.uma_android_automation.utils.MEMO_NO_CHECKBOX
import com.steve1316.uma_android_automation.utils.OPEN_DISPLAY_SETTINGS_X
import com.steve1316.uma_android_automation.utils.OPEN_DISPLAY_SETTINGS_Y
import com.steve1316.uma_android_automation.utils.OutcomeCorpus
import com.steve1316.uma_android_automation.utils.ROSTER_FIRST_CARD_X
import com.steve1316.uma_android_automation.utils.ROSTER_FIRST_CARD_Y
import com.steve1316.uma_android_automation.utils.RosterScreenKind
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.VeteranIdentityCatalog
import com.steve1316.uma_android_automation.utils.classifyApplyButton
import com.steve1316.uma_android_automation.utils.classifyChevron
import com.steve1316.uma_android_automation.utils.classifyFilterCheckbox
import com.steve1316.uma_android_automation.utils.isDisplaySettingsTitle

private const val TAG = "[VeteranProtectionScanner]"

/** Settle after a filter-dialog tap before the next read. The OK button and checkboxes update within
 * a frame, so this only covers capture latency. */
private const val TAP_SETTLE_SECONDS = 0.35

/** Settle after opening/closing the dialog or applying a filter, which cross-fades. */
private const val NAV_SETTLE_SECONDS = 1.0

/** How many verify-and-retap rounds to converge a partition onto its intended checkbox state. Each
 * round re-captures, so a stray or missed tap is corrected. Three is generous for an 18-box grid
 * that normally converges in one. */
private const val PARTITION_SET_ROUNDS = 3

/** Enumeration hard bound (only reached on the non-empty branch, never on this account). */
private const val ENUMERATE_HARD_BOUND_SLACK = 8
private const val CHEVRON_SETTLE_SECONDS = 0.6

/**
 * The read-only Veteran protection probe (PL-R2a).
 *
 * It answers two account-wide questions - "is any Veteran favorited?" and "does any Veteran have a
 * memo?" - because those are the only two markers that block a Veteran from being released, and the
 * game exposes no separate lock. It never favorites, memos, releases, or transfers anything.
 *
 * The clever, safe part: the game disables the Display Settings OK/Apply button when the current
 * (un-applied) filter selection would return zero rows. So the probe SETS a partition inside the
 * dialog and reads OK-enabled WITHOUT tapping OK. The applied filter is never changed and the roster
 * stays Filters: OFF; the probe leaves through Cancel. Enumeration - actually applying a partition and
 * walking it to identify WHICH Veterans are in it - runs only when a partition is non-empty, and on
 * an account with zero favorites and zero memos it never runs.
 *
 * Safety, in the order it is enforced:
 *  - Preconditions (roster list visible, Registered read, Filters: OFF) are checked BEFORE the first
 *    tap; a failure dispatches no gesture.
 *  - The Display Settings title is re-asserted before every mutation phase. A wrong frame aborts.
 *  - Every partition is verified by re-classifying its checkboxes after it is set; an unconvergeable
 *    partition aborts rather than reading a wrong OK state.
 *  - The exit always restores Filters: OFF (Cancel on the probe path; reopen/Reset/OK on the
 *    enumeration path) and re-reads the roster to prove it, recording RESTORE_FAILED if it cannot.
 */
class VeteranProtectionScanner(private val game: Game) {
    private val catalog = VeteranIdentityCatalog.loadFromAssets(game.myContext)
    private val reader = VeteranRosterReader(game.imageUtils, catalog)

    /** Aborts the probe with a specific outcome; caught by [runScan] which still records the result. */
    private class ProbeAbort(val outcome: ProtectionScanOutcome, message: String) : IllegalStateException(message)

    fun runScan() {
        val startedAt = System.currentTimeMillis()
        val scanId = "vp-$startedAt-${java.util.UUID.randomUUID().toString().substring(0, 8)}"
        MessageLog.i(TAG, "[PROTECTION-SCAN] ===== Veteran protection probe scanId=$scanId =====")

        val (listBitmap, listScreen) = reader.classifyScreenWithRetries()
        if (listScreen.kind != RosterScreenKind.ROSTER_LIST) {
            MessageLog.w(
                TAG,
                "[PROTECTION-SCAN] Precondition failed: expected the Veteran Roster list, saw ${listScreen.kind} " +
                    "(registered OCR='${listScreen.registeredRaw}' title OCR='${listScreen.titleRaw}'). No gesture dispatched.",
            )
            persistOutcome(scanId, startedAt, null, null, null, ProtectionScanOutcome.PRECONDITION_FAILED, listBitmap)
            return
        }
        val list = reader.readListState(listBitmap, listScreen, verbose = true)
        if (list.registeredUsed == null || list.filtersOff != true) {
            MessageLog.w(
                TAG,
                "[PROTECTION-SCAN] Precondition failed: registeredUsed=${list.registeredUsed ?: "UNREAD"} " +
                    "filtersOff=${list.filtersOff ?: "UNREAD"}. A probe under an unknown filter state is meaningless, so it stops. No gesture dispatched.",
            )
            persistOutcome(scanId, startedAt, list.registeredUsed, list.registeredCapacity, list.filtersOff, ProtectionScanOutcome.PRECONDITION_FAILED, listBitmap)
            return
        }
        MessageLog.i(TAG, "[PROTECTION-SCAN] Preconditions OK: used=${list.registeredUsed} capacity=${list.registeredCapacity ?: "UNREAD"} filtersOff=true")

        var favoriteApply = ApplyButtonState.UNKNOWN
        var memoApply = ApplyButtonState.UNKNOWN
        var favoritePop = ProtectionPopulation.UNKNOWN
        var memoPop = ProtectionPopulation.UNKNOWN
        var favoritedFps = emptyList<String>()
        var memoFps = emptyList<String>()
        var enumerationPerformed = false
        var outcome = ProtectionScanOutcome.COMPLETE
        var lastFrame = listBitmap

        try {
            openDialogToFilterBottom()

            // Baseline: a freshly opened Filters: OFF dialog must show every partition checkbox
            // unselected. If any reads selected, the frame is not what we think it is - abort before
            // touching anything.
            requireBaselineUnselected()

            favoriteApply = probePartition("favorite", FAVORITE_ICON_CHECKBOXES.toSet())
            favoritePop = populationFromApply(favoriteApply)
            resetFilters()

            memoApply = probePartition("memo", setOf(MEMO_HAS_CHECKBOX))
            memoPop = populationFromApply(memoApply)
            resetFilters()

            MessageLog.i(
                TAG,
                "[PROTECTION-SCAN] Populations: favorite=$favoritePop (OK $favoriteApply) memo=$memoPop (OK $memoApply)",
            )

            // Enumeration only for a non-empty partition. Dead on an account with zero favorites and
            // zero memos: both branches are skipped and the offline reader derives the whole-roster
            // complement from the trusted snapshot.
            if (favoritePop == ProtectionPopulation.NONEMPTY) {
                favoritedFps = enumeratePartition("favorite", FAVORITE_ICON_CHECKBOXES.toSet(), list.registeredCapacity ?: list.registeredUsed)
                enumerationPerformed = true
            }
            if (memoPop == ProtectionPopulation.NONEMPTY) {
                memoFps = enumeratePartition("memo", setOf(MEMO_HAS_CHECKBOX), list.registeredCapacity ?: list.registeredUsed)
                enumerationPerformed = true
            }

            // Clean exit: Cancel closes the dialog without applying, so the roster keeps Filters: OFF.
            cancelDialog()
        } catch (abort: ProbeAbort) {
            MessageLog.w(TAG, "[PROTECTION-SCAN] Aborted: ${abort.message} (outcome=${abort.outcome})")
            outcome = abort.outcome
            bestEffortDismissDialog()
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.e(TAG, "[PROTECTION-SCAN] Unexpected failure: $e")
            outcome = ProtectionScanOutcome.UI_UNEXPECTED
            bestEffortDismissDialog()
        }

        val (restored, afterBitmap) = verifyRosterFiltersOff(list.registeredUsed)
        lastFrame = afterBitmap ?: lastFrame
        if (outcome == ProtectionScanOutcome.COMPLETE && !restored) outcome = ProtectionScanOutcome.RESTORE_FAILED

        val record =
            VeteranProtectionScan(
                schemaVersion = VETERAN_PROTECTION_SCHEMA_VERSION,
                scanId = scanId,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                registeredUsed = list.registeredUsed,
                registeredCapacity = list.registeredCapacity,
                filtersOffConfirmed = list.filtersOff,
                favoritePopulation = favoritePop,
                favoriteApplyState = favoriteApply,
                memoPopulation = memoPop,
                memoApplyState = memoApply,
                enumerationPerformed = enumerationPerformed,
                favoritedFingerprints = favoritedFps,
                memoFingerprints = memoFps,
                restoredFiltersOff = restored,
                outcome = outcome,
                appVersion = BuildConfig.VERSION_NAME,
                screenWidth = lastFrame.width,
                screenHeight = lastFrame.height,
            )
        OutcomeCorpus.append(game.myContext, serializeVeteranProtectionScan(record), OutcomeCorpus.VETERAN_PROTECTION_PATH)
        MessageLog.i(
            TAG,
            "[PROTECTION-SCAN] scanId=$scanId outcome=$outcome favorite=$favoritePop memo=$memoPop " +
                "favoritedFps=${favoritedFps.size} memoFps=${memoFps.size} restoredFiltersOff=$restored " +
                "runtime=${(record.completedAt - record.startedAt) / 1000}s",
        )
        MessageLog.i(TAG, "[PROTECTION-SCAN] ===== end =====")
    }

    // -- Navigation ---------------------------------------------------------------------------------

    private fun openDialogToFilterBottom() {
        game.tapCoordinate(OPEN_DISPLAY_SETTINGS_X.toDouble(), OPEN_DISPLAY_SETTINGS_Y.toDouble(), "open_display_settings")
        game.wait(NAV_SETTLE_SECONDS)
        requireDialog("after opening Display Settings")
        game.tapCoordinate(DISPLAY_SETTINGS_FILTER_TAB_X.toDouble(), DISPLAY_SETTINGS_TAB_Y.toDouble(), "filter_tab")
        game.wait(TAP_SETTLE_SECONDS)
        scrollFilterListToBottom()
        requireDialog("after scrolling the Filter list to the bottom")
    }

    private fun scrollFilterListToBottom() {
        repeat(FILTER_SCROLL_TO_BOTTOM_SWIPES) {
            game.gestureUtils.swipe(
                FILTER_SCROLL_GUTTER_X.toFloat(),
                FILTER_SCROLL_SWIPE_FROM_Y.toFloat(),
                FILTER_SCROLL_GUTTER_X.toFloat(),
                FILTER_SCROLL_SWIPE_TO_Y.toFloat(),
                duration = FILTER_SCROLL_SWIPE_DURATION_MS,
            )
            game.wait(0.4)
        }
        game.wait(TAP_SETTLE_SECONDS)
    }

    /** Fail-closed screen assertion: the current frame must be the Display Settings dialog. */
    private fun requireDialog(context: String) {
        val bitmap = game.imageUtils.getSourceBitmap()
        val titleRaw = ocrTitle(bitmap)
        if (!isDisplaySettingsTitle(titleRaw)) {
            throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "not on the Display Settings dialog $context (title OCR='$titleRaw')")
        }
    }

    private fun ocrTitle(bitmap: Bitmap): String =
        try {
            game.imageUtils.performOCROnRegion(
                bitmap,
                DISPLAY_SETTINGS_TITLE_X,
                DISPLAY_SETTINGS_TITLE_Y,
                DISPLAY_SETTINGS_TITLE_W,
                DISPLAY_SETTINGS_TITLE_H,
                useThreshold = true,
                useGrayscale = true,
                scale = 2.0,
                ocrEngine = "tesseract",
                debugName = "protection_title",
            ).replace("\r", "").trim()
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            ""
        }

    // -- Probe --------------------------------------------------------------------------------------

    /** Sets the given partition, verifies it, and returns the OK-button reading without applying. */
    private fun probePartition(label: String, desiredSelected: Set<FilterCheckbox>): ApplyButtonState {
        requireDialog("before setting the $label partition")
        if (!setPartition(desiredSelected)) {
            throw ProbeAbort(ProtectionScanOutcome.PARTITION_SET_FAILED, "could not converge the $label partition onto its intended checkbox state")
        }
        game.wait(TAP_SETTLE_SECONDS)
        val bitmap = game.imageUtils.getSourceBitmap()
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
        val apply = classifyApplyButton(sampler)
        MessageLog.i(TAG, "[PROTECTION-SCAN] $label partition set; OK button classified $apply")
        if (apply == ApplyButtonState.UNKNOWN) {
            throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "the OK button read UNKNOWN for the $label partition (frame is not the dialog)")
        }
        return apply
    }

    /**
     * Taps checkboxes until every box in the grid matches its intended state, re-capturing between
     * rounds so a stray or missed tap self-corrects. Returns true when converged. A box in
     * [desiredSelected] must end SELECTED; every other favorite/memo box must end UNSELECTED.
     */
    private fun setPartition(desiredSelected: Set<FilterCheckbox>): Boolean {
        val allBoxes = ALL_FAVORITE_CHECKBOXES + listOf(MEMO_HAS_CHECKBOX, MEMO_NO_CHECKBOX)
        repeat(PARTITION_SET_ROUNDS) {
            val bitmap = game.imageUtils.getSourceBitmap()
            val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
            var allMatch = true
            for (box in allBoxes) {
                val want = if (box in desiredSelected) FilterCheckboxState.SELECTED else FilterCheckboxState.UNSELECTED
                if (classifyFilterCheckbox(sampler, box.cx, box.cy) != want) {
                    allMatch = false
                    game.tapCoordinate(box.cx.toDouble(), box.cy.toDouble(), box.label)
                    game.wait(0.15)
                }
            }
            if (allMatch) return true
            game.wait(0.3)
        }
        // Final verification on a fresh frame.
        val bitmap = game.imageUtils.getSourceBitmap()
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
        return allBoxes.all { box ->
            val want = if (box in desiredSelected) FilterCheckboxState.SELECTED else FilterCheckboxState.UNSELECTED
            classifyFilterCheckbox(sampler, box.cx, box.cy) == want
        }
    }

    /** Baseline check: a fresh Filters: OFF dialog has every partition checkbox unselected. */
    private fun requireBaselineUnselected() {
        val bitmap = game.imageUtils.getSourceBitmap()
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
        val allBoxes = ALL_FAVORITE_CHECKBOXES + listOf(MEMO_HAS_CHECKBOX, MEMO_NO_CHECKBOX)
        val selected = allBoxes.filter { classifyFilterCheckbox(sampler, it.cx, it.cy) == FilterCheckboxState.SELECTED }
        if (selected.isNotEmpty()) {
            throw ProbeAbort(
                ProtectionScanOutcome.UI_UNEXPECTED,
                "the freshly opened Filter dialog already shows selected checkboxes (${selected.joinToString { it.label }}); the scroll position or filter state is not what was expected",
            )
        }
    }

    private fun resetFilters() {
        requireDialog("before Reset Filters")
        game.tapCoordinate(DIALOG_RESET_FILTERS_X.toDouble(), DIALOG_RESET_FILTERS_Y.toDouble(), "reset_filters")
        game.wait(TAP_SETTLE_SECONDS)
        requireBaselineUnselected()
    }

    private fun cancelDialog() {
        requireDialog("before Cancel")
        game.tapCoordinate(DIALOG_CANCEL_X.toDouble(), DIALOG_CANCEL_Y.toDouble(), "cancel_display_settings")
        game.wait(NAV_SETTLE_SECONDS)
    }

    // -- Enumeration (non-empty partitions only; not exercised on a zero-favorite account) ----------

    /**
     * Applies the partition, walks the filtered subset collecting fingerprints, then restores
     * Filters: OFF. The walk reuses the same chevron primitives the roster scan is built on. Restore
     * runs in a finally so an applied filter is never left behind.
     */
    private fun enumeratePartition(label: String, desiredSelected: Set<FilterCheckbox>, hardBoundBase: Int?): List<String> {
        MessageLog.i(TAG, "[PROTECTION-SCAN] $label partition is non-empty; enumerating the filtered subset.")
        requireDialog("before applying the $label partition for enumeration")
        if (!setPartition(desiredSelected)) {
            throw ProbeAbort(ProtectionScanOutcome.PARTITION_SET_FAILED, "could not set the $label partition for enumeration")
        }
        val fingerprints: List<String>
        try {
            game.tapCoordinate(DIALOG_OK_X.toDouble(), DIALOG_OK_Y.toDouble(), "apply_${label}_filter")
            game.wait(NAV_SETTLE_SECONDS)
            fingerprints = walkFilteredFingerprints(hardBoundBase)
        } finally {
            restoreFiltersOffFromRoster()
        }
        MessageLog.i(TAG, "[PROTECTION-SCAN] $label enumeration collected ${fingerprints.size} fingerprints.")
        return fingerprints
    }

    /** Walks the currently-filtered roster with the detail chevron, collecting each resolved
     * fingerprint. Unidentified entries are skipped (logged), never guessed. */
    private fun walkFilteredFingerprints(hardBoundBase: Int?): List<String> {
        val fingerprints = mutableListOf<String>()
        val (_, screen) = reader.classifyScreenWithRetries(attempts = 3)
        if (screen.kind != RosterScreenKind.ROSTER_LIST) {
            throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "applying the filter did not return to the roster list (saw ${screen.kind})")
        }
        game.tapCoordinate(ROSTER_FIRST_CARD_X.toDouble(), ROSTER_FIRST_CARD_Y.toDouble(), "filtered_first_card")
        game.wait(CHEVRON_SETTLE_SECONDS)
        var (bitmap, detail) = reader.classifyScreenWithRetries(attempts = 3)
        if (detail.kind != RosterScreenKind.UMAMUSUME_DETAILS) {
            throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "opening the first filtered card did not produce the Details dialog (saw ${detail.kind})")
        }
        val hardBound = (hardBoundBase ?: 260) + ENUMERATE_HARD_BOUND_SLACK
        var seen = 0
        entryFingerprint(reader.readDetailObservation(bitmap, includeCareerInfo = false, verbose = false))?.let { fingerprints.add(it) }
        seen++
        while (seen < hardBound) {
            val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
            if (classifyChevron(sampler, CHEVRON_NEXT_BOX) == ChevronState.DISABLED) break
            game.tapCoordinate(DETAIL_NEXT_CHEVRON_X.toDouble(), DETAIL_NEXT_CHEVRON_Y.toDouble(), "filtered_next_chevron")
            game.wait(CHEVRON_SETTLE_SECONDS)
            val next = reader.classifyScreenWithRetries(attempts = 3)
            bitmap = next.first
            if (next.second.kind != RosterScreenKind.UMAMUSUME_DETAILS) break
            val fp = entryFingerprint(reader.readDetailObservation(bitmap, includeCareerInfo = false, verbose = false))
            if (fp != null) {
                if (fp == fingerprints.firstOrNull() && seen >= 2) break // wrapped
                fingerprints.add(fp)
            }
            seen++
        }
        // Close the detail dialog back to the (still filtered) roster list.
        game.tapCoordinate(DETAIL_CLOSE_X.toDouble(), DETAIL_CLOSE_Y.toDouble(), "filtered_detail_close")
        game.wait(CHEVRON_SETTLE_SECONDS)
        return fingerprints.distinct()
    }

    /** Reopens Display Settings from the roster list and clears the filter back to OFF. */
    private fun restoreFiltersOffFromRoster() {
        try {
            val (_, screen) = reader.classifyScreenWithRetries(attempts = 3)
            if (screen.kind != RosterScreenKind.ROSTER_LIST) {
                game.tapCoordinate(DETAIL_CLOSE_X.toDouble(), DETAIL_CLOSE_Y.toDouble(), "restore_close_detail")
                game.wait(CHEVRON_SETTLE_SECONDS)
            }
            game.tapCoordinate(OPEN_DISPLAY_SETTINGS_X.toDouble(), OPEN_DISPLAY_SETTINGS_Y.toDouble(), "restore_open_display_settings")
            game.wait(NAV_SETTLE_SECONDS)
            requireDialog("while restoring Filters OFF")
            game.tapCoordinate(DISPLAY_SETTINGS_FILTER_TAB_X.toDouble(), DISPLAY_SETTINGS_TAB_Y.toDouble(), "restore_filter_tab")
            game.wait(TAP_SETTLE_SECONDS)
            game.tapCoordinate(DIALOG_RESET_FILTERS_X.toDouble(), DIALOG_RESET_FILTERS_Y.toDouble(), "restore_reset_filters")
            game.wait(TAP_SETTLE_SECONDS)
            game.tapCoordinate(DIALOG_OK_X.toDouble(), DIALOG_OK_Y.toDouble(), "restore_apply_off")
            game.wait(NAV_SETTLE_SECONDS)
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.w(TAG, "[PROTECTION-SCAN] Restore path failed: $e. Clear the filter by hand.")
        }
    }

    // -- Exit / verification ------------------------------------------------------------------------

    /** Best-effort close of whatever dialog/detail is up, for the abort path. */
    private fun bestEffortDismissDialog() {
        try {
            val bitmap = game.imageUtils.getSourceBitmap()
            if (isDisplaySettingsTitle(ocrTitle(bitmap))) {
                game.tapCoordinate(DIALOG_CANCEL_X.toDouble(), DIALOG_CANCEL_Y.toDouble(), "abort_cancel")
                game.wait(NAV_SETTLE_SECONDS)
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.w(TAG, "[PROTECTION-SCAN] Abort dismiss failed: $e")
        }
    }

    /** Re-reads the roster list and confirms Filters: OFF with the same Registered count. */
    private fun verifyRosterFiltersOff(expectedUsed: Int?): Pair<Boolean, Bitmap?> {
        return try {
            val (bitmap, screen) = reader.classifyScreenWithRetries(attempts = 4)
            if (screen.kind != RosterScreenKind.ROSTER_LIST) {
                MessageLog.w(TAG, "[PROTECTION-SCAN] Could not re-read the roster list after the probe (saw ${screen.kind}). Confirm the filter by hand.")
                return false to bitmap
            }
            val after = reader.readListState(bitmap, screen, verbose = false)
            val ok = after.filtersOff == true && (expectedUsed == null || after.registeredUsed == expectedUsed)
            MessageLog.i(
                TAG,
                "[PROTECTION-SCAN] Post-probe roster state: Registered ${after.registeredUsed ?: "?"}/${after.registeredCapacity ?: "?"} " +
                    "filtersOff=${after.filtersOff ?: "UNREAD"} restored=$ok",
            )
            ok to bitmap
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.w(TAG, "[PROTECTION-SCAN] Post-probe verification failed: $e")
            false to null
        }
    }

    // -- Persistence for the pre-tap failure paths --------------------------------------------------

    private fun persistOutcome(
        scanId: String,
        startedAt: Long,
        registeredUsed: Int?,
        registeredCapacity: Int?,
        filtersOff: Boolean?,
        outcome: ProtectionScanOutcome,
        frame: Bitmap,
    ) {
        val record =
            VeteranProtectionScan(
                schemaVersion = VETERAN_PROTECTION_SCHEMA_VERSION,
                scanId = scanId,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                registeredUsed = registeredUsed,
                registeredCapacity = registeredCapacity,
                filtersOffConfirmed = filtersOff,
                favoritePopulation = ProtectionPopulation.UNKNOWN,
                favoriteApplyState = ApplyButtonState.UNKNOWN,
                memoPopulation = ProtectionPopulation.UNKNOWN,
                memoApplyState = ApplyButtonState.UNKNOWN,
                enumerationPerformed = false,
                favoritedFingerprints = emptyList(),
                memoFingerprints = emptyList(),
                restoredFiltersOff = true, // no filter was ever applied on a pre-tap failure
                outcome = outcome,
                appVersion = BuildConfig.VERSION_NAME,
                screenWidth = frame.width,
                screenHeight = frame.height,
            )
        OutcomeCorpus.append(game.myContext, serializeVeteranProtectionScan(record), OutcomeCorpus.VETERAN_PROTECTION_PATH)
        MessageLog.i(TAG, "[PROTECTION-SCAN] scanId=$scanId outcome=$outcome (no gesture path). ===== end =====")
    }
}
