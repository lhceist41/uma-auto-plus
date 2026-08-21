// ParentLab PL-R1b reconciliation - joins a current-roster snapshot to the PL-3 Veteran library.
// Pure, offline, deterministic. Reads two artifacts and produces a third; mutates neither.
//
// The asymmetry that shapes everything here: PL-3 identity hashes (trainee, scenario, fans, stats,
// skillPts, kept sparks), and the roster screen shows NONE of skillPts, the kept spark set, or (at
// identity-pass depth) scenario and fans. So a roster entry can never recompute a veteranId. It can
// only match on the projection the two sides share, and the honest tiering follows from exactly how
// much of that projection was actually visible:
//
//   trainee + the five final stats   -> always available from the identity pass
//   scenario / fans / dateAcquired   -> only when the Career Info pass ran
//
// A unique match on the stat projection alone is PROBABLE, not EXACT. EXACT requires at least one
// independent corroborating field to have been read AND agreed. Two historical records fitting
// equally well is AMBIGUOUS and stays that way; nothing is silently picked.
//
// History is never deleted. HISTORICAL_NOT_IN_ROSTER is an annotation on the library, not a removal,
// and it is only meaningful against a trusted-complete snapshot - a partial scan cannot prove an
// absence. That reliability flag is carried in the output rather than left for a reader to infer.

import type { Veteran, VeteranLibrary } from "./types.ts"
import { ROSTER_STAT_KEYS, type RosterEntryRecord, type RosterSnapshot } from "./roster.ts"

export const PARENTLAB_RECONCILE_SCHEMA = "parent_lab_roster_reconciliation" as const
export const PARENTLAB_RECONCILE_SCHEMA_VERSION = 1 as const

export type RosterMatchStatus =
    /** One historical Veteran fits, and an independent corroborating field was read and agreed. */
    | "EXACT_HISTORICAL_MATCH"
    /** One historical Veteran fits on everything visible, but no corroborating field was available. */
    | "PROBABLE_HISTORICAL_MATCH"
    /** No credible historical record. Expected for Veterans that predate the telemetry corpus. */
    | "ROSTER_ONLY"
    /** Two or more historical records fit equally well, or two roster entries contest one record. */
    | "AMBIGUOUS"
    /** The entry could not be joined at all: its trainee or stats did not read. */
    | "UNRESOLVED"

/** Statuses that assert this roster entry IS a specific historical Veteran. */
const CLAIMING = new Set<RosterMatchStatus>(["EXACT_HISTORICAL_MATCH", "PROBABLE_HISTORICAL_MATCH"])

/** Which corroborating fields were compared, and whether each agreed. Absent fields are not failures. */
export interface RosterCorroboration {
    readonly scenario: boolean | null
    readonly fans: boolean | null
    readonly dateAcquired: boolean | null
}

export interface RosterReconciledEntry {
    readonly scanIndex: number
    readonly character: string | null
    readonly outfit: string | null
    readonly rank: string | null
    readonly rating: number | null
    readonly rosterFingerprint: string | null
    readonly status: RosterMatchStatus
    /** The matched Veteran, for a claiming status. Null otherwise. */
    readonly veteranId: string | null
    /** Every historical candidate that survived filtering, sorted. Empty for ROSTER_ONLY/UNRESOLVED. */
    readonly candidateVeteranIds: readonly string[]
    readonly corroboration: RosterCorroboration
    /** Candidates that matched the stat projection but were contradicted by a corroborating field. */
    readonly contradictedCandidates: number
    /** Why the entry landed on this status, in one short phrase. Data, not prose. */
    readonly reason: string
}

/** A historical Veteran with no current roster entry: transferred, released, or never registered. */
export interface HistoricalNotInRoster {
    readonly veteranId: string
    readonly trainee: string
    readonly scenario: string
    readonly completedAt: number | null
    readonly fans: number
}

export interface RosterReconciliationCounts {
    readonly exact: number
    readonly probable: number
    readonly rosterOnly: number
    readonly ambiguous: number
    readonly unresolved: number
    readonly historicalNotInRoster: number
}

export interface RosterReconciliation {
    readonly schema: typeof PARENTLAB_RECONCILE_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_RECONCILE_SCHEMA_VERSION
    readonly scanId: string
    readonly observedAt: number | null
    readonly trustedComplete: boolean
    readonly counts: RosterReconciliationCounts
    readonly entries: readonly RosterReconciledEntry[]
    readonly historicalNotInRoster: readonly HistoricalNotInRoster[]
    /** False when the snapshot is not trusted-complete: a partial scan cannot prove a Veteran is
     * gone, so the list above is then a list of "not seen by this scan", not "not owned". */
    readonly historicalNotInRosterReliable: boolean
    readonly diagnostics: RosterReconciliationDiagnostics
}

