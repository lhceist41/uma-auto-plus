// STAM-1 Race Survival Shadow Model - shared types, version constants and evidence vocabulary.
//
// The model answers one question and refuses to answer any other: for this exact race, strategy,
// recovery package and debuff-risk budget, how much Stamina does the build need before it stops
// running out of HP. It is pure, offline, deterministic, and it changes nothing the bot does. No
// training choice, no lesson purchase, no skill buy and no career launch reads anything in here.
//
// It is an envelope estimator, not a race simulator. It never models per-frame velocity, never
// predicts a finishing position, and never claims a win probability. Where a mechanic is not decoded
// it says so by name in `unknownMechanics` instead of substituting a plausible number.
//
// Three evidence channels travel with every number the model uses, and they are never blended into
// one undocumented constant:
//
//   DECODED_GAME_DATA            read out of the installed game's master.mdb by
//                                scripts/generate-race-survival-data.mjs. Recovery and debuff HP
//                                fractions, course finish-time bands, condition enums.
//   EXTERNAL_MECHANICS_REFERENCE the race-engine formulas this repository has NOT decoded locally:
//                                the MaxHP expression, the per-strategy HP coefficients, and the
//                                cruise HP drain rate. Carried as named constants with their channel
//                                attached so a reader can see exactly which results depend on them.
//   EDITORIAL_RISK_POLICY        judgement calls, such as "insure against two stamina debuffs" or
//                                "keep 5% of MaxHP in hand". Never a mechanic, always the caller's.

import type { TargetRunningStyle, TargetSurface } from "../parentLab/targetBuild.ts"

/** Output schema discriminator + version for a Race Survival JSON export. */
export const RACE_SURVIVAL_SCHEMA = "race_survival"
export const RACE_SURVIVAL_SCHEMA_VERSION = 1

/**
 * What the model is. Named explicitly because the difference matters: an estimator bounds the answer
 * from decoded race duration and decoded HP effects, a simulator would reproduce the race.
 */
export const RACE_SURVIVAL_MODEL_KIND = "SURVIVAL_ENVELOPE_ESTIMATOR"

/** Where a number came from. Every constant, every input and every output carries one. */
export const EVIDENCE_CHANNELS = ["DECODED_GAME_DATA", "EXTERNAL_MECHANICS_REFERENCE", "EDITORIAL_RISK_POLICY"] as const
export type EvidenceChannel = (typeof EVIDENCE_CHANNELS)[number]

/** A constant the model uses, with the channel it came from and what it rests on. */
export interface EvidencedConstant {
    readonly name: string
    readonly value: number
    readonly channel: EvidenceChannel
    readonly provenance: string
}

/**
 * A running style the model can price.
 *
 * The first four are ParentLab's and DeckLab's own vocabulary, reused rather than redefined, so a
 * "late" build means the same thing in all three. Runaway is added here because it is a distinct HP
 * profile in the race engine rather than a distinct build target, and no other lab has a use for it.
 */
export const RACE_STRATEGIES = ["front", "pace", "late", "end", "runaway"] as const
export type RaceStrategy = (typeof RACE_STRATEGIES)[number]

/** Compile-time proof that the four shared styles stay in step with ParentLab's vocabulary. */
const _styleVocabularyIsShared: readonly TargetRunningStyle[] = ["front", "pace", "late", "end"]
void _styleVocabularyIsShared

/** Track surface. Shared with ParentLab and DeckLab. */
export type RaceSurface = TargetSurface

/** The distance bands the game's skill conditions use (`distance_type`), lowest to highest. */
export const DISTANCE_TYPES = ["sprint", "mile", "medium", "long"] as const
export type DistanceType = (typeof DISTANCE_TYPES)[number]

/**
 * Track condition, carried verbatim from the caller.
 *
 * Nothing in this model reads it yet: the HP cost of a soft or heavy track is not decoded here, and
 * inventing a multiplier for it would be exactly the folklore this model exists to replace. It rides
 * along so a later phase can use it, and its being unused is reported as an unknown mechanic.
 */
export const GROUND_CONDITIONS = ["firm", "good", "soft", "heavy"] as const
export type GroundCondition = (typeof GROUND_CONDITIONS)[number]

/**
 * Mechanics the model cannot price, each with how far this repository has actually got with it.
 *
 * VERIFIED means decoded from local game data and used. PARTIALLY_DECODED means some of it is decoded
 * and the rest is not, with the note saying which half. UNKNOWN means not decoded here at all; the
 * model does not price it and says so in its output.
 */
