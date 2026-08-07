/**
 * DecisionTrace v1 corpus analysis.
 *
 * Pure, streaming-friendly parsing and consistency checking for the per-turn `decision_trace`
 * records written on-device by `DecisionTrace.kt` / `OutcomeCorpus.append(.., DECISIONS_PATH)`,
 * consumed by `scripts/analyze-decisions.mjs` (CLI) and the Jest suite.
 *
 * The reader is read-only: it never rewrites, normalizes or truncates its input. It recognizes
 * exactly `type == "decision_trace"` at `v == 1`; anything else is reported, never silently
 * reinterpreted. Its job is to prove or disprove that a pulled corpus is internally consistent -
 * that the selection each record committed to is the one its own candidate list marks selected,
 * that turns are not double-emitted, and that a decision trace joins the `career_finalize` row on
 * `careerToken` - not to second-guess the bot's gameplay.
 *
 * Honesty rules mirror the writer: an absent optional field means "not known at decision time",
 * so a missing turn number, a missing identity, or an empty selection is REPORTED, never inferred
 * away. Only violations the writer cannot legitimately produce (a committed action absent from its
 * own candidate list, more than one selected candidate of a kind) are treated as hard consistency
 * failures.
 *
 * The analyzer holds only per-career running aggregates, never the whole corpus, so a 32 MB
 * decisions file (the on-device cap) analyzes in bounded memory.
 */

/** Record type discriminator, matching `DecisionTrace.SCHEMA`. */
export const DECISION_SCHEMA = "decision_trace"

/** The only schema version this reader understands, matching `DecisionTrace.SCHEMA_VERSION`. */
export const DECISION_SCHEMA_VERSION = 1

// Exit codes. Higher = more serious; the CLI reports the worst category found (see `worstExit`).
/** No parse errors, no schema failures, no consistency failures, no warnings. */
export const EXIT_CLEAN = 0
/** Analysis succeeded but surfaced non-blocking anomalies (duplicate/non-monotonic turns, empty selections, missing identity, an unclean join). */
export const EXIT_WARNINGS = 1
/** At least one line could not be parsed, or carried the wrong type or an unsupported version. */
export const EXIT_PARSE_OR_SCHEMA = 2
/** At least one record contradicts itself (committed selection absent from its candidates, or more than one selected candidate of a kind). */
export const EXIT_CONSISTENCY = 3

/** Returns the more serious of two exit codes. */
export function worstExit(a: number, b: number): number {
    return Math.max(a, b)
}

/** A loosely-typed decision_trace record. Only the fields the analyzer reads are named; unknown fields are ignored, never rejected. */
export interface DecisionRecord {
    type?: unknown
    v?: unknown
    ts?: unknown
    app?: unknown
    fp?: unknown
    scenario?: unknown
    trainee?: unknown
    preset?: unknown
    careerToken?: unknown
    queueRun?: unknown
    turn?: unknown
    year?: unknown
    month?: unknown
    phase?: unknown
    state?: unknown
    observation?: unknown
    settings?: unknown
    candidates?: unknown
    selected?: unknown
    raceEligibility?: unknown
    items?: unknown
    notes?: unknown
    [key: string]: unknown
}

/** The outcome of parsing one raw JSONL line. */
export type LineParse =
    | { kind: "blank"; lineNumber: number }
    | { kind: "parseError"; lineNumber: number; message: string; excerpt: string; bytes: number }
    | { kind: "wrongType"; lineNumber: number; type: unknown; bytes: number }
    | { kind: "unsupportedVersion"; lineNumber: number; version: unknown; bytes: number }
    | { kind: "malformedEnvelope"; lineNumber: number; missing: string[]; bytes: number }
    | { kind: "record"; lineNumber: number; record: DecisionRecord; bytes: number }

/** UTF-8 byte length of a line plus one for the newline that separated it. Approximate for the final unterminated line. */
function lineBytes(rawLine: string): number {
    return new TextEncoder().encode(rawLine).length + 1
}

/**
 * Parses one raw JSONL line into a typed outcome.
 *
 * Blank lines (whitespace only) are ignored, so a trailing newline never counts as a malformed
 * record. A line that is valid JSON but is not a `decision_trace` at `v == 1`, or lacks the
 * required envelope (`ts`), is returned as a schema failure rather than being interpreted.
 */
