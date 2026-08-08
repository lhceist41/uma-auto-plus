// ReplayLab v1 - an offline, read-only reader that reconstructs a factual replay of what the bot
// recorded, from the three durable telemetry streams:
//
//   - decision_trace v1 (decision/candidate/selection evidence)   -> parseDecisionLine
//   - career_state  v1 (pre-decision world facts)                 -> parseCareerStateLine + local projection
//   - career_finalize   (career-level structural outcome)         -> local read-only parser here
//
// Joined records use `careerToken + seq` and nothing else. ReplayLab is deliberately narrow:
//
//   Factual replay          - supported: identity, ordering where seq exists, recorded candidate
//                             contests, recorded selection/action, race-eligibility evidence, scenario
//                             evidence, finalize when joinable, and observed state changes between
//                             sequenced decisions.
//   Policy replay/rescoring  - NOT supported: no scoring kernel is called; only recorded scores are read.
//   Counterfactual replay    - NOT supported: no unchosen-action outcome, no alternate score/result, no
//                             causal per-turn effect is inferred.
//
// Terminology is constrained on purpose: a state delta is a `betweenDecisionObservedTransition`, never
// an "action effect" / "reward" / "causal delta"; the training score margin is `recordedScoreGap`, never
// "regret". These are load-bearing honesty guarantees, not style.
//
// Schema validation for decision_trace and career_state is reused from decisionAnalysis.ts and never
// forked here. career_state's exported parser retains only the join key, so ReplayLab additionally
// projects the state fields it needs (energy/mood/stats/aptitudes/race/scenario) from the same line it
// already validated; it does not re-implement the envelope checks.

import { parseDecisionLine, parseCareerStateLine, EXIT_CLEAN, EXIT_WARNINGS, EXIT_PARSE_OR_SCHEMA, EXIT_CONSISTENCY, worstExit } from "./decisionAnalysis.ts"
import type { DecisionRecord } from "./decisionAnalysis.ts"

/** Output schema discriminator for a ReplayLab JSON export. */
export const REPLAY_SCHEMA = "replay_lab"
/** Output schema version. Additive fields keep this at 1; bump only on a breaking shape change. */
export const REPLAY_SCHEMA_VERSION = 1

/** The five training facilities. A training contest is "complete" only when all five were recorded. */
export const TRAINING_FACILITIES = ["SPEED", "STAMINA", "POWER", "GUTS", "WIT"] as const

/** Fixed stat order for deterministic diffs, matching the corpus writers' short keys. */
const STAT_KEYS = ["spd", "sta", "pwr", "grt", "wit"] as const

/**
 * How much of a career the durable records can reconstruct.
 * - `TRACE_ONLY`: historical seq-less decision traces. Chronological (source order / ts) inspection,
 *   recorded candidates and selection, race-eligibility, and trace-side state - but no authoritative
 *   decision sequence, no CareerState facts, and therefore no seq-to-seq transitions.
 * - `JOINED`: sequenced traces with matching CareerState records. Authoritative seq timeline,
 *   pre-decision CareerState, and between-decision observed transitions.
 */
export type ReplayCapability = "TRACE_ONLY" | "JOINED"

/** Where a recovered numeric score came from. `reasonParsed` = read out of a deterministic reason string. */
export type ScoreSource = "reasonParsed" | "structured" | "unavailable"

/** Cohort identity labels. `app + fp` identify a config/build cohort; they do NOT prove identical policy code. */
export interface ReplayCohort {
    app: string | null
    fp: string | null
    scenario: string | null
    trainee: string | null
    preset: string | null
}

/** One action-cascade candidate as recorded (evidence only; the cascade is not an exhaustive action space). */
export interface ReplayActionCandidate {
    id: string
    selected: boolean
    rejected: boolean
    reason: string | null
}

/** The recorded action cascade for one decision. Candidates are the honest subset the engine ruled on. */
export interface ReplayActionContest {
    kind: "action"
    selectedAction: string | null
    candidates: ReplayActionCandidate[]
}

/** One training facility's recorded candidate line. `score` is null when the writer dropped it (hard-excluded). */
export interface ReplayTrainingCandidate {
    id: string
    selected: boolean
    rejected: boolean
    reason: string | null
    score: number | null
    scoreSource: ScoreSource
}

/**
 * The recorded `recordedScoreGap` for a training contest: the score margin between the selected
 * ANALYSIS training and its best comparably-scored alternative. It is a factual recorded difference,
 * NOT a regret, NOT a counterfactual, and NOT the effect of an action. It is suppressed (value null)
 * whenever any precondition fails; `suppressReason` says which.
 */
export interface ReplayScoreGap {
    eligible: boolean
    value: number | null
    suppressed: boolean
    suppressReason: string | null
    selectedScore: number | null
    scoreSource: ScoreSource
    bestAlternative: { id: string; score: number } | null
    trainingSource: string | null
}

/**
 * The recorded training contest for one decision. `present` is false when the turn recorded no training
 * evaluation. `complete` requires all five facilities. `selected` is the analysis/forced pick; its score
 * lives only in the reason string, so `scoreSource` records how (if at all) it was recovered.
 */
export interface ReplayTrainingContest {
    kind: "training"
    present: boolean
    complete: boolean
    facilitiesPresent: string[]
    trainingSource: string | null
    selected: { id: string; score: number | null; scoreSource: ScoreSource; reason: string | null } | null
    candidates: ReplayTrainingCandidate[]
    recordedScoreGap: ReplayScoreGap
}

/** Trace-side state block recorded on a decision_trace (distinct from the pre-decision CareerState snapshot). */
export interface ReplayTraceState {
    energy: number | null
    mood: string | null
    skillPts: number | null
    stats: Record<string, number> | null
}

/** One reconstructed decision. `seq` is null for TRACE_ONLY records; line order/ts are diagnostics only. */
export interface ReplayDecision {
    lineNumber: number
    ts: number | null
    seq: number | null
    observedTurn: number | null
    turnObserved: boolean
    committedAction: string | null
    committedTraining: string | null
    recovery: { action: string | null; reason: string | null } | null
    raceEligibility: { eligible: boolean; reason: string | null } | null
    actionContest: ReplayActionContest | null
    trainingContest: ReplayTrainingContest
    traceState: ReplayTraceState | null
    /** True only for a JOINED career when a CareerState record joined this decision on (token, seq). */
    hasStateJoin: boolean
}

