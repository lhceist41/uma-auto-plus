import { existsSync, readdirSync, readFileSync, statSync } from "node:fs"
import { basename, extname, join, relative } from "node:path"
import process from "node:process"
import { parseCorpus } from "../../outcomeAnalysis.ts"
import { buildVeteranLibrary } from "../buildVeteranLibrary.ts"
import type { VeteranCorpusInput } from "../types.ts"

// Full-backfill acceptance against the real career corpus. The corpus lives under `validation/corpus/`
// (a set of overlapping on-device pulls) which is gitignored per repo convention, so this suite is
// PRESENCE-GUARDED: it runs and asserts the exact audited counts where the corpus is present (the
// maintainer's machine), and skips with a note where it is absent (a fresh clone / CI). The hermetic
// behavioral coverage in buildVeteranLibrary.test.ts is what always runs.
//
// Expected counts are the ParentLab architecture audit's targets, reproduced exactly from this corpus:
//   204 unique Veterans, 32 trainees, 4724 joined spark records, 0 unjoined, 204/204 blue+red+green.
// If a future corpus change moves these, do NOT casually edit the numbers here: confirm the change is
// legitimate, then update with the reason recorded.

const CORPUS_DIR = join(process.cwd(), "validation", "corpus")
const AVAILABLE = existsSync(CORPUS_DIR)

const EXPECT = {
    veterans: 204,
    trainees: 32,
    joinedSparks: 4724,
    unjoined: 0,
    coverage: 204,
    // The 10 overlapping pulls carry 2005 raw kept-bearing career instances; content-addressed identity
    // collapses them to 204 (1801 duplicates). These pin that the real cross-pull dedupe ran -- a loader that
    // fused the pulls under one file id would report 204 kept instances and 0 collapsed instead.
    keptInstances: 2005,
    duplicatesCollapsed: 1801,
} as const

function collectCareerFiles(path: string, depth = 0): string[] {
    const st = statSync(path)
    if (st.isFile()) return extname(path).toLowerCase() === ".jsonl" && basename(path) === "careers.jsonl" ? [path] : []
    if (!st.isDirectory() || depth > 6) return []
    return readdirSync(path).flatMap((e) => collectCareerFiles(join(path, e), depth + 1))
}

function loadCorpus(): VeteranCorpusInput {
    const outcomes = []
    const sparks = []
    for (const file of collectCareerFiles(CORPUS_DIR)) {
        // DISTINCT id per pull (every file is named careers.jsonl): joinSparks associates within a file, so a
        // shared basename would fuse all pulls into one timeline. The per-pull relative path keeps them apart,
        // leaving the cross-pull overlap for the builder's content-addressed dedupe to collapse.
        const fileId = relative(CORPUS_DIR, file).replace(/\\/g, "/")
        const parsed = parseCorpus(readFileSync(file, "utf8"), fileId)
        outcomes.push(...parsed.outcomes)
        sparks.push(...parsed.sparks)
    }
    return { outcomes, sparks }
}

const suite = AVAILABLE ? describe : describe.skip

if (!AVAILABLE) {
    // A visible breadcrumb so a skipped acceptance run is never mistaken for a passing one.
    console.warn(`[parentLab] backfill acceptance skipped: ${CORPUS_DIR} not present (gitignored corpus).`)
}

suite("PL-3 full-backfill acceptance (real corpus)", () => {
    const lib = AVAILABLE ? buildVeteranLibrary(loadCorpus()) : null

    it("emits exactly 204 confirmed Veterans across 32 trainees", () => {
        expect(lib!.veterans).toHaveLength(EXPECT.veterans)
        expect(lib!.diagnostics.confirmedVeterans).toBe(EXPECT.veterans)
        expect(lib!.diagnostics.traineeCount).toBe(EXPECT.trainees)
    })

    it("joins every spark record with none left unjoined", () => {
        expect(lib!.diagnostics.sparkRecordsJoined).toBe(EXPECT.joinedSparks)
        expect(lib!.diagnostics.sparkRecordsUnjoined).toBe(EXPECT.unjoined)
    })

    it("has blue, red, and green coverage on all 204 Veterans", () => {
        const c = lib!.diagnostics.categoryCoverage
        expect(c.blue).toBe(EXPECT.coverage)
        expect(c.red).toBe(EXPECT.coverage)
        expect(c.green).toBe(EXPECT.coverage)
        expect(c.blueRedGreen).toBe(EXPECT.coverage)
    })

    it("collapses the overlapping pulls by content identity (no collisions) and rebuilds deep-equal", () => {
        expect(lib!.diagnostics.keptCareerInstances).toBe(EXPECT.keptInstances)
        expect(lib!.diagnostics.duplicatesCollapsed).toBe(EXPECT.duplicatesCollapsed)
        expect(lib!.diagnostics.duplicatesCollapsed).toBe(lib!.diagnostics.keptCareerInstances - EXPECT.veterans)
        expect(lib!.diagnostics.identityCollisions).toBe(0)
        expect(buildVeteranLibrary(loadCorpus())).toEqual(lib)
    })

    it("is independent of corpus input ordering", () => {
        const input = loadCorpus()
        const reversed = buildVeteranLibrary({ outcomes: [...input.outcomes].reverse(), sparks: [...input.sparks].reverse() })
        expect(reversed).toEqual(lib)
    })

    it("defers every canonical factor id and captures no lineage", () => {
        for (const v of lib!.veterans) {
            expect(v.lineage.captureStatus).toBe("uncaptured")
            expect(v.completeness.canonicalSparkIdsResolved).toBe(false)
            for (const s of v.sparks) expect(s.canonicalFactorId).toBeNull()
        }
    })
})
