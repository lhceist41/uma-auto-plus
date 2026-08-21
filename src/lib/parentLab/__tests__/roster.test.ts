import { buildRosterSnapshots, latestTrustedSnapshot, parseRosterScanRecords } from "../roster.ts"

// The roster snapshot is exercised through the same parse path the device writer produces: JSONL
// lines authored exactly as the Kotlin serializer writes them, parsed and derived offline.

const APTITUDES = { turf: "A", dirt: "B", sprint: "A", mile: "A", medium: "E", long: "G", front: "C", pace: "A", late: "E", end: "G" }

function entryLine(scanId: string, scanIndex: number, o: Record<string, unknown> = {}): string {
    const stats = { spd: 949 + scanIndex, sta: 699, pwr: 648, grt: 687, wit: 420 }
    return JSON.stringify({
        type: "roster_entry",
        schemaVersion: 1,
        scanId,
        scanIndex,
        observedAt: 1_700_000_000_000 + scanIndex,
        character: "Taiki Shuttle",
        outfit: "Wild Frontier",
        rank: "A",
        rating: 10192 + scanIndex,
        stats,
        statGrades: { spd: "A+", sta: "B", pwr: "B", grt: "B", wit: "C" },
        aptitudes: APTITUDES,
        favoriteState: "not_set",
        protectionState: "unknown",
        rosterFingerprint: `fp-${scanIndex}`,
        readCompleteness: 1,
        identityMultiplicity: 1,
        unresolvedFields: [],
        ...o,
    })
}

