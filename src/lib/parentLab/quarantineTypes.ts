// ParentLab PL-R3 transfer quarantine - the shared contract. Pure, offline, deterministic, read-only.
//
// PL-R2 answers "what does the evidence suggest about this Veteran right now". PL-R3 answers a
// strictly harder question: "has that suggestion held still long enough, across enough independent
// evidence, that a human could be asked to authorize an irreversible transfer of exactly this set".
// The two must never be collapsed. A one-time SAFE_TO_TRANSFER label is a reading of one snapshot;
// an approval is a permission to destroy inheritance that cannot be bought back.
//
// Nothing in this stage transfers, releases, favorites, memos or touches the device. It produces a
// ledger, a batch verdict and an approval artifact, and it stops there. The executor that would act
// on an approval is deliberately not implemented (see `ApprovedTransferBatch`).
//
// Four separations run through the module and are load-bearing:
//   recommendation vs maturity  - PL-R2 state is an input here, never a verdict;
//   identity vs content digest  - a fingerprint names a Veteran, a digest names its evidence, and
//                                 using either for the other's job is how the wrong animal gets sent;
//   individually safe vs safe   - a set of individually safe removals can still take the account's
//     as a set                    last carrier of something, so a batch is validated as a batch;
//   approval vs execution       - an approval is a one-time intent bound to one exact semantic batch,
//                                 and it goes stale the moment the account underneath it moves.

import type { RetentionConfidence, RetentionState, ReplacementDifficulty, ScarcityClaim, SelfFactorRef } from "./retentionTypes.ts"

/** Schema discriminator + version for every PL-R3 artifact (ledger and approval manifest alike). */
export const PARENTLAB_QUARANTINE_SCHEMA = "parent_lab_transfer_quarantine" as const
export const PARENTLAB_QUARANTINE_SCHEMA_VERSION = 1 as const

/**
 * The oldest retention document PL-R3 will read.
 *
 * Version 1 documents carry no identity multiplicity, no final stats and no self factor list, so a
 * candidate reference could not be built from them and a post-batch coverage recompute would have
 * nothing to recompute from. Reading one anyway would mean inferring the missing evidence, which is
 * exactly the failure this stage exists to prevent, so an older document is refused instead.
 */
export const MIN_SUPPORTED_RETENTION_SCHEMA_VERSION = 2

/** Confidence a candidate must hold, under every target profile, to be approval-eligible. */
export const REQUIRED_APPROVAL_CONFIDENCE: RetentionConfidence = "HIGH"

/**
 * How many consecutive eligible snapshots a candidate needs before it may be drafted for approval.
 *
 * Three, because the failure being defended against is a transient misread rather than a wrong rule:
 * one flaky OCR pass, one incomplete Inspiration capture, one scarcity index built while a capture
 * was missing. Two would clear on a single repeated fault; three requires the fault to reproduce
 * across three independent device passes, and the evidence to be byte-identical each time.
 */
export const DEFAULT_REQUIRED_CONSECUTIVE_ELIGIBLE_SNAPSHOTS = 3

/** The knobs a ledger build was run with. Persisted with the ledger so a stored verdict is readable. */
export interface QuarantinePolicy {
    readonly requiredConsecutiveEligibleSnapshots: number
    readonly requiredConfidence: RetentionConfidence
    /** Every target profile a snapshot must contain before any candidate in it can be eligible. */
    readonly requiredTargetProfiles: readonly string[]
}

/**
 * A reference to one Veteran on one account, safe to carry across snapshots.
 *
 * The primary identity is `rosterFingerprint`, the trusted content fingerprint the PL-R1b identity
 * pass produces. It is NOT a game-stable UUID: the game exposes no Veteran id, so the fingerprint is
 * derived from what the roster row displays, and two Veterans whose displayed content is identical
 * produce the same fingerprint. `occurrenceOrdinal` records which of those rows was seen, and is
 * deliberately not treated as a disambiguator - see `AMBIGUOUS_ROSTER_IDENTITY`.
 *
 * The remaining fields are re-identification evidence, not identity. They exist so a later stage can
 * check that the fingerprint still points at a Veteran that looks the same, and refuse when it does
 * not.
 */
