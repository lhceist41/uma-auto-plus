// PL-R3 quarantine ledger: candidate identity, maturity accrual, invalidation, determinism, and the
// seam back to the real advisor. Fixtures live in ./quarantineFixtures.ts and the reasoning behind
// their synthetic shape is documented there.

import { buildInspirationIndex, parseInspirationRecords } from "../inspiration.ts"
import { approveManifest, draftApprovalManifest, validateApprovedManifest } from "../approvalManifest.ts"
import { validateTransferBatch } from "../quarantineBatch.ts"
import { buildQuarantineLedger, DEFAULT_QUARANTINE_POLICY, ledgerEntry } from "../quarantineLedger.ts"
import { buildAdvisorSnapshot, candidateKeyFor, retentionReportsOf, sameCandidate } from "../quarantineSnapshot.ts"
import type { QuarantineLedger } from "../quarantineTypes.ts"
import { buildRetentionShadowReport } from "../retentionAdvisor.ts"
import { buildRetentionEvidence } from "../retentionEvidence.ts"
import { TARGET_PROFILE_IDS, TARGET_PROFILES } from "../retentionTargets.ts"
import { PARENTLAB_RETENTION_SCHEMA } from "../retentionTypes.ts"
import { buildRosterSnapshots, parseRosterScanRecords } from "../roster.ts"
import { FP_A, FP_B, FP_CAND, FP_PEER_B, DAY, eligible, peers, PROFILES, reportsFor, snapshotOf, statusOf, T0, type VetSpec } from "./quarantineFixtures.ts"

describe("PL-R3 candidate identity", () => {
    it("keys a unique fingerprint by the fingerprint alone", () => {
        const snapshot = snapshotOf("rs-1", [eligible(), ...peers()])
        expect(candidateKeyFor(FP_CAND, null)).toBe(FP_CAND)
        expect(snapshot.candidates.get(FP_CAND)?.ref.occurrenceOrdinal).toBeNull()
    })

    it("blocks every occurrence when one fingerprint names more than one Veteran", () => {
        const snapshot = snapshotOf("rs-1", [
            eligible({ fp: "dup", scanIndex: 0, multiplicity: 2 }),
            eligible({ fp: "dup", scanIndex: 1, multiplicity: 2 }),
            ...peers(),
        ])
        const ledger = buildQuarantineLedger([snapshot])
        expect(statusOf(ledger, "dup#0")).toBe("BLOCKED")
        expect(statusOf(ledger, "dup#1")).toBe("BLOCKED")
        expect(ledgerEntry(ledger, "dup#0")?.statusReasons).toContain("AMBIGUOUS_ROSTER_IDENTITY")
    })

    it("treats a changed stat read as a different Veteran for re-identification", () => {
        const a = snapshotOf("rs-1", [eligible(), ...peers()]).candidates.get(FP_CAND)
        const b = snapshotOf("rs-2", [eligible({ stats: { spd: 901, sta: 700, pwr: 650, grt: 600, wit: 500 } }), ...peers()]).candidates.get(FP_CAND)
        expect(sameCandidate(a!.ref, a!.ref)).toBe(true)
        expect(sameCandidate(a!.ref, b!.ref)).toBe(false)
    })
})

