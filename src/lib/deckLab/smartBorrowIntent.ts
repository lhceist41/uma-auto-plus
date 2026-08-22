// DeckLab - the Smart Borrow selection INTENT. Pure, offline, deterministic.
//
// A borrow recommendation lives entirely offline: resolveBorrowPool turns the on-device census into
// catalogue candidates, and searchDecks picks which one to borrow. The device that would act on that
// pick has no catalogue and no resolver - it only reads faithful picker rows (character, title,
// rarity, level, limit break). This module is the bridge: it distils one recommended supportCardId
// into a SmartBorrowIntent carrying the IDENTITY FIELDS a live locator can match a picker row on
// (canonical character + title + limit break), plus the supportCardId and an evidence digest for the
// offline audit trail. The device never recomputes the supportCardId; it asserts the identity fields
// still resolve to exactly one row, which the offline recommendation already tied to that id.
//
// It reads nothing, writes nothing, and mutates nothing. The CLI serialises the result to a JSON the
// operator pushes to the device for the read-only locate rehearsal.

import type { BorrowPoolResolution } from "./borrowPool.ts"

export const SMART_BORROW_INTENT_SCHEMA = "deck_lab_smart_borrow_intent"
export const SMART_BORROW_INTENT_SCHEMA_VERSION = 1

/**
 * One recommended borrow, expressed as what a live picker locator needs to find and verify it.
 *
 * Every field is derived from the offline resolution of a real borrow scan; nothing here is guessed.
 * `expectedLevel` / `expectedLimitBreak` are null when the scan did not actually observe them (the
 * candidate carried a LEVEL_UNKNOWN / LIMIT_BREAK_UNKNOWN warning), so the device never rejects a row
 * for disagreeing with a value that was never seen.
 */
export interface SmartBorrowIntent {
    readonly schema: string
    readonly schemaVersion: number
    /** The target this borrow was recommended for (the report's target label, e.g. "Medium"). */
    readonly targetProfile: string
    /** The scan the recommendation was computed from, so a stale scan can be caught. Null for a scan with no id. */
    readonly sourceBorrowScanId: string | null
    /** The catalogue id the offline recommendation chose. The device carries it as evidence only. */
    readonly supportCardId: number
    /** Canonical character name from the catalogue, the primary locator key. */
    readonly canonicalCharacter: string
    /** Canonical outfit/title from the catalogue; null only for an untitled card. */
    readonly canonicalTitle: string | null
    /** Character plus title, for the log. */
    readonly displayName: string
    readonly rarity: string
    /** Observed level of the best offered copy, or null when the scan did not read a level. */
    readonly expectedLevel: number | null
    /** Observed limit break (0..4) of the best offered copy, or null when it was not actually seen. */
    readonly expectedLimitBreak: number | null
    /** A redacted owner alias for optional disambiguation when two owners offer the same card. Never a raw name. */
    readonly sourceAlias: string | null
    /** How the noisy name band was joined onto the card offline, kept so a fuzzy match is never read as exact. */
    readonly resolutionPath: string
    /** Resolution/observation warnings carried through, so the device log states what the pick assumed. */
    readonly warnings: readonly string[]
    /** Stable digest over the load-bearing fields, so an edited or mismatched intent is detectable offline. */
    readonly recommendationEvidenceDigest: string
}

/**
 * A stable, dependency-free digest over the fields that decide WHICH card this intent selects.
 * djb2 over a canonical field ordering; the same intent always hashes the same, and any change to a
 * load-bearing field changes it. Not a security hash - an integrity/version marker for the audit trail.
 */
