// ParentLab PL-R2 - the shadow retention advisor. Pure, offline, deterministic, read-only.
//
// Rules first, scores never. Protection is decided by named rules that each cite a fact; the transfer
// side is reached only by passing a chain of strict gates; and nothing in this file blends the two
// into a single number that could quietly move a Veteran across the line. A weighted score can order
// a report. It cannot prove that letting something go is safe.
//
// State precedence, applied in this order and no other:
//   INSUFFICIENT evidence   -> UNKNOWN         (cannot assess; unknown is protected)
//   any merit protect rule  -> HARD_PROTECT
//   any keep reason         -> KEEP            (deliberately ahead of the transfer side: a Veteran
//                                               that is both valuable and dominated is kept)
//   strict transfer gates   -> SAFE_TO_TRANSFER / QUARANTINE_TRANSFER
//   factor evidence missing -> UNKNOWN
//   otherwise               -> REVIEW
//
// The engine is target-aware, and a per-target document must be read as such: a Veteran quarantined
// under MILE_PARENT may be the account's only LONG_PARENT coverage. `coverageSummary.targetsCovered`
// carries that fact on every record so a single-target read cannot be mistaken for a verdict.

import type { InspirationFactorRecord } from "./inspiration.ts"
import type { RosterReconciliation } from "./reconcile.ts"
import type { RosterSnapshot } from "./roster.ts"
import {
    buildFactorScarcityIndex,
    carriersAtOrAbove,
    factorKey,
    HIGH_VALUE_FACTOR_MIN_STARS,
    observedUniqueFactorKeys,
    OBSERVED_SCARCE_MAX_CARRIERS,
    replacementSummary,
    scarcestClaim,
    type RetentionEvidenceSet,
    type VeteranEvidence,
} from "./retentionEvidence.ts"
import { aptitudeGradeRank, clearsAptitudeGate, targetDimensions, TARGET_DIMENSION_NAMES, TARGET_PROFILES, type TargetDimensions, type TargetProfile, type TargetProfileId } from "./retentionTargets.ts"
import {
    PARENTLAB_RETENTION_SCHEMA,
    PARENTLAB_RETENTION_SCHEMA_VERSION,
    REPLACEABLE_DIFFICULTIES,
    RETENTION_CONFIDENCE_RANK,
    RETENTION_STATES,
    type DominanceBlocker,
    type DominanceFinding,
    type FactorScarcityIndex,
    type HardProtectReason,
    type InactiveRuleNote,
    type RetentionConfidence,
    type RetentionDataCompleteness,
    type RetentionGateReason,
    type RetentionKeepReason,
    type RetentionRiskReason,
    type RetentionShadowReport,
    type RetentionState,
    type RetentionStateCounts,
    type VeteranRetentionRecommendation,
} from "./retentionTypes.ts"
import type { VeteranLibrary } from "./types.ts"

/** Aptitude grade at or above which a gated profile counts as a strong fit rather than merely met. */
const STRONG_TARGET_GRADE = "S"

/** Star count at which an ancestor factor counts as real lineage depth. */
const DEEP_LINEAGE_MIN_STARS = 3

/**
 * Rules the engine deliberately does not apply on this repository's evidence.
 *
 * Reported on every document rather than left absent, because a silently missing rule looks exactly
 * like a rule that ran and found nothing.
 */
export const INACTIVE_RULES: readonly InactiveRuleNote[] = [
    {
        rule: "TRANSFER_REQUEST_HOLD",
        reason: "no Transfer Request reward or demand data exists in this repository, so holding value cannot be established from evidence. The state is defined and reachable by the engine, but no rule assigns it.",
    },
    {
        rule: "PARENT_AFFINITY_VALUE",
        reason: "the affinity formula is not decoded here. Lineage evidence is reported as observed ancestor factors only; no affinity number is derived.",
    },
    {
        rule: "ACQUISITION_RECENCY_PROTECT",
        reason: "the roster identity pass writes no Career Info block, so dateAcquired is null for every entry and a recently-acquired rule has nothing to read.",
    },
]

/** One Veteran's comparison-ready projection under one target profile. */
export interface RetentionCandidate {
    readonly evidence: VeteranEvidence
    readonly dimensions: TargetDimensions
    readonly hardProtectReasons: readonly HardProtectReason[]
    readonly observedUnique: readonly string[]
}