export interface CandidateRef {
    readonly rosterFingerprint: string
    /** 0-based position among same-fingerprint entries in the source snapshot, by scan index. Null
     * when the fingerprint occurred exactly once, where there is nothing to disambiguate. */
    readonly occurrenceOrdinal: number | null
    readonly character: string | null
    readonly outfit: string | null
    readonly rank: string | null
    /** The five final stats as read, keyed as the roster writes them. An unread stat stays null. */
    readonly stats: Readonly<Record<string, number | null>>
}

/** Ledger statuses. `APPROVED` is projected from an approval manifest, never produced by the fold. */
export const QUARANTINE_STATUSES = [
    /** Tracked, and this is the only snapshot it has ever been seen eligible in. */
    "OBSERVED",
    /** Tracked across more than one snapshot, not blocked, not yet mature. */
    "QUARANTINED",
    /** Eligible in enough consecutive snapshots, on unchanged evidence, with no blocker. */
    "MATURE",
    /** Present in the ledger's history but absent from the latest snapshot's roster. */
    "EXPIRED",
    /** Present in the latest snapshot with at least one safety fact that disqualifies it. */
    "BLOCKED",
    /** Covered by an approval manifest currently in APPROVED state. */
    "APPROVED",
] as const
export type QuarantineStatus = (typeof QUARANTINE_STATUSES)[number]

/**
 * Why an entry holds its status. Every one is a fact about the evidence, never a judgement.
 *
 * The blocker set is checked independently rather than inferred from PL-R2's state label. A label is
 * a summary of rules that ran on one machine at one time; re-deriving the facts underneath it is
 * what makes a stale or subtly wrong document visible instead of authoritative.
 */
export const QUARANTINE_STATUS_REASONS = [
    /** First snapshot this candidate has been seen eligible in. */
    "FIRST_OBSERVATION",
    /** Eligible, but for fewer consecutive snapshots than the policy requires. */
    "MATURITY_NOT_REACHED",
    /** Eligible for at least the required number of consecutive snapshots, on unchanged evidence. */
    "MATURITY_REACHED",
    /** The semantic evidence changed since the previous eligible snapshot, so the run restarted. */
    "EVIDENCE_DIGEST_CHANGED",
    /** The candidate's fingerprint is not in the latest snapshot's roster at all. */
    "ABSENT_FROM_LATEST_ROSTER",
    /** More than one entry in the snapshot carries this fingerprint, so it names no single Veteran. */
    "AMBIGUOUS_ROSTER_IDENTITY",
    /** The fingerprint is present but its character, outfit, rank or stats no longer match. */
    "IDENTITY_MISMATCH",
    /** The account marks the Veteran protected: it is favorited, has a memo, or its state is unknown. */
    "PROTECTED_ON_ACCOUNT",
    /** At least one merit protection rule fires on the Veteran under at least one target profile. */
    "HARD_PROTECT_PRESENT",
    /** The Veteran carries a factor no other Veteran carries at equal or better stars. */
    "UNIQUE_FACTOR_COVERAGE_PRESENT",
    /** Its Inspiration capture is missing, incomplete, or did not fully resolve. */
    "SPARK_EVIDENCE_UNTRUSTED",
    /** PL-R2 no longer calls it SAFE_TO_TRANSFER under every target profile. */
    "ADVISOR_STATE_NOT_ELIGIBLE",
    /** PL-R2 calls it QUARANTINE_TRANSFER, which is a review-queue state and not an approval state. */
    "ADVISOR_STATE_NOT_APPROVAL_ELIGIBLE",
    /** Confidence is below the level approval requires under at least one target profile. */
    "CONFIDENCE_BELOW_REQUIRED",
    /** Capture coverage does not support an account-wide scarcity claim. */
    "ACCOUNT_COVERAGE_NOT_ACCOUNT_WIDE",
    /** No peer dominates it under at least one target profile, so redundancy is unproven there. */
    "DOMINATOR_SET_EMPTY",
    /** Replacement difficulty is not one the transfer side accepts. */
    "REPLACEMENT_NOT_REPLACEABLE",
    /** The roster snapshot behind the latest observation is not trusted-complete. */
    "ROSTER_SNAPSHOT_UNTRUSTED",
    /** The snapshot does not carry a document for every required target profile. */
    "TARGET_PROFILE_COVERAGE_INCOMPLETE",
    /** An approval manifest in APPROVED state covers this candidate. */
    "APPROVED_BY_MANIFEST",
] as const
export type QuarantineStatusReason = (typeof QUARANTINE_STATUS_REASONS)[number]

