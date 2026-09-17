import { buildProtectionInventory, latestTrustedProtectionRecord, parseProtectionRecords } from "../protection.ts"
import { buildRosterSnapshots, parseRosterScanRecords, rosterBindingDigestForFingerprints, type RosterSnapshot } from "../roster.ts"
import { buildRetentionShadowReport } from "../retentionAdvisor.ts"
import { buildRetentionEvidence } from "../retentionEvidence.ts"
import { TARGET_PROFILES } from "../retentionTargets.ts"

// Everything goes through the real parse paths: the roster snapshot is built from JSONL by the actual
// roster parser, and the protection record from JSONL by the actual protection parser, so a change to
// either ingest path shows up here.

const SCAN = "rs-prot-0001"
const fingerprint = (index: number) => (index + 10).toString(16).repeat(32)
const DIGEST = rosterBindingDigestForFingerprints([0, 1, 2].map(fingerprint))!
const T = Date.UTC(2026, 7, 22, 12, 0, 0)
const APTITUDES = { turf: "A", dirt: "G", sprint: "C", mile: "A", medium: "A", long: "B", front: "A", pace: "A", late: "B", end: "C" }

function rosterEntry(index: number, fp: string | null, o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "roster_entry",
        schemaVersion: 1,
        scanId: SCAN,
        scanIndex: index,
        observedAt: T,
        character: `Char ${index}`,
        outfit: "Base",
        rank: "S",
        rating: 15000 - index,
        stats: { spd: 900, sta: 700, pwr: 650, grt: 600, wit: 500 },
        statGrades: { spd: "A", sta: "B", pwr: "B", grt: "C", wit: "C" },
        aptitudes: APTITUDES,
        favoriteState: "unknown",
        protectionState: "unknown",
        careerInfo: null,
        rosterFingerprint: fp,
        readCompleteness: 1,
        identityMultiplicity: 1,
        unresolvedFields: [],
        diagnostics: null,
        ...o,
    })
}

/** A trusted-complete N-entry snapshot with fingerprints fp-0 .. fp-(N-1). */
function snapshotOf(n: number): RosterSnapshot {
    const header = JSON.stringify({
        type: "roster_scan",
        schemaVersion: 1,
        scanId: SCAN,
        startedAt: T,
        completedAt: T,
        displayedRegisteredUsed: n,
        displayedRegisteredCapacity: 260,
        filtersOff: true,
        sortKey: "rating",
        sortDirection: "descending",
        entryLimit: 0,
        entriesEnumerated: n,
        uniqueFingerprints: n,
        unidentifiedCount: 0,
        duplicateFingerprintCount: 0,
        countDiscrepancy: 0,
        terminationReason: "count_reached",
        enumerationComplete: true,
        identityComplete: true,
        completeness: "trusted_complete",
        evidenceCropCount: 0,
        app: "test",
        screenWidth: 1080,
        screenHeight: 1920,
    })
    const rows = Array.from({ length: n }, (_, i) => rosterEntry(i, fingerprint(i)))
    const snapshots = buildRosterSnapshots(parseRosterScanRecords([...rows, header].join("\n")))
    return snapshots[0]
}

function protectionRecord(o: Record<string, unknown> = {}): string {
    return JSON.stringify({
        type: "veteran_protection",
        schemaVersion: 2,
        rosterBindingVersion: 1,
        rosterScanId: SCAN,
        rosterDigest: DIGEST,
        scanId: "vp-0001",
        startedAt: T,
        completedAt: T,
        registeredUsed: 3,
        registeredCapacity: 260,
        filtersOffConfirmed: true,
        favoritePopulation: "empty",
        favoriteApplyState: "disabled",
        memoPopulation: "empty",
        memoApplyState: "disabled",
        enumerationPerformed: false,
        favoritedFingerprints: [],
        memoFingerprints: [],
        restoredFiltersOff: true,
        outcome: "complete",
        app: "test",
        screenWidth: 1080,
        screenHeight: 1920,
        ...o,
    })
}

