package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.FactorAcceptancePath
import org.json.JSONArray
import org.json.JSONObject

/**
 * `veteran_inspiration` record: one per Veteran whose Inspiration tab was read, keyed to the roster identity by
 * `rosterFingerprint`. `selfFactors` (what this Veteran passes on) and `legacyAncestors` (the ancestry behind
 * it) stay separate; one flat factor bag would be unusable for a retention decision. Ancestors have no
 * game-stable identifier (no names shown), so they are addressed by position, and ancestor `rank` stays null:
 * the medal is a stylized badge the header-medal classifier does not cover, and a guessed rank is worse than
 * none. Distinct from `lineage_selected` (what a career launch selected), so it writes its own file.
 */
const val VETERAN_INSPIRATION_SCHEMA_VERSION: Int = 2

/** Which column of the two-column grid a factor card occupied; the panel order (stat, aptitude, unique, then whites) is itself evidence. */
enum class InspirationColumn { LEFT, RIGHT }

/**
 * One factor card: kind and stars are pixel-classified (authoritative), the name is OCR snapped onto the canonical
 * factor domain. Raw OCR jitters (~3.5% of names differ on a re-read), so only [canonicalName] feeds the
 * semantic [factorFingerprint].
 */
data class InspirationFactor(
    val rowIndex: Int,
    val column: InspirationColumn,
    val kind: SparkRowKind,
    val displayName: String,
    val stars: Int,
    val ambiguous: Boolean,
    /** Canonical name the raw OCR resolved to, or null (garbage, truncated, off-domain); null fails the semantic fingerprint closed. */
    val canonicalName: String? = null,
    val canonicalPath: FactorAcceptancePath = FactorAcceptancePath.REJECT,
    val canonicalScore: Double = 0.0,
    val canonicalSecondScore: Double? = null,
) {
    val resolved: Boolean get() = canonicalName != null

    val normalizedName: String get() = normalizeLineageFactorName(displayName)

    /** Semantic token `kind:CANONNAME:stars`, or null when unresolved; same format as the lineage canonical token so the two cross-link. */
    val factorFingerprint: String? get() = canonicalFactorToken(kind, canonicalName, stars)

    val structuralFingerprint: String get() = structuralFactorToken(kind, stars)
}

/** One Legacy Origin ancestor block; [ancestorIndex] is its position in this Veteran's list, not a game identifier. */
data class InspirationAncestor(
    val ancestorIndex: Int,
    val portraitObserved: Boolean,
    /** Always null: see the file header. */
    val rank: String?,
    val factors: List<InspirationFactor>,
) {
    val factorFingerprint: String? get() = canonicalFactorSetFingerprint(factors.map { it.factorFingerprint })

    val structuralFingerprint: String get() = structuralFactorSetFingerprint(factors.map { it.structuralFingerprint })

    val factorSetTrusted: Boolean get() = factors.isNotEmpty() && factors.all { it.resolved }
}

enum class InspirationReadTermination {
    REACHED_BOTTOM,

    NO_SCROLL_NEEDED,

    /** The last factor card was seen with empty space below it: the normal ending when a usage history sits below the factors. */
    REACHED_FACTOR_LIST_END,

    SCROLL_BUDGET_EXHAUSTED,

    STALLED,

    PANEL_NOT_READY,

    NOT_AT_TOP,
}

data class InspirationDiagnostics(
    val frames: Int,
    val swipes: Int,
    val startedAtTop: Boolean,
    /** The scrollbar confirmed the bottom of the whole panel; far below the last factor card when a usage history exists, so not required. */
    val reachedBottom: Boolean,
    /** The end of the FACTOR list was positively observed (panel bottom, or the last card with empty space beneath it); this, not content height, proves no factor was left unread. */
    val factorListEndObserved: Boolean,
    val gapFrames: Int,
    val spacingBreaks: Int,
    val alignmentFailures: Int,
    /** Frames whose scrollbar thumb never settled, and frames whose offset therefore came from dead reckoning off the swipe distance. */
    val unsettledFrames: Int,
    val deadReckonedFrames: Int,
    /** Scrollbar content height at rest vs the height the merged rows imply; disagreement beyond [INSPIRATION_CONTENT_HEIGHT_SLACK] means rows are missing. */
    val scrollbarContentHeight: Int?,
    val observedContentHeight: Int?,
    val rowsAccepted: Int,
    val clippedRowsRejected: Int,
    val leadingPartialBlockRows: Int,
    val blocksObserved: Int,
)

