// STAM-2 Joint Build Budget Planner - the human-readable report. Pure and deterministic.
//
// The rule this file enforces is the one STAM-1's report enforces: no bare number. A reader must be
// able to see, without opening any source, where every point of a projected stat came from, which of
// those points are decoded and which are bracketed, what the survival requirement was, what recovery
// it assumed, whether this build can actually reach that recovery, what the build gave up to clear
// the floor, and which alternative on the frontier gave up something else instead.
//
// Every figure is rendered at a fixed precision so two runs of the same input produce byte-identical
// text. Nothing here reads a clock, a path or an environment.

import { BUDGET_STATS, type Bracket, type StatBudget } from "./types.ts"
import { SURVIVAL_STAT } from "./budget.ts"
import { PARETO_DIMENSIONS, type JointBuildCandidate, type JointBuildRecommendation } from "./joint.ts"

function n1(value: number): string {
    return value.toFixed(1)
}

function band(bracket: Bracket): string {
    return `${n1(bracket.low)} to ${n1(bracket.high)} (mid ${n1(bracket.median)})`
}

function statLine(budget: StatBudget): string {
    const lines: string[] = []
    const floor = budget.requiredFloor === null ? "" : `   required floor ${budget.requiredFloor}`
    const preferred = budget.preferredRange ? `   preferred ${budget.preferredRange[0]} to ${budget.preferredRange[1]}` : ""
    lines.push(`  ${budget.stat.padEnd(8)} projected ${band(budget.projected).padEnd(34)} cap ${budget.statCap}${floor}${preferred}`)
    lines.push(`      decoded    start ${budget.startStat}  + Sparks ${band(budget.inheritanceFlat)}  + deck initial ${budget.supportInitialStats}`)
    lines.push(`      estimated  own training ${band(budget.deckTrainingContributionEstimate)}  + secondary ${band(budget.secondaryTrainingContributionEstimate)}`)
    lines.push(`      unpriced   support events ${n1(budget.supportEventEstimate.median)}  scenario systems ${n1(budget.scenarioContributionEstimate.median)}`)
    if (budget.deficitToMinimum !== null && budget.deficitToMinimum > 0) lines.push(`      DEFICIT    ${n1(budget.deficitToMinimum)} short of the floor at the low end of the projection`)
    if (budget.surplusAbovePreferred !== null && budget.surplusAbovePreferred > 0) lines.push(`      SURPLUS    ${n1(budget.surplusAbovePreferred)} above the preferred range at the midpoint`)
    if (budget.cappedOut) lines.push("      CAPPED     the midpoint runs into the ceiling; points beyond it are lost")
    return lines.join("\n")
}

function candidateHeading(candidate: JointBuildCandidate): string {
    return `${candidate.archetype} / ${candidate.parentPair.label} / ${candidate.deck.label}`
}

