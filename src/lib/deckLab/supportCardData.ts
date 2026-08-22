// DeckLab - the support-card catalogue. Pure, offline, deterministic.
//
// Reads the asset scripts/generate-support-card-data.mjs extracts from the game's own master.mdb and
// turns it into the indexes the rest of DeckLab asks questions of. Nothing here knows about the
// account, a target, or a deck: this layer answers only "what does the game say this card is, and
// what are its numbers at a given level".
//
// The one piece of real arithmetic in here is resolveEffectValue. A card effect is a threshold curve,
// not a formula: the asset stores the value at level 1 followed by one entry per five levels, with -1
// meaning "unchanged here". The value at a level is the newest entry at or below it. Nothing
// interpolates, because the game does not.

export const SUPPORT_CARD_SCHEMA = "deck_lab_support_cards"
export const SUPPORT_CARD_SCHEMA_VERSION = 2

/** Absent, as distinct from zero: the card has no such effect at this level. */
export const EFFECT_ABSENT = -1

export const SUPPORT_RARITIES = ["R", "SR", "SSR"] as const
export type SupportRarity = (typeof SUPPORT_RARITIES)[number]

/** The five training types, plus the two non-training roles a card can fill. */
export const TRAINING_SUPPORT_TYPES = ["Speed", "Stamina", "Power", "Guts", "Wit"] as const
export type TrainingSupportType = (typeof TRAINING_SUPPORT_TYPES)[number]

export const SUPPORT_TYPES = [...TRAINING_SUPPORT_TYPES, "Friend", "Group"] as const
export type SupportType = (typeof SUPPORT_TYPES)[number]

/** Limit-break steps. 0 is an unbroken copy, 4 is fully limit-broken. */
export const LIMIT_BREAK_STEPS = [0, 1, 2, 3, 4] as const
export type LimitBreak = (typeof LIMIT_BREAK_STEPS)[number]
export const MAX_LIMIT_BREAK = 4

/**
 * The effect type codes this repository relies on by name.
 *
 * Every code the game ships is carried through; these are the ones DeckLab reasons about. The names
 * are the game's own, read out of text_data, and the asset carries the full table so an unnamed code
 * can still be reported rather than dropped.
 */
export const EFFECT = {
    FRIENDSHIP_BONUS: 1,
    MOOD_EFFECT: 2,
    SPEED_BONUS: 3,
    STAMINA_BONUS: 4,
    POWER_BONUS: 5,
    GUTS_BONUS: 6,
    WIT_BONUS: 7,
    TRAINING_EFFECTIVENESS: 8,
    INITIAL_SPEED: 9,
    INITIAL_STAMINA: 10,
    INITIAL_POWER: 11,
    INITIAL_GUTS: 12,
    INITIAL_WIT: 13,
    INITIAL_FRIENDSHIP_GAUGE: 14,
    RACE_BONUS: 15,
    FAN_BONUS: 16,
    HINT_LEVELS: 17,
    HINT_FREQUENCY: 18,
    SPECIALTY_PRIORITY: 19,
    MAX_SPEED: 20,
    MAX_STAMINA: 21,
    MAX_POWER: 22,
    MAX_GUTS: 23,
    MAX_WIT: 24,
    EVENT_RECOVERY: 25,
    EVENT_EFFECTIVENESS: 26,
    FAILURE_PROTECTION: 27,
    ENERGY_COST_REDUCTION: 28,
    MINIGAME_EFFECTIVENESS: 29,
    SKILL_POINT_BONUS: 30,
    WIT_FRIENDSHIP_RECOVERY: 31,
} as const

/** The stat a per-stat effect code belongs to, for the three families that have one per stat. */
export const STAT_BONUS_EFFECT: Readonly<Record<TrainingSupportType, number>> = {
    Speed: EFFECT.SPEED_BONUS,
    Stamina: EFFECT.STAMINA_BONUS,
    Power: EFFECT.POWER_BONUS,
    Guts: EFFECT.GUTS_BONUS,
    Wit: EFFECT.WIT_BONUS,
}

