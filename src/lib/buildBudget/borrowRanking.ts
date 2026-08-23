// STAM-2a Build-aware Smart Borrow ranking. Pure, offline, deterministic, Shadow.
//
// DeckLab already answers "which borrow raises the deck composite most". That is the right question
// when nothing is constrained, and the wrong one the moment a race imposes a Stamina floor: a borrow
// worth two points of composite is worth less than a borrow that turns a build which fails the floor
// into one that clears it, and worth much less than one that unlocks the recovery skill the floor
// assumed. This module asks the second question, and keeps the first one visible beside it so the two
// answers can be compared rather than one quietly replacing the other.
//
// Three rules shape the ranking, and all three exist to stop a score buying its way past a constraint:
//
//   Survival is a TIER, compared first and never summed with anything. A borrow that moves a build
//   from FAILS to MARGINAL, or MARGINAL to CLEARS, beats every borrow that does not, whatever their
//   composites say. No survival bonus is added to any score anywhere.
//
//   Within a tier, candidates are Pareto-filtered on their deltas. There is no scalar. A borrow that
//   buys Power and costs Wit does not dominate one that does the reverse, and the report shows both.
//
//   Relief is named, not scored. SURVIVAL_BLOCKER_REMOVED and STAMINA_DEFICIT_REDUCED are different
//   facts about a borrow and stay different facts all the way into the report.
//
// Nothing here selects a borrow on a device, taps anything, or changes Smart Borrow execution. It
// produces a recommendation and, on request, the same safe intent contract the existing offline
// producer emits, with provenance saying which ranking chose it.

import { bestBorrowSwap } from "../deckLab/deckSearch.ts"
import type { BorrowOption } from "../deckLab/deckSearch.ts"
import type { BorrowCandidate, BorrowPoolResolution } from "../deckLab/borrowPool.ts"
import { ownedCardInput, valueCard } from "../deckLab/cardValue.ts"
import type { DeckTargetBuild } from "../deckLab/deckTarget.ts"
import type { SupportCardIndex } from "../deckLab/supportCardData.ts"
import { SURVIVAL_STAT, survivalTierOf, survivalTierRank, type SurvivalTier } from "./budget.ts"
import type { BuildBudgetEvidence } from "./evidence.ts"
import { archetypeProfile } from "./archetypes.ts"
import { evaluateCandidate, type BuildBudgetInput, type DeckCandidate, type JointBuildCandidate } from "./joint.ts"
import { BUDGET_STATS, BuildBudgetError, weakestConfidence, type BudgetConfidence, type BudgetStat } from "./types.ts"

export const BUILD_AWARE_BORROW_SCHEMA = "build_aware_smart_borrow"
export const BUILD_AWARE_BORROW_SCHEMA_VERSION = 1

/**
 * The named constraints a borrow can relieve.
 *
 * Each one is a fact about what changed, not a weight. They are reported as a set, and the ranking
 * reads only the tier change out of them; the rest exist so an operator can see WHY a borrow moved.
 */
export const RELIEF_DIMENSIONS = [
    /** The build went from not clearing the survival floor to clearing it, at the strict low end. */
    "SURVIVAL_BLOCKER_REMOVED",
    /** A recovery skill the survival minimum assumed became reachable. */
    "RECOVERY_REQUIREMENT_UNLOCKED",
    /** The Stamina deficit narrowed without necessarily closing. */
    "STAMINA_DEFICIT_REDUCED",
    /** The build was above its preferred Stamina range and is now inside it. */
    "OVER_STAMINA_REDUCED",
    /** Stamina the parent pair was supplying became redundant, so inheritance can be re-weighted. */
    "INHERITANCE_FREED",
    "POWER_DEFICIT_REDUCED",
    "SPEED_DEFICIT_REDUCED",
    "WIT_DEFICIT_REDUCED",
    "GUTS_DEFICIT_REDUCED",
] as const
export type ReliefDimension = (typeof RELIEF_DIMENSIONS)[number]

