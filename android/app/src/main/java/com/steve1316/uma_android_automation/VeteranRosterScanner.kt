package com.steve1316.uma_android_automation

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.AssembledRosterScan
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.RosterEntryObservation
import com.steve1316.uma_android_automation.bot.RosterListState
import com.steve1316.uma_android_automation.bot.RosterScanTermination
import com.steve1316.uma_android_automation.bot.assembleRosterScan
import com.steve1316.uma_android_automation.bot.entryFingerprint
import com.steve1316.uma_android_automation.bot.finalizeHybridRanks
import com.steve1316.uma_android_automation.bot.identityUnresolved
import com.steve1316.uma_android_automation.bot.rankFreeRosterIdentity
import com.steve1316.uma_android_automation.bot.walkRosterCycle
import com.steve1316.uma_android_automation.bot.settledRosterPagerRead
import com.steve1316.uma_android_automation.bot.serializeRosterScanEntry
import com.steve1316.uma_android_automation.bot.serializeRosterScanHeader
import com.steve1316.uma_android_automation.utils.CHEVRON_NEXT_BOX
import com.steve1316.uma_android_automation.utils.ChevronState
import com.steve1316.uma_android_automation.utils.DETAIL_CLOSE_X
import com.steve1316.uma_android_automation.utils.DETAIL_CLOSE_Y
import com.steve1316.uma_android_automation.utils.DETAIL_NEXT_CHEVRON_X
import com.steve1316.uma_android_automation.utils.DETAIL_NEXT_CHEVRON_Y
import com.steve1316.uma_android_automation.utils.OutcomeCorpus
import com.steve1316.uma_android_automation.utils.RosterCardRatingRead
import com.steve1316.uma_android_automation.utils.ROSTER_FIRST_CARD_X
import com.steve1316.uma_android_automation.utils.ROSTER_FIRST_CARD_Y
import com.steve1316.uma_android_automation.utils.ROSTER_VISIBLE_CARD_COUNT
import com.steve1316.uma_android_automation.utils.RosterEvidenceWriter
import com.steve1316.uma_android_automation.utils.RosterScreenKind
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.VeteranIdentityCatalog
import com.steve1316.uma_android_automation.utils.classifyChevron
import com.steve1316.uma_android_automation.utils.deniedZoneAt
import com.steve1316.uma_android_automation.utils.hybridListEvidenceEligible
import com.steve1316.uma_android_automation.utils.rosterListBindingStable
import com.steve1316.uma_android_automation.utils.rosterScanPrerequisitesMet

private const val TAG = "[VeteranRosterScanner]"

/** Slack over the account's registered capacity before the walk gives up. Generous enough that a
 * legitimate roster that grew between the count read and the walk still finishes, small enough that
 * a wrapping chevron cannot loop forever. */
private const val HARD_BOUND_SLACK = 8

/** Wall-clock ceiling for one walk. At the measured per-entry cost a full 260-entry Pass A run
 * finishes far inside this; exceeding it means something is retrying, not scanning. */
private const val WALL_CLOCK_BUDGET_MS = 45 * 60 * 1000L

/** Settle time after a chevron tap before the next capture. The dialog cross-fades its contents;
 * capturing mid-fade produces a half-old frame whose fingerprint belongs to neither entry. */
private const val CHEVRON_SETTLE_SECONDS = 0.6

/** A single chevron tap may settle slowly. Re-capture, but never tap it a second time. */
private const val TRANSITION_CAPTURE_ATTEMPTS = 3

/**
 * The read-only Veteran roster enumerator: opens the first card once and walks Details with the
 * next chevron. Optional ratings from the initial visible list are bound only after the full walk.
 *
 * Safety, in the order it is enforced:
 *  - Every tap coordinate is checked against [deniedZoneAt] at runtime, and the walk aborts rather
 *    than dispatching a gesture into Transfer, Batch Favorite, the favorite marker, share, Change,
 *    or the epithet pencil. The same coordinates are pinned offline by `VeteranRosterProbesTest`.
 *  - The preconditions (roster list visible, Registered count and Filters OFF) are
 *    checked BEFORE the first tap, and a failure means zero gestures were dispatched at all. A scan
 *    run with a filter applied would enumerate a subset and still look plausible, so this is
 *    fail-closed rather than a warning.
 *  - The Details title is re-asserted after every chevron tap. An unexpected screen stops the walk
 *    where it stands, marks the scan partial, and never tries to recover by tapping around.
 *  - The walk taps only the first card, the next chevron, and Close.
 *
 * The walk supplies cycle closure only after an extra settled next transition returns to the first
 * complete rank-free identity. [assembleRosterScan] also checks final count, filters, and identities.
 */
