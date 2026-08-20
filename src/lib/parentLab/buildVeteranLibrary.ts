// ParentLab PL-3 - the pure Veteran shadow-library builder.
//
// buildVeteranLibrary(corpus) reconstructs a deterministic, read-only library of account-owned Veterans
// from existing career telemetry. It REUSES `joinSparks` (never re-implements the spark<->career join),
// keeps only careers with a confirmed kept set, and collapses the heavy overlap between corpus pulls by
// content-addressed identity (identity.ts). No filesystem writes, no ordering dependence, no fabrication:
// same input -> deep-equal output; reversed/shuffled input -> same output; overlapping sources -> one Veteran.

import { joinSparks, isFinalizeOnly } from "../outcomeAnalysis.ts"
import type { CareerSparks, SparkRecord } from "../outcomeAnalysis.ts"
import {
    PARENTLAB_SCHEMA,
    PARENTLAB_SCHEMA_VERSION,
    SPARK_KIND_TO_CATEGORY,
} from "./types.ts"
import type {
    SparkCategory,
    SparkCategoryCoverage,
    Veteran,
    VeteranCorpusInput,
    VeteranLibrary,
    VeteranLibraryDiagnostics,
    VeteranSpark,
} from "./types.ts"
import { canonicalCareerEvidence, finalKeptRecord, normalizeSparkNameForIdentity, veteranIdFor } from "./identity.ts"

/** Maps a raw telemetry spark kind to its color category, degrading unknown kinds to `other`. */
function categoryOf(kind: string): SparkCategory {
    return SPARK_KIND_TO_CATEGORY[kind] ?? "other"
}

/** Builds the sorted, deterministic kept spark list for a Veteran. Canonical IDs stay unresolved (PL-3). */
function veteranSparks(kept: SparkRecord): VeteranSpark[] {
    return kept.rows
        .map((r): VeteranSpark => ({
            displayText: r.name.trim().replace(/\s+/g, " "),
            kind: r.kind,
            category: categoryOf(r.kind),
            stars: r.stars,
            canonicalFactorId: null,
            canonicalResolved: false,
        }))
        .slice()
        .sort(
            (a, b) =>
                (a.kind < b.kind ? -1 : a.kind > b.kind ? 1 : 0) ||
                (normalizeSparkNameForIdentity(a.displayText) < normalizeSparkNameForIdentity(b.displayText) ? -1 : normalizeSparkNameForIdentity(a.displayText) > normalizeSparkNameForIdentity(b.displayText) ? 1 : 0) ||
                a.stars - b.stars,
        )
}

/** Assembles a single Veteran (minus provenance, filled by the caller) from one confirmed kept-bearing career. */
function veteranFrom(c: CareerSparks, kept: SparkRecord, veteranId: string): Omit<Veteran, "provenance"> {
    const o = c.outcome
    const resultCaptured = o.turn !== null
    // Six tracked completeness flags; only the two the corpus can prove are ever true in PL-3.
    const flags = [resultCaptured, true /* keptSparksCaptured */, false /* lineage */, false /* raceHistory */, false /* skills */, false /* canonicalSparkIds */]
    const score = flags.filter(Boolean).length / flags.length
    return {
        schema: PARENTLAB_SCHEMA,
        schemaVersion: PARENTLAB_SCHEMA_VERSION,
        veteranId,
        // Outcome telemetry carries no career token; `fp` is a config-arm fingerprint, not a career id.
        sourceCareerToken: null,
        trainee: o.trainee,
        traineeDisplayName: o.trainee,
        scenario: o.scenario,
        completedAt: o.ts ?? null,
        result: {
            scenario: o.scenario,
            quality: o.quality ?? null,
            result: o.result,
            outcome: o.outcome,
            turn: o.turn,
            fans: o.fans,
            finalStats: { spd: o.spd, sta: o.sta, pwr: o.pwr, grt: o.grt, wit: o.wit },
            skillPts: o.skillPts,
            finaleRaces: o.finaleRaces ?? null,
            finaleWins: o.finaleWins ?? null,
            rank: null,
            score: null,
        },
        sparks: veteranSparks(kept),
        lineage: { captureStatus: "uncaptured", parents: null, grandparents: null },
        completeness: {
            resultCaptured,
            keptSparksCaptured: true,
            lineageCaptured: false,
            raceHistoryCaptured: false,
            skillsCaptured: false,
            canonicalSparkIdsResolved: false,
            score,
        },
    }
}

