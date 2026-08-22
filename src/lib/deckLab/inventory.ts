// DeckLab - the account's owned support cards. Pure, offline, deterministic, read-only.
//
// The owned inventory is not game data and is not in this repository. It is a maintained snapshot
// built from per-card screenshots, and it names cards the way the game prints them rather than by id.
// This module is the join: it resolves each owned row onto exactly one catalogue card, or refuses to
// and says why.
//
// Three things it deliberately will not do:
//
//   It will not guess. A row that matches two cards, or none, becomes an unresolved entry with the
//   reason attached. Deck advice built on a mis-joined card is worse than deck advice that says a
//   card is missing.
//
//   It will not paper over a contradiction. The snapshot carries both a limit-break index and a level
//   cap, and the catalogue knows which cap each limit-break step produces. When those disagree the
//   entry is flagged, not silently resolved in favour of one of them.
//
//   It will not treat a limit break as a level. A card can be limit-broken and left unlevelled, so
//   the current level is what every value is read at, and the headroom to the cap is reported.

import {
    buildSupportCardIndex,
    levelCapFor,
    limitBreakForLevelCap,
    MAX_LIMIT_BREAK,
    normalizeName,
    supportCardRef,
    SUPPORT_RARITIES,
    SUPPORT_TYPES,
    type LimitBreak,
    type SupportCardIndex,
    type SupportCardRecord,
    type SupportCardRef,
    type SupportRarity,
    type SupportType,
} from "./supportCardData.ts"

export const OWNED_INVENTORY_SCHEMA = "deck_lab_owned_support_inventory"
export const OWNED_INVENTORY_SCHEMA_VERSION = 1

/** How a resolved row was matched, most trustworthy first. */
export const MATCH_METHODS = ["CHARACTER_AND_TITLE", "TITLE_ONLY", "CHARACTER_AND_RARITY"] as const
export type MatchMethod = (typeof MATCH_METHODS)[number]

/** Why a row could not be resolved. */
export const UNRESOLVED_REASONS = ["NO_CATALOGUE_MATCH", "AMBIGUOUS_MATCH", "RARITY_CONFLICT", "SUPPORT_TYPE_CONFLICT", "MALFORMED_ROW"] as const
export type UnresolvedReason = (typeof UNRESOLVED_REASONS)[number]

/** Defects that leave a row usable but less trustworthy. */
export const OWNED_CARD_WARNINGS = ["LIMIT_BREAK_CAP_DISAGREEMENT", "LEVEL_ABOVE_CAP", "MATCHED_WITHOUT_CHARACTER", "MATCHED_WITHOUT_TITLE", "LOW_CONFIDENCE_SOURCE"] as const
export type OwnedCardWarning = (typeof OWNED_CARD_WARNINGS)[number]

export interface OwnedSupportCard {
    readonly card: SupportCardRef
    readonly limitBreak: LimitBreak
    /** The level the card is actually at. Every value in DeckLab is read at this level. */
    readonly level: number
    /** The level this copy could reach without another limit break. */
    readonly levelCap: number
    /** Levels available at the current limit break but not yet bought. */
    readonly unlevelledHeadroom: number
    readonly owned: true
    readonly matchMethod: MatchMethod
    readonly warnings: readonly OwnedCardWarning[]
    readonly evidenceSource: string
}

export interface UnresolvedOwnedRow {
    readonly rawCharacter: string
    readonly rawTitle: string
    readonly reason: UnresolvedReason
    readonly detail: string
}

export interface OwnedSupportInventorySnapshot {
    readonly schema: string
    readonly schemaVersion: number
    /** Where the rows came from, carried through verbatim so a report can name its evidence. */
    readonly inventoryName: string
    readonly snapshotDate: string | null
    readonly evidenceSource: string
    /** True only when this describes the whole account, so an absent card really means "not owned". */
    readonly claimsCompleteAccount: boolean
    readonly cards: readonly OwnedSupportCard[]
    readonly unresolved: readonly UnresolvedOwnedRow[]
    /** Rows read, before resolution. */
    readonly rowCount: number
    /** Copies of the same catalogue card, which the snapshot format cannot express but a report should not hide. */
    readonly duplicateCardIds: readonly number[]
}

export class InventoryError extends Error {
    constructor(message: string) {
        super(message)
        this.name = "InventoryError"
    }
}

