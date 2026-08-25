package com.steve1316.uma_android_automation

import org.json.JSONObject

/**
 * Pure, Android-free model + parsing + LOCATOR for the DeckLab Smart Borrow selection rehearsal.
 *
 * A borrow recommendation is computed entirely offline (resolveBorrowPool + searchDecks): the device
 * has no catalogue and cannot compute a supportCardId from a live picker row. The offline side distils
 * one recommended card into a [SmartBorrowIntent] carrying the IDENTITY FIELDS a live locator can match
 * a picker row on -- canonical character + title + limit break -- plus the supportCardId it stands for
 * (evidence only) and an integrity digest. The operator pushes that JSON to the device; this file reads
 * it and, given the read-only census rows, decides which live row is the recommended card WITHOUT ever
 * tapping one.
 *
 * The matcher fails CLOSED: a card that is absent, ambiguous, or whose observed limit break contradicts
 * a KNOWN expected one is never "located", so the rehearsal can never tap a card nobody chose. Level is
 * advisory only (a card levels over time; a live copy legitimately reads a different level), so it is
 * logged, never used to reject. Everything here is a pure function of already-observed rows, so it runs
 * unchanged in a JVM unit test.
 */

const val SMART_BORROW_INTENT_SCHEMA = "deck_lab_smart_borrow_intent"

/**
 * Which offline ranking chose the intent's card, mirroring the TypeScript `IntentSource`. The device
 * cannot recompute a recommendation, so this is provenance it reads only to decide whether a build-aware
 * launch may proceed (A2): the production launch gate requires [BUILD_AWARE]. [UNKNOWN] covers a v1
 * intent that predates the field and any unrecognised value, and always fails the build-aware gate closed.
 */
enum class IntentRecommendationSource {
    DECKLAB_COMPOSITE,
    BUILD_AWARE,
    UNKNOWN,
}

/** Maps the raw `recommendation_source` string to an [IntentRecommendationSource], defaulting to
 * [IntentRecommendationSource.UNKNOWN] for an absent field (a v1 intent) or any unrecognised value. */
fun parseIntentRecommendationSource(raw: String?): IntentRecommendationSource =
    when (raw?.trim()?.uppercase()) {
        "BUILD_AWARE" -> IntentRecommendationSource.BUILD_AWARE
        "DECKLAB_COMPOSITE" -> IntentRecommendationSource.DECKLAB_COMPOSITE
        else -> IntentRecommendationSource.UNKNOWN
    }

/** The recommended borrow, as the device needs it to find and verify the card. Mirrors the offline
 * `SmartBorrowIntent`. Nullable observed fields stay null when the scan never saw them, so the locator
 * never rejects a row for disagreeing with a value that was never observed. */
data class SmartBorrowIntent(
    val schema: String,
    val schemaVersion: Int,
    val targetProfile: String,
    val sourceBorrowScanId: String?,
    val supportCardId: Int,
    val canonicalCharacter: String,
    val canonicalTitle: String?,
    val displayName: String,
    val rarity: String?,
    val expectedLevel: Int?,
    val expectedLimitBreak: Int?,
    val sourceAlias: String?,
    val resolutionPath: String?,
    val recommendationEvidenceDigest: String?,
    /** Which offline ranking chose this card (schema v2). Provenance only for locate/select; the A2
     * launch gate requires [IntentRecommendationSource.BUILD_AWARE]. Defaults to UNKNOWN so a v1 intent
     * and every existing constructor stay valid. */
    val recommendationSource: IntentRecommendationSource = IntentRecommendationSource.UNKNOWN,
)

/**
 * Reads the operator-pushed intent JSON (snake_case keys, matching the offline serializer). Returns
 * null on anything malformed, the wrong schema, or a missing load-bearing field: a bad intent must
 * fail the rehearsal closed, never be half-read into a partial pick. Never throws.
 */
