// ParentLab PL-R4 - the per-Veteran parent planning record. Pure, offline, deterministic, read-only.
//
// One owned Veteran, projected onto one target build. Nothing here ranks or recommends: it produces
// the components a ranking is allowed to read, and names every component that could not be measured.
//
// Two separations run through the file and are load-bearing:
//
//   self factors vs legacy factors. A Veteran's own Sparks and the two Legacy Origin blocks behind it
//   are different assets with different reliability, and flattening them would let a strong ancestry
//   masquerade as a strong parent. They are never summed together.
//
//   factor families stay apart. Blue, pink, green and white are counted per family. A single "total
//   stars" number across families would let three filler white factors outweigh the one pink factor
//   the build actually needs, which is exactly the failure this stage exists to avoid.
//
// Rating is carried because a report may want to show it. It is never a ranking input: a higher-rated
// generic Veteran must not out-rank a lower-rated one carrying the rare factor the build needs.

import { pairwiseRelation, resolveCharaId, type SuccessionRelationIndex } from "./affinityData.ts"
import type { InspirationFactorRecord } from "./inspiration.ts"
import { aptitudeGradeRank, type RosterAptitudeKey } from "./retentionTargets.ts"
import { carriersAtOrAbove, factorKey, OBSERVED_SCARCE_MAX_CARRIERS, type VeteranEvidence } from "./retentionEvidence.ts"
import type { FactorScarcityIndex, ScarcityClaim } from "./retentionTypes.ts"
import type { TargetBuild } from "./targetBuild.ts"

/** The four factor families, in the order they are always reported. */
export const FACTOR_FAMILIES = ["stat", "aptitude", "unique", "white"] as const
export type FactorFamily = (typeof FACTOR_FAMILIES)[number]

/** Which target-build priority list a family reads. */
const PRIORITY_LIST_BY_FAMILY: Readonly<Record<FactorFamily, (b: TargetBuild) => readonly string[]>> = {
    stat: (b) => b.priorityStatFactors,
    aptitude: (b) => b.priorityAptitudeFactors,
    unique: (b) => b.priorityUniqueFactors,
    white: (b) => b.priorityWhiteFactors,
}

/** One of a candidate's factors that the build asked for. */
export interface CandidateFactorMatch {
    readonly factorKey: string
    readonly family: FactorFamily
    readonly canonicalName: string
    readonly stars: number
    /** Owned Veterans observed carrying this factor at this star count or better. */
    readonly observedCarriersAtStars: number
    readonly scarcity: ScarcityClaim
}

/**
 * One family's relevance for one candidate.
 *
 * `maxStars` and `totalStars` answer different questions and both are kept. A parent with one 3-star
 * Speed factor and a parent with three 1-star ones are not the same asset, and collapsing them onto
 * a single number would hide whichever difference the reader cared about.
 */
export interface CandidateFamilyRelevance {
    readonly family: FactorFamily
    readonly matched: readonly CandidateFactorMatch[]
    /** Distinct priority factors of this family the candidate carries. */
    readonly distinctFactors: number
    readonly maxStars: number
    readonly totalStars: number
}

/** A candidate's relevance across the four families. Deliberately no cross-family total. */
export type CandidateRelevance = Readonly<Record<FactorFamily, CandidateFamilyRelevance>>

/** The known part of the candidate's affinity to the target. */
export interface CandidateAffinity {
    readonly resolved: boolean
    /** Base relation points between the target trainee and this candidate's character, or null. */
    readonly points: number | null
    readonly sharedRelationTypes: number | null
    /** Why it did not resolve, when it did not. */
    readonly unresolvedReason: "TARGET_NOT_IN_RELATION_DOMAIN" | "CANDIDATE_NOT_IN_RELATION_DOMAIN" | "CANDIDATE_CHARACTER_UNREAD" | "SAME_CHARACTER_AS_TARGET" | null
}

/** The Legacy Origin support behind a candidate. Ancestor identity is never claimed. */
export interface CandidateLegacySupport {
    /** Legacy Origin blocks observed on this Veteran's own panel, or null when uncaptured. */
    readonly blocksObserved: number | null
    /** Always false: the panel shows a portrait and an unclassified medal, never a name. */
    readonly ancestorIdentityKnown: false
    readonly relevance: CandidateRelevance | null
    /** Distinct priority factors covered anywhere in the legacy blocks. */
    readonly distinctPriorityFactors: number
    /** Sum of stars over the matched priority factors across all blocks. */
    readonly totalPriorityStars: number
}