export const INITIAL_STAT_EFFECT: Readonly<Record<TrainingSupportType, number>> = {
    Speed: EFFECT.INITIAL_SPEED,
    Stamina: EFFECT.INITIAL_STAMINA,
    Power: EFFECT.INITIAL_POWER,
    Guts: EFFECT.INITIAL_GUTS,
    Wit: EFFECT.INITIAL_WIT,
}

export interface SupportCardEffectCurve {
    /** The game's effect type code. */
    readonly type: number
    /** Value at each of effectLevelThresholds, with -1 meaning unchanged from the previous entry. */
    readonly curve: readonly number[]
}

export interface SupportCardUniqueEffect {
    /** The card level at which the unique perk turns on. Below it the perk does nothing. */
    readonly unlockLevel: number
    /** The game's own wording for the perk, condition included. Null if the client ships none. */
    readonly description: string | null
    /** Unique effects whose type code is in the ordinary effect domain and therefore has a value. */
    readonly effects: readonly { readonly type: number; readonly value: number }[]
    /** Type codes in the separate conditional encoding this repository has not decoded. */
    readonly undecodedTypes: readonly number[]
}

export interface SupportCardRecord {
    readonly id: number
    readonly charaId: number
    /** Null for R cards, which the game ships without an epithet. */
    readonly title: string | null
    readonly rarity: SupportRarity
    readonly supportType: SupportType
    readonly effects: readonly SupportCardEffectCurve[]
    readonly uniqueEffect: SupportCardUniqueEffect | null
    readonly hintSkillIds: readonly number[]
    readonly groupMemberCharaIds: readonly number[] | null
    readonly restrictedScenarioIds: readonly number[]
}

export interface ScenarioRecord {
    /** The game's own scenario id: 1 URA Finale, 2 Unity Cup, 3 Grand Concert, 4 Trackblazer. */
    readonly id: number
    readonly name: string | null
    /** Stat cap bonus this scenario grants over the shared base, per stat. */
    readonly statCapBonus: Readonly<Record<TrainingSupportType, number>>
    /** Characters the scenario treats as its own. Empty is a real answer, not a missing one. */
    readonly specialCharaIds: readonly number[]
    /** Cards this scenario forbids outright. */
    readonly restrictedCardIds: readonly number[]
}

export interface SupportCardData {
    readonly schema: string
    readonly schemaVersion: number
    readonly source: string
    readonly effectTypes: Readonly<Record<string, string>>
    readonly undecodedUniqueEffectTypeFloor: number
    readonly effectLevelThresholds: readonly number[]
    readonly levelCapsByRarity: Readonly<Record<SupportRarity, readonly number[]>>
    readonly scenarios: readonly ScenarioRecord[]
    readonly characters: Readonly<Record<string, string | null>>
    readonly cards: readonly SupportCardRecord[]
}

/** The stable identity of a printed card. Ownership and limit break are account state, not identity. */
export interface SupportCardRef {
    readonly supportCardId: number
    readonly characterId: number
    readonly characterName: string
    /** Character plus title, or just the character for an untitled R card. */
    readonly displayName: string
    readonly title: string | null
    readonly rarity: SupportRarity
    readonly supportType: SupportType
}

export class SupportCardDataError extends Error {
    constructor(message: string) {
        super(message)
        this.name = "SupportCardDataError"
    }
}

function requireArray(value: unknown, what: string): unknown[] {
    if (!Array.isArray(value)) throw new SupportCardDataError(`${what} is not an array`)
    return value
}

/**
 * Validates the shape of a parsed asset. Deliberately strict: a silently mistyped catalogue would
 * produce deck advice that looks fine and is wrong about every card.
 */
