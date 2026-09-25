import { buildVeteranLibrary } from "../buildVeteranLibrary.ts"
import { buildInspirationIndex, detectInspirationConflicts, parseInspirationRecords } from "../inspiration.ts"
import { buildProtectionInventory, latestProtectionRecord, parseProtectionRecords } from "../protection.ts"
import { reconcileRoster } from "../reconcile.ts"
import { buildFactorScarcityIndex, buildRetentionEvidence } from "../retentionEvidence.ts"
import { buildRosterSnapshots, parseRosterScanRecords, rosterBindingDigest } from "../roster.ts"
import { fixtureFingerprint } from "./rosterFixtures.ts"
import { runParentLabCli } from "./cliFixture.ts"

function row(index: number): Record<string, any> {
    const record = {
        type: "roster_entry", schemaVersion: 1, scanId: "roster", scanIndex: index,
        character: "Taiki Shuttle", outfit: "Wild Frontier", rank: "A", rating: 10192 + index,
        stats: { spd: 949, sta: 699, pwr: 648, grt: 687, wit: 420 },
        aptitudes: { turf: "A", dirt: "B", sprint: "A", mile: "A", medium: "E", long: "G", front: "C", pace: "A", late: "E", end: "G" },
        favoriteState: "unknown", protectionState: "unknown", unresolvedFields: [],
    }
    return { ...record, rosterFingerprint: fixtureFingerprint(record) }
}

function header(count = 2): Record<string, any> {
    return {
        type: "roster_scan", schemaVersion: 1, scanId: "roster", displayedRegisteredUsed: count,
        displayedRegisteredCapacity: 260, filtersOff: true, entryLimit: 0, entriesEnumerated: count,
        uniqueFingerprints: count, unidentifiedCount: 0, duplicateFingerprintCount: 0, countDiscrepancy: 0,
        terminationReason: "cycle_closed", completeness: "trusted_complete", enumerationComplete: true, identityComplete: true,
    }
}

const snapshot = (rows = [row(0), row(1)], scan = header(rows.length)) =>
    buildRosterSnapshots(parseRosterScanRecords([...rows, scan].map((record) => JSON.stringify(record)).join("\n")))[0]

const neutral = Object.fromEntries(["track", "distance", "style", "attribute_sparks", "aptitude_sparks", "unique_sparks", "common_sparks", "favorites", "memo"].map((key) => [key, "neutral"]))

function protection() {
    return latestProtectionRecord(parseProtectionRecords(JSON.stringify({
        type: "veteran_protection", schemaVersion: 2, scanId: "protection", rosterBindingVersion: 1,
        rosterScanId: "roster", rosterDigest: rosterBindingDigest(snapshot()), registeredUsed: 2, registeredCapacity: 260,
        filtersOffConfirmed: true, filterBaselineEvidenceVersion: 1,
        favoritePopulation: "empty", favoriteApplyState: "disabled", favoriteBaselineVerified: true, favoriteBaselineReadings: neutral,
        memoPopulation: "empty", memoApplyState: "disabled", memoBaselineVerified: true, memoBaselineReadings: neutral,
        enumerationPerformed: false, favoritedFingerprints: [], memoFingerprints: [], restoredFiltersOff: true, outcome: "complete",
    })))
}

