// ParentLab PL-3 - Veteran shadow-library types. Pure, offline, deterministic, read-only.
//
// A "Veteran" here is one account-owned career the bot produced, reconstructed from existing career
// telemetry (the `outcomeAnalysis` corpus: career outcomes + `type:"sparks"` records). This is a DERIVED
// index rebuilt from corpus every time: it stores no facts the corpus cannot see and writes nothing.
//
// Two hard limits of the current telemetry are represented explicitly, never fabricated:
//   - Lineage (selected parents/grandparents) is not captured by current automation -> `uncaptured`,
//     which is NOT the same as a known-empty lineage.
//   - Canonical in-game factor IDs are deferred (the local master.mdb the audit checked was stale) ->
//     every spark's `canonicalFactorId` is null and `canonicalResolved` is false in PL-3.
//
// No RaceLab/ReplayLab/Android/runtime imports: this is a leaf analysis module like shadowAdvisor/types.

import type { OutcomeRecord, SparkRecord } from "../outcomeAnalysis.ts"

/** Library schema discriminator + version. Bumped only on a breaking Veteran/library shape change. Not the app version. */
export const PARENTLAB_SCHEMA = "parent_lab_veteran" as const
export const PARENTLAB_SCHEMA_VERSION = 1 as const

/**
 * Spark color category derived from the telemetry `kind` (pixel-classified, authoritative):
 *   stat -> blue, aptitude -> red, unique -> green, skill -> white.
 * `other` is the deliberate fallback for any future kind current parsing does not distinguish: PL-3
 * never assumes only blue/red/green exist, and never guesses a category from an unresolved OCR name.
 */
export type SparkCategory = "blue" | "red" | "green" | "white" | "other"

/** Maps a raw telemetry spark kind to its color category. Unknown kinds degrade to `other`, never dropped. */
export const SPARK_KIND_TO_CATEGORY: Readonly<Record<string, SparkCategory>> = {
    stat: "blue",
    aptitude: "red",
    unique: "green",
    skill: "white",
}

/**
 * One kept spark on a Veteran. `stars` and `kind` are pixel-classified (authoritative); `displayText`
 * is raw OCR and is NOT authoritative for identity. `canonicalFactorId` is the in-game factor ID a later
 * task will resolve from a fresh master.mdb: null/unresolved in PL-3, never guessed from `displayText`.
 */
export interface VeteranSpark {
    /** Normalized (trimmed, whitespace-collapsed) OCR display text. Not authoritative; may be unreadable. */
    readonly displayText: string
    /** Raw telemetry kind: stat | aptitude | unique | skill (or any future pixel-classified kind). */
    readonly kind: string
    /** Color category derived from `kind` via SPARK_KIND_TO_CATEGORY (fallback `other`). */
    readonly category: SparkCategory
    /** Star count 0-3, pixel-classified (no OCR). */
    readonly stars: number
    /** Canonical in-game factor ID. Deferred in PL-3: always null. */
    readonly canonicalFactorId: null
    /** Whether `canonicalFactorId` has been resolved. Always false in PL-3. */
    readonly canonicalResolved: boolean
}

/** Lineage capture status. PL-3 only ever produces `uncaptured` (current automation reads no parents). */
export type LineageCaptureStatus = "uncaptured"

/**
 * A Veteran's lineage. Current automation does not read selected parents/grandparents, so historical
 * Veterans have `captureStatus: "uncaptured"` with parents/grandparents null. Null here means "the
 * capture never happened", explicitly distinct from a known-empty lineage (which would be `[]`).
 */
export interface VeteranLineage {
    readonly captureStatus: LineageCaptureStatus
    /** null = not captured (NOT known-empty). A future capture stage sets this to a concrete array. */
    readonly parents: null
    /** null = not captured (NOT known-empty). */
    readonly grandparents: null
}

/**
 * Result evidence for a Veteran, drawn from the joined outcome record. Fields the corpus does not carry
 * (in-game rank, career score, race history, learned skills) are null/absent, never fabricated.
 */
