import { parseLineageRecords } from "../lineage.ts"

describe("parseLineageRecords", () => {
    const good = JSON.stringify({
        type: "lineage_selected",
        schemaVersion: 1,
        launchTransactionId: "tx-1",
        ts: 5000,
        scenario: "URA_Finale",
        trainee: "Special_Week",
        captureStatus: "captured",
        ancestors: [
            {
                role: "legacy1_parent",
                slotIndex: 0,
                portraitObserved: true,
                ownership: "owned",
                matchStatus: "probable_owned_match",
                hasLeadTriple: true,
                completeness: 1,
                factorFingerprint: "fp",
                factors: [{ kind: "stat", displayText: "Power", stars: 3 }],
            },
        ],
    })

    it("parses a well-formed record and normalizes scenario underscores", () => {
        const [r] = parseLineageRecords(good + "\n", "lineage.jsonl")
        expect(r.launchTransactionId).toBe("tx-1")
        expect(r.captureStatus).toBe("captured")
        expect(r.scenario).toBe("URA Finale")
        expect(r.ancestors).toHaveLength(1)
        expect(r.ancestors[0].factors[0].stars).toBe(3)
        expect(r.file).toBe("lineage.jsonl")
    })

    it("skips malformed lines, foreign record types, and bad capture statuses without failing", () => {
        const text = ["not json {", JSON.stringify({ type: "sparks", rows: [] }), JSON.stringify({ type: "lineage_selected", captureStatus: "bogus", ancestors: [] }), good].join("\n")
        const records = parseLineageRecords(text + "\n")
        expect(records).toHaveLength(1)
        expect(records[0].launchTransactionId).toBe("tx-1")
    })

    it("drops a malformed ancestor or factor but keeps the surrounding record", () => {
        const withJunk = JSON.stringify({
            type: "lineage_selected",
            schemaVersion: 1,
            launchTransactionId: "tx-2",
            captureStatus: "partial",
            ancestors: [
                null,
                { slotIndex: 1 }, // no role -> dropped
                {
                    role: "legacy1_parent",
                    slotIndex: 0,
                    factors: [{ kind: "stat", stars: "notnum" }, { kind: "unique", displayText: "U", stars: 2 }],
                },
            ],
        })
        const [r] = parseLineageRecords(withJunk + "\n")
        expect(r.ancestors).toHaveLength(1)
        expect(r.ancestors[0].factors).toHaveLength(1) // the non-numeric-star factor is dropped
        expect(r.ancestors[0].factors[0].displayText).toBe("U")
    })

    it("treats a missing launch id as unjoinable (null), never a fabricated value", () => {
        const noId = JSON.stringify({ type: "lineage_selected", captureStatus: "captured", ancestors: [] })
        const [r] = parseLineageRecords(noId + "\n")
        expect(r.launchTransactionId).toBeNull()
    })
})