describe("PL-R3 quarantine maturity", () => {
    it("records a first sighting as OBSERVED, never MATURE", () => {
        const ledger = buildQuarantineLedger([snapshotOf("rs-1", [eligible(), ...peers()])])
        expect(statusOf(ledger, FP_CAND)).toBe("OBSERVED")
        expect(ledgerEntry(ledger, FP_CAND)?.consecutiveEligibleSnapshots).toBe(1)
    })

    it("matures only after the required number of distinct snapshots", () => {
        const specs = [eligible(), ...peers()]
        const two = buildQuarantineLedger([snapshotOf("rs-1", specs, { observedAt: T0 }), snapshotOf("rs-2", specs, { observedAt: T0 + DAY })])
        expect(statusOf(two, FP_CAND)).toBe("QUARANTINED")
        const three = buildQuarantineLedger([
            snapshotOf("rs-1", specs, { observedAt: T0 }),
            snapshotOf("rs-2", specs, { observedAt: T0 + DAY }),
            snapshotOf("rs-3", specs, { observedAt: T0 + 2 * DAY }),
        ])
        expect(statusOf(three, FP_CAND)).toBe("MATURE")
        expect(ledgerEntry(three, FP_CAND)?.eligibleSnapshotIds).toEqual(["rs-1", "rs-2", "rs-3"])
        expect(ledgerEntry(three, FP_CAND)?.firstEligibleAt).toBe(T0)
        expect(ledgerEntry(three, FP_CAND)?.lastEligibleAt).toBe(T0 + 2 * DAY)
    })

    it("does not increment maturity when the same snapshot is replayed", () => {
        const specs = [eligible(), ...peers()]
        const one = snapshotOf("rs-1", specs)
        const ledger = buildQuarantineLedger([one, one, one])
        expect(statusOf(ledger, FP_CAND)).toBe("OBSERVED")
        expect(ledger.snapshots).toHaveLength(1)
        expect(ledger.duplicateSnapshotsIgnored).toEqual(["rs-1"])
    })

    it("does not increment maturity when the same roster scan is rebuilt from fresh documents", () => {
        const specs = [eligible(), ...peers()]
        const ledger = buildQuarantineLedger([snapshotOf("rs-1", specs, { observedAt: T0 }), snapshotOf("rs-1", specs, { observedAt: T0 + DAY })])
        expect(ledgerEntry(ledger, FP_CAND)?.consecutiveEligibleSnapshots).toBe(1)
        expect(ledger.duplicateSnapshotsIgnored).toEqual(["rs-1"])
    })

    it("restarts the run when the semantic evidence changes", () => {
        const base = [eligible(), ...peers()]
        const moved = [eligible({ dominators: [FP_PEER_B] }), ...peers()]
        const ledger = buildQuarantineLedger([
            snapshotOf("rs-1", base, { observedAt: T0 }),
            snapshotOf("rs-2", base, { observedAt: T0 + DAY }),
            snapshotOf("rs-3", moved, { observedAt: T0 + 2 * DAY }),
        ])
        expect(statusOf(ledger, FP_CAND)).toBe("QUARANTINED")
        expect(ledgerEntry(ledger, FP_CAND)?.consecutiveEligibleSnapshots).toBe(1)
        expect(ledgerEntry(ledger, FP_CAND)?.statusReasons).toContain("EVIDENCE_DIGEST_CHANGED")
    })

    it("never matures a QUARANTINE_TRANSFER candidate, even across many snapshots", () => {
        const specs = [eligible({ state: "QUARANTINE_TRANSFER", confidence: "MEDIUM" }), ...peers()]
        const ledger = buildQuarantineLedger(["rs-1", "rs-2", "rs-3", "rs-4"].map((id, i) => snapshotOf(id, specs, { observedAt: T0 + i * DAY })))
        expect(statusOf(ledger, FP_CAND)).toBe("BLOCKED")
        expect(ledgerEntry(ledger, FP_CAND)?.statusReasons).toContain("ADVISOR_STATE_NOT_APPROVAL_ELIGIBLE")
    })
})

