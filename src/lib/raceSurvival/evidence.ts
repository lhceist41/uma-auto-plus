// STAM-1 Race Survival Shadow Model - the decoded-evidence layer. Pure, offline, deterministic.
//
// Reads src/data/race_survival_data.json (generated from the installed game's master.mdb by
// scripts/generate-race-survival-data.mjs) and turns it into the three things the estimator needs:
//
//   a course, resolved from a race's track, distance and surface, carrying the game's own
//   finish-time band for it;
//   a recovery package, resolved from skill ids, carrying each skill's exact HP fraction and whether
//   it can legally fire in this race at all;
//   a threat, resolved the same way, so a debuff budget is priced from the game's numbers instead of
//   from a remembered one.
//
// Everything this file returns is DECODED_GAME_DATA. Where it cannot decide, it says which of the
// four support statuses applies and why, and never falls back to an assumed value.
//
// One deliberate limit: a skill's condition string is only partly decidable offline. The hard race
// gates (distance band, running style, surface) are decidable and are used as eligibility. Everything
// else -- position, pace, slope, overtakes, HP state -- depends on how the race actually unfolds, so
// it is classified into an activation class and never turned into a probability.

import { readFileSync } from "node:fs"
import type { ActivationClass, DebuffThreat, DistanceType, GroundCondition, RaceGates, RaceStrategy, RaceSurface, RecoverySkillEvidence, ResolvedCourse } from "./types.ts"
import { RaceSurvivalError } from "./types.ts"
import { finishTimeSeconds } from "./mechanics.ts"

/** The generator's schema identity. A mismatch is a hard failure, never a silent downgrade. */
export const EVIDENCE_SCHEMA = "race_survival_evidence"
export const EVIDENCE_SCHEMA_VERSION = 1

/** master.mdb target_type 1 is the skill's own runner. */
export const TARGET_TYPE_SELF = 1

/** The target types that name the opposing field. Proven negative-only by the generator. */
export const OPPONENT_TARGET_TYPES: readonly number[] = [4, 9, 18, 19, 20]

/**
 * Upper bound of each distance band, in metres.
 *
 * Derived from the repository's own compiled race catalogue rather than assumed: every Sprint race it
 * ships is 1400m or shorter, every Mile between 1500m and 1800m, every Medium between 1900m and
 * 2400m, and every Long 2500m or longer. The cut points below sit inside those gaps, and the test
 * suite re-derives them against the catalogue so a data refresh that moves a band fails loudly.
 */
export const DISTANCE_BAND_MAX: readonly (readonly [DistanceType, number])[] = [
    ["sprint", 1400],
    ["mile", 1800],
    ["medium", 2400],
]

/**
 * Track-name spellings that differ between the repository's race catalogue and the game database.
 *
 * The catalogue writes the Tokyo City Keiba track "Ooi"; master.mdb's own English text writes "Oi".
 * This is the only divergence across the 14 tracks the catalogue uses, and without it every Ooi race
 * (which includes the current Grand Concert target) resolves to no course at all.
 */
export const TRACK_NAME_ALIASES: Readonly<Record<string, string>> = { Ooi: "Oi" }

