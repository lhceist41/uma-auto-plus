// PL-R3 batch safety: what a set of individually safe removals does to the account taken together.
// Fixtures live in ./quarantineFixtures.ts.

import { validateTransferBatch } from "../quarantineBatch.ts"
import { buildQuarantineLedger } from "../quarantineLedger.ts"
import type { AdvisorSnapshot, QuarantineLedger } from "../quarantineTypes.ts"
import { FP_A, FP_B, FP_PEER_A, FP_PEER_B, DAY, eligible, peers, PROFILES, SHARED_CHARACTER, snapshotOf, T0, type VetSpec } from "./quarantineFixtures.ts"

describe("PL-R3 batch safety", () => {
    /** Two candidates that are each individually safe under the same peer population. */
    function pairLedger(specs: readonly VetSpec[]): { ledger: QuarantineLedger; snapshot: AdvisorSnapshot } {
        const snapshots = ["rs-1", "rs-2", "rs-3"].map((id, i) => snapshotOf(id, specs, { observedAt: T0 + i * DAY }))
        return { ledger: buildQuarantineLedger(snapshots), snapshot: snapshots[snapshots.length - 1] }
    }

    it("accepts a batch whose every claim survives the removal", () => {
        const specs = [eligible({ fp: FP_A }), eligible({ fp: FP_B }), ...peers()]
        const { ledger, snapshot } = pairLedger(specs)
        const result = validateTransferBatch(ledger, snapshot, [FP_A, FP_B])
        expect(result.rejections).toEqual([])
        expect(result.ok).toBe(true)
        expect(result.retainedCoverage.rosterAfter).toBe(2)
    })

    it("rejects a batch whose members are each safe but together hold the last carrier of a factor", () => {
        const rare = [{ factorKey: "stat:SPEED", stars: 1 }, { factorKey: "white:RARE SKILL", stars: 1 }]
        const specs = [eligible({ fp: FP_A, factors: rare }), eligible({ fp: FP_B, factors: rare }), ...peers()]
        const { ledger, snapshot } = pairLedger(specs)
        expect(validateTransferBatch(ledger, snapshot, [FP_A]).ok).toBe(true)
        expect(validateTransferBatch(ledger, snapshot, [FP_B]).ok).toBe(true)
        const both = validateTransferBatch(ledger, snapshot, [FP_A, FP_B])
        expect(both.ok).toBe(false)
        expect(both.rejections.map((r) => r.reason)).toContain("BATCH_REMOVES_LAST_FACTOR_CARRIER")
        expect(both.rejections.find((r) => r.reason === "BATCH_REMOVES_LAST_FACTOR_CARRIER")?.detail).toEqual(["white:RARE SKILL@1"])
    })

    it("rejects a batch that contains the peer a member depends on", () => {
        const specs = [eligible({ fp: FP_A, dominators: [FP_B] }), eligible({ fp: FP_B, dominators: [FP_PEER_A] }), ...peers()]
        const { ledger, snapshot } = pairLedger(specs)
        const result = validateTransferBatch(ledger, snapshot, [FP_A, FP_B])
        expect(result.ok).toBe(false)
        const rejection = result.rejections.find((r) => r.reason === "DOMINATOR_REMOVED_BY_BATCH")
        expect(rejection?.candidateKey).toBe(FP_A)
        expect(rejection?.detail).toEqual([...PROFILES].sort())
    })

    it("rejects a circular transfer set", () => {
        const specs = [eligible({ fp: FP_A, dominators: [FP_B, FP_PEER_A] }), eligible({ fp: FP_B, dominators: [FP_A, FP_PEER_A] }), ...peers()]
        const { ledger, snapshot } = pairLedger(specs)
        const result = validateTransferBatch(ledger, snapshot, [FP_A, FP_B])
        expect(result.ok).toBe(false)
        expect(result.rejections.map((r) => r.reason)).toContain("CIRCULAR_SUBSTITUTE_DEPENDENCE")
        expect(result.rejections.find((r) => r.reason === "CIRCULAR_SUBSTITUTE_DEPENDENCE")?.detail).toEqual([FP_A, FP_B])
    })

    it("rejects a batch that takes the last Veteran of a character", () => {
        const specs = [eligible({ fp: FP_A, character: "Sole One" }), ...peers()]
        const { ledger, snapshot } = pairLedger(specs)
        const result = validateTransferBatch(ledger, snapshot, [FP_A])
        expect(result.rejections.map((r) => r.reason)).toContain("BATCH_REMOVES_LAST_CHARACTER_SOURCE")
        expect(result.rejections.find((r) => r.reason === "BATCH_REMOVES_LAST_CHARACTER_SOURCE")?.detail).toEqual(["SOLE ONE"])
        expect(result.retainedCoverage.charactersAfter).toBe(1)
    })

    it("rejects a batch that would leave a target profile uncovered", () => {
        const specs = [
            eligible({ fp: FP_A, targetsCovered: ["MILE_PARENT", "LONG_PARENT"] }),
            { fp: FP_PEER_A, character: SHARED_CHARACTER, factors: [{ factorKey: "stat:SPEED", stars: 3 }], targetsCovered: ["MILE_PARENT"] },
            { fp: FP_PEER_B, character: SHARED_CHARACTER, factors: [{ factorKey: "stat:SPEED", stars: 3 }], targetsCovered: ["MILE_PARENT"] },
        ]
        const { ledger, snapshot } = pairLedger(specs)
        const result = validateTransferBatch(ledger, snapshot, [FP_A])
        expect(result.rejections.map((r) => r.reason)).toContain("BATCH_REMOVES_LAST_TARGET_COVERAGE")
        expect(result.retainedCoverage.targetsCoveredAfter).toEqual(["MILE_PARENT"])
    })

    it("rejects an immature candidate, an unknown candidate, a duplicate and an empty batch", () => {
        const specs = [eligible({ fp: FP_A }), ...peers()]
        const snapshot = snapshotOf("rs-1", specs)
        const ledger = buildQuarantineLedger([snapshot])
        expect(validateTransferBatch(ledger, snapshot, [FP_A]).rejections.map((r) => r.reason)).toContain("CANDIDATE_NOT_MATURE")
        expect(validateTransferBatch(ledger, snapshot, ["nope"]).rejections.map((r) => r.reason)).toEqual(expect.arrayContaining(["CANDIDATE_NOT_IN_LEDGER", "CANDIDATE_NOT_IN_LATEST_SNAPSHOT"]))
        expect(validateTransferBatch(ledger, snapshot, [FP_A, FP_A]).rejections.map((r) => r.reason)).toContain("DUPLICATE_CANDIDATE_IN_BATCH")
        expect(validateTransferBatch(ledger, snapshot, []).rejections.map((r) => r.reason)).toEqual(["EMPTY_BATCH"])
    })

    it("rejects a hard-protected Veteran even when it is somehow in the ledger", () => {
        const specs = [eligible({ fp: FP_A }), ...peers()]
        const snapshots = ["rs-1", "rs-2", "rs-3"].map((id, i) => snapshotOf(id, specs, { observedAt: T0 + i * DAY }))
        const ledger = buildQuarantineLedger(snapshots)
        const tainted = snapshotOf("rs-3", [eligible({ fp: FP_A, hardProtectReasons: ["SOLE_CHARACTER_SOURCE"] }), ...peers()], { observedAt: T0 + 2 * DAY })
        expect(validateTransferBatch(ledger, tainted, [FP_A]).rejections.map((r) => r.reason)).toContain("HARD_PROTECT_IN_BATCH")
    })

    it("refuses any batch against an untrusted snapshot", () => {
        const specs = [eligible({ fp: FP_A }), ...peers()]
        const snapshots = ["rs-1", "rs-2", "rs-3"].map((id, i) => snapshotOf(id, specs, { observedAt: T0 + i * DAY }))
        const ledger = buildQuarantineLedger(snapshots)
        const untrusted = snapshotOf("rs-3", specs, { observedAt: T0 + 2 * DAY, accountWide: false })
        expect(validateTransferBatch(ledger, untrusted, [FP_A]).rejections.map((r) => r.reason)).toContain("SNAPSHOT_NOT_TRUSTED")
    })

    it("rejects a stale MATURE ledger against a currently ineligible candidate", () => {
        const specs = [eligible({ fp: FP_A }), ...peers()]
        const { ledger } = pairLedger(specs)
        const current = snapshotOf("rs-3", [eligible({ fp: FP_A, protectionState: "unknown" }), ...peers()], { observedAt: T0 + 2 * DAY })
        expect(ledger.entries.find((entry) => entry.candidateKey === FP_A)?.status).toBe("MATURE")
        const result = validateTransferBatch(ledger, current, [FP_A])
        expect(result.ok).toBe(false)
        expect(result.rejections.map((rejection) => rejection.reason)).toContain("CANDIDATE_CURRENTLY_INELIGIBLE")
    })
})