describe("persisted roster trust boundary", () => {
    const library = buildVeteranLibrary({ outcomes: [], sparks: [] })
    const cases: readonly [string, (rows: Record<string, any>[], scan: Record<string, any>) => void][] = [
        ["duplicate indexes", (rows) => { rows[1].scanIndex = 0 }],
        ["unsupported header schema", (_, scan) => { scan.schemaVersion = 99 }],
        ["unsupported row schema", (rows) => { rows[1].schemaVersion = 99 }],
        ["duplicate tuples with different fingerprints", (rows) => { rows[1] = { ...rows[0], scanIndex: 1, rosterFingerprint: rows[1].rosterFingerprint } }],
        ["fingerprint disagrees with fields", (rows) => { rows[1].rosterFingerprint = "a".repeat(32) }],
        ["B+ with rating 6500", (rows) => { rows[1].rank = "B+"; rows[1].rating = 6500; rows[1].rosterFingerprint = fixtureFingerprint(rows[1]) }],
        ["changed identity with stale fingerprint and digest", (rows) => { rows[1].stats = { ...rows[1].stats, sta: 701 } }],
        ["missing required row", (rows) => { rows.pop() }],
        ["missing unboundedness", (_, scan) => { delete scan.entryLimit }],
        ["missing identity field", (rows) => { rows[1].character = null }],
    ]

    it("binds a canonical complete cycle to verified empty protection", () => {
        const clean = snapshot()
        expect(clean.trustedComplete).toBe(true)
        expect(reconcileRoster(library, clean).historicalNotInRosterReliable).toBe(true)
        expect(buildProtectionInventory(protection(), clean).compatible).toBe(true)
    })

    it.each(cases)("rejects %s across reconstruction absence and negative protection", (_, corrupt) => {
        const rows = [row(0), row(1)]
        const scan = header()
        corrupt(rows, scan)
        const actual = snapshot(rows, scan)
        expect(actual.trustedComplete).toBe(false)
        expect(rosterBindingDigest(actual)).toBeNull()
        expect(reconcileRoster(library, actual).historicalNotInRosterReliable).toBe(false)
        const inventory = buildProtectionInventory(protection(), actual)
        expect(inventory.compatible).toBe(false)
        expect([...inventory.byFingerprint.values()].every((entry) => entry.protectionState === "unknown")).toBe(true)
    })

    it("rechecks fields when a caller retains stale snapshot trust", () => {
        const clean = snapshot()
        const changed = { ...clean, entries: clean.entries.map((entry, i) => i ? { ...entry, rating: 10194 } : entry) }
        expect(changed.trustedComplete).toBe(true)
        expect(rosterBindingDigest(changed)).toBeNull()
    })

    it.each([
        ["missing index", { ...row(2), scanIndex: undefined }],
        ["fractional index", { ...row(2), scanIndex: 0.5 }],
        ["string index", { ...row(2), scanIndex: "1" }],
        ["contradictory identity and fingerprint", { ...row(2), scanIndex: undefined, character: "King Halo", rosterFingerprint: row(0).rosterFingerprint }],
        ["later unrecognized incomplete header", { ...header(), terminationReason: "unrecognized", completeness: "incomplete" }],
    ])("preserves scan-attributable malformed %s across trust and protection", (_, extra) => {
        const parsed = parseRosterScanRecords([row(0), row(1), header(), extra].map((record) => JSON.stringify(record)).join("\n"))
        expect(parsed.invalidRecords).toEqual([{ scanId: "roster", type: "terminationReason" in extra ? "roster_scan" : "roster_entry", lineNumber: 3 }])
        const actual = buildRosterSnapshots(parsed)[0]
        expect(actual.defects).toContain("attributable_malformed_record")
        expect(actual.trustedComplete).toBe(false)
        expect(actual.enumerationComplete).toBe(false)
        expect(rosterBindingDigest(actual)).toBeNull()
        expect(reconcileRoster(library, actual).historicalNotInRosterReliable).toBe(false)
        const inventory = buildProtectionInventory(protection(), actual)
        expect(inventory.compatible).toBe(false)
        expect(inventory.counts.notProtected).toBe(0)
        expect(inventory.counts.protectionUnknown).toBe(2)
    })

    it("isolates attributable invalidity from other scans and unrelated malformed text", () => {
        const other = [row(0), row(1), header()].map((record) => ({ ...record, scanId: "other" }))
        const records = [row(0), row(1), header(), ...other,
            { ...row(2), scanId: "other", scanIndex: null },
            { type: "foreign", scanId: "roster" }, { ...row(2), scanId: 123, scanIndex: null }]
        const parsed = parseRosterScanRecords(records.map((record) => JSON.stringify(record)).join("\n") + '\n{"scanId":"roster"')
        const snapshots = buildRosterSnapshots(parsed)
        const clean = snapshots.find((scan) => scan.scanId === "roster")!
        expect(clean.trustedComplete).toBe(true)
        expect(clean.defects).toEqual([])
        expect(reconcileRoster(library, clean).historicalNotInRosterReliable).toBe(true)
        expect(buildProtectionInventory(protection(), clean).compatible).toBe(true)
        expect(snapshots.find((scan) => scan.scanId === "other")!.trustedComplete).toBe(false)
    })

    it.each([
        ["E", 1300, 1799], ["E+", 1800, 2299], ["B", 6500, 8199], ["B+", 8200, 9999],
        ["A", 10000, 12099], ["A+", 12100, 14499], ["S", 14500, 15899], ["S+", 15900, 17499],
    ])("accepts only the approved %s interval endpoints", (rank, low, high) => {
        for (const rating of [low, high]) {
            const entry: Record<string, any> = { ...row(0), rank, rating }
            entry.rosterFingerprint = fixtureFingerprint(entry)
            expect(snapshot([entry]).trustedComplete).toBe(true)
        }
    })

    it.each([1299, 2300, 6499, 17500])("does not substitute a broader ladder at rating %i", (rating) => {
        const entry: Record<string, any> = { ...row(0), rating }
        entry.rosterFingerprint = fixtureFingerprint(entry)
        expect(snapshot([entry]).trustedComplete).toBe(false)
    })

    it("requires a real cycle for a one-card persisted roster", () => {
        expect(snapshot([row(0)]).trustedComplete).toBe(true)
        expect(snapshot([row(0)], { ...header(1), terminationReason: "single_card" }).trustedComplete).toBe(false)
    })
})

