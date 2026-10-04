package com.steve1316.uma_android_automation

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.OwnUiForeground
import com.steve1316.uma_android_automation.bot.ProtectionPopulation
import com.steve1316.uma_android_automation.bot.ProtectionScanOutcome
import com.steve1316.uma_android_automation.bot.RosterListState
import com.steve1316.uma_android_automation.bot.VETERAN_PROTECTION_SCHEMA_VERSION
import com.steve1316.uma_android_automation.bot.VeteranProtectionScan
import com.steve1316.uma_android_automation.bot.populationFromProvenFilters
import com.steve1316.uma_android_automation.bot.rosterBindingDigest
import com.steve1316.uma_android_automation.bot.serializeVeteranProtectionScan
import com.steve1316.uma_android_automation.utils.ALL_FAVORITE_CHECKBOXES
import com.steve1316.uma_android_automation.utils.ApplyButtonState
import com.steve1316.uma_android_automation.utils.DIALOG_CANCEL_X
import com.steve1316.uma_android_automation.utils.DIALOG_CANCEL_Y
import com.steve1316.uma_android_automation.utils.DIALOG_RESET_FILTERS_X
import com.steve1316.uma_android_automation.utils.DIALOG_RESET_FILTERS_Y
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_FILTER_TAB_X
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_TAB_Y
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_TITLE_H
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_TITLE_W
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_TITLE_X
import com.steve1316.uma_android_automation.utils.DISPLAY_SETTINGS_TITLE_Y
import com.steve1316.uma_android_automation.utils.FAVORITE_ICON_CHECKBOXES
import com.steve1316.uma_android_automation.utils.FAVORITE_NOT_SET_CHECKBOX
import com.steve1316.uma_android_automation.utils.FilterBaselineState
import com.steve1316.uma_android_automation.utils.FilterControlState
import com.steve1316.uma_android_automation.utils.FILTER_SCROLL_GUTTER_X
import com.steve1316.uma_android_automation.utils.FILTER_SCROLL_SWIPE_DURATION_MS
import com.steve1316.uma_android_automation.utils.FILTER_SCROLL_SWIPE_FROM_Y
import com.steve1316.uma_android_automation.utils.FILTER_SCROLL_SWIPE_TO_Y
import com.steve1316.uma_android_automation.utils.FILTER_SCROLL_TO_BOTTOM_SWIPES
import com.steve1316.uma_android_automation.utils.FilterCheckbox
import com.steve1316.uma_android_automation.utils.MEMO_HAS_CHECKBOX
import com.steve1316.uma_android_automation.utils.MEMO_NO_CHECKBOX
import com.steve1316.uma_android_automation.utils.OPEN_DISPLAY_SETTINGS_X
import com.steve1316.uma_android_automation.utils.OPEN_DISPLAY_SETTINGS_Y
import com.steve1316.uma_android_automation.utils.OutcomeCorpus
import com.steve1316.uma_android_automation.utils.RosterScreenKind
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.VeteranFilterDimension
import com.steve1316.uma_android_automation.utils.VeteranFilterDimensionRead
import com.steve1316.uma_android_automation.utils.VeteranFilterFrame
import com.steve1316.uma_android_automation.utils.VeteranFilterFrameRead
import com.steve1316.uma_android_automation.utils.VeteranFilterViewportAggregation
import com.steve1316.uma_android_automation.utils.VeteranIdentityCatalog
import com.steve1316.uma_android_automation.utils.classifyApplyButton
import com.steve1316.uma_android_automation.utils.classifyFilterCheckbox
import com.steve1316.uma_android_automation.utils.exactFilterTargetState
import com.steve1316.uma_android_automation.utils.filterBaselineState
import com.steve1316.uma_android_automation.utils.filterDialogChromeRecognized
import com.steve1316.uma_android_automation.utils.isDisplaySettingsTitle
import com.steve1316.uma_android_automation.utils.readVeteranFilterFrame
import com.steve1316.uma_android_automation.utils.rosterListBindingStable

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

