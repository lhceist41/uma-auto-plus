// STAM-2 Joint Build Budget Planner - the per-stat budget. Pure, offline, deterministic.
//
// One stat, every source that feeds it, and what that leaves against the requirement. The shape is
// the point: decoded contributions and estimated ones are separate fields all the way out, so a
// reader can always see how much of a projection is a table and how much is a bracket.
//
//   decoded    the trainee's starting stat, the inheritance flat, the deck's initial stats, every
//              stat ceiling term.
//   estimated  the training production, primary and secondary kept apart, and anything the model
//              declines to price, which stays at zero rather than being filled in.
//
// The two questions a budget answers are the deficit and the surplus, and they are asked at different
// ends of the bracket on purpose:
//
//   deficit is measured at the LOW end. A build clears a survival floor when even the pessimistic
//   projection clears it; clearing it only on the optimistic end is not clearing it.
//
//   surplus is measured at the MEDIAN. Over-investment is a question about the ordinary case, not the
//   worst one, and flagging over-Stamina off the low end would never fire.

import { BUDGET_STATS, addBrackets, capBracket, weakestConfidence, zeroBracket, type Bracket, type BudgetConfidence, type BudgetStat, type BudgetTrainee, type StatBudget } from "./types.ts"
import type { InheritanceContribution } from "./inheritance.ts"
import type { DeckStartingContribution, TrainingProduction } from "./training.ts"
import type { SurvivalConstraint } from "../raceSurvival/types.ts"

/** The stat a race survival constraint is expressed in. STAM-1 solves for Stamina and only Stamina. */
export const SURVIVAL_STAT: BudgetStat = "Stamina"

export interface StatBudgetInput {
    readonly trainee: BudgetTrainee
    readonly inheritance: InheritanceContribution
    readonly deckStart: DeckStartingContribution
    readonly production: TrainingProduction
    readonly scenarioCapBonus: Readonly<Record<BudgetStat, number>>
    readonly baselineStatCap: number
    readonly constraint: SurvivalConstraint | null
    readonly confidence: BudgetConfidence
}

/**
 * Builds the five stat budgets.
 *
 * The survival floor lands on exactly one of them. Putting a `requiredFloor` on Speed because Speed
 * happens to be important would be inventing a constraint the race never stated, so every stat but
 * Stamina carries a null floor and is reported purely as a budget.
 */
export function buildStatBudgets(input: StatBudgetInput): readonly StatBudget[] {
    const { trainee, inheritance, deckStart, production, scenarioCapBonus, baselineStatCap, constraint } = input

    return BUDGET_STATS.map((stat): StatBudget => {
        const startStat = trainee.startStats[stat] ?? 0
        const inheritanceFlat = inheritance.startStats[stat] ?? zeroBracket()
        const supportInitialStats = deckStart.initialStats[stat] ?? 0
        const inheritanceCap = inheritance.capBonus[stat] ?? 0
        const supportCapBonus = deckStart.capBonus[stat] ?? 0
        const scenarioCap = scenarioCapBonus[stat] ?? 0
        const statCap = baselineStatCap + scenarioCap + inheritanceCap + supportCapBonus

        const primary = production.primary[stat] ?? zeroBracket()
        const secondary = production.secondary[stat] ?? zeroBracket()
        // Support-card and trainee events are real income the model does not price. Zero is the honest
        // placeholder: it keeps the projection a floor instead of a guess.
        const supportEventEstimate = zeroBracket()
        // Scenario systems on top of the decoded base gains (Grand Concert lessons, Trackblazer
        // gimmicks) are likewise unpriced rather than approximated.
        const scenarioContributionEstimate = zeroBracket()

        const startingTotal: Bracket = {
            low: startStat + inheritanceFlat.low + supportInitialStats,
            median: startStat + inheritanceFlat.median + supportInitialStats,
            high: startStat + inheritanceFlat.high + supportInitialStats,
        }
        const uncapped = addBrackets(addBrackets(addBrackets(startingTotal, primary), addBrackets(secondary, supportEventEstimate)), scenarioContributionEstimate)
        const projected = capBracket(uncapped, statCap)
        const cappedOut = uncapped.median > statCap

        const isSurvivalStat = stat === SURVIVAL_STAT && constraint !== null
        const requiredFloor = isSurvivalStat ? constraint.minimumStamina : null
        const preferredRange = isSurvivalStat ? (constraint.preferredStaminaRange ?? null) : null

        const deficitToMinimum = requiredFloor === null ? null : Math.max(0, requiredFloor - projected.low)
        const surplusAbovePreferred = preferredRange === null ? null : Math.max(0, projected.median - preferredRange[1])

        const assumptions: string[] = []
        if (cappedOut) {
            assumptions.push(`The median projection runs into the ${stat} ceiling of ${statCap} (${baselineStatCap} base + ${scenarioCap} scenario + ${inheritanceCap} Sparks + ${supportCapBonus} deck), so points beyond it are lost.`)
        }
        if (inheritanceFlat.high > inheritanceFlat.low) {
            assumptions.push(`Inherited ${stat} is between ${inheritanceFlat.low} and ${inheritanceFlat.high} because the Spark accumulation rule is not decoded.`)
        }
        if (secondary.high > 0) {
            assumptions.push(`${Math.round(secondary.low)} to ${Math.round(secondary.high)} ${stat} comes from OTHER facilities' decoded secondary payouts, not from ${stat} training.`)
        }

        return {
            stat,
            requiredFloor,
            preferredRange,
            startStat,
            inheritanceFlat,
            inheritanceCap,
            supportInitialStats,
            supportCapBonus,
            scenarioCapBonus: scenarioCap,
            statCap,
            deckTrainingContributionEstimate: primary,
            secondaryTrainingContributionEstimate: secondary,
            supportEventEstimate,
            scenarioContributionEstimate,
            projected,
            deficitToMinimum,
            surplusAbovePreferred,
            cappedOut,
            confidence: input.confidence,
            assumptions,
        }
    })
}