fun parseSmartBorrowIntent(json: String): SmartBorrowIntent? {
    return try {
        val o = JSONObject(json)
        val schema = o.optString("schema", "")
        if (schema != SMART_BORROW_INTENT_SCHEMA) return null
        if (!o.has("support_card_id") || !o.has("canonical_character")) return null
        val supportCardId = o.optInt("support_card_id", -1)
        val canonicalCharacter = o.optString("canonical_character", "")
        if (supportCardId < 0 || canonicalCharacter.isBlank()) return null
        SmartBorrowIntent(
            schema = schema,
            schemaVersion = o.optInt("schema_version", 1),
            targetProfile = o.optString("target_profile", ""),
            sourceBorrowScanId = o.optStringOrNull("source_borrow_scan_id"),
            supportCardId = supportCardId,
            canonicalCharacter = canonicalCharacter,
            canonicalTitle = o.optStringOrNull("canonical_title"),
            displayName = o.optString("display_name", canonicalCharacter),
            rarity = o.optStringOrNull("rarity"),
            expectedLevel = o.optIntOrNull("expected_level"),
            expectedLimitBreak = o.optIntOrNull("expected_limit_break"),
            sourceAlias = o.optStringOrNull("source_alias"),
            resolutionPath = o.optStringOrNull("resolution_path"),
            recommendationEvidenceDigest = o.optStringOrNull("recommendation_evidence_digest"),
            recommendationSource = parseIntentRecommendationSource(o.optStringOrNull("recommendation_source")),
        )
    } catch (_: Exception) {
        null
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key, "")
    return v.ifBlank { null }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return if (optInt(key, Int.MIN_VALUE) == Int.MIN_VALUE) null else optInt(key)
}

/** The minimal projection of one observed picker row the locator needs. Built from a
 * [BorrowRowObservation] so the matcher stays free of OCR/pixel state and unit-testable. */
data class LocatableBorrowRow(
    val pageIndex: Int,
    val character: String?,
    val outfit: String?,
    val limitBreakIndex: Int?,
    val level: Int?,
    val ownerAlias: String?,
    val blocked: Boolean,
    val confidence: String?,
)

fun BorrowRowObservation.toLocatable(): LocatableBorrowRow =
    LocatableBorrowRow(
        pageIndex = pageIndex,
        character = character,
        outfit = outfit,
        limitBreakIndex = limitBreakIndex,
        level = level,
        ownerAlias = ownerAlias,
        blocked = blockedTag != null,
        confidence = confidence,
    )

/**
 * Why the locate resolved the way it did. Only [LOCATED] may ever authorise a (future) tap.
 *
 * Owner/source is NOT part of a support card's gameplay identity. The offline resolver merges owners by
 * supportCardId precisely because "card value never depends on how many friends happen to hold it", so
 * two live rows of the same canonical card at the same limit break are functionally interchangeable. The
 * locator treats them as ONE equivalence class instead of an ambiguity: it returns [LOCATED] (preferring
 * the intent's own source when still present, else a deterministic equivalent) and blocks ([AMBIGUOUS])
 * only when the matched rows are NOT equivalent -- the same character+title observed at genuinely
 * different limit breaks, which can happen only when the intent's own limit break is unknown.
 */
enum class SmartBorrowLocateVerdict {
    /** The intent's card is uniquely resolvable as one equivalence class; one row was picked from it. */
    LOCATED,

    /** No borrowable row matched the intent's canonical character + title. */
    NOT_FOUND,

    /** A row matched the identity but every match's observed limit break contradicts the intent's KNOWN
     * expected limit break: same card family, wrong copy. Fails closed. */
    LB_MISMATCH,

    /** The matched rows are NOT one equivalence class: the same character+title observed at different
     * limit breaks (only when the intent's own limit break is unknown), so they are materially different
     * copies and no deterministic pick is safe. Fails closed. */
    AMBIGUOUS,
}

/** The locate outcome plus the evidence behind it, for the log and the persisted record. */
data class SmartBorrowLocateMatch(
    val verdict: SmartBorrowLocateVerdict,
    /** The row picked from the located equivalence class, set only for [SmartBorrowLocateVerdict.LOCATED]. */
    val row: LocatableBorrowRow?,
    /** Every borrowable row that matched the canonical character + title, before the limit-break gate. */
    val identityCandidates: List<LocatableBorrowRow>,
    val reason: String,
    /** True when the located row was the intent's own source, singled out among several equivalent offerings. */
    val disambiguatedByAlias: Boolean = false,
    /** True when the located row was a deterministic pick among several interchangeable equivalent rows
     * (same card + limit break, different owners) rather than the intent's own or the only source. The
     * launch verifies the committed identity after selection regardless; this only records the fact. */
    val equivalentSource: Boolean = false,
    /** How many borrowable rows formed the located card's equivalence class (0 when not located). */
    val equivalenceClassSize: Int = if (row != null) 1 else 0,
)