/** A projected pre-decision CareerState snapshot. Only fields the writer actually recorded are present. */
export interface ReplayState {
    seq: number
    lineNumber: number
    ts: number | null
    turnObserved: boolean
    observedTurn: number | null
    energy: number | null
    mood: string | null
    negativeStatuses: string[] | null
    positiveStatuses: string[] | null
    stats: Record<string, number> | null
    skillPts: number | null
    aptitudes: { surface: Record<string, string>; distance: Record<string, string>; style: Record<string, string> } | null
    race: { mandatory: boolean; scheduled: boolean; goalRibbon: boolean } | null
    scenarioType: string | null
    scenarioExtension: Record<string, number> | null
}

/** One field's observed change across a transition. `kind` distinguishes a numeric delta from a set/availability change. */
export interface StateFieldDiff {
    field: string
    kind: "numeric" | "categorical" | "set" | "availability"
    from: unknown
    to: unknown
    /** Present only for numeric diffs. */
    delta?: number
}

/**
 * An observed transition between two consecutive AVAILABLE sequenced CareerState snapshots. The delta is
 * everything that changed between two pre-decision snapshots - structural non-decision turns, action
 * consequences, next-turn global checks, skill buys, scenario item use and other mutations all fall in
 * the interval. It is explicitly NOT attributed to the action at `fromSeq`.
 */
export interface BetweenDecisionObservedTransition {
    label: "betweenDecisionObservedTransition"
    fromSeq: number
    toSeq: number
    /** True when toSeq === fromSeq + 1. */
    consecutive: boolean
    /** True when the transition spans a sequence gap (a missing/unavailable intermediate state). */
    spansGap: boolean
    seqGap: number
    chosenActionAtFromSeq: string | null
    diffs: StateFieldDiff[]
}

/** Factual, structural finalize fields ReplayLab uses. No grade/score is recorded, so none is invented. */
export interface ReplayFinalize {
    present: boolean
    count: number
    finalizationDecision: string | null
    sessionOutcome: string | null
    verifiedRemainingSp: number | null
    scanComplete: boolean | null
    plannerComplete: boolean | null
    confirmationComplete: boolean | null
    policy: string | null
    objective: string | null
    finalizationReason: string | null
}

/** An anomaly/coverage fact. Most are warnings; `duplicateTokenSeq` is a consistency failure. */
export interface ReplayAnomaly {
    type: ReplayAnomalyType
    careerToken: string | null
    seq: number | null
    detail: string
}

export type ReplayAnomalyType =
    | "duplicateTokenSeq"
    | "seqGap"
    | "traceWithoutState"
    | "stateWithoutTrace"
    | "unparseableSelectedTrainingScore"
    | "incompleteTrainingContest"
    | "mixedCapabilityWithinCareer"
    | "identityInconsistency"
    | "scenarioExtensionInconsistency"

/** One reconstructed career, grouped by careerToken (a resumed segment with a new token is a separate career). */
export interface ReplayCareer {
    careerToken: string
    capability: ReplayCapability
    cohort: ReplayCohort
    decisionCount: number
    sequencedDecisionCount: number
    stateCount: number
    joinedCount: number
    stateWithoutTraceCount: number
    traceWithoutStateCount: number
    seqGapCount: number
    decisions: ReplayDecision[]
    transitions: BetweenDecisionObservedTransition[]
    finalize: ReplayFinalize
    anomalies: ReplayAnomaly[]
}

/** Corpus-level factual summary. No outcome is aggregated as a causal reward. */
export interface ReplaySummary {
    careerCount: number
    traceOnlyCount: number
    joinedCount: number
    decisionCount: number
    sequencedDecisionCount: number
    stateCoverage: { joined: number; sequencedDecisions: number }
    finalizeCoverage: { withFinalize: number; careers: number }
    trainingContestCount: number
    scoreGapEligibleCount: number
    scoreGapSuppressedCount: number
    seqGapCount: number
    anomalyCountsByType: Record<string, number>
}

/** The full ReplayLab result. Deterministic for identical inputs. */
export interface ReplayResult {
    schema: typeof REPLAY_SCHEMA
    version: typeof REPLAY_SCHEMA_VERSION
    careers: ReplayCareer[]
    summary: ReplaySummary
    /** Parse/schema failures across the required streams, in encounter order. */
    failures: { stream: "decisions" | "careerState" | "finalize"; lineNumber: number; detail: string }[]
    exitCode: number
}

/** Options for {@link createReplayLab}. Filters are exact and career-level, so state and trace never diverge. */
export interface ReplayLabOptions {
    careerToken?: string
    scenario?: string
    fp?: string
    action?: string
    strict?: boolean
}

/** The streaming ingester. Feed each corpus line by line, then call {@link ReplayLab.finish}. */
export interface ReplayLab {
    /** @returns false once a strict-mode stop has been triggered (caller should stop feeding decisions). */
    ingestDecisionLine(rawLine: string, lineNumber: number): boolean
    ingestCareerStateLine(rawLine: string, lineNumber: number): void
    ingestFinalizeLine(rawLine: string, lineNumber: number): void
    /** Marks that a career_state file was supplied, so a sequenced career can be classified JOINED. */
    noteCareerStateFile(): void
    finish(): ReplayResult
}

// ---- Small typed helpers over loose JSON (read-only; never mutate input) ----

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

function asString(value: unknown): string | null {
    return typeof value === "string" && value.length > 0 ? value : null
}

function asFiniteNumber(value: unknown): number | null {
    return typeof value === "number" && Number.isFinite(value) ? value : null
}

function asBool(value: unknown): boolean | null {
    return typeof value === "boolean" ? value : null
}

function asStringArray(value: unknown): string[] | null {
    if (!Array.isArray(value)) return null
    return value.filter((v): v is string => typeof v === "string")
}

/**
 * Recovers the selected training's score from its reason string. The writer emits
 * `won analysis (<mode>) with score <N.NN>` (or `... with score ?` when unknown). The mode can itself
 * contain parentheses, so the number is read from the end of the string, never by matching the parens.
 */
export function parseSelectedTrainingScore(reason: string | null): number | null {
    if (reason == null) return null
    const match = reason.match(/with score (-?\d+(?:\.\d+)?)\s*$/)
    if (!match) return null
    const n = Number(match[1])
    return Number.isFinite(n) ? n : null
}

// ---- Projection of a validated career_state line into a ReplayState ----

/**
 * Projects the domain fields ReplayLab needs from an already-validated career_state line. The caller
 * has confirmed via parseCareerStateLine that this is a career_state v1 record with a token and positive
 * seq; here we only read fields, honouring omit-when-unread (absent stays null, never defaulted to zero).
 */
