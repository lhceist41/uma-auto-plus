// Synthetic fixtures for the Joint Build Budget Planner tests.
//
// Everything here is invented on purpose. A test that depends on which cards the live game happens to
// ship stops testing the mechanic and starts testing the patch notes, so the numbers below are chosen
// to make one behaviour visible at a time, and they are round enough that a reader can do the
// arithmetic in their head and check the assertion.
//
// The one place the fixtures copy the real game is the SHAPE of the decoded tables: blue Sparks have a
// ten-rung ladder that pays both a stat and a cap, Power training pays secondary Stamina, growth
// percentages sum to thirty. A fixture that got those wrong would let a bug through.

import { valueCard, type CardValueProfile } from "../../deckLab/cardValue.ts"
import { buildDeckTarget, type DeckTargetBuild } from "../../deckLab/deckTarget.ts"
import { scoreDeck, type DeckScore } from "../../deckLab/deck.ts"
import { buildSupportCardIndex, parseSupportCardData, SUPPORT_CARD_SCHEMA, SUPPORT_CARD_SCHEMA_VERSION, type SupportCardData, type SupportCardIndex } from "../../deckLab/supportCardData.ts"
import type { SurvivalConstraint } from "../../raceSurvival/types.ts"
import { parseBorrowPoolSnapshot, resolveBorrowPool, type BorrowPoolResolution } from "../../deckLab/borrowPool.ts"
import { createBuildBudgetEvidence, type BuildBudgetEvidence } from "../evidence.ts"
import type { BudgetParentPair, BudgetStat, BudgetTrainee } from "../types.ts"

export const THRESHOLDS = [1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50]

/** A flat effect curve: the same value from level 1 up. Keeps a fixture's arithmetic checkable. */
export function flatCurve(value: number): number[] {
    return THRESHOLDS.map((_, i) => (i === 0 ? value : -1))
}

/** The real blue ladders, copied so a change to the shipped asset shows up as a test failure. */
export const BLUE_START_LADDER = [1, 4, 7, 10, 13, 16, 19, 22, 25, 28]
export const BLUE_CAP_LADDER = [1, 1, 1, 2, 2, 2, 3, 3, 3, 4]

const BLUE_GROUPS: readonly [number, BudgetStat][] = [
    [1, "Speed"],
    [2, "Stamina"],
    [3, "Power"],
    [4, "Guts"],
    [5, "Wit"],
]

function blueGroup(groupId: number, stat: BudgetStat) {
    return {
        groupId,
        factorType: 1,
        family: "stat",
        subfamily: "stat",
        canonicalName: stat,
        maxRarity: 3,
        factorIds: [groupId * 100 + 1, groupId * 100 + 2, groupId * 100 + 3],
        effects: [
            { targetType: groupId, kind: "START_STAT", stat, aptitude: null, levels: 10, value1: BLUE_START_LADDER, value2: new Array(10).fill(0) },
            { targetType: groupId + 60, kind: "MAX_STAT", stat, aptitude: null, levels: 10, value1: BLUE_CAP_LADDER, value2: new Array(10).fill(0) },
        ],
    }
}

/**
 * One white Spark that grants a skill, used for the recovery-through-inheritance route.
 *
 * Skill 9001 stands in for a gold recovery: the tests never assert anything about what it does in a
 * race, only about whether a build can reach it.
 */
const RECOVERY_SPARK_GROUP = {
    groupId: 20001,
    factorType: 4,
    family: "white",
    subfamily: "skill",
    canonicalName: "Deep Breaths",
    maxRarity: 3,
    factorIds: [2000101, 2000102, 2000103],
    effects: [{ targetType: 41, kind: "SKILL", stat: null, aptitude: null, levels: 5, value1: [9001, 9001, 9001, 9001, 9001], value2: [1, 2, 3, 4, 5] }],
}