/** Stat -> the relief dimension that names an improvement in it. Survival has its own dimensions. */
const STAT_RELIEF: Readonly<Record<BudgetStat, ReliefDimension | null>> = {
    Speed: "SPEED_DEFICIT_REDUCED",
    Stamina: null,
    Power: "POWER_DEFICIT_REDUCED",
    Guts: "GUTS_DEFICIT_REDUCED",
    Wit: "WIT_DEFICIT_REDUCED",
}

/**
 * What a borrow frees on the inheritance side.
 *
 * The honest version of "you no longer need the Stamina parent". It is derived by re-running the SAME
 * bounded parent-pair set against the deck that includes the borrow and reporting the best pair that
 * still clears, rather than by computing a theoretical optimum nobody owns. When no alternative pair
 * in the set does better, that is reported as no gain rather than as a hypothetical one.
 */
export interface InheritanceOpportunityGain {
    /** Starting stats the alternative pair supplies that the baseline pair did not, per stat. */
    readonly freedStartingStatsByStat: Readonly<Record<BudgetStat, number>>
    /** Stat ceiling the alternative pair supplies that the baseline pair did not. */
    readonly freedCapByStat: Readonly<Record<BudgetStat, number>>
    /** True when a different pair from the bounded set becomes viable once the borrow is in the deck. */
    readonly parentSwapAvailable: boolean
    /** The alternative pair's label, when one was found. */
    readonly alternativePairLabel: string | null
    /** What choosing that pair costs: lineage value given up, if any. Negative means it gains. */
    readonly parentQualityTradeoff: number
    readonly note: string
}

/** One live borrow candidate, evaluated against the whole build rather than against the deck alone. */
export interface BuildAwareBorrowEvaluation {
    readonly supportCardId: number
    readonly displayName: string
    readonly supportType: string

    readonly baselineBuild: JointBuildCandidate
    readonly buildWithBorrow: JointBuildCandidate
    /** The owned card the borrow displaced, under DeckLab's own swap rule. */
    readonly displacedCardId: number | null

    readonly survivalTierBefore: SurvivalTier
    readonly survivalTierAfter: SurvivalTier
    readonly survivalDeficitBefore: number
    readonly survivalDeficitAfter: number

    readonly recoveryRequirementBefore: string
    readonly recoveryRequirementAfter: string
    readonly recoveryNewlyReachable: readonly number[]
    readonly recoveryNewlyLost: readonly number[]

    readonly overStaminaBefore: boolean
    readonly overStaminaAfter: boolean

    readonly inheritanceOpportunity: InheritanceOpportunityGain

    /** Change in each stat's MIDPOINT projection. What a reader compares builds on. */
    readonly statBudgetDelta: Readonly<Record<BudgetStat, number>>
    /**
     * Change in the Stamina projection's LOW end, which is the number the survival tier is decided on.
     *
     * Carried separately because the two can genuinely move in opposite directions: a card whose value
     * is all in unconditional training effectiveness lifts the floor while a card whose value is all in
     * gated friendship lifts the midpoint, so a borrow can close a deficit and still lower the median.
     * Reporting only the median would make that read as a contradiction rather than as the mechanism.
     */
    readonly staminaFloorDelta: number
    readonly skillPointValueDelta: number
    readonly friendshipRampDelta: number
    /** Always true here: every candidate in this ranking is a borrow. Kept so a report can say it. */
    readonly borrowDependency: true

    readonly relief: readonly ReliefDimension[]
    /** DeckLab's own composite improvement for the same swap, carried for the comparison. */
    readonly deckLabImprovement: number

    readonly confidence: BudgetConfidence
    readonly assumptions: readonly string[]
    readonly unknowns: readonly string[]
}

/** The deltas a Pareto comparison reads. Every one is "higher is better". */
export const BORROW_DELTA_DIMENSIONS = ["staminaDelta", "speedDelta", "powerDelta", "gutsDelta", "witDelta", "skillPointDelta", "recoveryDelta", "rampRelief"] as const
export type BorrowDeltaDimension = (typeof BORROW_DELTA_DIMENSIONS)[number]
export type BorrowDeltaVector = Readonly<Record<BorrowDeltaDimension, number>>