function headerLine(scanId: string, o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "roster_scan",
        schemaVersion: 1,
        scanId,
        startedAt: 1_700_000_000_000,
        completedAt: 1_700_000_100_000,
        displayedRegisteredUsed: 3,
        displayedRegisteredCapacity: 260,
        filtersOff: true,
        sortKey: "Rating",
        sortDirection: "Asc",
        entryLimit: 0,
        entriesEnumerated: 3,
        uniqueFingerprints: 3,
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

function cleanScan(scanId = "scan-a", header: Record<string, unknown> = {}): string {
    return [entryLine(scanId, 0), entryLine(scanId, 1), entryLine(scanId, 2), headerLine(scanId, header)].join("\n")
}

function snapshotOf(text: string, scanId = "scan-a") {
    const snapshots = buildRosterSnapshots(parseRosterScanRecords(text, "roster_scan.jsonl"))
    const found = snapshots.find((s) => s.scanId === scanId)
    if (!found) throw new Error(`no snapshot for ${scanId}`)
    return found
}

describe("parseRosterScanRecords", () => {
    it("parses the header and entry rows the device writes", () => {
        const parsed = parseRosterScanRecords(cleanScan(), "roster_scan.jsonl")
        expect(parsed.scans).toHaveLength(1)
        expect(parsed.entries).toHaveLength(3)
        expect(parsed.malformedRecords).toBe(0)
        expect(parsed.scans[0].displayedRegisteredUsed).toBe(3)
        expect(parsed.entries[0].stats.spd).toBe(949)
        expect(parsed.entries[0].aptitudes.end).toBe("G")
        expect(parsed.entries[0].file).toBe("roster_scan.jsonl")
    })

    it("skips malformed lines and foreign records without discarding the rest", () => {
        const text = ["not json at all", JSON.stringify({ type: "career_finalize", trainee: "X" }), ...cleanScan().split("\n")].join("\n")
        const parsed = parseRosterScanRecords(text)
        expect(parsed.entries).toHaveLength(3)
        expect(parsed.scans).toHaveLength(1)
        expect(parsed.malformedRecords).toBe(2)
    })

    it("drops a header with an unrecognised termination rather than defaulting it", () => {
        const parsed = parseRosterScanRecords(headerLine("scan-a", { terminationReason: "vibes" }))
        expect(parsed.scans).toHaveLength(0)
        expect(parsed.malformedRecords).toBe(1)
    })

    it("drops an entry with no scanId or no scanIndex", () => {
        const parsed = parseRosterScanRecords([entryLine("scan-a", 0, { scanId: "" }), entryLine("scan-a", 1, { scanIndex: "x" })].join("\n"))
        expect(parsed.entries).toHaveLength(0)
        expect(parsed.malformedRecords).toBe(2)
    })

    it("reads an unfingerprinted entry as unidentified rather than as an empty fingerprint", () => {
        const parsed = parseRosterScanRecords(entryLine("scan-a", 0, { rosterFingerprint: undefined, character: undefined, unresolvedFields: ["character"] }))
        expect(parsed.entries[0].rosterFingerprint).toBeNull()
        expect(parsed.entries[0].character).toBeNull()
        expect(parsed.entries[0].unresolvedFields).toEqual(["character"])
    })

    it("reads the Career Info block when present and null when absent", () => {
        const withInfo = parseRosterScanRecords(
            entryLine("scan-a", 0, { careerInfo: { races: 18, wins: 13, fans: 191730, scenario: "The Beginning: URA Finale", rating: 10192, dateAcquired: "2026-08-10" } }),
        )
        expect(withInfo.entries[0].careerInfo).toEqual({ races: 18, wins: 13, fans: 191730, scenario: "The Beginning: URA Finale", rating: 10192, dateAcquired: "2026-08-10" })
        expect(parseRosterScanRecords(entryLine("scan-a", 0)).entries[0].careerInfo).toBeNull()
    })
})

describe("buildRosterSnapshots completeness", () => {
    it("trusts a scan whose enumerated count matches the account's own used count", () => {
        const snapshot = snapshotOf(cleanScan())
        expect(snapshot.trustedComplete).toBe(true)
        expect(snapshot.defects).toEqual([])
        expect(snapshot.scanCount).toBe(3)
        expect(snapshot.registeredUsed).toBe(3)
        expect(snapshot.countDiscrepancy).toBe(0)
        expect(snapshot.percentFull).toBe(1.2)
    })

    it("orders entries by scan index regardless of the order they were written", () => {
        const scrambled = [entryLine("scan-a", 2), entryLine("scan-a", 0), entryLine("scan-a", 1), headerLine("scan-a")].join("\n")
        expect(snapshotOf(scrambled).entries.map((e) => e.scanIndex)).toEqual([0, 1, 2])
    })

    it("refuses to trust a scan that enumerated fewer entries than the account reports", () => {
        const short = [entryLine("scan-a", 0), entryLine("scan-a", 1), headerLine("scan-a", { entriesEnumerated: 2, completeness: "incomplete", terminationReason: "chevron_end" })].join("\n")
        const snapshot = snapshotOf(short)
        expect(snapshot.trustedComplete).toBe(false)
        expect(snapshot.countDiscrepancy).toBe(-1)
        expect(snapshot.defects).toContain("count_mismatch")
    })

    it("refuses to trust a scan that enumerated more entries than the account reports", () => {
        const long = [...cleanScan().split("\n").slice(0, 3), entryLine("scan-a", 3), headerLine("scan-a", { entriesEnumerated: 4, completeness: "incomplete" })].join("\n")
        const snapshot = snapshotOf(long)
        expect(snapshot.countDiscrepancy).toBe(1)
        expect(snapshot.trustedComplete).toBe(false)
    })

    it("does not let a duplicate fingerprint hide a matching count", () => {
        const dup = [entryLine("scan-a", 0, { rosterFingerprint: "fp-0" }), entryLine("scan-a", 1, { rosterFingerprint: "fp-0" }), entryLine("scan-a", 2), headerLine("scan-a")].join("\n")
        const snapshot = snapshotOf(dup)
        expect(snapshot.scanCount).toBe(3)
        expect(snapshot.countDiscrepancy).toBe(0)
        expect(snapshot.uniqueFingerprints).toBe(2)
        expect(snapshot.duplicateFingerprints).toBe(1)
        expect(snapshot.trustedComplete).toBe(false)
        expect(snapshot.defects).toContain("duplicate_fingerprints")
    })

    it("keeps both positions of a duplicate rather than merging them", () => {
        const dup = [entryLine("scan-a", 0, { rosterFingerprint: "fp-0" }), entryLine("scan-a", 1, { rosterFingerprint: "fp-0" }), entryLine("scan-a", 2), headerLine("scan-a")].join("\n")
        expect(snapshotOf(dup).entries.map((e) => e.scanIndex)).toEqual([0, 1, 2])
    })

    it("treats an unconfirmed filter state as fail-closed", () => {
        for (const filtersOff of [false, undefined]) {
            expect(snapshotOf(cleanScan("scan-a", { filtersOff })).defects).toContain("filters_not_confirmed_off")
        }
    })

    it("never trusts a scan whose termination is not consistent with reaching the end", () => {
        for (const reason of ["entry_limit_reached", "hard_bound_reached", "stalled", "wrapped", "unexpected_screen", "precondition_failed"]) {
            const snapshot = snapshotOf(cleanScan("scan-a", { terminationReason: reason }))
            expect(snapshot.trustedComplete).toBe(false)
            expect(snapshot.defects).toContain("termination_not_at_end")
        }
        expect(snapshotOf(cleanScan("scan-a", { terminationReason: "chevron_end" })).trustedComplete).toBe(true)
    })

    it("never trusts a scan the device itself marked incomplete", () => {
        expect(snapshotOf(cleanScan("scan-a", { completeness: "incomplete" })).defects).toContain("device_marked_incomplete")
    })

    it("flags entry rows that never reached disk even though the header says they were read", () => {
        const truncated = [entryLine("scan-a", 0), entryLine("scan-a", 1), headerLine("scan-a")].join("\n")
        const snapshot = snapshotOf(truncated)
        expect(snapshot.scanCount).toBe(2)
        expect(snapshot.defects).toContain("entry_rows_missing")
        expect(snapshot.trustedComplete).toBe(false)
    })

    it("treats a headerless run as an interrupted partial scan, not as nothing", () => {
        const headerless = [entryLine("scan-a", 0), entryLine("scan-a", 1)].join("\n")
        const snapshot = snapshotOf(headerless)
        expect(snapshot.headerPresent).toBe(false)
        expect(snapshot.scanCount).toBe(2)
        expect(snapshot.registeredUsed).toBeNull()
        expect(snapshot.trustedComplete).toBe(false)
        expect(snapshot.defects).toContain("no_header_record")
        expect(snapshot.observedAt).toBe(1_700_000_000_001)
    })

    it("counts an unidentified entry and refuses to trust the scan", () => {
        const partial = [entryLine("scan-a", 0), entryLine("scan-a", 1), entryLine("scan-a", 2, { rosterFingerprint: undefined }), headerLine("scan-a", { unidentifiedCount: 1 })].join("\n")
        const snapshot = snapshotOf(partial)
        expect(snapshot.unidentified).toBe(1)
        expect(snapshot.defects).toContain("unidentified_entries")
    })
})

describe("buildRosterSnapshots selection", () => {
    it("returns snapshots newest first and rebuilds deterministically", () => {
        const text = [cleanScan("scan-old", { startedAt: 1_000, completedAt: 2_000 }), cleanScan("scan-new", { startedAt: 9_000, completedAt: 9_500 })].join("\n")
        const first = buildRosterSnapshots(parseRosterScanRecords(text))
        const second = buildRosterSnapshots(parseRosterScanRecords(text))
        expect(first.map((s) => s.scanId)).toEqual(["scan-new", "scan-old"])
        expect(JSON.stringify(first)).toBe(JSON.stringify(second))
    })

    it("does not let a newer incomplete walk displace an older trusted one", () => {
        const text = [
            cleanScan("scan-old", { startedAt: 1_000, completedAt: 2_000 }),
            cleanScan("scan-new", { startedAt: 9_000, completedAt: 9_500, completeness: "incomplete", terminationReason: "entry_limit_reached" }),
        ].join("\n")
        const snapshots = buildRosterSnapshots(parseRosterScanRecords(text))
        expect(snapshots[0].scanId).toBe("scan-new")
        expect(latestTrustedSnapshot(snapshots)?.scanId).toBe("scan-old")
    })

    it("returns no trusted snapshot rather than a wrong one when nothing is complete", () => {
        expect(latestTrustedSnapshot(buildRosterSnapshots(parseRosterScanRecords(cleanScan("scan-a", { completeness: "incomplete" }))))).toBeNull()
    })
})