describe("protection record parsing", () => {
    it("parses a valid record and skips malformed lines", () => {
        const text = [protectionRecord(), "{ not json", JSON.stringify({ type: "roster_scan", scanId: "x" }), JSON.stringify({ type: "veteran_protection", scanId: "y", outcome: "bogus" })].join("\n")
        const parsed = parseProtectionRecords(text)
        expect(parsed.records).toHaveLength(1)
        expect(parsed.records[0].favoritePopulation).toBe("empty")
        expect(parsed.malformedRecords).toBe(3)
    })

    it("picks the newest complete probe that restored filters", () => {
        const text = [
            protectionRecord({ scanId: "old", completedAt: T - 1000 }),
            protectionRecord({ scanId: "failed", completedAt: T + 5000, outcome: "restore_failed", restoredFiltersOff: false }),
            protectionRecord({ scanId: "newest", completedAt: T + 1000 }),
        ].join("\n")
        const parsed = parseProtectionRecords(text)
        expect(latestTrustedProtectionRecord(parsed)?.scanId).toBe("newest")
    })

    it("returns null when nothing is trustworthy", () => {
        const text = protectionRecord({ outcome: "ui_unexpected" })
        expect(latestTrustedProtectionRecord(parseProtectionRecords(text))).toBeNull()
    })
})

describe("protection inventory", () => {
    it("rejects a same-count changed roster and a same-content different scan ID", () => {
        const record = parseProtectionRecords(protectionRecord()).records[0]
        const original = snapshotOf(3)
        const changed = { ...original, entries: original.entries.map((entry, i) => i === 2 ? { ...entry, rosterFingerprint: "d".repeat(32) } : entry) }
        expect(changed.entries).toHaveLength(original.entries.length)
        expect(buildProtectionInventory(record, changed).counts.notProtected).toBe(0)
        expect(buildProtectionInventory(record, changed).defects).toContain("binding_invalid")
        const rescanned = { ...original, scanId: "rs-other", entries: original.entries.map((entry) => ({ ...entry, scanId: "rs-other" })) }
        expect(buildProtectionInventory(record, rescanned).counts.notProtected).toBe(0)
        expect(buildProtectionInventory(record, rescanned).defects).toContain("binding_invalid")
    })

    it("keeps legacy and missing or unsupported bindings diagnostic only", () => {
        const snapshot = snapshotOf(3)
        for (const override of [{ schemaVersion: 1 }, { rosterBindingVersion: undefined }, { rosterBindingVersion: 2 }, { rosterDigest: undefined }, { rosterScanId: undefined }, { registeredUsed: "3" }]) {
            const record = parseProtectionRecords(protectionRecord(override)).records[0]
            const inventory = buildProtectionInventory(record, snapshot)
            expect(inventory.compatible).toBe(false)
            expect(inventory.counts.notProtected).toBe(0)
            expect(inventory.counts.protectionUnknown).toBe(3)
        }
    })

    it("rejects malformed members and partial nonempty enumeration as a whole inventory", () => {
        const snapshot = snapshotOf(3)
        for (const members of [[], [fingerprint(1), fingerprint(1)], [fingerprint(1), null], ["d".repeat(32)], "bad"]) {
            const record = parseProtectionRecords(protectionRecord({ favoritePopulation: "nonempty", favoriteApplyState: "enabled", enumerationPerformed: true, favoritedFingerprints: members })).records[0]
            const inventory = buildProtectionInventory(record, snapshot)
            expect(inventory.compatible).toBe(false)
            expect(inventory.counts.notProtected).toBe(0)
        }
    })

    it("does not reuse an older empty probe after the newest selected probe mismatches", () => {
        const old = protectionRecord({ scanId: "old", completedAt: T - 1000 })
        const newest = protectionRecord({ scanId: "new", completedAt: T + 1000, rosterDigest: "0".repeat(32) })
        const selected = latestTrustedProtectionRecord(parseProtectionRecords([old, newest].join("\n")))
        expect(selected?.scanId).toBe("new")
        expect(buildProtectionInventory(selected, snapshotOf(3)).counts.notProtected).toBe(0)
    })

    it("empty favorite and memo populations make every Veteran not-protected", () => {
        const inv = buildProtectionInventory(parseProtectionRecords(protectionRecord()).records[0], snapshotOf(3))
        expect(inv.compatible).toBe(true)
        expect(inv.counts.notProtected).toBe(3)
        expect(inv.counts.protected).toBe(0)
        expect(inv.counts.protectionUnknown).toBe(0)
        expect(inv.byFingerprint.get(fingerprint(1))).toEqual({ favoriteState: "not_favorite", memoState: "no_memo", protectionState: "not_protected" })
    })

    it("an enumerated non-empty favorite partition names its members and leaves the rest not-protected", () => {
        const rec = parseProtectionRecords(protectionRecord({ favoritePopulation: "nonempty", favoriteApplyState: "enabled", enumerationPerformed: true, favoritedFingerprints: [fingerprint(1)] })).records[0]
        const inv = buildProtectionInventory(rec, snapshotOf(3))
        expect(inv.byFingerprint.get(fingerprint(1))?.protectionState).toBe("protected")
        expect(inv.byFingerprint.get(fingerprint(0))?.protectionState).toBe("not_protected")
        expect(inv.counts.protected).toBe(1)
        expect(inv.counts.favorite).toBe(1)
    })

    it("a non-empty partition that was NOT enumerated leaves everyone unknown", () => {
        const rec = parseProtectionRecords(protectionRecord({ favoritePopulation: "nonempty", favoriteApplyState: "enabled", enumerationPerformed: false })).records[0]
        const inv = buildProtectionInventory(rec, snapshotOf(3))
        expect(inv.counts.protectionUnknown).toBe(3)
        expect(inv.counts.notProtected).toBe(0)
    })

    it("a roster-count mismatch marks the inventory incompatible and keeps everything unknown", () => {
        const rec = parseProtectionRecords(protectionRecord({ registeredUsed: 999 })).records[0]
        const inv = buildProtectionInventory(rec, snapshotOf(3))
        expect(inv.compatible).toBe(false)
        expect(inv.defects).toContain("roster_count_mismatch")
        expect(inv.counts.protectionUnknown).toBe(3)
    })

    it("a missing probe never maps anyone to not-protected", () => {
        const inv = buildProtectionInventory(null, snapshotOf(3))
        expect(inv.compatible).toBe(false)
        expect(inv.defects).toContain("no_protection_record")
        expect(inv.counts.notProtected).toBe(0)
        expect(inv.counts.protectionUnknown).toBe(3)
    })
})