/**
 * Decides which observed row is the intent's card, or why none is, without tapping anything.
 *
 * Identity is canonical character AND canonical title (both matched through [borrowRowMatchesPreference]
 * so OCR bracket/spacing noise cannot break the match); a blocked row (duplicate / active-trainee) can
 * never be borrowed and is excluded. Among identity matches, a row is kept only if its observed limit
 * break does not contradict a KNOWN expected one (either side unknown is not decisive). The survivors
 * form an EQUIVALENCE CLASS -- the same canonical card at the same limit break, offered by one or more
 * owners, all interchangeable because owner has no gameplay effect. One is picked (the intent's own
 * source when still present, else a deterministic equivalent). Only a class that disagrees on a known
 * limit break is [SmartBorrowLocateVerdict.AMBIGUOUS] and fails closed.
 */
object SmartBorrowLocator {
    fun locate(intent: SmartBorrowIntent, rows: List<LocatableBorrowRow>): SmartBorrowLocateMatch {
        val identity =
            rows.filter { row ->
                !row.blocked &&
                    borrowRowMatchesPreference(row.character ?: "", intent.canonicalCharacter) &&
                    (intent.canonicalTitle == null || borrowRowMatchesPreference(row.outfit ?: "", intent.canonicalTitle))
            }
        if (identity.isEmpty()) {
            return SmartBorrowLocateMatch(SmartBorrowLocateVerdict.NOT_FOUND, null, identity, "no borrowable row matched ${intent.canonicalCharacter} [${intent.canonicalTitle ?: "-"}]")
        }

        // Limit-break gate: only decisive when BOTH the intent and the row observed a limit break.
        val lbAccepted =
            identity.filter { row ->
                intent.expectedLimitBreak == null || row.limitBreakIndex == null || row.limitBreakIndex == intent.expectedLimitBreak
            }
        if (lbAccepted.isEmpty()) {
            return SmartBorrowLocateMatch(
                SmartBorrowLocateVerdict.LB_MISMATCH,
                null,
                identity,
                "identity matched but every candidate's observed limit break != expected ${intent.expectedLimitBreak}",
            )
        }

        // Equivalence guard: the accepted rows already share the intent's character+title. They are one
        // equivalence class only if they do not disagree on a KNOWN limit break. Two distinct known limit
        // breaks (possible only when the intent's own limit break is unknown) are materially different
        // copies, so no deterministic pick is safe.
        val distinctKnownLbs = lbAccepted.mapNotNull { it.limitBreakIndex }.distinct()
        if (distinctKnownLbs.size > 1) {
            return SmartBorrowLocateMatch(
                SmartBorrowLocateVerdict.AMBIGUOUS,
                null,
                identity,
                "the intent's limit break is unknown and the pool holds ${intent.canonicalCharacter} [${intent.canonicalTitle ?: "-"}] at limit breaks ${distinctKnownLbs.sorted()}: not one equivalence class",
            )
        }

        // One equivalence class. Prefer the intent's own source when it is still present; otherwise take a
        // deterministic equivalent. Owner is not part of card identity, so any member is the same card;
        // the launch verifies the committed identity after the tap regardless.
        val byAlias = intent.sourceAlias?.takeIf { it.isNotBlank() }?.let { alias -> lbAccepted.filter { it.ownerAlias == alias } } ?: emptyList()
        if (byAlias.size == 1) {
            return SmartBorrowLocateMatch(
                SmartBorrowLocateVerdict.LOCATED,
                byAlias[0],
                identity,
                if (lbAccepted.size == 1) "exactly one borrowable row matched the intent" else "the intent's own source among ${lbAccepted.size} equivalent offerings",
                disambiguatedByAlias = lbAccepted.size > 1,
                equivalenceClassSize = lbAccepted.size,
            )
        }
        val chosen = pickDeterministicEquivalent(lbAccepted)
        return SmartBorrowLocateMatch(
            SmartBorrowLocateVerdict.LOCATED,
            chosen,
            identity,
            if (lbAccepted.size == 1) "exactly one borrowable row matched the intent" else "a deterministic equivalent among ${lbAccepted.size} interchangeable offerings of the same card",
            equivalentSource = lbAccepted.size > 1,
            equivalenceClassSize = lbAccepted.size,
        )
    }

