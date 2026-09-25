// ParentLab PL-R1c inspiration ingest - the read side of the on-device `veteran_inspiration` /
// `veteran_inspiration_scan` records (veteran_inspiration.jsonl). Pure, offline, deterministic,
// tolerant of malformed lines like parseCorpus.
//
// This is a third artifact, deliberately not merged into either of the two that exist. The PL-3
// Veteran library reconstructs every career the bot ever produced; the PL-R1b roster snapshot says
// what the account owns right now; this says what each owned Veteran can PASS ON, and what ancestry
// already sits behind it. It joins to the roster snapshot by `rosterFingerprint` and to nothing else.
//
// The one rule this module exists to enforce: a Veteran's factor set is usable only when the device
// proved it read the whole factor list - started at the top, saw the end, merged with no gap, read
// every name and every star. A partial factor list is not a smaller answer, it is a wrong one: a
// retention decision made on half a Veteran's sparks is worse than one made on none.

import type { RosterSnapshot } from "./roster.ts"

/** Library schema discriminator + version for the inspiration snapshot. Separate from the roster and
 * Veteran schemas: neither of those is touched by this stage. */
export const PARENTLAB_INSPIRATION_SCHEMA = "parent_lab_inspiration" as const
export const PARENTLAB_INSPIRATION_SCHEMA_VERSION = 1 as const

/** Why the device stopped reading one Veteran's panel. Mirrors the Kotlin
 * `InspirationReadTermination`, lower-cased. */
export type InspirationReadTermination =
    | "reached_bottom"
    | "reached_factor_list_end"
    | "no_scroll_needed"
    | "scroll_budget_exhausted"
    | "stalled"
    | "panel_not_ready"
    | "not_at_top"

/** Why a batch of captures stopped. Mirrors the Kotlin `InspirationScanTermination`. */
export type InspirationScanTermination =
    | "count_reached"
    | "cycle_closed"
    | "single_card"
    | "empty_list"
    | "entry_limit_reached"
    | "chevron_end"
    | "unexpected_screen"
    | "precondition_failed"
    | "hard_bound_reached"

/** Which column of the two-column grid the card occupied. */
export type InspirationColumn = "left" | "right"

/**
 * One factor card as the device read it.
 *
 * `kind` and `stars` are pixel-classified and are the authoritative fields: two reads of the same
 * panel agree on them exactly. `displayName` is the raw OCR and does NOT have that guarantee - about
 * one factor name in thirty comes back a glyph different on a re-read. From schema 2 the device snaps
 * the raw read onto the canonical factor domain: `canonicalName` is the resolved identity (null when
 * it did not resolve), `factorFingerprint` is the semantic `kind:CANON:stars` token built from it
 * (null when unresolved - fail closed), and `structuralFingerprint` (`kind:stars`) is the name-free
 * fallback that is always present and always stable.
 */
export interface InspirationFactorRecord {
    readonly rowIndex: number
    readonly column: InspirationColumn | null
    readonly kind: string
    readonly displayName: string
    readonly normalizedName: string
    readonly stars: number
    /** Resolved canonical name, or null when the raw OCR did not snap onto the domain. */
    readonly canonicalName: string | null
    /** How the canonical name was accepted: "strong" | "margin" | "abbreviation" | "reject" (null on pre-schema-2 records). */
    readonly canonicalPath: string | null
    /** Semantic token `kind:CANON:stars`, or null when the factor did not resolve. */
    readonly factorFingerprint: string | null
    /** Name-free `kind:stars` token, always present. */
    readonly structuralFingerprint: string
    readonly ambiguous: boolean
}

/** One Legacy Origin ancestor. `ancestorIndex` is its position in this Veteran's own panel and is not
 * a game identifier: the panel shows a portrait and a rank medal but no name. */
export interface InspirationAncestorRecord {
    readonly ancestorIndex: number
    readonly portraitObserved: boolean
    /** Always null today: the medal is a small stylized badge the calibrated classifier does not cover. */
    readonly rank: string | null
    readonly factorCount: number
    /** Trusted canonical set fingerprint, or null when any factor is unresolved (fail closed). */
    readonly ancestorFactorFingerprint: string | null
    /** Name-free structural set fingerprint, always present from schema 2. */
    readonly ancestorStructuralFingerprint: string | null
    /** Whether every factor resolved, so the canonical fingerprint is a trusted identity. */
    readonly factorSetTrusted: boolean
    readonly factors: readonly InspirationFactorRecord[]
}

/** The device's own measurements of how the traversal went, kept so an incomplete read can be
 * diagnosed from the corpus instead of by re-walking the roster. */
export interface InspirationDiagnosticsRecord {
    readonly frames: number
    readonly swipes: number
    readonly startedAtTop: boolean
    readonly reachedBottom: boolean
    readonly factorListEndObserved: boolean
    readonly gapFrames: number
    readonly spacingBreaks: number
    readonly alignmentFailures: number
    readonly unsettledFrames: number
    readonly deadReckonedFrames: number
    /** Height of the WHOLE panel, which below the factors includes an inspiration-usage history that
     * can be an order of magnitude taller than them. Not the factor list's height. */
    readonly scrollbarContentHeight: number | null
    readonly observedContentHeight: number | null
    readonly rowsAccepted: number
    readonly clippedRowsRejected: number
    readonly leadingPartialBlockRows: number
    readonly blocksObserved: number
}