/** Why a whole advisor snapshot cannot be used as evidence. Any defect makes every candidate in it
 * ineligible; the snapshot is still recorded, so a gap in the history is visible rather than silent. */
export const SNAPSHOT_DEFECTS = [
    /** The retention document predates the schema PL-R3 needs. */
    "RETENTION_SCHEMA_UNSUPPORTED",
    /** The documents in one snapshot disagree on which roster scan they describe. */
    "ROSTER_SNAPSHOT_MISMATCH",
    /** The documents in one snapshot cover different sets of roster entries. */
    "RECOMMENDATION_SET_MISMATCH",
    /** A required target profile has no document in this snapshot. */
    "TARGET_PROFILE_COVERAGE_INCOMPLETE",
    /** Two documents in one snapshot claim the same target profile. */
    "DUPLICATE_TARGET_PROFILE",
    /** The roster snapshot behind the documents is not trusted-complete. */
    "ROSTER_SNAPSHOT_UNTRUSTED",
    /** At least one roster entry never resolved to a fingerprint. */
    "UNRESOLVED_ROSTER_IDENTITY_PRESENT",
    /** The snapshot carries no recommendations at all. */
    "EMPTY_SNAPSHOT",
] as const
export type SnapshotDefect = (typeof SNAPSHOT_DEFECTS)[number]

/** One Veteran's PL-R2 verdict under one target profile, reduced to the safety-relevant fields. */
export interface CandidateTargetView {
    readonly targetProfile: string
    readonly state: RetentionState
    readonly confidence: RetentionConfidence
    readonly hardProtectReasons: readonly string[]
    readonly gateReasons: readonly string[]
    readonly riskReasons: readonly string[]
    /** Fingerprints of the peers that strictly dominate it: the redundancy proof. */
    readonly dominatorFingerprints: readonly string[]
    /** Fingerprints of peers that win the dimension comparison but failed a strict gate. */
    readonly substituteFingerprints: readonly string[]
    readonly replacementDifficulty: ReplacementDifficulty
    readonly observedUniqueFactorKeys: readonly string[]
    readonly scarcestClaim: ScarcityClaim
    readonly targetsCovered: readonly string[]
    readonly soleTargetCoverage: readonly string[]
}

/** One Veteran in one snapshot, merged across every target profile that snapshot carries. */
export interface SnapshotCandidate {
    readonly candidateKey: string
    readonly ref: CandidateRef
    readonly scanIndex: number
    readonly identityMultiplicity: number
    readonly favoriteState: string
    readonly protectionState: string
    /** Normalized character key, for the post-batch character coverage recompute. Null when unread. */
    readonly characterKey: string | null
    /** Every resolved self factor, or null when no trusted capture backs it. */
    readonly selfFactors: readonly SelfFactorRef[] | null
    /** Target profile ids whose aptitude gate it clears, unioned across the snapshot's documents. */
    readonly targetsCovered: readonly string[]
    /** One view per target profile in the snapshot, sorted by profile id. */
    readonly perTarget: readonly CandidateTargetView[]
    /** PL-R2 calls it SAFE_TO_TRANSFER or QUARANTINE_TRANSFER under at least one target profile. */
    readonly tracked: boolean
    /** Every independently re-derived safety fact that disqualifies it, sorted. */
    readonly blockers: readonly QuarantineStatusReason[]
    /** Tracked, and no blocker. The only state that accrues maturity. */
    readonly eligible: boolean
    /** Deterministic digest over the safety evidence above. Excludes clocks and scan ids. */
    readonly evidenceDigest: string
}

