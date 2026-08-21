package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.VeteranFactorDomain
import org.json.JSONArray
import org.json.JSONObject

/**
 * The passive lineage telemetry record (`type:"lineage_selected"`): one per career launch that read
 * the populated Legacy Select summary, carrying the six ancestor observations correlated to the
 * career by `launchTransactionId`. Pure model, assembler, and serializer - the navigator reads the
 * pixels and OCR, this turns them into the durable record; PL-4 ingest joins it to the career.
 *
 * What it deliberately does NOT claim: no exact owned-Veteran identity (the legacy flow shows no
 * names, so a portrait can only ever be a probable match), no affinity ranking, no canonical factor
 * ids. The spark-set fingerprint is the load-bearing identity evidence; a portrait/rank is retained
 * as a crop reference and left unresolved rather than fabricated from a stylized badge.
 */
const val LINEAGE_SCHEMA_VERSION: Int = 2

/** How complete the whole capture was. CAPTURED = all six ancestors read with their lead triple and
 * no truncated/ambiguous rows; PARTIAL = something is missing or low-confidence; FAILED = nothing. */
enum class LineageCaptureStatus { CAPTURED, PARTIAL, FAILED }

/** The six ancestor slots, in the fixed top-to-bottom order the summary and the Sparks view present:
 * each parent followed by its two grandparents. */
enum class LineageAncestorRole {
    LEGACY1_PARENT,
    LEGACY1_GRANDPARENT_A,
    LEGACY1_GRANDPARENT_B,
    LEGACY2_PARENT,
    LEGACY2_GRANDPARENT_A,
    LEGACY2_GRANDPARENT_B,
}

/** The canonical six roles in capture order; the traversal fills them by ancestor block index. */
val LINEAGE_ROLE_ORDER: List<LineageAncestorRole> = LineageAncestorRole.entries.toList()

/** Owned vs borrowed. `OWNED` is claimable only in the guests-off, owned-only Auto-Select context
 * (the only one PL-4a proved); a guest-enabled launch is `UNKNOWN` until a rental badge is read. */
enum class LineageOwnership { OWNED, BORROWED, UNKNOWN }

/** How confidently the ancestor is matched to an account-owned Veteran. Never promoted to an exact
 * match without a stable game identifier, which the legacy flow does not expose. */
enum class LineageMatch { PROBABLE_OWNED_MATCH, BORROWED_EXTERNAL, UNRESOLVED }

/** One factor row as observed: pixel-classified kind and stars (authoritative), raw OCR text (not),
 * plus honesty flags for a star read that could not be trusted or a row the list mask truncated. */
data class LineageFactorObservation(
    val kind: SparkRowKind,
    val displayText: String,
    val stars: Int,
    val ambiguous: Boolean,
    val clipped: Boolean,
)

/** One ancestor's raw observation, as the navigator accumulates it from a Sparks-view block. */
data class LineageAncestorObservation(
    val portraitObserved: Boolean,
    val factors: List<LineageFactorObservation>,
)

/** One assembled ancestor in the event: its role/slot, observation, derived ownership/match, the
 * deterministic factor fingerprint, and a 0..1 completeness. */
data class LineageAncestor(
    val role: LineageAncestorRole,
    val slotIndex: Int,
    val portraitObserved: Boolean,
    val rank: String?,
    val factors: List<LineageFactorObservation>,
    /** Raw-OCR set fingerprint, preserved unchanged as historical evidence. Unstable across re-reads. */
    val factorFingerprint: String,
    /** Trusted canonical set fingerprint derived by snapping each factor onto the canonical domain, or
     * null when the domain did not load or any factor is unresolved. This is what cross-links to a
     * Veteran's Inspiration self-fingerprint without the raw OCR jitter. */
    val canonicalFactorFingerprint: String?,
    /** Name-free `kind:stars` set fingerprint, always available; the OCR-free fallback identity. */
    val structuralFactorFingerprint: String,
    /** Whether every factor resolved to a canonical name, so [canonicalFactorFingerprint] is trusted. */
    val factorSetTrusted: Boolean,
    val ownership: LineageOwnership,
    val matchStatus: LineageMatch,
    val probableVeteranId: String?,
    val hasLeadTriple: Boolean,
    val completeness: Double,
)

