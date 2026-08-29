// ParentLab Manual Capacity Triage - Slice 1 contract. Pure, offline, deterministic, read-only.
//
// This subsystem answers a different question from the Strict Retention Advisor. Retention asks
// "can we prove this exact Veteran is safe or redundant?" and fails closed toward protection.
// Capacity Triage asks "among Veterans that are not strictly protected, which are candidates for a
// HUMAN to review when roster capacity gets tight?" - and it never decides that a Veteran should be
// released. It only admits a Veteran into a human-review pool or excludes it with a reason.
//
// Three boundaries are load-bearing and deliberately mirror nothing in retention/quarantine:
//   - The document schema string is its own constant, so retentionReportsOf and the quarantine
//     readers reject it structurally rather than mis-parsing it as a retention snapshot.
//   - The admission enum (ELIGIBLE / EXCLUDED) shares no member with RETENTION_STATES. Admitted is
//     never "safe": SAFE_TO_TRANSFER is a retention verdict this stage neither produces nor implies.
//   - There is no score, no rank, no ledger, no maturity gate and no executor/mutation field. Slice 1
//     is counts-only: it discovers how large the eligible human-review pool is and why the rest fell out.
//
// The strict retention state is carried verbatim on every record as context. It is never reinterpreted:
// a HARD_PROTECT stays HARD_PROTECT even as this stage excludes it, and a SAFE_TO_TRANSFER stays
// SAFE_TO_TRANSFER even when a capacity-specific evidence gate excludes it from manual review.

import type { RetentionConfidence, RetentionState } from "./retentionTypes.ts"
import { RETENTION_STATES } from "./retentionTypes.ts"

/** Schema discriminator for the capacity document. Intentionally distinct from
 * PARENTLAB_RETENTION_SCHEMA and the quarantine schemas so persistence readers cannot confuse them. */
export const PARENTLAB_CAPACITY_SCHEMA = "parent_lab_capacity_triage" as const
/** Version 1: admission/exclusion facts only. No ranking, opportunity cost, or residual ledger. */
export const PARENTLAB_CAPACITY_SCHEMA_VERSION = 1 as const
/** Human-readable document kind, carried alongside the schema for defense in depth. */
export const PARENTLAB_CAPACITY_KIND = "MANUAL_CAPACITY_TRIAGE" as const

/**
 * The only two verdicts this slice produces.
 *
 * ELIGIBLE_FOR_MANUAL_REVIEW means "a human may consider this Veteran when capacity is tight", NOT
 * "this Veteran is safe to release". EXCLUDED_FROM_MANUAL_REVIEW means the capacity gate refused it,
 * with at least one reason attached. Neither overlaps semantically with SAFE_TO_TRANSFER.
 */
export const CAPACITY_ADMISSIONS = ["ELIGIBLE_FOR_MANUAL_REVIEW", "EXCLUDED_FROM_MANUAL_REVIEW"] as const
export type CapacityAdmission = (typeof CAPACITY_ADMISSIONS)[number]

/**
 * Why a Veteran is excluded from the manual-review pool. Every reason is a fact re-derived from the
 * retention document's per-Veteran evidence, not read off its state label alone. A Veteran may carry
 * more than one reason; the histogram counts reason occurrences, so its total can exceed the excluded
 * count. The reconciliation invariant is on the verdicts, not the histogram: admitted + excluded ==
 * rosterCount.
 */
export const CAPACITY_EXCLUSION_REASONS = [
    /** Strict retention hard-protect (state HARD_PROTECT or any hardProtectReason present). Precious,
     * never a capacity-review candidate. */
    "STRICT_HARD_PROTECT",
    /** Strict retention UNKNOWN: required evidence is incomplete/inconsistent, so admission is unsafe. */
    "STRICT_STATE_UNKNOWN",
    /** The roster entry never resolved to a fingerprint, or identity did not resolve at all. */
    "ROSTER_IDENTITY_UNRESOLVED",
    /** The fingerprint occurs more than once in the snapshot, so it does not name one Veteran. */
    "ROSTER_IDENTITY_AMBIGUOUS",
    /** The roster snapshot behind this record is not trusted-complete. */
    "ROSTER_SNAPSHOT_UNTRUSTED",
    /** The account itself marks this Veteran protected: favorited or memoed. Cannot be released in-game. */
    "PROTECTED_ON_ACCOUNT",
    /** In-game protection (favorite or memo) could not be excluded: the state is unknown/untrusted. */
    "PROTECTION_STATE_UNKNOWN",
    /** This Veteran's own factor (Inspiration) evidence is missing, incomplete or untrusted. */
    "SELF_FACTOR_EVIDENCE_UNTRUSTED",
] as const
export type CapacityExclusionReason = (typeof CAPACITY_EXCLUSION_REASONS)[number]

/** A histogram over every exclusion reason, always fully keyed. */
export type CapacityExclusionHistogram = Readonly<Record<CapacityExclusionReason, number>>

/** A distribution over every strict retention state, always fully keyed. */
export type CapacityStrictStateDistribution = Readonly<Record<RetentionState, number>>

/**
 * The normalized evidence view the gate reads for one Veteran. Every field is a boolean/string fact
 * lifted from the retention recommendation, never a score. Carried on the record for auditability so
 * a reviewer can see exactly which fact drove the verdict.
 */
