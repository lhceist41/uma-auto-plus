// ParentLab PL-R1b roster ingest - the read side of the on-device `roster_scan` / `roster_entry`
// records (roster_scan.jsonl). Pure, offline, deterministic, tolerant of malformed lines like
// parseCorpus.
//
// A roster scan is a snapshot of what the account owns RIGHT NOW. That is a different concept from
// the PL-3 Veteran library, which is a faithful reconstruction of every career the bot ever
// produced, and the two are deliberately kept as separate artifacts joined by reconcile.ts rather
// than merged. Current ownership is not history and history is not current ownership.
//
// The one rule this module exists to enforce: a scan is trustworthy only when the walk enumerated
// exactly as many entries as the account's own `Registered used/capacity` said it owns, under a
// confirmed Filters: OFF, with every entry identified and no fingerprint repeated. Anything else is
// incomplete, and an incomplete snapshot must never be read as "these are the Veterans I own".

/** Library schema discriminator + version for the roster snapshot. Deliberately separate from
 * PARENTLAB_SCHEMA: PL-3's Veteran shape is proven and is not touched by this stage. */
export const PARENTLAB_ROSTER_SCHEMA = "parent_lab_roster" as const
export const PARENTLAB_ROSTER_SCHEMA_VERSION = 1 as const

/** Why the device stopped walking. Mirrors the Kotlin `RosterScanTermination`, lower-cased. */
export type RosterScanTermination =
    | "count_reached"
    | "chevron_end"
    | "wrapped"
    | "stalled"
    | "entry_limit_reached"
    | "hard_bound_reached"
    | "unexpected_screen"
    | "precondition_failed"

/** The five stat keys, in the order the device writes them and PL-3 stores them. */
export const ROSTER_STAT_KEYS = ["spd", "sta", "pwr", "grt", "wit"] as const
export type RosterStatKey = (typeof ROSTER_STAT_KEYS)[number]

/** The ten aptitude keys, in the order the device writes them. Canonical here rather than beside the
 * target profiles: the roster identity check and the profile gates must read the same list. */
export const ROSTER_APTITUDE_KEYS = ["turf", "dirt", "sprint", "mile", "medium", "long", "front", "pace", "late", "end"] as const
export type RosterAptitudeKey = (typeof ROSTER_APTITUDE_KEYS)[number]

/** The Career Info block, when the device read it. Every field is independently nullable: the block
 * is read field by field and a label that did not match yields null rather than a guess. */
export interface RosterCareerInfoRecord {
    readonly races: number | null
    readonly wins: number | null
    readonly fans: number | null
    readonly scenario: string | null
    readonly rating: number | null
    /** "YYYY-MM-DD" as displayed by the game. Day granularity: never a unique identifier by itself. */
    readonly dateAcquired: string | null
}

/**
 * What the device's readers saw before the parsers accepted or rejected it, written only for entries
 * that left an immutable field unresolved.
 *
 * Evidence, never identity. Nothing here may be promoted into a field: a `rawStatOcr.spd` of "1" is
 * a dropped-digit artifact the device already refused, and treating it as a stat would mint a wrong
 * fingerprint rather than merely lose a value. It exists so an unresolved field can be diagnosed
 * from the corpus instead of costing another walk of the whole roster.
 */
export interface RosterEntryDiagnosticsRecord {
    readonly rawNameOutfitOcr: string | null
    readonly rawRatingOcr: string | null
    /** Raw digit OCR per stat key, for the stats that produced any text at all. */
    readonly rawStatOcr: Readonly<Record<string, string>>
    readonly outfitCandidate: string | null
    readonly outfitScore: number | null
    readonly outfitSecondCandidate: string | null
    readonly outfitSecondScore: number | null
    readonly rankFamily: string | null
    readonly rankChosen: string | null
    readonly rankBestScore: number | null
    readonly rankSecondScore: number | null
}