function projectState(raw: string, seq: number, lineNumber: number): ReplayState {
    const record = JSON.parse(raw) as Record<string, unknown>
    const observation = isObject(record.observation) ? record.observation : {}
    const turnObserved = observation.turnObserved === true
    const condition = isObject(record.condition) ? record.condition : {}
    const statsObj = isObject(record.stats) ? record.stats : null
    const aptObj = isObject(record.aptitudes) ? record.aptitudes : null
    const raceObj = isObject(record.race) ? record.race : null
    const scenarioObj = isObject(record.scenario) ? record.scenario : null

    let stats: Record<string, number> | null = null
    if (statsObj) {
        stats = {}
        for (const key of STAT_KEYS) {
            const v = asFiniteNumber(statsObj[key])
            if (v !== null) stats[key] = v
        }
    }

    let aptitudes: ReplayState["aptitudes"] = null
    if (aptObj) {
        aptitudes = { surface: projectAptGroup(aptObj.surface), distance: projectAptGroup(aptObj.distance), style: projectAptGroup(aptObj.style) }
    }

    let race: ReplayState["race"] = null
    if (raceObj) {
        race = { mandatory: raceObj.mandatory === true, scheduled: raceObj.scheduled === true, goalRibbon: raceObj.goalRibbon === true }
    }

    let scenarioType: string | null = null
    let scenarioExtension: Record<string, number> | null = null
    if (scenarioObj) {
        scenarioType = asString(scenarioObj.type)
        // Only the numeric extension fields are diffable; nested inventories/booleans are left out of the diff.
        scenarioExtension = {}
        for (const [k, v] of Object.entries(scenarioObj)) {
            if (k === "type") continue
            const n = asFiniteNumber(v)
            if (n !== null) scenarioExtension[k] = n
        }
    }

    return {
        seq,
        lineNumber,
        ts: asFiniteNumber(record.ts),
        turnObserved,
        observedTurn: turnObserved ? asFiniteNumber(record.turn) : null,
        energy: asFiniteNumber(condition.energy),
        mood: asString(condition.mood),
        negativeStatuses: asStringArray(condition.negativeStatuses) ?? (condition.negativeStatuses === undefined && "energy" in condition ? [] : null),
        positiveStatuses: asStringArray(condition.positiveStatuses) ?? (condition.positiveStatuses === undefined && "energy" in condition ? [] : null),
        stats,
        skillPts: asFiniteNumber(record.skillPts),
        aptitudes,
        race,
        scenarioType,
        scenarioExtension,
    }
}

function projectAptGroup(value: unknown): Record<string, string> {
    const out: Record<string, string> = {}
    if (isObject(value)) {
        for (const [k, v] of Object.entries(value)) {
            if (typeof v === "string") out[k] = v
        }
    }
    return out
}

// ---- Contest classification from a decision_trace record ----

function classifyActionContest(record: DecisionRecord): ReplayActionContest | null {
    const selected = isObject(record.selected) ? record.selected : {}
    const selectedAction = asString(selected.action)
    const candidatesRaw = Array.isArray(record.candidates) ? record.candidates : []
    const candidates: ReplayActionCandidate[] = []
    for (const c of candidatesRaw) {
        if (!isObject(c) || c.type !== "action") continue
        candidates.push({ id: String(c.id ?? ""), selected: c.selected === true, rejected: c.rejected === true, reason: asString(c.reason) })
    }
    if (selectedAction === null && candidates.length === 0) return null
    return { kind: "action", selectedAction, candidates }
}

function classifyTrainingContest(record: DecisionRecord): ReplayTrainingContest {
    const selectedObj = isObject(record.selected) ? record.selected : {}
    const trainingSource = asString(selectedObj.trainingSource)
    const candidatesRaw = Array.isArray(record.candidates) ? record.candidates : []
    const training = candidatesRaw.filter((c): c is Record<string, unknown> => isObject(c) && c.type === "training")

    if (training.length === 0) {
        return emptyTrainingContest(trainingSource)
    }

    const candidates: ReplayTrainingCandidate[] = []
    let selected: ReplayTrainingContest["selected"] = null
    for (const c of training) {
        const id = String(c.id ?? "")
        const isSel = c.selected === true
        const reason = asString(c.reason)
        if (isSel) {
            const score = parseSelectedTrainingScore(reason)
            const scoreSource: ScoreSource = score !== null ? "reasonParsed" : "unavailable"
            selected = { id, score, scoreSource, reason }
            candidates.push({ id, selected: true, rejected: c.rejected === true, reason, score, scoreSource })
        } else {
            const score = asFiniteNumber(c.score)
            candidates.push({ id, selected: false, rejected: c.rejected === true, reason, score, scoreSource: score !== null ? "structured" : "unavailable" })
        }
    }

    const facilitiesPresent = candidates.map((c) => c.id).filter((id) => (TRAINING_FACILITIES as readonly string[]).includes(id))
    const distinctFacilities = new Set(facilitiesPresent)
    const complete = TRAINING_FACILITIES.every((f) => distinctFacilities.has(f))

    const recordedScoreGap = computeRecordedScoreGap(selected, candidates, complete, trainingSource)

    return { kind: "training", present: true, complete, facilitiesPresent: [...distinctFacilities], trainingSource, selected, candidates, recordedScoreGap }
}

function emptyTrainingContest(trainingSource: string | null): ReplayTrainingContest {
    return {
        kind: "training",
        present: false,
        complete: false,
        facilitiesPresent: [],
        trainingSource,
        selected: null,
        candidates: [],
        recordedScoreGap: { eligible: false, value: null, suppressed: true, suppressReason: "no training contest", selectedScore: null, scoreSource: "unavailable", bestAlternative: null, trainingSource },
    }
}

/**
 * Computes recordedScoreGap only for a valid complete ANALYSIS training contest with a recoverable
 * selected score, at least one comparably-scored alternative, AND a selected score that is at least the
 * highest alternative score (score-dominant). The gap is then the winning margin over the best
 * alternative - a factual recorded difference. Any other case is suppressed with a stated reason (never
 * a guess, never a regret).
 *
 * The score-dominance guard is load-bearing, not defensive: the real corpus contains ANALYSIS-labelled
 * picks that were selected BELOW the top raw score (e.g. SPEED "won analysis ... with score 0.00" while
 * POWER scored 71.85). The serialized `score` is only one input to the analysis; a skill-hint/priority
 * path can legitimately win the analysis under a lower raw score. Comparing scores there would fabricate
 * a misleading negative gap - exactly the "must not report 71.85 as regret" case - so it is suppressed.
 */
