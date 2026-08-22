package com.steve1316.uma_android_automation.utils

import android.content.Context
import android.util.Log
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.SparkRowKind
import org.json.JSONObject

/** How a factor OCR read was resolved onto a canonical name (or not), kept as evidence so a margin
 * accept is auditable offline and distinguishable from a strong one. Mirrors [OutfitAcceptancePath].
 * ABBREVIATION marks a race name the factor card truncated ("Hopeful S." -> "Hopeful Stakes") that the
 * deterministic race-abbreviation path recovered after the fuzzy scan rejected it. */
enum class FactorAcceptancePath { STRONG, MARGIN, ABBREVIATION, REJECT }

/**
 * The outcome of snapping one factor-card OCR read onto the canonical factor domain.
 *
 * [canonicalName] is the ONLY identity output and is null on REJECT: an unresolved read must never
 * mint a trusted fingerprint. The score fields are diagnostics that explain a reject or a margin
 * accept without a second scan; nothing downstream may promote a candidate from them.
 */
data class FactorResolution(
    val canonicalName: String?,
    val bestScore: Double,
    /** Runner-up score, or null when the read hit an exact skeleton (no scan) or the domain had a
     * single candidate. */
    val secondScore: Double?,
    val path: FactorAcceptancePath,
    /** Which family the winning candidate came from ("stat"/"aptitude"/"unique"/"skill"/"race"/
     * "scenario"), for diagnostics. Null on REJECT. */
    val sourceFamily: String?,
) {
    val margin: Double get() = if (secondScore != null) bestScore - secondScore else Double.POSITIVE_INFINITY
    val resolved: Boolean get() = canonicalName != null
}

/** Minimum lead the best factor candidate must hold over the runner-up on the relaxed margin path -
 * deliberately far stricter than the strong path's [CANONICAL_MIN_MARGIN], because a lower-confidence
 * absolute read demands a larger separation to be trusted against a large factor domain. */
const val FACTOR_RELAXED_MIN_MARGIN = 0.15

/** Relaxed absolute floor for the factor margin path. Sits above the strong floor's lower risk band
 * but well under the clean-read scores the strong path already accepts. A read below it stays
 * unresolved (garbage/empty/truncated/off-domain fail closed). Higher than the outfit relaxed floor
 * (0.58) because a factor is scored against hundreds of candidates, not one trainee's few costumes. */
const val FACTOR_RELAXED_MIN_SIMILARITY = 0.62

/**
 * The STRONG/MARGIN/REJECT decision for a factor candidate, factored out so the boundary is
 * unit-testable directly and matches the PL-R1b outfit model. [second] is the runner-up score, or
 * null when there is no runner-up (single candidate, or an exact-skeleton hit). STRONG reproduces the
 * canonical identity rule; MARGIN only adds accepts below the absolute floor when the lead is wide.
 */
fun factorAcceptancePath(best: Double, second: Double?): FactorAcceptancePath {
    val margin = if (second != null) best - second else Double.POSITIVE_INFINITY
    return when {
        best >= CANONICAL_MIN_SIMILARITY && margin >= CANONICAL_MIN_MARGIN -> FactorAcceptancePath.STRONG
        best >= FACTOR_RELAXED_MIN_SIMILARITY && margin >= FACTOR_RELAXED_MIN_MARGIN -> FactorAcceptancePath.MARGIN
        else -> FactorAcceptancePath.REJECT
    }
}