export function parseDecisionLine(rawLine: string, lineNumber: number): LineParse {
    const bytes = lineBytes(rawLine)
    const trimmed = rawLine.trim()
    if (trimmed.length === 0) return { kind: "blank", lineNumber }

    let parsed: unknown
    try {
        parsed = JSON.parse(trimmed)
    } catch (e) {
        return { kind: "parseError", lineNumber, message: e instanceof Error ? e.message : String(e), excerpt: excerpt(trimmed), bytes }
    }
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
        return { kind: "parseError", lineNumber, message: "line is not a JSON object", excerpt: excerpt(trimmed), bytes }
    }

    const record = parsed as DecisionRecord
    if (record.type !== DECISION_SCHEMA) {
        return { kind: "wrongType", lineNumber, type: record.type, bytes }
    }
    if (record.v !== DECISION_SCHEMA_VERSION) {
        return { kind: "unsupportedVersion", lineNumber, version: record.v, bytes }
    }
    // `ts` is the only always-written field besides type/v; every identity field is legitimately
    // optional. A record missing `ts` is a broken envelope, not a poor-identity turn.
    const missing: string[] = []
    if (typeof record.ts !== "number") missing.push("ts")
    if (missing.length > 0) return { kind: "malformedEnvelope", lineNumber, missing, bytes }

    return { kind: "record", lineNumber, record, bytes }
}

/** A short single-line excerpt of a raw line for diagnostics, so an error message never dumps a whole record. */
function excerpt(line: string, max = 120): string {
    const oneLine = line.replace(/\s+/g, " ")
    return oneLine.length <= max ? oneLine : oneLine.slice(0, max) + "..."
}

/** How a decision career's `careerToken` joined against the supplied `careers.jsonl` index. */
export type JoinStatus = "none" | "one" | "multiple" | "no-careers-file"

/** A parse or schema failure with the line that produced it. */
export interface LineFailure {
    lineNumber: number
    detail: string
}

/** Everything reported for one career (grouped by `careerToken`, or the unkeyed bucket). */
export interface CareerReport {
    /** The `careerToken`, or null for the bucket of records that carried none. */
    careerToken: string | null
    recordCount: number
    firstTs: number | null
    lastTs: number | null
    /** Distinct scenario values seen (more than one is an anomaly for a single career). */
    scenarios: string[]
    trainees: string[]
    presets: string[]
    queueRuns: number[]
    /** [min, max] of observed turn numbers, or null when no record carried an observed turn. */
    turnRange: [number, number] | null
    /** Records whose date was never read (no `turn` field). Caveated - a resume/default-date turn, not corruption. */
    missingTurnCount: number
    /** Observed turn numbers seen more than once, with their counts. */
    duplicateTurns: { turn: number; count: number }[]
    /** How many times, in record order, an observed turn was lower than the previous observed turn. */
    nonMonotonicCount: number
    /** Integers absent from [min, max] of observed turns. Expected (races/summer skip a main-screen decision), reported not failed. */
    turnGaps: number[]
    /** Committed main-screen action -> count. */
    selectedActionCounts: Record<string, number>
    /** Committed training stat -> count. */
    trainingSelectionCounts: Record<string, number>
    /** Records whose `selected` object was present but empty (a dialog ended the tick). */
    emptySelectionCount: number
    /** Candidate-count distribution across records. */
    candidateStats: { min: number; max: number; mean: number; recordsWithCandidates: number }
    /** Records that carried at least one candidate score / failChance / gains block. */
    recordsWithScoreData: number
    /** How many records set each observation read-flag true. */
    observationCoverage: { turn: number; stats: number; skillPoints: number; aptitudes: number }
    /** Records missing trainee or scenario identity. */
    recordsLackingIdentity: number
    totalBytes: number
    meanBytesPerRecord: number
    minBytes: number
    maxBytes: number
    // Hard consistency failures (line numbers), each one a state the writer cannot legitimately produce.
    selectedActionNotInCandidates: number[]
    selectedTrainingNotInCandidates: number[]
    multipleSelectedActionCandidates: number[]
    multipleSelectedTrainingCandidates: number[]
    // Join result, populated only when a careers file was supplied.
    join: JoinStatus
    joinMatchTypes: string[]
    joinMatchCount: number
    /** A weaker trainee|scenario|run match (ignoring the nonce) when the exact token did not join. Diagnostic only, never proven identity. */
    weakJoinSuggestion: string | null
}

