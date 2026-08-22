import { parseCorpus } from "../../outcomeAnalysis.ts"
import { buildVeteranLibrary } from "../buildVeteranLibrary.ts"
import { buildInspirationIndex, parseInspirationRecords } from "../inspiration.ts"
import { reconcileRoster } from "../reconcile.ts"
import { buildRosterSnapshots, parseRosterScanRecords } from "../roster.ts"
import { buildRetentionShadowReport, evaluateDominance, INACTIVE_RULES } from "../retentionAdvisor.ts"
import { buildFactorScarcityIndex, buildRetentionEvidence, observedUniqueFactorKeys, replacementSummary } from "../retentionEvidence.ts"
import { targetDimensions, TARGET_DIMENSION_NAMES, TARGET_PROFILES } from "../retentionTargets.ts"
import type { RetentionShadowReport, VeteranRetentionRecommendation } from "../retentionTypes.ts"

// Every fixture goes through the real parse paths - roster rows and inspiration captures as JSONL,
// careers through parseCorpus into buildVeteranLibrary - so a change to any ingest path shows up here
// rather than being papered over by hand-built objects.

const SCAN = "rs-test-0001"
const ISCAN = "insp-test-0001"
const T = Date.UTC(2026, 7, 21, 12, 0, 0)

const APTITUDES = { turf: "A", dirt: "G", sprint: "C", mile: "A", medium: "A", long: "B", front: "A", pace: "A", late: "B", end: "C" }

function rosterEntry(o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "roster_entry",
        schemaVersion: 1,
        scanId: SCAN,
        scanIndex: 0,
        observedAt: T,
        character: "Taiki Shuttle",
        outfit: "Wild Frontier",
        rank: "S",
        rating: 15000,
        stats: { spd: 900, sta: 700, pwr: 650, grt: 600, wit: 500 },
        statGrades: { spd: "A", sta: "B", pwr: "B", grt: "C", wit: "C" },
        aptitudes: APTITUDES,
        favoriteState: "unknown",
        protectionState: "unknown",
        careerInfo: null,
        rosterFingerprint: "fp-0",
        readCompleteness: 1,
        identityMultiplicity: 1,
        unresolvedFields: [],
        diagnostics: null,
        ...o,
    })
}

function rosterHeader(count: number, o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "roster_scan",
        schemaVersion: 1,
        scanId: SCAN,
        startedAt: T,
        completedAt: T,
        displayedRegisteredUsed: count,
        displayedRegisteredCapacity: 260,
        filtersOff: true,
        sortKey: "rating",
        sortDirection: "descending",
        entryLimit: 0,
        entriesEnumerated: count,
        uniqueFingerprints: count,
        unidentifiedCount: 0,
        duplicateFingerprintCount: 0,
        countDiscrepancy: 0,
        terminationReason: "count_reached",
        enumerationComplete: true,
        identityComplete: true,
        completeness: "trusted_complete",
        evidenceCropCount: 0,
        app: "test",
        screenWidth: 1600,
        screenHeight: 900,
        ...o,
    })
}

function factor(kind: string, name: string, stars: number) {
    return {
        rowIndex: 0,
        column: "left",
        kind,
        displayName: name,
        normalizedName: name.toUpperCase(),
        stars,
        canonicalName: name,
        canonicalPath: "strong",
        factorFingerprint: `${kind}:${name.toUpperCase()}:${stars}`,
        structuralFingerprint: `${kind}:${stars}`,
        ambiguous: false,
    }
}

function capture(fingerprint: string, factors: ReturnType<typeof factor>[], o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "veteran_inspiration",
        schemaVersion: 2,
        scanId: ISCAN,
        scanIndex: 0,
        observedAt: T,
        rosterFingerprint: fingerprint,
        character: "Taiki Shuttle",
        outfit: "Wild Frontier",
        rank: "S",
        selfPortraitObserved: true,
        selfFactorCount: factors.length,
        selfFactorFingerprint: `set:${fingerprint}`,
        selfStructuralFingerprint: `struct:${fingerprint}`,
        selfFactorSetTrusted: true,
        selfFactors: factors,
        legacyAncestors: [],
        termination: "reached_bottom",
        sparkCaptureComplete: true,
        screenReadCompleteness: 1,
        unresolvedFields: [],
        diagnostics: null,
        ...o,
    })
}

