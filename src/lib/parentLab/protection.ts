// ParentLab PL-R2a - the read side of the on-device `veteran_protection` records. Pure, offline,
// deterministic, tolerant of malformed lines like every other corpus reader.
//
// Protection in this game is DERIVED, never read as its own field: there is no lock concept, only two
// user-mutable markers that block a Veteran from being released - a favorite icon and a memo. The
// device probe establishes the account-wide POPULATION of each (empty / non-empty) and, only when a
// partition is non-empty, which Veterans are in it. This module binds one such probe to a trusted
// roster snapshot and derives each Veteran's favorite / memo / protection state.
//
// The one rule it exists to enforce: a protection state is only ever positive when the evidence can
// support it. An empty partition proves every Veteran is outside it; a non-empty partition that was
// not enumerated proves only that SOME Veteran is inside it, so the rest stay UNKNOWN, and UNKNOWN is
// treated as protected downstream. Nothing here maps unknown to not-protected.

import type { RosterSnapshot } from "./roster.ts"

export const PARENTLAB_PROTECTION_SCHEMA = "parent_lab_protection" as const
export const PARENTLAB_PROTECTION_SCHEMA_VERSION = 1 as const

/** A partition's account-wide size class, mirrored from the Kotlin `ProtectionPopulation`. */
export type ProtectionPopulation = "empty" | "nonempty" | "unknown"

/** How the device probe ended, mirrored from the Kotlin `ProtectionScanOutcome`. Only "complete" is
 * trustworthy; every other value keeps the whole inventory UNKNOWN. */
export type ProtectionScanOutcome = "complete" | "precondition_failed" | "ui_unexpected" | "partition_set_failed" | "restore_failed"

/** The OK-button reading a population was derived from, kept as raw evidence. */
export type ApplyButtonState = "enabled" | "disabled" | "unknown"

/** One `veteran_protection` record as written by the device probe. */
export interface VeteranProtectionRecord {
    readonly type: "veteran_protection"
    readonly schemaVersion: number
    readonly scanId: string
    readonly startedAt: number | null
    readonly completedAt: number | null
    readonly registeredUsed: number | null
    readonly registeredCapacity: number | null
    readonly filtersOffConfirmed: boolean | null
    readonly favoritePopulation: ProtectionPopulation
    readonly favoriteApplyState: ApplyButtonState
    readonly memoPopulation: ProtectionPopulation
    readonly memoApplyState: ApplyButtonState
    readonly enumerationPerformed: boolean
    /** Fingerprints of favorited Veterans, populated only when a non-empty favorite partition was
     * enumerated. Empty on an account with no favorites. */
    readonly favoritedFingerprints: readonly string[]
    readonly memoFingerprints: readonly string[]
    readonly restoredFiltersOff: boolean
    readonly outcome: ProtectionScanOutcome
    readonly app: string | null
    readonly screenWidth: number | null
    readonly screenHeight: number | null
    readonly file?: string
    readonly lineNumber?: number
}

export interface ParsedProtectionRecords {
    readonly records: readonly VeteranProtectionRecord[]
    readonly malformedRecords: number
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
const OUTCOMES = new Set<ProtectionScanOutcome>(["complete", "precondition_failed", "ui_unexpected", "partition_set_failed", "restore_failed"])
const APPLY_STATES = new Set<ApplyButtonState>(["enabled", "disabled", "unknown"])

function num(v: unknown): number | null {
    if (v === null || v === undefined || v === "") return null
    const n = Number(v)
    return Number.isFinite(n) ? n : null
}

function str(v: unknown): string | null {
    return typeof v === "string" && v.length > 0 ? v : null
}

function stringArray(v: unknown): string[] {
    if (!Array.isArray(v)) return []
    return v.filter((x): x is string => typeof x === "string" && x.length > 0)
}

function population(v: unknown): ProtectionPopulation {
    return typeof v === "string" && POPULATIONS.has(v as ProtectionPopulation) ? (v as ProtectionPopulation) : "unknown"
}

function applyState(v: unknown): ApplyButtonState {
    return typeof v === "string" && APPLY_STATES.has(v as ApplyButtonState) ? (v as ApplyButtonState) : "unknown"
}

/**
 * Parses a `veteran_protection` JSONL corpus. Malformed lines are skipped and counted. A record
 * missing its `scanId` or carrying an unrecognised `outcome` is dropped rather than defaulted into
 * something that would read as a valid probe.
 */
export function parseProtectionRecords(text: string, file?: string): ParsedProtectionRecords {
    const records: VeteranProtectionRecord[] = []
    let malformedRecords = 0
    const lines = text.split("\n")
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        if (!line) continue
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
        if (!scanId || !OUTCOMES.has(outcome as ProtectionScanOutcome)) {
            malformedRecords++
            continue
        }
        records.push({
            type: "veteran_protection",
            schemaVersion: num(obj.schemaVersion) ?? 0,
            scanId,
            startedAt: num(obj.startedAt),
            completedAt: num(obj.completedAt),
            registeredUsed: num(obj.registeredUsed),
            registeredCapacity: num(obj.registeredCapacity),
            filtersOffConfirmed: typeof obj.filtersOffConfirmed === "boolean" ? obj.filtersOffConfirmed : null,
            favoritePopulation: population(obj.favoritePopulation),
            favoriteApplyState: applyState(obj.favoriteApplyState),
            memoPopulation: population(obj.memoPopulation),
            memoApplyState: applyState(obj.memoApplyState),
            enumerationPerformed: obj.enumerationPerformed === true,
            favoritedFingerprints: stringArray(obj.favoritedFingerprints),
            memoFingerprints: stringArray(obj.memoFingerprints),
            restoredFiltersOff: obj.restoredFiltersOff === true,
            outcome: outcome as ProtectionScanOutcome,
            app: str(obj.app),
            screenWidth: num(obj.screenWidth),
            screenHeight: num(obj.screenHeight),
            file,
            lineNumber: i,
        })
    }
    return { records, malformedRecords }
}