/** True when both sides carry trusted factor evidence, the only basis a comparison is admissible on. */
function comparable(a: RetentionCandidate, b: RetentionCandidate): boolean {
    return a.evidence.captureTrusted && b.evidence.captureTrusted && a.evidence.rosterFingerprint !== null && b.evidence.rosterFingerprint !== null
}

/** Whether A carries every one of B's factors at equal or better stars. */
function coversFactorSet(a: readonly InspirationFactorRecord[], b: readonly InspirationFactorRecord[]): boolean {
    const best = new Map<string, number>()
    for (const f of a) {
        const key = factorKey(f)
        if (!key) continue
        best.set(key, Math.max(best.get(key) ?? 0, f.stars))
    }
    for (const f of b) {
        const key = factorKey(f)
        // An unresolved factor on B cannot be proven covered, so it is treated as uncovered.
        if (!key) return false
        if ((best.get(key) ?? 0) < f.stars) return false
    }
    return true
}

/**
 * The dominance test. Returns a finding when the candidate wins the dimension comparison, with
 * `blockedBy` empty for a true dominator and populated for a substitute that failed a strict gate.
 *
 * The dimension comparison alone is never enough. A candidate that is at least as good on every
 * target dimension and strictly better on one has only shown that it scores higher; it must also
 * carry the subject's actual factors, and the subject must add no unique coverage and hold no
 * protection of its own, before redundancy is established.
 */
export function evaluateDominance(candidate: RetentionCandidate, subject: RetentionCandidate, profile: TargetProfile): DominanceFinding | null {
    if (candidate.evidence.rosterFingerprint === subject.evidence.rosterFingerprint) return null

    const strictlyBetterOn: string[] = []
    for (const dim of TARGET_DIMENSION_NAMES) {
        const c = candidate.dimensions[dim]
        const s = subject.dimensions[dim]
        if (c < s) return null
        if (c > s) strictlyBetterOn.push(dim)
    }
    if (strictlyBetterOn.length === 0) return null

    const blockedBy: DominanceBlocker[] = []
    if (!comparable(candidate, subject)) blockedBy.push("EVIDENCE_NOT_TRUSTED")
    else if (!coversFactorSet(candidate.evidence.selfFactors ?? [], subject.evidence.selfFactors ?? [])) blockedBy.push("FACTOR_SET_NOT_COVERED")
    if (subject.observedUnique.length > 0) blockedBy.push("SUBJECT_HAS_UNIQUE_COVERAGE")
    if (subject.hardProtectReasons.length > 0) blockedBy.push("SUBJECT_HARD_PROTECTED")

    const who = `${candidate.evidence.entry.character ?? "unknown"}${candidate.evidence.entry.outfit ? ` (${candidate.evidence.entry.outfit})` : ""}`
    const better = strictlyBetterOn.join(", ")
    const explanation =
        blockedBy.length === 0
            ? `${who} matches or beats every ${profile.id} dimension, is strictly better on ${better}, and carries every one of this Veteran's factors at equal or better stars`
            : `${who} wins on ${better} under ${profile.id} but does not replace this Veteran: ${blockedBy.join(", ")}`
    return { rosterFingerprint: candidate.evidence.rosterFingerprint as string, character: candidate.evidence.entry.character, outfit: candidate.evidence.entry.outfit, strictlyBetterOn, blockedBy, explanation }
}

function maxStars(factors: readonly InspirationFactorRecord[] | null, kind: string): number {
    if (!factors) return 0
    let best = 0
    for (const f of factors) if (f.kind === kind && f.stars > best) best = f.stars
    return best
}