/** A completed career the Veteran library will confirm, with a kept spark set. */
function career(trainee: string, stats: { spd: number; sta: number; pwr: number; grt: number; wit: number }, fans: number): string[] {
    return [
        JSON.stringify({ result: "BREAKPOINT_REACHED", outcome: "COMPLETED", trainee, scenario: "URA_Finale", turn: 75, ts: T - fans, fans, ...stats, skillPts: 30 }),
        JSON.stringify({ type: "sparks", phase: "kept", ts: T - fans, rows: [{ name: "Speed", stars: 1, kind: "stat" }] }),
    ]
}

interface BuildOptions {
    readonly entries: readonly string[]
    readonly captures?: readonly string[]
    readonly careers?: readonly string[][]
    readonly profile?: keyof typeof TARGET_PROFILES
    readonly manualProtect?: readonly string[]
    readonly withReconciliation?: boolean
}

function build(options: BuildOptions) {
    const parsedRoster = parseRosterScanRecords([rosterHeader(options.entries.length), ...options.entries].join("\n"), "roster_scan.jsonl")
    const snapshot = buildRosterSnapshots(parsedRoster)[0]
    const index = buildInspirationIndex(parseInspirationRecords((options.captures ?? []).join("\n"), "veteran_inspiration.jsonl"))
    const corpus = parseCorpus((options.careers ?? []).flat().join("\n"), "careers.jsonl")
    const library = buildVeteranLibrary({ outcomes: corpus.outcomes, sparks: corpus.sparks })
    const reconciliation = options.withReconciliation === false ? null : reconcileRoster(library, snapshot)
    const evidence = buildRetentionEvidence(snapshot, index, reconciliation)
    const report = buildRetentionShadowReport({
        evidence,
        library,
        reconciliation,
        profile: TARGET_PROFILES[options.profile ?? "GENERAL_INHERITANCE"],
        manualProtect: new Set(options.manualProtect ?? []),
    })
    return { snapshot, evidence, library, reconciliation, report }
}

/** Finds the recommendation for a roster fingerprint. */
function forFingerprint(report: RetentionShadowReport, fingerprint: string): VeteranRetentionRecommendation {
    const found = report.recommendations.find((r) => r.rosterFingerprint === fingerprint)
    if (!found) throw new Error(`no recommendation for ${fingerprint}`)
    return found
}

/** Three same-character Veterans, every one captured, so scarcity coverage is account-wide. */
function fullyCoveredTrio(subjectFactors: ReturnType<typeof factor>[], dominatorFactors: ReturnType<typeof factor>[], overrides: Record<string, unknown> = {}) {
    return {
        entries: [
            rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-subject", rating: 16000, stats: { spd: 700, sta: 500, pwr: 500, grt: 400, wit: 400 }, ...overrides }),
            rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-dominator", rating: 12000, stats: { spd: 1100, sta: 900, pwr: 800, grt: 700, wit: 600 } }),
            rosterEntry({ scanIndex: 2, rosterFingerprint: "fp-third", rating: 11000, stats: { spd: 1000, sta: 800, pwr: 800, grt: 700, wit: 600 } }),
        ],
        captures: [capture("fp-subject", subjectFactors), capture("fp-dominator", dominatorFactors), capture("fp-third", dominatorFactors)],
        careers: [
            career("Taiki_Shuttle", { spd: 1200, sta: 1000, pwr: 900, grt: 800, wit: 700 }, 100000),
            career("Taiki_Shuttle", { spd: 1150, sta: 980, pwr: 880, grt: 790, wit: 690 }, 100001),
            career("Taiki_Shuttle", { spd: 1100, sta: 960, pwr: 860, grt: 780, wit: 680 }, 100002),
            career("Taiki_Shuttle", { spd: 1050, sta: 940, pwr: 840, grt: 770, wit: 670 }, 100003),
            career("Taiki_Shuttle", { spd: 1000, sta: 920, pwr: 820, grt: 760, wit: 660 }, 100004),
        ],
    }
}

