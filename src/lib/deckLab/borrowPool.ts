// DeckLab - the borrow pool: the support cards this account can actually borrow right now. Pure,
// offline, deterministic, read-only.
//
// A borrow-pool snapshot is not game data and is not in this repository. It is produced by a
// read-only on-device scan of the game's Borrow Card picker and names cards the way the picker
// prints them (an "[Outfit] Character" name band) rather than by id. This module is the join: it
// resolves each observed borrow row onto exactly one catalogue card, or refuses to and says why,
// and then hands DeckLab a set of real borrow candidates in place of the hypothetical universe.
//
// It reuses the owned-inventory resolver (buildOwnedInventory) card for card, so a borrow row and an
// owned row resolve by exactly the same fail-closed rules. What this layer adds on top is
// borrow-specific and lives nowhere else:
//
//   Provenance. Who a card can be borrowed from (friend / follow / guest) is captured and carried,
//   but it is never folded into card value -- a decoded game mechanic would have to use it first.
//
//   Same card, many owners. The same catalogue card offered by several friends is one borrow
//   candidate, not several, so a popular card cannot distort deck value by sheer count. The best
//   observed copy wins and every owner it was seen from is kept beside it.
//
//   Unknown limit break. The picker may not expose a card's limit break. An entry whose limit break
//   could not be observed still resolves, but it is marked and its confidence is lowered, because a
//   card's value is read at a limit break and guessing one silently would be a lie.

import { buildOwnedInventory, type OwnedCardWarning, type OwnedSupportCard, type UnresolvedReason } from "./inventory.ts"
import { normalizeName, type SupportCardIndex } from "./supportCardData.ts"

export const BORROW_POOL_SCHEMA = "deck_lab_borrow_pool"
export const BORROW_POOL_SCHEMA_VERSION = 1

/** Where a borrow candidate can be borrowed from, as the picker distinguishes it. */
export const BORROW_SOURCE_TYPES = ["FRIEND", "FOLLOW", "GUEST", "UNKNOWN"] as const
export type BorrowSourceType = (typeof BORROW_SOURCE_TYPES)[number]

/**
 * How far the scan got, and whether the pool it describes is the whole visible pool or only a
 * bounded window of it. Only COMPLETE_VISIBLE_POOL and UI_END_REACHED assert that an absent card is
 * really not borrowable; every other code means "this is what was seen, not all there is".
 */
export const BORROW_TERMINATIONS = ["COMPLETE_VISIBLE_POOL", "UI_END_REACHED", "BOUNDED_PARTIAL", "SCROLL_LIMIT_REACHED", "UNEXPECTED_SCREEN", "AMBIGUOUS_ALIGNMENT"] as const
export type BorrowTermination = (typeof BORROW_TERMINATIONS)[number]

/** The terminations under which the snapshot claims to have seen the whole borrowable pool. */
const COMPLETE_TERMINATIONS: ReadonlySet<BorrowTermination> = new Set(["COMPLETE_VISIBLE_POOL", "UI_END_REACHED"])

/** One observed row of the Borrow Card picker, before any catalogue resolution. */
export interface BorrowPoolEntry {
    /** Character name as the picker rendered it (the line under the outfit bracket). */
    readonly character: string
    /** Outfit / title in the bracket, or empty for a row that carried none. */
    readonly title: string
    readonly rarity: string | null
    readonly supportType: string | null
    /** The card's current level, or null when the picker did not expose it. */
    readonly level: number | null
    /** The level cap the visible limit break allows, or null when not exposed. */
    readonly levelCap: number | null
    /** The limit-break index (0..4), or null when the picker did not expose it. */
    readonly limitBreakIndex: number | null
    readonly sourceType: BorrowSourceType
    /**
     * A stable local alias for the owner, never the owner's real display name. The scanner is
     * responsible for redaction; this field exists so a report can say "seen from 3 sources" without
     * carrying personal data. Null when no owner was distinguished.
     */
    readonly ownerAlias: string | null
    /**
     * Identity of this observation, stable across scrolls of the same list. Used to tell a row seen
     * twice from two distinct rows that happen to name the same card. The scanner computes it; when it
     * is absent this module derives a conservative one from the visible fields.
     */
    readonly entryFingerprint: string
    /** Free-form evidence for the log and for a human audit (raw OCR, crop name, scores). */
    readonly evidence: string
    /** The scanner's own confidence label for this row, carried through verbatim. */
    readonly confidence: string | null
}