describe("PL-R3 invalidation", () => {
    const mature = (latest: VetSpec[]): QuarantineLedger =>
        buildQuarantineLedger([
            snapshotOf("rs-1", [eligible(), ...peers()], { observedAt: T0 }),
            snapshotOf("rs-2", [eligible(), ...peers()], { observedAt: T0 + DAY }),
            snapshotOf("rs-3", [eligible(), ...peers()], { observedAt: T0 + 2 * DAY }),
            snapshotOf("rs-4", latest, { observedAt: T0 + 3 * DAY }),
        ])

    it("blocks a matured candidate that becomes favorited", () => {
        const ledger = mature([eligible({ favoriteState: "favorite", protectionState: "protected" }), ...peers()])
        expect(statusOf(ledger, FP_CAND)).toBe("BLOCKED")
        expect(ledgerEntry(ledger, FP_CAND)?.statusReasons).toContain("PROTECTED_ON_ACCOUNT")
    })

    it("blocks a matured candidate whose factor becomes account-unique", () => {
        const ledger = mature([eligible({ observedUnique: ["stat:SPEED"] }), ...peers()])
        expect(statusOf(ledger, FP_CAND)).toBe("BLOCKED")
        expect(ledgerEntry(ledger, FP_CAND)?.statusReasons).toContain("UNIQUE_FACTOR_COVERAGE_PRESENT")
    })

    it("blocks every candidate when capture coverage stops supporting account-wide claims", () => {
        const ledger = buildQuarantineLedger([
            snapshotOf("rs-1", [eligible(), ...peers()], { observedAt: T0 }),
            snapshotOf("rs-2", [eligible(), ...peers()], { observedAt: T0 + DAY }),
            snapshotOf("rs-3", [eligible(), ...peers()], { observedAt: T0 + 2 * DAY }),
            snapshotOf("rs-4", [eligible(), ...peers()], { observedAt: T0 + 3 * DAY, accountWide: false }),
        ])
        expect(statusOf(ledger, FP_CAND)).toBe("BLOCKED")
        expect(ledgerEntry(ledger, FP_CAND)?.statusReasons).toContain("ACCOUNT_COVERAGE_NOT_ACCOUNT_WIDE")
    })

    it("blocks a matured candidate whose dominator set empties", () => {
        const ledger = mature([eligible({ dominators: [] }), ...peers()])
        expect(statusOf(ledger, FP_CAND)).toBe("BLOCKED")
        expect(ledgerEntry(ledger, FP_CAND)?.statusReasons).toContain("DOMINATOR_SET_EMPTY")
    })

    it("blocks a matured candidate whose advisor state weakens", () => {
        const ledger = mature([eligible({ state: "KEEP" }), ...peers()])
        expect(statusOf(ledger, FP_CAND)).toBe("BLOCKED")
        expect(ledgerEntry(ledger, FP_CAND)?.statusReasons).toContain("ADVISOR_STATE_NOT_ELIGIBLE")
    })

    it("blocks a matured candidate that later hard-protects", () => {
        const ledger = mature([eligible({ hardProtectReasons: ["MANUAL_PROTECT"] }), ...peers()])
        expect(statusOf(ledger, FP_CAND)).toBe("BLOCKED")
        expect(ledgerEntry(ledger, FP_CAND)?.statusReasons).toContain("HARD_PROTECT_PRESENT")
    })

    it("blocks a matured candidate whose Spark evidence stops being trusted", () => {
        const ledger = mature([eligible({ gateReasons: ["INSPIRATION_CAPTURE_INCOMPLETE"] }), ...peers()])
        expect(statusOf(ledger, FP_CAND)).toBe("BLOCKED")
        expect(ledgerEntry(ledger, FP_CAND)?.statusReasons).toContain("SPARK_EVIDENCE_UNTRUSTED")
    })

    it("expires a candidate that disappears from the roster", () => {
        const ledger = mature([...peers()])
        expect(statusOf(ledger, FP_CAND)).toBe("EXPIRED")
        expect(ledgerEntry(ledger, FP_CAND)?.statusReasons).toEqual(["ABSENT_FROM_LATEST_ROSTER"])
        expect(ledgerEntry(ledger, FP_CAND)?.consecutiveEligibleSnapshots).toBe(0)
    })

    it("blocks everything in a snapshot that is missing a target profile", () => {
        const ledger = buildQuarantineLedger([snapshotOf("rs-1", [eligible(), ...peers()], { profiles: ["GENERAL_INHERITANCE", "MILE_PARENT"] })])
        expect(statusOf(ledger, FP_CAND)).toBe("BLOCKED")
        expect(ledgerEntry(ledger, FP_CAND)?.statusReasons).toContain("TARGET_PROFILE_COVERAGE_INCOMPLETE")
    })

    it("refuses a retention document older than the schema PL-R3 needs", () => {
        const snapshot = snapshotOf("rs-1", [eligible(), ...peers()], { schemaVersion: 3 })
        expect(snapshot.defects).toContain("RETENTION_SCHEMA_UNSUPPORTED")
        expect(statusOf(buildQuarantineLedger([snapshot]), FP_CAND)).toBe("BLOCKED")
    })

    it("blocks every candidate when target reports disagree on protection binding", () => {
        const reports = reportsFor("rs-1", [eligible(), ...peers()])
        const changed = reports.map((report, i) => i === 1 ? { ...report, protectionBinding: { ...report.protectionBinding!, rosterDigest: "0".repeat(32) } } : report)
        const snapshot = buildAdvisorSnapshot(changed)
        expect(snapshot.defects).toContain("PROTECTION_BINDING_INVALID")
        expect([...snapshot.candidates.values()].every((candidate) => !candidate.eligible)).toBe(true)
    })

    it("rejects a report whose recommendation fingerprints disagree with the bound digest", () => {
        const reports = reportsFor("rs-1", [eligible(), ...peers()])
        const changed = reports.map((report) => ({ ...report, recommendations: report.recommendations.map((rec, i) => i === 0 ? { ...rec, rosterFingerprint: "d".repeat(32) } : rec) }))
        expect(buildAdvisorSnapshot(changed).defects).toContain("PROTECTION_BINDING_INVALID")
    })

    it("accepts the same index-to-fingerprint assignment regardless of recommendation array order", () => {
        const reports = reportsFor("rs-1", [eligible(), ...peers()])
        const reordered = reports.map((report, i) => i === 1 ? { ...report, recommendations: [...report.recommendations].reverse() } : report)
        expect(buildAdvisorSnapshot(reports).defects).toEqual([])
        const snapshot = buildAdvisorSnapshot(reordered)
        expect(snapshot.defects).toEqual([])
        expect(snapshot.candidates.get(FP_CAND)?.eligible).toBe(true)
    })

    it("rejects duplicate, missing, extra, and substituted scan-index assignments", () => {
        const reports = reportsFor("rs-1", [eligible(), ...peers()])
        const recs = reports[1].recommendations
        const variants = [
            recs.map((rec, i) => i === 1 ? { ...rec, scanIndex: 0 } : rec),
            recs.slice(0, -1),
            [...recs, { ...recs[0], scanIndex: recs.length, rosterFingerprint: "d".repeat(32) }],
            recs.map((rec, i) => i === 0 ? { ...rec, rosterFingerprint: "d".repeat(32) } : rec),
        ]
        for (const recommendations of variants) {
            const changed = reports.map((report, i) => i === 1 ? { ...report, recommendations } : report)
            const snapshot = buildAdvisorSnapshot(changed)
            expect(snapshot.defects).toContain("PROTECTION_BINDING_INVALID")
            expect([...snapshot.candidates.values()].every((candidate) => !candidate.eligible)).toBe(true)
        }
    })

    it("rejects target reports that disagree on candidate identity or protection fields", () => {
        const reports = reportsFor("rs-1", [eligible(), ...peers()])
        const variants = [
            { ...reports[1].recommendations[0], protectionState: "protected" },
            { ...reports[1].recommendations[0], stats: { ...reports[1].recommendations[0].stats, spd: 901 } },
        ]
        for (const changedRec of variants) {
            const changed = reports.map((report, i) => i === 1 ? { ...report, recommendations: [changedRec, ...report.recommendations.slice(1)] } : report)
            const snapshot = buildAdvisorSnapshot(changed)
            expect(snapshot.defects).toContain("RECOMMENDATION_SET_MISMATCH")
            expect(snapshot.candidates.get(FP_CAND)?.eligible).toBe(false)
        }
    })

    it("blocks a row permutation through maturity, batch validation, approval, and revalidation", () => {
        const specs = [eligible({ fp: FP_A }), eligible({ fp: FP_B }), ...peers()]
        const good = ["rs-1", "rs-2", "rs-3"].map((id, i) => snapshotOf(id, specs, { observedAt: T0 + i * DAY }))
        const goodLedger = buildQuarantineLedger(good)
        const draft = draftApprovalManifest({ ledger: goodLedger, snapshot: good[2], candidateKeys: [FP_A], createdAt: T0 + 4 * DAY }).manifest!
        expect(statusOf(goodLedger, FP_A)).toBe("MATURE")
        expect(draft).not.toBeNull()
        const approved = approveManifest({ manifest: draft, ledger: goodLedger, snapshot: good[2], at: T0 + 5 * DAY }).manifest
        expect(approved.humanApprovalStatus).toBe("APPROVED")

        const reports = reportsFor("rs-3", specs, { observedAt: T0 + 2 * DAY })
        const permuted = reports.map((report, i) => {
            if (i !== 1) return report
            const [attacked, decoy, ...rest] = report.recommendations
            return {
                ...report,
                recommendations: [
                    { ...decoy, scanIndex: attacked.scanIndex },
                    { ...attacked, scanIndex: decoy.scanIndex, state: "KEEP" as const, hardProtectReasons: ["SOLE_CHARACTER_SOURCE"] as const },
                    ...rest,
                ],
            }
        })
        expect(permuted[1].protectionBinding).toEqual(permuted[0].protectionBinding)
        expect(permuted[1].recommendations.map((rec) => rec.scanIndex)).toEqual(permuted[0].recommendations.map((rec) => rec.scanIndex))
        expect(permuted[1].recommendations.map((rec) => rec.rosterFingerprint).sort()).toEqual(permuted[0].recommendations.map((rec) => rec.rosterFingerprint).sort())

        const snapshot = buildAdvisorSnapshot(permuted)
        expect(snapshot.defects).toContain("PROTECTION_BINDING_INVALID")
        expect([...snapshot.candidates.values()].every((candidate) => !candidate.eligible)).toBe(true)
        const ledger = buildQuarantineLedger([good[0], good[1], snapshot])
        expect(statusOf(ledger, FP_A)).toBe("BLOCKED")
        expect(validateTransferBatch(ledger, snapshot, [FP_A]).ok).toBe(false)
        expect(draftApprovalManifest({ ledger, snapshot, candidateKeys: [FP_A], createdAt: T0 + 4 * DAY }).manifest).toBeNull()
        const attempt = approveManifest({ manifest: draft, ledger, snapshot, at: T0 + 5 * DAY })
        expect(attempt.validation.verdict).toBe("BLOCKED")
        expect(attempt.manifest.humanApprovalStatus).toBe("STALE")
        const revalidation = validateApprovedManifest({ manifest: approved, ledger, snapshot })
        expect(revalidation.verdict).toBe("BLOCKED")
        expect(revalidation.reasons).toContain("SNAPSHOT_NOT_TRUSTED")
    })
})

