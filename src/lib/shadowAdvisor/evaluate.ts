// Shadow Advisor S2 - offline corpus evaluation over the landed S1 policy. Pure, deterministic,
// evaluation/reporting ONLY. It never changes S1 policy, never tunes weights, never uses a career outcome
// (final rank/score/transition/enteredRace) to judge a recommendation, and never claims accuracy or that
// the advisor "would have" done better. It answers factual corpus questions: how often was a recommendation
// available, how often was the bot/advisor comparison meaningful, and among comparable turns how often they
// agreed, disagreed on the training facility, or disagreed on the action family.
//
// Assembly reuses ReplayLab as the authority for career structure, the careerToken+seq join, JOINED-only
// state, and duplicate/anomaly detection. S2 additionally groups the RAW decision/state records by
// (careerToken, seq) - never by turn - to feed S1's buildAdvisorContexts bridge, because ReplayLab's
// projected types intentionally omit raw candidate gains/failChance and per-seq CareerState. A duplicate
// (careerToken, seq) is marked unusable (issue) rather than arbitrarily resolved.

import { buildAdvisorContexts } from "./context.ts"
import type { AdvisorRawTurn } from "./context.ts"
import { recommend, DEFAULT_SHADOW_POLICY } from "./policy.ts"
import { compareToCommitted } from "./compare.ts"
import type { ShadowRecommendation, ComparisonResult, ComparisonState, AdvisorAction, RecommendationStatus } from "./types.ts"
import { createReplayLab } from "../replayLab.ts"
import type { ReplayCareer, ReplayDecision } from "../replayLab.ts"

/** S2 output schema version. Additive fields keep this at 1. */
export const EVALUATION_VERSION = "1"

/** Deterministic bucket key for an absent categorical value (scenario / trainingSource). */
const UNAVAILABLE = "UNAVAILABLE"

const RECOMMENDATION_STATUSES: readonly RecommendationStatus[] = ["recommendationAvailable", "insufficientEvidence", "notApplicable", "unsupportedDecisionContext"]
const ADVISOR_ACTIONS: readonly AdvisorAction[] = ["TRAIN", "REST", "RECOVER_MOOD"]
const COMPARISON_STATES: readonly ComparisonState[] = ["sameAction", "sameActionDifferentTraining", "differentAction", "advisorUnavailable", "comparisonNotApplicable"]

/** A factual ratio: value is null (never 0) when the denominator is 0. */
export interface RatioMetric {
    numerator: number
    denominator: number
    value: number | null
}

export type StatusCounts = Record<RecommendationStatus, number>
export type AdvisorActionCounts = Record<AdvisorAction, number>
export type ComparisonCounts = Record<ComparisonState, number>

export interface MarginStats {
    count: number
    min: number | null
    max: number | null
    mean: number | null
    median: number | null
    p25: number | null
    p75: number | null
}

export interface EvaluationSummary {
    statusCounts: StatusCounts
    advisorActionCounts: AdvisorActionCounts
    comparisonCounts: ComparisonCounts
    comparableCount: number
    candidateDisagreementCount: number
    actionFamilyDisagreementCount: number
    totalDisagreementCount: number
    recommendationAvailabilityRate: RatioMetric
    comparisonCoverageRate: RatioMetric
    exactAgreementRate: RatioMetric
    actionFamilyAgreementRate: RatioMetric
    disagreementRate: RatioMetric
}

export interface ScenarioSegment {
    scenarioType: string
    contextCount: number
    statusCounts: StatusCounts
    advisorActionCounts: AdvisorActionCounts
    comparisonCounts: ComparisonCounts
    comparableCount: number
    exactAgreementRate: RatioMetric
    actionFamilyAgreementRate: RatioMetric
    disagreementRate: RatioMetric
}

export interface TrainingSourceSegment {
    trainingSource: string
    botTrainDecisionCount: number
    comparisonCounts: ComparisonCounts
    comparableCount: number
    sameAction: number
    sameActionDifferentTraining: number
    differentAction: number
    exactAgreementRate: RatioMetric
    disagreementRate: RatioMetric
}