/** One `veteran_inspiration` record as written by the device. */
export interface VeteranInspirationRecord {
    readonly type: "veteran_inspiration"
    readonly schemaVersion: number
    readonly scanId: string
    readonly scanIndex: number | null
    readonly observedAt: number | null
    /** The roster identity this evidence attaches to. Null when the entry's own identity fields did
     * not all resolve, in which case the factors were still read but cannot be attributed. */
    readonly rosterFingerprint: string | null
    readonly character: string | null
    readonly outfit: string | null
    readonly rank: string | null
    readonly selfPortraitObserved: boolean
    readonly selfFactorCount: number
    /** Trusted canonical fingerprint of the Veteran's own Sparks, or null when any factor is unresolved. */
    readonly selfFactorFingerprint: string | null
    /** Name-free structural fingerprint of the Veteran's own Sparks, always present from schema 2. */
    readonly selfStructuralFingerprint: string | null
    /** Whether every self factor resolved, so the self canonical fingerprint is trusted. */
    readonly selfFactorSetTrusted: boolean
    readonly selfFactors: readonly InspirationFactorRecord[]
    /** Whether the persisted self-factor declarations agree with the parsed producer-shaped rows. */
    readonly selfFactorContentConsistent: boolean
    readonly canonicalDeclarationsConsistent: boolean
    readonly legacyAncestors: readonly InspirationAncestorRecord[]
    readonly termination: InspirationReadTermination | null
    /** The single flag a consumer should gate on. */
    readonly sparkCaptureComplete: boolean
    /** Whether asserted completeness is supported by the producer's persisted traversal evidence. */
    readonly sparkCaptureConsistent: boolean
    readonly screenReadCompleteness: number
    readonly unresolvedFields: readonly string[]
    readonly diagnostics: InspirationDiagnosticsRecord | null
    readonly file?: string
    readonly lineNumber?: number
}

/** One `veteran_inspiration_scan` header: a batch bound to one current-roster state. */
export interface InspirationScanRecord {
    readonly type: "veteran_inspiration_scan"
    readonly schemaVersion: number
    readonly scanId: string
    readonly startedAt: number | null
    readonly completedAt: number | null
    readonly registeredUsedAtStart: number | null
    readonly registeredUsedAtEnd: number | null
    readonly registeredCapacity: number | null
    readonly filtersOff: boolean | null
    readonly sortKey: string | null
    readonly sortDirection: string | null
    /** Requires a verified full pager cycle and stable unfiltered roster state. */
    readonly snapshotCompatibility: boolean
    readonly pagerCycleClosed: boolean
    readonly entryLimit: number | null
    readonly startIndex: number | null
    readonly entriesCaptured: number
    readonly entriesComplete: number
    readonly terminationReason: InspirationScanTermination | null
    readonly app: string | null
    readonly file?: string
    readonly lineNumber?: number
}

export interface ParsedInspiration {
    readonly scans: readonly InspirationScanRecord[]
    readonly entries: readonly VeteranInspirationRecord[]
    /** Lines that were valid JSON but not a usable record. Surfaced, never silently dropped. */
    readonly malformedRecords: number
}

const READ_TERMINATIONS = new Set<InspirationReadTermination>([
    "reached_bottom",
    "reached_factor_list_end",
    "no_scroll_needed",
    "scroll_budget_exhausted",
    "stalled",
    "panel_not_ready",
    "not_at_top",
])

const SCAN_TERMINATIONS = new Set<InspirationScanTermination>([
    "count_reached",
    "cycle_closed",
    "single_card",
    "empty_list",
    "entry_limit_reached",
    "chevron_end",
    "unexpected_screen",
    "precondition_failed",
    "hard_bound_reached",
])

function exactInteger(value: unknown): number | null {
    return typeof value === "number" && Number.isSafeInteger(value) ? value : null
}

function num(v: unknown): number | null {
    if (v === null || v === undefined || v === "") return null
    const n = Number(v)
    return Number.isFinite(n) ? n : null
}

function str(v: unknown): string | null {
    return typeof v === "string" && v.length > 0 ? v : null
}

function bool(v: unknown): boolean {
    return v === true
}

const FACTOR_KINDS = new Set(["stat", "aptitude", "unique", "white"])
const RESOLVED_CANONICAL_PATHS = new Set(["strong", "margin", "abbreviation", "display_alias"])
const TRAILING_GRADE_MARKER = /\s+[O0*@()\u00a9\u00b0\u25cb\u25ce\u2605\u2606]+$/u

function normalizeFactorName(raw: string): string {
    return raw.trim().replace(/\s+/g, " ").toUpperCase().replace(TRAILING_GRADE_MARKER, "")
}