/** Condition-string variable -> the activation family it belongs to. */
const CONDITION_VARIABLE_FAMILY: Readonly<Record<string, ActivationClass>> = {
    // Hard race gates. Decided as eligibility, not as activation, so they map to PHASE_ONLY here.
    distance_type: "PHASE_ONLY",
    ground_type: "PHASE_ONLY",
    running_style: "PHASE_ONLY",
    // Timing: every finished race passes through all of these.
    phase: "PHASE_ONLY",
    phase_random: "PHASE_ONLY",
    phase_firsthalf_random: "PHASE_ONLY",
    phase_laterhalf_random: "PHASE_ONLY",
    distance_rate: "PHASE_ONLY",
    distance_rate_after_random: "PHASE_ONLY",
    remain_distance: "PHASE_ONLY",
    remain_distance_viewer_id: "PHASE_ONLY",
    accumulatetime: "PHASE_ONLY",
    is_lastspurt: "PHASE_ONLY",
    // Course geometry: not decoded in this repository.
    slope: "GEOMETRY_CONDITIONAL",
    up_slope_random: "GEOMETRY_CONDITIONAL",
    corner: "GEOMETRY_CONDITIONAL",
    corner_random: "GEOMETRY_CONDITIONAL",
    is_finalcorner: "GEOMETRY_CONDITIONAL",
    // Position and the shape of the field.
    order: "POSITION_CONDITIONAL",
    order_rate: "POSITION_CONDITIONAL",
    order_rate_in20_continue: "POSITION_CONDITIONAL",
    distance_diff_rate: "POSITION_CONDITIONAL",
    bashin_diff_behind: "POSITION_CONDITIONAL",
    near_count: "POSITION_CONDITIONAL",
    is_surrounded: "POSITION_CONDITIONAL",
    infront_near_lane_time: "POSITION_CONDITIONAL",
    blocked_front_continuetime: "POSITION_CONDITIONAL",
    blocked_side_continuetime: "POSITION_CONDITIONAL",
    running_style_count_nige_otherself: "POSITION_CONDITIONAL",
    running_style_count_senko_otherself: "POSITION_CONDITIONAL",
    running_style_count_sashi_otherself: "POSITION_CONDITIONAL",
    running_style_count_oikomi_otherself: "POSITION_CONDITIONAL",
    temptation_opponent_count_infront: "POSITION_CONDITIONAL",
    temptation_opponent_count_behind: "POSITION_CONDITIONAL",
    is_exist_chara_id: "POSITION_CONDITIONAL",
    // In-race events.
    is_overtake: "EVENT_CONDITIONAL",
    is_move_lane: "EVENT_CONDITIONAL",
    change_order_onetime: "EVENT_CONDITIONAL",
    is_badstart: "EVENT_CONDITIONAL",
    // The runner's own HP.
    hp_per: "HP_CONDITIONAL",
    // Other skills having fired first.
    activate_count_heal: "SKILL_CHAIN_CONDITIONAL",
    activate_count_middle: "SKILL_CHAIN_CONDITIONAL",
    activate_count_end_after: "SKILL_CHAIN_CONDITIONAL",
}

/** One HP effect slot as the generator emits it. */
export interface EvidenceHpEffect {
    readonly slot: string
    readonly hpValueRaw: number
    readonly targetType: number
    readonly targetValue: number
}

/** One skill carrying at least one HP effect, as the generator emits it. */
export interface EvidenceHpSkill {
    readonly id: number
    readonly name: string | null
    readonly desc: string | null
    readonly rarity: number
    readonly condition1: string
    readonly precondition1: string
    readonly condition2: string
    readonly precondition2: string
    readonly cooldown1Raw: number
    readonly cooldown2Raw: number
    readonly effects: readonly EvidenceHpEffect[]
}

/** One course set, as the generator emits it. */
export interface EvidenceCourse {
    readonly id: number
    readonly trackId: number
    readonly track: string | null
    readonly distanceMeters: number
    readonly ground: RaceSurface
    readonly inout: number
    readonly turn: number
    readonly finishTimeMinRaw: number
    readonly finishTimeMaxRaw: number
}

/** The generated evidence file, as parsed. */
export interface RaceSurvivalEvidenceDocument {
    readonly schema: string
    readonly schemaVersion: number
    readonly source: string
    readonly fixedPointDivisor: number
    readonly hpAbilityType: number
    readonly courses: readonly EvidenceCourse[]
    readonly hpSkillEffects: readonly EvidenceHpSkill[]
}

/** The read-only evidence surface the model uses. */
export interface RaceSurvivalEvidence {
    readonly schemaVersion: number
    readonly source: string
    readonly courses: readonly EvidenceCourse[]
    readonly hpSkills: readonly EvidenceHpSkill[]
    hpSkillById(id: number): EvidenceHpSkill | undefined
}