/** The full analysis result. */
export interface AnalysisResult {
    decisionFilesRead: number
    careerFilesRead: number
    totalLines: number
    blankLines: number
    decisionRecordCount: number
    parseErrors: LineFailure[]
    schemaFailures: LineFailure[]
    /** Parse errors encountered in the careers.jsonl input (heterogeneous corpus; reported, not fatal to the decision analysis). */
    careerFileParseErrors: LineFailure[]
    careers: CareerReport[]
    /** Records that carried no careerToken, grouped together, or null when every record was keyed. */
    unkeyed: CareerReport | null
    /** True when a careers file was supplied and its careerToken index was consulted. */
    joinAttempted: boolean
    consistencyFailureCount: number
    warningCount: number
    /** True when strict mode aborted analysis at the first parse/schema failure. */
    abortedByStrict: boolean
    exitCode: number
}

/** Options controlling which records are analyzed and how failures are treated. */
export interface AnalyzerOptions {
    /** Analyze only records whose `careerToken` equals this value. */
    careerToken?: string
    /** Analyze only records with `ts >= since` (epoch millis). */
    since?: number
    /** Analyze only lines whose 1-based number is >= this (pair with a pre-run line count to isolate a new run). */
    fromLine?: number
    /** Abort at the first parse/schema failure instead of continuing past it. */
    strict?: boolean
}

interface CareerAccumulator {
    careerToken: string | null
    recordCount: number
    firstTs: number | null
    lastTs: number | null
    scenarios: Set<string>
    trainees: Set<string>
    presets: Set<string>
    queueRuns: Set<number>
    observedTurnsInOrder: number[]
    turnCounts: Map<number, number>
    missingTurnCount: number
    selectedActionCounts: Map<string, number>
    trainingSelectionCounts: Map<string, number>
    emptySelectionCount: number
    candMin: number
    candMax: number
    candSum: number
    candRecords: number
    recordsWithScoreData: number
    obsTurn: number
    obsStats: number
    obsSkillPoints: number
    obsAptitudes: number
    recordsLackingIdentity: number
    totalBytes: number
    minBytes: number
    maxBytes: number
    selectedActionNotInCandidates: number[]
    selectedTrainingNotInCandidates: number[]
    multipleSelectedActionCandidates: number[]
    multipleSelectedTrainingCandidates: number[]
}

const UNKEYED = "__unkeyed__" // internal bucket key for records with no careerToken; a real token always contains "|", so this never collides

/** A streaming analyzer. Feed decision lines and (optionally) careers lines, then call `finish()`. */
export interface DecisionAnalyzer {
    /** Ingest one raw line from the decisions corpus. Returns false when strict mode has aborted and no more lines should be fed. */
    ingestDecisionLine(rawLine: string, lineNumber: number): boolean
    /** Ingest one raw line from the careers corpus, to build the join index. */
    ingestCareerLine(rawLine: string, lineNumber: number): void
    /** Mark that a decisions file was fully read (for the file count). */
    noteDecisionFile(): void
    /** Mark that a careers file was fully read. */
    noteCareerFile(): void
    finish(): AnalysisResult
}

