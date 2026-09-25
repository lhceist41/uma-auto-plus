// ParentLab PL-R2a - the read side of the on-device `veteran_protection` records. Pure, offline,
// deterministic, tolerant of malformed lines like every other corpus reader.
//
// Protection in this game is DERIVED, never read as its own field: there is no lock concept, only two
// user-mutable markers that block a Veteran from being released - a favorite icon and a memo. The
// device probe establishes the account-wide POPULATION of each (empty / non-empty). A nonempty
// partition has no member census. This module binds one such probe to a trusted roster snapshot
// and derives each Veteran's favorite / memo / protection state.
//
// The one rule it exists to enforce: a protection state is only ever positive when the evidence can
// support it. An empty partition proves every Veteran is outside it; a non-empty partition that was
// not enumerated proves only that SOME Veteran is inside it, so the rest stay UNKNOWN, and UNKNOWN is
// treated as protected downstream. Nothing here maps unknown to not-protected.

import { rosterBindingDigest, type RosterSnapshot } from "./roster.ts"

export const PARENTLAB_PROTECTION_SCHEMA = "parent_lab_protection" as const
export const PARENTLAB_PROTECTION_SCHEMA_VERSION = 2 as const

/** A partition's account-wide size class, mirrored from the Kotlin `ProtectionPopulation`. */
export type ProtectionPopulation = "empty" | "nonempty" | "unknown"

/** How the device probe ended, mirrored from the Kotlin `ProtectionScanOutcome`. Only "complete" is
 * trustworthy; every other value keeps the whole inventory UNKNOWN. */
export type ProtectionScanOutcome = "complete" | "nonempty_partition_census_unavailable" | "precondition_failed" | "ui_unexpected" | "partition_set_failed" | "restore_failed" | "invalid"

/** The OK-button reading a population was derived from, kept as raw evidence. */
export type ApplyButtonState = "enabled" | "disabled" | "unknown"

/** One `veteran_protection` record as written by the device probe. */
export interface VeteranProtectionRecord {
    readonly type: "veteran_protection"
    readonly schemaVersion: number
    readonly rosterBindingVersion: number | null
    readonly rosterScanId: string | null
    readonly rosterDigest: string | null
    readonly scanId: string
    readonly startedAt: number | null
    readonly completedAt: number | null
    readonly registeredUsed: number | null
    readonly registeredCapacity: number | null
    readonly filtersOffConfirmed: boolean | null
    readonly favoritePopulation: ProtectionPopulation
    readonly favoriteApplyState: ApplyButtonState
    readonly favoriteBaselineVerified: boolean
    readonly filterBaselineEvidenceVersion: number | null
    readonly favoriteBaselineReadings: unknown
    readonly memoPopulation: ProtectionPopulation
    readonly memoApplyState: ApplyButtonState
    readonly memoBaselineVerified: boolean
    readonly memoBaselineReadings: unknown
    readonly enumerationPerformed: boolean | null
    /** Fingerprints of favorited Veterans, populated only when a non-empty favorite partition was
     * enumerated. Empty on an account with no favorites. */
    readonly favoritedFingerprints: unknown
    readonly memoFingerprints: unknown
    readonly restoredFiltersOff: boolean
    readonly outcome: ProtectionScanOutcome
    readonly app: string | null
    readonly screenWidth: number | null
    readonly screenHeight: number | null
    readonly file?: string
    readonly lineNumber?: number
}

export type ProtectionRecordCandidate =
    | { readonly kind: "record"; readonly record: VeteranProtectionRecord }
    | { readonly kind: "malformed"; readonly file?: string; readonly lineNumber: number }

export interface ParsedProtectionRecords {
    readonly records: readonly VeteranProtectionRecord[]
    readonly malformedRecords: number
    readonly latestCandidate: ProtectionRecordCandidate | null
}

export type FavoriteState = "favorite" | "not_favorite" | "unknown"
export type MemoState = "has_memo" | "no_memo" | "unknown"
export type DerivedProtectionState = "protected" | "not_protected" | "unknown"

/** One Veteran's derived protection, from the probe and the snapshot it binds to. */
export interface DerivedProtection {
    readonly favoriteState: FavoriteState
    readonly memoState: MemoState
    readonly protectionState: DerivedProtectionState
}

const POPULATIONS = new Set<ProtectionPopulation>(["empty", "nonempty", "unknown"])
const OUTCOMES = new Set<ProtectionScanOutcome>(["complete", "nonempty_partition_census_unavailable", "precondition_failed", "ui_unexpected", "partition_set_failed", "restore_failed"])
const APPLY_STATES = new Set<ApplyButtonState>(["enabled", "disabled", "unknown"])

function num(v: unknown): number | null {
    if (v === null || v === undefined || v === "") return null
    const n = Number(v)
    return Number.isFinite(n) ? n : null
}

function exactInteger(v: unknown): number | null {
    return typeof v === "number" && Number.isSafeInteger(v) ? v : null
}

