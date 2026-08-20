// ParentLab PL-3 - deterministic content-addressed Veteran identity. Pure, dependency-free.
//
// Outcome telemetry carries NO career token (the record's `fp` is a config-arm fingerprint, not a career
// id), so a Veteran's identity is a content hash of the normalized career evidence -- not ingestion order,
// not corpus filename. Two re-pulls of the same physical career copy the record verbatim (identical ts,
// stats, kept rows) and therefore hash equal; two genuinely distinct careers differ in the evidence tuple
// (distinct ts/turn/stats/kept set) and stay separate. The kept spark set is folded in so a reroll that
// changed only the retained sparks is not collapsed with the pre-reroll career.

import type { CareerSparks, SparkRecord } from "../outcomeAnalysis.ts"

/** Normalizes an OCR spark name for identity only: trim + collapse internal whitespace, casefold. Display keeps raw. */
export function normalizeSparkNameForIdentity(raw: string): string {
    return raw.trim().replace(/\s+/g, " ").toUpperCase()
}

/** The final confirmed kept set for a career: the LAST phase="kept" record, or null when none was observed. */
export function finalKeptRecord(c: CareerSparks): SparkRecord | null {
    return c.kept.length > 0 ? c.kept[c.kept.length - 1] : null
}

/**
 * Canonical, stable serialization of one confirmed career's evidence. Deterministic: field order is fixed
 * and the kept rows are sorted, so the string does not depend on corpus/file/row ordering. Only evidence
 * the corpus actually carries goes in -- never a filename, line number, or ingestion index.
 */
export function canonicalCareerEvidence(c: CareerSparks, kept: SparkRecord): string {
    const o = c.outcome
    const keptRows = kept.rows
        .map((r) => [r.kind, normalizeSparkNameForIdentity(r.name), r.stars] as const)
        .slice()
        .sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0) || (a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0) || a[2] - b[2])
    // A fixed-key object serialized with JSON.stringify: key order is the literal's insertion order, stable.
    return JSON.stringify({
        v: 1,
        trainee: o.trainee,
        scenario: o.scenario,
        turn: o.turn,
        result: o.result,
        outcome: o.outcome,
        quality: o.quality ?? null,
        fans: o.fans,
        stats: [o.spd, o.sta, o.pwr, o.grt, o.wit],
        skillPts: o.skillPts,
        finaleRaces: o.finaleRaces ?? null,
        finaleWins: o.finaleWins ?? null,
        // ts is written once on-device and copied verbatim on every pull: identical across duplicates of one
        // physical career, distinct between careers. It is career evidence (when it ended), not ingestion order.
        ts: o.ts ?? null,
        kept: keptRows,
    })
}

// FNV-1a over two lanes with different offset bases, concatenated to a 128-bit hex digest. BigInt keeps the
// arithmetic exact; no Math.random, no Date, no node:crypto -- fully pure and identical on every platform.
const FNV_PRIME = 0x100000001b3n
const MASK64 = (1n << 64n) - 1n
const OFFSET_A = 0xcbf29ce484222325n
const OFFSET_B = 0x84222325cbf29ce4n

function fnv1a64(bytes: Uint8Array, offset: bigint): bigint {
    let h = offset
    for (let i = 0; i < bytes.length; i++) {
        h ^= BigInt(bytes[i])
        h = (h * FNV_PRIME) & MASK64
    }
    return h
}

function utf8Bytes(s: string): Uint8Array {
    // TextEncoder is available in Node and RN; deterministic UTF-8. Avoids any platform Buffer dependency.
    return new TextEncoder().encode(s)
}

/** 128-bit content hash of a string, as 32 lowercase hex chars. Collision-resistant far beyond corpus scale. */
export function contentHash128(s: string): string {
    const bytes = utf8Bytes(s)
    const a = fnv1a64(bytes, OFFSET_A)
    const b = fnv1a64(bytes, OFFSET_B)
    const hex = (x: bigint) => x.toString(16).padStart(16, "0")
    return hex(a) + hex(b)
}

/** Derives a Veteran's deterministic local id from its canonical evidence string. */
export function veteranIdFor(canonical: string): string {
    return contentHash128(canonical)
}
