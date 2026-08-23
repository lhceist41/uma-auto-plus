// STAM-1 Race Survival Shadow Model - the survival envelope. Pure, offline, deterministic.
//
// This is where the decoded evidence and the external race formulas meet, and it is written so the
// join stays visible. The answer is a range, never a point, and the range does not come from
// hand-waving: it is the game's own per-course finish-time band, priced at both ends.
//
// The survival condition, written out once:
//
//     maxHp * (1 + recoveryFraction - debuffFraction) - flatDebuffHp
//         >=  cruiseDrainPerSecond * raceDurationSeconds  +  marginFraction * maxHp
//
// Recovery and debuffs are both fractions of MaxHP, which is what keeps the whole thing linear in
// Stamina and lets the inverse be solved exactly instead of searched (see mechanics.ts).
//
// What the model is blind to all points the same way. The cruise rate prices holding the course's
// base pace; the last spurt, an uphill, a rush and a soft track all cost MORE than that, and none of
// them is decoded here. So a Stamina figure out of this model is a floor, not a target, and the
// report says so rather than leaving a reader to assume the estimate is centred.

import {
    DEBUFF_BUDGET_OCCURRENCES,
    RACE_SURVIVAL_MODEL_KIND,
    RACE_SURVIVAL_SCHEMA,
    RACE_SURVIVAL_SCHEMA_VERSION,
    RaceSurvivalError,
    UNPRICED_MECHANICS,
} from "./types.ts"
import type { DebuffBudget, DebuffScenario, DebuffThreat, RaceSurvivalInput, RecoveryContribution, RecoverySkillEvidence, SurvivalConstraint, SurvivalEnvelope } from "./types.ts"
import { MAXHP_STAMINA_COEFFICIENT, MODEL_CONSTANTS, STRATEGY_HP_COEFFICIENT, computeCruiseHp, computeMaxHp, solveRequiredStamina } from "./mechanics.ts"
import { resolveCourse, resolveRecoverySkill, resolveWorstThreat } from "./evidence.ts"
import type { EligibilityContext, RaceSurvivalEvidence } from "./evidence.ts"

/** The STAM-2 handoff wire version. Bump only on a breaking shape change. */
export const SURVIVAL_CONSTRAINT_SCHEMA_VERSION = 1

/** The three debuff points every result reports, whatever budget was selected. */
const REPORTED_BUDGETS: readonly DebuffBudget[] = ["BASE", "ONE_STAMINA_DEBUFF", "TWO_STAMINA_DEBUFFS"]

/** Builds a defaulted input from the parts a caller actually has. */
export function createRaceSurvivalInput(partial: Partial<RaceSurvivalInput> & Pick<RaceSurvivalInput, "distanceMeters" | "surface" | "strategy" | "stamina">): RaceSurvivalInput {
    return {
        evidenceVersion: partial.evidenceVersion ?? 0,
        targetRace: partial.targetRace ?? null,
        courseId: partial.courseId ?? null,
        raceTrack: partial.raceTrack ?? null,
        distanceMeters: partial.distanceMeters,
        surface: partial.surface,
        groundCondition: partial.groundCondition ?? null,
        strategy: partial.strategy,
        stamina: partial.stamina,
        guts: partial.guts ?? null,
        targetSpeed: partial.targetSpeed ?? null,
        recoverySkillIds: partial.recoverySkillIds ?? [],
        debuffBudget: partial.debuffBudget ?? "BASE",
        customThreats: partial.customThreats ?? null,
        rushRiskPolicy: partial.rushRiskPolicy ?? null,
        marginFraction: partial.marginFraction ?? 0,
    }
}

/** Total HP a threat set removes, split into the fractional and flat halves. */
function threatTotals(threats: readonly DebuffThreat[]): { fraction: number; flatHp: number } {
    let fraction = 0
    let flatHp = 0
    for (const threat of threats) {
        const occurrences = Math.max(0, threat.maxOccurrences)
        if (threat.hpDamageFraction !== null) fraction += threat.hpDamageFraction * occurrences
        if (threat.flatHpDamage !== null) flatHp += threat.flatHpDamage * occurrences
    }
    return { fraction, flatHp }
}

/** The threat set a named budget resolves to, priced from the decoded debuff pool. */
function threatsForBudget(evidence: RaceSurvivalEvidence, input: RaceSurvivalInput, context: EligibilityContext, budget: DebuffBudget): readonly DebuffThreat[] {
    if (budget === "CUSTOM") return input.customThreats ?? []
    const occurrences = DEBUFF_BUDGET_OCCURRENCES[budget]
    if (occurrences === 0) return []
    const worst = resolveWorstThreat(evidence, context)
    if (!worst) return []
    return [{ ...worst, maxOccurrences: occurrences }]
}