interface Accumulator {
    base: Omit<Veteran, "provenance">
    files: Set<string>
    observations: number
    finalizeOnly: boolean
    canonical: string
    /** Distinct canonical strings seen under this veteranId (>1 signals a hash collision). */
    canonicalVariants: Set<string>
}

/**
 * Builds the Veteran shadow library from a merged corpus (outcomes + spark records). Pure and deterministic.
 * The single source of "which spark belongs to which career" is `joinSparks`; this function only decides which
 * joined careers become Veterans, how duplicates collapse, and how the result is shaped and sorted.
 */
export function buildVeteranLibrary(corpus: VeteranCorpusInput): VeteranLibrary {
    const outcomes = corpus.outcomes as Parameters<typeof joinSparks>[0]
    const sparks = corpus.sparks as Parameters<typeof joinSparks>[1]
    const join = joinSparks(outcomes, sparks)

    const malformedSparkRows = corpus.sparks.reduce((sum, s) => sum + s.droppedRows, 0)

    let keptCareerInstances = 0
    let incompleteCareersSkipped = 0
    const acc = new Map<string, Accumulator>()

    for (const c of join.careers) {
        const kept = finalKeptRecord(c)
        if (!kept) {
            // Has spark evidence (original/rerolled) but no confirmed kept set: NOT a finalized Veteran.
            incompleteCareersSkipped++
            continue
        }
        keptCareerInstances++
        const canonical = canonicalCareerEvidence(c, kept)
        const veteranId = veteranIdFor(canonical)
        const file = c.outcome.file ?? ""
        const existing = acc.get(veteranId)
        if (existing) {
            existing.files.add(file)
            existing.observations++
            existing.canonicalVariants.add(canonical)
            continue
        }
        acc.set(veteranId, {
            base: veteranFrom(c, kept, veteranId),
            files: new Set([file]),
            observations: 1,
            finalizeOnly: isFinalizeOnly(c.outcome),
            canonical,
            canonicalVariants: new Set([canonical]),
        })
    }

    let identityCollisions = 0
    const veterans: Veteran[] = []
    for (const a of acc.values()) {
        if (a.canonicalVariants.size > 1) identityCollisions += a.canonicalVariants.size - 1
        veterans.push({
            ...a.base,
            provenance: {
                files: [...a.files].sort(),
                observations: a.observations,
                finalizeOnly: a.finalizeOnly,
            },
        })
    }

    // Deterministic order independent of corpus/file ordering: veteranId is the total-order tiebreaker.
    veterans.sort(
        (x, y) =>
            x.trainee.localeCompare(y.trainee) ||
            x.scenario.localeCompare(y.scenario) ||
            (x.veteranId < y.veteranId ? -1 : x.veteranId > y.veteranId ? 1 : 0),
    )

    const diagnostics: VeteranLibraryDiagnostics = {
        careerOutcomes: corpus.outcomes.length,
        keptCareerInstances,
        confirmedVeterans: veterans.length,
        duplicatesCollapsed: keptCareerInstances - veterans.length,
        incompleteCareersSkipped,
        sparkRecordsJoined: join.joinedCount,
        sparkRecordsUnjoined: join.unjoined.length,
        malformedSparkRows,
        traineeCount: new Set(veterans.map((v) => v.trainee)).size,
        categoryCoverage: coverageOf(veterans),
        finalizeOnlyVeterans: veterans.filter((v) => v.provenance.finalizeOnly).length,
        identityCollisions,
    }

    return { schema: PARENTLAB_SCHEMA, schemaVersion: PARENTLAB_SCHEMA_VERSION, veterans, diagnostics }
}

/** Counts, per color category, how many Veterans carry at least one spark of that color. */
function coverageOf(veterans: readonly Veteran[]): SparkCategoryCoverage {
    let blue = 0,
        red = 0,
        green = 0,
        white = 0,
        other = 0,
        blueRedGreen = 0
    for (const v of veterans) {
        const has = new Set(v.sparks.map((s) => s.category))
        if (has.has("blue")) blue++
        if (has.has("red")) red++
        if (has.has("green")) green++
        if (has.has("white")) white++
        if (has.has("other")) other++
        if (has.has("blue") && has.has("red") && has.has("green")) blueRedGreen++
    }
    return { blue, red, green, white, other, blueRedGreen }
}