/** One roster snapshot's worth of advisor documents, normalized into comparable evidence. */
export interface AdvisorSnapshot {
    /** The roster scan id. Two documents built from one device walk are one snapshot, however many
     * times they are rebuilt or replayed. */
    readonly snapshotId: string
    readonly rosterScanId: string
    readonly rosterFingerprint: string
    readonly protectionScanId: string | null
    /** Newest input observation across the documents. Never a clock read. */
    readonly observedAt: number | null
    readonly targetProfiles: readonly string[]
    readonly accountWide: boolean
    readonly rosterTrusted: boolean
    readonly defects: readonly SnapshotDefect[]
    /** Every identified roster entry, keyed by candidate key, sorted on serialization. */
    readonly candidates: ReadonlyMap<string, SnapshotCandidate>
    /** Entries that never resolved to a fingerprint, and so cannot be referenced at all. */
    readonly unidentifiedEntries: number
    /** Deterministic digest over every candidate digest in the snapshot. */
    readonly digest: string
}

/** One tracked Veteran's quarantine history, folded over the ordered snapshot list. */
export interface QuarantineEntry {
    readonly candidateKey: string
    readonly candidateRef: CandidateRef

    /** Source observation time of the first eligible snapshot, never a clock read. Null when the
     * snapshot carried no observation time. */
    readonly firstEligibleAt: number | null
    readonly lastEligibleAt: number | null
    readonly consecutiveEligibleSnapshots: number
    readonly eligibleSnapshotIds: readonly string[]

    readonly latestState: RetentionState | null
    readonly latestTargetProfiles: readonly string[]
    readonly latestConfidence: RetentionConfidence | null

    readonly latestEvidenceDigest: string | null
    readonly latestReasonCodes: readonly string[]
    readonly latestDominators: readonly string[]
    readonly latestSubstitutes: readonly string[]

    readonly status: QuarantineStatus
    readonly statusReasons: readonly QuarantineStatusReason[]

    readonly lastSeenRosterSnapshot: string | null
    readonly lastSeenAdvisorSnapshot: string | null
}

/** A snapshot as recorded in the ledger: enough to audit the fold without re-reading the documents. */
export interface QuarantineSnapshotSummary {
    readonly snapshotId: string
    readonly rosterScanId: string
    readonly rosterFingerprint: string
    readonly protectionScanId: string | null
    readonly observedAt: number | null
    readonly targetProfiles: readonly string[]
    readonly accountWide: boolean
    readonly rosterTrusted: boolean
    readonly defects: readonly SnapshotDefect[]
    readonly candidateCount: number
    readonly trackedCount: number
    readonly eligibleCount: number
    readonly digest: string
}

export type QuarantineStatusCounts = Readonly<Record<QuarantineStatus, number>>

/** The persisted ledger. A pure fold over the snapshot list: same snapshots, same document. */
export interface QuarantineLedger {
    readonly schema: typeof PARENTLAB_QUARANTINE_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_QUARANTINE_SCHEMA_VERSION
    readonly policy: QuarantinePolicy
    /** Snapshots in fold order, oldest first. */
    readonly snapshots: readonly QuarantineSnapshotSummary[]
    readonly latestSnapshotId: string | null
    /** Roster scan ids supplied more than once. A repeat replaces the earlier observation of that
     * scan and never accrues maturity: replaying a document is not new evidence. */
    readonly duplicateSnapshotsIgnored: readonly string[]
    readonly entries: readonly QuarantineEntry[]
    readonly counts: QuarantineStatusCounts
}

