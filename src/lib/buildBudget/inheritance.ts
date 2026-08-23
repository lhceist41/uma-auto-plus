// STAM-2 Joint Build Budget Planner - inheritance pricing. Pure, offline, deterministic.
//
// Turns a parent pair's Sparks into numbers, using the game's own effect ladders and nothing else.
// This is the file that replaces "heavy Stamina inheritance" with "+16 starting Stamina, +2 Stamina
// cap, and a Long aptitude step", which is the difference between a planner and an opinion.
//
// Two things about the ladder shape the whole file:
//
//   The ladder is exact. succession_factor_effect states, for each factor group, exactly what the
//   Spark gives at each level. No interpolation, no scaling, no per-star formula.
//
//   The level is not. master.mdb does not say how a lineage's star counts add up to a level on that
//   ladder. So every inheritance figure is a BRACKET between two defensible readings:
//
//     low   only the single strongest carrier of that Spark counts. This is the floor: whatever the
//           accumulation rule is, one 3-star Spark cannot be worth less than a 3-star Spark.
//     high  every carrier's stars sum, clamped to the ladder. This is the ceiling: no rule can pay
//           more than the ladder's own last rung.
//
// The truth sits between them and the report says so. What is NOT done is picking a middle rule and
// presenting it as the mechanic, because a plausible invented rule is indistinguishable from a
// measured one once it is a number in a table.

import type { BuildBudgetEvidence, FactorGroup } from "./evidence.ts"
import { BUDGET_STATS, bracketOf, zeroBracket, type Bracket, type BudgetFactor, type BudgetStat } from "./types.ts"

/** How the ladder level was resolved for one factor group. Both ends are reported, never averaged. */
export interface FactorLevelBracket {
    readonly lowLevel: number
    readonly highLevel: number
    readonly ladderLength: number
    /** True when the summed stars ran past the ladder's last rung and were clamped to it. */
    readonly clamped: boolean
}

/** One Spark the pair carries, priced. */
export interface PricedFactor {
    readonly family: string
    readonly canonicalName: string
    readonly carriers: number
    readonly starsPerCarrier: readonly number[]
    readonly summedStars: number
    readonly level: FactorLevelBracket
    /** Flat starting stats this Spark hands over, per stat, as a bracket. */
    readonly startStats: Readonly<Partial<Record<BudgetStat, Bracket>>>
    /** Stat ceiling this Spark raises, per stat, at the conservative end of the level bracket. */
    readonly capBonus: Readonly<Partial<Record<BudgetStat, number>>>
    /** Aptitude grade steps this Spark grants, at the conservative end of the level bracket. */
    readonly aptitudeSteps: Readonly<Record<string, number>>
    /** Skill ids this Spark grants, with the level it grants them at. */
    readonly skills: readonly { readonly skillId: number; readonly level: number }[]
}

/** A Spark the reader saw but the ladder could not price. */
export interface UnpricedFactor {
    readonly family: string
    readonly canonicalName: string
    readonly stars: number
    readonly reason: "NAME_NOT_IN_FACTOR_DOMAIN" | "GROUP_HAS_NO_EFFECT_LADDER"
}

/** Everything a parent pair contributes, decoded. */
export interface InheritanceContribution {
    /** Flat starting stats, per stat, bracketed by the two level readings. */
    readonly startStats: Readonly<Record<BudgetStat, Bracket>>
    /** Stat ceiling raised, per stat. Conservative end of the level bracket. */
    readonly capBonus: Readonly<Record<BudgetStat, number>>
    readonly aptitudeSteps: Readonly<Record<string, number>>
    /** Skill ids the lineage hands the trainee. Feeds the recovery-access check, not the stat budget. */
    readonly skillIds: readonly number[]
    readonly priced: readonly PricedFactor[]
    readonly unpriced: readonly UnpricedFactor[]
    readonly assumptions: readonly string[]
}

function emptyStatBrackets(): Record<BudgetStat, Bracket> {
    const out = {} as Record<BudgetStat, Bracket>
    for (const stat of BUDGET_STATS) out[stat] = zeroBracket()
    return out
}

function emptyStatNumbers(): Record<BudgetStat, number> {
    const out = {} as Record<BudgetStat, number>
    for (const stat of BUDGET_STATS) out[stat] = 0
    return out
}

/**
 * The two level readings for a set of star counts on one factor group.
 *
 * The ladder length is the group's own, not a constant: blue Sparks run to ten rungs, white skill
 * Sparks to five, unique Sparks to four and pink aptitude Sparks to two. Reading a level off the
 * wrong ladder length is exactly the mistake that would make a pink Spark look ten times better than
 * it is.
 */
export function resolveFactorLevel(starsPerCarrier: readonly number[], ladderLength: number): FactorLevelBracket {
    const best = starsPerCarrier.reduce((m, s) => (s > m ? s : m), 0)
    const summed = starsPerCarrier.reduce((s, v) => s + v, 0)
    const lowLevel = Math.max(0, Math.min(best, ladderLength))
    const highLevel = Math.max(0, Math.min(summed, ladderLength))
    return { lowLevel, highLevel, ladderLength, clamped: summed > ladderLength }
}

function ladderValue(values: readonly number[], level: number): number {
    if (level <= 0) return 0
    return values[Math.min(level, values.length) - 1] ?? 0
}