/**
 * The canonical factor-name domain the Inspiration/lineage reader snaps its noisy factor OCR onto,
 * parsed from the generated `veteran_factor_domain.json` asset (see
 * `scripts/generate-veteran-factor-domain.mjs`, derived from src/data skills/races/scenarios).
 *
 * The domain is conditioned by the pixel-classified row kind: a STAT card is scored only against the
 * five stats, an APTITUDE card only against the ten aptitudes, a UNIQUE card only against unique
 * skills, and a WHITE card against the union of normal skills, races, and scenarios. That conditioning
 * is what keeps a white skill read from ever snapping onto a unique's long distinctive name and back.
 *
 * Resolution has three paths, the PL-R1b safety model plus the PL-R1c race-abbreviation fallback:
 *  - an exact-skeleton hit (the read normalizes to a canonical name's skeleton) is STRONG at once - the
 *    common case for a clean read, and the reason `Firm Conditions ○` and its glyph-less OCR agree;
 *  - otherwise a fuzzy scan picks the best candidate and STRONG/MARGIN/REJECT decides, so `FIRM
 *    CONDITIONSO` and `LONG COMERS` still recover their canonical names, and a garbage/off-domain read
 *    fails closed;
 *  - finally, only after a fuzzy REJECT, a race name the card truncated to an abbreviation
 *    ("Hopeful S.") is recovered by [resolveAbbreviatedRace] when it uniquely identifies one race,
 *    which is otherwise too short to clear the fuzzy floor against its long canonical.
 *
 * There is deliberately no hard-coded fallback domain. If the asset is missing or malformed the
 * domain is null, every factor reads unresolved, and the semantic fingerprint stays blocked - a loud
 * fail-closed packaging failure rather than a silent regression to raw-OCR fingerprints.
 */