function recoveryScore(status: string): number {
    if (status === "SATISFIED" || status === "NO_RECOVERY_ASSUMED") return 2
    if (status === "FELL_BACK_TO_NO_RECOVERY") return 1
    return 0
}

/**
 * The delta vector for a Pareto comparison inside one survival tier.
 *
 * Stamina appears here, but only as the change in the projected budget, NOT as the change in survival
 * margin. Survival is the tier and is settled before this vector is read; letting it in twice would
 * be the scalar-bonus mistake wearing a different hat.
 */
export function borrowDeltaVector(evaluation: BuildAwareBorrowEvaluation): BorrowDeltaVector {
    return {
        staminaDelta: Number(evaluation.statBudgetDelta.Stamina.toFixed(4)),
        speedDelta: Number(evaluation.statBudgetDelta.Speed.toFixed(4)),
        powerDelta: Number(evaluation.statBudgetDelta.Power.toFixed(4)),
        gutsDelta: Number(evaluation.statBudgetDelta.Guts.toFixed(4)),
        witDelta: Number(evaluation.statBudgetDelta.Wit.toFixed(4)),
        skillPointDelta: Number(evaluation.skillPointValueDelta.toFixed(4)),
        recoveryDelta: recoveryScore(evaluation.buildWithBorrow.recoveryPlan.status) - recoveryScore(evaluation.baselineBuild.recoveryPlan.status),
        rampRelief: Number((-evaluation.friendshipRampDelta).toFixed(4)),
    }
}

/**
 * Over-Stamina correction, applied to the vector rather than to a score.
 *
 * Once a build is already above its preferred Stamina range, further Stamina has no survival value
 * left to buy, so crediting it in a Pareto comparison would let a Stamina borrow dominate a Power one
 * on a dimension that has stopped meaning anything. The delta is clipped to zero in exactly that
 * state and nowhere else, which is what lets a Power alternative win without either card being named.
 */
export function correctedDeltaVector(evaluation: BuildAwareBorrowEvaluation): BorrowDeltaVector {
    const raw = borrowDeltaVector(evaluation)
    if (!evaluation.overStaminaAfter || raw.staminaDelta <= 0) return raw
    return { ...raw, staminaDelta: 0 }
}

export function borrowDominates(a: BorrowDeltaVector, b: BorrowDeltaVector): boolean {
    let strictlyBetter = false
    for (const dimension of BORROW_DELTA_DIMENSIONS) {
        if (a[dimension] < b[dimension]) return false
        if (a[dimension] > b[dimension]) strictlyBetter = true
    }
    return strictlyBetter
}

/** Names what a borrow relieved, by comparing the two builds. Facts only, no weights. */
export function reliefOf(evaluation: Omit<BuildAwareBorrowEvaluation, "relief" | "confidence" | "assumptions" | "unknowns">): ReliefDimension[] {
    const out: ReliefDimension[] = []
    if (survivalTierRank(evaluation.survivalTierAfter) > survivalTierRank(evaluation.survivalTierBefore) && evaluation.survivalTierAfter === "CLEARS") {
        out.push("SURVIVAL_BLOCKER_REMOVED")
    }
    if (evaluation.recoveryNewlyReachable.length) out.push("RECOVERY_REQUIREMENT_UNLOCKED")
    if (evaluation.survivalDeficitAfter < evaluation.survivalDeficitBefore) out.push("STAMINA_DEFICIT_REDUCED")
    if (evaluation.overStaminaBefore && !evaluation.overStaminaAfter) out.push("OVER_STAMINA_REDUCED")
    if (evaluation.inheritanceOpportunity.parentSwapAvailable) out.push("INHERITANCE_FREED")
    for (const stat of BUDGET_STATS) {
        const dimension = STAT_RELIEF[stat]
        if (dimension && evaluation.statBudgetDelta[stat] > 0) out.push(dimension)
    }
    return out
}

function statMedian(candidate: JointBuildCandidate, stat: BudgetStat): number {
    return candidate.statBudgets.find((b) => b.stat === stat)?.projected.median ?? 0
}

function statFloor(candidate: JointBuildCandidate, stat: BudgetStat): number {
    return candidate.statBudgets.find((b) => b.stat === stat)?.projected.low ?? 0
}