class VeteranRosterScanner(private val game: Game) {
    private val catalog = VeteranIdentityCatalog.loadFromAssets(game.myContext)
    private val reader = VeteranRosterReader(game.imageUtils, catalog)

    /** Raised when a tap coordinate resolves into a deny zone. Never expected: the coordinates are
     * constants pinned by a test. If it ever fires, the geometry moved and the walk must not tap. */
    private class DeniedTapException(message: String) : IllegalStateException(message)

    private fun safeTap(screen: RosterScreenKind, x: Int, y: Int, label: String) {
        val denied = deniedZoneAt(screen, x, y)
        if (denied != null) throw DeniedTapException("refusing to tap $label at ($x, $y): inside deny zone ${denied.label}")
        game.tapCoordinate(x.toDouble(), y.toDouble(), label)
    }

    /**
     * Runs one walk. [entryLimit] caps how many entries are read (the 5-entry and 20-entry bounded
     * validation runs); 0 means walk until a real termination condition fires. [captureEvidence]
     * turns on the failure-crop diagnostic: it changes nothing about what is read or how a field is
     * decided, only whether the pixels behind an UNRESOLVED field are kept for offline diagnosis.
     */
    fun runScan(entryLimit: Int, captureEvidence: Boolean = false): AssembledRosterScan {
        val startedAt = System.currentTimeMillis()
        val scanId = "rs-$startedAt-${java.util.UUID.randomUUID().toString().substring(0, 8)}"
        MessageLog.i(
            TAG,
            "[ROSTER-SCAN] ===== Veteran roster walk scanId=$scanId entryLimit=${if (entryLimit > 0) entryLimit else "none"} " +
                "evidence=${if (captureEvidence) "on" else "off"} identityDomain=${catalog?.let { "${it.characters.size} trainees/${it.outfitCount} outfits" } ?: "UNAVAILABLE"} =====",
        )
        if (catalog == null) {
            MessageLog.w(
                TAG,
                "[ROSTER-SCAN] The generated identity asset did not load, so no outfit can resolve and every entry will stay unfingerprinted. " +
                    "This is a packaging failure, not a roster problem.",
            )
        }
        val evidence = if (captureEvidence) RosterEvidenceWriter(game.myContext, scanId) else null

        val (listBitmap, listScreen) = reader.classifyScreenWithRetries()
        if (listScreen.kind != RosterScreenKind.ROSTER_LIST) {
            MessageLog.w(
                TAG,
                "[ROSTER-SCAN] Precondition failed: expected the Veteran Roster list, saw ${listScreen.kind} " +
                    "(registered OCR='${listScreen.registeredRaw}' title OCR='${listScreen.titleRaw}'). No gesture was dispatched.",
            )
            return finish(scanId, startedAt, RosterListState(null, null, null, null, null), entryLimit, emptyList(), RosterScanTermination.PRECONDITION_FAILED, listBitmap, evidence)
        }

        val list = reader.readListState(listBitmap, listScreen, verbose = true)
        if (!rosterScanPrerequisitesMet(list)) {
            MessageLog.w(
                TAG,
                "[ROSTER-SCAN] Precondition failed: registeredUsed=${list.registeredUsed ?: "UNREAD"} filtersOff=${list.filtersOff ?: "UNREAD"} " +
                    "sort=${list.sortKey ?: "UNREAD"}/${list.sortDirection ?: "UNREAD"}. " +
                    "An unread count or unconfirmed Filters OFF cannot support a complete walk. No gesture was dispatched.",
            )
            return finish(scanId, startedAt, list, entryLimit, emptyList(), RosterScanTermination.PRECONDITION_FAILED, listBitmap, evidence)
        }

        val used = requireNotNull(list.registeredUsed)
        val hardBound = (list.registeredCapacity ?: used) + HARD_BOUND_SLACK
        MessageLog.i(
            TAG,
            "[ROSTER-SCAN] Preconditions OK: used=$used capacity=${list.registeredCapacity ?: "UNREAD"} filtersOff=true " +
                "sort=${list.sortKey ?: "UNREAD"}/${list.sortDirection ?: "UNREAD"} hardBound=$hardBound",
        )

        val observations = mutableListOf<Pair<Long, RosterEntryObservation>>()
        val viewportSupported = listBitmap.width == 1080 && listBitmap.height == 1920
        val listRatings = mutableListOf<RosterCardRatingRead>()
        if (hybridListEvidenceEligible(list, viewportSupported)) {
            for (index in 0 until minOf(used, ROSTER_VISIBLE_CARD_COUNT)) {
                val read = try {
                    reader.readListCardRating(listBitmap, index) ?: RosterCardRatingRead(index, "", null, "rejected", "crop_missing")
                } catch (e: InterruptedException) {
                    throw e
                } catch (e: Exception) {
                    RosterCardRatingRead(index, "", null, "failed", "ocr_failed")
                }
                listRatings.add(read)
            }
            MessageLog.i(TAG, "[ROSTER-SCAN] Provisional list ratings: ${listRatings.count { it.rating != null }}/${listRatings.size} parsed; top anchor awaits the full Details census.")
        }
        var lastBitmap = listBitmap
        val termination =
            try {
                walk(used, hardBound, entryLimit, listRatings, observations, evidence) { lastBitmap = it }
            } catch (e: DeniedTapException) {
                MessageLog.e(TAG, "[ROSTER-SCAN] ${e.message}")
                RosterScanTermination.UNEXPECTED_SCREEN
            }

        val afterList = closeDialogAndVerify()
        val finalStateVerified = rosterListBindingStable(list, afterList)
        val completedWalk = termination in setOf(RosterScanTermination.CYCLE_CLOSED, RosterScanTermination.EMPTY_LIST)
        val finalTermination = if (completedWalk && !finalStateVerified) RosterScanTermination.UNEXPECTED_SCREEN else termination
        val finalObservations = finalizeHybridRanks(list, afterList, finalTermination, entryLimit, observations, listRatings, viewportSupported, finalTermination == RosterScanTermination.CYCLE_CLOSED)
        for ((index, pair) in finalObservations.withIndex()) {
            val d = pair.second.diagnostics
            if (d?.rankFamily in listOf("E", "B")) {
                MessageLog.i(TAG, "[ROSTER-SCAN] Final i=$index binding=${d?.bindingStatus} rank=${pair.second.rank ?: "UNRESOLVED"} reason=${d?.bindingRejectReason ?: "none"}")
            }
        }
        return finish(scanId, startedAt, list, entryLimit, finalObservations, finalTermination, lastBitmap, evidence, finalStateVerified)
    }

