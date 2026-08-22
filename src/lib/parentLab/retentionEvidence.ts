// ParentLab PL-R2 - evidence normalization and the factor scarcity index. Pure, offline, deterministic.
//
// This is the read side of the pipeline: it joins the three existing artifacts (current roster
// snapshot, PL-3 historical library with its reconciliation, PL-R1c Inspiration captures) into one
// per-Veteran evidence record, and derives the account's observed factor inventory from them. It
// makes no recommendation and mutates no input.
//
// The rule that shapes the whole file: an absent capture is not evidence of an absent factor. A
// Veteran whose Inspiration was never read contributes nothing to the inventory in either direction,
// so it is excluded from the coverage numerator instead of being counted as a Veteran that lacks
// every factor. Getting that backwards is what would turn "we have only looked at 20 of 257" into a
// confident and completely wrong claim that some factor exists exactly once on the account.

import type { InspirationFactorRecord, VeteranInspirationView } from "./inspiration.ts"
import { normalizeJoinName, type RosterMatchStatus, type RosterReconciliation } from "./reconcile.ts"
import { ROSTER_STAT_KEYS, type RosterEntryRecord, type RosterSnapshot } from "./roster.ts"
import { PARENTLAB_RETENTION_SCHEMA, PARENTLAB_RETENTION_SCHEMA_VERSION, type FactorScarcityEntry, type FactorScarcityIndex, type ReplacementDifficulty, type ReplacementSummary, type ScarcityClaim } from "./retentionTypes.ts"
import type { VeteranLibrary } from "./types.ts"

/**
 * Star floor at which a factor counts as high value for the protection rules.
 *
 * One star is the common case on almost every Veteran, so treating a 1-star factor as scarce would
 * make the scarcity rules fire on noise. Two stars is the first level that is actually worth
 * protecting, and it applies uniformly across kinds: a character's green factor is scarce only in the
 * sense that its character is, and character coverage is measured against the WHOLE roster elsewhere,
 * which is strictly better evidence than the captured subset can give.
 */
export const HIGH_VALUE_FACTOR_MIN_STARS = 2

/** Carrier count at or below which a factor is called scarce rather than common. */
export const OBSERVED_SCARCE_MAX_CARRIERS = 3

/** Scarcity claim strength, strongest first, so "the scarcest claim" never depends on string order. */
const SCARCITY_CLAIM_RANK: Readonly<Record<ScarcityClaim, number>> = {
    ACCOUNT_UNIQUE: 4,
    OBSERVED_UNIQUE: 3,
    OBSERVED_SCARCE: 2,
    OBSERVED_COMMON: 1,
    UNMEASURED: 0,
}

/** The semantic key for a factor: kind plus canonical name, upper-cased. Null when unresolved. */
export function factorKey(factor: InspirationFactorRecord): string | null {
    if (!factor.canonicalName) return null
    return `${factor.kind}:${factor.canonicalName.toUpperCase()}`
}

/**
 * One roster Veteran with every source joined onto it.
 *
 * `selfFactors` is populated only when the capture is both complete and fully resolved. That is
 * deliberately stricter than "a capture exists": a half-read factor list is not a smaller answer, and
 * an unresolved factor name cannot be counted in an inventory keyed by canonical name.
 */
export interface VeteranEvidence {
    readonly entry: RosterEntryRecord
    readonly rosterFingerprint: string | null
    /** The best capture for this Veteran, if any exists at all. */
    readonly capture: VeteranInspirationView | null
    /** Capture present, complete, and every self factor resolved onto the canonical domain. */
    readonly captureTrusted: boolean
    /** The Veteran's own factor set, or null when no TRUSTED capture backs it. */
    readonly selfFactors: readonly InspirationFactorRecord[] | null
    /** Legacy Origin ancestors observed on its panel, or null when uncaptured. */
    readonly lineageAncestorsObserved: number | null
    readonly matchStatus: RosterMatchStatus
    readonly historicalVeteranId: string | null
    /** Sum of the five final stats, or null when any of them did not read. */
    readonly statTotal: number | null
    /** Roster Veterans sharing this character, including this one. 0 when the character did not read. */
    readonly characterCarriers: number
    readonly characterOutfitCarriers: number
}

