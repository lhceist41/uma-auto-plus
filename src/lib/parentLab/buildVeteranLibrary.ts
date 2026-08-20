// ParentLab PL-3 - the pure Veteran shadow-library builder.
//
// buildVeteranLibrary(corpus) reconstructs a deterministic, read-only library of account-owned Veterans
// from existing career telemetry. It REUSES `joinSparks` (never re-implements the spark<->career join),
// keeps only careers with a confirmed kept set, and collapses the heavy overlap between corpus pulls by
// content-addressed final-state identity (identity.ts). No filesystem writes, no ordering dependence, no
// fabrication: same input -> deep-equal output; reversed/shuffled input -> same output; overlapping
// sources -> one Veteran; a finalize-only re-report -> never a Veteran of its own.

import { joinSparks, isFinalizeOnly } from "../outcomeAnalysis.ts"
import type { CareerSparks, SparkRecord } from "../outcomeAnalysis.ts"
import {
    PARENTLAB_SCHEMA,
    PARENTLAB_SCHEMA_VERSION,
    SPARK_KIND_TO_CATEGORY,
} from "./types.ts"
import type {
    LineageCaptureStatus,
    SparkCategory,
    SparkCategoryCoverage,
    Veteran,
    VeteranCorpusInput,
    VeteranLibrary,
    VeteranLibraryDiagnostics,
    VeteranLineageAncestor,
    VeteranSpark,
} from "./types.ts"
import type { LineageAncestorRecord, LineageEventRecord } from "./lineage.ts"
import { canonicalCareerEvidence, finalKeptRecord, normalizeSparkNameForIdentity, veteranIdFor } from "./identity.ts"

/** Deterministically index lineage events by their launch id, keeping the strongest per id (more
 * ancestors, then a captured over a partial read, then the later timestamp) so a re-emitted event
 * cannot make the join order-dependent. Events with no launch id cannot join and are dropped. */
function indexLineageEvents(events: readonly LineageEventRecord[]): Map<string, LineageEventRecord> {
    const rank = (s: LineageEventRecord["captureStatus"]) => (s === "captured" ? 2 : s === "partial" ? 1 : 0)
    const better = (a: LineageEventRecord, b: LineageEventRecord): boolean =>
        a.ancestors.length !== b.ancestors.length
            ? a.ancestors.length > b.ancestors.length
            : rank(a.captureStatus) !== rank(b.captureStatus)
              ? rank(a.captureStatus) > rank(b.captureStatus)
              : (a.ts ?? 0) > (b.ts ?? 0)
    const byId = new Map<string, LineageEventRecord>()
    for (const e of events) {
        if (!e.launchTransactionId) continue
        const cur = byId.get(e.launchTransactionId)
        if (!cur || better(e, cur)) byId.set(e.launchTransactionId, e)
    }
    return byId
}

/** Map one parsed lineage-event ancestor to its Veteran-facing shape (structurally identical). */
function mapLineageAncestor(a: LineageAncestorRecord): VeteranLineageAncestor {
    return {
        role: a.role,
        slotIndex: a.slotIndex,
        portraitObserved: a.portraitObserved,
        rank: a.rank,
        ownership: a.ownership,
        matchStatus: a.matchStatus,
        probableVeteranId: a.probableVeteranId,
        hasLeadTriple: a.hasLeadTriple,
        completeness: a.completeness,
        factorFingerprint: a.factorFingerprint,
        factors: a.factors.map((f) => ({ kind: f.kind, displayText: f.displayText, stars: f.stars, ambiguous: f.ambiguous, clipped: f.clipped })),
    }
}

/**
 * Fold a joined lineage event into a Veteran's lineage and completeness. A `failed` (or ancestor-less)
 * event leaves the Veteran uncaptured - a failed read is not a capture. Otherwise the Veteran records
 * the launch id, the ancestors, and `captured`/`partial`, and `completeness.lineageCaptured` flips to
 * true (the score reflects one more of the six tracked dimensions).
 */
function withLineage(base: Omit<Veteran, "provenance">, launchTransactionId: string, event: LineageEventRecord): Omit<Veteran, "provenance"> {
    if (event.captureStatus === "failed" || event.ancestors.length === 0) return base
    const status: LineageCaptureStatus = event.captureStatus === "captured" ? "captured" : "partial"
    const flags = [base.completeness.resultCaptured, true /* kept */, true /* lineage */, false, false, false]
    return {
        ...base,
        lineage: {
            captureStatus: status,
            parents: null,
            grandparents: null,
            launchTransactionId,
            overallAffinity: event.overallAffinity,
            ancestors: event.ancestors.map(mapLineageAncestor),
        },
        completeness: { ...base.completeness, lineageCaptured: true, score: flags.filter(Boolean).length / flags.length },
    }
}

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
        lineage: { captureStatus: "uncaptured", parents: null, grandparents: null, launchTransactionId: null, overallAffinity: null, ancestors: null },
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

/** One kept-bearing observation of a career, tagged with the finalize-only classification. */
interface Observation {
    c: CareerSparks
    kept: SparkRecord
    finalizeOnly: boolean
}

interface Group {
    /** Every kept-bearing observation sharing this final-state identity (real completions and re-reports). */
    observations: Observation[]
    /** Distinct canonical strings seen under this veteranId (>1 signals a hash collision). */
    canonicalVariants: Set<string>
}