function buildRecoveryContribution(evidence: RaceSurvivalEvidence, input: RaceSurvivalInput, context: EligibilityContext, maxHp: number): { contribution: RecoveryContribution; fraction: number } {
    const resolved: RecoverySkillEvidence[] = [...input.recoverySkillIds].sort((a, b) => a - b).map((id) => resolveRecoverySkill(evidence, id, context))
    const supported = resolved.filter((r) => r.supportStatus === "SUPPORTED")
    const unsupported = resolved.filter((r) => r.supportStatus !== "SUPPORTED")
    const fraction = supported.reduce((sum, r) => sum + (r.hpFraction ?? 0), 0)
    const totalPotentialHp = fraction * maxHp
    const coefficient = STRATEGY_HP_COEFFICIENT[input.strategy]

    const assumptions: string[] = [
        "Each supported recovery skill is counted once. Every skill in the decoded set carries a cooldown longer than any race, so none of them can repeat.",
        "No activation probability is decoded for any skill, so this is potential HP, not expected HP. A skill whose activation class is not PHASE_ONLY may never fire at all.",
    ]
    if (unsupported.length) {
        assumptions.push("Recovery listed as unsupported contributes zero HP, including skills whose nominal effect is large.")
    }

    return {
        contribution: {
            totalPotentialHp,
            expectedHp: null,
            effectiveStaminaEquivalent: coefficient ? totalPotentialHp / (MAXHP_STAMINA_COEFFICIENT * coefficient) : null,
            supported: Object.freeze(supported),
            unsupported: Object.freeze(unsupported),
            assumptions: Object.freeze(assumptions),
        },
        fraction,
    }
}

/**
 * Computes the survival envelope for one build against one race.
 *
 * Throws when the race does not resolve onto a decoded course: without the game's own finish-time
 * band there is no race duration, and inventing one would make every number downstream fiction.
 */
