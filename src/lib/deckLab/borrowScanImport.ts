// DeckLab - the bridge from the on-device borrow-pool scan to the offline resolver. Pure,
// deterministic, read-only.
//
// The Kotlin read-only census (CareerLaunchNavigator.scanBorrowPoolReadOnly) writes an append-only
// JSONL: one `borrow_pool_row` record per observed row and one `borrow_pool_scan` header. This module
// turns that raw device output into the BorrowPoolSnapshot the existing resolveBorrowPool consumes,
// with no field remapping (the Kotlin side already emits the snake_case keys parseBorrowPoolSnapshot
// accepts) and one privacy rule enforced here: the raw owner name is dropped, only the redacted alias
// crosses into the snapshot.

import { BorrowPoolError, parseBorrowPoolSnapshot, type BorrowPoolSnapshot } from "./borrowPool.ts"

/** The record kinds the JSONL carries. */
const ROW_RECORD = "borrow_pool_row"
const HEADER_RECORD = "borrow_pool_scan"

/** Keys copied verbatim from a row record into a snapshot entry. `owner_name_raw` is deliberately
 * NOT here: it is local diagnostic evidence and must never enter the snapshot. `blocked_tag` is
 * dropped too - it is a property of the scanning account's deck, not of the borrowable card. */
const ENTRY_KEYS = ["character", "title", "rarity", "support_type", "level", "limit_break_index", "source_type", "owner_alias", "entry_fingerprint", "confidence", "evidence", "page_index"] as const

function parseLine(line: string, lineNumber: number): Record<string, unknown> | null {
    const trimmed = line.trim()
    if (!trimmed) return null
    try {
        const value = JSON.parse(trimmed)
        if (!value || typeof value !== "object" || Array.isArray(value)) throw new BorrowPoolError(`borrow scan line ${lineNumber} is not a JSON object`)
        return value as Record<string, unknown>
    } catch (err) {
        if (err instanceof BorrowPoolError) throw err
        throw new BorrowPoolError(`borrow scan line ${lineNumber} is not valid JSON: ${err instanceof Error ? err.message : String(err)}`)
    }
}

/**
 * Reads the raw Kotlin borrow-scan JSONL into a BorrowPoolSnapshot.
 *
 * Behaviour that matters:
 *  - the owner's raw name is stripped; only the redacted alias survives;
 *  - the termination is taken from the scan header's `snapshot_termination`. A JSONL with no header
 *    (a truncated write) is a partial by construction: it falls back to BOUNDED_PARTIAL so an
 *    incomplete scan can never read as a complete pool;
 *  - the last header wins if more than one is present (an appended file holding several scans), and
 *    only rows carrying that header's `scan_id` are kept, so two concatenated scans never merge.
 */
export function parseBorrowScanJsonl(text: string): BorrowPoolSnapshot {
    const records = text
        .split(/\r?\n/)
        .map((line, i) => parseLine(line, i + 1))
        .filter((r): r is Record<string, unknown> => r !== null)
    const rows = records.filter((r) => r.record === ROW_RECORD)
    // Unknown record kinds are ignored, so the format can grow without breaking this reader. The last
    // header wins when several are present (an appended file holding more than one scan).
    const headers = records.filter((r) => r.record === HEADER_RECORD)
    const header: Record<string, unknown> | null = headers.length ? headers[headers.length - 1] : null

    // Keep only the rows belonging to the surviving header's scan, so concatenated scans never merge.
    const scanId = header && typeof header.scan_id === "string" ? (header.scan_id as string) : null
    const scanRows = scanId ? rows.filter((r) => r.scan_id === scanId) : rows

    const entries = scanRows.map((row) => {
        const entry: Record<string, unknown> = {}
        for (const key of ENTRY_KEYS) if (row[key] !== undefined && row[key] !== null) entry[key] = row[key]
        return entry
    })

    // No header means a truncated/partial write: never let it assert a complete pool.
    const termination = header && typeof header.snapshot_termination === "string" ? (header.snapshot_termination as string) : "BOUNDED_PARTIAL"

    const doc: Record<string, unknown> = {
        schema: header?.schema ?? "deck_lab_borrow_pool",
        scan_id: scanId ?? "device-scan",
        source_screen: "borrow_picker",
        observed_at: header?.completed_at ?? null,
        termination,
        entries,
    }
    return parseBorrowPoolSnapshot(doc)
}
