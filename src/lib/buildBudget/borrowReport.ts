// STAM-2a build-aware borrow report. Pure and deterministic.
//
// The section this report exists for is the comparison: DeckLab's answer and the build-aware answer,
// side by side, with the reason they agree or differ stated in the same breath. A phase that only
// printed the new answer would be impossible to trust, because a reader could not see what it
// changed. When the two agree, that is a result and is reported as one.
//
// Every figure is at fixed precision so two runs of the same input produce byte-identical text.
// Nothing here reads a clock, a path or an environment.

import { SURVIVAL_STAT } from "./budget.ts"
import { BUDGET_STATS, type Bracket } from "./types.ts"
import type { SmartBorrowIntent } from "../deckLab/smartBorrowIntent.ts"
import { BORROW_DELTA_DIMENSIONS, correctedDeltaVector, type BuildAwareBorrowEvaluation, type BuildAwareBorrowRanking } from "./borrowRanking.ts"

function n1(value: number): string {
    return value.toFixed(1)
}

function signed(value: number): string {
    return `${value >= 0 ? "+" : ""}${value.toFixed(1)}`
}

function band(bracket: Bracket): string {
    return `${n1(bracket.low)} to ${n1(bracket.high)} (mid ${n1(bracket.median)})`
}

function tierArrow(evaluation: BuildAwareBorrowEvaluation): string {
    return evaluation.survivalTierBefore === evaluation.survivalTierAfter ? evaluation.survivalTierAfter : `${evaluation.survivalTierBefore} -> ${evaluation.survivalTierAfter}`
}

function evaluationLine(evaluation: BuildAwareBorrowEvaluation, rank: number): string {
    const lines: string[] = []
    lines.push(`  ${String(rank).padStart(2)}. ${evaluation.displayName} (${evaluation.supportType}, card ${evaluation.supportCardId})`)
    lines.push(`      survival ${tierArrow(evaluation)}   deficit ${n1(evaluation.survivalDeficitBefore)} -> ${n1(evaluation.survivalDeficitAfter)}`)
    lines.push(`      stat deltas ${BUDGET_STATS.map((s) => `${s} ${signed(evaluation.statBudgetDelta[s])}`).join("  ")}`)
    lines.push(`      skill points ${signed(evaluation.skillPointValueDelta)}   friendship ramp ${signed(evaluation.friendshipRampDelta)}   DeckLab composite ${signed(evaluation.deckLabImprovement)}`)
    lines.push(`      relief ${evaluation.relief.length ? evaluation.relief.join(", ") : "none this planner models"}`)
    if (evaluation.recoveryRequirementBefore !== evaluation.recoveryRequirementAfter) {
        lines.push(`      recovery ${evaluation.recoveryRequirementBefore} -> ${evaluation.recoveryRequirementAfter}`)
    }
    if (evaluation.recoveryNewlyReachable.length) lines.push(`      newly reachable recovery ${evaluation.recoveryNewlyReachable.join(", ")}`)
    if (evaluation.recoveryNewlyLost.length) lines.push(`      LOST recovery ${evaluation.recoveryNewlyLost.join(", ")} by displacing the card that hinted it`)
    if (evaluation.overStaminaAfter && !evaluation.overStaminaBefore) lines.push("      pushes the build past its preferred Stamina range, so the extra Stamina buys no survival")
    lines.push(`      displaces ${evaluation.displacedCardId === null ? "nothing" : `card ${evaluation.displacedCardId}`}   confidence ${evaluation.confidence}`)
    return lines.join("\n")
}