internal enum class ProtectionFilterProbeStage {
    FAVORITE_TARGET,
    FAVORITE_POSITIVE_CONTROL,
    MEMO_TARGET,
    MEMO_POSITIVE_CONTROL,
}

internal class ProtectionFilterBaselineProof private constructor(
    private val stage: ProtectionFilterProbeStage,
    private val readings: Map<VeteranFilterDimension, VeteranFilterDimensionRead>,
) {
    private var fresh = true

    fun <T> beforeMutation(expectedStage: ProtectionFilterProbeStage, block: (Map<VeteranFilterDimension, VeteranFilterDimensionRead>) -> T): T {
        check(stage == expectedStage) { "baseline for $stage cannot prove $expectedStage" }
        check(fresh) { "baseline for $stage was already consumed by a UI mutation" }
        fresh = false
        return block(readings)
    }

    companion object {
        fun acquire(
            stage: ProtectionFilterProbeStage,
            readBaseline: () -> Map<VeteranFilterDimension, VeteranFilterDimensionRead>,
        ): ProtectionFilterBaselineProof = ProtectionFilterBaselineProof(stage, readBaseline())
    }
}

internal fun runProtectionFilterProbeSequence(
    resetAndRead: () -> Map<VeteranFilterDimension, VeteranFilterDimensionRead>,
    probe: (ProtectionFilterProbeStage, Map<VeteranFilterDimension, VeteranFilterDimensionRead>) -> ApplyButtonState,
) {
    fun run(stage: ProtectionFilterProbeStage): ApplyButtonState {
        val proof = ProtectionFilterBaselineProof.acquire(stage) { resetAndRead() }
        return proof.beforeMutation(stage) { readings -> probe(stage, readings) }
    }

    val favorite = run(ProtectionFilterProbeStage.FAVORITE_TARGET)
    if (favorite == ApplyButtonState.DISABLED) run(ProtectionFilterProbeStage.FAVORITE_POSITIVE_CONTROL)
    val memo = run(ProtectionFilterProbeStage.MEMO_TARGET)
    if (memo == ApplyButtonState.DISABLED) run(ProtectionFilterProbeStage.MEMO_POSITIVE_CONTROL)
}

internal fun finalVeteranFilterReadings(
    aggregation: VeteranFilterViewportAggregation,
    finalFrame: VeteranFilterFrameRead,
): Map<VeteranFilterDimension, VeteranFilterDimensionRead> {
    check(finalFrame.dimensions.keys.all(aggregation.readings::containsKey)) { "final filter viewport was not accumulated" }
    return aggregation.readings
}

internal data class FilterPass<T>(
    val readings: Map<VeteranFilterDimension, VeteranFilterDimensionRead>,
    val errors: List<String>,
    val bottomFrames: Pair<T, T>,
) {
    val baseline: FilterBaselineState get() = filterBaselineState(readings.mapValues { it.value.state }, errors.isNotEmpty())
}

