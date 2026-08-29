// ParentLab Manual Capacity Triage - Slice 2 contract: the Capacity Coverage Exposure Ledger.
// Pure, offline, deterministic, read-only. A human-review safety layer, not a value model.
//
// Slice 1 answered "which owned Veterans are candidates for a HUMAN capacity review?" and tallied why
// the rest fell out. Slice 2 answers a different, narrower question about the SAME pool:
//
//   If Veterans are removed from the eligible manual-review pool, what coverage does the account
//   provably lose, and which coverage slots are structurally protected from any review outcome?
//
// It never values, ranks, scores, tiers, recommends a transfer, or mutates anything. It partitions the
// carriers of each coverage slot into the eligible pool (exposed) versus outside it (anchored), and
// reports the structure so a human can see the exposure before acting on Slice 1's admissions.
//
// Three boundaries are load-bearing and mirror Slice 1's:
//   - The schema string is its own constant, so retention, Slice 1 and quarantine readers reject a
//     coverage document structurally rather than mis-parsing it.
//   - The exposure enum shares no member with the retention states or SAFE_TO_TRANSFER. "Exposed" is a
//     coverage-structure fact ("every observed carrier is inside the review pool"), never a value or
//     transfer-safety claim.
//   - There is no score, weight, tier, rank, priority, order, targetFreeSlots, recommendation or
//     safeToTransfer field, and no executor/mutation field. This slice describes structure only.

import type { CapacityAdmission } from "./capacityTypes.ts"

/** Schema discriminator for the coverage document. Distinct from every other ParentLab schema so a
 * persistence reader cannot confuse it with a retention, Slice 1 capacity, or quarantine document. */
export const PARENTLAB_CAPACITY_COVERAGE_SCHEMA = "parent_lab_capacity_coverage" as const
/** Version 1: coverage exposure accounting only. Valuation is deferred until the shared Factor Value
 * Domain lands, so no white subfamily, affinity, rebuildability or active-racer utility is modelled. */
export const PARENTLAB_CAPACITY_COVERAGE_SCHEMA_VERSION = 1 as const
/** Human-readable document kind, carried alongside the schema for defense in depth. */
export const PARENTLAB_CAPACITY_COVERAGE_KIND = "CAPACITY_COVERAGE_EXPOSURE" as const

/** The star floors a factor slot is measured at. A factor observed at N stars is a carrier for every
 * floor <= N, matching the retention scarcity index's carriersByMinStars semantics. */
export const COVERAGE_STAR_FLOORS = [1, 2, 3] as const
export type CoverageStarFloor = (typeof COVERAGE_STAR_FLOORS)[number]

/**
 * The exposure classification for one coverage slot.
 *
 * These describe coverage STRUCTURE only. None of them says a Veteran is valuable, redundant, or safe
 * to transfer, and none overlaps a retention state.
 */
export const COVERAGE_EXPOSURES = [
    /** >= 1 carrier is EXCLUDED from manual review AND has trusted evidence, so no review outcome can
     * reduce this slot to zero. The slot is structurally protected regardless of what a human does. */
    "ANCHORED",
    /** 2+ observed carriers and every observed carrier is inside the eligible pool. Releasing the whole
     * pool would cost this coverage; releasing part of it might not. */
    "FULLY_EXPOSED",
    /** Exactly 1 observed carrier and it is inside the eligible pool. A possible observed last-copy. */
    "FULLY_EXPOSED_SOLE",
    /** Trusted carrier evidence is insufficient to classify the slot. Never read as "not at risk". */
    "UNMEASURED",
] as const
export type CoverageExposure = (typeof COVERAGE_EXPOSURES)[number]

/**
 * How strong a scarcity statement the current capture coverage licenses.
 *
 * ACCOUNT is permitted only at complete account-wide coverage. Otherwise every factor count is an
 * observed lower bound, and the wording must never imply account-wide uniqueness.
 */
export const COVERAGE_CLAIM_STRENGTHS = ["ACCOUNT", "OBSERVED_LOWER_BOUND"] as const
export type CoverageClaimStrength = (typeof COVERAGE_CLAIM_STRENGTHS)[number]

/**
 * A per-Veteran, coverage-structural risk classification. An enum, never a score and never an order:
 * two Veterans with the same value are not ranked against each other by it.
 */
export const LAST_COPY_RISKS = [
    /** This Veteran is the sole observed carrier of at least one factor slot, or its own character. */
    "SOLE_OBSERVED_CARRIER",
    /** No sole exposure, but it participates in at least one shared fully-exposed factor slot. */
    "SHARED_FULLY_EXPOSED",
    /** No exposed slot was observed for this Veteran. */
    "NO_EXPOSED_SLOT_OBSERVED",
    /** This Veteran lacks the trusted factor evidence needed to classify factor exposure at all. */
    "UNMEASURED",
] as const
export type LastCopyRisk = (typeof LAST_COPY_RISKS)[number]

