import { fixtureFingerprint } from "./rosterFixtures.ts"
import { spawnSync } from "node:child_process"
import { resolve } from "node:path"
import { buildProtectionInventory, latestProtectionRecord, parseProtectionRecords } from "../protection.ts"
import { buildRosterSnapshots, parseRosterScanRecords, rosterBindingDigestForFingerprints, type RosterSnapshot } from "../roster.ts"
import { buildRetentionShadowReport } from "../retentionAdvisor.ts"
import { buildRetentionEvidence } from "../retentionEvidence.ts"
import { TARGET_PROFILES } from "../retentionTargets.ts"

// Everything goes through the real parse paths: the roster snapshot is built from JSONL by the actual
// roster parser, and the protection record from JSONL by the actual protection parser, so a change to
// either ingest path shows up here.

const SCAN = "rs-prot-0001"
const fingerprint = (index: number) => fixtureFingerprint(JSON.parse(rosterEntry(index, null)))
const T = Date.UTC(2026, 7, 22, 12, 0, 0)
const APTITUDES = { turf: "A", dirt: "G", sprint: "C", mile: "A", medium: "A", long: "B", front: "A", pace: "A", late: "B", end: "C" }
const NEUTRAL_FILTERS = { track: "neutral", distance: "neutral", style: "neutral", attribute_sparks: "neutral", aptitude_sparks: "neutral", unique_sparks: "neutral", common_sparks: "neutral", favorites: "neutral", memo: "neutral" }