function computeRecordedScoreGap(
    selected: ReplayTrainingContest["selected"],
    candidates: ReplayTrainingCandidate[],
    complete: boolean,
    trainingSource: string | null,
): ReplayScoreGap {
    const base = { selectedScore: selected?.score ?? null, scoreSource: selected?.scoreSource ?? "unavailable", trainingSource } as const
    if (selected === null) {
        return { eligible: false, value: null, suppressed: true, suppressReason: "no selected training", bestAlternative: null, ...base }
    }
    if (trainingSource !== "ANALYSIS") {
        return { eligible: false, value: null, suppressed: true, suppressReason: `selection source ${trainingSource ?? "unset"} is not a score-comparable ANALYSIS pick`, bestAlternative: null, ...base }
    }
    if (!complete) {
        return { eligible: false, value: null, suppressed: true, suppressReason: "training contest is not the complete five-facility contest", bestAlternative: null, ...base }
    }
    if (selected.score === null) {
        return { eligible: false, value: null, suppressed: true, suppressReason: "selected training score not recoverable from its reason string", bestAlternative: null, ...base }
    }
    let best: { id: string; score: number } | null = null
    for (const c of candidates) {
        if (c.selected || c.score === null) continue
        if (best === null || c.score > best.score) best = { id: c.id, score: c.score }
    }
    if (best === null) {
        return { eligible: false, value: null, suppressed: true, suppressReason: "no comparably-scored alternative training", bestAlternative: null, ...base }
    }
    if (best.score > selected.score) {
        // Selected below the top raw score: the analysis picked it on a factor the score does not capture
        // (hint/priority). A score gap here would be misleading, so it is suppressed rather than reported.
        return { eligible: false, value: null, suppressed: true, suppressReason: `selected training was not score-dominant (selected ${selected.score} < best alternative ${best.id} ${best.score}); selection was not driven by the recorded score`, bestAlternative: best, ...base }
    }
    return { eligible: true, value: selected.score - best.score, suppressed: false, suppressReason: null, bestAlternative: best, ...base }
}

function projectTraceState(record: DecisionRecord): ReplayTraceState | null {
    const state = isObject(record.state) ? record.state : null
    if (!state) return null
    let stats: Record<string, number> | null = null
    for (const key of STAT_KEYS) {
        const v = asFiniteNumber(state[key])
        if (v !== null) {
            stats ??= {}
            stats[key] = v
        }
    }
    return { energy: asFiniteNumber(state.energy), mood: asString(state.mood), skillPts: asFiniteNumber(state.skillPts), stats }
}

function toReplayDecision(record: DecisionRecord, lineNumber: number): ReplayDecision {
    const selected = isObject(record.selected) ? record.selected : {}
    const recoveryObj = isObject(selected.recovery) ? selected.recovery : null
    const raceElig = isObject(record.raceEligibility) ? record.raceEligibility : null
    const seq = typeof record.seq === "number" && Number.isInteger(record.seq) && record.seq > 0 ? record.seq : null
    return {
        lineNumber,
        ts: asFiniteNumber(record.ts),
        seq,
        observedTurn: asFiniteNumber(record.turn),
        turnObserved: isObject(record.observation) ? record.observation.turnObserved === true : false,
        committedAction: asString(selected.action),
        committedTraining: asString(selected.training),
        recovery: recoveryObj ? { action: asString(recoveryObj.action), reason: asString(recoveryObj.reason) } : null,
        raceEligibility: raceElig ? { eligible: raceElig.eligible === true, reason: asString(raceElig.reason) } : null,
        actionContest: classifyActionContest(record),
        trainingContest: classifyTrainingContest(record),
        traceState: projectTraceState(record),
        hasStateJoin: false,
    }
}

function cohortFromDecision(record: DecisionRecord): ReplayCohort {
    return { app: asString(record.app), fp: asString(record.fp), scenario: asString(record.scenario), trainee: asString(record.trainee), preset: asString(record.preset) }
}

// ---- State diff ----

function diffStates(from: ReplayState, to: ReplayState): StateFieldDiff[] {
    const diffs: StateFieldDiff[] = []
    numericDiff(diffs, "energy", from.energy, to.energy)
    categoricalDiff(diffs, "mood", from.mood, to.mood)
    numericDiff(diffs, "skillPts", from.skillPts, to.skillPts)
    for (const key of STAT_KEYS) {
        numericDiff(diffs, `stats.${key}`, from.stats?.[key] ?? null, to.stats?.[key] ?? null, from.stats != null, to.stats != null)
    }
    setDiff(diffs, "negativeStatuses", from.negativeStatuses, to.negativeStatuses)
    setDiff(diffs, "positiveStatuses", from.positiveStatuses, to.positiveStatuses)
    aptitudeDiff(diffs, from.aptitudes, to.aptitudes)
    if (from.race && to.race) {
        for (const key of ["mandatory", "scheduled", "goalRibbon"] as const) {
            if (from.race[key] !== to.race[key]) diffs.push({ field: `race.${key}`, kind: "categorical", from: from.race[key], to: to.race[key] })
        }
    }
    // Scenario-extension numbers are only comparable when the scenario type is unchanged; a type change is
    // flagged separately as an anomaly by the assembler and must not be diffed casually.
    if (from.scenarioType && to.scenarioType && from.scenarioType === to.scenarioType && from.scenarioExtension && to.scenarioExtension) {
        const keys = [...new Set([...Object.keys(from.scenarioExtension), ...Object.keys(to.scenarioExtension)])].sort()
        for (const key of keys) {
            numericDiff(diffs, `scenario.${key}`, from.scenarioExtension[key] ?? null, to.scenarioExtension[key] ?? null, key in from.scenarioExtension, key in to.scenarioExtension)
        }
    }
    return diffs
}

function numericDiff(out: StateFieldDiff[], field: string, from: number | null, to: number | null, fromAvail = from !== null, toAvail = to !== null): void {
    // Absent/unread is never treated as zero: an availability change is reported as such, not as a delta.
    if (!fromAvail || !toAvail || from === null || to === null) {
        if (fromAvail !== toAvail) out.push({ field, kind: "availability", from: fromAvail ? from : null, to: toAvail ? to : null })
        return
    }
    if (from !== to) out.push({ field, kind: "numeric", from, to, delta: to - from })
}