export interface RosterReconciliationDiagnostics {
    readonly historicalVeterans: number
    /** Roster entries carrying enough evidence to attempt a join (trainee + all five stats). */
    readonly joinableEntries: number
    /** Distinct join keys in the library that more than one Veteran shares. The ceiling on how many
     * entries can ever reach EXACT without a corroborating field. */
    readonly historicalKeyCollisions: number
    /** Roster entries whose single best match was claimed by another entry too. */
    readonly contestedMatches: number
    /** Entries carrying a Career Info block, so a corroborating comparison was possible at all. */
    readonly entriesWithCareerInfo: number
}

/** Join-key normalization: trim, collapse internal whitespace, casefold. Matches the PL-3 identity
 * normalization so an OCR-read roster name and a corpus trainee name agree on the same string. */
export function normalizeJoinName(raw: string): string {
    return raw.trim().replace(/\s+/g, " ").toUpperCase()
}

/** The projection the roster identity pass and a PL-3 Veteran genuinely share. Null when the roster
 * entry did not read enough to key on; a partially read entry is UNRESOLVED, never approximated. */
function joinKeyForEntry(entry: RosterEntryRecord): string | null {
    if (!entry.character) return null
    const stats: number[] = []
    for (const key of ROSTER_STAT_KEYS) {
        const value = entry.stats[key]
        if (value === null) return null
        stats.push(value)
    }
    return `${normalizeJoinName(entry.character)}|${stats.join(",")}`
}

function joinKeyForVeteran(veteran: Veteran): string {
    const s = veteran.result.finalStats
    return `${normalizeJoinName(veteran.trainee)}|${[s.spd, s.sta, s.pwr, s.grt, s.wit].join(",")}`
}

/** The day a Veteran's career completed, as "YYYY-MM-DD" in the local zone the game displays in.
 * Null when the record carries no completion timestamp. */
function completedDay(veteran: Veteran): string | null {
    if (veteran.completedAt === null) return null
    const d = new Date(veteran.completedAt)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
}

/** Scenario comparison is name-shaped, not exact: the roster shows the full in-game title ("The
 * Beginning: URA Finale") while the corpus stores the short arm name ("URA Finale"). Containment
 * either way is agreement; neither containing the other is a contradiction. */
function scenarioAgrees(rosterScenario: string, veteranScenario: string): boolean {
    const a = normalizeJoinName(rosterScenario)
    const b = normalizeJoinName(veteranScenario)
    return a === b || a.includes(b) || b.includes(a)
}

interface Corroborated {
    readonly veteran: Veteran
    readonly corroboration: RosterCorroboration
    readonly contradicted: boolean
}

/** Compares whatever Career Info fields the entry carries against one candidate. */
function corroborate(entry: RosterEntryRecord, veteran: Veteran): Corroborated {
    const info = entry.careerInfo
    const corroboration: RosterCorroboration = {
        scenario: info?.scenario ? scenarioAgrees(info.scenario, veteran.result.scenario) : null,
        fans: info?.fans !== undefined && info?.fans !== null ? info.fans === veteran.result.fans : null,
        dateAcquired: info?.dateAcquired ? completedDay(veteran) === info.dateAcquired : null,
    }
    const compared = [corroboration.scenario, corroboration.fans, corroboration.dateAcquired].filter((v): v is boolean => v !== null)
    return { veteran, corroboration, contradicted: compared.some((agreed) => !agreed) }
}

const NO_CORROBORATION: RosterCorroboration = { scenario: null, fans: null, dateAcquired: null }

function resolveEntry(entry: RosterEntryRecord, byKey: ReadonlyMap<string, readonly Veteran[]>): RosterReconciledEntry {
    const base = {
        scanIndex: entry.scanIndex,
        character: entry.character,
        outfit: entry.outfit,
        rank: entry.rank,
        rating: entry.rating,
        rosterFingerprint: entry.rosterFingerprint,
    }

    const key = joinKeyForEntry(entry)
    if (key === null) {
        return { ...base, status: "UNRESOLVED", veteranId: null, candidateVeteranIds: [], corroboration: NO_CORROBORATION, contradictedCandidates: 0, reason: "trainee or stats unread" }
    }

    const candidates = byKey.get(key) ?? []
    if (candidates.length === 0) {
        return { ...base, status: "ROSTER_ONLY", veteranId: null, candidateVeteranIds: [], corroboration: NO_CORROBORATION, contradictedCandidates: 0, reason: "no historical veteran with this trainee and stat set" }
    }

    const judged = candidates.map((v) => corroborate(entry, v))
    const surviving = judged.filter((j) => !j.contradicted)
    const contradictedCandidates = judged.length - surviving.length

    if (surviving.length === 0) {
        return {
            ...base,
            status: "ROSTER_ONLY",
            veteranId: null,
            candidateVeteranIds: [],
            corroboration: NO_CORROBORATION,
            contradictedCandidates,
            reason: "every historical candidate was contradicted by career info",
        }
    }

    const candidateVeteranIds = surviving.map((j) => j.veteran.veteranId).sort()
    if (surviving.length > 1) {
        return {
            ...base,
            status: "AMBIGUOUS",
            veteranId: null,
            candidateVeteranIds,
            corroboration: NO_CORROBORATION,
            contradictedCandidates,
            reason: `${surviving.length} historical veterans fit equally well`,
        }
    }

    const only = surviving[0]
    const agreedFields = [only.corroboration.scenario, only.corroboration.fans, only.corroboration.dateAcquired].filter((v) => v === true).length
    // skillPts and the kept spark set are never visible on the roster, so their absence is expected
    // and must not block a match; what separates EXACT from PROBABLE is whether anything INDEPENDENT
    // of the stat projection was actually read and agreed.
    return {
        ...base,
        status: agreedFields > 0 ? "EXACT_HISTORICAL_MATCH" : "PROBABLE_HISTORICAL_MATCH",
        veteranId: only.veteran.veteranId,
        candidateVeteranIds,
        corroboration: only.corroboration,
        contradictedCandidates,
        reason: agreedFields > 0 ? `unique match corroborated on ${agreedFields} independent field(s)` : "unique match on trainee and stats; no corroborating field was read",
    }
}

