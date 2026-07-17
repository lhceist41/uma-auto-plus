import {
    aggregate,
    analyzeSkillSpend,
    analyzeSparkFarm,
    canonicalizeStatName,
    classifyBucket,
    dedupe,
    harvestLogText,
    isBotFault,
    joinSparks,
    parseCorpus,
    parseJsonl,
    parseLedgerLine,
    percentile,
    recognizeAptitude,
    renderMarkdown,
    renderSkillSpendMarkdown,
    renderSparkFarmMarkdown,
} from "../outcomeAnalysis"
import type { OutcomeRecord } from "../outcomeAnalysis"

const MODERN_LINE =
    "00:45:49.128 [INFO] [CAREER_END] result=COMPLETE outcome=COMPLETED trainee=Matikanetannhauser scenario=URA_Finale turn=75 fans=277728 spd=880 sta=775 pwr=673 grt=779 wit=404 skillPts=3"

const FINALE_WIN_LINE =
    "01:20:04.500 [INFO] [CAREER_END] result=COMPLETE outcome=COMPLETED trainee=Symboli_Rudolf scenario=URA_Finale turn=75 fans=361000 spd=1100 sta=751 pwr=820 grt=600 wit=500 skillPts=8 finaleRaces=3 finaleWins=3 quality=WIN"

const FINALE_LOSS_LINE =
    "01:21:00.000 [INFO] [CAREER_END] result=COMPLETE outcome=COMPLETED trainee=Winning_Ticket scenario=URA_Finale turn=75 fans=233119 spd=952 sta=527 pwr=796 grt=569 wit=472 skillPts=10 finaleRaces=3 finaleWins=1 quality=FINALE_LOST"