/** A pink Spark, to prove the aptitude ladder is read off a two-rung ladder and not the blue one. */
const LONG_APTITUDE_GROUP = {
    groupId: 34,
    factorType: 2,
    family: "aptitude",
    subfamily: "aptitude",
    canonicalName: "Long",
    maxRarity: 3,
    factorIds: [3401, 3402, 3403],
    effects: [{ targetType: 34, kind: "APTITUDE", stat: null, aptitude: "Long", levels: 2, value1: [1, 2], value2: [0, 0] }],
}

/** Scenario 1 stands in for URA Finale, scenario 3 for Grand Concert with its smaller Power payout. */
const TRAINING_ROWS = [
    { scenarioId: 1, commandId: 101, trainingType: "Speed", stats: { Speed: 11, Stamina: 0, Power: 6, Guts: 0, Wit: 0 }, energy: -21, skillPoints: 4 },
    { scenarioId: 1, commandId: 102, trainingType: "Power", stats: { Speed: 0, Stamina: 6, Power: 9, Guts: 0, Wit: 0 }, energy: -20, skillPoints: 4 },
    { scenarioId: 1, commandId: 103, trainingType: "Guts", stats: { Speed: 5, Stamina: 0, Power: 5, Guts: 8, Wit: 0 }, energy: -22, skillPoints: 4 },
    { scenarioId: 1, commandId: 105, trainingType: "Stamina", stats: { Speed: 0, Stamina: 10, Power: 0, Guts: 6, Wit: 0 }, energy: -19, skillPoints: 4 },
    { scenarioId: 1, commandId: 106, trainingType: "Wit", stats: { Speed: 2, Stamina: 0, Power: 0, Guts: 0, Wit: 10 }, energy: 5, skillPoints: 5 },
    { scenarioId: 3, commandId: 101, trainingType: "Speed", stats: { Speed: 11, Stamina: 0, Power: 6, Guts: 0, Wit: 0 }, energy: -21, skillPoints: 4 },
    { scenarioId: 3, commandId: 102, trainingType: "Power", stats: { Speed: 0, Stamina: 4, Power: 9, Guts: 0, Wit: 0 }, energy: -20, skillPoints: 4 },
    { scenarioId: 3, commandId: 103, trainingType: "Guts", stats: { Speed: 5, Stamina: 0, Power: 5, Guts: 8, Wit: 0 }, energy: -22, skillPoints: 4 },
    { scenarioId: 3, commandId: 105, trainingType: "Stamina", stats: { Speed: 0, Stamina: 10, Power: 0, Guts: 6, Wit: 0 }, energy: -19, skillPoints: 4 },
    { scenarioId: 3, commandId: 106, trainingType: "Wit", stats: { Speed: 2, Stamina: 0, Power: 0, Guts: 0, Wit: 10 }, energy: 5, skillPoints: 5 },
]

export interface TraineeSpec {
    readonly cardId: number
    readonly character: string
    readonly outfit: string
    readonly growth: Readonly<Record<string, number>>
    readonly startStats: Readonly<Record<BudgetStat, number>>
}

/** Two trainees whose only meaningful difference is where their thirty growth points went. */
export const LOW_STAMINA_GROWTH: TraineeSpec = {
    cardId: 900101,
    character: "Fixture Sprinter",
    outfit: "[No Stamina]",
    growth: { speed: 20, stamina: 0, power: 10, guts: 0, wit: 0 },
    startStats: { Speed: 100, Stamina: 90, Power: 100, Guts: 90, Wit: 90 },
}

export const HIGH_STAMINA_GROWTH: TraineeSpec = {
    cardId: 900201,
    character: "Fixture Stayer",
    outfit: "[All Stamina]",
    growth: { speed: 10, stamina: 20, power: 0, guts: 0, wit: 0 },
    startStats: { Speed: 100, Stamina: 90, Power: 100, Guts: 90, Wit: 90 },
}