export function parseSupportCardData(raw: unknown): SupportCardData {
    if (!raw || typeof raw !== "object") throw new SupportCardDataError("support card data is not an object")
    const doc = raw as Record<string, unknown>
    if (doc.schema !== SUPPORT_CARD_SCHEMA) throw new SupportCardDataError(`support card data has schema ${String(doc.schema)}, expected ${SUPPORT_CARD_SCHEMA}`)
    if (doc.schemaVersion !== SUPPORT_CARD_SCHEMA_VERSION) {
        throw new SupportCardDataError(`support card data has schemaVersion ${String(doc.schemaVersion)}, expected ${SUPPORT_CARD_SCHEMA_VERSION}`)
    }

    const thresholds = requireArray(doc.effectLevelThresholds, "effectLevelThresholds").map((v) => {
        if (typeof v !== "number") throw new SupportCardDataError("effectLevelThresholds holds a non-number")
        return v
    })
    if (!thresholds.length) throw new SupportCardDataError("effectLevelThresholds is empty")

    const caps = (doc.levelCapsByRarity ?? {}) as Record<string, unknown>
    for (const rarity of SUPPORT_RARITIES) {
        const row = caps[rarity]
        if (!Array.isArray(row) || row.length !== LIMIT_BREAK_STEPS.length) throw new SupportCardDataError(`levelCapsByRarity.${rarity} is not a ${LIMIT_BREAK_STEPS.length}-entry array`)
        for (const v of row) if (typeof v !== "number") throw new SupportCardDataError(`levelCapsByRarity.${rarity} holds a non-number`)
    }

    const cards = requireArray(doc.cards, "cards").map((entry, i) => {
        if (!entry || typeof entry !== "object") throw new SupportCardDataError(`card ${i} is not an object`)
        const c = entry as Record<string, unknown>
        if (typeof c.id !== "number") throw new SupportCardDataError(`card ${i} has no numeric id`)
        if (typeof c.charaId !== "number") throw new SupportCardDataError(`card ${c.id} has no numeric charaId`)
        if (!SUPPORT_RARITIES.includes(c.rarity as SupportRarity)) throw new SupportCardDataError(`card ${c.id} has unknown rarity ${String(c.rarity)}`)
        if (!SUPPORT_TYPES.includes(c.supportType as SupportType)) throw new SupportCardDataError(`card ${c.id} has unknown supportType ${String(c.supportType)}`)
        const effects = requireArray(c.effects, `card ${c.id} effects`).map((e) => {
            const row = e as Record<string, unknown>
            const curve = requireArray(row.curve, `card ${c.id} effect curve`)
            if (curve.length !== thresholds.length) throw new SupportCardDataError(`card ${c.id} effect ${String(row.type)} has ${curve.length} curve entries, expected ${thresholds.length}`)
            return { type: row.type as number, curve: curve as number[] }
        })
        return {
            id: c.id,
            charaId: c.charaId,
            title: (c.title ?? null) as string | null,
            rarity: c.rarity as SupportRarity,
            supportType: c.supportType as SupportType,
            effects,
            uniqueEffect: (c.uniqueEffect ?? null) as SupportCardUniqueEffect | null,
            hintSkillIds: (c.hintSkillIds ?? []) as number[],
            groupMemberCharaIds: (c.groupMemberCharaIds ?? null) as number[] | null,
            restrictedScenarioIds: (c.restrictedScenarioIds ?? []) as number[],
        }
    })
    if (!cards.length) throw new SupportCardDataError("support card data has no cards")

    return {
        schema: SUPPORT_CARD_SCHEMA,
        schemaVersion: SUPPORT_CARD_SCHEMA_VERSION,
        source: String(doc.source ?? ""),
        effectTypes: (doc.effectTypes ?? {}) as Record<string, string>,
        undecodedUniqueEffectTypeFloor: typeof doc.undecodedUniqueEffectTypeFloor === "number" ? doc.undecodedUniqueEffectTypeFloor : 100,
        effectLevelThresholds: thresholds,
        levelCapsByRarity: caps as Record<SupportRarity, number[]>,
        scenarios: (doc.scenarios ?? []) as readonly ScenarioRecord[],
        characters: (doc.characters ?? {}) as Record<string, string | null>,
        cards,
    }
}

