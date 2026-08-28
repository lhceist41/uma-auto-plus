/**
 * DecisionTrace v1 corpus analysis.
 *
 * Pure, streaming-friendly parsing and consistency checking for the per-turn `decision_trace`
 * records written on-device by `DecisionTrace.kt` / `OutcomeCorpus.append(.., DECISIONS_PATH)`,
 * consumed by `scripts/analyze-decisions.mjs` (CLI) and the Jest suite.
 *
 * The reader is read-only: it never rewrites, normalizes or truncates its input. It recognizes
 * `type == "decision_trace"` at any supported `v` (see `SUPPORTED_DECISION_SCHEMA_VERSIONS`); anything
 * else is reported, never silently reinterpreted. Its job is to prove or disprove that a pulled corpus is internally consistent -
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

/** The current decision_trace schema version, matching `DecisionTrace.SCHEMA_VERSION` (the writer's output). */
export const DECISION_SCHEMA_VERSION = 1

/**
 * The decision_trace schema versions this reader accepts. v1 is the current writer output; v2 is accepted
 * ahead of any writer bump so a forward corpus reads without a reader change. Reader support is additive-only:
 * the parser reads a fixed field set and ignores the rest, so a v2 record carrying unknown fields still parses.
 */
export const SUPPORTED_DECISION_SCHEMA_VERSIONS: ReadonlySet<number> = new Set([1, 2])

/** Record type discriminator for the optional companion corpus, matching `CareerStateSerializer.SCHEMA`. */
export const CAREER_STATE_SCHEMA = "career_state"

/** The current career_state schema version, matching `CareerStateSerializer.SCHEMA_VERSION` (the writer's output). */
export const CAREER_STATE_SCHEMA_VERSION = 1

/** The career_state schema versions this reader accepts. Same forward-compatible, additive-only contract as `SUPPORTED_DECISION_SCHEMA_VERSIONS`. */
export const SUPPORTED_CAREER_STATE_SCHEMA_VERSIONS: ReadonlySet<number> = new Set([1, 2])

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
    /** Optional monotonic per-career sequence stamped by the CareerState writer. Absent on old records and when no CareerState was built. */
    seq?: unknown
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
 * True when `v` is a numeric schema version this reader supports. Non-numbers (a string "1", null, undefined,
 * an absent field) are never supported, exactly as the prior `record.v !== VERSION` gate only ever accepted the
 * numeric current version.
 */
function isSupportedVersion(v: unknown, supported: ReadonlySet<number>): boolean {
    return typeof v === "number" && supported.has(v)
}

/** Formats a supported-version set for a diagnostic message, e.g. "v1, v2". Sorted for a deterministic message. */
function formatSupportedVersions(supported: ReadonlySet<number>): string {
    return [...supported]
        .sort((a, b) => a - b)
        .map((v) => `v${v}`)
        .join(", ")
}

/**
 * Parses one raw JSONL line into a typed outcome.
 *
 * Blank lines (whitespace only) are ignored, so a trailing newline never counts as a malformed
 * record. A line that is valid JSON but is not a `decision_trace` at a supported `v`, or lacks the
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
    if (!isSupportedVersion(record.v, SUPPORTED_DECISION_SCHEMA_VERSIONS)) {
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

/** The outcome of parsing one raw career_state JSONL line. Only the join-key fields are retained; the full state blob is never kept. */
export type CareerStateLineParse =
    | { kind: "blank"; lineNumber: number }
    | { kind: "parseError"; lineNumber: number; message: string; excerpt: string; bytes: number }
    | { kind: "wrongType"; lineNumber: number; type: unknown; bytes: number }
    | { kind: "unsupportedVersion"; lineNumber: number; version: unknown; bytes: number }
    | { kind: "malformedEnvelope"; lineNumber: number; missing: string[]; bytes: number }
    | { kind: "record"; lineNumber: number; careerToken: string; seq: number; turnObserved: boolean; bytes: number }

/** A positive integer is the only valid `seq`; the writer stamps it from a per-career counter that starts at 1. */
function isPositiveInt(value: unknown): value is number {
    return typeof value === "number" && Number.isInteger(value) && value > 0
}

/**
 * Parses one raw career_state JSONL line into a typed outcome, mirroring `parseDecisionLine`'s taxonomy.
 *
 * A career_state record is only useful if it carries a join key, so a missing/invalid `identity.careerToken`
 * or a non-positive-integer `seq` is a malformed envelope (parse/schema class), not a poor-observation turn.
 * Every other field is legitimately omissible (the writer omits date/stats/etc. when unobserved), so absence
 * is tolerated, never inferred away. Only the join key and the `turnObserved` flag are read out.
 */