    /**
     * The walk itself. Returns why it stopped. Entries are appended to [observations] as they are
     * read, so an interrupted process leaves a checkpointed prefix rather than nothing (the offline
     * reader treats entry rows with no header record as a partial scan).
     */
    private fun walk(
        used: Int,
        hardBound: Int,
        entryLimit: Int,
        listRatings: List<RosterCardRatingRead>,
        observations: MutableList<Pair<Long, RosterEntryObservation>>,
        evidence: RosterEvidenceWriter?,
        publishFrame: (Bitmap) -> Unit,
    ): RosterScanTermination {
        val walkStart = System.currentTimeMillis()
        if (used == 0) return RosterScanTermination.EMPTY_LIST
        safeTap(RosterScreenKind.ROSTER_LIST, ROSTER_FIRST_CARD_X, ROSTER_FIRST_CARD_Y, "veteran_roster_first_card")
        game.wait(CHEVRON_SETTLE_SECONDS)
        var (bitmap, firstScreen) = reader.classifyScreenWithRetries(attempts = 3)
        publishFrame(bitmap)
        if (firstScreen.kind != RosterScreenKind.UMAMUSUME_DETAILS) return RosterScanTermination.UNEXPECTED_SCREEN
        val first = reader.readDetailObservation(bitmap, includeCareerInfo = false, verbose = false, listRead = listRatings.firstOrNull { it.scanIndex == 0 })
        var transitionFailure = RosterScanTermination.STALLED
        var lastChevron: ChevronState? = null
        val termination = walkRosterCycle(
            first, used, hardBound, entryLimit,
            budgetExceeded = { System.currentTimeMillis() - walkStart > WALL_CLOCK_BUDGET_MS },
            visit = { _, observation ->
                appendEntry(observation, entryFingerprint(observation), observations, walkStart, lastChevron, bitmap, evidence)
            },
            advance = advance@{ previous, count ->
                val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
                val chevron = classifyChevron(sampler, CHEVRON_NEXT_BOX)
                if (chevron != ChevronState.ENABLED) return@advance null
                lastChevron = chevron
                safeTap(RosterScreenKind.UMAMUSUME_DETAILS, DETAIL_NEXT_CHEVRON_X, DETAIL_NEXT_CHEVRON_Y, "veteran_detail_next_chevron")
                game.wait(CHEVRON_SETTLE_SECONDS)
                var candidate: RosterEntryObservation? = null
                for (attempt in 1..TRANSITION_CAPTURE_ATTEMPTS) {
                    val (nextBitmap, screen) = reader.classifyScreenWithRetries(attempts = 3)
                    publishFrame(nextBitmap)
                    if (screen.kind != RosterScreenKind.UMAMUSUME_DETAILS) {
                        transitionFailure = RosterScanTermination.UNEXPECTED_SCREEN
                        return@advance null
                    }
                    val read = reader.readDetailObservation(nextBitmap, includeCareerInfo = false, verbose = false, listRead = listRatings.firstOrNull { it.scanIndex == count && count < used })
                    if (candidate != null && settledRosterPagerRead(previous, candidate, read, used)) {
                        bitmap = nextBitmap
                        return@advance read
                    }
                    candidate = read.takeIf { rankFreeRosterIdentity(it) != null }
                    if (attempt < TRANSITION_CAPTURE_ATTEMPTS) game.wait(CHEVRON_SETTLE_SECONDS)
                }
                null
            },
        )
        return if (termination == RosterScanTermination.STALLED) transitionFailure else termination
    }