function priceGroup(group: FactorGroup, starsPerCarrier: readonly number[]): PricedFactor {
    // Every effect on a group shares the group's ladder length, so one level bracket serves them all.
    const ladderLength = group.effects.reduce((m, e) => (e.levels > m ? e.levels : m), 0)
    const level = resolveFactorLevel(starsPerCarrier, ladderLength)

    const startStats: Partial<Record<BudgetStat, Bracket>> = {}
    const capBonus: Partial<Record<BudgetStat, number>> = {}
    const aptitudeSteps: Record<string, number> = {}
    const skills: { skillId: number; level: number }[] = []

    for (const effect of group.effects) {
        if (effect.kind === "START_STAT" && effect.stat) {
            const low = ladderValue(effect.value1, level.lowLevel)
            const high = ladderValue(effect.value1, level.highLevel)
            const held = startStats[effect.stat]
            const next = bracketOf(low, high)
            startStats[effect.stat] = held ? { low: held.low + next.low, median: held.median + next.median, high: held.high + next.high } : next
        } else if (effect.kind === "MAX_STAT" && effect.stat) {
            // The conservative end: a cap claimed larger than it is would let a projection run past a
            // ceiling that actually binds. The figures are single digits, so the choice costs nothing.
            capBonus[effect.stat] = (capBonus[effect.stat] ?? 0) + ladderValue(effect.value1, level.lowLevel)
        } else if (effect.kind === "APTITUDE" && effect.aptitude) {
            aptitudeSteps[effect.aptitude] = (aptitudeSteps[effect.aptitude] ?? 0) + ladderValue(effect.value1, level.lowLevel)
        } else if (effect.kind === "SKILL") {
            const skillId = ladderValue(effect.value1, level.lowLevel)
            const skillLevel = ladderValue(effect.value2, level.lowLevel)
            if (skillId > 0) skills.push({ skillId, level: skillLevel })
        }
    }

    return {
        family: group.family,
        canonicalName: group.canonicalName,
        carriers: starsPerCarrier.length,
        starsPerCarrier: [...starsPerCarrier].sort((a, b) => b - a),
        summedStars: starsPerCarrier.reduce((s, v) => s + v, 0),
        level,
        startStats,
        capBonus,
        aptitudeSteps,
        skills: skills.sort((a, b) => a.skillId - b.skillId),
    }
}

/**
 * Prices every Spark a parent pair carries.
 *
 * Factors arrive already merged across the two parents, because who carried a Spark does not change
 * what it pays: the ladder reads a lineage total, not a per-parent one. What DOES change is how many
 * carriers there were, which is why the count survives into the priced record and drives the bracket.
 */
export function priceInheritance(evidence: BuildBudgetEvidence, factors: readonly BudgetFactor[]): InheritanceContribution {
    const byGroup = new Map<string, { group: FactorGroup; stars: number[] }>()
    const unpriced: UnpricedFactor[] = []

    for (const factor of factors) {
        if (!factor.canonicalName) continue
        const group = evidence.factorGroup(factor.family, factor.canonicalName)
        if (!group) {
            unpriced.push({ family: factor.family, canonicalName: factor.canonicalName, stars: factor.stars, reason: "NAME_NOT_IN_FACTOR_DOMAIN" })
            continue
        }
        if (!group.effects.length) {
            unpriced.push({ family: factor.family, canonicalName: factor.canonicalName, stars: factor.stars, reason: "GROUP_HAS_NO_EFFECT_LADDER" })
            continue
        }
        const key = String(group.groupId)
        const held = byGroup.get(key)
        if (held) held.stars.push(factor.stars)
        else byGroup.set(key, { group, stars: [factor.stars] })
    }

    const startStats = emptyStatBrackets()
    const capBonus = emptyStatNumbers()
    const aptitudeSteps: Record<string, number> = {}
    const skillIds = new Set<number>()
    const priced: PricedFactor[] = []

    for (const { group, stars } of [...byGroup.values()].sort((a, b) => a.group.groupId - b.group.groupId)) {
        const entry = priceGroup(group, stars)
        priced.push(entry)
        for (const stat of BUDGET_STATS) {
            const contribution = entry.startStats[stat]
            if (contribution) {
                startStats[stat] = { low: startStats[stat].low + contribution.low, median: startStats[stat].median + contribution.median, high: startStats[stat].high + contribution.high }
            }
            capBonus[stat] += entry.capBonus[stat] ?? 0
        }
        for (const [aptitude, steps] of Object.entries(entry.aptitudeSteps)) aptitudeSteps[aptitude] = (aptitudeSteps[aptitude] ?? 0) + steps
        for (const skill of entry.skills) skillIds.add(skill.skillId)
    }

    const assumptions: string[] = [
        "Inheritance is a bracket, not a figure: the low end counts only the strongest single carrier of each Spark, the high end sums every carrier's stars and clamps to the Spark's own ladder. master.mdb does not state which is right.",
        "The effect ladders themselves are exact, read from succession_factor_effect. Nothing is interpolated between rungs.",
    ]
    if (priced.some((p) => p.level.clamped)) {
        assumptions.push("At least one Spark's summed stars ran past its ladder's last rung, so its high end is the ladder maximum rather than the sum.")
    }
    if (unpriced.length) {
        assumptions.push(`${unpriced.length} Spark${unpriced.length === 1 ? "" : "s"} could not be priced and contribute nothing. An unpriced Spark makes the projection a floor, never an overstatement.`)
    }
    const capTotal = BUDGET_STATS.reduce((s, stat) => s + capBonus[stat], 0)
    if (capTotal > 0) {
        assumptions.push("Stat ceiling raises from Sparks are taken at the conservative end of the level bracket, so a projection is never allowed past a ceiling that might actually bind.")
    }

    return {
        startStats,
        capBonus,
        aptitudeSteps,
        skillIds: [...skillIds].sort((a, b) => a - b),
        priced,
        unpriced: unpriced.sort((a, b) => (a.canonicalName < b.canonicalName ? -1 : a.canonicalName > b.canonicalName ? 1 : 0)),
        assumptions,
    }
}