    /** Deterministic choice among interchangeable equivalent rows: highest known limit break, then
     * highest level, then lowest page index, then owner alias -- so the same pool always yields the same
     * pick and a re-scan cannot oscillate between equivalents. */
    private fun pickDeterministicEquivalent(rows: List<LocatableBorrowRow>): LocatableBorrowRow =
        rows.sortedWith(
            compareByDescending<LocatableBorrowRow> { it.limitBreakIndex ?: -1 }
                .thenByDescending { it.level ?: -1 }
                .thenBy { it.pageIndex }
                .thenBy { it.ownerAlias ?: "" },
        ).first()
}

/** Outcome of one read-only Smart Borrow locate rehearsal, for the diagnostic entry point. */
data class SmartBorrowLocateResult(
    val status: Status,
    val intent: SmartBorrowIntent? = null,
    val rowsObserved: Int = 0,
    val match: SmartBorrowLocateMatch? = null,
    val returnedToSupportFormation: Boolean = false,
    val friendSlotStillEmpty: Boolean = false,
    /** True only when the picker walk saw the whole list without stalling, so a CARD_NOT_FOUND here is a
     * genuine absence rather than a traversal the scroll gesture cut short. Defaults false (conservative). */
    val traversalComplete: Boolean = false,
    val failureReason: String? = null,
) {
    enum class Status {
        /** No usable intent file, or image utils unavailable: nothing was read. */
        INTENT_MISSING,
        NOT_ON_SUPPORT_FORMATION,
        FRIEND_SLOT_NOT_EMPTY,
        PICKER_OPEN_FAILED,

        /** Exactly one borrowable row resolved to the intent's card. Stage A success (still no tap). */
        LOCATED,

        /** The intent's card was not among the borrowable rows read. */
        CARD_NOT_FOUND,

        /** The card's identity matched but its observed limit break contradicts the known expected one. */
        LB_MISMATCH,

        /** Several equivalent offerings matched and none could be singled out. */
        AMBIGUOUS,
    }
}

/**
 * Selection authority (A3-R2): whether a locate result may authorise a positional tap.
 *
 * LOCATED alone is NOT sufficient. The picker traversal must ALSO have fully completed -- a stalled or
 * incomplete traversal must block as BORROW_LOCATOR_STALLED before any tap, because a stalled picker's
 * row coordinates and limit-break pip reads are not trustworthy. The A3-R1 live proof committed a
 * wrong-limit-break row after returning LOCATED off a stalled traversal (the verify caught it and rolled
 * back, but the tap should never have fired). This predicate is the structural gate a production tap must
 * pass; a source guard keeps `if (verdict == LOCATED) tap()` from ever returning.
 */
fun canSelectLocatedBorrow(status: SmartBorrowLocateResult.Status, traversalComplete: Boolean): Boolean =
    status == SmartBorrowLocateResult.Status.LOCATED && traversalComplete

/**
 * Whether a picker row matches the intent's FULL tap-safe identity: canonical character AND canonical
 * title AND a compatible limit break (decisive only when both the intent and the row observed one).
 *
 * Unlike a character+title-only text match, this never selects a wrong-limit-break copy of the same card,
 * which is exactly how the A3-R1 selection tapped an LB3 row for an LB4 intent. The immediate pre-tap
 * revalidation uses this so the row it taps is proven to be the intended limit break, not merely the same
 * character and outfit.
 */
fun rowMatchesIntentIdentity(intent: SmartBorrowIntent, character: String?, outfit: String?, limitBreakIndex: Int?): Boolean =
    borrowRowMatchesPreference(character ?: "", intent.canonicalCharacter) &&
        (intent.canonicalTitle == null || borrowRowMatchesPreference(outfit ?: "", intent.canonicalTitle)) &&
        (intent.expectedLimitBreak == null || limitBreakIndex == null || limitBreakIndex == intent.expectedLimitBreak)

/** Why the post-selection Friends-slot identity check resolved the way it did. Only [VERIFIED] may
 * ever be read as "the committed borrow is the intent's card". */
enum class SelectedSlotVerdict {
    /** Exactly one row carried the "Selected" marker and its identity (and limit break, when known)
     * matches the intent: the committed borrow is the intended card. */
    VERIFIED,

    /** No row carried the "Selected" marker in the bounded reopened-picker walk: nothing is committed
     * where one was expected, or the marker was unreadable. Fails closed. */
    NO_SELECTION,