function inspiration() {
    const selfFactors = [{
        rowIndex: 0, column: "left", kind: "stat", displayName: "Speed", normalizedName: "SPEED", stars: 1,
        canonicalName: "Speed", canonicalPath: "strong", factorFingerprint: "stat:SPEED:1", structuralFingerprint: "stat:1", ambiguous: false,
    }]
    const entries: Record<string, any>[] = [0, 1].map((index) => ({
        type: "veteran_inspiration", schemaVersion: 2, scanId: "inspiration", scanIndex: index,
        rosterFingerprint: row(index).rosterFingerprint, character: "Taiki Shuttle", outfit: "Wild Frontier", rank: "A",
        selfPortraitObserved: true, selfFactorCount: 1, selfFactorFingerprint: "stat:SPEED:1", selfStructuralFingerprint: "stat:1",
        selfFactorSetTrusted: true, selfFactors, legacyAncestors: [], termination: "reached_bottom", sparkCaptureComplete: true,
        screenReadCompleteness: 1, unresolvedFields: [], diagnostics: {
            frames: 1, swipes: 0, startedAtTop: true, reachedBottom: true, factorListEndObserved: true,
            gapFrames: 0, spacingBreaks: 0, alignmentFailures: 0, unsettledFrames: 0, deadReckonedFrames: 0,
            scrollbarContentHeight: null, observedContentHeight: null, rowsAccepted: 1,
            clippedRowsRejected: 0, leadingPartialBlockRows: 0, blocksObserved: 1,
        },
    }))
    const scan: Record<string, any> = {
        type: "veteran_inspiration_scan", schemaVersion: 2, scanId: "inspiration", registeredUsedAtStart: 2,
        registeredUsedAtEnd: 2, registeredCapacity: 260, filtersOff: true, snapshotCompatibility: true, pagerCycleClosed: true,
        entryLimit: 0, startIndex: 0, entriesCaptured: 2, entriesComplete: 2, terminationReason: "cycle_closed",
    }
    return { entries, scan }
}