/** The normalized evidence for one snapshot, in traversal order. */
export interface RetentionEvidenceSet {
    readonly snapshot: RosterSnapshot
    readonly veterans: readonly VeteranEvidence[]
    /** Newest observation time across the roster snapshot and the joined captures. Never a wall clock. */
    readonly observedAt: number | null
}

function statTotalOf(entry: RosterEntryRecord): number | null {
    let sum = 0
    for (const key of ROSTER_STAT_KEYS) {
        const v = entry.stats[key]
        if (v === null) return null
        sum += v
    }
    return sum
}

/**
 * Joins a roster snapshot to its captures and its historical reconciliation.
 *
 * The snapshot is the only authority on membership: a capture with no matching entry is simply not
 * reached here (the inspiration join already counts it as an orphan), and a historical Veteran with
 * no entry is not a member of the account at all.
 */
export function buildRetentionEvidence(
    snapshot: RosterSnapshot,
    inspirationIndex: ReadonlyMap<string, VeteranInspirationView>,
    reconciliation: RosterReconciliation | null,
): RetentionEvidenceSet {
    const characterCounts = new Map<string, number>()
    const characterOutfitCounts = new Map<string, number>()
    for (const entry of snapshot.entries) {
        if (entry.character) {
            const c = normalizeJoinName(entry.character)
            characterCounts.set(c, (characterCounts.get(c) ?? 0) + 1)
            const co = `${c}|${entry.outfit ? normalizeJoinName(entry.outfit) : ""}`
            characterOutfitCounts.set(co, (characterOutfitCounts.get(co) ?? 0) + 1)
        }
    }

    const byScanIndex = new Map<number, { status: RosterMatchStatus; veteranId: string | null }>()
    for (const r of reconciliation?.entries ?? []) byScanIndex.set(r.scanIndex, { status: r.status, veteranId: r.veteranId })

    let newest = snapshot.observedAt
    const veterans: VeteranEvidence[] = []
    for (const entry of snapshot.entries) {
        const fingerprint = entry.rosterFingerprint
        const capture = fingerprint ? (inspirationIndex.get(fingerprint) ?? null) : null
        if (capture?.observedAt !== null && capture?.observedAt !== undefined && (newest === null || capture.observedAt > newest)) newest = capture.observedAt
        const captureTrusted = capture !== null && capture.sparkCaptureComplete && capture.selfFactorSetTrusted
        const match = byScanIndex.get(entry.scanIndex)
        const character = entry.character ? normalizeJoinName(entry.character) : null
        veterans.push({
            entry,
            rosterFingerprint: fingerprint,
            capture,
            captureTrusted,
            selfFactors: captureTrusted ? capture.selfFactors : null,
            lineageAncestorsObserved: capture ? capture.legacyAncestorFactorCounts.length : null,
            matchStatus: match?.status ?? "UNRESOLVED",
            historicalVeteranId: match?.veteranId ?? null,
            statTotal: statTotalOf(entry),
            characterCarriers: character ? (characterCounts.get(character) ?? 0) : 0,
            characterOutfitCarriers: character ? (characterOutfitCounts.get(`${character}|${entry.outfit ? normalizeJoinName(entry.outfit) : ""}`) ?? 0) : 0,
        })
    }
    return { snapshot, veterans, observedAt: newest }
}

/**
 * Builds the account's observed factor inventory from the trusted captures only.
 *
 * Every count here is a statement about the captured subset. `coverage` and `accountWide` travel with
 * the index so no consumer can read a carrier count as an account-wide fact without also seeing how
 * much of the account was actually looked at.
 */
