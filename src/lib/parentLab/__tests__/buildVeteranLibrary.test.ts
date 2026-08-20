import { parseCorpus } from "../../outcomeAnalysis.ts"
import { buildVeteranLibrary } from "../buildVeteranLibrary.ts"
import type { VeteranCorpusInput } from "../types.ts"
import { PARENTLAB_SCHEMA, PARENTLAB_SCHEMA_VERSION } from "../types.ts"

// ---- synthetic corpus builders -------------------------------------------
// Author JSONL exactly as the on-device writer would, then parse it with the real `parseCorpus` so the
// tests exercise the same parse path the CLI and acceptance test use. A "file" is a name plus its lines,
// in write order (a career line precedes the spark lines it anchors), matching the on-device append order.

type Row = [name: string, stars: number, kind: string]

function careerLine(o: Record<string, unknown>): string {
    return JSON.stringify({
        result: "BREAKPOINT_REACHED",
        outcome: "COMPLETED",
        trainee: "Test_Uma",
        scenario: "URA_Finale",
        turn: 75,
        fans: 100000,
        spd: 1000,
        sta: 900,
        pwr: 800,
        grt: 500,
        wit: 600,
        skillPts: 200,
        ...o,
    })
}

function sparkLine(phase: string, rows: Row[], o: Record<string, unknown> = {}): string {
    return JSON.stringify({ type: "sparks", phase, rows: rows.map(([name, stars, kind]) => ({ name, stars, kind })), ...o })
}

/** Parse a set of named files and merge their records into a single builder input. */
function corpusFrom(files: { name: string; lines: string[] }[]): VeteranCorpusInput {
    const outcomes = []
    const sparks = []
    for (const f of files) {
        const parsed = parseCorpus(f.lines.join("\n") + "\n", f.name)
        outcomes.push(...parsed.outcomes)
        sparks.push(...parsed.sparks)
    }
    return { outcomes, sparks }
}

/** A minimal confirmed career: one career line followed by a kept set covering blue/red/green. */
function confirmedFile(name: string, career: Record<string, unknown>, keptRows: Row[]): { name: string; lines: string[] } {
    return { name, lines: [careerLine(career), sparkLine("kept", keptRows)] }
}

const BRG: Row[] = [["Speed", 3, "stat"], ["Long", 2, "aptitude"], ["Corner Recovery", 1, "unique"]]

// ---- tests ---------------------------------------------------------------

describe("buildVeteranLibrary - shape and basics", () => {
    it("emits a versioned library and confirms a kept-bearing career as one Veteran", () => {
        const lib = buildVeteranLibrary(corpusFrom([confirmedFile("a.jsonl", { ts: 1000 }, BRG)]))
        expect(lib.schema).toBe(PARENTLAB_SCHEMA)
        expect(lib.schemaVersion).toBe(PARENTLAB_SCHEMA_VERSION)
        expect(lib.veterans).toHaveLength(1)
        const v = lib.veterans[0]
        expect(v.trainee).toBe("Test Uma")
        expect(v.scenario).toBe("URA Finale")
        expect(v.completedAt).toBe(1000)
        expect(v.veteranId).toMatch(/^[0-9a-f]{32}$/)
        expect(v.result.finalStats).toEqual({ spd: 1000, sta: 900, pwr: 800, grt: 500, wit: 600 })
        expect(v.result.rank).toBeNull()
        expect(v.result.score).toBeNull()
    })

    it("maps spark kinds to color categories and reports coverage", () => {
        const lib = buildVeteranLibrary(corpusFrom([confirmedFile("a.jsonl", { ts: 1 }, BRG)]))
        const cats = lib.veterans[0].sparks.map((s) => s.category).sort()
        expect(cats).toEqual(["blue", "green", "red"])
        expect(lib.diagnostics.categoryCoverage.blue).toBe(1)
        expect(lib.diagnostics.categoryCoverage.red).toBe(1)
        expect(lib.diagnostics.categoryCoverage.green).toBe(1)
        expect(lib.diagnostics.categoryCoverage.blueRedGreen).toBe(1)
    })
})

describe("2. idempotent rebuild", () => {
    it("produces deep-equal output for the same corpus built twice", () => {
        const files = [confirmedFile("a.jsonl", { ts: 1 }, BRG), confirmedFile("b.jsonl", { ts: 2, trainee: "Symboli_Rudolf" }, BRG)]
        const first = buildVeteranLibrary(corpusFrom(files))
        const second = buildVeteranLibrary(corpusFrom(files))
        expect(second).toEqual(first)
    })
})

