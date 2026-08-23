// STAM-2 Joint Build Budget Planner - the training production model. Pure, offline, deterministic.
//
// Answers "if this deck trains this trainee for this many turns, roughly how many points of each stat
// comes out". Roughly is the operative word and it is built into the return type: every figure here is
// a bracket, and the bracket is wide on purpose.
//
// What is decoded, exactly:
//
//   the base outcome of one training, per scenario, including the SECONDARY stats it pays. Power
//   training's own row carries a Stamina gain, and Grand Concert pays less of it than URA Finale
//   does. This is the whole basis of a Power-flex build and it is a table, not a theory.
//
//   the trainee's growth percentages, per outfit.
//
//   every support-card effect value: the flat stat bonus a card adds to a training, its friendship
//   bonus, its training effectiveness, its mood effect, the flat stats it hands over at career start
//   and the stat ceilings it raises.
//
// What is NOT decoded is the rule that combines them. The game multiplies these together in an order
// and with terms this repository has not read out of anything. Rather than invent that rule and let
// it look measured, the model brackets it:
//
//   low   the deck's multipliers do nothing. Base gain times growth, plus the deck's decoded flat
//         stat bonuses. A genuine floor: every omitted term is non-negative.
//   high  every decoded multiplier applies at once to every same-type card. A genuine ceiling: no
//         real career has every card present on every training at full friendship in perfect mood.
//
// A single build's absolute band is therefore wide. What the band does NOT do is favour one candidate
// over another, because it is computed identically for every candidate, which is why comparing two
// decks or two parent pairs through it is still meaningful even where the absolute figure is not.

import type { CardValueProfile } from "../deckLab/cardValue.ts"
import { EFFECT, INITIAL_STAT_EFFECT, STAT_BONUS_EFFECT, TRAINING_SUPPORT_TYPES, resolveTotalEffects, type SupportCardIndex, type TrainingSupportType } from "../deckLab/supportCardData.ts"
import type { BuildBudgetEvidence } from "./evidence.ts"
import { BUDGET_STATS, BuildBudgetError, bracketOf, zeroBracket, type Bracket, type BudgetConstant, type BudgetStat } from "./types.ts"

/**
 * The editorial training-turn budget.
 *
 * The career is 78 turns, decoded. How many of them end up as trainings rather than races, rest,
 * recreation, infirmary visits and mandatory events is a run-time outcome, not a table. Forty is a
 * round editorial figure chosen so the projection lands in the right order of magnitude, and it is
 * reported as editorial everywhere it is used. A caller who knows better overrides it, and STAM-6
 * telemetry is where a measured value would come from.
 */
export const DEFAULT_TRAINING_TURNS = 40

/** The stat gain multiplier a growth percentage applies. Growth 20 means twenty percent more. */
export const GROWTH_PERCENT_DIVISOR = 100

export const TRAINING_CONSTANTS: readonly BudgetConstant[] = [
    {
        name: "DEFAULT_TRAINING_TURNS",
        value: DEFAULT_TRAINING_TURNS,
        channel: "EDITORIAL_MODEL",
        provenance: "Trainings per career. The 78-turn career length is decoded; the training share of it is not, and this is a round editorial figure, not a measurement.",
    },
    {
        name: "GROWTH_PERCENT_DIVISOR",
        value: GROWTH_PERCENT_DIVISOR,
        channel: "EXTERNAL_MECHANICS_REFERENCE",
        provenance: "Growth rate applies as a percentage multiplier on a training's stat gain. The rates are decoded from card_data; that they multiply the gain is external.",
    },
]

