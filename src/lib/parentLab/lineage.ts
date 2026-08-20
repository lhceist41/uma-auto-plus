// ParentLab lineage ingest - the read side of the on-device `type:"lineage_selected"` records
// (lineage.jsonl). Pure, offline, deterministic, tolerant of malformed lines like parseCorpus.
//
// One record is emitted per career launch that read the populated Legacy Select summary, carrying
// the six ancestor observations correlated to the career by `launchTransactionId`. This module only
// PARSES and VALIDATES them; buildVeteranLibrary joins them to Veterans by that id. No affinity
// ranking, no exact owned-Veteran matching, no canonical factor ids - the spark-set fingerprint the
// event already carries is the identity evidence a later stage will use.

/** The capture status the device recorded. `failed` events carry no usable ancestors. */
export type LineageEventCaptureStatus = "captured" | "partial" | "failed"

/** One observed factor row on an ancestor. `stars`/`kind` are pixel-classified (authoritative);
 * `displayText` is raw OCR and may be empty when the name was unreadable (kept, never dropped). */
export interface LineageFactorRecord {
    readonly kind: string
    readonly displayText: string
    readonly stars: number
    readonly ambiguous: boolean
    readonly clipped: boolean
}

/** One ancestor observation from the event. `rank`/`probableVeteranId` are unresolved in this stage. */
export interface LineageAncestorRecord {
    readonly role: string
    readonly slotIndex: number
    readonly portraitObserved: boolean
    readonly rank: string | null
    readonly ownership: string
    readonly matchStatus: string
    readonly probableVeteranId: string | null
    readonly hasLeadTriple: boolean
    readonly completeness: number
    readonly factorFingerprint: string
    readonly factors: readonly LineageFactorRecord[]
}

/** One parsed `lineage_selected` record. */
export interface LineageEventRecord {
    readonly type: "lineage_selected"
    readonly schemaVersion: number
    readonly launchTransactionId: string | null
    readonly ts: number | null
    readonly scenario: string
    readonly trainee: string
    readonly overallAffinity: string | null
    readonly captureStatus: LineageEventCaptureStatus
    readonly ancestors: readonly LineageAncestorRecord[]
    readonly file?: string
    readonly lineNumber?: number
}

const CAPTURE_STATUSES = new Set<LineageEventCaptureStatus>(["captured", "partial", "failed"])

function parseFactor(raw: unknown): LineageFactorRecord | null {
    if (typeof raw !== "object" || raw === null) return null
    const r = raw as Record<string, unknown>
    if (typeof r.kind !== "string" || !r.kind) return null
    const stars = Number(r.stars)
    if (!Number.isFinite(stars)) return null
    return {
        kind: r.kind,
        displayText: String(r.displayText ?? "").trim(),
        stars: Math.max(0, Math.round(stars)),
        ambiguous: r.ambiguous === true,
        clipped: r.clipped === true,
    }
}

function parseAncestor(raw: unknown): LineageAncestorRecord | null {
    if (typeof raw !== "object" || raw === null) return null
    const r = raw as Record<string, unknown>
    if (typeof r.role !== "string" || !r.role) return null
    const factors: LineageFactorRecord[] = []
    if (Array.isArray(r.factors)) {
        for (const rawF of r.factors) {
            const f = parseFactor(rawF)
            if (f) factors.push(f)
        }
    }
    return {
        role: r.role,
        slotIndex: Number.isFinite(Number(r.slotIndex)) ? Number(r.slotIndex) : -1,
        portraitObserved: r.portraitObserved === true,
        rank: typeof r.rank === "string" ? r.rank : null,
        ownership: typeof r.ownership === "string" ? r.ownership : "unknown",
        matchStatus: typeof r.matchStatus === "string" ? r.matchStatus : "unresolved",
        probableVeteranId: typeof r.probableVeteranId === "string" ? r.probableVeteranId : null,
        hasLeadTriple: r.hasLeadTriple === true,
        completeness: Number.isFinite(Number(r.completeness)) ? Number(r.completeness) : 0,
        factorFingerprint: String(r.factorFingerprint ?? ""),
        factors,
    }
}

/**
 * Parse a lineage JSONL corpus into validated `lineage_selected` records. Malformed lines and rows
 * are skipped, never fatal (interrupted writes, manual edits). A record with an unknown/absent
 * `captureStatus` or a non-array `ancestors` is skipped; individual malformed ancestors/factors are
 * dropped without discarding the surrounding record.
 */
export function parseLineageRecords(text: string, file?: string): LineageEventRecord[] {
    const out: LineageEventRecord[] = []
    const lines = text.split("\n")
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim()
        if (!line) continue
        let obj: any
        try {
            obj = JSON.parse(line)
        } catch {
            continue
        }
        if (typeof obj !== "object" || obj === null || obj.type !== "lineage_selected") continue
        const status = String(obj.captureStatus ?? "")
        if (!CAPTURE_STATUSES.has(status as LineageEventCaptureStatus)) continue
        if (!Array.isArray(obj.ancestors)) continue
        const ancestors: LineageAncestorRecord[] = []
        for (const rawA of obj.ancestors) {
            const a = parseAncestor(rawA)
            if (a) ancestors.push(a)
        }
        out.push({
            type: "lineage_selected",
            schemaVersion: Number.isFinite(Number(obj.schemaVersion)) ? Number(obj.schemaVersion) : 0,
            launchTransactionId: typeof obj.launchTransactionId === "string" && obj.launchTransactionId ? obj.launchTransactionId : null,
            ts: Number.isFinite(Number(obj.ts)) ? Number(obj.ts) : null,
            scenario: String(obj.scenario ?? "unknown").replace(/_/g, " "),
            trainee: String(obj.trainee ?? "unknown"),
            overallAffinity: typeof obj.overallAffinity === "string" ? obj.overallAffinity : null,
            captureStatus: status as LineageEventCaptureStatus,
            ancestors,
            file,
            lineNumber: i,
        })
    }
    return out
}