const GENERIC_SUBJECT = [factor("stat", "Speed", 1), factor("aptitude", "Mile", 1), factor("unique", "Shuttle Dash", 1), factor("white", "Corner Recovery", 1)]
const GENERIC_DOMINATOR = [factor("stat", "Speed", 1), factor("aptitude", "Mile", 1), factor("unique", "Shuttle Dash", 1), factor("white", "Corner Recovery", 1), factor("white", "Homestretch Haste", 1)]

// A subject carrying a two-star factor no peer carries, and a peer that outscores it on every
// dimension without carrying that factor. The pair exists because scoring higher and substituting are
// different things: this peer wins the numbers and still cannot replace what would be lost.
const SUBJECT_WITH_UNIQUE = [...GENERIC_SUBJECT, factor("white", "Rare Trick", 2)]
const STRONGER_NON_COVERING = [...GENERIC_DOMINATOR, factor("white", "Slipstream", 1), factor("white", "Late Kick", 2)]

describe("PL-R2 hard protection", () => {
    it("treats an unresolved roster identity as UNKNOWN at INSUFFICIENT confidence", () => {
        const { report } = build({
            entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: null })],
        })
        const r = report.recommendations[0]
        expect(r.state).toBe("UNKNOWN")
        expect(r.confidence).toBe("INSUFFICIENT")
        expect(r.gateReasons).toContain("ROSTER_IDENTITY_UNRESOLVED")
    })

    it("gates every Veteran on unknown favorite and protection state", () => {
        const { report } = build({ entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-0" })] })
        const r = forFingerprint(report, "fp-0")
        expect(r.gateReasons).toEqual(expect.arrayContaining(["PROTECTION_STATE_UNKNOWN", "FAVORITE_STATE_UNKNOWN"]))
        expect(r.unknownEvidence).toEqual(expect.arrayContaining(["rosterEntry.favoriteState", "rosterEntry.protectionState"]))
    })

    it("hard-protects the only Veteran of a character", () => {
        const { report } = build({
            entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-solo", character: "Maruzensky" }), rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-a" }), rosterEntry({ scanIndex: 2, rosterFingerprint: "fp-b" })],
        })
        expect(forFingerprint(report, "fp-solo").hardProtectReasons).toContain("SOLE_CHARACTER_SOURCE")
        expect(forFingerprint(report, "fp-a").hardProtectReasons).not.toContain("SOLE_CHARACTER_SOURCE")
    })

    it("hard-protects the only Veteran of a character/outfit pairing without double-reporting a sole character", () => {
        const { report } = build({
            entries: [
                rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-outfit", outfit: "Formula R" }),
                rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-a" }),
                rosterEntry({ scanIndex: 2, rosterFingerprint: "fp-b" }),
                rosterEntry({ scanIndex: 3, rosterFingerprint: "fp-solo", character: "Maruzensky", outfit: "Formula R" }),
            ],
        })
        expect(forFingerprint(report, "fp-outfit").hardProtectReasons).toContain("SOLE_CHARACTER_OUTFIT_SOURCE")
        const solo = forFingerprint(report, "fp-solo").hardProtectReasons
        expect(solo).toContain("SOLE_CHARACTER_SOURCE")
        expect(solo).not.toContain("SOLE_CHARACTER_OUTFIT_SOURCE")
    })

    it("honours an operator protect list", () => {
        const { report } = build({
            entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-0" }), rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-1" }), rosterEntry({ scanIndex: 2, rosterFingerprint: "fp-2" })],
            manualProtect: ["fp-1"],
        })
        expect(forFingerprint(report, "fp-1").state).toBe("HARD_PROTECT")
        expect(forFingerprint(report, "fp-1").hardProtectReasons).toContain("MANUAL_PROTECT")
        expect(forFingerprint(report, "fp-0").hardProtectReasons).not.toContain("MANUAL_PROTECT")
    })

    it("hard-protects the only Veteran covering a gated target profile", () => {
        const lowMile = { ...APTITUDES, mile: "C" }
        const { report } = build({
            entries: [
                rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-only-mile", aptitudes: { ...APTITUDES, mile: "A" } }),
                rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-a", aptitudes: lowMile }),
                rosterEntry({ scanIndex: 2, rosterFingerprint: "fp-b", aptitudes: lowMile }),
            ],
            profile: "MILE_PARENT",
        })
        const only = forFingerprint(report, "fp-only-mile")
        expect(only.hardProtectReasons).toContain("SOLE_TARGET_APTITUDE_COVERAGE")
        expect(only.coverageSummary.soleTargetCoverage).toContain("MILE_PARENT")
    })
})