/** What one deck contributes to one training facility, in decoded terms only. */
export interface FacilityDeckProfile {
    readonly stat: BudgetStat
    /** Cards whose own training type is this facility, plus Friend and Group cards, which fit anywhere. */
    readonly cardsPresent: number
    /** Flat stat added to a gain by the cards present, per stat. Decoded. */
    readonly flatStatBonus: Readonly<Record<BudgetStat, number>>
    /** Summed friendship bonus percentages of the cards present. Decoded values, external combination. */
    readonly friendshipBonusPercent: number
    readonly trainingEffectivenessPercent: number
    readonly moodEffectPercent: number
    /** Summed skill-point bonus percentages of the cards present. */
    readonly skillPointBonusPercent: number
    /** Summed initial bond of the cards present, which is what decides how fast friendship switches on. */
    readonly initialBondTotal: number
}

/** What the deck hands over before a single turn is spent. Entirely decoded, no bracket needed. */
export interface DeckStartingContribution {
    readonly initialStats: Readonly<Record<BudgetStat, number>>
    readonly capBonus: Readonly<Record<BudgetStat, number>>
}

/** How a career's turns are spread over the five facilities. */
export interface TurnAllocation {
    readonly trainingTurns: number
    readonly byStat: Readonly<Record<BudgetStat, number>>
    readonly origin: "OPERATOR" | "ARCHETYPE_PROFILE"
    readonly profileLabel: string | null
}

/** The production a whole allocation yields, split so a reader can see where each point came from. */
export interface TrainingProduction {
    /** Gains into the facility's own stat. */
    readonly primary: Readonly<Record<BudgetStat, Bracket>>
    /** Gains into a stat from OTHER facilities' secondary payouts. The Power-flex mechanism. */
    readonly secondary: Readonly<Record<BudgetStat, Bracket>>
    readonly skillPoints: Bracket
    readonly energySpent: number
    readonly assumptions: readonly string[]
}

function emptyStatNumbers(): Record<BudgetStat, number> {
    const out = {} as Record<BudgetStat, number>
    for (const stat of BUDGET_STATS) out[stat] = 0
    return out
}

function emptyStatBrackets(): Record<BudgetStat, Bracket> {
    const out = {} as Record<BudgetStat, Bracket>
    for (const stat of BUDGET_STATS) out[stat] = zeroBracket()
    return out
}

/** True when a card sits on every facility rather than one: Friend and Group cards have no own type. */
function isUniversalCard(supportType: string): boolean {
    return !(TRAINING_SUPPORT_TYPES as readonly string[]).includes(supportType)
}

/**
 * Reads the decoded effects of the cards that would appear on one facility.
 *
 * "Would appear" is the editorial part and it is deliberately crude: a card of training type S is
 * counted on facility S, and a Friend or Group card is counted everywhere. The game decides card
 * placement by a specialty-priority roll every turn, which this model does not simulate. Counting a
 * card on its own facility is the direction that roll leans, and the high end of the bracket is
 * explicitly the case where the lean always went the card's way.
 */
export function facilityProfile(index: SupportCardIndex, deck: readonly CardValueProfile[], stat: BudgetStat): FacilityDeckProfile {
    const flatStatBonus = emptyStatNumbers()
    let cardsPresent = 0
    let friendshipBonusPercent = 0
    let trainingEffectivenessPercent = 0
    let moodEffectPercent = 0
    let skillPointBonusPercent = 0
    let initialBondTotal = 0

    for (const profile of deck) {
        const supportType = profile.card.supportType
        if (!isUniversalCard(supportType) && supportType !== stat) continue
        const card = index.byId.get(profile.card.supportCardId)
        if (!card) throw new BuildBudgetError("unknownCard", `support card ${profile.card.supportCardId} is not in the catalogue`)
        const { effects } = resolveTotalEffects(index.data, card, profile.limitBreakState.level)

        cardsPresent += 1
        friendshipBonusPercent += effects.get(EFFECT.FRIENDSHIP_BONUS) ?? 0
        trainingEffectivenessPercent += effects.get(EFFECT.TRAINING_EFFECTIVENESS) ?? 0
        moodEffectPercent += effects.get(EFFECT.MOOD_EFFECT) ?? 0
        skillPointBonusPercent += effects.get(EFFECT.SKILL_POINT_BONUS) ?? 0
        initialBondTotal += effects.get(EFFECT.INITIAL_FRIENDSHIP_GAUGE) ?? 0
        for (const target of BUDGET_STATS) {
            flatStatBonus[target] += effects.get(STAT_BONUS_EFFECT[target as TrainingSupportType]) ?? 0
        }
    }

    return { stat, cardsPresent, flatStatBonus, friendshipBonusPercent, trainingEffectivenessPercent, moodEffectPercent, skillPointBonusPercent, initialBondTotal }
}

