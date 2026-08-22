// ParentLab PL-R3 - the transfer quarantine ledger. Pure, offline, deterministic, read-only.
//
// A recommendation is a reading of one moment. This file turns a sequence of those readings into a
// history, and answers the only question that matters before an irreversible action: has this
// Veteran been independently eligible, on unchanged evidence, often enough to be worth asking a
// human about.
//
// Three rules do the work.
//
// Maturity counts SNAPSHOTS, not runs. A snapshot is one device roster walk, keyed by its roster scan
// id. Re-running the CLI over the same documents, rebuilding the same report, or replaying the same
// JSON produces the same scan id and replaces the earlier observation instead of incrementing
// anything. Only a genuinely new walk can move the counter.
//
// Any change in the semantic evidence restarts the run. Not "reduces confidence" - restarts. If the
// dominator set turned over, the confidence moved, a reason code appeared, the protection strings
// changed or the factor inventory shifted, the previous observations were about different evidence
// and cannot be counted toward the same claim. Three consecutive snapshots therefore means three
// walks that agreed byte for byte about why the transfer is safe.
//
// A blocker outranks a run. The listed safety facts do not merely reset the counter, they hold the
// entry at BLOCKED for as long as they are true, because they are not "different evidence" but
// "evidence that says no".
//
// The fold never emits APPROVED. That status is projected from an approval manifest by
// `applyApprovals`, so the ledger stays a statement about evidence and the manifest stays the only
// place a human decision is recorded.

import {
    DEFAULT_REQUIRED_CONSECUTIVE_ELIGIBLE_SNAPSHOTS,
    PARENTLAB_QUARANTINE_SCHEMA,
    PARENTLAB_QUARANTINE_SCHEMA_VERSION,
    QUARANTINE_STATUSES,
    REQUIRED_APPROVAL_CONFIDENCE,
    type AdvisorSnapshot,
    type ApprovalStatus,
    type QuarantineEntry,
    type QuarantineLedger,
    type QuarantinePolicy,
    type QuarantineSnapshotSummary,
    type QuarantineStatus,
    type QuarantineStatusCounts,
    type QuarantineStatusReason,
    type SnapshotCandidate,
    type TransferApprovalManifest,
} from "./quarantineTypes.ts"
import { TARGET_PROFILE_IDS } from "./retentionTargets.ts"
import { RETENTION_CONFIDENCE_RANK, RETENTION_STATES, type RetentionConfidence, type RetentionState } from "./retentionTypes.ts"

/**
 * The most protective state and the lowest confidence across a candidate's target profiles.
 *
 * A per-target document must never be summarized by whichever profile happens to sort first: a
 * Veteran that is SAFE_TO_TRANSFER for Mile and HARD_PROTECT for Long is a HARD_PROTECT Veteran, and
 * reporting the Mile verdict as "the" state is precisely how a single-target read becomes a wrong
 * account-wide conclusion.
 */
function worstState(candidate: SnapshotCandidate | null): RetentionState | null {
    if (!candidate || candidate.perTarget.length === 0) return null
    let best = candidate.perTarget[0].state
    for (const t of candidate.perTarget) if (RETENTION_STATES.indexOf(t.state) < RETENTION_STATES.indexOf(best)) best = t.state
    return best
}

function lowestConfidence(candidate: SnapshotCandidate | null): RetentionConfidence | null {
    if (!candidate || candidate.perTarget.length === 0) return null
    let low = candidate.perTarget[0].confidence
    for (const t of candidate.perTarget) if (RETENTION_CONFIDENCE_RANK[t.confidence] < RETENTION_CONFIDENCE_RANK[low]) low = t.confidence
    return low
}

/** The policy a ledger is built with. Every field is recorded on the document it produces. */
export const DEFAULT_QUARANTINE_POLICY: QuarantinePolicy = {
    requiredConsecutiveEligibleSnapshots: DEFAULT_REQUIRED_CONSECUTIVE_ELIGIBLE_SNAPSHOTS,
    requiredConfidence: REQUIRED_APPROVAL_CONFIDENCE,
    requiredTargetProfiles: TARGET_PROFILE_IDS,
}

function summarize(snapshot: AdvisorSnapshot): QuarantineSnapshotSummary {
    const candidates = [...snapshot.candidates.values()]
    return {
        snapshotId: snapshot.snapshotId,
        rosterScanId: snapshot.rosterScanId,
        rosterFingerprint: snapshot.rosterFingerprint,
        protectionScanId: snapshot.protectionScanId,
        observedAt: snapshot.observedAt,
        targetProfiles: snapshot.targetProfiles,
        accountWide: snapshot.accountWide,
        rosterTrusted: snapshot.rosterTrusted,
        defects: snapshot.defects,
        candidateCount: candidates.length,
        trackedCount: candidates.filter((c) => c.tracked).length,
        eligibleCount: candidates.filter((c) => c.eligible).length,
        digest: snapshot.digest,
    }
}

