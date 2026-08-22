// DeckLab - what one support card is worth, under one target. Pure, offline, deterministic.
//
// Value is a vector, not a number. Every entry in it is a decoded figure read off the game's own
// effect table at the level the card is actually at, so "Friendship Bonus 25" in a report is the same
// 25 the game shows. Two things are kept strictly apart from those figures:
//
//   The composite. Ranking needs an ordering, and an ordering needs weights, and no weights are
//   decoded. So there is exactly one editorial number here, it is named EDITORIAL, and its weight
//   table is exported so it can be argued with. Nothing presents it as a game mechanic.
//
//   The unknowns. A unique perk whose type code this repository has not decoded is real value that
//   cannot be counted. It lowers confidence and is listed by the game's own description, and it never
//   becomes a number, because a made-up number would be indistinguishable from a decoded one.
//
// The level a card is read at is its CURRENT level, never its cap. A limit break raises the cap; it
// does not level the card, and a unique perk that gates on level 40 is off on a fully limit-broken
// card sitting at 35. That gap is the cheapest upgrade on an account, so it is modelled and reported.

import type { DeckTargetBuild } from "./deckTarget.ts"
import type { OwnedSupportCard } from "./inventory.ts"
import {
    EFFECT,
    INITIAL_STAT_EFFECT,
    resolveTotalEffects,
    STAT_BONUS_EFFECT,
    TRAINING_SUPPORT_TYPES,
    type SupportCardIndex,
    type SupportCardRef,
    type TrainingSupportType,
} from "./supportCardData.ts"

/** The named dimensions DeckLab reads off a card. Every one is backed by a decoded effect code. */
export const VALUE_DIMENSIONS = [
    "friendshipBonus",
    "moodEffect",
    "trainingEffectiveness",
    "specialtyPriority",
    "initialBond",
    "initialStats",
    "statBonusOwnType",
    "maxStatBonus",
    "raceBonus",
    "fanBonus",
    "hintLevels",
    "hintFrequency",
    "skillPointBonus",
    "eventRecovery",
    "eventEffectiveness",
    "failureProtection",
    "energyCostReduction",
    "witFriendshipRecovery",
    "minigameEffectiveness",
] as const
export type ValueDimension = (typeof VALUE_DIMENSIONS)[number]

/** Dimension -> the single effect code behind it. Composite dimensions are computed separately. */
const SINGLE_EFFECT_DIMENSIONS: Readonly<Partial<Record<ValueDimension, number>>> = {
    friendshipBonus: EFFECT.FRIENDSHIP_BONUS,
    moodEffect: EFFECT.MOOD_EFFECT,
    trainingEffectiveness: EFFECT.TRAINING_EFFECTIVENESS,
    specialtyPriority: EFFECT.SPECIALTY_PRIORITY,
    initialBond: EFFECT.INITIAL_FRIENDSHIP_GAUGE,
    raceBonus: EFFECT.RACE_BONUS,
    fanBonus: EFFECT.FAN_BONUS,
    hintLevels: EFFECT.HINT_LEVELS,
    hintFrequency: EFFECT.HINT_FREQUENCY,
    skillPointBonus: EFFECT.SKILL_POINT_BONUS,
    eventRecovery: EFFECT.EVENT_RECOVERY,
    eventEffectiveness: EFFECT.EVENT_EFFECTIVENESS,
    failureProtection: EFFECT.FAILURE_PROTECTION,
    energyCostReduction: EFFECT.ENERGY_COST_REDUCTION,
    witFriendshipRecovery: EFFECT.WIT_FRIENDSHIP_RECOVERY,
    minigameEffectiveness: EFFECT.MINIGAME_EFFECTIVENESS,
}

/**
 * Editorial weights for the one composite number in DeckLab.
 *
 * These are not decoded and are not claimed to be. The shape of them is the ordinary reading of how
 * training works: Friendship Bonus and Training Effectiveness multiply every gain a card is present
 * for, so they dominate; Specialty Priority decides how often the card is present at all; Initial
 * Bond decides how quickly the multipliers switch on. The rest are real but smaller, and the flat
 * stat and race numbers are worth least per point because they do not compound.
 *
 * They exist to order candidates and to prune a search, never to state a game figure. Change them and
 * the rankings change; that is the point of them being here rather than buried.
 */
