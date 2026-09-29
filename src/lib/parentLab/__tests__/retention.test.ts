import { runParentLabCli } from "./cliFixture.ts"
import { fixtureFingerprint, fixtureRank } from "./rosterFixtures.ts"
import { parseCorpus } from "../../outcomeAnalysis.ts"
import { contentHash128 } from "../identity.ts"
import { buildProtectionInventory, latestProtectionRecord, parseProtectionRecords } from "../protection.ts"
import { buildVeteranLibrary } from "../buildVeteranLibrary.ts"
import { buildCapacityCoverage } from "../capacityCoverage.ts"
import { buildInspirationIndex, parseInspirationRecords } from "../inspiration.ts"
import { buildAdvisorSnapshot } from "../quarantineSnapshot.ts"
import { reconcileRoster } from "../reconcile.ts"
import { buildRosterSnapshots, parseRosterScanRecords, rosterBindingDigest } from "../roster.ts"
import { buildRetentionShadowReport, evaluateDominance, INACTIVE_RULES } from "../retentionAdvisor.ts"
import { buildFactorScarcityIndex, buildRetentionEvidence, observedUniqueFactorKeys, replacementSummary } from "../retentionEvidence.ts"
import { targetDimensions, TARGET_DIMENSION_NAMES, TARGET_PROFILES } from "../retentionTargets.ts"
import { PARENTLAB_RETENTION_SCHEMA_VERSION } from "../retentionTypes.ts"
import type { RetentionShadowReport, VeteranRetentionRecommendation } from "../retentionTypes.ts"

// Every fixture goes through the real parse paths - roster rows and inspiration captures as JSONL,
// careers through parseCorpus into buildVeteranLibrary - so a change to any ingest path shows up here
// rather than being papered over by hand-built objects.

const SCAN = "rs-test-0001"
const ISCAN = "insp-test-0001"
const T = Date.UTC(2026, 7, 21, 12, 0, 0)

const APTITUDES = { turf: "A", dirt: "G", sprint: "C", mile: "A", medium: "A", long: "B", front: "A", pace: "A", late: "B", end: "C" }
const NEUTRAL_FILTERS = { track: "neutral", distance: "neutral", style: "neutral", attribute_sparks: "neutral", aptitude_sparks: "neutral", unique_sparks: "neutral", common_sparks: "neutral", favorites: "neutral", memo: "neutral" }
const rosterFingerprints = new Map<string, string>()
const rosterIdentities = new Map<string, { character: unknown; outfit: unknown; rank: unknown }>()
const testFingerprint = (label: string) => rosterFingerprints.get(label) ?? contentHash128(`retention fixture:${label}`)