export function createDecisionAnalyzer(options: AnalyzerOptions = {}): DecisionAnalyzer {
    const careers = new Map<string, CareerAccumulator>()
    const parseErrors: LineFailure[] = []
    const schemaFailures: LineFailure[] = []
    const careerFileParseErrors: LineFailure[] = []
    // careerToken -> record types found for it in careers.jsonl (career_finalize is the intended 1:1 target).
    const careerTokenIndex = new Map<string, string[]>()
    // Weak index: trainee|scenario|runN (token minus nonce) -> tokens, for a diagnostic-only fallback.
    const weakCareerIndex = new Map<string, Set<string>>()
    let totalLines = 0
    let blankLines = 0
    let decisionRecordCount = 0
    let decisionFilesRead = 0
    let careerFilesRead = 0
    let joinAttempted = false
    let aborted = false

    function accFor(token: string | null): CareerAccumulator {
        const key = token ?? UNKEYED
        let acc = careers.get(key)
        if (!acc) {
            acc = {
                careerToken: token,
                recordCount: 0,
                firstTs: null,
                lastTs: null,
                scenarios: new Set(),
                trainees: new Set(),
                presets: new Set(),
                queueRuns: new Set(),
                observedTurnsInOrder: [],
                turnCounts: new Map(),
                missingTurnCount: 0,
                selectedActionCounts: new Map(),
                trainingSelectionCounts: new Map(),
                emptySelectionCount: 0,
                candMin: Infinity,
                candMax: 0,
                candSum: 0,
                candRecords: 0,
                recordsWithScoreData: 0,
                obsTurn: 0,
                obsStats: 0,
                obsSkillPoints: 0,
                obsAptitudes: 0,
                recordsLackingIdentity: 0,
                totalBytes: 0,
                minBytes: Infinity,
                maxBytes: 0,
                selectedActionNotInCandidates: [],
                selectedTrainingNotInCandidates: [],
                multipleSelectedActionCandidates: [],
                multipleSelectedTrainingCandidates: [],
            }
            careers.set(key, acc)
        }
        return acc
    }

    function ingestRecord(record: DecisionRecord, lineNumber: number, bytes: number): void {
        // Filters. A record whose ts/careerToken is filtered out is simply skipped, never an error.
        if (options.careerToken !== undefined && record.careerToken !== options.careerToken) return
        if (options.since !== undefined && (typeof record.ts !== "number" || record.ts < options.since)) return

        decisionRecordCount++
        const token = typeof record.careerToken === "string" ? record.careerToken : null
        const acc = accFor(token)
        acc.recordCount++
        acc.totalBytes += bytes
        acc.minBytes = Math.min(acc.minBytes, bytes)
        acc.maxBytes = Math.max(acc.maxBytes, bytes)

        if (typeof record.ts === "number") {
            acc.firstTs = acc.firstTs === null ? record.ts : Math.min(acc.firstTs, record.ts)
            acc.lastTs = acc.lastTs === null ? record.ts : Math.max(acc.lastTs, record.ts)
        }
        if (typeof record.scenario === "string") acc.scenarios.add(record.scenario)
        if (typeof record.trainee === "string") acc.trainees.add(record.trainee)
        if (typeof record.preset === "string") acc.presets.add(record.preset)
        if (typeof record.queueRun === "number") acc.queueRuns.add(record.queueRun)
        if (typeof record.trainee !== "string" || typeof record.scenario !== "string") acc.recordsLackingIdentity++

        // Turn tracking. `turn` is present only when the date was actually read.
        if (typeof record.turn === "number") {
            const t = record.turn
            acc.turnCounts.set(t, (acc.turnCounts.get(t) ?? 0) + 1)
            acc.observedTurnsInOrder.push(t)
        } else {
            acc.missingTurnCount++
        }

        // Observation coverage.
        const obs = isObject(record.observation) ? record.observation : {}
        if (obs.turnObserved === true) acc.obsTurn++
        if (obs.statsObserved === true) acc.obsStats++
        if (obs.skillPointsObserved === true) acc.obsSkillPoints++
        if (obs.aptitudesObserved === true) acc.obsAptitudes++

        // Candidates.
        const candidates = Array.isArray(record.candidates) ? record.candidates : []
        acc.candSum += candidates.length
        acc.candMin = Math.min(acc.candMin, candidates.length)
        acc.candMax = Math.max(acc.candMax, candidates.length)
        acc.candRecords++
        if (candidates.some((c) => isObject(c) && (has(c, "score") || has(c, "failChance") || has(c, "gains")))) {
            acc.recordsWithScoreData++
        }

        const selectedActionCandidates = candidates.filter((c) => isObject(c) && c.type === "action" && c.selected === true)
        const selectedTrainingCandidates = candidates.filter((c) => isObject(c) && c.type === "training" && c.selected === true)
        if (selectedActionCandidates.length > 1) acc.multipleSelectedActionCandidates.push(lineNumber)
        if (selectedTrainingCandidates.length > 1) acc.multipleSelectedTrainingCandidates.push(lineNumber)

        // Selection.
        const selected = isObject(record.selected) ? record.selected : {}
        if (Object.keys(selected).length === 0) {
            acc.emptySelectionCount++
        }
        if (typeof selected.action === "string") {
            const action = selected.action
            acc.selectedActionCounts.set(action, (acc.selectedActionCounts.get(action) ?? 0) + 1)
            // The writer builds `selected.action` and the chosen candidate from the same ActionChoice,
            // so a committed action that is absent from its own selected candidates is a real contradiction.
            const represented = candidates.some((c) => isObject(c) && c.type === "action" && c.id === action && c.selected === true)
            if (!represented) acc.selectedActionNotInCandidates.push(lineNumber)
        }
        if (typeof selected.training === "string") {
            const training = selected.training
            acc.trainingSelectionCounts.set(training, (acc.trainingSelectionCounts.get(training) ?? 0) + 1)
            const represented = candidates.some((c) => isObject(c) && c.type === "training" && c.id === training && c.selected === true)
            if (!represented) acc.selectedTrainingNotInCandidates.push(lineNumber)
        }
    }

    function ingestDecisionLine(rawLine: string, lineNumber: number): boolean {
        if (aborted) return false
        totalLines++
        if (options.fromLine !== undefined && lineNumber < options.fromLine) return true

        const parsed = parseDecisionLine(rawLine, lineNumber)
        switch (parsed.kind) {
            case "blank":
                blankLines++
                return true
            case "parseError":
                parseErrors.push({ lineNumber, detail: `${parsed.message} :: ${parsed.excerpt}` })
                if (options.strict) aborted = true
                return !options.strict
            case "wrongType":
                schemaFailures.push({ lineNumber, detail: `unexpected type ${JSON.stringify(parsed.type)} (expected "${DECISION_SCHEMA}")` })
                if (options.strict) aborted = true
                return !options.strict
            case "unsupportedVersion":
                schemaFailures.push({ lineNumber, detail: `unsupported schema version ${JSON.stringify(parsed.version)} (this reader understands v${DECISION_SCHEMA_VERSION})` })
                if (options.strict) aborted = true
                return !options.strict
            case "malformedEnvelope":
                schemaFailures.push({ lineNumber, detail: `missing required envelope field(s): ${parsed.missing.join(", ")}` })
                if (options.strict) aborted = true
                return !options.strict
            case "record":
                ingestRecord(parsed.record, lineNumber, parsed.bytes)
                return true
        }
    }

    function ingestCareerLine(rawLine: string, lineNumber: number): void {
        joinAttempted = true
        const trimmed = rawLine.trim()
        if (trimmed.length === 0) return
        let parsed: unknown
        try {
            parsed = JSON.parse(trimmed)
        } catch (e) {
            careerFileParseErrors.push({ lineNumber, detail: `${e instanceof Error ? e.message : String(e)} :: ${excerpt(trimmed)}` })
            return
        }
        if (!isObject(parsed)) return
        const token = parsed.careerToken
        if (typeof token === "string" && token.length > 0) {
            const type = typeof parsed.type === "string" ? parsed.type : "(untyped)"
            const list = careerTokenIndex.get(token) ?? []
            list.push(type)
            careerTokenIndex.set(token, list)
            const weakKey = weakKeyOf(token)
            if (weakKey) {
                const set = weakCareerIndex.get(weakKey) ?? new Set<string>()
                set.add(token)
                weakCareerIndex.set(weakKey, set)
            }
        }
    }

    function finish(): AnalysisResult {
        const reports: CareerReport[] = []
        let unkeyed: CareerReport | null = null
        let consistencyFailureCount = 0
        let warningCount = 0

        for (const acc of careers.values()) {
            const report = buildReport(acc, joinAttempted, careerTokenIndex, weakCareerIndex)
            consistencyFailureCount +=
                report.selectedActionNotInCandidates.length +
                report.selectedTrainingNotInCandidates.length +
                report.multipleSelectedActionCandidates.length +
                report.multipleSelectedTrainingCandidates.length
            // Warnings: anomalies that are reported but not proof the writer is broken.
            if (report.duplicateTurns.length > 0) warningCount++
            if (report.nonMonotonicCount > 0) warningCount++
            if (report.emptySelectionCount > 0) warningCount++
            if (report.recordsLackingIdentity > 0) warningCount++
            if (joinAttempted && report.careerToken !== null && report.join !== "one") warningCount++
            if (acc.careerToken === null) {
                unkeyed = report
                warningCount++ // a real career always carries a token; unkeyed records are themselves an anomaly
            } else {
                reports.push(report)
            }
        }
        reports.sort((a, b) => (a.firstTs ?? 0) - (b.firstTs ?? 0))

        let exitCode = EXIT_CLEAN
        if (warningCount > 0) exitCode = worstExit(exitCode, EXIT_WARNINGS)
        if (parseErrors.length > 0 || schemaFailures.length > 0) exitCode = worstExit(exitCode, EXIT_PARSE_OR_SCHEMA)
        if (consistencyFailureCount > 0) exitCode = worstExit(exitCode, EXIT_CONSISTENCY)

        return {
            decisionFilesRead,
            careerFilesRead,
            totalLines,
            blankLines,
            decisionRecordCount,
            parseErrors,
            schemaFailures,
            careerFileParseErrors,
            careers: reports,
            unkeyed,
            joinAttempted,
            consistencyFailureCount,
            warningCount,
            abortedByStrict: aborted,
            exitCode,
        }
    }

    return {
        ingestDecisionLine,
        ingestCareerLine,
        noteDecisionFile() {
            decisionFilesRead++
        },
        noteCareerFile() {
            careerFilesRead++
        },
        finish,
    }
}