function canonicalFactorToken(factor: InspirationFactorRecord): string | null {
    return factor.canonicalName === null ? null : `${factor.kind}:${normalizeFactorName(factor.canonicalName)}:${factor.stars}`
}

function canonicalFactorSetFingerprint(factors: readonly InspirationFactorRecord[]): string | null {
    if (factors.length === 0) return null
    const tokens: string[] = []
    for (const factor of factors) {
        const token = canonicalFactorToken(factor)
        if (token === null) return null
        tokens.push(token)
    }
    return tokens.sort().join("|")
}

function structuralFactorSetFingerprint(factors: readonly InspirationFactorRecord[]): string {
    return factors.map((factor) => `${factor.kind}:${factor.stars}`).sort().join("|")
}

interface ParsedFactors {
    readonly records: readonly InspirationFactorRecord[]
    readonly sourceCount: number
    readonly producerShapeValid: boolean
}

function parseFactors(v: unknown): ParsedFactors {
    if (!Array.isArray(v)) return { records: [], sourceCount: 0, producerShapeValid: false }
    const out: InspirationFactorRecord[] = []
    const cells = new Set<string>()
    let producerShapeValid = v.length > 0
    for (const raw of v) {
        if (typeof raw !== "object" || raw === null) {
            producerShapeValid = false
            continue
        }
        const r = raw as Record<string, unknown>
        const kind = str(r.kind)
        if (!kind || !FACTOR_KINDS.has(kind)) {
            producerShapeValid = false
            continue
        }
        const column = str(r.column)
        const stars = exactInteger(r.stars)
        const rowIndex = exactInteger(r.rowIndex)
        const cell = `${rowIndex}:${column}`
        if (cells.has(cell)) producerShapeValid = false
        cells.add(cell)
        const canonicalName = str(r.canonicalName)
        const canonicalPath = str(r.canonicalPath)
        const factorFingerprint = str(r.factorFingerprint)
        const structuralFingerprint = str(r.structuralFingerprint)
        const parsed: InspirationFactorRecord = {
            rowIndex: rowIndex ?? 0,
            column: column === "left" || column === "right" ? column : null,
            kind,
            displayName: typeof r.displayName === "string" ? r.displayName : "",
            normalizedName: typeof r.normalizedName === "string" ? r.normalizedName : "",
            stars: stars ?? 0,
            canonicalName,
            canonicalPath,
            factorFingerprint,
            structuralFingerprint: structuralFingerprint ?? `${kind}:${stars ?? 0}`,
            ambiguous: bool(r.ambiguous),
        }
        const expectedCanonical = canonicalFactorToken(parsed)
        const expectedStructural = `${kind}:${parsed.stars}`
        const canonicalFieldsAgree = expectedCanonical === null
            ? factorFingerprint === null && canonicalPath === "reject"
            : factorFingerprint === expectedCanonical && canonicalPath !== null && RESOLVED_CANONICAL_PATHS.has(canonicalPath)
        if (rowIndex === null || rowIndex < 0 || parsed.column === null || stars === null || stars < 0 || stars > 3 ||
            typeof r.displayName !== "string" || typeof r.normalizedName !== "string" ||
            (r.ambiguous !== undefined && typeof r.ambiguous !== "boolean") ||
            structuralFingerprint !== expectedStructural || !canonicalFieldsAgree) {
            producerShapeValid = false
        }
        out.push(parsed)
    }
    const rows = new Set(out.map((factor) => factor.rowIndex))
    if ([...rows].some((row) => row >= rows.size || !cells.has(`${row}:left`))) producerShapeValid = false
    return { records: out, sourceCount: v.length, producerShapeValid }
}

interface ParsedAncestors {
    readonly records: readonly InspirationAncestorRecord[]
    readonly producerShapeValid: boolean
}

function parseAncestors(v: unknown): ParsedAncestors {
    if (!Array.isArray(v)) return { records: [], producerShapeValid: false }
    const out: InspirationAncestorRecord[] = []
    const indexes = new Set<number>()
    let producerShapeValid = true
    for (const raw of v) {
        if (typeof raw !== "object" || raw === null) {
            producerShapeValid = false
            continue
        }
        const r = raw as Record<string, unknown>
        const parsedFactors = parseFactors(r.factors)
        const factors = parsedFactors.records
        const factorCount = exactInteger(r.factorCount)
        const expectedCanonical = canonicalFactorSetFingerprint(factors)
        const expectedStructural = structuralFactorSetFingerprint(factors)
        const factorSetTrusted = bool(r.factorSetTrusted)
        const ancestorFactorFingerprint = str(r.ancestorFactorFingerprint)
        const rawStructural = typeof r.ancestorStructuralFingerprint === "string" ? r.ancestorStructuralFingerprint : null
        const ancestorIndex = exactInteger(r.ancestorIndex)
        if (!parsedFactors.producerShapeValid || factorCount !== parsedFactors.sourceCount || factorCount !== factors.length ||
            ancestorIndex === null || ancestorIndex < 0 || ancestorIndex >= v.length || indexes.has(ancestorIndex) ||
            typeof r.portraitObserved !== "boolean" ||
            typeof r.factorSetTrusted !== "boolean" || factorSetTrusted !== (expectedCanonical !== null) ||
            ancestorFactorFingerprint !== expectedCanonical || rawStructural !== expectedStructural) {
            producerShapeValid = false
        }
        if (ancestorIndex !== null) indexes.add(ancestorIndex)
        out.push({
            ancestorIndex: ancestorIndex ?? out.length,
            portraitObserved: bool(r.portraitObserved),
            rank: str(r.rank),
            factorCount: factorCount ?? factors.length,
            ancestorFactorFingerprint,
            ancestorStructuralFingerprint: rawStructural,
            factorSetTrusted,
            factors,
        })
    }
    return { records: out.sort((a, b) => a.ancestorIndex - b.ancestorIndex), producerShapeValid }
}

