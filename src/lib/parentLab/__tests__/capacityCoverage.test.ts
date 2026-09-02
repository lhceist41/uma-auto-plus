import { execFileSync } from "node:child_process"
import { mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { buildCapacityCoverage } from "../capacityCoverage.ts"
import {
    COVERAGE_EXPOSURES,
    COVERAGE_STAR_FLOORS,
    LAST_COPY_RISKS,
    PARENTLAB_CAPACITY_COVERAGE_KIND,
    PARENTLAB_CAPACITY_COVERAGE_SCHEMA,
    PARENTLAB_CAPACITY_COVERAGE_SCHEMA_VERSION,
    PERMANENT_COVERAGE_LIMITS,
    WHITE_SUBFAMILIES,
    isCapacityCoverageDocument,
    type WhiteFactorDomain,
} from "../capacityCoverageTypes.ts"
import { buildCapacityTriage } from "../capacityEvidence.ts"
import { PARENTLAB_CAPACITY_SCHEMA_VERSION, isCapacityTriageDocument } from "../capacityTypes.ts"
import { retentionReportsOf } from "../quarantineSnapshot.ts"
import { PARENTLAB_RETENTION_SCHEMA, PARENTLAB_RETENTION_SCHEMA_VERSION, RETENTION_STATES } from "../retentionTypes.ts"
import type { FactorScarcityEntry, ReplacementEvidenceProvenance, RetentionDataCompleteness, RetentionShadowReport, RetentionValueSummary, SelfFactorRef, VeteranRetentionRecommendation } from "../retentionTypes.ts"

// Synthetic fixtures only. No real roster, character, factor, or account data is embedded here: every
// factor key and character name below is invented. The real 257-Veteran corpus is exercised separately,
// read-only, by the Slice 2 offline acceptance run, never copied into source.

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

function valueSummary(selfFactors: readonly SelfFactorRef[] | null, o: Partial<RetentionValueSummary> = {}): RetentionValueSummary {
    return {
        statFactorStars: 3,
        aptitudeFactorStars: 2,
        uniqueFactorStars: 1,
        whiteFactorCount: 2,
        totalFactorStars: 8,
        scarcestClaim: "OBSERVED_SCARCE",
        observedUniqueFactorKeys: [],
        selfFactors,
        lineageAncestorsObserved: 1,
        rating: 15000,
        ...o,
    }
}

let seq = 0
function rec(o: Partial<VeteranRetentionRecommendation> & { selfFactors?: readonly SelfFactorRef[] | null } = {}): VeteranRetentionRecommendation {
    const scanIndex = o.scanIndex ?? seq++
    const selfFactors = o.selfFactors === undefined ? [{ factorKey: "stat:SYNTH_SPEED", stars: 3 }] : o.selfFactors
    const character = o.character === undefined ? "Synth Alpha" : o.character
    return {
        rosterFingerprint: `fp-${scanIndex}`,
        scanIndex,
        character,
        outfit: o.outfit === undefined ? "Synth Outfit One" : o.outfit,
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
        factorValueSummary: valueSummary(selfFactors),
        coverageSummary: { character, characterCarriers: 1, characterOutfitCarriers: 1, targetsCovered: o.coverageSummary?.targetsCovered ?? ["GENERAL_INHERITANCE"], soleTargetCoverage: [] },
        replacement: { difficulty: "MODERATE", historicalSamples: 3, historicalAtOrAbove: 1, statTotal: 3350, historicalMatchStatus: "PROBABLE", basis: "3 historical careers" },
        dominators: [],
        substitutes: [],
        dataCompleteness: completeness(o.dataCompleteness),
        unknownEvidence: [],
        explanation: "synthetic",
        ...o,
    }
}

/** Derives a scarcity index from the recs' trusted self factors, exactly as the real pipeline would:
 * a factor at N stars is a carrier for every floor <= N, counted only from trusted (selfFactors!==null)
 * captures. This keeps the fixture's observedCarriers consistent with the per-Veteran carrier evidence. */
function scarcityFrom(recs: VeteranRetentionRecommendation[]): FactorScarcityEntry[] {
    const byKey = new Map<string, { kind: string; canonicalName: string; starsPerVet: number[] }>()
    for (const r of recs) {
        const sf = r.factorValueSummary.selfFactors
        if (sf === null) continue
        for (const f of sf) {
            const colon = f.factorKey.indexOf(":")
            const kind = colon > 0 ? f.factorKey.slice(0, colon) : "white"
            const canonicalName = colon > 0 ? f.factorKey.slice(colon + 1) : f.factorKey
            let e = byKey.get(f.factorKey)
            if (!e) {
                e = { kind, canonicalName, starsPerVet: [] }
                byKey.set(f.factorKey, e)
            }
            e.starsPerVet.push(f.stars)
        }
    }
    return [...byKey.entries()]
        .sort((a, b) => a[0].localeCompare(b[0]))
        .map(([factorKey, e]) => {
            const carriersByMinStars: Record<string, number> = {}
            for (const floor of [1, 2, 3]) carriersByMinStars[String(floor)] = e.starsPerVet.filter((s) => s >= floor).length
            return {
                factorKey,
                kind: e.kind,
                canonicalName: e.canonicalName,
                observedCarriers: e.starsPerVet.length,
                carriersByMinStars,
                maxObservedStars: Math.max(0, ...e.starsPerVet),
            }
        })
}

function report(recs: VeteranRetentionRecommendation[], o: Partial<RetentionShadowReport> & { accountWide?: boolean; unresolvedFactorReads?: number; entries?: FactorScarcityEntry[] } = {}): RetentionShadowReport {
    const counts = Object.fromEntries(RETENTION_STATES.map((s) => [s, 0])) as Record<(typeof RETENTION_STATES)[number], number>
    for (const r of recs) counts[r.state]++
    const trusted = recs.filter((r) => r.factorValueSummary.selfFactors !== null).length
    return {
        schema: PARENTLAB_RETENTION_SCHEMA,
        schemaVersion: PARENTLAB_RETENTION_SCHEMA_VERSION,
        rosterScanId: "rs-cov-0001",
        protectionScanId: "ps-cov-0001",
        rosterFingerprint: `rs-cov-0001:${recs.length}/${recs.length}`,
        generatedAt: Date.UTC(2026, 7, 29, 12, 0, 0),
        targetProfile: "GENERAL_INHERITANCE",
        counts,
        scarcity: {
            schema: PARENTLAB_RETENTION_SCHEMA,
            schemaVersion: PARENTLAB_RETENTION_SCHEMA_VERSION,
            identifiedRosterEntries: recs.length,
            capturedTrusted: trusted,
            capturedUntrusted: recs.length - trusted,
            coverage: o.accountWide ? 1 : 0.75,
            accountWide: o.accountWide ?? true,
            entries: o.entries ?? scarcityFrom(recs),
            unresolvedFactorReads: o.unresolvedFactorReads ?? 0,
        },
        recommendations: recs,
        inactiveRules: [],
        replacementEvidence: null,
        ...o,
    }
}

/** A rec excluded by strict hard-protect but still carrying trusted factor evidence, so it anchors. */
function anchorRec(selfFactors: readonly SelfFactorRef[], o: Partial<VeteranRetentionRecommendation> = {}): VeteranRetentionRecommendation {
    return rec({ state: "HARD_PROTECT", hardProtectReasons: ["MANUAL_PROTECT"], selfFactors, ...o })
}

const slotByKeyFloor = (doc: ReturnType<typeof buildCapacityCoverage>, factorKey: string, floor: number) => doc.factorSlots.find((s) => s.factorKey === factorKey && s.starFloor === floor)

/** A synthetic replacement-evidence provenance block with the exact retention v3 field set. */
function provenance(o: Partial<ReplacementEvidenceProvenance> = {}): ReplacementEvidenceProvenance {
    return { confirmedVeterans: 7, traineeCount: 3, identityCollisions: 0, appVersions: ["1.4.0"], newestObservationTs: Date.UTC(2026, 7, 28, 9, 0, 0), ...o }
}

/** What JSON.parse of a hand-edited retention document can yield: the approved fields plus injected extras. */
function withExtras(): ReplacementEvidenceProvenance {
    return { ...provenance(), safeToDelete: true, fragility: 0.9, recommendation: "DELETE", nested: { transfer: true } } as unknown as ReplacementEvidenceProvenance
}

/** A synthetic white factor domain carrying the asset's identity fields. "Trackblazer" is public game
 * data and the required real-world ambiguity case: it exists in both the skill and scenario families of
 * the canonical domain asset. Overrides take arbitrary JSON so malformed-identity cases stay expressible. */
function domain(o: Record<string, unknown> = {}): WhiteFactorDomain {
    return {
        schemaVersion: 1,
        source: "synthetic test domain",
        skill: ["Synth Skill White", "Trackblazer"],
        race: ["Synth Race White"],
        scenario: ["Synth Scenario White", "Trackblazer"],
        ...o,
    } as unknown as WhiteFactorDomain
}

/** Decision, ranking, executor and destructive-advice field names this slice must never emit. */
const FORBIDDEN_CONTRACT_KEYS = ["action", "execute", "transfer", "delete", "release", "favorite", "approve", "score", "weight", "tier", "rank", "priority", "order", "opportunityCost", "targetFreeSlots", "recommendation", "safeToTransfer", "safeToDelete", "fragility"]

/** Every path in `value` whose key is exactly a forbidden name. Descends objects and arrays by index so a
 * decision field cannot hide below the top level. Exact-key matching only, never substring. */
function forbiddenKeyPaths(value: unknown, path = "$"): string[] {
    if (value === null || typeof value !== "object") return []
    if (Array.isArray(value)) return value.flatMap((item, i) => forbiddenKeyPaths(item, `${path}[${i}]`))
    return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) => (FORBIDDEN_CONTRACT_KEYS.includes(key) ? [`${path}.${key}`] : forbiddenKeyPaths(child, `${path}.${key}`)))
}

