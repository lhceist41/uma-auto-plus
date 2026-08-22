import { readFileSync } from "node:fs"
import { join } from "node:path"
import { compositeOf, EDITORIAL_DIMENSION_WEIGHTS, targetWeightedComposite, valueCard } from "../cardValue.ts"
import { buildCommunityPriorIndex, COMMUNITY_PRIOR_SCHEMA, COMMUNITY_PRIOR_SCHEMA_VERSION, CommunityPriorError, parseCommunityPrior } from "../communityPrior.ts"
import { checkDeckLegality, concaveSum, DECK_SIZE, scoreDeck } from "../deck.ts"
import { hypotheticalBorrowPool, searchDecks } from "../deckSearch.ts"
import { buildDeckTarget, resolveScenario, statWeightsFor } from "../deckTarget.ts"
import { assessInventory, buildFixtureInventory, buildOwnedInventory } from "../inventory.ts"
import { buildDeckLabReport } from "../report.ts"
import {
    buildSupportCardIndex,
    levelCapFor,
    limitBreakForLevelCap,
    parseSupportCardData,
    resolveEffectValue,
    resolveTotalEffects,
    resolveUniqueEffect,
    SUPPORT_CARD_SCHEMA,
    SUPPORT_CARD_SCHEMA_VERSION,
    SupportCardDataError,
    type SupportCardData,
    type SupportCardRecord,
} from "../supportCardData.ts"
import { assessCorpus, buildDeckObservation, DeckObservationError, MIN_OBSERVATIONS_FOR_BASELINE } from "../telemetry.ts"

// A synthetic catalogue rather than the shipped one, so a test states the mechanic it is proving
// instead of depending on which cards the live game happens to have. One test at the bottom does load
// the real asset, to prove the parser and the shipped file agree.

const THRESHOLDS = [1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50]

/** Builds an effect curve from level -> value pairs, filling the rest with the -1 hole marker. */
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
    uniqueEffect?: SupportCardRecord["uniqueEffect"]
    hintSkillIds?: number[]
    restrictedScenarioIds?: number[]
}

function makeData(cards: CardSpec[], characters: Record<string, string>): SupportCardData {
    return parseSupportCardData({
        schema: SUPPORT_CARD_SCHEMA,
        schemaVersion: SUPPORT_CARD_SCHEMA_VERSION,
        source: "synthetic test fixture",
        effectTypes: { "1": "Friendship Bonus", "8": "Training Effectiveness", "14": "Initial Friendship Gauge", "15": "Race Bonus", "17": "Hint Levels", "19": "Specialty Priority", "30": "Skill Point Bonus" },
        undecodedUniqueEffectTypeFloor: 100,
        effectLevelThresholds: THRESHOLDS,
        levelCapsByRarity: { R: [20, 25, 30, 35, 40], SR: [25, 30, 35, 40, 45], SSR: [30, 35, 40, 45, 50] },
        scenarios: [
            { id: 3, name: "Grand Concert", statCapBonus: { Speed: 400, Stamina: 100, Power: 100, Guts: 300, Wit: 100 }, specialCharaIds: [1002], restrictedCardIds: [] },
            { id: 4, name: "Trackblazer", statCapBonus: { Speed: 0, Stamina: 700, Power: 0, Guts: 0, Wit: 300 }, specialCharaIds: [], restrictedCardIds: [] },
        ],
        characters,
        cards: cards.map((c) => ({
            id: c.id,
            charaId: c.charaId,
            title: c.title,
            rarity: c.rarity ?? "SSR",
            supportType: c.supportType,
            effects: c.effects ?? [{ type: 1, curve: curve({ 1: 10 }) }],
            uniqueEffect: c.uniqueEffect ?? null,
            hintSkillIds: c.hintSkillIds ?? [],
            groupMemberCharaIds: null,
            restrictedScenarioIds: c.restrictedScenarioIds ?? [],
        })),
    })
}

function ownedRow(character: string, title: string, overrides: Record<string, unknown> = {}) {
    return { character, card_title: title, confidence: "High", limit_break_index: 4, ...overrides }
}

