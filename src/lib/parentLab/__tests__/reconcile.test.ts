import { parseCorpus } from "../../outcomeAnalysis.ts"
import { buildVeteranLibrary } from "../buildVeteranLibrary.ts"
import { reconcileRoster } from "../reconcile.ts"
import { buildRosterSnapshots, parseRosterScanRecords, type RosterSnapshot } from "../roster.ts"
import type { VeteranCorpusInput } from "../types.ts"

// Both sides are built through the real parse paths: careers as JSONL through parseCorpus into
// buildVeteranLibrary, roster rows as JSONL through parseRosterScanRecords into a snapshot. Nothing
// is hand-constructed, so a change to either identity path shows up here.

const STATS = { spd: 949, sta: 699, pwr: 648, grt: 687, wit: 420 }

/** A completed career, with a kept spark set so the builder confirms it as a Veteran. */
function career(o: Record<string, unknown> = {}): string[] {
    return [
        JSON.stringify({
            result: "BREAKPOINT_REACHED",
            outcome: "COMPLETED",
            trainee: "Taiki_Shuttle",
            scenario: "URA_Finale",
            turn: 75,
            ts: Date.UTC(2026, 7, 10, 12, 0, 0),
            fans: 191730,
            ...STATS,
            skillPts: 34,
            ...o,
        }),
        JSON.stringify({
            type: "sparks",
            phase: "kept",
            ts: (o.ts as number | undefined) ?? Date.UTC(2026, 7, 10, 12, 0, 0),
            rows: [
                { name: "Speed", stars: 3, kind: "stat" },
                { name: "Mile", stars: 2, kind: "aptitude" },
                { name: `Unique ${o.fans ?? 191730}`, stars: 1, kind: "unique" },
            ],
        }),
    ]
}

function library(...lines: string[][]) {
    const parsed = parseCorpus(lines.flat().join("\n"), "careers.jsonl")
    return buildVeteranLibrary({ outcomes: parsed.outcomes, sparks: parsed.sparks } satisfies VeteranCorpusInput)
}

function entryLine(scanIndex: number, o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "roster_entry",
        schemaVersion: 1,
        scanId: "scan-a",
        scanIndex,
        observedAt: 1_700_000_000_000 + scanIndex,
        character: "Taiki Shuttle",
        outfit: "Wild Frontier",
        rank: "A",
        rating: 10192,
        stats: STATS,
        statGrades: { spd: "A+", sta: "B", pwr: "B", grt: "B", wit: "C" },
        aptitudes: { turf: "A", dirt: "B", sprint: "A", mile: "A", medium: "E", long: "G", front: "C", pace: "A", late: "E", end: "G" },
        favoriteState: "not_set",
        protectionState: "unknown",
        rosterFingerprint: `fp-${scanIndex}`,
        readCompleteness: 1,
        identityMultiplicity: 1,
        unresolvedFields: [],
        ...o,
    })
}

function headerLine(o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "roster_scan",
        schemaVersion: 1,
        scanId: "scan-a",
        startedAt: 1_700_000_000_000,
        completedAt: 1_700_000_100_000,
        displayedRegisteredUsed: 1,
        displayedRegisteredCapacity: 260,
        filtersOff: true,
        sortKey: "Rating",
        sortDirection: "Asc",
        entryLimit: 0,
        entriesEnumerated: 1,
        uniqueFingerprints: 1,
        unidentifiedCount: 0,
        duplicateFingerprintCount: 0,
        countDiscrepancy: 0,
        terminationReason: "count_reached",
        completeness: "trusted_complete",
        app: "2.5.9",
        screenWidth: 1080,
        screenHeight: 1920,
        ...o,
    })
}

function snapshot(entries: string[], header: Record<string, unknown> = {}): RosterSnapshot {
    const text = [...entries, headerLine({ entriesEnumerated: entries.length, displayedRegisteredUsed: entries.length, uniqueFingerprints: entries.length, ...header })].join("\n")
    return buildRosterSnapshots(parseRosterScanRecords(text))[0]
}