beforeEach(() => {
    seq = 0
})

describe("factor slot classification", () => {
    test("one admitted + one excluded trusted carrier is ANCHORED", () => {
        const admitted = rec({ selfFactors: [{ factorKey: "white:SHARED_A", stars: 3 }] })
        const anchored = anchorRec([{ factorKey: "white:SHARED_A", stars: 3 }])
        const doc = buildCapacityCoverage(report([admitted, anchored]))
        const slot = slotByKeyFloor(doc, "white:SHARED_A", 1)!
        expect(slot.exposure).toBe("ANCHORED")
        expect(slot.anchoredCarriers).toBe(1)
        expect(slot.admittedCarriers).toBe(1)
        expect(slot.observedCarriers).toBe(2)
    })

    test("only admitted carriers is FULLY_EXPOSED", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:SHARED_B", stars: 2 }] }), rec({ selfFactors: [{ factorKey: "white:SHARED_B", stars: 2 }] })]))
        const slot = slotByKeyFloor(doc, "white:SHARED_B", 1)!
        expect(slot.exposure).toBe("FULLY_EXPOSED")
        expect(slot.admittedCarriers).toBe(2)
        expect(slot.anchoredCarriers).toBe(0)
    })

    test("exactly one admitted carrier is FULLY_EXPOSED_SOLE", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:SOLE_C", stars: 2 }] })]))
        expect(slotByKeyFloor(doc, "white:SOLE_C", 1)!.exposure).toBe("FULLY_EXPOSED_SOLE")
    })

    test("a sole 1-star carrier admitted by Slice 1 still surfaces as sole observed risk", () => {
        const soleVet = rec({ selfFactors: [{ factorKey: "white:SOLE_1STAR", stars: 1 }] })
        const doc = buildCapacityCoverage(report([soleVet]))
        expect(buildCapacityTriage(report([soleVet])).records[0].admission).toBe("ELIGIBLE_FOR_MANUAL_REVIEW")
        const slot = slotByKeyFloor(doc, "white:SOLE_1STAR", 1)!
        expect(slot.exposure).toBe("FULLY_EXPOSED_SOLE")
        const exposure = doc.exposures.find((e) => e.scanIndex === soleVet.scanIndex)!
        expect(exposure.soleCarrierSlots).toContain("white:SOLE_1STAR@1")
        expect(exposure.lastCopyRisk).toBe("SOLE_OBSERVED_CARRIER")
    })

    test("two admitted carriers sharing a factor are shared fully exposed on both", () => {
        const a = rec({ scanIndex: 0, selfFactors: [{ factorKey: "white:SHARED_D", stars: 3 }] })
        const b = rec({ scanIndex: 1, selfFactors: [{ factorKey: "white:SHARED_D", stars: 3 }] })
        const doc = buildCapacityCoverage(report([a, b]))
        expect(slotByKeyFloor(doc, "white:SHARED_D", 1)!.exposure).toBe("FULLY_EXPOSED")
        for (const s of doc.exposures) {
            expect(s.fullyExposedSharedSlots).toContain("white:SHARED_D@1")
            expect(s.lastCopyRisk).toBe("SHARED_FULLY_EXPOSED")
        }
    })

    test("star floors stay separate: 3-star satisfies 1/2/3, 1-star satisfies only 1", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:HIGH", stars: 3 }, { factorKey: "white:LOW", stars: 1 }] })]))
        expect([1, 2, 3].map((f) => slotByKeyFloor(doc, "white:HIGH", f)?.starFloor)).toEqual([1, 2, 3])
        expect(slotByKeyFloor(doc, "white:LOW", 1)).toBeDefined()
        expect(slotByKeyFloor(doc, "white:LOW", 2)).toBeUndefined()
        expect(slotByKeyFloor(doc, "white:LOW", 3)).toBeUndefined()
    })

    test("an excluded Veteran with selfFactors === null anchors nothing", () => {
        const admitted = rec({ selfFactors: [{ factorKey: "white:SOLE_E", stars: 3 }] })
        const noEvidence = rec({ state: "REVIEW", selfFactors: null, gateReasons: ["INSPIRATION_CAPTURE_MISSING"] })
        const doc = buildCapacityCoverage(report([admitted, noEvidence]))
        const slot = slotByKeyFloor(doc, "white:SOLE_E", 1)!
        expect(slot.exposure).toBe("FULLY_EXPOSED_SOLE")
        expect(slot.anchoredCarriers).toBe(0)
        expect(doc.recordsWithoutTrustedFactors).toBe(1)
    })

    test("unique factor slots are characterBound; white slots are not", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "unique:SYNTH_GREEN", stars: 2 }, { factorKey: "white:SYNTH_WHITE", stars: 2 }] })]))
        expect(slotByKeyFloor(doc, "unique:SYNTH_GREEN", 1)!.characterBound).toBe(true)
        expect(slotByKeyFloor(doc, "white:SYNTH_WHITE", 1)!.characterBound).toBe(false)
    })
})