/** Merit-based protection. Every reason is a rule over a fact, never a threshold on a blended score. */
export function hardProtectReasonsFor(
    evidence: VeteranEvidence,
    scarcity: FactorScarcityIndex,
    profile: TargetProfile,
    soleTargetCoverage: boolean,
    replacementVeryHard: boolean,
    manualProtect: ReadonlySet<string>,
): readonly HardProtectReason[] {
    const reasons: HardProtectReason[] = []
    if (evidence.rosterFingerprint && manualProtect.has(evidence.rosterFingerprint)) reasons.push("MANUAL_PROTECT")
    if (evidence.characterCarriers === 1) reasons.push("SOLE_CHARACTER_SOURCE")
    // Only reported when the character itself is not already sole: otherwise the two rules would say
    // the same thing twice about the same Veteran.
    else if (evidence.characterOutfitCarriers === 1) reasons.push("SOLE_CHARACTER_OUTFIT_SOURCE")
    if (soleTargetCoverage && profile.aptitudeGate !== null) reasons.push("SOLE_TARGET_APTITUDE_COVERAGE")
    // Sole-carrier-of-a-factor only hard-protects when the coverage behind it can carry an
    // account-wide claim. Below complete coverage the same observation is real but weak - at 19
    // captures out of 257 owned Veterans nearly every two-star factor looks unique - so it degrades
    // to the RARE_FACTOR_CARRIER keep reason instead. Both outcomes protect; only one of them
    // asserts something the evidence cannot support.
    if (scarcity.accountWide && observedUniqueFactorKeys(evidence, scarcity).length > 0) reasons.push("OBSERVED_UNIQUE_FACTOR")
    if (replacementVeryHard) reasons.push("IRREPLACEABLE_HISTORICAL_OUTCOME")
    return reasons
}

function keepReasonsFor(evidence: VeteranEvidence, scarcity: FactorScarcityIndex, profile: TargetProfile): readonly RetentionKeepReason[] {
    const reasons: RetentionKeepReason[] = []
    const factors = evidence.selfFactors
    if (factors) {
        const blue = maxStars(factors, "stat")
        const red = maxStars(factors, "aptitude")
        const green = maxStars(factors, "unique")
        const anyThreeStar = factors.some((f) => f.stars >= 3)
        if (anyThreeStar || (blue >= 2 && red >= 2 && green >= 2)) reasons.push("HIGH_VALUE_FACTOR_SET")
        const rare = factors.some((f) => {
            if (f.stars < HIGH_VALUE_FACTOR_MIN_STARS) return false
            const key = factorKey(f)
            if (!key) return false
            const carriers = carriersAtOrAbove(scarcity, key, f.stars)
            return carriers > 0 && carriers <= OBSERVED_SCARCE_MAX_CARRIERS
        })
        if (rare) reasons.push("RARE_FACTOR_CARRIER")
    }
    if (profile.aptitudeGate) {
        const have = aptitudeGradeRank(evidence.entry.aptitudes[profile.aptitudeGate.key])
        if (have >= aptitudeGradeRank(STRONG_TARGET_GRADE)) reasons.push("STRONG_TARGET_APTITUDE_FIT")
    }
    // A sole carrier is already hard-protected, so this covers the next band up: losing one of two
    // sources for a character halves the account's access to that character's green factor.
    if (evidence.characterCarriers === 2) reasons.push("SCARCE_CHARACTER_SOURCE")
    if (evidence.capture && evidence.capture.legacyAncestorFactors.some((set) => set.some((f) => f.stars >= DEEP_LINEAGE_MIN_STARS))) reasons.push("DEEP_LINEAGE_EVIDENCE")
    return reasons
}

function riskReasonsFor(evidence: VeteranEvidence, scarcity: FactorScarcityIndex, profile: TargetProfile, dominated: boolean): readonly RetentionRiskReason[] {
    const reasons: RetentionRiskReason[] = []
    if (dominated) reasons.push("DOMINATED_BY_PEER")
    const factors = evidence.selfFactors
    if (factors) {
        if (observedUniqueFactorKeys(evidence, scarcity).length === 0) reasons.push("NO_UNIQUE_OBSERVED_FACTOR")
        const generic = factors.every((f) => {
            const key = factorKey(f)
            if (!key) return false
            return carriersAtOrAbove(scarcity, key, f.stars) > OBSERVED_SCARCE_MAX_CARRIERS
        })
        if (generic) reasons.push("GENERIC_FACTOR_SET")
        if (maxStars(factors, "stat") <= 1 && maxStars(factors, "aptitude") <= 1 && maxStars(factors, "unique") <= 1) reasons.push("LOW_FACTOR_STAR_TOTAL")
    }
    if (profile.aptitudeGate && !clearsAptitudeGate(evidence.entry, profile)) reasons.push("WEAK_TARGET_APTITUDE_FIT")
    return reasons
}

