import { resolveCardIdentity, titleSimilarity, type CardIdentityMatch } from "../cardIdentity.ts"
import { buildSupportCardIndex, parseSupportCardData, SUPPORT_CARD_SCHEMA, SUPPORT_CARD_SCHEMA_VERSION, type SupportCardData } from "../supportCardData.ts"

// A synthetic catalogue that states the OCR-recovery mechanic each test proves, rather than depending
// on which cards the live game ships. Mirrors the fixture style of borrowPool.test.ts.

const THRESHOLDS = [1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50]

interface CardSpec {
    id: number
    charaId: number
    title: string | null
    rarity?: "R" | "SR" | "SSR"
    supportType: string
}

function makeIndex(cards: CardSpec[], characters: Record<string, string>) {
    const data: SupportCardData = parseSupportCardData({
        schema: SUPPORT_CARD_SCHEMA,
        schemaVersion: SUPPORT_CARD_SCHEMA_VERSION,
        source: "synthetic card-identity fixture",
        effectTypes: { "1": "Friendship Bonus" },
        undecodedUniqueEffectTypeFloor: 100,
        effectLevelThresholds: THRESHOLDS,
        levelCapsByRarity: { R: [20, 25, 30, 35, 40], SR: [25, 30, 35, 40, 45], SSR: [30, 35, 40, 45, 50] },
        scenarios: [],
        characters,
        cards: cards.map((c) => ({
            id: c.id,
            charaId: c.charaId,
            title: c.title,
            rarity: c.rarity ?? "SSR",
            supportType: c.supportType,
            effects: [{ type: 1, curve: THRESHOLDS.map(() => 10) }],
            uniqueEffect: null,
            hintSkillIds: [],
            groupMemberCharaIds: null,
            restrictedScenarioIds: [],
        })),
    })
    return buildSupportCardIndex(data)
}

const index = makeIndex(
    [
        // Fukukitaru: one titled SSR with a decorative note, an SR distractor, and the untitled R card.
        { id: 30001, charaId: 1, title: "Touching Sleeves Is Good Luck! ♪", supportType: "Speed" },
        { id: 20001, charaId: 1, title: "Fate's Forecast", rarity: "SR", supportType: "Wit" },
        { id: 10001, charaId: 1, title: null, rarity: "R", supportType: "Wit" },
        // Tannhauser: a Guts card whose title is close to nothing else it owns, plus a far Wit distractor.
        { id: 30010, charaId: 2, title: "Just Keep Going", supportType: "Guts" },
        { id: 30011, charaId: 2, title: "Machitan Adventure", rarity: "SSR", supportType: "Wit" },
        // Charlie: two titles a hair apart, to prove a close call stays unresolved.
        { id: 30020, charaId: 3, title: "Dream Big", supportType: "Speed" },
        { id: 30021, charaId: 3, title: "Dream High", supportType: "Power" },
        // Delta: a single titled variant plus its R card, for the single-variant and R-card rules.
        { id: 30030, charaId: 4, title: "Sentimental Flare ♪", supportType: "Speed" },
        { id: 10030, charaId: 4, title: null, rarity: "R", supportType: "Speed" },
        // A group card recorded under a character the picker prints as the group's name.
        { id: 30040, charaId: 5, title: "Group Anthem", supportType: "Group" },
    ],
    { "1": "Fukukitaru", "2": "Tannhauser", "3": "Charlie", "4": "Delta", "5": "Rudolf" },
)

const asMatch = (r: ReturnType<typeof resolveCardIdentity>): CardIdentityMatch => {
    if ("reason" in r) throw new Error(`expected a match, got reject ${r.reason}: ${r.detail}`)
    return r
}

describe("titleSimilarity", () => {
    test("identical strings score 1, empty pair scores 1", () => {
        expect(titleSimilarity("abc", "abc")).toBe(1)
        expect(titleSimilarity("", "")).toBe(1)
    })
    test("one edit in a short string is penalised more than in a long one", () => {
        expect(titleSimilarity("ume", "umel")).toBeCloseTo(0.75, 5)
        expect(titleSimilarity("justkeepgoing", "justkeepgoingl")).toBeGreaterThan(0.9)
    })
})

describe("exact and near-exact title", () => {
    test("a clean title resolves exactly", () => {
        const m = asMatch(resolveCardIdentity(index, { character: "Fukukitaru", title: "Touching Sleeves Is Good Luck! ♪", rarity: "SSR", supportType: "Speed" }))
        expect(m.card.id).toBe(30001)
        expect(m.path).toBe("EXACT_TITLE")
        expect(m.score).toBe(1)
    })

    test("a lost decorative note still resolves exactly, since normalization drops it", () => {
        const m = asMatch(resolveCardIdentity(index, { character: "Fukukitaru", title: "Touching Sleeves Is Good Luck!", rarity: "SSR", supportType: "Speed" }))
        expect(m.card.id).toBe(30001)
        expect(m.path).toBe("EXACT_TITLE")
    })

    test("trailing bracket and punctuation noise still resolves exactly", () => {
        const m = asMatch(resolveCardIdentity(index, { character: "Fukukitaru", title: "Touching Sleeves Is Good Luck! >", rarity: "SSR", supportType: "Speed" }))
        expect(m.card.id).toBe(30001)
        expect(m.path).toBe("EXACT_TITLE")
    })
})