describe("degradation and claim strength", () => {
    test("accountWide:false makes every factor claim OBSERVED_LOWER_BOUND and adds COVERAGE_INCOMPLETE", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:F", stars: 2 }] })], { accountWide: false }))
        for (const s of doc.factorSlots) expect(s.claimStrength).toBe("OBSERVED_LOWER_BOUND")
        expect(doc.limits.map((l) => l.code)).toContain("COVERAGE_INCOMPLETE")
        expect(doc.accountWide).toBe(false)
    })

    test("accountWide:true permits ACCOUNT claims and omits COVERAGE_INCOMPLETE", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:G", stars: 2 }] })], { accountWide: true }))
        for (const s of doc.factorSlots) expect(s.claimStrength).toBe("ACCOUNT")
        expect(doc.limits.map((l) => l.code)).not.toContain("COVERAGE_INCOMPLETE")
    })

    test("an untrusted roster is usable:false with pool 0 and an empty, non-reassuring ledger", () => {
        const recs = [rec({ dataCompleteness: completeness({ rosterTrusted: false }) }), rec({ dataCompleteness: completeness({ rosterTrusted: false }) })]
        const doc = buildCapacityCoverage(report(recs))
        expect(doc.usable).toBe(false)
        expect(doc.poolSize).toBe(0)
        expect(doc.factorSlots).toHaveLength(0)
        expect(doc.characterSlots).toHaveLength(0)
        expect(doc.exposures).toHaveLength(0)
        // Not silent-safe: no exposure was measured, so nothing may read as "at risk == 0 == safe".
        for (const v of Object.values(doc.factorExposureCounts)) expect(v).toBe(0)
        expect(doc.limits.length).toBeGreaterThan(0)
    })

    test("unresolved factor reads surface the UNRESOLVED_FACTOR_READS limit", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:H", stars: 2 }] })], { unresolvedFactorReads: 4 }))
        expect(doc.limits.map((l) => l.code)).toContain("UNRESOLVED_FACTOR_READS")
        expect(doc.unresolvedFactorReads).toBe(4)
    })

    test("an unsupported retention schema version fails closed", () => {
        expect(() => buildCapacityCoverage(report([rec()], { schemaVersion: 1 as unknown as typeof PARENTLAB_RETENTION_SCHEMA_VERSION }))).toThrow(/schema version/)
    })

    test("WHITE_SUBFAMILY_NOT_AVAILABLE is always present and every slot's whiteSubfamily is null", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:I", stars: 2 }, { factorKey: "unique:J", stars: 1 }] })]))
        expect(doc.limits.map((l) => l.code)).toContain("WHITE_SUBFAMILY_NOT_AVAILABLE")
        for (const s of doc.factorSlots) expect(s.whiteSubfamily).toBeNull()
    })

    test("UNMEASURED when observed carriers cannot be attributed to admitted or anchored", () => {
        // Scarcity claims two carriers at floor 1, but only one Veteran actually carries trusted evidence.
        const entries: FactorScarcityEntry[] = [{ factorKey: "white:GHOST", kind: "white", canonicalName: "GHOST", observedCarriers: 2, carriersByMinStars: { "1": 2, "2": 0, "3": 0 }, maxObservedStars: 1 }]
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:GHOST", stars: 1 }] })], { entries }))
        expect(slotByKeyFloor(doc, "white:GHOST", 1)!.exposure).toBe("UNMEASURED")
    })
})

describe("white subfamily classification", () => {
    const whiteDomain = domain()

    test("a supplied domain classifies white slots into skill/race/scenario and drops the limit", () => {
        const recs = [
            rec({ scanIndex: 0, selfFactors: [{ factorKey: "white:SYNTH SKILL WHITE", stars: 3 }] }),
            rec({ scanIndex: 1, selfFactors: [{ factorKey: "white:SYNTH RACE WHITE", stars: 3 }] }),
            rec({ scanIndex: 2, selfFactors: [{ factorKey: "white:SYNTH SCENARIO WHITE", stars: 3 }] }),
        ]
        const doc = buildCapacityCoverage(report(recs), whiteDomain)
        expect(slotByKeyFloor(doc, "white:SYNTH SKILL WHITE", 1)!.whiteSubfamily).toBe("skill")
        expect(slotByKeyFloor(doc, "white:SYNTH RACE WHITE", 1)!.whiteSubfamily).toBe("race")
        expect(slotByKeyFloor(doc, "white:SYNTH SCENARIO WHITE", 1)!.whiteSubfamily).toBe("scenario")
        expect(doc.whiteSubfamilyCoverage.available).toBe(true)
        expect(doc.limits.map((l) => l.code)).not.toContain("WHITE_SUBFAMILY_NOT_AVAILABLE")
    })

    test("non-white slots keep whiteSubfamily null even with a domain", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "unique:SYNTH GREEN", stars: 2 }, { factorKey: "stat:SYNTH SPEED", stars: 2 }] })]), whiteDomain)
        expect(slotByKeyFloor(doc, "unique:SYNTH GREEN", 1)!.whiteSubfamily).toBeNull()
        expect(slotByKeyFloor(doc, "stat:SYNTH SPEED", 1)!.whiteSubfamily).toBeNull()
    })

    test("a white name in no family stays null and is counted and listed as unresolved", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:SYNTH UNMATCHED", stars: 2 }] })]), whiteDomain)
        expect(slotByKeyFloor(doc, "white:SYNTH UNMATCHED", 1)!.whiteSubfamily).toBeNull()
        expect(doc.whiteSubfamilyCoverage.unresolved).toBeGreaterThanOrEqual(1)
        expect(doc.whiteSubfamilyCoverage.unresolvedNames).toContain("SYNTH UNMATCHED")
    })

    test("Trackblazer matches skill and scenario, so it stays null and is counted and listed as ambiguous", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:TRACKBLAZER", stars: 2 }] })]), whiteDomain)
        expect(slotByKeyFloor(doc, "white:TRACKBLAZER", 1)!.whiteSubfamily).toBeNull()
        expect(doc.whiteSubfamilyCoverage.ambiguous).toBeGreaterThanOrEqual(1)
        expect(doc.whiteSubfamilyCoverage.ambiguousNames).toContain("TRACKBLAZER")
    })

    test("omitting the domain preserves v1 behavior: white subfamily unavailable and the limit present", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:SYNTH SKILL WHITE", stars: 2 }] })]))
        expect(doc.whiteSubfamilyCoverage.available).toBe(false)
        expect(slotByKeyFloor(doc, "white:SYNTH SKILL WHITE", 1)!.whiteSubfamily).toBeNull()
        expect(doc.limits.map((l) => l.code)).toContain("WHITE_SUBFAMILY_NOT_AVAILABLE")
        for (const counts of Object.values(doc.whiteSubfamilyCoverage.exposureByFamily)) {
            for (const v of Object.values(counts)) expect(v).toBe(0)
        }
    })

    test("the breakdown uses a fixed neutral (alphabetical) family order", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:SYNTH SKILL WHITE", stars: 2 }] })]), whiteDomain)
        expect(Object.keys(doc.whiteSubfamilyCoverage.exposureByFamily)).toEqual(["race", "scenario", "skill"])
        expect([...WHITE_SUBFAMILIES]).toEqual(["race", "scenario", "skill"])
    })

    test("supplying a domain leaves the existing factor and character exposure partitions unchanged", () => {
        const recs = [
            rec({ scanIndex: 0, character: "Char A", selfFactors: [{ factorKey: "white:SYNTH SKILL WHITE", stars: 3 }] }),
            rec({ scanIndex: 1, character: "Char B", selfFactors: [{ factorKey: "white:SYNTH RACE WHITE", stars: 2 }] }),
            anchorRec([{ factorKey: "white:SYNTH SCENARIO WHITE", stars: 3 }], { scanIndex: 2, character: "Char C" }),
        ]
        const without = buildCapacityCoverage(report(recs))
        const withDomain = buildCapacityCoverage(report(recs), whiteDomain)
        expect(withDomain.factorExposureCounts).toEqual(without.factorExposureCounts)
        expect(withDomain.characterExposureCounts).toEqual(without.characterExposureCounts)
        // The only per-slot difference is the added whiteSubfamily classification.
        const strip = (d: ReturnType<typeof buildCapacityCoverage>) => d.factorSlots.map((s) => ({ ...s, whiteSubfamily: null }))
        expect(strip(withDomain)).toEqual(strip(without))
        // Every matched white slot lands in exactly one family, so the family totals plus the null cases
        // account for every white slot.
        const familyTotal = Object.values(withDomain.whiteSubfamilyCoverage.exposureByFamily).reduce((sum, counts) => sum + counts.ANCHORED + counts.FULLY_EXPOSED + counts.FULLY_EXPOSED_SOLE + counts.UNMEASURED, 0)
        const whiteSlots = withDomain.factorSlots.filter((s) => s.kind === "white").length
        expect(familyTotal + withDomain.whiteSubfamilyCoverage.unresolved + withDomain.whiteSubfamilyCoverage.ambiguous).toBe(whiteSlots)
    })

    test("output with a domain is deterministic", () => {
        const build = () => buildCapacityCoverage(report([rec({ scanIndex: 0, selfFactors: [{ factorKey: "white:SYNTH SKILL WHITE", stars: 3 }] })]), whiteDomain)
        expect(JSON.stringify(build())).toBe(JSON.stringify(build()))
    })
})