export function buildFactorScarcityIndex(evidence: RetentionEvidenceSet): FactorScarcityIndex {
    const identified = evidence.veterans.filter((v) => v.rosterFingerprint !== null).length
    let capturedTrusted = 0
    let capturedUntrusted = 0
    let unresolvedFactorReads = 0

    // factorKey -> star floor -> set of carrier fingerprints. A Veteran carrying the same factor twice
    // counts once per floor: the inventory question is how many VETERANS can pass it on.
    const carriers = new Map<string, { kind: string; canonicalName: string; byFloor: Map<number, Set<string>>; any: Set<string>; maxStars: number }>()

    for (const v of evidence.veterans) {
        if (v.capture && !v.captureTrusted) capturedUntrusted++
        if (!v.captureTrusted || !v.selfFactors || !v.rosterFingerprint) continue
        capturedTrusted++
        for (const factor of v.selfFactors) {
            const key = factorKey(factor)
            if (!key) {
                unresolvedFactorReads++
                continue
            }
            let slot = carriers.get(key)
            if (!slot) {
                slot = { kind: factor.kind, canonicalName: factor.canonicalName as string, byFloor: new Map(), any: new Set(), maxStars: 0 }
                carriers.set(key, slot)
            }
            slot.any.add(v.rosterFingerprint)
            if (factor.stars > slot.maxStars) slot.maxStars = factor.stars
            // A carrier at 3 stars also satisfies a 2-star and a 1-star requirement, so it is recorded
            // at every floor it clears rather than only at its own.
            for (let floor = 1; floor <= factor.stars; floor++) {
                let set = slot.byFloor.get(floor)
                if (!set) {
                    set = new Set()
                    slot.byFloor.set(floor, set)
                }
                set.add(v.rosterFingerprint)
            }
        }
    }

    const entries: FactorScarcityEntry[] = [...carriers.entries()]
        .map(([key, slot]) => ({
            factorKey: key,
            kind: slot.kind,
            canonicalName: slot.canonicalName,
            observedCarriers: slot.any.size,
            carriersByMinStars: Object.fromEntries([1, 2, 3].map((floor) => [String(floor), slot.byFloor.get(floor)?.size ?? 0])),
            maxObservedStars: slot.maxStars,
        }))
        .sort((a, b) => (a.factorKey < b.factorKey ? -1 : a.factorKey > b.factorKey ? 1 : 0))

    const coverage = identified > 0 ? Math.round((capturedTrusted / identified) * 10000) / 10000 : 0
    return {
        schema: PARENTLAB_RETENTION_SCHEMA,
        schemaVersion: PARENTLAB_RETENTION_SCHEMA_VERSION,
        identifiedRosterEntries: identified,
        capturedTrusted,
        capturedUntrusted,
        coverage,
        // Complete coverage means every identified roster entry carries a trusted complete capture.
        // Nothing weaker licenses an account-wide claim, and `identified > 0` keeps an empty roster
        // from reading as trivially complete.
        accountWide: identified > 0 && capturedTrusted === identified,
        entries,
        unresolvedFactorReads,
    }
}

/** Carriers of a factor at a given star floor, within the captured subset. 0 when never observed. */
export function carriersAtOrAbove(index: FactorScarcityIndex, key: string, stars: number): number {
    const entry = index.entries.find((e) => e.factorKey === key)
    if (!entry) return 0
    const floor = Math.max(1, Math.min(3, stars))
    return entry.carriersByMinStars[String(floor)] ?? 0
}

/** The factor keys where this Veteran is the only observed carrier at its own star count, sorted. */
export function observedUniqueFactorKeys(evidence: VeteranEvidence, index: FactorScarcityIndex, minStars = HIGH_VALUE_FACTOR_MIN_STARS): readonly string[] {
    if (!evidence.selfFactors) return []
    const out = new Set<string>()
    for (const factor of evidence.selfFactors) {
        if (factor.stars < minStars) continue
        const key = factorKey(factor)
        if (key && carriersAtOrAbove(index, key, factor.stars) === 1) out.add(key)
    }
    return [...out].sort()
}

