// ParentLab PL-R4 - the shadow affinity / lineage advisor document. Pure, offline, deterministic,
// read-only.
//
// One document per run, one section per target build. It answers "which owned Veterans are the
// strongest parent pair for this build, and why", and it answers it in components: every figure that
// enters a ranking is preserved next to the recommendation, and every mechanic that could not be
// measured is named in the same document.
//
// The advisor never launches an inheritance, never selects parents in game, never touches the device
// and never rewrites its inputs. A recommendation here is a line in a report.
//
// Determinism: `generatedAt` is the newest observation time carried by the evidence, never a wall
// clock, so two rebuilds from the same inputs are byte-identical.

import { affinityDataProvenance, AFFINITY_MECHANIC_EVIDENCE, UNKNOWN_AFFINITY_COMPONENTS, type AffinityMechanicEvidence } from "./affinityEvidence.ts"
import type { SuccessionRelationIndex } from "./affinityData.ts"
import { buildParentCandidates, type CandidateFactorMatch, type ParentCandidate } from "./parentCandidate.ts"
import {
    affinityMedianOf,
    balanceIndex,
    enumerateParentPairs,
    PAIR_CONFIDENCES,
    rankParentPairs,
    type FrontierEntry,
    type InactiveFrontierCategory,
    type PairConfidence,
    type PairDimensions,
    type PairReasonCode,
    type ParentPair,
    type PairFactorCoverage,
} from "./parentPairing.ts"
import type { RetentionEvidenceSet } from "./retentionEvidence.ts"
import type { FactorScarcityIndex } from "./retentionTypes.ts"
import type { TargetBuild } from "./targetBuild.ts"

/** Schema discriminator + version for the advisor document. Separate from every other ParentLab
 * schema: this stage reads them and writes none of them. */
export const PARENTLAB_AFFINITY_SCHEMA = "parent_lab_affinity_advisor" as const
export const PARENTLAB_AFFINITY_SCHEMA_VERSION = 1 as const

/** Default number of pairs listed per target. */
export const DEFAULT_TOP_PAIRS = 10

/** A candidate projected for the document. The full record stays in memory; this is what is written. */
export interface ReportedCandidate {
    readonly rosterFingerprint: string | null
    readonly character: string | null
    readonly outfit: string | null
    readonly rank: string | null
    /** Display only. Never a ranking input anywhere in this stage. */
    readonly rating: number | null
    readonly charaId: number | null
    readonly sameCharacterAsTarget: boolean
    readonly selfFactorsTrusted: boolean
    readonly targetAptitudes: Readonly<Record<string, { readonly grade: string | null; readonly rank: number }>>
    readonly affinityPoints: number | null
    readonly affinityUnresolvedReason: string | null
    readonly matchedFactors: readonly CandidateFactorMatch[]
    readonly legacyBlocksObserved: number | null
    readonly legacyPriorityStars: number
    readonly legacyAncestorIdentityKnown: false
    readonly characterCarriers: number
    readonly gaps: readonly string[]
}

/** The explainability record for one pair. Every component that produced the ranking is here. */
export interface ReportedPair {
    readonly pairKey: string
    readonly parentA: ReportedCandidate
    readonly parentB: ReportedCandidate
    readonly dimensions: PairDimensions
    readonly balanceIndex: number
    readonly knownAffinity: {
        readonly resolved: boolean
        readonly components: readonly { readonly id: string; readonly label: string; readonly points: number; readonly sharedRelationTypes: number }[]
        readonly knownPointsTotal: number
        readonly unknownComponents: readonly string[]
        /** Always false. The game's affinity total is not decoded in this repository. */
        readonly isGameAffinityTotal: false
        /** Computable, inclusion in the game total not decoded, so it stands on its own. */
        readonly parentToParentRelationPoints: number | null
    }
    readonly factorCoverage: readonly PairFactorCoverage[]
    readonly sharedPriorityFactors: number
    readonly confidence: PairConfidence
    readonly reasonCodes: readonly PairReasonCode[]
    readonly explanation: string
    readonly gaps: readonly string[]
}