/**
 * The limits/degradations a coverage document may carry. Six are permanent in v1 (the valuation the
 * shared domain will later supply); two are conditional on the input's coverage state.
 */
export const COVERAGE_LIMITS = [
    /** Capture coverage is not account-wide, so factor claims are observed lower bounds. Conditional. */
    "COVERAGE_INCOMPLETE",
    /** Some self-factor reads did not resolve onto the canonical domain. Conditional. */
    "UNRESOLVED_FACTOR_READS",
    /** White factor subfamily is not derived in v1; every white slot reports whiteSubfamily = null. */
    "WHITE_SUBFAMILY_NOT_AVAILABLE",
    /** Affinity is not decoded in this repository and is not consumed. */
    "AFFINITY_NOT_DECODED",
    /** Independent Training rebuildability / replacement difficulty is not measured here. */
    "REBUILDABILITY_NOT_MEASURED",
    /** A Veteran's active-racer utility is not modelled. */
    "ACTIVE_RACER_VALUE_NOT_MODELLED",
    /** Cross-target applicability of a factor is not modelled. */
    "TARGET_APPLICABILITY_NOT_MODELLED",
    /** Coverage is scoped to the single selected target profile; other profiles are not aggregated. */
    "SINGLE_TARGET_PROFILE_SCOPE",
] as const
export type CoverageLimitCode = (typeof COVERAGE_LIMITS)[number]

/** The permanent v1 limits, always present regardless of the input. */
export const PERMANENT_COVERAGE_LIMITS: readonly CoverageLimitCode[] = [
    "WHITE_SUBFAMILY_NOT_AVAILABLE",
    "AFFINITY_NOT_DECODED",
    "REBUILDABILITY_NOT_MEASURED",
    "ACTIVE_RACER_VALUE_NOT_MODELLED",
    "TARGET_APPLICABILITY_NOT_MODELLED",
    "SINGLE_TARGET_PROFILE_SCOPE",
]

/** One limit with deterministic display text. No clock reads, no environment reads. */
export interface CoverageLimit {
    readonly code: CoverageLimitCode
    readonly reason: string
}

/** A fully-keyed histogram over the exposure enum, so every classification is always present. */
export type CoverageExposureCounts = Readonly<Record<CoverageExposure, number>>

/**
 * One factor coverage slot, keyed by factorKey + starFloor.
 *
 * observedCarriers, admittedCarriers and anchoredCarriers describe the account-observed carrier set at
 * this star floor and its partition. `whiteSubfamily` is null in v1 (the shared domain is deferred).
 * `characterBound` is the structural fact that a unique (green) factor is bound to its character; it is
 * not a value judgment.
 */
export interface FactorCoverageSlot {
    readonly factorKey: string
    readonly kind: string
    readonly canonicalName: string
    /** Null for every slot in v1: white factor subfamily is not derived here (WHITE_SUBFAMILY_NOT_AVAILABLE). */
    readonly whiteSubfamily: string | null
    /** kind === "unique": a green factor is character-bound. A structural fact, not a value judgment. */
    readonly characterBound: boolean
    readonly starFloor: CoverageStarFloor
    readonly observedCarriers: number
    readonly admittedCarriers: number
    readonly anchoredCarriers: number
    readonly exposure: CoverageExposure
    readonly claimStrength: CoverageClaimStrength
    readonly explanation: string
}

/**
 * One character coverage slot, keyed on normalized character identity.
 *
 * observedCarriers here is ROSTER MEMBERSHIP (Veterans of this character on the roster), not
 * factor-capture coverage. The outfit list is context; strict retention already owns the per-outfit
 * SOLE_CHARACTER_OUTFIT_SOURCE verdict, so this slice deliberately does not emit an outfit-level one.
 */
export interface CharacterCoverageSlot {
    readonly characterKey: string
    readonly observedCarriers: number
    readonly admittedCarriers: number
    readonly anchoredCarriers: number
    readonly exposure: CoverageExposure
    /** Sorted, distinct outfit names observed for this character. Context only. */
    readonly outfits: readonly string[]
    readonly explanation: string
}

/**
 * The single target coverage slot for the selected report profile. Profile-scoped only: this slice
 * never aggregates multiple target profiles (SINGLE_TARGET_PROFILE_SCOPE is always emitted).
 */
