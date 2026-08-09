// Shadow Advisor S2 - deterministic text report. Factual vocabulary only: agreement / disagreement /
// coverage / available / comparable / non-comparable. It never prints accuracy, performance, better, worse,
// mistake, would-have, or expected. Ratios always show numerator/denominator so no bare percentage is
// ambiguous. Output is a pure function of the evaluation result (no clock, no environment).

import type { ShadowEvaluationResult, RatioMetric, ComparisonCounts, StatusCounts, AdvisorActionCounts, EvaluationRow } from "./evaluate.ts"

/** Formats a ratio as `value (num/den)`, or `n/a (0 comparable)` when the denominator is 0. */
function fmtRatio(label: string, r: RatioMetric): string {
    const v = r.value === null ? "n/a" : r.value.toFixed(4)
    return `${label}: ${v} (${r.numerator}/${r.denominator})`
}

function fmtStatus(c: StatusCounts): string {
    return `available ${c.recommendationAvailable}, insufficient ${c.insufficientEvidence}, notApplicable ${c.notApplicable}, unsupported ${c.unsupportedDecisionContext}`
}
function fmtActions(c: AdvisorActionCounts): string {
    return `TRAIN ${c.TRAIN}, REST ${c.REST}, RECOVER_MOOD ${c.RECOVER_MOOD}`
}
function fmtComparison(c: ComparisonCounts): string {
    return `sameAction ${c.sameAction}, sameActionDifferentTraining ${c.sameActionDifferentTraining}, differentAction ${c.differentAction}, advisorUnavailable ${c.advisorUnavailable}, comparisonNotApplicable ${c.comparisonNotApplicable}`
}
function fmtNum(n: number | null): string {
    return n === null ? "n/a" : Number.isInteger(n) ? String(n) : n.toFixed(2)
}

