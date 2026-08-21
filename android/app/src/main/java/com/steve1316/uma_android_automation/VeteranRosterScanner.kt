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
import com.steve1316.uma_android_automation.bot.serializeRosterScanEntry
import com.steve1316.uma_android_automation.bot.serializeRosterScanHeader
import com.steve1316.uma_android_automation.utils.CHEVRON_NEXT_BOX
import com.steve1316.uma_android_automation.utils.ChevronState
import com.steve1316.uma_android_automation.utils.DETAIL_CLOSE_X
import com.steve1316.uma_android_automation.utils.DETAIL_CLOSE_Y
import com.steve1316.uma_android_automation.utils.DETAIL_NEXT_CHEVRON_X
import com.steve1316.uma_android_automation.utils.DETAIL_NEXT_CHEVRON_Y
import com.steve1316.uma_android_automation.utils.OutcomeCorpus
import com.steve1316.uma_android_automation.utils.ROSTER_FIRST_CARD_X
import com.steve1316.uma_android_automation.utils.ROSTER_FIRST_CARD_Y
import com.steve1316.uma_android_automation.utils.RosterScreenKind
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.classifyChevron
import com.steve1316.uma_android_automation.utils.countChevronGreen
import com.steve1316.uma_android_automation.utils.deniedZoneAt

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

/** How many consecutive identical fingerprints mean the chevron has stopped advancing. Two adjacent
 * Veterans can legitimately be identical (same trainee, outfit, rank, rating, stats and aptitudes),
 * so two in a row is not evidence of a stall; three is. A false stall costs an INCOMPLETE scan,
 * never a wrong one. */
private const val STALL_REPEAT_COUNT = 3

/**
 * The read-only Veteran roster enumerator (PL-R1b): opens the first roster card once and walks the
 * whole roster with the `Umamusume Details` dialog's next chevron, reading each entry's identity
 * fields and writing one durable `roster_scan` header plus its `roster_entry` rows.
 *
 * Why the chevron and not the grid: the dialog advances exactly one roster entry per press in the
 * list's own sort order, so `scanIndex` is a counter rather than a scroll measurement. That removes
 * the entire skip/duplicate failure class the trainee-roster grid scan has already been bitten by
 * (2026-07-28), which is why this path exists at all.
 *
 * Safety, in the order it is enforced:
 *  - Every tap coordinate is checked against [deniedZoneAt] at runtime, and the walk aborts rather
 *    than dispatching a gesture into Transfer, Batch Favorite, the favorite marker, share, Change,
 *    or the epithet pencil. The same coordinates are pinned offline by `VeteranRosterProbesTest`.
 *  - The preconditions (roster list visible, `Registered X/Y` parsed, `Filters: OFF` confirmed) are
 *    checked BEFORE the first tap, and a failure means zero gestures were dispatched at all. A scan
 *    run with a filter applied would enumerate a subset and still look plausible, so this is
 *    fail-closed rather than a warning.
 *  - The Details title is re-asserted after every chevron tap. An unexpected screen stops the walk
 *    where it stands, marks the scan partial, and never tries to recover by tapping around.
 *  - The walk taps only: the first grid card, the next chevron, and Close.
 *
 * Completeness is not decided here; [assembleRosterScan] decides it from the counts, and a bounded
 * development run reports INCOMPLETE by construction.
 */
