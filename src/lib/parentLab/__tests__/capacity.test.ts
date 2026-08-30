import { buildCapacityTriage, buildCapacityTriageRecord, capacityExclusionsFor, normalizeCapacityEvidence } from "../capacityEvidence.ts"
import { CAPACITY_ADMISSIONS, PARENTLAB_CAPACITY_KIND, PARENTLAB_CAPACITY_SCHEMA, isCapacityTriageDocument } from "../capacityTypes.ts"
import { retentionReportsOf } from "../quarantineSnapshot.ts"
import { PARENTLAB_RETENTION_SCHEMA, PARENTLAB_RETENTION_SCHEMA_VERSION, RETENTION_STATES } from "../retentionTypes.ts"
import type { RetentionDataCompleteness, RetentionShadowReport, RetentionValueSummary, VeteranRetentionRecommendation } from "../retentionTypes.ts"

// Synthetic recommendations only: every branch of the capacity gate is exercised against a
// hand-built retention document. No real roster data is used.

function completeness(o: Partial<RetentionDataCompleteness> = {}): RetentionDataCompleteness {
    return {
        rosterTrusted: true,
        identityResolved: true,
        inspirationCaptured: true,
        inspirationComplete: true,
        inspirationTrusted: true,
        historicalMatched: true,
        protectionKnown: true,
        scarcityAccountWide: false,
        score: 0.875,
        ...o,
    }
}

function valueSummary(o: Partial<RetentionValueSummary> = {}): RetentionValueSummary {
    return {
        statFactorStars: 3,
        aptitudeFactorStars: 2,
        uniqueFactorStars: 1,
        whiteFactorCount: 2,
        totalFactorStars: 8,
        scarcestClaim: "OBSERVED_SCARCE",
        observedUniqueFactorKeys: [],
        selfFactors: [{ factorKey: "STAT:Speed", stars: 3 }],
        lineageAncestorsObserved: 1,
        rating: 15000,
        ...o,
    }
}

let seq = 0
function rec(o: Partial<VeteranRetentionRecommendation> = {}): VeteranRetentionRecommendation {
    const scanIndex = o.scanIndex ?? seq++
    return {
        rosterFingerprint: `fp-${scanIndex}`,
        scanIndex,
        character: "Taiki Shuttle",
        outfit: "Wild Frontier",
        rank: "S",
        identityMultiplicity: 1,
        stats: { spd: 900, sta: 700, pwr: 650, grt: 600, wit: 500 },
        favoriteState: "not_set",
        protectionState: "not_protected",
        state: "KEEP",
        confidence: "MEDIUM",
        hardProtectReasons: [],
        gateReasons: [],
        keepReasons: ["HIGH_VALUE_FACTOR_SET"],
        riskReasons: [],
        factorValueSummary: valueSummary(),
        coverageSummary: { character: "Taiki Shuttle", characterCarriers: 2, characterOutfitCarriers: 1, targetsCovered: ["GENERAL_INHERITANCE"], soleTargetCoverage: [] },
        replacement: { difficulty: "MODERATE", historicalSamples: 3, historicalAtOrAbove: 1, statTotal: 3350, historicalMatchStatus: "PROBABLE", basis: "3 historical careers" },
        dominators: [],
        substitutes: [],
        dataCompleteness: completeness(),
        unknownEvidence: [],
        explanation: "synthetic",
        ...o,
    }
}