export interface AffinityTargetReport {
    readonly build: TargetBuild
    readonly candidates: {
        readonly total: number
        readonly identified: number
        readonly selfFactorsTrusted: number
        readonly affinityResolved: number
        readonly sameCharacterAsTarget: number
        readonly carryingAnyPriorityFactor: number
    }
    /** Median relation points from the target to a single owned parent, across resolved candidates. */
    readonly affinityMedianPerParent: number | null
    readonly pairsEvaluated: number
    readonly frontier: readonly FrontierEntry[]
    /** Categories that separated nothing under this build, with the reason. */
    readonly inactiveFrontierCategories: readonly InactiveFrontierCategory[]
    /** Pairs named by the frontier or the top list, keyed by pairKey, sorted by key. */
    readonly pairs: readonly ReportedPair[]
    readonly topPairKeys: readonly string[]
    readonly recommendation: {
        readonly kind: "DOMINANT_PAIR" | "NO_DOMINANT_PAIR"
        readonly pairKey: string | null
        readonly note: string
    }
    readonly confidenceCounts: Readonly<Record<PairConfidence, number>>
    /** What this target's answer is missing, in reason codes. */
    readonly missingEvidence: readonly string[]
}

export interface AffinityAdvisorReport {
    readonly schema: typeof PARENTLAB_AFFINITY_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_AFFINITY_SCHEMA_VERSION
    /** Newest observation time across the inputs. Never a wall clock. */
    readonly generatedAt: number | null
    readonly rosterScanId: string
    readonly rosterTrustedComplete: boolean
    readonly relationDataProvenance: string
    readonly coverage: {
        readonly identifiedRosterEntries: number
        readonly capturedTrusted: number
        readonly coverage: number
        readonly accountWide: boolean
        readonly distinctFactors: number
    }
    readonly affinityEvidence: readonly AffinityMechanicEvidence[]
    readonly unknownAffinityComponents: readonly string[]
    readonly targets: readonly AffinityTargetReport[]
}

export interface AffinityAdvisorInput {
    readonly evidence: RetentionEvidenceSet
    readonly scarcity: FactorScarcityIndex
    readonly relations: SuccessionRelationIndex
    readonly builds: readonly TargetBuild[]
    readonly topCount?: number
}

function projectCandidate(c: ParentCandidate): ReportedCandidate {
    const matched: CandidateFactorMatch[] = []
    if (c.relevance) {
        for (const family of ["stat", "aptitude", "unique", "white"] as const) matched.push(...c.relevance[family].matched)
    }
    return {
        rosterFingerprint: c.rosterFingerprint,
        character: c.character,
        outfit: c.outfit,
        rank: c.rank,
        rating: c.rating,
        charaId: c.charaId,
        sameCharacterAsTarget: c.sameCharacterAsTarget,
        selfFactorsTrusted: c.selfFactorsTrusted,
        targetAptitudes: c.targetAptitudes,
        affinityPoints: c.affinity.points,
        affinityUnresolvedReason: c.affinity.unresolvedReason,
        matchedFactors: matched,
        legacyBlocksObserved: c.legacy.blocksObserved,
        legacyPriorityStars: c.legacy.totalPriorityStars,
        legacyAncestorIdentityKnown: false,
        characterCarriers: c.characterCarriers,
        gaps: c.gaps,
    }
}

function projectPair(pair: ParentPair, balance: number): ReportedPair {
    return {
        pairKey: pair.pairKey,
        parentA: projectCandidate(pair.parentA),
        parentB: projectCandidate(pair.parentB),
        dimensions: pair.dimensions,
        balanceIndex: balance,
        knownAffinity: {
            resolved: pair.affinityResolved,
            components: pair.affinity.known,
            knownPointsTotal: pair.affinity.knownPointsTotal,
            unknownComponents: pair.affinity.unknown,
            isGameAffinityTotal: false,
            parentToParentRelationPoints: pair.parentPairRelationPoints,
        },
        factorCoverage: pair.coverage,
        sharedPriorityFactors: pair.sharedPriorityFactors,
        confidence: pair.confidence,
        reasonCodes: pair.reasonCodes,
        explanation: pair.explanation,
        gaps: pair.gaps,
    }
}