export const MECHANIC_STATUSES = ["VERIFIED", "PARTIALLY_DECODED", "UNKNOWN"] as const
export type MechanicStatus = (typeof MECHANIC_STATUSES)[number]

export interface MechanicNote {
    readonly mechanic: string
    readonly status: MechanicStatus
    readonly note: string
}

/**
 * The mechanics this model does not price. Every survival result carries the subset that actually
 * bears on it, so an operator can see what the number is blind to without reading this file.
 */
export const UNPRICED_MECHANICS: readonly MechanicNote[] = [
    {
        mechanic: "SKILL_ACTIVATION_PROBABILITY",
        status: "UNKNOWN",
        note: "No activation probability is decoded for any skill. Recovery is reported as potential HP assuming each eligible skill fires once; no expected value is produced.",
    },
    {
        mechanic: "COURSE_SLOPE_GEOMETRY",
        status: "UNKNOWN",
        note: "master.mdb carries no slope or corner geometry: it lives in the client's course assets. Skills gated on slope or corner conditions cannot be resolved, only classified.",
    },
    {
        mechanic: "GROUND_CONDITION_HP_EFFECT",
        status: "UNKNOWN",
        note: "The HP cost of a soft or heavy track is not decoded. groundCondition is carried but not priced.",
    },
    {
        mechanic: "GUTS_LATE_RACE_MITIGATION",
        status: "UNKNOWN",
        note: "Guts is documented externally as reducing late-race HP cost, but no local evidence fixes the relation. Guts is carried and reported, never converted into HP.",
    },
    {
        mechanic: "TARGET_SPEED_HP_CONSUMPTION",
        status: "UNKNOWN",
        note: "HP cost rises with speed above the course base pace. The model prices a single cruise rate over the decoded race duration and does not resolve the last spurt separately.",
    },
    {
        mechanic: "STRATEGY_HP_CONSUMPTION_RATE",
        status: "UNKNOWN",
        note: "Strategy is priced on the MaxHP side only. No per-strategy consumption coefficient is decoded, so the drain rate is the same for every strategy.",
    },
    {
        mechanic: "RUSH_RISK",
        status: "UNKNOWN",
        note: "Becoming rushed costs HP. Neither its trigger rate nor its cost is decoded, so rushRiskPolicy is carried and reported, never priced.",
    },
    {
        mechanic: "RACE_DURATION",
        status: "PARTIALLY_DECODED",
        note: "The per-course finish-time band is decoded from race_course_set. Whether that band is the simulated race duration or a display reference is not proven here, which is why the model reports a range across the band instead of a single figure.",
    },
    {
        mechanic: "APTITUDE_RATE_APPLICATION",
        status: "PARTIALLY_DECODED",
        note: "The distance, ground and running-style aptitude multiplier tables are decoded exactly. Where the race engine applies them is not, so they are shipped as reference data and take no part in the HP math.",
    },
]

/** How a skill's activation is gated, derived from its condition string. Never a probability. */
export const ACTIVATION_CLASSES = [
    /** Gated only on race phase or elapsed distance, both of which every finished race passes through. */
    "PHASE_ONLY",
    /** Gated on the runner's position or the shape of the field, which the model cannot resolve. */
    "POSITION_CONDITIONAL",
    /** Gated on the runner's own HP, so it fires only once the build is already in trouble. */
    "HP_CONDITIONAL",
    /** Gated on course geometry (slope, corner), which is not decoded in this repository. */
    "GEOMETRY_CONDITIONAL",
    /** Gated on an in-race event: an overtake, a lane change, a block, a bad start. */
    "EVENT_CONDITIONAL",
    /** Gated on other skills having already fired. */
    "SKILL_CHAIN_CONDITIONAL",
    /** More than one of the above, or a condition variable this model does not classify. */
    "MIXED",
] as const
export type ActivationClass = (typeof ACTIVATION_CLASSES)[number]

