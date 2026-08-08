// RaceLab v1 - shared types.
//
// RaceLab is a factual, offline, read-only consumer of the landed canonical master-data layer. It does
// catalog lookup, objective modeling, aptitude-fit classification, schedule-pressure analysis, and
// race-plan validation. It never chooses races for the bot, never predicts outcomes or win probability,
// never simulates, and never invents missing race identity / fans / goal state. The live runtime policy
// remains the sole authority for what the bot actually does.

import type { CompiledRace, RaceKey } from "../masterData/types.ts"

export type { CompiledRace, RaceKey }

/** Output schema discriminator + version for a RaceLab JSON export. */
export const RACELAB_SCHEMA = "race_lab"
export const RACELAB_SCHEMA_VERSION = 1

/** Aptitude letter grades, lowest to highest. Mirrors the runtime `Aptitude` enum ordering (Types.kt). */
export type AptitudeGrade = "G" | "F" | "E" | "D" | "C" | "B" | "A" | "S"
export const APTITUDE_ORDER: readonly AptitudeGrade[] = ["G", "F", "E", "D", "C", "B", "A", "S"] as const

/**
 * The current-runtime race-entry aptitude gate: `Racing.kt#checkRaceAptitudeMatch` requires BOTH the
 * track-surface and the track-distance aptitude to be `>= B`. This is current-runtime compatibility, not
 * an ideal-strategy claim.
 */
export const RUNTIME_APTITUDE_GATE: AptitudeGrade = "B"

/** Rank of an aptitude letter (0..7), or -1 for an unknown/invalid letter. */
export function aptitudeRank(grade: string): number {
    return APTITUDE_ORDER.indexOf(grade as AptitudeGrade)
}

/**
 * Aptitude maps in the CareerState-compatible shape (uppercase enum-name keys, letter-grade values),
 * e.g. `{ surface: { TURF: "A", DIRT: "C" }, distance: { SPRINT: "C", MILE: "A", MEDIUM: "B", LONG: "G" } }`.
 * Every field is optional; an absent map or key means the grade is unavailable, never a default.
 */
export interface CareerStateAptitudes {
    surface?: Record<string, string>
    distance?: Record<string, string>
    style?: Record<string, string>
}

/** One axis (surface or distance) of a race's factual aptitude fit. `grade` null = unavailable, never defaulted. */
export interface AxisFit {
    axis: "surface" | "distance"
    /** The uppercase aptitude-map key this race maps to, e.g. "TURF" or "MILE". */
    aptitudeKey: string
    grade: AptitudeGrade | null
    /** grade >= B, or null when the grade is unavailable. Current-runtime gate semantics, not strategy. */
    meetsRuntimeGate: boolean | null
    status: "known" | "unavailable"
}

/** A race's factual aptitude fit. Running-style fit is intentionally omitted: races carry no style requirement. */
export interface RaceFit {
    surface: AxisFit
    distance: AxisFit
    /** Both axes >= B (the exact current-runtime gate), or null when either grade is unavailable. */
    meetsCurrentRuntimeAptitudeGate: boolean | null
}

/** Deterministic catalog statistics. */
export interface CatalogStats {
    raceCount: number
    uniqueKeyCount: number
    distinctBareNameCount: number
    bareNameCollisionCount: number
}

/** A scenario RaceLab can scope its claims to. character_objectives.json models URA career objectives only. */
export type Scenario = "URA" | "GrandConcert" | "Trackblazer" | "UnityCup"

/** One objective race option, resolved canonically. `rawMeta` is the objective record's own (display) fields. */
export interface ResolvedObjectiveOption {
    raceName: string
    canonicalRace: CompiledRace
    rawMeta: { grade: string | null; surface: string | null; distanceType: string | null; fans: number | null }
}

/** One objective turn: single-option (isChoice=false) or a choice among several canonical races. */
export interface ObjectiveRequirement {
    turn: number
    isChoice: boolean
    options: ResolvedObjectiveOption[]
}

/** A trainee's objective timeline. `scenario` is always URA in v1 (the only source-proven objective set). */
export interface ObjectiveTimeline {
    character: string
    scenario: Scenario
    requirements: ObjectiveRequirement[]
}

/** A parsed racing-plan entry, matching the runtime `PlannedRace` shape (Racing.kt#loadUserPlannedRaces). */
export interface PlannedRace {
    raceName: string
    date: string
    turnNumber: number
    priority: number
}

/** Severity of a plan-validation finding. */
export type IssueSeverity = "error" | "warning" | "info"

/** One plan-validation finding. `turn` present when the finding is turn-scoped. */
export interface PlanIssue {
    severity: IssueSeverity
    code: string
    detail: string
    turn: number | null
}

/** Where a scheduled race turn came from. */
export type ScheduleSource = "objective" | "plan" | "both"

/** One scheduled race turn (a turn carrying at least one required/planned race). */
export interface ScheduleEntry {
    turn: number
    source: ScheduleSource
    /** Number of distinct races landing on this exact turn (>1 = a same-turn stack). */
    raceCount: number
}

/** A maximal run of consecutive turns each carrying a race. */
export interface StreakWindow {
    kind: "streak"
    startTurn: number
    endTurn: number
    length: number
    source: ScheduleSource
    /**
     * Whether this streak reaches/exceeds a supplied consecutive-race limit. Null unless a limit was
     * given. The only source-proven limit is Trackblazer's `trackblazerConsecutiveRacesLimit` (default 2);
     * it is scenario-specific and configurable, never a universal rule.
     */
    reachesConsecutiveLimit: boolean | null
}

/** A turn carrying more than one distinct race. */
export interface SameTurnWindow {
    kind: "sameTurn"
    turn: number
    raceCount: number
    source: ScheduleSource
}

/** A gap (in turns) between two adjacent scheduled race turns. */
export interface GapWindow {
    kind: "gap"
    fromTurn: number
    toTurn: number
    gap: number
}

/** The deterministic schedule-pressure report. All windows are descriptive, never causal. */
export interface PressureReport {
    entries: ScheduleEntry[]
    streaks: StreakWindow[]
    sameTurn: SameTurnWindow[]
    gaps: GapWindow[]
}

/** The result of validating a plan. Issues are severity-tagged and deterministically ordered. */
export interface PlanValidationReport {
    plan: PlannedRace[]
    issues: PlanIssue[]
    pressure: PressureReport
    ok: boolean
}
