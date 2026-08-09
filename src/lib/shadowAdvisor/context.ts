// Shadow Advisor S1 - the ReplayLab-to-context bridge. This is the ONLY S1 file that knows ReplayLab
// career/decision structure or raw decision_trace / career_state record shapes.
//
// Why it also takes raw records: ReplayLab intentionally does NOT project the raw training candidate
// `gains`/`failChance` (its ReplayTrainingCandidate keeps only the current-policy `score`, which the
// advisor must never read), and ReplayCareer does not attach the per-seq pre-decision CareerState (race
// flags and negative statuses live only in career_state). Since ReplayLab must not be modified, this
// bridge reads the raw per-seq records the offline caller already has, keyed by the SAME authoritative
// (careerToken, seq) ReplayLab established. It joins by seq only, never by turn number, and never reads a
// candidate score, the committed action, enteredRace, transitions, finalize, or any later seq into a context.

import type { ReplayCareer } from "../replayLab.ts"
import type { AdvisorDecisionContext, AdvisorFacilityFact } from "./types.ts"
import { ADVISOR_GAIN_KEYS } from "./types.ts"

/**
 * The raw per-seq source rows the advisor context needs but ReplayLab's projected types omit. The caller
 * builds this from the same decisions.jsonl / career_state.jsonl it fed ReplayLab. `candidates` is the raw
 * decision_trace candidates array (unknown-typed; only training gains/failChance are read). `careerState`
 * is the parsed career_state record object for this seq, or null when no state joined this decision.
 */
export interface AdvisorRawTurn {
    readonly candidates: unknown
    readonly careerState: Record<string, unknown> | null
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

function asFiniteNumber(value: unknown): number | null {
    return typeof value === "number" && Number.isFinite(value) ? value : null
}

function asString(value: unknown): string | null {
    return typeof value === "string" && value.length > 0 ? value : null
}

/** Projects the raw `gains` block over the five canonical stat keys, or null when no gains block was present. */
function projectGains(raw: unknown): Record<string, number> | null {
    if (!isObject(raw)) return null
    const gains: Record<string, number> = {}
    for (const key of ADVISOR_GAIN_KEYS) {
        const v = asFiniteNumber(raw[key])
        if (v !== null) gains[key] = v
    }
    return gains
}

/** Projects pre-decision CareerState facts the advisor needs. Absent fields stay null, never defaulted. */
function projectState(careerState: Record<string, unknown> | null): AdvisorDecisionContext["state"] {
    if (careerState === null) {
        return { energy: null, mood: null, negativeStatuses: null, stats: null, skillPts: null, raceFlags: null }
    }
    const condition = isObject(careerState.condition) ? careerState.condition : null
    const statsObj = isObject(careerState.stats) ? careerState.stats : null
    const raceObj = isObject(careerState.race) ? careerState.race : null

    let stats: Record<string, number> | null = null
    if (statsObj) {
        stats = {}
        for (const key of ADVISOR_GAIN_KEYS) {
            const v = asFiniteNumber(statsObj[key])
            if (v !== null) stats[key] = v
        }
    }

    let negativeStatuses: string[] | null = null
    if (condition) {
        negativeStatuses = Array.isArray(condition.negativeStatuses) ? condition.negativeStatuses.filter((s): s is string => typeof s === "string") : []
    }

    return {
        energy: condition ? asFiniteNumber(condition.energy) : null,
        mood: condition ? asString(condition.mood) : null,
        negativeStatuses,
        stats,
        skillPts: asFiniteNumber(careerState.skillPts),
        raceFlags: raceObj ? { mandatory: raceObj.mandatory === true, scheduled: raceObj.scheduled === true, goalRibbon: raceObj.goalRibbon === true } : null,
    }
}

/**
 * The career identity token a raw career_state row carries in its `identity` block, or null when absent.
 * Used to reject a row that belongs to a different career (a caller mis-supplying a global seq-keyed map
 * would otherwise silently attach another career's same-seq state).
 */
function careerStateToken(careerState: Record<string, unknown>): string | null {
    const identity = isObject(careerState.identity) ? careerState.identity : null
    return identity ? asString(identity.careerToken) : null
}

/** The scenario token from the joined CareerState (never inferred from URA-specific assumptions). */
function projectScenarioType(careerState: Record<string, unknown> | null): string | null {
    if (careerState === null) return null
    const scenario = isObject(careerState.scenario) ? careerState.scenario : null
    return scenario ? asString(scenario.type) : null
}

/** Projects the training contest from raw candidates, gated by ReplayLab's completeness authority. */
function projectTrainingContest(raw: AdvisorRawTurn | null, replayComplete: boolean, replayPresent: boolean): AdvisorDecisionContext["trainingContest"] {
    const candidatesRaw = raw !== null && Array.isArray(raw.candidates) ? raw.candidates : []
    const training = candidatesRaw.filter((c): c is Record<string, unknown> => isObject(c) && c.type === "training")
    if (training.length === 0 && !replayPresent) return null
    const facilities: AdvisorFacilityFact[] = training.map((c) => ({
        id: asString(c.id) ?? "",
        gains: projectGains(c.gains),
        failChance: asFiniteNumber(c.failChance),
    }))
    // Defer to ReplayLab's completeness verdict, and additionally require the raw five-facility set to be
    // present here (a raw-supply gap must never masquerade as a complete contest).
    const complete = replayComplete && facilities.length >= 5
    return { complete, facilities }
}

/**
 * Builds one AdvisorDecisionContext per sequenced JOINED decision of a ReplayLab career. Only decisions
 * that ReplayLab stamped with an authoritative seq are eligible (TRACE_ONLY / unsequenced decisions are
 * skipped: S1 is designed for JOINED replay and never joins by turn). Raw per-seq facts are looked up by
 * that exact seq; a missing raw row yields null facts, which the policy reports as insufficient evidence.
 * Does not mutate any ReplayLab object.
 */
export function buildAdvisorContexts(career: ReplayCareer, rawBySeq: ReadonlyMap<number, AdvisorRawTurn>): AdvisorDecisionContext[] {
    const out: AdvisorDecisionContext[] = []
    for (const d of career.decisions) {
        if (d.seq === null) continue
        const raw = rawBySeq.get(d.seq) ?? null
        // Career-token guard: only use a raw career_state row that belongs to THIS career. A row whose
        // identity token is present and mismatched is nulled out (state becomes unavailable) rather than
        // trusted - no turn fallback, no search for another row. An absent token is trusted per the
        // single-career rawBySeq caller contract. Raw decision candidates carry no career token, so they
        // remain caller-trusted within that same contract (no per-candidate token check is possible).
        const rawState = raw?.careerState ?? null
        const stateToken = rawState !== null ? careerStateToken(rawState) : null
        const careerState = rawState !== null && !(stateToken !== null && stateToken !== career.careerToken) ? rawState : null
        out.push({
            careerToken: career.careerToken,
            seq: d.seq,
            turn: d.observedTurn,
            scenarioType: projectScenarioType(careerState),
            state: projectState(careerState),
            trainingContest: projectTrainingContest(raw, d.trainingContest.complete, d.trainingContest.present),
        })
    }
    return out
}