/** Why a piece of recovery evidence does or does not count toward this race. */
export const RECOVERY_SUPPORT_STATUSES = [
    /** Eligible: the skill's hard race gates match this race and it heals its own runner. */
    "SUPPORTED",
    /** The skill's distance, surface or running-style gate excludes this race or strategy. */
    "INELIGIBLE_FOR_TARGET",
    /** The skill's HP effect is not aimed at its own runner, so it never heals this build. */
    "NOT_SELF_TARGETED",
    /** The skill id is absent from the decoded HP effect set. */
    "NOT_IN_EVIDENCE",
] as const
export type RecoverySupportStatus = (typeof RECOVERY_SUPPORT_STATUSES)[number]

/** One recovery skill, resolved against the decoded evidence for one specific race. */
export interface RecoverySkillEvidence {
    readonly skillId: number
    readonly canonicalName: string | null
    /** Fraction of MaxHP this skill restores per activation. Decoded; null when unresolvable. */
    readonly hpFraction: number | null
    /** Whether the effect is aimed at the runner that owns the skill. */
    readonly targetConditionValid: boolean
    readonly activationClass: ActivationClass | null
    readonly supportStatus: RecoverySupportStatus
    /** The decoded race gates the skill carries, for the report to explain itself with. */
    readonly gates: RaceGates
    readonly channel: EvidenceChannel
    /** Deterministic, never a probability: SUPPORTED evidence is "decoded", anything else "unresolved". */
    readonly confidence: "decoded" | "unresolved"
}

/** The hard, offline-decidable race gates a skill carries in its condition string. */
export interface RaceGates {
    readonly distanceTypes: readonly DistanceType[] | null
    readonly runningStyles: readonly RaceStrategy[] | null
    readonly surfaces: readonly RaceSurface[] | null
}

/** One debuff the build is being insured against. */
export interface DebuffThreat {
    readonly skillId: number | null
    readonly canonicalName: string | null
    /** Fraction of the runner's MaxHP removed per occurrence. Decoded when a skill id resolves it. */
    readonly hpDamageFraction: number | null
    /** A flat HP figure, for a threat expressed that way. Null when the threat is fractional. */
    readonly flatHpDamage: number | null
    readonly maxOccurrences: number
    readonly channel: EvidenceChannel
    readonly confidence: "decoded" | "assumed"
}

/** The named debuff budgets a caller can ask for. */
export const DEBUFF_BUDGETS = ["BASE", "ONE_STAMINA_DEBUFF", "TWO_STAMINA_DEBUFFS", "CUSTOM"] as const
export type DebuffBudget = (typeof DEBUFF_BUDGETS)[number]

/** How many threat occurrences each named budget insures against. CUSTOM is caller-supplied. */
export const DEBUFF_BUDGET_OCCURRENCES: Readonly<Record<Exclude<DebuffBudget, "CUSTOM">, number>> = {
    BASE: 0,
    ONE_STAMINA_DEBUFF: 1,
    TWO_STAMINA_DEBUFFS: 2,
}

/** The versioned input contract. Every field the model cannot resolve stays explicitly absent. */
export interface RaceSurvivalInput {
    readonly evidenceVersion: number
    /** Display identity for the race. The model resolves the course from the fields below, not this. */
    readonly targetRace: string | null
    /** The compiled-race composite key when the caller resolved one, for the report to echo. */
    readonly courseId: number | null
    readonly raceTrack: string | null
    readonly distanceMeters: number
    readonly surface: RaceSurface
    readonly groundCondition: GroundCondition | null
    readonly strategy: RaceStrategy

    readonly stamina: number
    /** Carried and reported; not priced (see GUTS_LATE_RACE_MITIGATION). */
    readonly guts: number | null
    /** Carried and reported; not priced (see TARGET_SPEED_HP_CONSUMPTION). */
    readonly targetSpeed: number | null

    /** Skill ids the build owns that could recover HP. Resolved against decoded evidence. */
    readonly recoverySkillIds: readonly number[]
    readonly debuffBudget: DebuffBudget
    /** Only read when debuffBudget is CUSTOM. */
    readonly customThreats: readonly DebuffThreat[] | null
    /** Carried and reported; not priced (see RUSH_RISK). */
    readonly rushRiskPolicy: string | null
    /**
     * Safety margin the answer must keep in hand, as a fraction of MaxHP. Zero means "survive
     * exactly". Editorial by definition: no decoded mechanic says how much slack a build wants.
     */
    readonly marginFraction: number
}