describe("PL-R2 scarcity confidence", () => {
    it("refuses an account-wide claim when only part of the roster is captured", () => {
        const entries = Array.from({ length: 20 }, (_, i) => rosterEntry({ scanIndex: i, rosterFingerprint: `fp-${i}` }))
        const { report } = build({
            entries,
            captures: [capture("fp-0", [factor("stat", "Speed", 3)])],
        })
        expect(report.scarcity.accountWide).toBe(false)
        expect(report.scarcity.coverage).toBeCloseTo(0.05, 4)
        const r = forFingerprint(report, "fp-0")
        // The observation is real, so it still protects - but only as a keep reason, never as an
        // account-wide uniqueness claim.
        expect(r.factorValueSummary.scarcestClaim).toBe("OBSERVED_UNIQUE")
        expect(r.hardProtectReasons).not.toContain("OBSERVED_UNIQUE_FACTOR")
        expect(r.gateReasons).toContain("SCARCITY_COVERAGE_INSUFFICIENT")
        expect(r.state).toBe("KEEP")
    })

    it("makes an account-wide claim once every identified entry has a trusted capture", () => {
        const entries = [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-0" }), rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-1" }), rosterEntry({ scanIndex: 2, rosterFingerprint: "fp-2" })]
        const common = [factor("stat", "Speed", 1)]
        const { report } = build({
            entries,
            captures: [capture("fp-0", [factor("stat", "Speed", 3)]), capture("fp-1", common), capture("fp-2", common)],
        })
        expect(report.scarcity.accountWide).toBe(true)
        const r = forFingerprint(report, "fp-0")
        expect(r.factorValueSummary.scarcestClaim).toBe("ACCOUNT_UNIQUE")
        expect(r.hardProtectReasons).toContain("OBSERVED_UNIQUE_FACTOR")
        expect(r.state).toBe("HARD_PROTECT")
    })

    it("excludes an untrusted capture from the coverage numerator rather than counting it as absent", () => {
        const { report } = build({
            entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-0" }), rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-1" })],
            captures: [capture("fp-0", [factor("stat", "Speed", 2)]), capture("fp-1", [factor("stat", "Speed", 2)], { selfFactorSetTrusted: false })],
        })
        expect(report.scarcity.capturedTrusted).toBe(1)
        expect(report.scarcity.capturedUntrusted).toBe(1)
        expect(report.scarcity.accountWide).toBe(false)
        expect(forFingerprint(report, "fp-1").gateReasons).toContain("INSPIRATION_FACTORS_UNTRUSTED")
    })
})