describe("white factor domain provenance", () => {
    const withFactors = () => rec({ scanIndex: 0, selfFactors: [{ factorKey: "white:SYNTH SKILL WHITE", stars: 3 }] })
    const build = (d?: WhiteFactorDomain) => buildCapacityCoverage(report([withFactors()]), d)
    const untrustedBuild = (d?: WhiteFactorDomain) => buildCapacityCoverage(report([rec({ scanIndex: 0, dataCompleteness: completeness({ rosterTrusted: false }) })]), d)

    test("a supplied domain is projected with exactly the approved fields, in order", () => {
        const p = build(domain()).whiteFactorDomainProvenance!
        expect(Object.keys(p)).toEqual(["schemaVersion", "source", "counts", "contentHash128"])
        expect(p.schemaVersion).toBe(1)
        expect(p.source).toBe("synthetic test domain")
        expect(Object.keys(p.counts)).toEqual(["race", "scenario", "skill"])
        expect(p.counts).toEqual({ race: 1, scenario: 2, skill: 2 })
        expect(p.contentHash128).toMatch(/^[0-9a-f]{32}$/)
    })

    test("counts are canonical set sizes, not raw array lengths", () => {
        const p = build(domain({ skill: ["Synth Skill White", "SYNTH SKILL WHITE", "Trackblazer", "Trackblazer"] })).whiteFactorDomainProvenance!
        expect(p.counts.skill).toBe(2)
    })

    test("array order, letter case and duplicates move neither the hash nor the counts", () => {
        const declared = build(domain()).whiteFactorDomainProvenance!
        const equivalent = build(domain({ skill: ["Trackblazer", "synth skill white"], scenario: ["trackblazer", "SYNTH SCENARIO WHITE", "Trackblazer"] })).whiteFactorDomainProvenance!
        expect(equivalent.contentHash128).toBe(declared.contentHash128)
        expect(equivalent.counts).toEqual(declared.counts)
    })

    test("the asset's own identity does not move the classification hash", () => {
        const declared = build(domain()).whiteFactorDomainProvenance!
        const relabelled = build(domain({ schemaVersion: 9, source: "some other source" })).whiteFactorDomainProvenance!
        expect(relabelled.contentHash128).toBe(declared.contentHash128)
        expect(relabelled.schemaVersion).toBe(9)
        expect(relabelled.source).toBe("some other source")
    })

    test("renaming one white name moves the hash while every family count is preserved", () => {
        const declared = build(domain()).whiteFactorDomainProvenance!
        const renamed = build(domain({ skill: ["Synth Skill Renamed", "Trackblazer"] })).whiteFactorDomainProvenance!
        expect(renamed.counts).toEqual(declared.counts)
        expect(renamed.contentHash128).not.toBe(declared.contentHash128)
    })

    test("re-homing a name across families moves the hash while every family count is preserved", () => {
        // Trackblazer leaves skill for race and Synth Race White takes its place. Both families keep their
        // size, but the cross-family membership the classifier reports as ambiguous has changed.
        const declared = build(domain()).whiteFactorDomainProvenance!
        const rehomed = build(domain({ skill: ["Synth Skill White", "Synth Race White"], race: ["Trackblazer"] })).whiteFactorDomainProvenance!
        expect(rehomed.counts).toEqual(declared.counts)
        expect(rehomed.contentHash128).not.toBe(declared.contentHash128)
    })

    test("families the classifier does not read are outside the hash", () => {
        const declared = build(domain()).whiteFactorDomainProvenance!
        const withUnread = build(domain({ stat: ["Speed"], aptitude: ["Turf"], unique: ["Synth Unique"] })).whiteFactorDomainProvenance!
        expect(withUnread.contentHash128).toBe(declared.contentHash128)
    })

    test("no domain yields explicit null provenance and the unavailable limit instead", () => {
        const doc = build()
        expect(doc.whiteFactorDomainProvenance).toBeNull()
        expect(doc.whiteSubfamilyCoverage.available).toBe(false)
        const codes = doc.limits.map((l) => l.code)
        expect(codes).toContain("WHITE_SUBFAMILY_NOT_AVAILABLE")
        expect(codes).not.toContain("CAPTURE_FACTOR_DOMAIN_NOT_RECORDED")
        const roundTripped = JSON.parse(JSON.stringify(doc))
        expect(Object.keys(roundTripped)).toContain("whiteFactorDomainProvenance")
        expect(roundTripped.whiteFactorDomainProvenance).toBeNull()
    })

    test("provenance is present exactly when the white subfamily breakdown is available", () => {
        for (const doc of [build(domain()), build(), untrustedBuild(domain()), untrustedBuild()]) {
            expect(doc.whiteFactorDomainProvenance !== null).toBe(doc.whiteSubfamilyCoverage.available)
        }
    })

    test("the usable and fail-closed branches carry identical provenance", () => {
        const failClosed = untrustedBuild(domain())
        expect(failClosed.usable).toBe(false)
        expect(failClosed.poolSize).toBe(0)
        expect(failClosed.factorSlots).toHaveLength(0)
        expect(failClosed.exposures).toHaveLength(0)
        expect(failClosed.whiteFactorDomainProvenance).toEqual(build(domain()).whiteFactorDomainProvenance)
        expect(forbiddenKeyPaths(failClosed)).toEqual([])
        expect(forbiddenKeyPaths(build(domain()))).toEqual([])
    })

    test("a malformed schemaVersion throws before a document exists", () => {
        for (const bad of ["1", null, {}, [], true, 1.5, NaN, Infinity, -1, undefined, { safeToDelete: true }]) {
            expect(() => build(domain({ schemaVersion: bad }))).toThrow(/schemaVersion is not a non-negative integer/)
        }
    })

    test("a malformed source throws before a document exists", () => {
        for (const bad of [1, null, {}, [], true, undefined, { safeToDelete: true }]) {
            expect(() => build(domain({ source: bad }))).toThrow(/source is not a string/)
        }
    })

    test("a malformed domain identity throws on the untrusted-roster branch too", () => {
        expect(() => untrustedBuild(domain({ schemaVersion: "1" }))).toThrow(/schemaVersion is not a non-negative integer/)
        expect(() => untrustedBuild(domain({ source: { safeToDelete: true } }))).toThrow(/source is not a string/)
    })

    test("an unsupported retention schema version is reported ahead of a malformed domain identity", () => {
        const rep = report([withFactors()], { schemaVersion: 1 as unknown as typeof PARENTLAB_RETENTION_SCHEMA_VERSION })
        expect(() => buildCapacityCoverage(rep, domain({ schemaVersion: "1" }))).toThrow(/retention schema version/)
    })

    test("a supplied domain emits the capture-domain limit exactly once, in place of the unavailable one", () => {
        const codes = build(domain()).limits.map((l) => l.code)
        expect(codes.filter((c) => c === "CAPTURE_FACTOR_DOMAIN_NOT_RECORDED")).toHaveLength(1)
        expect(codes).not.toContain("WHITE_SUBFAMILY_NOT_AVAILABLE")
    })

    test("exactly one of the capture-domain and unavailable limits is present on every branch", () => {
        for (const doc of [build(domain()), build(), untrustedBuild(domain()), untrustedBuild()]) {
            const codes = doc.limits.map((l) => l.code)
            expect(codes.filter((c) => c === "CAPTURE_FACTOR_DOMAIN_NOT_RECORDED" || c === "WHITE_SUBFAMILY_NOT_AVAILABLE")).toHaveLength(1)
        }
    })

    test("the capture-domain limit sits immediately before AFFINITY_NOT_DECODED and is not a base limit", () => {
        for (const codes of [build(domain()).limits.map((l) => l.code), untrustedBuild(domain()).limits.map((l) => l.code)]) {
            expect(codes.indexOf("CAPTURE_FACTOR_DOMAIN_NOT_RECORDED")).toBe(codes.indexOf("AFFINITY_NOT_DECODED") - 1)
        }
        expect(PERMANENT_COVERAGE_LIMITS).not.toContain("CAPTURE_FACTOR_DOMAIN_NOT_RECORDED")
    })

    test("the capture-domain limit states the classification pin without claiming the capture domain agrees", () => {
        const reason = build(domain()).limits.find((l) => l.code === "CAPTURE_FACTOR_DOMAIN_NOT_RECORDED")!.reason
        expect(reason).toMatch(/is not recorded/)
        expect(reason).toMatch(/not shown to agree/)
        expect(reason).not.toMatch(/verified|compatible|matches/i)
    })

    test("the coverage schema is v4 while the capacity and retention schemas are unchanged", () => {
        expect(PARENTLAB_CAPACITY_COVERAGE_SCHEMA_VERSION).toBe(4)
        expect(build(domain()).schemaVersion).toBe(4)
        expect(build(domain()).capacitySchemaVersion).toBe(PARENTLAB_CAPACITY_SCHEMA_VERSION)
        expect(PARENTLAB_CAPACITY_SCHEMA_VERSION).toBe(1)
        expect(PARENTLAB_RETENTION_SCHEMA_VERSION).toBe(3)
    })

    test("output is byte-identical across rebuilds and across semantically equal domains", () => {
        expect(JSON.stringify(build(domain()))).toBe(JSON.stringify(build(domain())))
        const equivalent = domain({ skill: ["Trackblazer", "synth skill white"], scenario: ["Trackblazer", "trackblazer", "SYNTH SCENARIO WHITE"] })
        expect(JSON.stringify(build(equivalent))).toBe(JSON.stringify(build(domain())))
    })
})

