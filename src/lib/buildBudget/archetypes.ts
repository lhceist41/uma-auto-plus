// STAM-2 Joint Build Budget Planner - the flex archetypes. Pure, offline, deterministic.
//
// Three ways to pay for a race's Stamina requirement, evaluated against the same requirement so their
// costs can be compared rather than asserted:
//
//   STAMINA_FLEX     the deck carries a dedicated Stamina support card and turns go into the Stamina
//                    facility. The straightforward answer, and it costs a deck slot.
//   POWER_FLEX       no Stamina support card. The floor leans on Power training's decoded secondary
//                    Stamina payout, with some Stamina turns still spent. Costs fewer deck slots and
//                    more turns, and only works where the secondary payout is large enough.
//   NO_STAMINA_FLEX  no Stamina support card and no Stamina turns at all. Everything comes from
//                    inheritance, secondary payouts and recovery. Cheapest in slots, most fragile.
//
// Nothing here is gated by trainee name. An archetype is legal for a build when the account owns the
// cards it needs, and it survives when the numbers say it does. Whether Power-flex works for a given
// trainee is an output of the model, never an input to it.
//
// The turn profiles below are EDITORIAL_MODEL and are the only editorial numbers in the archetype
// layer. They are weights, not schedules: the planner does not claim a career plays out this way, it
// claims that comparing three builds under three named, fixed spreads is more informative than
// comparing them under one.

import { BUDGET_STATS, type BudgetStat, type BuildArchetype } from "./types.ts"
import type { TurnAllocation } from "./training.ts"

/** A named spread of a career's trainings over the five facilities. Weights, normalized on use. */
export interface ArchetypeProfile {
    readonly archetype: BuildArchetype
    readonly label: string
    readonly weights: Readonly<Record<BudgetStat, number>>
    /** How many support cards of the Stamina training type the archetype allows in the deck. */
    readonly maxStaminaCards: number | null
    readonly minStaminaCards: number
    readonly rationale: string
}

export const ARCHETYPE_PROFILES: readonly ArchetypeProfile[] = [
    {
        archetype: "STAMINA_FLEX",
        label: "stamina-flex",
        weights: { Speed: 0.35, Stamina: 0.3, Power: 0.2, Guts: 0.05, Wit: 0.1 },
        maxStaminaCards: null,
        minStaminaCards: 1,
        rationale: "A dedicated Stamina card and Stamina turns. Buys the floor directly and pays for it with a deck slot.",
    },
    {
        archetype: "POWER_FLEX",
        label: "power-flex",
        weights: { Speed: 0.35, Stamina: 0.15, Power: 0.35, Guts: 0.05, Wit: 0.1 },
        maxStaminaCards: 0,
        minStaminaCards: 0,
        rationale: "No Stamina card. Leans on the decoded secondary Stamina in Power training, keeping the slot for Power output.",
    },
    {
        archetype: "NO_STAMINA_FLEX",
        label: "no-stamina-flex",
        weights: { Speed: 0.4, Stamina: 0, Power: 0.4, Guts: 0.05, Wit: 0.15 },
        maxStaminaCards: 0,
        minStaminaCards: 0,
        rationale: "No Stamina card and no Stamina turns. Inheritance, secondary payouts and recovery carry the whole floor.",
    },
]

export function archetypeProfile(archetype: BuildArchetype): ArchetypeProfile {
    const found = ARCHETYPE_PROFILES.find((p) => p.archetype === archetype)
    if (!found) throw new Error(`no profile for archetype ${archetype}`)
    return found
}

/**
 * Turns a weight profile into whole trainings.
 *
 * Largest-remainder, so the parts sum to the whole exactly and the same input always produces the
 * same spread. A facility whose weight is zero gets zero turns and stays zero: NO_STAMINA_FLEX means
 * no Stamina turns, not "very few".
 */
export function allocateTurns(profile: ArchetypeProfile, trainingTurns: number): TurnAllocation {
    const total = BUDGET_STATS.reduce((s, stat) => s + profile.weights[stat], 0)
    const byStat = {} as Record<BudgetStat, number>
    const remainders: { stat: BudgetStat; remainder: number }[] = []
    let assigned = 0

    for (const stat of BUDGET_STATS) {
        const weight = profile.weights[stat]
        if (weight <= 0 || total <= 0) {
            byStat[stat] = 0
            continue
        }
        const exact = (trainingTurns * weight) / total
        const whole = Math.floor(exact)
        byStat[stat] = whole
        assigned += whole
        remainders.push({ stat, remainder: exact - whole })
    }

    remainders.sort((a, b) => b.remainder - a.remainder || BUDGET_STATS.indexOf(a.stat) - BUDGET_STATS.indexOf(b.stat))
    let leftover = trainingTurns - assigned
    for (const entry of remainders) {
        if (leftover <= 0) break
        byStat[entry.stat] += 1
        leftover -= 1
    }

    return { trainingTurns, byStat, origin: "ARCHETYPE_PROFILE", profileLabel: profile.label }
}

/** Builds an allocation an operator supplied directly, validated against the turn budget it claims. */
export function operatorAllocation(byStat: Readonly<Record<BudgetStat, number>>): TurnAllocation {
    const trainingTurns = BUDGET_STATS.reduce((s, stat) => s + (byStat[stat] ?? 0), 0)
    const normalized = {} as Record<BudgetStat, number>
    for (const stat of BUDGET_STATS) normalized[stat] = byStat[stat] ?? 0
    return { trainingTurns, byStat: normalized, origin: "OPERATOR", profileLabel: null }
}

/** Whether a deck's Stamina-card count satisfies an archetype. Composition only; no name is read. */
export function deckFitsArchetype(profile: ArchetypeProfile, staminaCardCount: number): boolean {
    if (staminaCardCount < profile.minStaminaCards) return false
    if (profile.maxStaminaCards !== null && staminaCardCount > profile.maxStaminaCards) return false
    return true
}