/** Max disagreement between the two content-height measurements; kept below one card pitch, which would hide a whole missed row. */
const val INSPIRATION_CONTENT_HEIGHT_SLACK: Int = 45

data class VeteranInspirationObservation(
    val schemaVersion: Int,
    val observedAt: Long,
    val scanId: String,
    val scanIndex: Int,
    /** The roster identity this evidence attaches to; null when its identity fields did not all resolve (factors are still recorded, unattributed). */
    val rosterFingerprint: String?,
    val character: String?,
    val outfit: String?,
    val rank: String?,
    val selfFactors: List<InspirationFactor>,
    val legacyAncestors: List<InspirationAncestor>,
    val selfPortraitObserved: Boolean,
    val termination: InspirationReadTermination,
    val sparkCaptureComplete: Boolean,
    val screenReadCompleteness: Double,
    val unresolvedFields: List<String>,
    val diagnostics: InspirationDiagnostics,
) {
    val selfFactorFingerprint: String? get() = canonicalFactorSetFingerprint(selfFactors.map { it.factorFingerprint })

    val selfStructuralFingerprint: String get() = structuralFactorSetFingerprint(selfFactors.map { it.structuralFingerprint })

    val selfFactorSetTrusted: Boolean get() = selfFactors.isNotEmpty() && selfFactors.all { it.resolved }
}

data class InspirationBlockObservation(val blockIndex: Int, val portraitObserved: Boolean, val factors: List<InspirationFactor>)

/**
 * Block 0 is the Veteran's own Sparks; every later block is a Legacy Origin ancestor, in panel order. A block
 * index of -1 (rows above the first blue stat card) means the traversal did not start at the top; those rows
 * are discarded, not attributed. [sparkCaptureComplete] is the flag consumers gate on and is deliberately
 * strict: a retention decision on a partial factor list is worse than one on none.
 */
fun assembleVeteranInspiration(
    scanId: String,
    scanIndex: Int,
    observedAt: Long,
    rosterFingerprint: String?,
    character: String?,
    outfit: String?,
    rank: String?,
    blocks: List<InspirationBlockObservation>,
    termination: InspirationReadTermination,
    diagnostics: InspirationDiagnostics,
): VeteranInspirationObservation {
    val selfBlock = blocks.firstOrNull { it.blockIndex == 0 }
    val ancestors =
        blocks.filter { it.blockIndex >= 1 }
            .sortedBy { it.blockIndex }
            .mapIndexed { i, block ->
                InspirationAncestor(
                    ancestorIndex = i,
                    portraitObserved = block.portraitObserved,
                    rank = null,
                    factors = block.factors,
                )
            }
    val allFactors = (selfBlock?.factors ?: emptyList()) + ancestors.flatMap { it.factors }

    val unresolved = mutableListOf<String>()
    if (!diagnostics.startedAtTop) unresolved.add("startedAtTop")
    if (diagnostics.gapFrames > 0) unresolved.add("contentGap")
    if (diagnostics.spacingBreaks > 0) unresolved.add("rowSpacing")
    if (diagnostics.leadingPartialBlockRows > 0) unresolved.add("leadingPartialBlock")
    if (selfBlock == null) unresolved.add("selfSparks")
    if (!diagnostics.factorListEndObserved) unresolved.add("factorListEnd")
    for (factor in allFactors) {
        val loc = "${factor.kind.name.lowercase()}:${factor.rowIndex}:${factor.column.name.lowercase()}"
        if (factor.displayName.isEmpty()) unresolved.add("factorName@$loc")
        if (factor.ambiguous) unresolved.add("factorStars@$loc")
        // An unresolved canonical name (truncated, off-domain) is marked for the fingerprint but does NOT gate sparkCaptureComplete: the read was complete.
        if (factor.displayName.isNotEmpty() && !factor.resolved) unresolved.add("factorCanonical@$loc")
    }

    // Neither `reachedBottom` nor a content-height comparison is a check: the scrollbar measures the whole panel,
    // and the usage history below the factors can be far taller than them. `factorListEndObserved` (the last
    // factor card seen with nothing after it) is what matters.
    val checks =
        listOf(
            diagnostics.startedAtTop,
            diagnostics.factorListEndObserved,
            diagnostics.gapFrames == 0,
            diagnostics.spacingBreaks == 0,
            diagnostics.leadingPartialBlockRows == 0,
            selfBlock != null,
            allFactors.isNotEmpty() && allFactors.none { it.displayName.isEmpty() },
            allFactors.none { it.ambiguous },
        )
    val completeness = checks.count { it }.toDouble() / checks.size

    return VeteranInspirationObservation(
        schemaVersion = VETERAN_INSPIRATION_SCHEMA_VERSION,
        observedAt = observedAt,
        scanId = scanId,
        scanIndex = scanIndex,
        rosterFingerprint = rosterFingerprint,
        character = character,
        outfit = outfit,
        rank = rank,
        selfFactors = selfBlock?.factors ?: emptyList(),
        legacyAncestors = ancestors,
        selfPortraitObserved = selfBlock?.portraitObserved ?: false,
        termination = termination,
        sparkCaptureComplete = checks.all { it },
        screenReadCompleteness = completeness,
        unresolvedFields = unresolved,
        diagnostics = diagnostics,
    )
}

