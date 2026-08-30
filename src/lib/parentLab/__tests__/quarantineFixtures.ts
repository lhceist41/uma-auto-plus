// PL-R3 test fixtures. Not a test file: Jest collects `*.test.ts` only.
//
// PL-R3's input is the persisted PL-R2 document, so the fixtures here ARE retention documents, built
// through the exported types rather than through the device pipeline. That is deliberate: the account
// this repository has evidence for produces zero transfer candidates and must keep producing zero, so
// every non-empty behaviour - maturity, invalidation, batch safety, approval - can only be proven on
// synthetic evidence. Manufacturing a candidate out of the real roster to make a test pass is exactly
// the failure this stage exists to prevent.

import { ledgerEntry } from "../quarantineLedger.ts"
import { buildAdvisorSnapshot } from "../quarantineSnapshot.ts"
import type { AdvisorSnapshot, QuarantineLedger } from "../quarantineTypes.ts"
import { TARGET_PROFILE_IDS } from "../retentionTargets.ts"
import { PARENTLAB_RETENTION_SCHEMA, PARENTLAB_RETENTION_SCHEMA_VERSION, type ReplacementDifficulty, type RetentionConfidence, type RetentionShadowReport, type RetentionState, type VeteranRetentionRecommendation } from "../retentionTypes.ts"

export const PROFILES = [...TARGET_PROFILE_IDS]
export const T0 = Date.UTC(2026, 7, 20, 12, 0, 0)
export const DAY = 86400000

export interface VetSpec {
    fp: string
    scanIndex?: number
    character?: string | null
    outfit?: string | null
    rank?: string | null
    stats?: Record<string, number | null>
    multiplicity?: number
    favoriteState?: string
    protectionState?: string
    state?: RetentionState
    confidence?: RetentionConfidence
    gateReasons?: string[]
    hardProtectReasons?: string[]
    observedUnique?: string[]
    dominators?: string[]
    substitutes?: string[]
    replacement?: ReplacementDifficulty
    factors?: { factorKey: string; stars: number }[]
    targetsCovered?: string[]
    rosterTrusted?: boolean
}

export function recommendation(spec: VetSpec, index: number): VeteranRetentionRecommendation {
    const factors = spec.factors ?? [{ factorKey: "stat:SPEED", stars: 1 }]
    return {
        rosterFingerprint: spec.fp,
        scanIndex: spec.scanIndex ?? index,
        character: spec.character === undefined ? `Char ${spec.fp}` : spec.character,
        outfit: spec.outfit === undefined ? "Base" : spec.outfit,
        rank: spec.rank ?? "A",
        identityMultiplicity: spec.multiplicity ?? 1,
        stats: spec.stats ?? { spd: 900, sta: 700, pwr: 650, grt: 600, wit: 500 },
        favoriteState: spec.favoriteState ?? "not_set",
        protectionState: spec.protectionState ?? "not_protected",
        state: spec.state ?? "KEEP",
        confidence: spec.confidence ?? "HIGH",
        hardProtectReasons: (spec.hardProtectReasons ?? []) as never,
        gateReasons: (spec.gateReasons ?? []) as never,
        keepReasons: [],
        riskReasons: [],
        factorValueSummary: {
            statFactorStars: 1,
            aptitudeFactorStars: 1,
            uniqueFactorStars: 1,
            whiteFactorCount: 0,
            totalFactorStars: factors.reduce((s, f) => s + f.stars, 0),
            scarcestClaim: "OBSERVED_COMMON",
            observedUniqueFactorKeys: spec.observedUnique ?? [],
            selfFactors: factors,
            lineageAncestorsObserved: 0,
            rating: 15000,
        },
        coverageSummary: {
            character: spec.character === undefined ? `Char ${spec.fp}` : spec.character,
            characterCarriers: 2,
            characterOutfitCarriers: 2,
            targetsCovered: spec.targetsCovered ?? ["MILE_PARENT", "LONG_PARENT"],
            soleTargetCoverage: [],
        },
        replacement: {
            difficulty: spec.replacement ?? "MODERATE",
            historicalSamples: 5,
            historicalAtOrAbove: 2,
            statTotal: 3350,
            historicalMatchStatus: "EXACT_HISTORICAL_MATCH",
            basis: "synthetic fixture",
        },
        dominators: (spec.dominators ?? []).map((fp) => ({ rosterFingerprint: fp, character: null, outfit: null, strictlyBetterOn: ["totalFactorStars"], blockedBy: [], explanation: `${fp} dominates` })),
        substitutes: (spec.substitutes ?? []).map((fp) => ({ rosterFingerprint: fp, character: null, outfit: null, strictlyBetterOn: ["totalFactorStars"], blockedBy: ["SUBJECT_HARD_PROTECTED" as const], explanation: `${fp} substitutes` })),
        dataCompleteness: {
            rosterTrusted: spec.rosterTrusted ?? true,
            identityResolved: true,
            inspirationCaptured: true,
            inspirationComplete: true,
            inspirationTrusted: true,
            historicalMatched: true,
            protectionKnown: true,
            scarcityAccountWide: true,
            score: 1,
        },
        unknownEvidence: [],
        explanation: "synthetic fixture",
    }
}