describe("3. input-order independence", () => {
    it("gives the same library when the input record arrays are reversed", () => {
        const files = [
            confirmedFile("a.jsonl", { ts: 1 }, BRG),
            confirmedFile("b.jsonl", { ts: 2, trainee: "Symboli_Rudolf" }, [["Stamina", 2, "stat"], ["Dirt", 1, "aptitude"], ["Guts x9", 1, "unique"]]),
        ]
        const forward = buildVeteranLibrary(corpusFrom(files))
        const input = corpusFrom(files)
        const reversed = buildVeteranLibrary({ outcomes: [...input.outcomes].reverse(), sparks: [...input.sparks].reverse() })
        expect(reversed).toEqual(forward)
    })

    it("gives the same library when whole files are supplied in the opposite order", () => {
        const A = confirmedFile("a.jsonl", { ts: 1 }, BRG)
        const B = confirmedFile("b.jsonl", { ts: 2, trainee: "Symboli_Rudolf" }, BRG)
        expect(buildVeteranLibrary(corpusFrom([B, A]))).toEqual(buildVeteranLibrary(corpusFrom([A, B])))
    })
})

describe("4. overlapping corpus dedupe", () => {
    it("collapses the same career appearing in two pulls into one Veteran, merging provenance", () => {
        const same = { ts: 555, trainee: "Symboli_Rudolf", spd: 1200 }
        const lib = buildVeteranLibrary(corpusFrom([confirmedFile("pull-1.jsonl", same, BRG), confirmedFile("pull-2.jsonl", same, BRG)]))
        expect(lib.veterans).toHaveLength(1)
        expect(lib.veterans[0].provenance.files).toEqual(["pull-1.jsonl", "pull-2.jsonl"])
        expect(lib.veterans[0].provenance.observations).toBe(2)
        expect(lib.diagnostics.keptCareerInstances).toBe(2)
        expect(lib.diagnostics.confirmedVeterans).toBe(1)
        expect(lib.diagnostics.duplicatesCollapsed).toBe(1)
    })
})

describe("5. distinct-career preservation", () => {
    it("keeps two careers with the same trainee/quality/score but different real evidence distinct", () => {
        // Same trainee, same fans/stats/quality; different ts AND different kept spark set -> two Veterans.
        const a = confirmedFile("a.jsonl", { ts: 1000, quality: "WIN" }, [["Speed", 3, "stat"], ["Long", 2, "aptitude"], ["Corner Recovery", 1, "unique"]])
        const b = confirmedFile("b.jsonl", { ts: 2000, quality: "WIN" }, [["Speed", 2, "stat"], ["Long", 1, "aptitude"], ["Corner Recovery", 3, "unique"]])
        const lib = buildVeteranLibrary(corpusFrom([a, b]))
        expect(lib.veterans).toHaveLength(2)
        expect(new Set(lib.veterans.map((v) => v.veteranId)).size).toBe(2)
    })
})

describe("6. confirmed kept-spark rule", () => {
    it("does not finalize a career that has only an original set (no kept)", () => {
        const file = { name: "a.jsonl", lines: [careerLine({ ts: 1 }), sparkLine("original", BRG)] }
        const lib = buildVeteranLibrary(corpusFrom([file]))
        expect(lib.veterans).toHaveLength(0)
        expect(lib.diagnostics.incompleteCareersSkipped).toBe(1)
        expect(lib.diagnostics.confirmedVeterans).toBe(0)
    })
})

describe("7. kept beats original", () => {
    it("uses the kept set, not original, when both are present", () => {
        const file = {
            name: "a.jsonl",
            lines: [careerLine({ ts: 1 }), sparkLine("original", [["Guts", 1, "stat"]]), sparkLine("kept", [["Speed", 3, "stat"], ["Long", 2, "aptitude"], ["Corner Recovery", 1, "unique"]])],
        }
        const lib = buildVeteranLibrary(corpusFrom([file]))
        expect(lib.veterans).toHaveLength(1)
        const names = lib.veterans[0].sparks.map((s) => s.displayText).sort()
        expect(names).toEqual(["Corner Recovery", "Long", "Speed"])
        expect(names).not.toContain("Guts")
    })

    it("uses the LAST kept set when a career has more than one", () => {
        const file = {
            name: "a.jsonl",
            lines: [careerLine({ ts: 1 }), sparkLine("kept", [["Guts", 1, "stat"]]), sparkLine("kept", [["Speed", 3, "stat"], ["Long", 2, "aptitude"], ["Corner Recovery", 1, "unique"]])],
        }
        const v = buildVeteranLibrary(corpusFrom([file])).veterans[0]
        expect(v.sparks.map((s) => s.displayText).sort()).toEqual(["Corner Recovery", "Long", "Speed"])
    })
})

