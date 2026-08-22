package com.steve1316.uma_android_automation

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.InspirationScanTermination
import com.steve1316.uma_android_automation.bot.RosterEntryObservation
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
 * Bounded per-Veteran capture attempts. The dominant incomplete-read cause is a transient frame-merge
 * gap on the longest-ancestry panels (Gold Ship, Maruzensky, Agnes Tachyon): across the PL-R1c
 * validation corpus the SAME Veteran read complete on one pass and gapped on another, so re-capturing
 * resamples it rather than accepting a partial factor list. Three attempts cleared every repeated
 * incomplete observed there. A canonical-resolution failure (a name that read but did not snap onto the
 * domain) is deliberately NOT a retry trigger: the read was complete, re-OCRing a deterministic misread
 * does not improve it, and widening the domain is a separate offline fix.
 */
private const val MAX_CAPTURE_ATTEMPTS = 3

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
     * Runs one batch. [entryLimit] caps how many Veterans are CAPTURED (the staged 1 / 3 / 20 runs);
     * 0 walks to the end of the roster. [startIndex] resumes the walk after a stop: the roster is
     * ordered deterministically (Rating/Desc, Filters OFF), so the walk chevrons past the first
     * [startIndex] entries WITHOUT capturing and begins capturing at that position, which lets a
     * long crawl continue after a host crash or accessibility rebind without redoing the entries it
     * already persisted. Park the game on the Veteran Roster list with Filters: OFF first.
     */
    fun runScan(entryLimit: Int, startIndex: Int = 0) {
        val startedAt = System.currentTimeMillis()
        val scanId = "insp-$startedAt-${java.util.UUID.randomUUID().toString().substring(0, 8)}"
        MessageLog.i(
            TAG,
            "[INSPIRATION-SCAN] ===== Veteran Inspiration capture scanId=$scanId " +
                "entryLimit=${if (entryLimit > 0) entryLimit else "none"} startIndex=$startIndex " +
                "maxAttempts=$MAX_CAPTURE_ATTEMPTS =====",
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
                walk(scanId, used, hardBound, entryLimit, startIndex, observations) { w, h ->
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
        startIndex: Int,
        observations: MutableList<VeteranInspirationObservation>,
        publishFrameSize: (Int, Int) -> Unit,
    ): InspirationScanTermination {
        val walkStart = System.currentTimeMillis()
        safeTap(RosterScreenKind.ROSTER_LIST, ROSTER_FIRST_CARD_X, ROSTER_FIRST_CARD_Y, "veteran_roster_first_card")
        game.wait(CHEVRON_SETTLE_SECONDS)

        // Entries already visited on the roster, capturing or not. Chevron-advancing past the first
        // [startIndex] resumes a long crawl; every visited entry counts against the roster size and the
        // hard bound so the walk still terminates against the same fences as a from-scratch run.
        var visited = 0

        while (true) {
            val (bitmap, screen) = rosterReader.classifyScreenWithRetries(attempts = 3)
            publishFrameSize(bitmap.width, bitmap.height)
            if (screen.kind != RosterScreenKind.UMAMUSUME_DETAILS) {
                MessageLog.w(
                    TAG,
                    "[INSPIRATION-SCAN] Frame at visited=$visited (captured ${observations.size}) is ${screen.kind}: " +
                        "not the Details dialog (title OCR='${screen.titleRaw}'). Stopping where it stands; no recovery taps are attempted.",
                )
                return InspirationScanTermination.UNEXPECTED_SCREEN
            }

            val skipping = visited < startIndex
            if (!skipping) {
                // The identity header sits above the tab strip and stays on screen whichever tab is
                // active, so the entry's fingerprint is read from the same band the roster walk reads
                // and is not affected by the Inspiration tab being selected.
                val identity = rosterReader.readDetailObservation(bitmap, includeCareerInfo = false, verbose = false)
                val fingerprint = entryFingerprint(identity)
                captureWithRetries(scanId, visited, fingerprint, identity, walkStart, observations)
            } else if (visited == 0) {
                MessageLog.i(TAG, "[INSPIRATION-SCAN] Resuming: chevron-advancing past $startIndex entry(s) before the first capture.")
            }

            visited++
            val captured = observations.size
            if (visited >= used) return InspirationScanTermination.COUNT_REACHED
            if (entryLimit > 0 && captured >= entryLimit) return InspirationScanTermination.ENTRY_LIMIT_REACHED
            if (visited >= hardBound) {
                MessageLog.w(TAG, "[INSPIRATION-SCAN] Hard bound reached at $visited visited entries against a bound of $hardBound.")
                return InspirationScanTermination.HARD_BOUND_REACHED
            }

            val current = game.imageUtils.getSourceBitmap()
            val chevron = classifyChevron(SparkPixelSampler { x, y -> current.getPixel(x, y) }, CHEVRON_NEXT_BOX)
            if (chevron == ChevronState.DISABLED) {
                MessageLog.i(TAG, "[INSPIRATION-SCAN] Next chevron classified DISABLED after $visited visited entries: last entry reached.")
                return InspirationScanTermination.CHEVRON_END
            }
            safeTap(RosterScreenKind.UMAMUSUME_DETAILS, DETAIL_NEXT_CHEVRON_X, DETAIL_NEXT_CHEVRON_Y, "veteran_detail_next_chevron")
            game.wait(CHEVRON_SETTLE_SECONDS)
        }
    }

    /**
     * Captures one parked Veteran, retrying a reliability failure up to [MAX_CAPTURE_ATTEMPTS] times.
     *
     * Every attempt is appended to the corpus as append-only evidence; nothing is overwritten, and the
     * offline resolver re-derives the best observation from the whole set by [rosterFingerprint]. The
     * in-memory [observations] list keeps only the best attempt for this Veteran, so the batch header's
     * captured/complete counts remain one-per-Veteran. Retrying stops as soon as a read is
     * [VeteranInspirationObservation.sparkCaptureComplete]: that is the reliability bar, and a complete
     * read that merely failed to canonicalize a name is not improved by re-OCRing it.
     */
    private fun captureWithRetries(
        scanId: String,
        scanIndex: Int,
        fingerprint: String?,
        identity: RosterEntryObservation,
        walkStart: Long,
        observations: MutableList<VeteranInspirationObservation>,
    ) {
        var best: VeteranInspirationObservation? = null
        for (attempt in 1..MAX_CAPTURE_ATTEMPTS) {
            val observation =
                inspirationReader.capture(
                    scanId = scanId,
                    scanIndex = scanIndex,
                    rosterFingerprint = fingerprint,
                    character = identity.character,
                    outfit = identity.outfit,
                    rank = identity.rank,
                    verbose = false,
                )
            OutcomeCorpus.append(game.myContext, serializeVeteranInspiration(observation), OutcomeCorpus.VETERAN_INSPIRATION_PATH)
            best = betterObservation(best, observation)
            MessageLog.i(
                TAG,
                "[INSPIRATION-SCAN] i=$scanIndex attempt=$attempt/$MAX_CAPTURE_ATTEMPTS ${identity.character ?: "?"} " +
                    "[${identity.outfit ?: "?"}] fp=${fingerprint ?: "UNRESOLVED"} self=${observation.selfFactors.size} " +
                    "ancestors=${observation.legacyAncestors.map { it.factors.size }} " +
                    "complete=${observation.sparkCaptureComplete} selfTrusted=${observation.selfFactorSetTrusted} " +
                    "termination=${observation.termination} unresolved=${observation.unresolvedFields.size} " +
                    "elapsed=${(System.currentTimeMillis() - walkStart) / 1000}s",
            )
            if (observation.sparkCaptureComplete) break
            if (attempt < MAX_CAPTURE_ATTEMPTS) {
                MessageLog.w(
                    TAG,
                    "[INSPIRATION-SCAN] i=$scanIndex incomplete (${observation.termination}, unresolved=" +
                        "${observation.unresolvedFields.joinToString(",").ifEmpty { "none" }}); re-capturing.",
                )
            }
        }
        val chosen = best!!
        observations.add(chosen)
        if (!chosen.sparkCaptureComplete) {
            MessageLog.w(
                TAG,
                "[INSPIRATION-SCAN] i=$scanIndex UNRESOLVED after $MAX_CAPTURE_ATTEMPTS attempt(s): best is " +
                    "complete=${chosen.sparkCaptureComplete} selfTrusted=${chosen.selfFactorSetTrusted} " +
                    "termination=${chosen.termination} unresolved=${chosen.unresolvedFields.joinToString(",").ifEmpty { "none" }}.",
            )
        }
    }

    /**
     * Deterministically picks the better of two attempts for the same Veteran. A complete read beats an
     * incomplete one; among reads of equal completeness a self-trusted set beats an untrusted one; then
     * more resolved factors, then more factors read; ties keep the incumbent (the earlier attempt). This
     * only chooses which attempt represents the Veteran in the batch header - the corpus keeps them all.
     */
    private fun betterObservation(current: VeteranInspirationObservation?, candidate: VeteranInspirationObservation): VeteranInspirationObservation {
        if (current == null) return candidate
        fun rank(o: VeteranInspirationObservation) =
            listOf(
                if (o.sparkCaptureComplete) 1 else 0,
                if (o.selfFactorSetTrusted) 1 else 0,
                o.selfFactors.count { it.resolved } + o.legacyAncestors.sumOf { a -> a.factors.count { it.resolved } },
                o.selfFactors.size + o.legacyAncestors.sumOf { it.factors.size },
            )
        val rc = rank(current)
        val rn = rank(candidate)
        for (i in rc.indices) {
            if (rn[i] > rc[i]) return candidate
            if (rn[i] < rc[i]) return current
        }
        return current
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