/** Builds the evidence surface from the generated file's text, verifying schema identity first. */
export function createRaceSurvivalEvidence(text: string): RaceSurvivalEvidence {
    let doc: RaceSurvivalEvidenceDocument
    try {
        doc = JSON.parse(text) as RaceSurvivalEvidenceDocument
    } catch (e) {
        throw new RaceSurvivalError("malformedEvidence", `race_survival_data.json: ${e instanceof Error ? e.message : String(e)}`)
    }
    if (doc?.schema !== EVIDENCE_SCHEMA) throw new RaceSurvivalError("unexpectedEvidenceSchema", `evidence schema ${String(doc?.schema)} is not ${EVIDENCE_SCHEMA}`)
    if (doc?.schemaVersion !== EVIDENCE_SCHEMA_VERSION) throw new RaceSurvivalError("unsupportedEvidenceVersion", `evidence schema version ${String(doc?.schemaVersion)} is not the supported ${EVIDENCE_SCHEMA_VERSION}`)

    const byId = new Map<number, EvidenceHpSkill>()
    for (const skill of doc.hpSkillEffects ?? []) {
        if (byId.has(skill.id)) throw new RaceSurvivalError("duplicateSkillId", `evidence carries duplicate skill id ${skill.id}`)
        byId.set(skill.id, Object.freeze(skill))
    }
    const courses = Object.freeze((doc.courses ?? []).map((c) => Object.freeze(c)))
    return {
        schemaVersion: doc.schemaVersion,
        source: doc.source,
        courses,
        hpSkills: Object.freeze([...byId.values()]),
        hpSkillById: (id) => byId.get(id),
    }
}

/** Convenience loader for the CLI and tests. */
export function loadRaceSurvivalEvidence(path: string): RaceSurvivalEvidence {
    let text: string
    try {
        text = readFileSync(path, "utf8")
    } catch (e) {
        throw new RaceSurvivalError("missingEvidence", `cannot read ${path}: ${e instanceof Error ? e.message : String(e)}`)
    }
    return createRaceSurvivalEvidence(text)
}

/** The distance band a length falls in. */
export function distanceTypeFor(distanceMeters: number): DistanceType {
    for (const [band, max] of DISTANCE_BAND_MAX) {
        if (distanceMeters <= max) return band
    }
    return "long"
}

/** What a caller knows about the race it wants priced. */
export interface CourseQuery {
    readonly targetRace?: string | null
    readonly raceTrack: string | null
    readonly distanceMeters: number
    readonly surface: RaceSurface
    readonly groundCondition?: GroundCondition | null
}

/**
 * Resolves a race onto the decoded course table.
 *
 * A track plus a distance plus a surface can still match more than one course set, because the game
 * ships inner and outer variants of the same course. When it does, the resolution is reported as
 * ambiguous and the finish-time band widens to cover every match, which is the honest reading: the
 * model does not know which variant a given race uses, so it prices the widest band it could be.
 */
export function resolveCourse(evidence: RaceSurvivalEvidence, query: CourseQuery): ResolvedCourse {
    const distanceType = distanceTypeFor(query.distanceMeters)
    const track = query.raceTrack === null ? null : (TRACK_NAME_ALIASES[query.raceTrack] ?? query.raceTrack)
    const matches = evidence.courses.filter((c) => c.track === track && c.distanceMeters === query.distanceMeters && c.ground === query.surface)

    if (!matches.length) {
        return {
            targetRace: query.targetRace ?? null,
            track,
            distanceMeters: query.distanceMeters,
            distanceType,
            surface: query.surface,
            groundCondition: query.groundCondition ?? null,
            courseSetIds: [],
            finishTimeSecondsLow: 0,
            finishTimeSecondsHigh: 0,
            resolution: "unresolved",
        }
    }

    const ids = matches.map((m) => m.id).sort((a, b) => a - b)
    const low = Math.min(...matches.map((m) => finishTimeSeconds(m.finishTimeMinRaw)))
    const high = Math.max(...matches.map((m) => finishTimeSeconds(m.finishTimeMaxRaw)))
    return {
        targetRace: query.targetRace ?? null,
        track,
        distanceMeters: query.distanceMeters,
        distanceType,
        surface: query.surface,
        groundCondition: query.groundCondition ?? null,
        courseSetIds: ids,
        finishTimeSecondsLow: low,
        finishTimeSecondsHigh: high,
        resolution: matches.length === 1 ? "exact" : "ambiguous",
    }
}