/** A versioned, append-only-friendly snapshot of one scan of the Borrow Card picker. */
export interface BorrowPoolSnapshot {
    readonly schema: string
    readonly schemaVersion: number
    /** Identifies this scan, so two snapshots from different scans never merge by accident. */
    readonly scanId: string
    readonly observedAt: string | null
    /** The screen the scan read, carried through so a report can name its evidence. */
    readonly sourceScreen: string
    /**
     * Which refresh of the borrow list this is. The list rotates; a report must never treat rows from
     * two generations as one simultaneous pool. Null when the scanner did not track refreshes.
     */
    readonly refreshGeneration: number | null
    /** Free-form note on which account / roster this was scanned against. Never personal data. */
    readonly rosterContext: string | null
    readonly entries: readonly BorrowPoolEntry[]
    readonly termination: BorrowTermination
}

export class BorrowPoolError extends Error {
    constructor(message: string) {
        super(message)
        this.name = "BorrowPoolError"
    }
}

const asString = (value: unknown): string | null => (typeof value === "string" && value.trim() ? value.trim() : null)
const asNumber = (value: unknown): number | null => (typeof value === "number" && Number.isFinite(value) ? value : null)

function pick(row: Record<string, unknown>, ...keys: string[]): unknown {
    for (const key of keys) if (row[key] !== undefined && row[key] !== null) return row[key]
    return null
}

function parseSourceType(value: unknown): BorrowSourceType {
    const text = asString(value)?.toUpperCase() ?? ""
    return (BORROW_SOURCE_TYPES as readonly string[]).includes(text) ? (text as BorrowSourceType) : "UNKNOWN"
}

/**
 * A conservative fallback fingerprint for a row the scanner did not fingerprint itself. It is built
 * only from stable visible identity (card name, rarity, type, limit break) and the owner alias, so two
 * genuinely identical observations collapse while a card offered by two different owners does not. It
 * deliberately excludes level, which drifts as a friend trains a card, and any raw evidence.
 */
function deriveFingerprint(entry: Omit<BorrowPoolEntry, "entryFingerprint">): string {
    return [normalizeName(entry.character), normalizeName(entry.title), entry.rarity ?? "", entry.supportType ?? "", entry.limitBreakIndex ?? "", entry.sourceType, entry.ownerAlias ?? ""].join("|")
}

/**
 * Reads the raw scanner JSON into a typed snapshot. Accepts both the camelCase this module writes and
 * the snake_case a scanner is likely to emit, so a fixture does not have to imitate exact keys. Fails
 * closed on a structurally invalid document (no entries array, unknown termination) rather than
 * silently producing an empty pool that would read as "nothing to borrow".
 */
export function parseBorrowPoolSnapshot(raw: unknown): BorrowPoolSnapshot {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new BorrowPoolError("borrow pool snapshot is not an object")
    const doc = raw as Record<string, unknown>

    const list = pick(doc, "entries", "rows")
    if (!Array.isArray(list)) throw new BorrowPoolError("borrow pool snapshot has no entries array")

    const rawTermination = asString(pick(doc, "termination", "completeness")) ?? ""
    if (!(BORROW_TERMINATIONS as readonly string[]).includes(rawTermination)) {
        throw new BorrowPoolError(`borrow pool termination "${rawTermination}" is not one of ${BORROW_TERMINATIONS.join(", ")}`)
    }

    const entries: BorrowPoolEntry[] = list.map((item) => {
        const row = (item ?? {}) as Record<string, unknown>
        const base = {
            character: asString(pick(row, "character", "characterName")) ?? "",
            title: asString(pick(row, "title", "card_title", "cardTitle", "outfit")) ?? "",
            rarity: asString(pick(row, "rarity")),
            supportType: asString(pick(row, "support_type", "supportType")),
            level: asNumber(pick(row, "level", "current_level", "currentLevel")),
            levelCap: asNumber(pick(row, "level_cap", "levelCap")),
            limitBreakIndex: asNumber(pick(row, "limit_break_index", "limitBreakIndex", "limitBreak")),
            sourceType: parseSourceType(pick(row, "source_type", "sourceType")),
            ownerAlias: asString(pick(row, "owner_alias", "ownerAlias")),
            evidence: asString(pick(row, "evidence")) ?? "",
            confidence: asString(pick(row, "confidence")),
        }
        const fingerprint = asString(pick(row, "entry_fingerprint", "entryFingerprint")) ?? deriveFingerprint(base)
        return { ...base, entryFingerprint: fingerprint }
    })

    return {
        schema: asString(pick(doc, "schema")) ?? BORROW_POOL_SCHEMA,
        schemaVersion: asNumber(pick(doc, "schema_version", "schemaVersion")) ?? BORROW_POOL_SCHEMA_VERSION,
        scanId: asString(pick(doc, "scan_id", "scanId")) ?? "unknown-scan",
        observedAt: asString(pick(doc, "observed_at", "observedAt")),
        sourceScreen: asString(pick(doc, "source_screen", "sourceScreen")) ?? "unknown",
        refreshGeneration: asNumber(pick(doc, "refresh_generation", "refreshGeneration")),
        rosterContext: asString(pick(doc, "roster_context", "rosterContext")),
        entries,
        termination: rawTermination as BorrowTermination,
    }
}

