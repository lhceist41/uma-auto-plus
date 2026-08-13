// Telemetry corpus collection - pure helpers for the durable read-only archive workflow (the CLI that
// wraps these lives in scripts/collect-telemetry.mjs). Everything here is deterministic and side-effect
// free: label sanitizing, bundle naming, adb argv construction (pull-only, no root), exact-byte hashing,
// and factual careerToken+seq corpus accounting. No filesystem, no child process, no clock: the CLI owns
// all IO and passes raw bytes / a UTC timestamp in. Nothing here mutates device telemetry or its bytes.

import { createHash } from "node:crypto"

/** Manifest schema version. Additive fields keep this at "1". */
export const MANIFEST_VERSION = "1"

/**
 * The app external telemetry directory on device, matching OutcomeCorpus.kt
 * (`getExternalFilesDir(null)/outcomes/`) for applicationId com.lhceist41.uma_auto_plus. Not baked into any
 * command as a device serial; the serial is supplied separately via --device.
 */
export const DEVICE_OUTCOMES_DIR = "/storage/emulated/0/Android/data/com.lhceist41.uma_auto_plus/files/outcomes"

/** The three telemetry file names the writer produces. decisions + careerState are required; careers optional. */
export const TELEMETRY_FILENAMES = { decisions: "decisions.jsonl", careerState: "career_state.jsonl", careers: "careers.jsonl", shadowAdvisor: "shadow_advisor.jsonl" } as const
export const REQUIRED_FILENAMES: readonly string[] = [TELEMETRY_FILENAMES.decisions, TELEMETRY_FILENAMES.careerState]

/** The device path to pull one telemetry file from (forward-slash, POSIX device path). */
export function remoteTelemetryPath(filename: string): string {
    return `${DEVICE_OUTCOMES_DIR}/${filename}`
}

/**
 * Sanitizes a human label into a safe path segment: only [A-Za-z0-9._-] survive, runs of anything else
 * collapse to a single "-", and leading/trailing dot/dash are trimmed. Rejects an empty result, a `..`
 * traversal token, and a segment that is only dots. Never yields a path separator or a shell metacharacter.
 */
export function sanitizeLabel(label: string): string {
    const trimmed = label.trim()
    if (trimmed.length === 0) throw new Error("label is empty")
    const slug = trimmed.replace(/[^A-Za-z0-9._-]+/g, "-").replace(/^[-.]+|[-.]+$/g, "")
    if (slug.length === 0) throw new Error(`label "${label}" sanitizes to empty`)
    if (slug.includes("..")) throw new Error(`label "${label}" produces a path-traversal token`)
    if (/^\.+$/.test(slug)) throw new Error(`label "${label}" is only dots`)
    if (slug.length > 80) throw new Error(`label "${label}" is too long after sanitizing (${slug.length} > 80)`)
    return slug
}

/**
 * Deterministic bundle id `<UTC-date>-<sanitized-label>`. The UTC date is the first 10 chars of an ISO
 * timestamp the CLI supplies (never read from a clock here, so tests are deterministic). No absolute path
 * or user directory is ever embedded.
 */
export function bundleId(nowUtcIso: string, label: string): string {
    const date = nowUtcIso.slice(0, 10)
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error(`invalid UTC timestamp: ${nowUtcIso}`)
    return `${date}-${sanitizeLabel(label)}`
}

/**
 * Builds the argv for a read-only `adb ... pull <remote> <local>` (executed by the CLI via execFile with no
 * shell, so no metacharacter can reach a shell). A device serial, when given, becomes `-s <serial>`. This
 * function can ONLY ever produce a pull; it has no branch that emits su/root/shell/rm/chmod/chown.
 */
export function buildAdbPullArgs(device: string | null, remotePath: string, localDest: string): string[] {
    const args: string[] = []
    if (device !== null && device.length > 0) args.push("-s", device)
    args.push("pull", remotePath, localDest)
    return args
}

/** Lowercase hex SHA-256 of exact bytes. */
export function sha256Hex(bytes: Uint8Array): string {
    return createHash("sha256").update(bytes).digest("hex")
}

export interface ParsedJsonl {
    records: Record<string, unknown>[]
    recordCount: number
    malformedLineCount: number
    malformedLineNumbers: number[]
}

/**
 * Parses JSONL from exact bytes WITHOUT normalizing them (the archived file keeps its original bytes; this
 * only reads). A blank line is ignored; a non-blank line that is not a JSON object is counted as malformed
 * and never silently dropped from the totals.
 */