const LEGACY_LINE = "05:08:39.225 [INFO] [CAREER_END] result=COMPLETE trainee=Gold_Ship scenario=Trackblazer turn=75 fans=540278 spd=839 sta=761 pwr=600 grt=519 wit=575 skillPts=1"

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

    it("parses the finale win/lose fields on a modern line", () => {
        const win = parseLedgerLine(FINALE_WIN_LINE)
        expect(win!.finaleRaces).toBe(3)
        expect(win!.finaleWins).toBe(3)
        expect(win!.quality).toBe("WIN")
        const loss = parseLedgerLine(FINALE_LOSS_LINE)
        expect(loss!.finaleWins).toBe(1)
        expect(loss!.quality).toBe("FINALE_LOST")
    })

    it("leaves finale fields undefined on a line that predates them", () => {
        const r = parseLedgerLine(MODERN_LINE)
        expect(r!.finaleRaces).toBeUndefined()
        expect(r!.quality).toBeUndefined()
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

    it("skips auxiliary typed records such as sparks", () => {
        const career = JSON.stringify({ result: "COMPLETE", outcome: "COMPLETED", trainee: "Test Uma", scenario: "URA Finale", turn: 75, fans: 1000 })
        const sparks = JSON.stringify({
            type: "sparks",
            ts: 1783300000001,
            trainee: "Test_Uma",
            phase: "original",
            rows: [
                { name: "Speed", stars: 2, kind: "stat" },
                { name: "Long", stars: 2, kind: "aptitude" },
            ],
        })
        const records = parseJsonl(`${career}\n${sparks}\n`)
        expect(records).toHaveLength(1)
        expect(records[0].trainee).toBe("Test Uma")
    })

    it("parses finale fields from a corpus record", () => {
        const line = JSON.stringify({
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
            finaleRaces: 3,
            finaleWins: 3,
            quality: "WIN",
        })
        const [r] = parseJsonl(line)
        expect(r.finaleWins).toBe(3)
        expect(r.quality).toBe("WIN")
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

    it("uses the finale win/lose signal over the turn proxy", () => {
        // A finale loss at turn 75 is NOT a full arc — the disambiguation the signal adds.
        expect(classifyBucket(record({ turn: 75, quality: "FINALE_LOST" }))).toBe("late")
        // A confirmed sweep is a full arc.
        expect(classifyBucket(record({ turn: 75, quality: "WIN" }))).toBe("full")
        // Records without the signal still fall back to the turn bands.
        expect(classifyBucket(record({ turn: 75 }))).toBe("full")
    })
})

describe("isBotFault", () => {
    it("flags only UNHANDLED_EXCEPTION crash-stops", () => {
        expect(isBotFault(record({ result: "UNHANDLED_EXCEPTION", outcome: "INCOMPLETE", turn: 32 }))).toBe(true)
        expect(isBotFault(record({ result: "COMPLETE" }))).toBe(false)
        expect(isBotFault(record({ result: "MANUALLY_STOPPED", outcome: "INCOMPLETE" }))).toBe(false)
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

    it("counts finale wins/reaches and moves a turn-75 finale loss out of full", () => {
        const records = [
            record({ quality: "WIN", finaleWins: 3, finaleRaces: 3 }),
            record({ quality: "WIN", finaleWins: 3, finaleRaces: 3 }),
            record({ quality: "FINALE_LOST", finaleWins: 1, finaleRaces: 3, turn: 75 }),
            record({ quality: "COMPLETED", turn: 40 }),
        ]
        const [summary] = aggregate(records)
        expect(summary.finaleReached).toBe(3)
        expect(summary.finaleWon).toBe(2)
        // The turn-75 finale loss buckets late, not full, so fullRate reads as a true win-rate.
        expect(summary.buckets.full).toBe(2)
        expect(summary.buckets.late).toBe(1)
        expect(summary.buckets.mid).toBe(1)
    })

    it("excludes UNHANDLED_EXCEPTION crash-stops from an arm's outcomes", () => {
        // The real career plus a mid-career crash-stop (the daily-reset lobby bounce) for the same
        // trainee. The crash must not count as a career, nor inflate the incomplete bucket.
        const records = [record({ turn: 75 }), record({ result: "UNHANDLED_EXCEPTION", outcome: "INCOMPLETE", turn: 32, fans: 31492 })]
        const [summary] = aggregate(records)
        expect(summary.n).toBe(1)
        expect(summary.buckets.full).toBe(1)
        expect(summary.buckets.incomplete).toBe(0)
    })

    it("produces no summary for an arm that is only bot faults", () => {
        expect(aggregate([record({ result: "UNHANDLED_EXCEPTION", outcome: "INCOMPLETE", turn: 32 })])).toEqual([])
    })
})

describe("renderMarkdown", () => {
    it("renders a table row per arm with the low-N marker", () => {
        const table = renderMarkdown(aggregate([record({})]))
        expect(table).toContain("| Test Uma | URA Finale | legacy | 1* |")
        expect(table).toContain("anecdote, not signal")
    })

    it("includes the Finale W/R column with the per-arm win/reach count", () => {
        const table = renderMarkdown(aggregate([record({ quality: "WIN", finaleWins: 3, finaleRaces: 3 })]))
        expect(table).toContain("Finale W/R")
        expect(table).toContain("| 1/1 |")
    })
})

// ---- Spark Farm Analyzer -------------------------------------------------

function careerJson(o: Partial<{ trainee: string; scenario: string; turn: number; outcome: string; result: string; spd: number; sta: number; pwr: number; grt: number; wit: number; app: string; fp: string; fans: number }> = {}): string {
    return JSON.stringify({
        result: o.result ?? "COMPLETE",
        outcome: o.outcome ?? "COMPLETED",
        trainee: o.trainee ?? "Test Uma",
        scenario: o.scenario ?? "URA Finale",
        turn: o.turn ?? 75,
        fans: o.fans ?? 100000,
        spd: o.spd ?? 900,
        sta: o.sta ?? 700,
        pwr: o.pwr ?? 700,
        grt: o.grt ?? 700,
        wit: o.wit ?? 700,
        skillPts: 5,
        app: o.app,
        fp: o.fp,
    })
}

function sparkJson(phase: string, trainee: string | undefined, rows: [string, number, string][]): string {
    return JSON.stringify({ type: "sparks", ts: 1, trainee, phase, rows: rows.map(([name, stars, kind]) => ({ name, stars, kind })) })
}

/** Parse a synthetic corpus and run the analyzer over it. */
function analyze(...lines: string[]) {
    const { outcomes, sparks } = parseCorpus(lines.join("\n") + "\n", "corpus.jsonl")
    return { outcomes, sparks, report: analyzeSparkFarm(outcomes, sparks) }
}

function skillSpendJson(
    o: Partial<{
        outcome: string
        trigger: string
        plan: string
        trainee: string
        scenario: string
        fp: string
        turn: number
        spBefore: number
        spAfter: number
        unspent: number
        proposed: { name: string; price: number }[]
        confirmed: string[]
        skipped: { name: string; reason: string }[]
        confirmedIncomplete: boolean
        policy: string
        threshold: number
        tier: string
        reason: string
        objective: string
        criticalRace: string
        criticalRaceSource: string
        turnsUntilRace: number
        plannedSkill: string
        plannedSkillObservedPrice: number
    }> = {},
): string {
    const record: Record<string, unknown> = { type: "skill_spend", ts: 1, policy: o.policy ?? "trigger-v1", outcome: o.outcome ?? "committed" }
    for (const key of [
        "trigger",
        "plan",
        "trainee",
        "scenario",
        "fp",
        "turn",
        "spBefore",
        "spAfter",
        "unspent",
        "proposed",
        "confirmed",
        "skipped",
        "confirmedIncomplete",
        "threshold",
        "tier",
        "reason",
        "objective",
        "criticalRace",
        "criticalRaceSource",
        "turnsUntilRace",
        "plannedSkill",
        "plannedSkillObservedPrice",
    ] as const) {
        if (o[key] !== undefined) record[key] = o[key]
    }
    return JSON.stringify(record)
}

describe("parseCorpus", () => {
    it("keeps parseJsonl career-outcome behavior unchanged (auxiliary sparks excluded)", () => {
        const career = careerJson({ trainee: "Test Uma", fans: 1000 })
        const sparks = sparkJson("original", "Test_Uma", [["Speed", 2, "stat"]])
        const outcomes = parseJsonl(`${career}\n${sparks}\n`)
        expect(outcomes).toHaveLength(1)
        expect(outcomes[0].trainee).toBe("Test Uma")
        expect(outcomes[0].source).toBe("jsonl")
    })

    it("parses a valid type=sparks record into a SparkRecord with normalized trainee", () => {
        const { outcomes, sparks } = parseCorpus(sparkJson("original", "El_Condor_Pasa", [["Speed", 2, "stat"], ["Long", 1, "aptitude"]]), "c.jsonl")
        expect(outcomes).toHaveLength(0)
        expect(sparks).toHaveLength(1)
        expect(sparks[0].phase).toBe("original")
        expect(sparks[0].trainee).toBe("El Condor Pasa")
        expect(sparks[0].rows).toHaveLength(2)
        expect(sparks[0].file).toBe("c.jsonl")
        expect(sparks[0].lineNumber).toBe(0)
    })

    it("drops malformed spark rows without aborting the record or later lines", () => {
        const spark = JSON.stringify({
            type: "sparks",
            phase: "kept",
            rows: [{ name: "Speed", stars: 3, kind: "stat" }, { name: "X", stars: "NaN", kind: "stat" }, { name: "Y", stars: 2, kind: "bogus" }, "nope"],
        })
        const career = careerJson({ trainee: "A" })
        const { outcomes, sparks } = parseCorpus(`${spark}\n${career}\n`)
        expect(sparks).toHaveLength(1)
        expect(sparks[0].rows).toHaveLength(1)
        expect(sparks[0].rows[0].name).toBe("Speed")
        expect(sparks[0].droppedRows).toBe(3)
        expect(outcomes).toHaveLength(1) // the later career line still parses
    })

    it("skips a malformed typed record whose rows are not an array", () => {
        const { sparks } = parseCorpus(JSON.stringify({ type: "sparks", phase: "kept", rows: "notarray" }))
        expect(sparks).toHaveLength(0)
    })

    it("retains file and line order across interleaved records", () => {
        const text = [careerJson({ trainee: "A" }), sparkJson("original", "A", [["Speed", 1, "stat"]]), careerJson({ trainee: "B" }), sparkJson("kept", "B", [["Speed", 2, "stat"]])].join("\n")
        const { outcomes, sparks } = parseCorpus(text, "f.jsonl")
        expect(outcomes.map((o) => o.lineNumber)).toEqual([0, 2])
        expect(sparks.map((s) => s.lineNumber)).toEqual([1, 3])
        expect(outcomes[0].file).toBe("f.jsonl")
    })
})

describe("joinSparks", () => {
    it("joins an original/rerolled/kept sequence to the nearest preceding career in the same file", () => {
        const { outcomes, sparks } = parseCorpus(
            [careerJson({ trainee: "A" }), sparkJson("original", "A", [["Speed", 1, "stat"]]), sparkJson("rerolled", "A", [["Speed", 2, "stat"]]), sparkJson("kept", "A", [["Speed", 2, "stat"]]), careerJson({ trainee: "B" }), sparkJson("original", "B", [["Power", 1, "stat"]])].join("\n"),
            "f.jsonl",
        )
        const join = joinSparks(outcomes, sparks)
        expect(join.unjoined).toHaveLength(0)
        expect(join.joinedCount).toBe(4)
        const a = join.careers.find((c) => c.outcome.trainee === "A")!
        const b = join.careers.find((c) => c.outcome.trainee === "B")!
        expect(a.all).toHaveLength(3)
        expect(a.kept).toHaveLength(1)
        expect(b.all).toHaveLength(1)
        expect(b.kept).toHaveLength(0)
    })

    it("never joins a spark record across files", () => {
        const p1 = parseCorpus(careerJson({ trainee: "A" }), "f1.jsonl")
        const p2 = parseCorpus(sparkJson("kept", "A", [["Speed", 2, "stat"]]), "f2.jsonl")
        const join = joinSparks([...p1.outcomes, ...p2.outcomes], [...p1.sparks, ...p2.sparks])
        expect(join.unjoined).toHaveLength(1)
        expect(join.careers).toHaveLength(0)
    })

    it("leaves a trainee-mismatched spark unjoined", () => {
        const { outcomes, sparks } = parseCorpus([careerJson({ trainee: "Alpha" }), sparkJson("kept", "Beta", [["Speed", 2, "stat"]])].join("\n"), "f.jsonl")
        const join = joinSparks(outcomes, sparks)
        expect(join.unjoined).toHaveLength(1)
        expect(join.careers).toHaveLength(0)
    })
})

describe("stat/aptitude canonicalization", () => {
    it("canonicalizes ordinary OCR variation to a blue stat", () => {
        expect(canonicalizeStatName("Speed")).toBe("Speed")
        expect(canonicalizeStatName("speed")).toBe("Speed")
        expect(canonicalizeStatName("Stamlna")).toBe("Stamina") // l->i slip
        expect(canonicalizeStatName("P0wer")).toBe("Power") // 0->o slip after digit strip
        expect(canonicalizeStatName("Guts")).toBe("Guts")
        expect(canonicalizeStatName("Wit")).toBe("Wit")
    })

    it("leaves distant or unreadable stat names unknown", () => {
        expect(canonicalizeStatName("")).toBeNull()
        expect(canonicalizeStatName("unreadable")).toBeNull()
        expect(canonicalizeStatName("Xyz")).toBeNull()
        expect(canonicalizeStatName("W1t")).toBeNull() // short token, ambiguous after digit strip
    })

    it("recognizes Long and Dirt conservatively", () => {
        expect(recognizeAptitude("Long")).toBe("Long")
        expect(recognizeAptitude("long")).toBe("Long")
        expect(recognizeAptitude("DIRT")).toBe("Dirt")
        expect(recognizeAptitude("Dirt ")).toBe("Dirt")
        expect(recognizeAptitude("Lung")).toBeNull()
        expect(recognizeAptitude("Turf")).toBeNull()
        expect(recognizeAptitude("")).toBeNull()
    })
})

describe("analyzeSparkFarm — confirmed-kept authority", () => {
    it("treats phase=kept as authoritative over original", () => {
        const { report } = analyze(careerJson({ trainee: "A" }), sparkJson("original", "A", [["Speed", 1, "stat"]]), sparkJson("kept", "A", [["Speed", 3, "stat"]]))
        const s = report.confirmedBlue.Speed
        expect(s.best).toBe(3)
        expect(s.threes).toBe(1)
        expect(s.hasThree).toBe(true)
        expect(s.ones).toBe(0) // the original 1-star is NOT counted as confirmed
        expect(report.coverage.careersWithKept).toBe(1)
    })

    it("does not describe original-only data as confirmed kept", () => {
        const { report } = analyze(careerJson({ trainee: "A" }), sparkJson("original", "A", [["Speed", 3, "stat"]]))
        expect(report.coverage.kept).toBe(0)
        expect(report.coverage.careersWithKept).toBe(0)
        expect(report.confirmedBlue.Speed.hasThree).toBe(false)
        expect(report.coverage.original).toBe(1)
    })

    it("does not describe rerolled-only data as confirmed kept", () => {
        const { report } = analyze(careerJson({ trainee: "A" }), sparkJson("rerolled", "A", [["Speed", 3, "stat"]]))
        expect(report.coverage.kept).toBe(0)
        expect(report.coverage.careersWithKept).toBe(0)
        expect(report.confirmedBlue.Speed.hasThree).toBe(false)
        expect(report.coverage.rerolled).toBe(1)
    })
})

describe("analyzeSparkFarm — blue-by-stat and missing 3-star", () => {
    it("computes best confirmed blue by stat and the missing-3-star list", () => {
        const { report } = analyze(
            careerJson({ trainee: "A" }),
            sparkJson("kept", "A", [["Speed", 2, "stat"]]),
            careerJson({ trainee: "B" }),
            sparkJson("kept", "B", [["Speed", 3, "stat"]]),
            careerJson({ trainee: "C" }),
            sparkJson("kept", "C", [["Stamina", 1, "stat"]]),
        )
        expect(report.confirmedBlue.Speed.best).toBe(3)
        expect(report.confirmedBlue.Speed.twos).toBe(1)
        expect(report.confirmedBlue.Speed.threes).toBe(1)
        expect(report.confirmedBlue.Speed.bestSource).toBe("B / URA Finale")
        expect(report.confirmedBlue.Stamina.best).toBe(1)
        // Speed is the only stat with a confirmed 3-star.
        expect(report.missingThreeBlue).toEqual(["Stamina", "Power", "Guts", "Wit"])
    })

    it("tracks unresolved stat-name reads separately instead of forcing them", () => {
        const { report } = analyze(careerJson({ trainee: "A" }), sparkJson("kept", "A", [["Xyz", 3, "stat"]]))
        expect(report.unknownStatReads).toBe(1)
        expect(report.confirmedBlue.Speed.hasThree).toBe(false)
        expect(report.missingThreeBlue).toHaveLength(5)
    })
})

describe("analyzeSparkFarm — Long/Dirt and final-stat eligibility", () => {
    it("reports confirmed Long/Dirt and counts unreadable aptitude rows", () => {
        const { report } = analyze(careerJson({ trainee: "A" }), sparkJson("kept", "A", [["Long", 2, "aptitude"], ["Dirt", 1, "aptitude"], ["unreadable", 0, "aptitude"]]))
        expect(report.long.best).toBe(2)
        expect(report.long.byStar[2]).toBe(1)
        expect(report.dirt.best).toBe(1)
        expect(report.unreadableAptitudeRows).toBe(1)
    })

    it("bands final stats below600 / 600-1099 / 1100+ and excludes incomplete careers", () => {
        const { report } = analyze(
            careerJson({ trainee: "A", spd: 500 }),
            careerJson({ trainee: "A", spd: 800 }),
            careerJson({ trainee: "A", spd: 1200 }),
            careerJson({ trainee: "A", spd: 1, outcome: "INCOMPLETE", result: "MANUALLY_STOPPED", turn: 5 }),
        )
        const spd = report.eligibility.overall.perStat.Speed
        expect(spd.n).toBe(3) // the incomplete career is excluded
        expect(spd.below600).toBe(1)
        expect(spd.mid).toBe(1)
        expect(spd.atLeast1100).toBe(1)
    })

    it("computes the all-five-at-least-600 rate over finished careers", () => {
        const { report } = analyze(careerJson({ trainee: "A" }), careerJson({ trainee: "A", spd: 500 }))
        const ov = report.eligibility.overall
        expect(ov.n).toBe(2)
        expect(ov.allFive600).toBe(1) // the spd=500 career fails the all-five gate
        expect(ov.avgBelow600).toBeCloseTo(0.5)
    })
})

describe("analyzeSparkFarm — yield sorting and low-N", () => {
    it("marks a low-N arm as low confidence and renders the marker", () => {
        const { report } = analyze(careerJson({ trainee: "A" }), sparkJson("kept", "A", [["Speed", 3, "stat"]]))
        expect(report.yield).toHaveLength(1)
        expect(report.yield[0].lowN).toBe(true)
        expect(report.yield[0].keptCount).toBe(1)
        const md = renderSparkFarmMarkdown(report)
        expect(md).toContain("anecdote, not signal")
    })
})

describe("renderSparkFarmMarkdown", () => {
    it("contains every required section and the corpus-honesty label", () => {
        const { report } = analyze(careerJson({ trainee: "A" }), sparkJson("kept", "A", [["Speed", 3, "stat"], ["Long", 2, "aptitude"]]))
        const md = renderSparkFarmMarkdown(report)
        expect(md).toContain("## 1. Corpus Coverage")
        expect(md).toContain("## 2. Confirmed Kept Blue Sparks")
        expect(md).toContain("## 3. Long and Dirt Aptitude Sparks")
        expect(md).toContain("## 4. Final-Stat Eligibility")
        expect(md).toContain("## 5. Farming Yield")
        expect(md).toContain("## 6. Next-Farm Guidance")
        expect(md).toContain("Missing from confirmed-kept corpus records")
    })
})

describe("analyzeSparkFarm — spark-only corpus", () => {
    it("does not crash and reports every spark as unjoined", () => {
        const { outcomes, sparks } = parseCorpus([sparkJson("original", "A", [["Speed", 1, "stat"]]), sparkJson("kept", "A", [["Speed", 3, "stat"]])].join("\n"), "sparks-only.jsonl")
        expect(outcomes).toHaveLength(0)
        const report = analyzeSparkFarm(outcomes, sparks)
        expect(report.coverage.careerOutcomes).toBe(0)
        expect(report.coverage.sparkRecords).toBe(2)
        expect(report.coverage.unjoined).toBe(2)
        expect(report.coverage.careersWithKept).toBe(0)
        expect(() => renderSparkFarmMarkdown(report)).not.toThrow()
    })
})

// Direct fp/scenario attribution on spark records (the new on-device writer fields).
function sparkArmJson(phase: string, trainee: string, scenario: string | undefined, fp: string | undefined, rows: [string, number, string][]): string {
    return JSON.stringify({ type: "sparks", ts: 1, trainee, scenario, fp, phase, rows: rows.map(([name, stars, kind]) => ({ name, stars, kind })) })
}

describe("spark-record fp/scenario attribution", () => {
    it("parseCorpus reads direct fp and normalizes scenario onto the SparkRecord", () => {
        const { sparks } = parseCorpus(sparkArmJson("kept", "Test_Uma", "Unity_Cup", "arm1", [["Speed", 3, "stat"]]))
        expect(sparks[0].fp).toBe("arm1")
        expect(sparks[0].scenario).toBe("Unity Cup")
        expect(sparks[0].trainee).toBe("Test Uma")
    })

    it("prefers a direct fp: a matching fp joins, a mismatched fp is rejected despite positional adjacency", () => {
        const matched = parseCorpus([careerJson({ trainee: "A", app: "1.3.8", fp: "armA" }), sparkArmJson("kept", "A", "URA Finale", "armA", [["Speed", 3, "stat"]])].join("\n"), "f.jsonl")
        const j1 = joinSparks(matched.outcomes, matched.sparks)
        expect(j1.unjoined).toHaveLength(0)
        expect(j1.careers).toHaveLength(1)

        const mismatched = parseCorpus([careerJson({ trainee: "A", app: "1.3.8", fp: "armA" }), sparkArmJson("kept", "A", "URA Finale", "armDIFFERENT", [["Speed", 3, "stat"]])].join("\n"), "f.jsonl")
        const j2 = joinSparks(mismatched.outcomes, mismatched.sparks)
        expect(j2.unjoined).toHaveLength(1) // direct fp overrides the positional guess
        expect(j2.careers).toHaveLength(0)
    })

    it("carries the same fp/scenario across original, rerolled and kept, joining them to one arm", () => {
        const { outcomes, sparks } = parseCorpus(
            [
                careerJson({ trainee: "A", app: "1.3.8", fp: "armX" }),
                sparkArmJson("original", "A", "Unity Cup", "armX", [["Speed", 1, "stat"]]),
                sparkArmJson("rerolled", "A", "Unity Cup", "armX", [["Speed", 2, "stat"]]),
                sparkArmJson("kept", "A", "Unity Cup", "armX", [["Speed", 3, "stat"]]),
            ].join("\n"),
            "f.jsonl",
        )
        const report = analyzeSparkFarm(outcomes, sparks)
        expect(report.coverage.joined).toBe(3)
        expect(report.coverage.unjoined).toBe(0)
        expect(report.confirmedBlue.Speed.hasThree).toBe(true)
        expect(report.yield).toHaveLength(1)
        expect(report.yield[0].arm).toBe("1.3.8@armX") // arm reflects the career's fp
    })

    it("still analyzes historical records that lack fp/scenario (positional fallback preserved)", () => {
        const { outcomes, sparks } = parseCorpus([careerJson({ trainee: "A" }), sparkJson("kept", "A", [["Speed", 3, "stat"]])].join("\n"), "old.jsonl")
        expect(sparks[0].fp).toBeUndefined()
        expect(sparks[0].scenario).toBeUndefined()
        const report = analyzeSparkFarm(outcomes, sparks)
        expect(report.coverage.unjoined).toBe(0)
        expect(report.confirmedBlue.Speed.hasThree).toBe(true)
    })
})

// ===========================================================================
// skill_spend records
// ===========================================================================

describe("parseCorpus — skill_spend records", () => {
    it("parses a valid skill_spend record with normalized trainee and scenario", () => {
        const { outcomes, sparks, skillSpends } = parseCorpus(
            skillSpendJson({
                trigger: "HIGH_WATER",
                plan: "skillPointCheck",
                trainee: "Super_Creek",
                scenario: "Unity_Cup",
                fp: "1e681a57e1",
                turn: 40,
                spBefore: 400,
                spAfter: 58,
                unspent: 58,
                proposed: [{ name: "A", price: 200 }],
                confirmed: ["A"],
            }),
            "c.jsonl",
        )
        expect(outcomes).toHaveLength(0)
        expect(sparks).toHaveLength(0)
        expect(skillSpends).toHaveLength(1)
        const r = skillSpends[0]
        expect(r.outcome).toBe("committed")
        expect(r.trigger).toBe("HIGH_WATER")
        expect(r.trainee).toBe("Super Creek")
        expect(r.scenario).toBe("Unity Cup")
        expect(r.fp).toBe("1e681a57e1")
        expect(r.proposed).toEqual([{ name: "A", price: 200 }])
        expect(r.confirmed).toEqual(["A"])
        expect(r.lineNumber).toBe(0)
    })

    it("omits absent optional fields rather than defaulting them", () => {
        const { skillSpends } = parseCorpus(skillSpendJson({ outcome: "empty_plan" }), "c.jsonl")
        const r = skillSpends[0]
        expect(r.outcome).toBe("empty_plan")
        expect(r.trainee).toBeUndefined()
        expect(r.fp).toBeUndefined()
        expect(r.spBefore).toBeUndefined()
        expect(r.proposed).toEqual([])
        expect(r.confirmed).toEqual([])
    })

    it("skips a skill_spend record with no usable outcome instead of inventing one", () => {
        const malformed = JSON.stringify({ type: "skill_spend", ts: 1 })
        const { skillSpends } = parseCorpus(`${malformed}\n${skillSpendJson()}\n`, "c.jsonl")
        expect(skillSpends).toHaveLength(1)
    })

    it("parses trigger-v2 threshold-policy fields and keeps trigger distinct from reason", () => {
        const { skillSpends } = parseCorpus(
            skillSpendJson({ policy: "trigger-v2", trigger: "HIGH_WATER", threshold: 600, tier: "established", reason: "adaptive threshold 600 (established)" }),
            "c.jsonl",
        )
        const r = skillSpends[0]
        expect(r.policy).toBe("trigger-v2")
        expect(r.threshold).toBe(600)
        expect(r.tier).toBe("established")
        expect(r.reason).toBe("adaptive threshold 600 (established)")
        expect(r.trigger).toBe("HIGH_WATER")
    })

    it("parses trigger-v3 Phase 2A fields on all three trigger shapes", () => {
        const { skillSpends } = parseCorpus(
            [
                skillSpendJson({ policy: "trigger-v3", trigger: "CRITICAL_RACE", objective: "race_reward", criticalRace: "Kashiwa Kinen", criticalRaceSource: "goal_ocr", turnsUntilRace: 2 }),
                skillSpendJson({ policy: "trigger-v3", trigger: "PLANNED_SKILL_AFFORDABLE", objective: "sparks", plannedSkill: "Swinging Maestro", plannedSkillObservedPrice: 274 }),
                skillSpendJson({ policy: "trigger-v3", trigger: "HIGH_WATER", objective: "rank" }),
            ].join("\n") + "\n",
            "c.jsonl",
        )
        expect(skillSpends[0].criticalRace).toBe("Kashiwa Kinen")
        expect(skillSpends[0].criticalRaceSource).toBe("goal_ocr")
        expect(skillSpends[0].turnsUntilRace).toBe(2)
        expect(skillSpends[0].objective).toBe("race_reward")
        expect(skillSpends[1].plannedSkill).toBe("Swinging Maestro")
        expect(skillSpends[1].plannedSkillObservedPrice).toBe(274)
        expect(skillSpends[2].objective).toBe("rank")
        expect(skillSpends[2].criticalRace).toBeUndefined()
        expect(skillSpends[2].plannedSkill).toBeUndefined()
    })

    it("leaves the v3 fields undefined on older records rather than inferring them", () => {
        const { skillSpends } = parseCorpus(skillSpendJson({ policy: "trigger-v2", threshold: 1000, tier: "manual", reason: "manual threshold 1000" }), "c.jsonl")
        expect(skillSpends[0].objective).toBeUndefined()
        expect(skillSpends[0].criticalRace).toBeUndefined()
    })

    it("leaves the v2 fields undefined on trigger-v1 records rather than inferring them", () => {
        const { skillSpends } = parseCorpus(skillSpendJson({ trigger: "HIGH_WATER" }), "c.jsonl")
        const r = skillSpends[0]
        expect(r.threshold).toBeUndefined()
        expect(r.tier).toBeUndefined()
        expect(r.reason).toBeUndefined()
    })

    it("drops malformed proposed/skipped rows without discarding the record", () => {
        const raw = JSON.stringify({
            type: "skill_spend",
            ts: 1,
            outcome: "committed",
            proposed: [{ name: "Good", price: 100 }, { name: "NoPrice" }, null],
            skipped: [{ name: "S", reason: "unbought_after_passes" }, { name: "NoReason" }],
            confirmed: ["Good", 42],
        })
        const { skillSpends } = parseCorpus(raw, "c.jsonl")
        expect(skillSpends[0].proposed).toEqual([{ name: "Good", price: 100 }])
        expect(skillSpends[0].skipped).toEqual([{ name: "S", reason: "unbought_after_passes" }])
        expect(skillSpends[0].confirmed).toEqual(["Good"])
    })

    it("leaves career and spark parsing untouched in a mixed corpus (old corpora unchanged)", () => {
        const { outcomes, sparks, skillSpends } = parseCorpus(
            [careerJson({ trainee: "Test Uma" }), sparkJson("kept", "Test_Uma", [["Speed", 3, "stat"]]), skillSpendJson()].join("\n") + "\n",
            "c.jsonl",
        )
        expect(outcomes).toHaveLength(1)
        expect(outcomes[0].trainee).toBe("Test Uma")
        expect(sparks).toHaveLength(1)
        expect(sparks[0].phase).toBe("kept")
        expect(skillSpends).toHaveLength(1)
    })

    it("a corpus with no skill_spend records yields an empty list (backward compatible)", () => {
        const { skillSpends } = parseCorpus(`${careerJson()}\n${sparkJson("original", "T", [["Speed", 1, "stat"]])}\n`, "c.jsonl")
        expect(skillSpends).toEqual([])
    })
})

describe("analyzeSkillSpend", () => {
    it("counts sessions by trigger, plan and outcome", () => {
        const { skillSpends } = parseCorpus(
            [
                skillSpendJson({ trigger: "HIGH_WATER", plan: "skillPointCheck", outcome: "committed" }),
                skillSpendJson({ trigger: "HIGH_WATER", plan: "skillPointCheck", outcome: "nothing_to_buy" }),
                skillSpendJson({ trigger: "CAREER_COMPLETE", plan: "careerComplete", outcome: "committed" }),
            ].join("\n") + "\n",
            "c.jsonl",
        )
        const s = analyzeSkillSpend(skillSpends)
        expect(s.sessions).toBe(3)
        expect(s.committed).toBe(2)
        expect(s.byTrigger).toEqual({ HIGH_WATER: 2, CAREER_COMPLETE: 1 })
        expect(s.byPlan).toEqual({ skillPointCheck: 2, careerComplete: 1 })
        expect(s.byOutcome).toEqual({ committed: 2, nothing_to_buy: 1 })
    })

    it("summarizes SP before/after and unspent", () => {
        const { skillSpends } = parseCorpus(
            [skillSpendJson({ spBefore: 400, spAfter: 58, unspent: 58 }), skillSpendJson({ spBefore: 1000, spAfter: 120, unspent: 120 })].join("\n") + "\n",
            "c.jsonl",
        )
        const s = analyzeSkillSpend(skillSpends)
        expect(s.spBefore.n).toBe(2)
        expect(s.spBefore.min).toBe(400)
        expect(s.spBefore.max).toBe(1000)
        expect(s.unspent.min).toBe(58)
        expect(s.unspent.max).toBe(120)
    })

    it("groups by arm only where identity exists, counting the rest as unidentified", () => {
        const { skillSpends } = parseCorpus(
            [
                skillSpendJson({ trainee: "Super_Creek", scenario: "Unity_Cup", fp: "abc", unspent: 10 }),
                skillSpendJson({ trainee: "Super_Creek", scenario: "Unity_Cup", fp: "abc", unspent: 30 }),
                skillSpendJson({ trainee: "Super_Creek", scenario: "Unity_Cup" }),
            ].join("\n") + "\n",
            "c.jsonl",
        )
        const s = analyzeSkillSpend(skillSpends)
        expect(s.byArm).toHaveLength(1)
        expect(s.byArm[0]).toMatchObject({ trainee: "Super Creek", scenario: "Unity Cup", arm: "abc", sessions: 2 })
        expect(s.unidentified).toBe(1)
    })

    it("tallies proposed vs confirmed skills across sessions", () => {
        const { skillSpends } = parseCorpus(
            skillSpendJson({ proposed: [{ name: "A", price: 1 }, { name: "B", price: 2 }], confirmed: ["A"], skipped: [{ name: "B", reason: "unbought_after_passes" }] }),
            "c.jsonl",
        )
        const s = analyzeSkillSpend(skillSpends)
        expect(s.proposedSkills).toBe(2)
        expect(s.confirmedSkills).toBe(1)
    })

    it("handles an empty input without dividing by zero", () => {
        const s = analyzeSkillSpend([])
        expect(s.sessions).toBe(0)
        expect(s.spBefore).toEqual({ n: 0, p50: 0, min: 0, max: 0 })
        expect(renderSkillSpendMarkdown(s)).toContain("No `skill_spend` records")
    })

    it("counts sessions whose confirmation is known-incomplete", () => {
        const { skillSpends } = parseCorpus(
            [skillSpendJson({ confirmedIncomplete: true }), skillSpendJson({}), skillSpendJson({ confirmedIncomplete: true })].join("\n") + "\n",
            "c.jsonl",
        )
        expect(analyzeSkillSpend(skillSpends).confirmedIncomplete).toBe(2)
    })

    it("buckets sessions by threshold-policy tier, with pre-v2 records kept apart rather than inferred", () => {
        const { skillSpends } = parseCorpus(
            [
                skillSpendJson({ policy: "trigger-v2", threshold: 1000, tier: "manual", reason: "manual threshold 1000" }),
                skillSpendJson({ policy: "trigger-v2", threshold: 600, tier: "established", reason: "adaptive threshold 600 (established)" }),
                skillSpendJson({ policy: "trigger-v2", threshold: 600, tier: "established", reason: "adaptive threshold 600 (established)" }),
                skillSpendJson({}), // trigger-v1: governing policy unknown
            ].join("\n") + "\n",
            "c.jsonl",
        )
        const s = analyzeSkillSpend(skillSpends)
        expect(s.byTier).toEqual({ manual: 1, established: 2, "pre-v2": 1 })
    })

    it("buckets sessions by objective, with pre-v3 records kept apart rather than inferred", () => {
        const { skillSpends } = parseCorpus(
            [
                skillSpendJson({ policy: "trigger-v3", objective: "race_reward" }),
                skillSpendJson({ policy: "trigger-v3", objective: "rank" }),
                skillSpendJson({ policy: "trigger-v3", objective: "rank" }),
                skillSpendJson({}), // trigger-v1: objective unknown
            ].join("\n") + "\n",
            "c.jsonl",
        )
        const s = analyzeSkillSpend(skillSpends)
        expect(s.byObjective).toEqual({ race_reward: 1, rank: 2, "pre-v3": 1 })
        const md = renderSkillSpendMarkdown(s)
        expect(md).toContain("By objective")
        expect(md).toContain("race_reward=1")
    })
})

describe("renderSkillSpendMarkdown", () => {
    it("renders counts, point summary and the arm table without claiming policy quality", () => {
        const { skillSpends } = parseCorpus(
            [
                skillSpendJson({ trigger: "HIGH_WATER", plan: "skillPointCheck", trainee: "Super_Creek", scenario: "Unity_Cup", fp: "abc", spBefore: 400, spAfter: 58, unspent: 58 }),
                skillSpendJson({ trigger: "CAREER_COMPLETE", plan: "careerComplete", outcome: "aborted_entry" }),
            ].join("\n") + "\n",
            "c.jsonl",
        )
        const md = renderSkillSpendMarkdown(analyzeSkillSpend(skillSpends))
        expect(md).toContain("# Skill Spend")
        expect(md).toContain("Sessions: **2**")
        expect(md).toContain("HIGH_WATER=1")
        expect(md).toContain("aborted_entry=1")
        expect(md).toContain("| Super Creek | Unity Cup | abc |")
        expect(md).toContain("Descriptive only")
    })

    it("flags a known-incomplete confirmation instead of letting the bought count read as whole", () => {
        const { skillSpends } = parseCorpus(skillSpendJson({ confirmedIncomplete: true }), "c.jsonl")
        const md = renderSkillSpendMarkdown(analyzeSkillSpend(skillSpends))
        expect(md).toContain("missed a purchase: **1**")
        expect(md).toContain("understated")
    })

    it("stays silent about incompleteness when nothing proved a gap", () => {
        const { skillSpends } = parseCorpus(skillSpendJson({}), "c.jsonl")
        expect(renderSkillSpendMarkdown(analyzeSkillSpend(skillSpends))).not.toContain("missed a purchase")
    })

    it("identifies manual vs adaptive sessions in the tier line", () => {
        const { skillSpends } = parseCorpus(
            [
                skillSpendJson({ policy: "trigger-v2", threshold: 1000, tier: "manual", reason: "manual threshold 1000" }),
                skillSpendJson({ policy: "trigger-v2", threshold: 350, tier: "developing", reason: "adaptive threshold 350 (auto -> developing)" }),
            ].join("\n") + "\n",
            "c.jsonl",
        )
        const md = renderSkillSpendMarkdown(analyzeSkillSpend(skillSpends))
        expect(md).toContain("By threshold-policy tier")
        expect(md).toContain("manual=1")
        expect(md).toContain("developing=1")
    })
})