/** The flat stats and stat ceilings the whole deck hands over at career start. Decoded, exact. */
export function deckStartingContribution(index: SupportCardIndex, deck: readonly CardValueProfile[]): DeckStartingContribution {
    const initialStats = emptyStatNumbers()
    const capBonus = emptyStatNumbers()
    const maxEffectByStat: Readonly<Record<BudgetStat, number>> = {
        Speed: EFFECT.MAX_SPEED,
        Stamina: EFFECT.MAX_STAMINA,
        Power: EFFECT.MAX_POWER,
        Guts: EFFECT.MAX_GUTS,
        Wit: EFFECT.MAX_WIT,
    }
    for (const profile of deck) {
        const card = index.byId.get(profile.card.supportCardId)
        if (!card) throw new BuildBudgetError("unknownCard", `support card ${profile.card.supportCardId} is not in the catalogue`)
        const { effects } = resolveTotalEffects(index.data, card, profile.limitBreakState.level)
        for (const stat of BUDGET_STATS) {
            initialStats[stat] += effects.get(INITIAL_STAT_EFFECT[stat as TrainingSupportType]) ?? 0
            capBonus[stat] += effects.get(maxEffectByStat[stat]) ?? 0
        }
    }
    return { initialStats, capBonus }
}

/**
 * The multiplier the high end of the bracket applies to a facility's base gain.
 *
 * EXTERNAL_MECHANICS_REFERENCE, and an overstatement by construction. The three decoded percentage
 * families are applied together as though every card of the facility's type were present, at full
 * friendship, in perfect mood, on every single training. No real career does that, which is exactly
 * what makes this a ceiling rather than a prediction.
 */
export function facilityCeilingMultiplier(profile: FacilityDeckProfile): number {
    const friendship = 1 + profile.friendshipBonusPercent / 100
    const effectiveness = 1 + profile.trainingEffectivenessPercent / 100
    const mood = 1 + profile.moodEffectPercent / 100
    return friendship * effectiveness * mood
}

/**
 * Projects a whole career's training production for one deck, trainee and turn allocation.
 *
 * Primary and secondary are kept apart all the way out. A build that clears its Stamina floor because
 * it spent twenty turns on the Stamina facility and a build that clears it out of Power training's
 * secondary payout are different builds with different costs, and a model that summed them into one
 * "Stamina" figure could not tell an operator which one it had found.
 */