describe("support card catalogue", () => {
    const data = makeData([{ id: 30001, charaId: 1001, title: "A", supportType: "Speed", effects: [{ type: 1, curve: curve({ 1: 5, 25: 10, 50: 15 }) }] }], { "1001": "Alpha" })

    test("an effect curve is a step function, and a hole is not a zero", () => {
        const c = curve({ 1: 5, 25: 10, 50: 15 })
        expect(resolveEffectValue(data, c, 1)).toBe(5)
        expect(resolveEffectValue(data, c, 24)).toBe(5)
        expect(resolveEffectValue(data, c, 25)).toBe(10)
        expect(resolveEffectValue(data, c, 49)).toBe(10)
        expect(resolveEffectValue(data, c, 50)).toBe(15)
    })

    test("an effect that starts above level 1 is absent below its first threshold", () => {
        const c = curve({ 30: 7 })
        expect(resolveEffectValue(data, c, 29)).toBe(-1)
        expect(resolveEffectValue(data, c, 30)).toBe(7)
    })

    test("limit break maps to the level cap the game states, in both directions", () => {
        expect(levelCapFor(data, "SSR", 0)).toBe(30)
        expect(levelCapFor(data, "SSR", 4)).toBe(50)
        expect(levelCapFor(data, "SR", 4)).toBe(45)
        expect(limitBreakForLevelCap(data, "SSR", 45)).toBe(3)
        expect(limitBreakForLevelCap(data, "SSR", 44)).toBeNull()
    })

    test("the parser rejects a wrong schema, a wrong version and a malformed curve", () => {
        expect(() => parseSupportCardData({ schema: "nope", schemaVersion: 2 })).toThrow(SupportCardDataError)
        expect(() => parseSupportCardData({ schema: SUPPORT_CARD_SCHEMA, schemaVersion: 99 })).toThrow(/schemaVersion/)
        expect(() =>
            makeData([{ id: 1, charaId: 1, title: "x", supportType: "Speed", effects: [{ type: 1, curve: [1, 2] }] }], { "1": "x" }),
        ).toThrow(/curve entries/)
    })

    test("a unique perk is dormant below its unlock level and never contributes there", () => {
        const card = makeData([{ id: 30001, charaId: 1001, title: "A", supportType: "Speed", uniqueEffect: { unlockLevel: 40, description: "Training Effectiveness (Friendship Gauge 80+)", effects: [{ type: 8, value: 20 }], undecodedTypes: [] } }], { "1001": "Alpha" }).cards[0]
        expect(resolveUniqueEffect(card, 39)?.unlocked).toBe(false)
        expect(resolveUniqueEffect(card, 39)?.effects).toEqual([])
        expect(resolveUniqueEffect(card, 40)?.effects).toEqual([{ type: 8, value: 20 }])
    })

    test("a unique perk adds to the base effect of the same type", () => {
        const d = makeData([{ id: 30001, charaId: 1001, title: "A", supportType: "Speed", effects: [{ type: 8, curve: curve({ 1: 5 }) }], uniqueEffect: { unlockLevel: 40, description: null, effects: [{ type: 8, value: 20 }], undecodedTypes: [] } }], { "1001": "Alpha" })
        expect(resolveTotalEffects(d, d.cards[0], 39).effects.get(8)).toBe(5)
        expect(resolveTotalEffects(d, d.cards[0], 40).effects.get(8)).toBe(25)
    })
})

describe("owned inventory resolution", () => {
    const data = makeData(
        [
            { id: 30001, charaId: 1001, title: "First Light", supportType: "Speed" },
            { id: 30002, charaId: 1002, title: "Second Wind", supportType: "Stamina" },
            { id: 20001, charaId: 1003, title: "Third Rail", rarity: "SR", supportType: "Power" },
            { id: 10001, charaId: 1004, title: null as unknown as string, rarity: "R", supportType: "Guts" },
        ],
        { "1001": "Alpha", "1002": "Beta", "1003": "Gamma", "1004": "Delta" },
    )
    const index = buildSupportCardIndex(data)

    test("resolves on character and title, ignoring case, punctuation and markup", () => {
        const inv = buildOwnedInventory({ cards: [ownedRow("alpha", "first   light!!")] }, index, { evidenceSource: "test", claimsCompleteAccount: true })
        expect(inv.unresolved).toEqual([])
        expect(inv.cards[0].card.supportCardId).toBe(30001)
        expect(inv.cards[0].matchMethod).toBe("CHARACTER_AND_TITLE")
    })

    test("falls back to title alone when the character name is not one the game lists, and says so", () => {
        const inv = buildOwnedInventory({ cards: [ownedRow("Some Team Name", "Second Wind")] }, index, { evidenceSource: "test", claimsCompleteAccount: true })
        expect(inv.cards[0].card.supportCardId).toBe(30002)
        expect(inv.cards[0].matchMethod).toBe("TITLE_ONLY")
        expect(inv.cards[0].warnings).toContain("MATCHED_WITHOUT_CHARACTER")
    })

    test("refuses a row that matches nothing rather than guessing", () => {
        const inv = buildOwnedInventory({ cards: [ownedRow("Nobody", "No Such Card")] }, index, { evidenceSource: "test", claimsCompleteAccount: true })
        expect(inv.cards).toEqual([])
        expect(inv.unresolved[0].reason).toBe("NO_CATALOGUE_MATCH")
    })

    test("refuses a row whose stated rarity or support type contradicts the catalogue", () => {
        const rarity = buildOwnedInventory({ cards: [ownedRow("Alpha", "First Light", { rarity: "SR" })] }, index, { evidenceSource: "test", claimsCompleteAccount: true })
        expect(rarity.unresolved[0].reason).toBe("RARITY_CONFLICT")
        const type = buildOwnedInventory({ cards: [ownedRow("Alpha", "First Light", { support_type: "Wit" })] }, index, { evidenceSource: "test", claimsCompleteAccount: true })
        expect(type.unresolved[0].reason).toBe("SUPPORT_TYPE_CONFLICT")
    })

    test("keeps a stated limit break that disagrees with the stated cap, and flags the disagreement", () => {
        const inv = buildOwnedInventory({ cards: [ownedRow("Alpha", "First Light", { limit_break_index: 2, level_cap: 50, current_level: 40 })] }, index, { evidenceSource: "test", claimsCompleteAccount: true })
        expect(inv.cards[0].limitBreak).toBe(2)
        expect(inv.cards[0].levelCap).toBe(40)
        expect(inv.cards[0].warnings).toContain("LIMIT_BREAK_CAP_DISAGREEMENT")
    })

    test("a limit break is not a level: headroom is reported, not assumed away", () => {
        const inv = buildOwnedInventory({ cards: [ownedRow("Alpha", "First Light", { limit_break_index: 4, current_level: 35 })] }, index, { evidenceSource: "test", claimsCompleteAccount: true })
        expect(inv.cards[0].levelCap).toBe(50)
        expect(inv.cards[0].level).toBe(35)
        expect(inv.cards[0].unlevelledHeadroom).toBe(15)
    })

    test("a level above the cap is clamped and flagged rather than silently trusted", () => {
        const inv = buildOwnedInventory({ cards: [ownedRow("Alpha", "First Light", { limit_break_index: 0, current_level: 50 })] }, index, { evidenceSource: "test", claimsCompleteAccount: true })
        expect(inv.cards[0].level).toBe(30)
        expect(inv.cards[0].warnings).toContain("LEVEL_ABOVE_CAP")
    })

    test("account-wide claims need both a clean resolution and an explicit completeness assertion", () => {
        const clean = buildOwnedInventory({ cards: [ownedRow("Alpha", "First Light")] }, index, { evidenceSource: "test", claimsCompleteAccount: true })
        expect(assessInventory(clean, index).trustedForAccountClaims).toBe(true)

        const notAsserted = buildOwnedInventory({ cards: [ownedRow("Alpha", "First Light")] }, index, { evidenceSource: "test", claimsCompleteAccount: false })
        expect(assessInventory(notAsserted, index).trustedForAccountClaims).toBe(false)

        const dirty = buildOwnedInventory({ cards: [ownedRow("Alpha", "First Light"), ownedRow("Nobody", "No Such Card")] }, index, { evidenceSource: "test", claimsCompleteAccount: true })
        expect(assessInventory(dirty, index).trustedForAccountClaims).toBe(false)
    })

    test("a fixture inventory never claims to be an account", () => {
        const fixture = buildFixtureInventory(index)
        expect(fixture.claimsCompleteAccount).toBe(false)
        expect(assessInventory(fixture, index).trustedForAccountClaims).toBe(false)
    })
})