/** Per-candidate evidence gaps. Each one is a fact about what could not be established. */
export const CANDIDATE_GAPS = [
    /** The roster entry never resolved to a fingerprint, so nothing attaches to it. */
    "IDENTITY_UNRESOLVED",
    /** No complete, fully resolved Inspiration capture backs this Veteran's own factors. */
    "NO_TRUSTED_FACTOR_CAPTURE",
    /** The candidate's character did not read, so no relation can be computed. */
    "CHARACTER_UNREAD",
    /** The candidate's character is outside the shipped relation domain. */
    "CHARACTER_NOT_IN_RELATION_DOMAIN",
    /** The target trainee is outside the shipped relation domain. */
    "TARGET_NOT_IN_RELATION_DOMAIN",
    /** No Legacy Origin block was captured for this Veteran. */
    "LEGACY_BLOCKS_UNCAPTURED",
    /** Legacy blocks exist, but who the ancestors are is not readable from the panel. */
    "LEGACY_ANCESTOR_IDENTITY_UNKNOWN",
    /** A roster aptitude the build cares about did not read. */
    "TARGET_APTITUDE_GRADE_UNREAD",
    /** The scarcity numbers describe the captured subset, not the whole account. */
    "SCARCITY_NOT_ACCOUNT_WIDE",
] as const
export type CandidateGap = (typeof CANDIDATE_GAPS)[number]

/** One owned Veteran as a parent candidate under one target build. */
export interface ParentCandidate {
    readonly rosterFingerprint: string | null
    readonly scanIndex: number
    readonly character: string | null
    readonly outfit: string | null
    readonly rank: string | null
    /** Carried for display only. Never a ranking input. */
    readonly rating: number | null
    readonly charaId: number | null
    readonly sameCharacterAsTarget: boolean
    /** Roster grades on the aptitude keys the build named, with their ordinals. */
    readonly targetAptitudes: Readonly<Record<string, { readonly grade: string | null; readonly rank: number }>>
    readonly statTotal: number | null
    /** Owned Veterans of the same character, including this one. */
    readonly characterCarriers: number
    readonly characterOutfitCarriers: number
    readonly selfFactorsTrusted: boolean
    readonly selfFactorCount: number | null
    readonly relevance: CandidateRelevance | null
    /** Distinct priority factors of any family this candidate's own Sparks cover. */
    readonly distinctPriorityFactors: number
    /** Matched priority factors carried by at most OBSERVED_SCARCE_MAX_CARRIERS owned Veterans. */
    readonly rareRelevantFactors: readonly CandidateFactorMatch[]
    readonly affinity: CandidateAffinity
    readonly legacy: CandidateLegacySupport
    readonly gaps: readonly CandidateGap[]
    /** Fraction (0..1) of the four evidence dimensions that are present: identity, self factors,
     * affinity, legacy blocks. Monotone with evidence; never a quality judgement. */
    readonly completeness: number
}

function emptyFamily(family: FactorFamily): CandidateFamilyRelevance {
    return { family, matched: [], distinctFactors: 0, maxStars: 0, totalStars: 0 }
}

function scarcityOf(carriers: number, accountWide: boolean): ScarcityClaim {
    if (carriers <= 0) return "UNMEASURED"
    if (carriers === 1) return accountWide ? "ACCOUNT_UNIQUE" : "OBSERVED_UNIQUE"
    if (carriers <= OBSERVED_SCARCE_MAX_CARRIERS) return "OBSERVED_SCARCE"
    return "OBSERVED_COMMON"
}

/**
 * Projects a factor list onto a build's priority lists, per family.
 *
 * A factor carried twice at different star counts contributes once per family at its best star count:
 * the question a parent answers is what it can pass on, and the same factor listed twice is still one
 * factor. `null` factors produce `null`, never an all-zero relevance, because an unmeasured Veteran is
 * not a Veteran that carries nothing.
 */