function parseDiagnostics(v: unknown): InspirationDiagnosticsRecord | null {
    if (typeof v !== "object" || v === null) return null
    const r = v as Record<string, unknown>
    return {
        frames: num(r.frames) ?? 0,
        swipes: num(r.swipes) ?? 0,
        startedAtTop: bool(r.startedAtTop),
        reachedBottom: bool(r.reachedBottom),
        factorListEndObserved: bool(r.factorListEndObserved),
        gapFrames: num(r.gapFrames) ?? 0,
        spacingBreaks: num(r.spacingBreaks) ?? 0,
        alignmentFailures: num(r.alignmentFailures) ?? 0,
        unsettledFrames: num(r.unsettledFrames) ?? 0,
        deadReckonedFrames: num(r.deadReckonedFrames) ?? 0,
        scrollbarContentHeight: num(r.scrollbarContentHeight),
        observedContentHeight: num(r.observedContentHeight),
        rowsAccepted: num(r.rowsAccepted) ?? 0,
        clippedRowsRejected: num(r.clippedRowsRejected) ?? 0,
        leadingPartialBlockRows: num(r.leadingPartialBlockRows) ?? 0,
        blocksObserved: num(r.blocksObserved) ?? 0,
    }
}

function blocksCompleteCapture(field: string): boolean {
    return field === "startedAtTop" || field === "contentGap" || field === "rowSpacing" ||
        field === "leadingPartialBlock" || field === "selfSparks" || field === "factorListEnd" ||
        field.startsWith("factorName@") || field.startsWith("factorStars@")
}

function canonicalDeclarationsAgree(factors: readonly InspirationFactorRecord[], unresolvedFields: readonly string[]): boolean {
    // Locations omit the block identity, so equal locations from different blocks retain multiplicity.
    const expected = factors.filter((factor) => factor.displayName.length > 0 && factor.canonicalName === null)
        .map((factor) => `factorCanonical@${factor.kind}:${factor.rowIndex}:${factor.column}`).sort()
    const declared = unresolvedFields.filter((field) => field.startsWith("factorCanonical@")).sort()
    return expected.length === declared.length && expected.every((field, index) => field === declared[index])
}

function producerCompletenessSupported(
    screenReadCompleteness: unknown,
    rawDiagnostics: unknown,
    diagnostics: InspirationDiagnosticsRecord | null,
    selfFactors: ParsedFactors,
    ancestors: ParsedAncestors,
    unresolvedFields: readonly string[],
    unresolvedFieldsPresent: boolean,
): boolean {
    if (typeof rawDiagnostics !== "object" || rawDiagnostics === null || diagnostics === null || !unresolvedFieldsPresent) return false
    const raw = rawDiagnostics as Record<string, unknown>
    const allFactors = [...selfFactors.records, ...ancestors.records.flatMap((ancestor) => ancestor.factors)]
    const blocks = [selfFactors.records, ...ancestors.records.map((ancestor) => ancestor.factors)]
    const rows = blocks.reduce((count, factors) => count + new Set(factors.map((factor) => factor.rowIndex)).size, 0)
    return screenReadCompleteness === 1 && selfFactors.producerShapeValid && ancestors.producerShapeValid &&
        raw.startedAtTop === true && raw.factorListEndObserved === true &&
        (exactInteger(raw.rowsAccepted) ?? 0) > 0 && (exactInteger(raw.blocksObserved) ?? 0) > 0 &&
        exactInteger(raw.rowsAccepted) === rows && exactInteger(raw.blocksObserved) === blocks.length &&
        exactInteger(raw.gapFrames) === 0 && exactInteger(raw.spacingBreaks) === 0 &&
        exactInteger(raw.leadingPartialBlockRows) === 0 &&
        allFactors.length > 0 && allFactors.every((factor) => factor.displayName.length > 0 && !factor.ambiguous) &&
        !unresolvedFields.some(blocksCompleteCapture)
}

/**
 * Parses an inspiration JSONL corpus into its batch headers and per-Veteran entries. Malformed lines
 * are skipped and counted, never fatal (interrupted writes, manual edits). A record missing the one
 * field that makes it usable - a `scanId` - is dropped rather than defaulted into something that
 * would read as a valid capture.
 */