interface RawRow {
    readonly character: string
    readonly title: string
    readonly rarity: string | null
    readonly supportType: string | null
    readonly level: number | null
    readonly levelCap: number | null
    readonly limitBreakIndex: number | null
    readonly confidence: string | null
    readonly sourceFile: string | null
}

/**
 * Reads the maintained snapshot format.
 *
 * Accepts either the maintained file (an object with a `cards` array) or a bare array of rows, and
 * accepts both camelCase and the snake_case the snapshot is written in, so a fixture does not have to
 * imitate the exact keys of a file that lives outside this repository.
 */
function readRows(raw: unknown): { rows: RawRow[]; meta: Record<string, unknown> } {
    const meta: Record<string, unknown> = Array.isArray(raw) ? {} : ((raw ?? {}) as Record<string, unknown>)
    const list = Array.isArray(raw) ? raw : (meta.cards ?? meta.supportCards)
    if (!Array.isArray(list)) throw new InventoryError("owned inventory has no cards array")

    const pick = (row: Record<string, unknown>, ...keys: string[]): unknown => {
        for (const key of keys) if (row[key] !== undefined && row[key] !== null) return row[key]
        return null
    }
    const num = (value: unknown): number | null => (typeof value === "number" && Number.isFinite(value) ? value : null)
    const str = (value: unknown): string | null => (typeof value === "string" && value.trim() ? value.trim() : null)

    const rows = list.map((entry) => {
        const row = (entry ?? {}) as Record<string, unknown>
        return {
            character: str(pick(row, "character", "characterName")) ?? "",
            title: str(pick(row, "card_title", "cardTitle", "title")) ?? "",
            rarity: str(pick(row, "rarity")),
            supportType: str(pick(row, "support_type", "supportType")),
            level: num(pick(row, "current_level", "currentLevel", "level")),
            levelCap: num(pick(row, "level_cap", "levelCap")),
            limitBreakIndex: num(pick(row, "limit_break_index", "limitBreakIndex", "limitBreak")),
            confidence: str(pick(row, "confidence")),
            sourceFile: str(pick(row, "source_file", "sourceFile")),
        }
    })
    return { rows, meta }
}

/**
 * Picks the single catalogue card a row names.
 *
 * Character and title together is the only strong match. Title alone is the fallback for the rows
 * whose "character" is not a trainee the game names in its character table (a Group card printed
 * under a team name, or a support staff member the inventory records under a different form of their
 * name); it is still unique across the catalogue, so it resolves, but it is recorded as weaker.
 * Character and rarity is the last resort, and only for a row with no title at all, which is what an
 * R card looks like.
 */
function matchCard(index: SupportCardIndex, row: RawRow): { card: SupportCardRecord; method: MatchMethod } | { reason: UnresolvedReason; detail: string } {
    const charKey = normalizeName(row.character)
    const titleKey = normalizeName(row.title)

    if (charKey && titleKey) {
        const exact = index.byNormalizedKey.get(`${charKey}|${titleKey}`) ?? []
        if (exact.length === 1) return { card: exact[0], method: "CHARACTER_AND_TITLE" }
        if (exact.length > 1) return { reason: "AMBIGUOUS_MATCH", detail: `character and title match ${exact.length} cards` }
    }

    if (titleKey) {
        const byTitle: SupportCardRecord[] = []
        for (const [key, cards] of index.byNormalizedKey) {
            if (key.endsWith(`|${titleKey}`)) byTitle.push(...cards)
        }
        if (byTitle.length === 1) return { card: byTitle[0], method: "TITLE_ONLY" }
        if (byTitle.length > 1) return { reason: "AMBIGUOUS_MATCH", detail: `title alone matches ${byTitle.length} cards` }
    }

    if (charKey && !titleKey && row.rarity) {
        const pool = (index.byNormalizedCharacter.get(charKey) ?? []).filter((c) => c.rarity === row.rarity)
        if (pool.length === 1) return { card: pool[0], method: "CHARACTER_AND_RARITY" }
        if (pool.length > 1) return { reason: "AMBIGUOUS_MATCH", detail: `character and rarity match ${pool.length} cards` }
    }

    return { reason: "NO_CATALOGUE_MATCH", detail: charKey || titleKey ? "no catalogue card matches this character and title" : "row names neither a character nor a title" }
}

/**
 * Resolves the limit break of a row.
 *
 * The snapshot states one and also states a level cap, and the catalogue knows which cap each step
 * produces. When they agree the answer is certain. When only one is present that one is used. When
 * they disagree the stated index wins, because it is what the maintained file is keyed on, and the
 * disagreement is attached to the card so a report can show it rather than a reader having to trust it.
 */