/** The survival verdict for a set of budgets. */
export interface SurvivalVerdict {
    /** True when even the low end of the Stamina projection clears the constraint minimum. */
    readonly survivesSelectedRisk: boolean
    /** True when the median sits above the preferred range, so more Stamina buys little. */
    readonly overStaminaRisk: boolean
    readonly staminaDeficit: number
    readonly staminaSurplus: number
    /** The stat the surplus would most plausibly have been spent on instead, and how much room it has. */
    readonly displacedStat: BudgetStat | null
    readonly displacedHeadroom: number
}

/**
 * Reads the survival verdict off the budgets.
 *
 * The over-Stamina test is the one that reproduces the "heavy Stamina inheritance can starve Power"
 * observation without anybody encoding it. It fires when the low end already clears the floor AND the
 * median sits above the preferred upper bound, which is exactly the state in which another point of
 * Stamina has no survival value left to buy. What it names as the alternative is whichever other stat
 * has the most unused ceiling, because that is where the displaced investment could actually land.
 */
export function readSurvivalVerdict(budgets: readonly StatBudget[]): SurvivalVerdict {
    const stamina = budgets.find((b) => b.stat === SURVIVAL_STAT)
    const deficit = stamina?.deficitToMinimum ?? 0
    const surplus = stamina?.surplusAbovePreferred ?? 0
    const survives = stamina?.requiredFloor === null || stamina?.requiredFloor === undefined ? false : deficit === 0
    const overStamina = survives && surplus > 0

    let displacedStat: BudgetStat | null = null
    let displacedHeadroom = 0
    if (overStamina) {
        for (const budget of budgets) {
            if (budget.stat === SURVIVAL_STAT) continue
            const headroom = budget.statCap - budget.projected.median
            if (headroom > displacedHeadroom) {
                displacedHeadroom = headroom
                displacedStat = budget.stat
            }
        }
    }

    return { survivesSelectedRisk: survives, overStaminaRisk: overStamina, staminaDeficit: deficit, staminaSurplus: surplus, displacedStat, displacedHeadroom }
}

/**
 * The joint confidence rule.
 *
 * A budget cannot be more confident than the survival constraint it is measured against, and it
 * cannot be more confident than the evidence behind its own inputs. Both are checked, and the answer
 * is the weaker: an inheritance set that half failed to price does not become trustworthy because the
 * race resolved cleanly.
 */
export function jointConfidence(constraint: SurvivalConstraint | null, inheritance: InheritanceContribution, traineeOrigin: BudgetTrainee["origin"], recoverySatisfied: boolean): BudgetConfidence {
    const survival: BudgetConfidence = constraint ? constraint.confidence : "low"
    const inheritanceConfidence: BudgetConfidence = inheritance.unpriced.length === 0 ? "moderate" : "low"
    const traineeConfidence: BudgetConfidence = traineeOrigin === "DECODED" ? "moderate" : "low"
    const recoveryConfidence: BudgetConfidence = recoverySatisfied ? "moderate" : "low"
    return weakestConfidence(survival, inheritanceConfidence, traineeConfidence, recoveryConfidence)
}
