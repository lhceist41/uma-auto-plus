package com.steve1316.uma_android_automation.bot

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Veteran Inspiration observation record (`type:"veteran_inspiration"`): one per Veteran whose
 * `Umamusume Details` -> Inspiration tab was read, carrying the Veteran's own Sparks and the factor
 * blocks of its two Legacy Origin ancestors, keyed to the roster identity by `rosterFingerprint`.
 *
 * Two things this deliberately keeps apart, because they are different facts about inheritance:
 *  - `selfFactors` is what THIS Veteran can pass on; and
 *  - `legacyAncestors` is the ancestry already sitting behind it.
 * Flattening them into one factor bag would make both unusable for a later retention decision, which
 * is exactly what this record exists to feed.
 *
 * Two things it deliberately does NOT claim. There is no game-stable ancestor identifier: the panel
 * shows an ancestor's portrait and rank medal but no name, so an ancestor is addressed only by its
 * position in this Veteran's own list. And ancestor `rank` stays null: the medal here is a small
 * stylized badge at a position the calibrated header-medal classifier does not cover, and a guessed
 * rank is worse than an absent one.
 *
 * Separate from `lineage_selected` (PL-4) on purpose. That record is what a career LAUNCH selected;
 * this one is what a REGISTERED Veteran carries. They are different evidence sources about different
 * moments and must stay distinguishable, so this writes its own file and never overwrites that one.
 */
const val VETERAN_INSPIRATION_SCHEMA_VERSION: Int = 1

/** Which column of the two-column grid a factor card occupied. Preserved because the panel's order is
 * deterministic (stat, aptitude, unique, then the white factors in reading order) and that order is
 * itself evidence. */
enum class InspirationColumn { LEFT, RIGHT }

/** One factor card as read: kind and stars are pixel-classified (authoritative), the name is OCR. */
data class InspirationFactor(
    /** Zero-based grid row within the block, in reading order. */
    val rowIndex: Int,
    val column: InspirationColumn,
    val kind: SparkRowKind,
    /** Raw OCR text, trimmed. Empty when the name did not read; never inferred from the kind. */
    val displayName: String,
    val stars: Int,
    val ambiguous: Boolean,
) {
    /** Whitespace-collapsed, upper-cased name, matching PL-4's lineage normalization so a factor read
     * from this screen and the same factor read from the Legacy Select view normalize identically. */
    val normalizedName: String get() = normalizeLineageFactorName(displayName)

    /** The deterministic per-factor token: `kind:NORMNAME:stars`. Byte-identical to the component
     * [ancestorFactorFingerprint] builds its set digest from, so the two sources cross-link directly. */
    val factorFingerprint: String get() = "${kind.name.lowercase()}:$normalizedName:$stars"

    /** The PL-4 observation shape, so the shared fingerprint helper is genuinely reused rather than
     * re-implemented against a second, drifting definition. */
    internal fun asLineageObservation(): LineageFactorObservation =
        LineageFactorObservation(kind, displayName, stars, ambiguous, clipped = false)
}

/** One Legacy Origin ancestor block. [ancestorIndex] is its position in this Veteran's own list and
 * is not a game identifier. */
data class InspirationAncestor(
    val ancestorIndex: Int,
    val portraitObserved: Boolean,
    /** Always null: see the file header. */
    val rank: String?,
    val factors: List<InspirationFactor>,
) {
    val factorFingerprint: String get() = ancestorFactorFingerprint(factors.map { it.asLineageObservation() })
}

/** Why the traversal of one Veteran's panel stopped. */
enum class InspirationReadTermination {
    /** The scrollbar reached the bottom of the content. The normal, complete ending. */
    REACHED_BOTTOM,

    /** The list fits in the viewport, so one frame was the whole of it. */
    NO_SCROLL_NEEDED,

    /** The last factor card was seen with empty space below it. The normal ending for a Veteran that
     * has an inspiration-usage history below its factors, which most do. */
    REACHED_FACTOR_LIST_END,

    /** The bounded swipe budget ran out before the bottom. */
    SCROLL_BUDGET_EXHAUSTED,

    /** Two consecutive swipes moved neither the scrollbar nor the content. */
    STALLED,

    /** The Inspiration tab was not open, or the panel showed no factor cards at all. */
    PANEL_NOT_READY,

    /** The traversal did not begin at the top of the content, so it cannot claim to have seen it all. */
    NOT_AT_TOP,
}

/** The traversal's own measurements, kept so an incomplete read can be diagnosed from the corpus
 * rather than by re-walking the roster. */