/**
 * Deterministic anchor for a career group: the real observation ordered first by (ts, turn, result,
 * outcome, quality, file). Every real observation in a group shares the final-state identity, so their
 * Veteran-facing fields are identical; the ordering only makes the choice order-independent.
 */
function pickAnchor(reals: Observation[]): Observation {
    return reals.reduce((best, cur) => (anchorCmp(cur.c, best.c) < 0 ? cur : best))
}

function anchorCmp(a: CareerSparks, b: CareerSparks): number {
    const x = a.outcome
    const y = b.outcome
    const num = (v: number | null | undefined) => v ?? Number.MAX_SAFE_INTEGER
    return (
        num(x.ts) - num(y.ts) ||
        num(x.turn) - num(y.turn) ||
        x.result.localeCompare(y.result) ||
        x.outcome.localeCompare(y.outcome) ||
        (x.quality ?? "").localeCompare(y.quality ?? "") ||
        (x.file ?? "").localeCompare(y.file ?? "")
    )
}

/**
 * Builds the Veteran shadow library from a merged corpus (outcomes + spark records). Pure and deterministic.
 * The single source of "which spark belongs to which career" is `joinSparks`; this function decides which
 * joined careers become Veterans, how duplicates collapse, and how the result is shaped and sorted.
 *
 * A career is grouped by its final-state identity (identity.ts). ADMISSION INVARIANT: a group becomes a
 * Veteran only when a real (non-finalize-only) observation anchors it. A finalize-only observation -- a
 * re-report of an already-finished Complete Career screen, which the bot walks without ever playing the
 * career -- never creates a Veteran: it either folds into a matching real career as a duplicate, or, when
 * no real career shares its final state, is surfaced as an orphan and admitted as no Veteran at all.
 */
export function buildVeteranLibrary(corpus: VeteranCorpusInput): VeteranLibrary {
    const outcomes = corpus.outcomes as Parameters<typeof joinSparks>[0]
    const sparks = corpus.sparks as Parameters<typeof joinSparks>[1]
    const join = joinSparks(outcomes, sparks)
    const lineageByTxId = indexLineageEvents(corpus.lineageEvents ?? [])

    const malformedSparkRows = corpus.sparks.reduce((sum, s) => sum + s.droppedRows, 0)

    let keptCareerInstances = 0
    let realKeptInstances = 0
    let finalizeOnlyObservations = 0
    let incompleteCareersSkipped = 0
    const groups = new Map<string, Group>()

    for (const c of join.careers) {
        const kept = finalKeptRecord(c)
        if (!kept) {
            // Has spark evidence (original/rerolled) but no confirmed kept set: NOT a finalized Veteran.
            incompleteCareersSkipped++
            continue
        }
        keptCareerInstances++
        const finalizeOnly = isFinalizeOnly(c.outcome)
        if (finalizeOnly) finalizeOnlyObservations++
        else realKeptInstances++
        const canonical = canonicalCareerEvidence(c, kept)
        const veteranId = veteranIdFor(canonical)
        let g = groups.get(veteranId)
        if (!g) {
            g = { observations: [], canonicalVariants: new Set() }
            groups.set(veteranId, g)
        }
        g.observations.push({ c, kept, finalizeOnly })
        g.canonicalVariants.add(canonical)
    }

    let identityCollisions = 0
    let finalizeOnlyCollapsed = 0
    let finalizeOnlyOrphans = 0
    const veterans: Veteran[] = []
    for (const [veteranId, g] of groups) {
        if (g.canonicalVariants.size > 1) identityCollisions += g.canonicalVariants.size - 1
        const reals = g.observations.filter((o) => !o.finalizeOnly)
        const finCount = g.observations.length - reals.length
        if (reals.length === 0) {
            // Orphan: only finalize-only re-reports share this final state, and none is a played career.
            finalizeOnlyOrphans += finCount
            continue
        }
        finalizeOnlyCollapsed += finCount
        const anchor = pickAnchor(reals)
        // Join by launchTransactionId only (never timestamp guessing): the id lives on the career's
        // real observation, and one launch maps to exactly one career. No id, or no matching event,
        // leaves the Veteran's lineage uncaptured - which every historical Veteran is.
        const launchTxId = reals.map((o) => o.c.outcome.launchTransactionId).find((id): id is string => !!id) ?? null
        const event = launchTxId ? lineageByTxId.get(launchTxId) : undefined
        const base = veteranFrom(anchor.c, anchor.kept, veteranId)
        veterans.push({
            ...(event ? withLineage(base, launchTxId as string, event) : base),
            provenance: {
                files: [...new Set(reals.map((o) => o.c.outcome.file ?? ""))].sort(),
                observations: reals.length,
                finalizeOnlyCollapsed: finCount,
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
        realKeptInstances,
        confirmedVeterans: veterans.length,
        duplicatesCollapsed: realKeptInstances - veterans.length,
        incompleteCareersSkipped,
        finalizeOnlyObservations,
        finalizeOnlyCollapsed,
        finalizeOnlyOrphans,
        sparkRecordsJoined: join.joinedCount,
        sparkRecordsUnjoined: join.unjoined.length,
        malformedSparkRows,
        traineeCount: new Set(veterans.map((v) => v.trainee)).size,
        categoryCoverage: coverageOf(veterans),
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