/** Every scroll keeps at least one section in view; only complete sections contribute readings. */
internal fun <T> scanProtectionFilterDialog(
    requireDialog: (String) -> Unit,
    scrollFilterListToTop: () -> Unit,
    stableFilterFrame: () -> Triple<T, T, VeteranFilterFrameRead>,
    scrollFilterListDown: () -> Unit,
): FilterPass<T> {
    requireDialog("before full filter traversal")
    scrollFilterListToTop()
    val aggregation = VeteranFilterViewportAggregation()
    val transitions = mutableSetOf<Pair<VeteranFilterDimension, VeteranFilterDimension>>()
    val commonRows = mutableSetOf<Int>()
    var previous = emptySet<VeteranFilterDimension>()
    repeat(8) { step ->
        val (first, second, frame) = stableFilterFrame()
        val visible = frame.anchors.map { it.dimension }.toSet()
        frame.anchors.zipWithNext().forEach { (a, b) -> transitions += a.dimension to b.dimension }
        frame.anchors.filter { it.dimension == VeteranFilterDimension.COMMON_SPARKS }.forEach { commonRows += it.commonRow }
        if (step == 0 && frame.anchors.firstOrNull()?.let { it.dimension == VeteranFilterDimension.TRACK && it.y in 325..342 } != true) {
            aggregation.diagnostic("absolute top anchor missing")
        }
        if (step > 0 && previous.intersect(visible).isEmpty()) aggregation.diagnostic("scroll section overlap missing at step $step")
        aggregation.add(frame)
        val common = frame.anchors.find { it.dimension == VeteranFilterDimension.COMMON_SPARKS }
        val favorites = frame.anchors.find { it.dimension == VeteranFilterDimension.FAVORITES }
        val memo = frame.anchors.find { it.dimension == VeteranFilterDimension.MEMO }
        if (common?.y in 376..393 && favorites?.y in 626..643 && memo?.y in 1403..1422) {
            VeteranFilterDimension.entries.zipWithNext().forEach { pair ->
                if (pair !in transitions) aggregation.diagnostic("section transition ${pair.first} to ${pair.second} unseen")
            }
            if (commonRows.any { it > 1 }) {
                val missingRows = (1..commonRows.max()).filter { it !in commonRows }
                if (missingRows.isNotEmpty()) aggregation.diagnostic("Common Spark row census incomplete: $missingRows")
                aggregation.diagnostic("populated Common Spark rows present")
            }
            return FilterPass(finalVeteranFilterReadings(aggregation, frame), aggregation.diagnostics, first to second)
        }
        previous = visible
        if (step < 7) {
            requireDialog("before advancing filter traversal")
            scrollFilterListDown()
        }
    }
    throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "absolute bottom anchor not reached within bounded traversal")
}

internal class ProbeAbort(val outcome: ProtectionScanOutcome, message: String) : IllegalStateException(message)

internal fun protectionTargetApplyState(
    label: String,
    readings: Map<VeteranFilterDimension, VeteranFilterDimensionRead>,
    target: VeteranFilterDimension,
    errors: List<String>,
    exactBottomSelection: () -> Boolean,
    readApplyStates: () -> List<ApplyButtonState>,
): ApplyButtonState {
    val expected = exactFilterTargetState(readings.mapValues { it.value.state }, target, errors.isNotEmpty())
    if (!expected || !exactBottomSelection()) {
        throw ProbeAbort(ProtectionScanOutcome.PARTITION_SET_FAILED, "$label exact target or unrelated filter proof failed: $errors $readings")
    }
    val states = readApplyStates()
    return if (states.size == 2 && states[0] == states[1]) states[0] else ApplyButtonState.UNKNOWN
}

internal fun protectionRosterRestored(before: RosterListState, after: RosterListState?): Boolean =
    rosterListBindingStable(before, after) && before.sortKey != null && before.sortDirection != null &&
        before.sortKey == after?.sortKey && before.sortDirection == after?.sortDirection

internal fun restoreProtectionScan(scan: VeteranProtectionScan, restored: Boolean): VeteranProtectionScan =
    if (restored) {
        scan.copy(restoredFiltersOff = true)
    } else {
        scan.copy(
            favoritePopulation = ProtectionPopulation.UNKNOWN,
            memoPopulation = ProtectionPopulation.UNKNOWN,
            favoriteBaselineVerified = false,
            memoBaselineVerified = false,
            restoredFiltersOff = false,
            outcome = ProtectionScanOutcome.RESTORE_FAILED,
        )
    }