data class LegacyLineageEvent(
    val schemaVersion: Int,
    val launchTransactionId: String?,
    val ts: Long,
    val scenario: String,
    val trainee: String,
    val overallAffinity: String?,
    val captureStatus: LineageCaptureStatus,
    val ancestors: List<LineageAncestor>,
)

/**
 * A trailing skill-grade marker as OCR renders it. Many skill names end in a small circle, double
 * circle, or star glyph, and no OCR engine reads those reliably: the SAME card read twice comes back
 * as "Medium Straightaways O" and then "Medium Straightaways", and "Victoria por plancha *" then
 * "Victoria por plancha". Five of nineteen Veterans re-read in the PL-R1c validation differed only by
 * one of these.
 *
 * A fingerprint that flips between two values for unchanged evidence is worse than one that cannot
 * tell a circle skill from its double-circle variant, so the marker is stripped from the normalized
 * name. The raw display text keeps whatever was read, and the star count is unaffected.
 */
private val TRAILING_GRADE_MARKER = Regex("""\s+[O0*@()\u00A9\u00B0\u25CB\u25CE\u2605\u2606]+$""")

/** Normalize an OCR factor name for the fingerprint only: trim, collapse whitespace, upper-case, and
 * drop a trailing grade marker OCR cannot read consistently. The raw display text is preserved
 * separately. Matches the PL-3 identity normalization so the two sides agree. */