export interface CapacityEvidenceView {
    /** The roster read resolved this entry to a fingerprint. */
    readonly identityResolved: boolean
    /** Entries in the same snapshot sharing this fingerprint. > 1 means the identity is ambiguous. */
    readonly identityMultiplicity: number
    /** The roster snapshot is trusted-complete. */
    readonly rosterTrusted: boolean
    /** Neither favorite nor protection state is unknown/untrusted. */
    readonly protectionKnown: boolean
    /** The account marks this Veteran protected (favorited or memoed). */
    readonly protectedOnAccount: boolean
    /** The effective favorite state after any protection probe was applied. */
    readonly favoriteState: string
    /** The effective protection state after any protection probe was applied. */
    readonly protectionState: string
    /** This Veteran's own factor evidence is present, complete and trusted. */
    readonly selfFactorEvidenceTrusted: boolean
    /** The strict advisor hard-protected this Veteran on its own merits. */
    readonly hardProtected: boolean
    /** The retention document's completeness score (0..1) for this Veteran, carried for context. */
    readonly dataCompletenessScore: number
}

/**
 * One Veteran's admission record. `exclusionReasons` is empty iff `admission` is ELIGIBLE. There is
 * deliberately no action, executor, target-slot or mutation field: a later slice, not this one, will
 * decide anything destructive, and even then only a human will.
 */
export interface CapacityTriageRecord {
    readonly rosterFingerprint: string | null
    readonly scanIndex: number
    readonly character: string | null
    readonly outfit: string | null
    readonly rank: string | null
    readonly identityMultiplicity: number
    readonly admission: CapacityAdmission
    /** Sorted, de-duplicated. Empty for an admitted Veteran. */
    readonly exclusionReasons: readonly CapacityExclusionReason[]
    /** The strict retention state, carried verbatim for context. Never reinterpreted by this stage. */
    readonly strictState: RetentionState
    /** The strict retention confidence, carried verbatim for context. */
    readonly strictConfidence: RetentionConfidence
    readonly evidence: CapacityEvidenceView
    /** Human-readable justification, assembled deterministically from the facts above. */
    readonly explanation: string
}

/** Roster-wide evidence completeness, carried for context. None of it gates admission per-Veteran. */
export interface CapacityEvidenceSummary {
    /** Roster entries carrying a fingerprint, from the retention scarcity index. */
    readonly identifiedRosterEntries: number
    /** Captures that are complete and whose self factor set fully resolved. */
    readonly capturedTrusted: number
    /** Captures present but not usable as inventory evidence. */
    readonly capturedUntrusted: number
    /** capturedTrusted / identifiedRosterEntries from the retention document. */
    readonly coverage: number
    /** True only at complete account-wide capture coverage. Context only: this stage does NOT gate on it. */
    readonly accountWide: boolean
    /** Every record's roster snapshot was trusted-complete. */
    readonly rosterTrusted: boolean
    /** Records whose own factor evidence was present, complete and trusted. */
    readonly recordsWithTrustedSelfFactors: number
    /** Records whose favorite AND protection state were both known/trusted. */
    readonly recordsProtectionKnown: number
}

/**
 * The Manual Capacity Triage document: admission/exclusion facts over one roster snapshot under one
 * target-profile lens. Counts-only. No ranking list.
 */
export interface CapacityTriageDocument {
    readonly schema: typeof PARENTLAB_CAPACITY_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_CAPACITY_SCHEMA_VERSION
    readonly kind: typeof PARENTLAB_CAPACITY_KIND
    /** The retention target-profile lens this triage was derived under, carried verbatim. */
    readonly targetProfile: string
    readonly rosterScanId: string
    readonly rosterFingerprint: string
    readonly protectionScanId: string | null
    /** Newest input observation time from the retention document, NOT a clock read. */
    readonly generatedAt: number | null
    readonly rosterCount: number
    readonly admittedCount: number
    readonly excludedCount: number
    readonly exclusionHistogram: CapacityExclusionHistogram
    readonly admittedStrictStateDistribution: CapacityStrictStateDistribution
    readonly excludedStrictStateDistribution: CapacityStrictStateDistribution
    readonly evidenceSummary: CapacityEvidenceSummary
    /** Preserves the retention document's recommendation order (roster scan order). */
    readonly records: readonly CapacityTriageRecord[]
}

/** A fully-zeroed exclusion histogram, so every reason key is always present. */
export function emptyExclusionHistogram(): Record<CapacityExclusionReason, number> {
    const out = {} as Record<CapacityExclusionReason, number>
    for (const reason of CAPACITY_EXCLUSION_REASONS) out[reason] = 0
    return out
}

/** A fully-zeroed strict-state distribution, so every state key is always present. */
export function emptyStrictStateDistribution(): Record<RetentionState, number> {
    const out = {} as Record<RetentionState, number>
    for (const state of RETENTION_STATES) out[state] = 0
    return out
}

/**
 * Structural guard: is this a Manual Capacity Triage document?
 *
 * The mirror of retentionReportsOf's schema check. A capacity document must never be mistaken for a
 * retention snapshot, and a retention document must never be mistaken for a capacity triage. Readers
 * on either side check their own schema and reject the other.
 */
export function isCapacityTriageDocument(document: unknown): document is CapacityTriageDocument {
    if (typeof document !== "object" || document === null) return false
    const doc = document as { schema?: unknown; kind?: unknown }
    return doc.schema === PARENTLAB_CAPACITY_SCHEMA && doc.kind === PARENTLAB_CAPACITY_KIND
}
