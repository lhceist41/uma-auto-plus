package com.steve1316.uma_android_automation

internal const val BORROW_PRETAP_VALIDATION_SETTING = "debugMode_stopBeforeBorrowTap"

/** Fresh row evidence accepted by the active Borrow selection policy. */
internal data class AcceptedBorrowRow(
    val centerY: Double,
    val identity: String,
    val observedIdentity: LocatableBorrowRow? = null,
)

internal enum class BorrowTapStatus {
    WAITING_FOR_ACCEPTED_ROW,
    TAPPED,
    LOCATED_VALIDATED_TAP_SUPPRESSED,
}

internal data class BorrowTapResult(
    val status: BorrowTapStatus,
    val row: AcceptedBorrowRow? = null,
) {
    val tapped: Boolean get() = status == BorrowTapStatus.TAPPED
    val suppressed: Boolean get() = status == BorrowTapStatus.LOCATED_VALIDATED_TAP_SUPPRESSED
}

/**
 * One-shot boundary between a freshly accepted Borrow row and its production tap.
 *
 * The default is inert. When armed, the first accepted row becomes terminal evidence and the tap
 * callback is not invoked. Later calls return that same evidence, so no fallback path can tap a
 * different row after validation has stopped the launch.
 */
internal class BorrowPreTapValidationGate(
    private val armed: Boolean = false,
) {
    private var terminalResult: BorrowTapResult? = null

    fun attempt(
        acceptedRow: AcceptedBorrowRow?,
        tap: (AcceptedBorrowRow) -> Unit,
    ): BorrowTapResult {
        terminalResult?.let { return it }
        if (acceptedRow == null) return BorrowTapResult(BorrowTapStatus.WAITING_FOR_ACCEPTED_ROW)

        if (armed) {
            return BorrowTapResult(BorrowTapStatus.LOCATED_VALIDATED_TAP_SUPPRESSED, acceptedRow)
                .also { terminalResult = it }
        }

        tap(acceptedRow)
        return BorrowTapResult(BorrowTapStatus.TAPPED, acceptedRow)
    }
}