function emptyStats(): Record<BudgetStat, number> {
    const out = {} as Record<BudgetStat, number>
    for (const stat of BUDGET_STATS) out[stat] = 0
    return out
}

/**
 * Re-runs the bounded parent set against the deck that includes the borrow.
 *
 * The question this answers is not "what is the best pair" but "does the borrow make a DIFFERENT pair
 * viable that was not viable before". A pair only counts as an alternative when it still reaches at
 * least the tier the baseline pair reaches with the borrow: a pair that frees Stamina inheritance by
 * dropping below the floor has not freed anything.
 */
function inheritanceOpportunity(
    evidence: BuildBudgetEvidence,
    index: SupportCardIndex,
    input: BuildBudgetInput,
    borrowDeck: DeckCandidate,
    baselineWithBorrow: JointBuildCandidate,
): InheritanceOpportunityGain {
    const profile = archetypeProfile(baselineWithBorrow.archetype)
    const baselinePair = baselineWithBorrow.parentPair
    const baselineStamina = baselineWithBorrow.inheritance.startStats[SURVIVAL_STAT]?.median ?? 0
    const baselineTier = survivalTierOf(baselineWithBorrow.verdict, baselineWithBorrow.recoveryPlan.effectiveConstraint)

    // Freed inheritance only means something when the tier being held is worth holding. "This pair
    // supplies less Stamina and still FAILS" is true of almost any pair and secures nothing: moving
    // inheritance away from Stamina on a build that misses the floor makes it worse in the only
    // dimension that is currently binding. So the claim is refused outright below the MARGINAL tier
    // rather than reported as a gain that is not one.
    if (survivalTierRank(baselineTier) < survivalTierRank("MARGINAL")) {
        return {
            freedStartingStatsByStat: emptyStats(),
            freedCapByStat: emptyStats(),
            parentSwapAvailable: false,
            alternativePairLabel: null,
            parentQualityTradeoff: 0,
            note: `The build does not reach the survival floor even with this borrow (${baselineTier}), so no inheritance is free to move: every point of Stamina the pair supplies is still doing work.`,
        }
    }

    let best: { candidate: JointBuildCandidate; freedStamina: number } | null = null
    for (const pair of input.parentPairs) {
        if (pair.label === baselinePair.label) continue
        const candidate = evaluateCandidate(evidence, index, input, pair, borrowDeck, profile)
        if (survivalTierRank(survivalTierOf(candidate.verdict, candidate.recoveryPlan.effectiveConstraint)) < survivalTierRank(baselineTier)) continue
        const stamina = candidate.inheritance.startStats[SURVIVAL_STAT]?.median ?? 0
        const freed = baselineStamina - stamina
        if (freed <= 0) continue
        // Most Stamina inheritance released while holding the tier; ties break on the pair label so
        // the answer does not depend on the order the caller happened to supply the pairs in.
        if (!best || freed > best.freedStamina || (freed === best.freedStamina && pair.label < best.candidate.parentPair.label)) {
            best = { candidate, freedStamina: freed }
        }
    }

    if (!best) {
        return {
            freedStartingStatsByStat: emptyStats(),
            freedCapByStat: emptyStats(),
            parentSwapAvailable: false,
            alternativePairLabel: null,
            parentQualityTradeoff: 0,
            note: `No pair in the bounded set of ${input.parentPairs.length} releases Stamina inheritance while holding the ${baselineTier} tier with this borrow. Reported as no gain rather than as a theoretical optimum.`,
        }
    }

    const freedStartingStatsByStat = emptyStats()
    const freedCapByStat = emptyStats()
    for (const stat of BUDGET_STATS) {
        const baselineValue = baselineWithBorrow.inheritance.startStats[stat]?.median ?? 0
        const alternativeValue = best.candidate.inheritance.startStats[stat]?.median ?? 0
        freedStartingStatsByStat[stat] = Number((alternativeValue - baselineValue).toFixed(1))
        freedCapByStat[stat] = (best.candidate.inheritance.capBonus[stat] ?? 0) - (baselineWithBorrow.inheritance.capBonus[stat] ?? 0)
    }

    return {
        freedStartingStatsByStat,
        freedCapByStat,
        parentSwapAvailable: true,
        alternativePairLabel: best.candidate.parentPair.label,
        parentQualityTradeoff: Number((baselineWithBorrow.pareto.lineageValue - best.candidate.pareto.lineageValue).toFixed(4)),
        note: `With this borrow, ${best.candidate.parentPair.label} holds the ${baselineTier} tier while supplying ${best.freedStamina.toFixed(1)} less starting Stamina than the baseline pair, so that much inheritance is free to sit elsewhere.`,
    }
}