function rosterEntry(o: Record<string, unknown> = {}): string {
    const record: Record<string, unknown> = {
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
    }
    if (!("rating" in o)) record.rating = 15000 - (record.scanIndex as number)
    if (!("rank" in o)) record.rank = fixtureRank(record.rating as number)
    if (typeof record.rosterFingerprint === "string") {
        const label = record.rosterFingerprint
        record.rosterFingerprint = fixtureFingerprint(record)
        rosterFingerprints.set(label, record.rosterFingerprint as string)
        rosterIdentities.set(record.rosterFingerprint as string, { character: record.character, outfit: record.outfit, rank: record.rank })
    }
    return JSON.stringify(record)
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
        terminationReason: count === 0 ? "empty_list" : "cycle_closed",
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

function factorSetFingerprint(factors: readonly ReturnType<typeof factor>[]): string {
    return factors.map((entry) => entry.factorFingerprint).sort().join("|")
}

function structuralFactorSetFingerprint(factors: readonly ReturnType<typeof factor>[]): string {
    return factors.map((entry) => entry.structuralFingerprint).sort().join("|")
}

function capture(fingerprint: string, factors: ReturnType<typeof factor>[], o: Record<string, unknown> = {}): string {
    const selfFactors = factors.map((entry, rowIndex) => ({ ...entry, rowIndex }))
    const record: Record<string, unknown> = {
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
        selfFactorFingerprint: factorSetFingerprint(factors),
        selfStructuralFingerprint: structuralFactorSetFingerprint(factors),
        selfFactorSetTrusted: true,
        selfFactors,
        legacyAncestors: [],
        termination: "reached_bottom",
        sparkCaptureComplete: true,
        screenReadCompleteness: 1,
        unresolvedFields: [],
        diagnostics: {
            frames: 1, swipes: 0, startedAtTop: true, reachedBottom: true, factorListEndObserved: true,
            gapFrames: 0, spacingBreaks: 0, alignmentFailures: 0, unsettledFrames: 0, deadReckonedFrames: 0,
            scrollbarContentHeight: null, observedContentHeight: null, rowsAccepted: new Set(selfFactors.map((entry) => entry.rowIndex)).size,
            clippedRowsRejected: 0, leadingPartialBlockRows: 0, blocksObserved: 1,
        },
        ...rosterIdentities.get(testFingerprint(fingerprint)),
        ...o,
    }
    if (typeof record.rosterFingerprint === "string") record.rosterFingerprint = testFingerprint(record.rosterFingerprint)
    return JSON.stringify(record)
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
    readonly captureCompatibility?: boolean
    readonly extraInspirationLines?: readonly string[]
    readonly protection?: boolean
    readonly protectionTail?: readonly string[]
}

function build(options: BuildOptions) {
    const parsedRoster = parseRosterScanRecords([rosterHeader(options.entries.length), ...options.entries].join("\n"), "roster_scan.jsonl")
    const snapshot = buildRosterSnapshots(parsedRoster)[0]
    const inspirationHeader = JSON.stringify({ type: "veteran_inspiration_scan", schemaVersion: 2, scanId: ISCAN, registeredUsedAtStart: options.entries.length, registeredUsedAtEnd: options.entries.length, registeredCapacity: 260, filtersOff: true, entryLimit: 0, startIndex: 0, entriesCaptured: options.captures?.length ?? 0, entriesComplete: (options.captures ?? []).filter((line) => JSON.parse(line).sparkCaptureComplete).length, terminationReason: "cycle_closed", pagerCycleClosed: (options.captures?.length ?? 0) === options.entries.length && options.captureCompatibility !== false, snapshotCompatibility: options.captureCompatibility ?? true })
    const captures = (options.captures ?? []).map((line, scanIndex) => JSON.stringify({ ...JSON.parse(line), scanIndex }))
    const index = buildInspirationIndex(parseInspirationRecords([inspirationHeader, ...captures, ...(options.extraInspirationLines ?? [])].join("\n"), "veteran_inspiration.jsonl"))
    const corpus = parseCorpus((options.careers ?? []).flat().join("\n"), "careers.jsonl")
    const library = buildVeteranLibrary({ outcomes: corpus.outcomes, sparks: corpus.sparks })
    const reconciliation = options.withReconciliation === false ? null : reconcileRoster(library, snapshot)
    const protectionLine = JSON.stringify({ type: "veteran_protection", schemaVersion: 2, rosterBindingVersion: 1, rosterScanId: snapshot.scanId, rosterDigest: rosterBindingDigest(snapshot), scanId: "vp-retention-test", registeredUsed: snapshot.registeredUsed, registeredCapacity: 260, filtersOffConfirmed: true, favoritePopulation: "empty", favoriteApplyState: "disabled", favoriteBaselineVerified: true, filterBaselineEvidenceVersion: 1, favoriteBaselineReadings: NEUTRAL_FILTERS, memoPopulation: "empty", memoApplyState: "disabled", memoBaselineVerified: true, memoBaselineReadings: NEUTRAL_FILTERS, enumerationPerformed: false, favoritedFingerprints: [], memoFingerprints: [], restoredFiltersOff: true, outcome: "complete" })
    const protectionRecord = options.protection ? latestProtectionRecord(parseProtectionRecords([protectionLine, ...(options.protectionTail ?? [])].join("\n"))) : null
    const inventory = options.protection ? buildProtectionInventory(protectionRecord, snapshot) : null
    const evidence = buildRetentionEvidence(snapshot, index, reconciliation, inventory)
    const report = buildRetentionShadowReport({
        evidence,
        library,
        reconciliation,
        profile: TARGET_PROFILES[options.profile ?? "GENERAL_INHERITANCE"],
        manualProtect: new Set((options.manualProtect ?? []).map(testFingerprint)),
    })
    const sources = {
        roster: [rosterHeader(options.entries.length), ...options.entries].join("\n"),
        inspiration: [inspirationHeader, ...captures, ...(options.extraInspirationLines ?? [])].join("\n"),
        careers: (options.careers ?? []).flat().join("\n"),
        protection: [protectionLine, ...(options.protectionTail ?? [])].join("\n"),
    }
    return { snapshot, evidence, library, reconciliation, report, sources }
}

function completeInspirationScan(scanId: string, captures: readonly string[]): string {
    const entries = captures.map((line, scanIndex) => JSON.stringify({ ...JSON.parse(line), scanId, scanIndex }))
    const header = JSON.stringify({
        type: "veteran_inspiration_scan", schemaVersion: 2, scanId,
        registeredUsedAtStart: entries.length, registeredUsedAtEnd: entries.length, registeredCapacity: 260, filtersOff: true,
        entryLimit: 0, startIndex: 0, entriesCaptured: entries.length,
        entriesComplete: entries.filter((line) => JSON.parse(line).sparkCaptureComplete).length,
        terminationReason: "cycle_closed", pagerCycleClosed: true, snapshotCompatibility: true,
    })
    return [header, ...entries].join("\n")
}

function mutateSubjectCapture(source: string, mutate: (record: Record<string, any>) => void): string {
    return source.split("\n").map((line) => {
        const record = JSON.parse(line)
        if (record.type === "veteran_inspiration" && record.scanId === ISCAN && record.scanIndex === 0) mutate(record)
        return JSON.stringify(record)
    }).join("\n")
}

/** Finds the recommendation for a roster fingerprint. */
function forFingerprint(report: RetentionShadowReport, fingerprint: string): VeteranRetentionRecommendation {
    const found = report.recommendations.find((r) => r.rosterFingerprint === testFingerprint(fingerprint))
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
        expect(report.scarcity.coverage).toBe(0)
        const r = forFingerprint(report, "fp-0")
        expect(r.factorValueSummary.scarcestClaim).not.toBe("ACCOUNT_UNIQUE")
        expect(r.hardProtectReasons).not.toContain("OBSERVED_UNIQUE_FACTOR")
        expect(r.gateReasons).toContain("SCARCITY_COVERAGE_INSUFFICIENT")
        expect(r.state).toBe("UNKNOWN")
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

    it("leaves a career with an unread final stat out of the stat-total comparison", () => {
        // -1 is the bot's unread value; summed as a number it would rank that career below every real one.
        const known = [
            career("Taiki_Shuttle", { spd: 900, sta: 700, pwr: 650, grt: 600, wit: 500 }, 100000),
            career("Taiki_Shuttle", { spd: 890, sta: 690, pwr: 640, grt: 590, wit: 490 }, 100001),
        ]
        const unread = career("Taiki_Shuttle", { spd: 900, sta: 700, pwr: 650, grt: 600, wit: -1 }, 100002)
        const { evidence, library } = build({ entries: [rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-0" })], careers: [...known, unread] })
        const summary = replacementSummary(evidence.veterans[0], library)
        expect(summary.historicalSamples).toBe(2)
        expect(summary.difficulty).toBe("UNKNOWN")
        expect(summary.basis).toMatch(/every stat read/)
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
            const v = evidence.veterans.find((x) => x.rosterFingerprint === testFingerprint(fingerprint))
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
        return { ...fixture, protection: true }
    }

    function ancestorBlocks(record: Record<string, any>, count = 2) {
        record.legacyAncestors = Array.from({ length: count }, (_, ancestorIndex) => ({
            ancestorIndex, portraitObserved: true, factorCount: record.selfFactors.length,
            ancestorFactorFingerprint: record.selfFactorFingerprint,
            ancestorStructuralFingerprint: record.selfStructuralFingerprint,
            factorSetTrusted: true, factors: record.selfFactors.map((entry: Record<string, any>) => ({ ...entry })),
        }))
        record.diagnostics.blocksObserved = count + 1
        record.diagnostics.rowsAccepted = new Set(record.selfFactors.map((entry: Record<string, any>) => entry.rowIndex)).size * (count + 1)
    }

    it.each<[string, unknown]>([
        ["missing", undefined], ["below count", 1], ["fraction", 260.5],
        ["string", "260"], ["array", [260]], ["outside Int", 2_147_483_648],
    ])("rejects roster capacity declaration %s through consumers and CLI", (_, capacity) => {
        const fixture = build(unlockedTrio())
        const positive = runParentLabCli("retention", fixture.sources, ["--json"])
        expect(positive.status).toBe(0)
        expect(forFingerprint(JSON.parse(positive.stdout), "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const roster = fixture.sources.roster.split("\n").map((line) => {
            const record = JSON.parse(line)
            if (record.type === "roster_scan") record.displayedRegisteredCapacity = capacity
            return JSON.stringify(record)
        }).join("\n")
        const snapshot = buildRosterSnapshots(parseRosterScanRecords(roster))[0]
        const evidence = buildRetentionEvidence(snapshot, buildInspirationIndex(parseInspirationRecords(fixture.sources.inspiration)), fixture.reconciliation)
        const result = runParentLabCli("retention", { ...fixture.sources, roster }, ["--json"])
        expect(result.status).toBe(1)
        const report = JSON.parse(result.stdout)
        expect({ trusted: snapshot.trustedComplete, consumer: buildFactorScarcityIndex(evidence).accountWide,
            cli: report.scarcity.accountWide, safe: report.counts.SAFE_TO_TRANSFER }).toEqual({ trusted: false, consumer: false, cli: false, safe: 0 })
    }, 30_000)

    it.each([3, 260, 2_147_483_647, 2_147_483_648])("checks matching roster/protection capacity %i against producer bounds", (capacity) => {
        const fixture = build(unlockedTrio())
        expect(forFingerprint(fixture.report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const sources = { ...fixture.sources }
        sources.roster = sources.roster.split("\n").map((line) => {
            const record = JSON.parse(line)
            if (record.type === "roster_scan") record.displayedRegisteredCapacity = capacity
            return JSON.stringify(record)
        }).join("\n")
        sources.protection = JSON.stringify({ ...JSON.parse(sources.protection), registeredCapacity: capacity })
        const snapshot = buildRosterSnapshots(parseRosterScanRecords(sources.roster))[0]
        const protection = latestProtectionRecord(parseProtectionRecords(sources.protection))
        const inventory = buildProtectionInventory(protection, snapshot)
        const valid = capacity <= 2_147_483_647
        const result = runParentLabCli("retention", sources, ["--json"])
        expect(result.status).toBe(valid ? 0 : 1)
        const report = JSON.parse(result.stdout)
        expect({ trusted: snapshot.trustedComplete, protection: inventory.compatible, accountWide: report.scarcity.accountWide,
            safe: report.counts.SAFE_TO_TRANSFER }).toEqual({ trusted: valid, protection: valid, accountWide: valid, safe: valid ? 1 : 0 })
        if (!valid) expect(inventory.defects).toContain("capacity_invalid")
    }, 30_000)

    it.each(["uniqueFingerprints", "unidentifiedCount", "duplicateFingerprintCount", "countDiscrepancy"])("rejects contradictory roster census %s without header fallback", (field) => {
        const fixture = build(unlockedTrio())
        expect(forFingerprint(fixture.report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const header = JSON.parse(fixture.sources.roster.split("\n")[0])
        header[field] = 1
        const roster = `${fixture.sources.roster}\n${JSON.stringify(header)}`
        const snapshot = buildRosterSnapshots(parseRosterScanRecords(roster))[0]
        const evidence = buildRetentionEvidence(snapshot, buildInspirationIndex(parseInspirationRecords(fixture.sources.inspiration)), fixture.reconciliation)
        const result = runParentLabCli("retention", { ...fixture.sources, roster }, ["--json"])
        expect(result.status).toBe(1)
        const report = JSON.parse(result.stdout)
        expect({ trusted: snapshot.trustedComplete, consumer: buildFactorScarcityIndex(evidence).accountWide,
            cli: report.scarcity.accountWide, safe: report.counts.SAFE_TO_TRANSFER }).toEqual({ trusted: false, consumer: false, cli: false, safe: 0 })
    }, 30_000)

    function unresolvedAncestor(record: Record<string, any>, index: number) {
        const ancestor = record.legacyAncestors[index]
        const factor = ancestor.factors[0]
        delete factor.canonicalName
        factor.canonicalPath = "reject"
        delete factor.factorFingerprint
        ancestor.factorSetTrusted = false
        delete ancestor.ancestorFactorFingerprint
        record.unresolvedFields.push(`factorCanonical@${factor.kind}:${factor.rowIndex}:${factor.column}`)
    }

    it.each([1, 2])("preserves %i unresolved ancestors sharing coordinates with resolved self factors", (count) => {
        const fixture = build(unlockedTrio())
        const inspiration = mutateSubjectCapture(fixture.sources.inspiration, (record) => {
            ancestorBlocks(record)
            for (let i = 0; i < count; i++) unresolvedAncestor(record, i)
            record.selfFactors.reverse()
            record.legacyAncestors.reverse().forEach((block: Record<string, any>) => block.factors.reverse())
            record.unresolvedFields.reverse()
        })
        const actual = observedCapture(fixture, inspiration)
        expect(actual.subject.captureTrusted).toBe(true)
        expect(actual.subject.selfFactors).not.toBeNull()
        expect(actual.report.scarcity.accountWide).toBe(true)
        expect(forFingerprint(actual.report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
    }, 30_000)

    it.each<[string, (record: Record<string, any>) => void]>([
        ["resolved self", (record) => { const f = record.selfFactors[0]; record.unresolvedFields.push(`factorCanonical@${f.kind}:${f.rowIndex}:${f.column}`) }],
        ["resolved ancestor", (record) => { const f = record.legacyAncestors[0].factors[0]; record.unresolvedFields.push(`factorCanonical@${f.kind}:${f.rowIndex}:${f.column}`) }],
        ["missing ancestor marker", (record) => { unresolvedAncestor(record, 0); record.unresolvedFields = [] }],
        ["missing duplicate marker", (record) => { unresolvedAncestor(record, 0); unresolvedAncestor(record, 1); record.unresolvedFields.pop() }],
        ["extra duplicate marker", (record) => { unresolvedAncestor(record, 0); record.unresolvedFields.push(record.unresolvedFields[0]) }],
        ["wrong marker location", (record) => { unresolvedAncestor(record, 0); record.unresolvedFields[0] = "factorCanonical@white:99:right" }],
    ])("rejects canonical declaration contradiction %s including older valid evidence", (_, mutate) => {
        const fixture = build(unlockedTrio())
        const control = mutateSubjectCapture(fixture.sources.inspiration, (record) => ancestorBlocks(record))
        expect(forFingerprint(observedCapture(fixture, control).report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const changed = mutateSubjectCapture(control, mutate)
        const older = control.split("\n").map((line) => JSON.stringify({ ...JSON.parse(line), scanId: "older-valid", observedAt: T - 1 })).join("\n")
        for (const inspiration of [changed, `${older}\n${changed}`]) {
            const actual = observedCapture(fixture, inspiration)
            expect(actual.parsed.entries.find((entry) => entry.scanId === ISCAN && entry.scanIndex === 0)?.legacyAncestors).toHaveLength(2)
            expect(actual.subject.capture).not.toBeNull()
            expect({ trusted: actual.subject.captureTrusted, factors: actual.subject.selfFactors,
                consumer: buildFactorScarcityIndex(actual.evidence).accountWide,
                cli: actual.report.scarcity.accountWide, safe: actual.report.counts.SAFE_TO_TRANSFER }).toEqual({ trusted: false, factors: null, consumer: false, cli: false, safe: 0 })
            expect(actual.subject.capture?.unresolvedFields).toContain("persistedCompleteness")
        }
    }, 30_000)

    function setStars(record: Record<string, any>, ancestor: boolean, stars: number) {
        const block = ancestor ? record.legacyAncestors[1] : record
        const factors = ancestor ? block.factors : block.selfFactors
        const entry = factors[0]
        entry.stars = stars
        entry.factorFingerprint = `${entry.kind}:${entry.canonicalName.toUpperCase()}:${stars}`
        entry.structuralFingerprint = `${entry.kind}:${stars}`
        block[ancestor ? "ancestorFactorFingerprint" : "selfFactorFingerprint"] = factorSetFingerprint(factors)
        block[ancestor ? "ancestorStructuralFingerprint" : "selfStructuralFingerprint"] = structuralFactorSetFingerprint(factors)
    }

    it.each(["older", "newer"])("unrelated canonical contradiction cannot displace valid authority %s", (age) => {
        const fixture = build(unlockedTrio())
        const positive = observedCapture(fixture, fixture.sources.inspiration)
        expect(positive.subject.captureTrusted).toBe(true)
        expect(forFingerprint(positive.report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const unrelated = fixture.sources.inspiration.split("\n").map((line) => {
            const r = JSON.parse(line)
            r.scanId = "foreign-identity"
            r.observedAt = age === "older" ? T - 100 : T + 100
            if (r.type === "veteran_inspiration" && r.scanIndex === 0) {
                r.character = "Unrelated Character"
                r.unresolvedFields = ["factorCanonical@stat:0:left"]
            }
            return JSON.stringify(r)
        }).join("\n")
        const outcomes: boolean[] = []
        for (const inspiration of [unrelated + "\n" + fixture.sources.inspiration, fixture.sources.inspiration + "\n" + unrelated]) {
            const actual = observedCapture(fixture, inspiration)
            outcomes.push(actual.subject.captureTrusted)
        }
        expect(outcomes).toEqual([true, true])
    }, 30_000)

    it.each<[string, unknown]>([
        ...["trustedForRetention", "enumerationComplete", "identityComplete"].flatMap((field) =>
            [false, null, "false", "true", 0, 1, [], {}].map((value): [string, unknown] => [field, value])),
    ])("rejects contrary roster safety declaration %s", (field, value) => {
        const fixture = build(unlockedTrio())
        const controlRoster = fixture.sources.roster.split("\n").map(line => {
            const r = JSON.parse(line)
            if (r.type === "roster_scan") Object.assign(r, { enumerationComplete: true, identityComplete: true, trustedForRetention: true })
            return JSON.stringify(r)
        }).join("\n")
        const control = runParentLabCli("retention", { ...fixture.sources, roster: controlRoster }, ["--json"])
        expect(control.status).toBe(0)
        expect(forFingerprint(JSON.parse(control.stdout), "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const roster = controlRoster.split("\n").map(line => { const r = JSON.parse(line); if (r.type === "roster_scan") r[field] = value; return JSON.stringify(r) }).join("\n")
        const snapshot = buildRosterSnapshots(parseRosterScanRecords(roster))[0]
        const evidence = buildRetentionEvidence(snapshot, buildInspirationIndex(parseInspirationRecords(fixture.sources.inspiration)), fixture.reconciliation)
        const cli = runParentLabCli("retention", { ...fixture.sources, roster }, ["--json"])
        const report = JSON.parse(cli.stdout)
        expect(snapshot.trustedComplete).toBe(false)
        expect(buildFactorScarcityIndex(evidence).accountWide).toBe(false)
        expect(report.scarcity.accountWide).toBe(false)
        expect(report.counts.SAFE_TO_TRANSFER).toBe(0)
    }, 30_000)

    it.each(["partial", "unsupported", "incompatible"])("selection boundary control %s", (kind) => {
        const fixture = build(unlockedTrio())
        const changed = fixture.sources.inspiration.split("\n").map(line => {
            const r = JSON.parse(line)
            r.scanId = "selection-control"
            r.observedAt = T + 100
            if (kind === "unsupported") r.schemaVersion = 3
            if (kind === "incompatible" && r.type === "veteran_inspiration_scan") r.snapshotCompatibility = false
            if (kind === "partial") {
                if (r.type === "veteran_inspiration_scan") r.entriesComplete = 2
                if (r.type === "veteran_inspiration" && r.scanIndex === 0) {
                    r.sparkCaptureComplete = false
                    r.screenReadCompleteness = 0.875
                    r.diagnostics.startedAtTop = false
                    r.unresolvedFields = ["startedAtTop"]
                }
            }
            return JSON.stringify(r)
        }).join("\n")
        for (const inspiration of [changed + "\n" + fixture.sources.inspiration, fixture.sources.inspiration + "\n" + changed]) {
            const actual = observedCapture(fixture, inspiration)
            expect(actual.subject.captureTrusted).toBe(true)
            expect(actual.report.scarcity.accountWide).toBe(true)
        }
    }, 30_000)

    it.each<[string, (r: Record<string, any>) => void]>([
        ["contradictory completeness", r => { r.screenReadCompleteness = 0 }],
        ["contradictory fingerprint", r => { r.selfFactorFingerprint = "stat:UNRELATED:3" }],
        ["contradictory census", r => { r.diagnostics.rowsAccepted++ }],
        ["duplicate cell", r => { r.selfFactors[1].rowIndex = r.selfFactors[0].rowIndex; r.selfFactors[1].column = r.selfFactors[0].column }],
    ])("favorable evidence cannot hide %s", (name, mutate) => {
        const fixture = build(unlockedTrio())
        expect(forFingerprint(observedCapture(fixture, fixture.sources.inspiration).report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const bad = mutateSubjectCapture(fixture.sources.inspiration, mutate)
        const badOnly = observedCapture(fixture, bad)
        expect(badOnly.subject.captureTrusted).toBe(false)
        const outcomes: boolean[] = []
        for (const age of [-100, 100]) {
            const other = bad.split("\n").map(line => JSON.stringify({ ...JSON.parse(line), scanId: "contradictory-capture", observedAt: T + age })).join("\n")
            for (const inspiration of [other + "\n" + fixture.sources.inspiration, fixture.sources.inspiration + "\n" + other]) {
                const actual = observedCapture(fixture, inspiration)
                expect(buildFactorScarcityIndex(actual.evidence).accountWide).toBe(false)
                expect(actual.report.scarcity.accountWide).toBe(false)
                expect(actual.report.counts.SAFE_TO_TRANSFER).toBe(0)
                outcomes.push(actual.subject.captureTrusted)
            }
        }
        expect(outcomes).toEqual([false, false, false, false])
    }, 30_000)

    it.each(["older", "newer"])("matching canonical contradiction blocks both orders %s", (age) => {
        const fixture = build(unlockedTrio())
        expect(forFingerprint(observedCapture(fixture, fixture.sources.inspiration).report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const bad = mutateSubjectCapture(fixture.sources.inspiration, r => { r.unresolvedFields = ["factorCanonical@stat:0:left"] })
            .split("\n").map(line => JSON.stringify({ ...JSON.parse(line), scanId: "bad-canonical", observedAt: age === "older" ? T - 100 : T + 100 })).join("\n")
        for (const inspiration of [bad + "\n" + fixture.sources.inspiration, fixture.sources.inspiration + "\n" + bad]) {
            const actual = observedCapture(fixture, inspiration)
            expect(actual.subject.captureTrusted).toBe(false)
            expect(actual.report.scarcity.accountWide).toBe(false)
            expect(actual.report.counts.SAFE_TO_TRANSFER).toBe(0)
        }
    }, 30_000)

    it("single capture fingerprint declaration controls authority", () => {
        const fixture = build(unlockedTrio())
        expect(forFingerprint(observedCapture(fixture, fixture.sources.inspiration).report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const inspiration = mutateSubjectCapture(fixture.sources.inspiration, r => { r.selfFactorFingerprint = "stat:UNRELATED:3" })
        const actual = observedCapture(fixture, inspiration)
        expect(actual.subject.captureTrusted).toBe(false)
        expect(actual.report.scarcity.accountWide).toBe(false)
        expect(actual.report.counts.SAFE_TO_TRANSFER).toBe(0)
    }, 30_000)

    it.each(["trustedForRetention", "enumerationComplete", "identityComplete", "all"])("preserves omitted legacy safety declarations %s", (field) => {
        const fixture = build(unlockedTrio())
        const roster = fixture.sources.roster.split("\n").map((line) => {
            const record = JSON.parse(line)
            if (record.type === "roster_scan") {
                for (const key of ["trustedForRetention", "enumerationComplete", "identityComplete"]) {
                    if (field === "all" || key === field) delete record[key]
                    else record[key] = true
                }
                record.unrelatedDiagnostic = { observed: "unknown" }
            }
            return JSON.stringify(record)
        }).join("\n")
        const snapshot = buildRosterSnapshots(parseRosterScanRecords(roster))[0]
        expect(snapshot.trustedComplete).toBe(true)
        const evidence = buildRetentionEvidence(snapshot, buildInspirationIndex(parseInspirationRecords(fixture.sources.inspiration)), fixture.reconciliation)
        expect(buildFactorScarcityIndex(evidence).accountWide).toBe(true)
        const cli = runParentLabCli("retention", { ...fixture.sources, roster }, ["--json"])
        expect(cli.status).toBe(0)
        const report = JSON.parse(cli.stdout)
        expect(report.scarcity.accountWide).toBe(true)
        expect(forFingerprint(report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
    }, 30_000)

    function observedCapture(fixture: ReturnType<typeof build>, inspiration: string) {
        const parsed = parseInspirationRecords(inspiration)
        const evidence = buildRetentionEvidence(fixture.snapshot, buildInspirationIndex(parsed), fixture.reconciliation)
        const subject = evidence.veterans.find((entry) => entry.rosterFingerprint === testFingerprint("fp-subject"))!
        const result = runParentLabCli("retention", { ...fixture.sources, inspiration }, ["--json"])
        expect(result.error).toBeUndefined()
        expect(result.status).toBe(0)
        const report: RetentionShadowReport = JSON.parse(result.stdout)
        return { parsed, evidence, subject, report }
    }

    it.each<[string, (record: Record<string, any>) => void]>([
        ["self right without left", (record) => { record.selfFactors[0].column = "right" }],
        ["self row outside contiguous indexes", (record) => { record.selfFactors[0].rowIndex = 99 }],
        ["duplicate ancestor index", (record) => { record.legacyAncestors[1].ancestorIndex = 0 }],
        ["ancestor index outside contiguous indexes", (record) => { record.legacyAncestors[1].ancestorIndex = 9 }],
        ["empty serialized ancestor", (record) => {
            const block = record.legacyAncestors[0]
            record.diagnostics.rowsAccepted -= block.factors.length
            block.factors = []
            block.factorCount = 0
            block.factorSetTrusted = false
            block.ancestorFactorFingerprint = null
            block.ancestorStructuralFingerprint = ""
        }],
        ["ancestor right without left", (record) => { record.legacyAncestors[1].factors[0].column = "right" }],
        ["ancestor row outside contiguous indexes", (record) => { record.legacyAncestors[0].factors[0].rowIndex = 99 }],
        ...[false, true].flatMap((ancestor) => [-1, 4].map((stars): [string, (record: Record<string, any>) => void] =>
            [`${ancestor ? "ancestor" : "self"} stars ${stars}`, (record) => setStars(record, ancestor, stars)])),
    ])("rejects producer-impossible Inspiration %s through consumers and CLI", (_, mutate) => {
        const fixture = build(unlockedTrio())
        const control = mutateSubjectCapture(fixture.sources.inspiration, (record) => ancestorBlocks(record))
        const positive = observedCapture(fixture, control)
        expect(positive.subject.captureTrusted).toBe(true)
        expect(positive.subject.selfFactors).not.toBeNull()
        expect(positive.report.scarcity.accountWide).toBe(true)
        expect(forFingerprint(positive.report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")

        const actual = observedCapture(fixture, mutateSubjectCapture(control, mutate))
        const rawSubject = actual.parsed.entries.find((entry) => entry.scanIndex === 0)!
        expect(rawSubject.diagnostics).not.toBeNull()
        expect(rawSubject.legacyAncestors).toHaveLength(2)
        expect(actual.subject.capture).not.toBeNull()
        expect({
            captureTrusted: actual.subject.captureTrusted, factors: actual.subject.selfFactors,
            consumerAccountWide: buildFactorScarcityIndex(actual.evidence).accountWide,
            cliAccountWide: actual.report.scarcity.accountWide, safe: actual.report.counts.SAFE_TO_TRANSFER,
        }).toEqual({ captureTrusted: false, factors: null, consumerAccountWide: false, cliAccountWide: false, safe: 0 })
        expect(actual.subject.capture?.unresolvedFields).toContain("persistedCompleteness")
    }, 30_000)

    it.each<[string, unknown]>([
        ["missing", undefined], ["below original count", 1], ["null", null], ["zero", 0], ["negative", -1],
        ["fractional", 3.5], ["numeric string", "260"], ["boolean", true], ["array", [260]], ["object", {}],
        ["outside producer integer range", 2_147_483_648], ["unsafe integer", Number.MAX_SAFE_INTEGER + 1],
    ])("rejects producer-impossible Inspiration capacity %s without header fallback", (_, capacity) => {
        const fixture = build(unlockedTrio())
        const positive = observedCapture(fixture, fixture.sources.inspiration)
        expect(positive.evidence.veterans.every((entry) => entry.captureTrusted)).toBe(true)
        expect(positive.report.scarcity.accountWide).toBe(true)
        expect(forFingerprint(positive.report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const header = JSON.parse(fixture.sources.inspiration.split("\n")[0])
        header.registeredCapacity = capacity
        const invalidHeader = JSON.stringify(header)
        const captures = fixture.sources.inspiration.split("\n").slice(1).join("\n")
        for (const inspiration of [`${invalidHeader}\n${captures}`, `${fixture.sources.inspiration}\n${invalidHeader}`]) {
            const actual = observedCapture(fixture, inspiration)
            expect(actual.parsed.scans.length).toBeGreaterThan(0)
            expect({
                trusted: actual.evidence.veterans.filter((entry) => entry.captureTrusted).length,
                factors: actual.evidence.veterans.filter((entry) => entry.selfFactors !== null).length,
                consumerAccountWide: buildFactorScarcityIndex(actual.evidence).accountWide,
                cliAccountWide: actual.report.scarcity.accountWide, safe: actual.report.counts.SAFE_TO_TRANSFER,
            }).toEqual({ trusted: 0, factors: 0, consumerAccountWide: false, cliAccountWide: false, safe: 0 })
            expect(actual.evidence.veterans.every((entry) => entry.capture !== null && !entry.capture.snapshotCompatible)).toBe(true)
        }
    }, 30_000)

    it.each([0, 1, 2, 3])("preserves %i ancestors, row permutations, and star boundaries", (count) => {
        const fixture = build(unlockedTrio())
        for (const stars of [0, 3]) {
            const inspiration = mutateSubjectCapture(fixture.sources.inspiration, (record) => {
                record.selfFactors.forEach((entry: Record<string, any>, index: number) => {
                    entry.rowIndex = Math.floor(index / 2)
                    entry.column = index % 2 === 0 ? "left" : "right"
                })
                ancestorBlocks(record, count)
                setStars(record, false, stars)
                if (count > 1) setStars(record, true, stars)
                record.selfFactors.reverse()
                record.legacyAncestors.reverse().forEach((block: Record<string, any>) => block.factors.reverse())
            })
            const actual = observedCapture(fixture, inspiration)
            expect(actual.subject.captureTrusted).toBe(true)
            expect(actual.subject.selfFactors).toHaveLength(GENERIC_SUBJECT.length)
            expect(actual.report.scarcity.accountWide).toBe(true)
        }
    }, 30_000)

    it.each([3, 100, 260, 2_147_483_647])("preserves original scan capacity %i independently of current roster capacity", (capacity) => {
        const fixture = build(unlockedTrio())
        const lines = fixture.sources.inspiration.split("\n")
        lines[0] = JSON.stringify({ ...JSON.parse(lines[0]), registeredCapacity: capacity })
        const actual = observedCapture(fixture, lines.join("\n"))
        expect(actual.evidence.veterans.every((entry) => entry.captureTrusted)).toBe(true)
        expect(actual.report.scarcity.accountWide).toBe(true)
        expect(forFingerprint(actual.report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
    }, 30_000)

    it.each([
        ["extra claimed self block", false, (record: Record<string, any>) => { record.diagnostics.blocksObserved = 2 }],
        ["one row for four self factors", false, (record: Record<string, any>) => { record.diagnostics.rowsAccepted = 1 }],
        ["duplicate self cell", false, (record: Record<string, any>) => { record.selfFactors[1].rowIndex = record.selfFactors[0].rowIndex }],
        ["extra claimed ancestor block", true, (record: Record<string, any>) => { record.diagnostics.blocksObserved++ }],
        ["omitted ancestor block census", true, (record: Record<string, any>) => { record.diagnostics.blocksObserved-- }],
        ["omitted ancestor row census", true, (record: Record<string, any>) => { record.diagnostics.rowsAccepted-- }],
        ["extra claimed row", true, (record: Record<string, any>) => { record.diagnostics.rowsAccepted++ }],
        ["factor count used as row census", true, (record: Record<string, any>) => {
            record.diagnostics.rowsAccepted = record.selfFactors.length + record.legacyAncestors.reduce((count: number, block: Record<string, any>) => count + block.factors.length, 0)
        }],
        ["duplicate self left cell in two-column row", true, (record: Record<string, any>) => { record.selfFactors[1].column = "left" }],
        ["duplicate self right cell in two-column row", true, (record: Record<string, any>) => { record.selfFactors[0].column = "right" }],
        ["duplicate ancestor left cell", true, (record: Record<string, any>) => { record.legacyAncestors[0].factors[1].column = "left" }],
        ["duplicate ancestor right cell", true, (record: Record<string, any>) => { record.legacyAncestors[1].factors[0].column = "right" }],
    ])("rejects Inspiration census or cell contradiction: %s", (_, withAncestors, mutate) => {
        const fixture = build(unlockedTrio())
        const control = mutateSubjectCapture(fixture.sources.inspiration, (record) => {
            if (!withAncestors) return
            record.selfFactors.forEach((entry: Record<string, any>, index: number) => {
                entry.rowIndex = Math.floor(index / 2)
                entry.column = index % 2 === 0 ? "left" : "right"
            })
            record.legacyAncestors = [0, 1].map((ancestorIndex) => ({
                ancestorIndex, portraitObserved: true, factorCount: record.selfFactors.length,
                ancestorFactorFingerprint: record.selfFactorFingerprint,
                ancestorStructuralFingerprint: record.selfStructuralFingerprint,
                factorSetTrusted: true, factors: record.selfFactors.map((entry: Record<string, any>) => ({ ...entry })),
            }))
            const blocks = [record.selfFactors, ...record.legacyAncestors.map((block: Record<string, any>) => block.factors)]
            record.diagnostics.blocksObserved = blocks.length
            record.diagnostics.rowsAccepted = blocks.reduce((count, block) => count + new Set(block.map((entry: Record<string, any>) => entry.rowIndex)).size, 0)
        })
        const reordered = mutateSubjectCapture(control, (record) => {
            record.selfFactors.reverse()
            record.legacyAncestors.reverse().forEach((block: Record<string, any>) => block.factors.reverse())
        })
        for (const inspiration of [control, reordered]) {
            const evidence = buildRetentionEvidence(fixture.snapshot, buildInspirationIndex(parseInspirationRecords(inspiration)), fixture.reconciliation)
            expect(evidence.veterans.find((entry) => entry.rosterFingerprint === testFingerprint("fp-subject"))?.captureTrusted).toBe(true)
            expect(buildFactorScarcityIndex(evidence).accountWide).toBe(true)
            const result = runParentLabCli("retention", { ...fixture.sources, inspiration }, ["--json"])
            expect(result.error).toBeUndefined()
            expect(result.status).toBe(0)
            expect(forFingerprint(JSON.parse(result.stdout), "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        }

        const inspiration = mutateSubjectCapture(control, mutate)
        const parsed = parseInspirationRecords(inspiration)
        const rawSubject = parsed.entries.find((entry) => entry.scanIndex === 0)!
        const controlSubject = parseInspirationRecords(control).entries.find((entry) => entry.scanIndex === 0)!
        expect(rawSubject.selfFactors).toHaveLength(controlSubject.selfFactors.length)
        expect(rawSubject.legacyAncestors).toHaveLength(controlSubject.legacyAncestors.length)
        expect(rawSubject.diagnostics).not.toBeNull()
        const evidence = buildRetentionEvidence(fixture.snapshot, buildInspirationIndex(parsed), fixture.reconciliation)
        const subject = evidence.veterans.find((entry) => entry.rosterFingerprint === testFingerprint("fp-subject"))!

        const result = runParentLabCli("retention", { ...fixture.sources, inspiration }, ["--json"])
        expect(result.error).toBeUndefined()
        expect(result.status).toBe(0)
        const report: RetentionShadowReport = JSON.parse(result.stdout)
        expect({
            completenessConsistent: rawSubject.sparkCaptureConsistent,
            captureTrusted: subject.captureTrusted,
            factors: subject.selfFactors,
            consumerAccountWide: buildFactorScarcityIndex(evidence).accountWide,
            cliAccountWide: report.scarcity.accountWide,
            cliCapturedTrusted: report.scarcity.capturedTrusted,
            cliSafeCount: report.counts.SAFE_TO_TRANSFER,
        }).toEqual({
            completenessConsistent: false, captureTrusted: false, factors: null,
            consumerAccountWide: false, cliAccountWide: false, cliCapturedTrusted: 2, cliSafeCount: 0,
        })
        expect(forFingerprint(report, "fp-subject").state).not.toBe("SAFE_TO_TRANSFER")
    }, 30_000)

    it.each([
        ["zero completeness score", (record: Record<string, any>) => { record.screenReadCompleteness = 0 }],
        ["zero rows and blocks", (record: Record<string, any>) => { record.diagnostics.rowsAccepted = 0; record.diagnostics.blocksObserved = 0 }],
        ["zero rows", (record: Record<string, any>) => { record.diagnostics.rowsAccepted = 0 }],
        ["zero blocks", (record: Record<string, any>) => { record.diagnostics.blocksObserved = 0 }],
    ])("rejects contradictory Inspiration %s in retention evidence and the CLI", (_, mutate) => {
        const fixture = build(unlockedTrio())
        expect(forFingerprint(fixture.report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const inspiration = mutateSubjectCapture(fixture.sources.inspiration, mutate)
        const evidence = buildRetentionEvidence(fixture.snapshot, buildInspirationIndex(parseInspirationRecords(inspiration)), fixture.reconciliation)
        const subject = evidence.veterans.find((entry) => entry.rosterFingerprint === testFingerprint("fp-subject"))!
        expect(subject.captureTrusted).toBe(false)
        expect(subject.selfFactors).toBeNull()
        expect(buildFactorScarcityIndex(evidence).accountWide).toBe(false)

        const result = runParentLabCli("retention", { ...fixture.sources, inspiration }, ["--json"])
        expect(result.error).toBeUndefined()
        expect(result.status).toBe(0)
        const report: RetentionShadowReport = JSON.parse(result.stdout)
        expect(report.scarcity.accountWide).toBe(false)
        expect(report.scarcity.capturedTrusted).toBe(2)
        expect(report.counts.SAFE_TO_TRANSFER).toBe(0)
        expect(forFingerprint(report, "fp-subject").state).not.toBe("SAFE_TO_TRANSFER")
    }, 30_000)

    it.each([
        ["bound capacity mismatch", (record: Record<string, any>) => { record.registeredCapacity = 261 }],
        ["string enumeration flag", (record: Record<string, any>) => { record.enumerationPerformed = "true" }],
        ["missing enumeration flag", (record: Record<string, any>) => { delete record.enumerationPerformed }],
    ])("rejects contradictory protection %s without older-evidence fallback", (_, mutate) => {
        const fixture = build(unlockedTrio())
        expect(forFingerprint(fixture.report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const record = JSON.parse(fixture.sources.protection!)
        mutate(record)
        const protection = `${fixture.sources.protection}\n${JSON.stringify(record)}`
        const inventory = buildProtectionInventory(latestProtectionRecord(parseProtectionRecords(protection)), fixture.snapshot)
        expect(inventory.compatible).toBe(false)
        expect(inventory.counts.notProtected).toBe(0)
        expect(inventory.counts.protectionUnknown).toBe(3)

        const result = runParentLabCli("retention", { ...fixture.sources, protection }, ["--json"])
        expect(result.error).toBeUndefined()
        expect(result.status).toBe(0)
        const report: RetentionShadowReport = JSON.parse(result.stdout)
        expect(report.counts.SAFE_TO_TRANSFER).toBe(0)
        expect(forFingerprint(report, "fp-subject").state).not.toBe("SAFE_TO_TRANSFER")
    }, 30_000)

    it("A-10 fails conflicting complete Inspiration captures closed in either record order", () => {
        const options = unlockedTrio()
        const fixture = build(options)
        const conflicting = completeInspirationScan("conflict", [capture("fp-subject", SUBJECT_WITH_UNIQUE), ...options.captures.slice(1)])
        for (const inspiration of [`${fixture.sources.inspiration}\n${conflicting}`, `${conflicting}\n${fixture.sources.inspiration}`]) {
            const evidence = buildRetentionEvidence(fixture.snapshot, buildInspirationIndex(parseInspirationRecords(inspiration)), fixture.reconciliation)
            const subject = evidence.veterans.find((entry) => entry.rosterFingerprint === testFingerprint("fp-subject"))!
            expect(subject.captureTrusted).toBe(false)
            expect(subject.selfFactors).toBeNull()
            expect(buildFactorScarcityIndex(evidence).accountWide).toBe(false)

            const result = runParentLabCli("retention", { ...fixture.sources, inspiration }, ["--json"])
            expect(result.error).toBeUndefined()
            expect(result.status).toBe(0)
            const report: RetentionShadowReport = JSON.parse(result.stdout)
            expect(report.scarcity.accountWide).toBe(false)
            expect(report.scarcity.capturedTrusted).toBe(2)
            expect(report.counts.SAFE_TO_TRANSFER).toBe(0)
            expect(forFingerprint(report, "fp-subject").state).not.toBe("SAFE_TO_TRANSFER")
        }
    }, 30_000)

    it("A-10 preserves rich, weak, and agreeing duplicate controls through the retention CLI", () => {
        const rich = build(unlockedTrio(SUBJECT_WITH_UNIQUE, STRONGER_NON_COVERING))
        const richReport: RetentionShadowReport = JSON.parse(runParentLabCli("retention", rich.sources, ["--json"]).stdout)
        expect(forFingerprint(richReport, "fp-subject").state).toBe("HARD_PROTECT")

        const options = unlockedTrio()
        const weak = build(options)
        const weakResult = runParentLabCli("retention", weak.sources, ["--json"])
        expect(forFingerprint(JSON.parse(weakResult.stdout), "fp-subject").state).toBe("SAFE_TO_TRANSFER")

        const agreeing = `${weak.sources.inspiration}\n${completeInspirationScan("agreeing", options.captures)}`
        const agreeingResult = runParentLabCli("retention", { ...weak.sources, inspiration: agreeing }, ["--json"])
        expect(agreeingResult.error).toBeUndefined()
        const agreeingReport: RetentionShadowReport = JSON.parse(agreeingResult.stdout)
        expect(agreeingReport.scarcity.accountWide).toBe(true)
        expect(forFingerprint(agreeingReport, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
    }, 30_000)

    it.each([
        ["factor row missing kind", (record: Record<string, any>) => { delete record.selfFactors.at(-1).kind }],
        ["empty asserted factor set", (record: Record<string, any>) => { record.selfFactors = [] }],
        ["unresolved canonical factor", (record: Record<string, any>) => {
            delete record.selfFactors.at(-1).canonicalName
            delete record.selfFactors.at(-1).factorFingerprint
            record.selfFactors.at(-1).canonicalPath = "reject"
        }],
        ["startedAtTop contradiction", (record: Record<string, any>) => {
            record.diagnostics.startedAtTop = false
            record.unresolvedFields = ["startedAtTop"]
        }],
        ["content gap contradiction", (record: Record<string, any>) => {
            record.diagnostics.gapFrames = 1
            record.unresolvedFields = ["contentGap"]
        }],
    ])("A-11 rejects %s through parsed evidence and the retention CLI", (_, mutate) => {
        const fixture = build(unlockedTrio(SUBJECT_WITH_UNIQUE, STRONGER_NON_COVERING))
        const inspiration = mutateSubjectCapture(fixture.sources.inspiration, mutate)
        const evidence = buildRetentionEvidence(fixture.snapshot, buildInspirationIndex(parseInspirationRecords(inspiration)), fixture.reconciliation)
        const subject = evidence.veterans.find((entry) => entry.rosterFingerprint === testFingerprint("fp-subject"))!
        expect(subject.captureTrusted).toBe(false)
        expect(subject.selfFactors).toBeNull()
        expect(buildFactorScarcityIndex(evidence).accountWide).toBe(false)

        const result = runParentLabCli("retention", { ...fixture.sources, inspiration }, ["--json"])
        expect(result.error).toBeUndefined()
        expect(result.status).toBe(0)
        const report: RetentionShadowReport = JSON.parse(result.stdout)
        expect(report.scarcity.accountWide).toBe(false)
        expect(report.counts.SAFE_TO_TRANSFER).toBe(0)
        expect(forFingerprint(report, "fp-subject").state).not.toBe("SAFE_TO_TRANSFER")
    }, 30_000)

    it("production CLI quarantines a transferable subject when the authoritative tail is malformed", () => {
        const fixture = build(unlockedTrio())
        const valid = runParentLabCli("retention", fixture.sources, ["--json"])
        expect(valid.error).toBeUndefined()
        expect(valid.status).toBe(0)
        const clean: RetentionShadowReport = JSON.parse(valid.stdout)
        expect(forFingerprint(clean, "fp-subject").state).toBe("SAFE_TO_TRANSFER")

        const badSources = { ...fixture.sources, protection: fixture.sources.protection + '\n{"type":"veteran_protection"' }
        const malformed = runParentLabCli("retention", badSources, ["--json"])
        expect(malformed.error).toBeUndefined()
        expect(malformed.status).toBe(0)
        const rejected: RetentionShadowReport = JSON.parse(malformed.stdout)
        expect(rejected.counts.SAFE_TO_TRANSFER).toBe(0)
        const subject = forFingerprint(rejected, "fp-subject")
        expect(subject.state).toBe("QUARANTINE_TRANSFER")
        expect(subject.gateReasons).toEqual(expect.arrayContaining(["FAVORITE_STATE_UNKNOWN", "PROTECTION_STATE_UNKNOWN"]))
        const text = runParentLabCli("retention", badSources)
        expect(text.status).toBe(0)
        expect(text.stdout).toContain("QUARANTINE_TRANSFER")
        expect(text.stdout).toContain("FAVORITE_STATE_UNKNOWN")
        expect(text.stdout).toContain("PROTECTION_STATE_UNKNOWN")
    }, 30_000)

    it("production CLI rejects contradictory protection capacity for a transferable subject", () => {
        const fixture = build(unlockedTrio())
        const valid = runParentLabCli("retention", fixture.sources, ["--json"])
        expect(valid.status).toBe(0)
        expect(forFingerprint(JSON.parse(valid.stdout), "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const badSources = { ...fixture.sources, protection: JSON.stringify({ ...JSON.parse(fixture.sources.protection), registeredCapacity: 1 }) }
        const rejected = runParentLabCli("retention", badSources, ["--json"])
        expect(rejected.error).toBeUndefined()
        expect(rejected.status).toBe(0)
        const report: RetentionShadowReport = JSON.parse(rejected.stdout)
        expect(report.counts.SAFE_TO_TRANSFER).toBe(0)
        const subject = forFingerprint(report, "fp-subject")
        expect(subject.state).not.toBe("SAFE_TO_TRANSFER")
        expect(subject.gateReasons).toEqual(expect.arrayContaining(["FAVORITE_STATE_UNKNOWN", "PROTECTION_STATE_UNKNOWN"]))
    }, 30_000)

    it.each([["character", "King Halo"], ["outfit", "Other Outfit"], ["rank", "A"]])("production CLI rejects contradictory Inspiration %s for a transferable subject", (field, value) => {
        const fixture = build(unlockedTrio())
        const valid = runParentLabCli("retention", fixture.sources, ["--json"])
        expect(valid.status).toBe(0)
        expect(forFingerprint(JSON.parse(valid.stdout), "fp-subject").state).toBe("SAFE_TO_TRANSFER")
        const inspiration = fixture.sources.inspiration.split("\n").map((line) => {
            const record = JSON.parse(line)
            if (record.type === "veteran_inspiration" && record.scanIndex === 0) record[field] = value
            return JSON.stringify(record)
        }).join("\n")
        const evidence = buildRetentionEvidence(fixture.snapshot, buildInspirationIndex(parseInspirationRecords(inspiration)), fixture.reconciliation)
        expect(evidence.veterans[0].captureTrusted).toBe(false)
        expect(evidence.veterans[0].selfFactors).toBeNull()
        const result = runParentLabCli("retention", { ...fixture.sources, inspiration }, ["--json"])
        expect(result.error).toBeUndefined()
        expect(result.status).toBe(0)
        const report: RetentionShadowReport = JSON.parse(result.stdout)
        expect(report.scarcity.accountWide).toBe(false)
        expect(report.scarcity.capturedTrusted).toBe(2)
        expect(report.counts.SAFE_TO_TRANSFER).toBe(0)
        expect(forFingerprint(report, "fp-subject").state).not.toBe("SAFE_TO_TRANSFER")
    }, 30_000)

    it("never trusts an incompatible-only Inspiration batch or makes an account-wide claim from it", () => {
        const result = build({ ...unlockedTrio(), captureCompatibility: false })
        expect(result.evidence.veterans.every((v) => v.capture !== null && !v.captureTrusted && v.selfFactors === null)).toBe(true)
        expect(buildFactorScarcityIndex(result.evidence).accountWide).toBe(false)
        expect(result.report.counts.SAFE_TO_TRANSFER).toBe(0)
        expect(result.report.counts.QUARANTINE_TRANSFER).toBe(0)
        expect([...buildAdvisorSnapshot([result.report]).candidates.values()].every((c) => !c.eligible)).toBe(true)
    })

    it("keeps captures visible but untrusted when no batch proves a full cycle", () => {
        const fixture = unlockedTrio()
        const badHeader = JSON.stringify({ type: "veteran_inspiration_scan", schemaVersion: 2, scanId: "bad", snapshotCompatibility: false })
        const result = build({ ...fixture, captures: fixture.captures.slice(0, 2), extraInspirationLines: [badHeader, capture("fp-third", GENERIC_DOMINATOR, { scanId: "bad" }), capture("fp-subject", GENERIC_SUBJECT, { scanId: "bad", observedAt: T + 10000 })] })
        expect(result.evidence.veterans.map((v) => v.captureTrusted)).toEqual([false, false, false])
        expect(result.evidence.veterans[0].capture?.scanId).toBe("bad")
        const scarcity = buildFactorScarcityIndex(result.evidence)
        expect(scarcity.capturedTrusted).toBe(0)
        expect(scarcity.capturedUntrusted).toBe(3)
        expect(scarcity.accountWide).toBe(false)
        expect(build(unlockedTrio()).report.counts.SAFE_TO_TRANSFER).toBeGreaterThan(0)
    })

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
        const subject = forFingerprint(build({ ...gated, protection: false }).report, "fp-subject")
        expect(subject.gateReasons).toEqual(expect.arrayContaining(["PROTECTION_STATE_UNKNOWN", "FAVORITE_STATE_UNKNOWN"]))
        expect(subject.state).toBe("QUARANTINE_TRANSFER")
    })

    it("does not revive older empty protection evidence after a malformed final log entry", () => {
        const result = build({ ...unlockedTrio(), protectionTail: ['{"type":"veteran_protection"'] })
        const subject = forFingerprint(result.report, "fp-subject")
        expect(result.evidence.protectionInventory).toBeNull()
        expect(subject.gateReasons).toEqual(expect.arrayContaining(["PROTECTION_STATE_UNKNOWN", "FAVORITE_STATE_UNKNOWN"]))
        expect(subject.state).not.toBe("SAFE_TO_TRANSFER")
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

describe("PL-R2 replacement-evidence provenance (introduced in schema v3)", () => {
    const threeEntries = [
        rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-0" }),
        rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-1" }),
        rosterEntry({ scanIndex: 2, rosterFingerprint: "fp-2" }),
    ]

    /** Builds a report over `entries` with an explicit library (null = no corpus supplied). Reconcile
     * still needs a library object, so a null library reconciles against an empty one - which is what
     * "no careers" means - while the advisor itself receives the null. */
    function reportWithLibrary(entries: readonly string[], library: ReturnType<typeof buildVeteranLibrary> | null): RetentionShadowReport {
        const parsedRoster = parseRosterScanRecords([rosterHeader(entries.length), ...entries].join("\n"), "roster_scan.jsonl")
        const snapshot = buildRosterSnapshots(parsedRoster)[0]
        const reconciliation = reconcileRoster(library ?? buildVeteranLibrary({ outcomes: [], sparks: [] }), snapshot)
        const evidence = buildRetentionEvidence(snapshot, new Map(), reconciliation)
        return buildRetentionShadowReport({ evidence, library, reconciliation, profile: TARGET_PROFILES.GENERAL_INHERITANCE })
    }

    /** A completed career carrying an explicit producer version and observation timestamp. */
    function careerWith(trainee: string, stats: { spd: number; sta: number; pwr: number; grt: number; wit: number }, fans: number, extra: { app?: string; ts: number }): string[] {
        return [
            JSON.stringify({ result: "BREAKPOINT_REACHED", outcome: "COMPLETED", trainee, scenario: "URA_Finale", turn: 75, ts: extra.ts, app: extra.app, fans, ...stats, skillPts: 30 }),
            JSON.stringify({ type: "sparks", phase: "kept", ts: extra.ts, rows: [{ name: "Speed", stars: 1, kind: "stat" }] }),
        ]
    }

    it("A. no career corpus: schema v4, replacementEvidence null, truthful basis, state unchanged vs empty library", () => {
        const nullReport = reportWithLibrary(threeEntries, null)
        expect(PARENTLAB_RETENTION_SCHEMA_VERSION).toBe(4)
        expect(nullReport.schemaVersion).toBe(4)
        expect(nullReport.replacementEvidence).toBeNull()
        for (const r of nullReport.recommendations) {
            expect(r.replacement.basis).toBe("no historical library supplied")
            expect(r.gateReasons).toContain("REPLACEMENT_DIFFICULTY_UNKNOWN")
        }
        // The truthfulness switch (null library vs an empty *built* library) must not move any state or
        // count: only the per-entry basis text and the provenance field itself are allowed to differ.
        const emptyReport = reportWithLibrary(threeEntries, buildVeteranLibrary({ outcomes: [], sparks: [] }))
        expect(emptyReport.counts).toEqual(nullReport.counts)
        for (const r of nullReport.recommendations) {
            const e = emptyReport.recommendations.find((x) => x.rosterFingerprint === r.rosterFingerprint)
            expect(e?.state).toBe(r.state)
        }
        // An empty *built* library is the bound-but-empty case, so its provenance is present, not null.
        expect(emptyReport.replacementEvidence).not.toBeNull()
        expect(emptyReport.replacementEvidence?.confirmedVeterans).toBe(0)
    })

    it("B. careers bound: provenance mirrors library diagnostics; versions distinct + sorted; ts is the max; order-independent", () => {
        // Versions supplied out of order and with a duplicate; timestamps deliberately out of order.
        const careers = [
            careerWith("Taiki_Shuttle", { spd: 900, sta: 700, pwr: 650, grt: 600, wit: 500 }, 1, { app: "1.4.0", ts: T - 5000 }),
            careerWith("Taiki_Shuttle", { spd: 890, sta: 690, pwr: 640, grt: 590, wit: 490 }, 2, { app: "1.3.7", ts: T - 1000 }),
            careerWith("Sakura_Bakushin_O", { spd: 880, sta: 680, pwr: 630, grt: 580, wit: 480 }, 3, { app: "1.3.7", ts: T - 9000 }),
            careerWith("Mejiro_McQueen", { spd: 870, sta: 670, pwr: 620, grt: 570, wit: 470 }, 4, { app: "1.3.8", ts: T - 200 }),
        ]
        const corpus = parseCorpus(careers.flat().join("\n"), "careers.jsonl")
        const library = buildVeteranLibrary({ outcomes: corpus.outcomes, sparks: corpus.sparks })
        expect(library.diagnostics.appVersions).toEqual(["1.3.7", "1.3.8", "1.4.0"])
        expect(library.diagnostics.newestObservationTs).toBe(T - 200)
        expect(library.diagnostics.identityCollisions).toBe(0)

        // Provenance is order-independent, exactly like the rest of the library.
        const reversed = buildVeteranLibrary({ outcomes: [...corpus.outcomes].reverse(), sparks: [...corpus.sparks].reverse() })
        expect(reversed.diagnostics.appVersions).toEqual(library.diagnostics.appVersions)
        expect(reversed.diagnostics.newestObservationTs).toBe(library.diagnostics.newestObservationTs)

        const report = reportWithLibrary(threeEntries, library)
        expect(report.replacementEvidence).toEqual({
            confirmedVeterans: library.diagnostics.confirmedVeterans,
            traineeCount: library.diagnostics.traineeCount,
            identityCollisions: library.diagnostics.identityCollisions,
            appVersions: ["1.3.7", "1.3.8", "1.4.0"],
            newestObservationTs: T - 200,
        })
    })

    it("C. bound-but-empty: non-null provenance with confirmedVeterans 0, still describing the parsed outcomes", () => {
        // Outcome records with declared app/ts but NO kept spark set: parsed, but no confirmed Veteran.
        const outcomeOnly = [
            JSON.stringify({ result: "UNHANDLED_EXCEPTION", outcome: "INCOMPLETE", trainee: "Taiki_Shuttle", scenario: "URA_Finale", turn: 40, ts: T - 3000, app: "9.9.9", fans: 1, spd: 1, sta: 1, pwr: 1, grt: 1, wit: 1, skillPts: 0 }),
            JSON.stringify({ result: "UNHANDLED_EXCEPTION", outcome: "INCOMPLETE", trainee: "Taiki_Shuttle", scenario: "URA_Finale", turn: 41, ts: T - 100, app: "9.9.8", fans: 2, spd: 2, sta: 2, pwr: 2, grt: 2, wit: 2, skillPts: 0 }),
        ].join("\n")
        const corpus = parseCorpus(outcomeOnly, "careers.jsonl")
        const library = buildVeteranLibrary({ outcomes: corpus.outcomes, sparks: corpus.sparks })
        expect(library.diagnostics.confirmedVeterans).toBe(0)
        expect(library.diagnostics.appVersions).toEqual(["9.9.8", "9.9.9"])
        expect(library.diagnostics.newestObservationTs).toBe(T - 100)

        const report = reportWithLibrary(threeEntries, library)
        expect(report.replacementEvidence).not.toBeNull()
        expect(report.replacementEvidence?.confirmedVeterans).toBe(0)
        expect(report.replacementEvidence?.appVersions).toEqual(["9.9.8", "9.9.9"])
        expect(report.replacementEvidence?.newestObservationTs).toBe(T - 100)
    })

    it("C2. truly empty corpus: non-null provenance, empty versions, null timestamp, never collapses to null", () => {
        const library = buildVeteranLibrary({ outcomes: [], sparks: [] })
        const report = reportWithLibrary(threeEntries, library)
        expect(report.replacementEvidence).not.toBeNull()
        expect(report.replacementEvidence?.confirmedVeterans).toBe(0)
        expect(report.replacementEvidence?.appVersions).toEqual([])
        expect(report.replacementEvidence?.newestObservationTs).toBeNull()
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

describe("PL-R2 roster identity-evidence consistency", () => {
    // The device writes a fingerprint only when every identity feeder read cleanly. A fingerprinted
    // entry that also names one unread contradicts its own writer, and reading it as identified is how
    // an aptitude nobody ever read ends up backing a transfer verdict.

    const UNREAD_LONG = { aptitudes: { ...APTITUDES, long: "A" }, unresolvedFields: ["aptitude_long"] }

    /** The one fixture where every strict gate is satisfiable, so the transfer side is reachable. */
    function unlockedTrio(overrides: Record<string, unknown> = {}) {
        return { ...fullyCoveredTrio(GENERIC_SUBJECT, GENERIC_DOMINATOR, { favoriteState: "not_set", protectionState: "not_protected", ...overrides }), protection: true }
    }

    /** Two Veterans clearing the LONG gate, the first of them open to an identity override. */
    function longCoverage(overrides: Record<string, unknown> = {}) {
        return {
            entries: [
                rosterEntry({ scanIndex: 0, rosterFingerprint: "fp-a", aptitudes: { ...APTITUDES, long: "A" }, favoriteState: "not_set", protectionState: "not_protected", ...overrides }),
                rosterEntry({ scanIndex: 1, rosterFingerprint: "fp-b", aptitudes: { ...APTITUDES, long: "A" } }),
                rosterEntry({ scanIndex: 2, rosterFingerprint: "fp-c", aptitudes: { ...APTITUDES, long: "B" } }),
            ],
            captures: [capture("fp-a", GENERIC_SUBJECT), capture("fp-b", GENERIC_DOMINATOR), capture("fp-c", GENERIC_DOMINATOR)],
            protection: true,
            profile: "LONG_PARENT" as const,
        }
    }

    it("still reaches SAFE_TO_TRANSFER while every identity field reads cleanly", () => {
        const { snapshot, report } = build(unlockedTrio())
        expect(snapshot.trustedComplete).toBe(true)
        expect(forFingerprint(report, "fp-subject").state).toBe("SAFE_TO_TRANSFER")
    })

    it("withdraws every transfer verdict when a fingerprinted entry's aptitude was never read", () => {
        const { snapshot, report } = build(unlockedTrio(UNREAD_LONG))
        expect(snapshot.trustedComplete).toBe(false)
        expect(snapshot.defects).toContain("unidentified_entries")
        expect(report.counts.SAFE_TO_TRANSFER).toBe(0)
        expect(report.recommendations.every((r) => r.state === "UNKNOWN")).toBe(true)
        expect(report.recommendations.every((r) => r.confidence === "INSUFFICIENT")).toBe(true)
        expect(report.recommendations.every((r) => r.gateReasons.includes("ROSTER_SNAPSHOT_UNTRUSTED"))).toBe(true)
    })

    it("blocks every quarantine candidate on the same untrusted snapshot", () => {
        const advisor = buildAdvisorSnapshot([build(unlockedTrio(UNREAD_LONG)).report])
        expect(advisor.rosterTrusted).toBe(false)
        expect(advisor.defects).toContain("ROSTER_SNAPSHOT_UNTRUSTED")
        const candidates = [...advisor.candidates.values()]
        expect(candidates.length).toBeGreaterThan(0)
        expect(candidates.every((c) => c.blockers.includes("ROSTER_SNAPSHOT_UNTRUSTED"))).toBe(true)
        expect(candidates.some((c) => c.eligible)).toBe(false)
    })

    it("builds a usable target-coverage ledger while both LONG clearers read cleanly", () => {
        const doc = buildCapacityCoverage(build(longCoverage()).report)
        expect(doc.usable).toBe(true)
        expect(doc.targetSlots.length).toBeGreaterThan(0)
        expect(doc.exposures.length).toBeGreaterThan(0)
    })

    it("empties the target-coverage ledger when a LONG clearer never read its aptitude", () => {
        // Both entries read "long: A", but one of them says it never read that grade - so the account
        // has no proven count of LONG clearers and the ledger must not render a sole or shared claim.
        const doc = buildCapacityCoverage(build(longCoverage(UNREAD_LONG)).report)
        expect(doc.usable).toBe(false)
        expect(doc.poolSize).toBe(0)
        expect(doc.targetSlots).toEqual([])
        expect(doc.exposures).toEqual([])
    })
})
