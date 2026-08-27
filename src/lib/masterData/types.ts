// Local Master-Data Compiler v1 - shared types and version constants.
//
// The committed `src/data/*.json` raw files stay the single authority. This layer adds a *derived*
// canonical projection (skills + races), a provenance manifest, and a deterministic dataset fingerprint.
// Nothing here mutates the raw layer, reaches the network, or touches Android/runtime.

/** Manifest wire version. Bump only on a breaking manifest-shape change. */
export const MANIFEST_SCHEMA_VERSION = 1
/** Compiled skills artifact wire version. */
export const SKILLS_SCHEMA_VERSION = 1
/** Compiled races artifact wire version. */
export const RACES_SCHEMA_VERSION = 1
/**
 * Compiler implementation identity, recorded in the manifest. A hand-maintained constant, not a git SHA,
 * so a normal compile never requires git to be installed (Part A). Bump on any change to compiled output.
 */
export const COMPILER_VERSION = "1.0.0"
/** Normalization identity: the field-mapping/sorting rules. Part of the deterministic fingerprint. */
export const NORMALIZATION_VERSION = 1

// Exit codes (worst wins). Compatible with the repo's analyzer conventions.
/** No errors, no warnings, and (in --check) generated output matches committed. */
export const EXIT_CLEAN = 0
/** Compiled successfully but surfaced non-blocking warnings (or, in --check, warnings with matching output). */
export const EXIT_WARNINGS = 1
/** A hard validation error or a CLI-usage error. No artifact is written. */
export const EXIT_VALIDATION = 2
/** --check only: generated artifacts differ from the committed ones (stale). Nothing is rewritten. */
export const EXIT_STALE = 3

/** The six authoritative raw families. Only skills + races are compiled; all six are integrity-checked. */
export type RawFamily = "skills" | "races" | "characters" | "supports" | "scenarios" | "objectives"

/** One raw input file, as read by the CLI and handed to the pure compiler (exact bytes preserved for hashing). */
export interface RawInput {
    family: RawFamily
    /** Normalized repository-relative path, e.g. `src/data/skills.json`. */
    path: string
    /** Exact file contents (used verbatim for the SHA256; never reformatted). */
    bytes: string
}

/** A compiled skill. Field names are canonicalized from the raw layer; null is preserved, never defaulted. */
export interface CompiledSkill {
    /** Canonical identity: the raw numeric `id`. Never renumbered. */
    id: number
    /** From raw `name_en`. */
    name: string
    /** From raw `desc_en`. */
    desc: string | null
    /** From raw `icon_id`. */
    iconId: number | null
    /** From raw `cost`. Null when the source did not record one (never inferred). */
    cost: number | null
    /** From raw `eval_pt`. Null when unrecorded. */
    evalPt: number | null
    /** Machine-readable activation expression, preserved verbatim from raw `condition`. */
    condition: string | null
    /** Machine-readable precondition expression, preserved verbatim from raw `precondition`. */
    precondition: string | null
    /** From raw `inherited`. */
    inherited: boolean | null
    /** From raw `community_tier`: 0=SS, 1=S, 2=A, 3=B. Null when the skill isn't on the community tier list
     * (JP-only, shop-less, negative) or when two tier-list sections disagreed on it. */
    tier: number | null
    /** Chain reference (raw `upgrade`): a skill `id`, or null. May reference an id absent from this dataset - see the manifest's chainReferenceUnresolved warnings. */
    upgrade: number | null
    /** Chain reference (raw `downgrade`): a skill `id`, or null. */
    downgrade: number | null
}

/** The canonical composite race identity: `(name, turnNumber)`, proven unique across the current dataset. */
export interface RaceKey {
    name: string
    turnNumber: number
}

/** One placement's fan payout: finishing position [place] pays [fans] fans. */
export interface RaceFanPayout {
    place: number
    fans: number
}

/** A compiled race. Identity is the composite key; no numeric race id is invented. Null course is preserved. */
export interface CompiledRace {
    key: RaceKey
    name: string
    turnNumber: number
    date: string | null
    grade: string
    raceTrack: string
    course: string | null
    direction: string
    terrain: string
    distanceType: string
    distanceMeters: number
    /** First-place (win) fan reward. Kept scalar for backwards compatibility. */
    fans: number
    /** The full placement-to-fans payout curve when the source provides it (order-sorted by
     * [RaceFanPayout.place]); omitted for races whose raw record predates the field. [fans] equals
     * the place-1 payout. A later races-needed model reads this instead of assuming a win. */
    fanPayoutsByPlace?: RaceFanPayout[]
    nameFormatted: string | null
}

/** Provenance for one raw source file. Absent upstream versions are recorded as explicit null, never invented. */
export interface RawSourceEntry {
    family: RawFamily
    path: string
    sha256: string
    recordCount: number
    /** `origin` is the repo-known generator; `sourceVersion` is null because the raw layer records none. */
    provenance: { origin: string; sourceVersion: string | null }
}

/** Provenance + hash + count for one compiled artifact. */
export interface CompiledArtifactEntry {
    path: string
    sha256: string
    recordCount: number
}

/** A non-blocking validation finding. */
export interface ValidationFinding {
    code: string
    detail: string
}

/** The manifest's validation summary. Detailed diagnostics go to stdout/stderr, not here. */
export interface ValidationSummary {
    warnings: ValidationFinding[]
    info: ValidationFinding[]
}

/** The compiled dataset manifest. Deterministic: contains no wall-clock time and no absolute paths. */
export interface MasterDataManifest {
    manifestSchemaVersion: number
    compiler: {
        version: string
        normalizationVersion: number
        skillsSchemaVersion: number
        racesSchemaVersion: number
    }
    /** Deterministic content fingerprint over compiler versions + sorted raw hashes + compiled hashes. */
    fingerprint: string
    /** Repo-known provenance; every upstream version field is explicit null (the raw layer stores none). */
    knownProvenance: {
        generator: string
        recordedSourceVersion: null
        recordedGamePatch: null
        recordedScrapeTime: null
    }
    /** All six raw inputs, sorted by path. */
    source: RawSourceEntry[]
    /** Compiled artifacts (skills, races), sorted by path. */
    compiled: CompiledArtifactEntry[]
    validation: ValidationSummary
}

/** A hard validation error. Stops artifact generation; the CLI writes nothing and exits non-zero. */
export interface CompileError {
    code: string
    detail: string
}

/** Deterministic statistics returned for the real-data proof and tests (not embedded verbatim in artifacts). */
export interface CompileStats {
    skillRawCount: number
    skillCompiledCount: number
    uniqueSkillIdCount: number
    raceRawCount: number
    raceCompiledCount: number
    uniqueRaceKeyCount: number
    distinctBareRaceNameCount: number
    bareNameCollisionCount: number
    objectiveReferencesChecked: number
    objectiveReferencesUnresolved: number
}

/** The result of a compile. When `ok` is false, `artifacts` is null and no file must be written. */
export interface CompileResult {
    ok: boolean
    errors: CompileError[]
    /** Canonical JSON strings for the three artifacts, ready to write byte-for-byte. Null on hard failure. */
    artifacts: { skills: string; races: string; manifest: string } | null
    manifest: MasterDataManifest | null
    validation: ValidationSummary
    stats: CompileStats
    fingerprint: string | null
    exitCode: number
}