describe("consumed Inspiration traversal", () => {
    const cases: readonly [string, (entries: Record<string, any>[], scan: Record<string, any>) => void][] = [
        ["duplicate index and missing zero", (entries) => { entries[0].scanIndex = 1 }],
        ["unsupported header schema", (_, scan) => { scan.schemaVersion = 99 }],
        ["unsupported capture schema", (entries) => { entries[0].schemaVersion = 99 }],
        ["missing entryLimit", (_, scan) => { delete scan.entryLimit }],
        ["missing startIndex", (_, scan) => { delete scan.startIndex }],
        ["resumed traversal", (_, scan) => { scan.startIndex = 1 }],
        ["bounded traversal", (_, scan) => { scan.entryLimit = 2 }],
        ["missing scan index", (entries) => { delete entries[0].scanIndex }],
        ["fractional scan index", (entries) => { entries[0].scanIndex = 0.5 }],
        ["missing required entry", (entries) => { entries.pop() }],
        ["duplicate identity mapping", (entries) => { entries[1].rosterFingerprint = entries[0].rosterFingerprint }],
        ["complete count contradiction", (_, scan) => { scan.entriesComplete = 1 }],
        ["unproven cycle", (_, scan) => { scan.pagerCycleClosed = false }],
        ["changed registered count", (_, scan) => { scan.registeredUsedAtEnd = 3 }],
        ["filtered list", (_, scan) => { scan.filtersOff = false }],
    ]

    const parse = (entries: Record<string, any>[], scan: Record<string, any>) =>
        parseInspirationRecords([...entries, scan].map((entry) => JSON.stringify(entry)).join("\n"))

    it("keeps complete unbounded entry-zero cycle compatible and account-wide", () => {
        const { entries, scan } = inspiration()
        const index = buildInspirationIndex(parse(entries, scan))
        expect([...index.values()].every((entry) => entry.snapshotCompatible)).toBe(true)
        expect(buildFactorScarcityIndex(buildRetentionEvidence(snapshot(), index, null)).accountWide).toBe(true)
    })

    it("separates complete coverage of surviving rows from trusted account membership", () => {
        const { entries, scan } = inspiration()
        const index = buildInspirationIndex(parse(entries, scan))
        const current = snapshot([row(0)], header(2))
        expect(current.trustedComplete).toBe(false)
        const scarcity = buildFactorScarcityIndex(buildRetentionEvidence(current, index, null))
        expect(scarcity.coverage).toBe(1)
        expect(scarcity.capturedTrusted).toBe(1)
        expect(scarcity.accountWide).toBe(false)
        const partial = new Map(index)
        partial.delete(row(1).rosterFingerprint)
        expect(buildFactorScarcityIndex(buildRetentionEvidence(snapshot(), partial, null)).accountWide).toBe(false)
    })

    it("reports 100 percent subset coverage without account-wide support in the actual CLI", () => {
        const { entries, scan } = inspiration()
        const sources = {
            roster: [row(0), header(2)].map((record) => JSON.stringify(record)).join("\n"),
            inspiration: [...entries, scan].map((record) => JSON.stringify(record)).join("\n"),
        }
        const json = runParentLabCli("retention", sources, ["--json"])
        expect(json.error).toBeUndefined()
        expect(json.status).toBe(1)
        expect(JSON.parse(json.stdout).scarcity).toMatchObject({ coverage: 1, identifiedRosterEntries: 1, capturedTrusted: 1, accountWide: false })
        const text = runParentLabCli("retention", sources)
        expect(text.status).toBe(1)
        expect(text.stdout).toMatch(/subset coverage\s+100\.0%/)
        expect(text.stdout).toMatch(/account-wide claims\s+NOT SUPPORTED/)
        expect(text.stdout).not.toMatch(/account-wide claims\s+SUPPORTED/)
    }, 30_000)

    it.each([["character", "King Halo"], ["outfit", "Other Outfit"], ["rank", "S+"]])("rejects explicit %s disagreement despite a matching fingerprint", (field, value) => {
        const { entries, scan } = inspiration()
        entries[0][field] = value
        const evidence = buildRetentionEvidence(snapshot(), buildInspirationIndex(parse(entries, scan)), null)
        expect(evidence.veterans[0].capture?.rosterFingerprint).toBe(row(0).rosterFingerprint)
        expect(evidence.veterans[0].captureTrusted).toBe(false)
        expect(evidence.veterans[0].selfFactors).toBeNull()
        expect(evidence.veterans[1].captureTrusted).toBe(true)
        expect(buildFactorScarcityIndex(evidence).accountWide).toBe(false)
    })

    it.each(cases)("rejects %s from compatibility conflicts and account-wide scarcity", (_, corrupt) => {
        const { entries, scan } = inspiration()
        corrupt(entries, scan)
        const parsed = parse(entries, scan)
        const index = buildInspirationIndex(parsed)
        expect([...index.values()].every((entry) => !entry.snapshotCompatible)).toBe(true)
        expect(detectInspirationConflicts(parsed).size).toBe(0)
        expect(buildFactorScarcityIndex(buildRetentionEvidence(snapshot(), index, null)).accountWide).toBe(false)
    })

    it("does not promote partial work by a repeated scan ID", () => {
        const { entries, scan } = inspiration()
        const parsed = parse(entries, scan)
        const index = buildInspirationIndex({ ...parsed, scans: [...parsed.scans, { ...parsed.scans[0], startIndex: 1 }] })
        expect([...index.values()].every((entry) => !entry.snapshotCompatible)).toBe(true)
    })

    it("rejects legacy one-card shortcuts even with an asserted cycle flag", () => {
        const { entries, scan } = inspiration()
        const single = { ...scan, registeredUsedAtStart: 1, registeredUsedAtEnd: 1, entriesCaptured: 1, entriesComplete: 1 }
        expect([...buildInspirationIndex(parse(entries.slice(0, 1), single)).values()][0].snapshotCompatible).toBe(true)
        expect([...buildInspirationIndex(parse(entries.slice(0, 1), { ...single, terminationReason: "single_card" })).values()][0].snapshotCompatible).toBe(false)
    })
})