function resolveLimitBreak(
    index: SupportCardIndex,
    card: SupportCardRecord,
    row: RawRow,
): { limitBreak: LimitBreak; levelCap: number; warnings: OwnedCardWarning[] } {
    const warnings: OwnedCardWarning[] = []
    const fromCap = row.levelCap === null ? null : limitBreakForLevelCap(index.data, card.rarity, row.levelCap)
    const stated = row.limitBreakIndex === null ? null : (Math.max(0, Math.min(MAX_LIMIT_BREAK, Math.trunc(row.limitBreakIndex))) as LimitBreak)

    let limitBreak: LimitBreak
    if (stated !== null && fromCap !== null) {
        limitBreak = stated
        if (stated !== fromCap) warnings.push("LIMIT_BREAK_CAP_DISAGREEMENT")
    } else if (stated !== null) {
        limitBreak = stated
    } else if (fromCap !== null) {
        limitBreak = fromCap
    } else {
        limitBreak = 0
        warnings.push("LIMIT_BREAK_CAP_DISAGREEMENT")
    }

    return { limitBreak, levelCap: levelCapFor(index.data, card.rarity, limitBreak), warnings }
}

/**
 * Builds the owned-card snapshot.
 *
 * `claimsCompleteAccount` is the caller's assertion, not something this function can verify, and it is
 * what every "you do not own a better card" statement downstream depends on. It is carried explicitly
 * so that claim is never made by accident.
 */
export function buildOwnedInventory(
    raw: unknown,
    index: SupportCardIndex,
    options: { readonly evidenceSource: string; readonly claimsCompleteAccount: boolean },
): OwnedSupportInventorySnapshot {
    const { rows, meta } = readRows(raw)
    const cards: OwnedSupportCard[] = []
    const unresolved: UnresolvedOwnedRow[] = []
    const seen = new Map<number, number>()

    for (const row of rows) {
        if (!row.character && !row.title) {
            unresolved.push({ rawCharacter: row.character, rawTitle: row.title, reason: "MALFORMED_ROW", detail: "row names neither a character nor a title" })
            continue
        }

        const match = matchCard(index, row)
        if ("reason" in match) {
            unresolved.push({ rawCharacter: row.character, rawTitle: row.title, reason: match.reason, detail: match.detail })
            continue
        }

        const card = match.card
        if (row.rarity && SUPPORT_RARITIES.includes(row.rarity as SupportRarity) && row.rarity !== card.rarity) {
            unresolved.push({ rawCharacter: row.character, rawTitle: row.title, reason: "RARITY_CONFLICT", detail: `snapshot says ${row.rarity}, catalogue says ${card.rarity}` })
            continue
        }
        if (row.supportType && SUPPORT_TYPES.includes(row.supportType as SupportType) && row.supportType !== card.supportType) {
            unresolved.push({
                rawCharacter: row.character,
                rawTitle: row.title,
                reason: "SUPPORT_TYPE_CONFLICT",
                detail: `snapshot says ${row.supportType}, catalogue says ${card.supportType}`,
            })
            continue
        }

        const { limitBreak, levelCap, warnings } = resolveLimitBreak(index, card, row)
        if (match.method === "TITLE_ONLY") warnings.push("MATCHED_WITHOUT_CHARACTER")
        if (match.method === "CHARACTER_AND_RARITY") warnings.push("MATCHED_WITHOUT_TITLE")
        if (row.confidence && row.confidence.toLowerCase() !== "high") warnings.push("LOW_CONFIDENCE_SOURCE")

        // A level above the cap the stated limit break allows cannot both be true; the level is clamped
        // so no downstream value is read off a level the card cannot be at, and the conflict is kept.
        const statedLevel = row.level ?? levelCap
        let level = statedLevel
        if (statedLevel > levelCap) {
            level = levelCap
            warnings.push("LEVEL_ABOVE_CAP")
        }

        seen.set(card.id, (seen.get(card.id) ?? 0) + 1)
        cards.push({
            card: supportCardRef(index, card),
            limitBreak,
            level,
            levelCap,
            unlevelledHeadroom: Math.max(0, levelCap - level),
            owned: true,
            matchMethod: match.method,
            warnings: [...new Set(warnings)].sort(),
            evidenceSource: row.sourceFile ?? options.evidenceSource,
        })
    }

    cards.sort((a, b) => a.card.supportCardId - b.card.supportCardId)

    return {
        schema: OWNED_INVENTORY_SCHEMA,
        schemaVersion: OWNED_INVENTORY_SCHEMA_VERSION,
        inventoryName: typeof meta.inventory_name === "string" ? meta.inventory_name : typeof meta.inventoryName === "string" ? meta.inventoryName : "owned support cards",
        snapshotDate: typeof meta.snapshot_date === "string" ? meta.snapshot_date : typeof meta.snapshotDate === "string" ? meta.snapshotDate : null,
        evidenceSource: options.evidenceSource,
        claimsCompleteAccount: options.claimsCompleteAccount,
        cards,
        unresolved,
        rowCount: rows.length,
        duplicateCardIds: [...seen.entries()]
            .filter(([, count]) => count > 1)
            .map(([id]) => id)
            .sort((a, b) => a - b),
    }
}

