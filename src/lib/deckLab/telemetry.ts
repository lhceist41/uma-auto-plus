// DeckLab - the contract a later account-specific learner would consume. Pure, offline, deterministic.
//
// Nothing here trains anything. This phase has no corpus of careers labelled with the deck that ran
// them, so any weight learned now would be fitted to a handful of runs and would look exactly like a
// measured one. What this module does is fix the shape of the observation so the corpus can start
// accumulating in a form a learner could actually use, and so the three kinds of value stay separable
// forever:
//
//   decodedGameValue      read off the game's own tables, true regardless of account
//   communityPrior        someone else's judgement, versioned, never mixed in
//   accountTelemetryValue what this account's own careers actually produced
//
// The deck is recorded by card id and by the level and limit break it was at, not by name and not by
// "the MLB one". A card that gets limit-broken between two careers is a different input, and an
// observation that cannot tell those apart is not worth keeping.

export const DECK_OBSERVATION_SCHEMA = "deck_lab_deck_performance_observation"
export const DECK_OBSERVATION_SCHEMA_VERSION = 1

/** How a career ended, as far as the observation can tell. */
export const CAREER_OUTCOMES = ["COMPLETED", "FAILED", "ABANDONED", "UNKNOWN"] as const
export type CareerOutcome = (typeof CAREER_OUTCOMES)[number]

export interface ObservedDeckSlot {
    readonly supportCardId: number
    /** The level the card was at for this career, which is what its effects were read at. */
    readonly level: number
    readonly limitBreak: number
    readonly borrowed: boolean
}

export interface ObservedFinalStats {
    readonly speed: number | null
    readonly stamina: number | null
    readonly power: number | null
    readonly guts: number | null
    readonly wit: number | null
}

export interface DeckPerformanceObservation {
    readonly schema: string
    readonly schemaVersion: number
    /** Joins this observation to the career telemetry this repository already writes. */
    readonly careerToken: string
    readonly scenarioId: number | null
    readonly traineeCharaId: number | null
    /** Exactly the deck that ran, at the levels it ran at. */
    readonly deck: readonly ObservedDeckSlot[]
    readonly outcome: CareerOutcome
    readonly finalStats: ObservedFinalStats
    readonly skillPoints: number | null
    readonly scenarioScore: number | null
    readonly fans: number | null
    /**
     * Fields the observation could not fill. A learner must be able to tell "this career scored zero"
     * from "nobody recorded the score", and a null alone does not carry that.
     */
    readonly missingFields: readonly string[]
}

export class DeckObservationError extends Error {
    constructor(message: string) {
        super(message)
        this.name = "DeckObservationError"
    }
}

const REQUIRED_FOR_LEARNING = ["outcome", "finalStats", "skillPoints", "scenarioScore"] as const

export interface ObservationInput {
    readonly careerToken: string
    readonly scenarioId?: number | null
    readonly traineeCharaId?: number | null
    readonly deck: readonly ObservedDeckSlot[]
    readonly outcome?: CareerOutcome
    readonly finalStats?: Partial<ObservedFinalStats>
    readonly skillPoints?: number | null
    readonly scenarioScore?: number | null
    readonly fans?: number | null
}

/**
 * Builds an observation, recording what it could not fill rather than defaulting it.
 *
 * Refuses a career with no token or an empty deck: both would produce a row that cannot be joined to
 * anything or attributed to anything, which is worse than no row.
 */
export function buildDeckObservation(input: ObservationInput): DeckPerformanceObservation {
    if (!input.careerToken) throw new DeckObservationError("an observation needs a careerToken to join on")
    if (!input.deck.length) throw new DeckObservationError("an observation needs the deck that ran")

    const finalStats: ObservedFinalStats = {
        speed: input.finalStats?.speed ?? null,
        stamina: input.finalStats?.stamina ?? null,
        power: input.finalStats?.power ?? null,
        guts: input.finalStats?.guts ?? null,
        wit: input.finalStats?.wit ?? null,
    }

    const missing: string[] = []
    if (!input.outcome || input.outcome === "UNKNOWN") missing.push("outcome")
    if (Object.values(finalStats).some((v) => v === null)) missing.push("finalStats")
    if (input.skillPoints === undefined || input.skillPoints === null) missing.push("skillPoints")
    if (input.scenarioScore === undefined || input.scenarioScore === null) missing.push("scenarioScore")
    if (input.fans === undefined || input.fans === null) missing.push("fans")

    return {
        schema: DECK_OBSERVATION_SCHEMA,
        schemaVersion: DECK_OBSERVATION_SCHEMA_VERSION,
        careerToken: input.careerToken,
        scenarioId: input.scenarioId ?? null,
        traineeCharaId: input.traineeCharaId ?? null,
        deck: [...input.deck].sort((a, b) => a.supportCardId - b.supportCardId),
        outcome: input.outcome ?? "UNKNOWN",
        finalStats,
        skillPoints: input.skillPoints ?? null,
        scenarioScore: input.scenarioScore ?? null,
        fans: input.fans ?? null,
        missingFields: [...new Set(missing)].sort(),
    }
}

export interface CorpusReadiness {
    readonly observations: number
    readonly usableForLearning: number
    readonly distinctDecks: number
    readonly distinctScenarios: number
    /** Nothing here trains a model. This says whether a later one would have anything to train on. */
    readonly readyForBaseline: boolean
    readonly blockers: readonly string[]
}

/**
 * The number of usable observations below which a learned per-card value would be fitted noise.
 *
 * Deliberately blunt. Deck effects are entangled across six slots, so separating one card's
 * contribution needs many careers in which that card varies while the rest do not. This threshold is
 * a floor on even attempting it, not a promise that clearing it is enough.
 */
export const MIN_OBSERVATIONS_FOR_BASELINE = 60

export function assessCorpus(observations: readonly DeckPerformanceObservation[]): CorpusReadiness {
    const usable = observations.filter((o) => !o.missingFields.some((f) => (REQUIRED_FOR_LEARNING as readonly string[]).includes(f)))
    const deckKeys = new Set(observations.map((o) => o.deck.map((s) => `${s.supportCardId}@${s.level}`).join(",")))
    const scenarios = new Set(observations.map((o) => o.scenarioId).filter((id) => id !== null))

    const blockers: string[] = []
    if (usable.length < MIN_OBSERVATIONS_FOR_BASELINE) blockers.push(`${usable.length} usable observations, ${MIN_OBSERVATIONS_FOR_BASELINE} is the floor for attempting a baseline`)
    if (deckKeys.size < 2) blockers.push("every observation used the same deck, so no card's contribution can be separated from any other's")
    if (scenarios.size > 1 && usable.length < MIN_OBSERVATIONS_FOR_BASELINE * scenarios.size) {
        blockers.push(`${scenarios.size} scenarios are mixed, and card value is scenario-specific, so each needs its own observations`)
    }

    return {
        observations: observations.length,
        usableForLearning: usable.length,
        distinctDecks: deckKeys.size,
        distinctScenarios: scenarios.size,
        readyForBaseline: blockers.length === 0,
        blockers,
    }
}