describe("character, target and per-Veteran exposure", () => {
    test("character slot exposure follows admitted vs excluded roster membership", () => {
        const shared1 = rec({ scanIndex: 0, character: "Char Shared", outfit: "Outfit A" })
        const shared2 = rec({ scanIndex: 1, character: "Char Shared", outfit: "Outfit B" })
        const anchoredChar = rec({ scanIndex: 2, character: "Char Anchored", state: "HARD_PROTECT", hardProtectReasons: ["MANUAL_PROTECT"] })
        const soleChar = rec({ scanIndex: 3, character: "Char Sole" })
        const doc = buildCapacityCoverage(report([shared1, shared2, anchoredChar, soleChar]))
        const byKey = (k: string) => doc.characterSlots.find((s) => s.characterKey === k)!
        expect(byKey("Char Shared").exposure).toBe("FULLY_EXPOSED")
        expect(byKey("Char Shared").outfits).toEqual(["Outfit A", "Outfit B"])
        expect(byKey("Char Anchored").exposure).toBe("ANCHORED")
        expect(byKey("Char Sole").exposure).toBe("FULLY_EXPOSED_SOLE")
        expect(doc.exposures.find((e) => e.scanIndex === 3)!.soleCharacterSlot).toBe(true)
    })

    test("the target slot is profile-scoped and anchored when an excluded Veteran clears the gate", () => {
        const admittedClear = rec({ scanIndex: 0 })
        const excludedClear = rec({ scanIndex: 1, state: "HARD_PROTECT", hardProtectReasons: ["MANUAL_PROTECT"] })
        const doc = buildCapacityCoverage(report([admittedClear, excludedClear]))
        expect(doc.targetSlots).toHaveLength(1)
        const t = doc.targetSlots[0]
        expect(t.targetProfile).toBe("GENERAL_INHERITANCE")
        expect(t.exposure).toBe("ANCHORED")
        expect(t.clearingCarriers).toBe(2)
        expect(t.admittedCarriers).toBe(1)
        expect(doc.limits.map((l) => l.code)).toContain("SINGLE_TARGET_PROFILE_SCOPE")
    })

    test("a gated target slot counts only Veterans that list the profile in targetsCovered", () => {
        const clears = rec({ scanIndex: 0, coverageSummary: { character: "Synth Alpha", characterCarriers: 1, characterOutfitCarriers: 1, targetsCovered: ["MILE_PARENT"], soleTargetCoverage: [] } })
        const doesNot = rec({ scanIndex: 1, coverageSummary: { character: "Synth Beta", characterCarriers: 1, characterOutfitCarriers: 1, targetsCovered: [], soleTargetCoverage: [] } })
        const doc = buildCapacityCoverage(report([clears, doesNot], { targetProfile: "MILE_PARENT" }))
        const t = doc.targetSlots[0]
        expect(t.targetProfile).toBe("MILE_PARENT")
        expect(t.clearingCarriers).toBe(1)
        expect(t.admittedCarriers).toBe(1)
        expect(t.exposure).toBe("FULLY_EXPOSED_SOLE")
    })

    test("per-Veteran lastCopyRisk precedence: sole beats shared beats none", () => {
        // The shared and none Veterans share a character so their character slot is not sole-exposed,
        // isolating the factor-slot signal that each case is meant to exercise.
        const sole = rec({ scanIndex: 0, character: "P Sole", selfFactors: [{ factorKey: "white:P_SOLE", stars: 2 }] })
        const shareA = rec({ scanIndex: 1, character: "P Shared", selfFactors: [{ factorKey: "white:P_SHARED", stars: 2 }] })
        const shareB = rec({ scanIndex: 2, character: "P Shared", selfFactors: [{ factorKey: "white:P_SHARED", stars: 2 }] })
        const none = rec({ scanIndex: 3, character: "P None", selfFactors: [{ factorKey: "white:P_ANCHORED", stars: 2 }] })
        const noneSib = rec({ scanIndex: 6, character: "P None", selfFactors: [{ factorKey: "white:P_ANCHORED", stars: 2 }] })
        const anchorNone = anchorRec([{ factorKey: "white:P_ANCHORED", stars: 2 }], { scanIndex: 4, character: "P AnchorSrc" })
        const doc = buildCapacityCoverage(report([sole, shareA, shareB, none, noneSib, anchorNone]))
        const byScan = (i: number) => doc.exposures.find((e) => e.scanIndex === i)!
        expect(byScan(0).lastCopyRisk).toBe("SOLE_OBSERVED_CARRIER")
        expect(byScan(1).lastCopyRisk).toBe("SHARED_FULLY_EXPOSED")
        expect(byScan(2).lastCopyRisk).toBe("SHARED_FULLY_EXPOSED")
        expect(byScan(3).lastCopyRisk).toBe("NO_EXPOSED_SLOT_OBSERVED")
    })

    test("null / whitespace character records are counted as unkeyed, not as a character slot", () => {
        const doc = buildCapacityCoverage(report([rec({ scanIndex: 0, character: null }), rec({ scanIndex: 1, character: "   " }), rec({ scanIndex: 2, character: "Real Char" })]))
        expect(doc.unkeyedRecords).toBe(2)
        expect(doc.characterSlots.map((s) => s.characterKey)).toEqual(["Real Char"])
    })

    test("exposureByKind breaks a Veteran's exposed slots down by factor kind", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:K1", stars: 1 }, { factorKey: "unique:K2", stars: 1 }] })]))
        const v = doc.exposures[0]
        expect(v.exposureByKind.white).toBeGreaterThanOrEqual(1)
        expect(v.exposureByKind.unique).toBeGreaterThanOrEqual(1)
    })
})

