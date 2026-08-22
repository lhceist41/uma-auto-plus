import { parseBorrowScanJsonl } from "../borrowScanImport.ts"
import { BorrowPoolError, resolveBorrowPool } from "../borrowPool.ts"
import { buildSupportCardIndex, parseSupportCardData, SUPPORT_CARD_SCHEMA, SUPPORT_CARD_SCHEMA_VERSION, type SupportCardData } from "../supportCardData.ts"

// A synthetic catalogue so a test states the mechanic it proves rather than depending on which cards
// the live game ships. Mirrors borrowPool.test.ts.

const THRESHOLDS = [1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50]

function curve(points: Record<number, number>): number[] {
    return THRESHOLDS.map((level) => (points[level] === undefined ? -1 : points[level]))
}

function makeData(): SupportCardData {
    return parseSupportCardData({
        schema: SUPPORT_CARD_SCHEMA,
        schemaVersion: SUPPORT_CARD_SCHEMA_VERSION,
        source: "synthetic borrow-scan fixture",
        effectTypes: { "1": "Friendship Bonus", "3": "Speed Bonus" },
        undecodedUniqueEffectTypeFloor: 100,
        effectLevelThresholds: THRESHOLDS,
        levelCapsByRarity: { R: [20, 25, 30, 35, 40], SR: [25, 30, 35, 40, 45], SSR: [30, 35, 40, 45, 50] },
        scenarios: [{ id: 3, name: "Grand Concert", statCapBonus: { Speed: 400, Stamina: 100, Power: 100, Guts: 300, Wit: 100 }, specialCharaIds: [], restrictedCardIds: [] }],
        characters: { "1001": "Alpha", "1002": "Bravo" },
        cards: [
            { id: 30001, charaId: 1001, title: "Speedster", rarity: "SSR", supportType: "Speed", effects: [{ type: 3, curve: curve({ 1: 5, 50: 40 }) }], uniqueEffect: null, hintSkillIds: [], groupMemberCharaIds: null, restrictedScenarioIds: [] },
            { id: 30002, charaId: 1002, title: "Stamina Star", rarity: "SSR", supportType: "Stamina", effects: [{ type: 1, curve: curve({ 1: 10 }) }], uniqueEffect: null, hintSkillIds: [], groupMemberCharaIds: null, restrictedScenarioIds: [] },
        ],
    })
}

const index = buildSupportCardIndex(makeData())

function row(fields: Record<string, unknown>): string {
    return JSON.stringify({ record: "borrow_pool_row", scan_id: "bp-1", source_type: "FOLLOW", confidence: "high", ...fields })
}

function header(fields: Record<string, unknown> = {}): string {
    return JSON.stringify({ record: "borrow_pool_scan", schema: "deck_lab_borrow_pool", scan_id: "bp-1", snapshot_termination: "UI_END_REACHED", completed_at: 123, ...fields })
}

describe("borrow-scan JSONL bridge", () => {
    test("rows and a header assemble into a snapshot with the header's termination", () => {
        const jsonl = [row({ character: "Alpha", title: "Speedster", limit_break_index: 4 }), header()].join("\n")
        const snap = parseBorrowScanJsonl(jsonl)
        expect(snap.entries).toHaveLength(1)
        expect(snap.entries[0].character).toBe("Alpha")
        expect(snap.termination).toBe("UI_END_REACHED")
    })

    test("the raw owner name is dropped; only the redacted alias crosses into the snapshot", () => {
        const jsonl = [row({ character: "Alpha", title: "Speedster", limit_break_index: 4, owner_alias: "owner-abc", owner_name_raw: "RealPlayerName" }), header()].join("\n")
        const snap = parseBorrowScanJsonl(jsonl)
        expect(snap.entries[0].ownerAlias).toBe("owner-abc")
        expect(JSON.stringify(snap)).not.toContain("RealPlayerName")
    })

    test("a JSONL with no header is a partial and can never read as a complete pool", () => {
        const jsonl = row({ character: "Alpha", title: "Speedster", limit_break_index: 4 })
        const snap = parseBorrowScanJsonl(jsonl)
        expect(snap.termination).toBe("BOUNDED_PARTIAL")
        const res = resolveBorrowPool(snap, index)
        expect(res.trustedAsCompletePool).toBe(false)
    })

    test("with two concatenated scans the last header wins and only its rows are kept", () => {
        const jsonl = [
            JSON.stringify({ record: "borrow_pool_row", scan_id: "old", character: "Bravo", title: "Stamina Star", source_type: "FOLLOW" }),
            JSON.stringify({ record: "borrow_pool_scan", scan_id: "old", snapshot_termination: "UI_END_REACHED" }),
            row({ character: "Alpha", title: "Speedster", limit_break_index: 4 }),
            header(),
        ].join("\n")
        const snap = parseBorrowScanJsonl(jsonl)
        expect(snap.scanId).toBe("bp-1")
        expect(snap.entries).toHaveLength(1)
        expect(snap.entries[0].character).toBe("Alpha")
    })

    test("blank lines are ignored and a malformed line is rejected", () => {
        const ok = ["", row({ character: "Alpha", title: "Speedster", limit_break_index: 4 }), "", header(), ""].join("\n")
        expect(parseBorrowScanJsonl(ok).entries).toHaveLength(1)
        expect(() => parseBorrowScanJsonl("not json\n" + header())).toThrow(BorrowPoolError)
    })

    test("the same card from two owners resolves to one candidate carrying both sources", () => {
        const jsonl = [
            row({ character: "Alpha", title: "Speedster", limit_break_index: 4, level: 45, owner_alias: "owner-1" }),
            row({ character: "Alpha", title: "Speedster", limit_break_index: 4, level: 50, source_type: "GUEST", owner_alias: "owner-2" }),
            header(),
        ].join("\n")
        const res = resolveBorrowPool(parseBorrowScanJsonl(jsonl), index)
        expect(res.candidates).toHaveLength(1)
        expect(res.candidates[0].sources).toHaveLength(2)
        expect(res.distinctCards).toBe(1)
    })

    test("a complete scan with every row resolved is trusted as a complete pool", () => {
        const jsonl = [row({ character: "Alpha", title: "Speedster", limit_break_index: 4 }), row({ character: "Bravo", title: "Stamina Star", limit_break_index: 4 }), header()].join("\n")
        const res = resolveBorrowPool(parseBorrowScanJsonl(jsonl), index)
        expect(res.trustedAsCompletePool).toBe(true)
    })

    test("the bridge assembles deterministically", () => {
        const jsonl = [row({ character: "Alpha", title: "Speedster", limit_break_index: 4, owner_alias: "owner-1" }), header()].join("\n")
        expect(JSON.stringify(parseBorrowScanJsonl(jsonl))).toBe(JSON.stringify(parseBorrowScanJsonl(jsonl)))
    })
})
