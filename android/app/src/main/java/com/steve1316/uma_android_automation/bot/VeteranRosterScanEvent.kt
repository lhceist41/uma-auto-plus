package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.APTITUDE_ROLES
import com.steve1316.uma_android_automation.utils.RosterIdentityEvidence
import com.steve1316.uma_android_automation.utils.STAT_KEYS
import com.steve1316.uma_android_automation.utils.rosterFingerprint
import org.json.JSONArray
import org.json.JSONObject

/**
 * The read-only Veteran roster scan record types (`type:"roster_scan"` header + `type:"roster_entry"`
 * rows): one header and N entry rows per chevron walk of the `Umamusume Details` dialog. Pure model,
 * assembler, and serializer - [com.steve1316.uma_android_automation.VeteranRosterScanner] drives the
 * screen and reads the pixels, this turns the observations into the durable records, and the offline
 * `src/lib/parentLab/roster.ts` reads them back.
 *
 * Two things are kept strictly apart, because collapsing them is how a roster snapshot starts lying:
 * an **observation** is what the screen showed, and the derived identity/completeness is computed
 * from it. Nothing here infers a field it did not read - an unread field stays null and is named in
 * `unresolvedFields`, and a scan whose enumerated count disagrees with the account's own
 * `Registered used/capacity` is INCOMPLETE no matter how clean every individual entry read.
 */
const val ROSTER_SCAN_SCHEMA_VERSION: Int = 1

/** Why the walk stopped. Recorded verbatim: the reason is what decides whether a short scan is a
 * legitimate bounded run or a failure, and the two must never be confused offline. */
enum class RosterScanTermination {
    /** `entriesEnumerated == displayedRegisteredUsed`. The account's own count is the authority. */
    COUNT_REACHED,

    /** The next chevron classified DISABLED, so the walk is standing on the last entry. */
    CHEVRON_END,

    /** The newly read entry repeats entry 0's fingerprint at index >= 2: the walk wrapped around. */
    WRAPPED,

    /** Three consecutive identical fingerprints: the chevron tap is no longer advancing the dialog. */
    STALLED,

    /** The operator-set bounded development limit (the 5-entry and 20-entry validation runs). */
    ENTRY_LIMIT_REACHED,

    /** The hard bound (capacity + slack) fired before any other condition. Always a failure. */
    HARD_BOUND_REACHED,

    /** A capture after a chevron tap was not the Details dialog. The walk stops where it stands. */
    UNEXPECTED_SCREEN,

    /** A precondition (roster list not found, Registered unreadable, filters not confirmed off)
     * failed before the first tap. Zero entries, zero gestures. */
    PRECONDITION_FAILED,
}

/** Whether the snapshot may be treated as the account's current roster - the retention verdict, equal
 * to `enumerationComplete && identityComplete`. Anything short of TRUSTED_COMPLETE is barred from
 * transfer analysis downstream - see [assembleRosterScan]. This is deliberately NOT the same fact as
 * "the walk covered the whole roster": a scan can enumerate all 257 positions cleanly (enumeration
 * complete) yet leave some entries unidentified (identity incomplete), and collapsing the two is how a
 * count-complete walk gets mislabelled as if it had missed entries. [VeteranRosterScan] carries the
 * two component facts separately for exactly that reason. */
enum class RosterScanCompleteness { TRUSTED_COMPLETE, INCOMPLETE }

/** The roster list status bar as read before the walk. Every field is nullable because "unread" and
 * "read as absent" are different facts and the preconditions depend on telling them apart. */
data class RosterListState(
    val registeredUsed: Int?,
    val registeredCapacity: Int?,
    val filtersOff: Boolean?,
    val sortKey: String?,
    val sortDirection: String?,
)

/** The Career Info block for one entry, when that pass ran. Absent (null observation) is different
 * from present-but-unread (a non-null observation with null fields). */
data class RosterCareerInfoObservation(
    val races: Int?,
    val wins: Int?,
    val fans: Int?,
    val scenario: String?,
    val rating: Int?,
    val dateAcquired: String?,
)