/**
 * Deduplicates and orders the snapshot list.
 *
 * Ordering is by observation time and then by scan id, so the fold does not depend on the order the
 * caller happened to pass files in. A repeated scan id is a re-observation of one walk: the LAST
 * occurrence wins (an evidence refresh over the same roster read is legitimate), and the repeat is
 * recorded rather than silently dropped so a suspicious replay is visible in the ledger.
 */
function orderSnapshots(snapshots: readonly AdvisorSnapshot[]): { ordered: readonly AdvisorSnapshot[]; duplicates: readonly string[] } {
    const byScan = new Map<string, AdvisorSnapshot>()
    const duplicates: string[] = []
    for (const s of snapshots) {
        if (byScan.has(s.rosterScanId)) duplicates.push(s.rosterScanId)
        byScan.set(s.rosterScanId, s)
    }
    const ordered = [...byScan.values()].sort((a, b) => (a.observedAt ?? 0) - (b.observedAt ?? 0) || (a.rosterScanId < b.rosterScanId ? -1 : a.rosterScanId > b.rosterScanId ? 1 : 0))
    return { ordered, duplicates: [...new Set(duplicates)].sort() }
}

/** Mutable accumulator for one candidate across the fold. Never exposed. */
interface Accumulator {
    firstEligibleAt: number | null
    lastEligibleAt: number | null
    consecutive: number
    eligibleSnapshotIds: string[]
    lastEligibleIndex: number
    lastEligibleDigest: string | null
    digestChangedOnLatest: boolean
    observations: number
    latest: SnapshotCandidate | null
    latestSnapshot: AdvisorSnapshot | null
}

function newAccumulator(): Accumulator {
    return {
        firstEligibleAt: null,
        lastEligibleAt: null,
        consecutive: 0,
        eligibleSnapshotIds: [],
        lastEligibleIndex: -2,
        lastEligibleDigest: null,
        digestChangedOnLatest: false,
        observations: 0,
        latest: null,
        latestSnapshot: null,
    }
}

/** The status and its reasons, from the accumulated history and the latest observation. */
function resolveStatus(acc: Accumulator, policy: QuarantinePolicy, presentInLatest: boolean): { status: QuarantineStatus; reasons: readonly QuarantineStatusReason[] } {
    if (!presentInLatest) return { status: "EXPIRED", reasons: ["ABSENT_FROM_LATEST_ROSTER"] }

    const latest = acc.latest as SnapshotCandidate
    if (latest.blockers.length > 0) return { status: "BLOCKED", reasons: latest.blockers }

    const reasons: QuarantineStatusReason[] = []
    if (acc.digestChangedOnLatest) reasons.push("EVIDENCE_DIGEST_CHANGED")
    if (acc.consecutive >= policy.requiredConsecutiveEligibleSnapshots) {
        reasons.push("MATURITY_REACHED")
        return { status: "MATURE", reasons: [...reasons].sort() }
    }
    reasons.push("MATURITY_NOT_REACHED")
    if (acc.eligibleSnapshotIds.length <= 1 && acc.observations <= 1) {
        reasons.push("FIRST_OBSERVATION")
        return { status: "OBSERVED", reasons: [...reasons].sort() }
    }
    return { status: "QUARANTINED", reasons: [...reasons].sort() }
}

/**
 * Folds an ordered snapshot sequence into the quarantine ledger.
 *
 * Deterministic by construction: no clock is read, the snapshot order is derived from the snapshots
 * themselves rather than from the caller's argument order, and every collection is sorted by a stable
 * key. The same set of snapshots produces a byte-identical document however it is supplied.
 */