export function parseJsonl(bytes: Uint8Array): ParsedJsonl {
    const text = Buffer.from(bytes).toString("utf8")
    const records: Record<string, unknown>[] = []
    const malformedLineNumbers: number[] = []
    const lines = text.split(/\r?\n/)
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i]
        if (line.trim().length === 0) continue
        try {
            const parsed = JSON.parse(line)
            if (parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)) records.push(parsed as Record<string, unknown>)
            else malformedLineNumbers.push(i + 1)
        } catch {
            malformedLineNumbers.push(i + 1)
        }
    }
    return { records, recordCount: records.length, malformedLineCount: malformedLineNumbers.length, malformedLineNumbers }
}

export interface FileMetadata {
    filename: string
    byteSize: number
    sha256: string
    recordCount: number
    malformedLineCount: number
}

/** File metadata over EXACT archived bytes: size, SHA-256, and record/malformed counts. */
export function fileMetadata(filename: string, bytes: Uint8Array, parsed: ParsedJsonl): FileMetadata {
    return { filename, byteSize: bytes.length, sha256: sha256Hex(bytes), recordCount: parsed.recordCount, malformedLineCount: parsed.malformedLineCount }
}

// ---- factual careerToken + seq accounting (never joins by turn; does NOT invoke ReplayLab) ----

function asString(value: unknown): string | null {
    return typeof value === "string" && value.length > 0 ? value : null
}
function asInt(value: unknown): number | null {
    return typeof value === "number" && Number.isInteger(value) ? value : null
}
function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

export interface TokenInventory {
    careerToken: string
    decisionRecordCount: number
    stateRecordCount: number
    sequencedDecisionCount: number
    sequencedStateCount: number
    sharedSeqCount: number
    decisionOnlySeqCount: number
    stateOnlySeqCount: number
    duplicateDecisionSeqCount: number
    duplicateStateSeqCount: number
    scenarios: string[]
    trainees: string[]
}

export interface CorpusGlobalSummary {
    decisionRecordCount: number
    stateRecordCount: number
    careerRecordCount: number | null
    distinctDecisionCareerTokenCount: number
    distinctStateCareerTokenCount: number
    pairedCareerTokenCount: number
    sharedSeqCount: number
    seqLessDecisionCount: number
    seqLessStateCount: number
    duplicateDecisionSeqCount: number
    duplicateStateSeqCount: number
}

export interface CorpusAnalysis {
    tokens: TokenInventory[]
    global: CorpusGlobalSummary
}

interface TokenAcc {
    decisionRecordCount: number
    stateRecordCount: number
    decisionSeqs: number[]
    stateSeqs: number[]
    scenarios: Set<string>
    trainees: Set<string>
}

function newTokenAcc(): TokenAcc {
    return { decisionRecordCount: 0, stateRecordCount: 0, decisionSeqs: [], stateSeqs: [], scenarios: new Set(), trainees: new Set() }
}

function seqStats(seqs: number[]): { sequenced: number; duplicates: number; distinct: Set<number> } {
    const counts = new Map<number, number>()
    for (const s of seqs) counts.set(s, (counts.get(s) ?? 0) + 1)
    let duplicates = 0
    for (const c of counts.values()) if (c > 1) duplicates++
    return { sequenced: seqs.length, duplicates, distinct: new Set(counts.keys()) }
}

/**
 * Factual per-careerToken and global accounting over the decision and state streams. Pairing is by
 * careerToken then seq ONLY; a shared seq means the same (careerToken, seq) appears in both streams. Same
 * seq under two different careerTokens stays isolated. This does not classify anything JOINED (ReplayLab is
 * not invoked); it reports the raw factual counts a curator uses to judge S2-usability.
 */