/**
 * What the readers saw before the parsers turned it into (or refused to turn it into) a value.
 *
 * This is evidence, never identity. Nothing here feeds [entryFingerprint], the unresolved-field
 * list, or the completeness verdict, and no consumer may promote a raw string or a near-miss
 * candidate into a field the parser rejected: a raw stat OCR of "1" is a dropped-digit artifact, not
 * a stat of 1. It exists so an unresolved immutable field can be diagnosed from the corpus instead
 * of costing another blind walk of the whole roster.
 *
 * [rawStatOcr] is positional in [STAT_KEYS] order, like [RosterEntryObservation.stats].
 */
data class RosterEntryDiagnostics(
    val rawNameOutfitOcr: String? = null,
    val rawRatingOcr: String? = null,
    val rawStatOcr: List<String?> = emptyList(),
    /** Which costume the name/outfit read came closest to inside the resolved trainee, and how close. */
    val outfitCandidate: String? = null,
    val outfitScore: Double? = null,
    val outfitSecondCandidate: String? = null,
    val outfitSecondScore: Double? = null,
    /** How the outfit was accepted: "strong", "margin", or "reject" (PL-R1b). Lets a margin accept be
     * counted and audited offline without re-deriving it from the scores. */
    val outfitAcceptancePath: String? = null,
    /** The rank medal's colour family, best-correlating tier, and the two template scores. */
    val rankFamily: String? = null,
    val rankChosen: String? = null,
    val rankBestScore: Double? = null,
    val rankSecondScore: Double? = null,
    /** How the rank tier was accepted: "strong", "margin", or "reject" (PL-R1b). */
    val rankAcceptancePath: String? = null,
)

/**
 * One entry exactly as the detail dialog showed it. [stats] and [statGrades] are positional in
 * [STAT_KEYS] order; [aptitudes] is positional in [APTITUDE_ROLES] order. [favoriteState] is the
 * saturation classification of the favorite glyph ("not_set" when the glyph is the pure-grayscale
 * outline, "unknown" when it is a saturated icon this stage deliberately does not identify).
 *
 * [diagnostics] is deliberately outside every derivation below: identity, completeness, and the
 * fingerprint are computed from the parsed fields alone, exactly as they were before it existed.
 */
data class RosterEntryObservation(
    val character: String?,
    val outfit: String?,
    val rank: String?,
    val rating: Int?,
    val stats: List<Int?>,
    val statGrades: List<String?>,
    val aptitudes: List<String?>,
    val favoriteState: String,
    val careerInfo: RosterCareerInfoObservation? = null,
    val diagnostics: RosterEntryDiagnostics? = null,
)

/**
 * One assembled entry: the observation plus what can be derived from it and nothing else.
 *
 * [rosterFingerprint] is non-null only when every immutable-identity feeder read cleanly, so an
 * entry can be counted without being identified. [identityMultiplicity] is how many entries in the
 * SAME scan carry this fingerprint; > 1 is preserved as evidence of a real duplicate or a stalled
 * chevron and is never collapsed (PL-R1 design doc Part 4).
 */
data class RosterScanEntry(
    val scanIndex: Int,
    val observedAt: Long,
    val observation: RosterEntryObservation,
    val rosterFingerprint: String?,
    val readCompleteness: Double,
    val unresolvedFields: List<String>,
    val identityMultiplicity: Int,
)

/** The scan header: the account-level counts, the view state the walk ran under, and the verdict. */
data class VeteranRosterScan(
    val schemaVersion: Int,
    val scanId: String,
    val startedAt: Long,
    val completedAt: Long,
    val list: RosterListState,
    val entryLimit: Int,
    val entriesEnumerated: Int,
    val uniqueFingerprints: Int,
    val unidentifiedCount: Int,
    val duplicateFingerprintCount: Int,
    val countDiscrepancy: Int?,
    val terminationReason: RosterScanTermination,
    /** The walk covered exactly the account's own roster: filters confirmed off, the Registered used
     * count read, that many entries enumerated, and a termination consistent with reaching the end.
     * True even when some of those entries did not identify - enumeration is about coverage, not
     * identity. This is the fact the transfer-analysis bar was hiding when only [completeness] existed. */
    val enumerationComplete: Boolean,
    /** Every enumerated entry resolved to a distinct identity: at least one entry, none unidentified,
     * no repeated fingerprint. Independent of [enumerationComplete] - a bounded 5-entry run can be
     * identity-complete without being enumeration-complete, and the full walk here is the reverse. */
    val identityComplete: Boolean,
    /** The retention verdict, `enumerationComplete && identityComplete`. Kept as the [completeness]
     * enum for wire and reader back-compat; this boolean names it as the doc's `trustedForRetention`. */
    val trustedForRetention: Boolean,
    val completeness: RosterScanCompleteness,
    /** How many failure-evidence crops the walk wrote for this scan. Zero when the diagnostic was
     * not armed, and zero on a clean walk even when it was: crops are written only for an entry's
     * unresolved immutable fields. Reported so a scan can never look like it preserved evidence it
     * did not. */
    val evidenceCropCount: Int,
    val appVersion: String,
    val screenWidth: Int,
    val screenHeight: Int,
)