describe("PL-R2 rating is a weak dimension", () => {
    it("keeps a lower-rated rare-factor Veteran above a higher-rated generic one", () => {
        const entries = Array.from({ length: 6 }, (_, i) => rosterEntry({ scanIndex: i, rosterFingerprint: `fp-${i}`, rating: 10000 + i }))
        const generic = [factor("stat", "Speed", 1)]
        const { report } = build({
            entries,
            // fp-0 is the LOWEST rated and carries the rare factor; fp-5 is the highest rated and generic.
            captures: [capture("fp-0", [factor("stat", "Wit", 3)]), ...[1, 2, 3, 4, 5].map((i) => capture(`fp-${i}`, generic))],
        })
        const rare = forFingerprint(report, "fp-0")
        const topRated = forFingerprint(report, "fp-5")
        expect(rare.factorValueSummary.rating).toBeLessThan(topRated.factorValueSummary.rating as number)
        expect(rare.state).toBe("HARD_PROTECT")
        expect(rare.hardProtectReasons).toContain("OBSERVED_UNIQUE_FACTOR")
        expect(topRated.hardProtectReasons).not.toContain("OBSERVED_UNIQUE_FACTOR")
    })

    it("never admits rating as a dominance dimension", () => {
        expect(TARGET_DIMENSION_NAMES).not.toContain("rating")
        const parsed = parseRosterScanRecords([rosterHeader(1), rosterEntry({ rating: 99999 })].join("\n"))
        const entry = buildRosterSnapshots(parsed)[0].entries[0]
        const dims = targetDimensions(entry, [], TARGET_PROFILES.GENERAL_INHERITANCE)
        expect(Object.values(dims).every((v) => v < 99999)).toBe(true)
    })
})

describe("PL-R2 missing telemetry is not negative evidence", () => {
    it("does not penalize a roster-only Veteran for having no career history", () => {
        const { report } = build({
            entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-0" }), rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-1" }), rosterEntry({ scanIndex: 2, rosterFingerprint: "fp-2" })],
            careers: [],
        })
        for (const r of report.recommendations) {
            expect(r.replacement.difficulty).toBe("UNKNOWN")
            expect(r.gateReasons).toContain("REPLACEMENT_DIFFICULTY_UNKNOWN")
            expect(r.state).not.toBe("SAFE_TO_TRANSFER")
            expect(r.state).not.toBe("QUARANTINE_TRANSFER")
            expect(r.riskReasons).not.toContain("DOMINATED_BY_PEER")
        }
    })

    it("reports an absent Inspiration capture as UNKNOWN rather than as a zero-value factor set", () => {
        const { report } = build({ entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-0" }), rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-1" }), rosterEntry({ scanIndex: 2, rosterFingerprint: "fp-2" })] })
        const r = forFingerprint(report, "fp-0")
        expect(r.state).toBe("UNKNOWN")
        expect(r.factorValueSummary.totalFactorStars).toBeNull()
        expect(r.factorValueSummary.scarcestClaim).toBe("UNMEASURED")
        expect(r.gateReasons).toContain("INSPIRATION_CAPTURE_MISSING")
    })
})

describe("PL-R2 replacement difficulty", () => {
    it("hard-protects an outcome no other historical career for the trainee reached", () => {
        const { report } = build({
            entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-best", stats: { spd: 1400, sta: 1200, pwr: 1100, grt: 1000, wit: 900 } }), rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-a" }), rosterEntry({ scanIndex: 2, rosterFingerprint: "fp-b" })],
            careers: [
                career("Taiki_Shuttle", { spd: 900, sta: 700, pwr: 650, grt: 600, wit: 500 }, 100000),
                career("Taiki_Shuttle", { spd: 890, sta: 690, pwr: 640, grt: 590, wit: 490 }, 100001),
                career("Taiki_Shuttle", { spd: 880, sta: 680, pwr: 630, grt: 580, wit: 480 }, 100002),
            ],
        })
        const best = forFingerprint(report, "fp-best")
        expect(best.replacement.difficulty).toBe("VERY_HARD")
        expect(best.hardProtectReasons).toContain("IRREPLACEABLE_HISTORICAL_OUTCOME")
        expect(best.state).toBe("HARD_PROTECT")
    })

    it("reports UNKNOWN below the minimum historical sample and lets that protect", () => {
        const { evidence, library } = build({
            entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-0" })],
            careers: [career("Taiki_Shuttle", { spd: 900, sta: 700, pwr: 650, grt: 600, wit: 500 }, 100000), career("Taiki_Shuttle", { spd: 890, sta: 690, pwr: 640, grt: 590, wit: 490 }, 100001)],
        })
        const summary = replacementSummary(evidence.veterans[0], library)
        expect(summary.difficulty).toBe("UNKNOWN")
        expect(summary.historicalSamples).toBe(2)
        expect(summary.basis).toMatch(/below the 3 needed/)
    })

    it("never states the band as a probability", () => {
        const { evidence, library } = build(fullyCoveredTrio(GENERIC_SUBJECT, GENERIC_DOMINATOR))
        const summary = replacementSummary(evidence.veterans[0], library)
        expect(summary.difficulty).not.toBe("UNKNOWN")
        expect(summary.basis).toMatch(/not a reroll probability/)
    })
})