export interface VeteranResult {
    readonly scenario: string
    /** Career-quality label if present (WIN / FINALE_LOST / COMPLETED / FORCE_END / INCOMPLETE), else null. */
    readonly quality: string | null
    /** Raw result code (BREAKPOINT_REACHED / COMPLETE / UNHANDLED_EXCEPTION / ...). */
    readonly result: string
    /** Outcome bucket label from the record (COMPLETED / FORCE_END / INCOMPLETE). */
    readonly outcome: string
    /** End turn, or null when the bot never read an in-career date (finalize-only re-reports carry null). */
    readonly turn: number | null
    readonly fans: number
    readonly finalStats: {
        readonly spd: number
        readonly sta: number
        readonly pwr: number
        readonly grt: number
        readonly wit: number
    }
    readonly skillPts: number
    /** Finale races entered (URA finale only), or null when the record predates the feature / N.A. */
    readonly finaleRaces: number | null
    /** Finale races won outright, or null when unavailable. */
    readonly finaleWins: number | null
    /** In-game rank: NOT carried by outcome telemetry today. Always null in PL-3. */
    readonly rank: null
    /** Career score: NOT carried by outcome telemetry today. Always null in PL-3. */
    readonly score: null
}

/**
 * Per-Veteran completeness summary a later ranking stage can weigh. Each flag is factual: true only when
 * the evidence is actually present. Historical Veterans naturally report kept sparks captured, but
 * lineage / race history / skills / canonical spark IDs NOT captured. `score` is the fraction of the six
 * tracked dimensions that are captured (0..1), monotone with evidence; it never gates library membership.
 */
export interface VeteranCompleteness {
    /** Result evidence present: a real observed end (an in-career turn was read). False for finalize-only re-reports. */
    readonly resultCaptured: boolean
    /** A confirmed kept spark set is present (true for every confirmed Veteran). */
    readonly keptSparksCaptured: boolean
    /** Lineage captured. Always false in PL-3. */
    readonly lineageCaptured: boolean
    /** Race history captured. Always false in PL-3 (the joined corpus carries no clean per-Veteran race list). */
    readonly raceHistoryCaptured: boolean
    /** Learned skills captured. Always false in PL-3 (not cleanly joinable from the current corpus). */
    readonly skillsCaptured: boolean
    /** Canonical factor IDs resolved. Always false in PL-3 (deferred to a fresh master.mdb pull). */
    readonly canonicalSparkIdsResolved: boolean
    /** Fraction (0..1) of the six flags above that are true. */
    readonly score: number
}

/** Where a Veteran's evidence was observed. Overlapping corpus pulls make `files` length > 1 common. */
export interface VeteranProvenance {
    /** Distinct corpus file identifiers a REAL observation of this career appeared in, sorted, deduped. */
    readonly files: readonly string[]
    /** How many real (non-finalize-only) kept-bearing observations of this career were seen (>=1). */
    readonly observations: number
    /** Finalize-only re-reports that shared this career's final-state identity and were folded in as duplicates. */
    readonly finalizeOnlyCollapsed: number
}

/**
 * One account-owned Veteran, reconstructed read-only from corpus. `veteranId` is a DERIVED, LOCAL,
 * content-addressed fingerprint of the career's FINAL SAVED STATE (trainee, scenario, fans, stats, skill
 * points, kept sparks) -- deterministic and stable across rebuilds and corpus ordering, and identical for
 * every observation of the one career. It is NOT an in-game Veteran ID. Every confirmed Veteran is anchored
 * by at least one real (non-finalize-only) observation. `sourceCareerToken` is null in PL-3: outcome
 * telemetry carries a config-arm fingerprint (`fp`), which is not a career token.
 */
