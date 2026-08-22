import { valueCard } from "../cardValue.ts"
import { buildDeckTarget } from "../deckTarget.ts"
import {
    borrowCandidateCards,
    BORROW_POOL_SCHEMA,
    BORROW_POOL_SCHEMA_VERSION,
    BorrowPoolError,
    parseBorrowPoolSnapshot,
    resolveBorrowPool,
    type BorrowPoolSnapshot,
} from "../borrowPool.ts"
import { buildSupportCardIndex, parseSupportCardData, SUPPORT_CARD_SCHEMA, SUPPORT_CARD_SCHEMA_VERSION, type SupportCardData } from "../supportCardData.ts"

// A synthetic catalogue, so a test states the mechanic it proves rather than depending on which cards
// the live game happens to ship. Mirrors the fixture style of deckLab.test.ts.

const THRESHOLDS = [1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50]

function curve(points: Record<number, number>): number[] {
    return THRESHOLDS.map((level) => (points[level] === undefined ? -1 : points[level]))
}

interface CardSpec {
    id: number
    charaId: number
    title: string
    rarity?: "R" | "SR" | "SSR"
    supportType: string
    effects?: { type: number; curve: number[] }[]
}

function makeData(cards: CardSpec[], characters: Record<string, string>): SupportCardData {
    return parseSupportCardData({
        schema: SUPPORT_CARD_SCHEMA,
        schemaVersion: SUPPORT_CARD_SCHEMA_VERSION,
        source: "synthetic borrow-pool fixture",
        effectTypes: { "1": "Friendship Bonus", "3": "Speed Bonus", "8": "Training Effectiveness" },
        undecodedUniqueEffectTypeFloor: 100,
        effectLevelThresholds: THRESHOLDS,
        levelCapsByRarity: { R: [20, 25, 30, 35, 40], SR: [25, 30, 35, 40, 45], SSR: [30, 35, 40, 45, 50] },
        scenarios: [{ id: 3, name: "Grand Concert", statCapBonus: { Speed: 400, Stamina: 100, Power: 100, Guts: 300, Wit: 100 }, specialCharaIds: [], restrictedCardIds: [] }],
        characters,
        cards: cards.map((c) => ({
            id: c.id,
            charaId: c.charaId,
            title: c.title,
            rarity: c.rarity ?? "SSR",
            supportType: c.supportType,
            effects: c.effects ?? [{ type: 1, curve: curve({ 1: 10 }) }],
            uniqueEffect: null,
            hintSkillIds: [],
            groupMemberCharaIds: null,
            restrictedScenarioIds: [],
        })),
    })
}

const index = buildSupportCardIndex(
    makeData(
        [
            { id: 30001, charaId: 1001, title: "Speedster", supportType: "Speed", effects: [{ type: 3, curve: curve({ 1: 5, 50: 40 }) }] },
            { id: 30002, charaId: 1002, title: "Stamina Star", supportType: "Stamina" },
            // Two cards sharing character AND title: an ambiguous match the resolver must refuse.
            { id: 40001, charaId: 2001, title: "Twin", supportType: "Wit" },
            { id: 40002, charaId: 2001, title: "Twin", supportType: "Guts" },
        ],
        { "1001": "Alpha", "1002": "Bravo", "2001": "Charlie" },
    ),
)

function entry(overrides: Record<string, unknown>): Record<string, unknown> {
    return { source_type: "FRIEND", confidence: "High", ...overrides }
}

function snapshot(entries: Record<string, unknown>[], termination = "COMPLETE_VISIBLE_POOL"): BorrowPoolSnapshot {
    return parseBorrowPoolSnapshot({
        schema: BORROW_POOL_SCHEMA,
        schema_version: BORROW_POOL_SCHEMA_VERSION,
        scan_id: "test",
        source_screen: "borrow_picker",
        termination,
        entries,
    })
}

