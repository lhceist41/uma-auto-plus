package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.VeteranFactorDomain
import org.json.JSONArray
import org.json.JSONObject

/**
 * Passive `lineage_selected` telemetry record: the six ancestor observations read from the Legacy Select
 * summary, correlated to the career by `launchTransactionId`. It claims no exact owned-Veteran identity
 * (the legacy flow shows no names); the spark-set fingerprint is the identity evidence.
 */
const val LINEAGE_SCHEMA_VERSION: Int = 2

/** CAPTURED = all six ancestors read with lead triple and no truncated/ambiguous rows; PARTIAL = something
 * missing or low-confidence; FAILED = nothing. */
enum class LineageCaptureStatus { CAPTURED, PARTIAL, FAILED }

/** The six ancestor slots in summary order: each parent followed by its two grandparents. */
enum class LineageAncestorRole {
    LEGACY1_PARENT,
    LEGACY1_GRANDPARENT_A,
    LEGACY1_GRANDPARENT_B,
    LEGACY2_PARENT,
    LEGACY2_GRANDPARENT_A,
    LEGACY2_GRANDPARENT_B,
}

val LINEAGE_ROLE_ORDER: List<LineageAncestorRole> = LineageAncestorRole.entries.toList()

/** `OWNED` is claimable only in the guests-off, owned-only Auto-Select context; a guest-enabled launch is
 * `UNKNOWN` until a rental badge is read. */
enum class LineageOwnership { OWNED, BORROWED, UNKNOWN }

/** Never promoted to an exact match: the legacy flow exposes no stable game identifier. */
enum class LineageMatch { PROBABLE_OWNED_MATCH, BORROWED_EXTERNAL, UNRESOLVED }

/** One factor row as observed: pixel-classified kind and stars (authoritative), raw OCR text (not), plus
 * flags for an untrusted star read or a row the list mask truncated. */
data class LineageFactorObservation(
    val kind: SparkRowKind,
    val displayText: String,
    val stars: Int,
    val ambiguous: Boolean,
    val clipped: Boolean,
)

data class LineageAncestorObservation(
    val portraitObserved: Boolean,
    val factors: List<LineageFactorObservation>,
)

data class LineageAncestor(
    val role: LineageAncestorRole,
    val slotIndex: Int,
    val portraitObserved: Boolean,
    val rank: String?,
    val factors: List<LineageFactorObservation>,
    /** Raw-OCR set fingerprint, unstable across re-reads; kept as evidence. */
    val factorFingerprint: String,
    /** Fingerprint of factors snapped onto the canonical domain; null when the domain did not load or any factor is unresolved. */
    val canonicalFactorFingerprint: String?,
    val structuralFactorFingerprint: String,
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
 * A trailing grade marker (circle, double circle, star) that OCR reads inconsistently: the same card comes
 * back as "Medium Straightaways O" then "Medium Straightaways". It is stripped from the normalized name so
 * the fingerprint stays stable; the raw display text and star count are unaffected.
 */
private val TRAILING_GRADE_MARKER = Regex("""\s+[O0*@()\u00A9\u00B0\u25CB\u25CE\u2605\u2606]+$""")

/** Fingerprint-only name normalization (trim, collapse whitespace, upper-case, drop a trailing grade marker);
 * matches the Veteran identity normalization so the two sides agree. */
internal fun normalizeLineageFactorName(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").uppercase().replace(TRAILING_GRADE_MARKER, "")

/** Order-independent fingerprint of an ancestor's factor set: `kind:NORMNAME:stars`, sorted, joined by `|`. */
internal fun ancestorFactorFingerprint(factors: List<LineageFactorObservation>): String =
    factors
        .map { "${it.kind.name.lowercase()}:${normalizeLineageFactorName(it.displayText)}:${it.stars}" }
        .sorted()
        .joinToString("|")

private fun observationHasLeadTriple(factors: List<LineageFactorObservation>): Boolean =
    factors.size >= 3 &&
        factors[0].kind == SparkRowKind.STAT &&
        factors[1].kind == SparkRowKind.APTITUDE &&
        factors[2].kind == SparkRowKind.UNIQUE

/** The lead triple is worth most; a full factor set with no truncated or ambiguous rows reaches 1.0. Never a gate. */
private fun ancestorCompleteness(factors: List<LineageFactorObservation>): Double {
    if (factors.isEmpty()) return 0.0
    var score = 0.0
    if (observationHasLeadTriple(factors)) score += 0.6
    if (factors.none { it.clipped }) score += 0.2
    if (factors.none { it.ambiguous }) score += 0.2
    return score
}

/** Blocks map to [LINEAGE_ROLE_ORDER] positionally (the summary proves six ancestors in fixed order). Fewer than
 * six blocks, a block missing its lead triple, or a clipped/ambiguous row yields PARTIAL; none yields FAILED. */
fun assembleLineageEvent(
    launchTransactionId: String?,
    ts: Long,
    scenario: String,
    trainee: String,
    overallAffinity: String?,
    guestsIncluded: Boolean,
    observedAncestors: List<LineageAncestorObservation>,
    /** Canonical factor domain for the derived fingerprint; null (asset missing) leaves it unresolved while the raw one is kept. */
    factorDomain: VeteranFactorDomain? = null,
): LegacyLineageEvent {
    val ownership = if (guestsIncluded) LineageOwnership.UNKNOWN else LineageOwnership.OWNED
    val match = if (ownership == LineageOwnership.OWNED) LineageMatch.PROBABLE_OWNED_MATCH else LineageMatch.UNRESOLVED
    val ancestors =
        observedAncestors.take(LINEAGE_ROLE_ORDER.size).mapIndexed { i, obs ->
            // Canonical tokens are derived, never replacing the raw OCR; an unresolved name leaves the canonical fingerprint null (fail closed).
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