/** One `roster_entry` record as written by the device walk. */
export interface RosterEntryRecord {
    readonly type: "roster_entry"
    readonly schemaVersion: number
    readonly scanId: string
    readonly scanIndex: number
    readonly observedAt: number | null
    readonly character: string | null
    readonly outfit: string | null
    readonly rank: string | null
    readonly rating: number | null
    readonly stats: Readonly<Record<RosterStatKey, number | null>>
    readonly statGrades: Readonly<Record<string, string>>
    readonly aptitudes: Readonly<Record<string, string>>
    /** "not_set" when the favorite glyph is the pure-grayscale outline, "unknown" when it is a
     * saturated icon the device deliberately does not identify. */
    readonly favoriteState: string
    /** Always "unknown" at this stage: a memo also protects a Veteran and is not visible on this
     * screen, so protection is only positively established by the filter-partition pass. */
    readonly protectionState: string
    readonly careerInfo: RosterCareerInfoRecord | null
    readonly rosterFingerprint: string | null
    readonly readCompleteness: number
    /** How many entries in the same scan share this fingerprint. > 1 is preserved, never collapsed. */
    readonly identityMultiplicity: number
    readonly unresolvedFields: readonly string[]
    /** Failure evidence, present only when the device had the diagnostic armed AND this entry left an
     * immutable field unresolved. Null is the normal case. */
    readonly diagnostics: RosterEntryDiagnosticsRecord | null
    readonly file?: string
    readonly lineNumber?: number
}

/** One `roster_scan` header record as written by the device walk. */
export interface RosterScanRecord {
    readonly type: "roster_scan"
    readonly schemaVersion: number
    readonly scanId: string
    readonly startedAt: number | null
    readonly completedAt: number | null
    readonly displayedRegisteredUsed: number | null
    readonly displayedRegisteredCapacity: number | null
    readonly filtersOff: boolean | null
    readonly sortKey: string | null
    readonly sortDirection: string | null
    readonly entryLimit: number
    readonly entriesEnumerated: number
    readonly uniqueFingerprints: number
    readonly unidentifiedCount: number
    readonly duplicateFingerprintCount: number
    readonly countDiscrepancy: number | null
    readonly terminationReason: RosterScanTermination
    /** The walk covered exactly the account's own roster, whether or not every entry identified.
     * Null on pre-split records that predate the field (they only carried the merged `completeness`). */
    readonly enumerationComplete: boolean | null
    /** Every enumerated entry resolved to a distinct identity. Null on pre-split records. */
    readonly identityComplete: boolean | null
    readonly completeness: "trusted_complete" | "incomplete"
    /** How many failure-evidence crops the walk wrote. Null on records that predate the field, which
     * is a different fact from a scan that wrote none. */
    readonly evidenceCropCount: number | null
    readonly app: string | null
    readonly screenWidth: number | null
    readonly screenHeight: number | null
    readonly file?: string
    readonly lineNumber?: number
}

export interface ParsedRosterScans {
    readonly scans: readonly RosterScanRecord[]
    readonly entries: readonly RosterEntryRecord[]
    /** Lines that were valid JSON but not a usable roster record. Surfaced, never silently dropped. */
    readonly malformedRecords: number
}

const TERMINATIONS = new Set<RosterScanTermination>([
    "count_reached",
    "chevron_end",
    "wrapped",
    "stalled",
    "entry_limit_reached",
    "hard_bound_reached",
    "unexpected_screen",
    "precondition_failed",
])

function num(v: unknown): number | null {
    // Number(null) is 0 and Number("") is 0, so the absent-value cases must be rejected before the
    // coercion: an unread Fans Earned that parsed as 0 would look like a real value and falsely
    // contradict a historical match during reconciliation.
    if (v === null || v === undefined || v === "") return null
    const n = Number(v)
    return Number.isFinite(n) ? n : null
}

function str(v: unknown): string | null {
    return typeof v === "string" && v.length > 0 ? v : null
}