/** The assembled scan: one header plus its entries, ready for serialization. */
data class AssembledRosterScan(val header: VeteranRosterScan, val entries: List<RosterScanEntry>)

/** The identity feeders. An entry missing any of these cannot be fingerprinted at all. Public so the
 * walk can decide which fields are worth preserving failure evidence for without re-deriving the
 * list and drifting from it. */
fun identityUnresolved(o: RosterEntryObservation): List<String> =
    buildList {
        if (o.character == null) add("character")
        if (o.outfit == null) add("outfit")
        if (o.rank == null) add("rank")
        if (o.rating == null) add("rating")
        o.stats.forEachIndexed { i, v -> if (v == null) add("stat_${STAT_KEYS.getOrElse(i) { i.toString() }}") }
        o.aptitudes.forEachIndexed { i, v -> if (v == null) add("aptitude_${APTITUDE_ROLES.getOrElse(i) { i.toString() }}") }
    }

/** Everything else that was attempted. Missing here degrades completeness but not identity. */
private fun auxiliaryUnresolved(o: RosterEntryObservation): List<String> =
    buildList {
        o.statGrades.forEachIndexed { i, v -> if (v == null) add("statGrade_${STAT_KEYS.getOrElse(i) { i.toString() }}") }
        val c = o.careerInfo ?: return@buildList
        if (c.races == null) add("careerRaces")
        if (c.wins == null) add("careerWins")
        if (c.fans == null) add("careerFans")
        if (c.scenario == null) add("careerScenario")
        if (c.rating == null) add("careerRating")
        if (c.dateAcquired == null) add("careerDateAcquired")
    }

/** Total fields the reader attempted for this observation, used as the completeness denominator. */
private fun attemptedFieldCount(o: RosterEntryObservation): Int =
    4 + o.stats.size + o.statGrades.size + o.aptitudes.size + if (o.careerInfo != null) 6 else 0

/**
 * The entry's immutable identity fingerprint, or null when any feeder is unread. Delegates to the
 * shared [rosterFingerprint] so the device hash stays byte-identical to the offline one.
 */
fun entryFingerprint(o: RosterEntryObservation): String? {
    if (identityUnresolved(o).isNotEmpty()) return null
    return rosterFingerprint(
        RosterIdentityEvidence(
            character = o.character!!,
            outfit = o.outfit!!,
            rank = o.rank!!,
            rating = o.rating!!,
            stats = o.stats.filterNotNull(),
            aptitudes = o.aptitudes.filterNotNull(),
        ),
    )
}

/**
 * Assembles the durable scan from the observations the walk collected, in traversal order.
 *
 * Completeness is decided structurally, never by how the walk "felt": the scan is TRUSTED_COMPLETE
 * only when filters were confirmed off, the account's displayed used count was read, the walk
 * enumerated exactly that many entries, every entry was identified, no fingerprint repeated, and the
 * walk stopped for a reason consistent with reaching the end. A bounded development run therefore
 * reports INCOMPLETE by construction, which is the point.
 */