describe("replacement evidence provenance", () => {
    const withFactors = () => rec({ scanIndex: 0, selfFactors: [{ factorKey: "white:PROV", stars: 3 }] })

    test("absent evidence carries the key through as null and emits no corpus-dependence limit", () => {
        const doc = buildCapacityCoverage(report([withFactors()]))
        expect(doc.replacementEvidence).toBeNull()
        expect(Object.keys(doc)).toContain("replacementEvidence")
        const roundTripped = JSON.parse(JSON.stringify(doc))
        expect(Object.keys(roundTripped)).toContain("replacementEvidence")
        expect(roundTripped.replacementEvidence).toBeNull()
        expect(doc.limits.map((l) => l.code)).not.toContain("REPLACEMENT_EVIDENCE_CORPUS_DEPENDENT")
    })

    test("a persisted v2-style report with no replacementEvidence key still emits the key as null", () => {
        const v2 = { ...report([withFactors()]) } as Record<string, unknown>
        delete v2.replacementEvidence
        expect("replacementEvidence" in v2).toBe(false)
        const doc = buildCapacityCoverage(v2 as unknown as RetentionShadowReport)
        expect(Object.keys(doc)).toContain("replacementEvidence")
        expect(doc.replacementEvidence).toBeNull()
        expect(doc.limits.map((l) => l.code)).not.toContain("REPLACEMENT_EVIDENCE_CORPUS_DEPENDENT")
    })

    test("present evidence is projected into a new object and adds the corpus-dependence limit exactly once", () => {
        const evidence = provenance()
        const doc = buildCapacityCoverage(report([withFactors()], { replacementEvidence: evidence }))
        expect(doc.replacementEvidence).toEqual(evidence)
        expect(doc.replacementEvidence).not.toBe(evidence)
        expect(Object.keys(doc.replacementEvidence as object).sort()).toEqual(["appVersions", "confirmedVeterans", "identityCollisions", "newestObservationTs", "traineeCount"])
        expect(doc.limits.filter((l) => l.code === "REPLACEMENT_EVIDENCE_CORPUS_DEPENDENT")).toHaveLength(1)
    })

    test("the corpus-dependence limit is seated immediately after REBUILDABILITY_NOT_MEASURED", () => {
        const codes = buildCapacityCoverage(report([withFactors()], { replacementEvidence: provenance() })).limits.map((l) => l.code)
        expect(codes.indexOf("REPLACEMENT_EVIDENCE_CORPUS_DEPENDENT")).toBe(codes.indexOf("REBUILDABILITY_NOT_MEASURED") + 1)
    })

    test("the coverage schema is v4 and the Slice 1 capacity schema version is unchanged", () => {
        const doc = buildCapacityCoverage(report([withFactors()], { replacementEvidence: provenance() }))
        expect(PARENTLAB_CAPACITY_COVERAGE_SCHEMA_VERSION).toBe(4)
        expect(doc.schemaVersion).toBe(4)
        expect(doc.capacitySchemaVersion).toBe(1)
        expect(doc.capacitySchemaVersion).toBe(PARENTLAB_CAPACITY_SCHEMA_VERSION)
    })

    test("an untrusted roster still carries the provenance and the limit while staying fail-closed", () => {
        const untrusted = rec({ scanIndex: 0, dataCompleteness: completeness({ rosterTrusted: false }) })
        const evidence = provenance()
        const doc = buildCapacityCoverage(report([untrusted], { replacementEvidence: evidence }))
        expect(doc.usable).toBe(false)
        expect(doc.poolSize).toBe(0)
        expect(doc.factorSlots).toHaveLength(0)
        expect(doc.exposures).toHaveLength(0)
        expect(doc.replacementEvidence).toEqual(evidence)
        expect(doc.limits.filter((l) => l.code === "REPLACEMENT_EVIDENCE_CORPUS_DEPENDENT")).toHaveLength(1)
    })

    test("extra top-level keys on a hand-edited provenance block are stripped on the usable branch", () => {
        const doc = buildCapacityCoverage(report([withFactors()], { replacementEvidence: withExtras() }))
        expect(doc.usable).toBe(true)
        expect(doc.replacementEvidence).toEqual(provenance())
        expect(Object.keys(doc.replacementEvidence as object).sort()).toEqual(["appVersions", "confirmedVeterans", "identityCollisions", "newestObservationTs", "traineeCount"])
        expect(doc.replacementEvidence).not.toHaveProperty("safeToDelete")
        expect(doc.replacementEvidence).not.toHaveProperty("fragility")
        expect(doc.replacementEvidence).not.toHaveProperty("recommendation")
        expect(doc.replacementEvidence).not.toHaveProperty("nested")
        expect(forbiddenKeyPaths(doc)).toEqual([])
        expect(doc.limits.filter((l) => l.code === "REPLACEMENT_EVIDENCE_CORPUS_DEPENDENT")).toHaveLength(1)
    })

    test("extra top-level keys are stripped on the fail-closed branch too", () => {
        const untrusted = rec({ scanIndex: 0, dataCompleteness: completeness({ rosterTrusted: false }) })
        const doc = buildCapacityCoverage(report([untrusted], { replacementEvidence: withExtras() }))
        expect(doc.usable).toBe(false)
        expect(doc.poolSize).toBe(0)
        expect(doc.factorSlots).toHaveLength(0)
        expect(doc.exposures).toHaveLength(0)
        expect(doc.replacementEvidence).toEqual(provenance())
        expect(Object.keys(doc.replacementEvidence as object).sort()).toEqual(["appVersions", "confirmedVeterans", "identityCollisions", "newestObservationTs", "traineeCount"])
        expect(forbiddenKeyPaths(doc)).toEqual([])
        expect(doc.limits.filter((l) => l.code === "REPLACEMENT_EVIDENCE_CORPUS_DEPENDENT")).toHaveLength(1)
    })

    test("output with present provenance is deterministic", () => {
        const build = () => buildCapacityCoverage(report([withFactors()], { replacementEvidence: provenance() }))
        expect(JSON.stringify(build())).toBe(JSON.stringify(build()))
    })

    test("provenance key order in the output does not depend on the input key order", () => {
        const declared = provenance()
        const reversed: ReplacementEvidenceProvenance = {
            newestObservationTs: declared.newestObservationTs,
            appVersions: declared.appVersions,
            identityCollisions: declared.identityCollisions,
            traineeCount: declared.traineeCount,
            confirmedVeterans: declared.confirmedVeterans,
        }
        const build = (evidence: ReplacementEvidenceProvenance) => buildCapacityCoverage(report([withFactors()], { replacementEvidence: evidence }))
        expect(JSON.stringify(build(declared))).toBe(JSON.stringify(build(reversed)))
        expect(Object.keys(build(reversed).replacementEvidence as object)).toEqual(["confirmedVeterans", "traineeCount", "identityCollisions", "appVersions", "newestObservationTs"])
    })

    /** Builds with an arbitrary value under replacementEvidence, as JSON.parse of a hand-edited document can yield. */
    const buildWith = (evidence: unknown) => () => buildCapacityCoverage(report([withFactors()], { replacementEvidence: evidence as ReplacementEvidenceProvenance }))
    const COUNT_FIELDS = ["confirmedVeterans", "traineeCount", "identityCollisions"] as const
    const MALFORMED_COUNTS = ["212", null, {}, [], true, -1, 1.5, NaN, Infinity, undefined]

    test("an explicit undefined provenance value carries the key through as null", () => {
        const doc = buildWith(undefined)()
        expect(doc.replacementEvidence).toBeNull()
        expect(doc.limits.map((l) => l.code)).not.toContain("REPLACEMENT_EVIDENCE_CORPUS_DEPENDENT")
    })

    test("zeroed counts, an empty version list and a null timestamp are valid and build", () => {
        const zeroed = provenance({ confirmedVeterans: 0, traineeCount: 0, identityCollisions: 0, appVersions: [], newestObservationTs: null })
        expect(buildWith(zeroed)().replacementEvidence).toEqual(zeroed)
    })

    test("a negative or fractional timestamp builds, since the producer's contract is finite, not integer", () => {
        for (const ts of [0, -1, 1.5, 1788020859193.5]) {
            expect(buildWith(provenance({ newestObservationTs: ts }))().replacementEvidence?.newestObservationTs).toBe(ts)
        }
    })

    test("a non-null provenance value that is not an object throws instead of building a document", () => {
        for (const bad of [0, false, "", [], "text", 123]) {
            expect(buildWith(bad)).toThrow(/replacement evidence is not an object/)
        }
    })

    test("a malformed count field throws", () => {
        for (const field of COUNT_FIELDS) {
            for (const bad of MALFORMED_COUNTS) {
                expect(buildWith({ ...provenance(), [field]: bad })).toThrow(new RegExp(`replacement evidence ${field} is not a non-negative integer`))
            }
        }
    })

    test("four valid fields do not rescue a fifth malformed one", () => {
        expect(buildWith({ ...provenance(), identityCollisions: -1 })).toThrow(/identityCollisions is not a non-negative integer/)
        expect(buildWith({ ...provenance(), appVersions: [1] })).toThrow(/appVersions is not an array of strings/)
    })

    test("a malformed appVersions field throws", () => {
        for (const bad of ["1.4.0", {}, null, [1], [null], ["1.4.0", 2], undefined]) {
            expect(buildWith({ ...provenance(), appVersions: bad })).toThrow(/appVersions is not an array of strings/)
        }
    })

    test("appVersions is type-checked only: emptiness, uniqueness, sortedness and syntax are not enforced", () => {
        for (const versions of [[], [""], ["1.4.0", "1.4.0"], ["9.9.9", "1.3.8", "not-a-version"]]) {
            expect(buildWith(provenance({ appVersions: versions }))().replacementEvidence?.appVersions).toEqual(versions)
        }
    })

    test("the emitted appVersions array does not share identity with the input array", () => {
        const versions = ["1.4.0", "1.3.8"]
        const emitted = buildWith(provenance({ appVersions: versions }))().replacementEvidence?.appVersions
        expect(emitted).toEqual(versions)
        expect(emitted).not.toBe(versions)
    })

    test("a malformed newestObservationTs throws", () => {
        for (const bad of ["1788020859193", {}, [], true, NaN, Infinity, undefined]) {
            expect(buildWith({ ...provenance(), newestObservationTs: bad })).toThrow(/newestObservationTs is not a finite number or null/)
        }
    })

    test("an injected decision object under an approved value key throws before a document exists", () => {
        expect(buildWith({ ...provenance(), appVersions: { safeToDelete: true } })).toThrow(/appVersions is not an array of strings/)
        expect(buildWith({ ...provenance(), newestObservationTs: { fragility: 1 } })).toThrow(/newestObservationTs is not a finite number or null/)
    })

    test("malformed provenance throws on the untrusted-roster branch too, never a fail-closed document", () => {
        const untrusted = rec({ scanIndex: 0, dataCompleteness: completeness({ rosterTrusted: false }) })
        const build = (evidence: unknown) => () => buildCapacityCoverage(report([untrusted], { replacementEvidence: evidence as ReplacementEvidenceProvenance }))
        expect(build(0)).toThrow(/replacement evidence is not an object/)
        expect(build({ ...provenance(), appVersions: { safeToDelete: true } })).toThrow(/appVersions is not an array of strings/)
    })

    test("an unsupported retention schema version is reported ahead of malformed provenance", () => {
        const rep = report([withFactors()], { schemaVersion: 1 as unknown as typeof PARENTLAB_RETENTION_SCHEMA_VERSION, replacementEvidence: 0 as unknown as ReplacementEvidenceProvenance })
        expect(() => buildCapacityCoverage(rep)).toThrow(/retention schema version/)
    })
})

