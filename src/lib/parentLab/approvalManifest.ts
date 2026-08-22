// ParentLab PL-R3 - the manual transfer approval manifest. Pure, offline, deterministic, read-only
// with respect to the account: nothing in this file transfers, releases, favorites or touches the
// device.
//
// A manifest is a one-time approval intent bound to one exact semantic batch. It is NOT a standing
// permission, and the distinction is the whole point: the danger with an irreversible action is not
// that the wrong decision gets made, it is that a right decision gets reused after the thing it was
// about has changed. So the manifest pins the exact evidence the human was shown - a digest per
// candidate and one over the batch - and any drift in that evidence makes it STALE.
//
// The lifecycle is deliberately narrow:
//
//   DRAFT     created from a validated batch. Not permission to do anything.
//   APPROVED  a human ran an explicit command naming this manifest, and revalidation passed.
//   REVOKED   withdrawn by a human. Terminal.
//   STALE     revalidation refused it. Terminal: a stale manifest is never repaired in place, a new
//             one is drafted against the current evidence and approved on its own merits.
//   CONSUMED  reserved for a future executor. Nothing in this repository ever sets it.
//
// There is no "approve all" and no auto-approval anywhere in this module. Every transition takes an
// explicit approval id, and `approveManifest` re-runs the full batch validation before it will move
// a manifest out of DRAFT.

import { contentHash128 } from "./identity.ts"
import { batchEvidenceDigestOf, validateTransferBatch } from "./quarantineBatch.ts"
import { ledgerEntry } from "./quarantineLedger.ts"
import { sameCandidate } from "./quarantineSnapshot.ts"
import {
    PARENTLAB_QUARANTINE_SCHEMA,
    PARENTLAB_QUARANTINE_SCHEMA_VERSION,
    type AdvisorSnapshot,
    type ApprovalInvalidationReason,
    type ApprovalStatus,
    type ApprovalValidation,
    type ApprovalVerdict,
    type BatchValidation,
    type QuarantineLedger,
    type TransferApprovalManifest,
} from "./quarantineTypes.ts"

/** Statuses a revalidation can still act on. Everything else is terminal and reports as such. */
const ACTIONABLE: ReadonlySet<ApprovalStatus> = new Set<ApprovalStatus>(["DRAFT", "APPROVED"])

/**
 * Reasons that mean the batch is wrong rather than merely out of date.
 *
 * The split matters for what a human is told. STALE says "re-draft this against current evidence";
 * BLOCKED says "this batch should not exist as written". Both refuse the approval, and neither is
 * repairable in place.
 */
const BLOCKING_REASONS: ReadonlySet<ApprovalInvalidationReason> = new Set<ApprovalInvalidationReason>([
    "CANDIDATE_ABSENT",
    "IDENTITY_MISMATCH",
    "CANDIDATE_NOT_MATURE",
    "BATCH_VALIDATION_FAILED",
    "SNAPSHOT_NOT_TRUSTED",
    "MANIFEST_NOT_ACTIONABLE",
])

/** Deterministic approval id from the batch's meaning plus its explicit creation time. */
export function approvalIdFor(batchEvidenceDigest: string, createdAt: number): string {
    return contentHash128(JSON.stringify({ v: 1, batchEvidenceDigest, createdAt }))
}

export interface DraftApprovalInput {
    readonly ledger: QuarantineLedger
    readonly snapshot: AdvisorSnapshot
    readonly candidateKeys: readonly string[]
    /** Explicit creation time. Supplied by the caller so the manifest is reproducible from its inputs. */
    readonly createdAt: number
}

/** A draft attempt: the manifest when the batch validated, and the validation either way. */
export interface DraftApprovalResult {
    readonly manifest: TransferApprovalManifest | null
    readonly validation: BatchValidation
}

/**
 * Drafts a manifest for a candidate set.
 *
 * A draft is refused outright when the batch does not validate. Producing a DRAFT that a human could
 * look at and approve, over a batch already known to be unsafe, would put the entire burden of the
 * refusal on them noticing a warning line.
 */
