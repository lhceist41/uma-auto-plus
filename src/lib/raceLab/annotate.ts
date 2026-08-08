// RaceLab v1 - historical annotation (pure, ReplayLab-independent).
//
// Annotates a historical sequenced turn with FACTS drawn only from an objective timeline plus the turn's
// own recorded action/race-day flags. Hard restriction: the identity of an entered OPTIONAL race is not in
// current telemetry, so it is always reported as `unavailable` and NEVER inferred. This is a pure function
// over a minimal record shape - it does not import or modify ReplayLab.

import { classifyRaceFit } from "./fit.ts"
import type { ObjectiveTimeline, CareerStateAptitudes, RaceFit, RaceKey } from "./types.ts"

/** The minimal per-turn history a caller extracts from ReplayLab-style records. */
export interface HistoricalTurnInput {
    /** Authoritative sequence number if the turn was JOINED; diagnostic only. */
    seq?: number | null
    /** Observed turn number, or null when the date was unread. */
    turn: number | null
    /** The committed main-screen action for the turn (e.g. "RACE", "TRAIN"), or null. */
    committedAction: string | null
    /** Recorded pre-decision race-day flags, if available. */
    raceDayFlags?: { mandatory: boolean; scheduled: boolean; goalRibbon: boolean } | null
}

/** One objective option's factual annotation for a historical turn. */
export interface AnnotatedObjectiveOption {
    key: RaceKey
    raceName: string
    fit: RaceFit | null
}

/** The factual annotation of one historical turn. `enteredRaceIdentity` is always "unavailable". */
export interface TurnAnnotation {
    seq: number | null
    turn: number | null
    raceActionRecorded: boolean
    isObjectiveTurn: boolean
    objectiveIsChoice: boolean
    objectiveOptions: AnnotatedObjectiveOption[]
    raceDayFlags: { mandatory: boolean; scheduled: boolean; goalRibbon: boolean } | null
    /** Always "unavailable": entered optional-race identity is not recorded in current telemetry. */
    enteredRaceIdentity: "unavailable"
}

/**
 * Annotates one historical turn. When the turn matches an objective requirement, the objective options
 * (and, if aptitudes are given, their fit) are attached. A RACE action on a non-objective turn is recorded
 * only as "a race action happened", with race identity unavailable.
 */
export function annotateHistoricalTurn(input: HistoricalTurnInput, objectiveTimeline: ObjectiveTimeline | undefined, aptitudes?: CareerStateAptitudes): TurnAnnotation {
    const requirement = input.turn !== null && objectiveTimeline ? objectiveTimeline.requirements.find((r) => r.turn === input.turn) : undefined
    const objectiveOptions: AnnotatedObjectiveOption[] = requirement
        ? requirement.options.map((o) => ({ key: o.canonicalRace.key, raceName: o.raceName, fit: aptitudes ? classifyRaceFit(o.canonicalRace, aptitudes) : null }))
        : []
    return {
        seq: input.seq ?? null,
        turn: input.turn,
        raceActionRecorded: input.committedAction === "RACE",
        isObjectiveTurn: requirement !== undefined,
        objectiveIsChoice: requirement?.isChoice ?? false,
        objectiveOptions,
        raceDayFlags: input.raceDayFlags ?? null,
        enteredRaceIdentity: "unavailable",
    }
}

/** Annotates a whole historical sequence, sorted by seq then turn for deterministic output. */
export function annotateHistory(inputs: readonly HistoricalTurnInput[], objectiveTimeline: ObjectiveTimeline | undefined, aptitudes?: CareerStateAptitudes): TurnAnnotation[] {
    return [...inputs]
        .sort((a, b) => (a.seq ?? Number.MAX_SAFE_INTEGER) - (b.seq ?? Number.MAX_SAFE_INTEGER) || (a.turn ?? Number.MAX_SAFE_INTEGER) - (b.turn ?? Number.MAX_SAFE_INTEGER))
        .map((i) => annotateHistoricalTurn(i, objectiveTimeline, aptitudes))
}