export interface InventoryCompleteness {
    readonly rowCount: number
    readonly resolved: number
    readonly unresolved: number
    readonly withWarnings: number
    /** Every owned row resolved onto exactly one catalogue card. */
    readonly fullyResolved: boolean
    /** The snapshot resolves cleanly and claims to describe the whole account. */
    readonly trustedForAccountClaims: boolean
    readonly catalogueSize: number
    readonly ownedDistinctCards: number
    readonly gaps: readonly string[]
}

/**
 * States what the snapshot can and cannot support.
 *
 * Two different claims are separated here. "This deck is the best of what you own" needs every row to
 * resolve. "You own nothing better" additionally needs the snapshot to be the whole account, which no
 * amount of internal consistency can establish, so it is only ever asserted, never inferred.
 */
export function assessInventory(snapshot: OwnedSupportInventorySnapshot, index: SupportCardIndex): InventoryCompleteness {
    const gaps: string[] = []
    if (snapshot.unresolved.length) gaps.push(`${snapshot.unresolved.length} owned rows did not resolve onto a catalogue card`)
    if (!snapshot.claimsCompleteAccount) gaps.push("the snapshot is not asserted to cover the whole account, so an absent card does not mean it is not owned")
    if (snapshot.duplicateCardIds.length) gaps.push(`${snapshot.duplicateCardIds.length} catalogue cards appear more than once`)
    const withWarnings = snapshot.cards.filter((c) => c.warnings.length).length
    if (withWarnings) gaps.push(`${withWarnings} owned cards carry a resolution warning`)

    const fullyResolved = snapshot.unresolved.length === 0 && snapshot.rowCount > 0
    return {
        rowCount: snapshot.rowCount,
        resolved: snapshot.cards.length,
        unresolved: snapshot.unresolved.length,
        withWarnings,
        fullyResolved,
        trustedForAccountClaims: fullyResolved && snapshot.claimsCompleteAccount,
        catalogueSize: index.data.cards.length,
        ownedDistinctCards: new Set(snapshot.cards.map((c) => c.card.supportCardId)).size,
        gaps,
    }
}

/**
 * A demonstration inventory built from the catalogue itself.
 *
 * Exists so the engine can be exercised and reported on when no real owned data is present, and it is
 * labelled everywhere it is used. It takes the lowest card ids that fill each role so the selection is
 * deterministic and obviously arbitrary rather than quietly plausible.
 */
export function buildFixtureInventory(index: SupportCardIndex, options: { readonly perType?: number; readonly limitBreak?: LimitBreak } = {}): OwnedSupportInventorySnapshot {
    const perType = options.perType ?? 3
    const limitBreak = options.limitBreak ?? 4
    const rows: Record<string, unknown>[] = []
    for (const type of SUPPORT_TYPES) {
        const pool = index.data.cards.filter((c) => c.supportType === type && c.title).sort((a, b) => a.id - b.id)
        for (const card of pool.slice(0, perType)) {
            rows.push({
                character: index.characterName(card.charaId),
                card_title: card.title,
                rarity: card.rarity,
                support_type: card.supportType,
                limit_break_index: limitBreak,
                current_level: levelCapFor(index.data, card.rarity, limitBreak),
                level_cap: levelCapFor(index.data, card.rarity, limitBreak),
                confidence: "High",
                source_file: "fixture",
            })
        }
    }
    return buildOwnedInventory({ inventory_name: "DeckLab fixture inventory (not a real account)", cards: rows }, index, {
        evidenceSource: "fixture",
        claimsCompleteAccount: false,
    })
}

export { buildSupportCardIndex }