export interface Veteran {
    readonly schema: typeof PARENTLAB_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_SCHEMA_VERSION
    /** Deterministic content-addressed local fingerprint (hex). Stable identity; NOT an in-game ID. */
    readonly veteranId: string
    /** In-game / telemetry career token if one is ever carried. Null in PL-3 (outcomes have no career token). */
    readonly sourceCareerToken: string | null
    readonly provenance: VeteranProvenance
    /** Normalized trainee identity (parseCorpus normalization: underscores -> spaces, EI -> El). */
    readonly trainee: string
    /** Display name (same normalized string today; kept separate so a later display map can diverge). */
    readonly traineeDisplayName: string
    readonly scenario: string
    /** Completion timestamp (epoch millis) when the record carried one, else null. */
    readonly completedAt: number | null
    readonly result: VeteranResult
    /** Authoritative kept spark set (the final phase="kept" record's rows), sorted deterministically. */
    readonly sparks: readonly VeteranSpark[]
    readonly lineage: VeteranLineage
    readonly completeness: VeteranCompleteness
}

/** Per-category Veteran coverage: how many confirmed Veterans carry >=1 spark of each color. */
export interface SparkCategoryCoverage {
    readonly blue: number
    readonly red: number
    readonly green: number
    readonly white: number
    readonly other: number
    /** Veterans carrying at least one blue AND one red AND one green spark. */
    readonly blueRedGreen: number
}

/** Deterministic build diagnostics. These are structured data, never only logged. */
export interface VeteranLibraryDiagnostics {
    /** Distinct career outcomes present in the input corpus (pre-join, pre-dedupe). */
    readonly careerOutcomes: number
    /** Raw kept-bearing career instances found across all files, before identity dedupe (real + finalize-only). */
    readonly keptCareerInstances: number
    /** Raw kept-bearing instances that are REAL (non-finalize-only) observations. */
    readonly realKeptInstances: number
    /** Confirmed Veterans emitted (== library length). */
    readonly confirmedVeterans: number
    /** Real kept instances that collapsed into an existing Veteran (realKeptInstances - confirmedVeterans). */
    readonly duplicatesCollapsed: number
    /** Careers carrying spark evidence but NO confirmed kept set (original/rerolled only): not finalized. */
    readonly incompleteCareersSkipped: number
    /** Finalize-only kept observations seen (re-reports of an already-finished career; not new completions). */
    readonly finalizeOnlyObservations: number
    /** Finalize-only observations that shared a confirmed Veteran's final-state identity and folded in as duplicates. */
    readonly finalizeOnlyCollapsed: number
    /** Finalize-only observations whose final-state identity matched no real career: admitted as NO Veteran, surfaced here. */
    readonly finalizeOnlyOrphans: number
    /** Spark records associated to some career by joinSparks (all phases). */
    readonly sparkRecordsJoined: number
    /** Spark records joinSparks could not associate (no preceding career / hard mismatch). */
    readonly sparkRecordsUnjoined: number
    /** Malformed spark rows dropped during parsing (summed droppedRows). */
    readonly malformedSparkRows: number
    /** Distinct normalized trainees across confirmed Veterans. */
    readonly traineeCount: number
    readonly categoryCoverage: SparkCategoryCoverage
    /** Distinct canonical serializations that hash-collided onto one veteranId (expected 0). */
    readonly identityCollisions: number
}

/** The built shadow library: deterministic, sorted, duplicate-safe, with diagnostics. */
export interface VeteranLibrary {
    readonly schema: typeof PARENTLAB_SCHEMA
    readonly schemaVersion: typeof PARENTLAB_SCHEMA_VERSION
    /** Confirmed Veterans, sorted deterministically by (trainee, scenario, veteranId). */
    readonly veterans: readonly Veteran[]
    readonly diagnostics: VeteranLibraryDiagnostics
}

/**
 * Builder input: the merged corpus records from one or more parsed files. This is exactly the shape
 * `parseCorpus` yields per file (concatenate `outcomes` and `sparks` across files); a full `ParsedCorpus`
 * is assignable here structurally. The builder calls `joinSparks` itself; callers do not pre-join.
 */
export interface VeteranCorpusInput {
    readonly outcomes: readonly OutcomeRecord[]
    readonly sparks: readonly SparkRecord[]
}