function buildReport(
    acc: CareerAccumulator,
    joinAttempted: boolean,
    careerTokenIndex: Map<string, string[]>,
    weakCareerIndex: Map<string, Set<string>>,
): CareerReport {
    const observed = acc.observedTurnsInOrder
    const turnRange: [number, number] | null = observed.length > 0 ? [Math.min(...observed), Math.max(...observed)] : null

    const duplicateTurns: { turn: number; count: number }[] = []
    for (const [turn, count] of acc.turnCounts) {
        if (count > 1) duplicateTurns.push({ turn, count })
    }
    duplicateTurns.sort((a, b) => a.turn - b.turn)

    let nonMonotonicCount = 0
    for (let i = 1; i < observed.length; i++) {
        if (observed[i] < observed[i - 1]) nonMonotonicCount++
    }

    const turnGaps: number[] = []
    if (turnRange) {
        for (let t = turnRange[0]; t <= turnRange[1]; t++) {
            if (!acc.turnCounts.has(t)) turnGaps.push(t)
        }
    }

    // Join.
    let join: JoinStatus = joinAttempted ? "none" : "no-careers-file"
    let joinMatchTypes: string[] = []
    let joinMatchCount = 0
    let weakJoinSuggestion: string | null = null
    if (joinAttempted && acc.careerToken !== null) {
        const matches = careerTokenIndex.get(acc.careerToken)
        if (matches && matches.length > 0) {
            joinMatchCount = matches.length
            joinMatchTypes = [...new Set(matches)].sort()
            join = matches.length === 1 ? "one" : "multiple"
        } else {
            join = "none"
            const weakKey = weakKeyOf(acc.careerToken)
            const weakSet = weakKey ? weakCareerIndex.get(weakKey) : undefined
            if (weakSet && weakSet.size > 0) {
                // Diagnostic only: a trainee|scenario|run match with a DIFFERENT nonce is not proven identity.
                weakJoinSuggestion = [...weakSet].sort().join(", ")
            }
        }
    }

    const candRecords = acc.candRecords
    return {
        careerToken: acc.careerToken,
        recordCount: acc.recordCount,
        firstTs: acc.firstTs,
        lastTs: acc.lastTs,
        scenarios: [...acc.scenarios].sort(),
        trainees: [...acc.trainees].sort(),
        presets: [...acc.presets].sort(),
        queueRuns: [...acc.queueRuns].sort((a, b) => a - b),
        turnRange,
        missingTurnCount: acc.missingTurnCount,
        duplicateTurns,
        nonMonotonicCount,
        turnGaps,
        selectedActionCounts: mapToObject(acc.selectedActionCounts),
        trainingSelectionCounts: mapToObject(acc.trainingSelectionCounts),
        emptySelectionCount: acc.emptySelectionCount,
        candidateStats: {
            min: candRecords > 0 ? acc.candMin : 0,
            max: acc.candMax,
            mean: candRecords > 0 ? acc.candSum / candRecords : 0,
            recordsWithCandidates: candRecords,
        },
        recordsWithScoreData: acc.recordsWithScoreData,
        observationCoverage: { turn: acc.obsTurn, stats: acc.obsStats, skillPoints: acc.obsSkillPoints, aptitudes: acc.obsAptitudes },
        recordsLackingIdentity: acc.recordsLackingIdentity,
        totalBytes: acc.totalBytes,
        meanBytesPerRecord: acc.recordCount > 0 ? acc.totalBytes / acc.recordCount : 0,
        minBytes: acc.recordCount > 0 ? acc.minBytes : 0,
        maxBytes: acc.maxBytes,
        selectedActionNotInCandidates: acc.selectedActionNotInCandidates,
        selectedTrainingNotInCandidates: acc.selectedTrainingNotInCandidates,
        multipleSelectedActionCandidates: acc.multipleSelectedActionCandidates,
        multipleSelectedTrainingCandidates: acc.multipleSelectedTrainingCandidates,
        join,
        joinMatchTypes,
        joinMatchCount,
        weakJoinSuggestion,
    }
}