export function buildEvidence(trainees: readonly TraineeSpec[] = [LOW_STAMINA_GROWTH, HIGH_STAMINA_GROWTH]): BuildBudgetEvidence {
    const document = {
        schema: "build_budget_evidence",
        schemaVersion: 1,
        source: "synthetic build-budget fixture",
        statCaps: {
            baseline: 1200,
            scenarioBonus: [
                { scenarioId: 1, bonus: { Speed: 200, Stamina: 200, Power: 200, Guts: 200, Wit: 200 } },
                { scenarioId: 3, bonus: { Speed: 400, Stamina: 100, Power: 100, Guts: 300, Wit: 100 } },
            ],
        },
        factorGroups: [...BLUE_GROUPS.map(([id, stat]) => blueGroup(id, stat)), LONG_APTITUDE_GROUP, RECOVERY_SPARK_GROUP],
        traineeGrowth: trainees.map((t) => ({ cardId: t.cardId, charaId: Math.floor(t.cardId / 100), character: t.character, outfit: t.outfit, growth: t.growth, runningStyle: 3 })),
        traineeBase: trainees.map((t) => ({
            cardId: t.cardId,
            starLevel: 5,
            startStats: t.startStats,
            aptitudes: { sprint: "C", mile: "A", medium: "A", long: "A", turf: "A", dirt: "G", front: "C", pace: "A", late: "A", end: "C" },
        })),
        trainingEffects: TRAINING_ROWS.map((row) => ({ ...row, subId: 1, resultState: 2, isCamp: false, mood: 0, failureRateByLevel: [500, 505, 510, 515, 520] })),
        careerTurns: [
            { scenarioId: 1, turnSetId: 1, totalTurns: 78 },
            { scenarioId: 3, turnSetId: 3, totalTurns: 78 },
        ],
    }
    return createBuildBudgetEvidence(JSON.stringify(document))
}

export interface CardSpec {
    readonly id: number
    readonly charaId: number
    readonly title: string
    readonly supportType: string
    readonly rarity?: "R" | "SR" | "SSR"
    readonly effects?: readonly { readonly type: number; readonly curve: number[] }[]
    readonly hintSkillIds?: readonly number[]
}

export function buildCatalogue(cards: readonly CardSpec[]): SupportCardIndex {
    const data: SupportCardData = parseSupportCardData({
        schema: SUPPORT_CARD_SCHEMA,
        schemaVersion: SUPPORT_CARD_SCHEMA_VERSION,
        source: "synthetic build-budget fixture",
        effectTypes: { "1": "Friendship Bonus", "4": "Stamina Bonus", "8": "Training Effectiveness", "10": "Initial Stamina", "14": "Initial Friendship Gauge", "21": "Max Stamina", "30": "Skill Point Bonus" },
        undecodedUniqueEffectTypeFloor: 100,
        effectLevelThresholds: THRESHOLDS,
        levelCapsByRarity: { R: [20, 25, 30, 35, 40], SR: [25, 30, 35, 40, 45], SSR: [30, 35, 40, 45, 50] },
        scenarios: [
            { id: 1, name: "URA Finale", statCapBonus: { Speed: 200, Stamina: 200, Power: 200, Guts: 200, Wit: 200 }, specialCharaIds: [], restrictedCardIds: [] },
            { id: 3, name: "Grand Concert", statCapBonus: { Speed: 400, Stamina: 100, Power: 100, Guts: 300, Wit: 100 }, specialCharaIds: [], restrictedCardIds: [] },
        ],
        characters: Object.fromEntries(cards.map((c) => [String(c.charaId), `Fixture ${c.charaId}`])),
        cards: cards.map((c) => ({
            id: c.id,
            charaId: c.charaId,
            title: c.title,
            rarity: c.rarity ?? "SSR",
            supportType: c.supportType,
            effects: c.effects ?? [],
            uniqueEffect: null,
            hintSkillIds: c.hintSkillIds ?? [],
            groupMemberCharaIds: null,
            restrictedScenarioIds: [],
        })),
    })
    return buildSupportCardIndex(data)
}

export function deckTargetFor(index: SupportCardIndex, scenario: string): DeckTargetBuild {
    return buildDeckTarget({ trainee: null, scenario, distance: "long", surface: "turf", runningStyle: "late" }, index)
}

