package com.steve1316.uma_android_automation

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.InspirationScanTermination
import com.steve1316.uma_android_automation.bot.VETERAN_INSPIRATION_SCHEMA_VERSION
import com.steve1316.uma_android_automation.bot.VeteranInspirationObservation
import com.steve1316.uma_android_automation.bot.VeteranInspirationScanHeader
import com.steve1316.uma_android_automation.bot.entryFingerprint
import com.steve1316.uma_android_automation.bot.serializeVeteranInspiration
import com.steve1316.uma_android_automation.bot.serializeVeteranInspirationScan
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
import com.steve1316.uma_android_automation.utils.VeteranFactorDomain
import com.steve1316.uma_android_automation.utils.VeteranIdentityCatalog
import com.steve1316.uma_android_automation.utils.classifyChevron
import com.steve1316.uma_android_automation.utils.deniedZoneAt

private const val TAG = "[VeteranInspirationScanner]"

/** Settle time after a chevron tap before the next capture, matching the roster walk's. */
private const val CHEVRON_SETTLE_SECONDS = 0.6

/** Slack over the account's registered capacity before the walk gives up, matching the roster walk. */
private const val HARD_BOUND_SLACK = 8

/**
 * The bounded, read-only Veteran Inspiration capture (PL-R1c): walks the roster with the
 * `Umamusume Details` dialog's next chevron exactly as [VeteranRosterScanner] does, and at each entry
 * reads the identity header AND the whole Inspiration panel, writing one `veteran_inspiration` record
 * per Veteran plus a `veteran_inspiration_scan` header.
 *
 * Deliberately a separate walk rather than a flag on [VeteranRosterScanner]. That walk's enumeration
 * behaviour is proven and pushed; an Inspiration capture costs roughly an order of magnitude more per
 * entry (a tab selection, several swipes, and up to forty OCR reads against one), so mixing the two
 * would have made the cheap, trustworthy roster enumeration hostage to the expensive one.
 *
 * Safety is the roster walk's, unchanged: it taps only the first grid card, the next chevron, Close,
 * and the Inspiration tab; every coordinate is checked against [deniedZoneAt] at runtime; the Details
 * title is re-asserted after every chevron; and an unexpected screen stops the walk where it stands
 * rather than tapping around to recover.
 *
 * The batch is bound to ONE roster state: the `Registered used` count is read before the first entry
 * and again after the last, and a change between them marks the whole batch
 * `snapshotCompatibility:false` rather than letting a mid-capture registration silently shift every
 * later entry's identity.
 */
class VeteranInspirationScanner(private val game: Game) {
    private val catalog = VeteranIdentityCatalog.loadFromAssets(game.myContext)
    private val factorDomain = VeteranFactorDomain.loadFromAssets(game.myContext)
    private val rosterReader = VeteranRosterReader(game.imageUtils, catalog)
    private val inspirationReader = VeteranInspirationReader(game, factorDomain)

    private class DeniedTapException(message: String) : IllegalStateException(message)

    private fun safeTap(screen: RosterScreenKind, x: Int, y: Int, label: String) {
        val denied = deniedZoneAt(screen, x, y)
        if (denied != null) throw DeniedTapException("refusing to tap $label at ($x, $y): inside deny zone ${denied.label}")
        game.tapCoordinate(x.toDouble(), y.toDouble(), label)
    }