/** Drops the trailing nonce from a careerToken, leaving trainee|scenario|runN. Null when the token is not the expected 4-part shape. */
function weakKeyOf(token: string): string | null {
    const parts = token.split("|")
    if (parts.length < 4) return null
    return parts.slice(0, parts.length - 1).join("|")
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

function has(obj: Record<string, unknown>, key: string): boolean {
    return Object.prototype.hasOwnProperty.call(obj, key)
}

function mapToObject(map: Map<string, number>): Record<string, number> {
    const out: Record<string, number> = {}
    for (const [k, v] of [...map].sort((a, b) => b[1] - a[1])) out[k] = v
    return out
}

/**
 * Renders a human-readable report. Deterministic and side-effect free so the CLI and any future
 * consumer print the same thing. Numbers are the analysis's own; nothing is inferred here.
 */
export function renderReport(result: AnalysisResult): string {
    const lines: string[] = []
    const push = (s = "") => lines.push(s)

    push(
        `${result.decisionRecordCount} decision record(s) across ${result.careers.length} career(s)` +
            `${result.unkeyed ? " + 1 unkeyed bucket" : ""} from ${result.decisionFilesRead} decision file(s)` +
            `${result.joinAttempted ? `, joined against ${result.careerFilesRead} careers file(s)` : ""}.`,
    )
    push(
        `${result.totalLines} line(s) read, ${result.blankLines} blank, ` +
            `${result.parseErrors.length} parse error(s), ${result.schemaFailures.length} schema failure(s), ` +
            `${result.consistencyFailureCount} consistency failure(s), ${result.warningCount} warning(s).`,
    )
    if (result.abortedByStrict) push(`STRICT: analysis aborted at the first parse/schema failure.`)
    push(`Exit code: ${result.exitCode} (${exitLabel(result.exitCode)}).`)

    if (result.parseErrors.length > 0) {
        push()
        push(`## Parse errors`)
        for (const f of result.parseErrors.slice(0, 20)) push(`- line ${f.lineNumber}: ${f.detail}`)
        if (result.parseErrors.length > 20) push(`- ... and ${result.parseErrors.length - 20} more`)
    }
    if (result.schemaFailures.length > 0) {
        push()
        push(`## Schema failures`)
        for (const f of result.schemaFailures.slice(0, 20)) push(`- line ${f.lineNumber}: ${f.detail}`)
        if (result.schemaFailures.length > 20) push(`- ... and ${result.schemaFailures.length - 20} more`)
    }
    if (result.careerFileParseErrors.length > 0) {
        push()
        push(`## Careers file parse errors (heterogeneous corpus; informational)`)
        for (const f of result.careerFileParseErrors.slice(0, 10)) push(`- line ${f.lineNumber}: ${f.detail}`)
        if (result.careerFileParseErrors.length > 10) push(`- ... and ${result.careerFileParseErrors.length - 10} more`)
    }

    const all = result.unkeyed ? [...result.careers, result.unkeyed] : result.careers
    for (const c of all) {
        push()
        push(`## ${c.careerToken ?? "(records with no careerToken)"}`)
        push(`- records: ${c.recordCount}`)
        push(`- time: ${fmtTs(c.firstTs)} .. ${fmtTs(c.lastTs)}`)
        push(`- scenario: ${fmtList(c.scenarios)}  trainee: ${fmtList(c.trainees)}  preset: ${fmtList(c.presets)}  queueRun: ${fmtList(c.queueRuns.map(String))}`)
        push(`- observed turns: ${c.turnRange ? `${c.turnRange[0]}..${c.turnRange[1]}` : "none observed"}  (missing turn field: ${c.missingTurnCount})`)
        if (c.turnGaps.length > 0) push(`  - gaps (expected on race/summer turns): ${summarizeInts(c.turnGaps)}`)
        if (c.duplicateTurns.length > 0) push(`  - WARNING duplicate observed turns: ${c.duplicateTurns.map((d) => `${d.turn}x${d.count}`).join(", ")}`)
        if (c.nonMonotonicCount > 0) push(`  - WARNING non-monotonic observed turns: ${c.nonMonotonicCount}`)
        push(`- selected actions: ${fmtCounts(c.selectedActionCounts)}`)
        push(`- training picks: ${fmtCounts(c.trainingSelectionCounts)}`)
        push(`- empty selections: ${c.emptySelectionCount}`)
        push(`- candidates/record: min ${c.candidateStats.min}, max ${c.candidateStats.max}, mean ${c.candidateStats.mean.toFixed(2)} (records with scored data: ${c.recordsWithScoreData})`)
        push(
            `- observation coverage: turn ${c.observationCoverage.turn}/${c.recordCount}, stats ${c.observationCoverage.stats}/${c.recordCount}, ` +
                `skillPts ${c.observationCoverage.skillPoints}/${c.recordCount}, aptitudes ${c.observationCoverage.aptitudes}/${c.recordCount}`,
        )
        if (c.recordsLackingIdentity > 0) push(`- WARNING records lacking trainee/scenario identity: ${c.recordsLackingIdentity}`)
        push(`- bytes: total ${c.totalBytes}, mean ${c.meanBytesPerRecord.toFixed(0)}, min ${c.minBytes}, max ${c.maxBytes}`)
        // Consistency.
        const cf =
            c.selectedActionNotInCandidates.length +
            c.selectedTrainingNotInCandidates.length +
            c.multipleSelectedActionCandidates.length +
            c.multipleSelectedTrainingCandidates.length
        if (cf === 0) {
            push(`- consistency: OK (every committed selection is represented by exactly one selected candidate)`)
        } else {
            if (c.selectedActionNotInCandidates.length > 0) push(`- CONSISTENCY FAILURE selected.action absent from candidates on line(s): ${fmtLines(c.selectedActionNotInCandidates)}`)
            if (c.selectedTrainingNotInCandidates.length > 0) push(`- CONSISTENCY FAILURE selected.training absent from candidates on line(s): ${fmtLines(c.selectedTrainingNotInCandidates)}`)
            if (c.multipleSelectedActionCandidates.length > 0) push(`- CONSISTENCY FAILURE >1 selected action candidate on line(s): ${fmtLines(c.multipleSelectedActionCandidates)}`)
            if (c.multipleSelectedTrainingCandidates.length > 0) push(`- CONSISTENCY FAILURE >1 selected training candidate on line(s): ${fmtLines(c.multipleSelectedTrainingCandidates)}`)
        }
        // Join.
        if (c.careerToken !== null) {
            if (c.join === "no-careers-file") {
                push(`- join: no careers file supplied`)
            } else if (c.join === "one") {
                push(`- join: OK (1 careers row: ${fmtList(c.joinMatchTypes)})`)
            } else if (c.join === "multiple") {
                push(`- WARNING join: ${c.joinMatchCount} careers rows share this token (${fmtList(c.joinMatchTypes)})`)
            } else {
                push(`- WARNING join: no careers row carries this token${c.weakJoinSuggestion ? ` (weak trainee|scenario|run match, different nonce: ${c.weakJoinSuggestion})` : ""}`)
            }
        }
    }
    return lines.join("\n")
}

function exitLabel(code: number): string {
    switch (code) {
        case EXIT_CLEAN:
            return "clean"
        case EXIT_WARNINGS:
            return "success with warnings"
        case EXIT_PARSE_OR_SCHEMA:
            return "parse/schema failure"
        case EXIT_CONSISTENCY:
            return "consistency failure"
        default:
            return "unknown"
    }
}

function fmtTs(ts: number | null): string {
    if (ts === null) return "-"
    // Deterministic UTC ISO string; no local-time surprises across machines.
    return new Date(ts).toISOString()
}

function fmtList(values: string[]): string {
    return values.length === 0 ? "-" : values.join(", ")
}

function fmtCounts(counts: Record<string, number>): string {
    const entries = Object.entries(counts)
    return entries.length === 0 ? "-" : entries.map(([k, v]) => `${k}:${v}`).join(", ")
}

function fmtLines(nums: number[]): string {
    return nums.length <= 12 ? nums.join(", ") : `${nums.slice(0, 12).join(", ")}, ... (+${nums.length - 12})`
}

/** Collapses a sorted int list into ranges for compact display, e.g. [4,5,6,9] -> "4-6, 9". */
function summarizeInts(nums: number[]): string {
    if (nums.length === 0) return "-"
    const parts: string[] = []
    let start = nums[0]
    let prev = nums[0]
    for (let i = 1; i < nums.length; i++) {
        if (nums[i] === prev + 1) {
            prev = nums[i]
            continue
        }
        parts.push(start === prev ? `${start}` : `${start}-${prev}`)
        start = nums[i]
        prev = nums[i]
    }
    parts.push(start === prev ? `${start}` : `${start}-${prev}`)
    return parts.join(", ")
}