/** The probe a consumer should trust: the newest COMPLETE one that restored Filters OFF, or null. A
 * newer failed probe never displaces an older good one, and no probe at all beats a wrong one. */
export function latestTrustedProtectionRecord(parsed: ParsedProtectionRecords): VeteranProtectionRecord | null {
    return (
        [...parsed.records]
            .filter((r) => r.outcome === "complete" && r.restoredFiltersOff)
            .sort((a, b) => (b.completedAt ?? b.startedAt ?? 0) - (a.completedAt ?? a.startedAt ?? 0))[0] ?? null
    )
}

/** Why a protection inventory is not usable. Empty when it is. */
export type ProtectionInventoryDefect =
    | "no_protection_record"
    | "probe_not_complete"
    | "filters_not_restored"
    | "roster_untrusted"
    | "roster_count_mismatch"

export interface ProtectionInventory {
    readonly schema: typeof PARENTLAB_PROTECTION_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_PROTECTION_SCHEMA_VERSION
    readonly protectionScanId: string | null
    readonly rosterScanId: string
    /** The probe and the snapshot describe the same roster (same registered count) and both are trusted. */
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
 * Derives one fingerprint's favorite/memo state from a population and its (possibly empty) enumerated
 * set. An empty partition makes every Veteran outside it; a non-empty ENUMERATED partition names its
 * members and the rest are outside; a non-empty un-enumerated partition leaves everyone unknown.
 */
function memberState<Present extends string, Absent extends string>(
    fingerprint: string,
    population: ProtectionPopulation,
    enumerated: boolean,
    members: readonly string[],
    present: Present,
    absent: Absent,
    unknown: "unknown",
): Present | Absent | "unknown" {
    if (population === "empty") return absent
    if (population === "nonempty") {
        if (!enumerated) return unknown
        return members.includes(fingerprint) ? present : absent
    }
    return unknown
}

/**
 * Binds one protection probe to a trusted roster snapshot and derives every identified Veteran's
 * protection state. When the probe is missing, not complete, did not restore filters, or does not
 * describe the same roster as the snapshot, the inventory is marked incompatible and every state is
 * UNKNOWN - never silently not-protected.
 */
export function buildProtectionInventory(record: VeteranProtectionRecord | null, snapshot: RosterSnapshot): ProtectionInventory {
    const defects: ProtectionInventoryDefect[] = []
    if (!record) defects.push("no_protection_record")
    if (record && record.outcome !== "complete") defects.push("probe_not_complete")
    if (record && !record.restoredFiltersOff) defects.push("filters_not_restored")
    if (!snapshot.trustedComplete) defects.push("roster_untrusted")
    if (record && snapshot.registeredUsed !== null && record.registeredUsed !== null && record.registeredUsed !== snapshot.registeredUsed) {
        defects.push("roster_count_mismatch")
    }
    const compatible = defects.length === 0

    const byFingerprint = new Map<string, DerivedProtection>()
    const counts = { favorite: 0, notFavorite: 0, favoriteUnknown: 0, hasMemo: 0, noMemo: 0, memoUnknown: 0, protected: 0, notProtected: 0, protectionUnknown: 0 }

    for (const entry of snapshot.entries) {
        const fp = entry.rosterFingerprint
        if (!fp) continue
        let fav: FavoriteState = "unknown"
        let memo: MemoState = "unknown"
        if (compatible && record) {
            fav = memberState(fp, record.favoritePopulation, record.enumerationPerformed, record.favoritedFingerprints, "favorite", "not_favorite", "unknown")
            memo = memberState(fp, record.memoPopulation, record.enumerationPerformed, record.memoFingerprints, "has_memo", "no_memo", "unknown")
        }
        const protectionState = protectionFrom(fav, memo)
        byFingerprint.set(fp, { favoriteState: fav, memoState: memo, protectionState })

        if (fav === "favorite") counts.favorite++
        else if (fav === "not_favorite") counts.notFavorite++
        else counts.favoriteUnknown++
        if (memo === "has_memo") counts.hasMemo++
        else if (memo === "no_memo") counts.noMemo++
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
        compatible,
        byFingerprint,
        counts,
        defects,
    }
}