internal fun normalizeLineageFactorName(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").uppercase().replace(TRAILING_GRADE_MARKER, "")

/** Deterministic fingerprint of an ancestor's factor set: each factor as `kind:NORMNAME:stars`,
 * sorted, joined by `|`. Order-independent and stable, so PL-4 can later match it against a
 * Veteran's kept spark set without depending on read order. */
internal fun ancestorFactorFingerprint(factors: List<LineageFactorObservation>): String =
    factors
        .map { "${it.kind.name.lowercase()}:${normalizeLineageFactorName(it.displayText)}:${it.stars}" }
        .sorted()
        .joinToString("|")

/** A complete ancestor opens stat, aptitude, unique. */
private fun observationHasLeadTriple(factors: List<LineageFactorObservation>): Boolean =
    factors.size >= 3 &&
        factors[0].kind == SparkRowKind.STAT &&
        factors[1].kind == SparkRowKind.APTITUDE &&
        factors[2].kind == SparkRowKind.UNIQUE

/** Per-ancestor completeness: the lead triple is worth most; a full factor set with no truncated or
 * ambiguous rows reaches 1.0. Monotone with evidence, never a gate. */
private fun ancestorCompleteness(factors: List<LineageFactorObservation>): Double {
    if (factors.isEmpty()) return 0.0
    var score = 0.0
    if (observationHasLeadTriple(factors)) score += 0.6
    if (factors.none { it.clipped }) score += 0.2
    if (factors.none { it.ambiguous }) score += 0.2
    return score
}

/**
 * Assemble the lineage event from the ancestor blocks the traversal accumulated in scroll order.
 * Blocks map to [LINEAGE_ROLE_ORDER] positionally - the summary proves exactly six ancestors in that
 * fixed order - so the i-th observed block is the i-th role. Fewer than six observed blocks, any
 * block missing its lead triple, or any clipped/ambiguous row yields PARTIAL; zero blocks yield
 * FAILED. Ownership is OWNED only in the guests-off context.
 */
fun assembleLineageEvent(
    launchTransactionId: String?,
    ts: Long,
    scenario: String,
    trainee: String,
    overallAffinity: String?,
    guestsIncluded: Boolean,
    observedAncestors: List<LineageAncestorObservation>,
    /** The canonical factor domain, so the raw OCR names are also snapped onto canonical identities as
     * a DERIVED interpretation. Null (asset missing) leaves the canonical fingerprint unresolved; the
     * raw fingerprint is always preserved either way. */
    factorDomain: VeteranFactorDomain? = null,
): LegacyLineageEvent {
    val ownership = if (guestsIncluded) LineageOwnership.UNKNOWN else LineageOwnership.OWNED
    val match = if (ownership == LineageOwnership.OWNED) LineageMatch.PROBABLE_OWNED_MATCH else LineageMatch.UNRESOLVED
    val ancestors =
        observedAncestors.take(LINEAGE_ROLE_ORDER.size).mapIndexed { i, obs ->
            // Canonical tokens are derived from the raw OCR, never replacing it. A factor whose name
            // does not resolve leaves the canonical set-fingerprint null (fail closed); the structural
            // kind:stars fingerprint stands regardless.
            val canonicalTokens = obs.factors.map { f -> canonicalFactorToken(f.kind, factorDomain?.resolve(f.displayText, f.kind)?.canonicalName, f.stars) }
            val structuralTokens = obs.factors.map { f -> structuralFactorToken(f.kind, f.stars) }
            LineageAncestor(
                role = LINEAGE_ROLE_ORDER[i],
                slotIndex = i,
                portraitObserved = obs.portraitObserved,
                rank = null,
                factors = obs.factors,
                factorFingerprint = ancestorFactorFingerprint(obs.factors),
                canonicalFactorFingerprint = canonicalFactorSetFingerprint(canonicalTokens),
                structuralFactorFingerprint = structuralFactorSetFingerprint(structuralTokens),
                factorSetTrusted = obs.factors.isNotEmpty() && canonicalTokens.all { it != null },
                ownership = ownership,
                matchStatus = match,
                probableVeteranId = null,
                hasLeadTriple = observationHasLeadTriple(obs.factors),
                completeness = ancestorCompleteness(obs.factors),
            )
        }
    val status =
        when {
            ancestors.isEmpty() -> LineageCaptureStatus.FAILED
            ancestors.size == LINEAGE_ROLE_ORDER.size && ancestors.all { it.completeness >= 1.0 } -> LineageCaptureStatus.CAPTURED
            else -> LineageCaptureStatus.PARTIAL
        }
    return LegacyLineageEvent(
        schemaVersion = LINEAGE_SCHEMA_VERSION,
        launchTransactionId = launchTransactionId,
        ts = ts,
        scenario = scenario,
        trainee = trainee,
        overallAffinity = overallAffinity,
        captureStatus = status,
        ancestors = ancestors,
    )
}

/** Serialize the event to the durable `type:"lineage_selected"` JSON record. */
fun serializeLineageEvent(event: LegacyLineageEvent): JSONObject =
    JSONObject().apply {
        put("type", "lineage_selected")
        put("schemaVersion", event.schemaVersion)
        event.launchTransactionId?.let { put("launchTransactionId", it) }
        put("ts", event.ts)
        put("scenario", event.scenario)
        put("trainee", event.trainee)
        event.overallAffinity?.let { put("overallAffinity", it) }
        put("captureStatus", event.captureStatus.name.lowercase())
        put(
            "ancestors",
            JSONArray().apply {
                event.ancestors.forEach { a ->
                    put(
                        JSONObject().apply {
                            put("role", a.role.name.lowercase())
                            put("slotIndex", a.slotIndex)
                            put("portraitObserved", a.portraitObserved)
                            a.rank?.let { put("rank", it) }
                            put("ownership", a.ownership.name.lowercase())
                            put("matchStatus", a.matchStatus.name.lowercase())
                            a.probableVeteranId?.let { put("probableVeteranId", it) }
                            put("hasLeadTriple", a.hasLeadTriple)
                            put("completeness", a.completeness)
                            put("factorFingerprint", a.factorFingerprint)
                            a.canonicalFactorFingerprint?.let { put("canonicalFactorFingerprint", it) }
                            put("structuralFactorFingerprint", a.structuralFactorFingerprint)
                            put("factorSetTrusted", a.factorSetTrusted)
                            put(
                                "factors",
                                JSONArray().apply {
                                    a.factors.forEach { f ->
                                        put(
                                            JSONObject().apply {
                                                put("kind", f.kind.name.lowercase())
                                                put("displayText", f.displayText)
                                                put("stars", f.stars)
                                                if (f.ambiguous) put("ambiguous", true)
                                                if (f.clipped) put("clipped", true)
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    )
                }
            },
        )
    }