function categoricalDiff(out: StateFieldDiff[], field: string, from: string | null, to: string | null): void {
    if (from === null || to === null) {
        if ((from === null) !== (to === null)) out.push({ field, kind: "availability", from, to })
        return
    }
    if (from !== to) out.push({ field, kind: "categorical", from, to })
}

function setDiff(out: StateFieldDiff[], field: string, from: string[] | null, to: string[] | null): void {
    if (from === null || to === null) {
        if ((from === null) !== (to === null)) out.push({ field, kind: "availability", from, to })
        return
    }
    const fromSet = new Set(from)
    const toSet = new Set(to)
    const added = to.filter((s) => !fromSet.has(s)).sort()
    const removed = from.filter((s) => !toSet.has(s)).sort()
    if (added.length > 0 || removed.length > 0) out.push({ field, kind: "set", from: removed, to: added })
}

function aptitudeDiff(out: StateFieldDiff[], from: ReplayState["aptitudes"], to: ReplayState["aptitudes"]): void {
    if (!from || !to) {
        if (!!from !== !!to) out.push({ field: "aptitudes", kind: "availability", from: from ? "present" : null, to: to ? "present" : null })
        return
    }
    for (const group of ["surface", "distance", "style"] as const) {
        const keys = [...new Set([...Object.keys(from[group]), ...Object.keys(to[group])])].sort()
        for (const key of keys) {
            const a = from[group][key] ?? null
            const b = to[group][key] ?? null
            if (a !== null && b !== null && a !== b) out.push({ field: `aptitudes.${group}.${key}`, kind: "categorical", from: a, to: b })
        }
    }
}

// ---- Local read-only finalize parser ----

interface FinalizeRecord {
    careerToken: string
    finalizationDecision: string | null
    sessionOutcome: string | null
    verifiedRemainingSp: number | null
    scanComplete: boolean | null
    plannerComplete: boolean | null
    confirmationComplete: boolean | null
    policy: string | null
    objective: string | null
    finalizationReason: string | null
}

type FinalizeParse =
    | { kind: "skip" }
    | { kind: "parseError"; message: string }
    | { kind: "record"; record: FinalizeRecord }

/**
 * The smallest read-only career_finalize parser ReplayLab needs. careers.jsonl is a mixed corpus, so a
 * line that is not a `career_finalize` object is skipped (not an error). A `career_finalize` missing its
 * `careerToken` join key is a parse/schema failure. Only the structural fields ReplayLab exposes are read.
 */
function parseFinalizeLine(rawLine: string): FinalizeParse {
    const trimmed = rawLine.trim()
    if (trimmed.length === 0) return { kind: "skip" }
    let parsed: unknown
    try {
        parsed = JSON.parse(trimmed)
    } catch (e) {
        return { kind: "parseError", message: e instanceof Error ? e.message : String(e) }
    }
    if (!isObject(parsed) || parsed.type !== "career_finalize") return { kind: "skip" }
    const token = asString(parsed.careerToken)
    if (token === null) return { kind: "parseError", message: "career_finalize record missing careerToken" }
    return {
        kind: "record",
        record: {
            careerToken: token,
            finalizationDecision: asString(parsed.finalizationDecision),
            sessionOutcome: asString(parsed.sessionOutcome),
            verifiedRemainingSp: asFiniteNumber(parsed.verifiedRemainingSp),
            scanComplete: asBool(parsed.scanComplete),
            plannerComplete: asBool(parsed.plannerComplete),
            confirmationComplete: asBool(parsed.confirmationComplete),
            policy: asString(parsed.policy),
            objective: asString(parsed.objective),
            finalizationReason: asString(parsed.finalizationReason),
        },
    }
}

// ---- The ingester ----

interface DecisionEntry {
    decision: ReplayDecision
    record: DecisionRecord
}

export function createReplayLab(options: ReplayLabOptions = {}): ReplayLab {
    const decisionsByToken = new Map<string, DecisionEntry[]>()
    const statesByToken = new Map<string, Map<number, ReplayState>>()
    const duplicateStateKeys: { token: string; seq: number }[] = []
    const finalizeByToken = new Map<string, FinalizeRecord[]>()
    const failures: ReplayResult["failures"] = []
    let careerStateSupplied = false
    let stopped = false

    function tokenPasses(token: string | null, scenario: string | null, fp: string | null): boolean {
        if (options.careerToken !== undefined && token !== options.careerToken) return false
        if (options.scenario !== undefined && scenario !== options.scenario) return false
        if (options.fp !== undefined && fp !== options.fp) return false
        return true
    }

    function ingestDecisionLine(rawLine: string, lineNumber: number): boolean {
        if (stopped) return false
        const parsed = parseDecisionLine(rawLine, lineNumber)
        if (parsed.kind === "blank") return true
        if (parsed.kind !== "record") {
            failures.push({ stream: "decisions", lineNumber, detail: describeParseFailure(parsed) })
            if (options.strict) stopped = true
            return !stopped
        }
        const record = parsed.record
        const token = asString(record.careerToken)
        if (!tokenPasses(token, asString(record.scenario), asString(record.fp))) return true
        // A record with no careerToken cannot be grouped or joined; it is kept under a stable unkeyed bucket
        // so its evidence is still inspectable, but it can never be JOINED.
        const key = token ?? UNKEYED
        const decision = toReplayDecision(record, lineNumber)
        const list = decisionsByToken.get(key)
        if (list) list.push({ decision, record })
        else decisionsByToken.set(key, [{ decision, record }])
        return true
    }

    function ingestCareerStateLine(rawLine: string, lineNumber: number): void {
        const parsed = parseCareerStateLine(rawLine, lineNumber)
        if (parsed.kind === "blank") return
        if (parsed.kind !== "record") {
            failures.push({ stream: "careerState", lineNumber, detail: describeParseFailure(parsed) })
            return
        }
        // parseCareerStateLine validated the envelope + join key; project the domain fields from the same line.
        const { careerToken, seq } = parsed
        // The careerToken filter is applied to the state stream too, so filtered runs never distort the join.
        // scenario/fp live in the state's identity block, read here only to honour those filters symmetrically.
        const rec = JSON.parse(rawLine.trim()) as Record<string, unknown>
        const identity = isObject(rec.identity) ? rec.identity : {}
        if (!tokenPasses(careerToken, asString(identity.scenario), asString(identity.fp))) return
        const state = projectState(rawLine, seq, lineNumber)
        let seqMap = statesByToken.get(careerToken)
        if (!seqMap) {
            seqMap = new Map()
            statesByToken.set(careerToken, seqMap)
        }
        if (seqMap.has(seq)) duplicateStateKeys.push({ token: careerToken, seq })
        else seqMap.set(seq, state)
    }

    function ingestFinalizeLine(rawLine: string, lineNumber: number): void {
        const parsed = parseFinalizeLine(rawLine)
        if (parsed.kind === "skip") return
        if (parsed.kind === "parseError") {
            failures.push({ stream: "finalize", lineNumber, detail: parsed.message })
            return
        }
        if (options.careerToken !== undefined && parsed.record.careerToken !== options.careerToken) return
        const list = finalizeByToken.get(parsed.record.careerToken)
        if (list) list.push(parsed.record)
        else finalizeByToken.set(parsed.record.careerToken, [parsed.record])
    }

    function finish(): ReplayResult {
        const careers: ReplayCareer[] = []
        for (const [token, entries] of decisionsByToken) {
            if (token === UNKEYED) {
                careers.push(assembleUnkeyed(entries))
                continue
            }
            careers.push(assembleCareer(token, entries, statesByToken.get(token) ?? null, finalizeByToken.get(token) ?? null, careerStateSupplied))
        }
        // A career_state token with no decisions at all is still reported as a coverage anomaly on a
        // synthetic empty career, so stateWithoutTrace is never silently dropped.
        for (const [token, seqMap] of statesByToken) {
            if (decisionsByToken.has(token)) continue
            careers.push(assembleStateOnly(token, seqMap))
        }

        careers.sort((a, b) => (a.careerToken < b.careerToken ? -1 : a.careerToken > b.careerToken ? 1 : 0))

        // Duplicate (token, seq) among state records is a consistency failure surfaced on the owning career.
        for (const dup of duplicateStateKeys) {
            const career = careers.find((c) => c.careerToken === dup.token)
            if (career) career.anomalies.push({ type: "duplicateTokenSeq", careerToken: dup.token, seq: dup.seq, detail: `duplicate career_state (token, seq=${dup.seq})` })
        }

        const filtered = options.action !== undefined ? careers.filter((c) => c.decisions.some((d) => d.committedAction === options.action)) : careers

        const summary = summarize(filtered)
        const exitCode = computeExit(failures, filtered)
        return { schema: REPLAY_SCHEMA, version: REPLAY_SCHEMA_VERSION, careers: filtered, summary, failures, exitCode }
    }

    return {
        ingestDecisionLine,
        ingestCareerStateLine,
        ingestFinalizeLine,
        noteCareerStateFile: () => {
            careerStateSupplied = true
        },
        finish,
    }
}