export interface EvaluationRow {
    careerToken: string
    seq: number
    turn: number | null
    scenarioType: string | null
    committed: { action: string | null; trainingType: string | null; trainingSource: string | null }
    advisor: { action: AdvisorAction | null; trainingType: string | null; scoreMargin: number | null }
    comparison: ComparisonState
    reasonCodes: string[]
    recommendationStatus: RecommendationStatus
}

export type EvaluationIssueType =
    | "duplicateDecisionSeq"
    | "duplicateStateSeq"
    | "replayCareerMissingRawDecision"
    | "replayCareerMissingRawState"
    | "replaySchemaFailure"

export interface EvaluationIssue {
    type: EvaluationIssueType
    careerToken: string | null
    seq: number | null
    detail: string
}

export interface EvaluationSource {
    decisionRecordCount: number
    stateRecordCount: number
    careerTokenFilter: string | null
    replayCareerCount: number
    joinedCareerCount: number
    evaluatedCareerCount: number
    skippedUnsequencedDecisionCount: number
    duplicateSkippedContextCount: number
    contextsBuilt: number
}

export interface ShadowEvaluationResult {
    evaluationVersion: typeof EVALUATION_VERSION
    advisorVersion: string
    policyId: string
    source: EvaluationSource
    summary: EvaluationSummary
    scenarioSegments: ScenarioSegment[]
    trainingSourceSegments: TrainingSourceSegment[]
    actionMatrix: Record<string, Record<string, number>>
    comparisonNotApplicableByCommittedAction: Record<string, number>
    marginStats: MarginStats
    reasonCodeCounts: Record<string, number>
    limitationCounts: Record<string, number>
    rows: EvaluationRow[]
    issues: EvaluationIssue[]
    /** 0 clean, 1 non-fatal corpus issues present. Parse/IO fatals are the CLI's concern, not this value. */
    exitCode: number
}

export interface EvaluateOptions {
    careerToken?: string
}

// ---- loose JSON helpers (read-only) ----

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}
function asString(value: unknown): string | null {
    return typeof value === "string" && value.length > 0 ? value : null
}
function asInt(value: unknown): number | null {
    return typeof value === "number" && Number.isInteger(value) ? value : null
}

function emptyStatusCounts(): StatusCounts {
    return { recommendationAvailable: 0, insufficientEvidence: 0, notApplicable: 0, unsupportedDecisionContext: 0 }
}
function emptyAdvisorActionCounts(): AdvisorActionCounts {
    return { TRAIN: 0, REST: 0, RECOVER_MOOD: 0 }
}
function emptyComparisonCounts(): ComparisonCounts {
    return { sameAction: 0, sameActionDifferentTraining: 0, differentAction: 0, advisorUnavailable: 0, comparisonNotApplicable: 0 }
}

function ratio(numerator: number, denominator: number): RatioMetric {
    return { numerator, denominator, value: denominator === 0 ? null : numerator / denominator }
}

function comparableOf(c: ComparisonCounts): number {
    return c.sameAction + c.sameActionDifferentTraining + c.differentAction
}
function exactAgreement(c: ComparisonCounts): RatioMetric {
    return ratio(c.sameAction, comparableOf(c))
}
function actionFamilyAgreement(c: ComparisonCounts): RatioMetric {
    return ratio(c.sameAction + c.sameActionDifferentTraining, comparableOf(c))
}
function disagreement(c: ComparisonCounts): RatioMetric {
    return ratio(c.sameActionDifferentTraining + c.differentAction, comparableOf(c))
}

/** Percentile by linear interpolation on rank; caller passes an ascending-sorted array. */
function percentile(sorted: number[], p: number): number {
    if (sorted.length === 1) return sorted[0]
    const idx = p * (sorted.length - 1)
    const lo = Math.floor(idx)
    const hi = Math.ceil(idx)
    if (lo === hi) return sorted[lo]
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo)
}

function marginStats(values: number[]): MarginStats {
    if (values.length === 0) return { count: 0, min: null, max: null, mean: null, median: null, p25: null, p75: null }
    const sorted = [...values].sort((a, b) => a - b)
    const sum = sorted.reduce((s, v) => s + v, 0)
    return {
        count: sorted.length,
        min: sorted[0],
        max: sorted[sorted.length - 1],
        mean: sum / sorted.length,
        median: percentile(sorted, 0.5),
        p25: percentile(sorted, 0.25),
        p75: percentile(sorted, 0.75),
    }
}