function stringMap(v: unknown): Record<string, string> {
    const out: Record<string, string> = {}
    if (typeof v !== "object" || v === null) return out
    for (const [k, value] of Object.entries(v as Record<string, unknown>)) {
        if (typeof value === "string" && value.length > 0) out[k] = value
    }
    return out
}

function parseStats(v: unknown): Record<RosterStatKey, number | null> {
    const raw = typeof v === "object" && v !== null ? (v as Record<string, unknown>) : {}
    const out = {} as Record<RosterStatKey, number | null>
    for (const key of ROSTER_STAT_KEYS) out[key] = num(raw[key])
    return out
}

function parseDiagnostics(v: unknown): RosterEntryDiagnosticsRecord | null {
    if (typeof v !== "object" || v === null) return null
    const r = v as Record<string, unknown>
    return {
        rawNameOutfitOcr: str(r.rawNameOutfitOcr),
        rawRatingOcr: str(r.rawRatingOcr),
        rawStatOcr: stringMap(r.rawStatOcr),
        outfitCandidate: str(r.outfitCandidate),
        outfitScore: num(r.outfitScore),
        outfitSecondCandidate: str(r.outfitSecondCandidate),
        outfitSecondScore: num(r.outfitSecondScore),
        rankFamily: str(r.rankFamily),
        rankChosen: str(r.rankChosen),
        rankBestScore: num(r.rankBestScore),
        rankSecondScore: num(r.rankSecondScore),
    }
}

function parseCareerInfo(v: unknown): RosterCareerInfoRecord | null {
    if (typeof v !== "object" || v === null) return null
    const r = v as Record<string, unknown>
    return {
        races: num(r.races),
        wins: num(r.wins),
        fans: num(r.fans),
        scenario: str(r.scenario),
        rating: num(r.rating),
        dateAcquired: str(r.dateAcquired),
    }
}

/**
 * Parses a roster-scan JSONL corpus into its header and entry records. Malformed lines are skipped
 * and counted, never fatal (interrupted writes, manual edits). A record missing the one field that
 * makes it usable - a `scanId`, or a recognised `terminationReason` on a header - is dropped rather
 * than defaulted into something that would read as a valid scan.
 */
export function parseRosterScanRecords(text: string, file?: string): ParsedRosterScans {
    const scans: RosterScanRecord[] = []
    const entries: RosterEntryRecord[] = []
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
        if (obj.type === "roster_scan") {
            const termination = String(obj.terminationReason ?? "")
            if (!scanId || !TERMINATIONS.has(termination as RosterScanTermination)) {
                malformedRecords++
                continue
            }
            scans.push({
                type: "roster_scan",
                schemaVersion: num(obj.schemaVersion) ?? 0,
                scanId,
                startedAt: num(obj.startedAt),
                completedAt: num(obj.completedAt),
                displayedRegisteredUsed: num(obj.displayedRegisteredUsed),
                displayedRegisteredCapacity: num(obj.displayedRegisteredCapacity),
                filtersOff: typeof obj.filtersOff === "boolean" ? obj.filtersOff : null,
                sortKey: str(obj.sortKey),
                sortDirection: str(obj.sortDirection),
                entryLimit: num(obj.entryLimit) ?? 0,
                entriesEnumerated: num(obj.entriesEnumerated) ?? 0,
                uniqueFingerprints: num(obj.uniqueFingerprints) ?? 0,
                unidentifiedCount: num(obj.unidentifiedCount) ?? 0,
                duplicateFingerprintCount: num(obj.duplicateFingerprintCount) ?? 0,
                countDiscrepancy: num(obj.countDiscrepancy),
                terminationReason: termination as RosterScanTermination,
                enumerationComplete: typeof obj.enumerationComplete === "boolean" ? obj.enumerationComplete : null,
                identityComplete: typeof obj.identityComplete === "boolean" ? obj.identityComplete : null,
                completeness: obj.completeness === "trusted_complete" ? "trusted_complete" : "incomplete",
                evidenceCropCount: num(obj.evidenceCropCount),
                app: str(obj.app),
                screenWidth: num(obj.screenWidth),
                screenHeight: num(obj.screenHeight),
                file,
                lineNumber: i,
            })
            continue
        }
        if (obj.type === "roster_entry") {
            const scanIndex = num(obj.scanIndex)
            if (!scanId || scanIndex === null) {
                malformedRecords++
                continue
            }
            entries.push({
                type: "roster_entry",
                schemaVersion: num(obj.schemaVersion) ?? 0,
                scanId,
                scanIndex,
                observedAt: num(obj.observedAt),
                character: str(obj.character),
                outfit: str(obj.outfit),
                rank: str(obj.rank),
                rating: num(obj.rating),
                stats: parseStats(obj.stats),
                statGrades: stringMap(obj.statGrades),
                aptitudes: stringMap(obj.aptitudes),
                favoriteState: str(obj.favoriteState) ?? "unknown",
                protectionState: str(obj.protectionState) ?? "unknown",
                careerInfo: parseCareerInfo(obj.careerInfo),
                rosterFingerprint: str(obj.rosterFingerprint),
                readCompleteness: num(obj.readCompleteness) ?? 0,
                identityMultiplicity: num(obj.identityMultiplicity) ?? 1,
                unresolvedFields: Array.isArray(obj.unresolvedFields) ? obj.unresolvedFields.filter((f: unknown) => typeof f === "string") : [],
                diagnostics: parseDiagnostics(obj.diagnostics),
                file,
                lineNumber: i,
            })
            continue
        }
        malformedRecords++
    }
    return { scans, entries, malformedRecords }
}