describe("contract, isolation and determinism", () => {
    test("no forbidden decision/ranking/executor field appears anywhere in an emitted document", () => {
        const doc = buildCapacityCoverage(report([rec({ selfFactors: [{ factorKey: "white:Z", stars: 3 }] }), anchorRec([{ factorKey: "white:Z", stars: 3 }], { scanIndex: 9 })]))
        expect(forbiddenKeyPaths(doc)).toEqual([])
    })

    test("the recursive guard finds a forbidden key nested below objects and arrays", () => {
        expect(forbiddenKeyPaths({ a: { b: [{ c: { safeToTransfer: true } }] } })).toEqual(["$.a.b[0].c.safeToTransfer"])
        expect(forbiddenKeyPaths({ slots: [{ fragility: 1 }, { safeToDelete: false }] })).toEqual(["$.slots[0].fragility", "$.slots[1].safeToDelete"])
    })

    test("a legitimate nested provenance and limits structure trips no forbidden key", () => {
        expect(forbiddenKeyPaths({ replacementEvidence: provenance(), limits: [{ code: "REBUILDABILITY_NOT_MEASURED", reason: "not measured" }], counts: { ANCHORED: 1 }, names: ["a", null] })).toEqual([])
    })

    test("documents built with present provenance carry no destructive semantics on either branch", () => {
        const evidence = provenance()
        const usable = buildCapacityCoverage(report([rec({ scanIndex: 0, selfFactors: [{ factorKey: "white:GUARD", stars: 3 }] })], { replacementEvidence: evidence }))
        const failClosed = buildCapacityCoverage(report([rec({ scanIndex: 0, dataCompleteness: completeness({ rosterTrusted: false }) })], { replacementEvidence: evidence }))
        expect(usable.replacementEvidence).not.toBeNull()
        expect(failClosed.replacementEvidence).not.toBeNull()
        expect(forbiddenKeyPaths(usable)).toEqual([])
        expect(forbiddenKeyPaths(failClosed)).toEqual([])
    })

    test("no coverage enum overlaps a retention state or SAFE_TO_TRANSFER", () => {
        for (const e of COVERAGE_EXPOSURES) {
            expect(RETENTION_STATES as readonly string[]).not.toContain(e)
            expect(e).not.toBe("SAFE_TO_TRANSFER")
        }
        for (const r of LAST_COPY_RISKS) {
            expect(RETENTION_STATES as readonly string[]).not.toContain(r)
            expect(r).not.toBe("SAFE_TO_TRANSFER")
        }
    })

    test("the retention reader rejects a coverage document", () => {
        const doc = buildCapacityCoverage(report([rec()]))
        expect(() => retentionReportsOf(doc)).toThrow(/schema/)
    })

    test("the Slice 1 capacity reader rejects a coverage document", () => {
        const doc = buildCapacityCoverage(report([rec()]))
        expect(isCapacityTriageDocument(doc)).toBe(false)
    })

    test("the coverage reader accepts only its own document and rejects retention and Slice 1 docs", () => {
        const retention = report([rec()])
        const slice1 = buildCapacityTriage(retention)
        const coverage = buildCapacityCoverage(retention)
        expect(isCapacityCoverageDocument(coverage)).toBe(true)
        expect(isCapacityCoverageDocument(retention)).toBe(false)
        expect(isCapacityCoverageDocument(slice1)).toBe(false)
        expect(coverage.schema).toBe(PARENTLAB_CAPACITY_COVERAGE_SCHEMA)
        expect(coverage.kind).toBe(PARENTLAB_CAPACITY_COVERAGE_KIND)
    })

    test("star floors are pinned to [1,2,3]", () => {
        expect(COVERAGE_STAR_FLOORS).toEqual([1, 2, 3])
    })

    test("output is deterministic and generatedAt copies the input observation, not a clock", () => {
        const build = () => buildCapacityCoverage(report([rec({ scanIndex: 0, selfFactors: [{ factorKey: "white:D1", stars: 3 }] }), rec({ scanIndex: 1, selfFactors: [{ factorKey: "white:D2", stars: 2 }] })]))
        expect(JSON.stringify(build())).toBe(JSON.stringify(build()))
        expect(build().generatedAt).toBe(Date.UTC(2026, 7, 29, 12, 0, 0))
    })

    test("in-process Slice 1 admission is preserved verbatim", () => {
        const recs = [rec({ scanIndex: 0, state: "KEEP" }), rec({ scanIndex: 1, state: "HARD_PROTECT", hardProtectReasons: ["MANUAL_PROTECT"] }), rec({ scanIndex: 2, state: "SAFE_TO_TRANSFER", confidence: "HIGH" }), rec({ scanIndex: 3, state: "UNKNOWN" })]
        const rep = report(recs)
        const triage = buildCapacityTriage(rep)
        const doc = buildCapacityCoverage(rep)
        const admissionByScan = new Map(triage.records.map((r) => [r.scanIndex, r.admission]))
        expect(doc.poolSize).toBe(triage.admittedCount)
        expect(doc.exposures.every((e) => e.admission === "ELIGIBLE_FOR_MANUAL_REVIEW")).toBe(true)
        for (const e of doc.exposures) expect(admissionByScan.get(e.scanIndex)).toBe("ELIGIBLE_FOR_MANUAL_REVIEW")
        expect(doc.exposures).toHaveLength(triage.admittedCount)
    })
})