function sortedNumberRecord(map: Map<string, number>): Record<string, number> {
    const out: Record<string, number> = {}
    for (const key of [...map.keys()].sort()) out[key] = map.get(key) as number
    return out
}

// ---- per-scenario / per-trainingSource accumulators ----

interface ScenarioAcc {
    contextCount: number
    statusCounts: StatusCounts
    advisorActionCounts: AdvisorActionCounts
    comparisonCounts: ComparisonCounts
}
interface TrainingSourceAcc {
    botTrainDecisionCount: number
    comparisonCounts: ComparisonCounts
}

function newScenarioAcc(): ScenarioAcc {
    return { contextCount: 0, statusCounts: emptyStatusCounts(), advisorActionCounts: emptyAdvisorActionCounts(), comparisonCounts: emptyComparisonCounts() }
}

// ---- raw record grouping (careerToken -> seq -> list) ----

function groupByTokenSeq(records: readonly unknown[], tokenOf: (r: Record<string, unknown>) => string | null, filter: string | null): Map<string, Map<number, Record<string, unknown>[]>> {
    const out = new Map<string, Map<number, Record<string, unknown>[]>>()
    for (const rec of records) {
        if (!isObject(rec)) continue
        const token = tokenOf(rec)
        const seq = asInt(rec.seq)
        if (token === null || seq === null) continue
        if (filter !== null && token !== filter) continue
        let seqMap = out.get(token)
        if (!seqMap) {
            seqMap = new Map()
            out.set(token, seqMap)
        }
        const list = seqMap.get(seq)
        if (list) list.push(rec)
        else seqMap.set(seq, [rec])
    }
    return out
}

/**
 * Evaluates a decision + career_state corpus against the landed default S1 policy. Records are the parsed
 * JSONL objects (the CLI owns file IO and JSON-syntax fatals). Deterministic: identical records in any order
 * produce identical output.
 */