export function projectTrainingProduction(
    evidence: BuildBudgetEvidence,
    index: SupportCardIndex,
    deck: readonly CardValueProfile[],
    scenarioId: number,
    growth: Readonly<Record<BudgetStat, number>>,
    allocation: TurnAllocation,
): TrainingProduction {
    const primary = emptyStatBrackets()
    const secondary = emptyStatBrackets()
    let skillPointsLow = 0
    let skillPointsHigh = 0
    let energySpent = 0
    const missingFacilities: BudgetStat[] = []

    for (const facility of BUDGET_STATS) {
        const turns = allocation.byStat[facility] ?? 0
        if (turns <= 0) continue
        const base = evidence.baseTraining(scenarioId, facility)
        if (!base) {
            missingFacilities.push(facility)
            continue
        }
        const deckProfile = facilityProfile(index, deck, facility)
        const ceiling = facilityCeilingMultiplier(deckProfile)
        energySpent += base.energy * turns

        for (const stat of BUDGET_STATS) {
            const baseGain = base.stats[stat]
            if (baseGain === 0) continue
            const growthMultiplier = 1 + (growth[stat] ?? 0) / GROWTH_PERCENT_DIVISOR
            const low = baseGain * growthMultiplier * turns
            const high = (baseGain + deckProfile.flatStatBonus[stat]) * ceiling * growthMultiplier * turns
            const contribution = bracketOf(low, high)
            const target = stat === facility ? primary : secondary
            target[stat] = { low: target[stat].low + contribution.low, median: target[stat].median + contribution.median, high: target[stat].high + contribution.high }
        }

        skillPointsLow += base.skillPoints * turns
        skillPointsHigh += base.skillPoints * (1 + deckProfile.skillPointBonusPercent / 100) * turns
    }

    const assumptions: string[] = [
        `Base training outcomes are the decoded per-scenario rows for scenario ${scenarioId}, at the ordinary board: no camp gains, no facility-level scaling, success only.`,
        "Every projection is a bracket. The low end applies no support-card multiplier at all; the high end applies every decoded multiplier at once, as though every card of a facility's type were present at full friendship in perfect mood on every training. The real figure is between them.",
        `Growth percentages multiply the gain: ${BUDGET_STATS.map((s) => `${s} ${growth[s] ?? 0}%`).join(", ")}.`,
        `Turn budget: ${allocation.trainingTurns} trainings, spread ${BUDGET_STATS.map((s) => `${s} ${allocation.byStat[s] ?? 0}`).join(", ")} (${allocation.origin === "OPERATOR" ? "operator-supplied" : `archetype profile ${allocation.profileLabel ?? "unnamed"}`}).`,
        "Support-card and trainee events pay stats that are not modelled here, so the projection is a floor by that much.",
    ]
    if (missingFacilities.length) {
        assumptions.push(`No decoded training row exists for ${missingFacilities.join(", ")} in scenario ${scenarioId}; turns allocated there produce nothing in this projection.`)
    }

    return { primary, secondary, skillPoints: bracketOf(skillPointsLow, skillPointsHigh), energySpent, assumptions }
}

/**
 * Friendship-ramp burden for a deck, as a comparative index rather than a count of turns.
 *
 * Everything a card's multipliers are worth is gated behind reaching its friendship threshold, and how
 * fast a deck gets there depends on initial bond, on how often the card shows up and on how many
 * turns go into that facility. Only the first of those is decoded, so this is an index, not a
 * prediction: higher means more of the deck's value is parked behind a gauge that has to be filled
 * first. Comparing two decks on it is meaningful; reading a number of turns off it is not.
 *
 * Zero means every card starts at or above the bond the deck's own cards ship with; the index rises
 * as cards with big multipliers arrive with little bond.
 */
export function friendshipRampBurden(index: SupportCardIndex, deck: readonly CardValueProfile[]): number {
    let burden = 0
    for (const profile of deck) {
        const card = index.byId.get(profile.card.supportCardId)
        if (!card) throw new BuildBudgetError("unknownCard", `support card ${profile.card.supportCardId} is not in the catalogue`)
        const { effects } = resolveTotalEffects(index.data, card, profile.limitBreakState.level)
        const gated = (effects.get(EFFECT.FRIENDSHIP_BONUS) ?? 0) + (effects.get(EFFECT.TRAINING_EFFECTIVENESS) ?? 0)
        const bond = effects.get(EFFECT.INITIAL_FRIENDSHIP_GAUGE) ?? 0
        // Value behind the gauge, discounted by the head start the card ships with. A card with a large
        // friendship bonus and no initial bond is the expensive case; a low-value card is cheap whatever
        // its bond, which is why the value term multiplies rather than adds.
        burden += gated * Math.max(0, 1 - bond / 100)
    }
    return Number(burden.toFixed(4))
}