    /**
     * Runs one batch. [entryLimit] caps how many Veterans are captured (the staged 1 / 3 / 20 runs);
     * 0 walks the whole roster. Park the game on the Veteran Roster list with Filters: OFF first.
     */
    fun runScan(entryLimit: Int) {
        val startedAt = System.currentTimeMillis()
        val scanId = "insp-$startedAt-${java.util.UUID.randomUUID().toString().substring(0, 8)}"
        MessageLog.i(
            TAG,
            "[INSPIRATION-SCAN] ===== Veteran Inspiration capture scanId=$scanId " +
                "entryLimit=${if (entryLimit > 0) entryLimit else "none"} =====",
        )

        val (listBitmap, listScreen) = rosterReader.classifyScreenWithRetries()
        if (listScreen.kind != RosterScreenKind.ROSTER_LIST) {
            MessageLog.w(
                TAG,
                "[INSPIRATION-SCAN] Precondition failed: expected the Veteran Roster list, saw ${listScreen.kind} " +
                    "(registered OCR='${listScreen.registeredRaw}' title OCR='${listScreen.titleRaw}'). No gesture was dispatched.",
            )
            return
        }
        val list = rosterReader.readListState(listBitmap, listScreen, verbose = true)
        if (list.registeredUsed == null || list.filtersOff != true) {
            MessageLog.w(
                TAG,
                "[INSPIRATION-SCAN] Precondition failed: registeredUsed=${list.registeredUsed ?: "UNREAD"} " +
                    "filtersOff=${list.filtersOff ?: "UNREAD"}. A capture under an unknown filter state would walk a " +
                    "subset and still look plausible, so it stops here. No gesture was dispatched.",
            )
            return
        }

        val used = list.registeredUsed
        val hardBound = (list.registeredCapacity ?: used) + HARD_BOUND_SLACK
        MessageLog.i(
            TAG,
            "[INSPIRATION-SCAN] Preconditions OK: used=$used capacity=${list.registeredCapacity ?: "UNREAD"} " +
                "filtersOff=true sort=${list.sortKey ?: "UNREAD"}/${list.sortDirection ?: "UNREAD"}",
        )

        val observations = mutableListOf<VeteranInspirationObservation>()
        var width = listBitmap.width
        var height = listBitmap.height
        val termination =
            try {
                walk(scanId, used, hardBound, entryLimit, observations) { w, h ->
                    width = w
                    height = h
                }
            } catch (e: DeniedTapException) {
                MessageLog.e(TAG, "[INSPIRATION-SCAN] ${e.message}")
                InspirationScanTermination.UNEXPECTED_SCREEN
            }

        val registeredUsedAtEnd = closeDialogAndReadRoster()
        val header =
            VeteranInspirationScanHeader(
                schemaVersion = VETERAN_INSPIRATION_SCHEMA_VERSION,
                scanId = scanId,
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                registeredUsedAtStart = used,
                registeredUsedAtEnd = registeredUsedAtEnd,
                registeredCapacity = list.registeredCapacity,
                filtersOff = list.filtersOff,
                sortKey = list.sortKey,
                sortDirection = list.sortDirection,
                // Unknown is not compatible: an unread post-walk count cannot prove the roster held still.
                snapshotCompatibility = registeredUsedAtEnd != null && registeredUsedAtEnd == used,
                entryLimit = entryLimit,
                entriesCaptured = observations.size,
                entriesComplete = observations.count { it.sparkCaptureComplete },
                terminationReason = termination,
                app = BuildConfig.VERSION_NAME,
                screenWidth = width,
                screenHeight = height,
            )
        OutcomeCorpus.append(game.myContext, serializeVeteranInspirationScan(header), OutcomeCorpus.VETERAN_INSPIRATION_PATH)

        val runtimeMs = header.completedAt - header.startedAt
        MessageLog.i(
            TAG,
            "[INSPIRATION-SCAN] scanId=$scanId captured=${header.entriesCaptured} complete=${header.entriesComplete} " +
                "termination=${header.terminationReason} snapshotCompatibility=${header.snapshotCompatibility} " +
                "registered=$used->${registeredUsedAtEnd ?: "UNREAD"} runtime=${runtimeMs / 1000}s",
        )
        if (header.entriesCaptured > 0) {
            MessageLog.i(TAG, "[INSPIRATION-SCAN] Mean per-Veteran cost: ${runtimeMs / header.entriesCaptured}ms")
        }
        MessageLog.i(TAG, "[INSPIRATION-SCAN] ===== end =====")
    }

