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
 * panel agree on them exactly. `displayName` is OCR and does NOT have that guarantee - about one
 * factor name in thirty comes back a glyph different on a re-read - so `factorFingerprint`, which is
 * built from the name, inherits that instability. Treat the fingerprint as a strong hint and the
 * kind/stars as fact until the names are snapped onto a known skill/race domain.
 */
export interface InspirationFactorRecord {
    readonly rowIndex: number
    readonly column: InspirationColumn | null
    readonly kind: string
    readonly displayName: string
    readonly normalizedName: string
    readonly stars: number
    readonly factorFingerprint: string
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
    readonly ancestorFactorFingerprint: string
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
    readonly scanIndex: number
    readonly observedAt: number | null
    /** The roster identity this evidence attaches to. Null when the entry's own identity fields did
     * not all resolve, in which case the factors were still read but cannot be attributed. */
    readonly rosterFingerprint: string | null
    readonly character: string | null
    readonly outfit: string | null
    readonly rank: string | null
    readonly selfPortraitObserved: boolean
    readonly selfFactorCount: number
    readonly selfFactorFingerprint: string
    readonly selfFactors: readonly InspirationFactorRecord[]
    readonly legacyAncestors: readonly InspirationAncestorRecord[]
    readonly termination: InspirationReadTermination | null
    /** The single flag a consumer should gate on. */
    readonly sparkCaptureComplete: boolean
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
    /** False when the roster's own count changed between the first and last entry, or could not be
     * re-read. A Veteran registered or released mid-batch shifts every later chevron position, which
     * would attach one Veteran's factors to another Veteran's identity. */
    readonly snapshotCompatibility: boolean
    readonly entryLimit: number
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
    "entry_limit_reached",
    "chevron_end",
    "unexpected_screen",
    "precondition_failed",
    "hard_bound_reached",
])

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

function parseFactors(v: unknown): InspirationFactorRecord[] {
    if (!Array.isArray(v)) return []
    const out: InspirationFactorRecord[] = []
    for (const raw of v) {
        if (typeof raw !== "object" || raw === null) continue
        const r = raw as Record<string, unknown>
        const kind = str(r.kind)
        const fingerprint = str(r.factorFingerprint)
        // A row with no kind or no fingerprint is not a factor; dropping it is safer than inventing one.
        if (!kind || !fingerprint) continue
        const column = str(r.column)
        out.push({
            rowIndex: num(r.rowIndex) ?? 0,
            column: column === "left" || column === "right" ? column : null,
            kind,
            displayName: typeof r.displayName === "string" ? r.displayName : "",
            normalizedName: typeof r.normalizedName === "string" ? r.normalizedName : "",
            stars: num(r.stars) ?? 0,
            factorFingerprint: fingerprint,
            ambiguous: bool(r.ambiguous),
        })
    }
    return out
}