function report(recs: VeteranRetentionRecommendation[], o: Partial<RetentionShadowReport> = {}): RetentionShadowReport {
    const counts = Object.fromEntries(RETENTION_STATES.map((s) => [s, 0])) as Record<(typeof RETENTION_STATES)[number], number>
    for (const r of recs) counts[r.state]++
    return {
        schema: PARENTLAB_RETENTION_SCHEMA,
        schemaVersion: PARENTLAB_RETENTION_SCHEMA_VERSION,
        rosterScanId: "rs-cap-0001",
        protectionScanId: "ps-cap-0001",
        rosterFingerprint: "rs-cap-0001:3/3",
        generatedAt: Date.UTC(2026, 7, 29, 12, 0, 0),
        targetProfile: "GENERAL_INHERITANCE",
        counts,
        scarcity: {
            schema: PARENTLAB_RETENTION_SCHEMA,
            schemaVersion: PARENTLAB_RETENTION_SCHEMA_VERSION,
            identifiedRosterEntries: recs.length,
            capturedTrusted: recs.filter((r) => r.factorValueSummary.selfFactors !== null).length,
            capturedUntrusted: recs.filter((r) => r.factorValueSummary.selfFactors === null).length,
            coverage: 0.75,
            accountWide: false,
            entries: [],
            unresolvedFactorReads: 0,
        },
        recommendations: recs,
        inactiveRules: [],
        replacementEvidence: null,
        ...o,
    }
}

beforeEach(() => {
    seq = 0
})

describe("capacity eligibility gate", () => {
    test("KEEP with trusted identity, clear protection and trusted self-factors is admitted", () => {
        expect(capacityExclusionsFor(rec({ state: "KEEP" }))).toEqual([])
        const r = buildCapacityTriageRecord(rec({ state: "KEEP" }))
        expect(r.admission).toBe("ELIGIBLE_FOR_MANUAL_REVIEW")
        expect(r.strictState).toBe("KEEP")
    })

    test("SAFE_TO_TRANSFER is admitted and its strict state is carried verbatim", () => {
        const r = buildCapacityTriageRecord(rec({ state: "SAFE_TO_TRANSFER", confidence: "HIGH" }))
        expect(r.admission).toBe("ELIGIBLE_FOR_MANUAL_REVIEW")
        expect(r.strictState).toBe("SAFE_TO_TRANSFER")
    })

    test("HARD_PROTECT is excluded by strict state", () => {
        expect(capacityExclusionsFor(rec({ state: "HARD_PROTECT", hardProtectReasons: ["SOLE_CHARACTER_SOURCE"] }))).toContain("STRICT_HARD_PROTECT")
    })

    test("a hard-protect reason excludes even when the label is not HARD_PROTECT", () => {
        expect(capacityExclusionsFor(rec({ state: "KEEP", hardProtectReasons: ["PROTECTED_ON_ACCOUNT"] }))).toContain("STRICT_HARD_PROTECT")
    })

    test("strict UNKNOWN is excluded", () => {
        expect(capacityExclusionsFor(rec({ state: "UNKNOWN" }))).toContain("STRICT_STATE_UNKNOWN")
    })

    test("known favorite/memo (protected on account) is excluded", () => {
        expect(capacityExclusionsFor(rec({ protectionState: "protected" }))).toContain("PROTECTED_ON_ACCOUNT")
        expect(capacityExclusionsFor(rec({ favoriteState: "favorite", protectionState: "not_protected" }))).toContain("PROTECTED_ON_ACCOUNT")
    })

    test("protection unknown/untrusted is excluded, and not double-counted with PROTECTED_ON_ACCOUNT", () => {
        const unknown = capacityExclusionsFor(rec({ protectionState: "unknown", favoriteState: "unknown" }))
        expect(unknown).toContain("PROTECTION_STATE_UNKNOWN")
        expect(unknown).not.toContain("PROTECTED_ON_ACCOUNT")

        const known = capacityExclusionsFor(rec({ protectionState: "protected", favoriteState: "unknown" }))
        expect(known).toContain("PROTECTED_ON_ACCOUNT")
        expect(known).not.toContain("PROTECTION_STATE_UNKNOWN")
    })

    test("ambiguous identity (multiplicity > 1) is excluded", () => {
        expect(capacityExclusionsFor(rec({ identityMultiplicity: 2 }))).toContain("ROSTER_IDENTITY_AMBIGUOUS")
    })

    test("unresolved roster identity is excluded", () => {
        expect(capacityExclusionsFor(rec({ rosterFingerprint: null, dataCompleteness: completeness({ identityResolved: false }) }))).toContain("ROSTER_IDENTITY_UNRESOLVED")
    })

    test("untrusted roster snapshot is excluded", () => {
        expect(capacityExclusionsFor(rec({ dataCompleteness: completeness({ rosterTrusted: false }) }))).toContain("ROSTER_SNAPSHOT_UNTRUSTED")
    })

    test("missing self-factor evidence is excluded", () => {
        expect(capacityExclusionsFor(rec({ factorValueSummary: valueSummary({ selfFactors: null }), gateReasons: ["INSPIRATION_CAPTURE_MISSING"] }))).toContain("SELF_FACTOR_EVIDENCE_UNTRUSTED")
        expect(capacityExclusionsFor(rec({ gateReasons: ["INSPIRATION_FACTORS_UNTRUSTED"] }))).toContain("SELF_FACTOR_EVIDENCE_UNTRUSTED")
    })

    test("rating and rank alone do not change admission", () => {
        const low = buildCapacityTriageRecord(rec({ rank: "C", factorValueSummary: valueSummary({ rating: 1 }) }))
        const high = buildCapacityTriageRecord(rec({ rank: "SS", factorValueSummary: valueSummary({ rating: 99999 }) }))
        expect(low.admission).toBe("ELIGIBLE_FOR_MANUAL_REVIEW")
        expect(high.admission).toBe("ELIGIBLE_FOR_MANUAL_REVIEW")
    })

    test("reasons are enum-ordered and de-duplicated", () => {
        const reasons = capacityExclusionsFor(rec({ state: "UNKNOWN", identityMultiplicity: 3, gateReasons: ["INSPIRATION_CAPTURE_MISSING"], factorValueSummary: valueSummary({ selfFactors: null }) }))
        expect(reasons).toEqual(["STRICT_STATE_UNKNOWN", "ROSTER_IDENTITY_AMBIGUOUS", "SELF_FACTOR_EVIDENCE_UNTRUSTED"])
    })
})