describe("PL-R3 determinism", () => {
    it("rebuilds an identical ledger from the same snapshots, in any order", () => {
        const specs = [eligible(), ...peers()]
        const snapshots = ["rs-1", "rs-2", "rs-3"].map((id, i) => snapshotOf(id, specs, { observedAt: T0 + i * DAY }))
        const a = JSON.stringify(buildQuarantineLedger(snapshots))
        const b = JSON.stringify(buildQuarantineLedger([...snapshots].reverse()))
        const c = JSON.stringify(buildQuarantineLedger(snapshots))
        expect(a).toBe(c)
        expect(a).toBe(b)
    })

    it("produces the same digest for the same semantic evidence and a different one when it moves", () => {
        const specs = [eligible(), ...peers()]
        const one = snapshotOf("rs-1", specs, { observedAt: T0 })
        const two = snapshotOf("rs-2", specs, { observedAt: T0 + DAY })
        expect(two.candidates.get(FP_CAND)?.evidenceDigest).toBe(one.candidates.get(FP_CAND)?.evidenceDigest)
        const moved = snapshotOf("rs-3", [eligible({ confidence: "MEDIUM" }), ...peers()], { observedAt: T0 + 2 * DAY })
        expect(moved.candidates.get(FP_CAND)?.evidenceDigest).not.toBe(one.candidates.get(FP_CAND)?.evidenceDigest)
    })

    it("carries the policy and the snapshot provenance on the document", () => {
        const ledger = buildQuarantineLedger([snapshotOf("rs-1", [eligible(), ...peers()])])
        expect(ledger.policy).toEqual(DEFAULT_QUARANTINE_POLICY)
        expect(ledger.snapshots[0].protectionScanId).toBe("vp-fixture")
        expect(ledger.snapshots[0].eligibleCount).toBe(1)
        expect(ledger.latestSnapshotId).toBe("rs-1")
    })
})