export interface SupportCardIndex {
    readonly data: SupportCardData
    readonly byId: ReadonlyMap<number, SupportCardRecord>
    /** Normalized character name + normalized title -> the cards that match. */
    readonly byNormalizedKey: ReadonlyMap<string, readonly SupportCardRecord[]>
    /** Normalized character name -> that character's cards. */
    readonly byNormalizedCharacter: ReadonlyMap<string, readonly SupportCardRecord[]>
    readonly characterName: (charaId: number) => string
    readonly effectName: (type: number) => string
    /** Characters the named scenario treats as its own. Empty for a scenario the table does not name. */
    readonly scenarioSpecialCharaIds: (scenarioId: number) => ReadonlySet<number>
    /** The scenario record, or null when the id is not one the client ships. */
    readonly scenario: (scenarioId: number) => ScenarioRecord | null
}

/**
 * Case, punctuation and markup-insensitive key.
 *
 * The owned inventory is read off screenshots and the catalogue is read out of the database, so the
 * two disagree on trailing punctuation and on the decorative marks several titles end with. Reducing
 * both to letters and digits is what makes the join land; it is not a fuzzy match, and a collision
 * is reported rather than guessed at.
 */
export function normalizeName(value: string | null | undefined): string {
    return String(value ?? "")
        .replace(/<[^>]*>/g, "")
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "")
}

export function buildSupportCardIndex(data: SupportCardData): SupportCardIndex {
    const byId = new Map<number, SupportCardRecord>()
    const byNormalizedKey = new Map<string, SupportCardRecord[]>()
    const byNormalizedCharacter = new Map<string, SupportCardRecord[]>()
    const scenarioSpecial = new Map<number, Set<number>>()

    const scenarioById = new Map<number, ScenarioRecord>()
    for (const entry of data.scenarios) {
        scenarioSpecial.set(entry.id, new Set(entry.specialCharaIds))
        scenarioById.set(entry.id, entry)
    }

    const nameOf = (charaId: number): string => data.characters[String(charaId)] ?? `Character ${charaId}`

    for (const card of data.cards) {
        if (byId.has(card.id)) throw new SupportCardDataError(`support card id ${card.id} appears twice`)
        byId.set(card.id, card)
        const charKey = normalizeName(nameOf(card.charaId))
        if (!byNormalizedCharacter.has(charKey)) byNormalizedCharacter.set(charKey, [])
        byNormalizedCharacter.get(charKey)!.push(card)
        const key = `${charKey}|${normalizeName(card.title)}`
        if (!byNormalizedKey.has(key)) byNormalizedKey.set(key, [])
        byNormalizedKey.get(key)!.push(card)
    }

    return {
        data,
        byId,
        byNormalizedKey,
        byNormalizedCharacter,
        characterName: nameOf,
        effectName: (type: number) => data.effectTypes[String(type)] ?? `Effect ${type}`,
        scenarioSpecialCharaIds: (scenarioId: number) => scenarioSpecial.get(scenarioId) ?? new Set<number>(),
        scenario: (scenarioId: number) => scenarioById.get(scenarioId) ?? null,
    }
}

export function supportCardRef(index: SupportCardIndex, card: SupportCardRecord): SupportCardRef {
    const characterName = index.characterName(card.charaId)
    return {
        supportCardId: card.id,
        characterId: card.charaId,
        characterName,
        displayName: card.title ? `${characterName} [${card.title}]` : characterName,
        title: card.title,
        rarity: card.rarity,
        supportType: card.supportType,
    }
}