/** Why a snapshot is not trustworthy. Empty when it is. Each reason is a fact, not a judgement. */
export type RosterSnapshotDefect =
    | "no_header_record"
    | "device_marked_incomplete"
    | "filters_not_confirmed_off"
    | "registered_count_unread"
    | "count_mismatch"
    | "duplicate_fingerprints"
    | "unidentified_entries"
    | "entry_rows_missing"
    | "termination_not_at_end"

/** The derived current-roster snapshot for one scan. Deterministic: same records in, same snapshot
 * out, regardless of input line order. */
export interface RosterSnapshot {
    readonly schema: typeof PARENTLAB_ROSTER_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_ROSTER_SCHEMA_VERSION
    readonly scanId: string
    /** The scan's completion time, or its start, or the newest entry observation. Null when unknown. */
    readonly observedAt: number | null
    readonly registeredUsed: number | null
    readonly registeredCapacity: number | null
    /** Percent of the account's Veteran capacity in use, rounded to one decimal. Null when unread. */
    readonly percentFull: number | null
    readonly filtersOff: boolean | null
    readonly sortKey: string | null
    readonly sortDirection: string | null
    readonly entryLimit: number
    /** Entry rows actually present for this scan. */
    readonly scanCount: number
    readonly uniqueFingerprints: number
    readonly duplicateFingerprints: number
    readonly unidentified: number
    /** scanCount minus the account's own displayed used count. Null when that count was unread. */
    readonly countDiscrepancy: number | null
    readonly terminationReason: RosterScanTermination | null
    /** The walk covered exactly the account's own roster (filters off, count read, every enumerated
     * position present, ended at a real end) - recomputed from the records here, not taken on the
     * header's word. True even when identity is incomplete: this is the fact a 257/257 count-complete
     * walk carries regardless of how many entries fingerprinted. */
    readonly enumerationComplete: boolean
    /** Every enumerated entry resolved to a distinct identity: at least one entry, none unidentified,
     * no repeated fingerprint. Orthogonal to [enumerationComplete]. */
    readonly identityComplete: boolean
    /** True only when EVERY completeness condition holds - `enumerationComplete && identityComplete`
     * plus the header agreeing. Barred from transfer analysis otherwise. */
    readonly trustedComplete: boolean
    readonly defects: readonly RosterSnapshotDefect[]
    readonly headerPresent: boolean
    /** Failure-evidence crops the device wrote for this scan. Null when the header predates the
     * field, so "no crops" and "this build could not write crops" stay distinguishable. */
    readonly evidenceCropCount: number | null
    readonly app: string | null
    /** Entries in traversal order. Duplicates are preserved as distinct positions, never merged. */
    readonly entries: readonly RosterEntryRecord[]
}