export function buildQuarantineLedger(snapshots: readonly AdvisorSnapshot[], policy: QuarantinePolicy = DEFAULT_QUARANTINE_POLICY): QuarantineLedger {
    const { ordered, duplicates } = orderSnapshots(snapshots)
    const accumulators = new Map<string, Accumulator>()

    for (let i = 0; i < ordered.length; i++) {
        const snapshot = ordered[i]
        for (const [key, candidate] of snapshot.candidates) {
            // Only a tracked candidate opens a ledger entry. A Veteran PL-R2 never put on the
            // transfer side is not "not yet mature", it is simply not a transfer candidate, and
            // recording all 257 as quarantine entries would bury the ones that are.
            if (!candidate.tracked && !accumulators.has(key)) continue
            let acc = accumulators.get(key)
            if (!acc) {
                acc = newAccumulator()
                accumulators.set(key, acc)
            }
            acc.observations++
            acc.latest = candidate
            acc.latestSnapshot = snapshot
            acc.digestChangedOnLatest = false

            if (!candidate.eligible) {
                acc.consecutive = 0
                acc.lastEligibleDigest = null
                continue
            }
            const contiguous = acc.lastEligibleIndex === i - 1
            const sameEvidence = acc.lastEligibleDigest === candidate.evidenceDigest
            if (contiguous && sameEvidence) {
                acc.consecutive++
            } else {
                if (contiguous && !sameEvidence && acc.lastEligibleDigest !== null) acc.digestChangedOnLatest = true
                acc.consecutive = 1
            }
            acc.lastEligibleIndex = i
            acc.lastEligibleDigest = candidate.evidenceDigest
            acc.eligibleSnapshotIds.push(snapshot.snapshotId)
            if (acc.firstEligibleAt === null) acc.firstEligibleAt = snapshot.observedAt
            acc.lastEligibleAt = snapshot.observedAt
        }
    }

    const latestSnapshot = ordered.length > 0 ? ordered[ordered.length - 1] : null
    const entries: QuarantineEntry[] = []
    for (const key of [...accumulators.keys()].sort()) {
        const acc = accumulators.get(key) as Accumulator
        const inLatest = latestSnapshot?.candidates.get(key) ?? null
        // The latest observation is what the status is decided on. When the candidate is gone from
        // the newest snapshot, `acc.latest` still holds the last time it WAS seen, which is exactly
        // the reference an EXPIRED entry has to keep.
        const latest = inLatest ?? acc.latest
        const { status, reasons } = resolveStatus({ ...acc, latest: inLatest ?? acc.latest }, policy, inLatest !== null)
        entries.push({
            candidateKey: key,
            candidateRef: (latest as SnapshotCandidate).ref,
            firstEligibleAt: acc.firstEligibleAt,
            lastEligibleAt: acc.lastEligibleAt,
            consecutiveEligibleSnapshots: inLatest === null ? 0 : acc.consecutive,
            eligibleSnapshotIds: [...acc.eligibleSnapshotIds],
            latestState: worstState(latest),
            latestTargetProfiles: latest ? latest.perTarget.map((t) => t.targetProfile) : [],
            latestConfidence: lowestConfidence(latest),
            latestEvidenceDigest: latest ? latest.evidenceDigest : null,
            latestReasonCodes: latest ? [...new Set(latest.perTarget.flatMap((t) => [...t.hardProtectReasons, ...t.gateReasons, ...t.riskReasons]))].sort() : [],
            latestDominators: latest ? [...new Set(latest.perTarget.flatMap((t) => t.dominatorFingerprints))].sort() : [],
            latestSubstitutes: latest ? [...new Set(latest.perTarget.flatMap((t) => t.substituteFingerprints))].sort() : [],
            status,
            statusReasons: reasons,
            lastSeenRosterSnapshot: (inLatest ? latestSnapshot?.rosterScanId : acc.latestSnapshot?.rosterScanId) ?? null,
            lastSeenAdvisorSnapshot: (inLatest ? latestSnapshot?.snapshotId : acc.latestSnapshot?.snapshotId) ?? null,
        })
    }

    return {
        schema: PARENTLAB_QUARANTINE_SCHEMA,
        schemaVersion: PARENTLAB_QUARANTINE_SCHEMA_VERSION,
        policy,
        snapshots: ordered.map(summarize),
        latestSnapshotId: latestSnapshot?.snapshotId ?? null,
        duplicateSnapshotsIgnored: duplicates,
        entries,
        counts: countsOf(entries),
    }
}

function countsOf(entries: readonly QuarantineEntry[]): QuarantineStatusCounts {
    return Object.fromEntries(QUARANTINE_STATUSES.map((s) => [s, entries.filter((e) => e.status === s).length])) as QuarantineStatusCounts
}

/** Manifest states whose candidates are genuinely spoken for. A revoked or stale manifest is not. */
const BINDING_APPROVAL_STATUSES: ReadonlySet<ApprovalStatus> = new Set<ApprovalStatus>(["APPROVED"])

/**
 * Projects approval manifests onto a ledger, so a candidate a human has authorized reads APPROVED.
 *
 * Deliberately a separate pass. The fold states what the evidence supports; a manifest states what a
 * human decided; keeping them apart means a decision can never be mistaken for a measurement, and
 * revoking a manifest restores the evidence-only view exactly.
 *
 * Only entries the fold left MATURE are promoted. If the evidence moved under an approved manifest,
 * the entry stays BLOCKED or EXPIRED here and the manifest's own revalidation reports it as stale.
 */
export function applyApprovals(ledger: QuarantineLedger, manifests: readonly TransferApprovalManifest[]): QuarantineLedger {
    const approved = new Set<string>()
    for (const m of manifests) if (BINDING_APPROVAL_STATUSES.has(m.humanApprovalStatus)) for (const key of m.candidateKeys) approved.add(key)
    if (approved.size === 0) return ledger

    const entries = ledger.entries.map((e) =>
        e.status === "MATURE" && approved.has(e.candidateKey) ? { ...e, status: "APPROVED" as QuarantineStatus, statusReasons: [...new Set([...e.statusReasons, "APPROVED_BY_MANIFEST" as QuarantineStatusReason])].sort() } : e,
    )
    return { ...ledger, entries, counts: countsOf(entries) }
}

/** The ledger entry for one candidate, or null. */
export function ledgerEntry(ledger: QuarantineLedger, candidateKey: string): QuarantineEntry | null {
    return ledger.entries.find((e) => e.candidateKey === candidateKey) ?? null
}