export interface BuildAwareBorrowInput {
    /** The joint-build input the baseline was produced from. Reused verbatim; nothing is re-derived. */
    readonly budgetInput: BuildBudgetInput
    /** The build the borrow is measured against. Must be a no-borrow build. */
    readonly baseline: JointBuildCandidate
    /** The resolved live pool. Only its resolved candidates are ever evaluated. */
    readonly resolution: BorrowPoolResolution
    readonly deckTarget: DeckTargetBuild
    /** DeckLab's own borrow ranking, for the side-by-side comparison. */
    readonly deckLabBorrowOptions?: readonly BorrowOption[]
}

/** Why a live borrow candidate was not evaluated. */
export const BORROW_SKIP_REASONS = ["ILLEGAL_FOR_SCENARIO", "NO_LEGAL_SWAP", "SAME_CHARACTER_AS_TRAINEE"] as const
export type BorrowSkipReason = (typeof BORROW_SKIP_REASONS)[number]

export interface SkippedBorrow {
    readonly supportCardId: number
    readonly displayName: string
    readonly reason: BorrowSkipReason
}

export interface BuildAwareBorrowRanking {
    readonly schema: typeof BUILD_AWARE_BORROW_SCHEMA
    readonly schemaVersion: number
    readonly targetLabel: string
    readonly baseline: JointBuildCandidate
    readonly baselineTier: SurvivalTier
    /** Every resolved live candidate that produced a legal swap, best tier first. */
    readonly evaluations: readonly BuildAwareBorrowEvaluation[]
    /** The Pareto frontier within the best reachable survival tier. */
    readonly frontier: readonly BuildAwareBorrowEvaluation[]
    readonly recommended: BuildAwareBorrowEvaluation | null
    /** DeckLab's own top borrow for the same baseline deck, whatever the build says about it. */
    readonly deckLabTop: BorrowOption | null
    readonly changedFromDeckLab: "SAME" | "CHANGED" | "NO_COMPARISON"
    readonly changeReason: string
    readonly skipped: readonly SkippedBorrow[]
    /** Live rows the resolver could not join onto a catalogue card. Never evaluated, never guessed. */
    readonly unresolvedLiveRows: number
    readonly poolTrustedComplete: boolean
    readonly sourceBorrowScanId: string | null
    readonly bounds: {
        readonly parentPairs: number
        readonly resolvedBorrowCandidates: number
        readonly evaluated: number
        readonly exhaustive: false
        readonly note: string
    }
    readonly confidence: BudgetConfidence
    readonly assumptions: readonly string[]
    readonly unknowns: readonly string[]
}

/**
 * Evaluates one resolved live borrow against the baseline build.
 *
 * Returns null when the borrow produces no legal swap into the baseline deck, which is a fact about
 * the deck and the card rather than a judgement about either.
 */