    /** More than one row carried the "Selected" marker: the reopened picker's state is ambiguous.
     * Fails closed (the game only ever marks one, so this is a read fault). */
    MULTIPLE_SELECTION,

    /** Exactly one row is marked selected but its identity or observed limit break contradicts the
     * intent: the wrong card sits in the slot. Fails closed. */
    IDENTITY_MISMATCH,
}

/** The selected-slot verification outcome plus the evidence behind it. */
data class SelectedSlotVerification(
    val verdict: SelectedSlotVerdict,
    /** The single marked-selected row, set only when exactly one was found (whatever the identity check). */
    val selectedRow: LocatableBorrowRow?,
    /** Every row that carried the "Selected" marker, for the log and the persisted record. */
    val selectedRows: List<LocatableBorrowRow>,
    val reason: String,
)

/**
 * Decides, purely, whether the row the reopened picker marks "Selected" is the intent's card.
 *
 * Identity is canonical character AND canonical title (matched through [borrowRowMatchesPreference],
 * the same OCR-noise-tolerant test the locator uses), and the observed limit break must not contradict
 * a KNOWN expected one (either side unknown is not decisive, exactly as in [SmartBorrowLocator]). It
 * fails CLOSED on zero or several selected rows, so a misread reopened picker can never be read as a
 * verified selection. A pure function of already-observed rows, so it runs unchanged in a JVM test.
 */
object SmartBorrowSelectionVerifier {
    fun verify(intent: SmartBorrowIntent, selectedRows: List<LocatableBorrowRow>): SelectedSlotVerification {
        return verifySelectedBorrowIdentity(
            expectedCharacter = intent.canonicalCharacter,
            expectedTitle = intent.canonicalTitle,
            expectedLimitBreak = intent.expectedLimitBreak,
            expectedLabel = "${intent.canonicalCharacter} [${intent.canonicalTitle ?: "-"}]",
            selectedRows = selectedRows,
        )
    }
}

internal fun verifySelectedBorrowIdentity(
    expectedCharacter: String?,
    expectedTitle: String?,
    expectedLimitBreak: Int?,
    expectedLabel: String,
    selectedRows: List<LocatableBorrowRow>,
    requireExpectedTitle: Boolean = false,
): SelectedSlotVerification {
    if (selectedRows.isEmpty()) {
        return SelectedSlotVerification(SelectedSlotVerdict.NO_SELECTION, null, selectedRows, "no reopened-picker row carried the Selected marker")
    }
    if (selectedRows.size > 1) {
        return SelectedSlotVerification(SelectedSlotVerdict.MULTIPLE_SELECTION, null, selectedRows, "${selectedRows.size} rows carried the Selected marker (expected exactly one)")
    }
    val row = selectedRows[0]
    if (expectedCharacter.isNullOrBlank() || (requireExpectedTitle && expectedTitle.isNullOrBlank())) {
        return SelectedSlotVerification(
            SelectedSlotVerdict.IDENTITY_MISMATCH,
            row,
            selectedRows,
            "accepted row identity was unreadable before the tap ($expectedLabel)",
        )
    }
    val identityOk =
        borrowRowMatchesPreference(row.character ?: "", expectedCharacter) &&
            (expectedTitle == null || borrowRowMatchesPreference(row.outfit ?: "", expectedTitle))
    if (!identityOk) {
        return SelectedSlotVerification(
            SelectedSlotVerdict.IDENTITY_MISMATCH,
            row,
            selectedRows,
            "selected row \"${row.character ?: "-"} [${row.outfit ?: "-"}]\" != expected $expectedLabel",
        )
    }
    // Limit-break gate: decisive only when BOTH the intent and the selected row observed one.
    if (expectedLimitBreak != null && row.limitBreakIndex != null && row.limitBreakIndex != expectedLimitBreak) {
        return SelectedSlotVerification(
            SelectedSlotVerdict.IDENTITY_MISMATCH,
            row,
            selectedRows,
            "selected row limit break ${row.limitBreakIndex} != expected $expectedLimitBreak",
        )
    }
    return SelectedSlotVerification(SelectedSlotVerdict.VERIFIED, row, selectedRows, "the marked-selected row matches the expected identity")
}