/** Why a batch cannot be drafted. Reported per candidate where one is at fault, else batch-wide. */
export const BATCH_REJECTION_REASONS = [
    /** No candidates were named. */
    "EMPTY_BATCH",
    /** A candidate key appears more than once in the batch. */
    "DUPLICATE_CANDIDATE_IN_BATCH",
    /** The candidate has no ledger entry. */
    "CANDIDATE_NOT_IN_LEDGER",
    /** The candidate's ledger status is not MATURE. */
    "CANDIDATE_NOT_MATURE",
    /** The candidate is not in the latest snapshot's roster. */
    "CANDIDATE_NOT_IN_LATEST_SNAPSHOT",
    /** The candidate's fingerprint names more than one Veteran in the latest snapshot. */
    "AMBIGUOUS_ROSTER_IDENTITY",
    /** The candidate carries a merit protection rule under at least one target profile. */
    "HARD_PROTECT_IN_BATCH",
    /** Removing the batch would leave the candidate with no dominator under some target profile,
     * so its own redundancy proof does not survive the batch it is part of. */
    "DOMINATOR_REMOVED_BY_BATCH",
    /** Candidates in the batch dominate each other in a cycle, so no removal order is safe. */
    "CIRCULAR_SUBSTITUTE_DEPENDENCE",
    /** Removing the batch would take the account's last carrier of a factor at some star floor. */
    "BATCH_REMOVES_LAST_FACTOR_CARRIER",
    /** Removing the batch would take the account's last Veteran of a character. */
    "BATCH_REMOVES_LAST_CHARACTER_SOURCE",
    /** Removing the batch would leave a target profile with no Veteran clearing its gate. */
    "BATCH_REMOVES_LAST_TARGET_COVERAGE",
    /** The latest snapshot itself is defective or does not support account-wide claims. */
    "SNAPSHOT_NOT_TRUSTED",
] as const
export type BatchRejectionReason = (typeof BATCH_REJECTION_REASONS)[number]

/** One rejection, with the candidate at fault when there is one and the evidence behind it. */
export interface BatchRejection {
    readonly reason: BatchRejectionReason
    /** The candidate at fault, or null for a batch-wide rejection. */
    readonly candidateKey: string | null
    /** The specific keys, characters, profiles or peers the rejection is about, sorted. */
    readonly detail: readonly string[]
    readonly explanation: string
}

/** What the account would still carry after the batch was removed. Reported whether or not it passed. */
export interface RetainedCoverage {
    readonly rosterBefore: number
    readonly rosterAfter: number
    readonly distinctFactorsBefore: number
    readonly distinctFactorsAfter: number
    readonly charactersBefore: number
    readonly charactersAfter: number
    /** Target profile ids that still have at least one Veteran clearing their gate, sorted. */
    readonly targetsCoveredAfter: readonly string[]
}

/** The batch verdict. Pure: it reads a ledger and a snapshot and writes nothing. */
export interface BatchValidation {
    readonly ok: boolean
    readonly candidateKeys: readonly string[]
    readonly rejections: readonly BatchRejection[]
    readonly retainedCoverage: RetainedCoverage
    /** Deterministic digest over the batch's semantic evidence. Empty string when the batch is empty. */
    readonly batchEvidenceDigest: string
}

/** The manifest lifecycle. `CONSUMED` is defined for a future executor and never set by PL-R3. */
export const APPROVAL_STATUSES = ["DRAFT", "APPROVED", "REVOKED", "STALE", "CONSUMED"] as const
export type ApprovalStatus = (typeof APPROVAL_STATUSES)[number]

/** The verdict of revalidating a manifest against the latest evidence. */
export const APPROVAL_VERDICTS = ["VALID", "STALE", "BLOCKED"] as const
export type ApprovalVerdict = (typeof APPROVAL_VERDICTS)[number]

/** Why a manifest is not VALID. */
export const APPROVAL_INVALIDATION_REASONS = [
    /** A candidate named by the manifest is not in the latest snapshot. */
    "CANDIDATE_ABSENT",
    /** A candidate is present but its character, outfit, rank or stats no longer match the manifest. */
    "IDENTITY_MISMATCH",
    /** A candidate's ledger status is no longer MATURE. */
    "CANDIDATE_NOT_MATURE",
    /** A candidate's semantic evidence differs from the digest recorded in the manifest. */
    "EVIDENCE_DIGEST_CHANGED",
    /** The batch digest differs from the one recorded in the manifest. */
    "BATCH_DIGEST_CHANGED",
    /** The batch no longer passes cross-candidate validation. */
    "BATCH_VALIDATION_FAILED",
    /** The set of target profiles behind the latest snapshot differs from the manifest's. */
    "TARGET_PROFILE_SET_CHANGED",
    /** The latest snapshot is defective, untrusted, or does not support account-wide claims. */
    "SNAPSHOT_NOT_TRUSTED",
    /** The manifest is REVOKED, CONSUMED or already STALE, so there is nothing left to validate. */
    "MANIFEST_NOT_ACTIONABLE",
] as const
export type ApprovalInvalidationReason = (typeof APPROVAL_INVALIDATION_REASONS)[number]