    private fun walk(
        scanId: String,
        used: Int,
        hardBound: Int,
        entryLimit: Int,
        observations: MutableList<VeteranInspirationObservation>,
        publishFrameSize: (Int, Int) -> Unit,
    ): InspirationScanTermination {
        val walkStart = System.currentTimeMillis()
        safeTap(RosterScreenKind.ROSTER_LIST, ROSTER_FIRST_CARD_X, ROSTER_FIRST_CARD_Y, "veteran_roster_first_card")
        game.wait(CHEVRON_SETTLE_SECONDS)

        while (true) {
            val (bitmap, screen) = rosterReader.classifyScreenWithRetries(attempts = 3)
            publishFrameSize(bitmap.width, bitmap.height)
            if (screen.kind != RosterScreenKind.UMAMUSUME_DETAILS) {
                MessageLog.w(
                    TAG,
                    "[INSPIRATION-SCAN] Frame ${observations.size} is ${screen.kind}, not the Details dialog " +
                        "(title OCR='${screen.titleRaw}'). Stopping where it stands; no recovery taps are attempted.",
                )
                return InspirationScanTermination.UNEXPECTED_SCREEN
            }

            // The identity header sits above the tab strip and stays on screen whichever tab is
            // active, so the entry's fingerprint is read from the same band the roster walk reads and
            // is not affected by the Inspiration tab being selected.
            val identity = rosterReader.readDetailObservation(bitmap, includeCareerInfo = false, verbose = false)
            val fingerprint = entryFingerprint(identity)
            val index = observations.size
            val observation =
                inspirationReader.capture(
                    scanId = scanId,
                    scanIndex = index,
                    rosterFingerprint = fingerprint,
                    character = identity.character,
                    outfit = identity.outfit,
                    rank = identity.rank,
                    verbose = false,
                )
            observations.add(observation)
            OutcomeCorpus.append(game.myContext, serializeVeteranInspiration(observation), OutcomeCorpus.VETERAN_INSPIRATION_PATH)
            MessageLog.i(
                TAG,
                "[INSPIRATION-SCAN] i=$index ${identity.character ?: "?"} [${identity.outfit ?: "?"}] " +
                    "fp=${fingerprint ?: "UNRESOLVED"} self=${observation.selfFactors.size} " +
                    "ancestors=${observation.legacyAncestors.map { it.factors.size }} " +
                    "complete=${observation.sparkCaptureComplete} termination=${observation.termination} " +
                    "unresolved=${observation.unresolvedFields.size} elapsed=${(System.currentTimeMillis() - walkStart) / 1000}s",
            )

            val count = observations.size
            if (count >= used) return InspirationScanTermination.COUNT_REACHED
            if (entryLimit > 0 && count >= entryLimit) return InspirationScanTermination.ENTRY_LIMIT_REACHED
            if (count >= hardBound) {
                MessageLog.w(TAG, "[INSPIRATION-SCAN] Hard bound reached at $count entries against a bound of $hardBound.")
                return InspirationScanTermination.HARD_BOUND_REACHED
            }

            val current = game.imageUtils.getSourceBitmap()
            val chevron = classifyChevron(SparkPixelSampler { x, y -> current.getPixel(x, y) }, CHEVRON_NEXT_BOX)
            if (chevron == ChevronState.DISABLED) {
                MessageLog.i(TAG, "[INSPIRATION-SCAN] Next chevron classified DISABLED after $count entries: last entry reached.")
                return InspirationScanTermination.CHEVRON_END
            }
            safeTap(RosterScreenKind.UMAMUSUME_DETAILS, DETAIL_NEXT_CHEVRON_X, DETAIL_NEXT_CHEVRON_Y, "veteran_detail_next_chevron")
            game.wait(CHEVRON_SETTLE_SECONDS)
        }
    }

    /** Closes the dialog and re-reads the roster status bar. Returns the post-walk `Registered used`
     * count, or null when it could not be read - which is treated as incompatible, never as unchanged. */
    private fun closeDialogAndReadRoster(): Int? =
        try {
            val (_, screen) = rosterReader.classifyScreenWithRetries(attempts = 2)
            if (screen.kind == RosterScreenKind.UMAMUSUME_DETAILS) {
                safeTap(RosterScreenKind.UMAMUSUME_DETAILS, DETAIL_CLOSE_X, DETAIL_CLOSE_Y, "veteran_detail_close")
                game.wait(CHEVRON_SETTLE_SECONDS)
            }
            val (afterBitmap, afterScreen) = rosterReader.classifyScreenWithRetries(attempts = 3)
            if (afterScreen.kind == RosterScreenKind.ROSTER_LIST) {
                val after = rosterReader.readListState(afterBitmap, afterScreen, verbose = false)
                MessageLog.i(
                    TAG,
                    "[INSPIRATION-SCAN] Post-walk roster state: Registered ${after.registeredUsed ?: "?"}/${after.registeredCapacity ?: "?"} " +
                        "filtersOff=${after.filtersOff ?: "UNREAD"}",
                )
                after.registeredUsed
            } else {
                MessageLog.w(TAG, "[INSPIRATION-SCAN] Could not re-read the roster list after the walk (saw ${afterScreen.kind}).")
                null
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.w(TAG, "[INSPIRATION-SCAN] Exit path failed: $e. Close the dialog by hand.")
            null
        }
}
