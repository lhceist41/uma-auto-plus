package com.steve1316.uma_android_automation.bot

/**
 * Counts consecutive energy recoveries that did not prove the turn moved. A later date, a proven move or an executed training starts the count again,
 * because the date alone cannot: it reads the same across real turns where the game shows no date (Grand Concert pre-debut) or its read falls back.
 */
internal class UnmovedEnergyRecoveries {
    private var day = -1
    private var unmoved = 0
    private var unconfirmed = 0

    fun on(day: Int): Int = if (isLaterTurn(day)) 0 else unmoved + unconfirmed

    /** The same rule [confirmTurnMoved] uses, so a stationary screen whose read flips to a fallback date and back cannot reset the bound. */
    private fun isLaterTurn(day: Int): Boolean = day > this.day && day !in DATE_READ_FALLBACK_DAYS

    /** [provenUnmoved] is true when the main screen showed no change, false when the screen never settled. */
    fun record(
        day: Int,
        provenUnmoved: Boolean,
    ): Int {
        if (isLaterTurn(day)) {
            this.day = day
            clear()
        }
        if (provenUnmoved) unmoved++ else unconfirmed++
        return unmoved + unconfirmed
    }

    fun clear() {
        unmoved = 0
        unconfirmed = 0
    }

    fun limitReached(): Boolean = unmoved >= MAX_UNMOVED_ENERGY_RECOVERIES || unmoved + unconfirmed >= MAX_UNCONFIRMED_ENERGY_RECOVERIES
}

/** The second proven-unmoved recovery in a row already skipped the outing, so a third would only repeat the same Rest. */
internal const val MAX_UNMOVED_ENERGY_RECOVERIES = 2

/** A Rest dialog the OK tap missed costs one unconfirmed try before the dialog handler finishes the Rest and the date changes, so allow one more. */
internal const val MAX_UNCONFIRMED_ENERGY_RECOVERIES = 3

/**
 * One low-energy recovery: the support-card outing (only while no recovery in a row has failed), then Rest. Nothing else re-checks energy on an
 * unchanged turn, so training is picked again and a recovery that did not move the turn would repeat forever; the bound needs proof of a move.
 *
 * @param tryOuting True when an outing started.
 * @param rest True when a Rest button was tapped.
 * @param turnMoved See [confirmTurnMoved].
 * @return False only when the turn provably did not move. An unconfirmed recovery (most often a dialog or transition after a real tap) counts as
 *   spent for the caller's turn bookkeeping, but still counts toward the bound.
 */
internal fun recoverEnergyAndConfirmTurn(
    day: Int,
    unmoved: UnmovedEnergyRecoveries,
    tryOuting: () -> Boolean,
    rest: () -> Boolean,
    turnMoved: () -> Boolean?,
    warn: (String) -> Unit,
    stop: (String) -> Nothing,
): Boolean {
    val outingAllowed = unmoved.on(day) == 0
    val tapped = (outingAllowed && tryOuting()) || rest()
    val moved = if (tapped) turnMoved() else false
    if (moved == true) {
        unmoved.clear()
        return true
    }

    val tries = unmoved.record(day, provenUnmoved = moved == false)
    if (unmoved.limitReached()) {
        stop("Energy recovery did not move turn $day after $tries tries, so the run stopped instead of looping. Rest once in the game, then press Start.")
    }
    val what = if (moved == false) "left turn $day unchanged" else "on turn $day could not be confirmed (a dialog, a transition or an unreadable screen)"
    warn("Energy recovery $what. The next try goes straight to Rest.")
    return moved == null
}

/** One look at the screen after a recovery tap. [day] and [energy] are null off the main screen or when they could not be read. */
internal data class TurnScreen(
    val mainScreen: Boolean,
    val trainingEvent: Boolean = false,
    val day: Int? = null,
    val energy: Int? = null,
)

internal const val TURN_CHECK_READS = 4

/** A failed pre-debut turns-left read gives turn 12 and a failed finale read gives turn 73, so reaching either proves nothing. */
internal val DATE_READ_FALLBACK_DAYS = setOf(12, 73)

/** Rest and Summer Rest add at least 30 energy, so a smaller rise is read noise. */
internal const val ENERGY_RISE_PROOF = 10

/**
 * Whether a recovery tap moved the turn, from up to [TURN_CHECK_READS] reads with a wait between them. True only on positive evidence: a training
 * event (only a spent turn opens one), a later date that is not a read fallback, or energy risen by [ENERGY_RISE_PROOF] over [energyBefore]. False
 * when the main screen showed energy that had room to rise but did not, at least twice, and nothing proved a move. Null otherwise: a dialog still
 * open (a missed OK or an outing's partner list), a long transition, or an energy bar that could not be read or was too full to show a rise.
 */
internal fun confirmTurnMoved(
    day: Int,
    energyBefore: Int?,
    read: () -> TurnScreen,
    wait: () -> Unit,
): Boolean? {
    var unchangedReads = 0
    repeat(TURN_CHECK_READS) { i ->
        if (i > 0) wait()
        val screen = read()
        if (screen.trainingEvent) return true
        if (!screen.mainScreen) return@repeat
        if (screen.day != null && screen.day > day && screen.day !in DATE_READ_FALLBACK_DAYS) return true
        if (screen.energy != null && energyBefore != null) {
            if (screen.energy >= energyBefore + ENERGY_RISE_PROOF) return true
            // A bar too full to rise by the proof margin cannot show that a Rest landed.
            if (energyBefore + ENERGY_RISE_PROOF <= 100) unchangedReads++
        }
    }
    return if (unchangedReads >= 2) false else null
}