/** Outcome of one Borrow "Remove" behaviour probe, for the diagnostic entry point. */
data class BorrowRemoveProbeResult(
    val status: Status,
    val removeControlFound: Boolean = false,
    val slotEmptyAfter: Boolean = false,
    val returnedToSupportFormation: Boolean = false,
    val failureReason: String? = null,
) {
    enum class Status {
        NOT_ON_SUPPORT_FORMATION,

        /** Nothing to remove: the operator did not borrow a throwaway card first. */
        FRIEND_SLOT_ALREADY_EMPTY,
        PICKER_OPEN_FAILED,

        /** The picker opened but carried no Remove control on the banner path. */
        REMOVE_CONTROL_ABSENT,

        /** Tapping Remove returned the Friends slot to empty: the reversible rollback path is proven. */
        REMOVE_CLEARS_TO_EMPTY,

        /** Tapping Remove did NOT restore an empty slot: the rollback path is not proven. */
        REMOVE_DID_NOT_CLEAR,
    }
}

/**
 * Outcome of ONE Smart Borrow select-verify-rollback cycle (DeckLab Smart Borrow 2.0, Stage B/C):
 * locate the intent's row, tap exactly it, verify the committed friend slot is that card via the
 * reopened picker's "Selected" marker, then Remove it and confirm the slot is empty again. Every
 * terminal status other than [REHEARSAL_PASSED] fails closed; the flow never presses Start Career and
 * spends nothing.
 */
data class SmartBorrowSelectResult(
    val status: Status,
    val iteration: Int = 1,
    val intent: SmartBorrowIntent? = null,
    val rowsObserved: Int = 0,
    val locateMatch: SmartBorrowLocateMatch? = null,
    val tapped: Boolean = false,
    val slotFilledAfterTap: Boolean = false,
    val verification: SelectedSlotVerification? = null,
    val removeControlFound: Boolean = false,
    val slotEmptyAfterRollback: Boolean = false,
    val deckNumberAtStart: Int? = null,
    val deckNumberAtEnd: Int? = null,
    val tpRawAtStart: String? = null,
    val tpRawAtEnd: String? = null,
    val tpUnchanged: Boolean = false,
    val returnedToSupportFormation: Boolean = false,
    val failureReason: String? = null,
) {
    /** True only for the fully clean pass: located, tapped, verified, rolled back to an empty slot. */
    val passed: Boolean get() = status == Status.REHEARSAL_PASSED

    enum class Status {
        /** No usable intent file, or image utils unavailable: nothing was read or tapped. */
        INTENT_MISSING,
        NOT_ON_SUPPORT_FORMATION,
        FRIEND_SLOT_NOT_EMPTY,
        PICKER_OPEN_FAILED,

        /** The intent's card was not among the borrowable rows: no tap. */
        CARD_NOT_FOUND,

        /** Identity matched but the observed limit break contradicts the known expected one: no tap. */
        LB_MISMATCH,

        /** Several equivalent offerings matched and none could be singled out: no tap. */
        AMBIGUOUS,

        /** The locate resolved, but more than one distinct borrowable row shares the intent's identity,
         * so a name-band tap cannot guarantee the exact copy: no tap (fail closed). */
        SELECT_AMBIGUOUS_IDENTITY,

        /** The row could not be tapped (it was gone from the re-scan, or the picker misbehaved). */
        SELECT_TAP_FAILED,

        /** The tap did not fill the friend slot (no card committed). */
        SLOT_NOT_FILLED,

        /** The committed slot's identity did not verify against the intent. Rolled back. */
        SELECTION_VERIFY_FAILED,

        /** The reopened picker over the filled slot carried no active Remove control: rollback BLOCKED
         * with a card still committed. The operator must clear the friend slot by hand. */
        REMOVE_CONTROL_ABSENT,

        /** Remove was tapped but the friend slot did not return to empty: rollback BLOCKED. */
        ROLLBACK_FAILED,

        /** Located, tapped, selection identity verified, and rolled back to an empty slot. */
        REHEARSAL_PASSED,
    }
}

/** Outcome of the two-iteration Smart Borrow select-verify-rollback rehearsal (Stage B then Stage C).
 * Stage C runs only after Stage B passes; [repeatable] is true when both cycles passed with the same
 * located identity, match path, and clean rollback. */
data class SmartBorrowSelectRehearsalResult(
    val stageB: SmartBorrowSelectResult,
    val stageC: SmartBorrowSelectResult? = null,
    val repeatable: Boolean = false,
    val finalSlotEmpty: Boolean = false,
    val noCareerStarted: Boolean = true,
)