describe("8. lineage semantics", () => {
    it("reports lineage as uncaptured with null parents/grandparents, never known-empty", () => {
        const v = buildVeteranLibrary(corpusFrom([confirmedFile("a.jsonl", { ts: 1 }, BRG)])).veterans[0]
        expect(v.lineage.captureStatus).toBe("uncaptured")
        expect(v.lineage.parents).toBeNull()
        expect(v.lineage.grandparents).toBeNull()
        expect(v.completeness.lineageCaptured).toBe(false)
    })
})

describe("9. partial-data handling", () => {
    it("surfaces malformed spark rows in diagnostics without crashing, and still finalizes the career", () => {
        // One malformed row (bad kind) inside an otherwise valid kept set: dropped, counted, set survives.
        const rows = [["Speed", 3, "stat"], ["junk", 2, "not_a_kind"], ["Long", 2, "aptitude"], ["Corner Recovery", 1, "unique"]] as Row[]
        const file = { name: "a.jsonl", lines: [careerLine({ ts: 1 }), sparkLine("kept", rows)] }
        const lib = buildVeteranLibrary(corpusFrom([file]))
        expect(lib.diagnostics.malformedSparkRows).toBe(1)
        expect(lib.veterans).toHaveLength(1)
        expect(lib.veterans[0].sparks).toHaveLength(3)
    })

    it("keeps an unreadable kept spark name as displayText and still builds the Veteran", () => {
        const rows = [["", 3, "stat"], ["Long", 2, "aptitude"], ["Corner Recovery", 1, "unique"]] as Row[]
        const file = { name: "a.jsonl", lines: [careerLine({ ts: 1 }), sparkLine("kept", rows)] }
        const v = buildVeteranLibrary(corpusFrom([file])).veterans[0]
        expect(v.sparks.some((s) => s.displayText === "" && s.category === "blue")).toBe(true)
    })

    it("skips a garbled JSONL line without crashing the build", () => {
        const file = { name: "a.jsonl", lines: ["{not valid json", careerLine({ ts: 1 }), sparkLine("kept", BRG)] }
        const lib = buildVeteranLibrary(corpusFrom([file]))
        expect(lib.veterans).toHaveLength(1)
    })
})

describe("10. stable surrogate id", () => {
    it("holds the veteranId stable across rebuild, order change, and overlapping sources", () => {
        const A = confirmedFile("a.jsonl", { ts: 7, trainee: "Symboli_Rudolf", spd: 1234 }, BRG)
        const B = confirmedFile("b.jsonl", { ts: 7, trainee: "Symboli_Rudolf", spd: 1234 }, BRG) // same career, second pull
        const single = buildVeteranLibrary(corpusFrom([A])).veterans[0].veteranId
        const rebuilt = buildVeteranLibrary(corpusFrom([A])).veterans[0].veteranId
        const overlapped = buildVeteranLibrary(corpusFrom([A, B])).veterans[0].veteranId
        const reordered = buildVeteranLibrary(corpusFrom([B, A])).veterans[0].veteranId
        expect(rebuilt).toBe(single)
        expect(overlapped).toBe(single)
        expect(reordered).toBe(single)
    })
})

describe("11. no canonical-ID fabrication", () => {
    it("leaves every spark's canonical factor id unresolved", () => {
        const v = buildVeteranLibrary(corpusFrom([confirmedFile("a.jsonl", { ts: 1 }, BRG)])).veterans[0]
        for (const s of v.sparks) {
            expect(s.canonicalFactorId).toBeNull()
            expect(s.canonicalResolved).toBe(false)
        }
        expect(v.completeness.canonicalSparkIdsResolved).toBe(false)
    })
})

describe("finalize-only re-reports", () => {
    it("confirms a turn=null re-report but flags it and lowers its completeness", () => {
        const file = { name: "a.jsonl", lines: [careerLine({ ts: 1, turn: null, outcome: "COMPLETED" }), sparkLine("kept", BRG)] }
        const lib = buildVeteranLibrary(corpusFrom([file]))
        expect(lib.veterans).toHaveLength(1)
        expect(lib.veterans[0].provenance.finalizeOnly).toBe(true)
        expect(lib.veterans[0].completeness.resultCaptured).toBe(false)
        expect(lib.diagnostics.finalizeOnlyVeterans).toBe(1)
    })
})

describe("deterministic sort", () => {
    it("orders veterans by trainee then scenario then veteranId regardless of input order", () => {
        const files = [
            confirmedFile("a.jsonl", { ts: 3, trainee: "Zzz_Uma" }, BRG),
            confirmedFile("b.jsonl", { ts: 1, trainee: "Aaa_Uma" }, BRG),
            confirmedFile("c.jsonl", { ts: 2, trainee: "Mmm_Uma" }, BRG),
        ]
        const names = buildVeteranLibrary(corpusFrom(files)).veterans.map((v) => v.trainee)
        expect(names).toEqual(["Aaa Uma", "Mmm Uma", "Zzz Uma"])
    })
})