function gateReasonsFor(evidence: VeteranEvidence, snapshot: RosterSnapshot, scarcity: FactorScarcityIndex, replacementUnknown: boolean, dominated: boolean): readonly RetentionGateReason[] {
    const gates: RetentionGateReason[] = []
    if (!snapshot.trustedComplete) gates.push("ROSTER_SNAPSHOT_UNTRUSTED")
    if (!evidence.rosterFingerprint) gates.push("ROSTER_IDENTITY_UNRESOLVED")
    // The roster writer emits a constant "unknown" today because a memo protects a Veteran and is not
    // visible on the list screen. The rule is written generally so a future filter-partition pass can
    // clear it; on current evidence it never does.
    if (evidence.entry.protectionState !== "not_protected") gates.push("PROTECTION_STATE_UNKNOWN")
    if (evidence.entry.favoriteState !== "not_set") gates.push("FAVORITE_STATE_UNKNOWN")
    if (!evidence.capture) gates.push("INSPIRATION_CAPTURE_MISSING")
    else if (!evidence.capture.sparkCaptureComplete) gates.push("INSPIRATION_CAPTURE_INCOMPLETE")
    else if (!evidence.capture.selfFactorSetTrusted) gates.push("INSPIRATION_FACTORS_UNTRUSTED")
    if (!scarcity.accountWide) gates.push("SCARCITY_COVERAGE_INSUFFICIENT")
    if (replacementUnknown) gates.push("REPLACEMENT_DIFFICULTY_UNKNOWN")
    if (!dominated) gates.push("NO_DOMINATOR_FOUND")
    return gates
}

function confidenceFor(evidence: VeteranEvidence, snapshot: RosterSnapshot, scarcity: FactorScarcityIndex, replacementKnown: boolean): RetentionConfidence {
    if (!snapshot.trustedComplete || !evidence.rosterFingerprint) return "INSUFFICIENT"
    if (!evidence.captureTrusted) return "LOW"
    if (scarcity.accountWide && replacementKnown) return "HIGH"
    return "MEDIUM"
}

function completenessFor(evidence: VeteranEvidence, snapshot: RosterSnapshot, scarcity: FactorScarcityIndex): RetentionDataCompleteness {
    const flags = {
        rosterTrusted: snapshot.trustedComplete,
        identityResolved: evidence.rosterFingerprint !== null,
        inspirationCaptured: evidence.capture !== null,
        inspirationComplete: evidence.capture?.sparkCaptureComplete === true,
        inspirationTrusted: evidence.captureTrusted,
        historicalMatched: evidence.matchStatus === "EXACT_HISTORICAL_MATCH" || evidence.matchStatus === "PROBABLE_HISTORICAL_MATCH",
        protectionKnown: evidence.entry.protectionState === "not_protected" && evidence.entry.favoriteState === "not_set",
        scarcityAccountWide: scarcity.accountWide,
    }
    const values = Object.values(flags)
    return { ...flags, score: Math.round((values.filter(Boolean).length / values.length) * 10000) / 10000 }
}

function unknownEvidenceFor(evidence: VeteranEvidence, completeness: RetentionDataCompleteness): readonly string[] {
    const out: string[] = []
    if (!completeness.identityResolved) out.push("rosterEntry.rosterFingerprint")
    if (evidence.entry.favoriteState !== "not_set") out.push("rosterEntry.favoriteState")
    if (evidence.entry.protectionState !== "not_protected") out.push("rosterEntry.protectionState")
    if (!evidence.entry.careerInfo) out.push("rosterEntry.careerInfo")
    if (!completeness.inspirationCaptured) out.push("inspiration.selfFactors")
    else if (!completeness.inspirationTrusted) out.push("inspiration.selfFactorSetTrusted")
    if (!completeness.historicalMatched) out.push("history.veteranId")
    if (!completeness.scarcityAccountWide) out.push("scarcity.accountWideCoverage")
    return out
}