/** Renders the deterministic default text report. `details` adds one sorted line per evaluated context. */
export function renderEvaluationReport(result: ShadowEvaluationResult, details = false): string {
    const lines: string[] = []
    const s = result.summary
    const src = result.source

    lines.push(`Shadow Advisor S2 evaluation v${result.evaluationVersion} - offline policy comparison (read-only). No outcome scoring, no counterfactuals.`)
    lines.push(`advisor v${result.advisorVersion} / policy ${result.policyId}${src.careerTokenFilter ? ` / careerToken ${src.careerTokenFilter}` : ""}`)

    lines.push("")
    lines.push("## coverage")
    lines.push(`source: ${src.decisionRecordCount} decision record(s), ${src.stateRecordCount} career_state record(s).`)
    lines.push(`careers: ${src.replayCareerCount} replay, ${src.joinedCareerCount} JOINED, ${src.evaluatedCareerCount} evaluated. contexts built: ${src.contextsBuilt}.`)
    lines.push(`skipped: ${src.skippedUnsequencedDecisionCount} unsequenced decision(s) (not joined by turn), ${src.duplicateSkippedContextCount} duplicate-seq context(s).`)

    lines.push("")
    lines.push("## recommendation")
    lines.push(`status: ${fmtStatus(s.statusCounts)}.`)
    lines.push(`advisor action (available only): ${fmtActions(s.advisorActionCounts)}.`)

    lines.push("")
    lines.push("## comparison")
    lines.push(`${fmtComparison(s.comparisonCounts)}.`)
    lines.push(`comparable = sameAction + sameActionDifferentTraining + differentAction = ${s.comparableCount} (excludes advisorUnavailable + comparisonNotApplicable).`)
    lines.push(`disagreement: candidate (different training) ${s.candidateDisagreementCount}, action-family ${s.actionFamilyDisagreementCount}, total ${s.totalDisagreementCount}.`)

    lines.push("")
    lines.push("## rates")
    lines.push(fmtRatio("recommendationAvailability (available/contexts)", s.recommendationAvailabilityRate))
    lines.push(fmtRatio("comparisonCoverage (comparable/contexts)", s.comparisonCoverageRate))
    lines.push(fmtRatio("exactAgreement (sameAction/comparable)", s.exactAgreementRate))
    lines.push(fmtRatio("actionFamilyAgreement (sameAction+sameActionDifferentTraining/comparable)", s.actionFamilyAgreementRate))
    lines.push(fmtRatio("disagreement (candidate+actionFamily/comparable)", s.disagreementRate))

    lines.push("")
    lines.push("## scenario segments")
    for (const seg of result.scenarioSegments) {
        lines.push(`- ${seg.scenarioType}: contexts ${seg.contextCount}, comparable ${seg.comparableCount}, ${fmtRatio("exact", seg.exactAgreementRate)}, ${fmtRatio("actionFamily", seg.actionFamilyAgreementRate)}, ${fmtRatio("disagreement", seg.disagreementRate)}`)
        lines.push(`    status [${fmtStatus(seg.statusCounts)}] | actions [${fmtActions(seg.advisorActionCounts)}]`)
    }

    lines.push("")
    lines.push("## trainingSource segments (bot TRAIN decisions)")
    for (const seg of result.trainingSourceSegments) {
        lines.push(`- ${seg.trainingSource}: botTrain ${seg.botTrainDecisionCount}, comparable ${seg.comparableCount}, sameAction ${seg.sameAction}, sameActionDifferentTraining ${seg.sameActionDifferentTraining}, differentAction ${seg.differentAction}, ${fmtRatio("exact", seg.exactAgreementRate)}, ${fmtRatio("disagreement", seg.disagreementRate)}`)
    }

    lines.push("")
    lines.push("## advisor score-margin distribution (heuristic margin, not confidence)")
    const m = result.marginStats
    lines.push(`count ${m.count}, min ${fmtNum(m.min)}, p25 ${fmtNum(m.p25)}, median ${fmtNum(m.median)}, mean ${fmtNum(m.mean)}, p75 ${fmtNum(m.p75)}, max ${fmtNum(m.max)}.`)

    lines.push("")
    lines.push("## reason codes")
    const reasonKeys = Object.keys(result.reasonCodeCounts)
    lines.push(reasonKeys.length > 0 ? reasonKeys.map((k) => `${k}=${result.reasonCodeCounts[k]}`).join(", ") : "(none)")

    lines.push("")
    lines.push("## non-comparable by committed action")
    const ncKeys = Object.keys(result.comparisonNotApplicableByCommittedAction)
    lines.push(ncKeys.length > 0 ? ncKeys.map((k) => `${k}=${result.comparisonNotApplicableByCommittedAction[k]}`).join(", ") : "(none)")

    lines.push("")
    lines.push("## comparable action matrix (committed -> advisor)")
    const committedKeys = Object.keys(result.actionMatrix)
    if (committedKeys.length === 0) lines.push("(no comparable rows)")
    for (const committed of committedKeys) {
        const inner = result.actionMatrix[committed]
        lines.push(`- ${committed}: ${Object.keys(inner).map((a) => `${a}=${inner[a]}`).join(", ")}`)
    }

    if (result.issues.length > 0) {
        lines.push("")
        lines.push(`## issues (${result.issues.length}, non-fatal)`)
        for (const issue of result.issues) lines.push(`- ${issue.type}${issue.careerToken ? ` [${issue.careerToken}]` : ""}${issue.seq !== null ? ` seq ${issue.seq}` : ""}: ${issue.detail}`)
    }

    if (details) {
        lines.push("")
        lines.push("## per-turn (sorted by careerToken, seq)")
        for (const row of result.rows) lines.push(detailLine(row))
    }

    return lines.join("\n")
}

function detailLine(row: EvaluationRow): string {
    const adv = row.advisor.action ? `${row.advisor.action}${row.advisor.trainingType ? `:${row.advisor.trainingType}` : ""}` : row.recommendationStatus
    const bot = row.committed.action ? `${row.committed.action}${row.committed.trainingType ? `:${row.committed.trainingType}` : ""}` : "?"
    const margin = row.advisor.scoreMargin === null ? "" : ` margin ${row.advisor.scoreMargin.toFixed(2)}`
    const src = row.committed.trainingSource ? ` src ${row.committed.trainingSource}` : ""
    return `- ${row.careerToken} seq ${row.seq} turn ${row.turn ?? "?"} [${row.scenarioType ?? "UNAVAILABLE"}] bot ${bot}${src} | advisor ${adv}${margin} | ${row.comparison} | reasons ${row.reasonCodes.join("/") || "-"}`
}