export const EDITORIAL_DIMENSION_WEIGHTS: Readonly<Record<ValueDimension, number>> = {
    friendshipBonus: 3.0,
    trainingEffectiveness: 3.0,
    specialtyPriority: 2.0,
    initialBond: 1.6,
    moodEffect: 1.2,
    hintLevels: 1.0,
    skillPointBonus: 0.9,
    hintFrequency: 0.8,
    statBonusOwnType: 0.8,
    failureProtection: 0.6,
    energyCostReduction: 0.6,
    witFriendshipRecovery: 0.6,
    raceBonus: 0.5,
    eventEffectiveness: 0.4,
    eventRecovery: 0.4,
    initialStats: 0.25,
    maxStatBonus: 0.25,
    fanBonus: 0.15,
    minigameEffectiveness: 0.1,
}

/** How much a report can be trusted about one card. */
export const CARD_CONFIDENCES = ["HIGH", "MEDIUM", "LOW"] as const
export type CardConfidence = (typeof CARD_CONFIDENCES)[number]

export interface CardLimitBreakState {
    readonly limitBreak: number
    readonly level: number
    readonly levelCap: number
    /** Levels already paid for by the current limit break but not yet bought. */
    readonly unlevelledHeadroom: number
    readonly uniqueUnlockLevel: number | null
    readonly uniqueUnlocked: boolean
    /** The composite this card would reach at its current cap, if levelling it is the only change. */
    readonly compositeAtCap: number
    /** Composite gained by levelling to the current cap. Zero when already there. */
    readonly compositeFromLevelling: number
}

export interface CardScenarioFit {
    readonly scenarioId: number | null
    /** False only when the scenario forbids the card outright. */
    readonly legal: boolean
    /** The scenario names this card's character as one of its own. Decoded; its magnitude is not. */
    readonly scenarioSpecialCharacter: boolean
    /** Weighted overlap between the card's training type and the stat caps this scenario raises. */
    readonly statCapAlignment: number
}

export interface CardTargetFit {
    /** The target's weight on this card's own training type, or null for Friend and Group cards. */
    readonly trainingTypeWeight: number | null
    /** Priority skills this card can hint. Empty when the target names no skills. */
    readonly matchedPrioritySkillIds: readonly number[]
    /** The card's whole decoded hint pool. */
    readonly hintPoolSize: number
}

export interface CardValueProfile {
    readonly card: SupportCardRef
    readonly owned: boolean
    readonly borrowed: boolean
    readonly dimensions: Readonly<Record<ValueDimension, number>>
    readonly limitBreakState: CardLimitBreakState
    readonly scenarioFit: CardScenarioFit
    readonly targetFit: CardTargetFit
    /** The unique perk's own wording, when the card has one. */
    readonly uniqueDescription: string | null
    /** Named mechanics this card has that DeckLab cannot quantify. */
    readonly unknownMechanics: readonly string[]
    readonly confidence: CardConfidence
    /** Editorial. See EDITORIAL_DIMENSION_WEIGHTS. Never a game figure. */
    readonly composite: number
    readonly compositeOrigin: "EDITORIAL_WEIGHTS"
    /** Warnings inherited from how the owned row was resolved. */
    readonly inventoryWarnings: readonly string[]
}

function emptyDimensions(): Record<ValueDimension, number> {
    const out = {} as Record<ValueDimension, number>
    for (const dimension of VALUE_DIMENSIONS) out[dimension] = 0
    return out
}

/**
 * Reads every named dimension off a card at a level.
 *
 * Absent effects read zero here, which is correct for a value vector: a card with no Race Bonus
 * contributes no race bonus. The distinction between absent and zero lives in the catalogue layer,
 * where it matters for resolving the curve.
 */