describe("CLI freshness binding and exit codes", () => {
    const cliPath = join(process.cwd(), "scripts", "parent-lab-capacity-coverage.mjs")
    const dir = mkdtempSync(join(tmpdir(), "cap-cov-"))

    function writeRetention(recs: VeteranRetentionRecommendation[], o: Parameters<typeof report>[1] = {}): string {
        const path = join(dir, `retention-${Math.abs(recs.length + (recs[0]?.scanIndex ?? 0))}-${o.accountWide === false ? "partial" : "full"}.json`)
        writeFileSync(path, JSON.stringify(report(recs, o)), "utf8")
        return path
    }

    function run(args: string[], env: Record<string, string> = {}): { status: number; stdout: string } {
        try {
            const stdout = execFileSync(process.execPath, [cliPath, ...args], { encoding: "utf8", env: { ...process.env, ...env } })
            return { status: 0, stdout }
        } catch (e) {
            const err = e as { status?: number; stdout?: string }
            return { status: err.status ?? -1, stdout: err.stdout ?? "" }
        }
    }

    test("a usable document exits 0", () => {
        const path = writeRetention([rec({ selfFactors: [{ factorKey: "white:CLI_A", stars: 2 }] })])
        expect(run(["--retention", path]).status).toBe(0)
    })

    test("an untrusted roster exits 1", () => {
        const path = writeRetention([rec({ dataCompleteness: completeness({ rosterTrusted: false }) })])
        expect(run(["--retention", path]).status).toBe(1)
    })

    test("a matching --expect-roster-scan is accepted", () => {
        const path = writeRetention([rec({ selfFactors: [{ factorKey: "white:CLI_B", stars: 2 }] })])
        expect(run(["--retention", path, "--expect-roster-scan", "rs-cov-0001"]).status).toBe(0)
    })

    test("a mismatching --expect-roster-scan fails closed with exit 2", () => {
        const path = writeRetention([rec({ selfFactors: [{ factorKey: "white:CLI_C", stars: 2 }] })])
        expect(run(["--retention", path, "--expect-roster-scan", "rs-DIFFERENT"]).status).toBe(2)
    })

    test("a malformed input fails with exit 2", () => {
        const path = join(dir, "malformed.json")
        writeFileSync(path, "{ not valid json", "utf8")
        expect(run(["--retention", path]).status).toBe(2)
    })

    test("a missing white factor domain asset fails closed with exit 2", () => {
        const path = writeRetention([rec({ selfFactors: [{ factorKey: "white:CLI_D", stars: 2 }] })])
        expect(run(["--retention", path], { PARENT_LAB_WHITE_FACTOR_DOMAIN: join(dir, "no-such-domain.json") }).status).toBe(2)
    })

    test("a structurally invalid white factor domain asset fails closed with exit 2", () => {
        const bad = join(dir, "wrong-shape-domain.json")
        writeFileSync(bad, JSON.stringify({ families: { skill: "not-an-array" } }), "utf8")
        const path = writeRetention([rec({ selfFactors: [{ factorKey: "white:CLI_E", stars: 2 }] })])
        expect(run(["--retention", path], { PARENT_LAB_WHITE_FACTOR_DOMAIN: bad }).status).toBe(2)
    })

    test("a syntax-invalid white factor domain asset fails closed with exit 2", () => {
        const broken = join(dir, "broken-domain.json")
        writeFileSync(broken, "{ not valid json", "utf8")
        const path = writeRetention([rec({ selfFactors: [{ factorKey: "white:CLI_F", stars: 2 }] })])
        expect(run(["--retention", path], { PARENT_LAB_WHITE_FACTOR_DOMAIN: broken }).status).toBe(2)
    })

    test("a white factor domain asset with a malformed schemaVersion fails closed with exit 2", () => {
        const bad = join(dir, "bad-identity-domain.json")
        writeFileSync(bad, JSON.stringify({ schemaVersion: "1", source: "synthetic", families: { skill: [], race: [], scenario: [] } }), "utf8")
        const path = writeRetention([rec({ selfFactors: [{ factorKey: "white:CLI_G", stars: 2 }] })])
        expect(run(["--retention", path], { PARENT_LAB_WHITE_FACTOR_DOMAIN: bad }).status).toBe(2)
    })
})