fun assembleRosterScan(
    scanId: String,
    startedAt: Long,
    completedAt: Long,
    list: RosterListState,
    entryLimit: Int,
    observations: List<Pair<Long, RosterEntryObservation>>,
    termination: RosterScanTermination,
    appVersion: String,
    screenWidth: Int,
    screenHeight: Int,
    evidenceCropCount: Int = 0,
): AssembledRosterScan {
    val fingerprints = observations.map { entryFingerprint(it.second) }
    val multiplicity = fingerprints.filterNotNull().groupingBy { it }.eachCount()

    val entries =
        observations.mapIndexed { index, (observedAt, o) ->
            val unresolved = identityUnresolved(o) + auxiliaryUnresolved(o)
            val attempted = attemptedFieldCount(o)
            RosterScanEntry(
                scanIndex = index,
                observedAt = observedAt,
                observation = o,
                rosterFingerprint = fingerprints[index],
                readCompleteness = if (attempted == 0) 0.0 else (attempted - unresolved.size).toDouble() / attempted,
                unresolvedFields = unresolved,
                identityMultiplicity = fingerprints[index]?.let { multiplicity[it] ?: 1 } ?: 1,
            )
        }

    val enumerated = entries.size
    val unique = multiplicity.size
    val unidentified = fingerprints.count { it == null }
    val duplicates = fingerprints.filterNotNull().size - unique
    val used = list.registeredUsed
    val terminatedAtEnd = termination == RosterScanTermination.COUNT_REACHED || termination == RosterScanTermination.CHEVRON_END
    // Two orthogonal facts, never one. Enumeration is about coverage (did the walk visit exactly the
    // account's own count of positions, under a confirmed filter state, ending at a real end);
    // identity is about resolution (did every visited position resolve to a distinct Veteran). The
    // retention verdict needs both, but each is recorded on its own so a count-complete walk with
    // unread fields reads as enumeration-complete rather than being lumped in with a walk that
    // actually missed entries.
    val enumerationComplete = list.filtersOff == true && used != null && enumerated == used && terminatedAtEnd
    val identityComplete = enumerated > 0 && unidentified == 0 && duplicates == 0
    val trustedForRetention = enumerationComplete && identityComplete

    return AssembledRosterScan(
        header =
            VeteranRosterScan(
                schemaVersion = ROSTER_SCAN_SCHEMA_VERSION,
                scanId = scanId,
                startedAt = startedAt,
                completedAt = completedAt,
                list = list,
                entryLimit = entryLimit,
                entriesEnumerated = enumerated,
                uniqueFingerprints = unique,
                unidentifiedCount = unidentified,
                duplicateFingerprintCount = duplicates,
                countDiscrepancy = used?.let { enumerated - it },
                terminationReason = termination,
                enumerationComplete = enumerationComplete,
                identityComplete = identityComplete,
                trustedForRetention = trustedForRetention,
                completeness = if (trustedForRetention) RosterScanCompleteness.TRUSTED_COMPLETE else RosterScanCompleteness.INCOMPLETE,
                evidenceCropCount = evidenceCropCount,
                appVersion = appVersion,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
            ),
        entries = entries,
    )
}

/** Serializes the scan header to its durable `type:"roster_scan"` record. */
fun serializeRosterScanHeader(h: VeteranRosterScan): JSONObject =
    JSONObject().apply {
        put("type", "roster_scan")
        put("schemaVersion", h.schemaVersion)
        put("scanId", h.scanId)
        put("startedAt", h.startedAt)
        put("completedAt", h.completedAt)
        h.list.registeredUsed?.let { put("displayedRegisteredUsed", it) }
        h.list.registeredCapacity?.let { put("displayedRegisteredCapacity", it) }
        h.list.filtersOff?.let { put("filtersOff", it) }
        h.list.sortKey?.let { put("sortKey", it) }
        h.list.sortDirection?.let { put("sortDirection", it) }
        put("entryLimit", h.entryLimit)
        put("entriesEnumerated", h.entriesEnumerated)
        put("uniqueFingerprints", h.uniqueFingerprints)
        put("unidentifiedCount", h.unidentifiedCount)
        put("duplicateFingerprintCount", h.duplicateFingerprintCount)
        h.countDiscrepancy?.let { put("countDiscrepancy", it) }
        put("terminationReason", h.terminationReason.name.lowercase())
        put("enumerationComplete", h.enumerationComplete)
        put("identityComplete", h.identityComplete)
        put("trustedForRetention", h.trustedForRetention)
        put("completeness", h.completeness.name.lowercase())
        put("evidenceCropCount", h.evidenceCropCount)
        put("app", h.appVersion)
        put("screenWidth", h.screenWidth)
        put("screenHeight", h.screenHeight)
    }