export function readDimensions(index: SupportCardIndex, cardId: number, level: number): { dimensions: Record<ValueDimension, number>; undecodedUniqueTypes: readonly number[]; uniqueDescription: string | null; uniqueUnlockLevel: number | null; uniqueUnlocked: boolean } {
    const card = index.byId.get(cardId)
    if (!card) throw new Error(`support card ${cardId} is not in the catalogue`)
    const { effects, unique, undecodedUniqueTypes } = resolveTotalEffects(index.data, card, level)

    const dimensions = emptyDimensions()
    for (const [dimension, type] of Object.entries(SINGLE_EFFECT_DIMENSIONS) as [ValueDimension, number][]) {
        dimensions[dimension] = effects.get(type) ?? 0
    }
    for (const stat of TRAINING_SUPPORT_TYPES) {
        dimensions.initialStats += effects.get(INITIAL_STAT_EFFECT[stat]) ?? 0
    }
    for (const type of [EFFECT.MAX_SPEED, EFFECT.MAX_STAMINA, EFFECT.MAX_POWER, EFFECT.MAX_GUTS, EFFECT.MAX_WIT]) {
        dimensions.maxStatBonus += effects.get(type) ?? 0
    }
    if ((TRAINING_SUPPORT_TYPES as readonly string[]).includes(card.supportType)) {
        dimensions.statBonusOwnType = effects.get(STAT_BONUS_EFFECT[card.supportType as TrainingSupportType]) ?? 0
    }

    return {
        dimensions,
        undecodedUniqueTypes,
        uniqueDescription: card.uniqueEffect?.description ?? null,
        uniqueUnlockLevel: card.uniqueEffect?.unlockLevel ?? null,
        uniqueUnlocked: unique?.unlocked ?? false,
    }
}

export function compositeOf(dimensions: Readonly<Record<ValueDimension, number>>): number {
    let total = 0
    for (const dimension of VALUE_DIMENSIONS) total += dimensions[dimension] * EDITORIAL_DIMENSION_WEIGHTS[dimension]
    return Number(total.toFixed(4))
}

/**
 * How well a card's training type lines up with the stat caps the scenario raises.
 *
 * Decoded on both sides: the scenario's cap bonuses come from the game's scenario table and the
 * card's type from its command. It is a normalized share, not a claim about how much that is worth.
 */
function statCapAlignment(build: DeckTargetBuild, supportType: string): number {
    if (!build.scenario) return 0
    if (!(TRAINING_SUPPORT_TYPES as readonly string[]).includes(supportType)) return 0
    const bonuses = build.scenario.statCapBonus
    const total = TRAINING_SUPPORT_TYPES.reduce((sum, stat) => sum + (bonuses[stat] ?? 0), 0)
    if (total <= 0) return 0
    return Number(((bonuses[supportType as TrainingSupportType] ?? 0) / total).toFixed(4))
}

export interface ValueCardInput {
    readonly supportCardId: number
    readonly level: number
    readonly levelCap: number
    readonly limitBreak: number
    readonly borrowed: boolean
    readonly owned: boolean
    readonly inventoryWarnings?: readonly string[]
}

export function ownedCardInput(card: OwnedSupportCard, borrowed = false): ValueCardInput {
    return {
        supportCardId: card.card.supportCardId,
        level: card.level,
        levelCap: card.levelCap,
        limitBreak: card.limitBreak,
        borrowed,
        owned: true,
        inventoryWarnings: card.warnings,
    }
}

