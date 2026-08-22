// ParentLab PL-R4 - the target-build abstraction. Pure, offline, deterministic.
//
// "Which parents are best" is not a question that has an answer on its own. It has an answer under a
// named build: a Veteran that is the obvious pick for a Long project can be the worst pick for a
// Sprint one, and a factor that is precious for one goal is filler for another. So every candidate
// record, every pair and every ranking in this stage is produced under exactly one TargetBuild, and
// the build travels with the output.
//
// The abstraction is deliberately thin and open. Distance, surface and running style map onto named
// canonical factors because the factor domain names them exactly; everything else is a list of
// canonical factor names the operator supplies. A future Champion Meeting or League of Heroes profile
// is a different set of lists, not a different type, and nothing here hard-codes one permanent
// account goal.
//
// One thing is labelled rather than hidden: the stat priorities implied by a distance are an
// editorial default, not a decoded mechanic, and `statPriorityOrigin` says which it was.

import { normalizeCharacterName, resolveCharaId, type SuccessionRelationIndex } from "./affinityData.ts"
import type { RosterAptitudeKey } from "./retentionTargets.ts"

export const TARGET_DISTANCES = ["sprint", "mile", "medium", "long"] as const
export type TargetDistance = (typeof TARGET_DISTANCES)[number]

export const TARGET_SURFACES = ["turf", "dirt"] as const
export type TargetSurface = (typeof TARGET_SURFACES)[number]

export const TARGET_RUNNING_STYLES = ["front", "pace", "late", "end"] as const
export type TargetRunningStyle = (typeof TARGET_RUNNING_STYLES)[number]

/** Distance -> the canonical aptitude factor name that inherits toward it. */
export const DISTANCE_APTITUDE_FACTOR: Readonly<Record<TargetDistance, string>> = {
    sprint: "Sprint",
    mile: "Mile",
    medium: "Medium",
    long: "Long",
}

/** Surface -> the canonical aptitude factor name that inherits toward it. */
export const SURFACE_APTITUDE_FACTOR: Readonly<Record<TargetSurface, string>> = {
    turf: "Turf",
    dirt: "Dirt",
}

/** Running style -> the canonical aptitude factor name that inherits toward it. */
export const RUNNING_STYLE_APTITUDE_FACTOR: Readonly<Record<TargetRunningStyle, string>> = {
    front: "Front Runner",
    pace: "Pace Chaser",
    late: "Late Surger",
    end: "End Closer",
}

/** Distance -> the roster aptitude key the device reads for it. */
export const DISTANCE_ROSTER_KEY: Readonly<Record<TargetDistance, RosterAptitudeKey>> = {
    sprint: "sprint",
    mile: "mile",
    medium: "medium",
    long: "long",
}

/** Surface -> the roster aptitude key the device reads for it. */
export const SURFACE_ROSTER_KEY: Readonly<Record<TargetSurface, RosterAptitudeKey>> = {
    turf: "turf",
    dirt: "dirt",
}

/** Running style -> the roster aptitude key the device reads for it. */
export const RUNNING_STYLE_ROSTER_KEY: Readonly<Record<TargetRunningStyle, RosterAptitudeKey>> = {
    front: "front",
    pace: "pace",
    late: "late",
    end: "end",
}

/**
 * Blue-factor priorities implied by a distance.
 *
 * This is an editorial default and is marked as one wherever it is used. Nothing in the game data
 * this repository has decoded states which stats a distance wants; these lists encode the ordinary
 * reading of the distance and exist so a caller who names only a distance still gets a usable build.
 * An operator list always replaces them.
 */
export const DEFAULT_STAT_PRIORITY_BY_DISTANCE: Readonly<Record<TargetDistance, readonly string[]>> = {
    sprint: ["Speed", "Power"],
    mile: ["Speed", "Power"],
    medium: ["Speed", "Stamina"],
    long: ["Stamina", "Speed"],
}

/** Where the build's blue-factor priorities came from. */
export const STAT_PRIORITY_ORIGINS = ["OPERATOR", "DEFAULT_BY_DISTANCE", "NONE"] as const
export type StatPriorityOrigin = (typeof STAT_PRIORITY_ORIGINS)[number]