const DIGEST = rosterBindingDigestForFingerprints([0, 1, 2].map(fingerprint))!

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
        terminationReason: n === 0 ? "empty_list" : "cycle_closed",
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
        favoriteBaselineVerified: true,
        filterBaselineEvidenceVersion: 1,
        favoriteBaselineReadings: NEUTRAL_FILTERS,
        memoPopulation: "empty",
        memoApplyState: "disabled",
        memoBaselineVerified: true,
        memoBaselineReadings: NEUTRAL_FILTERS,
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
    it.each([1, null, "invalid", "260", 3.5, -1, 0, undefined])("rejects incoherent persisted capacity %s", (registeredCapacity) => {
        const record = latestProtectionRecord(parseProtectionRecords(protectionRecord({ registeredCapacity })))
        const inventory = buildProtectionInventory(record, snapshotOf(3))
        expect(inventory.compatible).toBe(false)
        expect(inventory.defects).toContain("capacity_invalid")
        expect(inventory.counts.notProtected).toBe(0)
        expect(inventory.counts.favoriteUnknown).toBe(3)
        expect(inventory.counts.protectionUnknown).toBe(3)
    })

    it.each([3, 4, 260])("accepts capacity %i when it agrees with the bound roster", (registeredCapacity) => {
        const snapshot = { ...snapshotOf(3), registeredCapacity }
        const inventory = buildProtectionInventory(latestProtectionRecord(parseProtectionRecords(protectionRecord({ registeredCapacity }))), snapshot)
        expect(inventory.compatible).toBe(true)
        expect(inventory.counts.notProtected).toBe(3)
    })
    it.each([3, 4, 261])("rejects capacity %i when it differs from the bound roster", (registeredCapacity) => {
        const inventory = buildProtectionInventory(latestProtectionRecord(parseProtectionRecords(protectionRecord({ registeredCapacity }))), snapshotOf(3))
        expect(inventory.compatible).toBe(false)
        expect(inventory.defects).toContain("capacity_invalid")
        expect(inventory.counts.protectionUnknown).toBe(3)
    })
    it.each([[undefined], [null], ["true"], ["false"], [0], [1], [{}], [[]]])("preserves malformed enumeration metadata %s as unknown", (enumerationPerformed) => {
        const record = latestProtectionRecord(parseProtectionRecords(protectionRecord({ enumerationPerformed })))
        expect(record?.enumerationPerformed).toBeNull()
        const inventory = buildProtectionInventory(record, snapshotOf(3))
        expect(inventory.compatible).toBe(false)
        expect(inventory.counts.notProtected).toBe(0)
        expect(inventory.counts.protectionUnknown).toBe(3)
    })
    it.each([
        ["truncated", '{"type":"veteran_protection","scanId":"cut-off"'],
        ["syntactically malformed", "{ not json"],
    ])("treats a %s final nonblank line as authoritative", (_label, malformed) => {
        const parsed = parseProtectionRecords([protectionRecord({ scanId: "older-empty" }), malformed].join("\n"), "veteran_protection.jsonl")
        expect(parsed.records).toHaveLength(1)
        expect(parsed.latestCandidate).toEqual({ kind: "malformed", file: "veteran_protection.jsonl", lineNumber: 1 })
        expect(latestProtectionRecord(parsed)).toBeNull()
        const inventory = buildProtectionInventory(latestProtectionRecord(parsed), snapshotOf(3))
        expect(inventory.compatible).toBe(false)
        expect(inventory.defects).toContain("no_protection_record")
        expect(inventory.counts.notProtected).toBe(0)
        expect(inventory.counts.protectionUnknown).toBe(3)
    })

    it("lets a later valid record supersede an earlier malformed line", () => {
        const parsed = parseProtectionRecords(["{ not json", protectionRecord({ scanId: "later-valid" })].join("\n"))
        expect(parsed.latestCandidate?.kind).toBe("record")
        expect(latestProtectionRecord(parsed)?.scanId).toBe("later-valid")
        expect(buildProtectionInventory(latestProtectionRecord(parsed), snapshotOf(3)).counts.notProtected).toBe(3)
    })

    it("ignores blank trailing lines when selecting the latest candidate", () => {
        const parsed = parseProtectionRecords(`${protectionRecord({ scanId: "latest-valid" })}\n \n\t\r\n`)
        expect(parsed.latestCandidate?.kind).toBe("record")
        expect(latestProtectionRecord(parsed)?.scanId).toBe("latest-valid")
    })

    it("keeps an only malformed nonblank line as unavailable protection evidence", () => {
        const parsed = parseProtectionRecords("  { not json  \n\n")
        expect(parsed.records).toHaveLength(0)
        expect(parsed.malformedRecords).toBe(1)
        expect(parsed.latestCandidate).toMatchObject({ kind: "malformed", lineNumber: 0 })
        expect(latestProtectionRecord(parsed)).toBeNull()
        expect(buildProtectionInventory(latestProtectionRecord(parsed), snapshotOf(3)).counts.protectionUnknown).toBe(3)
    })

    it("keeps a recognizable malformed protection candidate in append order", () => {
        const text = [protectionRecord(), "{ not json", JSON.stringify({ type: "roster_scan", scanId: "x" }), JSON.stringify({ type: "veteran_protection", scanId: "y", outcome: "bogus" })].join("\n")
        const parsed = parseProtectionRecords(text)
        expect(parsed.records).toHaveLength(2)
        expect(parsed.records[0].favoritePopulation).toBe("empty")
        expect(latestProtectionRecord(parsed)?.outcome).toBe("invalid")
        expect(buildProtectionInventory(latestProtectionRecord(parsed), snapshotOf(3)).counts.protectionUnknown).toBe(3)
        expect(parsed.malformedRecords).toBe(3)
    })

    it("uses append order when a later complete probe follows a failed probe", () => {
        const text = [
            protectionRecord({ scanId: "old", completedAt: T - 1000 }),
            protectionRecord({ scanId: "failed", completedAt: T + 5000, outcome: "restore_failed", restoredFiltersOff: false }),
            protectionRecord({ scanId: "newest", completedAt: T + 1000 }),
        ].join("\n")
        const parsed = parseProtectionRecords(text)
        expect(latestProtectionRecord(parsed)?.scanId).toBe("newest")
        expect(buildProtectionInventory(latestProtectionRecord(parsed), snapshotOf(3)).counts.notProtected).toBe(3)
    })

    it.each([
        ["increasing", T - 1000, T + 1000],
        ["backward", T + 1000, T - 1000],
        ["equal", T, T],
        ["missing", T, undefined],
        ["malformed", T, "not-a-time"],
    ])("a later failed probe wins with %s timestamps", (_label, oldTs, laterTs) => {
        const records = parseProtectionRecords([
            protectionRecord({ scanId: "old-empty", completedAt: oldTs }),
            protectionRecord({ scanId: "later-failed", completedAt: laterTs, outcome: "ui_unexpected" }),
        ].join("\n"))
        const selected = latestProtectionRecord(records)
        expect(selected?.scanId).toBe("later-failed")
        expect(buildProtectionInventory(selected, snapshotOf(3)).counts.protectionUnknown).toBe(3)
    })

    it("a later protection candidate with no scan ID cannot revive older empty evidence", () => {
        const selected = latestProtectionRecord(parseProtectionRecords([
            protectionRecord({ scanId: "old-empty" }),
            protectionRecord({ scanId: null }),
        ].join("\n")))
        expect(selected?.outcome).toBe("invalid")
        expect(buildProtectionInventory(selected, snapshotOf(3)).counts.notProtected).toBe(0)
    })

    it("retains a failed probe as unknown evidence", () => {
        const text = protectionRecord({ outcome: "ui_unexpected" })
        const selected = latestProtectionRecord(parseProtectionRecords(text))
        expect(selected?.outcome).toBe("ui_unexpected")
        expect(buildProtectionInventory(selected, snapshotOf(3)).counts.notProtected).toBe(0)
    })
})