    private fun appendEntry(
        observation: RosterEntryObservation,
        fingerprint: String?,
        observations: MutableList<Pair<Long, RosterEntryObservation>>,
        walkStart: Long,
        chevron: ChevronState?,
        frame: Bitmap,
        evidence: RosterEvidenceWriter?,
    ) {
        val index = observations.size
        observations.add(System.currentTimeMillis() to observation)
        val stats = observation.stats.joinToString("/") { it?.toString() ?: "?" }
        val headerRead = listOfNotNull(observation.character, observation.outfit, observation.rank, observation.rating?.toString()).size
        MessageLog.i(
            TAG,
            "[ROSTER-SCAN] i=$index ${observation.character ?: "?"} [${observation.outfit ?: "?"}] rank=${observation.rank ?: "?"} " +
                "rating=${observation.rating ?: "?"} stats=$stats headerRead=$headerRead/4 fp=${fingerprint ?: "UNRESOLVED"} " +
                "rankPath=${observation.diagnostics?.rankResolutionPath ?: "?"} (provisional) rankReject=${observation.diagnostics?.rankRejectReason ?: "none"} " +
                "chevronBefore=${chevron ?: "n/a"} elapsed=${(System.currentTimeMillis() - walkStart) / 1000}s",
        )
        saveFailureEvidence(evidence, index, observation, frame)
    }

    /**
     * Persists the crop behind every field unresolved during the provisional Details read.
     *
     * A deferred E/B resolution may later clear rank from the final unresolved list; its earlier
     * provisional crop remains useful evidence. The call is a no-op when diagnostics are off.
     */
    private fun saveFailureEvidence(evidence: RosterEvidenceWriter?, index: Int, observation: RosterEntryObservation, frame: Bitmap) {
        if (evidence == null) return
        for (field in identityUnresolved(observation)) {
            val box = RosterEvidenceWriter.boxForField(field) ?: continue
            evidence.saveFieldCrop(frame, index, field, box)
        }
    }

