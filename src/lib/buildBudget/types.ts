// STAM-2 Joint Build Budget Planner - shared types, version constants and evidence vocabulary.
//
// The planner answers one question: for this race and this survival requirement, which combination of
// parent pair, support deck and borrow best pays for the Stamina the race needs, and what does paying
// for it cost in Speed, Power, Guts, Wit, skill points and deck-slot quality.
//
// It is pure, offline, deterministic and Shadow-only. Nothing in here is read by the bot at runtime:
// no training choice, no lesson purchase, no skill buy, no deck selection, no borrow selection and no
// career launch depends on any of it.
//
// The design rule that shapes every type below is that no creator heuristic is encoded. There is no
// rule saying "0% Stamina growth wants a Stamina parent" and no rule saying "never inherit Stamina on
// Super Creek". What is modelled is the underlying resource problem the heuristics are approximations
// of: a survival requirement, the sources that can supply it, the surplus or deficit that leaves, and
// the marginal cost of closing that deficit each way. Where the heuristics are right, they fall out.
//
// Four evidence channels travel with every number, and they are never blended:
//
//   DECODED_GAME_DATA            read out of the installed game's master.mdb. The inheritance ladders,
//                                the base training gains, the growth rates, the starting stats, the
//                                stat caps, the career turn count, every support-card effect value.
//   EXTERNAL_MECHANICS_REFERENCE formulas this repository has NOT decoded locally: how the decoded
//                                per-card percentages combine into one training's gain, and the race
//                                physics STAM-1 already carries.
//   EDITORIAL_MODEL              judgement calls this planner makes so it can produce a number at all:
//                                the training-turn budget, the archetype turn profiles, the weights
//                                in any composite. Never a game figure.
//   ACCOUNT_TELEMETRY            reserved. STAM-2 consumes no telemetry; the fields a later phase
//                                would calibrate are named in CALIBRATION_POINTS and left alone.

import type { TrainingSupportType } from "../deckLab/supportCardData.ts"
import { TRAINING_SUPPORT_TYPES } from "../deckLab/supportCardData.ts"
import type { EvidenceChannel, SurvivalConstraint } from "../raceSurvival/types.ts"

/** Output schema discriminator and version for a Joint Build Budget export. */
export const BUILD_BUDGET_SCHEMA = "joint_build_budget"
export const BUILD_BUDGET_SCHEMA_VERSION = 1

/**
 * The five stats a budget is kept in.
 *
 * DeckLab's training-stat vocabulary, reused rather than redefined, so a "Power" budget, a "Power"
 * support card and a "Power" Spark all mean the same thing across the three labs.
 */
export const BUDGET_STATS = TRAINING_SUPPORT_TYPES
export type BudgetStat = TrainingSupportType

/** Where a number came from. */
export const BUDGET_EVIDENCE_CHANNELS = ["DECODED_GAME_DATA", "EXTERNAL_MECHANICS_REFERENCE", "EDITORIAL_MODEL", "ACCOUNT_TELEMETRY"] as const
export type BudgetEvidenceChannel = (typeof BUDGET_EVIDENCE_CHANNELS)[number]

/**
 * Compile-time proof that this planner's channel vocabulary contains STAM-1's own two evidence
 * channels unchanged, so a constant can travel from a survival envelope into a budget without being
 * relabelled on the way.
 */
const _survivalChannelsAreCarried: readonly BudgetEvidenceChannel[] = ["DECODED_GAME_DATA", "EXTERNAL_MECHANICS_REFERENCE"] satisfies readonly Extract<
    EvidenceChannel,
    "DECODED_GAME_DATA" | "EXTERNAL_MECHANICS_REFERENCE"
>[]
void _survivalChannelsAreCarried

/** A constant the planner uses, with the channel it came from and what it rests on. */
export interface BudgetConstant {
    readonly name: string
    readonly value: number
    readonly channel: BudgetEvidenceChannel
    readonly provenance: string
}

/** How far this repository has got with a mechanic the planner would otherwise want. */
export const BUDGET_MECHANIC_STATUSES = ["VERIFIED", "PARTIALLY_DECODED", "UNKNOWN"] as const
export type BudgetMechanicStatus = (typeof BUDGET_MECHANIC_STATUSES)[number]

export interface BudgetMechanicNote {
    readonly mechanic: string
    readonly status: BudgetMechanicStatus
    readonly note: string
}

/**
 * The mechanics this planner cannot price, each named so an operator can see what a number is blind
 * to without reading any source. STAM-1's own unknowns travel separately and are not repeated here.
 */