export function evaluateCorpus(decisionRecords: readonly unknown[], stateRecords: readonly unknown[], options: EvaluateOptions = {}): ShadowEvaluationResult {
    const filter = options.careerToken ?? null

    // ReplayLab is the assembly authority. Feed the same records (re-serialized) so the careerToken+seq join,
    // JOINED classification, and duplicate/anomaly detection all come from ReplayLab, not a forked parser.
    const lab = createReplayLab(filter !== null ? { careerToken: filter } : {})
    stateRecords.forEach((r, i) => lab.ingestCareerStateLine(JSON.stringify(r), i + 1))
    lab.noteCareerStateFile()
    decisionRecords.forEach((r, i) => lab.ingestDecisionLine(JSON.stringify(r), i + 1))
    const replay = lab.finish()

    // Raw grouping for the S1 bridge (never by turn).
    const decByToken = groupByTokenSeq(decisionRecords, (r) => asString(r.careerToken), filter)
    const stateByToken = groupByTokenSeq(stateRecords, (r) => (isObject(r.identity) ? asString(r.identity.careerToken) : null), filter)

    const issues: EvaluationIssue[] = []
    const rows: EvaluationRow[] = []

    // Aggregators.
    const statusCounts = emptyStatusCounts()
    const advisorActionCounts = emptyAdvisorActionCounts()
    const comparisonCounts = emptyComparisonCounts()
    const scenarioAccs = new Map<string, ScenarioAcc>()
    const trainingSourceAccs = new Map<string, TrainingSourceAcc>()
    const matrix = new Map<string, Map<string, number>>()
    const nonComparableByCommitted = new Map<string, number>()
    const reasonCodeCounts = new Map<string, number>()
    const limitationCounts = new Map<string, number>()
    const margins: number[] = []

    let skippedUnsequencedDecisionCount = 0
    for (const rec of decisionRecords) {
        if (isObject(rec) && asString(rec.careerToken) !== null && asInt(rec.seq) === null) skippedUnsequencedDecisionCount++
    }
    for (const f of replay.failures) {
        issues.push({ type: "replaySchemaFailure", careerToken: null, seq: null, detail: `${f.stream} line ${f.lineNumber}: ${f.detail}` })
    }

    let joinedCareerCount = 0
    let evaluatedCareerCount = 0
    let duplicateSkippedContextCount = 0

    for (const career of replay.careers) {
        if (career.capability !== "JOINED") continue
        joinedCareerCount++
        const token = career.careerToken
        const decSeqMap = decByToken.get(token) ?? new Map<number, Record<string, unknown>[]>()
        const stSeqMap = stateByToken.get(token) ?? new Map<number, Record<string, unknown>[]>()

        // Poisoned seqs: a duplicate (careerToken, seq) on either stream is not arbitrarily resolved.
        const poisoned = new Set<number>()
        for (const [seq, list] of decSeqMap) if (list.length > 1) poisoned.add(seq)
        for (const [seq, list] of stSeqMap) if (list.length > 1) poisoned.add(seq)

        const rawBySeq = new Map<number, AdvisorRawTurn>()
        for (const d of career.decisions) {
            if (d.seq === null || poisoned.has(d.seq)) continue
            const decList = decSeqMap.get(d.seq)
            const stList = stSeqMap.get(d.seq)
            const candidates = decList && decList.length === 1 ? decList[0].candidates : undefined
            const careerState = stList && stList.length === 1 ? stList[0] : null
            if (candidates === undefined) issues.push({ type: "replayCareerMissingRawDecision", careerToken: token, seq: d.seq, detail: `no raw decision candidates for seq ${d.seq}` })
            if (careerState === null) issues.push({ type: "replayCareerMissingRawState", careerToken: token, seq: d.seq, detail: `no raw career_state for seq ${d.seq}` })
            rawBySeq.set(d.seq, { candidates, careerState })
        }

        // Deterministic duplicate issues for this career (sorted by seq).
        for (const seq of [...poisoned].sort((a, b) => a - b)) {
            if ((decSeqMap.get(seq)?.length ?? 0) > 1) issues.push({ type: "duplicateDecisionSeq", careerToken: token, seq, detail: `${decSeqMap.get(seq)?.length} decision rows share (token, seq=${seq})` })
            if ((stSeqMap.get(seq)?.length ?? 0) > 1) issues.push({ type: "duplicateStateSeq", careerToken: token, seq, detail: `${stSeqMap.get(seq)?.length} career_state rows share (token, seq=${seq})` })
        }

        const decBySeq = new Map<number, ReplayDecision>(career.decisions.filter((d) => d.seq !== null).map((d) => [d.seq as number, d]))
        const contexts = buildAdvisorContexts(career, rawBySeq).filter((c) => !poisoned.has(c.seq))
        // A poisoned seq that ReplayLab still listed as a decision contributes a skipped context.
        for (const d of career.decisions) if (d.seq !== null && poisoned.has(d.seq)) duplicateSkippedContextCount++

        if (contexts.length > 0) evaluatedCareerCount++

        for (const context of contexts) {
            const recommendation = recommend(context)
            const decision = decBySeq.get(context.seq)
            // A poisoned seq is already excluded; every surviving context has a unique ReplayDecision.
            const comparison: ComparisonResult = decision ? compareToCommitted(recommendation, decision) : { state: "advisorUnavailable", advisorAction: null, advisorTraining: null, committedAction: null, committedTraining: null, trainingSource: null }
            tallyRow(context.scenarioType, recommendation, comparison, {
                statusCounts,
                advisorActionCounts,
                comparisonCounts,
                scenarioAccs,
                trainingSourceAccs,
                matrix,
                nonComparableByCommitted,
                reasonCodeCounts,
                limitationCounts,
                margins,
            })
            rows.push({
                careerToken: token,
                seq: context.seq,
                turn: context.turn,
                scenarioType: context.scenarioType,
                committed: { action: comparison.committedAction, trainingType: comparison.committedTraining, trainingSource: comparison.trainingSource },
                advisor: { action: recommendation.recommended?.action ?? null, trainingType: recommendation.recommended?.trainingType ?? null, scoreMargin: recommendation.scoreMargin?.value ?? null },
                comparison: comparison.state,
                reasonCodes: recommendation.reasons.map((r) => r.code),
                recommendationStatus: recommendation.status,
            })
        }
    }

    rows.sort((a, b) => (a.careerToken < b.careerToken ? -1 : a.careerToken > b.careerToken ? 1 : a.seq - b.seq))
    issues.sort((a, b) => a.type.localeCompare(b.type) || (a.careerToken ?? "").localeCompare(b.careerToken ?? "") || (a.seq ?? -1) - (b.seq ?? -1) || a.detail.localeCompare(b.detail))

    const contextsBuilt = rows.length
    const comparableCount = comparableOf(comparisonCounts)
    const summary: EvaluationSummary = {
        statusCounts,
        advisorActionCounts,
        comparisonCounts,
        comparableCount,
        candidateDisagreementCount: comparisonCounts.sameActionDifferentTraining,
        actionFamilyDisagreementCount: comparisonCounts.differentAction,
        totalDisagreementCount: comparisonCounts.sameActionDifferentTraining + comparisonCounts.differentAction,
        recommendationAvailabilityRate: ratio(statusCounts.recommendationAvailable, contextsBuilt),
        comparisonCoverageRate: ratio(comparableCount, contextsBuilt),
        exactAgreementRate: ratio(comparisonCounts.sameAction, comparableCount),
        actionFamilyAgreementRate: ratio(comparisonCounts.sameAction + comparisonCounts.sameActionDifferentTraining, comparableCount),
        disagreementRate: ratio(comparisonCounts.sameActionDifferentTraining + comparisonCounts.differentAction, comparableCount),
    }

    return {
        evaluationVersion: EVALUATION_VERSION,
        advisorVersion: DEFAULT_SHADOW_POLICY.advisorVersion,
        policyId: DEFAULT_SHADOW_POLICY.policyId,
        source: {
            decisionRecordCount: decisionRecords.length,
            stateRecordCount: stateRecords.length,
            careerTokenFilter: filter,
            replayCareerCount: replay.careers.length,
            joinedCareerCount,
            evaluatedCareerCount,
            skippedUnsequencedDecisionCount,
            duplicateSkippedContextCount,
            contextsBuilt,
        },
        summary,
        scenarioSegments: buildScenarioSegments(scenarioAccs),
        trainingSourceSegments: buildTrainingSourceSegments(trainingSourceAccs),
        actionMatrix: buildMatrix(matrix),
        comparisonNotApplicableByCommittedAction: sortedNumberRecord(nonComparableByCommitted),
        marginStats: marginStats(margins),
        reasonCodeCounts: sortedNumberRecord(reasonCodeCounts),
        limitationCounts: sortedNumberRecord(limitationCounts),
        rows,
        issues,
        exitCode: issues.length > 0 ? 1 : 0,
    }
}