export function parseInspirationRecords(text: string, file?: string): ParsedInspiration {
    const scans: InspirationScanRecord[] = []
    const entries: VeteranInspirationRecord[] = []
    let malformedRecords = 0
    const lines = text.split("\n")
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        if (!line) continue
        let obj: any
        try {
            obj = JSON.parse(line)
        } catch {
            malformedRecords++
            continue
        }
        if (typeof obj !== "object" || obj === null) {
            malformedRecords++
            continue
        }
        const scanId = str(obj.scanId)
        if (obj.type === "veteran_inspiration_scan") {
            const termination = String(obj.terminationReason ?? "")
            if (!scanId) {
                malformedRecords++
                continue
            }
            scans.push({
                type: "veteran_inspiration_scan",
                schemaVersion: exactInteger(obj.schemaVersion) ?? 0,
                scanId,
                startedAt: num(obj.startedAt),
                completedAt: num(obj.completedAt),
                registeredUsedAtStart: exactInteger(obj.registeredUsedAtStart),
                registeredUsedAtEnd: exactInteger(obj.registeredUsedAtEnd),
                registeredCapacity: exactInteger(obj.registeredCapacity),
                filtersOff: typeof obj.filtersOff === "boolean" ? obj.filtersOff : null,
                sortKey: str(obj.sortKey),
                sortDirection: str(obj.sortDirection),
                snapshotCompatibility: bool(obj.snapshotCompatibility),
                pagerCycleClosed: bool(obj.pagerCycleClosed),
                entryLimit: exactInteger(obj.entryLimit),
                startIndex: exactInteger(obj.startIndex),
                entriesCaptured: exactInteger(obj.entriesCaptured) ?? -1,
                entriesComplete: exactInteger(obj.entriesComplete) ?? -1,
                terminationReason: SCAN_TERMINATIONS.has(termination as InspirationScanTermination)
                    ? (termination as InspirationScanTermination)
                    : null,
                app: str(obj.app),
                file,
                lineNumber: i + 1,
            })
            continue
        }
        if (obj.type === "veteran_inspiration") {
            if (!scanId) {
                malformedRecords++
                continue
            }
            const termination = String(obj.termination ?? "")
            const parsedSelfFactors = parseFactors(obj.selfFactors)
            const parsedAncestors = parseAncestors(obj.legacyAncestors)
            const selfFactorCount = exactInteger(obj.selfFactorCount)
            const expectedSelfFingerprint = canonicalFactorSetFingerprint(parsedSelfFactors.records)
            const expectedSelfStructural = structuralFactorSetFingerprint(parsedSelfFactors.records)
            const declaredSelfFingerprint = str(obj.selfFactorFingerprint)
            const declaredSelfStructural = typeof obj.selfStructuralFingerprint === "string" ? obj.selfStructuralFingerprint : null
            const declaredSelfTrusted = bool(obj.selfFactorSetTrusted)
            const selfFactorContentConsistent = parsedSelfFactors.producerShapeValid &&
                selfFactorCount === parsedSelfFactors.sourceCount && selfFactorCount === parsedSelfFactors.records.length &&
                typeof obj.selfFactorSetTrusted === "boolean" && declaredSelfTrusted === (expectedSelfFingerprint !== null) &&
                declaredSelfFingerprint === expectedSelfFingerprint && declaredSelfStructural === expectedSelfStructural
            const unresolvedFieldsPresent = Array.isArray(obj.unresolvedFields) &&
                obj.unresolvedFields.every((field: unknown) => typeof field === "string")
            const unresolvedFields = unresolvedFieldsPresent ? obj.unresolvedFields as string[] : []
            const canonicalDeclarationsConsistent = unresolvedFieldsPresent && canonicalDeclarationsAgree(
                [...parsedSelfFactors.records, ...parsedAncestors.records.flatMap((ancestor) => ancestor.factors)], unresolvedFields,
            )
            const diagnostics = parseDiagnostics(obj.diagnostics)
            entries.push({
                type: "veteran_inspiration",
                schemaVersion: exactInteger(obj.schemaVersion) ?? 0,
                scanId,
                scanIndex: exactInteger(obj.scanIndex),
                observedAt: num(obj.observedAt),
                rosterFingerprint: str(obj.rosterFingerprint),
                character: str(obj.character),
                outfit: str(obj.outfit),
                rank: str(obj.rank),
                selfPortraitObserved: bool(obj.selfPortraitObserved),
                selfFactorCount: selfFactorCount ?? -1,
                selfFactorFingerprint: declaredSelfFingerprint,
                selfStructuralFingerprint: declaredSelfStructural,
                selfFactorSetTrusted: declaredSelfTrusted,
                selfFactors: parsedSelfFactors.records,
                selfFactorContentConsistent: selfFactorContentConsistent && canonicalDeclarationsConsistent,
                canonicalDeclarationsConsistent,
                legacyAncestors: parsedAncestors.records,
                termination: READ_TERMINATIONS.has(termination as InspirationReadTermination)
                    ? (termination as InspirationReadTermination)
                    : null,
                sparkCaptureComplete: bool(obj.sparkCaptureComplete),
                sparkCaptureConsistent: canonicalDeclarationsConsistent && producerCompletenessSupported(
                    obj.screenReadCompleteness,
                    obj.diagnostics,
                    diagnostics,
                    parsedSelfFactors,
                    parsedAncestors,
                    unresolvedFields,
                    unresolvedFieldsPresent,
                ),
                screenReadCompleteness: num(obj.screenReadCompleteness) ?? 0,
                unresolvedFields,
                diagnostics,
                file,
                lineNumber: i + 1,
            })
            continue
        }
        malformedRecords++
    }
    return { scans, entries, malformedRecords }
}