    /** Closes the Details dialog and re-reads the list status bar as the integrity proof that the
     * walk changed nothing: the same Registered count, the same filters, the same sort. */
    private fun closeDialogAndVerify(): RosterListState? =
        try {
            val (bitmap, screen) = reader.classifyScreenWithRetries(attempts = 2)
            if (screen.kind == RosterScreenKind.UMAMUSUME_DETAILS) {
                safeTap(RosterScreenKind.UMAMUSUME_DETAILS, DETAIL_CLOSE_X, DETAIL_CLOSE_Y, "veteran_detail_close")
                game.wait(CHEVRON_SETTLE_SECONDS)
            } else if (screen.kind == RosterScreenKind.ROSTER_LIST) {
                MessageLog.i(TAG, "[ROSTER-SCAN] Already back on the roster list; no Close needed. (${bitmap.width}x${bitmap.height})")
            }
            val (afterBitmap, afterScreen) = reader.classifyScreenWithRetries(attempts = 3)
            if (afterScreen.kind == RosterScreenKind.ROSTER_LIST) {
                val after = reader.readListState(afterBitmap, afterScreen, verbose = false)
                MessageLog.i(
                    TAG,
                    "[ROSTER-SCAN] Post-walk roster state: Registered ${after.registeredUsed ?: "?"}/${after.registeredCapacity ?: "?"} " +
                        "filtersOff=${after.filtersOff ?: "UNREAD"} sort=${after.sortKey ?: "UNREAD"}/${after.sortDirection ?: "UNREAD"}",
                )
                after
            } else {
                MessageLog.w(TAG, "[ROSTER-SCAN] Could not re-read the roster list after the walk (saw ${afterScreen.kind}). Close the dialog by hand and confirm the roster count.")
                null
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.w(TAG, "[ROSTER-SCAN] Exit path failed: $e. Close the dialog by hand.")
            null
        }

    /** Assembles, logs, and persists the scan. Entries are written before the header so a truncated
     * write leaves headerless rows (read offline as partial) rather than a header promising rows
     * that are not there. */
    private fun finish(
        scanId: String,
        startedAt: Long,
        list: RosterListState,
        entryLimit: Int,
        observations: List<Pair<Long, RosterEntryObservation>>,
        termination: RosterScanTermination,
        frame: Bitmap,
        evidence: RosterEvidenceWriter?,
        finalStateVerified: Boolean = true,
    ): AssembledRosterScan {
        val assembled =
            assembleRosterScan(
                scanId = scanId,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                list = list,
                entryLimit = entryLimit,
                observations = observations,
                termination = termination,
                appVersion = BuildConfig.VERSION_NAME,
                screenWidth = frame.width,
                screenHeight = frame.height,
                evidenceCropCount = evidence?.cropCount ?: 0,
                finalStateVerified = finalStateVerified,
            )
        persist(assembled)
        val h = assembled.header
        MessageLog.i(
            TAG,
            "[ROSTER-SCAN] scanId=$scanId entries=${h.entriesEnumerated} displayedUsed=${h.list.registeredUsed ?: "UNREAD"} " +
                "unique=${h.uniqueFingerprints} duplicates=${h.duplicateFingerprintCount} unidentified=${h.unidentifiedCount} " +
                "discrepancy=${h.countDiscrepancy ?: "n/a"} termination=${h.terminationReason} " +
                "enumerationComplete=${h.enumerationComplete} identityComplete=${h.identityComplete} trustedForRetention=${h.trustedForRetention} " +
                "runtime=${(h.completedAt - h.startedAt) / 1000}s evidenceCrops=${h.evidenceCropCount}",
        )
        if (h.entriesEnumerated > 0) {
            MessageLog.i(TAG, "[ROSTER-SCAN] Mean per-entry cost: ${(h.completedAt - h.startedAt) / h.entriesEnumerated}ms")
        }
        MessageLog.i(TAG, "[ROSTER-SCAN] ===== end =====")
        return assembled
    }

    private fun persist(assembled: AssembledRosterScan) {
        val scanId = assembled.header.scanId
        for (entry in assembled.entries) {
            OutcomeCorpus.append(game.myContext, serializeRosterScanEntry(scanId, entry), OutcomeCorpus.ROSTER_SCAN_PATH)
        }
        OutcomeCorpus.append(game.myContext, serializeRosterScanHeader(assembled.header), OutcomeCorpus.ROSTER_SCAN_PATH)
    }
}