export interface SnapshotOptions {
    accountWide?: boolean
    observedAt?: number
    profiles?: string[]
    schemaVersion?: number
}

export function reportsFor(scanId: string, specs: readonly VetSpec[], options: SnapshotOptions = {}): readonly RetentionShadowReport[] {
    const recs = specs.map(recommendation)
    return (options.profiles ?? PROFILES).map((targetProfile) => ({
        schema: PARENTLAB_RETENTION_SCHEMA,
        schemaVersion: (options.schemaVersion ?? PARENTLAB_RETENTION_SCHEMA_VERSION) as typeof PARENTLAB_RETENTION_SCHEMA_VERSION,
        rosterScanId: scanId,
        protectionScanId: "vp-fixture",
        rosterFingerprint: `${scanId}:${recs.length}/${recs.length}`,
        generatedAt: options.observedAt ?? T0,
        targetProfile,
        counts: {} as never,
        scarcity: {
            schema: PARENTLAB_RETENTION_SCHEMA,
            schemaVersion: PARENTLAB_RETENTION_SCHEMA_VERSION,
            identifiedRosterEntries: recs.length,
            capturedTrusted: recs.length,
            capturedUntrusted: 0,
            coverage: 1,
            accountWide: options.accountWide ?? true,
            entries: [],
            unresolvedFactorReads: 0,
        },
        recommendations: recs,
        inactiveRules: [],
        replacementEvidence: null,
    }))
}

export function snapshotOf(scanId: string, specs: readonly VetSpec[], options: SnapshotOptions = {}): AdvisorSnapshot {
    return buildAdvisorSnapshot(reportsFor(scanId, specs, options))
}

/**
 * A peer population that keeps the coverage rules satisfied while a candidate is under test.
 *
 * The shared character is load-bearing: a Veteran that is the only one of its character is the last
 * source of that character's unique factor, so a fixture where every Veteran had its own character
 * would make every batch fail for a reason unrelated to what the test is about.
 */
export const SHARED_CHARACTER = "Shared Trainee"

export function peers(): VetSpec[] {
    return [
        { fp: "peer-a", character: SHARED_CHARACTER, factors: [{ factorKey: "stat:SPEED", stars: 3 }] },
        { fp: "peer-b", character: SHARED_CHARACTER, factors: [{ factorKey: "stat:SPEED", stars: 3 }] },
    ]
}

/** The candidate the maturity tests follow: eligible under every profile. */
export function eligible(overrides: Partial<VetSpec> = {}): VetSpec {
    return {
        fp: "cand-1",
        character: SHARED_CHARACTER,
        state: "SAFE_TO_TRANSFER",
        confidence: "HIGH",
        dominators: ["peer-a"],
        replacement: "MODERATE",
        factors: [{ factorKey: "stat:SPEED", stars: 1 }],
        ...overrides,
    }
}

export function statusOf(ledger: QuarantineLedger, key: string): string {
    return ledgerEntry(ledger, key)?.status ?? "MISSING"
}