/** The retention-readiness view of one Veteran: what it can pass on, and what sits behind it. */
export interface VeteranInspirationView {
    readonly rosterFingerprint: string
    readonly character: string | null
    readonly outfit: string | null
    readonly rank: string | null
    readonly observedAt: number | null
    readonly scanId: string
    readonly snapshotCompatible: boolean
    readonly selfFactorCount: number
    readonly selfFactors: readonly InspirationFactorRecord[]
    /** Trusted canonical self fingerprint, or null when any self factor is unresolved. */
    readonly selfFactorFingerprint: string | null
    /** Name-free structural self fingerprint, always present from schema 2. */
    readonly selfStructuralFingerprint: string | null
    /** Whether every self factor resolved, so the canonical self fingerprint is a trusted identity. */
    readonly selfFactorSetTrusted: boolean
    readonly factorContentConsistent: boolean
    readonly completenessConsistent: boolean
    readonly factorSetConflicted: boolean
    readonly legacyAncestorFactorCounts: readonly number[]
    readonly legacyAncestorFactors: readonly (readonly InspirationFactorRecord[])[]
    /** Per-ancestor trusted canonical fingerprints; an entry is null when that ancestor is unresolved. */
    readonly legacyAncestorFingerprints: readonly (string | null)[]
    /** Per-ancestor name-free structural fingerprints, always present from schema 2. */
    readonly legacyAncestorStructuralFingerprints: readonly (string | null)[]
    readonly sparkCaptureComplete: boolean
    readonly unresolvedFields: readonly string[]
}

/**
 * Ranks one capture against another for the same Veteran, strongest first. The tuple is compared
 * lexicographically, so a higher-priority field is never traded for a lower one:
 *
 *  1. snapshot-compatible - the batch's Registered count held for its whole walk, so the entry's
 *     on-screen identity could not have been shifted by a mid-batch registration;
 *  2. complete - the whole factor list was read (a partial read is LESS information, never more);
 *  3. self-trusted - every self factor snapped onto the canonical domain, so the self fingerprint is
 *     an identity rather than a noisy raw read;
 *  4. newest - only as a tie-break among otherwise-equal captures.
 *
 * Deliberately NOT "latest wins": a newer partial or untrusted read must never displace an older
 * complete trusted one. The factor set is immutable, so the better-quality read is the truer one
 * whenever it was taken.
 */
function captureRank(entry: VeteranInspirationRecord, compatibleScans: ReadonlySet<string>): readonly number[] {
    return [
        compatibleScans.has(entry.scanId) ? 1 : 0,
        captureComplete(entry) ? 1 : 0,
        factorSetTrusted(entry) ? 1 : 0,
        entry.observedAt ?? 0,
    ]
}

function captureComplete(entry: VeteranInspirationRecord): boolean {
    return entry.sparkCaptureComplete && entry.sparkCaptureConsistent
}

function factorSetTrusted(entry: VeteranInspirationRecord): boolean {
    return entry.selfFactorSetTrusted && entry.selfFactorContentConsistent && canonicalFactorSetFingerprint(entry.selfFactors) !== null
}

function rankBeats(a: readonly number[], b: readonly number[]): boolean {
    for (let i = 0; i < a.length; i++) {
        if (a[i] > b[i]) return true
        if (a[i] < b[i]) return false
    }
    return false
}

function compatibleScanIds(parsed: ParsedInspiration): ReadonlySet<string> {
    const compatible = new Set<string>()
    const incompatible = new Set<string>()
    for (const scan of parsed.scans) {
        const rows = parsed.entries.filter((entry) => entry.scanId === scan.scanId)
        const count = scan.registeredUsedAtStart
        const indexes = new Set(rows.map((entry) => entry.scanIndex))
        const fingerprints = new Set(rows.map((entry) => entry.rosterFingerprint))
        const traversal = scan.schemaVersion === 2 && rows.every((entry) => entry.schemaVersion === 2) &&
            scan.startIndex === 0 && rows.length === count && indexes.size === count && fingerprints.size === count &&
            rows.every((entry) => entry.scanIndex !== null && Number.isSafeInteger(entry.scanIndex) &&
                entry.scanIndex >= 0 && count !== null && entry.scanIndex < count && entry.rosterFingerprint !== null) &&
            scan.entriesComplete === rows.filter((entry) => entry.sparkCaptureComplete).length
        const fullCycle = scan.pagerCycleClosed && scan.entryLimit === 0 && count !== null && Number.isSafeInteger(count) && count > 0 &&
            scan.registeredCapacity !== null && scan.registeredCapacity >= count && scan.registeredCapacity <= 2_147_483_647 &&
            scan.registeredUsedAtEnd === count && scan.entriesCaptured === count && scan.filtersOff === true &&
            scan.terminationReason === "cycle_closed"
        if (scan.snapshotCompatibility && fullCycle && traversal) compatible.add(scan.scanId)
        else incompatible.add(scan.scanId)
    }
    for (const scanId of incompatible) compatible.delete(scanId)
    return compatible
}