class VeteranFactorDomain private constructor(
    val schemaVersion: Int,
    val source: String,
    private val candidatesByKind: Map<SparkRowKind, List<CanonEntry>>,
    private val exactByKind: Map<SparkRowKind, Map<String, String?>>,
    /** The race family on its own, so the truncated-abbreviation path scores a race read only against
     * races and never against skills or scenarios (which share the WHITE kind). */
    private val raceCandidates: List<CanonEntry>,
) {
    /** One canonical name and its precomputed normalized skeleton, so a scan never re-normalizes the
     * domain per read. */
    class CanonEntry(val canonical: String, val skeleton: String, val family: String)

    /** How many distinct canonical names the given kind can resolve to. */
    fun candidateCount(kind: SparkRowKind): Int = candidatesByKind[kind]?.size ?: 0

    /**
     * Snaps one factor OCR read onto its canonical name within the row's kind, or returns a REJECT
     * resolution (canonicalName null) when nothing is close enough, two candidates are too close to
     * separate, or the read is empty/off-domain.
     */
    fun resolve(rawOcr: String, kind: SparkRowKind): FactorResolution {
        val needle = normalizeIdentityText(rawOcr)
        if (needle.isEmpty()) return REJECT
        val candidates = candidatesByKind[kind] ?: return REJECT

        // Exact-skeleton fast path: a null value marks an ambiguous skeleton (two different canonical
        // names share it), which must NOT resolve; a non-null value is the unique canonical -> STRONG.
        val exactMap = exactByKind[kind]
        if (exactMap != null && exactMap.containsKey(needle)) {
            val canonical = exactMap[needle] ?: return REJECT
            return FactorResolution(canonical, bestScore = 1.0, secondScore = null, path = FactorAcceptancePath.STRONG, sourceFamily = familyOf(candidates, canonical))
        }

        var best: CanonEntry? = null
        var bestScore = -1.0
        var secondScore = -1.0
        for (entry in candidates) {
            val score = similarity(needle, entry.skeleton)
            if (score > bestScore) {
                secondScore = bestScore
                bestScore = score
                best = entry
            } else if (score > secondScore) {
                secondScore = score
            }
        }
        if (best == null) return REJECT
        val second = if (secondScore >= 0.0) secondScore else null
        val path = factorAcceptancePath(bestScore, second)
        if (path == FactorAcceptancePath.REJECT) {
            // The fuzzy scan could not place the read. A race name the factor card truncates to an
            // abbreviation ("Hopeful S.") is simply too short to clear the similarity floor against its
            // long canonical ("Hopeful Stakes"), so try the deterministic race-abbreviation path before
            // failing closed. It only fires here, after a reject, so no strong/margin read is affected.
            resolveAbbreviatedRace(rawOcr, kind, needle)?.let { return it }
        }
        return FactorResolution(
            canonicalName = if (path == FactorAcceptancePath.REJECT) null else best.canonical,
            bestScore = bestScore,
            secondScore = second,
            path = path,
            sourceFamily = if (path == FactorAcceptancePath.REJECT) null else best.family,
        )
    }

    /**
     * Deterministically recovers a race name the factor card truncated to an abbreviation, e.g.
     * "Hopeful S." -> "Hopeful Stakes", "NHK Mile C." -> "NHK Mile Cup". Race domain only (the WHITE
     * kind), and only when the raw OCR ended in the abbreviation period.
     *
     * The read is tokenized: every complete token must match the corresponding canonical token, and the
     * final abbreviated token must be an exact prefix of the canonical's (strictly longer) final token.
     * It accepts only when EXACTLY ONE race in the domain is compatible, so an abbreviation that could
     * name two races ("Kyoto K." -> Kyoto Kimpai / Kyoto Kinen) or is too short to carry a full complete
     * token ("S.") fails closed. This lowers no threshold and runs only after the fuzzy scan rejected.
     */
    private fun resolveAbbreviatedRace(rawOcr: String, kind: SparkRowKind, needle: String): FactorResolution? {
        if (kind != SparkRowKind.WHITE) return null
        val trimmed = rawOcr.trim()
        if (!trimmed.endsWith('.')) return null
        val ocrTokens = tokenizeIdentity(trimmed.trimEnd('.').trim())
        // At least one complete token before the abbreviated tail: a bare "S."/"C." carries no race
        // identity and must never resolve.
        if (ocrTokens.size < 2) return null

        var match: CanonEntry? = null
        for (entry in raceCandidates) {
            if (abbreviationCompatibleRace(ocrTokens, entry)) {
                if (match != null) return null // 2+ compatible races: ambiguous, fail closed
                match = entry
            }
        }
        val resolved = match ?: return null
        // Diagnostic score only, on the same metric the fuzzy scan uses (how close the raw read was to
        // the canonical skeleton). The acceptance itself is structural, recorded as ABBREVIATION.
        return FactorResolution(
            canonicalName = resolved.canonical,
            bestScore = similarity(needle, resolved.skeleton),
            secondScore = null,
            path = FactorAcceptancePath.ABBREVIATION,
            sourceFamily = "race",
        )
    }

    /** Whether the abbreviated OCR tokens are compatible with one race candidate: same token count,
     * every complete token matching, and the final token an exact prefix of the canonical's final
     * (strictly longer) token. The exact prefix is deliberate: an edit tolerance on the truncated tail
     * would make near-neighbor races ("Kyoto Kim." against Kimpai and Kinen) ambiguous and fail closed
     * for no gain, since the fuzzy path already recovers a longer, noisier read. */
    private fun abbreviationCompatibleRace(ocrTokens: List<String>, entry: CanonEntry): Boolean {
        val canonTokens = tokenizeIdentity(entry.canonical)
        if (canonTokens.size != ocrTokens.size) return false
        for (i in 0 until ocrTokens.size - 1) {
            if (!completeTokenMatches(ocrTokens[i], canonTokens[i])) return false
        }
        val last = ocrTokens.last()
        val canonLast = canonTokens.last()
        // A genuine truncation: the abbreviation is strictly shorter than the full final word. An
        // equal-length final token is not truncated and would already have resolved on the fuzzy path.
        if (last.isEmpty() || canonLast.length <= last.length) return false
        return canonLast.startsWith(last)
    }

    /** A complete (non-abbreviated) OCR token matches its canonical token when identical, or one OCR
     * error apart in a token long enough (>= 4 chars on both sides) that a single edit cannot flip it
     * onto a different word. Short tokens must match exactly. */
    private fun completeTokenMatches(a: String, b: String): Boolean {
        if (a == b) return true
        return minOf(a.length, b.length) >= 4 && editDistance(a, b) <= 1
    }

    /** Splits a raw string on whitespace and reduces each token to its identity skeleton, dropping any
     * that skeletonize to nothing (stray punctuation). Shared by the OCR read and the canonical race
     * names so both tokenize identically. */
    private fun tokenizeIdentity(s: String): List<String> =
        s.split(WHITESPACE).map { normalizeIdentityText(it) }.filter { it.isNotEmpty() }

    private fun familyOf(candidates: List<CanonEntry>, canonical: String): String? =
        candidates.firstOrNull { it.canonical == canonical }?.family

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]VeteranFactorDomain"

        const val ASSET_NAME: String = "veteran_factor_domain.json"
        const val SUPPORTED_SCHEMA_VERSION: Int = 1

        /** Whitespace splitter for the abbreviation tokenizer, compiled once. */
        private val WHITESPACE = Regex("\\s+")

        private val REJECT = FactorResolution(null, bestScore = 0.0, secondScore = null, path = FactorAcceptancePath.REJECT, sourceFamily = null)

        /** Which asset families a given row kind resolves against. WHITE spans skills, races, and
         * scenarios because the pixel classifier cannot tell those three white sub-types apart. */
        private val FAMILIES_FOR_KIND: Map<SparkRowKind, List<String>> =
            mapOf(
                SparkRowKind.STAT to listOf("stat"),
                SparkRowKind.APTITUDE to listOf("aptitude"),
                SparkRowKind.UNIQUE to listOf("unique"),
                SparkRowKind.WHITE to listOf("skill", "race", "scenario"),
            )

        /** Parses the runtime asset text. Returns null (never throws) on malformed JSON, a missing
         * family, or an unsupported schema version, so a bad asset degrades to "factor unresolved". */
        fun parse(json: String): VeteranFactorDomain? {
            return try {
                val root = JSONObject(json)
                val schema = root.getInt("schemaVersion")
                if (schema != SUPPORTED_SCHEMA_VERSION) {
                    Log.w(TAG, "unsupported veteran_factor_domain schemaVersion $schema (expected $SUPPORTED_SCHEMA_VERSION)")
                    return null
                }
                val familiesObj = root.getJSONObject("families")
                val byFamily = linkedMapOf<String, List<CanonEntry>>()
                for (family in familiesObj.keys()) {
                    val arr = familiesObj.getJSONArray(family)
                    byFamily[family] = (0 until arr.length()).map { i ->
                        val name = arr.getString(i)
                        CanonEntry(name, normalizeIdentityText(name), family)
                    }
                }

                val candidatesByKind = linkedMapOf<SparkRowKind, List<CanonEntry>>()
                val exactByKind = linkedMapOf<SparkRowKind, Map<String, String?>>()
                var collisions = 0
                for ((kind, families) in FAMILIES_FOR_KIND) {
                    // Deduplicate by canonical name across the kind's families (e.g. "Trackblazer"
                    // appears as both a skill and a scenario, but is one factor name).
                    val seen = HashSet<String>()
                    val candidates = ArrayList<CanonEntry>()
                    for (family in families) for (entry in byFamily[family].orEmpty()) if (seen.add(entry.canonical)) candidates.add(entry)
                    candidatesByKind[kind] = candidates

                    // Exact-skeleton map: a skeleton reached by two DIFFERENT canonical names is stored
                    // null so the fast path refuses it (the fuzzy scan then rejects on zero margin).
                    val exact = HashMap<String, String?>()
                    for (entry in candidates) {
                        if (!exact.containsKey(entry.skeleton)) {
                            exact[entry.skeleton] = entry.canonical
                        } else if (exact[entry.skeleton] != entry.canonical) {
                            exact[entry.skeleton] = null
                            collisions++
                        }
                    }
                    exactByKind[kind] = exact
                }
                if (candidatesByKind.values.all { it.isEmpty() }) {
                    Log.w(TAG, "veteran_factor_domain has no candidates in any family")
                    return null
                }
                if (collisions > 0) Log.w(TAG, "veteran_factor_domain has $collisions ambiguous skeleton(s); those factors fail closed")
                VeteranFactorDomain(schema, root.optString("source", "unknown"), candidatesByKind, exactByKind, byFamily["race"].orEmpty())
            } catch (e: Exception) {
                Log.w(TAG, "failed to parse veteran_factor_domain: ${e.message}")
                null
            }
        }

        /** Loads and parses the asset from the APK's assets. Returns null (never throws) when the
         * asset is missing or unreadable. */
        fun loadFromAssets(context: Context, assetName: String = ASSET_NAME): VeteranFactorDomain? {
            return try {
                parse(context.assets.open(assetName).bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                Log.w(TAG, "failed to load $assetName from assets: ${e.message}")
                null
            }
        }
    }
}