const UNKEYED = "__unkeyed__"

function describeParseFailure(parsed: { kind: string; [key: string]: unknown }): string {
    switch (parsed.kind) {
        case "parseError":
            return `parse error: ${String(parsed.message)}`
        case "wrongType":
            return `wrong type: ${JSON.stringify(parsed.type)}`
        case "unsupportedVersion":
            return `unsupported version: ${JSON.stringify(parsed.version)}`
        case "malformedEnvelope":
            return `malformed envelope, missing: ${(parsed.missing as string[]).join(", ")}`
        default:
            return parsed.kind
    }
}

// ---- Career assembly ----

function assembleCareer(token: string, entries: DecisionEntry[], seqMap: Map<number, ReplayState> | null, finalizeList: FinalizeRecord[] | null, careerStateSupplied: boolean): ReplayCareer {
    const anomalies: ReplayAnomaly[] = []
    const cohort = mergeCohort(entries.map((e) => cohortFromDecision(e.record)))
    identityAnomalies(token, entries, anomalies)

    const sequenced = entries.filter((e) => e.decision.seq !== null)
    const unsequenced = entries.filter((e) => e.decision.seq === null)
    // A career is JOINED whenever it is in the sequenced era and a career_state file was supplied - even
    // if no state matched, so the traceWithoutState coverage gap surfaces instead of a silent downgrade.
    const capability: ReplayCapability = careerStateSupplied && sequenced.length > 0 ? "JOINED" : "TRACE_ONLY"
    const sm = seqMap ?? new Map<number, ReplayState>()

    if (capability === "JOINED" && unsequenced.length > 0) {
        anomalies.push({ type: "mixedCapabilityWithinCareer", careerToken: token, seq: null, detail: `${unsequenced.length} of ${entries.length} decisions carry no seq in a JOINED career` })
    }

    // Duplicate seq among the sequenced traces is a self-contradiction the writer cannot produce (seq is a
    // per-career monotonic counter); it is a hard consistency failure, mirroring the state-side duplicate.
    if (capability === "JOINED") {
        const seqCounts = new Map<number, number>()
        for (const e of sequenced) seqCounts.set(e.decision.seq as number, (seqCounts.get(e.decision.seq as number) ?? 0) + 1)
        for (const [seq, count] of [...seqCounts].sort((a, b) => a[0] - b[0])) {
            if (count > 1) anomalies.push({ type: "duplicateTokenSeq", careerToken: token, seq, detail: `duplicate decision_trace (token, seq=${seq}) appears ${count} times` })
        }
    }

    // Ordering: JOINED -> seq ascending (stable on line number for equal seq); TRACE_ONLY -> source order.
    const ordered =
        capability === "JOINED"
            ? [...entries].sort((a, b) => (a.decision.seq ?? Number.MAX_SAFE_INTEGER) - (b.decision.seq ?? Number.MAX_SAFE_INTEGER) || a.decision.lineNumber - b.decision.lineNumber)
            : entries

    let joinedCount = 0
    if (capability === "JOINED") {
        for (const e of ordered) {
            if (e.decision.seq !== null && sm.has(e.decision.seq)) {
                e.decision.hasStateJoin = true
                joinedCount++
            } else if (e.decision.seq !== null) {
                anomalies.push({ type: "traceWithoutState", careerToken: token, seq: e.decision.seq, detail: `sequenced decision seq=${e.decision.seq} has no career_state` })
            }
        }
    }

    let stateWithoutTraceCount = 0
    let seqGapCount = 0
    const transitions: BetweenDecisionObservedTransition[] = []
    if (capability === "JOINED") {
        const decisionSeqs = new Set(sequenced.map((e) => e.decision.seq as number))
        for (const [seq] of [...sm].sort((a, b) => a[0] - b[0])) {
            if (!decisionSeqs.has(seq)) {
                stateWithoutTraceCount++
                anomalies.push({ type: "stateWithoutTrace", careerToken: token, seq, detail: `career_state seq=${seq} has no sequenced decision` })
            }
        }
        const availableStates = [...sm.values()].sort((a, b) => a.seq - b.seq)
        const actionBySeq = new Map(sequenced.map((e) => [e.decision.seq as number, e.decision.committedAction]))
        for (let i = 0; i + 1 < availableStates.length; i++) {
            const fromState = availableStates[i]
            const toState = availableStates[i + 1]
            const gap = toState.seq - fromState.seq
            if (gap > 1) seqGapCount++
            transitions.push({
                label: "betweenDecisionObservedTransition",
                fromSeq: fromState.seq,
                toSeq: toState.seq,
                consecutive: gap === 1,
                spansGap: gap > 1,
                seqGap: gap,
                chosenActionAtFromSeq: actionBySeq.get(fromState.seq) ?? null,
                diffs: diffStates(fromState, toState),
            })
        }
        // Scenario-type change across consecutive available states is flagged, never casually diffed.
        for (let i = 0; i + 1 < availableStates.length; i++) {
            const a = availableStates[i].scenarioType
            const b = availableStates[i + 1].scenarioType
            if (a !== null && b !== null && a !== b) {
                anomalies.push({ type: "scenarioExtensionInconsistency", careerToken: token, seq: availableStates[i + 1].seq, detail: `scenario type changed ${a} -> ${b} within one career token` })
            }
        }
        // A seq gap in the sequenced-decision timeline is a capability fact, not licence to infer a record.
        const seqsSorted = [...decisionSeqs].sort((a, b) => a - b)
        for (let i = 0; i + 1 < seqsSorted.length; i++) {
            if (seqsSorted[i + 1] - seqsSorted[i] > 1) {
                anomalies.push({ type: "seqGap", careerToken: token, seq: seqsSorted[i], detail: `decision seq gap ${seqsSorted[i]} -> ${seqsSorted[i + 1]}` })
            }
        }
    }

    contestAnomalies(token, ordered, anomalies)

    return {
        careerToken: token,
        capability,
        cohort,
        decisionCount: entries.length,
        sequencedDecisionCount: sequenced.length,
        stateCount: sm.size,
        joinedCount,
        stateWithoutTraceCount,
        traceWithoutStateCount: anomalies.filter((a) => a.type === "traceWithoutState").length,
        seqGapCount,
        decisions: ordered.map((e) => e.decision),
        transitions,
        finalize: assembleFinalize(finalizeList),
        anomalies,
    }
}