/**
 * The read-only Veteran protection probe (PL-R2a).
 *
 * It answers two account-wide questions - "is any Veteran favorited?" and "does any Veteran have a
 * memo?" - because those are the only two markers that block a Veteran from being released, and the
 * game exposes no separate lock. It never favorites, memos, releases, or transfers anything.
 *
 * The game disables the Display Settings OK/Apply button when the exact un-applied selection would
 * return zero rows. The probe verifies each selection and an enabled complementary control, then
 * leaves through Cancel. A nonempty partition has no independent membership census and remains
 * non-complete; no filtered member walk or protection array is published.
 *
 * Safety, in the order it is enforced:
 *  - Preconditions (roster list visible, Registered read, Filters: OFF) are checked BEFORE the first
 *    tap; a failure dispatches no gesture.
 *  - The Display Settings title is re-asserted before every mutation phase. A wrong frame aborts.
 *  - Every partition is verified by re-classifying its checkboxes after it is set; an unconvergeable
 *    partition aborts rather than reading a wrong OK state.
 *  - The exit dismisses the dialog in finally and re-reads Filters: OFF and the Registered count,
 *    recording RESTORE_FAILED if it cannot prove restoration.
 */
class VeteranProtectionScanner(private val game: Game) {
    private val catalog = VeteranIdentityCatalog.loadFromAssets(game.myContext)
    private val reader = VeteranRosterReader(game.imageUtils, catalog)

    fun runScan() {
        val startedAt = System.currentTimeMillis()
        val scanId = "vp-$startedAt-${java.util.UUID.randomUUID().toString().substring(0, 8)}"
        MessageLog.i(TAG, "[PROTECTION-SCAN] ===== Veteran protection probe scanId=$scanId =====")

        val roster = try {
            VeteranRosterScanner(game).runScan(0)
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.e(TAG, "[PROTECTION-SCAN] Roster walk failed: $e")
            persistOutcome(scanId, startedAt, null, null, null, ProtectionScanOutcome.UI_UNEXPECTED, game.imageUtils.getSourceBitmap())
            return
        }
        val digest = rosterBindingDigest(roster)
        if (digest == null) {
            val list = roster.header.list
            persistOutcome(scanId, startedAt, list.registeredUsed, list.registeredCapacity, list.filtersOff, ProtectionScanOutcome.PRECONDITION_FAILED, game.imageUtils.getSourceBitmap(), roster.header.scanId)
            return
        }

        val (listBitmap, listScreen) = reader.classifyScreenWithRetries()
        if (listScreen.kind != RosterScreenKind.ROSTER_LIST) {
            MessageLog.w(
                TAG,
                "[PROTECTION-SCAN] Precondition failed: expected the Veteran Roster list, saw ${listScreen.kind} " +
                    "(registered OCR='${listScreen.registeredRaw}' title OCR='${listScreen.titleRaw}'). No protection filter gesture dispatched.",
            )
            persistOutcome(scanId, startedAt, null, null, null, ProtectionScanOutcome.PRECONDITION_FAILED, listBitmap)
            return
        }
        val list = reader.readListState(listBitmap, listScreen, verbose = true)
        if (!rosterListBindingStable(roster.header.list, list) || list.sortKey == null || list.sortDirection == null) {
            MessageLog.w(
                TAG,
                "[PROTECTION-SCAN] Precondition failed: registeredUsed=${list.registeredUsed ?: "UNREAD"} " +
                    "filtersOff=${list.filtersOff ?: "UNREAD"}. A probe under an unknown filter state is meaningless, so it stops before protection filter gestures.",
            )
            persistOutcome(scanId, startedAt, list.registeredUsed, list.registeredCapacity, list.filtersOff, ProtectionScanOutcome.PRECONDITION_FAILED, listBitmap)
            return
        }
        MessageLog.i(TAG, "[PROTECTION-SCAN] Preconditions OK: used=${list.registeredUsed} capacity=${list.registeredCapacity ?: "UNREAD"} filtersOff=true")

        var favoriteApply = ApplyButtonState.UNKNOWN
        var memoApply = ApplyButtonState.UNKNOWN
        var favoritePop = ProtectionPopulation.UNKNOWN
        var memoPop = ProtectionPopulation.UNKNOWN
        var favoriteBaselineVerified = false
        var memoBaselineVerified = false
        var favoriteBaselineReadings: Map<VeteranFilterDimension, VeteranFilterDimensionRead> = emptyMap()
        var memoBaselineReadings: Map<VeteranFilterDimension, VeteranFilterDimensionRead> = emptyMap()
        val probeDiagnostics = mutableListOf<String>()
        var outcome = ProtectionScanOutcome.COMPLETE
        var lastFrame = listBitmap
        var dialogMayBeOpen = true
        var restored = false

        try {
            openDialogToFilter()

            runProtectionFilterProbeSequence(
                resetAndRead = ::resetFilters,
                probe = { stage, baselineReadings ->
                    when (stage) {
                        ProtectionFilterProbeStage.FAVORITE_TARGET -> {
                            probeDiagnostics += "favorite full neutral baseline verified"
                            favoriteBaselineReadings = baselineReadings
                            favoriteApply = probePartition("favorite", FAVORITE_ICON_CHECKBOXES.toSet(), VeteranFilterDimension.FAVORITES, probeDiagnostics)
                            favoriteBaselineVerified = true
                            favoritePop = populationFromProvenFilters(favoriteApply)
                            favoriteApply
                        }
                        ProtectionFilterProbeStage.FAVORITE_POSITIVE_CONTROL -> {
                            probeDiagnostics += "favorite target OK disabled; checking Not Set control"
                            val complementary = probePartition("favorite not set", setOf(FAVORITE_NOT_SET_CHECKBOX), VeteranFilterDimension.FAVORITES, probeDiagnostics)
                            if (complementary != ApplyButtonState.ENABLED) {
                                throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "favorite empty probe failed its positive control")
                            }
                            favoritePop = populationFromProvenFilters(favoriteApply, complementary)
                            complementary
                        }
                        ProtectionFilterProbeStage.MEMO_TARGET -> {
                            probeDiagnostics += "memo full neutral baseline verified"
                            memoBaselineReadings = baselineReadings
                            memoApply = probePartition("memo", setOf(MEMO_HAS_CHECKBOX), VeteranFilterDimension.MEMO, probeDiagnostics)
                            memoBaselineVerified = true
                            memoPop = populationFromProvenFilters(memoApply)
                            memoApply
                        }
                        ProtectionFilterProbeStage.MEMO_POSITIVE_CONTROL -> {
                            probeDiagnostics += "memo target OK disabled; checking No Memo control"
                            val complementary = probePartition("memo not set", setOf(MEMO_NO_CHECKBOX), VeteranFilterDimension.MEMO, probeDiagnostics)
                            if (complementary != ApplyButtonState.ENABLED) {
                                throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "memo empty probe failed its positive control")
                            }
                            memoPop = populationFromProvenFilters(memoApply, complementary)
                            complementary
                        }
                    }
                },
            )