/** Evidence gaps in the build itself, as opposed to gaps in a candidate. */
export const TARGET_BUILD_GAPS = [
    /** The target trainee is not in the shipped relation domain, so no affinity component resolves. */
    "TARGET_CHARACTER_NOT_IN_RELATION_DOMAIN",
    /** No distance was named, so no distance aptitude factor and no default stat priorities exist. */
    "DISTANCE_UNSPECIFIED",
    /** No surface was named. */
    "SURFACE_UNSPECIFIED",
    /** No running style was named. */
    "RUNNING_STYLE_UNSPECIFIED",
    /** The build names no priority factor of any family, so factor relevance cannot be measured. */
    "NO_PRIORITY_FACTORS",
] as const
export type TargetBuildGap = (typeof TARGET_BUILD_GAPS)[number]

/** What the caller asks for. Every field beyond the trainee is optional and independently omitted. */
export interface TargetBuildRequest {
    readonly targetTrainee: string
    readonly distance?: TargetDistance | null
    readonly surface?: TargetSurface | null
    readonly runningStyle?: TargetRunningStyle | null
    readonly scenario?: string | null
    /** Canonical blue factor names. Replaces the distance default entirely when supplied. */
    readonly statFactors?: readonly string[] | null
    /** Canonical aptitude factor names, added to the ones the distance/surface/style imply. */
    readonly aptitudeFactors?: readonly string[] | null
    /** Canonical green factor names. */
    readonly uniqueFactors?: readonly string[] | null
    /** Canonical white factor names (skill, race or scenario factors). */
    readonly whiteFactors?: readonly string[] | null
    readonly label?: string | null
}

/**
 * A resolved target build.
 *
 * The four priority lists are deduplicated and sorted, and their ORDER IS NOT a weighting: a factor
 * either is a priority for this build or is not. Ranking never reads a factor's position in a list,
 * because a position is a preference the operator did not state.
 */
export interface TargetBuild {
    readonly id: string
    readonly label: string
    readonly targetTrainee: string
    /** Resolved chara_id for the target, or null when the shipped relation tables do not cover it. */
    readonly targetCharaId: number | null
    readonly distance: TargetDistance | null
    readonly surface: TargetSurface | null
    readonly runningStyle: TargetRunningStyle | null
    readonly scenario: string | null
    readonly priorityStatFactors: readonly string[]
    readonly priorityAptitudeFactors: readonly string[]
    readonly priorityUniqueFactors: readonly string[]
    readonly priorityWhiteFactors: readonly string[]
    readonly statPriorityOrigin: StatPriorityOrigin
    /** Roster aptitude keys this build cares about, in a fixed order. Used as a reported dimension,
     * never as a filter: a candidate is never silently removed for a grade. */
    readonly rosterAptitudeKeys: readonly RosterAptitudeKey[]
    readonly gaps: readonly TargetBuildGap[]
}

function dedupeSorted(names: readonly string[]): readonly string[] {
    const seen = new Map<string, string>()
    for (const name of names) {
        const trimmed = name.trim()
        if (trimmed.length === 0) continue
        const key = trimmed.toUpperCase()
        if (!seen.has(key)) seen.set(key, trimmed)
    }
    return [...seen.values()].sort((a, b) => (a < b ? -1 : a > b ? 1 : 0))
}

/** Parses a distance name, or null when it names none. Case-insensitive. */
export function parseTargetDistance(value: string | null | undefined): TargetDistance | null {
    if (!value) return null
    const v = value.trim().toLowerCase() as TargetDistance
    return TARGET_DISTANCES.includes(v) ? v : null
}

/** Parses a surface name, or null when it names none. */
export function parseTargetSurface(value: string | null | undefined): TargetSurface | null {
    if (!value) return null
    const v = value.trim().toLowerCase() as TargetSurface
    return TARGET_SURFACES.includes(v) ? v : null
}

/** Parses a running style, or null when it names none. */
export function parseTargetRunningStyle(value: string | null | undefined): TargetRunningStyle | null {
    if (!value) return null
    const v = value.trim().toLowerCase() as TargetRunningStyle
    return TARGET_RUNNING_STYLES.includes(v) ? v : null
}