/** One candidate, decomposed. The section a reader acts on. */
export function formatCandidate(candidate: JointBuildCandidate): string {
    const lines: string[] = []
    lines.push(candidateHeading(candidate))
    lines.push(`  class ${candidate.recommendationClass}   confidence ${candidate.confidence}`)
    lines.push("")

    lines.push("  PARENTS")
    lines.push(`    ${candidate.parentPair.parentIds.join(" + ") || "(unnamed pair)"}`)
    for (const priced of candidate.inheritance.priced) {
        const stats = BUDGET_STATS.filter((s) => priced.startStats[s]).map((s) => `${s} ${band(priced.startStats[s] as Bracket)}`)
        const caps = BUDGET_STATS.filter((s) => (priced.capBonus[s] ?? 0) > 0).map((s) => `${s} cap +${priced.capBonus[s]}`)
        const apts = Object.entries(priced.aptitudeSteps).map(([a, v]) => `${a} +${v}`)
        const skills = priced.skills.map((s) => `skill ${s.skillId} lv${s.level}`)
        const effects = [...stats, ...caps, ...apts, ...skills]
        lines.push(`    ${priced.canonicalName.padEnd(28)} ${priced.carriers} carrier${priced.carriers === 1 ? "" : "s"} ${priced.starsPerCarrier.join("+")} stars -> ladder level ${priced.level.lowLevel}..${priced.level.highLevel} of ${priced.level.ladderLength}`)
        if (effects.length) lines.push(`        ${effects.join("; ")}`)
    }
    for (const unpriced of candidate.inheritance.unpriced) {
        lines.push(`    ${unpriced.canonicalName.padEnd(28)} ${unpriced.stars} stars   NOT PRICED (${unpriced.reason})`)
    }
    lines.push("")

    lines.push("  DECK")
    for (const card of candidate.deck.score.cards) {
        const borrowed = card.borrowed ? " (borrowed)" : ""
        lines.push(`    ${card.card.supportType.padEnd(8)} ${card.card.displayName}  LB${card.limitBreakState.limitBreak} lv${card.limitBreakState.level}${borrowed}`)
    }
    lines.push(`    friendship ramp burden ${n1(candidate.friendshipRampBurden)} (comparative index, not turns)`)
    lines.push("")

    lines.push(`  TURNS  ${candidate.allocation.trainingTurns} trainings, ${BUDGET_STATS.map((s) => `${s} ${candidate.allocation.byStat[s]}`).join(", ")}`)
    lines.push(`         source: ${candidate.allocation.origin === "OPERATOR" ? "operator-supplied" : `archetype profile ${candidate.allocation.profileLabel ?? "unnamed"} (editorial)`}`)
    lines.push("")

    lines.push("  STAT BUDGETS")
    for (const budget of candidate.statBudgets) lines.push(statLine(budget))
    lines.push("")

    lines.push("  RECOVERY")
    lines.push(`    ${candidate.recoveryPlan.status}`)
    for (const entry of candidate.recoveryPlan.entries) {
        const cost = entry.skillPointCost === null ? "cost not priced" : `${entry.skillPointCost} SP`
        const hinters = entry.hintingCardIds.length ? ` via card ${entry.hintingCardIds.join(", ")}` : ""
        lines.push(`    skill ${entry.skillId}  ${entry.route}${hinters}  ${entry.route === "DECK_HINT" ? cost : "no skill point cost"}`)
    }
    if (!candidate.recoveryPlan.entries.length) lines.push("    the survival constraint assumed no recovery")
    lines.push("")

    lines.push("  TRADEOFFS")
    if (!candidate.tradeoffs.length) lines.push("    none: no lever supplied Stamina in this build")
    for (const tradeoff of candidate.tradeoffs) {
        lines.push(`    ${tradeoff.lever}: +${n1(tradeoff.staminaGained)} Stamina`)
        for (const cost of tradeoff.costs) lines.push(`        cost: ${cost}`)
    }
    lines.push("")

    lines.push("  PARETO")
    lines.push(`    ${PARETO_DIMENSIONS.map((d) => `${d} ${n1(candidate.pareto[d])}`).join("   ")}`)

    return lines.join("\n")
}

/**
 * Explains a choice by naming what the runner-up would have done differently.
 *
 * A recommendation with no alternative beside it is an assertion. Naming the dimensions the next
 * frontier entry beats it on, and the ones it beats the next entry on, is what turns a pick into a
 * tradeoff a reader can disagree with.
 */
export function formatFrontierComparison(recommended: JointBuildCandidate, alternative: JointBuildCandidate): string {
    const better: string[] = []
    const worse: string[] = []
    for (const dimension of PARETO_DIMENSIONS) {
        const delta = recommended.pareto[dimension] - alternative.pareto[dimension]
        if (delta > 0.0001) better.push(`${dimension} +${n1(delta)}`)
        else if (delta < -0.0001) worse.push(`${dimension} ${n1(delta)}`)
    }
    const lines: string[] = []
    lines.push(`  against ${candidateHeading(alternative)}`)
    lines.push(`    the recommendation is ahead on: ${better.length ? better.join(", ") : "nothing"}`)
    lines.push(`    the alternative is ahead on:    ${worse.length ? worse.join(", ") : "nothing"}`)
    return lines.join("\n")
}