export function valueCard(index: SupportCardIndex, input: ValueCardInput, build: DeckTargetBuild): CardValueProfile {
    const card = index.byId.get(input.supportCardId)
    if (!card) throw new Error(`support card ${input.supportCardId} is not in the catalogue`)

    const atLevel = readDimensions(index, input.supportCardId, input.level)
    const atCap = readDimensions(index, input.supportCardId, input.levelCap)
    const composite = compositeOf(atLevel.dimensions)
    const compositeAtCap = compositeOf(atCap.dimensions)

    const scenarioId = build.scenario?.id ?? null
    const legal = scenarioId === null || !card.restrictedScenarioIds.includes(scenarioId)
    const special = scenarioId !== null && index.scenarioSpecialCharaIds(scenarioId).has(card.charaId)

    const matchedPrioritySkillIds = build.prioritySkillIds.size ? card.hintSkillIds.filter((id) => build.prioritySkillIds.has(id)) : []

    const unknownMechanics: string[] = []
    if (atLevel.undecodedUniqueTypes.length) {
        const label = atLevel.uniqueDescription ? `unique perk "${atLevel.uniqueDescription}"` : "unique perk"
        unknownMechanics.push(`${label} uses a conditional effect encoding this repository has not decoded, so it is not counted in any number here`)
    }
    if (special) {
        unknownMechanics.push(`this scenario names ${index.characterName(card.charaId)} as one of its own, which is decoded, but how much that is worth is not`)
    }
    if (!card.hintSkillIds.length) unknownMechanics.push("the catalogue lists no hint skills for this card")

    let confidence: CardConfidence = "HIGH"
    if (unknownMechanics.length || (input.inventoryWarnings?.length ?? 0) > 0) confidence = "MEDIUM"
    if ((input.inventoryWarnings ?? []).some((w) => w === "LIMIT_BREAK_CAP_DISAGREEMENT" || w === "LEVEL_ABOVE_CAP")) confidence = "LOW"

    return {
        card: {
            supportCardId: card.id,
            characterId: card.charaId,
            characterName: index.characterName(card.charaId),
            displayName: card.title ? `${index.characterName(card.charaId)} [${card.title}]` : index.characterName(card.charaId),
            title: card.title,
            rarity: card.rarity,
            supportType: card.supportType,
        },
        owned: input.owned,
        borrowed: input.borrowed,
        dimensions: atLevel.dimensions,
        limitBreakState: {
            limitBreak: input.limitBreak,
            level: input.level,
            levelCap: input.levelCap,
            unlevelledHeadroom: Math.max(0, input.levelCap - input.level),
            uniqueUnlockLevel: atLevel.uniqueUnlockLevel,
            uniqueUnlocked: atLevel.uniqueUnlocked,
            compositeAtCap,
            compositeFromLevelling: Number(Math.max(0, compositeAtCap - composite).toFixed(4)),
        },
        scenarioFit: {
            scenarioId,
            legal,
            scenarioSpecialCharacter: special,
            statCapAlignment: statCapAlignment(build, card.supportType),
        },
        targetFit: {
            trainingTypeWeight: (TRAINING_SUPPORT_TYPES as readonly string[]).includes(card.supportType) ? build.statWeight[card.supportType as TrainingSupportType] : null,
            matchedPrioritySkillIds,
            hintPoolSize: card.hintSkillIds.length,
        },
        uniqueDescription: atLevel.uniqueDescription,
        unknownMechanics,
        confidence,
        composite,
        compositeOrigin: "EDITORIAL_WEIGHTS",
        inventoryWarnings: input.inventoryWarnings ?? [],
    }
}

/**
 * The composite weighted by how much the target wants this card's training type.
 *
 * This is what a search ranks on, and it is where target-awareness enters a single card's ordering: a
 * Stamina card and a Speed card with identical effect tables rank differently for a Long build than
 * for a Sprint one. Friend and Group cards have no training type, so they carry the mean weight and
 * are neither promoted nor punished by the target's stat ordering.
 */
export function targetWeightedComposite(profile: CardValueProfile, build: DeckTargetBuild): number {
    const weights = TRAINING_SUPPORT_TYPES.map((stat) => build.statWeight[stat])
    const mean = weights.reduce((a, b) => a + b, 0) / weights.length
    const typeWeight = profile.targetFit.trainingTypeWeight ?? mean
    const skillBonus = profile.targetFit.matchedPrioritySkillIds.length ? 1 + Math.min(0.25, 0.05 * profile.targetFit.matchedPrioritySkillIds.length) : 1
    return Number((profile.composite * typeWeight * skillBonus).toFixed(4))
}