export function candidateRelevance(factors: readonly InspirationFactorRecord[] | null, build: TargetBuild, scarcity: FactorScarcityIndex): CandidateRelevance | null {
    if (!factors) return null
    const out = {} as Record<FactorFamily, CandidateFamilyRelevance>
    for (const family of FACTOR_FAMILIES) {
        const wanted = new Set(PRIORITY_LIST_BY_FAMILY[family](build).map((n) => n.toUpperCase()))
        if (wanted.size === 0) {
            out[family] = emptyFamily(family)
            continue
        }
        const best = new Map<string, InspirationFactorRecord>()
        for (const factor of factors) {
            if (factor.kind !== family || !factor.canonicalName) continue
            if (!wanted.has(factor.canonicalName.toUpperCase())) continue
            const key = factorKey(factor)
            if (!key) continue
            const held = best.get(key)
            if (!held || factor.stars > held.stars) best.set(key, factor)
        }
        const matched: CandidateFactorMatch[] = [...best.entries()]
            .map(([key, factor]) => {
                const carriers = carriersAtOrAbove(scarcity, key, factor.stars)
                return {
                    factorKey: key,
                    family,
                    canonicalName: factor.canonicalName as string,
                    stars: factor.stars,
                    observedCarriersAtStars: carriers,
                    scarcity: scarcityOf(carriers, scarcity.accountWide),
                }
            })
            .sort((a, b) => (a.factorKey < b.factorKey ? -1 : a.factorKey > b.factorKey ? 1 : 0))
        out[family] = {
            family,
            matched,
            distinctFactors: matched.length,
            maxStars: matched.reduce((m, f) => (f.stars > m ? f.stars : m), 0),
            totalStars: matched.reduce((s, f) => s + f.stars, 0),
        }
    }
    return out
}

/** Every matched factor across the families, in family order then key order. */
export function allMatches(relevance: CandidateRelevance | null): readonly CandidateFactorMatch[] {
    if (!relevance) return []
    const out: CandidateFactorMatch[] = []
    for (const family of FACTOR_FAMILIES) out.push(...relevance[family].matched)
    return out
}

/**
 * Builds one candidate record.
 *
 * The legacy blocks are projected with the same machinery as the self factors, against the same
 * scarcity index. That index counts SELF carriers only, which is the honest reading: a factor sitting
 * in an ancestry block is not a factor an owned Veteran can be selected to pass on directly, so it
 * must not inflate the account's supply of that factor.
 */