/** Terminations consistent with having actually reached the end of the roster. */
const END_TERMINATIONS = new Set<RosterScanTermination>(["count_reached", "chevron_end"])

/** Defects that mean the walk did NOT cover the whole roster. Their absence is enumeration
 * completeness. Kept separate from the identity defects so the two facts never merge again. */
const ENUMERATION_DEFECTS = new Set<RosterSnapshotDefect>([
    "no_header_record",
    "filters_not_confirmed_off",
    "registered_count_unread",
    "count_mismatch",
    "entry_rows_missing",
    "termination_not_at_end",
])

/** Defects that mean some enumerated entry did not resolve to a distinct identity. */
const IDENTITY_DEFECTS = new Set<RosterSnapshotDefect>(["duplicate_fingerprints", "unidentified_entries"])

/** The `unresolvedFields` tokens the device writes for an identity feeder. Matched exactly, never by
 * prefix: `statGrade_spd` is an auxiliary read and must not be mistaken for the identity `stat_spd`. */
const IDENTITY_UNRESOLVED_FIELDS = new Set<string>([
    "character",
    "outfit",
    "rank",
    "rating",
    ...ROSTER_STAT_KEYS.map((k) => `stat_${k}`),
    ...ROSTER_APTITUDE_KEYS.map((k) => `aptitude_${k}`),
])

/**
 * Whether the entry carries every field the device builds its fingerprint from.
 *
 * The writer nulls `rosterFingerprint` whenever any identity feeder is unread, so a fingerprint next
 * to a missing identity field contradicts the record's own writer and must not be read as identity.
 * `unresolvedFields` is authoritative alongside the parsed values: a record naming an identity field
 * unread stays unidentified even when that field also carries a plausible value, so a hand-edited
 * row cannot be promoted into trust by filling one in.
 */
function identityEvidenceComplete(e: RosterEntryRecord): boolean {
    if (e.character === null || e.outfit === null || e.rank === null || e.rating === null) return false
    if (ROSTER_STAT_KEYS.some((k) => e.stats[k] === null)) return false
    if (ROSTER_APTITUDE_KEYS.some((k) => !e.aptitudes[k])) return false
    return !e.unresolvedFields.some((f) => IDENTITY_UNRESOLVED_FIELDS.has(f))
}

