// DeckLab - the target a deck is built for. Pure, offline, deterministic.
//
// "Which deck is best" has no answer on its own; it has an answer under a named target. DeckLab reuses
// ParentLab's distance, surface and running-style vocabulary rather than inventing a second one, so a
// Long turf End Closer means the same thing in both labs and an operator learns one set of flags.
//
// Where the two labs genuinely differ:
//
//   ParentLab's TargetBuild is expressed in canonical inheritance factor names and needs the
//   succession-relation index to resolve a trainee. DeckLab has no use for factors and does not want
//   that dependency, so a DeckTargetBuild carries training stats instead, and resolves the trainee
//   against the support-card catalogue's own character table.
//
//   DeckLab needs a scenario. ParentLab does not: a Veteran's factors are the same whatever scenario
//   produced them, whereas a support card can be forbidden outright by one.
//
// The stat priorities a distance implies are an editorial default, exactly as in ParentLab, and
// statPriorityOrigin says which they were. Nothing decoded in this repository states which stats a
// distance wants.

import { TARGET_DISTANCES, TARGET_RUNNING_STYLES, TARGET_SURFACES, parseTargetDistance, parseTargetRunningStyle, parseTargetSurface, type TargetDistance, type TargetRunningStyle, type TargetSurface } from "../parentLab/targetBuild.ts"
import { normalizeName, TRAINING_SUPPORT_TYPES, type ScenarioRecord, type SupportCardIndex, type TrainingSupportType } from "./supportCardData.ts"

export { TARGET_DISTANCES, TARGET_RUNNING_STYLES, TARGET_SURFACES, parseTargetDistance, parseTargetRunningStyle, parseTargetSurface }
export type { TargetDistance, TargetRunningStyle, TargetSurface }

/**
 * Stat priorities implied by a distance.
 *
 * Editorial, and marked as such wherever it is used. It is the same list ParentLab uses for blue
 * factors, extended to a full ordering over the five training stats so a deck search has something to
 * rank every card against rather than only the top two. An operator list always replaces it.
 */
export const DEFAULT_STAT_PRIORITY_BY_DISTANCE: Readonly<Record<TargetDistance, readonly TrainingSupportType[]>> = {
    sprint: ["Speed", "Power", "Guts", "Wit", "Stamina"],
    mile: ["Speed", "Power", "Wit", "Stamina", "Guts"],
    medium: ["Speed", "Stamina", "Power", "Wit", "Guts"],
    long: ["Stamina", "Speed", "Power", "Wit", "Guts"],
}

export const STAT_PRIORITY_ORIGINS = ["OPERATOR", "DEFAULT_BY_DISTANCE", "NONE"] as const
export type StatPriorityOrigin = (typeof STAT_PRIORITY_ORIGINS)[number]

/** Things about a target that DeckLab could not resolve, carried so a report can say so. */
export const DECK_TARGET_GAPS = [
    "TRAINEE_NOT_IN_CATALOGUE",
    "TRAINEE_NOT_NAMED",
    "SCENARIO_NOT_IN_CATALOGUE",
    "STAT_PRIORITY_IS_EDITORIAL",
    "NO_PRIORITY_SKILLS",
] as const
export type DeckTargetGap = (typeof DECK_TARGET_GAPS)[number]

export interface DeckTargetRequest {
    readonly trainee?: string | null
    readonly scenario?: string | null
    readonly distance?: TargetDistance | null
    readonly surface?: TargetSurface | null
    readonly runningStyle?: TargetRunningStyle | null
    /** Ordered training stats. Replaces the distance default entirely when given. */
    readonly statPriority?: readonly string[] | null
    /** Skill ids the build wants. Used only against the decoded hint pools. */
    readonly prioritySkillIds?: readonly number[] | null
    readonly label?: string | null
}

export interface DeckTargetBuild {
    readonly label: string
    readonly traineeName: string | null
    /** Resolved against the catalogue's character table; null when the trainee is unknown to it. */
    readonly traineeCharaId: number | null
    readonly scenario: ScenarioRecord | null
    readonly scenarioName: string
    readonly distance: TargetDistance | null
    readonly surface: TargetSurface | null
    readonly runningStyle: TargetRunningStyle | null
    readonly statPriority: readonly TrainingSupportType[]
    readonly statPriorityOrigin: StatPriorityOrigin
    /** Priority stat -> weight in [0,1], highest priority first. Editorial, like the priority itself. */
    readonly statWeight: Readonly<Record<TrainingSupportType, number>>
    readonly prioritySkillIds: ReadonlySet<number>
    readonly gaps: readonly DeckTargetGap[]
}

export class DeckTargetError extends Error {
    constructor(message: string) {
        super(message)
        this.name = "DeckTargetError"
    }
}

/**
 * Scenario names an operator is likely to type, mapped onto the catalogue's own scenario ids.
 *
 * The catalogue carries the game's shipped titles, which are marketing lines rather than the short
 * names this repository and its settings use. This table is the bridge, and it is matched
 * punctuation-insensitively so "grand-concert" and "Grand Concert" both land.
 */