data class InspirationDiagnostics(
    val frames: Int,
    val swipes: Int,
    val startedAtTop: Boolean,
    /** The scrollbar confirmed the bottom of the whole panel - which for a Veteran with an
     * inspiration-usage history is far below the last factor card, and is not required. */
    val reachedBottom: Boolean,
    /** The end of the FACTOR list was positively observed: either the panel bottom, or a frame showing
     * the last card with empty space beneath it. This, not the content height, is the proof that no
     * factor was left unread. */
    val factorListEndObserved: Boolean,
    val gapFrames: Int,
    val spacingBreaks: Int,
    val alignmentFailures: Int,
    /** Frames whose scrollbar thumb never settled to the length measured at rest, and frames whose
     * offset therefore came from dead reckoning off the swipe distance instead of the scrollbar. */
    val unsettledFrames: Int,
    val deadReckonedFrames: Int,
    /** Content height the scrollbar reported with the panel at rest, and the height the merged rows
     * imply. Disagreement beyond [INSPIRATION_CONTENT_HEIGHT_SLACK] means rows are missing off one end. */
    val scrollbarContentHeight: Int?,
    val observedContentHeight: Int?,
    val rowsAccepted: Int,
    val clippedRowsRejected: Int,
    val leadingPartialBlockRows: Int,
    val blocksObserved: Int,
)

/** How far the two independent content-height measurements may disagree before the read is called
 * incomplete. One card pitch would already hide a whole missed row, so the bound sits well below it. */
const val INSPIRATION_CONTENT_HEIGHT_SLACK: Int = 45

/** One Veteran's assembled Inspiration observation. */
data class VeteranInspirationObservation(
    val schemaVersion: Int,
    val observedAt: Long,
    val scanId: String,
    val scanIndex: Int,
    /** The roster identity this evidence attaches to. Null when the entry's own identity fields did
     * not all resolve, in which case the factors are still recorded but cannot be attributed. */
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
    val selfFactorFingerprint: String get() = ancestorFactorFingerprint(selfFactors.map { it.asLineageObservation() })
}

/** One block of factor rows as the traversal segmented them, before roles are assigned. */
data class InspirationBlockObservation(val blockIndex: Int, val portraitObserved: Boolean, val factors: List<InspirationFactor>)

/**
 * Assembles one Veteran's observation from the segmented blocks and the traversal's diagnostics.
 *
 * Block 0 is the Veteran's own Sparks; every later block is a Legacy Origin ancestor, in panel order.
 * A block index of -1 (rows above the first blue stat card) is a traversal that did not start at the
 * top; it is counted in the diagnostics and its rows are discarded rather than attributed to anyone.
 *
 * [sparkCaptureComplete] is the single flag a consumer should gate on, and it is deliberately strict:
 * the traversal started at the top, reached the bottom, merged with no gap and no irregular row
 * spacing, agreed with the scrollbar on the total content height, saw at least one block with no
 * leading partial, read every factor name, and read no ambiguous star. Anything less is an incomplete
 * picture of what this Veteran can pass on, and a retention decision made on a partial factor list is
 * worse than one made on none.
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
        if (factor.displayName.isEmpty()) unresolved.add("factorName@${factor.kind.name.lowercase()}:${factor.rowIndex}:${factor.column.name.lowercase()}")
        if (factor.ambiguous) unresolved.add("factorStars@${factor.kind.name.lowercase()}:${factor.rowIndex}:${factor.column.name.lowercase()}")
    }

    // Neither `reachedBottom` nor a content-height comparison is one of the checks, and both were
    // tried first. The scrollbar measures the WHOLE panel, and below the factors sits an
    // inspiration-usage history that can be an order of magnitude taller than them, so the panel's
    // height says nothing about whether every factor was read and its bottom is not worth scrolling
    // to. `factorListEndObserved` is the fact that actually matters: the last factor card was seen
    // with nothing after it.
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
                    put("factorFingerprint", f.factorFingerprint)
                    if (f.ambiguous) put("ambiguous", true)
                },
            )
        }
    }

/** Serializes one Veteran's observation to its durable `type:"veteran_inspiration"` record. */
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
        put("selfFactorFingerprint", o.selfFactorFingerprint)
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
                            put("ancestorFactorFingerprint", a.factorFingerprint)
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

/** Why a multi-Veteran Inspiration capture stopped. */
enum class InspirationScanTermination {
    COUNT_REACHED,
    ENTRY_LIMIT_REACHED,
    CHEVRON_END,
    UNEXPECTED_SCREEN,
    PRECONDITION_FAILED,
    HARD_BOUND_REACHED,
}

/**
 * The header for one batch of Inspiration captures.
 *
 * It binds the batch to ONE current-roster state. The roster's `Registered used` count is read before
 * the first entry and again after the last, and [snapshotCompatibility] is false when they differ:
 * a Veteran registered or released mid-capture shifts every later chevron position, so silently
 * merging the two halves would attach one Veteran's factors to another Veteran's identity. That is a
 * corruption a later stage could not detect, which is why it is decided here and recorded, not
 * inferred afterwards.
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
    val entryLimit: Int,
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
        put("entryLimit", h.entryLimit)
        put("entriesCaptured", h.entriesCaptured)
        put("entriesComplete", h.entriesComplete)
        put("terminationReason", h.terminationReason.name.lowercase())
        put("app", h.app)
        put("screenWidth", h.screenWidth)
        put("screenHeight", h.screenHeight)
    }