describe("borrow-pool parsing", () => {
    test("a snapshot that is not an object is rejected", () => {
        expect(() => parseBorrowPoolSnapshot(null)).toThrow(BorrowPoolError)
        expect(() => parseBorrowPoolSnapshot([])).toThrow(BorrowPoolError)
    })

    test("a snapshot with no entries array is rejected", () => {
        expect(() => parseBorrowPoolSnapshot({ termination: "COMPLETE_VISIBLE_POOL" })).toThrow(/no entries array/)
    })

    test("an unknown termination is rejected rather than treated as complete", () => {
        expect(() => parseBorrowPoolSnapshot({ entries: [], termination: "DONE" })).toThrow(/termination/)
    })

    test("snake_case and camelCase entry keys both parse", () => {
        const snap = parseBorrowPoolSnapshot({
            entries: [{ character: "Alpha", card_title: "Speedster", limit_break_index: 2, source_type: "guest" }],
            termination: "UI_END_REACHED",
        })
        expect(snap.entries[0].title).toBe("Speedster")
        expect(snap.entries[0].limitBreakIndex).toBe(2)
        expect(snap.entries[0].sourceType).toBe("GUEST")
    })

    test("an unrecognized source type falls back to UNKNOWN", () => {
        const snap = snapshot([entry({ character: "Alpha", title: "Speedster", source_type: "acquaintance" })])
        expect(snap.entries[0].sourceType).toBe("UNKNOWN")
    })
})

describe("borrow-pool identity resolution", () => {
    test("an exact character-and-title row resolves to one card", () => {
        const res = resolveBorrowPool(snapshot([entry({ character: "Alpha", title: "Speedster", limit_break_index: 4, level_cap: 50, current_level: 50 })]), index)
        expect(res.candidates).toHaveLength(1)
        expect(res.candidates[0].card.card.supportCardId).toBe(30001)
        expect(res.candidates[0].limitBreakKnown).toBe(true)
        expect(res.unresolved).toHaveLength(0)
    })

    test("an ambiguous row resolves to nothing and is kept as unresolved", () => {
        const res = resolveBorrowPool(snapshot([entry({ character: "Charlie", title: "Twin", limit_break_index: 4 })]), index)
        expect(res.candidates).toHaveLength(0)
        expect(res.unresolved).toHaveLength(1)
        expect(res.unresolved[0].reason).toBe("AMBIGUOUS_MATCH")
    })

    test("an unreadable row is set aside, never guessed", () => {
        const res = resolveBorrowPool(snapshot([entry({ character: "Ab", title: "" })]), index)
        expect(res.candidates).toHaveLength(0)
        expect(res.unresolved[0].reason).toBe("UNREADABLE_ROW")
    })

    test("the same card from two owners is one candidate carrying both sources", () => {
        const res = resolveBorrowPool(
            snapshot([
                entry({ character: "Alpha", title: "Speedster", limit_break_index: 4, level_cap: 50, current_level: 45, owner_alias: "friend-A" }),
                entry({ character: "Alpha", title: "Speedster", limit_break_index: 4, level_cap: 50, current_level: 50, source_type: "GUEST", owner_alias: "guest-B" }),
            ]),
            index,
        )
        expect(res.candidates).toHaveLength(1)
        expect(res.candidates[0].sources).toHaveLength(2)
        expect(res.candidates[0].warnings).toContain("MULTIPLE_SOURCES")
        // Best observed copy wins: the level-50 guest copy, not the level-45 friend copy.
        expect(res.candidates[0].card.level).toBe(50)
        expect(res.resolvedRows).toBe(2)
        expect(res.distinctCards).toBe(1)
    })

    test("two different cards from the same owner alias stay two candidates", () => {
        const res = resolveBorrowPool(
            snapshot([
                entry({ character: "Alpha", title: "Speedster", limit_break_index: 4, owner_alias: "friend-X" }),
                entry({ character: "Bravo", title: "Stamina Star", limit_break_index: 4, owner_alias: "friend-X" }),
            ]),
            index,
        )
        expect(res.candidates).toHaveLength(2)
        expect(res.candidates.every((c) => c.sources.length === 1)).toBe(true)
    })
})

