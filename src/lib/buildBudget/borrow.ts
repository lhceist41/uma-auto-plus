// STAM-2 Joint Build Budget Planner - borrow valuation. Pure, offline, deterministic, advisory.
//
// DeckLab already answers "which borrow raises the deck composite most". That is the right question
// when nothing is constrained and the wrong one when something is: a borrow that adds two points of
// composite is worth less than a borrow that closes a Stamina deficit the build cannot otherwise
// close, and worth much less than one that unlocks the recovery skill the survival minimum assumed.
//
// So this file values a borrow by what CONSTRAINT it relieves, not by how much score it adds:
//
//   it closes a survival deficit          the build goes from failing the floor to clearing it
//   it unlocks required recovery          a recovery the minimum assumed becomes reachable
//   it frees inheritance                  Stamina the pair was supplying becomes redundant, so a
//                                         differently weighted pair becomes viable
//   it reduces over-Stamina               the deck slot it takes was buying Stamina already surplus
//
// Nothing here selects a borrow, emits an intent or touches a device. Smart Borrow's own selection is
// unchanged and this output is not wired to it: STAM-2 defines the valuation, a later phase may
// consume it.

import { SURVIVAL_STAT } from "./budget.ts"
import type { JointBuildCandidate } from "./joint.ts"
import { BUDGET_STATS, type BudgetStat } from "./types.ts"

/** What a borrow does to a build's constraints, rather than to its score. */
export interface BorrowBudgetEffect {
    readonly borrowCardId: number
    readonly borrowDisplayName: string
    readonly survivalDeficitBefore: number
    readonly survivalDeficitAfter: number
    /** Stamina the parent pair supplies that the borrow makes redundant, so inheritance can move. */
    readonly inheritanceOpportunityGain: number
    /** Median stat lost in the other four stats by spending the slot on this borrow. */
    readonly deckOpportunityCost: Readonly<Record<BudgetStat, number>>
    readonly recoveryConstraintChange: {
        readonly statusBefore: string
        readonly statusAfter: string
        readonly newlyReachable: readonly number[]
        readonly newlyUnreachable: readonly number[]
    }
    /** Ordered, plain-language reasons this borrow is or is not worth the slot. */
    readonly relief: readonly string[]
}

function staminaBudget(candidate: JointBuildCandidate) {
    return candidate.statBudgets.find((b) => b.stat === SURVIVAL_STAT)
}

function medianOf(candidate: JointBuildCandidate, stat: BudgetStat): number {
    return candidate.statBudgets.find((b) => b.stat === stat)?.projected.median ?? 0
}

/**
 * Values one borrow by comparing the build that uses it against the same build without it.
 *
 * Both sides must be the same parent pair and the same archetype; comparing a borrowed deck against a
 * different pair's deck would attribute the pair's contribution to the borrow. The caller is
 * responsible for that pairing and the function states the assumption rather than checking a label it
 * cannot verify.
 */
export function valueBorrow(withBorrow: JointBuildCandidate, withoutBorrow: JointBuildCandidate): BorrowBudgetEffect | null {
    const borrowed = withBorrow.deck.score.borrowedCard
    if (!borrowed) return null

    const before = withoutBorrow.verdict.staminaDeficit
    const after = withBorrow.verdict.staminaDeficit

    // Inheritance opportunity: with the borrow in place, how much of the Stamina the Sparks supply is
    // no longer doing any survival work. That is the amount a differently weighted pair could redirect.
    const stamina = staminaBudget(withBorrow)
    const inheritedStamina = stamina?.inheritanceFlat.median ?? 0
    const surplus = stamina?.surplusAbovePreferred ?? 0
    const inheritanceOpportunityGain = Number(Math.min(inheritedStamina, Math.max(0, surplus)).toFixed(1))

    const deckOpportunityCost = {} as Record<BudgetStat, number>
    for (const stat of BUDGET_STATS) {
        const delta = medianOf(withoutBorrow, stat) - medianOf(withBorrow, stat)
        deckOpportunityCost[stat] = Number(Math.max(0, delta).toFixed(1))
    }

    const reachableBefore = new Set(withoutBorrow.recoveryPlan.reachable)
    const reachableAfter = new Set(withBorrow.recoveryPlan.reachable)
    const newlyReachable = [...reachableAfter].filter((id) => !reachableBefore.has(id)).sort((a, b) => a - b)
    const newlyUnreachable = [...reachableBefore].filter((id) => !reachableAfter.has(id)).sort((a, b) => a - b)

    const relief: string[] = []
    if (before > 0 && after === 0) relief.push(`closes a ${before.toFixed(1)} point Stamina survival deficit outright`)
    else if (after < before) relief.push(`narrows the Stamina deficit from ${before.toFixed(1)} to ${after.toFixed(1)} without closing it`)
    else if (after > before) relief.push(`widens the Stamina deficit from ${before.toFixed(1)} to ${after.toFixed(1)}`)
    if (newlyReachable.length) relief.push(`makes required recovery ${newlyReachable.join(", ")} reachable`)
    if (newlyUnreachable.length) relief.push(`loses access to required recovery ${newlyUnreachable.join(", ")} by displacing the card that hinted it`)
    if (inheritanceOpportunityGain > 0) relief.push(`frees ${inheritanceOpportunityGain.toFixed(1)} points of inherited Stamina to be re-weighted toward another stat`)
    if (withBorrow.verdict.overStaminaRisk && !withoutBorrow.verdict.overStaminaRisk) relief.push("pushes the build past the preferred Stamina range, so the slot is now buying Stamina with no survival value")
    const costed = BUDGET_STATS.filter((s) => deckOpportunityCost[s] > 0)
    if (costed.length) relief.push(`costs ${costed.map((s) => `${deckOpportunityCost[s].toFixed(1)} ${s}`).join(", ")} at the midpoint`)
    if (!relief.length) relief.push("relieves no constraint this planner models; DeckLab's own composite is the better guide for it")

    return {
        borrowCardId: borrowed.card.supportCardId,
        borrowDisplayName: borrowed.card.displayName,
        survivalDeficitBefore: Number(before.toFixed(1)),
        survivalDeficitAfter: Number(after.toFixed(1)),
        inheritanceOpportunityGain,
        deckOpportunityCost,
        recoveryConstraintChange: {
            statusBefore: withoutBorrow.recoveryPlan.status,
            statusAfter: withBorrow.recoveryPlan.status,
            newlyReachable,
            newlyUnreachable,
        },
        relief,
    }
}

/** Renders a borrow valuation. Fixed precision, deterministic. */
export function formatBorrowBudgetEffect(effect: BorrowBudgetEffect): string {
    const lines: string[] = []
    lines.push(`  ${effect.borrowDisplayName} (card ${effect.borrowCardId})`)
    lines.push(`    survival deficit ${effect.survivalDeficitBefore.toFixed(1)} -> ${effect.survivalDeficitAfter.toFixed(1)}`)
    lines.push(`    recovery ${effect.recoveryConstraintChange.statusBefore} -> ${effect.recoveryConstraintChange.statusAfter}`)
    for (const reason of effect.relief) lines.push(`    ${reason}`)
    return lines.join("\n")
}