/** Serializes one assembled entry to its durable `type:"roster_entry"` record. */
fun serializeRosterScanEntry(scanId: String, e: RosterScanEntry): JSONObject {
    val o = e.observation
    return JSONObject().apply {
        put("type", "roster_entry")
        put("schemaVersion", ROSTER_SCAN_SCHEMA_VERSION)
        put("scanId", scanId)
        put("scanIndex", e.scanIndex)
        put("observedAt", e.observedAt)
        o.character?.let { put("character", it) }
        o.outfit?.let { put("outfit", it) }
        o.rank?.let { put("rank", it) }
        o.rating?.let { put("rating", it) }
        put(
            "stats",
            JSONObject().apply { STAT_KEYS.forEachIndexed { i, k -> o.stats.getOrNull(i)?.let { put(k, it) } } },
        )
        put(
            "statGrades",
            JSONObject().apply { STAT_KEYS.forEachIndexed { i, k -> o.statGrades.getOrNull(i)?.let { put(k, it) } } },
        )
        put(
            "aptitudes",
            JSONObject().apply { APTITUDE_ROLES.forEachIndexed { i, k -> o.aptitudes.getOrNull(i)?.let { put(k, it) } } },
        )
        put("favoriteState", o.favoriteState)
        // Protection is deliberately never inferred from the favorite glyph: a memo also protects a
        // Veteran and is not visible here, so the only positive answer comes from the PL-R1e filter
        // partition. Until that runs, unknown means protected (PL-R1 design doc Part 6).
        put("protectionState", "unknown")
        o.careerInfo?.let { c ->
            put(
                "careerInfo",
                JSONObject().apply {
                    c.races?.let { put("races", it) }
                    c.wins?.let { put("wins", it) }
                    c.fans?.let { put("fans", it) }
                    c.scenario?.let { put("scenario", it) }
                    c.rating?.let { put("rating", it) }
                    c.dateAcquired?.let { put("dateAcquired", it) }
                },
            )
        }
        e.rosterFingerprint?.let { put("rosterFingerprint", it) }
        // Failure evidence, plus the margin-accepted rows (PL-R1b). A strong, fully-resolved entry
        // needs none - emitting raw OCR for all 257 rows would bury the rows that matter. But a margin
        // accept resolved BELOW the absolute floor, so its scores are the audit trail that lets a
        // strong-vs-margin count and a wrong-margin-decision review happen offline without another walk.
        val marginAccepted = o.diagnostics?.let { it.outfitAcceptancePath == "margin" || it.rankAcceptancePath == "margin" } == true
        if (identityUnresolved(o).isNotEmpty() || marginAccepted) {
            o.diagnostics?.let { d -> put("diagnostics", serializeRosterEntryDiagnostics(d)) }
        }
        put("readCompleteness", e.readCompleteness)
        put("identityMultiplicity", e.identityMultiplicity)
        put("unresolvedFields", JSONArray().apply { e.unresolvedFields.forEach { put(it) } })
    }
}

/** Serializes the read evidence. Every field is omitted when absent, so the record carries only what
 * was actually observed and an unread field is never rendered as an empty string. */
fun serializeRosterEntryDiagnostics(d: RosterEntryDiagnostics): JSONObject =
    JSONObject().apply {
        d.rawNameOutfitOcr?.let { put("rawNameOutfitOcr", it) }
        d.rawRatingOcr?.let { put("rawRatingOcr", it) }
        if (d.rawStatOcr.any { it != null }) {
            put(
                "rawStatOcr",
                JSONObject().apply { STAT_KEYS.forEachIndexed { i, k -> d.rawStatOcr.getOrNull(i)?.let { put(k, it) } } },
            )
        }
        d.outfitCandidate?.let { put("outfitCandidate", it) }
        d.outfitScore?.let { put("outfitScore", it) }
        d.outfitSecondCandidate?.let { put("outfitSecondCandidate", it) }
        d.outfitSecondScore?.let { put("outfitSecondScore", it) }
        d.outfitAcceptancePath?.let { put("outfitAcceptancePath", it) }
        d.rankFamily?.let { put("rankFamily", it) }
        d.rankChosen?.let { put("rankChosen", it) }
        d.rankBestScore?.let { put("rankBestScore", it) }
        d.rankSecondScore?.let { put("rankSecondScore", it) }
        d.rankAcceptancePath?.let { put("rankAcceptancePath", it) }
    }
