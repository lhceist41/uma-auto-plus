import { buildSmartBorrowIntent, intentEvidenceDigest, serializeSmartBorrowIntent, SmartBorrowIntentError, SMART_BORROW_INTENT_SCHEMA } from "../smartBorrowIntent.ts"
import { parseBorrowScanJsonl } from "../borrowScanImport.ts"
import { resolveBorrowPool, type BorrowPoolResolution } from "../borrowPool.ts"
import { buildSupportCardIndex, parseSupportCardData, SUPPORT_CARD_SCHEMA, SUPPORT_CARD_SCHEMA_VERSION, type SupportCardData } from "../supportCardData.ts"

// A synthetic catalogue so a test states the mechanic it proves rather than depending on which cards
// the live game ships. Mirrors borrowScanImport.test.ts.

const THRESHOLDS = [1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50]

function curve(points: Record<number, number>): number[] {
    return THRESHOLDS.map((level) => (points[level] === undefined ? -1 : points[level]))
}

function makeData(): SupportCardData {
    return parseSupportCardData({
        schema: SUPPORT_CARD_SCHEMA,
        schemaVersion: SUPPORT_CARD_SCHEMA_VERSION,
        source: "synthetic smart-borrow-intent fixture",
        effectTypes: { "1": "Friendship Bonus", "3": "Speed Bonus" },
        undecodedUniqueEffectTypeFloor: 100,
        effectLevelThresholds: THRESHOLDS,
        levelCapsByRarity: { R: [20, 25, 30, 35, 40], SR: [25, 30, 35, 40, 45], SSR: [30, 35, 40, 45, 50] },
        scenarios: [{ id: 3, name: "Grand Concert", statCapBonus: { Speed: 400, Stamina: 100, Power: 100, Guts: 300, Wit: 100 }, specialCharaIds: [], restrictedCardIds: [] }],
        characters: { "1001": "Kitasan Black", "1002": "Bravo" },
        cards: [
            { id: 30001, charaId: 1001, title: "Fire at My Heels", rarity: "SSR", supportType: "Speed", effects: [{ type: 3, curve: curve({ 1: 5, 50: 40 }) }], uniqueEffect: null, hintSkillIds: [], groupMemberCharaIds: null, restrictedScenarioIds: [] },
            { id: 30002, charaId: 1002, title: "Stamina Star", rarity: "SSR", supportType: "Stamina", effects: [{ type: 1, curve: curve({ 1: 10 }) }], uniqueEffect: null, hintSkillIds: [], groupMemberCharaIds: null, restrictedScenarioIds: [] },
        ],
    })
}

const index = buildSupportCardIndex(makeData())

function row(fields: Record<string, unknown>): string {
    return JSON.stringify({ record: "borrow_pool_row", scan_id: "bp-live", source_type: "FOLLOW", confidence: "high", ...fields })
}

function header(fields: Record<string, unknown> = {}): string {
    return JSON.stringify({ record: "borrow_pool_scan", schema: "deck_lab_borrow_pool", scan_id: "bp-live", snapshot_termination: "UI_END_REACHED", completed_at: 123, ...fields })
}

/** A resolution with Kitasan Black [Fire at My Heels] at MLB (limit break 4, level 50, cap 50). */
function mlbResolution(): BorrowPoolResolution {
    const jsonl = [row({ character: "Kitasan Black", title: "Fire at My Heels", rarity: "SSR", level: 50, limit_break_index: 4, owner_alias: "owner-11112222" }), header()].join("\n")
    return resolveBorrowPool(parseBorrowScanJsonl(jsonl), index)
}

describe("smart borrow intent", () => {
    test("builds the intent from a resolved candidate, carrying observed level and limit break", () => {
        const intent = buildSmartBorrowIntent(mlbResolution(), 30001, "Medium")
        expect(intent.schema).toBe(SMART_BORROW_INTENT_SCHEMA)
        expect(intent.supportCardId).toBe(30001)
        expect(intent.canonicalCharacter).toBe("Kitasan Black")
        expect(intent.canonicalTitle).toBe("Fire at My Heels")
        expect(intent.expectedLevel).toBe(50)
        expect(intent.expectedLimitBreak).toBe(4)
        expect(intent.sourceAlias).toBe("owner-11112222")
        expect(intent.sourceBorrowScanId).toBe("bp-live")
        expect(intent.targetProfile).toBe("Medium")
    })

    test("an unobserved limit break carries through as null, never an assumed value", () => {
        // A row with no limit_break_index and no level cap: the resolver values it at an assumed LB but
        // flags LIMIT_BREAK_UNKNOWN. The intent must not present the assumed value as observed.
        const jsonl = [row({ character: "Kitasan Black", title: "Fire at My Heels", rarity: "SSR", level: 30 }), header()].join("\n")
        const resolution = resolveBorrowPool(parseBorrowScanJsonl(jsonl), index)
        const intent = buildSmartBorrowIntent(resolution, 30001, "Mile")
        expect(intent.expectedLimitBreak).toBeNull()
        expect(intent.warnings).toContain("LIMIT_BREAK_UNKNOWN")
    })

    test("a supportCardId not in the resolution throws rather than emitting a locatable-by-nothing intent", () => {
        expect(() => buildSmartBorrowIntent(mlbResolution(), 39999, "Long")).toThrow(SmartBorrowIntentError)
    })

    test("the evidence digest is stable and changes when a load-bearing field changes", () => {
        const base = {
            targetProfile: "Medium",
            sourceBorrowScanId: "bp-live",
            supportCardId: 30001,
            canonicalCharacter: "Kitasan Black",
            canonicalTitle: "Fire at My Heels",
            expectedLevel: 50,
            expectedLimitBreak: 4,
        }
        const digest = intentEvidenceDigest(base)
        expect(digest).toMatch(/^djb2-[0-9a-f]{8}$/)
        expect(intentEvidenceDigest(base)).toBe(digest)
        expect(intentEvidenceDigest({ ...base, expectedLimitBreak: 3 })).not.toBe(digest)
        expect(intentEvidenceDigest({ ...base, supportCardId: 30002 })).not.toBe(digest)
        expect(intentEvidenceDigest({ ...base, targetProfile: "Mile" })).not.toBe(digest)
    })

    test("the built intent's digest matches an independent recomputation over its own fields", () => {
        const intent = buildSmartBorrowIntent(mlbResolution(), 30001, "Medium")
        expect(intent.recommendationEvidenceDigest).toBe(
            intentEvidenceDigest({
                targetProfile: intent.targetProfile,
                sourceBorrowScanId: intent.sourceBorrowScanId,
                supportCardId: intent.supportCardId,
                canonicalCharacter: intent.canonicalCharacter,
                canonicalTitle: intent.canonicalTitle,
                expectedLevel: intent.expectedLevel,
                expectedLimitBreak: intent.expectedLimitBreak,
            }),
        )
    })

    test("serialisation uses snake_case keys and never carries a raw owner name", () => {
        const json = serializeSmartBorrowIntent(buildSmartBorrowIntent(mlbResolution(), 30001, "Medium"))
        const parsed = JSON.parse(json)
        expect(parsed.support_card_id).toBe(30001)
        expect(parsed.canonical_character).toBe("Kitasan Black")
        expect(parsed.expected_limit_break).toBe(4)
        expect(parsed.source_alias).toBe("owner-11112222")
        expect(parsed.recommendation_evidence_digest).toMatch(/^djb2-[0-9a-f]{8}$/)
    })
})
