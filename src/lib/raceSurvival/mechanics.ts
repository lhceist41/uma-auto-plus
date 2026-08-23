// STAM-1 Race Survival Shadow Model - the race-mechanics layer. Pure, offline, deterministic.
//
// Everything in this file that is a number carries an evidence channel, because two of the three
// pieces of the survival equation are decoded from the game and one is not:
//
//   MaxHP. The expression `0.8 * strategyCoefficient * Stamina + courseDistance` and the five
//   per-strategy coefficients are EXTERNAL_MECHANICS_REFERENCE. They are not in master.mdb: the
//   database carries no HP formula, no per-strategy HP coefficient and no race-physics constants of
//   any kind, which was checked by scanning every integer column in all 416 tables for them. They are
//   carried here as named constants so a reader can see precisely which outputs depend on them.
//
//   Race duration. DECODED_GAME_DATA: race_course_set ships a finish-time band per course, and the
//   model reads that band rather than guessing how long a race lasts. The band is why every Stamina
//   answer is a range: the fast end and the slow end of the same course want different builds.
//
//   Cruise HP drain. EXTERNAL_MECHANICS_REFERENCE: the rate a runner burns HP holding the course's
//   base pace. Also absent from master.mdb.
//
// What this file deliberately does NOT do is model velocity. There is no last-spurt term, no guts
// mitigation, no slope cost and no pace model, because none of those are decoded here and a number
// invented for them would look exactly like a number that was measured. The consequence is stated
// plainly in the output: the estimate is a cruise-pace floor, and the mechanics it is blind to all
// push the real requirement upward, never downward.

import type { EvidencedConstant, RaceStrategy } from "./types.ts"
import { RaceSurvivalError } from "./types.ts"

/**
 * The Stamina-to-HP coefficient in the MaxHP expression.
 *
 * EXTERNAL_MECHANICS_REFERENCE. Not decoded from master.mdb.
 */
export const MAXHP_STAMINA_COEFFICIENT = 0.8

/**
 * Per-strategy HP coefficients, highest is most HP per point of Stamina.
 *
 * EXTERNAL_MECHANICS_REFERENCE. Not decoded from master.mdb: the database carries the running-style
 * aptitude multiplier table (race_proper_runningstyle_rate) but nothing that maps a running style to
 * an HP coefficient, and none of these five values appears in any integer column of any table.
 */
export const STRATEGY_HP_COEFFICIENT: Readonly<Record<RaceStrategy, number>> = {
    front: 0.95,
    pace: 0.89,
    late: 1.0,
    end: 0.995,
    runaway: 0.86,
}

/**
 * HP burned per second holding the course's base pace.
 *
 * EXTERNAL_MECHANICS_REFERENCE. Not decoded from master.mdb. This is the single rate the estimator
 * uses; running above base pace costs more, which is the largest thing the model is blind to.
 */
export const CRUISE_HP_DRAIN_PER_SECOND = 20

/** master.mdb stores finish times as integers scaled by this. DECODED_GAME_DATA. */
export const FIXED_POINT_DIVISOR = 10000

/**
 * Rounding slack for the closed-form inverse solve, in Stamina points.
 *
 * The solve is exact arithmetic, so this exists only to stop a value that lands on an integer from
 * being pushed to the next one by floating-point representation. It is a tolerance on the rounding,
 * not a search tolerance: nothing here iterates.
 */
export const STAMINA_SOLVE_EPSILON = 1e-9

/** The constants above, in the form a report can print without knowing their names. */
export const MODEL_CONSTANTS: readonly EvidencedConstant[] = [
    {
        name: "MAXHP_STAMINA_COEFFICIENT",
        value: MAXHP_STAMINA_COEFFICIENT,
        channel: "EXTERNAL_MECHANICS_REFERENCE",
        provenance: "MaxHP = 0.8 * strategyCoefficient * Stamina + courseDistance. Not present in master.mdb.",
    },
    ...(Object.keys(STRATEGY_HP_COEFFICIENT) as RaceStrategy[]).map(
        (strategy): EvidencedConstant => ({
            name: `STRATEGY_HP_COEFFICIENT.${strategy}`,
            value: STRATEGY_HP_COEFFICIENT[strategy],
            channel: "EXTERNAL_MECHANICS_REFERENCE",
            provenance: "Per-strategy HP coefficient. Not present in master.mdb.",
        }),
    ),
    {
        name: "CRUISE_HP_DRAIN_PER_SECOND",
        value: CRUISE_HP_DRAIN_PER_SECOND,
        channel: "EXTERNAL_MECHANICS_REFERENCE",
        provenance: "HP burned per second at the course base pace. Not present in master.mdb.",
    },
]

