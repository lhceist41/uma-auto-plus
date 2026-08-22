// ParentLab PL-R2 - target profiles. Pure, offline, deterministic.
//
// Retention value is goal-dependent: a Veteran that is redundant for a Mile project can be the only
// thing covering a Long one. So "is A better than B" is never asked in the abstract, only under a
// named profile, and the profile decides which dimensions are protected.
//
// The set is deliberately small. Only profiles the repository's own evidence supports cleanly are
// defined: the roster identity pass reads all ten aptitude grades for every entry, and the canonical
// factor domain names the aptitude and stat factors exactly, so a distance profile is a real gate
// rather than a guess. Nothing here models affinity - the affinity formula is not decoded in this
// repository, and inventing one would put a fabricated number underneath a retention decision.

import type { InspirationFactorRecord } from "./inspiration.ts"
import type { RosterEntryRecord } from "./roster.ts"

/** The roster aptitude keys the device writes, in its own order. */
export const ROSTER_APTITUDE_KEYS = ["turf", "dirt", "sprint", "mile", "medium", "long", "front", "pace", "late", "end"] as const
export type RosterAptitudeKey = (typeof ROSTER_APTITUDE_KEYS)[number]

/**
 * Aptitude letter grades, worst to best. The index IS the ordinal, so a comparison never depends on
 * string order. A grade the game adds later that is not listed here ranks as unknown (-1) rather than
 * silently sorting as the worst real grade.
 */
export const APTITUDE_GRADE_ORDER = ["G", "F", "E", "D", "C", "B", "A", "A+", "S", "S+"] as const

/** Ordinal of an aptitude grade, or -1 when the grade is absent or unrecognised. */
export function aptitudeGradeRank(grade: string | null | undefined): number {
    if (!grade) return -1
    return APTITUDE_GRADE_ORDER.indexOf(grade.trim().toUpperCase() as (typeof APTITUDE_GRADE_ORDER)[number])
}

export const TARGET_PROFILE_IDS = ["GENERAL_INHERITANCE", "MILE_PARENT", "LONG_PARENT"] as const
export type TargetProfileId = (typeof TARGET_PROFILE_IDS)[number]

/**
 * One retention goal.
 *
 * `aptitudeGate` is coverage: a Veteran either clears it or does not, and being the only one that
 * clears it is an account-coverage fact independent of any Spark capture. The two factor lists name
 * the canonical factors that pass the goal on directly, which is a different question from the
 * Veteran's own trained aptitude and is kept separate from it on purpose.
 */
export interface TargetProfile {
    readonly id: TargetProfileId
    readonly label: string
    /** Minimum grade on one roster aptitude, or null for a profile with no coverage gate. */
    readonly aptitudeGate: { readonly key: RosterAptitudeKey; readonly minGrade: string } | null
    /** Canonical aptitude-factor names that inherit directly toward this goal. */
    readonly targetAptitudeFactors: readonly string[]
    /** Canonical stat-factor names that inherit directly toward this goal. */
    readonly targetStatFactors: readonly string[]
}

export const TARGET_PROFILES: Readonly<Record<TargetProfileId, TargetProfile>> = {
    GENERAL_INHERITANCE: {
        id: "GENERAL_INHERITANCE",
        label: "General inheritance value",
        aptitudeGate: null,
        targetAptitudeFactors: [],
        targetStatFactors: [],
    },
    MILE_PARENT: {
        id: "MILE_PARENT",
        label: "Mile parent",
        aptitudeGate: { key: "mile", minGrade: "A" },
        targetAptitudeFactors: ["Mile"],
        targetStatFactors: ["Speed"],
    },
    LONG_PARENT: {
        id: "LONG_PARENT",
        label: "Long parent",
        aptitudeGate: { key: "long", minGrade: "A" },
        targetAptitudeFactors: ["Long"],
        targetStatFactors: ["Stamina"],
    },
}

/** Resolves a profile id case-insensitively, or null when it names no defined profile. */
export function resolveTargetProfile(id: string): TargetProfile | null {
    const key = id.trim().toUpperCase() as TargetProfileId
    return TARGET_PROFILES[key] ?? null
}

/** Whether a roster entry clears a profile's aptitude gate. A profile with no gate is cleared by
 * every entry; an unread aptitude clears nothing, because an unread grade is not a passing grade. */
export function clearsAptitudeGate(entry: RosterEntryRecord, profile: TargetProfile): boolean {
    if (!profile.aptitudeGate) return true
    const have = aptitudeGradeRank(entry.aptitudes[profile.aptitudeGate.key])
    const need = aptitudeGradeRank(profile.aptitudeGate.minGrade)
    return have >= 0 && need >= 0 && have >= need
}

/**
 * The dimension names, in the order they are reported. Every dimension is monotone "higher is
 * better", which is what makes a Pareto comparison meaningful.
 *
 * Rating is deliberately NOT here. Rating may order a report, but if it were a dominance dimension a
 * higher-rated generic Veteran could dominate a lower-rated one carrying a rare factor purely on the
 * rating gap, which is exactly the failure this stage is built to prevent.
 */
export const TARGET_DIMENSION_NAMES = [
    "statFactorStars",
    "aptitudeFactorStars",
    "uniqueFactorStars",
    "whiteFactorCount",
    "totalFactorStars",
    "onTargetAptitudeFactorStars",
    "onTargetStatFactorStars",
    "targetAptitudeGrade",
] as const
export type TargetDimensionName = (typeof TARGET_DIMENSION_NAMES)[number]

/** One Veteran's monotone dimension vector under one profile. */
export type TargetDimensions = Readonly<Record<TargetDimensionName, number>>

function maxStars(factors: readonly InspirationFactorRecord[], kind: string): number {
    let best = 0
    for (const f of factors) if (f.kind === kind && f.stars > best) best = f.stars
    return best
}

function maxStarsNamed(factors: readonly InspirationFactorRecord[], kind: string, names: readonly string[]): number {
    if (names.length === 0) return 0
    const wanted = new Set(names.map((n) => n.toUpperCase()))
    let best = 0
    for (const f of factors) {
        if (f.kind !== kind || !f.canonicalName) continue
        if (wanted.has(f.canonicalName.toUpperCase()) && f.stars > best) best = f.stars
    }
    return best
}

/**
 * Projects one Veteran onto a profile's dimensions.
 *
 * `factors` is the Veteran's own self factor set, or null when no trusted capture exists. Null is not
 * zero: a Veteran with no capture has an unmeasured factor profile, and the caller must not compare
 * it - `dominates` refuses any comparison whose evidence is not trusted on both sides.
 */
export function targetDimensions(entry: RosterEntryRecord, factors: readonly InspirationFactorRecord[] | null, profile: TargetProfile): TargetDimensions {
    const f = factors ?? []
    const gateGrade = profile.aptitudeGate ? aptitudeGradeRank(entry.aptitudes[profile.aptitudeGate.key]) : 0
    return {
        statFactorStars: maxStars(f, "stat"),
        aptitudeFactorStars: maxStars(f, "aptitude"),
        uniqueFactorStars: maxStars(f, "unique"),
        whiteFactorCount: f.filter((x) => x.kind === "white").length,
        totalFactorStars: f.reduce((sum, x) => sum + x.stars, 0),
        onTargetAptitudeFactorStars: maxStarsNamed(f, "aptitude", profile.targetAptitudeFactors),
        onTargetStatFactorStars: maxStarsNamed(f, "stat", profile.targetStatFactors),
        // Clamped at 0 so an unread aptitude cannot read as "better than the worst real grade".
        targetAptitudeGrade: Math.max(0, gateGrade),
    }
}