/**
 * Resolves a request into a build.
 *
 * The relation index is consulted only to resolve the target's chara_id. An unresolved target is not
 * an error: the build is still usable for factor relevance, and the affinity component simply reports
 * itself as unresolved everywhere instead of being fabricated.
 */
export function buildTargetBuild(request: TargetBuildRequest, index: SuccessionRelationIndex): TargetBuild {
    const distance = request.distance ?? null
    const surface = request.surface ?? null
    const runningStyle = request.runningStyle ?? null

    const impliedAptitudes: string[] = []
    if (distance) impliedAptitudes.push(DISTANCE_APTITUDE_FACTOR[distance])
    if (surface) impliedAptitudes.push(SURFACE_APTITUDE_FACTOR[surface])
    if (runningStyle) impliedAptitudes.push(RUNNING_STYLE_APTITUDE_FACTOR[runningStyle])

    const operatorStats = request.statFactors ?? null
    let statPriorityOrigin: StatPriorityOrigin = "NONE"
    let statFactors: readonly string[] = []
    if (operatorStats && operatorStats.length > 0) {
        statFactors = dedupeSorted(operatorStats)
        statPriorityOrigin = "OPERATOR"
    } else if (distance) {
        statFactors = dedupeSorted(DEFAULT_STAT_PRIORITY_BY_DISTANCE[distance])
        statPriorityOrigin = "DEFAULT_BY_DISTANCE"
    }

    const aptitudeFactors = dedupeSorted([...impliedAptitudes, ...(request.aptitudeFactors ?? [])])
    const uniqueFactors = dedupeSorted(request.uniqueFactors ?? [])
    const whiteFactors = dedupeSorted(request.whiteFactors ?? [])

    const rosterAptitudeKeys: RosterAptitudeKey[] = []
    if (distance) rosterAptitudeKeys.push(DISTANCE_ROSTER_KEY[distance])
    if (surface) rosterAptitudeKeys.push(SURFACE_ROSTER_KEY[surface])
    if (runningStyle) rosterAptitudeKeys.push(RUNNING_STYLE_ROSTER_KEY[runningStyle])

    const targetCharaId = resolveCharaId(index, request.targetTrainee)

    const gaps: TargetBuildGap[] = []
    if (targetCharaId === null) gaps.push("TARGET_CHARACTER_NOT_IN_RELATION_DOMAIN")
    if (!distance) gaps.push("DISTANCE_UNSPECIFIED")
    if (!surface) gaps.push("SURFACE_UNSPECIFIED")
    if (!runningStyle) gaps.push("RUNNING_STYLE_UNSPECIFIED")
    if (statFactors.length + aptitudeFactors.length + uniqueFactors.length + whiteFactors.length === 0) gaps.push("NO_PRIORITY_FACTORS")

    const id = [normalizeCharacterName(request.targetTrainee) || "UNNAMED", (distance ?? "any").toUpperCase(), (surface ?? "any").toUpperCase(), (runningStyle ?? "any").toUpperCase()].join("_")

    const label = request.label?.trim() || `${request.targetTrainee}${distance ? ` ${distance}` : ""}${surface ? ` ${surface}` : ""}${runningStyle ? ` ${runningStyle}` : ""}`

    return {
        id,
        label,
        targetTrainee: request.targetTrainee,
        targetCharaId,
        distance,
        surface,
        runningStyle,
        scenario: request.scenario ?? null,
        priorityStatFactors: statFactors,
        priorityAptitudeFactors: aptitudeFactors,
        priorityUniqueFactors: uniqueFactors,
        priorityWhiteFactors: whiteFactors,
        statPriorityOrigin,
        rosterAptitudeKeys,
        gaps,
    }
}

/** The build's priority factor keys, in the `kind:NAME` form the scarcity index uses. */
export function targetPriorityFactorKeys(build: TargetBuild): readonly string[] {
    const keys: string[] = []
    for (const name of build.priorityStatFactors) keys.push(`stat:${name.toUpperCase()}`)
    for (const name of build.priorityAptitudeFactors) keys.push(`aptitude:${name.toUpperCase()}`)
    for (const name of build.priorityUniqueFactors) keys.push(`unique:${name.toUpperCase()}`)
    for (const name of build.priorityWhiteFactors) keys.push(`white:${name.toUpperCase()}`)
    return keys.sort()
}