/**
 * The best capture per Veteran, keyed by `rosterFingerprint`, chosen by [captureRank].
 *
 * A capture from a batch whose header is missing (an interrupted write) or whose snapshot was
 * incompatible ranks below a clean one but is still used when it is the only evidence for a Veteran,
 * so no Veteran is silently dropped to MISSING by a single bad batch.
 */
export function buildInspirationIndex(parsed: ParsedInspiration): ReadonlyMap<string, VeteranInspirationView> {
    const compatibleScans = compatibleScanIds(parsed)
    const conflicts = detectInspirationConflicts(parsed)
    const best = new Map<string, VeteranInspirationRecord>()
    for (const entry of parsed.entries) {
        const fingerprint = entry.rosterFingerprint
        if (!fingerprint) continue
        const held = best.get(fingerprint)
        if (!held || rankBeats(captureRank(entry, compatibleScans), captureRank(held, compatibleScans))) {
            best.set(fingerprint, entry)
        }
    }
    // A contradictory producer declaration cannot be hidden by ranking an older, cleaner capture.
    for (const entry of parsed.entries) {
        if (!entry.rosterFingerprint || !compatibleScans.has(entry.scanId)) continue
        const held = best.get(entry.rosterFingerprint)
        const sameIdentity = held && entry.character !== null && entry.outfit !== null && entry.rank !== null &&
            entry.character === held.character && entry.outfit === held.outfit && entry.rank === held.rank
        const contradictory = !entry.canonicalDeclarationsConsistent ||
            (entry.sparkCaptureComplete && (!entry.selfFactorContentConsistent || !entry.sparkCaptureConsistent))
        if (sameIdentity && contradictory) {
            best.set(entry.rosterFingerprint, entry)
        }
    }
    const out = new Map<string, VeteranInspirationView>()
    for (const [fingerprint, entry] of best) {
        const factorSetConflicted = conflicts.has(fingerprint)
        const contentConsistent = entry.selfFactorContentConsistent
        const completenessConsistent = entry.sparkCaptureConsistent
        const trustedFactors = factorSetTrusted(entry) && !factorSetConflicted
        const unresolvedFields = new Set(entry.unresolvedFields)
        if (!contentConsistent) unresolvedFields.add("persistedFactorContent")
        if (!completenessConsistent) unresolvedFields.add("persistedCompleteness")
        if (factorSetConflicted) unresolvedFields.add("factorSetConflict")
        out.set(fingerprint, {
            rosterFingerprint: fingerprint,
            character: entry.character,
            outfit: entry.outfit,
            rank: entry.rank,
            observedAt: entry.observedAt,
            scanId: entry.scanId,
            snapshotCompatible: compatibleScans.has(entry.scanId),
            selfFactorCount: entry.selfFactors.length,
            selfFactors: entry.selfFactors,
            selfFactorFingerprint: trustedFactors ? canonicalFactorSetFingerprint(entry.selfFactors) : null,
            selfStructuralFingerprint: structuralFactorSetFingerprint(entry.selfFactors),
            selfFactorSetTrusted: trustedFactors,
            factorContentConsistent: contentConsistent,
            completenessConsistent,
            factorSetConflicted,
            legacyAncestorFactorCounts: entry.legacyAncestors.map((a) => a.factors.length),
            legacyAncestorFactors: entry.legacyAncestors.map((a) => a.factors),
            legacyAncestorFingerprints: entry.legacyAncestors.map((a) => a.ancestorFactorFingerprint),
            legacyAncestorStructuralFingerprints: entry.legacyAncestors.map((a) => a.ancestorStructuralFingerprint),
            sparkCaptureComplete: captureComplete(entry),
            unresolvedFields: [...unresolvedFields].sort(),
        })
    }
    return out
}

/**
 * Fingerprints carrying two or more mutually-contradicting trusted captures: distinct
 * `selfFactorFingerprint` values, each from a snapshot-compatible, complete, self-trusted read.
 *
 * A Veteran's factor set is immutable, so two trusted reads of the same Veteran that disagree on it
 * cannot both be right - one is a mis-attribution or a canonical instability. The canonical layer was
 * built precisely so this does not happen (PL-R1c proved zero trusted-fingerprint mismatches across
 * paired re-reads), so a non-empty result here is a signal to inspect, not a routine occurrence. It is
 * surfaced rather than silently resolved: the map value is the sorted distinct trusted fingerprints
 * seen, so the caller can fail closed on that Veteran.
 */
