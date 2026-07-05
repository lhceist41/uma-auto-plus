import { aggregate, classifyBucket, dedupe, harvestLogText, parseJsonl, parseLedgerLine, percentile, renderMarkdown } from "../outcomeAnalysis"
import type { OutcomeRecord } from "../outcomeAnalysis"

const MODERN_LINE =
    "00:45:49.128 [INFO] [CAREER_END] result=COMPLETE outcome=COMPLETED trainee=Matikanetannhauser scenario=URA_Finale turn=75 fans=277728 spd=880 sta=775 pwr=673 grt=779 wit=404 skillPts=3"

const LEGACY_LINE =
    "05:08:39.225 [INFO] [CAREER_END] result=COMPLETE trainee=Gold_Ship scenario=Trackblazer turn=75 fans=540278 spd=839 sta=761 pwr=600 grt=519 wit=575 skillPts=1"

const STOPPED_LINE =
    '00:01:00.000 [INFO] [CAREER_END] result=MANUALLY_STOPPED outcome=INCOMPLETE trainee=Daiwa_Scarlet scenario=URA_Finale turn=1 fans=1 spd=1 sta=153 pwr=84 grt=175 wit=131 skillPts=120 stopReason="Stopped on trainee mismatch - restart from the home screen."'

function record(overrides: Partial<OutcomeRecord>): OutcomeRecord {
    return {
        result: "COMPLETE",
        outcome: "COMPLETED",
        trainee: "Test Uma",
        scenario: "URA Finale",
        turn: 75,
        fans: 100000,
        spd: 900,
        sta: 600,
        pwr: 700,
        grt: 500,
        wit: 450,
        skillPts: 10,
        source: "log",
        ...overrides,
    }
}

describe("parseLedgerLine", () => {
    it("parses a modern line with outcome", () => {
        const r = parseLedgerLine(MODERN_LINE)
        expect(r).not.toBeNull()
        expect(r!.trainee).toBe("Matikanetannhauser")
        expect(r!.scenario).toBe("URA Finale")
        expect(r!.outcome).toBe("COMPLETED")
        expect(r!.turn).toBe(75)
        expect(r!.fans).toBe(277728)
        expect(r!.sta).toBe(775)
        expect(r!.skillPts).toBe(3)
        expect(r!.source).toBe("log")
    })

    it("infers outcome on a legacy line without the field", () => {
        const r = parseLedgerLine(LEGACY_LINE)
        expect(r!.outcome).toBe("COMPLETED")
        expect(r!.trainee).toBe("Gold Ship")
        expect(r!.scenario).toBe("Trackblazer")
    })

    it("keeps spaces inside quoted stopReason values", () => {
        const r = parseLedgerLine(STOPPED_LINE)
        expect(r!.outcome).toBe("INCOMPLETE")
        expect(r!.stopReason).toBe("Stopped on trainee mismatch - restart from the home screen.")
    })

    it("returns null for a non-ledger line", () => {
        expect(parseLedgerLine("00:00:01.000 [INFO] [RACE] Starting Racing process.")).toBeNull()
    })

    it("rejects a line truncated mid-write", () => {
        expect(parseLedgerLine("00:00:01.000 [INFO] [CAREER_END] result=COMPLETE outcome=COMPLETED trainee=Test scenario=URA_Finale turn=75 fans=54")).toBeNull()
    })

    it("repairs the EI OCR misread of El", () => {
        const r = parseLedgerLine(LEGACY_LINE.replace("Gold_Ship", "EI_Condor_Pasa"))
        expect(r!.trainee).toBe("El Condor Pasa")
    })
})

describe("harvestLogText", () => {
    it("finds every ledger line in a log", () => {
        const text = ["noise", MODERN_LINE, "more noise", LEGACY_LINE].join("\n")
        const records = harvestLogText(text, "session.txt")
        expect(records).toHaveLength(2)
        expect(records[0].file).toBe("session.txt")
    })
})