const DISTANCE_TYPE_BY_CODE: Readonly<Record<string, DistanceType>> = { 1: "sprint", 2: "mile", 3: "medium", 4: "long" }
const RUNNING_STYLE_BY_CODE: Readonly<Record<string, RaceStrategy>> = { 1: "front", 2: "pace", 3: "late", 4: "end" }
const GROUND_TYPE_BY_CODE: Readonly<Record<string, RaceSurface>> = { 1: "turf", 2: "dirt" }

/** Splits a skill's condition text into its alternative clauses, each a list of `variable op value`. */
function conditionClauses(condition: string): string[][] {
    if (!condition) return []
    return condition
        .split("@")
        .filter((clause) => clause.length > 0)
        .map((clause) => clause.split("&").filter((term) => term.length > 0))
}

function equalityValues(terms: readonly string[], variable: string): string[] {
    const values: string[] = []
    for (const term of terms) {
        const match = term.match(/^([a-z_0-9]+)==(-?\d+)$/)
        if (match && match[1] === variable) values.push(match[2])
    }
    return values
}

/**
 * The hard race gates a skill carries.
 *
 * A skill's two condition blocks are independent activation branches and its `@` clauses are
 * alternatives, so a gate only counts when EVERY branch carries it: a skill with one Long-only clause
 * and one unrestricted clause is not a Long-only skill. Returning null for an axis means "not gated",
 * which is what makes an eligibility check safe to run in both directions.
 */
export function parseRaceGates(skill: EvidenceHpSkill): RaceGates {
    const blocks = [skill.condition1, skill.condition2].filter((c) => c.length > 0)
    if (!blocks.length) return { distanceTypes: null, runningStyles: null, surfaces: null }

    const collect = <T extends string>(variable: string, table: Readonly<Record<string, T>>): readonly T[] | null => {
        const found = new Set<T>()
        for (const block of blocks) {
            for (const terms of conditionClauses(block)) {
                const values = equalityValues(terms, variable)
                if (!values.length) return null
                for (const value of values) {
                    const mapped = table[value]
                    if (mapped === undefined) return null
                    found.add(mapped)
                }
            }
        }
        return found.size ? Object.freeze([...found].sort()) : null
    }

    return {
        distanceTypes: collect<DistanceType>("distance_type", DISTANCE_TYPE_BY_CODE),
        runningStyles: collect<RaceStrategy>("running_style", RUNNING_STYLE_BY_CODE),
        surfaces: collect<RaceSurface>("ground_type", GROUND_TYPE_BY_CODE),
    }
}

/** How a skill's activation is gated, from its condition variables. Never a probability. */
export function classifyActivation(skill: EvidenceHpSkill): ActivationClass {
    const found = new Set<ActivationClass>()
    let sawUnknownVariable = false
    for (const block of [skill.condition1, skill.condition2, skill.precondition1, skill.precondition2]) {
        for (const terms of conditionClauses(block)) {
            for (const term of terms) {
                const variable = term.replace(/[<>=!].*$/, "")
                if (!variable) continue
                const family = CONDITION_VARIABLE_FAMILY[variable]
                if (family === undefined) sawUnknownVariable = true
                else found.add(family)
            }
        }
    }
    if (sawUnknownVariable) return "MIXED"
    const conditional = [...found].filter((f) => f !== "PHASE_ONLY")
    if (!conditional.length) return "PHASE_ONLY"
    if (conditional.length === 1) return conditional[0]
    return "MIXED"
}

/** The race a skill is being checked against. */
export interface EligibilityContext {
    readonly distanceType: DistanceType
    readonly surface: RaceSurface
    readonly strategy: RaceStrategy
}

/** Whether a skill's hard race gates admit this race and strategy. */
export function gatesAdmit(gates: RaceGates, context: EligibilityContext): boolean {
    if (gates.distanceTypes && !gates.distanceTypes.includes(context.distanceType)) return false
    if (gates.surfaces && !gates.surfaces.includes(context.surface)) return false
    if (gates.runningStyles && !gates.runningStyles.includes(context.strategy)) return false
    return true
}