export function detectInspirationConflicts(parsed: ParsedInspiration): ReadonlyMap<string, readonly string[]> {
    const compatibleScans = compatibleScanIds(parsed)
    const byFingerprint = new Map<string, Set<string>>()
    for (const entry of parsed.entries) {
        const fingerprint = entry.rosterFingerprint
        if (!fingerprint) continue
        if (!compatibleScans.has(entry.scanId)) continue
        if (!captureComplete(entry) || !factorSetTrusted(entry)) continue
        const canonicalFingerprint = canonicalFactorSetFingerprint(entry.selfFactors)
        if (canonicalFingerprint === null) continue
        let set = byFingerprint.get(fingerprint)
        if (!set) {
            set = new Set<string>()
            byFingerprint.set(fingerprint, set)
        }
        set.add(canonicalFingerprint)
    }
    const conflicts = new Map<string, readonly string[]>()
    for (const [fingerprint, set] of byFingerprint) {
        if (set.size > 1) conflicts.set(fingerprint, [...set].sort())
    }
    return conflicts
}

/** Coverage of one roster snapshot by the inspiration captures available. */
export interface InspirationCoverage {
    readonly schema: typeof PARENTLAB_INSPIRATION_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_INSPIRATION_SCHEMA_VERSION
    readonly rosterScanId: string
    /** Roster entries that carry a fingerprint at all - only those can be joined. */
    readonly identifiedRosterEntries: number
    readonly captured: number
    readonly capturedComplete: number
    readonly capturedIncomplete: number
    readonly missing: number
    /** Captures whose fingerprint matches no entry in this roster snapshot: a Veteran read from an
     * older roster state, or released since. Never silently folded in. */
    readonly orphanCaptures: number
    readonly totalSelfFactors: number
    readonly totalAncestorFactors: number
    readonly views: readonly VeteranInspirationView[]
}

/**
 * Joins the inspiration captures onto a roster snapshot by `rosterFingerprint`.
 *
 * Only the snapshot decides who is in the account; a capture with no matching entry is counted as an
 * orphan rather than added, because the roster snapshot is the authority on current ownership and a
 * capture is only ever evidence about one of its members.
 */
export function joinInspirationToRoster(
    snapshot: RosterSnapshot,
    index: ReadonlyMap<string, VeteranInspirationView>,
): InspirationCoverage {
    const fingerprints = new Set<string>()
    for (const entry of snapshot.entries) {
        if (entry.rosterFingerprint) fingerprints.add(entry.rosterFingerprint)
    }
    const views: VeteranInspirationView[] = []
    for (const fingerprint of fingerprints) {
        const view = index.get(fingerprint)
        if (view) views.push(view)
    }
    views.sort((a, b) => (a.rosterFingerprint < b.rosterFingerprint ? -1 : a.rosterFingerprint > b.rosterFingerprint ? 1 : 0))
    let orphans = 0
    for (const fingerprint of index.keys()) {
        if (!fingerprints.has(fingerprint)) orphans++
    }
    const complete = views.filter((v) => v.sparkCaptureComplete)
    return {
        schema: PARENTLAB_INSPIRATION_SCHEMA,
        schemaVersion: PARENTLAB_INSPIRATION_SCHEMA_VERSION,
        rosterScanId: snapshot.scanId,
        identifiedRosterEntries: fingerprints.size,
        captured: views.length,
        capturedComplete: complete.length,
        capturedIncomplete: views.length - complete.length,
        missing: fingerprints.size - views.length,
        orphanCaptures: orphans,
        totalSelfFactors: complete.reduce((n, v) => n + v.selfFactorCount, 0),
        totalAncestorFactors: complete.reduce((n, v) => n + v.legacyAncestorFactorCounts.reduce((m, c) => m + c, 0), 0),
        views,
    }
}

/**
 * The OCR-free shape of one ancestor's factor set: the kinds and star counts, in panel order for the
 * lead triple and sorted for the rest.
 *
 * This exists because the two evidence sources for the same inheritance cannot be matched by name.
 * The Legacy Select reader (PL-4) predates the OCR fixes this stage needed and returns names like
 * "OPower" or bare "O" for its white rows, while a factor NAME from either source is only about
 * ninety-seven percent stable across re-reads anyway. The kinds and star counts are pixel-classified
 * and are exactly stable, so they are what a cross-source match can honestly be built on.
 */
export function ancestorStarSignature(factors: readonly InspirationFactorRecord[]): string {
    const lead = ["stat", "aptitude", "unique"].map((kind) => {
        const found = factors.find((f) => f.kind === kind)
        return found ? `${kind}:${found.stars}` : `${kind}:-`
    })
    const whites = factors
        .filter((f) => f.kind === "white")
        .map((f) => f.stars)
        .sort((a, b) => a - b)
    return `${lead.join("|")}|white:${whites.join(",")}`
}