interface TallyBag {
    statusCounts: StatusCounts
    advisorActionCounts: AdvisorActionCounts
    comparisonCounts: ComparisonCounts
    scenarioAccs: Map<string, ScenarioAcc>
    trainingSourceAccs: Map<string, TrainingSourceAcc>
    matrix: Map<string, Map<string, number>>
    nonComparableByCommitted: Map<string, number>
    reasonCodeCounts: Map<string, number>
    limitationCounts: Map<string, number>
    margins: number[]
}

function bump(map: Map<string, number>, key: string): void {
    map.set(key, (map.get(key) ?? 0) + 1)
}

function tallyRow(scenarioType: string | null, rec: ShadowRecommendation, cmp: ComparisonResult, bag: TallyBag): void {
    bag.statusCounts[rec.status]++
    if (rec.status === "recommendationAvailable" && rec.recommended) bag.advisorActionCounts[rec.recommended.action]++
    bag.comparisonCounts[cmp.state]++

    const scenarioKey = scenarioType ?? UNAVAILABLE
    let sacc = bag.scenarioAccs.get(scenarioKey)
    if (!sacc) {
        sacc = newScenarioAcc()
        bag.scenarioAccs.set(scenarioKey, sacc)
    }
    sacc.contextCount++
    sacc.statusCounts[rec.status]++
    if (rec.status === "recommendationAvailable" && rec.recommended) sacc.advisorActionCounts[rec.recommended.action]++
    sacc.comparisonCounts[cmp.state]++

    // trainingSource segmentation covers bot TRAIN decisions only (trainingSource is bot metadata).
    if (cmp.committedAction === "TRAIN") {
        const tsKey = cmp.trainingSource ?? UNAVAILABLE
        let tacc = bag.trainingSourceAccs.get(tsKey)
        if (!tacc) {
            tacc = { botTrainDecisionCount: 0, comparisonCounts: emptyComparisonCounts() }
            bag.trainingSourceAccs.set(tsKey, tacc)
        }
        tacc.botTrainDecisionCount++
        tacc.comparisonCounts[cmp.state]++
    }

    // Comparable confusion matrix (committed family -> advisor family) over comparable rows only.
    if (cmp.state === "sameAction" || cmp.state === "sameActionDifferentTraining" || cmp.state === "differentAction") {
        const committed = cmp.committedAction as string
        const advisor = cmp.advisorAction as string
        let inner = bag.matrix.get(committed)
        if (!inner) {
            inner = new Map()
            bag.matrix.set(committed, inner)
        }
        bump(inner, advisor)
    }
    if (cmp.state === "comparisonNotApplicable") bump(bag.nonComparableByCommitted, cmp.committedAction ?? UNAVAILABLE)

    for (const r of rec.reasons) bump(bag.reasonCodeCounts, r.code)
    for (const l of rec.limitations) bump(bag.limitationCounts, l)
    if (rec.scoreMargin) bag.margins.push(rec.scoreMargin.value)
}