private fun serializeFactors(factors: List<InspirationFactor>): JSONArray =
    JSONArray().apply {
        factors.forEach { f ->
            put(
                JSONObject().apply {
                    put("rowIndex", f.rowIndex)
                    put("column", f.column.name.lowercase())
                    put("kind", f.kind.name.lowercase())
                    put("displayName", f.displayName)
                    put("normalizedName", f.normalizedName)
                    put("stars", f.stars)
                    // Canonical identity only when resolved; the structural token is always present and name-free.
                    f.canonicalName?.let { put("canonicalName", it) }
                    put("canonicalPath", f.canonicalPath.name.lowercase())
                    f.factorFingerprint?.let { put("factorFingerprint", it) }
                    put("structuralFingerprint", f.structuralFingerprint)
                    if (f.ambiguous) put("ambiguous", true)
                },
            )
        }
    }

fun serializeVeteranInspiration(o: VeteranInspirationObservation): JSONObject =
    JSONObject().apply {
        put("type", "veteran_inspiration")
        put("schemaVersion", o.schemaVersion)
        put("scanId", o.scanId)
        put("scanIndex", o.scanIndex)
        put("observedAt", o.observedAt)
        o.rosterFingerprint?.let { put("rosterFingerprint", it) }
        o.character?.let { put("character", it) }
        o.outfit?.let { put("outfit", it) }
        o.rank?.let { put("rank", it) }
        put("selfPortraitObserved", o.selfPortraitObserved)
        put("selfFactorCount", o.selfFactors.size)
        o.selfFactorFingerprint?.let { put("selfFactorFingerprint", it) }
        put("selfStructuralFingerprint", o.selfStructuralFingerprint)
        put("selfFactorSetTrusted", o.selfFactorSetTrusted)
        put("selfFactors", serializeFactors(o.selfFactors))
        put(
            "legacyAncestors",
            JSONArray().apply {
                o.legacyAncestors.forEach { a ->
                    put(
                        JSONObject().apply {
                            put("ancestorIndex", a.ancestorIndex)
                            put("portraitObserved", a.portraitObserved)
                            a.rank?.let { put("rank", it) }
                            put("factorCount", a.factors.size)
                            a.factorFingerprint?.let { put("ancestorFactorFingerprint", it) }
                            put("ancestorStructuralFingerprint", a.structuralFingerprint)
                            put("factorSetTrusted", a.factorSetTrusted)
                            put("factors", serializeFactors(a.factors))
                        },
                    )
                }
            },
        )
        put("termination", o.termination.name.lowercase())
        put("sparkCaptureComplete", o.sparkCaptureComplete)
        put("screenReadCompleteness", o.screenReadCompleteness)
        put("unresolvedFields", JSONArray(o.unresolvedFields))
        put(
            "diagnostics",
            JSONObject().apply {
                put("frames", o.diagnostics.frames)
                put("swipes", o.diagnostics.swipes)
                put("startedAtTop", o.diagnostics.startedAtTop)
                put("reachedBottom", o.diagnostics.reachedBottom)
                put("factorListEndObserved", o.diagnostics.factorListEndObserved)
                put("gapFrames", o.diagnostics.gapFrames)
                put("spacingBreaks", o.diagnostics.spacingBreaks)
                put("alignmentFailures", o.diagnostics.alignmentFailures)
                put("unsettledFrames", o.diagnostics.unsettledFrames)
                put("deadReckonedFrames", o.diagnostics.deadReckonedFrames)
                o.diagnostics.scrollbarContentHeight?.let { put("scrollbarContentHeight", it) }
                o.diagnostics.observedContentHeight?.let { put("observedContentHeight", it) }
                put("rowsAccepted", o.diagnostics.rowsAccepted)
                put("clippedRowsRejected", o.diagnostics.clippedRowsRejected)
                put("leadingPartialBlockRows", o.diagnostics.leadingPartialBlockRows)
                put("blocksObserved", o.diagnostics.blocksObserved)
            },
        )
    }