describe("borrow-pool limit break", () => {
    test("an unobserved limit break lowers confidence and is marked", () => {
        const res = resolveBorrowPool(snapshot([entry({ character: "Bravo", title: "Stamina Star", limit_break_index: null, level_cap: null, current_level: null })]), index)
        expect(res.candidates[0].limitBreakKnown).toBe(false)
        expect(res.candidates[0].warnings).toContain("LIMIT_BREAK_UNKNOWN")
        expect(res.candidates[0].warnings).toContain("LEVEL_UNKNOWN")
        expect(res.notes.some((n) => n.includes("unobserved limit break"))).toBe(true)
    })

    test("the same card at different limit breaks yields different DeckLab value when its effects differ by level", () => {
        const build = buildDeckTarget({ trainee: null, scenario: "Grand Concert" }, index)
        const lb0 = resolveBorrowPool(snapshot([entry({ character: "Alpha", title: "Speedster", limit_break_index: 0, level_cap: 30, current_level: 30 })]), index).candidates[0].card
        const lb4 = resolveBorrowPool(snapshot([entry({ character: "Alpha", title: "Speedster", limit_break_index: 4, level_cap: 50, current_level: 50 })]), index).candidates[0].card
        expect(lb0.levelCap).toBe(30)
        expect(lb4.levelCap).toBe(50)
        const value = (card: typeof lb0) => valueCard(index, { supportCardId: card.card.supportCardId, level: card.level, levelCap: card.levelCap, limitBreak: card.limitBreak, borrowed: true, owned: false }, build).composite
        expect(value(lb4)).toBeGreaterThan(value(lb0))
    })
})

describe("borrow-pool completeness semantics", () => {
    test("a complete visible pool with every row resolved is trusted", () => {
        const res = resolveBorrowPool(snapshot([entry({ character: "Alpha", title: "Speedster", limit_break_index: 4 })], "COMPLETE_VISIBLE_POOL"), index)
        expect(res.trustedAsCompletePool).toBe(true)
    })

    test("a bounded-partial scan is never trusted as complete", () => {
        const res = resolveBorrowPool(snapshot([entry({ character: "Alpha", title: "Speedster", limit_break_index: 4 })], "BOUNDED_PARTIAL"), index)
        expect(res.trustedAsCompletePool).toBe(false)
        expect(res.notes.some((n) => n.includes("BOUNDED_PARTIAL"))).toBe(true)
    })

    test("an unexpected screen is never trusted as complete", () => {
        const res = resolveBorrowPool(snapshot([entry({ character: "Alpha", title: "Speedster", limit_break_index: 4 })], "UNEXPECTED_SCREEN"), index)
        expect(res.trustedAsCompletePool).toBe(false)
    })

    test("a complete scan with an unresolved row is not trusted as a complete pool", () => {
        const res = resolveBorrowPool(
            snapshot([entry({ character: "Alpha", title: "Speedster", limit_break_index: 4 }), entry({ character: "Charlie", title: "Twin" })], "COMPLETE_VISIBLE_POOL"),
            index,
        )
        expect(res.trustedAsCompletePool).toBe(false)
    })
})

describe("borrow-pool integration boundary", () => {
    test("resolved candidates are exactly the OwnedSupportCard list searchDecks takes, with unique ids", () => {
        const res = resolveBorrowPool(
            snapshot([
                entry({ character: "Alpha", title: "Speedster", limit_break_index: 4, owner_alias: "a" }),
                entry({ character: "Alpha", title: "Speedster", limit_break_index: 4, owner_alias: "b" }),
                entry({ character: "Bravo", title: "Stamina Star", limit_break_index: 4 }),
            ]),
            index,
        )
        const cards = borrowCandidateCards(res)
        expect(cards).toHaveLength(2)
        const ids = cards.map((c) => c.card.supportCardId)
        expect(new Set(ids).size).toBe(ids.length)
        expect(cards.every((c) => c.owned)).toBe(true)
    })
})

describe("borrow-pool determinism", () => {
    test("the same raw observations resolve to a byte-identical result", () => {
        const raw = {
            schema: BORROW_POOL_SCHEMA,
            scan_id: "det",
            source_screen: "borrow_picker",
            termination: "COMPLETE_VISIBLE_POOL",
            entries: [
                entry({ character: "Bravo", title: "Stamina Star", limit_break_index: 3, level_cap: 40, current_level: 40, owner_alias: "z" }),
                entry({ character: "Alpha", title: "Speedster", limit_break_index: 4, level_cap: 50, current_level: 50, owner_alias: "a" }),
            ],
        }
        const first = resolveBorrowPool(parseBorrowPoolSnapshot(raw), index)
        const second = resolveBorrowPool(parseBorrowPoolSnapshot(raw), index)
        expect(JSON.stringify(second)).toBe(JSON.stringify(first))
    })
})