describe("reconcileRoster tiering", () => {
    it("reports a unique stat-projection match as PROBABLE when nothing corroborating was read", () => {
        const result = reconcileRoster(library(career()), snapshot([entryLine(0)]))
        expect(result.entries[0].status).toBe("PROBABLE_HISTORICAL_MATCH")
        expect(result.entries[0].veteranId).not.toBeNull()
        expect(result.counts).toMatchObject({ probable: 1, exact: 0, rosterOnly: 0, ambiguous: 0, unresolved: 0 })
    })

    it("promotes to EXACT once an independent field was read and agreed", () => {
        const entry = entryLine(0, { careerInfo: { races: 18, wins: 13, fans: 191730, scenario: "The Beginning: URA Finale", rating: 10192, dateAcquired: null } })
        const result = reconcileRoster(library(career()), snapshot([entry]))
        expect(result.entries[0].status).toBe("EXACT_HISTORICAL_MATCH")
        expect(result.entries[0].corroboration).toEqual({ scenario: true, fans: true, dateAcquired: null })
        expect(result.diagnostics.entriesWithCareerInfo).toBe(1)
    })

    it("matches the full in-game scenario title against the corpus short name", () => {
        const entry = entryLine(0, { careerInfo: { races: null, wins: null, fans: null, scenario: "The Beginning: URA Finale", rating: null, dateAcquired: null } })
        expect(reconcileRoster(library(career()), snapshot([entry])).entries[0].corroboration.scenario).toBe(true)
    })

    it("does not let a missing skillPts block a valid visible-evidence match", () => {
        // skillPts is part of PL-3 identity and is never visible on the roster. Its absence must be
        // expected, not treated as a failed comparison.
        const result = reconcileRoster(library(career({ skillPts: 999 })), snapshot([entryLine(0)]))
        expect(result.entries[0].status).toBe("PROBABLE_HISTORICAL_MATCH")
    })

    it("reports AMBIGUOUS when two historical veterans fit equally well", () => {
        const result = reconcileRoster(library(career({ fans: 100 }), career({ fans: 200, ts: Date.UTC(2026, 7, 11, 12, 0, 0) })), snapshot([entryLine(0)]))
        expect(result.entries[0].status).toBe("AMBIGUOUS")
        expect(result.entries[0].candidateVeteranIds).toHaveLength(2)
        expect(result.entries[0].veteranId).toBeNull()
        expect(result.diagnostics.historicalKeyCollisions).toBe(1)
    })

    it("breaks a two-candidate ambiguity with the fans the Career Info pass read", () => {
        const entry = entryLine(0, { careerInfo: { races: null, wins: null, fans: 200, scenario: null, rating: null, dateAcquired: null } })
        const result = reconcileRoster(library(career({ fans: 100 }), career({ fans: 200, ts: Date.UTC(2026, 7, 11, 12, 0, 0) })), snapshot([entry]))
        expect(result.entries[0].status).toBe("EXACT_HISTORICAL_MATCH")
        expect(result.entries[0].contradictedCandidates).toBe(1)
    })

    it("breaks a same-fans ambiguity with the date acquired", () => {
        const entry = entryLine(0, { careerInfo: { races: null, wins: null, fans: 191730, scenario: null, rating: null, dateAcquired: "2026-08-11" } })
        const result = reconcileRoster(library(career({ ts: Date.UTC(2026, 7, 10, 12, 0, 0) }), career({ ts: Date.UTC(2026, 7, 11, 12, 0, 0), skillPts: 120 })), snapshot([entry]))
        expect(result.entries[0].status).toBe("EXACT_HISTORICAL_MATCH")
        expect(result.entries[0].corroboration.dateAcquired).toBe(true)
    })

    it("reports ROSTER_ONLY when no historical record carries the trainee and stats", () => {
        const result = reconcileRoster(library(career({ trainee: "King_Halo" })), snapshot([entryLine(0)]))
        expect(result.entries[0].status).toBe("ROSTER_ONLY")
        expect(result.entries[0].reason).toContain("no historical veteran")
        expect(result.counts.rosterOnly).toBe(1)
    })

    it("reports ROSTER_ONLY when every stat-key candidate is contradicted by career info", () => {
        const entry = entryLine(0, { careerInfo: { races: null, wins: null, fans: 5, scenario: null, rating: null, dateAcquired: null } })
        const result = reconcileRoster(library(career()), snapshot([entry]))
        expect(result.entries[0].status).toBe("ROSTER_ONLY")
        expect(result.entries[0].contradictedCandidates).toBe(1)
    })

    it("reports UNRESOLVED when the entry itself did not read enough to join", () => {
        const noName = entryLine(0, { character: undefined, rosterFingerprint: undefined })
        const noStat = entryLine(1, { stats: { ...STATS, wit: undefined } })
        const result = reconcileRoster(library(career()), snapshot([noName, noStat]))
        expect(result.entries.map((e) => e.status)).toEqual(["UNRESOLVED", "UNRESOLVED"])
        expect(result.diagnostics.joinableEntries).toBe(0)
    })

    it("normalizes the corpus underscore trainee name against the OCR-read roster name", () => {
        expect(reconcileRoster(library(career({ trainee: "Taiki_Shuttle" })), snapshot([entryLine(0)])).entries[0].status).toBe("PROBABLE_HISTORICAL_MATCH")
    })
})