/** Assembles the human-readable line from the reason codes. Deterministic: same codes, same text. */
function explain(state: RetentionState, confidence: RetentionConfidence, hard: readonly string[], keep: readonly string[], risk: readonly string[], gates: readonly string[]): string {
    const parts = [`${state} at ${confidence} confidence`]
    if (hard.length) parts.push(`protected by ${hard.join(", ")}`)
    if (keep.length) parts.push(`retention value from ${keep.join(", ")}`)
    if (risk.length) parts.push(`expendability signals ${risk.join(", ")}`)
    if (gates.length) parts.push(`blocked from the transfer side by ${gates.join(", ")}`)
    return parts.join("; ")
}

/** Inputs to one document build. Nothing here is mutated. */
export interface RetentionAdvisorInput {
    readonly evidence: RetentionEvidenceSet
    readonly library: VeteranLibrary | null
    readonly reconciliation: RosterReconciliation | null
    readonly profile: TargetProfile
    /** Roster fingerprints the operator has explicitly protected. */
    readonly manualProtect?: ReadonlySet<string>
}

/**
 * Builds the shadow document for one target profile.
 *
 * Deterministic by construction: no wall clock is read (`generatedAt` is the newest observation time
 * across the inputs), every collection is sorted by a stable key, and no input object is written to.
 */