export function buildParentCandidate(evidence: VeteranEvidence, build: TargetBuild, scarcity: FactorScarcityIndex, index: SuccessionRelationIndex): ParentCandidate {
    const entry = evidence.entry
    const charaId = resolveCharaId(index, entry.character)
    const sameCharacterAsTarget = charaId !== null && build.targetCharaId !== null && charaId === build.targetCharaId

    const targetAptitudes: Record<string, { grade: string | null; rank: number }> = {}
    let aptitudeUnread = false
    for (const key of build.rosterAptitudeKeys) {
        const grade = entry.aptitudes[key as RosterAptitudeKey] ?? null
        const rank = aptitudeGradeRank(grade)
        if (rank < 0) aptitudeUnread = true
        targetAptitudes[key] = { grade, rank }
    }

    const relevance = candidateRelevance(evidence.selfFactors, build, scarcity)
    const matches = allMatches(relevance)
    const rareRelevantFactors = matches.filter((m) => m.observedCarriersAtStars > 0 && m.observedCarriersAtStars <= OBSERVED_SCARCE_MAX_CARRIERS)

    let affinity: CandidateAffinity
    if (build.targetCharaId === null) {
        affinity = { resolved: false, points: null, sharedRelationTypes: null, unresolvedReason: "TARGET_NOT_IN_RELATION_DOMAIN" }
    } else if (!entry.character) {
        affinity = { resolved: false, points: null, sharedRelationTypes: null, unresolvedReason: "CANDIDATE_CHARACTER_UNREAD" }
    } else if (charaId === null) {
        affinity = { resolved: false, points: null, sharedRelationTypes: null, unresolvedReason: "CANDIDATE_NOT_IN_RELATION_DOMAIN" }
    } else if (sameCharacterAsTarget) {
        // A character is never related to itself in these tables, and summing its own memberships
        // would produce a large meaningless number. Unresolved is the truthful answer.
        affinity = { resolved: false, points: null, sharedRelationTypes: null, unresolvedReason: "SAME_CHARACTER_AS_TARGET" }
    } else {
        const pair = pairwiseRelation(index, build.targetCharaId, charaId)
        affinity = pair
            ? { resolved: true, points: pair.points, sharedRelationTypes: pair.sharedRelationTypes.length, unresolvedReason: null }
            : { resolved: false, points: null, sharedRelationTypes: null, unresolvedReason: "CANDIDATE_NOT_IN_RELATION_DOMAIN" }
    }

    const legacyBlocks = evidence.capture?.legacyAncestorFactors ?? null
    let legacy: CandidateLegacySupport
    if (!legacyBlocks || legacyBlocks.length === 0) {
        legacy = { blocksObserved: legacyBlocks ? 0 : null, ancestorIdentityKnown: false, relevance: null, distinctPriorityFactors: 0, totalPriorityStars: 0 }
    } else {
        // The blocks are merged into one factor list before projection: which of the two ancestors a
        // factor came from is not a question the advisor can answer usefully while both are anonymous.
        const merged = legacyBlocks.flat()
        const legacyRelevance = candidateRelevance(merged, build, scarcity)
        const legacyMatches = allMatches(legacyRelevance)
        legacy = {
            blocksObserved: legacyBlocks.length,
            ancestorIdentityKnown: false,
            relevance: legacyRelevance,
            distinctPriorityFactors: legacyMatches.length,
            totalPriorityStars: legacyMatches.reduce((s, m) => s + m.stars, 0),
        }
    }

    const gaps: CandidateGap[] = []
    if (!evidence.rosterFingerprint) gaps.push("IDENTITY_UNRESOLVED")
    if (!evidence.selfFactors) gaps.push("NO_TRUSTED_FACTOR_CAPTURE")
    if (!entry.character) gaps.push("CHARACTER_UNREAD")
    else if (charaId === null) gaps.push("CHARACTER_NOT_IN_RELATION_DOMAIN")
    if (build.targetCharaId === null) gaps.push("TARGET_NOT_IN_RELATION_DOMAIN")
    if (legacy.blocksObserved === null) gaps.push("LEGACY_BLOCKS_UNCAPTURED")
    else if (legacy.blocksObserved > 0) gaps.push("LEGACY_ANCESTOR_IDENTITY_UNKNOWN")
    if (aptitudeUnread) gaps.push("TARGET_APTITUDE_GRADE_UNREAD")
    if (!scarcity.accountWide) gaps.push("SCARCITY_NOT_ACCOUNT_WIDE")

    const present = [evidence.rosterFingerprint !== null, evidence.selfFactors !== null, affinity.resolved, legacy.blocksObserved !== null && legacy.blocksObserved > 0].filter(Boolean).length

    return {
        rosterFingerprint: evidence.rosterFingerprint,
        scanIndex: entry.scanIndex,
        character: entry.character,
        outfit: entry.outfit,
        rank: entry.rank,
        rating: entry.rating,
        charaId,
        sameCharacterAsTarget,
        targetAptitudes,
        statTotal: evidence.statTotal,
        characterCarriers: evidence.characterCarriers,
        characterOutfitCarriers: evidence.characterOutfitCarriers,
        selfFactorsTrusted: evidence.selfFactors !== null,
        selfFactorCount: evidence.selfFactors ? evidence.selfFactors.length : null,
        relevance,
        distinctPriorityFactors: matches.length,
        rareRelevantFactors,
        affinity,
        legacy,
        gaps,
        completeness: Math.round((present / 4) * 10000) / 10000,
    }
}

/** Builds a candidate for every Veteran in an evidence set, in traversal order. */
export function buildParentCandidates(
    veterans: readonly VeteranEvidence[],
    build: TargetBuild,
    scarcity: FactorScarcityIndex,
    index: SuccessionRelationIndex,
): readonly ParentCandidate[] {
    return veterans.map((v) => buildParentCandidate(v, build, scarcity, index))
}
