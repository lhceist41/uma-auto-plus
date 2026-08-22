// DeckLab - the community ranking prior. Pure, offline, deterministic.
//
// There is a community-maintained ranking sheet for support cards. It is useful and it is not truth:
// it encodes one group's judgement at one point in time, under assumptions about scenario, target and
// account that are not stated in the sheet. So it enters DeckLab as a PRIOR, in its own field, next
// to but never mixed into the decoded game figures.
//
// The pipeline is deliberately four steps with a file in the middle:
//
//   external sheet -> a normalized, versioned snapshot committed as a file -> this parser -> DeckLab
//
// Nothing in DeckLab fetches anything. A report that used the prior names the snapshot, its version
// and its provenance, so a reader can tell how old the opinion they are being shown is.
//
// Import status, 2026-08-22: the sheet is served as a JavaScript application shell, so its cells are
// not present in the page a fetch returns and it cannot be imported deterministically without an
// export step a human performs. The contract and the parser below are therefore complete and the
// snapshot is absent; DeckLab runs without it and says so.

import { normalizeName, type SupportCardIndex } from "./supportCardData.ts"

export const COMMUNITY_PRIOR_SCHEMA = "deck_lab_community_prior"
export const COMMUNITY_PRIOR_SCHEMA_VERSION = 1

/**
 * How a snapshot was produced. A prior whose origin is unknown is still usable and is marked as such
 * rather than being quietly promoted to the same standing as one with a stated export.
 */
export const PRIOR_PROVENANCE_KINDS = ["MANUAL_EXPORT", "SCRIPTED_EXPORT", "HAND_ENTERED", "UNKNOWN"] as const
export type PriorProvenanceKind = (typeof PRIOR_PROVENANCE_KINDS)[number]

export interface CommunityPriorEntry {
    /** Resolved catalogue card id, when the row named a card DeckLab could resolve. */
    readonly supportCardId: number | null
    readonly rawCharacter: string
    readonly rawTitle: string
    /** The sheet's own tier label, carried verbatim. */
    readonly tier: string | null
    /** The sheet's own numeric rank or score, carried verbatim. Lower rank is better. */
    readonly rank: number | null
    readonly score: number | null
    /** The scenario the sheet's opinion is about, when it states one. */
    readonly scenario: string | null
    readonly note: string | null
}

export interface CommunityPriorSnapshot {
    readonly schema: string
    readonly schemaVersion: number
    /** Free text naming the sheet, its tab, and anything else needed to find it again. */
    readonly sourceName: string
    readonly sourceUrl: string | null
    /** The date the sheet was read, not the date this file was parsed. */
    readonly capturedOn: string | null
    readonly provenance: PriorProvenanceKind
    readonly entries: readonly CommunityPriorEntry[]
    readonly unresolvedRows: readonly { readonly rawCharacter: string; readonly rawTitle: string; readonly reason: string }[]
}

export class CommunityPriorError extends Error {
    constructor(message: string) {
        super(message)
        this.name = "CommunityPriorError"
    }
}

/**
 * Parses a normalized prior snapshot and resolves its rows onto catalogue cards.
 *
 * A row that does not resolve is kept in unresolvedRows rather than dropped: a sheet naming cards this
 * client does not ship is a fact about the sheet worth reporting, not noise to hide.
 */