export function buildRetentionShadowReport(input: RetentionAdvisorInput): RetentionShadowReport {
    const { evidence, library, profile } = input
    const snapshot = evidence.snapshot
    const manualProtect = input.manualProtect ?? new Set<string>()
    const scarcity = buildFactorScarcityIndex(evidence)

    // Target coverage over the WHOLE roster, not the captured subset: an aptitude grade is read for
    // every identified entry, so this is one of the few account-wide facts available today.
    const covering = new Map<TargetProfileId, VeteranEvidence[]>()
    for (const id of Object.keys(TARGET_PROFILES) as TargetProfileId[]) {
        covering.set(
            id,
            evidence.veterans.filter((v) => clearsAptitudeGate(v.entry, TARGET_PROFILES[id])),
        )
    }
    const soleCoverage = new Map<string, TargetProfileId[]>()
    for (const [id, list] of covering) {
        if (TARGET_PROFILES[id].aptitudeGate && list.length === 1 && list[0].rosterFingerprint) {
            const key = list[0].rosterFingerprint
            soleCoverage.set(key, [...(soleCoverage.get(key) ?? []), id])
        }
    }

    const replacements = new Map<number, ReturnType<typeof replacementSummary>>()
    for (const v of evidence.veterans) replacements.set(v.entry.scanIndex, replacementSummary(v, library))

    // Pass one: everything a dominance comparison needs, computed without reference to any peer.
    const candidates: RetentionCandidate[] = evidence.veterans.map((v) => {
        const replacement = replacements.get(v.entry.scanIndex)
        const sole = v.rosterFingerprint ? (soleCoverage.get(v.rosterFingerprint)?.includes(profile.id) ?? false) : false
        return {
            evidence: v,
            dimensions: targetDimensions(v.entry, v.selfFactors, profile),
            hardProtectReasons: hardProtectReasonsFor(v, scarcity, profile, sole, replacement?.difficulty === "VERY_HARD", manualProtect),
            observedUnique: observedUniqueFactorKeys(v, scarcity),
        }
    })
    // Only trusted candidates can dominate anything, so the pairwise pass runs over that subset alone.
    const comparableCandidates = candidates.filter((c) => c.evidence.captureTrusted && c.evidence.rosterFingerprint !== null)

    const recommendations: VeteranRetentionRecommendation[] = candidates.map((candidate) => {
        const v = candidate.evidence
        const replacement = replacements.get(v.entry.scanIndex) as ReturnType<typeof replacementSummary>

        const dominators: DominanceFinding[] = []
        const substitutes: DominanceFinding[] = []
        if (v.captureTrusted) {
            for (const peer of comparableCandidates) {
                const finding = evaluateDominance(peer, candidate, profile)
                if (!finding) continue
                if (finding.blockedBy.length === 0) dominators.push(finding)
                else substitutes.push(finding)
            }
            dominators.sort((a, b) => (a.rosterFingerprint < b.rosterFingerprint ? -1 : 1))
            substitutes.sort((a, b) => (a.rosterFingerprint < b.rosterFingerprint ? -1 : 1))
        }

        const keepReasons = keepReasonsFor(v, scarcity, profile)
        const riskReasons = riskReasonsFor(v, scarcity, profile, dominators.length > 0)
        const gateReasons = gateReasonsFor(v, snapshot, scarcity, replacement.difficulty === "UNKNOWN", dominators.length > 0)
        const confidence = confidenceFor(v, snapshot, scarcity, replacement.difficulty !== "UNKNOWN")
        const completeness = completenessFor(v, snapshot, scarcity)

        let state: RetentionState
        if (confidence === "INSUFFICIENT") state = "UNKNOWN"
        else if (candidate.hardProtectReasons.length > 0) state = "HARD_PROTECT"
        else if (keepReasons.length > 0) state = "KEEP"
        else if (confidence === "HIGH" && gateReasons.length === 0 && dominators.length > 0 && candidate.observedUnique.length === 0 && REPLACEABLE_DIFFICULTIES.has(replacement.difficulty)) state = "SAFE_TO_TRANSFER"
        else if (RETENTION_CONFIDENCE_RANK[confidence] >= RETENTION_CONFIDENCE_RANK.MEDIUM && dominators.length > 0 && candidate.observedUnique.length === 0 && REPLACEABLE_DIFFICULTIES.has(replacement.difficulty)) state = "QUARANTINE_TRANSFER"
        else if (!v.captureTrusted) state = "UNKNOWN"
        else state = "REVIEW"

        return {
            rosterFingerprint: v.rosterFingerprint,
            scanIndex: v.entry.scanIndex,
            character: v.entry.character,
            outfit: v.entry.outfit,
            rank: v.entry.rank,
            state,
            confidence,
            hardProtectReasons: candidate.hardProtectReasons,
            gateReasons,
            keepReasons,
            riskReasons,
            factorValueSummary: {
                statFactorStars: v.selfFactors ? maxStars(v.selfFactors, "stat") : null,
                aptitudeFactorStars: v.selfFactors ? maxStars(v.selfFactors, "aptitude") : null,
                uniqueFactorStars: v.selfFactors ? maxStars(v.selfFactors, "unique") : null,
                whiteFactorCount: v.selfFactors ? v.selfFactors.filter((f) => f.kind === "white").length : null,
                totalFactorStars: v.selfFactors ? v.selfFactors.reduce((sum, f) => sum + f.stars, 0) : null,
                scarcestClaim: scarcestClaim(v, scarcity),
                observedUniqueFactorKeys: candidate.observedUnique,
                lineageAncestorsObserved: v.lineageAncestorsObserved,
                rating: v.entry.rating,
            },
            coverageSummary: {
                character: v.entry.character,
                characterCarriers: v.characterCarriers,
                characterOutfitCarriers: v.characterOutfitCarriers,
                targetsCovered: (Object.keys(TARGET_PROFILES) as TargetProfileId[]).filter((id) => TARGET_PROFILES[id].aptitudeGate !== null && clearsAptitudeGate(v.entry, TARGET_PROFILES[id])),
                soleTargetCoverage: v.rosterFingerprint ? (soleCoverage.get(v.rosterFingerprint) ?? []) : [],
            },
            replacement,
            dominators,
            substitutes,
            dataCompleteness: completeness,
            unknownEvidence: unknownEvidenceFor(v, completeness),
            explanation: explain(state, confidence, candidate.hardProtectReasons, keepReasons, riskReasons, gateReasons),
        }
    })

    const counts = Object.fromEntries(RETENTION_STATES.map((s) => [s, recommendations.filter((r) => r.state === s).length])) as RetentionStateCounts
    return {
        schema: PARENTLAB_RETENTION_SCHEMA,
        schemaVersion: PARENTLAB_RETENTION_SCHEMA_VERSION,
        rosterScanId: snapshot.scanId,
        rosterFingerprint: `${snapshot.scanId}:${scarcity.identifiedRosterEntries}/${snapshot.scanCount}`,
        generatedAt: evidence.observedAt,
        targetProfile: profile.id,
        counts,
        scarcity,
        recommendations: [...recommendations].sort((a, b) => a.scanIndex - b.scanIndex),
        inactiveRules: INACTIVE_RULES,
    }
}
