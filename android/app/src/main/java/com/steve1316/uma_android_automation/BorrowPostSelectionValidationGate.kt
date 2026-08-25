package com.steve1316.uma_android_automation

internal const val BORROW_POST_SELECTION_VALIDATION_SETTING = "debugMode_stopAfterBorrowSelectionVerified"

internal enum class BorrowPostSelectionAuthority {
    PRODUCTION_LAUNCH,
    REHEARSAL,
}

internal fun BorrowPostSelectionAuthority.allowsPostSelectionValidation(armed: Boolean): Boolean =
    armed && this == BorrowPostSelectionAuthority.PRODUCTION_LAUNCH

internal enum class BorrowPostSelectionStatus {
    WAITING_FOR_TAPPED_ROW,
    SELECTION_VERIFICATION_FAILED,
    SELECTED_VERIFIED_START_SUPPRESSED,
}

internal data class BorrowPostSelectionResult(
    val status: BorrowPostSelectionStatus,
    val acceptedRow: AcceptedBorrowRow? = null,
    val verification: SelectedSlotVerification? = null,
    val slotCommitted: Boolean = false,
    val freshVerifier: Boolean = false,
    val returnedToSupportFormation: Boolean = false,
    val startCareerTapped: Boolean = false,
    val tpRawAtStart: String? = null,
    val tpRawAtEnd: String? = null,
    val reason: String,
) {
    val suppressed: Boolean get() = status == BorrowPostSelectionStatus.SELECTED_VERIFIED_START_SUPPRESSED
    val tpUnchanged: Boolean? get() = if (tpRawAtStart == null || tpRawAtEnd == null) null else tpRawAtStart == tpRawAtEnd
    val tpEvidenceStatus: String
        get() =
            when (tpUnchanged) {
                true -> "unchanged"
                false -> "changed"
                null -> "unknown"
            }
}

private fun String?.normalizedTpEvidence(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/**
 * One-shot boundary between a real Borrow tap and either Start Career entry point.
 *
 * The default is inert. When armed, a tapped accepted row must be corroborated by a fresh selected
 * marker read after the slot commits and the picker returns to Support Formation. Success and failure
 * are both terminal, so no fallback can proceed to Start Career after a real selection.
 */
internal class BorrowPostSelectionValidationGate(
    val armed: Boolean = false,
) {
    private var tappedRow: AcceptedBorrowRow? = null
    private var tpRawAtStart: String? = null
    private var terminalResult: BorrowPostSelectionResult? = null

    val hasTappedRow: Boolean get() = tappedRow != null
    val acceptedTappedRow: AcceptedBorrowRow? get() = tappedRow

    fun recordTap(result: BorrowTapResult, tpRawBeforeTap: String? = null) {
        if (!armed || terminalResult != null || !result.tapped || result.row == null) return
        tappedRow = result.row
        tpRawAtStart = tpRawBeforeTap.normalizedTpEvidence()
    }

    fun failClosedWithoutCurrentTap(
        slotCommitted: Boolean,
        returnedToSupportFormation: Boolean,
    ): BorrowPostSelectionResult? {
        if (!armed || tappedRow != null) return null
        return evaluate(
            slotCommitted = slotCommitted,
            freshVerifier = false,
            verification = null,
            returnedToSupportFormation = returnedToSupportFormation,
        )
    }

    fun evaluate(
        slotCommitted: Boolean,
        freshVerifier: Boolean,
        verification: SelectedSlotVerification?,
        returnedToSupportFormation: Boolean,
        tpRawAtEnd: String? = null,
    ): BorrowPostSelectionResult? {
        if (!armed) return null
        terminalResult?.let { return it }

        val row = tappedRow
        val normalizedTpRawAtEnd = tpRawAtEnd.normalizedTpEvidence()
        val tpUnchanged = tpRawAtStart == null || normalizedTpRawAtEnd == null || tpRawAtStart == normalizedTpRawAtEnd
        val verified =
            row != null &&
                slotCommitted &&
                freshVerifier &&
                returnedToSupportFormation &&
                tpUnchanged &&
                verification?.verdict == SelectedSlotVerdict.VERIFIED
        val reason =
            when {
                row == null -> "post-selection validation was armed but no real Borrow tap occurred in this navigation"
                !slotCommitted -> "the Borrow slot was not freshly confirmed as committed"
                !freshVerifier -> "the committed selection was not read by a fresh verifier"
                verification == null -> "the reopened picker could not be read"
                verification.verdict != SelectedSlotVerdict.VERIFIED -> verification.reason
                !returnedToSupportFormation -> "the picker did not return to Support Formation"
                !tpUnchanged -> "TP changed before Start Career (${tpRawAtStart ?: "-"} -> ${tpRawAtEnd ?: "-"})"
                else -> "the tapped Borrow row is freshly verified as the committed selection"
            }
        return BorrowPostSelectionResult(
            status =
                if (verified) {
                    BorrowPostSelectionStatus.SELECTED_VERIFIED_START_SUPPRESSED
                } else {
                    BorrowPostSelectionStatus.SELECTION_VERIFICATION_FAILED
                },
            acceptedRow = row,
            verification = verification,
            slotCommitted = slotCommitted,
            freshVerifier = freshVerifier,
            returnedToSupportFormation = returnedToSupportFormation,
            tpRawAtStart = tpRawAtStart,
            tpRawAtEnd = normalizedTpRawAtEnd,
            reason = reason,
        ).also { terminalResult = it }
    }
}

/** Verifies the selected marker against identity read from the accepted row before its real tap. */
internal object AcceptedBorrowSelectionVerifier {
    fun verify(acceptedRow: AcceptedBorrowRow, selectedRows: List<LocatableBorrowRow>): SelectedSlotVerification {
        val expected = acceptedRow.observedIdentity
        return verifySelectedBorrowIdentity(
            expectedCharacter = expected?.character,
            expectedTitle = expected?.outfit,
            expectedLimitBreak = expected?.limitBreakIndex,
            expectedLabel = acceptedRow.identity,
            selectedRows = selectedRows,
            requireExpectedTitle = true,
        )
    }
}