/** The MaxHP inputs. Stamina and distance are the build's and the race's; strategy picks the coefficient. */
export interface MaxHpInput {
    readonly stamina: number
    readonly distanceMeters: number
    readonly strategy: RaceStrategy
}

/**
 * MaxHP for a build on a course.
 *
 * Note the shape: the course distance is an additive floor every runner gets for free, so a build
 * with zero Stamina still starts a 2000m race with 2000 HP. That is why the Stamina requirement for
 * a race is not proportional to its distance, and why a survival answer has to be solved rather than
 * looked up in a table of "Medium wants 800".
 */
export function computeMaxHp({ stamina, distanceMeters, strategy }: MaxHpInput): number {
    if (!Number.isFinite(stamina) || stamina < 0) throw new RaceSurvivalError("invalidStamina", `stamina must be a non-negative number, got ${stamina}`)
    if (!Number.isFinite(distanceMeters) || distanceMeters <= 0) throw new RaceSurvivalError("invalidDistance", `distanceMeters must be positive, got ${distanceMeters}`)
    const coefficient = STRATEGY_HP_COEFFICIENT[strategy]
    if (coefficient === undefined) throw new RaceSurvivalError("unknownStrategy", `no HP coefficient for strategy ${strategy}`)
    return MAXHP_STAMINA_COEFFICIENT * coefficient * stamina + distanceMeters
}

/** HP burned holding the base pace for this many seconds. */
export function computeCruiseHp(durationSeconds: number): number {
    if (!Number.isFinite(durationSeconds) || durationSeconds <= 0) {
        throw new RaceSurvivalError("invalidDuration", `durationSeconds must be positive, got ${durationSeconds}`)
    }
    return CRUISE_HP_DRAIN_PER_SECOND * durationSeconds
}

/** Converts a master.mdb fixed-point finish time to seconds. */
export function finishTimeSeconds(raw: number): number {
    return raw / FIXED_POINT_DIVISOR
}

/** Everything the closed-form inverse needs, all of it already resolved by the caller. */
export interface RequiredStaminaInput {
    readonly requiredHp: number
    readonly distanceMeters: number
    readonly strategy: RaceStrategy
    /** Recovery, as a fraction of MaxHP. Zero when the build has none. */
    readonly recoveryFraction: number
    /** Debuff damage, as a fraction of MaxHP. Zero at the BASE budget. */
    readonly debuffFraction: number
    /** Debuff damage expressed as flat HP rather than a fraction. Zero when there is none. */
    readonly flatDebuffHp: number
    /** Safety margin to keep in hand, as a fraction of MaxHP. Editorial; zero means survive exactly. */
    readonly marginFraction: number
}

/**
 * The smallest whole Stamina that survives, solved in closed form.
 *
 * Recovery and debuffs are both fractions of MaxHP, which makes the survival condition linear in
 * Stamina once it is written out:
 *
 *     maxHp * (1 + recovery - debuff - margin)  >=  requiredHp + flatDebuffHp
 *     0.8 * C * Stamina + distance              >=  (requiredHp + flatDebuffHp) / B
 *
 * with B the bracket on the left. There is no search, no iteration and no floating-point
 * nondeterminism: the same inputs always produce the same integer.
 *
 * Returns null when B is zero or negative, which means the debuff and margin budget removes at least
 * as much HP as the build has: no amount of Stamina survives that, and saying so is the honest
 * answer rather than returning an enormous number.
 */
export function solveRequiredStamina(input: RequiredStaminaInput): number | null {
    const { requiredHp, distanceMeters, strategy, recoveryFraction, debuffFraction, flatDebuffHp, marginFraction } = input
    const coefficient = STRATEGY_HP_COEFFICIENT[strategy]
    if (coefficient === undefined) throw new RaceSurvivalError("unknownStrategy", `no HP coefficient for strategy ${strategy}`)

    const bracket = 1 + recoveryFraction - debuffFraction - marginFraction
    if (bracket <= 0) return null

    const neededMaxHp = (requiredHp + flatDebuffHp) / bracket
    const neededFromStamina = neededMaxHp - distanceMeters
    if (neededFromStamina <= 0) return 0
    const exact = neededFromStamina / (MAXHP_STAMINA_COEFFICIENT * coefficient)
    return Math.ceil(exact - STAMINA_SOLVE_EPSILON)
}

/**
 * Effective HP at a given Stamina, after recovery and debuffs. The forward direction of the same
 * relation the inverse solves, kept beside it so the two cannot drift apart.
 */
export function computeEffectiveHp(input: Omit<RequiredStaminaInput, "requiredHp" | "marginFraction"> & { stamina: number }): number {
    const maxHp = computeMaxHp({ stamina: input.stamina, distanceMeters: input.distanceMeters, strategy: input.strategy })
    return maxHp * (1 + input.recoveryFraction - input.debuffFraction) - input.flatDebuffHp
}