export function analyzeCorpus(decisionRecords: readonly unknown[], stateRecords: readonly unknown[], careerRecords: readonly unknown[] | null): CorpusAnalysis {
    const byToken = new Map<string, TokenAcc>()
    const ensure = (token: string): TokenAcc => {
        let acc = byToken.get(token)
        if (!acc) {
            acc = newTokenAcc()
            byToken.set(token, acc)
        }
        return acc
    }

    let seqLessDecisionCount = 0
    let seqLessStateCount = 0
    const decisionTokens = new Set<string>()
    const stateTokens = new Set<string>()

    for (const rec of decisionRecords) {
        if (!isObject(rec)) continue
        const token = asString(rec.careerToken)
        if (token === null) continue
        decisionTokens.add(token)
        const acc = ensure(token)
        acc.decisionRecordCount++
        const seq = asInt(rec.seq)
        if (seq === null) seqLessDecisionCount++
        else acc.decisionSeqs.push(seq)
        const scenario = asString(rec.scenario)
        if (scenario !== null) acc.scenarios.add(scenario)
        const trainee = asString(rec.trainee)
        if (trainee !== null) acc.trainees.add(trainee)
    }

    for (const rec of stateRecords) {
        if (!isObject(rec)) continue
        const identity = isObject(rec.identity) ? rec.identity : null
        const token = identity ? asString(identity.careerToken) : null
        if (token === null) continue
        stateTokens.add(token)
        const acc = ensure(token)
        acc.stateRecordCount++
        const seq = asInt(rec.seq)
        if (seq === null) seqLessStateCount++
        else acc.stateSeqs.push(seq)
        const scenarioObj = isObject(rec.scenario) ? rec.scenario : null
        const scenarioType = scenarioObj ? asString(scenarioObj.type) : null
        if (scenarioType !== null) acc.scenarios.add(scenarioType)
        const trainee = identity ? asString(identity.trainee) : null
        if (trainee !== null) acc.trainees.add(trainee)
    }

    let globalShared = 0
    let globalDupDecision = 0
    let globalDupState = 0
    const tokens: TokenInventory[] = [...byToken.keys()].sort().map((careerToken) => {
        const acc = byToken.get(careerToken) as TokenAcc
        const d = seqStats(acc.decisionSeqs)
        const s = seqStats(acc.stateSeqs)
        let shared = 0
        let decisionOnly = 0
        for (const seq of d.distinct) {
            if (s.distinct.has(seq)) shared++
            else decisionOnly++
        }
        let stateOnly = 0
        for (const seq of s.distinct) if (!d.distinct.has(seq)) stateOnly++
        globalShared += shared
        globalDupDecision += d.duplicates
        globalDupState += s.duplicates
        return {
            careerToken,
            decisionRecordCount: acc.decisionRecordCount,
            stateRecordCount: acc.stateRecordCount,
            sequencedDecisionCount: d.sequenced,
            sequencedStateCount: s.sequenced,
            sharedSeqCount: shared,
            decisionOnlySeqCount: decisionOnly,
            stateOnlySeqCount: stateOnly,
            duplicateDecisionSeqCount: d.duplicates,
            duplicateStateSeqCount: s.duplicates,
            scenarios: [...acc.scenarios].sort(),
            trainees: [...acc.trainees].sort(),
        }
    })

    let pairedCareerTokenCount = 0
    for (const token of decisionTokens) if (stateTokens.has(token)) pairedCareerTokenCount++

    return {
        tokens,
        global: {
            decisionRecordCount: decisionRecords.length,
            stateRecordCount: stateRecords.length,
            careerRecordCount: careerRecords === null ? null : careerRecords.length,
            distinctDecisionCareerTokenCount: decisionTokens.size,
            distinctStateCareerTokenCount: stateTokens.size,
            pairedCareerTokenCount,
            sharedSeqCount: globalShared,
            seqLessDecisionCount,
            seqLessStateCount,
            duplicateDecisionSeqCount: globalDupDecision,
            duplicateStateSeqCount: globalDupState,
        },
    }
}

// ---- manifest assembly ----

export type CollectionMode = "adbPull" | "fromDir"

export interface ManifestSource {
    mode: CollectionMode
    deviceSerial: string | null
    deviceTelemetryPath: string | null
    fromDir: string | null
}

export interface BuildManifestParams {
    label: string
    sanitizedLabel: string
    bundleId: string
    collectedAtUtc: string
    source: ManifestSource
    files: FileMetadata[]
    analysis: CorpusAnalysis
    totalByteSize: number
}

export interface Manifest {
    manifestVersion: string
    label: string
    sanitizedLabel: string
    bundleId: string
    collectedAtUtc: string
    collectionMode: CollectionMode
    source: ManifestSource
    filePresence: Record<string, boolean>
    files: FileMetadata[]
    careerTokens: TokenInventory[]
    summary: CorpusGlobalSummary & { totalByteSize: number }
}

/** Assembles the deterministic manifest. Files are ordered by filename; token/seq sections are pre-sorted. */
export function buildManifest(params: BuildManifestParams): Manifest {
    const files = [...params.files].sort((a, b) => a.filename.localeCompare(b.filename))
    const present = new Set(files.map((f) => f.filename))
    return {
        manifestVersion: MANIFEST_VERSION,
        label: params.label,
        sanitizedLabel: params.sanitizedLabel,
        bundleId: params.bundleId,
        collectedAtUtc: params.collectedAtUtc,
        collectionMode: params.source.mode,
        source: params.source,
        filePresence: {
            [TELEMETRY_FILENAMES.decisions]: present.has(TELEMETRY_FILENAMES.decisions),
            [TELEMETRY_FILENAMES.careerState]: present.has(TELEMETRY_FILENAMES.careerState),
            [TELEMETRY_FILENAMES.careers]: present.has(TELEMETRY_FILENAMES.careers),
            [TELEMETRY_FILENAMES.shadowAdvisor]: present.has(TELEMETRY_FILENAMES.shadowAdvisor),
        },
        files,
        careerTokens: params.analysis.tokens,
        summary: { ...params.analysis.global, totalByteSize: params.totalByteSize },
    }
}