describe("partial Inspiration coverage", () => {
    test("one Veteran with missing Inspiration is excluded without rejecting fully-evidenced peers", () => {
        const good1 = rec({ scanIndex: 0 })
        const good2 = rec({ scanIndex: 1 })
        const missing = rec({ scanIndex: 2, factorValueSummary: valueSummary({ selfFactors: null }), gateReasons: ["INSPIRATION_CAPTURE_MISSING"] })
        const doc = buildCapacityTriage(report([good1, good2, missing]))
        expect(doc.rosterCount).toBe(3)
        expect(doc.admittedCount).toBe(2)
        expect(doc.excludedCount).toBe(1)
        expect(doc.exclusionHistogram.SELF_FACTOR_EVIDENCE_UNTRUSTED).toBe(1)
    })

    test("incomplete account-wide coverage does not globally zero the pool", () => {
        const doc = buildCapacityTriage(report([rec(), rec(), rec()]))
        expect(doc.evidenceSummary.accountWide).toBe(false)
        expect(doc.admittedCount).toBe(3)
    })
})

describe("capacity document accounting", () => {
    test("admitted + excluded reconciles to roster count", () => {
        const recs = [rec({ state: "KEEP" }), rec({ state: "HARD_PROTECT", hardProtectReasons: ["MANUAL_PROTECT"] }), rec({ state: "UNKNOWN" }), rec({ protectionState: "unknown", favoriteState: "unknown" })]
        const doc = buildCapacityTriage(report(recs))
        expect(doc.admittedCount + doc.excludedCount).toBe(doc.rosterCount)
        expect(doc.rosterCount).toBe(4)
        expect(doc.admittedCount).toBe(1)
    })

    test("strict-state distributions partition the pool and every excluded record has a reason", () => {
        const recs = [rec({ state: "KEEP" }), rec({ state: "HARD_PROTECT", hardProtectReasons: ["MANUAL_PROTECT"] }), rec({ state: "UNKNOWN" })]
        const doc = buildCapacityTriage(report(recs))
        const admittedTotal = Object.values(doc.admittedStrictStateDistribution).reduce((a, b) => a + b, 0)
        const excludedTotal = Object.values(doc.excludedStrictStateDistribution).reduce((a, b) => a + b, 0)
        expect(admittedTotal).toBe(doc.admittedCount)
        expect(excludedTotal).toBe(doc.excludedCount)
        for (const r of doc.records) {
            if (r.admission === "EXCLUDED_FROM_MANUAL_REVIEW") expect(r.exclusionReasons.length).toBeGreaterThan(0)
            else expect(r.exclusionReasons).toEqual([])
        }
    })

    test("output is deterministic for identical input", () => {
        const recs = () => [rec({ scanIndex: 0, state: "KEEP" }), rec({ scanIndex: 1, state: "REVIEW", riskReasons: ["DOMINATED_BY_PEER"] }), rec({ scanIndex: 2, state: "UNKNOWN" })]
        expect(JSON.stringify(buildCapacityTriage(report(recs())))).toBe(JSON.stringify(buildCapacityTriage(report(recs()))))
    })

    test("record order is preserved from the retention document", () => {
        const doc = buildCapacityTriage(report([rec({ scanIndex: 5 }), rec({ scanIndex: 2 }), rec({ scanIndex: 9 })]))
        expect(doc.records.map((r) => r.scanIndex)).toEqual([5, 2, 9])
    })

    test("an incompatible retention schema version fails closed", () => {
        expect(() => buildCapacityTriage(report([rec()], { schemaVersion: 1 as unknown as typeof PARENTLAB_RETENTION_SCHEMA_VERSION }))).toThrow(/schema version/)
    })

    test("the capacity output contract has no executor or scoring field", () => {
        const doc = buildCapacityTriage(report([rec()]))
        // `rank` is deliberately allowed: it is the in-game grade carried verbatim as context, not a
        // computed ranking. Everything below is a scoring/ledger/executor field this slice must not have.
        const forbidden = ["action", "execute", "transfer", "delete", "release", "favorite", "approve", "residual", "score", "weight", "tier", "opportunityCost", "minResidual", "targetFreeSlots"]
        const recordKeys = Object.keys(doc.records[0])
        for (const key of forbidden) expect(recordKeys).not.toContain(key)
        expect(new Set(doc.records.map((r) => r.admission)).size).toBeLessThanOrEqual(CAPACITY_ADMISSIONS.length)
        for (const r of doc.records) expect(CAPACITY_ADMISSIONS).toContain(r.admission)
    })

    test("normalized evidence carries facts, not a verdict", () => {
        const e = normalizeCapacityEvidence(rec())
        expect(e).toMatchObject({ identityResolved: true, rosterTrusted: true, protectionKnown: true, protectedOnAccount: false, selfFactorEvidenceTrusted: true, hardProtected: false })
    })
})

describe("structural isolation from retention/quarantine", () => {
    test("retentionReportsOf rejects a capacity document", () => {
        const doc = buildCapacityTriage(report([rec()]))
        expect(() => retentionReportsOf(doc)).toThrow(/schema/)
    })

    test("a capacity document is not a retention document and vice versa", () => {
        const capacity = buildCapacityTriage(report([rec()]))
        const retention = report([rec()])
        expect(isCapacityTriageDocument(capacity)).toBe(true)
        expect(isCapacityTriageDocument(retention)).toBe(false)
        expect(capacity.schema).toBe(PARENTLAB_CAPACITY_SCHEMA)
        expect(capacity.kind).toBe(PARENTLAB_CAPACITY_KIND)
        expect(capacity.schema).not.toBe(PARENTLAB_RETENTION_SCHEMA)
    })

    test("a retention document is still accepted by its own reader (isolation is one-way by schema)", () => {
        const retention = report([rec()])
        const wrapped = { schema: PARENTLAB_RETENTION_SCHEMA, schemaVersion: PARENTLAB_RETENTION_SCHEMA_VERSION, reports: [retention] }
        expect(retentionReportsOf(wrapped)).toHaveLength(1)
    })
})