export function draftApprovalManifest(input: DraftApprovalInput): DraftApprovalResult {
    const { ledger, snapshot, candidateKeys, createdAt } = input
    const validation = validateTransferBatch(ledger, snapshot, candidateKeys)
    if (!validation.ok) return { manifest: null, validation }

    const keys = validation.candidateKeys
    const approvalId = approvalIdFor(validation.batchEvidenceDigest, createdAt)
    return {
        manifest: {
            schema: PARENTLAB_QUARANTINE_SCHEMA,
            schemaVersion: PARENTLAB_QUARANTINE_SCHEMA_VERSION,
            approvalId,
            createdAt,
            sourceAdvisorSnapshot: snapshot.snapshotId,
            sourceRosterScanId: snapshot.rosterScanId,
            sourceRosterFingerprint: snapshot.rosterFingerprint,
            candidateRefs: keys.map((k) => (snapshot.candidates.get(k) as { ref: TransferApprovalManifest["candidateRefs"][number] }).ref),
            candidateKeys: keys,
            candidateEvidenceDigests: keys.map((k) => snapshot.candidates.get(k)?.evidenceDigest ?? ""),
            batchEvidenceDigest: validation.batchEvidenceDigest,
            targetProfiles: snapshot.targetProfiles,
            humanApprovalStatus: "DRAFT",
            approvedAt: null,
            revokedAt: null,
            history: [{ at: createdAt, from: "DRAFT", to: "DRAFT", verdict: null, reasons: [], againstSnapshotId: snapshot.snapshotId }],
        },
        validation,
    }
}

export interface RevalidateInput {
    readonly manifest: TransferApprovalManifest
    readonly ledger: QuarantineLedger
    readonly snapshot: AdvisorSnapshot
}

/**
 * Revalidates a manifest against the latest trusted evidence. Pure: returns a verdict, never a state.
 *
 * This is the function both `approveManifest` (Part 12, before DRAFT -> APPROVED) and any future
 * executor (Part 13, immediately before acting) must call. An APPROVED status on its own proves only
 * that the account looked a certain way at approval time.
 */
export function validateApprovedManifest(input: RevalidateInput): ApprovalValidation {
    const { manifest, ledger, snapshot } = input
    const reasons = new Set<ApprovalInvalidationReason>()
    const detail: Record<string, string[]> = {}
    const add = (reason: ApprovalInvalidationReason, keys: readonly string[]): void => {
        reasons.add(reason)
        detail[reason] = [...new Set([...(detail[reason] ?? []), ...keys])].sort()
    }

    if (!ACTIONABLE.has(manifest.humanApprovalStatus)) add("MANIFEST_NOT_ACTIONABLE", [manifest.humanApprovalStatus])
    if (!snapshot.rosterTrusted || !snapshot.accountWide || snapshot.defects.length > 0) add("SNAPSHOT_NOT_TRUSTED", snapshot.defects)
    if (manifest.targetProfiles.join(",") !== snapshot.targetProfiles.join(",")) add("TARGET_PROFILE_SET_CHANGED", [manifest.targetProfiles.join(","), snapshot.targetProfiles.join(",")])

    for (let i = 0; i < manifest.candidateKeys.length; i++) {
        const key = manifest.candidateKeys[i]
        const candidate = snapshot.candidates.get(key) ?? null
        if (!candidate) {
            add("CANDIDATE_ABSENT", [key])
            continue
        }
        if (!sameCandidate(manifest.candidateRefs[i], candidate.ref)) add("IDENTITY_MISMATCH", [key])
        if (candidate.evidenceDigest !== manifest.candidateEvidenceDigests[i]) add("EVIDENCE_DIGEST_CHANGED", [key])
        const entry = ledgerEntry(ledger, key)
        if (!entry || (entry.status !== "MATURE" && entry.status !== "APPROVED")) add("CANDIDATE_NOT_MATURE", [key])
    }

    if (batchEvidenceDigestOf(snapshot, manifest.candidateKeys) !== manifest.batchEvidenceDigest) add("BATCH_DIGEST_CHANGED", [manifest.batchEvidenceDigest])

    const batchValidation = validateTransferBatch(ledger, snapshot, manifest.candidateKeys)
    if (!batchValidation.ok) add("BATCH_VALIDATION_FAILED", batchValidation.rejections.map((r) => r.reason))

    const list = [...reasons].sort()
    const verdict: ApprovalVerdict = list.length === 0 ? "VALID" : list.some((r) => BLOCKING_REASONS.has(r)) ? "BLOCKED" : "STALE"
    return { approvalId: manifest.approvalId, verdict, reasons: list, detail, againstSnapshotId: snapshot.snapshotId, batchValidation }
}

