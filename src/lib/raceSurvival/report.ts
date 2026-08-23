// STAM-1 Race Survival Shadow Model - the human-readable report. Pure and deterministic.
//
// The rule this file exists to enforce: no bare number. A reader must be able to see, without opening
// any source, why a Stamina figure came out where it did, which race facts drove it, which recovery
// was counted and which was thrown away, what risk was insured, and what the model is blind to.
//
// Every figure is rendered at a fixed precision so two runs of the same input produce byte-identical
// text. Nothing here reads a clock, a path or an environment.

import type { DebuffScenario, RecoverySkillEvidence, SurvivalEnvelope } from "./types.ts"

function hp(value: number): string {
    return value.toFixed(1)
}

function pct(fraction: number): string {
    return `${(fraction * 100).toFixed(2)}%`
}

function stamina(value: number | null): string {
    return value === null ? "unsatisfiable" : String(value)
}

function describeGates(evidence: RecoverySkillEvidence): string {
    const parts: string[] = []
    if (evidence.gates.distanceTypes) parts.push(`distance ${evidence.gates.distanceTypes.join("/")}`)
    if (evidence.gates.surfaces) parts.push(`surface ${evidence.gates.surfaces.join("/")}`)
    if (evidence.gates.runningStyles) parts.push(`style ${evidence.gates.runningStyles.join("/")}`)
    return parts.length ? parts.join(", ") : "no race gate"
}

function recoveryLine(evidence: RecoverySkillEvidence, maxHp: number): string {
    const name = evidence.canonicalName ?? `skill ${evidence.skillId}`
    const amount = evidence.hpFraction === null ? "no self-targeted HP" : `${pct(evidence.hpFraction)} of MaxHP = ${hp(evidence.hpFraction * maxHp)} HP`
    return `    ${String(evidence.skillId).padEnd(7)} ${name}\n        ${amount}; ${describeGates(evidence)}; activation ${evidence.activationClass ?? "unknown"}; ${evidence.supportStatus}`
}

function scenarioLine(scenario: DebuffScenario, inputStamina: number): string {
    const threats = scenario.threats.length ? scenario.threats.map((t) => `${t.canonicalName ?? `skill ${t.skillId}`} x${t.maxOccurrences}`).join(", ") : "none"
    const survives = scenario.survivesAtInputStamina === null ? "unknown" : scenario.survivesAtInputStamina ? "yes" : "no"
    return `    ${scenario.label.padEnd(20)} HP loss ${pct(scenario.hpLossFraction).padStart(7)}  required Stamina ${stamina(scenario.requiredStaminaTarget).padStart(12)}  survives at ${inputStamina}: ${survives}  [${threats}]`
}

