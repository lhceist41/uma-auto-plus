package com.steve1316.uma_android_automation.bot

/**
 * Songs bought per concert cycle and the turn a Lessons visit is on. The visit hook runs before the turn's date read, so after a step
 * that moved the turn the date still shows the previous one; a performed concert ends its cycle whatever the date shows.
 */
internal class GrandConcertCycle {
    /** A floor on the cycle's new songs (blind to free or pre-attach songs). */
    var songsThisCycle = 0
        private set

    /** The last concert performed, or proven past by a date read. */
    var lastConcertTurn = 0
        private set

    /** The turn a turn-moving step started on, until the next date read shows where the turn is. */
    private var advancedFromTurn: Int? = null

    /** A step that started on an unread date is ignored. */
    fun noteTurnAdvanced(fromTurn: Int) {
        if (fromTurn > 1) advancedFromTurn = maxOf(advancedFromTurn ?: 0, fromTurn)
    }

    /**
     * Runs [step] and marks the turn when [moved] reads its result as a move. The start is this cycle's estimate, not the raw date: a race
     * day on the prep screen runs with no date read since the previous move.
     */
    inline fun <T> trackStep(dateDay: Int, moved: (T) -> Boolean, step: () -> T): T {
        val fromTurn = visitTurn(dateDay)
        val result = step()
        if (moved(result)) noteTurnAdvanced(fromTurn)
        return result
    }

    fun noteDateRead() {
        advancedFromTurn = null
    }

    fun noteSongBought() {
        songsThisCycle++
    }

    /** [dateDay] at or below 1 is an unread date and stays unread. */
    fun visitTurn(dateDay: Int): Int = if (dateDay <= 1) dateDay else maxOf(dateDay, (advancedFromTurn ?: 0) + 1, lastConcertTurn + 1)

    /**
     * Starts the next cycle once the escort has finished a concert. The pending screen still shows the concert turn or the one
     * before it; an unread [dateDay] means the next concert after the last one. Returns the concert turn when the counter reset.
     */
    fun noteConcertPerformed(dateDay: Int?): Int? {
        val turn =
            (if (dateDay != null && dateDay > 1) CONCERT_TURNS.firstOrNull { it >= dateDay } else null)
                ?: CONCERT_TURNS.firstOrNull { it > lastConcertTurn }
                ?: return null
        return startCycleAfter(turn)
    }

    /** A date read past a concert proves it was performed, for an escort that did not see it finish. Never fed an inferred turn. */
    fun syncToDate(dateDay: Int): Int? {
        if (dateDay <= 1) return null
        // Strictly before: the concert turn still belongs to the ending cycle; with <= the counter reset early.
        return startCycleAfter(CONCERT_TURNS.lastOrNull { it < dateDay } ?: return null)
    }

    private fun startCycleAfter(concertTurn: Int): Int? {
        if (concertTurn <= lastConcertTurn) return null
        lastConcertTurn = concertTurn
        songsThisCycle = 0
        return concertTurn
    }

    companion object {
        private val CONCERT_TURNS = GrandConcertScenario.CONCERT_TURNS.value

        /** The concert turn's visit runs before its concert, so it counts as 0. */
        fun turnsUntilConcert(turn: Int): Int? = CONCERT_TURNS.firstOrNull { it >= turn }?.let { it - turn }
    }
}
