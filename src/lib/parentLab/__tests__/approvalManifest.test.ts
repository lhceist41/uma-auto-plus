// PL-R3 approval manifests: explicit drafting, explicit approval, revocation, and the staleness that
// keeps an approval from outliving the evidence it was granted on. Fixtures live in
// ./quarantineFixtures.ts.

import { approveManifest, draftApprovalManifest, latestManifests, markStaleIfInvalid, revokeManifest, validateApprovedManifest } from "../approvalManifest.ts"
import { batchEvidenceDigestOf } from "../quarantineBatch.ts"
import { applyApprovals, buildQuarantineLedger } from "../quarantineLedger.ts"
import type { AdvisorSnapshot, QuarantineLedger } from "../quarantineTypes.ts"
import { FP_A, FP_B, FP_PEER_B, DAY, eligible, peers, snapshotOf, statusOf, T0 } from "./quarantineFixtures.ts"

describe("PL-R3 approval manifests", () => {
    const SPECS = [eligible({ fp: FP_A }), eligible({ fp: FP_B }), ...peers()]
    const IDS = ["rs-1", "rs-2", "rs-3"]

    function ready(): { ledger: QuarantineLedger; snapshot: AdvisorSnapshot } {
        const snapshots = IDS.map((id, i) => snapshotOf(id, SPECS, { observedAt: T0 + i * DAY }))
        return { ledger: buildQuarantineLedger(snapshots), snapshot: snapshots[snapshots.length - 1] }
    }

    it("drafts, then approves only under an explicit command", () => {
        const { ledger, snapshot } = ready()
        const draft = draftApprovalManifest({ ledger, snapshot, candidateKeys: [FP_A], createdAt: T0 + 4 * DAY })
        expect(draft.manifest?.humanApprovalStatus).toBe("DRAFT")
        expect(draft.manifest?.candidateKeys).toEqual([FP_A])
        expect(draft.manifest?.approvedAt).toBeNull()
        // A draft alone changes nothing about the ledger: no implicit approval anywhere.
        expect(statusOf(applyApprovals(ledger, [draft.manifest!]), FP_A)).toBe("MATURE")

        const approved = approveManifest({ manifest: draft.manifest!, ledger, snapshot, at: T0 + 5 * DAY })
        expect(approved.validation.verdict).toBe("VALID")
        expect(approved.manifest.humanApprovalStatus).toBe("APPROVED")
        expect(approved.manifest.approvedAt).toBe(T0 + 5 * DAY)
        expect(statusOf(applyApprovals(ledger, [approved.manifest]), FP_A)).toBe("APPROVED")
    })

    it("refuses to draft a batch that does not validate", () => {
        const { ledger, snapshot } = ready()
        const rare = [{ factorKey: "white:ONLY HERE", stars: 1 }]
        const bad = ["rs-1", "rs-2", "rs-3"].map((id, i) => snapshotOf(id, [eligible({ fp: FP_A, factors: rare }), eligible({ fp: FP_B, factors: rare }), ...peers()], { observedAt: T0 + i * DAY }))
        const badLedger = buildQuarantineLedger(bad)
        const draft = draftApprovalManifest({ ledger: badLedger, snapshot: bad[2], candidateKeys: [FP_A, FP_B], createdAt: T0 })
        expect(draft.manifest).toBeNull()
        expect(draft.validation.ok).toBe(false)
        // The good path still works, so the refusal is about the batch and not about the fixture.
        expect(draftApprovalManifest({ ledger, snapshot, candidateKeys: [FP_A], createdAt: T0 }).manifest).not.toBeNull()
    })

    it("marks a draft STALE instead of approving it when the evidence moved", () => {
        const { ledger, snapshot } = ready()
        const draft = draftApprovalManifest({ ledger, snapshot, candidateKeys: [FP_A], createdAt: T0 + 4 * DAY }).manifest!
        const moved = ["rs-1", "rs-2", "rs-3", "rs-4"].map((id, i) => snapshotOf(id, i === 3 ? [eligible({ fp: FP_A, dominators: [FP_PEER_B] }), eligible({ fp: FP_B }), ...peers()] : SPECS, { observedAt: T0 + i * DAY }))
        const movedLedger = buildQuarantineLedger(moved)
        const attempt = approveManifest({ manifest: draft, ledger: movedLedger, snapshot: moved[3], at: T0 + 5 * DAY })
        expect(attempt.validation.verdict).not.toBe("VALID")
        expect(attempt.validation.reasons).toContain("EVIDENCE_DIGEST_CHANGED")
        expect(attempt.manifest.humanApprovalStatus).toBe("STALE")
        expect(attempt.manifest.approvedAt).toBeNull()
    })

    it("stales an already-approved manifest once the roster changes underneath it", () => {
        const { ledger, snapshot } = ready()
        const draft = draftApprovalManifest({ ledger, snapshot, candidateKeys: [FP_A], createdAt: T0 + 4 * DAY }).manifest!
        const approved = approveManifest({ manifest: draft, ledger, snapshot, at: T0 + 5 * DAY }).manifest
        expect(approved.humanApprovalStatus).toBe("APPROVED")

        const later = [...IDS, "rs-4"].map((id, i) => snapshotOf(id, i === 3 ? [eligible({ fp: FP_B }), ...peers()] : SPECS, { observedAt: T0 + i * DAY }))
        const laterLedger = buildQuarantineLedger(later)
        const verdict = validateApprovedManifest({ manifest: approved, ledger: laterLedger, snapshot: later[3] })
        expect(verdict.verdict).toBe("BLOCKED")
        expect(verdict.reasons).toContain("CANDIDATE_ABSENT")
        expect(markStaleIfInvalid({ manifest: approved, ledger: laterLedger, snapshot: later[3] }, T0 + 6 * DAY).manifest.humanApprovalStatus).toBe("STALE")
    })

    it("approval revalidation applies the current candidate guard despite a mature ledger", () => {
        const { ledger, snapshot } = ready()
        const draft = draftApprovalManifest({ ledger, snapshot, candidateKeys: [FP_A], createdAt: T0 }).manifest!
        const approved = approveManifest({ manifest: draft, ledger, snapshot, at: T0 + DAY }).manifest
        const current = snapshotOf("rs-3", [eligible({ fp: FP_A, favoriteState: "unknown" }), eligible({ fp: FP_B }), ...peers()], { observedAt: T0 + 2 * DAY })
        const validation = validateApprovedManifest({ manifest: approved, ledger, snapshot: current })
        expect(validation.verdict).not.toBe("VALID")
        expect(validation.reasons).toContain("BATCH_VALIDATION_FAILED")
        expect(validation.batchValidation?.rejections.map((rejection) => rejection.reason)).toContain("CANDIDATE_CURRENTLY_INELIGIBLE")
    })

    it("revokes, and a revoked manifest binds nothing", () => {
        const { ledger, snapshot } = ready()
        const draft = draftApprovalManifest({ ledger, snapshot, candidateKeys: [FP_A], createdAt: T0 + 4 * DAY }).manifest!
        const approved = approveManifest({ manifest: draft, ledger, snapshot, at: T0 + 5 * DAY }).manifest
        const revoked = revokeManifest(approved, T0 + 6 * DAY)
        expect(revoked.humanApprovalStatus).toBe("REVOKED")
        expect(revoked.revokedAt).toBe(T0 + 6 * DAY)
        expect(revoked.history.map((h) => h.to)).toEqual(["DRAFT", "APPROVED", "REVOKED"])
        expect(statusOf(applyApprovals(ledger, [revoked]), FP_A)).toBe("MATURE")
        expect(validateApprovedManifest({ manifest: revoked, ledger, snapshot }).reasons).toContain("MANIFEST_NOT_ACTIONABLE")
    })

    it("binds an approval to one exact batch, not to a candidate name", () => {
        const { ledger, snapshot } = ready()
        const single = draftApprovalManifest({ ledger, snapshot, candidateKeys: [FP_A], createdAt: T0 }).manifest!
        const pair = draftApprovalManifest({ ledger, snapshot, candidateKeys: [FP_A, FP_B], createdAt: T0 }).manifest!
        expect(single.approvalId).not.toBe(pair.approvalId)
        expect(single.batchEvidenceDigest).not.toBe(pair.batchEvidenceDigest)
        expect(batchEvidenceDigestOf(snapshot, [FP_A, FP_B])).toBe(batchEvidenceDigestOf(snapshot, [FP_B, FP_A]))
    })

    it("collapses an append-only manifest log to the latest state per approval id", () => {
        const { ledger, snapshot } = ready()
        const draft = draftApprovalManifest({ ledger, snapshot, candidateKeys: [FP_A], createdAt: T0 }).manifest!
        const approved = approveManifest({ manifest: draft, ledger, snapshot, at: T0 + DAY }).manifest
        const revoked = revokeManifest(approved, T0 + 2 * DAY)
        const latest = latestManifests([draft, approved, revoked])
        expect(latest).toHaveLength(1)
        expect(latest[0].humanApprovalStatus).toBe("REVOKED")
    })
})