export const UNPRICED_BUDGET_MECHANICS: readonly BudgetMechanicNote[] = [
    {
        mechanic: "SUCCESSION_STAR_ACCUMULATION",
        status: "PARTIALLY_DECODED",
        note: "The per-factor effect ladder is decoded exactly. How a lineage's star counts add up to a level on that ladder, and whether a grandparent's star weighs the same as a parent's, is not in master.mdb. Inheritance is therefore priced as a range across the ladder rather than a point.",
    },
    {
        mechanic: "TRAINING_GAIN_COMBINATION",
        status: "PARTIALLY_DECODED",
        note: "The base gain per training, and every support-card effect value that modifies it, are decoded. The rule that combines them into one training's actual gain is not decoded here, so every training projection is a bracket: the low end applies no card multiplier at all, the high end applies them all at once.",
    },
    {
        mechanic: "TRAINING_FACILITY_LEVEL_SCALING",
        status: "UNKNOWN",
        note: "single_mode_training_effect is keyed by command, not by the 1..5 facility level the same command carries in single_mode_training. How a level-5 facility scales the base gain is not decoded, and no scaling is applied.",
    },
    {
        mechanic: "TRAINING_TURN_BUDGET",
        status: "UNKNOWN",
        note: "The career length is decoded (78 turns). How many of those turns end up spent on training rather than racing, resting or in events is a policy and a run-time outcome, not a table. The turn budget is an editorial input and is reported as one.",
    },
    {
        mechanic: "SUPPORT_EVENT_STAT_GAINS",
        status: "UNKNOWN",
        note: "Support-card and trainee events hand over stats, and the amounts live in the event tables this planner does not read. No event contribution is estimated; the projection is a floor by that much.",
    },
    {
        mechanic: "FRIENDSHIP_GAUGE_DYNAMICS",
        status: "UNKNOWN",
        note: "Initial bond and the friendship threshold are decoded, but how many turns a given deck takes to switch its multipliers on is not. Friendship ramp is reported as a comparative burden index, never as turns.",
    },
    {
        mechanic: "SCENARIO_TRAINING_MECHANICS",
        status: "PARTIALLY_DECODED",
        note: "The per-scenario base training gains and stat cap bonuses are decoded. Scenario-specific systems on top of them (Grand Concert lessons, Trackblazer gimmicks, Unity Cup links) are not priced.",
    },
]

/** Where future account telemetry would enter, named so it does not enter anywhere else by accident. */
export const CALIBRATION_POINTS: readonly string[] = [
    "StatBudget.deckTrainingContributionEstimate: measured stat gain per training from decision_trace, replacing the decoded-base bracket.",
    "StatBudget.supportEventEstimate: measured event stat income per career from the outcome corpus, currently zero.",
    "StatBudget.projectedLow/Median/High: measured final-stat quantiles per (trainee, scenario, deck) from careers.jsonl.",
    "BuildBudgetInput.trainingTurns: measured trainings per career, replacing the editorial turn budget.",
]

/** How confident an answer is. Never "high": every projection here rests on an undecoded combination rule. */
export const BUDGET_CONFIDENCES = ["low", "moderate"] as const
export type BudgetConfidence = (typeof BUDGET_CONFIDENCES)[number]

/** The lower of two confidences. The joint answer is never more confident than its weakest input. */
export function weakestConfidence(...values: readonly BudgetConfidence[]): BudgetConfidence {
    return values.includes("low") ? "low" : "moderate"
}

/** A bracketed quantity. The planner never reports a bare projected stat. */
export interface Bracket {
    readonly low: number
    readonly median: number
    readonly high: number
}

export function bracketOf(low: number, high: number): Bracket {
    const lo = Math.min(low, high)
    const hi = Math.max(low, high)
    return { low: lo, median: (lo + hi) / 2, high: hi }
}

export function addBrackets(a: Bracket, b: Bracket): Bracket {
    return { low: a.low + b.low, median: a.median + b.median, high: a.high + b.high }
}

export function zeroBracket(): Bracket {
    return { low: 0, median: 0, high: 0 }
}

/** Clamps a bracket to a ceiling, which is what a stat cap does to a projection. */
export function capBracket(bracket: Bracket, ceiling: number): Bracket {
    return { low: Math.min(bracket.low, ceiling), median: Math.min(bracket.median, ceiling), high: Math.min(bracket.high, ceiling) }
}

