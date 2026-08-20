// ParentLab PL-3 - deterministic content-addressed Veteran identity. Pure, dependency-free.
//
// Outcome telemetry carries NO career token (the record's `fp` is a config-arm fingerprint, not a career
// id), so a Veteran's identity is a content hash of the FINAL SAVED STATE of the career -- not ingestion
// order, not corpus filename, and deliberately NOT the observation-level fields (`ts`, `turn`, `result`,
// `outcome`, `quality`). Identity is the inheritable end state: trainee, scenario, fans, final stats,
// skill points, and the kept spark set. Two re-pulls of one career copy that state verbatim and hash
// equal; two genuinely distinct careers differ somewhere in that state and stay separate. `ts`/`turn`
// are observation provenance, handled by the builder -- keeping them OUT of identity is what stops a
// finalize-only re-report (which re-reads the screen with a fresh timestamp and turn) from ever forging a
// second identity for a career that is already represented (see buildVeteranLibrary.ts).

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
 * Canonical, stable serialization of one career's FINAL SAVED STATE. Deterministic: field order is fixed
 * and the kept rows are sorted, so the string does not depend on corpus/file/row ordering. Only the
 * inheritable end state goes in -- trainee, scenario, fans, final stats, skill points, kept spark set.
 * Observation-level fields (`ts`, `turn`, `result`, `outcome`, `quality`) are deliberately excluded so that
 * every observation of one career -- a normal completion or a later finalize-only re-report -- serializes
 * to the SAME identity. The builder then keeps a career only when a real (non-finalize-only) observation
 * anchors it, and merges the rest as provenance.
 */
export function canonicalCareerEvidence(c: CareerSparks, kept: SparkRecord): string {
    const o = c.outcome
    const keptRows = kept.rows
        .map((r) => [r.kind, normalizeSparkNameForIdentity(r.name), r.stars] as const)
        .slice()
        .sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0) || (a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0) || a[2] - b[2])
    // A fixed-key object serialized with JSON.stringify: key order is the literal's insertion order, stable.
    return JSON.stringify({
        v: 2,
        trainee: o.trainee,
        scenario: o.scenario,
        fans: o.fans,
        stats: [o.spd, o.sta, o.pwr, o.grt, o.wit],
        skillPts: o.skillPts,
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