export function parseCareerStateLine(rawLine: string, lineNumber: number): CareerStateLineParse {
    const bytes = lineBytes(rawLine)
    const trimmed = rawLine.trim()
    if (trimmed.length === 0) return { kind: "blank", lineNumber }

    let parsed: unknown
    try {
        parsed = JSON.parse(trimmed)
    } catch (e) {
        return { kind: "parseError", lineNumber, message: e instanceof Error ? e.message : String(e), excerpt: excerpt(trimmed), bytes }
    }
    if (!isObject(parsed)) {
        return { kind: "parseError", lineNumber, message: "line is not a JSON object", excerpt: excerpt(trimmed), bytes }
    }

    const record = parsed
    if (record.type !== CAREER_STATE_SCHEMA) {
        return { kind: "wrongType", lineNumber, type: record.type, bytes }
    }
    if (!isSupportedVersion(record.v, SUPPORTED_CAREER_STATE_SCHEMA_VERSIONS)) {
        return { kind: "unsupportedVersion", lineNumber, version: record.v, bytes }
    }
    const missing: string[] = []
    if (typeof record.ts !== "number") missing.push("ts")
    const identity = isObject(record.identity) ? record.identity : {}
    const token = identity.careerToken
    if (typeof token !== "string" || token.length === 0) missing.push("identity.careerToken")
    if (!isPositiveInt(record.seq)) missing.push("seq")
    if (missing.length > 0) return { kind: "malformedEnvelope", lineNumber, missing, bytes }

    const observation = isObject(record.observation) ? record.observation : {}
    return { kind: "record", lineNumber, careerToken: token as string, seq: record.seq as number, turnObserved: observation.turnObserved === true, bytes }
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

/** A composite `(careerToken, seq)` join key, reported for duplicate-key diagnostics. */
export interface CareerStateKeyRef {
    careerToken: string
    seq: number
}

/** Per-career slice of the career_state join, sorted by careerToken for determinism. Bounded (counts only). */
export interface CareerStateJoinCareer {
    careerToken: string
    stateRecords: number
    sequencedTraces: number
    joinedPairs: number
    stateWithoutTrace: number
    traceWithoutState: number
}

/**
 * The token+seq join between the decision-trace corpus and the optional career_state corpus.
 * Present on `AnalysisResult` only when a career_state input was supplied. Missing joins
 * (`stateWithoutTrace` / `traceWithoutState`) are coverage diagnostics, not failures: a state can be
 * built on an unknown-date turn where the tracer never opened a window, and a trace can outlive a
 * swallowed CareerState build. Only duplicate composite keys (impossible for the writer to produce)
 * and malformed state records are hard errors.
 */
export interface CareerStateJoin {
    careerStateFilesRead: number
    stateRecordCount: number
    /** State records whose own `observation.turnObserved` was true. Informational. */
    stateTurnObservedCount: number
    /** Every analyzed decision record (== sequencedTraces + unsequencedTraces). */
    traceRecordCount: number
    /** Decision records carrying both a careerToken and a valid positive-int `seq` (join-eligible). */
    sequencedTraceCount: number
    /** Decision records with no usable join key (no `seq`, or seq present but no careerToken). Old records land here. */
    unsequencedTraceCount: number
    /** Distinct `(careerToken, seq)` pairs present in BOTH corpora. */
    joinedPairCount: number
    /** Distinct state pairs with no matching trace. Benign coverage gap, not a failure. */
    stateWithoutTrace: number
    /** Distinct sequenced-trace pairs with no matching state. Benign coverage gap, not a failure. */
    traceWithoutState: number
    /** Repeated `(careerToken, seq)` among state records. A hard consistency failure (exit 3). */
    stateDuplicateKeys: CareerStateKeyRef[]
    /** Repeated `(careerToken, seq)` among sequenced traces. A hard consistency failure (exit 3). */
    traceDuplicateKeys: CareerStateKeyRef[]
    /** Career_state lines that failed to parse or carried the wrong type/version/envelope (exit 2). */
    stateParseSchemaFailures: LineFailure[]
    /** Career_state blank lines (ignored, never failed). */
    stateBlankLines: number
    perCareer: CareerStateJoinCareer[]
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
    /** The cross-career aggregate, present only when the analyzer ran in aggregate mode. Additive: absent in the default single-career result. */
    aggregate?: AggregateResult
    /** The token+seq career_state join, present only when a career_state input was supplied. Additive: absent otherwise, so default output is unchanged. */
    join?: CareerStateJoin
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
    /** Compute the corpus-level cross-career aggregate (attached to `AnalysisResult.aggregate`). Off by default; the single-career report is unchanged either way. */
    aggregate?: boolean
    /** True when a career_state input is supplied; enables token+seq join tracking and the join report. Off by default; output is byte-identical when off. */
    careerState?: boolean
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
    // Aggregate-only candidate diagnostics. Populated only when the analyzer runs in aggregate mode;
    // otherwise they stay at these zero/empty values and are never read (the single-career path is unchanged).
    zeroCandidateRecords: number
    recordsWithGains: number
    recordsWithFailChance: number
    recordsWithTrainingScore: number
    recordsWithHardExcluded: number
    /** candidate-count -> how many records had that many candidates (bounded: counts are small integers), for an exact corpus median. */
    candHistogram: Map<number, number>
}

/** The `career_finalize` fields aggregated by the outcome section. Compact (one per careerToken), so no raw trace is retained. */
interface FinalizeSummary {
    finalizationDecision: string | null
    sessionOutcome: string | null
    verifiedRemainingSp: number | null
    scenario: string | null
    trainee: string | null
    objective: string | null
    retryUsed: boolean | null
}

/** A representative turn reference where an observation flag was not a confirmed read. Bounded per flag. */
export interface UnobservedRef {
    careerToken: string | null
    turn: number | null
}

const UNKEYED = "__unkeyed__" // internal bucket key for records with no careerToken; a real token always contains "|", so this never collides

/** A streaming analyzer. Feed decision lines and (optionally) careers lines, then call `finish()`. */
export interface DecisionAnalyzer {
    /** Ingest one raw line from the decisions corpus. Returns false when strict mode has aborted and no more lines should be fed. */
    ingestDecisionLine(rawLine: string, lineNumber: number): boolean
    /** Ingest one raw line from the careers corpus, to build the join index. */
    ingestCareerLine(rawLine: string, lineNumber: number): void
    /** Ingest one raw line from the optional career_state corpus, to build the token+seq join index. */
    ingestCareerStateLine(rawLine: string, lineNumber: number): void
    /** Mark that a decisions file was fully read (for the file count). */
    noteDecisionFile(): void
    /** Mark that a careers file was fully read. */
    noteCareerFile(): void
    /** Mark that a career_state file was fully read. */
    noteCareerStateFile(): void
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
    // Aggregate-only: careerToken -> compact career_finalize field values, for the outcome section. Populated only in aggregate mode.
    const finalizeByToken = new Map<string, FinalizeSummary[]>()
    const collectAggregate = options.aggregate === true
    // Career_state join (opt-in): only tracked/reported when a career_state input is supplied, so default output is byte-identical.
    const collectJoin = options.careerState === true
    // Bounded join index: careerToken -> (seq -> count). Stores only the join key + a count, never the state blob.
    const stateByToken = new Map<string, Map<number, number>>()
    const traceSeqByToken = new Map<string, Map<number, number>>()
    const stateDuplicateKeys: CareerStateKeyRef[] = []
    const traceDuplicateKeys: CareerStateKeyRef[] = []
    const stateParseSchemaFailures: LineFailure[] = []
    let stateRecordCount = 0
    let stateTurnObservedCount = 0
    let stateBlankLines = 0
    let sequencedTraceCount = 0
    let unsequencedTraceCount = 0
    let careerStateFilesRead = 0
    // Aggregate-only: a few representative turn references per observation flag that was not a confirmed read, bounded so the analyzer stays streaming.
    const UNOBSERVED_EXAMPLE_CAP = 8
    const unobservedExamples: Record<"turn" | "stats" | "skillPoints" | "aptitudes", UnobservedRef[]> = { turn: [], stats: [], skillPoints: [], aptitudes: [] }
    function pushUnobserved(flag: "turn" | "stats" | "skillPoints" | "aptitudes", token: string | null, turn: number | null): void {
        const list = unobservedExamples[flag]
        if (list.length < UNOBSERVED_EXAMPLE_CAP) list.push({ careerToken: token, turn })
    }
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
                zeroCandidateRecords: 0,
                recordsWithGains: 0,
                recordsWithFailChance: 0,
                recordsWithTrainingScore: 0,
                recordsWithHardExcluded: 0,
                candHistogram: new Map(),
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

        // Aggregate-only collection. Gated so the single-career path allocates and computes nothing extra.
        if (collectAggregate) {
            const n = candidates.length
            acc.candHistogram.set(n, (acc.candHistogram.get(n) ?? 0) + 1)
            if (n === 0) acc.zeroCandidateRecords++
            if (candidates.some((c) => isObject(c) && has(c, "gains"))) acc.recordsWithGains++
            if (candidates.some((c) => isObject(c) && has(c, "failChance"))) acc.recordsWithFailChance++
            if (candidates.some((c) => isObject(c) && has(c, "score"))) acc.recordsWithTrainingScore++
            if (candidates.some((c) => isObject(c) && c.rejected === true)) acc.recordsWithHardExcluded++
            // A flag that is not exactly `true` is not a confirmed read; record a bounded example turn ref.
            const turnRef = typeof record.turn === "number" ? record.turn : null
            if (obs.turnObserved !== true) pushUnobserved("turn", token, turnRef)
            if (obs.statsObserved !== true) pushUnobserved("stats", token, turnRef)
            if (obs.skillPointsObserved !== true) pushUnobserved("skillPoints", token, turnRef)
            if (obs.aptitudesObserved !== true) pushUnobserved("aptitudes", token, turnRef)
        }

        // Career_state join: index this trace by (careerToken, seq) when both are present. A record is
        // join-eligible only with a token AND a valid positive-int seq; anything else is unsequenced
        // (old records carry no seq, and a seq without a token cannot form a join key). Never infer seq from turn.
        if (collectJoin) {
            if (token !== null && isPositiveInt(record.seq)) {
                const seq = record.seq
                sequencedTraceCount++
                let m = traceSeqByToken.get(token)
                if (!m) {
                    m = new Map()
                    traceSeqByToken.set(token, m)
                }
                const prev = m.get(seq) ?? 0
                m.set(seq, prev + 1)
                if (prev >= 1) traceDuplicateKeys.push({ careerToken: token, seq })
            } else {
                unsequencedTraceCount++
            }
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
                schemaFailures.push({ lineNumber, detail: `unsupported schema version ${JSON.stringify(parsed.version)} (this reader understands ${formatSupportedVersions(SUPPORTED_DECISION_SCHEMA_VERSIONS)})` })
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
            // Aggregate-only: retain the finalize field values (never the whole row) for the outcome section.
            if (collectAggregate && parsed.type === "career_finalize") {
                const summaries = finalizeByToken.get(token) ?? []
                summaries.push({
                    finalizationDecision: typeof parsed.finalizationDecision === "string" ? parsed.finalizationDecision : null,
                    sessionOutcome: typeof parsed.sessionOutcome === "string" ? parsed.sessionOutcome : null,
                    verifiedRemainingSp: typeof parsed.verifiedRemainingSp === "number" ? parsed.verifiedRemainingSp : null,
                    scenario: typeof parsed.scenario === "string" ? parsed.scenario : null,
                    trainee: typeof parsed.trainee === "string" ? parsed.trainee : null,
                    objective: typeof parsed.objective === "string" ? parsed.objective : null,
                    retryUsed: typeof parsed.retryUsed === "boolean" ? parsed.retryUsed : null,
                })
                finalizeByToken.set(token, summaries)
            }
        }
    }

    function ingestCareerStateLine(rawLine: string, lineNumber: number): void {
        const parsed = parseCareerStateLine(rawLine, lineNumber)
        switch (parsed.kind) {
            case "blank":
                stateBlankLines++
                return
            case "parseError":
                stateParseSchemaFailures.push({ lineNumber, detail: `${parsed.message} :: ${parsed.excerpt}` })
                return
            case "wrongType":
                stateParseSchemaFailures.push({ lineNumber, detail: `unexpected type ${JSON.stringify(parsed.type)} (expected "${CAREER_STATE_SCHEMA}")` })
                return
            case "unsupportedVersion":
                stateParseSchemaFailures.push({ lineNumber, detail: `unsupported schema version ${JSON.stringify(parsed.version)} (this reader understands ${formatSupportedVersions(SUPPORTED_CAREER_STATE_SCHEMA_VERSIONS)})` })
                return
            case "malformedEnvelope":
                stateParseSchemaFailures.push({ lineNumber, detail: `missing/invalid required field(s): ${parsed.missing.join(", ")}` })
                return
            case "record": {
                stateRecordCount++
                if (parsed.turnObserved) stateTurnObservedCount++
                let m = stateByToken.get(parsed.careerToken)
                if (!m) {
                    m = new Map()
                    stateByToken.set(parsed.careerToken, m)
                }
                const prev = m.get(parsed.seq) ?? 0
                m.set(parsed.seq, prev + 1)
                // A duplicate composite key is impossible for the writer to legitimately produce (seq is per-career monotonic).
                if (prev >= 1) stateDuplicateKeys.push({ careerToken: parsed.careerToken, seq: parsed.seq })
                return
            }
        }
    }

    /** Builds the token+seq join report from the two bounded indexes. Deterministic (keys sorted). Only called when collectJoin. */
    function buildJoin(): CareerStateJoin {
        let joinedPairCount = 0
        let stateWithoutTrace = 0
        let traceWithoutState = 0
        const tokens = new Set<string>([...stateByToken.keys(), ...traceSeqByToken.keys()])
        const perCareer: CareerStateJoinCareer[] = []
        for (const token of [...tokens].sort(cmpStr)) {
            const stateMap = stateByToken.get(token)
            const traceMap = traceSeqByToken.get(token)
            let joined = 0
            let sWithout = 0
            let tWithout = 0
            if (stateMap) {
                for (const seq of stateMap.keys()) {
                    if (traceMap && traceMap.has(seq)) joined++
                    else sWithout++
                }
            }
            if (traceMap) {
                for (const seq of traceMap.keys()) {
                    if (!(stateMap && stateMap.has(seq))) tWithout++
                }
            }
            joinedPairCount += joined
            stateWithoutTrace += sWithout
            traceWithoutState += tWithout
            perCareer.push({
                careerToken: token,
                stateRecords: stateMap ? sumMapValues(stateMap) : 0,
                sequencedTraces: traceMap ? sumMapValues(traceMap) : 0,
                joinedPairs: joined,
                stateWithoutTrace: sWithout,
                traceWithoutState: tWithout,
            })
        }
        return {
            careerStateFilesRead,
            stateRecordCount,
            stateTurnObservedCount,
            traceRecordCount: sequencedTraceCount + unsequencedTraceCount,
            sequencedTraceCount,
            unsequencedTraceCount,
            joinedPairCount,
            stateWithoutTrace,
            traceWithoutState,
            stateDuplicateKeys: [...stateDuplicateKeys].sort(cmpKeyRef),
            traceDuplicateKeys: [...traceDuplicateKeys].sort(cmpKeyRef),
            stateParseSchemaFailures,
            stateBlankLines,
            perCareer,
        }
    }

    function finish(): AnalysisResult {
        const reports: CareerReport[] = []
        let unkeyed: CareerReport | null = null
        let consistencyFailureCount = 0
        let warningCount = 0
        // Keep each keyed career's accumulator beside its report (aggregate mode reads the accumulator's
        // gated counters/histogram); the unkeyed bucket is tracked separately.
        const keyedPairs: { acc: CareerAccumulator; report: CareerReport }[] = []
        let unkeyedPair: { acc: CareerAccumulator; report: CareerReport } | null = null

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
                unkeyedPair = { acc, report }
                warningCount++ // a real career always carries a token; unkeyed records are themselves an anomaly
            } else {
                reports.push(report)
                keyedPairs.push({ acc, report })
            }
        }
        reports.sort((a, b) => (a.firstTs ?? 0) - (b.firstTs ?? 0))
        keyedPairs.sort((a, b) => (a.report.firstTs ?? 0) - (b.report.firstTs ?? 0))

        // Career_state join: computed only when a career_state input was supplied. Missing joins are benign
        // (no exit bump); duplicate composite keys are exit 3, malformed state records are exit 2.
        const join = collectJoin ? buildJoin() : undefined

        let exitCode = EXIT_CLEAN
        if (warningCount > 0) exitCode = worstExit(exitCode, EXIT_WARNINGS)
        if (parseErrors.length > 0 || schemaFailures.length > 0) exitCode = worstExit(exitCode, EXIT_PARSE_OR_SCHEMA)
        if (consistencyFailureCount > 0) exitCode = worstExit(exitCode, EXIT_CONSISTENCY)
        if (join) {
            if (join.stateParseSchemaFailures.length > 0) exitCode = worstExit(exitCode, EXIT_PARSE_OR_SCHEMA)
            if (join.stateDuplicateKeys.length > 0 || join.traceDuplicateKeys.length > 0) exitCode = worstExit(exitCode, EXIT_CONSISTENCY)
        }

        const result: AnalysisResult = {
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
        if (collectAggregate) {
            result.aggregate = buildAggregate({
                keyedPairs,
                unkeyedPair,
                decisionRecordCount,
                parseErrorCount: parseErrors.length,
                schemaFailureCount: schemaFailures.length,
                consistencyFailureCount,
                warningCount,
                joinAttempted,
                finalizeByToken,
                unobservedExamples,
            })
        }
        if (join) result.join = join
        return result
    }

    return {
        ingestDecisionLine,
        ingestCareerLine,
        ingestCareerStateLine,
        noteDecisionFile() {
            decisionFilesRead++
        },
        noteCareerFile() {
            careerFilesRead++
        },
        noteCareerStateFile() {
            careerStateFilesRead++
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

    // Additive career_state join section, present only when a career_state input was supplied.
    if (result.join) {
        push()
        push(renderCareerStateJoin(result.join))
    }
    return lines.join("\n")
}

/**
 * Renders the token+seq career_state join as a deterministic section. `stateWithoutTrace` /
 * `traceWithoutState` are labeled as coverage diagnostics, not failures. Only duplicate composite
 * keys and malformed state records are flagged as errors here.
 */
export function renderCareerStateJoin(join: CareerStateJoin): string {
    const lines: string[] = []
    const push = (s = "") => lines.push(s)
    push(`## Career-state join (token+seq)`)
    push(`- career_state records: ${join.stateRecordCount} (turnObserved: ${join.stateTurnObservedCount}) from ${join.careerStateFilesRead} file(s), ${join.stateBlankLines} blank`)
    push(`- decision traces: ${join.traceRecordCount} (sequenced: ${join.sequencedTraceCount}, unsequenced: ${join.unsequencedTraceCount})`)
    push(`- joined (token,seq) pairs: ${join.joinedPairCount}`)
    push(`- coverage gaps (benign, not failures): state without trace ${join.stateWithoutTrace}, trace without state ${join.traceWithoutState}`)
    if (join.stateParseSchemaFailures.length > 0) {
        push(`- PARSE/SCHEMA FAILURE career_state line(s):`)
        for (const f of join.stateParseSchemaFailures.slice(0, 20)) push(`  - line ${f.lineNumber}: ${f.detail}`)
        if (join.stateParseSchemaFailures.length > 20) push(`  - ... and ${join.stateParseSchemaFailures.length - 20} more`)
    }
    if (join.stateDuplicateKeys.length > 0) {
        push(`- CONSISTENCY FAILURE duplicate (careerToken, seq) among career_state records: ${fmtKeyRefs(join.stateDuplicateKeys)}`)
    }
    if (join.traceDuplicateKeys.length > 0) {
        push(`- CONSISTENCY FAILURE duplicate (careerToken, seq) among sequenced traces: ${fmtKeyRefs(join.traceDuplicateKeys)}`)
    }
    if (join.stateParseSchemaFailures.length === 0 && join.stateDuplicateKeys.length === 0 && join.traceDuplicateKeys.length === 0) {
        push(`- integrity: OK (no duplicate composite keys, no malformed career_state records)`)
    }
    for (const p of join.perCareer) {
        push(`### ${p.careerToken}`)
        push(`- state ${p.stateRecords}, sequenced traces ${p.sequencedTraces}, joined ${p.joinedPairs}, state-without-trace ${p.stateWithoutTrace}, trace-without-state ${p.traceWithoutState}`)
    }
    return lines.join("\n")
}

/** Compact display of composite keys for a diagnostic line, bounded so a large list never floods the report. */
function fmtKeyRefs(refs: CareerStateKeyRef[]): string {
    const shown = refs.slice(0, 12).map((r) => `${r.careerToken}#${r.seq}`)
    return refs.length <= 12 ? shown.join(", ") : `${shown.join(", ")}, ... (+${refs.length - 12})`
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

// ---------------------------------------------------------------------------
// Cross-career aggregate (opt-in, read-only).
//
// The aggregate is descriptive only: it counts what the bot chose across careers, groups it by
// scenario, and summarizes trace coverage and the joined career_finalize outcomes. It never scores a
// decision, never claims causality, and never invents a field the corpus does not carry. Behavioral
// sections (scenarios, actions, trainings, per-career rows, outcomes) cover keyed careers; quality
// sections (observations, candidates) and the corpus summary cover the whole analyzed file; outcomes
// aggregate strictly over careers that join exactly one career_finalize, with the denominator reported.
// ---------------------------------------------------------------------------

/** A `{value, count}` row used across the aggregate; always sorted count-desc then value-asc for determinism. */
export interface CountRow {
    value: string
    count: number
}

/** A distribution row grouped by career vs record for the corpus scenario/trainee/preset breakdowns. */
export interface CorpusGroupRow {
    value: string
    careers: number
    records: number
}

export interface AggregateCorpus {
    totalValidRecords: number
    distinctKeyedCareers: number
    unkeyedRecordCount: number
    careersWithExactlyOneFinalize: number
    careersWithNoFinalize: number
    careersWithDuplicateFinalize: number
    scenarioDistribution: CorpusGroupRow[]
    traineeDistribution: CorpusGroupRow[]
    presetDistribution: CorpusGroupRow[]
    parseSchemaFailures: number
    consistencyFailures: number
    warnings: number
    earliestTs: number | null
    latestTs: number | null
    totalBytes: number
    meanBytesPerRecord: number
    recordsPerCareer: { mean: number; median: number; min: number; max: number }
}

export interface AggregatePctRow {
    id: string
    count: number
    pct: number
}

export interface AggregateScenario {
    scenario: string
    careerCount: number
    traceRecordCount: number
    meanRecordsPerCareer: number
    observedTurnRecords: number
    observedTurnPct: number
    /** Sum of per-career turn-gap counts (races/summer legitimately skip a main-screen decision). Diagnostic, not a failure. */
    gapCount: number
    selectedActions: AggregatePctRow[]
    selectedTrainings: AggregatePctRow[]
    observationCoverage: { flag: string; observed: number; pct: number }[]
    emptySelectionCount: number
    candidateConsistencyFailureCount: number
    meanCandidateCount: number
    meanRecordBytes: number
}

export interface AggregateDistributionRow {
    id: string
    count: number
    /** Percentage of all traces that carried a committed value of this kind (keyed careers). */
    pct: number
    /** Number of keyed careers in which this value appeared. */
    careerCount: number
    scenarioBreakdown: CountRow[]
}

export interface AggregateObservationFlag {
    flag: string
    observed: number
    missing: number
    pctObserved: number
    /** Keyed careers with at least one record where this flag was not a confirmed read. */
    careersWithUnobserved: number
    /** A few representative turn references where this flag was unobserved (bounded). */
    examples: UnobservedRef[]
}

export interface AggregateObservations {
    totalRecords: number
    flags: AggregateObservationFlag[]
}

export interface AggregateCandidates {
    totalRecords: number
    meanCandidateCount: number
    medianCandidateCount: number
    recordsWithZeroCandidates: number
    recordsSelectedActionNotRepresented: number
    recordsWithMultipleSelectedAction: number
    recordsWithMultipleSelectedTraining: number
    recordsWithTrainingScores: number
    recordsWithGains: number
    recordsWithFailChance: number
    recordsWithHardExcluded: number
    /** Not derivable from v1: the candidate list is the actions the cascade named, not the full action space. Always null. */
    partialActionCandidateCoverage: null
    note: string
}

export interface AggregateOutcomes {
    /** Denominator for every value tally below: careers that joined exactly one career_finalize row. */
    joinedCareerCount: number
    careersWithNoFinalize: number
    careersWithDuplicateFinalize: number
    finalizationDecisionCounts: CountRow[]
    sessionOutcomeCounts: CountRow[]
    /** From the career_finalize rows themselves, whose scenario/trainee spelling can differ from the decision side. */
    scenarioCounts: CountRow[]
    traineeCounts: CountRow[]
    objectiveCounts: CountRow[]
    retryUsedCount: number
    remainingSp: { count: number; mean: number | null; median: number | null; min: number | null; max: number | null }
    note: string
}

export interface AggregateCareerRow {
    careerToken: string
    scenario: string
    trainee: string
    preset: string
    traceCount: number
    turnRange: [number, number] | null
    gapCount: number
    actions: Record<string, number>
    trainings: Record<string, number>
    join: JoinStatus
    finalizationDecision: string | null
    sessionOutcome: string | null
    remainingSp: number | null
    warningFailureCount: number
}

export interface AggregateResult {
    mode: "aggregate"
    corpus: AggregateCorpus
    scenarios: AggregateScenario[]
    actions: AggregateDistributionRow[]
    trainings: AggregateDistributionRow[]
    observations: AggregateObservations
    candidates: AggregateCandidates
    outcomes: AggregateOutcomes
    careers: AggregateCareerRow[]
}

interface AggregateInputs {
    keyedPairs: { acc: CareerAccumulator; report: CareerReport }[]
    unkeyedPair: { acc: CareerAccumulator; report: CareerReport } | null
    decisionRecordCount: number
    parseErrorCount: number
    schemaFailureCount: number
    consistencyFailureCount: number
    warningCount: number
    joinAttempted: boolean
    finalizeByToken: Map<string, FinalizeSummary[]>
    unobservedExamples: Record<"turn" | "stats" | "skillPoints" | "aptitudes", UnobservedRef[]>
}

const OBS_FLAGS = ["turn", "stats", "skillPoints", "aptitudes"] as const

/** Builds the cross-career aggregate from the finished per-career accumulators/reports. Pure and deterministic. */
function buildAggregate(input: AggregateInputs): AggregateResult {
    const keyed = [...input.keyedPairs].sort((a, b) => {
        const t = (a.report.firstTs ?? 0) - (b.report.firstTs ?? 0)
        return t !== 0 ? t : cmpStr(a.report.careerToken ?? "", b.report.careerToken ?? "")
    })
    // All buckets (keyed + unkeyed) for whole-file quality sections.
    const all = input.unkeyedPair ? [...keyed, input.unkeyedPair] : keyed

    // Finalize join classification (careerToken -> how many career_finalize rows). The outcome denominator.
    let oneFinalize = 0
    let noFinalize = 0
    let dupFinalize = 0
    const finalizeOne: FinalizeSummary[] = []
    for (const { report } of keyed) {
        const summaries = report.careerToken !== null ? (input.finalizeByToken.get(report.careerToken) ?? []) : []
        if (summaries.length === 1) {
            oneFinalize++
            finalizeOne.push(summaries[0])
        } else if (summaries.length === 0) {
            noFinalize++
        } else {
            dupFinalize++
        }
    }

    // ---- corpus ----
    const scenarioGroup = new Map<string, { careers: number; records: number }>()
    const traineeGroup = new Map<string, { careers: number; records: number }>()
    const presetGroup = new Map<string, { careers: number; records: number }>()
    const perCareerRecordCounts: number[] = []
    let earliestTs: number | null = null
    let latestTs: number | null = null
    for (const { report } of keyed) {
        perCareerRecordCounts.push(report.recordCount)
        addGroup(scenarioGroup, singleOr(report.scenarios), report.recordCount)
        addGroup(traineeGroup, singleOr(report.trainees), report.recordCount)
        addGroup(presetGroup, singleOr(report.presets), report.recordCount)
        if (report.firstTs !== null) earliestTs = earliestTs === null ? report.firstTs : Math.min(earliestTs, report.firstTs)
        if (report.lastTs !== null) latestTs = latestTs === null ? report.lastTs : Math.max(latestTs, report.lastTs)
    }
    if (input.unkeyedPair) {
        const r = input.unkeyedPair.report
        if (r.firstTs !== null) earliestTs = earliestTs === null ? r.firstTs : Math.min(earliestTs, r.firstTs)
        if (r.lastTs !== null) latestTs = latestTs === null ? r.lastTs : Math.max(latestTs, r.lastTs)
    }
    let totalBytesAll = 0
    for (const { acc } of all) totalBytesAll += acc.totalBytes

    const corpus: AggregateCorpus = {
        totalValidRecords: input.decisionRecordCount,
        distinctKeyedCareers: keyed.length,
        unkeyedRecordCount: input.unkeyedPair ? input.unkeyedPair.report.recordCount : 0,
        careersWithExactlyOneFinalize: oneFinalize,
        careersWithNoFinalize: noFinalize,
        careersWithDuplicateFinalize: dupFinalize,
        scenarioDistribution: groupRows(scenarioGroup),
        traineeDistribution: groupRows(traineeGroup),
        presetDistribution: groupRows(presetGroup),
        parseSchemaFailures: input.parseErrorCount + input.schemaFailureCount,
        consistencyFailures: input.consistencyFailureCount,
        warnings: input.warningCount,
        earliestTs,
        latestTs,
        totalBytes: totalBytesAll,
        meanBytesPerRecord: safeMean(totalBytesAll, input.decisionRecordCount),
        recordsPerCareer: {
            mean: safeMean(sum(perCareerRecordCounts), perCareerRecordCounts.length),
            median: median(perCareerRecordCounts),
            min: perCareerRecordCounts.length > 0 ? Math.min(...perCareerRecordCounts) : 0,
            max: perCareerRecordCounts.length > 0 ? Math.max(...perCareerRecordCounts) : 0,
        },
    }

    // ---- per-scenario (keyed careers grouped by their single scenario) ----
    const byScenario = new Map<string, { acc: CareerAccumulator; report: CareerReport }[]>()
    for (const pair of keyed) {
        const key = singleOr(pair.report.scenarios)
        const list = byScenario.get(key) ?? []
        list.push(pair)
        byScenario.set(key, list)
    }
    const scenarios: AggregateScenario[] = [...byScenario.entries()]
        .sort((a, b) => cmpStr(a[0], b[0]))
        .map(([scenario, pairs]) => {
            const traceRecordCount = sum(pairs.map((p) => p.report.recordCount))
            const actionMap = new Map<string, number>()
            const trainingMap = new Map<string, number>()
            let obsTurn = 0
            let obsStats = 0
            let obsSkill = 0
            let obsApt = 0
            let candSum = 0
            let candRecords = 0
            let totalBytes = 0
            let empty = 0
            let gap = 0
            let consistency = 0
            for (const { acc, report } of pairs) {
                mergeInto(actionMap, report.selectedActionCounts)
                mergeInto(trainingMap, report.trainingSelectionCounts)
                obsTurn += acc.obsTurn
                obsStats += acc.obsStats
                obsSkill += acc.obsSkillPoints
                obsApt += acc.obsAptitudes
                candSum += acc.candSum
                candRecords += acc.candRecords
                totalBytes += acc.totalBytes
                empty += acc.emptySelectionCount
                gap += report.turnGaps.length
                consistency +=
                    report.selectedActionNotInCandidates.length +
                    report.selectedTrainingNotInCandidates.length +
                    report.multipleSelectedActionCandidates.length +
                    report.multipleSelectedTrainingCandidates.length
            }
            const totalAction = mapTotal(actionMap)
            const totalTraining = mapTotal(trainingMap)
            return {
                scenario,
                careerCount: pairs.length,
                traceRecordCount,
                meanRecordsPerCareer: safeMean(traceRecordCount, pairs.length),
                observedTurnRecords: obsTurn,
                observedTurnPct: pct(obsTurn, traceRecordCount),
                gapCount: gap,
                selectedActions: pctRows(actionMap, totalAction),
                selectedTrainings: pctRows(trainingMap, totalTraining),
                observationCoverage: [
                    { flag: "turn", observed: obsTurn, pct: pct(obsTurn, traceRecordCount) },
                    { flag: "stats", observed: obsStats, pct: pct(obsStats, traceRecordCount) },
                    { flag: "skillPoints", observed: obsSkill, pct: pct(obsSkill, traceRecordCount) },
                    { flag: "aptitudes", observed: obsApt, pct: pct(obsApt, traceRecordCount) },
                ],
                emptySelectionCount: empty,
                candidateConsistencyFailureCount: consistency,
                meanCandidateCount: safeMean(candSum, candRecords),
                meanRecordBytes: safeMean(totalBytes, traceRecordCount),
            }
        })

    // ---- action / training distributions (keyed) ----
    const actions = distribution(keyed, (r) => r.selectedActionCounts)
    const trainings = distribution(keyed, (r) => r.trainingSelectionCounts)

    // ---- observations (whole file) ----
    const obsSums: Record<string, number> = { turn: 0, stats: 0, skillPoints: 0, aptitudes: 0 }
    let obsTotalRecords = 0
    for (const { acc } of all) {
        obsSums.turn += acc.obsTurn
        obsSums.stats += acc.obsStats
        obsSums.skillPoints += acc.obsSkillPoints
        obsSums.aptitudes += acc.obsAptitudes
        obsTotalRecords += acc.recordCount
    }
    const observations: AggregateObservations = {
        totalRecords: obsTotalRecords,
        flags: OBS_FLAGS.map((flag) => {
            const observed = obsSums[flag]
            const accField = flag === "turn" ? "obsTurn" : flag === "stats" ? "obsStats" : flag === "skillPoints" ? "obsSkillPoints" : "obsAptitudes"
            const careersWithUnobserved = keyed.filter(({ acc }) => (acc[accField as keyof CareerAccumulator] as number) < acc.recordCount).length
            return {
                flag,
                observed,
                missing: obsTotalRecords - observed,
                pctObserved: pct(observed, obsTotalRecords),
                careersWithUnobserved,
                examples: input.unobservedExamples[flag],
            }
        }),
    }

    // ---- candidate diagnostics (whole file) ----
    const histAll = new Map<number, number>()
    let candSumAll = 0
    let candRecordsAll = 0
    let zeroCand = 0
    let selActionNotRep = 0
    let multiAction = 0
    let multiTraining = 0
    let withScore = 0
    let withGains = 0
    let withFail = 0
    let withHardExcl = 0
    for (const { acc } of all) {
        for (const [n, f] of acc.candHistogram) histAll.set(n, (histAll.get(n) ?? 0) + f)
        candSumAll += acc.candSum
        candRecordsAll += acc.candRecords
        zeroCand += acc.zeroCandidateRecords
        selActionNotRep += acc.selectedActionNotInCandidates.length
        multiAction += acc.multipleSelectedActionCandidates.length
        multiTraining += acc.multipleSelectedTrainingCandidates.length
        withScore += acc.recordsWithTrainingScore
        withGains += acc.recordsWithGains
        withFail += acc.recordsWithFailChance
        withHardExcl += acc.recordsWithHardExcluded
    }
    const candidates: AggregateCandidates = {
        totalRecords: candRecordsAll,
        meanCandidateCount: safeMean(candSumAll, candRecordsAll),
        medianCandidateCount: medianFromHistogram(histAll),
        recordsWithZeroCandidates: zeroCand,
        recordsSelectedActionNotRepresented: selActionNotRep,
        recordsWithMultipleSelectedAction: multiAction,
        recordsWithMultipleSelectedTraining: multiTraining,
        recordsWithTrainingScores: withScore,
        recordsWithGains: withGains,
        recordsWithFailChance: withFail,
        recordsWithHardExcluded: withHardExcl,
        partialActionCandidateCoverage: null,
        note: "partial action-candidate coverage is not derivable from v1: the candidate list is the actions the cascade named, not the full theoretical action space.",
    }

    // ---- outcomes (careers with exactly one career_finalize) ----
    const decisionCounts = new Map<string, number>()
    const sessionCounts = new Map<string, number>()
    const outScenario = new Map<string, number>()
    const outTrainee = new Map<string, number>()
    const outObjective = new Map<string, number>()
    let retryUsed = 0
    const remainingSpVals: number[] = []
    for (const f of finalizeOne) {
        bump(decisionCounts, f.finalizationDecision ?? "(missing)")
        bump(sessionCounts, f.sessionOutcome ?? "(missing)")
        bump(outScenario, f.scenario ?? "(missing)")
        bump(outTrainee, f.trainee ?? "(missing)")
        bump(outObjective, f.objective ?? "(missing)")
        if (f.retryUsed === true) retryUsed++
        if (typeof f.verifiedRemainingSp === "number") remainingSpVals.push(f.verifiedRemainingSp)
    }
    const outcomes: AggregateOutcomes = {
        joinedCareerCount: oneFinalize,
        careersWithNoFinalize: noFinalize,
        careersWithDuplicateFinalize: dupFinalize,
        finalizationDecisionCounts: countRows(decisionCounts),
        sessionOutcomeCounts: countRows(sessionCounts),
        scenarioCounts: countRows(outScenario),
        traineeCounts: countRows(outTrainee),
        objectiveCounts: countRows(outObjective),
        retryUsedCount: retryUsed,
        remainingSp: {
            count: remainingSpVals.length,
            mean: remainingSpVals.length > 0 ? safeMean(sum(remainingSpVals), remainingSpVals.length) : null,
            median: remainingSpVals.length > 0 ? median(remainingSpVals) : null,
            min: remainingSpVals.length > 0 ? Math.min(...remainingSpVals) : null,
            max: remainingSpVals.length > 0 ? Math.max(...remainingSpVals) : null,
        },
        note: "aggregated only over careers with exactly one career_finalize join; scenario/trainee are the finalize row's own values.",
    }

    // ---- per-career table ----
    const careers: AggregateCareerRow[] = keyed.map(({ report }) => {
        const summaries = report.careerToken !== null ? (input.finalizeByToken.get(report.careerToken) ?? []) : []
        const one = summaries.length === 1 ? summaries[0] : null
        const cf =
            report.selectedActionNotInCandidates.length +
            report.selectedTrainingNotInCandidates.length +
            report.multipleSelectedActionCandidates.length +
            report.multipleSelectedTrainingCandidates.length
        let warn = 0
        if (report.duplicateTurns.length > 0) warn++
        if (report.nonMonotonicCount > 0) warn++
        if (report.emptySelectionCount > 0) warn++
        if (report.recordsLackingIdentity > 0) warn++
        if (input.joinAttempted && report.join !== "one") warn++
        return {
            careerToken: report.careerToken ?? "(unkeyed)",
            scenario: singleOr(report.scenarios),
            trainee: singleOr(report.trainees),
            preset: singleOr(report.presets),
            traceCount: report.recordCount,
            turnRange: report.turnRange,
            gapCount: report.turnGaps.length,
            actions: report.selectedActionCounts,
            trainings: report.trainingSelectionCounts,
            join: report.join,
            finalizationDecision: one ? one.finalizationDecision : null,
            sessionOutcome: one ? one.sessionOutcome : null,
            remainingSp: one ? one.verifiedRemainingSp : null,
            warningFailureCount: cf + warn,
        }
    })

    return { mode: "aggregate", corpus, scenarios, actions, trainings, observations, candidates, outcomes, careers }
}

/** Builds a corpus-wide distribution (keyed careers) for a chosen per-career count map (selectedAction or training). */
function distribution(
    keyed: { acc: CareerAccumulator; report: CareerReport }[],
    pick: (r: CareerReport) => Record<string, number>,
): AggregateDistributionRow[] {
    const total = new Map<string, number>()
    const careersWith = new Map<string, Set<string>>()
    const byScenario = new Map<string, Map<string, number>>()
    let grandTotal = 0
    for (const { report } of keyed) {
        const scenario = singleOr(report.scenarios)
        const token = report.careerToken ?? "(unkeyed)"
        for (const [id, count] of Object.entries(pick(report))) {
            total.set(id, (total.get(id) ?? 0) + count)
            grandTotal += count
            const set = careersWith.get(id) ?? new Set<string>()
            set.add(token)
            careersWith.set(id, set)
            const sb = byScenario.get(id) ?? new Map<string, number>()
            sb.set(scenario, (sb.get(scenario) ?? 0) + count)
            byScenario.set(id, sb)
        }
    }
    return [...total.entries()]
        .sort((a, b) => b[1] - a[1] || cmpStr(a[0], b[0]))
        .map(([id, count]) => ({
            id,
            count,
            pct: pct(count, grandTotal),
            careerCount: careersWith.get(id)?.size ?? 0,
            scenarioBreakdown: countRows(byScenario.get(id) ?? new Map()),
        }))
}

// --- small deterministic helpers used only by the aggregate ---

function cmpStr(a: string, b: string): number {
    return a < b ? -1 : a > b ? 1 : 0
}

/** Total of a `seq -> count` map's values, for the per-career state/trace record counts. */
function sumMapValues(m: Map<number, number>): number {
    let t = 0
    for (const v of m.values()) t += v
    return t
}

/** Deterministic order for composite-key diagnostics: careerToken, then seq. */
function cmpKeyRef(a: CareerStateKeyRef, b: CareerStateKeyRef): number {
    return cmpStr(a.careerToken, b.careerToken) || a.seq - b.seq
}

function sum(nums: number[]): number {
    let s = 0
    for (const n of nums) s += n
    return s
}

function safeMean(total: number, count: number): number {
    return count > 0 ? round2(total / count) : 0
}

function round2(x: number): number {
    return Math.round(x * 100) / 100
}

/** Percentage to one decimal place; 0 when the denominator is 0. */
function pct(part: number, whole: number): number {
    return whole > 0 ? Math.round((part / whole) * 1000) / 10 : 0
}

function median(nums: number[]): number {
    if (nums.length === 0) return 0
    const s = [...nums].sort((a, b) => a - b)
    const mid = Math.floor(s.length / 2)
    return s.length % 2 === 1 ? s[mid] : round2((s[mid - 1] + s[mid]) / 2)
}

/** Exact median of the values summarized by a count histogram, without retaining every value. */
function medianFromHistogram(hist: Map<number, number>): number {
    const entries = [...hist.entries()].sort((a, b) => a[0] - b[0])
    const total = sum(entries.map((e) => e[1]))
    if (total === 0) return 0
    const lowerIdx = Math.floor((total - 1) / 2)
    const upperIdx = Math.ceil((total - 1) / 2)
    let seen = 0
    let lo: number | null = null
    let hi: number | null = null
    for (const [val, freq] of entries) {
        const start = seen
        const end = seen + freq - 1
        if (lo === null && lowerIdx >= start && lowerIdx <= end) lo = val
        if (hi === null && upperIdx >= start && upperIdx <= end) hi = val
        seen += freq
        if (lo !== null && hi !== null) break
    }
    return round2(((lo ?? 0) + (hi ?? 0)) / 2)
}

function mergeInto(target: Map<string, number>, source: Record<string, number>): void {
    for (const [k, v] of Object.entries(source)) target.set(k, (target.get(k) ?? 0) + v)
}

function mapTotal(m: Map<string, number>): number {
    let t = 0
    for (const v of m.values()) t += v
    return t
}

function bump(m: Map<string, number>, key: string): void {
    m.set(key, (m.get(key) ?? 0) + 1)
}

function countRows(m: Map<string, number>): CountRow[] {
    return [...m.entries()].sort((a, b) => b[1] - a[1] || cmpStr(a[0], b[0])).map(([value, count]) => ({ value, count }))
}

function pctRows(m: Map<string, number>, total: number): AggregatePctRow[] {
    return [...m.entries()].sort((a, b) => b[1] - a[1] || cmpStr(a[0], b[0])).map(([id, count]) => ({ id, count, pct: pct(count, total) }))
}

function addGroup(m: Map<string, { careers: number; records: number }>, key: string, records: number): void {
    const g = m.get(key) ?? { careers: 0, records: 0 }
    g.careers++
    g.records += records
    m.set(key, g)
}

function groupRows(m: Map<string, { careers: number; records: number }>): CorpusGroupRow[] {
    return [...m.entries()]
        .sort((a, b) => b[1].records - a[1].records || cmpStr(a[0], b[0]))
        .map(([value, g]) => ({ value, careers: g.careers, records: g.records }))
}

/** The single value of a per-career set, or a labeled sentinel when a career shows zero or many (an anomaly). */
function singleOr(values: string[]): string {
    if (values.length === 0) return "(none)"
    if (values.length === 1) return values[0]
    return `(mixed: ${values.join("/")})`
}

/**
 * Renders the aggregate as a deterministic human-readable report. Percentages are shown to one decimal
 * place. Descriptive only; nothing here judges a decision or claims causality.
 */
export function renderAggregateReport(agg: AggregateResult): string {
    const lines: string[] = []
    const push = (s = "") => lines.push(s)
    const c = agg.corpus

    push(`# DecisionTrace corpus aggregate (descriptive only; no decision is scored, no causality claimed)`)
    push()
    push(`## Corpus`)
    push(`- valid decision records: ${c.totalValidRecords} across ${c.distinctKeyedCareers} keyed career(s), ${c.unkeyedRecordCount} unkeyed record(s)`)
    push(`- records/career: mean ${c.recordsPerCareer.mean}, median ${c.recordsPerCareer.median}, min ${c.recordsPerCareer.min}, max ${c.recordsPerCareer.max}`)
    push(`- finalize joins: ${c.careersWithExactlyOneFinalize} exactly-one, ${c.careersWithNoFinalize} none, ${c.careersWithDuplicateFinalize} duplicate`)
    push(`- scenarios: ${groupLine(c.scenarioDistribution)}`)
    push(`- trainees: ${groupLine(c.traineeDistribution)}`)
    push(`- presets: ${groupLine(c.presetDistribution)}`)
    push(`- failures: ${c.parseSchemaFailures} parse/schema, ${c.consistencyFailures} consistency, ${c.warnings} warning(s)`)
    push(`- time: ${fmtTs(c.earliestTs)} .. ${fmtTs(c.latestTs)}`)
    push(`- bytes: total ${c.totalBytes}, mean/record ${c.meanBytesPerRecord}`)

    push()
    push(`## Per scenario`)
    for (const s of agg.scenarios) {
        push(`### ${s.scenario}`)
        push(`- careers: ${s.careerCount}, trace records: ${s.traceRecordCount} (mean ${s.meanRecordsPerCareer}/career)`)
        push(`- observed-turn coverage: ${s.observedTurnRecords}/${s.traceRecordCount} (${s.observedTurnPct}%), turn gaps: ${s.gapCount}`)
        push(`- actions: ${pctRowLine(s.selectedActions)}`)
        push(`- trainings: ${pctRowLine(s.selectedTrainings)}`)
        push(`- observation reads: ${s.observationCoverage.map((o) => `${o.flag} ${o.observed} (${o.pct}%)`).join(", ")}`)
        push(`- empty selections: ${s.emptySelectionCount}, candidate consistency failures: ${s.candidateConsistencyFailureCount}`)
        push(`- candidates/trace: mean ${s.meanCandidateCount}, bytes/record: mean ${s.meanRecordBytes}`)
    }

    push()
    push(`## Action distribution (of ${sumRows(agg.actions)} traces with a committed action)`)
    for (const a of agg.actions) push(`- ${a.id}: ${a.count} (${a.pct}%) in ${a.careerCount} career(s) [${countLine(a.scenarioBreakdown)}]`)

    push()
    push(`## Training distribution (of ${sumRows(agg.trainings)} traces with a committed training)`)
    for (const t of agg.trainings) push(`- ${t.id}: ${t.count} (${t.pct}%) in ${t.careerCount} career(s) [${countLine(t.scenarioBreakdown)}]`)

    push()
    push(`## Observation quality (whole file, ${agg.observations.totalRecords} record(s))`)
    for (const f of agg.observations.flags) {
        push(`- ${f.flag}: ${f.observed} observed (${f.pctObserved}%), ${f.missing} unobserved, in ${f.careersWithUnobserved} career(s)`)
        if (f.examples.length > 0) push(`  - e.g. ${f.examples.map((e) => `${e.careerToken ?? "(unkeyed)"}@turn ${e.turn ?? "?"}`).join("; ")}`)
    }

    push()
    push(`## Candidate diagnostics (whole file, ${agg.candidates.totalRecords} record(s))`)
    const cd = agg.candidates
    push(`- candidates/record: mean ${cd.meanCandidateCount}, median ${cd.medianCandidateCount}`)
    push(`- zero-candidate records: ${cd.recordsWithZeroCandidates}`)
    push(`- selected action not represented: ${cd.recordsSelectedActionNotRepresented}, >1 selected action: ${cd.recordsWithMultipleSelectedAction}, >1 selected training: ${cd.recordsWithMultipleSelectedTraining}`)
    push(`- records carrying training scores: ${cd.recordsWithTrainingScores}, gains: ${cd.recordsWithGains}, failChance: ${cd.recordsWithFailChance}, hard-excluded candidate(s): ${cd.recordsWithHardExcluded}`)
    push(`- partial action-candidate coverage: not derivable from v1 (${cd.note})`)

    push()
    push(`## Career-finalize outcomes (denominator: ${agg.outcomes.joinedCareerCount} 1:1-joined career(s))`)
    const o = agg.outcomes
    push(`- unjoined: ${o.careersWithNoFinalize} no finalize, ${o.careersWithDuplicateFinalize} duplicate`)
    push(`- finalization decision: ${countLine(o.finalizationDecisionCounts)}`)
    push(`- session outcome: ${countLine(o.sessionOutcomeCounts)}`)
    push(`- scenario (finalize field): ${countLine(o.scenarioCounts)}`)
    push(`- trainee (finalize field): ${countLine(o.traineeCounts)}`)
    push(`- objective: ${countLine(o.objectiveCounts)}`)
    push(`- retry used: ${o.retryUsedCount}`)
    push(
        `- remaining SP (n=${o.remainingSp.count}): ${o.remainingSp.count > 0 ? `mean ${o.remainingSp.mean}, median ${o.remainingSp.median}, min ${o.remainingSp.min}, max ${o.remainingSp.max}` : "-"}`,
    )

    push()
    push(`## Careers`)
    for (const cr of agg.careers) {
        push(`### ${cr.careerToken}`)
        push(`- scenario: ${cr.scenario}, trainee: ${cr.trainee}, preset: ${cr.preset}`)
        push(`- traces: ${cr.traceCount}, turns: ${cr.turnRange ? `${cr.turnRange[0]}..${cr.turnRange[1]}` : "none observed"}, gaps: ${cr.gapCount}`)
        push(`- actions: ${fmtCounts(cr.actions)}`)
        push(`- trainings: ${fmtCounts(cr.trainings)}`)
        push(
            `- finalize: join ${cr.join}${cr.finalizationDecision ? `, decision ${cr.finalizationDecision}` : ""}${cr.sessionOutcome ? `, outcome ${cr.sessionOutcome}` : ""}${cr.remainingSp !== null ? `, remaining SP ${cr.remainingSp}` : ""}`,
        )
        push(`- warnings/failures: ${cr.warningFailureCount}`)
    }

    return lines.join("\n")
}

function groupLine(rows: CorpusGroupRow[]): string {
    return rows.length === 0 ? "-" : rows.map((r) => `${r.value} (${r.careers}c/${r.records}r)`).join(", ")
}

function countLine(rows: CountRow[]): string {
    return rows.length === 0 ? "-" : rows.map((r) => `${r.value}:${r.count}`).join(", ")
}

function pctRowLine(rows: AggregatePctRow[]): string {
    return rows.length === 0 ? "-" : rows.map((r) => `${r.id}:${r.count} (${r.pct}%)`).join(", ")
}

function sumRows(rows: { count: number }[]): number {
    return sum(rows.map((r) => r.count))
}
