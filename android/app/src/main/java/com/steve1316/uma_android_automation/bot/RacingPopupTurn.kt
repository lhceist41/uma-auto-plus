package com.steve1316.uma_android_automation.bot

/**
 * Which turn a forced-race popup (Insufficient Fans, Goal Not Reached) belongs to. The game shows it at the start of a turn, so one closed
 * before that turn's date read still forces the race after the read; one closed after the read belongs to the turn already read.
 */
internal class RacingPopupTurn {
    private var closedBeforeDateRead = false

    /** A popup was closed; [dateReadThisTurn] is false while the read of the turn it opens is still to come. */
    fun closed(dateReadThisTurn: Boolean) {
        if (!dateReadThisTurn) closedBeforeDateRead = true
    }

    /** Whether the popup race is armed after a date read that starts a new turn ([newTurn]) or repeats the current one, given [armed] before it. */
    fun armedAfterDateRead(
        newTurn: Boolean,
        armed: Boolean,
    ): Boolean {
        val result = if (newTurn) closedBeforeDateRead else armed
        closedBeforeDateRead = false
        return result
    }

    companion object {
        /** An armed popup races unless the scenario fan policy defers this turn's fan requirement; that policy stays in charge of it. */
        fun forcesRace(
            armed: Boolean,
            fanRaceDeferred: () -> Boolean,
        ): Boolean = armed && !fanRaceDeferred()
    }
}