            resetFilters()

            MessageLog.i(
                TAG,
                "[PROTECTION-SCAN] Populations: favorite=$favoritePop (OK $favoriteApply) memo=$memoPop (OK $memoApply)",
            )

            if (favoritePop == ProtectionPopulation.NONEMPTY || memoPop == ProtectionPopulation.NONEMPTY) {
                outcome = ProtectionScanOutcome.NONEMPTY_PARTITION_CENSUS_UNAVAILABLE
            } else if (favoritePop != ProtectionPopulation.EMPTY || memoPop != ProtectionPopulation.EMPTY) {
                outcome = ProtectionScanOutcome.UI_UNEXPECTED
            }
            cancelDialog()
            dialogMayBeOpen = false
        } catch (abort: ProbeAbort) {
            MessageLog.w(TAG, "[PROTECTION-SCAN] Aborted: ${abort.message} (outcome=${abort.outcome})")
            probeDiagnostics += abort.message ?: "probe aborted"
            outcome = abort.outcome
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.e(TAG, "[PROTECTION-SCAN] Unexpected failure: $e")
            probeDiagnostics += "unexpected ${e.javaClass.simpleName}"
            outcome = ProtectionScanOutcome.UI_UNEXPECTED
        } finally {
            if (dialogMayBeOpen) bestEffortDismissDialog()
            val (verified, afterBitmap) = verifyRosterFiltersOff(list)
            restored = verified
            lastFrame = afterBitmap ?: lastFrame
        }

        probeDiagnostics += if (restored) "roster filters, count and sort restored" else "roster restoration unverified"
        val record = restoreProtectionScan(
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
                favoriteBaselineVerified = favoriteBaselineVerified,
                favoriteBaselineReadings = favoriteBaselineReadings.mapKeys { it.key.name.lowercase() }.mapValues { it.value.state.name.lowercase() },
                memoPopulation = memoPop,
                memoApplyState = memoApply,
                memoBaselineVerified = memoBaselineVerified,
                memoBaselineReadings = memoBaselineReadings.mapKeys { it.key.name.lowercase() }.mapValues { it.value.state.name.lowercase() },
                filterBaselineEvidenceVersion = 1,
                probeDiagnostics = probeDiagnostics + favoriteBaselineReadings.values.flatMap { it.reasons } + memoBaselineReadings.values.flatMap { it.reasons },
                enumerationPerformed = false,
                favoritedFingerprints = emptyList(),
                memoFingerprints = emptyList(),
                restoredFiltersOff = restored,
                outcome = outcome,
                appVersion = BuildConfig.VERSION_NAME,
                screenWidth = lastFrame.width,
                screenHeight = lastFrame.height,
                rosterBindingVersion = 1,
                rosterScanId = roster.header.scanId,
                rosterDigest = digest,
            ),
            restored,
        )
        OutcomeCorpus.append(game.myContext, serializeVeteranProtectionScan(record), OutcomeCorpus.VETERAN_PROTECTION_PATH)
        MessageLog.i(
            TAG,
            "[PROTECTION-SCAN] scanId=$scanId outcome=${record.outcome} favorite=${record.favoritePopulation} memo=${record.memoPopulation} " +
                "favoritedFps=0 memoFps=0 restoredFiltersOff=$restored " +
                "runtime=${(record.completedAt - record.startedAt) / 1000}s",
        )
        MessageLog.i(TAG, "[PROTECTION-SCAN] ===== end =====")
    }

    // -- Navigation ---------------------------------------------------------------------------------

    private fun openDialogToFilter() {
        game.tapCoordinate(OPEN_DISPLAY_SETTINGS_X.toDouble(), OPEN_DISPLAY_SETTINGS_Y.toDouble(), "open_display_settings")
        game.wait(NAV_SETTLE_SECONDS)
        if (!isDisplaySettingsTitle(ocrTitle(game.imageUtils.getSourceBitmap()))) {
            throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "Display Settings title unrecognized after opening")
        }
        game.tapCoordinate(DISPLAY_SETTINGS_FILTER_TAB_X.toDouble(), DISPLAY_SETTINGS_TAB_Y.toDouble(), "filter_tab")
        game.wait(TAP_SETTLE_SECONDS)
        requireDialog("after selecting Filter")
    }

    private fun scrollFilterListToTop() {
        repeat(FILTER_SCROLL_TO_BOTTOM_SWIPES) {
            OwnUiForeground.waitForGame()
            game.gestureUtils.swipe(
                FILTER_SCROLL_GUTTER_X.toFloat(),
                FILTER_SCROLL_SWIPE_TO_Y.toFloat(),
                FILTER_SCROLL_GUTTER_X.toFloat(),
                FILTER_SCROLL_SWIPE_FROM_Y.toFloat(),
                duration = FILTER_SCROLL_SWIPE_DURATION_MS,
            )
            game.wait(0.4)
        }
        game.wait(TAP_SETTLE_SECONDS)
    }

    private fun scrollFilterListDown() {
        OwnUiForeground.waitForGame()
        game.gestureUtils.swipe(
            FILTER_SCROLL_GUTTER_X.toFloat(), FILTER_SCROLL_SWIPE_FROM_Y.toFloat(),
            FILTER_SCROLL_GUTTER_X.toFloat(), FILTER_SCROLL_SWIPE_TO_Y.toFloat(),
            duration = FILTER_SCROLL_SWIPE_DURATION_MS,
        )
        game.wait(0.4)
    }

    private fun requireDialog(context: String) {
        val bitmap = game.imageUtils.getSourceBitmap()
        if (!filterDialogChromeRecognized(filterFrame(bitmap))) {
            throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "Filter dialog geometry or fixed controls unrecognized $context")
        }
    }

    private fun filterFrame(bitmap: Bitmap): VeteranFilterFrame = VeteranFilterFrame(
        bitmap.width, bitmap.height, SparkPixelSampler { x, y -> bitmap.getPixel(x, y) },
    ) { x, y, width, height ->
        try {
            game.imageUtils.performOCROnRegion(
                bitmap, x, y, width, height,
                useThreshold = true, useGrayscale = true, scale = 2.0,
                ocrEngine = "tesseract", debugName = "protection_filter",
            ).replace("\r", "").trim()
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            ""
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

    private fun stableFilterFrame(): Triple<Bitmap, Bitmap, VeteranFilterFrameRead> {
        game.wait(TAP_SETTLE_SECONDS)
        val first = game.imageUtils.getSourceBitmap()
        game.wait(TAP_SETTLE_SECONDS)
        val second = game.imageUtils.getSourceBitmap()
        val a = readVeteranFilterFrame(filterFrame(first))
        val b = readVeteranFilterFrame(filterFrame(second))
        if (a != b) throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "filter anchors or controls changed between settled captures")
        return Triple(first, second, b)
    }

    private fun scanFilterDialog(): FilterPass<Bitmap> =
        scanProtectionFilterDialog(::requireDialog, ::scrollFilterListToTop, ::stableFilterFrame, ::scrollFilterListDown)

    /** Sets the given partition, proves all other filters neutral, then reads OK without applying. */
    private fun probePartition(label: String, desiredSelected: Set<FilterCheckbox>, target: VeteranFilterDimension, diagnostics: MutableList<String>): ApplyButtonState {
        requireDialog("before setting the $label partition")
        if (!setPartition(desiredSelected)) {
            throw ProbeAbort(ProtectionScanOutcome.PARTITION_SET_FAILED, "could not converge the $label partition onto its intended checkbox state")
        }
        val pass = scanFilterDialog()
        val apply = protectionTargetApplyState(
            label, pass.readings, target, pass.errors,
            exactBottomSelection = { pass.bottomFrames.toList().all { exactBottomSelection(it, desiredSelected) } },
            readApplyStates = { pass.bottomFrames.toList().map { frame -> classifyApplyButton(SparkPixelSampler { x, y -> frame.getPixel(x, y) }) } },
        )
        MessageLog.i(TAG, "[PROTECTION-SCAN] $label partition set; OK button classified $apply")
        if (apply == ApplyButtonState.UNKNOWN) {
            throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "the OK button read UNKNOWN for the $label partition (frame is not the dialog)")
        }
        diagnostics += "$label exact target and unrelated filters verified; OK=$apply"
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
            val frame = readVeteranFilterFrame(filterFrame(bitmap))
            if (frame.errors.isNotEmpty() || frame.dimensions[VeteranFilterDimension.MEMO] == null) return false
            val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
            var allMatch = true
            for (box in allBoxes) {
                val want = if (box in desiredSelected) FilterControlState.ACTIVE else FilterControlState.NEUTRAL
                val actual = classifyFilterCheckbox(sampler, box.cx, box.cy)
                if (actual == FilterControlState.UNKNOWN) return false
                if (actual != want) {
                    allMatch = false
                    game.tapCoordinate(box.cx.toDouble(), box.cy.toDouble(), box.label)
                    game.wait(0.15)
                }
            }
            if (allMatch) return true
            game.wait(0.3)
        }
        val bitmap = game.imageUtils.getSourceBitmap()
        return exactBottomSelection(bitmap, desiredSelected)
    }

    private fun exactBottomSelection(bitmap: Bitmap, desiredSelected: Set<FilterCheckbox>): Boolean {
        val frame = readVeteranFilterFrame(filterFrame(bitmap))
        if (frame.errors.isNotEmpty() || frame.dimensions[VeteranFilterDimension.MEMO] == null ||
            frame.dimensions[VeteranFilterDimension.FAVORITES] == null) return false
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
        return (ALL_FAVORITE_CHECKBOXES + listOf(MEMO_HAS_CHECKBOX, MEMO_NO_CHECKBOX)).all { box ->
            classifyFilterCheckbox(sampler, box.cx, box.cy) == if (box in desiredSelected) FilterControlState.ACTIVE else FilterControlState.NEUTRAL
        }
    }

    private fun requireNeutralFilterBaseline(): Map<VeteranFilterDimension, VeteranFilterDimensionRead> {
        val pass = scanFilterDialog()
        val baseline = pass.baseline
        if (baseline != FilterBaselineState.NEUTRAL_VERIFIED) {
            throw ProbeAbort(ProtectionScanOutcome.UI_UNEXPECTED, "Reset Filters baseline $baseline: ${pass.errors} ${pass.readings}")
        }
        return pass.readings
    }

    private fun resetFilters(): Map<VeteranFilterDimension, VeteranFilterDimensionRead> {
        requireDialog("before Reset Filters")
        game.tapCoordinate(DIALOG_RESET_FILTERS_X.toDouble(), DIALOG_RESET_FILTERS_Y.toDouble(), "reset_filters")
        game.wait(TAP_SETTLE_SECONDS)
        return requireNeutralFilterBaseline()
    }

    private fun cancelDialog() {
        requireDialog("before Cancel")
        game.tapCoordinate(DIALOG_CANCEL_X.toDouble(), DIALOG_CANCEL_Y.toDouble(), "cancel_display_settings")
        game.wait(NAV_SETTLE_SECONDS)
    }

    // -- Exit / verification ------------------------------------------------------------------------

    /** Best-effort close of whatever dialog/detail is up, for the abort path. */
    private fun bestEffortDismissDialog() {
        try {
            val bitmap = game.imageUtils.getSourceBitmap()
            if (filterDialogChromeRecognized(filterFrame(bitmap))) {
                game.tapCoordinate(DIALOG_RESET_FILTERS_X.toDouble(), DIALOG_RESET_FILTERS_Y.toDouble(), "abort_reset")
                game.wait(TAP_SETTLE_SECONDS)
            }
            if (isDisplaySettingsTitle(ocrTitle(game.imageUtils.getSourceBitmap()))) {
                game.tapCoordinate(DIALOG_CANCEL_X.toDouble(), DIALOG_CANCEL_Y.toDouble(), "abort_cancel")
                game.wait(NAV_SETTLE_SECONDS)
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.w(TAG, "[PROTECTION-SCAN] Abort dismiss failed: $e")
        }
    }

    /** Re-reads the roster list and confirms Filters: OFF with the same count and sort identity. */
    private fun verifyRosterFiltersOff(before: RosterListState): Pair<Boolean, Bitmap?> {
        return try {
            val (bitmap, screen) = reader.classifyScreenWithRetries(attempts = 4)
            if (screen.kind != RosterScreenKind.ROSTER_LIST) {
                MessageLog.w(TAG, "[PROTECTION-SCAN] Could not re-read the roster list after the probe (saw ${screen.kind}). Confirm the filter by hand.")
                return false to bitmap
            }
            val after = reader.readListState(bitmap, screen, verbose = false)
            val ok = protectionRosterRestored(before, after)
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
        rosterScanId: String? = null,
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
                rosterScanId = rosterScanId,
            )
        OutcomeCorpus.append(game.myContext, serializeVeteranProtectionScan(record), OutcomeCorpus.VETERAN_PROTECTION_PATH)
        MessageLog.i(TAG, "[PROTECTION-SCAN] scanId=$scanId outcome=$outcome (no protection filter gestures). ===== end =====")
    }
}