/** The trainee half of a budget input. Starting stats and growth are decoded; the caller may override. */
export interface BudgetTrainee {
    readonly traineeName: string
    /** The card id of the specific outfit, when one resolved. Growth is per outfit, not per character. */
    readonly cardId: number | null
    readonly starLevel: number | null
    readonly startStats: Readonly<Record<BudgetStat, number>>
    /** Growth percentages. The five sum to thirty for every playable outfit. */
    readonly growth: Readonly<Record<BudgetStat, number>>
    readonly aptitudes: Readonly<Record<string, string>> | null
    readonly origin: "DECODED" | "OPERATOR"
}

/** One factor a parent carries, as the Veteran reader produces it. */
export interface BudgetFactor {
    readonly family: string
    readonly canonicalName: string
    readonly stars: number
}

/** The inheritance half of a budget input, as two parents' factor lists. */
export interface BudgetParentPair {
    readonly label: string
    readonly parentIds: readonly string[]
    readonly factors: readonly BudgetFactor[]
    /** Base relation points between the target and each parent, when ParentLab resolved them. */
    readonly affinityPoints: readonly (number | null)[]
    /** Carried from ParentLab, never recomputed here. */
    readonly evidenceComplete: boolean
}

/** The deck half of a budget input. */
export interface BudgetDeck {
    readonly label: string
    readonly supportCardIds: readonly number[]
    readonly borrowedCardId: number | null
}

/** What a stat's budget looks like once every source has been accounted for. */
export interface StatBudget {
    readonly stat: BudgetStat

    /** From the survival constraint, and only ever set on the stat the constraint is about. */
    readonly requiredFloor: number | null
    readonly preferredRange: readonly [number, number] | null

    /** Decoded, deterministic contributions. These are not estimates. */
    readonly startStat: number
    readonly inheritanceFlat: Bracket
    readonly inheritanceCap: number
    readonly supportInitialStats: number
    readonly supportCapBonus: number
    readonly scenarioCapBonus: number
    readonly statCap: number

    /** Estimated contributions. Bracketed, and never collapsed into the decoded figures above. */
    readonly deckTrainingContributionEstimate: Bracket
    readonly secondaryTrainingContributionEstimate: Bracket
    readonly supportEventEstimate: Bracket
    readonly scenarioContributionEstimate: Bracket

    readonly projected: Bracket
    /** Positive when the floor is not met at the low end of the projection. Null with no floor. */
    readonly deficitToMinimum: number | null
    /** Positive when the median projection sits above the preferred upper bound. Null with no range. */
    readonly surplusAbovePreferred: number | null
    /** True when the projection runs into the stat ceiling, so further investment is wasted. */
    readonly cappedOut: boolean
    readonly confidence: BudgetConfidence
    readonly assumptions: readonly string[]
}

/** How a build's Stamina sits against the survival requirement. */
export const RECOMMENDATION_CLASSES = [
    "BALANCED",
    "STAMINA_DEFICIT",
    /** The midpoint of the projection clears the floor but the pessimistic end does not. */
    "STAMINA_MARGINAL",
    "OVER_STAMINA",
    "RECOVERY_DEPENDENT",
    "POWER_FLEX",
    "STAMINA_FLEX",
    "BORROW_DEPENDENT",
    "CAP_LIMITED",
] as const
export type RecommendationClass = (typeof RECOMMENDATION_CLASSES)[number]

/** The deck-composition strategies the planner evaluates against the same survival requirement. */
export const BUILD_ARCHETYPES = [
    /** The deck carries at least one dedicated Stamina support card. */
    "STAMINA_FLEX",
    /** No Stamina support card; the Stamina floor leans on Power training's secondary gain. */
    "POWER_FLEX",
    /** No Stamina support card and no turns on the Stamina facility at all. */
    "NO_STAMINA_FLEX",
] as const
export type BuildArchetype = (typeof BUILD_ARCHETYPES)[number]

/** Why an archetype was not evaluated for a build. */
export const ARCHETYPE_EXCLUSIONS = ["NO_LEGAL_DECK", "REQUIRES_STAMINA_CARD_NONE_OWNED", "REQUIRES_NO_STAMINA_CARD"] as const
export type ArchetypeExclusion = (typeof ARCHETYPE_EXCLUSIONS)[number]

/** One way a build pays for the Stamina the race needs, and what that way costs. */
export interface TradeoffLine {
    readonly lever: string
    readonly staminaGained: number
    readonly costs: readonly string[]
}

/** A Joint Build Budget error. */
export class BuildBudgetError extends Error {
    readonly code: string
    constructor(code: string, message: string) {
        super(message)
        this.name = "BuildBudgetError"
        this.code = code
    }
}

/** The survival constraint STAM-1 produces, re-exported so a consumer imports one module, not two. */
export type { SurvivalConstraint }