describe("PL-R2 dominance", () => {
    function candidates(fixture: ReturnType<typeof fullyCoveredTrio>, profile: keyof typeof TARGET_PROFILES = "GENERAL_INHERITANCE") {
        const { evidence, report } = build({ ...fixture, profile })
        const scarcity = buildFactorScarcityIndex(evidence)
        const make = (fingerprint: string) => {
            const v = evidence.veterans.find((x) => x.rosterFingerprint === fingerprint)
            if (!v) throw new Error(`no evidence for ${fingerprint}`)
            const rec = forFingerprint(report, fingerprint)
            return { evidence: v, dimensions: targetDimensions(v.entry, v.selfFactors, TARGET_PROFILES[profile]), hardProtectReasons: rec.hardProtectReasons, observedUnique: observedUniqueFactorKeys(v, scarcity) }
        }
        return { report, subject: make("fp-subject"), dominator: make("fp-dominator") }
    }

    it("establishes true Pareto dominance when the factor set is covered", () => {
        const { subject, dominator } = candidates(fullyCoveredTrio(GENERIC_SUBJECT, GENERIC_DOMINATOR))
        const finding = evaluateDominance(dominator, subject, TARGET_PROFILES.GENERAL_INHERITANCE)
        expect(finding).not.toBeNull()
        expect(finding?.blockedBy).toEqual([])
        expect(finding?.strictlyBetterOn).toEqual(expect.arrayContaining(["whiteFactorCount", "totalFactorStars"]))
    })

    it("blocks dominance when the subject carries a factor the candidate does not cover", () => {
        const subjectWithExtra = [...GENERIC_SUBJECT, factor("white", "Slipstream", 1)]
        const { subject, dominator } = candidates(fullyCoveredTrio(subjectWithExtra, GENERIC_DOMINATOR))
        const finding = evaluateDominance(dominator, subject, TARGET_PROFILES.GENERAL_INHERITANCE)
        // Equal white counts and equal totals mean the candidate is no longer strictly better anywhere.
        expect(finding).toBeNull()
    })

    it("blocks dominance when the subject is the only observed carrier of a high-value factor", () => {
        const { subject, dominator } = candidates(fullyCoveredTrio(SUBJECT_WITH_UNIQUE, STRONGER_NON_COVERING))
        expect(subject.observedUnique).toEqual(["white:RARE TRICK"])
        const finding = evaluateDominance(dominator, subject, TARGET_PROFILES.GENERAL_INHERITANCE)
        // The peer wins every dimension, so it reaches the strict gates - and fails them.
        expect(finding).not.toBeNull()
        expect(finding?.strictlyBetterOn.length).toBeGreaterThan(0)
        expect(finding?.blockedBy).toContain("SUBJECT_HAS_UNIQUE_COVERAGE")
        expect(finding?.blockedBy).toContain("FACTOR_SET_NOT_COVERED")
    })

    it("refuses any comparison when either side lacks trusted factor evidence", () => {
        const fixture = fullyCoveredTrio(GENERIC_SUBJECT, GENERIC_DOMINATOR)
        const withUntrustedSubject = { ...fixture, captures: [capture("fp-subject", GENERIC_SUBJECT, { sparkCaptureComplete: false }), ...fixture.captures.slice(1)] }
        const { report } = build(withUntrustedSubject)
        const subject = forFingerprint(report, "fp-subject")
        expect(subject.dominators).toEqual([])
        expect(subject.substitutes).toEqual([])
        expect(subject.gateReasons).toContain("INSPIRATION_CAPTURE_INCOMPLETE")
        expect(subject.state).toBe("UNKNOWN")
    })

    it("changes the dominance outcome when the target profile changes", () => {
        // Identical factor sets; the subject is the better Long prospect on aptitude grade alone.
        const fixture = fullyCoveredTrio(GENERIC_SUBJECT, GENERIC_DOMINATOR, { aptitudes: { ...APTITUDES, long: "S" } })
        const general = candidates(fixture, "GENERAL_INHERITANCE")
        const long = candidates(fixture, "LONG_PARENT")
        expect(evaluateDominance(general.dominator, general.subject, TARGET_PROFILES.GENERAL_INHERITANCE)?.blockedBy).toEqual([])
        // Under LONG_PARENT the candidate is WORSE on targetAptitudeGrade, so it dominates nothing.
        expect(evaluateDominance(long.dominator, long.subject, TARGET_PROFILES.LONG_PARENT)).toBeNull()
    })

    it("reports a near-miss peer as a substitute with the gate that stopped it", () => {
        const { report } = build(fullyCoveredTrio(SUBJECT_WITH_UNIQUE, STRONGER_NON_COVERING))
        const subject = forFingerprint(report, "fp-subject")
        expect(subject.dominators).toEqual([])
        expect(subject.substitutes.length).toBeGreaterThan(0)
        expect(subject.substitutes[0].blockedBy).toContain("SUBJECT_HAS_UNIQUE_COVERAGE")
        expect(subject.substitutes[0].explanation).toMatch(/does not replace this Veteran/)
    })
})