export function evaluateBorrow(
    evidence: BuildBudgetEvidence,
    index: SupportCardIndex,
    input: BuildAwareBorrowInput,
    candidate: BorrowCandidate,
): BuildAwareBorrowEvaluation | { readonly skipped: SkippedBorrow } {
    const { baseline, budgetInput, deckTarget } = input
    const ref = candidate.card.card
    const borrowedProfile = valueCard(index, { ...ownedCardInput(candidate.card, true), owned: false }, deckTarget)

    if (!borrowedProfile.scenarioFit.legal) {
        return { skipped: { supportCardId: ref.supportCardId, displayName: ref.displayName, reason: "ILLEGAL_FOR_SCENARIO" } }
    }
    if (deckTarget.traineeCharaId !== null && borrowedProfile.card.characterId === deckTarget.traineeCharaId) {
        return { skipped: { supportCardId: ref.supportCardId, displayName: ref.displayName, reason: "SAME_CHARACTER_AS_TRAINEE" } }
    }

    const swap = bestBorrowSwap(index, baseline.deck.score, borrowedProfile, deckTarget)
    if (!swap) {
        return { skipped: { supportCardId: ref.supportCardId, displayName: ref.displayName, reason: "NO_LEGAL_SWAP" } }
    }

    const borrowDeck: DeckCandidate = { label: `borrow ${ref.displayName}`, score: swap.deck }
    const profile = archetypeProfile(baseline.archetype)
    const withBorrow = evaluateCandidate(evidence, index, budgetInput, baseline.parentPair, borrowDeck, profile)

    const reachableBefore = new Set(baseline.recoveryPlan.reachable)
    const reachableAfter = new Set(withBorrow.recoveryPlan.reachable)
    const recoveryNewlyReachable = [...reachableAfter].filter((id) => !reachableBefore.has(id)).sort((a, b) => a - b)
    const recoveryNewlyLost = [...reachableBefore].filter((id) => !reachableAfter.has(id)).sort((a, b) => a - b)

    const statBudgetDelta = emptyStats()
    for (const stat of BUDGET_STATS) statBudgetDelta[stat] = Number((statMedian(withBorrow, stat) - statMedian(baseline, stat)).toFixed(4))

    const opportunity = inheritanceOpportunity(evidence, index, budgetInput, borrowDeck, withBorrow)

    const partial = {
        supportCardId: ref.supportCardId,
        displayName: ref.displayName,
        supportType: ref.supportType,
        baselineBuild: baseline,
        buildWithBorrow: withBorrow,
        displacedCardId: swap.displaced?.card.supportCardId ?? null,
        survivalTierBefore: survivalTierOf(baseline.verdict, baseline.recoveryPlan.effectiveConstraint),
        survivalTierAfter: survivalTierOf(withBorrow.verdict, withBorrow.recoveryPlan.effectiveConstraint),
        survivalDeficitBefore: Number(baseline.verdict.staminaDeficit.toFixed(1)),
        survivalDeficitAfter: Number(withBorrow.verdict.staminaDeficit.toFixed(1)),
        recoveryRequirementBefore: baseline.recoveryPlan.status,
        recoveryRequirementAfter: withBorrow.recoveryPlan.status,
        recoveryNewlyReachable,
        recoveryNewlyLost,
        overStaminaBefore: baseline.verdict.overStaminaRisk,
        overStaminaAfter: withBorrow.verdict.overStaminaRisk,
        inheritanceOpportunity: opportunity,
        statBudgetDelta,
        staminaFloorDelta: Number((statFloor(withBorrow, SURVIVAL_STAT) - statFloor(baseline, SURVIVAL_STAT)).toFixed(4)),
        skillPointValueDelta: Number((withBorrow.production.skillPoints.median - baseline.production.skillPoints.median).toFixed(4)),
        friendshipRampDelta: Number((withBorrow.friendshipRampBurden - baseline.friendshipRampBurden).toFixed(4)),
        borrowDependency: true as const,
        deckLabImprovement: swap.improvement,
    }

    const assumptions: string[] = [
        `The borrow displaces ${swap.displaced ? swap.displaced.card.displayName : "no card"} under DeckLab's own swap rule, which is the swap that raises target stat coverage most among the legal ones.`,
        `Both builds are evaluated under the same archetype (${baseline.archetype}), the same parent pair and the same turn budget, so the difference between them is the borrow and nothing else.`,
        opportunity.note,
    ]
    if (candidate.warnings.length) {
        assumptions.push(`The live scan carried ${candidate.warnings.join(", ")} for this card, so its level or limit break may not be what the projection assumed.`)
    }

    const unknowns: string[] = withBorrow.recoveryPlan.status === "NOT_SATISFIED" ? ["the survival minimum assumes recovery this build cannot reach, so its Stamina figures are not comparable to a build that can"] : []

    // Live card resolution is its own evidence channel: a card joined onto the catalogue by a fuzzy
    // name recovery is not as certain as one read exactly, and the ranking must not be more confident
    // than the weakest link in that chain.
    const resolutionConfidence: BudgetConfidence = candidate.resolutionPath === "EXACT_TITLE" && candidate.limitBreakKnown ? "moderate" : "low"
    const confidence = weakestConfidence(baseline.confidence, withBorrow.confidence, resolutionConfidence)

    return { ...partial, relief: reliefOf(partial), confidence, assumptions, unknowns }
}