describe("advisor integration", () => {
    const profile = TARGET_PROFILES.GENERAL_INHERITANCE

    it("a proven-unprotected Veteran clears the favorite and protection gates", () => {
        const snapshot = snapshotOf(3)
        const inv = buildProtectionInventory(parseProtectionRecords(protectionRecord()).records[0], snapshot)
        const evidence = buildRetentionEvidence(snapshot, new Map(), null, inv)
        const report = buildRetentionShadowReport({ evidence, library: null, reconciliation: null, profile })
        const rec = report.recommendations.find((r) => r.rosterFingerprint === fingerprint(1))!
        expect(rec.gateReasons).not.toContain("PROTECTION_STATE_UNKNOWN")
        expect(rec.gateReasons).not.toContain("FAVORITE_STATE_UNKNOWN")
    })

    it("without a protection inventory the gates stay closed as before", () => {
        const snapshot = snapshotOf(3)
        const evidence = buildRetentionEvidence(snapshot, new Map(), null)
        const report = buildRetentionShadowReport({ evidence, library: null, reconciliation: null, profile })
        const rec = report.recommendations.find((r) => r.rosterFingerprint === fingerprint(1))!
        expect(rec.gateReasons).toContain("PROTECTION_STATE_UNKNOWN")
        expect(rec.gateReasons).toContain("FAVORITE_STATE_UNKNOWN")
    })

    it("a favorited Veteran is HARD_PROTECT with PROTECTED_ON_ACCOUNT", () => {
        const snapshot = snapshotOf(3)
        const rec0 = parseProtectionRecords(protectionRecord({ favoritePopulation: "nonempty", favoriteApplyState: "enabled", enumerationPerformed: true, favoritedFingerprints: [fingerprint(2)] })).records[0]
        const inv = buildProtectionInventory(rec0, snapshot)
        const evidence = buildRetentionEvidence(snapshot, new Map(), null, inv)
        const report = buildRetentionShadowReport({ evidence, library: null, reconciliation: null, profile })
        const rec = report.recommendations.find((r) => r.rosterFingerprint === fingerprint(2))!
        expect(rec.state).toBe("HARD_PROTECT")
        expect(rec.hardProtectReasons).toContain("PROTECTED_ON_ACCOUNT")
    })
})