export interface TargetCoverageSlot {
    readonly targetProfile: string
    /** Veterans clearing this profile's aptitude gate, from the retention coverage summary. */
    readonly clearingCarriers: number
    readonly admittedCarriers: number
    readonly anchoredCarriers: number
    readonly exposure: CoverageExposure
    readonly explanation: string
}

/**
 * One eligible Veteran's coverage exposure. Emitted for admitted Veterans only: an excluded Veteran is
 * outside the review pool by construction, so it cannot be the thing a review outcome removes.
 *
 * `lastCopyRisk` is an enum, never a score or an order. The slot-key lists use `factorKey@starFloor`.
 */
export interface VeteranCoverageExposure {
    readonly rosterFingerprint: string | null
    readonly scanIndex: number
    readonly character: string | null
    readonly outfit: string | null
    /** Carried verbatim from Slice 1. Always ELIGIBLE here, since only admitted Veterans are emitted. */
    readonly admission: CapacityAdmission
    /** Factor slots (factorKey@starFloor) where this Veteran is the sole observed carrier. Sorted. */
    readonly soleCarrierSlots: readonly string[]
    /** Shared fully-exposed factor slots (factorKey@starFloor) this Veteran participates in. Sorted. */
    readonly fullyExposedSharedSlots: readonly string[]
    /** True when this Veteran is the sole carrier of a fully-exposed-sole character slot. */
    readonly soleCharacterSlot: boolean
    /** Count of exposed factor slots this Veteran participates in, keyed by factor kind. Sorted keys. */
    readonly exposureByKind: Readonly<Record<string, number>>
    readonly lastCopyRisk: LastCopyRisk
    readonly explanation: string
}

/**
 * The Capacity Coverage Exposure Ledger: coverage exposure over one roster snapshot under one target
 * profile lens, built in-process from the SAME retention report Slice 1 triaged. Structure only.
 */
export interface CapacityCoverageDocument {
    readonly schema: typeof PARENTLAB_CAPACITY_COVERAGE_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_CAPACITY_COVERAGE_SCHEMA_VERSION
    readonly kind: typeof PARENTLAB_CAPACITY_COVERAGE_KIND
    readonly targetProfile: string
    readonly rosterScanId: string
    readonly rosterFingerprint: string
    readonly protectionScanId: string | null
    /** Newest input observation time from the retention document, NOT a clock read. */
    readonly generatedAt: number | null
    /** The Slice 1 capacity schema version this document was built against, for provenance. */
    readonly capacitySchemaVersion: number
    /** False when the roster snapshot is untrusted. An unusable document must never read as "safe". */
    readonly usable: boolean
    /** Size of the eligible manual-review pool (Slice 1 admitted). 0 when unusable. */
    readonly poolSize: number
    readonly excludedSize: number
    readonly rosterCount: number
    readonly coverage: number
    readonly accountWide: boolean
    readonly unresolvedFactorReads: number
    /** Veterans that anchor nothing because their own factor evidence is missing/untrusted. */
    readonly recordsWithoutTrustedFactors: number
    /** Records that resolved to no usable character key. */
    readonly unkeyedRecords: number
    readonly factorSlots: readonly FactorCoverageSlot[]
    readonly characterSlots: readonly CharacterCoverageSlot[]
    readonly targetSlots: readonly TargetCoverageSlot[]
    readonly exposures: readonly VeteranCoverageExposure[]
    readonly factorExposureCounts: CoverageExposureCounts
    readonly characterExposureCounts: CoverageExposureCounts
    readonly limits: readonly CoverageLimit[]
}

/** A fully-zeroed exposure histogram, so every exposure key is always present. */
export function emptyCoverageExposureCounts(): Record<CoverageExposure, number> {
    const out = {} as Record<CoverageExposure, number>
    for (const exposure of COVERAGE_EXPOSURES) out[exposure] = 0
    return out
}

/** Canonical slot-key string for a factor slot: `factorKey@starFloor`. Deterministic and stable. */
export function factorSlotKey(factorKey: string, starFloor: number): string {
    return `${factorKey}@${starFloor}`
}

/**
 * Structural guard: is this a Capacity Coverage Exposure document?
 *
 * The mirror of isCapacityTriageDocument and retentionReportsOf's schema check. A coverage document
 * must never be mistaken for a retention snapshot or a Slice 1 capacity document, and vice versa.
 */
export function isCapacityCoverageDocument(document: unknown): document is CapacityCoverageDocument {
    if (typeof document !== "object" || document === null) return false
    const doc = document as { schema?: unknown; kind?: unknown }
    return doc.schema === PARENTLAB_CAPACITY_COVERAGE_SCHEMA && doc.kind === PARENTLAB_CAPACITY_COVERAGE_KIND
}
