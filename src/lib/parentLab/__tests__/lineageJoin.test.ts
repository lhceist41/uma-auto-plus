import { parseCorpus } from "../../outcomeAnalysis.ts"
import { buildVeteranLibrary } from "../buildVeteranLibrary.ts"
import { parseLineageRecords } from "../lineage.ts"
import type { VeteranCorpusInput } from "../types.ts"

// The lineage join is exercised through the same parse paths the device writer and CLI use: careers
// authored as JSONL and parsed by parseCorpus, lineage events authored as JSONL and parsed by
// parseLineageRecords, then merged into one builder input.

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

function sparkLine(phase: string, rows: Row[]): string {
    return JSON.stringify({ type: "sparks", phase, rows: rows.map(([name, stars, kind]) => ({ name, stars, kind })) })
}

const BRG: Row[] = [
    ["Speed", 3, "stat"],
    ["Long", 2, "aptitude"],
    ["Corner Recovery", 1, "unique"],
]

function ancestor(role: string, slotIndex: number, opts: { lead?: boolean; clipped?: boolean } = {}): Record<string, unknown> {
    return {
        role,
        slotIndex,
        portraitObserved: true,
        ownership: "owned",
        matchStatus: "probable_owned_match",
        hasLeadTriple: opts.lead ?? true,
        completeness: opts.lead === false || opts.clipped ? 0.6 : 1.0,
        factorFingerprint: `${role}-fp`,
        factors: [
            { kind: "stat", displayText: "Power", stars: 3 },
            { kind: "aptitude", displayText: "Mile", stars: 2 },
            { kind: "unique", displayText: "U", stars: 1, clipped: opts.clipped ?? false },
        ],
    }
}

const SIX_ROLES = [
    "legacy1_parent",
    "legacy1_grandparent_a",
    "legacy1_grandparent_b",
    "legacy2_parent",
    "legacy2_grandparent_a",
    "legacy2_grandparent_b",
]

function lineageLine(o: { launchTransactionId?: string; captureStatus: string; ancestors: Record<string, unknown>[] }): string {
    return JSON.stringify({
        type: "lineage_selected",
        schemaVersion: 1,
        ts: 5000,
        scenario: "URA_Finale",
        trainee: "Test_Uma",
        overallAffinity: "double",
        ...o,
    })
}

function build(careerLines: string[], lineageLines: string[]): VeteranCorpusInput {
    const parsed = parseCorpus(careerLines.join("\n") + "\n", "careers.jsonl")
    const lineageEvents = parseLineageRecords(lineageLines.join("\n") + "\n", "lineage.jsonl")
    return { outcomes: parsed.outcomes, sparks: parsed.sparks, lineageEvents }
}

const capturedEvent = (txId: string) =>
    lineageLine({ launchTransactionId: txId, captureStatus: "captured", ancestors: SIX_ROLES.map((r, i) => ancestor(r, i)) })

describe("ParentLab lineage join (PL-4)", () => {
    it("a historical Veteran with no launch id stays uncaptured even when lineage events exist", () => {
        const input = build([careerLine({ ts: 1000 }), sparkLine("kept", BRG)], [capturedEvent("tx-orphan")])
        const v = buildVeteranLibrary(input).veterans[0]
        expect(v.lineage.captureStatus).toBe("uncaptured")
        expect(v.lineage.ancestors).toBeNull()
        expect(v.completeness.lineageCaptured).toBe(false)
    })

    it("a lineage event joins to the career that carries its launch id", () => {
        const input = build([careerLine({ ts: 1000, launchTransactionId: "tx-1" }), sparkLine("kept", BRG)], [capturedEvent("tx-1")])
        const v = buildVeteranLibrary(input).veterans[0]
        expect(v.lineage.captureStatus).toBe("captured")
        expect(v.lineage.launchTransactionId).toBe("tx-1")
        expect(v.lineage.ancestors).toHaveLength(6)
        expect(v.lineage.ancestors?.map((a) => a.role)).toEqual(SIX_ROLES)
        expect(v.lineage.overallAffinity).toBe("double")
        expect(v.completeness.lineageCaptured).toBe(true)
        expect(v.completeness.score).toBeCloseTo(3 / 6)
    })

    it("a lineage event with the wrong launch id does not join", () => {
        const input = build([careerLine({ ts: 1000, launchTransactionId: "tx-1" }), sparkLine("kept", BRG)], [capturedEvent("tx-different")])
        const v = buildVeteranLibrary(input).veterans[0]
        expect(v.lineage.captureStatus).toBe("uncaptured")
        expect(v.completeness.lineageCaptured).toBe(false)
    })

    it("a partial capture stays partial", () => {
        const partial = lineageLine({
            launchTransactionId: "tx-1",
            captureStatus: "partial",
            ancestors: [ancestor("legacy1_parent", 0), ancestor("legacy1_grandparent_a", 1, { clipped: true })],
        })
        const input = build([careerLine({ ts: 1000, launchTransactionId: "tx-1" }), sparkLine("kept", BRG)], [partial])
        const v = buildVeteranLibrary(input).veterans[0]
        expect(v.lineage.captureStatus).toBe("partial")
        expect(v.lineage.ancestors).toHaveLength(2)
        expect(v.completeness.lineageCaptured).toBe(true)
    })

    it("a failed capture is not a capture: the Veteran stays uncaptured", () => {
        const failed = lineageLine({ launchTransactionId: "tx-1", captureStatus: "failed", ancestors: [] })
        const input = build([careerLine({ ts: 1000, launchTransactionId: "tx-1" }), sparkLine("kept", BRG)], [failed])
        const v = buildVeteranLibrary(input).veterans[0]
        expect(v.lineage.captureStatus).toBe("uncaptured")
    })

    it("the join is deterministic and idempotent under shuffled and duplicated inputs", () => {
        const careers = [careerLine({ ts: 1000, launchTransactionId: "tx-1" }), sparkLine("kept", BRG)]
        // Two lineage events for the same launch (a re-emit): the stronger captured one must win
        // deterministically regardless of order.
        const weak = lineageLine({ launchTransactionId: "tx-1", captureStatus: "partial", ancestors: [ancestor("legacy1_parent", 0)] })
        const strong = capturedEvent("tx-1")
        const a = buildVeteranLibrary(build(careers, [weak, strong])).veterans[0]
        const b = buildVeteranLibrary(build(careers, [strong, weak])).veterans[0]
        expect(a).toEqual(b)
        expect(a.lineage.captureStatus).toBe("captured")
        expect(a.lineage.ancestors).toHaveLength(6)
    })
})