/** Scores a deck at a uniform level, so a fixture's arithmetic does not depend on level curves. */
export function scoreFixtureDeck(index: SupportCardIndex, target: DeckTargetBuild, cardIds: readonly number[], level = 50, borrowedId: number | null = null): DeckScore {
    const profiles: CardValueProfile[] = cardIds.map((id) =>
        valueCard(index, { supportCardId: id, level, levelCap: level, limitBreak: 4, owned: true, borrowed: id === borrowedId }, target),
    )
    return scoreDeck(index, profiles, target, { requireFullSize: false })
}

export function traineeFrom(spec: TraineeSpec): BudgetTrainee {
    const growth = { Speed: spec.growth.speed, Stamina: spec.growth.stamina, Power: spec.growth.power, Guts: spec.growth.guts, Wit: spec.growth.wit } as Record<BudgetStat, number>
    return { traineeName: spec.character, cardId: spec.cardId, starLevel: 5, startStats: spec.startStats, growth, aptitudes: null, origin: "DECODED" }
}

/** A pair carrying the named Sparks. Carrier counts drive the inheritance bracket, so they are explicit. */
export function pairWith(factors: readonly { family: string; canonicalName: string; stars: number }[], label = "fixture pair"): BudgetParentPair {
    return { label, parentIds: ["parentA", "parentB"], factors, affinityPoints: [7, 5], evidenceComplete: true }
}

export function constraintOf(minimum: number | null, preferred: readonly [number, number] | null, recovery: readonly number[] = [], confidence: "low" | "moderate" = "moderate"): SurvivalConstraint {
    return {
        schemaVersion: 1,
        targetRace: "Fixture Cup",
        targetStrategy: "late",
        minimumStamina: minimum,
        preferredStaminaRange: preferred,
        recoveryRequirements: recovery,
        debuffRiskPolicy: "TWO_STAMINA_DEBUFFS",
        confidence,
        unknownMechanics: ["SKILL_ACTIVATION_PROBABILITY"],
    }
}

/**
 * A resolved live borrow pool built from named catalogue cards.
 *
 * `unresolvedCharacters` are rows the resolver will fail to join, which is the point: the ranking must
 * be able to prove it never touched them, and a fixture with only resolvable rows could not show that.
 */
export function makeBorrowResolution(
    index: SupportCardIndex,
    cardIds: readonly number[],
    options: { readonly scanId?: string; readonly unresolvedCharacters?: readonly string[]; readonly termination?: string; readonly levelById?: Readonly<Record<number, number>> } = {},
): BorrowPoolResolution {
    const entries = cardIds.map((id, i) => {
        const card = index.byId.get(id)
        if (!card) throw new Error(`fixture borrow pool names card ${id}, which is not in the fixture catalogue`)
        return {
            character: index.data.characters[String(card.charaId)] ?? `Fixture ${card.charaId}`,
            title: card.title ?? "",
            rarity: card.rarity,
            support_type: card.supportType,
            level: options.levelById?.[id] ?? 50,
            limit_break_index: 4,
            source_type: "FOLLOW",
            owner_alias: `owner-${i}`,
            entry_fingerprint: `fixture-${id}`,
            confidence: "High",
        }
    })
    for (const [i, character] of (options.unresolvedCharacters ?? []).entries()) {
        entries.push({
            character,
            title: "[Nothing In The Catalogue]",
            rarity: "R",
            support_type: "Speed",
            level: 20,
            limit_break_index: 0,
            source_type: "FOLLOW",
            owner_alias: `owner-x${i}`,
            entry_fingerprint: `fixture-unresolved-${i}`,
            confidence: "Low",
        })
    }
    const snapshot = parseBorrowPoolSnapshot({
        schema: "deck_lab_borrow_pool",
        schema_version: 1,
        scan_id: options.scanId ?? "fixture-scan",
        source_screen: "borrow_picker",
        termination: options.termination ?? "UI_END_REACHED",
        entries,
    })
    return resolveBorrowPool(snapshot, index)
}