/** The strongest scarcity claim any of this Veteran's factors supports under the current coverage. */
export function scarcestClaim(evidence: VeteranEvidence, index: FactorScarcityIndex): ScarcityClaim {
    if (!evidence.selfFactors) return "UNMEASURED"
    let best: ScarcityClaim = "OBSERVED_COMMON"
    for (const factor of evidence.selfFactors) {
        const key = factorKey(factor)
        if (!key) continue
        const carriers = carriersAtOrAbove(index, key, factor.stars)
        let claim: ScarcityClaim
        if (carriers === 1) claim = index.accountWide ? "ACCOUNT_UNIQUE" : "OBSERVED_UNIQUE"
        else if (carriers <= OBSERVED_SCARCE_MAX_CARRIERS) claim = "OBSERVED_SCARCE"
        else claim = "OBSERVED_COMMON"
        if (SCARCITY_CLAIM_RANK[claim] > SCARCITY_CLAIM_RANK[best]) best = claim
    }
    return best
}

/**
 * Replacement difficulty, banded from the bot's own historical corpus.
 *
 * The measurable question is how rare this Veteran's stat outcome is among the careers this bot has
 * actually run for the same trainee. That is NOT a reroll probability and the band must never be read
 * as one: the corpus is a record of the configurations that were run, not a sample of a distribution.
 *
 * The Veteran's own historical record is excluded from the comparison when it was matched, so
 * "nothing else the bot produced reached this" is a statement about other careers rather than a
 * tautology about this one. Fewer than [MIN_HISTORICAL_SAMPLES] careers for the trainee is UNKNOWN,
 * and UNKNOWN protects.
 */
export const MIN_HISTORICAL_SAMPLES = 3

export function replacementSummary(evidence: VeteranEvidence, library: VeteranLibrary | null): ReplacementSummary {
    const character = evidence.entry.character ? normalizeJoinName(evidence.entry.character) : null
    const samples = character && library ? library.veterans.filter((v) => normalizeJoinName(v.trainee) === character) : []
    const statTotal = evidence.statTotal
    const unknown = (basis: string): ReplacementSummary => ({
        difficulty: "UNKNOWN" as ReplacementDifficulty,
        historicalSamples: samples.length,
        historicalAtOrAbove: null,
        statTotal,
        historicalMatchStatus: evidence.matchStatus,
        basis,
    })

    if (!library) return unknown("no historical library supplied")
    if (!character) return unknown("roster entry has no readable character")
    if (statTotal === null) return unknown("roster entry has an unread stat, so no stat total exists")
    if (samples.length < MIN_HISTORICAL_SAMPLES) return unknown(`only ${samples.length} historical career(s) for this trainee; below the ${MIN_HISTORICAL_SAMPLES} needed for a band`)

    const others = samples.filter((v) => v.veteranId !== evidence.historicalVeteranId)
    const atOrAbove = others.filter((v) => {
        const s = v.result.finalStats
        return s.spd + s.sta + s.pwr + s.grt + s.wit >= statTotal
    }).length
    const basis = `stat-total rank among ${others.length} other historical career(s) for this trainee; corpus rarity, not a reroll probability`

    let difficulty: ReplacementDifficulty
    if (others.length === 0) difficulty = "UNKNOWN"
    else if (atOrAbove === 0) difficulty = "VERY_HARD"
    else if (atOrAbove / others.length <= 0.1) difficulty = "HARD"
    else if (atOrAbove / others.length <= 0.4) difficulty = "MODERATE"
    else difficulty = "EASY"

    return {
        difficulty,
        historicalSamples: samples.length,
        historicalAtOrAbove: atOrAbove,
        statTotal,
        historicalMatchStatus: evidence.matchStatus,
        basis: others.length === 0 ? "every historical career for this trainee is this Veteran itself" : basis,
    }
}