/** Builds the report for one target build. */
export function buildAffinityTargetReport(input: AffinityAdvisorInput, build: TargetBuild): AffinityTargetReport {
    const candidates = buildParentCandidates(input.evidence.veterans, build, input.scarcity, input.relations)
    const median = affinityMedianOf(candidates)
    const pairs = enumerateParentPairs(candidates, build, input.relations, input.scarcity, median)
    const ranking = rankParentPairs(pairs, input.topCount ?? DEFAULT_TOP_PAIRS)

    const confidenceCounts = {} as Record<PairConfidence, number>
    for (const level of PAIR_CONFIDENCES) confidenceCounts[level] = 0
    for (const pair of pairs) confidenceCounts[pair.confidence]++

    const balanceByKey = new Map(ranking.topPairs.map((t) => [t.pairKey, t.balanceIndex]))
    const reported: ReportedPair[] = [...ranking.pairsByKey.values()]
        .map((pair) => projectPair(pair, balanceByKey.get(pair.pairKey) ?? balanceIndex(pair.dimensions, ranking.dimensionMins, ranking.dimensionMaxs)))
        .sort((a, b) => (a.pairKey < b.pairKey ? -1 : a.pairKey > b.pairKey ? 1 : 0))

    const missing = new Set<string>(UNKNOWN_AFFINITY_COMPONENTS)
    for (const gap of build.gaps) missing.add(`BUILD_${gap}`)
    if (!input.scarcity.accountWide) missing.add("SCARCITY_NOT_ACCOUNT_WIDE")
    if (candidates.some((c) => !c.selfFactorsTrusted)) missing.add("SOME_CANDIDATES_WITHOUT_TRUSTED_FACTORS")
    if (candidates.some((c) => !c.affinity.resolved)) missing.add("SOME_CANDIDATE_RELATIONS_UNRESOLVED")

    const recommendation = ranking.dominantPairKey
        ? {
              kind: "DOMINANT_PAIR" as const,
              pairKey: ranking.dominantPairKey,
              note: "This pair is at least as good as every other pair on every known dimension and strictly better on at least one. The unknown affinity components still apply.",
          }
        : {
              kind: "NO_DOMINANT_PAIR" as const,
              pairKey: null,
              note: "No pair dominates every other pair on every known dimension, so the frontier is the answer: pick the category that matches the project.",
          }

    return {
        build,
        candidates: {
            total: candidates.length,
            identified: candidates.filter((c) => c.rosterFingerprint !== null).length,
            selfFactorsTrusted: candidates.filter((c) => c.selfFactorsTrusted).length,
            affinityResolved: candidates.filter((c) => c.affinity.resolved).length,
            sameCharacterAsTarget: candidates.filter((c) => c.sameCharacterAsTarget).length,
            carryingAnyPriorityFactor: candidates.filter((c) => c.distinctPriorityFactors > 0).length,
        },
        affinityMedianPerParent: median,
        pairsEvaluated: ranking.pairsEvaluated,
        frontier: ranking.frontier,
        inactiveFrontierCategories: ranking.inactiveCategories,
        pairs: reported,
        topPairKeys: ranking.topPairs.map((t) => t.pairKey),
        recommendation,
        confidenceCounts,
        missingEvidence: [...missing].sort(),
    }
}

/** Builds the whole document, one section per build, in the order the builds were given. */
export function buildAffinityAdvisorReport(input: AffinityAdvisorInput): AffinityAdvisorReport {
    const snapshot = input.evidence.snapshot
    return {
        schema: PARENTLAB_AFFINITY_SCHEMA,
        schemaVersion: PARENTLAB_AFFINITY_SCHEMA_VERSION,
        generatedAt: input.evidence.observedAt,
        rosterScanId: snapshot.scanId,
        rosterTrustedComplete: snapshot.trustedComplete,
        relationDataProvenance: affinityDataProvenance(input.relations.data),
        coverage: {
            identifiedRosterEntries: input.scarcity.identifiedRosterEntries,
            capturedTrusted: input.scarcity.capturedTrusted,
            coverage: input.scarcity.coverage,
            accountWide: input.scarcity.accountWide,
            distinctFactors: input.scarcity.entries.length,
        },
        affinityEvidence: AFFINITY_MECHANIC_EVIDENCE,
        unknownAffinityComponents: UNKNOWN_AFFINITY_COMPONENTS,
        targets: input.builds.map((build) => buildAffinityTargetReport(input, build)),
    }
}