/** Why an observed borrow row did not become a usable candidate. */
export interface UnresolvedBorrowEntry {
    readonly rawCharacter: string
    readonly rawTitle: string
    readonly reason: UnresolvedReason | "UNREADABLE_ROW"
    readonly detail: string
    readonly ownerAlias: string | null
}

/** One resolved borrow candidate: a catalogue card plus everything borrow-specific about it. */
export interface BorrowCandidate {
    /** The card as DeckLab values it. Passed to searchDecks as a borrow candidate. */
    readonly card: OwnedSupportCard
    /** True when a limit break was actually observed; false when it was assumed. */
    readonly limitBreakKnown: boolean
    /** Distinct owners this exact card was seen offered by, most trustworthy copy first. */
    readonly sources: readonly BorrowProvenance[]
    /** Resolution and observation warnings, de-duplicated. */
    readonly warnings: readonly BorrowCandidateWarning[]
}

export const BORROW_CANDIDATE_WARNINGS = ["LIMIT_BREAK_UNKNOWN", "LEVEL_UNKNOWN", "MULTIPLE_SOURCES", "LOW_CONFIDENCE_SCAN"] as const
export type BorrowCandidateWarning = (typeof BORROW_CANDIDATE_WARNINGS)[number] | OwnedCardWarning

export interface BorrowProvenance {
    readonly sourceType: BorrowSourceType
    readonly ownerAlias: string | null
    readonly level: number | null
    readonly limitBreakKnown: boolean
    readonly confidence: string | null
}

export interface BorrowPoolResolution {
    readonly snapshot: BorrowPoolSnapshot
    /** One candidate per distinct catalogue card, best observed copy kept, sorted by card id. */
    readonly candidates: readonly BorrowCandidate[]
    readonly unresolved: readonly UnresolvedBorrowEntry[]
    /** Rows read before resolution. */
    readonly entryCount: number
    /** Rows that resolved onto a catalogue card (before same-card grouping). */
    readonly resolvedRows: number
    /** Distinct catalogue cards available to borrow. */
    readonly distinctCards: number
    /**
     * True only when the snapshot claims to have seen the whole visible pool AND every row resolved,
     * so an absent card can be read as "not borrowable right now". Never inferred from row counts.
     */
    readonly trustedAsCompletePool: boolean
    readonly notes: readonly string[]
}

/** Alphanumerics a row must carry before it can be resolved without guessing. */
const MIN_READABLE_CHARS = 3

function isReadable(entry: BorrowPoolEntry): boolean {
    return (normalizeName(entry.character) + normalizeName(entry.title)).length >= MIN_READABLE_CHARS
}

/**
 * Resolves one entry through the owned-inventory resolver by shaping it as a single owned row. Running
 * the resolver one entry at a time (rather than in a batch) is what lets each resolved card keep its
 * own provenance and fingerprint; the per-call cost is negligible because the catalogue index is built
 * once by the caller. Returns the single resolved card, or the reason it did not resolve.
 */
function resolveOne(index: SupportCardIndex, entry: BorrowPoolEntry): { card: OwnedSupportCard } | { reason: UnresolvedReason; detail: string } {
    const snapshot = buildOwnedInventory(
        {
            cards: [
                {
                    character: entry.character,
                    card_title: entry.title,
                    rarity: entry.rarity,
                    support_type: entry.supportType,
                    current_level: entry.level,
                    level_cap: entry.levelCap,
                    limit_break_index: entry.limitBreakIndex,
                    confidence: entry.confidence,
                    source_file: "borrow scan",
                },
            ],
        },
        index,
        { evidenceSource: "borrow scan", claimsCompleteAccount: false },
    )
    if (snapshot.cards.length === 1) return { card: snapshot.cards[0] }
    const first = snapshot.unresolved[0]
    return { reason: first?.reason ?? "NO_CATALOGUE_MATCH", detail: first?.detail ?? "row did not resolve onto a catalogue card" }
}