/**
 * Reconciles a current-roster snapshot against the PL-3 Veteran library.
 *
 * Deterministic: entries come out in scan order, candidate id lists and the not-in-roster list are
 * sorted, and nothing depends on Map iteration order. Running it twice on the same inputs produces
 * byte-identical output.
 */
export function reconcileRoster(library: VeteranLibrary, snapshot: RosterSnapshot): RosterReconciliation {
    const byKey = new Map<string, Veteran[]>()
    for (const veteran of library.veterans) {
        const key = joinKeyForVeteran(veteran)
        const list = byKey.get(key)
        if (list) list.push(veteran)
        else byKey.set(key, [veteran])
    }
    const historicalKeyCollisions = [...byKey.values()].filter((v) => v.length > 1).length

    let resolved = snapshot.entries.map((entry) => resolveEntry(entry, byKey))

    // One historical Veteran cannot be two current roster entries. When two entries both claim the
    // same record - the duplicate-fingerprint case, or a stalled chevron that read one Veteran twice
    // - neither claim is trustworthy, so both become AMBIGUOUS rather than one being picked.
    const claimCounts = new Map<string, number>()
    for (const entry of resolved) {
        if (entry.veteranId && CLAIMING.has(entry.status)) claimCounts.set(entry.veteranId, (claimCounts.get(entry.veteranId) ?? 0) + 1)
    }
    const contested = new Set([...claimCounts.entries()].filter(([, n]) => n > 1).map(([id]) => id))
    if (contested.size > 0) {
        resolved = resolved.map((entry) =>
            entry.veteranId && contested.has(entry.veteranId)
                ? { ...entry, status: "AMBIGUOUS" as const, veteranId: null, reason: "two roster entries claim the same historical veteran" }
                : entry,
        )
    }
    const contestedMatches = resolved.filter((e) => e.reason === "two roster entries claim the same historical veteran").length

    const claimed = new Set(resolved.filter((e) => e.veteranId !== null && CLAIMING.has(e.status)).map((e) => e.veteranId as string))
    const historicalNotInRoster: HistoricalNotInRoster[] = library.veterans
        .filter((v) => !claimed.has(v.veteranId))
        .map((v) => ({ veteranId: v.veteranId, trainee: v.trainee, scenario: v.scenario, completedAt: v.completedAt, fans: v.result.fans }))
        .sort((a, b) => (a.trainee < b.trainee ? -1 : a.trainee > b.trainee ? 1 : 0) || (a.veteranId < b.veteranId ? -1 : a.veteranId > b.veteranId ? 1 : 0))

    const counts: RosterReconciliationCounts = {
        exact: resolved.filter((e) => e.status === "EXACT_HISTORICAL_MATCH").length,
        probable: resolved.filter((e) => e.status === "PROBABLE_HISTORICAL_MATCH").length,
        rosterOnly: resolved.filter((e) => e.status === "ROSTER_ONLY").length,
        ambiguous: resolved.filter((e) => e.status === "AMBIGUOUS").length,
        unresolved: resolved.filter((e) => e.status === "UNRESOLVED").length,
        historicalNotInRoster: historicalNotInRoster.length,
    }

    return {
        schema: PARENTLAB_RECONCILE_SCHEMA,
        schemaVersion: PARENTLAB_RECONCILE_SCHEMA_VERSION,
        scanId: snapshot.scanId,
        observedAt: snapshot.observedAt,
        trustedComplete: snapshot.trustedComplete,
        counts,
        entries: resolved,
        historicalNotInRoster,
        historicalNotInRosterReliable: snapshot.trustedComplete,
        diagnostics: {
            historicalVeterans: library.veterans.length,
            joinableEntries: snapshot.entries.filter((e) => joinKeyForEntry(e) !== null).length,
            historicalKeyCollisions,
            contestedMatches,
            entriesWithCareerInfo: snapshot.entries.filter((e) => e.careerInfo !== null).length,
        },
    }
}