/** Renders one envelope as a decomposed report. */
export function formatSurvivalEnvelope(envelope: SurvivalEnvelope): string {
    const lines: string[] = []
    const race = envelope.race

    lines.push(`Race Survival Shadow Model (${envelope.modelKind}, schema ${envelope.schema} v${envelope.schemaVersion})`)
    lines.push("")
    lines.push("RACE")
    lines.push(`  ${race.targetRace ?? "(unnamed race)"}`)
    lines.push(`  ${race.track ?? "(unknown track)"} ${race.distanceMeters}m ${race.surface} (${race.distanceType})`)
    lines.push(`  course sets ${race.courseSetIds.join(", ") || "none"} (${race.resolution})`)
    lines.push(`  decoded finish-time band ${race.finishTimeSecondsLow.toFixed(1)}s to ${race.finishTimeSecondsHigh.toFixed(1)}s`)
    if (race.groundCondition) lines.push(`  ground condition ${race.groundCondition} (carried, not priced)`)
    lines.push("")

    lines.push("BUILD")
    lines.push(`  strategy ${envelope.strategy}`)
    lines.push(`  Stamina ${envelope.inputStamina} -> MaxHP ${hp(envelope.maxHp)}`)
    lines.push(`  effective HP after recovery and the ${envelope.selectedRiskPolicy} budget: ${hp(envelope.effectiveHp)}`)
    lines.push("")

    lines.push("CRUISE COST")
    lines.push(`  fastest end of the band  ${hp(envelope.baselineRequiredHpLow)} HP`)
    lines.push(`  midpoint                 ${hp(envelope.baselineRequiredHpTarget)} HP`)
    lines.push(`  slowest end of the band  ${hp(envelope.baselineRequiredHpHigh)} HP`)
    lines.push(`  margin at Stamina ${envelope.inputStamina} against the midpoint: ${hp(envelope.baselineMarginHp)} HP`)
    lines.push("")

    lines.push("RECOVERY")
    const contribution = envelope.recoveryContribution
    lines.push(`  supported total ${hp(contribution.totalPotentialHp)} HP potential (expected HP: not decoded)`)
    if (contribution.effectiveStaminaEquivalent !== null) {
        lines.push(`  diagnostic only: the same HP would cost ${contribution.effectiveStaminaEquivalent.toFixed(1)} Stamina under this strategy`)
    }
    if (contribution.supported.length) {
        lines.push("  counted:")
        for (const evidence of contribution.supported) lines.push(recoveryLine(evidence, envelope.maxHp))
    } else {
        lines.push("  counted: none")
    }
    if (contribution.unsupported.length) {
        lines.push("  not counted:")
        for (const evidence of contribution.unsupported) lines.push(recoveryLine(evidence, envelope.maxHp))
    }
    lines.push("")

    lines.push("DEBUFF BUDGET")
    for (const scenario of envelope.debuffScenarios) lines.push(scenarioLine(scenario, envelope.inputStamina))
    lines.push("")

    lines.push(`REQUIRED STAMINA (policy ${envelope.selectedRiskPolicy}, margin ${pct(envelope.marginFraction)})`)
    lines.push(`  at the fastest end of the band  ${stamina(envelope.requiredStaminaLow)}`)
    lines.push(`  at the midpoint                 ${stamina(envelope.requiredStaminaTarget)}`)
    lines.push(`  at the slowest end of the band  ${stamina(envelope.requiredStaminaHigh)}`)
    lines.push(`  survives baseline: ${envelope.survivesBaseline ? "yes" : "no"}   survives selected risk: ${envelope.survivesSelectedRisk ? "yes" : "no"}`)
    lines.push("")

    lines.push("EVIDENCE")
    lines.push("  DECODED_GAME_DATA: course finish-time band, every recovery and debuff HP fraction, every skill race gate.")
    for (const constant of envelope.constants) {
        lines.push(`  ${constant.channel}: ${constant.name} = ${constant.value} (${constant.provenance})`)
    }
    lines.push("")

    lines.push("ASSUMPTIONS")
    for (const assumption of envelope.assumptions) lines.push(`  - ${assumption}`)
    for (const assumption of contribution.assumptions) lines.push(`  - ${assumption}`)
    lines.push("")

    lines.push("NOT PRICED")
    for (const mechanic of envelope.unknownMechanics) lines.push(`  ${mechanic.status.padEnd(18)} ${mechanic.mechanic}\n      ${mechanic.note}`)
    lines.push("")
    lines.push(`CONFIDENCE: ${envelope.confidence}`)

    return lines.join("\n")
}

/** One row of a Stamina sweep. */
export interface StaminaComparisonRow {
    readonly stamina: number
    readonly maxHp: number
    readonly effectiveHp: number
    readonly marginHp: number
    readonly survivesSelectedRisk: boolean
}

/** Renders a Stamina sweep as a fixed-width table. */
export function formatStaminaComparison(rows: readonly StaminaComparisonRow[]): string {
    const lines: string[] = ["STAMINA COMPARISON (against the midpoint of the decoded finish-time band)", "  Stamina    MaxHP  effectiveHP   marginHP  survives"]
    for (const row of rows) {
        lines.push(`  ${String(row.stamina).padStart(7)}  ${hp(row.maxHp).padStart(7)}  ${hp(row.effectiveHp).padStart(11)}  ${hp(row.marginHp).padStart(9)}  ${row.survivesSelectedRisk ? "yes" : "no"}`)
    }
    return lines.join("\n")
}