export interface ApproveInput extends RevalidateInput {
    /** Explicit approval time, so the transition is reproducible from its inputs. */
    readonly at: number
}

/** The outcome of an approval attempt: the manifest as it now stands, plus why. */
export interface ApprovalTransition {
    readonly manifest: TransferApprovalManifest
    readonly validation: ApprovalValidation
}

/**
 * Moves a manifest from DRAFT to APPROVED, and only ever on a clean revalidation.
 *
 * A refused approval is recorded as STALE rather than left in DRAFT. A DRAFT that has already failed
 * revalidation once is a trap: it stays in the list looking approvable, and the next person to run
 * the command gets no signal that the evidence underneath it has already moved.
 */
export function approveManifest(input: ApproveInput): ApprovalTransition {
    const { manifest, at } = input
    const validation = validateApprovedManifest(input)
    if (manifest.humanApprovalStatus !== "DRAFT") {
        return { manifest, validation }
    }
    const to: ApprovalStatus = validation.verdict === "VALID" ? "APPROVED" : "STALE"
    return {
        manifest: {
            ...manifest,
            humanApprovalStatus: to,
            approvedAt: to === "APPROVED" ? at : null,
            history: [...manifest.history, { at, from: "DRAFT", to, verdict: validation.verdict, reasons: validation.reasons, againstSnapshotId: input.snapshot.snapshotId }],
        },
        validation,
    }
}

/** Withdraws a manifest. Terminal, and always allowed: refusing to revoke would be the unsafe default. */
export function revokeManifest(manifest: TransferApprovalManifest, at: number): TransferApprovalManifest {
    if (manifest.humanApprovalStatus === "REVOKED") return manifest
    return {
        ...manifest,
        humanApprovalStatus: "REVOKED",
        revokedAt: at,
        history: [...manifest.history, { at, from: manifest.humanApprovalStatus, to: "REVOKED", verdict: null, reasons: [], againstSnapshotId: null }],
    }
}

/**
 * Marks an APPROVED manifest STALE after its evidence moved.
 *
 * Called by the report path so a stored manifest cannot sit at APPROVED while the account underneath
 * it has changed. The transition is one-way: nothing in this module moves a manifest back.
 */
export function markStaleIfInvalid(input: RevalidateInput, at: number): ApprovalTransition {
    const validation = validateApprovedManifest(input)
    const { manifest } = input
    if (manifest.humanApprovalStatus !== "APPROVED" || validation.verdict === "VALID") return { manifest, validation }
    return {
        manifest: {
            ...manifest,
            humanApprovalStatus: "STALE",
            history: [...manifest.history, { at, from: "APPROVED", to: "STALE", verdict: validation.verdict, reasons: validation.reasons, againstSnapshotId: input.snapshot.snapshotId }],
        },
        validation,
    }
}

/**
 * Collapses an append-only manifest log into the current state of each manifest.
 *
 * The store is append-only so every transition stays auditable; the last record for an approval id
 * is its current state. Records are folded in file order, which is the order they were written.
 */
export function latestManifests(records: readonly TransferApprovalManifest[]): readonly TransferApprovalManifest[] {
    const byId = new Map<string, TransferApprovalManifest>()
    for (const m of records) byId.set(m.approvalId, m)
    return [...byId.values()].sort((a, b) => a.createdAt - b.createdAt || (a.approvalId < b.approvalId ? -1 : 1))
}