export function computeSurvivalEnvelope(evidence: RaceSurvivalEvidence, input: RaceSurvivalInput): SurvivalEnvelope {
    if (input.marginFraction < 0 || input.marginFraction >= 1) {
        throw new RaceSurvivalError("invalidMargin", `marginFraction must be in [0, 1), got ${input.marginFraction}`)
    }
    const course = resolveCourse(evidence, {
        targetRace: input.targetRace,
        raceTrack: input.raceTrack,
        distanceMeters: input.distanceMeters,
        surface: input.surface,
        groundCondition: input.groundCondition,
    })
    if (course.resolution === "unresolved") {
        throw new RaceSurvivalError("unresolvedCourse", `no decoded course set for ${String(input.raceTrack)} ${input.distanceMeters}m ${input.surface}; the race duration cannot be read and nothing downstream would be evidence-backed`)
    }

    const context: EligibilityContext = { distanceType: course.distanceType, surface: course.surface, strategy: input.strategy }
    const maxHp = computeMaxHp({ stamina: input.stamina, distanceMeters: input.distanceMeters, strategy: input.strategy })

    const { contribution, fraction: recoveryFraction } = buildRecoveryContribution(evidence, input, context, maxHp)

    const selectedThreats = threatsForBudget(evidence, input, context, input.debuffBudget)
    const selected = threatTotals(selectedThreats)

    const durationLow = course.finishTimeSecondsLow
    const durationHigh = course.finishTimeSecondsHigh
    const durationTarget = (durationLow + durationHigh) / 2
    const requiredHpLow = computeCruiseHp(durationLow)
    const requiredHpTarget = computeCruiseHp(durationTarget)
    const requiredHpHigh = computeCruiseHp(durationHigh)

    const effectiveHp = maxHp * (1 + recoveryFraction - selected.fraction) - selected.flatHp
    const baselineMarginHp = effectiveHp - requiredHpTarget

    const solveAt = (requiredHp: number, totals: { fraction: number; flatHp: number }): number | null =>
        solveRequiredStamina({
            requiredHp,
            distanceMeters: input.distanceMeters,
            strategy: input.strategy,
            recoveryFraction,
            debuffFraction: totals.fraction,
            flatDebuffHp: totals.flatHp,
            marginFraction: input.marginFraction,
        })

    const budgets: DebuffBudget[] = input.debuffBudget === "CUSTOM" ? [...REPORTED_BUDGETS, "CUSTOM"] : [...REPORTED_BUDGETS]
    const debuffScenarios: DebuffScenario[] = budgets.map((budget) => {
        const threats = threatsForBudget(evidence, input, context, budget)
        const totals = threatTotals(threats)
        const occurrences = threats.reduce((sum, t) => sum + Math.max(0, t.maxOccurrences), 0)
        const required = solveAt(requiredHpTarget, totals)
        const scenarioEffectiveHp = maxHp * (1 + recoveryFraction - totals.fraction) - totals.flatHp
        return {
            label: budget,
            occurrences,
            threats: Object.freeze(threats),
            hpLossFraction: totals.fraction,
            requiredStaminaTarget: required,
            survivesAtInputStamina: scenarioEffectiveHp >= requiredHpTarget,
        }
    })

    // Baseline means no debuffs and no editorial margin: does the build simply finish the race.
    const survivesBaseline = maxHp * (1 + recoveryFraction) >= requiredHpTarget
    const survivesSelectedRisk = effectiveHp >= requiredHpTarget + input.marginFraction * maxHp

    const assumptions: string[] = [
        `Race duration is the decoded finish-time band for ${course.courseSetIds.length === 1 ? "course set" : "course sets"} ${course.courseSetIds.join(", ")}: ${durationLow.toFixed(1)}s to ${durationHigh.toFixed(1)}s.`,
        "HP is priced at a single cruise rate over that duration. The last spurt, uphills, a rush and a soft track all cost more, and none of them is decoded here, so every Stamina figure below is a floor rather than a target.",
        `Strategy affects MaxHP only (coefficient ${STRATEGY_HP_COEFFICIENT[input.strategy]}). No per-strategy consumption rate is decoded.`,
    ]
    if (course.resolution === "ambiguous") {
        assumptions.push(`The track, distance and surface match ${course.courseSetIds.length} course variants; the band spans all of them, so the range is wider than the real course's.`)
    }
    if (input.debuffBudget !== "BASE" && input.debuffBudget !== "CUSTOM" && !selectedThreats.length) {
        assumptions.push("No decoded debuff can legally target this race, so the selected debuff budget removes no HP.")
    }
    if (input.guts !== null) assumptions.push(`Guts ${input.guts} is carried but not priced: no local evidence fixes its late-race HP relation.`)
    if (input.groundCondition !== null) assumptions.push(`Ground condition ${input.groundCondition} is carried but not priced.`)
    if (input.rushRiskPolicy !== null) assumptions.push(`Rush risk policy "${input.rushRiskPolicy}" is carried but not priced.`)
    if (input.marginFraction > 0) assumptions.push(`A safety margin of ${(input.marginFraction * 100).toFixed(1)}% of MaxHP is an editorial risk policy, not a mechanic.`)

    const allRecoveryResolved = contribution.unsupported.every((r) => r.supportStatus !== "NOT_IN_EVIDENCE")
    const confidence: "low" | "moderate" = course.resolution === "exact" && allRecoveryResolved ? "moderate" : "low"

    return {
        schema: RACE_SURVIVAL_SCHEMA,
        schemaVersion: RACE_SURVIVAL_SCHEMA_VERSION,
        modelKind: RACE_SURVIVAL_MODEL_KIND,
        evidenceVersion: evidence.schemaVersion,
        race: course,
        strategy: input.strategy,
        inputStamina: input.stamina,
        maxHp,
        baselineRequiredHpLow: requiredHpLow,
        baselineRequiredHpTarget: requiredHpTarget,
        baselineRequiredHpHigh: requiredHpHigh,
        effectiveHp,
        baselineMarginHp,
        debuffScenarios: Object.freeze(debuffScenarios),
        recoveryContribution: contribution,
        selectedRiskPolicy: input.debuffBudget,
        marginFraction: input.marginFraction,
        requiredStaminaLow: solveAt(requiredHpLow, selected),
        requiredStaminaTarget: solveAt(requiredHpTarget, selected),
        requiredStaminaHigh: solveAt(requiredHpHigh, selected),
        survivesBaseline,
        survivesSelectedRisk,
        constants: MODEL_CONSTANTS,
        assumptions: Object.freeze(assumptions),
        unknownMechanics: UNPRICED_MECHANICS,
        confidence,
    }
}

/**
 * The STAM-2 handoff for one envelope.
 *
 * Nothing consumes this yet. It exists so the Build Budget Planner has a stable contract to be
 * written against, and so that the thing it will consume is the model's own output rather than a
 * number somebody copied out of a report.
 */
export function buildSurvivalConstraint(envelope: SurvivalEnvelope): SurvivalConstraint {
    const preferred: readonly [number, number] | null =
        envelope.requiredStaminaTarget !== null && envelope.requiredStaminaHigh !== null ? [envelope.requiredStaminaTarget, envelope.requiredStaminaHigh] : null
    return {
        schemaVersion: SURVIVAL_CONSTRAINT_SCHEMA_VERSION,
        targetRace: envelope.race.targetRace,
        targetStrategy: envelope.strategy,
        minimumStamina: envelope.requiredStaminaLow,
        preferredStaminaRange: preferred,
        recoveryRequirements: Object.freeze(envelope.recoveryContribution.supported.map((r) => r.skillId)),
        debuffRiskPolicy: envelope.selectedRiskPolicy,
        confidence: envelope.confidence,
        unknownMechanics: Object.freeze(envelope.unknownMechanics.filter((m) => m.status === "UNKNOWN").map((m) => m.mechanic)),
    }
}