describe("parseJsonl", () => {
    it("round-trips a corpus record and skips malformed lines", () => {
        const good = JSON.stringify({
            ts: 1783300000000,
            app: "1.3.5",
            fp: "a1b2c3d4e5",
            result: "COMPLETE",
            outcome: "COMPLETED",
            trainee: "Test Uma",
            scenario: "URA Finale",
            turn: 75,
            fans: 250000,
            spd: 950,
            sta: 700,
            pwr: 720,
            grt: 600,
            wit: 480,
            skillPts: 12,
            cfg: { statPrioritization: "Stamina,Speed,Power,Wit,Guts" },
        })
        const records = parseJsonl(`${good}\n{broken json\n\n`)
        expect(records).toHaveLength(1)
        expect(records[0].fp).toBe("a1b2c3d4e5")
        expect(records[0].cfg?.statPrioritization).toBe("Stamina,Speed,Power,Wit,Guts")
        expect(records[0].source).toBe("jsonl")
    })
})

describe("classifyBucket", () => {
    it("buckets by outcome and turn", () => {
        expect(classifyBucket(record({ turn: 75 }))).toBe("full")
        expect(classifyBucket(record({ turn: 73 }))).toBe("full")
        expect(classifyBucket(record({ turn: 72 }))).toBe("late")
        expect(classifyBucket(record({ turn: 60 }))).toBe("late")
        expect(classifyBucket(record({ turn: 55 }))).toBe("mid")
        expect(classifyBucket(record({ turn: 30 }))).toBe("mid")
        expect(classifyBucket(record({ turn: 24 }))).toBe("early")
        expect(classifyBucket(record({ turn: 75, outcome: "INCOMPLETE" }))).toBe("incomplete")
    })

    it("never counts a source-confirmed FORCE_END as a full arc", () => {
        expect(classifyBucket(record({ turn: 74, outcome: "FORCE_END" }))).toBe("late")
        expect(classifyBucket(record({ turn: 55, outcome: "FORCE_END" }))).toBe("mid")
        expect(classifyBucket(record({ turn: 24, outcome: "FORCE_END" }))).toBe("early")
    })
})

describe("dedupe", () => {
    it("drops the log copy of a career that also exists as a corpus record", () => {
        const jsonl = record({ source: "jsonl", app: "1.3.6", fp: "aaaaaaaaaa" })
        const logCopy = record({ source: "log" })
        const olderLogRun = record({ source: "log", fans: 123456 })
        const unique = dedupe([jsonl, logCopy, olderLogRun])
        expect(unique).toHaveLength(2)
        expect(unique).toContain(jsonl)
        expect(unique).toContain(olderLogRun)
    })
})

describe("percentile", () => {
    it("nearest-rank behavior and empty safety", () => {
        expect(percentile([], 50)).toBe(0)
        expect(percentile([10], 50)).toBe(10)
        expect(percentile([1, 2, 3, 4], 50)).toBe(2)
        expect(percentile([1, 2, 3, 4], 75)).toBe(3)
    })
})

describe("aggregate", () => {
    it("groups by trainee+scenario+arm and computes distributions", () => {
        const records = [
            record({ fans: 100000, turn: 75 }),
            record({ fans: 200000, turn: 75 }),
            record({ fans: 300000, turn: 55 }),
            record({ outcome: "INCOMPLETE", result: "MANUALLY_STOPPED", fans: 5, turn: 10 }),
        ]
        const [summary] = aggregate(records)
        expect(summary.n).toBe(4)
        expect(summary.buckets.full).toBe(2)
        expect(summary.buckets.mid).toBe(1)
        expect(summary.buckets.incomplete).toBe(1)
        // Incomplete runs are excluded from distributions: median fans over the 3 finished runs.
        expect(summary.fans.p50).toBe(200000)
        expect(summary.fullRate).toBeCloseTo(2 / 3)
        expect(summary.forceEndTurns).toEqual([55])
        expect(summary.lowN).toBe(true)
    })

    it("splits arms on fingerprint", () => {
        const records = [record({ app: "1.3.5", fp: "aaaaaaaaaa" }), record({ app: "1.3.5", fp: "bbbbbbbbbb" }), record({})]
        const summaries = aggregate(records)
        expect(summaries).toHaveLength(3)
        expect(summaries.map((s) => s.arm).sort()).toEqual(["1.3.5@aaaaaaaaaa", "1.3.5@bbbbbbbbbb", "legacy"])
    })
})

describe("renderMarkdown", () => {
    it("renders a table row per arm with the low-N marker", () => {
        const table = renderMarkdown(aggregate([record({})]))
        expect(table).toContain("| Test Uma | URA Finale | legacy | 1* |")
        expect(table).toContain("anecdote, not signal")
    })
})