function str(v: unknown): string | null {
    return typeof v === "string" && v.length > 0 ? v : null
}

function population(v: unknown): ProtectionPopulation {
    return typeof v === "string" && POPULATIONS.has(v as ProtectionPopulation) ? (v as ProtectionPopulation) : "unknown"
}

function applyState(v: unknown): ApplyButtonState {
    return typeof v === "string" && APPLY_STATES.has(v as ApplyButtonState) ? (v as ApplyButtonState) : "unknown"
}

/**
 * Parses a `veteran_protection` JSONL corpus in append order. Every nonblank line remains a candidate,
 * including malformed JSON, so later unreadable evidence supersedes older negative evidence.
 */
export function parseProtectionRecords(text: string, file?: string): ParsedProtectionRecords {
    const records: VeteranProtectionRecord[] = []
    let malformedRecords = 0
    let latestCandidate: ProtectionRecordCandidate | null = null
    const lines = text.split("\n")
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        if (!line) continue
        latestCandidate = { kind: "malformed", file, lineNumber: i }
        let obj: any
        try {
            obj = JSON.parse(line)
        } catch {
            malformedRecords++
            continue
        }
        if (typeof obj !== "object" || obj === null || obj.type !== "veteran_protection") {
            malformedRecords++
            continue
        }
        const scanId = str(obj.scanId)
        const outcome = String(obj.outcome ?? "")
        const valid = scanId !== null && OUTCOMES.has(outcome as ProtectionScanOutcome)
        if (!valid) malformedRecords++
        const record: VeteranProtectionRecord = {
            type: "veteran_protection",
            schemaVersion: exactInteger(obj.schemaVersion) ?? 0,
            rosterBindingVersion: exactInteger(obj.rosterBindingVersion),
            rosterScanId: str(obj.rosterScanId),
            rosterDigest: str(obj.rosterDigest),
            scanId: scanId ?? "",
            startedAt: num(obj.startedAt),
            completedAt: num(obj.completedAt),
            registeredUsed: exactInteger(obj.registeredUsed),
            registeredCapacity: exactInteger(obj.registeredCapacity),
            filtersOffConfirmed: typeof obj.filtersOffConfirmed === "boolean" ? obj.filtersOffConfirmed : null,
            favoritePopulation: population(obj.favoritePopulation),
            favoriteApplyState: applyState(obj.favoriteApplyState),
            favoriteBaselineVerified: obj.favoriteBaselineVerified === true,
            filterBaselineEvidenceVersion: exactInteger(obj.filterBaselineEvidenceVersion),
            favoriteBaselineReadings: obj.favoriteBaselineReadings,
            memoPopulation: population(obj.memoPopulation),
            memoApplyState: applyState(obj.memoApplyState),
            memoBaselineVerified: obj.memoBaselineVerified === true,
            memoBaselineReadings: obj.memoBaselineReadings,
            enumerationPerformed: typeof obj.enumerationPerformed === "boolean" ? obj.enumerationPerformed : null,
            favoritedFingerprints: obj.favoritedFingerprints,
            memoFingerprints: obj.memoFingerprints,
            restoredFiltersOff: obj.restoredFiltersOff === true,
            outcome: valid ? (outcome as ProtectionScanOutcome) : "invalid",
            app: str(obj.app),
            screenWidth: num(obj.screenWidth),
            screenHeight: num(obj.screenHeight),
            file,
            lineNumber: i,
        }
        records.push(record)
        latestCandidate = { kind: "record", record }
    }
    return { records, malformedRecords, latestCandidate }
}

/** Returns the last protection candidate in this append stream. Its timestamp is diagnostic only;
 * a later failed, invalid or malformed entry blocks reuse of older empty evidence. */
export function latestProtectionRecord(parsed: ParsedProtectionRecords): VeteranProtectionRecord | null {
    return parsed.latestCandidate?.kind === "record" ? parsed.latestCandidate.record : null
}

/** Why a protection inventory is not usable. Empty when it is. */
export type ProtectionInventoryDefect =
    | "no_protection_record"
    | "probe_not_complete"
    | "filters_not_restored"
    | "roster_untrusted"
    | "roster_count_mismatch"
    | "capacity_invalid"
    | "binding_invalid"
    | "roster_binding_ineligible"
    | "partition_invalid"

export interface ProtectionInventory {
    readonly schema: typeof PARENTLAB_PROTECTION_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_PROTECTION_SCHEMA_VERSION
    readonly protectionScanId: string | null
    readonly rosterScanId: string
    readonly rosterDigest: string | null
    /** The probe is complete and bound to this trusted roster's scan ID and content digest. */
    readonly compatible: boolean
    /** Per-fingerprint derived state. Only fingerprints from the snapshot appear. */
    readonly byFingerprint: ReadonlyMap<string, DerivedProtection>
    readonly counts: {
        readonly favorite: number
        readonly notFavorite: number
        readonly favoriteUnknown: number
        readonly hasMemo: number
        readonly noMemo: number
        readonly memoUnknown: number
        readonly protected: number
        readonly notProtected: number
        readonly protectionUnknown: number
    }
    readonly defects: readonly ProtectionInventoryDefect[]
}