/** Best observed copy of a card: highest level cap, then highest level, then a known limit break over an assumed one. */
function betterCopy(a: BorrowCandidate, b: BorrowCandidate): BorrowCandidate {
    if (a.card.levelCap !== b.card.levelCap) return a.card.levelCap > b.card.levelCap ? a : b
    if (a.card.level !== b.card.level) return a.card.level > b.card.level ? a : b
    if (a.limitBreakKnown !== b.limitBreakKnown) return a.limitBreakKnown ? a : b
    return a
}

/**
 * Resolves a whole borrow-pool snapshot into DeckLab borrow candidates.
 *
 * Every trusted candidate is exactly one catalogue card. Unreadable rows and rows that match zero or
 * many cards are set aside with a reason and never enter a recommendation. The same card seen from
 * several owners becomes one candidate carrying every source, so card value never depends on how many
 * friends happen to hold it.
 */
export function resolveBorrowPool(snapshot: BorrowPoolSnapshot, index: SupportCardIndex): BorrowPoolResolution {
    const unresolved: UnresolvedBorrowEntry[] = []
    const byCardId = new Map<number, BorrowCandidate>()
    let resolvedRows = 0

    for (const entry of snapshot.entries) {
        if (!isReadable(entry)) {
            unresolved.push({ rawCharacter: entry.character, rawTitle: entry.title, reason: "UNREADABLE_ROW", detail: "row carried too few readable characters to resolve", ownerAlias: entry.ownerAlias })
            continue
        }

        const result = resolveOne(index, entry)
        if ("reason" in result) {
            unresolved.push({ rawCharacter: entry.character, rawTitle: entry.title, reason: result.reason, detail: result.detail, ownerAlias: entry.ownerAlias })
            continue
        }
        resolvedRows++

        const limitBreakKnown = entry.limitBreakIndex !== null || entry.levelCap !== null
        const warnings: BorrowCandidateWarning[] = [...result.card.warnings]
        if (!limitBreakKnown) warnings.push("LIMIT_BREAK_UNKNOWN")
        if (entry.level === null) warnings.push("LEVEL_UNKNOWN")
        if (entry.confidence && entry.confidence.toLowerCase() !== "high") warnings.push("LOW_CONFIDENCE_SCAN")

        const provenance: BorrowProvenance = { sourceType: entry.sourceType, ownerAlias: entry.ownerAlias, level: entry.level, limitBreakKnown, confidence: entry.confidence }
        const candidate: BorrowCandidate = { card: result.card, limitBreakKnown, sources: [provenance], warnings: [...new Set(warnings)].sort() }

        const existing = byCardId.get(result.card.card.supportCardId)
        if (!existing) {
            byCardId.set(result.card.card.supportCardId, candidate)
        } else {
            const best = betterCopy(existing, candidate)
            const merged: BorrowCandidate = {
                card: best.card,
                limitBreakKnown: best.limitBreakKnown,
                sources: [...existing.sources, provenance],
                warnings: [...new Set([...existing.warnings, ...candidate.warnings, "MULTIPLE_SOURCES"] as BorrowCandidateWarning[])].sort(),
            }
            byCardId.set(result.card.card.supportCardId, merged)
        }
    }

    const candidates = [...byCardId.values()].sort((a, b) => a.card.card.supportCardId - b.card.card.supportCardId)

    const notes: string[] = []
    const complete = COMPLETE_TERMINATIONS.has(snapshot.termination)
    if (!complete) notes.push(`the scan ended ${snapshot.termination}, so an absent card is not proof it cannot be borrowed`)
    if (unresolved.length) notes.push(`${unresolved.length} observed rows did not resolve onto a catalogue card`)
    const lbUnknown = candidates.filter((c) => !c.limitBreakKnown).length
    if (lbUnknown) notes.push(`${lbUnknown} candidates have an unobserved limit break and are valued at an assumed one`)

    return {
        snapshot,
        candidates,
        unresolved,
        entryCount: snapshot.entries.length,
        resolvedRows,
        distinctCards: candidates.length,
        trustedAsCompletePool: complete && unresolved.length === 0 && candidates.length > 0,
        notes,
    }
}

/** The resolved candidates as the plain OwnedSupportCard list searchDecks takes for borrow analysis. */
export function borrowCandidateCards(resolution: BorrowPoolResolution): OwnedSupportCard[] {
    return resolution.candidates.map((c) => c.card)
}