/** What one recovery package contributes, in HP first. */
export interface RecoveryContribution {
    /** Sum of the supported skills' HP, assuming each fires once. Never scaled by a made-up rate. */
    readonly totalPotentialHp: number
    /** Null, always, until an activation probability is decoded. Present so callers stop asking. */
    readonly expectedHp: null
    /** Optional diagnostic: the Stamina that would buy the same HP under the same strategy. */
    readonly effectiveStaminaEquivalent: number | null
    readonly supported: readonly RecoverySkillEvidence[]
    readonly unsupported: readonly RecoverySkillEvidence[]
    readonly assumptions: readonly string[]
}

/** One point on the debuff axis: what this many threats does to the Stamina requirement. */
export interface DebuffScenario {
    readonly label: string
    readonly occurrences: number
    readonly threats: readonly DebuffThreat[]
    /** Total fraction of MaxHP the threats remove. */
    readonly hpLossFraction: number
    readonly requiredStaminaTarget: number | null
    readonly survivesAtInputStamina: boolean | null
}

/** The full survival answer for one build against one race. */
export interface SurvivalEnvelope {
    readonly schema: typeof RACE_SURVIVAL_SCHEMA
    readonly schemaVersion: number
    readonly modelKind: typeof RACE_SURVIVAL_MODEL_KIND
    /** Fingerprint of the decoded evidence file the answer was computed against. */
    readonly evidenceVersion: number

    readonly race: ResolvedCourse
    readonly strategy: RaceStrategy
    readonly inputStamina: number
    readonly maxHp: number

    /** Cruise HP cost across the decoded finish-time band: low = fastest, high = slowest. */
    readonly baselineRequiredHpLow: number
    readonly baselineRequiredHpTarget: number
    readonly baselineRequiredHpHigh: number
    /** Effective HP at the input Stamina, after recovery and the selected debuff budget. */
    readonly effectiveHp: number
    readonly baselineMarginHp: number

    readonly debuffScenarios: readonly DebuffScenario[]
    readonly recoveryContribution: RecoveryContribution

    readonly selectedRiskPolicy: DebuffBudget
    readonly marginFraction: number
    /** Stamina needed at the fastest end of the decoded band. Null when no Stamina can satisfy it. */
    readonly requiredStaminaLow: number | null
    readonly requiredStaminaTarget: number | null
    readonly requiredStaminaHigh: number | null

    readonly survivesBaseline: boolean
    readonly survivesSelectedRisk: boolean

    readonly constants: readonly EvidencedConstant[]
    readonly assumptions: readonly string[]
    readonly unknownMechanics: readonly MechanicNote[]
    /**
     * Never "high": this is an estimator over a partially decoded race model. "moderate" when the
     * course resolved to a single decoded course set and every recovery skill resolved; "low" when
     * either was ambiguous or unresolved.
     */
    readonly confidence: "low" | "moderate"
}

/** A race resolved onto the decoded course table. */
export interface ResolvedCourse {
    readonly targetRace: string | null
    readonly track: string | null
    readonly distanceMeters: number
    readonly distanceType: DistanceType
    readonly surface: RaceSurface
    readonly groundCondition: GroundCondition | null
    /** The decoded course-set ids that match. More than one means the inout variant is ambiguous. */
    readonly courseSetIds: readonly number[]
    readonly finishTimeSecondsLow: number
    readonly finishTimeSecondsHigh: number
    readonly resolution: "exact" | "ambiguous" | "unresolved"
}

/**
 * The STAM-2 handoff. Defined here, produced by this phase, consumed by nothing yet: the Build Budget
 * Planner that will combine it with inheritance, deck, growth and scenario bonuses is a later task.
 */
export interface SurvivalConstraint {
    readonly schemaVersion: number
    readonly targetRace: string | null
    readonly targetStrategy: RaceStrategy
    /** The smallest Stamina that survives at the fastest end of the decoded band. */
    readonly minimumStamina: number | null
    /** [target, high] across the decoded finish-time band: what a build should actually aim at. */
    readonly preferredStaminaRange: readonly [number, number] | null
    /** The recovery the minimum assumes; dropping any of it invalidates the constraint. */
    readonly recoveryRequirements: readonly number[]
    readonly debuffRiskPolicy: DebuffBudget
    readonly confidence: "low" | "moderate"
    readonly unknownMechanics: readonly string[]
}

/** A deterministic Race Survival failure. */
export class RaceSurvivalError extends Error {
    readonly code: string
    constructor(code: string, message: string) {
        super(message)
        this.name = "RaceSurvivalError"
        this.code = code
    }
}