/** Combines a favorite and a memo state into a protection verdict. Protected when either marker is
 * present; not-protected only when BOTH are proven absent; unknown whenever either is unknown. */
function protectionFrom(fav: FavoriteState, memo: MemoState): DerivedProtectionState {
    if (fav === "favorite" || memo === "has_memo") return "protected"
    if (fav === "not_favorite" && memo === "no_memo") return "not_protected"
    return "unknown"
}

/**
 * Binds one protection probe to a trusted roster snapshot and derives every identified Veteran's
 * protection state. When the probe is missing, not complete, did not restore filters, or does not
 * describe the same roster as the snapshot, the inventory is marked incompatible and every state is
 * UNKNOWN - never silently not-protected.
 */
export function buildProtectionInventory(record: VeteranProtectionRecord | null, snapshot: RosterSnapshot): ProtectionInventory {
    const defects: ProtectionInventoryDefect[] = []
    const digest = rosterBindingDigest(snapshot)
    if (!record) defects.push("no_protection_record")
    if (record && record.outcome !== "complete") defects.push("probe_not_complete")
    if (record && !record.restoredFiltersOff) defects.push("filters_not_restored")
    if (!snapshot.trustedComplete) defects.push("roster_untrusted")
    if (digest === null) defects.push("roster_binding_ineligible")
    if (record && (!Number.isSafeInteger(record.registeredUsed) || record.registeredUsed === null || record.registeredUsed <= 0 || record.registeredUsed !== snapshot.registeredUsed)) {
        defects.push("roster_count_mismatch")
    }
    if (record && (record.registeredCapacity === null || !Number.isSafeInteger(record.registeredCapacity) || record.registeredCapacity <= 0 || record.registeredCapacity > 2_147_483_647 ||
        record.registeredUsed === null || record.registeredCapacity < record.registeredUsed ||
        record.registeredCapacity !== snapshot.registeredCapacity)) defects.push("capacity_invalid")
    if (record && (record.schemaVersion !== 2 || record.rosterBindingVersion !== 1 || record.rosterScanId !== snapshot.scanId || record.rosterDigest !== digest || !/^[0-9a-f]{32}$/.test(record.rosterDigest ?? "") || record.filtersOffConfirmed !== true)) defects.push("binding_invalid")
    const partitionValid = (pop: ProtectionPopulation, apply: ApplyButtonState, members: unknown): boolean =>
        pop === "empty" && apply === "disabled" && Array.isArray(members) && members.length === 0
    const dimensions = ["track", "distance", "style", "attribute_sparks", "aptitude_sparks", "unique_sparks", "common_sparks", "favorites", "memo"]
    const fullNeutral = (value: unknown): boolean => typeof value === "object" && value !== null && !Array.isArray(value) &&
        Object.keys(value).length === dimensions.length && dimensions.every((name) => (value as Record<string, unknown>)[name] === "neutral")
    if (record && (record.filterBaselineEvidenceVersion !== 1 || !record.favoriteBaselineVerified || !record.memoBaselineVerified ||
        !fullNeutral(record.favoriteBaselineReadings) || !fullNeutral(record.memoBaselineReadings) ||
        !partitionValid(record.favoritePopulation, record.favoriteApplyState, record.favoritedFingerprints) ||
        !partitionValid(record.memoPopulation, record.memoApplyState, record.memoFingerprints) || record.enumerationPerformed !== false)) defects.push("partition_invalid")
    const compatible = defects.length === 0

    const byFingerprint = new Map<string, DerivedProtection>()
    const counts = { favorite: 0, notFavorite: 0, favoriteUnknown: 0, hasMemo: 0, noMemo: 0, memoUnknown: 0, protected: 0, notProtected: 0, protectionUnknown: 0 }

    for (const entry of snapshot.entries) {
        const fp = entry.rosterFingerprint
        if (!fp) continue
        let fav: FavoriteState = "unknown"
        let memo: MemoState = "unknown"
        if (compatible && record) {
            fav = "not_favorite"
            memo = "no_memo"
        }
        const protectionState = protectionFrom(fav, memo)
        byFingerprint.set(fp, { favoriteState: fav, memoState: memo, protectionState })

        if (fav === "not_favorite") counts.notFavorite++
        else counts.favoriteUnknown++
        if (memo === "no_memo") counts.noMemo++
        else counts.memoUnknown++
        if (protectionState === "protected") counts.protected++
        else if (protectionState === "not_protected") counts.notProtected++
        else counts.protectionUnknown++
    }

    return {
        schema: PARENTLAB_PROTECTION_SCHEMA,
        schemaVersion: PARENTLAB_PROTECTION_SCHEMA_VERSION,
        protectionScanId: record?.scanId ?? null,
        rosterScanId: snapshot.scanId,
        rosterDigest: compatible ? digest : null,
        compatible,
        byFingerprint,
        counts,
        defects,
    }
}