function parseAncestors(v: unknown): InspirationAncestorRecord[] {
    if (!Array.isArray(v)) return []
    const out: InspirationAncestorRecord[] = []
    for (const raw of v) {
        if (typeof raw !== "object" || raw === null) continue
        const r = raw as Record<string, unknown>
        const factors = parseFactors(r.factors)
        out.push({
            ancestorIndex: num(r.ancestorIndex) ?? out.length,
            portraitObserved: bool(r.portraitObserved),
            rank: str(r.rank),
            factorCount: num(r.factorCount) ?? factors.length,
            ancestorFactorFingerprint: str(r.ancestorFactorFingerprint) ?? "",
            factors,
        })
    }
    return out.sort((a, b) => a.ancestorIndex - b.ancestorIndex)
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
                schemaVersion: num(obj.schemaVersion) ?? 0,
                scanId,
                startedAt: num(obj.startedAt),
                completedAt: num(obj.completedAt),
                registeredUsedAtStart: num(obj.registeredUsedAtStart),
                registeredUsedAtEnd: num(obj.registeredUsedAtEnd),
                registeredCapacity: num(obj.registeredCapacity),
                filtersOff: typeof obj.filtersOff === "boolean" ? obj.filtersOff : null,
                sortKey: str(obj.sortKey),
                sortDirection: str(obj.sortDirection),
                snapshotCompatibility: bool(obj.snapshotCompatibility),
                entryLimit: num(obj.entryLimit) ?? 0,
                entriesCaptured: num(obj.entriesCaptured) ?? 0,
                entriesComplete: num(obj.entriesComplete) ?? 0,
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
            entries.push({
                type: "veteran_inspiration",
                schemaVersion: num(obj.schemaVersion) ?? 0,
                scanId,
                scanIndex: num(obj.scanIndex) ?? 0,
                observedAt: num(obj.observedAt),
                rosterFingerprint: str(obj.rosterFingerprint),
                character: str(obj.character),
                outfit: str(obj.outfit),
                rank: str(obj.rank),
                selfPortraitObserved: bool(obj.selfPortraitObserved),
                selfFactorCount: num(obj.selfFactorCount) ?? 0,
                selfFactorFingerprint: str(obj.selfFactorFingerprint) ?? "",
                selfFactors: parseFactors(obj.selfFactors),
                legacyAncestors: parseAncestors(obj.legacyAncestors),
                termination: READ_TERMINATIONS.has(termination as InspirationReadTermination)
                    ? (termination as InspirationReadTermination)
                    : null,
                sparkCaptureComplete: bool(obj.sparkCaptureComplete),
                screenReadCompleteness: num(obj.screenReadCompleteness) ?? 0,
                unresolvedFields: Array.isArray(obj.unresolvedFields) ? obj.unresolvedFields.filter((x: unknown) => typeof x === "string") : [],
                diagnostics: parseDiagnostics(obj.diagnostics),
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
    readonly selfFactorCount: number
    readonly selfFactors: readonly InspirationFactorRecord[]
    readonly selfFactorFingerprint: string
    readonly legacyAncestorFactorCounts: readonly number[]
    readonly legacyAncestorFactors: readonly (readonly InspirationFactorRecord[])[]
    readonly legacyAncestorFingerprints: readonly string[]
    readonly sparkCaptureComplete: boolean
    readonly unresolvedFields: readonly string[]
}

/**
 * The best capture per Veteran, keyed by `rosterFingerprint`.
 *
 * "Best" is a complete capture over an incomplete one, and the newest among equals. A newer partial
 * read must never displace an older complete one: the partial is not more current information about
 * the same Veteran, it is less information about it, and the factor set is immutable anyway.
 */
export function buildInspirationIndex(parsed: ParsedInspiration): ReadonlyMap<string, VeteranInspirationView> {
    const best = new Map<string, VeteranInspirationRecord>()
    for (const entry of parsed.entries) {
        const fingerprint = entry.rosterFingerprint
        if (!fingerprint) continue
        const held = best.get(fingerprint)
        if (!held) {
            best.set(fingerprint, entry)
            continue
        }
        if (entry.sparkCaptureComplete !== held.sparkCaptureComplete) {
            if (entry.sparkCaptureComplete) best.set(fingerprint, entry)
            continue
        }
        if ((entry.observedAt ?? 0) > (held.observedAt ?? 0)) best.set(fingerprint, entry)
    }
    const out = new Map<string, VeteranInspirationView>()
    for (const [fingerprint, entry] of best) {
        out.set(fingerprint, {
            rosterFingerprint: fingerprint,
            character: entry.character,
            outfit: entry.outfit,
            rank: entry.rank,
            observedAt: entry.observedAt,
            scanId: entry.scanId,
            selfFactorCount: entry.selfFactors.length,
            selfFactors: entry.selfFactors,
            selfFactorFingerprint: entry.selfFactorFingerprint,
            legacyAncestorFactorCounts: entry.legacyAncestors.map((a) => a.factors.length),
            legacyAncestorFactors: entry.legacyAncestors.map((a) => a.factors),
            legacyAncestorFingerprints: entry.legacyAncestors.map((a) => a.ancestorFactorFingerprint),
            sparkCaptureComplete: entry.sparkCaptureComplete,
            unresolvedFields: entry.unresolvedFields,
        })
    }
    return out
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