function buildScenarioSegments(accs: Map<string, ScenarioAcc>): ScenarioSegment[] {
    return [...accs.keys()].sort().map((scenarioType) => {
        const a = accs.get(scenarioType) as ScenarioAcc
        return {
            scenarioType,
            contextCount: a.contextCount,
            statusCounts: a.statusCounts,
            advisorActionCounts: a.advisorActionCounts,
            comparisonCounts: a.comparisonCounts,
            comparableCount: comparableOf(a.comparisonCounts),
            exactAgreementRate: exactAgreement(a.comparisonCounts),
            actionFamilyAgreementRate: actionFamilyAgreement(a.comparisonCounts),
            disagreementRate: disagreement(a.comparisonCounts),
        }
    })
}

function buildTrainingSourceSegments(accs: Map<string, TrainingSourceAcc>): TrainingSourceSegment[] {
    return [...accs.keys()].sort().map((trainingSource) => {
        const a = accs.get(trainingSource) as TrainingSourceAcc
        return {
            trainingSource,
            botTrainDecisionCount: a.botTrainDecisionCount,
            comparisonCounts: a.comparisonCounts,
            comparableCount: comparableOf(a.comparisonCounts),
            sameAction: a.comparisonCounts.sameAction,
            sameActionDifferentTraining: a.comparisonCounts.sameActionDifferentTraining,
            differentAction: a.comparisonCounts.differentAction,
            exactAgreementRate: exactAgreement(a.comparisonCounts),
            disagreementRate: disagreement(a.comparisonCounts),
        }
    })
}

function buildMatrix(matrix: Map<string, Map<string, number>>): Record<string, Record<string, number>> {
    const out: Record<string, Record<string, number>> = {}
    for (const committed of [...matrix.keys()].sort()) out[committed] = sortedNumberRecord(matrix.get(committed) as Map<string, number>)
    return out
}

/** Recursively key-sorted JSON for byte-stable output regardless of input record order. */
export function stableStringify(value: unknown): string {
    return JSON.stringify(sortValue(value))
}
function sortValue(value: unknown): unknown {
    if (Array.isArray(value)) return value.map(sortValue)
    if (value !== null && typeof value === "object") {
        const out: Record<string, unknown> = {}
        for (const key of Object.keys(value as Record<string, unknown>).sort()) out[key] = sortValue((value as Record<string, unknown>)[key])
        return out
    }
    return value
}

// Reference the fixed-order constants so an accidental future removal is a compile break, and to make the
// intended canonical orderings explicit for readers.
export const S2_STATUS_ORDER = RECOMMENDATION_STATUSES
export const S2_ACTION_ORDER = ADVISOR_ACTIONS
export const S2_COMPARISON_ORDER = COMPARISON_STATES