describe("PL-R2 recommendation gates", () => {
    /** The one fixture where every strict gate is satisfiable, so the transfer side is reachable. */
    function unlockedTrio(subjectFactors = GENERIC_SUBJECT, dominatorFactors = GENERIC_DOMINATOR) {
        const fixture = fullyCoveredTrio(subjectFactors, dominatorFactors, { favoriteState: "not_set", protectionState: "not_protected" })
        return fixture
    }

    it("produces SAFE_TO_TRANSFER only when every strict gate passes", () => {
        const { report } = build(unlockedTrio())
        const subject = forFingerprint(report, "fp-subject")
        expect(subject.confidence).toBe("HIGH")
        expect(subject.gateReasons).toEqual([])
        expect(subject.dominators.length).toBeGreaterThan(0)
        expect(subject.state).toBe("SAFE_TO_TRANSFER")
    })

    it("withdraws SAFE_TO_TRANSFER the moment in-game protection cannot be excluded", () => {
        const fixture = unlockedTrio()
        const gated = { ...fixture, entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-subject", rating: 16000, stats: { spd: 700, sta: 500, pwr: 500, grt: 400, wit: 400 }, favoriteState: "unknown", protectionState: "unknown" }), ...fixture.entries.slice(1)] }
        const subject = forFingerprint(build(gated).report, "fp-subject")
        expect(subject.gateReasons).toEqual(expect.arrayContaining(["PROTECTION_STATE_UNKNOWN", "FAVORITE_STATE_UNKNOWN"]))
        expect(subject.state).toBe("QUARANTINE_TRANSFER")
    })

    it("withdraws the transfer side entirely when the subject adds unique coverage", () => {
        // Same fully-unlocked account as the SAFE_TO_TRANSFER case above; the ONLY difference is that
        // the subject now carries a two-star factor no peer carries.
        const subject = forFingerprint(build(unlockedTrio(SUBJECT_WITH_UNIQUE, STRONGER_NON_COVERING)).report, "fp-subject")
        expect(subject.factorValueSummary.observedUniqueFactorKeys).toEqual(["white:RARE TRICK"])
        expect(subject.dominators).toEqual([])
        expect(subject.state).toBe("HARD_PROTECT")
    })

    it("keeps a valuable but dominated Veteran rather than quarantining it", () => {
        // A three-star factor makes the subject a HIGH_VALUE_FACTOR_SET; the peer still covers it.
        const subjectFactors = [factor("stat", "Speed", 3), factor("aptitude", "Mile", 1), factor("unique", "Shuttle Dash", 1)]
        const dominatorFactors = [...subjectFactors, factor("white", "Homestretch Haste", 1)]
        const subject = forFingerprint(build(unlockedTrio(subjectFactors, dominatorFactors)).report, "fp-subject")
        expect(subject.keepReasons).toContain("HIGH_VALUE_FACTOR_SET")
        expect(subject.state).toBe("KEEP")
    })

    it("never reaches the transfer side without a dominator, however strong the numbers look", () => {
        const fixture = unlockedTrio()
        // One Veteran, fully captured, fully unlocked, replaceable - and nothing to replace it WITH.
        const alone = {
            ...fixture,
            entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-subject", favoriteState: "not_set", protectionState: "not_protected", stats: { spd: 700, sta: 500, pwr: 500, grt: 400, wit: 400 } })],
            captures: [capture("fp-subject", GENERIC_SUBJECT)],
        }
        const subject = forFingerprint(build(alone).report, "fp-subject")
        expect(subject.gateReasons).toContain("NO_DOMINATOR_FOUND")
        expect(subject.state).not.toBe("SAFE_TO_TRANSFER")
        expect(subject.state).not.toBe("QUARANTINE_TRANSFER")
    })

    it("marks the Transfer Request rule inactive rather than guessing at holding value", () => {
        const { report } = build({ entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-0" })] })
        expect(report.counts.TRANSFER_REQUEST_HOLD).toBe(0)
        expect(report.inactiveRules.map((r) => r.rule)).toContain("TRANSFER_REQUEST_HOLD")
        expect(INACTIVE_RULES.find((r) => r.rule === "TRANSFER_REQUEST_HOLD")?.reason).toMatch(/no Transfer Request/)
    })

    it("marks every recommendation UNKNOWN when the snapshot is not trusted-complete", () => {
        const parsed = parseRosterScanRecords([rosterHeader(2, { completeness: "incomplete", filtersOff: false }), rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-0" }), rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-1" })].join("\n"))
        const snapshot = buildRosterSnapshots(parsed)[0]
        expect(snapshot.trustedComplete).toBe(false)
        const evidence = buildRetentionEvidence(snapshot, new Map(), null)
        const report = buildRetentionShadowReport({ evidence, library: null, reconciliation: null, profile: TARGET_PROFILES.GENERAL_INHERITANCE })
        expect(report.counts.UNKNOWN).toBe(2)
        expect(report.recommendations.every((r) => r.confidence === "INSUFFICIENT")).toBe(true)
        expect(report.recommendations.every((r) => r.gateReasons.includes("ROSTER_SNAPSHOT_UNTRUSTED"))).toBe(true)
    })
})

describe("PL-R2 determinism", () => {
    it("rebuilds byte-identically from the same inputs", () => {
        const fixture = fullyCoveredTrio(GENERIC_SUBJECT, GENERIC_DOMINATOR)
        const first = JSON.stringify(build(fixture).report)
        const second = JSON.stringify(build(fixture).report)
        expect(second).toBe(first)
    })

    it("derives generatedAt from the evidence rather than from a clock", () => {
        const { report } = build(fullyCoveredTrio(GENERIC_SUBJECT, GENERIC_DOMINATOR))
        expect(report.generatedAt).toBe(T)
    })

    it("does not mutate its inputs", () => {
        const fixture = fullyCoveredTrio(GENERIC_SUBJECT, GENERIC_DOMINATOR)
        const { snapshot, evidence } = build(fixture)
        const snapshotBefore = JSON.stringify(snapshot)
        const evidenceBefore = JSON.stringify(evidence.veterans.map((v) => v.entry))
        buildRetentionShadowReport({ evidence, library: null, reconciliation: null, profile: TARGET_PROFILES.GENERAL_INHERITANCE })
        expect(JSON.stringify(snapshot)).toBe(snapshotBefore)
        expect(JSON.stringify(evidence.veterans.map((v) => v.entry))).toBe(evidenceBefore)
    })
})