describe("limit break and level change what a card is worth", () => {
    // One card whose Friendship Bonus only reaches its top value at level 50, which only a fully
    // limit-broken SSR can reach, plus a unique perk gated at 45.
    const data = makeData(
        [
            {
                id: 30001,
                charaId: 1001,
                title: "Late Bloomer",
                supportType: "Speed",
                effects: [{ type: 1, curve: curve({ 1: 5, 30: 15, 50: 40 }) }],
                uniqueEffect: { unlockLevel: 50, description: "big", effects: [{ type: 8, value: 20 }], undecodedTypes: [] },
            },
            { id: 20001, charaId: 1002, title: "Steady SR", rarity: "SR", supportType: "Speed", effects: [{ type: 1, curve: curve({ 1: 20, 45: 35 }) }] },
        ],
        { "1001": "Alpha", "1002": "Beta" },
    )
    const index = buildSupportCardIndex(data)
    const build = buildDeckTarget({ scenario: "Grand Concert", distance: "mile" }, index)

    const at = (id: number, lb: number) => {
        const rarity = index.byId.get(id)!.rarity
        const cap = levelCapFor(data, rarity, lb)
        return valueCard(index, { supportCardId: id, level: cap, levelCap: cap, limitBreak: lb, borrowed: false, owned: true }, build)
    }

    test("the same card at a higher limit break crosses a threshold it cannot reach at LB0", () => {
        expect(at(30001, 0).dimensions.friendshipBonus).toBe(15)
        expect(at(30001, 3).dimensions.friendshipBonus).toBe(15)
        expect(at(30001, 4).dimensions.friendshipBonus).toBe(40)
        expect(at(30001, 4).composite).toBeGreaterThan(at(30001, 0).composite)
    })

    test("a unique perk stays dormant at LB3 and switches on at MLB", () => {
        expect(at(30001, 3).limitBreakState.uniqueUnlocked).toBe(false)
        expect(at(30001, 4).limitBreakState.uniqueUnlocked).toBe(true)
        expect(at(30001, 4).dimensions.trainingEffectiveness).toBe(20)
    })

    test("rarity alone never decides the winner: an MLB SR beats an unbroken SSR", () => {
        const mlbSR = at(20001, 4)
        const lb0SSR = at(30001, 0)
        expect(mlbSR.card.rarity).toBe("SR")
        expect(lb0SSR.card.rarity).toBe("SSR")
        expect(mlbSR.composite).toBeGreaterThan(lb0SSR.composite)
        // And the ordering flips once the SSR is fully broken, so this is about state, not about SR.
        expect(at(30001, 4).composite).toBeGreaterThan(mlbSR.composite)
    })

    test("levelling a card that is already limit-broken is reported as available value", () => {
        const under = valueCard(index, { supportCardId: 30001, level: 30, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, build)
        expect(under.limitBreakState.unlevelledHeadroom).toBe(20)
        expect(under.limitBreakState.compositeFromLevelling).toBeGreaterThan(0)
        expect(under.limitBreakState.uniqueUnlocked).toBe(false)
    })
})

describe("the target changes which cards win", () => {
    const data = makeData(
        [
            { id: 30001, charaId: 1001, title: "Speedy", supportType: "Speed", effects: [{ type: 1, curve: curve({ 1: 25 }) }] },
            { id: 30002, charaId: 1002, title: "Stayer", supportType: "Stamina", effects: [{ type: 1, curve: curve({ 1: 25 }) }] },
        ],
        { "1001": "Alpha", "1002": "Beta" },
    )
    const index = buildSupportCardIndex(data)

    const rank = (distance: "mile" | "long") => {
        const build = buildDeckTarget({ scenario: "Grand Concert", distance }, index)
        return [30001, 30002]
            .map((id) => valueCard(index, { supportCardId: id, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, build))
            .sort((a, b) => targetWeightedComposite(b, build) - targetWeightedComposite(a, build))
            .map((p) => p.card.supportType)
    }

    test("two cards with identical effect tables rank differently for a Mile target and a Long one", () => {
        expect(rank("mile")[0]).toBe("Speed")
        expect(rank("long")[0]).toBe("Stamina")
    })

    test("an operator stat priority replaces the editorial default and is labelled as operator-set", () => {
        const editorial = buildDeckTarget({ distance: "long" }, index)
        expect(editorial.statPriorityOrigin).toBe("DEFAULT_BY_DISTANCE")
        expect(editorial.gaps).toContain("STAT_PRIORITY_IS_EDITORIAL")

        const operator = buildDeckTarget({ distance: "long", statPriority: ["Wit", "Guts"] }, index)
        expect(operator.statPriorityOrigin).toBe("OPERATOR")
        expect(operator.statPriority).toEqual(["Wit", "Guts"])
        expect(operator.gaps).not.toContain("STAT_PRIORITY_IS_EDITORIAL")
    })

    test("stat weights descend with priority and never reach zero for an unnamed stat", () => {
        const weights = statWeightsFor(["Speed", "Stamina"])
        expect(weights.Speed).toBe(1)
        expect(weights.Stamina).toBeLessThan(1)
        expect(weights.Guts).toBeGreaterThan(0)
    })
})

describe("the scenario changes which cards are worth anything", () => {
    const data = makeData(
        [
            { id: 30001, charaId: 1002, title: "Concert Star", supportType: "Speed" },
            { id: 30002, charaId: 1003, title: "Banned Elsewhere", supportType: "Speed", restrictedScenarioIds: [3] },
        ],
        { "1002": "Suzuka", "1003": "Sirius" },
    )
    const index = buildSupportCardIndex(data)

    test("a scenario card is recognised only in its own scenario, and never as a fabricated number", () => {
        const gc = buildDeckTarget({ scenario: "Grand Concert" }, index)
        const tb = buildDeckTarget({ scenario: "Trackblazer" }, index)
        const inGc = valueCard(index, { supportCardId: 30001, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, gc)
        const inTb = valueCard(index, { supportCardId: 30001, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, tb)

        expect(inGc.scenarioFit.scenarioSpecialCharacter).toBe(true)
        expect(inTb.scenarioFit.scenarioSpecialCharacter).toBe(false)
        // The recognition is decoded; its magnitude is not, so the composite must be untouched by it.
        expect(inGc.composite).toBe(inTb.composite)
        expect(inGc.unknownMechanics.join(" ")).toMatch(/how much that is worth is not/)
    })

    test("a scenario restriction is decoded and makes the card illegal there but not elsewhere", () => {
        const gc = buildDeckTarget({ scenario: "Grand Concert" }, index)
        const tb = buildDeckTarget({ scenario: "Trackblazer" }, index)
        expect(valueCard(index, { supportCardId: 30002, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, gc).scenarioFit.legal).toBe(false)
        expect(valueCard(index, { supportCardId: 30002, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, tb).scenarioFit.legal).toBe(true)
    })

    test("scenarios resolve by short name, shipped title and id alike", () => {
        expect(resolveScenario(index, "Grand Concert")?.id).toBe(3)
        expect(resolveScenario(index, "gc")?.id).toBe(3)
        expect(resolveScenario(index, 4)?.id).toBe(4)
        expect(resolveScenario(index, "not a scenario")).toBeNull()
    })

    test("a scenario the catalogue does not carry is reported as a gap rather than ignored", () => {
        expect(buildDeckTarget({ scenario: "Nonexistent Cup" }, index).gaps).toContain("SCENARIO_NOT_IN_CATALOGUE")
    })
})

describe("deck legality", () => {
    const data = makeData(
        [
            { id: 30001, charaId: 1001, title: "A1", supportType: "Speed" },
            { id: 30002, charaId: 1001, title: "A2", supportType: "Wit" },
            { id: 30003, charaId: 1003, title: "C", supportType: "Power" },
            { id: 30004, charaId: 1004, title: "D", supportType: "Guts" },
            { id: 30005, charaId: 1005, title: "E", supportType: "Stamina" },
            { id: 30006, charaId: 1006, title: "F", supportType: "Friend" },
            { id: 30007, charaId: 1007, title: "G", supportType: "Speed" },
        ],
        { "1001": "Alpha", "1003": "Gamma", "1004": "Delta", "1005": "Epsilon", "1006": "Zeta", "1007": "Eta" },
    )
    const index = buildSupportCardIndex(data)
    const build = buildDeckTarget({ trainee: "Gamma", scenario: "Grand Concert", distance: "mile" }, index)
    const profile = (id: number, borrowed = false) => valueCard(index, { supportCardId: id, level: 50, levelCap: 50, limitBreak: 4, borrowed, owned: true }, build)

    test("two cards of the same character are illegal even when their types differ", () => {
        const deck = [30001, 30002, 30004, 30005, 30006, 30007].map((id) => profile(id))
        const legality = checkDeckLegality(deck, build)
        expect(legality.legal).toBe(false)
        expect(legality.violations.map((v) => v.violation)).toContain("DUPLICATE_CHARACTER")
    })

    test("the trainee's own card is locked out of her own deck", () => {
        const deck = [30001, 30003, 30004, 30005, 30006, 30007].map((id) => profile(id))
        const legality = checkDeckLegality(deck, build)
        expect(legality.violations.map((v) => v.violation)).toContain("TRAINEE_CHARACTER_IN_DECK")
    })

    test("a deck holds six cards and at most one borrowed", () => {
        const five = [30001, 30004, 30005, 30006, 30007].map((id) => profile(id))
        expect(checkDeckLegality(five, build).violations.map((v) => v.violation)).toContain("WRONG_SIZE")

        const twoBorrowed = [profile(30001, true), profile(30004, true), profile(30005), profile(30006), profile(30007), profile(30002)]
        expect(checkDeckLegality(twoBorrowed, build).violations.map((v) => v.violation)).toContain("TOO_MANY_BORROWED")
    })

    test("each rule says whether it was decoded or is a recorded game rule", () => {
        const deck = [30001, 30002, 30004, 30005, 30006, 30007].map((id) => profile(id))
        const sources = new Map(checkDeckLegality(deck, build).violations.map((v) => [v.violation, v.source]))
        expect(sources.get("DUPLICATE_CHARACTER")).toBe("KNOWN_GAME_RULE")
        expect(DECK_SIZE).toBe(6)
    })
})

describe("a deck is more than the sum of six cards", () => {
    // Six strong Speed cards against a spread of one card per type, all with identical effect tables,
    // so the only thing that can separate them is the cross-card modelling.
    const spec: CardSpec[] = []
    const characters: Record<string, string> = {}
    const TYPES = ["Speed", "Stamina", "Power", "Guts", "Wit", "Friend"]
    for (let i = 0; i < 6; i++) {
        spec.push({ id: 30001 + i, charaId: 1001 + i, title: `S${i}`, supportType: "Speed", effects: [{ type: 1, curve: curve({ 1: 25 }) }], hintSkillIds: [900, 901] })
        characters[String(1001 + i)] = `Speedy ${i}`
    }
    TYPES.forEach((type, i) => {
        spec.push({ id: 30101 + i, charaId: 1101 + i, title: `M${i}`, supportType: type, effects: [{ type: 1, curve: curve({ 1: 25 }) }], hintSkillIds: [910 + i, 920 + i] })
        characters[String(1101 + i)] = `Mixed ${i}`
    })
    const index = buildSupportCardIndex(makeData(spec, characters))
    const build = buildDeckTarget({ scenario: "Grand Concert", distance: "medium" }, index)
    const profile = (id: number) => valueCard(index, { supportCardId: id, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, build)

    const allSpeed = [30001, 30002, 30003, 30004, 30005, 30006].map(profile)
    const spread = [30101, 30102, 30103, 30104, 30105, 30106].map(profile)

    test("six individually identical cards of one type lose to a legal deck that spreads across types", () => {
        const stacked = scoreDeck(index, allSpeed, build)
        const balanced = scoreDeck(index, spread, build)
        expect(stacked.legality.legal).toBe(true)
        expect(balanced.legality.legal).toBe(true)
        expect(balanced.dimensions.targetStatCoverage).toBeGreaterThan(stacked.dimensions.targetStatCoverage)
        expect(balanced.dimensions.trainingTypeBalance).toBeGreaterThan(stacked.dimensions.trainingTypeBalance)
    })

    test("the deck composite is a deck-level figure, so stacking one type cannot win it", () => {
        const stacked = scoreDeck(index, allSpeed, build)
        const balanced = scoreDeck(index, spread, build)
        // The first version summed the six card composites, which made the all-Speed deck the highest
        // scoring deck on the account. The composite has to see the crowding for BEST_BALANCED to mean
        // anything, so it is built from the deck dimensions instead.
        expect(balanced.composite).toBeGreaterThan(stacked.composite)
        const naiveSum = allSpeed.reduce((sum, c) => sum + c.composite, 0)
        expect(stacked.composite).toBeLessThan(naiveSum)
    })

    test("same-type value aggregates concavely, so the fifth card of a type adds less than the first", () => {
        expect(concaveSum([10])).toBe(10)
        expect(concaveSum([10, 10])).toBeLessThan(20)
        const one = concaveSum([10])
        const two = concaveSum([10, 10])
        const three = concaveSum([10, 10, 10])
        expect(two - one).toBeGreaterThan(three - two)
    })

    test("cards that hint the same skills cover fewer skills together, and the redundancy is reported", () => {
        const stacked = scoreDeck(index, allSpeed, build)
        const balanced = scoreDeck(index, spread, build)
        expect(stacked.dimensions.hintCoverage).toBe(2)
        expect(balanced.dimensions.hintCoverage).toBe(12)
        expect(stacked.dimensions.redundancy).toBeGreaterThan(balanced.dimensions.redundancy)
    })
})

describe("the borrow slot takes a slot rather than adding one", () => {
    const spec: CardSpec[] = []
    const characters: Record<string, string> = {}
    // Five good cards and one deliberately weak one, so the weak slot is the obvious thing to displace.
    const strengths = [30, 28, 26, 24, 22, 2]
    const types = ["Speed", "Stamina", "Power", "Guts", "Wit", "Friend"]
    strengths.forEach((strength, i) => {
        spec.push({ id: 30001 + i, charaId: 1001 + i, title: `Owned${i}`, supportType: types[i], effects: [{ type: 1, curve: curve({ 1: strength }) }] })
        characters[String(1001 + i)] = `Owner ${i}`
    })
    spec.push({ id: 30900, charaId: 1900, title: "Borrowable", supportType: "Friend", effects: [{ type: 1, curve: curve({ 1: 45 }) }] })
    characters["1900"] = "Lender"
    const index = buildSupportCardIndex(makeData(spec, characters))

    const inventory = buildOwnedInventory(
        { cards: strengths.map((_, i) => ownedRow(`Owner ${i}`, `Owned${i}`, { current_level: 50, level_cap: 50 })) },
        index,
        { evidenceSource: "test", claimsCompleteAccount: true },
    )
    const borrowPool = buildOwnedInventory({ cards: [ownedRow("Lender", "Borrowable", { current_level: 50, level_cap: 50 })] }, index, { evidenceSource: "test", claimsCompleteAccount: false })
    const build = buildDeckTarget({ scenario: "Grand Concert", distance: "medium" }, index)

    test("a borrow displaces the weakest marginal card, not whatever sits in the last slot", () => {
        const result = searchDecks(index, inventory, build, { borrowCandidates: borrowPool.cards })
        const best = result.borrowOptions[0]
        expect(best).toBeDefined()
        expect(best.borrowed.card.displayName).toContain("Borrowable")
        expect(best.displaced?.card.displayName).toContain("Owned5")
        expect(best.improvement).toBeGreaterThan(0)
        expect(best.deck.cards).toHaveLength(DECK_SIZE)
    })

    test("the no-borrow deck stays available and is reported as its own archetype", () => {
        const result = searchDecks(index, inventory, build, { borrowCandidates: borrowPool.cards })
        expect(result.bestNoBorrow).not.toBeNull()
        expect(result.bestNoBorrow!.cards.every((c) => !c.borrowed)).toBe(true)
        expect(result.archetypes.map((a) => a.archetype)).toContain("BEST_NO_BORROW")
    })

    test("--no-borrow suppresses borrow analysis entirely", () => {
        const result = searchDecks(index, inventory, build, { borrowCandidates: borrowPool.cards, noBorrow: true })
        expect(result.borrowOptions).toEqual([])
        expect(result.archetypes.map((a) => a.archetype)).not.toContain("BEST_BORROW_UPGRADE")
    })

    test("borrow value is stated against the no-borrow baseline, with the displaced card as its cost", () => {
        const result = searchDecks(index, inventory, build, { borrowCandidates: borrowPool.cards })
        const best = result.borrowOptions[0]
        expect(best.deck.dimensions.borrowValue).toBeCloseTo(best.improvement, 4)
        expect(best.deck.dimensions.accountOpportunityCost).toBeCloseTo(best.displaced!.composite, 4)
    })
})

describe("uncertainty is carried, never converted into a number", () => {
    const data = makeData(
        [
            { id: 30001, charaId: 1001, title: "Conditional", supportType: "Speed", uniqueEffect: { unlockLevel: 40, description: "Training Effectiveness (Friendship Gauge 80+)", effects: [], undecodedTypes: [101] } },
            // charaId 1003 on purpose: 1002 is a Grand Concert special character in this fixture, which
            // is itself an uncounted mechanic and would make the "plain" card MEDIUM for that reason.
            { id: 30002, charaId: 1003, title: "Plain", supportType: "Speed", hintSkillIds: [900] },
        ],
        { "1001": "Alpha", "1002": "Beta", "1003": "Gamma" },
    )
    const index = buildSupportCardIndex(data)
    const build = buildDeckTarget({ scenario: "Grand Concert", distance: "mile" }, index)

    test("an undecoded unique perk lowers confidence and adds no value", () => {
        const conditional = valueCard(index, { supportCardId: 30001, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, build)
        const plain = valueCard(index, { supportCardId: 30002, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, build)
        expect(conditional.composite).toBe(plain.composite)
        expect(conditional.confidence).toBe("MEDIUM")
        expect(conditional.unknownMechanics.join(" ")).toMatch(/has not decoded/)
        // The game's own wording is carried so the perk can be named rather than only counted.
        expect(conditional.uniqueDescription).toBe("Training Effectiveness (Friendship Gauge 80+)")
    })

    test("an inventory warning drags a card's confidence down", () => {
        const shaky = valueCard(index, { supportCardId: 30002, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true, inventoryWarnings: ["LIMIT_BREAK_CAP_DISAGREEMENT"] }, build)
        expect(shaky.confidence).toBe("LOW")
    })

    test("a deck is only as confident as its least confident card", () => {
        const good = valueCard(index, { supportCardId: 30002, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, build)
        const bad = valueCard(index, { supportCardId: 30001, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, build)
        expect(scoreDeck(index, [good], build, { requireFullSize: false }).confidence).toBe("HIGH")
        expect(scoreDeck(index, [good, bad], build, { requireFullSize: false }).confidence).toBe("MEDIUM")
    })

    test("the composite is always labelled as editorial", () => {
        const profile = valueCard(index, { supportCardId: 30002, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, build)
        expect(profile.compositeOrigin).toBe("EDITORIAL_WEIGHTS")
        expect(compositeOf(profile.dimensions)).toBe(profile.composite)
        expect(Object.keys(EDITORIAL_DIMENSION_WEIGHTS).length).toBeGreaterThan(0)
    })
})

describe("the community prior stays a prior", () => {
    const data = makeData([{ id: 30001, charaId: 1001, title: "First Light", supportType: "Speed" }], { "1001": "Alpha" })
    const index = buildSupportCardIndex(data)

    const snapshot = {
        schema: COMMUNITY_PRIOR_SCHEMA,
        schemaVersion: COMMUNITY_PRIOR_SCHEMA_VERSION,
        sourceName: "test sheet",
        sourceUrl: "https://example.invalid/sheet",
        capturedOn: "2026-08-22",
        provenance: "MANUAL_EXPORT",
        entries: [
            { character: "Alpha", title: "First Light", tier: "S", rank: 1 },
            { character: "Nobody", title: "Ghost Card", tier: "A", rank: 2 },
        ],
    }

    test("rows resolve onto catalogue cards and unresolvable rows are kept, not dropped", () => {
        const prior = buildCommunityPriorIndex(parseCommunityPrior(snapshot, index))
        expect(prior.resolved).toBe(1)
        expect(prior.unresolved).toBe(1)
        expect(prior.byCardId.get(30001)?.tier).toBe("S")
    })

    test("a prior never changes a decoded value or a composite", () => {
        const build = buildDeckTarget({ scenario: "Grand Concert", distance: "mile" }, index)
        const profile = valueCard(index, { supportCardId: 30001, level: 50, levelCap: 50, limitBreak: 4, borrowed: false, owned: true }, build)
        const prior = buildCommunityPriorIndex(parseCommunityPrior(snapshot, index))
        // There is no code path from the prior into a value: the profile type has no field for it.
        expect(Object.keys(profile)).not.toContain("communityPrior")
        expect(prior.snapshot.provenance).toBe("MANUAL_EXPORT")
    })

    test("a prior with the wrong schema is rejected rather than partly read", () => {
        expect(() => parseCommunityPrior({ schema: "other", schemaVersion: 1, entries: [] }, index)).toThrow(CommunityPriorError)
    })
})

describe("the telemetry contract", () => {
    const slot = { supportCardId: 30001, level: 50, limitBreak: 4, borrowed: false }

    test("an observation records the deck at the levels it ran at", () => {
        const observation = buildDeckObservation({ careerToken: "c1", deck: [slot, { ...slot, supportCardId: 30002, level: 35 }] })
        expect(observation.deck.map((s) => s.level)).toEqual([50, 35])
    })

    test("missing outcome fields are listed rather than defaulted to zero", () => {
        const observation = buildDeckObservation({ careerToken: "c1", deck: [slot] })
        expect(observation.skillPoints).toBeNull()
        expect(observation.missingFields).toContain("skillPoints")
        expect(observation.missingFields).toContain("outcome")
    })

    test("an observation with nothing to join on or nothing to attribute is refused", () => {
        expect(() => buildDeckObservation({ careerToken: "", deck: [slot] })).toThrow(DeckObservationError)
        expect(() => buildDeckObservation({ careerToken: "c1", deck: [] })).toThrow(DeckObservationError)
    })

    test("a corpus this small is not ready to learn from, and says why", () => {
        const readiness = assessCorpus([buildDeckObservation({ careerToken: "c1", deck: [slot] })])
        expect(readiness.readyForBaseline).toBe(false)
        expect(readiness.blockers.join(" ")).toMatch(new RegExp(String(MIN_OBSERVATIONS_FOR_BASELINE)))
        expect(readiness.blockers.join(" ")).toMatch(/same deck/)
    })
})

describe("the report explains itself and is deterministic", () => {
    const spec: CardSpec[] = []
    const characters: Record<string, string> = {}
    const types = ["Speed", "Stamina", "Power", "Guts", "Wit", "Friend", "Speed", "Stamina"]
    types.forEach((type, i) => {
        spec.push({
            id: 30001 + i,
            charaId: 1001 + i,
            title: `Card${i}`,
            supportType: type,
            effects: [
                { type: 1, curve: curve({ 1: 20 + i }) },
                { type: 14, curve: curve({ 1: 10 + i }) },
            ],
            hintSkillIds: [800 + i, 810 + i],
        })
        characters[String(1001 + i)] = `Owner ${i}`
    })
    const index = buildSupportCardIndex(makeData(spec, characters))
    const inventory = buildOwnedInventory({ cards: types.map((_, i) => ownedRow(`Owner ${i}`, `Card${i}`, { current_level: 50, level_cap: 50 })) }, index, {
        evidenceSource: "test",
        claimsCompleteAccount: true,
    })
    const build = buildDeckTarget({ scenario: "Grand Concert", distance: "medium" }, index)

    const run = () =>
        buildDeckLabReport({
            index,
            inventory,
            completeness: assessInventory(inventory, index),
            isFixture: false,
            results: [searchDecks(index, inventory, build, { borrowCandidates: [] })],
            prior: null,
        })

    test("the same inputs produce a byte-identical report", () => {
        expect(JSON.stringify(run())).toBe(JSON.stringify(run()))
    })

    test("every card in the recommended deck carries reasons, fit and its own decoded effects", () => {
        const target = run().targets[0]
        expect(target.recommended).not.toBeNull()
        for (const card of target.recommended!.deck.cards) {
            expect(card.whyIncluded.length).toBeGreaterThan(0)
            expect(card.targetFit).toBeTruthy()
            expect(card.scenarioFit).toBeTruthy()
            expect(card.limitBreakImpact).toMatch(/limit break/)
            expect(Object.keys(card.decodedEffects).length).toBeGreaterThan(0)
        }
    })

    test("the report says what the search did not cover and that the composite is editorial", () => {
        const report = run()
        expect(report.caveats.join(" ")).toMatch(/editorial/)
        expect(report.caveats.join(" ")).toMatch(/known game rules/)
        expect(report.targets[0].searchCompleteness.combinationsEvaluated).toBeGreaterThan(0)
        expect(report.targets[0].recommended!.deck.compositeOrigin).toMatch(/editorial/)
    })

    test("a fixture-backed report says so in its caveats", () => {
        const fixtureReport = buildDeckLabReport({
            index,
            inventory,
            completeness: assessInventory(inventory, index),
            isFixture: true,
            results: [searchDecks(index, inventory, build, { borrowCandidates: [] })],
            prior: null,
        })
        expect(fixtureReport.caveats.join(" ")).toMatch(/FIXTURE INVENTORY/)
    })

    test("a single recommendation is only labelled dominant when one deck is alone on the frontier", () => {
        const target = run().targets[0]
        if (!target.recommendedIsDominant) expect(target.recommended!.archetype).toBe("BEST_BALANCED")
    })
})

describe("the shipped catalogue", () => {
    test("parses, indexes, and carries the four playable scenarios", () => {
        const raw = JSON.parse(readFileSync(join(__dirname, "..", "..", "..", "data", "support_cards.json"), "utf8"))
        const data = parseSupportCardData(raw)
        const index = buildSupportCardIndex(data)
        expect(data.cards.length).toBeGreaterThan(200)
        expect(data.scenarios.map((s) => s.id)).toEqual([1, 2, 3, 4])
        expect(resolveScenario(index, "Grand Concert")?.id).toBe(3)
        // The Grand Concert scenario names its own characters, which is what scenario awareness reads.
        expect(index.scenarioSpecialCharaIds(3).size).toBeGreaterThan(0)
        // Trackblazer names none, and an empty set is a real answer rather than a missing one.
        expect(index.scenarioSpecialCharaIds(4).size).toBe(0)
    })

    test("a hypothetical borrow pool is SSR-only and fully limit-broken by construction", () => {
        const raw = JSON.parse(readFileSync(join(__dirname, "..", "..", "..", "data", "support_cards.json"), "utf8"))
        const index = buildSupportCardIndex(parseSupportCardData(raw))
        const pool = hypotheticalBorrowPool(index)
        expect(pool.length).toBeGreaterThan(0)
        expect(pool.every((c) => c.card.rarity === "SSR")).toBe(true)
        expect(pool.every((c) => c.limitBreak === 4 && c.level === c.levelCap)).toBe(true)
    })
})