const SCENARIO_ALIASES: Readonly<Record<string, number>> = {
    urafinale: 1,
    ura: 1,
    unitycup: 2,
    unity: 2,
    grandconcert: 3,
    gc: 3,
    grandlive: 3,
    trackblazer: 4,
    twinkstarclimax: 4,
    twinklestarclimax: 4,
}

/** Resolves a scenario by short name, shipped title, or numeric id. Null when nothing matches. */
export function resolveScenario(index: SupportCardIndex, value: string | number | null | undefined): ScenarioRecord | null {
    if (value === null || value === undefined || value === "") return null
    if (typeof value === "number") return index.scenario(value)
    const raw = String(value).trim()
    if (/^\d+$/.test(raw)) return index.scenario(Number(raw))
    const key = normalizeName(raw)
    if (SCENARIO_ALIASES[key] !== undefined) return index.scenario(SCENARIO_ALIASES[key])
    for (const scenario of index.data.scenarios) {
        const name = normalizeName(scenario.name)
        if (name && (name === key || name.includes(key) || key.includes(name))) return scenario
    }
    return null
}

export function parseStatPriority(values: readonly string[] | null | undefined): TrainingSupportType[] | null {
    if (!values || !values.length) return null
    const out: TrainingSupportType[] = []
    for (const raw of values) {
        const key = normalizeName(raw)
        const match = TRAINING_SUPPORT_TYPES.find((s) => normalizeName(s) === key)
        if (!match) throw new DeckTargetError(`${raw} is not one of ${TRAINING_SUPPORT_TYPES.join(", ")}`)
        if (!out.includes(match)) out.push(match)
    }
    return out
}

/**
 * Weights for an ordered stat priority.
 *
 * A linear ramp from 1 down to a floor, so a stat that is not a priority still counts for something
 * rather than nothing. Stats the operator did not name at all sit at the floor. Editorial, like the
 * ordering it weights.
 */
const STAT_WEIGHT_FLOOR = 0.2

export function statWeightsFor(priority: readonly TrainingSupportType[]): Record<TrainingSupportType, number> {
    const weights = {} as Record<TrainingSupportType, number>
    for (const stat of TRAINING_SUPPORT_TYPES) weights[stat] = STAT_WEIGHT_FLOOR
    if (!priority.length) return weights
    const span = 1 - STAT_WEIGHT_FLOOR
    const last = Math.max(1, priority.length - 1)
    priority.forEach((stat, i) => {
        weights[stat] = Number((1 - (span * i) / last).toFixed(4))
    })
    return weights
}

/** Resolves a trainee name against the catalogue's character table. */
function resolveTraineeCharaId(index: SupportCardIndex, name: string): number | null {
    const key = normalizeName(name)
    for (const [charaId, charaName] of Object.entries(index.data.characters)) {
        if (normalizeName(charaName) === key) return Number(charaId)
    }
    return null
}

export function buildDeckTarget(request: DeckTargetRequest, index: SupportCardIndex): DeckTargetBuild {
    const gaps: DeckTargetGap[] = []

    const traineeName = request.trainee?.trim() || null
    let traineeCharaId: number | null = null
    if (!traineeName) gaps.push("TRAINEE_NOT_NAMED")
    else {
        traineeCharaId = resolveTraineeCharaId(index, traineeName)
        if (traineeCharaId === null) gaps.push("TRAINEE_NOT_IN_CATALOGUE")
    }

    const scenario = resolveScenario(index, request.scenario ?? null)
    if (request.scenario && !scenario) gaps.push("SCENARIO_NOT_IN_CATALOGUE")

    const operatorPriority = parseStatPriority(request.statPriority ?? null)
    let statPriority: readonly TrainingSupportType[]
    let statPriorityOrigin: StatPriorityOrigin
    if (operatorPriority) {
        statPriority = operatorPriority
        statPriorityOrigin = "OPERATOR"
    } else if (request.distance) {
        statPriority = DEFAULT_STAT_PRIORITY_BY_DISTANCE[request.distance]
        statPriorityOrigin = "DEFAULT_BY_DISTANCE"
        gaps.push("STAT_PRIORITY_IS_EDITORIAL")
    } else {
        statPriority = []
        statPriorityOrigin = "NONE"
    }

    const prioritySkillIds = new Set(request.prioritySkillIds ?? [])
    if (!prioritySkillIds.size) gaps.push("NO_PRIORITY_SKILLS")

    const parts = [traineeName ?? "any trainee", scenario?.name ?? request.scenario ?? "any scenario", request.distance ?? "any distance"]
    return {
        label: request.label?.trim() || parts.join(" / "),
        traineeName,
        traineeCharaId,
        scenario,
        scenarioName: scenario?.name ?? (request.scenario ? String(request.scenario) : "any scenario"),
        distance: request.distance ?? null,
        surface: request.surface ?? null,
        runningStyle: request.runningStyle ?? null,
        statPriority,
        statPriorityOrigin,
        statWeight: statWeightsFor(statPriority),
        prioritySkillIds,
        gaps: [...new Set(gaps)].sort(),
    }
}