export function intentEvidenceDigest(fields: {
    readonly targetProfile: string
    readonly sourceBorrowScanId: string | null
    readonly supportCardId: number
    readonly canonicalCharacter: string
    readonly canonicalTitle: string | null
    readonly expectedLevel: number | null
    readonly expectedLimitBreak: number | null
}): string {
    const canonical = [
        fields.targetProfile,
        fields.sourceBorrowScanId ?? "",
        String(fields.supportCardId),
        fields.canonicalCharacter,
        fields.canonicalTitle ?? "",
        fields.expectedLevel === null ? "" : String(fields.expectedLevel),
        fields.expectedLimitBreak === null ? "" : String(fields.expectedLimitBreak),
    ].join("")
    let hash = 5381
    for (let i = 0; i < canonical.length; i++) {
        hash = (hash * 33) ^ canonical.charCodeAt(i)
        hash = hash >>> 0
    }
    return `djb2-${hash.toString(16).padStart(8, "0")}`
}

export class SmartBorrowIntentError extends Error {
    constructor(message: string) {
        super(message)
        this.name = "SmartBorrowIntentError"
    }
}

/**
 * Builds the intent for one recommended supportCardId out of a resolved borrow pool.
 *
 * The candidate must be present in the resolution - a recommendation for a card the scan did not
 * resolve is a contradiction, so it throws rather than emitting an intent nothing can locate. The
 * observed level/limit break carry through only when the scan actually saw them.
 */
export function buildSmartBorrowIntent(resolution: BorrowPoolResolution, supportCardId: number, targetProfile: string): SmartBorrowIntent {
    const candidate = resolution.candidates.find((c) => c.card.card.supportCardId === supportCardId)
    if (!candidate) {
        throw new SmartBorrowIntentError(`support card ${supportCardId} is not among the ${resolution.candidates.length} resolved borrow candidates`)
    }

    const ref = candidate.card.card
    const levelKnown = !candidate.warnings.includes("LEVEL_UNKNOWN")
    const expectedLevel = levelKnown ? candidate.card.level : null
    const expectedLimitBreak = candidate.limitBreakKnown ? candidate.card.limitBreak : null
    // Sources are ordered best copy first, so the first alias is the one whose level/limit break the
    // intent carries. A blank/unreadable owner collapsed to null upstream, so it stays null here.
    const sourceAlias = candidate.sources.length ? candidate.sources[0].ownerAlias : null
    const sourceBorrowScanId = resolution.snapshot.scanId

    const digest = intentEvidenceDigest({
        targetProfile,
        sourceBorrowScanId,
        supportCardId,
        canonicalCharacter: ref.characterName,
        canonicalTitle: ref.title,
        expectedLevel,
        expectedLimitBreak,
    })

    return {
        schema: SMART_BORROW_INTENT_SCHEMA,
        schemaVersion: SMART_BORROW_INTENT_SCHEMA_VERSION,
        targetProfile,
        sourceBorrowScanId,
        supportCardId,
        canonicalCharacter: ref.characterName,
        canonicalTitle: ref.title,
        displayName: ref.displayName,
        rarity: ref.rarity,
        expectedLevel,
        expectedLimitBreak,
        sourceAlias,
        resolutionPath: candidate.resolutionPath,
        warnings: candidate.warnings,
        recommendationEvidenceDigest: digest,
    }
}

/** The JSON the operator pushes to the device (outcomes/smart_borrow_intent.json). Snake_case keys so
 * the Kotlin reader maps them without a rename table, matching the device's other JSON inputs. */
export function serializeSmartBorrowIntent(intent: SmartBorrowIntent): string {
    const doc = {
        schema: intent.schema,
        schema_version: intent.schemaVersion,
        target_profile: intent.targetProfile,
        source_borrow_scan_id: intent.sourceBorrowScanId,
        support_card_id: intent.supportCardId,
        canonical_character: intent.canonicalCharacter,
        canonical_title: intent.canonicalTitle,
        display_name: intent.displayName,
        rarity: intent.rarity,
        expected_level: intent.expectedLevel,
        expected_limit_break: intent.expectedLimitBreak,
        source_alias: intent.sourceAlias,
        resolution_path: intent.resolutionPath,
        warnings: intent.warnings,
        recommendation_evidence_digest: intent.recommendationEvidenceDigest,
    }
    return `${JSON.stringify(doc, null, 2)}\n`
}