/** The whole report. */
export function formatJointBuildRecommendation(result: JointBuildRecommendation): string {
    const lines: string[] = []
    const constraint = result.survivalConstraint

    lines.push(`Joint Build Budget Planner (schema ${result.schema} v${result.schemaVersion}) - Shadow only, changes nothing the bot does`)
    lines.push("")
    lines.push("TARGET")
    lines.push(`  ${result.target}`)
    lines.push(`  scenario ${result.scenarioId}`)
    lines.push(`  trainee ${result.trainee.traineeName}${result.trainee.cardId === null ? "" : ` (card ${result.trainee.cardId}${result.trainee.starLevel === null ? "" : `, ${result.trainee.starLevel} stars`})`}`)
    lines.push(`  start ${BUDGET_STATS.map((s) => `${s} ${result.trainee.startStats[s]}`).join(", ")}   (${result.trainee.origin.toLowerCase()})`)
    lines.push(`  growth ${BUDGET_STATS.map((s) => `${s} ${result.trainee.growth[s]}%`).join(", ")}`)
    lines.push("")

    lines.push("SURVIVAL REQUIREMENT (from the race survival model)")
    lines.push(`  race ${constraint.targetRace ?? "(unnamed)"} as ${constraint.targetStrategy}, risk policy ${constraint.debuffRiskPolicy}`)
    lines.push(`  minimum ${SURVIVAL_STAT} ${constraint.minimumStamina ?? "unsatisfiable"}   preferred ${constraint.preferredStaminaRange ? `${constraint.preferredStaminaRange[0]} to ${constraint.preferredStaminaRange[1]}` : "none"}`)
    lines.push(`  assumes recovery ${constraint.recoveryRequirements.length ? constraint.recoveryRequirements.join(", ") : "none"}`)
    lines.push(`  survival confidence ${constraint.confidence}`)
    lines.push("")

    lines.push("SEARCH")
    lines.push(`  ${result.bounds.parentPairsConsidered} parent pairs x ${result.bounds.decksConsidered} decks x ${result.bounds.archetypesConsidered} archetypes`)
    lines.push(`  ${result.bounds.combinationsEnumerated} combinations enumerated, ${result.bounds.combinationsRejected} set aside`)
    lines.push(`  ${result.bounds.note}`)
    lines.push("")

    if (result.recommended) {
        lines.push("RECOMMENDED")
        lines.push(formatCandidate(result.recommended))
        lines.push("")
        const alternatives = result.frontier.filter((c) => c !== result.recommended)
        if (alternatives.length) {
            lines.push("ALTERNATIVES ON THE FRONTIER")
            for (const alternative of alternatives) lines.push(formatFrontierComparison(result.recommended, alternative))
            lines.push("")
        } else {
            lines.push("ALTERNATIVES ON THE FRONTIER")
            lines.push("  none: one candidate dominates every other that cleared the floor")
            lines.push("")
        }
    } else {
        lines.push("RECOMMENDED")
        lines.push("  none: no enumerated combination clears the survival floor at the low end of its own projection")
        lines.push("")
    }

    lines.push("BY ARCHETYPE")
    for (const candidate of result.byArchetype) {
        const stamina = candidate.statBudgets.find((b) => b.stat === SURVIVAL_STAT)
        const status = candidate.verdict.survivesSelectedRisk ? `clears the floor with ${n1(candidate.pareto.staminaMargin)} credited margin` : `${n1(candidate.verdict.staminaDeficit)} short`
        lines.push(`  ${candidate.archetype.padEnd(16)} ${status}`)
        lines.push(`    ${candidate.deck.label}`)
        lines.push(`    Stamina ${band(stamina?.projected ?? { low: 0, median: 0, high: 0 })}   Power ${n1(candidate.pareto.powerBudget)}   Speed ${n1(candidate.pareto.speedBudget)}   Wit ${n1(candidate.pareto.witBudget)}`)
        lines.push(`    class ${candidate.recommendationClass}`)
    }
    lines.push("")

    if (result.rejected.length) {
        lines.push("SET ASIDE")
        for (const candidate of result.rejected.slice(0, 5)) {
            lines.push(`  ${candidateHeading(candidate)}`)
            lines.push(`    ${candidate.rejection}: ${candidate.rejectionDetail ?? "no detail"}`)
        }
        if (result.rejected.length > 5) lines.push(`  ...and ${result.rejected.length - 5} more, all for the same kinds of reason`)
        lines.push("")
    }

    lines.push("ASSUMPTIONS")
    for (const assumption of result.assumptions) lines.push(`  - ${assumption}`)
    lines.push("")

    lines.push("NOT PRICED")
    for (const mechanic of result.unknownMechanics) lines.push(`  ${mechanic}`)
    lines.push("")
    lines.push(`CONFIDENCE: ${result.confidence}`)

    return lines.join("\n")
}