function assembleUnkeyed(entries: DecisionEntry[]): ReplayCareer {
    const cohort = mergeCohort(entries.map((e) => cohortFromDecision(e.record)))
    const anomalies: ReplayAnomaly[] = []
    contestAnomalies(null, entries, anomalies)
    return {
        careerToken: UNKEYED,
        capability: "TRACE_ONLY",
        cohort,
        decisionCount: entries.length,
        sequencedDecisionCount: 0,
        stateCount: 0,
        joinedCount: 0,
        stateWithoutTraceCount: 0,
        traceWithoutStateCount: 0,
        seqGapCount: 0,
        decisions: entries.map((e) => e.decision),
        transitions: [],
        finalize: assembleFinalize(null),
        anomalies,
    }
}

function assembleStateOnly(token: string, seqMap: Map<number, ReplayState>): ReplayCareer {
    const anomalies: ReplayAnomaly[] = []
    for (const [seq] of [...seqMap].sort((a, b) => a[0] - b[0])) {
        anomalies.push({ type: "stateWithoutTrace", careerToken: token, seq, detail: `career_state seq=${seq} has no decision in this corpus` })
    }
    return {
        careerToken: token,
        capability: "TRACE_ONLY",
        cohort: { app: null, fp: null, scenario: null, trainee: null, preset: null },
        decisionCount: 0,
        sequencedDecisionCount: 0,
        stateCount: seqMap.size,
        joinedCount: 0,
        stateWithoutTraceCount: seqMap.size,
        traceWithoutStateCount: 0,
        seqGapCount: 0,
        decisions: [],
        transitions: [],
        finalize: assembleFinalize(null),
        anomalies,
    }
}

function assembleFinalize(list: FinalizeRecord[] | null): ReplayFinalize {
    if (!list || list.length === 0) {
        return { present: false, count: 0, finalizationDecision: null, sessionOutcome: null, verifiedRemainingSp: null, scanComplete: null, plannerComplete: null, confirmationComplete: null, policy: null, objective: null, finalizationReason: null }
    }
    // If more than one finalize joined a token, the last by encounter is exposed; count is preserved.
    const r = list[list.length - 1]
    return { present: true, count: list.length, finalizationDecision: r.finalizationDecision, sessionOutcome: r.sessionOutcome, verifiedRemainingSp: r.verifiedRemainingSp, scanComplete: r.scanComplete, plannerComplete: r.plannerComplete, confirmationComplete: r.confirmationComplete, policy: r.policy, objective: r.objective, finalizationReason: r.finalizationReason }
}

function mergeCohort(cohorts: ReplayCohort[]): ReplayCohort {
    const pick = (get: (c: ReplayCohort) => string | null): string | null => {
        for (const c of cohorts) {
            const v = get(c)
            if (v !== null) return v
        }
        return null
    }
    return { app: pick((c) => c.app), fp: pick((c) => c.fp), scenario: pick((c) => c.scenario), trainee: pick((c) => c.trainee), preset: pick((c) => c.preset) }
}

function identityAnomalies(token: string, entries: DecisionEntry[], out: ReplayAnomaly[]): void {
    for (const field of ["scenario", "trainee", "fp"] as const) {
        const values = new Set(entries.map((e) => asString(e.record[field])).filter((v): v is string => v !== null))
        if (values.size > 1) out.push({ type: "identityInconsistency", careerToken: token, seq: null, detail: `multiple ${field} values within one token: ${[...values].sort().join(", ")}` })
    }
}

function contestAnomalies(token: string | null, entries: DecisionEntry[], out: ReplayAnomaly[]): void {
    for (const e of entries) {
        const tc = e.decision.trainingContest
        if (!tc.present) continue
        if (tc.selected && tc.selected.scoreSource === "unavailable" && tc.selected.reason !== null) {
            out.push({ type: "unparseableSelectedTrainingScore", careerToken: token, seq: e.decision.seq, detail: `selected training ${tc.selected.id} score not parseable from reason` })
        }
        if (!tc.complete) {
            out.push({ type: "incompleteTrainingContest", careerToken: token, seq: e.decision.seq, detail: `training contest has ${tc.facilitiesPresent.length}/5 facilities` })
        }
    }
}