describe("character-local fuzzy recovery", () => {
    test("an Is->ls OCR confusion recovers within the character's own cards", () => {
        const m = asMatch(resolveCardIdentity(index, { character: "Fukukitaru", title: "Touching Sleeves ls Good Luck! ♪", rarity: "SSR", supportType: "Speed" }))
        expect(m.card.id).toBe(30001)
        expect(m.path).toBe("CHARACTER_LOCAL_FUZZY")
        expect(m.margin).toBeGreaterThan(0.15)
    })

    test("a trailing !->l corruption recovers", () => {
        const m = asMatch(resolveCardIdentity(index, { character: "Tannhauser", title: "Just Keep Goingl", rarity: "SSR", supportType: "Guts" }))
        expect(m.card.id).toBe(30010)
        expect(m.path).toBe("CHARACTER_LOCAL_FUZZY")
    })

    test("a badge glyph appended to a single-variant title recovers", () => {
        const m = asMatch(resolveCardIdentity(index, { character: "Delta", title: "Sentimental Flare D1", rarity: "SSR", supportType: "Speed" }))
        expect(m.card.id).toBe(30030)
        expect(m.path).toBe("CHARACTER_LOCAL_FUZZY")
        expect(m.margin).toBe(1)
    })
})

describe("ambiguity and low confidence stay unresolved", () => {
    test("two same-character titles within margin remain unresolved", () => {
        const r = resolveCardIdentity(index, { character: "Charlie", title: "Dream Bigh", rarity: "SSR", supportType: "Speed" })
        expect("reason" in r && r.reason).toBe("AMBIGUOUS_FUZZY")
    })

    test("a title far from every card the character owns is rejected, not forced onto the single best", () => {
        const r = resolveCardIdentity(index, { character: "Delta", title: "Totally Unrelated Name", rarity: "SSR", supportType: "Speed" })
        expect("reason" in r && r.reason).toBe("LOW_SIMILARITY")
    })

    test("a confidently observed rarity that conflicts blocks a fuzzy recovery", () => {
        const r = resolveCardIdentity(index, { character: "Delta", title: "Sentimental Flare D1", rarity: "R", supportType: "Speed" })
        expect("reason" in r && r.reason).toBe("RARITY_CONFLICT")
    })

    test("an unknown character never fuzzy-matches against the whole catalogue", () => {
        const r = resolveCardIdentity(index, { character: "Nobody At All", title: "Just Keep Goingl", rarity: "SSR", supportType: "Guts" })
        expect("reason" in r && r.reason).toBe("NO_CANDIDATE")
    })
})

describe("support type is corroboration only, never identity", () => {
    test("a wrong observed type does not veto a strong exact title match", () => {
        const m = asMatch(resolveCardIdentity(index, { character: "Tannhauser", title: "Just Keep Going", rarity: "SSR", supportType: "Wit" }))
        expect(m.card.id).toBe(30010)
        expect(m.card.supportType).toBe("Guts")
        expect(m.typeCorroborated).toBe(false)
    })

    test("a wrong observed type cannot pull a fuzzy match onto a same-character card of that type", () => {
        // Observed type Wit; the Wit card (30011) is far by title, the Guts card (30010) is the real match.
        const m = asMatch(resolveCardIdentity(index, { character: "Tannhauser", title: "Just Keep Goingl", rarity: "SSR", supportType: "Wit" }))
        expect(m.card.id).toBe(30010)
        expect(m.card.supportType).toBe("Guts")
    })
})

describe("title-only and rarity fallbacks", () => {
    test("a group card the picker prints under the group name resolves by unique title alone", () => {
        const m = asMatch(resolveCardIdentity(index, { character: "Heirs To The Throne", title: "Group Anthem", rarity: "SSR", supportType: "Group" }))
        expect(m.card.id).toBe(30040)
        expect(m.path).toBe("TITLE_ONLY")
    })

    test("an untitled R card resolves by character and rarity", () => {
        const m = asMatch(resolveCardIdentity(index, { character: "Delta", title: "", rarity: "R", supportType: "Speed" }))
        expect(m.card.id).toBe(10030)
        expect(m.path).toBe("CHARACTER_AND_RARITY")
    })

    test("fuzzy can be turned off, leaving an exact-only resolver", () => {
        const r = resolveCardIdentity(index, { character: "Tannhauser", title: "Just Keep Goingl", rarity: "SSR", supportType: "Guts" }, { allowFuzzy: false })
        expect("reason" in r && r.reason).toBe("NO_CANDIDATE")
    })
})

describe("determinism", () => {
    test("the same row and catalogue produce the same result and path", () => {
        const a = resolveCardIdentity(index, { character: "Fukukitaru", title: "Touching Sleeves ls Good Luck! ♪", rarity: "SSR", supportType: "Speed" })
        const b = resolveCardIdentity(index, { character: "Fukukitaru", title: "Touching Sleeves ls Good Luck! ♪", rarity: "SSR", supportType: "Speed" })
        expect(a).toEqual(b)
    })
})