/** The self-targeted HP gain a skill grants, as a fraction of MaxHP, or null when it grants none. */
export function selfRecoveryFraction(skill: EvidenceHpSkill, fixedPointDivisor = 10000): number | null {
    const gains = skill.effects.filter((e) => e.targetType === TARGET_TYPE_SELF && e.hpValueRaw > 0)
    if (!gains.length) return null
    return gains.reduce((sum, e) => sum + e.hpValueRaw, 0) / fixedPointDivisor
}

/** The HP a skill removes from the opposing field, as a positive fraction, or null when it removes none. */
export function opponentDamageFraction(skill: EvidenceHpSkill, fixedPointDivisor = 10000): number | null {
    const hits = skill.effects.filter((e) => OPPONENT_TARGET_TYPES.includes(e.targetType) && e.hpValueRaw < 0)
    if (!hits.length) return null
    return Math.abs(hits.reduce((sum, e) => sum + e.hpValueRaw, 0)) / fixedPointDivisor
}

/** Resolves one owned skill id into recovery evidence for one specific race. */
export function resolveRecoverySkill(evidence: RaceSurvivalEvidence, skillId: number, context: EligibilityContext): RecoverySkillEvidence {
    const skill = evidence.hpSkillById(skillId)
    if (!skill) {
        return {
            skillId,
            canonicalName: null,
            hpFraction: null,
            targetConditionValid: false,
            activationClass: null,
            supportStatus: "NOT_IN_EVIDENCE",
            gates: { distanceTypes: null, runningStyles: null, surfaces: null },
            channel: "DECODED_GAME_DATA",
            confidence: "unresolved",
        }
    }
    const gates = parseRaceGates(skill)
    const fraction = selfRecoveryFraction(skill)
    const activationClass = classifyActivation(skill)
    const targetConditionValid = fraction !== null
    let supportStatus: RecoverySkillEvidence["supportStatus"]
    if (!targetConditionValid) supportStatus = "NOT_SELF_TARGETED"
    else if (!gatesAdmit(gates, context)) supportStatus = "INELIGIBLE_FOR_TARGET"
    else supportStatus = "SUPPORTED"

    return {
        skillId,
        canonicalName: skill.name,
        hpFraction: fraction,
        targetConditionValid,
        activationClass,
        supportStatus,
        gates,
        channel: "DECODED_GAME_DATA",
        confidence: supportStatus === "SUPPORTED" ? "decoded" : "unresolved",
    }
}

/**
 * The worst stamina debuff the field can legally aim at this build in this race.
 *
 * Resolved from the decoded set rather than named in code, so a debuff budget is priced by whatever
 * the game actually ships for this distance. Ties break on the lower skill id, which keeps the answer
 * deterministic across data refreshes that add an equal-strength debuff.
 */
export function resolveWorstThreat(evidence: RaceSurvivalEvidence, context: EligibilityContext): DebuffThreat | null {
    let best: { skill: EvidenceHpSkill; fraction: number } | null = null
    for (const skill of evidence.hpSkills) {
        const fraction = opponentDamageFraction(skill)
        if (fraction === null) continue
        // The gate is read from the attacker's point of view: a Medium-only debuff can only be cast
        // in a Medium race, so it only threatens a build racing one.
        const gates = parseRaceGates(skill)
        if (gates.distanceTypes && !gates.distanceTypes.includes(context.distanceType)) continue
        if (gates.surfaces && !gates.surfaces.includes(context.surface)) continue
        if (!best || fraction > best.fraction || (fraction === best.fraction && skill.id < best.skill.id)) {
            best = { skill, fraction }
        }
    }
    if (!best) return null
    return {
        skillId: best.skill.id,
        canonicalName: best.skill.name,
        hpDamageFraction: best.fraction,
        flatHpDamage: null,
        maxOccurrences: 1,
        channel: "DECODED_GAME_DATA",
        confidence: "decoded",
    }
}