function summarize(careers: ReplayCareer[]): ReplaySummary {
    let decisionCount = 0
    let sequencedDecisionCount = 0
    let joinedCount = 0
    let withFinalize = 0
    let trainingContestCount = 0
    let scoreGapEligibleCount = 0
    let scoreGapSuppressedCount = 0
    let seqGapCount = 0
    const anomalyCountsByType: Record<string, number> = {}
    let traceOnly = 0
    let joined = 0

    for (const c of careers) {
        if (c.capability === "JOINED") joined++
        else traceOnly++
        decisionCount += c.decisionCount
        sequencedDecisionCount += c.sequencedDecisionCount
        joinedCount += c.joinedCount
        seqGapCount += c.seqGapCount
        if (c.finalize.present) withFinalize++
        for (const d of c.decisions) {
            if (d.trainingContest.present) {
                trainingContestCount++
                if (d.trainingContest.recordedScoreGap.eligible) scoreGapEligibleCount++
                else if (d.trainingContest.selected) scoreGapSuppressedCount++
            }
        }
        for (const a of c.anomalies) anomalyCountsByType[a.type] = (anomalyCountsByType[a.type] ?? 0) + 1
    }

    return {
        careerCount: careers.length,
        traceOnlyCount: traceOnly,
        joinedCount: joined,
        decisionCount,
        sequencedDecisionCount,
        stateCoverage: { joined: joinedCount, sequencedDecisions: sequencedDecisionCount },
        finalizeCoverage: { withFinalize, careers: careers.length },
        trainingContestCount,
        scoreGapEligibleCount,
        scoreGapSuppressedCount,
        seqGapCount,
        anomalyCountsByType,
    }
}

function computeExit(failures: ReplayResult["failures"], careers: ReplayCareer[]): number {
    let exit = EXIT_CLEAN
    if (failures.length > 0) exit = worstExit(exit, EXIT_PARSE_OR_SCHEMA)
    for (const c of careers) {
        for (const a of c.anomalies) {
            // A duplicate composite key is a hard consistency failure; every other anomaly is a warning.
            exit = worstExit(exit, a.type === "duplicateTokenSeq" ? EXIT_CONSISTENCY : EXIT_WARNINGS)
        }
    }
    return exit
}

// ---- Text report (constrained terminology; no causal language, no "regret") ----

/** Renders the deterministic human-readable report. Uses only observational/recorded terminology. */
export function renderReplayReport(result: ReplayResult): string {
    const lines: string[] = []
    const s = result.summary
    lines.push(`ReplayLab v${REPLAY_SCHEMA_VERSION} - factual replay (read-only). No policy re-execution, no counterfactuals.`)
    lines.push(`${s.careerCount} career(s): ${s.joinedCount} JOINED, ${s.traceOnlyCount} TRACE_ONLY. ${s.decisionCount} decision(s), ${s.sequencedDecisionCount} sequenced.`)
    lines.push(`state coverage: ${s.stateCoverage.joined}/${s.stateCoverage.sequencedDecisions} sequenced decisions joined a CareerState. finalize: ${s.finalizeCoverage.withFinalize}/${s.finalizeCoverage.careers} careers.`)
    lines.push(`training contests: ${s.trainingContestCount} (recordedScoreGap eligible ${s.scoreGapEligibleCount}, suppressed ${s.scoreGapSuppressedCount}). seq gaps: ${s.seqGapCount}.`)
    if (result.failures.length > 0) lines.push(`parse/schema failures: ${result.failures.length} (see below).`)
    const anomalyTypes = Object.keys(s.anomalyCountsByType).sort()
    if (anomalyTypes.length > 0) lines.push(`anomalies: ${anomalyTypes.map((t) => `${t}=${s.anomalyCountsByType[t]}`).join(", ")}.`)

    for (const c of result.careers) {
        lines.push("")
        lines.push(`## ${c.careerToken}  [${c.capability}]`)
        lines.push(`- cohort: app ${c.cohort.app ?? "?"} / fp ${c.cohort.fp ?? "?"} / ${c.cohort.scenario ?? "?"} / ${c.cohort.trainee ?? "?"}${c.cohort.preset ? ` / preset ${c.cohort.preset}` : ""}  (cohort label only; not a policy-code identity)`)
        lines.push(`- decisions ${c.decisionCount} (sequenced ${c.sequencedDecisionCount}), states ${c.stateCount}, joined ${c.joinedCount}`)
        if (c.capability === "JOINED") {
            lines.push(`- coverage: stateWithoutTrace ${c.stateWithoutTraceCount}, traceWithoutState ${c.traceWithoutStateCount}, seqGaps ${c.seqGapCount}`)
            lines.push(`- betweenDecisionObservedTransitions: ${c.transitions.length}`)
            for (const t of c.transitions) {
                const changed = t.diffs.map((d) => d.field).join(", ") || "no observed field change"
                lines.push(`    seq ${t.fromSeq} -> ${t.toSeq}${t.spansGap ? ` (gap ${t.seqGap})` : ""} after action ${t.chosenActionAtFromSeq ?? "?"}: ${changed}`)
            }
        }
        const gaps = c.decisions.filter((d) => d.trainingContest.recordedScoreGap.eligible)
        for (const d of gaps) {
            const g = d.trainingContest.recordedScoreGap
            lines.push(`- seq ${d.seq ?? "-"} recordedScoreGap: selected ${d.trainingContest.selected?.id} ${g.selectedScore} vs best alt ${g.bestAlternative?.id} ${g.bestAlternative?.score} = margin ${g.value} (recorded score margin; observational only, not a counterfactual)`)
        }
        if (c.finalize.present) {
            lines.push(`- finalize: ${c.finalize.finalizationDecision ?? "?"} / ${c.finalize.sessionOutcome ?? "?"} / remaining SP ${c.finalize.verifiedRemainingSp ?? "?"}`)
        } else {
            lines.push(`- finalize: absent (valid; a pre-resume segment legitimately has none)`)
        }
        if (c.anomalies.length > 0) {
            const byType = new Map<string, number>()
            for (const a of c.anomalies) byType.set(a.type, (byType.get(a.type) ?? 0) + 1)
            lines.push(`- anomalies: ${[...byType].map(([t, n]) => `${t}=${n}`).join(", ")}`)
        }
    }

    if (result.failures.length > 0) {
        lines.push("")
        lines.push("## parse/schema failures")
        for (const f of result.failures) lines.push(`- ${f.stream} line ${f.lineNumber}: ${f.detail}`)
    }
    return lines.join("\n")
}