// -- Scan header ---------------------------------------------------------------------------------

enum class InspirationScanTermination {
    COUNT_REACHED,
    CYCLE_CLOSED,
    SINGLE_CARD,
    EMPTY_LIST,
    ENTRY_LIMIT_REACHED,
    /** Legacy diagnostic value; disabled chevrons do not prove Veteran pager completion. */
    CHEVRON_END,
    UNEXPECTED_SCREEN,
    PRECONDITION_FAILED,
    HARD_BOUND_REACHED,
}

/**
 * Binds a batch to ONE current-roster state: `Registered used` is read before the first entry and after the last.
 * [pagerCycleClosed] requires the full traversal and return to the anchor; [snapshotCompatibility] also requires
 * stable unfiltered list facts. Partial batches keep their captures without claiming account-wide membership.
 */
data class VeteranInspirationScanHeader(
    val schemaVersion: Int,
    val scanId: String,
    val startedAt: Long,
    val completedAt: Long,
    val registeredUsedAtStart: Int?,
    val registeredUsedAtEnd: Int?,
    val registeredCapacity: Int?,
    val filtersOff: Boolean?,
    val sortKey: String?,
    val sortDirection: String?,
    val snapshotCompatibility: Boolean,
    val pagerCycleClosed: Boolean,
    val entryLimit: Int,
    val startIndex: Int,
    val entriesCaptured: Int,
    val entriesComplete: Int,
    val terminationReason: InspirationScanTermination,
    val app: String,
    val screenWidth: Int,
    val screenHeight: Int,
)

fun serializeVeteranInspirationScan(h: VeteranInspirationScanHeader): JSONObject =
    JSONObject().apply {
        put("type", "veteran_inspiration_scan")
        put("schemaVersion", h.schemaVersion)
        put("scanId", h.scanId)
        put("startedAt", h.startedAt)
        put("completedAt", h.completedAt)
        h.registeredUsedAtStart?.let { put("registeredUsedAtStart", it) }
        h.registeredUsedAtEnd?.let { put("registeredUsedAtEnd", it) }
        h.registeredCapacity?.let { put("registeredCapacity", it) }
        h.filtersOff?.let { put("filtersOff", it) }
        h.sortKey?.let { put("sortKey", it) }
        h.sortDirection?.let { put("sortDirection", it) }
        put("snapshotCompatibility", h.snapshotCompatibility)
        put("pagerCycleClosed", h.pagerCycleClosed)
        put("entryLimit", h.entryLimit)
        put("startIndex", h.startIndex)
        put("entriesCaptured", h.entriesCaptured)
        put("entriesComplete", h.entriesComplete)
        put("terminationReason", h.terminationReason.name.lowercase())
        put("app", h.app)
        put("screenWidth", h.screenWidth)
        put("screenHeight", h.screenHeight)
    }