/** One lifecycle transition, appended and never rewritten. */
export interface ApprovalHistoryEntry {
    readonly at: number
    readonly from: ApprovalStatus
    readonly to: ApprovalStatus
    readonly verdict: ApprovalVerdict | null
    readonly reasons: readonly ApprovalInvalidationReason[]
    /** The snapshot the transition was decided against, for audit. */
    readonly againstSnapshotId: string | null
}

/**
 * A one-time approval intent bound to one exact semantic batch.
 *
 * It is NOT a standing permission. `batchEvidenceDigest` and the per-candidate digests pin the exact
 * evidence the human was shown; any drift in that evidence makes the manifest STALE, and a future
 * executor must revalidate immediately before acting rather than trusting the status alone.
 */
export interface TransferApprovalManifest {
    readonly schema: typeof PARENTLAB_QUARANTINE_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_QUARANTINE_SCHEMA_VERSION
    readonly approvalId: string
    readonly createdAt: number
    readonly sourceAdvisorSnapshot: string
    readonly sourceRosterScanId: string
    readonly sourceRosterFingerprint: string
    readonly candidateRefs: readonly CandidateRef[]
    readonly candidateKeys: readonly string[]
    /** Per-candidate evidence digests, index-aligned with `candidateKeys`. */
    readonly candidateEvidenceDigests: readonly string[]
    readonly batchEvidenceDigest: string
    readonly targetProfiles: readonly string[]
    readonly humanApprovalStatus: ApprovalStatus
    readonly approvedAt: number | null
    readonly revokedAt: number | null
    readonly history: readonly ApprovalHistoryEntry[]
}

/** The result of revalidating a manifest. Pure: a verdict, never a mutation. */
export interface ApprovalValidation {
    readonly approvalId: string
    readonly verdict: ApprovalVerdict
    readonly reasons: readonly ApprovalInvalidationReason[]
    /** Candidate keys each reason applies to, keyed by reason and sorted. */
    readonly detail: Readonly<Record<string, readonly string[]>>
    readonly againstSnapshotId: string | null
    readonly batchValidation: BatchValidation | null
}

/**
 * The minimum a future destructive executor would have to be handed. Defined here so the boundary is
 * explicit; deliberately not implemented, and nothing in this repository produces or consumes one.
 *
 * Holding this object is still NOT sufficient to transfer anything. Every unresolved risk below is a
 * real gap between "this batch was approved" and "this exact Veteran is the row being acted on":
 *
 *   on-device re-identification - the game exposes no Veteran id. Immediately before acting, the
 *     executor would have to re-read the row it is about to act on and prove it matches the
 *     CandidateRef by content. Nothing here can do that; the roster walk identifies rows offline,
 *     after the fact.
 *   duplicate-content Veterans   - two Veterans with identical displayed content share a fingerprint.
 *     PL-R3 refuses to approve those, but an executor must re-check: a duplicate that appears AFTER
 *     approval makes an approved reference ambiguous again.
 *   UI ordering changes          - the roster is sorted by a mutable sort key, and registering a new
 *     Veteran shifts every position. A scan index is a position in one walk, never an address.
 *   transfer confirmation dialog - the dialog's exact text, layout and failure modes are undecoded in
 *     this repository. A misread confirmation is an irreversible action taken on a guess.
 *   post-transfer verification   - there is no designed way yet to prove the transfer removed the
 *     intended Veteran and only that one.
 *   rollback impossibility       - none of the above can be undone. There is no restore path, so
 *     every one of these gaps has to be closed before, not after.
 */
export interface ApprovedTransferBatch {
    readonly manifest: TransferApprovalManifest
    readonly revalidation: ApprovalValidation
    readonly candidateRefs: readonly CandidateRef[]
    readonly rosterScanId: string
    readonly rosterFingerprint: string
}