/** The level cap a card of this rarity reaches at this limit break. */
export function levelCapFor(data: SupportCardData, rarity: SupportRarity, limitBreak: number): number {
    const row = data.levelCapsByRarity[rarity]
    if (!row) throw new SupportCardDataError(`no level caps for rarity ${rarity}`)
    const step = Math.max(0, Math.min(MAX_LIMIT_BREAK, Math.trunc(limitBreak)))
    return row[step]
}

/** The limit break a level cap implies, or null if no step produces that cap. */
export function limitBreakForLevelCap(data: SupportCardData, rarity: SupportRarity, levelCap: number): LimitBreak | null {
    const row = data.levelCapsByRarity[rarity]
    if (!row) return null
    const step = row.indexOf(levelCap)
    return step < 0 ? null : (step as LimitBreak)
}

/**
 * The value of one effect curve at a level, or EFFECT_ABSENT if the card has no such effect yet.
 *
 * The curve is a step function. Walking it forward and keeping the newest entry at or below the level
 * is the whole rule; a -1 is a hole to step over, never a zero to read.
 */
export function resolveEffectValue(data: SupportCardData, curve: readonly number[], level: number): number {
    let value = EFFECT_ABSENT
    for (let i = 0; i < data.effectLevelThresholds.length; i++) {
        if (data.effectLevelThresholds[i] > level) break
        if (curve[i] !== EFFECT_ABSENT) value = curve[i]
    }
    return value
}

/** Every effect a card actually has at a level, keyed by type code. Absent effects are omitted. */
export function resolveCardEffects(data: SupportCardData, card: SupportCardRecord, level: number): Map<number, number> {
    const out = new Map<number, number>()
    for (const effect of card.effects) {
        const value = resolveEffectValue(data, effect.curve, level)
        if (value !== EFFECT_ABSENT) out.set(effect.type, value)
    }
    return out
}

export interface ResolvedUniqueEffect {
    readonly unlocked: boolean
    readonly unlockLevel: number
    readonly description: string | null
    /** Decoded contributions, empty when the perk is locked. */
    readonly effects: readonly { readonly type: number; readonly value: number }[]
    /** Type codes that are unlocked but not decoded: real, unquantified value. */
    readonly undecodedTypes: readonly number[]
}

/**
 * Resolves a card's unique perk at a level.
 *
 * A limit break raises the cap; it does not level the card. A perk that gates on level 40 stays off on
 * a fully limit-broken card sitting at level 35, and that gap is a real, cheap upgrade the advisor
 * should be able to point at, so it is modelled rather than smoothed over.
 */
export function resolveUniqueEffect(card: SupportCardRecord, level: number): ResolvedUniqueEffect | null {
    if (!card.uniqueEffect) return null
    const unlocked = level >= card.uniqueEffect.unlockLevel
    return {
        unlocked,
        unlockLevel: card.uniqueEffect.unlockLevel,
        description: card.uniqueEffect.description,
        effects: unlocked ? card.uniqueEffect.effects : [],
        undecodedTypes: unlocked ? card.uniqueEffect.undecodedTypes : [],
    }
}

/**
 * Every effect a card has at a level, unique perk folded in.
 *
 * Unique contributions add to the base effect of the same type, which is how the game presents them
 * (a card with a base Training Effectiveness and a unique Training Effectiveness shows one combined
 * figure). Undecoded unique types are returned separately and never become a number.
 */
export function resolveTotalEffects(
    data: SupportCardData,
    card: SupportCardRecord,
    level: number,
): { readonly effects: Map<number, number>; readonly unique: ResolvedUniqueEffect | null; readonly undecodedUniqueTypes: readonly number[] } {
    const effects = resolveCardEffects(data, card, level)
    const unique = resolveUniqueEffect(card, level)
    if (unique) {
        for (const contribution of unique.effects) {
            effects.set(contribution.type, (effects.get(contribution.type) ?? 0) + contribution.value)
        }
    }
    return { effects, unique, undecodedUniqueTypes: unique?.undecodedTypes ?? [] }
}