describe("production CLI loading", () => {
    it.each(["parent-lab-retention.mjs", "parent-lab-affinity.mjs"])("%s loads its help path", (name) => {
        const result = spawnSync(process.execPath, [resolve(process.cwd(), "scripts", name), "--help"], { encoding: "utf8" })
        expect(result.status).toBe(0)
        expect(result.stdout).toContain(name.replace(".mjs", ""))
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
        const selected = latestProtectionRecord(parseProtectionRecords([old, newest].join("\n")))
        expect(selected?.scanId).toBe("new")
        expect(buildProtectionInventory(selected, snapshotOf(3)).counts.notProtected).toBe(0)
    })

    it("a newer nonempty result supersedes an older empty probe on the same roster", () => {
        const old = protectionRecord({ scanId: "old", completedAt: T - 1000 })
        const newest = protectionRecord({ scanId: "new", completedAt: T + 1000, outcome: "nonempty_partition_census_unavailable", favoritePopulation: "nonempty", favoriteApplyState: "enabled" })
        const selected = latestProtectionRecord(parseProtectionRecords([old, newest].join("\n")))
        expect(selected?.scanId).toBe("new")
        const inv = buildProtectionInventory(selected, snapshotOf(3))
        expect(inv.compatible).toBe(false)
        expect(inv.counts.notProtected).toBe(0)
        expect(inv.counts.protectionUnknown).toBe(3)
    })

    it("empty favorite and memo populations make every Veteran not-protected", () => {
        const inv = buildProtectionInventory(parseProtectionRecords(protectionRecord()).records[0], snapshotOf(3))
        expect(inv.compatible).toBe(true)
        expect(inv.counts.notProtected).toBe(3)
        expect(inv.counts.protected).toBe(0)
        expect(inv.counts.protectionUnknown).toBe(0)
        expect(inv.byFingerprint.get(fingerprint(1))).toEqual({ favoriteState: "not_favorite", memoState: "no_memo", protectionState: "not_protected" })
    })

    it.each(["favoriteBaselineVerified", "memoBaselineVerified"])("rejects a missing %s from an older complete record", (field) => {
        const record = JSON.parse(protectionRecord())
        delete record[field]
        const inventory = buildProtectionInventory(latestProtectionRecord(parseProtectionRecords(JSON.stringify(record))), snapshotOf(3))
        expect(inventory.compatible).toBe(false)
        expect(inventory.defects).toContain("partition_invalid")
        expect(inventory.counts.protectionUnknown).toBe(3)
    })

    it.each(["filterBaselineEvidenceVersion", "favoriteBaselineReadings", "memoBaselineReadings"])("rejects missing full-filter proof field %s", (field) => {
        const record = JSON.parse(protectionRecord())
        delete record[field]
        const inventory = buildProtectionInventory(latestProtectionRecord(parseProtectionRecords(JSON.stringify(record))), snapshotOf(3))
        expect(inventory.compatible).toBe(false)
        expect(inventory.defects).toContain("partition_invalid")
        expect(inventory.counts.protectionUnknown).toBe(3)
    })

    it("rejects a false-empty favorite intersection with an unrelated active filter", () => {
        const record = latestProtectionRecord(parseProtectionRecords(protectionRecord({ favoriteBaselineReadings: { ...NEUTRAL_FILTERS, distance: "active" } })))
        const inventory = buildProtectionInventory(record, snapshotOf(3))
        expect(inventory.compatible).toBe(false)
        expect(inventory.counts.protectionUnknown).toBe(3)
    })

    it("rejects missing or novel dimensions in either baseline proof", () => {
        for (const readings of [{ ...NEUTRAL_FILTERS, extra: "neutral" }, Object.fromEntries(Object.entries(NEUTRAL_FILTERS).filter(([key]) => key !== "common_sparks"))]) {
            const record = latestProtectionRecord(parseProtectionRecords(protectionRecord({ memoBaselineReadings: readings })))
            expect(buildProtectionInventory(record, snapshotOf(3)).compatible).toBe(false)
        }
    })

    it.each([
        { label: "favorite baseline unknown", override: { favoritePopulation: "unknown", favoriteBaselineVerified: false } },
        { label: "memo baseline unknown", override: { memoPopulation: "unknown", memoBaselineVerified: false } },
        { label: "favorite nonempty", override: { favoritePopulation: "nonempty", favoriteApplyState: "enabled" } },
        { label: "memo nonempty", override: { memoPopulation: "nonempty", memoApplyState: "enabled" } },
    ])("whole inventory stays unknown when $label", ({ override }) => {
        const record = latestProtectionRecord(parseProtectionRecords(protectionRecord({ ...override, outcome: "ui_unexpected" })))
        const inventory = buildProtectionInventory(record, snapshotOf(3))
        expect(inventory.compatible).toBe(false)
        expect(inventory.counts.protectionUnknown).toBe(3)
        expect(inventory.counts.notProtected).toBe(0)
    })

    it("rejects legacy complete records with plausible but unproven nonempty member arrays", () => {
        for (const [favorite, memo] of [[true, false], [false, true], [true, true]]) {
            const rec = parseProtectionRecords(protectionRecord({
                favoritePopulation: favorite ? "nonempty" : "empty",
                favoriteApplyState: favorite ? "enabled" : "disabled",
                memoPopulation: memo ? "nonempty" : "empty",
                memoApplyState: memo ? "enabled" : "disabled",
                enumerationPerformed: true,
                favoritedFingerprints: favorite ? [fingerprint(0)] : [],
                memoFingerprints: memo ? [fingerprint(1)] : [],
            })).records[0]
            const inv = buildProtectionInventory(rec, snapshotOf(3))
            expect(inv.compatible).toBe(false)
            expect(inv.defects).toContain("partition_invalid")
            expect(inv.counts.protectionUnknown).toBe(3)
            expect(inv.counts.notProtected).toBe(0)
        }
    })

    it("a non-empty partition that was NOT enumerated leaves everyone unknown", () => {
        const rec = parseProtectionRecords(protectionRecord({ favoritePopulation: "nonempty", favoriteApplyState: "enabled", enumerationPerformed: false })).records[0]
        const inv = buildProtectionInventory(rec, snapshotOf(3))
        expect(inv.counts.protectionUnknown).toBe(3)
        expect(inv.counts.notProtected).toBe(0)
    })

    it("a non-complete nonempty outcome cannot turn partial arrays into negative evidence", () => {
        for (const [favorite, memo] of [[true, false], [false, true], [true, true]]) {
            const rec = parseProtectionRecords(protectionRecord({
                outcome: "nonempty_partition_census_unavailable",
                favoritePopulation: favorite ? "nonempty" : "empty",
                favoriteApplyState: favorite ? "enabled" : "disabled",
                memoPopulation: memo ? "nonempty" : "empty",
                memoApplyState: memo ? "enabled" : "disabled",
                enumerationPerformed: false,
                favoritedFingerprints: favorite ? [fingerprint(0)] : [],
                memoFingerprints: memo ? [fingerprint(1)] : [],
            })).records[0]
            const inv = buildProtectionInventory(rec, snapshotOf(3))
            expect(inv.compatible).toBe(false)
            expect(inv.defects).toContain("probe_not_complete")
            expect(inv.counts.favoriteUnknown).toBe(3)
            expect(inv.counts.memoUnknown).toBe(3)
            expect(inv.counts.protectionUnknown).toBe(3)
            expect(inv.counts.notProtected).toBe(0)
        }
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

    it("later incomplete evidence keeps the shared retention and affinity input unknown", () => {
        const snapshot = snapshotOf(3)
        const record = latestProtectionRecord(parseProtectionRecords([
            protectionRecord({ scanId: "old-empty", completedAt: T + 1000 }),
            protectionRecord({ scanId: "later-incomplete", completedAt: T - 1000, outcome: "ui_unexpected", favoriteBaselineVerified: false }),
        ].join("\n")))
        const inventory = buildProtectionInventory(record, snapshot)
        const evidence = buildRetentionEvidence(snapshot, new Map(), null, inventory)
        expect(inventory.protectionScanId).toBe("later-incomplete")
        expect(evidence.protectionInventory).toBeNull()
        expect(evidence.veterans.every(({ entry }) => entry.favoriteState === "unknown" && entry.protectionState === "unknown")).toBe(true)
        const report = buildRetentionShadowReport({ evidence, library: null, reconciliation: null, profile })
        expect(report.recommendations[0].gateReasons).toEqual(expect.arrayContaining(["FAVORITE_STATE_UNKNOWN", "PROTECTION_STATE_UNKNOWN"]))
    })

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

    it("unproven nonempty favorite members keep protection gates closed", () => {
        const snapshot = snapshotOf(3)
        const rec0 = parseProtectionRecords(protectionRecord({ favoritePopulation: "nonempty", favoriteApplyState: "enabled", enumerationPerformed: true, favoritedFingerprints: [fingerprint(2)] })).records[0]
        const inv = buildProtectionInventory(rec0, snapshot)
        const evidence = buildRetentionEvidence(snapshot, new Map(), null, inv)
        const report = buildRetentionShadowReport({ evidence, library: null, reconciliation: null, profile })
        const rec = report.recommendations.find((r) => r.rosterFingerprint === fingerprint(2))!
        expect(rec.gateReasons).toContain("PROTECTION_STATE_UNKNOWN")
        expect(rec.gateReasons).toContain("FAVORITE_STATE_UNKNOWN")
    })
})