export function parseCommunityPrior(raw: unknown, index: SupportCardIndex): CommunityPriorSnapshot {
    if (!raw || typeof raw !== "object") throw new CommunityPriorError("community prior is not an object")
    const doc = raw as Record<string, unknown>
    if (doc.schema !== COMMUNITY_PRIOR_SCHEMA) throw new CommunityPriorError(`community prior has schema ${String(doc.schema)}, expected ${COMMUNITY_PRIOR_SCHEMA}`)
    if (doc.schemaVersion !== COMMUNITY_PRIOR_SCHEMA_VERSION) {
        throw new CommunityPriorError(`community prior has schemaVersion ${String(doc.schemaVersion)}, expected ${COMMUNITY_PRIOR_SCHEMA_VERSION}`)
    }
    if (!Array.isArray(doc.entries)) throw new CommunityPriorError("community prior has no entries array")

    const entries: CommunityPriorEntry[] = []
    const unresolvedRows: { rawCharacter: string; rawTitle: string; reason: string }[] = []

    for (const entry of doc.entries) {
        const row = (entry ?? {}) as Record<string, unknown>
        const rawCharacter = typeof row.character === "string" ? row.character.trim() : ""
        const rawTitle = typeof row.title === "string" ? row.title.trim() : ""
        const charKey = normalizeName(rawCharacter)
        const titleKey = normalizeName(rawTitle)

        let supportCardId: number | null = null
        const exact = index.byNormalizedKey.get(`${charKey}|${titleKey}`) ?? []
        if (exact.length === 1) supportCardId = exact[0].id
        else {
            const byTitle: number[] = []
            for (const [key, cards] of index.byNormalizedKey) {
                if (titleKey && key.endsWith(`|${titleKey}`)) byTitle.push(...cards.map((c) => c.id))
            }
            if (byTitle.length === 1) supportCardId = byTitle[0]
            else unresolvedRows.push({ rawCharacter, rawTitle, reason: byTitle.length ? `matches ${byTitle.length} catalogue cards` : "no catalogue card matches" })
        }

        entries.push({
            supportCardId,
            rawCharacter,
            rawTitle,
            tier: typeof row.tier === "string" ? row.tier : null,
            rank: typeof row.rank === "number" ? row.rank : null,
            score: typeof row.score === "number" ? row.score : null,
            scenario: typeof row.scenario === "string" ? row.scenario : null,
            note: typeof row.note === "string" ? row.note : null,
        })
    }

    return {
        schema: COMMUNITY_PRIOR_SCHEMA,
        schemaVersion: COMMUNITY_PRIOR_SCHEMA_VERSION,
        sourceName: typeof doc.sourceName === "string" ? doc.sourceName : "unnamed community ranking",
        sourceUrl: typeof doc.sourceUrl === "string" ? doc.sourceUrl : null,
        capturedOn: typeof doc.capturedOn === "string" ? doc.capturedOn : null,
        provenance: PRIOR_PROVENANCE_KINDS.includes(doc.provenance as PriorProvenanceKind) ? (doc.provenance as PriorProvenanceKind) : "UNKNOWN",
        entries,
        unresolvedRows,
    }
}

export interface CommunityPriorIndex {
    readonly snapshot: CommunityPriorSnapshot
    /** Card id -> the sheet's entry, when it resolved. */
    readonly byCardId: ReadonlyMap<number, CommunityPriorEntry>
    readonly resolved: number
    readonly unresolved: number
}

export function buildCommunityPriorIndex(snapshot: CommunityPriorSnapshot): CommunityPriorIndex {
    const byCardId = new Map<number, CommunityPriorEntry>()
    for (const entry of snapshot.entries) {
        if (entry.supportCardId !== null && !byCardId.has(entry.supportCardId)) byCardId.set(entry.supportCardId, entry)
    }
    return { snapshot, byCardId, resolved: byCardId.size, unresolved: snapshot.unresolvedRows.length }
}

/**
 * What the prior says about the cards in a deck, reported alongside the decoded value and never folded
 * into it.
 *
 * Agreement between the prior and DeckLab's own ordering is worth seeing precisely because it is not
 * guaranteed: where they disagree is where either the sheet's assumptions or these editorial weights
 * are worth questioning.
 */
export interface PriorCommentary {
    readonly sourceName: string
    readonly capturedOn: string | null
    readonly provenance: PriorProvenanceKind
    readonly covered: number
    readonly uncovered: number
    readonly entries: readonly { readonly supportCardId: number; readonly displayName: string; readonly tier: string | null; readonly rank: number | null; readonly scenario: string | null }[]
}

export function priorCommentaryFor(cards: readonly { readonly supportCardId: number; readonly displayName: string }[], prior: CommunityPriorIndex | null): PriorCommentary | null {
    if (!prior) return null
    const entries = []
    let uncovered = 0
    for (const card of cards) {
        const entry = prior.byCardId.get(card.supportCardId)
        if (!entry) {
            uncovered += 1
            continue
        }
        entries.push({ supportCardId: card.supportCardId, displayName: card.displayName, tier: entry.tier, rank: entry.rank, scenario: entry.scenario })
    }
    return {
        sourceName: prior.snapshot.sourceName,
        capturedOn: prior.snapshot.capturedOn,
        provenance: prior.snapshot.provenance,
        covered: entries.length,
        uncovered,
        entries,
    }
}