/** The whole build-aware borrow report. */
export function formatBuildAwareBorrowRanking(ranking: BuildAwareBorrowRanking, intent: SmartBorrowIntent | null = null): string {
    const lines: string[] = []
    const baseline = ranking.baseline
    const constraint = baseline.recoveryPlan.effectiveConstraint
    const staminaBudget = baseline.statBudgets.find((b) => b.stat === SURVIVAL_STAT)

    lines.push(`Build-aware Smart Borrow ranking (schema ${ranking.schema} v${ranking.schemaVersion}) - Shadow only, selects nothing on any device`)
    lines.push("")

    lines.push("TARGET")
    lines.push(`  ${ranking.targetLabel}`)
    lines.push("")

    lines.push("SURVIVAL CONSTRAINT")
    lines.push(`  race ${constraint.targetRace ?? "(unnamed)"} as ${constraint.targetStrategy}, risk policy ${constraint.debuffRiskPolicy}`)
    lines.push(`  minimum Stamina ${constraint.minimumStamina ?? "unsatisfiable"}   preferred ${constraint.preferredStaminaRange ? `${constraint.preferredStaminaRange[0]} to ${constraint.preferredStaminaRange[1]}` : "none"}`)
    lines.push(`  assumes recovery ${constraint.recoveryRequirements.length ? constraint.recoveryRequirements.join(", ") : "none"}`)
    lines.push("")

    lines.push("BASELINE JOINT BUILD (no borrow)")
    lines.push(`  archetype ${baseline.archetype}   class ${baseline.recommendationClass}   tier ${ranking.baselineTier}`)
    lines.push(`  parents ${baseline.parentPair.label}`)
    lines.push(`  deck ${baseline.deck.label}`)
    for (const card of baseline.deck.score.cards) lines.push(`    ${card.card.supportType.padEnd(8)} ${card.card.displayName}`)
    lines.push(`  Stamina ${band(staminaBudget?.projected ?? { low: 0, median: 0, high: 0 })} against a floor of ${staminaBudget?.requiredFloor ?? "unknown"}`)
    lines.push(`  recovery ${baseline.recoveryPlan.status}`)
    lines.push("")

    lines.push("LIVE BORROW POOL")
    lines.push(`  scan ${ranking.sourceBorrowScanId ?? "(no id)"}`)
    lines.push(`  ${ranking.bounds.resolvedBorrowCandidates} resolved candidates, ${ranking.bounds.evaluated} evaluated, ${ranking.skipped.length} skipped, ${ranking.unresolvedLiveRows} live rows unresolved and never guessed at`)
    lines.push(`  pool trusted as complete: ${ranking.poolTrustedComplete ? "yes" : "no"}`)
    for (const skip of ranking.skipped) lines.push(`    skipped ${skip.displayName}: ${skip.reason}`)
    lines.push("")

    lines.push("OLD DECKLAB RANKING (composite only)")
    if (ranking.deckLabTop) {
        lines.push(`  ${ranking.deckLabTop.borrowed.card.displayName} (${ranking.deckLabTop.borrowed.card.supportType}, card ${ranking.deckLabTop.borrowed.card.supportCardId})`)
        lines.push(`  target stat coverage ${signed(ranking.deckLabTop.improvement)} against the best no-borrow deck`)
    } else {
        lines.push("  none: DeckLab produced no borrow ranking for this target")
    }
    lines.push("")

    lines.push("BUILD-AWARE RANKING")
    lines.push("  Ordered by survival tier first, then Pareto on the deltas inside that tier. No survival")
    lines.push("  bonus is added to any score: a tier change cannot be outbid by a composite gain.")
    lines.push("")
    if (!ranking.evaluations.length) {
        lines.push("  no resolved live borrow produced a legal swap into the baseline deck")
    }
    ranking.evaluations.slice(0, 8).forEach((evaluation, i) => lines.push(evaluationLine(evaluation, i + 1)))
    if (ranking.evaluations.length > 8) lines.push(`  ...and ${ranking.evaluations.length - 8} more, all at or below the tier of those above`)
    lines.push("")

    lines.push("PARETO FRONTIER WITHIN THE BEST REACHABLE TIER")
    if (!ranking.frontier.length) lines.push("  empty: nothing was evaluable")
    for (const evaluation of ranking.frontier) {
        const vector = correctedDeltaVector(evaluation)
        lines.push(`  ${evaluation.displayName}`)
        lines.push(`    ${BORROW_DELTA_DIMENSIONS.map((d) => `${d} ${signed(vector[d])}`).join("  ")}`)
    }
    lines.push("")

    lines.push("OLD VS NEW")
    lines.push(`  ${ranking.changedFromDeckLab}`)
    lines.push(`  ${ranking.changeReason}`)
    lines.push("")

    if (ranking.recommended) {
        const recommended = ranking.recommended
        lines.push("CONSTRAINT RELIEF OF THE RECOMMENDATION")
        lines.push(`  ${recommended.displayName}`)
        for (const relief of recommended.relief) lines.push(`    ${relief}`)
        if (!recommended.relief.length) lines.push("    none: it is the least dominated option, not a fix for anything")
        lines.push("")

        lines.push("INHERITANCE OPPORTUNITY")
        const opportunity = recommended.inheritanceOpportunity
        lines.push(`  parent swap available: ${opportunity.parentSwapAvailable ? "yes" : "no"}`)
        if (opportunity.parentSwapAvailable) {
            lines.push(`  alternative pair ${opportunity.alternativePairLabel}`)
            lines.push(`  starting stats moved ${BUDGET_STATS.map((s) => `${s} ${signed(opportunity.freedStartingStatsByStat[s])}`).join("  ")}`)
            lines.push(`  lineage value given up ${signed(opportunity.parentQualityTradeoff)}`)
        }
        lines.push(`  ${opportunity.note}`)
        lines.push("")

        lines.push("TRADEOFFS")
        for (const stat of BUDGET_STATS) {
            const delta = recommended.statBudgetDelta[stat]
            if (Math.abs(delta) < 0.05) continue
            lines.push(`  ${stat.padEnd(8)} ${signed(delta)} at the midpoint`)
        }
        lines.push(`  skill points ${signed(recommended.skillPointValueDelta)}`)
        lines.push(`  friendship ramp burden ${signed(recommended.friendshipRampDelta)} (lower is better)`)
        lines.push("")
    }

    lines.push("BOUNDS")
    lines.push(`  ${ranking.bounds.parentPairs} parent pairs re-run per borrow for the inheritance question, ${ranking.bounds.resolvedBorrowCandidates} resolved live cards`)
    lines.push(`  ${ranking.bounds.note}`)
    lines.push("")

    lines.push("ASSUMPTIONS")
    for (const assumption of ranking.assumptions) lines.push(`  - ${assumption}`)
    lines.push("")

    if (ranking.unknowns.length) {
        lines.push("UNKNOWNS")
        for (const unknown of ranking.unknowns) lines.push(`  - ${unknown}`)
        lines.push("")
    }

    lines.push("SMART BORROW INTENT CANDIDATE")
    if (intent) {
        lines.push(`  ${intent.displayName} (card ${intent.supportCardId})`)
        lines.push(`  source ${intent.recommendationSource}   scan ${intent.sourceBorrowScanId ?? "(no id)"}   digest ${intent.recommendationEvidenceDigest}`)
        lines.push(`  expected level ${intent.expectedLevel ?? "not observed"}   limit break ${intent.expectedLimitBreak ?? "not observed"}   alias ${intent.sourceAlias ?? "none"}`)
        lines.push(`  resolution ${intent.resolutionPath}${intent.warnings.length ? `   warnings ${intent.warnings.join(", ")}` : ""}`)
        lines.push("  Emitted offline only. Nothing in this phase selects it on a device.")
    } else {
        lines.push("  none emitted")
    }
    lines.push("")
    lines.push(`CONFIDENCE: ${ranking.confidence}`)

    return lines.join("\n")
}