describe("reconcileRoster one-to-one assignment", () => {
    it("refuses to hand one historical veteran to two roster entries", () => {
        const result = reconcileRoster(library(career()), snapshot([entryLine(0), entryLine(1, { rosterFingerprint: "fp-0" })]))
        expect(result.entries.map((e) => e.status)).toEqual(["AMBIGUOUS", "AMBIGUOUS"])
        expect(result.entries.every((e) => e.veteranId === null)).toBe(true)
        expect(result.diagnostics.contestedMatches).toBe(2)
    })

    it("leaves a contested veteran in the not-in-roster list rather than claiming it", () => {
        const result = reconcileRoster(library(career()), snapshot([entryLine(0), entryLine(1, { rosterFingerprint: "fp-0" })]))
        expect(result.historicalNotInRoster).toHaveLength(1)
        expect(result.counts.historicalNotInRoster).toBe(1)
    })
})

describe("reconcileRoster historical absence", () => {
    it("lists a historical veteran no roster entry matched", () => {
        const result = reconcileRoster(library(career(), career({ trainee: "King_Halo", fans: 5000, ts: Date.UTC(2026, 6, 1) })), snapshot([entryLine(0)]))
        expect(result.historicalNotInRoster.map((v) => v.trainee)).toEqual(["King Halo"])
        expect(result.historicalNotInRosterReliable).toBe(true)
    })

    it("never deletes history: the library is unchanged and every veteran is accounted for", () => {
        const lib = library(career(), career({ trainee: "King_Halo", fans: 5000, ts: Date.UTC(2026, 6, 1) }))
        const before = JSON.stringify(lib)
        const result = reconcileRoster(lib, snapshot([entryLine(0)]))
        expect(JSON.stringify(lib)).toBe(before)
        expect(result.counts.exact + result.counts.probable + result.counts.historicalNotInRoster).toBe(lib.veterans.length)
    })

    it("marks the absence list unreliable when the snapshot is not trusted-complete", () => {
        const partial = snapshot([entryLine(0)], { completeness: "incomplete", terminationReason: "entry_limit_reached", displayedRegisteredUsed: 257 })
        const result = reconcileRoster(library(career(), career({ trainee: "King_Halo", fans: 5000, ts: Date.UTC(2026, 6, 1) })), partial)
        expect(result.trustedComplete).toBe(false)
        expect(result.historicalNotInRosterReliable).toBe(false)
        expect(result.historicalNotInRoster.length).toBeGreaterThan(0)
    })
})

describe("reconcileRoster determinism", () => {
    it("produces byte-identical output across rebuilds and input orderings", () => {
        const careers = [career({ fans: 1 }), career({ trainee: "King_Halo", fans: 2, ts: Date.UTC(2026, 6, 1) }), career({ trainee: "Symboli_Rudolf", fans: 3, ts: Date.UTC(2026, 6, 2) })]
        const rows = [entryLine(0), entryLine(1, { character: "King Halo", rosterFingerprint: "fp-1" }), entryLine(2, { character: "Symboli Rudolf", rosterFingerprint: "fp-2" })]
        const first = reconcileRoster(library(...careers), snapshot(rows))
        const reordered = reconcileRoster(library(...[...careers].reverse()), snapshot([rows[2], rows[0], rows[1]]))
        expect(JSON.stringify(reordered)).toBe(JSON.stringify(first))
        expect(first.entries.map((e) => e.scanIndex)).toEqual([0, 1, 2])
    })

    it("sorts candidate ids and the absence list so a rebuild cannot reorder them", () => {
        const result = reconcileRoster(library(career({ fans: 100 }), career({ fans: 200, ts: Date.UTC(2026, 7, 11) })), snapshot([entryLine(0)]))
        const ids = result.entries[0].candidateVeteranIds
        expect([...ids].sort()).toEqual([...ids])
        const trainees = result.historicalNotInRoster.map((v) => v.trainee)
        expect([...trainees].sort()).toEqual([...trainees])
    })
})