/**
 * Ranks every resolved live borrow against one baseline build.
 *
 * The ordering is lexicographic on the survival tier and Pareto within it. There is no scalar score
 * anywhere in this function, which is deliberate: the moment survival becomes a number it can be
 * outbid, and the whole point of the phase is that it cannot.
 */
export function rankBuildAwareBorrows(evidence: BuildBudgetEvidence, index: SupportCardIndex, input: BuildAwareBorrowInput): BuildAwareBorrowRanking {
    if (input.baseline.deck.score.borrowedCard) {
        throw new BuildBudgetError("baselineAlreadyBorrows", "the baseline build already borrows a card; a borrow must be measured against a build that does not")
    }

    const evaluations: BuildAwareBorrowEvaluation[] = []
    const skipped: SkippedBorrow[] = []
    for (const candidate of input.resolution.candidates) {
        const result = evaluateBorrow(evidence, index, input, candidate)
        if ("skipped" in result) skipped.push(result.skipped)
        else evaluations.push(result)
    }

    // Best tier first, then deficit closed, then the card id, so the order is total and stable.
    evaluations.sort(
        (a, b) =>
            survivalTierRank(b.survivalTierAfter) - survivalTierRank(a.survivalTierAfter) ||
            a.survivalDeficitAfter - b.survivalDeficitAfter ||
            b.deckLabImprovement - a.deckLabImprovement ||
            a.supportCardId - b.supportCardId,
    )

    const bestTier = evaluations.length ? evaluations[0].survivalTierAfter : "UNSATISFIABLE"
    const inBestTier = evaluations.filter((e) => e.survivalTierAfter === bestTier)
    const frontier = inBestTier
        .filter((candidate) => !inBestTier.some((other) => other !== candidate && borrowDominates(correctedDeltaVector(other), correctedDeltaVector(candidate))))
        .sort((a, b) => a.survivalDeficitAfter - b.survivalDeficitAfter || b.deckLabImprovement - a.deckLabImprovement || a.supportCardId - b.supportCardId)

    const recommended = frontier.length ? frontier[0] : null
    const deckLabTop = input.deckLabBorrowOptions?.length ? input.deckLabBorrowOptions[0] : null

    let changedFromDeckLab: BuildAwareBorrowRanking["changedFromDeckLab"] = "NO_COMPARISON"
    let changeReason = "DeckLab produced no borrow ranking for this target, so there is nothing to compare against."
    if (deckLabTop && recommended) {
        if (deckLabTop.borrowed.card.supportCardId === recommended.supportCardId) {
            changedFromDeckLab = "SAME"
            changeReason = `Both rankings pick ${recommended.displayName}. DeckLab picks it for a composite gain of ${deckLabTop.improvement.toFixed(4)}; the build-aware ranking picks it because it reaches the ${recommended.survivalTierAfter} tier and is not dominated inside it.`
        } else {
            changedFromDeckLab = "CHANGED"
            const displaced = evaluations.find((e) => e.supportCardId === deckLabTop.borrowed.card.supportCardId)
            if (!displaced) {
                changeReason = `DeckLab's pick ${deckLabTop.borrowed.card.displayName} produced no legal swap into the baseline build's deck, so it could not be evaluated against the build at all. ${recommended.displayName} is the best of the ${evaluations.length} that could.`
            } else if (displaced.survivalTierAfter !== recommended.survivalTierAfter) {
                // The tier genuinely moved. This is the case the phase exists for, and it is the only
                // case in which the tier is what decided the pick.
                changeReason =
                    `DeckLab's pick ${deckLabTop.borrowed.card.displayName} reaches only the ${displaced.survivalTierAfter} tier (${displaced.survivalDeficitAfter.toFixed(1)} short) while ${recommended.displayName} reaches ${recommended.survivalTierAfter} (${recommended.survivalDeficitAfter.toFixed(1)} short). ` +
                    `Survival is settled before any composite is read, so the higher tier wins whatever the composites say: ${deckLabTop.borrowed.card.displayName} gains ${deckLabTop.improvement.toFixed(1)} composite and does not change the tier.`
            } else {
                // Same tier. Saying "the higher tier wins" here would be false, and the real reason is
                // the ordering inside the tier, so that is what gets said.
                const deficitMoved = Math.abs(displaced.survivalDeficitAfter - recommended.survivalDeficitAfter) > 0.05
                const within = deficitMoved
                    ? `${recommended.displayName} leaves ${recommended.survivalDeficitAfter.toFixed(1)} short against ${displaced.survivalDeficitAfter.toFixed(1)}`
                    : `neither closes more of the deficit than the other, so the Pareto comparison on the remaining deltas decided it`
                changeReason =
                    `Both picks stay in the ${recommended.survivalTierAfter} tier, so the tier did NOT decide this. ${within}. ` +
                    `DeckLab's pick ${deckLabTop.borrowed.card.displayName} leads on composite (${deckLabTop.improvement.toFixed(1)}) and that is not a dimension this ranking reads.`
            }
        }
    } else if (deckLabTop && !recommended) {
        changedFromDeckLab = "CHANGED"
        changeReason = `DeckLab recommends ${deckLabTop.borrowed.card.displayName}, but no resolved live borrow produced a legal, evaluable swap into the baseline build, so the build-aware ranking recommends none.`
    }

    const assumptions: string[] = [
        `Ranking is lexicographic on the survival tier, then Pareto on the deltas within that tier. No survival bonus is added to any score: a tier change cannot be outbid by a composite gain.`,
        `Only the ${input.resolution.candidates.length} resolved live borrow candidates were evaluated. ${input.resolution.unresolved.length} live rows did not join onto a catalogue card and were never guessed at.`,
        `Every evaluation holds the archetype, parent pair and turn budget of the baseline fixed, so the measured difference is the borrow.`,
    ]
    if (!input.resolution.trustedAsCompletePool) {
        assumptions.push("The live pool is not trusted as complete, so a card absent from it cannot be read as unborrowable, only as unobserved.")
    }
    if (input.resolution.unresolved.length) {
        assumptions.push(`Unresolved live rows stay unresolved in this phase: ${input.resolution.unresolved.map((u) => u.rawCharacter).join("; ")}.`)
    }

    const confidence = recommended ? recommended.confidence : "low"

    return {
        schema: BUILD_AWARE_BORROW_SCHEMA,
        schemaVersion: BUILD_AWARE_BORROW_SCHEMA_VERSION,
        targetLabel: input.budgetInput.targetLabel,
        baseline: input.baseline,
        baselineTier: survivalTierOf(input.baseline.verdict, input.baseline.recoveryPlan.effectiveConstraint),
        evaluations,
        frontier,
        recommended,
        deckLabTop,
        changedFromDeckLab,
        changeReason,
        skipped: skipped.sort((a, b) => a.supportCardId - b.supportCardId),
        unresolvedLiveRows: input.resolution.unresolved.length,
        poolTrustedComplete: input.resolution.trustedAsCompletePool,
        sourceBorrowScanId: input.resolution.snapshot.scanId,
        bounds: {
            parentPairs: input.budgetInput.parentPairs.length,
            resolvedBorrowCandidates: input.resolution.candidates.length,
            evaluated: evaluations.length,
            exhaustive: false,
            note: "One baseline deck, one archetype, the caller's bounded parent set re-run per borrow for the inheritance question. No claim of global optimality over decks or archetypes.",
        },
        confidence,
        assumptions,
        unknowns: [...new Set(evaluations.flatMap((e) => e.unknowns))],
    }
}