function snapshotFor(scanId: string, header: RosterScanRecord | undefined, rows: readonly RosterEntryRecord[]): RosterSnapshot {
    const entries = [...rows].sort((a, b) => a.scanIndex - b.scanIndex)
    const fingerprints = entries.map((e) => (identityEvidenceComplete(e) ? e.rosterFingerprint : null)).filter((f): f is string => f !== null)
    const uniqueFingerprints = new Set(fingerprints).size
    const duplicateFingerprints = fingerprints.length - uniqueFingerprints
    const unidentified = entries.length - fingerprints.length
    const registeredUsed = header?.displayedRegisteredUsed ?? null
    const registeredCapacity = header?.displayedRegisteredCapacity ?? null

    const defects: RosterSnapshotDefect[] = []
    if (!header) defects.push("no_header_record")
    if (header && header.completeness !== "trusted_complete") defects.push("device_marked_incomplete")
    if (header?.filtersOff !== true) defects.push("filters_not_confirmed_off")
    if (registeredUsed === null) defects.push("registered_count_unread")
    else if (entries.length !== registeredUsed) defects.push("count_mismatch")
    if (duplicateFingerprints > 0) defects.push("duplicate_fingerprints")
    if (unidentified > 0) defects.push("unidentified_entries")
    // The device counts what it read; a missing entry row means the write was truncated even though
    // the walk itself finished, which the header alone would never reveal.
    if (header && entries.length !== header.entriesEnumerated) defects.push("entry_rows_missing")
    if (header && !END_TERMINATIONS.has(header.terminationReason)) defects.push("termination_not_at_end")

    const observedAt =
        header?.completedAt ?? header?.startedAt ?? entries.reduce<number | null>((max, e) => (e.observedAt !== null && (max === null || e.observedAt > max) ? e.observedAt : max), null)

    // Derived here from the records, not read off the header: the header's own enumerationComplete is
    // cross-checked by these but never the sole authority, exactly as trustedComplete already was.
    const enumerationComplete = !defects.some((d) => ENUMERATION_DEFECTS.has(d))
    const identityComplete = entries.length > 0 && !defects.some((d) => IDENTITY_DEFECTS.has(d))

    return {
        schema: PARENTLAB_ROSTER_SCHEMA,
        schemaVersion: PARENTLAB_ROSTER_SCHEMA_VERSION,
        scanId,
        observedAt,
        registeredUsed,
        registeredCapacity,
        percentFull: registeredUsed !== null && registeredCapacity ? Math.round((registeredUsed / registeredCapacity) * 1000) / 10 : null,
        filtersOff: header?.filtersOff ?? null,
        sortKey: header?.sortKey ?? null,
        sortDirection: header?.sortDirection ?? null,
        entryLimit: header?.entryLimit ?? 0,
        scanCount: entries.length,
        uniqueFingerprints,
        duplicateFingerprints,
        unidentified,
        countDiscrepancy: registeredUsed === null ? null : entries.length - registeredUsed,
        terminationReason: header?.terminationReason ?? null,
        enumerationComplete,
        identityComplete,
        trustedComplete: defects.length === 0,
        defects,
        headerPresent: header !== undefined,
        evidenceCropCount: header?.evidenceCropCount ?? null,
        app: header?.app ?? null,
        entries,
    }
}

/**
 * Builds one snapshot per scan present in the parsed records, newest first.
 *
 * A scan whose entry rows were written but whose header never was (an interrupted walk: the device
 * checkpoints entries as it reads them and writes the header last) still produces a snapshot, marked
 * `no_header_record` and never trusted. That is the whole interruption story: a partial scan is
 * visible and honest, and cannot masquerade as a current one.
 */
export function buildRosterSnapshots(parsed: ParsedRosterScans): readonly RosterSnapshot[] {
    const byScan = new Map<string, RosterEntryRecord[]>()
    for (const entry of parsed.entries) {
        const list = byScan.get(entry.scanId)
        if (list) list.push(entry)
        else byScan.set(entry.scanId, [entry])
    }
    const headers = new Map<string, RosterScanRecord>()
    for (const scan of parsed.scans) {
        const existing = headers.get(scan.scanId)
        // A duplicated header (a re-pulled corpus overlapping an earlier one) keeps the later write.
        if (!existing || (scan.lineNumber ?? 0) >= (existing.lineNumber ?? 0)) headers.set(scan.scanId, scan)
    }
    const scanIds = new Set<string>([...byScan.keys(), ...headers.keys()])
    const snapshots = [...scanIds].map((id) => snapshotFor(id, headers.get(id), byScan.get(id) ?? []))
    // Newest first; scanId breaks a tie so the order never depends on Set iteration order.
    return snapshots.sort((a, b) => (b.observedAt ?? 0) - (a.observedAt ?? 0) || (a.scanId < b.scanId ? -1 : a.scanId > b.scanId ? 1 : 0))
}

/**
 * The snapshot a consumer should treat as "what the account owns now": the newest TRUSTED scan, or
 * null when no scan is trustworthy. Deliberately not "the newest scan": a newer incomplete walk must
 * never displace an older complete one, and no snapshot at all is a better answer than a wrong one.
 */
export function latestTrustedSnapshot(snapshots: readonly RosterSnapshot[]): RosterSnapshot | null {
    return snapshots.find((s) => s.trustedComplete) ?? null
}