describe("PL-R3 document reading", () => {
    it("accepts both the single-report and the multi-report document shapes", () => {
        const reports = reportsFor("rs-1", [eligible(), ...peers()])
        expect(retentionReportsOf({ schema: PARENTLAB_RETENTION_SCHEMA, schemaVersion: 2, reports })).toHaveLength(PROFILES.length)
        expect(retentionReportsOf(reports[0])).toHaveLength(1)
    })

    it("throws rather than producing an empty snapshot from a malformed document", () => {
        expect(() => retentionReportsOf(null)).toThrow(/not an object/)
        expect(() => retentionReportsOf({ schema: "something_else" })).toThrow(/schema/)
        expect(() => retentionReportsOf({ schema: PARENTLAB_RETENTION_SCHEMA })).toThrow(/neither/)
    })
})

describe("PL-R3 against a real advisor document", () => {
    // The seam test: a real roster walk, through the real advisor, into a real snapshot. It asserts
    // the wiring rather than a verdict - the roster here has no captures at all, so every Veteran is
    // UNKNOWN and nothing is a transfer candidate, which is the correct outcome for it.
    const SCAN = "rs-seam-0001"
    const APTITUDES = { turf: "A", dirt: "G", sprint: "C", mile: "A", medium: "A", long: "B", front: "A", pace: "A", late: "B", end: "C" }

    function rosterJsonl(count: number): string {
        const header = JSON.stringify({
            type: "roster_scan",
            schemaVersion: 1,
            scanId: SCAN,
            startedAt: T0,
            completedAt: T0,
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
            terminationReason: "list_end",
            enumerationComplete: true,
            identityComplete: true,
            completeness: "trusted_complete",
            evidenceCropCount: 0,
            app: "test",
            screenWidth: 1080,
            screenHeight: 1920,
        })
        const rows = Array.from({ length: count }, (_, i) =>
            JSON.stringify({
                type: "roster_entry",
                schemaVersion: 1,
                scanId: SCAN,
                scanIndex: i,
                observedAt: T0,
                character: `Trainee ${i}`,
                outfit: "Base",
                rank: "A",
                rating: 15000 - i,
                stats: { spd: 900 + i, sta: 700, pwr: 650, grt: 600, wit: 500 },
                statGrades: { spd: "A", sta: "B", pwr: "B", grt: "C", wit: "C" },
                aptitudes: APTITUDES,
                favoriteState: "not_set",
                protectionState: "unknown",
                careerInfo: null,
                rosterFingerprint: `fp-${i}`,
                readCompleteness: 1,
                identityMultiplicity: 1,
                unresolvedFields: [],
                diagnostics: null,
            }),
        )
        return [header, ...rows].join("\n")
    }

    it("normalizes a real advisor document into a snapshot with nothing eligible", () => {
        const parsed = parseRosterScanRecords(rosterJsonl(3), "seam.jsonl")
        const snapshot = buildRosterSnapshots(parsed)[0]
        const evidence = buildRetentionEvidence(snapshot, buildInspirationIndex(parseInspirationRecords("", "none.jsonl")), null)
        const reports = TARGET_PROFILE_IDS.map((id) => buildRetentionShadowReport({ evidence, library: null, reconciliation: null, profile: TARGET_PROFILES[id] }))
        const advisorSnapshot = buildAdvisorSnapshot(reports)

        expect(advisorSnapshot.rosterScanId).toBe(SCAN)
        expect(advisorSnapshot.candidates.size).toBe(3)
        expect(advisorSnapshot.protectionScanId).toBeNull()
        expect(advisorSnapshot.candidates.get("fp-0")?.ref.stats).toEqual({ spd: 900, sta: 700, pwr: 650, grt: 600, wit: 500 })
        expect([...advisorSnapshot.candidates.values()].filter((c) => c.tracked)).toEqual([])
        expect([...advisorSnapshot.candidates.values()].filter((c) => c.eligible)).toEqual([])
        expect(buildQuarantineLedger([advisorSnapshot]).entries).toEqual([])
    })
})