class VeteranRosterScanner(private val game: Game) {
    private val reader = VeteranRosterReader(game.imageUtils)

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
     * validation runs); 0 means walk until a real termination condition fires.
     */
    fun runScan(entryLimit: Int) {
        val startedAt = System.currentTimeMillis()
        val scanId = "rs-$startedAt-${java.util.UUID.randomUUID().toString().substring(0, 8)}"
        MessageLog.i(TAG, "[ROSTER-SCAN] ===== Veteran roster walk scanId=$scanId entryLimit=${if (entryLimit > 0) entryLimit else "none"} =====")

        val (listBitmap, listScreen) = reader.classifyScreenWithRetries()
        if (listScreen.kind != RosterScreenKind.ROSTER_LIST) {
            MessageLog.w(
                TAG,
                "[ROSTER-SCAN] Precondition failed: expected the Veteran Roster list, saw ${listScreen.kind} " +
                    "(registered OCR='${listScreen.registeredRaw}' title OCR='${listScreen.titleRaw}'). No gesture was dispatched.",
            )
            finish(scanId, startedAt, RosterListState(null, null, null, null, null), entryLimit, emptyList(), RosterScanTermination.PRECONDITION_FAILED, listBitmap)
            return
        }

        val list = reader.readListState(listBitmap, listScreen, verbose = true)
        if (list.registeredUsed == null || list.filtersOff != true) {
            MessageLog.w(
                TAG,
                "[ROSTER-SCAN] Precondition failed: registeredUsed=${list.registeredUsed ?: "UNREAD"} filtersOff=${list.filtersOff ?: "UNREAD"}. " +
                    "A scan under an unknown filter state would enumerate a subset and still look complete, so it stops here. No gesture was dispatched.",
            )
            finish(scanId, startedAt, list, entryLimit, emptyList(), RosterScanTermination.PRECONDITION_FAILED, listBitmap)
            return
        }

        val used = list.registeredUsed
        val hardBound = (list.registeredCapacity ?: used) + HARD_BOUND_SLACK
        MessageLog.i(
            TAG,
            "[ROSTER-SCAN] Preconditions OK: used=$used capacity=${list.registeredCapacity ?: "UNREAD"} filtersOff=true " +
                "sort=${list.sortKey ?: "UNREAD"}/${list.sortDirection ?: "UNREAD"} hardBound=$hardBound",
        )

        val observations = mutableListOf<Pair<Long, RosterEntryObservation>>()
        val fingerprints = mutableListOf<String?>()
        var lastBitmap = listBitmap
        val termination =
            try {
                walk(list, used, hardBound, entryLimit, observations, fingerprints) { lastBitmap = it }
            } catch (e: DeniedTapException) {
                MessageLog.e(TAG, "[ROSTER-SCAN] ${e.message}")
                RosterScanTermination.UNEXPECTED_SCREEN
            }

        closeDialogAndVerify()
        finish(scanId, startedAt, list, entryLimit, observations, termination, lastBitmap)
    }

    /**
     * The walk itself. Returns why it stopped. Entries are appended to [observations] as they are
     * read, so an interrupted process leaves a checkpointed prefix rather than nothing (the offline
     * reader treats entry rows with no header record as a partial scan).
     */
    private fun walk(
        list: RosterListState,
        used: Int,
        hardBound: Int,
        entryLimit: Int,
        observations: MutableList<Pair<Long, RosterEntryObservation>>,
        fingerprints: MutableList<String?>,
        publishFrame: (Bitmap) -> Unit,
    ): RosterScanTermination {
        val walkStart = System.currentTimeMillis()

        safeTap(RosterScreenKind.ROSTER_LIST, ROSTER_FIRST_CARD_X, ROSTER_FIRST_CARD_Y, "veteran_roster_first_card")
        game.wait(CHEVRON_SETTLE_SECONDS)

        var (bitmap, screen) = reader.classifyScreenWithRetries(attempts = 3)
        publishFrame(bitmap)
        if (screen.kind != RosterScreenKind.UMAMUSUME_DETAILS) {
            MessageLog.w(TAG, "[ROSTER-SCAN] Opening the first card did not produce the Details dialog (saw ${screen.kind}, title OCR='${screen.titleRaw}'). Stopping.")
            return RosterScanTermination.UNEXPECTED_SCREEN
        }
        recordEntry(bitmap, observations, fingerprints, walkStart)

        while (true) {
            val count = observations.size
            if (count >= used) return RosterScanTermination.COUNT_REACHED
            if (entryLimit > 0 && count >= entryLimit) return RosterScanTermination.ENTRY_LIMIT_REACHED
            if (count >= hardBound) {
                MessageLog.w(TAG, "[ROSTER-SCAN] Hard bound reached: $count entries against a bound of $hardBound. The walk is not terminating on its own.")
                return RosterScanTermination.HARD_BOUND_REACHED
            }
            if (System.currentTimeMillis() - walkStart > WALL_CLOCK_BUDGET_MS) {
                MessageLog.w(TAG, "[ROSTER-SCAN] Wall-clock budget exhausted after $count entries. Stopping and marking the scan partial.")
                return RosterScanTermination.HARD_BOUND_REACHED
            }
            if (isStalled(fingerprints)) {
                MessageLog.w(TAG, "[ROSTER-SCAN] $STALL_REPEAT_COUNT consecutive identical entries at index ${count - 1}: the chevron is no longer advancing. Stopping.")
                return RosterScanTermination.STALLED
            }

            val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
            val chevron = classifyChevron(sampler, CHEVRON_NEXT_BOX)
            if (chevron == ChevronState.DISABLED) {
                MessageLog.i(TAG, "[ROSTER-SCAN] Next chevron classified DISABLED (green=${countChevronGreen(sampler, CHEVRON_NEXT_BOX)}) after $count entries: last entry reached.")
                return RosterScanTermination.CHEVRON_END
            }

            safeTap(RosterScreenKind.UMAMUSUME_DETAILS, DETAIL_NEXT_CHEVRON_X, DETAIL_NEXT_CHEVRON_Y, "veteran_detail_next_chevron")
            game.wait(CHEVRON_SETTLE_SECONDS)

            val next = reader.classifyScreenWithRetries(attempts = 3)
            bitmap = next.first
            screen = next.second
            publishFrame(bitmap)
            if (screen.kind != RosterScreenKind.UMAMUSUME_DETAILS) {
                MessageLog.w(
                    TAG,
                    "[ROSTER-SCAN] After chevron $count the frame is ${screen.kind}, not the Details dialog (title OCR='${screen.titleRaw}'). " +
                        "Stopping where it stands; the scan is partial and no recovery taps are attempted.",
                )
                return RosterScanTermination.UNEXPECTED_SCREEN
            }

            val observation = reader.readDetailObservation(bitmap, includeCareerInfo = false, verbose = false)
            val fingerprint = entryFingerprint(observation)
            if (fingerprint != null && count >= 2 && fingerprint == fingerprints.firstOrNull()) {
                MessageLog.w(TAG, "[ROSTER-SCAN] Entry $count repeats entry 0's fingerprint: the walk wrapped around after $count entries. Stopping without recording it.")
                return RosterScanTermination.WRAPPED
            }
            appendEntry(observation, fingerprint, observations, fingerprints, walkStart, chevron)
        }
    }

    /** True when the last [STALL_REPEAT_COUNT] identified entries all carry the same fingerprint. */
    private fun isStalled(fingerprints: List<String?>): Boolean {
        if (fingerprints.size < STALL_REPEAT_COUNT) return false
        val tail = fingerprints.takeLast(STALL_REPEAT_COUNT)
        val first = tail.first() ?: return false
        return tail.all { it == first }
    }

    private fun recordEntry(
        bitmap: Bitmap,
        observations: MutableList<Pair<Long, RosterEntryObservation>>,
        fingerprints: MutableList<String?>,
        walkStart: Long,
    ) {
        val observation = reader.readDetailObservation(bitmap, includeCareerInfo = false, verbose = false)
        appendEntry(observation, entryFingerprint(observation), observations, fingerprints, walkStart, chevron = null)
    }

    private fun appendEntry(
        observation: RosterEntryObservation,
        fingerprint: String?,
        observations: MutableList<Pair<Long, RosterEntryObservation>>,
        fingerprints: MutableList<String?>,
        walkStart: Long,
        chevron: ChevronState?,
    ) {
        val index = observations.size
        observations.add(System.currentTimeMillis() to observation)
        fingerprints.add(fingerprint)
        val stats = observation.stats.joinToString("/") { it?.toString() ?: "?" }
        val headerRead = listOfNotNull(observation.character, observation.outfit, observation.rank, observation.rating?.toString()).size
        MessageLog.i(
            TAG,
            "[ROSTER-SCAN] i=$index ${observation.character ?: "?"} [${observation.outfit ?: "?"}] rank=${observation.rank ?: "?"} " +
                "rating=${observation.rating ?: "?"} stats=$stats headerRead=$headerRead/4 fp=${fingerprint ?: "UNRESOLVED"} " +
                "chevronBefore=${chevron ?: "n/a"} elapsed=${(System.currentTimeMillis() - walkStart) / 1000}s",
        )
    }

    /** Closes the Details dialog and re-reads the list status bar as the integrity proof that the
     * walk changed nothing: the same Registered count, the same filters, the same sort. */
    private fun closeDialogAndVerify() {
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
            } else {
                MessageLog.w(TAG, "[ROSTER-SCAN] Could not re-read the roster list after the walk (saw ${afterScreen.kind}). Close the dialog by hand and confirm the roster count.")
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.w(TAG, "[ROSTER-SCAN] Exit path failed: $e. Close the dialog by hand.")
        }
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
    ) {
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
            )
        persist(assembled)
        val h = assembled.header
        MessageLog.i(
            TAG,
            "[ROSTER-SCAN] scanId=$scanId entries=${h.entriesEnumerated} displayedUsed=${h.list.registeredUsed ?: "UNREAD"} " +
                "unique=${h.uniqueFingerprints} duplicates=${h.duplicateFingerprintCount} unidentified=${h.unidentifiedCount} " +
                "discrepancy=${h.countDiscrepancy ?: "n/a"} termination=${h.terminationReason} " +
                "enumerationComplete=${h.enumerationComplete} identityComplete=${h.identityComplete} trustedForRetention=${h.trustedForRetention} " +
                "runtime=${(h.completedAt - h.startedAt) / 1000}s",
        )
        if (h.entriesEnumerated > 0) {
            MessageLog.i(TAG, "[ROSTER-SCAN] Mean per-entry cost: ${(h.completedAt - h.startedAt) / h.entriesEnumerated}ms")
        }
        MessageLog.i(TAG, "[ROSTER-SCAN] ===== end =====")
    }

    private fun persist(assembled: AssembledRosterScan) {
        val scanId = assembled.header.scanId
        for (entry in assembled.entries) {
            OutcomeCorpus.append(game.myContext, serializeRosterScanEntry(scanId, entry), OutcomeCorpus.ROSTER_SCAN_PATH)
        }
        OutcomeCorpus.append(game.myContext, serializeRosterScanHeader(assembled.header), OutcomeCorpus.ROSTER_SCAN_PATH)
    }
}
